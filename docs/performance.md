# 翻页性能验证

当前仿真效果为 iOSCurl 和 GoogleCurl。iOSCurl 核心及背景取色见 [渲染说明](rendering.md)，通用内容交接见 [手势与 EPUB](performance.md)。

## 测量方法

`ReaderTurnPerformanceTest` 使用普通文本、同原文 EPUB 和默认 ARM EPUB，分开记录候选页准备、快照/效果启动、逐帧更新、逐帧绘制及 Window FrameMetrics。通用探针测试无动画、滑动、覆盖、视差滑动和 GoogleCurl；iOSCurl 的专用输入及缓动通过 `IosCurlDemoTest` 直接验证。

保留动画时长、屏幕尺寸、位图预算、内容和阅读位置一致，分别报告准备阶段与视觉阶段。无动画导航耗时不能当成动画帧率。跨章、首次载入和预加载未完成时的内容成本应单列。

## 当前约束

iOSCurl 使用 EGL 离屏绘制、同步 readback 和 Canvas 接入。CPU 几何数值一致不保证 GPU 等待或帧率一致。GoogleCurl 的 Canvas 网格成本依赖网格密度、内容与设备。预算下采样会影响纹理清晰度，不能将减少纹理尺寸后的数据当成原分辨率优化。

## 手势响应与正文交接

### 职责

Widget 负责内容手势仲裁、长按选择、候选页准备和阅读位置提交。翻页效果只消费事务中的页面；TXT、EPUB 和自定义正文都通过同一 `PageSurface` 合约提供内容。

开始视觉事务后固定持有快照/录制资源。快照播放期间保持正文的可见生命周期，暂时将其 alpha 设为0；`ReadVisualHandoff` 在遮罩仍覆盖时准备目标正文，随后原子提交位置并撤掉遮罩。取消、异常和配置变更恢复正文及资源所有权。

iOSCurl 使用自己的原版识别、0.25s begin 过渡和独立缓动轨道，仍由管理器提供帧时钟；GoogleCurl 及平移效果使用通用进度。手指停止移动后，iOSCurl 仅在 begin 预热尚未结束时继续请求时钟帧，不用独立定时器改变阅读位置。

EPUB 内容准备与视觉算法分层。三页缓存、录制资源和内容交接遵循 [正文层](reader-body.md) 与 [分页缓存](page-cache.md) 的所有权规则；效果本身不通过 JS 加载或定位页面。

EPUB 的硬件录制持有真实页面的子 RenderNode，能够在硬件 Canvas 上回放不等于可以作为不可变快照。隐藏或停放 WebView 可能替换这些节点的显示列表，造成取消后再次翻页只剩阴影。EPUB 因此声明 `SnapshotPreferredSurface`，不再声明 `RecordedPageSurface`；滑动、覆盖、视差滑动与两种 curl 都在隐藏正文之前捕获事务独占的像素，直到动画结束才归还租约。

像素快照遵守已有预算和降采样阶梯。隔离失败时，平移效果请求 `ResourcesUnavailable` 降级，仍保留原手势的完成/复位决定，不回退到不稳定的 WebView 显示列表。动画结束归还像素租约，三页内容缓存照常保留，书籍内容和阅读位置不受影响。

### 设备验证入口

- `IosCurlDemoTest` 直接使用原工厂验证前后翻、取消、多指、RTL、纵向、设置恢复和资源失败后的手势判定。
- `IosCurlBackgroundDeviceTest` 验证实际卷曲正面的主题取色和夜间背面。
- `ReaderGestureHandoffTest` 的通用探针覆盖平移、GoogleCurl 及 TXT/EPUB 页面交接，支持通过 `effect` 限定已有项目。
- `cancelledEpubDragKeepsBothPagesVisibleOnRetry` 直接使用五种原工厂，通过 PixelCopy 检查前后翻、系统取消、手指反向退回复位，以及立即/预加载后重试时两页的可见内容，并验证取消不提交位置、重试仍能完成翻页。
- `ReaderTurnPerformanceTest` 区分页面准备、效果启动、更新、绘制和窗口 FrameMetrics，不能用导航总时长替代帧率。
