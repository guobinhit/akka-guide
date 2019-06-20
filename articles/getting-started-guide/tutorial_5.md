# 第 5 部分: 查询设备组
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
到目前为止，我们所看到的对话模式很简单，因为它们要求 Actor 保持很少或根本就没有状态。明确地：

- 设备 Actor 返回一个不需要状态更改的读取
- 记录温度，更新单个字段
- 设备组 Actor 通过添加或删除映射中的条目来维护组成员身份

在本部分中，我们将使用一个更复杂的示例。由于房主会对整个家庭的温度感兴趣，我们的目标是能够查询一个组中的所有设备 Actor。让我们先研究一下这样的查询 API 应该如何工作。

## 处理可能的情况
我们面临的第一个问题是，一个组的成员是动态的。每个传感器设备都由一个可以随时停止的 Actor 表示。在查询开始时，我们可以询问所有现有设备 Actor 当前的温度。但是，在查询的生命周期中：

- 设备 Actor 可能会停止工作，无法用温度读数做出响应。
- 一个新的设备 Actor 可能会启动，并且不会包含在查询中，因为我们不知道它。

这些问题可以用许多不同的方式来解决，但重要的是要解决所期望的行为。以下工作对于我们的用例是很有用的：

- 当查询到达时，组 Actor 将获取现有设备 Actor 的快照（`snapshot`），并且只向这些 Actor 询问温度。
- 查询到达后启动的 Actor 可以被忽略。
- 如果快照中的某个 Actor 在查询期间停止而没有应答，我们将向查询消息的发送者报告它停止的事实。

除了设备 Actor 动态地变化之外，一些 Actor 可能需要很长时间来响应。例如，它们可能被困在一个意外的无限循环中，或者由于一个 bug 而失败，并放弃我们的请求。我们不希望查询无限期地继续，因此在以下任何一种情况下，我们都会认为它是完成的：

- 快照中的所有 Actor 要么已响应，要么确认已停止。
- 我们达到了预定的（`pre-defined`）最后期限。

考虑到这些决定，再加上快照中的设备可能刚刚启动但尚未接收到要记录的温度，我们可以针对温度查询为每个设备 Actor 定义四种状态：

- 它有一个可用的温度：`Temperature`。
- 它已经响应，但还没有可用的温度：`TemperatureNotAvailable`。
- 它在响应之前已停止：`DeviceNotAvailable`。
- 它在最后期限之前没有响应：`DeviceTimedOut`。

在消息类型中汇总这些信息，我们可以将以下代码添加到`DeviceGroup`：

```java
public static final class RequestAllTemperatures {
  final long requestId;

  public RequestAllTemperatures(long requestId) {
    this.requestId = requestId;
  }
}

public static final class RespondAllTemperatures {
  final long requestId;
  final Map<String, TemperatureReading> temperatures;

  public RespondAllTemperatures(long requestId, Map<String, TemperatureReading> temperatures) {
    this.requestId = requestId;
    this.temperatures = temperatures;
  }
}

public static interface TemperatureReading {
}

public static final class Temperature implements TemperatureReading {
  public final double value;

  public Temperature(double value) {
    this.value = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Temperature that = (Temperature) o;

    return Double.compare(that.value, value) == 0;
  }

  @Override
  public int hashCode() {
    long temp = Double.doubleToLongBits(value);
    return (int) (temp ^ (temp >>> 32));
  }

  @Override
  public String toString() {
    return "Temperature{" +
      "value=" + value +
      '}';
  }
}

public enum TemperatureNotAvailable implements TemperatureReading {
  INSTANCE
}

public enum DeviceNotAvailable implements TemperatureReading {
  INSTANCE
}

public enum DeviceTimedOut implements TemperatureReading {
  INSTANCE
}
```
## 实现查询功能
实现查询的一种方法是向组设备 Actor 添加代码。然而，在实践中，这可能非常麻烦并且容易出错。请记住，当我们启动查询时，我们需要获取当前设备的快照并启动计时器，以便强制执行截止时间。同时，另一个查询可以到达。对于第二个查询，我们需要跟踪完全相同的信息，但与前一个查询隔离。这将要求我们在查询和设备 Actor 之间维护单独的映射。

相反，我们将实现一种更简单、更优雅的方法。我们将创建一个表示单个查询的 Actor，并代表组 Actor 执行完成查询所需的任务。到目前为止，我们已经创建了属于典型域对象（`classical domain`）的 Actor，但是现在，我们将创建一个表示流程或任务而不是实体的 Actor。我们通过保持我们的组设备 Actor 简单和能够更好地隔离测试查询功能而受益。

