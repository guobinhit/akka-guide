# Akka 简介
欢迎来到 Akka，它是一组用于设计跨越处理器和网络的可扩展、弹性系统的开源库。Akka 允许你专注于满足业务需求，而不是编写初级代码来提供可靠的行为、容错性和高性能。

许多常见的实践和公认的编程模型并不能解决现代计算机体系结构所固有的重要挑战。为了取得成功，分布式系统必须在组件崩溃而没有响应、消息丢失而没有在线跟踪以及网络延迟波动的环境中进行处理。这些问题经常发生在精心管理的数据中心内部环境中，在虚拟化架构中更是如此。

为了帮助我们应对这些现实问题，Akka 提供：

- 不使用原子或锁之类的低级并发构造的多线程行为，甚至可以避免你考虑内存可见性问题。
- 系统及其组件之间的透明远程通信，使你不再编写和维护困难的网络代码。
- 一个集群的、高可用的体系结构，具有弹性、可按需扩展性，使你能够提供真正的反应式系统。

Akka 对 Actor 模型的使用提供了一个抽象级别，使得编写正确的并发、并行和分布式系统更加容易。Actor 模型贯穿了整个 Akka 库，为我们提供了一致的理解和使用它们的方法。因此，Akka 提供了一种深度的集成，我们无法通过选择库（`picking libraries`）来解决单个问题以及尝试将它们组合在一起。

通过学习 Akka 以及如何使用 Actor 模型，你将能够熟练的使用大量的工具集，这些工具可以在统一的编程模型中解决困难的分布式/并行系统问题，在统一的编程模型中，所有东西都紧密且高效地结合在一起。

## 如何开始？
如果这是你第一次体验 Akka，我们建议你从运行一个简单的 Hello World 项目开始。有关下载和运行 Hello World 示例的说明，请参阅「[快速入门指南](https://github.com/guobinhit/akka-guide/blob/master/articles/qucikstart-akka-java.md)」。快速入门指南将引导你完成示例代码，其中介绍了如何定义 Actor 系统、Actor 和消息，以及如何使用测试模块和日志。在 30 分钟内，你应该能够运行 Hello World 示例并了解它是如何构造的。

本入门指南提供了更高级别的信息，它涵盖了为什么 Actor 模型适合现代分布式系统的需要，并且包括一个有助于进一步了解 Akka 的教程。这些主题包括：

- [为什么现代系统需要新的编程模型](https://github.com/guobinhit/akka-guide/blob/master/articles/getting-started-guide/actors-motivation.md)
- [Actor 模型如何满足现代分布式系统的需求](https://github.com/guobinhit/akka-guide/blob/master/articles/getting-started-guide/actor-intro.md)
- [Akka 库和模块概述](https://github.com/guobinhit/akka-guide/blob/master/articles/getting-started-guide/modules.md)
- 一个基于 Hello World 示例的「[更复杂的例子](https://doc.akka.io/docs/akka/current/guide/tutorial.html)」以说明常见的 Akka 模式。


----------

**英文原文链接**：[Introduction to Akka](https://doc.akka.io/docs/akka/current/guide/introduction.html).

----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————