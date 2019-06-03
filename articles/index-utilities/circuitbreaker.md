# 断路器
## 为什么要使用它们？

在分布式系统中，断路器（`circuit breaker`）用于提供稳定性和防止级联故障（`cascading failures`）。这些应该与远程系统之间的接口的超时一起使用（`judicious timeouts`），以防止单个组件的故障导致所有组件停机。

例如，我们有一个 Web 应用程序与远程第三方 Web 服务交互。假设第三方已经超过了他们的容量，他们的数据库在负载下崩溃了。假设数据库出现故障，将错误返回给第三方 Web 服务需要很长时间。这反过来会使调用在很长一段时间后失败。回到我们的 Web 应用程序，用户已经注意到他们提交的表单看起来挂起要花更长的时间。好吧，用户做他们知道要做的事情，那就是使用刷新按钮，向已经运行的请求添加更多的请求。这最终导致 Web 应用程序因资源耗尽而失败。这将影响所有用户，甚至那些不使用依赖于此第三方 Web 服务的功能的用户。

在 Web 服务调用上引入断路器将导致请求开始快速失败，从而让用户知道有问题，并且不需要刷新请求。这也限制了故障行为仅限于那些使用依赖于第三方的功能的用户，其他用户不再受到影响，因为没有资源耗尽。断路器还允许开发人员将使用功能的部分站点标记为不可用，或者在断路器打开时根据需要显示一些缓存的内容。

Akka 库提供了一个名为`akka.pattern.CircuitBreaker`的断路器的实现，其行为如下所述。

## 它们做什么？

- 正常运行时，断路器处于`Closed`状态：
  - 超出配置的`callTimeout`的异常或调用增加失败计数器
  - 成功将失败计数重置为零
  - 当失败计数器达到`maxFailures`时，断路器跳闸至`Open`状态
- 当断路器处于`Open`状态时：
  - 所有调用都以`CircuitBreakerOpenException`快速失败
  - 配置`resetTimeout`后，断路器进入`Half-Open`状态
- 当断路器处于`Half-Open`状态时：
  - 允许尝试的第一个调用通过，但不会快速失败
  - 所有其他调用都会快速失败，异常情况与`Open`状态相同
  - 如果第一次调用成功，断路器复位回`Closed`状态，`resetTimeout`复位
  - 如果第一次呼叫失败，断路器将再次跳闸至`Open`状态（对于指数后退断路器，`resetTimeout`乘以指数后退系数）
- 状态转换侦听器：
  - 可以通过`onOpen`、`onClose`和`onHalfOpen`为每个状态条目提供回调
  - 它们在提供的`ExecutionContext`中执行
- 调用结果侦听器：
  - 回调可用于收集有关所有调用的统计信息，或对成功、失败或超时等特定调用结果做出反应
  - 支持的回调包括：`onCallSuccess`、`onCallFailure`、`onCallTimeout`和`onCallBreakerOpen`
  - 它们在提供的`ExecutionContext`中执行。

