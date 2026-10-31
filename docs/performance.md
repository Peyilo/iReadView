# 翻页性能与验收

优化以保持画面、手势和动画时长为前提。分别测量页面准备、效果启动、跟手更新、GPU 绘制及正文交接；导航总耗时、缓存命中和某一颜色柱段都不能单独证明动画流畅。全流程低于 8ms 的目标尚未通过。

## 线程与资源

Widget 负责手势仲裁、候选页准备和阅读位置提交。效果通过 `PageSurface` 获取页面，不读取书籍或 DOM，也不维护第二个帧时钟。iOSCurl 保留原版 0.25s begin、跟手轨迹和 settle 时间。

iOSCurl 的 EGL 工作线程只消费最新姿态，跨事务复用上下文、着色器、顶点缓冲与两块纹理存储。每次翻页完整更新页面像素，不按 View、Picture 或 Bitmap 身份跳过更新。硬件输出不逐帧回读；软件输出仍有 `readPixels` 和 CPU 复制成本。默认 Texture 与直接 Surface 的布局约束见[渲染说明](rendering.md)。

普通实时 View 和 EPUB 使用事务独占的像素快照。EPUB 的硬件显示列表可能借用 WebView 子 RenderNode，隐藏或停放正文会改变这些节点，不能作为独立快照。iOSCurl 通过独立读租约上传后归还 CPU 位图；取消、异常和卸载也必须结束租约。默认位图预算为 16MiB，实际尺寸受预算阶梯影响。

完全不透明的页面可使用按需准备的硬件 Canvas 捕获，EGL 将生产图像复制到事务独占的固定纹理后立即归还生产缓冲。独立 `RecordedPageSurface` 由主线程回放、生产线程提交；实时 View 和 EPUB 仅在 API 29 及以上启用，并在主线程完成提交，防止窗口遍历提前替换子 RenderNode。API 28 及以下的实时页面保留位图路径。未就绪、缺少背景元数据、预算或平台能力时也使用位图路径。

API 29 及以上在合成 View 就绪时提前预热捕获资源。预算在 UI 回调中读取，尺寸选择与翻页开始时共用同一策略；初次按需准备也使用实际请求的尺寸。预热不捕获页面内容、不提前推进动画、不改变既有分辨率阶梯。未完成预热的首次输入仍允许位图回退。

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

## 已验证的性能边界

保持完整网格和原分辨率的 API 28/36 模拟器对照中，复用纹理存储仍每轮完整上传两张新图，GL 错误查询未删除。使用相同三页缓存、直接 Surface 和 64MiB 预算，每组 49 次上传：

| API / 分配方式 | 上传中位数 | 上传 P95 | 上传至首次提交 P95 |
| --- | ---: | ---: | ---: |
| 28 / 每次新分配 | 13.71ms | 17.77ms | 25.91ms |
| 28 / 复用存储 | 12.87ms | 16.55ms | 25.23ms |
| 36 / 每次新分配 | 9.26ms | 14.19ms | 24.54ms |
| 36 / 复用存储 | 8.91ms | 13.24ms | 23.44ms |

这些数据说明分配复用的有限收益。相同记录中 EPUB 手势窗口 P95 并未一致改善：API 28 为 53.73 → 54.91ms，API 36 为 34.40 → 37.86ms。不能宣称全部内容或完整流程已达到 8ms。

独立 GPU 完成等待诊断中，API 28 的 764 帧中位数/P95/最大值为 5.60/7.73/34.52ms，API 36 的 831 帧为 4.12/7.65/36.10ms。这些是动画 GPU 样本，不能与窗口表相加，也不包括全部准备与输入帧。稳态 P95 低于 8ms 不代表首帧或每帧均达标。

使用相同三页缓存、直接 Surface、1080 × 2424 视口、64MiB 预算和原动画参数，API 36 的实时硬件捕获对照如下。每组各内容包含 8 次自动翻页和 4 组提交/取消手势，开启 GPU 柱状图，未开启方法采样：

| 内容 / 手势窗口 P95 | 位图捕获 | 硬件捕获 |
| --- | ---: | ---: |
| 普通文本 | 28.65ms | 18.64ms |
| 重排 EPUB | 38.67ms | 16.06ms |
| ARM EPUB | 41.63ms | 16.40ms |

49 次启动中，硬件方案有 46 次直接捕获，3 次首次预热使用位图；启动至首次提交的中位数为 32.43 → 33.87ms、P95 为 62.64 → 60.80ms。独立复测中硬件方案的启动 P95 为 66.14ms，EPUB 手势窗口 P95 为 22.13ms，自动翻页窗口 P95 为 38.99ms；改善未覆盖全部阶段。因此窗口输入阶段改善不代表首帧延迟已解决，也未达到完整流程 8ms 的目标。

