# 第 2 部分: 创建第一个 Actor
## 依赖
在你的项目中添加如下依赖：

```xml
<!-- Maven -->
<dependency>
  <groupId>com.typesafe.akka</groupId>
  <artifactId>akka-actor_2.11</artifactId>
  <version>2.5.19</version>
</dependency>

<!-- Gradle -->
dependencies {
  compile group: 'com.typesafe.akka', name: 'akka-actor_2.11', version: '2.5.19'
}

<!-- sbt -->
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.5.19"
```

## 简介
随着对 Actor 层次结构和行为的理解，剩下的问题是如何将物联网（`IoT`）系统的顶级组件映射到 Actor。让代表设备和仪表盘的 Actor 处于顶层是很有吸引力的。相反，我们建议创建一个表示整个应用程序的显式组件。换句话说，我们的物联网系统中只有一个顶级的 Actor。创建的管理设备和仪表板的组件将是此 Actor 的子 Actor。这允许我们将示例用例的体系结构图重构为 Actor 树：

![actor-tree](https://github.com/guobinhit/akka-guide/blob/master/images/getting-started-guide/tutorial_2/actor-tree.png)

我们可以用几行简单的代码定义第一个 Actor，即`IotSupervisor`。为了开始你的教程应用程序：

- 在适当的包路径下创建新的`IotSupervisor`源文件，例如在`com.example`包中；
- 将以下代码粘贴到新文件中以定义`IotSupervisor`。

```java
package com.example;

import akka.actor.AbstractActor;
import akka.actor.ActorLogging;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class IotSupervisor extends AbstractActor {
  private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  public static Props props() {
    return Props.create(IotSupervisor.class, IotSupervisor::new);
  }

  @Override
  public void preStart() {
    log.info("IoT Application started");
  }

  @Override
  public void postStop() {
    log.info("IoT Application stopped");
  }

  // No need to handle any messages
  @Override
  public Receive createReceive() {
    return receiveBuilder()
            .build();
  }
}
```
代码类似于我们在前面的实验中使用的 Actor 示例，但请注意：

- 我们使用的不是`println()`，而是`akka.event.Logging`，它直接调用了 Akka 的内置日志功能。
- 我们使用推荐的模式来创建 Actor，即通过在 Actor 内部定义`props()`静态方法来创建 Actor。

要提供创建 Actor 系统的主入口点，请将以下代码添加到新的`IotMain`类中。

```java
package com.example;

import java.io.IOException;

import akka.actor.ActorSystem;
import akka.actor.ActorRef;

public class IotMain {

  public static void main(String[] args) throws IOException {
    ActorSystem system = ActorSystem.create("iot-system");

    try {
      // Create top level supervisor
      ActorRef supervisor = system.actorOf(IotSupervisor.props(), "iot-supervisor");

      System.out.println("Press ENTER to exit the system");
      System.in.read();
    } finally {
      system.terminate();
    }
  }
}
```
这个应用程序除了打印它的启动信息之外，几乎没有任何作用。但是，我们已经完成了第一个 Actor 的创建工作，并且准备好添加其他 Actor 了。

## 下一步是什么？
在下面的章节中，我们将通过以下方式逐步扩展应用程序：

- 创建设备的表示。
- 创建设备管理组件。
- 向设备组添加查询功能。

----------

**英文原文链接**：[Part 2: Creating the First Actor](https://doc.akka.io/docs/akka/current/guide/tutorial_2.html).

----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————