# 文档索引

文档按主题命名，不维护文件序号或聊天交接记录。所有命令从仓库根目录执行，路径均相对仓库；机器安装路径、临时日志和生成报告不属于项目文档。

## 入门与设计

| 文档 | 内容 |
| --- | --- |
| [构建与持续集成](building.md) | 环境要求、Gradle 任务、发布产物、CI 与诊断 |
| [宿主接入](integration.md) | 创建 Widget、来源、导航、恢复和 Maven 消费 |
| [产品范围](requirements.md) | 目标、功能要求和核心不变量 |
| [架构](architecture.md) | 模块、职责、依赖方向与扩展边界 |
| [API 契约](api-contracts.md) | 线程、身份、取消、所有权和生命周期 |
| [兼容边界](compatibility.md) | 支持范围、已知限制和验证方式 |

## 内容与定制

| 文档 | 内容 |
| --- | --- |
| [内容与书源](content-sources.md) | TXT、EPUB、网络来源、图片和链接 |
| [正文层](reader-body.md) | ReaderBodyView 与格式专属呈现 |
| [EPUB 引擎](epub.md) | 容器、WebView、定位、交互及限制 |
| [内容扩展](content-extensions.md) | 自定义块排版、图片读取和预算 |
| [组件扩展](extensions.md) | 正文、选区、划线、呈现和效果替换 |
| [宿主场景](host-scenarios.md) | 能力查询、点击策略、选区定位和业务动作 |
| [分页缓存](page-cache.md) | 单页/三页策略、预加载和资源所有权 |
| [页面模板](page-chrome.md) | 任意 View 扩展与默认六位置页眉页脚 |
| [排版](typography.md) | 样式、单位、边距、字体和设置面板 |
| [选择与标注](selection-annotations.md) | 选择、跨页拖动、菜单、高亮和划线 |
| [翻页渲染](rendering.md) | 内置效果、背景、阴影与资源处理 |
| [PageCurl Core 接入](pagecurl-core.md) | 外部算法库与 Android 适配层 |
| [翻页性能](performance.md) | 页面准备、跟手动画、正文交接和测量 |

## 维护规则

- 可执行 API 以源码和 KDoc 为准；修改契约时同步更新所属专题。
- 构建日志、JVM 导出、截图和性能原始记录输出到忽略的 `build/`，不提交为长期说明。
- 构建、单元测试、设备交互与性能测量分别记录，使用对应提交的 CI 结果判断。
- 删除或重命名文档时更新引用；运行 `python tools/check-docs.py` 检查相对链接和机器路径。
- 平台安装路径通过环境变量或未跟踪的配置提供；示例不得依赖维护者的目录或设备编号。
