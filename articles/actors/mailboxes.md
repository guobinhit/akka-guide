# 邮箱
## 依赖

为了使用邮箱（`Mailboxes`），你需要将以下依赖添加到你的项目中：

```xml
<!-- Maven -->
<dependency>
  <groupId>com.typesafe.akka</groupId>
  <artifactId>akka-actor_2.12</artifactId>
  <version>2.5.21</version>
</dependency>

<!-- Gradle -->
dependencies {
  compile group: 'com.typesafe.akka', name: 'akka-actor_2.12', version: '2.5.21'
}

<!-- sbt -->
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.5.21"
```

## 简介
Akka 的邮箱中保存着发给 Actor 的信息。通常，每个 Actor 都有自己的邮箱，但也有例外，如使用`BalancingPool`，则所有路由器（`routees`）将共享一个邮箱实例。

## 邮箱选择
### 指定 Actor 的消息队列类型

通过让某个 Actor 实现参数化接口`RequiresMessageQueue`，可以为某个 Actor 类型指定某种类型的消息队列。下面是一个例子：

```java
import akka.dispatch.BoundedMessageQueueSemantics;
import akka.dispatch.RequiresMessageQueue;

public class MyBoundedActor extends MyActor
    implements RequiresMessageQueue<BoundedMessageQueueSemantics> {}
```

`RequiresMessageQueue`接口的类型参数需要映射到配置中的邮箱，如下所示：

```java
bounded-mailbox {
  mailbox-type = "akka.dispatch.NonBlockingBoundedMailbox"
  mailbox-capacity = 1000 
}

akka.actor.mailbox.requirements {
  "akka.dispatch.BoundedMessageQueueSemantics" = bounded-mailbox
}
```

现在，每次创建`MyBoundedActor`类型的 Actor 时，它都会尝试获取一个有界邮箱。如果 Actor 在部署中配置了不同的邮箱，可以直接配置，也可以通过具有指定邮箱类型的调度器（`dispatcher`）配置，那么这将覆盖此映射。

- **注释**：接口中的所需类型为 Actor 创建的邮箱中的队列类型，如果队列未实现所需类型，则 Actor 创建将失败。

### 指定调度器的消息队列类型

调度器还可能需要运行在其上的 Actor 使用的邮箱类型。例如，`BalancingDispatcher`需要一个消息队列，该队列对于多个并发使用者是线程安全的。这需要对调度器进行配置，如下所示：

```yml
my-dispatcher {
  mailbox-requirement = org.example.MyInterface
}
```

给定的需求命名一个类或接口，然后确保该类或接口是消息队列实现的父类型。如果发生冲突，例如，如果 Actor 需要不满足此要求的邮箱类型，则 Actor 创建将失败。

### 如何选择邮箱类型

创建 Actor 时，`ActorRefProvider`首先确定执行它的调度器。然后确定邮箱如下：

1. 如果 Actor 的部署配置节（`section`）包含`mailbox`键，那么它将命名一个描述要使用的邮箱类型的配置节。
2. 如果 Actor 的`Props`包含邮箱选择（`mailbox selection`），即对其调用了`withMailbox`，则该属性将命名一个描述要使用的邮箱类型的配置节。请注意，这需要绝对配置路径，例如`myapp.special-mailbox`，并且不嵌套在`akka`命名空间中。
3. 如果调度器的配置节包含`mailbox-type`键，则将使用相同的节来配置邮箱类型。
4. 如果 Actor 需要如上所述的邮箱类型，则将使用该要求（`requirement`）的映射来确定要使用的邮箱类型；如果失败，则尝试使用调度器的要求（如果有）。
5. 如果调度器需要如上所述的邮箱类型，那么将使用该要求的映射来确定要使用的邮箱类型。
6. 将使用默认邮箱`akka.actor.default-mailbox`。

### 默认邮箱

如果未按上述说明指定邮箱，则使用默认邮箱。默认情况下，它是一个无边界的邮箱，由`java.util.concurrent.ConcurrentLinkedQueue`支持。

`SingleConsumerOnlyUnboundedMailbox`是一个效率更高的邮箱，它可以用作默认邮箱，但不能与`BalancingDispatcher`一起使用。

