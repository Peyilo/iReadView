# 生成材料：按需读取

- `release-inventory.json`、`release-jvm-api.txt`：对应其中日期/哈希的公共 JVM 导出，用 `tools/Export-ReleaseApi.ps1` 重新生成。
- `ios-curl-kotlin/`：Kotlin 核心、Android 适配与主题背景取色的验证摘要。每份结果只对应记录的源码和产物。
- `local-publication-inventory.json`、`current-release-bundle-inventory.json`、`maven-host-*`：历史发布与独立消费记录，具体日期和哈希以文件为准。

已移除旧仿真的可执行预览、截图和专用数学报告。历史发布清单不能替代当前源码、构建或设备验证。
