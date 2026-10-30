# 翻页性能与验收

优化以保持画面、手势和动画时长为前提。分别测量页面准备、效果启动、跟手更新、GPU 绘制及正文交接；导航总耗时、缓存命中和某一颜色柱段都不能单独证明动画流畅。全流程低于 8ms 的目标尚未通过。

## 线程与资源

Widget 负责手势仲裁、候选页准备和阅读位置提交。效果通过 `PageSurface` 获取页面，不读取书籍或 DOM，也不维护第二个帧时钟。iOSCurl 保留原版 0.25s begin、跟手轨迹和 settle 时间。

iOSCurl 的 EGL 工作线程只消费最新姿态，跨事务复用上下文、着色器、顶点缓冲与两块纹理存储。每次翻页完整更新页面像素，不按 View、Picture 或 Bitmap 身份跳过更新。硬件输出不逐帧回读；软件输出仍有 `readPixels` 和 CPU 复制成本。默认 Texture 与直接 Surface 的布局约束见[渲染说明](rendering.md)。

普通实时 View 和 EPUB 使用事务独占的像素快照。EPUB 的硬件显示列表可能借用 WebView 子 RenderNode，隐藏或停放正文会改变这些节点，不能作为独立快照。iOSCurl 通过独立读租约上传后归还 CPU 位图；取消、异常和卸载也必须结束租约。默认位图预算为 16MiB，实际尺寸受预算阶梯影响。

完全不透明的独立 `RecordedPageSurface` 可使用按需准备的硬件 Canvas 捕获：主线程回放、生产线程提交、EGL 复制到固定纹理。未就绪、缺少背景元数据、预算或平台能力时使用位图路径。实时 View 和 EPUB 不使用此捕获路径；API 28 的实时 WebView 捕获曾出现目标页缺失，因此不能以独立页面的像素检查替代真实正文交接回归。

文本与 EPUB 共用[分页缓存](page-cache.md)。完整且有效的取消候选归还缓存，未完成或过期候选释放。EPUB 分页视口使用硬件层，软件快照直接绘制 WebView；取消交接刷新当前层，连续滚动保留默认图层。直接 Surface 保持下方正文，并在恢复后的 Window 绘制及一像素 GPU fence 完成后清空最终图像。

## 设备复测

先构建并安装 Demo 与 AndroidTest APK，在选定且已解锁的设备上执行：

```sh
adb shell am instrument -w -r \
  -e class org.peyilo.iread.demo.IosCurlPerformanceTest \
  -e formats text,epub,arm -e effects ios_curl -e turns 8 -e gesturePairs 4 \
  -e cacheMode three -e rawFrames true \
  org.peyilo.iread.demo.test/androidx.test.runner.AndroidJUnitRunner
```

存在多个设备时用 `adb -s` 指定目标。默认使用 Texture；`-e output surface` 切换直接 Surface。`cacheMode` 可选 `three` 或 `single`，结果记录实际策略。默认每种内容包含 8 次自动翻页、4 组提交/取消手势；必须验证实际阅读位置，未触发翻页的采样无效。不同系统、输出、缓存及内容串行测量，不同时运行构建或其他设备用例。

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
| `canvasCapture/canvasLock/canvasRecord/canvasSubmit/canvasCopy` | 独立录制页面的硬件捕获阶段；实时页面不会产生这些样本 |
| `readPixels/copyPixels/eglRestore` | 位图后端的回读、复制和上下文恢复 |

`startupToFirstSubmit` 不包含此前的候选正文准备，也不代表 GPU 已完成或图像已上屏。缺失终态的异步记录不计入成功样本。只观察 `upload` 或工作线程稳态会漏掉首帧前的主线程耗时。

同时检查 Window FrameMetrics 的 animation、input、draw、sync、commands、swap 和 total。unknown delay 按同一帧的 total 减去已知 CPU/窗口阶段估算，不包含另加 GPU 时间。各阶段的 P95 不能相加。首个输入帧按 vsync 关联后可能仍标记 Idle，整体结果必须保留。

直接 Surface 的父窗口指标不包含该 Surface 的 GPU 工作；报告通过 `metricsScope` 标明范围。柱状图变低或工作转移到其他线程，不等于整体帧成本下降。必要时使用独立诊断构建，在提交前临时用 `glFinish()` 等待 GPU 完成作交叉测量；正常实现不保留该同步。

主线程等待可进一步采集 ART v3 dual-clock 方法跟踪：

```sh
python tools/analyze-method-trace.py curl-method.trace --method nInvokeFunctor --thread main --output curl-method.json
```

采样包含误差、等待和调度，仅用于定位调用链；区分驱动等待与计算还需结合原生 RenderThread 采样，不能把采样构建用于最终验收。

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

## 正确性与验收入口

- `IosCurlGpuDeviceTest` 对两个输出执行装订边、方向、轴向、RTL 和透明度矩阵；复用相同录制对象并改变图片像素，防止错误缓存。另用 `capture=bitmap/live` 验证位图和实时页面边界，以及取消、卸载和线程清理。
- `ReaderGestureHandoffTest.cancelledEpubDragKeepsBothPagesVisibleOnRetry` 用真实 EPUB 和 PixelCopy 验证前后翻、系统取消、反向复位、立即重试及取消后的正文恢复。传入 `-e output surface -e effect ios_curl` 检查直接 Surface 的完整屏幕合成。
- `EpubSnapshotRenderingTest` 覆盖插图、固定版式、ARM EPUB 的多列软件快照、真实窗口画面及 CSS 动画；`UnifiedSelectionIntegrationTest` 检查滚动选区同步。
- `IosCurlDemoTest` 检查公开入口、真实手势、多指、RTL、纵向、取消和资源失败；`BitmapRetentionDeviceTest` 检查异步读者结束前不回收图片。

API 28 的直接 Surface 取消交接用例仍可复现恢复后显示目标页，修改前的位图捕获 APK 同样失败；默认 Texture 的同一用例通过。该问题属于未通过的显示边界，不能将独立页面的 GPU 像素矩阵通过写成 EPUB 交接已通过。

画面与手势检查通过后再验收性能，不能放宽像素容差、缩短动画、减少帧数或只选稳态样本。设备测试、性能测量和[编译 CI](building.md)各自报告实际覆盖范围。
