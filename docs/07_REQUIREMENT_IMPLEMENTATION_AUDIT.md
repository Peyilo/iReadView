# 需求实现与验收证据核对

本文件对照 01 原始需求记录代码位置、实际缺口和验收证据。它不是完成声明。构建成功只支持可编译结论；代码路径存在不能证明设备行为。需求范围以 01 第 4/5/6/8 节为准，04/05 的增强设计不自动替代或缩小该范围。

## 1. 当前核对环境

2026-10-06：设备清单包含在线 emulator-5554（sdk_gphone64_x86_64），查询系统版本为 API 36。本轮仅查询环境，未安装/启动应用或运行测试；设备在线不等于测试通过，也不覆盖 API 21/22、低端机、高刷新率机、平板/折叠屏和真实辅助服务。此前早期测试记录不能证明当前实现通过。

## 2. 阅读视图与页面结构

| 原始需求 | 当前代码入口/承担对象 | 本轮代码核对结论 | 尚缺证据 |
|---|---|---|---|
| FR-VIEW-01 正文、可选页眉页脚、默认章节名及页码/时间 | ReaderStyle；DefaultReaderPageFactory；ReaderPageClock | 发现默认页脚只含进度的缺口，已修正 DEFAULT 页脚为 PAGE_PROGRESS_AND_TIME；时间更新保留进度。正文 bodyHost 与页眉页脚为独立承载区 | 默认/隐藏/显式内容、分钟刷新、时区/12/24小时、窄窗口/大字体实际布局 |
| FR-VIEW-02 目录/章节/分页占位与可恢复错误 | BookReadView；ReaderPlaceholderController；DefaultReaderPlaceholderFactory；分页/滚动呈现 | 已定位状态及占位路径；不能仅凭其存在认定所有错误恢复场景通过 | 慢来源、目录失败、章失败/布局失败、空目录/空章、旧重试动作及取消 |
| FR-VIEW-03 页面/占位外观及绑定扩展 | ReaderPageFactory；ReaderPlaceholderFactory；ReaderPageOwnership/ReaderViewOwnership | 已定位公开工厂、bodyHost、创建/绑定/解绑及预留归属路径，Demo 有公开 SPI 接入示例 | 跨实例/跨角色复用拒绝、宿主异常、测量、按钮动作和回收实况 |
| FR-VIEW-04 旋转/窗口/正文视口变化与锚定 | BaseReadView.onSizeChanged；BookReadView；两种呈现 onViewportChanged；布局/分页键 | 已定位外尺寸通知及呈现重排路径；尚未逐种窗口变化完成行为核对 | 旋转、多窗口、页眉高度、density/fontScale、布局方向、动画/选择期间尺寸变化和位置 |

本轮 Debug 构建通过。页脚组合内容追加到枚举末尾，XML 值为 8；原枚举值保留。Demo 主页面显式选择组合项，排版面板可选择它，既有偏好/状态的显式 PAGE_PROGRESS 继续表示仅进度。

## 3. 完整目标的剩余核对范围

后续继续逐项记录，不以本轮页面结构表作为全库验收结论：

- 01 第 4.1 节：FR-BOOK-01 至 09，目录/来源/编码/EPUB/状态/预载/解析/数据 API/更新。
- 第 4.3 节：FR-NAV-01 至 07，全书页、边界、回调、打开位置和恢复。
- 第 4.3.1 节：FR-MARK-01 至 08，书签/高亮/选择、文本变更和宿主所有权。
- 第 4.4 节：FR-MODE-01 至 05，分页、连续滚动、邻章续接和模式锚定。
- 第 4.5 节：FR-FLIP-01 至 09，输入、六种效果、取消/中断、兼容和自定义效果。
- 第 4.6 节：FR-STYLE-01 至 09，背景、字体、分层间距、主题、重排和定制。
- 第 4.7 节：FR-DEMO-01 至 07，所有体验入口、控制/目录/排版/设置、退出与扩展演示。
- 第 5 节全部质量目标：主线程工作、响应/画面、资源预算、可取消布局、设备性能矩阵、稳定性、无障碍、兼容性与数据安全。
- 第 6 节九项状态/一致性规则，以及第 8 节全部二十项验收场景。
- 04/05 的公开 API/线程/生命周期契约、Java 接入、打包边界和实施交付项；可配置 TXT 身份/规范化及临时文件回收已有实现，尚需对应行为证据。

01 第 10 节明确待产品确认的双页、完整 EPUB 媒体/脚注、跨章节选择/笔记、可选存储、Compose 包装及设备性能阈值不伪装成已确认基线；保留决策状态。TXT 跨进程持久索引复用已有可选实现路径（见第33节），尚缺运行验收；复杂局部自定义无障碍语义仍未完成，不以整块语义替代。

## 4. 书籍目录与来源

| 原始需求 | 当前代码入口/承担对象 | 本轮核对结论 | 尚缺证据 |
|---|---|---|---|
| FR-BOOK-01 目录标题/顺序/卷与章节 | BookDescriptor/TocEntry | 模型复制目录，按深度优先展平，限制深度并拒绝重复章 ID | 分组/空目录/重复 ID/变更顺序的来源与导航一致性 |
| FR-BOOK-02 本地/远程按需章节 | BookSource；Memory/Text/Epub 来源；NetworkBookSource | 已定位双阶段目录/章契约、来源独立于 Widget，网络宿主逐章读取 | 慢来源、多请求/取消、并发与当前章响应；不能仅凭接口证明不卡顿 |
| FR-BOOK-03 编码/规则/前言/文件流资源 | ReaderInput；TextBookSource；TextSnapshotParser | 读取严格指定编码，共用规则保留前言/空行并产生空章节；修复版本未覆盖实际解析输出，新增 revision v2 | 边界输入、不同编码/规则/ID 与两个存储等价；旧快照/标注迁移不匹配 |
| FR-BOOK-04 轻量 EPUB 目录/正文提取 | EpubTextBookSource；EpubArchive/Package/Navigation/Xhtml/Xml | 已定位容器/目录/按需文本提取；没有把 CSS/图片/脚注当作完整阅读能力 | 真实 EPUB2/3、损坏资源、预算、取消/关闭、目录/片段导航和提取顺序 |
| FR-BOOK-05 加载/合并/失败重试 | ChapterRepository；ChapterState；BookReadView.retryChapter | 已定位状态、inFlight 请求归属及共享消费者；尚非并发行为通过证据 | 重复请求、最后消费者取消、失败后新请求、重入状态与迟到任务 |
| FR-BOOK-06 邻章预载及滚动续接 | ReaderCoordinator.preloadNear；LoadingOptions；ScrollingBookPresentation | 已定位前后窗口/任务替代，滚动边界另按需获取 | 可见优先、慢加载、失败不破坏正文、窗口变更/并发预算和边界 |
| FR-BOOK-07 标题/段落顺序与内容类型 | HeadingBlock/TextBlock；来源解析器；BlockLayoutProvider | 已定位纯文本结构与自定义块接口；图片不是默认文本来源已支持能力 | 标题与段落输出、复杂输入/自定义块与分页顺序；完整图片范围仍待决策 |
| FR-BOOK-08 双向数据 API | BookSource/模型；BookReadView/ReaderListener/ReaderState | 已定位输入模型及状态/错误/位置/交互输出；源码层不要求宿主页对象 | Java调用、线程、回调顺序/恰好一次、模型所有权及宿主扩展实际接入 |
| FR-BOOK-09 目录/章更新与迟到隔离 | MemoryBookSource.replaceBook/replaceChapter；BookReadView.reloadBook/invalidateChapter；ReaderCoordinator | 已定位显式失效与安装归属，TXT 输出版本修复防止错误复用 | 改章/删章/重排目录、更新期间回调重入、缓存/索引失效、恢复与旧任务隔离 |

本轮仅静态核对并通过 Debug 构建，未新增或运行测试。07 第3节列出的来源范围已开始核对，但上表尚缺的行为证据全部保留，不将“代码已定位”升级为完成。

## 5. 阅读导航

| 原始需求 | 当前代码入口/承担对象 | 本轮核对结论 | 尚缺证据 |
|---|---|---|---|
| FR-NAV-01 页数/当前页、指定页和首尾前后页 | BaseReadView 导航；BookReadView.goToPage/chapterFirstPage/chapterLastPage；ReaderState | 已定位标准位置/分页状态与 NavigationResult；书籍首末页和章节首末页有不同入口 | 无章/未就绪/空章、实际页数、正反向/首末及成功结果 |
| FR-NAV-02 章节信息与章导航 | currentChapter/chapterCount；goToChapter/goToChapterNumber/nextChapter/previousChapter | 已定位章身份/编号及同一 goToPosition 路径，邻章按目录顺序计算 | 更新目录/未知章/页号、邻章边界、序号和标题一致性 |
| FR-NAV-03 全书页一致映射 | BookPageIndex；BookPageIndexBuilder；goToGlobalPage | 显式逐章索引/前缀定位，未知索引明确拒绝；读取后再次检查索引实例 | Long边界/逐章映射、失效/取消/多消费者、导航中输入变化 |
| FR-NAV-04 书首尾/章间与空内容安全 | 两种呈现 prepare/方向能力；统一导航 | 已定位 AtBoundary、章节转换、位置/页范围/版本检查，不把虚拟页当作滚动真实边界 | 手势/点击/程序全部路径、空目录/章、跨章、实际书尾和取消 |
| FR-NAV-05 旧/新位置输出且不混入动画 | NavigationController.location/onCommit；PositionChangeReason；BookReadView 事件适配 | 统一 show 后检查归属再提交；动画进度与位置事件分开，重排有独立原因 | 事件恰好一次、动画取消、监听重入、纯样式变化与可见滚动事件 |
| FR-NAV-06 指定初始章/页与默认书首 | BookReadView.openBook；OpenBookRequest | 修复有效章节+非正页报错类型为 InvalidPage；显式位置优先，BOOK_START/FAIL 区分；回退 Unchanged 保留原因 | 0/负/越界/极端参数、拒绝不崩溃、回退和失败策略，状态/结果一致性 |
| FR-NAV-07 标准位置读出与恢复 | ReadingPosition/ContentAnchor/ReaderSnapshot；exportSnapshot/restoreSnapshot；呈现锚点映射 | 已定位书/章/块/offset与版本身份，快照重建来源后恢复；TXT v2 已提高版本真实性 | 同/不同版式、版本变化、已删章/块、旧快照、系统恢复与宿主设置优先级 |

本轮只完成代码核对、两个打开结果分支修复和 Debug 构建；未新增或运行测试。全部动态场景仍未据此宣告通过。第3节导航范围已开始核对，上表未获行为证据的条目继续保留。

## 6. 书签、高亮与文本选择

| 原始需求 | 当前代码入口/承担对象 | 本轮核对结论 | 尚缺证据 |
|---|---|---|---|
| FR-MARK-01 书签读入与重排锚定 | AnnotationSnapshot/Bookmark；replaceAnnotations；AnnotationStore | 输入快照复制列表，记录独立位置和修订号；未加载章待验证 | 导入/重排/模式切换/未加载章节与书籍不匹配 |
| FR-MARK-02 添加/查看/删除与变更输出 | addBookmark/removeBookmark；ReaderListener；AnnotationPanel | 已定位独立存储变更及快照事件，ID 与高亮共用冲突域 | 增删/重复/不存在、事件次数、宿主保存和回调重入 |
| FR-MARK-03 集合读取与书签访问 | annotations/goToBookmark；AnnotationStore.validate | 已定位严格导航及显式近似恢复；新增文本锚点字符边界验证 | 删除章/块、版本变化、未知ID、近似回退原因和不跳错书 |
| FR-MARK-04 高亮导入/导出/增删改 | Highlight/TextRange；add/update/removeHighlight；AnnotationSnapshot | 高亮、状态分离，导出保留原记录；修改重新进入验证 | 全量替换原子性、ID冲突、状态变化、修改/事件及持久化 |
| FR-MARK-05 长按/调整与选择数据 | TextSelectionResolver；ReaderBodyView；分页/滚动选择；SelectionToolbarController | 已定位共享几何/原文选择与工具栏；extract 新增字符端点检查 | 多语言、手柄/跨页/离屏、逆向缩小、取消、旧工具栏动作 |
| FR-MARK-06 呈现/创建/查看/删除与样式 | AnnotationStore；ReaderBodyView；highlightSelection；HighlightStyles；AnnotationPanel | 仅有效记录进入装饰，quote/版本/范围校验；修复导入非法字符端点可通过的问题 | 几何/RTL、跨页/块、改色/未知样式回退、删除和查看命中 |
| FR-MARK-07 跨段边界/文本变更与失效 | TextRange；AnnotationStore.validateHighlight；TextCharacterBoundaries | 同章跨块按原文顺序及单换行 quote 比对，拒绝不支持块/范围；导入不静默修正端点 | 空段/章界、内容变化/缺失/歧义、字符边界差异及失效不绘制 |
| FR-MARK-08 所有权与明确输出 | 不可变模型；AnnotationStore.snapshot/statusSnapshot；Widget 变更事件 | 宿主列表被复制，状态不改写原记录；本轮失效范围继续可导出 | 外部集合修改、监听重入、多实例、不上传正文/记录及线程行为 |

