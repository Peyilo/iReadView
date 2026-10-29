# 正文、选区和翻页的独立扩展

Widget拥有导航事务、已提交位置和页面生命周期。正文引擎、内容绘制、选区绘制/交互、翻页动画通过独立SPI组合；宿主无须继承ReaderView或引用internal实现。

| 替换范围 | 扩展契约 | 安装入口 |
|---|---|---|
| 内容绘制，保留已有排版/命中 | ReaderBodyRenderer / Factory | setPresentationComponents |
| 选区底色、手柄和匹配的命中几何 | ReaderSelectionRenderer / Factory | setPresentationComponents |
| 持久划线装饰，保留原文范围和选择算法 | ReaderUnderlineRenderer / Factory | setPresentationComponents |
| 初始长按拦截、手柄拖动及边缘翻页/滚动 | ReaderSelectionController / Factory、ReaderSelectionDelegate | setPresentationComponents |
| 完整排版/正文引擎与坐标模型 | BookPresentationFactory、ReaderBodyView | setBookPresentationFactory |
| 翻页视觉效果 | PageTurnEffectFactory / Effect、PageSurface | setPageTurnEffect |
| 菜单业务动作/视图 | SelectionToolbarFactory、SelectionActions | setSelectionToolbarFactory |

## 单独替换选区外观

```kotlin
val components = reader.presentationComponents.copy(
    selectionRendererFactory = ReaderSelectionRendererFactory {
        object : ReaderSelectionPainter() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun drawFill(canvas: Canvas, path: Path, style: SelectionStyle) {
                paint.color = 0x5580A0FF
                canvas.drawPath(path, paint)
            }
        }
    }
)
val result = reader.setPresentationComponents(components)
```

`ReaderSelectionPainter`是可继承的默认实现。只改底色可继承并覆盖drawFill；完全替换可实现ReaderSelectionRenderer。创建手柄、绘制、命中范围和抓取偏移属于同一接口，避免自定义手柄外形与触摸区域不一致。文本分页、文本滚动和EPUB都从同一组件配置创建实例；不会退回WebView CSS Selection。

Canvas/Path是借用参数，只能在本次调用使用，不得修改或保存传入Path。每个正文分别拥有实例；有状态renderer不能跨三个缓存正文共享。

## 替换正文绘制

```kotlin
val components = reader.presentationComponents.copy(
    bodyRendererFactory = ReaderBodyRendererFactory {
        object : ReaderBodyRenderer {
            override fun draw(canvas: Canvas, body: ReaderBodyView,
                              drawDefault: (Canvas) -> Unit) {
                // 可替换内容绘制，或在其前后增加自定义绘制。
                drawDefault(canvas)
            }
        }
    }
)
reader.setPresentationComponents(components)
```

原生文本把块内容绘制放入该回调，活动选区在它之后绘制；EPUB把WebView子内容绘制放入该回调，活动选区仍由原生层覆盖。外层保存/恢复Canvas状态，避免绘制策略污染后续选区层。`drawDefault`只在本次调用有效，不可异步保存后使用。

原生块正文额外实现可选 `BlockReaderBodyContent`，可读取只读fragments使用其公开布局SPI；EPUB不实现此能力，不需要伪造文本块。修改绘制时应保持已有坐标映射：如果更换HTML引擎、重排、字形布局或命中规则，应通过下面的完整后端工厂提供匹配的ReaderBodyView/BookPresentation，不能只平移文字而继续使用旧选区坐标。

## 独立替换划线绘制

```kotlin
reader.setPresentationComponents(reader.presentationComponents.copy(
    underlineRendererFactory = ReaderUnderlineRendererFactory {
        val standard = ReaderUnderlinePainter()
        object : ReaderUnderlineRenderer {
            override fun draw(canvas: Canvas, segments: List<TextLineSegment>,
                              style: TextLineStyle, density: Float) {
                standard.draw(canvas, segments, style.copy(color = Color.MAGENTA), density)
            }
            override fun close() = standard.close()
        }
    }
))
```

该组件同时用于文本分页、文本滚动和 EPUB 的持久划线；无需替换正文引擎、活动选区控制器或标注存储。segments 是借用的局部物理像素坐标，TextLineStyle 尺寸单位仍是 dp。底层正文在对应片段处变换 Canvas，renderer 只消费提供的线段。自定义绘制有 Canvas 状态隔离，不能保存参数异步绘制；默认 ReaderUnderlinePainter 保持原有直接路径。

每个正文独立创建并持有实例，clearContentBinding 不关闭它，最终 release 关闭一次。创建划线组件失败时，先关闭本次已创建的绘制/选区实例，保留原始异常并附加清理异常，正文可重试配置。最终清理中一个组件抛出普通异常也会继续清理其他组件。工厂须返回新实例，close 须幂等且不抛异常。

ReaderPresentationComponents 新字段有默认值，既有 Kotlin 命名参数和 Java 三参数构造继续可用；data class 的 copy 等 JVM 签名变化仍要求扩展库与核心共同重新编译，不能据此声称二进制兼容。

## 替换选区交互

