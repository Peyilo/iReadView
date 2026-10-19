# 当前状态与验证边界

更新：2026-10-19。完整行为目标保留在[原始需求](01_PRODUCT_REQUIREMENTS.md)，以下不是全部验收通过声明。最近多书源改动的证据范围见下文。

## 功能现状

| 领域 | 源码已有 | 仍需工作/证据 |
|---|---|---|
| Widget/呈现 | 通用容器、书籍层、独立分页与连续滚动、模式切换 | 窗口/字体/模式变化中的位置、取消、重入、生命周期实机回归 |
| 来源/正文 | 内存、TXT、网络章节；ReaderBodyView 抽象，TextReaderBodyView 与自研 EpubReaderBodyView；统一 Widget 与模板，无 Readium 运行时 | DOM 标注已接入；复杂 EPUB/固定版式/RTL/旧 WebView 矩阵未完成，详见专题 |
| 导航 | 统一位置提交、前后页/章节/边界、快照恢复、显式全书页索引 | 全书索引取消/失效、多消费者、目录更新、不同输入的一致性 |
| 样式/模板 | 多层边距、字体/字距/行段距、背景；任意View SPI与六位置默认模板 | 六位置、按钮、电量、可见性、重建及自定义组合的设备验收 |
| 选择/标注 | 段落选择、同章跨页拖动/代理、已有高亮整范围重选、紧凑菜单、统一书签/高亮/划线 | 最近行首/行尾修补未设备验收；Unicode、RTL、重叠与边缘翻页系统回归 |
| 翻页 | none、slide、cover、iBookSlide、iBookCurl、googleCurl、pageCurl及自定义效果 | 用户视觉认可、所有极值/尺寸/方向、硬件帧率与长期资源趋势 |
| 无障碍 | 源映射、可见/离屏遍历、同章扩选及动作 | 复杂局部自定义语义未完成，TalkBack/SwitchAccess未完成实测 |
| 发布/Demo | AAR、本地Maven ZIP、Kotlin/Java/XML消费；真实TXT/网络、模板配置UI | 当前改动下的独立宿主回归、正式发布/升级流程与完整设备矩阵 |

## 保留的有限证据

- 2026-10-19 正文/选区/动画扩展：新增ReaderBodyRenderer、ReaderSelectionRenderer、ReaderSelectionController及工厂，通过ReaderPresentationComponents注入；新增宿主BookPresentationFactory覆盖书源/默认后端，翻页继续独立消费PageSurface。三个内置呈现路径共用选区控制器SPI；配置版本变化真正替换Presentation，纯绘制配置保留章节布局，基类最终release保证组件关闭。最终27项JVM测试、Debug/AndroidTest和两库Release构建通过，扩展与选区设备专项 `OK (4 tests)`（32.353秒）；手势/双页独立复测 `OK (2 tests)`（58.881秒）。早期联合测试出现156.5 ms卷页首帧和已修复的原生滚动边界失效，没有将失败整轮计为通过。API、生命周期迁移和证据见[扩展契约](EXTENSIONS.md)。

- 2026-10-19 统一三页缓存：新增 `ReaderPageCacheMode` 和主线程 `setPageCacheMode` / `pageCacheMode`，原生文本与EPUB默认三页，可运行时切回单页，Busy拒绝且位置不变。两者组合相同的备用页所有权缓存和空闲预加载器，Widget/正文不互相识别具体格式。27项JVM测试、Debug/AndroidTest与两库Release构建通过；8项联合设备测试（201.128秒）覆盖全部效果性能、双页显示、手势、选区和整章往返，最终滚动路径再通过4项缓存/选区回归（27.733秒）。普通文本动画页面准备中位数1.1–1.8 ms，卷页GPU长帧仍存在；原始数据和API边界见[分页缓存](PAGE_CACHE.md)。

