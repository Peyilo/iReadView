# 段落选择与图文菜单

## 长按与跨页选择

分页、连续滚动模式长按文字时，以章节源内容定位段落，不只选屏幕上的可见文字。TXT/EPUB 的段落 TextBlock 整块选择；自定义 TextContentBlock 内含换行时，以 CR/LF 分隔段落。空段落不产生选区，偏移仍使用原 UTF-16 文本，不修改源内容或锚点。

段落跨页时范围包含后续页面的原文。原有手柄拖动、分页边缘停留自动翻页、滚动边缘移动路径保留；跨页限于同一章节，未新增跨章节选择。拖动时隐藏浮动菜单，松手后按最新选区和可见位置重新创建菜单。

## 公共 API

- `SelectionMenuItem(id, title, iconResource, action)`：唯一非空 id、文字、drawable 资源及回调，每项同时展示图标和文字。图标保留资源本身的颜色，不强制染色。
- `SelectionMenuAction.onClick(selection, actions)`：主线程回调，含完整选中文字及受控动作。菜单调用前检查 `actions.isValid()`；回调可主动 clearSelection，默认不强制关闭。
- `SelectionMenuOptions`：最大宽度默认360dp、每项目标宽度56dp，深灰背景/白色文字。建议最大宽度280..400dp，每项56..80dp。
- `BookReadView.setSelectionMenuItems(items, options)`：安装图文分页菜单；列表快照复制，重复 id 拒绝。空列表保留选区但不显示菜单。可通过 defaultItems 加上自己的 item 保留内置复制、划线。
- `SelectionToolbars.menu`：返回 SelectionToolbarFactory，可直接安装；原有完全自定义 View SPI 继续支持。setSelectionToolbarFactory(null) 现在恢复内置图文菜单。

```kotlin
reader.setSelectionMenuItems(
    SelectionToolbars.defaultItems() + SelectionMenuItem(
        id = "comment",
        title = "发段评",
        iconResource = R.drawable.ic_comment,
        action = SelectionMenuAction { selection, actions ->
            // 打开宿主评论界面，使用 selection.range 和 selection.text。
            actions.clearSelection()
        }
    ),
    SelectionMenuOptions(maxWidthDp = 360f, itemWidthDp = 64f)
)
```

菜单工厂及回调为运行期对象，不序列化；宿主在重建 View 后重新安装。内置复制和划线仍由已有 SelectionActions 处理；自定义操作由宿主实现。isValid 对选区版本、内容代次和释放状态检查，旧菜单不会对新书或新选区触发回调。自定义异步任务完成后也需再次检查 validity，不能把点击时的有效性当作长期保证。

## 行背景和端点样式（2026-10-14）

选中背景按每行实际选中文字的布局路径绘制，使用 baseline+ascent/descent 限制高度，不覆盖额外行距，也不把整个段落合成一块背景。保留 StaticLayout 双向文本路径处理；换行符本身不染满行尾空白。普通高亮同样使用行路径。选区背景绘制在文字下方，不对字色叠加染色。

起始端点为贯穿选中行文字高度的竖线，上方圆点；终止端点为竖线，下方圆点。默认手柄#F66B22、背景#33F66B22、圆点半径4dp。命中区域继续至少24dp半径，拖动仍以文字 caret 为目标，避免改造圆点位置后跳字。

公共 `SelectionStyle.fillColor/handleColor/handleRadiusDp` 保留；新增便利 API `ReaderStyles.withSelectionBackgroundColor(style, argb)` 与 `withSelectionHandleColor(style, argb)`，修改后仍通过 applyStyle 提交。XML 可使用 ireadSelectionColor、ireadSelectionHandleColor、ireadSelectionHandleRadius，状态及 Demo 偏好照常保存。

```kotlin
var next = ReaderStyles.withSelectionBackgroundColor(reader.style, 0x33F66B22)
next = ReaderStyles.withSelectionHandleColor(next, 0xFFF66B22.toInt())
reader.applyStyle(next) { completion -> /* 成功后保存宿主偏好 */ }
```

菜单采用固定项目宽度，常规行高52dp、垂直内边距各4dp、图文间距3dp、箭头槽32dp；不拉伸 item 填满整条。字体缩放增加高度，长标签单行省略但保留完整无障碍描述。Demo 偏好版本4迁移旧蓝色选区到橙色，保留已调正文排版；切换阅读主题不再覆盖自定义手柄色。

## 布局与生命周期

内置菜单使用深灰圆角 PopupWindow，优先位于选区上方，空间不足时位于下方，并限制在窗口可见范围内。实际宽度取配置与可见窗口宽度的较小值。操作过多时按容量分页，右箭头显示下一组，后续页左箭头返回上一组；第一页隐藏左箭头，末页隐藏右箭头。不通过缩小字号塞入全部 item。中文标签单行显示，过长省略，完整标签用于无障碍描述。字体缩放增加行高。

SelectionToolbarController 统一管理内置菜单和自定义工具栏的所有权、关闭及 unbind；菜单翻组只改变菜单页面，不改变阅读页码/选区。关闭、换书、拖动时清理监听和选区引用，保持 PopupWindow 非焦点模式以支持原正文手柄交互。

Demo 的 ReaderSelectionMenus 使用宿主 API 提供复制、划线、分享、查看、字数、取消六项，展示溢出翻组。阅读输入中的“自定义视图示例”保留原完整 View 工厂，作为另一种 SPI 用法。

## 兼容及验收范围

长按由词选择改为段落选择，默认工具栏从系统 ActionMode 改为图文 PopupWindow。SelectionActions 新增抽象 isValid，外部自行实现此接口时需补实现，旧二进制宿主需重新编译。旧完整工具栏工厂仍可接入。

本轮仅构建 Debug APK、Release AAR 及发布仓库包；未添加或运行测试。设备上的选区定位、跨页拖动及不同尺寸/字体缩放外观仍需验收。
## 高亮、划线菜单更新

原标为“划线”的背景操作已更名“高亮”；新增真实划线及线条设置入口。库默认 item 为复制、高亮、划线；Demo 额外接入线条设置、分享、查看、字数、取消。独立注解、样式 API 和 Demo 编辑界面见[划线设计](28_UNDERLINE_ANNOTATIONS.md)，前文六项菜单描述为初版记录。

## 当前组宽度（2026-10-14）

浮动菜单宽度按当前组实际 item 数量计算：项目数×项目宽度，加实际显示的箭头及左右内边距。第一页不保留左箭头槽，最后一页不保留右箭头槽；少项组不再沿用最大容量或首组宽度。切组后重测内容，通过 PopupWindow.update 同步调整窗口尺寸并围绕选区重新定位，继续受可见窗口边界限制。分页容量使用稳定的窗口宽度预算，避免窄末组反过来改变分组。此处为库内实现改动，公共配置 API 保留。

