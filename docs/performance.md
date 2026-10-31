# 翻页性能与验收

优化以保持画面、手势和动画时长为前提。分别测量页面准备、效果启动、跟手更新、GPU 绘制及正文交接；导航总耗时、缓存命中和某一颜色柱段都不能单独证明动画流畅。全流程低于 8ms 的目标尚未通过。

## 线程与资源

Widget 负责手势仲裁、候选页准备和阅读位置提交。效果通过 `PageSurface` 获取页面，不读取书籍或 DOM，也不维护第二个帧时钟。iOSCurl 保留原版 0.25s begin、跟手轨迹和 settle 时间。

iOSCurl 的 EGL 工作线程只消费最新姿态，跨事务复用上下文、着色器、顶点缓冲与两块纹理存储。每次翻页完整更新页面像素，不按 View、Picture 或 Bitmap 身份跳过更新。硬件输出不逐帧回读；软件输出仍有 `readPixels` 和 CPU 复制成本。默认 Texture 与直接 Surface 的布局约束见[渲染说明](rendering.md)。

普通实时 View 和 EPUB 使用事务独占的像素快照。EPUB 的硬件显示列表可能借用 WebView 子 RenderNode，隐藏或停放正文会改变这些节点，不能作为独立快照。iOSCurl 通过独立读租约上传后归还 CPU 位图；取消、异常和卸载也必须结束租约。默认位图预算为 16MiB，实际尺寸受预算阶梯影响。

完全不透明的页面可使用按需准备的硬件 Canvas 捕获，EGL 将生产图像复制到事务独占的固定纹理后立即归还生产缓冲。独立 `RecordedPageSurface` 由主线程回放、生产线程提交；实时 View 和 EPUB 仅在 API 29 及以上启用，并在主线程完成提交，防止窗口遍历提前替换子 RenderNode。API 28 及以下的实时页面保留位图路径。未就绪、缺少背景元数据、预算或平台能力时也使用位图路径。

API 29 及以上在合成 View 就绪时提前预热捕获资源。预算在 UI 回调中读取，尺寸选择与翻页开始时共用同一策略；初次按需准备也使用实际请求的尺寸。预热不捕获页面内容、不提前推进动画、不改变既有分辨率阶梯。未完成预热的首次输入仍允许位图回退。

API 26–28 的 EPUB 稳定视口另有每本书 32MiB 的缓存拥有量上限，与效果预算分开；失效或退出缓存不强制回收仍被效果引用的像素。该上限不是进程内存上限。

文本与 EPUB 共用[分页缓存](page-cache.md)。完整且有效的取消候选归还缓存，未完成或过期候选释放。EPUB 分页视口在旧系统只为已提交的当前页启用硬件层，API 29 及以上也缓存备用页；软件快照直接绘制 WebView，连续滚动保留默认图层。直接 Surface 保持下方正文，并在恢复后的 Window 绘制及一像素 GPU fence 完成后清空最终图像。

## 设备复测

先构建并安装 Demo 与 AndroidTest APK，在选定且已解锁的设备上执行：

```sh
adb shell am instrument -w -r \
  -e class org.peyilo.iread.demo.IosCurlPerformanceTest \
  -e formats text,epub,arm -e effects ios_curl -e turns 8 -e gesturePairs 4 \
  -e cacheMode three -e rawFrames true \
  org.peyilo.iread.demo.test/androidx.test.runner.AndroidJUnitRunner
```

存在多个设备时用 `adb -s` 指定目标。默认使用 Texture；`-e output surface` 请求直接 Surface，API 21–28 实际使用 Texture。报告的 `output` 是请求值，`compositorView` 记录实际挂载的合成 View；不能混淆两个后端的窗口测量范围。`cacheMode` 可选 `three` 或 `single`，结果记录实际策略。默认每种内容包含 8 次自动翻页、4 组提交/取消手势；必须验证实际阅读位置，未触发翻页的采样无效。不同系统、输出、缓存及内容串行测量，不同时运行构建或其他设备用例。

