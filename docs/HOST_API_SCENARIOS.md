# 宿主能力判断、输入与自定义业务

使用者应通过公开契约组合组件，不判断TextReaderBodyView/EpubReaderBodyView类型，也不直接访问WebView或内部存储。来源、排版与图像策略见[内容扩展](CONTENT_EXTENSIONS.md)，绘制/选择/翻页替换见[组件扩展](EXTENSIONS.md)。

## 按能力显示功能

```kotlin
val capabilities = reader.contentCapabilities
val enableIndex = capabilities.wholeBookPageIndex == ReaderFeatureSupport.Supported
val hasSelection = capabilities.selectionModel == ReaderSelectionModel.Text ||
                   capabilities.selectionModel == ReaderSelectionModel.Document
```

同一快照也通过`ReaderState.contentCapabilities`随`ReaderListener.onStateChanged`发布，Java使用`getContentCapabilities()`。主线程读取，不触发IO或排版。它描述当前正文引擎的功能；phase/navigationState/ReaderResult仍决定当前能否执行。Supported不意味着每个节点都能选择，也不表示已经生成全书索引。Unknown表示自定义引擎未声明，应按产品策略隐藏或允许尝试；不能当作Unsupported或已支持。关闭/释放后回到全Unknown，不暴露前一本书的能力。

| 能力 | 原生文本分页 | 原生文本滚动 | EPUB分页 | EPUB滚动 |
|---|---|---|---|---|
| selectionModel | Text | Text | Document | Document |
| links / rangedAnnotations | Supported | Supported | Supported | Supported |
| wholeBookPageIndex | Supported | Supported | Unsupported | Unsupported |
| pageTurnEffects | Supported | Unsupported | Supported | Unsupported |

最后两项由Widget核对实际source、引擎supportsBlockLayout/supportsPageTurnEffects和已提交模式派生。来源有文本块不证明替换引擎使用原生页模型；EPUB内的章节页数不等于可用的全书页索引。Demo的PageIndexPanel依据此能力决定是否开始计算，不再先弹进度窗口再等待Unsupported。

自定义BookPresentation覆盖`contentFeatures: BookPresentationFeatures`声明selectionModel、links和rangedAnnotations；声明应快速、无副作用且在该实例生命周期内稳定。默认三项Unknown，保持已有第三方引擎可用，且不凭接口默认实现猜测功能。Widget另从原有能力标志计算索引/动画支持，合成面向宿主的ReaderContentCapabilities；后端声明类型中不存在会被忽略的索引/动画字段。能力声明不自动实现选择、链接或标注，也不取代相关SPI。

## 自定义点击区域及键盘配置

```kotlin
reader.setInputOptions(reader.inputOptions.copy(volumeKeyPagingEnabled = true))
reader.setReaderTapListener { tap ->
    when {
        tap.y < .2f -> { reader.previousPage(NavigationOptions()) { }; true }
        tap.y > .8f -> { reader.nextPage(NavigationOptions()) { }; true }
        else -> { showReadingMenu(); true }
    }
}
// reader.setReaderTapListener(null) 恢复内置边缘区域处理。
```

ReaderTap是Widget内容视口的归一化坐标，包含真实物理左右，不会为RTL自动反转。宿主可自定义点击区域、顺序和菜单；返回true消费事件，false继续内置点击翻页和performClick。链接/已有选区等正文消费优先于宿主点击监听。监听发生在tapPagingEnabled开关之前，因此自定义监听须自行遵循自己的开关策略；不要把输入选项当作屏蔽所有宿主回调的总开关。调用前后会核对内容代次与释放状态，宿主回调不能让默认处理继续操作已换书内容。

常规手势/点击/键盘/音量键启用及翻页轴通过ReaderInputOptions配置；输入最终调用同一导航事务。效果只消费进度，不得另改页码。原始拖动识别仍归BaseReadView，未声明完全可替换的手势路由器；长按、选区拖动和边缘扩选则用ReaderSelectionControllerFactory替换。此边界避免宿主输入策略、正文引擎和动画分别维护阅读位置。

## 以选区保存书签、笔记与引用

```kotlin
val selected = reader.getSelection()
val position = reader.getSelectionPosition()
if (selected != null && position != null) {
    reader.addBookmark(Bookmark(UUID.randomUUID().toString(), position, selected.text.take(40)))
    // 笔记由宿主保存：文本、position、笔记内容及业务id。
}
```

getSelectionPosition主线程调用，不清除选区、不创建标注、不执行导航。文本返回起点ContentAnchor，文档由当前引擎转换为不透明documentLocator；不支持该转换的引擎、无选区、关闭或释放时返回null。不要解析DOM路径或把documentLocator转换成UTF-16偏移。保存后可用goToPosition恢复；读取持久引用时仍应校验书籍身份、revision及quote。位置导航不自动验证宿主保存的引文，排版页码只是提示。

Demo选区菜单的“书签”使用此API，文本与EPUB共享同一动作。异步编辑笔记前先复制selection/position，随后检查原SelectionActions.isValid()；不要跨换书或组件替换长期保存旧动作对象。

内置ReaderAnnotation类型仍为sealed，统一存储内置书签、高亮和划线。应用笔记/任意元数据由宿主存储并通过公开SelectionToolbarFactory/菜单动作接入，不要求把未知业务对象塞进库的标注仓库。库目前不承诺任意第三方标注类型的校验与绘制；新增这样的类型需要独立定义范围、绘制、点击、校验与持久化契约，不能仅去掉sealed让when分支悄悄忽略对象。

## 验证范围

2026-10-23最终核心27项JVM通过；Debug/AndroidTest、核心Release与EPUB Release及本地Maven发布成功。API36设备专项5项通过（15.713秒设备用时），主工程日志`app/build/capabilities-release-final.log`。其中包含能力/选区定位四路径、自定义未知能力引擎、图片策略运行时替换及两项组件清理用例。Demo“书签”和索引预检调用已编译；没有把菜单按钮人工点击或完整输入设备矩阵算作本轮已测。

最终AAR重新发布后，独立Maven宿主Debug与R8压缩Release重新构建成功，日志`app/build/capabilities-host-final.log`；本轮未重新执行该宿主设备启动，实际启动记录仍以前一轮内容扩展的Debug观察为限。

ReaderExtensionIntegrationTest在文本/EPUB × 分页/滚动四条路径查询能力并对照ReaderState，捕获当前选择位置并建立对应书签、验证不清选区，关闭后不残留能力/位置；另外创建未声明功能的替换引擎，确认不继承原生选择/链接声明，也不因来源有文本块而开放全书索引。已有选区交互、组件清理与图片运行时替换在同轮回归。输入自定义点击的API契约来自当前BaseReadView分派路径，本轮未新增完整点击区域/键盘/辅助输入设备矩阵。
