# 独立 Maven 宿主

本工程只按 Maven 坐标消费 iReadView，不引用项目模块、includeBuild 或复制实现。它提供 XML/Kotlin 与代码/Java 两种 `ReaderView` 接入，演示模板、内容扩展、图片加载、LTR/RTL 和公开翻页工厂。

## 构建

准备 JDK 17 和 Android SDK，通过 `ANDROID_HOME` 或独立工程自己的 `local.properties` 配置 SDK。从仓库根目录执行，Windows 使用 `gradlew.bat`：

```sh
./gradlew :app:ireadview:bundleReleaseRepository
./gradlew -p examples/maven-host :app:assembleDebug :app:assembleRelease
```

默认仓库为 `../../app/ireadview/build/repository`，坐标为 `org.peyilo.ireadview:ireadview:0.1.0-SNAPSHOT`。独立复制工程后，可通过 `-PireadRepository=<repository-directory>` 和 `-PireadVersion=<version>` 指定仓库与版本。

第三方依赖由 `google()` / `mavenCentral()` 解析，包含由 iReadView 元数据传递的 PageCurl Core。更新同一 SNAPSHOT 后可用 `--refresh-dependencies` 刷新依赖。

Debug APK 输出到本示例的 `app/build/outputs/apk/debug/`。Release 开启 R8 和资源压缩，输出未签名 APK；分发前由宿主配置签名。库保持普通 AAR，最终压缩策略由应用决定。

## 生命周期与恢复

`SnapshotBundles` 是示例自己的 Bundle 编码：保存书籍身份、源位置、模式和效果，不保存正文、来源、View、认证或运行时工厂。重建时重新创建来源，再使用 `restoreSnapshot`。无效格式按没有快照处理，应用长期阅读历史由宿主另行存储。

停止时先撤销旧请求归属、取消请求、`closeBook`，再关闭不再共享的来源；最终销毁时 `release`。模板解绑时清理文字、监听器与宿主回调。

API 及支持范围见[宿主接入](../../docs/integration.md)与[兼容边界](../../docs/compatibility.md)。