本轮 Debug 构建通过，未新增或运行测试。字符规则采用平台 BreakIterator，不能以此证明所有 emoji/Unicode 版本具有一致字素行为。上述条目已定位/静态核对，全部设备与变更事件行为仍需证据。

## 7. 分页与连续滚动

| 原始需求 | 当前代码入口/承担对象 | 本轮核对结论 | 尚缺证据 |
|---|---|---|---|
| FR-MODE-01 行/段/章标题分页与稳定顺序 | TextBlockLayoutProvider；PaginationEngine；PagedBookPresentation | 已定位 StaticLayout/块单位及片段装页，锚点与绘制共用测量 | 行/段跨页、长段/空章/过高自定义、缩进/RTL、页界不丢字 |
| FR-MODE-02 连续内容/跨章与可见进度 | ScrollingBookPresentation.Chapter/visible/positionAt/reportAndLoad | 实际可见偏移映射原文和虚拟页；修复当前片段无锚点误报全章末尾，改邻近端点索引 | 自定义无锚点/间距、惯性滚动、事件去重、实际章/页一致性 |
| FR-MODE-03 邻章加载/失败重试/书界 | loadBoundary；RowsAdapter 边界占位；ChapterRepository | 邻章按目录范围加载，归属/代次复核，最多三章，捕获可见偏移后重建/seek | 追加/前插不跳动、双向同时加载、失败重试、当前章保护、章首/末 |
| FR-MODE-04 双向切换锚定 | BookReadView.setReadingMode/候选包装；两种呈现 prepare | 共用已提交位置，新模式候选准备/显示后接管，失败路径保留原模式 | 跨两章滚动后切回、位置版本/重排、忙/取消/失败与监听重入 |
| FR-MODE-05 内容流特例与共同数据 | 连续片段/RecyclerView 复用；reportVisiblePosition；虚拟分页查询 | 滚动按内容流管理，无独立翻页动画 Page；统一位置与能力入口 | 程序视口移动/实际书尾、虚拟页与可见行映射、效果兼容和循环切换 |

本轮 Debug 构建通过，未新增或运行测试。回退索引首次构建为 O(chunk 数) 的几何查询，仅用于无锚点路径；之后 O(1)，实际成本未测量，不能据此证明流畅/资源目标。模式、连续滚动与全部场景尚未获当前设备行为证据。

## 8. 翻页输入与效果

| 原始需求 | 当前代码入口/承担对象 | 本轮核对结论 | 尚缺证据 |
|---|---|---|---|
| FR-FLIP-01 水平/垂直手势与配置 | BaseReadView.onTouchEvent/startPageGesture；ReaderInputOptions；PageTurnOptions | 已定位方向/系统slop/输入开关、阈值/时长；垂直按无动画路径 | 拖动/点击区别、嵌套父拦截、多指、纵向松手与慢加载 |
| FR-FLIP-02 跟随、松手提交与反向复位 | PageTurnGesture；BaseReadView.releasePageGesture；EffectManager | 归一进度与有符号速度决定，准备未开始的复位取消；本轮删除已到端点多余双帧等待 | 距离/速度/反向、0/1端点、None/减少动画/效果失败仍遵守决定 |
| FR-FLIP-03 六种预设与安全回退 | PageTurnEffects；SlidingEffect；CornerCurlEffect/EdgeCurlEffect | 六种稳定ID已对应工厂，卷页共用快照/生命周期，借图预算不足可回退 | 每种视觉/前后/RTL/尺寸/端点、纸面接缝及设备限制 |
| FR-FLIP-04 点击分区与消费 | BaseReadView.dispatchContentTap；宿主tap/click处理 | 已定位默认边缘方向及宿主优先消费、内容选择触摸优先 | 归一坐标/RTL/padding、子控件、双回调和辅助操作 |
| FR-FLIP-05 程序导航/间隔/force | BaseReadView.navigateInternal；NavigationOptions；lastTurnUptime | 普通导航检查Busy与Throttled，force替代请求仍走呈现边界 | 间隔时点、取消不节流、force中断、边界/没有位置与滚动 |
| FR-FLIP-06 重入/中断与最终位置 | NavigationController；EffectManager；View生命周期 | show后归属校验并单次提交，终止效果与租约清理独立 | 连续触摸/点击、切效果/样式/换书/尺寸、回调重入、无残影/半翻 |
| FR-FLIP-07 效果与呈现兼容 | BookReadView模式；isPageTurnEffectActive；EffectSelection | 滚动保存分页效果选择，回分页启用，效果不改变ReadingMode | 模式反复切换/失败、DeferredUntilPaged、资源与恢复 |
| FR-FLIP-08 自定义效果输入/生命周期 | PageTurnEffect/Host/Context/Frame/Surface；ReaderEffects | 容器驱动进度/时钟/提交，扩展仅绘制，普通异常回退及独立清理诊断；最终端点仍发update | 所有钩子异常/重入/取消、Surface不支持、绘制异常与lease释放 |
| FR-FLIP-09 稳定ID与选项 | PageTurnEffects.fromId；配置XML/SavedState；ReaderSnapshot | 内置工厂标识与自定义宿主工厂分开，参数校验及轻量恢复已定位 | 未知ID/旧状态/显式覆盖、多实例、配置变更/减少动画与外观 |

本轮通过 Debug 构建，未新增或运行测试。静态检查与端点路径优化不证明完整交互、生命周期、画面或性能验收；本表尚缺证据必须继续取得，不能用预设工厂数量或可编译代替视觉要求。

## 9. 样式、排版与页面定制

| 原始需求 | 当前代码入口/承担对象 | 本轮核对结论 | 尚缺证据 |
|---|---|---|---|
| FR-STYLE-01 背景 | ReaderBackground；ReaderBackgroundBinding；PageStyle | 纯色/资源/工厂，透明资源下铺背景色；每页独立绑定 | 图片/透明层、异常工厂、换页/回收、多实例与资源释放 |
| FR-STYLE-02 字体 | BodyStyle/TitleStyle/ChromeStyle；ReaderFontResolver | 独立字号/色/字体，资源/本地/运行时/系统字体；非装饰变化重排 | 字体缺失/损坏、字体优先级、取消、异步解析与设备兼容 |
| FR-STYLE-03 间距 | EdgeInsets；ReaderBodyInsets；文本布局和页面 factory | 三层正文视口边距合并一次，页/标题/段落/页眉页脚各层有配置 | 各层叠加、缩进/字符/行/段间距、极端范围与方向 |
| FR-STYLE-04 标题对齐 | TitleAlignment；TextBlockLayoutProvider | START/CENTER/END 方向语义入口已定位 | LTR/RTL、跨页标题和不同字体实况 |
| FR-STYLE-05 示例主题 | ReaderThemes；MainActivity 样式面板 | 示例通过公开样式组合主题，库无固定主题清单 | 明暗/纹理背景、各区文字对比和选择/高亮可见性 |
| FR-STYLE-06 重排位置与页码 | BookReadView.applyStyle；统一位置导航；ReaderCoordinator | 修复事务代次在回调前捕获及回滚保留装饰，原文位置强制导航 | 成功/失败/取消、监听重入、最新请求优先、页码/索引及位置一致 |
| FR-STYLE-07 页眉页脚定制 | ChromeStyle；DefaultReaderPageFactory；ReaderPageFactory | 可见/内容/尺寸/对齐/字体/色/背景/padding，默认进度与时间 | 自定义测量、极端高度、隐藏/恢复、时钟和正文视口 |
| FR-STYLE-08 页面组合 | ReaderPageTemplate；ReaderPageFactory；BlockLayoutProvider | 页面模板/正文容器与自定义块提供组合入口及位置上下文 | 宿主实际组合、所有权、异常/解绑、图片自定义实现 |
| FR-STYLE-09 完整间距/单位 | EdgeInsets(dp)；字号(sp)/字符间距(em)；块/标题 padding；布局 SPI | 内置文字和页面层具有配置；自定义图片由提供者测量，不宣称默认图片排版 | 单位/方向、多密度/字体缩放、分片不重复 padding、自定义图片约定 |

仅高亮/选区差异走装饰刷新，其他变化目前保守重排；未实现纯背景/文字颜色的布局缓存优化。本轮静态核对及 Debug 构建不构成行为验收，未新增或运行测试。字体、背景、定制页面、监听重入和失败恢复仍缺当前设备证据。
## 10. 示例 App 与宿主接入

| 原始需求 | 当前代码入口/承担对象 | 本轮核对结论 | 尚缺证据 |
|---|---|---|---|
| FR-DEMO-01 体验入口 | MainActivity；NetworkActivity/ContainerActivity/CustomContentActivity/TextSourceActivity；Manifest | 已定位示例书/文件选择/网络/独立容器和文本配置入口 | 各入口实际打开、取消选择、来源失败与后台返回 |
| FR-DEMO-02 控制面板 | ReaderMenuController；MainActivity 中央点击与公开动作 | 不改变阅读器视口的覆盖面板；目录/排版/设置/邻章/页号/进度入口已定位 | 中央显隐、辅助输入、拖动时改布局/换书、忙/失败及生命周期 |
| FR-DEMO-03 目录可用性 | showEpubContents/showSpineContents；控制面板 refresh | 修复空目标 EPUB 行伪可用，文字说明并禁用；控制面板就绪/空闲/非空才启用目录；更新对话框复核书籍实例 | 层级目录、禁用行辅助服务、有效目标缺失/读取失败、目录替换与旧对话框 |
| FR-DEMO-04 排版编辑 | TypographySheet；applyDemoStyle | 最新不可变样式复制+公开事务；分层边距、字号、字符/行/段间距与标题对齐已定位 | 各参数独立可用、非法输入、失败回滚、实时恢复及持久配置 |
| FR-DEMO-05 效果与主题 | ReaderEffects/ReaderThemes；MainActivity 设置 | 内置/自定义效果工厂、宿主主题组合及减少动画配置入口 | 全部效果/主题、滚动延后生效、恢复/配置变更与可读性 |
| FR-DEMO-06 退出与进度 | ReaderActionHandler；ReadingStorage/SnapshotState；Activity 生命周期 | 退出由宿主接管，进度/来源元信息存宿主；网络/TXT 独立页停止关闭来源 | 页眉退出、系统返回、前后台/进程恢复、来源/任务/授权最终释放 |
| FR-DEMO-07 API 扩展示例 | NetworkBookSource；AnnotationPanel/Storage；CustomPageFactory/Effects；CardReadView/Divider | 已定位来源、恢复、标注导入导出、页面和效果/容器扩展；不代表第三方实际集成通过 | 全流程行为、所有权/错误策略、持久记录兼容、Java 与真实宿主接入 |

本轮静态核对并通过 Debug 构建，未新增或运行测试。来源/页面/标注/效果示例代码只是接入证据，生命周期、交互与设备行为仍未得到验证；质量属性、一致性规则和原始20个验收场景继续保留。
## 11. 质量属性逐条证据映射

以下编号 Q-5.x-n 对应 01 第5.x节第n个条目；全部尚未完成行为/质量验收，不以实现机制作为性能或稳定性结论。

