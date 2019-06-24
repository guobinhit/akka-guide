# Actors
## 依赖

为了使用 Actors，你必须在项目中添加如下依赖：

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

「[Actor 模型](https://en.wikipedia.org/wiki/Actor_model)」为编写并发和分布式系统提供了更高级别的抽象。它减少了开发人员必须处理显式锁和线程管理的问题，使编写正确的并发和并行系统变得更容易。1973 年卡尔·休伊特（`Carl Hewitt`）在论文中定义了 Actors，然后通过  Erlang 语言所普及，并且在爱立信（`Ericsson`）成功地建立了高度并发和可靠的电信系统。

Akka 的 Actors API 类似于 Scala Actors，它从 Erlang 中借用了一些语法。

## 创建 Actors

由于 Akka 实施父级监督，每个 Actor 都受到其父级的监督并且监督其子级，因此建议你熟悉「[Actor 系统](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/actor-systems.md)」和「[监督](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/supervision.md)」，它还可能有助于阅读「[Actor 引用、路径和地址](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/addressing.md)」。


### 定义 Actor 类

Actor 类是通过继承`AbstractActor`类并在`createReceive`方法中设置“初始行为”来实现的。

`createReceive`方法没有参数，并返回`AbstractActor.Receive`。它定义了 Actor 可以处理哪些消息，以及如何处理消息的实现。可以使用名为`ReceiveBuilder`的生成器来构建此类行为。在`AbstractActor`中，有一个名为`receiveBuilder`的方便的工厂方法。

下面是一个例子：

```java
import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class MyActor extends AbstractActor {
  private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            String.class,
            s -> {
              log.info("Received String message: {}", s);
            })
        .matchAny(o -> log.info("received unknown message"))
        .build();
  }
}
```

请注意，Akka Actor 消息循环是彻底的（`exhaustive`），与 Erlang 和后 Scala Actors 相比，它是不同的。这意味着你需要为它可以接受的所有消息提供一个模式匹配，如果你希望能够处理未知消息，那么你需要有一个默认情况，如上例所示。否则，`akka.actor.UnhandledMessage(message, sender, recipient)`将发布到`ActorSystem`的`EventStream`。

请进一步注意，上面定义的行为的返回类型是`Unit`；如果 Actor 应回复收到的消息，则必须按照下面的说明显式完成。

`createReceive`方法的结果是`AbstractActor.Receive`，它是围绕部分 Scala 函数对象的包装。它作为其“初始行为”存储在 Actor 中，有关在 Actor 构造后更改其行为的详细信息，请参见「[Become/Unbecome](https://github.com/guobinhit/akka-guide/blob/master/articles/actors/actors.md#becomeunbecome)」。

### Props

`Props`是一个配置类，用于指定创建 Actor 的选项，将其视为不可变的，因此可以自由共享用于创建 Actor 的方法，包括关联的部署信息（例如，要使用哪个调度程序，请参阅下面的更多内容）。下面是一些如何创建`Props`实例的示例。

```java
import akka.actor.Props;
Props props1 = Props.create(MyActor.class);
Props props2 =
    Props.create(ActorWithArgs.class, () -> new ActorWithArgs("arg")); // careful, see below
Props props3 = Props.create(ActorWithArgs.class, "arg");
```

第二个变量演示了如何将构造函数参数传递给正在创建的 Actor，但它只能在 Actor 之外使用，如下所述。

最后一行显示了传递构造函数参数的可能性，而不管它在哪个上下文中使用。在`Props`对象的构造过程中，会验证是否存在匹配的构造函数，如果未找到匹配的构造函数或找到多个匹配的构造函数，则会导致`IllegalArgumentException`。

### 危险的变体

```java
// NOT RECOMMENDED within another actor:
// encourages to close over enclosing class
Props props7 = Props.create(ActorWithArgs.class, () -> new ActorWithArgs("arg"));
```

不建议在另一个 Actor 中使用此方法，因为它鼓励关闭封闭范围，从而导致不可序列化的属性和可能的竞态条件（破坏 Actor 封装）。另一方面，在 Actor 的同伴对象（`companion object`）中的`Props`工厂中使用这个变体是完全正确的，如下面的“推荐实践”中所述。

这些方法有两个用例：将构造函数参数传递给由新引入的`Props.create(clazz, args)`方法或下面的推荐实践解决的 Actor，并将 Actor “就地”创建为匿名类。后者应该通过将这些 Actor 命名为类来解决，如果它们没有在顶级`object`中声明，则需要将封闭实例的`this`引用作为第一个参数传递。

- **警告**：在另一个 Actor 中声明一个 Actor 是非常危险的，并且会破坏 Actor 的封装。千万不要把 Actor 的`this`引用传给`Props`！

### 推荐实践

为每个 Actor 提供静态工厂方法是一个好主意，这有助于使合适的`Props`创建尽可能接近 Actor 的定义。这也避免了与使用`Props.create(...)`方法相关联的陷阱，该方法将参数作为构造函数参数，因为在静态方法中，给定的代码块不会保留对其封闭范围的引用：

```java
static class DemoActor extends AbstractActor {
  /**
   * Create Props for an actor of this type.
   *
   * @param magicNumber The magic number to be passed to this actor’s constructor.
   * @return a Props for creating this actor, which can then be further configured (e.g. calling
   *     `.withDispatcher()` on it)
   */
  static Props props(Integer magicNumber) {
    // You need to specify the actual type of the returned actor
    // since Java 8 lambdas have some runtime type information erased
    return Props.create(DemoActor.class, () -> new DemoActor(magicNumber));
  }

  private final Integer magicNumber;

  public DemoActor(Integer magicNumber) {
    this.magicNumber = magicNumber;
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            Integer.class,
            i -> {
              getSender().tell(i + magicNumber, getSelf());
            })
        .build();
  }
}

static class SomeOtherActor extends AbstractActor {
  // Props(new DemoActor(42)) would not be safe
  ActorRef demoActor = getContext().actorOf(DemoActor.props(42), "demo");
  // ...
}
```

另一个好的做法是声明 Actor 可以接收的消息尽可能接近 Actor 的定义（例如，作为 Actor 内部的静态类或使用其他合适的类），这使得更容易知道它可以接收到什么：

```java
static class DemoMessagesActor extends AbstractLoggingActor {

  public static class Greeting {
    private final String from;

    public Greeting(String from) {
      this.from = from;
    }

    public String getGreeter() {
      return from;
    }
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            Greeting.class,
            g -> {
              log().info("I was greeted by {}", g.getGreeter());
            })
        .build();
  }
}
```

### 使用 Props 创建 Actors

Actors 是通过将`Props`实例传递到`actorOf`工厂方法来创建的，该方法在`ActorSystem`和`ActorContext`上可用。

```java
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
```

使用`ActorSystem`将创建顶级 Actor，由`ActorSystem`提供的守护者 Actor 进行监督，而使用`ActorContext`将创建子 Actor。

```java
static class FirstActor extends AbstractActor {
  final ActorRef child = getContext().actorOf(Props.create(MyActor.class), "myChild");

  @Override
  public Receive createReceive() {
    return receiveBuilder().matchAny(x -> getSender().tell(x, getSelf())).build();
  }
}
```

建议创建一个子级、子子级这样的层次结构，以便它适合应用程序的逻辑故障处理结构，详见「[Actor 系统](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/actor-systems.md)」。

对`actorOf`的调用返回`ActorRef`的实例。这是 Actor 实例的句柄，也是与之交互的唯一方法。`ActorRef`是不可变的，并且与它所表示的 Actor 有一对一的关系。`ActorRef`也是可序列化的，并且具有网络意识（`network-aware`）。这意味着你可以序列化它，通过网络发送它，并在远程主机上使用它，并且它仍然在网络上表示原始节点上的同一个 Actor。

`name`参数是可选的，但你最好为 Actor 命名，因为它用于日志消息和标识 Actor。名称不能为空或以`$`开头，但可以包含 URL 编码字符（例如，空格为`%20`）。如果给定的名称已被同一父级的另一个子级使用，则会引发`InvalidActorNameException`。

Actor 在创建时自动异步启动。

### 依赖注入

如果你的 Actor 有一个接受参数的构造函数，那么这些参数也需要成为`Props`的一部分，如上所述。但在某些情况下，必须使用工厂方法，例如，当依赖注入框架确定实际的构造函数参数时。

```java
import akka.actor.Actor;
import akka.actor.IndirectActorProducer;
class DependencyInjector implements IndirectActorProducer {
  final Object applicationContext;
  final String beanName;

  public DependencyInjector(Object applicationContext, String beanName) {
    this.applicationContext = applicationContext;
    this.beanName = beanName;
  }

  @Override
  public Class<? extends Actor> actorClass() {
    return TheActor.class;
  }

  @Override
  public TheActor produce() {
    TheActor result;
    result = new TheActor((String) applicationContext);
    return result;
  }
}

  final ActorRef myActor =
      getContext()
          .actorOf(
              Props.create(DependencyInjector.class, applicationContext, "TheActor"), "TheActor");
```

- **警告**：有时，你可能会试图提供始终返回同一实例的`IndirectActorProducer`，例如使用静态字段。这是不支持的，因为它违背了 Actor 重新启动的含义，在这里「[重启意味着什么？](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/supervision.md#%E9%87%8D%E5%90%AF%E6%84%8F%E5%91%B3%E7%9D%80%E4%BB%80%E4%B9%88)」进行了描述。

当使用依赖注入框架时，Actor `bean`不能有`singleton`作用域。

在「[Using Akka with Dependency Injection](http://letitcrash.com/post/55958814293/akka-dependency-injection)」指南和「[Akka Java Spring](https://github.com/typesafehub/activator-akka-java-spring)」教程中，有关于依赖注入的更深层次的描述。

### 收件箱

当在与 Actor 通信的 Actor 外部编写代码时，`ask`模式可以是一个解决方案（见下文），但它不能做两件事：接收多个回复（例如，通过向通知服务订阅`ActorRef`）和观察其他 Actor 的生命周期。出于这些目的，有一个`Inbox`类：

```java
final Inbox inbox = Inbox.create(system);
inbox.send(target, "hello");
try {
  assert inbox.receive(Duration.ofSeconds(1)).equals("world");
} catch (java.util.concurrent.TimeoutException e) {
  // timeout
}
```

`send`方法包装了一个普通的`tell`，并将内部 Actor 的引用作为发送者提供。这允许在最后一行接收回复。监视 Actor 也很简单：

```java
final Inbox inbox = Inbox.create(system);
inbox.watch(target);
target.tell(PoisonPill.getInstance(), ActorRef.noSender());
try {
  assert inbox.receive(Duration.ofSeconds(1)) instanceof Terminated;
} catch (java.util.concurrent.TimeoutException e) {
  // timeout
}
```

## Actor API

`AbstractActor`类定义了一个名为`createReceive`的方法，该方法用于设置 Actor 的“初始行为”。

如果当前的 Actor 行为与接收到的消息不匹配，则调用`unhandled`，默认情况下，它在Actor 系统的事件流上发布`akka.actor.UnhandledMessage(message, sender, recipient)`（将配置项`akka.actor.debug.unhandled`设置为`on`，以便将其转换为实际的`Debug`消息）。

此外，它还提供：

- `getSelf()`，对 Actor 的`ActorRef`的引用
- `getSender()`，前一次接收到的消息的发送方 Actor 的引用
- `supervisorStrategy()`，用户可重写定义用于监视子 Actor 的策略

该策略通常在 Actor 内部声明，以便访问决策函数中 Actor 的内部状态：由于故障作为消息发送给其监督者并像其他消息一样进行处理（尽管不属于正常行为），因此 Actor 内的所有值和变量都可用，就像`sender`引用一样（报告失败的是直接子级；如果原始失败发生在一个遥远的后代中，则每次仍向上一级报告）。

- `getContext()`公开 Actor 和当前消息的上下文信息，例如：
  - 创建子 Actor 的工厂方法（`actorOf`）
  - Actor 所属的系统
  - 父级监督者
  - 受监督的子级
  - 生命周期监控
  - 如`Become/Unbecome`中所述的热交换行为栈
 
其余可见的方法是用户可重写的生命周期钩子方法，如下所述：

```java
public void preStart() {}

public void preRestart(Throwable reason, Optional<Object> message) {
  for (ActorRef each : getContext().getChildren()) {
    getContext().unwatch(each);
    getContext().stop(each);
  }
  postStop();
}

public void postRestart(Throwable reason) {
  preStart();
}

public void postStop() {}
```

上面显示的实现是`AbstractActor`类提供的默认值。

### Actor 生命周期

![actor-path](https://github.com/guobinhit/akka-guide/blob/master/images/actors/actors/actor-path.png)

Actor 系统中的一条路径表示一个“地方”，可能被一个活着的 Actor 占据。最初（除了系统初始化的 Actor 之外）路径是空的，当调用`actorOf()`时，它将通过传递的`Props`描述的 Actor 的化身（`incarnation`）分配给给定的路径，Actor 的化身由路径（`path`）和`UID`标识。

值得注意的是：

- `restart`
- `stop`，然后重新创建 Actor

如下所述。

重新启动只交换由`Props`定义的`Actor`实例，因此`UID`保持不变。只要化身是相同的，你可以继续使用相同的`ActorRef`。重启是通过 Actor 的父 Actor 的「[监督策略](https://github.com/guobinhit/akka-guide/blob/master/articles/actors/fault-tolerance.md#%E5%88%9B%E5%BB%BA%E7%9B%91%E7%9D%A3%E7%AD%96%E7%95%A5)」来处理的，关于重启的含义还有「[更多的讨论](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/supervision.md#%E9%87%8D%E5%90%AF%E6%84%8F%E5%91%B3%E7%9D%80%E4%BB%80%E4%B9%88)」。

当 Actor 停止时，化身的生命周期就结束了。此时将调用适当的生命周期事件，并将终止通知观察 Actor。当化身停止后，可以通过使用`actorOf()`创建 Actor 来再次使用路径。在这种情况下，新化身的名称将与前一个相同，但`UID`将不同。Actor 可以由 Actor 本身、另一个 Actor 或 Actor 系统停止，详见「[停止 Actor](https://github.com/guobinhit/akka-guide/blob/master/articles/actors/actors.md#%E5%81%9C%E6%AD%A2-actor)」。

- **注释**：重要的是要注意，Actor 不再被引用时不会自动停止，创建的每个 Actor 也必须显式销毁。唯一的简化是，停止父 Actor 也将递归地停止此父 Actor 创建的所有子 Actor。

`ActorRef`总是代表一个化身（路径和`UID`），而不仅仅是一个给定的路径。因此，如果一个 Actor 被停止，一个同名的新 Actor 被创造出来，旧化身的 Actor 引用就不会指向新的 Actor。

另一方面，`ActorSelection`指向路径（或者如果使用通配符，则指向多个路径），并且完全忽略了具体化当前正在占用的路径。由于这个原因，`ActorSelection`不能被监控。可以通过向`ActorSelection`发送`Identify`消息来获取当前化身的`ActorRef`，该消息将以包含正确引用的`ActorIdentity`回复，详见「[ActorSelection](https://github.com/guobinhit/akka-guide/blob/master/articles/actors/actors.md#%E9%80%9A%E8%BF%87-actor-selection-%E8%AF%86%E5%88%AB-actor)」。这也可以通过`ActorSelection`的`resolveOne`方法来实现，该方法返回匹配`ActorRef`的`Future`。

### 生命周期监控，或称为 DeathWatch

为了在另一个 Actor 终止时得到通知（即永久停止，而不是临时失败和重新启动），Actor 可以注册自己，以便在终止时接收另一个 Actor 发送的`Terminated`消息，详见「[停止 Actor](https://github.com/guobinhit/akka-guide/blob/master/articles/actors/actors.md#%E5%81%9C%E6%AD%A2-actor)」。此服务由 Actor 系统的`DeathWatch`组件提供。

注册监视器（`monitor`）很容易：

```java
import akka.actor.Terminated;
static class WatchActor extends AbstractActor {
  private final ActorRef child = getContext().actorOf(Props.empty(), "target");
  private ActorRef lastSender = system.deadLetters();

  public WatchActor() {
    getContext().watch(child); // <-- this is the only call needed for registration
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .matchEquals(
            "kill",
            s -> {
              getContext().stop(child);
              lastSender = getSender();
            })
        .match(
            Terminated.class,
            t -> t.actor().equals(child),
            t -> {
              lastSender.tell("finished", getSelf());
            })
        .build();
  }
}
```

在这里，有一点需要我们注意：`Terminated`消息的生成与注册和终止发生的顺序无关。特别是，即使被监视的 Actor 在注册时已经被终止，监视的 Actor 也将收到一条`Terminated`消息。

多次注册并不一定会导致生成多条消息，但不能保证只接收到一条这样的消息：如果被监视的 Actor 的终止消息已经生成并将消息排队，并且在处理此消息之前完成了另一个注册，则第二条消息也将进入消息队列。因为注册监视已经终止的 Actor 会导致立即生成`Terminated`消息。

也可以通过`context.unwatch(target)`取消监视另一个 Actor 的存活情况。即使`Terminated`消息已经在邮箱中排队，也可以这样做；在调用`unwatch`之后，将不再处理该 Actor 的`Terminated`消息。

### Start 钩子

启动 Actor 之后，立即调用其`preStart`方法。

```java
@Override
public void preStart() {
  target = getContext().actorOf(Props.create(MyActor.class, "target"));
}
```

首次创建 Actor 时调用此方法。在重新启动期间，它由`postRestart`的默认实现调用，这意味着通过重写该方法，你可以选择是否只为此 Actor 或每次重新启动时调用一次此方法中的初始化代码。当创建 Actor 类的实例时，总是会调用作为 Actor 构造函数一部分的初始化代码，该实例在每次重新启动时都会发生。

### Restart 钩子

所有 Actor 都受到监督，即通过故障处理策略链接到另一个 Actor。如果在处理消息时引发异常，则可以重新启动 Actor，详见「[监督](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/supervision.md)」。重新启动涉及上述挂钩：

1. 通过调用导致`preRestart`的异常和触发该异常的消息来通知旧 Actor ；如果重新启动不是由处理消息引起的，则后者可能为`None`，例如，当监督者不捕获异常并由其监督者依次重新启动时，或者某个 Actor 由于其同级 Actor 的失败而导致重新启动时。如果消息可用，那么该消息的发送者也可以通过常规方式访问，即调用`sender`。此方法是清理、准备移交给新的 Actor 实例等的最佳位置。默认情况下，它会停止所有子级并调用`postStop`。
2. 来自`actorOf`调用的初始工厂用于生成新实例。
3. 调用新 Actor 的`postRestart`方法时出现导致重新启动的异常。默认情况下，会调用`preStart `，就像在正常启动情况下一样。

Actor 重新启动仅替换实际的 Actor 对象；邮箱的内容不受重新启动的影响，因此在`postRestart`钩子返回后，将继续处理消息，而且将不再接收触发异常的消息。重新启动时发送给 Actor 的任何消息都将像往常一样排队进入其邮箱。

- **警告**：请注意，与用户消息相关的失败通知的顺序是不确定的。特别是，父级可以在处理子级在失败之前发送的最后一条消息之前重新启动其子级。有关详细信息，请参阅「[讨论：消息排序](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/message-delivery-reliability.md#%E8%AE%A8%E8%AE%BA%E6%B6%88%E6%81%AF%E6%8E%92%E5%BA%8F)」。

### Stop 钩子

停止某个 Actor 后，将调用其`postStop`钩子，该钩子可用于将该 Actor 从其他服务中注销。此钩子保证在禁用此 Actor 的消息队列后运行，即发送到已停止 Actor 的消息将被重定向到`ActorSystem`的`deadLetters`。

## 通过 Actor Selection 识别 Actor

如「[Actor 引用、路径和地址](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/addressing.md)」中所述，每个 Actor 都有唯一的逻辑路径，该路径通过从子级到父级的 Actor 链获得，直到到达 Actor 系统的根，并且它有一个物理路径，如果监督链包含任何远程监督者，则该路径可能有所不同。系统使用这些路径来查找 Actor，例如，当接收到远程消息并搜索收件人时，它们很有用：Actor 可以通过指定逻辑或物理的绝对或相对路径来查找其他 Actor，并接收带有结果的`ActorSelection`：

```java
// will look up this absolute path
getContext().actorSelection("/user/serviceA/actor");
// will look up sibling beneath same supervisor
getContext().actorSelection("../joe");
```

- **注释**：与其他 Actor 交流时，最好使用他们的`ActorRef`，而不是依靠`ActorSelection`。但也有例外，如
  - 使用「[至少一次传递](https://github.com/guobinhit/akka-guide/blob/master/articles/actors/persistence.md#%E8%87%B3%E5%B0%91%E4%B8%80%E6%AC%A1%E4%BC%A0%E9%80%92)」能力发送消息
  - 启动与远程系统的第一次连接

在所有其他情况下，可以在 Actor 创建或初始化期间提供`ActorRef`，将其从父级传递到子级，或者通过将其`ActorRef`发送到其他 Actor 来引出 Actor。

提供的路径被解析为`java.net.URI`，这意味着它在路径元素上被`/`拆分。如果路径以`/`开头，则为绝对路径，查找从根守护者（它是`/user`的父级）开始；否则，查找从当前 Actor 开始。如果路径元素等于`..`，则查找将向当前遍历的 Actor 的监督者“向上”一步，否则将向命名的子级“向下”一步。应该注意的是`..`在 Actor 路径中，总是指逻辑结构，即监督者。

Actor 选择（`selection`）的路径元素可以包含允许向该部分广播消息的通配符模式：

```java
// will look all children to serviceB with names starting with worker
getContext().actorSelection("/user/serviceB/worker*");
// will look up all siblings beneath same supervisor
getContext().actorSelection("../*");
```

消息可以通过`ActorSelection`发送，并且在传递每个消息时查找`ActorSelection`的路径。如果选择与任何 Actor 都不匹配，则消息将被删除。

要获取`ActorRef`以进行`ActorSelection`，你需要向选择发送消息，并使用来自 Actor 的答复的`getSender()`引用。有一个内置的`Identify`消息，所有 Actor 都将理解该消息，并使用包含`ActorRef`的`ActorIdentity`消息自动回复。此消息由 Actor 专门处理，如果具体的名称查找失败（即非通配符路径元素与存活的 Actor 不对应），则会生成负结果。请注意，这并不意味着保证回复的传递，它仍然是正常的消息。

```java
import akka.actor.ActorIdentity;
import akka.actor.ActorSelection;
import akka.actor.Identify;
static class Follower extends AbstractActor {
  final Integer identifyId = 1;

  public Follower() {
    ActorSelection selection = getContext().actorSelection("/user/another");
    selection.tell(new Identify(identifyId), getSelf());
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            ActorIdentity.class,
            id -> id.getActorRef().isPresent(),
            id -> {
              ActorRef ref = id.getActorRef().get();
              getContext().watch(ref);
              getContext().become(active(ref));
            })
        .match(
            ActorIdentity.class,
            id -> !id.getActorRef().isPresent(),
            id -> {
              getContext().stop(getSelf());
            })
        .build();
  }

  final AbstractActor.Receive active(final ActorRef another) {
    return receiveBuilder()
        .match(
            Terminated.class, t -> t.actor().equals(another), t -> getContext().stop(getSelf()))
        .build();
  }
}
```

你还可以使用`ActorSelection`的`resolveOne`方法获取`ActorRef`以进行`ActorSelection`。如果存在这样的 Actor，它将返回匹配的`ActorRef`的`Future`，可参阅「[ Java 8 兼容性](https://github.com/guobinhit/akka-guide/blob/master/articles/index-utilities/java8-compat.md)」。如果不存在这样的 Actor 或标识在提供的`timeout`内未完成，则完成此操作并抛出`akka.actor.ActorNotFound`异常。

如果启用「[远程处理](https://doc.akka.io/docs/akka/current/remoting.html)」，也可以查找远程 Actor 的地址：

```java
getContext().actorSelection("akka.tcp://app@otherhost:1234/user/serviceB");
```

「[远程处理样例](https://doc.akka.io/docs/akka/current/remoting.html#remote-sample)」中给出了一个在启用远程处理（`remoting`）的情况下演示 Actor 查找的例子。

## 信息和不变性

- **重要的**：消息可以是任何类型的对象，但必须是不可变的。Akka 还不能强制执行不可变性，所以必须按惯例执行。

以下是不可变消息的示例：

```java
public class ImmutableMessage {
  private final int sequenceNumber;
  private final List<String> values;

  public ImmutableMessage(int sequenceNumber, List<String> values) {
    this.sequenceNumber = sequenceNumber;
    this.values = Collections.unmodifiableList(new ArrayList<String>(values));
  }

  public int getSequenceNumber() {
    return sequenceNumber;
  }

  public List<String> getValues() {
    return values;
  }
}
```

## 发送消息

消息通过以下方法之一发送给 Actor。

- `tell`的意思是“发送并忘记（`fire-and-forget`）”，例如异步发送消息并立即返回。
- `ask`异步发送消息，并返回一个表示可能的答复。

每一个发送者都有消息顺序的保证。

- **注释**：使用`ask`会带来性能方面的影响，因为有些东西需要跟踪它何时超时，需要有一些东西将一个`Promise`连接到`ActorRef`中，并且还需要通过远程处理实现它。所以，我们更倾向于使用`tell`，只有当你有足够的理由时才应该使用`ask`。

在所有这些方法中，你可以选择传递自己的`ActorRef`。将其作为一种实践，因为这样做将允许接收者 Actor 能够响应你的消息，因为发送者引用与消息一起发送。

### Tell: Fire-forget

这是发送消息的首选方式，它不用等待消息返回，因此不是阻塞的。这提供了最佳的并发性和可伸缩性的特性。

```java
// don’t forget to think about who is the sender (2nd argument)
target.tell(message, getSelf());
```

发送方引用与消息一起传递，并在处理此消息时通过`getSender()`方法在接收 Actor 中使用。在一个 Actor 内部，通常是`getSelf()`作为发送者，但在某些情况下，回复（`replies`）应该路由到另一个 Actor，例如，父对象，其中`tell`的第二个参数将是另一个 Actor。在 Actor 外部，如果不需要回复，则第二个参数可以为`null`；如果在 Actor 外部需要回复，则可以使用下面描述的`ask`模式。

### Ask: Send-And-Receive-Future

`ask`模式涉及 Actor 和`Future`，因此它是作为一种使用模式而不是`ActorRef`上的一种方法提供的：

```java
import static akka.pattern.Patterns.ask;
import static akka.pattern.Patterns.pipe;

import java.util.concurrent.CompletableFuture;
final Duration t = Duration.ofSeconds(5);

// using 1000ms timeout
CompletableFuture<Object> future1 =
    ask(actorA, "request", Duration.ofMillis(1000)).toCompletableFuture();

// using timeout from above
CompletableFuture<Object> future2 = ask(actorB, "another request", t).toCompletableFuture();

CompletableFuture<Result> transformed =
    CompletableFuture.allOf(future1, future2)
        .thenApply(
            v -> {
              String x = (String) future1.join();
              String s = (String) future2.join();
              return new Result(x, s);
            });

pipe(transformed, system.dispatcher()).to(actorC);
```

这个例子演示了`ask`和`pipeTo`模式在`Future`上的结合，因为这可能是一个常见的组合。请注意，以上所有内容都是完全非阻塞和异步的：`ask`生成一个，其中两个使用`CompletableFuture.allOf`和`thenApply`方法组合成新的`Future`，然后`pipe`在`CompletionStage`上安装一个处理程序，以将聚合的`Result`提交给另一个 Actor。

使用`ask`会像使用`tell`一样向接收 Actor 发送消息，并且接收 Actor 必须使用`getSender().tell(reply, getSelf())`才能完成返回的值。`ask`操作涉及创建一个用于处理此回复的内部 Actor，该 Actor 需要有一个超时，在该超时之后才能将其销毁，以便不泄漏资源；具体请参阅下面更多的内容。

- **警告**：要完成带异常的，你需要向发件人发送`akka.actor.Status.Failure`消息。当 Actor 在处理消息时抛出异常，不会自动执行此操作。

```java
try {
  String result = operation();
  getSender().tell(result, getSelf());
} catch (Exception e) {
  getSender().tell(new akka.actor.Status.Failure(e), getSelf());
  throw e;
}
```

如果 Actor 未完成，则它将在超时期限（指定为`ask`方法的参数）之后过期；这将使用`AskTimeoutException`完成`CompletionStage`。

这可以用于注册回调以便在完成时获取通知，从而提供避免阻塞的方法。

- **警告**：当使用`Future`的回调时，内部 Actor 需要小心避免关闭包含 Actor 的引用，即不要从回调中调用方法或访问封闭 Actor 的可变状态。这将破坏 Actor 的封装，并可能引入同步错误和竞态条件，因为回调将被同时调度到封闭 Actor。不幸的是，目前还没有一种方法可以在编译时检测到这些非法访问。另见「[Actors 和共享可变状态](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/jmm.md#actors-%E5%92%8C%E5%85%B1%E4%BA%AB%E5%8F%AF%E5%8F%98%E7%8A%B6%E6%80%81)」。

### 转发消息

你可以将消息从一个 Actor 转发到另一个 Actor。这意味着即使消息通过“中介器”，原始发送方地址/引用也会得到维护。这在编写充当路由器、负载平衡器、复制器等的 Actor 时很有用。

```java
target.forward(result, getContext());
```

## 接收消息

Actor 必须通过在`AbstractActor`中实现`createReceive`方法来定义其初始接收行为：

```java
@Override
public Receive createReceive() {
  return receiveBuilder().match(String.class, s -> System.out.println(s.toLowerCase())).build();
}
```

返回类型是`AbstractActor.Receive`，它定义了 Actor 可以处理哪些消息，以及如何处理这些消息的实现。可以使用名为`ReceiveBuilder`的生成器来构建此类行为。下面是一个例子：

```java
import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class MyActor extends AbstractActor {
  private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            String.class,
            s -> {
              log.info("Received String message: {}", s);
            })
        .matchAny(o -> log.info("received unknown message"))
        .build();
  }
}
```

如果你希望提供许多`match`案例，但希望避免创建长调用跟踪，可以将生成器的创建拆分为多个语句，如示例中所示：

```java
import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

public class GraduallyBuiltActor extends AbstractActor {
  private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  @Override
  public Receive createReceive() {
    ReceiveBuilder builder = ReceiveBuilder.create();

    builder.match(
        String.class,
        s -> {
          log.info("Received String message: {}", s);
        });

    // do some other stuff in between

    builder.matchAny(o -> log.info("received unknown message"));

    return builder.build();
  }
}
```

在 Actor 中，使用小方法也是一种很好的做法。建议将消息处理的实际工作委托给方法，而不是在每个`lambda`中定义具有大量代码的大型`ReceiveBuilder`。一个结构良好的 Actor 可以是这样的：

```java
static class WellStructuredActor extends AbstractActor {

  public static class Msg1 {}

  public static class Msg2 {}

  public static class Msg3 {}

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(Msg1.class, this::receiveMsg1)
        .match(Msg2.class, this::receiveMsg2)
        .match(Msg3.class, this::receiveMsg3)
        .build();
  }

  private void receiveMsg1(Msg1 msg) {
    // actual work
  }

  private void receiveMsg2(Msg2 msg) {
    // actual work
  }

  private void receiveMsg3(Msg3 msg) {
    // actual work
  }
}
```

这样做有以下好处：

- 更容易看到 Actor 能处理什么样的信息
- 异常情况下的可读堆栈跟踪
- 更好地使用性能分析工具
- Java HotSpot 有更好的机会进行优化

`Receive`可以通过其他方式实现，而不是使用`ReceiveBuilder`，因为它最终只是一个 Scala `PartialFunction`的包装器。在 Java 中，可以通过扩展`AbstractPartialFunction`实现`PartialFunction`。例如，可以实现「[与 DSL 匹配的 Vavr 模式适配器](http://www.vavr.io/vavr-docs/#_pattern_matching)」，有关更多详细信息，请参阅「[Akka Vavr 示例项目](https://developer.lightbend.com/start/?group=akka&project=akka-sample-vavr)」。

如果对某些 Actor 来说，验证`ReceiveBuilder`匹配逻辑是一个瓶颈，那么你可以考虑通过扩展`UntypedAbstractActor`而不是`AbstractActor`来在较低的级别实现它。`ReceiveBuilder`创建的分部函数由每个`match`语句的多个`lambda`表达式组成，其中每个`lambda`都引用要运行的代码。这是 JVM 在优化时可能会遇到的问题，并且产生的代码的性能可能不如非类型化版本。当扩展`UntypedAbstractActor`时，每个消息都作为非类型化`Object`接收，你必须以其他方式检查并转换为实际的消息类型，如下所示：

```java
static class OptimizedActor extends UntypedAbstractActor {

  public static class Msg1 {}

  public static class Msg2 {}

  public static class Msg3 {}

  @Override
  public void onReceive(Object msg) throws Exception {
    if (msg instanceof Msg1) receiveMsg1((Msg1) msg);
    else if (msg instanceof Msg2) receiveMsg2((Msg2) msg);
    else if (msg instanceof Msg3) receiveMsg3((Msg3) msg);
    else unhandled(msg);
  }

  private void receiveMsg1(Msg1 msg) {
    // actual work
  }

  private void receiveMsg2(Msg2 msg) {
    // actual work
  }

  private void receiveMsg3(Msg3 msg) {
    // actual work
  }
}
```

## 回复消息

如果你想有一个回复消息的句柄，可以使用`getSender()`，它会给你一个`ActorRef`。你可以通过使用`getSender().tell(replyMsg, getSelf())`发送`ActorRef`来进行回复。你还可以存储`ActorRef`以供稍后回复或传递给其他 Actor。如果没有发送者（发送的消息没有 Actor 或`Future`上下文），那么发送者默认为死信 Actor 引用。

```java
getSender().tell(s, getSelf());
```

## 接收超时

`ActorContext`的`setReceiveTimeout`方法定义了非活动超时，在该超时之后，将触发发送`ReceiveTimeout`消息。指定超时时间后，接收函数应该能够处理`akka.actor.ReceiveTimeout`消息。`1`毫秒是支持的最小超时时间。

请注意，接收超时可能会在另一条消息排队后立即触发并排队`ReceiveTimeout`消息；因此，不保证接收超时，如通过此方法配置的那样，事先必须有空闲时间。

设置后，接收超时将保持有效（即在非活动期后继续重复触发），可以通过传入`Duration.Undefined`消息来关闭此功能。

```java
static class ReceiveTimeoutActor extends AbstractActor {
  public ReceiveTimeoutActor() {
    // To set an initial delay
    getContext().setReceiveTimeout(Duration.ofSeconds(10));
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .matchEquals(
            "Hello",
            s -> {
              // To set in a response to a message
              getContext().setReceiveTimeout(Duration.ofSeconds(1));
            })
        .match(
            ReceiveTimeout.class,
            r -> {
              // To turn it off
              getContext().cancelReceiveTimeout();
            })
        .build();
  }
}
```

标记为`NotInfluenceReceiveTimeout`的消息将不会重置计时器。当`ReceiveTimeout`受外部不活动而不受内部活动（如定时勾选消息）影响时，这可能会很有用。

## 定时器和调度消息
通过直接使用「[调度程序](https://github.com/guobinhit/akka-guide/blob/master/articles/index-utilities/scheduler.md)」，可以将消息安排在以后的时间点发送，但是在将 Actor 中的定期或单个消息安排到自身时，使用对命名定时器的支持更为方便和安全。当 Actor 重新启动并由定时器处理时，调度消息的生命周期可能难以管理。

```java
import java.time.Duration;
import akka.actor.AbstractActorWithTimers;

static class MyActor extends AbstractActorWithTimers {

  private static Object TICK_KEY = "TickKey";

  private static final class FirstTick {}

  private static final class Tick {}

  public MyActor() {
    getTimers().startSingleTimer(TICK_KEY, new FirstTick(), Duration.ofMillis(500));
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            FirstTick.class,
            message -> {
              // do something useful here
              getTimers().startPeriodicTimer(TICK_KEY, new Tick(), Duration.ofSeconds(1));
            })
        .match(
            Tick.class,
            message -> {
              // do something useful here
            })
        .build();
  }
}
```

每个定时器都有一个键，可以更换或取消。它保证不会收到来自具有相同键的定时器的前一个实例的消息，即使当它被取消或新定时器启动时，它可能已经在邮箱中排队。

定时器绑定到拥有它的 Actor 的生命周期，因此当它重新启动或停止时自动取消。请注意，`TimerScheduler`不是线程安全的，即它只能在拥有它的 Actor 中使用。

## 停止 Actor

通过调用`ActorRefFactory`的`stop`方法（即`ActorContext`或`ActorSystem`）来停止 Actor。通常，上下文用于停止 Actor 本身或子 Actor，以及停止顶级 Actor 的系统。Actor 的实际终止是异步执行的，也就是说，`stop`可能会在 Actor 停止之前返回。

```java
import akka.actor.ActorRef;
import akka.actor.AbstractActor;

public class MyStoppingActor extends AbstractActor {

  ActorRef child = null;

  // ... creation of child ...

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .matchEquals("interrupt-child", m -> getContext().stop(child))
        .matchEquals("done", m -> getContext().stop(getSelf()))
        .build();
  }
}
```

当前邮件（如果有）的处理将在 Actor 停止之前继续，但不会处理邮箱中的其他邮件。默认情况下，这些消息将发送到`ActorSystem`的`deadLetters`，但这取决于邮箱的实现。

一个 Actor 的终止分两步进行：首先，Actor 暂停其邮箱处理并向其所有子级发送停止命令，然后继续处理其子级的内部终止通知，直到最后一个终止，最后终止其自身（调用`postStop`、转储邮箱、在`DeathWatch`上发布`Terminated`、通知其监督者）。此过程确保 Actor 系统子树以有序的方式终止，将`stop`命令传播到叶，并将其确认信息收集回已停止的监督者。如果其中一个 Actor 没有响应（即长时间处理消息，因此不接收`stop`命令），那么整个过程将被阻塞。

在`ActorSystem.terminate()`之后，系统守护者 Actor 将被停止，上述过程将确保整个系统的正确终止。

`postStop()`钩子在 Actor 完全停止后调用。这样可以清理资源：

```java
@Override
public void postStop() {
  final String message = "stopped";
  // don’t forget to think about who is the sender (2nd argument)
  target.tell(message, getSelf());
  final Object result = "";
  target.forward(result, getContext());
  target = null;
}
```

- **注释**：由于停止 Actor 是异步的，因此不能立即重用刚刚停止的子级的名称；这将导致`InvalidActorNameException`。相反，`watch()`终止的 Actor，并创建其替换，以响应最终到达的`Terminated`消息。

### PoisonPill

你还可以向 Actor 发送`akka.actor.PoisonPill`消息，该消息将在处理消息时停止 Actor。`PoisonPill`作为普通消息排队，并将在邮箱中已排队的消息之后处理。

```java
victim.tell(akka.actor.PoisonPill.getInstance(), ActorRef.noSender());
```

### 杀死一个 Actor

你也可以通过发送一条`Kill`消息来“杀死”一个 Actor。与`PoisonPill`不同的是，这可能会使 Actor 抛出`ActorKilledException`，并触发失败。Actor 将暂停操作，并询问其监督者如何处理故障，这可能意味着恢复 Actor、重新启动或完全终止 Actor。更多信息，请参阅「[监督意味着什么？](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/supervision.md#%E7%9B%91%E7%9D%A3%E6%84%8F%E5%91%B3%E7%9D%80%E4%BB%80%E4%B9%88)」。

像这样使用`Kill`：

```java
victim.tell(akka.actor.Kill.getInstance(), ActorRef.noSender());

// expecting the actor to indeed terminate:
expectTerminated(Duration.ofSeconds(3), victim);
```

一般来说，虽然在设计 Actor 交互时不建议过分依赖于`PoisonPill`或`Kill`，但通常情况下，鼓励使用诸如`PleaseCleanupAndStop`之类的协议级消息，因为 Actor 知道如何处理这些消息。像`PoisonPill`和`Kill`这样的信息是为了能够停止那些你无法控制的 Actor  的。

### 优雅的停止

如果你需要等待终止或组合多个 Actor 的有序终止，则`gracefulStop`非常有用：

```java
import static akka.pattern.Patterns.gracefulStop;
import akka.pattern.AskTimeoutException;
import java.util.concurrent.CompletionStage;

try {
  CompletionStage<Boolean> stopped =
      gracefulStop(actorRef, Duration.ofSeconds(5), Manager.SHUTDOWN);
  stopped.toCompletableFuture().get(6, TimeUnit.SECONDS);
  // the actor has been stopped
} catch (AskTimeoutException e) {
  // the actor wasn't stopped within 5 seconds
}
```

当`gracefulStop()`成功返回时，Actor 的`postStop()`钩子将被执行：在`postStop()`结尾和`gracefulStop()`返回之间存在一个“发生在边缘之前（`happens-before edge`）”的关系。

在上面的例子中，一个定制的`Manager.Shutdown`消息被发送到目标 Actor，以启动停止 Actor 的过程。你可以为此使用`PoisonPill`，但在停止目标 Actor 之前，你与其他 Actor 进行交互的可能性很有限。可以在`postStop`中处理简单的清理任务。

- **警告**：请记住，停止的 Actor 和取消注册的 Actor 是彼此异步发生的独立事件。因此，在`gracefulStop()`返回后，你可能会发现该名称仍在使用中。为了保证正确的注销，只能重用你控制的监督者中的名称，并且只响应`Terminated`消息，即不用于顶级 Actor。

### 协调关闭

有一个名为`CoordinatedShutdown`的扩展，它将按特定顺序停止某些 Actor 和服务，并在关闭过程中执行注册的任务。

关闭阶段的顺序在配置`akka.coordinated-shutdown.phases`中定义。默认阶段定义为：

```xml
# CoordinatedShutdown is enabled by default and will run the tasks that
# are added to these phases by individual Akka modules and user logic.
#
# The phases are ordered as a DAG by defining the dependencies between the phases
# to make sure shutdown tasks are run in the right order.
#
# In general user tasks belong in the first few phases, but there may be use
# cases where you would want to hook in new phases or register tasks later in
# the DAG.
#
# Each phase is defined as a named config section with the
# following optional properties:
# - timeout=15s: Override the default-phase-timeout for this phase.
# - recover=off: If the phase fails the shutdown is aborted
#                and depending phases will not be executed.
# - enabled=off: Skip all tasks registered in this phase. DO NOT use
#                this to disable phases unless you are absolutely sure what the
#                consequences are. Many of the built in tasks depend on other tasks
#                having been executed in earlier phases and may break if those are disabled.
# depends-on=[]: Run the phase after the given phases
phases {

  # The first pre-defined phase that applications can add tasks to.
  # Note that more phases can be added in the application's
  # configuration by overriding this phase with an additional
  # depends-on.
  before-service-unbind {
  }

  # Stop accepting new incoming connections.
  # This is where you can register tasks that makes a server stop accepting new connections. Already
  # established connections should be allowed to continue and complete if possible.
  service-unbind {
    depends-on = [before-service-unbind]
  }

  # Wait for requests that are in progress to be completed.
  # This is where you register tasks that will wait for already established connections to complete, potentially
  # also first telling them that it is time to close down.
  service-requests-done {
    depends-on = [service-unbind]
  }

  # Final shutdown of service endpoints.
  # This is where you would add tasks that forcefully kill connections that are still around.
  service-stop {
    depends-on = [service-requests-done]
  }

  # Phase for custom application tasks that are to be run
  # after service shutdown and before cluster shutdown.
  before-cluster-shutdown {
    depends-on = [service-stop]
  }

  # Graceful shutdown of the Cluster Sharding regions.
  # This phase is not meant for users to add tasks to.
  cluster-sharding-shutdown-region {
    timeout = 10 s
    depends-on = [before-cluster-shutdown]
  }

  # Emit the leave command for the node that is shutting down.
  # This phase is not meant for users to add tasks to.
  cluster-leave {
    depends-on = [cluster-sharding-shutdown-region]
  }

  # Shutdown cluster singletons
  # This is done as late as possible to allow the shard region shutdown triggered in
  # the "cluster-sharding-shutdown-region" phase to complete before the shard coordinator is shut down.
  # This phase is not meant for users to add tasks to.
  cluster-exiting {
    timeout = 10 s
    depends-on = [cluster-leave]
  }

  # Wait until exiting has been completed
  # This phase is not meant for users to add tasks to.
  cluster-exiting-done {
    depends-on = [cluster-exiting]
  }

  # Shutdown the cluster extension
  # This phase is not meant for users to add tasks to.
  cluster-shutdown {
    depends-on = [cluster-exiting-done]
  }

  # Phase for custom application tasks that are to be run
  # after cluster shutdown and before ActorSystem termination.
  before-actor-system-terminate {
    depends-on = [cluster-shutdown]
  }

  # Last phase. See terminate-actor-system and exit-jvm above.
  # Don't add phases that depends on this phase because the
  # dispatcher and scheduler of the ActorSystem have been shutdown.
  # This phase is not meant for users to add tasks to.
  actor-system-terminate {
    timeout = 10 s
    depends-on = [before-actor-system-terminate]
  }
}
```

如果需要，可以在应用程序的配置中添加更多的阶段（`phases`），方法是使用附加的`depends-on`覆盖阶段。尤其是在`before-service-unbind`、`before-cluster-shutdown`和`before-actor-system-terminate`的阶段，是针对特定于应用程序的阶段或任务的。

默认阶段是以单个线性顺序定义的，但是通过定义阶段之间的依赖关系，可以将阶段排序为有向非循环图（`DAG`）。阶段是按 DAG 的拓扑排序的。

可以将任务添加到具有以下内容的阶段：

```java
CoordinatedShutdown.get(system)
    .addTask(
        CoordinatedShutdown.PhaseBeforeServiceUnbind(),
        "someTaskName",
        () -> {
          return akka.pattern.Patterns.ask(someActor, "stop", Duration.ofSeconds(5))
              .thenApply(reply -> Done.getInstance());
        });
```

在任务完成后，应返回`CompletionStage<Done>`。任务名称参数仅用于调试或日志记录。

添加到同一阶段的任务是并行执行的，没有任何排序假设。在完成前一阶段的所有任务之前，下一阶段将不会启动。

如果任务没有在配置的超时内完成（请参见「[reference.conf](https://doc.akka.io/docs/akka/current/general/configuration.html#config-akka-actor)」），则下一个阶段无论如何都会启动。如果任务失败或未在超时内完成，则可以为一个阶段配置`recover=off`以中止关闭过程的其余部分。

任务通常应在系统启动后尽早注册。运行时，将执行已注册的协调关闭任务，但不会运行添加得太晚的任务。

要启动协调关闭进程，可以对`CoordinatedShutdown`扩展调用`runAll`：

```java
CompletionStage<Done> done =
    CoordinatedShutdown.get(system).runAll(CoordinatedShutdown.unknownReason());
```

多次调用`runAll`方法是安全的，它只能运行一次。

这也意味着`ActorSystem`将在最后一个阶段终止。默认情况下，不会强制停止 JVM（如果终止了所有非守护进程线程，则会停止 JVM）。要启用硬`System.exit`作为最终操作，可以配置：

```java
akka.coordinated-shutdown.exit-jvm = on
```

当使用「[Akka 集群](https://github.com/guobinhit/akka-guide/blob/master/articles/clustering/cluster-usage.md)」时，当集群节点将自己视为`Exiting`时，`CoordinatedShutdown`将自动运行，即从另一个节点离开将触发离开节点上的关闭过程。当使用 Akka 集群时，会自动添加集群的优雅离开任务，包括集群单例的优雅关闭和集群分片，即运行关闭过程也会触发尚未进行的优雅离开。

默认情况下，当 JVM 进程退出时，例如通过`kill SIGTERM`信号（`SIGINT`时`Ctrl-C`不起作用），将运行`CoordinatedShutdown`。此行为可以通过以下方式禁用：

```java
akka.coordinated-shutdown.run-by-jvm-shutdown-hook=off
```

如果你有特定于应用程序的 JVM 关闭钩子，建议你通过`CoordinatedShutdown`对它们进行注册，以便它们在 Akka 内部关闭钩子之前运行，例如关闭 Akka 远程处理。

```java
CoordinatedShutdown.get(system)
    .addJvmShutdownHook(() -> System.out.println("custom JVM shutdown hook..."));
```

对于某些测试，可能不希望通过`CoordinatedShutdown`来终止`ActorSystem`。你可以通过将以下内容添加到测试时使用的`ActorSystem`的配置中来禁用此功能：

```xml
# Don't terminate ActorSystem via CoordinatedShutdown in tests
akka.coordinated-shutdown.terminate-actor-system = off
akka.coordinated-shutdown.run-by-jvm-shutdown-hook = off
akka.cluster.run-coordinated-shutdown-when-down = off
```

## Become/Unbecome
### 升级

Akka 支持在运行时对 Actor 的消息循环（例如其实现）进行热交换：从 Actor 内部调用`context.become`方法。`become`采用实现新消息处理程序的`PartialFunction<Object, BoxedUnit>`。热交换代码保存在一个`Stack`中，可以压入和弹出。

- **警告**：请注意，Actor 在被其监督者重新启动时将恢复其原始行为。

要使用`become`热交换 Actor 的行为，可以参考以下操作：

```java
static class HotSwapActor extends AbstractActor {
  private AbstractActor.Receive angry;
  private AbstractActor.Receive happy;

  public HotSwapActor() {
    angry =
        receiveBuilder()
            .matchEquals(
                "foo",
                s -> {
                  getSender().tell("I am already angry?", getSelf());
                })
            .matchEquals(
                "bar",
                s -> {
                  getContext().become(happy);
                })
            .build();

    happy =
        receiveBuilder()
            .matchEquals(
                "bar",
                s -> {
                  getSender().tell("I am already happy :-)", getSelf());
                })
            .matchEquals(
                "foo",
                s -> {
                  getContext().become(angry);
                })
            .build();
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .matchEquals("foo", s -> getContext().become(angry))
        .matchEquals("bar", s -> getContext().become(happy))
        .build();
  }
}
```

`become`方法的这种变体对于许多不同的事情都很有用，例如实现有限状态机（`FSM`，例如「[Dining Hakkers](https://developer.lightbend.com/start/)」）。它将替换当前行为（即行为堆栈的顶部），这意味着你不使用`unbecome`，而是始终显式安装下一个行为。

另一种使用`become`的方法不是替换而是添加到行为堆栈的顶部。在这种情况下，必须注意确保`pop`操作的数量（即`unbecome`）与`push`操作的数量在长期内匹配，否则这将导致内存泄漏，这就是为什么此行为不是默认行为。

```java
static class Swapper extends AbstractLoggingActor {
  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .matchEquals(
            Swap,
            s -> {
              log().info("Hi");
              getContext()
                  .become(
                      receiveBuilder()
                          .matchEquals(
                              Swap,
                              x -> {
                                log().info("Ho");
                                getContext()
                                    .unbecome(); // resets the latest 'become' (just for fun)
                              })
                          .build(),
                      false); // push on top instead of replace
            })
        .build();
  }
}

static class SwapperApp {
  public static void main(String[] args) {
    ActorSystem system = ActorSystem.create("SwapperSystem");
    ActorRef swapper = system.actorOf(Props.create(Swapper.class), "swapper");
    swapper.tell(Swap, ActorRef.noSender()); // logs Hi
    swapper.tell(Swap, ActorRef.noSender()); // logs Ho
    swapper.tell(Swap, ActorRef.noSender()); // logs Hi
    swapper.tell(Swap, ActorRef.noSender()); // logs Ho
    swapper.tell(Swap, ActorRef.noSender()); // logs Hi
    swapper.tell(Swap, ActorRef.noSender()); // logs Ho
    system.terminate();
  }
}
```

### 对 Scala Actor 嵌套接收进行编码，而不会意外泄漏内存

请参阅「[Unnested receive example](https://github.com/akka/akka/blob/v2.5.21/akka-docs/src/test/scala/docs/actor/UnnestedReceives.scala)」。

## Stash

`AbstractActorWithStash`类使 Actor 能够临时存储"不能"或"不应该"使用 Actor 当前行为处理的消息。更改 Actor 的消息处理程序后，即在调用`getContext().become()`或`getContext().unbecome()`之前，所有隐藏的消息都可以“`unstashed`”，从而将它们预存到 Actor 的邮箱中。这样，可以按照与最初接收到的消息相同的顺序处理隐藏的消息。扩展`AbstractActorWithStash`的 Actor 将自动获得基于`deque`的邮箱。

- **注释**：抽象类`AbstractActorWithStash`实现了标记接口`RequiresMessageQueue<DequeBasedMessageQueueSemantics>`，如果希望对邮箱进行更多控制，请参阅「[邮箱](https://github.com/guobinhit/akka-guide/blob/master/articles/actors/mailboxes.md)」文档。

下面是`AbstractActorWithStash`类的一个示例：

```java
static class ActorWithProtocol extends AbstractActorWithStash {
  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .matchEquals(
            "open",
            s -> {
              getContext()
                  .become(
                      receiveBuilder()
                          .matchEquals(
                              "write",
                              ws -> {
                                /* do writing */
                              })
                          .matchEquals(
                              "close",
                              cs -> {
                                unstashAll();
                                getContext().unbecome();
                              })
                          .matchAny(msg -> stash())
                          .build(),
                      false);
            })
        .matchAny(msg -> stash())
        .build();
  }
}
```

调用`stash()`会将当前消息（Actor 最后收到的消息）添加到 Actor 的`stash`中。它通常在处理 Actor 消息处理程序中的默认情况时调用，以存储其他情况未处理的消息。将同一条消息存储两次是非法的；这样做会导致`IllegalStateException`。`stash`也可以是有界的，在这种情况下，调用`stash()`可能导致容量冲突，从而导致`StashOverflowException`。可以使用邮箱配置的`stash-capacity`设置（一个`int`值）存储容量。

调用`unstashAll()`将消息从`stash`排队到 Actor 的邮箱，直到达到邮箱的容量（如果有），请注意，`stash`中的消息是预先发送到邮箱的。如果有界邮箱溢出，将引发`MessageQueueAppendFailedException`。调用`unstashAll()`后，`stash`保证为空。

`stash`由`scala.collection.immutable.Vector`支持。因此，即使是非常大量的消息也可能被存储起来，而不会对性能产生重大影响。

请注意，与邮箱不同，`stash`是短暂的 Actor 状态的一部分。因此，它应该像 Actor 状态中具有相同属性的其他部分一样进行管理。`preRestart`的`AbstractActorWithStash`实现将调用`unstashAll()`，这通常是需要的行为。

- **注释**：如果你想强制你的 Actor 只能使用无界的`stash`，那么你应该使用`AbstractActorWithUnboundedStash`类。

## Actor 和异常

当 Actor 处理消息时，可能会引发某种异常，例如数据库异常。

### 消息发生了什么？

如果在处理邮件时引发异常（即从邮箱中取出并移交给当前行为），则此邮件将丢失。重要的是要知道它不会放回邮箱。因此，如果你想重试处理消息，你需要自己处理它，捕获异常并重试处理流程。确保对重试次数进行了限制，因为你不希望系统进行`livelock`，否则的话，这会在程序没有进展的情况下消耗大量 CPU 周期。

### 邮箱发生了什么》

如果在处理邮件时引发异常，则邮箱不会发生任何异常。如果 Actor 重新启动，则会出现相同的邮箱。因此，该邮箱上的所有邮件也将在那里。

### Actor 发生了什么？

如果 Actor 内的代码抛出异常，则该 Actor 将被挂起，并且监控过程将启动。根据监督者的决定，Actor 被恢复（好像什么都没有发生）、重新启动（清除其内部状态并从头开始）或终止。

## 初始化模式

Actor 的丰富生命周期钩子提供了一个有用的工具箱来实现各种初始化模式。在`ActorRef`的生命周期中，Actor 可能会经历多次重新启动，旧实例被新实例替换，外部观察者看不见内部的变化，外部观察者只看到`ActorRef`引用。

每次实例化一个 Actor 时，可能都需要初始化，但有时只需要在创建`ActorRef`时对第一个实例进行初始化。以下部分提供了不同初始化需求的模式。

### 通过构造函数初始化

使用构造函数进行初始化有很多好处。首先，它让使用`val`字段存储在 Actor 实例的生命周期中不发生更改的任何状态成为可能，从而使 Actor 的实现更加健壮。当创建一个调用`actorOf`的 Actor 实例时，也会在重新启动时调用构造函数，因此 Actor 的内部始终可以假定发生了正确的初始化。这也是这种方法的缺点，因为在某些情况下，人们希望避免在重新启动时重新初始化内部信息。例如，在重新启动时保护子 Actor 通常很有用。下面的部分提供了这个案例的模式。


### 通过 preStart 初始化

在第一个实例的初始化过程中，即在创建`ActorRef`时，只直接调用一次 Actor 的`preStart()`方法。在重新启动的情况下，`postRestart()`调用`preStart()`，因此如果不重写，则在每次重新启动时都会调用`preStart()`。但是，通过重写`postRestart()`，可以禁用此行为，并确保只有一个对`preStart()`的调用。

此模式的一个有用用法是在重新启动期间禁用为子级创建新的`ActorRef`。这可以通过重写`preRestart()`来实现。以下是这些生命周期挂钩的默认实现：

```java
@Override
public void preStart() {
  // Initialize children here
}

// Overriding postRestart to disable the call to preStart()
// after restarts
@Override
public void postRestart(Throwable reason) {}

// The default implementation of preRestart() stops all the children
// of the actor. To opt-out from stopping the children, we
// have to override preRestart()
@Override
public void preRestart(Throwable reason, Optional<Object> message) throws Exception {
  // Keep the call to postStop(), but no stopping of children
  postStop();
}
```

请注意，子 Actor 仍然重新启动，但没有创建新的`ActorRef`。可以递归地为子级应用相同的原则，确保只在创建引用时调用它们的`preStart()`方法。

有关更多信息，请参阅「[重启意味着什么？](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/supervision.md#%E9%87%8D%E5%90%AF%E6%84%8F%E5%91%B3%E7%9D%80%E4%BB%80%E4%B9%88)」。

### 通过消息传递初始化

有些情况下，在构造函数中无法传递 Actor 初始化所需的所有信息，例如在存在循环依赖项的情况下。在这种情况下，Actor 应该监听初始化消息，并使用`become()`或有限状态机（`finite state-machine`）状态转换来编码 Actor 的初始化和未初始化状态。

```java
@Override
public Receive createReceive() {
  return receiveBuilder()
      .matchEquals(
          "init",
          m1 -> {
            initializeMe = "Up and running";
            getContext()
                .become(
                    receiveBuilder()
                        .matchEquals(
                            "U OK?",
                            m2 -> {
                              getSender().tell(initializeMe, getSelf());
                            })
                        .build());
          })
      .build();
}
```

如果 Actor 可能在初始化消息之前收到消息，那么一个有用的工具可以是`Stash`存储消息，直到初始化完成，然后在 Actor 初始化之后重放消息。

- **警告**：此模式应小心使用，并且仅当上述模式均不适用时才应用。其中一个潜在的问题是，消息在发送到远程 Actor 时可能会丢失。此外，在未初始化状态下发布`ActorRef`可能会导致在初始化完成之前接收到用户消息的情况。



----------

**英文原文链接**：[Actors](https://doc.akka.io/docs/akka/current/actors.html).


----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————