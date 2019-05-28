# 发现

Akka 发现（`Discovery`）提供了一个围绕各种定位服务方式的接口。内置方法有：

- DNS
- 配置
- 聚合

此外，「[Akka Management](https://developer.lightbend.com/docs/akka-management/current/)」包括以下方法：

- Kubernetes API
- AWS
- Consul
- Marathon API

`Discovery`曾是 Akka 管理的一部分，但从 Akka 的`2.5.19`版和 Akka 管理的`1.0.0`版开始，它已成为 Akka 的模块。如果你还将 Akka 管理用于其他服务发现方法或加载程序，请确保你至少使用了 Akka 管理（`Management`）的`1.0.0`版本。

## 依赖

为了使用 Akka 发现，你需要将以下依赖添加到你的项目中：

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

`Lookup`包含一个必需的`serviceName`和一个可选的`portName`和`protocol`。如何解释这些内容取决于发现方法，例如，如果缺少任何字段，DNS 会执行 A/AAAA 记录查询，并执行 SRV 查询以进行完整查找：

```java
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

当服务打开多个端口（如 HTTP 端口和 Akka 远程处理端口）时，可以使用`port`。

## 发现方法：DNS

DNS 发现映射`Lookup`查询如下：

- `serviceName`、`portName`和`protocol`：SRV 查询，格式为`_port._protocol._name`，其中`_`是需要添加的名称。
- 任何缺少任何字段的查询都将映射到`serviceName`的 A/AAAA 查询。

Akka 服务发现术语和 SRV 术语之间的映射：

- `SRV service = port`
- `SRV name = serviceName`
- `SRV protocol = protocol`

在`application.conf`中将`akka-dns`配置为发现的实现：

```yml
akka {
  discovery {
   method = akka-dns
  }
}
```

从那时起，你就可以使用通用 API 来隐藏调用以下方法所使用的发现方法的事实：

```java
import akka.discovery.ServiceDiscovery;
ActorSystem system = ActorSystem.create("Example");
// ...
SimpleServiceDiscovery discovery = ServiceDiscovery.get(system).discovery();
Future<SimpleServiceDiscovery.Resolved> result = discovery.lookup("service-name", Duration.create("500 millis"));
```

### 它是如何工作的？

根据发出的是`Simple`查找还是`Full`查找，DNS 发现将使用 A/AAAA 记录或 SRV 记录。SRV 记录的优点是它们可以包含一个端口。

### SRV 记录

设置了所有字段的查找将成为 SRV 查询。例如：

```yml
dig srv _service._tcp.akka.test

; <<>> DiG 9.11.3-RedHat-9.11.3-6.fc28 <<>> srv service.tcp.akka.test
;; global options: +cmd
;; Got answer:
;; ->>HEADER<<- opcode: QUERY, status: NOERROR, id: 60023
;; flags: qr aa rd ra; QUERY: 1, ANSWER: 2, AUTHORITY: 1, ADDITIONAL: 5

;; OPT PSEUDOSECTION:
; EDNS: version: 0, flags:; udp: 4096
; COOKIE: 5ab8dd4622e632f6190f54de5b28bb8fb1b930a5333c3862 (good)
;; QUESTION SECTION:
;service.tcp.akka.test.         IN      SRV

;; ANSWER SECTION:
_service._tcp.akka.test.  86400   IN      SRV     10 60 5060 a-single.akka.test.
_service._tcp.akka.test.  86400   IN      SRV     10 40 5070 a-double.akka.test.
```

在这种情况下，`service.tcp.akka.test`解析为端口`5060`上的`a-double.akka.test`和端口`5070`上的`a-double.akka.test `。当前发现不支持权重。

### A/AAAA 记录

缺少任何字段的查找将成为 A/AAAA 记录查询。例如：

```yml
dig a-double.akka.test

; <<>> DiG 9.11.3-RedHat-9.11.3-6.fc28 <<>> a-double.akka.test
;; global options: +cmd
;; Got answer:
;; ->>HEADER<<- opcode: QUERY, status: NOERROR, id: 11983
;; flags: qr aa rd ra; QUERY: 1, ANSWER: 2, AUTHORITY: 1, ADDITIONAL: 2

;; OPT PSEUDOSECTION:
; EDNS: version: 0, flags:; udp: 4096
; COOKIE: 16e9815d9ca2514d2f3879265b28bad05ff7b4a82721edd0 (good)
;; QUESTION SECTION:
;a-double.akka.test.            IN      A

;; ANSWER SECTION:
a-double.akka.test.     86400   IN      A       192.168.1.21
a-double.akka.test.     86400   IN      A       192.168.1.22
```

在这种情况下，`a-double.akka.test`将解析为`192.168.1.21`和`192.168.1.22`。

## 发现方法：配置

配置（`Configuration`）当前忽略服务名称以外的所有字段。

对于简单的用例，可以使用配置来发现服务。将 Akka Discovery 与配置一起使用而不是与你自己的配置值一起使用的好处是，可以将应用程序迁移到更复杂的发现方法，而无需更改任何代码。

在`application.conf`中将其配置为发现方法：

```yml
akka {
  discovery.method = config
}
```

默认情况下，可发现的服务在`akka.discovery.config.services`中定义，格式如下：

```yml
akka.discovery.config.services = {
  service1 = {
    endpoints = [
      {
        host = "cat"
        port = 1233
      },
      {
        host = "dog"
        port = 1234
      }
    ]
  },
  service2 = {
    endpoints = []
  }
}
```

上面的块定义了两个服务，`service1`和`service2`。每个服务可以有多个`endpoints`。

## 发现方法：聚合多个发现方法

聚合（`Aggregate`）发现允许聚合多个发现方法，例如，尝试通过 DNS 解析并返回到配置。

若要使用聚合发现，请添加其依赖项以及要聚合的所有发现。

将`aggregate`配置为`akka.discovery.method`，并尝试使用哪些发现方法，以及按何种顺序执行。

```yml
akka {
  discovery {
    method = aggregate
    aggregate {
      discovery-methods = ["akka-dns", "config"]
    }
    config {
      services {
        service1 {
          endpoints [
            {
              host = "host1"
              port = 1233
            },
            {
              host = "host2"
              port = 1234
            }
          ]
        }
      }
    }
  }
}
```

上述配置将导致首先检查`akka-dns`，如果它失败或没有返回给定服务名称的目标，那么将查询`config`，如上配置了一个名为`service1`的服务，其中两个主机为`host1`和`host2`。

## 从 Akka Management Discovery 迁移

Akka Discovery 与旧版本的 Akka Management Discovery 不兼容。如果还使用 Akka Discovery，则应至少使用任何 Akka Management 模块的`1.0.0`版本。

迁移步骤：
 - 任何自定义发现方法现在都应实现`akka.discovery.ServiceDiscovery`
 - `discovery-method`现在必须是`akka.discovery`下的一个配置位置，至少要有一个属性`class`，指定`akka.discovery.ServiceDiscovery`实现的完全限定名。以前的版本允许它是类名或完全限定的配置位置，例如`akka.discovery.kubernetes-api`，而不仅仅是`kubernetes-api`。



----------

**英文原文链接**：[Discovery](https://doc.akka.io/docs/akka/current/discovery/index.html).


----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————