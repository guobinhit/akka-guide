# FSM
## 依赖

为了使用有限状态机（`Finite State Machine`）Actor，你需要将以下依赖添加到你的项目中：

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

## 示例项目

你可以查看「[FSM 示例项目](https://developer.lightbend.com/start/?group=akka&project=akka-samples-fsm-java)」，以了解实际应用中的情况。

## 概述

FSM（有限状态机）是一个抽象的基类，它实现了一个 Akka Actor，并在「[Erlang设 计原则](http://erlang.org/documentation/doc-4.8.2/doc/design_principles/fsm.html)」中得到了最好的描述。

FSM 可以描述为一组形式的关系：

- `State(S) x Event(E) -> Actions (A), State(S’)`

这些关系被解释为如下含义：

- 如果我们处于状态`S`，并且事件`E`发生，那么我们应该执行操作`A`，并向状态`S’`过渡。

## 一个简单的例子

为了演示`AbstractFSM`类的大部分特性，考虑一个 Actor，该 Actor 在消息到达突发（`burst`）时接收和排队消息，并在突发结束或收到刷新（`flush`）请求后发送它们。

首先，考虑使用以下所有导入语句：

```java
import akka.actor.AbstractFSM;
import akka.actor.ActorRef;
import akka.japi.pf.UnitMatch;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.time.Duration;
```

我们的“`Buncher`” Actor 的协议（`contract`）是接受或产生以下信息：

```java
static final class SetTarget {
  private final ActorRef ref;

  public SetTarget(ActorRef ref) {
    this.ref = ref;
  }

  public ActorRef getRef() {
    return ref;
  }

  @Override
  public String toString() {
    return "SetTarget{" + "ref=" + ref + '}';
  }
}

static final class Queue {
  private final Object obj;

  public Queue(Object obj) {
    this.obj = obj;
  }

  public Object getObj() {
    return obj;
  }

  @Override
  public String toString() {
    return "Queue{" + "obj=" + obj + '}';
  }
}

static final class Batch {
  private final List<Object> list;

  public Batch(List<Object> list) {
    this.list = list;
  }

  public List<Object> getList() {
    return list;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Batch batch = (Batch) o;

    return list.equals(batch.list);
  }

  @Override
  public int hashCode() {
    return list.hashCode();
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("Batch{list=");
    list.stream()
        .forEachOrdered(
            e -> {
              builder.append(e);
              builder.append(",");
            });
    int len = builder.length();
    builder.replace(len, len, "}");
    return builder.toString();
  }
}

static enum Flush {
  Flush
}
```

启动它需要`SetTarget`，为要传递的`Batches`设置目标；`Queue`将添加到内部队列，而`Flush`将标记突发（`burst`）的结束。

```java
// states
enum State {
  Idle,
  Active
}

// state data
interface Data {}

enum Uninitialized implements Data {
  Uninitialized
}

final class Todo implements Data {
  private final ActorRef target;
  private final List<Object> queue;

  public Todo(ActorRef target, List<Object> queue) {
    this.target = target;
    this.queue = queue;
  }

  public ActorRef getTarget() {
    return target;
  }

  public List<Object> getQueue() {
    return queue;
  }

  @Override
  public String toString() {
    return "Todo{" + "target=" + target + ", queue=" + queue + '}';
  }

  public Todo addElement(Object element) {
    List<Object> nQueue = new LinkedList<>(queue);
    nQueue.add(element);
    return new Todo(this.target, nQueue);
  }

  public Todo copy(List<Object> queue) {
    return new Todo(this.target, queue);
  }

  public Todo copy(ActorRef target) {
    return new Todo(target, this.queue);
  }
}
```

Actor 可以处于两种状态：没有消息排队（即`Idle`）或有消息排队（即`Active`）。它将保持`Active`状态，只要消息一直到达并且不请求刷新。Actor 的内部状态数据由发送的目标 Actor 引用和消息的实际队列组成。

现在让我们来看看我们的`FSM` Actor 的结构（`skeleton`）：

```java
public class Buncher extends AbstractFSM<State, Data> {
  {
    startWith(Idle, Uninitialized);

    when(
        Idle,
        matchEvent(
            SetTarget.class,
            Uninitialized.class,
            (setTarget, uninitialized) ->
                stay().using(new Todo(setTarget.getRef(), new LinkedList<>()))));

    onTransition(
        matchState(
                Active,
                Idle,
                () -> {
                  // reuse this matcher
                  final UnitMatch<Data> m =
                      UnitMatch.create(
                          matchData(
                              Todo.class,
                              todo ->
                                  todo.getTarget().tell(new Batch(todo.getQueue()), getSelf())));
                  m.match(stateData());
                })
            .state(
                Idle,
                Active,
                () -> {
                  /* Do something here */
                }));

    when(
        Active,
        Duration.ofSeconds(1L),
        matchEvent(
            Arrays.asList(Flush.class, StateTimeout()),
            Todo.class,
            (event, todo) -> goTo(Idle).using(todo.copy(new LinkedList<>()))));

    whenUnhandled(
        matchEvent(
                Queue.class,
                Todo.class,
                (queue, todo) -> goTo(Active).using(todo.addElement(queue.getObj())))
            .anyEvent(
                (event, state) -> {
                  log()
                      .warning(
                          "received unhandled request {} in state {}/{}",
                          event,
                          stateName(),
                          state);
                  return stay();
                }));

    initialize();
  }
}
```

基本策略是通过继承`AbstractFSM`类并将可能的状态和数据值指定为类型参数来声明 Actor。在 Actor 的主体中，DSL 用于声明状态机：

- `startWith`定义初始状态和初始数据
- `when(<state>) { ... }`是要处理的每个状态的声明（可能是多个状态，传递的`PartialFunction`将使用`orElse`连接）
- 最后使用`initialize`启动它，它执行到初始状态的转换并设置定时器（如果需要）。

在这种情况下，我们从`Idle`状态开始，使用`Uninitialized`数据，其中只处理`SetTarget()`消息；`stay`准备结束此事件的处理，以避免离开当前状态，而`using`修饰符使 FSM 用包含目标 Actor 引用的`Todo()`对象替换内部状态（此时`Uninitialized`在这个点）。`Active`状态已声明状态超时，这意味着如果在 1 秒内没有收到消息，将生成`FSM.StateTimeout `消息。这与在这种情况下接收`Flush`命令的效果相同，即转换回`Idle`状态并将内部队列重置为空向量。但是消息是如何排队的呢？由于这两种状态下的工作方式相同，因此我们利用以下事实：未由`when()`块处理的任何事件都传递给`whenUnhandled()`块：

```java
whenUnhandled(
    matchEvent(
            Queue.class,
            Todo.class,
            (queue, todo) -> goTo(Active).using(todo.addElement(queue.getObj())))
        .anyEvent(
            (event, state) -> {
              log()
                  .warning(
                      "received unhandled request {} in state {}/{}",
                      event,
                      stateName(),
                      state);
              return stay();
            }));
```

这里处理的第一个案例是将`Queue() `请求添加到内部队列并进入`Active`状态（如果已经存在的话，这显然会保持`Active`状态），但前提是在接收到`Queue()`事件时，FSM 数据没有`Uninitialized`。否则，在所有其他未处理的情况下，第二种情况只会记录一个警告，而不会更改内部状态。

唯一缺少的部分是`Batches`实际发送到目标的位置，为此我们使用了`onTransition`机制：你可以声明多个这样的块，如果发生状态转换（即只有当状态实际更改时），所有这些块都将尝试匹配行为。

```java
onTransition(
    matchState(
            Active,
            Idle,
            () -> {
              // reuse this matcher
              final UnitMatch<Data> m =
                  UnitMatch.create(
                      matchData(
                          Todo.class,
                          todo ->
                              todo.getTarget().tell(new Batch(todo.getQueue()), getSelf())));
              m.match(stateData());
            })
        .state(
            Idle,
            Active,
            () -> {
              /* Do something here */
            }));
```

转换回调是由`matchState`构造的一个生成器，后跟零或多个`state`，它将当前`state`和下一个`state`作为一对状态的输入。在状态更改期间，旧的状态数据通过`stateData()`可用，如展示的这样，新的状态数据将作为`nextStateData()`可用。

- **注释**：可以使用`goto(S)`或`stay()`实现相同的状态转换（当前处于状态`S`时）。不同之处在于，`goto(S)`会发出一个事件`S->S`，该事件可以由`onTransition`处理，而`stay()`则不会。

为了验证这个`Buncher`是否真的有效，使用「[TestKit](https://doc.akka.io/docs/akka/current/testing.html)」编写一个测试非常容易，这里使用 JUnit 作为示例：

```java
public class BuncherTest extends AbstractJavaTest {

  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create("BuncherTest");
  }

  @AfterClass
  public static void tearDown() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  @Test
  public void testBuncherActorBatchesCorrectly() {
    new TestKit(system) {
      {
        final ActorRef buncher = system.actorOf(Props.create(Buncher.class));
        final ActorRef probe = getRef();

        buncher.tell(new SetTarget(probe), probe);
        buncher.tell(new Queue(42), probe);
        buncher.tell(new Queue(43), probe);
        LinkedList<Object> list1 = new LinkedList<>();
        list1.add(42);
        list1.add(43);
        expectMsgEquals(new Batch(list1));
        buncher.tell(new Queue(44), probe);
        buncher.tell(Flush, probe);
        buncher.tell(new Queue(45), probe);
        LinkedList<Object> list2 = new LinkedList<>();
        list2.add(44);
        expectMsgEquals(new Batch(list2));
        LinkedList<Object> list3 = new LinkedList<>();
        list3.add(45);
        expectMsgEquals(new Batch(list3));
        system.stop(buncher);
      }
    };
  }

  @Test
  public void testBuncherActorDoesntBatchUninitialized() {
    new TestKit(system) {
      {
        final ActorRef buncher = system.actorOf(Props.create(Buncher.class));
        final ActorRef probe = getRef();

        buncher.tell(new Queue(42), probe);
        expectNoMessage();
        system.stop(buncher);
      }
    };
  }
}
```

## 引用
### AbstractFSM 类

`AbstractFSM`抽象类是用于实现 FSM 的基类。它实现了 Actor，因为创建了一个 Actor 来驱动 FSM。

```java
public class Buncher extends AbstractFSM<State, Data> {
  {
    startWith(Idle, Uninitialized);

    when(
        Idle,
        matchEvent(
            SetTarget.class,
            Uninitialized.class,
            (setTarget, uninitialized) ->
                stay().using(new Todo(setTarget.getRef(), new LinkedList<>()))));

    onTransition(
        matchState(
                Active,
                Idle,
                () -> {
                  // reuse this matcher
                  final UnitMatch<Data> m =
                      UnitMatch.create(
                          matchData(
                              Todo.class,
                              todo ->
                                  todo.getTarget().tell(new Batch(todo.getQueue()), getSelf())));
                  m.match(stateData());
                })
            .state(
                Idle,
                Active,
                () -> {
                  /* Do something here */
                }));

    when(
        Active,
        Duration.ofSeconds(1L),
        matchEvent(
            Arrays.asList(Flush.class, StateTimeout()),
            Todo.class,
            (event, todo) -> goTo(Idle).using(todo.copy(new LinkedList<>()))));

    whenUnhandled(
        matchEvent(
                Queue.class,
                Todo.class,
                (queue, todo) -> goTo(Active).using(todo.addElement(queue.getObj())))
            .anyEvent(
                (event, state) -> {
                  log()
                      .warning(
                          "received unhandled request {} in state {}/{}",
                          event,
                          stateName(),
                          state);
                  return stay();
                }));

    initialize();
  }
}
```

- **注释**：`AbstractFSM`类定义了一个`receive`方法，该方法处理内部消息，并将其他所有信息传递给 FSM 逻辑（根据当前状态）。当覆盖`receive`方法时，请记住，例如状态超时处理取决于通过 FSM 逻辑实际传递消息。

`AbstractFSM`类采用两个类型参数：

- 所有状态名的父类型，通常是枚举
- `AbstractFSM`模块本身跟踪的状态数据的类型。

特别地，状态数据和状态名称一起描述状态机的内部状态；如果你坚持这个方案，并且不向 FSM 类添加可变字段，则可以在一些众所周知的地方显式地进行内部状态的所有更改。

### 定义状态

状态由方法的一个或多个调用定义。

- `when(<name>[, stateTimeout = <timeout>])(stateFunction)`

给定的名称必须是与`AbstractFSM`类的第一个类型参数类型兼容的对象。此对象用作哈希键，因此必须确保它正确实现`equals`和`hashCode`；尤其是它不能是可变的。最适合这些需求的是`case`对象。

如果给定`stateTimeout`参数，那么默认情况下，所有转换到该状态（包括保持）的操作都将接收该超时。使用显式超时启动转换可用于重写此默认值，有关详细信息，请参阅「[Initiating Transitions](https://doc.akka.io/docs/akka/current/fsm.html#initiating-transitions)」。在使用`setStateTimeout(state, duration)`进行操作处理期间，可以更改任何状态的状态超时。这将启用运行时配置，例如通过外部消息。

`stateFunction`参数是一个`PartialFunction[Event, State]`，它使用状态函数生成器语法方便地给出，如下所示：

```java
when(
    Idle,
    matchEvent(
        SetTarget.class,
        Uninitialized.class,
        (setTarget, uninitialized) ->
            stay().using(new Todo(setTarget.getRef(), new LinkedList<>()))));
```

- **警告**：需要为每个可能的 FSM 状态定义处理程序，否则在尝试切换到未声明的状态时将出现故障。

建议将状态声明为枚举，然后验证每个状态都有一个`when`子句。如果要使状态的处理“`unhandled`”（下面将详细介绍），则仍需要这样声明：

```java
when(SomeState, AbstractFSM.NullFunction());
```

### 定义初始状态

每个 FSM 都需要一个起点（`starting point`），该起点使用：

```java
startWith(state, data[, timeout])
```

可选的给定超时参数重写为所需初始状态给定的任何规范。如果要取消默认超时，请使用`Duration.Inf`。

### 未处理的事件

如果状态不处理接收到的事件，则会记录警告。如果要在这种情况下执行其他操作，可以使用`whenUnhandled(stateFunction)`指定：

```java
 whenUnhandled(
      matchEvent(
              X.class,
              (x, data) -> {
                log().info("Received unhandled event: " + x);
                return stay();
              })
          .anyEvent(
              (event, data) -> {
                log().warning("Received unknown event: " + event);
                return goTo(Error);
              }));
}
```

在此处理程序中，可以使用`stateName`方法查询 FSM 的状态。

- **重要的**：此处理程序不是堆叠的，这意味着每次调用`whenUnhandled`都会替换先前安装的（`installed`）处理程序。

### 启动转换

任何`stateFunction`的结果都必须是下一个状态的定义，除非终止 FSM，如「[Termination from Inside](https://doc.akka.io/docs/akka/current/fsm.html#termination-from-inside)」。状态定义可以是当前状态（如`stay`指令所述），也可以是`goto(state)`给出的不同状态。结果对象允许通过下面描述的修饰符进一步限定：

- `forMax(duration)`，此修饰符设置下一个状态的状态超时。这意味着计时器（`timer`）启动，到期时向 FSM 发送`StateTimeout`消息。此计时器在同时接收到任何其他消息时被取消；你可以依赖这样一个事实，即在干预消息之后将不会处理`StateTimeout`消息。此修饰符还可用于重写为目标状态指定的任何默认超时。如果要取消默认超时，请使用`Duration.Inf`。
- `using(data)`，此修饰符将旧状态数据替换为给定的新数据。如果你遵循上面的建议，这是唯一一个修改内部状态数据的地方。
- `replying(msg)`，此修饰符向当前处理的消息发送答复，否则不会修改状态转换。

所有修饰符都可以链接起来，以实现一个漂亮简洁的描述：

```java
when(
    SomeState,
    matchAnyEvent(
        (msg, data) -> {
          return goTo(Processing)
              .using(newData)
              .forMax(Duration.ofSeconds(5))
              .replying(WillDo);
        }));
```

实际上并非所有情况下都需要括号，但它们在视觉上区分修饰符和它们的参数，因此使代码更易于阅读。

- **注释**：请注意，`return`语句不能在`when`块或类似块中使用；这是一个 Scala 限制。使用`if () ... else ...`或者将其移动到方法定义中。

### 监视转换

概念上，“状态之间”会发生转换，这意味着在将任何操作放入事件处理块之后，这是显而易见的，因为下一个状态仅由事件处理逻辑返回的值定义。你不必担心设置内部状态变量的确切顺序，因为 FSM Actor 中的所有内容都在以单线程运行。

### 内部监控

到目前为止，FSM DSL 一直以状态和事件为中心。双视图（`dual view`）将其描述为一系列转换。这是由方法启用的

```java
onTransition(handler)
```

它将动作与转换相关联，而不是与状态和事件相关联。处理程序是一个以一对状态作为输入的部分函数；不需要结果状态，因为无法修改正在进行的转换。

```java
onTransition(
    matchState(Idle, Active, () -> setTimer("timeout", Tick, Duration.ofSeconds(1L), true))
        .state(Active, null, () -> cancelTimer("timeout"))
        .state(null, Idle, (f, t) -> log().info("entering Idle from " + f)));
```

也可以将接受两种状态的函数对象传递给`onTransition`，以将转换处理逻辑实现为一种方法：

```java
public void handler(StateType from, StateType to) {
  // handle transition here
}

  onTransition(this::handler);
```

使用此方法注册的处理程序是堆叠（`stacked`）的，因此你可以在适合你的设计块中散置`intersperse`块。但是，应该注意的是，要为每个转换（`transition`）调用所有处理程序，而不仅仅是第一个匹配的处理程序。这是专门设计的，这样你就可以将某个方面的所有转换处理放在一个地方，而不必担心前面的声明会影响后面的声明；不过，操作仍然是按声明顺序执行的。

- **注释**：这种内部监控可用于根据转换构造你的 FSM，例如，在添加新的目标状态时，不能忘记在离开某个状态时取消计时器。

### 外部监控

外部 Actor 可以通过发送消息`SubscribeTransitionCallBack(actorRef)`来注册以获得状态转换的通知。命名的 Actor 将立即发送一条`CurrentState(self, stateName)`消息，并在触发状态更改时接收`Transition(actorRef, oldState, newState)`消息。

通过向 FSM Actor 发送`UnsubscribeTransitionCallBack(actorRef)`，可以注销外部监控。

在不注销的情况下停止侦听器（`listener`）将不会从订阅列表中删除该侦听器；请在停止侦听器之前使用`UnsubscribeTransitionCallback`。

### 定时器
除了状态超时之外，FSM 还管理由`String`名称标识的定时器（`timers`）。你可以使用

```java
setTimer(name, msg, interval, repeat)
```

其中`msg`是将在持续时间`interval`结束后发送的消息对象。如果`repeat`为`true`，则计时器按`interval`参数给定的固定速率调度。在添加新计时器之前，任何具有相同名称的现有计时器都将自动取消。

计时器取消可以使用：

```java
cancelTimer(name)
```

它保证立即工作，这意味着即使计时器已经启动并将其排队，也不会在调用后处理计划的消息。任何计时器的状态都可以通过以下方式获取：

```java
isTimerActive(name)
```

这些命名的计时器补充状态超时，因为它们不受接收其他消息的影响。

### 从内部终止

通过将结果状态指定为以下方式来停止 FSM：

```java
stop([reason[, data]])
```

原因必须是`Normal`（默认）、`Shutdown`或`Failure(reason)`之一，并且可以给出第二个参数来更改终止处理期间可用的状态数据。

- **注释**：应该注意的是，停止不会中止动作，并立即停止 FSM。停止操作必须以与状态转换相同的方式从事件处理程序返回，但请注意，在`when`块中不能使用`return`语句。

```java
when(
    Error,
    matchEventEquals(
        "stop",
        (event, data) -> {
          // do cleanup ...
          return stop();
        }));
```

可以使用`onTermination(handler)`指定在 FSM 停止时执行的自定义代码。处理程序是一个分部函数，它以`StopEvent(reason, stateName, stateData) `作为参数：

```java
onTermination(
    matchStop(
            Normal(),
            (state, data) -> {
              /* Do something here */
            })
        .stop(
            Shutdown(),
            (state, data) -> {
              /* Do something here */
            })
        .stop(
            Failure.class,
            (reason, state, data) -> {
              /* Do something here */
            }));
```

对于`whenUnhandled`案例，此处理程序不堆叠，因此每次调用`onTermination`都会替换先前安装的处理程序。

### 从外部终止

当使用`stop()`方法停止与 FSM 关联的`ActorRef`时，将执行其`postStop`钩子。`AbstractFSM`类的默认实现是在准备处理`StopEvent(Shutdown, ...)`时执行`onTermination`处理程序。

- **警告**：如果你重写`postStop`并希望调用`onTermination`处理程序，请不要忘记调用`super.postStop`。

## 有限状态机的测试和调试

在开发和故障排除过程中，FSM 和其他 Actor 一样需要关注。如「[TestFSMRef](https://doc.akka.io/docs/akka/current/testing.html#testfsmref)」和以下所述，有专门的工具可用。

### 事件跟踪

在「[配置](https://doc.akka.io/docs/akka/current/general/configuration.html)」中设置`akka.actor.debug.fsm`可以通过`LoggingFSM`实例记录事件跟踪：

```java
static class MyFSM extends AbstractLoggingFSM<StateType, Data> {
  @Override
  public int logDepth() {
    return 12;
  }

  {
    onTermination(
        matchStop(
            Failure.class,
            (reason, state, data) -> {
              String lastEvents = getLog().mkString("\n\t");
              log()
                  .warning(
                      "Failure in state "
                          + state
                          + " with data "
                          + data
                          + "\n"
                          + "Events leading up to this point:\n\t"
                          + lastEvents);
            }));
    // ...
  }
}
```

此 FSM 将在`DEBUG`级别记录日志：

- 所有已处理的事件，包括`StateTimeout`和定时计时器消息
- 每次设置和取消指定计时器
- 所有状态转换

生命周期更改和特殊消息可以按照对「[Actors](https://doc.akka.io/docs/akka/current/testing.html#actor-logging)」的描述进行记录。

### 滚动事件日志

`AbstractLoggingFSM`类向 FSM 添加了另一个功能：滚动事件日志（`rolling event log`），可在调试期间（用于跟踪 FSM 如何进入特定故障状态）或其他创造性用途中使用：

```java
static class MyFSM extends AbstractLoggingFSM<StateType, Data> {
  @Override
  public int logDepth() {
    return 12;
  }

  {
    onTermination(
        matchStop(
            Failure.class,
            (reason, state, data) -> {
              String lastEvents = getLog().mkString("\n\t");
              log()
                  .warning(
                      "Failure in state "
                          + state
                          + " with data "
                          + data
                          + "\n"
                          + "Events leading up to this point:\n\t"
                          + lastEvents);
            }));
    // ...
  }
}
```

`logDepth`默认为零，这将关闭事件日志。

- **警告**：日志缓冲区是在 Actor 创建期间分配的，这就是使用虚拟方法调用完成配置的原因。如果要使用`val`进行重写，请确保其初始化发生在运行`LoggingFSM`的初始值设定项之前，并且不要在分配缓冲区后更改`logDepth`返回的值。

事件日志的内容可使用`getLog`方法获取，该方法返回`IndexedSeq[LogEntry]`，其中最早的条目位于索引零。

## 示例

与 Actor 的`become/unbecome`相比，一个更大的 FSM 示例可以下载成一个随时可以运行「[Akka FSM 示例](https://example.lightbend.com/v1/download/akka-samples-fsm-java)」和一个教程。此示例的源代码也可以在「[Akka Samples Repository](https://developer.lightbend.com/start/?group=akka&amp;project=akka-sample-fsm-java)」中找到。




----------

**英文原文链接**：[FSM](https://doc.akka.io/docs/akka/current/fsm.html).





----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————