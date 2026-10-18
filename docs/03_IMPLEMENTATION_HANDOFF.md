# 新对话交接

整理日期：2026-10-18。先读本文和[当前架构](02_ARCHITECTURE_AND_PROJECT_STRUCTURE.md)，再按任务读[文档入口](README.md)中的专题。不要整批加载历史产物/JVM清单。

## 工作区与Git

- 主项目：`D:/Code/Agent/iRead`；不要在旧参考目录实施本项目修改。
- 旧参考：`F:/User/Documents/ChatGPT/libreadview`，只作用户明确要求的对照；不能覆盖其中既有修改。
- 模块`:app`与`:app:ireadview`；库包`org.peyilo.ireadview`，私有远程`git@github.com:Peyilo/iReadView.git`。
- 文档整理前基线为`46f785a`，分支main。后续选择、模板、Demo及文档收敛工作随2026-10-18的提交一并保存；最新提交和远程同步状态以git log/git status为准。继续前先检查工作区，不得reset/clean丢掉新增工作。
- 文档整理步骤只修改文档并检查引用，没有修改生产源码或重跑软件构建/测试；其后按用户要求将此前代码及文档一起提交并推送。

## 用户确定的方向

- 这是可嵌入Widget库；架构、设计模式协调简洁，代码与当前设计同步维护。
- 基础容器、书籍层、分页/滚动呈现和视觉效果分开；宿主只用公开API。
- 页眉页脚必须保留任意View的底层扩展，六位置默认模板是更高层便利实现。不能反向强迫自定义布局遵循三栏。
- Demo主阅读页直接使用模板；不继续调用/迁移旧单行内容和对齐配置。外观字号/边距等仍可用底层样式API。
- 选区菜单紧凑，按当前组item数量收缩，图标+文字，超宽分页。长按通常选择整段；命中已有高亮则恢复它的完整跨段/跨页范围。
- 高亮与划线是不同标注；菜单均为添加/取消切换，统一存储。线条样式API保留，Demo不要恢复线条样式编辑UI。
- 起点圆点在上、终点圆点在下；按行绘制选中底色，颜色可配置；支持同章跨页拖动及离页端点代理。
- ReaderView本身全屏，系统安全边距由Demo模板包装处理；不要靠单独涂状态栏掩饰布局问题。
- Apple pageCurl一比一复现仅讨论过，用户已暂缓，未授权新实现；不要把它当当前任务。
- 用户可能同时操作模拟器，不能把未控制的页码变化当成程序缺陷。

## 最近已实现、但多数尚未设备验收

1. 行首手柄：圆点与真实caret分离，竖线只按一个线宽限制可见边界；不再整根按圆点半径内移。行末命中补软换行exclusive端点，普通/最近位置共用。
2. 长按已有高亮：分页/滚动按绘制路径命中记录ID，验证完整range/quote后恢复选区；未命中仍选段落。
3. 六位置模板：ReaderPageChromeSlot、setPageChrome、withHeader/withFooter/withBackButton；内置书名/章名/页码/时间/电量及自定义文字/点击。
4. 模板解耦：内容解析与View分离，电量监听从通用时钟移回默认模板；自定义SPI无新增模板专属要求。
5. Demo模板接入：PageChromeSettings/PageChromeCodec，六位置配置UI与独立保存；主阅读页不再切换旧模板。CustomPageFactory移至CustomContentActivity专门示例。
6. 分页尾空白：排除章节尾部空白的视觉高度，保留原文、ID及锚点；同一套连续片段也生效。
7. iBook当前材料网格、正面弯曲、主题背色、两个接收面阴影及源投影范围裁剪见[渲染专题](RENDERING.md)。不要恢复失败的无限条带/仅接收区域裁剪方案。

## 验证边界与下一步

截至上次实现：Demo Debug APK、库Release及Maven包已有构建记录；Demo最后一次模板调整只重建Debug。最近选择/模板/电量/重建未做设备交互验收。旧阴影离屏检查和章节分页测试只能证明有限场景，详见[状态与证据](07_REQUIREMENT_IMPLEMENTATION_AUDIT.md)。

用户最后明确任务是整理文档并换对话继续，没有指定下一项功能。此前建议搜索、笔记/标注扩展、内容变化重定位、跨章选择、Compose和发布体系；这些只是建议，不是待自动执行的授权清单。优先与用户明确下一项；不要把旧清单中的未实现增强写成当前能力。

## 本机工具与产物

- PowerShell；JDK17：`D:/DevKits/JDK/jdk-17.0.10`；Android SDK：`D:/DevKits/AndroidSDK`。
- Python：`D:/App/Anaconda/python.exe`；处理中文脚本设`PYTHONUTF8=1`。
- 构建：`./gradlew.bat :app:assembleDebug :app:ireadview:assembleRelease :app:ireadview:bundleReleaseRepository`。
- APK：`app/build/outputs/apk/debug/app-debug.apk`。生成接口清单用`tools/Export-ReleaseApi.ps1`，实际命令见[发布清单](09_RELEASE_JVM_API_REFERENCE.md)。
- 既有会话要求：除非用户要求测试/验证，不自动新增或运行软件测试；构建与实际测试要分别报告。文档整理只检查文件引用和源码未变。

网络代理配置是本机外部任务，脚本/备份位于桌面V2Ray-Proxy-Setup，与本仓库无关；本次未查询其最终WinHTTP状态，也不将其列入阅读库待办。
