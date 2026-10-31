# 翻页渲染与背景颜色

## 现行效果

仿真翻页只提供 `PageTurnEffects.iosCurl(IosCurlOptions)` 与 `PageTurnEffects.googleCurl(GoogleCurlOptions)`，稳定 ID 分别为 `ios_curl` 与 `google_curl`。另保留无动画、滑动、覆盖和视差滑动；连续滚动是独立呈现模式。

Demo 阅读设置的“仿真效果”和翻页示例只列出 **iOSCurl / GoogleCurl**。Java 示例使用相同公开工厂。XML 的枚举值保持现有编号：GoogleCurl 为5，iOSCurl 为6，中间空位不复用。

## 事务与资源

`PageTurnEffectManager` 管理视觉事务、唯一帧时钟、快照预算、松手后的完成/复位和终态清理；导航层拥有阅读位置提交。效果消费 `PageSurface`，不读取书籍或 EPUB DOM，也不能自行导航。

`PageTurnSnapshots` 在事务开始时捕获 current/target。支持独立读租约的宿主将两张图交给 iOSCurl 工作线程上传；事务可先关闭原租约，最后一个读者结束后才允许位图回池。硬件路径直接合成 GPU 图像，不借用逐帧输出位图；软件宿主保留离屏位图输出。完成、取消、异常及 detach 释放事务资源，工作线程异步清理自己的 EGL 资源。

GPU 工作线程保留两块纹理存储到 Surface/效果释放，避免每次翻页重复删除和分配。相同尺寸及 GL 格式使用 `texSubImage2D` 完整更新，不跳过任何页面的像素上传；尺寸或格式改变时重新定义存储。这个有界分配池不保留 CPU 位图租约，也不改变快照预算、分辨率和透明度。软件渲染器仍在事务结束释放纹理。

`RecordedPageSurface` 表示可独立于可见层级回放的命令，不保证它引用的 Bitmap 像素不变；软件 Picture 也可能保留可变图片引用。纹理缓存不能只比较录制对象或 View 的身份。需要固定像素的效果仍捕获事务独占的图像，GPU 像素回归覆盖同一表面对象在两轮之间更新图片的情况。

iOSCurl 可对两张完全不透明的页面使用硬件 Canvas 捕获，前提是卷曲正面提供有效背景元数据、内部预算允许且 API/GLES 能力可用。API 29 及以上支持实时 View 和 EPUB，并在合成 View 就绪后按实际预算预热捕获资源；旧系统仅对 `isRecorded = true` 的独立命令开放该路径。首次按需准备尚未就绪时使用位图，不改变分辨率阶梯，也不提前推进动画。路径选择依据表面能力，不依据文件格式。

`IosCurlCanvasCapture` 在主线程绘制页面。独立命令由生产线程提交，实时 View 在主线程完成提交，避免窗口遍历替换借用的子 RenderNode。EGL 线程将两个外部图像各复制到事务拥有的二维纹理，再立即归还生产缓冲区。动画只采样固定的目标纹理；每轮重新捕获，像素变化不依赖对象身份。关闭时先结束录制和生产端，再销毁消费端及 EGL。两张目标图使用现有预算和尺寸阶梯；生产缓冲及驱动分配另占 GPU 内存，位图预算不是进程内存上限。

`ViewPageTurnEffect` 可提供提前挂载的合成 View，用空闲时间准备 Surface 和着色器。`SnapshotPageTurnEffect.usesPageSnapshots` 是动态就绪状态：首帧尚未被合成器接收时保持正文，接收到本次事务图像后才暂停正文绘制；后端不接管时使用 alpha 隐藏。取消、Surface 丢失及配置变化沿统一事务边界恢复正文。

`ReadVisualHandoff.setSnapshotPlayback` 允许正文后端接管可逆的绘制抑制，避免透明视图仍注册 WebView 绘制回调。默认返回 false，使用 Widget 的 alpha 策略。Widget 在目标页交接前和取消/异常终态恢复绘制；后端不能借此改变位置或销毁正文。EPUB 在覆盖期间跳过正文子树，保留 View 和 DOM。停放页仅在 DOM 视觉栅栏等待期间参与窗口绘制，并通过透明离屏图层隔离像素；就绪后跳过绘制。空裁剪不能可靠隔离旧版 WebView 的绘制回调，曾导致备用页覆盖当前页。

EPUB 分页视口使用 WebView 硬件层缓存，减少父窗口重绘时重复调用 WebView 绘制器；内容失效仍更新图层，CSS 动画继续运行。API 29 及以上可缓存当前页与备用页，旧系统只缓存已提交的当前页，避免备用页软件录制与多个硬件层交错时出现过期画面。旧系统的备用页以及所有系统的连续滚动使用默认图层，WebView 仍通过 HWUI 绘制。缓存增加视口大小的 GPU 纹理开销，数量受单页/三页缓存策略约束。软件快照直接绘制 WebView，避开停放硬件层可能为空的框架绘图缓存；正常硬件绘制仍走框架路径。

取消时刷新当前 WebView 的硬件层，使保留候选后恢复的窗口不复用过期图层；刷新位于取消交接边界，不在每次截图后重复执行。软件窗口不额外触发该失效，避免形成重复绘制循环。候选取消的资源归还规则见[分页缓存](page-cache.md)。

### 旧版 EPUB 的稳定视口缓存

API 26–28 对稳定视口准备完整分辨率的 ARGB 位图，供软件快照及当前窗口复用，避免每次交接重新调用 Chromium 绘制。缓存仍由原来的单页/三页生命周期拥有；View、DOM、命中测试和无障碍树继续存在。带透明度、变换、裁剪、阴影、动画或独立图层的页面根节点使用框架绘制。

