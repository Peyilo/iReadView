# iReadView Widget 类结构与 API 设计

当前阅读面板和新增 ReaderStyles / 固定 dp 字距契约见 [排版 API](26_READER_PANELS_AND_TYPOGRAPHY_API.md)。

> 更新：2026-10-07；保留 2026-09-30 目标 API 设计及后续调整。当前接入契约见 [08_HOST_INTEGRATION_GUIDE.md](08_HOST_INTEGRATION_GUIDE.md)，已落地范围见 [06_IMPLEMENTATION_PROGRESS.md](06_IMPLEMENTATION_PROGRESS.md)，实际职责归属见 [05_WIDGET_IMPLEMENTATION_PLAN.md](05_WIDGET_IMPLEMENTATION_PLAN.md) 第 2 节。本文含尚未实现的目标接口，不能将完整签名清单当作当前 AAR 已支持能力，也不表示未决产品范围已经确认。
>
> 本文将 [产品需求](01_PRODUCT_REQUIREMENTS.md) 和 [架构方向](02_ARCHITECTURE_AND_PROJECT_STRUCTURE.md) 细化为类、接口、方法和行为契约。实现方式见 [05_WIDGET_IMPLEMENTATION_PLAN.md](05_WIDGET_IMPLEMENTATION_PLAN.md)。代码片段为 Kotlin 签名设计，省略 import、注解、方法体及部分配套类型，不是可直接编译的源码。

## 1. Widget 接入原则

iReadView 交付 Android Library AAR，并提供包含依赖元数据及源码的本地 Maven 仓库包。默认接入入口是 `org.peyilo.ireadview.widget.ReaderView`，支持 XML、代码创建、标准 View 测量布局、触摸分发、无障碍及状态保存。宿主在 Activity、Fragment 或其他 ViewGroup 中放入这个 Widget，然后给它配置数据来源、样式和监听器。

公开操作由 Widget 暴露；内部会话协调器由 Widget 管理。宿主不必创建控制器、会话对象、ViewModel、Activity 或 Fragment 才能显示正文。Compose 宿主可用 `AndroidView` 包装；首版核心工程不增加 Compose 依赖。这里的 Widget 指应用内 View 控件。

宿主负责文件授权、网络协议、内容持久化、目录/设置面板及应用级导航。Widget 提供正文、加载/错误状态、默认选区交互和标注变更。目录及设置面板通过回调接入宿主。

