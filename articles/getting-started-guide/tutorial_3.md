# 第 3 部分: 使用设备 Actors
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
在前面的主题中，我们解释了如何在大范围内查看 Actor 系统，也就是说，如何表示组件，如何在层次结构中排列 Actor。在这一部分中，我们将通过实现设备 Actor 来在小范围内观察 Actor。

如果我们处理对象，我们通常将 API 设计为接口，由实际实现来填充抽象方法集合。在 Actor 的世界里，协议取代了接口。虽然在编程语言中无法将一般协议形式化，但是我们可以组成它们最基本的元素，消息。因此，我们将从识别我们要发送给设备 Actor 的消息开始。

通常，消息分为类别或模式。通过识别这些模式，你将发现在它们之间进行选择和实现变得更加容易。第一个示例演示“请求-响应”消息模式。

## 识别设备的消息
设备 Actor 的任务很简单：

- 收集温度测量值
- 当被询问时，报告上次测量的温度

然而，设备可能在没有立即进行温度测量的情况下启动。因此，我们需要考虑温度不存在的情况。这还允许我们在不存在写入部分的时候测试 Actor 的查询部分，因为设备 Actor 可以报告空结果。

从设备 Actor 获取当前温度的协议很简单。Actor：

- 等待当前温度的请求。
- 对请求作出响应，并答复：
  - 包含当前温度，或者
  - 指示温度尚不可用。

我们需要两条消息，一条用于请求，一条用于回复。我们的第一次尝试可能如下：

```java
public static final class ReadTemperature {
}

public static final class RespondTemperature {
  final Optional<Double> value;

  public RespondTemperature(Optional<Double> value) {
    this.value = value;
  }
}
```
这两条消息似乎涵盖了所需的功能。但是，我们选择的方法必须考虑到应用程序的分布式性质。虽然本地 JVM 上的 Actor 通信的基本机制与远程 Actor 通信的基本机制相同，但我们需要记住以下几点：

- 因为网络链路带宽和消息大小等因素的存在，本地和远程消息在传递延迟方面会有明显的差异。
- 可靠性是一个问题，因为远程消息发送需要更多的步骤，这意味着更多的步骤可能出错。
- 本地发送将在同一个 JVM 中传递对消息的引用，而对发送的底层对象没有任何限制，而远程传输将限制消息的大小。

此外，当在同一个 JVM 中发送时，如果一个 Actor 在处理消息时由于编程错误而失败，则效果与处理消息时由于远程主机崩溃而导致远程网络请求失败的效果相同。尽管在这两种情况下，服务会在一段时间后恢复（Actor 由其监督者重新启动，主机由操作员或监控系统重新启动），但在崩溃期间，单个请求会丢失。因此，你的 Actor 代码发送的每一条信息都可能丢失，这是一个安全的、悲观的赌注。

但是如果进一步理解协议灵活性的需求，它将有助于考虑 Akka 消息订阅和消息传递的安全保证。Akka 为消息发送提供以下行为：

- 至多发送一次消息，即消息发送没有保证；
- 按“发送方、接收方”对维护消息顺序。

以下各节更详细地讨论了此行为：

