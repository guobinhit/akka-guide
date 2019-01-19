# 集群的使用方法
> **注释**：本文描述了如何使用 Akka 集群。

@[toc]

有关 Akka 集群概念的介绍，请参阅「[集群规范](https://doc.akka.io/docs/akka/current/common/cluster.html)」。

Akka 集群的核心是集群成员（`cluster membership`），以跟踪哪些节点是集群的一部分以及它们的健康状况。

## 依赖
为了使用 Akka 集群，你必须在你的项目中添加如下依赖：

```xml
<!-- Maven -->
<dependency>
  <groupId>com.typesafe.akka</groupId>
  <artifactId>akka-cluster_2.11</artifactId>
  <version>2.5.19</version>
</dependency>

<!-- Gradle -->
dependencies {
  compile group: 'com.typesafe.akka', name: 'akka-cluster_2.11', version: '2.5.19'
}

<!-- sbt -->
libraryDependencies += "com.typesafe.akka" %% "akka-cluster" % "2.5.19"
```

## 简单的项目
你可以查看「[集群示例](https://developer.lightbend.com/start/?group=akka&project=akka-samples-cluster-java)」项目，以了解 Akka 集群的实际使用情况。

## 何时何地使用 Akka 集群？
如果你打算使用微服务架构或传统的分布式应用程序，则必须进行架构的选择。这个选择将影响你应该如何使用 Akka 集群。

### 微服务

微服务（`Microservices`）具有许多吸引人的特性，例如，微服务的独立性允许多个更小、更专注的团队能够更频繁地提供新功能，并且能够更快地响应业务机会。响应性微服务（`Reactive Microservices`）应该是独立的、自主的，并且有一个单一的责任，正如 Jonas Bonér 在「[Reactive Microsystems: The Evolution of Microservices at Scale](https://info.lightbend.com/ebook-reactive-microservices-the-evolution-of-microservices-at-scale-register.html?_ga=2.96861852.2096678574.1547779241-367153478.1547175682)」一书中所指出的那样。

在微服务架构中，你应该考虑服务内部和服务之间的通信。

一般来说，我们建议不要在不同的服务之间使用 Akka 集群和 Actor 消息传递，因为这会导致服务之间的代码耦合过紧，并且难以独立地部署这些服务，这是使用微服务架构的主要原因之一。有关这方面的一些背景，请参见「[Lagom Framework](https://www.lagomframework.com/)」中关于「[Internal and External Communication](https://www.lagomframework.com/documentation/current/java/InternalAndExternalCommunication.html)」的讨论，其中每个微服务都是一个 Akka 集群。

单个服务的节点需要较少的去耦。它们共享相同的代码，由单个团队或个人作为一个集合部署在一起。在滚动部署（`rolling deployment`）期间，可能有两个版本同时运行，但整个集合的部署只有一个控制点。因此，业务内通信可以利用 Akka 集群的故障管理和 Actor 消息传递使用方便和性能优异的优点。

在不同的服务之间，「[Akka HTTP](https://doc.akka.io/docs/akka-http/current/)」或「[Akka gRPC](https://developer.lightbend.com/docs/akka-grpc/current/?_ga=2.163912443.533569272.1547780922-367153478.1547175682)」可用于同步（但不阻塞）通信，而「[Akka Streams Kafka](https://doc.akka.io/docs/akka-stream-kafka/current/home.html)」或其他「[Alpakka](https://doc.akka.io/docs/alpakka/current/)」连接器可用于集成异步通信。所有这些通信机制都可以很好地与端到端的反向压力（`end-to-end back-pressure`）的消息流配合使用，同步通信工具也可以用于单个请求-响应（`request response`）交互。同样重要的是要注意，当使用这些工具时，通信的双方不必使用 Akka 实现，编程语言也不重要。

## 传统的分布式应用
我们承认微服务也带来了许多新的挑战，它不是构建应用程序的唯一方法。传统的分布式应用程序可能不那么复杂，在许多情况下也工作得很好。例如，对于一个小的初创企业，只有一个团队，在那里构建一个应用程序，上市时间就是一切。Akka 集群可以有效地用于构建这种分布式应用程序。

在这种情况下，你只有一个部署单元，从单个代码库构建（或使用传统的二进制依赖性管理模块化），但使用单个集群跨多个节点部署。更紧密的耦合是可以的，因为有一个部署和控制的中心点。在某些情况下，节点可能具有专门的运行时角色，这意味着集群不是完全相同的（例如，`front-end`和`back-end`节点，或专用的`master/worker`节点），但如果这些节点是从相同的构建构件运行的，则这只是一种运行时行为，不会造成与紧耦合中可能出现的问题相同的问题。

紧耦合的分布式应用程序多年来为行业和许多 Akka 用户提供了良好的服务，仍然是一个有效的选择。

## 分布式整体
还有一种反模式（`anti-pattern`），有时被称为“分布式整体（`distributed monolith`）”。你有多个彼此独立地构建和部署的服务，但是它们之间的紧密耦合使得这非常危险，例如共享集群、共享代码和服务 API 调用的依赖项，或者共享数据库模式。由于代码和部署单元的物理分离，有一种错误的自主权感，但是由于一个服务的实现中的更改泄漏到其他服务的行为中，你很可能会遇到问题。参见 Ben Christensen 的「[Don’t Build a Distributed Monolith](https://www.microservices.com/talks/dont-build-a-distributed-monolith/)」。

在这种情况下，发现自己处于这种状态的组织通常会通过集中协调多个服务的部署来作出反应，此时，在承担成本的同时，你已经失去了微服务的主要好处。你正处于一个中间状态，以一种单独的方式构建和部署那些并不真正可分离的东西。有些人这样做，有些人设法使它工作，但这不是我们推荐的，它需要小心管理。

## 一个简单的集群示例
以下配置允许使用`Cluster`扩展。它加入集群，Actor 订阅集群成员事件并记录它们。

`application.conf`配置如下：

```java
akka {
  actor {
    provider = "cluster"
  }
  remote {
    log-remote-lifecycle-events = off
    netty.tcp {
      hostname = "127.0.0.1"
      port = 0
    }
  }

  cluster {
    seed-nodes = [
      "akka.tcp://ClusterSystem@127.0.0.1:2551",
      "akka.tcp://ClusterSystem@127.0.0.1:2552"]

    # auto downing is NOT safe for production deployments.
    # you may want to use it during development, read more about it in the docs.
    #
    # auto-down-unreachable-after = 10s
  }
}

# Enable metrics extension in akka-cluster-metrics.
akka.extensions=["akka.cluster.metrics.ClusterMetricsExtension"]

# Sigar native library extract location during tests.
# Note: use per-jvm-instance folder when running multiple jvm on one host.
akka.cluster.metrics.native-library-extract-folder=${user.dir}/target/native
```
要在 Akka 项目中启用集群功能，你至少应该添加「[Remoting](https://doc.akka.io/docs/akka/current/remoting.html)」设置，但使用集群。`akka.cluster.seed-nodes`通常也应该添加到`application.conf`文件中。

- **注释**：如果你在 Docker 容器中运行 Akka，或者由于其他原因，节点具有单独的内部和外部 IP 地址，则必须根据 NAT 或 Docker 容器中的 Akka 配置远程处理。

种子节点是为集群的初始、自动、连接配置的接触点。

请注意，如果要在不同的计算机上启动节点，则需要在`application.conf`中指定计算机的 IP 地址或主机名，而不是`127.0.0.1`。

使用集群扩展的 Actor 可能如下所示：

```java
/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.cluster;

import akka.actor.AbstractActor;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.ClusterEvent.MemberEvent;
import akka.cluster.ClusterEvent.MemberUp;
import akka.cluster.ClusterEvent.MemberRemoved;
import akka.cluster.ClusterEvent.UnreachableMember;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class SimpleClusterListener extends AbstractActor {
  LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
  Cluster cluster = Cluster.get(getContext().getSystem());

  //subscribe to cluster changes
  @Override
  public void preStart() {
    cluster.subscribe(getSelf(), ClusterEvent.initialStateAsEvents(), 
        MemberEvent.class, UnreachableMember.class);
  }

  //re-subscribe when restart
  @Override
  public void postStop() {
    cluster.unsubscribe(getSelf());
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
      .match(MemberUp.class, mUp -> {
        log.info("Member is Up: {}", mUp.member());
      })
      .match(UnreachableMember.class, mUnreachable -> {
        log.info("Member detected as unreachable: {}", mUnreachable.member());
      })
      .match(MemberRemoved.class, mRemoved -> {
        log.info("Member is Removed: {}", mRemoved.member());
      })
      .match(MemberEvent.class, message -> {
        // ignore
      })
      .build();
  }
}
```
Actor 将自己注册为某些集群事件的订阅者。它在订阅开始时接收与集群当前状态对应的事件，然后接收集群中发生更改的事件。

你自己运行这个例子最简单的方法是下载准备好的「[Akka Cluster Sample with Java](https://example.lightbend.com/v1/download/akka-samples-cluster-java?_ga=2.10741492.1303390800.1547782321-52773400.1547780943)」和教程。它包含有关如何运行`SimpleClusterApp`的说明。此示例的源代码可以在「[Akka Samples Repository](https://developer.lightbend.com/start/?group=akka&amp;project=akka-sample-cluster-java)」中找到。

## 联接种子节点

- **注释**：当在云系统上启动集群时，如 Kubernetes、AWS、Google Cloud,、Azure、Mesos 或其他维护 DNS 或其他发现节点的方式，你可能希望使用开源「[Akka Cluster Bootstrap](https://developer.lightbend.com/docs/akka-management/current/bootstrap/index.html)」模块实现的自动加入过程。

### 联接已配置的种子节点
你可以决定是手动加入集群，还是自动加入到配置的初始接触点，即所谓的种子节点。在连接过程之后，种子节点并不特殊，它们以与其他节点完全相同的方式参与集群。

当一个新节点启动时，它会向所有种子节点发送一条消息，然后向首先应答的节点发送`join`命令。如果没有任何种子节点响应（可能尚未启动），则会重试此过程，直到成功或关闭。

你在`application.conf`配置文件中定义种子节点：

```java
akka.cluster.seed-nodes = [
  "akka.tcp://ClusterSystem@host1:2552",
  "akka.tcp://ClusterSystem@host2:2552"]
```
当 JVM 启动时，也可以将其定义 Java 系统属性：
```java
-Dakka.cluster.seed-nodes.0=akka.tcp://ClusterSystem@host1:2552
-Dakka.cluster.seed-nodes.1=akka.tcp://ClusterSystem@host2:2552
```
种子节点可以任意顺序启动，不需要运行所有的种子节点，但是在初始启动集群时必须启动配置列表`seed-nodes`中第一个元素的节点，否则其他种子节点将不会初始化，其他节点也不能加入集群。第一个种子节点之所以特殊，其原因是避免从空集群开始时形成分离的岛（`islands`）。同时启动所有配置的种子节点是最快的（顺序无关紧要），否则它可以占用配置的`seed-node-timeout`，直到节点可以加入。

一旦启动了两个以上的种子节点，就可以关闭第一个种子节点。如果第一个种子节点重新启动，它将首先尝试加入现有集群中的其他种子节点。请注意，如果同时停止所有种子节点，并使用相同的`seed-nodes`配置重新启动它们，它们将自己加入并形成新的集群，而不是加入现有集群的其余节点。这可能是不需要的，应该通过将几个节点列为种子节点来避免冗余，并且不要同时停止所有节点。

### 使用 Cluster Bootstrap 自动联接种子节点

与手动配置种子节点（这在开发或静态分配的节点 IP 中很有用）不同，你可能希望使用云提供者（`cloud providers`）或集群协调者（`cluster orchestrator`）或其他某种形式的服务发现（如托管 DNS）来自动发现种子节点。开源的 Akka 管理库包括「[Cluster Bootstrap](https://developer.lightbend.com/docs/akka-management/current/bootstrap/index.html)」模块，它就专注于处理这个问题。有关更多详细信息，请参阅其文档。

### 使用`joinSeedNodes`编程联接到种子节点
你还可以使用`Cluster.get(system).joinSeedNodes`以编程方式进行连接种子节点，这在启动时使用一些外部工具或 API 动态发现其他节点时很有吸引力。当使用`joinSeedNodes`时，除了应该是第一个种子节点的节点之外，不应该包括节点本身，并且应该将其放在`joinSeedNodes`的参数中的第一个节点中。

```java
import akka.actor.Address;
import akka.cluster.Cluster;

final Cluster cluster = Cluster.get(system);
List<Address> list = new LinkedList<>(); //replace this with your method to dynamically get seed nodes
cluster.joinSeedNodes(list);
```
在配置属性`seed-node-timeout`中定义的时间段之后，将自动重试联系种子节点失败的尝试。在尝试联接失败之后，经过`retry-unsuccessful-join-after`配置的时间，将自动重试加入特定种子节点失败的尝试。重试意味着它尝试联系所有种子节点，然后连接首先应答的节点。如果种子节点列表中的第一个节点在配置的`seed-node-timeout`时间内无法联系任何其他种子节点，那么它将连接自身。

默认情况下，给定种子节点的联接将无限期重试，直到成功联接为止。如果配置超时失败，则可以中止该进程。当中止时，它将运行「[Coordinated Shutdown](https://doc.akka.io/docs/akka/current/actors.html#coordinated-shutdown)」，默认情况下将终止`ActorSystem`。也可以将 Coordinated Shutdown 配置为退出 JVM。如果`seed-nodes`是动态组装的，并且在尝试失败后使用新`seed-nodes`重新启动，则定义此超时非常有用。

```java
akka.cluster.shutdown-after-unsuccessful-join-seed-nodes = 20s
akka.coordinated-shutdown.terminate-actor-system = on
```
如果不配置种子节点或使用`joinSeedNodes`，则需要手动加入集群，可以使用「[JMX](https://doc.akka.io/docs/akka/current/cluster-usage.html#cluster-jmx)」或「[HTTP](https://doc.akka.io/docs/akka/current/cluster-usage.html#cluster-http)」执行。

你可以加入集群中的任何节点。它不必配置为种子节点。请注意，您只能联接到现有的集群成员，这意味着对于`bootstrapping`，某些节点必须联接到自己，然后以下节点可以联接它们以组成集群。

Actor 系统只能加入一次集群。其他尝试将被忽略。当它成功加入时，必须重新启动才能加入另一个集群或再次加入同一个集群。重新启动后可以使用相同的主机名和端口，当它成为集群中现有成员的新化身（`incarnation`），尝试加入时，将从集群中删除现有成员，然后允许它加入。

- **注释**：对于集群中的所有成员，`ActorSystem`的名称必须相同。当你启动`ActorSystem`时，将给出`ActorSystem`的名称。

## Downing
当故障检测器（`failure detector`）认为某个成员`unreachable`时，不允许`leader`履行其职责，例如将新加入成员的状态更改为`Up`。节点必须首先再次`reachable`，或者`unreachable`的成员的状态必须更改为Down``。将状态更改为`Down`可以自动或手动执行。默认情况下，必须使用「[JMX](https://doc.akka.io/docs/akka/current/cluster-usage.html#cluster-jmx)」或「[HTTP](https://doc.akka.io/docs/akka/current/cluster-usage.html#cluster-http)」手动完成。

它也可以用`Cluster.get(system).down(address)`以编程方式执行。

如果一个节点仍在运行，并且将其自身视为`Down`，那么它将关闭。如果在运行时将`run-coordinated-shutdown-when-down`设置为`on`（默认值），则 Coordinated Shutdown 将自动运行，但是节点不会尝试优雅地离开集群，因此不会发生分区和单例迁移。

「[Split Brain Resolver](https://developer.lightbend.com/docs/akka-commercial-addons/current/split-brain-resolver.html)」是「[Lightbend Reactive Platform](https://www.lightbend.com/lightbend-platform)」的一部分，它提供了一种针对`downing`问题的预打包（`pre-packaged`）解决方案。如果你不使用 RP，你无论如何都应该仔细阅读 Split Brain Resolver 的文档，并确保你使用的解决方案能够处理此处描述的问题。

### Auto-downing (DO NOT USE)
有一个自动`downing`的功能，但你不应该在生产中使用。出于测试目的，你可以通过配置启用它：

```java
akka.cluster.auto-down-unreachable-after = 120s
```
这意味着在配置时间内没联接成功的话，集群的`leader`将自动改变`unreachable`节点的状态为`down`。

这是一种从集群成员中删除`unreachable`节点的天真方法。它在开发过程中是有用的，但在生产环境中，它最终会破坏集群。当发生网络分区时，分区的两侧将看到另一侧`unreachable`，并将其从集群中移除。这导致了两个分开的、断开的簇的形成，称为 Split Brain。

此行为不限于网络分区（`network partitions`，也称为网络分裂）。如果集群中的节点过载，或者经历长 GC 暂停，也可能发生这种情况。

- **警告**：
  - 我们建议不要在生产中使用 Akka 集群的`auto-down`功能。它对生产系统有多种不良后果。
  - 如果你使用的是 Cluster Singleton 或 Cluster Sharding，那么它可能会破坏这些特性提供的契约。两者都保证了一个 Actor 在集群中是唯一的。启用`auto-down`功能后，可能形成多个独立集群。当这种情况发生时，保证的唯一性将不再是真的，从而导致系统中的不良行为。
  - 当 Akka Persistence 与 Cluster Sharding 结合使用时，这种情况更为严重。在这种情况下，缺少唯一性的 Actor 会导致多个 Actor 写入同一个日志。Akka Persistence 的工作是单一写入原则（`single writer principle`）。拥有多个写入者会损坏日志并使其无法使用。
  - 最后，即使你不使用 Persistence、Sharding 或 Singletons 等特性，`auto-downing`也会导致系统形成多个小集群。这些小集群将彼此独立。它们将无法通信，因此你可能会遇到性能下降。一旦出现这种情况，就需要人工干预来修正集群。
  - 由于这些问题，`auto-downing`不应该在生产环境中使用。

## Leaving
从集群中删除成员有两种方法。

你可以停止 Actor 系统（或 JVM 进程）。它将被检测为`unreachable`，并在自动或手动`downing`后移除，如上文所述。

如果你告诉集群一个节点应该离开，那么可以执行更优雅的退出。这可以使用「[JMX](https://doc.akka.io/docs/akka/current/cluster-usage.html#cluster-jmx)」或「[HTTP](https://doc.akka.io/docs/akka/current/cluster-usage.html#cluster-http)」执行。也可以通过以下方式以编程方式执行：

```java
final Cluster cluster = Cluster.get(system);
cluster.leave(cluster.selfAddress());
```
注意，这个命令可以发送给集群中的任何成员，不一定是要离开的成员。

当集群节点将自己视为`Exiting`时，Coordinated Shutdown 将自动运行，即从另一个节点退出将触发`leaving`节点上的关闭过程。当使用 Akka 集群时，会自动添加集群的优雅离开任务，包括 Cluster Singletons 的优雅关闭和 Cluster Sharding，即运行关闭过程也会触发尚未进行的优雅离开。

通常情况下，这是自动处理的，但在此过程中，如果出现网络故障，可能仍然需要将节点的状态设置为`Down`，以便完成删除。

## WeaklyUp 成员
如果一个节点是`unreachable`的，那么消息收敛是不可能的，因此`leader`的任何行为也是不可能的。但是，在这个场景中，我们仍然可能希望新节点加入集群。

如果不能达到收敛，加入成员将被提升为`WeaklyUp`，并成为集群的一部分。一旦达到消息收敛，`leader`就会把`WeaklyUp`的成员调为`Up`。

默认情况下启用此功能，但可以使用配置选项禁用此功能：

```java
akka.cluster.allow-weakly-up-members = off
```
你可以订阅`WeaklyUp`的成员事件，以使用处于此状态的成员，但你应该注意，网络分裂（`network partition`）另一端的成员不知道新成员的存在。例如，在`quorum decisions`时，你不应该把`WeaklyUp`的成员计算在内。

## 订阅集群事件
可以使用`Cluster.get(system).subscribe`订阅集群成员的更改通知。

```java
cluster.subscribe(getSelf(), MemberEvent.class, UnreachableMember.class);
```
完整状态的快照`akka.cluster.ClusterEvent.CurrentClusterState`将作为第一条消息发送给订阅者，随后是在有更新事件时，再发送消息。

请注意，如果在完成初始联接过程之前启动订阅，则可能会收到一个空的`CurrentClusterState`，其中不包含成员，后面是已联接的其他节点的`MemberUp`事件。例如，当你在`cluster.join()`之后立即启动订阅时，可能会发生这种情况，如下所示。这是预期行为。当节点在集群中被接受后，你将收到该节点和其他节点的`MemberUp`。

```java
Cluster cluster = Cluster.get(getContext().getSystem());
  cluster.join(cluster.selfAddress());
cluster.subscribe(getSelf(), MemberEvent.class, UnreachableMember.class);
```
为了避免在开始时接收空的`CurrentClusterState`，你可以像下例所示这样使用它，将订阅推迟到接收到自己节点的`MemberUp`事件为止：

```java
Cluster cluster = Cluster.get(getContext().getSystem());
  cluster.join(cluster.selfAddress());
cluster.registerOnMemberUp(
  () -> cluster.subscribe(getSelf(), MemberEvent.class, UnreachableMember.class)
);
```
如果您发现处理`CurrentClusterState`不方便，可以使用`ClusterEvent.initialStateAsEvents()`作为参数进行订阅。这意味着，您将收到与当前状态相对应的事件，以模拟在过去发生事件时，如果正在监听这些事件，你将看到的情况，而不是作为第一条消息接收`CurrentClusterState`。请注意，这些初始事件只对应于当前状态，而不是集群中实际发生的所有更改的完整历史记录。

```java
cluster.subscribe(getSelf(), ClusterEvent.initialStateAsEvents(), 
    MemberEvent.class, UnreachableMember.class);
```
跟踪成员生命周期的事件有：

- `ClusterEvent.MemberJoined`，新成员已加入集群，其状态已更改为`Joining`。
- `ClusterEvent.MemberUp`，新成员已加入集群，其状态已更改为`Up`。
- `ClusterEvent.MemberExited`，某个成员正在离开集群，其状态已更改为`Exiting`。请注意，在另一个节点上发布此事件时，该节点可能已关闭。
- `ClusterEvent.MemberRemoved`，成员已从集群中完全删除。
- `ClusterEvent.UnreachableMember`，一个成员被认为是`unreachable`，由至少一个其他节点的故障检测器检测到。
- `ClusterEvent.ReachableMember`，在`unreachable`之后，成员被认为是`reachable`的。以前检测到它不可访问的所有节点都再次检测到它是可访问的。

有更多类型的变更事件，请参阅扩展`akka.cluster.ClusterEvent.ClusterDomainEvent`类的 API 文档，以了解有关事件的详细信息。

有时，不订阅集群事件，只使用`Cluster.get(system).state()`获取完整成员状态是很方便的。请注意，此状态不一定与发布到集群订阅的事件同步。

### Worker Dial-in Example
让我们来看一个示例，该示例演示了名为`backend`的工作者如何检测并注册到名为`frontend`的新主节点。

示例应用程序提供了一个转换文本的服务。当一些文本发送到其中一个`frontend`服务时，它将被委托给一个`backend`，后者执行转换作业，并将结果发送回原始客户机。新的`backend`节点以及新的`frontend`节点可以在集群中动态地添加或删除。

消息：

```java
public interface TransformationMessages {

  public static class TransformationJob implements Serializable {
    private final String text;

    public TransformationJob(String text) {
      this.text = text;
    }

    public String getText() {
      return text;
    }
  }

  public static class TransformationResult implements Serializable {
    private final String text;

    public TransformationResult(String text) {
      this.text = text;
    }

    public String getText() {
      return text;
    }

    @Override
    public String toString() {
      return "TransformationResult(" + text + ")";
    }
  }

  public static class JobFailed implements Serializable {
    private final String reason;
    private final TransformationJob job;

    public JobFailed(String reason, TransformationJob job) {
      this.reason = reason;
      this.job = job;
    }

    public String getReason() {
      return reason;
    }

    public TransformationJob getJob() {
      return job;
    }

    @Override
    public String toString() {
      return "JobFailed(" + reason + ")";
    }
  }

  public static final String BACKEND_REGISTRATION = "BackendRegistration";
}
```
`backend`执行文件转换工作：

```java
public class TransformationBackend extends AbstractActor {

  Cluster cluster = Cluster.get(getContext().getSystem());

  //subscribe to cluster changes, MemberUp
  @Override
  public void preStart() {
    cluster.subscribe(getSelf(), MemberUp.class);
  }

  //re-subscribe when restart
  @Override
  public void postStop() {
    cluster.unsubscribe(getSelf());
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
      .match(TransformationJob.class, job -> {
        getSender().tell(new TransformationResult(job.getText().toUpperCase()),
         getSelf());
      })
      .match(CurrentClusterState.class, state -> {
        for (Member member : state.getMembers()) {
          if (member.status().equals(MemberStatus.up())) {
            register(member);
          }
        }
      })
      .match(MemberUp.class, mUp -> {
        register(mUp.member());
      })
      .build();
  }

  void register(Member member) {
    if (member.hasRole("frontend"))
      getContext().actorSelection(member.address() + "/user/frontend").tell(
          BACKEND_REGISTRATION, getSelf());
  }
}
```
请注意，`TransformationBackend` Actor 订阅集群事件以检测新的、潜在的`frontend`节点，并向它们发送注册消息，以便它们知道可以使用`backend`工作者。

接收用户作业并委托给已注册的`backend`的`frontend`节点：

```java
public class TransformationFrontend extends AbstractActor {

  List<ActorRef> backends = new ArrayList<ActorRef>();
  int jobCounter = 0;

  @Override
  public Receive createReceive() {
    return receiveBuilder()
      .match(TransformationJob.class, job -> backends.isEmpty(), job -> {
        getSender().tell(
          new JobFailed("Service unavailable, try again later", job),
            getSender());
      })
      .match(TransformationJob.class, job -> {
        jobCounter++;
        backends.get(jobCounter % backends.size())
          .forward(job, getContext());
      })
      .matchEquals(BACKEND_REGISTRATION, x -> {
        getContext().watch(getSender());
        backends.add(getSender());
      })
      .match(Terminated.class, terminated -> {
        backends.remove(terminated.getActor());
      })
      .build();
  }
}
```
请注意，`TransformationFrontend` Actor 监视已注册的`backend`，以便能够将其从可用`backend`列表中删除。Death Watch 对集群中的节点使用集群故障检测器，例如检测网络故障和 JVM 崩溃，并优雅地终止被监视的 Actor。当`unreachable`的集群节点被关闭和删除时，Death Watch 将向监视 Actor 生成`Terminated`消息。

在示例中运行`Worker Dial-in Example`最简单的方法是下载准备好的「[Akka Cluster Sample with Java](https://example.lightbend.com/v1/download/akka-samples-cluster-java?_ga=2.10741492.1303390800.1547782321-52773400.1547780943)」和教程。它包含有关如何运行`Worker Dial-in Example`示例的说明。此示例的源代码可以在「[Akka Samples Repository](https://developer.lightbend.com/start/?group=akka&amp;project=akka-sample-cluster-java)」中找到。

## 节点角色
并非集群的所有节点都需要执行相同的功能：可能有一个子集运行 Web 前端，一个子集运行数据访问层，一个子集用于数字处理。例如，通过集群感知路由器（`cluster-aware routers`）部署 Actor，可以考虑节点角色（`node roles`）以实现这种职责分配。

节点的角色在名为`akka.cluster.roles`的配置属性中定义，通常在启动脚本中将其定义为系统属性或环境变量。

节点的角色是可以订阅的`MemberEvent`中成员信息的一部分。

## 如何在达到群集大小时启动
一个常见的用例是在集群已经初始化、成员已经加入并且集群已经达到一定的大小之后启动 Actor。

通过配置选项，你可以在`leader`将`Joining`成员的状态更改为`Up`之前定义所需的成员数：

```java
akka.cluster.min-nr-of-members = 3
```
以类似的方式，你可以在`leader`将`Joining`成员的状态更改为`Up`之前，定义特定角色所需的成员数：

```java
akka.cluster.role {
  frontend.min-nr-of-members = 1
  backend.min-nr-of-members = 2
}
```
可以在`registerOnMemberUp`回调中启动 Actor，当当前成员状态更改为`Up`时，将调用该回调，例如集群至少具有已定义的成员数。

```java
Cluster.get(system).registerOnMemberUp(new Runnable() {
  @Override
  public void run() {
    system.actorOf(Props.create(FactorialFrontend.class, upToN, true),
        "factorialFrontend");
  }
});
```
这个回调可以用于启动 Actor 以外的其他事情。

## 如何清理 Removed 状态的成员
You can do some clean up in a registerOnMemberRemoved callback, which will be invoked when the current member status is changed to ‘Removed’ or the cluster have been shutdown.

An alternative is to register tasks to the Coordinated Shutdown.



----------

**英文原文链接**：[Cluster Usage](https://doc.akka.io/docs/akka/current/cluster-usage.html).

----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://blog.csdn.net/qq_35246620/article/details/86293353) —— ☆☆☆ ————