# 自研 EPUB 正文层

当前架构以 [ReaderBodyView 设计](reader-body.md) 为准。整个 Demo 和 EPUB 扩展均 minSdk 21。EPUB、TXT、网络小说统一通过 `ReaderView.openBook(BookSource)`，没有独立 EPUB Activity 或导航 Fragment。

## 接入

```kotlin
val source = EpubReaderBookSource(
    bookId,
    ReaderInput.fromUri(context, uri),
    File(context.cacheDir, "epub")
)
reader.openBook(source)
// reader.closeBook()/release() 停止使用后，宿主关闭 source。
```

包名 `org.peyilo.ireadview.epub`，模块 `:app:ireadview-epub`。宿主可以是普通 Activity。ReaderInput 也可来自 asset、文件或宿主提供的网络流；URI 授权、HTTP 协议和重试由宿主负责。

Maven开发坐标为`org.peyilo.ireadview:ireadview-epub:0.1.0-SNAPSHOT`，由POM/Gradle metadata传递依赖核心。`:app:ireadview-epub:bundleReleaseRepository`生成模块仓库ZIP；宿主同时提供核心仓库并启用coreLibraryDesugaring与项目固定版本desugar_jdk_libs_nio。完整依赖配置和公开SPI消费见[独立EPUB宿主](../examples/maven-epub-host/README.md)。该工程用同一个ReaderView切换文本/EPUB，不引入独立正文Activity。

Demo 默认使用用户提供的 `app/src/main/assets/epubs/the-art-of-arm-assembly.epub`，原文件未修改，SHA256：`f751f1d8bc8fa034788e26410540189b0d22f59a239e934894aee586b63b380e`。另外两本原创小样用于重排/固定版式开发。

## 当前结构

```text
ReaderView / BookReadView
 ├─ BookSource：TXT / 网络章节 → 原生块排版
 │   └─ PagedBookPresentation / ScrollingBookPresentation
 │       └─ ReaderPageTemplate.bodyHost → TextReaderBodyView : ReaderBodyView
 └─ EpubReaderBookSource：EPUB 容器与资源
     └─ EpubBookPresentation
         └─ 同一 ReaderPageTemplate.bodyHost → EpubReaderBodyView : ReaderBodyView
             └─ 系统 WebView + 自研 epub-body.js
```

`ReaderBodyView` 是主线程 View 抽象：选区几何、绑定结束、最终释放。具体实现保留各自的内容绑定协议；文本片段和文档 DOM 不强制互相转换。页面模板负责页眉页脚，Widget 负责已提交位置、导航事务、手势与翻页效果，正文实现负责本地排版与命中。

`EpubPublication` 对外封装现有 EPUB 容器、OPF、spine、EPUB3 nav / EPUB2 NCX 和资源读取，渲染模块不访问内部解析器。保留资源字节及相对路径。默认归档上限64 MiB、单条目32 MiB、总展开256 MiB、条目10000；不将 ZIP 路径解压到任意文件目录。

`EpubReaderBookSource` 和候选/当前页通过引用租约共享出版物。来源关闭或重载不提前关闭仍在显示的包。候选取消释放 WebView、回调、页面模板和租约。

## 自己控制的渲染与交互

