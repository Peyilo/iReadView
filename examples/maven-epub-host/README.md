# EPUB 独立 Maven 宿主

本工程只声明 `org.peyilo.ireadview:ireadview-epub:0.1.0-SNAPSHOT`，通过元数据引入核心和 PageCurl Core。没有 project/includeBuild 依赖，也不复制库源码。两个来源按钮在同一个 `ReaderView` 切换 `MemoryBookSource` 和 `EpubReaderBookSource`。

## 构建

需要 JDK 17 和 Android SDK，使用 `ANDROID_HOME` 或此工程的 `local.properties` 配置 SDK。从仓库根目录执行，Windows 使用 `gradlew.bat`：

```sh
./gradlew :app:ireadview:bundleReleaseRepository :app:ireadview-epub:bundleReleaseRepository
./gradlew -p examples/maven-epub-host :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease
```

两个仓库默认分别位于库模块的 `build/repository`，可通过 `-PireadRepository`、`-PireadEpubRepository` 和 `-PireadVersion` 覆盖。EPUB 仓库 ZIP 只包含 EPUB 模块；独立交付时同时提供核心仓库，并保留 `google()` / `mavenCentral()`。

宿主最低 API 21，启用 core library desugaring，使用与主工程一致的 `desugar_jdk_libs_nio`。不要求 Readium、FragmentActivity 或 AppCompat。Release 开启 R8/资源压缩，未配置发布签名。

## 内容与验证

`demo.epub` 是 `tools/Generate-EpubSamples.py` 生成的原创重排样例，不包含主 Demo 的 ARM 书籍。示例检查单 Widget 加载、正文绘制 SPI、来源切换、下一页提交和 `ReaderSnapshot` 恢复。

`PublishedEngineIntegrationTest` 位于此工程的 `src/androidTest`。连接设备后可从仓库根目录执行：

```sh
./gradlew -p examples/maven-epub-host :app:connectedDebugAndroidTest
```

切换来源或停止时取消请求，先 `closeBook()` 释放呈现，再关闭宿主持有的来源；最终调用 `release()`。重建时重新创建来源，不能保存 WebView 或页面对象。支持范围见[EPUB 引擎](../../docs/epub.md)与[兼容边界](../../docs/compatibility.md)。