将`SingleConsumerOnlyUnboundedMailbox`配置为默认邮箱：

```yml
akka.actor.default-mailbox {
  mailbox-type = "akka.dispatch.SingleConsumerOnlyUnboundedMailbox"
}
```

### 将哪个配置传递到邮箱类型

每个邮箱类型都由一个扩展`MailboxType`并接受两个构造函数参数的类实现：`ActorSystem.Settings`对象和`Config`部分。后者是通过从 Actor 系统的配置中获取命名的配置节、用邮箱类型的配置路径覆盖其`id`键并添加回退（`fall-back `）到默认邮箱配置节来计算的。

## 内置邮箱实现

Akka 附带了许多邮箱实现：

- `UnboundedMailbox`（默认）
  - 默认邮箱
  - 由`java.util.concurrent.ConcurrentLinkedQueue`支持
  - 是否阻塞：`No`
  - 是否有界：`No`
  - 配置名称：`unbounded`或`akka.dispatch.UnboundedMailbox`
- `SingleConsumerOnlyUnboundedMailbox`，此队列可能比默认队列快，也可能不比默认队列快，具体取决于你的用例，请确保正确地进行基准测试！
  - 由多个生产商单个使用者队列支持，不能与BalancingDispatcher一起使用
  - 是否阻塞：`No`
  - 是否有界：`No`
  - 配置名称：`akka.dispatch.SingleConsumerOnlyUnboundedMailbox`
- `NonBlockingBoundedMailbox`
  - 由一个非常高效的”多生产者，单消费者“队列支持
  - 是否阻塞：`No`（将溢出的消息丢弃为`deadLetters`）
  - 是否有界：`Yes`
  - 配置名称：`akka.dispatch.NonBlockingBoundedMailbox`
- `UnboundedControlAwareMailbox`
  - 传递以更高优先级扩展`akka.dispatch.ControlMessage`的消息
  - 由两个`java.util.concurrent.ConcurrentLinkedQueue`支持
  - 是否阻塞：`No`
  - 是否有界：`No`
  - 配置名称：`akka.dispatch.UnboundedControlAwareMailbox`
- `UnboundedPriorityMailbox`
  - 由`java.util.concurrent.PriorityBlockingQueue`支持
  - 等优先级邮件的传递顺序未定义，与`UnboundedStablePriorityMailbox`相反
  - 是否阻塞：`No`
  - 是否有界：`No`
  - 配置名称：`akka.dispatch.UnboundedPriorityMailbox`
- `UnboundedStablePriorityMailbox`
- 由包装在`akka.util.PriorityQueueStabilizer`中的`java.util.concurrent.PriorityBlockingQueue`提供支持
- 对于优先级相同的消息保留`FIFO`顺序，与`UnboundedPriorityMailbox`相反
- 是否阻塞：`No`
- 是否有界：`No`
- 配置名称：`akka.dispatch.UnboundedStablePriorityMailbox`

其他有界邮箱实现，如果达到容量并配置了非零`mailbox-push-timeout-time`超时时间，则会阻止发件人。特别地，以下邮箱只能与零`mailbox-push-timeout-time`一起使用。

- `BoundedMailbox`
  - 由`java.util.concurrent.LinkedBlockingQueue`支持
  - 是否阻塞：如果与非零`mailbox-push-timeout-time`一起使用，则为`Yes`，否则为`NO`
  - 是否有界：`Yes`
  - 配置名称：`bounded`或`akka.dispatch.BoundedMailbox`
- `BoundedPriorityMailbox`
  - 由包装在`akka.util.BoundedBlockingQueue`中的`java.util.PriorityQueue`提供支持
  - 优先级相同的邮件的传递顺序未定义，与`BoundedStablePriorityMailbox`相反
  - 是否阻塞：如果与非零`mailbox-push-timeout-time`一起使用，则为`Yes`，否则为`NO`
  - 是否有界：`Yes`
  - 配置名称：`akka.dispatch.BoundedPriorityMailbox`
