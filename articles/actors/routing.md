# 路由
## 依赖

为了使用路由（`Routing`），你需要将以下依赖添加到你的项目中：

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

## 简介
消息可以通过路由（`router`）发送，以有效地将它们路由到目标 Actor，即其路由器（`routees`）。`Router`可以在 Actor 内部或外部使用，你可以自己管理路由器，也可以使用具有配置功能的自包含路由 Actor。

根据应用程序的需要，可以使用不同的路由策略。Akka 提供了一些非常有用的路由策略。但是，正如你将在本章中看到的，也可以创建自己的。

## 一个简单的路由
下面的示例说明如何使用`Router`，并从 Actor 内部管理路由器。

```java
static final class Work implements Serializable {
  private static final long serialVersionUID = 1L;
  public final String payload;

  public Work(String payload) {
    this.payload = payload;
  }
}

static class Master extends AbstractActor {

  Router router;

  {
    List<Routee> routees = new ArrayList<Routee>();
    for (int i = 0; i < 5; i++) {
      ActorRef r = getContext().actorOf(Props.create(Worker.class));
      getContext().watch(r);
      routees.add(new ActorRefRoutee(r));
    }
    router = new Router(new RoundRobinRoutingLogic(), routees);
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            Work.class,
            message -> {
              router.route(message, getSender());
            })
        .match(
            Terminated.class,
            message -> {
              router = router.removeRoutee(message.actor());
              ActorRef r = getContext().actorOf(Props.create(Worker.class));
              getContext().watch(r);
              router = router.addRoutee(new ActorRefRoutee(r));
            })
        .build();
  }
}
```

我们创建一个`Router`，并指定它在将消息路由到路由器时应该使用`RoundRobinRoutingLogic`。

Akka 附带的路由逻辑包括：

- `akka.routing.RoundRobinRoutingLogic`
- `akka.routing.RandomRoutingLogic`
- `akka.routing.SmallestMailboxRoutingLogic`
- `akka.routing.BroadcastRoutingLogic`
- `akka.routing.ScatterGatherFirstCompletedRoutingLogic`
- `akka.routing.TailChoppingRoutingLogic`
- `akka.routing.ConsistentHashingRoutingLogic`

我们创建路由器作为普通的子 Actor 包裹在`ActorRefRoutee`之中。我们`watch`路由器，以便在它们被终止时能够替换它们。

通过路由发送消息是通过`router`方法完成的，正如上面示例中的`Work`消息一样。

`Router`是不可变的，`RoutingLogic`是线程安全的；这意味着它们也可以在 Actor 之外使用。