### 定义查询 Actor
首先，我们需要设计查询 Actor 的生命周期。这包括识别其初始状态、将要采取的第一个操作以及清除（如果需要）。查询 Actor 需要以下信息：

- 要查询的活动设备 Actor 的快照和 ID。
- 启动查询的请求的 ID（以便我们可以在响应中包含它）。
- 发送查询的 Actor 的引用。我们会直接给这个 Actor 响应。
- 指示查询等待响应的期限。将其作为参数将简化测试。

### 设置查询超时
由于我们需要一种方法来指示我们愿意等待响应的时间，现在是时候引入一个我们还没有使用的新的 Akka 特性，即内置的调度器（`built-in scheduler`）功能了。使用调度器（`scheduler`）很简单：

- 我们可以从`ActorSystem`中获取调度器，而`ActorSystem`又可以从 Actor 的上下文中访问：`getContext().getSystem().scheduler()`。这需要一个`ExecutionContext`，它是将执行计时器任务本身的线程池。在我们的示例中，我们通过传入`getContext().dispatcher()`来使用与 Actor 相同的调度器。
- `scheduler.scheduleOnce(time, actorRef, message, executor, sender)`方法将在指定的`time`将消息`message`调度到`Future`，并将其发送给 Actor 的`ActorRef`。

我们需要创建一个表示查询超时的消息。为此，我们创建了一个没有任何参数的简单消息`CollectionTimeout`。`scheduleOnce`的返回值是`Cancellable`，如果查询及时成功完成，可以使用它取消定时器。在查询开始时，我们需要询问每个设备 Actor 当前的温度。为了能够快速检测那些在`ReadTemperature`信息之前停止的设备，我们还将观察每个 Actor。这样，对于那些在查询生命周期中停止的消息，我们就可以得到`Terminated`消息，因此我们不需要等到超时时再将这些消息标记为不可用。

综上所述，`DeviceGroupQuery` Actor 的代码大致如下：

```java
public class DeviceGroupQuery extends AbstractActor {
  public static final class CollectionTimeout {
  }

  private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  final Map<ActorRef, String> actorToDeviceId;
  final long requestId;
  final ActorRef requester;

  Cancellable queryTimeoutTimer;

  public DeviceGroupQuery(Map<ActorRef, String> actorToDeviceId, long requestId, ActorRef requester, FiniteDuration timeout) {
    this.actorToDeviceId = actorToDeviceId;
    this.requestId = requestId;
    this.requester = requester;

    queryTimeoutTimer = getContext().getSystem().scheduler().scheduleOnce(
            timeout, getSelf(), new CollectionTimeout(), getContext().dispatcher(), getSelf()
    );
  }

  public static Props props(Map<ActorRef, String> actorToDeviceId, long requestId, ActorRef requester, FiniteDuration timeout) {
    return Props.create(DeviceGroupQuery.class, () -> new DeviceGroupQuery(actorToDeviceId, requestId, requester, timeout));
  }

  @Override
  public void preStart() {
    for (ActorRef deviceActor : actorToDeviceId.keySet()) {
      getContext().watch(deviceActor);
      deviceActor.tell(new Device.ReadTemperature(0L), getSelf());
    }
  }

  @Override
  public void postStop() {
    queryTimeoutTimer.cancel();
  }
}
```
### 跟踪 Actor 状态

除了挂起的定时器之外，查询 Actor 还有一个状态方面，它跟踪一组 Actor：已回复、已停止或未回复。跟踪此状态的一种方法是在 Actor 中创建可变字段。另一种方法利用改变 Actor 对消息的响应方式的能力。`Receive`是一个可以从另一个函数返回的函数（如果你愿意的话，也可以是对象）。默认情况下，`receive`块定义了 Actor 的行为，但在 Actor 的生命周期中可以多次更改它。我们调用`context.become(newBehavior)`，其中`newBehavior`是任何类型的`Receive`。我们将利用此功能跟踪 Actor 的状态。

对于我们的用例：

- 我们不直接定义`receive`，而是委托`waitingForReplies`函数来创建`Receive`。
- `waitingForReplies`函数将跟踪两个更改的值：
  - 已收到响应的`Map`；
  - 我们还在等待 Actor 响应的`Set`。

我们有三件事要做：

