# 集群指标扩展
## 依赖

为了使用集群指标扩展（`Cluster Metrics Extension`），你需要将以下依赖添加到你的项目中：

```xml
<!-- Maven -->
<dependency>
  <groupId>com.typesafe.akka</groupId>
  <artifactId>akka-cluster-metrics_2.12</artifactId>
  <version>2.5.22</version>
</dependency>

<!-- Gradle -->
dependencies {
  compile group: 'com.typesafe.akka', name: 'akka-cluster-metrics_2.12', version: '2.5.22'
}

<!-- sbt -->
libraryDependencies += "com.typesafe.akka" %% "akka-cluster-metrics" % "2.5.22"
```

并将以下配置添加到`application.conf`中：

```yml
akka.extensions = [ "akka.cluster.metrics.ClusterMetricsExtension" ]
```

## 简介

集群的成员节点可以收集系统健康指标，并在集群指标扩展的帮助下将其发布到其他集群节点和系统事件总线上注册的订阅者。

集群指标信息主要用于负载均衡路由器（`load-balancing routers`），也可用于实现基于指标的高级节点生命周期，例如当 CPU 窃取时间过多时“节点让它崩溃”。

如果启用了该功能，状态为「[WeaklyUp](https://doc.akka.io/docs/akka/current/cluster-usage.html#weakly-up)」的集群成员将参与集群指标收集和分发。

## 指标收集器

指标集合委托给`akka.cluster.metrics.MetricsCollector.`的实现。

不同的收集器（`collector`）实现提供发布到集群的不同指标子集。当未设置`Sigar`时，某些消息路由和让其崩溃功能可能无法工作。

集群指标扩展附带两个内置收集器实现：

1. `akka.cluster.metrics.SigarMetricsCollector`，它要求提供`Sigar`，并且更丰富/更精确
2. `akka.cluster.metrics.JmxMetricsCollector`，用作回退，不太丰富/精确

你也可以插入（`plug-in`）自己的指标收集器实现。

默认情况下，指标扩展将使用收集器提供程序回滚，并尝试按以下顺序加载它们：

1. 配置的用户提供的收集器
2. 内置的`akka.cluster.metrics.SigarMetricsCollector`
3. 最后是`akka.cluster.metrics.JmxMetricsCollector`

## 指标事件

指标扩展定期地将集群指标的当前快照发布到节点系统事件总线。

发布间隔由`akka.cluster.metrics.collector.sample-interval`设置控制。

`akka.cluster.metrics.ClusterMetricsChanged`事件的有效负载将包含节点的最新指标，以及在收集器采样间隔期间接收到的其他群集成员节点指标流言。

你可以通过指标侦听器 Actor 订阅这些事件，以实现自定义节点生命周期：

```java
ClusterMetricsExtension.get(system).subscribe(metricsListenerActor);
```

## Hyperic Sigar 配置

与可以从普通 JMX MBean 中检索到的指标相比，用户提供的指标收集器和内置的指标收集器都可以选择使用`Hyperic Sigar`来获取更广泛、更准确的指标范围。

`Sigar`使用的是本机 O/S 库，需要提供库，即在运行时将 O/S 本机库部署、提取和加载到 JVM 中。

用户可以通过以下方式之一提供`Sigar`类和本机库：

1. 使用「[Kamon sigar-loader](https://github.com/kamon-io/sigar-loader)」加载器用作用户项目的项目依赖项。指标扩展将根据需要在`Kamon sigar provisioner`的帮助下提取和加载`Sigar`库。
2. 使用「[Kamon sigar-loader](https://github.com/kamon-io/sigar-loader)」加载器作为 Java 代理：`java -javaagent:/path/to/sigar-loader.jar`。`Kamon sigar loader`代理将在 JVM 启动期间提取和加载`Sigar`库。
2. 将`sigar.jar`放在`classpath`上，将 O/S 的`Sigar`本机库放在`java.library.path`上。用户需要手动管理项目依赖项和库部署。

- **警告**：当使用`Kamon sigar loader`并在同一主机上运行同一应用程序的多个实例时，必须确保将`Sigar`库提取到一个唯一的每个实例目录中。你可以使用`akka.cluster.metrics.native-library-extract-folder`配置设置控制提取目录。

为了使用`Sigar`的功能，需要在用户项目中添加以下依赖项：

```xml
<!-- Maven -->
<dependency>
  <groupId>io.kamon</groupId>
  <artifactId>sigar-loader</artifactId>
  <version>1.6.6-rev002</version>
</dependency>

<!-- Gradle -->
dependencies {
  compile group: 'io.kamon', name: 'sigar-loader', version: '1.6.6-rev002'
}

<!-- sbt -->
libraryDependencies += "io.kamon" % "sigar-loader" % "1.6.6-rev002"
```

你可以从「[Maven Central](https://search.maven.org/#search%7Cga%7C1%7Csigar-loader)」中下载`Kamon sigar loader`的依赖包。

## 自适应负载平衡

`AdaptiveLoadBalancingPool` / `AdaptiveLoadBalancingGroup`根据集群指标数据对集群节点的消息执行负载平衡。它使用随机选择的路由，概率来自于相应节点的剩余容量。它可以配置为使用特定的`MetricsSelector`来产生概率，即`a.k.a.`权重：

- `heap` / `HeapMetricsSelector` - 已用和最大 JVM 堆内存。基于剩余堆容量的权重；`(max - used) / max`
- `load` / `SystemLoadAverageMetricsSelector` - 过去 1 分钟的系统平均负载，在 Linux 系统顶部可以找到相应的值。如果系统平均负载接近`cpus/cores`的数量，则系统可能接近瓶颈。基于剩余负载能力的权重；`1 - (load / processors)`
- `cpu` / `CpuMetricsSelector` - 以百分比表示的 CPU 利用率，`User + Sys + Nice + Wait`之和。基于剩余 CPU 容量的权重；`1 - utilization`
- `mix` / `MixMetricsSelector` - 组合堆、CPU 和负载。基于组合选择器剩余容量平均值的权重。
- `akka.cluster.metrics.MetricsSelector`的任何自定义实现

使用「[指数加权移动平均值](http://en.wikipedia.org/wiki/Moving_average#Exponential_moving_average)」平滑收集的指标值。在「[集群配置](https://doc.akka.io/docs/akka/current/cluster-usage.html#cluster-configuration)」中，你可以调整过去的数据相对于新数据的衰减速度。

让我们来看看这台正在运行的路由器。还有什么比计算阶乘（`factorial`）更苛刻的呢？

执行阶乘计算的后端工作程序：

```java
public class FactorialBackend extends AbstractActor {

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            Integer.class,
            n -> {
              CompletableFuture<FactorialResult> result =
                  CompletableFuture.supplyAsync(() -> factorial(n))
                      .thenApply((factorial) -> new FactorialResult(n, factorial));

              pipe(result, getContext().dispatcher()).to(getSender());
            })
        .build();
  }

  BigInteger factorial(int n) {
    BigInteger acc = BigInteger.ONE;
    for (int i = 1; i <= n; ++i) {
      acc = acc.multiply(BigInteger.valueOf(i));
    }
    return acc;
  }
}
```

接收用户作业并通过路由器委派到后端的前端：

```java
public class FactorialFrontend extends AbstractActor {
  final int upToN;
  final boolean repeat;

  LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  ActorRef backend =
      getContext().actorOf(FromConfig.getInstance().props(), "factorialBackendRouter");

  public FactorialFrontend(int upToN, boolean repeat) {
    this.upToN = upToN;
    this.repeat = repeat;
  }

  @Override
  public void preStart() {
    sendJobs();
    getContext().setReceiveTimeout(Duration.ofSeconds(10));
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            FactorialResult.class,
            result -> {
              if (result.n == upToN) {
                log.debug("{}! = {}", result.n, result.factorial);
                if (repeat) sendJobs();
                else getContext().stop(getSelf());
              }
            })
        .match(
            ReceiveTimeout.class,
            x -> {
              log.info("Timeout");
              sendJobs();
            })
        .build();
  }

  void sendJobs() {
    log.info("Starting batch of factorials up to [{}]", upToN);
    for (int n = 1; n <= upToN; n++) {
      backend.tell(n, getSelf());
    }
  }
}
```

如你所见，路由器的定义方式与其他路由器相同，在这种情况下，配置如下：

```yml
akka.actor.deployment {
  /factorialFrontend/factorialBackendRouter = {
    # Router type provided by metrics extension.
    router = cluster-metrics-adaptive-group
    # Router parameter specific for metrics extension.
    # metrics-selector = heap
    # metrics-selector = load
    # metrics-selector = cpu
    metrics-selector = mix
    #
    routees.paths = ["/user/factorialBackend"]
    cluster {
      enabled = on
      use-roles = ["backend"]
      allow-local-routees = off
    }
  }
}
```

只有`router`类型和`metrics-selector`参数特定于此路由器，其他事物的工作方式与其他路由器相同。

同样类型的路由器也可以在代码中定义：

```java
int totalInstances = 100;
Iterable<String> routeesPaths = Arrays.asList("/user/factorialBackend", "");
boolean allowLocalRoutees = true;
Set<String> useRoles = new HashSet<>(Arrays.asList("backend"));
ActorRef backend =
    getContext()
        .actorOf(
            new ClusterRouterGroup(
                    new AdaptiveLoadBalancingGroup(
                        HeapMetricsSelector.getInstance(), Collections.<String>emptyList()),
                    new ClusterRouterGroupSettings(
                        totalInstances, routeesPaths, allowLocalRoutees, useRoles))
                .props(),
            "factorialBackendRouter2");

int totalInstances = 100;
int maxInstancesPerNode = 3;
boolean allowLocalRoutees = false;
Set<String> useRoles = new HashSet<>(Arrays.asList("backend"));
ActorRef backend =
    getContext()
        .actorOf(
            new ClusterRouterPool(
                    new AdaptiveLoadBalancingPool(
                        SystemLoadAverageMetricsSelector.getInstance(), 0),
                    new ClusterRouterPoolSettings(
                        totalInstances, maxInstancesPerNode, allowLocalRoutees, useRoles))
                .props(Props.create(FactorialBackend.class)),
            "factorialBackendRouter3");
```

运行自适应负载平衡示例最简单的方法下载「[Akka Cluster Sample with Java](https://example.lightbend.com/v1/download/akka-samples-cluster-java)」中的代码和教程。它包含有关如何运行自适应负载平衡示例的说明，此示例的源代码也可以在「[ Akka Samples Repository](https://developer.lightbend.com/start/?group=akka&project=akka-sample-cluster-java)」中找到。

## 订阅指标事件

可以直接订阅指标事件来实现其他功能。

```java
import akka.actor.AbstractActor;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent.CurrentClusterState;
import akka.cluster.metrics.ClusterMetricsChanged;
import akka.cluster.metrics.NodeMetrics;
import akka.cluster.metrics.StandardMetrics;
import akka.cluster.metrics.StandardMetrics.HeapMemory;
import akka.cluster.metrics.StandardMetrics.Cpu;
import akka.cluster.metrics.ClusterMetricsExtension;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class MetricsListener extends AbstractActor {
  LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  Cluster cluster = Cluster.get(getContext().getSystem());

  ClusterMetricsExtension extension = ClusterMetricsExtension.get(getContext().getSystem());

  // Subscribe unto ClusterMetricsEvent events.
  @Override
  public void preStart() {
    extension.subscribe(getSelf());
  }

  // Unsubscribe from ClusterMetricsEvent events.
  @Override
  public void postStop() {
    extension.unsubscribe(getSelf());
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            ClusterMetricsChanged.class,
            clusterMetrics -> {
              for (NodeMetrics nodeMetrics : clusterMetrics.getNodeMetrics()) {
                if (nodeMetrics.address().equals(cluster.selfAddress())) {
                  logHeap(nodeMetrics);
                  logCpu(nodeMetrics);
                }
              }
            })
        .match(
            CurrentClusterState.class,
            message -> {
              // Ignore.
            })
        .build();
  }

  void logHeap(NodeMetrics nodeMetrics) {
    HeapMemory heap = StandardMetrics.extractHeapMemory(nodeMetrics);
    if (heap != null) {
      log.info("Used heap: {} MB", ((double) heap.used()) / 1024 / 1024);
    }
  }

  void logCpu(NodeMetrics nodeMetrics) {
    Cpu cpu = StandardMetrics.extractCpu(nodeMetrics);
    if (cpu != null && cpu.systemLoadAverage().isDefined()) {
      log.info("Load: {} ({} processors)", cpu.systemLoadAverage().get(), cpu.processors());
    }
  }
}
```

## 自定义指标收集器

指标集合委托给`akka.cluster.metrics.MetricsCollector`的实现

你也可以插入自己的指标收集器，而不是内置的`akka.cluster.metrics.SigarMetricsCollector`或`akka.cluster.metrics.JmxMetricsCollector`。

看看这两个实现的灵感。

自定义指标收集器实现类必须在`akka.cluster.metrics.collector.provider`配置属性中指定。

## 配置

可以使用以下属性配置群集指标扩展：

```yml
##############################################
# Akka Cluster Metrics Reference Config File #
##############################################

# This is the reference config file that contains all the default settings.
# Make your edits in your application.conf in order to override these settings.

# Sigar provisioning:
#
#  User can provision sigar classes and native library in one of the following ways:
# 
#  1) Use https://github.com/kamon-io/sigar-loader Kamon sigar-loader as a project dependency for the user project.
#  Metrics extension will extract and load sigar library on demand with help of Kamon sigar provisioner.
# 
#  2) Use https://github.com/kamon-io/sigar-loader Kamon sigar-loader as java agent: `java -javaagent:/path/to/sigar-loader.jar`
#  Kamon sigar loader agent will extract and load sigar library during JVM start.
# 
#  3) Place `sigar.jar` on the `classpath` and sigar native library for the o/s on the `java.library.path`
#  User is required to manage both project dependency and library deployment manually.

# Cluster metrics extension.
# Provides periodic statistics collection and publication throughout the cluster.
akka.cluster.metrics {
  # Full path of dispatcher configuration key.
  # Use "" for default key `akka.actor.default-dispatcher`.
  dispatcher = ""
  # How long should any actor wait before starting the periodic tasks.
  periodic-tasks-initial-delay = 1s
  # Sigar native library extract location.
  # Use per-application-instance scoped location, such as program working directory.
  native-library-extract-folder = ${user.dir}"/native"
  # Metrics supervisor actor.
  supervisor {
    # Actor name. Example name space: /system/cluster-metrics
    name = "cluster-metrics"
    # Supervision strategy.
    strategy {
      #
      # FQCN of class providing `akka.actor.SupervisorStrategy`.
      # Must have a constructor with signature `<init>(com.typesafe.config.Config)`.
      # Default metrics strategy provider is a configurable extension of `OneForOneStrategy`.
      provider = "akka.cluster.metrics.ClusterMetricsStrategy"
      #
      # Configuration of the default strategy provider.
      # Replace with custom settings when overriding the provider.
      configuration = {
        # Log restart attempts.
        loggingEnabled = true
        # Child actor restart-on-failure window.
        withinTimeRange = 3s
        # Maximum number of restart attempts before child actor is stopped.
        maxNrOfRetries = 3
      }
    }
  }
  # Metrics collector actor.
  collector {
    # Enable or disable metrics collector for load-balancing nodes.
    # Metrics collection can also be controlled at runtime by sending control messages
    # to /system/cluster-metrics actor: `akka.cluster.metrics.{CollectionStartMessage,CollectionStopMessage}`
    enabled = on
    # FQCN of the metrics collector implementation.
    # It must implement `akka.cluster.metrics.MetricsCollector` and
    # have public constructor with akka.actor.ActorSystem parameter.
    # Will try to load in the following order of priority:
    # 1) configured custom collector 2) internal `SigarMetricsCollector` 3) internal `JmxMetricsCollector`
    provider = ""
    # Try all 3 available collector providers, or else fail on the configured custom collector provider.
    fallback = true
    # How often metrics are sampled on a node.
    # Shorter interval will collect the metrics more often.
    # Also controls frequency of the metrics publication to the node system event bus.
    sample-interval = 3s
    # How often a node publishes metrics information to the other nodes in the cluster.
    # Shorter interval will publish the metrics gossip more often.
    gossip-interval = 3s
    # How quickly the exponential weighting of past data is decayed compared to
    # new data. Set lower to increase the bias toward newer values.
    # The relevance of each data sample is halved for every passing half-life
    # duration, i.e. after 4 times the half-life, a data sample’s relevance is
    # reduced to 6% of its original relevance. The initial relevance of a data
    # sample is given by 1 – 0.5 ^ (collect-interval / half-life).
    moving-average-half-life = 12s
  }
}

# Cluster metrics extension serializers and routers.
akka.actor {
  # Protobuf serializer for remote cluster metrics messages.
  serializers {
    akka-cluster-metrics = "akka.cluster.metrics.protobuf.MessageSerializer"
  }
  # Interface binding for remote cluster metrics messages.
  serialization-bindings {
    "akka.cluster.metrics.ClusterMetricsMessage" = akka-cluster-metrics
    "akka.cluster.metrics.AdaptiveLoadBalancingPool" = akka-cluster-metrics
    "akka.cluster.metrics.MixMetricsSelector" = akka-cluster-metrics
    "akka.cluster.metrics.CpuMetricsSelector$" = akka-cluster-metrics
    "akka.cluster.metrics.HeapMetricsSelector$" = akka-cluster-metrics
    "akka.cluster.metrics.SystemLoadAverageMetricsSelector$" = akka-cluster-metrics
  }
  # Globally unique metrics extension serializer identifier.
  serialization-identifiers {
    "akka.cluster.metrics.protobuf.MessageSerializer" = 10
  }
  #  Provide routing of messages based on cluster metrics.
  router.type-mapping {
    cluster-metrics-adaptive-pool  = "akka.cluster.metrics.AdaptiveLoadBalancingPool"
    cluster-metrics-adaptive-group = "akka.cluster.metrics.AdaptiveLoadBalancingGroup"
  }
}
```


----------

**英文原文链接**：[Cluster Metrics Extension](https://doc.akka.io/docs/akka/current/cluster-metrics.html).



----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————