# Pulp 多版本定向校验

仅解析 APK 中真实 DEX 定义和资源表，不生成整包反编译源码。

运行 `python -B validator_selftest.py` 检查读取器。运行 `python -B validator.py --help` 查看定向类、成员与资源验证参数。

映射复现：`python -B map-generate.py --evidence-root ../../.verify/569 --recheck`，再运行同目录 `map-java.py`、`map-coverage.py`、`map-matrix.py` 并传相同 `--evidence-root`。生成的 Java 数据文件先输出到证据目录，审阅后复制到源码。

证据输入包含五版 validator 元数据、audit 源码目标、inventory 静态调用锚点、resource XML 角色映射和阅读器定向 Smali 摘要。具体输入以脚本内读取项为准，缺证据保留 unresolved。已注册项零失败不表示全部功能已适配；静态定义命中不表示实机功能或视觉通过。

`.verify/569` 保存本轮 APK 身份、原始定向查询与报告。APK 路径使用本地 inventory 中的实测位置。
