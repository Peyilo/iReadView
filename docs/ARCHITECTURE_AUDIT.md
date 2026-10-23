# 组件架构审计

目标是使主要职责能由宿主通过公开契约替换，避免来源、正文、选区和翻页相互识别具体格式，并提供常见业务所需API。此审计不把“所有私有类接口化”作为成功条件；导航提交、代次与资源所有权仍由核心维护。

## 当前证据

| 边界/需求 | 源码和消费证据 | 验证范围 |
|---|---|---|
| 来源独立，支持文本、网络分章与文档 | BookSource及可选资源/导航能力；Demo网络源，Memory/Txt/EPUB来源 | 章节仓库JVM、ReaderWidget与EPUB整章设备用例 |
| 可替换完整呈现/新格式 | BookPresentationFactory、PresentationBookSource；EPUB独立模块 | 扩展设备测试替换工厂，独立EPUB Maven宿主实际加载 |
| 默认排版可组合，图片IO可替换 | BlockLayoutProviders.text、ReaderImageLoader/Options | ContentStrategyDeviceTest；核心Maven宿主公开策略 |
| 正文、选区外观和交互独立 | ReaderPresentationComponents的独立工厂 | 扩展测试文本/EPUB分页及滚动四路径，实例创建/关闭计数 |
| 持久划线可单独替换 | ReaderUnderlineRendererFactory | 四路径真实绘制、独立生命周期测试 |
| 菜单与宿主业务分离 | SelectionToolbarFactory/SelectionActions、getSelectionPosition | 统一选区测试；Demo分享/书签/查看与原生定位捕获 |
| 页面结构不受默认模板限制 | ReaderPageFactory及任意View Decoration SPI | 独立Java宿主任意页眉页脚，核心宿主编译/有限实际显示 |
| 动画独立，不提交阅读位置 | PageTurnEffectFactory只消费PageSurface | 扩展测试独立Effect，手势/双页及白闪用例 |
| 可配置共享缓存和占位/加载策略 | PageCacheMode、LoadingOptions、PlaceholderFactory | 缓存设备/JVM与Widget错误状态路径；非每种自定义占位组合均设备验收 |
| 能力判断不依赖格式类型 | BookPresentationFeatures与ReaderContentCapabilities | 四路径、未声明第三方引擎、关闭/释放状态检查 |
| 业务存储由宿主拥有 | Snapshot/统一标注、选区原生位置 | 生命周期/选区设备与注解JVM；任意业务类型不放入sealed内置仓库 |
| 外部只依赖公开核心 | Maven核心宿主与EPUB宿主；源码无internal引用 | 两独立工程Debug/R8构建，有限设备消费与重建 |
| 单向模块依赖持续可检查 | verifyArchitectureBoundaries | 正向通过；注入core→EPUB及Demo→internal两项违规均拒绝，移除后再次通过 |

能力、操作准备状态和视觉性能分别验证，不将可替换SPI的存在等同于每个实现的行为已通过。此前数据与限制见[功能状态](07_REQUIREMENT_IMPLEMENTATION_AUDIT.md)、[扩展](EXTENSIONS.md)、[内容扩展](CONTENT_EXTENSIONS.md)与[宿主场景](HOST_API_SCENARIOS.md)。

## 自动依赖边界检查

```powershell
./gradlew.bat verifyArchitectureBoundaries
```

检查核心生产源码不得引用Demo或可选EPUB引擎，消费者生产源码不得引用核心internal。当前扫描139个核心与57个消费者源码文件（包含两个独立宿主）。这是源码包名检查，不是完整字节码/反射依赖分析；外部Maven构建及运行证据用于补充它。验证失败的临时探针已经删除，不能提交或编译进产品。

主工程各模块check任务同时依赖此检查，可纳入既有Gradle验证流程；独立示例是其他Gradle根工程，通过Maven构建验证消费边界。

## 已明确保留的边界

原始通用拖动识别、导航事务和内置缓存实现没有承诺全部可替换；输入已有开关、轴和自定义点击契约，长按/选区拖动可通过控制器替换。内置标注类型为sealed，笔记等业务使用宿主存储和统一选择/位置API。复杂EPUB、API21真机、完整无障碍与所有设备性能矩阵仍是原有产品验证边界，不因架构SPI和API36测试通过而宣称完成。

## 验收结论（2026-10-23）

架构整理、主要职责的公开替换入口、常用宿主API及独立消费链路已完成。审计逐项核对了上述源码契约、公开消费示例、资源所有权及对应结果。导航/缓存核心不变量保留集中所有权；业务层与可选EPUB模块不引用核心internal，核心不反向依赖消费者。

- 核心27项JVM：报告app/ireadview/build/test-results/testDebugUnitTest，失败/错误均0。
- 联合API36设备18项：app/build/architecture-audit-device-final.log及JUnit报告，18项全通过，设备用时145.867秒。覆盖Widget/双实例/取消与重建、四路径组件及能力/选区定位、图片运行时策略、组件异常清理、三页缓存、统一选区、ARM整章往返及手势/双页交接。
- 独立EPUB Maven宿主：本轮Debug/AndroidTest/R8 Release构建及1项设备测试通过（7.381秒），包含文本/EPUB同Widget切换、绘制SPI、导航与Activity重建恢复，日志architecture-epub-host.log。原生内容策略4项及核心Maven宿主的证据见内容扩展文档。
- 两库发布POM、EPUB renderer资产、minSdk21及无Readium依赖已检查；独立工程只声明EPUB坐标即可传递获取核心。发布日志architecture-epub-publication.log。
- 依赖边界检查及两项反向注入检查通过，最终日志architecture-boundaries-final.log。check任务图已确认依赖此检查（architecture-check-wiring.log）；dry-run只证明任务连接，没有算作完整lint/check运行通过。

最初联合设备轮次因双实例测试替换掉Demo根节点，触发宿主onStop崩溃而中断，不计通过。修正测试使宿主根节点保留，两个实例仍在挂载/测量前分别openBook，原独立性断言保留；随后完整18项通过。所有app/build日志为本机验证产物，不纳入Git。

此结论覆盖组件架构目标。已知产品边界仍按上一节保留；没有宣称完整EPUB标准、API21真机、全部辅助输入或所有硬件性能已经验收。
