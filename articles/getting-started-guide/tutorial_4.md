# 第 4 部分: 使用设备组
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
让我们仔细看看用例所需的主要功能。在用于监测家庭温度的完整物联网系统中，将设备传感器连接到系统的步骤可能如下：

 1. 家庭中的传感器设备通过某种协议进行连接。
 2. 管理网络连接的组件接受连接。
 3. 传感器提供其组和设备 ID，以便在系统的设备管理器组件中注册。
 4. 设备管理器组件通过查找或创建负责保持传感器状态的 Actor 来处理注册。
 5. Actor 以一种确认（`acknowledgement`）回应，暴露其`ActorRef`。
 6. 网络组件现在使用`ActorRef`在传感器和设备 Actor 之间进行通信，而不需要经过设备管理器。

步骤 1 和 2 发生在教程系统的边界之外。在本章中，我们将开始处理步骤 3 - 6，并创建传感器在系统中注册和与 Actor 通信的方法。但首先，我们有另一个体系结构决策——我们应该使用多少个层次的 Actor 来表示设备组和设备传感器？

Akka 程序员面临的主要设计挑战之一是为 Actor 选择最佳的粒度。在实践中，根据 Actor 之间交互的特点，通常有几种有效的方法来组织系统。例如，在我们的用例中，可能有一个 Actor 维护所有的组和设备——或许可以使用哈希表（`hash maps`）。对于每个跟踪同一个家中所有设备状态的组来说，有一个 Actor 也是合理的。

以下指导原则可以帮助我们选择最合适的 Actor 层次结构：

- 一般来说，更倾向于更大的粒度。引入比需要更多的细粒度 Actor 会导致比它解决的问题更多的问题。
- 当系统需要时添加更细的粒度：
  - 更高的并发性。
  - 有许多状态的 Actor 之间的复杂交互。在下一章中，我们将看到一个很好的例子。
  - 足够多的状态，划分为较小的 Actor 是有意义地。
  - 多重无关责任。使用不同的 Actor 可以使单个 Actor 失败并恢复，而对其他的 Actor 影响很小。

## 设备管理器层次结构

考虑到上一节中概述的原则，我们将设备管理器组件建模为具有三个级别的 Actor 树：

- 顶级监督者 Actor 表示设备的系统组件。它也是查找和创建设备组和设备 Actor 的入口点。
- 在下一个级别，每个组 Actor 都监督设备 Actor 使用同一个组 ID。它们还提供服务，例如查询组中所有可用设备的温度读数。
- 设备 Actor 管理与实际设备传感器的所有交互，例如存储温度读数。

