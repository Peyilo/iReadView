# iReadView Widget API 实现方案

> 更新：2026-10-07。本文保留完整目标方案及按时间追加的设计调整；第 2/3 节按当前源码列出职责归属与实现路径，其余早期阶段描述不代表当前完成状态。当前接入见 [08_HOST_INTEGRATION_GUIDE.md](08_HOST_INTEGRATION_GUIDE.md)，实现摘要见 [06_IMPLEMENTATION_PROGRESS.md](06_IMPLEMENTATION_PROGRESS.md)，需求与缺失验收证据见 [07_REQUIREMENT_IMPLEMENTATION_AUDIT.md](07_REQUIREMENT_IMPLEMENTATION_AUDIT.md)。
>
> 目标：为每类 API 指定承担实现的对象、Android 技术方案、异步流程、资源归属及失败处理，使 Widget 可以独立嵌入宿主。

## 1. 模块、包与依赖

保持 `:app` 与 `:app:ireadview`。所有可复用阅读行为在 Library 中；Demo 只导入 Widget、API、内置来源/效果工厂与公开 SPI。AAR 不包含 Demo Activity、文件选择 UI、业务网络协议或数据库。

```text
org.peyilo.ireadview/
├─ widget/                     public：BaseReadView、BookReadView、ReaderView
├─ api/
│  ├─ model/                   public：书籍/正文/位置/标注/快照
│  ├─ source/                  public：BookSource、ReaderInput、来源配置
│  ├─ style/                   public：ReaderStyle、字体/背景/选区样式
│  ├─ state/                   public：状态、结果、错误、能力、选项
│  ├─ event/                   public：监听器、事件、ReaderActionHandler
│  ├─ input/                   public：输入配置、触摸轴
│  └─ effect/                  public：效果接口/工厂、PageSurface与预算
├─ spi/
│  ├─ presentation/            public：ReadContentAdapter、BookPresentation、Host、布局查询
│  ├─ page/                    public：页面/占位 factory
│  ├─ selection/               public：选区工具栏 factory
│  └─ content/                 public：块布局 provider、绘制/选区几何
├─ source/                     public：MemoryBookSource、TextBookSource、EpubTextBookSource
└─ internal/
   ├─ container/               通用位置存储、视口、导航事务与输入
   ├─ book/                    书籍会话、加载调度、目录展平、版本校验
   ├─ presentation/paged/      默认分页呈现
   ├─ presentation/scroll/     默认滚动呈现
   ├─ layout/                  文本布局、分页、锚点/页码映射
   ├─ render/                  正文/效果层、页面池、快照资源
   ├─ annotation/              标注快照与验证
   ├─ selection/               字符边界、原文选择及工具栏
   ├─ source/                  内置来源解析、快照和资源租约
   ├─ style/                   样式解码、字体、背景和间距
   ├─ effect/                  效果管理、手势与绘制
   ├─ state/                   事件发布与轻量 SavedState
   └─ cache/                   有界内容、布局及位图缓存
```

这是 API 设计后的具体包方案，细化并调整 02 文档的职责目录。内部内容/页对象标记 Kotlin `internal`；公开 SPI 输出其独立契约，避免公开签名出现 internal 类型。Kotlin 的 internal 不是 Java 字节码的强隔离，Demo 同时遵守公开包导入约定；发布前检查 public/protected 签名及 JVM 暴露面。

当前运行依赖（版本由 gradle/libs.versions.toml 管理）：

| 依赖 | 用途及公开方式 |
|---|---|
| Android View / FrameLayout / Canvas / StaticLayout | Widget、文本布局及效果绘制；核心能力 |
| AndroidX Core | 无障碍代理、兼容辅助 API；优先 implementation，公开签名需要时才 api |
| AndroidX CustomView 1.2.0 | ExploreByTouchHelper 管理 Canvas 正文虚拟节点/焦点；仅 implementation，不出现在公共 SPI 类型中 |
| kotlinx-coroutines-android | suspend 输入、IO/CPU 调度、可取消任务；公开方法不暴露 Job/Scope/Flow |
| AndroidX RecyclerView | 连续滚动内容复用；implementation，公开 API 不出现 RecyclerView |
| Android XmlPullParser / java.util.zip | 轻量 EPUB 文本与目录提取；未引入额外解析运行依赖 |

不要求宿主 AppCompatActivity、LifecycleOwner、ViewModel、Compose 或数据库。当前工程 minSdk 21、compileSdk 36；基础实现已添加 coroutines 1.9.0，Core 使用 implementation，并移除未使用的 AppCompat 库依赖。RecyclerView 1.4.0 与 CustomView 1.2.0 已接入，全部当前运行依赖为 implementation；没有向宿主暴露 RecyclerView/Job/Scope/Flow。原始 AAR 不打包依赖；本地 Maven 仓库包提供依赖元数据，独立宿主已有 Debug/压缩 Release 构建证据，接入与运行限制详见 08。

## 2. 当前职责归属

下表按当前生产源码记录实际类和组合关系。单次位置提交保留在 NavigationController；没有为了匹配早期方案而新增只转发调用的包装层。

| 实际对象/方法 | 所有者 | 当前职责 |
|---|---|---|
| `NavigationController<L>` | BaseReadView | 唯一已提交位置、准备/显示/结算、取消与替代；合并早期 LocationStore |
| `BaseReadView` 输入与视口方法、`PageTurnGesture` | BaseReadView | View 生命周期、尺寸变化、手势/点击/键盘路由；未建立独立 InputRouter/ViewportTracker |
| `PageTurnEffectManager` | BaseReadView | 效果工厂、兼容、帧更新、取消和有界快照；对应早期 EffectManager |
| `ReaderCoordinator` | BookReadView | 来源会话、章节/布局状态、工作代次、布局请求合并和缓存，不另存已提交位置 |
| `ReaderCoordinator.preloadNear` | ReaderCoordinator | 邻章预载窗口及任务归属，未独立成 PreloadScheduler |
| `ChapterRepository` | ReaderCoordinator | 工作线程来源/指纹/估重、共享加载、消费者计数、重试与内容缓存 |
| `TextBlockLayoutProvider`、`PaginationEngine`、padding 变换 | ReaderCoordinator | 内置文本测量、自定义 provider 调度、同源分页/连续片段与位置映射；无独立 TextLayoutEngine |
| `BookPageIndexBuilder` | ReaderCoordinator | 显式全书索引工作、取消、版本核对与前缀页数 |
| `AnnotationStore` | BookReadView | 宿主标注快照、ID/版本/范围验证、有效性与变更 |
| `TextSelectionResolver`、`TextCharacterBoundaries`、`SelectionToolbarController` | Widget及呈现协作 | 原文选择/边界和工具栏绑定；手柄/边缘导航在正文及呈现中，不另设总括 SelectionController |
| `PagedBookPresentation`、`ScrollingBookPresentation` | BookReadView | 模式各自准备/显示/回滚、模板和可见位置；沿共同导航提交 |
| `ReaderBodyView`、正文辅助节点/遍历工具 | 呈现 | 同布局绘制/高亮/命中、手柄与辅助节点，来源目标在工作阶段解析 |
| `ReaderPlaceholderController`、`ReaderPageClock`、页面归属工具 | Widget及呈现 | 占位绑定/重试、分钟更新、页面/子 View 归属与清理 |
| Widget 私有 `dispatch`/`publishState` | BaseReadView/BookReadView | 主线程监听快照及逐监听重入检查；未独立成 ReaderEventDispatcher |
| `ByteBudgetCache`、`RetainedSize` | 来源仓库/协调器/效果 | 已捕获权重与缓存预算；不等同整进程内存限制 |
| 内置来源解析器、`OwnedSourceFile` | Text/Epub来源 | 解析与临时资源租约，由宿主关闭来源；Widget不接管来源文件或业务持久化 |

生命周期/会话代次与导航事务归属独立：普通导航不使同版式缓存失效；换书、内容更新、尺寸/样式变化按既有规则失效。实现中的局部序号用于外部回调重入与任务归属，不进入公共位置或状态模型。

上述职责归属不证明相应运行行为通过。第 3 节起保留目标流程和历史阶段，遇到尚未实现的旧描述先对照 06 当前摘要及后续调整；增强缺口及全部原始验收范围仍以 07 为准。

## 3. API 实现对应表

| API 面 | 入口 | 实现方法 |
|---|---|---|
| 创建 Widget | XML/ReaderView 构造函数 | obtainStyledAttributes 解析样式；首次使用装配内层布局；构造时不调用子类钩子 |
| 打开/关闭 | openBook、closeBook、release | Coordinator 建立/结束会话；Base 容器重置导航和位置；release 再清监听器与来源引用 |
| 内容输入 | BookSource / MemoryBookSource | ChapterRepository 从 IO 调度读取；校验身份/代次、准备指纹与估重；TextContentBlock 统一原文契约 |
| 更新/重试 | reloadBook、invalidateChapter、retryChapter | 精确失效目录/章节/派生布局；同 ID 锚点恢复；只重试匹配当前代次请求 |
| 页/章节导航 | next/previous、goTo* | 统一 NavigationController；呈现负责计算目标及加载/布局，容器负责提交 |
| 全书页 | buildBookPageIndex、goToGlobalPage | 每章页数 + 前缀和；二分定位章节再跳章内页；未知时返回明确拒绝 |
| 阅读模式 | setReadingMode | 保存语义锚点，准备新呈现后切换；不通过 effect ID 切模式 |
| 外观 | applyStyle / factory setter | BookReadView 比较样式：仅高亮/选区差异免重排，其余保守失效布局并按锚点恢复；没有独立 StyleDiff |
| 标注输入/输出 | replace/export/add/update/remove | AnnotationStore 复制数据、原子更新、发增删改事件；不自动持久化 |
| 文本选择 | get/clear/highlightSelection | TextSelectionResolver/字符边界与正文几何；SelectionToolbarController 绑定动作，呈现处理手柄/边缘移动；没有总括 SelectionController |
| 翻页效果 | setPageTurnEffect / options | PageTurnEffectManager 管理 factory 实例；容器统一手势阈值和动画时钟 |
| 自定义页面 | ReaderPageFactory | 建独立 root/bodyHost；库测量真实正文区，绑定/解绑时不泄漏宿主 View |
| 自定义呈现 | BookPresentation | 使用受控 BookPresentationHost 获取章节/布局；prepare/show 配合导航事务 |
| 快照恢复 | export/restoreSnapshot / SavedState | 保存轻量位置与内置标识；重新绑定来源后异步恢复，来源与完整标注另由宿主管理 |
| Java 异步调用 | callback 重载 | 对同一 suspend 实现做桥接；请求句柄取消其 Job；终态主线程恰好回调一次 |
| 无障碍/输入 | View 动作、ReaderActionHandler | 虚拟文本节点/动作进入同一导航入口；目录/设置/退出由宿主处理 |

## 4. 线程、作用域与版本规则

Widget 自建 `SupervisorJob` 和 Main 调度作用域，来源读取使用 IO，正文布局使用 Default。子任务失败不能取消整个 Widget；领域错误由协调器捕获。CPU 任务只处理不可变文本、样式副本与独立 TextPaint，不访问 View 或共享可变 Drawable。

内部每个结果至少带适用范围中的以下键：

```text
viewLifetimeEpoch   attach/detach/release 后识别旧资源任务
bookSessionId       open/close/换书后识别旧会话
catalogGeneration   目录更新
chapterGeneration   章节失效/重试更新
contentRevision     来源提供或内置来源生成的内容版本
layoutGeneration    字体、边距、可用尺寸、正文模板测量变化
navigationToken     本次导航所有权，不作为缓存键
```

任务取消是资源优化，结果入库/显示前的代次校验是正确性保证。某些来源忽略取消、平台布局调用无法中途停止时，迟到结果仍必须被丢弃。

请求句柄另外记录终止原因：用户取消/close/detach/release 完成为取消，更新的同类命令替代旧命令完成为 Superseded。先确定请求终态再终止底层任务，callback 桥接与 suspend 入口遵守同一规则。

LayoutKey 使用测量后的正文宽高 px、density/fontScale、字体身份、文字排版参数、布局方向、内容版本及块 provider 版本。背景颜色、时间、纯文字颜色变化不影响断行；默认将它们从布局键中排除。自定义块 provider 须声明哪些样式影响其几何，无法区分时保守重排。

Widget 无有效视口时保留加载状态；禁止用 0 尺寸生成“0 页”。初次尚未 attach 的 open 可加载目录/内容并等待布局；已发生 detach 的在途请求被取消，重新 attach 不重复完成旧请求。

## 5. 打开、加载、目录更新

openBook 流程：

1. 主线程取消旧导航/会话，清选区、替换标注快照，增加 bookSessionId；当前位置暂为空。
2. IO 读取目录；校验来源 bookId、稳定章节 ID 和目录结构，展平章节并生成 ID → 顺序查表。
3. 空目录返回 Empty 并显示空目录状态；正常目录按初始位置策略解析目标章节。
4. Repository 加载目标章，校验 chapterId 和正文块；错误显示当前章可重试占位。
5. 主线程测量页面模板，取得实际正文视口；后台布局与分页，解析锚点或章节页。
6. 当前目标通过统一导航提交为可读位置；发布状态及 Open/Restore 原因事件，完成 open。
7. 可读后调度邻章预加载；预加载失败仅改变对应章节状态。

