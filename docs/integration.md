# 宿主接入指南

区域边距、四种文字角色、固定 dp 字距及 Java/Kotlin 样式编辑方法见 [阅读面板与排版 API](typography.md)。ReaderStyles 返回新样式，使用 applyStyle 提交并在成功后保存。

本指南以当前实现为准，面向 Android View 宿主。接口约束见[API 契约](api-contracts.md)，兼容范围见[兼容边界](compatibility.md)。

## 依赖与入口

开发工程使用 `implementation(project(":app:ireadview"))`。Release 构建命令为 `gradlew.bat :app:ireadview:assembleRelease`，产物在 `app/ireadview/build/outputs/aar/ireadview-release.aar`。已有本地 Maven 打包，开发坐标为 `org.peyilo.ireadview:ireadview:0.1.0-SNAPSHOT`，发布步骤见[构建与发布](building.md)。

直接通过文件依赖 AAR 时，依赖不会嵌入 AAR。宿主需按库 build.gradle.kts/version catalogue 提供 Kotlin 标准库、AndroidX Core/CustomView/RecyclerView、coroutines Android 的运行依赖以及 PageCurl Core；推荐通过 Maven 元数据消费。库 minSdk21、compileSdk36、Java目标11；示例应用的网络权限不属于库。

```xml
<org.peyilo.ireadview.widget.ReaderView
    android:id="@+id/reader"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

代码入口为 `ReaderView(context)`。提供确定的可测量空间和稳定 View ID，避免把阅读器放入高度无限的滚动父容器。调用 Widget 的配置、状态和导航方法应在主线程；来源读取与内置排版由库调度到工作线程。

## 来源与身份

- 已有内容：`MemoryBookSource(BookDescriptor, Collection<ChapterContent>)`。
- TXT：`TextBookSource(bookId, ReaderInput, TextBookOptions)`；可配置字符集、标题规则、逐行规范化、稳定 ID 和磁盘记录目录。
- EPUB 原书显示：依赖 `:app:ireadview-epub`，用 `EpubReaderBookSource(bookId, input, cacheDirectory)` 调用同一个 `reader.openBook(source)`。内部 EpubReaderBodyView 使用系统 WebView 和自研排版；普通 Activity 即可。目录用 reader.getNavigation，跳转、样式、菜单仍由 Widget 控制，详见[接入说明与边界](epub.md)。
- EPUB 简化适配：`EpubBookSource(bookId, input, cacheDirectory)` 提供文字、独立图片块与正文链接。旧 `EpubTextBookSource` 默认继续纯文本。这两个来源不解释完整 CSS/SVG/固定版式；不要用于要求原书排版的入口。
- 网络或业务数据：宿主实现 `BookSource`，负责权限、认证、请求和重试策略。不同章可能并发读取；返回不可变模型，不在来源中访问 View。

bookId/章节ID/块ID应稳定，重复 ID 被拒绝。revision 需涵盖实际输出原文、标题和结构的变化，不能只取与输出无关的远程字段。锚点 offset 为输出文字的 UTF-16 偏移，合法字符边界由平台文本规则判定。

`ReaderInput` 每次返回新流，内置来源关闭其取得的流。URI授权由宿主取得/保留；库不请求权限，也不上传内容。TextBookSource/EPUB 来源由宿主关闭，`closeBook()` 不替宿主关闭 BookSource。不要在仍被其他阅读器共享的来源上直接 close。

```kotlin
// 以下示例是简化块模型适配；原书 EPUB 请使用上面的可选模块。
val source = EpubBookSource("stable-book-id", ReaderInput.fromUri(context, uri), File(context.cacheDir, "epub"))
reader.setReaderLinkHandler { _, _, target ->
    if (target is LinkTarget.External) {
        // 由宿主选择浏览器、应用内预览等处理方式。
        openExternalLink(target.uri)
        true
    } else false // 使用库的书内跳转
}
// 在主线程的宿主协程中调用：reader.openBook(source)
```

新增格式只需将内容解析为 ChapterContent，网络小说按章实现 BookSource；返回图片时另外实现 BookResourceSource，返回书内链接时实现 BookLinkSource。图片引用使用来源内资源 ID；读取须遵守 maxBytes、revision 和取消。自定义块继续注册 BlockLayoutProvider。设计及资源预算见[内容与书源架构](content-sources.md)。

## 打开、结果与取消

```kotlin
val request = reader.openBook(source, callback = ReaderCallback { completion ->
    when (completion) {
        is ReaderCompletion.Completed -> when (val result = completion.value) {
            is OpenResult.Opened -> { /* 读取 result.position/restoration */ }
            is OpenResult.Empty -> { /* 空目录 */ }
            is OpenResult.Failed -> { /* 显示 result.error，可由宿主重试 */ }
            OpenResult.Superseded -> { /* 已被新操作替代 */ }
        }
        ReaderCompletion.Cancelled -> { /* 本请求已取消 */ }
    }
})
// 宿主不再需要本请求时：
request.cancel()
```

代码片段使用 api.state 下的回调/结果类型。已有协程宿主也可调用 suspend 重载，但必须从主线程进入。Java 不需要操作 Continuation，实际模型构造和回调消费见 [JavaReaderActivity.java](../app/src/main/java/org/peyilo/iread/demo/ui/examples/JavaReaderActivity.java)。Java 可直接调用 ReaderInput.fromFile/fromAsset/fromResource/fromUri/fromStream 静态入口；openStream 声明 IOException，直接实现 SAM 时可自然使用抛出受检 IO 异常的打开函数。fromStream 的 Kotlin Function0 参数不声明受检异常，受检 IO opener 宜直接实现 ReaderInput SAM 或使用文件/URI工厂。TextBookSource(bookId, input) 与 EpubTextBookSource(bookId, input, cacheDirectory) 提供默认配置的 Java 构造重载。

取消句柄可跨线程且幂等。isCompleted 表示操作已结束，可能早于主线程队列中的回调送达；终态已产生后取消不改写它。回调用于本请求结果，ReaderListener 用于会话状态；宿主用自己的序号/书籍实例检查拒绝旧结果更新新界面。普通通知异常被诊断，不重送达；致命 Error 传播。

## 保存、停止与重建

停止流程的职责顺序：

1. 读取 `exportSnapshot()`，存在位置时由宿主持久化；null 表示没有可导出的已提交位置。
2. 撤销宿主请求/来源引用的归属，再取消旧请求。
3. `closeBook()` 结束书籍会话；在 finally 中关闭宿主拥有且不再共享的可关闭来源。
4. 页面永久销毁时 `release()`；release 后该 Widget 不可重新打开。

恢复前重新创建来源，并调用 `restoreSnapshot(source, snapshot, callback)`。来源身份需与快照一致；位置/版本变化可能产生明确回退或失败，检查 Opened.restoration。快照包含轻量位置/模式/内置效果，不保存正文、运行时字体、工厂、网络授权或 URI。宿主 Bundle 格式示例为 SnapshotState，最近书籍和标注存储属于示例宿主。

暂时离开窗口会取消库内工作；重新挂载仍可使用未 release 的 Widget。关闭网络来源后需要重建才能再次读取。避免把已关闭来源传回 restoreSnapshot。

## 状态、样式与资源预算

通过 ReaderListener 观察 ReaderState；Java 可继承 ReaderListenerAdapter，只覆盖需要的事件。通用容器子类使用 ReadViewListenerAdapter<L>。章节 Loaded 仅表示原文已加载，分页状态另取 getChapterPagination；全书页数未知时不要用章节页数冒充总页数。完整索引需显式 buildBookPageIndex，旧内容/版式使其失效。

样式通过 applyStyle 更新，StyleApplication 返回 layoutChanged 与位置，当前样式从 reader.style 读取。仅高亮/选区差异免重排；颜色/背景等其他变化当前保守重排。尺寸使用 dp/sp/em 的对应字段，水平 padding 为 start/end。运行时背景工厂需每次返回新且未挂载 Drawable。

LoadingOptions 控制邻章数量、并发和内容/布局缓存字节；效果位图预算属于 PageTurnOptions.bitmapBudgetBytes。预算不等于整进程内存上限，也不包含所有当前显示工作集、宿主来源或自定义资源。

## 扩展与示例位置

| 需要 | 公开入口 | 实际示例 |
|---|---|---|
| 页眉/页脚与页面组合 | ReaderPageFactory/ReaderPageTemplate | CustomPageFactory |
| 加载/错误/空状态 | ReaderPlaceholderFactory | CustomPlaceholderFactory |
| 自定义正文块 | TextContentBlock或ContentBlock/BlockLayoutProvider | AttributedTextBlock / DividerLayoutProvider |
| 自定义效果 | PageTurnEffectFactory/Effect/Host | ReaderEffects |
| 自定义选区工具栏 | SelectionToolbarFactory/SelectionActions | CustomSelectionToolbarFactory |
| 非书籍容器 | BaseReadView<L>/ReadContentAdapter<L> | CardReadView |
| 标注输入输出 | AnnotationSnapshot/replaceAnnotations/exportAnnotations | AnnotationPanel / AnnotationStorage |

提供者/工厂返回新绑定对象，遵守创建/绑定/解绑和 View 所有权；不可复用已挂载到其他页面的 View/Drawable。自定义效果仅绘制，容器驱动时间、导航和提交；自定义块测量不访问 UI、不绕开取消检查。

内部作用域、协调器和 JVM 模块后缀合成方法不属于支持接口。扩展应使用公开导航/请求和生命周期钩子。库不强制 AppCompatActivity、LifecycleOwner、数据库或 Compose。

## 当前限制

同章选择、统一标注及 EPUB 文档呈现已实现；跨章选区/笔记、完整媒体、双页和 Compose 等仍待产品确认。持久 TXT 索引复用已有可选实现，跨进程/损坏/资源行为尚未验收；复杂局部自定义无障碍语义尚未完成。兼容范围以[兼容边界](compatibility.md)为准，Release/Java 编译成功不证明视觉、性能、辅助服务或目标设备矩阵通过。
自定义 BlockLayout/单位/片段在测量后必须不可变；片段须保留原块ID、可选范围非负且已知双端有序、坐标有限。分页器拒绝违约片段并报告 LayoutFailed，库内单位处理检查取消；宿主自己的耗时同步代码仍须调用 checkCancellation。
TXT 磁盘快照的 maxIndexBytes 约束整个私有索引文件，包括格式头、正文记录、目录/摘要和尾部；超预算会使新快照构建失败并保留旧快照。来源活动快照按临时租约关闭删除；开启reuseDiskIndex时独立管理的持久缓存可保留索引供后续来源复用。该内部格式不是宿主读写接口，宿主通过BookSource获得数据并负责关闭来源。

## TXT 索引复用

在TextBookOptions中设置indexDirectory与reuseDiskIndex=true，可用persistentIndexCacheBytes限制保留缓存（默认256MiB，最多128条）。每次仍读取原输入并校验预算；命中复用已解析记录，活动来源持有私有副本，close不删除独立缓存。配置/输入变化自动换键；缓存忙/损坏/不可写可重建，不作为唯一数据源。

自定义TextContentIdProvider须覆盖persistentIndexKey，提供包含算法/设置的稳定版本标识；默认null不复用，命中时不会调用ID生成函数。非系统注册Charset实现也重建。保留预算不包含原始输入spool/活动副本/构建文件；实际性能和跨进程行为未验证。TXT配置Demo可开启复用，界面不报告未经证实的缓存命中。

Java自定义TXT身份可继承TextContentIdProviderAdapter；只覆盖blockId或chapterId时沿用其余默认值，复用另需覆盖getPersistentIndexKey提供稳定算法版本。JavaReaderActivity展示完整TextBookOptions构造，默认规则/预算可从new TextBookOptions()的getter读取。该示例已通过Java编译，运行复用/恢复未验收。

## 带元数据的自定义文本

实现TextContentBlock并提供不可变blockId/text，可附加宿主来源标签等元数据。默认排版使用正文样式，不需注册提供者；内置HeadingBlock使用标题样式。AttributedTextBlock展示持有大小估算，CustomContentActivity展示混合图形/长文本。Java直接实现时须提供getText/getBlockId/getEstimatedRetainedBytes，未知大小返回null（不进入缓存）。

原文不追加描述/标签，所有ContentAnchor、片段范围、命中/插入点、选区和辅助来源范围以同一text的UTF-16字符边界为准。宿主提供者先于默认提供者匹配；定制绘制时须提供对应几何。图形只有描述时继续ContentBlock，整块描述可声明accessibilityDescribesWholeBlock；不能仅为朗读说明而实现TextContentBlock。

内容/元数据变化须更新ChapterContent/ChapterDescriptor的revision；布局算法变化更新提供者version。内置章节指纹不覆盖自定义文本元数据。持有大小稳定且在工作线程访问，不触碰View。该接口接入已有同章选择/标注和来源遍历，复杂非文本局部语义仍有限制；兼容范围见[兼容边界](compatibility.md)。

默认分页器检查单位及片段的已知偏移非负、双端有序；TextContentBlock另须不超过text.length。null范围仍可表示不支持，违反范围约定报告LayoutFailed。数值校验不替宿主验证实际绘制/命中/辅助文本内容，Unicode边界及对应几何仍需遵守原文契约。

可见辅助范围必须对应TextContentBlock.text的实际子串，文字内容及长度相等，端点符合字符边界。组件沿当前章节校验；同长度标签/替换文字、非文本块或缺少内容快照不能提供原文选择/扩展及Source离屏定位。描述仍可局部朗读，既有无来源视口/整块描述规则保留。转换过的朗读说明宜返回null来源范围，避免暗示它可映射到原文。

## 本地 Maven 交付

库通过AGP release组件与maven-publish生成依赖元数据；只配置项目build目录中的文件仓库。默认开发坐标为org.peyilo.ireadview:ireadview:0.1.0-SNAPSHOT，可用-PireadVersion指定新版本。它不是正式稳定版本，也未上传在线仓库。采用[Android官方发布流程](https://developer.android.com/build/publish-library/upload-library)。

生成可转交的仓库ZIP：

```powershell
.\gradlew.bat :app:ireadview:bundleReleaseRepository
```

输出app/ireadview/build/distributions/ireadview-maven-0.1.0-SNAPSHOT.zip，文件仓库在app/ireadview/build/repository。ZIP顶层为ireadview-maven，包含AAR、sources.jar、POM、Gradle module metadata及Maven快照元数据/校验文件。包不会嵌入第三方依赖；iOSCurl 通过元数据传递依赖 `org.peyilo.pagecurl:pagecurl-core:0.1.0`，不再随 ZIP 打包旧 ioscurl JAR。其解析仍需google()/mavenCentral()或宿主自己的依赖镜像。多次发布或切换版本后，build仓库及ZIP可能包含此前版本。

宿主将ZIP解压到工程vendor目录，在settings.gradle.kts的dependencyResolutionManagement.repositories添加：

```kotlin
maven {
    url = uri("vendor/ireadview-maven")
    content { includeGroup("org.peyilo.ireadview") }
}
// 保留 google() / mavenCentral()，供传递依赖解析。
```

宿主模块依赖：

```kotlin
implementation("org.peyilo.ireadview:ireadview:0.1.0-SNAPSHOT")
```

相同SNAPSHOT重建后宿主可能缓存旧版本，可在宿主构建时使用--refresh-dependencies，或者以-PireadVersion生成不同开发版本并同步坐标。源码包含库内部源码，不表示internal或编译器桥接成为支持API。ZIP条目排序和时间归一不证明整个构建可重复，快照发布元数据仍有时间信息。

## 独立消费示例

[examples/maven-host](../examples/maven-host/README.md)是独立Gradle宿主：Kotlin页面通过XML创建ReaderView，Java页面代码创建，库只按Maven坐标引入，运行依赖从发布元数据传递。无需复制库源码/控制器或补充项目依赖。独立工程可用ireadRepository/ireadVersion指定解压仓库及开发版本，SDK需自行配置。

独立示例另有启用代码/资源压缩的Release配置，当前assembleRelease通过，静态APK XML、AAPT规则和R8映射保留ReaderView及XML构造入口；不需保留整个库。产物是未签名APK，尚未安装/运行；实际XML膨胀和压缩后恢复/交互仍待验收。库保持非压缩发布，由最终宿主决定优化与自己的反射保留规则，细节见示例README。

SnapshotBundles是独立示例的宿主Bundle格式：schema/format版本检查、块与偏移配对、轻量位置/模式/内置效果；无效记录沿无快照打开。Kotlin/Java页面均接入onSaveInstanceState与onCreate读取，实际恢复行为需在目标应用和设备中验证。格式不序列化正文或来源对象，也不成为库支持协议。

ChapterLayout.continuousFragments返回的列表与片段尺寸须不可变、可由布局工作线程读取，不能在尺寸getter访问View。默认滚动呈现在工作线程准备行高度/累计表并逐片段检查取消，回主线程核对绑定代次。宿主同步getter仍应短且稳定；绘制、View绑定及交互沿相应UI线程契约。

## 专题接入

- 任意页眉页脚View与默认六位置模板：[模板专题](page-chrome.md)。Demo直接使用模板，库底层兼容API仍保留。
- 段落/跨页选区、图文菜单、统一标注和划线：[选择专题](selection-annotations.md)。
- 各效果、主题背色及阴影范围：[渲染专题](rendering.md)。动画不负责提交阅读位置。
- 当前源码职责与不确定范围分别见[架构](architecture.md)、[状态](compatibility.md)。
