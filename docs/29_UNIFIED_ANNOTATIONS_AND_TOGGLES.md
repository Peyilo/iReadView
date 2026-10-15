# 注解统一管理与菜单切换

## 公共模型和管理接口

书签、高亮、划线统一实现 ReaderAnnotation（id、bookId）。高亮和划线实现 RangedAnnotation，共享源范围、摘录和校验；形态与各自样式仍由具体类型表达。

AnnotationSnapshot.items 为统一不可变集合，fromItems(bookId, items) 建立快照；bookmarks/highlights/underlines 保留为兼容的分类视图，旧构造方式保留。内部 AnnotationStore 只有一个按 id 索引的集合，统一处理书籍绑定、重复 id、添加、更新、删除、待校验和状态发布，不再为每种类型维护独立存储。

BookReadView 提供 addAnnotation、updateAnnotation、removeAnnotation；原 addHighlight/addUnderline/addBookmark 等类型接口保留并共享同一存储事务。更新不能借同一 id 偷换注解类型。

未来库增加文本注释等范围类型时，可以复用 RangedAnnotation 的范围/摘录校验与同一存储生命周期；位置类型复用书签位置模型。ReaderAnnotation 为 sealed 家族，目前不承诺第三方任意类型动态注入。

## 高亮和划线切换

SelectionActions 新增 hasHighlight/hasUnderline、toggleHighlight/toggleUnderline。选区由有效同类注解完整覆盖时，点击删除覆盖它的注解；没有覆盖时添加。部分交集不等于完整覆盖，旧版本失效的标注不参与状态判断。取消删除的是原注解整条记录，不自动将其拆成残余片段。高亮与划线互不删除。

菜单默认使用切换动作；已有 highlight()/underline() 仍是显式新增动作，不改变原返回注解模型的 API 语义。选区消失、换书或内容代次变化后动作拒绝执行。菜单状态通过 SelectionMenuItem.activeTitle/state 自定义：默认显示高亮/取消高亮、划线/取消划线。

## Demo 统一保存

AnnotationStorage 根格式升为版本2：

```json
{"version":2,"book":"book-id","items":[
  {"id":"mark-id","type":"underline","schema":1,"payload":{}}
]}
```

所有类型统一一个 items 数组。AnnotationCodecs 注册类型编码器，处理各自 payload；保存组件只负责书籍和统一记录事务。新增类型只需注册编码器，不再新增根级存储数组。线型/颜色等继续随划线记录保存，TextLineStyleJson 是纯编码工具，不包含设置 UI 或默认样式偏好。

版本1的 bookmarks/highlights/underlines 数组仍能读取，下一次保存写入版本2。未知类型、未知记录 schema 或无法解码的 JSON 对象在保存时原样保留，不呈现为可操作注解；避免旧 Demo 写回时清掉将来的记录。未来根版本不读取为当前快照，也跳过写入以保护原记录。

## 绘制与 Demo 简化

划线逐行范围使用 getLineVisibleEnd 排除尾部空白，并将选择路径裁到该行 getLineLeft/getLineRight 的实际文字边界；不再使用整个 layout.width，修正软换行时向右伸长。线条样式 API 和显式更新 API 均保留。

Demo 移除线条设置 item、LineStyleSheet、样式偏好和注解详情的样式编辑按钮。菜单保留直接高亮/划线切换；注解列表继续支持查看、跳转和删除。已有保存的自定义线型不会被迁移成默认线型。

## 构建与兼容

Debug APK、Release AAR 和 Maven 包构建通过；未新增或运行测试，设备上的右边界、切换和旧数据恢复仍需验收。SelectionActions 新增抽象方法，Java 自行实现需补齐并重新编译；SelectionMenuItem 的 data class copy 完整签名新增状态参数，旧二进制宿主需重编译。原四参数构造保留。
