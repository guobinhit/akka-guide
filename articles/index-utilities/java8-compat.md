# Java 8 兼容性

Akka 要求在你的机器上安装 Java 8 或更高版本。

从 Akka 2.4.2 开始，我们已经开始引入 Java 8 类型（最突出的是`java.util.concurrent.CompletionStage`和`java.util.Optional`），在不中断二进制或源兼容性的情况下，这是可能的。如果这是不可能的（例如，在返回类型的`ActorSystem.terminate()`中），请参阅`scala-java8-compat`库，它允许 Scala 和 Java 对等体之间的轻松转换。Maven 构建中可以使用以下方法包含构件：

```xml
<dependency>
  <groupId>org.scala-lang.modules</groupId>
  <artifactId>scala-java8-compat_2.11</artifactId>
  <version>0.7.0</version>
</dependency>
```

只要我们能够依赖 Scala 2.12 提供完全互操作性，我们就能够无缝地集成所有功能接口，这意味着 Scala 用户可以使用`lambda`语法直接实现 Java 功能接口，以及 Java 用户可以使用`lambda`语法直接实现 Scala 函数。




----------

**英文原文链接**：[Java 8 Compatibility](https://doc.akka.io/docs/akka/current/java8-compat.html).


----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————