# 容错

当 Actor 在处理消息或初始化过程中抛出非预期的异常、失败时，默认情况下，Actor 将停止。

- **注释**：类型化 Actor 和非类型化 Actor 之间的一个重要区别是，如果抛出异常，并且在创建 Actor 时未定义监督策略，则类型化 Actor 将会停止，而非类型化 Actor 则会重启。

请注意，失败和验证错误之间有一个重要区别：

- 验证错误意味着发送给 Actor 的命令是无效的，这应该作为 Actor 协议的一部分建模，而不是使 Actor 抛出异常。
- 相反，失败是一些意外的事情，或者是 Actor 本身无法控制的事情，例如断开的数据库连接。与验证错误相反，将协议的某些部分建模为发送 Actor 没有太大的作用。
- 对于失败，应用“让它崩溃”的理念是很有用的：我们将责任转移到其他地方，而不是混合细粒度恢复和内部状态的更正，这些内部状态可能由于失败而部分无效。在许多情况下，解决方法可以是销毁 Actor，然后用我们知道有效的新状态创建一个新的 Actor。

## 监督

在 Akka 类型中，这个“其他地方”被称为监督（`In Akka Typed this “somewhere else” is called supervision`）。监督允许你声明性地描述在 Actor 内部抛出某种类型的异常时应该发生什么。要使用监督，实际的 Actor 行为将使用`Behaviors.supervise`进行包装，例如重新启动`IllegalStateExceptions`：

```java
Behaviors.supervise(behavior)
    .onFailure(IllegalStateException.class, SupervisorStrategy.restart());
```

或者，要继续，请忽略失败并处理下一条消息，而不是：

```java
Behaviors.supervise(behavior)
    .onFailure(IllegalStateException.class, SupervisorStrategy.resume());
```

可以使用更复杂的重启策略，例如，在 10 秒钟内重启不超过 10 次：

```java
Behaviors.supervise(behavior)
    .onFailure(
        IllegalStateException.class,
        SupervisorStrategy.restart().withLimit(10, FiniteDuration.apply(10, TimeUnit.SECONDS)));
```

要使用不同的策略处理不同的异常，可以嵌套调用`supervise`方法：

```java
Behaviors.supervise(
        Behaviors.supervise(behavior)
            .onFailure(IllegalStateException.class, SupervisorStrategy.restart()))
    .onFailure(IllegalArgumentException.class, SupervisorStrategy.stop());
```

有关策略的完整列表，请参阅有关`SupervisorStrategy`的公共方法。

### 包装行为

通过改变行为（`behavior`）来存储状态是很常见的，例如

```java
interface CounterMessage {}

public static final class Increase implements CounterMessage {}

public static final class Get implements CounterMessage {
  final ActorRef<Got> sender;

  public Get(ActorRef<Got> sender) {
    this.sender = sender;
  }
}

public static final class Got {
  final int n;

  public Got(int n) {
    this.n = n;
  }
}

public static Behavior<CounterMessage> counter(int currentValue) {
  return Behaviors.receive(CounterMessage.class)
      .onMessage(
          Increase.class,
          (context, o) -> {
            return counter(currentValue + 1);
          })
      .onMessage(
          Get.class,
          (context, o) -> {
            o.sender.tell(new Got(currentValue));
            return Behaviors.same();
          })
      .build();
}
```

进行此项监督时，只需将其添加到顶层：

```java
Behaviors.supervise(counter(1));
```

每个返回的行为都将自动与监督者重新包装。

## 当父级正在重新启动时，子 Actor 将停止

子 Actor 通常在重新启动父 Actor 时运行的`setup`块中启动。每次重新启动父 Actor 时，都会停止子 Actor，以避免资源泄漏，从而创建新的子 Actor。

```java
static Behavior<String> child(long size) {
  return Behaviors.receiveMessage(msg -> child(size + msg.length()));
}

static Behavior<String> parent() {
  return Behaviors.<String>supervise(
          Behaviors.setup(
              ctx -> {
                final ActorRef<String> child1 = ctx.spawn(child(0), "child1");
                final ActorRef<String> child2 = ctx.spawn(child(0), "child2");

                return Behaviors.receiveMessage(
                    msg -> {
                      // there might be bugs here...
                      String[] parts = msg.split(" ");
                      child1.tell(parts[0]);
                      child2.tell(parts[1]);
                      return Behaviors.same();
                    });
              }))
      .onFailure(SupervisorStrategy.restart());
}
```

