# Actor 引用、路径和地址
本章描述如何在可能的分布式 Actor 系统中标识和定位 Actor。它与这样一个核心理念紧密相连：「[Actor 系统](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/actor-systems.md)」形成了内在的监督层次结构，并且 Actor 之间的通信在跨多个网络节点的位置方面是透明的。

![actor-system](https://github.com/guobinhit/akka-guide/blob/master/images/addressing/actor-system.png)

上图显示了 Actor 系统中最重要的实体之间的关系，有关详细信息，请继续阅读。

## 什么是 Actor 的引用？
Actor 引用是`ActorRef`的一个子类型，其首要目的是支持将消息发送给它所代表的 Actor。每个 Actor 都可以通过`self`字段访问其规范（本地）引用；默认情况下，对于发送给其他 Actor 的所有消息，此引用也作为发送者引用包含在内。相应地，在消息处理期间，Actor 可以通过`sender()`方法访问表示当前消息发送者的引用。

根据 Actor 系统的配置，支持几种不同类型的 Actor 引用：

- 纯本地 Actor 引用由未配置为支持网络功能的 Actor 系统使用。如果通过网络连接发送到远程 JVM，这些 Actor 引用将不起作用。
- 启用远程处理时，支持网络功能的 Actor 系统使用本地 Actor 引用，这些引用表示同一个 JVM 中的 Actor。为了在发送到其他网络节点时也可以访问，这些引用包括协议和远程寻址信息。
- 本地 Actor 引用的一个子类型用于路由器（即 Actor 混合在`Router`特性中）。它的逻辑结构与前面提到的本地引用相同，但是向它们发送消息会直接发送给它们的一个子级。
- 远程 Actor 引用表示可以使用远程通信访问的 Actor，即向其发送消息将透明地序列化消息并将其发送到远程 JVM。
- 有几种特殊类型的 Actor 引用，其行为类似于所有实际用途的本地 Actor 引用：
  - `PromiseActorRef`是一个`Promise`为了完成 Actor 响应的特殊表示。`akka.pattern.ask`创建这个 Actor 引用。
  - `DeadLetterActorRef`是死信服务的默认实现，Akka 将其目的地关闭或不存在的所有消息路由到该服务。
  - `EmptyLocalActorRef`是 Akka 在查找不存在的本地 Actor 路径时返回的：它相当于一个`DeadLetterActorRef`，但它保留了自己的路径，以便 Akka 可以通过网络发送它，并将其与该路径的其他现有 Actor 引用进行比较，其中一些引用可能是在 Actor 死亡之前获得的。
- 还有一些一次性（`one-off`）的内部实现，你应该永远都不会真正看到：
  - 有一个 Actor 引用，它不代表一个 Actor，只作为根守护者的伪监督者（`pseudo-supervisor`），我们称之为“在时空的气泡中行走的人”。
  - 在实际启动 Actor 创建工具之前启动的第一个日志记录服务是一个假 Actor 引用，它接受日志事件并将其直接打印到标准输出；它是`Logging.StandardOutLogger`。

## 什么是 Actor 路径？

由于 Actor 是以严格的层次结构方式创建的，因此存在一个唯一的 Actor 名称序列，该序列通过递归地沿着子级和父级之间的监督链接向下到 Actor 系统的根来给出。这个序列可以看作是文件系统中的封闭文件夹，因此我们采用名称`path`来引用它，尽管 Actor 层次结构与文件系统层次结构有一些基本的区别。

一个 Actor 路径由一个锚点组成，该锚点标识 Actor 系统，然后连接从根守护者到指定的 Actor 的路径元素；路径元素是被遍历的 Actor 的名称，由斜线分隔。

### Actor 的引用和路径有什么区别？
Actor 引用指定一个单独的 Actor，引用的生命周期与该 Actor 的生命周期匹配；Actor 路径表示一个可能由 Actor 位置或不由 Actor 位置标识的名称，并且路径本身没有生命周期，它永远不会变为无效。你可以在不创建 Actor 的情况下创建 Actor 路径，但在不创建相应的 Actor 的情况下无法创建 Actor 引用。

你可以创建一个 Actor，终止它，然后使用相同的 Actor 路径创建一个新的 Actor。新创建的 Actor 是原 Actor 的化身。他们不是同一个 Actor。Actor 引用旧的化身对新的化身无效。发送到旧 Actor 引用的消息将不会传递到新的化身，即使它们具有相同的路径。

### Actor 路径锚点
每个 Actor 路径都有一个地址组件，描述了协议和位置，通过这些协议和位置可以访问相应的 Actor，路径中的元素是从根目录向上的层次结构中 Actor 的名称。例如：

```
"akka://my-sys/user/service-a/worker1"                   // purely local
"akka.tcp://my-sys@host.example.com:5678/user/service-b" // remote
```

在这里，`akka.tcp`是 2.4 版本的默认远程传输；其他传输是可插拔的。主机和端口部分（如示例中的`host.example.com:5678`）的解释取决于所使用的传输机制，但必须遵守 URI 结构规则。

### 逻辑 Actor 路径
通过跟踪指向根守护者的父级监督链接获得的唯一路径称为逻辑 Actor 路径。此路径与 Actor 的创建祖先完全匹配，因此只要设置了 Actor 系统的远程处理配置（以及路径的地址组件），它就完全具有确定性。

### 物理 Actor 路径
虽然逻辑 Actor 路径描述了一个 Actor 系统中的功能位置，但是基于配置的远程部署意味着可以在与其父系统不同的网络主机上创建 Actor，即在不同的 Actor 系统中。在这种情况下，从根守护者向上遵循 Actor 路径需要遍历网络，这是一个昂贵的操作。因此，每个 Actor 也有一个物理路径，从实际 Actor 对象所在的 Actor 系统的根守护者开始。当查询其他 Actor 时，使用此路径作为发送者引用，将允许他们直接回复此 Actor，从而最小化路由所导致的延迟。

一个重要的方面是，物理 Actor 路径从不跨越多个 Actor 系统或 JVM。这意味着，如果一个 Actor 的祖先被远程监控，那么它的逻辑路径（监督层次）和物理路径（Actor 部署）可能会发生偏离。

### Actor 路径别名或符号链接？

在一些实际的文件系统中，你可能会想到一个 Actor 的“路径别名”或“符号链接”，即一个 Actor 可以使用多个路径访问。但是，你应该注意，Actor 层次结构不同于文件系统层次结构。不能自由地创建 Actor 路径（如符号链接）来引用任意的 Actor。如上述逻辑和物理 Actor 路径部分所述，Actor 路径必须是表示监督层次结构的逻辑路径，或者是表示 Actor 部署的物理路径。

## 如何获得 Actor 引用？
对于如何获取 Actor 引用，有两个通用的类别：通过创建 Actor 或通过查找 Actor，后者的功能包括从具体的 Actor 路径创建 Actor 引用和查询逻辑的 Actor 层次结构。

### 创建 Actor
Actor 系统通常是通过使用`ActorSystem.actorOf`方法在守护 Actor 下创建 Actor，然后从创建的 Actor 中使用`ActorContext.actorOf`来生成 Actor 树来启动的。这些方法返回对新创建的 Actor 的引用。每个 Actor 都可以（通过其`ActorContext`）直接访问其父级、自身及其子级的引用。这些引用可以在消息中发送给其他 Actor，从而使这些 Actor 能够直接回复。

### 用具体的路径查找 Actor
此外，可以使用`ActorSystem.actorSelection`方法查找 Actor 引用。选择（`selection`）可用于与所述 Actor 通信，并且在传递每条消息时查找所述选择对应的 Actor。

要获取绑定到特定 Actor 生命周期的`ActorRef`，你需要向 Actor 发送消息，例如内置标识消息，并使用来自 Actor 的答复的`sender()`引用。

### 绝对路径 vs. 相对路径
除了`ActorSystem.actorSelection`，还有`ActorContext.actorSelection`，在任何 Actor 中都可以作为`context.actorSelection`使用。这将生成一个 Actor 选择，与`ActorSystem`上的孪生兄弟非常相似，但它不是从 Actor 树的根开始查找路径，而是从当前 Actor 开始。由两个点（`".."`）组成的路径元素可用于访问父 Actor。例如，你可以向特定的兄弟姐妹 Actor 发送消息：

```
context.actorSelection("../brother") ! msg
```

绝对路径也可以用通常的方式在上下文中查找，即

```
context.actorSelection("/user/serviceA") ! msg
```

这都将按预期工作。

### 查询逻辑 Actor 层次结构

由于 Actor 系统形成了类似于文件系统的层次结构，因此在路径上进行匹配的方式与 Unix shells 支持的方式相同：你可以用通配符(`*«*»*`和`«?»`）替换（部分）路径元素名制定一个可以匹配零个或更多实际 Actor 的选择。因为结果不是单个 Actor 引用，所以它具有不同的`ActorSelection`类型，并且不支持`ActorRef`执行的完整操作集。可以使用`ActorSystem.actorSelection`和`ActorContext.actorSelection`方法来制定选择，并且确实支持发送消息：

```
context.actorSelection("../*") ! msg
```

将向包括当前 Actor 在内的所有兄弟姐妹 Actor 发送`msg`。对于使用`actorSelection`获取的引用，将遍历监督层次结构以执行消息发送。由于与选定内容匹配的 Actor 的确切集合可能会发生变化，即使消息正在传递给收件人，也不可能观看选定内容的实时变化。为了做到这一点，通过发送一个请求并收集所有答案，提取发送者引用，然后观察所有发现的具体 Actor 来解决不确定性。这种解决选择的方案可以在将来的版本中加以改进。

总结：`actorOf` vs. `actorSelection`

- **注释**：以上部分的详细描述可以总结和记忆如下：
  - `actorOf`只创建了一个新的 Actor，它将其创建为调用此方法的上下文（可能是任何 Actor 或 Actor 系统）的直接子级。
  - `actorSelection`仅在传递消息时查找现有的 Actor，即不创建 Actor，或在创建选择时验证 Actor 的存在。

## Actor 引用和路径相等
`ActorRef`的相等符合`ActorRef`对应于目标 Actor 化身的意图。当两个 Actor 引用具有相同的路径并指向相同的 Actor 化身时，它们将被比较为相等的。指向终止的 Actor 的引用与指向具有相同路径的其他（重新创建的）Actor 的引用不同。请注意，由失败引起的 Actor 重新启动仍然意味着它是同一个 Actor 的化身，即对于`ActorRef`的使用者来说，重新启动是不可见的。

如果需要跟踪集合中的 Actor 引用，而不关心具体的 Actor 化身，则可以使用`ActorPath`作为键，因为在比较 Actor 路径时不考虑目标 Actor 的标识符。

## 复用 Actor 路径
当一个 Actor 被终止时，它的引用将指向死信邮箱，`DeathWatch`将发布其最终的转换，一般情况下，它不会再次恢复生命（因为 Actor 的生命周期不允许这样做）。虽然可以在以后用相同的路径创建一个 Actor，因为如果不保留所有已创建的 Actor 集，就不可能执行相反的操作，但这不是一个好的实践：用`actorSelection`向一个“死亡”的 Actor 发送的消息会突然重新开始工作，但没有任何顺序保证。在这个转换和任何其他事件之间，该路径所代表的新 Actor 可能会接收到发往该路径所代表的前一个 Actor 的消息。

在非常特殊的情况下，这可能是正确的做法，但一定要将处理这一点严格限制在 Actor 的监督者身上，因为只有这样的 Actor 才能可靠地检测到名字的正确注销，在此之前，新子 Actor 的创建将失败。

当测试对象依赖于在特定路径上实例时，也可能需要在测试期间使用它。在这种情况下，最好模拟其监督者，以便将`Terminated`消息转发到测试过程中的适当点，以便后者等待正确的名称注销。

## 远程部署的交互作用

当 Actor 创建子节点时，Actor 系统的部署程序将决定新 Actor 是驻留在同一个 JVM 中，还是驻留在另一个节点上。在第二种情况下，Actor 的创建将通过网络连接触发，在不同的 JVM 中发生，从而在不同的 Actor 系统中发生。远程系统将把新 Actor 放在为此目的保留的特殊路径下，新 Actor 的监督者将是远程 Actor 引用（表示触发其创建的 Actor）。在这种情况下，`context.parent`（监督者引用）和`context.path.parent`（Actor 路径中的父节点）不表示同一个 Actor。但是，在监督者中查找子级的名称会在远程节点上找到它，保留逻辑结构，例如发送到未解析的 Actor 引用时。

![actor-path-in-system](https://github.com/guobinhit/akka-guide/blob/master/images/addressing/actor-path-in-system.png)

## 地址部分用于什么？
当通过网络发送 Actor 引用时，它由其路径表示。因此，路径必须完全编码向底层 Actor 发送消息所需的所有信息。这是通过在路径字符串的地址部分编码协议、主机和端口来实现的。当 Actor 系统从远程节点接收到 Actor 路径时，它检查该路径的地址是否与该 Actor 系统的地址匹配，在这种情况下，它将解析为 Actor 的本地引用。否则，它将由远程 Actor 引用表示。

## Actor 路径的顶级范围
在路径层次结构的根目录下，存在根目录守护者，在其上可以找到所有其他 Actor；其名称为`"/"`。下一级包括以下内容：

- `"/user"`是所有用户创建的 Actor 的顶级守护者 Actor；使用`ActorSystem.actorOf`创建的 Actor 位于此 Actor 的下面。
- `"/system"`是所有系统创建的 Actor 的顶级守护者 Actor，例如在 Actor 系统开始时通过配置自动部署日志监听器。
- `"/deadletters"`是死信 Actor，即所有发送到已停止或不存在的 Actor 的消息都会重新路由（在尽最大努力的基础上：消息也可能会丢失，即使是在本地 JVM 中）。
- `"/temp"`是所有短期系统创建的 Actor 的守护者 Actor，例如在`ActorRef.ask`的实现中使用的 Actor。
- `"/remote"`是一个人工路径，其下面所有 Actor 的监督者都是远程 Actor 引用。

像这样为 Actor 构建名称空间的需要源于一个中心且非常简单的设计目标：层次结构中的所有内容都是一个 Actor，并且所有 Actor 都以相同的方式工作。因此，你不仅可以查找你创建的 Actor，还可以查找系统守护者并向其发送消息（在本例中，它将尽职尽责地丢弃该消息）。这一强有力的原则意味着不需要记住任何怪癖，它使整个系统更加统一和一致。

如果你想更多地了解 Actor 的顶层（`top-level`）结构，可见「[The Top-Level Supervisors](https://doc.akka.io/docs/akka/current/general/supervision.html#toplevel-supervisors)」。


----------

**英文原文链接**：[Actor References, Paths and Addresses](https://doc.akka.io/docs/akka/current/general/addressing.html).


----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————