- [消息传递](#消息传递)
- [消息序列](#消息序列)

### 消息传递
消息子系统提供的传递语义通常分为以下类别：

- 至多一次传递：`At-most-once delivery`，每一条消息都是传递零次或一次；在更因果关系的术语中，这意味着消息可能会丢失，但永远不会重复。
- 至少一次传递：`At-least-once delivery`，可能多次尝试传递每条消息，直到至少一条成功；同样，在更具因果关系的术语中，这意味着消息可能重复，但永远不会丢失。
- 恰好一次传递：`Exactly-once delivery`，每条消息只给收件人传递一次；消息既不能丢失，也不能重复。

第一种“至多一次传递”是 Akka 使用的方式，它是最廉价也是性能最好的方式。它具有最小的实现开销，因为它可以以一种“即发即弃（`fire-and-forget`）”的方式完成，而不需要将状态保持在发送端或传输机制中。第二个，“至少一次传递”，需要重试以抵消传输损失。这增加了在发送端保持状态和在接收端具有确认机制的开销。“恰好一次传递”最为昂贵，并且会导致最差的性能：除了“至少一次传递”所增加的开销之外，它还要求将状态保留在接收端，以便筛选出重复的传递。

在 Actor 系统中，我们需要确切含义——即在哪一点上，系统认为消息传递完成：

 1. 消息何时在网络上发送？
 2. 目标 Actor 的主机何时接收消息？
 3. 消息何时被放入目标 Actor 的邮箱？
 4. 消息目标 Actor 何时开始处理消息？
 5. 目标 Actor 何时成功处理完消息？

大多数声称保证传递的框架和协议实际上提供了类似于第 4 点和第 5 点的内容。虽然这听起来很合理，但它真的有用吗？要理解其含义，请考虑一个简单、实用的示例：用户尝试下单，而我们希望只有当订单数据库中的磁盘上实际存在订单信息后，才说订单已成功处理。

如果我们依赖消息的成功处理，那么一旦订单提交给负责验证它、处理它并将其放入数据库的内部 API，Actor 就会报告成功。不幸的是，在调用 API 之后，可能会立即发生以下任何情况：

- 主机可能崩溃。
- 反序列化可能失败。
- 验证可能失败。
- 数据库可能不可用。
- 可能发生编程错误。

这说明传递的保证不会转化为域级别的保证。我们只希望在订单被实际完全处理和持久化后报告成功。**唯一能够报告成功的实体是应用程序本身，因为只有它对所需的域保证最了解**。没有一个通用的框架能够找出一个特定领域的细节，以及在该领域中什么被认为是成功的。

在这个特定的例子中，我们只希望在数据库成功写入之后就发出成功的信号，在这里数据库确认订单现在已安全存储。基于这些原因，Akka 解除了对应用程序本身的保证责任，即你必须自己使用 Akka 提供的工具来实现这些保证。这使你能够完全控制你想要提供的保证。现在，让我们考虑一下 Akka 提供的消息序列，它可以很容易地解释应用程序逻辑。

### 消息序列
在 Akka 中 ，对于一对给定的 Actor，直接从第一个 Actor 发送到第二个 Actor 的消息不会被无序接收。该词直接强调，此保证仅在与`tell`运算符直接发送到最终目的地时适用，而在使用中介时不适用。

如果：

- Actor A1 向 A2 发送消息`M1`、`M2`和`M3`。
- Actor A3 向 A2 发送消息`M4`、`M5`和`M6`。

这意味着，对于 Akka 信息：

- 如果`M1`传递，则必须在`M2`和`M3`之前传递。
- 如果`M2`传递，则必须在`M3`之前传递。
- 如果`M4`传递，则必须在`M5`和`M6`之前传递。
- 如果`M5`传递，则必须在`M6`之前传递。
- A2 可以看到 A1 的消息与 A3 的消息交织在一起。
- 由于没有保证的传递，任何消息都可能丢失，即不能到达 A2。

这些保证实现了一个良好的平衡：让一个 Actor 发送的消息有序到达，便于构建易于推理的系统，而另一方面，允许不同 Actor 发送的消息交错到达，则为 Actor 系统的有效实现提供了足够的自由度。

有关消息传递保证的详细信息，请参阅「[消息传递可靠性](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/message-delivery-reliability.md)」。

## 增加设备消息的灵活性

我们的第一个查询协议是正确的，但没有考虑分布式应用程序的执行。如果我们想在查询设备 Actor 的 Actor 中实现重发（因为请求超时），或者如果我们想查询多个 Actor，我们需要能够关联请求和响应。因此，我们在消息中再添加一个字段，这样请求者就可以提供一个 ID（我们将在稍后的步骤中将此代码添加到我们的应用程序中）：

```java
public static final class ReadTemperature {
  final long requestId;

  public ReadTemperature(long requestId) {
    this.requestId = requestId;
  }
}

public static final class RespondTemperature {
  final long requestId;
  final Optional<Double> value;

  public RespondTemperature(long requestId, Optional<Double> value) {
    this.requestId = requestId;
    this.value = value;
  }
}
```
## 定义设备 Actor 及其读取协议

正如我们在`Hello World`示例中了解到的，每个 Actor 都定义了它接受的消息类型。我们的设备 Actor 有责任为给定查询的响应使用相同的 ID 参数，这将使它看起来像下面这样。

```java
import java.util.Optional;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

class Device extends AbstractActor {
  private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  final String groupId;

  final String deviceId;

  public Device(String groupId, String deviceId) {
    this.groupId = groupId;
    this.deviceId = deviceId;
  }

  public static Props props(String groupId, String deviceId) {
    return Props.create(Device.class, () -> new Device(groupId, deviceId));
  }

  public static final class ReadTemperature {
    final long requestId;

    public ReadTemperature(long requestId) {
      this.requestId = requestId;
    }
  }

  public static final class RespondTemperature {
    final long requestId;
    final Optional<Double> value;

    public RespondTemperature(long requestId, Optional<Double> value) {
      this.requestId = requestId;
      this.value = value;
    }
  }

  Optional<Double> lastTemperatureReading = Optional.empty();

  @Override
  public void preStart() {
    log.info("Device actor {}-{} started", groupId, deviceId);
  }

  @Override
  public void postStop() {
    log.info("Device actor {}-{} stopped", groupId, deviceId);
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
            .match(ReadTemperature.class, r -> {
              getSender().tell(new RespondTemperature(r.requestId, lastTemperatureReading), getSelf());
            })
            .build();
  }
}
```
在上述代码中需要注意：

- 静态方法定义了如何构造`Device` Actor。`props`参数包括设备及其所属组的 ID，稍后我们将使用该 ID。
- 这个类包含了我们先前讨论过的消息的定义。
- 在`Device`类中，`lastTemperatureReading`的值最初设置为`Optional.empty()`，在查询的时候，Actor 将报告它。

## 测试 Actor

基于上面的简单 Actor，我们可以编写一个简单的测试。你可以在此处的「[快速入门指南测试示例](https://developer.lightbend.com/guides/akka-quickstart-java/testing-actors.html)」中检查 Actor 测试的完整示例。你将在这里找到一个关于如何完全设置 Actor 测试的示例，以便正确地运行它。

在项目的测试目录中，将以下代码添加到`DeviceTest.java`文件中。

你可以通过`mvn test`或`sbt`命令来运行此测试代码。

```java
@Test
public void testReplyWithEmptyReadingIfNoTemperatureIsKnown() {
  TestKit probe = new TestKit(system);
  ActorRef deviceActor = system.actorOf(Device.props("group", "device"));
  deviceActor.tell(new Device.ReadTemperature(42L), probe.getRef());
  Device.RespondTemperature response = probe.expectMsgClass(Device.RespondTemperature.class);
  assertEquals(42L, response.requestId);
  assertEquals(Optional.empty(), response.value);
}
```

现在，当 Actor 收到来自传感器的信息时，它需要一种方法来改变温度的状态。

## 添加写入协议

写入协议（`write protocol`）的目的是在 Actor 收到包含温度的消息时更新`currentTemperature`字段。同样，将写入协议定义为一个非常简单的消息是很有吸引力的，比如：

```java
public static final class RecordTemperature {
  final double value;

  public RecordTemperature(double value) {
    this.value = value;
  }
}
```
但是，这种方法没有考虑到记录温度消息的发送者永远无法确定消息是否被处理。我们已经看到，Akka 不保证这些消息的传递，并将其留给应用程序以提供成功通知。在我们的情况下，一旦我们更新了上次的温度记录，例如`TemperatureRecorded`，我们希望向发送方发送确认。就像在温度查询和响应的情况下一样，最好包含一个 ID 字段以提供最大的灵活性。

## 具有读写消息的 Actor

将读写协议放在一起，设备 Actor 如下所示：

```java
import java.util.Optional;

import akka.actor.AbstractActor;
import akka.actor.AbstractActor.Receive;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class Device extends AbstractActor {
  private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  final String groupId;

  final String deviceId;

  public Device(String groupId, String deviceId) {
    this.groupId = groupId;
    this.deviceId = deviceId;
  }

  public static Props props(String groupId, String deviceId) {
    return Props.create(Device.class, () -> new Device(groupId, deviceId));
  }

  public static final class RecordTemperature {
    final long requestId;
    final double value;

    public RecordTemperature(long requestId, double value) {
      this.requestId = requestId;
      this.value = value;
    }
  }

  public static final class TemperatureRecorded {
    final long requestId;

    public TemperatureRecorded(long requestId) {
      this.requestId = requestId;
    }
  }

  public static final class ReadTemperature {
    final long requestId;

    public ReadTemperature(long requestId) {
      this.requestId = requestId;
    }
  }

  public static final class RespondTemperature {
    final long requestId;
    final Optional<Double> value;

    public RespondTemperature(long requestId, Optional<Double> value) {
      this.requestId = requestId;
      this.value = value;
    }
  }

  Optional<Double> lastTemperatureReading = Optional.empty();

  @Override
  public void preStart() {
    log.info("Device actor {}-{} started", groupId, deviceId);
  }

  @Override
  public void postStop() {
    log.info("Device actor {}-{} stopped", groupId, deviceId);
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
            .match(RecordTemperature.class, r -> {
              log.info("Recorded temperature reading {} with {}", r.value, r.requestId);
              lastTemperatureReading = Optional.of(r.value);
              getSender().tell(new TemperatureRecorded(r.requestId), getSelf());
            })
            .match(ReadTemperature.class, r -> {
              getSender().tell(new RespondTemperature(r.requestId, lastTemperatureReading), getSelf());
            })
            .build();
  }
}
```
我们现在还应该编写一个新的测试用例，同时使用读/查询和写/记录功能：

```java
@Test
public void testReplyWithLatestTemperatureReading() {
  TestKit probe = new TestKit(system);
  ActorRef deviceActor = system.actorOf(Device.props("group", "device"));

  deviceActor.tell(new Device.RecordTemperature(1L, 24.0), probe.getRef());
  assertEquals(1L, probe.expectMsgClass(Device.TemperatureRecorded.class).requestId);

  deviceActor.tell(new Device.ReadTemperature(2L), probe.getRef());
  Device.RespondTemperature response1 = probe.expectMsgClass(Device.RespondTemperature.class);
  assertEquals(2L, response1.requestId);
  assertEquals(Optional.of(24.0), response1.value);

  deviceActor.tell(new Device.RecordTemperature(3L, 55.0), probe.getRef());
  assertEquals(3L, probe.expectMsgClass(Device.TemperatureRecorded.class).requestId);

  deviceActor.tell(new Device.ReadTemperature(4L), probe.getRef());
  Device.RespondTemperature response2 = probe.expectMsgClass(Device.RespondTemperature.class);
  assertEquals(4L, response2.requestId);
  assertEquals(Optional.of(55.0), response2.value);
}
```

## 下一步是什么？

到目前为止，我们已经开始设计我们的总体架构，并且我们编写了第一个直接对应于域的 Actor。我们现在必须创建负责维护设备组和设备 Actor 本身的组件。

----------

**英文原文链接**：[Part 3: Working with Device Actors](https://doc.akka.io/docs/akka/current/guide/tutorial_3.html).

----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————