# 统一分页缓存

TXT、网络小说等原生文本和自研EPUB正文默认使用 `THREE_PAGE`：当前页加最多两个备用页。备用页完成真实模板/正文准备，前后翻页命中时直接复用；不是为了组件树数量创建三个空容器。书首、书尾、预加载未完成或选区交互期间可能不足三页。

## 宿主 API

```kotlin
import org.peyilo.ireadview.api.model.ReaderPageCacheMode

// 默认 THREE_PAGE；两个正文后端共用同一设置。
reader.setPageCacheMode(ReaderPageCacheMode.THREE_PAGE)
reader.setPageCacheMode(ReaderPageCacheMode.SINGLE_PAGE)
val mode = reader.pageCacheMode
```

Java使用 `setPageCacheMode(...)` / `getPageCacheMode()`。设置必须在主线程调用，返回 `ReaderResult<Unit>`；运行中的导航/翻页返回 `Rejected(Busy)`，释放后的Widget返回 `Rejected(Released)`，不会在动画仍持有表面时清理页面。异步导航请求排队但尚未开始时，仍可调整设置。

切换不重新打开书籍、不改变已提交位置。单页模式取消空闲预加载并释放缓存拥有的备用页；正在取消的准备任务在退出时清理自己的资源。单页导航仍允许临时挂载目标候选页，动画结束后只保留当前页。三页模式重新在空闲时准备邻页。

此设置属于Widget配置，跨换书和分页/滚动模式切换保留。滚动呈现不应用分页页面池，回到分页后采用已选策略。它不写入ReaderSnapshot或宿主存储；需要跨进程恢复时由宿主保存并在打开书籍前设置。Demo在“阅读输入 → 页面缓存”调用同一API，可比较两种策略。

## 组合式架构

```text
BookReadView.pageCacheMode
  └─ BookPresentationHost.pageCacheMode / onPageCacheModeChanged()
       ├─ PagedBookPresentation（TextReaderBodyView）
       └─ EpubBookPresentation（EpubReaderBodyView）
            两者组合相同的 ReaderPageCache<T> + ReaderPagePreloader
```

- `ReaderPageCache<T>`只管理备用页的容量、取出/归还、清空和释放。它不依赖Android View、书籍、WebView、文本块或翻页效果，可独立进行所有权测试。
- `ReaderPagePreloader`管理主线程空闲调度、相同任务去重、取消及等待清理；在阅读空闲24 ms后开始。实际导航开始前取消并等待，避免预加载改动正在使用的页面，也不与动画争用渲染资源。
- 两种Presentation分别提供邻页定位、实际正文绑定和有效性判断。它们通过组合使用缓存策略，不相互引用，也不要求ReaderBodyView承担书籍位置或缓存管理。
- 当前页归Presentation，目标候选归导航事务，备用页归缓存。`take`转移所有权，缓存释放不会关闭已取出的候选；取消由候选清理，提交后旧当前页才归还缓存。创建新候选前预留容量，三页模式同时挂载的正文不超过三份。
- 内容代次、样式、模板、来源和书籍关闭沿用既有呈现层失效/释放边界。原生装饰变化清理备用页，重新准备时应用最新选区/标注；EPUB复用其DOM/批注同步与显示列表失效机制。显示/停放状态变化仍须重录EPUB表面，避免已修复的“第二次翻页当前页消失”。

第三方BookPresentation可读取Host策略并组合这两个SPI；新增格式不需要修改Widget中的格式判断。

## 验证范围

`ReaderPageCacheTest`验证缓存取出后的所有权、候选预留、容量裁剪、运行时禁用和重复归还/清空的释放次数。

`ReaderPageCacheIntegrationTest`用同一流程打开原生文本与EPUB，验证三页挂载、下一页使用预加载正文、后退复用旧当前页、Busy拒绝、单页释放、重新启用、位置不变、样式失效、滚动切换及换书/关闭后的清理。性能结论还需看完整动画帧数据，不能仅凭三个root或缓存命中宣称所有效果达到60fps。

2026-10-19，27项核心JVM测试通过，Debug/AndroidTest APK和核心/EPUB Release AAR构建成功。API36模拟器1080×2424，联合运行 `OK (8 tests)`（201.128秒）：缓存契约、全部21组效果/内容性能与42次真实提交/取消、双页可见性、手势/白闪、两种正文选区和滚动同步、ARM102页前后翻及跨章。日志 `app/build/page-cache-regression.log`。

最终版本另将EPUB滚动呈现的备用页容量固定为零，只保存宿主的分页策略，补跑缓存契约及三项选区测试 `OK (4 tests)`（27.733秒），日志 `app/build/page-cache-final-device.log`。它没有改变上述分页性能路径；API21真机兼容与低端设备性能矩阵未在本轮执行。

历史性能数据见 [三页策略](performance/turn-three-page.json)，只对应当时版本。当前仿真只有 iOSCurl / GoogleCurl；缓存命中改善准备成本，但不代表 GPU 瓶颈或 API21 真机性能已解决。