- `BoundedStablePriorityMailbox`
  - 由包装在`akka.util.PriorityQueueStabilizer`和`akka.util.BoundedBlockingQueue`中的`java.util.PriorityQueue`提供支持
  - 对于优先级相同的消息保留`FIFO`顺序，与`BoundedPriorityMailbox`相反
  - 是否阻塞：如果与非零`mailbox-push-timeout-time`一起使用，则为`Yes`，否则为`NO`
  - 是否有界：`Yes`
  - 配置名称：`akka.dispatch.BoundedStablePriorityMailbox`
- `BoundedControlAwareMailbox`
  - 传递以更高优先级扩展`akka.dispatch.ControlMessage`的消息
  - 由两个`java.util.concurrent.ConcurrentLinkedQueue`支持，如果达到容量，则在排队时阻塞
  - 是否阻塞：如果与非零`mailbox-push-timeout-time`一起使用，则为`Yes`，否则为`NO`
  - 是否有界：`Yes`
  - 配置名称：`akka.dispatch.BoundedControlAwareMailbox`

## 邮箱配置示例
### PriorityMailbox

如何创建`PriorityMailbox`:

```java
static class MyPrioMailbox extends UnboundedStablePriorityMailbox {
  // needed for reflective instantiation
  public MyPrioMailbox(ActorSystem.Settings settings, Config config) {
    // Create a new PriorityGenerator, lower prio means more important
    super(
        new PriorityGenerator() {
          @Override
          public int gen(Object message) {
            if (message.equals("highpriority"))
              return 0; // 'highpriority messages should be treated first if possible
            else if (message.equals("lowpriority"))
              return 2; // 'lowpriority messages should be treated last if possible
            else if (message.equals(PoisonPill.getInstance()))
              return 3; // PoisonPill when no other left
            else return 1; // By default they go between high and low prio
          }
        });
  }
}
```

然后将其添加到配置中：

```java
prio-dispatcher {
  mailbox-type = "docs.dispatcher.DispatcherDocSpec$MyPrioMailbox"
  //Other dispatcher configuration goes here
}
```

下面是一个关于如何使用它的示例：

```java
class Demo extends AbstractActor {
  LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  {
    for (Object msg :
        new Object[] {
          "lowpriority",
          "lowpriority",
          "highpriority",
          "pigdog",
          "pigdog2",
          "pigdog3",
          "highpriority",
          PoisonPill.getInstance()
        }) {
      getSelf().tell(msg, getSelf());
    }
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .matchAny(
            message -> {
              log.info(message.toString());
            })
        .build();
  }
}

// We create a new Actor that just prints out what it processes
ActorRef myActor =
    system.actorOf(Props.create(Demo.class, this).withDispatcher("prio-dispatcher"));

/*
Logs:
  'highpriority
  'highpriority
  'pigdog
  'pigdog2
  'pigdog3
  'lowpriority
  'lowpriority
*/
```

也可以这样直接配置邮箱类型（这是顶级配置项）：

```java
prio-mailbox {
  mailbox-type = "docs.dispatcher.DispatcherDocSpec$MyPrioMailbox"
  //Other mailbox configuration goes here
}

akka.actor.deployment {
  /priomailboxactor {
    mailbox = prio-mailbox
  }
}
```

然后从这样的部署中使用它：

```java
ActorRef myActor = system.actorOf(Props.create(MyActor.class), "priomailboxactor");
```

或者这样的代码：

```java
ActorRef myActor = system.actorOf(Props.create(MyActor.class).withMailbox("prio-mailbox"));
```

### ControlAwareMailbox

如果 Actor 需要立即接收控制消息，无论邮箱中已经有多少其他消息，`ControlAwareMailbox`都非常有用。

可以这样配置：

```java
control-aware-dispatcher {
  mailbox-type = "akka.dispatch.UnboundedControlAwareMailbox"
  //Other dispatcher configuration goes here
}
```

控制消息需要扩展`ControlMessage`特性：

```java
static class MyControlMessage implements ControlMessage {}
```

下面是一个关于如何使用它的示例：

```java
class Demo extends AbstractActor {
  LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  {
    for (Object msg :
        new Object[] {"foo", "bar", new MyControlMessage(), PoisonPill.getInstance()}) {
      getSelf().tell(msg, getSelf());
    }
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .matchAny(
            message -> {
              log.info(message.toString());
            })
        .build();
  }
}

// We create a new Actor that just prints out what it processes
ActorRef myActor =
    system.actorOf(Props.create(Demo.class, this).withDispatcher("control-aware-dispatcher"));

/*
Logs:
  'MyControlMessage
  'foo
  'bar
*/
```

