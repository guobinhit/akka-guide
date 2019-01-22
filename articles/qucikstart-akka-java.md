# 快速入门 Akka Java 指南

Akka 是一个用于在 JVM 上构建高并发、分布式和容错的事件驱动应用程序的运行时工具包。Akka 既可以用于 Java，也可以用于 Scala。本指南通过描述 Java 版本的`Hello World`示例来介绍 Akka。如果你喜欢将 Akka 与 Scala 结合使用，请切换到「[快速入门 Akka Scala 指南](https://developer.lightbend.com/guides/akka-quickstart-scala/)」。

Actors 是 Akka 的执行单元。Actor 模型是一种抽象，它让编写正确的并发、并行和分布式系统更容易。`Hello World`示例说明了 Akka 的基础知识。在 30 分钟内，你应该能够下载并运行示例，并使用本指南了解示例是如何构造的。这会让你初步了解 Akka 的魅力，希望这能够让你拥有深入了解 Akka 的兴趣（`This will get your feet wet, and hopefully inspire you to dive deeper into the wonderful sea of Akka`）！

在体验过这个示例之后，想深入了解 Akka，阅读「[Getting Started Guide](https://doc.akka.io/docs/akka/2.5/guide/introduction.html?language=java)」是一个很好的选择。

## 下载示例
Java 版本的`Hello World`示例是一个包含 Maven 和 Gradle 构建文件的压缩项目。你可以在 Linux、MacOS 或 Windows 上运行它。唯一的先决条件是安装 Java 8 和 Maven 或 Gradle。

下载和解压示例：

- 在「[Lightbend Tech Hub](https://developer.lightbend.com/start/?group=akka&project=akka-quickstart-java)」上通过点击`CREATE A PROJECT FOR ME`下载压缩文件。
- 将 ZIP 文件解压缩到方便的位置：  
  - 在 Linux 和 OSX 系统上，打开终端并使用命令`unzip akka-quickstart-java.zip`。
  - 在 Windows 上，使用文件资源管理器等工具提取项目。

## 运行示例
确保你已经安装了构建工具，然后打开终端窗口，并从项目目录中键入以下命令以运行`Hello World`：

```
//  Maven
$ mvn compile exec:exec

// Grade
$ gradle run
```

输出应该如下所示（一直向右滚动以查看 Actor 输出）：

```
// Maven
[INFO] Scanning for projects...
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] Building app 1.0
[INFO] ------------------------------------------------------------------------
[INFO]
[INFO] --- exec-maven-plugin:1.6.0:exec (default-cli) @ app ---
>>> Press ENTER to exit <<<
[INFO] [05/11/2017 14:07:20.790] [helloakka-akka.actor.default-dispatcher-2] [akka://helloakka/user/printerActor] Hello, Java
[INFO] [05/11/2017 14:07:20.791] [helloakka-akka.actor.default-dispatcher-2] [akka://helloakka/user/printerActor] Good day, Play
[INFO] [05/11/2017 14:07:20.791] [helloakka-akka.actor.default-dispatcher-2] [akka://helloakka/user/printerActor] Howdy, Akka
[INFO] [05/11/2017 14:07:20.791] [helloakka-akka.actor.default-dispatcher-2] [akka://helloakka/user/printerActor] Howdy, Lightbend

// Grade
:compileJava UP-TO-DATE
:processResources NO-SOURCE
:classes UP-TO-DATE
:run
>>> Press ENTER to exit <<<
[INFO] [05/11/2017 14:08:22.884] [helloakka-akka.actor.default-dispatcher-2] [akka://helloakka/user/printerActor] Howdy, Akka
[INFO] [05/11/2017 14:08:22.884] [helloakka-akka.actor.default-dispatcher-2] [akka://helloakka/user/printerActor] Good day, Play
[INFO] [05/11/2017 14:08:22.884] [helloakka-akka.actor.default-dispatcher-2] [akka://helloakka/user/printerActor] Hello, Java
[INFO] [05/11/2017 14:08:22.884] [helloakka-akka.actor.default-dispatcher-2] [akka://helloakka/user/printerActor] Howdy, Lightbend
<=========----> 75% EXECUTING
> :run
```

恭喜你，你刚刚运行了你的第一个 Akka 应用程序。

## Hello World 都做了什么？
正如你在控制台输出中看到的，该示例输出了一些问候语。让我们看看运行时都发生了什么。

![mainclass-actorsystem](https://github.com/guobinhit/akka-guide/blob/master/images/qucikstart-akka-java/mainclass-actorsystem.png)

首先，主函数`main`创建了一个`akka.actor.ActorSystem`，它是一个运行`Actors`的容器。接下来，它创建了三个`Greeter Actor`实例和一个`Printer Actor`实例。

然后，该示例将消息发送给`Greeter Actor`实例，后者在内部存储消息。最后，发送给`Greeter Actor`的指令消息触发它们向`Printer Actor`发送消息，`Printer Actor`将消息输出到控制台：

![mainclass-hhgp](https://github.com/guobinhit/akka-guide/blob/master/images/qucikstart-akka-java/mainclass-hhgp.png)

Akka 对 Actors 和异步消息传递的使用带来了一系列好处。大家可以考虑一下都带来了什么好处？

## 使用 Actor 模型的好处
Akka 的以下特性使你能够以直观的方式解决困难的并发性和可伸缩性挑战：

- 事件驱动模型：`Event-driven model`，Actors 通过响应消息来执行工作。Actors 之间的通信是异步的，允许 Actors 发送消息并继续自己的工作，而不是阻塞等待响应。
- 强隔离原则：`Strong isolation principles`，与 Java 中的常规对象不同，Actors 在调用的方法方面，没有一个公共 API。相反，它的公共 API 是通过 Actors 处理的消息来定义的。这可以防止 Actors 之间共享状态；观察另一个 Actors 状态的唯一方法是向其发送请求状态的消息。
- 位置透明：`Location transparency`，系统通过工厂方法构造 Actors 并返回对实例的引用。因为位置无关紧要，所以 Actors 实例可以启动、停止、移动和重新启动，以向上和向下扩展以及从意外故障中恢复。
- 轻量级：`Lightweight`，每个实例只消耗几百个字节，这实际上允许数百万并发 Actors 存在于一个应用程序中。

让我们看看在`Hello World`示例中使用 Actors 和消息一起工作的一些最佳实践。

## 定义 Actors 和消息

消息可以是任意类型（`Object`的任何子类型），你可以将装箱类型（如`String`、`Integer`、`Boolean`等）作为消息发送，也可以将普通数据结构（如数组和集合类型）作为消息发送。

`Hello World`的 Actors 使用三种不同的消息：

- `WhoToGreet`：问候消息的接受者；
- `Greet`：执行问候的指令；
- `Greeting`：包含问候语的消息。

在定义 Actors 及其消息时，请记住以下建议：

- 因为消息是 Actor 的公共 API，所以定义具有良好名称、丰富语义和特定于域的含义的消息是一个很好的实践，即使它们只是包装你的数据类型，这将使基于 Actor 的系统更容易使用、理解和调试。
- 消息应该是不可变的，因为它们在不同的线程之间共享。
- 将 Actor 的关联消息作为静态类放在 Actor 的类中是一个很好的实践，这使得理解 Actor 期望和处理的消息类型更加容易。
- 在 Actor 类中使用静态`props`方法来描述如何构造 Actor 也是一种常见的模式。

让我们看看 Actor 如何实现`Greeter`和`Printer`来演示这些最佳实践。

###  Greeter Actor
下面的代码段来自于`Greeter.java`，其实现了`Greeter Actor`：

```java
package com.lightbend.akka.sample;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import com.lightbend.akka.sample.Printer.Greeting;

public class Greeter extends AbstractActor {
  static public Props props(String message, ActorRef printerActor) {
    return Props.create(Greeter.class, () -> new Greeter(message, printerActor));
  }

  static public class WhoToGreet {
    public final String who;

    public WhoToGreet(String who) {
        this.who = who;
    }
  }

  static public class Greet {
    public Greet() {
    }
  }

  private final String message;
  private final ActorRef printerActor;
  private String greeting = "";

  public Greeter(String message, ActorRef printerActor) {
    this.message = message;
    this.printerActor = printerActor;
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(WhoToGreet.class, wtg -> {
          this.greeting = message + ", " + wtg.who;
        })
        .match(Greet.class, x -> {
          printerActor.tell(new Greeting(greeting), getSelf());
        })
        .build();
  }
}
```

让我们来解析上面的功能：

- `Greeter`类扩展了`akka.actor.AbstractActor`类并实现了`createReceive`方法。
- `Greeter`构造函数接受两个参数：`String message`，它将在构建问候语时使用，`ActorRef printerActor`是处理问候语输出的 Actor 的引用。
- `receiveBuilder`定义了行为；Actor 应该如何响应它接收到的不同消息。Actor 可以有状态。访问或改变 Actor 的内部状态是线程安全的，因为它受 Actor 模型的保护。`createReceive`方法应处理 Actor 期望的消息。对于`Greeter`，它需要两种类型的消息：`WhoToGreet`和`Greet`，前者将更新 Actor 的问候语状态，后者将触发向`Printer Actor`发送问候语。
- `greeting`变量包含 Actor 的状态，默认设置为`""`。
- 静态`props`方法创建并返回`Props`实例。`Props`是一个配置类，用于指定创建 Actor 的选项，将其视为不可变的，因此可以自由共享用于创建可以包含相关部署信息的 Actor 的方法。这个例子简单地传递了 Actor 在构造时需要的参数。我们将在本教程的后面部分看到`props`方法的实际应用。

### Printer Actor
`Printer`的实现非常简单：

- 它通过`Logging.getLogger(getContext().getSystem(), this);`创建一个日志器。通过这样做，我们可以在 Actor 中编写`log.info() `，而不需要任何额外的连接。
- 它只处理一种类型的消息`Greeting`，并记录该消息的内容。

```java
package com.lightbend.akka.sample;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class Printer extends AbstractActor {
  static public Props props() {
    return Props.create(Printer.class, () -> new Printer());
  }

  static public class Greeting {
    public final String message;

    public Greeting(String message) {
      this.message = message;
    }
  }

  private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  public Printer() {
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(Greeting.class, greeting -> {
            log.info(greeting.message);
        })
        .build();
  }
}
```
## 创建 Actors
到目前为止，我们已经研究了 Actors 的定义和他们的消息。现在，让我们更深入地了解位置透明（`location transparency`）的好处，看看如何创建 Actor 实例。

### 位置透明的好处
在 Akka 中，不能使用`new`关键字创建 Actor 的实例。相反，你应该使用工厂方法创建 Actor 实例。工厂不返回 Actor 实例，而是返回指向 Actor 实例的引用`akka.actor.ActorRef`。在分布式系统中，这种间接创建实例的方法增加了很多好处和灵活性。

在 Akka 中位置无关紧要。位置透明性意味着，无论是在正在运行 Actor 的进程内，还是运行在远程计算机上，`ActorRef`都可以保持相同语义。如果需要，运行时可以通过在更改 Actor 的位置或整个应用程序拓扑来优化系统。这就启用了故障管理的“让它崩溃（`let it crash`）”模型，在该模型中，系统可以通过销毁有问题的 Actor 和重新启动健康的 Actor 来自我修复。

### Akka ActorSystem
`akka.actor.ActorSystem`工厂在某种程度上类似于 Spring 的 BeanFactory，它是运行 Actors 的容器并管理他们的生命周期。`actorOf`工厂方法创建 Actors 并接受两个参数，一个名为`props`的配置对象和一个`String`类型的 Actor 名称。

Actor 和 ActorSystem 的名字在 Akka 中很重要。例如，使用它们进行查找。使用与你的域模型（`domain model`）一致的有意义的名称可以更容易地对它们进行推理。

前面我们看了`Hello World`的 Actors 定义。现在，让我们看看`AkkaQuickstart.java`文件中创建 Greeter Actor 和 Printer Actor 实例的代码：

```java
final ActorRef printerActor = 
  system.actorOf(Printer.props(), "printerActor");
final ActorRef howdyGreeter = 
  system.actorOf(Greeter.props("Howdy", printerActor), "howdyGreeter");
final ActorRef helloGreeter = 
  system.actorOf(Greeter.props("Hello", printerActor), "helloGreeter");
final ActorRef goodDayGreeter = 
  system.actorOf(Greeter.props("Good day", printerActor), "goodDayGreeter");
```
注意以下事项：

- 使用 ActorSystem 上的`actorOf`方法创建 Printer Actor。正如我们在前面讨论的，它使用了`Printer`类的静态`props`方法来获取`Props`值。ActorRef 提供了对新创建的 Printer Actor 实例的引用。
- 对于`Greeter`，代码创建三个 Actor 实例，每个实例都有一个特定的问候语。

**注意**：在本例中，Greeter Actors 都使用了相同的 Printer 实例，但我们可以创建多个 Printer Actor 实例。示例中使用一个实例来说明稍后我们将讨论的消息传递（`message passing`）的一个重要概念。

接下来，我们来看看如何与 Actors 通信。

## 异步通信
 Actors 是被动的和消息驱动的。Actor 在收到消息前什么都不做。Actors 使用异步消息进行通信。这样可以确保发送者不会一直等待接收者处理他们的消息。相反，发件人将邮件放在收件人的邮箱之后，就可以自由地进行其他工作。Actor 的邮箱本质上是一个具有排序语义的消息队列。从同一个参与者发送的多条消息的顺序被保留，但可以与另一个 Actor 发送的消息交错。

你可能想知道 Actor 在不处理消息的时候在做什么，比如，做什么实际的工作？实际上，它处于挂起状态，在这种状态下，它不消耗除内存之外的任何资源。同样，这也展示了 Actors 的轻量级和高效性。

### 给 Actor 发生消息
要将消息放入 Actor 的邮箱，我们需要使用`ActorRef`的`tell`方法。例如，`Hello World`的主函数`main`向 Greeter Actor 发送如下消息：

```java
howdyGreeter.tell(new WhoToGreet("Akka"), ActorRef.noSender());
howdyGreeter.tell(new Greet(), ActorRef.noSender());

howdyGreeter.tell(new WhoToGreet("Lightbend"), ActorRef.noSender());
howdyGreeter.tell(new Greet(), ActorRef.noSender());

helloGreeter.tell(new WhoToGreet("Java"), ActorRef.noSender());
helloGreeter.tell(new Greet(), ActorRef.noSender());

goodDayGreeter.tell(new WhoToGreet("Play"), ActorRef.noSender());
goodDayGreeter.tell(new Greet(), ActorRef.noSender());
```
Greeter Actor  也向 Printer Actor 发送消息：

```java
printerActor.tell(new Greeting(greeting), getSelf());
```
我们已经研究了如何创建 Actor 和发送消息。现在，让我们来回顾一下`Main`类的全部内容。

## Main class

`Hello World`的 `Main`类创建和控制 Actors。注意，使用`ActorSystem`作为容器，并使用`actorOf`方法创建 Actors。最后，类创建要发送给 Actors 的消息。

```java
package com.lightbend.akka.sample;

import java.io.IOException;

import com.lightbend.akka.sample.Greeter.Greet;
import com.lightbend.akka.sample.Greeter.WhoToGreet;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

public class AkkaQuickstart {
  public static void main(String[] args) {
    final ActorSystem system = ActorSystem.create("helloakka");
    try {
      final ActorRef printerActor = 
        system.actorOf(Printer.props(), "printerActor");
      final ActorRef howdyGreeter = 
        system.actorOf(Greeter.props("Howdy", printerActor), "howdyGreeter");
      final ActorRef helloGreeter = 
        system.actorOf(Greeter.props("Hello", printerActor), "helloGreeter");
      final ActorRef goodDayGreeter = 
        system.actorOf(Greeter.props("Good day", printerActor), "goodDayGreeter");

      howdyGreeter.tell(new WhoToGreet("Akka"), ActorRef.noSender());
      howdyGreeter.tell(new Greet(), ActorRef.noSender());

      howdyGreeter.tell(new WhoToGreet("Lightbend"), ActorRef.noSender());
      howdyGreeter.tell(new Greet(), ActorRef.noSender());

      helloGreeter.tell(new WhoToGreet("Java"), ActorRef.noSender());
      helloGreeter.tell(new Greet(), ActorRef.noSender());

      goodDayGreeter.tell(new WhoToGreet("Play"), ActorRef.noSender());
      goodDayGreeter.tell(new Greet(), ActorRef.noSender());

      System.out.println(">>> Press ENTER to exit <<<");
      System.in.read();
    } catch (IOException ioe) {
    } finally {
      system.terminate();
    }
  }
}
```
类似地，让我们再次看看定义 Actor 和他们接受的消息的完整源代码。

## 完整代码
下面是创建示例应用程序的三个类`Greeter`、`Printer`和`AkkaQuickstart`的完整源代码：

### Greater.java

```java
package com.lightbend.akka.sample;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import com.lightbend.akka.sample.Printer.Greeting;

public class Greeter extends AbstractActor {
  static public Props props(String message, ActorRef printerActor) {
    return Props.create(Greeter.class, () -> new Greeter(message, printerActor));
  }

  static public class WhoToGreet {
    public final String who;

    public WhoToGreet(String who) {
        this.who = who;
    }
  }

  static public class Greet {
    public Greet() {
    }
  }

  private final String message;
  private final ActorRef printerActor;
  private String greeting = "";

  public Greeter(String message, ActorRef printerActor) {
    this.message = message;
    this.printerActor = printerActor;
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(WhoToGreet.class, wtg -> {
          this.greeting = message + ", " + wtg.who;
        })
        .match(Greet.class, x -> {
          printerActor.tell(new Greeting(greeting), getSelf());
        })
        .build();
  }
}
```
### Printer.java
```java
package com.lightbend.akka.sample;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class Printer extends AbstractActor {
  static public Props props() {
    return Props.create(Printer.class, () -> new Printer());
  }

  static public class Greeting {
    public final String message;

    public Greeting(String message) {
      this.message = message;
    }
  }

  private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  public Printer() {
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(Greeting.class, greeting -> {
            log.info(greeting.message);
        })
        .build();
  }
}
```
### AkkaQuickstart.java
```java
package com.lightbend.akka.sample;

import java.io.IOException;

import com.lightbend.akka.sample.Greeter.Greet;
import com.lightbend.akka.sample.Greeter.WhoToGreet;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

public class AkkaQuickstart {
  public static void main(String[] args) {
    final ActorSystem system = ActorSystem.create("helloakka");
    try {
      final ActorRef printerActor = 
        system.actorOf(Printer.props(), "printerActor");
      final ActorRef howdyGreeter = 
        system.actorOf(Greeter.props("Howdy", printerActor), "howdyGreeter");
      final ActorRef helloGreeter = 
        system.actorOf(Greeter.props("Hello", printerActor), "helloGreeter");
      final ActorRef goodDayGreeter = 
        system.actorOf(Greeter.props("Good day", printerActor), "goodDayGreeter");

      howdyGreeter.tell(new WhoToGreet("Akka"), ActorRef.noSender());
      howdyGreeter.tell(new Greet(), ActorRef.noSender());

      howdyGreeter.tell(new WhoToGreet("Lightbend"), ActorRef.noSender());
      howdyGreeter.tell(new Greet(), ActorRef.noSender());

      helloGreeter.tell(new WhoToGreet("Java"), ActorRef.noSender());
      helloGreeter.tell(new Greet(), ActorRef.noSender());

      goodDayGreeter.tell(new WhoToGreet("Play"), ActorRef.noSender());
      goodDayGreeter.tell(new Greet(), ActorRef.noSender());

      System.out.println(">>> Press ENTER to exit <<<");
      System.in.read();
    } catch (IOException ioe) {
    } finally {
      system.terminate();
    }
  }
}
```
作为另一个最佳实践，我们应该提供一些单元测试。

## 测试 Actors
`Hello World`示例中的测试展示了 JUnit 框架的使用。虽然测试的覆盖范围不完整，但它简单地展示了测试 Actor 代码是多么的容易，并提供了一些基本概念。你可以把它作为一个练习来增加你自己的知识。

测试类使用的是`akka.test.javadsl.TestKit`，它是用于 Actor 和 Actor 系统集成测试的模块。这个类只使用了`TestKit`提供的一部分功能。

集成测试可以帮助我们确保 Actors 的行为是异步的。第一个测试使用`TestKit`探针来询问和验证预期的行为。让我们看看源代码片段：

```java
package com.lightbend.akka.sample;

import static org.junit.Assert.assertEquals;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.lightbend.akka.sample.Greeter.Greet;
import com.lightbend.akka.sample.Greeter.WhoToGreet;
import com.lightbend.akka.sample.Printer.Greeting;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

public class AkkaQuickstartTest {
    static ActorSystem system;

    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
    }

    @AfterClass
    public static void teardown() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    @Test
    public void testGreeterActorSendingOfGreeting() {
        final TestKit testProbe = new TestKit(system);
        final ActorRef helloGreeter = system.actorOf(Greeter.props("Hello", testProbe.getRef()));
        helloGreeter.tell(new WhoToGreet("Akka"), ActorRef.noSender());
        helloGreeter.tell(new Greet(), ActorRef.noSender());
        Greeting greeting = testProbe.expectMsgClass(Greeting.class);
        assertEquals("Hello, Akka", greeting.message);
    }
}
```

一旦我们引用了`TestKit`探针，我们就将它的`ActorRef`作为构造函数参数的一部分传递给`Greeter`。然后，我们向`Greeter`发送两条信息：一条是设置问候语，另一条是触发`Greeting`的发送。`TestKit`的`expectMsg`方法验证是否发送了消息。

示例代码只涉及了`TestKit`功能的一小部分，在「[这里](https://doc.akka.io/docs/akka/current/testing.html?language=java)」可以找到更完整的概述。

现在我们已经检查了所有代码。让我们再次运行该示例并查看其输出。

## 运行应用程序
你可以通过命令行或者 IDE 来运行`Hello World`应用程序。在本指南的最后一个主题，我们描述了如何在 IntelliJ IDEA 中运行该示例。但是，在我们再次运行应用程序之前，让我们先快速的查看构建文件。

 - Maven POM 文件

```xml
<project>
    <modelVersion>4.0.0</modelVersion>

    <groupId>hello-akka-java</groupId>
    <artifactId>app</artifactId>
    <version>1.0</version>

    <properties>
      <akka.version>$akka_version$</akka.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>com.typesafe.akka</groupId>
            <artifactId>akka-actor_2.12</artifactId>
            <version>\${akka.version}</version>
        </dependency>
        <dependency>
            <groupId>com.typesafe.akka</groupId>
            <artifactId>akka-testkit_2.12</artifactId>
            <version>\${akka.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>junit</groupId>
            <artifactId>junit</artifactId>
            <version>4.12</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.5.1</version>
                <configuration>
                    <source>1.8</source>
                    <target>1.8</target>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>1.6.0</version>
                <configuration>
                    <executable>java</executable>
                    <arguments>
                        <argument>-classpath</argument>
                        <classpath />
                        <argument>com.lightbend.akka.sample.AkkaQuickstart</argument>
                    </arguments>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```
- Grade 构建文件

```grade
apply plugin: 'java'
apply plugin: 'idea'
apply plugin: 'application'


repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
  compile 'com.typesafe.akka:akka-actor_2.12:$akka_version$'
  testCompile 'com.typesafe.akka:akka-testkit_2.12:$akka_version$'
  testCompile 'junit:junit:4.12'
}

mainClassName = "com.lightbend.akka.sample.AkkaQuickstart"

run {
  standardInput = System.in
}
```
### 运行项目
和前面一样，从控制台运行应用程序：

```
//  Maven
$ mvn compile exec:exec

// Grade
$ gradle run
```

输出应该如下所示（一直向右滚动以查看 Actor 输出）：

```
// Maven
[INFO] Scanning for projects...
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] Building app 1.0
[INFO] ------------------------------------------------------------------------
[INFO]
[INFO] --- exec-maven-plugin:1.6.0:exec (default-cli) @ app ---
>>> Press ENTER to exit <<<
[INFO] [05/11/2017 14:07:20.790] [helloakka-akka.actor.default-dispatcher-2] [akka://helloakka/user/printerActor] Hello, Java
[INFO] [05/11/2017 14:07:20.791] [helloakka-akka.actor.default-dispatcher-2] [akka://helloakka/user/printerActor] Good day, Play
[INFO] [05/11/2017 14:07:20.791] [helloakka-akka.actor.default-dispatcher-2] [akka://helloakka/user/printerActor] Howdy, Akka
[INFO] [05/11/2017 14:07:20.791] [helloakka-akka.actor.default-dispatcher-2] [akka://helloakka/user/printerActor] Howdy, Lightbend

// Grade
:compileJava UP-TO-DATE
:processResources NO-SOURCE
:classes UP-TO-DATE
:run
>>> Press ENTER to exit <<<
[INFO] [05/11/2017 14:08:22.884] [helloakka-akka.actor.default-dispatcher-2] [akka://helloakka/user/printerActor] Howdy, Akka
[INFO] [05/11/2017 14:08:22.884] [helloakka-akka.actor.default-dispatcher-2] [akka://helloakka/user/printerActor] Good day, Play
[INFO] [05/11/2017 14:08:22.884] [helloakka-akka.actor.default-dispatcher-2] [akka://helloakka/user/printerActor] Hello, Java
[INFO] [05/11/2017 14:08:22.884] [helloakka-akka.actor.default-dispatcher-2] [akka://helloakka/user/printerActor] Howdy, Lightbend
<=========----> 75% EXECUTING
> :run
```
还记得我们设置 Printer Actor 使用 Akka 的 Logger 吗？这就是为什么我们记录东西时会有很多额外的信息。日志输出包含诸如何时和从哪个 Actor 记录日志之类的信息。现在，让我们将重点放在 Printer Actor 的输出上：

```
... Howdy, Akka
... Hello, Java
... Good day, Play
... Howdy, Lightbend
```
这是我们的代码向 Greeter Actor 发送消息的结果：

```java
howdyGreeter.tell(new WhoToGreet("Akka"), ActorRef.noSender());
howdyGreeter.tell(new Greet(), ActorRef.noSender());

howdyGreeter.tell(new WhoToGreet("Lightbend"), ActorRef.noSender());
howdyGreeter.tell(new Greet(), ActorRef.noSender());

helloGreeter.tell(new WhoToGreet("Java"), ActorRef.noSender());
helloGreeter.tell(new Greet(), ActorRef.noSender());

goodDayGreeter.tell(new WhoToGreet("Play"), ActorRef.noSender());
goodDayGreeter.tell(new Greet(), ActorRef.noSender());
```

为了执行单元测试，我们输入`test`命令：

```
//  Maven
$ mvn test

// Grade
$ gradle test
```
尝试多次运行代码，并观察日志的顺序，你注意到它们的输入顺序发生变化了吗？这里发生了什么？异步行为变得很明显。这可能是你的一个新思维模式。但是，一旦你获得了使用它的经验，一切都会变得清晰；就像「[Neo in the Matrix](https://en.wikipedia.org/wiki/Neo_(The_Matrix))」一样。

### 下一步
如果你使用 IntelliJ，请尝试将示例项目与 IntelliJ IDEA 集成。

想要继续了解更多有关 Akka 和 Actor Systems 的信息，请参阅「[Getting Started Guide](https://doc.akka.io/docs/akka/2.5/guide/introduction.html?language=java)」，欢迎你加入我们！

## IntelliJ IDEA

JetBrains 的 IntelliJ 是 Java/Scala 社区中领先的 IDE 之一，它对 Akka 有着极好的支持。本节将指导你完成示例项目的设置、测试和运行。

### 设置项目
设置项目很简单。打开 IntelliJ 并选择`File -> Open...`并指向你安装示例项目的目录。

### 检查项目代码

如果我们打开文件`src/main/java/com/lightbend/akka/sample/HelloAkka.java`，我们将看到许多行以`//# ...`开头，作为文档的注释。为了从源代码中去掉这些行，我们可以在 IntelliJ 中使用出色的查找/替换功能。选择`Edit -> Find -> Replace in Path...`，选中`Regex`框并添加`[//#].*`正则表达式，然后单击查找窗口中的`Replace in Find Window...`。选择想要替换的内容，并对所有文件重复此操作。

### 测试和运行

对于测试，我们只需右键单击文件`src/test/java/com/lightbend/akka/sample/HelloAkkaTest.java`，然后选择`Run 'HelloAkkaTest'`。

类似地运行应用程序，我们右击文件`src/main/java/com/lightbend/akka/sample/HelloAkka.java`，并选择`Run 'HelloAkka.main()'`。

有关更多详细信息，请参阅「[运行应用程序](#运行应用程序)」部分。

想要进一步了解 IntelliJ IDEA，可以参阅「[史上最简单的 IntelliJ IDEA 教程](https://github.com/guobinhit/intellij-idea-tutorial)」系列文章。

----------

**英文原文链接**：[Akka Quickstart with Java](https://developer.lightbend.com/guides/akka-quickstart-java/index.html).

----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————

