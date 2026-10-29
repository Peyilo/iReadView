# 内容排版与原生图片扩展

正文引擎、文字块排版与图片 IO 是不同职责。以下入口用于原生块引擎；自研 EPUB WebView 的 XHTML/CSS 资源管线仍归 EPUB 模块管理，不能将 ImageBlock 解码配置误认为 WebView 全局拦截器。

## 委托默认文字排版

```kotlin
val standard = BlockLayoutProviders.text()
reader.setBlockLayoutProviders(listOf(object : BlockLayoutProvider by standard {
    override val id = "app.extra-spacing"
    override val version = "1"
    override fun measure(block: ContentBlock, context: BlockLayoutContext): BlockLayout {
        val measured = standard.measure(block, context)
        return object : BlockLayout by measured {
            override val spacingAfterPx = measured.spacingAfterPx + 2f * context.viewport.density
        }
    }
}))
```

`BlockLayoutProviders.text()`返回公开BlockLayoutProvider契约，不要求引用internal类。它支持TextContentBlock、HeadingBlock与LinkedTextBlock，保留源偏移、链接命中、选择路径和无障碍几何。此例只改变块后间距，仍转发同一份文字几何；若装饰fragment也必须转发这些能力，不能只重画文字而丢掉命中。包装器使用自己的稳定id/version，行为变化后重新注册；自定义provider优先，默认文字/图片仍作兜底。引擎已扣除块内边距，measure不能重复添加。

## 替换图片获取与解码

```kotlin
val standard = ReaderImageLoaders.defaultLoader()
val options = ReaderImageLoadingOptions(
    loader = ReaderImageLoader { request ->
        // 也可委托应用自己的有预算解码器或内存资源。
        // 不改变 request 中的 source / bookRevision / 预算后再委托。
        standard.load(request)
    },
    maxEncodedImageBytes = 2 * 1024 * 1024,
    maxDecodedImageBytes = 2L * 1024 * 1024,
    maxDecodedChapterBytes = 8L * 1024 * 1024
)
val result = reader.setImageLoadingOptions(options)
```

默认loader通过BookResourceSource读取，携带当前bookRevision，先检查编码数据上限、读取尺寸，再按像素预算和两倍视口限制采样解码。源不具备资源能力则返回null。自定义loader也可为普通BookSource提供宿主生成的图片；独立Maven宿主的HostContentStrategies就是此场景。

库在IO线程调用loader，不在measure或动画帧中读取图片。不同章节可能并行调用同一个loader，因此实现要线程安全、响应协程取消。加载配置在准备时快照化，并参与布局与全书索引的配置键；旧配置不能与新配置共享布局结果。

每次章节准备依次请求唯一resourceId；已由自定义BlockLayoutProvider处理的图片不会再加载。每个请求的maxDecodedBytes取“单图预算”和“本次章节剩余预算”的较小值，章节预算不足4字节时停止调用。章节预算为0可关闭默认图片加载并保留alt占位。普通失败、null、已回收/硬件Bitmap、超预算返回值使用占位；CancellationException向上传播，不吞掉导航取消。

预算要求必须在自定义loader分配前执行。库对返回值的检查只能避免不合规资源进入排版，不能撤销自定义代码已经发生的分配。返回Bitmap须支持软件Canvas绘制且在所有布局引用期间不可被外部修改/回收；可与宿主缓存共享只读内容。库按引用保留，不主动recycle；loader及外部缓存由宿主拥有，setImageLoadingOptions/closeBook/release不关闭宿主loader。此预算是单次准备的工作集，不是整个进程或全部缓存的总上限。

## 运行时配置

setImageLoadingOptions主线程调用并返回ReaderResult；正在开书、导航/翻页、切模式或拖动选区时返回Busy，释放后返回Released。成功仅代表配置已安装，异步恢复通过readerState/监听事件观察。配置改变后失效原生章节布局及全书索引，按源锚点重新准备，不重新loadBook。`reader.imageLoadingOptions`读取当前配置；恢复`ReaderImageLoadingOptions()`即可回到默认策略和预算。宿主回调重入修改配置时，较早的安装返回Superseded。

加载器配置不写入ReaderSnapshot，宿主重建时重新安装。此新增SPI不承诺旧AAR二进制兼容；使用者应一并重新编译相关扩展。

## 验证范围

ContentStrategyDeviceTest使用真实Android Bitmap/StaticLayout验证：资源去重与预算耗尽停止、绘制像素、超预算占位、取消传播、自定义块绕过默认图片加载、默认解码器的revision/编码预算，以及公开文字provider包装后选择/无障碍几何仍可用。ReaderExtensionIntegrationTest另验证运行时换loader后重新请求、锚点恢复、无重复loadBook，以及活动拖选时拒绝修改。独立Maven宿主从发布AAR消费这些入口，Kotlin与Java宿主均安装同一公开示例策略。
