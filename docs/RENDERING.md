# 当前翻页渲染与阴影约束

更新：2026-10-18。保留现行实现和避免退化所需的依据；被撤回的纸张研究、逐轮候选和失败修补不再作为设计方案。

## 效果与事务边界

PageTurnEffects提供none、slide、cover、iBookSlide、iBookCurl/curl（兼容别名）、googleCurl、pageCurl；默认稳定ID以源码工厂为准。连续滚动是独立呈现，不是效果。

PageTurnEffectManager控制手势、松手完成/复位、动画时钟及资源终态；NavigationController提交位置。效果只画PageSurface，不改书页数据。PageTurnSnapshots共享预算捕获，前翻翻起current，后翻翻起target；RTL对应几何与纹理方向。

**撤回状态**：曾提出为了页眉页脚给所有新Android平移模式强制截图，用户随后确认本来每页都有，要求撤回。当前保持API29+原View绘制；旧系统平移模式尝试两张原分辨率快照，预算不足回退View。不要重新应用那次误诊改动。

## iBook现行路径

主要源码：IBookCurlGeometry、IBookCurlMesh、CurlEffects、IBookFrontShadow、IBookCurlShadow、IBookShadowFootprint。

几何采用装订约束的可展曲面近似及前面浅弯曲，文字随材料UV映射；正背面分区裁剪。显示网格会压缩隐藏顶点，不能把它当作实际遮光纸面。现代路径使用索引网格，兼容路径使用bitmap mesh；材料投影支持使用独立可见材料网格。退化三角形与非有限值必须排除。

前面浅弯曲使用缓存积分映射；曲面微遮蔽是轻度补充，独立于背色混合，不应通过整页变暗掩盖几何问题。绷直时半径可能被约束到0，投影高度不能因此突变消失。

### 阴影高度近似

设requestedR为进度包络中的请求半径，r为约束后的半径，D=max(requestedR-r,0)，s为正向材料距离。绷直补偿近似为：

```text
extraHeight = 2*D*s/(s+requestedR) * clamp(materialX/pageWidth)
shadowHeight = materialHeight + extraHeight
```

这是视觉高度近似，不是完整薄壳物理模型。根部和端点需连续；不能仅凭截图声称真实物理仿真。

### 当前页与下一页投影

两个投影共享光向/柔化轮廓。当前页使用规则距离场，避免沿曲边逐段画窄条产生扇形折叠。下一页仍用有限渐变条带，但必须同时裁到实际光线投影支持：

```text
Q = (screenX + slopeX*shadowHeight, screenY + slopeY*shadowHeight)
S = union(projected visible material triangles)
VisibleNext = PageRect - FrontFace - BackFace
DrawnShadow = ShadowRibbon ∩ S ∩ VisibleNext
```

投影三角形统一绕序，WINDING并集不应因正背面相反方向抵消。生产/设备检查共用clipIBookNextShadow。先从原材料纹理坐标构建支持，再做RTL纹理反射。

**不可恢复的旧修补**：只做PageRect减正背面，仍会允许细矩形阴影延伸到无关区域；换渐变纹理只能改善色带，不能修复投影范围。不能拿轴对齐包围矩形或隐藏顶点塌缩的显示网格替代实际支持。

当前条带使用缓存1024×1渐变纹理及有限网格，端部向内衰减，不向轴外增加长尾；资源按clear/release归属清理。当前页距离场用有限采样/LUT，避免每帧昂贵逐像素计算和海量临时路径。当前有限支持软阴影不是完整面积光源半影积分。

### 主题背色

```text
backRGB = backContentAlpha*contentRGB + (1-backContentAlpha)*backgroundRGB
```

从真正翻起页面取底色，翻页开始固定，下一次再读取主题。PageBackgroundSurface可提供背景元数据；内置背景可独立采样，缺失时从页面快照主色近似。不要固定浅色纸背导致夜间发亮，也不要隐藏背字绕开映射错误。Google路径没有该背面区域。

## 独立pageCurl路径

PageTurnEffects.pageCurl(PageCurlOptions)稳定ID为page_curl，使用BoundPageCurlEffect/Renderer/Shader，GLES2离屏渲染，复用快照与事务资源。与iBook网格效果不同，勿混用两套参数含义。

默认radiusFraction/shadowStrength/lightSize/tilt为0.10/0.25/0.43/0.55；相应with方法公开。建议半径0.08～0.14、强度0.15～0.30、柔和度0.30～0.50、倾斜-0.60～0.60，仍须遵循源码校验范围。

受控圆柱以材料坐标映射到正背面纹理；屏幕到材料逆映射包含asin圆弧分支及展开尾部，按材料是否位于页矩形、正背遮挡决定采样。背字混合、光照和两个接收面阴影在该shader实现；不是在Canvas上画一张不变形的旋转截图。

[WebGL观察Demo](examples/shader-preview/index.html)保留为独立参考。网页的拖动阈值/松手曲线不覆盖Widget的PageTurnOptions，不能把网页验收自动当成Android验收。

## 历史证据及限制

[数学摘要](generated/ibook-mathematical-validation.json)记录2026-10-15的固定姿态：纸面202,958个三角形方向错误0；距离场276,480个单元折叠0，对比旧窄条577个折叠单元。报告绑定当时AAR哈希，不是当前源码的重新运行结果。

[投影支持检查](generated/ibook-shadow-source-support.json)记录24组姿态。Android Bitmap Canvas旧范围外像素78,666，新0，保留合法像素11,383；边界避让1px。检查源码为IBookProjectionTest、IBookShadowFootprintTest和IBookShadowClipDeviceTest。

这些检查不是所有姿态的证明，也未覆盖硬件Canvas帧率、真实设备/刷新率、长时间资源变化或用户视觉认可。API28/36早期有限UI观察和generated/page-curl中的图片/JSON只是当时pageCurl路径的历史参考。