DOM、字体、滚动和交互变化通过文档专属的 WebMessagePort 通知宿主，重新绑定或关闭正文时关闭通道；原生页眉页脚及选区仍跟随 View 失效。Chromium 的纯 VSync 重绘通知不等同于内容变化。正在选择文字、含可见图片、SVG、控件或媒体的视口，以及含动画、过渡或图片背景规则的文档，不保留稳定视口缓存。真正失效时先撤下缓存，再回到实时绘制；录制期间再次失效的结果也会丢弃。

每本书的缓存拥有量上限为 32MiB，与效果的快照预算分开计算。预算不足或分配失败时使用原 Picture 路径。已交给效果的像素保持不可变，退出缓存只归还预算，不强制回收仍可能被读者引用的位图；因此该上限不是进程或驱动内存上限。API 21–25 和 API 29 及以上保留原录制路径，不安装这套内容变化通道。

## iOSCurl

算法使用 Maven Central 的 `org.peyilo.pagecurl:pagecurl-core:0.1.0`，手势、曲面、UV、原版阴影与缓动使用 Kotlin 执行。Android 的 `internal/effect/ioscurl` 仅负责输入转换、事务适配和 GLES 绘制。没有 JNI、自编 native 库、WebView 或 WASM 算法运行依赖。详见 [Kotlin 实现](pagecurl-core.md)。

### 硬件输出与线程边界

`IosCurlRendering` 通过 `IosCurlCompositor` 选择 TextureView、直接 Surface 或位图输出；两个 View 适配器分别管理消费端及首帧/终态交接；`IosCurlGpuWorker` 独占 EGL 上下文、纹理上传、网格和绘制。工作线程仅保留最新姿态，丢弃尚未绘制的旧输入，不维护第二套动画时钟。Widget 继续提供原版 begin/settle 时间。

硬件路径不执行整页 `glReadPixels`、CPU 像素复制或每帧 Bitmap 纹理重传。EGL 上下文保持在渲染线程，着色器与 VBO 跨事务复用。每帧的全部批次只上传一次顶点/索引流，保持绘制顺序和局部索引；顶点直接使用核心的打包布局；不支持 half 属性的 GLES2 驱动才展开颜色浮点值。GLES3 创建失败可使用 GLES2，最低 API 仍为 21。

`ViewPageTurnEffect.requiresCanvasRedraw = false` 表示合成器自行触发像素更新；Widget 继续调用 update 和推进时钟，但不重复提交无变化的窗口绘制。动态就绪状态、异常及终态仍可通过 `host.requestRedraw()` 请求窗口更新，避免 TextureView 与 Widget 双重重绘造成 swap 等待。

默认 `IosCurlOutput.Texture` 遵循普通 View 的裁剪、变换和兄弟视图叠放语义。Demo 的固定正文矩形选用 `IosCurlOutput.Surface`：API 29 及以上直接提交缓冲区，省去中间纹理合成；API 21–28 使用 TextureView。兼容选择集中在渲染适配器，避开 API 28 复测中出现的 SurfaceControl 位置更新崩溃，不改变完整网格或动画时长。直接模式要求真实 Activity 窗口和不透明页面，正文区域内不能叠放需要盖住 Surface 的兄弟 UI，也不能依赖 View 旋转/透明度动画；不满足这些布局条件的宿主应选 Texture。

直接 Surface 保持正文绘制，不将正文 alpha 清零；结束时保留最后一帧，等待恢复后的父窗口完成实际绘制和一像素 GPU 复制，再清空 Surface。该复制只用于交接 fence，不读回整页，也不逐帧执行；已进入复制阶段后忽略排队中的重复窗口回调。异步 epoch 保护取消、重入及 detach，防止上一事务的清屏擦掉下一事务。

`PageSurface.isOpaque` 默认为 false，仅在整个矩形每个像素都不透明时声明 true。截图按该合约标记 alpha；透明输入在直接模式中继续使用位图输出，像素与现有透明混合一致。

内置快照池仅在页面声明完全不透明时跳过复用位图的清零，随后完整覆盖所有像素。透明页面和公开 `PageTurnEffectHost.borrowBitmap` 仍返回已清零的位图，预算、租约及归还规则一致。

没有合成 View、Surface 未就绪或宿主不支持 `RetainableBitmapLease` 时，保留原离屏位图路径。该路径正确性相同，但仍承担读回成本。资源不足或运行时失败走现有统一降级，手势判定和阅读位置提交不受后端控制。

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
- `IosCurlGpuDeviceTest` 对比硬件合成与位图路径的像素，覆盖两个输出、透明/不透明输入、装订边、前后翻、轴向和 RTL；`capture=bitmap` 验证关闭捕获预算，`capture=live` 验证实时表面保留位图隔离，另检查捕获中取消、卸载与线程清理。`BitmapRetentionDeviceTest` 检查取消后异步读者结束前不回收位图。
- `IosCurlDeviceTest` 检查离屏单栏绘制与资源回收；`IosCurlDemoTest` 验证真实设置、手势和事务交接。
- `EpubSnapshotRenderingTest` 对插图、固定版式和 ARM EPUB 的多列页面逐像素比较软件快照，并比较真实窗口画面；另验证硬件层中的 CSS 动画继续更新。
- `ReaderTurnPerformanceTest` 和 `ReaderGestureHandoffTest` 的通用进度探针测试平移和 GoogleCurl。iOSCurl 的原版输入/缓动使用其专门测试，不通过会隐藏可选能力的宿主效果包装器。

硬件合成仍有设备相关的 GPU 和 BufferQueue 等待。有限设备执行不代表所有 GPU 稳定 60fps；测量方法和适用范围见[翻页性能](performance.md)。
