# Stash
## 依赖

为了使用 Akka Actor 类型，你需要将以下依赖添加到你的项目中：

```xml
<!-- Maven -->
<dependency>
  <groupId>com.typesafe.akka</groupId>
  <artifactId>akka-actor-typed_2.12</artifactId>
  <version>2.5.23</version>
</dependency>

<!-- Gradle -->
dependencies {
  compile group: 'com.typesafe.akka', name: 'akka-actor-typed_2.12', version: '2.5.23'
}

<!-- sbt -->
libraryDependencies += "com.typesafe.akka" %% "akka-actor-typed" % "2.5.23"
```

## 简介

`Stash`使 Actor 能够临时缓冲所有或某些不能或不应该使用 Actor 当前行为处理的消息。

当这一点很有用，一个典型的例子是，如果 Actor 在接受第一条真实消息之前加载了一些初始状态或者初始化了一些资源；另一个例子是，Actor 在处理下一条消息之前正在等待完成某件事情。

让我们用一个例子来说明这两个问题。它是一个 Actor，就像对存储在数据库中的值的单点访问一样使用。启动后，它从数据库中加载当前状态，并在等待该初始值的同时，所有传入的消息都被存储起来。

当一个新的状态保存在数据库中时，它也会存储传入的消息，以使处理连续进行，一个接一个，没有多个挂起的写入。

```java
import akka.actor.typed.javadsl.StashBuffer;

interface DB {
  CompletionStage<Done> save(String id, String value);

  CompletionStage<String> load(String id);
}

public static class DataAccess {

  static interface Command {}

  public static class Save implements Command {
    public final String payload;
    public final ActorRef<Done> replyTo;

    public Save(String payload, ActorRef<Done> replyTo) {
      this.payload = payload;
      this.replyTo = replyTo;
    }
  }

  public static class Get implements Command {
    public final ActorRef<String> replyTo;

    public Get(ActorRef<String> replyTo) {
      this.replyTo = replyTo;
    }
  }

  static class InitialState implements Command {
    public final String value;

    InitialState(String value) {
      this.value = value;
    }
  }

  static class SaveSuccess implements Command {
    public static final SaveSuccess instance = new SaveSuccess();

    private SaveSuccess() {}
  }

  static class DBError implements Command {
    public final RuntimeException cause;

    public DBError(RuntimeException cause) {
      this.cause = cause;
    }
  }

  private final StashBuffer<Command> buffer = StashBuffer.create(100);
  private final String id;
  private final DB db;

  public DataAccess(String id, DB db) {
    this.id = id;
    this.db = db;
  }

  Behavior<Command> behavior() {
    return Behaviors.setup(
        context -> {
          context.pipeToSelf(
              db.load(id),
              (value, cause) -> {
                if (cause == null) return new InitialState(value);
                else return new DBError(asRuntimeException(cause));
              });

          return init();
        });
  }

  private Behavior<Command> init() {
    return Behaviors.receive(Command.class)
        .onMessage(
            InitialState.class,
            (context, message) -> {
              // now we are ready to handle stashed messages if any
              return buffer.unstashAll(context, active(message.value));
            })
        .onMessage(
            DBError.class,
            (context, message) -> {
              throw message.cause;
            })
        .onMessage(
            Command.class,
            (context, message) -> {
              // stash all other messages for later processing
              buffer.stash(message);
              return Behaviors.same();
            })
        .build();
  }

  private Behavior<Command> active(String state) {
    return Behaviors.receive(Command.class)
        .onMessage(
            Get.class,
            (context, message) -> {
              message.replyTo.tell(state);
              return Behaviors.same();
            })
        .onMessage(
            Save.class,
            (context, message) -> {
              context.pipeToSelf(
                  db.save(id, message.payload),
                  (value, cause) -> {
                    if (cause == null) return SaveSuccess.instance;
                    else return new DBError(asRuntimeException(cause));
                  });
              return saving(message.payload, message.replyTo);
            })
        .build();
  }

  private Behavior<Command> saving(String state, ActorRef<Done> replyTo) {
    return Behaviors.receive(Command.class)
        .onMessageEquals(
            SaveSuccess.instance,
            context -> {
              replyTo.tell(Done.getInstance());
              return buffer.unstashAll(context, active(state));
            })
        .onMessage(
            DBError.class,
            (context, message) -> {
              throw message.cause;
            })
        .onMessage(
            Command.class,
            (context, message) -> {
              buffer.stash(message);
              return Behaviors.same();
            })
        .build();
  }

  private static RuntimeException asRuntimeException(Throwable t) {
    // can't throw Throwable in lambdas
    if (t instanceof RuntimeException) {
      return (RuntimeException) t;
    } else {
      return new RuntimeException(t);
    }
  }
}
```

需要注意的一件重要的事情是，`StashBuffer`是一个缓冲区，存储的消息将保存在内存中，直到它们被取消显示（或者 Actor 被停止并被垃圾回收）。建议避免存储过多的消息，以避免过多的内存使用，如果许多 Actor 存储过多的消息，甚至会有`OutOfMemoryError`的风险。因此，`StashBuffer`是有界的，在创建时必须指定它可以保存多少消息的`capacity`。

如果你试图存储的消息超过了`capacity`的容量，那么将抛出一个`StashOverflowException`。你可以在存储消息之前使用`StashBuffer.isFull`，以避免出现这种情况并采取其他操作，例如删除消息。

当通过调用`unstashAll`取消缓冲消息的显示时，所有消息将按添加它们的顺序依次处理，并且除非引发异常，否则将全部处理。在`unstashAll`完成之前，Actor 对其他新消息没有响应。这也是保持隐藏信息数量低的另一个原因。占用消息处理线程太长时间的 Actor 可能会导致其他 Actor 发生饥饿现象。

这可以通过使用`StashBuffer.unstash` 的`numberOfMessages`参数来减轻消息负载，然后在继续取消更多的显示之前向`context.getSelf`发送一条消息。这意味着其他新消息可能会在这期间到达，并且必须将它们存储起来，以保持消息的原始顺序。不过这将使它变得更加复杂，所以最好将隐藏消息的数量保持在较低的水平。



----------

**英文原文链接**：[Stash](https://doc.akka.io/docs/akka/current/typed/stash.html).




----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————