# 作为 FSM」的行为

对于非类型化的 Actor，有明确的支持来构建「[有限状态机](https://doc.akka.io/docs/akka/current/fsm.html)」。在 Akka 类型中不需要支持，因为用行为表示 FSM 很简单。

为了了解如何使用 Akka 类型的 API 来建模 FSM，下面是从「[非类型化的 Actor FSM 文档](https://doc.akka.io/docs/akka/current/fsm.html)」移植的`Buncher`示例。它演示了如何：

- 使用不同行为模拟状态
- 通过将行为表示为一种方法，在每个状态下存储数据的模型
- 实现状态超时

FSM 可以接收的事件为 Actor 可以接收的消息类型：

```java
interface Event {}
static final class SetTarget implements Event {
  private final ActorRef<Batch> ref;

  public SetTarget(ActorRef<Batch> ref) {
    this.ref = ref;
  }

  public ActorRef<Batch> getRef() {
    return ref;
  }
}
final class Timeout implements Event {}

static final Timeout TIMEOUT = new Timeout();
public enum Flush implements Event {
  FLUSH
}
static final class Queue implements Event {
  private final Object obj;

  public Queue(Object obj) {
    this.obj = obj;
  }

  public Object getObj() {
    return obj;
  }
}
```

启动它需要`SetTarget`，为要传递的`Batches`设置目标；`Queue`将添加到内部队列，而`Flush`将标记突发的结束。

非类型化 FSM 也有一个`D`（数据）类型参数。Akka 类型化不需要知道这一点，它可以通过将你的行为定义为方法来存储。

```java
interface Data {}
final class Todo implements Data {
  private final ActorRef<Batch> target;
  private final List<Object> queue;

  public Todo(ActorRef<Batch> target, List<Object> queue) {
    this.target = target;
    this.queue = queue;
  }

  public ActorRef<Batch> getTarget() {
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

  public Todo copy(ActorRef<Batch> target) {
    return new Todo(target, this.queue);
  }
}
```

每个状态都会变成一种不同的行为。不需要显式`goto`，因为 Akka 类型已经要求你返回下一个行为。

```java
// FSM states represented as behaviors
private static Behavior<Event> uninitialized() {
  return Behaviors.receive(Event.class)
      .onMessage(
          SetTarget.class,
          (context, message) -> idle(new Todo(message.getRef(), Collections.emptyList())))
      .build();
}

private static Behavior<Event> idle(Todo data) {
  return Behaviors.receive(Event.class)
      .onMessage(Queue.class, (context, message) -> active(data.addElement(message)))
      .build();
}

private static Behavior<Event> active(Todo data) {
  return Behaviors.withTimers(
      timers -> {
        // State timeouts done with withTimers
        timers.startSingleTimer("Timeout", TIMEOUT, Duration.ofSeconds(1));
        return Behaviors.receive(Event.class)
            .onMessage(Queue.class, (context, message) -> active(data.addElement(message)))
            .onMessage(
                Flush.class,
                (context, message) -> {
                  data.getTarget().tell(new Batch(data.queue));
                  return idle(data.copy(new ArrayList<>()));
                })
            .onMessage(
                Timeout.class,
                (context, message) -> {
                  data.getTarget().tell(new Batch(data.queue));
                  return idle(data.copy(new ArrayList<>()));
                })
            .build();
      });
}
```

要设置状态超时，请使用`Behaviors.withTimers`和`startSingleTimer`。

以前在`onTransition`块中所做的任何副作用（`side effects`）都直接进入行为。


----------

**英文原文链接**：[Behaviors as Finite state machines](https://doc.akka.io/docs/akka/current/typed/fsm.html).



----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————