![device-manager](https://github.com/guobinhit/akka-guide/blob/master/images/getting-started-guide/tutorial_4/device-manager.png)

我们选择这三层架构的原因如下：

- 划分组为单独的 Actor：
  - 隔离组中发生的故障。如果一个 Actor 管理所有设备组，则一个组中导致重新启动的错误将清除组的状态，否则这些组不会出现故障。
  - 简化了查询属于一个组的所有设备的问题。每个组 Actor 只包含与其组相关的状态。
  - 提高系统的并行性。因为每个组都有一个专用的 Actor，所以它们可以并发运行，我们可以并发查询多个组。
- 将传感器建模为单个设备 Actor：
  - 将一个设备 Actor 的故障与组中的其他设备隔离开来。
  - 增加收集温度读数的平行度。来自不同传感器的网络连接直接与各自的设备 Actor 通信，从而减少了竞争点。

定义了设备体系结构后，我们就可以开始研究注册传感器的协议了。

## 注册协议

作为第一步，我们需要设计协议来注册一个设备，以及创建负责它的组和设备 Actor。此协议将由`DeviceManager`组件本身提供，因为它是唯一已知且预先可用的 Actor：设备组和设备 Actor 是按需创建的。

更详细地看一下注册，我们可以概述必要的功能：

- 当`DeviceManager`接收到具有组和设备 ID 的请求时：
  - 如果管理器已经有了设备组的 Actor，那么它会将请求转发给它。
  - 否则，它会创建一个新的设备组 Actor，然后转发请求。
- `DeviceGroup` Actor 接收为给定设备注册 Actor 的请求：
  - 如果组已经有设备的 Actor，则组 Actor 将请求转发给设备 Actor。
  - 否则，设备组 Actor 首先创建设备 Actor，然后转发请求。
- 设备 Actor 接收请求并向原始发送者发送确认。由于设备 Actor 确认接收（而不是组 Actor），传感器现在将有`ActorRef`，可以直接向其 Actor 发送消息。

我们将用来传递注册请求及其确认的消息有一个简单的定义：

```java
public static final class RequestTrackDevice {
  public final String groupId;
  public final String deviceId;

  public RequestTrackDevice(String groupId, String deviceId) {
    this.groupId = groupId;
    this.deviceId = deviceId;
  }
}

public static final class DeviceRegistered {
}
```
在这种情况下，我们在消息中没有包含请求 ID 字段。由于注册只发生一次，当组件将系统连接到某个网络协议时，ID 并不重要。但是，包含请求 ID 通常是一种最佳实践。

现在，我们将从头开始实现该协议。在实践中，自上向下和自下而上的方法都很有效，但是在我们的例子中，我们实使用自下而上的方法，因为它允许我们立即为新特性编写测试，而不需要模拟出稍后需要构建的部分。

## 向设备 Actor 添加注册支持

在我们的层次结构的底部是`Device` Actor。他们在注册过程中的工作很简单：回复注册请求并向发送者确认。对于带有不匹配的组或设备 ID 的请求，添加一个保护措施也是明智的。

我们假设注册消息发送者的 ID 保留在上层。我们将在下一节向你展示如何实现这一点。

设备 Actor 的注册代码如下所示：

```java
import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import jdocs.tutorial_4.DeviceManager.DeviceRegistered;
import jdocs.tutorial_4.DeviceManager.RequestTrackDevice;

import java.util.Optional;

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
            .match(RequestTrackDevice.class, r -> {
              if (this.groupId.equals(r.groupId) && this.deviceId.equals(r.deviceId)) {
                getSender().tell(new DeviceRegistered(), getSelf());
              } else {
                log.warning(
                        "Ignoring TrackDevice request for {}-{}.This actor is responsible for {}-{}.",
                        r.groupId, r.deviceId, this.groupId, this.deviceId
                );
              }
            })
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
我们现在可以编写两个新的测试用例，一个成功注册，另一个在 ID 不匹配时测试用例：

```java
@Test
public void testReplyToRegistrationRequests() {
  TestKit probe = new TestKit(system);
  ActorRef deviceActor = system.actorOf(Device.props("group", "device"));

  deviceActor.tell(new DeviceManager.RequestTrackDevice("group", "device"), probe.getRef());
  probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
  assertEquals(deviceActor, probe.getLastSender());
}

@Test
public void testIgnoreWrongRegistrationRequests() {
  TestKit probe = new TestKit(system);
  ActorRef deviceActor = system.actorOf(Device.props("group", "device"));

  deviceActor.tell(new DeviceManager.RequestTrackDevice("wrongGroup", "device"), probe.getRef());
  probe.expectNoMessage();

  deviceActor.tell(new DeviceManager.RequestTrackDevice("group", "wrongDevice"), probe.getRef());
  probe.expectNoMessage();
}
```
- **注释**：我们使用了`TestKit`中的`expectNoMsg()`帮助者方法。此断言等待到定义的时间限制，如果在此期间收到任何消息，则会失败。如果在等待期间未收到任何消息，则断言通过。通常最好将这些超时保持在较低的水平（但不要太低），因为它们会增加大量的测试执行时间。

## 向设备组 Actor 添加注册支持
我们已经完成了设备级别的注册支持，现在我们必须在组级别实现它。当涉及到注册时，组 Actor 有更多的工作要做，包括：

- 通过将注册请求转发给现有设备 Actor 或创建新 Actor 并转发消息来处理注册请求。
- 跟踪组中存在哪些设备 Actor，并在停止时将其从组中删除。

### 处理注册请求
设备组 Actor 必须将请求转发给现有的子 Actor，或者应该创建一个子 Actor。要通过设备 ID 查找子 Actor，我们将使用`Map<String, ActorRef>`。

我们还希望保留请求的原始发送者的 ID，以便设备 Actor 可以直接回复。这可以通过使用`forward`而不是`tell`运算符来实现。两者之间的唯一区别是，`forward`保留原始发送者，而`tell`将发送者设置为当前 Actor。就像我们的设备 Actor 一样，我们确保不响应错误的组 ID。将以下内容添加到你的源文件中：

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

  final Map<String, ActorRef> deviceIdToActor = new HashMap<>();

  @Override
  public void preStart() {
    log.info("DeviceGroup {} started", groupId);
  }

  @Override
  public void postStop() {
    log.info("DeviceGroup {} stopped", groupId);
  }

  private void onTrackDevice(DeviceManager.RequestTrackDevice trackMsg) {
    if (this.groupId.equals(trackMsg.groupId)) {
      ActorRef deviceActor = deviceIdToActor.get(trackMsg.deviceId);
      if (deviceActor != null) {
        deviceActor.forward(trackMsg, getContext());
      } else {
        log.info("Creating device actor for {}", trackMsg.deviceId);
        deviceActor = getContext().actorOf(Device.props(groupId, trackMsg.deviceId), "device-" + trackMsg.deviceId);
        deviceIdToActor.put(trackMsg.deviceId, deviceActor);
        deviceActor.forward(trackMsg, getContext());
      }
    } else {
      log.warning(
              "Ignoring TrackDevice request for {}. This actor is responsible for {}.",
              groupId, this.groupId
      );
    }
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
            .match(DeviceManager.RequestTrackDevice.class, this::onTrackDevice)
            .build();
  }
}
```
正如我们对设备所做的那样，我们测试了这个新功能。我们还测试了两个不同 ID 返回的 Actor 实际上是不同的，我们还尝试记录每个设备的温度读数，以查看 Actor 是否有响应。

```java
@Test
public void testRegisterDeviceActor() {
  TestKit probe = new TestKit(system);
  ActorRef groupActor = system.actorOf(DeviceGroup.props("group"));

  groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"), probe.getRef());
  probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
  ActorRef deviceActor1 = probe.getLastSender();

  groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device2"), probe.getRef());
  probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
  ActorRef deviceActor2 = probe.getLastSender();
  assertNotEquals(deviceActor1, deviceActor2);

  // Check that the device actors are working
  deviceActor1.tell(new Device.RecordTemperature(0L, 1.0), probe.getRef());
  assertEquals(0L, probe.expectMsgClass(Device.TemperatureRecorded.class).requestId);
  deviceActor2.tell(new Device.RecordTemperature(1L, 2.0), probe.getRef());
  assertEquals(1L, probe.expectMsgClass(Device.TemperatureRecorded.class).requestId);
}

@Test
public void testIgnoreRequestsForWrongGroupId() {
  TestKit probe = new TestKit(system);
  ActorRef groupActor = system.actorOf(DeviceGroup.props("group"));

  groupActor.tell(new DeviceManager.RequestTrackDevice("wrongGroup", "device1"), probe.getRef());
  probe.expectNoMessage();
}
```
如果注册请求已经存在设备 Actor，我们希望使用现有的 Actor 而不是新的 Actor。我们尚未对此进行测试，因此需要修复此问题：

```java
@Test
public void testReturnSameActorForSameDeviceId() {
  TestKit probe = new TestKit(system);
  ActorRef groupActor = system.actorOf(DeviceGroup.props("group"));

  groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"), probe.getRef());
  probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
  ActorRef deviceActor1 = probe.getLastSender();

  groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"), probe.getRef());
  probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
  ActorRef deviceActor2 = probe.getLastSender();
  assertEquals(deviceActor1, deviceActor2);
}
```

### 跟踪组内的设备 Actor
到目前为止，我们已经实现了在组中注册设备 Actor 的逻辑。然而，设备增增减减（`come and go`），所以我们需要一种方法从`Map<String, ActorRef>`中删除设备 Actor。我们假设当一个设备被删除时，它对应的设备 Actor 被停止。正如我们前面讨论的，监督只处理错误场景——而不是优雅的停止。因此，当其中一个设备 Actor 停止时，我们需要通知其父 Actor。

Akka 提供了一个死亡观察功能（`Death Watch feature`），允许一个 Actor 观察另一个 Actor，并在另一个 Actor 被停止时得到通知。与监督者不同的是，观察（`watching`）并不局限于父子关系，任何 Actor 只要知道`ActorRef`就可以观察其他 Actor。在被观察的 Actor 停止后，观察者接收一条`Terminated(actorRef)`消息，该消息还包含对被观察的 Actor 的引用。观察者可以显式处理此消息，也可以失败并出现`DeathPactException`。如果在被观察的 Actor 被停止后，该 Actor 不能再履行自己的职责，则后者很有用。在我们的例子中，组应该在一个设备停止后继续工作，所以我们需要处理`Terminated(actorRef)`消息。

我们的设备组 Actor 需要包括以下功能：

- 当新设备 Actor 被创建时开始观察（`watching`）。
- 当通知指示设备已停止时，从映射`Map<String, ActorRef>`中删除设备 Actor。

不幸的是，`Terminated`的消息只包含子 Actor 的`ActorRef`。我们需要 Actor 的 ID 将其从现有设备到设备的 Actor 映射中删除。为了能够进行删除，我们需要引入另一个占位符`Map<ActorRef, String>`，它允许我们找到与给定`ActorRef`对应的设备 ID。

添加用于标识 Actor 的功能后，代码如下：

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

  private void onTrackDevice(DeviceManager.RequestTrackDevice trackMsg) {
    if (this.groupId.equals(trackMsg.groupId)) {
      ActorRef deviceActor = deviceIdToActor.get(trackMsg.deviceId);
      if (deviceActor != null) {
        deviceActor.forward(trackMsg, getContext());
      } else {
        log.info("Creating device actor for {}", trackMsg.deviceId);
        deviceActor = getContext().actorOf(Device.props(groupId, trackMsg.deviceId), "device-" + trackMsg.deviceId);
        getContext().watch(deviceActor);
        actorToDeviceId.put(deviceActor, trackMsg.deviceId);
        deviceIdToActor.put(trackMsg.deviceId, deviceActor);
        deviceActor.forward(trackMsg, getContext());
      }
    } else {
      log.warning(
              "Ignoring TrackDevice request for {}. This actor is responsible for {}.",
              groupId, this.groupId
      );
    }
  }

  private void onTerminated(Terminated t) {
    ActorRef deviceActor = t.getActor();
    String deviceId = actorToDeviceId.get(deviceActor);
    log.info("Device actor for {} has been terminated", deviceId);
    actorToDeviceId.remove(deviceActor);
    deviceIdToActor.remove(deviceId);
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
            .match(DeviceManager.RequestTrackDevice.class, this::onTrackDevice)
            .match(Terminated.class, this::onTerminated)
            .build();
  }
}
```
到目前为止，我们还没有办法获得组设备 Actor 跟踪的设备，因此，我们还不能测试我们的新功能。为了使其可测试，我们添加了一个新的查询功能（消息`RequestDeviceList`），其中列出了当前活动的设备 ID：

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

  private void onTrackDevice(DeviceManager.RequestTrackDevice trackMsg) {
    if (this.groupId.equals(trackMsg.groupId)) {
      ActorRef deviceActor = deviceIdToActor.get(trackMsg.deviceId);
      if (deviceActor != null) {
        deviceActor.forward(trackMsg, getContext());
      } else {
        log.info("Creating device actor for {}", trackMsg.deviceId);
        deviceActor = getContext().actorOf(Device.props(groupId, trackMsg.deviceId), "device-" + trackMsg.deviceId);
        getContext().watch(deviceActor);
        actorToDeviceId.put(deviceActor, trackMsg.deviceId);
        deviceIdToActor.put(trackMsg.deviceId, deviceActor);
        deviceActor.forward(trackMsg, getContext());
      }
    } else {
      log.warning(
              "Ignoring TrackDevice request for {}. This actor is responsible for {}.",
              groupId, this.groupId
      );
    }
  }

  private void onDeviceList(RequestDeviceList r) {
    getSender().tell(new ReplyDeviceList(r.requestId, deviceIdToActor.keySet()), getSelf());
  }

  private void onTerminated(Terminated t) {
    ActorRef deviceActor = t.getActor();
    String deviceId = actorToDeviceId.get(deviceActor);
    log.info("Device actor for {} has been terminated", deviceId);
    actorToDeviceId.remove(deviceActor);
    deviceIdToActor.remove(deviceId);
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
            .match(DeviceManager.RequestTrackDevice.class, this::onTrackDevice)
            .match(RequestDeviceList.class, this::onDeviceList)
            .match(Terminated.class, this::onTerminated)
            .build();
  }
}
```
我们几乎准备好测试设备的移除功能了。但是，我们仍然需要以下功能：

