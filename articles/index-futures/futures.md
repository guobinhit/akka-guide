# Futures
## 依赖

本节将解释如何使用普通的 Scala Futures，但重点介绍它们与 Akka Actor 的互操作性，因此要遵循你希望依赖的示例：

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

在 Scala 标准库中，「[Future](https://en.wikipedia.org/wiki/Futures_and_promises)」是用于检索某些并发操作结果的数据结构。可以同步（阻塞）或异步（非阻塞）访问此结果。

为了能够从 Java 中使用这一点，Akka 在`akka.dispatch.Futures`中提供了一个 Java 友好的接口。

详见「[Java 8 Compatibility](https://doc.akka.io/docs/akka/current/java8-compat.html)」的 Java 兼容性。

## 执行上下文

为了执行回调和操作，`Future`需要一个名为`ExecutionContext`的东西，它非常类似于`java.util.concurrent.Executor`。如果你在作用域中有一个`ActorSystem`，它将使用它的默认调度器作为`ExecutionContext`，或者你可以使用`ExecutionContexts`类提供的工厂方法包装`Executors`和`ExecutorServices`，甚至创建自己的方法。

```java
import akka.dispatch.*;
import jdocs.AbstractJavaTest;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.Await;
import scala.concurrent.Promise;
import akka.util.Timeout;

ExecutionContext ec = ExecutionContexts.fromExecutorService(yourExecutorServiceGoesHere);

// Use ec with your Futures
Future<String> f1 = Futures.successful("foo");

// Then you shut down the ExecutorService at the end of your application.
yourExecutorServiceGoesHere.shutdown();
```

### Actor 内部

每个 Actor 都被配置为在一个`MessageDispatcher`上运行，而该调度器（`dispatcher`）同时也是一个`ExecutionContext`。如果`Future`调用的性质与该 Actor 的活动匹配或兼容（例如，所有 CPU 绑定且无延迟要求），则可以通过导入`getContext().getDispatcher()`来重新使用调度程序来运行`Future`。

```java
import akka.actor.AbstractActor;
import akka.dispatch.Futures;

public class ActorWithFuture extends AbstractActor {
  ActorWithFuture() {
    Futures.future(() -> "hello", getContext().dispatcher());
  }

  @Override
  public Receive createReceive() {
    return AbstractActor.emptyBehavior();
  }
}
```

## 与 Actor 一起使用

一般来说，从`AbstractActor`那里得到回复有两种方式：第一种是通过已发送的消息（`actorRef.tell(msg, sender)`），只有原始发送者是`AbstractActor`时才有效，第二种是通过`Future`。

使用`ActorRef`的`ask`方法发送消息将返回一个`Future`。要等待并检索实际结果，最简单的方法是：

```java
import akka.dispatch.*;
import jdocs.AbstractJavaTest;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.Await;
import scala.concurrent.Promise;
import akka.util.Timeout;

Timeout timeout = Timeout.create(Duration.ofSeconds(5));
Future<Object> future = Patterns.ask(actor, msg, timeout);
String result = (String) Await.result(future, timeout.duration());
```






----------

**英文原文链接**：[Coordination](https://doc.akka.io/docs/akka/current/coordination.html).



----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————