# Actor 发现
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

对于「[非类型化的 Actor](https://doc.akka.io/docs/akka/current/general/addressing.html)」，你将使用`ActorSelection`来查找 Actor。给定一个包含地址信息的 Actor 路径，你可以为任何 Actor 获取`ActorRef`。在 Akka 类型中不存在`ActorSelection`，那么如何获取 Actor 引用呢？你可以在消息中发送`refs`，但需要一些东西来引导交互。

## Receptionist

为此，有个 Actor 叫`Receptionist`。你注册了应该可以从本地`Receptionist`实例中的其他节点发现的特定 Actor。`Receptionist`的 API 也基于 Actor 消息。然后，Actor 引用的注册表将自动分发到集群中的所有其他节点。你可以使用注册时使用的键查找这些 Actor。对这样一个`Find`请求的答复是一个`Listing`，其中包含为键注册的一组 Actor 引用的`Set`。请注意，可以将多个 Actor 注册到同一个键。

注册表是动态的。新的 Actor 可以在系统的生命周期中注册。当已注册的 Actor 停止或节点从群集中删除时，条目将被删除。为了方便这个动态方面，你还可以通过发送`Receptionist.Subscribe`消息来订阅更改。当某个键的条目被更改时，它将向订阅服务器发送`Listing`消息。

使用`Receptionist`的主要场景是，当一个 Actor 需要被另一个 Actor 发现，但你无法在传入消息中引用它时。

以下示例中使用了这些导入：

```java
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
```

首先，我们创建一个`PingService` Actor，并根据一个`ServiceKey`向`Receptionist`注册它，该`ServiceKey`稍后将用于查找引用：

```java
public static class PingService {

  static final ServiceKey<Ping> pingServiceKey = ServiceKey.create(Ping.class, "pingService");

  public static class Pong {}

  public static class Ping {
    private final ActorRef<Pong> replyTo;

    public Ping(ActorRef<Pong> replyTo) {
      this.replyTo = replyTo;
    }
  }

  static Behavior<Ping> createBehavior() {
    return Behaviors.setup(
        context -> {
          context
              .getSystem()
              .receptionist()
              .tell(Receptionist.register(pingServiceKey, context.getSelf()));

          return Behaviors.receive(Ping.class).onMessage(Ping.class, PingService::onPing).build();
        });
  }

  private static Behavior<Ping> onPing(ActorContext<Ping> context, Ping msg) {
    context.getLog().info("Pinged by {}", msg.replyTo);
    msg.replyTo.tell(new Pong());
    return Behaviors.same();
  }
}
```

然后我们有另一个 Actor 需要构造`PingService`：

```java
public static class Pinger {
  static Behavior<PingService.Pong> createBehavior(ActorRef<PingService.Ping> pingService) {
    return Behaviors.setup(
        (ctx) -> {
          pingService.tell(new PingService.Ping(ctx.getSelf()));
          return Behaviors.receive(PingService.Pong.class)
              .onMessage(PingService.Pong.class, Pinger::onPong)
              .build();
        });
  }

  private static Behavior<PingService.Pong> onPong(
      ActorContext<PingService.Pong> context, PingService.Pong msg) {
    context.getLog().info("{} was ponged!!", context.getSelf());
    return Behaviors.stopped();
  }
}
```

最后，在守护者 Actor 中，我们生成服务，并订阅针对`ServiceKey`注册的任何 Actor。订阅意味着守护者 Actor 将通过`Listing`消息被通知任何新的注册：

```java
public static Behavior<Void> createGuardianBehavior() {
  return Behaviors.setup(
          context -> {
            context
                .getSystem()
                .receptionist()
                .tell(
                    Receptionist.subscribe(
                        PingService.pingServiceKey, context.getSelf().narrow()));
            context.spawnAnonymous(PingService.createBehavior());
            return Behaviors.receive(Object.class)
                .onMessage(
                    Receptionist.Listing.class,
                    (c, msg) -> {
                      msg.getServiceInstances(PingService.pingServiceKey)
                          .forEach(
                              pingService ->
                                  context.spawnAnonymous(Pinger.createBehavior(pingService)));
                      return Behaviors.same();
                    })
                .build();
          })
      .narrow();
}
```

每次注册一个新的`PingService`（本例中只有一次）时，守护者 Actor 都为每个当前已知的`PingService`生成一个`Pinger`。`Pinger`发送一个`Ping`消息，当接收到`Pong`回复时，它将停止。

## 集群 Receptionist

`Receptionist`也在集群中工作，注册到`Receptionist`的 Actor 将出现在集群其他节点的`Receptionist`中。

`Receptionist`的状态通过[分布式数据](https://doc.akka.io/docs/akka/current/distributed-data.html)传播，这意味着每个节点最终都将达到每个`ServiceKey`的同一组 Actor。

与仅本地接收的一个重要区别是串行化问题，从另一个节点上的 Actor 发送和返回的所有消息都必须是可序列化的，具体请参阅「[集群](https://doc.akka.io/docs/akka/current/typed/cluster.html#serialization)」。


----------

**英文原文链接**：[Actor discovery](https://doc.akka.io/docs/akka/current/typed/actor-discovery.html).



----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————