- 不依赖 Readium。Readium Kotlin Toolkit 的公开源码仅用于架构参考，来源与版权说明见[第三方声明](../THIRD_PARTY_NOTICES.md)。
- HTML/CSS、图片、SVG图片、字体通过私有合成 HTTPS 来源读取包内资源；不允许 WebView 访问文件或远程网页。正文脚本、frame、表单等不执行；没有 Android JavaScript 对象桥。宿主用 evaluateJavascript 调用自己的排版脚本。[WebView 资源拦截 API](https://developer.android.com/reference/android/webkit/WebViewClient#shouldInterceptRequest(android.webkit.WebView,%20android.webkit.WebResourceRequest)) 是底层资源边界。
- 重排模式采用 CSS 分栏。候选正文在隐藏模板中完成测量和定位，提供 PageSurface；iReadView 决定动画、提交和取消。滚动模式回报可见文档位置；章节前后导航复用相同导航入口。
- 正文准备、动画和显示交接遵循[翻页性能与交接](performance.md)的所有权约束。EPUB 在隐藏正文前提供事务独占的像素快照，不将仍引用 WebView 子节点的硬件显示列表作为不可变快照。准备期间复用已完成的视觉栅栏，旧系统的软件录制在空闲阶段完成；失败时通过统一资源降级处理。
- 分页模式最多保留三份正文：当前页与两份备用页，利用阅读空闲准备后续页，导航/动画期间暂停预加载。章内复用已加载文档，仅移动分栏位置，准备候选时不滚动当前正文。取消后完整且有效的候选归还缓存；未完成准备的页面释放。关闭呈现层释放全部正文。换章、内容版本、视口或样式变化后不能复用过期布局。未改变的批注不重复拆分/包裹 DOM，划线几何随页面更新。
- 文本与 EPUB 共用[缓存策略及调度 SPI](page-cache.md)，宿主可调用 `setPageCacheMode(SINGLE_PAGE/THREE_PAGE)`。默认三页，单页模式只保留当前页及导航过程中暂存的候选页。
- WebView 禁用宽视口启发式，并固定 viewport 的最小/最大缩放，防止宽内容触发自动缩小、扩大 innerWidth 并使滚动提前到达上限。分栏页码按最近列计算，容纳 CSS 小数宽度与实际滚动位置的舍入差。候选截图前等待 WebView 显示列表更新，API21/22 使用帧回调兼容路径。
- DOM 定位使用版本化 `documentLocator`：href、正文节点路径/UTF-16偏移、progression。页码是当前排版提示；改字号后优先重定位文本。目录 fragment 和正文指向 spine 的包内链接进入同一导航队列；外链交给 ReaderLinkHandler。非 spine 附属文档尚未实现独立阅读栈。
- 长按、活动选区背景和手柄全部由 iReadView 控制：默认选择完整段落，复用文本模式的 Canvas 选区绘制与分页拖动控制器，支持同文档跨页扩选与代理手柄；滚动使用共享边缘停留/速度策略。WebView 不接管触摸选择、ActionMode 或 CSS ::selection。选区、复制、高亮、划线与菜单走统一 Widget API，详见[选择与标注](selection-annotations.md)。
- 正文/标题字号、颜色、行距、字距、段间距、首行缩进、正文内外边距、块 padding 与标题 padding 映射到原有 ReaderStyle。System 字体可映射；Resource/Runtime Typeface 目前尚未完整映射到 Web 字体，不能认为所有字体参数均已支持。

## 验证与未完成项

以下仍需开发或专项验收，不能宣称“完全兼容 EPUB”：

- DOM 高亮/划线已接入统一注解仓库、状态、菜单与 Demo 持久化；使用真实 DOM 范围和规范文本地址，不转换成伪造文本块。复杂重叠/双向文本/跨章选区和完整兼容矩阵仍需专项验证。
- 固定版式目前实现单页 viewport 缩放；混合版式、spine 级覆盖、spread、RTL/竖排、SVG spine、字体混淆、CFI 与媒体叠加尚未完整实现。
- 大表格、公式、复杂出版物 CSS、跨文档选区、无障碍跨章节遍历需要矩阵验证。脚本型 EPUB、DRM、远程嵌入内容当前不执行/解密。
- 当前滚动只连续显示一个 spine 文档，跨文档仍通过统一前后导航；尚非全书无缝滚动。
- 原生全书页索引尚未支持 DOM 排版，返回 Unsupported，不生成“一章一页”的假索引。
- API21 合并 manifest 保持最低版本要求；旧系统 WebView 的排版能力取决于安装版本，API21 设备兼容矩阵尚未完成。
