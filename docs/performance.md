# 翻页性能验证

当前仿真效果为 iOSCurl 和 GoogleCurl。iOSCurl 核心及背景取色见 [渲染说明](rendering.md)，通用内容交接见下文“手势响应与正文交接”。

## 测量方法

`ReaderTurnPerformanceTest` 使用普通文本、同原文 EPUB 和默认 ARM EPUB，分开记录候选页准备、快照/效果启动、逐帧更新、逐帧绘制及 Window FrameMetrics。通用探针测试无动画、滑动、覆盖、视差滑动和 GoogleCurl；`IosCurlPerformanceTest` 直接使用原工厂测量 iOSCurl 自动翻页与真实拖动，保留专用输入和缓动能力。

保留动画时长、屏幕尺寸、位图预算、内容和阅读位置一致，分别报告准备阶段与视觉阶段。无动画导航耗时不能当成动画帧率。跨章、首次载入和预加载未完成时的内容成本应单列。

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
  -e formats text,epub,arm -e effects ios_curl -e turns 8 \
  org.peyilo.iread.demo.test/androidx.test.runner.AndroidJUnitRunner
```

用 `adb -s` 显式指定设备，避免操作其他正在使用的模拟器。测试设备需解锁，并完成首次全屏模式提示。手势注入必须标明触屏来源，并验证完成/取消后的实际位置；没有触发翻页的采样无效。报告写入 Demo 私有目录的 `files/ioscurl-performance.json`。

采集 `atrace --async_start -b 16384 -a org.peyilo.iread.demo gfx view`，测试结束后用 `--async_stop -b 16384 -a org.peyilo.iread.demo -o /data/local/tmp/curl.trace gfx view` 保存并拉取。分析命令：

```sh
python tools/analyze-curl-trace.py curl.trace --output curl-stages.json
```

`iRead.curl.mesh/upload/raster/drawBatches/present` 位于 GPU 工作线程；位图后端另有 `readPixels/copyPixels/eglRestore`。同时检查 Window 的 animation、draw、sync、commands、swap，不能只看其中一种颜色柱段，也不能以转移到其他线程作为流畅性的唯一依据。原始 trace 和设备报告放在未跟踪的构建目录。

GPU 柱状图验收可在测试设备上启用 `adb shell setprop debug.hwui.profile visual_bars`，向性能用例添加 `-e captureProfiles true`。用例在每种内容的自动/手势采样后保存 `files/curl-profile-<format>-<kind>.png`，便于对照完整柱状图及参考线。结束后用 `adb shell setprop debug.hwui.profile false` 关闭。截图在采样结束后保存，不把截图压缩时间计入动画指标。

## 当前优化及验收范围

保留完整网格、原分辨率、原版手势、0.25s begin 和 settle 时间。优化包括工作线程最新姿态邮箱、批次合并上传、独占 EGL 上下文、停止 Widget/合成器重复重绘，以及 EPUB 停放页的空裁剪绘制。没有通过减少帧数、缩短动画、降低网格密度或纹理尺寸取得数据。

1080 × 2424、density 420 的 API 28/36 模拟器，使用文本、同原文 EPUB、ARM EPUB，分别完成 8 次自动前后翻及真实提交/反向取消手势。直接 Surface 的父窗口 FrameMetrics **不包含该 Surface 的 GPU 工作**，柱状图变低不能单独作为验收依据。报告明确标记 `metricsScope`；增加 `-e output surface` 可运行该输出，省略时测默认 Texture。

为核对 GPU 完成成本，在独立测量构建中，于 drawBatches 后、present 前临时插入带 trace 的 `GLES20.glFinish()`，将其等待计入 `iRead.curl.frame`。该同步仅用于交叉测量，已从正常实现移除，不能把 GL 提交返回时间当作 GPU 完成时间。两次记录为：

| 模拟器 | 动画帧样本 | 含 GPU 完成等待的中位数 | P95 | 最大值 |
| --- | ---: | ---: | ---: | ---: |
| API 28 | 764 | 5.60ms | 7.73ms | 34.52ms |
| API 36 | 831 | 4.12ms | 7.65ms | 36.10ms |

这说明所测设备的动画帧 P95 已低于 8ms，**不表示全部帧或首帧均低于 8ms**。首帧纹理上传、正文准备/交接及系统调度仍有长尾；API 28 的整次纹理上传 P95 为 23.74ms，API 36 为 10.97ms。全流程 8ms 目标尚未全部验收，后续应继续优化资源预备和交接，不以这张表掩盖边界耗时，也不将有限模拟器结果推广为所有设备保证。

直接 Surface 的完整画面对比覆盖 32 组输入，包含透明回退、装订边、轴向、RTL 和前后方向。对比失败的 Canvas/Mesh 实验路径未进入实现；像素容差没有为性能结果放宽。
