# PageCurl Core 接入

算法通过 Maven Central 的 `org.peyilo.pagecurl:pagecurl-core:0.1.0` 引入，源码和算法说明由独立 [PageCurl-Core 仓库](https://github.com/Peyilo/PageCurl-Core)维护。版本统一配置在 `gradle/libs.versions.toml`，iRead 不包含算法源码模块。

```text
Demo → PageTurnEffects.iosCurl(IosCurlOptions)
                 ↓
ireadview internal/effect/ioscurl
  IosCurlInput       Android 事件、坐标和时间转换
  IosCurlEffect      导航事务、快照、算法调用和缓动
  IosCurlMaterial    纸张背景与阴影材质
  IosCurlRenderer    GLES2 绘制及离屏输出
                 ↓
PageCurl Core：gesture / control / layout / mesh
```

## 使用

```kotlin
reader.setPageTurnEffect(PageTurnEffects.iosCurl())
```

宿主通过 `IosCurlOptions` 调整装订位置及阴影。`PageTurnOptions` 提供位图预算，`ReaderInputOptions` 提供轴向配置。Demo 与 EPUB 不直接使用算法类型；[架构检查](building.md)限制 PageCurl Core 仅出现在效果适配层。

## 边界

- 算法库接收逻辑点、材质和几何参数，返回有序绘制批次；不加载书籍、不访问 WebView、不提交阅读位置。
- 手势位置按 density 转换，时间来自同一单调时钟。Widget 驱动更新、松手缓动和终态；不另建导航或时钟。
- 适配器依据 `CurlLayout` 选择实际卷曲纸页，解析一次背景材质，映射 current/target 纹理与静态下层页。
- GLES2 解释算法顶点、UV、预乘 half4 与批次顺序。输入的材质及阴影配置不改变宿主内容。
- 单栏的背面使用同一张纸的镜像内容；算法的中缝/多面能力不等于 Widget 已完成双页阅读。
- 效果归还快照、输出位图与 GL 资源；准备失败走统一降级，不改变已识别手势的提交决定。

## 发布和验证

`ireadview` 的 POM/Gradle 元数据传递依赖 PageCurl Core AAR。仓库 ZIP 不再携带旧算法 JAR；独立宿主通过 `mavenCentral()` 解析依赖。

`IosCurlDeviceTest` 检查单栏装订、前后翻、轴向、RTL 和租约释放；`IosCurlBackgroundDeviceTest` 检查主题、阴影与真实纸面来源；`IosCurlDemoTest` 检查公开入口、手势、取消与降级。算法内部数值验证归独立仓库，性能测量与渲染边界见[翻页渲染](rendering.md)和[翻页性能](performance.md)。
