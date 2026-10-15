# 阅读面板与排版 API

## Demo 面板

参考旧 ChapListFragment、TypesettingFragment 和 SettingsFragment：

- ContentsSheet：32dp 顶部圆角、目录标题、400dp RecyclerView，定位当前章节，选择后关闭并导航。TXT 与 EPUB 层级目录统一使用该面板。
- TypographySheet：可滚动底部面板，原样式的灰色分段项、白色游标、双列数值控件和六区域四向边距；增加字体、页眉/页脚、缩进及行距倍率。
- ReadingSettingsSheet：原样式的翻页模式与阅读主题分段项，补充 iBook/Google/卷页、减少动态及阅读工具。滚动使用 ReadingMode，与翻页效果区分。
- ReaderBottomSheets：统一窗口、压暗和底部 inset 策略；ReferenceSliderView / SegmentedChoiceView 为通用视觉控件，ChapterProgressView 添加章节语义。

源码位于 Demo 的 ui/reader/{contents,typography,settings,menu,controls}。Library 不依赖这些控件或 Material。ReaderActivity 管理生命周期、书籍绑定和成功样式的保存；失败时面板重新读取实际样式。

## 原参数与当前 API

### 默认排版与旧 Demo 对齐

Demo 的统一默认样式由 `res/values/reader_defaults.xml` 的 `ReaderDefaultStyle` 提供，`reader/ReaderDefaults.kt` 管理默认字体选择和偏好迁移版本；内置书籍、本地 TXT、网络书籍和卷页示例均使用该样式。默认值在 Widget 构造时读取，不通过异步请求覆盖恢复状态。依据旧项目的 `basic/ReadStyle.kt`、`demo/ReadActivity.kt`、`libreadview_item_read_page.xml` 和默认白色主题逐项迁移：

| 参数 | 默认值 |
|---|---|
| 正文 / 标题字号 | 20sp / 28sp |
| 正文 / 标题字体 | linja-waso / linja-waso-Bold；原字体以 Demo font 资源打包 |
| 标题对齐、首行缩进 | START（左到右为左对齐）、2em |
| 标题 / 正文字距 | 0dp / 0dp |
| 标题 / 正文行距 | 倍率1，各额外10dp |
| 段距 | 18dp |
| 页面边距（始、上、末、下） | 20、0、20、0dp |
| 页眉边距 | 0、8、0、20dp |
| 页脚边距 | 0、20、0、8dp |
| 内容外边距 BODY | 0、10、0、10dp |
| 标题边距 | 0、0、0、72dp |
| 正文内边距 CONTENT | 全0dp |
| 额外标题后间距、视口/段落内边距 | 0dp，避免与旧标题底部72dp叠加 |
| 页眉 / 页脚字号、字体 | 11sp，系统默认无衬线 |
| 默认白色主题 | 背景#F6F6F6、正文#1E1A1A、标题及页眉页脚#000000 |

标题行距新增 `TitleStyle.lineSpacingMultiplier/lineSpacingExtraDp`、`ReaderStyles.withTitleLineSpacing` 和 XML `ireadTitleLineSpacingMultiplier/ireadTitleLineSpacingExtra`，接入 StaticLayout、分页、选区与状态保存。缺少新键的旧状态沿用当前基准值。

2026-10-14 调整：当前阅读根布局已经避让系统栏，页眉顶部不再沿用旧64dp，页脚底部不再沿用旧20dp，两者均改为8dp。内侧边距与正文参数继续参照旧 Demo。

Demo 偏好 typographyRevision=4（版本3迁移导航边距，版本4迁移橙色选区）；版本2冷启动时只迁移页眉顶部和页脚底部，保留其他排版调整；更早记录沿用先前的默认排版迁移策略。输入/效果偏好保留，此后用户调节的排版照常保存。Widget 自身的通用默认值不由 Demo 预设改变。

这里对齐参数和字体；旧引擎逐字符布局，新引擎使用 StaticLayout，字体度量、自动换行及分页未声明像素一致。旧页脚左右分开的进度与时钟，当前仍使用合并的 PAGE_PROGRESS_AND_TIME 内容。

| 参数 | 当前 API | Demo 范围 |
|---|---|---|
| 六区域四向边距 | padding/withPadding + PaddingRegion | 0..100 dp；标题底部0..300 dp |
| 标题、正文字号 | textSizeSp/withTextSizeSp + TextRole | 10..60 sp |
| 标题、正文字距 | letterSpacingDp/withLetterSpacingDp | 0..20 dp |
| 额外行距、段距 | withLineSpacing、withParagraphSpacingDp | 0..100 dp |
| 标题对齐 | withTitleAlignment | START/CENTER/END |
| 页眉/页脚字号 | withTextSizeSp | 8..30 sp |
| 缩进、行距倍率 | withFirstLineIndentEm、withLineSpacing | 0..4 em；0.5..3倍 |
| 字体 | withFont + ReaderFont | 默认、无衬线、衬线、等宽 |
| 页眉/页脚高度与可见性 | withChromeHeightDp/withChromeVisibility | 滑条0表示自动高度，API使用null；显示/隐藏 |
| 页眉/页脚对齐与内容 | withChromeAlignment/withChromeContent | 起始/居中/末尾；章节、进度、时间等 |
| 各文字区域颜色 | textColor/withTextColor | API接收ARGB颜色；Demo主题统一设置 |
| 段落内边距 | withPadding(PARAGRAPH, ...) | 0..32 dp |

