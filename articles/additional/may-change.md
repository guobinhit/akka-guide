# 模块标记为“可能改变”

为了能够引入新的模块和 API，而不必在它们发布时冻结它们，因为我们的「[二进制兼容性](https://github.com/guobinhit/akka-guide/blob/master/articles/additional/binary-compatibility-rules.md)」保证了我们引入的术语可能会改变。

具体地说，可能改变（`may change`）意味着 API 或模块处于早期访问模式，并且它：

- 不包括在 LightBend 的商业支持范围内（除非另有明确规定）
- 不能保证在次要版本中是二进制兼容的
- 可能在小版本中以中断方式更改了 API
- 可能会在一次小的发布中从 Akka 中完全放弃

完整的模块可以标记为“可能改变”，这可以在其模块描述和文档中找到。

单独的公共 API 可以用`akka.api.annotation.ApiMayChange`进行注释，以表明它比它所在的模块的其余部分的安全性更低。例如，当将“新” Java 8 API 引入到现有的稳定模块中时，这些 API 可以用这种注释标记，以指示它们尚未被冻结。请小心使用这些方法和类，但是如果你看到这样的 API，那么现在就是最好的来尝试使用它们的时候，并且可以在它们被冻结为完全稳定的 API 之前提供反馈，例如使用`akka-user`邮件列表、GitHub 问题或 Gitter。

可以提供尽最大努力的迁移指南，但这取决于可能更改模块的具体情况。

这样做的目的是能够尽早发布特性，使它们易于使用，并根据反馈进行改进，或者甚至发现模块或 API 根本就没有用。

这些是标记为“可能改变”的当前完整模块：

- [多节点测试](https://github.com/guobinhit/akka-guide/blob/master/articles/clustering/multi-node-testing.md)
- [Akka 类型](https://doc.akka.io/docs/akka/current/typed/actors.html)


----------

**英文原文链接**：[Modules marked “May Change”](https://doc.akka.io/docs/akka/current/common/may-change.html).



----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————