报告在 Demo 私有目录 `files/ioscurl-performance.json`。比较时固定书籍、视口、density、动画参数和预算；降低纹理尺寸后的结果不能算原分辨率优化。性能用例使用 64MiB 快照预算容纳两张 1080 × 2424 图像，高于公共默认预算，不能推广为默认配置保证。

GPU 柱状图可用 `adb shell setprop debug.hwui.profile visual_bars` 开启，向用例添加 `-e captureProfiles true`。采样后生成 `files/curl-profile-<format>-<kind>.png`；截图压缩不计入动画采样。结束后用 `adb shell setprop debug.hwui.profile false` 关闭。

## Trace 与窗口指标

采集 `atrace --async_start -b 16384 -a org.peyilo.iread.demo gfx view`，结束后用 `--async_stop -b 16384 -a org.peyilo.iread.demo -o /data/local/tmp/curl.trace gfx view` 保存并拉取。原始记录、截图和分析结果放入忽略的构建目录，不提交为项目文档。

```sh
python tools/analyze-curl-trace.py curl.trace --output curl-stages.json
```

| 指标 | 范围 |
| --- | --- |
| `mesh/raster/drawBatches/present` | EGL 线程的几何、光栅及提交；返回不代表 GPU 完成 |
| `uploadPage/uploadCheck` | 逐页完整更新及错误查询；查询可能等待前序 GPU 工作 |
| `uploadToFirstSubmit` | 同一线程从上传开始到其后首个含 present 的 curl 帧返回 |
| `startupToFirstSubmit` | 跨线程从效果 start 内的准备起点到首个 curl 提交返回，含 UI 捕获和排队 |
| `cancelledStartup` | 首个提交前已取消或失败的独立样本，不混入成功启动统计 |
| `start/snapshot/acceptPages/update` | 主线程效果启动、位图捕获、传递页面及姿态更新；不包含此前的正文准备 |
| `canvasCapture/canvasLock/canvasRecord/canvasSubmit/canvasCopy` | 硬件捕获的记录、生产提交和 EGL 复制；实时来源的提交也会阻塞主线程 |
| `iRead.epub.record` | EPUB 同步录制准备，不含此前的 DOM 栅栏等待，也不代表 GPU 完成 |
| `readPixels/copyPixels/eglRestore` | 位图后端的回读、复制和上下文恢复 |

`startupToFirstSubmit` 不包含此前的候选正文准备，也不代表 GPU 已完成或图像已上屏。缺失终态的异步记录不计入成功样本。只观察 `upload` 或工作线程稳态会漏掉首帧前的主线程耗时。

同时检查 Window FrameMetrics 的 animation、input、draw、sync、commands、swap 和 total。unknown delay 按同一帧的 total 减去已知 CPU/窗口阶段估算，不包含另加 GPU 时间。各阶段的 P95 不能相加。首个输入帧按 vsync 关联后可能仍标记 Idle，整体结果必须保留。

直接 Surface 的父窗口指标不包含该 Surface 的 GPU 工作；报告通过 `metricsScope` 标明范围。柱状图变低或工作转移到其他线程，不等于整体帧成本下降。必要时使用独立诊断构建，在提交前临时用 `glFinish()` 等待 GPU 完成作交叉测量；正常实现不保留该同步。

主线程等待可进一步采集 ART v3 dual-clock 方法跟踪：

```sh
python tools/analyze-method-trace.py curl-method.trace --method nInvokeFunctor --thread main --output curl-method.json
```

向性能用例传入 `-e profileMethods true` 可生成 `files/ioscurl-method-<format>-<effect>.trace`，仅覆盖手势采样段，报告的 `methodTracing` 标明是否启用。采样包含误差、等待和调度，仅用于定位调用链；区分驱动等待与计算还需结合原生 RenderThread 采样，不能把采样构建用于最终验收。

