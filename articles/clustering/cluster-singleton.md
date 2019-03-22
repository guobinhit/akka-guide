# 集群单例
## 依赖
为了使用集群单例（`Cluster Singleton`），你必须在项目中添加如下依赖：

```xml
<!-- Maven -->
<dependency>
  <groupId>com.typesafe.akka</groupId>
  <artifactId>akka-cluster-tools_2.12</artifactId>
  <version>2.5.21</version>
</dependency>

<!-- Gradle -->
dependencies {
  compile group: 'com.typesafe.akka', name: 'akka-cluster-tools_2.12', version: '2.5.21'
}

<!-- sbt -->
libraryDependencies += "com.typesafe.akka" %% "akka-cluster-tools" % "2.5.21"
```
## 简介
对于某些用例，确保集群中某个类型的某个 Actor 恰好运行在某个位置是方便的，有时也是强制的。

一些例子：

- 对特定的集群范围一致性决策或跨集群系统协调行动的单一责任点
- 外部系统的单一入口点
- 单主多工
- 集中命名服务或路由逻辑

使用单例不应该是第一个设计选择。它有几个缺点，如单点瓶颈。单点故障也是一个相关的问题，但是在某些情况下，这个特性通过确保最终将启动另一个单点实例来解决这个问题。

集群单例模式由`akka.cluster.singleton.ClusterSingletonManager`实现。它在所有集群节点或标记有特定角色的一组节点中管理一个单实例 Actor 实例。`ClusterSingletonManager`是一个 Actor，它应该在集群中的所有节点或具有指定角色的所有节点上尽早启动。实际的单例 Actor 是由最老节点上的`ClusterSingletonManager`通过从提供的`Props`创建子 Actor 来启动的。`ClusterSingletonManager`确保在任何时间点最多运行一个单实例。

单例 Actor 总是在具有指定角色的最老成员上运行。最老的成员由`akka.cluster.Member#isOlderThan`确定。从群集中删除该成员时，这可能会发生变化。请注意，在移交（`hand-over`）过程中，如果没有活动的单例，则将是一个很短的时间段。

当最老的节点由于诸如 JVM 崩溃、硬关闭或网络故障而无法访问时，集群故障检测器会注意到。然后将接管一个新的最老节点，并创建一个新的单例 Actor。对于这些故障场景，将不会有一个优雅的移交，但通过所有合理的方法阻止了多个活动的单例。对于其他情况，最终可以通过配置超时来解决。

你可以使用提供的`akka.cluster.singleton.ClusterSingletonProxy`访问单例 Actor，该代理将所有消息路由到单例的当前实例。代理将跟踪集群中最老的节点，并通过显式发送单例的`actorSelection` 的`akka.actor.Identify`消息并等待其回复来解析单例的`ActorRef`。如果单例（`singleton`）在特定（可配置）时间内没有回复，则会定期执行此操作。考虑到实现，可能会有一段时间内`ActorRef`不可用，例如，当节点离开集群时。在这些情况下，代理将缓冲发送到单例的消息，然后在单例最终可用时传递它们。如果缓冲区已满，则当通过代理发送新消息时，`ClusterSingletonProxy`将删除旧消息。缓冲区的大小是可配置的，可以通过使用`0`的缓冲区大小来禁用它。

值得注意的是，由于这些 Actor 的分布式特性，消息总是会丢失。一如既往，额外的逻辑应该在单例（确认）和客户机（重试）Actor 中实现，以确保至少一次消息传递。

单例实例不会在状态为`WeaklyUp`的成员上运行。

## 需要注意的潜在问题

这种模式一开始似乎很吸引人，但它有几个缺点，其中一些缺点如下所示：

