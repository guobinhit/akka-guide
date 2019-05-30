# 协作

- **警告**：此模块当前标记为「[可能更改](https://doc.akka.io/docs/akka/current/common/may-change.html)」。它已准备好用于生产，但 API 可能在没有警告或预测期的情况下发生变化。

Akka 协作（`Coordination`）是一套用于分布式协作的工具。


`Discovery`曾是 Akka 管理的一部分，但从 Akka 的`2.5.19`版和 Akka 管理的`1.0.0`版开始，它已成为 Akka 的模块。如果你还将 Akka 管理用于其他服务发现方法或加载程序，请确保你至少使用了 Akka 管理（`Management`）的`1.0.0`版本。

## 依赖

为了使用 Akka 协作，你需要将以下依赖添加到你的项目中：

```xml
<!-- Maven -->
<dependency>
  <groupId>com.typesafe.akka</groupId>
  <artifactId>akka-coordination_2.12</artifactId>
  <version>2.5.23</version>
</dependency>

<!-- Gradle -->
dependencies {
  compile group: 'com.typesafe.akka', name: 'akka-coordination_2.12', version: '2.5.23'
}

<!-- sbt -->
libraryDependencies += "com.typesafe.akka" %% "akka-coordination" % "2.5.23"
```

## Lease

`Lease`是分布式锁的可插拔 API。

## 使用 Lease

`Lease`通过以下方式加载：

- `Lease`名称
- 配置位置以指示应加载哪个实现
- 所有者名称

任何`Lease`实现应提供以下保证：

- 同一名称的`Lease`多次加载，即使在不同的节点上，也是同一`Lease`
- 一次只能有一个所有者获得`Lease`

获取`Lease`：

```java
Lease lease =
    LeaseProvider.get(system).getLease("<name of the lease>", "docs-lease", "<owner name>");
CompletionStage<Boolean> acquired = lease.acquire();
boolean stillAcquired = lease.checkLease();
CompletionStage<Boolean> released = lease.release();
```

获取`Lease`会返回一个`CompletionStage`，因为`Lease`实现通常是通过第三方系统（如 Kubernetes API 服务器或 ZooKeeper）实现的。

一旦获得`Lease`，就可以调用`checkLease`以确保`Lease`仍然被获得。由于`Lease`实现基于其他分布式系统，因此`Lease`可能会因第三方系统超时而丢失。此操作不是异步的，因此可以在执行任何具有`Lease`的操作之前调用它。

`Lease`有一个所有者。如果同一所有者多次尝试获取`Lease`，那么它将成功，即`Lease`是可重入的。

选择一个对你的用例来说是唯一的`Lease`名称是很重要的。如果集群中每个节点的`Lease`都需要唯一，那么可以使用群集主机端口：

```java
String owner = Cluster.get(system).selfAddress().hostPort();
```

对于在同一节点上有多个不同`Lease`的用例，必须在名称中添加一些唯一的东西。例如，`Lease`可以与集群分片（`Cluster Sharding`）一起使用，在这种情况下，分片 ID 包含在每个分片的`Lease`名称中。

## 其他 Akka 模块的用途

`Lease`可以用于「[集群单例](https://doc.akka.io/docs/akka/current/cluster-singleton.html#lease)」和「[集群分片](https://doc.akka.io/docs/akka/current/cluster-sharding.html#lease)」。

## Lease 实现

- [Kubernetes API](https://doc.akka.io/docs/akka-enhancements/current/kubernetes-lease.html)

## 实现一个 Lease
实现应该扩展`akka.coordination.lease.javadsl.Lease`

```java
static class SampleLease extends Lease {

  private LeaseSettings settings;

  public SampleLease(LeaseSettings settings) {
    this.settings = settings;
  }

  @Override
  public LeaseSettings getSettings() {
    return settings;
  }

  @Override
  public CompletionStage<Boolean> acquire() {
    return null;
  }

  @Override
  public CompletionStage<Boolean> acquire(Consumer<Optional<Throwable>> leaseLostCallback) {
    return null;
  }

  @Override
  public CompletionStage<Boolean> release() {
    return null;
  }

  @Override
  public boolean checkLease() {
    return false;
  }
}
```

这些方法应提供以下保证：

- `acquire`：如果成功获取`Lease`，则为`true`；如果`Lease`被其他所有者占用，则为`false`；如果无法与实现`Lease`的第三方系统通信，则失败。
- `release`：如果`Lease`已明确释放，则为`true`；如果`Lease`未明确释放，则为`false`；如果不知道`Lease`是否已释放，则失败。
- 在`acquire`的`CompletionStage`完成之前，`checkLease`应返回`false`；如果`Lease`由于与第三方通信错误而丢失，也应该返回`false`。检查`Lease`也不应阻塞。
- `Lease lost callback`只能在`acquire`的`CompletionStage`完成后调用，如果`Lease`丢失（例如，由于与第三方系统的通信丢失），则应调用该回调。

此外，期望`Lease`的实现包括生存时间机制（`a time to live mechanism`），这意味着在节点崩溃的情况下不会永远保留`Lease`。如果用户希望在这种情况下进行外部干预以获得最大的安全性，那么生存时间可以设置为无限。

配置必须为`Lease`实现的 FQCN 定义`lease-class`属性。

如果默认值来自`akka.coordination.lease`，则`Lease`实现应支持以下属性：

```yml
# if the node that acquired the leases crashes, how long should the lease be held before another owner can get it
heartbeat-timeout = 120s

# interval for communicating with the third party to confirm the lease is still held
heartbeat-interval = 12s

# lease implementations are expected to time out acquire and release calls or document
# that they do not implement an operation timeout
lease-operation-timeout = 5s
```

此配置位置将传递到`getLease`。

```java
akka.actor.provider = cluster
docs-lease {
  lease-class = "docs.akka.coordination.SampleLease"
  heartbeat-timeout = 100s
  heartbeat-interval = 1s
  lease-operation-timeout = 1s
  # Any lease specific configuration
}
```


----------

**英文原文链接**：[Coordination](https://doc.akka.io/docs/akka/current/coordination.html).



----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————