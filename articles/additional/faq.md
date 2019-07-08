# 常见问题
## Akka 项目
### Akka 这个名字是从哪里来的？

它是瑞典北部一座美丽的「[瑞典山](https://lh4.googleusercontent.com/-z28mTALX90E/UCOsd249TdI/AAAAAAAAAB0/zGyNNZla-zY/w442-h331/akka-beautiful-panorama.jpg)」的名字，叫做拉波尼亚。这座山有时也被称为“拉波尼亚女王”。

Akka 也是 Sámi（瑞典土著）神话中一位女神的名字。她是世界上所有美丽善良的女神。这座山可以看作是这个女神的象征。

此外，Akka 这个名字也是 Actor 内核中字母 A 和 K 的回文。

Akka 还是：

- 尼尔斯在瑞典作家塞尔玛·拉格尔夫的《尼尔斯奇遇》中穿越瑞典的那只鹅的名字
- 芬兰语中的“讨厌的老妇人”和印度语中的“姐姐”
- 一种字体的名字
- 摩洛哥的一个城镇的名字
- 一颗近地小行星的名字

## 具有明确生命周期的资源

Actor、ActorSystem、ActorMaterializer（对于流），所有这些类型的对象都绑定必须显式释放的资源。原因是，Actor 的生命是他们自己的，独立于消息是否正在传递给他们。因此，你应该始终确保对于此类对象的每次创建都实现了匹配的`stop`、`terminate`或`shutdown`调用。

特别是，你通常希望将这些值绑定到不可变的引用，即 Java 中的`final ActorSystem system`或 Scala 的`val system: ActorSystem`。

### JVM 应用程序或 Scala REPL “挂起”

由于 ActorSystem 的显式生命周期，JVM 在停止之前不会退出。因此，有必要关闭正在运行的应用程序或 Scala REPL 会话中的所有 ActorSystem，以便终止这些进程。

关闭 ActorSystem 将正确终止在其中创建的所有 Actor 和 ActorMaterializer。

## 普通 Actor

### 在我的 Actor 中使用 Future 时 sender()/getsender() 消失了，为什么？

当使用`Future`的回调时，内部 Actor 需要小心避免关闭包含 Actor 的引用，即不要从回调中调用方法或访问封闭 Actor 的可变状态。这会破坏 Actor 的封装，并可能引入同步错误和竞态条件，因为回调将被同时调度到封闭 Actor。不幸的是，目前还没有一种方法可以在编译时检测到这些非法访问。

在「[Actor 和共享可变状态](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/jmm.md#actors-%E5%92%8C%E5%85%B1%E4%BA%AB%E5%8F%AF%E5%8F%98%E7%8A%B6%E6%80%81)」文档中，可以阅读到更多关于它的信息。

### 为什么会发生 OutOfMemoryError 错误？

出现`OutOfMemoryError`有许多原因。例如，在纯基于推送的系统中，如果消息使用者的速度可能低于相应的消息生产者，则必须添加某种消息流控制。否则，消息将在使用者邮箱中排队，从而填满堆内存。

能够获取灵感的一些文章：

- [Balancing Workload across Nodes with Akka 2](https://letitcrash.com/post/29044669086/balancing-workload-across-nodes-with-akka-2#_=_)
- [Work Pulling Pattern to prevent mailbox overflow, throttle and distribute work](http://www.michaelpollmeier.com/akka-work-pulling-pattern)

## Actors Scala API
### 如何获取接收中丢失消息的编译时错误？

有一种解决方案可以帮助你获得不处理应该处理的消息的编译时警告，那就是定义 Actor 输入和输出实现基本特性的消息，然后进行匹配，以检查其是否详尽。

下面是一个示例，编译器将警告你`receive`中的匹配不是详尽的：

```java
object MyActor {
  // these are the messages we accept
  sealed abstract trait Message
  final case class FooMessage(foo: String) extends Message
  final case class BarMessage(bar: Int) extends Message

  // these are the replies we send
  sealed abstract trait Reply
  final case class BazMessage(baz: String) extends Reply
}

class MyActor extends Actor {
  import MyActor._
  def receive = {
    case message: Message =>
      message match {
        case BarMessage(bar) => sender() ! BazMessage("Got " + bar)
        // warning here:
        // "match may not be exhaustive. It would fail on the following input: FooMessage(_)"
      }
  }
}
```

## 远程处理
### 我想发送到远程系统，但它什么都没做

确保在两端都启用了远程处理：客户端和服务器。两者都需要配置主机名和端口，你需要知道服务器的端口；在大多数情况下，客户端可以使用自动端口（即配置端口`0`）。如果两个系统都在同一网络主机上运行，则它们的端口必须不同。

如果仍然看不到任何内容，请查看远程生命周期事件的日志记录告诉你的信息（通常在`INFO`级别记录）或打开「[辅助远程日志记录选项](https://github.com/guobinhit/akka-guide/blob/master/articles/index-utilities/logging.md#%E8%BE%85%E5%8A%A9%E8%BF%9C%E7%A8%8B%E6%97%A5%E5%BF%97%E8%AE%B0%E5%BD%95%E9%80%89%E9%A1%B9)」以查看所有发送和接收的消息（在`DEBUG`级别记录）。

### 调试远程处理问题时应启用哪些选项？

请看一下「[远程配置](https://doc.akka.io/docs/akka/current/remoting.html#remote-configuration)」，典型的候选配置是：

- `akka.remote.log-sent-messages`
- `akka.remote.log-received-messages`
- `akka.remote.log-remote-lifecycle-events`（这还包括反序列化错误）

### 远程 Actor 的名字是什么？

当你想向远程主机上的 Actor 发送消息时，需要知道其「[完整路径](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/addressing.md)」，其形式如下：

```yml
akka.protocol://system@host:1234/user/my/actor/hierarchy/path
```

请注意此处所需的所有部分：

- `protocol`是用于与远程系统通信的协议。大多数情况下，它是 TCP。
- `system`是远程系统的名称，必须完全匹配，区分大小写！
- `host`是远程系统的 IP 地址或 DNS 名称，它必须与该系统的配置匹配，即`akka.remote.netty.tcp.hostname`
- `1234`是远程系统侦听连接和接收消息的端口号
- `/user/my/actor/hierarchy/path`是远程系统监控层次结构中远程 Actor 的绝对路径，包括系统的监督者（即`/user`，还有其他的，如`/system`日志记录器，`/temp`保留用于`ask`的临时 Actor 引用，`/remote`启用远程部署等）；这与 Actor 如何在远程主机上打印自身引用（例如日志输出）相匹配。

### 为什么没有收到来自远程 Actor 的答复？

最常见的原因是本地系统的名称（即上面答案中的`system@host:1234`部分）无法从远程系统的网络位置访问，例如，因为主机配置为`0.0.0.0`、`localhost`或是一个`NAT`的 IP 地址。

如果你在 NAT 下或 Docker 容器内运行`ActorSystem`，请确保将`akka.remote.netty.tcp.hostname`和`akka.remote.netty.tcp.port`设置为可从其他`ActorSystem`访问的地址。如果需要将网络接口绑定到其他地址，请使用`akka.remote.netty.tcp.bind-hostname`和`akka.remote.netty.tcp.bind-port`设置。另外，请确保将网络配置为从`ActorSystem`可访问的地址转换为`ActorSystem`网络接口绑定的地址。

### 消息传递的可靠性如何？

一般规则是“至多一次传递”，即不保证传递的可靠性。可以在其上建立更强大的可靠性，Akka 就提供了这样的工具。

通过阅读「[消息传递可靠性](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/message-delivery-reliability.md)」可以了解更多的信息。

## 调试
### 如何打开调试日志记录？

要在 Actor 系统中打开调试日志记录，请将以下内容添加到配置中：

```yml
akka.loglevel = DEBUG
```

要启用不同类型的调试日志记录，请将以下内容添加到配置中：

- `akka.actor.debug.receive`将记录发送给 Actor 的所有消息，如果 Actor 的`receive`方法是`LoggingReceive`的话。
- `akka.actor.debug.autoreceive`将记录发送给所有 Actor 的所有特殊消息，如`Kill`、`PoisonPill` 等。
- `akka.actor.debug.lifecycle`将记录所有 Actor 的所有 Actor 生命周期事件。

在「[日志记录](https://github.com/guobinhit/akka-guide/blob/master/articles/index-utilities/logging.md)」和「[actor.logging-scala](https://doc.akka.io/docs/akka/current/testing.html#actor-logging)」中可以了解更多关于它的信息。




----------

**英文原文链接**：[Frequently Asked Questions](https://doc.akka.io/docs/akka/current/additional/faq.html).




----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————