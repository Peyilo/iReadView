# EPUB 的独立 Maven 宿主

本工程仅依赖`org.peyilo.ireadview:ireadview-epub:0.1.0-SNAPSHOT`，核心库由其Maven元数据传递引入。不使用project/includeBuild，不复制库实现，也不直接拼接AAR。两个来源按钮在同一个ReaderView切换MemoryBookSource和EpubReaderBookSource；正文绘制组件、导航和位置恢复继续使用核心公开API。

## 构建和运行

在主工程根目录使用JDK17与Android SDK。独立工程可通过ANDROID_HOME或自己的local.properties指定SDK。

```powershell
./gradlew.bat :app:ireadview:publishReleasePublicationToLocalRepository :app:ireadview-epub:bundleReleaseRepository
./gradlew.bat -p examples/maven-epub-host :app:assembleDebug :app:assembleRelease
./gradlew.bat -p examples/maven-epub-host :app:connectedDebugAndroidTest
```

仓库默认分别位于两个库的build/repository，可通过ireadRepository和ireadEpubRepository覆盖。离线交付的EPUB仓库ZIP只包含EPUB模块；需要同时提供核心模块仓库。EPUB AAR的POM及Gradle metadata保留core/jsoup/coroutines依赖，不把它们打进fat AAR。

宿主minSdk21，消费EPUB时启用coreLibraryDesugaring并配置与项目一致的desugar_jdk_libs_nio:2.1.5，见app/build.gradle.kts。没有Readium、FragmentActivity或AppCompat要求。Release开启R8/资源压缩且未配置签名，不能把Release构建成功当作Release设备运行通过。

## 所有权与示例范围

切来源、进入后台和销毁时取消宿主请求，先让ReaderView.closeBook释放呈现，再关闭宿主持有的EpubReaderBookSource；最终reader.release释放组件。页面位置使用ReaderSnapshot和源定位，Activity重建时重新创建来源，不保存WebView/页面对象。

demo.epub与主工程原创illustrated-flow.epub一致，来自tools/Generate-EpubSamples.py，不包含用户ARM书籍。该小样例用于证明打包和公开SPI消费，不代表所有EPUB格式兼容。

PublishedEngineIntegrationTest实际验证：发布的EPUB在单一Widget内加载、独立绘制SPI注入、切换到文本再切回EPUB、两种引擎下一页提交，以及Activity重建后章节与页位置恢复。

2026-10-23：两库本地发布和EPUB仓库ZIP生成成功，独立宿主Debug/AndroidTest/R8 Release构建成功，API36设备测试1项通过（7.381秒）。主工程日志app/build/architecture-epub-publication.log及architecture-epub-host.log。未执行API21真机或未签名Release的运行验收。
