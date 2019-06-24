# 持久化
## 依赖
为了使用 Akka 持久化（`Persistence`）功能，你必须在项目中添加如下依赖：

```xml
<!-- Maven -->
<dependency>
  <groupId>com.typesafe.akka</groupId>
  <artifactId>akka-persistence_2.12</artifactId>
  <version>2.5.20</version>
</dependency>

<!-- Gradle -->
dependencies {
  compile group: 'com.typesafe.akka', name: 'akka-persistence_2.12', version: '2.5.20'
}

<!-- sbt -->
libraryDependencies += "com.typesafe.akka" %% "akka-persistence" % "2.5.20"
```
Akka 持久性扩展附带了一些内置持久性插件，包括基于内存堆的日志、基于本地文件系统的快照存储和基于 LevelDB 的日志。

基于 LevelDB 的插件需要以下附加依赖：
```xml
<!-- Maven -->
<dependency>
  <groupId>org.fusesource.leveldbjni</groupId>
  <artifactId>leveldbjni-all</artifactId>
  <version>1.8</version>
</dependency>

<!-- Gradle -->
dependencies {
  compile group: 'org.fusesource.leveldbjni', name: 'leveldbjni-all', version: '1.8'
}

<!-- sbt -->
libraryDependencies += "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8"
```
## 示例项目
你可以查看「[持久化示例](https://developer.lightbend.com/start/?group=akka&project=akka-samples-persistence-java)」项目，以了解 Akka 持久化的实际使用情况。

## 简介
Akka 持久性使有状态的 Actor 能够持久化其状态，以便在 Actor 重新启动（例如，在 JVM 崩溃之后）、由监督者或手动停止启动或迁移到集群中时可以恢复状态。Akka 持久性背后的关键概念是，只有 Actor 接收到的事件才被持久化，而不是 Actor 的实际状态（尽管也提供了 Actor 状态快照支持）。事件通过附加到存储（没有任何变化）来持久化，这允许非常高的事务速率和高效的复制。有状态的 Actor 通过将存储的事件重放给 Actor 来恢复，从而允许它重建其状态。这可以是更改的完整历史记录，也可以从快照中的检查点开始，这样可以显著缩短恢复时间。Akka  持久化（`persistence`）还提供具有至少一次消息传递（`at-least-once message delivery `）语义的点对点（`point-to-point`）通信。

- **注释**：《通用数据保护条例》（GDPR）要求必须根据用户的请求删除个人信息。删除或修改携带个人信息的事件是困难的。数据分解可以用来忘记信息，而不是删除或修改信息。这是通过使用给定数据主体 ID（`person`）的密钥加密数据，并在忘记该数据主体时删除密钥来实现的。Lightbend 的「[GDPR for Akka Persistence](https://developer.lightbend.com/docs/akka-commercial-addons/current/gdpr/index.html)」提供了一些工具来帮助构建支持 GDPR 的系统。

Akka 持久化的灵感来自于「[eventsourced](https://github.com/eligosource/eventsourced)」库的正式替换。它遵循与`eventsourced`相同的概念和体系结构，但在 API 和实现级别上存在显著差异。另请参见「[migration-eventsourced-2.3](https://doc.akka.io/docs/akka/current/project/migration-guide-eventsourced-2.3.x.html)」。

## 体系结构
- `AbstractPersistentActor`：是一个持久的、有状态的 Actor。它能够将事件持久化到日志中，并能够以线程安全的方式对它们作出响应。它可以用于实现命令和事件源 Actor。当一个持久性 Actor 启动或重新启动时，日志消息将重播给该 Actor，以便它可以从这些消息中恢复其状态。
- `AbstractPersistentActorAtLeastOnceDelivery`：将具有至少一次传递语义的消息发送到目的地，也可以在发送方和接收方 JVM 崩溃的情况下发送。
- `AsyncWriteJournal`：日志存储发送给持久性 Actor 的消息序列。应用程序可以控制哪些消息是日志记录的，哪些消息是由持久性 Actor 接收的，而不进行日志记录。日志维护每一条消息上增加的`highestSequenceNr`。日志的存储后端是可插入的。持久性扩展附带了一个`leveldb`日志插件，它将写入本地文件系统。
- 快照存储区（`Snapshot store`）：快照存储区保存持久性 Actor 状态的快照。快照用于优化恢复时间。快照存储的存储后端是可插入的。持久性扩展附带了一个“本地”快照存储插件，该插件将写入本地文件系统。
- 事件源（`Event sourcing`）：基于上面描述的构建块，Akka 持久化为事件源应用程序的开发提供了抽象（详见「[事件源](https://doc.akka.io/docs/akka/current/persistence.html#event-sourcing)」部分）。

## 事件源
请参阅「[EventSourcing](https://docs.microsoft.com/en-us/previous-versions/msp-n-p/jj591559(v=pandp.10))」的介绍，下面是 Akka 通过持久性 Actor 实现的。

持久性 Actor 接收（非持久性）命令，如果该命令可以应用于当前状态，则首先对其进行验证。在这里，验证可以意味着任何事情，从简单检查命令消息的字段到与几个外部服务的对话。如果验证成功，则从命令生成事件，表示命令的效果。这些事件随后被持久化，并且在成功持久化之后，用于更改 Actor 的状态。当需要恢复持久性 Actor 时，只重播持久性事件，我们知道这些事件可以成功应用。换句话说，与命令相反，事件在被重播到持久性 Actor 时不会失败。事件源 Actor 还可以处理不更改应用程序状态的命令，例如查询命令。

关于“事件思考”的另一篇优秀文章是 Randy Shoup 的「[Events As First-Class Citizens](https://hackernoon.com/events-as-first-class-citizens-8633e8479493)」。如果你开始开发基于事件的应用程序，这是一个简短的推荐阅读。

Akka 持久化使用`AbstractPersistentActor`抽象类支持事件源。扩展此类的 Actor 使用`persist`方法来持久化和处理事件。`AbstractPersistentActor`的行为是通过实现`createReceiveRecover`和`createReceive`来定义的。这在下面的示例中进行了演示。

```java
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.persistence.AbstractPersistentActor;
import akka.persistence.SnapshotOffer;

import java.io.Serializable;
import java.util.ArrayList;

class Cmd implements Serializable {
  private static final long serialVersionUID = 1L;
  private final String data;

  public Cmd(String data) {
    this.data = data;
  }

  public String getData() {
    return data;
  }
}

class Evt implements Serializable {
  private static final long serialVersionUID = 1L;
  private final String data;

  public Evt(String data) {
    this.data = data;
  }

  public String getData() {
    return data;
  }
}

class ExampleState implements Serializable {
  private static final long serialVersionUID = 1L;
  private final ArrayList<String> events;

  public ExampleState() {
    this(new ArrayList<>());
  }

  public ExampleState(ArrayList<String> events) {
    this.events = events;
  }

  public ExampleState copy() {
    return new ExampleState(new ArrayList<>(events));
  }

  public void update(Evt evt) {
    events.add(evt.getData());
  }

  public int size() {
    return events.size();
  }

  @Override
  public String toString() {
    return events.toString();
  }
}

class ExamplePersistentActor extends AbstractPersistentActor {

  private ExampleState state = new ExampleState();
  private int snapShotInterval = 1000;

  public int getNumEvents() {
    return state.size();
  }

  @Override
  public String persistenceId() {
    return "sample-id-1";
  }

  @Override
  public Receive createReceiveRecover() {
    return receiveBuilder()
        .match(Evt.class, state::update)
        .match(SnapshotOffer.class, ss -> state = (ExampleState) ss.snapshot())
        .build();
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            Cmd.class,
            c -> {
              final String data = c.getData();
              final Evt evt = new Evt(data + "-" + getNumEvents());
              persist(
                  evt,
                  (Evt e) -> {
                    state.update(e);
                    getContext().getSystem().getEventStream().publish(e);
                    if (lastSequenceNr() % snapShotInterval == 0 && lastSequenceNr() != 0)
                      // IMPORTANT: create a copy of snapshot because ExampleState is mutable
                      saveSnapshot(state.copy());
                  });
            })
        .matchEquals("print", s -> System.out.println(state))
        .build();
  }
}
```
该示例定义了两种数据类型，即`Cmd`和`Evt`，分别表示命令和事件。`ExamplePersistentActor`的状态是包含在`ExampleState`中的持久化事件数据的列表。

持久化 Actor 的`createReceiveRecover`方法通过处理`Evt`和`SnapshotOffer`消息来定义在恢复过程中如何更新状态。持久化 Actor 的`createReceive`方法是命令处理程序。在本例中，通过生成一个事件来处理命令，该事件随后被持久化和处理。通过使用事件（或事件序列）作为第一个参数和事件处理程序作为第二个参数调用`persist`来持久化事件。

`persist`方法异步地持久化事件，并为成功持久化的事件执行事件处理程序。成功的持久化事件在内部作为触发事件处理程序执行的单个消息发送回持久化 Actor。事件处理程序可能会关闭持久性 Actor 状态并对其进行改变。持久化事件的发送者是相应命令的发送者。这允许事件处理程序回复命令的发送者（未显示）。

事件处理程序的主要职责是使用事件数据更改持久性 Actor 状态，并通过发布事件通知其他人成功的状态更改。

当使用`persist`持久化事件时，可以确保持久化 Actor 不会在`persist`调用和关联事件处理程序的执行之间接收进一步的命令。这也适用于单个命令上下文中的多个`persist`调用。传入的消息将被存储，直到持久化完成。

如果事件的持久性失败，将调用`onPersistFailure`（默认情况下记录错误），并且 Actor 将无条件停止。如果在存储事件之前拒绝了该事件的持久性，例如，由于序列化错误，将调用`onPersistRejected`（默认情况下记录警告），并且 Actor 将继续执行下一条消息。

运行这个例子最简单的方法是自己下载准备好的「[ Akka 持久性示例](https://example.lightbend.com/v1/download/akka-samples-persistence-java)」和教程。它包含有关如何运行`PersistentActorExample`的说明。此示例的源代码也可以在「[Akka 示例仓库](https://developer.lightbend.com/start/?group=akka&project=akka-samples-persistence-java)」中找到。

- **注释**：在使用`getContext().become()`和`getContext().unbecome()`进行正常处理和恢复期间，还可以在不同的命令处理程序之间切换。要使 Actor 在恢复后进入相同的状态，你需要特别注意在`createReceiveRecover`方法中使用`become`和`unbecome`执行相同的状态转换，就像在命令处理程序中那样。请注意，当使用来自`createReceiveRecover`的`become`时，在重播事件时，它仍然只使用`createReceiveRecover`行为。重播完成后，将使用新行为。

### 标识符
持久性 Actor 必须有一个标识符（`identifier`），该标识符在不同的 Actor 化身之间不会发生变化。必须使用`persistenceId`方法定义标识符。

```java
@Override
public String persistenceId() {
  return "my-stable-persistence-id";
}
```
- **注释**：`persistenceId`对于日志中的给定实体（数据库表/键空间）必须是唯一的。当重播持久化到日志的消息时，你将查询具有`persistenceId`的消息。因此，如果两个不同的实体共享相同的`persistenceId`，则消息重播行为已损坏。

### 恢复
默认情况下，通过重放日志消息，在启动和重新启动时自动恢复持久性 Actor。在恢复期间发送给持久性 Actor 的新消息不会干扰重播的消息。在恢复阶段完成后，它们被一个持久性 Actor 存放和接收。

可以同时进行的并发恢复的数量限制为不使系统和后端数据存储过载。当超过限制时，Actor 将等待其他恢复完成。配置方式为：

```
akka.persistence.max-concurrent-recoveries = 50
```
- **注释**：假设原始发件人已经很长时间不在，那么使用`getSender()`访问已重播消息的发件人将始终导致`deadLetters`引用。如果在将来的恢复过程中确实需要通知某个 Actor，请将其`ActorPath`显式存储在持久化事件中。

### 恢复自定义
应用程序还可以通过在`AbstractPersistentActor`的`recovery`方法中返回自定义的`Recovery`对象来定制恢复的执行方式，要跳过加载快照和重播所有事件，可以使用`SnapshotSelectionCriteria.none()`。如果快照序列化格式以不兼容的方式更改，则此选项非常有用。它通常不应该在事件被删除时使用。

```java
@Override
public Recovery recovery() {
  return Recovery.create(SnapshotSelectionCriteria.none());
}
```
另一种可能的恢复自定义（对调试有用）是在重播上设置上限，使 Actor 仅在“过去”的某个点上重播（而不是重播到其最新状态）。请注意，在这之后，保留新事件是一个坏主意，因为以后的恢复可能会被以前跳过的事件后面的新事件混淆。

```java
@Override
public Recovery recovery() {
  return Recovery.create(457L);
}
```
通过在`PersistentActor`的`recovery`方法中返回`Recovery.none()`可以关闭恢复：

```java
@Override
public Recovery recovery() {
  return Recovery.none();
}
```
### 恢复状态
通过以下方法，持久性 Actor 可以查询其自己的恢复状态：

```java
public boolean recoveryRunning();

public boolean recoveryFinished();
```
有时候，在处理发送给持久性 Actor 的任何其他消息之前，当恢复完成时，需要执行额外的初始化。持久性 Actor 将在恢复之后和任何其他收到的消息之前收到一条特殊的`RecoveryCompleted`消息。

```java
class MyPersistentActor5 extends AbstractPersistentActor {

  @Override
  public String persistenceId() {
    return "my-stable-persistence-id";
  }

  @Override
  public Receive createReceiveRecover() {
    return receiveBuilder()
        .match(
            RecoveryCompleted.class,
            r -> {
              // perform init after recovery, before any other messages
              // ...
            })
        .match(String.class, this::handleEvent)
        .build();
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(String.class, s -> s.equals("cmd"), s -> persist("evt", this::handleEvent))
        .build();
  }

  private void handleEvent(String event) {
    // update state
    // ...
  }
}
```
即使日志中没有事件且快照存储为空，或者是具有以前未使用的`persistenceId`的新持久性 Actor，Actor 也将始终收到`RecoveryCompleted`消息。

如果从日志中恢复 Actor 的状态时出现问题，则调用`onRecoveryFailure`（默认情况下记录错误），Actor 将停止。

### 内部存储
持久性 Actor 有一个私有存储区，用于在恢复期间对传入消息进行内部缓存，或者通过`persist\persistAll`方法持久化事件。你仍然可以从`Stash`接口`use/inherit`。内部存储（`internal stash`）与正常存储进行合作，通过`unstashAll`方法并确保消息正确地`unstashed`到内部存储以维持顺序保证。

你应该小心，不要向持久性 Actor 发送超过它所能跟上的消息，否则隐藏的消息的数量将无限增长。通过在邮箱配置中定义最大存储容量来防止`OutOfMemoryError`是明智的：

```
akka.actor.default-mailbox.stash-capacity=10000
```
注意，其是每个 Actor 的藏匿存储容量（`stash capacity`）。如果你有许多持久性 Actor，例如在使用集群分片（`cluster sharding`）时，你可能需要定义一个小的存储容量，以确保系统中存储的消息总数不会消耗太多的内存。此外，持久性 Actor 定义了三种策略来处理超过内部存储容量时的故障。默认的溢出策略是`ThrowOverflowExceptionStrategy`，它丢弃当前接收到的消息并抛出`StashOverflowException`，如果使用默认的监视策略，则会导致 Actor 重新启动。你可以重写`internalStashOverflowStrategy`方法，为任何“单个（`individual`）”持久性 Actor 返回`DiscardToDeadLetterStrategy`或`ReplyToStrategy`，或者通过提供 FQCN 为所有持久性 Actor 定义“默认值”，FQCN 必须是持久配置中`StashOverflowStrategyConfigurator`的子类：

```
akka.persistence.internal-stash-overflow-strategy=
  "akka.persistence.ThrowExceptionConfigurator"
```
`DiscardToDeadLetterStrategy`策略还具有预打包（`pre-packaged`）的伴生配置程序`akka.persistence.DiscardConfigurator`。

你还可以通过 Akka 的持久性扩展查询默认策略：

```java
Persistence.get(getContext().getSystem()).defaultInternalStashOverflowStrategy();
```

- **注释**：在持久性 Actor 中，应避免使用有界的邮箱（`bounded mailbox`），否则来自存储后端的消息可能会被丢弃。你可以用有界的存储（`bounded stash`）来代替它。

### Relaxed 本地一致性需求和高吞吐量用例
如果面对`relaxed`本地一致性和高吞吐量要求，有时`PersistentActor`及其`persist`在高速使用传入命令方面可能不够，因为它必须等到与给定命令相关的所有事件都被处理后才能开始处理下一个命令。虽然这种抽象在大多数情况下都非常有用，但有时你可能会面临关于一致性的`relaxed`要求——例如，你可能希望尽可能快地处理命令，假设事件最终将在后台被持久化并正确处理，如果需要，可以对持久性失败进行逆向反应（`retroactively reacting`）。

`persistAsync`方法提供了一个工具来实现高吞吐量的持久性 Actor。当日志仍在处理持久化`and/or`用户代码正在执行事件回调时，它不会存储传入的命令。

在下面的示例中，即使在处理下一个命令之后，事件回调也可以“任何时候”调用。事件之间的顺序仍然是有保证的（`evt-b-1`将在`evt-a-2`之后发送，也将在`evt-a-1`之后发送）。

```java
class MyPersistentActor extends AbstractPersistentActor {

  @Override
  public String persistenceId() {
    return "my-stable-persistence-id";
  }

  private void handleCommand(String c) {
    getSender().tell(c, getSelf());

    persistAsync(
        String.format("evt-%s-1", c),
        e -> {
          getSender().tell(e, getSelf());
        });
    persistAsync(
        String.format("evt-%s-2", c),
        e -> {
          getSender().tell(e, getSelf());
        });
  }

  @Override
  public Receive createReceiveRecover() {
    return receiveBuilder().match(String.class, this::handleCommand).build();
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder().match(String.class, this::handleCommand).build();
  }
}
```
- **注释**：为了实现名为“命令源（`command sourcing`）”的模式，请立即对所有传入消息调用`persistAsync`，并在回调中处理它们。
- **警告**：如果在对`persistAsync`的调用和日志确认写入之间重新启动或停止 Actor，则不会调用回调。

### 推迟操作，直到执行了前面的持久处理程序
有时候，在处理`persistAsync`或`persist`时，你可能会发现，最好定义一些“在调用以前的`persistAsync/persist`处理程序之后发生”的操作。`PersistentActor`提供了名为`defer`和`deferAsync`的实用方法，它们分别与`persist`和`persistAsync`工作类似，但不会持久化传入事件。建议将它们用于读取操作，在域模型中没有相应事件的操作。

使用这些方法与持久化方法非常相似，但它们不会持久化传入事件。它将保存在内存中，并在调用处理程序时使用。

```java
class MyPersistentActor extends AbstractPersistentActor {

  @Override
  public String persistenceId() {
    return "my-stable-persistence-id";
  }

  private void handleCommand(String c) {
    persistAsync(
        String.format("evt-%s-1", c),
        e -> {
          getSender().tell(e, getSelf());
        });
    persistAsync(
        String.format("evt-%s-2", c),
        e -> {
          getSender().tell(e, getSelf());
        });

    deferAsync(
        String.format("evt-%s-3", c),
        e -> {
          getSender().tell(e, getSelf());
        });
  }

  @Override
  public Receive createReceiveRecover() {
    return receiveBuilder().match(String.class, this::handleCommand).build();
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder().match(String.class, this::handleCommand).build();
  }
}
```
请注意，`sender()`在处理程序回调中是安全的，它将指向调用此`defer`或`deferAsync`处理程序的命令的原始发送者。

调用方将按此（保证）顺序得到响应：

```java
final ActorRef persistentActor = system.actorOf(Props.create(MyPersistentActor.class));
persistentActor.tell("a", sender);
persistentActor.tell("b", sender);

// order of received messages:
// a
// b
// evt-a-1
// evt-a-2
// evt-a-3
// evt-b-1
// evt-b-2
// evt-b-3
```
你也可以在调用`persist`时，调用`defer`或者`deferAsync`：
```java
class MyPersistentActor extends AbstractPersistentActor {

  @Override
  public String persistenceId() {
    return "my-stable-persistence-id";
  }

  private void handleCommand(String c) {
    persist(
        String.format("evt-%s-1", c),
        e -> {
          sender().tell(e, self());
        });
    persist(
        String.format("evt-%s-2", c),
        e -> {
          sender().tell(e, self());
        });

    defer(
        String.format("evt-%s-3", c),
        e -> {
          sender().tell(e, self());
        });
  }

  @Override
  public Receive createReceiveRecover() {
    return receiveBuilder().match(String.class, this::handleCommand).build();
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder().match(String.class, this::handleCommand).build();
  }
}
```

- **警告**：如果在对`defer`或`deferAsync`的调用之间重新启动或停止 Actor，并且日志已经处理并确认了前面的所有写入操作，则不会调用回调。

### 嵌套的持久调用
可以在各自的回调块中调用`persist`和`persistAsync`，它们将正确地保留线程安全性（包括`getSender()`的正确值）和存储保证。

一般来说，鼓励创建不需要使用嵌套事件持久化的命令处理程序，但是在某些情况下，它可能会有用。了解这些情况下回调执行的顺序以及它们对隐藏行为（`persist()`强制执行）的影响是很重要的。在下面的示例中，发出了两个持久调用，每个持久调用在其回调中发出另一个持久调用：

```java
@Override
public Receive createReceiveRecover() {
  final Procedure<String> replyToSender = event -> getSender().tell(event, getSelf());

  return receiveBuilder()
      .match(
          String.class,
          msg -> {
            persist(
                String.format("%s-outer-1", msg),
                event -> {
                  getSender().tell(event, getSelf());
                  persist(String.format("%s-inner-1", event), replyToSender);
                });

            persist(
                String.format("%s-outer-2", msg),
                event -> {
                  getSender().tell(event, getSelf());
                  persist(String.format("%s-inner-2", event), replyToSender);
                });
          })
      .build();
}
```
向此`PersistentActor`发送两个命令时，将按以下顺序执行持久化处理程序：

```java
persistentActor.tell("a", ActorRef.noSender());
persistentActor.tell("b", ActorRef.noSender());

// order of received messages:
// a
// a-outer-1
// a-outer-2
// a-inner-1
// a-inner-2
// and only then process "b"
// b
// b-outer-1
// b-outer-2
// b-inner-1
// b-inner-2
```
首先，发出持久调用的“外层”，并应用它们的回调。成功完成这些操作后，将调用内部回调（一旦日志确认了它们所持续的事件是持久的）。只有在成功地调用了所有这些处理程序之后，才能将下一个命令传递给持久性 Actor。换句话说，通过最初在外层上调用`persist()`来保证输入命令的存储被扩展，直到所有嵌套的`persist`回调都被处理完毕。

也可以使用相同的模式嵌套`persistAsync`调用：

```java
@Override
public Receive createReceive() {
  final Procedure<String> replyToSender = event -> getSender().tell(event, getSelf());

  return receiveBuilder()
      .match(
          String.class,
          msg -> {
            persistAsync(
                String.format("%s-outer-1", msg),
                event -> {
                  getSender().tell(event, getSelf());
                  persistAsync(String.format("%s-inner-1", event), replyToSender);
                });

            persistAsync(
                String.format("%s-outer-2", msg),
                event -> {
                  getSender().tell(event, getSelf());
                  persistAsync(String.format("%s-inner-1", event), replyToSender);
                });
          })
      .build();
}
```
在这种情况下，不会发生存储，但事件仍将持续，并且按预期顺序执行回调：

```java
persistentActor.tell("a", getSelf());
persistentActor.tell("b", getSelf());

// order of received messages:
// a
// b
// a-outer-1
// a-outer-2
// b-outer-1
// b-outer-2
// a-inner-1
// a-inner-2
// b-inner-1
// b-inner-2

// which can be seen as the following causal relationship:
// a -> a-outer-1 -> a-outer-2 -> a-inner-1 -> a-inner-2
// b -> b-outer-1 -> b-outer-2 -> b-inner-1 -> b-inner-2
```
尽管可以通过保持各自的语义来嵌套混合`persist`和`persistAsync`，但这不是推荐的做法，因为这可能会导致嵌套过于复杂。

- **警告**：虽然可以在彼此内部嵌套`persist`调用，但从 Actor 消息处理线程以外的任何其他线程调用`persist`都是非法的。例如，通过`Futures`调用`persist`就是非法的！这样做将打破`persist`方法旨在提供的保证，应该始终从 Actor 的接收块（`receive block`）中调用`persist`和`persistAsync`。

### 失败
如果事件的持久性失败，将调用`onPersistFailure`（默认情况下记录错误），并且 Actor 将无条件停止。

当`persist`失败时，它无法恢复的原因是不知道事件是否实际持续，因此处于不一致状态。由于日志可能不可用，在持续失败时重新启动很可能会失败。最好是停止 Actor，然后在退后超时后重新启动。提供`akka.pattern.BackoffSupervisor` Actor 以支持此类重新启动。

```java
@Override
public void preStart() throws Exception {
  final Props childProps = Props.create(MyPersistentActor1.class);
  final Props props =
      BackoffSupervisor.props(
          childProps, "myActor", Duration.ofSeconds(3), Duration.ofSeconds(30), 0.2);
  getContext().actorOf(props, "mySupervisor");
  super.preStart();
}
```
如果在存储事件之前拒绝了该事件的持久性，例如，由于序列化错误，将调用`onPersistRejected`（默认情况下记录警告），并且 Actor 将继续执行下一条消息。

如果在启动 Actor 时无法从日志中恢复 Actor 的状态，将调用`onRecoveryFailure`（默认情况下记录错误），并且 Actor 将被停止。请注意，加载快照失败也会像这样处理，但如果你知道序列化格式已以不兼容的方式更改，则可以禁用快照加载，请参阅「[恢复自定义](#https://doc.akka.io/docs/akka/current/persistence.html#recovery-custom)」。

### 原子写入
每个事件都是原子存储的（`stored atomically`），但也可以使用`persistAll`或`persistAllAsync`方法原子存储多个事件。这意味着传递给该方法的所有事件都将被存储，或者在出现错误时不存储任何事件。

因此，持久性 Actor 的恢复永远不会只在`persistAll`持久化事件的一个子集的情况下部分完成。

有些日志可能不支持几个事件的原子写入（`atomic writes`），它们将拒绝`persistAll`命令，例如调用`OnPersistRejected`时出现异常（通常是`UnsupportedOperationException`）。

### 批量写入
为了在使用`persistAsync`时优化吞吐量，持久性 Actor 在将事件写入日志（作为单个批处理）之前在内部批处理要在高负载下存储的事件。批（`batch`）的大小由日志往返期间发出的事件数动态确定：向日志发送批之后，在收到上一批已写入的确认信息之前，不能再发送其他批。批写入从不基于计时器，它将延迟保持在最小值。

### 消息删除
可以在指定的序列号之前删除所有消息（由单个持久性 Actor 记录）；持久性 Actor 可以为此端调用`deleteMessages`方法。

在基于事件源的应用程序中删除消息通常要么根本不使用，要么与快照一起使用，即在成功存储快照之后，可以发出一条`deleteMessages(toSequenceNr)`消息。

- **警告**：如果你使用「[持久性查询](https://doc.akka.io/docs/akka/current/persistence-query.html)」，查询结果可能会丢失日志中已删除的消息，这取决于日志插件中如何实现删除。除非你使用的插件在持久性查询结果中仍然显示已删除的消息，否则你必须设计应用程序，使其不受丢失消息的影响。

在持久性 Actor 发出`deleteMessages`消息之后，如果删除成功，则向持久性 Actor 发送`DeleteMessagesSuccess`消息，如果删除失败，则向持久性 Actor 发送`DeleteMessagesFailure`消息。

消息删除不会影响日志的最高序列号，即使在调用`deleteMessages`之后从日志中删除了所有消息。

### 持久化状态处理
持久化、删除和重放消息可以成功，也可以失败。

| Method | Success      |
|:--------| :-------------|
| `persist / persistAsync` |调用持久化处理器 |
| `onPersistRejected` |无自动行为|
| `recovery` |`RecoveryCompleted` |
| `deleteMessages` |`DeleteMessagesSuccess` |

最重要的操作（`persist`和`recovery`）将故障处理程序建模为显式回调，用户可以在`PersistentActor`中重写该回调。这些处理程序的默认实现会发出一条日志消息（`persist`或`recovery`失败的`error`），记录失败原因和有关导致失败的消息的信息。

对于严重的故障（如恢复或持久化事件失败），在调用故障处理程序后将停止持久性 Actor。这是因为，如果底层日志实现发出持久性失败的信号，那么它很可能要么完全失败，要么过载并立即重新启动，然后再次尝试持久性事件，这很可能不会帮助日志恢复，因为它可能会导致一个「[Thundering herd](https://en.wikipedia.org/wiki/Thundering_herd_problem)」问题，因为许多持久性 Actor 会重新启动并尝试继续他们的活动。相反，使用`BackoffSupervisor`，它实现了一个指数级的退避（`exponential-backoff`）策略，允许持久性 Actor 在重新启动之间有更多的喘息空间。

- **注释**：日志实现可以选择实现重试机制，例如，只有在写入失败`N`次之后，才会向用户发出持久化失败的信号。换句话说，一旦一个日志返回一个失败，它就被 Akka 持久化认为是致命的，导致失败的持久行 Actor 将被停止。检查你正在使用的日志实现文档，了解它是否或如何使用此技术。

### 安全地关闭持久性 Actor
当从外部关闭持久性 Actor 时，应该特别小心。对于正常的 Actor，通常可以接受使用特殊的`PoisonPill`消息来向 Actor 发出信号，一旦收到此信息，它就应该停止自己。事实上，此消息是由 Akka 自动处理的。

当与`PersistentActor`一起使用时，这可能很危险。由于传入的命令将从 Actor 的邮箱中排出，并在等待确认时放入其内部存储（在调用持久处理程序之前），因此 Actor 可以在处理已放入其存储的其他消息之前接收和（自动）处理`PoisonPill`，从而导致 Actor 的提前（`pre-mature`）停止。

- **警告**：当与持久性 Actor 一起工作时，考虑使用明确的关闭消息而不是使用`PoisonPill`。

下面的示例强调了消息如何到达 Actor 的邮箱，以及在使用`persist()`时它们如何与其内部存储机制交互。注意，使用`PoisonPill`时可能发生的早期停止行为：

```java
final class Shutdown {}

class MyPersistentActor extends AbstractPersistentActor {
  @Override
  public String persistenceId() {
    return "some-persistence-id";
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            Shutdown.class,
            shutdown -> {
              getContext().stop(getSelf());
            })
        .match(
            String.class,
            msg -> {
              System.out.println(msg);
              persist("handle-" + msg, e -> System.out.println(e));
            })
        .build();
  }

  @Override
  public Receive createReceiveRecover() {
    return receiveBuilder().matchAny(any -> {}).build();
  }
}
```

```java
// UN-SAFE, due to PersistentActor's command stashing:
persistentActor.tell("a", ActorRef.noSender());
persistentActor.tell("b", ActorRef.noSender());
persistentActor.tell(PoisonPill.getInstance(), ActorRef.noSender());
// order of received messages:
// a
//   # b arrives at mailbox, stashing;        internal-stash = [b]
//   # PoisonPill arrives at mailbox, stashing; internal-stash = [b, Shutdown]
// PoisonPill is an AutoReceivedMessage, is handled automatically
// !! stop !!
// Actor is stopped without handling `b` nor the `a` handler!
```

```java
// SAFE:
persistentActor.tell("a", ActorRef.noSender());
persistentActor.tell("b", ActorRef.noSender());
persistentActor.tell(new Shutdown(), ActorRef.noSender());
// order of received messages:
// a
//   # b arrives at mailbox, stashing;        internal-stash = [b]
//   # Shutdown arrives at mailbox, stashing; internal-stash = [b, Shutdown]
// handle-a
//   # unstashing;                            internal-stash = [Shutdown]
// b
// handle-b
//   # unstashing;                            internal-stash = []
// Shutdown
// -- stop --
```
### 重播滤波器
在某些情况下，事件流可能已损坏，并且多个写入程序（即多个持久性 Actor 实例）使用相同的序列号记录不同的消息。在这种情况下，你可以配置如何在恢复时过滤来自多个编写器（`writers`）的重播（`replayed`）消息。

在你的配置中，在`akka.persistence.journal.xxx.replay-filter`部分（其中`xxx`是日志插件`id`）下，你可以从以下值中选择重播过滤器（`replay filter`）的模式：

- `repair-by-discard-old`
- `fail`
- `warn`
- `off`

例如，如果为 LevelDB 插件配置重播过滤器，则如下所示：

```
# The replay filter can detect a corrupt event stream by inspecting
# sequence numbers and writerUuid when replaying events.
akka.persistence.journal.leveldb.replay-filter {
  # What the filter should do when detecting invalid events.
  # Supported values:
  # `repair-by-discard-old` : discard events from old writers,
  #                           warning is logged
  # `fail` : fail the replay, error is logged
  # `warn` : log warning but emit events untouched
  # `off` : disable this feature completely
  mode = repair-by-discard-old
}
```
## 快照
当你使用 Actor 建模你的域时，你可能会注意到一些 Actor 可能会积累非常长的事件日志并经历很长的恢复时间。有时，正确的方法可能是分成一组生命周期较短的 Actor。但是，如果这不是一个选项，你可以使用快照（`snapshots`）来大幅缩短恢复时间。

持久性 Actor 可以通过调用`saveSnapshot`方法来保存内部状态的快照。如果快照保存成功，持久性 Actor 将收到`SaveSnapshotSuccess`消息，否则将收到`SaveSnapshotFailure`消息。

```java
private Object state;
private int snapShotInterval = 1000;

@Override
public Receive createReceive() {
  return receiveBuilder()
      .match(
          SaveSnapshotSuccess.class,
          ss -> {
            SnapshotMetadata metadata = ss.metadata();
            // ...
          })
      .match(
          SaveSnapshotFailure.class,
          sf -> {
            SnapshotMetadata metadata = sf.metadata();
            // ...
          })
      .match(
          String.class,
          cmd -> {
            persist(
                "evt-" + cmd,
                e -> {
                  updateState(e);
                  if (lastSequenceNr() % snapShotInterval == 0 && lastSequenceNr() != 0)
                    saveSnapshot(state);
                });
          })
      .build();
}
```
其中，`metadata`的类型为`SnapshotMetadata`：

```java
final case class SnapshotMetadata(persistenceId: String, sequenceNr: Long, timestamp: Long = 0L)
```
在恢复过程中，通过`SnapshotOffer`消息向持久性 Actor 提供以前保存的快照，从中可以初始化内部状态。

```java
private Object state;

@Override
public Receive createReceiveRecover() {
  return receiveBuilder()
      .match(
          SnapshotOffer.class,
          s -> {
            state = s.snapshot();
            // ...
          })
      .match(
          String.class,
          s -> {
            /* ...*/
          })
      .build();
}
```
在`SnapshotOffer`消息之后重播的消息（如果有）比提供的快照状态年轻（`younger`）。他们最终将持久性 Actor 恢复到当前（即最新）状态。

通常，只有在持久性 Actor 以前保存过一个或多个快照，并且其中至少一个快照与可以指定用于恢复的`SnapshotSelectionCriteria`匹配时，才会提供持久性 Actor 快照。

```java
@Override
public Recovery recovery() {
  return Recovery.create(
      SnapshotSelectionCriteria.create(457L, System.currentTimeMillis()));
}
```
如果未指定，则默认为`SnapshotSelectionCriteria.latest()`，后者选择最新的（最年轻的）快照。要禁用基于快照的恢复，应用程序应使用`SnapshotSelectionCriteria.none()`。如果没有保存的快照与指定的`SnapshotSelectionCriteria`匹配，则恢复将重播所有日志消息。

- **注释**：为了使用快照，必须配置默认的快照存储（`akka.persistence.snapshot-store.plugin`），或者持久性 Actor 可以通过重写`String snapshotPluginId()`显式地选择快照存储。由于某些应用程序可以不使用任何快照，因此不配置快照存储是合法的。但是，当检测到这种情况时，Akka 会记录一条警告消息，然后继续操作，直到 Actor 尝试存储快照，此时操作将失败（例如，通过使用`SaveSnapshotFailure`进行响应）。注意集群分片（`Cluster Sharding`）的“持久性模式”使用快照。如果使用该模式，则需要定义快照存储插件。

### 快照删除
持久性 Actor 可以通过使用快照拍摄时间的序列号调用`deleteSnapshot`方法来删除单个快照。

要批量删除与`SnapshotSelectionCriteria`匹配的一系列快照，持久性 Actor 应使用`deleteSnapshots`方法。根据所用的日志，这可能是低效的。最佳做法是使用`deleteSnapshot`执行特定的删除，或者为`SnapshotSelectionCriteria`包含`minSequenceNr`和`maxSequenceNr`。

### 快照状态处理
保存或删除快照既可以成功，也可以失败，此信息通过状态消息报告给持久性 Actor，如下表所示：

| Method | Success      | Failure message |
|:--------| :-------------| :-------------|
| `saveSnapshot(Any)` |`SaveSnapshotSuccess` |`SaveSnapshotFailure` |
| `deleteSnapshot(Long)` |`DeleteSnapshotSuccess`|`DeleteSnapshotFailure` |
| `deleteSnapshots(SnapshotSelectionCriteria)` |`DeleteSnapshotsSuccess` |`DeleteSnapshotsFailure` |

如果 Actor 未处理故障消息，则将为每个传入的故障消息记录默认的警告日志消息。不会对成功消息执行默认操作，但是你可以自由地处理它们，例如，为了删除快照的内存中表示形式，或者在尝试再次保存快照失败的情况下。

## 扩容
在一个用例中，如果需要的持久性 Actor 的数量高于一个节点的内存中所能容纳的数量，或者弹性很重要，因此如果一个节点崩溃，那么持久性 Actor 很快就会在一个新节点上启动，并且可以恢复操作，那么「[集群分片](https://doc.akka.io/docs/akka/current/cluster-sharding.html)」非常适合将持久性 Actor 通过他们的`id`分散到集群和地址上。

Akka 持久化（`persistence`）是基于单写入（`single-writer`）原则的。对于特定的`persistenceId`，一次只能激活一个`PersistentActor`实例。如果多个实例同时持久化事件，那么这些事件将被交错，并且在重播时可能无法正确解释。集群分片确保数据中心内每个`id`只有一个活动实体（`PersistentActor`）。LightBend 的「[Multi-DC Persistence](https://developer.lightbend.com/docs/akka-commercial-addons/current/persistence-dc/index.html)」支持跨数据中心的双活（`active-active`）持久性实体。

在 Akka 之上构建的「[Lagom](https://www.lagomframework.com/)」框架编码了许多与此相关的最佳实践。有关更多详细信息，请参阅 Lagom 文档中的「[Managing Data Persistence](https://www.lagomframework.com/documentation/current/java/ES_CQRS.html)」和「[Persistent Entity](https://www.lagomframework.com/documentation/current/java/PersistentEntity.html)」。

### 至少一次传递
要将具有至少一次传递（`at-least-once delivery`）语义的消息发送到目标，可以使用`AbstractPersistentActorWithAtLeastOnceDelivery`，而不是在发送端扩展`AbstractPersistentActor`。当消息在可配置的超时时间内未被确认时，它负责重新发送消息。

发送 Actor 的状态，包括那些已发送但未被接收者确认的消息，必须是持久的，这样它才能在发送 Actor 或 JVM 崩溃后存活下来。`AbstractPersistentActorWithAtLeastOnceDelivery`类本身不持久任何内容。

- **注释**：至少有一次传递意味着原始消息发送顺序并不总是保持不变，并且目标可能接收到重复的消息。该语义与普通`ActorRef`发送操作的语义不匹配：
  - 不是至多一次传递
  - 同一“发送方和接收者”对的消息顺序由于可能的重发而不被保留
  - 在崩溃和目标 Actor 的重新启动之后，消息仍然被传递给新的 Actor 化身。

这些语义类似于`ActorPath`所表示的含义，因此在传递消息时需要提供路径而不是引用。消息将与 Actor 选择（`selection`）一起发送到路径。

使用`deliver`方法将消息发送到目标。当目标已用确认消息答复时，调用`confirmDelivery`方法。

###  deliver 与 confirmDelivery 的关系
若要将消息发送到目标路径，请在持久化发送消息的意图之后使用`deliver`方法。

目标 Actor 必须返回确认消息。当发送 Actor 收到此确认消息时，你应该持久化消息已成功传递的事实，然后调用`confirmDelivery`方法。

如果持久性 Actor 当前未恢复，则`deliver`方法将消息发送到目标 Actor。恢复时，将缓冲消息，直到使用`confirmDelivery`确认消息。一旦恢复完成，如果有未确认的未完成消息（在消息重播期间），持久性 Actor 将在发送任何其他消息之前重新发送这些消息。

传递需要`deliveryIdToMessage`函数将提供的`deliveryId`传递到消息中，以便`deliver`和`confirmDelivery`之间的关联成为可能。`deliveryId`必须在传递之间往返。在收到消息后，目标 Actor 会将包装在确认消息中的相同`deliveryId`发送回发送者。然后，发送方将使用它调用`confirmDelivery`方法来完成传递过程。

```java
class Msg implements Serializable {
  private static final long serialVersionUID = 1L;
  public final long deliveryId;
  public final String s;

  public Msg(long deliveryId, String s) {
    this.deliveryId = deliveryId;
    this.s = s;
  }
}

class Confirm implements Serializable {
  private static final long serialVersionUID = 1L;
  public final long deliveryId;

  public Confirm(long deliveryId) {
    this.deliveryId = deliveryId;
  }
}

class MsgSent implements Serializable {
  private static final long serialVersionUID = 1L;
  public final String s;

  public MsgSent(String s) {
    this.s = s;
  }
}

class MsgConfirmed implements Serializable {
  private static final long serialVersionUID = 1L;
  public final long deliveryId;

  public MsgConfirmed(long deliveryId) {
    this.deliveryId = deliveryId;
  }
}

class MyPersistentActor extends AbstractPersistentActorWithAtLeastOnceDelivery {
  private final ActorSelection destination;

  public MyPersistentActor(ActorSelection destination) {
    this.destination = destination;
  }

  @Override
  public String persistenceId() {
    return "persistence-id";
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            String.class,
            s -> {
              persist(new MsgSent(s), evt -> updateState(evt));
            })
        .match(
            Confirm.class,
            confirm -> {
              persist(new MsgConfirmed(confirm.deliveryId), evt -> updateState(evt));
            })
        .build();
  }

  @Override
  public Receive createReceiveRecover() {
    return receiveBuilder().match(Object.class, evt -> updateState(evt)).build();
  }

  void updateState(Object event) {
    if (event instanceof MsgSent) {
      final MsgSent evt = (MsgSent) event;
      deliver(destination, deliveryId -> new Msg(deliveryId, evt.s));
    } else if (event instanceof MsgConfirmed) {
      final MsgConfirmed evt = (MsgConfirmed) event;
      confirmDelivery(evt.deliveryId);
    }
  }
}

class MyDestination extends AbstractActor {
  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            Msg.class,
            msg -> {
              // ...
              getSender().tell(new Confirm(msg.deliveryId), getSelf());
            })
        .build();
  }
}
```
持久化模块生成的`deliveryId`是严格单调递增的序列号，没有间隙。相同的序列用于 Actor 的所有目的地，即当发送到多个目的地时，目的地将看到序列中的间隙。无法使用自定义`deliveryId`。但是，你可以将消息中的自定义关联标识符发送到目标。然后必须在内部`deliveryId`（传递到`deliveryIdToMessage`函数）和自定义关联`id`（传递到消息）之间保留映射。你可以通过将此类映射存储在一个`Map(correlationId -> deliveryId)`中来实现这一点，从该映射中，你可以在消息的接收者用你的自定义关联`id`答复之后，检索要传递到`confirmDelivery`方法的`deliveryId`。

`AbstractPersistentActorWithAtLeastOnceDelivery`类的状态由未确认的消息和序列号组成。它不存储此状态本身。你必须持久化与`PersistentActor`的`deliver`和`confirmDelivery`调用相对应的事件，以便在`PersistentActor`的恢复阶段通过调用相同的方法恢复状态。有时，这些事件可以从其他业务级事件派生，有时必须创建单独的事件。在恢复过程中，`deliver`调用不会发送消息，如果未执行匹配的`confirmDelivery`，则稍后将发送这些消息。

对快照的支持由`getDeliverySnapshot`和`setDeliverySnapshot`提供。`AtLeastOnceDeliverySnapshot`包含完整的传递状态，也包括未确认的消息。如你需要 Actor 状态的其他部分的自定义快照，则还必须包括`AtLeastOnceDeliverySnapshot`。它使用`protobuf`和普通的 Akka 序列化机制进行序列化。最简单的方法是将`AtLeastOnceDeliverySnapshot`的字节作为`blob`包含在自定义快照中。

重新传递尝试之间的间隔由`redeliverInterval`方法定义。可以使用`akka.persistence.at-least-once-delivery.redeliver-interval`配置键配置默认值。方法可以被实现类重写以返回非默认值。

在每次重新传递突发时将发送的最大消息数由`redeliveryBurstLimit`方法定义（突发频率是重新传递间隔的一半）。如果有很多未确认的消息（例如，如果目标 Actor 长时间不可用），这有助于防止同时发送大量的消息。默认值可以使用`akka.persistence.at-least-once-delivery.redelivery-burst-limit`配置键进行配置。方法可以被实现类重写以返回非默认值。

在多次尝试传递之后，至少会向`self`发送一条`AtLeastOnceDelivery.UnconfirmedWarning`消息。重新发送仍将继续，但你可以选择调用`confirmDelivery`以取消重新发送。发出警告前的传送尝试次数由`warnAfterNumberOfUnconfirmedAttempts`方法定义。可以使用`akka.persistence.at-least-once-delivery.warn-after-number-of-unconfirmed-attempts`配置键配置默认值。方法可以被实现类重写以返回非默认值。

`AbstractPersistentActorWithAtLeastOnceDelivery`类将消息保存在内存中，直到确认它们的成功传递为止。允许 Actor 在内存中保留的未确认消息的最大数目由`maxUnconfirmedMessages`方法定义。如果超过此限制，则传递方法将不接受更多的消息，并将引发`AtLeastOnceDelivery.MaxUnconfirmedMessagesExceededException`。可以使用`akka.persistence.at-least-once-delivery.max-unconfirmed-messages`配置键配置默认值。方法可以被实现类重写以返回非默认值。

## 事件适配器
在使用事件源（`event sourcing`）的长时间运行的项目中，有时需要将数据模型与域模型完全分离。

事件适配器（`Event Adapters`）在以下情况中提供帮助：

- **版本迁移**（`Version Migrations`），存储在版本 1 中的现有事件应“向上转换”为新的版本 2 表示，这样做的过程涉及实际代码，而不仅仅是序列化层的更改。对于这些场景，`toJournal`函数通常是一个标识函数，但是`fromJournal`实现为`v1.Event=>v2.Event`，在`fromJournal`方法中执行必要的映射。这种技术有时在其他 CQRS 库中被称为`upcasting`。
- **分离域和数据模型**（`Separating Domain and Data models`），由于`EventAdapters`，可以完全分离域模型和用于在日志中持久化数据的模型。例如，你可能希望在域模型中使用`case`类，但是将它们的协议缓冲区（或任何其他二进制序列化格式）计数器部分保留到日志中。可以使用简单的`toJournal:MyModel=>MyDataModel`和`fromJournal:MyDataModel=>MyModel`适配器来实现此功能。
- **日志专用数据类型**（`Journal Specialized Data Types`），暴露基础日志所理解的数据类型，例如，对于理解 JSON 的数据存储，可以写一个`EventAdapter `的`toJournal:Any=>JSON`，这样日志就可以直接存储 JSON，而不是将对象序列化为其二进制表示。

实现一个`EventAdapter`非常重要：

```java
class MyEventAdapter implements EventAdapter {
  @Override
  public String manifest(Object event) {
    return ""; // if no manifest needed, return ""
  }

  @Override
  public Object toJournal(Object event) {
    return event; // identity
  }

  @Override
  public EventSeq fromJournal(Object event, String manifest) {
    return EventSeq.single(event); // identity
  }
}
```
然后，为了在日志中的事件上使用它，必须使用以下配置语法绑定它：

```
akka.persistence.journal {
  inmem {
    event-adapters {
      tagging        = "docs.persistence.MyTaggingEventAdapter"
      user-upcasting = "docs.persistence.UserUpcastingEventAdapter"
      item-upcasting = "docs.persistence.ItemUpcastingEventAdapter"
    }

    event-adapter-bindings {
      "docs.persistence.Item"        = tagging
      "docs.persistence.TaggedEvent" = tagging
      "docs.persistence.v1.Event"    = [user-upcasting, item-upcasting]
    }
  }
}
```
可以将多个适配器（`adapter`）绑定到一个类以进行恢复，在这种情况下，所有绑定适配器的`fromJournal`方法将应用于给定的匹配事件（按照配置中的定义顺序）。由于每个适配器可以返回从`0`到`n`个适配事件（称为`EventSeq`），因此每个适配器都可以调查事件，如果确实需要对其进行适配，则返回相应的事件。在这个过程中没有任何贡献的其他适配器只返回`EventSeq.empty`。然后，在重放过程中，将调整后的事件传递给`PersistentActor`。

- **注释**：有关更高级的模式演化技术，请参阅「[Persistence - Schema Evolution](https://doc.akka.io/docs/akka/current/persistence-schema-evolution.html)」文档。

## 存储插件
日志和快照存储的存储后端可以插入到 Akka 持久性扩展中。

Akka 社区项目页面提供了持久性日志和快照存储插件的目录，请参阅「[社区插件](https://akka.io/community/)」。

插件可以通过“默认”为所有持久性 Actor 的选择，也可以在持久性 Actor 定义自己的插件集时“单独”选择。

当持久性 Actor 不重写`journalPluginId`和`snapshotPluginId`方法时，持久性扩展将使用`reference.conf`中配置的“默认”日志和快照存储插件：

```
akka.persistence.journal.plugin = ""
akka.persistence.snapshot-store.plugin = ""
```
但是，这些条目作为空的`""`提供，需要通过在用户的`application.conf`中的覆盖进行显式的用户配置。有关将消息写入 LevelDB 的日志插件的示例，请参阅「[Local LevelDB](https://doc.akka.io/docs/akka/current/persistence.html#local-leveldb-journal)」。有关将快照作为单个文件写入本地文件系统的快照存储插件的示例，请参阅「[Local snapshot](https://doc.akka.io/docs/akka/current/persistence.html#local-snapshot-store)」。

应用程序可以通过实现插件 API 并通过配置激活插件来提供自己的插件。插件开发需要以下导入：

```java
import akka.dispatch.Futures;
import akka.persistence.*;
import akka.persistence.journal.japi.*;
import akka.persistence.snapshot.japi.*;
```
### 持久性插件的预先初始化
默认情况下，持久性插件在使用时按需启动。然而，在某些情况下，预先启动某个插件可能会很有好处。为了做到这一点，你应该首先在`akka.extensions`键下添加`akka.persistence.Persistence`。然后，在`akka.persistence.journal.auto-start-journals`和`akka.persistence.snapshot-store.auto-start-snapshot-stores`下指定希望自动启动的插件的`ID`。

例如，如果你希望对 LevelDB 日志插件和本地快照存储插件进行预先初始化，那么你的配置应该如下所示：

```
akka {

  extensions = [akka.persistence.Persistence]

  persistence {

    journal {
      plugin = "akka.persistence.journal.leveldb"
      auto-start-journals = ["akka.persistence.journal.leveldb"]
    }

    snapshot-store {
      plugin = "akka.persistence.snapshot-store.local"
      auto-start-snapshot-stores = ["akka.persistence.snapshot-store.local"]
    }
  }
}
```
## 预打包插件
### 本地 LevelDB 日志
LevelDB 日志插件配置条目是`akka.persistence.journal.leveldb`。它将消息写入本地 LevelDB 实例。通过定义配置属性启用此插件：

```
# Path to the journal plugin to be used
akka.persistence.journal.plugin = "akka.persistence.journal.leveldb"
```
基于 LevelDB 的插件还需要以下附加依赖声明：

```xml
<!-- Maven -->
<dependency>
  <groupId>org.fusesource.leveldbjni</groupId>
  <artifactId>leveldbjni-all</artifactId>
  <version>1.8</version>
</dependency>

<!-- Gradle -->
dependencies {
  compile group: 'org.fusesource.leveldbjni', name: 'leveldbjni-all', version: '1.8'
}

<!-- sbt -->
libraryDependencies += "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8"
```
LevelDB 文件的默认位置是当前工作目录中名为`journal`的目录。可以通过配置更改此位置，其中指定的路径可以是相对路径或绝对路径：

```
akka.persistence.journal.leveldb.dir = "target/journal"
```
使用这个插件，每个 Actor 系统运行自己的私有 LevelDB 实例。

LevelDB 的一个特点是，删除操作不会从日志中删除消息，而是为每个已删除的消息添加一个“逻辑删除”。在大量使用日志的情况下，尤其是包括频繁删除的情况下，这可能是一个问题，因为用户可能会发现自己正在处理不断增加的日志大小。为此，LevelDB 提供了一个特殊的功能，通过以下配置开启：

```
# Number of deleted messages per persistence id that will trigger journal compaction
akka.persistence.journal.leveldb.compaction-intervals {
  persistence-id-1 = 100
  persistence-id-2 = 200
  # ...
  persistence-id-N = 1000
  # use wildcards to match unspecified persistence ids, if any
  "*" = 250
}
```
### 共享 LevelDB 日记
一个 LevelDB 实例也可以由多个 Actor 系统（在同一个或不同的节点上）共享。例如，这允许持久性 Actor 故障转移到备份节点，并继续从备份节点使用共享日志实例。

- **警告**：共享的 LevelDB 实例是一个单一的故障点，因此只能用于测试目的。
- **注释**：此插件已被「[Persistence Plugin Proxy](https://doc.akka.io/docs/akka/current/persistence.html#persistence-plugin-proxy)」取代。

通过实例化`SharedLeveldbStore` Actor 可以启动共享 LevelDB 实例。

```java
final ActorRef store = system.actorOf(Props.create(SharedLeveldbStore.class), "store");
```
默认情况下，共享实例将日志消息写入当前工作目录中名为`journal`的本地目录。存储位置可以通过配置进行更改：

```
akka.persistence.journal.leveldb-shared.store.dir = "target/shared"
```
使用共享 LevelDB 存储的 Actor 系统必须激活`akka.persistence.journal.leveldb-shared`插件。

```
akka.persistence.journal.plugin = "akka.persistence.journal.leveldb-shared"
```

必须通过插入（远程）`SharedLeveldbStore` Actor 引用来初始化此插件。注入是通过使用 Actor 引用作为参数调用`SharedLeveldbJournal.setStore`方法完成的。

```java
class SharedStorageUsage extends AbstractActor {
  @Override
  public void preStart() throws Exception {
    String path = "akka.tcp://example@127.0.0.1:2552/user/store";
    ActorSelection selection = getContext().actorSelection(path);
    selection.tell(new Identify(1), getSelf());
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            ActorIdentity.class,
            ai -> {
              if (ai.correlationId().equals(1)) {
                Optional<ActorRef> store = ai.getActorRef();
                if (store.isPresent()) {
                  SharedLeveldbJournal.setStore(store.get(), getContext().getSystem());
                } else {
                  throw new RuntimeException("Couldn't identify store");
                }
              }
            })
        .build();
  }
}
```
内部日志命令（由持久性 Actor 发送）被缓冲，直到注入完成。注入是幂等的，即只使用第一次注入。

### 本地快照存储
本地快照存储（`local snapshot store`）插件配置条目为`akka.persistence.snapshot-store.local`。它将快照文件写入本地文件系统。通过定义配置属性启用此插件：

```
# Path to the snapshot store plugin to be used
akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
```
默认存储位置是当前工作目录中名为`snapshots`的目录。这可以通过配置进行更改，其中指定的路径可以是相对路径或绝对路径：

```
akka.persistence.snapshot-store.local.dir = "target/snapshots"
```
请注意，不必指定快照存储插件。如果不使用快照，则无需对其进行配置。

### 持久化插件代理
持久化插件代理（`persistence plugin proxy`）允许跨多个 Actor 系统（在相同或不同节点上）共享日志和快照存储。例如，这允许持久性 Actor 故障转移到备份节点，并继续从备份节点使用共享日志实例。代理的工作方式是将所有日志/快照存储消息转发到一个共享的持久性插件实例，因此支持代理插件支持的任何用例。

- **警告**：共享日志/快照存储是单一故障点，因此应仅用于测试目的。

日志和快照存储代理分别通过`akka.persistence.journal.proxy`和`akka.persistence.snapshot-store.proxy`配置条目进行控制。将`target-journal-plugin`或`target-snapshot-store-plugin`键设置为要使用的基础插件（例如：`akka.persistence.journal.leveldb`）。在一个 Actor 系统中，`start-target-journal`和`start-target-snapshot-store`键应设置为`on`，这是将实例化共享持久性插件的系统。接下来，需要告诉代理如何找到共享插件。这可以通过设置`target-journal-address`和`target-snapshot-store-address`配置键来实现，也可以通过编程方式调用`PersistencePluginProxy.setTargetLocation`方法来实现。

- **注释**：当需要扩展时，Akka 会延迟地启动扩展，这包括代理。这意味着为了让代理正常工作，必须实例化目标节点上的持久性插件。这可以通过实例化`PersistencePluginProxyExtension`扩展或调用`PersistencePluginProxy.start`方法来完成。此外，代理持久性插件可以（也应该）使用其原始配置键进行配置。

## 自定义序列化
快照的序列化和`Persistent`消息的有效负载可以通过 Akka 的序列化基础设施进行配置。例如，如果应用程序想要序列化

- payloads of type MyPayload with a custom MyPayloadSerializer and
- snapshots of type MySnapshot with a custom MySnapshotSerializer

它必须增加：

```
akka.actor {
  serializers {
    my-payload = "docs.persistence.MyPayloadSerializer"
    my-snapshot = "docs.persistence.MySnapshotSerializer"
  }
  serialization-bindings {
    "docs.persistence.MyPayload" = my-payload
    "docs.persistence.MySnapshot" = my-snapshot
  }
}
```
到应用程序配置。如果未指定，则使用默认序列化程序。

有关更高级的模式演化技术，请参阅「[Persistence - Schema Evolution](https://doc.akka.io/docs/akka/current/persistence-schema-evolution.html)」文档。

## 测试

在 sbt 中使用 LevelDB 默认设置运行测试时，请确保在 sbt 项目中设置`fork := true`。否则，你将看到一个`UnsatisfiedLinkError`。或者，你可以通过设置切换到 LevelDB Java 端口。

```
akka.persistence.journal.leveldb.native = off
```

或

```
akka.persistence.journal.leveldb-shared.store.native = off
```

在你的 Akka 配置中，LevelDB Java 端口仅用于测试目的。

还要注意的是，对于 LevelDB Java 端口，你将需要以下依赖项：

```xml
<!-- Maven -->
<dependency>
  <groupId>org.iq80.leveldb</groupId>
  <artifactId>leveldb</artifactId>
  <version>0.9</version>
</dependency>

<!-- Gradle -->
dependencies {
  compile group: 'org.iq80.leveldb', name: 'leveldb', version: '0.9'
}

<!-- sbt -->
libraryDependencies += "org.iq80.leveldb" % "leveldb" % "0.9"
```
- **警告**：由于`TestActorRef`具有同步性，因此无法使用它来测试持久性提供的类（即`PersistentActor`和`AtLeastOnceDelivery`）。这些特性需要能够在后台执行异步任务，以便处理与持久性相关的内部事件。当「[测试基于持久性的项目](https://doc.akka.io/docs/akka/current/testing.html#async-integration-testing)」时，总是依赖于使用`TestKit`的异步消息传递。

## 配置
持久性模块有几个配置属性，请参阅参考「[配置](https://doc.akka.io/docs/akka/current/general/configuration.html#config-akka-persistence)」。

## 多持久性插件配置
默认情况下，持久性 Actor 将使用在`reference.conf`配置资源的以下部分中配置的“默认”日志和快照存储插件：

```
# Absolute path to the default journal plugin configuration entry.
akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
# Absolute path to the default snapshot store plugin configuration entry.
akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
```
注意，在这种情况下，Actor 只重写`persistenceId`方法：

```java
abstract class AbstractPersistentActorWithDefaultPlugins extends AbstractPersistentActor {
  @Override
  public String persistenceId() {
    return "123";
  }
}
```
当持久性 Actor 重写`journalPluginId`和`snapshotPluginId`方法时，Actor 将由这些特定的持久性插件而不是默认值提供服务：

```java
abstract class AbstractPersistentActorWithOverridePlugins extends AbstractPersistentActor {
  @Override
  public String persistenceId() {
    return "123";
  }

  // Absolute path to the journal plugin configuration entry in the `reference.conf`
  @Override
  public String journalPluginId() {
    return "akka.persistence.chronicle.journal";
  }

  // Absolute path to the snapshot store plugin configuration entry in the `reference.conf`
  @Override
  public String snapshotPluginId() {
    return "akka.persistence.chronicle.snapshot-store";
  }
}
```
请注意，`journalPluginId`和`snapshotPluginId`必须引用正确配置的`reference.conf`插件条目，这些插件具有标准类属性以及特定于这些插件的设置，即：

```
# Configuration entry for the custom journal plugin, see `journalPluginId`.
akka.persistence.chronicle.journal {
  # Standard persistence extension property: provider FQCN.
  class = "akka.persistence.chronicle.ChronicleSyncJournal"
  # Custom setting specific for the journal `ChronicleSyncJournal`.
  folder = $${user.dir}/store/journal
}
# Configuration entry for the custom snapshot store plugin, see `snapshotPluginId`.
akka.persistence.chronicle.snapshot-store {
  # Standard persistence extension property: provider FQCN.
  class = "akka.persistence.chronicle.ChronicleSnapshotStore"
  # Custom setting specific for the snapshot store `ChronicleSnapshotStore`.
  folder = $${user.dir}/store/snapshot
}
```

## 在运行时提供持久性插件配置
默认情况下，持久性 Actor 将使用在`ActorSystem`创建时加载的配置来创建日志和快照存储插件。

当持久性 Actor 重写`journalPluginConfig`和`snapshotPluginConfig`方法时，Actor 将使用声明的`Config`对象，并对默认配置进行回退（`fallback`）。它允许在运行时动态配置日志和快照存储：

```java
abstract class AbstractPersistentActorWithRuntimePluginConfig extends AbstractPersistentActor
    implements RuntimePluginConfig {
  // Variable that is retrieved at runtime, from an external service for instance.
  String runtimeDistinction = "foo";

  @Override
  public String persistenceId() {
    return "123";
  }

  // Absolute path to the journal plugin configuration entry in the `reference.conf`
  @Override
  public String journalPluginId() {
    return "journal-plugin-" + runtimeDistinction;
  }

  // Absolute path to the snapshot store plugin configuration entry in the `reference.conf`
  @Override
  public String snapshotPluginId() {
    return "snapshot-store-plugin-" + runtimeDistinction;
  }

  // Configuration which contains the journal plugin id defined above
  @Override
  public Config journalPluginConfig() {
    return ConfigFactory.empty()
        .withValue(
            "journal-plugin-" + runtimeDistinction,
            getContext()
                .getSystem()
                .settings()
                .config()
                .getValue(
                    "journal-plugin") // or a very different configuration coming from an external
            // service.
            );
  }

  // Configuration which contains the snapshot store plugin id defined above
  @Override
  public Config snapshotPluginConfig() {
    return ConfigFactory.empty()
        .withValue(
            "snapshot-plugin-" + runtimeDistinction,
            getContext()
                .getSystem()
                .settings()
                .config()
                .getValue(
                    "snapshot-store-plugin") // or a very different configuration coming from an
            // external service.
            );
  }
}
```

## 更多可见

- [Persistent FSM](https://doc.akka.io/docs/akka/current/persistence-fsm.html)
- [Building a new storage backend](https://doc.akka.io/docs/akka/current/persistence-journals.html)

----------

**英文原文链接**：[Persistence](https://doc.akka.io/docs/akka/current/persistence.html).


----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————