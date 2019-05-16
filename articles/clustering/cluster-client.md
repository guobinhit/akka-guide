# 集群客户端
## 依赖

为了使用集群客户端（`Cluster Client`），你需要将以下依赖添加到你的项目中：

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

不属于集群的 Actor 系统可以通过「[ClusterClient](https://doc.akka.io/japi/akka/2.5/?akka/cluster/client/ClusterClient.html)」与集群中的某个 Actor 通信，客户端可以在属于另一个集群的`ActorSystem`中运行。它只需要知道一个（或多个）节点的位置，用作初始接触点。它将与集群中的某个「[ClusterReceptionist](https://doc.akka.io/japi/akka/2.5/?akka/cluster/client/ClusterReceptionist.html)」建立连接。它将监视与接待员（`receptionist`）的连接，并在连接中断时建立新的连接。在寻找新的接待员时，它使用从以前的集群中检索到的新的联络点，或定期更新的联络点，即不一定是最初的联络点。

使用`ClusterClient`从外部与集群进行通信，要求系统与客户端既可以连接，也可以通过 Akka 远程连接到集群中的所有节点和接待员。这就产生了紧密耦合，因为客户端和集群系统可能需要具有相同版本的 Akka、库、消息类、序列化程序，甚至可能是 JVM。在许多情况下，使用更明确和解耦的协议（如「[HTTP](https://doc.akka.io/docs/akka-http/current/index.html)」或「[gRPC](https://developer.lightbend.com/docs/akka-grpc/current/)」）是更好的解决方案。

此外，由于 Akka 远程处理（`Remoting`）主要设计为 Akka 群集的协议，因此没有明确的资源管理，当使用了`ClusterClient`时，它将导致与群集的连接，直到`ActorSystem`停止（与其他类型的网络客户端不同）。

当向同一集群中运行的 Actor 发送消息时，不应使用`ClusterClient`。对于属于同一集群的 Actor，集群中的「[分布式发布订阅](https://doc.akka.io/docs/akka/current/distributed-pub-sub.html)」以更高效的方式提供与`ClusterClient`类似的功能。

使用集群客户端时，连接系统必须将其`akka.actor.provider`设置为`remote`或`cluster`。

接待员（`receptionist`）应该在集群中的所有节点或具有指定角色的所有节点上启动。接待员可以从`ClusterReceptionist`扩展启动，也可以作为普通 Actor 启动。

你可以通过`ClusterClient`使用`ClusterReceptionist`，将消息发送给集群中注册在`DistributedPubSubMediator`中的任何 Actor。「[ClusterClientReceptionist](https://doc.akka.io/japi/akka/2.5/?akka/cluster/client/ClusterClientReceptionist.html)」提供了应该可以从客户端访问 Actor 的注册方法。消息包装在`ClusterClient.Send`、`ClusterClient.SendToAll`或`ClusterClient.Publish`中。

`ClusterClient`和`ClusterClientReceptionist`都会发出可订阅的事件。`ClusterClient`发送从`ClusterClientReceptionist`处收到联系点（`contact points`）列表相关的通知。此列表的一个用途可能是让客户端记录其联系点。然后，重新启动的客户端可以使用此信息取代任何以前配置的联系点。

`ClusterClientReceptionist`发送从`ClusterClient`处收到联系点的通知。此通知使包含接待员的服务器能够了解所连接的客户端。

1. `ClusterClient.Send`：如果存在匹配路径，则消息将传递给一个收件人。如果多个条目与路径匹配，则消息将被传递到一个随机目标。消息的发送者可以指定首选本地路径，即消息发送到与所用的接待员 Actor 处于相同本地 Actor 系统中的 Actor（如果存在），否则随机发送到任何其他匹配条目。

2. `ClusterClient.SendToAll`：消息将传递给具有匹配路径的所有收件人。

3. `ClusterClient.Publish`：消息将传递给所有已注册为命名主题订阅者的收件人 Actor。

来自目标 Actor 的响应消息通过接待员进行隧道化（`tunneled`），以避免从其他集群节点到客户端的入站连接（`inbound connections`）：

- 目标 Actor 看到的「[getSender()](https://doc.akka.io/japi/akka/2.5/?akka/actor/Actor.html)」不是客户端本身，而是接待员
- 从目的地发回并由客户端看到的响应消息的「[getSender()](https://doc.akka.io/japi/akka/2.5/?akka/actor/Actor.html)」是`deadLetters`

因为客户端通常应该通过`ClusterClient`发送后续消息。如果客户端应该直接与集群中的 Actor 通信，那么可以在回复消息中传递原始发送者。

当建立到接待员的连接时，`ClusterClient`将缓冲消息，并在建立连接时发送它们。如果缓冲区已满，则当通过客户端发送新消息时，`ClusterClient`将删除旧消息。缓冲区的大小是可配置的，也可以通过使用`0`大小的缓冲区来禁用它。

值得注意的是，由于这些 Actor 的分布式特性，消息总可能丢失。一如既往，额外的逻辑应该在目标（确认）和客户端（重试）Actor 中实现，以确保至少一次的消息传递。

## 一个示例

在集群节点上，首先启动接待员。注意，建议在 Actor 系统启动时加载扩展，方法是在`akka.extensions`配置属性中定义它：

```
akka.extensions = ["akka.cluster.client.ClusterClientReceptionist"]
```

接下来，注册对客户端可用的 Actor。

```java
ActorRef serviceA = system.actorOf(Props.create(Service.class), "serviceA");
ClusterClientReceptionist.get(system).registerService(serviceA);

ActorRef serviceB = system.actorOf(Props.create(Service.class), "serviceB");
ClusterClientReceptionist.get(system).registerService(serviceB);
```

在客户端，你创建了`ClusterClient` Actor，并将其用作网关，用于向集群中某个位置由其路径（不含地址信息）标识的 Actor 发送消息。

```java
final ActorRef c =
    system.actorOf(
        ClusterClient.props(
            ClusterClientSettings.create(system).withInitialContacts(initialContacts())),
        "client");
c.tell(new ClusterClient.Send("/user/serviceA", "hello", true), ActorRef.noSender());
c.tell(new ClusterClient.SendToAll("/user/serviceB", "hi"), ActorRef.noSender());
```

`initialContacts`参数是一个`Set<ActorPath>`，可以这样创建：

```java
Set<ActorPath> initialContacts() {
  return new HashSet<ActorPath>(
      Arrays.asList(
          ActorPaths.fromString("akka.tcp://OtherSys@host1:2552/system/receptionist"),
          ActorPaths.fromString("akka.tcp://OtherSys@host2:2552/system/receptionist")));
}
```

你可能会在配置或系统属性中定义初始连接点（`contact points`）的地址信息，另请参见「[配置](https://doc.akka.io/docs/akka/current/cluster-client.html#cluster-client-config)」。

在「[Distributed workers with Akka and Java](https://github.com/typesafehub/activator-akka-distributed-workers-java)」指南中，有一个更全面的示例。

## ClusterClientReceptionist 扩展

在上面的示例中，使用`akka.cluster.client.ClusterClientReceptionist`扩展启动和访问接待员。这在大多数情况下是方便和完美的，但是可以知道，`akka.cluster.client.ClusterReceptionist`是一个普通的 Actor，你可以同时拥有几个不同的接待员，服务不同类型的客户端。

注意，「[ClusterClientReceptionist](https://doc.akka.io/japi/akka/2.5/?akka/cluster/client/ClusterClientReceptionist.html)」使用「[DistributedPubSub](https://doc.akka.io/japi/akka/2.5/?akka/cluster/pubsub/DistributedPubSub.html)」扩展，这在「[集群中的分布式发布订阅](https://doc.akka.io/docs/akka/current/distributed-pub-sub.html)」中进行了描述。

建议在 Actor 系统启动时加载扩展，方法是在`akka.extensions`配置属性中定义它：

```
akka.extensions = ["akka.cluster.client.ClusterClientReceptionist"]
```

## 事件

如前所述，`ClusterClient`和`ClusterClientReceptionist`都会发出可订阅的事件。下面的代码片段声明了一个 Actor，该 Actor 将在连接点（可用接待员的地址）可用时接收通知。代码说明订阅事件和接收`ClusterClient`初始状态。

```java
public static class ClientListener extends AbstractActor {
  private final ActorRef targetClient;
  private final Set<ActorPath> contactPoints = new HashSet<>();

  public ClientListener(ActorRef targetClient) {
    this.targetClient = targetClient;
  }

  @Override
  public void preStart() {
    targetClient.tell(SubscribeContactPoints.getInstance(), sender());
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            ContactPoints.class,
            msg -> {
              contactPoints.addAll(msg.getContactPoints());
              // Now do something with an up-to-date "contactPoints"
            })
        .match(
            ContactPointAdded.class,
            msg -> {
              contactPoints.add(msg.contactPoint());
              // Now do something with an up-to-date "contactPoints"
            })
        .match(
            ContactPointRemoved.class,
            msg -> {
              contactPoints.remove(msg.contactPoint());
              // Now do something with an up-to-date "contactPoints"
            })
        .build();
  }
}
```

同样，我们也可以让一个 Actor 以类似的方式来学习集群客户端与`ClusterClientReceptionist`之间的联系：

```java
public static class ReceptionistListener extends AbstractActor {
  private final ActorRef targetReceptionist;
  private final Set<ActorRef> clusterClients = new HashSet<>();

  public ReceptionistListener(ActorRef targetReceptionist) {
    this.targetReceptionist = targetReceptionist;
  }

  @Override
  public void preStart() {
    targetReceptionist.tell(SubscribeClusterClients.getInstance(), sender());
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            ClusterClients.class,
            msg -> {
              clusterClients.addAll(msg.getClusterClients());
              // Now do something with an up-to-date "clusterClients"
            })
        .match(
            ClusterClientUp.class,
            msg -> {
              clusterClients.add(msg.clusterClient());
              // Now do something with an up-to-date "clusterClients"
            })
        .match(
            ClusterClientUnreachable.class,
            msg -> {
              clusterClients.remove(msg.clusterClient());
              // Now do something with an up-to-date "clusterClients"
            })
        .build();
  }
}
```

## 配置

可以使用以下属性配置`ClusterClientReceptionist`扩展（或`ClusterReceptionistSettings`）：

```yml
# Settings for the ClusterClientReceptionist extension
akka.cluster.client.receptionist {
  # Actor name of the ClusterReceptionist actor, /system/receptionist
  name = receptionist

  # Start the receptionist on members tagged with this role.
  # All members are used if undefined or empty.
  role = ""

  # The receptionist will send this number of contact points to the client
  number-of-contacts = 3

  # The actor that tunnel response messages to the client will be stopped
  # after this time of inactivity.
  response-tunnel-receive-timeout = 30s
  
  # The id of the dispatcher to use for ClusterReceptionist actors. 
  # If not specified default dispatcher is used.
  # If specified you need to define the settings of the actual dispatcher.
  use-dispatcher = ""

  # How often failure detection heartbeat messages should be received for
  # each ClusterClient
  heartbeat-interval = 2s

  # Number of potentially lost/delayed heartbeats that will be
  # accepted before considering it to be an anomaly.
  # The ClusterReceptionist is using the akka.remote.DeadlineFailureDetector, which
  # will trigger if there are no heartbeats within the duration
  # heartbeat-interval + acceptable-heartbeat-pause, i.e. 15 seconds with
  # the default settings.
  acceptable-heartbeat-pause = 13s

  # Failure detection checking interval for checking all ClusterClients
  failure-detection-interval = 2s
}
```

当使用`ActorSystem`参数创建时，`ClusterClientSettings`将读取以下配置属性。还可以修改`ClusterClientSettings`，或者从另一个具有如下相同布局的配置部分创建它。`ClusterClientSettings`是`ClusterClient.props`工厂方法的参数，即如果需要的话，每个客户端可以配置不同的设置。

```yml
# Settings for the ClusterClient
akka.cluster.client {
  # Actor paths of the ClusterReceptionist actors on the servers (cluster nodes)
  # that the client will try to contact initially. It is mandatory to specify
  # at least one initial contact. 
  # Comma separated full actor paths defined by a string on the form of
  # "akka.tcp://system@hostname:port/system/receptionist"
  initial-contacts = []
  
  # Interval at which the client retries to establish contact with one of 
  # ClusterReceptionist on the servers (cluster nodes)
  establishing-get-contacts-interval = 3s
  
  # Interval at which the client will ask the ClusterReceptionist for
  # new contact points to be used for next reconnect.
  refresh-contacts-interval = 60s
  
  # How often failure detection heartbeat messages should be sent
  heartbeat-interval = 2s
  
  # Number of potentially lost/delayed heartbeats that will be
  # accepted before considering it to be an anomaly.
  # The ClusterClient is using the akka.remote.DeadlineFailureDetector, which
  # will trigger if there are no heartbeats within the duration 
  # heartbeat-interval + acceptable-heartbeat-pause, i.e. 15 seconds with
  # the default settings.
  acceptable-heartbeat-pause = 13s
  
  # If connection to the receptionist is not established the client will buffer
  # this number of messages and deliver them the connection is established.
  # When the buffer is full old messages will be dropped when new messages are sent
  # via the client. Use 0 to disable buffering, i.e. messages will be dropped
  # immediately if the location of the singleton is unknown.
  # Maximum allowed buffer size is 10000.
  buffer-size = 1000

  # If connection to the receiptionist is lost and the client has not been
  # able to acquire a new connection for this long the client will stop itself.
  # This duration makes it possible to watch the cluster client and react on a more permanent
  # loss of connection with the cluster, for example by accessing some kind of
  # service registry for an updated set of initial contacts to start a new cluster client with.
  # If this is not wanted it can be set to "off" to disable the timeout and retry
  # forever.
  reconnect-timeout = off
}
```

## 故障处理

启动集群客户端时，必须为其提供一个初始连接点列表，这些连接点是正在运行接待员的集群节点。然后，它会重复地（通过`establishing-get-contacts-interval`来配置一个间隔）尝试联系这些连接点，直到它与其中一个连接。在运行时，连接点的列表被来自接待员的数据连续更新（再次，具有可配置的`refresh-contacts-interval`间隔），因此如果群集中的接待员比提供给客户端的初始连接点更多，则客户端将接触（`learn about`）它们。

当客户端运行时，它将检测到其与接待员的连接失败，如果错过的心跳超过可配置的数量，客户端将尝试重新连接到其已知的连接点，以找到可以访问的接待员。

## 当无法到达群集时

如果集群客户端找不到可以在可配置的时间间隔内与之连接的接待员，则可以完全停止集群客户机。这是通过`reconnect-timeout`配置的，默认为`off`。当从某种服务注册表提供初始连接点、群集节点地址完全是动态的、整个群集可能关闭或崩溃、在新地址上重新启动时，这可能很有用。由于在这种情况下客户端将被停止，监视 Actor 可以监视它，并且在终止时，可以获取一组新的初始连接点，并启动一个新的集群客户端。


----------

**英文原文链接**：[Cluster Client](https://doc.akka.io/docs/akka/current/cluster-client.html).


----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————