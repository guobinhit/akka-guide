# 调度器
## 依赖

调度器（`Dispatchers`）是核心 Akka 的一部分，这意味着它们是`akka-actor-typed`依赖的一部分：

```xml
<!-- Maven -->
<dependency>
  <groupId>com.typesafe.akka</groupId>
  <artifactId>akka-actor-typed_2.12</artifactId>
  <version>2.5.23</version>
</dependency>

<!-- Gradle -->
dependencies {
  compile group: 'com.typesafe.akka', name: 'akka-actor-typed_2.12', version: '2.5.23'
}

<!-- sbt -->
libraryDependencies += "com.typesafe.akka" %% "akka-actor-typed" % "2.5.23"
```

## 简介

Akka 的`MessageDispatcher`是Akka Actor `tick`的原因，可以说，它是机器的引擎。所有`MessageDispatcher`实现也是一个执行器，这意味着它们可以用于执行任意代码，例如「[Futures](https://doc.akka.io/docs/akka/current/futures.html)」。

## 选择调度器

当不指定自定义调度器的时候，默认调度器用于生成所有 Actor。这适用于所有不阻塞的 Actor。阻塞 Actor 需要小心管理，「[这里](https://doc.akka.io/docs/akka/current/dispatchers.html#blocking-needs-careful-management)」有更多的细节。

要选择调度器，请使用`DispatcherSelector`创建用于生成 Actor 的`Props`实例：

```java
context.spawn(yourBehavior, "DefaultDispatcher");
context.spawn(
    yourBehavior, "ExplicitDefaultDispatcher", DispatcherSelector.defaultDispatcher());
context.spawn(yourBehavior, "BlockingDispatcher", DispatcherSelector.blocking());
context.spawn(
    yourBehavior,
    "DispatcherFromConfig",
    DispatcherSelector.fromConfig("your-dispatcher"));
```

`DispatcherSelector`有两种方便的方法来查找默认的调度器和自定义调度器，你可以使用它来执行阻塞的 Actor，例如不支持`CompletionStages`的遗留数据库 API。

最后一个示例演示如何从配置中加载自定义调度器，并在`application.conf`中对此进行答复：

```yml
your-dispatcher {
  type = Dispatcher
  executor = "thread-pool-executor"
  thread-pool-executor {
    fixed-pool-size = 32
  }
  throughput = 1
}
```

有关如何配置自定义调度器的完整详细信息，请参阅「[非类型化文档](https://doc.akka.io/docs/akka/current/dispatchers.html#types-of-dispatchers)」。


----------

**英文原文链接**：[Dispatchers](https://doc.akka.io/docs/akka/current/typed/dispatchers.html).




----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————