滑条范围继承旧示例，不是 API 限制或推荐值。正文建议16..24sp，标题22..32sp；字距0..4dp，额外行距0..8dp，段距4..20dp，边距0..32dp。过大边距/高度可能造成不可用正文区域，由 applyStyle 按原事务规则失败/回滚。

## 公共样式读写

ReaderStyles 的 @JvmStatic 方法供 Java/Kotlin 调用，with* 返回不可变 ReaderStyle，需通过 BookReadView.applyStyle 提交；不会绕过现有重排、锚点恢复和校验。TextRole 包括 BODY、TITLE、HEADER、FOOTER，统一字号、字体、颜色；固定和相对字距仅支持 BODY/TITLE，其他角色传入字距 helper 会明确报参数错误。

| PaddingRegion | 实际字段 |
|---|---|
| PAGE | page.padding |
| HEADER / FOOTER | header.padding / footer.padding |
| BODY / CONTENT | body.containerPadding / body.contentPadding |
| TITLE | title.padding |
| BODY_VIEWPORT | page.viewportPadding |
| PARAGRAPH | body.blockPadding；拆分页不重复添加 |

区域按当前 Widget 定义，不直接复刻旧内部成员。边距使用逻辑 start/end；字号为 sp，缩进与相对字距为 em，固定字距/行距/段距为 dp。标题对齐使用起始/末尾名称。

```kotlin
var next = ReaderStyles.withPadding(reader.style, PaddingRegion.PAGE,
    EdgeInsets(startDp = 20f, topDp = 12f, endDp = 20f, bottomDp = 12f))
next = ReaderStyles.withTextSizeSp(next, TextRole.BODY, 20f)
next = ReaderStyles.withLetterSpacingDp(next, TextRole.BODY, 1f)
next = ReaderStyles.withLineSpacing(next, 1.4f, 2f)
reader.applyStyle(next) { completion -> /* 成功后保存宿主偏好 */ }
```

BodyStyle/TitleStyle 新增可空 letterSpacingDp，非空优先于 letterSpacingEm；null 恢复 em，withLetterSpacingEm 会清除固定覆盖。TextPaint 用 dp*density/textSizePx 转换，StaticLayout、分页、绘制和选区共用几何。XML 新增 dimension 属性 ireadBodyLetterSpacingDp / ireadTitleLetterSpacingDp，可设为1dp/2dp；原 float 属性仍为 em。

Library SavedState 与 Demo 偏好保存新字段；旧记录缺少新键时恢复 em 行为。字段追加在构造参数末尾，已有源码可继续使用默认值；完整 JVM 构造/copy 签名有变化，旧编译宿主须重编译，不声明二进制兼容。

滑条在松手时提交一次样式；取消手势或控件离开窗口时恢复原值。设置面板在请求结束后读取实际模式和动画选项，拒绝修改时恢复选择。原先无入口的翻页效果 AlertDialog 已移除，统一由设置面板提供操作。

## Demo 系统导航栏

小说阅读页面默认隐藏系统底部导航栏，状态栏保持可见。设置面板“系统底部导航栏”提供显示/隐藏选择，并通过独立 reader-window 偏好保存。ReaderSystemBars 使用 WindowInsetsControllerCompat，只控制导航栏；从底部滑动可临时唤出系统按钮或手势区域。阅读窗口重新获得焦点及四个底部菜单窗口均应用同一偏好；主页维持原系统栏行为。根布局继续处理系统栏 inset，导航栏隐藏后释放底部空间。此项属于 Demo 宿主窗口配置，未增加 Widget API。

## 构建范围

Debug APK、Release AAR 和本地 Maven 仓库包构建通过，JVM API 清单由新 AAR 刷新。未新增或运行测试；设备外观、字体缩放、全部边界组合及生命周期恢复尚未验收。
## 导航栏隐藏后的空间处理（早期实现，已由下述全屏方案替代）

Demo 的 ReaderSystemBars 显式关闭 decor 自动避让，在窗口 content 根处理可见系统 inset：顶部为状态栏/挖孔，底部为显示的导航栏与键盘高度的较大值；隐藏导航栏不保留其稳定高度，临时滑出的导航栏覆盖而不缩小正文。处理后的系统 inset 清零后再下发，避免子容器及 Material BottomSheet 重复添加底部占位。

ReaderActivity、NetworkActivity、CurlActivity 及四个底部面板共用此策略，移除阅读根 fitsSystemWindows；底部面板关闭手势 inset 的 peek-height 占位，一级菜单底部自身 padding 从24dp降到8dp。主页维持原窗口策略。此修改只在 Demo 宿主，不修改库排版边距。仅 Debug APK 构建验收，设备效果未验收。

## 全屏 ReaderView 与透明系统栏（2026-10-14）

此前外层根 padding 和状态栏色块方案已移除。ReaderActivity 中 ReaderView 铺满窗口，包括状态栏后方，页面背景由阅读模板本身绘制；系统栏透明，只根据主题决定图标明暗。没有额外顶底色块或 content.overlay。

Demo FullScreenPageFactory 包装默认或自定义模板，给模板内部 page.padding 叠加系统安全范围，使正文/页眉/页脚避让，ReaderView 和页面背景仍是全屏。安全值不写入阅读样式偏好。系统 inset 改变时重新安装模板以触发原有重排；隐藏导航栏时不加底部系统 padding。状态栏继续可见，不等于隐藏状态栏。

底部弹窗透明区显示后方全屏阅读页，只有弹窗自己的控件区域避让状态栏/键盘。Network/Curl 带独立工具条的调试入口仍使用 decor 自动避让，不套用主阅读页的全屏模板。主页行为保留。仅 Debug APK 构建通过，设备布局尚未验收。

