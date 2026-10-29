# 构建、验证与发布

## 环境

需要 JDK 17、Android SDK Platform 36、Build Tools 36.0.0，以及 `google()` / `mavenCentral()` 的网络访问。Gradle 8.13、AGP 8.11.0 和 Kotlin 2.0.21 由仓库固定；Java/Kotlin 字节码目标为 11。

设置 `JAVA_HOME` 和 `ANDROID_HOME` 指向自己的安装目录，或使用未跟踪的 `local.properties` 配置 `sdk.dir`。不提交 SDK 路径、签名密钥或机器生成的报告。

以下命令从仓库根目录运行；Windows 将 `./gradlew` 替换为 `./gradlew.bat`。文档检查及 EPUB 样例生成使用 Python 3.9+；JVM 签名导出脚本使用 PowerShell。

## 构建和检查

```sh
python tools/check-docs.py
./gradlew verifyArchitectureBoundaries :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:testDebugUnitTest :app:ireadview:testDebugUnitTest :app:ireadview-epub:testDebugUnitTest
./gradlew :app:ireadview:assembleDebugAndroidTest :app:ireadview:assembleRelease :app:ireadview-epub:assembleRelease
```

设备测试需要已连接的模拟器或设备，可运行对应模块的 `connectedDebugAndroidTest`。性能用例应选择一致的内容、视口与效果，不能将编译或导航总耗时当作动画帧率。

## Maven 交付

```sh
./gradlew :app:ireadview:bundleReleaseRepository :app:ireadview-epub:bundleReleaseRepository
./gradlew -p examples/maven-host :app:assembleDebug :app:assembleRelease
./gradlew -p examples/maven-epub-host :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease
```

| 产物 | 仓库相对路径 |
| --- | --- |
| Demo APK | `app/build/outputs/apk/debug/app-debug.apk` |
| 核心 AAR | `app/ireadview/build/outputs/aar/ireadview-release.aar` |
| EPUB AAR | `app/ireadview-epub/build/outputs/aar/ireadview-epub-release.aar` |
| 核心 Maven ZIP | `app/ireadview/build/distributions/` |
| EPUB Maven ZIP | `app/ireadview-epub/build/distributions/` |

默认开发坐标为 `org.peyilo.ireadview:ireadview:0.1.0-SNAPSHOT` 和 `org.peyilo.ireadview:ireadview-epub:0.1.0-SNAPSHOT`，可通过 `-PireadVersion` 设置版本。两个文件仓库分别在模块的 `build/repository/`，ZIP 只包含对应模块及元数据；宿主仍需解析 AndroidX、协程、Jsoup 和 PageCurl Core 等依赖。

独立消费示例见[文本宿主](../examples/maven-host/README.md)和[EPUB 宿主](../examples/maven-epub-host/README.md)。EPUB 宿主启用 core library desugaring，版本与仓库一致。Release 示例开启 R8/资源压缩，未配置发布签名。

## JVM 签名导出

先构建核心 Release AAR，再执行：

```powershell
./tools/Export-ReleaseApi.ps1 -GeneratedDate (Get-Date -Format yyyy-MM-dd)
```

脚本从 `JAVA_HOME` 获取 JDK，将签名及摘要写入 `app/ireadview/build/reports/api/`。JVM 可见成员包含编译器合成内容，不构成公共 API 白名单；以源码 KDoc 和[API 契约](api-contracts.md)判断支持范围。

## GitHub Actions

[Android CI](../.github/workflows/android-ci.yml)在每次 push、PR 和手动触发时运行，不过滤分支或文件路径。它安装所需 SDK，检查 Gradle Wrapper，执行文档校验、架构检查、Debug/Release 构建、JVM 测试及 AndroidTest 编译，并用本次生成的 Maven 产物构建两个独立宿主。

任务失败时查看 Actions 的失败步骤和 Gradle `--stacktrace` 输出。工作流始终上传 `build/reports` 与 `build/test-results`，用于定位测试和静态报告。CI 不执行设备测试，也不上传到 Maven Central。

GitHub 只能检查已推送的提交；尚未推送的本地提交需要手动执行相同命令。构建、单元测试、设备回归和性能数据属于不同验证层级。
