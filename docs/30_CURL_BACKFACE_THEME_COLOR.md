# 仿真翻页背面与主题颜色

## 旧实现的依据

libreadview 的 IBookCurlEffect 在翻页开始时读取真正翻起的页面背景：下一页取当前页，上一页取上一页。ColorDrawable 直接取颜色，BitmapDrawable 使用4×4网格采样平均。IBookCurlRenderer 将截图像素与此底色通过 ColorMatrix 混合，比例为0.2，Alpha 保留。

旧代码 RGB 公式：`背面 = 0.2 × 页面内容像素 + 0.8 × 页面背景色`。因此背景像素本身基本不变，文字变淡；暗色背景不会变成浅色纸片。

## iReadView 修正

原 iBook CurlOptions 默认背面为固定#EEE9DF，卷页 shader 也固定浅纸色，导致深色主题背面发灰发亮。现在默认自动取翻起页面的背景底色，混合形式与旧实现相同：

`RGB背面 = backContentAlpha × RGB内容 + (1 − backContentAlpha) × RGB底色`

- iBook 保留 backContentAlpha 默认0.3，API29+索引网格与旧 bitmap mesh 路径共用每次翻页生成的 ColorMatrixColorFilter。
- PageCurl 的 GLES shader 使用同一底色解析逻辑，通过 uniform 传入背面色，保留该效果原混合比例0.15及纸面光照。
- Google/MoonReader 当前为行进正面，没有背面区域，此配置不作用于它。

底色在开始翻页时确定，动画期间保持稳定。向前取 current，向后取 target，包含 RTL 和取消翻页；下一次翻页重新读取主题。没有在 Demo 主题菜单中硬编码“夜间背面颜色”，所以资源背景、自定义模板和宿主调用样式接口均走同一逻辑。

## 背景来源

PageBackgroundSurface 是 PageSurface 的可选扩展接口，提供可空 backgroundColor。已有 PageSurface 实现不需要补方法。内置 ViewPageSurface 实现该接口：

1. ColorDrawable 直接读取背景色。
2. 图片、渐变、LayerDrawable 等可复制背景，通过独立4×4小位图绘制，仅采样 Drawable，不绘制文字，不申请完整页面额外位图，不修改原 Drawable 的 bounds/state。
3. 没有背景元数据或无法单独采样时，使用本次已捕获的翻起页面截图，8×8采样、RGB分桶，取出现最多的颜色桶平均值，降低文字干扰。全透明且无有效背景时回退白色。

自定义 PageSurface 可以实现 PageBackgroundSurface 提供精确底色；不实现时仍可使用截图估计。对复杂照片、大片文字、动画背景，估计不保证等于宿主希望的纸张基色，可显式指定背面色。

## 公共配置

```kotlin
// 默认：随实际翻起页面背景计算。
reader.setPageTurnEffect(PageTurnEffects.iBookCurl(CurlOptions()))

// 显式底色：忽略自动背景解析，保留宿主自定义能力。
reader.setPageTurnEffect(PageTurnEffects.iBookCurl(
    CurlOptions(backTintColor = 0xFF182022.toInt(), backContentAlpha = 0.2f)
))

// shader 卷页同样支持自动或指定。
reader.setPageTurnEffect(PageTurnEffects.pageCurl(
    PageCurlOptions(backTintColor = null, backContentAlpha = 0.15f)
))
```

backTintColor 默认null；显式颜色的 Alpha 忽略，保留页面截图 Alpha。backContentAlpha 范围0..1，是内容与底色的混合比例，不是纸张透明度。建议0.15..0.3。

## 兼容与构建范围

CurlOptions.backTintColor 由 Int 改为 Int?，JVM getter/构造参数变为 Integer；完整构造/copy 签名变化，需要重新编译旧宿主。PageCurlOptions 追加背面参数，Java旧短构造重载保留，完整copy签名变化。自定义颜色示例继续传入明确 Int，行为仍为固定底色。

Debug APK、Release AAR、本地 Maven 仓库包构建通过，API 清单更新。未添加或运行测试；浅色/夜间/资源背景、反向翻页、取消及 GLES shader 运行效果尚未设备验收。
