# iReadView

可嵌入 Android 应用的阅读组件。通过同一个 `ReaderView` 显示 TXT、EPUB 和网络章节，提供分页、连续滚动、选择、标注、页面模板和可替换翻页效果。书架、账号、网络协议和业务持久化由宿主实现。

## 快速开始

需要 JDK 17、Android SDK Platform 36 和 Build Tools 36.0.0。设置 `ANDROID_HOME`，或在不纳入版本控制的 `local.properties` 中设置 `sdk.dir`。

```sh
./gradlew :app:assembleDebug
```

Windows 使用 `gradlew.bat` 运行相同任务。Gradle、AGP 和 Kotlin 版本由仓库固定，应用及两个库模块的最低 Android 版本均为 API 21。

- [宿主接入](docs/integration.md)：创建控件、打开书籍、保存和恢复位置。
- [构建与持续集成](docs/building.md)：构建、测试、发布和错误报告。
- [文档索引](docs/README.md)：架构、API 及各专题。
- [Demo](app/README.md)、[文本 Maven 宿主](examples/maven-host/README.md)、[EPUB Maven 宿主](examples/maven-epub-host/README.md)。

## 模块

| 模块 | 职责 |
| --- | --- |
| `:app:ireadview` | View Widget、TXT/块内容、导航、选区、页面模板和效果适配 |
| `:app:ireadview-epub` | 系统 WebView 与自研脚本组成的 EPUB 正文引擎 |
| `:app` | 文件导入、网络来源、阅读界面和配置持久化示例 |

EPUB 与文本共用 `ReaderBodyView`、页面模板和导航入口。iOSCurl 依赖 Maven Central 的 `org.peyilo.pagecurl:pagecurl-core:0.1.0`；GoogleCurl 在阅读库中实现。宿主通过 `PageTurnEffects` 配置效果。

库可生成 AAR 和包含依赖元数据的 Maven 仓库 ZIP。iReadView 当前使用开发坐标 `org.peyilo.ireadview:ireadview:0.1.0-SNAPSHOT`；[发布说明](docs/building.md)列出产物和消费方式。

功能与兼容范围见[兼容边界](docs/compatibility.md)，第三方依赖及示例书籍说明见[第三方声明](THIRD_PARTY_NOTICES.md)。

## 贡献者

- [Peyilo](https://github.com/Peyilo)：项目维护。
- [Codex（OpenAI）](https://developers.openai.com/codex/)：AI 辅助开发，参与 Kotlin 移植、内核整理、EPUB 翻页修复及回归验证。