- 我们可以从其中一个设备接收`RespondTemperature`。
- 我们可以为同时被停止的设备 Actor 接收`Terminated`的消息。
- 我们可以达到截止时间（`deadline`）并收到一个`CollectionTimeout`消息。

在前两种情况下，我们需要跟踪响应，现在我们将其委托给`receivedResponse`方法，稍后我们将讨论该方法。在超时的情况下，我们需要简单地把所有还没有响应的 Actor（集合`stillWaiting`的成员）放在`DeviceTimedOut`中作为最终响应的状态。然后我们用收集到的结果回复查询提交者，并停止查询 Actor。

要完成此操作，请将以下代码添加到`DeviceGroupQuery`源文件中：

```java
@Override
public Receive createReceive() {
  return waitingForReplies(new HashMap<>(), actorToDeviceId.keySet());
}

public Receive waitingForReplies(
        Map<String, DeviceGroup.TemperatureReading> repliesSoFar,
        Set<ActorRef> stillWaiting) {
  return receiveBuilder()
          .match(Device.RespondTemperature.class, r -> {
            ActorRef deviceActor = getSender();
            DeviceGroup.TemperatureReading reading = r.value
                    .map(v -> (DeviceGroup.TemperatureReading) new DeviceGroup.Temperature(v))
                    .orElse(DeviceGroup.TemperatureNotAvailable.INSTANCE);
            receivedResponse(deviceActor, reading, stillWaiting, repliesSoFar);
          })
          .match(Terminated.class, t -> {
            receivedResponse(t.getActor(), DeviceGroup.DeviceNotAvailable.INSTANCE, stillWaiting, repliesSoFar);
          })
          .match(CollectionTimeout.class, t -> {
            Map<String, DeviceGroup.TemperatureReading> replies = new HashMap<>(repliesSoFar);
            for (ActorRef deviceActor : stillWaiting) {
              String deviceId = actorToDeviceId.get(deviceActor);
              replies.put(deviceId, DeviceGroup.DeviceTimedOut.INSTANCE);
            }
            requester.tell(new DeviceGroup.RespondAllTemperatures(requestId, replies), getSelf());
            getContext().stop(getSelf());
          })
          .build();
}
```
目前还不清楚我们将如何“改变”`repliesSoFar`和`stillWaiting`数据结构。需要注意的一点是，`waitingForReplies`函数不能直接处理消息。它返回一个`Receive`函数来处理消息。这意味着，如果我们使用不同的参数再次调用`waitingForReplies`，那么它将返回一个全新的`Receive`，该`Receive`将使用这些新参数。

我们已经看到了如何通过从`receive`的返回来安装（`install`）初始化`Receive`。例如，为了安装一个新的`Receive`，为了记录一个新的回复，我们需要一些机制。此机制是方法`context.become(newReceive)`，它将 Actor 的消息处理函数更改为提供的`newReceive`函数。可以想象，在开始之前，Actor 会自动调用`context.become(receive)`，即安装从`receive`返回的`Receive`函数。这是另一个重要的观察：处理消息的不是`receive`，而是返回一个实际处理消息的`Receive`函数。

我们现在必须弄清楚在`receivedResponse`中该怎么做。首先，我们需要在`repliesSoFar`中记录新的结果，并将 Actor 从`stillWaiting`中移除。下一步是检查是否还有其他我们正在等待的 Actor。如果没有，我们将查询结果发送给原始请求者并停止查询 Actor。否则，我们需要更新`repliesSoFar`和`stillWaiting`结构并等待更多的消息。

在之前的代码中，我们将`Terminated`视为隐式响应`DeviceNotAvailable`，因此`receivedResponse`不需要执行任何特殊操作。但是，还有一个小任务我们仍然需要做。我们可能从设备 Actor 那里接收到正确的响应，但是在查询的生命周期中，它会停止。我们不希望此第二个事件覆盖已收到的响应。换句话说，我们不希望在记录响应之后接收`Terminated`。这很容易通过调用`context.unwatch(ref)`实现。此方法还确保我们不会接收已经在 Actor 邮箱中的`Terminated`事件。多次调用此函数也是安全的，只有第一次调用才会有任何效果，其余的调用将被忽略

通过以上的分析，我们创建`receivedResponse`方法为：