Android 的自定义控件支持通过继承 View/ViewGroup、XML 属性、测量及绘制回调实现；本方案选择组合控件作为入口。[Android 自定义 View 文档](https://developer.android.com/develop/ui/views/layout/custom-views/custom-components)

## 2. 类结构与职责

```text
android.widget.FrameLayout
└─ BaseReadView<L : Any>                    abstract，通用阅读容器
   └─ BookReadView                         abstract，书籍阅读公共能力
      └─ ReaderView                       open，默认可直接使用的 Widget
         └─ 宿主自定义 ReaderView 子类       可替换页面模板/内容呈现

ReadContentAdapter<L>                      public SPI，内容呈现契约
└─ BookPresentation                       abstract，书籍呈现扩展基类
   ├─ PagedBookPresentation               internal，默认分页呈现
   ├─ ScrollingBookPresentation           internal，默认连续滚动呈现
   └─ 宿主自定义 BookPresentation 子类       public 扩展实现

PageTurnEffectFactory → PageTurnEffect     public SPI，视觉效果扩展
BookSource                                public SPI，目录/章节按需输入
ReaderPageFactory → ReaderPageTemplate    public SPI，页面布局扩展
ReaderPlaceholderFactory                  public SPI，占位视图扩展
ContentBlock                              public 模型，非文本块需 BlockLayoutProvider
└─ TextContentBlock                        public 原文接口，内置排版或宿主提供者
   ├─ TextBlock / HeadingBlock             默认正文 / 标题
   └─ 宿主不可变文本块                     元数据保留在宿主，偏移仍指向 text
ReaderListener / ReaderTapListener        public，事件与点击输出
```

`public SPI` 表示宿主可实现的扩展契约，不表示所有扩展都在同一个包：来源位于 `api.source`，效果位于 `api.effect`，页面/内容/呈现/工具栏位于 `spi` 对应子包。模型与事件分别位于 `api.model` / `api.event`。内部缓存、任务与协调器不属于宿主接入契约。

| 类 | 负责 | 主要扩展点 |
|---|---|---|
| `BaseReadView<L>` | 内容承载、视口、输入路由、导航事务、最终位置提交、效果装配及中断 | `createContentAdapter()`；位置类型 `L` 由子类选择 |
| `BookReadView` | 内容来源、书籍状态、章节导航、位置恢复、标注、样式、模式切换 | `supportedModes`、`createPresentation(mode, host)` |
| `ReaderView` | 装配默认分页/滚动呈现、页面模板、文本绘制与预设效果 | 继承 `BookReadView` 的工厂钩子；一般定制使用 setter |
| `BookPresentation` | 具体内容流、视口中的位置映射、导航目标准备及内容显示 | 分页、滚动或宿主自定义内容组织 |

基类使用泛型位置 `L`，因此可用于普通离散内容展示而无需加载书籍解析器。`BookReadView` 将位置固定为 `ReadingPosition`，提供需求约定的书籍、标注和事件。`ReaderView` 默认分页，支持显式切换连续滚动；两种呈现使用独立实现对象。首版不增加行为重复的 `PagedReaderView`、`ScrollingReaderView` 门面类。

继承用于确定控件共性与业务边界，呈现对象用于切换 Widget 内部的内容组织方式。宿主可继承 `BookReadView` 完全替换呈现，也可继承 `ReaderView` 仅替换其中一种呈现。

导航入口和位置提交由基类统一实现，均为 `final`。子类通过准备目标和报告可见内容参与导航，不能覆盖提交逻辑直接改页码。

## 3. 命名、数据与线程约定

| 项目 | 约定 |
|---|---|
| 包根 | `org.peyilo.ireadview`，与现有 Library namespace 一致 |
| 编号 | 公开章节号、章节页号、全书页号从 1 开始；内部列表索引从 0 开始 |
| 身份 | `bookId`、`chapterId`、`blockId`、标注 `id` 是宿主提供的非空稳定 String；分组 ID 与章节 ID 分开校验 |
| 内容偏移 | 原始规范化文本中的 UTF-16 offset，从 0 开始；区间为 `[start, end)` |
| 排版单位 | 字号 sp，边距/固定间距 dp，字符间距/首行缩进 em，行高倍率为无量纲 Float |
| SPI 几何单位 | 测量后的 px；Widget 坐标原点左上；点击另提供归一化坐标 |
| 方向 | padding 使用 start/top/end/bottom；翻页的上一页/下一页是内容顺序，视觉方向由配置决定 |
| 数据所有权 | 输入与输出集合复制为不可修改快照；不保留宿主可变 List/Map 的引用 |
| View 线程 | Widget setter、查询、监听回调、呈现绑定及 View/Canvas 操作在主线程；稳定原文/尺寸/估重等纯数据按相应 SPI 在工作线程读取 |
| suspend 入口 | 从主线程调用，在内部切换线程执行 IO/排版；挂起期间不阻塞 UI |
| 错误 | 内容错误返回领域结果；协程取消抛出 `CancellationException`；非法编程参数可抛 `IllegalArgumentException` |

utf-16 偏移与 Android 文本布局一致，但命中测试、选区手柄移动和分页断点必须落在合法字素边界，避免拆开代理对、组合字符和 emoji 序列。排版缩进与空白展示不插入原文，不改变锚点。

方法签名中的 `ReaderResult<T>` 表示 `Success(value)`、`Failure(ReaderError)`、`Rejected(ReaderRejection)`、`Superseded`；不使用模糊的 Boolean 表达失败原因。`ReaderRejection` 覆盖 `NoBook`、`NotReady`、`Busy`、`AtBoundary`、`UnknownChapter`、`InvalidPage`、`PageIndexUnavailable`、`BookMismatch`、`InvalidAnchor`、`Unsupported`、`Released` 等场景。`ReaderError` 包含阶段、稳定错误码、可选章节 ID、可重试标识和可用于本地诊断的 cause；UI 文案由宿主/资源决定。

## 4. Widget 公开操作

### 4.1 通用容器

```kotlin
abstract class BaseReadView<L : Any> : FrameLayout {
    val currentLocation: L?
    val navigationState: ReadNavigationState

    suspend fun navigate(
        target: ReadNavigation<L>,
        options: NavigationOptions = NavigationOptions()
    ): NavigationResult<L>
    suspend fun nextPage(options: NavigationOptions = NavigationOptions()): NavigationResult<L>
    suspend fun previousPage(options: NavigationOptions = NavigationOptions()): NavigationResult<L>
    suspend fun firstPage(): NavigationResult<L>
    suspend fun lastPage(): NavigationResult<L>

    fun setPageTurnEffect(factory: PageTurnEffectFactory): ReaderResult<EffectSelection>
    fun setPageTurnOptions(options: PageTurnOptions): ReaderResult<Unit>
    fun setReaderTapListener(listener: ReaderTapListener?)
    fun setInputOptions(options: ReaderInputOptions)
    fun addReadViewListener(listener: ReadViewListener<L>)
    fun removeReadViewListener(listener: ReadViewListener<L>)
    fun cancelTransition(): Boolean
    fun release()

    protected abstract fun createContentAdapter(): ReadContentAdapter<L>
}
```

`ReadNavigation<L>` 包含 `Next`、`Previous`、`First`、`Last`、`Position(location)`。`firstPage/lastPage` 在书籍 Widget 中指全书首/末可显示页；章节内首/末页使用 4.2 的章节方法。滚动中 `nextPage/previousPage` 移动约一个正文视口，并由可见文本计算最终位置。

`NavigationOptions` 包含 `animated = true`、`force = false`。`force` 可跳过最小翻页间隔并中止当前未提交的翻页，仍须校验边界、加载状态和目标位置；不允许忽略错误。动画正在结算时，普通导航返回 `Busy`，不积累无界命令队列。

`NavigationResult<L>` 的终态为 `Moved(from, to)`、`Unchanged(location)`、`Rejected(reason)`、`Failed(error)`、`Superseded`。`cancelTransition()` 仅取消当前动画并复位；`navigate` 调用方取消协程时也取消该调用拥有的未提交导航。

`ReadNavigationState` 为 `Idle`、`Preparing`、`Dragging`、`Settling`。通用层的 `ReadViewListener<L>` 提供 `onLocationChanged(view, change: ReadLocationChange<L>)`、`onNavigationStateChanged(view, state)`、`onEffectFallback(view, fallback)`、`onError(view, error)`，均有空默认实现。书籍层由同一个提交源生成 ReadingPosition 事件；同时注册两层监听时会收到各自事件，并不发生二次提交。

### 4.2 书籍 Widget

```kotlin
abstract class BookReadView : BaseReadView<ReadingPosition> {
    val readerState: ReaderState
    val currentPosition: ReadingPosition?
    val currentBook: BookDescriptor?
    val currentChapter: ChapterDescriptor?
    val chapterCount: Int                    // 未获取目录时为 0，状态另行区分
    val style: ReaderStyle
    val readingMode: ReadingMode
    val annotations: AnnotationSnapshot?

    suspend fun openBook(source: BookSource, request: OpenBookRequest = OpenBookRequest()): OpenResult
    fun closeBook()
    suspend fun reloadBook(): ReaderResult<BookDescriptor>
    suspend fun invalidateChapter(chapterId: String): ReaderResult<Unit>
    suspend fun retryChapter(chapterId: String): ReaderResult<Unit>

    suspend fun goToPosition(position: ReadingPosition): NavigationResult<ReadingPosition>
    suspend fun goToChapter(chapterId: String, pageNumber: Int = 1): NavigationResult<ReadingPosition>
    suspend fun goToChapterNumber(chapterNumber: Int, pageNumber: Int = 1): NavigationResult<ReadingPosition>
    suspend fun nextChapter(): NavigationResult<ReadingPosition>
    suspend fun previousChapter(): NavigationResult<ReadingPosition>
    suspend fun goToPage(pageNumber: Int): NavigationResult<ReadingPosition>
    suspend fun chapterFirstPage(): NavigationResult<ReadingPosition>
    suspend fun chapterLastPage(): NavigationResult<ReadingPosition>

    fun getChapterState(chapterId: String): ChapterState?
    fun getChapterPagination(chapterId: String): ChapterPaginationState?
    suspend fun buildBookPageIndex(): ReaderResult<BookPageIndex>
    suspend fun goToGlobalPage(pageNumber: Long): NavigationResult<ReadingPosition>

    suspend fun setReadingMode(mode: ReadingMode): ReaderResult<ModeChange>
    suspend fun applyStyle(style: ReaderStyle): ReaderResult<StyleApplication>
    fun setLoadingOptions(options: LoadingOptions)
    fun setPageFactory(factory: ReaderPageFactory)
    fun setPlaceholderFactory(factory: ReaderPlaceholderFactory)
    fun setSelectionToolbarFactory(factory: SelectionToolbarFactory?)
    fun setBlockLayoutProviders(providers: List<BlockLayoutProvider>)
    fun setReaderActionHandler(handler: ReaderActionHandler?)
    fun addReaderListener(listener: ReaderListener)
    fun removeReaderListener(listener: ReaderListener)

    fun replaceAnnotations(snapshot: AnnotationSnapshot): ReaderResult<Unit>
    fun exportAnnotations(): AnnotationSnapshot?
    fun addBookmark(bookmark: Bookmark): ReaderResult<Bookmark>
    fun removeBookmark(id: String): ReaderResult<Unit>
    suspend fun goToBookmark(id: String): NavigationResult<ReadingPosition>
    fun addHighlight(highlight: Highlight): ReaderResult<Highlight>
    fun updateHighlight(highlight: Highlight): ReaderResult<Highlight>
    fun removeHighlight(id: String): ReaderResult<Unit>
    fun getSelection(): TextSelection?
    fun clearSelection()
    fun highlightSelection(id: String, styleId: String): ReaderResult<Highlight>
    fun setAnnotationIdFactory(factory: AnnotationIdFactory)

    fun exportSnapshot(): ReaderSnapshot?
    suspend fun restoreSnapshot(source: BookSource, snapshot: ReaderSnapshot): OpenResult

    protected abstract val supportedModes: Set<ReadingMode>
    protected abstract fun createPresentation(
        mode: ReadingMode,
        host: BookPresentationHost
    ): BookPresentation
}

open class ReaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.ireadReaderViewStyle,
    defStyleRes: Int = R.style.Widget_IRead_ReaderView
) : BookReadView
```

`BookReadView.createContentAdapter()` 为 final，实现为当前 `BookPresentation` 的路由。所有工厂钩子在子类完成构造后首次装配时调用，不在基类构造函数中调用可覆写方法。

`ReadingMode` 使用稳定标识：内置 `PAGED`（`paged`）、`CONTINUOUS_SCROLL`（`continuous_scroll`），宿主可通过 `ReadingMode.custom(id)` 定义自己支持的模式。自定义 ID 由宿主命名，必须出现在子类的 supportedModes 中，默认 ReaderView 只支持两个内置模式。

打开新书立即建立新会话代次，取消旧请求、动画、选区及缓存。`OpenResult` 为 `Opened(position, restoration)`、`Empty(book)`、`Failed(error)` 或 `Superseded`。缺失初始位置从书首开始；提供的初始位置无效时依 `OpenBookRequest.initialFallback` 决定，默认回到书首并明确返回 `Restoration.Fallback(reason, actual)`。普通跳转失败保持当前阅读位置，不套用打开时的回退。

`OpenBookRequest` 包含 `initialPosition`、`initialChapterNumber`、`initialPageNumber`、`annotations`、`initialFallback`。完整位置优先于编号；二者同时传入时编号仅作提示。标注集合必须匹配 `source.bookId`。

`goToPage` 操作当前章节。`nextChapter/previousChapter` 跳到目标章节第一页，边界返回 `AtBoundary`。无效 ID 或编号不自动改成相邻章节。

`invalidateChapter` 用于宿主来源已更新后的显式通知：增加章节内容代次、取消旧加载和分页、清掉相关索引，当前章重新读取并恢复锚点；邻章仅失效，按需重读。`retryChapter` 仅重试失败加载；正常已加载章返回成功且不重复读取。`reloadBook` 重新加载目录，并按稳定章节 ID 恢复位置；目录/章节删除导致的回退通过结果和事件输出。

`applyStyle` 的 StyleApplication 结果包含 layoutChanged 与恢复后 position；当前样式通过 style 查询。仅高亮/选区差异刷新装饰，其他样式变化目前保守触发重排，纯文字色/背景的缓存优化尚未实现。`setReadingMode` 返回旧/新模式与锚点恢复结果。两者在视觉恢复完成后结束，过期调用返回 `Superseded`。页面工厂变更产生新的布局代次并通过状态/事件报告重建结果。

未打开书籍时，模式和样式操作只更新配置并立即成功，结果中的位置为空；首次 open 使用该配置。已打开时以当前锚点准备新布局/呈现，成功后才替换已显示内容。失败时保留之前可读的配置和画面，并通过结果/错误事件报告。正在读取书籍、尚无已提交位置时允许更新配置，旧布局任务失效，open 使用最新配置继续完成。

`setBlockLayoutProviders` 原子替换宿主 provider 列表，按注册顺序匹配，再回退内置文字/标题 provider；变更使相关布局失效。provider 身份和版本必须稳定，异步 measure 不可观察其未通知的可变状态。Widget setter 的状态/错误事件用于报告随后重建结果。

`LoadingOptions` 包含 `preloadBefore`、`preloadAfter`、`maxConcurrentLoads`、`contentCacheBytes`、`layoutCacheBytes`；效果位图预算由 `PageTurnOptions.bitmapBudgetBytes` 单独管理。LoadingOptions 的数量非负、并发至少 1、预算须为有效正值。设置后调整邻章窗口，生效不要求重开书。`ReaderInputOptions` 包含分页手势启用、点击翻页启用、键盘翻页启用、音量键翻页启用及触摸轴偏好；默认音量键关闭，手势/键盘操作统一进入导航入口。

### 4.3 Java 与非协程调用

为 `openBook`、`navigate`、章节/页/书签导航、`reloadBook`、章节失效/重试、`buildBookPageIndex`、`setReadingMode`、`applyStyle`、`restoreSnapshot` 提供对应 callback 重载，返回 `ReaderRequest`。不要求 Java 调用者操作协程 Continuation。

```kotlin
interface ReaderRequest {
    fun cancel()
    val isCompleted: Boolean
}

fun interface ReaderCallback<T> {
    fun onComplete(outcome: ReaderCompletion<T>)
}

// 示例：其他挂起操作采用同样的重载形式。
fun openBook(source: BookSource, request: OpenBookRequest, callback: ReaderCallback<OpenResult>): ReaderRequest
fun nextPage(options: NavigationOptions, callback: ReaderCallback<NavigationResult<ReadingPosition>>): ReaderRequest
```

`ReaderCompletion<T>` 为 `Completed(value)` 或 `Cancelled`。callback 主线程恰好调用一次，包括取消、关闭和释放；取消通知不作为旧会话的阅读事件。Widget 保存当前 `readerState` 供查询，callback 只表示本次请求的终态。请求与回调均延后调度；isCompleted 表示操作已结束，可能早于已排队回调的实际送达。cancel 可跨线程、幂等，不能改写已经产生的终态。宿主回调普通 Exception 仅记录诊断，不重新送达或改写结果；Error 不被吞掉。

终止原因区分：调用方取消、close、detach、release 对应协程取消/callback Cancelled；后来的 open、模式/样式请求或强制导航替代同类旧操作时，旧操作完成为其领域结果中的 Superseded。实现须记录终止原因后再取消底层任务，避免所有终止都被模糊转成同一结果。

## 5. 书籍和内容输入

```kotlin
// 当前模型的简化签名；集合由构造函数复制，省略初始化与内部索引。
class BookDescriptor(
    val bookId: String,
    val title: String,
    val revision: String? = null,
    tableOfContents: List<TocEntry>
) {
    val tableOfContents: List<TocEntry>
    val chapters: List<ChapterDescriptor>
}

sealed interface TocEntry {
    data class Chapter(val descriptor: ChapterDescriptor) : TocEntry
    class Group(val id: String, val title: String, children: List<TocEntry>) : TocEntry {
        val children: List<TocEntry>
    }
}

data class ChapterDescriptor(val chapterId: String, val title: String, val revision: String? = null)

class ChapterContent(
    val bookId: String,
    val chapterId: String,
    val revision: String? = null,
    blocks: List<ContentBlock>
) {
    val blocks: List<ContentBlock>
}

interface ContentBlock {
    val blockId: String
    val estimatedRetainedBytes: Long? get() = null
}
interface TextContentBlock : ContentBlock { val text: String }
// 两个内置类型另提供稳定的 estimatedRetainedBytes。
data class TextBlock(override val blockId: String, override val text: String) : TextContentBlock
data class HeadingBlock(override val blockId: String, override val text: String, val level: Int = 1) : TextContentBlock

interface BookSource {
    val bookId: String
    suspend fun loadBook(): BookDescriptor
    suspend fun loadChapter(chapterId: String): ChapterContent
}
```

目录分组按深度优先顺序展平成稳定章节序列。重复章节 ID、重复块 ID、来源返回错误书籍/章节 ID、循环/超深目录等作为内容校验错误报告。`HeadingBlock` 是正文中的标题，目录标题是元数据；默认呈现不会在来源已提供章节标题块时再复制标题。

`BookSource` 由宿主实现，允许内部访问数据库、本地文件或网络。Widget 从 IO 调度器调用来源；不同章节可能并行，同章同代次合并请求；来源应支持协程取消并自行切换其特殊线程需求。Widget 将普通来源异常转成 `ReaderError`，取消异常继续传播。

配套来源：

| 类型 | API 形式与职责 |
|---|---|
| `MemoryBookSource` | `MemoryBookSource(book, chapters)`；`replaceBook(book)`、`replaceChapter(content)` 更新来源；宿主再调用 Widget 的目录重载/章节失效 API |
| `TextBookSource` | `TextBookSource(bookId, input, options)`；编码、标题匹配规则、正文规范化和稳定章节/段落标识 |
| `EpubTextBookSource` | `EpubTextBookSource(bookId, input, cacheDirectory, options)`；宿主提供私有缓存目录并关闭来源，提取目录/正文文本，复杂媒体版式未确认 |
| 宿主远程来源 | 实现 `BookSource`；鉴权、HTTP Client、重试节奏、磁盘缓存归宿主 |

`ReaderInput` 采用可重新打开的流工厂，包含 `fromFile(file)`、`fromUri(context, uri)` / `fromUri(contentResolver, uri)`、`fromAsset(context, path)`、`fromResource(resources, id)`、`fromStream(opener)`，Java可调用静态入口。来源打开的流由来源关闭；调用方若传一次性流，必须包装为能提供后续读取所需内容的来源。URI 持久授权由宿主申请。

早期 `TextSourceOptions` / `ContentIdProvider` 命名已被实际 `TextBookOptions` / `TextContentIdProvider` 替代；不属于当前支持入口。当前配置与身份、规范化/预算见下段和08，不能据此创建旧草案类型。

当前落地模型为 `TextBookOptions(title, charset, chapterHeading, preambleTitle, maxInputBytes, indexDirectory, maxIndexBytes, contentIdProvider, normalization)`，默认 UTF-8、中文章节/英文 Chapter 全行规则、32 MiB 输入上限。`chapterHeading = null` 关闭标题识别；自定义 Regex 匹配 trim 后的整行，但输出保留原行空白。ReaderInput 已提供文件、URI（Context/ContentResolver）、资源、asset 和流工厂入口；每次返回新流，来源在成功、失败及取消时关闭。调用者不能重复交出已关闭的一次性流。

TextBookSource 支持有上限的内存快照和可选磁盘章节索引：loadBook 在 IO 中重读并原子替换，失败/取消保留旧快照；loadChapter 使用最近成功目录的快照，未加载目录时先建立。Mutex 串行化重读/章节访问，读取与解析循环检查取消；同步流 read 本身的阻塞仍取决于输入实现。编码采用严格 decoder，坏字节抛错，不猜测编码、不替换字符；只去掉解码后的首 BOM，CRLF/CR 固定转 LF，每个逻辑行按所选 normalization 处理后形成 TextBlock，标题形成 HeadingBlock，末尾换行不额外生成空块。标题前所有内容保留，未匹配标题和空文件各提供一个章节。未配置内容标识策略时，章节/块 ID 为有序 chapter-N/line-N，仅承诺相同解析输入与配置稳定；内容改动后原锚点仍需通过既有 revision/quote 校验，不能承诺插入章节后的 ID 不变。TXT revision v2 同时覆盖原始字节摘要、输出正文/身份/章结构、书名与规范化策略；正文仓库另按解析块指纹校验。

字节上限不是总堆内存上限：内存路径解码与块快照会有额外占用；磁盘路径仍有单行及当前章节模型占用。TextContentIdProvider 和 TextNormalization 已提供可选内容标识及逐行规范化策略；大型文本性能验收仍待完成。

Demo 已通过 ACTION_OPEN_DOCUMENT 选择 TXT，再由宿主编码对话框确认（UTF-8/UTF-16/UTF-16LE/UTF-16BE/GB18030）；任一步取消不调用 openBook。系统返回读权限且可持久化时由宿主申请持久授权，申请失败提示并仍可尝试本次读取；库不扩大权限。宿主用 URI SHA-256 加编码构成示例 bookId，避免不同编码下的锚点混用。输入选择成功后保存 URI/编码到 Activity Bundle，重建来源并配合 Widget 自己的 SavedState 恢复位置。冷启动最近阅读与逐书快照已由宿主 SharedPreferences 持久化，Activity 重建仍优先 View 状态；授权丢失仍可失败并重选。打开失败按既有 openBook 语义进入错误状态，重试带请求序号防止旧对话框覆盖后续打开。示例书籍专用修改目录/造高亮逻辑不对 TXT 执行；真实 TXT 使用 reloadBook 和选词工具栏。

普通数据输入使用 `ContentBlock`，块身份和内容须不可变。`TextContentBlock` 声明可选择原文：宿主提供者优先，未注册时沿内置正文排版；仅 `HeadingBlock` 使用内置标题样式。非文本自定义块必须注册 `BlockLayoutProvider`，缺少提供者沿 `LayoutFailed` 报错，不静默丢失。原文选择/标注/辅助来源遍历识别文本接口；图形说明不是可选择原文。自定义元数据不纳入内置章节指纹，内容变更由宿主更新 revision，排版算法变更更新提供者 version。未知持有大小不进入缓存。图片默认加载/呈现仍属待定范围。

### EPUB 目录与正文内部解析

公开入口为 `source.EpubTextBookSource(bookId, input, cacheDirectory, options)`，实现 BookSource/AutoCloseable。EpubTextBookOptions 提供 fallbackTitle 与归档/XML/fallback 预算，默认值与下文内部预算一致；cacheDirectory 由宿主提供，Widget 不申请权限、不关闭宿主来源。

loadBook 在 IO 上串行复制并解析新归档，只加载元数据/导航；成功且未取消/关闭时原子替换快照，失败保留旧快照和诊断。loadChapter 首次可隐式建立快照，按 manifest spine ID 查找资源并按需提取单章，不另存整书正文缓存。目录依 spine 原序生成，标题用 nav/NCX 的资源标签，缺失时回退 manifest ID；书名使用 OPF 标题或 fallbackTitle。原始 ZIP SHA-256 为 book/chapter revision，正文 ID/offset 采用上文提取规则。

来源的 diagnostics 是不可变 EpubDiagnostic(code, resourcePath, message) 列表快照，收集当前成功快照的元数据与已成功提取章节诊断，按 code/resourcePath 去重；重载成功重置，失败不覆盖，close 后为空。navigation 返回不可变 EpubTocEntry 树（title/resourcePath/fragment/children），保留原导航信息；片段通过 resolveNavigation 按需转换为 ReadingPosition。BookDescriptor 目录目前是 spine 平面列表，并明确提供 CatalogueUsesSpineOrder 诊断。宿主可自行展示 navigation，元素锚点转换规则见下文；不支持跨内容版本的元素身份恢复。

close 永久且幂等，拒绝后续读取并退休私有归档；已经借用流的读者保留租约直至结束，发布前再次检查关闭状态。loadBook/loadChapter 共用来源级 Mutex，避免同时重载/章节提取修改诊断；同步属性查询与 close 使用短生命周期锁。取消、失败或关闭期间的新归档会释放，不删除宿主原文件。当前每次重载复制 ZIP，尚无跨进程归档复用和崩溃遗留清理。

```kotlin
val source = EpubTextBookSource(
    bookId = hostStableBookId,
    input = ReaderInput.fromUri(context, uri),
    cacheDirectory = File(context.cacheDir, "reader-epub")
)
// 交给 Widget 打开；宿主在退休该来源时 close，诊断可在加载后查询。
```

EpubNavigationParser 优先提取 EPUB 3 `nav epub:type="toc"` 的 ol/li 层级、a/span 标签与容器资源/片段；nav 失败时若存在 NCX 则回退并诊断，取消不触发回退。EPUB 2 NCX 保留 navMap/navPoint 文档顺序，检查唯一 ID、navLabel/content，缺少标签或目标给出诊断。外部或带查询的目标不加载，缺失容器资源保留引用并诊断；xml:base 明确拒绝。无导航提供空树与诊断。章节标题映射以资源路径去重，整文档链接优先于片段标签，不把多个片段伪造成多个 spine 章节。完整树和片段由来源 navigation 暴露，resolveNavigation 转换当前条目的正文位置；BookDescriptor 仍按 spine 排序。

EpubXhtmlParser 通过同一 XML 预算与归档完整性校验提取单章：要求 XHTML html 根及唯一直接 body；正文结构边界输出 TextBlock，h1–h6 输出 HeadingBlock。普通 HTML ASCII 空白折叠，NBSP 保留，内联元素不凭空插入空格，br 保留换行；pre 保留 XML 规范化后的文本空白，但沿用 Widget 字体和布局。空普通块不输出，block-N 依输出顺序生成，UTF-16 偏移指向提取后文本。相同输入规则下 ID 可复现，不保证内容插入后的身份稳定。

图片只保留 alt；脚本、样式、嵌入媒体、外部命名空间元素及 ruby 注音不输出，产生按资源/代码去重的诊断。表格按文本块提取并诊断，不保留网格。CSS、行内样式和链接行为尚不进入正文模型，元素锚点由来源单独映射，统一记录文本呈现范围诊断，不声称完整 EPUB 排版。正文块与诊断列表不可变；已由 EpubTextBookSource 接入公开 BookSource，真实文件与设备行为仍待验证。

## 6. 阅读位置、恢复及页码

EPUB 元数据层 EpubPackageParser 已读取 container.xml 中首个 OPF rendition，验证根命名空间与资源存在；多 rendition 给出内部诊断。支持 OPF package version 2.0/3.0，保存不可变 manifest、spine 顺序、标题、导航路径/NCX 路径和诊断。以 manifest ID 为阅读条目身份，路径由归档引用解析器处理；重复 manifest/spine ID、无效 linear、空阅读顺序/无 linear 条目明确失败。非线性资源保留原 spine 顺序并诊断，不丢弃原文；非 XHTML/不可用条目沿 fallback 查找可读 XHTML，循环、缺失引用和默认超过 64 层失败。导航路径定位由 package 层负责，标签和层级由 EpubNavigationParser 提取。[OPF manifest 与阅读顺序](https://www.w3.org/TR/epub-33/#sec-spine-elem)

EpubXml 使用 Android PullParser、命名空间和 XML 输入编码声明，关闭 DTD 处理，拒绝包含内部声明的 DOCTYPE；常见 XHTML 命名实体为固定替换表，未知实体明确失败。外部 DTD 不加载、不执行书籍提供的实体声明。默认深度 64、元素 100000、文本/属性累计字符 32 MiB、单元素属性不超过 128，每个 token 检查取消。XML 完整解析后沿用归档 CRC/长度校验。远程/带查询的 manifest 资源标为不可用并诊断，不发网络请求；spine 必须获得可用 XHTML fallback。OPF xml:base 暂不支持，检测到明确失败以避免错误相对路径；固定版式只记录文本提取范围诊断。正文与目录解析已具备内部实现，公开来源生命周期已接入，运行行为仍待验证。[Android PullParser 特性](https://developer.android.com/reference/kotlin/android/content/res/XmlResourceParser)

EpubTextBookSource 已接入内部容器层 EpubArchive。可重开输入先复制到宿主缓存目录下的私有 ZIP，原始归档 SHA-256 为快照 revision；默认压缩输入预算 64 MiB、单条目 32 MiB、声明展开总量 256 MiB、最多 10000 条目。扫描拒绝绝对/回退/空路径段、反斜线/控制字符及重复文件身份，不把 ZIP 名称当作落盘路径；只支持 stored/deflated。检查根 mimetype 的内容、大小/存储方法及 META-INF/container.xml 存在；不声称已完成所有 OCF 格式校验。[OCF 容器结构](https://www.w3.org/TR/epub-33/#sec-ocf)

资源读取按快照元数据核对 ZIP 条目，实际展开流检查长度预算和 CRC，解析返回后排空以完成完整性校验；skip 同样经过计数/校验，不支持回绕。解析器借用流，由容器关闭。归档资源引用解析支持相对路径/片段与容器内 .. 归一化，越过根、外部 scheme/authority/query 明确拒绝；不获取远程资源。OwnedSourceFile 统一 TXT/EPUB 私有文件的幂等读租约和关闭后延迟删除，失败/取消释放半成品；仅清理由来源创建的临时文件，不操作宿主原文件。

当前条目总预算依据 ZIP 声明值，实际读取再限制该条目；未访问资源不展开。ZipFile 元数据打开仍有内存开销，限制不等同于总堆内存上限。OPF/spine 解析已接入；导航与正文已具备内部解析实现，公开来源已接入，Demo EPUB 选择与来源类型恢复已接入，设备行为仍待验证；容器基础编译通过不能证明这些能力已交付。

磁盘 TXT 使用 `TextBookOptions.indexDirectory` 指定宿主缓存目录，默认 null 保留内存路径，`maxIndexBytes` 默认 128 MiB。来源扫描严格指定编码，逐块检查取消/关闭及输入预算，逐行写入私有 UTF-8 长度记录；首 BOM、跨缓冲 CRLF/CR、前言、空行、BOM-only 和末尾换行规则与内存路径一致。目录快照仅保留章节起止字节偏移、块数、标题/身份和原始字节 revision。loadChapter 以 RandomAccessFile 读取对应区间，检查记录类型/长度/章节端点和 UTF-8 解码，输出同一 line-N 标识及正文模型。

TextBookSource 实现 AutoCloseable：创建者在不再使用时 close，关闭永久有效，后续加载拒绝。成功目录重载原子安装新索引并淘汰旧文件，失败/取消清理半成品并保留旧成功快照。关闭与安装通过短生命周期锁协调；磁盘读取持有计数租约，关闭/淘汰等待最后读取释放再删除文件，不阻塞等待整章读取结束。关闭不会撤回已经借出的 ChapterContent，也不由 Widget 自动调用；共享来源的宿主必须协调消费者。删除失败记本地诊断，缓存被系统淘汰时章节读取明确失败，可重载来源重新建索引。Demo 为 TXT 指定应用 cacheDir，成功切书后关闭旧来源、Activity 销毁时 release Widget 后关闭所有已创建来源。

当前磁盘路径避免完整输入/整书正文常驻内存，但仍需容纳单行、章节目录及一次请求的完整章节；不是任意巨型单行/单章的恒定内存承诺。索引未跨进程复用，异常终止遗留文件及宿主缓存淘汰策略、长书峰值/性能验收仍待完善。

无障碍正文节点使用标准 NEXT/PREVIOUS_AT_MOVEMENT_GRANULARITY 和粒度/扩展参数，声明字符、词和段落三种粒度。AccessibilityTextTraversal 仅解析当前可见节点文本：字符/词采用平台 BreakIterator，词跳过非字母/数字片段，段落按原文 LF 切分；不声明尚未实现的视觉行/整页粒度。BodyAccessibilityHelper 记录节点游标与固定扩展端点，可见切片指纹/原文范围或选区版本变化后重新初始化，换绑/焦点清理丢弃游标。普通移动清除选区、设置折叠节点游标；扩展通过现有原文范围入口更新 TextSelection，反向回到固定端点时清除选区。

完成移动后报告 TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY 的 action/粒度/fromIndex/toIndex 和选区变化事件，偏移始终相对于该虚拟节点可见文本。回调后再次校验节点切片与实际选区，重入改变页面/范围时不发送旧事件。阅读游标不改变已提交 ReadingPosition，也不发起来源读取。到达节点边界返回 false，由服务或用户另行切换节点/翻页；跨节点自动移动与扩展仍待实现，平台 Unicode 边界表现和事件/焦点顺序仍待设备验证。[Android 节点动作契约](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo)

ReadingStorage 是宿主持久化示例：版本 1 JSON 保存逐书 ReaderSnapshot（锚点、revision、页码提示、模式、可选内置效果）以及最近成功输入的 URI/编码；同一次 SharedPreferences edit 安装快照与最近记录。坏格式、版本/身份不匹配记录忽略，不将输入身份写入 Widget 快照。状态变化经 600ms 合并后导出当前位置，切书/onStop/onSaveInstanceState 再补存；只保存与已成功打开来源匹配的快照，准备中新来源的状态不覆盖旧记录。SharedPreferences.apply 异步写盘，不承诺任意强杀瞬间的数据完整性。

冷启动与手动再次打开按书籍身份使用 restoreSnapshot；Activity 状态重建则直接 openBook，优先已有 View 位置/模式/配置。失败提供重试/从头打开，旧失败对话框受打开序号限制；无效锚点的回退由原恢复规则处理并提示。最近 URI 已失去授权时不会扩大权限，可重新选择或切回示例。当前已提供最多 32 本最近阅读列表、逐书进度、记录移除与URI授权淘汰；ReadingStorage不持久化全文、样式或自定义效果实例。样式与输入偏好由独立ReaderPreferences保存，见后文。

```kotlin
data class ContentAnchor(val blockId: String, val offset: Int)

data class ReadingPosition(
    val bookId: String,
    val chapterId: String,
    val anchor: ContentAnchor?,
    val contentRevision: String?,
    val pageNumberHint: Int?,
    val chapterNumberHint: Int?
)

data class TextRange(
    val bookId: String,
    val chapterId: String,
    val contentRevision: String?,
    val start: ContentAnchor,
    val endExclusive: ContentAnchor
)
```

锚点以块 ID 与原文 offset 确定；revision 用于发现内容变更，不包含字体/屏幕信息。分页位置取正文首个可显示文本锚点；连续滚动位置取正文顶部第一个可见文本行的起始锚点。无文本块的空章节允许 `anchor = null`，用章节 ID 定位其空状态。

恢复顺序：匹配书籍 ID → 查稳定章节 ID → 校验/解析锚点 → 查当前分页映射 → 使用章节页提示 → 在同章节选择有效回退位置。缺失章节时仅打开/重载的明确回退策略可选择书首，普通导航报告失败。`chapterNumberHint` 用于旧来源缺少稳定 ID 的显式兼容，不能覆盖匹配到的稳定章节 ID。

默认来源的块 ID 在相同规范化文本、相同内容版本下稳定；内容编辑后的 ID 保持能力由来源保证。跨版本恢复是尽可能定位的行为，不能承诺任意文本修改都能精确恢复。书签可使用带原因的附近位置回退；高亮必须通过原文快照和范围校验后才绘制，歧义时标为失效。

### 6.1 章节与全书页索引

`ChapterPaginationState` 包含 `Unknown`、`Calculating`、`Ready(pageCount, layoutKey)`、`Failed(error)`。未分页章的页数未知，不返回 0 伪装为已计算。空章节显示一个明确的空状态页，页数为 1；空目录没有阅读位置和可导航页。

`BookPageIndex` 包含每章页数与前缀和、全书 `Long` 总页数、来源目录代次及当前 `layoutKey`。只有所有章在相同版式下分页成功才成为完整索引。

`ReaderState.pageIndex` 使用 `Unavailable`、`Building(completedChapters, chapterCount)`、`Ready(indexSummary)`、`Failed(error)`；`ReaderState.globalPageNumber` 在当前位置之前的章节页数都已知时可提供，否则为 null。全书总页数在完整索引完成前为 null。

`buildBookPageIndex()` 是宿主显式请求，会逐章读取与分页，仅保留有界缓存和轻量页数；长书/远程书籍可能耗时，可取消。默认打开不会自动下载全书。全书跳页仅在完整索引有效时接受；样式、尺寸或目录改变后索引失效，返回 `PageIndexUnavailable`。重新读取的章节若 revision 变化，也必须失效旧索引并校验原请求。

滚动模式的章节页提示由同宽同高的虚拟分页映射产生，表示回分页时的落点；不表示 RecyclerView item 编号。

## 7. 标注与文本选择

```kotlin
data class Bookmark(val id: String, val position: ReadingPosition, val label: String?)
data class Highlight(val id: String, val range: TextRange, val quote: String, val styleId: String)
data class AnnotationSnapshot(val bookId: String, val bookmarks: List<Bookmark>, val highlights: List<Highlight>)
data class TextSelection(val range: TextRange, val text: String)

interface AnnotationIdFactory {
    fun newBookmarkId(): String
    fun newHighlightId(): String
}
```

Widget 维护本次会话中的标注快照。宿主可从数据库传入 `replaceAnnotations`，读取 `exportAnnotations`，或处理增删改事件后保存。导入/导出不约定 JSON、数据库表或网络格式；以上纯数据模型用于宿主自行映射。replace 整批验证书籍 ID/重复标注 ID 后原子替换；来源未加载的范围置为 `PendingValidation`，加载后报告验证结果。

未打开书籍时 annotations/exportAnnotations 返回 null；已经打开且没有标注时返回对应 bookId 的空集合。未打开时增删改标注返回 NoBook，不用空字符串伪造书籍 ID。

`AnnotationStatus` 为 `PendingValidation`、`Valid`、`Invalid(reason)`；失效的记录仍可导出，不绘制到错误文字。`addBookmark`/`addHighlight` 不覆盖同 ID 记录，重复 ID 返回冲突；高亮修改使用 `updateHighlight`。删除不存在的 ID 返回 `Unchanged` 语义的成功结果，便于宿主幂等同步。

本设计建议允许同章跨段落选择与高亮，首版限制单章；跨章节输入返回 `Unsupported`。这是待评审建议，需求文档需明确最终范围。选区是临时 UI 状态，不自动创建高亮；默认工具栏选择“高亮”才调用 `highlightSelection` 并发出变更事件。内置交互 ID 默认 UUID，宿主可替换 `AnnotationIdFactory`。

高亮 `quote` 为原文快照，跨段落的提取在块间插入单个 `\n`，范围 offset 不包含这些合成分隔符。导入时对照相同提取规则验证。`styleId` 映射到 `ReaderStyle.highlightStyles`；未知样式回退默认颜色并报告样式回退。

选区创建后保留其内容版本；版本改变使旧选区失效。滚动自动延伸或翻页选择可在单章范围内实现，跨章边界停止。选区手柄优先消费触摸，选择模式中的点击/滑动不会触发普通翻页。点击已有高亮发出 `onHighlightTapped`，默认工具栏提供删除入口；修改颜色通过高亮更新 API。

## 8. 样式、页面模板与占位

`ReaderStyle` 为不可变配置，`applyStyle(style.copy(...))` 一次提交一组变化。sp/dp 在测量时按当前 density、fontScale 转成 px；Android View 自身 padding 与 `ReaderStyle.page.padding` 各生效一次，分别为控件外部内容边界与阅读页内部边距。

| 配置类型 | 主要字段 |
|---|---|
| `ReaderStyle` | `page`、`body`、`title`、`header`、`footer`、`highlightStyles`、`selectionStyle` |
| `PageStyle` | 背景、`padding`、`viewportPadding` |
| `BodyStyle` | 字体、字号、颜色、`containerPadding`、`contentPadding`、`blockPadding`、字间距 em、首行缩进 em、行距倍率/额外 dp、段距 dp |
| `TitleStyle` | 字体、字号、颜色、padding、字间距、对齐、与正文间距 |
| `ChromeStyle` | visible、高度策略、字体、字号、颜色、背景、padding、对齐、默认内容选择 |
| `HighlightStyle` | stable style ID、填充色及可选装饰 |
| `SelectionStyle` | 选区色、手柄色及尺寸 |

主要配置签名：

```kotlin
data class EdgeInsets(val startDp: Float, val topDp: Float, val endDp: Float, val bottomDp: Float)

data class ReaderStyle(
    val page: PageStyle,
    val body: BodyStyle,
    val title: TitleStyle,
    val header: ChromeStyle,
    val footer: ChromeStyle,
    val highlightStyles: HighlightStyles,
    val selectionStyle: SelectionStyle
)

data class BodyStyle(
    val textSizeSp: Float = 18f,
    val textColor: Int = 0xff302c28.toInt(),
    val font: Typeface? = null,
    val letterSpacingEm: Float = 0f,
    val firstLineIndentEm: Float = 2f,
    val lineSpacingMultiplier: Float = 1.4f,
    val lineSpacingExtraDp: Float = 0f,
    val paragraphSpacingDp: Float = 12f,
    val contentPadding: EdgeInsets = EdgeInsets(),
    val readerFont: ReaderFont? = null,
    val containerPadding: EdgeInsets = EdgeInsets(),
    val blockPadding: EdgeInsets = EdgeInsets()
)
```

其余样式字段使用上表的相同单位和不可变约定；提供 `ReaderStyle.default()` 及各子配置默认值，宿主可从默认样式 copy 修改。文字颜色使用 `@ColorInt`，资源使用对应 `@FontRes/@DrawableRes`；字号、行高及可用尺寸必须有效，非法数值在提交前拒绝。

`ReaderFont` 包含系统 family、字体资源、运行时 Typeface；`ReaderBackground` 包含颜色、Drawable 资源、`DrawableFactory`。运行时字体/工厂是 View 配置，不进入可持久化的阅读位置模型；Drawable 工厂每次返回独立实例，避免一个 Drawable 被多个页面共享修改。

```kotlin
interface ReaderPageFactory {
    fun create(context: Context): ReaderPageTemplate
}

interface ReaderPageTemplate {
    val root: View
    val bodyHost: ViewGroup
    fun bind(context: ReaderPageContext)
    fun unbind()
}

interface ReaderPlaceholderFactory {
    fun create(context: Context, kind: PlaceholderKind): View
    fun bind(view: View, state: PlaceholderState, actions: PlaceholderActions)
    fun unbind(view: View)
}

interface SelectionToolbarFactory {
    fun create(context: Context, selection: TextSelection, actions: SelectionActions): View
    fun unbind(view: View) {}
}
```

`ReaderPageContext` 提供书名、章节、位置/章节页提示、已知页数、时间及呈现模式。默认页眉章节名，页脚章节页号/页数与时间。未知页数显示加载或未知状态。`bodyHost` 是由库插入正文/滚动列表的承载区；模板可自由放置标题之外的装饰与控件，但不自行修改正文页码。

页面 factory 用于替换 View 结构；正常定制仍使用默认分页和正文渲染。若需完全改变正文内容流，继承 `BookPresentation`；若仅新增块类型，实现 `BlockLayoutProvider`。滚动模式页眉/页脚固定，正文承载区插入连续内容流。

工厂须返回独立 View，不能复用已有 parent 的 root；Widget 自己管理 bind/unbind。页面先绑定影响尺寸的内容，再测量 `bodyHost` 得到真实正文视口。自定义页眉/页脚高度变化必须 `requestLayout()`；Widget 检测正文视口变化并重新分页。绑定当前位置页码时须避免不断改变正文可用高度造成布局循环。

`PlaceholderKind` 包含目录加载/失败、章节加载/失败、空目录、空章节和排版失败。`PlaceholderActions` 暴露 `retry()`，由库提供绑定到当前会话/章节的受控动作。失效旧占位按钮不能重新启动前一本书的请求。

`SelectionActions` 提供 `copyText()`、`highlight(styleId)`、`clearSelection()`，均绑定当前选区版本；旧工具栏不能对新书产生标注。默认工具栏用宿主 ID factory 创建高亮，复制只响应读者明确点击。页面 context 另外提供 `requestReaderAction(action)`，供自定义页眉的退出/目录等控件触发已注册宿主 handler。

BookReadView 现提供主线程 `getSelectionActions(): SelectionActions?`，无选区/已释放返回 null；返回动作绑定选区修订号和内容代次。默认工具栏、宿主控件与无障碍操作复用同一动作实现。复制执行前再次借用已显示内容验证原文，剪贴板异常返回 SELECTION/CopyActionFailed，成功后只清除仍属于该动作的选区。高亮 ID factory 属于宿主回调，回调后再次校验绑定，防止重入改变选区后对新范围创建标注。clearSelection 同样检查主线程和绑定有效性。

正文虚拟节点提供本地化 ACTION_LONG_CLICK“选择可见文本”及 ACTION_SET_SELECTION；范围参数为当前虚拟节点文本的 UTF-16 偏移，要求有序、在范围内且原文字符边界有效；折叠范围清除选区并设置节点游标。BlockLayoutFragment 新增默认 null 的 `accessibilityRangeForVerticalRange`，自定义片段需明确提供与朗读文本对应的原文范围才参与选择；内置文本按同一 StaticLayout 的可见行导出文本与范围。执行时重新计算可见范围并校验活动页面/滚动候选、代次及导航空闲，借用内容生成现有 TextSelection，不额外读取来源或提交阅读位置。滚动回收清理动作闭包和片段。节点报告可见部分中的选区相对范围，范围变化刷新节点。

BookReadView 根节点声明 ACTION_COPY 和本地化“高亮选区/清除选区”；只在启用、空闲、选区可见且未拖手柄时提供。节点查询捕获绑定动作，选区变化使缓存失效，旧查询后执行不能处理新选区；高亮使用默认 styleId 和现有 AnnotationIdFactory。节点内字符/词/段落移动已接入；同一正文视图跨节点普通移动已接入，跨节点无障碍扩展及服务焦点体验仍待实现/设备验收，不能据此宣称完整无障碍交付。

## 9. 翻页效果与输入

```kotlin
interface PageTurnEffectFactory {
    val id: String
    fun create(): PageTurnEffect
}

interface PageTurnEffect {
    fun attach(host: PageTurnEffectHost)
    fun supports(direction: TurnDirection, viewport: ReadViewport): Boolean
    fun start(context: PageTurnContext)
    fun update(frame: PageTurnFrame)
    fun draw(canvas: Canvas, frame: PageTurnFrame)
    fun finish(outcome: TurnOutcome)
    fun abort(reason: TurnAbortReason)
    fun detach()
}

interface PageSurface {
    val widthPx: Int
    val heightPx: Int
    fun draw(canvas: Canvas)
}
```

`PageTurnContext` 包含事务 token、当前/目标 `PageSurface`、视口、上一/下一方向及触摸起点；`PageTurnFrame` 包含当前触点、归一化 progress、拖动/提交结算/复位结算阶段。自定义效果通过这些输入定义视觉过渡。

动画时钟、提交/复位决定和最终位置由容器拥有；效果接收初始帧、按显示帧合并的拖动变化与定时收尾帧，不独立修改阅读位置或调用完成回调。释放发生在最后一次合并MOVE绘制前时，容器先提交释放时的准确进度、触点和Settling阶段，再推进收尾时钟；触点插值以该释放位置为固定起点。静止拖动不持续调用update；完整触点变化（含纵向变化）仍参与更新。host.requestRedraw只重绘当前状态，不调用update或推进进度。容器在结算结束后调用 `finish(Committed|Cancelled)`；中断调用 `abort(reason)`，不会再对该事务调用 finish。一个 start 对应一次 finish 或 abort，异常路径也由管理器结束事务并释放资源。



`PageTurnEffectHost` 提供重绘请求与受预算约束的位图租借能力；不提供可写会话或导航对象。`PageSurface` 是事务有效期内只读绘制句柄，效果若要快照，使用租借位图并通过 `draw` 绘制；不长期持有页面 View。自定义 SurfaceView/外部纹理无法可靠快照时拒绝该效果并明确回退。

`PageTurnOptions` 包含动画时长 ms、提交距离比例、速度阈值 dp/s、最小翻页间隔 ms、视觉方向/轴、减少动画选项。容器使用这些统一策略判定手势；效果只确定画面。垂直分页首版配无动画效果；不支持方向的效果返回不兼容结果。


`setPageTurnEffect` 先中断未提交翻页，再验证兼容性并安装新实例。效果/选项设置在中断导航和插件调用后核对配置序号及内容代次；被重入设置、生命周期或内容变化替代的调用返回既有 `ReaderResult.Superseded`，不覆盖较新的选择。工厂 ID 稳定且非空，每次 create 返回独立归属的实例；未采用的实例也须能通过 detach 释放创建/部分绑定资源。滚动中保存分页效果选择并返回 `EffectSelection.DeferredUntilPaged`；不触发模式变化。效果创建/运行失败回退无动画，发出 `onEffectFallback`；运行事务捕获效果实例、安装序号和内容/配置归属，宿主或插件回调后复核，已替代事务沿既有 SupersededNavigation 取消，不使用较新的实例继续旧动画。supports 的 CancellationException 直接传播，不作为方向不支持处理；目标内容已校验且仍有效时可无动画完成原导航，否则保持旧位置并报告失败。

`EffectSelection` 包含 `Active(id)`、`DeferredUntilPaged(id)`、`Fallback(requestedId, appliedId, reason)`。效果配置数值在安装前校验，提交比例取 `(0, 1]`，动画时长/间隔非负，速度阈值非负；减少动画启用时直接使用无动画结算。

`ReaderTapListener.onTap(ReaderTap): Boolean` 使用 Widget 内归一化 x/y 和正文/页眉/页脚区域信息。子控件及选区处理后才进入区域回调；返回 true 表示消费，false 则调用标准 `performClick()`/`OnClickListener`。库不固定“中间打开菜单”的宿主行为。默认分页 ReaderView 可提供左右翻页策略，宿主注册自定义 listener 时完全接管该策略。

键盘默认支持方向键/PageUp/PageDown 的阅读导航；音量键翻页为宿主显式启用项，默认不拦截系统音量。宿主通过 `ReaderActionHandler` 接收目录、设置、退出请求；库不启动宿主 Activity 或弹出应用设置页。

`ReaderActionHandler.onAction(view, action): Boolean` 返回是否已处理，action 为 `ShowTableOfContents`、`ShowSettings`、`ExitReader`。页面控件及无障碍动作由 Widget 将请求转给该 handler；未注册时目录/设置/退出不声明为可执行动作。翻页等能力只有在对应边界和状态允许时才声明可用。

实际接口位于 api.event，由 BaseReadView 提供 `setReaderActionHandler` 与同步 `requestReaderAction`，view 参数为 `BaseReadView<*>`，泛型呈现也能复用。调用限定主线程；禁用、已释放或未注册返回 false，handler 返回值直接表示处理结果，异常记录本地诊断并返回 false。宿主动作独立于翻页忙碌、边界和选区，退出/设置可在准备中请求；库不自动导航、关闭书籍或保存进度。handler 在 release 时清除，不序列化，由宿主重建注册。注册变化通知无障碍节点刷新；三个本地化自定义动作仅在启用且注册时声明，执行重新检查当前 handler。

ReaderPageContext 追加默认返回 false 的 `requestReaderAction: (ReaderAction) -> Boolean`，旧页面工厂仍可使用默认值。内置呈现为绑定后上下文提供入口，检查活动模板/候选、代次及当前已提交位置；预排版上下文、隐藏候选与旧页返回 false。滚动每次绑定捕获该次位置，旧位置闭包不会操作当前窗口。页面模板应在 unbind 中清除绑定上下文。Demo 的目录按钮、中间点击及设置按钮共用同一 handler；无障碍退出由 Activity 处理。完整设备行为仍待验证。

## 10. 状态与事件

`ReaderState` 是主线程可读取的不可变快照：`bookId`、会话阶段、目录状态、当前章加载/排版状态、当前位置、呈现模式、导航忙状态、全书索引摘要、可执行能力和最近可恢复错误。

其中 `ReaderCapabilities` 提供 supportedModes、文本选择/标注支持、当前动作可用性和效果适用性。自定义呈现不支持文字命中时明确禁用选区 UI 并拒绝相关操作；能力关闭不会删除宿主已导入的标注。未知加载状态不等同于已到书尾。

会话阶段为 `Closed`、`LoadingBook`、`LoadingChapter`、`AwaitingViewport`、`LayingOut`、`Ready`、`Empty`、`Error`、`Released`。邻章失败不将正在阅读的主状态改为 Error，使用该章状态与章节事件表达。`ChapterState` 包含 `Unloaded`、`Loading`、`Loaded(revision)`、`Failed(error)`；Loaded 仅表示内容就绪，分页另行报告。

```kotlin
interface ReaderListener {
    fun onStateChanged(view: BookReadView, state: ReaderState) {}
    fun onBookChanged(view: BookReadView, book: BookDescriptor?) {}
    fun onChapterStateChanged(view: BookReadView, chapterId: String, state: ChapterState) {}
    fun onPaginationChanged(view: BookReadView, chapterId: String, state: ChapterPaginationState) {}
    fun onPositionChanged(view: BookReadView, event: PositionChange) {}
    fun onSelectionChanged(view: BookReadView, selection: TextSelection?) {}
    fun onAnnotationsChanged(view: BookReadView, change: AnnotationChange) {}
    fun onAnnotationStatusChanged(view: BookReadView, id: String, status: AnnotationStatus) {}
    fun onHighlightTapped(view: BookReadView, highlight: Highlight) {}
    fun onEffectFallback(view: BaseReadView<*>, fallback: EffectFallback) {}
    fun onError(view: BookReadView, error: ReaderError) {}
}
```

位置事件包含 from/to 和 `PositionChangeReason`：`Open`、`UserNavigation`、`ProgrammaticNavigation`、`Scroll`、`Restore`、`Relayout`、`ModeChange`、`ContentUpdate`。排版只改变派生页码而语义锚点相同时，发分页/状态事件；若实际锚点产生回退，发带真实原因的位置事件。纯动画进度与取消不发位置变更。

标注事件包含 Add/Update/Remove/Replace、旧/新数据和 `AnnotationChangeOrigin`（`User`、`HostApi`、`HostReplace`）。宿主替换同一快照不产生重复事件；`HostReplace` 供更新 UI，持久化监听可忽略，避免导入后回写形成循环。多个监听器按注册顺序收到相同快照；回调中移除监听器安全，回调中的新导航排到本次事件发布之后。

导航提交顺序固定：验证会话及布局代次 → 显示最终内容 → 更新状态快照 → 位置事件 → 状态事件 → 完成当前请求。旧会话结果不得发新的阅读事件。连续滚动按显示帧合并可见锚点更新，不逐像素发布事件；停止滚动时强制刷新最终锚点。

## 11. 生命周期、快照与 XML

Widget 在首次使用时建立自己拥有的任务作用域；首次创建且未测量时可接受 open/configuration，加载状态为 `AwaitingViewport`，open 完成要等待有效正文视口。调用方取消或 close 可结束等待。

`onDetachedFromWindow` 取消未完成操作、停止动画/时间刷新、释放 View/效果暂存资源，保留书籍来源引用、已提交位置、样式和标注快照。重新 attach 创建新的任务代次并按位置重建；不重放已取消的调用。初次尚未 attach 与已发生 detach 分开处理。

`closeBook` 清除书籍会话，可再次 open；`release` 幂等且永久结束操作权限，先清监听器及宿主动作引用，再逐项尝试取消任务、子类会话清理、导航重置、适配器/效果解绑。普通Exception不阻止后续步骤，最后向调用方抛首个异常并附加其余suppressed原因；致命Error立即传播，不承诺其后步骤继续。书籍层先设置Released/结束打开归属，再独立尝试时钟与协调器清理。之后其他业务操作返回 Released。仅凭 detach 不推断宿主页面永久销毁；Fragment `onDestroyView` 中可显式 release。

`ReaderSnapshot` 只含格式版本、书籍 ID、已提交 `ReadingPosition`、模式及内置效果 ID。来源对象、全文、图片、标注集合、运行时 Typeface/Drawable 工厂和位图不放入快照。宿主分别恢复来源、样式与标注，再恢复位置。自定义效果由宿主重新安装。

`onSaveInstanceState/onRestoreInstanceState` 使用 `View.BaseSavedState` 保存以上轻量快照；Widget 有唯一 `android:id` 才参与正常 View 状态恢复。恢复状态不自动重建来源，只暂存匹配 bookId 的位置。显式 open 初始位置/restoreSnapshot 优先于 View 暂存状态；不匹配的书籍丢弃暂存状态并报告恢复不适用。[Android View 状态保存](https://developer.android.com/topic/libraries/architecture/views/saving-states-views)

XML 示例：

```xml
<org.peyilo.ireadview.widget.ReaderView
    android:id="@+id/reader"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:ireadReadingMode="paged"
    app:ireadPageTurnEffect="slide"
    app:ireadPagingAxis="horizontal"
    app:ireadGesturePagingEnabled="true"
    app:ireadVolumeKeyPagingEnabled="false"
    app:ireadMinimumTurnIntervalMs="250"
    app:ireadBodyTextSize="18sp"
    app:ireadBodyTextColor="@color/reader_text"
    app:ireadPageBackground="@color/reader_background"
    app:ireadHeaderVisible="true"
    app:ireadFooterVisible="true" />
```

XML 只配置可静态表达的默认样式/模式/内置效果；书籍来源、标注、复杂工厂和监听器用代码。属性优先级为运行时 setter → XML → defStyleAttr → defStyleRes → 库默认值。不在 XML 直接给文件路径并同步读书。默认 Widget 管理内部 children；宿主覆盖层应放在外部父布局中。

输入与效果配置现已落地 XML：ireadGesturePagingEnabled、ireadTapPagingEnabled、ireadKeyboardPagingEnabled、ireadVolumeKeyPagingEnabled、ireadPagingAxis（horizontal/vertical）、ireadTapEdgeFraction、ireadTurnDurationMs、ireadReduceMotion、ireadCommitDistanceFraction、ireadFlingVelocityDpPerSecond、ireadMinimumTurnIntervalMs、ireadPageTurnEffect（六个内置枚举）。单位与代码 API 一致，时长/间隔整数为 ms，速度 float 为 dp/s，比例无单位；位图预算仍由代码配置。无效比例、负时长/间隔、未知轴/效果在构造时拒绝，不静默夹紧。obtainStyledAttributes 自动处理主题优先级，finally 归还 TypedArray；构造时初始化配置不调用子类的呈现兼容性钩子。

通用 BaseReadView 的 ConfigurationSavedState 保存版本化轻量配置 Bundle，书籍层 SavedState 在其 superState 上保存模式/位置，不重复序列化配置。输入与动画参数分组恢复，缺失/未知版本/无效组保留 XML 默认；PageTurnEffects.fromId 仅解析六个内置 ID，未知 ID 返回 null。保存内置标识根据库工厂类型识别，自定义工厂即使使用相同 ID 也不按内置效果重建。自定义效果和来源仍由宿主提供。恢复优先级按组为显式运行时 setter（即使值与默认相同）→ 有效 SavedState → XML/主题/库默认；恢复后再次 setter 仍立即生效。计时器、速度采样、半途手势、页面/位图和 factory 实例不保存。状态类保持 Android 可读取的 CREATOR；目前仅有编译记录，进程重建与 Parcel 往返仍待验证。

ReaderSnapshot 已实现 bookId/position/modeId/formatVersion/builtInEffectId；效果字段为格式 1 的可选追加字段，默认 null，已有位置构造参数顺序不变。导出仅记录真实内置工厂 ID，自定义工厂导出 null，输入/动画参数仍由 View SavedState 或宿主配置保存。restoreSnapshot 先验证 Released、书籍 ID、模式和可选效果 ID；未知效果返回 EFFECT/UnsupportedSnapshotEffect，尚未关闭旧会话。null 保留宿主当前效果，非空 ID 通过公开 setter 明确安装（滚动中保留到分页），再按同一打开入口恢复位置。显式恢复的模式/效果设置优先于随后到达的 View SavedState；显式设置相同模式也保留此优先级。close/open/模式事件后校验打开请求序号，监听器开启新会话或 release 后旧恢复返回 Superseded，不继续清理或覆盖新会话。宿主显式保存的阅读快照与系统 View 配置恢复分开，不把全部输入选项并入阅读位置模型。

## 12. 内容呈现与内容块 SPI

```kotlin
interface ReadContentAdapter<L : Any> {
    val contentView: View
    fun attach(host: ReadContentHost<L>)
    fun onViewportChanged(viewport: ReadViewport)
    suspend fun prepare(target: ReadNavigation<L>): ReadPreparation<L>
    suspend fun show(candidate: PreparedRead<L>): L
    fun detach()
}

interface PreparedRead<L : Any> {
    val target: L
    val generation: Long
    val turnSurfaces: PageTurnSurfaces?        // 无离散页面时为 null
    fun dispose()
}

abstract class BookPresentation : ReadContentAdapter<ReadingPosition>
```

`ReadPreparation<L>` 为 `Ready(candidate)`、`Unchanged(location)`、`Rejected(reason)` 或 `Failed(error)`。prepare 可以异步加载/排版、预建目标页面，但不能改变当前阅读位置。show 在主线程发起最终显示，可挂起等待 View 布局，并返回实际落点；不发位置事件，基类校验后统一提交。show 被取消或失败时须恢复最后已提交内容，回滚也须校验代次。dispose 对prepare返回的Ready候选在提交/取消/过期之后调用一次，释放候选专用资源，不销毁已转移给当前页面的资源。普通Exception保留原导航结果，经既有onError报告NAVIGATION阶段的CandidateDisposeFailed（retryable=false、cause原异常）；诊断延后且按事务token/内容代次逐监听核对。致命Error继续传播，finally仍解除属于该事务的pending/Busy状态。

`ReadContentHost<L>` 提供 `generation`、当前视口、受控 `requestNavigation` 和 `reportVisiblePosition(location, generation)`。滚动呈现只能在匹配当前代次并且没有未完成导航提交时报告可见位置；程序滚动由事务协调器抑制中间报告。宿主自定义实现须满足可见位置属于实际显示内容、导航目标合法及单次提交的 SPI 契约。

`BookPresentationHost` 在上述契约上提供目录/样式/标注只读快照、`getChapter(chapterId)`、`layoutChapter(chapterId, viewport)`、加载/排版状态及错误报告能力。它是受控门面，不暴露内部 `ReaderCoordinator`、Job、缓存 Map 或持久化能力。`BookReadView` 通过 createPresentation 传入，子类无需自行创建会话。

主要 SPI 签名：

```kotlin
interface ReadContentHost<L : Any> {
    val generation: Long
    val viewport: ReadViewport
    val committedLocation: L?
    suspend fun requestNavigation(target: ReadNavigation<L>): NavigationResult<L>
    fun reportVisiblePosition(location: L, generation: Long): ReaderResult<Unit>
    fun reportError(error: ReaderError)
}

interface BookPresentationHost : ReadContentHost<ReadingPosition> {
    val book: BookDescriptor?
    val style: ReaderStyle
    val annotations: AnnotationSnapshot?
    suspend fun getChapter(chapterId: String): ReaderResult<ChapterContent>
    suspend fun layoutChapter(chapterId: String, viewport: ReadViewport): ReaderResult<ChapterLayout>
}

interface ChapterLayout {
    val chapterId: String
    val contentRevision: String?
    val layoutKey: String
    val pageCount: Int
    fun pageForAnchor(anchor: ContentAnchor): ReaderResult<Int>
    fun anchorForPage(pageNumber: Int): ReaderResult<ContentAnchor?>
    fun fragmentsForPage(pageNumber: Int): ReaderResult<List<BlockLayoutFragment>>
}

interface BlockLayoutProvider {
    val id: String
    val version: String
    fun supports(block: ContentBlock): Boolean
    fun measure(block: ContentBlock, context: BlockLayoutContext): BlockLayout
}
```

`ReadViewport` 提供控件/正文区域 px 与 density/fontScale/layoutDirection；传给 layoutChapter 的正文几何必须与实际 bodyHost 相同。`BlockLayoutContext` 提供宽高限制 px、已解析文字样式、内容版本和取消检查能力。`BlockLayoutFragment` 提供块 ID、原文范围（非文本为空）、片段高度及绘制/命中句柄。上述派生对象仅在内容/布局代次内有效；查询无效页/锚点返回明确错误。

`layoutChapter` 返回 SPI 的只读 `ChapterLayout`：内容版本/布局键、页数、页与锚点互查、可绘制块片段；由内置排版服务构建，宿主呈现可复用。默认分页和滚动共享同一份文本布局/选择几何，避免显示与命中测试使用不同断行。

`BlockLayoutProvider` 按内容类型注册，提供稳定 `id`、`version`、`supports(block)`、`measure(block, BlockLayoutContext): BlockLayout`。measure 在计算线程执行，不能创建/操作 View。`BlockLayout` 提供单位高度及可绘制不可变片段；`BlockLayoutFragment` 可选提供字符命中、选区路径和插入点几何。draw 在主线程执行。自定义非文本块可被定位但不参与文本选择；单块超过视口时，provider 必须提供可分割片段或明确的整体显示策略。

自定义片段可以提供无障碍文本；`accessibilityDescribesWholeBlock` 默认 false。只有每个可访问垂直切片的描述均覆盖整个逻辑块时才设 true：普通粒度完成描述后可越过该块的剩余片段。局部文字描述应提供精确源范围，不能以整块声明掩盖未读内容。该声明不提供源 offset，也不启用文字选区。Demo 分隔块展示了完整图形描述的接入。

页面模板、块布局和呈现都可扩展，但默认配置中不公开内部 Page/Adapter，也不让宿主保存它们作为进度。SPI 派生布局对象仅在其内容/版式版本有效期内可使用。

## 13. API 与需求覆盖

| 需求 | 公开入口 | 实现归属 |
|---|---|---|
| FR-EXT-01～04 | BaseReadView、BookReadView、BookPresentation | Widget 基类 + 呈现 SPI |
| FR-BOOK-01～04、07～08 | BookSource、目录/内容模型、内置来源 | 来源适配 + 内容校验 |
| FR-BOOK-05～06、09 | LoadingOptions、章节状态、retry/invalidate/reload | 请求合并、预加载、代次失效 |
| FR-VIEW-01～04 | 页面/占位 factory、style、视口 SPI | 页面测量、正文布局、重排 |
| FR-NAV-01～07 | 导航方法、ReadingPosition、全书页索引 | 导航事务 + 锚点/页码映射 |
| FR-MARK-01～08 | 标注 API、TextRange、选择/标注事件 | 标注仓库 + 选区几何 |
| FR-MODE-01～05 | ReadingMode、setReadingMode | 独立分页/滚动呈现 |
| FR-FLIP-01～09 | PageTurnEffect、PageTurnOptions、tap listener | 手势路由 + 效果管理器 |
| FR-STYLE-01～09 | ReaderStyle、页面 factory、块/呈现 SPI | 样式差异判定 + 重新排版 |
| FR-DEMO-01～07 | 宿主示例、ReaderActionHandler、全部公开 API | Demo 使用公开接入面 |

## 14. 评审时需要明确的范围

本设计采用可实施的默认建议：原生 Android View Widget；当前工程 minSdk 21；默认单页；宿主持久化；标注同章跨段落；全书准确页数为显式计算。minSdk 21 来自当前工程配置，性能目标仍未确定。

仍须产品决定：默认图片支持与 EPUB 提取细节、正式双页模式、是否调整效果清单、跨章节选择、目标设备/性能门槛，以及是否提供可选磁盘存储适配器。上述增强不阻碍本文中的默认文字阅读 API，但确定后可能补充能力类型。首版不提前公开未实现的图片/双页 setter。

本次交付是 API 及实现方法设计；实际落地前需按本文评审类名、方法语义、SPI 边界和未决范围。后续改动应同时更新需求、本文与实现方案，避免不同文档对同一方法给出矛盾定义。

## 15. 实现中的接口细化

方向能力现在使用 NavigationAvailability（Available、AtBoundary、Unknown、NotReady、Busy、Released）和 ReadNavigationCapabilities(previous,next)，BaseReadView.navigationCapabilities 为主线程只读查询；ReaderState 增加 navigationState 和 navigationCapabilities。查询是观察值，不占有目标、不开 IO/排版、不替代导航边界校验；Available 表示现有几何允许尝试，不保证来源读取必定成功。最小间隔、选区输入优先级和 force 仍由实际请求决定，不混入目录/页面的结构性边界。

ReadContentAdapter.navigationAvailability(target) 默认 Unknown，宿主自定义容器可渐进接入；内置分页检查已显示候选的实际页数/页号和目录邻章，连续滚动检查 RecyclerView.canScrollVertically 与窗口外目录邻章，列表布局未完成时返回 Unknown，避免用虚拟页提示错误推断书尾。ReadContentHost.reportNavigationCapabilitiesChanged(generation) 允许几何变化而锚点未变时报告；通知按代次/值去重，通过通用 onNavigationCapabilitiesChanged 和 Book 的 onStateChanged 输出，滚动沿用每帧位置调度。失效代次不广播，SPI 查询异常记录本地诊断并返回 Unknown，仍保留程序导航入口。

无障碍前后页动作及 Demo 按钮消费同一份能力：Available/Unknown 可尝试，已知边界/未就绪/忙/释放禁用；无障碍执行时再次查询，防止服务使用旧动作。位置仍仅由 NavigationController 提交，能力查询/通知不增加位置事件。滚动先报告本帧实际锚点，再通知能力，通知后重新检查活动候选/代次/忙状态，防止监听器重入新会话后继续绑定旧页眉或加载旧边界。

Canvas 正文的初步无障碍实现是 ReaderBodyView 内部 BodyAccessibilityHelper，使用 AndroidX ExploreByTouchHelper。BlockLayoutFragment 增加默认可选 accessibilityText、accessibilityHeading 和 accessibilityTextForVerticalRange(topPx,bottomPx)，坐标是片段局部 px；默认文本按 Layout 原始行范围取当前可见文本，标题保留 heading。分页和滚动共用，不重新排版/读取章节；自定义块返回 null 表示没有可朗读文本，图形块也可提供整体标签。屏幕节点边界按正文 padding、片段位置与祖先可见矩形相交；不暴露隐藏候选和未出现的列表行。

换绑定清理虚拟焦点并更新 ID，滚动按帧刷新可见行/几何，解绑移除观察器和待执行回调。旧 ID 查询返回禁用不可见节点，自定义语义异常跳过该片段并记录一次本地诊断。BaseReadView 的 ACTION_SCROLL_FORWARD/BACKWARD 用本地化前后页标签进入同一导航，仍受 Busy/边界/最小间隔约束；禁用/无位置/忙/选区接管时不执行。内置呈现现在通过通用 SPI 查询实际方向能力，已知 AtBoundary/NotReady/Busy/Released 时不声明或执行动作；自定义呈现默认 Unknown，可由准备最终校验。目录/设置/退出已接入宿主动作分发；可见文本选择与根节点复制/高亮/清除选区已接入，节点内字符/词/段落移动及同一正文视图跨节点普通移动已实现，跨滚动行移动、跨节点扩展与设备焦点验收尚未完成，不将初步节点实现称为完整无障碍交付。

翻页效果的第一条实现链路已落地：公开 api.effect 中的 PageTurnEffectFactory/PageTurnEffect、只读 PageSurface、PageTurnContext/Frame、EffectSelection 及 BitmapLease；BaseReadView 持有 PageTurnEffectManager，给现有 Adapter 包装动画 show，不新增位置提交源。PreparedRead.surface 和 ReadContentAdapter.currentSurface 默认 null；分页实现导出已测量模板的只读绘制句柄，候选高亮/选区在动画前准备。SurfaceView/TextureView 模板不导出句柄，选择动画时明确回退无动画。


PageTurnOptions 当前实际字段为 durationMs、reduceMotion、bitmapBudgetBytes、commitDistanceFraction、flingVelocityDpPerSecond、minimumTurnIntervalMs，默认 240ms、false、16MiB、0.33、600dp/s、0ms。手势开关已统一迁到 ReaderInputOptions，比例/速度适用于选定轴。效果仅消费帧/画面，不决定提交。时钟使用可取消的显示帧等待，进度采用平滑插值；最终 Adapter 显示成功后结束效果并由原导航控制器提交位置。取消中止效果、移除帧回调/覆盖层并释放租借，原页保持可见。替代开始前先终止旧效果，防止旧 abort 清除新 start 的上下文。一次 start 对应一次 finish 或 abort；attach/detach 也有独立状态保护。

finish/abort/detach 清理异常独立通过既有 onError 报告，ReaderStage 为 EFFECT，稳定错误码分别为 EffectFinishFailed、EffectAbortFailed、EffectDetachFailed；retryable=false，cause 保留原始异常，message 包含效果 ID 和可取得的事务 token。终止/解绑标记先于插件调用，异常不能重复调用同一终止或阻止后续租借归还/池回收。finish/abort 失败淘汰故障实例并回退 none；detach 失败仍完成效果替换或解绑，不把已完成的正文显示变成导航失败。清理前捕获内容/配置归属，失败后结合安装序号复核，过期错误仅保留日志。允许的报告排到清理后，再按内容代次和效果配置序号抑制过期监听事件；进入子类钩子前、钩子返回后及两层每位监听器前复核。两层监听沿既有路径接收，不再次提交位置。BookReadView 记录最近错误并发布状态，不改变当前会话阶段。release 后监听所有权结束，仍保留本地日志诊断；库不将正文拼入诊断消息，cause 内容由插件提供。

横向触摸超过系统 touchSlop 且水平位移占优时锁定本次前进/后退方向，进入统一 prepare；准备期间保存最新输入，候选有效后才发 Dragging。进度为沿锁定方向位移/完整 Widget 内容视口宽度，夹紧到 0..1；RTL 映射目标方向，触点使用扣除 Widget padding 的视口坐标。VelocityTracker 以 px/s 采样，dp/s 阈值乘 density。松手距离达到比例或同向速度达到阈值且位移超过 slop 则提交；快速反向松手优先复位。结算沿剩余进度缩短时长，平滑插值回到 0 或 1，角部纵向位移同时回归起点。ACTION_CANCEL/多指进入要求复位；准备尚未完成则直接取消准备，不在迟到候选上继续翻页。

内部 PageTurnGesture 只传递主线程触点、进度和一次性松手决定，不持有页面或位置。正常复位调用 finish(Cancelled)，以内部 NavigationReset 返回 NavigationResult.Unchanged(原位置)，不调用候选 show、不写位置、不发位置事件；被更新/强制导航/公开 cancelTransition 等替代则 abort 并取消任务。none、reduceMotion、零时长或画面不兼容时仍等待松手决定，再直接显示或复位。效果运行失败立即终止并归还租借，仍等待输入决定，不能自动提交目标。忙时新触摸不排队，边界准备拒绝后同一触摸不反复重试。选区/长按仍优先。ReaderInputOptions 现在统一持有输入开关和分页轴，不在 PageTurnOptions 重复保留开关。

位图租借只在效果事务内可用，尺寸和总分配受预算约束；空闲位图可复用，超预算先淘汰空闲资源，归还/终止不会重复释放，解绑回收池。借出前清空像素；借用者不能自行 recycle/reconfigure，也不能在归还后访问租借。none/slide/cover/iBookSlide 直接绘制只读 Surface；两种仿真共享 SnapshotCurlEffect，同时租借并捕获翻动纸面与底页；两张图计入同一预算，第二张失败先归还第一张再降低尺寸。快照按宽高的 100%、75%、50%、25% 尝试租借，低分辨率纹理映射回完整视口；全部失败报告 ResourcesUnavailable 并回退 none，最终显示仍通过原导航事务。网格数组复用，不在每帧创建位图；角部几何仍有小对象分配，性能待验证。[Canvas.drawBitmapMesh](https://developer.android.com/reference/android/graphics/Canvas) 不支持网格抗锯齿，缩放纹理使用过滤。创建、attach、兼容性或运行失败通过 onEffectFallback 报告。

BaseReadView 提供 setPageTurnEffect/setPageTurnOptions、pageTurnEffectId/pageTurnOptions。BookReadView 在滚动中返回 DeferredUntilPaged，保留选择并在回分页后使用，不改变模式。打开、恢复、重排、内容更新及模式切换直接显示；普通用户/程序导航才使用效果，同一已提交位置不重复动画。Demo 的“效果”入口提供六项预设、减少动画和输入设置。当前仅有 Debug 构建记录，效果外观、取消/插件异常、位图预算与真实窗口表现仍待设备验证。

输入实际接口是 api.input.ReaderInputOptions、PagingAxis.Horizontal/Vertical，以及 BaseReadView.inputOptions/setInputOptions（返回 ReaderResult<Unit>）。字段 gesturePagingEnabled/tapPagingEnabled/keyboardPagingEnabled 默认 true，volumeKeyPagingEnabled 默认 false，pagingAxis 默认 Horizontal，tapEdgeFraction 默认 0.3 且须为 0..0.5 内正值。改变输入配置取消本次触摸任务，不中断独立程序导航。垂直手势按高度计算进度、上滑前进/下滑后退、无动画；仍在候选准备后等待松手，不提前显示。它不切换 ReadingMode，连续滚动保留自己的手势处理，按钮/键盘导航沿用滚动呈现的视口移动。

点击顺序是内容/高亮/选区 → ReaderTapListener → 可选默认边缘翻页 → performClick。归一化触点扣除 Widget padding；横向两边根据 RTL 映射上一页/下一页，纵向用上下两边。ReaderTapListener 返回 true 阻止后续处理，返回 false 时边缘可自动导航；中间继续常规点击，宿主自行显示菜单。回调改变代次或释放 Widget 后不继续默认处理。Demo 只消费中间目录点击，边缘由库处理。

Widget 可获得焦点，未被正文子控件消费的正文按下请求阅读焦点。硬件按键只在 Widget 本身有焦点、当前内容可用且没有选区接管时处理：PageDown/Down/Space 前进，PageUp/Up/Shift+Space 后退，Left/Right 按 RTL 映射；音量 Down/Up 可选前进/后退。Alt/Ctrl/Meta 组合及未知按键交给父类；已处理的按下/松开配对消费，长按重复不排队翻页，失焦/解绑清空按键记录。未捕获 Activity 或输入框事件，也不承诺软键盘发送 KeyEvent。[Android 硬件键盘事件](https://developer.android.com/develop/ui/views/touch-and-input/keyboard-input/commands)

minimumTurnIntervalMs 从上次 UserNavigation/ProgrammaticNavigation 的位置提交结束计时，默认 0 不限制；普通导航忙时仍先返回 Busy，间隔未到返回 Throttled，不启动来源读取/动画。NavigationOptions.respectInterval 默认 true；false 仅跳过间隔，force=true 同时替代未完成请求并跳过间隔，仍检查目标边界。打开/恢复/重排不受普通翻页间隔影响，内容代次变化清空时间记录；未移动/复位不重新计时。同章选区扩展显式 respectInterval=false，保持既有停留策略。

全书索引现已落地：BookPageIndex 只保存 IndexedChapter（章节 ID、页数、正文版本、章节布局键、内容指纹）和不可修改的 Long 页数前缀；positionForPage 二分映射全书页，pageForPosition 返回可匹配的全书页。BookPageIndexSummary 提供总页数、目录安装代次及索引布局键。ReaderState 的 pageIndex/globalPageNumber/totalPageCount 与 onBookPageIndexChanged 输出计算进度与完整结果；未完成时总页数仍为 null，已知章节前缀可提供当前位置的全书页号。

BookPageIndexBuilder 是独立轻量服务，合并同输入的构建消费者；最后消费者取消时停止工作。逐章借用现有内容仓库及分页结果，完成一章后只留下轻量记录，不持有全书布局。可见导航期间暂缓开始下一章，并在章间让出执行；已经开始的来源读取不会被强制抢占。后台布局不取消正常邻章预加载；共享布局任务也增加消费者计数，避免索引取消停止可见调用仍在使用的布局。完整目录安装/关闭、内容失效、版式/正文视口变化会使索引失效；目录读取失败不改变安装代次。色表/选区绘制配置不改变索引输入。

正文在来源读取的工作线程计算分块 UTF-16 SHA-256 指纹，覆盖文本块 ID、类型、原文及标题级别；后续加载先核对索引，再发 Loaded 事件，避免监听器在旧索引上重入导航。文本即使未更新 revision，原文变化也会使索引失效。自定义块当前无法通用序列化，指纹为 null；无 revision 的自定义块重新读取时保守失效，来源应提供可靠版本。缓存中的内容只有显式失效或被重新读取后才能发现来源变化，库不假装实时监控外部文件。

buildBookPageIndex 使用呈现实际测量的正文视口（layoutViewport SPI），不会把 Widget 外尺寸当正文尺寸。goToGlobalPage 先核对目标章当前内容，再走唯一导航事务；期间索引失效取消该次导航并返回 PageIndexUnavailable。外部用户取消仍是取消，不伪装成域拒绝。模式切换若正文视口相同可保留索引，否则清派生分页状态，避免混合不同版式的章节页数。Demo 目录中的“全书页”提供计算进度、取消及有效 Long 页号跳转。当前仅通过 Debug 构建，运行与性能行为待验证。

模式与连续流现已落地：ReadingMode 是按 id 比较的不可变值对象，内置 paged/continuous_scroll，并允许 custom(id)。BookReadView 的模式候选包装器在原内容之上准备隐藏呈现，仅在 show 成功和代次有效后更新 readingMode 并移除旧呈现；新内容准备失败、调用取消或代次被替代会释放候选。没有单独的“滚动当前位置”存储，滚动可见位置仍经 NavigationController 提交。模式事件单独发出，因此语义位置相同的切换也能被宿主识别。

ChapterLayout.continuousFragments(): ReaderResult<List<ContinuousFragment>> 给出有界内容片段及 spacingAfterPx，默认 Unsupported；内置分页器一次测量后同时生成分页与连续流引用，不重复测量文字。连续列表不加入分页底部空白，并保留跨虚拟页边界的段落间距。BlockLayoutFragment.anchorForVerticalOffset(yPx) 返回可见行起始锚点，文字实现按 Layout 行信息计算，避免用横向命中推断 RTL 行首；缺少行几何的自定义片段默认返回块片段起始位置。

ScrollingBookPresentation 用 RecyclerView + LinearLayoutManager 承载连续流，在已显示章前后按需加载，最多保留三个章的布局窗口；加载/失败边界有独立 item，失败点击重试。追加或前插内容时保存当前可见章与正文偏移，再映射回新的列表项，目录项 ID 与 item 编号不混用。程序 next/previous 计算约一个正文视口的偏移，并提前加载需要进入的章节；First/Last 和锚点/页提示跳转共用准备过程。列表项高度使用向上取整的像素高度，位置映射使用相同高度前缀与二分查询；原文 offset 仍来自共享文字 Layout。

滚动按帧合并位置报告，空闲后刷新最终位置及选区工具栏几何；导航准备期间停止惯性滚动并屏蔽中间报告。显示位置取顶部第一条可见正文行，边界状态不生成文字锚点；书尾按实际可滚动范围夹紧，请求文字仍保持可见，报告实际顶部行。ReaderPageContext 现在携带 readingMode，滚动模板页眉/页脚固定。XML ireadReadingMode、ReaderSnapshot.modeId 与 SavedState 保留模式；快照恢复不支持的模式明确失败，控件状态中不支持的模式保留子类默认模式。

滚动高亮与选区复用 ReaderBodyView；手柄可在同章 item 间拖动，在 28dp 边缘区域停留 350ms 后自动延伸。速度随进入深度变化，最高 420dp/s，按显示帧推进，帧间隔夹到 40ms 防止暂停后大幅跳动；取消移除尚未执行的帧回调。可见片段反推章节在列表中的原点，位移限制在章节首尾；缩小范围时额外限制固定端点与指尖距离，原文范围仍经 TextSelectionResolver 校验。拖动暂停邻章窗口拼接，松手后恢复。选区或内容代次失效、导航、视口变化和解绑停止任务。

装饰层按帧合并刷新，只更新可见正文路径，不重绑整个列表。自然 Scroll 中仍可见且版本有效的选区保留，离开可见区域后清除；普通程序导航到别章仍清除。当前章节窗口限制数量，不等同于严格显示布局字节预算；超大章切片加载与设备性能验收仍待推进。[RecyclerView 1.4.0](https://developer.android.com/jetpack/androidx/releases/recyclerview) 是本轮稳定依赖。上述实现仅完成 Debug 构建，不代表已通过跨章、RTL 或窗口交互验收。

第一轮采用通用导航事务持有唯一位置，书籍协调器只管理来源、加载及派生布局。呈现工厂现已采用 `supportedModes` 与 `createPresentation(mode, host)`；ReaderView 装配独立分页和连续滚动实现。页面模板实际测量后才能分页；布局等待覆盖尺寸不变的重新布局，尺寸变化后的目标页装配排到当前布局结束之后。

内容块布局 SPI 已细化为 `BlockLayout.units: List<BlockLayoutUnit>` 和 `fragment(firstUnitIndex, endUnitExclusive)`，提供整行/整块单位供分页器装配。片段包含 block ID、原文 offset、height/left/top px 和 draw；分页器合并同块相邻行，避免按字符重新布局或每行重绘整段。自定义 provider 必须输出正高度、稳定块 ID 与可用于正文视口的合法片段。

当前样式使用实际实现的不可变字段，运行时字体为 Typeface。完整 ReaderFont/背景表达、其他 padding 层级及绘制差异优化在相应功能实施时加入。尚未实现的动画不公开成功接口；目标接口与已实现 API 的对应由 06 记录。

章节更新已改为按章清理内容、布局任务及分页状态。当前章先尝试忽略旧内容版本恢复原锚点；锚点或页提示无效时回到同章首屏，通过 `ReaderListener.onPositionRestored(view, restoration, reason)` 输出 `Restoration.Fallback` 和 `ContentUpdate` 原因。普通导航没有这种回退。失败章节的 `retryChapter` 已实现：非当前章重取内容并将布局留待实际呈现；正常已加载章不重复读取。

`reloadBook` 已提供 suspend/callback 接入。先读取和校验目录，失败保留当前目录及位置；安装成功后按稳定章节 ID 恢复安装时最新已提交位置。章节被删除回退书首，锚点/页提示失效回退同章首屏；成功恢复通过 `onPositionRestored` 输出 Exact/Fallback 与 Restore 原因，方法返回新目录。空目录清除已显示内容并报告 Empty 状态。当前章更新或删除时先清旧页及旧提交位置，再准备有效内容，会产生 Restore 原因的旧位置到空位置、空位置到实际位置的事件。相同非空章节 revision，或章节版本都为空且书籍非空 revision 相同，才允许复用内容及布局缓存。未知版本按内容可能变化处理。安装后的取消不撤回新目录；缺少有效落点时报告可重试错误。

文字 sp 在主线程用 Android TypedValue 转为实际像素后传给布局 SPI，并加入缓存键；`ReadViewport.fontScale` 保留系统配置，实际字号不通过 density × fontScale 近似。callback 请求排队启动，避免在当前监听器事件内嵌套执行新导航。样式重排取消时仅仍拥有该代次的调用回滚，不能覆盖更新调用的样式。

标注数据层已落地：`AnnotationSnapshot` 使用构造时复制的不可修改列表；replace 先整批验证书籍身份和全局标注 ID 唯一性，再原子替换。Widget 提供 annotations/exportAnnotations、书签/高亮增删改、getAnnotationStatus 和书签导航及 callback。冲突分别返回 DuplicateAnnotation/UnknownAnnotation；删除不存在记录成功且不产生数据变更事件。`ReaderListener.onAnnotationsChanged(view, snapshot)` 输出全量数据，`onAnnotationStatusChanged(view, id, status)` 输出 PendingValidation/Valid/Invalid。标注仓库有自己的修订号，监听器嵌套修改后停止传播旧标注事件，不触发阅读位置变更。

导入借用缓存或当前活动布局的章节快照验证，无快照时等待后续加载；更新/重载将相关记录重新置为待校验或 UnknownChapter。非文本自定义块暂不支持范围校验。高亮非空 revision 不匹配直接失效，不做近似迁移；quote 的跨块换行按第 7 节定义逐段比较。书签允许忽略旧版本后重定位稳定块、使用同章页提示或首屏，并通过恢复事件明确报告。数据层接收记录成功与实际有效状态分开。Demo 自行序列化并持久化标注，库不规定存储后端。

分页高亮已接入：`ChapterLayout.rangesForText` 将同章范围拆为 BlockTextRange；`BlockLayoutFragment.selectionPath(startOffset, endExclusive)` 返回片段局部 px 路径，默认 null 表示无几何能力。文字实现通过 [Layout.getSelectionPath](https://developer.android.com/reference/android/text/Layout#getSelectionPath(int,%20int,%20android.graphics.Path)) 获得路径，分页片段转换垂直坐标，正文层裁剪后绘制在文字下方。装饰刷新不改变阅读位置或文本分页；点击路径命中发 `onHighlightTapped(view, highlight)`，优先于普通点击区动作。未知样式发 `onHighlightStyleFallback` 并使用 default。`ReaderStyle.highlightStyles` 当前使用防御性复制的 HighlightStyles 包装类型，而非可被宿主原地修改的 Map；仅改变该字段不会重排。BookPresentation 新增 onDecorationsChanged/currentContent/onTap 扩展点，默认不支持，活动内容快照仍由布局持有，标注仓库不复制全文。

长按初始选区已实现：片段 `anchorAt(xPx, yPx)` 使用局部坐标命中文字，TextSelectionResolver 用平台词/字符边界解析范围，Widget 再校验当前内容版本与提取原文。选区数据不进入标注快照，`onSelectionChanged` 通知临时状态，复制和高亮通过绑定选区修订号的 SelectionActions 执行。SelectionStyle 实现 fillColor/handleColor/handleRadiusDp；手柄可拖动扩大范围，并在同章边缘停留后跨页扩展。选择模式不触发普通滑动翻页；点击正文清除，换章/更新/目录安装/解绑清除，字体重排可保留同版本范围。AnnotationIdFactory 已落地，默认 UUID。旧工具栏动作拒绝 InvalidSelection，自定义工具栏异常报告 SELECTION 阶段错误。

片段 `caretForOffset(offset, endExclusive): TextCaret?` 提供局部插入点 x、行顶/底几何；ReaderBodyView 装配可见端点、扩大手柄命中目标、保存抓取位置与文本行的偏移。BookPresentation 的 onSelectionTouch 先消费手柄输入，TextSelectionResolver 校验原文顺序并归一字符边界；反向/空范围不替换有效选区。拖动期间暂停工具栏动作，结束后更新受控动作，默认 ActionMode 原地复用，避免上下文栏反复显隐导致重排。BookPresentation.selectionBounds 输出可见选区矩形：API 23+ 使用 [浮动 ActionMode](https://developer.android.com/reference/android/view/ActionMode) 的内容矩形定位，API 21/22 保留原生上下文栏；自定义 PopupWindow 根据选区上下方空间与窗口可见区域定位。边缘裁剪、混合方向行尾几何仍需要设备验证。

同章跨页扩展使用呈现生命周期内的取消任务，默认正文上下 24dp 区域停留 550ms 后请求一页。`BookPresentationHost.requestSelectionPage(chapterId, pageNumber, generation)` 先检查当前选区及代次，再经统一事务提交 UserNavigation；新页面就绪后更新端点。章首末及固定端点之外不继续导航，松手/取消/视口变化/解绑取消任务，失败或被替代停止扩展。片段新增 nearestAnchorAt 专供拖动时的合法边界吸附，默认复用严格 anchorAt；内置文字实现按 Layout 行范围直接解析，没有逐像素或重新按字符排版。不会借跨页选择绕过位置单次提交，也不支持跨章选择。

Demo 使用独立宿主 FileInput 记录 TXT/EPUB 来源类型；格式选择后进入系统文档选择器，TXT 额外选择编码，EPUB 直接建立来源。最近输入保存格式字段并兼容旧 TXT 记录，进度继续使用原 ReaderSnapshot。统一打开回调/序号保护、重试/从头打开与 AutoCloseable 来源退休；Widget 不承担文件授权和来源寿命。EPUB 目录的提取说明由宿主读取来源 diagnostics，明确只覆盖元数据和已成功读取章节，未读取全书不宣称无其他省略。

EPUB 片段定位现已接入来源内部正文锚点映射与公开 `suspend resolveNavigation(entry: EpubTocEntry): ReadingPosition?`。仅接受当前成功快照 navigation 中的同一条目实例；重载后的旧条目、外部/非 spine 资源返回 null。无片段或空片段返回章节开头；有片段按需提取该章、发布诊断并查找 id/xml:id，缺失、head/省略元素或无正文可映射时返回 null，不自动降级为章首。资源有多个 spine 身份时选首个。XML/ZIP 错误抛出，取消传播；与读取/重载共用 Mutex，不自动操作 Widget，宿主应在导航前确认当前书籍与请求仍有效。

正文 ID 在整个 XHTML 文档中检查唯一性（id/xml:id 同一元素同值只算一次），锚点只为保留的 body 内容生成。结构元素先结束上一块，再记录下一块边界；内联 ID 记录此时已提取的 UTF-16 偏移，尚未输出的折叠空白不计入该边界。被裁掉的尾部空白向块末夹紧；空结构元素指向后续正文块，文末空元素指向最后块末；空文档不产生锚点。返回位置携带归档 revision，后续由既有 Widget 定位/版式恢复链处理，不引入 EPUB 专用呈现。

Demo 的 EPUB 目录入口已默认使用原 navigation 树，按文档顺序与缩进展示分组和子项；无导航时使用 spine 目录，原目录也提供“正文顺序”入口。条目解析通过宿主 MainScope 调用 resolveNavigation，返回后校验请求序号、打开序号、BookDescriptor 实例和 Activity 状态，再调用 Widget 既有 suspend goToPosition。等待对话框可取消；取消、换书、BookDescriptor 改变、目录重读、onStop/onDestroy 使请求失效并取消 Job，销毁取消宿主 Scope。null 目标明确提示不可定位，解析失败提示读取错误，不误跳到章首。正文顺序与提取说明的旧弹窗也校验书籍实例后再操作。该流程不扩展 Widget 源类型分支。

### 已实现的静态样式 XML 属性

ReaderView 使用 ReaderStyleAttributes 将 TypedArray 统一转换为现有 ReaderStyle，再传给两种呈现。未声明的属性精确保留模型默认值；dimension 经当前 DisplayMetrics 转换为 dp/sp，Android 34+ 的 sp 逆转换使用 deriveDimension，低版本按 density/fontScale。参数最终经过样式模型有限值/正值/非负值校验，非法标题枚举与空字体 family 明确失败。TypedArray 仍由构造调用方在 finally 回收。

| 范围 | 属性与单位 |
| --- | --- |
| 阅读页 | ireadPageBackground（color）；ireadPagePaddingStart/Top/End/Bottom（dimension，转 dp） |
| 正文 | ireadBodyTextSize（dimension，转 sp）、ireadBodyTextColor、ireadBodyFontFamily（系统 family 字符串）、ireadBodyLetterSpacing（em）、ireadFirstLineIndent（em）、ireadLineSpacingMultiplier（倍率）、ireadLineSpacingExtra/ireadParagraphSpacing（dimension，转 dp） |
| 正文内容边距 | ireadBodyPaddingStart/Top/End/Bottom（dimension，转 dp，映射 contentPadding） |
| 正文区域边距 | ireadViewportPaddingStart/Top/End/Bottom（dimension，映射 page.viewportPadding） |
| 正文容器边距 | ireadBodyContainerPaddingStart/Top/End/Bottom（dimension） |
| 非标题块边距 | ireadBodyBlockPaddingStart/Top/End/Bottom（dimension） |
| 标题块边距/字距 | ireadTitlePaddingStart/Top/End/Bottom（dimension）、ireadTitleLetterSpacing（float，em） |
| 标题 | ireadTitleTextSize（dimension，转 sp）、ireadTitleTextColor、ireadTitleFontFamily（系统 family）、ireadTitleAlignment（start/center/end）、ireadTitleSpacingAfter（dimension，转 dp） |
| 页眉/页脚 | ireadHeaderVisible/ireadFooterVisible、ireadHeaderTextSize/ireadFooterTextSize（dimension，转 sp）、ireadHeaderTextColor/ireadFooterTextColor |
| 装饰 | ireadDefaultHighlightColor、ireadSelectionColor、ireadSelectionHandleColor、ireadSelectionHandleRadius（dimension，转 dp） |

系统 family 在布局准备阶段通过 Typeface.create 解析，Android 对未知名称采用自身字体回退；属性现支持 @font 引用，构造只记录 ReaderFont 描述，不同步加载文件字体。高亮只修改 default 调色项，其他命名项仍由代码提供。页面 padding 和正文 contentPadding 分层，外部 android:padding 仍由 View 自身处理。ReaderFont、打包字体资源、ReaderBackground 与分层边距/标题字距已接入，分层边界见后文约定。运行时 applyStyle 优先于 XML；可保存的样式数值现已自动写入 View SavedState；Typeface 等运行时对象仍需 XML 或宿主提供，详见动态样式恢复约定。

### 动态样式的 View 状态恢复

BookReadView 的 SavedState 在原 Bundle 中追加 style 子 Bundle，使用 ReaderStyleStateCodec version 1 保存页背景/边距、正文尺寸/颜色/字距/缩进/行段距/内容边距、标题尺寸/颜色/对齐/间距、页眉页脚可见性/尺寸/颜色、选区颜色/手柄和命名高亮调色板。所有数据是原始类型与数组，不序列化 Typeface 或工厂实例，也不扩展 ReaderSnapshot 的位置协议；旧状态缺失 style 时保留 XML。未知版本、缺失/错误数组、非法模型参数使整份样式恢复被忽略。

恢复只发生于尚未打开书籍且宿主未显式 applyStyle 的实例；优先级为显式运行时 applyStyle（即使相同值）→ 有效 SavedState 数值 → XML/主题/默认。显式调用在 Released 检查之后记录优先级，后续布局失败不会改变宿主曾作显式配置这一事实。打开后的样式仍使用原事务和锚点恢复，不在状态回调中重开书籍或触发第二套排版。body/title font 保留当前 XML/宿主的 Typeface，重建前的运行时 Typeface 不保证恢复。

调色板最多保存 256 项、ID 总计 16384 个 UTF-16 字符，超出时只省略调色板组，其他样式继续保存；恢复保留当前调色板。代码 API 不因此限制调色项数量，需恢复更大或复杂配置的宿主自行持久化。Demo 在 View 恢复后的首次 post 中合并缺失的 blue 调色项，保留当前默认和已恢复颜色，避免 onCreate 过早 applyStyle 使样式状态失效。独立冷启动仍只恢复宿主存储的 ReaderSnapshot，样式不会自动跨独立会话保存到偏好存储。

### 字体描述与加载

ReaderFont 已实现 System(family, style)、Resource(@FontRes resourceId)、Runtime(typeface)。BodyStyle/TitleStyle 在末尾追加可空 readerFont，保留已有 font: Typeface? 参数；readerFont 非空优先于 font，置 null 后继续使用旧字段/库默认。系统 family/style 校验空名称与 Android 四种样式范围，未知 family 由 Android 字体回退；Runtime 保留宿主提供实例。ireadBodyFontFamily/ireadTitleFontFamily 支持系统名称或 @font，字符串资源仍解析为 family 名称，XML 构造只保留描述。

ReaderCoordinator 在共享布局请求的 IO 阶段通过 ReaderFontResolver 解析有效 Typeface，再用原来的 Default 排版阶段构建文字。布局与全书索引仍以未解析 ReaderStyle/ReaderFont 身份为键，不把每次生成的 Typeface 当作来源变化；内置文本及自定义 BlockLayoutProvider 都收到含有效 font 的 BlockLayoutContext。加载异常沿现有 LayoutFailed 路径，样式事务负责回滚，取消继续传播。打包字体用 ResourcesCompat.getFont；Android 文档明确其同步接口会阻塞，因此放在 IO。XML font-family 的 fontProvider 属性明确拒绝；下载字体由宿主预先加载并以 Runtime 提供，不在 Widget 内请求 provider。[ResourcesCompat 字体加载](https://developer.android.com/reference/kotlin/androidx/core/content/res/ResourcesCompat)

System 与 Resource 的描述可追加保存到 style 子 Bundle，重建后重新加载；Runtime 与旧 Typeface 仍不序列化，缺失字体描述时保留当前 XML/宿主值。字体资源 ID 状态只适用于同一应用构建的 View 重建，不作为跨版本外部存储协议。字号/字体改变沿同一 applyStyle→内容代次失效→定位恢复链，分页/滚动/全书索引共用，无专用字体呈现路径。

### 页面背景描述与 Drawable 所有权

ReaderBackground 已实现 Color(color)、Resource(@DrawableRes id)、Factory(DrawableFactory)。DrawableFactory.create(context) 必须每次返回未挂接且独立的 Drawable；不会序列化工厂或 Drawable 实例。PageStyle 末尾追加 readerBackground，非空时优先于旧 backgroundColor；Resource/Factory 透明部分以 backgroundColor 作底色，null 仅显示旧背景色。旧构造参数保持顺序，宿主需切回颜色时可同时将 readerBackground 置 null。

默认 ReaderPageTemplate 用 ReaderBackgroundBinding 在每个 root 上绑定独立实例；重复 bind 同一背景描述/底色不重建，改变或 unbind 释放 root 的旧背景引用。资源通过 ContextCompat.getDrawable/mutate 获取，工厂返回已设置 callback 的 Drawable 明确拒绝，失败沿现有页面准备/样式事务路径处理。背景先于显式 page padding 绑定，避免 Drawable 自身 padding（如 nine-patch）重复压缩正文。withDecorations的外层root统一拥有整页背景与page.padding；内层默认模板的页面背景透明且page.padding为零，避免重复创建背景及图片坐标系分裂。自定义槽、保留的默认槽和正文均受整页padding约束，View自身padding及body专属边距独立生效。默认分页和滚动都复用此模板，翻页 Surface 由 root.draw 输出背景；自定义模板收到 PageStyle，是否应用背景由模板负责。

ireadPageBackground 现接受 color 或 @drawable（也接受 drawable 类型 mipmap），XML 只记录资源描述，页面 bind 时创建实际 Drawable。View SavedState 保存 Color/Resource/回退颜色模式的描述，重建创建新实例；Factory 不保存，缺失描述时保留当前 XML/宿主背景。旧版本 style Bundle 没有背景描述时同样保留当前值。背景图不是正文图片功能，不改变 EPUB 文本提取范围。加载/错误占位当前仍使用 backgroundColor 底色，尚未扩展占位模板定制或图像占位。

### 页眉页脚的高度、内容和边距

ChromeStyle 在原 visible/textSizeSp/textColor 之后追加 heightDp（null 自适应、非空有限正 dp）、padding（EdgeInsets）、alignment（START/CENTER/END）、content（DEFAULT/BOOK_TITLE/CHAPTER_TITLE/PAGE_NUMBER/PAGE_COUNT/PAGE_PROGRESS/NONE）及 background（ReaderBackground）。默认 ReaderStyle.header 保留 12dp 下间距，footer 保留 12dp 上间距并末端对齐；裸 ChromeStyle 的 padding 为零，宿主可自行指定。背景逐 TextView 使用独立绑定实例，先背景后显式 padding，默认无色透明。

DEFAULT 在页眉显示章节名（无章节时书名），页脚显示当前页/章节页数与时间；其他枚举可显式显示书名、章节名、页码、页总数、组合进度或不显示文字。准备阶段没有位置信息时页码文字为空；滚动模式仍显示现有虚拟章节页信息，不宣称全书百分比。固定高度包含 TextView padding，过小高度可能裁剪文字，正文可用区域由模板实际测量确定，继续沿原布局恢复链。

XML 对 header/footer 分别新增 ireadHeader/ FooterHeight、PaddingStart/Top/End/Bottom、Background、Alignment、Content（实际属性名无空格，如 ireadHeaderContent）。Height 为 dimension；Background 为 color/@drawable；Alignment 为 start/center/end；Content 为 default_content/book_title/chapter_title/page_number/page_count/page_progress/no_content。构造时检查未知枚举和非法高度；未声明值保留默认模型。View 样式状态追加 headerOptions/footerOptions，旧状态缺失时保留当前新字段，原可见性/尺寸/颜色仍恢复。背景工厂不保存，数值/资源描述恢复。页眉页脚字体已接入，时间内容/刷新已接入，更多高度策略仍需后续实施。

页眉页脚字体已扩为 ChromeStyle.font: Typeface? 与 readerFont: ReaderFont?，优先级和 body/title 一致；XML 新增 ireadHeaderFontFamily/ireadFooterFontFamily（名称、@string、@font），System/Resource 描述在 chrome 状态选项内保存，Runtime/旧 Typeface 不保存。ReaderFontResolver.prepareChrome 在 IO 上加载，然后默认模板使用有效 font，在首次测量正文区域前生效；两种呈现均先捕获代次/样式，字体准备返回后核对代次，再建立候选。错误返回 LAYOUT/FontLoadFailed，取消传播，不悄悄改用错误字体后继续布局。

滚动候选保留该次已测量的字体样式，可见位置变更的同步 bind 使用已准备值并合并当前装饰调色项，不在滚动回调中读字体文件。初次 template.bind/addView 纳入候选 try/finally，绑定抛错时也执行 unbind，避免半成品背景/字体引用残留。自定义模板接收的 ReaderPageContext 同样包含已准备 chrome font；正文 provider 的字体仍由共享章节布局阶段准备。时间内容及统一生命周期刷新已接入，行为边界见后文。

### 页面时间与生命周期

ChromeContent 末尾追加 TIME（保留原枚举序号），XML header/footer Content 对应 time；样式状态仍保存 content。默认模板使用系统 DateFormat.getTimeFormat 按当前语言、时区及 12/24 小时偏好格式化，初次 bind 读取 ReaderPageContext.timeMillis（新增末尾可选字段，默认当前墙上时间）。时间不是 ReadingPosition/ReaderSnapshot 的内容，不保存历史时刻。

ReaderPageTemplate 新增默认 needsTimeUpdates=false 与 onTimeChanged(timeMillis) 空方法；BookPresentation 也提供对应默认钩子。内置呈现只转发给代次有效的 active 模板，候选页面只取得 bind 时间，不各自创建计时器。默认模板只在 visible 的 header/footer 使用 TIME 时申请更新，unbind 清除请求。自定义模板可主动选择该钩子，须只更新文字/绘制，保持已测量正文区域，不在时钟回调中导航或发起来源读取。

BookReadView 持有一个 ReaderPageClock：已提交位置、View attached/isShown/windowVisible、当前呈现需要更新时才安排分钟回调；首次启用/恢复可见立即刷新，再按墙上时间对齐下一分钟。隐藏/窗口后台、detach 和 release 移除回调，重新 attach/可见重新安排；不在每帧和每次位置通知中格式化时间。回调后复查呈现实例、内容代次、释放/挂接状态，重入防护避免自定义回调通过事件嵌套无限刷新。异常日志每个呈现/代次一次并停止该代次时钟，不改变阅读 phase、不重复提交位置；下一有效呈现可继续刷新。系统时钟/时区/偏好改变于下次分钟刷新或可见恢复时反映，目前没有注册常驻广播监听。

Demo 输入设置增加页眉章节名/时间切换，仍通过 applyStyle 的现有布局事务配置，不另设时间专用 Widget。minute tick 不调用 navigate/publishState，不使来源和排版缓存失效。卷页期间位图是既有捕获时刻的页面，不额外修改已租借快照。

### 公开占位工厂（首屏接入）

spi.page.ReaderPlaceholderFactory 提供 create(context, kind)/bind(view, state, actions)/unbind(view)，通过 BookReadView.setPlaceholderFactory 配置。PlaceholderState 包含 kind、bookId、chapterId、ReaderError 和当前 ReaderStyle；PlaceholderActions 提供 canRetry 与 retry(): Boolean（是否接受重试）。枚举覆盖 CLOSED/BOOK_LOADING/BOOK_FAILED/CHAPTER_LOADING/CHAPTER_FAILED/EMPTY_BOOK/EMPTY_CHAPTER/LAYOUT_FAILED。容器首屏、章节内 EMPTY_CHAPTER 和滚动加载/失败边界均已接入同一工厂，行为细节见后文。

未提交位置时按 ReaderPhase/错误阶段显示占位；可读页面已有位置时继续保留正文，不用全屏错误覆盖。默认占位是本地化说明和受控重试按钮，使用当前文字色和 page 底色；来源错误 message 可由自定义工厂改为宿主提示。每次返回 fresh、无 parent 的 View，控制器最多持有一个绑定；状态/会话或 factory 改变先使旧动作失效，再 remove/unbind。首屏失败重试复用当前 BookSource 和最近 OpenBookRequest，重新进入原 openBook 路径，不改写初始位置要求。

retry 要求当前可见启用占位、尚未消费该绑定动作、Widget 未释放/启用/导航空闲、相同打开序号/内容代次/来源、可重试 Error 且尚无已提交位置；旧按钮拒绝，每份绑定最多接受一次，生命周期任务失效后不会重开来源。创建/bind 异常记录并降级默认工厂，该控制器期间不反复尝试已失败的工厂实例；unbind 异常记录但继续释放。重入更新使旧绑定失效并延后最新更新，重新打开/清理使延后回调失效。factory 是运行时对象，不加入 XML、SavedState 或 ReaderSnapshot。

Demo 在首屏失败后记录可恢复来源；占位重试进入 Ready 后通过 ReaderListener 更新宿主已提交来源/进度和退休集合，避免绕过 Activity 打开回调导致文件类型与进度遗漏。下一次显式打开清除此恢复记录。章节内部占位已接入；自定义 Demo 模板与设备验证仍继续实施。

### 章节内空内容与滚动边界占位

BookPresentationHost 新增默认 placeholderFactory/retryChapter，BookReadView 注入当前工厂和已有章节重试服务。默认分页在 contentSnapshot.blocks 为空时将 EMPTY_CHAPTER 占位放入 bodyHost，保留已有的单页/章节 ReadingPosition 与空锚点，不用新的占位位置类型。候选失败/替换/取消/dispose/detach 都 clear/unbind；setPlaceholderFactory 通知当前呈现更新占位。

滚动列表的空章节及边界项改为 FrameLayout holder + ReaderPlaceholderController，绑定采用同一工厂；holder recycle/adapter detach 清理控制器动作和引用。空章节虚拟高度与实际占位行同步为 128dp，边界行也是 128dp，正文片段高度未改变。边界失败保存实际 ReaderError，按 CHAPTER_FAILED/LAYOUT_FAILED 显示；未失败用 CHAPTER_LOADING。控制器状态去重包含 retry 可用性，导航恢复空闲后更新动作显示，避免忙碌时绑定的重试按钮一直隐藏。

边界 retry 要求当前候选/代次/row/holder 位置有效、空闲且未拖选区、错误可重试，并遵循最多三章及不淘汰可见章规则。先调用 host.retryChapter 清除来源/布局失败缓存，再沿 loadChapter 加载该邻章；开始时切回加载占位，成功追加/淘汰窗口并按可见章偏移恢复，失败保存新错误，不全屏覆盖正文。不直接提交一个重试阅读位置。加载任务完成和重试动作仍核对 active/代次，旧回收按钮不能加载当前新边界。

### 宿主模板与占位示例

公开 ReaderPageFactories.default()/ReaderPlaceholderFactories.default() 返回默认工厂的新实例，宿主可在自定义模式后恢复默认，无需访问 internal 实现。CustomPageFactory 展示独立工具栏 root/bodyHost、受控目录/设置按钮及章节页进度+时间页脚；只用公开 ReaderPageContext/ReaderAction/时间钩子，按钮在未绑定位置时禁用，unbind 清除动作引用。它选择自己的工具栏颜色/结构和固定文字内容，并遵循字号、字体、可见性及页边距；不会冒充默认模板的全部样式行为。

CustomPlaceholderFactory 展示进度指示、友好的加载/空内容/失败文案和 retry 按钮，只调用 PlaceholderActions，不直接打开来源；unbind 清掉监听器并隐藏进度指示。Demo 设置可分别切换页面模板和占位工厂，页面选择回到默认后恢复原默认内容配置。Activity Bundle 只存两个宿主选择标志，重建由宿主重新创建工厂，Widget 状态不序列化工厂实例；独立冷启动尚不保存这些示例选择偏好。

### Demo 最近阅读记录

ReadingStorage 在原逐书 ReaderSnapshot 和 recent v1/v2 记录外新增 version 1 history 数组，最多32条，按书籍身份去重、最近进度保存优先；记录包含来源类型、URI/TXT编码、书名与最近更新时间。书名规范化换行并限制256个UTF-16字符（末尾不截断代理对），损坏单项忽略，重复项取首项，未知/无效数组回退可用旧 recent。原单条最近记录在下次成功保存时自然进入列表，ReaderSnapshot 协议不改变。

进度、recent和history由同次 SharedPreferences editor.apply 更新；超过32项淘汰已知旧记录及其进度，标注和原文件仍保留。history 返回不可变拷贝；宿主 forget(bookId) 移除该条与进度，移除当前 recent 时选择剩余首项或清空。该机制不列举并删除未知旧存储键，也不宣称 apply 在任意强杀时均已持久化。

Demo“打开书籍→最近阅读”显示书名、来源类型/编码、章节页提示与URI末段辅助区分；选择时验证URI+格式+编码的身份，再用原 openSource/restoreSnapshot。授权或资源已失效仍走现有失败/重试/重新选择流程，不自动扩大权限。移除入口拒绝当前正在阅读书籍，避免随后自动保存重新写回；移除保留原文件和标注。URI授权追踪/回收已接入宿主；只有Demo新取得且无剩余记录/活动打开请求引用的读授权可以释放。


### Demo URI读授权生命周期

UriGrantStore 是 applicationContext 单例，独立 IO 队列串行处理授权取得和清理；不扩展 Widget API、ReaderSnapshot 或 BookSource。Android 的持久授权属于应用而非阅读器，旧版本/其他功能已经取得的读授权不纳入本账本，写授权不释放。仅在选择结果同时允许持久授权和读授权时尝试保留；失败仍允许当前会话尝试读取。

新读授权先提交宿主账本，再调用 ContentResolver，避免 Activity 协程取消留下无追踪授权。Activity 等待结果时检查选择序号，切书/新选择使旧结果失效；后台事务不受 Activity 销毁取消。URI相同但编码/来源身份不同的记录共享读授权引用。引用集合包括历史、当前成功输入、当前请求、待取得授权的选择与占位重试输入。

清理在历史保存/移除后提交（相同引用集合合并），onStop 补一次重试；先确认阅读存储同步落盘成功，再读取实际持久授权，逐项释放本账本中无引用的 READ 位。失败保留账本供后续尝试，已经不存在的授权移除陈旧账本项；释放后账本提交失败也只留下可再次核对的陈旧项。不删除文档或标注，不枚举并清扫所有应用授权，不在未修改记录的启动阶段根据损坏历史推测清理。进程骤停留下的无引用账本项在后续记录维护中回收。该Demo目前是此账本授权的唯一使用者；增加其他宿主功能时必须共同维护引用，Android不提供每个功能独立的授权计数。

依据：[Android ContentResolver 持久授权接口](https://developer.android.com/reference/android/content/ContentResolver)。队列不构成跨进程锁，Demo使用单进程。真实文件提供器、授权撤销、应用退出/进程死亡和快速选择仍待设备验证。

### 分层间距实现与几何约定

新增字段均追加构造参数并默认零边距/字距：PageStyle.viewportPadding、BodyStyle.containerPadding/blockPadding、TitleStyle.padding/letterSpacingEm；已有 contentPadding 和 XML ireadBodyPadding* 含义不变。各层职责为：

| 层 | 作用范围 | 应用者 |
|---|---|---|
| View android:padding | Widget外部内容边界 | Android View |
| page.padding | 页模板内整体内容（包括chrome） | 默认模板；自定义模板自行应用 |
| page.viewportPadding | 仅正文区域，排除chrome | 内置分页/滚动呈现 |
| body.containerPadding | 正文容器外层内边距 | 内置分页/滚动呈现 |
| body.contentPadding | 正文可排版区域内边距 | 内置分页/滚动呈现 |
| body.blockPadding | 所有非HeadingBlock块四边 | 共享PaginationEngine |
| title.padding | HeadingBlock四边，替代body.blockPadding | 共享PaginationEngine |

三个正文区域层目前没有独立背景/裁剪装饰，ReaderBodyInsets将dp求和后一次转px，并由正文View/RecyclerView持有，避免增加空壳View。它们仍为独立配置以便宿主表达布局责任。起始/末尾按View方向应用，块边距按ReadViewport.rightToLeft解析；段落文字的方向不改变页面边距。正文垂直边距在分页每页/滚动固定viewport生效，非逐章重复；块上下边距只添加到首/末测量单元，块跨页中间片段不重复。paragraphSpacingDp与title.spacingAfterDp继续作为块外后间距，叠加于padding.bottomDp。

共享块测量入口先减去横向边距，再调用provider；provider获得内容宽度，仅返回内容几何，不得再次应用block/title padding。PaddedBlockLayout包装原单位/片段，保留blockId和UTF-16锚点；统一绘制、选区路径、手柄、命中、最近锚点、纵向可见文本范围坐标，分页和滚动复用。边距区域不响应普通文本命中；拖动在边距内夹紧到正文，辅助技术仅可见边距时不暴露文本。空块集合不产生伪文字/边距块。测量单元含首尾边距若超过viewport仍按已有布局失败处理，不裁掉边距、不制造可提交的截断内容。

标题letterSpacingEm直接作用于TextPaint；四组边距与标题字距可用XML初始化、applyStyle事务修改，参与ReaderStyle缓存键和布局失效。style SavedState version 1追加可选原始字段，旧状态缺失字段保留当前XML/默认值，非法数组/数值仍拒绝整个样式恢复。ReaderSnapshot不添加样式。

Demo“输入设置→排版设置”提供五层四边dp输入以及标题字距em输入，校验有限/非负边距；使用现有applyStyle，当前值在点击应用时合并，失败按Widget事务回滚并显示错误。此入口仍属Demo，独立冷启动偏好已由ReaderPreferences接入。Debug构建通过，未新增或运行测试；跨页首尾、长段落/自定义块、RTL、可见行恢复、选区/手柄、无障碍范围及旋转/旧状态恢复待设备验证。

### Demo偏好与主题

ReaderPreferences是宿主自己的version 1 JSON协议，不调用库internal的ReaderStyleStateCodec，不把偏好并入ReaderSnapshot。单独的reader-preferences存储保存样式、输入、翻页参数、内置模式、宿主可重建效果标识和自定义页面/占位选择标志；各组独立解码，无效样式整体保留XML，坏输入/翻页组选项不影响其他组，未知版本/超64Ki UTF-16字符记录忽略。保存数据相同则不重复apply；样式组超60000字符则不保存该组，其余偏好仍可恢复。

样式覆盖页面背景/边距/viewport、正文字体描述与排版/分层边距、标题字体/颜色/字距/边距/对齐/后间距、chrome内容/字体/高度/背景/边距、选区及有界高亮调色表。字体仅保存System/Resource（family最多4096字符）；背景仅Color/Resource/null，资源保存完整名称并在当前安装解析与验证资源类型，避免用跨安装的整数ID。Runtime Typeface、旧font对象和DrawableFactory不序列化，未写入描述时保留XML/宿主的当前值；明确null写default描述，区分遗漏与回退。已消失资源导致样式组恢复失败，使用XML，实际字体/Drawable加载仍沿Widget准备流程。高亮最多256项、ID字符总和16384，超限调色表省略并保留XML。

Activity无savedInstanceState时读取偏好，View恢复时机之后依次调用公开suspend样式/模式方法完成初始化，再合并缺失blue高亮项，最后打开来源。避免把排队未完成的applyStyle当作已应用、随后用XML构造的调色配置覆盖偏好。Activity状态重建不套用磁盘偏好，Widget SavedState优先；模板标志来自Activity Bundle，冷启动才来自偏好。冷启动逐书ReaderSnapshot随后恢复其模式/内置效果，有效逐书配置覆盖全局初始值。用户显式设置继续按公开接口优先级工作。

MainActivity缓存最近成功提交的ReaderStyle，仅成功回调更新；输入/效果/模板等同步设置成功后及模式成功回调、onStop/onSaveInstanceState补存。样式等待/失败/取消不会把预备版式作为偏好写出；偏好初始化完成前不保存，避免初始化中覆盖已有整组数据。SharedPreferences.apply不承诺任意强杀瞬间落盘。

ReaderThemes提供纸色（渐变资源）、清晰白、柔和绿、夜间四种宿主配色；通过ReaderStyle同时修改背景、正文/标题/chrome文字与手柄颜色，保留排版、字体、位置和标注数据。入口为“输入设置→阅读主题”，调用既有applyStyle事务。自定义工具栏跟随主题底色，仍是自身结构/内容策略，不宣称应用默认模板所有装饰。主题选择保存最终配置，不保存脆弱的数组序号；库不固定主题集合。

Debug构建通过，未新增或运行测试；冷启动/旋转/进程重建、逐书与全局配置优先级、坏组/未知版本、资源更新、超大调色表、设置失败/取消与主题实际对比度/服务阅读仍待设备验证。

### Demo完整排版面板

TypographySheet替代原MainActivity间距编辑函数，是只使用公开ReaderStyle模型的宿主UI组件。注入currentStyle与apply回调，不持有Widget内部对象、不创建独立样式副本或存储协议；所有修改沿ReaderActivity.applyReaderStyle→Widget事务→成功偏好保存执行。菜单分为页面/正文区域、正文、章节标题、页眉、页脚：

| 分组 | 已提供编辑 |
|---|---|
| 页面/正文区域 | page.padding、page.viewportPadding、body.containerPadding、body.contentPadding |
| 正文 | 字号sp、系统字体、字距em、首行缩进em、行距倍率/额外dp、段距dp、blockPadding |
| 标题 | 字号sp、系统字体、字距em、padding、对齐、后间距dp |
| 页眉/页脚 | 显示、字号sp、系统字体、padding、对齐、内容选择、自动/固定dp高度（0表示自动） |

数值必须有限，字号/行距倍率大于零，边距/缩进/行段距/高度不小于零，字距允许负数，沿模型约束校验；过大值导致排版失败由原事务回滚。四边输入置于ScrollView，字段labelFor关联生成的唯一View ID；单数值编辑器用标题/单位描述输入。起始/末尾按相对方向标记，不在UI伪称固定左/右。字体菜单提供系统默认、sans-serif、serif、monospace；选择明确清除旧Typeface入口，字库资源/运行时字体仍可由宿主API提供，不宣称面板支持系统字体文件选择。

编辑器仅缓存输入文字，点击应用时从current()重新读取整个样式并修改对应字段，保留其间更新的其他组配置。所有弹窗由一个引用统一管理，打开下级释放上级，onStop/onDestroy关闭；菜单/选择/数值按钮同时检查Activity存活和当前窗口身份，过期窗口不能提交。取消不修改样式，未提交草稿不保存。自定义页面模板仍可自行解释chrome结构（工具栏示例不按默认模板所有chrome字段绘制），API改动实际生效范围以所选模板为准。

需求FR-DEMO-04列出的排版编辑项已接入，字体/chrome额外选项也通过同一入口；这仍是编译与源代码实现记录，未新增或运行测试，键盘/小屏/横屏、TalkBack标签、旋转/后台、版式失败回滚和实际自定义模板表现待设备验证。

### Demo阅读控制面板与章节页进度

ReaderMenuController使用Activity内容FrameLayout上的非模态ScrollView覆盖层，显示/隐藏不改ReaderView尺寸，不触发控制面板引起的重排；中间点击toggle，底部“控制面板”按钮提供键盘/辅助技术入口，收起/返回键关闭。面板提供目录、排版、阅读设置、上一章/下一章、章节页SeekBar与精确页号输入。当前固定页数来自公开getChapterPagination的Ready状态，不触发全书页索引计算；滚动模式中的页数/跳转继续使用既有虚拟分页与ReadingPosition契约。

导航只在ReaderPhase.Ready、Idle、有当前位置与已知正数章节页数时启用；上一章/下一章再检查实际目录边界，准备期不保留伪可用按钮。Target捕获BookDescriptor实例、章节ID、revision、layoutKey、pageCount，提交前和宿主协程开始时均重新检查当前目标及Widget启用状态。显式构造ReadingPosition提交goToPosition，不在排队后通过goToPage重新猜测章节。版本/排版改变、换书、导航忙碌使旧目标失效；页号输入主动关闭，触摸拖动预览取消。触摸拖动只预览目标页，释放后一次导航，真实位置/进度只取Widget成功提交结果；键盘/辅助技术修改SeekBar通过fromUser且无触摸跟踪的分支进入同一跳转。

面板内部最多一个导航Job，开始时禁用导航输入，结束更新实际状态；目录/排版/设置打开前先收起。关闭、换书、onStop/onDestroy移除覆盖层、关闭页号弹窗并取消宿主请求，序号防止已取消任务的finally覆盖后来的任务引用。失败/Busy沿现有导航结果文案，不提前写成功位置；进度持久化仍由已有ReaderListener链路处理。面板展开状态与未提交页号草稿不持久化，Activity重建默认收起。

FR-DEMO-02的控制面板入口和章节导航/进度已接入；构建通过，未新增或运行测试。实际窗口布局/横屏/IME、覆盖层外中间点击、原生SeekBar辅助技术事件、取消/快速换书/重排与滚动虚拟页跳转仍待设备验证。

返回适配：API33及以上仅在覆盖层展开时注册OnBackInvokedCallback，关闭立即注销，API21–32沿Activity.onBackPressed关闭；平台新API隔离在RequiresApi内部对象中。依据：[Android OnBackInvokedDispatcher](https://developer.android.com/reference/android/window/OnBackInvokedDispatcher)。未实现预测返回过程中面板随手势移动的动画，返回提交仅收起覆盖层；实际各版本手势/硬件返回仍待验证。

### 宿主自定义翻页效果示例

ReaderEffects维护宿主效果目录：六种内置工厂加demo.wipe自定义工厂，工厂每次create返回新的WipeEffect实例。fromId只在Demo中解析自定义标识，PageTurnEffects.fromId和ReaderSnapshot协议不扩展。效果菜单可选择“宿主擦除示例”，沿公开setPageTurnEffect；滚动中沿既有DeferredUntilPaged、减少动画/纵向分页仍由Widget策略处理。

WipeEffect仅依赖PageTurnEffect公开接口：attach声明绑定，supports检查有效viewport，start保留当前事务PageTurnContext并验证两页面尺寸相同，draw使用progress对target做矩形裁剪叠到current上，方向取Forward/Backward与rightToLeft；0完整当前页，1完整目标页，中间添加细阴影。Canvas两层save/restoreToCount均用finally保护，页面引用在finish/abort/detach清空，detach解除绑定。不调用导航、不写阅读位置、不启动动画、不分配缓存位图、不借用未使用的host/BitmapLease；拖动、反向复位、结算、回退和提交生命周期由Widget统一管理，效果类不决定是否翻页成功。

ReaderPreferences的既有effect字符串现在允许宿主目录已知标识，不序列化实例或任意类名；冷启动按宿主resolver重建。Activity Bundle仅为自定义效果追加demoCustomEffect标识，在View SavedState恢复前安装工厂；内置效果仍由原Widget SavedState负责。自定义效果的ReaderSnapshot.builtInEffectId继续为null，打开时保留宿主工厂；其他书籍已有内置效果快照仍按原契约覆盖全局初始选择。目录移除/未知标识不反射加载类，保留XML默认。

本例满足FR-DEMO-07的自定义效果接入展示，独立通用容器演示继续实施。Debug构建通过，未新增或运行测试；拖动/回退端点、RTL、效果切换/释放/模式恢复、减少动画和真实Canvas外观仍待设备验证。

### 独立通用容器效果Demo

ContainerActivity是独立宿主页面，从“打开书籍与演示→独立容器效果演示”进入，Manifest仅应用内部可启动。CardReadView继承BaseReadView<Int>，Int为五张固定卡片的零基序号；CardAdapter实现公开ReadContentAdapter<Int>，不引用BookReadView、BookSource、章节/位置/排版内部对象。

adapter attach绑定公开ReadContentHost，prepare按First/Last/Next/Previous/Position解析边界并检查有效viewport，在主线程创建和精确测量独立TextView候选；PreparedRead包含目标序号、代次和可绘制PageSurface，show检查所有者/代次/未dispose后同步替换可见卡片并转移surface所有权。未显示候选dispose清surface引用；已转移候选dispose不清已显示内容，下一次显示/adapter detach清旧surface。候选取消之前不改变可见卡片，show没有挂起点、不自行提交位置；BaseReadView统一完成提交与事件。卡片文字由原生TextView提供辅助技术语义，通用前后能力按卡片实际边界查询。

onViewportChanged复测已显示卡片，不生成虚假的新位置；第一次有效尺寸补初始化，挂载以当前已提交序号或Activity恢复序号导航，解绑移除待初始化回调，release清理。通用子类按实际目标序号定义Forward/Backward，包括显式位置跳转。手势、边缘点击、键盘、间隔、减少动画和效果生命周期继续由BaseReadView驱动，Demo显示真实提交卡片和导航忙碌状态，不新增帧循环。

Activity展示前后按钮、宿主统一效果目录（六种内置+自定义擦除）、减少动画和退出；ReadViewListener更新能力和结果。View具稳定资源ID，原通用SavedState恢复输入/动画参数；Activity另存已提交卡片序号和宿主已知效果标识，并重建工厂，不保存TextView/页面/候选/位图/Job。此演示不与书籍进度或阅读偏好混用，未做独立冷启动持久化。

独立容器效果演示入口已实现，构建通过，未新增或运行测试。通用适配器的实际挂载/重建/旋转、拖动提交/复位、效果位图租借、键盘/辅助技术、边界与取消仍待设备验证；其完成不证明网络来源示例、可复用任意宿主模型或完整运行验收已完成。

### 网络章节来源Demo

NetworkActivity与NetworkBookSource只在宿主app中实现，不给Widget增加HTTP/站点/认证分支。入口为“打开书籍与演示→网络章节来源演示”；用户显式输入HTTPS目录地址后请求，未绑定第三方生产服务，不启动时自动探测站点。Demo Manifest声明INTERNET，库Manifest不申请网络权限。

宿主示例协议为UTF-8 JSON目录：version=1、title、chapters数组，每项id/title/url；url相对目录地址解析，也可为同源绝对HTTPS。参考docs/examples/network/catalog.json及两份chapter-*.txt，可把这些文件部署到自己的HTTPS服务后输入目录地址。请求只接受HTTP200，不跟随重定向、不读取压缩响应，不使用登录/账号信息/URL片段，不发送全文/标注/进度；服务器需直接返回同源UTF-8资源。此格式展示BookSource接入方式，不宣称通用小说站点抓取或服务可靠性。

目录最多256KiB/1000章，书名/章名最多256字符、章节ID最多128且唯一、章节地址最多2048；正文最多4MiB/100000行。连接/单次socket读取超时10秒，请求使用30秒协程期限并在取消时独立disconnect，异常若父协程已取消继续传播取消；期限到达而父协程仍有效转为普通IO失败便于重试。disconnect为尽力关闭，平台DNS/TLS阻塞不保证精确30秒返回，socket timeout提供额外边界。请求集由锁保护，source.close永久关闭并断开活动连接；流use关闭，完成/异常/取消均解除连接登记。不做自动重试，不绕过证书校验。

读取时同时检查Content-Length和实际字节上限，已声明长度必须完整；严格UTF-8解码，首BOM删除，CRLF/CR归一化。目录loadBook用Mutex串行，完整校验后原子替换不可变快照，失败/取消保留原快照；不同loadChapter可并行，各取一次章节映射并按需请求。章节生成HeadingBlock(title)与按行TextBlock(line-N)，章节修订 v2 为域标识、长度前缀 UTF-16 标题和原始响应字节的 SHA-256；bookId为规范化目录URI摘要，目录revision同为字节摘要，不把网络地址放进Widget位置。来源不重复维护正文缓存，Widget沿原缓存/排版/旧代次规则工作。

Activity提供地址/打开/取消/重试/前后页。换地址前验证新来源，成功构造后取消旧请求、关闭旧会话与来源；显式取消不在重建后自动重开。onStop导出轻量snapshot再closeBook/source，防止后台预载仍访问网络；恢复前台重建来源并restoreSnapshot，保存Bundle版本化位置及URL/恢复标志，覆盖先stop再save时View已无位置的情况。序号拒绝旧打开/翻页回调修改新状态。Activity有稳定View ID恢复轻量配置，网络地址/位置不进入最近书籍存储，不新增冷启动书架项或跨会话全文存储。

Debug构建通过，未新增或运行测试，未实际请求远端服务；DNS/TLS/重定向/压缩/响应截断/慢响应、并发章读取/取消、停止后重建与真实网络首屏/错误重试仍待设备验证。该实现完成网络示例的代码接入，不替代完整运行验收。

网络目录边界补记：目录地址在URI构造前最多2048个UTF-16字符，规范化后目录/章节绝对地址的ASCII表示也最多2048字符。JSONObject之前执行可取消的结构预检：对象根、匹配对象/数组括号、最多32层、字符串转义识别、闭合完整与根后只允许JSON空白；拒绝单引号/注释扩展。预检仅限制结构与解析器递归预算，实际语法仍由JSONObject解析，并非另写完整JSON解析器。version必须为整数1，id/title/url必须为真实字符串，不接受JSONObject.getString的隐式类型转换。Content-Length出现时必须为十进制非负可表示整数，格式无效不当作未知长度；既有大小/完整性规则继续生效。失败发生在原子目录安装前，旧快照保持。

该补记为代码与构建证据，未新增或运行测试；深度/转义/根后内容、错误字段类型、超长/编码地址和非法长度头仍需实际验证。

### Demo全书页数面板

PageIndexPanel管理计算进度与全书页号输入窗口，仅调用公开buildBookPageIndex/goToGlobalPage挂起API。宿主持有一个请求Job和窗口监听器：关闭/取消、换书、再次打开、Activity停止/销毁时解除监听并取消任务；计算完成转入页号窗口时先解除旧窗口的dismiss动作，避免窗口切换取消正在完成的请求。序号使旧任务不能打开新窗口或发布结果，延迟启动确保Job在同步完成前已有明确所有者。

页号使用Long并按捕获索引的totalPageCount验证。页号窗口绑定BookDescriptor和BookPageIndex实例；公开索引变化通知发现实例失效即关闭，提交及协程执行前再次复核。最终仍调用goToGlobalPage，沿Widget内部索引解析与导航期间失效取消规则执行，不在宿主复制全书索引或把全书页号转换成绕过检查的位置导航。计算失败/拒绝/被新排版取代明确显示结果，目录空时不创建输入窗口。

Debug构建通过，未新增或运行测试；真实计算取消、快速换书/重排、目录刷新、空目录、超大页号和生命周期行为仍待设备验证。

### Demo书签与高亮管理面板

AnnotationPanel替代MainActivity的内嵌书签弹窗和高亮点击弹窗，仅使用exportAnnotations/getAnnotationStatus、公开增删改与导航API。目录“书签”入口打开计数菜单，分别展示书签和高亮、添加当前位置书签、保留示例高亮动作；点击正文高亮也进入同一详情。高亮列表显示前80字符预览（先截取再归一化换行），详情最多4096字符，截尾不破坏代理对，UI显示截取不改变原quote或标注模型。

详情区分Valid/PendingValidation/Invalid及失效原因。有效高亮通过带原revision和start锚点的ReadingPosition严格跳转，不提供书签式章首回退；待校验高亮明确提供“打开章节校验”，先正常导航到该章首以触发Widget现有验证，再重开详情查看结果，而非在未校验摘录上宣称精确定位。失效高亮仅查看/删除/改色，不提供精确跳转。书签仍使用goToBookmark原恢复契约，失效/回退文案由公开状态与原事件提供。

删除/改色检查当前书籍实例和当前标注与捕获记录相同；颜色使用公开调色表ID，不改语义位置/quote/revision。导航在宿主scope中执行前再次校验实例和标注，使用既有导航结果处理；一个Job及序号防旧回调，换书/onStop/onDestroy关闭窗口、取消导航。窗口菜单/按钮检查当前窗口身份，旧窗口不能修改另一书籍；取消无隐式删除。增删改事件继续由AnnotationStorage监听保存，面板不写数据库或新增标注协议。

Debug构建通过，未新增或运行测试；真实尚未加载章节校验、内容更新/摘录不符、回退/Busy、颜色/删除/旧窗口、超长摘录与服务阅读仍待设备验证。


### 正文虚拟节点相邻移动

BodyAccessibilityHelper在当前节点字符/词/段落移动无后续片段且非扩展选区时，按已绑定片段阅读顺序查找同一ReaderBodyView内的相邻可见语义节点；跳过不可见、空白、语义失败或该粒度无有效片段的节点。进入节点时从首/尾边界开始，忽略其历史游标；文字范围仍取现有片段可见切片，不加载章节、不重新分页、不改变ReadingPosition。选择清除沿既有入口执行，接受后更新目标游标并转移标准辅助功能焦点，再复核启用状态与切片身份，遍历事件的索引/文本长度属于目标节点。

该实现覆盖分页同一正文视图内的相邻段落普通移动。滚动模式的各行使用独立ReaderBodyView，跨行/跨视图协调、跨节点扩展选区以及离开当前可见页的导航仍未完成；不通过自动扩大选区或翻页掩盖这一缺口。Debug构建通过，未新增或运行测试；TalkBack焦点、事件顺序、双向/各粒度/语义块跳过、节点重绑与服务重入仍待设备验证。


### 同一正文视图跨节点扩展选区

BodyAccessibilityHelper已支持EXTEND_SELECTION在同一ReaderBodyView的相邻可见节点间继续字符/词/段落移动。扩展会话保存固定ContentAnchor、固定节点原文切片、当前游标节点和选区版本；换绑定/清焦点/显式设置选区清会话，外部选区变化或固定切片变化使旧会话失效。进入相邻节点时移动游标从该节点方向边界开始，固定端点不随焦点移动，反向移动可收缩、越过固定端点再次扩展；两端相同沿原清除选区入口处理。

内部ReaderBodyView新增双锚点回调，分页/滚动绑定均验证活动候选、代次及既有可用性后调用TextSelectionResolver.fromAnchors。解析器在原ChapterContent中检查两端字符边界，按原块顺序规范化范围，再沿extract获得原文摘录；中间非文本块不能形成有效选区。最终通过既有reportSelection提交并沿原装饰/工具栏/复制/高亮规则执行，不新增公开API或选区模型。接受后复核目标节点投影、固定切片和焦点转移后的选区版本，防止宿主重入后把过期游标写入新选区。

本段更新前述同一正文视图跨节点扩展的待实现状态。滚动行仍是独立ReaderBodyView，因此跨行协调和跨可见页导航尚未完成；固定节点不再可见时不会继续使用旧切片。Debug构建通过，未新增或运行测试；真实服务的双向扩展/跨端点、Unicode、重绑/选区重入、复制高亮与焦点事件仍待设备验证。


### 滚动正文相邻可见行移动

ReaderBodyView提供内部边界进入方法与相邻行移动回调。当前节点和本行节点在普通字符/词/段落移动中已无后续内容时，ScrollingBookPresentation查询当前候选RecyclerView的已挂载行，按adapter阅读顺序尝试相邻正文行。只扫描childCount对应位置，避免大型章节所有离屏chunk的线性遍历；非正文、不可见或没有该粒度有效文本的行跳过。目标行按方向首/尾边界初始化游标，复用原遍历/清除选区/标准焦点转移/事件流程；成功后清理仍绑定原片段的源行焦点，防止服务回调重绑后清理新行。

回调要求活动候选、内容代次、空闲导航、布局稳定，以及源行对象身份与adapter位置一致；目标失败后再次检查候选/源行。行回收清除回调，外部边界进入不再向外递归，避免相邻行循环。普通移动可以进入当前可见的下一章正文，选区仍沿已有清除入口处理。此实现不提交ReadingPosition、不滚动列表、不访问数据来源或扩大窗口；离屏导航继续使用Widget原前后动作。

本段更新此前滚动跨可见行普通移动的待实现状态。跨行扩展选区和离开可见范围的文本粒度导航尚未完成；跨节点扩展仍只覆盖同一正文视图。Debug构建通过，未新增或运行测试；真实TalkBack跨宿主View焦点、语义/占位行、快速复用/重排/回调重入及事件顺序仍待设备验证。


### 滚动跨可见行扩展选区与同步提交

相邻行回调现可传递内部AccessibilityTraversalAnchor：固定原文锚点及其原节点/切片的有效性检查。目标行从方向边界继续移动，保存固定端点和本行游标/版本；固定节点仍须可见并保持原绑定。双锚点比较由TextSelectionResolver按原ChapterContent块顺序提供，节点投影不依赖不同View的虚拟ID顺序。同章节可见行允许双向扩展/收缩/跨固定端点，滚动回调在章节对象边界停止扩展，普通移动保留跨可见章节能力；选区仍为既有同章TextRange。回收/隐藏原节点会使固定端点验证失败，未建立离屏文本选择协调器。

滚动辅助功能选取/扩展/清除接受后，立即刷新当前候选已挂载行的选区装饰，再由辅助功能检查投影与版本、发布事件；常规装饰帧保留现有调度。修复此前reportSelection成功但辅助功能同步检查仍看到上一帧范围的问题。分页/滚动回调还检查提交后host.selection仍等于请求或确实为空，宿主监听重入替换选区时不报告旧动作成功。原书籍/代次/布局/行身份检查继续生效。

本段更新跨可见滚动行扩展的待实现状态。离开可见范围的文本粒度导航/选区协调、实际TalkBack跨View焦点和完整设备验收仍未完成。Debug构建通过，未新增或运行测试；真实服务双向扩展、相同块跨chunk、Unicode、章节边界、行回收/重排/监听重入和复制/高亮仍待验证。

### 缓存估算计费与未知资源

布局缓存已移除pageCount×2048固定估算。ContentBlock增加可选estimatedRetainedBytes（默认null），表示不可变块连同ID/负载持有的稳定非负估算；TextBlock/HeadingBlock按UTF-16文本与对象开销提供默认值。未知/负数估算使该章节不进入正文或布局缓存，但仍可正常准备和呈现；自定义块可主动提供估算。BlockLayout同名属性包含测量单位和渲染资源，排除独立计费的ChapterContent；ChapterLayout同名属性是总估算。默认null保持自定义实现可用，不以小常数假定未知资源。

内置文本布局按文本副本、StaticLayout/paint基础开销与每行结构估算；边距包装计入复制单位。PaginationEngine在工作线程累加正文快照、各块测量结构、页列表和共享分页/连续片段包装，形成不可变总估算；任一资源未知则总量未知。累加饱和，过大值不溢出为小值。两个缓存各自保守计入借用正文，可能重复计费共享对象。这不是精确Java/native堆计量。

ByteBudgetCache保存条目入库计费值，删除/淘汰不重新调用估算器；零值至少计1字节，null/负值/超过预算不准入。加入前先淘汰至budget-newBytes，保证Long总量不溢出；预算setter立即trim。LRU命中保留，显示工作集仍由呈现持有。LoadingOptions约束缓存估算，不能当作显示章节、在途排版或全应用内存上限。Debug构建通过，未新增或运行测试；实际heap/native校准、峰值/长章/自定义资源和长时间趋势仍待设备验证。

### 正文淘汰后的排版复用

ReaderCoordinator.LayoutKey已从ChapterContent对象身份改为内置文本的bookId/chapterId/revision/SHA-256内容指纹，加原viewport/style/字号/provider输入。ChapterRepository在工作线程先完成指纹，因此主线程构造键不再次扫描或摘要全文；来源重读生成新不可变快照，但原块ID/标题级别/文本及revision相同，仍可命中已保留布局或合并相同排版请求。布局仍持有原内容快照，指纹证明内置文本的结构/原文一致；修订、锚点或文字变化产生新键，显式章节/目录失效仍按chapterId清理。

包含自定义块时contentFingerprint为null，键继续保留ChapterContent实例身份，不以版本或内置文本指纹推断未知负载相同。缓存键中的章节字段用于失效，不再为纯文本键额外持有新快照。命中布局时若公开分页状态不是该layoutKey的Ready，重新发布正确页数/键；避免之前其他排版的失败/计算状态仍显示在已复用的布局上。缓存不足仍正常重读/重排，不新增隐式的无界正文保留。

Debug构建通过，未新增或运行测试；小正文缓存与较大布局缓存、相同/不同修订和块结构、自定义负载、重读/合并/失效以及分页状态通知实际行为仍待验证。

### 自定义块渲染器的缓存身份

内置文本指纹的跨快照复用现限定为默认provider链。只要宿主注册了BlockLayoutProvider，LayoutKey保留ChapterContent实例身份，即使块全为TextBlock/HeadingBlock；自定义measure可以依赖块实例或宿主负载，不能只凭内置文本指纹推断结果相同。默认链的等价文本复用仍保留。

布局/全书索引输入把每个provider捕获为实例身份加注册时读取的id/version值。比较使用引用身份，不调用插件equals来合并不同渲染器；版本值是键的独立快照，后续改变getter不改旧键。排版请求使用同一捕获链的实例与顺序。公开SPI注释明确id稳定、布局行为变化需升级version并重新setBlockLayoutProviders；测量中不得修改provider。此约定仍为宿主责任，库不会监控任意插件字段变化。

该补记收紧前述指纹复用范围，保障Widget扩展面的一致性。Debug构建通过，未新增或运行测试；覆盖内置块的自定义provider、插件相等/版本更新、快速重新注册、在途布局/索引及缓存行为仍待验证。

### 分页状态的请求归属与取消

ReaderCoordinator除布局键的共享请求外，保存每章分页状态的请求归属。新任务开始取得归属并发布Calculating；只有仍为当前归属的任务可发布Ready/Failed，较早的不同布局请求可以完成自身结果和合法缓存，不覆盖后开始任务的公开状态。缓存命中解除旧计算的状态归属并发布对应Ready，旧任务取消不得清除已命中的布局状态。

最后消费者取消后，若取消任务仍持有该章Calculating归属，发布Unknown，避免永久计算中。任务成功/失败在入库和通知前检查当前协程有效性、会话及请求表中的实例；状态通知后再检查，宿主监听重入失效不能继续返回过期成功/失败。整体布局失效先清请求/状态归属及缓存，再取消捕获的旧任务；章节与目录选择性失效解除相关归属，取消终态不能恢复被失效的状态。共享消费者计数与实际取消机制不变。

Debug构建通过，未新增或运行测试；同章不同布局完成顺序、索引/前台共享、消费者取消、缓存命中时取消以及宿主状态监听重入仍待实际验证。归属只描述粗粒度分页状态，阅读位置依旧由导航提交。

### 章节请求在状态回调后的有效性

ChapterRepository共享请求现同时检查仓库未关闭、章节代次与inFlight中的请求实例。发布Loading后先检查当前协程与请求有效性，再申请读取并发许可；加载监听中关闭/失效时不继续读取。来源返回、正文通知与Loaded状态通知之后继续复核，失败分支在发布Failed前后同样检查协程和请求归属，取消/替换后不返回旧失败。回调桥接仍由原请求机制处理终态。

章节失效先移除旧inFlight及缓存，再取消捕获请求并发布Unloaded；旧完成处理器不能移除新请求或恢复旧状态。并发许可释放、消费者合并与末消费者取消保留。Debug构建通过，未新增或运行测试；Loading/Failed监听中换书/更新/重试、来源忽略取消、等待许可取消和真实终态顺序仍待验证。协调器涉及多个子系统的完整失效通知顺序仍需继续检查，不把局部检查视为全链路验收。

### 章节失效的清理与通知顺序

ChapterRepository提供内部invalidateSilently：先增加章节代次、解除旧请求/缓存并写Unloaded，再取消旧请求；返回延后通知动作，执行时复核仓库未关闭、代次未变化且状态仍为Unloaded，避免后来已启动的Loading被旧通知覆盖。普通仓库invalidate沿同一入口立即通知。

ReaderCoordinator章节失效先解除旧预载、布局请求/状态归属与缓存，再清正文仓库，取消捕获的旧任务，然后使全书索引失效并发布分页/章节通知。后续阶段检查会话、仓库实例与失效序号；监听重入换书或再次失效返回Superseded。若通知期间新分页状态已建立，旧Unknown不覆盖它。整体布局失效也先清布局请求/状态/缓存，再取消并使索引失效；取消回调换书时不操作新会话索引。

BookPageIndexBuilder失效返回代次令牌；取消旧请求后只有代次仍相同且没有新请求/索引才发布Unavailable。构建入口检查失效通知期间是否已有新代次，避免覆盖监听回调中发起的新构建。BookReadView的当前/邻章retry检查协调器失效结果，Superseded后不继续刷新或加载回调中新会话的章节。

Debug构建通过，未新增或运行测试；多层取消、Unavailable/Unknown/Unloaded回调中的换书/再失效/重试/索引构建及真实终态顺序仍待验证。这里规定更新事务的阶段与归属，不把同步回调视为无重入的普通函数。

### 目录安装的会话与通知归属

ReaderCoordinator.installCatalogue现返回ReaderResult<Unit>。先捕获会话/仓库及本次目录版本，安装描述并解除旧预载、非复用章节的布局请求/状态归属与缓存，再更新正文仓库并取消捕获任务；随后失效索引、发布书籍通知。取消或通知后复核会话、仓库、目录版本和描述实例，换书或再次安装时返回Superseded，不继续通知或恢复旧目录位置。目录读取失败/取消仍不进入安装阶段，保留既有目录规则。

安装期间的章节通知抑制由独立安装owner管理，finally仅解除仍属于自己的owner，替代原共享Boolean，避免嵌套安装结束时旧流程错误解除新安装的抑制。BookReadView.reloadBook检查安装结果，再执行位置恢复；旧刷新被替代后不会继续导航到旧描述。可复用章节规则仍依赖明确revision，目录顺序与bookId校验保留。

Debug构建通过，未新增或运行测试；取消回调/索引状态/书籍通知中的换书与再次刷新、保留/更新/删除章节、空目录及失败保留实际行为仍待验证。

### 全书索引失败通知与重试归属

BookPageIndexBuilder对同输入共享构建追加状态条件：Failed通知期间的再次请求不合并到仍处于isActive但已准备返回失败的旧任务，改为按既有invalidate/新代次构建。普通Building同输入仍合并，已有完整index仍直接返回。构建以代次及request实例共同确认归属；初始Building通知后、各章layout返回后、失败/Ready通知后检查协程活动与归属，替代后不发布或返回旧失败/成功。

异常分支同样先复核再发布Failed，通知后再次检查；finally只有仍持有本次请求且没有完整索引、没有Failed终态时才发布Unavailable。最后消费者取消仍取消共享任务，内容/目录/布局失效仍返回Superseded；旧任务的完成处理器不能清除新request。Debug构建通过，未新增或运行测试；Failed中同步重试、Building/Ready/Unavailable中取消换书、共享消费者和异常/回调重入实际顺序仍待验证。

### 章节与分页的失败重试合并

ChapterRepository在Failed状态通知期间的新get不消费仍未结束的旧失败请求，创建新共享读取；旧请求在回调后用实例检查返回Superseded。正常Loading请求依旧合并。ReaderCoordinator以失败请求实例集合标记即将返回失败的布局任务，标记发生在Failed通知前；同布局键重试跳过该失败任务，其他仍在计算的任务继续共享。使用请求实例而非每章粗粒度Failed状态，避免不同布局输入的失败阻止合法共享。完成时移除自己的标记，旧完成处理器不清新请求。

布局缓存命中发布Ready后也检查协程有效性、会话、目录版本和失效序号；监听中换书/目录安装/内容或布局失效时不继续返回旧缓存结果。缓存预算调整和普通淘汰不被当作内容失效，已借出的布局仍可使用。Debug构建通过，未新增或运行测试；Failed中的直接请求/显式retry、同章不同布局、重试乱序、缓存Ready通知中重入及真实终态仍待验证。

### Widget恢复和错误通知后的结果归属

BookReadView打开书籍在目录失败、空目录通知、加载阶段通知、初始回退导航及最终错误通知后复核openSequence；reloadBook的目录失败、空目录和最终恢复错误同样复核。通知中的换书/重新打开使旧操作返回Superseded，而不是把旧Failed/Empty/Rejected返回成仍适用于新会话的结果。章节恢复在onPositionRestored及错误通知后复核contentGeneration，旧恢复不继续报告成功或旧错误。

invalidateChapter/retryChapter在内容代次失效前捕获书籍描述实例与打开序号，失效回调后先复核再调用协调器，防止换书后相同chapterId命中新书。协调器返回及邻章读取/错误通知后继续检查，失效或重试被替代则终止。位置仍由统一导航提交，不新增恢复位置事件。Debug构建通过，未新增或运行测试；错误/空目录/恢复通知中的换书、同名章节重试、回退与取消终态实际行为仍待验证。

### 暂停与关闭的资源所有权

ReaderCoordinator.suspendWork先推进工作代次，并捕获旧仓库/作用域/预载/布局任务，解除当前字段和所有布局状态/缓存归属，然后失效索引，最后关闭或取消捕获的旧对象。索引及取消回调中新建的工作不属于该捕获集合，旧清理不再给当前repository/sessionScope赋null或全量清掉新缓存。章节仓库自身close也先清请求/许可等待者登记，再取消捕获对象。

close先推进书籍会话并清source/book，再暂停旧工作；旧取消回调不能通过resumeWork重建被关闭来源。关闭通知后返回是否仍属于该关闭会话，open尊重被替代的关闭结果，不覆盖通知中开启的新来源。createRepository另捕获工作代次，解决同会话detach/attach重建仓库时只看sessionId仍可能接收旧仓库通知的问题；索引观察通知后再次复核再输出正文。

Debug构建通过，未新增或运行测试；关闭/暂停取消回调中的重开/挂载、同书籍工作重建、并发许可和来源不响应取消、实际资源释放仍待验证。工作代次只约束资源结果，不增加阅读位置事件。

### 邻章预加载调度的归属

preloadNear先验证ReadingPosition.bookId属于当前描述，再捕获书籍/仓库/作用域、工作代次和LoadingOptions，使用独立预加载调度序号。过期预载先移除登记再取消捕获Job；取消后及每个新Job.start后复核调度归属。Loading或取消通知中换书、工作重建、修改配置或新位置重入调度时，旧计划停止，不按旧章节集合继续启动任务。Job仍先登记再延迟启动，完成处理器只移除自身。

前台getChapter在取消无关邻章前捕获原书籍/仓库/工作代次，并推进调度序号停止旧预载计划；先移除所有需取消登记再取消，读取前后检查上下文，旧来源结果不返回到新书。前台读取结果不因单纯新预载计划出现而失效，导航仍由原事务处理。预加载数量、IO并发、缓存和消费者共享规则保持。Debug构建通过，未新增或运行测试；Loading/取消通知中快速导航/换书/配置调整、同名章节、工作重建和预载失败实况仍待验证。

### 滚动边界任务与候选关闭

ScrollCandidate增加独立closed标记，close幂等且先标记关闭、解除loading登记再取消捕获Job。reportAndLoad及loadBoundary拒绝已关闭候选，取消/停止滚动回调不能重入启动其工作。每个边界Job同时绑定候选、host内容代次、BookDescriptor实例和loading方向槽中的任务实例。

重试后、章节读取后及异常处理检查协程活动和任务归属；导航忙碌或拖动选区期间不安装读取结果，避免修改正在准备导航的旧列表。rebuild之后重新检查才seek，初始Loading列表/seek阶段若发生替代则取消尚未启动任务。失败仅记入仍活动且空闲的候选；既有章节窗口最多三章、可见锚点保留与失败重试流程不变。被丢弃的读取可以沿已有缓存供后续准备，未增加后台副本或新的位置提交入口。

Debug构建通过，未新增或运行测试；边界读取与程序导航/取消/选区重叠、重试回调换书、候选关闭及列表锚定实际行为仍待验证。模板解绑异常及完整呈现清理路径仍需继续检查。

### 页面模板清理异常

分页与滚动呈现共用内部cleanupReaderPage，逐项执行占位/列表清理、移除root、template.unbind；某项Exception不阻止后续步骤，首异常保留其余suppressed原因，清理异常不替换原准备失败或取消。分页候选增加幂等close，show替换、detach和未显示dispose沿同一入口；滚动原候选close接入相同逻辑。

旧页面解绑异常不再从show向外抛出，避免新页面已显示却让导航按失败收尾。错误记录本地日志，并延后通过host.reportError报告NAVIGATION/PageCleanupFailed；绑定原候选内容代次且要求呈现未detached，不把旧候选的清理诊断发给新书。延后报告使导航先完成既有提交，诊断不增加位置事件。释放后没有可用监听时仍留本地日志。清理函数只处理普通Exception，致命虚拟机错误不被伪装为可恢复结果。

Debug构建通过，未新增或运行测试；自定义模板unbind抛错、前序清理抛错、取消/换书/模式切换、幂等dispose和资源释放仍待验证。宿主模板仍应自行释放所持资源，库不能替代插件内部清理。

### 模板重入后的显示流程

分页show在装饰更新与root可见性变化后检查当前协程、内容代次、detached和原活动候选身份，确认有效才转移新候选所有权。滚动show在可见性变化和旧模板close后作同样复核，旧unbind/原生View回调触发换书、模式切换或替代导航时，不继续对已关闭列表installInput。延后装饰/位置工作也检查活动候选、代次和detached。

取消沿原协程传播；有效性已变化时呈现提前停止后续操作，将最终确认留给NavigationController原有show后token/generation检查，不在呈现新增位置提交或伪失败。滚动detach先解除active引用，再停止拖动/取消作用域和关闭捕获候选，清理回调不能继续查询旧活动页。Debug构建通过，未新增或运行测试；root可见性/模板unbind/装饰回调中的换书、模式切换、强制导航和监听释放仍待验证。

### 创建候选时的模板所有权

工厂成功返回模板后，fresh root/bodyHost关系校验、正文视图创建/挂载、首次bind与root安装纳入同一try/finally。记录是否取得未挂载模板及是否尝试root安装；失败时移除Widget创建的正文/list视图，再沿共享清理解绑模板。主动close也移除组件正文/list，模板被宿主保留时不继续包含旧库视图。首次bind后复核协程/内容代次，并要求root仍未挂载，再安装候选。

工厂返回已有parent的root表示未取得该页面所有权，拒绝后不移除该root或调用其unbind，避免误清当前活动页/宿主外部页面。无parent但bodyHost不在root内的无效新模板仍执行解绑；库未在其bodyHost挂载正文。create本身抛错时没有返回对象，工厂自行清理其内部半成品。Debug构建通过，未新增或运行测试；校验/构造/挂载/bind失败、bind内换书或外部挂载、工厂误复用已显示root和资源释放实际行为仍待验证。

### 跨 Widget 的模板预留

分页与滚动呈现共用内部 ReaderPageOwnership，在校验和首次 bind 前按模板实例与 root 实例预留所有权；预留覆盖尚未挂载的准备候选及活动页面，避免同一工厂向多个 Widget 或重入准备返回相同对象。判断使用对象身份，不依赖宿主 equals。已有 parent、模板已预留或 root 已预留时拒绝候选，不移除或解绑其他使用者的页面。

共享清理完成正文/list、root 和 unbind 的全部普通异常处理后才解除预留；unbind 回调重入仍不能取得尚在释放的模板。清理普通 Exception 后也解除预留，致命错误仍沿既有约定传播。预留表仅持有未释放候选；正常关闭、取消和失败沿同一清理入口解除。SPI 明确 root/bodyHost 在模板生命期保持稳定且 getter 不抛错，create 未返回有效模板前的半成品由工厂释放。未增加公开所有权接口或持久化状态。

Debug 构建通过，未新增或运行测试；共用工厂的多 Widget、未挂载候选重叠、复用模板/root、unbind 重入及异常、取消/关闭的资源释放仍需实际验证。

### 占位视图预留与回调清理

ReaderPlaceholderController 按 View 实例共享主线程预留，覆盖 create 返回后、bind、显示与 unbind 的归属；另一个控制器返回已预留或已挂载视图时拒绝，降级默认占位，不解绑或移除原使用者的视图。SPI 明确每次创建独立未占用 View，bind 只更新内容而不挂载；首次 bind 后复核 parent，组件负责安装。

显示与 clear 共用更新边界，回调中的显示/清理请求先推进序号并保存最新意图，当前 bind 结束后清理失效绑定，再延后处理；不会在 bind 内直接嵌套 unbind 或在 unbind 内重新绑定。移除与解绑分别处理普通 Exception，某一步失败仍尝试下一步，最后解除预留。重试异常同样只处理普通 Exception，致命错误传播。既有绑定动作失效、一次重试与默认工厂降级规则保持。

Debug 构建通过，未新增或运行测试；跨 Widget/列表 holder 误复用、create/bind/unbind 中切书或清理、挂载错误、连续更新与实际释放仍待验证。

### 选区工具栏创建与释放

SelectionToolbarFactory 增加默认空实现 unbind(view)，已有源码实现无需补方法即可重新编译；宿主可在此解除监听和资源引用。create 返回的合法独立 View 在弹窗替换、暂停、关闭、准备失败或被替代时沿幂等清理调用 unbind。工厂未返回 View 前的半成品由工厂自行清理。已挂载或被其他控制器预留的 root 拒绝且不解绑他人对象。

SelectionToolbarController 使用调用序号隔离 show/dismiss/pause，create、测量及原生 ActionMode 创建后复核；自定义 root 按实例跨控制器预留，释放回调完成后解除。关闭先解除活动引用，再分别移除弹窗监听、关闭窗口、解除窗口内容和解绑，普通 Exception 记录后继续下一步。自然关闭只对仍属于该流程的选区调用 clearSelection；旧准备异常只记日志，不关闭或报告到替代工具栏。BookReadView 错误处理也复核选区修订号、对象及内容代次。默认 ActionMode 仍在拖动时保留并禁用动作，旧 mode 的菜单回调拒绝操作新绑定。

Debug 构建通过，未新增或运行测试；自定义 create/unbind/测量回调重入、跨 Widget 误复用、弹窗显示失败与自然关闭、原生工具栏创建/关闭以及实际资源释放仍待设备验证。

### 工厂视图所有权的共同实现

ReaderViewOwnership 统一按 View 实例管理页面 root、占位 View 与自定义选区工具栏 root 的主线程预留，不再各自维护隔离的视图表。三类使用者之间的错误交叉复用也被拒绝；未取得所有权时不解绑或移除他人资源。释放同时核对视图与原 owner 实例，旧清理不能解除替代绑定。ReaderPageOwnership 仅保留模板实例到 root 的约束，并委托共同视图表；占位和工具栏使用单次绑定实例作为 owner。

各使用者保留原有准备、取消、重入及清理边界，共同组件只承担 acquire/release，不管理宿主回调或公开状态。架构文档新增对应职责表。Debug 构建通过，未新增或运行测试；跨工厂类型/多 Widget 误复用、旧回调释放与正常清理的实际行为仍待验证。

### 默认工具栏恢复与 Demo 扩展接入

setSelectionToolbarFactory 接受 null，恢复组件原生 ActionMode；当前选区仍沿原动作/修订号显示工具栏，不重建选区或标注。运行时工厂依旧由宿主提供，不进入 Widget SavedState 或 ReaderSnapshot。

CustomSelectionToolbarFactory 只依赖公开 SelectionToolbarFactory/SelectionActions 与样式模型，每次创建独立视图，提供选择长度提示、复制、高亮与取消；失败/拒绝/被替代结果有提示，成功行为由受控动作完成。unbind 清除监听及提示文本。输入设置可在系统默认和该示例之间切换；宿主偏好 JSON 与 Activity 状态保存选择标志，恢复时重建工厂，旧偏好缺字段默认系统工具栏。示例采用自己的结构和当前主题底色/文字色，不宣称套用默认页眉排版。

Debug 构建通过，未新增或运行测试；长按后的切换、复制/高亮结果、解绑、旋转/冷启动及窄窗口和辅助功能交互仍待设备验证。

### 自定义正文块的宿主示例

DividerBlock/DividerLayoutProvider 展示公开 ContentBlock 与 BlockLayoutProvider 接入。块为不可变图形分隔标记，稳定 blockId、非负持有字节估算；provider 有固定 id/version，工作线程只读取布局上下文、检查取消和创建绘制数据，不使用 View 或 Activity。单个 24dp 单位提供同一 fragment 给分页和连续流，组件仍应用 block padding；绘制结果不持有完整上下文或其取消回调。布局提供保守资源估算。

图形没有可选择原文，start/end offset 为 null，不伪造文字几何；提供完整无障碍描述和 block/0 的可见位置锚点。跨越该非文本块的文字选区仍按现有 UnsupportedContent 规则拒绝，普通文本能力保持。块超过真实视口时沿既有排版失败处理，不缩放正文或绕过排版约束。

CustomContentActivity 独立书籍接入该 provider，菜单“打开书籍与演示→自定义正文块演示”可查看并切换分页/滚动、前后导航或重试。固定 Widget ID 沿控件 View 状态恢复路径；onStart 延后打开，onStop 取消未完成打开并移除排队回调，打开序号避免旧完成清掉新请求，onDestroy 释放控件。未改普通全文本样例或引入图像来源功能。

Debug 构建通过，未新增或运行测试；图形跨页边界、锚点恢复/旋转、滚动切换、缓存趋势、取消及无障碍朗读仍待设备验证。

### 分页普通文本遍历的离屏衔接

ReaderBodyView/BodyAccessibilityHelper 增加独立的异步离屏入口。可见节点与可见相邻行均无后续文字时，普通字符/词/段落移动可以接受一个待执行请求；接受只代表已登记，不提前清除源焦点或伪造文本遍历事件。启动前复核源片段、可见文本切片、选区修订号、原虚拟节点焦点和呈现身份。选区扩展仍走已有可见范围逻辑，本轮不把扩展伪装成普通翻页。

PagedBookPresentation 每次只持有一个遍历 Job，主线程延后启动，拒绝忙碌、手柄拖动及书籍边界。通过 BookPresentationHost.requestAccessibilityNavigation 进入原导航控制器；Widget 实现关闭动画并不受普通翻页间隔限制，仍输出 UserNavigation 的真实单次提交。SPI 默认委托自定义宿主已有 requestNavigation，保留源码接入兼容。成功后检查原书籍、内容代次、当前候选、结果位置与提交位置，再在新正文边界执行同一粒度并发送已有文本/焦点事件。

失败/取消/被替代不提前移除原正文焦点；尺寸变化取消遍历，detach 取消所在作用域。普通异常沿原错误通道处理，取消正常传播。目标页没有可遍历文字时，已提交页面保持有效，但不会伪造文字节点或连续自动读取后续章；这类空/无语义页的辅助焦点衔接仍待补齐。滚动离屏移动及离屏选区扩展继续待实现。

Debug 构建通过，未新增或运行测试；TalkBack/SwitchAccess 实际异步动作返回、页/章边界、加载失败、焦点切换、取消/换书/重排和空页行为仍待设备验证，不能以构建证明无障碍验收完成。

### 滚动普通文字遍历的离屏衔接

异步离屏请求增加可选原文边界锚点。BodyAccessibilityHelper 提供 traverseAtAnchor：在目标可见节点的精确 source range 内定位游标，确认该粒度存在可移动区间后续接现有文本遍历、选区清理与焦点事件。分页入口保留相邻页策略；滚动入口使用锚点，避免把普通一屏滚动直接当成内置正文的精确文字续读。

ScrollingBookPresentation 每次仅登记一个 Job，延后启动并检查源焦点/切片、活动候选、书籍/内容代次、导航忙状态与手柄拖动。对内置 TextBlock/HeadingBlock，在工作线程从原文边界找相同粒度的下一/上一段，分别保存显示锚点和进入游标；块扫描检查取消，计算后回主线程复核。确到章末/章首时只借用紧邻章快照，不预读整本书。通过既有 requestAccessibilityNavigation 的无动画统一提交路径显示目标，按章节及锚点在已挂载目标正文行中续接焦点，不以 RecyclerView item 序号代替阅读位置。

目标/当前提交位置、书籍和候选仍有效才操作虚拟节点；列表回收清除离屏回调，尺寸变化取消 Job，detach 取消作用域，异常本地记录且取消正常传播。已挂载行仍优先使用原可见遍历。自定义内容缺少可解析原文、空章或未能找到源目标时保留普通视口导航与边界遍历回退，不宣称该回退具有精确文字连续性；这些无语义/自定义块的协调以及离屏选区扩展继续待补齐。

Debug 构建通过，未新增或运行测试；TalkBack/SwitchAccess 异步返回、半行/字素/词边界、正反向跨块/跨章、空章/自定义内容、列表回收、失败/取消/重排与实际焦点事件仍待设备验证。

### 共用离屏原文定位与明确结果

分页与滚动改用内部 AccessibilitySourceTraversal，删除滚动私有的重复查找逻辑。定位器只读取不可变章节快照并接受取消检查，不操作 View、加载来源或提交位置；Found 包含显示位置与进入游标，Exhausted 表示内置文字在该方向确已耗尽，Unsupported 表示遇到不能解析的自定义块，Invalid 表示未知块/偏移/字符边界或粒度。无效输入停止，不降级到无关视口；章内耗尽才查紧邻章，邻章失败或仍无目标停止并保留原焦点，不把它与自定义回退混用。

分页普通离屏移动也使用原文锚点和目标游标，不仅进入相邻页的首尾；worker 定位后复核原请求，再沿统一导航显示目标。对缺少源语义的自定义内容保留原页/视口回退，精确连续性仍未完成。两种呈现只借用紧邻章，不为一次动作连续扫描整本书；连续空/无目标章节的辅助衔接继续待处理。

AccessibilityTextTraversal 词识别改为 Unicode 码点判断字母/数字，支持补充平面字符，去掉每次候选 substring 分配。worker 块/粒度候选及字符扫描检查取消；平台 BreakIterator 仍提供字符与词边界。架构职责同步。Debug 构建通过，未新增或运行测试；正反向页/章/块边界、Unicode 与取消、空/自定义内容、离屏选区扩展及辅助服务设备验收仍待完成。

### 连续空章与自定义块定位

新增内部 AccessibilityChapterTraversal，分页与滚动共用其逐章借用流程，移除各自重复的邻章处理。纯原文定位器确认 Exhausted 后才沿目录前/后方向继续；每次 worker/来源挂起前后核对原请求并检查取消，章节间 yield。只持有当前借用快照，不收集整书正文或构建全书分页；既有仓库仍决定共享读取和缓存预算。中间空章或该粒度无文字章不提交位置，找到最终目标后由原导航单次提交。失败/无效/被替代或最终书边界停止，保留源焦点。连续无目标章很多时可能逐一读取到边界，不能保证固定读取次数；用户改变焦点、导航或内容后停止旧查找。

Unsupported 结果携带遇到的自定义块 block/0 位置。呈现可以定位该块后，通过 traverseAtBlock 从其可见语义边界进入，不再把跨空章后遇到的块误定位到旧视口。该入口仅做普通文字遍历，不伪造可选择源范围；块没有对应描述/粒度、block/0 无有效映射，或自定义描述自身离屏续接时仍存在待完善边界。未知源切片保留原页/视口回退，复杂自定义语义及离屏选区扩展尚未完成。

Debug 构建通过，未新增或运行测试；连续空/无词章、最终边界、来源失败/取消、正反向自定义块描述、模式/重排/焦点变化及辅助服务实际事件仍待设备验证。

### 自定义整块语义的完成边界

BlockLayoutFragment 新增默认 false 的 accessibilityDescribesWholeBlock，宿主仅在所有可访问垂直切片描述整个逻辑块时声明 true。完成此描述意味着可越过同一块剩余片段；局部文字/多片段未读内容不能误用。未提供原文范围的描述仍不可选择，该声明不伪造源偏移。

内部 AccessibilityTraversalBoundary 区分 Source 原文偏移与 CompletedBlock 身份。helper 在整块描述完成后，跳过同一正文中的同 blockId 片段；滚动已挂载行限定同章同块后跳过。离屏请求经共同源定位器核对块身份，沿方向从下一块开始，并保留原有连续空章/取消/归属规则。Demo 分隔块明确 opt-in；默认未声明的自定义实现继续采用旧保守行为。章节间相同 blockId 不被当作同一块。

Debug 构建通过，未新增或运行测试；整块描述正反向、同块多片段、边界/空章、语义 getter 异常、声明错误及辅助服务实际事件仍待设备验证。复杂局部自定义语义和离屏选区扩展继续待完善。

### 同章离屏选区扩展

ReaderBodyView 增加内部固定锚点保留回调，helper 在源可见切片耗尽时把固定端点转换为原文身份验证的 AccessibilityTraversalAnchor。preserveAccessibilityAnchor 绑定书籍实例、章节、内容代次和正文版本/指纹；内置指纹已由仓库在 IO 计算，不重新复制全文。缺少版本与指纹时保留原快照身份作为保守验证，不把另一次未知内容读入视为相同正文。固定端点不依赖旧虚拟节点继续挂载；当前源切片、焦点及选区投影版本仍需在准备前有效。

分页与滚动扩展共用原文定位、统一无动画导航和目标锚点遍历入口。扩展只在当前章查下一/上一粒度，不沿连续空章查找，不进入邻章，不把 Unsupported 自定义块降级成普通移动。显示并提交后检查原选区未被改变、固定内容身份及活动候选/实际位置，再调用既有 fromAnchors/reportSelection。目标虚拟节点按源游标继续更新扩展范围，接受后才发送焦点、文字与选择事件；后续可继续扩展或反向缩小。每个动作最终数据仍是原 TextSelection，没有新增第二份选择模型或位置提交入口。

失败/取消/被替代在准备期间保留原选区与源焦点；已发生的有效页面提交不伪装回滚。换书、内容/视口变化或回调改变选区时不继续提交过期扩展。滚动回收清掉保留锚点回调，固定描述依赖轻量元数据；未知内容身份的保守快照保留需纳入实际资源验证。跨章选择仍明确不支持，复杂自定义局部语义仍未完成。

Debug 构建通过，未新增或运行测试；同章正反向跨页/离屏行、首次扩展、缩至固定点、缓存预算为零/无版本内容、选区回调重入、取消/重排和 TalkBack/SwitchAccess 焦点/事件实况仍待设备验证。

### 离屏请求进入导航后的有效性检查

BaseReadView 保留原 navigateWithReason 三参数接入，新增带主线程 isCurrent 的受保护重载；进入导航、状态回调后的 prepare 入口及调用呈现 show 前检查请求，效果真正执行 show 回调时再次检查。失效沿 SupersededNavigation 返回原 Superseded，NavigationController 仍唯一提交位置，并在 finally 释放已取得候选。没有另建焦点导航控制器或提交入口。

BookPresentationHost 新增带 isCurrent 的 requestAccessibilityNavigation 重载，既有两参数方法保留。Widget host 同时核对启用/挂载/内容代次与调用者原请求；自定义 host 默认只提供入口检查，可覆盖该重载接入自己的显示前检查。分页/滚动传入源候选、书籍、焦点/切片、选区与固定端点有效性，源检查不包含 navigationBusy，避免请求进入自身 Preparing/Settling 后把自己误判为忙碌失效；启动前仍拒绝其他忙碌导航。

这样加载或准备期间改变焦点/选区的迟到候选在调用呈现显示前被拒绝，原页和位置保持。谓词不在 show 完成后重新检查源节点，因为有效显示本身会释放旧页/源焦点；显示内部的模板回调仍沿呈现现有协程/内容代次/候选复核，不能据此声称已验证全部服务焦点交错。不会为了轮询焦点而新增定时器，也不承诺焦点变化立即中断正在共享的 IO。

Debug 构建通过，未新增或运行测试；来源/模板慢加载时焦点或选区变化、Preparing/Settling 回调重入、候选释放、显示内部事件及自定义 host 强校验仍待设备验证。

### 正文绑定的释放约束

ReaderBodyView 内部使用 clearContentBinding 统一结束一次正文绑定：先撤销选择、导航及固定端点回调，取消排队的无障碍刷新，再清空选择几何、片段、虚拟节点、遍历游标和扩展固定点。不新增公开 API，宿主仍通过 Widget 与既有 SPI 接入。

分页候选关闭或准备失败、滚动行重新绑定/回收，以及滚动适配器关闭时均使用此入口。适配器记录所有已绑定而未回收的正文视图，覆盖暂存于 RecyclerView 缓存的行。临时 detach 仅清理焦点和刷新任务，保留内容供重新 attach 使用。普通服务通知异常记录日志并继续清理，不承诺处理致命虚拟机错误。

### 来源临时文件回收的落地修订

TextBookOptions.indexDirectory 与 EPUB 缓存目录现在使用 iread-temporary-v1 子目录。OwnedSourceFile 在创建时持有独立标记文件的进程锁，正常 close 等最后一个读租约释放后删除正文文件并释放标记；不把读租约的生命周期缩短到 source.close 调用本身。

创建新的磁盘来源快照时，尝试回收超过 24 小时且能够取得独占标记锁的文件，每次最多检查 64 个过期候选，并在遍历中检查取消。进程内已登记标记不会被打开，跨进程活动标记无法取得锁时保留。回收限定匹配标记及其同目录 payload，不递归删除，不处理旧版本直接创建的 iread-text-*.index / iread-epub-*.zip 或宿主原文件；拒绝管理子目录/候选符号链接。普通回收错误记录诊断，不阻止新快照建立；创建新文件无法建立租约时来源加载失败。

这是临时文件回收机制，不是持久索引缓存：loadBook 仍按原输入重建。进程异常退出后锁由系统释放，过期文件在后续创建磁盘来源时回收；不足 24 小时或再未创建来源的文件不会立即删除，时钟变化可能延迟回收。没有新增定时器或公开清理 API。实际文件锁、异常退出、多进程及删除行为仍需 Android 设备验证。

### TXT 可替换内容标识

TextBookOptions.contentIdProvider 接受公开 TextContentIdProvider，默认 null 保持 chapter-N / line-N。chapterId(TextChapterIdentity) 获得 bookId、从 1 开始的 chapterNumber（含前言）和最终目录标题；blockId(TextBlockIdentity) 获得 bookId、chapterNumber、从 1 开始的 blockNumber、规范化原始行 text 和 isHeading。接口方法有默认顺序编号实现，可只覆盖其中一种。公开上下文是不可变数据，不含 View、来源、可变解析器或完整章节。

策略在 IO 快照构建时调用，每个实际块一次，章节回调在其所有块回调之后执行（此时才能确定前言标题）。每次重建都会调用；策略必须对相同输入返回一致结果，不依赖可变调用计数，多个来源共享时需线程安全。组件不推断宿主业务身份，也不承诺任意插入/删除后的 ID 自动稳定。

共用 TextContentIdentity 校验非空白、有效 Unicode，章 ID 在全书唯一、块 ID 在章内唯一；拒绝不合规或重复 ID，不 trim、不修改、不替换失败值。策略异常使新加载失败；旧成功快照保留，磁盘半成品按原 owner 清理。默认顺序编号无需另存去重集合，自定义策略仅保留章 ID 集合及当前章块 ID 集合用于校验。

磁盘记录持久保存构建时得到的块 ID，loadChapter 读取并校验而不再调用策略；记录预算包含 ID 的 UTF-8 字节和长度字段。内存快照同样保存解析时得到的 ID。既有临时索引不跨进程复用，无需迁移旧记录。ID 策略不改变原文 UTF-16 offset；输出身份纳入快照 revision，高亮/恢复仍遵守修订号和 quote 校验；切换解析配置后宿主应重建来源会话。自定义策略的计算/分配成本由宿主承担，输入字节上限不等同于所有标识的内存预算。

### TXT 规范化与解析一致性

TextBookOptions.normalization 接受 TextNormalization，默认 PRESERVE 保留既有行为。TRIM_LINE_END 删除每行末尾的 Kotlin Unicode 空白，保留开头缩进；TRIM_LINE_EDGES 删除每行两端的 Kotlin Unicode 空白。全部方式只删除开头 BOM、处理 CRLF/CR 逻辑行边界、保留空行、末尾换行不额外生成块。纯空白但没有末尾换行的输入在 trim 后仍生成一个空文本块，真正空输入/只有 BOM 生成空章节。章节标题匹配仍使用处理后行的 trim 值；非默认模式是宿主显式选择，不在分页/绘制期间重复清理原文。

规范化先于标题识别、ID 策略和记录构建，ID 回调和输出 TextBlock/HeadingBlock 得到相同文本，锚点 UTF-16 offset 以该输出文本为准。TXT revision v2 通过版本标识、原始摘要、规范化策略和实际解析输出生成 SHA-256，默认 PRESERVE 也采用此规则；不同规范化策略即便恰好输出相同文本也保守区分。既有位置/标注按原策略验证修订号与 quote，不自动把旧偏移转换为新偏移。

内存/磁盘路径共用 TextSnapshotParser 和 TextLineReader，前者负责章节语法及身份，后者负责逐行规范化；存储回调分别创建不可变模型或写入有预算的记录。内存路径不再创建整书 BOM/newline 替换副本，但解码输入及完整模型仍有内存开销。规范化枚举不接受任意正文变换回调，复杂预处理由宿主 BookSource 提供。策略及记录会影响内容身份，宿主改变来源配置时需重建会话。

### TXT 来源配置的宿主示例

TextSourceActivity 通过 MainActivity 的“打开书籍与演示 → TXT 来源配置演示”进入，只使用 ReaderInput、TextBookSource/TextBookOptions、TextContentIdProvider、TextNormalization 与 ReaderView 公开接口。示例数据包含 BOM、CRLF、章节标题、缩进、行尾空白及重复空白行。提供内存/磁盘切换、三种规范化、顺序/内容摘要 ID、模式切换、翻页和重读。

示例的内容 ID 对唯一章节标题/非空行使用摘要，对重复空白行使用章内编号；这是该样例的身份策略，不是任意书籍的稳定 ID 算法。相同文本多次出现时，真实宿主需自行消除歧义，不能宣称摘要天然唯一。配置切换重建来源，保留最后快照交给组件恢复；身份/修订号变化时允许既有回退并提示。

onStop 先导出最后位置、撤销请求归属并取消，再 closeBook 与 close 来源；onStart 创建新来源并恢复，onDestroy release Widget。设置和轻量 ReaderSnapshot 以版本化 Bundle 保存，固定 View ID 接入控件状态；不保存来源、工厂、流或临时文件。网络与 TXT 示例共用 SnapshotState 宿主序列化格式，网络旧版 Bundle 字段保持兼容。没有向最近书籍记录新增样例项或冷启动书架持久化。

### 默认页脚的需求修订

依据 FR-VIEW-01，DEFAULT 页脚改为同时显示章节页进度与当前时间；默认页眉仍显示章节名。ChromeContent 在末尾追加 PAGE_PROGRESS_AND_TIME，保留既有序号，XML header/footer content 增加 page_progress_and_time=8。TIME 仍仅时间，PAGE_PROGRESS 仍仅进度。未知章节页数为省略号，尚无位置时组合项仅显示时间。

默认模板保存轻量进度前缀，分钟回调只替换当前时间，不抹除进度；unbind 清前缀和更新时间标志，继续使用每 Widget 的 ReaderPageClock。系统语言/时区/12/24小时格式与既有 TIME 一致。Demo 主页面与样式选择器已接入组合项；保存的显式旧选择保持其语义。单行页脚与固定高度仍可能在狭窄窗口/大字体时裁剪，构建成功不代表实际布局验收完成。

### TXT revision v2：原输入与实际输出

修复相同字节在不同编码/标题规则/身份策略下输出不同内容却拥有相同 revision 的缺口。TextSnapshotParser 逐块累计 ID、文本、块类型，逐章累计 ID、标题和块数，附带书名与 normalization；有长度前缀和事件类型，保持原始 UTF-16 表示，分块检查取消，不拼装第二份整书字符串。最终以 iRead:txt-snapshot:v2 域标识、原始 SHA-256 与解析输出摘要共同生成 revision。

内存/磁盘使用同一个解析结果摘要，存储位置和预算不参与身份。编码或标题规则变化但所有输出完全一致时仍可得到相同 revision；来源/策略对象实例不作为内容版本，实际改变的输出身份/结构/文字会改变版本。内存先暂存解析块列表和章元数据，摘要完成后创建带最终 revision 的不可变模型，并逐章释放暂存列表引用；磁盘按记录建立元数据后附最终 revision。

此修复有意改变此前默认原始字节 revision；旧保存位置/标注按既有不匹配规则恢复或标记失效，不伪装为同版，也不静默改写宿主记录。此前 raw digest 及仅 normalization v1 的说明由本修订覆盖。更新与版本保持的运行行为仍需验证。

### 初始定位拒绝与恢复结果修订

OpenBookRequest 的 initialPosition（或匹配书籍的 View 恢复位置）优先于章节/页提示。未提供位置时，initialChapterNumber 不在目录内报告 UnknownChapter；章节有效但 initialPageNumber <= 0 报告 InvalidPage，避免把页参数错误说成章节缺失。正页号超过实际分页范围仍由统一导航报告 InvalidPage。

initialFallback=BOOK_START 在目标被拒绝时导航到书首并保留原拒绝原因；FAIL 不执行该回退。无论最后导航返回 Moved 还是 Unchanged，只要执行了回退，OpenResult.Opened.restoration 都为 Fallback(reason, actual)，不因最终位置未变而声称 Exact。书籍不匹配的显式位置仍在替换旧会话前拒绝，来源/布局失败不当作无效定位静默回退。未新增公开入口或位置提交源。

### 标注与选择的字符边界一致性

AnnotationStore 验证导入书签的文本锚点、高亮的每个原文块范围时，使用与实时选择及无障碍动作相同的 TextCharacterBoundaries。文本端点必须在范围内并为当前平台 BreakIterator 字符边界；半代理对或平台认定的组合字符内部端点返回 InvalidRange。TextSelectionResolver.extract 同样校验，防止自定义呈现提交未归一范围后通过选区原文比对。

库不为导入范围自动扩大/缩小、不改写宿主原始记录或 quote；失效高亮保留可导出，但不绘制。章节尚未加载仍 PendingValidation，文本版本/原文不匹配及不支持内容继续保留原原因优先级。书签近似导航仍遵循既有恢复策略，状态校验不新增自动重定位或持久化副作用。

全部边界采用当前平台字符分割，复杂 emoji/脚本及不同 Android 版本的实际表现仍须验证；不宣称所有系统拥有相同最新 Unicode 字素规则。整个块区间的 0/length 端点走快速路径，不为每个完整中间块另建分割器。不新增公开数据字段或错误枚举。

### 滚动可见位置的无锚点片段修订

连续滚动的当前片段若无法提供 anchorForVerticalOffset，不再直接借用全章末片段的锚点。Chapter 首次遇到此情况时建立轻量回退索引：当前/后续片段的有效首端锚点优先，无后续有效首端时采用当前/此前有效末端；全部都无法提供锚点则保留 null，不发明字符偏移。正常文字仍使用实际可见行锚点，不经过回退索引。

索引对不可变连续片段作两次线性端点查询，仅保存每个 chunk 的锚点引用；之后查询 O(1)，随当前章节显示窗口淘汰，不读来源、不新增分页或位置提交源。首次索引建立的实际耗时与持有开销仍需测量，属于显示工作集，不能宣称内容/布局缓存预算包含它。自定义布局应提供稳定、纯读且轻量的锚点查询；此修复只能提供邻近定位，不承诺无锚点内容自身的精确恢复。

### 已到达结算端点的效果帧

PageTurnEffectManager 结算 from==to 时不再先后等待两个显示帧：直接构建目标 progress、SettlingCommit/SettlingReset 和最终 touchOrigin 的效果帧，调用 update，再校验取消/turn 归属，然后沿原 show/finish 或复位路径完成。该分支适用于手势已到达 1 或返回 0，不新增提交点、不跳过生命周期和异常回退。

自定义效果仍收到最终结算 update，即使不再有数值插值；finish/abort/detach、资源租约与取消规则保持原契约。端点的真实视觉、目标页面布局等待以及自定义回调重入仍需设备验证，不以减少帧等待宣称帧率或响应数字已达标。

### 样式事务归属与回滚

applyStyle 在会触发回调的导航中断、布局失效和状态发布之前捕获预期 contentGeneration，回调后复核归属及 release 状态。被新布局请求、换书或释放替代的请求返回 Superseded，不继续恢复位置，也不回滚较新的配置。仅装饰更新在通知后另检查样式仍是本次提交值，避免把重入的新样式结果归给旧调用。

布局准备失败或取消时，仅原事务仍持有归属才能恢复旧布局样式。回滚保留等待期间已接受的 highlightStyles/selectionStyle；每个可重入步骤后再次检查，失去归属立即停止。取消仍抛出原 CancellationException。实现使用 applyStyle 内部局部回滚函数与既有导航代次，不增加公共类或第二套事务管理器。

目前仅高亮/选区样式走装饰刷新。字体、背景、文字颜色、页眉页脚等其他变化保守地失效布局并按原文位置重排，索引输入同样排除装饰样式但保留其他样式字段。纯颜色/背景的重绘优化尚未实现，StyleApplication.relayout 因而可能为 true；不承诺这些变化保留分页缓存。背景与字体继续由既有绑定/解析器负责，Widget 不持有主题列表。
### 示例目录操作的可用性与会话归属

示例控制面板仅在可读、导航空闲且目录非空时启用目录入口。EPUB 原目录保留层级和无目标条目，但无 resourcePath 的行使用禁用项并显示“无正文目标”；不通过点击后报错模拟可导航项。有资源路径的目标仍需按需读取/解析，缺失片段或加载失败沿既有可取消导航提示处理，不预读全书来保证所有目标成功。

示例更新目录对话框捕获打开时的 BookDescriptor 实例，点击时检查同一实例及 Activity 有效性后才修改内存来源和重新加载，避免换书/目录更新后旧对话框写入新会话。以上属于宿主 UI 与生命周期策略，未增加 Widget 接口或库内目录/持久化管理器。
### 网络宿主的停止与连接清理

NetworkActivity.stop 在递增会话序号/保存可用快照后，把 request/source 转移到局部变量并清空字段，再取消旧请求。finally 链保证继续关闭阅读器和旧来源；创建新来源后若停止旧会话失败，关闭未安装的新来源。来源属于宿主，Widget.closeBook 不替宿主关闭 BookSource。

NetworkBookSource.close 在连接锁下标记 closed、移除元数据快照并取出/清空连接登记，锁外逐个 disconnect。普通连接清理异常记录诊断并继续其余连接；读取任务仍在自己的 finally 中清理连接。重复 close 不再重复枚举已退休连接，不引入 Widget 内部网络层。disconnect 属于尽力取消，既有连接/读取超时仍为最终阻塞边界，未验证设备的实时中断延迟。
### 网络示例章节修订 v2

目录中的章节标题会生成正文 HeadingBlock，因此章节 revision 必须包含标题。NetworkBookSource.chapterRevision 将版本域标识、长度前缀和精确 UTF-16 标题、原始正文响应字节共同摘要；正文按8 KiB分块更新并检查取消，完成摘要后再复核取消/来源关闭。固定输出规则的变化需提高版本域，来源 URL/对象实例不作为正文版本字段。

此前仅原始响应字节的章节修订被替换：相同正文不同标题得到不同版本，旧位置/标注继续按现有版本不匹配规则处理，不静默迁移。目录版本与 bookId 规则不变；实现留在网络宿主示例，不为 Widget 增加网络协议。实际标题更新、恢复、标注失效与取消时点仍待验证。
### 页面扩展切换与延后重排的归属

setPageFactory/setBlockLayoutProviders 共用内部 refreshLayoutConfiguration：更新配置后，在中断导航之前捕获预期内容代次；中断与布局失效回调后检查代次及释放状态，再安排位置恢复。被较新配置/换书/释放替代的旧调用停止，不再次清理新布局。视口变化也在布局失效回调后复核代次。

restoreVisiblePosition 使用独立递增序号合并同一内容代次下的重复 post。旧 Job 先移出字段再取消；取消回调、post 和协程进入时检查序号/代次。LAZY 创建后先登记 Job 再启动，finally 仅清理自己的字段；失败通知也检查归属，避免旧排版失败改写当前状态。离开窗口使延后请求失效并先移除旧 Job。继续复用统一导航，后台任务只负责重建呈现，不新增位置提交点或公共事务类型。

配置切换仍为同步 Unit API；失去归属时静默停止后续恢复，当前配置以最新请求为准。延后恢复继续使用普通导航的 Busy/节流规则，不强制中断较新的宿主导航。编译成功不证明全部扩展回调重入、取消和实际位置恢复行为。
### Java 监听适配器与实际消费示例

公开 ReaderListenerAdapter : ReaderListener 与 ReadViewListenerAdapter<L> : ReadViewListener<L> 是抽象空实现适配器。Java 匿名子类仅覆盖关心的回调，Kotlin 仍可直接实现原接口；适配器无状态、无导航/请求所有权，也不改变回调线程及异常策略。本项目当前编译方式下，原 Kotlin 接口的默认函数在 Java 看来仍是 abstract，适配器避免宿主手工补齐所有方法，未切换全库 JVM 默认方法 ABI。

JavaReaderActivity.java 是真实加入 APK 的 Java 宿主：直接构造 ReaderView、BookDescriptor/TocEntry/ChapterContent/MemoryBookSource，使用 callback 重载打开/恢复/导航和 ReaderRequest.cancel/isCompleted；监听仅覆盖 onStateChanged。默认参数并非全部转换为 Java 重载，模型示例明确传入完整字段；全默认配置如 NavigationOptions 可调用无参构造。

请求字段在取消前撤销、序号拒绝旧回调；请求返回时检查序号与 isCompleted，不把已经同步完成的句柄重新登记。前后台保存位置并 closeBook，前台重建内存来源恢复；有稳定 View ID，Bundle 复用宿主 SnapshotState，不保存来源/协程/工厂。回调次数展示只是示例数据，不构成恰好一次验收证据。入口在首页“打开书籍与演示”，Activity 不导出。
### 可取消的内置内容指纹准备

ChapterRepository 在来源工作线程取得 ChapterContent 后，调用内部 prepareContentFingerprint 并传入当前协程的 ensureActive。类型扫描、块/字符串边界及每4096字节 UTF-16 缓冲批次检查取消；整章复用同一缓冲，不再逐字符串分配。指纹序列仍为原块数、块ID、类型/标题级别、原文长度及 UTF-16 数据，结果不因实现调整改变。

ChapterContent 内部同步保存完成标记与可空结果：只有完整摘要且最后取消检查成功后才写入缓存。自定义块的 null 是成功的未知指纹结果；取消/异常不会被缓存为 null，也不会保存半摘要，后续读取可重新准备。已准备快照的主线程指纹访问只读已完成值，协调器/索引/无障碍身份仍使用原字段，不新增公共模型属性或后台任务层。

同一个不可变对象的并行首次准备使用既有同步语义，等待该对象计算锁本身不是挂起可取消等待；计算者会在批次检查取消，等待者进入锁后检查自己的取消。未承诺常数时间或即时抢占，源构造/自定义估重等其他同步代码不由此自动变为可取消。实际大章取消延迟、资源趋势与共享对象竞争仍待验证。
### 宿主通知异常边界

统一 submit 仍以单个协程完成钩子捕获终态并投递一次主线程回调。onComplete 普通异常被记录，不反馈为请求失败、不调用第二次回调，也不递归发布 onError；宿主负责修正回调逻辑。BaseReadView/BookReadView 的监听分发同样只隔离 Exception，Error 继续传播，代次变化后停止旧事件后续分发。异常隔离不增加结果枚举或请求管理器，恰好一次实际行为仍需覆盖各取消/替代/释放时点。
### 交付边界：内部作用域与 Release AAR

BaseReadView.readerScope 改为 internal，宿主子类不借用 Widget 内部 CoroutineScope；通用容器示例改用既有 callback navigate 初始化。子类通过导航/请求句柄和生命周期钩子扩展，后台任务生命周期仍由库拥有。此调整收回未写入公开目标 API 的 protected 实现属性；直接依赖该旧属性的开发期子类需改用 callback 或宿主自己的任务作用域。

Kotlin internal 不是字节码访问安全屏障：Release 产物仍含带模块后缀的 public 合成访问入口，Java 不能把这些入口当稳定 API。公开支持范围为声明的 API/SPI 与正常 Widget 方法；内部包和合成入口不提供兼容性契约。

本轮生成并读取 ireadview-release.aar：Manifest 仅库包及 minSdk21，未包含 Demo Activity/INTERNET 权限；classes.jar 包含 ReaderView/监听适配器，未发现 Demo 包或测试类名称。未发布 Maven 坐标或执行宿主压缩验收。consumer-rules 无全库 keep，直接引用的扩展由宿主调用，反射扩展由宿主补规则；生成 AAR 不代表压缩后的反射/XML或所有设备运行通过。
### 位置与导航通知后的派生动作

BookReadView 位置通知捕获内容代次、打开序号、书籍实例与本次目标位置；清理选区、逐个位置监听、发布状态和预载后复核。换书/新提交/释放使旧事件停止后续动作，不再以旧目标为新会话安排预载或选区工具栏。导航状态钩子捕获呈现实例、位置和状态，钩子/状态发布后复核，旧 Idle 不能继续把新 Preparing 视作可读空闲。

BaseReadView 在子类钩子之后检查当前 location/state，并在每个通用监听器之前检查同样的条件；辅助滚动事件与能力刷新也按当前匹配决定。两个私有 dispatch 增加可选内部有效性谓词，位置/导航通知使用它处理同代次内的重入提交；默认通知路径继续使用原代次规则。所有检查围绕原位置提交源，不增加公共事件或新的导航状态机。

因监听器重入使事件过时，尚未调用的监听器可能不再收到该旧事件；宿主需以最新状态为准，不将订阅顺序当历史事件队列。ReaderCallback 请求终态送达规则保持独立，不因这些事件门控取消已经排队的请求回调。其他通知类型不宣称已获完整重入验收。
### 状态快照的通知归属

publishState 使用私有递增发布序号，入口捕获打开序号/内容代次；分钟更新、状态查询、占位绑定回调后复核。嵌套 publishState 在同一内容代次内也替代旧发布，逐监听谓词阻止旧快照继续送达后续监听器。占位类型从捕获的 state.position 派生，不混用较新实时位置。该序号仅表示通知批次，不是新的会话状态、布局版本或导航提交点。

reportReaderError 捕获会话/代次并按 lastError 对象归属分发，通知中较新错误、位置提交清除错误或换书后，旧错误不继续发给后续监听器或再次发布状态。占位重试动作继续用会话/代次/来源/可读位置规则，未绑定发布序号；相同占位复用时不会因一次普通状态通知而令重试失效。

状态事件是当前观察值而非完整历史队列；监听重入使旧批次过时后可停止后续送达。ReaderCallback 请求终态仍按独立恰好一次目标处理，公共 ReaderState 和结果类型未增加字段。
### Java 输入工厂与默认来源构造

ReaderInput 的六个 companion 工厂均增加 @JvmStatic，Java 可通过接口名调用 fromFile/fromAsset/fromResource/fromUri 两个重载/fromStream。openStream 声明 throws IOException，支持 Java 直接实现会抛受检 IO 异常的 SAM；Kotlin 行为及来源关闭流的规则不变。fromStream 保留 Function0 签名，受检 IO opener 推荐直接 ReaderInput SAM 或已有文件/URI工厂，不新增重复输入抽象。

TextBookSource/EpubTextBookSource 增加 @JvmOverloads 默认 options 构造入口，完整配置构造保留。JavaReaderActivity 用静态流工厂与二参 TXT 构造实际编译，提供内存模型/TXT 切换；来源字段先撤销，阅读器关闭/release 的 finally 退休旧 TXT 来源。Bundle 保存来源选择与同源快照，切换身份不复用异书位置；没有新增宿主全文存储/书架。

源码及 javap 确认静态工厂、IOException 声明及两个来源默认构造存在，Java/Debug/Release 构建通过，未运行测试。现有 Java 代码直接调用 openStream 现在须处理其已声明的 IOException；运行关闭、恢复和受检异常路径仍待行为证据。
### 自定义布局的片段与取消边界

PaginationEngine 在 provider 匹配、measure 返回、逐单位装页、fragment 返回和章结束时检查 BlockLayoutContext.checkCancellation；padding 包装也在测量后与逐单位拷贝时检查。provider 内部耗时操作仍须主动协作，库不能强行抢占同步扩展代码。

生成片段进入页列表前验证 blockId 与原块一致、可选 offset 非负且双端存在时顺序正确、left/top 有限，以及原有正数/视口高度条件。违反契约沿现有 LayoutFailed 返回，不把错误片段放入章节布局、缓存或完整索引；取消继续抛 CancellationException。单位列表每块取一次，要求布局/单位/片段发布后不可变，库不承担宿主未通知变更的兼容。

可选 offset 不强制必须成对出现或连续，未知自定义文本范围仍保留，未新增图片/字素/自定义选区支持承诺。检查复用共享分页器，分页与滚动消费同一布局，不增加独立验证服务或公共类型。
### 内置文字逐行准备的取消

TextBlockLayoutProvider 在构造 StaticLayout 前后检查取消，并在每行生成 BlockLayoutUnit 时检查；单位列表冻结后再次检查，然后传给不可变 TextBlockLayout。原先单位在布局对象构造器内整批 map，无法观察任务取消。现在检查仍属于测量阶段，最终布局只持有冻结单位和 StaticLayout，不保留 BlockLayoutContext 或取消回调，避免缓存布局引用工作任务。

行高/原文offset/片段几何算法未改变。Android StaticLayout 的单次平台调用及字符串包装/列表拷贝本身不能被检查回调强制抢占；本修复不宣称所有长段落的取消延迟已达标，设备与大章证据仍需取得。
### 内容缓存的工作线程估重

ChapterRepository 来源工作阶段完成内容指纹与 retained-byte 估算，返回私有 PreparedContent(content, bytes)。主线程缓存接纳只读取已捕获的估重，不逐块扫描或调用宿主 estimatedRetainedBytes getter；peek/get 仍返回原 ChapterContent。未知/负估重继续不接纳缓存，超预算/饱和规则保持。

RetainedSize 新增带取消检查的内部重载，逐块及 getter 前后检查；分页器的内容估重也提供其布局取消检查。自定义 getter 应稳定且不访问 View，其同步长计算仍须由宿主控制，库只能在返回后检查。仓库保持内容+预算记账的职责，不向公共模型增加缓存状态，也不引入跨层估重服务；缓存与布局独立估算仍要求宿主估重稳定。

这消除了缓存发布阶段的整章估重扫描，不证明主线程所有路径无大工作或整进程内存达标。显示工作集、来源资源与宿主数据不等于缓存预算，设备量化验收继续待完成。
### 目录与全书页查询的内部查找表

BookDescriptor 在不可变目录构造时建立私有 chapterId→描述映射，并以映射大小检查重复ID；BookPageIndex 建立 chapterId→序号映射，重复ID仍拒绝。协调器章状态/分页状态与 currentChapter 使用目录查找，完整索引章版本核对和 pageForPosition 不重复扫描列表。查找表只保存已有元数据引用/序号，不持有正文/测量布局，未增加公开查询接口。

globalPageNumber 无完整索引时移除 chapters.take 临时列表，按序访问前章状态；每章查询不再扫描全目录，因此该回退路径由嵌套扫描变为线性。完整索引查询平均常数时间，page→章仍用原二分/Long前缀。回退路径仍需扫前章，不宣称所有状态查询常数时间；未知分页、版本/指纹不符仍输出null，不自动构建索引。

增加的元数据映射随目录/索引快照生命周期释放，其占用不包含在正文/布局缓存预算中。原位置、版本和更新失效规则保持，真实性能与大目录资源表现仍待测量。
### 默认布局导航锚点的字符边界

默认 PaginatedChapter 的 pageForAnchor 对 TextBlock/HeadingBlock 使用 TextCharacterBoundaries.isBoundary，rangesForText 对每个文本块使用 isRange。非法 UTF-16 端点返回 InvalidAnchor，不自动吸附到附近字符；分页与滚动呈现均沿该布局查询进行定位。整章末尾 offset=text.length 仍是合法端点，沿原规则定位最后匹配页；非文本块仍只接受 offset=0。

此规则复用标注/选择的当前平台 BreakIterator，没有新公共模型或验证层。标注导入仍使用自身 InvalidRange 状态，导航使用已有 InvalidAnchor，打开恢复遵循现有回退选项。外部自定义 ChapterLayout 负责自己的边界契约，库不推定其内部文本含义；跨 Android 的复杂字素差异仍需实际验证。

### 连续片段的 Android 行高约束

ContinuousFragment 构造验证正文高度为正有限值，段后间距非负有限，实际 Float 合计高度为有限值且不超过 Int.MAX_VALUE。比较时转为 Double，避免整数上限转 Float 后变成 2^31。滚动行高不能依赖 toInt 的饱和转换静默缩短布局；默认分页器构造失败沿既有 LayoutFailed 处理，自定义布局同样须提供合法行几何。构造/字段签名不变，不增加布局验证服务。

padding 包装捕获一次 provider 的 units，估重和逐单位变换共用该列表，避免逐行重新调用宿主 getter；冻结输出、取消检查和原几何算法保留。Debug/Java/Release构建通过，未新增或运行测试；极大间距/边界舍入、自定义来源错误及连续滚动实际行为仍待验证，未宣称全章累计浮点精度或性能已达标。

### 默认布局的按块页锚点索引

分页工作阶段随已验证片段登记 blockId→(页号、起止offset) 列表，完成后冻结各列表并复核取消。默认 pageForAnchor 在原内容范围/字符边界检查后只遍历目标块条目，保留片段源顺序、未知offset按0及最后匹配页回退；不假定自定义offset连续有序，因此没有使用会改变重叠片段语义的二分。索引不引用正文片段/任务回调，不新增公开对象或位置提交源。

布局估重增加每片段64字节及每索引块128字节，未知估重仍不接纳缓存。源块查找仍线性，字符边界仍沿平台规则，长块自身的页条目仍线性；这不是所有锚点查询常数时间或设备性能达标的声明。padding包装最新产物的字段检查未发现context/取消回调字段，构造参数不等于长期持有。

### 章节快照共享块 ID 查找

ChapterContent 在构造时建立私有 blockId→序号映射，沿原列表顺序验证空白/重复ID；internal blockForId/blockIndexForId 为默认布局、选择、标注和辅助来源遍历共用。未知ID分别返回null/-1，原拒绝/失效原因与跨块遍历顺序保持。索引只引用已有ID与序号，随不可变章节快照释放；不引入公共查询/模型状态或独立查找服务，自定义块仍须遵守身份不可变契约。

RetainedSize 的每块结构开销从8增加到72字节，覆盖列表引用与查找表条目/槽/整数估算；内容缓存和布局估重均复用该规则。更大章节可能更早超预算而不接纳，显示工作集仍不等于缓存预算。构造索引仍为同步线性工作，内置来源在工作线程构造；字符分割/范围遍历/长块页条目仍可能线性，不把ID查找的平均常数时间当作整体延迟通过。

Debug/Java/Release构建通过，未新增或运行测试。空白/重复/未知ID、大量块下的选择/标注/辅助定位、内容更新映射和资源趋势仍需实际证据。本项替代前轮“源块查找仍线性”的默认路径说明，其他线性工作限制保留。

### 选区文本校验避免重复摘录

TextSelectionResolver 的 extract 与 matches 共用私有 inline 范围遍历，统一书/章/修订、块顺序、支持类型和字符端点检查。extract 继续拼接真实摘录；matches 用 regionMatches 逐块对照已有选区文本，跨块分隔仍为单个换行，空摘录拒绝，完整比对必须同时耗尽来源与目标。BookReadView 接收选区、复制和建立高亮前改用 matches，不为校验额外构造完整字符串。

AnnotationStore 的导入校验仍保留可空修订及分原因失效规则，失效记录继续可导出，不把选区的严格修订匹配替代该契约。此调整没有引入任意选区长度上限、公共结果或新校验层；提取本身仍分配摘录，逐字符比对/跨块遍历和平台字符分割仍为同步工作。

Debug/Java/Release构建通过，未新增或运行测试。单块/跨空块/换行、版本/端点/不支持内容、错误选区文本以及复制/高亮的实际行为和内存趋势仍需运行证据，不以减少字符串副本认定所有选区预算或主线程响应完成。

### 标注批量路径的临时结构

AnnotationStore.replace 用一份 ID 集合逐项检查书签/高亮重复，不先创建两份ID列表及拼接列表。所有身份/重复检查仍在清空现有数据之前完成，BookMismatch继续先于DuplicateAnnotation，拒绝不改动记录/状态/修订。markPending/validate直接遍历原LinkedHashMap并条件处理，不创建筛选列表；记录顺序、状态变更计数和按章范围保持。

initialStatus复用BookDescriptor的内部章节查找，避免每条标注重扫目录。没有新增按章标注镜像、维护索引或公共层，也不改变失效记录的可导出性。全体标注遍历/文本验证仍为同步线性工作，导入去重集合仍占空间，不能据此宣称任意规模标注主线程响应或内存预算已达标。

Debug/Java/Release构建通过，未新增或运行测试。跨类型重复ID、异书和重复同时存在的拒绝顺序、拒绝前后快照、目录更新/删除与大量标注实际表现仍待验收。

### TXT 磁盘记录的版本与完整性

DiskTextSnapshot 私有文件增加 IRTX magic/formatVersion=1 的8字节头，计入 maxIndexBytes，章起始偏移在头之后。每章记录摘要覆盖域标识、ID字节长度/UTF-8字节、标题标志及正文长度/UTF-8字节；构建和读取使用同一序列。读取先验证格式头和原边界/ID/编码，再核对章SHA-256摘要，失败不发布ChapterContent。按章摘要保存在当前私有Entry元数据中，不改变正文revision v2或公共模型。

ID/正文读取和摘要按8KiB批次检查取消，异常仍沿来源失败/既有租约finally清理；单次文件读取及字节数组分配不能强制抢占。旧无头文件不作为新格式打开，也没有跨版本迁移；当前源每次构建新临时文件，不读取旧来源缓存。

这一步是持久复用的记录完整性基础。当前仍每次loadBook重读解析、关闭删除临时文件；目录/偏移/摘要尚未持久保存，没有稳定配置身份、持久条目命中或共享缓存租约，不能把校验头/摘要当作跨进程复用完成。后续须按原始输入+解析配置确认命中，持久元数据与payload原子提交、预算/并发租约及损坏回退完整实现，自定义ID策略不可仅凭对象身份跨进程命中。

### TXT 索引自描述目录（formatVersion=2）

私有索引格式升级为2，正文记录后追加书籍ID/标题/正文revision、按原顺序的章节ID/标题/起止位置/记录数/章摘要；最后44字节固定尾部为目录起始offset、目录SHA-256和尾部magic。构建按整文件maxIndexBytes预检目录/尾部开销，写完同步文件，再丢弃构建目录并从文件恢复当前快照。目录SHA先于可变长度解码验证，恢复检查总长度/版本/书籍身份、章节数量/唯一ID、连续边界/记录数和目录终点。

目录字符串按长度前缀的精确UTF-16码元保存，避免writeUTF的64KiB限制及替换宿主标题中的码元；正文/块ID仍使用原严格UTF-8记录。目录读/写/摘要循环按8KiB检查取消，长度受目录边界和整文件预算限制，结束构造后复核取消；文件同步和数组分配不保证抢占。

本项替代前轮格式1及“章摘要仅在内存”的记录：当前章摘要和目录已序列化，但文件仍由临时OwnedSourceFile管理、close删除，每次loadBook仍重读输入。没有输入/配置缓存键、持久条目发布和共享租约；跨进程索引复用继续待实现。目录校验摘要用于识别损坏，不是对不可信文件的真实性签名或缓存命中证明；后续必须验证输入与配置身份。

### TXT 可选持久索引复用

TextBookOptions新增reuseDiskIndex（默认false）与persistentIndexCacheBytes（默认256MiB，正值）；开启复用必须配置indexDirectory。默认流程不查询策略缓存键、不创建持久缓存。开启时先将一次输入流完整有界复制到原有临时租约文件并摘要，未命中从该固定输入解析，不再次打开宿主来源。缓存命中依据原字节SHA-256、bookId/书名/前言、charset名称、规范化、标题regex/flags、ID策略与格式/解析版本，以及Android构建指纹/API/runtime版本；输入超过maxInputBytes仍失败。

TextContentIdProvider.persistentIndexKey默认null；自定义策略只有显式提供非空白、覆盖全部输出语义/设置的稳定版本键才复用，命中不调用生成ID的回调。未提供键或非系统注册实现的自定义Charset沿普通重建路径。宿主必须在策略行为改变时更新键；对象身份不能证明跨进程一致。正文revision仍由原始输入与解析输出v2产生，不把缓存键替代公开内容版本。

PersistentTextIndexCache只处理来源内部的可丢弃加速数据，不进入Widget：独立iread-text-index-v2子目录、同进程登记与跨进程非阻塞独占目录锁，忙时不等待而重建。命中在短期锁内复制到来源私有OwnedSourceFile，随后验证目录及所有章的确切记录摘要；损坏尝试删除缓存并重建，来源仍按原规则关闭私有副本。缓存本身在来源关闭后保留，无活动来源借用共享payload；这替代了此前拟议的长期共享payload租约，省去新增共享读者状态机，代价是复制IO与额外临时磁盘占用。

发布先写独立.pending、同步内容，再在同目录锁内rename至最终条目；未完成文件不作为命中，下一次持锁操作尝试清理遗留pending。按访问时间淘汰，保留总最终条目字节受persistentIndexCacheBytes约束，最多128条；删除失败无法腾出空间时跳过缓存操作/发布，原加载继续。多来源共享目录时，各次操作按调用方预算尝试淘汰。扫描/复制/摘要逐批检查取消，IO/Security缓存失败仅日志并回到重建；取消和致命错误不吞掉。同步IO、目录列举/排序不保证抢占。

maxIndexBytes约束单个完整索引（包括目录/尾部），持久预算只约束保留索引条目；原始临时输入、活动私有快照、构建文件、宿主数据及OS缓存另计，不等于磁盘/内存总峰值。缓存全章摘要扫描仍为线性IO，避开解析/ID回调与逐行索引重写，不承诺每本书更快。只支持库管理的私有目录，不提供宿主编辑该格式的接口，也没有对外真实性签名。

TXT配置Demo新增索引复用选项与Bundle恢复，内存模式保留选择但不启用；内容ID样例提供明确算法版本键。界面只显示允许复用，不声称命中次数。Debug/Java/Release构建通过，未新增或运行测试；跨进程命中、输入/配置/策略更新、损坏/取消/锁竞争、预算/遗留回收及真实资源/速度仍需运行证据，不能据此认定增强项验收完成。

### 持久缓存剩余预算记账

PersistentTextIndexCache淘汰不再累计所有文件到可能饱和的Long总值再扣减。先预留本次待发布大小，从budget-incoming开始按访问时间较近且能容纳的条目逐个扣减，并限制保留数；零大小、不能容纳或超数量条目尝试删除，删除失败返回不可接纳。剩余值始终非负且不执行文件大小总和，避免饱和值丢失超限量后错误接纳。

publish即使目标已存在或本次文件不能接纳，也在取得租约时先按当前预算整理目录。已有目标可能被当前预算淘汰，允许随后请求重建；缓存是可丢弃加速数据，原输入/活动副本仍独立。条目筛选/排序键读取增加取消检查，目录列举和单次文件操作仍不保证抢占。排序相同时间没有固定先后承诺。

Debug/Java/Release构建通过，未新增或运行测试。不同宿主预算、已有目标、大条目/稀疏文件、128条限制、删除失败/取消及真实磁盘占用仍需运行证据，不把剩余预算算法证明当作跨进程或完整资源验收。

### TXT 目录与摘要的操作级缓冲

目录读/写函数显式接收本次操作的8KiB缓冲，目录摘要也复用它；缓存命中验证各章摘要在章循环之外分配同样大小缓冲。原先每个目录字符串和每章校验都创建8KiB数组，现固定到操作次数，不随章节/字符串数量重复分配。缓冲仅在同步工作阶段局部传递，不存入DiskTextSnapshot/Entry，不跨来源共享或增加全局池，序列格式/摘要/长度和取消算法保持。

Debug/Java/Release构建通过，未新增或运行测试；最新DiskTextSnapshot产物字段只有书籍/所有者/章节表及静态格式常量，没有操作缓冲字段。目录字符串CharArray/结果字符串、章摘要和来源私有文件仍需分配/持有；不宣称整体常量内存、GC趋势或大目录性能已达标，仍需设备证据。

### 正文无障碍动作的异常与切片身份

BodyAccessibilityHelper 的可选原文范围/标题、选择/清除/扩展、相邻行移动回调改为显式catch(Exception)，普通失败记录诊断并拒绝相应动作；Error继续传播，锚点比较异常也按同一普通失败规则处理。已有纯文本/整块/节点内描述粒度移动不新增API或另一提交机制。平台焦点/事件发送不是这些宿主回调边界，不宣称所有平台异常已被隔离。

Cursor切片身份由hash+length改为确切可见String与原范围，避免两个相同长度且hash碰撞的不同描述/切片复用旧游标。保留的文本只属于已查询正文节点，随原clearFocus/replace和正文绑定清理释放，不向章节模型追加副本；这仍是显示工作集，占用不等于正文缓存预算。复杂局部自定义内容离屏语义与真实辅助服务行为仍未完成验收。

Debug/Java/Release构建通过，未新增或运行测试；描述/range/heading异常、选择/移动回调普通和致命错误、hash碰撞/可见裁切、回收/焦点/服务事件仍需实际证据。

### 当前 Release JVM 清单

09新增当前AAR产物签名说明，generated/release-jvm-api.txt记录javap -protected -s的Java可见类型/成员，release-inventory.json绑定AAR/classes.jar摘要及Manifest。当前489个class条目、所选包207个Java可见声明、Demo/测试类名匹配0；数量不代表支持API总数。明确Kotlininternal/合成桥接、嵌套SavedState和Continuation不是普通宿主支持入口，不以字节码public替代源码契约。

按源码/产物修正当前包结构：效果接口/工厂在api.effect，输入在api.input，工具栏在spi.selection；没有spi.effect或顶层effects包。本轮只编辑文档/读取当前Release产物，没有修改代码或运行测试/构建，独立消费/压缩/全部签名行为与设备验收继续缺证据。

### Java TXT 身份策略适配器

TextContentIdProviderAdapter是无状态抽象类，继承策略接口并生成Kotlin默认成员的JVM桥接，Java可只覆盖需要的blockId/chapterId/persistentIndexKey。不引入策略状态、配置builder或全局JVM默认模式，默认键仍null。直接实现接口仍须满足Java视角抽象成员。

JavaReaderActivity实际构造TextBookOptions，复用默认规则/预算getter，设置私有磁盘目录、复用开关与版本化策略；匿名适配器只覆盖块ID和缓存键，章ID沿默认实现。块ID改为java-line-N，旧保存TXT修订按原规则失配/回退，不静默迁移。已有取消/来源关闭/模式来源选择规则保留，界面不宣称缓存命中。

Debug/Java/Release构建通过，当前产物清单刷新到490类条目/208个所选包Java声明，Demo/测试类名匹配0，摘要同步。Java编译支持这个具体策略/config调用；未新增或运行测试，策略默认桥接运行、复用/关闭/恢复及全部Java扩展仍待验收。

### 正文节点刷新时的焦点有效性

BodyAccessibilityHelper.refresh同时检查辅助服务与键盘虚拟焦点：节点必须仍存在、与实际可见区域相交且当前裁切能提供非空描述，否则沿原clearFocus清理两类焦点、游标/扩展和临时事件。此前只处理辅助服务焦点的离屏情况，描述失败或键盘焦点不在可见区仍可能保留。有效焦点不主动转移到其他节点，不更改导航提交或临时正文绑定策略。

该检查发生在原滚动/attach刷新周期，不覆盖任意宿主属性变更的自动通知。描述getter仍须稳定可重入且遵守SPI不可变契约，普通异常按既有失败描述处理，致命错误传播。Debug/Java/Release构建通过，未新增或运行测试；键盘/服务双焦点、裁切为空/getter失败、隐藏/恢复、滚动回收及设备焦点事件仍待验收。


### TextContentBlock：带宿主元数据的原文（2026-10-07）

新增公开interface TextContentBlock : ContentBlock，成员val text: String；TextBlock和HeadingBlock实现此接口，构造参数保持。字符串和元数据不可变，偏移仍为原文UTF-16边界。无宿主提供者时内置排版使用正文样式；HeadingBlock沿原标题样式。内置提供者version更新为2。注册的自定义提供者继续优先，需提供对应原文的命中/选区/辅助来源几何。

同章选择与文本匹配、标注校验、分页锚点/文本范围和离屏辅助来源遍历改为识别此接口，没有增加独立提交或选择管线。纯图形继续ContentBlock，无原文的描述不能被当作文本。ChapterContent指纹仍仅覆盖内置TextBlock/HeadingBlock，自定义元数据需由章节revision表达；估算大小getter仍在工作线程执行，null意味着不入缓存。Java实现该接口须实现getText/getBlockId及JVM抽象的getEstimatedRetainedBytes（未知可返回null）。

AttributedTextBlock携带sourceTag，沿默认正文排版；自定义内容Demo保留图形提供者并加入长文本，书/章版本同步更新2。Debug/Java/Release构建通过；运行选择、恢复、服务遍历及复杂自定义几何尚未验收。


### 原文布局范围校验（2026-10-07）

默认分页器在打包前逐个检查BlockLayoutUnit的已知偏移非负且双端有序；为TextContentBlock还检查偏移不超过text.length。每个生成片段沿同一检查，再进入分页、连续列表和锚点索引。null端点仍表示未提供范围，非文本块没有可推断的原文长度；失败沿既有LayoutFailed返回，不发布部分布局。

每块读取一次原文长度，单位循环检查取消，不在每行构建字符边界迭代器。此处只验证数值范围，不证明宿主几何/辅助描述与原文一致，也不替代选择/锚点入口的字符边界校验。Debug/Java/Release构建通过，未新增或运行测试。


### 辅助描述来源映射的真实性（2026-10-07）

BlockLayoutFragment.accessibilityRangeForVerticalRange返回的范围须对应TextContentBlock.text的完全相同子串，端点为平台字符边界。默认正文辅助节点在接受该范围前核对实际原文，只有长度相等不足以证明映射；无快照/非文本/失配范围不提供原文选择与Source离屏边界，描述仍可普通局部朗读。不会将宿主转换后的标签或同长度替换文本当作可选择原文。

该校验由内部当前绑定回调和TextSelectionResolver共用完成，普通异常沿原描述范围失败处理，致命错误传播。回调随正文解绑清除；不新增公开接口，不读取来源或提交选择。Debug/Java/Release构建通过，运行辅助服务与失配描述行为未验收。


### 滚动行动作的当前绑定（2026-10-07）

滚动正文的辅助范围核对/选择/清除/锚点比较与保留/邻行及离屏入口，以当前ViewHolder的bindingAdapter和bindingAdapterPosition定位行，并核对行实例身份。NO_POSITION、换适配器或新建的等值行均不能让旧绑定继续执行。边界重试动作共用此检查；未新增公开接口或提交路径。

不再通过row in rows或整列表查找原行判断绑定归属，减少大章节辅助查询的整列表扫描。代次、候选、导航busy及旧焦点有效性门禁保留；原文匹配本身仍不因busy关闭。Debug/Release构建通过（含Demo编译，Java任务已是最新），未新增或运行测试；预布局、刷新/回收和辅助服务的实际行为未验收。


### 库的本地依赖元数据交付（2026-10-07）

ireadview增加maven-publish，AGP singleVariant release附源码包。开发坐标org.peyilo.ireadview:ireadview:0.1.0-SNAPSHOT，ireadVersion属性可覆盖版本；发布到build/repository，bundleReleaseRepository生成含仓库目录/元数据的ZIP。未增加宿主运行API，没有在线仓库/权限配置或假定许可。

POM/Gradle module由真实release组件生成，记录Kotlin标准库和现有AndroidX/coroutines依赖，未手写猜测依赖清单；测试依赖不属于发布图。源码包含内部源码，不扩张支持API。Debug与本地打包构建通过，静态元数据已读取；独立消费/压缩/设备行为仍缺证据，正式版本与在线发布未宣告完成。接入步骤见08第11节。


### 独立 Maven 宿主编译（2026-10-07）

examples/maven-host为独立Gradle工程，只以Maven坐标引用库，未包含库project/includeBuild或补写运行依赖。Kotlin XML入口、Java代码创建入口、监听适配器/回调/取消/实例内快照恢复及带元数据TextContentBlock都有宿主源码。使用当前发布快照构建Debug APK成功，Kotlin/Java任务实际执行；资源编译不证明XML运行膨胀。

依赖报告确认运行图从发布模块传递解析，Core KTX最终被依赖约束提升到1.13.0，其余按实际图；POM声明版本不等于宿主最终选择版本。未新增或运行测试，未安装/启动；持久化、压缩/设备与全部API兼容仍未验收。源码范围/步骤见示例README。


### 宿主压缩配置与产物核对（2026-10-07）

独立Maven宿主release启用代码/资源压缩与Android优化默认规则，库AAR本身保持非压缩发布，未增整库keep。Release构建26秒通过，R8/资源压缩执行；AAPT生成XML ReaderView及双参构造规则，R8映射保留类名，APK压缩布局仍引用完整名称。资源文件路径可能重写，宿主按R.layout引用，不依赖内部打包路径。

当前产物为未签名APK，未配置签名凭据/上传。静态证据不证明XML运行膨胀、状态恢复、辅助服务或宿主反射扩展兼容；宿主自己的反射查找仍须自己的保留规则。未新增或运行测试。


### 独立宿主的系统状态快照（2026-10-07）

SnapshotBundles属于示例宿主而非库接口；仅保存书/章/锚点/版本、页章提示、模式及内置效果，schema与库format检查分开。块ID与offset须成对，模型构造验证数值，普通RuntimeException诊断并丢弃无效记录，致命Error传播。Kotlin/Java两页面在onSaveInstanceState导出，onCreate读取，onStart创建来源/restoreSnapshot；关闭后已有快照可兜底。

不存正文/来源/View/运行工厂；系统提供Bundle的重建与普通全新启动的磁盘历史分开。Debug/Release压缩构建通过（Release临时文件占用后重试通过），未新增或运行测试；系统恢复与格式降级仍缺运行证据。


### 长章节累计位置的内部精度（2026-10-07）

连续滚动章节累计起点、总高度/偏移及定位过程改用Double，片段内几何仍保持SPI Float像素。positionAt在扣除章节起点后才转本地Float；offsetFor共用累计表，seek只将行内差值转RecyclerView整数偏移。辅助导航、邻章加载锚定与选区边缘移动沿相同内部位置类型，不新增公开模型或第二套提交。

减少Float在约千万像素以上累计时的单像素舍入；这不证明任意规模或自定义Float片段都无精度损失。当前累计数组每项8字节，属于滚动候选工作集。Debug/Release构建通过（15秒），未新增或运行测试；长章节定位/跨章/选择与设备性能待验收。


### 滚动数值表准备的线程（2026-10-07）

默认滚动呈现仍在主线程调用ChapterLayout.continuousFragments取得不可变片段列表，随后将行高度IntArray/累计DoubleArray准备放到Default。空章显示高度在主线程捕获，工作阶段不读取View/宿主状态；每片段检查取消，完成后回主线程复核书籍对象、代次和detach，旧结果不得交给候选。数组分配和单个宿主尺寸getter不能被循环检查强制抢占。

返回片段的数值尺寸须稳定、可在工作线程读取且不依赖View；绘制/命中等局部UI职责未迁移。ChapterGeometry只保存数值数组/高度，不保留Job/取消回调；私有Chapter引用它的数组，候选释放后回收。该调整不表示所有滚动准备工作已移出主线程。Debug/Java/Release构建通过，未运行测试。


### 连续锚点的块片段索引（2026-10-07）

默认滚动章节准备同时建立私有blockId→IntArray片段序号索引，按原片段顺序记录，定位offsetFor只扫描目标块。保留首次有效匹配、边界处最后fallback、空范围/未知块行为；不假定自定义范围排序、不对任意重叠范围套二分。公开锚点与几何接口保持，复杂单块仍可能扫描其全部片段。

索引在Default工作阶段逐片段/逐冻结条目检查取消，不持有片段副本、Job或回调；候选持有ID引用、序号数组和映射，随候选释放。新增约每片段4字节序号与映射结构，构建期有临时boxed列表；不包含在缓存预算的全部显示工作集中。Debug/Release构建通过，未运行测试。


### 连续行数据的准备与复用（2026-10-07）

默认滚动章节在Default准备行对象及只读章节行表，逐行检查取消；只有章节元数据构造移入工作阶段，View创建/绑定和片段绘制/命中仍在主线程。构造结束回主线程核对来源代次后使用，取消检查参数不保存为Chapter字段，最新AAR私有字段核对支持该结论。

邻章续接时RowSegments组合已有章节行列表及边界行，保持稳定Row身份，不重新flatten创建全部行；seek按章节列表身份查起点并加chunk序号。分段计数检查总行数的Int上界，RecyclerView通知和布局工作仍由框架执行。列表/行属于当前候选工作集，冻结复制与分配不是强制抢占；不是新共享缓存、全书预创建或另一导航提交路径。Debug/Release构建通过，未运行测试。


### 书籍效果诊断的通知归属（2026-10-07）

BookReadView效果错误不再单独dispatch/publishState，改为共用私有publishReaderError：捕获内容代次/打开序号/错误对象，每个ReaderListener前复核，通知后仍有效才发布状态。监听器导航/换书/清除错误或发布另一错误时，剩余旧书籍错误通知停止。reportReaderError仍按是否已有位置设置Error/Ready，效果诊断保留原阅读阶段，避免把效果故障变成来源阶段变化。

该调整只统一书籍错误通知与状态发布；通用BaseReadView诊断仍按其自身生命周期契约处理，不能据此声明所有通用事件重入已完整验收。Debug/Java/Release构建通过，未运行测试，嵌套错误/导航/关闭及效果生命周期仍待运行证据。


### 通用效果排队通知的配置归属（2026-10-07）

BaseReadView为效果选择和PageTurnOptions设置维护私有配置序号，初始配置同样推进序号。fallback/diagnostic发出时捕获配置序号和内容代次，排队执行前复核；子类钩子返回后以及每位ReadViewListener前再次复核。后续设置即使选择同一ID，也代表新的配置请求并使旧排队事件失效。诊断日志仍在发出时记录，release后不向监听器发送。

此序号限定排队通知的归属，不改变EffectSelection结果或增加公开接口，也不代表插件create/attach/detach内部重入已全面防护。BookReadView钩子内部的ReaderListener仍遵循其书籍/错误归属检查。Debug/Java/Release构建通过，未新增或运行测试；设备通知时序和异常插件行为尚未验证。


### 效果安装的同步重入（2026-10-07）

BaseReadView设置入口在停止手势、中断导航、读取模式钩子和abort之后复核配置/内容归属。PageTurnEffectManager安装序号覆盖嵌套安装和detach，安装过程另用调用方短期谓词检查选项/书籍配置变化；该谓词只在本次调用使用，不保存在效果或任务字段。被替代安装以内部null返回，容器映射既有Superseded，不新增公开结果类型。

旧实例解绑前捕获实例及已安装ID、结束attached所有权并选择none，再执行插件detach。因此解绑/创建/绑定中的选项重入不会保留已解绑实例；创建或绑定后失效的候选经detach清理，新安装不被旧调用覆盖。安装ID捕获一次，后续读取和清理使用缓存ID。abort先清active/overlay，旧清理返回后不清新的active；finish失败及degrade继续核对效果身份/安装序号，避免淘汰较新的安装。

Debug/Java/Release构建通过（7秒），未新增或运行测试。该保护不证明所有插件回调、supports/start/update/draw、旧候选清理诊断的事件归属、子类钩子内部监听或重新挂载行为均已通过。设备重入/异常资源释放与原完整验收仍缺运行证据。


### 效果清理错误的原始归属（2026-10-07）

Manager.cleanup在执行插件前捕获安装序号及容器的内容/配置归属谓词，失败后复核；未采用候选的detach额外检查原安装请求仍有效。diagnostic携带内部是否允许通知标志，BaseReadView始终记录日志，仅有效诊断进入既有排队入口。谓词只存在于清理调用栈，不保存到效果、Turn或租借中，不新增公开错误类型。

BookReadView效果fallback/error钩子也捕获相同配置归属，在每位ReaderListener及错误后状态发布前复核。publishReaderError为效果路径叠加谓词，来源/导航错误继续沿默认书籍归属检查；已记录的lastError不因后续更换效果自动删除。较新配置使尚未送达的旧效果通知失效。

Debug/Java/Release构建通过（7秒），未新增或运行测试。源码/编译证据覆盖这些诊断边界和逐监听路径；旧插件释放的实际完整性、所有运行回调、跨窗口生命周期、辅助及完整设备验收仍未证明。


### 运行效果事务的归属检查（2026-10-07）

PageTurnEffectManager.transition在入口捕获renderer/ID、安装序号和容器配置归属。中断旧动画、阶段通知、PageSurface尺寸getter及supports之后复核协程与原归属；supports使用捕获实例，显式传播CancellationException，普通异常仍沿UnsupportedDirection降级。无动画路径也在释放手势、阶段通知与show前后复核，效果失败降级仍保持用户commit/reset决定。

启动、更新沿现有ensureTurnActive检查，另要求active仍为该Turn；showOverlay回调返回后先复核再显示遮罩。运行失败终止后、show前后和finish后复核操作归属，旧事务不能返回新配置下的旧显示结果。归属谓词只随transition调用栈存在，不保存到Turn/工厂/租借；配置捕获入口与清理诊断共用，未增加控制器或公开API。

Debug/Java/Release构建通过（4秒），未新增或运行测试。编译和源码边界不证明动画外观、取消后实际页面回滚、自定义几何/所有回调重入、租借释放和设备验收全部完成。


### 导航准备阶段的通知归属（2026-10-07）

NavigationController在取消旧pending和Idle通知前先推进本次token；通知中启动的新导航可替代本次调用，外层复核后返回Superseded，不覆盖新pending。Preparing通知后检查协程和token/内容代次，过期事务不再调用prepare。prepare返回时先登记Ready候选的清理责任，再统一检查取消/归属，Unchanged、Rejected、Failed同样不能作为旧事务结果送回。

transitionState回调后先复核再校验/发布阶段，show和提交仍沿现有候选校验。过期事务抛普通异常时归为Superseded，有效事务仍为NavigationFailed；已提交位置与提交后通知契约保持。仅调整现有导航对象，不新增公开状态或控制器。

Debug/Java/Release构建通过（3秒），未新增或运行测试。源码检查不证明通知中嵌套导航、忽略取消的适配器、实际候选清理/页面回滚或完整设备验收通过；候选dispose的异常诊断策略仍需单独核对。


### 候选dispose失败的诊断（2026-10-07）

NavigationController不再用runCatching吞掉候选dispose异常；普通Exception生成NAVIGATION/CandidateDisposeFailed，retryable=false并保留cause，导航原结果不被替换。清理与状态收尾采用嵌套finally：致命Error不吞，只有本事务token仍有效才解除pending并通知Idle，旧清理不改变新任务状态。

BaseReadView始终记录日志，诊断排队后按原token/内容代次与release状态核对，调用模块内部书籍钩子及每位通用监听器前再次复核。BookReadView复用publishReaderError并叠加同一谓词，保持阅读phase，不新增公开回调/控制器。默认呈现已隔离报告的PageCleanupFailed仍走原通道；此码覆盖逃出候选dispose边界的异常。

Debug/Java/Release构建通过（7秒），未新增或运行测试。源码和编译不证明候选资源实际释放、异常/取消后页面恢复、嵌套通知或完整设备验收通过。


### 通用适配器Host的绑定归属（2026-10-07）

BaseReadView每次安装/撤销适配器推进私有绑定序号，交给attach的ReadContentHost捕获序号和实例。requestNavigation/reportVisiblePosition在主线程检查release与绑定；旧绑定返回Superseded，release仍返回Released。reportError和能力变更上报同样检查归属，旧绑定不广播；错误派发逐通用监听复核，不新增公开接口。

能力通知另捕获内容代次、适配器实例和绑定序号，在查询后、子类钩子后、每位监听器及辅助事件前复核；同实例重绑不使旧Host恢复有效。Host只读generation/viewport/committedLocation仍表示容器当前状态，不能作为旧绑定继续上报的授权。

Debug/Java/Release构建通过（4秒），未新增或运行测试。证据限定Host入口与能力派发，create/attach/onViewportChanged重入、View绑定/解绑异常及遗留View清理仍需继续处理；完整设备/原需求验收未证明。


### 适配器解绑使用已绑定View（2026-10-07）

BaseReadView安装时保存实际加入容器的contentView。detachAdapter先捕获旧适配器/旧View，再清容器引用并推进绑定序号；插件detach在try内执行，finally使用捕获的View清理，不在插件清理后再次访问可能变化或抛错的contentView getter。仅当View仍属于该容器且未被新绑定接管时移除，避免旧清理移除重入接管的同一View。

detach异常保持传播，finally保证尝试View清理；此变更不宣称release其余步骤均具备异常隔离。Debug/Java/Release构建通过（4秒），未新增或运行测试。create/contentView getter/attach/viewport回调中的完整绑定重入、View移除异常与实际资源释放仍待完善和设备证据。


### 永久释放的普通异常收尾（2026-10-07）

BaseReadView.release先设置isReleased并清通用监听/点击/动作处理引用，然后通过内部releaseReaderResources逐项尝试失效内容、取消lifetime、onRelease、导航reset、适配器解绑、已初始化效果解绑。工具函数只执行清理列表、捕获普通Exception并保留首异常与suppressed，最后原样抛给调用方；不吞致命Error、不保存任务或所有权，不增加公开结果/控制器。

BookReadView.onRelease先推进打开序号、设置Released及清书籍监听，再用同一规则独立尝试pageClock.clear与coordinator.close。二次release仍幂等返回，不自动重试第一次失败步骤；不能把“尝试后续清理”理解为任意宿主资源都已成功释放。

Debug/Java/Release构建通过（5秒），未新增或运行测试。证据覆盖步骤顺序/异常聚合和编译；插件内部清理完整性、致命错误中断、来源/缓存真实释放及原设备矩阵仍未证明。


### 临时窗口解绑的异常收尾（2026-10-07）

BaseReadView.onDetachedFromWindow捕获旧lifetime，复用releaseReaderResources独立尝试内容失效、旧任务取消、子类解绑、适配器与已初始化效果清理；普通Exception不跳过后续步骤，聚合后保持传播。super.onDetachedFromWindow位于外层finally，库清理抛错仍执行Android收尾。临时解绑保留可恢复会话，不设置isReleased、不清宿主监听。

BookReadView.onContainerDetached先结束旧relayout字段归属并设置AwaitingViewport，再独立尝试时钟、选区、旧任务取消及协调器暂停。选区通知可能打开另一册书，暂停前检查内容代次/打开序号/release，旧回调不再暂停新会话或最后覆盖其phase。窗口再次绑定沿原恢复路径。

Debug/Java/Release构建通过（5秒），未新增或运行测试。普通异常下的步骤尝试不证明所有资源成功释放；致命Error仍中断后续库清理，完整窗口重入/create/attach/viewport、Android收尾自身异常和设备恢复仍待完善/验证。


### 窗口恢复及尺寸通知的归属（2026-10-07）

BaseReadView.onAttachedToWindow捕获恢复用lifetime和内容代次，ensureContentAdapter返回后核对适配器/绑定序号/任务活动；模式钩子和效果reattach后再次核对，有效时才调用onContainerAttached。BookReadView恢复入口捕获内容代次及打开序号，在coordinator.resumeWork后检查再恢复位置，恢复后仍有效才更新时钟，旧回调不继续操作新会话。

onSizeChanged预期仅自身内容失效增加一次代次，失效回调后复核，再捕获当前适配器/绑定；onViewportChanged后再次复核才进入书籍尺寸钩子。同实例重绑也使旧尺寸回调失效。沿既有字段与入口，无新公开状态/控制器。

Debug/Java/Release构建通过（5秒），未新增或运行测试。检查覆盖上述回调间边界，不能替代create/contentView/attach内部完整安装事务、异常回滚、实际重新进入窗口/尺寸重排、辅助或完整设备验收。


### 任意 View 装饰的组合入口


### iBook实现与Demo边界（2026-10-09）

本节记录早期圆柱迁入；当前锥面见10第3/45节。该阶段内置iBook/curl由CornerCurlEffect使用内部IBookCurlGeometry/IBookCurlMesh实现装订斜圆柱。几何采用端点包络、整条材料装订边支撑与弧长补偿；现代索引分区、旧系统规则网格复用SnapshotCurlEffect的两张预算快照。公共工厂、curl ID、CurlOptions和控制点协议保持，未增加公共控制器或第三种仿真家族。



IBookCurlGeometry的角度包络及弧长补偿分析见10第26/27节。捕获保持显示方向，RTL只转换材料纹理与绘制几何，公共PageTurnEffect/CurlOptions协议保持。




### 页面捕获职责与平移绘制策略（2026-10-09）

PageTurnSnapshots是内部事务资源组件，负责两页的显示方向捕获与租借归还；SnapshotCurlEffect负责仿真效果的平面端点及纸面/背景选择，SlidingEffect负责平移图层与阴影。预算、动画时钟和位置提交继续归PageTurnEffectManager。Google几何按材料列复用波形计算，完整网格仍保留。

滑动、覆盖、视差仅在API29以下且两张原分辨率快照均可借出时使用缓存；不足则绘制原PageSurface。API29及以上继续View绘制，因为实际对照中快照使现代系统帧时间变差。两种仿真仍按既有1/.75/.5/.25比例在预算内捕获。没有新增公共接口或第三种仿真家族。证据及限制见10第30/31节。





## 2026-10-10：卷曲外观参数的后端契约




## 2026-10-12：重新调研3D书页建模






## 2026-10-12：当前卷页版本

PageTurnEffects.pageCurl() / page_curl 已替换旧实验模式。采用当前网页的受控圆柱、装订约束和文字材料映射，Canvas窄带渲染适配API21及以上。前后翻页与松手提交/复位复用现有事务时钟、输入策略和快照预算。旧仿真研究文档、脚本与DevelopablePaper实验类已移除。完整设计及观察记录见[当前卷页文档](23_SHADER_PAGE_TURN_ROUTE.md)。




## 2026-10-13：卷页公开参数与统一阴影

新增PageCurlOptions及四个with配置函数，公开注释覆盖用途、建议范围和允许范围。默认值来自用户正在浏览的Web页面：radiusFraction=0.10、shadowStrength=0.25、lightSize=0.43、tilt=0.55；观察进度0.275不冻结Widget动画。PageTurnEffects.pageCurl(options)支持独立配置实例，自定义配置由宿主保存。Android复用Web阴影算法，GLES2小尺寸遮罩与Canvas纸面绘制配合，统一两种投影。Debug APK、Debug/Release AAR构建通过，API36已查看实际画面；本轮未运行自动测试。细节见[当前卷页文档](23_SHADER_PAGE_TURN_ROUTE.md)。


## 2026-10-13：背面文字与阴影交接修正

Android卷页已改用统一GLES2完整帧绘制：直接反求材料UV，纹理使用mipmap三线性过滤，阴影与文字共用材料坐标和接收面高度。删除160条Canvas分带绘制和独立低分辨率遮罩，公开PageCurlOptions及默认值不变。CPU快照上传后归还池，再租借输出；读回缓冲区仅缓存一个不超过8MiB的常用缓冲。Debug APK与Debug/Release AAR构建通过，已查看API36画面；未运行自动测试，未声称真机性能或全部GPU路径通过。详情见[当前卷页设计](23_SHADER_PAGE_TURN_ROUTE.md)。


## 2026-10-13：iBook阴影端部与退化状态

CornerCurlEffect改用IBookCurlShadow，锥面/圆柱统一按真实轮廓高度、投影边长计算柔化宽度和透明度；端部采用高斯软圆帽。解决旧实现固定宽度在radius=0时突然关闭、端部硬切断的问题。公共CurlOptions参数不变，注释已更新。当前算法与范围见[阴影连续性文档](25_IBOOK_SHADOW_CONTINUITY.md)。


## 段落选择与图文菜单更新（2026-10-14）

分页/滚动的长按入口改为源段落选择；原跨页手柄导航保留。默认选择工具栏改为图标+文字的分页浮动菜单，宿主可通过 setSelectionMenuItems 和 SelectionMenuItem 自定义项目、图标和回调，完整 View 工厂仍支持。宽度上限和 item 目标宽度由 SelectionMenuOptions 配置，超过容量显示右侧下一组箭头；SelectionActions.isValid 防止失效菜单动作。完整接口、兼容性及验收范围见[段落选择与图文菜单](27_PARAGRAPH_SELECTION_AND_MENU.md)。