![calls-failing-fast](https://github.com/guobinhit/akka-guide/blob/master/images/index-utilities/circuitbreaker/calls-failing-fast.png)

## 示例
### 初始化

以下是断路器的配置方式：

- 最多 5 次失败
- 调用超时 10 秒
- 重置超时 1 分钟

```java
import akka.actor.AbstractActor;
import akka.event.LoggingAdapter;
import java.time.Duration;
import akka.pattern.CircuitBreaker;
import akka.event.Logging;

import static akka.pattern.Patterns.pipe;

import java.util.concurrent.CompletableFuture;

public class DangerousJavaActor extends AbstractActor {

  private final CircuitBreaker breaker;
  private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  public DangerousJavaActor() {
    this.breaker =
        new CircuitBreaker(
                getContext().getDispatcher(),
                getContext().getSystem().getScheduler(),
                5,
                Duration.ofSeconds(10),
                Duration.ofMinutes(1))
            .addOnOpenListener(this::notifyMeOnOpen);
  }

  public void notifyMeOnOpen() {
    log.warning("My CircuitBreaker is now open, and will not close for one minute");
  }
```

### 基于 Future 和同步的 API

一旦断路器 Actor 被初始化，就可以使用基于`Future`和同步的 API 与该 Actor 进行交互。这两个 API 都被认为是`Call Protection`，因为无论是同步还是异步，断路器的目的都是在调用另一个服务时保护你的系统免受级联故障的影响。在基于`Future`的 API 中，我们使用`withCircuitBreaker`，它采用异步方法（某些方法在`Future`中包装），例如调用从数据库中检索数据，然后将结果传回发送者。如果由于某种原因，本例中的数据库没有响应，或者存在其他问题，断路器将打开并停止尝试一次又一次地攻击数据库，直到超时结束。

同步 API 还将使用断路器逻辑包装你的调用，但是，它使用`withSyncCircuitBreaker`并接收一个`Future`不会包装的方法。

```java
public String dangerousCall() {
  return "This really isn't that dangerous of a call after all";
}

@Override
public Receive createReceive() {
  return receiveBuilder()
      .match(
          String.class,
          "is my middle name"::equals,
          m ->
              pipe(
                      breaker.callWithCircuitBreakerCS(
                          () -> CompletableFuture.supplyAsync(this::dangerousCall)),
                      getContext().getDispatcher())
                  .to(sender()))
      .match(
          String.class,
          "block for me"::equals,
          m -> {
            sender().tell(breaker.callWithSyncCircuitBreaker(this::dangerousCall), self());
          })
      .build();
}
```

### 显式控制失败计数

默认情况下，断路器将`Exception`视为同步 API 中的故障，或将失败的`Future`视为基于`Future`的 API 中的故障。故障将增加失败计数，当失败计数达到`maxFailures`时，断路器打开。但是，有些应用程序可能需要某些异常不增加失败计数，反之亦然，有时我们希望增加失败计数，即使调用成功。Akka 断路器提供了实现这种用例的方法：

- `withCircuitBreaker`
- `withSyncCircuitBreaker`
- `callWithCircuitBreaker`
- `callWithCircuitBreakerCS`
- `callWithSyncCircuitBreaker`

以上所有方法都接受参数`defineFailureFn`。

`defineFailureFn`的类型：`BiFunction[Optional[T], Optional[Throwable], java.lang.Boolean]`

受保护调用的响应使用`Optional[T]`来模拟成功的返回值，并使用`Optional[Throwable]`来模拟异常。如果调用应增加失败计数，则此函数应返回`true`，否则返回`false`。

```java
private final CircuitBreaker breaker;

public EvenNoFailureJavaExample() {
  this.breaker =
      new CircuitBreaker(
          getContext().getDispatcher(),
          getContext().getSystem().getScheduler(),
          5,
          Duration.ofSeconds(10),
          Duration.ofMinutes(1));
}

public int luckyNumber() {
  BiFunction<Optional<Integer>, Optional<Throwable>, Boolean> evenNoAsFailure =
      (result, err) -> (result.isPresent() && result.get() % 2 == 0);

  // this will return 8888 and increase failure count at the same time
  return this.breaker.callWithSyncCircuitBreaker(() -> 8888, evenNoAsFailure);
}
```

### 底层 API

底层 API 允许你详细描述断路器的行为，包括决定在成功或失败时返回给调用 Actor 的内容。这在期望远程调用发送答复时特别有用。目前，`CircuitBreaker`不支持本地的`Tell Protection`（针对预期应答的调用提供保护），因此需要使用底层的超级用户（`power-user`） API，`succeed`和`fail`方法以及`isClose`、`isOpen`和`isHalfOpen`来实现它。

如下面的示例所示，可以通过使用`succeed`和`fail`方法来实现一个`Tell Protection`，这将计入`CircuitBreaker`计数。在本例中，如果`breaker.isClosed`，则对远程服务进行调用，一旦收到响应，则调用`succeed`方法，该方法告诉`CircuitBreaker`保持断路器关闭。另一方面，如果收到错误或超时，将会调用`fail`方法并触发故障，断路器将此故障累积到断路器打开的计数中。

- **注释**：以下示例不会在状态为`HalfOpen`时进行远程调用。使用超级用户 API，你有责任判断何时在`HalfOpen`状态下进行远程调用。

```java
@Override
public Receive createReceive() {
  return receiveBuilder()
      .match(
          String.class,
          payload -> "call".equals(payload) && breaker.isClosed(),
          payload -> target.tell("message", self()))
      .matchEquals("response", payload -> breaker.succeed())
      .match(Throwable.class, t -> breaker.fail())
      .match(ReceiveTimeout.class, t -> breaker.fail())
      .build();
}
```

----------

**英文原文链接**：[Circuit Breaker](https://doc.akka.io/docs/akka/current/common/circuitbreaker.html).



----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————