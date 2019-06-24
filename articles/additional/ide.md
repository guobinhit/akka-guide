# IDE 提示
## 在 IntelliJ / Eclipse 中配置自动导入功能

为了获得平稳的开发体验，当使用诸如 Eclipse 或 IntelliJ 之类的 IDE 进行 Scala 和 viceversa 的工作时，你可以从`javadsl`中禁止自动导入（`auto-importer`）功能。

在 IntelliJ 中，自动导入设置位于`Editor/General/Auto Import`下。使用诸如`akka.stream.javadsl*`或`akka.stream.scaladsl*`或`*javadsl*`或`*scaladsl*`等名称掩码指示要从`import/completion`中排除的 DSL。请参见下面的屏幕截图：

![auto-import](https://github.com/guobinhit/akka-guide/blob/master/images/additional/ide/auto-import.png)

Eclipse 的使用者则可以在 IDE 的`Window/Preferences/Java/Appearance/Type Filters`路径下进行配置。



----------

**英文原文链接**：[IDE Tips](https://doc.akka.io/docs/akka/current/additional/ide.html).



----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————