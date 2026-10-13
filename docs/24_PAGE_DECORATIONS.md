# 页眉页脚与卷页集成

当前卷页实现、空间映射、手势结算及接入方式见 [23：当前卷页版本](23_SHADER_PAGE_TURN_ROUTE.md)。本文件只说明保留的页面装饰接口；旧仿真研究记录已移除。

`ReaderPageFactories.withDecorations(header, footer)` 包装默认正文模板，以 `ReaderPageDecorationFactory` / `ReaderPageDecoration` 创建、绑定、解绑任意 View 或 ViewGroup。Java 可使用 `ReaderPageDecorationAdapter` 的默认生命周期实现。

- 每页创建独立、未挂载的 View，不能在多个页面之间共享同一个 View 实例。
- 槽的 visible/heightDp 由阅读样式决定；槽内子 View 的排版由宿主负责。
- null 保留默认槽。分钟刷新需要显式选择；解绑时释放宿主自己的资源。
- 卷页捕获整个页面，因此页眉、正文和页脚使用同一材料映射；快照预算由 Widget 管理。
- 翻页中的位图是视觉快照，不替代平整页面上的可点击 View。自定义页装饰不提交阅读位置，也不创建额外导航控制器。

其他宿主集成协议见 [接入指南](08_HOST_INTEGRATION_GUIDE.md) 和 [Widget API](04_WIDGET_API_DESIGN.md)。

