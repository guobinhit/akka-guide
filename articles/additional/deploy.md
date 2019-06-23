# 如何部署 Akka?

Akka 可以以不同的方式使用：

- 作为库：在应用程序的环境变量中作为一个常规 JAR 使用，将其放入`WEB-INF/lib`
- 作为与「[sbt-native-packager](https://github.com/sbt/sbt-native-packager)」打包的应用程序

## 原生打包程序

`sbt-native-packager`是用于创建任何类型应用程序（包括 Akka 应用程序）的分发的工具。

在`project/build.properties`文件中定义 sbt 版本：

```yml
sbt.version=0.13.13
```

在`project/plugins.sbt`文件中添加`sbt-native-packager`：

```
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.1.5")
```

按照「[ sbt-native-packager 插件文档](https://sbt-native-packager.readthedocs.io/en/latest/archetypes/java_app/index.html)」中`JavaAppPackaging`的说明进行操作。

## 在 Docker 容器中

你可以在 Docker 容器内同时使用 Akka 远程处理和 Akka 集群。但是请注意，在使用 Docker 时，你需要特别注意网络配置，在「[Akka behind NAT or in a Docker container](https://doc.akka.io/docs/akka/current/remoting.html#remote-configuration-nat)」中描述了相关信息。

你可以查看「[带有 docker-compse 示例项目的集群](https://developer.lightbend.com/start/?group=akka&project=akka-sample-cluster-docker-compose-java)」，以了解实际中的情况。

为了让 JVM 在 Docker 容器中运行良好，可能需要调整一些通用（不是特定于 Akka）参数。

### 资源限制

Docker 允许限制每个容器的资源使用。

### 内存

你可能希望在 JVM `8u131`之后使用「[-XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap](https://dzone.com/articles/running-a-jvm-in-a-container-without-getting-kille)」选项设置，这使它了解`c-group`内存限制。在 JVM 10 和更高版本上，则不再需要`-XX:+UnlockExperimentalVMOptions`选项设置。

### CPU

对于多线程应用程序（如 JVM），CFS 调度程序限制是不合适的，因为它们会限制允许的 CPU 使用，即使主机系统有更多的 CPU 周期可用。这意味着你的应用程序可能缺少 CPU 时间，但你的系统似乎处于空闲状态。

因此，最好完全避免`--cpus`和`--cpu-quota`，而是使用`--cpu-shares`指定相对容器权重。

## 在 Kubernetes 中
### 集群 bootstrap

为了利用你在 Kubernetes 内部运行而形成集群这一事实，你可以使用「[Akka Cluster Bootstrap](https://doc.akka.io/docs/akka-management/current/bootstrap/)」模块。

你可以查看「[带有 Kubernetes 示例项目的集群]()」，以了解实际中的情况。

### 资源限制

为了避免 CFS 调度程序限制，最好不要使用`resources.limits.cpu`，而是使用`resources.requests.cpu`配置。




----------

**英文原文链接**：[How can I deploy Akka?](https://doc.akka.io/docs/akka/current/additional/deploy.html).



----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————