# iReadView

可嵌入Android View Widget库，默认入口`org.peyilo.ireadview.widget.ReaderView`，支持XML或代码创建。项目以库和示例宿主分离，不提供内置书架、账号或业务存储。

## 开始阅读

- **换对话继续开发**：先读[交接说明](docs/03_IMPLEMENTATION_HANDOFF.md)和[架构](docs/02_ARCHITECTURE_AND_PROJECT_STRUCTURE.md)。
- **集成到App**：[宿主接入](docs/08_HOST_INTEGRATION_GUIDE.md)。
- **按任务找设计**：[文档索引](docs/README.md)。
- [Demo结构与流程](app/README.md)、[独立Maven宿主](examples/maven-host/README.md)、[第三方说明](THIRD_PARTY_NOTICES.md)。

## 模块与能力

`:app:ireadview`为库，`:app`为Demo。当前有分页/连续滚动、源锚点导航与恢复、TXT/轻量EPUB、显式全书页索引、选区/书签/高亮/划线、多种效果、任意View扩展与六位置默认页眉页脚。

Demo提供内置小说、本地TXT/EPUB和网络书源；主阅读页全屏、正文中部打开底部菜单，通过公开模板/样式API配置。网络来源属于Demo，库不请求网络或文件权限。

已有能力不等于验收通过：最近选择、模板和电量实现主要有构建证据；完整设备/无障碍/性能回归未完成。跨章选择、笔记/自定义标注类型、搜索、Compose、完整EPUB媒体等不作为当前已交付能力，见[状态](docs/07_REQUIREMENT_IMPLEMENTATION_AUDIT.md)。

## 构建

JDK17、Android SDK；本机路径及Git工作区状态见交接说明。

```powershell
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:ireadview:assembleRelease :app:ireadview:bundleReleaseRepository
```

APK：`app/build/outputs/apk/debug/app-debug.apk`；AAR：`app/ireadview/build/outputs/aar/ireadview-release.aar`。
本地Maven开发坐标：`org.peyilo.ireadview:ireadview:0.1.0-SNAPSHOT`；ZIP有依赖元数据，不嵌入第三方依赖。尚未配置正式在线发布。
