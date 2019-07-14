# OSGi 中的 Akka
## 依赖

为了在 OSGi 中使用 Akka，你需要将以下依赖添加到你的项目中：

```xml
<!-- Maven -->
<dependency>
  <groupId>com.typesafe.akka</groupId>
  <artifactId>akka-osgi_2.12</artifactId>
  <version>2.5.23</version>
</dependency>

<!-- Gradle -->
dependencies {
  compile group: 'com.typesafe.akka', name: 'akka-osgi_2.12', version: '2.5.23'
}

<!-- sbt -->
libraryDependencies += "com.typesafe.akka" %% "akka-osgi" % "2.5.23"
```

## 示例项目

你可以查看「[OSGi Dining Hakkers](https://developer.lightbend.com/start/?group=akka&project=akka-samples-osgi-dining-hakkers)」示例项目，以了解其在实践中的使用情况。

## 背景

「[OSGi](https://www.osgi.org/developer/where-to-start/https://www.manning.com/books/osgi-in-action)」是一个成熟的基于组件的系统打包和部署标准。它具有与项目 Jigsaw（最初计划为 JDK 1.8）相似的功能，但它有更强大的设施来支持遗留 Java 代码。这就是说，尽管 Jigsaw-ready 模块需要对大多数源文件进行重大更改，有时还需要对整个应用程序的结构进行修改，但 OSGi 可以用于将几乎任何 Java 代码模块化为 JDK 1.2，通常对二进制文件没有任何更改。

这些遗留功能是 OSGi 的主要优势和主要弱点。OSGi 的创建者早就意识到，实现者不太可能急于在现有 JAR 中支持 OSGi 元数据。在 JRE 中已经有一些新的概念需要学习，而直接使用 J2EE 管理良好的团队的附加价值并不明显。已经出现了“包装”二进制 JAR 的工具，因此它们可以作为捆绑包使用，但此功能仅在有限的情况下使用。这里的`80/20 规则`为一个应用程序可能会有「80% 的复杂性可以通过 20% 的配置解决」，但这足以给 OSGi 名声大振。

本文档旨在为大家提供使用 Akka 所需的基础知识，要获得比这里提供的更多的信息，「[OSGi In Action](https://www.manning.com/books/osgi-in-action)」是值得探索的。

## OSGi 应用程序的核心组件和结构

在 OSGi 中部署的基本单元是`Bundle`。一个`Bundle`是一个`MANIFEST.MF`文件中附加条目包括 https://www.osgi.org/bundle-headers-reference/ 的 Java JAR 包，它最小地暴露了导入和导出的包和`Bundle`的名称和版本。由于这些`manifest`条目在 OSGi 部署之外被忽略，所以`Bundle`可以作为 JRE 中的 JAR 互换使用。

当加载一个`Bundle`时，需要为每个`Bundle`实例化 Java ClassLoader 的专门实现。每个类加载器读取`manifest`条目，并在容器单例中发布功能（以`Bundle-Exports`的形式）和需求（作为`Bundle-Imports`），以供其他`Bundle`发现。通过这些类加载器将导入与跨`Bundle`导出匹配的过程是解析过程，这是 OSGi 容器中`Bundle`的生命周期 FSM 中六个离散步骤之一：

1. `INSTALLED`：已从磁盘加载已安装的`Bundle`，并实例化了具有其功能的类加载器。`Bundle`是手动或通过特定于容器的描述符迭代安装的。对于那些熟悉遗留打包（如 EJB）的人来说，OSGi 的模块化特性意味着`Bundle`可以被具有重叠依赖关系的多个应用程序使用。通过从存储库中单独解析这些重叠，可以跨多个部署将这些重叠消除到同一个容器中。
2. `RESOLVED`：已解决的`Bundle`是满足其需求（导入）的`Bundle`，解析意味着可以启动一个`Bundle`。
3. `STARTING`：启动的`Bundle`可以被其他`Bundle`使用。对于以其他方式完全关闭已解析`Bundle`的应用程序，这里的含义是必须按照深度优先搜索所有要启动的`Bundle`的顺序启动它们。当一个`Bundle`启动时，`Bundle`中任何暴露的生命周期接口都会被调用，从而使`Bundle`有机会启动自己的服务端点和线程。
4. `ACTIVE`：一旦一个`Bundle`的生命周期接口无错误地返回，`Bundle`就被标记为活动的。
5. `STOPPING`：正在停止的`Bundle`调用`Bundle`的停止生命周期，并在完成时转换回`RESOLVED`状态。在`STARTING`时创建的任何长时间运行的服务或线程都应在调用`Bundle`的停止生命周期时关闭。
6. `UNINSTALLED`：`Bundle`只能从`INSTALLED`状态转换到此状态，这意味着在停止之前无法卸载它。

注意这个 FSM 中对生命周期接口的依赖性。虽然没有要求`Bundle`发布这些接口或接受此类回调，但生命周期接口提供`main()`方法的语义，并允许`Bundle`启动和停止长时间运行的服务，如 REST 页面服务、ActorSystem、群集等。

其次，请注意，在考虑需求和能力时，将它们与存储库依赖性等同是一个常见的误解，这可能在 Maven 或 Ivy 中发现。虽然它们提供了类似的实用功能，但是 OSGi 有几种并行类型的依赖关系（如 Blueprint Services），这些依赖关系不容易映射到存储库功能上来。实际上，核心规范将这些设施留给使用中的容器。反过来，一些容器有工具从存储库元数据生成应用程序加载描述符。

## 显著的行为变化

结合对`Bundle`生命周期的理解，OSGi 开发人员必须注意有时引入的意外行为。这些行为通常都在 JVM 规范中，但是有些意外的情况，仍然会导致失败。

`Bundle`不应导出重叠的包空间。对于传统的 JVM 框架来说，期望由多个 JAR 组成的应用程序中的插件驻留在一个包名称下并不少见。例如，前端应用程序可能会扫描`com.example.plugins`中的所有类，以查找特定的服务实现，该包存在于几个贡献的 JAR 中。虽然支持具有复杂`manifest`头的重叠包是可能的，但最好使用不重叠的包空间和设施（如「[Akka 集群](https://github.com/guobinhit/akka-guide/blob/master/articles/clustering/cluster-specification.md)」）进行服务发现。在风格上，许多组织选择使用根包路径作为包分发文件的名称。

除非像类那样显式导出，否则资源不会在`Bundle`之间共享。这种情况的常见情况是，`getClass().getClassLoader().getResources("foo")`将返回名为`foo`的类路径上的所有文件。`getResources()`方法只从当前的类加载器返回资源，并且由于每个`Bundle`都有单独的类加载器，因此资源文件（如配置）不再能够以这种方式进行搜索。

## 配置 OSGi 框架

要在 OSGi 环境中使用 Akka，必须对容器进行配置，使`org.osgi.framework.bootdelegation`属性将`sun.misc`包委托给引导类加载器，而不是通过普通的 OSGi 类空间解析它。

## 预期用途

Akka 只支持严格限制在单个 OSGi 包中使用`ActorSystem`，其中该`Bundle`包含或导入所有 Actor 系统的需求。这意味着不建议将`ActorSystem`作为一个服务来提供，Actor 可以通过其他`Bundle`动态部署到该服务上，`ActorSystem`及其包含的 Actor 在这种方式下不应该以是动态的。`ActorRef`可以安全地暴露在其他`Bundle`中。

## Activator

要在 OSGi 环境中引导 Akka，可以使用`akka.osgi.ActorSystemActivator`类方便地设置`ActorSystem`。

```java
import akka.actor.{ ActorSystem, Props }
import org.osgi.framework.BundleContext
import akka.osgi.ActorSystemActivator

class Activator extends ActorSystemActivator {

  def configure(context: BundleContext, system: ActorSystem): Unit = {
    // optionally register the ActorSystem in the OSGi Service Registry
    registerService(context, system)

    val someActor = system.actorOf(Props[SomeActor], name = "someName")
    someActor ! SomeMessage
  }
}
```

这里的目标是将 OSGi 生命周期更直接地映射到 Akka 生命周期。`ActorSystemActivator`使用类加载器创建 Actor 系统，该类加载器从应用程序`Bundle`和所有可传递依赖项中查找资源（`application.conf`和`reference.conf`文件）和类。


----------

**英文原文链接**：[Akka in OSGi](https://doc.akka.io/docs/akka/current/additional/osgi.html).




----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————