| 原条目 | 实现位置/本轮静态证据 | 要求的进一步证据 |
|---|---|---|
| Q-5.1-1 主交互路径不整章远程读取/整书解析/大规模分页 | 来源 IO；协调器异步布局；统一导航准备 | 主线程调用栈/跟踪，慢网络、大书、大章实际输入响应 |
| Q-5.1-2 当前章优先/邻章不冻结 | ChapterRepository 请求共享；ReaderCoordinator 预载替代/窗口 | 当前/预载竞争、慢/失败/取消、触摸响应与首次可读时间 |
| Q-5.1-3 画面连续且最终位置一致 | 效果管理/快照租约；两种呈现；show 后提交 | 所有效果录屏/帧时间、接缝/闪白/空洞、实际画面与位置 |
| Q-5.1-4 缓存/预载随邻近范围受控 | 有界内容/布局缓存；滚动最多三章；效果预算 | 长时间阅读堆/资源趋势、工作集与缓存分开、超大章/显示峰值 |
| Q-5.1-5 过时分页可取消且不覆盖 | 布局任务归属/代次；样式事务回调检查 | 大章连续改尺寸/样式/换书、取消时点、旧结果不提交 |
| Q-5.1-6 目标设备性能矩阵 | 当前只读识别 API36 模拟器，不是性能结果 | 低端/中端/高刷新设备及量化阈值确认；帧/首次显示/响应/峰值/长读趋势 |
| Q-5.2-1 旋转/尺寸/前后台/效果切换 | View 保存轻量状态/重新挂载；宿主重建来源 | 各组合场景、不崩溃、不重复位置、不显示错章的事件与画面 |
| Q-5.2-2 取消/更新/销毁释放且位置一致 | 效果清理/所有权；协调器关闭；本轮网络宿主先撤销引用+finally 来源关闭 | 各阶段销毁/重入/异常、最终连接/页面/租约释放和位置事件 |
| Q-5.2-3 来源异常可恢复 | 来源严格解析/预算；占位重试；Demo 重新打开 | 空/无章/编码/部分章错误、用户可执行恢复与旧会话保留规则 |
| Q-5.3-1 辅助识别及导航/目录/设置替代操作 | 正文虚拟节点；公开 ReaderAction；按钮/键盘/控件 | TalkBack/SwitchAccess 真实遍历/选择/跨页与全部操作，复杂局部自定义语义仍未完成 |
| Q-5.3-2 状态不只靠颜色 | 占位/错误文字；本轮 EPUB 无目标禁用并文字说明 | 加载/错误/选择全部 UI、辅助播报；无颜色时仍可理解 |
| Q-5.3-3 字体缩放/深色/服务/触控尺寸 | sp 配置；宿主明暗主题；选区/控件几何 | 系统 fontScale/深色场景、正文/页眉截断、最小触控尺寸与服务实况 |
| Q-5.3-4 减少动态效果保留导航 | PageTurnOptions.reduceMotion；效果无动画路径；Demo 设置 | 系统偏好/宿主设置、所有输入/效果、完成/取消/边界 |
| Q-5.4-1 URI 授权及取消选择 | ReaderInput.fromUri；MainActivity picker；UriGrantStore 宿主授权账本 | 授权拒绝/撤销/临时/持久、选择取消、进程恢复与账本释放 |
| Q-5.4-2 不擅自上传正文/位置/配置 | 来源、持久化留在宿主；库无网络传输实现 | 发布依赖/Manifest 边界核对及网络行为证据；不能仅凭文本搜索通过 |
| Q-5.4-3 宿主管理权限/认证/重试 | NetworkBookSource 有限 HTTPS 示例；Widget 仅 BookSource | 真实宿主接入、权限/认证/重试边界、示例约束文档；不承诺生产服务可靠性 |

本轮网络 close 先锁内退休登记，锁外独立断开；stop 的 finally 确保旧来源释放。Debug 构建通过，未新增或运行测试，不能证明上述条目中的取消延迟、异常覆盖或资源趋势。第6节九项一致性规则及第8节二十个验收场景仍需单独逐项证据，不能由此表推定通过。
## 12. 九项状态与一致性规则

编号 I-n 对应 01 第6节第n项；以下只记录代码承担对象及证据缺口，不宣告任何规则已经验收。

| 原规则 | 当前承担对象/静态证据 | 必须取得的行为证据 |
|---|---|---|
| I-1 可观察会话阶段 | BookReadView.phase/publishState；ReaderState/ReaderListener；占位/错误恢复 | 正常/空/失败/取消/重试/关闭的阶段、线程与事件顺序；重入不混会话 |
| I-2 每次手势仅提交前/后或复位 | BaseReadView 手势；EffectManager；NavigationController | 距离/速度/反向/多指/取消/慢准备，最终结果恰好一个 |
| I-3 内容位置独立于动画且一次提交 | ReadingPosition；show 后 onCommit；PositionChangeReason | 动画中无提前位置事件，成功只一次，取消/重入/滚动视口一致 |
| I-4 新排版隔离旧任务 | ReaderCoordinator 任务/布局输入归属；applyStyle；分页失效 | 连续改样式/大小、大章取消、旧回调和结果不可污染新布局 |
| I-5 页/章/全书/滚动转换与模式锚定 | ChapterPaginationState；BookPageIndex；两种呈现；setReadingMode | 原文对应几何、真实边界、跨章双向切换，未知索引不伪造页数 |
| I-6 重试隔离迟到章节 | ChapterRepository inFlight/消费者与章节代次；retryChapter | 失败/最后消费者取消后重试、旧任务迟到、结果/状态归属 |
| I-7 换书隔离内容/缓存/回调/动画 | openSequence/contentGeneration；协调器/repository 关闭；效果/页面所有权；宿主序号 | 相同章ID换书/重入、各阶段销毁与迟到任务、最终资源和画面 |
| I-8 自定义遵守所有权及规则 | BookPresentationHost；ReaderPage/ViewOwnership；BlockLayoutProvider；公开导航协议 | 实际自定义接入、View复用、钩子异常/重入、锚点/状态/边界与清理 |
| I-9 宿主掌控标注存储 | AnnotationSnapshot/Store；公开输入/输出；AnnotationStorage | 集合所有权/导入导出/变更事件、宿主持久恢复及不强制后端 |

本轮修复网络来源版本遗漏合成标题：HeadingBlock 的目录标题参与章节摘要 v2，旧修订失配按现有恢复/标注规则处理。TXT revision v2 与网络示例分别保持来源自己的版本语义，Widget 不规定网络或存储格式。构建成功仅证明可编译，标题变化缓存/索引/标注失效与全部九项规则尚缺当前运行证据；第8节二十个验收场景仍继续待逐项核对。
## 13. 原始二十项验收场景

编号 A-n 严格对应 01 第8节第n项。当前各项均为“未完成验收”：已有实现/早期记录/当前构建不证明最新代码经过这些场景。下表规定应取得的证据，保留原场景完整范围。

| 场景 | 对应实现/本表关联范围 | 完成验收应取得的证据 |
|---|---|---|
| A-1 无标题文本保留开头且可导航 | TextSnapshotParser；FR-BOOK-02/07 | 实际无标题输入、输出章/块与首屏正文及导航 |
| A-2 自定义标题规则保持目录顺序 | TextBookOptions；FR-BOOK-02 | 自定义规则输入/预期章节与实际目录/正文顺序 |
| A-3 指定初始章页及无效回退 | openBook；FR-NAV-06 | 正常/0/负/越界参数、FAIL/BOOK_START 的结果/状态/画面 |
| A-4 连续前后翻页及取消 | 统一导航/手势；FR-FLIP-01/02 | 中间页内容与事件记录，提交/反向/复位最终位置 |
| A-5 首尾/跨章全部输入 | 呈现方向能力；FR-NAV-04 | 手势/点击/程序在书与章首尾的实际边界和跨章结果 |
| A-6 动画中反向/连续点击/效果/尺寸 | 效果管理；FR-FLIP-06；FR-VIEW-04 | 组合中断录屏及位置事件，无卡页/残影/重复提交 |
| A-7 全部六种效果 | PageTurnEffects；FR-FLIP-03/08 | 每个预设的拖动/程序/边界/中断/切换、卷页多尺寸视觉 |
| A-8 慢在线来源预载/失败重试 | NetworkBookSource；repository；FR-BOOK-05/06 | 受控慢/失败网络下触摸响应、续接、重试且原位置保持 |
| A-9 改字号/字体/边距/背景锚定 | applyStyle；FR-STYLE-01/02/03/06 | 各参数和组合的正文、页数/页码、原文锚点及失败恢复 |
| A-10 双模式跨两章位置转换 | setReadingMode；FR-MODE-02/04 | 滚动跨两章后分页与反向切换的可见原文对应 |
| A-11 旋转/窗口与加载动画 | View生命周期；FR-VIEW-04；Q-5.2-1 | 加载/动画各阶段旋转与窗口记录，位置和事件无错乱 |
| A-12 换书/关闭隔离及渲染释放 | 会话代次/资源所有权；I-7 | 旧任务迟到、错章隔离，效果租约/页面及临时资源最终释放 |
| A-13 示例完整读者操作 | MainActivity/各面板；FR-DEMO-01～06 | 本地选择/取消、目录、主题、排版、模式和退出完整实际流程 |
| A-14 空/解析/目录/单章失败恢复 | 来源/占位/重试；FR-VIEW-02 | 每种输入失败可理解提示和真正可执行恢复结果 |
| A-15 XML/代码/双实例宿主 | ReaderView/BaseReadView；FR-VIEW-03 | XML与代码测量显示、同屏实例来源/位置/效果独立 |
| A-16 标注输入/变更/恢复/失效 | AnnotationStore/选择；FR-MARK-01～08 | 导入/交互输出、重排/模式锚定、失效不画错文字的记录与画面 |
| A-17 公开定制无内部依赖 | 页面/呈现/效果 SPI；I-8 | 实际扩展流程、Demo/消费方依赖边界与各钩子所有权 |
| A-18 全书未知/显式索引/取消失效 | BookPageIndexBuilder；FR-NAV-03 | 未知 UI、完整逐页映射、取消与内容/布局更新隔离 |
| A-19 快换书/更新/离窗与恢复 | openSequence/代次/重挂/快照；I-6/7 | 各竞争组合、旧任务不提交，重挂与宿主重建实际原文恢复 |
| A-20 Java/非协程取消恰好一次 | ReaderRequest/Callback/Completion；submit | 真实 Java 消费代码、每种取消时点回调计数、加载/边界/不支持结果 |

本轮页面扩展切换和延后重排的归属修复属于 A-9/11/17/19 的代码进展：同代次 post 按序号合并，旧失败不覆盖新状态，配置切换回调后检查代次。Debug 构建通过，未新增或运行测试，以上二十项仍全部缺少当前完整验收证据；性能/辅助服务/设备矩阵也不能由场景表替代。
## 14. Java 接入编译证据

2026-10-06：检查当前编译产物，ReaderListener 的15个回调均呈现为 Java abstract 方法。新增 ReaderListenerAdapter 与泛型 ReadViewListenerAdapter 空实现抽象类，保留原接口，不改全库默认方法编译配置。

JavaReaderActivity.java 实际纳入应用，Debug 构建执行 compileDebugJavaWithJavac 并通过，证明：Java 能构造这些公开内容/来源/ReaderView，使用打开/恢复/前后导航的 ReaderCallback 重载、取得/取消 ReaderRequest、读取 isCompleted、导出 ReaderSnapshot，并仅覆盖适配器 onStateChanged。入口与非导出 Activity/固定 ID 已登记，Bundle 编解码属于宿主。

此证据补充 FR-BOOK-08、FR-DEMO-07 和 A-20，但不宣告这些需求通过：示例不覆盖全部 API 的 Java 调用、真正 Java 自定义 BookSource 的异步实现、对全部请求分支计数、旋转/后台恢复和发布 AAR 独立消费。界面的回调次数是演示而非测试断言，当前未新增或运行测试；取消恰好一次及边界/不支持结果仍需行为证据。
## 15. 大章节内容指纹准备

Q-5.1-5/I-4/A-19 的静态核对发现：来源返回后 fingerprint 在工作线程计算，但旧 lazy 的字符串扫描没有取消检查。当前改为 repository 传入协程检查，按块和4KiB批次检查，整章单缓冲，内部锁下只保存成功完成值；取消后的同一对象可重算，不伪装未知指纹。SHA-256 输入序列和公开模型不变。

Debug 构建含 Java 编译通过，未新增或运行测试。此证据不证明大章取消响应、同步锁等待、共享对象重试或资源量化目标；还需大段/多段、中途取消后重新准备、两个宿主共享对象与长读趋势的当前运行证据。自定义块继续无通用结构指纹，宿主可靠 revision/保守失效契约保留。
## 16. 请求终态与宿主通知

A-20/I-1/I-3 静态检查：submit 每个 Job 注册一个完成钩子，捕获 Completed 或 Cancelled，再投递主线程回调；终态取消后不重写，isCompleted 可先于送达。本轮增加普通回调 Exception 诊断隔离，不重送达；两类监听改为仅捕获 Exception，使 Error 传播。没有新增请求管理器或完成枚举。

