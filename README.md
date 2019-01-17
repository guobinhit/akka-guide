# Akka 中文指南

Akka 是一个用 Scala 编写的库，用于在 JVM 平台上简化编写可容错的、高可伸缩性的 Java 和 Scala 的 Actor 模型应用，其同时提供了Java 和 Scala 的开发接口。Akka 允许我们专注于满足业务需求，而不是编写初级代码。在 Akka 里，Actor 之间通信的唯一机制就是消息传递。Akka 对 Actor 模型的使用提供了一个抽象级别，使得编写正确的并发、并行和分布式系统更加容易。Actor 模型贯穿了整个 Akka 库，为我们提供了一致的理解和使用它们的方法。

## 目录一

- [安全公告](https://blog.csdn.net/qq_35246620/article/details/86417274) [[Security Announcements](https://doc.akka.io/docs/akka/current/security/index.html)]
- [入门指南](https://blog.csdn.net/qq_35246620/article/details/86293353) [[Getting Started Guide](https://doc.akka.io/docs/akka/current/guide/index.html)]
  - [Akka 简介](https://blog.csdn.net/qq_35246620/article/details/86417820) [[Introduction to Akka](https://doc.akka.io/docs/akka/current/guide/introduction.html)]
  - [为什么现代系统需要新的编程模型](https://blog.csdn.net/qq_35246620/article/details/86417820) [[Why modern systems need a new programming model](https://doc.akka.io/docs/akka/current/guide/actors-motivation.html) ]
  -  [Actor 模型如何满足现代分布式系统的需求](https://blog.csdn.net/qq_35246620/article/details/86480911) [[How the Actor Model Meets the Needs of Modern, Distributed Systems](https://doc.akka.io/docs/akka/current/guide/actors-intro.html)]
  - [Akka 库和模块概述](https://blog.csdn.net/qq_35246620/article/details/86488507) [[Overview of Akka libraries and modules](https://doc.akka.io/docs/akka/current/guide/modules.html)]
  - [Akka 应用程序示例简介](https://blog.csdn.net/qq_35246620/article/details/86495572) [[Introduction to the Example](https://doc.akka.io/docs/akka/current/guide/tutorial.html)]
  - [第 1 部分: Actor 的体系结构](https://blog.csdn.net/qq_35246620/article/details/86496208) [[Part 1: Actor Architecture](https://doc.akka.io/docs/akka/current/guide/tutorial_1.html)]
  - [第 2 部分: 创建第一个 Actor](https://blog.csdn.net/qq_35246620/article/details/86505966) [[Part 2: Creating the First Actor](https://doc.akka.io/docs/akka/current/guide/tutorial_2.html)]
  - [第 3 部分: 使用设备 Actors](https://blog.csdn.net/qq_35246620/article/details/86506386) [[Part 3: Working with Device Actors](https://doc.akka.io/docs/akka/current/guide/tutorial_3.html)]
- [General Concepts](https://doc.akka.io/docs/akka/current/general/index.html)
- [Actors](https://doc.akka.io/docs/akka/current/general/index.html)
- [Akka Typed](https://doc.akka.io/docs/akka/current/general/index.html)
- [Clustering](https://doc.akka.io/docs/akka/current/general/index.html)
- [Streams](https://doc.akka.io/docs/akka/current/general/index.html)
- [Networking](https://doc.akka.io/docs/akka/current/general/index.html)
- [Discovery](https://doc.akka.io/docs/akka/current/general/index.html)
- [Futures and Agents](https://doc.akka.io/docs/akka/current/general/index.html)
- [Utilities](https://doc.akka.io/docs/akka/current/general/index.html)
- [Other Akka modules](https://doc.akka.io/docs/akka/current/general/index.html)
- [HowTo: Common Patterns](https://doc.akka.io/docs/akka/current/general/index.html)
- [Project Information](https://doc.akka.io/docs/akka/current/project/index.html)
- [Additional Information](https://doc.akka.io/docs/akka/current/additional/index.html)


## 目录二

- [安全公告](https://blog.csdn.net/qq_35246620/article/details/86417274)
- [入门指南](https://blog.csdn.net/qq_35246620/article/details/86293353)
  - [Akka 简介](https://blog.csdn.net/qq_35246620/article/details/86417820) 
  - [为什么现代系统需要新的编程模型](https://blog.csdn.net/qq_35246620/article/details/86417820) 
  -  [Actor 模型如何满足现代分布式系统的需求](https://blog.csdn.net/qq_35246620/article/details/86480911)
  - [Akka 库和模块概述](https://blog.csdn.net/qq_35246620/article/details/86488507) 
  - [Akka 应用程序示例简介](https://blog.csdn.net/qq_35246620/article/details/86495572)
  - [第 1 部分: Actor 的体系结构](https://blog.csdn.net/qq_35246620/article/details/86496208)
  - [第 2 部分: 创建第一个 Actor](https://blog.csdn.net/qq_35246620/article/details/86505966)
  - [第 3 部分: 使用设备 Actors](https://blog.csdn.net/qq_35246620/article/details/86506386)
- [General Concepts](https://doc.akka.io/docs/akka/current/general/index.html)
- [Actors](https://doc.akka.io/docs/akka/current/general/index.html)
- [Akka Typed](https://doc.akka.io/docs/akka/current/general/index.html)
- [Clustering](https://doc.akka.io/docs/akka/current/general/index.html)
- [Streams](https://doc.akka.io/docs/akka/current/general/index.html)
- [Networking](https://doc.akka.io/docs/akka/current/general/index.html)
- [Discovery](https://doc.akka.io/docs/akka/current/general/index.html)
- [Futures and Agents](https://doc.akka.io/docs/akka/current/general/index.html)
- [Utilities](https://doc.akka.io/docs/akka/current/general/index.html)
- [Other Akka modules](https://doc.akka.io/docs/akka/current/general/index.html)
- [HowTo: Common Patterns](https://doc.akka.io/docs/akka/current/general/index.html)
- [Project Information](https://doc.akka.io/docs/akka/current/project/index.html)
- [Additional Information](https://doc.akka.io/docs/akka/current/additional/index.html)


----------

**翻译声明**：本文翻译自 Akka 官方文档「[Akka Documentation](https://doc.akka.io/docs/akka/current/index.html)」，感兴趣的同学可以阅读英文原文！
