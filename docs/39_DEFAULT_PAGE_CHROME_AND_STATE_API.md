# 默认页眉页脚模板与状态更新 API

## 对照来源与默认布局

读取旧项目的 `libreadview_item_read_page.xml`、`ReadPage.kt`、`ReadStyle.kt` 和 `BasicReadView.bindReadPage` / 滚动页绑定逻辑。

默认模板现在直接采用同一结构：

- 页眉：14dp返回箭头（含1dp竖向、5dp末端内边距）＋单行标题，长标题尾部省略。
- 页脚：左侧章内 `页码/页数`，右侧本地时钟，11sp。两个文本分别布局，窄窗口中各占最多一半宽度并省略，避免相互覆盖。
- 页眉DEFAULT内容在章首页显示书名、其余页显示章节标题；显式CHAPTER_TITLE/BOOK_TITLE等原有配置继续优先。返回默认请求ReaderAction.ExitReader，宿主通过setReaderActionHandler处理。
- 页脚DEFAULT和PAGE_PROGRESS_AND_TIME都使用左右分栏。其他ChromeContent仍按原有含义显示，并遵循alignment。
- Demo和所有未设置自定义工厂的Widget自动使用该模板；FullScreenPageFactory仍只在模板内部叠加系统安全边距。

不搬入旧ReadStyle中硬编码的64dp状态栏占位。库默认页边距20/0/20/0dp，页眉上下8/20dp、页脚20/8dp；宿主继续负责窗口系统安全区域。Demo原有边距已采用这个分配。本次修改默认ChromeStyle字号为11sp，用户显式样式/已保存样式不强制覆盖。

## 公共 API

类型位于 `org.peyilo.ireadview.spi.page`，与现有模板/装饰接口同层。

| API | 作用 |
| --- | --- |
| ReaderPageChrome | 不可变的标题、页脚两端文本、返回显示开关和四处点击行为 |
| ReaderPageChromeProvider.provide(page) | 按书名、章节、页码、模式、时间及捕获的宿主业务状态生成内容 |
| ReaderPageChromeAction.onClick(page) | 点击对应条目后的宿主操作；默认返回走已有ExitReader动作 |
| BookReadView.setPageChromeProvider(provider) | 安装并刷新；null恢复内置内容 |
| BookReadView.refreshPageChrome() | 宿主状态变化后重新读取内容，不重新分页、不移动阅读位置 |
| ReaderPageContext.chrome | 自定义模板/装饰读取当前提供器结果 |
| ReaderPageTemplate.refreshChrome() | 自定义完整模板接入轻量刷新；默认实现无操作 |

ReaderPageChrome中headerText、footerStartText、footerEndText：null继承默认值，空字符串清空文本。backAction为空时使用默认ExitReader；其余Action为空时不可点击。showBackButton=false移除图标的水平占位，页眉保留最小高度，不改变正文可用高度。

所有注册、刷新、提供器及点击都在主线程。provide可能在候选页准备时调用，此时position/pageCount可空；必须快速、无副作用，不调用page.chrome（递归）、不导航、不修改Widget。点击回调可以操作宿主或使用page.requestReaderAction发起有页面代际校验的标准动作。

## Kotlin：根据状态替换页脚

```kotlin
import org.peyilo.ireadview.spi.page.*

var statusText: String? = null
reader.setPageChromeProvider(ReaderPageChromeProvider { page ->
    ReaderPageChrome(
        // null时仍显示默认章内页码；右侧时钟保持默认。
        footerStartText = statusText,
        footerStartAction = statusText?.let {
            ReaderPageChromeAction { clickedPage ->
                showSyncDetails(clickedPage.position)
            }
        }
    )
})

// 网络、自动阅读、下载等业务状态变化时，回到主线程：
statusText = "同步中…"
reader.refreshPageChrome()

// 恢复默认页码：
statusText = null
reader.refreshPageChrome()

// 完全移除提供器：
reader.setPageChromeProvider(null)
```

showSyncDetails是调用者自己的函数。可以在ReaderListener中按状态更新并刷新，但不要在provide内触发状态变更。

## Java

```java
// statusText是宿主字段，可随业务状态改变。
reader.setPageChromeProvider(page -> new ReaderPageChrome(null, statusText));
statusText = "离线模式";
reader.refreshPageChrome();
```

ReaderPageChrome有Java重载构造函数与getters；有复杂自定义需求时可使用完整构造函数或既有ReaderPageFactories.withDecorations。

## 刷新与生命周期

- 分页/滚动模式均把稳定的宿主提供器桥接传给每个页面上下文；候选页提交前再次刷新，避免准备期间的业务状态变化丢失。
- 翻页动画中调用refreshPageChrome会等到Idle刷新，不取消手势，不让已捕获快照与当前页面产生不同的文字。
- 默认模板只更新单行文字和点击监听，不触碰bodyHost，不清空布局缓存，不发布位置变化。改变字体、slot高度、多行结构请用ReaderStyle或setPageFactory正常重排。
- 使用原有每Widget一个分钟时钟；只对正在显示且需要默认时间的槽更新，无新轮询定时器。自定义时钟文本后不再为该槽订阅默认时钟。
- 完整自定义模板需覆写refreshChrome，保存自己的最后上下文并读取context.chrome；FullScreenPageFactory已转发刷新。withDecorations会重新bind自定义装饰（仅限装饰，正文不绑定），每次绑定应替换旧监听。
- unbind清理上下文、监听与背景；release释放Widget保存的业务提供器引用。宿主业务状态不自动序列化，重建后由宿主重新注册。

## 构建范围

Debug APK、Release AAR、Maven仓库包构建通过。本次未添加或运行测试；设备长标题、字体缩放、滚动/翻页时状态变化等视觉与生命周期情况尚未做运行验收。
