# 作为 FSM」的行为

对于非类型化的 Actor，有明确的支持来构建「[有限状态机](https://doc.akka.io/docs/akka/current/fsm.html)」。在 Akka 类型中不需要支持，因为用行为表示 FSM 很简单。

为了了解如何使用 Akka 类型的 API 来建模 FSM，下面是从「[非类型化的 Actor FSM 文档](https://doc.akka.io/docs/akka/current/fsm.html)」移植的`Buncher`示例。它演示了如何：

- 使用不同行为模拟状态
- 通过将行为表示为一种方法，在每个状态下存储数据的模型
- 实现状态超时

FSM 可以接收的事件为 Actor 可以接收的消息类型：




----------

**英文原文链接**：[Behaviors as Finite state machines](https://doc.akka.io/docs/akka/current/typed/fsm.html).



----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————