Debug/Java 编译通过，未新增或运行测试。当前结构不等于证明各时点恰好一次：启动前/工作中/结果生成后/已排队/回调中取消，关闭/释放/替代及宿主异常仍需计数和状态证据。公开文档完成语义与 StyleApplication 字段/保守重排范围已同步，不能把静态异常边界认定为稳定性或崩溃率验收。
## 17. Release AAR 交付边界

2026-10-06 当前 Debug/Java 编译与 :app:ireadview:assembleRelease 成功。直接读取 app/ireadview/build/outputs/aar/ireadview-release.aar：Manifest 为 org.peyilo.ireadview/minSdk21，无权限/Activity；classes.jar 518条目，ReaderView/ReaderListenerAdapter存在，Demo包及测试类名称匹配数为0。这是当前产物内容证据，不是设备或独立消费验证。

protected readerScope 原签名暴露 CoroutineScope，已收为 Kotlin internal；CardReadView 改公开 callback 导航。javap 显示模块后缀的 public 合成 getter 仍存在，内部/合成入口不能作为支持契约；不以源码 internal 宣称 Java 强隔离。consumer-rules 未增加 blanket keep。

FR-BOOK-08/A-15/A-17/A-20/Q-5.4-2 的交付范围仍缺：外部消费 AAR及依赖配置、实际压缩后的 XML/扩展资源、全部公开签名、依赖/Manifest安全边界与设备行为。当前未配置/发布 Maven 坐标，未新增或运行测试，也不把 AAR 生成成功认定为发布就绪。
## 18. 位置与导航事件重入

I-1/I-3/I-7/A-6/A-19 静态核对发现位置/状态钩子之后的派生动作缺少一致门控。当前 BookReadView 检查打开序号、内容代次、书籍/呈现身份、位置及状态；逐监听谓词处理同代次内的重入导航，过时事件不继续旧目标预载/工具栏。Base通用钩子后通知/辅助滚动/能力刷新复核当前值。请求回调终态投递与位置事件门控仍分开。

Debug/Java/Release构建通过，未新增或运行测试。必须取得监听内换书/强制导航/模式切换/释放、多个监听器重入顺序与旧Idle不影响新Preparing的实际证据。过时旧事件可停止尚未调用的监听器，不能假定订阅即完整历史队列；不宣告所有其他通知重入路径已覆盖。
## 19. 状态快照与错误通知

I-1/I-6/I-7/FR-VIEW-02 的静态检查发现仅内容代次不足以覆盖同代次嵌套状态发布。publishState 已加私有发布序号，clock/读取/占位绑定及逐监听检查，占位类型使用捕获快照。reportReaderError 对错误对象与会话/代次检查，过时错误不继续发送。占位重试不绑通知批次，继续复用原来源/会话/代次规则。

Debug/Java/Release 构建通过，未新增或运行测试。需要clock和占位绑定内重入、同代次多个监听/新错误、位置提交清错、相同占位复用重试的运行证据。通知批次不是完整历史队列，公共状态/请求结果未增加版本字段，不能据此宣告全部状态顺序或重试通过。
## 20. Java 输入与来源构造

FR-BOOK-08/FR-DEMO-07/A-20 新增编译证据：Java示例使用 ReaderInput.fromStream 静态入口和 TextBookSource 二参默认构造，内存/TXT来源切换纳入真实界面。javap确认六个静态工厂、openStream throws IOException、TXT二参及EPUB三参构造存在；Debug/Java/Release构建通过。

未新增或运行测试，不能证明全部工厂和 Java 文件受检异常处理、来源关闭/取消、切换身份/恢复或 EPUB Java接入行为通过。openStream受检声明要求 Java直接调用方处理IOException；fromStream的Function0仍不声明受检异常，SAM/文件/URI工厂为相应用法，08指南已同步。
## 21. 内容布局提供者的错误与取消

FR-MODE-01/I-8/A-17/Q-5.1-5 静态核对：分页器原先仅校验布局块身份，没有拒绝 fragment 改成其他块ID。当前在进入页列表前检查片段身份、可选范围非负/顺序和有限坐标，沿现有 LayoutFailed；provider匹配/测量/片段返回/逐单位装页/padding拷贝增加取消检查，单位列表每块捕获一次。

Debug/Java/Release构建通过，未新增或运行测试。需要坏身份/范围/NaN、多个小单位、中途取消、padding及分页/滚动自定义片段的实际证据。可选范围仍可未知，不强制连续/成对；provider内同步长计算仍须主动协作，不能声称被库强制抢占或全部扩展已经验证。
内置文字路径后续核对补充：TextBlockLayout 构造器原先整批 map 行单位没有取消检查，现由 provider 逐行准备/冻结后传入并复核取消，平台布局前也检查。缓存布局不保存取消回调/context，行高/offset不变。Debug/Java/Release构建通过，未新增或运行测试；StaticLayout单次平台调用、包装/列表拷贝不能被这些检查强制抢占，长段落真实取消延迟及资源证据继续缺失。
## 22. 内容缓存接纳的主线程估重

Q-5.1-1/2/4/5 静态检查：ByteBudgetCache 原 sizeOf 回调在仓库主线程 cache.put 时遍历整章并调用宿主块估重。当前工作线程准备内容和估重，仓库私有绑定捕获权重，主线程缓存仅读取；公开查询不返回内部绑定。共享RetainedSize取消重载也用于分页，块/getter前后检查；未知/负/过预算不接纳策略保留。

Debug/Java/Release构建通过，未新增或运行测试。只证明该估重循环已移出发布路径；实际主线程跟踪、大量块/自定义getter异常及取消、缓存淘汰与显示工作集/整进程峰值仍缺当前设备证据。同步getter不能被库检查强制抢占，稳定非UI约束已写入SPI/架构说明。
## 23. 大目录与全书页状态查询

FR-NAV-01/02/03/Q-5.1-2 静态核对：完整索引反查原先两次扫描章节，无完整索引的前章累计又通过paginationState逐次扫描目录并生成take列表。当前目录/完整索引建立内部ID映射，章状态查询复用；回退按序访问，不创建前缀列表，未知前章仍立即返回null。完整索引反查平均常数时间，部分回退仍线性，页→章保留二分和Long前缀。

Debug/Java/Release构建通过，未新增或运行测试。重复ID、目录删除/重排/快照替换、版本/指纹不符、大目录实际查询延迟和额外元数据占用仍缺运行证据；映射不保留正文且不纳入内容缓存预算，不以静态复杂度推定设备性能通过。
## 24. 默认布局的导航字符边界

FR-NAV-01/FR-MARK-01/I-3 静态核对发现默认 pageForAnchor/rangesForText 只检查 UTF-16 长度，未与实时选择/导入标注共用字符边界。当前文本/标题端点沿 TextCharacterBoundaries 验证，不合法返回 InvalidAnchor；分页与滚动使用同一布局入口。文本末尾仍合法，非文本块仍接受零偏移，未增加自动吸附或新拒绝枚举。

Debug/Java/Release构建通过，未新增或运行测试。代理对/组合字符内部导航、空文本/末尾/跨块范围、打开恢复回退、两种模式及 Android 字符分割差异仍待运行证据。平台 BreakIterator 不保证各系统最新Unicode规则一致，外部自定义 ChapterLayout 不受默认实现的内部验证代管。

## 25. 连续片段行高与 padding 单位读取

FR-MODE-02/I-8 静态检查发现合法有限 spacingAfterPx 仍可能使总行高超出 Android Int 尺寸，滚动 toInt 会饱和。ContinuousFragment 现检查实际Float合计有限并以Double与Int.MAX_VALUE比较，正文须为正有限值；默认分页构造错误沿原LayoutFailed。padding单位列表每块捕获一次，估重/变换复用，避免每行回调getter。

Debug/Java/Release构建通过，未新增或运行测试。超大间距/溢出/整数边界舍入、两种呈现失败恢复、自定义几何和getter及滚动设备行为仍需证据；该行约束不证明全章累计浮点精度、大书资源或全部扩展契约通过。

## 26. 当前职责与历史方案的区分

05第2节按生产源码核对所有者和实际类名，替换拟议但未建立的独立包装层；内部包/当前依赖同步。04与06说明日期和后续修订优先关系，README以实现/剩余证据表呈现，并去掉离屏辅助定位尚未实现的旧说法。历史目标/阶段描述继续保留，当前接入以08及源码契约为入口。

本轮仅编辑文档，未新增或运行测试/构建；上一代码轮Debug/Java/Release成功不等于动态验收。职责核对支持文档与源码关系，不证明全部方法、扩展或原始质量/验收场景完成，也没有删除增强缺口和待产品确认项。

## 27. 默认页锚点查找的扫描范围

FR-NAV-01/Q-5.1-2 静态核对发现默认导航定位在呈现准备阶段遍历全章页，按页filter创建列表。分页工作阶段新增私有按块锚点页条目，列表冻结/取消复核后供布局查询；源顺序/重叠区间首次命中及最后匹配页规则保留，无自动吸附。缓存估重包含新增条目/映射，索引不保存任务回调或正文片段。

未新增或运行测试。内置/自定义未知和重叠offset、合法末尾、无页块、模式定位、索引额外资源和设备延迟仍需实际证据；源块扫描/字符分割/同块条目仍可能线性，不认定全章主线程工作已全部移除。对最新padding包装产物的字段检查未发现BlockLayoutContext/取消回调字段，仅支持该包装的持有关系判断。

本轮 Debug/Java 编译及 Release AAR 构建通过，仅支持可编译结论，未运行测试。

## 28. 多路径共享章节块身份查询

FR-MARK-01/FR-NAV-01/Q-5.1-2 静态检查发现默认布局、选区、标注和辅助来源定位重复扫描整章块列表。ChapterContent在构造时合并身份验证与私有ID→序号表准备；内部查询由上述路径共用。原列表顺序/未知ID和范围检查保留，没有新增公共模型或查找服务。RetainedSize每块结构估算增至72字节，计入列表引用与映射条目/表/整数，预算政策未变。

Debug/Java/Release构建通过，未新增或运行测试。重复/空白/未知ID、超大块目录、内容更新与各类交互/缓存预算趋势仍待运行证据；查找平均常数时间不证明构造/字符分割/范围或长块页条目都为常数时间。内置来源构造在工作阶段，模型构造本身同步不可抢占，宿主来源仍负责自己的执行方式。

## 29. 已有选区文本的逐块比对

FR-MARK-03/04/Q-5.1-2 静态检查发现三处选区有效性校验提取整段临时字符串后再与已有文本比较。当前matches/extract共享私有范围遍历，matches逐块/换行/末尾比对，BookReadView相应调用替换，保留原书章修订与字符规则。AnnotationStore导入标注的可空修订/失效原因和可导出性不变，没有追加任意长度限制。

Debug/Java/Release构建通过，未新增或运行测试。跨块换行/空文本、非法端点/旧修订/错误文本、复制和高亮结果及设备内存仍待动态证据；源码证明消除了该校验的完整摘录副本，不证明提取、全部分割/遍历或选区响应预算达标。

## 30. 标注批量验证与导入的临时列表

FR-MARK-01/07/Q-5.1-2 静态检查：导入原先构造多个ID列表再去重，按章重置/验证先filter创建列表，每条初始状态再扫描章节目录。当前单集合去重、原顺序条件遍历及共用目录查询，拒绝前不改旧数据，异书拒绝仍优先。未增加公共类型或按章标注镜像，失效记录保持原始模型并可导出。

Debug/Java/Release构建通过，未新增或运行测试。重复/异书组合、拒绝原快照/修订、大目录/标注数量、更新失效状态和资源趋势仍缺动态证据；全体标注及原文验证仍同步线性，不能从临时列表减少推定所有主线程响应或内存目标完成。

## 31. TXT 磁盘记录格式与章节完整性

FR-BOOK-03/07/Q-5.4-1 的记录前置补齐：私有文件头有magic/版本，章记录摘要涵盖确切ID/标志/正文序列，读取校验后发布，8KiB读/摘要循环检查取消，头计入索引预算。章节revision保持原输入与实际解析摘要v2；原ID/范围/编码检查和租约finally保留。

未新增或运行测试。无头/改版本、正文/ID/标题标志改动、截断、预算/取消及文件最终释放仍需实际证据。摘要仅在当前Entry内存元数据中；持久目录、稳定解析配置身份、缓存命中/并发租约/原子持久发布和损坏回退尚未实现，TXT跨进程索引复用继续未完成。单次IO/数组分配仍不可强制抢占，不以循环检查证明取消延迟。

