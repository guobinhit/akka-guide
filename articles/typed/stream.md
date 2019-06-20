# 流
## 依赖

为了使用 Akka 流类型，你需要将以下依赖添加到你的项目中：

```xml
<!-- Maven -->
<dependency>
  <groupId>com.typesafe.akka</groupId>
  <artifactId>akka-stream-typed_2.12</artifactId>
  <version>2.5.23</version>
</dependency>

<!-- Gradle -->
dependencies {
  compile group: 'com.typesafe.akka', name: 'akka-stream-typed_2.12', version: '2.5.23'
}

<!-- sbt -->
libraryDependencies += "com.typesafe.akka" %% "akka-stream-typed" % "2.5.23"
```

## 简介

「[Akka Streams](https://doc.akka.io/docs/akka/current/stream/index.html)」使对类型安全的消息处理管道建模变得容易。对于类型化的 Actors，可以在不丢失类型信息的情况下将流连接到 Actors。

此模块包含现有`ActorRef`源的类型化替换，以及「[ActorMaterializerFactory](https://doc.akka.io/japi/akka/2.5/?akka/stream/typed/javadsl/ActorMaterializerFactory.html)」的工厂方法，后者采用类型化`ActorSystem`。

从这些工厂方法和源可以与来自原始模块的原始 Akka 流构建块混合和匹配。

- **注释**：此模块已准备好用于生产，但仍标记为「[可能更改](https://doc.akka.io/docs/akka/current/common/may-change.html)」。这意味着 API 或语义可以在没有警告的情况下进行更改，但这些更改将在 Akka 2.6.0 中收集并执行，而不是在 2.5.x 补丁版本中执行。

## Actor Source

发送到特定 Actor 的消息驱动的流可以使用「[ActorSource.actorRef](https://doc.akka.io/japi/akka/2.5/?akka/stream/typed/javadsl/ActorSource.html#actorRef)」启动。此源具体化为类型化的`ActorRef`，它只接受与流类型相同的消息。

```java
import akka.actor.typed.ActorRef;
import akka.japi.JavaPartialFunction;
import akka.stream.ActorMaterializer;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.typed.javadsl.ActorSource;

interface Protocol {}

class Message implements Protocol {
  private final String msg;

  public Message(String msg) {
    this.msg = msg;
  }
}

class Complete implements Protocol {}

class Fail implements Protocol {
  private final Exception ex;

  public Fail(Exception ex) {
    this.ex = ex;
  }
}

  final JavaPartialFunction<Protocol, Throwable> failureMatcher =
      new JavaPartialFunction<Protocol, Throwable>() {
        public Throwable apply(Protocol p, boolean isCheck) {
          if (p instanceof Fail) {
            return ((Fail) p).ex;
          } else {
            throw noMatch();
          }
        }
      };

  final Source<Protocol, ActorRef<Protocol>> source =
      ActorSource.actorRef(
          (m) -> m instanceof Complete, failureMatcher, 8, OverflowStrategy.fail());

  final ActorRef<Protocol> ref =
      source
          .collect(
              new JavaPartialFunction<Protocol, String>() {
                public String apply(Protocol p, boolean isCheck) {
                  if (p instanceof Message) {
                    return ((Message) p).msg;
                  } else {
                    throw noMatch();
                  }
                }
              })
          .to(Sink.foreach(System.out::println))
          .run(mat);

  ref.tell(new Message("msg1"));
  // ref.tell("msg2"); Does not compile
```

## Actor Sink

有两个`sink`可接受类型化的`ActorRef`。要将流中的所有消息发送给 Actor 而不考虑反压力（`backpressure`），请使用「[ActorSink.actorRef](https://doc.akka.io/japi/akka/2.5/?akka/stream/typed/javadsl/ActorSink.html#actorRef)」。


```java
import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.typed.javadsl.ActorSink;

interface Protocol {}

class Message implements Protocol {
  private final String msg;

  public Message(String msg) {
    this.msg = msg;
  }
}

class Complete implements Protocol {}

class Fail implements Protocol {
  private final Throwable ex;

  public Fail(Throwable ex) {
    this.ex = ex;
  }
}

  final ActorRef<Protocol> actor = null;

  final Sink<Protocol, NotUsed> sink = ActorSink.actorRef(actor, new Complete(), Fail::new);

  Source.<Protocol>single(new Message("msg1")).runWith(sink, mat);
```

为了使 Actor 能够对反压力作出反应，需要在 Actor 和流之间引入一个协议。使用「[ActorSink.actorRefWithAck](https://doc.akka.io/japi/akka/2.5/?akka/stream/typed/javadsl/ActorSink.html#actorRefWithAck)」可以在 Actor 准备接收更多元素时发出需求信号。

```java
import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.typed.javadsl.ActorSink;

class Ack {}

interface Protocol {}

class Init implements Protocol {
  private final ActorRef<Ack> ack;

  public Init(ActorRef<Ack> ack) {
    this.ack = ack;
  }
}

class Message implements Protocol {
  private final ActorRef<Ack> ackTo;
  private final String msg;

  public Message(ActorRef<Ack> ackTo, String msg) {
    this.ackTo = ackTo;
    this.msg = msg;
  }
}

class Complete implements Protocol {}

class Fail implements Protocol {
  private final Throwable ex;

  public Fail(Throwable ex) {
    this.ex = ex;
  }
}

  final ActorRef<Protocol> actor = null;

  final Sink<String, NotUsed> sink =
      ActorSink.actorRefWithAck(
          actor, Message::new, Init::new, new Ack(), new Complete(), Fail::new);

  Source.single("msg1").runWith(sink, mat);
```




----------

**英文原文链接**：[Streams](https://doc.akka.io/docs/akka/current/typed/stream.html).



----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————