- 集群单例可能很快成为性能瓶颈，
- 你不能依赖集群单例不间断地可用，例如，当运行单例的节点死亡时，需要几秒钟的时间才能注意到这一点，并将单例迁移到另一个节点，
- 在使用自动关闭（`Automatic Downing`）的集群中出现网络分裂的情况下（参见文档中的自「[Auto Downing](https://doc.akka.io/docs/akka/current/cluster-usage.html#automatic-vs-manual-downing)」），可能会发生孤立的集群并各自决定成为它们自己的单例，这意味着系统中可能有多个单例运行，但是这些集群无法发现它们（因为网络分裂）

尤其最后一点是你应该注意的。一般来说，当使用集群单例模式时，你应该自己处理`downing`的节点，而不是依赖于基于时间的自动关闭功能。

- **警告**：不要将集群单例与自动关闭一起使用，因为它允许集群分裂为两个单独的集群，从而导致启动多个单例，每个单独的集群中都有一个单例！

## 示例

假设我们需要一个到外部系统的单一入口点。从 JMS 队列接收消息的 Actor，严格要求只有一个 JMS 消费者才能确保消息按顺序处理。这也许不是人们想要如何设计事物，而是与外部系统集成时典型的现实场景。

在解释如何创建集群单例 Actor 之前，我们先定义将由单例使用的消息类。

```java
public class TestSingletonMessages {
  public static class UnregistrationOk {}

  public static class End {}

  public static class Ping {}

  public static class Pong {}

  public static class GetCurrent {}

  public static UnregistrationOk unregistrationOk() {
    return new UnregistrationOk();
  }

  public static End end() {
    return new End();
  }

  public static Ping ping() {
    return new Ping();
  }

  public static Pong pong() {
    return new Pong();
  }

  public static GetCurrent getCurrent() {
    return new GetCurrent();
  }
}
```

在集群中的每个节点上，你需要启动`ClusterSingletonManager`并提供单例 Actor 的`Props`，在本例中是 JMS 队列消费者。

```java
final ClusterSingletonManagerSettings settings =
    ClusterSingletonManagerSettings.create(system).withRole("worker");

system.actorOf(
    ClusterSingletonManager.props(
        Props.create(Consumer.class, () -> new Consumer(queue, testActor)),
        TestSingletonMessages.end(),
        settings),
    "consumer");
```

在这里，我们将单例限制为使用`worker`角色标记的节点，但是所有独立于角色的节点都可以不指定`withRole`来使用。

我们使用一个特定于应用程序的`terminationMessage`（即`TestSingletonMessages.end()`消息）来在实际停止单例 Actor 之前关闭资源。请注意，如果你只需要停止 Actor，`PoisonPill`是一个完美的`terminationMessage`。

下面是这个示例中，单例 Actor 如何处理`terminationMessage`。

```java
.match(End.class, message -> queue.tell(UnregisterConsumer.class, getSelf()))
.match(
    UnregistrationOk.class,
    message -> {
      stoppedBeforeUnregistration = false;
      getContext().stop(getSelf());
    })
.match(Ping.class, message -> getSender().tell(TestSingletonMessages.pong(), getSelf()))
```

使用上面给出的名称，可以使用正确配置的代理从任何集群节点获得对单例的访问。

```java
ClusterSingletonProxySettings proxySettings =
    ClusterSingletonProxySettings.create(system).withRole("worker");

ActorRef proxy =
    system.actorOf(
        ClusterSingletonProxy.props("/user/consumer", proxySettings), "consumerProxy");
```

在「[Distributed workers with Akka and Java](https://github.com/typesafehub/activator-akka-distributed-workers-java)」中，有一个更全面的示例！

## 配置

当使用`ActorSystem`参数创建时，`ClusterSingletonManagerSettings`将读取以下配置属性。还可以修改`ClusterSingletonManagerSettings`，或者从另一个具有如下相同布局的配置部分创建它。`ClusterSingletonManagerSettings`是`ClusterSingletonManager.props`工厂方法的一个参数，也就是说，如果需要，可以使用不同的设置配置每个单例。

```
akka.cluster.singleton {
  # The actor name of the child singleton actor.
  singleton-name = "singleton"
  
  # Singleton among the nodes tagged with specified role.
  # If the role is not specified it's a singleton among all nodes in the cluster.
  role = ""
  
  # When a node is becoming oldest it sends hand-over request to previous oldest, 
  # that might be leaving the cluster. This is retried with this interval until 
  # the previous oldest confirms that the hand over has started or the previous 
  # oldest member is removed from the cluster (+ akka.cluster.down-removal-margin).
  hand-over-retry-interval = 1s
  
  # The number of retries are derived from hand-over-retry-interval and
  # akka.cluster.down-removal-margin (or ClusterSingletonManagerSettings.removalMargin),
  # but it will never be less than this property.
  # After the hand over retries and it's still not able to exchange the hand over messages
  # with the previous oldest it will restart itself by throwing ClusterSingletonManagerIsStuck,
  # to start from a clean state. After that it will still not start the singleton instance
  # until the previous oldest node has been removed from the cluster.
  # On the other side, on the previous oldest node, the same number of retries - 3 are used
  # and after that the singleton instance is stopped.
  # For large clusters it might be necessary to increase this to avoid too early timeouts while
  # gossip dissemination of the Leaving to Exiting phase occurs. For normal leaving scenarios
  # it will not be a quicker hand over by reducing this value, but in extreme failure scenarios
  # the recovery might be faster.
  min-number-of-hand-over-retries = 15
}
```

当使用`ActorSystem`参数创建时，`ClusterSingletonProxySettings`将读取以下配置属性。还可以修改`ClusterSingletonProxySettings`，或者从另一个具有如下相同布局的配置部分创建它。`ClusterSingletonProxySettings`是`ClusterSingletonProxy.props`工厂方法的参数，也就是说，如果需要，可以使用不同的设置配置每个单例代理。

```
akka.cluster.singleton-proxy {
  # The actor name of the singleton actor that is started by the ClusterSingletonManager
  singleton-name = ${akka.cluster.singleton.singleton-name}
  
  # The role of the cluster nodes where the singleton can be deployed. 
  # If the role is not specified then any node will do.
  role = ""
  
  # Interval at which the proxy will try to resolve the singleton instance.
  singleton-identification-interval = 1s
  
  # If the location of the singleton is unknown the proxy will buffer this
  # number of messages and deliver them when the singleton is identified. 
  # When the buffer is full old messages will be dropped when new messages are
  # sent via the proxy.
  # Use 0 to disable buffering, i.e. messages will be dropped immediately if
  # the location of the singleton is unknown.
  # Maximum allowed buffer size is 10000.
  buffer-size = 1000 
}
```

## 监督

有两个 Actor 可能会受到监督。对于上面创建的消费者单例，这些将是：

- 集群单例管理器，例如运行在集群中每个节点上的`/user/consumer`
- 用户 Actor，例如`/user/consumer/singleton`，管理器从最老的节点开始。

集群单例管理器 Actor 不应该改变其监视策略，因为它应该一直在运行。但是，有时添加对用户 Actor 的监督是有用的。要完成此操作，请添加一个父监督者 Actor，该 Actor 将用于创建“真正”的单例实例。下面是一个示例实现（归功于这个「[StackOverflow](https://stackoverflow.com/questions/36701898/how-to-supervise-cluster-singleton-in-akka/36716708#36716708)」答案）

```java
import akka.actor.AbstractActor;
import akka.actor.AbstractActor.Receive;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;

public class SupervisorActor extends AbstractActor {
  final Props childProps;
  final SupervisorStrategy supervisorStrategy;
  final ActorRef child;

  SupervisorActor(Props childProps, SupervisorStrategy supervisorStrategy) {
    this.childProps = childProps;
    this.supervisorStrategy = supervisorStrategy;
    this.child = getContext().actorOf(childProps, "supervised-child");
  }

  @Override
  public SupervisorStrategy supervisorStrategy() {
    return supervisorStrategy;
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder().matchAny(msg -> child.forward(msg, getContext())).build();
  }
}
```

然后，在这里使用它：

```java
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;
```

```java
return getContext()
    .system()
    .actorOf(
        ClusterSingletonManager.props(
            Props.create(
                SupervisorActor.class, () -> new SupervisorActor(props, supervisorStrategy)),
            PoisonPill.getInstance(),
            ClusterSingletonManagerSettings.create(getContext().system())),
        name = name);
```


----------

**英文原文链接**：[Cluster Singleton](https://doc.akka.io/docs/akka/current/cluster-singleton.html).





----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————