同章同代次有一个共享加载 Deferred；单个调用方取消不取消仍有其他使用者的共享请求。章节失效、会话结束或已无消费者且不在预加载窗口时取消底层任务。为可见请求保留优先通道，避免全书索引/预加载占满并发槽位。

`LoadingOptions` 控制前/后预加载数量、最大并发、内容/布局/效果位图字节预算；首版具体默认值在实现中选取并写入 API KDoc，性能验收后调整。目标章节及当前页资源优先保留，离开窗口的请求取消或降低优先级；禁止缓存随着读过的章节累计。

invalidateChapter 先使旧结果不可提交，再移除该章内容/布局/全书索引项。当前章更新时保留最后已提交语义位置，显示更新状态并重新恢复；高亮先变 PendingValidation。reloadBook 按稳定 ID 重新排序和复用仍有效内容，删除/替换章节不得继续显示旧数据。

目录加载失败与章节加载失败分开建模。手动 retry 可再次尝试；Widget 不使用无限自动重试。网络退避与持久缓存由 BookSource 实现方决定。

## 6. 文本排版与分页

### 6.1 使用 Android 文本布局

默认文本/标题块使用独立 `TextPaint` 与 `StaticLayout`，由平台处理换行、字体 shaping、双向文本和字间距；Canvas 根据同一布局绘制。避免用逐字符宽度累加自建中英文断行规则。