可以重写此项，以便在重新启动父 Actor 时不影响子 Actor。然后，重新启动的父实例将具有与失败前相同的子实例。

如果子 Actor 是从`setup`创建的，如前一个示例中所示，并且在重新启动父 Actor 时它们应该保持完整（不停止），则应将`supervise`放置在`setup`中，并使用`SupervisorStrategy.restart().withStopChildren(false)`，如下所示：

```java
static Behavior<String> parent2() {
  return Behaviors.setup(
      ctx -> {
        final ActorRef<String> child1 = ctx.spawn(child(0), "child1");
        final ActorRef<String> child2 = ctx.spawn(child(0), "child2");

        // supervision strategy inside the setup to not recreate children on restart
        return Behaviors.<String>supervise(
                Behaviors.receiveMessage(
                    msg -> {
                      // there might be bugs here...
                      String[] parts = msg.split(" ");
                      child1.tell(parts[0]);
                      child2.tell(parts[1]);
                      return Behaviors.same();
                    }))
            .onFailure(SupervisorStrategy.restart().withStopChildren(false));
      });
}
```

这意味着`setup`块只在父 Actor 第一次启动时运行，而不是在重新启动时运行。

## 失败在整个层次结构中冒泡

在某些情况下，在 Actor 层次结构中向上推送关于如何处理失败的决策，并让父 Actor 处理失败（在非类型化的 Akka Actor 中，这是默认的工作方式）可能很有用。

当一个子 Actor 被终止时，父 Actor 要得到通知，就必须`watch`这个子 Actor。如果由于故障而停止子进程，则将收到包含原因的`ChildFailed`信号。`ChildFailed`扩展了`Terminated`，因此如果你的用例不需要区分停止和失败，你可以使用`Terminated`信号来处理这两个用例。

如果父级反过来不处理`Terminated`消息，那么它本身将失败，并抛出`akka.actor.typed.DeathPactException`。

在某些情况下，如果希望原始异常在层次结构中冒泡，可以通过处理`Terminated`信号并在每个 Actor 中重新抛出异常来完成。

```java
public class BubblingSample {
  interface Message {}

  public static class Fail implements Message {
    public final String text;

    public Fail(String text) {
      this.text = text;
    }
  }

  public static Behavior<Message> failingChildBehavior =
      Behaviors.receive(Message.class)
          .onMessage(
              Fail.class,
              (context, message) -> {
                throw new RuntimeException(message.text);
              })
          .build();

  public static Behavior<Message> middleManagementBehavior =
      Behaviors.setup(
          (context) -> {
            context.getLog().info("Middle management starting up");
            final ActorRef<Message> child = context.spawn(failingChildBehavior, "child");
            // we want to know when the child terminates, but since we do not handle
            // the Terminated signal, we will in turn fail on child termination
            context.watch(child);

            // here we don't handle Terminated at all which means that
            // when the child fails or stops gracefully this actor will
            // fail with a DeathWatchException
            return Behaviors.receive(Message.class)
                .onMessage(
                    Message.class,
                    (innerCtx, message) -> {
                      // just pass messages on to the child
                      child.tell(message);
                      return Behaviors.same();
                    })
                .build();
          });

  public static Behavior<Message> bossBehavior =
      Behaviors.setup(
          (context) -> {
            context.getLog().info("Boss starting up");
            final ActorRef<Message> middleManagement =
                context.spawn(middleManagementBehavior, "middle-management");
            context.watch(middleManagement);

            // here we don't handle Terminated at all which means that
            // when middle management fails with a DeathWatchException
            // this actor will also fail
            return Behaviors.receive(Message.class)
                .onMessage(
                    Message.class,
                    (innerCtx, message) -> {
                      // just pass messages on to the child
                      middleManagement.tell(message);
                      return Behaviors.same();
                    })
                .build();
          });

  public static void main(String[] args) {
    final ActorSystem<Message> system = ActorSystem.create(bossBehavior, "boss");

    system.tell(new Fail("boom"));
    // this will now bubble up all the way to the boss and as that is the user guardian it means
    // the entire actor system will stop
  }
}
```


----------

**英文原文链接**：[Fault Tolerance](https://doc.akka.io/docs/akka/current/typed/fault-tolerance.html).



----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————