## 创建自己的邮箱类型

示例如下：

```java
// Marker interface used for mailbox requirements mapping
public interface MyUnboundedMessageQueueSemantics {}
```

```java
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.dispatch.Envelope;
import akka.dispatch.MailboxType;
import akka.dispatch.MessageQueue;
import akka.dispatch.ProducesMessageQueue;
import com.typesafe.config.Config;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;
import scala.Option;

public class MyUnboundedMailbox
    implements MailboxType, ProducesMessageQueue<MyUnboundedMailbox.MyMessageQueue> {

  // This is the MessageQueue implementation
  public static class MyMessageQueue implements MessageQueue, MyUnboundedMessageQueueSemantics {
    private final Queue<Envelope> queue = new ConcurrentLinkedQueue<Envelope>();

    // these must be implemented; queue used as example
    public void enqueue(ActorRef receiver, Envelope handle) {
      queue.offer(handle);
    }

    public Envelope dequeue() {
      return queue.poll();
    }

    public int numberOfMessages() {
      return queue.size();
    }

    public boolean hasMessages() {
      return !queue.isEmpty();
    }

    public void cleanUp(ActorRef owner, MessageQueue deadLetters) {
      for (Envelope handle : queue) {
        deadLetters.enqueue(owner, handle);
      }
    }
  }

  // This constructor signature must exist, it will be called by Akka
  public MyUnboundedMailbox(ActorSystem.Settings settings, Config config) {
    // put your initialization code here
  }

  // The create method is called to create the MessageQueue
  public MessageQueue create(Option<ActorRef> owner, Option<ActorSystem> system) {
    return new MyMessageQueue();
  }
}
```

然后，将`MailboxType`的 FQCN 指定为调度器配置或邮箱配置中`mailbox-type`的值。

- **注释**：请确保包含一个采用`akka.actor.ActorSystem.Settings`和`com.typesafe.config.Config`参数的构造函数，因为此构造函数是通过反射调用来构造邮箱类型的。作为第二个参数传入的配置是配置中描述使用此邮箱类型的调度器或邮箱设置的部分；邮箱类型将为使用它的每个调度器或邮箱设置实例化一次。

你还可以使用邮箱作为调度器的要求（`requirement`），如下所示：

```java
custom-dispatcher {
  mailbox-requirement =
  "jdocs.dispatcher.MyUnboundedMessageQueueSemantics"
}

akka.actor.mailbox.requirements {
  "jdocs.dispatcher.MyUnboundedMessageQueueSemantics" =
  custom-dispatcher-mailbox
}

custom-dispatcher-mailbox {
  mailbox-type = "jdocs.dispatcher.MyUnboundedMailbox"
}
```

或者像这样定义 Actor 类的要求：

```java
static class MySpecialActor extends AbstractActor
    implements RequiresMessageQueue<MyUnboundedMessageQueueSemantics> {
  // ...
}
```

## system.actorOf 的特殊语义

为了使`system.actorOf`既同步又不阻塞，同时保持返回类型`ActorRef`（以及返回的`ref`完全起作用的语义），对这种情况进行了特殊处理。在幕后，构建了一种空的 Actor 引用，将其发送给系统的守护者 Actor，该 Actor 实际上创建了 Actor 及其上下文，并将其放入引用中。在这之前，发送到`ActorRef`的消息将在本地排队，只有在交换真正的填充之后，它们才会被传输到真正的邮箱中。因此，

```java
final Props props = ...
// this actor uses MyCustomMailbox, which is assumed to be a singleton
system.actorOf(props.withDispatcher("myCustomMailbox").tell("bang", sender);
assert(MyCustomMailbox.getInstance().getLastEnqueued().equals("bang"));
```

可能会失败；你必须留出一段时间通过并重试检查`TestKit.awaitCond`。



----------

**英文原文链接**：[Mailboxes](https://doc.akka.io/docs/akka/current/mailboxes.html).



----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————