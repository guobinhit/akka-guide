# Akka 扩展

如果你想向 Akka 添加特性，有一个非常优雅但功能强大的机制来实现这一点。它被称为 Akka 扩展（`Extensions`），由两个基本组件组成：`Extension`和`ExtensionId`。

每个`ActorSystem`只加载一次扩展，由 Akka 管理。你可以选择按需加载扩展，也可以通过 Akka 配置在`ActorSystem`创建时加载扩展。有关如何实现这一点的详细信息，请参阅下面的“从配置中加载”部分。

- **警告**：由于扩展是一种钩住 Akka 本身的方法，因此扩展的实现者需要确保其扩展的线程安全。

## 建立扩展

现在，让我们创建一个示例扩展，用于计算某些事情的发生次数。

首先，我们定义`Extension`应该做什么：

```java
import akka.actor.*;
import java.util.concurrent.atomic.AtomicLong;

static class CountExtensionImpl implements Extension {
  // Since this Extension is a shared instance
  // per ActorSystem we need to be threadsafe
  private final AtomicLong counter = new AtomicLong(0);

  // This is the operation this Extension provides
  public long increment() {
    return counter.incrementAndGet();
  }
}
```

然后，我们需要为扩展创建一个`ExtensionId`，这样我们就可以获取它。

```java
import akka.actor.*;
import java.util.concurrent.atomic.AtomicLong;

static class CountExtension extends AbstractExtensionId<CountExtensionImpl>
    implements ExtensionIdProvider {
  // This will be the identifier of our CountExtension
  public static final CountExtension CountExtensionProvider = new CountExtension();

  private CountExtension() {}

  // The lookup method is required by ExtensionIdProvider,
  // so we return ourselves here, this allows us
  // to configure our extension to be loaded when
  // the ActorSystem starts up
  public CountExtension lookup() {
    return CountExtension.CountExtensionProvider; // The public static final
  }

  // This method will be called by Akka
  // to instantiate our Extension
  public CountExtensionImpl createExtension(ExtendedActorSystem system) {
    return new CountExtensionImpl();
  }
}
```

现在我们需要做的就是实际使用它：

```java
// typically you would use static import of the
// CountExtension.CountExtensionProvider field
CountExtension.CountExtensionProvider.get(system).increment();
```

或者从 Akka Actor 的内部：

```java
static class MyActor extends AbstractActor {
  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .matchAny(
            msg -> {
              // typically you would use static import of the
              // CountExtension.CountExtensionProvider field
              CountExtension.CountExtensionProvider.get(getContext().getSystem()).increment();
            })
        .build();
  }
}
```

就这些了！

## 从配置中加载

为了能够从 Akka 的配置中加载扩展，必须在提供给`ActorSystem`的配置的`akka.extensions`部分中添加`ExtensionId`或`ExtensionIdProvider`实现的 FQCN。

```yml
akka {
  extensions = ["docs.extension.ExtensionDocTest.CountExtension"]
}
```

## 适用性

一切都是可能的！顺便问一下，你知道 Akka Typed Actor、序列化和其他特性是作为 Akka 扩展实现的吗？

### 应用程序特定设置

该「[配置](https://doc.akka.io/docs/akka/current/general/configuration.html)」可用于特定于应用程序的设置。一个好的实践是将这些设置放在扩展中。

配置示例：

```yml
myapp {
  db {
    uri = "mongodb://example1.com:27017,example2.com:27017"
  }
  circuit-breaker {
    timeout = 30 seconds
  }
}
```

`Extension`示例：

```java
import akka.actor.Extension;
import akka.actor.AbstractExtensionId;
import akka.actor.ExtensionIdProvider;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import com.typesafe.config.Config;
import java.util.concurrent.TimeUnit;
import java.time.Duration;

static class SettingsImpl implements Extension {

  public final String DB_URI;
  public final Duration CIRCUIT_BREAKER_TIMEOUT;

  public SettingsImpl(Config config) {
    DB_URI = config.getString("myapp.db.uri");
    CIRCUIT_BREAKER_TIMEOUT =
        Duration.ofMillis(
            config.getDuration("myapp.circuit-breaker.timeout", TimeUnit.MILLISECONDS));
  }
}

static class Settings extends AbstractExtensionId<SettingsImpl> implements ExtensionIdProvider {
  public static final Settings SettingsProvider = new Settings();

  private Settings() {}

  public Settings lookup() {
    return Settings.SettingsProvider;
  }

  public SettingsImpl createExtension(ExtendedActorSystem system) {
    return new SettingsImpl(system.settings().config());
  }
}
```

使用它：

```java
static class MyActor extends AbstractActor {
  // typically you would use static import of the Settings.SettingsProvider field
  final SettingsImpl settings = Settings.SettingsProvider.get(getContext().getSystem());
  Connection connection = connect(settings.DB_URI, settings.CIRCUIT_BREAKER_TIMEOUT);
}
```

## 库扩展

第三方库可以通过将其附加到其`reference.conf`中的`akka.library-extensions`来注册它的扩展，以便在 Actor 系统启动时自动加载。

```java
akka.library-extensions += "docs.extension.ExampleExtension"
```

由于无法有选择地删除此类扩展，因此应小心使用，并且仅在用户不希望禁用此类扩展或不支持禁用此类子功能的情况下使用。这一点很重要的一个例子是在测试中。

- **警告**：永远不要分配`akka.library-extensions= ["Extension"]`，因为这样会破坏库扩展机制并使行为依赖于类路径顺序。


----------

**英文原文链接**：[Akka Extensions](https://doc.akka.io/docs/akka/current/extending-akka.html).


----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————