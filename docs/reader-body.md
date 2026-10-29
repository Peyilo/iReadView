# 正文层设计

当前目标：所有格式由 `ReaderView.openBook(BookSource)` 进入；页面模板的 `bodyHost` 挂载对应的 `ReaderBodyView`。Demo 最低 API 21，默认 EPUB 是用户提供的《The Art of ARM Assembly》。不依赖 Readium 运行时。

## 职责

- `ReaderBodyView` 是 Android 正文 View 抽象，定义正文绑定清理、选区/手柄几何和资源释放。生命周期在主线程，清理和释放必须幂等；卸载时取消回调。它不拥有书籍、页眉页脚、阅读进度或翻页效果。
- 正文绘制、选区绘制/交互通过 `ReaderPresentationComponents` 分别注入，完整后端通过 `setBookPresentationFactory` 注入；默认正文不直接创建固定选区实现。最终release由基类模板方法保证，后端资源放入onRelease，见[组件扩展API](extensions.md)。
- `TextReaderBodyView` 接收已经排版的块片段，以 Canvas 绘制。原有文本偏移、跨页选择和无障碍遍历留在该实现，不强迫 EPUB 伪造文本块。
- `EpubReaderBodyView` 自己管理系统 WebView、包内 HTML/CSS/图片/字体请求、DOM 分栏、定位和选区。原始 XHTML 不转换为纯文本；渲染引擎由项目维护。
- `BookPresentation` 协调正文实现和统一页面模板：准备隐藏候选页，完成排版后提供 PageSurface，iReadView 决定导航提交、取消和翻页效果。正文不得自行提交新的书籍位置。
- 分页文本与EPUB共同组合 `ReaderPageCache<T>` 和 `ReaderPagePreloader`，默认当前页加两个备用页；通过 `ReaderView.setPageCacheMode` 统一切换单页/三页。缓存所有权和空闲调度不放入ReaderBodyView，详见[分页缓存](page-cache.md)。
- 书源负责容器、元数据、spine、目录和资源字节。网络小说仍通过 BookSource 加载正文块；内容格式与本地/网络传输方式分开。

## 边界与定位

文本使用 ContentAnchor；EPUB 使用带内容版本的 documentLocator，记录资源、DOM 文本节点路径/偏移及分栏进度。分页数字只是当前排版的提示，不是跨样式稳定锚点。选区通过 ReaderSelection 进入统一 Widget API 与 SelectionToolbarFactory；活动选区由共享 ReaderSelectionPainter 绘制，文本与 EPUB 的分页手柄行为由共享 ReaderSelectionInteraction 控制，包内链接交给同一导航管道，外链交给 ReaderLinkHandler。

WebView 仅访问私有合成 HTTPS 来源的包内资源，不打开文件路径或远程网页；正文脚本不能获得 Android JS 对象。项目脚本由宿主 evaluateJavascript 调用。清理后旧回调必须失效。资源读取沿用归档大小、展开量、条目数和路径校验。

## 分阶段验收

1. 落实正文抽象，TXT 原有展示不变；自研 EPUB 通过相同模板显示 XHTML、CSS、图片和链接，删除 Readium 依赖与 Fragment 宿主约束。
2. 验证重排分页/滚动、目录 fragment、跨章节、字体与间距修改后位置恢复、长按自定义菜单以及候选页取消。
3. 扩展固定版式、RTL/竖排、嵌入字体混淆、复杂表格/数学/媒体等能力，建立 EPUB 2/3 样本矩阵。逐项记录证据，不能用编译通过或单本书显示成功宣称“完全兼容 EPUB”。DRM 不属于通用内容解密能力。

此文为本次实现的设计约束；具体已验证范围见 epub.md。
