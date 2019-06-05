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

在 Akka 中日志记录（`Logging`）不与特定的日志后端绑定。默认情况下，日志消息打印到 STDOUT，但你可以插入 SLF4J 记录器或自己的记录器。日志记录是异步执行的，以确保日志记录对性能的影响最小。日志记录通常意味着 IO 和锁，如果代码是同步执行的，这会减慢代码的操作速度。

## 如何记录日志

创建一个`LoggingAdapter`，并使用`error`、`warning`、`info`或`debug`方法，如本例所示：

```java
import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
```

```java
class MyActor extends AbstractActor {
  LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  @Override
  public void preStart() {
    log.debug("Starting");
  }

  @Override
  public void preRestart(Throwable reason, Optional<Object> message) {
    log.error(
        reason,
        "Restarting due to [{}] when processing [{}]",
        reason.getMessage(),
        message.isPresent() ? message.get() : "");
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .matchEquals("test", msg -> log.info("Received test"))
        .matchAny(msg -> log.warning("Received unknown message: {}", msg))
        .build();
  }
}
```

`Logging.getLogger`的第一个参数也可以是任何`LoggingBus`；在演示的情况下，Actor 系统的地址包含在日志源的`akkaSource`表示中（请参阅「[Logging Thread, Akka Source and Actor System in MDC](https://doc.akka.io/docs/akka/current/logging.html#logging-thread-akka-source-and-actor-system-in-mdc)」），而在第二种情况下，这不会自动完成。`Logging.getLogger`的第二个参数是此日志通道的源。源对象根据以下规则转换为字符串：

- 如果是 Actor 或`ActorRef`，则使用其路径
- 如果是`String`，则按原样使用
- 在类的情况下，为类的`simpleName`近似值
- 在所有其他情况下，为类的`simpleName`

日志消息可能包含参数占位符`{}`，如果启用了日志级别，则将替换它。提供比占位符更多的参数会导致将警告附加到日志语句（即，在具有相同严重性的同一行上）。可以将数组作为唯一的替换参数传递，以便单独处理其元素：

```java
final Object[] args = new Object[] {"The", "brown", "fox", "jumps", 42};
system.log().debug("five parameters: {}, {}, {}, {}, {}", args);
```

日志源的 Java `Class`也包含在生成的`LogEvent`中。如果是简单字符串，则用`marker`类`akka.event.DummyClassForStringSources`替换，以便对这种情况进行特殊处理，例如在 SLF4J 事件侦听器中，它将使用字符串而不是类的名称来查找要使用的记录器实例。

### 死信的记录

默认情况下，发送到死信（`dead letters`）的消息记录在`info`级别。死信的存在并不一定表示有问题，但为了谨慎起见，默认情况下会记录这些死信。几条消息后，此日志记录将关闭，以避免日志被淹没。你可以完全禁用此日志记录，或者调整记录的死信数。在系统关闭期间，很可能会看到死信，因为 Actor 邮箱中的挂起消息会发送到死信。你还可以在关机期间禁用死信记录。

```yml
akka {
  log-dead-letters = 10
  log-dead-letters-during-shutdown = on
}
```

要进一步自定义日志记录或对死信采取其他操作，可以订阅事件流。

### 辅助日志记录选项

Akka 有一些配置选项用于非常低级别的调试。这些在开发中比在生产中更有意义。

你几乎肯定需要将日志设置为`DEBUG`，才能使用以下任何选项：

```yml
akka {
  loglevel = "DEBUG"
}
```

如果你想知道 Akka 加载了哪些配置设置，则此配置选项非常好：

```yml
akka {
  # Log the complete configuration at INFO level when the actor system is started.
  # This is useful when you are uncertain of what configuration is used.
  log-config-on-start = on
}
```

如果你希望对 Actor 处理的所有自动接收的消息进行非常详细的日志记录：

```yml
akka {
  actor {
    debug {
      # enable DEBUG logging of all AutoReceiveMessages (Kill, PoisonPill etc.)
      autoreceive = on
    }
  }
}
```

如果你希望非常详细地记录 Actor 的所有生命周期更改（重启、死亡等）：

```yml
akka {
  actor {
    debug {
      # enable DEBUG logging of actor lifecycle changes
      lifecycle = on
    }
  }
}
```

如果希望在`DEBUG`时记录未处理的消息：

```yml
akka {
  actor {
    debug {
      # enable DEBUG logging of unhandled messages
      unhandled = on
    }
  }
}
```

如果你希望非常详细地记录扩展`LoggingFSM`的 FSM Actor 的所有事件、转换和计时器，请执行以下操作：

```yml
akka {
  actor {
    debug {
      # enable DEBUG logging of all LoggingFSMs for events, transitions and timers
      fsm = on
    }
  }
}
```

如果要监视`ActorSystem.eventStream`上的订阅消息（订阅/取消订阅）：

```yml
akka {
  actor {
    debug {
      # enable DEBUG logging of subscription changes on the eventStream
      event-stream = on
    }
  }
}
```

### 辅助远程日志记录选项

如果要查看在`DEBUG`日志级别通过远程处理发送的所有消息，请使用以下配置选项。请注意，这会记录由传输层而不是由 Actor 发送的消息。

```yml
akka {
  remote {
    # If this is "on", Akka will log all outbound messages at DEBUG level,
    # if off then they are not logged
    log-sent-messages = on
  }
}
```

如果要查看在`DEBUG`日志级别通过远程处理接收到的所有消息，请使用以下配置选项。请注意，这会记录由传输层而不是由 Actor 接收的消息。

```yml
akka {
  remote {
    # If this is "on", Akka will log all inbound messages at DEBUG level,
    # if off then they are not logged
    log-received-messages = on
  }
}
```

如果要在`INFO`日志级别查看有效负载大小（字节）大于指定限制的消息类型：

```yml
akka {
  remote {
    # Logging of message types with payload size in bytes larger than
    # this value. Maximum detected size per message type is logged once,
    # with an increase threshold of 10%.
    # By default this feature is turned off. Activate it by setting the property to
    # a value in bytes, such as 1000b. Note that for all messages larger than this
    # limit there will be extra performance and scalability cost.
    log-frame-size-exceeding = 1000b
  }
}
```

详见「[TestKit 的日志记录选项](https://doc.akka.io/docs/akka/current/testing.html#actor-logging)」。

### 关闭日志记录

要关闭日志记录，可以将日志级别配置为`OFF`。

```yml
akka {
  stdout-loglevel = "OFF"
  loglevel = "OFF"
}
```

`stdout-loglevel`仅在系统启动和关闭期间有效，并将其设置为`OFF`，确保在系统启动或关闭期间不会记录任何内容。

## 记录器

日志记录是通过事件总线异步执行的。日志事件由事件处理程序 Actor 处理，该 Actor 接收日志事件的顺序与发出日志事件的顺序相同。

- **注释**：事件处理程序 Actor 有一个无界的收件箱（`not have a bounded inbox`），并且在默认调度程序上运行。这意味着记录大量数据可能会严重影响应用程序。不过，通过使用异步日志后端可以稍微减轻这一点，具体请参见「[直接使用 SLF4J API](https://doc.akka.io/docs/akka/current/logging.html#slf4j-directly)」。

你可以配置在系统启动时创建哪些事件处理程序，并监听日志记录事件。这是使用「[配置](https://doc.akka.io/docs/akka/current/general/configuration.html)」中的`loggers`元素完成的。在这里，你还可以定义日志级别。基于日志源的更多细粒度筛选可以在自定义`LoggingFilter`中实现，该过滤器可以在`logging-filter`配置属性中定义。

```yml
akka {
  # Loggers to register at boot time (akka.event.Logging$DefaultLogger logs
  # to STDOUT)
  loggers = ["akka.event.Logging$DefaultLogger"]
  # Options: OFF, ERROR, WARNING, INFO, DEBUG
  loglevel = "DEBUG"
}
```

默认的日志输出到 STDOUT 并在默认情况下注册。不用于生产。`akka-slf4j`模块中还提供了一个 SLF4J 记录器。

创建监听器的示例：

```java
import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import akka.event.Logging.InitializeLogger;
import akka.event.Logging.Error;
import akka.event.Logging.Warning;
import akka.event.Logging.Info;
import akka.event.Logging.Debug;
```

```java
class MyEventListener extends AbstractActor {
  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            InitializeLogger.class,
            msg -> {
              getSender().tell(Logging.loggerInitialized(), getSelf());
            })
        .match(
            Error.class,
            msg -> {
              // ...
            })
        .match(
            Warning.class,
            msg -> {
              // ...
            })
        .match(
            Info.class,
            msg -> {
              // ...
            })
        .match(
            Debug.class,
            msg -> {
              // ...
            })
        .build();
  }
}
```

## 在启动和关闭期间记录到 STDOUT

当 Actor 系统启动和关闭时，不使用配置的记录器。相反，日志消息被打印到STDOUT（`System.out`）。此 STDOUT 记录器的默认日志级别为`WARNING`，可以通过设置`akka.stdout-loglevel=OFF`将其禁止。

## SLF4J

Akka 为「[SLF4J](https://www.slf4j.org/)」提供了一个记录器。这个模块可以在`akka-slf4j.jar`中找到，它只有一个依赖项`slf4j-api jar`。在运行时，你还需要一个 SLF4J 后端。我们建议使用「[Logback](https://logback.qos.ch/)」：

```xml
<!-- Maven -->
<dependency>
  <groupId>com.typesafe.akka</groupId>
  <artifactId>akka-slf4j_2.12</artifactId>
  <version>2.5.23</version>
</dependency>
<dependency>
  <groupId>ch.qos.logback</groupId>
  <artifactId>logback-classic</artifactId>
  <version>1.2.3</version>
</dependency>

<!-- Gradle -->
dependencies {
  compile group: 'com.typesafe.akka', name: 'akka-slf4j_2.12', version: '2.5.23',
  compile group: 'ch.qos.logback', name: 'logback-classic', version: '1.2.3'
}

<!-- sbt -->
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-slf4j" % "2.5.23",
  "ch.qos.logback" % "logback-classic" % "1.2.3"
)
```

你需要在「[配置](https://doc.akka.io/docs/akka/current/general/configuration.html)」的`loggers`元素中启用`Slf4jLogger`。在这里，你还可以定义事件总线的日志级别。可以在 SLF4J 后端的配置中定义更细粒度的日志级别（例如`logback.xml`）。你还应该在`logging-filter`配置属性中定义`akka.event.slf4j.Slf4jLoggingFilter`。它将使用后端配置（例如`logback.xml`）过滤日志事件，然后将其发布到事件总线。

- **警告**：如果将`loglevel`设置为比`DEBUG`更高的级别，则任何调试事件都将在源中被过滤掉，并且永远不会到达日志后端，无论后端是如何配置的。

```yml
akka {
  loggers = ["akka.event.slf4j.Slf4jLogger"]
  loglevel = "DEBUG"
  logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
}
```

其中一个问题是时间戳是在事件处理程序中属性化的，而不是在实际执行日志记录时。

为每个日志事件选择的 SLF4J 记录器是根据创建`LoggingAdapter`时指定的日志源的类来选择的，除非直接将其作为字符串提供，在这种情况下使用该字符串，即`LoggerFactory.getLogger(Class c)`在第一种情况下使用，而`LoggerFactory.getLogger(String s)`在第二种情况下使用。

- **注释**：注意，如果创建了`LoggingAdapter`，并将`ActorSystem`提供给工厂，那么 Actor 系统的名称将附加到`String`日志源。如果不打算这样做，请提供一个`LoggingBus`，如下所示：

```java
final LoggingAdapter log = Logging.getLogger(system.eventStream(), "my.string");
```

### 直接使用 SLF4J API

如果直接在应用程序中使用 SLF4J API，请记住，日志记录操作将在底层基础结构写入日志语句时阻塞。

这可以通过将日志记录实现配置为使用非阻塞附加器来避免。`Logback`提供了这样做的「[AsyncAppender](https://logback.qos.ch/manual/appenders.html#AsyncAppender)」。它还包含一个功能，如果日志负载很高，它将删除`INFO`和`DEBUG`消息。

### MDC 中的日志线程、Akka 源和 Actor 系统

由于日志记录是异步完成的，因此执行日志记录的线程将捕获在具有属性名`sourceThread`的映射诊断上下文（MDC）。对于`Logback`，模式布局配置中的`%X{sourceThread}`说明符可以使用线程名称：

```yml
<appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
  <encoder>
    <pattern>%date{ISO8601} %-5level %logger{36} %X{sourceThread} - %msg%n</pattern>
  </encoder>
</appender>
```

- **注释**：最好在应用程序的非 Akka 部分也使用`sourceThread` MDC 值，以便在日志中始终提供此属性。

另一个有用的工具是，Akka 在实例化其中的记录器时捕获 Actor 的地址，这意味着完整的实例标识可用于将日志消息（如与路由器成员关联）关联起来。此信息在属性名为`akkaSource`的 MDC 中可用：

```yml
<appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
  <encoder>
    <pattern>%date{ISO8601} %-5level %logger{36} %X{akkaSource} - %msg%n</pattern>
  </encoder>
</appender>
```

最后，执行日志记录的 Actor 系统在属性名为`sourceActorSystem`的 MDC 中可用：

```yml
<appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
  <encoder>
    <pattern>%date{ISO8601} %-5level %logger{36} %X{sourceActorSystem} - %msg%n</pattern>
  </encoder>
</appender>
```

有关此属性还包含哪些非 Actor 的详细信息，请参阅「[如何记录日志](#如何记录日志)」。

### 更精确的MDC日志输出时间戳

Akka 的日志记录是异步的，这意味着当调用底层的记录器实现时，日志条目的时间戳是从中获取的，这一点一开始可能令人惊讶。如果要更准确地输出时间戳，请使用 MDC 属性`akkaTimestamp`：

```yml
<appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
  <encoder>
    <pattern>%X{akkaTimestamp} %-5level %logger{36} %X{akkaSource} - %msg%n</pattern>
  </encoder>
</appender>
```

### 由应用程序定义的 MDC 值

SLF4J 中的一个有用功能是 MDC，Akka 可以让应用程序指定自定义值，为此，你需要使用专用的`LoggingAdapter`，即`DiagnosticLoggingAdapter`。为了获得它，你可以使用工厂，提供一个`AbstractActor`作为`logSource`：

```java
// Within your AbstractActor
final DiagnosticLoggingAdapter log = Logging.getLogger(this);
```

一旦你拥有了日志记录器，你就需要在记录某些内容之前添加自定义值。这样，在附加日志之前，这些值将被放入 SLF4J MDC 中，并在之后移除。

- **注释**：清理（删除）应该在 Actor 的末尾完成，否则，如果没有设置为新映射，则下一条消息将使用相同的 MDC 值记录。使用`log.clearMDC()`。

```java
import akka.event.DiagnosticLoggingAdapter;
import java.util.HashMap;
import java.util.Map;
```

```java
class MdcActor extends AbstractActor {

  final DiagnosticLoggingAdapter log = Logging.getLogger(this);

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .matchAny(
            msg -> {
              Map<String, Object> mdc;
              mdc = new HashMap<String, Object>();
              mdc.put("requestId", 1234);
              mdc.put("visitorId", 5678);
              log.setMDC(mdc);

              log.info("Starting new request");

              log.clearMDC();
            })
        .build();
  }
}
```

现在，这些值将在 MDC 中可用，因此你可以在布局模式中使用它们：

```yml
<appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
  <encoder>
    <pattern>
      %-5level %logger{36} [req: %X{requestId}, visitor: %X{visitorId}] - %msg%n
    </pattern>
  </encoder>
</appender>
```

### 使用标记

除了 MDC 数据之外，一些日志库还允许将所谓的标记（`markers`）附加到日志语句。这些用于过滤罕见和特殊事件，例如，你可能希望标记检测到某些恶意活动的日志，并用`SECURITY`标记对其进行标记，并且在你的附加器配置中，立即触发电子邮件和其他通知。

当通过`Logging.withMarker`获得标记时，也可以通过``LoggingAdapters``获得标记。传递给所有日志调用的第一个参数应该是`akka.event.LogMarker`。

Akka 在`akka-slf4j`中提供的`slf4j`桥将自动获取该标记值，并使其对 SLF4J 可用。例如，你可以这样使用它：

```yml
<pattern>%date{ISO8601} [%marker][%level] [%msg]%n</pattern>
```

更高级的（包括大多数 Akka 添加的信息）示例模式是：

```yml
<pattern>%date{ISO8601} level=[%level] marker=[%marker] logger=[%logger] akkaSource=[%X{akkaSource}] sourceActorSystem=[%X{sourceActorSystem}] sourceThread=[%X{sourceThread}] mdc=[ticket-#%X{ticketNumber}: %X{ticketDesc}] - msg=[%msg]%n----%n</pattern>
```

### 使用 SLF4J 的标记

使用 SLF4J 时，也可以将`org.slf4j.Marker`与`LoggingAdapter`一起使用。

由于`akka-actor`避免依赖于任何特定的日志记录库，因此对它的支持包括在`akka-slf4j`中，它提供了`Slf4jLogMarker`类型，可以作为第一个参数传递，而不是`akka-actor`传递日志框架不可知的日志标记类型。两者之间最显著的区别是，SLF4J 的标记可以有子标记，因此可以使用子标记来依赖更多的信息，而不仅仅是一个字符串。

## java.util.logging

Akka 包含一个「[java.util.logging](https://docs.oracle.com/javase/8/docs/api/java/util/logging/package-summary.html#package.description)」的记录器。

你需要在配置的`loggers`元素中启用`akka.event.jul.JavaLogger`。在这里，你还可以定义事件总线的日志级别。可以在日志后端的配置中定义更细粒度的日志级别。你还应该在`logging-filter`配置属性中定义`akka.event.jul.JavaLoggingFilter`。它将在日志事件发布到事件总线之前使用后端配置过滤日志事件。

- **警告**：如果将`loglevel`设置为比`DEBUG`更高的级别，则任何调试事件都将在源中被过滤掉，并且永远不会到达日志后端，无论后端是如何配置的。

```yml
akka {
  loglevel = DEBUG
  loggers = ["akka.event.jul.JavaLogger"]
  logging-filter = "akka.event.jul.JavaLoggingFilter"
}
```

其中一个问题是时间戳是在事件处理程序中属性化的，而不是在实际执行日志记录时。

为每个日志事件选择的` java.util.logging.Logger`是根据创建`LoggingAdapter`时指定的日志源的类来选择的，除非它直接作为字符串提供，在这种情况下使用该字符串，即在第一种情况下使用`LoggerFactory.getLogger(Class c)`，在第二种情况下使用`LoggerFactory.getLogger(String s)`。

- **注释**：注意，如果创建了`LoggingAdapter`，并将`ActorSystem`提供给工厂，那么 Actor 系统的名称将附加到`String`日志源。如果不打算这样做，请提供一个`LoggingBus`，如下所示：

```java
final LoggingAdapter log = Logging.getLogger(system.eventStream(), "my.string");
```


----------

**英文原文链接**：[Logging](https://doc.akka.io/docs/akka/current/logging.html).



----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————