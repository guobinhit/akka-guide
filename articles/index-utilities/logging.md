# 日志记录
## 依赖
要使用日志记录（`Logging`），你必须至少在项目中使用 Akka Actors 依赖项，并且很可能希望通过 SLF4J 模块配置日志记录，或者使用`java.util.logging`。

```xml
<!-- Maven -->
<dependency>
  <groupId>com.typesafe.akka</groupId>
  <artifactId>akka-actor_2.12</artifactId>
  <version>2.5.23</version>
</dependency>

<!-- Gradle -->
dependencies {
  compile group: 'com.typesafe.akka', name: 'akka-actor_2.12', version: '2.5.23'
}

<!-- sbt -->
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.5.23"
```

## 简介








----------

**英文原文链接**：[Logging](https://doc.akka.io/docs/akka/current/logging.html).


----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————