API 23 及以上使用 `StaticLayout.Builder`；API 21/22 通过兼容工厂调用可用的旧构造方法。Builder 在 API 23 加入，不能直接作为 minSdk 21 的无条件实现。[StaticLayout.Builder 官方参考](https://developer.android.com/reference/android/text/StaticLayout.Builder.html?authuser=2)、[StaticLayout 官方参考](https://developer.android.com/reference/android/text/StaticLayout.html)

首行缩进采用布局缩进/段落 span 等不改原文的方式，续行及续页不重复缩进；标题使用独立对齐与样式。标题、正文默认不重复插入章名。跨平台版本高级断行参数通过兼容层映射；不支持的参数使用明确默认，不能承诺不同系统版本页界完全一致。

`TextLayoutEngine` 为块构建高度、行位置、UTF-16 字符范围及字素边界索引。`PaginationEngine` 按可用高度装配连续行/块片段，记录每页首末锚点、绘制偏移及字符命中信息；段距、标题间距与 padding 只在真实块边界生效，跨页片段不重复加入。

普通段落之间做协程取消检查。单次 StaticLayout 构建不提供协程中断；极端单段输入的耗时需单独测量，过期结果必须丢弃。若后续增加长段分段布局，须先证明边界 shaping、双向文本、缩进和命中一致性，不通过随意按固定字符截断来宣称问题已解决。

### 6.2 页与锚点映射

页范围按原文顺序半开区间组织；锚点恰好处于新页起点时归新页。章末 offset 可以作为高亮 endExclusive，但阅读定位规范到最后可显示的内容位置。未排版章不输出假页数。

空章节生成一个空状态页；空目录没有页。过高非文本块由 provider 的片段策略处理；不支持分割时明确报告布局/能力限制，不产生高度为 0 的无限分页循环。

字体/正文高度变化前捕获当前语义锚点，重排后二分页起点映射恢复。屏幕变化不只监听控件外尺寸：header/footer 测量、正文区、density、fontScale、布局方向均参与视口判定。相同尺寸/布局键不重复启动计算。

### 6.3 页面测量

ReaderPageTemplate 返回 root 与 bodyHost，库把 ReaderBodyView 或滚动列表放入 bodyHost。先绑定影响几何的标题/页眉/页脚，再测量并取得正文区域；正文布局完成后绑定页码。

默认页眉/页脚给数字和时间预留稳定高度。自定义页码文本若反复影响正文尺寸，检测重复布局键并报告模板布局不稳定，避免无限“页数改变 → 页脚变高 → 再分页”循环。

page padding、viewport padding、body container/content padding 和块 padding 分层扣减，不能对同一层重复扣除。无正文可用区域时报告 InvalidViewport，保留明确占位，不开启翻页动画。

## 7. 导航事务与动画

```text
Idle
 └─ Preparing                 校验边界、加载/布局、准备候选
     ├─ ReadyForDrag          当前/目标画面可用，手势跟随
     │   ├─ SettlingCommit    结算到目标
     │   └─ SettlingCancel    结算回原页
     └─ SettlingCommit        程序导航可直接结算

任何未提交阶段 → Cancelled / Superseded / Failed → Idle
SettlingCommit → 最终呈现就绪 → 单次 Commit → Idle
```

`PreparedRead` 持有已校验目标位置、generation 和可选翻页画面。NavigationController替换任务前先保留本次token，旧任务取消及Idle通知后复核，避免覆盖通知中发起的新导航。Preparing通知后复核再调用prepare；prepare返回时先登记Ready候选的finally清理责任，再对所有结果检查活动/token/内容代次，状态选择回调后也复核再通知。Preparing 期间旧位置保持可查询。目标未加载时显示目标加载状态；手势不能把加载占位当成已读下一页。取消手势不自动在迟到内容返回后再执行翻页。

EffectManager 使用 View 动画帧调度驱动统一 progress，手指拖动和程序结算使用相同帧输入。`ViewConfiguration` 区分点击/拖动，`VelocityTracker` 辅助速度判定；normalized 阈值及 dp/s 速度统一转换。动画中不执行来源 IO 或大规模布局。


横向手势由 BaseReadView 持有输入与任务，PageTurnGesture 是不含页面/位置的主线程邮箱；NavigationController 在候选有效后进入 Dragging，Manager 按显示帧读进度并驱动同一效果实例。松手比例默认 0.33、速度默认 600dp/s，快反向释放优先复位；系统 slop 与最大速度来自 ViewConfiguration。[VelocityTracker](https://developer.android.com/reference/android/view/VelocityTracker) 用 computeCurrentVelocity(1000) 得到 px/s，统一乘 density 转换阈值。复位以内部 NavigationReset 返回 Unchanged，候选正常 dispose，唯一提交源不变化。准备期间取消直接取消任务；none/减少动画/不兼容回退也必须等待松手，效果失败先清理租借再等决定。输入代次失效、效果/选项变更及公开取消会停止任务和速度采样。

动画结算后，Adapter 的 `show(candidate)` 在主线程发起最终呈现，必要时挂起等待下一次 View 布局，再返回实际位置。容器在同一主线程连续段中校验 token/代次并更新 LocationStore，之后发布一次位置事件。分页效果层保持最终画面直到正文 View 就绪，再撤下，避免中间闪白。

滚动列表的定位通常在随后布局周期生效，不能假定调用滚动方法后立即读到目标可见文本。[LinearLayoutManager 官方参考](https://developer.android.google.cn/reference/androidx/recyclerview/widget/LinearLayoutManager)

show 被取消、目标过期或最终显示失败时，呈现恢复最后已提交内容；尚未提交的目标不可变成对外进度。回滚动作也检查代次，不得恢复前一本书的页面。候选 dispose 一次，转移给当前页的资源由当前呈现接管。

强制导航、换效果、换书、换模式、内容更新、尺寸变化及 detach 均中止未提交事务。普通快速调用返回 Busy；不把连续点击堆积成未来自动执行的导航。`ACTION_CANCEL` 回原页；首尾边界不开始效果、不产生位置事件。

自定义效果异常时 Manager 结束其生命周期，使用无动画最终显示经过验证的候选；候选也无效时保留原位置。效果异常不绕过导航校验。finish/abort/detach 的清理异常已通过统一 cleanup 边界隔离：先结束终止/解绑所有权，再调用插件，租借由 Manager 最终归还。finish/abort 失败淘汰实例并回退，detach 失败继续完成替换/解绑。既有 onError 输出 EFFECT 阶段及 EffectFinishFailed/EffectAbortFailed/EffectDetachFailed，保留 cause；消息包含效果 ID/可用 token，非章节重试错误。主线程排队报告防止监听器重入清理，旧代次不向新会话广播；BookReadView 保存最近错误而不改变会话阶段，release 期间只保留本地日志。设备上的异常插件行为仍待验证。

## 8. 默认分页与连续滚动

输入策略已统一为 ReaderInputOptions，手势开关从 PageTurnOptions 迁出，避免同一能力有两份配置。BaseReadView 中的输入路由支持横向/垂直轴、默认边缘点击、当前焦点硬件键盘和可选音量键；仍用 UserNavigation 进入同一控制器。纵向无动画也等待松手决定。宿主 tap listener 先消费，中间交给普通 click；子控件/选区优先。PageTurnOptions.minimumTurnIntervalMs 与 NavigationOptions.respectInterval 实现明确的 Throttled 拒绝；force 保留边界校验，跳过间隔并替代请求。重复按键不形成连续导航队列。Demo 输入设置覆盖四个开关、方向及 0/250ms 间隔。尚未通过设备键盘/焦点和嵌套触摸验收。

### 8.1 分页呈现

PagedBookPresentation 以有限页面池保留当前显示页与可用邻页。页面池大小受页面/位图预算约束，不把每个章节页都创建为 View。导航准备时绑定目标页面、完成测量并借出只读 PageSurface；提交后交换页面职责。

文本块原始内容、块布局、分页索引和页面 View 分别缓存。页码列表是轻量派生数据，View 与效果快照不进入持久模型。覆盖/滑动效果用 Canvas 平移/裁剪，卷页用 Path、几何变换和必要的有限快照；每帧避免创建大对象/位图。

### 8.2 滚动呈现

当前实现已采用下述 RecyclerView 方案，并通过 continuousFragments 与分页共享同一次文字测量。连续片段携带块后的间距；列表高度取整与偏移查询采用一致的高度前缀。显示窗口暂限三个章，前插/追加时保留可见章及正文偏移；章节加载边界失败可点击重试。程序翻页计算正文视口位移后准备隐藏列表，最终显示继续走统一事务。手柄边缘自动滚动已实现，按帧推进并限制同章/固定端点，拖动期间暂停窗口拼接。当前仅有 Debug 构建记录；显示布局字节上限和设备性能仍待完善。

ScrollingBookPresentation 在固定页眉/页脚之间使用 RecyclerView + LinearLayoutManager。item 是有界的章节内容片段/块布局片段，以及章节加载、失败、空状态边界；不把整章超长 TextView 或完整滚动流一次性放进 ScrollView。

列表维护 book/chapter/block ID 到片段的映射，item identity 与文字位置独立。长块可按已计算的完整行组分片，复用原块布局与 offset；这种视图分片不重新切断源文本或改变断行。

向前添加邻章片段前捕获当前可见语义锚点与视口内相对偏移，列表更新后用新 item 映射恢复，再校验主锚点，避免 prepending 时跳动。邻章失败以对应边界 retry item 展示；到达全书边界不继续请求。

可见位置由第一条正文可见文本行计算；章间 loading item 不产生虚构文字锚点。按显示帧合并滚动回调，空闲时刷新最终值。程序导航和模式切换期间暂时抑制中间 Scroll 事件，最终由事务提交。

滚动 next/previous 以一个正文视口为目标位移，prepare 先确保将要进入的内容可用；目标被书首/书尾夹住时返回实际终点，已经在终点则 AtBoundary。

### 8.3 模式切换

当前已落地 supportedModes/createPresentation(mode, host)、稳定 ReadingMode、suspend/callback 切换、状态/事件以及 XML/快照/控件状态保存。模式候选包装器管理新呈现的准备与失败释放，显示成功才替换旧呈现；没有额外位置存储。旧呈现清理异常作为独立错误报告，避免已完成新内容显示后返回一次虚假的导航失败。

保存当前锚点 → 创建/准备新呈现与布局 → 定位同一文本 → 事务显示并提交 ModeChange → 解绑旧呈现。准备失败继续保留旧模式，并输出失败结果。模式切换重用内容仓库、样式与标注，不迁移标注数据。

当前模式不支持某个效果时返回 DeferredUntilPaged；从滚动回分页时安装已选分页效果。从滚动恢复的章节由实际可见锚点决定。

## 9. 选择、高亮与无障碍

长按使用与正文绘制相同的 Layout：通过垂直行索引和水平字符 offset 命中，然后归一到合法字素边界。Android Layout 提供行/字符命中及选区路径能力，可以用作此处的几何基础。[Layout 官方参考](https://developer.android.com/reference/android/text/Layout)

选择控制器用章节块顺序比较 start/end，逐块提取原文，并为跨页/跨 item 的区间生成各片段选区矩形。手柄拖动优先于翻页手势；滚动边缘自动移动时仍限制本章。取消或章节内容更新清选区；字体重排可通过有效锚点恢复选区几何。

高亮绘制在文字下方，选择层在高亮之上；复制/高亮工具栏由默认实现或 SelectionToolbarFactory 提供。默认高亮创建及点击删除使用 Widget 标注 API，从而统一验证、ID 及变更事件。

导入标注先校验结构；章节内容就绪后逐条检验版本、offset、顺序及 quote。内容修订后，只在稳定块定位和文本校验明确成功时重用范围；无法证明或出现重复文本歧义时 Invalid，不用附近相似文字冒充正确高亮。失效状态不擅自改写宿主存储。

Canvas 正文需要额外提供无障碍语义，不能只设置整个 View 的 contentDescription。ReaderBodyView 通过无障碍虚拟节点暴露可见段落/片段及文本，提供滚动/上一页/下一页和选择相关动作；目录、设置与退出动作交 ReaderActionHandler。屏幕阅读器使用文本快照，不朗读隐藏邻页或重复整个章节。

当前已接入 BodyAccessibilityHelper，复用 BlockLayoutFragment 的坐标与原文行范围，默认文本/标题支持 accessibilityText、accessibilityHeading 与 accessibilityTextForVerticalRange。滚动大段落只输出当前垂直可见行，不缓存第二份全文；自定义块默认 null，可提供自己的语义。虚拟节点只列出可见且非空片段；绑定换内容前清焦点、使用新 ID，旧服务请求返回禁用不可见节点。正文将 hover/key/focus 转发给 ExploreByTouchHelper，祖先滚动按帧刷新几何，解绑移除观察器/回调。自定义文本语义异常在本次绑定内只记录一次并跳过节点。

BaseReadView 上的前后滚动无障碍动作已进入同一 UserNavigation，忙/禁用/选区接管时不接受，提交后报告显示变化；自然滚动沿用列表事件，不重复发根滚动事件。泛型容器已增加只读方向能力查询；内置分页按真实页数和目录判断，滚动按实际列表可滚动范围及窗口邻章判断。已知边界不声明动作，Unknown 仍由准备最终校验。虚拟正文已支持可见文本选择和指定范围，根节点复用复制/高亮/清除选区动作，节点内字符/词/段落移动已接入；ReaderActionHandler 已接入目录/设置/退出，自定义页面与无障碍动作共用容器分发。TalkBack、Switch Access、标题顺序和焦点保持仍需设备验收，当前只有 Debug 构建记录。[ExploreByTouchHelper](https://developer.android.com/reference/androidx/customview/widget/ExploreByTouchHelper)、[CustomView 版本](https://developer.android.com/jetpack/androidx/releases/customview)

XML/宿主设置的字体缩放按 sp 生效；减少动画配置使容器直接无动画提交。正文控件 touch target、对比度和服务交互需在实际 Widget 实现阶段检查。

宿主动作实现归 BaseReadView 所有：主线程同步分发、禁用/释放拒绝、异常返回 false、release 清除引用；与导航事务和忙碌状态分开。BookPresentationHost 提供带位置/代次的请求 SPI，ReaderPageContext 的默认闭包保持旧工厂接入，内置分页/滚动再校验活动实例，防止隐藏候选和旧页触发。无障碍自定义动作使用资源 ID 和本地化标签，注册时刷新、执行时再校验 handler。Demo 复用公开入口处理目录、设置和退出。未新增或运行测试，仅 Debug 构建通过。

选区无障碍操作已沿用原文几何与同一动作实现：片段可见文本与 source range 同源，虚拟节点长点击选择当前可见行，ACTION_SET_SELECTION 支持局部 UTF-16 范围并拒绝字符边界切断。活动呈现/代次/忙碌校验在执行时生效，RecyclerView 回收清理闭包。BookReadView.getSelectionActions 暴露带修订号/代次的操作，根节点复制/默认高亮/清除与工具栏共用；查询捕获动作，选区改变后缓存清空。剪贴板异常与 ID factory 重入得到明确失败/拒绝结果。节点内字符/词/段落移动已实现，跨节点扩展仍未实现；仅 Debug 构建通过，无设备服务验收。

## 10. 内置来源与内容更新

MemoryBookSource 是直接提供规范化目录与章节的最简单接入方式，更新使用不可变快照并校验书籍身份。Host 更新来源后显式调用 invalidate/reload，使来源数据更新与显示失效有清楚的边界。

TextBookSource 使用编码感知读取，建立标题规则/章节索引，不依赖文件名作为稳定 bookId。规范化至少明确 BOM、换行和段落切分规则；章节标题前的文字保留。offset 对应解析后的固定正文文本，宿主可查询同一模型进行标注映射。

已实现 ReaderInput 的文件/资源/asset/URI/可重开流和 TextBookOptions/TextBookSource：IO 严格解码、有界读取、固定 BOM/换行规则、每行块、前言保留、空文件章节、字节 revision、有序章节/块 ID。loadBook 重新解析后原子安装，loadChapter 共享成功快照；失败/取消保留旧快照，各次流 use 关闭。默认使用内存快照，指定 indexDirectory 则逐行写入磁盘章节记录，按章读取；默认最多 32 MiB 输入、128 MiB 索引，解码及模型占用另计。自定义 ID/规范化仍未实现。Widget 不获得输入流所有权，不新增文件解析或授权分支。

EpubTextBookSource 读取容器入口、OPF spine 顺序、可用导航目录，提取正文标题/段落；只作轻量文本阅读，正文输出沿用相同模型。忽略的内容类型需产生来源诊断，不能声称完整 EPUB 展示。解析器选择、外部实体关闭、ZIP 条目路径/大小限制、异常编码及重复 ID 处理在该来源实施时具体定义。

元数据步骤已实现 internal.source.EpubXml/EpubPackageParser：namespace/token PullParser、DTD 关闭/固定 XHTML 实体、结构/字符预算；首 OPF rendition、版本 2.0/3.0、标题/manifest/spine、重复与缺失校验、非线性保留和有深度预算的 fallback。阅读顺序与导航路径不可变，结构诊断由公开来源统一转换为 EpubDiagnostic。远程资源不加载，无法获得 XHTML 的 spine 明确失败；未支持 xml:base，检测后拒绝。扫描不读取正文，避免把目录加载变成整书提取。NCX/nav 标签和正文块规范化已有内部实现；公开来源已接入，解析运行行为尚未验证。

容器前置步骤已实现 internal.source.EpubArchive：输入复制/归档 revision、条目数量/路径/重复/压缩类型/声明展开预算、mimetype/container 文件检查、相对资源引用、实际长度/CRC 校验和完整排空。TXT/EPUB 共用 OwnedSourceFile 的幂等租约与延迟删除，避免单独维护两套关闭状态机。OPF/spine 解析已接入，已有导航目录与正文 XML 提取内部实现，公开来源已有实现，Demo 尚未接入；不可把容器编译通过写成完整 EPUB 验收。

URI 访问权限由宿主授予；来源只读取获授权输入。临时索引文件归内置来源实例管理，关闭/淘汰时删除；Widget 不自动关闭可能被其他 Widget 共享的宿主 BookSource。带外部资源的来源可自行实现 AutoCloseable，由创建者管理生命周期。

TextBookSource 已实现可选磁盘章节索引与 AutoCloseable。internal.source.TextSourceSnapshot 区分内存/磁盘存储；DiskTextSnapshot 逐行扫描/规范化并建立原文块记录与章节偏移，公开模型与身份策略共用。预算分别覆盖输入和生成索引，目录安装原子化，半成品/旧快照清理不干扰成功快照；关闭与读租约协调。Demo 使用应用缓存目录，成功切书关闭旧来源，销毁时先释放 Widget 再关闭剩余来源。没有把文件资源生命周期加到泛型容器。跨进程索引复用、崩溃遗留清理和巨型单行/单章性能仍待实现/验收。

Demo 的 TXT 文件选择/编码确认、取消保持阅读、系统授权申请、打开失败重试和切回示例已接入。成功 URI/编码选择由 Activity 状态保存，Widget 状态只保存位置/配置；宿主按相同 URI+编码身份重建来源。重试/延迟初始化检查打开序号，避免覆盖新请求。冷启动最近阅读与逐书 ReaderSnapshot 已持久化，仍未释放历史持久授权；后续宿主书籍记录管理需定义授权保留/移除策略。文件来源仍不依赖 Demo 或 Activity。

## 11. 全书页索引、缓存与资源

当前已实现 BookPageIndexBuilder、轻量 Long 前缀、输入合并/消费者取消、进度和完整索引状态。计算借用已测量正文视口与共享分页；每章后让出执行，可见导航时延后下一章，已开始的读取不强制抢占。布局共享增加消费者计数。索引采用目录安装代次，失败的目录读取不使它过期。逐章来源读取在工作线程准备文本指纹，加载事件前校验旧索引；无版本自定义块重新读取时保守失效。全书页跳转被索引失效取消后返回 PageIndexUnavailable；程序导航仍沿用同一提交源。Demo 已提供计算/取消与跳页，当前仅有 Debug 构建记录。

全书页号通过章节页数前缀和计算；查页使用二分定位章，再解析章内页。索引记录每章内容 revision 与版式键，不只记录一个总数。任意章数变化、章节内容更新或版式变化使 Ready 索引失效。

buildBookPageIndex 是独立可取消任务，优先级低于可见加载/排版；重复同键构建合并消费者。过程中只保存轻量页数与必要映射，原章节内容按预算淘汰；一章失败返回 Failed 并保留诊断，不能发布错误“完整总页数”。来源没有 revision 时，只能保证已计算快照；后续读取通过内容指纹检查变化并失效。

| 资源 | 所有者 | 释放/失效点 |
|---|---|---|
| 宿主 BookSource | 宿主 | closeBook/release 清引用；资源关闭由宿主决定 |
| 目录及轻量页数 | Coordinator | 换书/目录更新/版式失效 |
| 章节内容 | ChapterRepository | 内容代次变化、缓存淘汰、close/release |
| 块布局/页索引 | LayoutCache | 布局键变化、字节预算淘汰、close/release |
| 当前/候选页面 View | Presentation | 页面池复用、候选回滚、模式切换、detach |
| 效果位图租借 | EffectManager | 每次 finish/abort、效果异常、detach/release |
| 选择手柄/工具栏 | SelectionController | 清选区、数据更新、模式切换、detach |
| 页脚时钟任务 | Widget 呈现 | 窗口不可见/离开窗口时停止，重入恢复 |
| Listener / 工厂 | Widget / 宿主配置 | remove/release；detach 不代表永久删除 |

缓存按估算字节预算与访问顺序控制，不只限制对象个数。当前正在显示/翻页使用的布局与画面有短期 lease，不能在使用中被淘汰。来源若返回极大单章，至少必须容纳当前可读工作集；预算不足时明确报告资源限制，具体容量需设备测量决定。

## 12. Widget 状态恢复和异常路径

SavedState 只保存轻量配置、位置/模式和父 View 状态，内部动态 children 的保存由 Widget 自己控制，避免多个页面 ID 相同导致状态覆盖。必须调用父类保存/恢复；宿主提供唯一 Widget ID。配置编解码集中在 internal.state.ReaderConfigurationState，BaseReadView 统一拥有输入/动画状态，BookReadView 只追加书籍位置/模式。分组版本/参数校验、显式 setter 优先级及自定义工厂排除已接入；PageTurnEffects.fromId 解析六个稳定内置 ID。XML 通过 TypedArray 初始化同一不可变模型，不另建参数存储，也不在构造时调用呈现模式钩子。自定义效果与来源由宿主重建，运行中任务和时钟不序列化。ReaderSnapshot 已追加可选 builtInEffectId，预校验效果/模式后显式恢复；null 保留宿主效果，自定义工厂不假装成内置 ID。公开模式设置/快照恢复优先于系统模式恢复，关闭/打开事件后的请求序号校验防止重入覆盖。系统配置状态不并入书籍位置快照。当前仅有 Debug 构建记录。

重建后保存的来源无法从 Parcelable 自动恢复：宿主重新创建来源、注册工厂/监听、设置样式和标注，再 open/restore。正文页码提示重新计算，运行时 Drawable/字体/自定义效果实例不序列化。

detach 时取消旧 lifetime 的加载、排版、导航和事件投递，保留可恢复的会话描述；reattach 新建作用域后重新校验位置并恢复显示。release 幂等，旧 callback 只完成为 Cancelled，不能再引用新会话进行操作。

| 场景 | 对外结果与画面 |
|---|---|
| 空目录/空文件 | Empty 状态；内置文本来源可形成空章节；无越界导航 |
| 初始位置不合法 | OpenResult 明确回退或失败，依 initialFallback |
| 普通跳转不合法 | Rejected，已提交位置保持 |
| 当前章加载失败 | 章节错误占位 + retry，保留可恢复进度描述 |
| 邻章失败 | 当前正文继续可读，邻章边界可重试 |
| 自定义块不支持 | UnsupportedBlock，显示内容错误占位 |
| 页模板正文尺寸无效 | InvalidViewport/布局错误，不以空白表示成功 |
| 字体/尺寸变化 | 取消旧布局，按内容锚点恢复，事件区分 Relayout |
| 旧来源迟到 | 丢弃，不更新缓存/页面/新会话事件 |
| 高亮无法证明范围 | Invalid，保留可导出记录且不绘制 |
| 自定义效果异常 | 回退无动画并报告；候选失效时取消导航 |

## 13. 实施顺序与后续验收

正文已接入节点内字符/词/段落粒度：平台边界解析器与虚拟节点游标独立于导航位置，标准扩展参数调用同一选区入口；折叠范围和缩回端点清除选区。读取游标随切片/选区版本变化或解绑失效，事件带相对范围并在宿主回调后再校验切片。未声明行/页粒度，未自动跨节点或翻页。Debug 构建通过，服务事件与 Unicode 行为仍需设备验收。

Demo 已补齐独立 ReadingStorage：版本 JSON、逐书 ReaderSnapshot/最近 URI+编码的原子偏好设置编辑、身份/损坏检查、状态保存合并与生命周期补存。切书先保存旧进度；成功打开后才关联新来源，准备期状态不写入。冷启动及手动重开使用显式快照，Activity 重建保留 View 状态优先；恢复失败提供从头打开。持久化保持宿主责任，不扩展库 Parcelable 或 BookSource。异步 apply 不保证任意强杀时最近 600ms 的进度，书架/记录清理和持久授权淘汰后续补齐。

1. 评审 04 中类名、方法和结果语义，确定未决产品范围；不在本阶段宣称 API 已发布。
2. 实现纯数据模型、结果/监听契约、BookSource 和 MemoryBookSource；补齐 Java 重载设计及公开符号清单。
3. 实现 BaseReadView、BookReadView、无动画导航事务、生命周期与基本加载占位；Demo 放入真正的 ReaderView。
4. 实现 StaticLayout 兼容层、分页、锚点和页面模板；Demo 从公开 API 完成打开、章节/页跳转、排版与恢复。
5. 实现标注导入/导出、选择、高亮验证及默认工具栏；Demo 演示宿主自行存取。
6. 实现滚动呈现、跨章接续、模式切换及全书页索引；验证未知页数与显式全书计算行为。
7. 逐个实现预设效果，验证中断/取消/边界；Demo 再实现一个独立自定义效果和自定义页面模板。
8. 完成无障碍、XML 样式、SavedState、URI/TXT/EPUB 来源与错误恢复；定义设备及性能门槛后进行验收。

实现阶段需要覆盖的关键用例：取消与过期结果不能提交；跨章翻页/滚动最终位置一致；字号/窗口改变后锚点恢复；同章请求合并和邻章失败隔离；标注范围变更检测；Java callback 每次一次；XML 创建、detach/reattach 与进程重建后的来源重绑定；Demo 无 internal 包依赖。

实现阶段已开始：核心代码、单元测试及真实 Widget 集成用例已经加入。布局等待使用绘制前检查以覆盖同尺寸布局；尺寸回调中的重排排到本次布局之后。当前完整验证状态及剩余能力以 06 的记录为准，性能与多设备兼容仍未验收。

2026-10-01 调整：邻章预加载复用同章共享加载，最后消费者离开会取消读取；并发和缓存预算可在当前会话调整。章节失效仅清目标章布局，不再清全书布局；当前章恢复失败回退到本章首屏并发恢复事件。主线程解析系统实际 sp 像素大小作为不可变测量输入和布局键，适配非线性字体缩放。正常 callback 请求排队启动，样式取消回滚按代次限定。目录重载的读取与安装分开，读取失败保留旧目录；安装按明确版本复用缓存，当前章变更/删除先清旧页再恢复，结果通过恢复事件报告。

标注数据层采用独立 AnnotationStore：原子快照替换、ID 冲突、增删改和状态验证；只保存标注及状态，不长期持有章节全文。章节加载、缓存和当前布局快照驱动验证，逐段比较 quote 避免额外拼接完整选区。标注事件使用独立修订号抑制嵌套更新后的旧通知。书签导航使用现有事务，回退单独报告。分页高亮复用 Layout 的选区路径，跨块范围映射、片段裁剪及点击命中已经接入；装饰层在数据变化时生成可见路径，绘制期间复用，不重新分页。高亮色表防御性复制，单独颜色变化仅重绘。文本命中、选区/手柄与默认工具栏继续按第 9 节实施。Demo 通过公开标注接口示例 JSON/SharedPreferences 存取、添加跨块高亮及点击更色/删除，保存格式不进入库 API。

文本命中、长按初始选区、手柄拖动及同章跨页扩展已落地。TextSelectionResolver 负责原文顺序、范围和平台边界；SelectionToolbarController 负责默认 ActionMode/自定义弹窗及可见选区矩形定位；Widget 持有选区与修订号，受控动作校验当前版本后复制或高亮。BaseReadView 只提供长按触发/取消、内容输入优先处理与输入屏蔽钩子，不依赖书籍或 TextSelection。手柄从 caretForOffset 取得插入点几何，保持抓取偏移；拖动期间暂停工具栏动作，原生 ActionMode 原地复用以保持正文高度。边缘扩展任务跟随呈现生命周期，先经 Widget 同章/代次校验及统一导航，再更新选区；失败/取消/固定端点和章节边界停止。拖动近邻命中由 Layout 几何提供，不逐像素搜索。复杂字素、行尾方向、边缘裁剪与实际窗口交互仍需设备验证。

EPUB 目录与正文已有内部实现：EpubNavigationParser 保留 nav/NCX 层级及片段并生成章节标签映射；EpubXhtmlParser 输出现有 ContentBlock，统一空白规范化、标题层级及内容省略诊断。普通文本和 pre 均复用 Widget 排版，不新增第二套呈现链。两者遵循原有取消/XML/归档预算，输出不可变集合。详细边界见 04 的“EPUB 目录与正文内部解析”；公开来源与诊断生命周期已接入，Demo 接入仍继续实施，尚不能作为 EPUB 阅读验收。

公开来源已接入 EpubTextBookSource 与预算/诊断/原始导航树 API，复用容器/XML/正文层。目录按 spine 提供，正文按需提取，失败保留旧快照，宿主关闭来源；生命周期规则详见 04。接下来 Demo 需支持 EPUB URI 选择和来源类型持久恢复；片段锚点导航尚未完成，当前构建记录不替代真实 EPUB 与设备验收。

Demo 已接入 TXT/EPUB 文件格式选择、统一 FileInput/来源工厂、URI 授权、重试及来源退休；最近输入记录 v2 包含 sourceType 并兼容 v1 TXT，Activity 状态重建同样保留类型，Widget ReaderSnapshot 未扩展宿主字段。EPUB 目录提供当前已读取资源的提取说明和重读入口。冷/暖恢复、授权丢失、取消/异常及真实文件表现仍待设备验证；精确片段导航仍未完成。

EPUB 元素锚点已有实现：正文提取同时构建不可变 id/xml:id 到 ContentAnchor 的映射，文档 ID 唯一检查、空块/尾部偏移夹紧、省略内容无锚点。来源 resolveNavigation 接受当前树条目，按需读取并返回带 revision 的 ReadingPosition；无法定位返回 null，XML/ZIP 失败继续抛出，不把失效目标替换为章首。Demo 原始导航树异步解析/跳转入口仍需接入。Debug 构建通过，未新增或运行测试；空白/空元素/内联/重复 ID、编码片段、过期条目和实际分页/滚动定位待验证。

Demo 原目录/片段跳转已接入，原树文档顺序和缩进、spine 备用入口、可取消解析与现有 goToPosition 共用；源解析和宿主导航分工明确。请求与书籍实例/打开序号验证，换书/重载/停止/销毁取消，旧弹窗拒绝作用于新书。Android 协程依赖显式加入宿主运行时。仍需验证实际服务/设备下的分组阅读、等待取消、空目标、Unicode 片段、mode 切换和旧请求不会提交；构建记录不代表 EPUB 验收完成。

静态 XML 样式已扩展到当前模型字段，统一 ReaderStyleAttributes 负责 TypedArray→不可变 ReaderStyle，ReaderView 构造只调用初始化方法：页/正文内容边距、正文系统字体/字距/缩进/行段距、标题字体/对齐/间距、chrome 字号/颜色以及默认高亮/选区颜色和手柄。未声明值保留默认，单位转换与模型校验一致；Demo XML 增加实际使用例。字体资源/Drawable、更多分层样式字段和样式 SavedState 仍待实施，不能把本轮编译通过当作完整样式验收。

动态样式已有 View SavedState 支持：ReaderStyleStateCodec 用版本化原始 Bundle 保存当前模型数值与有界调色板，BookReadView SavedState 追加 style，旧/无效状态保持 XML；未打开且无显式 applyStyle 时恢复，显式相同配置也阻止 SavedState 覆盖。Typeface 保留新实例 XML/宿主值，ReaderSnapshot 不增加样式。Demo 初始化调色板延后到恢复后并合并，避免提前 setter 吃掉恢复值。字体/Drawable 对象恢复、更多样式字段及独立冷启动样式偏好仍待后续工作。

ReaderFont 系统/打包资源/运行时三类描述已接入 BodyStyle/TitleStyle（新字段非空优先，保留原 Typeface 兼容入口），XML family 支持 @font；资源 IO 加载放在共享布局请求内，排版/缓存/索引沿原链。解析后有效 Typeface 传给所有 provider，资源异常走 LayoutFailed。明确拒绝 XML font provider，宿主加载下载字体后提供 Runtime。System/Resource 描述保存到 style Bundle，运行时对象仍由宿主提供。字体资源加载、样式回滚、布局缓存/索引以及多版本设备表现尚待验证。

ReaderBackground 与默认模板背景绑定已实现：颜色/资源/独立 DrawableFactory、PageStyle 可空新字段优先于旧底色、透明 drawable 合成底色、逐 root 实例与 unbind 释放、背景后显式 padding。XML @drawable、View 状态 Color/Resource/回退模式描述，Demo 用纸色渐变资源展示。自定义模板自行应用背景，工厂对象仍由宿主提供；占位定制和其他 chrome/分层样式仍需实施。Drawable 外观/回调/复用、翻页快照及状态恢复尚待设备验证。

ChromeStyle 基础定制已扩展：固定/自适应高度、边距、对齐、默认内容选择及 ReaderBackground。默认模板统一 bindChrome，页眉/页脚独立背景实例，既有正文 viewport 测量参与高度变更；XML 与兼容 style 状态追加相应字段，Demo 显式配置章节标题和章节页进度。字体和时间内容/生命周期刷新尚未完成，不能宣称 chrome 全部交付；固定小高度裁剪与滚动虚拟页说明按 04 执行。

Chrome 字体已接入 ReaderFont/Typeface 兼容入口、XML 与状态描述。分页/滚动在首次模板测量前完成 IO 字体准备并校验代次，失败沿 LAYOUT/FontLoadFailed；滚动可见位置绑定复用候选字体，避免主线程资源加载。初次绑定进入候选清理范围。时间内容/刷新继续实施，实际字体与 viewport、取消/错误/重载行为仍待验证。

时间内容已接入 TIME/XML 与模板/呈现默认更新钩子；Widget 统一 ReaderPageClock 管理可见 active 页的分钟更新，隐藏候选无计时器，解绑/释放/后台停止、恢复即时更新。回调所有权/代次/重入/异常保护，时间不导航、不发状态事件。Demo 增加页眉章节名/时间开关。系统变更在分钟/恢复时刷新，不注册常驻 receiver；设备生命周期/时区/格式与自定义模板行为仍需验证。

首屏公开占位已接入 ReaderPlaceholderFactory/State/Actions 与 setPlaceholderFactory；旧 TextView 替换为控制器，fresh root、bind/unbind、状态去重、重入/过期动作、异常默认回退和首屏受控重试。已有正文不被全屏失败覆盖。默认占位本地化，Demo 首屏占位重试成功跟进宿主来源/进度。章节内部 EMPTY_CHAPTER、滚动加载/失败边界和自定义占位 Demo 仍继续实施；枚举包含这些种类不代表已接入。

章节内部占位已接入共同工厂：分页空块章节 bodyHost 覆盖、单页原位置/空锚点保持；滚动空章节/边界 FrameLayout holder，实际错误状态与受控 retry、recycle/detach 清理。空章/边界统一128dp，保持可见章偏移。边界重试先经过已有 retryChapter 清除失败缓存，避免重复读取缓存失败；三章窗口与活动代次/row/holder校验沿用原规则。自定义占位 Demo 及设备验收继续保留在范围内。

自定义页面/占位 Demo 已接入公开接口：工具栏目录/设置动作、章节页进度/时间钩子、bodyHost 正文插入及解绑清理；加载进度/友好文案/受控 retry。设置可切换并通过公开 default 工厂恢复默认；Activity 保存选择标志重建 factory，Widget 不序列化对象。自定义示例有自身结构/颜色/内容策略，不宣称完整应用默认模板所有样式。实际尺寸、触摸优先、辅助技术、取消/旧动作和恢复仍待设备验证。

Demo 最近阅读列表已实现有界history/旧recent迁移、身份去重、书名/来源/进度展示、重新打开与record移除。保存进度与metadata同edit，最多32条淘汰已知旧进度；当前书籍拒绝移除，原文件/标注保留。Widget仍只接收BookSource/ReaderSnapshot，不增加书架字段。URI授权回收、文件友好显示名和更多主题/进度控制继续实施。


#### URI授权管理实现补记

已接入宿主 UriGrantStore：application IO串行队列、持久新读授权账本、引用保护、记录维护后的无引用授权清理与停止时重试；授权取得完成的旧选择不会覆盖新请求。保留其他功能/旧版本既有授权及所有写授权，文件与标注不随授权清理删除。清理前同步确认阅读存储落盘，不在初次启动根据未知/损坏历史清扫。Widget接口和快照不包含权限数据。构建通过；提供器授权、快速选择/重建、同URI多编码及淘汰/记录移除待设备验证。

#### 分层样式实现补记

Page.viewportPadding、Body.containerPadding/blockPadding、Title.padding/letterSpacingEm已追加API、XML与兼容SavedState。内置两种呈现共用ReaderBodyInsets，块测量统一包装内容宽度与首尾单元/片段几何，所有绘制/选区/命中/可见文本偏移同源；provider只处理扣除横向边距后的内容几何。Demo提供各层四边与标题字距编辑。构建通过；大边距失败/事务回滚、跨页连续流、RTL、自定义provider、选区/辅助技术和状态恢复待设备验证。更多chrome高度策略、冷启动偏好和剩余验收继续实施。

#### 宿主偏好和主题实现补记

已新增ReaderPreferences独立version 1 JSON：可恢复样式字段、输入/翻页参数、内置模式/效果与自定义模板标志；分组验证、有界文本/调色表、资源名恢复、运行时对象留给宿主。冷启动按公开suspend接口顺序初始化后打开书籍；Activity View状态重建不覆盖，逐书snapshot后恢复模式/效果。只保存成功提交样式，生命周期补存。Demo四种明暗主题协调背景/正文/标题/chrome/手柄，通过公开事务切换，Widget不内建主题列表。Debug构建通过；设置恢复、损坏存储、资源更名、事务取消和设备显示待验证。完整排版面板的字体/更多数值编辑、进度控制与剩余扩展/验收继续实施。

#### 完整排版编辑实现补记

TypographySheet从MainActivity抽出，页面/正文区域、正文、标题、页眉、页脚五组覆盖FR-DEMO-04，并支持系统字体、首行缩进及chrome可见/字体/内容/高度。统一数值/四边/选择编辑、模型约束、最新样式copy和宿主apply回调；生命周期关闭和窗口身份拒绝旧操作。成功偏好保存复用既有链路，Widget接口未增加UI依赖。Debug构建通过；实际小屏/键盘/辅助技术、生命周期、回滚和模板字段作用仍待设备验证。章节进度/控制面板、网络来源示例、独立效果与自定义效果演示等完整剩余范围继续推进。

#### 阅读控制面板实现补记

已新增ReaderMenuController非模态覆盖层、中间点击toggle/按钮/返回关闭、目录/排版/设置、邻章按钮与章节SeekBar/精确页输入。全部通过公开状态/分页结果与goToPosition，目标实例/章节/revision/layoutKey/页数校验、拖动预览后一次提交、键盘/辅助技术进度分支、单Job及生命周期取消；显示隐藏不改变正文viewport。Debug构建通过；实际窗口/IME/辅助技术、模式与虚拟页、重排/取消/恢复待验证。网络/独立效果/自定义效果演示与其他完整剩余要求继续实施。

#### 自定义效果接入实现补记

ReaderEffects增加demo.wipe宿主工厂/新实例，公开PageTurnEffect生命周期、双PageSurface裁剪/阴影、方向/RTL、Canvas finally还原与finish/abort/detach清引用；无需自有时钟/导航/位图。菜单接入，宿主偏好/Activity仅保存已知标识并重建，自定义实例不进入Widget SavedState或ReaderSnapshot。Debug构建通过；实际手势/结算/取消、RTL、模式/重建、端点与视觉待验证。独立容器、网络章节来源演示与完整剩余范围继续推进。

#### 独立通用容器Demo实现补记

新增ContainerActivity/CardReadView与CardAdapter，五卡片Int位置、公开ReadContentAdapter/PreparedRead/PageSurface、尺寸准备/代次检查/显示所有权/释放；BaseReadView统一导航/手势/提交/效果。独立入口、前后/效果/减少动画/退出、能力监听、已提交序号与已知factory ID重建。Debug构建通过；挂载/旋转/取消/位图效果/服务与真实边界待验证。网络章节演示、可访问性跨节点及完整剩余验收继续实施。

#### 网络来源Demo实现补记

NetworkBookSource宿主BookSource已接入有界HTTPS目录JSON/同源UTF-8章节、响应状态/字节/长度验证、严格编码、SHA修订与固定身份、元数据原子快照、并发章请求、协程期限/取消disconnect及AutoCloseable。NetworkActivity独立入口、显式地址/取消/重试/前后页、停止关闭与快照恢复，示例目录/正文文件提供协议样本。Widget/库Manifest未增加网络逻辑/权限。Debug构建通过，未发实际网络请求或运行测试；服务、取消/后台/重建及错误边界仍待设备验证。可访问性跨节点、缓存/性能、其他完整验收继续推进。

网络来源边界继续完善：目录JSON进入JSONObject前进行32层结构预检与取消检查，明确拒绝非字符串关键字段/非整数版本；输入和解析后地址有界，Content-Length格式非法明确失败。未增加新网络协议或Widget接口，失败继续保留旧目录。构建通过，运行边界仍待验证。

#### Demo全书页数实现补记

PageIndexPanel已替代Activity内嵌计算/输入弹窗，统一窗口、监听器与单Job所有权、序号及生命周期取消。完成窗口切换先解除旧dismiss动作，避免自取消；书籍/索引实例在通知、提交和执行前复核，Long范围校验后调用公开goToGlobalPage保留Widget内部失效保护。Debug构建通过，未新增或运行测试，计算/取消/快速重排/换书/重建及实际页号跳转待设备验证；完整剩余范围继续推进。

#### Demo标注管理实现补记

AnnotationPanel补齐高亮列表/摘录详情/严格定位/改色/删除与明确校验状态，并复用书签查看/恢复/删除/添加。未校验项先显式打开章节触发验证，失效项不伪提供精确定位；截取仅UI、记录不改。窗口/书籍/记录相等检查、单导航Job/序号和生命周期清理，公开API+既有持久监听链路。构建通过，真实标注验证/内容变化/窗口/服务与恢复待验证，完整剩余范围继续推进。


#### 正文相邻虚拟节点移动补记

同一ReaderBodyView内非扩展字符/词/段落移动已在节点边界寻找相邻可见有效切片，按进入方向重置游标、转移标准辅助功能焦点并发布目标节点索引事件，复用正文原范围与清除选区入口。构建通过，未新增或运行测试。跨节点扩展、滚动跨行协调、可见页外导航及真实服务焦点/事件验收仍为剩余工作；完整辅助功能范围保持。


#### 跨节点扩展选区补记

同一正文视图内EXTEND_SELECTION已保存固定原文锚点并跨相邻可见节点移动，双向收缩/跨固定端点沿统一双锚点解析器和reportSelection执行。两种呈现绑定内部回调并检查活动候选/内容代次，解析原块顺序与字符边界/摘录；会话受固定切片、选区版本、节点身份和焦点重入复核约束。Debug构建通过，未新增或运行测试；滚动跨行/视图与可见页外协调、实机服务焦点和完整选区体验继续保留在剩余范围。


#### 滚动相邻可见正文行普通移动补记

普通字符/词/段落移动已在本行边界通过内部回调进入相邻已挂载可见正文行。仅扫描attached child positions，按阅读顺序/方向复用边界游标和遍历事件，活动/代次/布局/源行位置与身份检查及回收清理；成功后源行清焦点受原片段身份约束。构建通过，未新增或运行测试。跨行扩展、可见范围外文本粒度协调与真实服务焦点/复用/事件验证继续保持在剩余范围。


#### 滚动跨可见行扩展与选区同步补记

同章节相邻可见行已传递固定锚点及原切片验证，原块顺序比较支持双向扩展/收缩/跨端点，章节对象边界禁止扩展。选取/扩展/清除成功后同步更新挂载行装饰，解决延后一帧导致服务读取旧范围的问题；分页/滚动检查监听重入后仍为请求选区。构建通过，未新增或运行测试。离屏协调、真实跨View焦点、跨chunk/Unicode/章节边界/复用和完整设备验收仍待完成。

#### 缓存预算计费修正

已用正文/测量行/边距单位/分页与连续片段累计估算替代布局pageCount固定权重。公开ContentBlock/BlockLayout/ChapterLayout可选稳定估算，未知资源不准入缓存但正常呈现。ByteBudgetCache保存计费值、预先淘汰避免Long溢出、预算更新即trim；累加饱和。构建通过，未新增或运行测试；估算校准/实际峰值和长期性能仍为第5节未完成的设备验收，不能宣称缓存预算等于应用内存上限。

#### 正文淘汰后的布局复用补记

布局键现以工作线程已计算的内置文本指纹和书籍/章节/revision/排版输入匹配，正文缓存淘汰后重读的同内容新快照可复用布局。自定义块仍按内容实例身份区分；失效按章节字段清理。命中时恢复对应Ready页数/layoutKey，避免留着其他布局的失败/计算状态。构建通过，未新增或运行测试；不同预算/快照、合并/失效/状态回调和实际来源重读成本仍待验证。

#### 自定义provider缓存边界补记

文本指纹跨快照复用限定默认provider链；注册自定义provider后继续保留内容实例身份。布局/索引键捕获provider引用身份和id/version值，避免插件equals合并不同渲染器或可变getter污染旧键。行为变化需version升级及重新注册，测量中不可修改的宿主契约已写入SPI。构建通过，未新增或运行测试；实际覆盖内置块、重注册/版本、并发取消/索引验证继续保留。

#### 分页取消与状态归属补记

章节分页状态现绑定最新任务归属，旧布局不能覆盖新任务的Ready/Failed。取消仍属于Calculating的请求转Unknown；缓存命中取得对应Ready且解除旧任务状态归属。入库/通知前及通知后检查任务有效性、会话、请求实例；布局/章节/目录失效解除归属，整体先清后取消。构建通过，未新增或运行测试；乱序/共享/取消/监听重入与实机状态验收待完成。

#### 章节加载回调重入补记

共享章节请求增加实例归属检查，Loading通知后再检查有效性才申请许可；成功/失败通知前后检查协程/代次/请求，避免关闭、失效或重试后继续返回旧结果。失效先移除旧请求/缓存再取消，完成处理器不影响新请求。构建通过，未新增或运行测试；许可等待/来源取消/状态监听重入和协调器完整失效顺序待验证与继续检查。

#### 章节失效通知顺序补记

正文仓库清理与通知分离，延后通知受代次/当前Unloaded状态约束。协调器先解除旧正文/分页/预载所有权，取消捕获任务后更新索引及公开状态；后续阶段受失效序号/会话/仓库身份约束。索引失效/构建检查取消或通知中的新代次，retry尊重Superseded。构建通过，未新增或运行测试；各状态回调换书/重试/再失效/构建、取消终态及恢复继续待验证。

#### 目录安装重入补记

目录安装先解除旧请求/缓存再取消和通知，以会话/仓库/目录版本/描述实例复核，被替代返回Superseded。独立安装owner代替通知抑制Boolean，旧finally不解除新安装的抑制；reloadBook检查安装结果后才恢复位置。构建通过，未新增或运行测试；嵌套安装、通知中换书/刷新和保留/删除/空目录的实际验收仍待完成。

#### 全书索引失败重试补记

Failed通知期间同输入不再合并旧失败任务，按新代次构建；普通Building仍共享。初始通知/章节结果/失败和Ready通知前后检查活动、代次、请求实例；异常和finally受相同归属约束，旧任务不覆盖新构建状态。构建通过，未新增或运行测试；通知中重试/换书/取消与消费者终态实际行为继续待验证。

#### 章节/分页失败共享补记

失败通知中章节新get和同布局新请求不再合并旧失败任务，正常进行中请求共享保留；分页用失败请求实例标记区分同章不同输入，完成清自己的标记。缓存Ready通知后复核活动/会话/目录/失效，预算淘汰不当作内容失效。构建通过，未新增或运行测试；直接请求/显式retry/乱序及缓存通知重入待实际验证。

#### Widget恢复结果重入补记

open/reload的失败、空目录、阶段和回退后追加打开序号检查；章节恢复通知后检查内容代次。章节失效/重试捕获原书籍实例与序号，在代次失效回调后、协调器返回和邻章读取/错误通知后复核，避免操作新书同名章节或返回旧结果。构建通过，未新增或运行测试；各通知重入、位置回退和终态实际验收仍待完成。

#### 暂停/关闭资源归属补记

暂停先解除仓库/作用域/预载/布局所有权，失效索引后关闭捕获旧对象；仓库close先清登记再取消。close先清来源/书籍并检查关闭被替代，open不覆盖回调中新会话。工作代次使同会话仓库重建仍拒绝旧通知。构建通过，未新增或运行测试；取消回调重开/重挂载、来源取消与资源释放仍待验证。

#### 邻章预加载重入补记

预加载计划绑定书籍身份、仓库/作用域、工作代次、配置和调度序号，验证位置bookId。取消旧Job及启动新Job后复核，新计划取代旧循环。前台get先捕获上下文并解除无关预载登记再取消，读取前后拒绝旧上下文；普通预载调度不会单独使合法前台读取结果失效。构建通过，未新增或运行测试；快速导航/换书/配置与通知重入、共享消费者实际验收继续待完成。

#### 滚动边界候选归属补记

边界任务绑定候选/内容代次/书籍/方向槽Job，重试与读取/异常后复核，忙碌导航或拖选区时不安装结果。rebuild后复核再seek，初始列表通知替代取消未启动Job。候选close幂等、先关闭标记和移除登记再取消，阻止取消回调启动旧工作。构建通过，未新增或运行测试；导航/取消/选区重叠、换书/重试与锚定继续待验证，模板清理继续检查。

#### 页面模板清理异常补记

分页/滚动共用逐项清理，普通异常后继续释放，首cause保留suppressed；分页候选close幂等，show/detach/dispose统一。旧解绑异常不破坏新页面提交，诊断本地日志和原代次绑定的延后PageCleanupFailed通知。构建通过，未新增或运行测试；插件清理抛错、取消/换书/模式、重复dispose和实际资源释放待验证。

#### 模板重入后显示流程补记

分页装饰/可见性、滚动可见性/旧close后复核活动、代次、协程和detached；被替代停止监听安装等后续步骤，最终仍由导航控制器token检查决定提交。滚动延后帧检查候选身份，detach先清active后取消旧工作。构建通过，未新增或运行测试；插件/root回调重入、模式/强制导航及监听资源实况待验证。

#### 候选创建清理范围补记

模板返回后的校验、正文创建/挂载与bind纳入清理边界，记录fresh模板/root安装归属，失败和主动close移除库正文视图。已有parent的非法root不取得所有权、不误解绑或移除；bind后复核代次并拒绝外部挂载。构建通过，未新增或运行测试；插件校验/挂载/bind错误、错误复用及重入与资源实况待验证。

#### 模板预留补记

模板与 root 按实例身份在首次 bind 前预留，覆盖跨 Widget 和未挂载准备；冲突拒绝后不清理其他所有者。共享清理在普通异常处理与 unbind 完成后解除预留，释放重入期间保持占用。SPI 明确稳定、无异常的视图 getter，以及工厂创建失败的内部资源责任。Debug 构建通过，未新增或运行测试；重叠准备、多 Widget、误复用、unbind 重入/异常与实际释放继续待验证。

#### 占位生命周期补记

占位 View 以实例身份跨控制器预留，拒绝挂载或重复占用对象时不清理他人视图。clear 纳入更新边界，重入清理/显示先失效序号、保存最新请求，bind 后清理过期绑定再延后处理。移除/解绑普通异常独立记录并解除预留，SPI 明确 bind 不挂载。Debug 构建通过，未新增或运行测试；跨 Widget/holder、插件回调重入、挂载错误和资源实况继续待验证。

#### 选区工具栏生命周期补记

SelectionToolbarFactory 增加默认 unbind；合法返回 View 的取消准备、替换、暂停及关闭走幂等释放，跨控制器按 root 实例预留，非法复用不清他人对象。调用序号隔离 create/测量/原生模式回调，旧异常不关闭替代工具栏；窗口与插件普通清理异常分别记录继续。BookReadView 同步选区/代次错误归属检查。Debug 构建通过，未新增或运行测试；弹窗与原生工具栏、插件重入/错误复用和实际资源释放待设备验证。

#### 工厂视图预留收敛补记

三类工厂共用 ReaderViewOwnership 的实例预留与匹配 owner 释放，消除独立表的交叉复用缺口；页面仅另保留模板身份约束。控制器清理/重入职责保持，架构职责表同步。Debug 构建通过，未新增或运行测试；跨类型/多 Widget 误复用、过期清理与资源实况继续待验证。

#### 选区工具栏 Demo 接入补记

公开 setter 的 null 恢复原生工具栏。新增只使用公开 SPI 的 CustomSelectionToolbarFactory，展示复制/高亮/取消及解绑监听；输入设置提供切换，宿主偏好和 Activity 标志重建工厂，旧记录缺字段回到默认。Debug 构建通过，未新增或运行测试；选区切换、操作、重建/冷启动、窗口与辅助交互待设备验证。

#### 自定义正文块 Demo 接入补记

新增纯公开 SPI 的不可变图形块与 provider，工作线程测量、稳定身份/字节估算、单单位分页及连续绘制、非选择语义与无障碍描述。独立 Activity 菜单入口展示前后导航、分页/滚动与重试，固定 Widget ID、打开序号和停止/销毁清理。测量结果不保留上下文/取消回调。Debug 构建通过，未新增或运行测试；边界、锚定/重建、缓存与辅助交互实况待验证。

#### 分页离屏普通遍历补记

可见文字耗尽后新增异步请求入口，启动前核对源焦点/切片/选区版本，等待准备时保留源焦点。分页只登记一个任务，使用统一无动画 UserNavigation 成功提交后在新正文边界续同一粒度；绑定书籍/代次/实际提交，尺寸变化及 detach 取消。SPI 默认委托现有宿主导航。Debug 构建通过，未新增或运行测试；滚动离屏、离屏扩展、空/无语义页焦点衔接和服务设备实况继续待完成。

#### 滚动离屏普通遍历补记

离屏请求携带原文边界，新增目标节点锚点游标入口。滚动内置文字在 worker 查找粒度目标并检查取消，最多借用紧邻章快照，统一无动画导航提交后校验候选/书籍/位置并在已挂载目标行续接。源焦点/切片/代次、忙碌/拖动及回收清理隔离旧任务。Debug 构建通过，未新增或运行测试；自定义/空/无语义内容仍有视口回退限制，离屏扩展和服务设备实况继续待实现/验证。

#### 离屏原文定位统一补记

分页/滚动共用 worker 原文定位器，返回 Found/Exhausted/Unsupported/Invalid，删除重复查找；只有确耗尽才读取紧邻章，无效/失败/邻章无目标停止，明确自定义回退限制。分页接入显示锚点与进入游标。词识别按 Unicode 码点且不复制子串，增加扫描取消检查，架构职责同步。Debug 构建通过，未新增或运行测试；连续空章、自定义语义、离屏扩展与设备实况继续待完成。

#### 连续空章定位补记

共用 AccessibilityChapterTraversal 按目录逐章借用，只有耗尽才续读，worker/来源前后复核取消与请求，章节间 yield；不收集全文或布局，中间章不提交，最终目标统一导航。自定义 Unsupported 携带块位置，新增普通语义块边界入口，避免旧视口回退定位错章。Debug 构建通过，未新增或运行测试；自定义描述续接、无语义块、离屏选区扩展及设备实况继续待完成。长空章链可逐一读到边界，实际预算/取消趋势待验收。

#### 自定义整块语义边界补记

公开片段默认 false 的整块描述声明，内部 Source/CompletedBlock 明确分离。普通遍历完成完整描述后跳过同块剩余可见片段或同章滚动行，离屏沿共同定位器从下一块续读，不伪造文字选区。Demo 分隔块 opt-in，API 核心说明同步并移除未落地的 SelectableBlockLayout 名称。Debug 构建通过，未新增或运行测试；复杂局部语义、离屏扩展和设备实况仍待完成。

#### 同章离屏选区扩展补记

分页与滚动接入保留原文身份的固定锚点，显示目标后复核原选区/书籍/代次/内容再沿既有选择接口更新，目标节点续接扩展游标。扩展只查当前章，不进入邻章或降级 Unsupported；源准备前仍检查切片/焦点/投影版本。未知版本/指纹使用快照身份保守验证，回收清回调。Debug 构建通过，未新增或运行测试；正反向/缩小/首次扩展、缓存与重入及辅助服务实况待设备验证，复杂自定义局部语义继续待完善。

#### 离屏请求导航检查补记

BaseReadView 带谓词的受保护导航重载在进入、prepare 和呈现 show 前校验，失效使用原 Superseded/候选 finally 清理。host 增加兼容重载，Widget 强检查、自定义 host 默认入口检查。分页/滚动源条件排除自身 Busy 状态但保持焦点/切片/书籍/选择约束；不在有效显示后用已释放源节点反查。Debug 构建通过，未新增或运行测试；慢准备/焦点变化、状态与显示内部回调、自定义 host 和资源实况待验证。

### 正文生命周期收敛记录

已将分页释放与滚动绑定/回收的正文清理收敛到 ReaderBodyView.clearContentBinding。滚动适配器维护已绑定视图集合，关闭时先取快照并清空集合，再逐个撤销正文绑定；重新绑定先撤销旧回调。临时离屏与结束绑定采用不同生命周期操作，避免离屏行重现时丢失内容。

Debug 构建通过，未新增或运行测试。待设备检查：长距离滚动、缓存行复用、模式切换、失败候选释放、辅助服务焦点及内存趋势。

### 异常退出后临时文件的回收

TXT 与 EPUB 已在共用 OwnedSourceFile 补入创建期进程租约、独立标记文件和过期候选回收；避免两种来源各实现清理状态机。构建期文件到成功快照转移同一 owner，失败/取消释放原 owner。读者计数与进程锁职责分开，最后读者离开才释放锁。进程内登记避免打开活动标记引起部分平台文件锁被释放。

回收以管理子目录、24 小时保留期、64 个候选上限、跨进程非阻塞独占锁和取消检查约束；旧文件不自动迁移/删除，跨进程索引复用仍未实现。Debug 构建证明可编译，未新增或运行测试；设备上异常退出/多进程/系统淘汰/权限及长时间资源趋势尚待验证。

### TXT 标识扩展落地

已落实原 ContentIdProvider 目标为专用于文本来源的 TextContentIdProvider，并追加 TextBookOptions.contentIdProvider。内存/磁盘解析共用 TextContentIdentity 验证，解析阶段冻结 ID；磁盘记录增加 ID 字节字段及预算/读取校验，按章读取不重新调用宿主策略。默认编号兼容，正文规范化与 revision 规则保持既有契约。

Debug 构建通过，未新增或运行测试。待设备/来源验证：两种路径一致性、重复/空白/非法 Unicode 标识、策略异常/取消时保留旧快照、前言/空文件和修改后的书签/高亮恢复；可配置规范化与跨进程持久索引仍待实现。

### TXT 规范化与解析合并

已落地 TextNormalization 三种逐行方式，默认兼容保留；TextSnapshotParser/TextLineReader 将内存与磁盘的章节语法和行读取统一，存储仅消费块/章结果。非默认策略计入 revision，防止把变换后的 offset 当作同一版本；身份回调后再次检查取消/关闭，避免继续写入过期解析结果。

Debug 构建通过，未新增或运行测试。待来源/设备验证：默认路径回归、跨缓冲 BOM/CRLF、空白末行/空章节、标题与 ID 输入一致、两种存储的正文/目录/revision 一致性、取消及旧快照保留、切换策略后的恢复/标注状态。跨进程索引复用和大型章节性能仍待完成。

### TXT 配置宿主接入示例

已新增独立 TextSourceActivity 及菜单/Manifest/View ID，展示 TXT 内存/磁盘、规范化和身份策略公开契约；样例对重复空白行显式消歧，内容摘要不当作通用唯一性保证。状态请求序号隔离旧完成，后台关闭来源，前台重建并按轻量快照恢复。SnapshotState 合并网络/文本示例的 Bundle 快照读写，保留旧网络字段格式。

Debug 构建通过，未新增或运行测试。仍需设备检查：配置切换后正文/ID/revision 和回退、模式与位置恢复、Activity 重建、后台取消、临时文件释放；此示例是宿主接入和手动观察入口，不是上述行为已通过的证据。

### 原始需求核对：默认页脚

逐项核对发现 FR-VIEW-01 默认页脚未同时含时间，已追加组合内容及 XML/样式选择入口，DEFAULT 解析为组合项，分钟更新保留进度前缀。没有增加独立时钟、页面位置副本或另一个页脚 View。Debug 构建通过，未新增或运行测试。核对索引见 07_REQUIREMENT_IMPLEMENTATION_AUDIT.md，页面结构外的完整需求/质量/验收范围继续保留。

### 来源核对：解析后的版本身份

FR-BOOK-03/09 核对发现仅原字节加 normalization 的摘要未覆盖实际编码/章结构/内容 ID 差异，已统一使用 TXT revision v2。共用解析器按输出增量摘要，最终结合原字节 SHA-256；两种存储共用，不引用源对象地址。长字段分块检查取消，内存暂存仅保留已解析模型引用，逐章完成最终快照。

Debug 构建通过，未新增或运行测试。需覆盖：相同输入不同编码/标题/ID/规范化输出、内存与磁盘相同版本、输出未变时复用、旧 snapshot/标注不匹配与回退、失败取消旧快照保留。07 新增来源需求核对表，完整质量及验收范围继续保留。

### 导航需求核对：初始定位结果

FR-NAV-01 至 07 已加入 07 核对表。openBook 修复已知章节+非正页号的拒绝分类，并在回退导航 Unchanged 分支保留 Restoration.Fallback。继续由 NavigationController 单次提交，初始定位与普通导航共用 prepare/show，不加额外恢复位置存储。

Debug 构建通过，未新增或运行测试；负/零/越界页、未知/极端章节号、显式位置优先、BOOK_START/FAIL、回退 Unchanged 及状态/回调重入仍需实际验证，全书索引、边界与模式锚定的验收范围不缩小。

### 标注需求核对：字符端点

FR-MARK-01 至 08 已加入 07 核对表。修复导入标注与交互选择边界不一致：共用 TextCharacterBoundaries 校验书签锚点、高亮范围、选择提取及无障碍边界，非法导入记录保持原值并标 InvalidRange；完整块快速路径避免重复分割。没有通过修正宿主范围来隐式扩大高亮。

Debug 构建通过，未新增或运行测试。仍需覆盖代理对/组合字符/emoji、空段跨块、不同 Android 分割规则、待校验→失效→导出、实时/自定义选择与工具栏、重排/版本变更及监听重入，完整标注验收保留。

### 模式需求核对：可见锚点

FR-MODE-01 至 05 已加入 07。发现滚动当前片段无锚点时直接回退全章末尾的问题，修复为每章按阅读顺序的邻近端点索引：优先当前/之后首端，无后续再用此前/当前末端，全无锚点保留 null。正常文字路径保持实际可见行，索引懒建两次扫描、只存引用，查询常数成本，与最多三章显示窗口共同释放。

Debug 构建通过，未新增或运行测试。需验证自定义无锚点块在章首/中/末、连续无锚点块、全部无锚点章、位置/虚拟页/模式恢复，以及首建耗时与资源预算；真实滚动续接、边界失败、取消和切换锚定全部继续待验收。

### 翻页需求核对：端点结算

FR-FLIP-01 至 09 已加入 07。结算端点 from==to 的多余双帧等待已删除，仍发送最终阶段帧、前后取消/所有权检查，然后复用 show/finish/复位和资源释放链路。效果/手势/位置所有权未引入新状态机。

Debug 构建通过，未新增或运行测试。已到达0/1端点、慢来源松手、快速反向、自定义最终update异常/重入、布局等待、低预算回退和全部六种效果实际行为仍待验证。

### 样式需求核对：回调重入与回滚

FR-STYLE-01 至 09 加入 07 核对表。applyStyle 在中断/失效前记录预期代次，复核回调后的事务归属；成功状态发布和装饰通知也检查替代。回滚只恢复旧布局字段，保留并行接受的装饰配置，并在恢复步骤间检查代次，复用既有导航协议及局部函数。

仅装饰差异无需重排，其他样式当前保守重排；纯颜色/背景缓存优化、背景透明层、多字体失败、RTL 分层间距、位置恢复及重入/取消需后续证据。未增加公共事务层，未新增或运行测试。
### 示例需求核对：目录可用性

FR-DEMO-01 至 07 加入 07 核对表。原 EPUB 目录无正文目标行改为禁用并有文字说明，控制面板未就绪/忙/空目录时禁用目录入口；保留按需读取目标及失败提示。更新目录对话框绑定打开时的书籍实例，阻止旧界面修改替换后的会话，全部逻辑留在宿主。

目录启用状态、辅助服务禁用语义、换书后旧对话框、来源失败/恢复与文件授权释放仍待行为证据；示例入口数量或构建成功不能代表公开 API 接入与生命周期验收。未新增或运行测试。
### 生命周期质量核对：网络宿主退出

网络宿主停止改为先撤销字段归属，再取消/关闭，finally 继续释放旧来源；新来源未安装前若停止失败则清理新对象。网络来源关闭先清空登记，锁外独立断开各连接，普通异常不阻止剩余清理。复用既有序号和来源所有权，不增加通用会话管理层。

Debug 构建通过，未新增或运行测试。07 新增第5节质量属性的逐条证据映射；慢网络/退出/取消/重开和最终连接释放实际行为、设备资源/帧时间、辅助服务及目标矩阵仍待验证。
### 一致性核对：网络标题属于正文版本

网络章摘要 v2 纳入目录提供的标题与响应字节，补齐合成 HeadingBlock 内容变化的修订标识；长度前缀/精确 UTF-16 防拼接歧义及替代编码，响应摘要分块检查取消，返回前检查来源关闭。旧修订按既有失配契约处理，未新增库 API 或通用摘要框架。

07 新增原始第6节九项一致性规则映射；机制存在不代表运行场景通过。标题更新、旧标注/位置恢复、并发重载和取消需要行为证据，未新增或运行测试。
### 页面扩展切换：统一失效与延后任务合并

页面工厂/块布局提供者切换共享内部配置失效入口，回调前记录代次并在中断/失效后复核；视口失效同样检查。延后位置恢复增加本地序号，取消前移出 Job，post/协程/失败通知检查归属，LAZY 先登记后启动，旧 finally 不能清掉新任务。未增加公共类型或导航提交源。

Debug 构建通过，未新增或运行测试。07 补齐原始20项验收场景与所需证据的对应表；反复换工厂/提供者、回调重入、同代次重复恢复、离开/重挂窗口与迟到错误仍待行为验收。
### Java 消费方接入

新增无状态 ReaderListenerAdapter/ReadViewListenerAdapter，保留原接口和当前 JVM ABI。独立 Java Activity 编译消费公开模型/Widget/callback/取消/快照 API，菜单/Manifest/固定 ID 已接入；主线程请求序号、取消前撤销引用和同步完成句柄检查。快照只用宿主共用 Bundle 格式，未增加库持久化层。

Debug 构建含 compileDebugJavaWithJavac 已通过，未新增或运行测试。Java 示例编译证明这些具体调用可用，不证明全部公开 API 的 Java 易用性、取消回调恰好一次、实际旋转/后台恢复或消费 AAR 发布边界，07 保留证据范围。
### 大章内容指纹取消

来源返回后的内置文本指纹准备新增任务检查回调，在类型/块/字符串及4KiB批次检查取消，整章共享缓冲，完成后才缓存结果；自定义 null 成功值与未完成状态区分。repository 传入工作协程检查，模型不引入协程依赖，指纹序列/公共字段保持。

Debug 构建通过，未新增或运行测试。大段/多段取消、取消后相同对象重试、并行首次计算与资源测量仍缺证据；同步锁等待与来源/自定义计算不宣称即时可取消。
### 请求通知一致性

submit 主线程回调隔离普通宿主 Exception，诊断后不重试/改写终态；两类监听分发由 runCatching 改为 Exception 边界，致命 Error 不再被吞掉。补齐 ReaderRequest 完成/取消 KDoc：操作完成可能先于排队回调，取消幂等且不能回写终态。API 正文同步 StyleApplication 的真实字段与保守重排范围，避免历史设计与实现矛盾。

Debug/Java 编译通过，未新增或运行测试。完成/取消竞争、通知重入/异常/释放、致命错误传播与各领域替代结果仍需行为证据。
### Release 打包边界

生成 Release AAR 后检查 Manifest/classes.jar，库 Manifest 不含 Demo 权限/Activity，公开入口存在，类清单未发现 Demo/测试类。签名核对发现 protected readerScope 暴露内部任务控制，改为 internal，并把 CardReadView 初始化迁到公共 callback 导航；保留既有容器扩展/生命周期钩子，不增加新的任务 API。consumer-rules 注释说明直接引用和宿主反射责任，不添加全库保留规则。

Debug/Java 与 Release AAR 构建通过，未新增或运行测试。AAR 独立外部消费、依赖元数据发布、Java 合成入口边界、压缩/资源/XML、API21/设备行为仍待证据；原始目标保持，未宣告发布就绪。
### 位置通知派生动作归属

位置事件/导航钩子后续发布、预载和工具栏添加代次/打开序号/书籍或呈现实例/当前位置/状态检查。两个私有 dispatch 的内部谓词在每个监听器前复核，处理同代次内的重入导航；通用辅助事件和能力刷新不跟随已被替代的 location/state。统一提交点/公共结果/请求送达机制保留。

Debug/Java/Release 构建通过，未新增或运行测试。监听器内换书/force导航/模式切换/释放、后续监听顺序、旧Idle和选区/预载实际行为仍缺运行证据。测试阶段选择仍待用户回复，不将持续实现当作验收完成。
### 状态快照发布批次

publishState 内部序号防止同代次嵌套通知继续旧快照；入口即捕获会话/代次，clock/查询/占位绑定后检查，逐监听复核，占位类型使用捕获快照。reportReaderError 按会话/代次/错误对象归属隔离新错误或提交后的旧通知。发布序号不参与公开状态、导航或占位重试动作身份。

Debug/Java/Release 构建通过，未新增或运行测试。时间/占位回调重入、多个状态监听、同代次新错误及可见重试复用仍缺实际证据，当前测试阶段选择仍未收到用户回复。
### Java 来源接入统一

ReaderInput 工厂增加静态桥接，openStream 向 Java 声明 IOException；TXT/EPUB 默认 options 增加构造重载，无新输入类型。Java Activity 实际使用静态 fromStream 与二参 TXT 来源，增加模式切换/来源关闭/Bundle选择保存，错书快照不传入恢复。08接入指南同步 companion/受检异常和默认来源构造用法。

Debug/Java/Release构建通过，javap确认静态/受检异常及重载，未新增或运行测试。来源切换、取消与关闭、两种前后台/重建恢复、Java 文件异常和全部输入工厂仍需运行证据。
### 布局提供者边界

补齐分页器仅校验 BlockLayout 身份却未检查 fragment 身份的缺口，验证片段 ID/可选 offset/有限坐标，复用 LayoutFailed。provider 匹配/measure/fragment 前后和单位装页/padding拷贝增加取消检查，units每块取一次，明确不可变布局契约，不新增验证层或结果类型。

Debug/Java/Release构建通过，未新增或运行测试。错误片段、部分未知范围、自定义耗时不协作/取消、padding与分页/滚动一致性仍待行为证据，不承诺同步回调可被强制打断。
### 内置文字测量的行单位准备

把行单位生成移到 TextBlockLayoutProvider 测量阶段并逐行检查取消，冻结后检查再构造布局；布局对象不保存取消检查/context，几何与原文偏移保持。补上分页器之前的库内可取消循环，未新建调度层。

Debug/Java/Release构建通过，未新增或运行测试。StaticLayout单次平台调用仍非协作可取消，超长段落/多行取消及缓存持有情况仍需行为/资源证据。
### 内容缓存接纳的主线程工作

内容估重从 cache.put 的主线程遍历移到来源工作阶段，仓库私有 PreparedContent 承载内容/捕获估重，cache.peek/get 仍返回公开原模型。RetainedSize 取消重载用于来源与分页准备，逐块/getter前后检查；未知/负/超预算规则保留，公共ContentBlock文档明确getter工作线程约束。

Debug/Java/Release构建通过，未新增或运行测试。大量块的实际发布响应、getter异常/未知/取消与预算淘汰、显示工作集和设备内存仍需证据。
### 大目录状态查询

不可变目录/完整页索引各自建立私有ID映射，协调器章节存在性/当前章/完整索引位置反查改为查找。部分全书页号回退移除临时前缀列表及重复目录扫描，保持逐前章确认已知分页；不新增自动索引或公共接口。映射只存元数据，与快照一起替代/释放。

Debug/Java/Release构建通过，未新增或运行测试。重复ID/顺序重载/版本失配、Long前缀/二分映射、大目录响应与映射资源实际表现仍缺证据；部分索引回退仍为线性，不声称全部查询O(1)。
### 默认布局锚点与文本范围的边界一致性

分页器默认章节布局在 pageForAnchor/rangesForText 复用现有字符边界工具，拒绝文本长度内但位于字符内部的端点。两种呈现共用此入口，不增加公共层；非文本零偏移、合法文本末尾及已有拒绝/回退结果保留。

Debug/Java/Release构建通过，未新增或运行测试。代理对/组合字符、文本末端和跨块范围、分页/滚动导航、打开回退及各平台复杂字素行为仍需运行证据；自定义 ChapterLayout 边界由扩展实现负责。

### 连续片段的 Android 行高约束

ContinuousFragment 构造验证正文高度为正有限值，段后间距非负有限，实际 Float 合计高度为有限值且不超过 Int.MAX_VALUE。比较时转为 Double，避免整数上限转 Float 后变成 2^31。滚动行高不能依赖 toInt 的饱和转换静默缩短布局；默认分页器构造失败沿既有 LayoutFailed 处理，自定义布局同样须提供合法行几何。构造/字段签名不变，不增加布局验证服务。

padding 包装捕获一次 provider 的 units，估重和逐单位变换共用该列表，避免逐行重新调用宿主 getter；冻结输出、取消检查和原几何算法保留。Debug/Java/Release构建通过，未新增或运行测试；极大间距/边界舍入、自定义来源错误及连续滚动实际行为仍待验证，未宣称全章累计浮点精度或性能已达标。

### 默认布局的按块页锚点索引

分页工作阶段随已验证片段登记 blockId→(页号、起止offset) 列表，完成后冻结各列表并复核取消。默认 pageForAnchor 在原内容范围/字符边界检查后只遍历目标块条目，保留片段源顺序、未知offset按0及最后匹配页回退；不假定自定义offset连续有序，因此没有使用会改变重叠片段语义的二分。索引不引用正文片段/任务回调，不新增公开对象或位置提交源。

布局估重增加每片段64字节及每索引块128字节，未知估重仍不接纳缓存。源块查找仍线性，字符边界仍沿平台规则，长块自身的页条目仍线性；这不是所有锚点查询常数时间或设备性能达标的声明。padding包装最新产物的字段检查未发现context/取消回调字段，构造参数不等于长期持有。

本轮 Debug/Java 编译及 Release AAR 构建通过，仅支持可编译结论，未运行测试。

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

本轮 Debug/Java 编译及 Release AAR 构建通过；未运行测试，格式/完整性与持久复用行为仍未验收。

### TXT 索引自描述目录（formatVersion=2）

私有索引格式升级为2，正文记录后追加书籍ID/标题/正文revision、按原顺序的章节ID/标题/起止位置/记录数/章摘要；最后44字节固定尾部为目录起始offset、目录SHA-256和尾部magic。构建按整文件maxIndexBytes预检目录/尾部开销，写完同步文件，再丢弃构建目录并从文件恢复当前快照。目录SHA先于可变长度解码验证，恢复检查总长度/版本/书籍身份、章节数量/唯一ID、连续边界/记录数和目录终点。

目录字符串按长度前缀的精确UTF-16码元保存，避免writeUTF的64KiB限制及替换宿主标题中的码元；正文/块ID仍使用原严格UTF-8记录。目录读/写/摘要循环按8KiB检查取消，长度受目录边界和整文件预算限制，结束构造后复核取消；文件同步和数组分配不保证抢占。

本项替代前轮格式1及“章摘要仅在内存”的记录：当前章摘要和目录已序列化，但文件仍由临时OwnedSourceFile管理、close删除，每次loadBook仍重读输入。没有输入/配置缓存键、持久条目发布和共享租约；跨进程索引复用继续待实现。目录校验摘要用于识别损坏，不是对不可信文件的真实性签名或缓存命中证明；后续必须验证输入与配置身份。

本轮 Debug/Java 编译及 Release AAR 构建通过；未运行测试，自描述目录和持久复用仍缺动态验收。

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


### 自定义文本复用现有职责（2026-10-07）

TextContentBlock只声明稳定原文，不拥有导航或布局状态。TextBlockLayoutProvider支持该接口（version=2，宿主提供者优先）；TextSelectionResolver、AnnotationStore、PaginationEngine与AccessibilitySourceTraversal按同一text处理原文。章节blockId映射、字符边界、布局几何、导航事务和取消路径保持共用。图形仍沿ContentBlock与整块描述契约。

AttributedTextBlock示范宿主来源标签和持有大小，CustomContentActivity混合普通文本/自定义文本/图形。自定义附加数据不能由text推断缓存等价，ChapterContent未放宽内置指纹范围；宿主更新内容revision/提供者version。构建通过，未新增或运行测试，行为验收仍按07。


### 布局单位与片段共用偏移检查（2026-10-07）

PaginationEngine沿单一private validateOffsets检查单位及片段非负/有序的可选偏移，TextContentBlock另限text.length。单位在打包前检查，片段在加入分页/滚动及锚点列表前检查；取消仍在逐单位/片段位置处理。没有新服务/状态/提交路径，错误仍由协调器转LayoutFailed。该数值检查不承担逐行Unicode分段或验证宿主绘制内容。


### 可见描述校验复用选择原文（2026-10-07）

TextSelectionResolver增加matchesBlockRange，按当前章节检查范围长度、TextCharacterBoundaries与regionMatches，不分配第二份子串。BodyAccessibilityHelper.selectionRange通过ReaderBodyView.accessibilityMatchesText获得纯校验结果；分页/滚动绑定当前快照，按候选/代次/解绑状态拒绝旧绑定，回收清除回调。无来源描述仍局部移动，原文选择及Source边界须成功匹配。

校验未使用navigationBusy拒绝条件，避免正在准备离屏目标准备时原焦点有效性闭包失效；不改变选择/导航的提交门禁。Debug/Java/Release构建通过，未新增或运行测试。


### 滚动绑定身份校验（2026-10-07）

RowsAdapter以私有isBound(holder,row)共用当前适配器、位置和row引用检查，正文辅助回调及边界重试复用；相邻行起点直接取holder位置。去除归属检查中的contains/indexOfFirst整列表扫描，不增设全局行映射或绑定状态。等待更新的NO_POSITION与等值新行均拒绝旧动作，正常重新绑定后恢复能力；导航/来源提交职责不变。


### 本地 Maven 仓库包（2026-10-07）

库build.gradle.kts配置release发布组件/源码包、MavenPublication和文件仓库；bundleReleaseRepository依赖本地发布并压缩build/repository，条目顺序/时间规范化，未引入额外发布脚本或依赖版本副本。默认版本0.1.0-SNAPSHOT，通过ireadVersion覆盖；项目依赖开发方式保持。此项补齐AAR独立交付缺依赖元数据的实现，宿主外部消费证据仍未完成。


### 独立宿主源码与消费构建（2026-10-07）

新增examples/maven-host，独立settings/应用模块固定AGP/Kotlin工具版本，库仅implementation Maven坐标；仓库/库版本可用ireadRepository/ireadVersion属性覆盖。XML Kotlin页面和Java代码页面以公开API接入MemoryBookSource/TextContentBlock与生命周期。Debug构建和运行依赖报告通过，没有运行测试；编译消费已获具体示例证据，运行/压缩/完整矩阵仍待完成。


### 独立宿主 Release 压缩（2026-10-07）

examples/maven-host的release配置isMinifyEnabled/isShrinkResources及默认优化规则，assembleRelease实际执行R8/资源压缩与打包通过。没有通过保留全库或忽略全部警告回避优化。静态记录绑定未签名APK摘要、生成规则、ReaderView映射与压缩XML；运行/签名分发及全部宿主兼容仍待验收。


### 保存格式保持在独立宿主（2026-10-07）

examples/maven-host新增SnapshotBundles，由Java静态入口及Kotlin直接调用；两页面接入onSaveInstanceState/onCreate，恢复前重建MemoryBookSource。轻量格式显式版本/字段验证，不新增库存储控制器或对象序列化依赖。Debug及压缩Release构建通过，第一次R8文件占用重试通过；设备重建/格式异常仍未验收。


### 滚动累计表与局部几何（2026-10-07）

ScrollingBookPresentation内部Chapter.starts用DoubleArray，offsetFor读取同表起点；prepare/ScrollCandidate/visible/seek/positionAt与选区边界使用Double累计偏移，只有片段本地调用转Float或RecyclerView行内偏移转Int。二分中点用low+(high-low+1)/2，避免索引相加溢出。接口、缓存键、导航提交与来源身份不变，累计表随候选释放。构建通过，未运行测试。


### 滚动派生数值数据的工作准备（2026-10-07）

loadChapter取得布局/不可变连续片段后，以Default逐片段准备IntArray行高度和DoubleArray累计表，携带一次捕获的空章高度；主线程在layout/片段回调后及工作返回后复核book/generation/detach。ChapterGeometry只装数值数据，Chapter不再主线程map/累加高度；不用新作用域或缓存服务。逐片段ensureActive，数组分配/同步getter仍非抢占式。行重建/局部几何/呈现绑定继续原线程路径，不以此宣称所有准备开销消失。


### 连续锚点限定目标块（2026-10-07）

loadChapter的工作准备向ChapterGeometry加入blockChunks映射，构建时以原序号追加后冻结IntArray；Chapter.offsetFor通过blockId查序号并读取同一starts。保留自定义范围源顺序及fallback，不使用错误的范围排序假设，也不新建导航服务。索引属于当前候选，不跨会话保留。冻结循环检查取消，准备期临时列表/数组峰值另计。


### 连续行重建只组合分段（2026-10-07）

Chapter与只读行表在loadChapter工作阶段一起准备，构造取消参数仅用于初始化；Chapter仍是呈现私有对象，未将任何View调用移到工作线程。RowSegments保存当前章节/边界列表及小规模起点表，rebuild不再逐片段创建行或复制所有章节引用，seek使用章节列表身份定位起点。新增分段类型承担真实组合/访问逻辑，不是转发控制器。绑定身份/通知/候选淘汰/位置提交沿既有路径。


### 书籍错误发布共用入口（2026-10-07）

BookReadView私有publishReaderError承担lastError、逐ReaderListener归属与后续状态发布；reportReaderError只先决定来源/导航错误阶段，onPageTurnEffectError保留阶段后共用发布。移除效果路径的弱检查重复实现，不增加公开接口或事件服务，通用容器路径保持。


### 通用效果事件共用排队入口（2026-10-07）

BaseReadView私有postEffectEvent统一fallback与diagnostic的排队及代次/配置归属检查，复用既有dispatch逐监听谓词。效果/选项设置开始时推进序号，构造期初始配置也覆盖。无需增加事件控制器或公开状态模型；日志保留即时诊断，子类钩子和通用监听的职责保持。此变更限定通知时序，插件生命周期内部重入另需审查。


### 效果配置和安装归属协作（2026-10-07）

容器维护配置/内容归属，Manager维护安装/解绑归属；install只在调用栈内使用调用方谓词，返回可空内部选择，容器映射Superseded。复用已有两层对象，无新增控制器。旧实例解绑先选择none并捕获清理对象，未采用候选独立清理；稳定ID缓存避免生命周期重复访问宿主getter。abort终止active所有权在插件回调前，旧finish/degrade按安装身份清理。复杂回调事件与运行资源证据继续保留为未完成项。


### 效果清理诊断沿原配置派发（2026-10-07）

cleanup统一在插件调用前捕获归属、失败后核对，未采用候选传原请求谓词。diagnostic内部布尔标志只决定通知资格，日志始终保留；不另建诊断队列。BaseReadView的internal捕获方法供BookReadView效果钩子复用，逐ReaderListener与错误后状态发布纳入相同配置门禁，来源错误逻辑保持。捕获回调不保存到效果/事务对象。


### 运行效果沿捕获实例执行（2026-10-07）

transition复用captureConfigurationOwnership，捕获安装序号与renderer后逐同步/挂起边界核对；supports不再从可变effect读取实例，也不吞取消异常。ensureTurnActive同时检查active身份，遮罩回调后复核；无动画show与动画结束同用操作归属门禁。SupersededNavigation交既有导航事务处理，显示/提交职责保持，没有新增效果任务容器。


### 准备结果统一复核（2026-10-07）

navigate以token预留替代原先通知后再取token的顺序；旧取消/Idle回调后复核新归属。Preparing→prepare与prepare→所有结果均有活动/代次检查，Ready先登记finally清理；transitionState返回后复核再通知，过期普通异常映射Superseded。单次提交和候选finally沿原对象负责，未增加包装层。


### 候选清理与状态收尾独立（2026-10-07）

NavigationController的finally先dispose，普通异常经内部onCleanupError交容器，嵌套finally按token解除本任务。容器负责日志/排队/逐监听归属，BookReadView模块内部钩子复用publishReaderError且保留phase；不增加公开SPI回调。Error传播，旧清理不覆盖新pending。PreparedRead KDoc明确返回Ready后的清理责任及已转移资源归属。


### 通用适配器绑定序号（2026-10-07）

BaseReadView增加私有adapterBindingSequence，安装与detach清所有权前推进。Host捕获序号+created身份，导航/位置/错误/能力入口复核；同实例重绑不再复活旧Host。notifyNavigationCapabilitiesChanged复核代次/实例/绑定并复用逐监听dispatch。沿现有容器承担绑定职责，无新Host包装层或公开API。


### 已绑定View的解绑责任（2026-10-07）

BaseReadView私有adapterContentView保存绑定时实际View；detachAdapter清引用/绑定所有权后调用旧detach，finally移除捕获View，并检查parent及新绑定是否接管同一View。无需新增绑定包装类；异常保持传播，创建/绑定回调完整重入和release逐项异常收尾仍是后续工作。


### 永久释放共用异常聚合（2026-10-07）

新增模块内部releaseReaderResources函数承担实际独立步骤执行和普通异常聚合，BaseReadView与BookReadView共用；不是转发控制器。容器先撤销操作/监听权限，书籍先结束打开序号/阶段，再交各所有者清理。普通异常后继续、最后抛首cause及suppressed；致命Error立即传播，失败步骤不自动重试。适配器捕获View的finally沿原实现负责。


### 窗口解绑与永久释放共用清理规则（2026-10-07）

临时onDetachedFromWindow复用内部清理函数并捕获旧lifetime，Android super收尾置于finally；保留会话和监听，未混为永久release。书籍先撤销relayout引用/设置等待阶段，再逐项清时钟/选区/旧job；coordinator暂停有代次与打开序号检查，通知中新会话不被旧暂停影响。完整窗口绑定重入另需继续审查。


### 恢复回调复核现有归属（2026-10-07）

窗口恢复逐边界核对lifetime、代次、适配器及绑定序号，书籍resumeWork/restoreVisiblePosition间核对打开序号；尺寸失效与viewport通知后复核再调子类钩子。复用现有Widget字段，不增加生命周期协调器。适配器创建/绑定事务和实际窗口恢复证据仍待补齐。

### 宿主仿真候选与内置效果边界（2026-10-08）





