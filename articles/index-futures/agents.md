# Agents
## 依赖

为了使用代理（`Agents`），你需要将以下依赖添加到你的项目中：

```xml
<!-- Maven -->
<dependency>
  <groupId>com.typesafe.akka</groupId>
  <artifactId>akka-agent_2.12</artifactId>
  <version>2.5.23</version>
</dependency>

<!-- Gradle -->
dependencies {
  compile group: 'com.typesafe.akka', name: 'akka-agent_2.12', version: '2.5.23'
}

<!-- sbt -->
libraryDependencies += "com.typesafe.akka" %% "akka-agent" % "2.5.23"
```

## 简介

Akka 的代理受到了「Clojure 中的代理」的启发。

- **废弃警告**：代理已被弃用，并计划在下一个主要版本中删除。我们发现，他们的抽象性（他们不在网络上工作）使他们不如纯粹的 Actor，而且面对 Akka Typed 很快被包括在内的情况，我们认为维持现有的代理没有什么价值。

代理提供单个位置的异步更改。代理在其生命周期内绑定到单个存储位置（`storage location`），并且只允许该位置的突变（到一个新的状态）作为操作的结果。更新操作是异步应用于代理状态的函数，其返回值将成为代理的新状态。代理的状态应该是不可变的。

虽然对代理的更新是异步的，但是代理的状态始终可以立即供任何线程（使用`get`）读取，而不需要任何消息。

代理是反应性的。所有代理的更新操作在`ExecutionContext`中的线程之间交错。在任何时间点，每个代理最多执行一个`send`操作。从另一个线程发送到代理的操作将按发送顺序发生，可能与从其他线程发送到同一代理的操作交错出现。

- **注释**：代理是创建它们的节点的本地代理。这意味着你通常不应将它们包括在可能传递给远程 Actor 的消息中，或者作为远程 Actor 的构造函数参数；这些远程 Actor 将无法读取或更新代理。

## 创建代理

通过调用`new Agent<ValueType>(value, executionContext)`传入代理的初始值并提供要用于它的`ExecutionContext`来创建代理，

```java
import scala.concurrent.ExecutionContext;
import akka.agent.Agent;
import akka.dispatch.ExecutionContexts;
ExecutionContext ec = ExecutionContexts.global();
Agent<Integer> agent = Agent.create(5, ec);
```

## 读取代理的值

通过使用`get()`调用代理，可以引用代理（可以获取代理的值），如下所示：

```java
Integer result = agent.get();
```

读取代理的当前值不涉及任何消息传递，并且会立即发生。因此，虽然对代理的更新是异步的，但是读取代理的状态是同步的。

你还可以获得代理值的`Future`，该值将在当前入队的更新完成后完成：

```java
import scala.concurrent.Future;
Future<Integer> future = agent.future();
```

通过「[Futures](https://doc.akka.io/docs/akka/current/futures.html)」可以了解有关`Futures`更多的信息。 

## 更新代理（发送 & 更改）

通过发送转换当前值的函数（`akka.dispatch.Mapper`）或只发送一个新值来更新代理。代理将以原子方式和异步方式应用新值或函数。更新是以`fire-forget`的方式完成的，你只能保证它将被应用。无法保证何时应用更新，但将按顺序从单个线程发送给代理。通过调用`send`函数来应用值或函数。

```java
import akka.dispatch.Mapper;
// send a value, enqueues this change
// of the value of the Agent
agent.send(7);

// send a Mapper, enqueues this change
// to the value of the Agent
agent.send(
    new Mapper<Integer, Integer>() {
      public Integer apply(Integer i) {
        return i * 2;
      }
    });
```

你也可以调度一个函数来更新内部状态，但它是在自己的线程上进行的。这不使用反应式线程池，可以用于长时间运行或阻塞操作。你可以使用`sendOff`方法来实现这一点。使用`sendOff`或`send`的调度仍将按顺序执行。

```java
import akka.dispatch.Mapper;
// sendOff a function
agent.sendOff(longRunningOrBlockingFunction, theExecutionContextToExecuteItIn);
```

所有`send`方法都有一个相应的`alter`方法，它返回一个`Future`。有关`Futures`的更多信息，请参阅「[Futures](https://doc.akka.io/docs/akka/current/futures.html)」。

```java
import scala.concurrent.Future;
import akka.dispatch.Mapper;
// alter a value
Future<Integer> f1 = agent.alter(7);

// alter a function (Mapper)
Future<Integer> f2 =
    agent.alter(
        new Mapper<Integer, Integer>() {
          public Integer apply(Integer i) {
            return i * 2;
          }
        });
// alterOff a function (Mapper)
Future<Integer> f3 =
    agent.alterOff(longRunningOrBlockingFunction, theExecutionContextToExecuteItIn);
```

## 配置

代理模块有几个配置属性，具体请参阅「[配置](https://doc.akka.io/docs/akka/current/general/configuration.html#config-akka-agent)」。

## 不推荐使用事务代理

参与封闭 STM 事务的代理是`2.3`版本中不推荐使用的功能。

如果代理在封闭的 Scala STM 事务中使用，那么它将参与该事务。如果在事务中发送到代理，那么发送到代理的操作将一直保持到事务提交为止，如果事务中止，则放弃。


----------

**英文原文链接**：[Agents](https://doc.akka.io/docs/akka/current/agents.html).



----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————