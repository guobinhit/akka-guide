# 容错
## 依赖

容错（`fault tolerance`）概念与 Actor 相关，为了使用这些概念，需要在项目中添加如下依赖：

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

正如在「[Actor 系统](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/actor-systems.md)」中所解释的，每个 Actor 都是其子级的监督者，因此每个 Actor 都定义了故障处理的监督策略。这一策略不能在 Actor 系统启动之后改变，因为它是 Actor 系统结构的一个组成部分。

## 实践中的故障处理

首先，让我们看一个示例，它演示了处理数据存储错误的一种方法，这是现实应用程序中的典型故障源。当然，这取决于实际的应用程序，当数据存储不可用时可以做什么，但是在这个示例中，我们使用了一种尽最大努力的重新连接方法。

阅读以下源代码。内部的注释解释了故障处理的不同部分以及添加它们的原因。强烈建议运行此示例，因为很容易跟踪日志输出以了解运行时发生的情况。

## 创建监督策略

以下章节将更深入地解释故障处理机制和备选方案。

为了演示，让我们考虑以下策略：

```java
private static SupervisorStrategy strategy =
    new OneForOneStrategy(
        10,
        Duration.ofMinutes(1),
        DeciderBuilder.match(ArithmeticException.class, e -> SupervisorStrategy.resume())
            .match(NullPointerException.class, e -> SupervisorStrategy.restart())
            .match(IllegalArgumentException.class, e -> SupervisorStrategy.stop())
            .matchAny(o -> SupervisorStrategy.escalate())
            .build());

@Override
public SupervisorStrategy supervisorStrategy() {
  return strategy;
}
```

我们选择了一些众所周知的异常类型，以演示在「[监督](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/supervision.md)」中描述的故障处理指令的应用。首先，“一对一策略”意味着每个子级都被单独对待（这和`all-for-one`策略的效果非常相似，唯一的区别是`all-for-one`策略中任何决定都适用于监督者的所有子级，而不仅仅是失败的子级）。在上面的示例中，`10`和`Duration.create(1, TimeUnit.MINUTES)`分别传递给`maxNrOfRetries`和`withinTimeRange`参数，这意味着策略每分钟重新启动一个子级最多`10`次。如果在`withinTimeRange`持续时间内重新启动计数超过`maxNrOfRetries`，则子 Actor 将停止。

此外，这些参数还有一些特殊的值。如果你指定：

- `maxNrOfRetries`为`-1`，`withinTimeRange`为`Duration.Inf()`
  - 总是无限制地重新启动子级
- `maxNrOfRetries`为`-1`，`withinTimeRange`为有限的`Duration`
  - `maxNrOfRetries`被视为`1`
- `maxNrOfRetries`为非负数，`withinTimeRange`为`Duration.Inf()`
  - `withinTimeRange`被视为无限持续（即无论需要多长时间），一旦重新启动计数超过`maxNrOfRetries`，子 Actor 将停止。

构成主体的`match`语句，由`DeciderBuilder`的`match`方法返回的`PFBuilder`组成，其中`builder`由`build`方法完成。这是将子故障类型映射到相应指令的部分。

- **注释**：如果策略在监督者 Actor（而不是单独的类）中声明，则其决策者可以线程安全方式访问 Actor 的所有内部状态，包括获取对当前失败的子级的引用，可用作失败消息的`getSender()`。

### 默认监督策略

如果定义的策略不包括引发的异常，则使用升级。

如果没有为 Actor 定义监督策略，则默认情况下会处理以下异常：

- `ActorInitializationException`将停止失败的子 Actor
- `ActorKilledException`将停止失败的子 Actor
- `DeathPactException`将停止失败的子 Actor
- `Exception`将重新启动失败的子 Actor
- 其他类型的`Throwable`将升级到父级 Actor

如果异常一直升级到根守护者，它将以与上面定义的默认策略相同的方式处理它。

### 停止监督策略

更接近 Erlang 的方法是在子级失败时采取措施阻止他们，然后在`DeathWatch`显示子级死亡时由监督者采取纠正措施。此策略还预打包为`SupervisorStrategy.stoppingStrategy`，并附带一个`StoppingSupervisorStrategy`配置程序，以便在你希望`/user`监护者应用它时使用。

### 记录 Actor 的失败

默认情况下，除非升级，否则`SupervisorStrategy`会记录故障。升级的故障应该在层次结构中更高的级别处理并记录。

通过在实例化时将`loggingEnabled`设置为`false`，可以将`SupervisorStrategy`的默认日志设置为不可用。定制的日志记录可以在`Decider`内完成。请注意，当在监督者 Actor 内部声明`SupervisorStrategy`时，对当前失败的子级的引用可用作`sender`。

你还可以通过重写`logFailure`方法自定义自己的`SupervisorStrategy`中的日志记录。

## 顶级 Actor 的监督者