API 28 在旧直接 Surface 配置下试验同方案，启动至首次提交 P95 从 44.22ms 增至 118.71ms，实时 Canvas 的提交 P95 达 53.71ms，未予启用。该旧配置也不作为当前 Texture 兼容路径的性能结果。不能以把快照工作移出效果 start 的计时范围来宣称启动更快。

API 36 的同条件预热对照中，49 次启动的软件快照回退从 3 次降至 0 次；效果 `start` 最大耗时从 29.09ms 降至 1.56ms，启动至首次提交 P95 从 52.46ms 降至 41.77ms。窗口手势 P95 的普通文本为 10.12 → 11.98ms、EPUB 为 10.86 → 11.33ms、ARM EPUB 为 11.08 → 9.91ms，改善并非覆盖每个窗口指标。此处使用三页缓存、直接 Surface、64MiB 预算、原动画参数及完整网格，未开启方法采样。

同配置的 Texture 输出复测也没有软件快照回退。普通文本、重排 EPUB、ARM EPUB 的自动翻页窗口 P95 分别为 19.07、8.80、8.83ms，手势窗口 P95 为 10.81、10.12、10.04ms。49 次启动至首次提交 P95 为 42.91ms；文本自动翻页的窗口 swap P95 为 15.61ms。GPU 柱状图仍有超限帧，不能依据 EGL 提交耗时或部分较低的颜色柱段判定完整流程达标。

EPUB 硬件录制准备 P95 约 1.3ms。虽然捕获路径不直接回放它，移除该准备后富文本窗口恢复的像素检查失败，因此保留；不能仅凭“无回放调用”认定它没有准备作用。

API 28 重启专用模拟器后，在相同视口、预算和动画参数下复测当前兼容路径，报告确认实际合成 View 为 Texture：

| 内容 / 窗口 P95 | 自动翻页 | 手势翻页 |
| --- | ---: | ---: |
| 普通文本 | 12.74ms | 16.74ms |
| 重排 EPUB | 81.16ms | 61.75ms |
| ARM EPUB | 79.88ms | 63.44ms |

同次采样包含 49 次启动和 1270 次 EGL 绘制提交；快照、上传、启动至首次提交的 P95 分别为 7.47、14.98、25.25ms。EGL 帧提交 P95 为 4.06ms，仍不能替代完整窗口或 GPU 完成指标。旧版原始 APK 在重启后的严格空闲页颜色检查中仍失败；修复后的同一检查和多效果取消回归通过。出现系统级 GL 错误、进程崩溃或未实际翻页的采样必须作废。

## 正确性与验收入口

- `IosCurlGpuDeviceTest` 对两个输出执行装订边、方向、轴向、RTL 和透明度矩阵；复用相同录制对象并改变图片像素，防止错误缓存。另用 `capture=bitmap/live` 验证位图和实时页面边界，以及取消、卸载和线程清理。
- `ReaderGestureHandoffTest.cancelledEpubDragKeepsBothPagesVisibleOnRetry` 用真实 EPUB 和 PixelCopy 验证前后翻、系统取消、反向复位、立即重试及取消后的正文恢复。传入 `-e output surface -e effect ios_curl` 检查直接 Surface 的完整屏幕合成。
- `EpubSnapshotRenderingTest` 覆盖插图、固定版式、ARM EPUB 的多列软件快照、真实窗口画面及 CSS 动画；`UnifiedSelectionIntegrationTest` 检查滚动选区同步。
- `EpubHardwareCaptureTest` 在 API 29 及以上直接调用生产捕获适配器，比较当前页与停放目标页的捕获纹理和公开导航后的实际窗口，并在不同取消时点关闭真实 WebView、检查线程释放。它不替代完整手势交接检查。
- `IosCurlDemoTest` 检查公开入口、真实手势、多指、RTL、纵向、取消和资源失败；`BitmapRetentionDeviceTest` 检查异步读者结束前不回收图片。

API 28 备用页污染当前窗口的修复包括透明隔离层、按需绘制及当前页独占硬件层缓存，详见[正文渲染](rendering.md)。取消用例对两个输出选项均检查空闲页面及恢复后的正确颜色；`-e verifyFirstFrames true` 额外检查早期手势采样未出现空白。旧系统两个选项实际都走 Texture，不能据此声称直接 Surface 交接通过。纯色用例不能代替富文本像素检查。

画面与手势检查通过后再验收性能，不能放宽像素容差、缩短动画、减少帧数或只选稳态样本。设备测试、性能测量和[编译 CI](building.md)各自报告实际覆盖范围。
