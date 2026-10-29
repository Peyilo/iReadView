# Demo

`:app` 是 iReadView 的示例宿主，最低支持 API 21。TXT、网络章节和 EPUB 共用 `ReaderView`；正文、模板、选择与翻页通过库的公开 API/SPI 配置。

## 入口

- 内置小说与文件导入：`ReaderActivity` 展示阅读、目录、位置恢复及样式设置。
- EPUB 原书阅读：默认使用随仓库提供的《The Art of ARM Assembly》，也可选择原创重排/固定版式样例或导入 EPUB。
- 网络来源：`NetworkActivity` 和数据来源示例展示按章请求、取消、重试与目录导航；协议及网站解析属于宿主代码。
- 定制示例：页面模板、正文块、工具栏、加载占位、Java 调用、通用容器和翻页效果。

## 结构

| 目录 | 内容 |
| --- | --- |
| `src/main/java/org/peyilo/iread/demo/ui` | Activity、阅读面板和示例界面 |
| `src/main/java/org/peyilo/iread/demo/data` | 来源、导入副本及业务存储 |
| `src/main/java/org/peyilo/iread/demo/reader` | 默认样式、模板配置与阅读辅助 |
| `src/main/java/org/peyilo/iread/demo/content` | 自定义内容及扩展示例 |
| `src/main/assets/epubs` | [EPUB 示例及版权说明](src/main/assets/epubs/README.md) |
| `src/androidTest` | 手势、正文、组件、缓存和翻页设备回归 |

## 阅读与配置

小说阅读页通过正文中部点击打开底部菜单，提供章节、目录、排版和设置。`ReaderDefaultStyle` 位于 `res/values/reader_defaults.xml`；页面装饰和六位置模板见[页面模板](../docs/page-chrome.md)，样式单位见[排版](../docs/typography.md)。

iOSCurl 通过 `PageTurnEffects.iosCurl()` 使用 PageCurl Core，GoogleCurl 通过独立工厂配置。`CurlActivity` 展示翻页；正文格式与效果无关。

文件读取授权、导入副本、最近书籍、阅读位置、标注与偏好保存均由 Demo 管理。来源切换或结束时先取消宿主请求、关闭阅读会话，再关闭不再共享的来源；永久销毁控件时调用 `release()`。

EPUB 原书模式依赖可选 `:app:ireadview-epub`，使用系统 WebView 与自研脚本。它挂载到普通文本所用的页面模板，不需要 Readium、独立正文 Activity 或导航 Fragment。支持范围见[EPUB 引擎](../docs/epub.md)。

## 构建

从仓库根目录运行 `./gradlew :app:assembleDebug`，Windows 使用 `gradlew.bat`。环境、测试和产物位置见[构建说明](../docs/building.md)。独立库消费方式见[文本宿主](../examples/maven-host/README.md)与[EPUB 宿主](../examples/maven-epub-host/README.md)。