```java
public void receivedResponse(ActorRef deviceActor,
                             DeviceGroup.TemperatureReading reading,
                             Set<ActorRef> stillWaiting,
                             Map<String, DeviceGroup.TemperatureReading> repliesSoFar) {
  getContext().unwatch(deviceActor);
  String deviceId = actorToDeviceId.get(deviceActor);

  Set<ActorRef> newStillWaiting = new HashSet<>(stillWaiting);
  newStillWaiting.remove(deviceActor);

  Map<String, DeviceGroup.TemperatureReading> newRepliesSoFar = new HashMap<>(repliesSoFar);
  newRepliesSoFar.put(deviceId, reading);
  if (newStillWaiting.isEmpty()) {
    requester.tell(new DeviceGroup.RespondAllTemperatures(requestId, newRepliesSoFar), getSelf());
    getContext().stop(getSelf());
  } else {
    getContext().become(waitingForReplies(newRepliesSoFar, newStillWaiting));
  }
}
```
在这一点上，我们很自然地会问，使用`context.become()`技巧，而不是使`repliesSoFar`和`stillWaiting`结构成为 Actor 的可变字段（例如，`vars`），我们获得了什么？在这个简单的例子中，没有那么多。当你突然有更多的状态时，这种状态保持的价值变得更加明显。由于每个状态可能都有与其自身相关的临时数据，因此将这些数据作为字段保存会污染 Actor 的全局状态，也就是说，不清楚在什么状态下使用了哪些字段。使用参数化的`Receive`“工厂”方法，我们可以保持仅与状态相关的数据私有化。使用可变字段而不是`context.become()`重写查询仍然是一个很好的练习。但是，建议你熟悉我们在这里使用的解决方案，因为它有助于以更干净和更可维护的方式构造更复杂的 Actor 代码。

现在，我们的查询 Actor 完成了，代码如下：

```java
public class DeviceGroupQuery extends AbstractActor {
  public static final class CollectionTimeout {
  }

  private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  final Map<ActorRef, String> actorToDeviceId;
  final long requestId;
  final ActorRef requester;

  Cancellable queryTimeoutTimer;

  public DeviceGroupQuery(Map<ActorRef, String> actorToDeviceId, long requestId, ActorRef requester, FiniteDuration timeout) {
    this.actorToDeviceId = actorToDeviceId;
    this.requestId = requestId;
    this.requester = requester;

    queryTimeoutTimer = getContext().getSystem().scheduler().scheduleOnce(
            timeout, getSelf(), new CollectionTimeout(), getContext().dispatcher(), getSelf()
    );
  }

  public static Props props(Map<ActorRef, String> actorToDeviceId, long requestId, ActorRef requester, FiniteDuration timeout) {
    return Props.create(DeviceGroupQuery.class, () -> new DeviceGroupQuery(actorToDeviceId, requestId, requester, timeout));
  }

  @Override
  public void preStart() {
    for (ActorRef deviceActor : actorToDeviceId.keySet()) {
      getContext().watch(deviceActor);
      deviceActor.tell(new Device.ReadTemperature(0L), getSelf());
    }
  }

  @Override
  public void postStop() {
    queryTimeoutTimer.cancel();
  }

  @Override
  public Receive createReceive() {
    return waitingForReplies(new HashMap<>(), actorToDeviceId.keySet());
  }

  public Receive waitingForReplies(
          Map<String, DeviceGroup.TemperatureReading> repliesSoFar,
          Set<ActorRef> stillWaiting) {
    return receiveBuilder()
            .match(Device.RespondTemperature.class, r -> {
              ActorRef deviceActor = getSender();
              DeviceGroup.TemperatureReading reading = r.value
                      .map(v -> (DeviceGroup.TemperatureReading) new DeviceGroup.Temperature(v))
                      .orElse(DeviceGroup.TemperatureNotAvailable.INSTANCE);
              receivedResponse(deviceActor, reading, stillWaiting, repliesSoFar);
            })
            .match(Terminated.class, t -> {
              receivedResponse(t.getActor(), DeviceGroup.DeviceNotAvailable.INSTANCE, stillWaiting, repliesSoFar);
            })
            .match(CollectionTimeout.class, t -> {
              Map<String, DeviceGroup.TemperatureReading> replies = new HashMap<>(repliesSoFar);
              for (ActorRef deviceActor : stillWaiting) {
                String deviceId = actorToDeviceId.get(deviceActor);
                replies.put(deviceId, DeviceGroup.DeviceTimedOut.INSTANCE);
              }
              requester.tell(new DeviceGroup.RespondAllTemperatures(requestId, replies), getSelf());
              getContext().stop(getSelf());
            })
            .build();
  }

  public void receivedResponse(ActorRef deviceActor,
                               DeviceGroup.TemperatureReading reading,
                               Set<ActorRef> stillWaiting,
                               Map<String, DeviceGroup.TemperatureReading> repliesSoFar) {
    getContext().unwatch(deviceActor);
    String deviceId = actorToDeviceId.get(deviceActor);

    Set<ActorRef> newStillWaiting = new HashSet<>(stillWaiting);
    newStillWaiting.remove(deviceActor);

    Map<String, DeviceGroup.TemperatureReading> newRepliesSoFar = new HashMap<>(repliesSoFar);
    newRepliesSoFar.put(deviceId, reading);
    if (newStillWaiting.isEmpty()) {
      requester.tell(new DeviceGroup.RespondAllTemperatures(requestId, newRepliesSoFar), getSelf());
      getContext().stop(getSelf());
    } else {
      getContext().become(waitingForReplies(newRepliesSoFar, newStillWaiting));
    }
  }
}
```
### 测试查询 Actor
现在，让我们验证查询 Actor 实现的正确性。我们需要单独测试各种场景，以确保一切都按预期工作。为了能够做到这一点，我们需要以某种方式模拟设备 Actor 来运行各种正常或故障场景。幸运的是，我们将合作者（`collaborators`）列表（实际上是一个`Map`）作为查询 Actor 的参数，这样我们就可以传入`TestKit`引用。在我们的第一个测试中，我们在有两个设备的情况下进行测试，两个设备都报告了温度：