本轮 Debug/Java 编译及 Release AAR 构建通过；未运行测试，格式/完整性与持久复用行为仍未验收。

## 32. TXT 文件内目录恢复

FR-BOOK-03/07 的持久复用前置：format2序列化目录/边界/记录数/章节摘要及目录尾部校验，构建实际从文件恢复当前快照，不只写入未使用结构。整文件预算覆盖正文/目录/尾部，验证版本/身份/数量/重复ID/连续覆盖与目录终点，按码元保存标题，批次检查取消并同步文件。记录摘要规则及正文revision v2不变。

未新增或运行测试。目录/trailer损坏、长度/数量/边界/身份改动、长标题/预算/取消与前后快照仍待实际证据。当前OwnedSourceFile仍临时且close删除；稳定输入/解析配置身份、持久发布/命中/并发租约及损坏回退未接入，完整跨进程复用继续未完成。校验摘要不证明文件真实性或输入相等，数组/同步IO不保证抢占。

本轮 Debug/Java 编译及 Release AAR 构建通过；未运行测试，自描述目录和持久复用仍缺动态验收。

## 33. TXT 持久索引复用实现路径

增强设计的跨进程复用新增实现：显式reuseDiskIndex/独立保留预算，原输入一次有界固定快照及SHA/config/策略/平台键；默认策略可复用，自定义ID需persistentIndexKey，否则重建。缓存短期非阻塞目录租约内复制到来源私有文件，再恢复目录/验证全部章摘要；坏缓存回退重建。发布暂存sync+rename，按访问淘汰最终条目预算/最多128条、清理pending；忙/缓存IO失败不阻断来源，取消传播。长期共享payload借用改为私有副本，复用解析结果但仍有扫描/复制IO。

Debug/Java/Release构建通过，未新增或运行测试。路径接通不证明跨进程命中通过；需首次/重复/冷进程打开、每个配置/输入/策略/平台变化、未知策略键、损坏目录/正文、锁/权限/删除失败、取消/关闭和总临时资源/性能趋势证据。保留预算不含原始spool/活动副本或整磁盘，未声称永久缓存可靠存储、全部原始验收或增强交付完成。

## 34. 持久索引缓存的预算扣减

Q-5.1-4 静态检查发现缓存大小求和饱和后再删除扣减不能恢复真实总量。当前从非负剩余budget-incoming逐项接纳，较近且能放入条目优先，零/超容量/数量条目尝试删除，失败不发布；目标存在或输入条目不适合保留的publish分支也整理预算。筛选/排序键检查取消。

Debug/Java/Release构建通过，未新增或运行测试。真实大/稀疏文件、多个来源预算、数量边界/相同访问时间、删除失败/锁忙/取消和实际保留字节仍待动态证据。目录整理可淘汰原目标且该次不重发布，后续仍可重建；保留预算不是活动spool/私有副本或整磁盘上限，缓存没有持久可靠存储承诺。

## 35. TXT 目录与缓存校验缓冲

Q-5.1-4 静态核对：目录每个字符串和缓存校验每章创建8KiB数组，现复用操作局部缓冲，目录摘要同源复用。最新产物DiskTextSnapshot字段未出现工作缓冲；不新增全局池/共享状态或公共签名，格式与取消检查保留。

Debug/Java/Release构建通过，未新增或运行测试。大量章/标题、缓存命中/未命中、取消与GC/设备内存仍待动态证据。目录结果、字符串/CharArray/章摘要及活动文件仍有原分配，不把工作数组复用认定整体常量内存或持久复用验收通过。

## 36. 正文辅助动作异常与可见切片

Q-5.2-2/Q-5.3-2 静态核对：BodyAccessibilityHelper六处runCatching隔离所有Throwable，改为普通Exception日志/拒绝，Error传播，锚点比较普通失败同规则；可选range失败仍可阅读描述。Cursor切片身份改确切可见文本与原文范围，取消仅hash+length判断，避免不同裁切/描述碰撞保持旧游标。节点内局部描述移动已有实现，不据此把复杂离屏自定义语义认定完成。

未新增或运行测试。每类可选属性/动作异常、普通与致命错误、切片hash碰撞、显示工作集清理/焦点/服务事件仍待运行证据。确切文本随原clearFocus/replace清理，预算为显示工作集；不宣告所有平台异常、所有辅助交互或增强能力已经验收。

本轮最终 Debug/Java 编译及 Release AAR 构建通过；未运行测试。所有游标进入/相邻切片构造均使用确切文本身份。

## 37. 当前 Release JVM 接口产物清单

FR-BOOK-08/A-20新增产物读取证据：09及generated清单绑定当前AAR/classes.jar摘要，输出所选公开包中Java可见public类型的public/protected成员，489类条目/207声明/Demo测试类名匹配0，Manifest包/minSdk21无权限Activity。TXT复用与策略getter签名已出现，修正拟议spi.effect/effects为实际api.effect，并同步api.input/spi.selection。

本轮未修改代码或运行测试/构建，只读取上一代码轮成功生成的当前产物。207声明包含Kotlininternal/桥接/嵌套类型，不是支持API数量；清单不是兼容基线，Kotlin可空/默认/线程契约仍以源码和08为准。独立消费/依赖/压缩/全部Java签名和原始验收缺口保留，不把归档读取当作运行通过。

## 38. Java TXT 自定义策略和配置编译

FR-BOOK-08/A-20新增真实Java调用：TextContentIdProviderAdapter无状态默认桥接，Java示例匿名覆盖块ID/键，默认章ID不补空实现；TextBookOptions默认getter及完整构造配置磁盘/复用。Debug/Java/Release构建通过，当前产物清单/摘要刷新490类/208声明、Demo测试类名匹配0。

未新增或运行测试。只支持具体Java调用编译可用，不证明默认桥接运行、全部Java策略、缓存命中/关闭/更新和旧快照失配回退通过；策略键责任和null不复用保持，未引入配置builder/全局JVM默认模式。声明数量包含编译器入口，仍非支持API总数。

## 39. 正文辅助与键盘焦点刷新

Q-5.3-2 静态核对：refresh此前仅检查辅助焦点节点的可见矩形，没有清理键盘离屏焦点或不再可读的描述。现两类焦点都检查节点/可见区域/可读描述，失效沿同一clearFocus，游标/固定扩展/事件清理，未增加自动焦点迁移或导航提交源。

Debug/Java/Release构建通过，未新增或运行测试。双焦点、裁切空/属性异常、隐藏/attach/滚动及回收、服务/键盘事件仍缺运行证据；原刷新周期不等于任意宿主属性变化即刻通知，不宣告复杂自定义语义或辅助服务完整验收通过。


## 40. 自定义文本原文契约

FR-BOOK/FR-MARK与Q-5.3-2静态核对：此前原文流程只能识别TextBlock/HeadingBlock，宿主文本块即使提供正确几何也被视作不支持。现新增TextContentBlock，默认排版、分页范围/锚点、选择提取/匹配、标注校验和辅助来源遍历识别它。内置标题仍HeadingBlock，宿主提供者优先；纯图形和任意局部描述不自动成为原文。

AttributedTextBlock及长文本示例沿公开模型，无新导航/选择管线。自定义元数据未被不完整text指纹替代，revision/提供者version责任保持。Debug/Java/Release构建通过，产物清单492类/210声明，Demo/测试类名匹配0；getText及内置文本实现关系有字节码证据。未新增或运行测试，自定义文本选择/标注/Unicode边界/跨视口与跨章普通辅助移动、元数据更新/恢复、宿主提供者优先与设备行为仍待验收。复杂非文本局部语义继续未完成，不认定原需求完整通过。


## 41. 原文布局偏移上界

布局SPI静态核对：此前片段只检查非负/双端有序，单位偏移不检查，TextContentBlock范围可超出原文。现默认分页器在打包前逐单位并在发布前逐片段共用数值规则；已知文本偏移不得超过text.length。每块只读取一次长度、逐单位检查取消，异常仍沿LayoutFailed，不引入另一提交源。

Debug/Java/Release构建通过，未新增或运行测试。此证据不证明数值正确的范围必定匹配绘制/辅助描述，也不证明字符边界、自定义布局重叠/遗漏、null端点、违约/取消/旧页面保留及设备行为通过。当前09清单为第40节产物快照，不作为本节AAR摘要证据。


## 42. 可见辅助描述的原文匹配

FR-MARK与Q-5.3-2静态核对：可见辅助范围此前只比较块ID和描述长度，同长度错误描述可进入原文选择/离屏来源边界。现TextSelectionResolver.matchesBlockRange校验真实TextContentBlock字符串、范围/字符边界及regionMatches；分页/滚动通过当前绑定回调共用，回收/解绑清除，无额外来源读取或选择提交。

普通局部描述仍可朗读；无快照/非文本/不匹配范围不给原文映射，保留既有无来源视口/整块描述回退。映射不按导航busy拒绝，原焦点有效性可跨准备期核对。Debug/Java/Release构建通过，未新增或运行测试；同长度替换/越界/Unicode、匹配范围、回收/更新/取消、等待导航焦点及辅助服务事件尚未验收。该检查不证明几何/绘制正确或复杂非文本局部语义已完成。


## 43. 滚动行当前绑定与旧动作

FR-MODE、Q-5.1及Q-5.3-2静态核对：正文辅助归属此前使用contains全表扫描且按Row.Text数据相等；相邻移动另扫描原行位置。现RowsAdapter.isBound按ViewHolder当前适配器/位置和行引用检查，正文辅助回调及边界重试共用，NO_POSITION/替换/等值新行拒绝旧绑定。起点改取holder位置，去除这些归属检查的线性扫描；不声称整套滚动/辅助遍历为常数时间。

Debug/Release构建通过（6秒，Demo Kotlin执行、Java任务已是最新），未新增或运行测试。预布局/notify更新/复用/临时detach、慢导航期间原焦点、同章/跨章/旧回调以及大章节性能仍缺运行证据。复杂非文本局部语义继续未完成，09仍为第40节产物快照。


## 44. Widget 的本地 Maven 交付

Widget交付/实施发布项核对：此前仅AAR，未配置依赖元数据。现release组件生成AAR、源码、POM/module和本地仓库ZIP，默认开发坐标org.peyilo.ireadview:ireadview:0.1.0-SNAPSHOT，可覆盖版本。本次读取POM：kotlin-stdlib 2.0.21 compile，core-ktx 1.10.1/recyclerview 1.4.0/customview 1.2.0/coroutines-android 1.9.0 runtime；module有API/runtime/sources变体，没有测试依赖。

Debug及本地发布包构建通过（9秒），ZIP静态清单/摘要见generated/local-publication-inventory.json；未新增或运行测试。产物生成不证明独立宿主解析/编译、XML、压缩、Java全部API、旧二进制兼容或设备行为。开发快照不代表正式版本；未配置在线仓库或上传，仍保留完整交付/验收要求。09签名清单仍是第40节产物快照。


## 45. 独立 Maven 宿主编译消费

交付边界与Java接入核对：examples/maven-host独立settings只含宿主app，依赖库Maven坐标，无库project/includeBuild、直接AAR或手工运行依赖。包含XML Kotlin页面、Java程序创建页面、监听/回调/取消/实例内快照及宿主TextContentBlock。Debug APK构建通过（23秒，33任务执行），实际执行Kotlin/Java编译、资源编译/打包。

运行依赖报告从Maven模块解析AndroidX/协程，Core KTX经约束为1.13.0，说明宿主最终版本可能不同于POM声明；具体图和APK摘要已保存generated文件。未新增或运行测试、未安装/启动。证据证明此工具链/示例的编译消费，不证明XML实际膨胀/渲染、恢复、压缩、全部Java API/外部版本兼容或设备矩阵。示例不实现进程级快照持久化，不以实例内保存替代原恢复需求。


## 46. 独立宿主压缩 Release

库交付/XML边界核对：Maven宿主启用R8代码与资源压缩，Release构建通过（26秒，44任务执行），生成未签名APK。AAPT生成ReaderView Context/AttributeSet构造保留规则，R8映射类名未改；APK的layout/activity_main指向本轮res/v9.xml，静态XML仍含完整Widget名称。规则/映射/XML摘录和APK摘要已保存generated文件，没有新增整库keep或忽略全部警告规则。

