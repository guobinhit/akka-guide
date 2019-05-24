# 发现

Akka Discovery 提供了一个围绕各种定位服务方式的接口。内置方法有：

- Configuration
- DNS
- Aggregate

此外，「[Akka Management](https://developer.lightbend.com/docs/akka-management/current/)」包括以下方法：

- Kubernetes API
- AWS
- Consul
- Marathon API

`Discovery`曾是 Akka 管理的一部分，但从 Akka 的`2.5.19`版和 Akka 管理的`1.0.0`版开始，它已成为 Akka 的模块。如果你还将 Akka 管理用于其他服务发现方法或加载程序，请确保你至少使用了 Akka 管理（`Management`）的`1.0.0`版本。

## 依赖

为了使用 Akka Discovery，你需要将以下依赖添加到你的项目中：

```xml
<!-- Maven -->
<dependency>
  <groupId>com.typesafe.akka</groupId>
  <artifactId>akka-discovery_2.12</artifactId>
  <version>2.5.22</version>
</dependency>

<!-- Gradle -->
dependencies {
  compile group: 'com.typesafe.akka', name: 'akka-discovery_2.12', version: '2.5.22'
}

<!-- sbt -->
libraryDependencies += "com.typesafe.akka" %% "akka-discovery" % "2.5.22"
```

## 它是如何工作的？

加载扩展：

```java
ActorSystem as = ActorSystem.create();
ServiceDiscovery serviceDiscovery = Discovery.get(as).discovery();
```

`Lookup`包含一个必需的`serviceName`和一个可选的`portName`和`protocol`。如何解释这些内容取决于发现方法，例如，如果缺少任何字段，`DNS`会执行`A/AAAA`记录查询，并执行`SRV`查询以进行完整查找：

```
serviceDiscovery.lookup(Lookup.create("akka.io"), Duration.ofSeconds(1));
// convenience for a Lookup with only a serviceName
serviceDiscovery.lookup("akka.io", Duration.ofSeconds(1));
```

`portName`和`protocol`是可选的，其含义由方法解释。

```java
CompletionStage<ServiceDiscovery.Resolved> lookup =
    serviceDiscovery.lookup(
        Lookup.create("akka.io").withPortName("remoting").withProtocol("tcp"),
        Duration.ofSeconds(1));
```

当服务打开多个端口（如 HTTP 端口和 Akka 远程处理端口）时，可以使用端口。


----------

**英文原文链接**：[Discovery](https://doc.akka.io/docs/akka/current/discovery/index.html).

----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————