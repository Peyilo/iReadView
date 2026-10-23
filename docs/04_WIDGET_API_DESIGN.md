# 当前API契约

更新：2026-10-23。本文概括已实现入口与约束，不复制整份类声明。精确重载、默认值和Java签名以生产源码KDoc及按需生成的[发布清单](09_RELEASE_JVM_API_REFERENCE.md)为准。未实现能力见[状态](07_REQUIREMENT_IMPLEMENTATION_AUDIT.md)。

组件替换入口、实例所有权和失败回滚见[扩展契约](EXTENSIONS.md)；默认排版委托及可替换图片IO/解码见[内容扩展](CONTENT_EXTENSIONS.md)。`contentCapabilities`与`ReaderState.contentCapabilities`公布当前引擎能力，`getSelectionPosition()`将活动选区转换为原生定位，配合自定义点击/选区菜单支撑宿主书签和笔记；三者均主线程使用，具体语义和示例见[宿主场景API](HOST_API_SCENARIOS.md)。这些新增契约不承诺旧AAR二进制兼容，核心与扩展应一起重新编译。

## 公开面

| 领域 | 接口与入口 |
|---|---|
| 控件 | BaseReadView<L>、BookReadView、ReaderView；XML或构造函数 |
| EPUB 正文 | `EpubReaderBookSource` 交给同一个 ReaderView.openBook；`EpubReaderBodyView : ReaderBodyView` 自研 DOM 排版，documentLocator 与文本 ContentAnchor 分开，见[正文抽象](READER_BODY.md) |
| 来源 | BookSource、ReaderInput、MemoryBookSource、TextBookSource、EpubTextBookSource、EpubBookSource；可选 BookResourceSource/BookLinkSource |
| 不可变内容 | BookDescriptor/TocEntry/ChapterDescriptor、ChapterContent、ContentBlock/TextContentBlock、LinkedTextBlock/TextLink、ImageBlock/LinkTarget |
| 阅读位置 | ReadingPosition/ContentAnchor、ReaderSnapshot、显式BookPageIndex |
| 操作 | openBook/closeBook/release、reloadBook/invalidateChapter/retryChapter；页/章/全书页导航；setReadingMode |
| 样式 | ReaderStyle、ReaderStyles、字体/背景、applyStyle、输入/加载/效果选项 |
| 标注 | AnnotationSnapshot/ReaderAnnotation、书签/高亮/划线增删改、有效性状态 |
| 交互 | ReaderListener/Adapter、ReaderActionHandler、SelectionActions及工具栏SPI |
| 页面 | ReaderPageFactory/Template、DecorationFactory/Decoration、PlaceholderFactory |
| 分页缓存 | `setPageCacheMode(ReaderPageCacheMode)` / `pageCacheMode`；文本与EPUB默认三页，可切单页，见[缓存策略](PAGE_CACHE.md) |
| 默认模板 | ReaderPageChrome/Slot、setPageChrome/setPageChromeProvider/refreshPageChrome |
| 内容/呈现 | BlockLayoutProvider/Layout/Fragment、BookPresentation与受控Host |
| 可替换组件 | `setPresentationComponents`、`setBookPresentationFactory`；正文绘制、选区renderer/controller与后端工厂，见[扩展API](EXTENSIONS.md) |
| 效果 | PageTurnEffectFactory/Effect/Host、PageSurface、PageTurnEffects |

公开包是widget、api、spi、source；internal、编译器桥接和模块后缀方法不因JVM可见而成为支持接口。宿主不装配库内Coordinator、Scope或控制器。

## 线程、取消、结果

- Widget配置、导航、状态访问、View绑定及回调从主线程进入；内置来源读取用IO，布局用Default。
- 同一操作的suspend与callback重载共享实现。Java使用ReaderCallback/ReaderRequest，不操作Continuation。
- ReaderRequest.cancel跨线程、幂等；已产生终态不能被后续取消改写。isCompleted可能早于排队回调送达。
- ReaderCompletion描述本请求完成/取消；OpenResult/NavigationResult/ReaderResult区分成功、拒绝、失败或被替代。不能把取消一律报错或把失败默认为第一页。
- 监听事件与操作结果职责不同。事件发布保留归属和重入检查；宿主也应核对书籍/请求序号，防止旧回调更新新界面。
- 扩展代码不应阻塞主线程。同步读取/测量代码需要主动检查取消，单次阻塞调用不能被库强行抢占。

## 身份与源文本

