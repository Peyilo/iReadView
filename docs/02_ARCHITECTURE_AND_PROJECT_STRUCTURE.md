# 当前架构与职责

更新：2026-10-18。只描述当前实现和必须维持的边界；接入见[宿主指南](08_HOST_INTEGRATION_GUIDE.md)，状态见[验证清单](07_REQUIREMENT_IMPLEMENTATION_AUDIT.md)。

## 模块与依赖

- `:app:ireadview`：Android View Widget库，包根`org.peyilo.ireadview`，输出AAR及本地Maven仓库ZIP。
- `:app`：示例宿主，包根`org.peyilo.iread.demo`；使用公开API，负责书源、权限、界面和业务持久化。
- `:app:ireadview-epub`：自研 EPUB 正文模块，系统 WebView + 自有分页/DOM 定位，minSdk21，无 Readium 运行时或 Fragment 要求。TextReaderBodyView 与 EpubReaderBodyView 共同继承 ReaderBodyView，挂载同一页面模板，详见[正文抽象](READER_BODY.md)与[EPUB 接入](EPUB_ENGINE.md)。
- `examples/maven-host`：独立Kotlin/Java/XML消费工程，不复制库源码。
- 核心依赖为Android View/Canvas/StaticLayout、AndroidX Core/CustomView/RecyclerView与协程。宿主不必使用AppCompat、LifecycleOwner、数据库或Compose。

## 分层

```text
宿主应用 → ReaderView → BookReadView → BaseReadView<L>
               ↓            ↓               ↓
     分页/滚动呈现SPI    来源/标注/样式    导航提交、输入、效果生命周期
               ↓            ↓
       页面/正文块SPI   ReaderCoordinator
```

`BaseReadView<L>`是泛型容器，不解析书籍。`BookReadView`增加书籍业务。`ReaderView`装配默认分页与滚动呈现。内容呈现决定页面/流怎样组织，翻页效果仅决定画面如何过渡；滚动不是翻页效果。

## 实际职责归属

| 对象 | 职责 |
|---|---|
| NavigationController | 唯一已提交位置；准备、显示、提交/取消、替代归属 |
| BaseReadView / PageTurnGesture | 输入、视口、生命周期、控制效果的手势进度 |
| PageTurnEffectManager | 工厂安装、帧调度、终态与租借资源；不另存阅读位置 |
| BookReadView | 公开书籍/导航/样式/标注接口及事件协调 |
| ReaderCoordinator / ChapterRepository | 章节读取、请求合并、代次、预载、状态、内容/布局缓存 |
| TextBlockLayoutProvider / PaginationEngine | 同源文字几何、分页与连续片段、锚点映射 |
| BookPageIndexBuilder | 显式且可取消的全书页索引 |
| PagedBookPresentation / ScrollingBookPresentation | 各模式候选页面/连续流、准备与显示、可见锚点、选择拖动 |
| ReaderBodyView / 辅助节点工具 | 正文、高亮/划线、命中、手柄和可访问源范围 |
| AnnotationStore / TextSelectionResolver / SelectionToolbarController | 统一标注校验、源选区与工具栏动作 |
| ReaderPlaceholderController / ReaderPageClock | 占位与重试；通用分钟刷新 |
| 内置Text/Epub来源、OwnedSourceFile | 解析、预算与私有文件租约；来源由宿主关闭 |
| BookResourceSource / BookLinkSource | 可选资源读取与书内链接解析；无网络协议或 View 依赖 |
| ImageBlockLayoutProvider / LinkedTextBlock | 有预算的图片排版、原文链接范围与命中；分页/滚动共用 |

不为了匹配旧设计增设LocationStore、InputRouter、StyleDiff或总括SelectionController等空转发层。

## 页眉页脚的两个层次

1. **基础结构SPI**：ReaderPageDecorationFactory/Decoration支持任意区域View；ReaderPageFactory/Template可替换整页。它们不要求三栏、内置内容枚举、返回按钮或电量。
2. **默认模板**：是上述SPI的一个实现。ReaderPageChrome/Slot提供六位置、文字/动作、返回按钮配置；内容解析和电量订阅属于模板内部。

Widget保存配置提供器并协调刷新，不解释六个位置；呈现层只处理模板几何、绑定和生命周期。`context.chrome`是可选配置桥接，自定义实现无需使用。默认模板内部由DefaultPageChromeResolver解析内容，DefaultPage负责布局，DefaultChromeBatterySource负责可见性限定的电量监听；ReaderPageClock仅处理时间。

具体接入及Demo行为见[页眉页脚](PAGE_CHROME.md)。

## 关键边界

- 持久位置使用书/章/块身份和UTF-16源偏移；页码是版式派生结果。全书未知页数不能用章页数或加载窗口代替。
- 分页/滚动共享来源、样式、原文范围和标注；模式切换按源锚点恢复，不按scrollY恢复。
- 会话/内容代次、导航事务、布局键、来源版本分别判定归属；旧结果、回调重入不得覆盖新会话。
- 来源和布局在工作线程处理不可变数据；View绑定/绘制/交互在主线程。扩展同步代码应短且主动检查取消。
- 宿主持有来源、URI授权、网络鉴权与业务持久化。Widget不接管数据库、书架或账号同步。
- 工厂每次创建独立未挂载对象；候选页会重叠存在。部分失败、取消、替代、detach和release均需释放资源。
- 预算约束相应缓存/位图，不是整个进程内存上限。不要让动画每帧重新解析、分页或捕获整页。
- effects只接收只读PageSurface及手势/帧状态；提交页码由统一导航负责。

## 源码导航

多格式及网络书源的目标、边界和迁移见[内容与书源架构](CONTENT_SOURCES.md)。

库公开入口：widget、api.model/source/state/style/input/effect/event、spi.page/presentation/content/selection、source。
内部实现：internal.container/book/presentation/layout/render/selection/annotation/source/style/effect/cache等。
Demo按ui、data/source、data/storage、reader、content分包，详见[Demo结构](../app/README.md)。