```java
@Test
public void testReturnTemperatureValueForWorkingDevices() {
  TestKit requester = new TestKit(system);

  TestKit device1 = new TestKit(system);
  TestKit device2 = new TestKit(system);

  Map<ActorRef, String> actorToDeviceId = new HashMap<>();
  actorToDeviceId.put(device1.getRef(), "device1");
  actorToDeviceId.put(device2.getRef(), "device2");

  ActorRef queryActor = system.actorOf(DeviceGroupQuery.props(
          actorToDeviceId,
          1L,
          requester.getRef(),
          new FiniteDuration(3, TimeUnit.SECONDS)));

  assertEquals(0L, device1.expectMsgClass(Device.ReadTemperature.class).requestId);
  assertEquals(0L, device2.expectMsgClass(Device.ReadTemperature.class).requestId);

  queryActor.tell(new Device.RespondTemperature(0L, Optional.of(1.0)), device1.getRef());
  queryActor.tell(new Device.RespondTemperature(0L, Optional.of(2.0)), device2.getRef());

  DeviceGroup.RespondAllTemperatures response = requester.expectMsgClass(DeviceGroup.RespondAllTemperatures.class);
  assertEquals(1L, response.requestId);

  Map<String, DeviceGroup.TemperatureReading> expectedTemperatures = new HashMap<>();
  expectedTemperatures.put("device1", new DeviceGroup.Temperature(1.0));
  expectedTemperatures.put("device2", new DeviceGroup.Temperature(2.0));

  assertEquals(expectedTemperatures, response.temperatures);
}
```
这是一个很好的例子，但我们知道有时设备不能提供温度测量。这种情况与前一种情况略有不同：

```java
@Test
public void testReturnTemperatureNotAvailableForDevicesWithNoReadings() {
  TestKit requester = new TestKit(system);

  TestKit device1 = new TestKit(system);
  TestKit device2 = new TestKit(system);

  Map<ActorRef, String> actorToDeviceId = new HashMap<>();
  actorToDeviceId.put(device1.getRef(), "device1");
  actorToDeviceId.put(device2.getRef(), "device2");

  ActorRef queryActor = system.actorOf(DeviceGroupQuery.props(
          actorToDeviceId,
          1L,
          requester.getRef(),
          new FiniteDuration(3, TimeUnit.SECONDS)));

  assertEquals(0L, device1.expectMsgClass(Device.ReadTemperature.class).requestId);
  assertEquals(0L, device2.expectMsgClass(Device.ReadTemperature.class).requestId);

  queryActor.tell(new Device.RespondTemperature(0L, Optional.empty()), device1.getRef());
  queryActor.tell(new Device.RespondTemperature(0L, Optional.of(2.0)), device2.getRef());

  DeviceGroup.RespondAllTemperatures response = requester.expectMsgClass(DeviceGroup.RespondAllTemperatures.class);
  assertEquals(1L, response.requestId);

  Map<String, DeviceGroup.TemperatureReading> expectedTemperatures = new HashMap<>();
  expectedTemperatures.put("device1", DeviceGroup.TemperatureNotAvailable.INSTANCE);
  expectedTemperatures.put("device2", new DeviceGroup.Temperature(2.0));

  assertEquals(expectedTemperatures, response.temperatures);
}
```
我们也知道，有时设备 Actor 会在响应之前停止：

