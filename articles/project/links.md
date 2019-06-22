# 项目
## 商业支持

商业支持由「[LightBend](https://www.lightbend.com/)」提供。Akka 是「[Lightbend Reactive Platform](https://www.lightbend.com/lightbend-platform)」的一部分。

## 赞助商

Lightbend 是 Akka 项目、 Scala 编程语言、Play Web Framework、Lagom、sbt 和许多其他开源项目的背后公司。它还提供了 Lightbend 反应式平台，该平台由开源核心和商业企业套件提供支持，用于在 JVM 上构建可扩展的反应式系统。请访问「[lightbend.com](https://www.lightbend.com/)」以了解更多信息。

## 邮件列表

- [Akka User Google Group](http://groups.google.com/group/akka-user)

## Gitter

关于使用 Akka 的聊天室：<a href="https://gitter.im/akka/akka">
<img src="https://img.shields.io/badge/gitter%3A-akka%2Fakka-blue.svg?style=flat-square" alt="gitter: akka/akka">
</a>

关于处理与 Akka 开发和贡献相关的所有问题的聊天室：<a href="https://gitter.im/akka/akka">
<img src="https://img.shields.io/badge/gitter%3A-akka%2Fdev-blue.svg?style=flat-square" alt="gitter: akka/dev">
</a>

## 源代码

Akka 使用Git，并在「[Github akka/akka](https://github.com/akka/akka)」托管。

## 发布仓库

所有 Akka 版本都通过 Sonatype 发布到 Maven 中央仓库，请参见「[search.maven.org](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.typesafe.akka%22)」。


## 快照仓库

在 https://repo.akka.io/snapshots 中，可以使用每日构建（`nightly builds`）作为`SNAPSHOT`和时间戳版本。

对于时间戳版本，请从 https://repo.akka.io/snapshots/com/typesafe/akka 中选择时间戳。属于同一个构建的所有 Akka 模块都具有相同的时间戳。

- **警告**：除非你知道自己在做什么，否则不鼓励使用 Akka 快照、每日构建和里程碑版本。

### 快照仓库的 sbt 定义

确保将仓库添加到 sbt 冲突解决程序：

```yml
resolvers += "Akka Snapshots" at "https://repo.akka.io/snapshots/"
```

使用以时间戳定义的库依赖项版本。例如：


```xml
libraryDependencies += "com.typesafe.akka" % "akka-remote_2.12" % "2.5-20170510-230859"
```

### 快照仓库的 Maven 定义

确保将仓库添加到`pom.xml`中的 Maven 仓库中：

```xml
<repositories>
  <repository>
    <id>akka-snapshots</id>
    <name>Akka Snapshots</name>
    <url>https://repo.akka.io/snapshots/</url>
    <layout>default</layout>
  </repository>
</repositories>
```

使用以时间戳定义的库依赖项版本。例如：

```xml
<dependencies>
  <dependency>
    <groupId>com.typesafe.akka</groupId>
    <artifactId>akka-remote_2.12</artifactId>
    <version>2.5-20170510-230859</version>
  </dependency>
</dependencies>
```




----------

**英文原文链接**：[Project](https://doc.akka.io/docs/akka/current/project/links.html).


----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————