- 2026-10-19 EPUB滚动选区跟随：选区背景和手柄按WebView实际滚动位移在原生回调中同步更新，JS几何携带采样滚动原点，收到旧坐标后重定位而非回跳；连续滚动保留完整行矩形并在绘制时裁剪。API36最终选区联合运行 `OK (3 tests)`（46.051秒），验证长按不松手、同UI调用中30次正反滚动即时位移、旧坐标返回校正及原两套统一选区流程。Debug/AndroidTest与EPUB Release构建成功。先前联合回归在两项翻页用例通过后模拟器断开，不计整轮通过。设计和测试范围见[滚动选区同步](SELECTION_AND_ANNOTATIONS.md#epub滚动时活动选区同步)。

- 2026-10-19 连续翻页当前页消失修复：新增双色EPUB设备用例在修改前稳定失败于slide第二次拖动（当前页颜色像素0），避免仅用无白闪采样判定两页正确显示。Page停放/显示切换使硬件录制缓存失效，重新准备后续动画表面。修复后三种滑动类效果连续前后三页共18次双页像素检查通过，与原手势/白闪、统一选区、ARM整章往返联合运行 `OK (5 tests)`（93.171秒）；Debug/AndroidTest APK与EPUB Release AAR构建成功。详见[当前页消失回归](GESTURE_AND_EPUB_HANDOFF.md#当前页消失的修复)，未重新声明全部性能或API21真机通过。

- 2026-10-19 手势响应与EPUB交接：保持240 ms动画配置，加入空闲邻页准备、API29+硬件显示列表、API33+GPU直接卷页和可选视觉交接SPI；避免INVISIBLE停止WebView绘制，目标正文就绪后撤除动画遮罩。最终联合设备运行 `OK (6 tests)`（193.17秒），覆盖21组性能、42次提交/取消探测、18组按住/反拖与416帧无白闪采样、GPU材质对照、ARM102页往返及统一选区；核心24项JVM测试、Debug/AndroidTest与两库Release构建通过。复杂卷页长帧与快速连翻准备等待仍存在，当前结果及原始数据见[手势响应及正文交接](GESTURE_AND_EPUB_HANDOFF.md)，不是全部问题或API21真机矩阵已经完成的结论。

- 2026-10-19 多效果性能对照：同原文文本/EPUB及ARM后半章共21组，全部7种效果均测prepare、start、update、draw与Window FrameMetrics；含42次真实拖动提交/取消。针对WebView重复合成/查询、章首DOM扫描、快照预算回退和pageCurl回读/阴影工作量优化。性能用例通过，最终45组GPU阴影像素对照+整章分页+两套选区回归 `OK (4 tests)`（37.934秒），核心24项JVM测试及两库Release构建通过。原始数据、方法和仍偏高的cover/iBook/pageCurl长帧见[完整性能对照](EPUB_TURN_PERFORMANCE.md)，不能宣称EPUB全部效果已追平文本或达到60fps。

- 2026-10-19 EPUB 翻页稳定性与复用：真实 ARM EPUB 第一章曾因 WebView 自动缩小视口卡在99/102页。固定缩放与分页舍入后，新增设备用例逐页走完102页、进入下一章、返回章尾并逐页退回章首；同时验证章内两份 WebView 复用、动画翻页、取消导航后继续翻页。与两套统一选区用例联合运行 `OK (3 tests)`（41.657秒），核心24项 JVM 测试通过，Debug/AndroidTest APK 与 EPUB Release AAR 构建通过。本机模拟器无动画正向翻页中位数51 ms，首个备用文档加载317 ms、跨章467 ms；这是导航调用耗时，不是帧率或真机性能结论。日志 `app/build/pagination-selection-device-tests.log`，用例：[EPUB 分页设备回归](../app/src/androidTest/java/org/peyilo/iread/demo/EpubPaginationIntegrationTest.kt)。API21/22帧同步兼容路径尚未经真实设备验收。

- 2026-10-19 选区行为统一：活动选区取消 WebView DOM Selection/ActionMode/CSS ::selection，文本与 EPUB 共用 Canvas 手柄绘制、命中与分页拖动控制器；滚动共用边缘停留/速度策略。getSelection/getSelectedText、复制、高亮、划线、注解导航和 Demo 持久化均接入格式无关契约。核心24项 JVM 测试全部通过（包含3项文档注解生命周期测试），两套 Android 设备用例全部通过：相同真实触摸分别验证文本和 EPUB 的完整段落选择、缩短、跨页扩选固定端点、旧动作失效、高亮新增/取消/序列化恢复/跳转、波浪划线/复制、重开书籍及滚动边缘扩选。Debug APK/AndroidTest APK、两库 Release AAR 构建通过。设备日志 app/build/unified-selection-device-tests.log 最终为 OK (2 tests)，本轮不是 API21/所有 WebView/复杂排版完整矩阵验收。测试源码：[统一选区设备用例](../app/src/androidTest/java/org/peyilo/iread/demo/UnifiedSelectionIntegrationTest.kt)、[文档标注生命周期](../app/ireadview/src/test/java/org/peyilo/ireadview/DocumentAnnotationLifecycleTest.kt)。

- 2026-10-19 自研 EPUB 正文接入：核心与 EPUB Release AAR、Demo Debug APK 构建通过；模拟器实际打开默认 ARM EPUB，验证正文图文、原书目录跳转、章节内分页与真实 DOM 快照、长按自定义菜单，并截获统一模板的翻页动画；修正封面图片撑大视口后复查封面完整显示，真实 TXT 显示与进度恢复已回归。组件树确认两个正文实现位于同一个 ReaderView/DefaultChromeRoot 下。原 EPUB Readium 运行时和 Fragment 专属接入已移除。API21 真实设备矩阵及完整 EPUB 兼容性未验收，详见[正文设计](READER_BODY.md)与[EPUB 边界](EPUB_ENGINE.md)。

- 2026-10-18 多书源增量：完成[目标与设计](CONTENT_SOURCES.md)、格式无关图片/链接模型、EPUB Flow 入口、图片默认排版、两种模式的链接命中和 Demo 接入。修复基线 BookReadView 给 ReaderPageClock 多传参数的编译阻塞（时钟已通过呈现层通知模板）。Debug APK 与库 Release AAR 构建通过；没有新增/运行软件测试或设备交互验收。跨图片选择、图片标注和无障碍链接动作尚未支持。

- 最近模板/选择实现曾通过Debug、Release与Maven包构建；Demo最后的直接模板接入只重建Debug。日期为2026-10-15前后会话记录，不是2026-10-18重新验收。
- 章尾空白分页：新增4项测试修改前3项失败；修正后与原3项分页测试共7项通过。它只证明对应纯布局场景，不证明所有真实章节无空白。
- iBook数学与阴影：保留generated中的两个带日期报告。24组固定姿态离屏检查旧方案范围外像素78,666，新方案0，保留合法阴影像素11,383，边界允许1像素舍入；不代表硬件渲染/所有姿态已通过。
- 独立Maven宿主早期Debug/压缩Release构建及API36有限交互观察保留在示例README与历史产物摘要中。旧哈希不能证明当前未提交改动已通过该验证。
- 最近行首手柄、行末命中、高亮整范围重选、六位置模板、电量监听和Demo重建仅有构建证据。

## 已确认的限制

- TextRange限制同一章节；不支持跨章范围标注/选择。
- ReaderAnnotation/RangedAnnotation是sealed类型，目前不能由第三方任意扩充类型；笔记类型未实现。
- 未提供独立全文搜索、Compose包装、朗读跟随能力。
- 内容修订和quote失配会明确失效；未提供通用基于上下文的标注重新定位。
- EPUB 自研正文已经保留 XHTML/CSS/图片，DOM 持久高亮/划线与统一选区已接入；完整固定版式、RTL/竖排、混淆字体、非 spine 导航和跨章节无缝滚动等仍未完成，不能宣称完全兼容。
- 在线正式发布与完整API兼容性回归流程未完成；库现为0.1.0-SNAPSHOT开发交付。

## 建议而非已授权任务

先收敛选择/分页/导航稳定性和性能证据，再考虑搜索、笔记及标注扩展、重定位、跨章选择、Compose、正式交付。须由下一轮用户选定具体任务；不要将建议自动扩大为实现计划。

## 后续验收重点

按01的FR和场景检查，尤其：失败/取消/回调重入、换书/目录更新、正文视口变化、无/未知页数、标注失效、拖动中旋转/切模式、RTL/复杂字素、低内存/低端机、高刷新率、后台恢复、真实辅助服务。构建、单元、离屏、完整设备结果分别记录，不把“代码路径存在”升级为运行通过。
