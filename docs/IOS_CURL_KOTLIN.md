# iOS curl 的 Kotlin 实现与接入

工作仓库是 `D:/Code/Agent/iRead`。算法来自此前离线还原并完成对照的 iOS12.5.7 / 16H81 C11 实现；Android 正式运行路径已经全部改为 Kotlin。没有 JNI、自编 native 库、固件、Unicorn、WebView 或 WASM 运行依赖。GLES2 仅调用 Android 系统图形接口。

## 模块与依赖方向

```text
Demo → ireadview 公共 API
            ├─ 原有导航事务、页面准备、提交/取消、位图预算
            └─ internal/effect/ioscurl
                 ├─ IosCurlInput：MotionEvent 与逻辑点转换
                 ├─ IosCurlEffect：视觉事务、快照与算法调用
                 ├─ IosCurlMaterial：从实际正面页面解析纸张背景色
                 └─ render/IosCurlRenderer：GLES2 资源和绘制
                          ↓
                    pagecurl-core
                 model / gesture / control / math
                 layout / transition / mesh
```

`app/pagecurl-core` 是独立 Kotlin/JVM 模块。生产源码没有 Android、Java、阅读库、EPUB 或 Demo 导入，可迁移到其他 Kotlin target。`verifyArchitectureBoundaries` 会检查这些约束。

Android 通用层只增加了两个可选内部能力：`EffectInputProvider` 提供输入会话，`PageTurnDynamics` 提供原版缓动和预热帧。它们不引用 iOS 算法类型。原有特效继续沿用原有输入和缓动。`PageTurnContext.vertical` 与 `PageTurnFrame.elapsedSeconds` 将方向和时间显式传入，避免算法自己读取平台时钟。

## 接入

```kotlin
reader.setPageTurnEffect(PageTurnEffects.iosCurl())
reader.setInputOptions(reader.inputOptions.copy(pagingAxis = PagingAxis.Horizontal))

// 从最大边装订；跟随 pagingAxis 选择右侧或底部。
reader.setPageTurnEffect(PageTurnEffects.iosCurl(IosCurlOptions(IosCurlBinding.Maximum)))

// 纵向翻页复用同一算法。
reader.setInputOptions(reader.inputOptions.copy(pagingAxis = PagingAxis.Vertical))
```

XML 支持 `app:ireadPageTurnEffect="ios_curl"`，稳定恢复 ID 为 `ios_curl`。默认配置可随阅读快照恢复；非默认配置仍由宿主保存并重新提供。

Demo 的“仿真效果”只提供 **iOSCurl / GoogleCurl**；仿真翻页示例 `CurlActivity` 默认使用 iOSCurl。GoogleCurl 的公开参数为独立 `GoogleCurlOptions`，只包含实际生效的行波、阴影、网格和控制点设置。

## 手势与动画

坐标和速度进入算法前从像素转换为逻辑点。Pan 使用原来的双轴 OR 10pt 滞回、逐轴扣除、两次采样的 25/75 速度权重、300pt/s 方向阈值及慢速释放的半页位置判断。Possible 阶段第二指使识别失败；已经开始后第二指不接管第一指。Tap 保留 45pt 移动半径、1.5s 截止时间和原版边缘区域。

手动翻页先执行 0.25s 的 begin 过渡，再跟踪手指；松手使用原版速度相关时长。自动翻页使用原版 0.4s 时长。time、radius、angle 使用各自的 CA cubic track，并在滤镜边界转换成 float32。常规 `PageTurnOptions.durationMs`、距离和甩动阈值不覆盖这一特效的原版算法；`reduceMotion` 与 `durationMs=0` 仍可关闭视觉动画。

内容选择、长按、链接、页眉控件等输入优先权继续由 Widget/内容适配器决定。候选页面的准备、阅读位置提交、忙碌状态和生命周期取消仍服从 iReadView 的串行事务契约。核心保留了原版多状态兼容与 FIFO 算法，但当前 Android 单栏适配器没有启用 Web 的多卷页并行叠加。

## 主题背景色