## 当前性能边界

以下为 API 28/36 专用模拟器的串行测量，视口为 1080 × 2424、density 420，三页缓存、64MiB 效果快照预算、完整网格及原动画参数。每种内容均包含 8 次自动翻页和 4 组提交/取消手势，开启 GPU 柱状图，未开启方法采样。每组共有 49 次效果启动；阅读位置和取消结果均通过断言。

### API 28：稳定视口缓存与准备隔离层

旧版 WebView 的 VSync 失效通知会反复触发页面准备，交接时还会重新提交实时正文。当前实现把内容变化与纯重绘分开，在稳定视口复用有界像素；动态内容和不适合回放的模板使用原路径，详见[渲染说明](rendering.md)。原生文本未采用此项 EPUB 优化。

稳定视口缓存引入时，两版仍使用整页准备隔离层。相同 APK 测试入口与设备配置下，先运行原实现，再运行缓存实现，Window FrameMetrics 的 P95 如下；当前隔离层实现的结果见后续对照。

| 内容 | 原实现自动 | 缓存实现自动 | 原实现手势 | 缓存实现手势 |
| --- | ---: | ---: | ---: | ---: |
| 普通文本 | 24.78ms | 25.16ms | 25.15ms | 22.42ms |
| 重排 EPUB | 99.45ms | 52.14ms | 101.06ms | 27.17ms |
| ARM EPUB | 103.37ms | 55.49ms | 77.36ms | 25.69ms |

反向顺序复测中，重排 EPUB 的手势 P95 为 100.85 → 26.52ms，ARM EPUB 为 69.71 → 25.67ms。两个输出请求实际均为 Texture，不能称为旧系统直接 Surface 对照。文本手势数据存在波动，不能据此宣称原生文本获得确定收益。

首次对照的 EPUB 同步录制次数为 80 → 38，中位数为 40.81 → 10.14ms、P95 为 112.10 → 74.61ms；反向复测次数为 81 → 38、P95 为 105.83 → 60.30ms。这包含尚未优化的动态视口及新页准备，不能只报告较快的缓存命中。

准备中的旧版 WebView 随后改用非空的单像素透明隔离层，完整尺寸的软件页面捕获独立执行。原布局、页面快照尺寸、动画时长和网格均保持不变。相同稳定视口缓存下，先测整页隔离层，再测单像素隔离层：

| 内容 | 整页隔离自动 | 单像素隔离自动 | 整页隔离手势 | 单像素隔离手势 |
| --- | ---: | ---: | ---: | ---: |
| 普通文本 | 25.59ms | 25.20ms | 24.90ms | 25.07ms |
| 重排 EPUB | 44.89ms | 24.71ms | 26.29ms | 24.51ms |
| ARM EPUB | 44.28ms | 25.54ms | 26.38ms | 23.69ms |

反向顺序复测中，重排 EPUB 自动翻页 P95 为 57.55 → 20.32ms，ARM EPUB 为 43.19 → 26.01ms。收敛后的最终代码保留旧系统的软件准备优化，API 29 及以上仍完整准备 GPU 页面。最终代码的两次 API 28 复测中，重排 EPUB 自动翻页为 27.23/27.31ms、手势为 25.34/26.31ms；ARM EPUB 自动为 26.93/25.97ms、手势为 24.78/25.48ms。

最终代码的两次同步录制准备 P95 为 39.70/78.41ms，启动至首次提交 P95 为 31.02/35.06ms；窗口 swap P95 仍可达约 12–13ms。准备层改善没有消除新页准备、输入、上传和窗口交接的长帧。柱状图仍有红黄尖峰，不能将约 6ms 的 EGL 提交 P95 当作 GPU 完成或完整流程达标。

固定像素的独立硬件捕获也作过两轮对照：主线程 `start` 中位数降至约 1.1ms，但启动至首次提交中位数从约 22ms 增至约 37ms、P95 从 31.57–40.26ms 增至 48.92–55.11ms，因此未启用。仅缩短主线程柱段、把等待移到生产线程不能作为优化验收。

