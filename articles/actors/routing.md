# 路由
## 依赖

为了使用路由（`Routing`），你需要将以下依赖添加到你的项目中：

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
Akka 的邮箱中保存着发给 Actor 的信息。通常，每个 Actor 都有自己的邮箱，但也有例外，如使用`BalancingPool`，则所有路由器（`routees`）将共享一个邮箱实例。

## 邮箱选择
### 指定 Actor 的消息队列类型


----------

**英文原文链接**：[Routing](https://doc.akka.io/docs/akka/current/routing.html).


----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————