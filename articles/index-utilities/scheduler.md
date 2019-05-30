# 调度程序
## 依赖

为了使用调度程序（`Scheduler`），你需要将以下依赖添加到你的项目中：

```xml
<!-- Maven -->
<dependency>
  <groupId>com.typesafe.akka</groupId>
  <artifactId>akka-actor_2.12</artifactId>
  <version>2.5.23</version>
</dependency>

<!-- Gradle -->
dependencies {
  compile group: 'com.typesafe.akka', name: 'akka-actor_2.12', version: '2.5.23'
}

<!-- sbt -->
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.5.23"
```

## 简介
有时候需要让事情在未来发生，那你去哪里看呢？除了`ActorSystem`，不需要看其他的！在这里，你可以找到返回`akka.actor.Scheduler`实例的`scheduler`方法，该实例对于每个`ActorSystem`都是唯一的，并且在内部用于调度在特定时间点发生的事情。

你可以安排向 Actor 发送消息和执行任务（函数或`Runnable`）。你将得到一个`Cancellable`返回，你也可以调用`cancel`来取消计划操作的执行。

当对 Actor 中的定期或单个消息进行自我调度时，建议使用「[Actor Timers](https://doc.akka.io/docs/akka/current/actors.html#actors-timers)」，而不是直接使用`Scheduler`。

Akka 中的调度程序设计用于高吞吐量的数千到数百万个触发器。主要的用例是触发器 Actor 接收超时、`Future`的超时、断路器（`circuit breakers`）和其他与时间相关的事件，这些事件总是同时发生在许多情况下。该实现基于哈希轮计时器（`Hashed Wheel Timer`），这是处理此类用例的已知数据结构和算法，如果你想了解其内部工作原理，请参阅 Varghese 和 Lauck 的「[Hashed and Hierarchical Timing Wheels](http://www.cs.columbia.edu/~nahum/w6998/papers/sosp87-timing-wheels.pdf)」白皮书。

Akka 调度程序不是为长期调度而设计的（请参阅「[akka-quartz-scheduler](https://github.com/enragedginger/akka-quartz-scheduler)」，而不是这个用例），也不用于高精度的事件触发。在未来，你可以安排触发事件的最长时间约为 8 个月，实际上，这太多了，无法发挥作用，因为这将假定系统在这段时间内从未停止。如果你需要长期的调度，我们强烈建议你寻找替代的调度程序，因为这不是实现 Akka 调度程序的用例。

- **警告**：Akka 使用的调度程序的默认实现基于作业桶（`job buckets`），作业桶根据固定的计划清空。它不会在准确的时间执行任务，但在每一个时间点上，它将运行所有到期（超过）的内容。默认计划程序的准确性可以由`akka.scheduler.tick-duration`配置属性修改。

## 一些示例

```java
import akka.actor.Props;
import jdocs.AbstractJavaTest;
import java.time.Duration;
```

计划在 50 毫秒后向`testActor`发送`foo`消息：

```java
system
    .scheduler()
    .scheduleOnce(Duration.ofMillis(50), testActor, "foo", system.dispatcher(), null);
```

调度一个`Runnable`，它将当前时间发送给`testActor`，在 50 毫秒后执行：

```java
system
    .scheduler()
    .scheduleOnce(
        Duration.ofMillis(50),
        new Runnable() {
          @Override
          public void run() {
            testActor.tell(System.currentTimeMillis(), ActorRef.noSender());
          }
        },
        system.dispatcher());
```

在 0 毫秒后向`tickActor`发送`Tick`消息，并于每 50 毫秒后重复发生此消息：

```java
class Ticker extends AbstractActor {
  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .matchEquals(
            "Tick",
            m -> {
              // Do someting
            })
        .build();
  }
}

ActorRef tickActor = system.actorOf(Props.create(Ticker.class, this));

// This will schedule to send the Tick-message
// to the tickActor after 0ms repeating every 50ms
Cancellable cancellable =
    system
        .scheduler()
        .schedule(
            Duration.ZERO, Duration.ofMillis(50), tickActor, "Tick", system.dispatcher(), null);

// This cancels further Ticks to be sent
cancellable.cancel();
```

- **警告**：如果调度函数或`Runnable`实例，则应特别注意不要关闭不稳定的引用。在实践中，这意味着不要在 Actor 实例的范围内的闭包中使用`this`，不要直接访问`sender()`，也不要直接调用 Actor 实例的方法。如果需要调度调用，请将消息调度为`self`（包含必要的参数），然后在收到消息时调用该方法。

## 来自 akka.actor.ActorSystem

```java
/**
 * Light-weight scheduler for running asynchronous tasks after some deadline
 * in the future. Not terribly precise but cheap.
 */
def scheduler: Scheduler
```

- **警告**：当`ActorSystem`终止时，所有计划的任务都将被执行，即任务可以在超时之前执行。

## Scheduler 接口

实际的调度程序实现是在`ActorSystem`启动时反射加载的，这意味着可以使用`akka.scheduler.implementation`配置属性提供不同的实现。引用的类必须实现以下接口：

```java
/**
 * An Akka scheduler service. This one needs one special behavior: if Closeable, it MUST execute all
 * outstanding tasks upon .close() in order to properly shutdown all dispatchers.
 *
 * <p>Furthermore, this timer service MUST throw IllegalStateException if it cannot schedule a task.
 * Once scheduled, the task MUST be executed. If executed upon close(), the task may execute before
 * its timeout.
 *
 * <p>Scheduler implementation are loaded reflectively at ActorSystem start-up with the following
 * constructor arguments: 1) the system’s com.typesafe.config.Config (from system.settings.config)
 * 2) a akka.event.LoggingAdapter 3) a java.util.concurrent.ThreadFactory
 */
public abstract class AbstractScheduler extends AbstractSchedulerBase {

  /**
   * Schedules a function to be run repeatedly with an initial delay and a frequency. E.g. if you
   * would like the function to be run after 2 seconds and thereafter every 100ms you would set
   * delay = Duration(2, TimeUnit.SECONDS) and interval = Duration(100, TimeUnit.MILLISECONDS)
   */
  @Override
  public abstract Cancellable schedule(
      FiniteDuration initialDelay,
      FiniteDuration interval,
      Runnable runnable,
      ExecutionContext executor);

  /**
   * Schedules a function to be run repeatedly with an initial delay and a frequency. E.g. if you
   * would like the function to be run after 2 seconds and thereafter every 100ms you would set
   * delay = Duration(2, TimeUnit.SECONDS) and interval = Duration.ofMillis(100)
   */
  public Cancellable schedule(
      final java.time.Duration initialDelay,
      final java.time.Duration interval,
      final Runnable runnable,
      final ExecutionContext executor) {
    return schedule(
        JavaDurationConverters.asFiniteDuration(initialDelay),
        JavaDurationConverters.asFiniteDuration(interval),
        runnable,
        executor);
  }

  /**
   * Schedules a Runnable to be run once with a delay, i.e. a time period that has to pass before
   * the runnable is executed.
   */
  @Override
  public abstract Cancellable scheduleOnce(
      FiniteDuration delay, Runnable runnable, ExecutionContext executor);

  /**
   * Schedules a Runnable to be run once with a delay, i.e. a time period that has to pass before
   * the runnable is executed.
   */
  public Cancellable scheduleOnce(
      final java.time.Duration delay, final Runnable runnable, final ExecutionContext executor) {
    return scheduleOnce(JavaDurationConverters.asFiniteDuration(delay), runnable, executor);
  }

  /**
   * The maximum supported task frequency of this scheduler, i.e. the inverse of the minimum time
   * interval between executions of a recurring task, in Hz.
   */
  @Override
  public abstract double maxFrequency();
}
```

## Cancellable 接口

调度任务将导致`Cancellable`（或在调度程序关闭后尝试时引发`IllegalStateException`）。这允许你取消已计划执行的某些操作。

- **警告**：如果任务已经启动，则不会中止执行。检查`cancel`的返回值以检测调度任务是已取消还是（最终）将运行。

```java
/**
 * Signifies something that can be cancelled
 * There is no strict guarantee that the implementation is thread-safe,
 * but it should be good practice to make it so.
 */
trait Cancellable {

  /**
   * Cancels this Cancellable and returns true if that was successful.
   * If this cancellable was (concurrently) cancelled already, then this method
   * will return false although isCancelled will return true.
   *
   * Java & Scala API
   */
  def cancel(): Boolean

  /**
   * Returns true if and only if this Cancellable has been successfully cancelled
   *
   * Java & Scala API
   */
  def isCancelled: Boolean
}
```


----------

**英文原文链接**：[Scheduler](https://doc.akka.io/docs/akka/current/scheduler.html).


----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————