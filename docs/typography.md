# 阅读面板与排版 API

> 当前Demo用六位置模板配置内容，旧单行内容/整行对齐不再作为Demo调用路径；底层ReaderStyles样式API仍保留，见[模板专题](page-chrome.md)。

## Demo 面板

参考旧 ChapListFragment、TypesettingFragment 和 SettingsFragment：

- ContentsSheet：32dp 顶部圆角、目录标题、400dp RecyclerView，定位当前章节，选择后关闭并导航。TXT 与 EPUB 层级目录统一使用该面板。
- TypographySheet：可滚动底部面板，原样式的灰色分段项、白色游标、双列数值控件和六区域四向边距；增加字体、页眉/页脚、缩进及行距倍率。
- ReadingSettingsSheet：原样式的翻页模式与阅读主题分段项，补充 iBook/Google/卷页、减少动态及阅读工具。滚动使用 ReadingMode，与翻页效果区分。
- ReaderBottomSheets：统一窗口、压暗和底部 inset 策略；ReferenceSliderView / SegmentedChoiceView 为通用视觉控件，ChapterProgressView 添加章节语义。

源码位于 Demo 的 ui/reader/{contents,typography,settings,menu,controls}。Library 不依赖这些控件或 Material。ReaderActivity 管理生命周期、书籍绑定和成功样式的保存；失败时面板重新读取实际样式。

## 原参数与当前 API

### 默认排版

Demo 的统一默认样式由 `res/values/reader_defaults.xml` 的 `ReaderDefaultStyle` 提供，`reader/ReaderDefaults.kt` 管理默认字体选择和偏好迁移版本；内置书籍、本地 TXT、网络书籍和卷页示例均使用该样式。默认值在 Widget 构造时读取，不通过异步请求覆盖恢复状态。默认参数为：

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

当前ReaderView本身全屏，FullScreenPageFactory在模板内部为内容增加系统安全边距；页眉顶部与页脚底部外观padding均为8dp，不沿用旧64dp/20dp。内侧边距与正文参数继续参照参考Demo。

Demo偏好保留当前typographyRevision=4的外观数据；旧revision2/3兼容分支已移除。模板六位置/返回按钮使用PageChromeCodec单独保存，不读取旧内容和整行对齐字段。Widget自身通用默认值不由Demo预设改变。

这里对齐参数和字体；旧引擎逐字符布局，新引擎使用 StaticLayout，字体度量、自动换行及分页未声明像素一致。当前Demo页脚使用显式三位置：左章内进度、中时间、右电量；不再靠旧PAGE_PROGRESS_AND_TIME单项内容表达布局。

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
| 页眉/页脚对齐与内容 | 库保留withChromeAlignment/withChromeContent底层API | Demo改用ReaderPageChrome六位置模板，见模板专题 |
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

Library SavedState 与 Demo 偏好保存新字段；旧记录缺少新键时恢复 em 行为。字段追加在构造参数末尾，已有源码可继续使用默认值；变更构造或 copy 签名时，宿主与扩展需一起重新编译。

滑条在松手时提交一次样式；取消手势或控件离开窗口时恢复原值。设置面板在请求结束后读取实际模式和动画选项，拒绝修改时恢复选择。翻页配置统一由设置面板提供。

## Demo窗口与模板

小说主阅读页的ReaderView填满窗口，默认隐藏系统底部导航栏、保持状态栏可见。ReaderSystemBars负责窗口策略；FullScreenPageFactory在默认模板内部增加可见系统栏/挖孔安全边距，导航栏隐藏后不保留旧底部占位。底部面板使用宿主窗口策略，不另造一层常驻导航。

这是Demo的窗口配置，不给Widget内置某种Activity或系统栏政策。NetworkActivity等独立示例可使用各自窗口布局；不要把小说主页面的全屏声明当成所有示例都采用同一布局。
