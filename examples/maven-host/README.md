# 独立 Maven 宿主

这是独立 Gradle 工程，只包含一个宿主应用，以 Maven 坐标消费 iReadView。没有 project 依赖、includeBuild、源码复制或直接添加 AAR，也没有手写库的运行依赖。

## 构建

从 iReadView 根目录运行，使用 JDK 17，并设置 Android SDK（例如 ANDROID_HOME）：

```powershell
.\gradlew.bat :app:ireadview:bundleReleaseRepository
.\gradlew.bat -p examples/maven-host :app:assembleDebug
```

宿主默认使用 `../../app/ireadview/build/repository` 和 `org.peyilo.ireadview:ireadview:0.1.0-SNAPSHOT`。工程可独立复制，用 `-PireadRepository=<解压的ireadview-maven目录>` 指定仓库；使用其他开发版本时传 `-PireadVersion=<版本>`。相同快照更新后可以给宿主构建加 `--refresh-dependencies`。

APK 输出为 `app/build/outputs/apk/debug/app-debug.apk`。第三方依赖从 google()/mavenCentral() 解析，需要对应网络访问或本机缓存；交付 ZIP 不包含第三方库。

## 示例内容

- MainActivity：从 XML 创建 ReaderView，使用 Kotlin 回调、前后页按钮和 ReaderListener。
- JavaReaderActivity：代码创建 ReaderView，使用 Java 回调和 ReaderListenerAdapter。
- Java 页面通过 ReaderPageDecorationAdapter 工厂创建 LinearLayout 页眉（ImageView + TextView）和页脚（TextView + Button），解绑时清理文字及监听器；页脚使用绑定上下文请求宿主设置动作。Apple/Google 使用同一组 Java CurlOptions 和控制点 SAM，支持标准/提前完成的 smoothstep 行程，状态栏固定两行。
- SampleBooks：不可变 MemoryBookSource，自定义 TextContentBlock 保留宿主来源标签并使用默认文本排版。
- 两个页面：停止时先取消旧请求再 closeBook，销毁时 release；用实例字段及 onSaveInstanceState 保存轻量快照，Activity 重建时读取系统提供的 Bundle 并重新创建来源。

## 2026-10-07 构建记录（历史）

2026-10-07：独立 Debug 构建通过，Kotlin/Java 编译执行、XML 资源编译及 APK 打包通过；运行依赖报告显示从 Maven 模块传递解析 AndroidX 与协程，Core KTX 经传递约束最终选择 1.13.0。这是此工程的构建结果，不是全部宿主版本的兼容承诺。

没有新增或运行测试，没有安装/启动应用。XML 实际膨胀、显示/生命周期/恢复、辅助服务、压缩后的运行行为和设备矩阵尚未验收。依赖解析记录与 APK 摘要分别见主工程 docs/generated/maven-host-runtime-dependencies.txt 与 maven-host-build-inventory.json。

## Release 压缩构建

应用release配置isMinifyEnabled/isShrinkResources与Android优化默认规则，采用[Android官方说明](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization)中适用于当前AGP 8.11的DSL。

```powershell
.\gradlew.bat -p examples/maven-host :app:assembleRelease
```

输出app/build/outputs/apk/release/app-release-unsigned.apk；这是未签名构建产物，不可直接作为安装/分发包。此工程没有签名凭据配置。库仍以未压缩Release AAR发布，最终宿主决定压缩规则。

2026-10-07：Release构建通过（26秒），R8与资源压缩实际执行。AAPT生成的规则保留XML ReaderView及Context/AttributeSet构造入口，R8映射仍保留该类名；APK的layout/activity_main映射至本次压缩后的res/v9.xml，该XML仍引用完整ReaderView名称。资源路径是产物细节，不能硬编码或假定下次构建相同。

未新增整库keep或忽略所有警告规则。构建产物记录见主工程docs/generated/maven-host-release-inventory.json和maven-host-release-inspection.txt。没有新增或运行测试、没有签名/安装/启动；此静态核对不证明压缩后实际XML膨胀、恢复、Java全部接口、服务加载或宿主自己的反射入口通过。

## 保存与 Activity 重建

SnapshotBundles是宿主格式，保存book/chapter/block/offset、内容revision、页/章序号提示、模式和内置效果。独立宿主两种语言页面都在onSaveInstanceState导出当前位置；关闭后使用已有快照兜底。onCreate读取系统Bundle，onStart重新创建来源并调用restoreSnapshot。宿主schema与库format各有版本检查，损坏/缺字段/无效数值记录诊断并按无快照打开；致命错误不被吞掉。

不保存正文/来源对象/View/字体/工厂/认证数据，格式属于示例而非库协议。系统提供旧Bundle时具备恢复代码路径，但尚未运行旋转/进程回收验收；普通全新启动没有此Bundle，本示例没有另外写磁盘阅读历史。库View的自身配置状态与宿主快照有不同职责，快照指定位置由restoreSnapshot处理。

2026-10-07：新增保存代码后Debug构建通过；Release首次因Windows临时dex文件占用失败，未改保留规则，重试R8与打包通过。generated构建摘要及Release静态检查已刷新，恢复/损坏格式/压缩后的运行行为仍缺验证。

## 当前发布包接入观察（2026-10-09）

设置会话 JAVA_HOME/ANDROID_HOME 后，独立 Debug 与 Release 构建通过（2分57秒），Java 编译、R8 和资源压缩实际执行。Gradle 解析的 Maven AAR SHA-256 与当前库 Release AAR 一致，见主工程 `docs/generated/maven-host-release-inventory.json`；旧日期段落记录的是当时的证据范围。

未签名 Release 保留原样；仅在忽略的 build 目录生成使用本机 Android 调试密钥签名的临时观察副本，未配置产品发布签名。该副本仅安装到独立 emulator-5556/API36：XML ReaderView 显示；Java 页眉/页脚显示；页脚设置回调后仍为第1页；下一页变第2页。没有新增整库 keep 规则。


## 两种自定义翻页的 Java 示例（2026-10-09）

JavaReaderActivity增加Apple/Google、LTR/RTL及前后页按钮，通过公开PageTurnEffects工厂及View.layoutDirection配置Widget。同一个不可变CurlOptions配置两种效果：半径0.10、阴影18dp/0.20、背面内容0.25、64×48网格；控制点SAM将sheetProgress映射为q=p²(3−2p)，返回x=w(1−2q)、原默认y。该曲线连续并保留两个端点；Google只使用x，Apple同时使用默认y与手势起点。

宿主支持RTL的Manifest配置与阅读器方向一起决定Android显示方向。页眉ImageView/TextView容器与页脚TextView/Button容器通过ReaderPageDecorationAdapter创建和解绑，页脚经ReaderActionHandler响应；翻页和输入仍由Widget负责。此例用于展示Maven消费与Java公开接口，不是库附带阅读Activity。





## 2026-10-10 当前包验证