- 为了通过我们的测试用例停止一个设备 Actor。从外面看，任何 Actor 都可以通过发送一个特殊的内置消息`PoisonPill`来停止，该消息指示 Actor 停止。
- 为了在设备 Actor 停止后得到通知。我们也可以使用`Death Watch`功能观察设备。`TestKit`有两条消息，我们可以很容易地使用`watch()`来观察指定的 Actor，使用`expectTerminated`来断言被观察的 Actor 已被终止。

我们现在再添加两个测试用例。在第一个测试中，我们测试在添加了一些设备之后，是否能返回正确的 ID 列表。第二个测试用例确保在设备 Actor 停止后正确删除设备 ID：

```java
@Test
public void testListActiveDevices() {
  TestKit probe = new TestKit(system);
  ActorRef groupActor = system.actorOf(DeviceGroup.props("group"));

  groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"), probe.getRef());
  probe.expectMsgClass(DeviceManager.DeviceRegistered.class);

  groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device2"), probe.getRef());
  probe.expectMsgClass(DeviceManager.DeviceRegistered.class);

  groupActor.tell(new DeviceGroup.RequestDeviceList(0L), probe.getRef());
  DeviceGroup.ReplyDeviceList reply = probe.expectMsgClass(DeviceGroup.ReplyDeviceList.class);
  assertEquals(0L, reply.requestId);
  assertEquals(Stream.of("device1", "device2").collect(Collectors.toSet()), reply.ids);
}

@Test
public void testListActiveDevicesAfterOneShutsDown() {
  TestKit probe = new TestKit(system);
  ActorRef groupActor = system.actorOf(DeviceGroup.props("group"));

  groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"), probe.getRef());
  probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
  ActorRef toShutDown = probe.getLastSender();

  groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device2"), probe.getRef());
  probe.expectMsgClass(DeviceManager.DeviceRegistered.class);

  groupActor.tell(new DeviceGroup.RequestDeviceList(0L), probe.getRef());
  DeviceGroup.ReplyDeviceList reply = probe.expectMsgClass(DeviceGroup.ReplyDeviceList.class);
  assertEquals(0L, reply.requestId);
  assertEquals(Stream.of("device1", "device2").collect(Collectors.toSet()), reply.ids);

  probe.watch(toShutDown);
  toShutDown.tell(PoisonPill.getInstance(), ActorRef.noSender());
  probe.expectTerminated(toShutDown);

  // using awaitAssert to retry because it might take longer for the groupActor
  // to see the Terminated, that order is undefined
  probe.awaitAssert(() -> {
    groupActor.tell(new DeviceGroup.RequestDeviceList(1L), probe.getRef());
    DeviceGroup.ReplyDeviceList r =
      probe.expectMsgClass(DeviceGroup.ReplyDeviceList.class);
    assertEquals(1L, r.requestId);
    assertEquals(Stream.of("device2").collect(Collectors.toSet()), r.ids);
    return null;
  });
}
```
## 创建设备管理器 Actor

