# 如何：常见模式

本节列出了常用的 Actor 模式，这些模式被认为是有用的、优雅的或有指导意义的。任何内容都是受欢迎的，例如消息路由策略、监督模式、重启处理等。作为一个特殊的奖励，添加到本节的内容都标记了贡献者的名称，如果在他或她的代码中发现循环模式的每个 Akka 用户都可以为了所有人的利益分享它。在适用的情况下，添加到`akka.pattern`包中以创建「[类似于 OTP 库](http://erlang.org/doc/man_index.html)」也是有意义的。

## 调度周期消息

详见「[Actor Timers](https://doc.akka.io/docs/akka/current/actors.html#actors-timers)」。

## 具有高级错误报告的一次性 Actor 树

从 Java 进入 Actor 世界的一个好方法是使用`Patterns.ask()`。这个方法启动一个临时 Actor 来转发消息，并从 Actor 那里收集要“询问”的结果。如果所请求的 Actor 中出现错误，则将接管默认的监督处理。`Patterns.ask()`的调用方将不会收到通知。

如果调用者对这种异常感兴趣，他们必须确保被询问的 Actor 以`Status.Failure(Throwable)`进行答复。在被询问的 Actor 之后，可能会生成一个复杂的 Actor 层次结构来完成异步工作。然后监督是控制错误处理的既定方法。

不幸的是，被问到的 Actor 必须了解监督，并且必须捕获异常。这样的 Actor 不太可能在不同的 Actor 层次结构中重用，并且包含残缺的`try/catch`块。

此模式提供了一种将监督和错误传播封装到临时 Actor 的方法。最后，由`Patterns.ask()`返回的承诺作为一个失败来实现，包括异常，详见「[ Java 8 兼容性](https://github.com/guobinhit/akka-guide/blob/master/articles/index-utilities/java8-compat.md)」。

让我们看一下示例代码：

```java
/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.pattern;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;
import java.time.Duration;

import akka.actor.ActorKilledException;
import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.Cancellable;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.Scheduler;
import akka.actor.Status;
import akka.actor.SupervisorStrategy;
import akka.actor.Terminated;
import akka.actor.AbstractActor;
import akka.pattern.Patterns;

public class SupervisedAsk {

  private static class AskParam {
    Props props;
    Object message;
    Duration timeout;

    AskParam(Props props, Object message, Duration timeout) {
      this.props = props;
      this.message = message;
      this.timeout = timeout;
    }
  }

  private static class AskTimeout {}

  public static class AskSupervisorCreator extends AbstractActor {

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .match(
              AskParam.class,
              message -> {
                ActorRef supervisor = getContext().actorOf(Props.create(AskSupervisor.class));
                supervisor.forward(message, getContext());
              })
          .build();
    }
  }

  public static class AskSupervisor extends AbstractActor {
    private ActorRef targetActor;
    private ActorRef caller;
    private AskParam askParam;
    private Cancellable timeoutMessage;

    @Override
    public SupervisorStrategy supervisorStrategy() {
      return new OneForOneStrategy(
          0,
          Duration.ZERO,
          cause -> {
            caller.tell(new Status.Failure(cause), getSelf());
            return SupervisorStrategy.stop();
          });
    }

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .match(
              AskParam.class,
              message -> {
                askParam = message;
                caller = getSender();
                targetActor = getContext().actorOf(askParam.props);
                getContext().watch(targetActor);
                targetActor.forward(askParam.message, getContext());
                Scheduler scheduler = getContext().getSystem().scheduler();
                timeoutMessage =
                    scheduler.scheduleOnce(
                        askParam.timeout,
                        getSelf(),
                        new AskTimeout(),
                        getContext().getDispatcher(),
                        null);
              })
          .match(
              Terminated.class,
              message -> {
                Throwable ex = new ActorKilledException("Target actor terminated.");
                caller.tell(new Status.Failure(ex), getSelf());
                timeoutMessage.cancel();
                getContext().stop(getSelf());
              })
          .match(
              AskTimeout.class,
              message -> {
                Throwable ex =
                    new TimeoutException(
                        "Target actor timed out after " + askParam.timeout.toString());
                caller.tell(new Status.Failure(ex), getSelf());
                getContext().stop(getSelf());
              })
          .build();
    }
  }

  public static CompletionStage<Object> askOf(
      ActorRef supervisorCreator, Props props, Object message, Duration timeout) {
    AskParam param = new AskParam(props, message, timeout);
    return Patterns.ask(supervisorCreator, param, timeout);
  }

  public static synchronized ActorRef createSupervisorCreator(ActorRefFactory factory) {
    return factory.actorOf(Props.create(AskSupervisorCreator.class));
  }
}
```

在`askOf`方法中，会向`SupervisorCreator`发送用户消息。`SupervisorCreator`创建一个`SupervisorActor`并转发消息。这可以防止由于 Actor 创建而导致 Actor 系统过载。监督者负责创建用户 Actor、转发消息、处理 Actor 终止和监督。此外，如果执行时间过期，则监管者将停止用户 Actor。

如果发生异常，监督者会通知临时 Actor 引发了哪个异常。之后，Actor 层次结构停止。

最后，我们能够执行一个 Actor 并接收结果或异常。

```java
/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.pattern;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.Props;
import akka.actor.AbstractActor;
import akka.util.Timeout;
import scala.concurrent.duration.FiniteDuration;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public class SupervisedAskSpec {

  public Object execute(
      Class<? extends AbstractActor> someActor,
      Object message,
      Duration timeout,
      ActorRefFactory actorSystem)
      throws Exception {
    // example usage
    try {
      ActorRef supervisorCreator = SupervisedAsk.createSupervisorCreator(actorSystem);
      CompletionStage<Object> finished =
          SupervisedAsk.askOf(supervisorCreator, Props.create(someActor), message, timeout);
      return finished.toCompletableFuture().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      // exception propagated by supervision
      throw e;
    }
  }
}
```

## 可扩展的分布式事件源和 CQRS

「[Lagom 框架](https://www.lagomframework.com/)」编码了将 Akka 持久性和 Akka 持久性查询与集群分片相结合的许多最佳实践，以构建具有事件源和 CQRS 的可扩展和弹性系统。


请参见 Lagom 文档中的「[管理数据持久性](https://www.lagomframework.com/documentation/current/java/ES_CQRS.html)」和「[持久性实体](https://www.lagomframework.com/documentation/current/java/PersistentEntity.html)」。



----------

**英文原文链接**：[HowTo: Common Patterns](https://doc.akka.io/docs/akka/current/howto.html).



----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————