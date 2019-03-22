# 配置
你可以在不定义任何配置的情况下开始使用 Akka，因为提供了合理的默认值。稍后，你可能需要修改设置以更改默认行为或适应特定的运行时环境。你可以修改的典型设置示例：

- 日志级别和日志记录器后端
- 启用远程处理
- 消息序列化程序
- 路由器的定义
- 调度员调整

Akka 使用「[Typesafe Config Library](https://github.com/lightbend/config)」，这对于配置你自己的应用程序或使用或不使用 Akka 构建的库也是一个不错的选择。这个库是用 Java 实现的，没有外部依赖关系；你应该看看它的文档，特别是关于「[ConfigFactory](https://lightbend.github.io/config/latest/api/com/typesafe/config/ConfigFactory.html)」。

- **警告**：如果你使用来自`2.9.x`系列的 Scala REPL 的 Akka，并且没有向`ActorSystem`提供自己的`ClassLoader`，那么使用`-Yrepl-sync`启动 REPL，以解决 REPLs 提供的上下文类加载器中的缺陷。

## 从哪里读取配置？
Akka 的所有配置（`configuration`）都保存在`ActorSystem`实例中，或者换句话说，从外部看，`ActorSystem`是配置信息的唯一使用者。在构造 Actor 系统时，可以传入`Config`对象，也可以不传入，其中第二种情况等同于传递`ConfigFactory.load()`（使用正确的类加载器）。这大致意味着默认值是解析类路径根目录下的所有`application.conf`、`application.json`和`application.properties`。有关详细信息，请参阅上述文档。然后，Actor 系统合并在类路径根目录下找到的所有`reference.conf`资源，以形成可靠的（`fallback`）配置，即内部使用。

```java
appConfig.withFallback(ConfigFactory.defaultReference(classLoader))
```
其原理是代码从不包含默认值，而是依赖于它们在相关库提供的`reference.conf`中的存在。

作为系统属性给出的覆盖具有最高优先级，请参阅「[HOCON](https://github.com/lightbend/config/blob/master/HOCON.md)」规范（靠近底部）。另外值得注意的是，默认为应用程序的应用程序配置可以使用`config.resource`属性覆盖，还有更多内容，请参阅「[Config](https://github.com/lightbend/config/blob/master/README.md)」文档。

- **注释**：如果你正在编写 Akka 应用程序，请将你的配置保存在类路径根目录下的`application.conf`中。如果你正在编写基于 Akka 的库，请将其配置保存在 JAR 文件根目录下的`reference.conf`中。

## 使用 JarJar、OneJar、Assembly 或任何  jar-bundler 时

- **警告**：Akka 的配置方法很大程度上依赖于每个`module/jar`都有自己的`reference.conf`文件的概念，所有这些都将由配置发现并加载。不幸的是，这也意味着如果你将多个 Jar 放入或合并到同一个 Jar 中，那么你还需要合并所有`reference.conf`。否则，所有默认值将丢失，Akka 将不起作用。

如果使用 Maven 打包应用程序，还可以使用「[Apache Maven Shade Plugin](http://maven.apache.org/plugins/maven-shade-plugin/)」的「[Resource Transformers](http://maven.apache.org/plugins/maven-shade-plugin/examples/resource-transformers.html#AppendingTransformer)」，其支持将构建类路径上的所有`reference.conf`合并为一个。

插件配置可能如下所示：

```xml
<plugin>
 <groupId>org.apache.maven.plugins</groupId>
 <artifactId>maven-shade-plugin</artifactId>
 <version>1.5</version>
 <executions>
  <execution>
   <phase>package</phase>
   <goals>
    <goal>shade</goal>
   </goals>
   <configuration>
    <shadedArtifactAttached>true</shadedArtifactAttached>
    <shadedClassifierName>allinone</shadedClassifierName>
    <artifactSet>
     <includes>
      <include>*:*</include>
     </includes>
    </artifactSet>
    <transformers>
      <transformer
       implementation="org.apache.maven.plugins.shade.resource.AppendingTransformer">
       <resource>reference.conf</resource>
      </transformer>
      <transformer
       implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
       <manifestEntries>
        <Main-Class>akka.Main</Main-Class>
       </manifestEntries>
      </transformer>
    </transformers>
   </configuration>
  </execution>
 </executions>
</plugin>
```
## 自定义 application.conf
一个自定义的`application.conf`配置可能如下所示：

```
# In this file you can override any option defined in the reference files.
# Copy in parts of the reference files and modify as you please.

akka {

  # Loggers to register at boot time (akka.event.Logging$DefaultLogger logs
  # to STDOUT)
  loggers = ["akka.event.slf4j.Slf4jLogger"]

  # Log level used by the configured loggers (see "loggers") as soon
  # as they have been started; before that, see "stdout-loglevel"
  # Options: OFF, ERROR, WARNING, INFO, DEBUG
  loglevel = "DEBUG"

  # Log level for the very basic logger activated during ActorSystem startup.
  # This logger prints the log messages to stdout (System.out).
  # Options: OFF, ERROR, WARNING, INFO, DEBUG
  stdout-loglevel = "DEBUG"

  # Filter of log events that is used by the LoggingAdapter before
  # publishing log events to the eventStream.
  logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"

  actor {
    provider = "cluster"

    default-dispatcher {
      # Throughput for default Dispatcher, set to 1 for as fair as possible
      throughput = 10
    }
  }

  remote {
    # The port clients should connect to. Default is 2552.
    netty.tcp.port = 4711
  }
}
```
## 包括文件
有时，包含另一个配置文件可能很有用，例如，如果你有一个`application.conf`，具有所有与环境无关的设置，然后覆盖特定环境的某些设置。

使用`-Dconfig.resource=/dev.conf`指定系统属性将加载`dev.conf`文件，其中包括`application.conf`

- **dev.conf**

```
include "application"

akka {
  loglevel = "DEBUG"
}
```
在 HOCON 规范中解释了更高级的包含和替换机制。

## 配置日志记录
如果系统或配置属性`akka.log-config-on-start`设置为`on`，那么当 Actor 系统启动时，将在`INFO`级别记录完整配置。当你不确定使用了什么配置时，这很有用。

如果有疑问，你可以在使用配置对象构建 Actor 系统之前或之后检查它们：

```
Welcome to Scala 2.12 (Java HotSpot(TM) 64-Bit Server VM, Java 1.8.0).
Type in expressions to have them evaluated.
Type :help for more information.

scala> import com.typesafe.config._
import com.typesafe.config._

scala> ConfigFactory.parseString("a.b=12")
res0: com.typesafe.config.Config = Config(SimpleConfigObject({"a" : {"b" : 12}}))

scala> res0.root.render
res1: java.lang.String =
{
    # String: 1
    "a" : {
        # String: 1
        "b" : 12
    }
}
```

每个项目前面的注释给出了有关设置起点（文件和行号）的详细信息以及可能出现的注释，例如在参考配置中。合并引用和 Actor 系统的设置如下所示：

```java
final ActorSystem system = ActorSystem.create();
System.out.println(system.settings());
// this is a shortcut for system.settings().config().root().render()
```
## 关于类加载器的一句话
在配置文件的几个地方，可以指定由 Akka 实例化的某个对象的完全限定类名。这是使用 Java 反射完成的，Java 反射又使用类加载器。在应用程序容器或 OSGi 包等具有挑战性的环境中获得正确的方法并不总是很简单的，Akka 的当前方法是，每个`ActorSystem`实现存储当前线程的上下文类加载器（如果可用，否则只存储其自己的加载器，如`this.getClass.getClassLoader`）并将其用于所有反射访问。这意味着将 Akka 放在引导类路径上会从奇怪的地方产生`NullPointerException`：这是不支持的。

## 应用程序特定设置
配置也可用于特定于应用程序的设置。一个好的做法是将这些设置放在「[Extension](https://doc.akka.io/docs/akka/current/extending-akka.html#extending-akka-settings)」中。

### 配置多个 ActorSystem
如果你有多个`ActorSystem`（或者你正在编写一个库，并且有一个`ActorSystem`可能与应用程序的`ActorSystem`分离），那么你可能需要分离每个系统的配置。

考虑到`ConfigFactory.load()`从整个类路径中合并所有具有匹配名称的资源，利用该功能区分配置层次结构中的 Actor 系统是最容易：

```
myapp1 {
  akka.loglevel = "WARNING"
  my.own.setting = 43
}
myapp2 {
  akka.loglevel = "ERROR"
  app2.setting = "appname"
}
my.own.setting = 42
my.other.setting = "hello"
```

```java
val config = ConfigFactory.load()
val app1 = ActorSystem("MyApp1", config.getConfig("myapp1").withFallback(config))
val app2 = ActorSystem("MyApp2",
  config.getConfig("myapp2").withOnlyPath("akka").withFallback(config))
```

这两个示例演示了“提升子树（`lift-a-subtree`）”技巧的不同变化：在第一种情况下，从 Actor 系统中访问的配置是

```
akka.loglevel = "WARNING"
my.own.setting = 43
my.other.setting = "hello"
// plus myapp1 and myapp2 subtrees
```
在第二种情况下，只提升“akka”子树，结果如下

```
akka.loglevel = "ERROR"
my.own.setting = 42
my.other.setting = "hello"
// plus myapp1 and myapp2 subtrees
```
- **注释**：配置库非常强大，说明其所有功能超出了本文的范围。尤其不包括如何将其他配置文件包含在其他文件中（参见「[Including files](https://doc.akka.io/docs/akka/current/general/configuration.html#including-files)」中的一个小示例）以及通过路径替换复制配置树的部分。

在实例化`ActorSystem`时，还可以通过其他方式以编程方式指定和分析配置。

```java
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
val customConf = ConfigFactory.parseString("""
  akka.actor.deployment {
    /my-service {
      router = round-robin-pool
      nr-of-instances = 3
    }
  }
  """)
// ConfigFactory.load sandwiches customConfig between default reference
// config and default overrides, and then resolves it.
val system = ActorSystem("MySystem", ConfigFactory.load(customConf))
```

## 从自定义位置读取配置
你可以在代码或使用系统属性中替换或补充`application.conf`。

如果你使用的是`ConfigFactory.load()`（默认情况下 Akka 会这样做），那么可以通过定义`-Dconfig.resource=whatever`、`-Dconfig.file=whatever`或`-Dconfig.url=whatever`来替换`application.conf`。

在用`-Dconfig.resource`和`friends`指定的替换文件中，如果你仍然想使用`application.{conf,json,properties}`，可以使用`include "application"`。在`include "application"`之前指定的设置将被包含的文件覆盖，而在`include "application"`之后指定的设置将覆盖包含的文件。

在代码中，有许多自定义选项。

`ConfigFactory.load()`有几个重载；这些重载允许你指定一些夹在系统属性（重写）和默认值（来自`reference.conf`）之间的内容，替换常规`application.{conf,json,properties}`并替换` -Dconfig.file`和`friends`。

`ConfigFactory.load()`的最简单变体采用资源基名称（`resource basename`），而不是应用程序；然后将使用`myname.conf`、`myname.json`和`myname.properties`而不是`application.{conf,json,properties}`。

最灵活的变体采用`Config`对象，你可以使用`ConfigFactory`中的任何方法加载该对象。例如，你可以使用`ConfigFactory.parseString()`在代码中放置配置字符串，也可以制作映射和`ConfigFactory.parseMap()`，或者加载文件。

你还可以将自定义配置与常规配置结合起来，这可能看起来像：

```java
// make a Config with just your special setting
Config myConfig = ConfigFactory.parseString("something=somethingElse");
// load the normal config stack (system props,
// then application.conf, then reference.conf)
Config regularConfig = ConfigFactory.load();
// override regular stack with myConfig
Config combined = myConfig.withFallback(regularConfig);
// put the result in between the overrides
// (system props) and defaults again
Config complete = ConfigFactory.load(combined);
// create ActorSystem
ActorSystem system = ActorSystem.create("myname", complete);
```
使用`Config`对象时，请记住蛋糕中有三个“层”：

- `ConfigFactory.defaultOverrides()`（系统属性）
- 应用程序的设置
- `ConfigFactory.defaultReference()`（`reference.conf`）

通常的目标是定制中间层，而不让其他两层单独使用。

- `ConfigFactory.load()`加载整个堆栈
- `ConfigFactory.load()`的重载允许你指定不同的中间层
- `ConfigFactory.parse()`变体加载单个文件或资源

要堆叠两层，请使用`override.withFallback(fallback)`；尝试将系统属性（`defaultOverrides()`）保持在顶部，并将`reference.conf`（`defaultReference()`）保持在底部。

请记住，你通常可以在`application.conf`中添加另一个`include`语句，而不是编写代码。`application.conf`顶部的`include`将被`application.conf`的其余部分覆盖，而底部的`include`将覆盖前面的内容。

## Actor 部署配置
特定 Actor 的部署设置可以在配置的`akka.actor.deployment`部分中定义。在部署部分，可以定义调度程序、邮箱、路由器设置和远程部署等内容。这些功能的配置在详细介绍相应主题的章节中进行了描述。示例如下：

```
akka.actor.deployment {

  # '/user/actorA/actorB' is a remote deployed actor
  /actorA/actorB {
    remote = "akka.tcp://sampleActorSystem@127.0.0.1:2553"
  }
  
  # all direct children of '/user/actorC' have a dedicated dispatcher 
  "/actorC/*" {
    dispatcher = my-dispatcher
  }

  # all descendants of '/user/actorC' (direct children, and their children recursively)
  # have a dedicated dispatcher
  "/actorC/**" {
    dispatcher = my-dispatcher
  }
  
  # '/user/actorD/actorE' has a special priority mailbox
  /actorD/actorE {
    mailbox = prio-mailbox
  }
  
  # '/user/actorF/actorG/actorH' is a random pool
  /actorF/actorG/actorH {
    router = random-pool
    nr-of-instances = 5
  }
}

my-dispatcher {
  fork-join-executor.parallelism-min = 10
  fork-join-executor.parallelism-max = 10
}
prio-mailbox {
  mailbox-type = "a.b.MyPrioMailbox"
}
```
- **注释**：特定 Actor 的部署部分由 Actor 相对于`/user`的路径标识。

可以使用星号作为 Actor 路径部分的通配符匹配，因此可以指定：`/*/sampleActor`，它将匹配层次结构中该级别上的所有`sampleActor`。此外，请注意：

- 可以在最后一个位置使用通配符来匹配特定级别的所有 Actor：`/someparent/*`
- 可以在最后一个位置使用双通配符以递归方式匹配所有子 Actor 及其子参 Actor：`/someparent/**`
- 非通配符匹配总是比通配符具有更高的匹配优先级，并且单个通配符匹配比双通配符具有更高的优先级，因此：`/foo/bar`被认为比`/foo/*`更具体，后者被认为比`/foo/**`更具体，仅使用最高优先级匹配。
- 通配符不能用于部分匹配，如`/foo*/bar`、`/f*o/bar`等。
- 双通配符只能放在最后一个位置。

## 参考配置列表
每个 Akka 模块都有一个带有默认值的参考配置文件。

- [akka-actor](https://doc.akka.io/docs/akka/current/general/configuration.html#akka-actor)
- [akka-agent](https://doc.akka.io/docs/akka/current/general/configuration.html#akka-agent)
- [akka-camel](https://doc.akka.io/docs/akka/current/general/configuration.html#akka-camel)
- [akka-cluster](https://doc.akka.io/docs/akka/current/general/configuration.html#akka-cluster)
- [akka-multi-node-testkit](https://doc.akka.io/docs/akka/current/general/configuration.html#akka-multi-node-testkit)
- [akka-persistence](https://doc.akka.io/docs/akka/current/general/configuration.html#akka-persistence)
- [akka-remote](https://doc.akka.io/docs/akka/current/general/configuration.html#akka-remote)
- [akka-remote (artery)](https://doc.akka.io/docs/akka/current/general/configuration.html#akka-remote-artery-)
- [akka-testkit](https://doc.akka.io/docs/akka/current/general/configuration.html#akka-testkit)
- [akka-cluster-metrics](https://doc.akka.io/docs/akka/current/general/configuration.html#akka-cluster-metrics)
- [akka-cluster-tools](https://doc.akka.io/docs/akka/current/general/configuration.html#akka-cluster-tools)
- [akka-cluster-sharding](https://doc.akka.io/docs/akka/current/general/configuration.html#akka-cluster-sharding)
- [akka-distributed-data](https://doc.akka.io/docs/akka/current/general/configuration.html#akka-distributed-data)


----------

**英文原文链接**：[Configuration](https://doc.akka.io/docs/akka/current/general/configuration.html).


----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————