```java
@Test
public void testReturnDeviceNotAvailableIfDeviceStopsBeforeAnswering() {
  TestKit requester = new TestKit(system);

  TestKit device1 = new TestKit(system);
  TestKit device2 = new TestKit(system);

  Map<ActorRef, String> actorToDeviceId = new HashMap<>();
  actorToDeviceId.put(device1.getRef(), "device1");
  actorToDeviceId.put(device2.getRef(), "device2");

  ActorRef queryActor = system.actorOf(DeviceGroupQuery.props(
          actorToDeviceId,
          1L,
          requester.getRef(),
          new FiniteDuration(3, TimeUnit.SECONDS)));

  assertEquals(0L, device1.expectMsgClass(Device.ReadTemperature.class).requestId);
  assertEquals(0L, device2.expectMsgClass(Device.ReadTemperature.class).requestId);

  queryActor.tell(new Device.RespondTemperature(0L, Optional.of(1.0)), device1.getRef());
  device2.getRef().tell(PoisonPill.getInstance(), ActorRef.noSender());

  DeviceGroup.RespondAllTemperatures response = requester.expectMsgClass(DeviceGroup.RespondAllTemperatures.class);
  assertEquals(1L, response.requestId);

  Map<String, DeviceGroup.TemperatureReading> expectedTemperatures = new HashMap<>();
  expectedTemperatures.put("device1", new DeviceGroup.Temperature(1.0));
  expectedTemperatures.put("device2", DeviceGroup.DeviceNotAvailable.INSTANCE);

  assertEquals(expectedTemperatures, response.temperatures);
}
```
如果你还记得，还有一个用例与设备 Actor 停止相关。我们可以从一个设备 Actor 得到一个正常的回复，但是随后接收到同一个 Actor 的一个`Terminated`消息。在这种情况下，我们希望保持第一次回复，而不是将设备标记为`DeviceNotAvailable`。我们也应该测试一下：

```java
@Test
public void testReturnTemperatureReadingEvenIfDeviceStopsAfterAnswering() {
  TestKit requester = new TestKit(system);

  TestKit device1 = new TestKit(system);
  TestKit device2 = new TestKit(system);

  Map<ActorRef, String> actorToDeviceId = new HashMap<>();
  actorToDeviceId.put(device1.getRef(), "device1");
  actorToDeviceId.put(device2.getRef(), "device2");

  ActorRef queryActor = system.actorOf(DeviceGroupQuery.props(
          actorToDeviceId,
          1L,
          requester.getRef(),
          new FiniteDuration(3, TimeUnit.SECONDS)));

  assertEquals(0L, device1.expectMsgClass(Device.ReadTemperature.class).requestId);
  assertEquals(0L, device2.expectMsgClass(Device.ReadTemperature.class).requestId);

  queryActor.tell(new Device.RespondTemperature(0L, Optional.of(1.0)), device1.getRef());
  queryActor.tell(new Device.RespondTemperature(0L, Optional.of(2.0)), device2.getRef());
  device2.getRef().tell(PoisonPill.getInstance(), ActorRef.noSender());

  DeviceGroup.RespondAllTemperatures response = requester.expectMsgClass(DeviceGroup.RespondAllTemperatures.class);
  assertEquals(1L, response.requestId);

  Map<String, DeviceGroup.TemperatureReading> expectedTemperatures = new HashMap<>();
  expectedTemperatures.put("device1", new DeviceGroup.Temperature(1.0));
  expectedTemperatures.put("device2", new DeviceGroup.Temperature(2.0));

  assertEquals(expectedTemperatures, response.temperatures);
}
```
最后一种情况是，并非所有设备都能及时响应。为了保持我们的测试相对较快，我们将用较小的超时构造`DeviceGroupQuery` Actor：