Android 的 `IosCurlMaterial` 在每次事务开始时复用 `PageBackTint`，从实际参与卷曲的正面纸页读取背景元数据或快照主色，并将 RGB 写入背面纸张贡献。装订边、RTL 及前后翻会改变纸页来源，不固定使用 current 或 target。一个事务只解析一次；正面纹理、原版中性明暗和背面内容映射不再重复染色。核心默认材质仍保留数值基准，主题适配由 Android 层提供材质输入。

## 单栏与双栏扩展

当前 Android 接入为单栏。前翻时纸页来自 current，背面是同一纸页的镜像内容；后翻时卷入的是前一张纸页。不会把下一页正面误当成当前单栏纸页的背面。

核心已保留六种装订位置，包括横向/纵向中缝。`CurlConfig.doubleSided`、`CurlLayout`、`CurlPagePass`、`CurlPageSource` 将正反面来源、页面框、内容镜像和静态页顺序分开表达。20 种合法布局已对照 C 版验证，其中包含中缝双栏。

阅读库公共 `PageTurnSpreadSurface` 预留组合页访问能力；Android 单栏特效当前不读取其多页信息。后续双栏应由排版/内容层提供 spread 的各页 surface，适配器映射为核心 source，再扩展合成器；不要在几何算法中加载书籍、查询相邻页或拼接 EPUB 内容。

## 渲染、资源与发布

GLES2 适配器执行原版模式 0/3/12，使用线性采样、边缘钳制和预乘 source-over 混合；半精度颜色只做一次预乘/量化。为了兼容 GLES2，顶点输入中的 half4 在上传前展开成 float4，不改变其数值。阴影包含在核心生成的网格和颜色中，Android 层没有再拟合一套阴影公式。

效果复用阅读库快照和位图租约。上传页纹理后归还 CPU 快照，再借用输出位图；完成、取消、异常与 detach 都清理 GL 资源和租约。当前通过 EGL 离屏绘制、readback 和 Canvas 接入，性能仍受设备 GPU/readback 影响；数值对照不代表所有设备都达到相同帧率。位图预算不足时沿用库的下采样策略，可按宿主需求增大 `bitmapBudgetBytes`。

发布的 `ireadview` POM 带 `pagecurl-core` 依赖。`publishReleasePublicationToLocalRepository` 和 `bundleReleaseRepository` 会同时收集该 JAR 与元数据；单独拷贝 AAR 的使用者需同时携带核心 JAR，优先使用 Maven 产物。

## 验证

`pagecurl-core:test` 包含 816 帧网格（位置、UV、half4、索引、模式和批次顺序）、1,440 组手势参数、10,000 组数学函数、2,160 次 Pan 事件、360 次 Tap 事件、864 组缓动、20 种布局和 960 组转场决策的 C 版对照，以及 FIFO 行为测试。正式测试不需要 C 或固件。

移植阶段在 API 28 和 API 36 模拟器验证过单栏装订方向/翻页方向/轴向/RTL；本次背景取色与清理回归在 API 28 重新覆盖16组绘制及资源回收，并加入主题取色设备测试。Demo 测试覆盖前后翻、反向取消、多指、RTL、纵向、设置恢复，以及资源不足回退后保持原手势判定。图形数值对照与设备执行均通过，但没有宣称 Android 与原始 iPhone GPU 的逐像素光栅结果相同。

构建与验证汇总见 [验证记录](generated/ios-curl-kotlin/validation.json)。背景取色与夜间背面的设备检查见 `IosCurlBackgroundDeviceTest`。

```powershell
./gradlew.bat :app:pagecurl-core:test :app:ireadview:testDebugUnitTest verifyArchitectureBoundaries
./gradlew.bat :app:ireadview:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.peyilo.ireadview.IosCurlBackgroundDeviceTest,org.peyilo.ireadview.IosCurlDeviceTest"
./gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.peyilo.iread.demo.IosCurlDemoTest"
./gradlew.bat :app:assembleDebug :app:ireadview:bundleReleaseRepository
```
