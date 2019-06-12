# 路由
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

在某些情况下，将同一类型的消息分发到一组 Actor 上是很有用的，这样消息就可以并行处理，单个 Actor 一次只处理一条消息。

路由（`router`）本身就是一种行为，它衍生到一个正在运行的 Actor 身上，然后将发送给它的任何消息转发到路由集之外的最后一个收件人。

Akka 型路由包括两种类型的路由：池路由和组路由。

## 池路由

池路由是用一个路由`Behavior`创建的，并产生许多具有该行为的子代，然后将消息转发给这些子代。

如果停止子进程，池路由会将其从其一组路由中删除。当最后一个子节点停止时，路由本身停止。为了制造一个处理故障的弹性路由，必须监视路由的`Behavior`。

```java
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.GroupRouter;
import akka.actor.typed.javadsl.PoolRouter;
import akka.actor.typed.javadsl.Routers;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;

class Worker {
  interface Command {}

  static class DoLog implements Command {
    public final String text;

    public DoLog(String text) {
      this.text = text;
    }
  }

  static final Behavior<Command> behavior =
      Behaviors.setup(
          context -> {
            context.getLog().info("Starting worker");

            return Behaviors.receive(Command.class)
                .onMessage(
                    DoLog.class,
                    (notUsed, doLog) -> {
                      context.getLog().info("Got message {}", doLog.text);
                      return Behaviors.same();
                    })
                .build();
          });
}

        // make sure the workers are restarted if they fail
        Behavior<Worker.Command> supervisedWorker =
            Behaviors.supervise(Worker.behavior).onFailure(SupervisorStrategy.restart());
        PoolRouter<Worker.Command> pool = Routers.pool(4, supervisedWorker);
        ActorRef<Worker.Command> router = context.spawn(pool, "worker-pool");

        for (int i = 0; i < 10; i++) {
          router.tell(new Worker.DoLog("msg " + i));
        }
```

## 组路由

组路由是用`ServiceKey`创建的，它使用`Receptionist`来发现该键的可用 Actor，并将消息路由到当前已知的已注册 Actor 之一。

由于使用了`Receptionist`，这意味着组路由可以随时识别集群，并将接收集群中任何节点上注册的路由，目前没有逻辑来避免路由到不可访问的节点，请参见「[#26355](https://github.com/akka/akka/issues/26355)」。

这也意味着路由的集合最终是一致的，并且当组路由器启动时，它知道的路由集合立即是空的。当路由器集为空时，发送到路由的消息将被转发到死信。

```java
// this would likely happen elsewhere - if we create it locally we
// can just as well use a pool
ActorRef<Worker.Command> worker = context.spawn(Worker.behavior, "worker");
context.getSystem().receptionist().tell(Receptionist.register(serviceKey, worker));

GroupRouter<Worker.Command> group = Routers.group(serviceKey);
ActorRef<Worker.Command> router = context.spawn(group, "worker-group");

// note that since registration of workers goes through the receptionist there is no
// guarantee the router has seen any workers yet if we hit it directly like this and
// these messages may end up in dead letters - in a real application you would not use
// a group router like this - it is to keep the sample simple
for (int i = 0; i < 10; i++) {
  router.tell(new Worker.DoLog("msg " + i));
}
```

## 路由策略

有两种不同的策略用于选择转发消息的路由，在生成消息之前可以从路由中选择该路由：

```java
PoolRouter<Worker.Command> alternativePool = pool.withPoolSize(2).withRoundRobinRouting();
```

### Round Robin

在一组路由上旋转，确保如果有`n`个路由，那么对于通过路由发送的`n`条消息，每个 Actor 都被转发一条消息。

这是池路由的默认路由。

###  Random
通过路由发送消息时随机选择路由。

这是组路由的默认值路由。

## 路由和性能

注意，如果路由共享一个资源，那么资源将决定增加 Actor 的数量是否会实际提供更高的吞吐量或更快的答案。例如，如果路由是 CPU 绑定的 Actor，那么创建更多的路由的性能不会比执行 Actor 的线程更好。

由于路由本身是一个 Actor 并且有一个邮箱，这意味着消息按顺序路由到路由器，在那里可以并行处理（取决于调度程序中可用的线程）。在高吞吐量的用例中，顺序路由可能是瓶颈。Akka 类型没有为此提供优化的工具。


----------

**英文原文链接**：[Routers](https://doc.akka.io/docs/akka/current/typed/routers.html).




----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————