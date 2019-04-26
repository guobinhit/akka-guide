# 集群中的分布式发布订阅
## 依赖

为了使用分布式发布订阅（`Distributed Publish Subscribe`），你需要将以下依赖添加到你的项目中：

```xml
<!-- Maven -->
<dependency>
  <groupId>com.typesafe.akka</groupId>
  <artifactId>akka-cluster-tools_2.12</artifactId>
  <version>2.5.22</version>
</dependency>

<!-- Gradle -->
dependencies {
  compile group: 'com.typesafe.akka', name: 'akka-cluster-tools_2.12', version: '2.5.22'
}

<!-- sbt -->
libraryDependencies += "com.typesafe.akka" %% "akka-cluster-tools" % "2.5.22"
```

## 简介

在不知道 Actor 正在哪个节点运行的情况下，如何向其发送消息？

如何将消息发送给集群中对命名主题感兴趣的所有 Actor？

此模式提供了一个中介 Actor `akka.cluster.pubsub.DistributedPubSubMediator`，它管理 Actor 引用的注册表，并将条目复制到所有集群节点或标记有特定角色的一组节点中的同级 Actor。

`DistributedPubSubMediator` Actor 支持在集群中的所有节点或具有指定角色的所有节点上启动。中介程序可以以`DistributedPubSub`扩展启动，也可以作为普通的 Actor 启动。

注册表最终是一致的，即更改在其他节点上不立即可见，但通常在几秒钟后将其完全复制到所有其他节点。更改只在注册表的自己部分执行，并且这些更改都是版本控制的。增量（`Deltas`）以可扩展的方式通过`gossip`协议传播到其他节点。

