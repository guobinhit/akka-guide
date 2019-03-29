# 调度器
## 依赖
调度器（`Dispatchers`）是 Akka 核心的一部分，这意味着它们也是`akka-actor`依赖的一部分：

```xml
<!-- Maven -->
<dependency>
  <groupId>com.typesafe.akka</groupId>
  <artifactId>akka-actor_2.12</artifactId>
  <version>2.5.21</version>
</dependency>

<!-- Gradle -->
dependencies {
  compile group: 'com.typesafe.akka', name: 'akka-actor_2.12', version: '2.5.21'
}

<!-- sbt -->
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.5.21"
```

## 简介

正如在「[Actor System](https://doc.akka.io/docs/akka/current/general/actor-systems.html)」中所解释的，每个 Actor 都是其子级的监督者，因此每个 Actor 定义了故障处理的监督策略。这一策略不能在 Actor 系统启动之后改变，因为它是 Actor 系统结构的一个组成部分。

Akka 的`MessageDispatcher`是 Akka Actor “`tick`”的原因，可以说，它是机器的引擎。所有`MessageDispatcher`实现也是一个`ExecutionContext`，这意味着它们可以用于执行任意代码，例如「[Futures](https://doc.akka.io/docs/akka/current/futures.html)」。

## 默认调度器

每个`ActorSystem`都将有一个默认的调度器，在没有为 Actor 配置其他内容的情况下使用该调度器。可以配置默认调度器，默认情况下是具有指定`default-executor`的`Dispatcher`。如果在传入`ExecutionContext`的情况下创建`ActorSystem`，则此`ExecutionContext`将用作此`ActorSystem`中所有调度程序的默认执行器。如果没有给定`ExecutionContext`，它将回退到在`akka.actor.default-dispatcher.default-executor.fallback`中指定的执行器。默认情况下，这是一个“`fork-join-executor`”，在大多数情况下，它提供了出色的性能。

## 查找调度器

调度器实现`ExecutionContext`接口，因此可以用于运行`Future`的调用等。

```java
// this is scala.concurrent.ExecutionContext
// for use with Futures, Scheduler, etc.
final ExecutionContext ex = system.dispatchers().lookup("my-dispatcher");
```

## 为 Actor 设置调度器

因此，如果你想给你的 Actor 一个不同于默认的调度器，你需要做两件事，第一件事是配置调度器：

```yml
my-dispatcher {
  # Dispatcher is the name of the event-based dispatcher
  type = Dispatcher
  # What kind of ExecutionService to use
  executor = "fork-join-executor"
  # Configuration for the fork join pool
  fork-join-executor {
    # Min number of threads to cap factor-based parallelism number to
    parallelism-min = 2
    # Parallelism (threads) ... ceil(available processors * factor)
    parallelism-factor = 2.0
    # Max number of threads to cap factor-based parallelism number to
    parallelism-max = 10
  }
  # Throughput defines the maximum number of messages to be
  # processed per actor before the thread jumps to the next actor.
  # Set to 1 for as fair as possible.
  throughput = 100
}
```

- **注释**：请注意，`parallelism-max`不会在`ForkJoinPool`分配的线程总数上设置上限。这是一个设置，专门讨论池保持运行的热线程数，以减少处理新的传入任务的延迟。你可以在 JDK 的「[ForkJoinPool 文档](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ForkJoinPool.html)」中了解更多关于并行性的信息。

另一个使用“`thread-pool-executor`”的示例：

```yml
blocking-io-dispatcher {
  type = Dispatcher
  executor = "thread-pool-executor"
  thread-pool-executor {
    fixed-pool-size = 32
  }
  throughput = 1
}
```

- **注释**：线程池执行器调度程序由`java.util.concurrent.ThreadPoolExecutor`实现。你可以在 JDK 的「[ThreadPoolExecutor 文档](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ThreadPoolExecutor.html)」中了解更多关于它的信息。

有关更多选项，请参阅「[配置](https://doc.akka.io/docs/akka/current/general/configuration.html)」的默认调度器部分。

然后你就可以像往常一样创建 Actor，并在部署配置中定义调度器。

```java
ActorRef myActor = system.actorOf(Props.create(MyActor.class), "myactor");
```

```yml
akka.actor.deployment {
  /myactor {
    dispatcher = my-dispatcher
  }
}
```

部署配置的另一种选择是在代码中定义调度器。如果在部署配置中定义`dispatcher`，则将使用此值，而不是以编程方式提供的参数。

```java
ActorRef myActor =
    system.actorOf(Props.create(MyActor.class).withDispatcher("my-dispatcher"), "myactor3");
```

- **注释**：在`withDispatcher`中指定的调度器和部署配置中的`dispatcher`属性实际上是进入配置的路径。所以在这个例子中，它是一个顶级部分，但是你可以把它作为一个子部分，在这里你可以用句点来表示子部分，就像这样：`foo.bar.my-dispatcher`

## 调度器类型

有 3 种不同类型的消息调度器：

- `Dispatcher`：这是一个基于事件的调度程序，它将一组 Actor 绑定到线程池。如果未指定调度器，则使用默认调度器。
  - 可共享性：`Unlimited`
  - 邮箱：任意，为每个 Actor 创建一个
  - 用例：默认调度器，`Bulkheading`
  - 驱动：`java.util.concurrent.ExecutorService`。使用`fork-join-executor`、`thread-pool-executor`或`akka.dispatcher.ExecutorServiceConfigurator`的`FQCN`指定的`executor`。
- `PinnedDispatcher`：这个调度器为每个使用它的 Actor 指定唯一的线程；即每个 Actor 将拥有自己的线程池，池中只有一个线程。
  - 可共享性：`None`
  - 邮箱：任意，为每个 Actor 创建一个
  - 用例：`Bulkheading`
  - 驱动：任何`akka.dispatch.ThreadPoolExecutorConfigurator`。默认情况下为`thread-pool-executor`。
- `CallingThreadDispatcher`：此调度器仅在当前调用的线程上运行。这个调度器不创建任何新的线程，但是它可以从不同的线程并发地用于同一个 Actor。有关详细信息和限制，请参阅「[CallingThreadDispatcher](https://doc.akka.io/docs/akka/current/testing.html#callingthreaddispatcher)」。
  - 可共享性：`Unlimited`
  - 邮箱：任意，为每个 Actor 创建一个（按需）
  - 用例：`Testing`
  - 驱动：调用线程（`duh`）

### 更多调度器配置示例

配置具有固定线程池大小的调度器，例如，对于执行阻塞 IO 的 Actor：

```yml
blocking-io-dispatcher {
  type = Dispatcher
  executor = "thread-pool-executor"
  thread-pool-executor {
    fixed-pool-size = 32
  }
  throughput = 1
}
```

然后使用它：

```java
ActorRef myActor =
    system.actorOf(Props.create(MyActor.class).withDispatcher("blocking-io-dispatcher"));
```

另一个基于核（`cores`）数量使用线程池的示例，例如，对于 CPU 绑定的任务：

```yml
my-thread-pool-dispatcher {
  # Dispatcher is the name of the event-based dispatcher
  type = Dispatcher
  # What kind of ExecutionService to use
  executor = "thread-pool-executor"
  # Configuration for the thread pool
  thread-pool-executor {
    # minimum number of threads to cap factor-based core number to
    core-pool-size-min = 2
    # No of core threads ... ceil(available processors * factor)
    core-pool-size-factor = 2.0
    # maximum number of threads to cap factor-based number to
    core-pool-size-max = 10
  }
  # Throughput defines the maximum number of messages to be
  # processed per actor before the thread jumps to the next actor.
  # Set to 1 for as fair as possible.
  throughput = 100
}
```

在保持某些内部状态的 Actor 数量相对较少的情况下，使用关联池（`affinity pool`）的不同类型的调度器可能会增加吞吐量。关联池尽可能的确保 Actor 总是被安排在同一线程上运行。这个 Actor 到线程的连接（`pinning`）旨在增加 CPU 缓存命中率，这可能使吞吐量显著提高。

```yml
affinity-pool-dispatcher {
  # Dispatcher is the name of the event-based dispatcher
  type = Dispatcher
  # What kind of ExecutionService to use
  executor = "affinity-pool-executor"
  # Configuration for the thread pool
  affinity-pool-executor {
    # Min number of threads to cap factor-based parallelism number to
    parallelism-min = 8
    # Parallelism (threads) ... ceil(available processors * factor)
    parallelism-factor = 1
    # Max number of threads to cap factor-based parallelism number to
    parallelism-max = 16
  }
  # Throughput defines the maximum number of messages to be
  # processed per actor before the thread jumps to the next actor.
  # Set to 1 for as fair as possible.
  throughput = 100
}
```

配置一个`PinnedDispatcher`：

```yml
my-pinned-dispatcher {
  executor = "thread-pool-executor"
  type = PinnedDispatcher
}
```

然后使用它：

```java
ActorRef myActor =
    system.actorOf(Props.create(MyActor.class).withDispatcher("my-pinned-dispatcher"));
```

注意，根据上面的`my-thread-pool-dispatcher`示例，`thread-pool-executor`配置不适用（`NOT applicable`）。这是因为每个 Actor 在使用`PinnedDispatcher`时都有自己的线程池，而该池只有一个线程。

注意，不能保证随着时间的推移使用相同的线程，因为核心池超时用于`PinnedDispatcher`，以在空闲 Actor 的情况下保持资源使用率低。要始终使用同一线程，需要添加`PinnedDispatcher`的配置`thread-pool-executor.allow-core-timeout=off`。

## 阻塞需要小心管理

在某些情况下，不可避免地要执行阻塞操作，即让线程休眠一段不确定的时间，等待发生外部事件。例如，传统的 RDBMS 驱动程序或消息传递 API，其根本原因通常是（网络）I/O 发生在表面之下（`occurs under the covers`）。

```java
class BlockingActor extends AbstractActor {

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            Integer.class,
            i -> {
              Thread.sleep(5000); // block for 5 seconds, representing blocking I/O, etc
              System.out.println("Blocking operation finished: " + i);
            })
        .build();
  }
}
```

面对这种情况，你可能会试图将阻塞调用包装在`Future`，并改为使用它，但这种策略过于简单：当应用程序在增加的负载下运行时，很可能会发现瓶颈或内存或线程不足。

```java
class BlockingFutureActor extends AbstractActor {
  ExecutionContext ec = getContext().getDispatcher();

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            Integer.class,
            i -> {
              System.out.println("Calling blocking Future: " + i);
              Future<Integer> f =
                  Futures.future(
                      () -> {
                        Thread.sleep(5000);
                        System.out.println("Blocking future finished: " + i);
                        return i;
                      },
                      ec);
            })
        .build();
  }
}
```

### 问题：在默认调度器上阻塞

在这里，关键的一行是：

```java
ExecutionContext ec = getContext().getDispatcher();
```

使用`getContext().getDispatcher()`作为调度器，在该调度器上阻塞`Future`的执行可能是一个问题，因为默认情况下，除非为 Actor 设置单独的调度器，否则此调度器也将用于所有其他 Actor。

如果所有可用的线程都被阻塞，那么同一调度器上的所有 Actor 都将因线程而发生饥饿，并且无法处理传入的消息。

- **注释**：如果可能，还应避免阻塞 API。尝试寻找或构建`Reactive` API，以便将阻塞最小化，或者将其转移到专用的调度器。通常在与现有库或系统集成时，不可能避免阻塞 API，下面的解决方案解释了如何正确处理阻塞操作。请注意，同样的提示也适用于管理 Akka 中任何地方的阻塞操作，包括流、HTTP 和其他构建在其上的响应式库。

让我们用上面的`BlockingFutureActor`和下面的`PrintActor`设置一个应用程序。

```java
class PrintActor extends AbstractActor {
  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            Integer.class,
            i -> {
              System.out.println("PrintActor: " + i);
            })
        .build();
  }
}
```

```java
ActorRef actor1 = system.actorOf(Props.create(BlockingFutureActor.class));
ActorRef actor2 = system.actorOf(Props.create(PrintActor.class));

for (int i = 0; i < 100; i++) {
  actor1.tell(i, ActorRef.noSender());
  actor2.tell(i, ActorRef.noSender());
}
```

在这里，应用程序向`BlockingFutureActor`和`PrintActor`发送 100 条消息，大量`akka.actor.default-dispatcher`线程正在处理请求。当你运行上述代码时，很可能会看到整个应用程序被卡在如下位置：

```
>　PrintActor: 44
>　PrintActor: 45
```

`PrintActor`被认为是非阻塞的，但是它不能继续处理剩余的消息，因为所有线程都被另一个阻塞 Actor 占用和阻塞，从而导致线程不足。

在下面的螺纹状态图中，颜色具有以下含义：

- 天蓝色 - 休眠状态
- 橙色 - 等待状态
- 绿色 - 运行状态

线程信息是使用`YourKit profiler`记录的，但是任何好的 JVM `profiler`都有这个特性，包括免费的和与 Oracle JDK VisualVM 捆绑的，以及 Oracle Flight Recorder。

线程的橙色部分表示它处于空闲状态。空闲线程很好，它们准备接受新的工作。然而，大量的天蓝色（阻塞，或者像我们的例子中那样休眠）线程是非常糟糕的，会导致线程饥饿。

- **注释**：如果你订阅了 LightBend 的商业服务，你可以使用「[线程饥饿检测器](https://developer.lightbend.com/docs/akka-commercial-addons/current/starvation-detector.html)」，如果它检测到你的任何调度程序有饥饿和其他问题，它将发出警告日志语句。这是识别生产系统中发生的问题的有用步骤，然后你可以应用下面解释的建议解决方案。

![turquoise-orange-green](https://github.com/guobinhit/akka-guide/blob/master/images/actors/dispatchers/turquoise-orange-green.png)
在上面的示例中，我们通过向阻塞 Actor 发送数百条消息来加载代码，这会导致默认调度器的线程被阻塞。然后，Akka 中基于`fork join`池的调度器尝试通过向池中添加更多线程来补偿此阻塞（`default-akka.actor.default-dispatcher 18,19,20,...`）。但是，如果这些操作会立即被阻塞，并且最终阻塞操作将主宰整个调度器，那么这将无济于事。

实质上，`Thread.sleep`操作控制了所有线程，并导致在默认调度器上执行的任何操作都需要资源，包括尚未为其配置显式调度器的任何 Actor。

### 解决方案：用于阻塞操作的专用调度器

隔离阻塞行为以使其不影响系统其余部分的最有效方法之一是，为所有这些阻塞操作准备和使用专用调度器。这种技术通常被称为“`bulk-heading`”或简单的“`isolating blocking`”。

在`application.conf`中，专门用于阻塞行为的调度器应配置如下：

```yml
my-blocking-dispatcher {
  type = Dispatcher
  executor = "thread-pool-executor"
  thread-pool-executor {
    fixed-pool-size = 16
  }
  throughput = 1
}
```

基于`thread-pool-executor`的调度器允许我们对它将承载的线程数设置限制，这样我们就可以严格控制系统中最多有多少被阻塞的线程。

具体的大小应该根据你期望在此调度器上运行的工作负载以及运行应用程序的计算机的核数量（`number of cores`）进行微调。通常，核数周围的小数字是一个很好的默认值。

每当需要进行阻塞时，使用上面配置的调度器而不是默认调度程序：

```java
class SeparateDispatcherFutureActor extends AbstractActor {
  ExecutionContext ec = getContext().getSystem().dispatchers().lookup("my-blocking-dispatcher");

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            Integer.class,
            i -> {
              System.out.println("Calling blocking Future on separate dispatcher: " + i);
              Future<Integer> f =
                  Futures.future(
                      () -> {
                        Thread.sleep(5000);
                        System.out.println("Blocking future finished: " + i);
                        return i;
                      },
                      ec);
            })
        .build();
  }
}
```

线程池行为如下图所示：

![turquoise-orange-green-2](https://github.com/guobinhit/akka-guide/blob/master/images/actors/dispatchers/turquoise-orange-green-2.png)

发送给`SeparateDispatcherFutureActor`和`PrintActor`的消息由默认调度器处理，绿线表示实际执行。

在`my-blocking-dispatcher`上运行阻塞操作时，它使用线程（达到配置的限制）来处理这些操作。在这种情况下，休眠与这个调度器很好地隔离开来，默认的调度器不受影响，允许应用程序的其余部分继续运行，就好像没有发生什么不好的事情一样。经过一段时间的空闲之后，由这个调度程序启动的线程将被关闭。

在这种情况下，其他 Actor 的吞吐量没有受到影响，它们仍然在默认调度器上工作。

这是处理响应式应用程序中任何类型的阻塞的推荐方法。

有关 Akka HTTP 的类似讨论，请参阅「[Handling blocking operations in Akka HTTP](https://doc.akka.io/docs/akka-http/current/handling-blocking-operations-in-akka-http-routes.html?language=java#handling-blocking-operations-in-akka-http)」。

### 阻止操作的可用解决方案

针对“阻塞问题”的充分解决方案的非详尽清单包括以下建议：

- 在由路由器管理的 Actor（或一组 Actor）内执行阻塞调用，确保配置专门用于此目的或足够大的线程池。
- 在`Future`上执行阻塞调用，确保在任何时间点对此类调用的数量上限，提交无限数量的此类任务将耗尽内存或线程限制。
- 在`Future`执行阻塞调用，为线程池提供一个线程数上限，该上限适用于运行应用程序的硬件，如本节中详细介绍的那样。
- 指定一个线程来管理一组阻塞资源（例如，驱动多个通道的 NIO 选择器），并在事件作为 Actor 消息发生时分派它们。

第一种可能性特别适用于本质上是单线程的资源，例如数据库句柄，传统上一次只能执行一个未完成的查询，并使用内部同步来确保这一点。一种常见的模式是为`N`个 Actor 创建一个路由器，每个 Actor 包装一个 DB 连接，并处理发送到路由器的查询。然后，必须根据最大吞吐量调整数量`N`，这将根据部署在哪个硬件上的 DBMS 而有所不同。

- **注释**：配置线程池是一项最适合授权给 Akka 的任务，在`application.conf`中对其进行配置，并通过`ActorSystem`进行实例化。



----------

**英文原文链接**：[Dispatchers](https://doc.akka.io/docs/akka/current/dispatchers.html).





----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————