```java
@Test
public void testReturnDeviceTimedOutIfDeviceDoesNotAnswerInTime() {
  TestKit requester = new TestKit(system);

  TestKit device1 = new TestKit(system);
  TestKit device2 = new TestKit(system);

  Map<ActorRef, String> actorToDeviceId = new HashMap<>();
  actorToDeviceId.put(device1.getRef(), "device1");
  actorToDeviceId.put(device2.getRef(), "device2");

  ActorRef queryActor = system.actorOf(DeviceGroupQuery.props(
          actorToDeviceId,
          1L,
          requester.getRef(),
          new FiniteDuration(1, TimeUnit.SECONDS)));

  assertEquals(0L, device1.expectMsgClass(Device.ReadTemperature.class).requestId);
  assertEquals(0L, device2.expectMsgClass(Device.ReadTemperature.class).requestId);

  queryActor.tell(new Device.RespondTemperature(0L, Optional.of(1.0)), device1.getRef());

  DeviceGroup.RespondAllTemperatures response = requester.expectMsgClass(
          java.time.Duration.ofSeconds(5),
          DeviceGroup.RespondAllTemperatures.class);
  assertEquals(1L, response.requestId);

  Map<String, DeviceGroup.TemperatureReading> expectedTemperatures = new HashMap<>();
  expectedTemperatures.put("device1", new DeviceGroup.Temperature(1.0));
  expectedTemperatures.put("device2", DeviceGroup.DeviceTimedOut.INSTANCE);

  assertEquals(expectedTemperatures, response.temperatures);
}
```
查询功能已经按预期工作了，现在是时候在`DeviceGroup` Actor 中添加这个新功能了。

## 向设备组添加查询功能

现在在设备组 Actor 中包含查询功能相当简单。我们在查询 Actor 本身中完成了所有繁重的工作，设备组 Actor 只需要使用正确的初始参数创建它，而不需要其他任何参数。

```java
public class DeviceGroup extends AbstractActor {
  private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  final String groupId;

  public DeviceGroup(String groupId) {
    this.groupId = groupId;
  }

  public static Props props(String groupId) {
    return Props.create(DeviceGroup.class, () -> new DeviceGroup(groupId));
  }

  public static final class RequestDeviceList {
    final long requestId;

    public RequestDeviceList(long requestId) {
      this.requestId = requestId;
    }
  }

  public static final class ReplyDeviceList {
    final long requestId;
    final Set<String> ids;

    public ReplyDeviceList(long requestId, Set<String> ids) {
      this.requestId = requestId;
      this.ids = ids;
    }
  }

  public static final class RequestAllTemperatures {
    final long requestId;

    public RequestAllTemperatures(long requestId) {
      this.requestId = requestId;
    }
  }

  public static final class RespondAllTemperatures {
    final long requestId;
    final Map<String, TemperatureReading> temperatures;

    public RespondAllTemperatures(long requestId, Map<String, TemperatureReading> temperatures) {
      this.requestId = requestId;
      this.temperatures = temperatures;
    }
  }

  public static interface TemperatureReading {
  }

  public static final class Temperature implements TemperatureReading {
    public final double value;

    public Temperature(double value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Temperature that = (Temperature) o;

      return Double.compare(that.value, value) == 0;
    }

    @Override
    public int hashCode() {
      long temp = Double.doubleToLongBits(value);
      return (int) (temp ^ (temp >>> 32));
    }

    @Override
    public String toString() {
      return "Temperature{" +
        "value=" + value +
        '}';
    }
  }

  public enum TemperatureNotAvailable implements TemperatureReading {
    INSTANCE
  }

  public enum DeviceNotAvailable implements TemperatureReading {
    INSTANCE
  }

  public enum DeviceTimedOut implements TemperatureReading {
    INSTANCE
  }


  final Map<String, ActorRef> deviceIdToActor = new HashMap<>();
  final Map<ActorRef, String> actorToDeviceId = new HashMap<>();

  @Override
  public void preStart() {
    log.info("DeviceGroup {} started", groupId);
  }

  @Override
  public void postStop() {
    log.info("DeviceGroup {} stopped", groupId);
  }


  private void onAllTemperatures(RequestAllTemperatures r) {
    // since Java collections are mutable, we want to avoid sharing them between actors (since multiple Actors (threads)
    // modifying the same mutable data-structure is not safe), and perform a defensive copy of the mutable map:
    //
    // Feel free to use your favourite immutable data-structures library with Akka in Java applications!
    Map<ActorRef, String> actorToDeviceIdCopy = new HashMap<>(this.actorToDeviceId);

    getContext().actorOf(DeviceGroupQuery.props(
        actorToDeviceIdCopy, r.requestId, getSender(), new FiniteDuration(3, TimeUnit.SECONDS)));
  }

  @Override
  public Receive createReceive() {
            // ... other cases omitted
            .match(RequestAllTemperatures.class, this::onAllTemperatures)
            .build();
  }
}
```
或许值得重述一下我们在本章开头所说的话。通过将只与查询本身相关的临时状态保留在单独的 Actor 中，我们使组 Actor 的实现非常简单。它将一切委托给子 Actor，因此不必保留与核心业务无关的状态。此外，多个查询现在可以彼此并行运行，事实上，可以根据需要运行任意多个查询。在我们的例子中，查询单个设备 Actor 是一种快速操作，但是如果不是这样，例如，因为需要通过网络联系远程传感器，这种设计将显著提高吞吐量。

