# 翻页性能验证

当前仿真效果为 iOSCurl 和 GoogleCurl。iOSCurl 核心及背景取色见 [渲染说明](rendering.md)，通用内容交接见下文“手势响应与正文交接”。

## 测量方法

`ReaderTurnPerformanceTest` 使用普通文本、同原文 EPUB 和默认 ARM EPUB，分开记录候选页准备、快照/效果启动、逐帧更新、逐帧绘制及 Window FrameMetrics。通用探针测试无动画、滑动、覆盖、视差滑动和 GoogleCurl；`IosCurlPerformanceTest` 直接使用原工厂测量 iOSCurl 自动翻页与真实拖动，保留专用输入和缓动能力。

保留动画时长、屏幕尺寸、位图预算、内容和阅读位置一致，分别报告准备阶段与视觉阶段。无动画导航耗时不能当成动画帧率。跨章、首次载入和预加载未完成时的内容成本应单列。

窗口报告同时包含 input、unknown、animation、layout、draw、sync、commands 和 swap。`unattributed` 是同一帧 total 减去公开 CPU 阶段之和，不代表单独测得的 GPU 时间；GPU 指标不参与该减法。诊断时查看同一帧的分项，不能相加各项独立的 P95。首个输入帧按 vsync 关联时可能仍标记为 Idle，整体结果必须保留这些帧。

## 当前约束

iOSCurl 硬件路径在独占 EGL 的工作线程生成网格、上传纹理并提交图像，主线程只提交最新姿态；没有逐帧 readback。默认 Texture 保留普通 View 语义，Demo 固定正文矩形使用直接 Surface；两种后端的能力边界见[渲染说明](rendering.md)。软件或旧宿主保留离屏位图路径，仍有同步 readback 成本。CPU 几何数值一致不保证 GPU 等待或帧率一致。GoogleCurl 的 Canvas 网格成本依赖网格密度、内容与设备。预算下采样会影响纹理清晰度，不能将减少纹理尺寸后的数据当成原分辨率优化。

## 手势响应与正文交接

### 职责

Widget 负责内容手势仲裁、长按选择、候选页准备和阅读位置提交。翻页效果只消费事务中的页面；TXT、EPUB 和自定义正文都通过同一 `PageSurface` 合约提供内容。

开始视觉事务后固定持有快照/录制资源。异步首帧真正就绪前保持正文；快照播放期间保持正文的可见生命周期，通过正文的快照播放通知停止绘制，或在默认策略中将 alpha 设为0；`ReadVisualHandoff` 在遮罩仍覆盖时准备目标正文，随后原子提交位置并撤掉遮罩。取消、异常和配置变更恢复正文及资源所有权。

iOSCurl 使用自己的原版识别、0.25s begin 过渡和独立缓动轨道，仍由管理器提供帧时钟；GoogleCurl 及平移效果使用通用进度。手指停止移动后，iOSCurl 仅在 begin 预热尚未结束时继续请求时钟帧，不用独立定时器改变阅读位置。

EPUB 内容准备与视觉算法分层。三页缓存、录制资源和内容交接遵循 [正文层](reader-body.md) 与 [分页缓存](page-cache.md) 的所有权规则；效果本身不通过 JS 加载或定位页面。

EPUB 的硬件录制持有真实页面的子 RenderNode，能够在硬件 Canvas 上回放不等于可以作为不可变快照。隐藏或停放 WebView 可能替换这些节点的显示列表，造成取消后再次翻页只剩阴影。EPUB 因此声明 `SnapshotPreferredSurface`，不再声明 `RecordedPageSurface`；滑动、覆盖、视差滑动与两种 curl 都在隐藏正文之前捕获事务独占的像素。平移和 GoogleCurl 保留像素到动画结束；iOSCurl 硬件路径在上传完成、独立读租约结束后归还 CPU 位图，GPU 纹理保留到事务结束。直接 Surface 的正文保持绘制，最终缓冲区在父窗口交接 fence 完成前保留。

像素快照遵守已有预算和降采样阶梯。隔离失败时，平移效果请求 `ResourcesUnavailable` 降级，仍保留原手势的完成/复位决定，不回退到不稳定的 WebView 显示列表。动画结束归还像素租约，三页内容缓存照常保留，书籍内容和阅读位置不受影响。

### 设备验证入口

