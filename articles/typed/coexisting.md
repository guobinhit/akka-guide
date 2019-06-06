# 共存
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

我们相信 Akka 类型（`Typed`）将逐渐在现有系统中被采用，因此在同一个`ActorSystem`中，能够同时使用类型化和非类型化的 Actor 是很重要的。此外，我们不能在一个大爆炸版本（`one big bang release`）中集成所有现有模块，这也是为什么这两种编写 Actor 的方式必须能够共存的另一个原因。

有两种不同的`ActorSystem`：`akka.actor.ActorSystem`和`akka.actor.typed.ActorSystem`。

目前，类型化的 Actor 系统是通过在表面（`hood`）下使用非类型化的 Actor 系统来实现的。这在将来可能会改变。

类型化和非类型化可以通过以下方式交互：

- 非类型化的 Actor 系统可以创建类型化的 Actor
- 类型化的 Actor 可以向非类型化的 Actor 发送消息，反之亦然
- 从非类型化的父级生成并监督类型化的子级，反之亦然
- 从非类型化的 Actor 监视类型化的 Actor，反之亦然
- 非类型化的 Actor 系统可以转换为类型化的 Actor 系统

示例使用非类型化类的完全限定类名来区分具有相同名称的类型化类和非类型化类。

## 非类型化到类型化

同时存在的应用程序可能仍然有一个非类型化的`ActorSystem`。这可以转换为类型化的`ActorSystem`，以便新代码和迁移的部件不依赖于非类型化的系统：

```java
// In java use the static methods on Adapter to convert from typed to untyped
import akka.actor.typed.javadsl.Adapter;
akka.actor.ActorSystem untypedActorSystem = akka.actor.ActorSystem.create();
ActorSystem<Void> typedActorSystem = Adapter.toTyped(untypedActorSystem);
```

然后，对于新类型化的 Actor，这里介绍如何从未类型化的 Actor 创建、监视并向其发送消息。

```java
public abstract static class Typed {
  interface Command {}

  public static class Ping implements Command {
    public final akka.actor.typed.ActorRef<Pong> replyTo;

    public Ping(ActorRef<Pong> replyTo) {
      this.replyTo = replyTo;
    }
  }

  public static class Pong {}

  public static Behavior<Command> behavior() {
    return Behaviors.receive(Typed.Command.class)
        .onMessage(
            Typed.Ping.class,
            (context, message) -> {
              message.replyTo.tell(new Pong());
              return same();
            })
        .build();
  }
}
```

顶级非类型化 Actor 是以通常的方式创建的：

```java
akka.actor.ActorSystem as = akka.actor.ActorSystem.create();
akka.actor.ActorRef untyped = as.actorOf(Untyped.props());
```

然后它可以创建一个类型化的 Actor，监视它，并向它发送消息：

```java
public static class Untyped extends AbstractActor {
  public static akka.actor.Props props() {
    return akka.actor.Props.create(Untyped.class);
  }

  private final akka.actor.typed.ActorRef<Typed.Command> second =
      Adapter.spawn(getContext(), Typed.behavior(), "second");

  @Override
  public void preStart() {
    Adapter.watch(getContext(), second);
    second.tell(new Typed.Ping(Adapter.toTyped(getSelf())));
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            Typed.Pong.class,
            message -> {
              Adapter.stop(getContext(), second);
            })
        .match(
            akka.actor.Terminated.class,
            t -> {
              getContext().stop(getSelf());
            })
        .build();
  }
}
```

我们导入`Adapter`类并调用静态方法进行转换。

```java
// In java use the static methods on Adapter to convert from typed to untyped
import akka.actor.typed.javadsl.Adapter;
```

要在类型化和非类型化之间转换，`akka.actor.typed.javadsl.Adapter`中有适配器方法。注意上面示例中的内联注释。

## 类型化到非类型化

让我们把这个例子颠倒过来，首先创建类型化的 Actor，然后再创建非类型化的 Actor 作为子级。

下面将演示如何创建、监视和向此非类型化 Actor 来回（`back and forth`）发送消息：

```java
public static class Untyped extends AbstractActor {
  public static akka.actor.Props props() {
    return akka.actor.Props.create(Untyped.class);
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            Typed.Ping.class,
            message -> {
              message.replyTo.tell(new Typed.Pong());
            })
        .build();
  }
}
```

创建 Actor 系统和类型化的 Actor：

```java
ActorSystem as = ActorSystem.create();
ActorRef<Typed.Command> typed = Adapter.spawn(as, Typed.behavior(), "Typed");
```

然后，类型化的 Actor 创建非类型化的 Actor，监视它并发送和接收响应消息：

```java
public abstract static class Typed {
  public static class Ping {
    public final akka.actor.typed.ActorRef<Pong> replyTo;

    public Ping(ActorRef<Pong> replyTo) {
      this.replyTo = replyTo;
    }
  }

  interface Command {}

  public static class Pong implements Command {}

  public static Behavior<Command> behavior() {
    return akka.actor.typed.javadsl.Behaviors.setup(
        context -> {
          akka.actor.ActorRef second = Adapter.actorOf(context, Untyped.props(), "second");

          Adapter.watch(context, second);

          second.tell(
              new Typed.Ping(context.getSelf().narrow()), Adapter.toUntyped(context.getSelf()));

          return akka.actor.typed.javadsl.Behaviors.receive(Typed.Command.class)
              .onMessage(
                  Typed.Pong.class,
                  (_ctx, message) -> {
                    Adapter.stop(context, second);
                    return same();
                  })
              .onSignal(akka.actor.typed.Terminated.class, (_ctx, sig) -> stopped())
              .build();
        });
  }
}
```

## 监督

非类型化 Actor 的默认监督（`supervision`）是重新启动，而类型化 Actor 的默认监督是停止。当组合非类型化和类型化 Actor 时，默认监督基于子级的默认行为，即，如果非类型化 Actor 创建类型化子级，则其默认监督将停止；如果类型化的 Actor 创建了非类型化的子级，则其默认监督将是重新启动。


----------

**英文原文链接**：[Coexistence](https://doc.akka.io/docs/akka/current/typed/coexisting.html).


----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————