- 书/章/块ID稳定且非空，目录章ID唯一、章内块ID唯一。修改原文/结构或相关自定义元数据时更新revision。
- ContentAnchor.offset与TextRange基于未被展示说明污染的原文UTF-16偏移，端点满足字符边界。
- 页码随字体、样式和视口改变，只作提示/派生导航；未知全书页数保持未知。buildBookPageIndex显式、可取消，并受版本/布局失效控制。
- TextContentBlock.text是原文；标签、朗读替代文本不能冒充同长度可选源范围。非文字图形不自动具备文字选择。
- 可见/离屏辅助遍历使用同一来源锚点；复杂局部自定义可访问语义和实际辅助服务验收仍有限制。

## 来源与更新

ReaderInput每次提供新流，取得流的一方负责关闭。来源负责权限/认证/网络协议，不访问View；章节可能并发加载。BookSource由宿主创建、共享与关闭，closeBook不替宿主close来源。

TXT支持编码、标题规则、空前言、逐行规范化和身份策略。规范化会影响输出与修订；空行及段落不能因展示方便改写原文。磁盘索引和可选持久复用遵守预算、取消、完整性及私有文件租约；自定义ID策略需稳定persistentIndexKey才能复用，不以对象身份充当持久键。

EpubTextBookSource 默认提取纯文本；EpubBookSource 保留图片和正文链接，仍不承诺完整 CSS、脚注 UI、SVG 或固定版式。setReaderLinkHandler 可拦截任意链接；未拦截的书内链接由 BookLinkSource 解析，外链由宿主处理。资源读取携带书籍 revision 和字节上限。详见[内容与书源](CONTENT_SOURCES.md)。reloadBook、invalidateChapter、retryChapter显式更新数据和派生缓存，旧任务不可覆盖新代次。

## 生命周期与持久化

- exportSnapshot只导出已提交轻量位置、模式/已知效果等；restoreSnapshot需重新提供匹配来源，并检查restoration结果。
- 正文、运行时工厂/Typeface/Drawable、认证、来源和标注不作为Widget轻量状态序列化。
- 暂时detach暂停/取消活动工作，重新attach可恢复未release控件。release永久结束控件，不可重新打开。
- 宿主负责书籍历史、标注存储和URI授权回收。停止时先解除自身旧请求归属，再取消/关会话，最后关闭不再共享的来源。

## 工厂与扩展所有权

每次create返回独立且未挂载的root/View/Drawable；候选页可同时存在，不能借同一实例跨页面/控件复用。root/bodyHost身份在模板生命周期中稳定，bodyHost须属于root。创建部分失败也要释放已取得资源。

bind可能先收到未知position/pageCount，再收到完整页信息；保持用于分页的几何稳定。解绑清除监听、任务、页面上下文及资源。刷新文字/动作不应偷偷改变正文视口；几何改变通过样式/工厂刷新路径重新测量。

页眉页脚的任意View SPI与默认三栏配置分层，详见[模板专题](PAGE_CHROME.md)。自定义模板无需实现ReaderPageChrome语义。占位重试动作要检查当时书籍/章/代次，不让旧按钮重试当前新书。

## 布局几何和预算

BlockLayoutProvider工作线程测量，不访问View；provider版本改变应使布局缓存失效。单位/片段及尺寸必须不可变、有限，已知偏移有序且不超原文。null范围可表示不支持；违约应报LayoutFailed。

正文绘制、命中、caret、selectionPath、underlineSegments、可访问源范围须对应同一原文。分页与滚动共用片段；章尾空白只从可视高度排除，尾部旧锚点映射最后页，不能删除源块来隐藏问题。

LoadingOptions的缓存/并发预算与PageTurnOptions位图预算分别管理，不是进程内存上限。来源spool、活动副本、自定义资源与当前工作集可能另占内存。

## 选择、标注和效果

选区 API 已统一到 ReaderSelection/getSelectedText/copySelectedText/highlightSelection/underlineSelection，以及统一注解分类与 goToAnnotation；普通文本与 EPUB 共用选区 Canvas 手柄绘制和分页拖动控制器。选区/工具栏动作校验当前源内容及选择代次；不要将旧SelectionActions留到换书后执行。统一标注验证revision/range/quote，失效时报告状态，不猜测搬到其他文字。第三方任意标注类型当前受sealed家族限制。细节见[选择与标注](SELECTION_AND_ANNOTATIONS.md)。

效果只绘制，不提交页码或自行驱动时钟。容器控制方向、手势/进度、提交/复位及取消，终态finish或abort恰当清理；失败按既有策略回退而不偷改用户导航决定。静止触点不需要无限重算几何。RTL、前后翻页及配置重入均受事务归属约束，见[渲染专题](RENDERING.md)。
