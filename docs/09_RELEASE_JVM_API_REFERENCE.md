# 发布产物与JVM接口清单

这些文件是按需核对材料，不是新对话必读正文，也不是正式支持API白名单。

- [AAR/类摘要](generated/release-inventory.json)：具体AAR/classes.jar哈希与数量。
- [原始JVM签名](generated/release-jvm-api.txt)：JDK17 javap导出；含编译器桥接，不应整体加载作上下文。
- [仓库ZIP选定AAR摘要](generated/current-release-bundle-inventory.json)：只代表被选条目，不代表ZIP内全部历史快照。
- [其余证据说明](generated/README.md)：历史报告不可冒充当前验证。

## 生成

```powershell
$env:JAVA_HOME='D:/DevKits/JDK/jdk-17.0.10'
./gradlew.bat :app:ireadview:assembleRelease :app:ireadview:bundleReleaseRepository
./tools/Export-ReleaseApi.ps1 -JdkHome D:/DevKits/JDK/jdk-17.0.10 -GeneratedDate YYYY-MM-DD
```

重新构建会改变哈希/签名，导出工具不证明运行验收。当前清单保留2026-10-15记录：569个class条目、241个所选公开包类型声明，Demo/测试类匹配0；不是241个正式支持接口。库Manifest只有库包/minSdk21，无Demo权限或Activity。

Kotlin internal不是Java字节码强隔离；模块后缀、access$、copy$default、DefaultConstructorMarker、Continuation等不应成为宿主扩展入口。以API/SPI的源码契约判断支持范围，Java优先用callback/request和提供的Adapter/重载。

发布坐标、传递依赖、SNAPSHOT更新及独立宿主见[接入指南](08_HOST_INTEGRATION_GUIDE.md)和[独立工程](../examples/maven-host/README.md)。尚无正式在线发布/完整兼容性验收承诺。