- `IosCurlDemoTest` 直接使用原工厂验证前后翻、取消、多指、RTL、纵向、设置恢复和资源失败后的手势判定。
- `IosCurlBackgroundDeviceTest` 验证实际卷曲正面的主题取色和夜间背面。
- `ReaderGestureHandoffTest` 的通用探针覆盖平移、GoogleCurl 及 TXT/EPUB 页面交接，支持通过 `effect` 限定已有项目。
- `cancelledEpubDragKeepsBothPagesVisibleOnRetry` 直接使用五种原工厂，通过 PixelCopy 检查前后翻、系统取消、手指反向退回复位，以及立即/预加载后重试时两页的可见内容，并验证取消不提交位置、重试仍能完成翻页。传入 `-e output surface -e effect ios_curl` 时通过屏幕合成截图检查直接 Surface，额外验证取消后恢复的正文颜色，避免只复制父窗口而漏掉 Surface。
- `ReaderTurnPerformanceTest` 区分页面准备、效果启动、更新、绘制和窗口 FrameMetrics，不能用导航总时长替代帧率。

## iOSCurl 复测入口

先安装 Demo 和 AndroidTest APK，在选定设备上执行：

```sh
adb shell am instrument -w -r \
  -e class org.peyilo.iread.demo.IosCurlPerformanceTest \
  -e formats text,epub,arm -e effects ios_curl -e turns 8 -e gesturePairs 4 \
  -e cacheMode three -e rawFrames true \
  org.peyilo.iread.demo.test/androidx.test.runner.AndroidJUnitRunner
```

用 `adb -s` 显式指定设备，避免操作其他正在使用的模拟器。测试设备需解锁，并完成首次全屏模式提示。手势注入必须标明触屏来源，并验证完成/取消后的实际位置；没有触发翻页的采样无效。报告写入 Demo 私有目录的 `files/ioscurl-performance.json`。

默认每种内容执行 8 次自动翻页和 4 组提交/取消手势，共 8 次真实拖动。`cacheMode` 可选 `three`（默认）或 `single`，结果记录实际缓存模式。`rawFrames` 可选，启用后保存带 vsync 和导航状态的逐帧数据。性能采样应串行运行，避免并行构建、其他设备用例或采样分析器争用宿主资源。

采集 `atrace --async_start -b 16384 -a org.peyilo.iread.demo gfx view`，测试结束后用 `--async_stop -b 16384 -a org.peyilo.iread.demo -o /data/local/tmp/curl.trace gfx view` 保存并拉取。分析命令：

```sh
python tools/analyze-curl-trace.py curl.trace --output curl-stages.json
```

`iRead.curl.mesh/upload/raster/drawBatches/present` 位于 GPU 工作线程；位图后端另有 `readPixels/copyPixels/eglRestore`。同时检查 Window 的 animation、draw、sync、commands、swap，不能只看其中一种颜色柱段，也不能以转移到其他线程作为流畅性的唯一依据。原始 trace 和设备报告放在未跟踪的构建目录。

上传进一步拆为 `iRead.curl.uploadPage`（每张图的 `texImage2D` 调用）和 `iRead.curl.uploadCheck`（驱动错误查询）。后者可能包含前序 GPU 命令等待，不能当成纯 CPU 校验成本，也不能删除校验后把转移到 present 的等待算作优化。

复用存储时 `uploadPage` 对应完整的 `texSubImage2D` 更新。分析工具另报告 `iRead.curl.uploadToFirstSubmit`：在同一工作线程上，从一次上传开始到其后首个包含 present 的 curl 帧返回；没有首帧的已取消上传不计入，新上传覆盖此前未消费的起点。它包含工作线程内的等待，不包含上传前的 UI 准备，也不保证 GPU 已完成或图像已上屏，必须和窗口及 GPU 完成指标一起看。

同条件的一次复测记录如下；各阶段分位数不能相加，也不能替代前述整帧和窗口指标：

| 模拟器 / 阶段 | 样本 | 中位数 | P95 |
| --- | ---: | ---: | ---: |
| API 28 / 单页上传 | 98 | 4.02ms | 6.91ms |
| API 28 / 上传后查询 | 49 | 5.32ms | 7.63ms |
| API 36 / 单页上传 | 98 | 4.55ms | 6.99ms |
| API 36 / 上传后查询 | 49 | 0.02ms | 0.16ms |

API 28 的查询等待是页面首帧成本的一部分；优化仍需覆盖准备、实际提交和交接，不能只消除测量点。

