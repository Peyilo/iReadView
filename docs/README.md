# 文档入口

整理日期：2026-10-19。本文档集只保留当前契约、必要设计依据和有明确范围的验证记录，不再维护逐轮追加日志。

## 新对话最小阅读集合

1. [交接说明](03_IMPLEMENTATION_HANDOFF.md)：工作区、用户约束、最新改动、验证边界及待办。
2. [当前架构](02_ARCHITECTURE_AND_PROJECT_STRUCTURE.md)：职责和扩展边界。
3. 按任务读取下面一个专题。不要默认加载所有文档或原始 JVM 清单。

| 需要 | 文档 |
|---|---|
| 原始行为基线、FR编号和完整验收场景 | [产品需求](01_PRODUCT_REQUIREMENTS.md) |
| 公共 API、线程、生命周期与所有权 | [API 契约](04_WIDGET_API_DESIGN.md) |
| 能力查询、点击策略、选区定位和宿主笔记/书签 | [宿主场景API](HOST_API_SCENARIOS.md) |
| 当前功能状态、已知缺口和证据 | [状态与验证](07_REQUIREMENT_IMPLEMENTATION_AUDIT.md) |
| 组件可替换性、依赖边界和外部消费的审计 | [架构审计](ARCHITECTURE_AUDIT.md) |
| 创建 Widget、来源、恢复、本地 Maven 接入 | [宿主接入](08_HOST_INTEGRATION_GUIDE.md) |
| 多书源、TXT/EPUB/网络小说、图片和正文链接 | [内容与书源架构](CONTENT_SOURCES.md) |
| 默认文字排版组合、图片加载/解码、预算和运行时切换 | [内容扩展](CONTENT_EXTENSIONS.md) |
| 正文 View 抽象、文本/EPUB 的职责边界 | [正文层设计](READER_BODY.md) |
| 独立替换正文绘制、选区交互/绘制、完整引擎与翻页 | [组件扩展API](EXTENSIONS.md) |
| 文本/EPUB共用的三页缓存、所有权和切换API | [分页缓存](PAGE_CACHE.md) |
| 自研 EPUB 正文、系统 WebView 与 API21 边界 | [EPUB 引擎接入](EPUB_ENGINE.md) |
| 六位置默认模板及任意 View 自定义的两层边界 | [页眉页脚](PAGE_CHROME.md) |
| 选择、跨页手柄、菜单、高亮、划线与统一标注 | [选择与标注](SELECTION_AND_ANNOTATIONS.md) |
| iBook、Google、GLSL卷页、阴影数学及历史验证 | [翻页渲染](RENDERING.md) |
| 文本/EPUB全部翻页效果的性能对照与当前瓶颈 | [翻页性能测量](EPUB_TURN_PERFORMANCE.md) |
| 手势首帧、跟手动画、正文交接与白闪回归 | [手势响应及正文交接](GESTURE_AND_EPUB_HANDOFF.md) |
| 样式字段、单位与排版调节方法 | [排版 API](26_READER_PANELS_AND_TYPOGRAPHY_API.md) |
| 发布摘要及 Java/JVM 签名导出方法 | [发布清单说明](09_RELEASE_JVM_API_REFERENCE.md) |
| Demo目录与各示例用途 | [Demo说明](../app/README.md) |

## 可信度与维护

- 当前实现以生产源码/KDoc为准；需求文档是行为目标，不等于已实现或验收。
- 构建成功、有限单元/离屏检查、完整设备交互验收是不同证据。
- generated 下的历史报告保留原日期、哈希和范围，不可改写成当前版本已通过。
- 修改后更新当前专题及状态；避免再把同一过程追加到多篇文档。
- 此次删除了原05实施计划、06长进度日志及23～45中的重复/被替代专题。有效内容归并至当前架构、API、状态和三个专题；被撤回方案、失败的局部修补结论不再作为指南。必要的反例在渲染/选择专题中保留。
- 原始需求、可运行示例、生成工具、原始产物清单和有效历史证据保留，按需读取。
