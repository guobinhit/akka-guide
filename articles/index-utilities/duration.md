# 持续时间

持续时间（`Duration`）在整个 Akka 库中使用，因此这个概念由一个特殊的数据类型`scala.concurrent.duration.Duration`表示。此类型的值可以表示无限（`Duration.Inf`、`Duration.MinusInf`）或有限的持续时间，也可以表示未定义的持续时间（`Duration.Undefined`）。

## 依赖

为了使用`Duration`，你需要将以下依赖添加到你的项目中：

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

## 有限 vs. 无限

由于试图将无限的时间转换为具体的时间单位（如秒）会引发异常，因此在编译时可以使用不同的类型来区分这两种类型：

- `FiniteDuration`保证是有限的，调用`toNanos`是安全的。
- `Duration`可以是有限的，也可以是无限的，所以这种类型应该只在有限性不重要时使用；这是`FiniteDuration`的一种超类型。

## Scala

在 Scala 中，可以使用`mini-DSL`构造持续时间，并支持所有预期的算术运算：

```java
import scala.concurrent.duration._

val fivesec = 5.seconds
val threemillis = 3.millis
val diff = fivesec - threemillis
assert(diff < fivesec)
val fourmillis = threemillis * 4 / 3 // you cannot write it the other way around
val n = threemillis / (1 millisecond)
```

- **注释**：如果表达式被清晰地分隔（例如括号内或参数列表中），则可以省略点，但如果时间单位是一行中的最后一个标记，则建议使用点，否则，根据下一行的起始内容，分号推理可能会出错。

## Java

Java 提供较少的语法糖，因此你必须将操作拼写为方法调用，而不是：

```java
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.Deadline;
```

```java
final Duration fivesec = Duration.create(5, "seconds");
final Duration threemillis = Duration.create("3 millis");
final Duration diff = fivesec.minus(threemillis);
assert diff.lt(fivesec);
assert Duration.Zero().lt(Duration.Inf());
```

## 最后期限

`Durations`有一个兄弟名为`Deadline`，它是一个类，它持有绝对时间点的表示，并且支持通过计算现在和最后期限（`deadline`）之间的差异来派生一个持续时间。如果你想保持一个完整的最后期限，而不必注意簿记（`book-keeping`）工作，这是有用的。时间的流逝可以表示为：

```java
val deadline = 10.seconds.fromNow
// do something
val rest = deadline.timeLeft
```

在 Java 中，利用持续时间创建这些：

```java
final Deadline deadline = Duration.create(10, "seconds").fromNow();
final Duration rest = deadline.timeLeft();
```




----------

**英文原文链接**：[Duration](https://doc.akka.io/docs/akka/current/common/duration.html).


----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————