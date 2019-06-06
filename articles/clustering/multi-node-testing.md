# 多节点测试
## 依赖

为了使用多节点测试（`Multi Node Testing`），你需要将以下依赖添加到你的项目中：

```xml
<!-- Maven -->
<dependency>
  <groupId>com.typesafe.akka</groupId>
  <artifactId>akka-multi-node-testkit_2.12</artifactId>
  <version>2.5.22</version>
</dependency>

<!-- Gradle -->
dependencies {
  compile group: 'com.typesafe.akka', name: 'akka-multi-node-testkit_2.12', version: '2.5.22'
}

<!-- sbt -->
libraryDependencies += "com.typesafe.akka" %% "akka-multi-node-testkit" % "2.5.22"
```

## 示例项目

你可以查看「[多节点示例](https://developer.lightbend.com/start/?group=akka&project=akka-samples-multi-node-scala)」项目，以了解实践中的情况。

## 多节点测试概念

当我们讨论 Akka 中的多节点测试时，我们指的是在不同的 JVM 中的多个 Actor 系统上运行协调测试的过程。多节点测试套件由三个主要部分组成。

- [Test Conductor](https://doc.akka.io/docs/akka/current/multi-node-testing.html#the-test-conductor)，它协调和控制被测节点。
- [Multi Node Spec](https://doc.akka.io/docs/akka/current/multi-node-testing.html#the-multi-node-spec)，它是一个方便的包装器，用于启动`TestConductor`并让所有节点连接到它。
- [SbtMultiJvm 插件](https://doc.akka.io/docs/akka/current/multi-node-testing.html#the-sbtmultijvm-plugin)，它可以在多个 JVM 中（可能在多台机器上）启动测试。

## Test Conductor

多节点测试的基础是`TestConductor`。它是一个插入网络堆栈的 Akka 扩展，用于协调参与测试的节点，并提供以下几个功能：

- 节点地址查找：查找到另一个测试节点的完整路径（不需要在测试节点之间共享配置）
- 节点屏障协调：在指定屏障处等待其他节点。
- 网络故障注入：限制流量、丢弃数据包、拔出和重新插入节点。

下面是`TestConductor`的示意图概述：

![test-conductor-sc](https://github.com/guobinhit/akka-guide/blob/master/images/clustering/multi-node-testing/test-conductor-sc.png)

`TestConductor`服务器负责协调屏障并向`TestConductor`客户端发送命令，例如限制与其他客户端之间的网络流量。有关可能操作的更多信息，请参阅`akka.remote.testconductor.Conductor` API文档。

## Multi Node Spec

`Multi Node Spec`由两部分组成。`MultiNodeConfig`负责公共配置、枚举和命名测试节点；`MultiNodeSpec`包含许多方便函数，用于使测试节点相互作用。有关可能操作的更多信息，请参阅`akka.remote.testkit.MultiNodeSpec` API文档。

`MultiNodeSpec`的设置是通过 Java 系统属性来配置的，Java 系统属性设置在所有要运行测试节点的 JVM 上。可以在 JVM 命令行上设置`-Dproperty=value`。

这些是可用的属性：

- `multinode.max-nodes`，测试可以拥有的最大节点数。
- `multinode.host`，此节点的主机名或 IP，必须可以使用`InetAddress.getByName`解析。
- `multinode.port`，此节点的端口号，默认为`0`，将使用随机端口。
- `multinode.server-host`，服务器节点的主机名或 IP，必须可以使用`InetAddress.getByName`解析。
- `multinode.server-port`，服务器节点的端口号，默认为`4711`。
- `multinode.index`，按照为测试定义的角色序列对此节点的索引，索引`0`是特殊的，该计算机将是服务器，所有故障注入和节流都必须从此节点完成。

## SbtMultiJvm 插件

`SbtMultiJvm`插件已经更新，可以通过自动生成相关的`multinode.*`属性来运行多节点测试。这意味着你可以在一台机器上运行多节点测试，而无需任何特殊配置，方法是将它们作为普通的多 JVM 测试运行。然后，通过使用插件中的多节点添加，可以在多台计算机上运行这些测试，而无需进行任何更改。

### 多节点特定添加

该插件还具有许多新的`multi-node-*` sbt 任务和设置，以支持在多台计算机上运行测试。将必要的测试类和依赖项打包，以便通过「[SbtAssembly](https://github.com/sbt/sbt-assembly)」分发到一个`jar`文件中，该文件的格式为`<projectName>_<scalaVersion>-<projectVersion>-multi-jvm-assembly.jar`。

- **注释**：为了能够在多台机器上分发和启动测试，假设主机和目标系统都是具有`ssh`和`rsync`可用性的类似 POSIX 的系统。

这些是可用的`sbt`多节点设置：

- `multiNodeHosts`，提供一个用于运行测试的主机序列，在表单`user@host:java`中，`host`是唯一需要的部分。将覆盖文件中的设置。
- `multiNodeHostsFileName`，用于在主机中读取以用于运行测试的文件。每行一个，格式同上。默认为基本项目目录中的`multi-node-test.hosts`。
- `multiNodeTargetDirName`，为目标计算机上的目录命名，在其中复制`jar`文件。默认为`ssh`用户的基本目录中用于`rsync` jar 文件的`multi-node-test`。
- `multiNodeJavaName`，目标机器上的默认 Java 可执行文件的名称，默认为`java`。

下面是一些如何定义主机的示例：

- `localhost`，使用默认`java`在本地主机上定位当前用户。
- `user1@host1`，用户`user1`在主机`host1`使用默认`java`。
- `user2@host2:/usr/lib/jvm/java-7-openjdk-amd64/bin/java`，用户`user2`在主机`host2`上使用`java 7`。
- `host3:/usr/lib/jvm/java-6-openjdk-amd64/bin/java`，使用`java 6`在主机`host3`上的当前用户。

## 运行多节点测试

要在`sbt`内部以多节点模式（即分发`jar`文件并远程启动测试）运行所有多节点测试，请使用`multiNodeTest`任务：

```java
multiNodeTest
```

要在多 JVM 模式（即本地计算机上的所有 JVM）下运行所有的 JVM，请执行以下操作：

```java
multi-jvm:test
```

要运行单个测试，请使用`multiNodeTestOnly`任务：

```java
multiNodeTestOnly your.MultiNodeTest
```

要在多 JVM 模式下运行单个测试，请执行以下操作：

```java
multi-jvm:testOnly your.MultiNodeTest
```

可以列出多个测试名称以运行多个特定测试。`sbt`中的制表符使完成测试名称变得容易。

## 多节点测试示例

首先，我们需要一些脚手架（`scaffolding`）来将`MultiNodeSpec`与你最喜欢的测试框架连接起来。让我们定义一个使用`ScalaTest`启动和停止`MultiNodeSpec`的特征`STMultiNodeSpec`。

```java
package akka.remote.testkit

import scala.language.implicitConversions

import org.scalatest.{ BeforeAndAfterAll, WordSpecLike }
import org.scalatest.Matchers

/**
 * Hooks up MultiNodeSpec with ScalaTest
 */
trait STMultiNodeSpec extends MultiNodeSpecCallbacks with WordSpecLike with Matchers with BeforeAndAfterAll {
  self: MultiNodeSpec =>

  override def beforeAll() = multiNodeSpecBeforeAll()

  override def afterAll() = multiNodeSpecAfterAll()

  // Might not be needed anymore if we find a nice way to tag all logging from a node
  override implicit def convertToWordSpecStringWrapper(s: String): WordSpecStringWrapper =
    new WordSpecStringWrapper(s"$s (on node '${self.myself.name}', $getClass)")
}
```

然后我们需要定义一个配置。让我们使用两个节点`"node1"`和`"node2"`，并将其称为`MultiNodeSampleConfig`。

```java
package akka.remote.sample

import akka.remote.testkit.{ MultiNodeConfig, STMultiNodeSpec }

object MultiNodeSampleConfig extends MultiNodeConfig {
  val node1 = role("node1")
  val node2 = role("node2")
}
```

最后到节点测试代码。它启动两个节点，并演示一个屏障和一个远程 Actor 消息发送/接收。

```java
package akka.remote.sample

import akka.actor.{ Actor, Props }
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender

class MultiNodeSampleSpecMultiJvmNode1 extends MultiNodeSample
class MultiNodeSampleSpecMultiJvmNode2 extends MultiNodeSample

object MultiNodeSample {
  class Ponger extends Actor {
    def receive = {
      case "ping" => sender() ! "pong"
    }
  }
}

class MultiNodeSample extends MultiNodeSpec(MultiNodeSampleConfig) with STMultiNodeSpec with ImplicitSender {

  import MultiNodeSample._
  import MultiNodeSampleConfig._

  def initialParticipants = roles.size

  "A MultiNodeSample" must {

    "wait for all nodes to enter a barrier" in {
      enterBarrier("startup")
    }

    "send to and receive from a remote node" in {
      runOn(node1) {
        enterBarrier("deployed")
        val ponger = system.actorSelection(node(node2) / "user" / "ponger")
        ponger ! "ping"
        import scala.concurrent.duration._
        expectMsg(10.seconds, "pong")
      }

      runOn(node2) {
        system.actorOf(Props[Ponger], "ponger")
        enterBarrier("deployed")
      }

      enterBarrier("finished")
    }
  }
}
```

自己运行这个示例的最简单方法是下载准备好的「[Akka Multi-Node Testing Sample with Scala](https://example.lightbend.com/v1/download/akka-samples-multi-node-scala)」以及教程，此示例的源代码可以在「[Akka Samples Repository](https://developer.lightbend.com/start/?group=akka&project=akka-sample-multi-node-scala)」中找到。

## 要记住的事情

在编写多节点测试时，需要记住一些事情，否则你的测试可能会以令人惊讶的方式运行。

- 不要关闭第一个节点。第一个节点是控制器，如果它关闭，测试将中断。
- 要使用`blackhole`、`passThrough`和`throttle`，你必须通过在`MultiNodeConfig`中指定`testTransport(on = true)`来激活故障注入器和节流器传输适配器。
- 节流、关闭和其他故障注入只能从第一个节点完成，这也是控制器。
- 关闭节点后，不要使用`node(address)`请求节点的地址。在关闭节点之前获取地址。
- 不要使用地址查找、屏障入口等来自主测试线程以外的其他线程的`MultiNodeSpec`方法。这也意味着你不应该在 Actor、`future`或`scheduled`的任务中使用它们。

## 配置

多节点测试模块有几个配置属性，请参考「[配置](https://doc.akka.io/docs/akka/current/general/configuration.html#config-akka-multi-node-testkit)」。



----------

**英文原文链接**：[Multi Node Testing](https://doc.akka.io/docs/akka/current/multi-node-testing.html).



----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————