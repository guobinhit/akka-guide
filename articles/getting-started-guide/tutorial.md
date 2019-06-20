# Akka 应用程序示例简介
写散文时，最难的部分往往是写前几句话。在开始构建 Akka 系统时，也有类似的“空白画布（`blank canvas`）”感觉。你可能会想：哪个应该是第一个 Actor？它应该保存在哪里？它应该做什么？幸运的是，与散文不同，既定的最佳实践可以指导我们完成这些初始步骤。在本文的其余部分中，我们将研究一个简单的 Akka 应用程序的核心逻辑，以向你介绍 Actor，并向你展示如何使用他们来制定解决方案。该示例演示了帮助你启动 Akka 项目的常见模式。

## 先决条件

你应该提前跟着「[快速入门 Akka Java 指南](https://github.com/guobinhit/akka-guide/blob/master/articles/qucikstart-akka-java.md)」中的指令来下载并运行`Hello World`示例。你将使用它作为种子项目，并添加本教程中描述的功能。

##  IoT 示例用例

在本教程中，我们将使用 Akka 构建物联网（`IoT`）系统的一部分，该系统报告安装在客户家中的传感器设备的数据。这个例子着重在温度的读数上。目标是使用示例代码允许客户登录并查看他们家不同区域最近报告的温度。你可以想象这样的传感器也可以收集相对湿度或其他有趣的数据，应用程序应该支持读取和更改设备配置，甚至可能在传感器状态超出特定范围时向房主发出警报。

在实际系统中，应用程序将通过移动应用程序或浏览器暴露给客户。本指南仅着重于存储通过网络协议（如 HTTP）调用的温度的核心逻辑，它还包括编写测试来帮助你熟悉和精通测试 Actor。

教程应用程序由两个主要组件组成：

- 设备数据收集：`Device data collection`，维护远程设备的本地表示，一个家庭的多个传感器设备被组织成一个设备组。
- 用户仪表板：`User dashboard`，定期从登录用户家中的设备收集数据，并将结果显示为报告。

下图说明了示例应用程序体系结构。因为我们对每个传感器设备的状态感兴趣，所以我们将把设备建模为 Actor。正在运行的应用程序将根据需要创建尽可能多的设备 Actor 和设备组实例。

![device-user](https://github.com/guobinhit/akka-guide/blob/master/images/tutorial/device-user.png)

## 在本教程中你将学到什么？

本教程介绍并说明：

- Actor 等级及其对 Actor 行为的影响
- 如何为 Actor 选择正确的粒度
- 如何将协议定义为消息
- 典型的会话风格

让我们从了解 Actor 开始。

----------

**英文原文链接**：[Introduction to the Example](https://doc.akka.io/docs/akka/current/guide/tutorial.html).

----------
———— ☆☆☆ —— [返回 -> Akka 中文指南 <- 目录](https://github.com/guobinhit/akka-guide/blob/master/README.md) —— ☆☆☆ ————