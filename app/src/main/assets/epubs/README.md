# EPUB 示例

默认展示书籍为用户提供的《The Art of ARM Assembly》，原文件来自 `F:/User/Desktop/The Art of ARM Assembly.epub`，以 `the-art-of-arm-assembly.epub` 原样复制到此目录。该书的版权和授权信息以原书为准，不属于仓库原创示例。

- `the-art-of-arm-assembly.epub`：默认展示书籍，用户提供的原始 EPUB。

另两本书由本仓库 `tools/Generate-EpubSamples.py` 生成，正文、SVG 和 PNG 均为仓库内编写的示例内容，无远程下载资源。

- `illustrated-flow.epub`：两章流式 XHTML，作者 CSS、SVG/PNG、浮动图文、表格、ruby、粗斜体、目录、脚注和跨章片段链接。
- `fixed-layout.epub`：两页 600×800 固定版式，绝对定位图文，spine/manifest 与版式元数据。

首页点击“EPUB 原书阅读”默认打开《The Art of ARM Assembly》；阅读页“书籍”可重新选择该书、切换原创示例或选择本地 EPUB。已有活动页面重建时仍恢复当前书籍和位置。示例不是完整 EPUB 一致性验收套件。