我们通过测试所有的功能一起工作来结束这一章。此测试是前一个测试的变体，现在使用组查询功能：

```java
@Test
public void testCollectTemperaturesFromAllActiveDevices() {
  TestKit probe = new TestKit(system);
  ActorRef groupActor = system.actorOf(DeviceGroup.props("group"));

  groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"), probe.getRef());
  probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
  ActorRef deviceActor1 = probe.getLastSender();

  groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device2"), probe.getRef());
  probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
  ActorRef deviceActor2 = probe.getLastSender();

  groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device3"), probe.getRef());
  probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
  ActorRef deviceActor3 = probe.getLastSender();

  // Check that the device actors are working
  deviceActor1.tell(new Device.RecordTemperature(0L, 1.0), probe.getRef());
  assertEquals(0L, probe.expectMsgClass(Device.TemperatureRecorded.class).requestId);
  deviceActor2.tell(new Device.RecordTemperature(1L, 2.0), probe.getRef());
  assertEquals(1L, probe.expectMsgClass(Device.TemperatureRecorded.class).requestId);
  // No temperature for device 3

  groupActor.tell(new DeviceGroup.RequestAllTemperatures(0L), probe.getRef());
  DeviceGroup.RespondAllTemperatures response = probe.expectMsgClass(DeviceGroup.RespondAllTemperatures.class);
  assertEquals(0L, response.requestId);

  Map<String, DeviceGroup.TemperatureReading> expectedTemperatures = new HashMap<>();
  expectedTemperatures.put("device1", new DeviceGroup.Temperature(1.0));
  expectedTemperatures.put("device2", new DeviceGroup.Temperature(2.0));
  expectedTemperatures.put("device3", DeviceGroup.TemperatureNotAvailable.INSTANCE);

  assertEquals(expectedTemperatures, response.temperatures);
}
```
## 总结
在物联网（`IoT`）系统的背景下，本指南介绍了以下概念。如有必要，你可以通过以下链接进行查看：

- [Actor 的层级结构及其生命周期](https://github.com/guobinhit/akka-guide/blob/master/articles/getting-started-guide/tutorial_1.md)
- [灵活性设计消息的重要性](https://github.com/guobinhit/akka-guide/blob/master/articles/getting-started-guide/tutorial_3.md)
- [如何监视和停止 Actor](https://github.com/guobinhit/akka-guide/blob/master/articles/getting-started-guide/tutorial_4.md)

## 下一步是什么？
要继续你的 Akka 之旅，我们建议：

- 开始用 Akka 构建你自己的应用程序，如果你陷入困境的话，希望你能参与到我们的「[社区](https://akka.io/get-involved/)」中，寻求帮助。
- 如果你想了解更多的背景知识，请阅读参考文件的其余部分，并查看一些关于 Akka 的「[书籍和视频](https://doc.akka.io/docs/akka/current/additional/books.html)」。

要从本指南获得完整的应用程序，你可能需要提供 UI 或 API。为此，我们建议你查看以下技术，看看哪些适合你：

- 「[Akka HTTP](https://doc.akka.io/docs/akka/current/additional/books.html)」是一个 HTTP 服务和客户端的库，使发布和使用 HTTP 端点（`endpoints`）成为可能。
- 「[Play Framework](https://doc.akka.io/docs/akka/current/additional/books.html)」是一个成熟的 Web 框架，它构建在 Akka HTTP 之上，它能与 Akka 很好地集成，可用于创建一个完整的现代 Web 用户界面。
- 「[Lagom](https://doc.akka.io/docs/akka/current/additional/books.html)」是一个基于 Akka 的独立的微服务框架，它编码了 Akka 和 Play 的许多最佳实践。

----------

**英文原文链接**：[Part 5: Querying Device Groups](https://doc.akka.io/docs/akka/current/guide/tutorial_5.html).

----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————