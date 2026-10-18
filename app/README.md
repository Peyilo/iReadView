# Demo 工程结构

`:app` 是 iReadView 的示例宿主。类名直接描述职责，不再使用 `Demo` 前缀；包根 `org.peyilo.iread.demo` 已表明它们属于 Demo。应用 ID 和资源 namespace 保持该值，源码的子包按职责组织。

## 源码目录

以下目录位于 `src/main/java/org/peyilo/iread/demo/`。

| 目录 | 职责与主要类 |
|---|---|
| `ui/home` | 启动菜单 `HomeActivity`，按按钮选择不同 Demo |
| `ui/reader` | 阅读入口 `ReaderActivity`，组合来源、阅读器和宿主面板 |
| `ui/reader/panel` | 标注与页索引面板 `AnnotationPanel`、`PageIndexPanel` |
| `ui/reader/menu` | `ReaderMenuController` 管理底部菜单与导航，`ChapterProgressView` 绘制双游标章节进度 |
| `ui/reader/typography` | `TypographySheet`，原样式的边距与文字参数面板 |
| `ui/reader/contents` | `ContentsSheet`，目录列表底部面板 |
| `ui/reader/settings` | `ReadingSettingsSheet`，效果、主题和阅读工具 |
| `ui/reader/controls` | `ReferenceSliderView`、`SegmentedChoiceView`，共享参数控件 |
| `ui/effects` | `CurlActivity`，真实小说的翻页效果观察与页装饰交互 |
| `ui/container` | `ContainerActivity`、`CardReadView`，非书籍容器示例 |
| `ui/examples` | `NetworkActivity`、`TextSourceActivity`、`CustomContentActivity`、`JavaReaderActivity`；Java 页装饰辅助类与其宿主放在一起 |
| `data/source` | `BiqugeBookSource`、`NetworkBookSource`、`HttpsBookClient`、`TextFiles`、`ProgressTextBookSource`，宿主来源、网络传输与文件处理 |
| `data/storage` | `ReadingStorage`、`AnnotationStorage`、`ReaderPreferences`、`UriGrantStore`，宿主记录、配置及文件授权 |
| `data/state` | `SnapshotState`，轻量阅读快照的 Bundle 编解码 |
| `reader` | `ReaderEffects`、`ReaderThemes`、`CustomPageFactory`、`CustomPlaceholderFactory`、`CustomSelectionToolbarFactory`，通过公开 API 配置和扩展阅读器 |
| `content` | `AttributedTextBlock`、`DividerBlock`、`DividerLayoutProvider`，自定义正文数据与布局提供者 |

`TextFiles` 与 `ProgressTextBookSource`、`DividerBlock` 与 `DividerLayoutProvider` 分别放在独立文件中。仅由一个类使用的私有适配器或页面实现可留在所属文件内。

## 资源与依赖边界

- `src/main/AndroidManifest.xml` 使用 Activity 的完整类名注册入口。
- `src/main/res` 放 Demo 页面与样式；子包使用根 namespace 的 `R`。
- `src/main/assets/txts` 放真实小说样本，仅打包进 Demo APK。
- `src/androidTest` 保留既有集成测试，入口引用随源码移动同步更新。
- Demo 通过 `:app:ireadview` 的公开 API/SPI 使用阅读器，来源、持久化、授权和示例界面属于宿主。

包与类名调整不修改书籍身份、来源版本域、偏好键和快照数据键，已有记录继续按现有恢复规则读取。

## 启动菜单与阅读流程

1. **阅读内置书籍**：打开 assets 中的《妖精之诗》，此入口明确选择内置小说，不被最近的外部文件覆盖。
2. **选择本地 TXT**：先打开系统文件选择器；选择成功后进入 ReaderActivity，传递 URI 和读取授权，后台识别编码、解析目录。取消选择仍停留在菜单。
3. **网络书源加载**：进入 NetworkActivity，直接沿用旧项目 BiqugeBookLoader 的站点与规则，默认打开 https://www.yuyouku.com/book/185030/ 。Widget 请求某章时再加载正文，提供目录跳转、翻页效果、取消和重试。

“更多 Widget 示例”包含翻页/页装饰、独立容器、自定义正文、TXT 配置和 Java 接入。读取界面仍保留来源切换及设置操作。

主页采用暖白背景、深绿内置书籍主卡片及独立入口卡片。封面和入口图标为本地矢量资源；整张卡片可点击，并提供按压反馈、键盘焦点与按钮无障碍语义。布局使用可滚动容器和随文字增长的卡片高度，保持三个主要入口与更多示例的层次。

内置书籍与本地文件的阅读页仅使用书页自身的页眉/页脚。点击正文中部弹出底部菜单，点击外部、下滑或返回键关闭。菜单照搬旧 Demo 的两排布局：上一章、双游标章节进度、下一章，以及目录、排版、设置。布局为 res/layout/view_reader_menu.xml，图标沿用旧项目的矢量路径，主题与旧项目一致。阅读模式、书籍切换等操作收纳在设置中，不另加菜单行。

BiqugeBookSource 将旧 BiqugeBookLoader 的 BookLoader 职责适配为公开 BookSource：书名使用 article h1 的 ownText，目录使用 #chapters-list li a，正文使用 #txtContent；移除 div.gad2，展开 p 标签，再按 br 分段并转成纯文本。网络请求直接使用旧实现的 Jsoup.connect、浏览器 User-Agent 和 20 秒 timeout。章节模型、稳定身份、修订及 IO 调度接入当前 Widget；目录或正文缺失仍明确返回失败。

默认书源固定为原项目的站点与示例书籍，不自动切换其他站点。页面可以输入同站的其他书籍目录地址。网络取消会拒绝过期结果，已开始的 Jsoup 请求受其 20 秒超时约束。NetworkBookSource/HttpsBookClient 保留为原 JSON 协议示例代码，当前网络入口仅使用 BiqugeBookSource。

jsoup 与支持 API21 的 NIO desugaring 仅添加到 Demo：参考[jsoup Android 接入说明](https://jsoup.org/download)和[desugar_jdk_libs 配置](https://github.com/google/desugar_jdk_libs/blob/master/CHANGELOG.md)。Library 不承担网络请求或 HTML 解析依赖。

## 构建

在项目根目录使用 JDK 17 和 Android SDK：

```shell
./gradlew :app:assembleDebug
```

Windows 使用 `gradlew.bat`。输出为 `app/build/outputs/apk/debug/app-debug.apk`。

目录、排版、设置均采用底部面板，详情与 Library 新增样式 API 见 [排版 API](../docs/26_READER_PANELS_AND_TYPOGRAPHY_API.md)。原六区域边距之外还可调节正文视口与段落内边距；标题/正文使用固定 dp 字距，页眉页脚通过默认模板逐一配置六位置内容及返回按钮；字号、字体、边距、高度和整体可见性使用底层样式API，不再调用旧单行内容与对齐配置。
# 默认小说排版

内置书籍、本地 TXT、网络加载及卷页小说示例共用 `res/values/reader_defaults.xml` 中的 `ReaderDefaultStyle`，参数和默认字体取自旧 Demo：正文20sp、标题28sp左对齐、额外行距10dp、段距18dp、标题底部72dp；完整六区域边距见[排版文档](../docs/26_READER_PANELS_AND_TYPOGRAPHY_API.md)。首次升级冷启动迁移旧排版偏好，后续调整继续保存。


主阅读页直接使用库内六位置默认模板；模板配置独立持久化，无旧页眉页脚内容迁移。完整自定义页面放在CustomContentActivity示例中，参见[Demo模板接入](../docs/PAGE_CHROME.md)。
