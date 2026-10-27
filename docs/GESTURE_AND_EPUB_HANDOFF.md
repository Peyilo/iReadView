# 手势响应与 EPUB 正文交接

## 当前职责

Widget 负责内容手势仲裁、长按选择、候选页准备和阅读位置提交。翻页效果只消费事务中的页面；TXT、EPUB 和自定义正文都通过同一 `PageSurface` 合约提供内容。

开始视觉事务后固定持有快照/录制资源。快照播放期间保持正文的可见生命周期，暂时将其 alpha 设为0；`ReadVisualHandoff` 在遮罩仍覆盖时准备目标正文，随后原子提交位置并撤掉遮罩。取消、异常和配置变更恢复正文及资源所有权。

iOSCurl 使用自己的原版识别、0.25s begin 过渡和独立缓动轨道，仍由管理器提供帧时钟；GoogleCurl 及平移效果使用通用进度。手指停止移动后，iOSCurl 仅在 begin 预热尚未结束时继续请求时钟帧，不用独立定时器改变阅读位置。

EPUB 内容准备与视觉算法分层。三页缓存、录制资源和内容交接遵循 [正文层](READER_BODY.md) 与 [分页缓存](PAGE_CACHE.md) 的所有权规则；效果本身不通过 JS 加载或定位页面。

## 验证入口

- `IosCurlDemoTest` 直接使用原工厂验证前后翻、取消、多指、RTL、纵向、设置恢复和资源失败后的手势判定。
- `IosCurlBackgroundDeviceTest` 验证实际卷曲正面的主题取色和夜间背面。
- `ReaderGestureHandoffTest` 的通用探针覆盖平移、GoogleCurl 及 TXT/EPUB 页面交接，支持通过 `effect` 限定已有项目。
- `ReaderTurnPerformanceTest` 区分页面准备、效果启动、更新、绘制和窗口 FrameMetrics，不能用导航总时长替代帧率。

PixelCopy 会增加采样开销；观察到有限姿态无白闪不代表全部设备、刷新率或逐像素连续性已验收。API21 仍需实际设备验证。旧的原始观测保留在 `performance/` 中，仅对应各文件记录的历史版本，不表示当前效果清单或帧率。
