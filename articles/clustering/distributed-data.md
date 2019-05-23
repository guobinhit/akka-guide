# 分布式数据
## 依赖

为了使用分布式数据（`Distributed Data`），你需要将以下依赖添加到你的项目中：

```xml
<!-- Maven -->
<dependency>
  <groupId>com.typesafe.akka</groupId>
  <artifactId>akka-distributed-data_2.12</artifactId>
  <version>2.5.22</version>
</dependency>

<!-- Gradle -->
dependencies {
  compile group: 'com.typesafe.akka', name: 'akka-distributed-data_2.12', version: '2.5.22'
}

<!-- sbt -->
libraryDependencies += "com.typesafe.akka" %% "akka-distributed-data" % "2.5.22"
```

## 示例项目

你可以下载「[Distributed Data](https://developer.lightbend.com/start/?group=akka&project=akka-samples-distributed-data-java)」示例项目来看看分布式数据是如何在实践中应用的。

## 简介

当需要在 Akka 集群中的节点之间共享数据时，Akka 分布式数据非常有用。通过提供类似 API 的键值存储的 Actor 访问数据。键是具有数据值类型信息的唯一标识符。这些值是无冲突的复制数据类型（`Conflict Free Replicated Data Types (CRDTs)`）。

所有数据条目都通过直接复制和基于`gossip`的协议传播到集群中的所有节点或具有特定角色的节点。你可以对读写的一致性级别进行细粒度控制。

自然`CRDTs`可以在不协调的情况下从任何节点执行更新。来自不同节点的并发更新将由单调合并函数（`monotonic merge function`）自动解决，所有数据类型都必须提供该函数。状态变化总是收敛的。为计数器、集合、映射和寄存器提供了几种有用的数据类型，你还可以实现自己的自定义数据类型。

它最终是一致的，旨在提供高读写可用性（分区容限），低延迟。请注意，在最终一致的系统中，读取可能会返回过期的值。

## 使用 Replicator

`akka.cluster.ddata.Replicator` Actor 提供了与数据交互的 API。`Replicator` Actor 必须在集群中的每个节点上启动，或者在标记有特定角色的节点组上启动。它与运行在其他节点上的具有相同路径（而不是地址）的其他`Replicator`实例通信。为了方便起见，它可以与`akka.cluster.ddata.DistributedData`扩展一起使用，但也可以使用`Replicator.props`作为普通 Actor 启动。如果它是作为一个普通的 Actor 启动的，那么它必须在所有节点上以相同的名称、相同的路径启动。

状态为「[WeaklyUp](https://doc.akka.io/docs/akka/current/cluster-usage.html#weakly-up)」的集群成员将参与分布式数据。这意味着数据将通过后台`gossip`协议复制到`WeaklyUp`节点。请注意，如果一致性模式是从所有节点或大多数节点读/写，则它不会参与任何操作。`WeaklyUp`节点不算作集群的一部分。因此，就一致操作而言，3 个节点 + 5 个`WeaklyUp`的节点本质上是 3 个节点。

下面是一个 Actor 的示例，它将`tick`消息调度到自己，并为每个`tick`添加或删除`ORSet`（`observed-remove set`）中的元素。它还订阅了这一变化。

```java
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.cluster.Cluster;
import akka.cluster.ddata.DistributedData;
import akka.cluster.ddata.Key;
import akka.cluster.ddata.ORSet;
import akka.cluster.ddata.ORSetKey;
import akka.cluster.ddata.Replicator;
import akka.cluster.ddata.Replicator.Changed;
import akka.cluster.ddata.Replicator.Subscribe;
import akka.cluster.ddata.Replicator.Update;
import akka.cluster.ddata.Replicator.UpdateResponse;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class DataBot extends AbstractActor {

  private static final String TICK = "tick";

  private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  private final ActorRef replicator = DistributedData.get(getContext().getSystem()).replicator();
  private final Cluster node = Cluster.get(getContext().getSystem());

  private final Cancellable tickTask =
      getContext()
          .getSystem()
          .scheduler()
          .schedule(
              Duration.ofSeconds(5),
              Duration.ofSeconds(5),
              getSelf(),
              TICK,
              getContext().getDispatcher(),
              getSelf());

  private final Key<ORSet<String>> dataKey = ORSetKey.create("key");

  @SuppressWarnings("unchecked")
  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(String.class, a -> a.equals(TICK), a -> receiveTick())
        .match(
            Changed.class,
            c -> c.key().equals(dataKey),
            c -> receiveChanged((Changed<ORSet<String>>) c))
        .match(UpdateResponse.class, r -> receiveUpdateResponse())
        .build();
  }

  private void receiveTick() {
    String s = String.valueOf((char) ThreadLocalRandom.current().nextInt(97, 123));
    if (ThreadLocalRandom.current().nextBoolean()) {
      // add
      log.info("Adding: {}", s);
      Update<ORSet<String>> update =
          new Update<>(dataKey, ORSet.create(), Replicator.writeLocal(), curr -> curr.add(node, s));
      replicator.tell(update, getSelf());
    } else {
      // remove
      log.info("Removing: {}", s);
      Update<ORSet<String>> update =
          new Update<>(
              dataKey, ORSet.create(), Replicator.writeLocal(), curr -> curr.remove(node, s));
      replicator.tell(update, getSelf());
    }
  }

  private void receiveChanged(Changed<ORSet<String>> c) {
    ORSet<String> data = c.dataValue();
    log.info("Current elements: {}", data.getElements());
  }

  private void receiveUpdateResponse() {
    // ignore
  }

  @Override
  public void preStart() {
    Subscribe<ORSet<String>> subscribe = new Subscribe<>(dataKey, getSelf());
    replicator.tell(subscribe, ActorRef.noSender());
  }

  @Override
  public void postStop() {
    tickTask.cancel();
  }
}
```

### 更新

若要修改和复制数据值，请向本地`Replicator`发送一条`Replicator.Update`消息。

`Update`的`key`的当前数据值作为参数传递给`Update`的`modify`函数。函数应该返回数据的新值，然后根据给定的一致性级别复制该值。

`modify`函数由`Replicator` Actor 调用，因此必须是一个纯函数，只使用封闭范围中的数据参数和稳定字段。例如，它必须不访问封闭 Actor 的发送方（`getSender()`）引用。

由于`modify`函数通常不可序列化，因此只能从与`Replicator`运行在同一本地`ActorSystem`中的 Actor 发送`Update`。

你提供的写入一致性级别具有以下含义：

- `writeLocal`，该值将立即只被写入本地副本，然后通过`gossip`进行传播。
- `WriteTo(n)`，该值将立即写入至少`n`个副本，包括本地副本
- `WriteMajority`，该值将立即写入大多数副本，即至少`N/2 + 1`个副本，其中`N`是群集（或群集角色组）中的节点数
- `WriteAll`，该值将立即写入群集中的所有节点（或群集中角色组中的所有节点）。

当你指定在`x`个节点中写入`n`个节点时，更新将首先复制到`n`个节点。如果在超时的`1/5`之后没有足够的`Acks`，更新将复制到其他`n`个节点。如果剩余节点少于`n`个，则使用所有剩余节点。可访问节点比不可访问节点更受欢迎。

请注意，`WriteMajority`有一个`minCap`参数，该参数对于指定小集群以实现更好的安全性非常有用。

```java
class DemonstrateUpdate extends AbstractActor {
  final SelfUniqueAddress node =
      DistributedData.get(getContext().getSystem()).selfUniqueAddress();
  final ActorRef replicator = DistributedData.get(getContext().getSystem()).replicator();

  final Key<PNCounter> counter1Key = PNCounterKey.create("counter1");
  final Key<GSet<String>> set1Key = GSetKey.create("set1");
  final Key<ORSet<String>> set2Key = ORSetKey.create("set2");
  final Key<Flag> activeFlagKey = FlagKey.create("active");

  @Override
  public Receive createReceive() {
    ReceiveBuilder b = receiveBuilder();

    b.matchEquals(
        "demonstrate update",
        msg -> {
          replicator.tell(
              new Replicator.Update<PNCounter>(
                  counter1Key,
                  PNCounter.create(),
                  Replicator.writeLocal(),
                  curr -> curr.increment(node, 1)),
              getSelf());

          final WriteConsistency writeTo3 = new WriteTo(3, Duration.ofSeconds(1));
          replicator.tell(
              new Replicator.Update<GSet<String>>(
                  set1Key, GSet.create(), writeTo3, curr -> curr.add("hello")),
              getSelf());

          final WriteConsistency writeMajority = new WriteMajority(Duration.ofSeconds(5));
          replicator.tell(
              new Replicator.Update<ORSet<String>>(
                  set2Key, ORSet.create(), writeMajority, curr -> curr.add(node, "hello")),
              getSelf());

          final WriteConsistency writeAll = new WriteAll(Duration.ofSeconds(5));
          replicator.tell(
              new Replicator.Update<Flag>(
                  activeFlagKey, Flag.create(), writeAll, curr -> curr.switchOn()),
              getSelf());
        });
    return b.build();
  }
}
```

作为`Update`的答复，如果在提供的超时内根据提供的一致性级别成功复制了值，则会向`Update`的发送方发送`Replicator.UpdateSuccess`。否则将返回`Replicator.UpdateFailure`子类。请注意，`Replicator.UpdateTimeout`回复并不意味着更新完全失败或已回滚。它可能仍然被复制到一些节点上，并最终通过`gossip`协议复制到所有节点上。

```java
b.match(
    UpdateSuccess.class,
    a -> a.key().equals(counter1Key),
    a -> {
      // ok
    });
```

```java
b.match(
        UpdateSuccess.class,
        a -> a.key().equals(set1Key),
        a -> {
          // ok
        })
    .match(
        UpdateTimeout.class,
        a -> a.key().equals(set1Key),
        a -> {
          // write to 3 nodes failed within 1.second
        });
```

你总会看到自己的写入。例如，如果发送两条`Update`消息更改同一个`key`的值，则第二条消息的`modify`函数将看到第一条`Update`消息执行的更改。

在`Update`消息中，你可以传递一个可选的请求上下文，`Replicator`不关心该上下文，但它包含在回复消息中。这是一种传递上下文信息（例如原始发送者）的方便方法，无需使用`ask`或维护本地相关数据结构。

```java
class DemonstrateUpdateWithRequestContext extends AbstractActor {
  final Cluster node = Cluster.get(getContext().getSystem());
  final ActorRef replicator = DistributedData.get(getContext().getSystem()).replicator();

  final WriteConsistency writeTwo = new WriteTo(2, Duration.ofSeconds(3));
  final Key<PNCounter> counter1Key = PNCounterKey.create("counter1");

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            String.class,
            a -> a.equals("increment"),
            a -> {
              // incoming command to increase the counter
              Optional<Object> reqContext = Optional.of(getSender());
              Replicator.Update<PNCounter> upd =
                  new Replicator.Update<PNCounter>(
                      counter1Key,
                      PNCounter.create(),
                      writeTwo,
                      reqContext,
                      curr -> curr.increment(node, 1));
              replicator.tell(upd, getSelf());
            })
        .match(
            UpdateSuccess.class,
            a -> a.key().equals(counter1Key),
            a -> {
              ActorRef replyTo = (ActorRef) a.getRequest().get();
              replyTo.tell("ack", getSelf());
            })
        .match(
            UpdateTimeout.class,
            a -> a.key().equals(counter1Key),
            a -> {
              ActorRef replyTo = (ActorRef) a.getRequest().get();
              replyTo.tell("nack", getSelf());
            })
        .build();
  }
}
```

### 获取

要检索数据的当前值，请向`Replicator`发生`Replicator.Get`消息。你提供的一致性级别具有以下含义：

- `readLocal`，该值将只从本地副本中读取
- `ReadFrom(n)`，该值将从`n`个副本（包括本地副本）中读取和合并
- `ReadMajority`，该值将从大多数副本（即至少`N/2 + 1`个副本）中读取和合并，其中`N`是集群（或集群角色组）中的节点数
- `ReadAll`，该值将从群集中的所有节点（或群集角色组中的所有节点）中读取和合并。

请注意，`ReadMajority`有一个`minCap`参数，该参数对于指定小集群以获得更好的安全性非常有用。

```java
class DemonstrateGet extends AbstractActor {
  final ActorRef replicator = DistributedData.get(getContext().getSystem()).replicator();

  final Key<PNCounter> counter1Key = PNCounterKey.create("counter1");
  final Key<GSet<String>> set1Key = GSetKey.create("set1");
  final Key<ORSet<String>> set2Key = ORSetKey.create("set2");
  final Key<Flag> activeFlagKey = FlagKey.create("active");

  @Override
  public Receive createReceive() {
    ReceiveBuilder b = receiveBuilder();

    b.matchEquals(
        "demonstrate get",
        msg -> {
          replicator.tell(
              new Replicator.Get<PNCounter>(counter1Key, Replicator.readLocal()), getSelf());

          final ReadConsistency readFrom3 = new ReadFrom(3, Duration.ofSeconds(1));
          replicator.tell(new Replicator.Get<GSet<String>>(set1Key, readFrom3), getSelf());

          final ReadConsistency readMajority = new ReadMajority(Duration.ofSeconds(5));
          replicator.tell(new Replicator.Get<ORSet<String>>(set2Key, readMajority), getSelf());

          final ReadConsistency readAll = new ReadAll(Duration.ofSeconds(5));
          replicator.tell(new Replicator.Get<Flag>(activeFlagKey, readAll), getSelf());
        });
    return b.build();
  }
}
```

作为`Get`的回复，如果在提供的超时内根据提供的一致性级别成功检索到值，则会向`Get`的发送方发送`Replicator.GetSuccess`。否则将发送`Replicator.GetFailure`。如果`key`不存在，则将发送`Replicator.NotFound`消息。

```java
b.match(
        GetSuccess.class,
        a -> a.key().equals(counter1Key),
        a -> {
          GetSuccess<PNCounter> g = a;
          BigInteger value = g.dataValue().getValue();
        })
    .match(
        NotFound.class,
        a -> a.key().equals(counter1Key),
        a -> {
          // key counter1 does not exist
        });
```

```java
b.match(
        GetSuccess.class,
        a -> a.key().equals(set1Key),
        a -> {
          GetSuccess<GSet<String>> g = a;
          Set<String> value = g.dataValue().getElements();
        })
    .match(
        GetFailure.class,
        a -> a.key().equals(set1Key),
        a -> {
          // read from 3 nodes failed within 1.second
        })
    .match(
        NotFound.class,
        a -> a.key().equals(set1Key),
        a -> {
          // key set1 does not exist
        });
```

你总是会读取自己写入的东西。例如，如果发送一条`Update`消息，后跟一个具有相同`key`的`Get`，则`Get`将检索由前面的`Update`消息执行的更改。但是，没有定义回复消息的顺序，即在上一个示例中，你可能会在`UpdateSuccess`之前收到`GetSuccess`。

在`Get`消息中，你可以通过与上述`Update`消息相同的方式传递可选的请求上下文。例如，在接收和转换``GetSuccess之后，可以传递和回复原始发送者。

```java
class DemonstrateGetWithRequestContext extends AbstractActor {
  final ActorRef replicator = DistributedData.get(getContext().getSystem()).replicator();

  final ReadConsistency readTwo = new ReadFrom(2, Duration.ofSeconds(3));
  final Key<PNCounter> counter1Key = PNCounterKey.create("counter1");

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            String.class,
            a -> a.equals("get-count"),
            a -> {
              // incoming request to retrieve current value of the counter
              Optional<Object> reqContext = Optional.of(getSender());
              replicator.tell(new Replicator.Get<PNCounter>(counter1Key, readTwo), getSelf());
            })
        .match(
            GetSuccess.class,
            a -> a.key().equals(counter1Key),
            a -> {
              ActorRef replyTo = (ActorRef) a.getRequest().get();
              GetSuccess<PNCounter> g = a;
              long value = g.dataValue().getValue().longValue();
              replyTo.tell(value, getSelf());
            })
        .match(
            GetFailure.class,
            a -> a.key().equals(counter1Key),
            a -> {
              ActorRef replyTo = (ActorRef) a.getRequest().get();
              replyTo.tell(-1L, getSelf());
            })
        .match(
            NotFound.class,
            a -> a.key().equals(counter1Key),
            a -> {
              ActorRef replyTo = (ActorRef) a.getRequest().get();
              replyTo.tell(0L, getSelf());
            })
        .build();
  }
}
```

### 一致性

`Update`和`Get`中提供的一致性级别指定每个请求必须成功响应写入和读取请求的副本数。

对于低延迟读取，使用`readLocal`会有检索过时数据的风险，也就是说，来自其他节点的更新可能还不可见。

使用`readLocal`时，更新仅写入本地副本，然后使用`gossip`协议在后台传播，传播到所有节点可能需要几秒钟。

当使用`readLocal`时，你将永远不会收到`GetFailure`响应，因为本地副本始终对本地读卡器可用。但是，如果`modify`函数引发异常，或者如果使用持久存储失败，则`WriteLocal`仍可以使用`UpdateFailure`消息进行答复。

`WriteAll`和`ReadAll`是最强的一致性级别，但也是最慢的，可用性最低。例如，一个节点对于`Get`请求不可用，你将不会收到该值。

如果一致性很重要，可以使用以下公式确保读取始终反映最新的写入：

```java
(nodes_written + nodes_read) > N
```

其中`N`是集群中节点的总数，或者具有`Replicator`所用角色的节点的数量。

例如，在 7 节点集群中，这些一致性属性是通过写入 4 个节点和读取 4 个节点，或写入 5 个节点和读取 3 个节点来实现的。

通过将`WriteMajority`和`ReadMajority`级别结合起来，读始终反映最新的写入。`Replicator`对大多数复本进行写入和读取，即`N / 2 + 1`。例如，在 5 节点集群中，它写入 3 个节点并读取 3 个节点。在 6 节点集群中，它写入 4 个节点并读取 4 个节点。

你可以为`WriteMajority`和`ReadMajority`定义最小数量的节点，这将最小化读取过时数据的风险。最小`cap`由`WriteMajority`和`ReadMajority`的`minCap`属性提供，并定义所需的多数。如果`minCap`高于`N / 2 + 1`，将使用`minCap`。

例如，如果`minCap`为 5，3 个节点的集群的`WriteMajority`和`ReadMajority`将为 3，6 个节点的集群将为 5，12 个节点的集群将为`7 ( N / 2 + 1 )`。

对于小集群（`<7`），在`WriteMajority`和`ReadMajority`之间成员状态更改的风险相当高，无法保证将多数写入和读取结合在一起的良好属性。因此，`WriteMajority`和`ReadMajority`有一个`minCap`参数，该参数对于指定小集群以实现更好的安全性非常有用。这意味着，如果集群大小小于大多数大小，它将使用`minCap`节点数，但最多使用集群的总大小。

下面是使用`WriteMajority`和`ReadMajority`的示例：

```java
private final WriteConsistency writeMajority = new WriteMajority(Duration.ofSeconds(3));
private static final ReadConsistency readMajority = new ReadMajority(Duration.ofSeconds(3));
```

```java
private Receive matchGetCart() {
  return receiveBuilder()
      .matchEquals(GET_CART, s -> receiveGetCart())
      .match(
          GetSuccess.class,
          this::isResponseToGetCart,
          g -> receiveGetSuccess((GetSuccess<LWWMap<String, LineItem>>) g))
      .match(
          NotFound.class,
          this::isResponseToGetCart,
          n -> receiveNotFound((NotFound<LWWMap<String, LineItem>>) n))
      .match(
          GetFailure.class,
          this::isResponseToGetCart,
          f -> receiveGetFailure((GetFailure<LWWMap<String, LineItem>>) f))
      .build();
}

private void receiveGetCart() {
  Optional<Object> ctx = Optional.of(getSender());
  replicator.tell(
      new Replicator.Get<LWWMap<String, LineItem>>(dataKey, readMajority, ctx), getSelf());
}

private boolean isResponseToGetCart(GetResponse<?> response) {
  return response.key().equals(dataKey)
      && (response.getRequest().orElse(null) instanceof ActorRef);
}

private void receiveGetSuccess(GetSuccess<LWWMap<String, LineItem>> g) {
  Set<LineItem> items = new HashSet<>(g.dataValue().getEntries().values());
  ActorRef replyTo = (ActorRef) g.getRequest().get();
  replyTo.tell(new Cart(items), getSelf());
}

private void receiveNotFound(NotFound<LWWMap<String, LineItem>> n) {
  ActorRef replyTo = (ActorRef) n.getRequest().get();
  replyTo.tell(new Cart(new HashSet<>()), getSelf());
}

private void receiveGetFailure(GetFailure<LWWMap<String, LineItem>> f) {
  // ReadMajority failure, try again with local read
  Optional<Object> ctx = Optional.of(getSender());
  replicator.tell(
      new Replicator.Get<LWWMap<String, LineItem>>(dataKey, Replicator.readLocal(), ctx),
      getSelf());
}
```

```java
private Receive matchAddItem() {
  return receiveBuilder().match(AddItem.class, this::receiveAddItem).build();
}

private void receiveAddItem(AddItem add) {
  Update<LWWMap<String, LineItem>> update =
      new Update<>(dataKey, LWWMap.create(), writeMajority, cart -> updateCart(cart, add.item));
  replicator.tell(update, getSelf());
}
```

在少数情况下，执行`Update`时，需要首先尝试从其他节点获取最新数据。这可以通过首先发送带有`ReadMajority`的`Get`，然后在收到`GetSuccess`、`GetFailure`或`NotFound`回复时继续`Update`来完成。当你需要根据最新信息做出决定，或者从`ORSet`或`ORMap`中删除条目时，可能需要这样做。如果一个条目从一个节点添加到`ORSet`或`ORMap`，并从另一个节点删除，则只有在执行删除的节点上看到添加的条目时，才会删除该条目（因此名称为已删除集）。

以下示例说明了如何执行此操作：

```java
private void receiveRemoveItem(RemoveItem rm) {
  // Try to fetch latest from a majority of nodes first, since ORMap
  // remove must have seen the item to be able to remove it.
  Optional<Object> ctx = Optional.of(rm);
  replicator.tell(
      new Replicator.Get<LWWMap<String, LineItem>>(dataKey, readMajority, ctx), getSelf());
}

private void receiveRemoveItemGetSuccess(GetSuccess<LWWMap<String, LineItem>> g) {
  RemoveItem rm = (RemoveItem) g.getRequest().get();
  removeItem(rm.productId);
}

private void receiveRemoveItemGetFailure(GetFailure<LWWMap<String, LineItem>> f) {
  // ReadMajority failed, fall back to best effort local value
  RemoveItem rm = (RemoveItem) f.getRequest().get();
  removeItem(rm.productId);
}

private void removeItem(String productId) {
  Update<LWWMap<String, LineItem>> update =
      new Update<>(dataKey, LWWMap.create(), writeMajority, cart -> cart.remove(node, productId));
  replicator.tell(update, getSelf());
}

private boolean isResponseToRemoveItem(GetResponse<?> response) {
  return response.key().equals(dataKey)
      && (response.getRequest().orElse(null) instanceof RemoveItem);
}
```

- **警告**：即使你使用了`WriteMajority`和`ReadMajority`，但是如果集群成员在`Update`和`Get`之间发生了更改，则也有读取过时数据的小风险。例如，在 5 个节点的集群中，当你`Update`并将更改写入 3 个节点时：`n1`、`n2`、`n3`。然后再添加 2 个节点，从 4 个节点读取一个`Get`请求，正好是`n4`、`n5`、`n6`、`n7`，也就是说，在`Get`请求的响应中看不到`n1`、`n2`、`n3`上的值。

### 订阅

你也可以通过向`Replicator`发送`Replicator.Subscribe`消息来订阅感兴趣的通知。它将在更新订阅键的数据时向注册订阅者发送`Replicator.Changed`消息。将使用配置的`notify-subscribers-interval`定期通知订阅者，还可以向`Replicator`发送显式`Replicator.FlushChange`消息以立即通知订阅者。

如果订阅者被终止，则会自动删除订阅者。订阅者也可以使用`Replicator.Unsubscribe`取消订阅消息。

```java
class DemonstrateSubscribe extends AbstractActor {
  final ActorRef replicator = DistributedData.get(getContext().getSystem()).replicator();
  final Key<PNCounter> counter1Key = PNCounterKey.create("counter1");

  BigInteger currentValue = BigInteger.valueOf(0);

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            Changed.class,
            a -> a.key().equals(counter1Key),
            a -> {
              Changed<PNCounter> g = a;
              currentValue = g.dataValue().getValue();
            })
        .match(
            String.class,
            a -> a.equals("get-count"),
            a -> {
              // incoming request to retrieve current value of the counter
              getSender().tell(currentValue, getSender());
            })
        .build();
  }

  @Override
  public void preStart() {
    // subscribe to changes of the Counter1Key value
    replicator.tell(new Subscribe<PNCounter>(counter1Key, getSelf()), ActorRef.noSender());
  }
}
```

### 删除

可以通过发送`Replicator.Delete`消息到本地`Replicator`来请求删除某个数据条目。如果在提供的超时内根据提供的一致性级别成功删除了值，则作为`Delete`的回复，会向`Delete`的发送者发送`Replicator.DeleteSuccess`。否则将发送`Replicator.ReplicationDeleteFailure`。请注意，`ReplicationDeleteFailure`并不意味着删除完全失败或已回滚。它可能仍然被复制到某些节点，并最终被复制到所有节点。

已删除的键不能再次使用，但仍建议删除未使用的数据条目，因为这样可以减少新节点加入群集时的复制开销。随后的`Delete`、`Update`和`Get`请求将用`Replicator.DataDeleted`回复。订阅者将收到`Replicator.Deleted`。

在`Delete`消息中，你可以通过与上述`Update`消息相同的方式传递可选请求上下文。例如，在接收和转换`DeleteSuccess`之后，可以传递和回复原始发件人。

```java
class DemonstrateDelete extends AbstractActor {
  final ActorRef replicator = DistributedData.get(getContext().getSystem()).replicator();

  final Key<PNCounter> counter1Key = PNCounterKey.create("counter1");
  final Key<ORSet<String>> set2Key = ORSetKey.create("set2");

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .matchEquals(
            "demonstrate delete",
            msg -> {
              replicator.tell(
                  new Delete<PNCounter>(counter1Key, Replicator.writeLocal()), getSelf());

              final WriteConsistency writeMajority = new WriteMajority(Duration.ofSeconds(5));
              replicator.tell(new Delete<PNCounter>(counter1Key, writeMajority), getSelf());
            })
        .build();
  }
}
```

- **警告**：由于删除的键继续包含在每个节点的存储数据以及`gossip`消息中，一系列顶级实体的连续更新和删除将导致内存使用量增加，直到`ActorSystem`耗尽内存。要在需要频繁添加和删除的地方使用 Akka 分布式数据，你应该使用固定数量的支持更新和删除的顶级数据类型，例如`ORMap`或`ORSet`。

### delta-CRDT

支持「[Delta State Replicated Data Types](https://arxiv.org/abs/1603.01529)」。`delta-CRDT`是一种减少发送更新的完整状态需求的方法。例如，将元素`'c'`和`'d'`添加到集合`{'a', 'b'}`将导致发送`{'c', 'd'}`，并将其与接收端的状态合并，从而导致集合`{'a', 'b', 'c', 'd'}`。

如果数据类型标记为`RequiresCausalDeliveryOfDeltas`，则复制`deltas`的协议支持因果一致性（`causal consistency`）。否则，它只是最终一致的（`eventually consistent`）。如果不存在因果一致性，则意味着如果在两个单独的`Update`操作中添加元素`'c'`和`'d'`，这些增量可能偶尔以不同的顺序传播到节点，从而达到更新的因果顺序。在这个例子中，它可以导致集合` {'a', 'b', 'd'}`在元素`'c'`出现之前就可以看到。最终将是`{'a', 'b', 'c', 'd'}`。

请注意，`delta-CRDTs`有时也会复制完整状态，例如，将新节点添加到集群时，或者由于网络分离或类似问题而无法传播增量时。

可以使用配置属性禁用`delta`传播：

```java
akka.cluster.distributed-data.delta-crdt.enabled=off
```

## 数据类型

数据类型必须是收敛状态的`CRDTs`并实现`AbstractReplicatedData`接口，即它们提供了一个单调的合并函数，并且状态更改始终是收敛的。

你可以使用自己的自定义`AbstractReplicatedData`或`AbstractDeltaReplicatedData`类型，并且此包提供以下几种类型：

- Counters：`GCounter`、`PNCounter`
- Sets：`GSet`、`ORSet`
- Maps：`ORMap`、`ORMultiMap`、`LWWMap`、`PNCounterMap`
- Registers：`LWWRegister`，`Flag`

### Counters

`GCounter`是一个“仅增长计数器”。它只支持递增，不支持递减。

它的工作方式与矢量时钟类似。它跟踪每个节点的一个计数器，总值是这些计数器的总和。合并是通过获取每个节点的最大计数来实现的。

如果需要递增和递减，可以使用`PNCounter`（正/负计数器）。

它跟踪的增量（`P`）与减量（`N`）分开。`P`和`N`表示为两个内部`GCounter`。合并是通过合并内部`P`和`N`计数器来处理的。计数器的值是`P`计数器的值减去`N`计数器的值。

```java
final SelfUniqueAddress node = DistributedData.get(system).selfUniqueAddress();
final PNCounter c0 = PNCounter.create();
final PNCounter c1 = c0.increment(node, 1);
final PNCounter c2 = c1.increment(node, 7);
final PNCounter c3 = c2.decrement(node, 2);
System.out.println(c3.value()); // 6
```

`GCounter`和`PNCounter`支持`delta-CRDT`，不需要传递`delta`。

可以在具有`PNCounterMap`数据类型的映射中管理几个相关计数器。当计数器被放置在`PNCounterMap`中，而不是将它们作为单独的顶级值放置时，它们被保证作为一个单元一起复制，这有时是相关数据所必需的。

```java
final SelfUniqueAddress node = DistributedData.get(system).selfUniqueAddress();
final PNCounterMap<String> m0 = PNCounterMap.create();
final PNCounterMap<String> m1 = m0.increment(node, "a", 7);
final PNCounterMap<String> m2 = m1.decrement(node, "a", 2);
final PNCounterMap<String> m3 = m2.increment(node, "b", 1);
System.out.println(m3.get("a")); // 5
System.out.println(m3.getEntries());
```

### Maps

如果只需要向集合中添加元素而不删除元素，则`GSet`（仅增长集合）是要使用的数据类型。元素可以是任何类型的可以序列化的值。合并是两个集合的交集。

```java
final GSet<String> s0 = GSet.create();
final GSet<String> s1 = s0.add("a");
final GSet<String> s2 = s1.add("b").add("c");
if (s2.contains("a")) System.out.println(s2.getElements()); // a, b, c
```

`GSet`支持`delta-CRDT`，不需要传递`delta`。

如果需要添加和删除操作，应使用`ORSet`（`observed-remove set`）。元素可以添加和删除任意次数。如果一个元素同时添加和删除，则添加将成功。不能删除未看到的元素。

`ORSet`有一个版本向量，当元素添加到集合中时，该向量将递增。添加元素的节点的版本也会针对所谓的“出生点”中的每个元素进行跟踪。合并函数使用版本向量和点来跟踪操作的因果关系并解决并发更新问题。

```java
final Cluster node = Cluster.get(system);
final ORSet<String> s0 = ORSet.create();
final ORSet<String> s1 = s0.add(node, "a");
final ORSet<String> s2 = s1.add(node, "b");
final ORSet<String> s3 = s2.remove(node, "a");
System.out.println(s3.getElements()); // b
```

`ORSet`支持`delta-CRDT`，不需要传递`delta`。

## Maps

`ORMap`（`observed-remove map`）是一个具有`Any`类型的键的映射，值本身就是复制的数据类型。它支持为一个映射条目添加、更新和删除任意次数。

如果同时添加和删除一个条目，则添加将成功。无法删除未看到的条目。这与`ORSet`的语义相同。

如果一个条目同时更新为不同的值，那么这些值将被合并，因此需要复制这些值的数据类型。

直接使用`ORMap`相当不方便，因为它不公开特定类型的值。`ORMap`是用来构建更具体`Map`的低级工具，例如下面特殊的`Map`。

- `ORMultiMap`（`observed-remove multi-map`）是一个多映射实现，它用一个`ORSet`来包装一个`ORMap`以获得该映射的值。
- `PNCounterMap`（`positive negative counter map`）是命名计数器的映射（其中名称可以是任何类型）。它是具有`PNCounter`值的特殊`ORMap`。
- `LWWMap`（`last writer wins map`）是一个具有`LWWRegister`（`last writer wins register`）值的特殊`ORMap`。

`ORMap`、`ORMultiMap`、`PNCounterMap`和`LWWMap`支持`delta-CRDT`，它们需要传递`delta`。这里对`delta`的支持意味着作为所有这些映射的基础键类型的`ORSet`使用`delta`传播来传递更新。实际上，`Map`的更新是一个`pair`，由`ORSet`的`delta`组成键和保存在`Map`中的相应值（`ORSet`、`PNCounter`或`LWWRegister`）。

```java
final SelfUniqueAddress node = DistributedData.get(system).selfUniqueAddress();
final ORMultiMap<String, Integer> m0 = ORMultiMap.create();
final ORMultiMap<String, Integer> m1 = m0.put(node, "a", new HashSet<>(Arrays.asList(1, 2, 3)));
final ORMultiMap<String, Integer> m2 = m1.addBinding(node, "a", 4);
final ORMultiMap<String, Integer> m3 = m2.removeBinding(node, "a", 2);
final ORMultiMap<String, Integer> m4 = m3.addBinding(node, "b", 1);
System.out.println(m4.getEntries());
```

更改数据项时，该项的完整状态将复制到其他节点，即更新映射时，将复制整个映射。因此，不使用一个包含 1000 个元素的`ORMap`，而是更有效地将其分解为 10 个顶级`ORMap`条目，每个条目包含 100 个元素。顶级条目是单独复制的，这就需要权衡不同条目可能不会同时复制，并且你可能会看到相关条目之间的不一致。单独的顶级条目不能原子地一起更新。

有一个特殊版本的`ORMultiMap`，是使用单独的构造函数`ORMultiMap.emptyWithValueDeltas[A, B]`创建的，它还将更新作为`delta`传播到它的值（`ORSet`类型）。这意味着用`ORMultiMap.emptyWithValueDeltas`启动的`ORMultiMap`将其更新作为成对传播，包含键的`delta`和值的`delta`。它在网络带宽消耗方面效率更高。

但是，此行为尚未成为`ORMultiMap`的默认行为，如果希望在代码中使用它，则需要将`ORMultiMap.empty[A, B]`（或者`ORMultiMap()`）的调用替换为`ORMultiMap.emptyWithValueDeltas[A, B]`，其中`A`和`B`分别是映射中的键和值的类型。

请注意，尽管具有相同的`Scala`类型，但`ORMultiMap.emptyWithValueDeltas`与`ORMultiMap`不兼容，因为复制机制不同。我们需要格外小心，不要将两者混合，因为它们具有相同的类型，所以编译器不会提示错误。尽管如此，`ORMultiMap.emptyWithValueDeltas`使用与`ORMultiMap`相同的`ORMultiMapKey`类型进行引用。

请注意，`LWWRegister`和`LWWMap`依赖于同步的时钟，并且仅当值的选择对于在时钟偏差内发生的并发更新不重要时才应使用。请阅读下面有关`LWWRegister`的部分。

### Flags 和 Registers

`Flag`是布尔值的数据类型，该值初始化为`false`，可以切换为`true`。之后就不能改变了。在合并中，`true`胜于`false`。

```java
final Flag f0 = Flag.create();
final Flag f1 = f0.switchOn();
System.out.println(f1.enabled());
```

`LWWRegister`（`last writer wins register`）可以保存任何（可序列化）值。

合并`LWWRegister`将获得时间戳最高的`register`。请注意，这依赖于同步时钟。只有当值的选择对于时钟偏差内发生的并发更新不重要时，才应使用`LWWRegister`。

如果时间戳完全相同，则合并接受由地址最低的节点（按`UniqueAddress`排序）更新的`register`。

```java
final SelfUniqueAddress node = DistributedData.get(system).selfUniqueAddress();
final LWWRegister<String> r1 = LWWRegister.create(node, "Hello");
final LWWRegister<String> r2 = r1.withValue(node, "Hi");
System.out.println(r1.value() + " by " + r1.updatedBy() + " at " + r1.timestamp());
```

不使用基于`System.currentTimeMillis()`的时间戳，而是使用基于其他内容的时间戳值，例如，用于乐观并发控制的数据库记录的不断增加的版本号。

```java
class Record {
  public final int version;
  public final String name;
  public final String address;

  public Record(int version, String name, String address) {
    this.version = version;
    this.name = name;
    this.address = address;
  }
}


  final SelfUniqueAddress node = DistributedData.get(system).selfUniqueAddress();
  final LWWRegister.Clock<Record> recordClock =
      new LWWRegister.Clock<Record>() {
        @Override
        public long apply(long currentTimestamp, Record value) {
          return value.version;
        }
      };

  final Record record1 = new Record(1, "Alice", "Union Square");
  final LWWRegister<Record> r1 = LWWRegister.create(node, record1);

  final Record record2 = new Record(2, "Alice", "Madison Square");
  final LWWRegister<Record> r2 = LWWRegister.create(node, record2);

  final LWWRegister<Record> r3 = r1.merge(r2);
  System.out.println(r3.value());
```

对于`first-write-wins`语义，可以使用`LWWRegister#reverseClock`而不是`LWWRegister#defaultClock`。

`defaultClock`使用`System.currentTimeMillis()`和`currentTimestamp + 1`的最大值。这意味着时间戳对于在相同毫秒内发生的同一节点上的更改会增加。它还意味着，当只有一个活动的`writer`（如集群单例）时，可以安全地使用不带同步时钟的`LWWRegister`。这样的单个`writer`应该首先使用`ReadMajority`（或更多）读取当前值，然后再使用`WriteMajority`（或更多）更改和写入值。

### 自定义数据类型

你可以实现自己的数据类型。唯一的要求是它实现`AbstractReplicatedData`特性的`mergeData`函数。

有状态的`CRDTs`的一个好特性是，它们通常组成得很好，也就是说，你可以组合几个较小的数据类型来构建更丰富的数据结构。例如，`PNCounter`由两个内部`GCounter`实例组成，分别跟踪递增和递减。

下面是一个定制的`TwoPhaseSet`的简单实现，它使用两种内部`GSet`类型来跟踪添加和删除。`TwoPhaseSet`可以添加和删除一个元素，但此后再也不能添加。

```java
public class TwoPhaseSet extends AbstractReplicatedData<TwoPhaseSet> {

  public final GSet<String> adds;
  public final GSet<String> removals;

  public TwoPhaseSet(GSet<String> adds, GSet<String> removals) {
    this.adds = adds;
    this.removals = removals;
  }

  public static TwoPhaseSet create() {
    return new TwoPhaseSet(GSet.create(), GSet.create());
  }

  public TwoPhaseSet add(String element) {
    return new TwoPhaseSet(adds.add(element), removals);
  }

  public TwoPhaseSet remove(String element) {
    return new TwoPhaseSet(adds, removals.add(element));
  }

  public Set<String> getElements() {
    Set<String> result = new HashSet<>(adds.getElements());
    result.removeAll(removals.getElements());
    return result;
  }

  @Override
  public TwoPhaseSet mergeData(TwoPhaseSet that) {
    return new TwoPhaseSet(this.adds.merge(that.adds), this.removals.merge(that.removals));
  }
}
```

数据类型应该是不可变的，即“修改”方法应该返回一个新实例。

如果`AbstractDeltaReplicatedData`支持`delta-CRDT`，则实现它的其他方法。

### 序列化

数据类型必须可以使用「[Akka Serializer](https://doc.akka.io/docs/akka/current/serialization.html)」进行序列化。强烈建议对自定义数据类型使用`Protobuf`或类似工具实现有效的序列化。内置数据类型用`ReplicatedDataSerialization`标记，并用`akka.cluster.ddata.protobuf.ReplicatedDataSerializer`序列化。

数据类型的序列化用于远程消息，也用于创建消息摘要（`SHA-1`）以检测更改。因此，有效地进行序列化并为相同的内容生成相同的字节非常重要。例如，集合和映射应该在序列化中确定地排序。

这是上述`TwoPhaseSet`的`protobuf`表现：

```java
option java_package = "docs.ddata.protobuf.msg";
option optimize_for = SPEED;

message TwoPhaseSet {
  repeated string adds = 1;
  repeated string removals = 2;
}
```

`TwoPhaseSet`的序列化程序：

```java
import jdocs.ddata.TwoPhaseSet;
import docs.ddata.protobuf.msg.TwoPhaseSetMessages;
import docs.ddata.protobuf.msg.TwoPhaseSetMessages.TwoPhaseSet.Builder;
import java.util.ArrayList;
import java.util.Collections;

import akka.actor.ExtendedActorSystem;
import akka.cluster.ddata.GSet;
import akka.cluster.ddata.protobuf.AbstractSerializationSupport;

public class TwoPhaseSetSerializer extends AbstractSerializationSupport {

  private final ExtendedActorSystem system;

  public TwoPhaseSetSerializer(ExtendedActorSystem system) {
    this.system = system;
  }

  @Override
  public ExtendedActorSystem system() {
    return this.system;
  }

  @Override
  public boolean includeManifest() {
    return false;
  }

  @Override
  public int identifier() {
    return 99998;
  }

  @Override
  public byte[] toBinary(Object obj) {
    if (obj instanceof TwoPhaseSet) {
      return twoPhaseSetToProto((TwoPhaseSet) obj).toByteArray();
    } else {
      throw new IllegalArgumentException("Can't serialize object of type " + obj.getClass());
    }
  }

  @Override
  public Object fromBinaryJava(byte[] bytes, Class<?> manifest) {
    return twoPhaseSetFromBinary(bytes);
  }

  protected TwoPhaseSetMessages.TwoPhaseSet twoPhaseSetToProto(TwoPhaseSet twoPhaseSet) {
    Builder b = TwoPhaseSetMessages.TwoPhaseSet.newBuilder();
    ArrayList<String> adds = new ArrayList<>(twoPhaseSet.adds.getElements());
    if (!adds.isEmpty()) {
      Collections.sort(adds);
      b.addAllAdds(adds);
    }
    ArrayList<String> removals = new ArrayList<>(twoPhaseSet.removals.getElements());
    if (!removals.isEmpty()) {
      Collections.sort(removals);
      b.addAllRemovals(removals);
    }
    return b.build();
  }

  protected TwoPhaseSet twoPhaseSetFromBinary(byte[] bytes) {
    try {
      TwoPhaseSetMessages.TwoPhaseSet msg = TwoPhaseSetMessages.TwoPhaseSet.parseFrom(bytes);
      GSet<String> adds = GSet.create();
      for (String elem : msg.getAddsList()) {
        adds = adds.add(elem);
      }
      GSet<String> removals = GSet.create();
      for (String elem : msg.getRemovalsList()) {
        removals = removals.add(elem);
      }
      // GSet will accumulate deltas when adding elements,
      // but those are not of interest in the result of the deserialization
      return new TwoPhaseSet(adds.resetDelta(), removals.resetDelta());
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }
}
```

请注意，集合中的元素是经过排序的，因此对于相同的元素，`SHA-1`摘要是相同的。

在配置中注册序列化程序：

```java
akka.actor {
  serializers {
    twophaseset = "jdocs.ddata.protobuf.TwoPhaseSetSerializer"
  }
  serialization-bindings {
    "jdocs.ddata.TwoPhaseSet" = twophaseset
  }
}
```

使用压缩有时是减少数据大小的一个好主意。`Gzip`压缩由`akka.cluster.ddata.protobuf.AbstractSerializationSupport`接口提供：

```java
@Override
public byte[] toBinary(Object obj) {
  if (obj instanceof TwoPhaseSet) {
    return compress(twoPhaseSetToProto((TwoPhaseSet) obj));
  } else {
    throw new IllegalArgumentException("Can't serialize object of type " + obj.getClass());
  }
}

@Override
public Object fromBinaryJava(byte[] bytes, Class<?> manifest) {
  return twoPhaseSetFromBinary(decompress(bytes));
}
```

如上所示，两个嵌入的`GSet`可以序列化，但一般来说，从现有的内置类型组合新的数据类型时，最好对这些类型使用现有的序列化程序。这可以通过在`protobuf`中将这些字段声明为字节字段来实现：

```java
message TwoPhaseSet2 {
  optional bytes adds = 1;
  optional bytes removals = 2;
}
```

并使用序列化支持特性提供的方法`otherMessageToProto`和`otherMessageFromBinary`来序列化和反序列化`GSet`实例。这适用于任何具有已注册的 Akka 序列化程序的类型。下面就是`TwoPhaseSet`这样的序列化程序：

```java
import jdocs.ddata.TwoPhaseSet;
import docs.ddata.protobuf.msg.TwoPhaseSetMessages;
import docs.ddata.protobuf.msg.TwoPhaseSetMessages.TwoPhaseSet2.Builder;

import akka.actor.ExtendedActorSystem;
import akka.cluster.ddata.GSet;
import akka.cluster.ddata.protobuf.AbstractSerializationSupport;
import akka.cluster.ddata.protobuf.ReplicatedDataSerializer;

public class TwoPhaseSetSerializer2 extends AbstractSerializationSupport {

  private final ExtendedActorSystem system;
  private final ReplicatedDataSerializer replicatedDataSerializer;

  public TwoPhaseSetSerializer2(ExtendedActorSystem system) {
    this.system = system;
    this.replicatedDataSerializer = new ReplicatedDataSerializer(system);
  }

  @Override
  public ExtendedActorSystem system() {
    return this.system;
  }

  @Override
  public boolean includeManifest() {
    return false;
  }

  @Override
  public int identifier() {
    return 99998;
  }

  @Override
  public byte[] toBinary(Object obj) {
    if (obj instanceof TwoPhaseSet) {
      return twoPhaseSetToProto((TwoPhaseSet) obj).toByteArray();
    } else {
      throw new IllegalArgumentException("Can't serialize object of type " + obj.getClass());
    }
  }

  @Override
  public Object fromBinaryJava(byte[] bytes, Class<?> manifest) {
    return twoPhaseSetFromBinary(bytes);
  }

  protected TwoPhaseSetMessages.TwoPhaseSet2 twoPhaseSetToProto(TwoPhaseSet twoPhaseSet) {
    Builder b = TwoPhaseSetMessages.TwoPhaseSet2.newBuilder();
    if (!twoPhaseSet.adds.isEmpty())
      b.setAdds(otherMessageToProto(twoPhaseSet.adds).toByteString());
    if (!twoPhaseSet.removals.isEmpty())
      b.setRemovals(otherMessageToProto(twoPhaseSet.removals).toByteString());
    return b.build();
  }

  @SuppressWarnings("unchecked")
  protected TwoPhaseSet twoPhaseSetFromBinary(byte[] bytes) {
    try {
      TwoPhaseSetMessages.TwoPhaseSet2 msg = TwoPhaseSetMessages.TwoPhaseSet2.parseFrom(bytes);

      GSet<String> adds = GSet.create();
      if (msg.hasAdds()) adds = (GSet<String>) otherMessageFromBinary(msg.getAdds().toByteArray());

      GSet<String> removals = GSet.create();
      if (msg.hasRemovals())
        adds = (GSet<String>) otherMessageFromBinary(msg.getRemovals().toByteArray());

      return new TwoPhaseSet(adds, removals);
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }
}
```

### 持久存储

默认情况下，数据只保存在内存中。它是冗余的，因为它被复制到集群中的其他节点，但是如果停止所有节点，数据就会丢失，除非你已将其保存到其他位置。

条目可以配置为持久的，即存储在每个节点的本地磁盘上。下一次启动`replicator`时，即当 Actor 系统重新启动时，将加载存储的数据。这意味着只要旧集群中的至少一个节点参与到新集群中，数据就可以生存。持久条目的键配置为：

```java
akka.cluster.distributed-data.durable.keys = ["a", "b", "durable*"]
```

在键的末尾使用`*`支持前缀匹配。

通过指定以下内容，可以使所有条目持久：

```java
akka.cluster.distributed-data.durable.keys = ["*"]
```

「[LMDB](https://github.com/lmdbjava/lmdbjava/)」是默认的存储实现。通过实现`akka.cluster.ddata.DurableStore`中描述的 Actor 协议并为新实现定义`akka.cluster.distributed-data.durable.store-actor-class`属性，可以将其替换为另一个实现。

数据文件的位置配置为：

```java
# Directory of LMDB file. There are two options:
# 1. A relative or absolute path to a directory that ends with 'ddata'
#    the full name of the directory will contain name of the ActorSystem
#    and its remote port.
# 2. Otherwise the path is used as is, as a relative or absolute path to
#    a directory.
akka.cluster.distributed-data.durable.lmdb.dir = "ddata"
```

在生产环境中运行时，你可能希望将目录配置为特定路径（`alt 2`），因为默认目录包含 Actor 系统的远程端口以使名称唯一。如果使用动态分配的端口（`0`），则每次都会不同，并且不会加载以前存储的数据。

使数据持久化有性能成本。默认情况下，在发送`UpdateSuccess`回复之前，每个更新都会刷新到磁盘。为了获得更好的性能，但是如果 JVM 崩溃，则有可能丢失最后一次写入，你可以启用写后模式（`write behind mode`）。然后在将更改写入 LMDB 并刷新到磁盘之前的一段时间内累积更改。当对同一个键执行多次写入时，启用写后处理特别有效，因为它只是将被序列化和存储的每个键的最后一个值。如果 JVM 崩溃的风险很小，则会丢失写操作，因为数据通常会根据给定的`WriteConsistency`立即复制到其他节点。

```java
akka.cluster.distributed-data.durable.lmdb.write-behind-interval = 200 ms
```

请注意，如果由于某种原因无法存储数据，则应准备接收`WriteFailure`作为对持久条目`Update`的答复。当启用`write-behind-interval`时，这些错误将只被记录，而`UpdateSuccess`仍然是对`Update`的答复。

当为持久数据修剪 CRDT 垃圾时，有一个重要的警告。如果一个从未修剪过的旧数据条目被注入，并在修剪（`pruning`）标记被删除后与现有数据合并，则该值将不正确。标记的生存时间由配置`akka.cluster.distributed-data.durable.remove-pruning-marker-after`定义，以天为单位。如果具有持久数据的节点没有参与修剪（例如，它被关闭），并且在这段时间之后开始修剪，这是可能的。具有持久数据的节点的停止时间不应超过此持续时间，如果在此持续时间之后再次加入，则应首先手动（从`lmdb`目录中）删除其数据。

### CRDT 垃圾

`CRDT`的一个问题是，某些数据类型会累积历史记录（垃圾）。例如，`GCounter`跟踪每个节点的一个计数器。如果已经从一个节点更新了`GCounter`，它将永远关联该节点的标识符。对于添加和删除了许多群集节点的长时间运行的系统来说，这可能成为一个问题。为了解决这个问题，`Replicator`执行与已从集群中删除的节点相关联的数据修剪。需要修剪的数据类型必须实现`RemovedNodePruning`特性。有关详细信息，请参阅`Replicator`的 API 文档。

## 样例

在「[Akka Distributed Data Samples with Java](https://example.lightbend.com/v1/download/akka-samples-distributed-data-java)」中包含一些有趣的样例和教程。

- 低延迟投票服务
- 高可用购物车
- 分布式服务注册表
- 复制缓存
- 复制指标

## 局限性

你应该注意一些限制。

`CRDTs`不能用于所有类型的问题，并且最终的一致性不适合所有域。有时候你需要很强的一致性。

它不适用于大数据。顶级条目数不应超过 100000 条。当一个新节点添加到集群中时，所有这些条目都会被传输（`gossiped`）到新节点。条目被分割成块，所有现有节点在`gossip`中协作，但传输所有条目需要一段时间（数十秒），这意味着顶级条目不能太多。当前建议的限制为 100000。如果需要的话，我们将能够改进这一点，但是设计仍然不打算用于数十亿个条目。

所有的数据都保存在内存中，这也是它不适用于大数据的另一个原因。

当一个数据条目被更改时，如果它不支持`delta-CRDT`，则该条目的完整状态可以复制到其他节点。`delta-CRDT`的完整状态也会被复制，例如当向集群中添加新节点时，或者当由于网络分裂或类似问题而无法传播`delta`时。这意味着你不能有太大的数据条目，因为远程消息的大小将太大。

## 了解有关 CRDT 的更多信息

- [Eventually Consistent Data Structures](https://vimeo.com/43903960)
- [Strong Eventual Consistency and Conflict-free Replicated Data Types (video)](https://www.youtube.com/watch?v=oyUHd894w18&amp;feature=youtu.be)
- [A comprehensive study of Convergent and Commutative Replicated Data Types](http://hal.upmc.fr/file/index/docid/555588/filename/techreport.pdf)

## 配置

可以使用以下属性配置`DistributedData`扩展：

```yml
# Settings for the DistributedData extension
akka.cluster.distributed-data {
  # Actor name of the Replicator actor, /system/ddataReplicator
  name = ddataReplicator

  # Replicas are running on members tagged with this role.
  # All members are used if undefined or empty.
  role = ""

  # How often the Replicator should send out gossip information
  gossip-interval = 2 s
  
  # How often the subscribers will be notified of changes, if any
  notify-subscribers-interval = 500 ms

  # Maximum number of entries to transfer in one gossip message when synchronizing
  # the replicas. Next chunk will be transferred in next round of gossip.
  max-delta-elements = 1000
  
  # The id of the dispatcher to use for Replicator actors. If not specified
  # default dispatcher is used.
  # If specified you need to define the settings of the actual dispatcher.
  use-dispatcher = ""

  # How often the Replicator checks for pruning of data associated with
  # removed cluster nodes. If this is set to 'off' the pruning feature will
  # be completely disabled.
  pruning-interval = 120 s
  
  # How long time it takes to spread the data to all other replica nodes.
  # This is used when initiating and completing the pruning process of data associated
  # with removed cluster nodes. The time measurement is stopped when any replica is 
  # unreachable, but it's still recommended to configure this with certain margin.
  # It should be in the magnitude of minutes even though typical dissemination time
  # is shorter (grows logarithmic with number of nodes). There is no advantage of 
  # setting this too low. Setting it to large value will delay the pruning process.
  max-pruning-dissemination = 300 s
  
  # The markers of that pruning has been performed for a removed node are kept for this
  # time and thereafter removed. If and old data entry that was never pruned is somehow
  # injected and merged with existing data after this time the value will not be correct.
  # This would be possible (although unlikely) in the case of a long network partition.
  # It should be in the magnitude of hours. For durable data it is configured by 
  # 'akka.cluster.distributed-data.durable.pruning-marker-time-to-live'.
 pruning-marker-time-to-live = 6 h
  
  # Serialized Write and Read messages are cached when they are sent to 
  # several nodes. If no further activity they are removed from the cache
  # after this duration.
  serializer-cache-time-to-live = 10s
  
  # Settings for delta-CRDT
  delta-crdt {
    # enable or disable delta-CRDT replication
    enabled = on
    
    # Some complex deltas grow in size for each update and above this
    # threshold such deltas are discarded and sent as full state instead.
    # This is number of elements or similar size hint, not size in bytes.
    max-delta-size = 200
  }
  
  durable {
    # List of keys that are durable. Prefix matching is supported by using * at the
    # end of a key.  
    keys = []
    
    # The markers of that pruning has been performed for a removed node are kept for this
    # time and thereafter removed. If and old data entry that was never pruned is
    # injected and merged with existing data after this time the value will not be correct.
    # This would be possible if replica with durable data didn't participate in the pruning
    # (e.g. it was shutdown) and later started after this time. A durable replica should not 
    # be stopped for longer time than this duration and if it is joining again after this
    # duration its data should first be manually removed (from the lmdb directory).
    # It should be in the magnitude of days. Note that there is a corresponding setting
    # for non-durable data: 'akka.cluster.distributed-data.pruning-marker-time-to-live'.
    pruning-marker-time-to-live = 10 d
    
    # Fully qualified class name of the durable store actor. It must be a subclass
    # of akka.actor.Actor and handle the protocol defined in 
    # akka.cluster.ddata.DurableStore. The class must have a constructor with 
    # com.typesafe.config.Config parameter.
    store-actor-class = akka.cluster.ddata.LmdbDurableStore
    
    use-dispatcher = akka.cluster.distributed-data.durable.pinned-store
    
    pinned-store {
      executor = thread-pool-executor
      type = PinnedDispatcher
    }
    
    # Config for the LmdbDurableStore
    lmdb {
      # Directory of LMDB file. There are two options:
      # 1. A relative or absolute path to a directory that ends with 'ddata'
      #    the full name of the directory will contain name of the ActorSystem
      #    and its remote port.
      # 2. Otherwise the path is used as is, as a relative or absolute path to
      #    a directory.
      #
      # When running in production you may want to configure this to a specific
      # path (alt 2), since the default directory contains the remote port of the
      # actor system to make the name unique. If using a dynamically assigned 
      # port (0) it will be different each time and the previously stored data 
      # will not be loaded.
      dir = "ddata"
      
      # Size in bytes of the memory mapped file.
      map-size = 100 MiB
      
      # Accumulate changes before storing improves performance with the
      # risk of losing the last writes if the JVM crashes.
      # The interval is by default set to 'off' to write each update immediately.
      # Enabling write behind by specifying a duration, e.g. 200ms, is especially 
      # efficient when performing many writes to the same key, because it is only 
      # the last value for each key that will be serialized and stored.  
      # write-behind-interval = 200 ms
      write-behind-interval = off
    }
  }
  
}
```



----------

**英文原文链接**：[Distributed Data](https://doc.akka.io/docs/akka/current/distributed-data.html).



----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————