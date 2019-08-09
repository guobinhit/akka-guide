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

## 路由的使用方法
在本节中，我们将描述如何创建不同类型的路由 Actor。

本节中的路由 Actor 是从名为`parent`的顶级 Actor 中创建的。请注意，配置中的部署路径以`/parent/`开头，后跟路由 Actor 的名称。

```java
system.actorOf(Props.create(Parent.class), "parent");
```

### RoundRobinPool 和 RoundRobinGroup

以「[round-robin](https://en.wikipedia.org/wiki/Round-robin)」方式路由到它的路由器。

在配置文件中定义`RoundRobinPool`：

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

在代码中定义`RoundRobinPool`：

```java
ActorRef router2 =
    getContext().actorOf(new RoundRobinPool(5).props(Props.create(Worker.class)), "router2");
```

在配置文件中定义`RoundRobinGroup`：

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

在代码中定义`RoundRobinGroup`：

```java
List<String> paths = Arrays.asList("/user/workers/w1", "/user/workers/w2", "/user/workers/w3");
ActorRef router4 = getContext().actorOf(new RoundRobinGroup(paths).props(), "router4");
```

### RandomPool 和 RandomGroup

此路由类型为每条消息随机选择的一个路由器类型。

在配置文件中定义`RandomPool`：

```yml
akka.actor.deployment {
  /parent/router5 {
    router = random-pool
    nr-of-instances = 5
  }
}
```

```java
ActorRef router5 =
    getContext().actorOf(FromConfig.getInstance().props(Props.create(Worker.class)), "router5");
```

在代码中定义`RandomPool`：

```java
ActorRef router6 =
    getContext().actorOf(new RandomPool(5).props(Props.create(Worker.class)), "router6");
```

在配置文件中定义`RandomGroup`：

```yml
akka.actor.deployment {
  /parent/router7 {
    router = random-group
    routees.paths = ["/user/workers/w1", "/user/workers/w2", "/user/workers/w3"]
  }
}
```

```java
ActorRef router7 = getContext().actorOf(FromConfig.getInstance().props(), "router7");
```

在代码中定义`RandomGroup`：

```java
List<String> paths = Arrays.asList("/user/workers/w1", "/user/workers/w2", "/user/workers/w3");
ActorRef router8 = getContext().actorOf(new RandomGroup(paths).props(), "router8");
```

### BalancingPool

一种将工作从繁忙的路由器重新分配到空闲的路由器的路由。所有路由共享同一个邮箱。

- **注释 1**：`BalancingPool`的特性是，它的路由器没有真正不同的身份，它们有不同的名称，但在大多数情况下，与它们交互不会以正常的 Actor 结束。因此，你不能将其用于需要状态保留在路由中的工作流，在这种情况下，你必须在消息中包含整个状态。使用「[SmallestMailboxPool](https://doc.akka.io/docs/akka/current/routing.html#smallestmailboxpool)」，你可以拥有一个垂直扩展的服务，该服务可以在回复原始客户端之前以状态方式与后端的其他服务交互。另一个优点是，它不像`BalancingPool`那样对消息队列的实现进行限制。

- **注释 2**：在路由使用「[BalancingPool](https://doc.akka.io/docs/akka/current/routing.html#balancing-pool)」时，不要使用「[广播消息](https://doc.akka.io/docs/akka/current/routing.html#broadcast-messages)」，详情见「[特殊处理的消息](https://doc.akka.io/docs/akka/current/routing.html#router-special-messages)」中的描述。

在配置文件中定义`BalancingPool`：

```yml
akka.actor.deployment {
  /parent/router9 {
    router = balancing-pool
    nr-of-instances = 5
  }
}
```

```java
ActorRef router9 =
    getContext().actorOf(FromConfig.getInstance().props(Props.create(Worker.class)), "router9");
```

在代码中定义`BalancingPool`：

```java
ActorRef router10 =
    getContext().actorOf(new BalancingPool(5).props(Props.create(Worker.class)), "router10");
```

添加配置的平衡调度器，该平衡调度器由池使用，可配置在路由部署配置的`pool-dispatcher`部分。

```yml
akka.actor.deployment {
  /parent/router9b {
    router = balancing-pool
    nr-of-instances = 5
    pool-dispatcher {
      attempt-teamwork = off
    }
  }
}
```

`BalancingPool`自动为其路由使用一个特殊的`BalancingDispatcher`，并且会忽略在路由`Props`对象上设置的任何调度程序。这是为了通过所有路由器共享同一个邮箱来实现平衡语义。

虽然无法更改路由使用的调度程序，但可以对使用的执行器进行微调。默认情况下，将使用`fork-join-dispatcher`，并可以按照「[调度器](https://github.com/guobinhit/akka-guide/blob/master/articles/actors/dispatchers.md)」中的说明进行配置。在期望路由器执行阻塞操作的情况下，用一个`thread-pool-executor`替换它可能很有用，该执行器显式地提示分配的线程数：

```java
akka.actor.deployment {
  /parent/router10b {
    router = balancing-pool
    nr-of-instances = 5
    pool-dispatcher {
      executor = "thread-pool-executor"

      # allocate exactly 5 threads for this pool
      thread-pool-executor {
        core-pool-size-min = 5
        core-pool-size-max = 5
      }
    }
  }
}
```

在默认的无界邮箱不太适合的情况下，也可以更改平衡调度程序使用的`mailbox`。无论是否需要管理每个消息的优先级，都可能出现这样的场景。因此，可以实现优先级邮箱并配置调度程序：

```yml
akka.actor.deployment {
  /parent/router10c {
    router = balancing-pool
    nr-of-instances = 5
    pool-dispatcher {
      mailbox = myapp.myprioritymailbox
    }
  }
}
```

- **注释**：请记住，`BalancingDispatcher`需要一个消息队列，该队列对于多个并发消费者必须是线程安全的。因此，对于支持自定义邮箱的消息队列，这种调度程序必须实现`akka.dispatch.MultipleConsumerSemantics`。请参阅有关如何在「[邮箱](https://github.com/guobinhit/akka-guide/blob/master/articles/actors/mailboxes.md)」中实现自定义邮箱的详细信息。

特别地，在`BalancingPool`没有`Group`变量。

### SmallestMailboxPool

它是一个试图发送到邮箱中邮件最少的非挂起子路由器的路由。选择顺序如下：

- 选择邮箱为空的任何空闲路由（不处理邮件）
- 选择任何邮箱为空的路由
- 选择邮箱中挂起邮件最少的路由
- 选择任何远程路由器，远程 Actor 被认为是最低优先级，因为它们的邮箱大小是未知的

在配置文件中定义`SmallestMailboxPool`：

```yml
akka.actor.deployment {
  /parent/router11 {
    router = smallest-mailbox-pool
    nr-of-instances = 5
  }
}
```

```java
ActorRef router11 =
    getContext()
        .actorOf(FromConfig.getInstance().props(Props.create(Worker.class)), "router11");
```

在代码中定义`SmallestMailboxPool`：

```java
ActorRef router12 =
    getContext()
        .actorOf(new SmallestMailboxPool(5).props(Props.create(Worker.class)), "router12");
```

在`SmallestMailboxPool`中没有`Group`变量，因为邮箱的大小和 Actor 的内部调度状态实际上在路由路径中不可用。

### BroadcastPool 和 BroadcastGroup

广播路由（`broadcast router`）把它接收到的信息转发给它的所有路由器。

在配置文件中定义`BroadcastPool`：

```yml
akka.actor.deployment {
  /parent/router13 {
    router = broadcast-pool
    nr-of-instances = 5
  }
}
```

```java
ActorRef router13 =
    getContext()
        .actorOf(FromConfig.getInstance().props(Props.create(Worker.class)), "router13");
```

在代码中定义`BroadcastPool`：

```java
ActorRef router14 =
    getContext().actorOf(new BroadcastPool(5).props(Props.create(Worker.class)), "router14");
```

在配置文件中定义`BroadcastGroup`：

```yml
akka.actor.deployment {
  /parent/router15 {
    router = broadcast-group
    routees.paths = ["/user/workers/w1", "/user/workers/w2", "/user/workers/w3"]
  }
}
```

```java
ActorRef router15 = getContext().actorOf(FromConfig.getInstance().props(), "router15");
```

在代码中定义`BroadcastGroup`：

```java
List<String> paths = Arrays.asList("/user/workers/w1", "/user/workers/w2", "/user/workers/w3");
ActorRef router16 = getContext().actorOf(new BroadcastGroup(paths).props(), "router16");
```

- **注释**：广播路由总是向它们的路由器广播每一条消息。如果你不想广播每一条消息，那么你可以使用非广播路由，并根据需要使用「[广播消息](https://doc.akka.io/docs/akka/current/routing.html#broadcast-messages)」。

### ScatterGatherFirstCompletedPool 和 ScatterGatherFirstCompletedGroup

`ScatterGatherFirstCompletedRouter`将消息发送到其所有路由器，然后等待它得到的第一个回复。此结果将被发送回原始发件人，而其他答复将被丢弃。

它期望在配置的持续时间内至少有一个回复，否则它将以`akka.actor.Status.Failure`中的`akka.pattern.AskTimeoutException`进行回复。

在配置文件中定义`ScatterGatherFirstCompletedPool`：

```yml
akka.actor.deployment {
  /parent/router17 {
    router = scatter-gather-pool
    nr-of-instances = 5
    within = 10 seconds
  }
}
```

```java
ActorRef router17 =
    getContext()
        .actorOf(FromConfig.getInstance().props(Props.create(Worker.class)), "router17");
```

在代码中定义`ScatterGatherFirstCompletedPool`：

```java
Duration within = Duration.ofSeconds(10);
ActorRef router18 =
    getContext()
        .actorOf(
            new ScatterGatherFirstCompletedPool(5, within).props(Props.create(Worker.class)),
            "router18");
```

在配置文件中定义`ScatterGatherFirstCompletedGroup`：

```yml
akka.actor.deployment {
  /parent/router19 {
    router = scatter-gather-group
    routees.paths = ["/user/workers/w1", "/user/workers/w2", "/user/workers/w3"]
    within = 10 seconds
  }
}
```

```java
ActorRef router19 = getContext().actorOf(FromConfig.getInstance().props(), "router19");
```

在代码中定义`ScatterGatherFirstCompletedGroup`：

```java
List<String> paths = Arrays.asList("/user/workers/w1", "/user/workers/w2", "/user/workers/w3");
Duration within2 = Duration.ofSeconds(10);
ActorRef router20 =
    getContext()
        .actorOf(new ScatterGatherFirstCompletedGroup(paths, within2).props(), "router20");
```

### TailChoppingPool 和 TailChoppingGroup

`TailChoppingRouter`首先将消息发送到一个随机选择的路由器，然后在一个小延迟后发送到第二个路由器（从剩余的路由器随机选择）等。它等待第一个回复，然后返回并转发给原始发送者，而其他答复将被丢弃。

此路由器的目标是通过对多个路由器执行冗余查询来减少延迟，前提是其他 Actor 之一的响应速度可能仍然比初始 Actor 快。

彼得·贝利斯在一篇博文中很好地描述了这种优化：「[通过多余的工作来加速分布式查询](http://www.bailis.org/blog/doing-redundant-work-to-speed-up-distributed-queries/)」。

在配置文件中定义`TailChoppingPool`：

```yml
akka.actor.deployment {
  /parent/router21 {
    router = tail-chopping-pool
    nr-of-instances = 5
    within = 10 seconds
    tail-chopping-router.interval = 20 milliseconds
  }
}
```

```java
ActorRef router21 =
    getContext()
        .actorOf(FromConfig.getInstance().props(Props.create(Worker.class)), "router21");
```

在代码中定义`TailChoppingPool`：

```java
Duration within3 = Duration.ofSeconds(10);
Duration interval = Duration.ofMillis(20);
ActorRef router22 =
    getContext()
        .actorOf(
            new TailChoppingPool(5, within3, interval).props(Props.create(Worker.class)),
            "router22");
```

在配置文件中定义`TailChoppingGroup`：

```yml
akka.actor.deployment {
  /parent/router23 {
    router = tail-chopping-group
    routees.paths = ["/user/workers/w1", "/user/workers/w2", "/user/workers/w3"]
    within = 10 seconds
    tail-chopping-router.interval = 20 milliseconds
  }
}
```

```java
ActorRef router23 = getContext().actorOf(FromConfig.getInstance().props(), "router23");
```

在代码中定义`TailChoppingGroup`：

```java
List<String> paths = Arrays.asList("/user/workers/w1", "/user/workers/w2", "/user/workers/w3");
Duration within4 = Duration.ofSeconds(10);
Duration interval2 = Duration.ofMillis(20);
ActorRef router24 =
    getContext().actorOf(new TailChoppingGroup(paths, within4, interval2).props(), "router24");
```

### ConsistentHashingPool 和 ConsistentHashingGroup

`ConsistentHashingPool`使用「[一致性哈希](https://en.wikipedia.org/wiki/Consistent_hashing)」根据发送的消息选择路由。「[这篇文章](http://www.tom-e-white.com/2007/11/consistent-hashing.html)」详细描述了如何实现一致性哈希。

有 3 种方法定义一致性哈希键要使用的数据。

- 你可以使用路由的`withHashMapper`定义将传入消息映射到其一致的哈希键。这使发送方的决策透明。
- 消息可以实现`akka.routing.ConsistentHashingRouter.ConsistentHashable`。键是消息的一部分，它便于与消息一起定义。
- 消息可以包装到`akka.routing.ConsistentHashingRouter.ConsistentHashableEnvelope`，以定义用于一致性哈希键的数据。发送者知道要使用的键。

这些定义一致性哈希键的方法可以同时用于一个路由。首先尝试使用`withHashMapper`。

代码示例：

```java
static class Cache extends AbstractActor {
  Map<String, String> cache = new HashMap<String, String>();

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            Entry.class,
            entry -> {
              cache.put(entry.key, entry.value);
            })
        .match(
            Get.class,
            get -> {
              Object value = cache.get(get.key);
              getSender().tell(value == null ? NOT_FOUND : value, getSelf());
            })
        .match(
            Evict.class,
            evict -> {
              cache.remove(evict.key);
            })
        .build();
  }
}

static final class Evict implements Serializable {
  private static final long serialVersionUID = 1L;
  public final String key;

  public Evict(String key) {
    this.key = key;
  }
}

static final class Get implements Serializable, ConsistentHashable {
  private static final long serialVersionUID = 1L;
  public final String key;

  public Get(String key) {
    this.key = key;
  }

  public Object consistentHashKey() {
    return key;
  }
}

static final class Entry implements Serializable {
  private static final long serialVersionUID = 1L;
  public final String key;
  public final String value;

  public Entry(String key, String value) {
    this.key = key;
    this.value = value;
  }
}

static final String NOT_FOUND = "NOT_FOUND";
```

```java
final ConsistentHashMapper hashMapper =
    new ConsistentHashMapper() {
      @Override
      public Object hashKey(Object message) {
        if (message instanceof Evict) {
          return ((Evict) message).key;
        } else {
          return null;
        }
      }
    };

ActorRef cache =
    system.actorOf(
        new ConsistentHashingPool(10)
            .withHashMapper(hashMapper)
            .props(Props.create(Cache.class)),
        "cache");

cache.tell(new ConsistentHashableEnvelope(new Entry("hello", "HELLO"), "hello"), getRef());
cache.tell(new ConsistentHashableEnvelope(new Entry("hi", "HI"), "hi"), getRef());

cache.tell(new Get("hello"), getRef());
expectMsgEquals("HELLO");

cache.tell(new Get("hi"), getRef());
expectMsgEquals("HI");

cache.tell(new Evict("hi"), getRef());
cache.tell(new Get("hi"), getRef());
expectMsgEquals(NOT_FOUND);
```

在上面的示例中，你可以看到`Get`消息实现`ConsistentHashable`本身，而`Entry`消息包装在`ConsistentHashableEnvelope`中。`Evict`消息由`hashMapping`部分函数处理。

在配置文件中定义`ConsistentHashingPool`：

```yml
akka.actor.deployment {
  /parent/router25 {
    router = consistent-hashing-pool
    nr-of-instances = 5
    virtual-nodes-factor = 10
  }
}
```

```java
ActorRef router25 =
    getContext()
        .actorOf(FromConfig.getInstance().props(Props.create(Worker.class)), "router25");
```

在代码中定义`ConsistentHashingPool`：

```java
ActorRef router26 =
    getContext()
        .actorOf(new ConsistentHashingPool(5).props(Props.create(Worker.class)), "router26");
```

在配置文件中定义`ConsistentHashingGroup`：

```yml
akka.actor.deployment {
  /parent/router27 {
    router = consistent-hashing-group
    routees.paths = ["/user/workers/w1", "/user/workers/w2", "/user/workers/w3"]
    virtual-nodes-factor = 10
  }
}
```

```java
ActorRef router27 = getContext().actorOf(FromConfig.getInstance().props(), "router27");
```

在代码中定义`ConsistentHashingGroup`：

```java
List<String> paths = Arrays.asList("/user/workers/w1", "/user/workers/w2", "/user/workers/w3");
ActorRef router28 = getContext().actorOf(new ConsistentHashingGroup(paths).props(), "router28");
```

`virtual-nodes-factor`是在一致性哈希节点环中使用的每个路由器的虚拟节点数，以使分布更均匀。


### Specially Handled Messages



----------

**英文原文链接**：[Routing](https://doc.akka.io/docs/akka/current/routing.html).


----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————