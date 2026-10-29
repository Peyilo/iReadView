# 内容与书源

## 来源、传输和呈现分离

所有书籍通过 `ReaderView.openBook(BookSource)` 打开。来源负责身份、目录、章节与资源，呈现后端负责布局和定位，Widget 负责导航、交互与状态提交。

```text
文件 / URI / asset / 宿主网络流
             ↓
BookSource（身份、revision、目录、章节）
             ↓
原生块呈现 / 文档呈现 / 宿主 BookPresentation
             ↓
同一 ReaderView、页面模板、选择 API 和翻页效果
```

`ReaderInput` 每次提供新的可读流，由取得流的一方关闭。授权、HTTP 客户端、认证、缓存和重试归宿主；来源可以被多个阅读器使用，`closeBook()` 不替宿主关闭来源。

## 内置选择

| 来源 | 内容及用途 |
| --- | --- |
| `MemoryBookSource` | 宿主提供的不可变目录和章节块 |
| `TextBookSource` | TXT 编码、标题规则、索引和按章读取 |
| `EpubTextBookSource` | EPUB 的纯文字提取兼容入口 |
| `EpubBookSource` | EPUB 转换为文字、独立图片块与正文链接 |
| `EpubReaderBookSource` | 可选 EPUB 模块；保留 HTML/CSS 图文，使用文档定位 |
| 自定义 `BookSource` | 网络分章、业务数据或新的块格式 |

EPUB 原书呈现使用 `EpubReaderBookSource`；简化块适配不解释完整 CSS、SVG 或固定版式。两种定位语法不可相互冒充，详见[正文层](reader-body.md)与[EPUB 引擎](epub.md)。

## 块、资源和链接

- `ChapterContent` 包含稳定 ID 的块。文字保持原文 String 与 UTF-16 范围；变更内容、结构或链接元数据时更新 revision。
- `LinkedTextBlock` 关联文字范围与链接；`ImageBlock` 仅保存来源内资源 ID、alt 与可选链接，不保存 View 或 Bitmap。
- `BookResourceSource` 按资源 ID、revision 和字节上限读取数据；View 不直接访问 ZIP 路径、文件或 HTTP。
- `BookLinkSource` 将书内链接解析为位置，无法定位时返回 null；外链交给 `ReaderLinkHandler` 和宿主业务。
- 自定义块由 `BlockLayoutProvider` 测量；原生图片通过 `ReaderImageLoader` 读取和解码，失败或超预算时保留占位。配置与所有权见[内容扩展](content-extensions.md)。

资源定位限制在出版物内，禁止路径越界。网络章节按需加载，不要求下载整本书。`reloadBook`、`invalidateChapter` 与 `retryChapter` 显式更新内容和缓存，旧代次不能覆盖新内容。

## 扩展格式

能转换为块的新格式实现 `BookSource` 与必要的资源/链接能力；需要 HTML、专属坐标或新的分页算法时实现 `BookPresentationFactory`，配套提供位置、选择和 `PageSurface`。Widget 不增加文件扩展名分支，格式接口不与网络传输耦合。
