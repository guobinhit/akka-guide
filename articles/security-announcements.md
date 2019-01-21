# 安全公告
## 接收安全建议
接收所有安全公告的最好方法是订阅 [Akka 安全列表](https://groups.google.com/forum/#!forum/akka-security)。

发送安全邮件列表的频繁非常低，只有在安全报告被核心团队接收和修复后才会发送通知。

## 报告漏洞
我们强烈鼓励大家在公共论坛上披露这些问题之前，先向我们的私人安全邮件列表报告这些问题。

根据最佳实践，我们强烈建议任何人在公共论坛如邮件列表或者 Github 问题上披露安全漏洞之前，先向 [security@akka.io]() 报告潜在的安全漏洞。

发送到上述电子邮件的报告将由我们的安全团队处理，他们将与您合作，以确保及时修复。

## 相关安全文件

- [Disabling the Java Serializer](https://doc.akka.io/docs/akka/current/remoting.html#disable-java-serializer)
- [Remote deployment whitelist](https://doc.akka.io/docs/akka/current/remoting.html#remote-deployment-whitelist)
- [Remote Security](https://doc.akka.io/docs/akka/current/remoting.html#remote-security)

## 已修复的安全漏洞
- [Java Serialization, Fixed in Akka 2.4.17](https://doc.akka.io/docs/akka/current/security/2017-02-10-java-serialization.html)
- [Camel Dependency, Fixed in Akka 2.5.4](https://doc.akka.io/docs/akka/current/security/2017-08-09-camel.html)
- [Broken random number generators AES128CounterSecureRNG / AES256CounterSecureRNG, Fixed in Akka 2.5.16](https://doc.akka.io/docs/akka/current/security/2018-08-29-aes-rng.html)


----------

**英文原文链接**：[Security Announcements](https://doc.akka.io/docs/akka/current/security/index.html).

----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————