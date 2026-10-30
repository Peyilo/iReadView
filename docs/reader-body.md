# 正文层设计

所有格式由 `ReaderView.openBook(BookSource)` 进入；页面模板的 `bodyHost` 挂载对应的 `ReaderBodyView`。Demo 最低 API 21，默认 EPUB 是《The Art of ARM Assembly》，书籍来源见[资源说明](../app/src/main/assets/epubs/README.md)。

## 职责

- `ReaderBodyView` 是 Android 正文 View 抽象，定义正文绑定清理、选区/手柄几何和资源释放。生命周期在主线程，清理和释放必须幂等；卸载时取消回调。它不拥有书籍、页眉页脚、阅读进度或翻页效果。
- 正文绘制、选区绘制/交互通过 `ReaderPresentationComponents` 分别注入，完整后端通过 `setBookPresentationFactory` 注入；默认正文不直接创建固定选区实现。最终 `release` 由基类模板方法保证，后端资源放入 `onRelease`，见[组件扩展 API](extensions.md)。
- `TextReaderBodyView` 接收已经排版的块片段，以 Canvas 绘制。原有文本偏移、跨页选择和无障碍遍历留在该实现，不强迫 EPUB 伪造文本块。
- `EpubReaderBodyView` 自己管理系统 WebView、包内 HTML/CSS/图片/字体请求、DOM 分栏、定位和选区。原始 XHTML 不转换为纯文本；渲染引擎由项目维护。
- `BookPresentation` 协调正文实现和统一页面模板：准备隐藏候选页，完成排版后提供 PageSurface，iReadView 决定导航提交、取消和翻页效果。正文不得自行提交新的书籍位置。
- 分页文本与EPUB共同组合 `ReaderPageCache<T>` 和 `ReaderPagePreloader`，默认当前页加两个备用页；通过 `ReaderView.setPageCacheMode` 统一切换单页/三页。缓存所有权和空闲调度不放入ReaderBodyView，详见[分页缓存](page-cache.md)。
- 书源负责容器、元数据、spine、目录和资源字节。网络小说仍通过 BookSource 加载正文块；内容格式与本地/网络传输方式分开。

## 边界与定位

文本使用 ContentAnchor；EPUB 使用带内容版本的 documentLocator，记录资源、DOM 文本节点路径/偏移及分栏进度。分页数字只是当前排版的提示，不是跨样式稳定锚点。选区通过 ReaderSelection 进入统一 Widget API 与 SelectionToolbarFactory；活动选区由共享 ReaderSelectionPainter 绘制，文本与 EPUB 的分页手柄行为由共享 ReaderSelectionInteraction 控制，包内链接交给同一导航管道，外链交给 ReaderLinkHandler。

WebView 仅访问私有合成 HTTPS 来源的包内资源，不打开文件路径或远程网页；正文脚本不能获得 Android JS 对象。项目脚本由宿主 evaluateJavascript 调用。清理后旧回调必须失效。资源读取沿用归档大小、展开量、条目数和路径校验。

## 验证边界

文本与 EPUB 共用缓存策略、导航事务、选区和菜单入口，设备测试分别验证其正文绘制与页面交接。EPUB 的重排、固定版式、字体、目录、链接和媒体支持范围见 [EPUB 引擎](epub.md)与[兼容边界](compatibility.md)；编译通过或单本书显示成功不代表完整 EPUB 兼容性。