顶级 Actor 是指使用`system.actorOf()`创建的 Actor，它们是「[用户守护者](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/supervision.md#%E9%A1%B6%E7%BA%A7%E7%9B%91%E7%9D%A3%E8%80%85)」的子代。守护者应用配置的策略，在这种情况下没有应用特殊的规则。

### 测试应用

下面的部分展示了不同指令在实践中的效果，其中需要一个测试设置。首先，我们需要一个合适的监督者：

```java
import akka.japi.pf.DeciderBuilder;
import akka.actor.SupervisorStrategy;

static class Supervisor extends AbstractActor {

  private static SupervisorStrategy strategy =
      new OneForOneStrategy(
          10,
          Duration.ofMinutes(1),
          DeciderBuilder.match(ArithmeticException.class, e -> SupervisorStrategy.resume())
              .match(NullPointerException.class, e -> SupervisorStrategy.restart())
              .match(IllegalArgumentException.class, e -> SupervisorStrategy.stop())
              .matchAny(o -> SupervisorStrategy.escalate())
              .build());

  @Override
  public SupervisorStrategy supervisorStrategy() {
    return strategy;
  }


  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            Props.class,
            props -> {
              getSender().tell(getContext().actorOf(props), getSelf());
            })
        .build();
  }
}
```
这个监督者将被用来创建一个子级，我们可以用它进行实验：

```java
static class Child extends AbstractActor {
  int state = 0;

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            Exception.class,
            exception -> {
              throw exception;
            })
        .match(Integer.class, i -> state = i)
        .matchEquals("get", s -> getSender().tell(state, getSelf()))
        .build();
  }
}
```

通过使用「[TestKit](https://doc.akka.io/docs/akka/current/testing.html)」中描述的实用程序，测试更容易，其中`TestProbe`提供了一个 Actor 引用，可用于接收和检查回复。

```java
import akka.testkit.TestProbe;
import akka.testkit.ErrorFilter;
import akka.testkit.EventFilter;
import akka.testkit.TestEvent;
import static java.util.concurrent.TimeUnit.SECONDS;
import static akka.japi.Util.immutableSeq;
import scala.concurrent.Await;

public class FaultHandlingTest extends AbstractJavaTest {
  static ActorSystem system;
  scala.concurrent.duration.Duration timeout =
      scala.concurrent.duration.Duration.create(5, SECONDS);

  @BeforeClass
  public static void start() {
    system = ActorSystem.create("FaultHandlingTest", config);
  }

  @AfterClass
  public static void cleanup() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  @Test
  public void mustEmploySupervisorStrategy() throws Exception {
    // code here
  }
}
```

让我们创建 Actor：

```java
Props superprops = Props.create(Supervisor.class);
ActorRef supervisor = system.actorOf(superprops, "supervisor");
ActorRef child =
    (ActorRef) Await.result(ask(supervisor, Props.create(Child.class), 5000), timeout);
```

第一个测试将演示`Resume`指令，因此我们通过在 Actor 中设置一些非初始状态进行尝试，并使其失败：

```java
child.tell(42, ActorRef.noSender());
assert Await.result(ask(child, "get", 5000), timeout).equals(42);
child.tell(new ArithmeticException(), ActorRef.noSender());
assert Await.result(ask(child, "get", 5000), timeout).equals(42);
```

如你所见，值`42`保留了故障处理指令。现在，如果我们将失败更改为更严重的`NullPointerException`，情况将不再如此：

```java
child.tell(new NullPointerException(), ActorRef.noSender());
assert Await.result(ask(child, "get", 5000), timeout).equals(0);
```

最后，如果发生致命的`IllegalArgumentException`，监督者将终止该子级：

```java
final TestProbe probe = new TestProbe(system);
probe.watch(child);
child.tell(new IllegalArgumentException(), ActorRef.noSender());
probe.expectMsgClass(Terminated.class);
```

到目前为止，监督者完全不受子级失败的影响，因为指令集（`directives set`）确实处理了它。如果出现`Exception`情况，则情况不再如此，监督者会将失败升级。

```java
child = (ActorRef) Await.result(ask(supervisor, Props.create(Child.class), 5000), timeout);
probe.watch(child);
assert Await.result(ask(child, "get", 5000), timeout).equals(0);
child.tell(new Exception(), ActorRef.noSender());
probe.expectMsgClass(Terminated.class);
```

监管者本身由`ActorSystem`提供的顶级 Actor 进行监督，它具有在所有异常情况下重新启动的默认策略（`ActorInitializationException`和`ActorKilledException`的显著异常）。因为重启时的默认指令是杀死所有的子级，所以我们不希望子级在这次失败中幸存。

如果不需要这样做（这取决于用例），我们需要使用一个不同的监督者来覆盖这个行为。

```java
static class Supervisor2 extends AbstractActor {

  private static SupervisorStrategy strategy =
      new OneForOneStrategy(
          10,
          Duration.ofMinutes(1),
          DeciderBuilder.match(ArithmeticException.class, e -> SupervisorStrategy.resume())
              .match(NullPointerException.class, e -> SupervisorStrategy.restart())
              .match(IllegalArgumentException.class, e -> SupervisorStrategy.stop())
              .matchAny(o -> SupervisorStrategy.escalate())
              .build());

  @Override
  public SupervisorStrategy supervisorStrategy() {
    return strategy;
  }


  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            Props.class,
            props -> {
              getSender().tell(getContext().actorOf(props), getSelf());
            })
        .build();
  }

  @Override
  public void preRestart(Throwable cause, Optional<Object> msg) {
    // do not kill all children, which is the default here
  }
}
```

使用此父级，子级可以在升级的重新启动后存活，如上一个测试所示：

```java
superprops = Props.create(Supervisor2.class);
supervisor = system.actorOf(superprops);
child = (ActorRef) Await.result(ask(supervisor, Props.create(Child.class), 5000), timeout);
child.tell(23, ActorRef.noSender());
assert Await.result(ask(child, "get", 5000), timeout).equals(23);
child.tell(new Exception(), ActorRef.noSender());
assert Await.result(ask(child, "get", 5000), timeout).equals(0);
```



----------

**英文原文链接**：[Fault Tolerance](https://doc.akka.io/docs/akka/current/fault-tolerance.html).



----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————