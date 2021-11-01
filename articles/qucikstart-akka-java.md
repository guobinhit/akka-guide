# 快速入门 Akka Java 指南

Akka 是一个用于在 JVM 上构建高并发、分布式和可容错的事件驱动应用程序的运行时工具包。Akka 既可以用于 Java，也可以用于 Scala。本指南通过描述 Java 版本的`Hello World`示例来介绍 Akka。如果你喜欢将 Akka 与 Scala 结合使用，请切换到「[快速入门 Akka Scala 指南](https://github.com/guobinhit/akka-guide/blob/master/articles/qucikstart-akka-scala.md)」。

Actors 是 Akka 的执行单元。Actor 模型是一种抽象，它让编写正确的并发、并行和分布式系统更加容易。`Hello World`示例说明了 Akka 的基础知识。在 30 分钟内，你应该能够下载并运行示例，并使用本指南了解示例是如何构造的。这会让你初步了解 Akka 的魅力，希望这能够让你拥有深入了解 Akka 的兴趣！

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
Scanning for projects...
[INFO]
[INFO] ------------------------< hello-akka-java:app >-------------------------
[INFO] Building app 1.0
[INFO] --------------------------------[ jar ]---------------------------------
[INFO]
[INFO] --- maven-resources-plugin:2.6:resources (default-resources) @ app ---
[WARNING] Using platform encoding (UTF-8 actually) to copy filtered resources, i.e. build is platform dependent!
[INFO]
[INFO] --- exec-maven-plugin:1.6.0:exec (default-cli) @ app ---
[2019-10-12 09:20:30,248] [INFO] [akka.event.slf4j.Slf4jLogger] [helloakka-akka.actor.default-dispatcher-3] [] -
Slf4jLogger started
SLF4J: A number (1) of logging calls during the initialization phase have been intercepted and are
SLF4J: now being replayed. These are subject to the filtering rules of the underlying logging system.
SLF4J: See also http://www.slf4j.org/codes.html#replay
>>> Press ENTER to exit <<<
[2019-10-12 09:20:30,288] [INFO] [com.lightbend.akka.sample.Greeter] [helloakka-akka.actor.default-dispatcher-6]
[akka://helloakka/user/greeter] - Hello Charles!
[2019-10-12 09:20:30,290] [INFO] [com.lightbend.akka.sample.GreeterBot] [helloakka-akka.actor.default-dispatcher-3]
[akka://helloakka/user/Charles] - Greeting 1 for Charles
[2019-10-12 09:20:30,291] [INFO] [com.lightbend.akka.sample.Greeter] [helloakka-akka.actor.default-dispatcher-6]
[akka://helloakka/user/greeter] - Hello Charles!
[2019-10-12 09:20:30,291] [INFO] [com.lightbend.akka.sample.GreeterBot] [helloakka-akka.actor.default-dispatcher-3]
[akka://helloakka/user/Charles] - Greeting 2 for Charles
[2019-10-12 09:20:30,291] [INFO] [com.lightbend.akka.sample.Greeter] [helloakka-akka.actor.default-dispatcher-6]
[akka://helloakka/user/greeter] - Hello Charles!
[2019-10-12 09:20:30,291] [INFO] [com.lightbend.akka.sample.GreeterBot] [helloakka-akka.actor.default-dispatcher-3]
[akka://helloakka/user/Charles] - Greeting 3 for Charles

// Grade
:run 
[2019-10-12 09:47:16,399] [INFO] [akka.event.slf4j.Slf4jLogger] [helloakka-akka.actor.default-dispatcher-3] [] -
Slf4jLogger started
SLF4J: A number (1) of logging calls during the initialization phase have been intercepted and are
SLF4J: now being replayed. These are subject to the filtering rules of the underlying logging system.
SLF4J: See also http://www.slf4j.org/codes.html#replay
>>> Press ENTER to exit <<<
[2019-10-12 09:47:16,437] [INFO] [com.lightbend.akka.sample.Greeter] [helloakka-akka.actor.default-dispatcher-6]
[akka://helloakka/user/greeter] - Hello Charles!
[2019-10-12 09:47:16,439] [INFO] [com.lightbend.akka.sample.GreeterBot] [helloakka-akka.actor.default-dispatcher-3]
[akka://helloakka/user/Charles] - Greeting 1 for Charles
[2019-10-12 09:47:16,440] [INFO] [com.lightbend.akka.sample.Greeter] [helloakka-akka.actor.default-dispatcher-6]
[akka://helloakka/user/greeter] - Hello Charles!
[2019-10-12 09:47:16,440] [INFO] [com.lightbend.akka.sample.GreeterBot] [helloakka-akka.actor.default-dispatcher-3]
[akka://helloakka/user/Charles] - Greeting 2 for Charles
[2019-10-12 09:47:16,440] [INFO] [com.lightbend.akka.sample.Greeter] [helloakka-akka.actor.default-dispatcher-6]
[akka://helloakka/user/greeter] - Hello Charles!
[2019-10-12 09:47:16,440] [INFO] [com.lightbend.akka.sample.GreeterBot] [helloakka-akka.actor.default-dispatcher-3]
[akka://helloakka/user/Charles] - Greeting 3 for Charles
<=========----> 75% EXECUTING [27s]
> :run
```

恭喜你，你刚刚运行了你的第一个 Akka 应用程序。

## Hello World 都做了什么？
示例包含了三个Actor：

- Greeter： 接收命令来`Greet`其他人，并使用`Greeted`来确认收到了消息。
- GreeterBot：接收到从其他Greeter回复的问候，并发送多个额外的问候消息，并收集回复信息直到达到指定的数量。
- GreeterMain：引导一切的守护Actor

## 使用 Actor 模型的好处
Akka 的以下特性使你能够以直观的方式解决困难的并发性和可伸缩性挑战：

- 事件驱动模型：`Event-driven model`，Actor 通过响应消息来执行工作。Actor 之间的通信是异步的，允许 Actor 发送消息并继续自己的工作，而不是阻塞等待响应。
- 强隔离原则：`Strong isolation principles`，与 Java 中的常规对象不同，Actor 在调用的方法方面，没有一个公共 API。相反，它的公共 API 是通过 Actor 处理的消息来定义的。这可以防止 Actor 之间共享状态；观察另一个 Actor 状态的唯一方法是向其发送请求状态的消息。
- 位置透明：`Location transparency`，系统通过工厂方法构造 Actor 并返回对实例的引用。因为位置无关紧要，所以 Actor 实例可以启动、停止、移动和重新启动，以向上和向下扩展以及从意外故障中恢复。
- 轻量级：`Lightweight`，每个实例只消耗几百个字节，这实际上允许数百万并发 Actor 存在于一个应用程序中。

让我们看看在`Hello World`示例中使用 Actor 和消息一起工作的一些最佳实践。

## 定义 Actor 和消息

每个Actor定义它可以接收的消息类型`T`。类(classes)和对象(objects)由于不可变并支持模式匹配，可以当做非常特别的消息类型。我们在Actor中会用到这些特性接收匹配的消息。

`Hello World`的 Actor 使用三种不同的消息：

- `Greet`：向`Greeter`执行问候的指令；
- `Greeted`：`Greeter`用来确认问候发生时回复的消息；
- `SayHello`：`GreeterMain`开始执行问候进程的指令；

在定义 Actor 及其消息时，请记住以下建议：

- 因为消息是 Actor 的公共 API，所以**定义具有良好名称、丰富语义和特定于域的含义的消息是一个很好的实践**，即使它们只是包装你的数据类型，这将使基于 Actor 的系统更容易使用、理解和调试。
- **消息应该是不可变的**，因为它们在不同的线程之间共享。
- 将 Actor 的关联消息作为静态类放在 Actor 的类中是一个很好的实践，这使得理解 Actor 期望和处理的消息类型更加容易。
- 通过静态工厂方法获得Actor的初始行为是一个很好的实践

让我们来看看`Greeter`，`GreeterBot`和`GreeterMain`的实现是如何证明上述的这些实践建议的。

让我们看看 Actor 如何实现`Greeter`和`Printer`来演示这些最佳实践。

###  Greeter Actor
下面的代码段来自于`Greeter.java`，其实现了`Greeter Actor`：

```java
public class Greeter extends AbstractBehavior<Greeter.Greet> {

  public static final class Greet {
    public final String whom;
    public final ActorRef<Greeted> replyTo;

    public Greet(String whom, ActorRef<Greeted> replyTo) {
      this.whom = whom;
      this.replyTo = replyTo;
    }
  }

  public static final class Greeted {
    public final String whom;
    public final ActorRef<Greet> from;

    public Greeted(String whom, ActorRef<Greet> from) {
      this.whom = whom;
      this.from = from;
    }

  }

  public static Behavior<Greet> create() {
    return Behaviors.setup(Greeter::new);
  }

  private Greeter(ActorContext<Greet> context) {
    super(context);
  }

  @Override
  public Receive<Greet> createReceive() {
    return newReceiveBuilder().onMessage(Greet.class, this::onGreet).build();
  }

  private Behavior<Greet> onGreet(Greet command) {
    getContext().getLog().info("Hello {}!", command.whom);
    command.replyTo.tell(new Greeted(command.whom, getContext().getSelf()));
    return this;
  }
}
```

上面这个代码片段定义了两种消息类型，一种被Actor用来问候其他人，另外一种被Actor用来确认问候已经完成。`Greet`类型不仅包含了被问候人的信息，还持有了`ActorRef`，是由消息发送者提供的以便于`Greeter`Actor可以发回确认信息。

Actor的行为被定义为`Greeter`继承自`AbstractBehavior`，带有`newReceiveBuilder`的工厂行为。处理吓一条信息然后可能导致与处理当前这条信息的行为不同。只要当前实例是可变的就可以通过修改当前实例来更新状态。在当前这个例子中，我们不需要更新任何状态，所以我们直接返回`this`而不包含任何字段更新，这也意味着，处理下一条消息的行为与当前相同。

被当前行为处理的消息类型被声明为类`Greet`。通常，一个`actor`处理超过一种具体的消息类型，这样会有一个所有消息类型可以实现的公共的接口。

在最后一行我们能看到`Greeter`Actor使用`tell`方法发送消息到另外一个Actor。这是一个不会阻塞调用者线程的异步操作。

因为`replyTo`地址通过类型`ActorRef<Greeted>`进行声明，编译器仅允许我们发送这种类型的消息，使用其他消息类型会导致编译错误。

这个Actor可以接收的消息类型和回复的消息类型定义了我们所说的`协议`。当前用例是一个简单的`请求-回复`协议，但Actor可以定义任意我们需要的复杂协议。协议与其行为被恰当的包装在了一个范围——`Greeter`类

### Greeter bot actor
```java
package $package$;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;

public class GreeterBot extends AbstractBehavior<Greeter.Greeted> {

    public static Behavior<Greeter.Greeted> create(int max) {
        return Behaviors.setup(context -> new GreeterBot(context, max));
    }

    private final int max;
    private int greetingCounter;

    private GreeterBot(ActorContext<Greeter.Greeted> context, int max) {
        super(context);
        this.max = max;
    }

    @Override
    public Receive<Greeter.Greeted> createReceive() {
        return newReceiveBuilder().onMessage(Greeter.Greeted.class, this::onGreeted).build();
    }

    private Behavior<Greeter.Greeted> onGreeted(Greeter.Greeted message) {
        greetingCounter++;
        getContext().getLog().info("Greeting {} for {}", greetingCounter, message.whom);
        if (greetingCounter == max) {
            return Behaviors.stopped();
        } else {
            message.from.tell(new Greeter.Greet(message.whom, getContext().getSelf()));
            return this;
        }
    }
}
```
注意这个Actor如何使用实例变量管理计数器。不需要诸如`synchronized`或`AtomicInteger`这样的并发保护，因为一个Actor实例一次只处理一条消息。

### Greeter main actor

第三个Actor产生了`Greeter`和`GreeterBot`，并启动他们的交互，创建Actor以及`spawn`做了什么将在下面讨论。

```java
package $package$;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;

public class GreeterMain extends AbstractBehavior<GreeterMain.SayHello> {

    public static class SayHello {
        public final String name;

        public SayHello(String name) {
            this.name = name;
        }
    }

    private final ActorRef<Greeter.Greet> greeter;

    public static Behavior<SayHello> create() {
        return Behaviors.setup(GreeterMain::new);
    }

    private GreeterMain(ActorContext<SayHello> context) {
        super(context);
        //#create-actors
        greeter = context.spawn(Greeter.create(), "greeter");
        //#create-actors
    }

    @Override
    public Receive<SayHello> createReceive() {
        return newReceiveBuilder().onMessage(SayHello.class, this::onSayHello).build();
    }

    private Behavior<SayHello> onSayHello(SayHello command) {
        //#create-actors
        ActorRef<Greeter.Greeted> replyTo =
                getContext().spawn(GreeterBot.create(3), command.name);
        greeter.tell(new Greeter.Greet(command.name, replyTo));
        //#create-actors
        return this;
    }
}
```

## 创建 Actor

到目前为止，我们已经研究了 Actor 的定义和他们的消息。现在，让我们更深入地了解位置透明（`location transparency`）的好处，看看如何创建 Actor 实例。

### 位置透明的好处
在 Akka 中，不能使用`new`关键字创建 Actor 的实例。相反，你应该使用工厂方法创建 Actor 实例。工厂不返回 Actor 实例，而是返回指向 Actor 实例的引用`akka.actor.ActorRef`。在分布式系统中，这种间接创建实例的方法增加了很多好处和灵活性。

在 Akka 中位置无关紧要。位置透明性意味着，无论是在正在运行 Actor 的进程内，还是运行在远程计算机上，`ActorRef`都可以保持相同语义。如果需要，运行时可以通过更改 Actor 的位置或整个应用程序拓扑来优化系统。这就启用了故障管理的“让它崩溃（`let it crash`）”模型，在该模型中，系统可以通过销毁有问题的 Actor 和重新启动健康的 Actor 来自我修复。

### Akka ActorSystem
`ActorSystem`是Akka的初始接入点。通常每个应用使用一个`AcotrSystem`来创建。`ActorSystem`有一个名称和一个守护Actor。应用的启动通常在守护Actor中完成。

当前这个`ActorSystem`的守护Actor是`GreeterMain`。

```java
final ActorSystem<GreeterMain.SayHello> greeterMain = ActorSystem.create(GreeterMain.create(), "helloakka");
```

它使用`Behaviors.setup`来启动应用

```java
package $package$;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;

public class GreeterMain extends AbstractBehavior<GreeterMain.SayHello> {

    public static class SayHello {
        public final String name;

        public SayHello(String name) {
            this.name = name;
        }
    }

    private final ActorRef<Greeter.Greet> greeter;

    public static Behavior<SayHello> create() {
        return Behaviors.setup(GreeterMain::new);
    }

    private GreeterMain(ActorContext<SayHello> context) {
        super(context);
        //#create-actors
        greeter = context.spawn(Greeter.create(), "greeter");
        //#create-actors
    }

    @Override
    public Receive<SayHello> createReceive() {
        return newReceiveBuilder().onMessage(SayHello.class, this::onSayHello).build();
    }

    private Behavior<SayHello> onSayHello(SayHello command) {
        //#create-actors
        ActorRef<Greeter.Greeted> replyTo =
                getContext().spawn(GreeterBot.create(3), command.name);
        greeter.tell(new Greeter.Greet(command.name, replyTo));
        //#create-actors
        return this;
    }
}
```

### 新建子actors

其他actor的创建在`ActorContext`上使用`spawn`方法。`GreeterMain`在启动时使用这种方式创建一个`Greeter`，并且每收到一个`SayHello`消息创建一个新的`GreeterBot`。

```java
greeter = context.spawn(Greeter.create(), "greeter");
ActorRef<Greeter.Greeted> replyTo =
        getContext().spawn(GreeterBot.create(3), command.name);
greeter.tell(new Greeter.Greet(command.name, replyTo));
```

## 异步通信
 Actor 是被动的和消息驱动的。Actor 在收到消息前什么都不做。Actor 使用异步消息进行通信。这样可以确保发送者不会一直等待接收者处理他们的消息。相反，发件人将邮件放在收件人的邮箱之后，就可以自由地进行其他工作。Actor 的邮箱本质上是一个具有排序语义的消息队列。从同一个 Actor 发送的多条消息的顺序被保留，但可以与另一个 Actor 发送的消息交错。

你可能想知道 Actor 在不处理消息的时候在做什么，比如，做什么实际的工作？实际上，它处于挂起状态，在这种状态下，它不消耗除内存之外的任何资源。同样，这也展示了 Actor 的轻量级和高效性。

### 给 Actor 发生消息
要将消息放入 Actor 的邮箱，我们需要使用`ActorRef`的`tell`方法。例如，`Hello World`的主函数`main`向`Greeter` Actor 发送如下消息：

```java
greeterMain.tell(new GreeterMain.SayHello("Charles"));
```
`Greeter` Actor  也回复确认消息：

```java
command.replyTo.tell(new Greeted(command.whom, getContext().getSelf()));
```
我们已经研究了如何创建 Actor 和发送消息。现在，让我们来回顾一下`Main`类的全部内容。

## Main class

`Hello World`中的`AkkaQuickstart`对象创建了带有守护者的`ActorSystem`，守护者是顶层的负责启动应用的actor。守护者通常使用包含初始启动的`Behaviors.setup`进行定义。

```java
package $package$;

import akka.actor.typed.ActorSystem;

import java.io.IOException;
public class AkkaQuickstart {
  public static void main(String[] args) {
    //#actor-system
    final ActorSystem<GreeterMain.SayHello> greeterMain = ActorSystem.create(GreeterMain.create(), "helloakka");
    //#actor-system

    //#main-send-messages
    greeterMain.tell(new GreeterMain.SayHello("Charles"));
    //#main-send-messages

    try {
      System.out.println(">>> Press ENTER to exit <<<");
      System.in.read();
    } catch (IOException ignored) {
    } finally {
      greeterMain.terminate();
    }
  }
}
```
## 完整代码
下面是创建示例应用程序的三个类`Greeter`，`GreeterBot`，`GreeterMain`和`AkkaQuickstart`的完整源代码：

### Greater.java

```java
package $package$;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;

import java.util.Objects;

// #greeter
public class Greeter extends AbstractBehavior<Greeter.Greet> {

  public static final class Greet {
    public final String whom;
    public final ActorRef<Greeted> replyTo;

    public Greet(String whom, ActorRef<Greeted> replyTo) {
      this.whom = whom;
      this.replyTo = replyTo;
    }
  }

  public static final class Greeted {
    public final String whom;
    public final ActorRef<Greet> from;

    public Greeted(String whom, ActorRef<Greet> from) {
      this.whom = whom;
      this.from = from;
    }

// #greeter
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Greeted greeted = (Greeted) o;
      return Objects.equals(whom, greeted.whom) &&
              Objects.equals(from, greeted.from);
    }

    @Override
    public int hashCode() {
      return Objects.hash(whom, from);
    }

    @Override
    public String toString() {
      return "Greeted{" +
              "whom='" + whom + '\'' +
              ", from=" + from +
              '}';
    }
// #greeter
  }

  public static Behavior<Greet> create() {
    return Behaviors.setup(Greeter::new);
  }

  private Greeter(ActorContext<Greet> context) {
    super(context);
  }

  @Override
  public Receive<Greet> createReceive() {
    return newReceiveBuilder().onMessage(Greet.class, this::onGreet).build();
  }

  private Behavior<Greet> onGreet(Greet command) {
    getContext().getLog().info("Hello {}!", command.whom);
    //#greeter-send-message
    command.replyTo.tell(new Greeted(command.whom, getContext().getSelf()));
    //#greeter-send-message
    return this;
  }
}
// #greeter
```
### GreeterBot.java
```java
package $package$;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;

public class GreeterBot extends AbstractBehavior<Greeter.Greeted> {

    public static Behavior<Greeter.Greeted> create(int max) {
        return Behaviors.setup(context -> new GreeterBot(context, max));
    }

    private final int max;
    private int greetingCounter;

    private GreeterBot(ActorContext<Greeter.Greeted> context, int max) {
        super(context);
        this.max = max;
    }

    @Override
    public Receive<Greeter.Greeted> createReceive() {
        return newReceiveBuilder().onMessage(Greeter.Greeted.class, this::onGreeted).build();
    }

    private Behavior<Greeter.Greeted> onGreeted(Greeter.Greeted message) {
        greetingCounter++;
        getContext().getLog().info("Greeting {} for {}", greetingCounter, message.whom);
        if (greetingCounter == max) {
            return Behaviors.stopped();
        } else {
            message.from.tell(new Greeter.Greet(message.whom, getContext().getSelf()));
            return this;
        }
    }
}
```
### GreeterMain.java

```java
package $package$;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;

public class GreeterMain extends AbstractBehavior<GreeterMain.SayHello> {

    public static class SayHello {
        public final String name;

        public SayHello(String name) {
            this.name = name;
        }
    }

    private final ActorRef<Greeter.Greet> greeter;

    public static Behavior<SayHello> create() {
        return Behaviors.setup(GreeterMain::new);
    }

    private GreeterMain(ActorContext<SayHello> context) {
        super(context);
        //#create-actors
        greeter = context.spawn(Greeter.create(), "greeter");
        //#create-actors
    }

    @Override
    public Receive<SayHello> createReceive() {
        return newReceiveBuilder().onMessage(SayHello.class, this::onSayHello).build();
    }

    private Behavior<SayHello> onSayHello(SayHello command) {
        //#create-actors
        ActorRef<Greeter.Greeted> replyTo =
                getContext().spawn(GreeterBot.create(3), command.name);
        greeter.tell(new Greeter.Greet(command.name, replyTo));
        //#create-actors
        return this;
    }
}
```

### AkkaQuickstart.java

```java
package $package$;

import akka.actor.typed.ActorSystem;

import java.io.IOException;
public class AkkaQuickstart {
  public static void main(String[] args) {
    //#actor-system
    final ActorSystem<GreeterMain.SayHello> greeterMain = ActorSystem.create(GreeterMain.create(), "helloakka");
    //#actor-system

    //#main-send-messages
    greeterMain.tell(new GreeterMain.SayHello("Charles"));
    //#main-send-messages

    try {
      System.out.println(">>> Press ENTER to exit <<<");
      System.in.read();
    } catch (IOException ignored) {
    } finally {
      greeterMain.terminate();
    }
  }
}
```
作为另一个最佳实践，我们应该提供一些单元测试。

## 测试 Actor
`Hello World`示例中的测试展示了 JUnit 框架的使用。虽然测试的覆盖范围不完整，但它简单地展示了测试 Actor 代码是多么的容易，并提供了一些基本概念。

```java
package $package$;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import org.junit.ClassRule;
import org.junit.Test;

//#definition
public class AkkaQuickstartTest {

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();
//#definition

    //#test
    @Test
    public void testGreeterActorSendingOfGreeting() {
        TestProbe<Greeter.Greeted> testProbe = testKit.createTestProbe();
        ActorRef<Greeter.Greet> underTest = testKit.spawn(Greeter.create(), "greeter");
        underTest.tell(new Greeter.Greet("Charles", testProbe.getRef()));
        testProbe.expectMessage(new Greeter.Greeted("Charles", underTest));
    }
    //#test
}
```

### 测试类定义

```java
public class AkkaQuickstartTest {

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();
```

使用`TestKitJunitResource` JUnit规则包含对JUnit的支持。自动创建并清理`ActorTestKit`。通过[完整文档](https://doc.akka.io/docs/akka/2.6/typed/testing-async.html)查看如何直接使用`testkit`

### 测试方法

这个测试使用`TestProbe `来检查并确认是否得到期望的行为，源码片段如下：

```java
@Test
public void testGreeterActorSendingOfGreeting() {
    TestProbe<Greeter.Greeted> testProbe = testKit.createTestProbe();
    ActorRef<Greeter.Greet> underTest = testKit.spawn(Greeter.create(), "greeter");
    underTest.tell(new Greeter.Greet("Charles", testProbe.getRef()));
    testProbe.expectMessage(new Greeter.Greeted("Charles", underTest));
}
```

一旦我们有了`TestProbe `的引用，我们将它传递给`Greeter`作为`Greet`消息的一部分。然后确认`Greeter`响应问候发生。

### 完整测试程序

这里是完整的测试代码：

```java
package $package$;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import org.junit.ClassRule;
import org.junit.Test;

//#definition
public class AkkaQuickstartTest {

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();
//#definition

    //#test
    @Test
    public void testGreeterActorSendingOfGreeting() {
        TestProbe<Greeter.Greeted> testProbe = testKit.createTestProbe();
        ActorRef<Greeter.Greet> underTest = testKit.spawn(Greeter.create(), "greeter");
        underTest.tell(new Greeter.Greet("Charles", testProbe.getRef()));
        testProbe.expectMessage(new Greeter.Greeted("Charles", underTest));
    }
    //#test
}
```

示例代码只涉及了`ActorTestKit`功能的一小部分，在「[这里](https://doc.akka.io/docs/akka/current/testing.html?language=java)」可以找到更完整的概述。

## 运行应用程序
你可以通过命令行或者 IDE 来运行`Hello World`应用程序。在本指南的最后一个主题，我们描述了如何在 [IntelliJ IDEA](https://developer.lightbend.com/guides/akka-quickstart-java/intellij-idea.html) 中运行该示例。但是，在我们再次运行应用程序之前，让我们先快速的查看构建文件。

 - Maven POM 文件

```xml
<!-- #build-sample -->
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
            <artifactId>akka-actor-typed_2.13</artifactId>
            <version>\${akka.version}</version>
        </dependency>
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <version>1.2.3</version>
        </dependency>
        <dependency>
            <groupId>com.typesafe.akka</groupId>
            <artifactId>akka-actor-testkit-typed_2.13</artifactId>
            <version>\${akka.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>junit</groupId>
            <artifactId>junit</artifactId>
            <version>4.13.1</version>
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
                        <argument>$package$.AkkaQuickstart</argument>
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
  implementation 'com.typesafe.akka:akka-actor-typed_2.13:$akka_version$'
  implementation 'ch.qos.logback:logback-classic:1.2.3'
  testImplementation 'com.typesafe.akka:akka-actor-testkit-typed_2.13:$akka_version$'
  testImplementation 'junit:junit:4.13.1'
}

mainClassName = "$package$.AkkaQuickstart"

run {
  standardInput = System.in
}
```
注意：有些依赖有后缀`_2.13`，这个后缀是编译依赖的scala版本。所有依赖必须使用相同的scala版本编译。所以你不能在单个项目中使用`akka-actors_2.13`和`akka-testkit_2.12`，因为它们有不同的scala版本。

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
Scanning for projects...
[INFO]
[INFO] ------------------------< hello-akka-java:app >-------------------------
[INFO] Building app 1.0
[INFO] --------------------------------[ jar ]---------------------------------
[INFO]
[INFO] --- maven-resources-plugin:2.6:resources (default-resources) @ app ---
[WARNING] Using platform encoding (UTF-8 actually) to copy filtered resources, i.e. build is platform dependent!
[INFO]
[INFO] --- exec-maven-plugin:1.6.0:exec (default-cli) @ app ---
[2019-10-12 09:20:30,248] [INFO] [akka.event.slf4j.Slf4jLogger] [helloakka-akka.actor.default-dispatcher-3] [] -
Slf4jLogger started
SLF4J: A number (1) of logging calls during the initialization phase have been intercepted and are
SLF4J: now being replayed. These are subject to the filtering rules of the underlying logging system.
SLF4J: See also http://www.slf4j.org/codes.html#replay
>>> Press ENTER to exit <<<
[2019-10-12 09:20:30,288] [INFO] [com.lightbend.akka.sample.Greeter] [helloakka-akka.actor.default-dispatcher-6]
[akka://helloakka/user/greeter] - Hello Charles!
[2019-10-12 09:20:30,290] [INFO] [com.lightbend.akka.sample.GreeterBot] [helloakka-akka.actor.default-dispatcher-3]
[akka://helloakka/user/Charles] - Greeting 1 for Charles
[2019-10-12 09:20:30,291] [INFO] [com.lightbend.akka.sample.Greeter] [helloakka-akka.actor.default-dispatcher-6]
[akka://helloakka/user/greeter] - Hello Charles!
[2019-10-12 09:20:30,291] [INFO] [com.lightbend.akka.sample.GreeterBot] [helloakka-akka.actor.default-dispatcher-3]
[akka://helloakka/user/Charles] - Greeting 2 for Charles
[2019-10-12 09:20:30,291] [INFO] [com.lightbend.akka.sample.Greeter] [helloakka-akka.actor.default-dispatcher-6]
[akka://helloakka/user/greeter] - Hello Charles!
[2019-10-12 09:20:30,291] [INFO] [com.lightbend.akka.sample.GreeterBot] [helloakka-akka.actor.default-dispatcher-3]
[akka://helloakka/user/Charles] - Greeting 3 for Charles

// Grade
:run 
[2019-10-12 09:47:16,399] [INFO] [akka.event.slf4j.Slf4jLogger] [helloakka-akka.actor.default-dispatcher-3] [] -
Slf4jLogger started
SLF4J: A number (1) of logging calls during the initialization phase have been intercepted and are
SLF4J: now being replayed. These are subject to the filtering rules of the underlying logging system.
SLF4J: See also http://www.slf4j.org/codes.html#replay
>>> Press ENTER to exit <<<
[2019-10-12 09:47:16,437] [INFO] [com.lightbend.akka.sample.Greeter] [helloakka-akka.actor.default-dispatcher-6]
[akka://helloakka/user/greeter] - Hello Charles!
[2019-10-12 09:47:16,439] [INFO] [com.lightbend.akka.sample.GreeterBot] [helloakka-akka.actor.default-dispatcher-3]
[akka://helloakka/user/Charles] - Greeting 1 for Charles
[2019-10-12 09:47:16,440] [INFO] [com.lightbend.akka.sample.Greeter] [helloakka-akka.actor.default-dispatcher-6]
[akka://helloakka/user/greeter] - Hello Charles!
[2019-10-12 09:47:16,440] [INFO] [com.lightbend.akka.sample.GreeterBot] [helloakka-akka.actor.default-dispatcher-3]
[akka://helloakka/user/Charles] - Greeting 2 for Charles
[2019-10-12 09:47:16,440] [INFO] [com.lightbend.akka.sample.Greeter] [helloakka-akka.actor.default-dispatcher-6]
[akka://helloakka/user/greeter] - Hello Charles!
[2019-10-12 09:47:16,440] [INFO] [com.lightbend.akka.sample.GreeterBot] [helloakka-akka.actor.default-dispatcher-3]
[akka://helloakka/user/Charles] - Greeting 3 for Charles
<=========----> 75% EXECUTING [27s]
> :run
```
还记得我们实现 Greeter Actor 使用 Akka 的 Logger 吗？这就是为什么我们记录东西时会有很多额外的信息。例如，日志输出包含诸如何时和从哪个 Actor 记录日志之类的信息。

请注意应用程序一直执行，直到你按下回车键或使用其他方式中断。

为了执行单元测试，我们输入`test`命令：

```
//  Maven
$ mvn test

// Grade
$ gradle test
```
### 下一步
如果你使用 IntelliJ，请尝试将示例项目与 [IntelliJ IDEA](https://developer.lightbend.com/guides/akka-quickstart-java/intellij-idea.html) 集成。

想要继续了解更多有关 Akka 和 Actor Systems 的信息，请参阅「[Getting Started Guide](http://doc.akka.io/docs/akka/current/java/guide/introduction.html)」，欢迎你加入我们！

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

