# 选择、菜单与统一标注

## 长按与范围

默认按源段落选择完整文本，包括当前页不可见的续段。分页和滚动长按先用实际高亮路径命中ID，在宿主有效记录中找到高亮，验证书籍/章节/可选修订及完整range/quote后恢复整条选区；跨段跨页部分保留。未命中、记录失效或无法恢复时选择段落。重叠记录按逆绘制顺序命中最上层，不自动合并。

选区使用格式无关的 ReaderSelection：普通文本为 TextSelection（同章 TextRange），EPUB 为 DocumentTextSelection（同一 spine 文档的规范文本节点路径与 UTF-16 偏移）。屏幕矩形只是几何，不是选择范围；两种正文都不支持跨章节选区。换书、版本变化或选区失效后，旧动作不得生效。

EPUB 不使用浏览器的长按、DOM Selection、系统 ActionMode 或 CSS ::selection 来控制活动选区。触摸先经过 Widget 的长按时钟；正文只查询段落、字符命中与可见几何。TextReaderBodyView 和 EpubReaderBodyView 共用 ReaderSelectionPainter 的 Canvas 填色、竖条、圆点、命中范围和抓取偏移，分页共用 ReaderSelectionInteraction 的 touchSlop/550ms 页边停留策略。滚动共用 ReaderSelectionScrollPolicy 的350ms停留、28dp边缘带和逐帧420dp/s上限。

## 对外 API 与 Demo

以下 API 均由 ReaderView/BookReadView 提供，不需要宿主解析 EPUB DOM：

| 操作 | 统一接口 |
|---|---|
| 获取选区/文字 | `getSelection(): ReaderSelection?`、`getSelectedText(): String?` |
| 选区/手柄几何 | `getSelectionBounds()`、`getSelectionHandleBounds(start)`；返回 Widget 局部物理像素的副本 |
| 复制/清除 | `copySelectedText()`、`clearSelection()` |
| 新增高亮/划线 | `highlightSelection(styleId)`、`underlineSelection(style)`；也有指定 id 的重载 |
| 添加/取消 | `toggleSelectionHighlight()`、`toggleSelectionUnderline()` |
| 绑定当前选区的动作 | `getSelectionActions()`；失效后拒绝执行 |
| 标注与跳转 | `exportAnnotations/replaceAnnotations`、`add/update/removeAnnotation`、`getAnnotationPosition/goToAnnotation` |
| 统一分类 | `AnnotationSnapshot.allHighlights/allUnderlines` 与 `HighlightAnnotation/UnderlineAnnotation` |

`getTextSelection()`、原生 Highlight/Underline 和 snapshot.highlights/underlines 保留给显式需要文本锚点的调用者；格式无关调用使用上述统一接口。`SelectionActions.highlight/underline` 和 `onHighlightTapped` 现在返回/接收统一注解接口，这属于当前开发版 API 的签名调整。

Demo 的 ReaderSelectionMenus 分享、查看和字数动作调用 reader.getSelectedText；复制、高亮、划线使用库提供的代次绑定动作。AnnotationPanel 用统一分类和 goToAnnotation 展示、跳转、删除两种格式的注解。AnnotationCodecs 将文档注解放入原有版本2 items 记录中保存，不另建 EPUB 注解仓库。

## 绘制与拖动

- 逐行绘制选中背景，排除换行符、行距和不相关空白；不把整段外接矩形涂色。SelectionStyle公开fillColor、handleColor、handleRadiusDp；当前橙色默认填充#33F66B22、手柄#F66B22、半径4dp。
- 起点圆点在竖线上方，终点在下方。圆点独立保持在可见边界，竖线使用真实caret；行首竖线若完全落在View外，仅限制到View内一个线宽，不能按圆点半径把整条移入首字。
- 抓取偏移按真实caret计算。分页显示离页端点的代理手柄，位置取本页实际选中片段交集；显示/轻点代理不改变原范围，超过touchSlop才调整端点。
- 边缘拖动沿统一导航延迟翻页并继续源锚点扩选；滚动复用行不重复生成分页代理手柄。
- 软换行处平台横向命中可能停在末字前，TextBlockLayoutProvider普通/最近命中共用补充映射：末尾文字方向与段落方向一致时，以最后可命中caret与行边的中点映射到getLineVisibleEnd；RTL反向，混合方向相反尾run仍用平台映射。正文横坐标可到达右边界。

验证范围：同一设备用例同时覆盖文本和 EPUB 的整段长按、代理手柄、缩短范围、边缘翻页后继续扩选且固定端点不变、过期动作拒绝、高亮持久化/恢复/跳转/取消。同一用例还验证了波浪划线、复制清除、重新打开后的注解恢复，以及滚动模式的边缘自动扩选；最新结果见状态文档。该证据不是所有 Unicode、RTL、固定版式或无障碍输入组合的完整验收。

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

## EPUB滚动时活动选区同步

活动底色与手柄仍由原生Canvas绘制。连续滚动时，WebView的 `onScrollChanged` 根据实际像素位移同步平移已有选区路径、端点和可见边界，不等待JS重新查询或100 ms阅读位置上报。DOM几何返回时携带同一次查询的CSS滚动原点；原生收到后根据当前WebView滚动位置重定位，避免延迟返回的几何把底色拉回旧位置。

滚动模式保留完整选区行矩形及实际端点，在绘制时裁剪到正文视口，确保原先在屏幕外的选中文字滚入后仍有底色。分页仍使用可见行和代理手柄。此改动针对活动选区；持久划线等其他装饰不作为此同步机制范围。

`UnifiedSelectionIntegrationTest.epubSelectionTracksNativeScrollBeforeJavascriptReply` 长按后保持手指按下，在同一UI调用中连续正反向滚动30次，每次立即验证填色路径同步位移且高度不变，再注入旧滚动原点的选区快照检查不会回跳。检查期间JS回调无法穿插执行，因此不会把“等待异步最终正确”误判为同步跟随。

## 统一标注

ReaderAnnotation 包含 id/bookId；原生 RangedAnnotation 包含 range/quote；DocumentRangedAnnotation 包含实际文档选区。Bookmark、Highlight、Underline、DocumentHighlight、DocumentUnderline 都存入 AnnotationSnapshot.items。allHighlights/allUnderlines 是格式无关分类，原有文本分类和构造仍保留。文档范围由渲染层依据原始路径、UTF-16 偏移、内容版本与原始 DOM 文本核验；异步校验必须携带原注解对象，已更新/删除的记录拒收旧结果。

EPUB 持久高亮使用临时 span 包装原始文本切片；包装前保存规范地址，拆分的文本节点保留原始路径与偏移，因此新增/删除/重叠高亮不会把进度或选区锚定到临时 span。活动选区始终由 Canvas 绘制。文档划线通过同一个 ReaderUnderlinePainter/UnderlinePainter 绘制五种线型，坐标来自实际文字行，排除图片框和行距。

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
