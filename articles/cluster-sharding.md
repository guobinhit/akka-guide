# 集群分片
## 依赖
为了使用集群分片（`Cluster Sharding`），你必须在项目中添加如下依赖：

```xml
<!-- Maven -->
<dependency>
  <groupId>com.typesafe.akka</groupId>
  <artifactId>akka-cluster-sharding_2.11</artifactId>
  <version>2.5.19</version>
</dependency>

<!-- Gradle -->
dependencies {
  compile group: 'com.typesafe.akka', name: 'akka-cluster-sharding_2.11', version: '2.5.19'
}

<!-- sbt -->
libraryDependencies += "com.typesafe.akka" %% "akka-cluster-sharding" % "2.5.19"
```
## 示例项目
你可以查看「[集群分片](https://developer.lightbend.com/start/?group=akka&amp;project=akka-samples-cluster-sharding-java)」项目，以了解 Akka 集群分片的实际使用情况。

## 简介
当你需要将 Actor 分布在集群中的多个节点上，并且希望能够使用它们的逻辑标识符与它们进行交互，但不必关心它们在集群中的物理位置时，集群分片（`Cluster sharding`）非常有用，这也可能随着时间的推移而改变。

例如，它可以是表示域驱动设计（`Domain-Driven Design`）术语中聚合根（`Aggregate Roots`）的 Actor。在这里，我们称这些 Actor 为“实体”。这些 Actor 通常具有持久（`durable`）状态，但此功能不限于具有持久状态的 Actor。

集群切分通常在有许多状态 Actor 共同消耗的资源（例如内存）多于一台机器上所能容纳的资源时使用。如果你只有几个有状态的 Actor，那么在集群单例（`Cluster Singleton`）节点上运行它们可能更容易。

在这个上下文中，分片意味着具有标识符（称为实体）的 Actor 可以自动分布在集群中的多个节点上。每个实体 Actor 只在一个地方运行，消息可以发送到实体，而不需要发送者知道目标 Actor 的位置。这是通过这个扩展提供的`ShardRegion` Actor 发送消息来实现的，它知道如何将带有实体 ID 的消息路由到最终目标。

如果启用了该功能，则集群分片将不会在状态为`WeaklyUp`的成员上活动。

- **警告**：不要将 Cluster Sharding 与 Automatic Downing 一起使用，因为它允许集群分裂为两个单独的集群，从而导致多个分片和实体启动，每个集群中只有一个节点！详见「[Downing](https://doc.akka.io/docs/akka/current/cluster-usage.html#automatic-vs-manual-downing)」。

## 一个示例
这就是实体 Actor 的样子：

```java
public class Counter extends AbstractPersistentActor {

  public enum CounterOp {
    INCREMENT, DECREMENT
  }

  public static class Get {
    final public long counterId;

    public Get(long counterId) {
      this.counterId = counterId;
    }
  }

  public static class EntityEnvelope {
    final public long id;
    final public Object payload;

    public EntityEnvelope(long id, Object payload) {
      this.id = id;
      this.payload = payload;
    }
  }

  public static class CounterChanged {
    final public int delta;

    public CounterChanged(int delta) {
      this.delta = delta;
    }
  }

  int count = 0;

  // getSelf().path().name() is the entity identifier (utf-8 URL-encoded)
  @Override
  public String persistenceId() {
    return "Counter-" + getSelf().path().name();
  }

  @Override
  public void preStart() throws Exception {
    super.preStart();
    getContext().setReceiveTimeout(Duration.ofSeconds(120));
  }

  void updateState(CounterChanged event) {
    count += event.delta;
  }

  @Override
  public Receive createReceiveRecover() {
    return receiveBuilder()
        .match(CounterChanged.class, this::updateState)
        .build();
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
      .match(Get.class, this::receiveGet)
      .matchEquals(CounterOp.INCREMENT, msg -> receiveIncrement())
      .matchEquals(CounterOp.DECREMENT, msg -> receiveDecrement())
      .matchEquals(ReceiveTimeout.getInstance(), msg -> passivate())
      .build();
  }

  private void receiveGet(Get msg) {
    getSender().tell(count, getSelf());
  }

  private void receiveIncrement() {
    persist(new CounterChanged(+1), this::updateState);
  }

  private void receiveDecrement() {
    persist(new CounterChanged(-1), this::updateState);
  }

  private void passivate() {
    getContext().getParent().tell(
        new ShardRegion.Passivate(PoisonPill.getInstance()), getSelf());
  }
}
```
上面的 Actor 使用事件源和`AbstractPersistentActor`中提供的支持来存储其状态。它不必是持久性 Actor，但是如果节点之间的实体发生故障或迁移，那么它必须能够恢复其状态（如果它是有价值的）。

请注意如何定义`persistenceId`。Actor 的名称是实体标识符（UTF-8 URL 编码）。你也可以用另一种方式定义它，但它必须是唯一的。

当使用分片扩展时，你首先要使用`ClusterSharding.start`方法注册支持的实体类型，通常是在集群中每个节点上的系统启动时。`ClusterSharding.start`为你提供了可以传递的参考。请注意，如果当前群集节点的角色与在`ClusterShardingSettings`中指定的角色不匹配，`ClusterSharding.start`将以代理模式启动`ShardRegion`。

```java
import akka.japi.Option;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;

Option<String> roleOption = Option.none();
ClusterShardingSettings settings = ClusterShardingSettings.create(system);
ActorRef startedCounterRegion =
    ClusterSharding.get(system)
        .start("Counter", Props.create(Counter.class), settings, messageExtractor);
```
`messageExtractor`定义了特定于应用程序的方法，以从传入消息中提取实体标识符和分片标识符。

```java
import akka.cluster.sharding.ShardRegion;

ShardRegion.MessageExtractor messageExtractor =
    new ShardRegion.MessageExtractor() {

      @Override
      public String entityId(Object message) {
        if (message instanceof Counter.EntityEnvelope)
          return String.valueOf(((Counter.EntityEnvelope) message).id);
        else if (message instanceof Counter.Get)
          return String.valueOf(((Counter.Get) message).counterId);
        else return null;
      }

      @Override
      public Object entityMessage(Object message) {
        if (message instanceof Counter.EntityEnvelope)
          return ((Counter.EntityEnvelope) message).payload;
        else return message;
      }

      @Override
      public String shardId(Object message) {
        int numberOfShards = 100;
        if (message instanceof Counter.EntityEnvelope) {
          long id = ((Counter.EntityEnvelope) message).id;
          return String.valueOf(id % numberOfShards);
        } else if (message instanceof Counter.Get) {
          long id = ((Counter.Get) message).counterId;
          return String.valueOf(id % numberOfShards);
        } else {
          return null;
        }
      }
    };
```
此示例说明了在消息中定义实体标识符的两种不同方法：

- `Get`消息包含标识符本身。
- `EntityEnvelope`包含标识符，发送给实体 Actor 的实际消息包装在信封中。

注意这两种消息类型是如何在上面展示的`entityId`和`entityMessage`方法中处理的。发送给实体 Actor 的消息是`entityMessage`返回的，这使得在需要时可以打开信封（`unwrap envelopes`）。

分片是一起管理的一组实体。分组由上文所示的`extractShardId`函数定义。对于特定的实体标识符，分片标识符必须始终相同。否则，实体 Actor 可能会同时在多个位置意外启动。

创建一个好的分片算法（`sharding algorithm`）本身就是一个有趣的挑战。尝试产生一个统一的分布，即在每个分片中有相同数量的实体。根据经验，分片的数量应该比计划的最大集群节点数量大十倍。分片少于节点数量将导致某些节点不会承载任何分片。太多的分片将导致对分片的管理效率降低，例如重新平衡开销，并增加延迟，因为协调器（`coordinator`）参与每个分片的第一条消息的路由。正在运行的集群中的所有节点上的分片算法必须相同。它可以在停止群集中的所有节点后进行更改。

一个简单的分片算法在大多数情况下都可以很好地工作，它是以分片的实体标识符模数的`hashCode`的绝对值为基础的。为了方便起见，`ShardRegion.HashCodeMessageExtractor`提供了这一功能。

向实体发送的消息始终通过本地`ShardRegion`发送。命名实体类型的`ShardRegion` Actor 引用由`ClusterSharding.start`返回，也可以使用`ClusterSharding.shardRegion`检索。如果`ShardRegion`不知道其位置的话，它将查找实体的分片位置。它将把消息委托给正确的节点，并根据需要创建实体 Actor，即在传递特定实体的第一条消息时。

```java
ActorRef counterRegion = ClusterSharding.get(system).shardRegion("Counter");
counterRegion.tell(new Counter.Get(123), getSelf());

counterRegion.tell(new Counter.EntityEnvelope(123, Counter.CounterOp.INCREMENT), getSelf());
counterRegion.tell(new Counter.Get(123), getSelf());
```

## 它是如何工作的?
`ShardRegion` Actor 在集群中的每个节点或标记有特定角色的节点组上启动。`ShardRegion`由两个特定于应用程序的函数创建，用于从传入消息中提取实体标识符（`entity identifier`）和分片标识符（`shard identifier`）。分片是统一管理的一组实体。对于特定分片中的第一条消息，`ShardRegion`将从中心协调者`ShardCoordinator`请求分片的位置。

`ShardCoordinator`决定哪个`ShardRegion`将拥有`Shard`，并通知`ShardRegion`。区域（`region`）将确认此请求并将`Shard` 监督者创建为子 Actor。然后，当`Shard` Actor 需要时，将创建各个`Entities`。因此，传入消息通过`ShardRegion`和`Shard`传输到目标`Entity`。

如果`shard home`是另一个`ShardRegion`实例，则消息将转发到该`ShardRegion`实例。当解析分片的位置时，该分片的传入消息将被缓冲，并在分片所在地（`home`）已知时传递。到已解析分片的后续消息可以立即传递到目标目的地，而不涉及`ShardCoordinator`。

### 场景

一旦知道`Shard`的位置，`ShardRegions`就直接发送消息。下面是进入此状态的场景。在场景中，使用以下符号：

- `SC` - `ShardCoordinator`
- `M#` - `Message` 1, 2, 3, 等
- `SR#` - `ShardRegion` 1, 2 3, 等
- `S#` - `Shard` 1 2 3, 等
- `E#` - `Entity` 1 2 3, 等，实体是指由集群分片管理的 Actor。

#### 场景1：向属于本地 ShardRegion 的未知分片发送消息

1. 传入消息`M1`到`ShardRegion`实例`SR1`。
2. `M1`映射到分片`S1`。`SR1`不知道`S1`，所以它向`SC`询问`S1`的位置。
3. `SC`回答`S1`的位置是`SR1`。
4. `R1`为实体`E1`创建子 Actor，并将`S1`的缓冲消息发送给`E1`子 Actor。
5. 到达`R1`的`S1`的所有传入消息都可以由`R1`处理，而不需要`SC`。它根据需要创建实体子级，并将消息转发给它们。

#### 场景2：向属于远程 ShardRegion 的未知分片发送消息

1. 传入消息`M2`到`ShardRegion`实例`SR1`。
2. `M2`映射到`S2`。`SR1`不知道`S2`，所以它向`SC`询问`S2`的位置。
3. `SC`回答`S2`的位置是`SR2`。
4. `SR1`将`S2`的缓冲消息发送到`SR2`。
5. 到达`SR1`的`S2`的所有传入消息都可以由`SR1`处理，而不需要`SC`。它将消息转发到`SR2`。
6. `SR2`接收到`S2`的消息，询问`SC`，`SC`回答`S2`的位置是`SR2`，这时我们将回到`场景1`中（但`SR2`除外）。

### 分片位置

为了确保特定实体 Actor 的至多一个实例在集群中的某个地方运行，所有节点都具有相同的分片（`shard`）所在位置视图是很重要的。因此，分片分配决策由中心`ShardCoordinator`执行，它作为一个集群单例运行，即在所有集群节点中的最老成员上或标记有特定角色的一组节点上执行一个实例。

决定分片位置的逻辑在可插拔分片分配策略中定义。默认实现`ShardCoordinator.LeastShardAllocationStrategy`将新的分片分配给`ShardRegion`，其中以前分配的分片数量最少。此策略可以由特定于应用程序的实现替代。

### 分片再平衡

为了能够在集群中使用新添加的成员，协调器（`coordinator`）促进了分片的重新平衡（`rebalancing of shards`），即将实体从一个节点迁移到另一个节点。在重新平衡过程中，协调器首先通知所有`ShardRegion` Actor 已开始对分片的切换。这意味着它们将开始缓冲该分片的传入消息，就像分片位置未知一样。在重新平衡过程中，协调器不会回答任何有关正在重新平衡的分片位置的请求，即本地缓冲将继续，直到完成切换。负责重新平衡分片的`ShardRegion`将通过向该分片中的所有实体发送指定的`stopMessage`（默认为`PoisonPill`）来停止该分片中的所有实体。所有实体终止后，拥有实体的`ShardRegion`将确认已向协调器完成移交。此后，协调器将回复分片位置的请求，从而为分片分配一个新的位置，然后将分片区域 Actor 中的缓冲消息发送到新位置。这意味着实体的状态不会被转移或迁移。如果实体的状态很重要，那么它应该是持久的，例如「[Persistence](https://doc.akka.io/docs/akka/current/persistence.html)」，以便可以在新的位置恢复。

决定要重新平衡哪些分片的逻辑在可插入分片分配策略（`a pluggable shard allocation strategy`）中定义。默认实现`ShardCoordinator.LeastShardAllocationStrategy`从`ShardRegion`中选择用于切换的分片，其中包含以前分配的大多数碎片。然后，它们将以最少数量的先前分配的分片（即集群中的新成员）分配给`ShardRegion`。

对于`LeastShardAllocationStrategy`，有一个可配置的阈值（`rebalance-threshold`），说明开始重新平衡时差异必须有多大。在分片最多的区域和分片最少的区域中，分片数量的差异必须大于发生重新平衡的`rebalance-threshold`。

当`rebalance-threshold`为`1`时，给出了最佳分布，因此通常是最佳选择。更高的阈值意味着更多的分片可以同时重新平衡，而不是一个接一个。这样做的优点是，重新平衡过程可以更快，但缺点是不同节点之间的分片数量（因此负载）可能会显著不同。

### ShardCoordinator 状态
`ShardCoordinator`中分片位置的状态是持久的，带有「[Distributed Data](https://doc.akka.io/docs/akka/current/distributed-data.html)」或「[Persistence](https://doc.akka.io/docs/akka/current/persistence.html)」，可以在故障中幸存。当从集群中删除崩溃或无法访问的协调节点（通过`down`）时，新的`ShardCoordinator`单例 Actor 将接管并恢复状态。在这种故障期间，具有已知位置的分片仍然可用，而新（未知）分片的消息将被缓冲，直到新的`ShardCoordinator`可用。

### 消息排序

只要发送者使用同一个`ShardRegion` Actor 将消息传递给实体 Actor，消息的顺序就会保持不变。只要没有达到缓冲区限制，消息就会以“最多一次传递”的语义尽最大努力传递，与普通消息发送的方式相同。可靠的端到端（`end-to-end`）消息传递，通过在「[Persistence](https://doc.akka.io/docs/akka/current/persistence.html)」中使用`AtLeastOnceDelivery`，可以实现“至少一次传递”的语义。

### 开销

由于到协调器的往返（`round-trip`），针对新的或以前未使用的分片的消息引入了一些额外的延迟。重新平衡分片也可能增加延迟。在设计特定于应用程序的分片解决方案时，应该考虑这一点，例如，为了避免太细的分片。一旦知道分片的位置，唯一的开销（`overhead`）就是通过`ShardRegion`发送消息，而不是直接发送消息。

##  分布式数据模式 vs. 持久化模式
协调器的状态和分片「[Remembering Entities](https://doc.akka.io/docs/akka/current/cluster-sharding.html#cluster-sharding-remembering)」的状态是持久的，可以在失败中幸存。「[Distributed Data](https://doc.akka.io/docs/akka/current/distributed-data.html)」或「[Persistence](https://doc.akka.io/docs/akka/current/persistence.html)」可用于存储。默认情况下使用分布式数据（`Distributed Data`）。

使用两种模式时的功能相同。如果你的分片实体本身不使用 Akka 持久化（`Persistence`），那么使用分布式数据模式更方便，因为你不必为持久性设置和操作单独的数据存储（如 Cassandra）。除此之外，使用一种模式而不使用另一种模式没有主要原因。

在集群中的所有节点上使用相同的模式很重要，即不可能执行滚动升级来更改此设置。

### 分布式数据模式

此模式通过配置启用（默认情况下启用）：

```
akka.cluster.sharding.state-store-mode = ddata
```

`ShardCoordinator`的状态将在集群内由分布式数据模块复制，具有`WriteMajority/ReadMajority`一致性。协调器的状态不持久，它没有存储到磁盘。当集群中的所有节点都已停止时，状态将丢失，也不再需要了。

记忆实体（`Remembering Entities`）的状态也是持久的，即存储在磁盘上。存储的实体也会在群集完全重新启动后启动。

集群分片（`Cluster Sharding`）使用它自己的每个节点角色的分布式数据`Replicator`。通过这种方式，可以将所有节点的子集用于某些实体类型，将另一个子集用于其他实体类型。每个这样的复制器（`replicator`）都有一个包含节点角色的名称，因此集群中所有节点上的角色配置都必须相同，即在执行滚动升级时不能更改角色。

分布式数据的设置在`akka.cluster.sharding.distributed-data`部分中配置。对于不同的分片实体类型，不可能有不同的`distributed-data`设置。

### 持久化模式
此模式通过配置启用：

```
akka.cluster.sharding.state-store-mode = persistence
```

因为它是在集群中运行的，所以必须用分布式日志配置持久化。

## 达到最少成员数后启动

在集群设置`akka.cluster.min-nr-of-members`或`akka.cluster.role.<role-name>.min-nr-of-members`时，使用集群分片是很好的。这将推迟分片的分配，直到至少有配置数量的区域已经启动并注册到协调器。这就避免了许多分片被分配到第一个注册的区域，只有在以后才被重新平衡到其他节点。

有关`min-nr-of-members`的详细信息，请参阅「[How To Startup when Cluster Size Reached](https://doc.akka.io/docs/akka/current/cluster-usage.html#min-members)」。

## 仅代理模式

`ShardRegion` Actor 也可以在仅代理模式（`proxy only mode`）下启动，即它不会承载任何实体本身，但知道如何将消息委托到正确的位置。`ShardRegion`以仅代理模式使用`ClusterSharding.startProxy`方法启动。此外，如果当前群集节点的角色与传递给`ClusterSharding.start`方法的`ClusterShardingSettings`中指定的角色不匹配时，则`ShardRegion`将以仅代理模式启动。

## Passivation

如果实体的状态是持久的，则可以停止不用于减少内存消耗的实体。这是由实体 Actor 的特定于应用程序的实现完成的，例如通过定义接收超时（`context.setReceiveTimeout`）。如果某个消息在停止时已排队到该实体，则将删除邮箱中排队的消息。为了在不丢失此类消息的情况下支持优雅的钝化（`passivation`），实体 Actor 可以将`ShardRegion.Passivate`发送给其父`Shard`。在`Passivate`中指定的包装消息将被发送回实体，然后该实体将自行停止。在接收到`Passivate`和终止实体之间，传入消息将被`Shard`缓冲。这样的缓冲消息随后被传递到实体的新化身。

### Automatic Passivation

如果实体使用`akka.cluster.sharding.passivate-idle-entity-after`设置一段时间没有收到消息，或者通过将`ClusterShardingSettings.passivateIdleEntityAfter`显式设置为一个合适的时间以保持 Actor 活动，则可以将这些实体配置为自动钝化（`automatically passivated`）。请注意，只有通过分片发送的消息才会被计算在内，因此直接发送到 Actor 的`ActorRef`的消息或它发送给自身的消息不会被计算为活动。默认情况下，自动钝化是禁止的。

## Remembering Entities
通过在调用`ClusterSharding.start`时将`ClusterShardingSettings`中的`rememberEntities`标志设置为`true`，并确保`shardIdExtractor`处理`Shard.StartEntity(EntityId)`，可以使每个`Shard`中的实体列表持久化，这意味着`ShardId`必须可以从`EntityId`中提取。

```java
@Override
public String shardId(Object message) {
  int numberOfShards = 100;
  if (message instanceof Counter.EntityEnvelope) {
    long id = ((Counter.EntityEnvelope) message).id;
    return String.valueOf(id % numberOfShards);
  } else if (message instanceof Counter.Get) {
    long id = ((Counter.Get) message).counterId;
    return String.valueOf(id % numberOfShards);
  } else if (message instanceof ShardRegion.StartEntity) {
    long id = Long.valueOf(((ShardRegion.StartEntity) message).entityId());
    return String.valueOf(id % numberOfShards);
  } else {
    return null;
  }
}
```
当配置为记忆实体（`remember entities`）时，每当`Shard`重新平衡到另一个节点上或在崩溃后恢复时，它将重新创建以前在该分片中运行的所有实体。要永久停止实体，必须向实体 Actor 的父级发送一条`Passivate`消息，否则在配置中指定的实体重新启动回退之后，该实体将自动重新启动。

当使用分布式数据模式时，实体的标识符存储在分布式数据的「[Durable Storage](https://doc.akka.io/docs/akka/current/distributed-data.html#ddata-durable)」中。你可能需要更改`akka.cluster.sharding.distributed-data.durable.lmdb.dir`的配置，因为默认目录包含 Actor 系统的远程端口。如果使用动态分配的端口（`0`），则每次都会不同，并且不会加载以前存储的数据。

当`rememberEntities`设置为`false`时，`Shard`不会在重新平衡或从崩溃中恢复后自动重新启动任何实体。只有在`Shard`中收到实体的第一条消息后，才会启动实体。如果实体停止而不使用`Passivate`，则不会重新启动。

请注意，实体本身的状态将不会被恢复，除非它们已被持久化，例如「[Persistence](https://doc.akka.io/docs/akka/current/persistence.html)」。

当启动/停止实体以及重新平衡分片时，`rememberEntities`的性能成本相当高。这种成本随着每个分片的实体数量增加而增加，我们目前不建议在每个分片上使用超过 10000 个实体。

## 监督

如果需要为实体 Actor 使用其他`supervisorStrategy`，而不是默认（重新启动）策略，则需要创建一个中间父 Actor，该 Actor 定义子实体 Actor 的`supervisorStrategy`。

```java
static class CounterSupervisor extends AbstractActor {

  private final ActorRef counter =
      getContext().actorOf(Props.create(Counter.class), "theCounter");

  private static final SupervisorStrategy strategy =
      new OneForOneStrategy(
          DeciderBuilder.match(IllegalArgumentException.class, e -> SupervisorStrategy.resume())
              .match(ActorInitializationException.class, e -> SupervisorStrategy.stop())
              .match(Exception.class, e -> SupervisorStrategy.restart())
              .matchAny(o -> SupervisorStrategy.escalate())
              .build());

  @Override
  public SupervisorStrategy supervisorStrategy() {
    return strategy;
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(Object.class, msg -> counter.forward(msg, getContext()))
        .build();
  }
}
```

你以同样的方式启动这样一个监督者（`supervisor`），就像它是实体 Actor 一样。

```java
ClusterSharding.get(system)
    .start(
        "SupervisedCounter", Props.create(CounterSupervisor.class), settings, messageExtractor);
```

请注意，当新消息针对（`targeted to`）实体时，停止的实体将再次启动。

## 优雅地关闭

你可以将`ShardRegion.gracefulShutdownInstance`消息发送给`ShardRegion` Actor，以分发由该`ShardRegion`承载的所有分片，然后将停止`ShardRegion` Actor。你可以监控（`watch`）`ShardRegion` Actor 以便知道什么时候完成。在此期间，其他区域将以协调器触发重新平衡时的相同方式缓冲这些分片的消息。当分片被停止时，协调器将把这些分片分配到其他地方。

这是由「[Coordinated Shutdown](https://doc.akka.io/docs/akka/current/actors.html#coordinated-shutdown)」自动执行的，因此是集群成员正常退出进程的一部分。

## 删除内部群集分片数据

集群分片协调器使用 Akka 持久化存储分片的位置。重新启动整个 Akka 集群时，可以安全地删除这些数据。请注意，这不是应用程序数据。

有一个实用程序`akka.cluster.sharding.RemoveInternalClusterShardingData`，用于删除此数据。

- **警告**：在运行使用群集分片的 Akka 群集节点时，切勿使用此程序。使用此程序前，请停止所有群集节点。


如果由于数据损坏而无法启动群集分片协调器，则可能需要删除数据，如果同时意外运行两个群集，例如由于使用自动关闭而存在网络分裂，则可能会发生这种情况。

- **警告**：不要将集群分片（`Cluster Sharding`）与自动关闭（`Automatic Downing`）一起使用，因为它允许集群分裂为两个单独的集群，从而导致多个分片和实体启动。


使用这个程序作为一个独立的 Java 主程序：

```
java -classpath <jar files, including akka-cluster-sharding>
  akka.cluster.sharding.RemoveInternalClusterShardingData
    -2.3 entityType1 entityType2 entityType3
```

该程序包含在`akka-cluster-sharding.jar` 文件中。使用与普通应用程序相同的类路径和配置运行它是最简单的。它可以以类似的方式从 sbt 或 Maven 运行。

指定实体类型名称（与在`ClusterSharding`的`start`方法中使用的名称相同）作为程序参数。

如果将`-2.3`指定为第一个程序参数，它还将尝试使用不同的`persistenceId`删除在`Akka 2.3.x`中由集群分片（`Cluster Sharding`）存储的数据。

## 配置

可以使用以下属性配置`ClusterSharding`扩展。当使用`ActorSystem`参数创建时，`ClusterShardingSettings`将读取这些配置属性。还可以修改`ClusterShardingSettings`或从另一个配置部分创建它，布局如下。`ClusterShardingSettings`是`ClusterSharding`扩展的`start`方法的参数，也就是说，如果需要，每个实体类型都可以配置不同的设置。

```xml
# Settings for the ClusterShardingExtension
akka.cluster.sharding {

  # The extension creates a top level actor with this name in top level system scope,
  # e.g. '/system/sharding'
  guardian-name = sharding

  # Specifies that entities runs on cluster nodes with a specific role.
  # If the role is not specified (or empty) all nodes in the cluster are used.
  role = ""

  # When this is set to 'on' the active entity actors will automatically be restarted
  # upon Shard restart. i.e. if the Shard is started on a different ShardRegion
  # due to rebalance or crash.
  remember-entities = off

  # Set this to a time duration to have sharding passivate entities when they have not
  # gotten any message in this long time. Set to 'off' to disable.
  passivate-idle-entity-after = off

  # If the coordinator can't store state changes it will be stopped
  # and started again after this duration, with an exponential back-off
  # of up to 5 times this duration.
  coordinator-failure-backoff = 5 s

  # The ShardRegion retries registration and shard location requests to the
  # ShardCoordinator with this interval if it does not reply.
  retry-interval = 2 s

  # Maximum number of messages that are buffered by a ShardRegion actor.
  buffer-size = 100000

  # Timeout of the shard rebalancing process.
  # Additionally, if an entity doesn't handle the stopMessage
  # after (handoff-timeout - 5.seconds).max(1.second) it will be stopped forcefully
  handoff-timeout = 60 s

  # Time given to a region to acknowledge it's hosting a shard.
  shard-start-timeout = 10 s

  # If the shard is remembering entities and can't store state changes
  # will be stopped and then started again after this duration. Any messages
  # sent to an affected entity may be lost in this process.
  shard-failure-backoff = 10 s

  # If the shard is remembering entities and an entity stops itself without
  # using passivate. The entity will be restarted after this duration or when
  # the next message for it is received, which ever occurs first.
  entity-restart-backoff = 10 s

  # Rebalance check is performed periodically with this interval.
  rebalance-interval = 10 s

  # Absolute path to the journal plugin configuration entity that is to be
  # used for the internal persistence of ClusterSharding. If not defined
  # the default journal plugin is used. Note that this is not related to
  # persistence used by the entity actors.
  # Only used when state-store-mode=persistence
  journal-plugin-id = ""

  # Absolute path to the snapshot plugin configuration entity that is to be
  # used for the internal persistence of ClusterSharding. If not defined
  # the default snapshot plugin is used. Note that this is not related to
  # persistence used by the entity actors.
  # Only used when state-store-mode=persistence
  snapshot-plugin-id = ""

  # Defines how the coordinator stores its state. Same is also used by the
  # shards for rememberEntities.
  # Valid values are "ddata" or "persistence". 
  state-store-mode = "ddata"

  # The shard saves persistent snapshots after this number of persistent
  # events. Snapshots are used to reduce recovery times.
  # Only used when state-store-mode=persistence
  snapshot-after = 1000

  # The shard deletes persistent events (messages and snapshots) after doing snapshot
  # keeping this number of old persistent batches.
  # Batch is of size `snapshot-after`.
  # When set to 0 after snapshot is successfully done all messages with equal or lower sequence number will be deleted.
  # Default value of 2 leaves last maximum 2*`snapshot-after` messages and 3 snapshots (2 old ones + fresh snapshot)
  keep-nr-of-batches = 2

  # Setting for the default shard allocation strategy
  least-shard-allocation-strategy {
    # Threshold of how large the difference between most and least number of
    # allocated shards must be to begin the rebalancing.
    # The difference between number of shards in the region with most shards and
    # the region with least shards must be greater than (>) the `rebalanceThreshold`
    # for the rebalance to occur.
    # 1 gives the best distribution and therefore typically the best choice.
    # Increasing the threshold can result in quicker rebalance but has the
    # drawback of increased difference between number of shards (and therefore load)
    # on different nodes before rebalance will occur.
    rebalance-threshold = 1

    # The number of ongoing rebalancing processes is limited to this number.
    max-simultaneous-rebalance = 3
  }

  # Timeout of waiting the initial distributed state (an initial state will be queried again if the timeout happened)
  # Only used when state-store-mode=ddata
  waiting-for-state-timeout = 5 s

  # Timeout of waiting for update the distributed state (update will be retried if the timeout happened)
  # Only used when state-store-mode=ddata
  updating-state-timeout = 5 s

  # The shard uses this strategy to determines how to recover the underlying entity actors. The strategy is only used
  # by the persistent shard when rebalancing or restarting. The value can either be "all" or "constant". The "all"
  # strategy start all the underlying entity actors at the same time. The constant strategy will start the underlying
  # entity actors at a fix rate. The default strategy "all".
  entity-recovery-strategy = "all"

  # Default settings for the constant rate entity recovery strategy
  entity-recovery-constant-rate-strategy {
    # Sets the frequency at which a batch of entity actors is started.
    frequency = 100 ms
    # Sets the number of entity actors to be restart at a particular interval
    number-of-entities = 5
  }

  # Settings for the coordinator singleton. Same layout as akka.cluster.singleton.
  # The "role" of the singleton configuration is not used. The singleton role will
  # be the same as "akka.cluster.sharding.role".
  coordinator-singleton = ${akka.cluster.singleton}
  
  # Settings for the Distributed Data replicator. 
  # Same layout as akka.cluster.distributed-data.
  # The "role" of the distributed-data configuration is not used. The distributed-data
  # role will be the same as "akka.cluster.sharding.role".
  # Note that there is one Replicator per role and it's not possible
  # to have different distributed-data settings for different sharding entity types.
  # Only used when state-store-mode=ddata
  distributed-data = ${akka.cluster.distributed-data}
  distributed-data {
    # minCap parameter to MajorityWrite and MajorityRead consistency level.
    majority-min-cap = 5
    durable.keys = ["shard-*"]
    
    # When using many entities with "remember entities" the Gossip message
    # can become to large if including to many in same message. Limit to
    # the same number as the number of ORSet per shard.
    max-delta-elements = 5
    
  }

  # The id of the dispatcher to use for ClusterSharding actors.
  # If not specified default dispatcher is used.
  # If specified you need to define the settings of the actual dispatcher.
  # This dispatcher for the entity actors is defined by the user provided
  # Props, i.e. this dispatcher is not used for the entity actors.
  use-dispatcher = ""
}
```

自定义分片分配策略（`shard allocation strategy`）可以在`ClusterSharding.start`的可选参数中定义。有关如何实现自定义分片分配策略的详细信息，请参阅`AbstractShardAllocationStrategy`的 API 文档。

## 检查群集分片状态

有两个检查群集状态的请求可用：

- `ShardRegion.getShardRegionStateInstance`，它将返回一个`ShardRegion.ShardRegionState`，其中包含区域中运行的分片的标识符以及每个分片的活动实体。
- `ShardRegion.GetClusterShardingStats`，它将查询集群中的所有区域，并返回一个`ShardRegion.ClusterShardingStats`，其中包含每个区域中运行的分片的标识符以及每个分片中活动的实体数。

可以通过`ClusterSharding.getShardTypeNames`获取所有已启动分片的类型名。

这些消息的目的是测试（`testing`）和监控（`monitoring`），它们不提供直接向各个实体发送消息的访问权。

## 滚动升级

在进行滚动升级（`rolling upgrades`）时，必须特别注意不要改变以下任何分片方面：

- `extractShardId`函数
- 分片区域运行的角色
- 持久化模式

如果其中任何一个需要更改，则需要完全重新启动群集。


----------

**英文原文链接**：[Cluster Sharding](https://doc.akka.io/docs/akka/current/cluster-sharding.html).


----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————