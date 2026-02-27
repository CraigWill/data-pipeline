# Markdown 文档目录

本目录包含所有项目相关的 Markdown 文档，主要是状态报告、问题解决方案和操作指南。

## 目录结构

```
md/
├── README.md                                    # 本文件
├── CURRENT_CDC_STATUS.md                        # 当前 CDC 状态报告
├── CDC_*.md                                     # CDC 相关文档
├── CSV_*.md                                     # CSV 文件相关文档
├── ORACLE_*.md                                  # Oracle 相关文档
├── FLINK_*.md                                   # Flink 相关文档
├── TASKMANAGER_*.md                             # TaskManager 相关文档
└── ...
```

## 文档分类

### 当前状态报告
- `CURRENT_CDC_STATUS.md` - 最新的 CDC 系统状态报告
- `CURRENT_STATUS.md` - 系统当前状态

### CDC 实施和配置
- `CDC_IMPLEMENTATION_SUCCESS.md` - CDC 实施成功记录
- `CDC_COMPARISON.md` - CDC 方案对比
- `CDC_JOB_STARTED.md` - CDC 作业启动记录
- `CDC_JOB_RESTART_SUCCESS.md` - CDC 作业重启成功记录
- `CDC_FINAL_STATUS.md` - CDC 最终状态
- `CDC_SUCCESS_STATUS.md` - CDC 成功状态
- `CDC_STATUS_SUMMARY.md` - CDC 状态摘要

### 问题解决方案
- `CDC_CONNECTION_ISSUE.md` - CDC 连接问题解决
- `CDC_AUTO_COMMIT_FIX.md` - 自动提交问题修复
- `CSV_FILE_NOT_GENERATED_SOLUTION.md` - CSV 文件未生成问题解决
- `TASKMANAGER_HEARTBEAT_TIMEOUT_FIX.md` - TaskManager 心跳超时修复
- `TASKMANAGER_REGISTRATION_FIX.md` - TaskManager 注册问题修复
- `ORACLE_CONNECTION_TIMEOUT_FIX.md` - Oracle 连接超时修复
- `ORACLE_CONNECTION_TIMEOUT_FIXED.md` - Oracle 连接超时已修复
- `KAFKA_CONNECTION_FIX.md` - Kafka 连接修复
- `JDBC_DRIVER_FIX_SUCCESS.md` - JDBC 驱动修复成功

### CSV 文件相关
- `CSV_FILE_GENERATION_STATUS.md` - CSV 文件生成状态
- `CSV_GENERATION_SPEED_OPTIMIZATION.md` - CSV 生成速度优化
- `CSV_HEADER_FIX.md` - CSV 标题行修复
- `CSV_TIMESTAMP_NAMING.md` - CSV 时间戳命名
- `NEW_CSV_FORMAT.md` - 新的 CSV 格式

### Oracle 配置
- `ORACLE_CDC_DEPLOYMENT_SUCCESS.md` - Oracle CDC 部署成功
- `ARCHIVELOG_ENABLED_NEXT_STEPS.md` - 归档日志启用后的步骤
- `ENABLE_ARCHIVELOG_GUIDE.md` - 启用归档日志指南

### Flink 配置
- `FLINK_CDC_STATUS.md` - Flink CDC 状态
- `FLINK_CDC3_LATEST_MODE_SUCCESS.md` - Flink CDC 3.x Latest 模式成功
- `FLINK_HA_ENABLED.md` - Flink 高可用已启用

### 系统组件
- `SYSTEM_COMPONENTS.md` - 系统组件说明
- `KAFKA_USAGE_STATUS.md` - Kafka 使用状态
- `DEBEZIUM_*.md` - Debezium 相关文档

### 快速开始和指南
- `QUICK_START_CDC.md` - CDC 快速开始
- `QUICK_START_AUTO_RECOVERY.md` - 自动恢复快速开始

### 其他
- `AUTO_RECOVERY_SUMMARY.md` - 自动恢复摘要
- `FINAL_JOB_STATUS.md` - 最终作业状态
- `OUTPUT_SUMMARY.md` - 输出摘要
- `db-connection-summary.md` - 数据库连接摘要
- `SINGLE_SOURCE_FIX.md` - 单一数据源修复

## 使用方法

从项目根目录查看文档：

```bash
# 查看当前状态
cat md/CURRENT_CDC_STATUS.md

# 查看问题解决方案
cat md/CSV_FILE_NOT_GENERATED_SOLUTION.md

# 查看所有 CDC 相关文档
ls md/CDC_*.md
```

## 文档命名规范

- `CURRENT_*` - 当前状态报告
- `*_STATUS.md` - 状态报告
- `*_FIX.md` 或 `*_FIXED.md` - 问题修复记录
- `*_SUCCESS.md` - 成功实施记录
- `*_GUIDE.md` - 操作指南
- `*_SUMMARY.md` - 摘要文档
- `QUICK_START_*.md` - 快速开始指南

## 相关目录

- Shell 脚本: `../shell/`
- 项目文档: `../docs/`
- SQL 脚本: `../` (根目录)
- Docker 配置: `../docker/`

## 维护说明

1. 新的状态报告应该更新 `CURRENT_CDC_STATUS.md`
2. 问题解决后应该创建对应的 `*_FIX.md` 或 `*_FIXED.md` 文档
3. 重要的配置变更应该记录在相应的文档中
4. 定期清理过时的文档，或移动到 `archive/` 子目录
