# Akka 中文指南

Akka 是一个用 Scala 编写的库，用于在 JVM 平台上简化编写可容错的、高可伸缩性的 Java 和 Scala 的 Actor 模型应用，其同时提供了Java 和 Scala 的开发接口。Akka 允许我们专注于满足业务需求，而不是编写初级代码。在 Akka 里，Actor 之间通信的唯一机制就是消息传递。Akka 对 Actor 模型的使用提供了一个抽象级别，使得编写正确的并发、并行和分布式系统更加容易。Actor 模型贯穿了整个 Akka 库，为我们提供了一致的理解和使用它们的方法。


- [Gitter Chat](https://gitter.im/akka/akka?source=orgpage)，Akka 在线交流平台；
- [Akka in GitHub](https://github.com/akka/akka)，Akka 开源项目仓库；
- [Akka Official Website](https://akka.io/)，Akka 官网。


## 快速入门指南

- [快速入门 Akka Java 指南](https://github.com/guobinhit/akka-guide/blob/master/articles/qucikstart-akka-java.md)
- [快速入门 Akka Scala 指南](https://developer.lightbend.com/guides/akka-quickstart-scala/)

## 目录

- [安全公告](https://github.com/guobinhit/akka-guide/blob/master/articles/security-announcements.md)
- [入门指南](https://github.com/guobinhit/akka-guide/blob/master/README.md)
  - [Akka 简介](https://github.com/guobinhit/akka-guide/blob/master/articles/getting-started-guide/introduction-to-akka.md) 
  - [为什么现代系统需要新的编程模型](https://github.com/guobinhit/akka-guide/blob/master/articles/getting-started-guide/actors-motivation.md) 
  - [Actor 模型如何满足现代分布式系统的需求](https://github.com/guobinhit/akka-guide/blob/master/articles/getting-started-guide/actor-intro.md)
  - [Akka 库和模块概述](https://github.com/guobinhit/akka-guide/blob/master/articles/getting-started-guide/modules.md) 
  - [Akka 应用程序示例简介](https://github.com/guobinhit/akka-guide/blob/master/articles/getting-started-guide/tutorial.md)
  - [第 1 部分: Actor 的体系结构](https://github.com/guobinhit/akka-guide/blob/master/articles/getting-started-guide/akka-guide-part1.md)
  - [第 2 部分: 创建第一个 Actor](https://github.com/guobinhit/akka-guide/blob/master/articles/getting-started-guide/akka-guide-part2.md)
  - [第 3 部分: 使用设备 Actors](https://github.com/guobinhit/akka-guide/blob/master/articles/getting-started-guide/akka-guide-part3.md)
  - [第 4 部分: 使用设备组](https://github.com/guobinhit/akka-guide/blob/master/articles/getting-started-guide/akka-guide-part4.md)
  - [第 5 部分: 查询设备组](https://github.com/guobinhit/akka-guide/blob/master/articles/getting-started-guide/akka-guide-part5.md)
- [一般概念](https://github.com/guobinhit/akka-guide/blob/master/README.md)
  - [术语及概念](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/terminology.md)
  - [Actor 系统](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/actor-systems.md)
  - [什么是 Actor？](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/actors.md)
  - [监督和监控](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/supervision.md)
  - [Actor 引用、路径和地址](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/addressing.md)
  - [位置透明](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/remoting.md)
  - [Akka 和 Java 内存模型](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/jmm.md)
  - [消息传递可靠性](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/message-delivery-reliability.md)
  - [配置](https://github.com/guobinhit/akka-guide/blob/master/articles/general-concepts/configuration.md)
- [Actors](https://github.com/guobinhit/akka-guide/blob/master/README.md)
  - [Actors](https://github.com/guobinhit/akka-guide/blob/master/articles/actors/actors.md)
  - [容错](https://github.com/guobinhit/akka-guide/blob/master/articles/actors/fault-tolerance.md)
  - [调度器](https://github.com/guobinhit/akka-guide/blob/master/articles/actors/dispatchers.md)
  - [信箱](https://github.com/guobinhit/akka-guide/blob/master/articles/actors/mailboxes.md)
  - [路由](https://github.com/guobinhit/akka-guide/blob/master/articles/actors/routing.md)
  - [FSM](https://github.com/guobinhit/akka-guide/blob/master/articles/actors/fsm.md)
  - [持久化](https://github.com/guobinhit/akka-guide/blob/master/articles/actors/persistence.md)
- [Akka Typed](https://doc.akka.io/docs/akka/current/general/index.html)
- [集群](https://github.com/guobinhit/akka-guide/blob/master/README.md)
  - [集群规范](https://github.com/guobinhit/akka-guide/blob/master/articles/clustering/cluster-specification.md) 
  - [集群的使用方法](https://github.com/guobinhit/akka-guide/blob/master/articles/clustering/cluster-usage.md) 
  - [集群感知路由器](https://github.com/guobinhit/akka-guide/blob/master/articles/clustering/cluster-routing.md) 
  - [集群单例](https://github.com/guobinhit/akka-guide/blob/master/articles/clustering/cluster-singleton.md) 
  - [集群分片](https://github.com/guobinhit/akka-guide/blob/master/articles/clustering/cluster-sharding.md) 
- [Streams](https://doc.akka.io/docs/akka/current/stream/index.html)
- [Networking](https://doc.akka.io/docs/akka/current/index-network.html)
- [Discovery](https://doc.akka.io/docs/akka/current/discovery/index.html)
- [Futures and Agents](https://doc.akka.io/docs/akka/current/index-futures.html)
  - [Futures](https://doc.akka.io/docs/akka/current/futures.html) 
  - [Agents](https://doc.akka.io/docs/akka/current/agents.html) 
- [工具](https://doc.akka.io/docs/akka/current/index-utilities.html)
- [其他 Akka 模块](https://doc.akka.io/docs/akka/current/common/other-modules.html)
  - [Akka HTTP](https://doc.akka.io/docs/akka-http/current/?language=java) 
  - [Alpakka](https://doc.akka.io/docs/alpakka/current/) 
  - [Alpakka Kafka Connector](http://doc.akka.io/docs/akka-stream-kafka/current/home.html) 
  - [Cassandra Plugins for Akka Persistence](https://github.com/akka/akka-persistence-cassandra) 
  - [Akka Management](http://developer.lightbend.com/docs/akka-management/current/) 
  - [Community Projects](https://doc.akka.io/docs/akka/current/common/other-modules.html) 
  - [Related Projects Sponsored by Lightbend](https://doc.akka.io/docs/akka/current/common/other-modules.html) 
    - [Play Framework](https://www.playframework.com) 
    - [Lagom](https://www.lagomframework.com) 
- [HowTo: Common Patterns](https://doc.akka.io/docs/akka/current/general/index.html)
- [项目信息](https://doc.akka.io/docs/akka/current/project/index.html)
- [附加信息](https://doc.akka.io/docs/akka/current/additional/index.html)



----------

**English Original Editon**: [Akka Documentation](https://doc.akka.io/docs/akka/current/index.html)

