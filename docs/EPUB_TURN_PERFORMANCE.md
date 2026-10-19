# 文本与 EPUB 翻页性能对照

本文保留第一轮优化的数据与当时的实现/瓶颈。后续手势响应、三页预加载、硬件显示列表及GPU直接卷页的当前设计与测量见[手势响应及正文交接](GESTURE_AND_EPUB_HANDOFF.md)，不要将下文的历史路径当作当前架构。

2026-10-19，Android API36 模拟器，1080×2424。同一 ReaderView、默认 ReaderStyle、分页模式、240 ms 动画、16 MiB 位图预算。最低版本仍为 API21；本性能用例因使用 Window FrameMetrics 仅在 API24+ 运行。

## 测量方式

[设备用例](../app/src/androidTest/java/org/peyilo/iread/demo/ReaderTurnPerformanceTest.kt)比较三组内容：同一份300段原文的 MemoryBookSource 和 XHTML EPUB，以及默认 ARM EPUB 第一章的后半章。普通文本/EPUB 使用相同原文与样式参数，排版引擎不同，每页字数不要求完全相同。

每组运行全部7种效果；每种8次导航，4次前翻、4次后翻，丢弃首个样本，样本间保留150 ms阅读空闲时间。文本/普通EPUB从第40页附近开始，ARM从第80页附近开始。复测还在每组效果后发送一次真实拖动提交和一次反向拖回取消，并恢复到原页；共42次手势探测。

通过包装真实效果测量 prepare、effect.start（含快照）、update、draw 和 PageSurface.draw；Window FrameMetrics 收集动画窗口内的 TOTAL_DURATION，API31+另收集 GPU_DURATION。**帧P95包含动画启动的长帧，不是FPS，也不是仅测 draw() 的CPU耗时。**无动画模式没有动画帧样本。

原始数据：[优化前](performance/turn-before.json)、[优化后](performance/turn-after.json)。单次模拟器对比用于定位开销；不同GPU、温度、系统/WebView版本会改变结果，不能据此保证真机60fps。

## 同一原文对照

动画窗口帧耗时P95，单位ms：

| 效果 | 文本优化前 | 文本优化后 | EPUB优化前 | EPUB优化后 |
|---|---:|---:|---:|---:|
| slide | 18.8 | 18.7 | 55.9 | 19.6 |
| cover | 18.8 | 19.1 | 34.6 | 34.4 |
| ibook_slide | 18.7 | 18.4 | 34.6 | 19.3 |
| curl（iBook卷页） | 52.2 | 54.2 | 73.9 | 67.2 |
| google_curl | 18.7 | 18.9 | 33.9 | 19.4 |
| page_curl | 55.9 | 29.3 | 103.6 | 67.6 |

none模式导航耗时中位数：文本47.0→47.2 ms，EPUB34.7→30.6 ms。它不代表有动画情况下的帧性能。

## ARM EPUB 后半章

| 效果 | 准备中位数前→后(ms) | 帧P95前→后(ms) |
|---|---:|---:|
| none（无动画） | 65.4→31.9（完整导航） | — |
| slide | 48.1→14.6 | 48.0→19.4 |
| cover | 46.7→13.4 | 34.3→34.0 |
| ibook_slide | 49.3→14.4 | 50.8→33.8 |
| curl | 45.7→15.4 | 95.2→67.3 |
| google_curl | 45.0→16.2 | 23.3→19.2 |
| page_curl | 48.7→17.7 | 118.0→67.6 |

page_curl的7个保留样本中，窗口动画帧样本数从40增至65；帧P95仍明显偏高，不能只用导航耗时中位数宣称不卡顿。

## 差异位置与修改

1. `EpubReaderBodyView.goToPage/refreshGeometry`：原先有两次异步JS查询；分页触发的 onScrollChanged 还会在100 ms后再次查询几何，可能落在动画中。现合并定位与几何读取，分页不再运行滚动模式的延迟上报；无选区时避免重复JS清除。
2. `epub-body.js:firstText/state`：原先每次定位从章首遍历文本及行矩形，后半章更慢。现建立分栏文本矩形索引；批注重建DOM时使索引失效，仍返回规范文本节点/偏移地址。
3. `SlidingEffect.start/draw`：API29+原先逐帧调用两页的 live PageSurface.draw。原生View的display list成本较低，WebView还涉及独立合成。新增可选 `SnapshotPreferredSurface`，EPUB请求事务快照；使用现有位图预算/缩放档位，资源不足仍回退live绘制。16 MiB在此分辨率通常装不下两份全尺寸页面，因此必须允许预算内缩放，否则快照优化会被悄悄跳过。动画过程中可能使用较低分辨率纹理，提交后显示原始正文。
4. `BaseReadView` / `PageTurnEffectManager`：效果通过可选 `SnapshotPageTurnEffect.usesPageSnapshots` 明确告知已完成快照，此时暂时隐藏正文容器，避免下层WebView继续合成。提交、取消、异常均在finally恢复可见性；live或资源不足回退不能设置该标记。第三方效果及包装器若使用此能力，应如实传递该状态。
5. `BoundPageCurlEffect/Renderer/Shader`：原先把预算缩放后的纹理再渲染到更大的输出，并在UI线程逐帧glReadPixels。现输出与捕获纹理尺寸一致，避免无信息的放大；通过遮挡物最高点和光源方向范围做保守裁剪，跳过不可能有阴影的接收点。仍保留原48次采样、阴影参数和外观，不用降低采样数换速度。

## 验证与剩余问题

全部21组效果/内容组合的动画导航及42次真实手势提交/取消通过。另运行[分页回归](../app/src/androidTest/java/org/peyilo/iread/demo/EpubPaginationIntegrationTest.kt)、[统一选区回归](../app/src/androidTest/java/org/peyilo/iread/demo/UnifiedSelectionIntegrationTest.kt)，覆盖ARM整章102页往返、跨章、取消后续翻页、文本/EPUB选区及标注。核心24项JVM测试、Debug/AndroidTest APK及两库Release AAR构建通过。

[阴影GPU像素对照](../app/src/androidTest/java/org/peyilo/iread/demo/PageCurlShadowParityTest.kt)直接渲染裁剪前后两个shader，检查45组姿态/半径/光源大小组合（含正负倾斜、RTL及前后翻），每通道允许不超过2级量化差。它不是所有GPU/尺寸的完整图像矩阵。

**尚未完全追平文本模式。**cover仍有约34 ms的启动长帧；iBook卷页两种正文都有持续长帧，EPUB仍更高。page_curl虽减少主线程更新及GPU回读工作，仍保留同步回读路径、截图/上传启动成本，不能保证每帧16.7 ms。后续需继续分解RenderThread、驱动等待和快照启动开销；API21真实设备性能尚未验收。