未新增或运行测试，未签名/安装/启动。证据覆盖该工具链与宿主的压缩构建，不证明XML实际膨胀、恢复/反射/服务/设备行为或所有公开API兼容；产物大小不是性能测量，资源重写路径不是稳定接口。所有原始行为验收项继续保留。


## 47. 独立宿主保存重建快照

FR-NAV-07/宿主接入核对：示例此前仅实例字段，现Kotlin/Java页面在onSaveInstanceState导出轻量SnapshotBundles，onCreate读系统Bundle、onStart创建来源并restoreSnapshot。宿主schema和库format检查，块/偏移配对及模型约束验证，无效记录普通异常诊断并丢弃；不吞致命Error、不序列化正文/来源/View。

Debug编译打包通过；Release首次遇Windows dex文件占用失败，未修改规则，重试R8/打包成功。generated产物摘要和静态XML/映射同步，未新增或运行测试、未安装/启动。代码路径不证明旋转/进程回收、损坏Bundle降级、压缩恢复或View/宿主状态优先级通过；系统Bundle重建不替代普通冷启动的宿主磁盘历史，原范围保持。


## 48. 类结构与实现表的规范正文

设计文档核对：04第5节仍为早期data class/ContentBlock文本草案，第2节把全部扩展归为spi，自定义块无例外要求提供者；05第3节仍写SelectionController/StyleDiff/EffectManager。现直接对齐源码的class/集合复制、TextContentBlock与实际包归属/默认提供者/错误路径、EPUB参数/ReaderInput工厂及当前职责。02摘要同步当前架构与本地Maven交付，保留后续未决/增强与行为验收范围。

本轮只读源码并修文档，没有改代码、构建、新增或运行测试。证据证明上述文档段落和当前声明一致，不证明所有旧签名已被逐个重审或整个库验收完成；09仍是历史产物快照，08为当前接入指南。


## 49. 连续滚动章节累计精度

FR-MODE/FR-NAV与Q-5.1静态核对：原章节起点和offsetFor各用Float重复累计，大章节单像素精度易丢失。现章节累计位置统一DoubleArray/Double，offsetFor读同一starts；可见定位/seek/跨章及选区边界保持累计精度，扣除章节起点后才交局部Float/整数接口。二分中点避免索引和溢出，公开模型/导航提交保持。

Debug/Release构建通过（15秒），未新增或运行测试。每项累计存储4→8字节，属于当前候选工作集，未把缓存预算当总内存上限。证据不证明所有规模精度、任意自定义几何、实际末尾/跨章/恢复/辅助/选择或性能通过；现有发布/独立宿主/JVM清单各绑定之前产物，不能冒充本轮构建证据。


## 50. 滚动派生数值表的线程与取消

Q-5.1主线程/取消核对：Chapter构造原在主线程map行高度并累加起点。现loadChapter将纯数值IntArray/DoubleArray准备交Default，每片段ensureActive，空章高度主线程捕获；在layout/片段回调后与工作返回后核对书籍/代次/detach。尺寸getter须稳定且不读取View，ChapterGeometry没有任务/回调字段；返回后候选持有数值数组，不新增共享缓存或提交点。

Debug/Java/Release构建通过（8秒），未新增或运行测试。数组分配/单个宿主getter不能强制抢占，行列表重建/局部几何仍有主线程工作；大章节响应/取消/重入/旧结果和真实内存缺运行证据。不能把这项编译与源码证据替代全部性能/设备验收。


## 51. 连续锚点片段索引

FR-NAV/FR-MODE与Q-5.1静态核对：offsetFor此前扫描全部连续片段，现Default准备blockId→IntArray序号，查询只访问目标块并沿starts计算位置。按源顺序记录，不假定任意自定义范围排序；首次匹配、边界最后fallback与未知块返回保持。取消检查覆盖构建/冻结，映射只有ID与序号，不复制片段/任务。

Debug/Release构建通过（7秒），未新增或运行测试。候选工作集增加每片段序号及映射，构建期临时boxed列表仍有峰值，缓存预算不覆盖全部工作集。大量块、单长块、非连续/重叠范围、空文本、选择/辅助/恢复及设备响应/内存缺运行证据；目标块内仍线性，不宣告整体常数时间或性能验收。


## 52. 连续行分段与章节复用

FR-MODE-邻章续接/Q-5.1静态核对：rebuild此前主线程创建全部保留章行，seek扫描行表。现Chapter在Default逐行构造/取消/冻结，只读章节行保留实例；RowSegments组合章节/边界并按段访问，seek按章节行表身份查段起点+chunk序号。总行数检查Int上界，原章节窗口/绑定归属/通知/提交保持。

Debug/Release构建通过（6秒），最新AAR字段核对Chapter未保存取消回调（原fallback lazy及呈现归属仍在）。未新增或运行测试。冻结复制/数组分配不可强制抢占，RecyclerView通知/布局/绑定仍有主线程开销；前后续接/空章/淘汰/旧动作、取消/恢复/选区/辅助及响应/内存缺运行证据，不能把数据分段替代整个性能矩阵验收。


## 53. 书籍效果错误的逐监听归属

事件一致性静态核对：BookReadView.onPageTurnEffectError此前独立无谓词dispatch并仅检查代次后publish。现效果/来源错误共用publishReaderError的代次、打开序号和lastError对象检查，每位书籍监听器与后续状态发布复核；效果保留phase，来源错误仍按位置选择Error/Ready，不扩大错误影响范围。

Debug/Java/Release构建通过（8秒），未新增或运行测试。证据只覆盖这条书籍错误路径，未证明通用ReadViewListener事件、嵌套错误/导航/换书/关闭、效果中断与设备行为全部通过。原验收范围和通用容器职责保持。


## 54. 同一内容代次的效果通知归属

FR-FLIP事件一致性静态核对：通用效果排队通知此前只有内容代次门禁，同书更换效果/选项不能使旧事件失效。现捕获私有效果配置序号，执行前、子类钩子返回后及每位ReadViewListener前复核；初始和公开配置设置均推进序号，日志仍保留，公开选择结果不变。

Debug/Java/Release构建通过（6秒），未新增或运行测试。证据限定BaseReadView排队入口与通用监听；不证明子类钩子内部书籍监听因效果重入停止、插件create/attach/detach内部重入、重新挂载或设备时序已通过。原需求/验收范围保留，既有Maven/JVM清单仍绑定历史产物。


## 55. 效果安装和中断的同步归属

FR-FLIP/生命周期静态核对：设置入口中断导航可触发宿主监听，工厂和效果创建/绑定/解绑可同步再次配置。现容器捕获配置序号/代次逐边界复核，Manager捕获安装序号并使用短期调用方谓词，失效安装内部null映射既有Superseded；候选清理不覆盖新选择。解绑前选择none、捕获旧实例/缓存ID，options重入不保留已解绑实例。abort先结束active所有权，旧finish失败和degrade有身份/安装门禁。

Debug/Java/Release构建通过（7秒），未新增或运行测试。证据限定源码边界和编译；未证明所有插件回调、同实例违规工厂、旧候选清理诊断归属、辅助/子类监听、资源租借和设备重入通过。原范围保留，历史发布/JVM/独立宿主清单未刷新成当前产物。


## 56. 旧效果清理诊断与书籍监听

FR-FLIP事件一致性核对：cleanup此前在异常发生后才由容器捕获配置，插件重入或已失效候选清理可能把旧诊断归到新配置。现调用前捕获安装/内容/配置归属，失败后核对，候选detach另用原请求谓词；过期诊断仍日志、不进入监听队列。书籍fallback/error使用同一配置捕获，在逐ReaderListener及后续错误状态发布前复核，来源/导航错误使用原默认门禁。

Debug/Java/Release构建通过（7秒），未新增或运行测试。未证明实际异常资源释放、所有运行回调/生命周期、辅助或完整原行为矩阵；不清除已记录lastError、不把日志数量当验收。既有发布和JVM清单仍是历史产物证据。


## 57. 运行效果事务及supports取消

FR-FLIP/导航静态核对：supports后此前只查协程、Turn从可变effect读取，取消异常会落入普通Exception降级。现transition捕获实例/ID/安装序号/容器归属，尺寸getter和supports后复核，取消显式传播；无动画show前后、失败终止后、遮罩回调及show/finish边界统一核对，Turn要求active身份。沿既有SupersededNavigation和最终提交路径，归属回调不保存到Turn。

Debug/Java/Release构建通过（4秒），未新增或运行测试。证据覆盖源码与编译，不证明设备上的回滚/取消、全部自定义回调、动画/位图/辅助及完整原需求验收；历史Maven/JVM/独立宿主证据未替换为本轮产物。


## 58. 导航准备与替代通知归属

FR-NAV/单次提交静态核对：navigate原在interrupt通知后再取token，Preparing通知后直接prepare，非Ready结果未复核。现预留token再取消/Idle通知，通知后检查归属；Preparing后及prepare返回后统一复核，Ready先登记finally清理。transitionState回调后检查再通知，过期普通异常返回Superseded，有效异常仍为NavigationFailed。位置提交保持既有来源。

Debug/Java/Release构建通过（3秒），未新增或运行测试。证据覆盖源码/编译，不证明所有通知嵌套、适配器忽略取消、候选dispose异常、实际回滚及原完整矩阵验收。历史产物清单保持历史归属，未重新发布。


## 59. 候选清理错误及finally归属

FR-NAV/清理一致性静态核对：candidate.dispose此前runCatching忽略所有Throwable。现普通Exception保留导航结果，报告NAVIGATION/CandidateDisposeFailed（不可重试、cause保留）；Base日志及排队沿原token/代次，书籍/通用逐监听复核。嵌套finally按token解除本任务，致命Error传播，旧清理不清新pending；阅读phase保持，默认呈现PageCleanupFailed仍沿原边界。

Debug/Java/Release构建通过（7秒），未新增或运行测试。未证明实际候选释放、所有致命/普通异常路径、取消回滚、通知重入及原完整矩阵；历史发布产物清单未更新成当前代码证据。


## 60. 通用适配器Host绑定归属

FR-EXT/生命周期静态核对：BaseReadView给适配器的Host此前可在解绑后导航/上报错误，动态generation也不能识别旧绑定。现安装/解绑推进私有序号，Host以序号+实例检查导航/位置/错误/能力，旧请求Superseded、release保持Released；能力查询/子类钩子/逐监听/辅助事件检查同一绑定，防止同实例重绑复活旧Host。

Debug/Java/Release构建通过（4秒），未新增或运行测试。源码与编译不证明create/attach/viewport重入、实际View解绑/遗留清理、旧Host设备调用、辅助或原完整矩阵验收；上述绑定/解绑实现仍须继续完善，历史发布清单未刷新。


## 61. 适配器解绑的View清理

FR-EXT/生命周期静态核对：detachAdapter原在插件detach后读取contentView，detach异常会跳过移除，getter变化也可能移除错误View。现安装缓存实际View，解绑先清引用/绑定序号，finally移除捕获View；检查parent与新绑定接管，旧清理不移除新绑定使用的同一View。detach异常保持传播。

初次编译补正缺少的View导入，最终Debug/Java/Release构建通过（4秒），未新增或运行测试。证据不证明完整create/getter/attach/viewport重入、View移除异常、release逐项收尾或设备资源释放；原范围和后续工作保留，未刷新历史发布清单。


## 62. 永久释放的普通异常收尾

生命周期/资源静态核对：BaseReadView.release原onRelease或detach异常会跳过后续任务/效果/监听清理，书籍coordinator.close异常也会跳过Released/监听结束。现容器先设置永久释放并清监听/动作，书籍先结束打开归属/阶段/监听；共用内部函数逐项执行普通Exception后续步骤，首异常及suppressed最终向调用方传播。致命Error立即传播，二次release仍幂等且不自动重试失败步骤。

Debug/Java/Release构建通过（5秒），未新增或运行测试。证据仅证明源码顺序/聚合和编译，不证明任意插件/来源资源成功释放、致命错误中断下全部步骤、窗口生命周期或原完整矩阵；绑定回调重入工作仍保留，历史产物清单未刷新。


## 63. 临时窗口解绑的异常和会话归属

生命周期静态核对：onDetachedFromWindow原清理异常会跳过后续适配器/效果和Android super，书籍选区通知可换书后继续暂停新会话。现普通异常逐项聚合，取消捕获旧lifetime，Android super置finally；书籍先撤销旧relayout/设置等待阶段，独立清理，暂停前检查内容代次/打开序号/release。临时解绑保留会话与监听，不设置永久Released。

