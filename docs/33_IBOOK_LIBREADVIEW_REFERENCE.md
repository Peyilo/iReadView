# 对照 libreadview 的 iBook 实现

参考仓库：F:/User/Documents/ChatGPT/libreadview。

## 实际读取的实现

`app/libreadview/src/main/java/org/peyilo/libreadview/turning/render/IBookCurlRenderer.kt`：

- `computeRegionAMeshVerts` / `computeRegionCMeshVerts`：圆柱投影，隐藏材料压到半径边缘，背面另做反射；不是当前圆锥模型。
- `buildFrontPagePath` / `buildBackPagePath`：只沿网格四条外边建立边界。
- `render`：分别用 pathA / pathB / pathC 裁剪正面、下一页和背面，再绘制阴影。
- `makeShadowLut`：256×1渐变 Bitmap，仅初始化一次。
- `drawBottomPageShadow` / `drawCylinderShadow`：通过缩放、旋转、平移复用阴影 LUT；圆柱阴影裁到 pathAC。
- `drawShadowA`：TODO；没有完整的背面对当前页投影实现。

旧实现还留有控制点重合除零、装订约束等 TODO，不适合原样替换当前几何模型。

## 本次迁移

1. 正面、背面纹理均按各自实际网格边界裁剪，两条 Android 绘制路径一致。共用规则网格边界提取方法；索引网格继续只取外轮廓，不创建逐三角形轮廓或每帧 Path 布尔差集。
2. 当前页距离场阴影保持一维渐变查表缓存，改用归一化距离平方索引。512项表在实例初始化时生成；每帧删除距离求平方根与 pow 调用，梯度公式、公共强度/柔和度参数不变。两种阴影继续共用光向和软渐变定义。
3. 保留正面 S 过渡、圆锥/圆柱模型、主题背面颜色解析和当前页投影。距离场避免尖角偏移带折叠的原理与回归检查见32号文档。

这次采用旧项目的边界裁剪、渐变缓存与资源复用方式。当前页投影依旧是我们新增的近似实现；旧项目没有提供可直接迁移的对应完整实现。

## 检查

4项数学回归检查通过，Debug APK / Release AAR / Maven 包构建通过。设备像素输出和帧耗时仍未验收。
