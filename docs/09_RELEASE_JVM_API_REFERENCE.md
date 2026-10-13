# Release JVM 接口清单说明

本清单从当前 Release AAR 的 classes.jar 读取，以编译产物核对设计和 Java 可见签名。它是一次产物快照，不是支持 API 白名单，也不是兼容性或运行验收报告。

## 1. 产物与清单

- [JVM 类型、public/protected 方法与描述符](generated/release-jvm-api.txt)
- [产物摘要、数量与 Manifest](generated/release-inventory.json)
- [当前宿主接入契约](08_HOST_INTEGRATION_GUIDE.md)
- [实际职责归属](05_WIDGET_IMPLEMENTATION_PLAN.md#2-当前职责归属)

2026-10-13 清单对应的 AAR 的 classes.jar 含527个class条目，所选公开包下输出218个Java可见类型声明；这不是218个支持接口。Demo包与测试类名匹配数为0，库Manifest只有org.peyilo.ireadview包/minSdk21，没有权限或Activity。SHA-256绑定具体AAR和classes.jar，源码/构建变化后应重新生成。

## 2. 当前包结构

| 包 | 当前类型/职责 |
|---|---|
| widget | BaseReadView、BookReadView、ReaderView，Android View入口 |
| api.model | 目录、正文、位置、模式、标注/快照及页索引 |
| api.source | BookSource、ReaderInput、TXT身份/规范化契约 |
| api.state | 状态、结果、操作选项和请求句柄 |
| api.style | 样式、字体与背景 |
| api.event | 监听器/适配器、动作处理 |
| api.input | ReaderInputOptions和触摸轴 |
| api.effect | PageTurnEffect、PageSurface、效果上下文/预算及PageTurnEffects工厂 |
| spi.presentation | 通用/书籍呈现、Host、ChapterLayout和导航输入 |
| spi.page | 页面/占位创建与绑定 |
| spi.selection | 选区工具栏与动作绑定 |
| spi.content | 块布局/片段/原文几何 |
| source | MemoryBookSource、TextBookSource、EpubTextBookSource及具体来源配置/诊断 |

当前没有spi.effect或顶层effects生产包，不移动已有代码来匹配早期拟议目录。

## 3. JVM 与源码契约的区分

清单使用JDK17的javap -protected -s，按api/spi/widget/source包前缀选取class，省略文件函数容器Kt和匿名/lambda类；输出所选Java可见public类型的public/protected成员。包含嵌套类型和Companion，也可能包含Kotlin源码internal类型。

getReaderScope$ireadview_release等模块后缀、access$方法、DefaultConstructorMarker、copy$default及默认参数桥接是编译器入口，不属于支持宿主调用的契约。BaseReadView的内部SavedState嵌套类型亦不能仅因字节码public当作扩展接口。internal不是JVM强隔离，不能自动把清单作为Java自动补全或API兼容基线。

suspend入口在Java视角包含Continuation；宿主Java通常使用ReaderCallback/ReaderRequest。Kotlin可空性、默认参数、线程/生命周期、资源归属和回调规则不由这些JVM描述符完整表达，仍按源码KDoc与08接入指南使用。

## 4. 本次新增接口的编译证据

TextBookOptions的getReuseDiskIndex/getPersistentIndexCacheBytes以及TextContentIdProvider的getPersistentIndexKey出现在当前产物，支持相应编译签名已生成的结论。Java直接实现该策略接口须实现JVM视角的抽象成员，包括新增getter；默认null键表示不复用自定义策略。Java也可继承TextContentIdProviderAdapter只覆盖需要的成员。当前Java示例实际构造TXT磁盘复用配置与匿名策略适配器，编译通过；这不证明全部策略或运行行为通过。

清单还可查ReaderInput静态工厂、IOException声明、来源默认构造、监听适配器与Widget回调方法；不存在的目标签名应先核对当前源码和04后续调整，不能仅引用早期设计片段。

## 5. 证据范围与更新

清单已按iBook几何迁入后的Release AAR刷新，后续构建仍不会自动更新清单。Debug/Java构建与本地Release Maven包生成通过，未新增或运行仓库自动化测试，清单中附有对应产物摘要。此产物核对没有证明独立AAR消费、压缩/XML恢复、全部Java/Kotlin接入、二进制兼容或依赖安全；本轮Java运行观察单独记录，不能扩展成整个发布包的运行验收。

更新时先生成Release AAR，再从该AAR提取classes.jar、枚举所选类并输出javap public/protected签名；同时刷新AAR/classes.jar摘要、条目数和Manifest。内部类和编译器桥接仍需按源码可见性人工区分。历史清单不能作为更新后产物的证据。

TextContentBlock在本清单中声明getText，TextBlock/HeadingBlock实现该接口；构造参数保持。DefaultImpls包含Kotlin桥接，不能当作宿主入口。Java自定义块仍须实现ContentBlock的抽象getter，包括未知时可返回null的持有大小；本轮只证明产物签名与Demo编译，未证明旧二进制宿主兼容或运行行为。

## 6. 本轮翻页与页装饰的 Java 入口

当前产物包含CurlOptions的完整参数构造及默认参数重载、CurlControlPointPolicy的resolve SAM、PageTurnEffects.iBookCurl/googleCurl静态工厂及ReaderPageFactories.withDecorations零/一/二工厂参数重载。ReaderPageDecoration在JVM中有五个抽象方法；Kotlin默认实现不等于Java可省略这些方法。ReaderPageDecorationAdapter提供unbind、getNeedsTimeUpdates、onTimeChanged默认方法，Java子类只需实现getView与bind；安装监听器或任务时仍应重写解绑清理。





















2026-10-10锥面迁入：当前AAR为c9cde9bc…5b1716，521类/217所选公开声明，库Manifest不含Activity。CurlOptions公共签名保持，Apple半径说明为参考峰值，局部锥面半径随生成线变化。实际Maven Debug/默认R8构建38秒成功，ZIP所选AAR一致。当前几何与证据边界见10第3/45节；上文313版本属于迁入前历史。





