# 条形阴影越界：光线投影范围检查

## 已复现原因

用户指出的是阴影范围，不是渐变色阶。37号渐变纹理替换没有解决这一范围错误。

`IBookCurlShadow` 仍会围绕卷边生成一条细长矩形/梯形。36号裁剪只检查接收面是否露出：页面矩形减去当前正面与背面。这个条件无法保证露出的区域真的被翻起纸张遮光；条带沿纸边或法向扩展的剩余部分会落到不相关区域。

## 修正

增加 `IBookShadowFootprint`，直接使用实际正背面材料三角形。每个顶点按同一光向投到下一页 z=0：

```
Q = (screenX + slopeX*shadowHeight,
     screenY + slopeY*shadowHeight)
S = union(projected paper triangles)
VisibleNext = PageRect - FrontFace - BackFace
DrawnShadow = ShadowRibbon ∩ S ∩ VisibleNext
```

投影三角形统一为相同绕序，WINDING填充表示重叠并集，不因前后面方向相反抵消。丢弃退化/非有限投影三角形。不是轴对齐包围矩形，也不是仅缩短条带长度。

Canvas裁剪顺序由生产与设备测试共用的 `clipIBookNextShadow` 实现。复用数组、Path、已有材料网格，不做逐三角形Path布尔op。较旧Android显示用的规则网格会把隐藏材料压到边缘，因此不能拿它当遮光纸面；两条显示路径均使用可见材料分区网格构建同一个光线投影范围。RTL的材料纹理反射在范围生成后进行。

保留当前页投影、曲面微遮蔽、主题颜色和现有公共参数。本次使用几何投影范围作为严格支持边界，没有把柔化条带任意扩展到边界以外；这是一种有限支持的软阴影近似，尚不是完整面积光源半影积分。shadowHeight沿用34号视觉高度近似。

## 检查证据（2026-10-15）

- JVM固定姿态检查24组：旧接收区域裁剪保留5,565个光线投影范围外的可见阴影采样点，合法范围内保留316个采样点。
- emulator-5554，Android 16，实际Android Bitmap Canvas和生产裁剪函数离屏检查24组：旧方案范围外像素78,666；新方案范围外像素0；新方案保留合法阴影像素11,383。
- 像素统计避让投影边界1像素，允许光栅化舍入差异。检查覆盖200×400、半径比例0.08、抓取高度0/100/400、进度0.2/0.4/0.6/0.8、纵向偏移±160。不是所有姿态的证明。
- 设备测试不拖动正在阅读的界面，测试包独立自测；完成后移除临时测试包。离屏Bitmap Canvas验证了裁剪和实际纹理阴影像素，尚未覆盖硬件Canvas的逐帧性能和全部视觉姿态。

回归文件：`IBookShadowFootprintTest`、`IBookShadowClipDeviceTest`。中间数据和PNG位于库模块build/reports/ibook-shadow，发布结果摘要另存docs/generated/ibook-shadow-source-support.json。

## 构建

5项JVM回归、1项Android离屏检查通过；Debug APK、Release AAR及Maven仓库包构建通过。发布清单557个class条目、237项公共声明，无Demo/测试类混入。