### API 36：当前两个输出的复测

此系统保留实时硬件捕获和原 RenderNode 准备，不安装旧版内容变化通道。以下是当前实现的窗口 P95，不作为两个输出的等范围比较：

| 内容 | Texture 自动 | Texture 手势 | Surface 自动 | Surface 手势 |
| --- | ---: | ---: | ---: | ---: |
| 普通文本 | 19.26ms | 10.64ms | 9.79ms | 12.03ms |
| 重排 EPUB | 9.56ms | 10.50ms | 9.88ms | 13.25ms |
| ARM EPUB | 8.79ms | 10.77ms | 9.45ms | 11.66ms |

Texture 的启动至首次提交 P95 为 45.36ms，Surface 为 41.32ms。文本 Texture 自动翻页的 swap P95 为 13.83ms，仍需继续定位。Surface 表中的父窗口不包含独立 Surface 的 GPU 成本；两种输出都不能宣称完整流程低于 8ms。EPUB 硬件录制准备 P95 约 1.4–1.7ms，仍保留其窗口恢复作用。

独立 GPU 完成等待诊断曾测得 API 28/36 的动画 GPU 样本 P95 为 7.73/7.65ms，最大值为 34.52/36.10ms。它不覆盖全部准备和输入帧，也不是上述窗口数据；不能与窗口阶段相加或替代最终验收。资源预热、纹理存储复用和较短的 EGL 提交都只有局部收益。

## 正确性与验收入口

- `IosCurlGpuDeviceTest` 对两个输出执行装订边、方向、轴向、RTL 和透明度矩阵；复用相同录制对象并改变图片像素，防止错误缓存。另用 `capture=bitmap/live` 验证位图和实时页面边界，以及取消、卸载和线程清理。
- `ReaderGestureHandoffTest.cancelledEpubDragKeepsBothPagesVisibleOnRetry` 用真实 EPUB 和 PixelCopy 验证前后翻、系统取消、反向复位、立即重试及取消后的正文恢复。传入 `-e output surface -e effect ios_curl` 检查直接 Surface 的完整屏幕合成。
- `EpubSnapshotRenderingTest` 覆盖插图、固定版式、ARM EPUB 的多列软件快照、真实窗口画面及 CSS 动画；`UnifiedSelectionIntegrationTest` 检查滚动选区同步。
- `EpubRecordingCacheTest` 检查当前页与备用页对原软件绘制的逐像素一致性、真实窗口误差、DOM/原生页眉失效、预算耗尽和录制中失效。窗口比较必须实际覆盖缓存回放，并断言参考窗口已经撤下缓存、回到实时 View 绘制，不能以两次缓存回放代替。
- `EpubHardwareCaptureTest` 在 API 29 及以上直接调用生产捕获适配器，比较当前页与停放目标页的捕获纹理和公开导航后的实际窗口，并在不同取消时点关闭真实 WebView、检查线程释放。它不替代完整手势交接检查。
- `IosCurlDemoTest` 检查公开入口、真实手势、多指、RTL、纵向、取消和资源失败；`BitmapRetentionDeviceTest` 检查异步读者结束前不回收图片。

API 28 备用页污染当前窗口的修复包括透明隔离层、按需绘制及当前页独占硬件层缓存，详见[正文渲染](rendering.md)。取消用例对两个输出选项均检查空闲页面及恢复后的正确颜色；`-e verifyFirstFrames true` 额外检查早期手势采样未出现空白。旧系统两个选项实际都走 Texture，不能据此声称直接 Surface 交接通过。纯色用例不能代替富文本像素检查。

画面与手势检查通过后再验收性能，不能放宽像素容差、缩短动画、减少帧数或只选稳态样本。设备测试、性能测量和[编译 CI](building.md)各自报告实际覆盖范围。
