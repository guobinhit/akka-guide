# 监督和监控
本章概述了监督（`supervision`）背后的概念、提供的原语及其语义。有关如何转换为真实代码的详细信息，请参阅 Scala 和 Java API 的相应章节。

## 示例项目
你可以查看「[监督示例项目](https://developer.lightbend.com/start/?group=akka&project=akka-samples-supervision-java)」，以了解实际使用的情况。

## 监督意味着什么？
正如「[Actor 系统](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/actor-systems.md)」中监督所描述的，Actor 之间的依赖关系是：`supervisor`将任务委托给子级，因此必须对其失败作出响应。当子级检测到故障（即抛出异常）时，它会挂起自身及其所有下级，并向其监督者发送一条消息，也就是故障信号。根据监督工作的性质和失败的性质，监督者有以下四种选择：

- 恢复子级，保持其累积的内部状态
- 重新启动子级，清除其累积的内部状态
- 永久停止子级
- 使失败升级，从而使自己失败（*译者说，即继续向上一级监督者发送失败消息*）

始终将一个 Actor 视为监管层级的一部分是很重要的，这解释了第四个选择的存在（作为一个监督者也从属于上一级的另一个监督者），并对前三个有影响：恢复一个 Actor 恢复其所有子级，重新启动一个 Actor 需要重新启动其所有子级（如需更多详细信息，请参见下文），同样，终止 Actor 也将终止其所有子级。需要注意的是，`Actor`类的`preRestart`钩子的默认行为是在重新启动之前终止它的所有子级，但是这个钩子可以被重写；递归重新启动应用于执行这个钩子之后剩下的所有子级。

每个监督者都配置了一个函数，将所有可能的故障原因（即异常）转换为上面给出的四个选项之一；值得注意的是，该函数不将故障 Actor 的身份（`identity`）作为输入。这样的结构似乎不够灵活，很容易找到类似的例子，例如希望将不同的策略应用于不同的子级。在这一点上，重要的是要理解监督是关于形成一个递归的故障处理结构。如果你试图在一个层面上做的太多，就很难解释了，因此在这种情况下，建议的方法是增加一个层面（`level`）的监督。

Akka 实现了一种称为“父母监督（`parental supervision`）”的特殊形式。Actor 只能由其他 Actor 创建，其中顶级 Actor 由库提供，每个创建的 Actor 都由其父 Actor 监督。这种限制使得 Actor 监督层次的形成变得含蓄，并鼓励做出合理的设计决策。应当指出的是，这也保证了 Actor 不能成为孤儿，也不能从外部依附于监管者，否则他们可能会不知不觉地被抓起来（`which might otherwise catch them unawares`）。此外，这也为 Actor 应用程序（子树，`sub-trees of`）生成了一个自然、干净的关闭过程。

- **警告**：与监督（`supervision`）相关的父子通信通过特殊的系统消息进行，这些消息的邮箱与用户消息分开。这意味着与监督相关的事件并不是相对于普通消息确定的顺序。一般来说，用户不能影响正常消息和故障通知的顺序。有关详细信息和示例，请参阅「[讨论：消息排序](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/message-delivery-reliability.md#%E8%AE%A8%E8%AE%BA%E6%B6%88%E6%81%AF%E6%8E%92%E5%BA%8F)」部分。

## 顶级监督者
![top-level-supervisors](https://github.com/guobinhit/akka-guide/blob/master/images/supervision/top-level-supervisors.png)

一个 Actor 系统在创建过程中至少会启动三个 Actor，如上图所示。有关 Actor 路径的详细信息，请参阅「[Actor 路径的顶级范围](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/addressing.md#actor-%E8%B7%AF%E5%BE%84%E7%9A%84%E9%A1%B6%E7%BA%A7%E8%8C%83%E5%9B%B4)」。

- `/user`: The Guardian Actor，最可能与之交互的 Actor 是所有用户创建的 Actor 的父级，守护者名为`/user`。使用`system.actorOf()`创建的 Actor 是此 Actor 的子级。这意味着当这个守护者终止时，系统中的所有正常 Actor 也将关闭。这也意味着守护者的监管策略决定了顶级正常 Actor 的监督方式。自 Akka 2.1 开始，可以使用`akka.actor.guardian-supervisor-strategy`来配置它，该设置采用了一个`SupervisorStrategyConfigurator`的完全限定类名。当守护者升级失败时，根守护者的响应将会终止守护者，这实际上将关闭整个 Actor 系统。
- `/system`: The System Guardian，为了实现有序的关闭顺序，引入了这个特殊的守护者，当所有正常的 Actor 都终止，日志记录也保持活动状态，即使日志记录本身也是使用 Actor 实现的。这是通过让系统守护者监视（`watch`）用户守护者并在接收到`Terminated`消息时启动自己的关闭来实现的。顶层系统 Actor 使用一种策略进行监督，该策略将在所有类型的`Exception`（其中，`ActorInitializationException`和`ActorKilledException`除外）上无限期重新启动，这将终止相关的子级。所有其他可抛的异常事件都会升级，这将关闭整个 Actor 系统。
- `/`: The Root Guardian，根守护者是所有所谓的“顶级” Actor 的祖父（`grand-parent`）级，并使用`SupervisorStrategy.stoppingStrategy`监督「[Actor 路径的顶级范围](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/addressing.md#actor-%E8%B7%AF%E5%BE%84%E7%9A%84%E9%A1%B6%E7%BA%A7%E8%8C%83%E5%9B%B4)」中提到的所有特殊 Actor，其目的是在任何类型的`Exception`情况下终止子 Actor。所有其他可抛的异常都会升级……但是给谁？因为每个真正的 Actor 都有一个监督者，所以根守护者的监督者不能是真正的 Actor。因为这意味着它是在“气泡的外面”，所以它被称为“气泡行者（`bubble-walker`）”。这是一个虚构的`ActorRef`，它在出现问题的第一个征兆时停止其子系统，并在根守护程序完全终止（所有子系统递归停止）后将 Actor 系统的`isTerminated`状态设置为`true`。

## 重启意味着什么？
当与处理特定消息时失败的 Actor 一起出现时，失败的原因分为三类：

- 接收到特定的系统性（即编程）错误消息
- 处理消息过程中使用的某些外部资源出现故障
- Actor 的内部状态已损坏

除非能明确识别故障，否则不能排除第三种原因，这就导致了内部状态需要清除的结论。如果监督者决定其其他子级或本身不受损坏的影响，例如，由于有意识地应用了错误内核模式，因此最好重新启动子级。这是通过创建底层`Actor`类的新实例并将失败的实例替换为子`ActorRef`中的新实例来实现的；这样做的能力是将 Actor 封装在特殊引用中的原因之一。然后，新的 Actor 将继续处理其邮箱，这意味着重新启动在 Actor 除本身之外是不可见的，但有一个明显的例外，即发生故障的消息不会被重新处理。

重新启动期间事件的精确顺序如下：

 1. 挂起 Actor（这意味着在恢复之前它不会处理正常消息），并递归挂起所有子级
 2. 调用旧实例的`preRestart`钩子（默认为向所有子实例发送终止请求并调用`postStop`）
 3. 等待在`preRestart`期间被请求终止（使用`context.stop()`）的所有子级实际终止；就像所有 Actor 操作都是非阻塞的一样，最后一个被杀死的子级的终止通知将影响到下一步的进展。
 4. 通过再次调用最初提供的工厂来创建新的 Actor 实例
 5. 在新实例上调用`postRestart`（默认情况下，该实例还调用`preStart`）
 6. 向步骤 3 中未杀死的所有子级发送重新启动请求；从步骤 2 开始，重新启动的子级将递归地执行相同的过程。
 7. 恢复 Actor

## 生命周期监控意味着什么？
- **注释**：Akka 中的生命周期监控通常被称为`DeathWatch`。

与上面描述的父母和子女之间的特殊关系不同，每个 Actor 可以监视（`monitor`）任何其他 Actor。由于 Actor 从完全活跃地创造中出现，并且在受影响的监督者之外无法看到重新启动，因此可用于监控的唯一状态更改是从活跃到死亡的过渡。因此，监控（`Monitoring`）被用来将一个 Actor 与另一个 Actor 联系起来，这样它就可以对另一个 Actor 的终止做出反应，而不是对失败做出反应的监督。

生命周期监控是使用监控 Actor 要接收的`Terminated`消息来实现的，在该消息中，默认行为是如果不进行其他处理，则抛出一个特殊的`DeathPactException`。为了开始监听`Terminated`消息，需要调用`ActorContext.watch(targetActorRef)`。若要停止监听，则需要调用`ActorContext.unwatch(targetActorRef)`。一个重要的属性是，不管监控请求和目标终止的顺序如何，消息都将被传递，即使在注册时目标已经死了，你仍然会收到消息。

如果监督者无法重新启动其子级，并且必须终止它们（例如，在 Actor 初始化期间发生错误时），则监控特别有用。在这种情况下，它应该监控这些子级并重新创建它们，或者计划自己在稍后重试。

另一个常见的用例是，Actor 需要在缺少外部资源的情况下失败，外部资源也可能是其自己的子资源之一。如果第三方通过`system.stop(child)`方法或发送`PoisonPill`终止子级，则监督者可能会受到影响。

### 使用 BackoffSupervisor 模式延迟重新启动
作为内置模式提供的`akka.pattern.BackoffSupervisor`实现了所谓的指数退避监督策略，在失败时再次启动子 Actor，并且每次重新启动之间的时间延迟越来越大。

当启动的 Actor 失败（故障可以用两种不同的方式来表示，通过一个 Actor 停止或崩溃）时，此模式非常有用，因为某些外部资源不可用，我们需要给它一些时间重新启动。一个主要示例是当「[PersistentActor](https://github.com/guobinhit/akka-guide/blob/master/articles/actors/persistence.md)」因持久性失败而失败（通过停止）时，这表明数据库可能已关闭或过载，在这种情况下，在启动持久性 Actor 之前给它一点时间来恢复是很有意义的。

下面的 Scala 片段演示了如何创建一个退避监督者，在给定的 EchoActor 因故障停止后，该监督者将以 3、6、12、24 和最后 30 秒的间隔启动：

```scala
val childProps = Props(classOf[EchoActor])

val supervisor = BackoffSupervisor.props(
  Backoff.onStop(
    childProps,
    childName = "myEcho",
    minBackoff = 3.seconds,
    maxBackoff = 30.seconds,
    randomFactor = 0.2, // adds 20% "noise" to vary the intervals slightly
    maxNrOfRetries = -1
  ))

system.actorOf(supervisor, name = "echoSupervisor")
```
与上述 Scala 代码等价的 Java 代码为：

```java
import java.time.Duration;

final Props childProps = Props.create(EchoActor.class);
final Props supervisorProps =
    BackoffSupervisor.props(
        Backoff.onStop(
            childProps,
            "myEcho",
            Duration.ofSeconds(3),
            Duration.ofSeconds(30),
            0.2)); // adds 20% "noise" to vary the intervals slightly

system.actorOf(supervisorProps, "echoSupervisor");
```
为了避免多个 Actor 在完全相同的时间点重新启动，例如，由于共享资源（如数据库在相同配置的时间间隔后关闭和重新启动），因此强烈建议使用`randomFactor`为回退间隔添加一点额外的变化。通过在重新启动间隔中增加额外的随机性，Actor 将在稍微不同的时间点开始，从而避免大流量峰值冲击恢复共享数据库或他们所需的其他资源。

还可以将`akka.pattern.BackoffSupervisor` Actor 配置为在 Actor 崩溃且监控策略决定应重新启动时，在延迟之后重新启动 Actor。

下面的 Scala 片段演示了如何创建一个退避监督者，在给定的 EchoActor 因某些异常而崩溃后，该监督者将以 3、6、12、24 和最后 30 秒的间隔启动：

```scala
val childProps = Props(classOf[EchoActor])

val supervisor = BackoffSupervisor.props(
  Backoff.onFailure(
    childProps,
    childName = "myEcho",
    minBackoff = 3.seconds,
    maxBackoff = 30.seconds,
    randomFactor = 0.2, // adds 20% "noise" to vary the intervals slightly
    maxNrOfRetries = -1
  ))

system.actorOf(supervisor, name = "echoSupervisor")
```
与上述 Scala 代码等价的 Java 代码为：

```java
import java.time.Duration;

final Props childProps = Props.create(EchoActor.class);
final Props supervisorProps =
    BackoffSupervisor.props(
        Backoff.onFailure(
            childProps,
            "myEcho",
            Duration.ofSeconds(3),
            Duration.ofSeconds(30),
            0.2)); // adds 20% "noise" to vary the intervals slightly

system.actorOf(supervisorProps, "echoSupervisor");
```
`akka.pattern.BackoffOptions`可用于自定义退避监督者 Actor 的行为，以下是一些示例：

```scala
val supervisor = BackoffSupervisor.props(
  Backoff.onStop(
    childProps,
    childName = "myEcho",
    minBackoff = 3.seconds,
    maxBackoff = 30.seconds,
    randomFactor = 0.2, // adds 20% "noise" to vary the intervals slightly
    maxNrOfRetries = -1
  ).withManualReset // the child must send BackoffSupervisor.Reset to its parent
    .withDefaultStoppingStrategy // Stop at any Exception thrown
)
```
上面的代码设置了一个退避监督者，要求子 Actor 在成功处理消息时向其父级发送`akka.pattern.BackoffSupervisor.Reset`消息，从而重置后退。它还使用默认的停止策略，任何异常都会导致子 Actor 停止。

```scala
val supervisor = BackoffSupervisor.props(
  Backoff.onFailure(
    childProps,
    childName = "myEcho",
    minBackoff = 3.seconds,
    maxBackoff = 30.seconds,
    randomFactor = 0.2, // adds 20% "noise" to vary the intervals slightly
    maxNrOfRetries = -1
  ).withAutoReset(10.seconds) // reset if the child does not throw any errors within 10 seconds
    .withSupervisorStrategy(
      OneForOneStrategy() {
        case _: MyException ⇒ SupervisorStrategy.Restart
        case _              ⇒ SupervisorStrategy.Escalate
      }))
```
上面的代码设置了一个退避监督者，如果抛出`MyException`，在退避后重新启动子级，而任何其他异常都将被升级。如果子 Actor 在 10 秒内没有抛出任何错误，则会自动重置后退。

## One-For-One 策略 vs. All-For-One 策略
Akka 提供了两种监管策略：一种是`OneForOneStrategy`，另一种是`AllForOneStrategy`。两者都配置了从异常类型到监督指令（见上文）的映射，并限制了在终止之前允许子级失败的频率。它们之间的区别在于前者只将获得的指令应用于失败的子级，而后者也将其应用于所有的子级。通常，你应该使用`OneForOneStrategy`，如果没有明确指定，它也是默认的。

`AllForOneStrategy`适用于子级群体之间有很强的依赖性，以至于一个子 Actor 的失败会影响其他子 Actor 的功能，即他们之间的联系是不可分割的。由于重新启动无法清除邮箱，因此通常最好在失败时终止子级，并在监督者（通过监视子级的生命周期）中显式地重新创建它们；否则，你必须确保任何 Actor 都可以接受在重新启动之前排队但在重新启动之后处理消息。

在`All-For-One`策略中，通常停止一个子级将不会自动终止其他子级；通过监控他们的生命周期可以完成：如果监督者不处理`Terminated`消息，它将抛出`DeathPactException`（这取决于它的监督者），它将重新启动，默认的`preRestart`将终止其所有的子级。

请注意，创建的一次性 Actor 来自一个`all-for-one`监督者，通过临时 Actor 的失败升级影响所有其他的 Actor。如果不需要这样做，可以安装一个中间监督者；这可以通过为`worker`声明一个大小为 1 的路由器来实现，详见「[路由](https://github.com/guobinhit/akka-guide/blob/master/articles/actors/routing.md)」。

----------

**英文原文链接**：[Supervision and Monitoring](https://doc.akka.io/docs/akka/current/general/supervision.html).

----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————