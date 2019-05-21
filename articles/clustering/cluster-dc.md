# 跨多个数据中心集群

本章介绍如何跨多个数据中心、可用性`zones`或区域使用 Akka 集群。

了解使用 Akka 集群时数据中心边界的原因是，与同一数据中心中的节点之间的通信相比，跨数据中心的通信通常具有更高的延迟和更高的故障率。

然而，节点的分组并不局限于数据中心的物理边界，即使这是主要的使用情况。由于其他原因，它也可以用作逻辑分组，例如隔离某些节点以提高稳定性，或者将大型集群拆分为较小的节点组以获得更好的可伸缩性。

## 动机

使用多个数据中心的原因有很多，例如：

- 冗余度，以允许在一个位置发生故障，仍然可以运行。
- 为用户附近的请求提供服务，以提供更好的响应能力。
- 在许多服务器上平衡负载。

可以使用跨越多个数据中心（`data centers`）的默认设置运行普通的 Akka 集群，但这可能会导致以下问题：

- 在网络分裂（`network partition`）期间，群集成员关系的管理将停止，如下面单独一节所述。这意味着在数据中心之间的网络分裂期间，不能添加和删除节点。
- 对跨数据中心的网络连接进行更频繁的误报检测。在数据中心内部和跨数据中心的故障检测中不可能有不同的设置。
- 对于网络分裂中的节点关闭/删除，对于数据中心内的故障和跨数据中心的故障，通常应采取不同的处理方法。对于数据中心之间的网络分裂，系统通常不应关闭不可访问的节点，而是等待其恢复，或者等待人工或外部监控系统做出决定。对于同一个数据中心内的故障，可以采用更积极的停机机制进行快速故障切换。
- 集群单例的快速故障转移和从一个数据中心到另一个数据中心的集群分片很难以安全的方式进行。存在单例或分片实体在网络分裂的两侧变得活跃的风险。
- 由于缺少位置信息，因此很难优化通信，使其更倾向于靠近较远节点的节点。例如，如果将消息路由到自己的数据中心中的节点，那么支持集群的路由器将更高效。

为了避免这些问题，可以为每个数据中心运行一个单独的 Akka 集群，并使用数据中心之间的另一个通信通道，例如 HTTP、外部消息代理或集群客户端。然而，许多构建在集群成员关系（`membership`）之上的好用的工具都会丢失。例如，不可能在不同的集群中使用分布式数据。

