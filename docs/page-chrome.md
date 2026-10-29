# 页眉页脚：基础扩展与默认模板

## 两层定制

| 层次 | 入口 | 控制范围 |
|---|---|---|
| 基础结构SPI | ReaderPageDecorationFactory/Decoration；完整ReaderPageFactory/Template | 任意View/ViewGroup、布局、交互、绑定与清理，不限三栏或内置内容 |
| 默认模板配置 | ReaderPageFactories.default()、ReaderPageChrome/Slot、setPageChrome/Provider | 六位置文字/内容/动作及独立返回按钮；免写View |

默认模板是SPI的一个实现。添加第四个控件、两行标题或复杂交互可直接使用基础扩展，不强迫六位置模型扩展成万能布局协议。自定义实现可从ReaderPageContext与捕获宿主状态绑定，完全忽略可选context.chrome。

Widget/呈现层只协调提供器、模板绑定/测量和刷新，不解释三栏或电量。DefaultPageChromeResolver解析默认内容/更新需求，DefaultPage管理View，DefaultChromeBatterySource管理默认模板电量监听。通用ReaderPageClock仅负责时间。

## 六位置布局与兼容边界

```text
页眉：[返回] [start] [center] [end]
正文：Widget管理
页脚：       [start] [center] [end]
```

start/end采用Android方向语义，LTR为左/右、RTL镜像。返回在页眉start之前，不占六个文字位置；三个位置均分按钮之后的剩余宽度，中间文字居中于该剩余区域。长文字单行省略，空位置保留宽度。

每行设置任意新位置就启用该行三栏。库保留旧headerText/footerStartText/footerEndText、ChromeStyle.content/alignment等底层能力；未配置新位置的行沿用旧布局。新位置为null继承该位置的旧默认，NONE显式清空。Demo不依赖此兼容层，六位置全部显式提供。

字号、颜色、字体、内边距、高度、背景、整体显隐继承header/footer ChromeStyle，默认11sp。仅改文字/动作不重新分页；几何变化通过样式或工厂刷新处理。

## Kotlin配置

```kotlin
reader.setPageChrome(ReaderPageChrome(
    headerStart = ReaderPageChromeSlot(ChromeContent.CHAPTER_TITLE),
    headerCenter = ReaderPageChromeSlot(ChromeContent.TIME),
    headerEnd = ReaderPageChromeSlot(ChromeContent.PAGE_PROGRESS),
    footerStart = ReaderPageChromeSlot(ChromeContent.BOOK_TITLE),
    footerCenter = ReaderPageChromeSlot(ChromeContent.BATTERY_LEVEL),
    footerEnd = ReaderPageChromeSlot(ChromeContent.NONE),
    showBackButton = true,
    backAction = ReaderPageChromeAction { finish() }
))
```

内置内容：BOOK_TITLE、CHAPTER_TITLE、PAGE_NUMBER、PAGE_COUNT、PAGE_PROGRESS、TIME、PAGE_PROGRESS_AND_TIME、BATTERY_LEVEL、NONE、DEFAULT。页号/页数属于当前章，候选未确定时可为空。电量为百分比，不可用为空；时间使用系统12/24小时格式。

ReaderPageChromeSlot.text非null覆盖content，空字符串清空；action独立响应点击。业务状态如下载进度、全书进度等可用自定义text，不应不断进入库枚举。

## Java配置

```java
ReaderPageChrome chrome = new ReaderPageChrome()
    .withHeader(new ReaderPageChromeSlot(ChromeContent.CHAPTER_TITLE),
                new ReaderPageChromeSlot(ChromeContent.TIME),
                new ReaderPageChromeSlot(ChromeContent.PAGE_PROGRESS))
    .withFooter(new ReaderPageChromeSlot(ChromeContent.BOOK_TITLE),
                new ReaderPageChromeSlot(ChromeContent.BATTERY_LEVEL),
                new ReaderPageChromeSlot(ChromeContent.NONE))
    .withBackButton(true, page -> finish());
reader.setPageChrome(chrome);
```

withBackButton(false)隐藏按钮并释放水平宽度，不改变正文高度；未给点击回调时请求受绑定位置/代次保护的ReaderAction.ExitReader，由宿主ReaderActionHandler处理。

## 动态刷新

setPageChrome把提供器替换为常量配置，内置数据仍逐页解析。动态业务使用setPageChromeProvider，在主线程捕获当前状态并返回ReaderPageChrome；状态变化后调用refreshPageChrome。提供器必须快速、无副作用，不读取page.chrome递归，不在其中导航或更新Widget。

refreshPageChrome只更新文字/动作，导航期间延迟至Idle；不替换自定义工厂。自定义Template实现refreshChrome，Decoration组合器会重新bind其区域；可用该刷新接入宿主状态，不必读取默认六位置模型。null提供器恢复库默认配置。

## 任意View组合与生命周期

```kotlin
reader.setPageFactory(ReaderPageFactories.withDecorations(header = myHeaderFactory))
// 未替换页脚保留默认模板；也可直接setPageFactory(myPageFactory)。
```

工厂每次返回新且未挂载对象，候选页可并存；View身份稳定，bodyHost属于root。bind可能先收到未知页码，再收到完整页数据。自定义区域直接拥有内部布局与事件，库不替它安装点击监听；未消费输入可继续到Widget。通过context.requestReaderAction请求目录/设置/退出，宿主提供处理器。unbind清理监听、任务及页面引用。

时间复用唯一分钟调度。电量只由实际需要它的、可见且附着的默认模板订阅系统粘性广播；隐藏候选不订阅，隐藏/detach/unbind注销，显示后刷新。已替换为自定义区域的部分不启动默认电量订阅。自定义模板若需要电量可自管数据源，不依赖此内部类。

## Demo当前接入

- 主ReaderActivity直接配置默认模板：页眉左章节名、中右空；页脚左章内进度、中时间、右电量，返回默认显示。
- FullScreenPageFactory只补系统安全边距并转发生命周期，不解释内容或切换旧布局。
- TypographySheet逐位置选择内容及返回显隐；PageChromeSettings/PageChromeCodec独立保存六位置/按钮并在重建时重新安装回调。
- Demo不读取/迁移旧单行内容、整行alignment、customPage状态；保留底层字号、边距、高度等外观API。旧排版revision2/3兼容分支移除，当前revision4的其他外观按既有格式读取。
- NetworkActivity/TextSourceActivity也显式使用模板；完整自定义示例留在CustomContentActivity，Java/效果专门示例继续演示基础SPI。

最近模板及电量修改仅有构建证据，未完成设备显示、点击、旋转、可见性和恢复验收。
