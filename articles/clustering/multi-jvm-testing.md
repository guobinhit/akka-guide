# 多虚拟机测试

支持同时在多个 JVM 中运行应用程序（具有`main`方法的对象）和`ScalaTest`测试。对于多个系统相互通信的集成测试很有用。

## 安装程序

多 JVM 测试是一个`sbt`插件，可以在 https://github.com/sbt/sbt-multi-jvm 中找到。要在项目中配置它，应执行以下步骤：

1. 将它作为插件添加到你的`project/plugins.sbt`中：

```java
addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm" % "0.4.0")
```

2. 通过启用`MultiJvmPlugin`并设置`MultiJvm`配置，将多 JVM 测试添加到`build.sbt `或`project/Build.scala`。

```java
lazy val root = (project in file("."))
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)
```

请注意，默认情况下，`MultiJvm`测试源位于`src/multi-jvm/...`，而不是位于`src/test/....`。

下面是一个使用`sbt-multi-jvm`插件的「[示例项目](https://developer.lightbend.com/start/?group=akka&project=akka-sample-multi-node-scala)」。

## 运行测试

多 JVM 任务类似于常规任务：`test`、`testOnly`和`run`，但在多 JVM 配置下。

因此，在 Akka 中，要运行`akka-remote`项目使用中的所有多 JVM 测试（在`sbt`提示下）：

```java
akka-remote-tests/multi-jvm:test
```

或者可以先切换到`akka-remote-tests`项目，然后运行测试：

```java
project akka-remote-tests
multi-jvm:test
```

要运行单个测试，请使用`testOnly`：

```java
multi-jvm:testOnly akka.remote.RandomRoutedRemoteActor
```

可以列出多个测试名称以运行多个特定测试。`sbt`中的制表符使完成测试名称变得容易。

也可以使用`testOnly`指定 JVM 选项，方法是在测试名称和`--`之后包含这些选项。例如：

```java
multi-jvm:testOnly akka.remote.RandomRoutedRemoteActor -- -Dsome.option=something
```

## 创建应用程序测试

通过命名约定，可以发现并组合这些测试。`MultiJvm`测试源位于`src/multi-jvm/...`。使用以下模式命名测试：

```java
{TestName}MultiJvm{NodeName}
```

也就是说，每个测试的名称中间都有`MultiJvm`。前面的部分将测试/应用程序分组在一起，使用一个将一起运行的`TestName`。后面的部分`NodeName`是每个分叉的 JVM 的一个区别名称。

因此，要创建一个名为`Sample`的 3 节点测试，可以创建三个应用程序，如下所示：

```java
package sample

object SampleMultiJvmNode1 {
  def main(args: Array[String]) {
    println("Hello from node 1")
  }
}

object SampleMultiJvmNode2 {
  def main(args: Array[String]) {
    println("Hello from node 2")
  }
}

object SampleMultiJvmNode3 {
  def main(args: Array[String]) {
    println("Hello from node 3")
  }
}
```

当你在`sbt`提示下调用`multi-jvm:run sample.Sample`时，将生成三个 JVM，每个节点一个。它看起来像这样：

```java
> multi-jvm:run sample.Sample
...
[info] * sample.Sample
[JVM-1] Hello from node 1
[JVM-2] Hello from node 2
[JVM-3] Hello from node 3
[success] Total time: ...
```

## 更改默认值

你可以为分叉（`forked`）的 JVM 指定 JVM 选项：

```java
jvmOptions in MultiJvm := Seq("-Xmx256M")
```

你可以通过向项目中添加以下配置来更改多 JVM 测试源目录的名称：

```java
unmanagedSourceDirectories in MultiJvm :=
   Seq(baseDirectory(_ / "src/some_directory_here")).join.value
```

你可以更改`MultiJvm`标识符。例如，要将其更改为`ClusterTest`，请使用`multiJvmMarker`设置：

```java
multiJvmMarker in MultiJvm := "ClusterTest"
```

你的测试现在应该命名为`{TestName}ClusterTest{NodeName}`。

## JVM 实例的配置

你可以为每个生成的 JVM 定义特定的 JVM 选项。通过在测试中创建一个后缀为`.opts`的文件，并将其放在与测试相同的目录中，可以做到这一点。

例如，要将 JVM 选项`-Dakka.remote.port=9991`和`-Xmx256m`送到`SampleMultiJvmNode1`，让我们创建三个`*.opts`文件并将这些选项添加到它们中。用空格分隔多个选项。

- `SampleMultiJvmNode1.opts`：

```java
-Dakka.remote.port=9991 -Xmx256m
```

- `SampleMultiJvmNode2.opts`：

```java
-Dakka.remote.port=9992 -Xmx256m
```

- `SampleMultiJvmNode3.opts`：

```java
-Dakka.remote.port=9993 -Xmx256m
```

## ScalaTest

还支持创建`ScalaTest`测试，而不是应用程序。要做到这一点，请使用与上面相同的命名约定，但要使用`main`方法创建`ScalaTest`套件，而不是对象。你需要在`classpath`上有`ScalaTest`。这里有一个类似于上面的例子，但是使用`ScalaTest`：

```java
package sample

import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers

class SpecMultiJvmNode1 extends WordSpec with MustMatchers {
  "A node" should {
    "be able to say hello" in {
      val message = "Hello from node 1"
      message must be("Hello from node 1")
    }
  }
}

class SpecMultiJvmNode2 extends WordSpec with MustMatchers {
  "A node" should {
    "be able to say hello" in {
      val message = "Hello from node 2"
      message must be("Hello from node 2")
    }
  }
}
```

为了运行这些测试，你可以在`sbt`提示下调用`multi-jvm:testOnly sample.Spec`。

## 多节点添加

还对`SbtMultiJvm`插件进行了一些添加，以适应「[可能更改](https://doc.akka.io/docs/akka/current/common/may-change.html)」模块的「[多节点测试](https://doc.akka.io/docs/akka/current/multi-node-testing.html)」，如该部分所述。



----------

**英文原文链接**：[Multi JVM Testing](https://doc.akka.io/docs/akka/current/multi-jvm-testing.html).


----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————