我们经常建议将微服务实现为一个 Akka 集群。服务的外部 API 将是 HTTP、gRPC 或消息代理，而不是 Akka 远程处理或集群（参见 Lagom 框架文档中的其他讨论：[内部和外部通信](https://www.lagomframework.com/documentation/current/java/InternalAndExternalCommunication.html)），但是在多个节点上运行的服务内部通信将使用普通的 Actor 消息或基于 Akka 集群。当将此服务部署到多个数据中心时，如果内部通信无法使用普通的 Actor 消息传递，则会很不方便，因为它被分为几个 Akka 集群。在内部使用 Actor 消息传递的好处是性能、易于开发和从 Actor 的角度对你的领域进行推理。

因此，可以让 Actor 集群了解（`aware`）数据中心，这样一个 Akka 集群就可以跨越多个数据中心，并且仍然能够容忍网络分裂。

## 定义数据中心

这些功能基于这样一种理念：通过设置`akka.cluster.multi-data-center.self-data-center`配置属性，可以将节点分配给一组节点。节点只能属于一个数据中心，如果未指定任何内容，则节点将属于默认数据中心。

节点的分组并不局限于数据中心的物理边界，即使这是主要的使用情况。由于其他原因，它也可以用作逻辑分组，例如隔离某些节点以提高稳定性，或者将大型集群拆分为较小的节点组以获得更好的可伸缩性。

## 成员关系

成员状态转换（`membership transition`）由一个名为`leader`的节点管理。每个数据中心都有一个`leader`，负责同一数据中心内成员的这些转换。其他数据中心的成员由各自的数据中心`leader`独立管理。当数据中心中的节点之间存在任何不可访问性观测时，无法执行这些操作，但不同数据中心之间的不可访问性不会影响数据中心中成员状态管理的进度。当数据中心之间存在网络分裂时，也可以添加和删除节点，如果节点未分组到数据中心，则不可能添加和删除节点。

![node-transition](https://github.com/guobinhit/akka-guide/blob/master/images/clustering/cluster-dc/node-transition.png)

用户操作（如`joining`、`leaving`和`downing`）可以发送到集群中的任何节点，而不仅仅发送到节点数据中心中的节点。种子节点也是全局的。

数据中心成员关系是通过向成员的角色添加前缀为`"dc-"`的数据中心名称来实现的，因此集群中的所有其他成员都知道此信息。这是一个实现细节，但如果你能在日志消息中看到这一点，就更好了。

你可以检索有关成员所属数据中心的信息：

```java
final Cluster cluster = Cluster.get(system);
// this node's data center
String dc = cluster.selfDataCenter();
// all known data centers
Set<String> allDc = cluster.state().getAllDataCenters();
// a specific member's data center
Member aMember = cluster.state().getMembers().iterator().next();
String aDc = aMember.dataCenter();
```

## 故障检测

故障检测是通过发送心跳消息来检测节点是否无法访问来执行的。与跨数据中心相比，在同一个数据中心中的节点之间执行此操作的频率更高且更确定。不同数据中心之间的「[故障检测](https://doc.akka.io/docs/akka/current/cluster-usage.html#failure-detector)」应解释为数据中心之间的网络连接出现问题的迹象。

可以为这两个目的配置两个不同的故障检测器：

- `akka.cluster.failure-detector`，用于在自己的数据中心内进行故障检测
- `akka.cluster.multi-data-center.failure-detector`，用于跨不同数据中心的故障检测

订阅集群事件时，`UnreachableMember`和`ReachableMember`事件用于在自己的数据中心内进行观察。与注册订阅的数据中心相同。

对于跨数据中心的不可访问通知，你可以订阅`UnreachableDataCenter`和`ReachableDataCenter`事件。

跨数据中心检测故障的心跳消息仅在每侧的多个最旧节点（`oldest nodes`）之间执行。节点数配置为`akka.cluster.multi-data-center.cross-data-center-connections`。仅使用有限数量的节点的原因是保持跨数据中心的连接数较低。在跨数据中心传播成员信息时，同样的节点也用于`gossip`协议。在一个数据中心内，所有节点都参与流言和故障检测。

这会影响滚动升级的执行方式。不要同时停止所有最老的用于`gossip`协议的节点。一次停止一个或几个节点，以便新节点可以接管职责。最好将最旧的节点保留到最后。

## 集群单例

集群单例是每个数据中心的单例。如果在所有节点上启动`ClusterSingletonManager`，并且定义了 3 个不同的数据中心，那么集群中将有 3 个活动的单例实例，每个数据中心都有一个。这是自动处理的，但需要注意。为每个数据中心设计一个单例，以便系统在数据中心之间的网络分裂期间也可以使用。

单例数据中心而非全局的原因是，当每个数据中心使用一个`leader`时，不能保证跨数据中心的成员信息一致，这使得选择单个全局单例数据中心变得困难。

如果你需要一个全局单例，你必须选择一个数据中心来承载该单例，并且只在该数据中心的节点上启动`ClusterSingletonManager`。如果无法从另一个数据中心访问数据中心，则无法访问单例，这是在选择一致性而非可用性时的合理权衡。

默认情况下，`ClusterSingletonProxy`将消息路由到自己的数据中心中的单例，但它可以使用`ClusterSingletonProxySettings`中的`data-center`参数启动，以定义它应将消息路由到另一个数据中心中的单例。例如，当一个数据中心中有一个全局单例并从其他数据中心访问它时，这是非常有用的。

以下是如何为特定数据中心创建单例代理：

```java
ActorRef proxyDcB =
    system.actorOf(
        ClusterSingletonProxy.props(
            "/user/consumer",
            ClusterSingletonProxySettings.create(system)
                .withRole("worker")
                .withDataCenter("B")),
        "consumerProxyDcB");
```

如果使用自己的数据中心作为`withDataCenter`参数，则该参数将作为自己数据中心中的单例的代理，如果不提供`withDataCenter`，则该参数也是默认值。

## 集群分片

集群分片中的协调器（`coordinator`）是一个集群单例，因此，如上所述，集群分片也是每个数据中心的。每个数据中心都有自己的协调员和区域，与其他数据中心隔离。如果你在所有节点上以相同的名称启动一个实体类型，并且你定义了 3 个不同的数据中心，然后将消息发送到相同的实体 ID 到所有数据中心的共享区域，那么你将得到该实体 ID 的 3 个活动实体实例，每个数据中心一个。这是因为`region/coordinator`只知道自己的数据中心，并将在那里激活实体。它不知道其他数据中心中存在相应的实体。

尤其是当与基于单编写器原则（`single-writer principle`）的 Akka 持久性一起使用时，避免在多个位置同时运行同一实体和共享数据存储是很重要的。这将导致数据损坏，因为不同实例存储的事件可能会交错，并且在以后的重播中会有不同的解释。有关活动持久实体，请参见Lightbend 的「[Multi-DC 持久性](https://developer.lightbend.com/docs/akka-commercial-addons/current/persistence-dc/index.html)」。

如果你需要全局实体，则必须选择一个数据中心来承载该实体类型，并且只在该数据中心的节点上启动集群。如果无法从另一个数据中心访问数据中心，则无法访问实体，这是在选择一致性而非可用性时的合理权衡。

群集分片代理默认将消息路由到其自己的数据中心的分片区域，但可以使用`data-center`参数启动它，以定义它应将消息路由到位于另一个数据中心的分片区域。例如，当一个数据中心中有全局实体并从其他数据中心访问它们时，这非常有用。

以下是如何为特定数据中心创建分片代理：

```java
ActorRef counterProxyDcB =
    ClusterSharding.get(system)
        .startProxy(
            "Counter",
            Optional.empty(),
            Optional.of("B"), // data center name
            messageExtractor);
```

管理全局实体的另一种方法是，通过将消息路由到正确的区域，确保某些实体 ID 仅位于一个数据中心中。例如，路由功能可以是奇数实体 ID 路由到数据中心`A`，偶数实体 ID 路由到数据中心`B`。在将消息发送到本地区域 Actor 之前，你可以决定将消息路由到哪个数据中心。如上文所述，可以使用分片代理发送其他数据中心的消息，并将自己的数据中心的消息发送到本地区域。


----------

**英文原文链接**：[Cluster across multiple data centers](https://doc.akka.io/docs/akka/current/cluster-dc.html).


----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————