在我们的层次结构中，我们需要在`DeviceManager`源文件中为设备管理器组件创建入口点。此 Actor 与设备组 Actor 非常相似，但创建的是设备组 Actor 而不是设备 Actor：

```java
public class DeviceManager extends AbstractActor {
  private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  public static Props props() {
    return Props.create(DeviceManager.class, DeviceManager::new);
  }

  public static final class RequestTrackDevice {
    public final String groupId;
    public final String deviceId;

    public RequestTrackDevice(String groupId, String deviceId) {
      this.groupId = groupId;
      this.deviceId = deviceId;
    }
  }

  public static final class DeviceRegistered {
  }

  final Map<String, ActorRef> groupIdToActor = new HashMap<>();
  final Map<ActorRef, String> actorToGroupId = new HashMap<>();

  @Override
  public void preStart() {
    log.info("DeviceManager started");
  }

  @Override
  public void postStop() {
    log.info("DeviceManager stopped");
  }

  private void onTrackDevice(RequestTrackDevice trackMsg) {
    String groupId = trackMsg.groupId;
    ActorRef ref = groupIdToActor.get(groupId);
    if (ref != null) {
      ref.forward(trackMsg, getContext());
    } else {
      log.info("Creating device group actor for {}", groupId);
      ActorRef groupActor = getContext().actorOf(DeviceGroup.props(groupId), "group-" + groupId);
      getContext().watch(groupActor);
      groupActor.forward(trackMsg, getContext());
      groupIdToActor.put(groupId, groupActor);
      actorToGroupId.put(groupActor, groupId);
    }
  }

  private void onTerminated(Terminated t) {
    ActorRef groupActor = t.getActor();
    String groupId = actorToGroupId.get(groupActor);
    log.info("Device group actor for {} has been terminated", groupId);
    actorToGroupId.remove(groupActor);
    groupIdToActor.remove(groupId);
  }

  public Receive createReceive() {
    return receiveBuilder()
            .match(RequestTrackDevice.class, this::onTrackDevice)
            .match(Terminated.class, this::onTerminated)
            .build();
  }
}
```
我们将设备管理器的测试留给你作为练习，因为它与我们为设备组 Actor 编写的测试非常相似。

## 下一步是什么？
我们现在有了一个用于注册和跟踪设备以及记录测量值的分层组件。我们已经了解了如何实现不同类型的对话模式，例如：

- 请求响应（`Request-respond`），用于温度记录。
- 代理响应（`Delegate-respond`），用于设备注册。
- 创建监视终止（`Create-watch-terminate`），用于将组和设备 Actor 创建为子级。

在下一章中，我们将介绍组查询功能，这将建立一种新的分散收集（`scatter-gather`）对话模式。特别地，我们将实现允许用户查询属于一个组的所有设备的状态的功能。

----------

**英文原文链接**：[Part 4: Working with Device Groups](https://doc.akka.io/docs/akka/current/guide/tutorial_4.html).

----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————