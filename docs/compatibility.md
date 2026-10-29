# 兼容边界

## 支持范围

两个库及 Demo 的最低 Android 版本为 API 21。构建工具版本由 Gradle Wrapper、根构建脚本和版本目录固定。内置正文支持原生文本块与可选 EPUB 文档引擎，宿主可替换相关实现。

## 已知限制

| 领域 | 范围 |
| --- | --- |
| EPUB | 支持常见 EPUB 2/3 容器、目录、图文和链接；不宣称完整 EPUB 一致性，复杂固定版式、RTL、脚注及旧 WebView 需单独验证 |
| Web 内容 | 不执行出版物脚本、frame 或表单，不加载出版物要求的远程资源；DRM、音视频和阅读系统脚本 API 不属于当前实现 |
| 字体 | 原生 Typeface 与 EPUB Web 字体的映射范围不同；Resource/Runtime 字体不保证完整映射 |
| 选择 | 同章节或同文档跨页扩选；跨章节选区、复杂双向文本、重叠标注及跨图片选择仍有边界 |
| 标注 | 内置书签、高亮与划线；任意第三方类型及笔记业务由宿主扩展，不自动获得内置校验/绘制能力 |
| 呈现 | 原生全书页索引只用于实际支持块排版的引擎；章节页数不能代替全书页数；双页阅读尚未形成完整宿主流程 |
| 无障碍 | 提供来源范围、遍历和动作接口；复杂自定义语义及 TalkBack/Switch Access 需设备检查 |
| 性能 | EGL 回读、WebView 准备和图片解码存在设备差异；缓存命中不能证明全部效果稳定 60fps |
| 发布 | iReadView 提供开发 Maven 产物；核心与扩展一起编译，不承诺旧 SPI 的二进制兼容 |

## 验证入口

[持续集成](building.md)检查架构边界、文档、编译、JVM 测试、AndroidTest 编译和独立 Maven 消费。PR 与提交的实际结果、错误和报告见对应 GitHub Actions run。

设备用例位于各模块 `src/androidTest`，包括 `IosCurlDeviceTest`、`IosCurlBackgroundDeviceTest`、`IosCurlDemoTest`、`UnifiedSelectionIntegrationTest`、`ReaderPageCacheIntegrationTest` 及独立 EPUB 宿主测试。它们覆盖有限场景，不能据此推断完整设备矩阵。

性能验证保留动画时长与输入轨迹，分别记录页面准备、效果启动、更新、绘制和 Window FrameMetrics，方法见[翻页性能](performance.md)。