Debug/Java/Release构建通过（5秒），未新增或运行测试。证据覆盖源码和编译，不证明资源成功释放、致命Error后全部库步骤、完整窗口绑定重入、super自身异常、设备恢复或原矩阵验收；历史交付清单未刷新。


## 64. 恢复和尺寸回调间的归属

生命周期/FR-STYLE静态核对：onAttachedToWindow和onSizeChanged原在宿主/插件回调后继续调用子类入口，书籍resumeWork后也无会话复核。现窗口入口捕获lifetime/代次/适配器/绑定，模式和效果回调后复核；书籍恢复工作/位置后按打开序号检查。尺寸失效及viewport通知后核对预期代次/绑定再进入子类钩子，沿既有两层Widget职责。

Debug/Java/Release构建通过（5秒），未新增或运行测试。证据不证明完整create/getter/attach安装事务、异常回滚、窗口重新绑定/恢复、尺寸重排/辅助或原完整矩阵；历史发布清单未刷新，完整安装事务继续保留为后续工作。

## 65. 仿真翻页、控制点与任意 View 装饰（2026-10-08）

按用户最新优先级完善 Apple/iBook、Google/MoonReader 两类仿真。curl 保留为 Apple 兼容别名；CurlOptions/CurlControlPointPolicy 公开外观、网格和控制点策略，非默认配置不伪装成可恢复默认工厂。Apple 用纯几何 BoundCurlGeometry 取代归位零向量奇点，约束正面装订边并使两端网格连续退化；是逐行圆柱二维近似，倾斜折边阴影仍有局限。Google 以本机 PlayLikeCurl 为主要参考，移植波形/半径增长/平移/透视关系至 Canvas 网格，许可通知保留。两种效果共用双快照预算生命周期。

ReaderPageFactories.withDecorations 组合任意 View 页眉/页脚，默认正文继续参与既有测量和导航；提供独立 create/bind/unbind/分钟更新协议。CurlActivity 以真实 ImageView/ProgressBar 装饰展示效果切换和拖动，状态区固定高度以避免切换时重排。

Debug/Java/Release 构建通过（5秒）。本次源码 APK 在 API36、1080×2424 模拟器手动检查两种效果，各观察到前翻/短拖复位/后翻页码 1→2→2→1，并检查实际中间帧。直接调用已编译几何的临时采样覆盖 70,525,455 材料点：装订边位移0；相邻控制点 x/y 各0.01px的样本最大位移0.103272px，归位差0.001px时最大位移0.005982px；端点断言通过。这是有限采样，不是全域误差上界或完整交互验收。未新增/运行仓库自动化测试套件。

## 66. 按用户要求回退 iBook 逐行卷曲（2026-10-08）

用户指出视觉实现未达到要求，并明确要求直接回退。已删除 BoundCurlGeometry，恢复上一版 CornerCurlEffect 斜圆柱投影；没有用另一套新模型替代。§65 中 Apple 的连续性/绑定边采样、截图和检查只描述被撤销方案，不代表当前源码或已完成需求。装订边和归位突变重新列为未完成；Google、公开自定义参数与任意 View 装饰保留。04/05/10 当前说明已同步更正。
回退后 Debug/Java/Release 构建通过（7秒），模拟器已安装回退 APK；没有将构建成功视为 iBook 视觉验收通过。

## 67. 全模式演示、交互装饰与 RTL 观察（2026-10-08）

补齐 CurlActivity 的六效果入口、方向/自定义参数/模式切换；任意 View 装饰增加真实 Button 与 SeekBar 交互。控件绑定使用页上下文，解绑撤销监听；ReaderPageDecoration 的主线程、稳定 View、重复绑定/尺寸/清理契约已写入公开注释。自定义策略重映射 x 的平滑行程，使 Google 示例实际消费控制点策略。状态容器固定48dp、标题单行省略，提示文字不再改变正文视口。实测发现 Demo 未声明 supportsRtl，已修正并构建/安装；最后 Debug/Java/Release 构建通过（1秒）。


没有新增/运行仓库自动化测试套件。iBook保持回退、未完成；连续滚动组合、快速变向、生命周期配置变化、API21/不同尺寸/性能与完整原始需求仍待独立确认。历史发布包/JVM清单未更新。

## 68. 独立模拟器与斜圆柱候选预览（2026-10-08）

用户确认正在操作 emulator-5554，后续安装/输入转到独立 AVD iReadCurlReview（emulator-5556）。未修改或关闭用户设备。Demo 长按 Apple 可预览宿主内部 DemoObliqueCurlPreview，普通点击 Apple 使用回退版；公开内置 iBook/curl 工厂仍为 CornerCurlEffect。候选保留斜圆柱单位法向、装订边约束与连续端点，不新增第三类公开仿真效果，也未宣称 iBook 已完成。

直接调用已编译 Kotlin 候选采样14,040,675材料点：坐标有限、正面装订边误差0、端点断言通过；控制点x/y各变0.01px最大样本位移0.030527586px，右侧归位差0.001px最大位移0.002100177px。单尺寸LTR的三个起始高度观察到1→2→2→1，并截取回退版/候选相同拖动中间帧及候选近归位单帧。首次检查受ADB暂时离线影响已作废重做。采样/单帧不能证明像素连续、跟手感或视觉质量通过。



## 69. 候选复用 CurlOptions 与独立双向检查（2026-10-08）

Demo斜圆柱候选现在消费同一个CurlOptions与CurlControlPointPolicy，支持网格/半径/背面tint和内容比例/阴影配置；RTL触点转为规范坐标，阴影关闭时不绘制。Demo保持候选选择，参数开关不再切回回退版。所有改动仍在宿主候选，内置iBook/curl继续使用CornerCurlEffect。


内置iBook保持回退；候选质量、复杂斜拖/遮挡、像素连续、画质预算、快速变向/多点/边界/生命周期、API21/不同尺寸/性能和原始完整需求继续保留。没有把页码/构建通过当作视觉验收。


## 70. 斜圆柱面分区合批与快照画质对照（2026-10-08）


编译后的Kotlin模型检查3,932,410个顶点、34,038个裁剪多边形：坐标/纹理/索引/半平面/反射检查通过；相对原投影的最大样本误差0.00017263349px。软件渲染独立矩阵48条页码均符合预期，并查看实际合批中间帧。临时检查放在忽略的build目录；未新增或运行仓库自动化测试套件。

Demo长按“仿真参数”通过现有PageTurnOptions在8/16MiB共同预算之间切换，库默认不变。日志确认1080×1794视口分别租借540×897与1080×1794纹理；正文1,332,800像素对照，半尺寸近归位/完成平均RGB差11.673046/255，原尺寸软件分区样本0.040849/255、硬件合批样本0.048879/255，原尺寸最大通道差2。证明本样本锐度变化主要来自降采样，不宣称全页/全部路径逐像素一致。


Debug与库Release构建通过（2秒），只在独立设备安装。候选仍未获视觉认可，内置iBook保持回退。低API/真机/不同尺寸、复杂斜拖/快速反向/多点/页面边界、透明模板/资源峰值/长时间性能与全部原始需求仍保留。历史发布包/JVM清单未刷新。


## 71. Google 离屏阴影与反向拉回复位（2026-10-08）


Debug与库Release构建通过（28秒），APK只安装到emulator-5556。默认/自定义×LTR/RTL的前翻/后翻晚段8张截图已查看，8次松手页码符合预期；前翻离屏后无固定阴影，后翻仍有可见形变与随卷边阴影。这是样本检查，不证明整个动画逐帧连续。


用户确认共享emulator-5554有同时操作，此前停手后页码变化不判作程序问题；后续仅操作独立5556。内置iBook仍为回退版，不在本轮通过项中，候选未获认可。模式/效果在拖动或收尾期间切换仅核对了源码语义，实际交互、边界/生命周期/低API/真机/性能与完整原始需求仍未完成；未新增或运行仓库自动化测试套件，历史发布包/JVM清单未更新。

## 72. 持续触摸时切换效果与模式结果反馈（2026-10-08）

Demo阅读模式按钮原先忽略回调，现在处理成功、Busy、取消、替代和失败；独立请求序号及onStop失效避免旧回调继续更新界面。库既有配置取消和模式拒绝协议不变，04/08/10同步说明宿主处理方式，没有添加公共控制器或自动重试队列。

只在独立emulator-5556执行分离触摸保持按下，再用键盘激活已有按钮。候选拖动中切换模式返回Busy且拖动继续；拉回松手后切至连续滚动，树含RecyclerView，随后返回分页，页脚均1/23。忙碌现场截图和三份XML已保存并检查，不以页码等同完整锚点验证。


Debug与库Release构建通过（3秒），APK只安装到5556，用户5554未被操作。未新增或运行仓库自动化测试套件。内置iBook继续回退，候选未获认可；收尾竞态、PageTurnOptions运行检查、多点/取消/生命周期/边界/资源峰值/低API/真机/性能及完整原始需求仍保留。历史发布包/JVM清单未更新。

## 73. 候选斜拖折轴与RTL文字栅格归位（2026-10-08）

对照参考ireadview拉动方向，宿主候选把atan(dy/整页高度)改为atan2(dy,max(0,w-controlX))乘端点包络。保持单位法向、装订边钳制、半径与分区投影；给出包络消除归位方向歧义的连续性说明。当前编译模型14,040,675材料点的有限/装订/端点断言通过，0.01px邻点最大样本位移0.06506531px，0.001px归位邻域最大0.0048828125px；3,945,700分区顶点与解析投影最大样本误差0.00017263349px。旧迭代采样及性能数据不作为新角度模型的验收结果。

实际查看顶部角度修改前/后、底部修改后与最终RTL中间帧。最初斜拖捕获未达到水平主轴启动条件，已作废重做；最左缘后翻又被系统返回手势抢占，已作废并只把独立5556切为三键导航重做，用户5554未被操作。新视口1080×1731、24页，不混作此前1794高/23页证据。

候选RTL近归位/完成正文370个像素最大通道差38，追溯到镜像Canvas捕获的字形栅格差。改为显示方向捕获，现代路径镜像纹理坐标，旧路径反向材料列，平面端点直接绘制原快照；不增加位图租借或修改公共SPI。相同默认RTL样本最大差降为2，自定义最大3。四组正文ROI平均RGB差约0.045747至0.045873/255，均无通道差≥4像素；不外推到全页、全部字体或任意View。


最终Debug与库Release构建通过（2秒），只安装到5556；未新增或运行仓库自动化测试套件。内置iBook仍保持回退，候选未获视觉认可。手指跟随、极端斜拖/收尾竞态、较早API/真机/不同尺寸、资源峰值和当前模型性能仍未完成。历史发布包/JVM清单未更新。


## 74. Google RTL快照与API28兼容检查（2026-10-08）

Google改为按显示方向捕获View，在RTL网格反向材料列后镜像几何；内部捕获钩子默认保留回退iBook路径。公共API、快照租借数量及PlayLikeCurl波形/透视参数不变。独立API36同条件正文ROI对照，自定义RTL近归位/完成最大通道差40→9，平均RGB差1.111525→1.078343；默认波形在该非端点仍有实际形变，不将剩余差异判作文字捕获缺陷或宣称全部视觉问题已解决。


Debug与库Release构建通过（7秒），仅安装独立5556/5558，用户5554未被操作。未新增或运行仓库自动化测试套件。内置iBook继续回退，候选仅Demo主动启用且未获认可；较早API的一组运行不能替代API21/真机/全帧视觉验收。极端手势、收尾竞态、生命周期、资源峰值及完整需求仍未完成，历史发布包/JVM清单未更新。

## 75. Google自定义幅度上限的复现入口与视觉问题（2026-10-08）

Demo长按Google切换radiusFraction=0.3强卷曲，保留普通默认/自定义控制点与清晰预览入口。补充API字段语义：Apple使用圆柱半径/页宽；Google把该值乘2.25作为参考波幅，并耦合宽度压缩；背面设置只作用Apple。没有新增公共控制器或第三种仿真效果。

初次PowerShell公式计算因数字重载取整误判可见投影反向，已作废；据此尝试的自适应透视修改已撤回，最终库公式保持距离2。独立Java Float模型对当前公式6个半径各采样1,002,001点，有限/正分母通过，可见投影水平导数样本均为正；这不是Android渲染器运行或全范围证明。


