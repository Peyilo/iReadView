# 独立划线注解

## 命名与数据模型

原选区菜单的“划线”实际上调用 highlight() 填充背景，现改名“高亮”，更换荧光笔图标。新“划线”调用独立的 underline()，不会建立高亮背景。

`Underline(id, range, quote, style)` 是独立注解，`AnnotationSnapshot.underlines` 与 bookmarks/highlights 分开保存。它与高亮共享源范围、UTF-16/字符边界、摘录及内容版本校验算法，但不混入 highlights 集合。不同注解类型的 id 仍要求全书唯一。选择动作生成 id 沿用宿主 AnnotationIdFactory.newHighlightId，调用方可通过 underlineSelection(id, style) 指定 id。

## 线条样式 API

| 参数 | 含义 / 推荐值 |
|---|---|
| TextLinePattern | STRAIGHT、WAVY、DASHED、DOTTED、DOUBLE |
| color | ARGB，默认#F66B22 |
| thicknessDp | 粗细，默认1.5dp；建议1..3dp |
| waveAmplitudeDp / waveLengthDp | 波浪振幅/波长，默认1.5/8dp；建议1..3/6..12dp |
| dashLengthDp / gapDp | 虚线段长与间隙；gap 也用于点线及双线间隔，默认6/3dp |

`TextLineStyle` 为不可变模型，可通过 copy 调整颜色和粗细。`TextLineStyles.straight/wavy/dashed/dotted/doubleLine` 提供带 Java 重载的便利工厂。长度需为有限正值，振幅允许0。

```kotlin
val style = TextLineStyles.wavy(
    color = 0xFF2786C5.toInt(), thicknessDp = 2f,
    amplitudeDp = 1.5f, waveLengthDp = 8f
)
reader.getSelectionActions()?.underline(style)
// 也可显式指定注解 id：reader.underlineSelection("underline-123", style)
```

## 增删改及持久化

- SelectionActions.underline(style)：校验当前选区代次，成功后清除选区。
- BookReadView.underlineSelection(id, style)：当前选区转换为划线。
- addUnderline(Underline)、updateUnderline(Underline)、removeUnderline(id)：同现有注解事务发布回调和状态。
- exportAnnotations()/replaceAnnotations()：导出或恢复独立 underlines 集合，包括每条完整样式。
- getAnnotationStatus(id)：复用有效、待校验和内容变化等状态。

库不规定磁盘格式；Demo AnnotationStorage 使用版本2统一 items 记录及类型编码器，版本1 underlines 数组仍能迁移。线条样式直接在每条注解中保存，不依赖运行期样式 id 或主题，也不因调节下一条划线默认值而改变旧注解。

## 几何与实现

分页和滚动均将已校验的划线源范围映射到各自可见片段。内置文字布局通过 underlineSegments 提供独立的逐行/双向文字片段，ReaderBodyView 缓存线段，UnderlinePainter 在每行文字框底部绘制笔画；不是填充背景。双向文本的离散选中片段独立绘制，跨页只显示本页交集，不连过行距和页边。

直线、虚线及点线采用 Canvas 笔画/DashPathEffect，波浪采用有采样上限的正弦路径，双线绘制两条笔画。相位按每个可见行段开始；不是整章连续波相位。笔画位于行字框底部并受正文片段裁剪，异常大的粗细/振幅可能覆盖字形，推荐值见表。自定义内容需实现 BlockLayoutFragment.underlineSegments 并返回 TextLineSegment，默认空列表表示不支持此绘制；非文字块不自动生成线条。清理正文绑定时释放划线缓存及范围引用。

## Demo 操作

当前 Demo 已移除线条设置面板、默认样式偏好和单条样式编辑 UI，保留库的线型/颜色/粗细 API。菜单的高亮和划线改用添加/取消切换，注解列表可查看、跳转和删除。统一保存及旧格式迁移见[统一注解管理](29_UNIFIED_ANNOTATIONS_AND_TOGGLES.md)。

## 兼容与验收

AnnotationSnapshot 构造新增 underlines，保留 Java 三参数重载。SelectionActions 新增 underline；Java 自行实现 SelectionActions/BookPresentationHost 时需补齐 JVM 抽象方法，BlockLayoutFragment 新增 underlineSegments 自定义几何入口；Java 自行实现时需补齐。宿主应重新编译。旧完整工具栏 SPI 保留。

只进行了 Debug APK、Release AAR 和 Maven 包构建，未新增或运行测试。设备上的各线型、跨页/滚动、字体缩放和恢复效果尚待验收。