- **注释**：一般来说，发送到路由的任何消息都将发送到路由器，但有一个例外。特殊的「[广播消息](https://doc.akka.io/docs/akka/current/routing.html#broadcast-messages)」将发送到路由中的所有路由器。因此，当你将「[BalancingPool](https://doc.akka.io/docs/akka/current/routing.html#balancing-pool)」用于「[特殊处理的消息](https://doc.akka.io/docs/akka/current/routing.html#router-special-messages)」中描述的路由器时，不要使用广播消息。

## 路由器 Actor

路由也可以被创建为一个独立的 Actor，管理路由器本身，并从配置中加载路由逻辑和其他设置。

这种类型的路由 Actor 有两种不同的风格：

- 池（`Pool`）- 路由器创建作为子 Actor 的路由器，并在它们终止时将它们从路由中删除。
- 组（`Group`）- 路由器 Actor 在外部创建到路由的路由器，路由使用 Actor 选择将消息发送到指定路径，而不监视终止。

路由 Actor 的设置可以通过配置或编程方式定义。为了让 Actor 使用外部可配置的路由，必须使用`FromConfig`的`props`包装器来表示 Actor 接受来自配置的路由设置。这与远程部署相反，远程部署不需要这样的标记支持。如果 Actor 的属性没有包装在`FromConfig`中，那么它将忽略部署配置的路由部分。

你通过路由器 Actor 向路由发送消息，方式与普通 Actor 相同，即通过其`ActorRef`。路由 Actor 在不更改原始发件人的情况下将消息转发到其路由器。当路由器回复路由的消息时，回复将发送到原始发件人，而不是发送给路由 Actor。

- **注释**：一般来说，发送到路由的任何消息都会发送到路由器，但也有一些例外。这些信息记录在下面的「[特殊处理的消息](https://doc.akka.io/docs/akka/current/routing.html#router-special-messages)」部分中。

### 池

下面的代码和配置片段演示了如何创建将消息转发到五个`Worker`路由器的「[round-robin](https://doc.akka.io/docs/akka/current/routing.html#round-robin-router)」路由。路由器将被创建为路由的子级。

```yml
akka.actor.deployment {
  /parent/router1 {
    router = round-robin-pool
    nr-of-instances = 5
  }
}
```

```java
ActorRef router1 =
    getContext().actorOf(FromConfig.getInstance().props(Props.create(Worker.class)), "router1");
```

这里是相同的例子，但是路由器配置是以编程方式提供的，而不是从配置中提供的。

```java
ActorRef router2 =
    getContext().actorOf(new RoundRobinPool(5).props(Props.create(Worker.class)), "router2");
```

#### 远程部署的路由器

除了能够将本地 Actor 创建为路由器之外，还可以指示路由将其创建的子级部署到一组远程主机上。路由器将以循环方式部署。为了远程部署路由器，请将路由配置包装在`RemoteRouterConfig`中，并附加要部署到的节点的远程地址。远程部署要求类路径中包含`akka-remote`模块。

```java
Address[] addresses = {
  new Address("akka.tcp", "remotesys", "otherhost", 1234),
  AddressFromURIString.parse("akka.tcp://othersys@anotherhost:1234")
};
ActorRef routerRemote =
    system.actorOf(
        new RemoteRouterConfig(new RoundRobinPool(5), addresses)
            .props(Props.create(Echo.class)));
```

#### 发送者

默认情况下，当路由器发送消息时，它将「[隐式地将自己设置为发送者](https://doc.akka.io/docs/akka/current/actors.html#actors-tell-sender)」。

```java
getSender().tell("reply", getSelf());
```

然而，将路由器设置为发送者通常很有用。例如，如果要隐藏路由后面的路由器的详细信息，可能需要将路由设置为发送者。下面的代码段显示了如何将父路由设置为发送者。

```java
getSender().tell("reply", getContext().getParent());
```

#### 监督

由池路由创建的路由器将被创建为路由的子级。因此，路由也是子 Actor 的监督者。

路由 Actor 的监督策略可以配置为具有`Pool`的`supervisorStrategy`属性。如果没有配置，路由默认为“总是升级”策略。这意味着错误将被传递给路由器的监督者进行处理。路由器的监督者将决定如何处理任何错误。

注意路由的监督者会将此错误视为路由本身的错误。因此，停止或重新启动的指令将导致路由本身停止或重新启动。反过来，路由将导致其子路由器停止并重新启动。

应该提到的是，路由的重新启动行为已经被覆盖，因此重新启动时，在重新创建子 Actor 的同时，仍然会保留池中相同数量的 Actor。

这意味着，如果你没有指定路由或其父路由器的`supervisorStrategy`，则路由器中的故障将升级到路由器的父路由器，默认情况下，该路由器将重新启动所有路由器（它使用`Escalate`，并且在重新启动期间不会停止路由器）。原因是要使默认的行为添加`.withRouter`到子级的定义不会更改应用于子级的监视策略。这可能是一个效率低下的问题，你可以通过在定义路由时指定策略来避免。

设置策略的过程如下：

```java
final SupervisorStrategy strategy =
    new OneForOneStrategy(
        5,
        Duration.ofMinutes(1),
        Collections.<Class<? extends Throwable>>singletonList(Exception.class));
final ActorRef router =
    system.actorOf(
        new RoundRobinPool(5).withSupervisorStrategy(strategy).props(Props.create(Echo.class)));
```

### 组
有时，与其让路由 Actor 创建其路由器，不如单独创建路由器并将其提供给路由供其使用。你可以通过将路由器的路径传递给路由的配置来实现这一点。消息将与`ActorSelection`一起发送到这些路径，「[通配符](https://doc.akka.io/docs/akka/current/general/addressing.html#querying-the-logical-actor-hierarchy)」可以并且将产生与显式使用`ActorSelection`相同的语义。

下面的示例演示了如何通过向路由提供三个路由器 Actor 的路径字符串来创建路由。

```yml
akka.actor.deployment {
  /parent/router3 {
    router = round-robin-group
    routees.paths = ["/user/workers/w1", "/user/workers/w2", "/user/workers/w3"]
  }
}
```

```java
ActorRef router3 = getContext().actorOf(FromConfig.getInstance().props(), "router3");
```

这里是相同的例子，但是路由器配置是以编程方式提供的，而不是从配置中提供的。

```java
List<String> paths = Arrays.asList("/user/workers/w1", "/user/workers/w2", "/user/workers/w3");
ActorRef router4 = getContext().actorOf(new RoundRobinGroup(paths).props(), "router4");
```

路由器 Actor 是从路由外部创建的：

```java
system.actorOf(Props.create(Workers.class), "workers");
```

```java
static class Workers extends AbstractActor {
  @Override
  public void preStart() {
    getContext().actorOf(Props.create(Worker.class), "w1");
    getContext().actorOf(Props.create(Worker.class), "w2");
    getContext().actorOf(Props.create(Worker.class), "w3");
  }
  // ...
```

路径可能包含远程主机上运行的 Actor 的协议和地址信息。远程处理要求类路径中包含`akka-remote`模块。

```
akka.actor.deployment {
  /parent/remoteGroup {
    router = round-robin-group
    routees.paths = [
      "akka.tcp://app@10.0.0.1:2552/user/workers/w1",
      "akka.tcp://app@10.0.0.2:2552/user/workers/w1",
      "akka.tcp://app@10.0.0.3:2552/user/workers/w1"]
  }
}
```



----------

**英文原文链接**：[Routing](https://doc.akka.io/docs/akka/current/routing.html).


----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————