最终Debug与库Release构建通过（5秒）；只在5556安装最终版本，5558保留上一节版本，用户5554未被操作。未新增或运行仓库自动化测试套件；内置iBook继续回退。Google上限参数的压缩/行程关系、整段动画反馈、其他未完成需求与历史发布包更新仍保留。

## 76. Google幅度与完整可见行程（2026-10-08）

修正第75节的强幅度提前离屏问题：新增内部GoogleCurlGeometry，保持PlayLikeCurl波形、压缩、透视2及明暗公式，一次求解右边缘刚好离屏的相位，再映射到完整canonical控制进度。每帧只更新相位和投影，不求根、不分配网格数组。阴影按剩余可见宽度衰减，端点趋于透明。公共工厂/选项/两快照预算与内置回退iBook不变。


查看强幅度LTR/RTL前后翻4张代表帧，原先没有卷面的后翻时刻现在均出现卷面；4次页码符合预期。API36普通/强幅度各默认/自定义×LTR/RTL×三起始高度，每组1→2→2→1，共96条记录0不符；API28普通幅度同矩阵48条记录0不符。共144条矩阵记录含最终APK哈希，分开记录API36的24页/API28的27页。


最后行为修改后Debug与库Release构建通过（4秒），只安装独立5556/5558，未操作用户5554。未新增或运行仓库自动化测试套件。内置iBook仍回退、候选未获认可；整体视觉质量、极端手势/收尾竞态、生命周期、资源峰值、真机和完整需求仍未完成。历史发布包/JVM清单未更新。

## 77. 页装饰的原生交互与现行设计说明（2026-10-08）

检查DecoratedPage与BaseReadView事件路径，包装模板不替子View安装触摸/点击代理，Widget没有拦截子View事件的onInterceptTouchEvent覆写。ReaderPageDecoration.view注释补充正常Android分发与自定义View消费输入的职责；不添加统一手势代理或公共控制器。


10第1–5节现行设计替换旧Google固定映射，明确GoogleCurlGeometry/EdgeCurlEffect分工与RTL捕获，标记后续各轮历史记录；02架构文档同步。仅修改注释/文档，没有事件实现改动、没有重新构建或安装、没有新增/运行仓库自动化测试套件，用户5554未被操作。内置iBook仍回退，其余未完成范围与历史发布包/JVM清单保持待处理。

## 78. 旧硬件分区候选因实际性能撤回（2026-10-08）

尝试让API28硬件Canvas消费现有斜圆柱分区：Shader仿射三角形、裁剪后drawBitmap均未保留。几何模型重新核对3,945,700顶点及34,038多边形通过，但定时画面滞留早期位置，不能作为视觉改善证据。原路径/裁剪原型各4组前后翻页码正确，仍未达到可用绘制条件。

重置gfxinfo后的同设备/同手势窗口：原型35帧、P50约550ms、P90约650ms；恢复BitmapMesh为201帧、P50约53ms、P90约61ms。两窗口均被系统全部判慢，主机及另一设备负载未严格控制，不作倍数或真机帧率结论。恢复路径也未获得性能验收。决定撤回大量逐三角绘制调用，失败源码及首版诊断放入忽略的build目录；当前仍使用旧规则网格/现代分区两种细分策略、同一解析几何。


最终Debug与库Release构建通过（2秒），仅安装5556/5558，未操作用户5554，未新增或运行仓库自动化测试套件。内置iBook继续回退，宿主候选未获认可；兼容路径画质/性能、完整斜拖/收尾、真机和完整需求仍保留。历史发布包/JVM清单未更新。

## 79. 静止拖动的绘制调度（2026-10-08）

Dragging从每个显示帧重复计算改为完整触点变化时更新，并按显示帧合并；初始帧、定时收尾、取消和原有提交政策保留。PageTurnGesture承担conflated输入通知，终止/onDraw异常唤醒管理器；实际渲染异常恢复仍未注入验收。公开签名不变，update调用频率与host.requestRedraw只绘制当前状态的约定同步到文档。


Debug与库Release构建通过（6秒），仅操作5556/5558，未操作用户5554，未新增或运行仓库自动化测试套件。iBook仍保持回退、候选未获认可，移动性能与完整视觉验收、异常注入、真机及历史发布包/JVM清单仍待完成。

## 80. 旧路径性能与嵌套SavedState（2026-10-08）

软件drawVertices合成虽在补足旧JNI要求的颜色数组容量后能绘制同一分区，却增加第三张预算租借、降低捕获分辨率并加重逐帧位图上传；独立API28斜拖P50约81ms，高于原路径约48ms。该合成路径撤回，不以共用几何或一张代表画面作为兼容绘制完成证据。防护颜色容量保留，不改变有效顶点/公式。

候选旧路径仅在ny严格为0时删除纵向冗余网格行，非零斜拖不改变细分。水平解析映射沿y仿射，API28一个相同持页位置的页面ROI像素差0；默认每面三角形6144→128，实际窗口仍全部判慢，不能宣称流畅。最终APK两版本各48条默认/自定义×LTR/RTL×三高度页码记录0不符，不等于全部画面或连续性验收。


最后Debug与库Release构建通过（2秒），仅安装5556/5558，未操作用户5554，未新增/运行仓库自动化测试套件。内置iBook保持回退，候选仍未获认可；旧路径性能/画质、整体视觉/连续性、真机与最低API重建还未完成。历史发布包/JVM清单未刷新。

## 81. iBook候选的实际形态评审产物（2026-10-08）


已请求用户指出当前纸面形态方向是否符合预期及最明显的视觉问题，尚无形态认可结论。此前回退请求继续生效，候选仍仅Demo启用。没有实现变化/新构建/安装/仓库自动化测试，未操作用户5554。固定装订边与解析C0仍属候选模型证据，内置回退版的相关缺陷仍未完成，完整目标及发布包/JVM清单不作完成声明。

## 82. Java自定义接入与Release产物一致性（2026-10-08）

ReaderPageDecorationAdapter补足Java不可直接继承Kotlin接口默认值的接入成本，保留原接口和生命周期。实际Java工厂创建普通TextView/Button装饰并清理监听器；Google CurlOptions和控制点SAM由JavaReaderActivity使用，编译通过。API36 LTR一条实际路线观察绑定页动作到达宿主、页眉1→2→1及短拖曲面/复位；不外推全部Java客户端、Android版本、参数或视觉质量。


Debug/Java及本地发布包构建通过，状态栏修改后Debug再构建3秒。仅操作独立5556，未操作用户5554，未新增/运行仓库自动化测试套件。内置iBook回退继续生效，候选未获视觉认可；完整效果质量、连续性、旧硬件性能和最低API/真机仍待完成，不缩小目标或标记完成。

## 83. 当前发布 AAR 的独立压缩宿主消费（2026-10-09）

独立Gradle宿主从Maven解析的AAR哈希与当前Release清单一致，Java装饰适配器/动作/Google控制点实际编译，Debug与R8 Release构建通过。临时调试密钥签名副本在独立API36显示XML/Java页面，页脚回调不翻页、下一页更新页眉，未添加整库keep；这是窄范围压缩运行证据，不代表全接口或二进制兼容验收。


库/iBook几何未改，内置回退仍有效，候选仍待视觉反馈；完整效果质量/连续性、旧硬件性能、恢复锚点与最低API/真机工作未完成。

## 84. iBook兼容绘制路径与归位证据（2026-10-09）


回退版默认控制点近右缘的Double公式代入给出数百像素变形，0.001下限仅压缩过渡区，不证明视觉连续。此数值是说明性分析，不是实际Float采样或设备像素验证。内置iBook装订/归位仍未修复，候选仅Demo，整体目标未完成。仅输入5558，未操作5554，未新增/运行仓库自动化测试。

## 85. iBook候选弧长与控制点关系（2026-10-09）


Debug构建及独立API28/API36各48条页码记录完成，实际水平/斜拖持页和新双方向录像抽样/末帧查看。此证据支持候选几何变化，没有证明全部视觉、取消/生命周期或性能。候选仍仅Demo，内置iBook回退，公共AAR/清单不变；原始整体目标未完成。未输入5554、未新增/运行仓库自动化测试。

## 86. 公共iBook实现、重复职责清理与实际绘制（2026-10-09）

库内CornerCurlEffect采用IBookCurlGeometry/IBookCurlMesh处理连续斜圆柱、装订边支撑和弧长补偿，资源/时钟/提交维持既有职责。Demo只委托同一公开renderer，删除重复模型和租借实现；没有新增仿真家族或公共控制器。04/08/09/10说明当前库实现，早期候选/回退状态保留为历史。


库几何已有实现，不等于用户形态认可、旧系统性能或所有效果/场景验收。只操作独立5556/5558，未新增/运行仓库自动化测试；整体目标仍未完成。

## 87. 全公开模式代表画面与端点阴影（2026-10-09）


上述结果支持默认参数单视口代表状态的图层、方向与取消/提交检查，不能代替所有动画帧、反向甩动、性能、生命周期或最低API真机验证。用户确认与5554同时交互，该设备的不受控页码不作程序故障结论；本轮只输入5556/5558，未新增/运行仓库自动化测试。iBook整体形态尚未获用户认可，目标没有完成。
## 88. iBook归位像素与旧系统明暗覆盖（2026-10-09）

在连续几何基础上发现并修正API28规则网格的近端点暗带：圆柱趋零时，点采样光照仍扩散至整格。旧系统绘制按材料带宽/单元法向尺度估计覆盖并衰减；没有增加平铺阈值或改变公开控制点。


这是实际残影修复，不是全倾角/网格/半径、动态流畅度或用户形态认可的证明。只输入5556/5558，未新增/运行仓库自动化测试；整体目标仍未完成。
## 89. 页面捕获共享与性能回归边界（2026-10-09）

Google列复用的几何采样与代表持页一致，但单台API28整体帧时间没有改善，不能宣称性能已解决。双页捕获提取为内部PageTurnSnapshots，资源预算和提交仍归Widget管理器；仿真与平移职责没有变成新的公共控制器。

旧系统平移在预算足够时使用原分辨率双快照，API29以上沿原View绘制，预算不足使用原页面。选择来自实际现代快照回归；最终API36平移P50均17ms。API28初版缓存窗口约61ms且慢帧100%，仍不满足流畅目标。最终两设备六模式代表持页及120条页码记录完成，像素比较已明确现代一致与旧系统平移栅格化差异，未以页码通过替代视觉验收。

当前AAR 7f5ba9de…a9742c5、521 class/217所选公开声明，构建/本地打包完成。全部阶段证据和范围见10第30/31节；当前产物独立宿主R8、全部资源失败注入、真机/最低API及用户认可仍未完成。只输入独立5556/5558，未新增/运行仓库自动化测试套件，完整目标保持进行中。


## 90. 当前AAR的Maven/R8自定义入口证据（2026-10-09）


该证据只覆盖示例的一条接入路线，不替代旧系统性能、任意装饰View生命周期、全部Android/API/参数、旋转/进程死亡或iBook用户认可。库公开API/当前AAR未改；未新增/运行仓库自动化测试，完整目标仍进行中。

## 91. iBook自定义控制点阴影边界（2026-10-09）

控制点API允许连续重映射几何行程；旧iBook阴影使用导航sheetProgress，因此合法提前离页策略留下有限暗带，直到事务平面分支才消失。现使用geometry.travel并在几何端点显式归零，实际Maven Debug/API28/API36×LTR/RTL对照支持修复：最大通道差API36→0、API28 27→1，取消仍保持原页。没有增加公共控制器/类型或第三家族。


## 92. 组合页装饰的回调批次（2026-10-09）

组合模板已经按绑定代次停止失效的后续槽回调，撤销先于unbind清理；该状态仅归模板内部，不替代Widget会话、导航或时间管理。普通ViewGroup准备/反复绑定/方向/预算及按钮滑块路线在API28/API36当前bc70产物完成，各60条六模式+72条预算控件记录0不符，完整代表图已查看。



















# 2026-10-10：回退状态


## 2026-10-10：形状候选的证据边界



## 2026-10-10：锥面进入内置iBook




## 2026-10-12：重新调研3D书页建模




## 2026-10-12：当前卷页版本

PageTurnEffects.pageCurl() / page_curl 已替换旧实验模式。采用当前网页的受控圆柱、装订约束和文字材料映射，Canvas窄带渲染适配API21及以上。前后翻页与松手提交/复位复用现有事务时钟、输入策略和快照预算。旧仿真研究文档、脚本与DevelopablePaper实验类已移除。完整设计及观察记录见[当前卷页文档](23_SHADER_PAGE_TURN_ROUTE.md)。