硬件工作线程现在保留两块纹理存储，每次翻页仍完整上传两张新捕获的图片。同尺寸/格式使用 [GLUtils.texSubImage2D](https://developer.android.com/reference/android/opengl/GLUtils) 更新；尺寸、格式改变时使用 `texImage2D`。存储在 Surface/效果释放时销毁，不持有额外 CPU 快照，GL 错误检查仍在上传末尾。推迟检查的实验没有获得一致的首帧收益，未采用。

三页缓存、直接 Surface、相同 64MiB 快照预算下，对原分配方式和存储复用分别运行三种内容的完整自动/手势用例。每组包含 49 次上传，结果为：

| API / 分配方式 | 上传中位数 | 上传 P95 | 上传至首次提交 P95 |
| --- | ---: | ---: | ---: |
| 28 / 每次新分配 | 13.71ms | 17.77ms | 25.91ms |
| 28 / 复用存储 | 12.87ms | 16.55ms | 25.23ms |
| 36 / 每次新分配 | 9.26ms | 14.19ms | 24.54ms |
| 36 / 复用存储 | 8.91ms | 13.24ms | 23.44ms |

同进程交替执行新分配和更新的独立诊断，用两张 1080 × 2424 图片、相同 EGL 上下文与小离屏输出隔离上传成本；预热后每种方式各 40 个样本，并等待 GPU 完成。API 28 的上传中位数为 15.43 → 13.32ms，API 36 为 12.24 → 8.83ms。小离屏输出不代表完整屏幕光栅成本，该诊断不能替代实际翻页验收。

这项优化没有消除窗口长尾：同次对照中，API 28 同原文 EPUB 手势窗口 P95 为 53.73 → 54.91ms，API 36 为 34.40 → 37.86ms，不能宣称全流程改善或达到 8ms。画面回归增加尺寸、格式与像素变化后和新分配逐像素比较；试验过的整页容器硬件层因取消后出现空白而撤回，原 WebView 图层及交接策略保持不变。

缓存录制对象的身份不足以证明像素未变：软件 Picture 可以引用可变 Bitmap。`IosCurlGpuDeviceTest` 重用相同表面和绘制命令、逐轮修改图片，并与未缓存的像素结果对比；`EpubSnapshotRenderingTest` 检查三页预加载不会透过半透明模板显示备用文档。没有通过这些像素边界的复用方案不进入实现。

GPU 柱状图验收可在测试设备上启用 `adb shell setprop debug.hwui.profile visual_bars`，向性能用例添加 `-e captureProfiles true`。用例在每种内容的自动/手势采样后保存 `files/curl-profile-<format>-<kind>.png`，便于对照完整柱状图及参考线。结束后用 `adb shell setprop debug.hwui.profile false` 关闭。截图在采样结束后保存，不把截图压缩时间计入动画指标。

### 主线程等待分析

ART 采样方法跟踪可用于定位主线程的 Java 调用链，使用设备的 `am profile start --sampling` 和 `am profile stop` 采集，再拉取文件分析：

```sh
python tools/analyze-method-trace.py curl-method.trace --method nInvokeFunctor --thread main --output curl-method.json
```

工具接受非流式 ART v3 dual-clock 格式，报告采样到的包含子调用的 wall/CPU 时间、祖先栈和溢出标记。采样时间有误差，wall 包含等待和调度；带采样器的数据仅用于定位，不用于最终性能验收。需要区分驱动等待与实际计算时，再结合 RenderThread 原生采样，不能仅凭 Java 方法名称判断瓶颈。

## 当前优化及验收范围

保留完整网格、原分辨率、原版手势、0.25s begin 和 settle 时间。优化包括工作线程最新姿态邮箱、批次合并上传、独占 EGL 上下文、停止 Widget/合成器重复重绘、EPUB 分页视口硬件层缓存及停放页的空裁剪绘制。完全不透明的内部快照跳过重复清零，透明输入和公开租约行为不变；窗口交接避免排队回调重复发起 PixelCopy。没有通过减少帧数、缩短动画、降低网格密度或纹理尺寸取得数据。

分页硬件层避免旧 WebView 在父窗口每次绘制时重复进入绘制器。软件快照直接绘制 WebView，防止框架的软件绘图缓存捕获空硬件层。`EpubSnapshotRenderingTest` 验证三种出版物的多列快照与原软件路径逐像素一致，并检查实际窗口画面和 CSS 动画；连续滚动保持默认图层，选区同步另由 `UnifiedSelectionIntegrationTest` 验证。

1080 × 2424、density 420 的 API 28/36 模拟器，使用文本、同原文 EPUB、ARM EPUB，分别完成 8 次自动前后翻及真实提交/反向取消手势。性能用例将位图预算固定为 64MiB，容纳两张原分辨率快照；这高于公共选项默认的 16MiB，不能将结果视为默认预算下的清晰度和性能保证。直接 Surface 的父窗口 FrameMetrics **不包含该 Surface 的 GPU 工作**，柱状图变低不能单独作为验收依据。报告明确标记 `metricsScope`；增加 `-e output surface` 可运行该输出，省略时测默认 Texture。

为核对 GPU 完成成本，在独立测量构建中，于 drawBatches 后、present 前临时插入带 trace 的 `GLES20.glFinish()`，将其等待计入 `iRead.curl.frame`。该同步仅用于交叉测量，已从正常实现移除，不能把 GL 提交返回时间当作 GPU 完成时间。两次记录为：

| 模拟器 | 动画帧样本 | 含 GPU 完成等待的中位数 | P95 | 最大值 |
| --- | ---: | ---: | ---: | ---: |
| API 28 | 764 | 5.60ms | 7.73ms | 34.52ms |
| API 36 | 831 | 4.12ms | 7.65ms | 36.10ms |

上述独立 GPU 帧样本的 P95 低于 8ms，**不表示全部帧或首帧均低于 8ms**。首帧纹理上传、正文准备/交接及系统调度仍有长尾；该次记录中 API 28 的整次纹理上传 P95 为 23.74ms，API 36 为 10.97ms。

原生与 EPUB 都将完整准备、代次仍有效的取消候选归还有界缓存，避免下一次导航重新创建相同正文；不完整候选仍释放，单页模式容量为零。EPUB 只在取消交接边界刷新当前页硬件层，防止保留候选后恢复空白。正文数量上限仍为三份，动画参数和像素容差不变。

以下对照只切换上述取消复用实现，其余源码、用例和参数相同：三页缓存、直接 Surface、GPU 柱状图、每种内容 8 次自动翻页和 4 组提交/取消手势。原实现与当前实现的 APK 串行运行，窗口采样包含输入与交接；没有把原实现改成单页模式来比较。

| API / 内容 / 操作 | 取消复用前帧数 | 取消复用后帧数 | 取消复用前 total P95 | 取消复用后 total P95 |
| --- | ---: | ---: | ---: | ---: |
| 28 / 文本 / 自动 | 27 | 30 | 26.15ms | 30.07ms |
| 28 / 文本 / 手势 | 35 | 31 | 34.79ms | 35.80ms |
| 28 / 同原文 EPUB / 自动 | 41 | 42 | 87.43ms | 69.86ms |
| 28 / 同原文 EPUB / 手势 | 56 | 35 | 98.39ms | 52.41ms |
| 28 / ARM EPUB / 自动 | 47 | 48 | 56.93ms | 59.96ms |
| 28 / ARM EPUB / 手势 | 58 | 48 | 119.73ms | 63.21ms |
| 36 / 文本 / 自动 | 26 | 28 | 12.23ms | 14.65ms |
| 36 / 文本 / 手势 | 34 | 31 | 31.14ms | 27.95ms |
| 36 / 同原文 EPUB / 自动 | 41 | 40 | 30.53ms | 34.02ms |
| 36 / 同原文 EPUB / 手势 | 51 | 36 | 34.04ms | 44.29ms |
| 36 / ARM EPUB / 自动 | 40 | 41 | 28.01ms | 45.32ms |
| 36 / ARM EPUB / 手势 | 50 | 36 | 53.03ms | 43.11ms |

API 28 的同原文 EPUB 手势 commands P95 从 54.36ms 降至 44.15ms，ARM EPUB 从 47.88ms 降至 43.08ms；大于 32ms 的手势窗口帧分别从 34 降至 20、31 降至 24。取消后不再销毁有效正文的收益由对象身份测试验证，窗口长尾仍受输入和调度影响。API 36 同原文 EPUB 的手势 P95 反而升高，自动翻页也没有一致收益，因此不能宣称所有内容、模式和指标均改善。

这里是父窗口样本，不能与 GPU 帧表相加，也不包括每一帧直接 Surface 输出；窗口采样数量减少不等于动画帧数减少。API 28 的命令提交和 unknown delay、两个系统的首个输入帧仍有明显长尾；柱状图也保留高柱。全流程 8ms 目标尚未通过，后续应继续优化资源预备和交接，不以稳态 GPU 数据掩盖边界耗时，也不将有限模拟器结果推广为所有设备保证。

直接 Surface 的完整画面对比覆盖 32 组输入，包含透明回退、装订边、轴向、RTL 和前后方向。对比失败的 Canvas/Mesh 实验路径未进入实现；像素容差没有为性能结果放宽。
