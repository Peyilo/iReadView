# 翻页性能验证

当前仿真效果为 iOSCurl 和 GoogleCurl。iOSCurl 核心及背景取色见 [渲染说明](RENDERING.md)，通用内容交接见 [手势与 EPUB](GESTURE_AND_EPUB_HANDOFF.md)。

## 测量方法

`ReaderTurnPerformanceTest` 使用普通文本、同原文 EPUB 和默认 ARM EPUB，分开记录候选页准备、快照/效果启动、逐帧更新、逐帧绘制及 Window FrameMetrics。通用探针测试无动画、滑动、覆盖、视差滑动和 GoogleCurl；iOSCurl 的专用输入及缓动通过 `IosCurlDemoTest` 直接验证。

保留动画时长、屏幕尺寸、位图预算、内容和阅读位置一致，分别报告准备阶段与视觉阶段。无动画导航耗时不能当成动画帧率。跨章、首次载入和预加载未完成时的内容成本应单列。

## 当前约束

iOSCurl 使用 EGL 离屏绘制、同步 readback 和 Canvas 接入。CPU 几何数值一致不保证 GPU 等待或帧率一致。GoogleCurl 的 Canvas 网格成本依赖网格密度、内容与设备。预算下采样会影响纹理清晰度，不能将减少纹理尺寸后的数据当成原分辨率优化。

`performance/` 中已有 JSON 是此前版本的原始历史记录，仅对应其中的日期、配置和效果 ID。旧实现已移除，这些记录不代表当前发布包的能力，也不能用于宣称当前 iOSCurl 达到稳定60fps。新的跨格式性能结论必须重测，不能改写旧数据。