```kotlin
reader.setPresentationComponents(reader.presentationComponents.copy(
    selectionControllerFactory = ReaderSelectionControllerFactory { context, delegate ->
        val standard = ReaderSelectionInteraction(context, delegate)
        object : ReaderSelectionController by standard {
            override fun onLongPress(x: Float, y: Float): Boolean? {
                return null // 使用后端默认初始范围；返回true接管，false禁止。
            }
        }
    }
))
```

也可完全实现ReaderSelectionController。Delegate提供正文坐标下的命中、抓取偏移、扩选、边缘位置、移页/滚动、拖动状态与错误报告。默认控制器处理触摸阈值、异步结果失效、停留触发、逐帧滚动和反向拖动。异步扩选/移页必须在提交前检查传入的 `current()`，reset/release必须取消旧任务。reset结束当前手势，release是最终清理；实现须幂等且不抛异常。

原生滚动模式已移除原先单独的拖动/边缘任务，三个内置呈现路径共用此工厂与默认控制器。原始范围和命中数据仍由后端解释：初始默认段落选择、源文本偏移或DOM路径不能由绘制组件自行改写；需要新范围算法时，控制器可接管长按并使用宿主选择API，或提供完整后端。

## 替换完整正文后端

```kotlin
reader.setBookPresentationFactory(BookPresentationFactory { context, mode, source, host ->
    if (handles(source, mode)) {
        MyBookPresentation(context, mode, source, host)
    } else null
})
```

选择顺序：宿主工厂非null结果 → PresentationBookSource工厂 → Widget默认原生呈现。`null`表示交回默认链；`setBookPresentationFactory(null)`撤销宿主覆盖。`BookPresentations.createNative(context, mode, host)`公开提供原生呈现，方便组合，避免引用internal类或递归调用Widget工厂；该方法也可直接从Java调用。

工厂每次返回新的未挂载Presentation，构造阶段不做IO；prepare完成后台数据/排版并提供PreparedRead/PageSurface，show原子展示，dispose清理取消候选，detach释放当前页和缓存。正文引擎不得自行提交阅读位置。BookSource仍负责数据和资源。`BookPresentation.supportsBlockLayout`默认false，只有实际使用原生ChapterLayout页模型的后端才声明true；全书页索引/原生章节预加载同时核对来源与呈现能力，避免“来源有文本块”被误认为替换引擎也有相同页码。原生后端明确拒绝DOM documentLocator，避免将另一引擎的位置悄悄解释成普通页码。

自定义ReaderBodyView在挂载前调用 `configureComponents(host.presentationComponents)`；内容绘制调用受保护的drawBodyContent。`clearContentBinding`只结束可复用绑定；`release()`是最终幂等模板方法，保证渲染组件被关闭。WebView、任务等引擎资源放在受保护的 `onRelease()`，不要覆盖release。临时回收的滚动行不关闭组件，Adapter最终卸载时释放包括回收池中的全部正文。

## 翻页继续独立替换

```kotlin
reader.setPageTurnEffect(MyPageTurnEffectFactory())
```

Effect仅消费PageSurface、视口、方向和进度，不识别TXT、EPUB、正文View或选区。start准备视觉资源，update响应Widget提供的拖动/自动进度，draw绘制，finish/abort/detach归还资源；效果不能自行改变阅读位置或读取具体WebView。包装默认效果时如实转发SnapshotPageTurnEffect状态，避免错误隐藏live正文。位图使用Host预算租约，不绕开缓存/资源限制。

## 运行时替换与兼容

- 两个新安装API均主线程调用，返回ReaderResult。打开书籍、切换模式、导航/翻页或活动选区拖动时返回Busy，释放后返回Released；被回调重入替换的配置返回Superseded。
- 成功代表配置已安装，当前内容通过异步恢复重新绑定；观察readerState/监听事件确定新正文就绪。旧选区动作随原绑定失效，阅读位置按源锚点/DOM定位恢复，不重新调用BookSource.loadBook。
- 不同引擎的位置语法可能不兼容。宿主应提供新引擎可解析的位置，或显式导航到firstPage；不要将旧DOM地址强制解释成文本偏移/页码。
- 配置版本变化会创建新的Presentation，准备成功后交换并清理旧页面、三个缓存正文、控制器和组件。失败/取消不提交候选位置；配置仍可由宿主恢复DEFAULT/null后重试。
- 仅替换绘制/选区组件时保留章节排版缓存；完整后端、页面工厂或块布局提供者变化才使原生排版配置失效。替换准备期间旧、新两代呈现可短暂共存，不能把单代三页上限解释为跨代同时只有三个View。
- `ReaderPresentationComponents.DEFAULT`恢复内置组件；factory配置不进入ReaderSnapshot或Demo偏好存储，由宿主负责重建。
- 原有setPageTurnEffect、菜单API、ReaderSelectionPainter默认外观和统一选择API保持；自定义ReaderBodyView若原来覆盖release，应迁移到onRelease。扩展库与核心需一起重新编译，不能把SPI 调整当成旧AAR二进制兼容保证。

## 验证

`ReaderExtensionIntegrationTest`对文本/EPUB的分页和滚动注入三个组件工厂及完整后端工厂，同时替换翻页Effect，检查绘制/长按/触摸调用、真实整段选择、运行时恢复默认、不重复loadBook，以及旧renderer/controller的创建与关闭数量相等。其他默认行为由统一选区、缓存、双页可见性、手势/交接与整章分页用例回归。
