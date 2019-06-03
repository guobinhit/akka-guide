# 事件总线
`EventBus`最初被认为是一种向 Actor 组发送消息的方法，它被概括为一组实现简单接口的抽象基类：

```java
/**
 * Attempts to register the subscriber to the specified Classifier
 *
 * @return true if successful and false if not (because it was already subscribed to that
 *     Classifier, or otherwise)
 */
public boolean subscribe(Subscriber subscriber, Classifier to);

/**
 * Attempts to deregister the subscriber from the specified Classifier
 *
 * @return true if successful and false if not (because it wasn't subscribed to that Classifier,
 *     or otherwise)
 */
public boolean unsubscribe(Subscriber subscriber, Classifier from);

/** Attempts to deregister the subscriber from all Classifiers it may be subscribed to */
public void unsubscribe(Subscriber subscriber);

/** Publishes the specified Event to this bus */
public void publish(Event event);
```

- **注释**：请注意，`EventBus`不保留已发布消息的发送者。如果你需要原始发件人的引用，则必须在消息中提供。

此机制在 Akka 内的不同地方使用，例如「[事件流](https://doc.akka.io/docs/akka/current/event-bus.html#event-stream)」。实现可以使用下面介绍的特定构建基块。

事件总线（`event bus`）必须定义以下三个类型参数：

- `Event`是在该总线上发布的所有事件的类型
- `Subscriber`是允许在该事件总线上注册的订阅者类型
- `Classifier`定义用于选择用于调度事件的订阅者的分类器

下面的特性在这些类型中仍然是通用的，但是需要为任何具体的实现定义它们。

## 分类器

这里介绍的分类器（`classifiers`）是 Akka 发行版的一部分，但是如果你没有找到完美的匹配，那么滚动你自己的分类器并不困难，请检查「[Github](https://github.com/akka/akka/blob/v2.5.23/akka-actor/src/main/scala/akka/event/EventBus.scala)」上现有分类器的实现情况。

### 查找分类

最简单的分类就是从每个事件中提取一个任意的分类器，并为每个可能的分类器维护一组订阅者。特征`LookupClassification`仍然是通用的，因为它抽象了如何比较订阅者以及如何准确地分类。

以下示例说明了需要实现的必要方法：

```java
import akka.event.japi.LookupEventBus;

static class MsgEnvelope {
  public final String topic;
  public final Object payload;

  public MsgEnvelope(String topic, Object payload) {
    this.topic = topic;
    this.payload = payload;
  }
}

/**
 * Publishes the payload of the MsgEnvelope when the topic of the MsgEnvelope equals the String
 * specified when subscribing.
 */
static class LookupBusImpl extends LookupEventBus<MsgEnvelope, ActorRef, String> {

  // is used for extracting the classifier from the incoming events
  @Override
  public String classify(MsgEnvelope event) {
    return event.topic;
  }

  // will be invoked for each event for all subscribers which registered themselves
  // for the event’s classifier
  @Override
  public void publish(MsgEnvelope event, ActorRef subscriber) {
    subscriber.tell(event.payload, ActorRef.noSender());
  }

  // must define a full order over the subscribers, expressed as expected from
  // `java.lang.Comparable.compare`
  @Override
  public int compareSubscribers(ActorRef a, ActorRef b) {
    return a.compareTo(b);
  }

  // determines the initial size of the index data structure
  // used internally (i.e. the expected number of different classifiers)
  @Override
  public int mapSize() {
    return 128;
  }
}
```

此实现的测试可能如下所示：

```java
LookupBusImpl lookupBus = new LookupBusImpl();
lookupBus.subscribe(getTestActor(), "greetings");
lookupBus.publish(new MsgEnvelope("time", System.currentTimeMillis()));
lookupBus.publish(new MsgEnvelope("greetings", "hello"));
expectMsgEquals("hello");
```

如果不存在特定事件的订阅者，则此分类器是有效的。

### 子通道分类

如果分类器形成了一个层次结构，并且希望不仅可以在叶节点上订阅，那么这个分类可能就是正确的分类。这种分类是为分类器只是事件的 JVM 类而开发的，订阅者可能对订阅某个类的所有子类感兴趣，但它可以与任何分类器层次结构一起使用。

以下示例说明了需要实现的必要方法：

```java
import akka.event.japi.SubchannelEventBus;

static class StartsWithSubclassification implements Subclassification<String> {
  @Override
  public boolean isEqual(String x, String y) {
    return x.equals(y);
  }

  @Override
  public boolean isSubclass(String x, String y) {
    return x.startsWith(y);
  }
}

/**
 * Publishes the payload of the MsgEnvelope when the topic of the MsgEnvelope starts with the
 * String specified when subscribing.
 */
static class SubchannelBusImpl extends SubchannelEventBus<MsgEnvelope, ActorRef, String> {

  // Subclassification is an object providing `isEqual` and `isSubclass`
  // to be consumed by the other methods of this classifier
  @Override
  public Subclassification<String> subclassification() {
    return new StartsWithSubclassification();
  }

  // is used for extracting the classifier from the incoming events
  @Override
  public String classify(MsgEnvelope event) {
    return event.topic;
  }

  // will be invoked for each event for all subscribers which registered themselves
  // for the event’s classifier
  @Override
  public void publish(MsgEnvelope event, ActorRef subscriber) {
    subscriber.tell(event.payload, ActorRef.noSender());
  }
}
```

此实现的测试可能如下所示：

```java
SubchannelBusImpl subchannelBus = new SubchannelBusImpl();
subchannelBus.subscribe(getTestActor(), "abc");
subchannelBus.publish(new MsgEnvelope("xyzabc", "x"));
subchannelBus.publish(new MsgEnvelope("bcdef", "b"));
subchannelBus.publish(new MsgEnvelope("abc", "c"));
expectMsgEquals("c");
subchannelBus.publish(new MsgEnvelope("abcdef", "d"));
expectMsgEquals("d");
```

在没有为事件找到订阅者的情况下，该分类器也很有效，但它使用常规锁来同步内部分类器缓存，因此它不适合订阅以非常高的频率更改的情况（请记住，通过发送第一条消息“打开”分类器也必须重新检查所有以前的订阅）。

### 扫描分类

前一个分类器是为严格分层的多分类器订阅而构建的，如果有重叠的分类器覆盖事件空间的各个部分而不形成分层结构，则此分类器非常有用。

以下示例说明了需要实现的必要方法：

```java
import akka.event.japi.ScanningEventBus;

/**
 * Publishes String messages with length less than or equal to the length specified when
 * subscribing.
 */
static class ScanningBusImpl extends ScanningEventBus<String, ActorRef, Integer> {

  // is needed for determining matching classifiers and storing them in an
  // ordered collection
  @Override
  public int compareClassifiers(Integer a, Integer b) {
    return a.compareTo(b);
  }

  // is needed for storing subscribers in an ordered collection
  @Override
  public int compareSubscribers(ActorRef a, ActorRef b) {
    return a.compareTo(b);
  }

  // determines whether a given classifier shall match a given event; it is invoked
  // for each subscription for all received events, hence the name of the classifier
  @Override
  public boolean matches(Integer classifier, String event) {
    return event.length() <= classifier;
  }

  // will be invoked for each event for all subscribers which registered themselves
  // for the event’s classifier
  @Override
  public void publish(String event, ActorRef subscriber) {
    subscriber.tell(event, ActorRef.noSender());
  }
}
```

此实现的测试可能如下所示：

```java
ScanningBusImpl scanningBus = new ScanningBusImpl();
scanningBus.subscribe(getTestActor(), 3);
scanningBus.publish("xyzabc");
scanningBus.publish("ab");
expectMsgEquals("ab");
scanningBus.publish("abc");
expectMsgEquals("abc");
```

这个分类器总是需要一个与订阅数量成比例的时间，与实际匹配的数量无关。

### Actor 分类

这个分类最初是专门为实现「[DeathWatch](https://doc.akka.io/docs/akka/current/actors.html#deathwatch)」而开发的：订阅者和分类器都是`ActorRef`类型的。

这种分类需要一个`ActorSystem`来执行与作为 Actor 的订阅者相关的簿记操作，而订阅者可以在不首先从`EventBus`取消订阅的情况下终止。`ManagedActorClassification`维护一个系统 Actor，自动处理取消订阅终止的 Actor。

以下示例说明了需要实现的必要方法：

```java
import akka.event.japi.ManagedActorEventBus;

static class Notification {
  public final ActorRef ref;
  public final int id;

  public Notification(ActorRef ref, int id) {
    this.ref = ref;
    this.id = id;
  }
}

static class ActorBusImpl extends ManagedActorEventBus<Notification> {

  // the ActorSystem will be used for book-keeping operations, such as subscribers terminating
  public ActorBusImpl(ActorSystem system) {
    super(system);
  }

  // is used for extracting the classifier from the incoming events
  @Override
  public ActorRef classify(Notification event) {
    return event.ref;
  }

  // determines the initial size of the index data structure
  // used internally (i.e. the expected number of different classifiers)
  @Override
  public int mapSize() {
    return 128;
  }
}
```

此实现的测试可能如下所示：

```java
ActorRef observer1 = new TestKit(system).getRef();
ActorRef observer2 = new TestKit(system).getRef();
TestKit probe1 = new TestKit(system);
TestKit probe2 = new TestKit(system);
ActorRef subscriber1 = probe1.getRef();
ActorRef subscriber2 = probe2.getRef();
ActorBusImpl actorBus = new ActorBusImpl(system);
actorBus.subscribe(subscriber1, observer1);
actorBus.subscribe(subscriber2, observer1);
actorBus.subscribe(subscriber2, observer2);
Notification n1 = new Notification(observer1, 100);
actorBus.publish(n1);
probe1.expectMsgEquals(n1);
probe2.expectMsgEquals(n1);
Notification n2 = new Notification(observer2, 101);
actorBus.publish(n2);
probe2.expectMsgEquals(n2);
probe1.expectNoMessage(Duration.ofMillis(500));
```

这个分类器在事件类型中仍然是通用的，对于所有用例都是有效的。

## 事件流

事件流（`event stream`）是每个 Actor 系统的主要事件总线：它用于承载「[日志消息](https://doc.akka.io/docs/akka/current/logging.html)」和「[死信](https://doc.akka.io/docs/akka/current/event-bus.html#dead-letters)」，用户代码也可以将其用于其他目的。它使用子通道分类，允许注册到相关的信道集（用于`RemotingLifecycleEvent`）。下面的示例演示简单订阅的工作原理。给定一个简单的 Actor：

```java
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
```

```java
static class DeadLetterActor extends AbstractActor {
  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            DeadLetter.class,
            msg -> {
              System.out.println(msg);
            })
        .build();
  }
}
```

可以这样订阅：

```java
final ActorSystem system = ActorSystem.create("DeadLetters");
final ActorRef actor = system.actorOf(Props.create(DeadLetterActor.class));
system.getEventStream().subscribe(actor, DeadLetter.class);
```

值得指出的是，由于在事件流中实现子通道分类的方式，可以订阅一组事件，方法是订阅它们的公共超类，如下例所示：

```java
interface AllKindsOfMusic {}

class Jazz implements AllKindsOfMusic {
  public final String artist;

  public Jazz(String artist) {
    this.artist = artist;
  }
}

class Electronic implements AllKindsOfMusic {
  public final String artist;

  public Electronic(String artist) {
    this.artist = artist;
  }
}

static class Listener extends AbstractActor {
  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            Jazz.class,
            msg -> System.out.printf("%s is listening to: %s%n", getSelf().path().name(), msg))
        .match(
            Electronic.class,
            msg -> System.out.printf("%s is listening to: %s%n", getSelf().path().name(), msg))
        .build();
  }
}
  final ActorRef actor = system.actorOf(Props.create(DeadLetterActor.class));
  system.getEventStream().subscribe(actor, DeadLetter.class);

  final ActorRef jazzListener = system.actorOf(Props.create(Listener.class));
  final ActorRef musicListener = system.actorOf(Props.create(Listener.class));
  system.getEventStream().subscribe(jazzListener, Jazz.class);
  system.getEventStream().subscribe(musicListener, AllKindsOfMusic.class);

  // only musicListener gets this message, since it listens to *all* kinds of music:
  system.getEventStream().publish(new Electronic("Parov Stelar"));

  // jazzListener and musicListener will be notified about Jazz:
  system.getEventStream().publish(new Jazz("Sonny Rollins"));
```

与 Actor 分类类似，`EventStream`将在订阅者终止时自动删除订阅者。

- **注释**：事件流是一个本地设施，这意味着它不会将事件分发到集群环境中的其他节点（除非你明确地向流订阅远程 Actor）。如果你需要在 Akka 集群中广播事件，而不明确地知道你的收件人（即获取他们的`ActorRefs`），你可能需要查看：「[集群中的分布式发布订阅](https://doc.akka.io/docs/akka/current/distributed-pub-sub.html)」。

### 默认处理程序

启动后，Actor 系统创建并订阅事件流的 Actor 以进行日志记录：这些是在`application.conf`中配置的处理程序：

```java
akka {
  loggers = ["akka.event.Logging$DefaultLogger"]
}
```

此处按完全限定类名列出的处理程序将订阅优先级高于或等于配置的日志级别的所有日志事件类，并且在运行时更改日志级别时，它们的订阅将保持同步：

```java
system.eventStream.setLogLevel(Logging.DebugLevel());
```

这意味着对于一个不会被记录的级别，日志事件通常根本不会被调度（除非已经完成了对相应事件类的手动订阅）。

### 死信

如「[停止 Actor](https://doc.akka.io/docs/akka/current/actors.html#stopping-actors)」所述，Actor 在其死亡后终止或发送时排队的消息将重新路由到死信邮箱，默认情况下，死信邮箱将发布用死信包装的消息。此包装包含已重定向信封的原始发件人、收件人和消息。

一些内部消息（用死信抑制特性标记）不会像普通消息一样变成死信。这些是设计安全的，并且预期有时会到达一个终止的 Actor，因为它们不需要担心，所以它们被默认的死信记录机制抑制。

但是，如果你发现自己需要调试这些低级抑制死信（`low level suppressed dead letters`），仍然可以明确订阅它们：

```java
system.getEventStream().subscribe(actor, SuppressedDeadLetter.class);
```

或所有死信（包括被压制的）：

```java
system.getEventStream().subscribe(actor, AllDeadLetters.class);
```

## 其他用途

事件流总是在那里并且随时可以使用，你可以发布自己的事件（它接受`Object`）并向监听器订阅相应的 JVM 类。


----------

**英文原文链接**：[Event Bus](https://doc.akka.io/docs/akka/current/event-bus.html).

----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————