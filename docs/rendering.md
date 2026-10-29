# 翻页渲染与背景颜色

## 现行效果

仿真翻页只提供 `PageTurnEffects.iosCurl(IosCurlOptions)` 与 `PageTurnEffects.googleCurl(GoogleCurlOptions)`，稳定 ID 分别为 `ios_curl` 与 `google_curl`。另保留无动画、滑动、覆盖和视差滑动；连续滚动是独立呈现模式。

Demo 阅读设置的“仿真效果”和翻页示例只列出 **iOSCurl / GoogleCurl**。Java 示例使用相同公开工厂。XML 的枚举值保持现有编号：GoogleCurl 为5，iOSCurl 为6，中间空位不复用。

## 事务与资源

`PageTurnEffectManager` 管理视觉事务、唯一帧时钟、快照预算、松手后的完成/复位和终态清理；导航层拥有阅读位置提交。效果消费 `PageSurface`，不读取书籍或 EPUB DOM，也不能自行导航。

`PageTurnSnapshots` 在事务开始时捕获 current/target。上传 GPU 纹理后，iOSCurl 归还 CPU 快照，再借用输出位图。完成、取消、异常及 detach 释放 GPU 资源和位图租约。平移效果保持既有显示列表/快照策略。

## iOSCurl

算法使用 Maven Central 的 `org.peyilo.pagecurl:pagecurl-core:0.1.0`，手势、曲面、UV、原版阴影与缓动使用 Kotlin 执行。Android 的 `internal/effect/ioscurl` 仅负责输入转换、事务适配和 GLES2 绘制。没有 JNI、自编 native 库、WebView 或 WASM 算法运行依赖。详见 [Kotlin 实现](pagecurl-core.md)。

### 从正面页面确定背面纸张色

`IosCurlMaterial` 在每次翻页开始、快照租约归还之前确定背景颜色：

1. 按实际 curl layout 选择正面纸页的 surface 与快照，不能固定取 current 或 target；前后翻、装订边和 RTL 都会影响来源。
2. 复用 `PageBackTint.resolve`。优先读取 `PageBackgroundSurface` 提供的背景元数据；没有有效元数据时，在快照上取8×8样本，以 RGB 主色桶排除少量文字和装饰的影响。
3. 仅将解析出的 RGB 用于背面纸张材质，保留原版 alpha、背面内容采样和中性光照。正面纹理已经包含主题底色，不再叠加一次主题色；阴影也不作主题染色。
4. 一个翻页事务只解析一次，后续帧使用固定材质；下一次翻页重新读取主题。

单栏的正反面属于同一张纸。背面仍显示当前卷曲纸页的镜像内容，露出的下层页保持独立。夜间背景不能出现固定白色背板。

### 柔和阴影

Android 默认使用中性黑色、0.55 不透明度的投影，并将原渐隐网格的范围扩至1.35倍。只改变半影条带的延伸和投影颜色，不模糊页面文字，也不改变手势、纸面顶点或 UV。`IosCurlOptions.shadowSoftness`（1–3）和 `shadowOpacity`（0–1）可分别调整范围和强度。

白底、米色和夜间底色共用半透明黑色，按预乘 alpha 的 source-over 混合：接收页面的 RGB 随遮挡程度衰减，因此保留原底色的色相；纯黑底也不会被灰色阴影提亮。不按主题叠加一层固定灰色或有色遮罩。独立 Kotlin 核心默认仍保留原始阴影参数，以继续执行原版数值对照。

## GoogleCurl

`internal/effect/googlecurl` 包含 `GoogleCurlEffect` 和 `GoogleCurlGeometry`。它绘制前面行波与透视压缩，保持原有几何和边缘阴影，不生成反折背面。

`GoogleCurlOptions` 只包含实际生效的行波幅度、阴影宽度/强度、网格列/行数和控制点策略。`GoogleCurlControlPointPolicy` 可由 Kotlin 或 Java SAM 提供，控制点使用右侧为翻起边的标准坐标，x 从页宽连续移动到负页宽。

## 验证

- `CurlCatalogTest` 检查公开仿真工厂仅有两个，以及 Google 行波的首尾边界。
- `IosCurlBackgroundDeviceTest` 检查元数据/快照取色、夜间材质、各方向的真实来源及每事务一次取色。
- `IosCurlBackgroundDeviceTest` 的 GLES 像素检查覆盖白、米色、夜间及纯黑背景，确保阴影只变暗。
- `SnapshotIsolationDeviceTest` 检查平移效果使用预算内快照、真实合成器变透明后两页仍可见，以及隔离失败时归还部分租约并请求降级。
- `IosCurlDeviceTest` 检查 GLES2 单栏绘制与资源回收；`IosCurlDemoTest` 验证真实设置、手势和事务交接。
- `ReaderTurnPerformanceTest` 和 `ReaderGestureHandoffTest` 的通用进度探针测试平移和 GoogleCurl。iOSCurl 的原版输入/缓动使用其专门测试，不通过会隐藏可选能力的宿主效果包装器。

GLES 离屏回读仍可能造成设备相关开销。数值对照、构建成功和有限设备执行不代表所有 GPU 逐像素一致或稳定60fps；性能结论需要对应当前代码与设备。
