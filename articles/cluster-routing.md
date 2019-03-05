# 集群感知路由器

所有「[routers](https://doc.akka.io/docs/akka/current/routing.html)」都可以知道集群中的成员节点，即部署新的路由（`routees`）或在集群中的节点上查找路由。当一个节点无法访问或离开集群时，该节点的路由将自动从路由器中注销。当新节点加入集群时，会根据配置向路由器添加额外的路由。当一个节点在不可访问之后再次可访问时，也会添加路由。

群集感知路由（`Cluster aware routers`）可以使用`WeaklyUp`状态的成员（如果启用该功能）。

有两种不同类型的路由器。

- `Group`，使用 Actor `selection`将消息发送到指定路径的路由器：路由可以在群集中不同节点上运行的路由器之间共享。这种类型路由器的一个用例示例是运行在集群中某些后端节点上的服务，可由运行在集群中前端节点上的路由器使用。
- `Pool`，将路由创建为子 Actor  ，并将它们部署到远程节点上：每个路由器都有自己的路由实例。例如，如果在 10 节点群集中的 3 个节点上启动路由器，那么如果将路由器配置为每个节点使用一个实例，则总共有 30 个路由。不同路由器创建的路由不会在路由器之间共享。这种类型路由器的一个用例示例是一个单独的`master`，它协调作业并将实际工作委托给集群中其他节点上运行的路由。

## 依赖
为了使用集群感知路由器（`Cluster Aware Routers`），你必须在项目中添加如下依赖：

```xml
<!-- Maven -->
<dependency>
  <groupId>com.typesafe.akka</groupId>
  <artifactId>akka-cluster_2.12</artifactId>
  <version>2.5.21</version>
</dependency>

<!-- Gradle -->
dependencies {
  compile group: 'com.typesafe.akka', name: 'akka-cluster_2.12', version: '2.5.21'
}

<!-- sbt -->
libraryDependencies += "com.typesafe.akka" %% "akka-cluster" % "2.5.21"
```
## 组路由器

使用`Group`时，必须在集群成员节点上启动路由 Actor。这不是由路由器完成的。组的配置如下所示：

```
akka.actor.deployment {
  /statsService/workerRouter {
      router = consistent-hashing-group
      routees.paths = ["/user/statsWorker"]
      cluster {
        enabled = on
        allow-local-routees = on
        use-roles = ["compute"]
      }
    }
}
```

- **注意**：当启动 Actor 系统时，路由 Actor 应该尽早启动，因为一旦成员状态更改为`Up`，路由器就会尝试使用它们。

`routees.paths`中定义的 Actor 路径用于选择由路由器将消息转发到的 Actor。路径不应包含协议和地址信息，因为它们是从集群成员（`membership`）动态检索的。消息将使用「[ActorSelection](https://doc.akka.io/docs/akka/current/actors.html#actorselection)」转发到路由，因此应该使用相同的传递语义。通过指定`use-roles`，可以将对路由的查找限制到标记了特定角色集的成员节点。

`max-total-nr-of-instances`定义群集中的路由总数。默认情况下，`max-total-nr-of-instances`设置为高值（`10000`），当节点加入集群时，将导致新的路由添加到路由器。如果要限制路由总数，请将其设置为较低的值。

同样类型的路由器也可以在代码中定义：

```java
int totalInstances = 100;
Iterable<String> routeesPaths = Collections.singletonList("/user/statsWorker");
boolean allowLocalRoutees = true;
Set<String> useRoles = new HashSet<>(Arrays.asList("compute"));
ActorRef workerRouter =
    getContext()
        .actorOf(
            new ClusterRouterGroup(
                    new ConsistentHashingGroup(routeesPaths),
                    new ClusterRouterGroupSettings(
                        totalInstances, routeesPaths, allowLocalRoutees, useRoles))
                .props(),
            "workerRouter2");
```

有关设置的详细说明，请参阅「[参考配置](https://doc.akka.io/docs/akka/current/general/configuration.html#config-akka-cluster)」。

### 带路由组的路由器示例

让我们来看看如何将集群感知路由器与一组路由（即发送到路由器路径的路由）一起使用。

示例应用程序提供了一个计算文本统计信息的服务。当一些文本被发送到服务时，它将其拆分为单词，并将任务分配给一个单独的工作进程（路由器的一个路由），以计算每个单词中的字符数。每个字的字符数被发送回一个聚合器（`aggregator`），该聚合器在收集所有结果时计算每个字的平均字符数。

消息：

```java
public interface StatsMessages {

  public static class StatsJob implements Serializable {
    private final String text;

    public StatsJob(String text) {
      this.text = text;
    }

    public String getText() {
      return text;
    }
  }

  public static class StatsResult implements Serializable {
    private final double meanWordLength;

    public StatsResult(double meanWordLength) {
      this.meanWordLength = meanWordLength;
    }

    public double getMeanWordLength() {
      return meanWordLength;
    }

    @Override
    public String toString() {
      return "meanWordLength: " + meanWordLength;
    }
  }

  public static class JobFailed implements Serializable {
    private final String reason;

    public JobFailed(String reason) {
      this.reason = reason;
    }

    public String getReason() {
      return reason;
    }

    @Override
    public String toString() {
      return "JobFailed(" + reason + ")";
    }
  }
}
```

计算每个字中字符数的工作者（`worker`）：

```java
public class StatsWorker extends AbstractActor {

  Map<String, Integer> cache = new HashMap<String, Integer>();

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            String.class,
            word -> {
              Integer length = cache.get(word);
              if (length == null) {
                length = word.length();
                cache.put(word, length);
              }
              getSender().tell(length, getSelf());
            })
        .build();
  }
}
```

从用户接收文本并将其拆分为单词、委派给`workers`和聚合（`aggregates`）的服务：

```java
public class StatsService extends AbstractActor {

  // This router is used both with lookup and deploy of routees. If you
  // have a router with only lookup of routees you can use Props.empty()
  // instead of Props.create(StatsWorker.class).
  ActorRef workerRouter =
      getContext()
          .actorOf(FromConfig.getInstance().props(Props.create(StatsWorker.class)), "workerRouter");

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            StatsJob.class,
            job -> !job.getText().isEmpty(),
            job -> {
              String[] words = job.getText().split(" ");
              ActorRef replyTo = getSender();

              // create actor that collects replies from workers
              ActorRef aggregator =
                  getContext().actorOf(Props.create(StatsAggregator.class, words.length, replyTo));

              // send each word to a worker
              for (String word : words) {
                workerRouter.tell(new ConsistentHashableEnvelope(word, word), aggregator);
              }
            })
        .build();
  }
}
```

```java
public class StatsAggregator extends AbstractActor {

  final int expectedResults;
  final ActorRef replyTo;
  final List<Integer> results = new ArrayList<Integer>();

  public StatsAggregator(int expectedResults, ActorRef replyTo) {
    this.expectedResults = expectedResults;
    this.replyTo = replyTo;
  }

  @Override
  public void preStart() {
    getContext().setReceiveTimeout(Duration.ofSeconds(3));
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            Integer.class,
            wordCount -> {
              results.add(wordCount);
              if (results.size() == expectedResults) {
                int sum = 0;
                for (int c : results) {
                  sum += c;
                }
                double meanWordLength = ((double) sum) / results.size();
                replyTo.tell(new StatsResult(meanWordLength), getSelf());
                getContext().stop(getSelf());
              }
            })
        .match(
            ReceiveTimeout.class,
            x -> {
              replyTo.tell(new JobFailed("Service unavailable, try again later"), getSelf());
              getContext().stop(getSelf());
            })
        .build();
  }
}
```

注意，到目前为止还没有特定的集群，只是普通的 Actor。

所有节点都启动`StatsService`和`StatsWorker` Actor。记住，在这种情况下，路由是`worker`。路由器配置了`routees.paths`：

```
akka.actor.deployment {
  /statsService/workerRouter {
    router = consistent-hashing-group
    routees.paths = ["/user/statsWorker"]
    cluster {
      enabled = on
      allow-local-routees = on
      use-roles = ["compute"]
    }
  }
}
```

这意味着用户请求可以发送到任何节点上的`StatsService`，并且它将在所有节点上使用`StatsWorker`。

最简单的运行路由器示例的方法是下载「[Akka Cluster Sample with Java](https://example.lightbend.com/v1/download/akka-samples-cluster-java)」，它包含有关如何使用路由组运行路由器示例的说明。此示例的源代码也可以在「[Akka Samples Repository](https://developer.lightbend.com/start/?group=akka&project=akka-sample-cluster-java)」中找到。

## 带有远程部署路由池的路由器

将`Pool`与在群集成员节点上创建和部署的路由一起使用时，路由器的配置如下所示：

```
akka.actor.deployment {
  /statsService/singleton/workerRouter {
      router = consistent-hashing-pool
      cluster {
        enabled = on
        max-nr-of-instances-per-node = 3
        allow-local-routees = on
        use-roles = ["compute"]
      }
    }
}
```

可以通过指定`use-roles`将路由（`routees`）的部署限制到标记了特定角色集的成员节点。

`max-total-nr-of-instances`定义群集中的路由总数，但不会超过每个节点的路由数，`max-nr-of-instances-per-node`。默认情况下，`max-total-nr-of-instances`设置为高值（`10000`），当节点加入集群时，将导致新的路由添加到路由器。如果要限制路由总数，请将其设置为较低的值。

同样类型的路由器也可以在代码中定义：

```java
int totalInstances = 100;
int maxInstancesPerNode = 3;
boolean allowLocalRoutees = false;
Set<String> useRoles = new HashSet<>(Arrays.asList("compute"));
ActorRef workerRouter =
    getContext()
        .actorOf(
            new ClusterRouterPool(
                    new ConsistentHashingPool(0),
                    new ClusterRouterPoolSettings(
                        totalInstances, maxInstancesPerNode, allowLocalRoutees, useRoles))
                .props(Props.create(StatsWorker.class)),
            "workerRouter3");
```

有关设置的详细说明，请参阅「[参考配置](https://doc.akka.io/docs/akka/current/general/configuration.html#config-akka-cluster)」。

### 带有远程部署路由池的路由器示例

让我们看看如何在创建和部署`workers`的单个主节点（`master node`）上使用集群感知路由器。为了跟踪单个主节点，我们使用集群工具模块中的集群单例。`ClusterSingletonManager`在每个节点上启动：

```java
ClusterSingletonManagerSettings settings =
    ClusterSingletonManagerSettings.create(system).withRole("compute");
system.actorOf(
    ClusterSingletonManager.props(
        Props.create(StatsService.class), PoisonPill.getInstance(), settings),
    "statsService");
```

我们还需要在每个节点上有一个 Actor，跟踪当前单个主节点的位置，并将作业委托给`StatsService`。由`ClusterSingletonProxy`提供：

```java
ClusterSingletonProxySettings proxySettings =
    ClusterSingletonProxySettings.create(system).withRole("compute");
system.actorOf(
    ClusterSingletonProxy.props("/user/statsService", proxySettings), "statsServiceProxy");
```

`ClusterSingletonProxy`接收来自用户的文本，并将其委托给当前的`StatsService`（单主）。它监听集群事件以查找最老节点上的`StatsService`。

所有节点都启动`ClusterSingletonProxy`和`ClusterSingletonManager`。路由器现在配置如下：

```
akka.actor.deployment {
  /statsService/singleton/workerRouter {
    router = consistent-hashing-pool
    cluster {
      enabled = on
      max-nr-of-instances-per-node = 3
      allow-local-routees = on
      use-roles = ["compute"]
    }
  }
}
```

最简单的运行带有远程部署路由池的路由器示例的方法是下载「[Akka Cluster Sample with Java](https://example.lightbend.com/v1/download/akka-samples-cluster-java)」，它包含有关如何使用远程部署路由池运行路由器示例的说明。此示例的源代码也可以在「[Akka Samples Repository](https://developer.lightbend.com/start/?group=akka&project=akka-sample-cluster-java)」中找到。



----------

**英文原文链接**：[Cluster Aware Routers](https://doc.akka.io/docs/akka/current/cluster-routing.html).




----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————