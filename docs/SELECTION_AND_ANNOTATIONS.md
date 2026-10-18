# 选择、菜单与统一标注

更新：2026-10-18；精确签名见widget.BookReadView、api.model.Annotations、spi.selection与api.style.TextLineStyle/Styles。

## 长按与范围

默认按源段落选择完整文本，包括当前页不可见的续段。分页和滚动长按先用实际高亮路径命中ID，在宿主有效记录中找到高亮，验证书籍/章节/可选修订及完整range/quote后恢复整条选区；跨段跨页部分保留。未命中、记录失效或无法恢复时选择段落。重叠记录按逆绘制顺序命中最上层，不自动合并。

选区基于同章TextRange与源UTF-16字符边界，不是屏幕矩形；当前不支持跨章。换书、版本变化或选区失效后，旧动作不得生效。

## 绘制与拖动

- 逐行绘制选中背景，排除换行符、行距和不相关空白；不把整段外接矩形涂色。SelectionStyle公开fillColor、handleColor、handleRadiusDp；当前橙色默认填充#33F66B22、手柄#F66B22、半径4dp。
- 起点圆点在竖线上方，终点在下方。圆点独立保持在可见边界，竖线使用真实caret；行首竖线若完全落在View外，仅限制到View内一个线宽，不能按圆点半径把整条移入首字。
- 抓取偏移按真实caret计算。分页显示离页端点的代理手柄，位置取本页实际选中片段交集；显示/轻点代理不改变原范围，超过touchSlop才调整端点。
- 边缘拖动沿统一导航延迟翻页并继续源锚点扩选；滚动复用行不重复生成分页代理手柄。
- 软换行处平台横向命中可能停在末字前，TextBlockLayoutProvider普通/最近命中共用补充映射：末尾文字方向与段落方向一致时，以最后可命中caret与行边的中点映射到getLineVisibleEnd；RTL反向，混合方向相反尾run仍用平台映射。正文横坐标可到达右边界。

最近这些交互修补仅构建通过，尚未设备确认，不应当作所有Unicode/方向情况的数学证明。

## 紧凑图文菜单

SelectionToolbars.menu(items, options)提供默认浮动菜单；每个SelectionMenuItem有唯一id、文字、drawable图标、action，可选activeTitle/state。宽度受可见窗口和maxWidthDp限制，超量分组并显示前后箭头；当前组按实际item数重测宽度，不保留不存在的箭头槽。

```kotlin
val items = SelectionToolbars.defaultItems() + SelectionMenuItem(
    "lookup", "查询", R.drawable.my_lookup_icon,
    SelectionMenuAction { selection, _ -> lookup(selection.text) }
)
reader.setSelectionToolbarFactory(SelectionToolbars.menu(items,
    SelectionMenuOptions(maxWidthDp = 360f, itemWidthDp = 56f)))
```

工厂复制item列表，id必须唯一；空列表隐藏菜单。设置null恢复原工具栏路径；完全自定义View继续使用SelectionToolbarFactory并在unbind撤销监听。宿主提供分享/查询等业务，库不内置网络服务。

## 统一标注

ReaderAnnotation包含id/bookId；RangedAnnotation包含range/quote。当前类型为Bookmark、Highlight、Underline，统一存入AnnotationSnapshot.items，分类视图bookmarks/highlights/underlines及旧构造保留。

BookReadView提供addAnnotation/updateAnnotation/removeAnnotation、exportAnnotations/replaceAnnotations和类型便利方法；更新不得以同一id偷换类型。AnnotationStore统一处理书籍绑定、全书唯一ID、范围/quote/版本及状态。PendingValidation不等于有效，失效标注不应错误绘制。

ReaderAnnotation目前为sealed，不宣称第三方自定义类型已开放；未来笔记可复用统一生命周期，但当前没有实现。存储/同步由宿主负责。Demo以版本2统一items和类型编码器保存，保留既有标注数据格式迁移；此迁移与已移除的旧页眉页脚偏好兼容不是同一件事。

## 高亮与划线动作

- highlight()/underline()和highlightSelection/underlineSelection是显式新增。
- hasHighlight/hasUnderline与toggleHighlight/toggleUnderline处理添加/取消。当前选区被有效同类注解完整覆盖才视作已有；部分交集不算。取消删除原记录整条，不自动切出残余片段；两种类型互不删除。
- 菜单显示“高亮/取消高亮”“划线/取消划线”。文字底色属于高亮，线条属于Underline，不能混用命名。
- 菜单动作跟随选区代次；文本选择、辅助遍历、标注和持久位置共同使用未改写源文本。

## 线条样式

TextLinePattern包括STRAIGHT、WAVY、DASHED、DOTTED、DOUBLE。TextLineStyle可配置ARGB颜色、thicknessDp、waveAmplitudeDp/waveLengthDp、dashLengthDp/gapDp；TextLineStyles提供Java重载便利工厂。

默认颜色#F66B22、粗细1.5dp、波幅/波长1.5/8dp、虚线段/间隔6/3dp。建议粗细1～3dp、波幅1～3dp、波长6～12dp；数值有限、长度为正、振幅可为0。

```kotlin
val style = TextLineStyles.wavy(color = 0xFF2786C5.toInt(),
    thicknessDp = 2f, amplitudeDp = 1.5f, waveLengthDp = 8f)
reader.getSelectionActions()?.underline(style)
```

每条Underline保存完整样式。内置布局以underlineSegments返回逐行/双向离散段，由UnderlinePainter绘制并按片段裁剪，不连过行距或页边；自定义块默认空列表表示不支持，需提供同源几何。笔画粗细、振幅过大可能覆盖字形。

Demo只使用菜单切换及列表查看/跳转/删除；按用户要求不提供线型、颜色、粗细编辑UI，库API保留。