状态为「[WeaklyUp](https://doc.akka.io/docs/akka/current/cluster-usage.html#weakly-up)」的集群成员将参与分布式发布订阅，即如果发布服务器和订阅服务器位于网络分区的同一侧，则状态为`WeaklyUp`的节点上的订阅服务器将接收已发布的消息。

你可以通过任何节点上的中介（`mediator`）向任何其他节点上注册的 Actor 发送消息。

下面的「[Publish](https://doc.akka.io/docs/akka/current/distributed-pub-sub.html#distributed-pub-sub-publish)」和「[Send](https://doc.akka.io/docs/akka/current/distributed-pub-sub.html#distributed-pub-sub-send)」部分解释了两种不同的消息传递模式。

## 发布

这是真正的`pub/sub`模式。这种模式的典型用法是即时消息应用程序中的聊天室功能。

Actor 注册到命名主题。这将在每个节点上启用许多订阅服务器。消息将传递给主题的所有订户。

为了提高效率，消息在每个节点（具有匹配主题）上仅通过线路（`wire`）发送一次，然后传递给本地主题表示的所有订阅者。

你可以使用`DistributedPubSubMediator.Subscribe`将 Actor 注册到本地中介。成功的`Subscribe`和`Unsubscribe`通过`DistributedPubSubMediator.SubscribeAck`和`DistributedPubSubMediator.UnsubscribeAck`确认。确认意味着订阅已注册，但在复制到其他节点之前，它仍然需要一些时间。

你可以通过发送`DistributedPubSubMediator.Publish`将消息发布到本地中介。

当中介 Actor 停止时，Actor 将自动从注册表中删除，或者你也可以使用`DistributedPubSubMediator.Unsubscribe`显式删除条目。

订阅者 Actor 的示例：

```java
static class Subscriber extends AbstractActor {
  LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  public Subscriber() {
    ActorRef mediator = DistributedPubSub.get(getContext().system()).mediator();
    // subscribe to the topic named "content"
    mediator.tell(new DistributedPubSubMediator.Subscribe("content", getSelf()), getSelf());
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(String.class, msg -> log.info("Got: {}", msg))
        .match(DistributedPubSubMediator.SubscribeAck.class, msg -> log.info("subscribed"))
        .build();
  }
}
```

订阅者 Actor 可以在集群中的多个节点上启动，所有节点都将接收发布到`content`主题的消息。

```java
system.actorOf(Props.create(Subscriber.class), "subscriber1");
// another node
system.actorOf(Props.create(Subscriber.class), "subscriber2");
system.actorOf(Props.create(Subscriber.class), "subscriber3");
```

发布到此`content`主题的简单 Actor：

```java
static class Publisher extends AbstractActor {

  // activate the extension
  ActorRef mediator = DistributedPubSub.get(getContext().system()).mediator();

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            String.class,
            in -> {
              String out = in.toUpperCase();
              mediator.tell(new DistributedPubSubMediator.Publish("content", out), getSelf());
            })
        .build();
  }
}
```

它可以从群集中的任何位置向主题发布消息：

```java
// somewhere else
ActorRef publisher = system.actorOf(Props.create(Publisher.class), "publisher");
// after a while the subscriptions are replicated
publisher.tell("hello", null);
```

### 主题组

Actor 还可以以`group` ID 订阅命名主题。如果订阅`group` ID，则通过提供的`RoutingLogic`（默认随机）将发布到主题的每条消息（`sendOneMessageToEachGroup`标志设置为`true`）传递给每个订阅组中的一个 Actor。

如果所有订阅的 Actor 都具有相同的组 ID，那么这就像`Send`一样工作，并且每个消息只传递到一个订阅者。

如果所有订阅的 Actor 都有不同的组名，那么这就像正常`Publish`一样工作，并且每个消息都广播给所有订阅者。

- **注释**：如果使用组 ID，它将是主题标识符的一部分。使用`sendOneMessageToEachGroup=false`发布的消息将不会传递给使用组 ID 订阅的订阅者。使用`sendOneMessageToEachGroup=true`发布的消息将不会传递给没有使用组 ID 订阅的订阅者。

## 发送

这是一种点对点（`point-to-point`）模式，其中每个消息都传递到一个目的地，但你仍然不必知道目的地在哪里。这种模式的典型用法是在即时消息应用程序中与另一个用户进行私人聊天。它还可以用于将任务分发给已注册的工作者，如集群感知路由器，其中路由器可以动态注册自己。

如果注册表中存在匹配路径，则消息将传递给一个收件人。如果多个条目与路径匹配，因为它已在多个节点上注册，则消息将通过提供的路由逻辑（默认随机）发送到一个目标。消息的发送者可以指定首选本地路径，即消息被发送到与所使用的中介 Actor 相同的本地 Actor 系统中的 Actor（如果存在），否则路由到任何其他匹配条目。

你可以使用`DistributedPubSubMediator.Put`将 Actor 注册到本地中介（`mediator`）。`Put`中的`ActorRef`必须与中介属于同一个本地 Actor 系统。没有地址信息的路径是发送消息的关键（`key`）。在每个节点上，给定路径只能有一个 Actor，因为该路径在一个本地 Actor 系统中是唯一的。

使用目标 Actor 的路径（不含地址信息），你可以通过`DistributedPubSubMediator.Send`将消息发送到本地中介。

Actor 在终止时会自动从注册表中删除，或者你也可以使用`DistributedPubSubMediator.Remove`显式删除条目。

目标 Actor 的示例：

```java
static class Destination extends AbstractActor {
  LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  public Destination() {
    ActorRef mediator = DistributedPubSub.get(getContext().system()).mediator();
    // register to the path
    mediator.tell(new DistributedPubSubMediator.Put(getSelf()), getSelf());
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder().match(String.class, msg -> log.info("Got: {}", msg)).build();
  }
}
```

目标 Actor 可以在集群中的多个节点上启动，并且所有节点都将接收发送到路径的消息（没有地址信息）。

```java
system.actorOf(Props.create(Destination.class), "destination");
// another node
system.actorOf(Props.create(Destination.class), "destination");
```

发送到路径的简单 Actor：

```java
static class Sender extends AbstractActor {

  // activate the extension
  ActorRef mediator = DistributedPubSub.get(getContext().system()).mediator();

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            String.class,
            in -> {
              String out = in.toUpperCase();
              boolean localAffinity = true;
              mediator.tell(
                  new DistributedPubSubMediator.Send("/user/destination", out, localAffinity),
                  getSelf());
            })
        .build();
  }
}
```

它可以从群集中的任何位置向路径发送消息：

```java
// somewhere else
ActorRef sender = system.actorOf(Props.create(Publisher.class), "sender");
// after a while the destinations are replicated
sender.tell("hello", null);
```

也可以将消息广播给已向`Put`注册的 Actor。将`DistributedPubSubMediator.SendToAll`l消息发送到本地中介，然后将包装好的消息传递到具有匹配路径的所有收件人。具有相同路径且没有地址信息的 Actor 可以在不同的节点上注册。在每个节点上只能有一个这样的 Actor，因为路径在一个本地 Actor 系统中是唯一的。

此模式的典型用法是将消息广播到具有相同路径的所有副本，例如，在所有执行相同操作的不同节点上的 3 个 Actor，以实现冗余。你还可以选择指定一个属性（`allButSelf`），决定是否应将消息发送到自节点上的匹配路径。

## DistributedPubSub 扩展

在上面的示例中，使用`akka.cluster.pubsub.DistributedPubSub`扩展启动和访问中介。这在大多数情况下都是很方便和完美的，但是也可以将中间 Actor 作为普通的 Actor 来启动，并且你可以同时拥有几个不同的中介，以便能够将大量的`actors/topics`分配给不同的中介。例如，你可能希望对不同的中介使用不同的集群角色。

可以使用以下属性配置`DistributedPubSub`扩展：

```yml
# Settings for the DistributedPubSub extension
akka.cluster.pub-sub {
  # Actor name of the mediator actor, /system/distributedPubSubMediator
  name = distributedPubSubMediator

  # Start the mediator on members tagged with this role.
  # All members are used if undefined or empty.
  role = ""

  # The routing logic to use for 'Send'
  # Possible values: random, round-robin, broadcast
  routing-logic = random

  # How often the DistributedPubSubMediator should send out gossip information
  gossip-interval = 1s

  # Removed entries are pruned after this duration
  removed-time-to-live = 120s

  # Maximum number of elements to transfer in one message when synchronizing the registries.
  # Next chunk will be transferred in next round of gossip.
  max-delta-elements = 3000

  # When a message is published to a topic with no subscribers send it to the dead letters.
  send-to-dead-letters-when-no-subscribers = on
  
  # The id of the dispatcher to use for DistributedPubSubMediator actors. 
  # If not specified default dispatcher is used.
  # If specified you need to define the settings of the actual dispatcher.
  use-dispatcher = ""
}
```

建议在 Actor 系统启动时通过在`akka.extensions`配置属性中定义它来加载扩展。否则，它将在第一次使用时激活，然后需要一段时间才能就位（`populated.`）。

```java
akka.extensions = ["akka.cluster.pubsub.DistributedPubSub"]
```

## 传递保证

与 Akka 中的「[ Message Delivery Reliability](https://doc.akka.io/docs/akka/current/general/message-delivery-reliability.html)」一样，该模式下的消息传递保证为至多一次传递（`at-most-once delivery`）。换言之，信息可能会丢失。

如果你需要至少一次的传递保证，我们建议与「[Kafka Akka Streams](https://doc.akka.io/docs/akka-stream-kafka/current/home.html)」集成。


----------

**英文原文链接**：[Distributed Publish Subscribe in Cluster](https://doc.akka.io/docs/akka/current/distributed-pub-sub.html).



----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————