# CDC 作业恢复指南 - 确保不丢失变更

## 概述

本指南说明如何确保 CDC 作业在重启时不丢失任何数据变更。

## 数据不丢失的机制

### 1. Checkpoint 机制

- **Checkpoint 间隔**: 每 10 秒执行一次 checkpoint
- **文件提交策略**: 使用 `OnCheckpointRollingPolicy`，文件在每次 checkpoint 完成时提交
- **保留策略**: 作业取消时保留 checkpoint (`RETAIN_ON_CANCELLATION`)

### 2. Oracle CDC 位置追踪

Debezium 使用 `log_mining_flush` 表记录已处理的 SCN (System Change Number) 位置。重启时会从上次记录的位置继续。

## 正确的停止方式

### 推荐：带 Savepoint 停止（不丢失数据）

```bash
# 通过 API 停止作业并创建 savepoint
curl -X POST "http://localhost:5001/api/jobs/{jobId}/stop"

# 或指定 savepoint 目录
curl -X POST "http://localhost:5001/api/jobs/{jobId}/stop?targetDirectory=file:///opt/flink/savepoints"
```

### 不推荐：直接取消（可能丢失最近 10 秒数据）

```bash
curl -X POST "http://localhost:5001/api/jobs/{jobId}/cancel"
```

## 从 Savepoint/Checkpoint 恢复

### 方法 1: 通过 Flink Web UI

1. 访问 http://localhost:8081
2. 点击 "Submit New Job"
3. 选择 JAR 文件
4. 在 "Savepoint Path" 中填入 savepoint 路径
5. 提交作业

### 方法 2: 通过 REST API

```bash
# 从 savepoint 恢复
curl -X POST "http://localhost:8081/jars/{jarId}/run" \
  -H "Content-Type: application/json" \
  -d '{
    "savepointPath": "file:///opt/flink/savepoints/savepoint-xxx",
    "programArgs": "--hostname oracle11g --port 1521 ..."
  }'
```

### 方法 3: 从最新 Checkpoint 恢复

如果没有 savepoint，可以从最新的 checkpoint 恢复：

```bash
# 查看 checkpoint 目录
ls -la /opt/flink/checkpoints/{jobId}/

# 找到最新的 checkpoint 目录 (chk-xxx)
# 使用该路径作为 savepointPath 参数
```

## 查看 Checkpoint 状态

```bash
# 获取作业的 checkpoint 信息
curl "http://localhost:5001/api/jobs/{jobId}/checkpoints"
```

## 最佳实践

1. **定期创建 Savepoint**: 在计划维护前手动创建 savepoint
2. **监控 Checkpoint**: 确保 checkpoint 正常完成
3. **使用 Stop 而非 Cancel**: 停止作业时使用带 savepoint 的 stop 命令
4. **保留 Checkpoint 目录**: 不要删除 `/opt/flink/checkpoints` 目录

## 故障恢复场景

### 场景 1: 计划内重启

1. 调用 `/api/jobs/{jobId}/stop` 创建 savepoint
2. 记录返回的 savepoint 路径
3. 重启后从 savepoint 恢复

### 场景 2: 意外崩溃

1. Flink 会自动从最新 checkpoint 恢复
2. 如果自动恢复失败，手动从 checkpoint 目录恢复

### 场景 3: 集群重建

1. 确保 checkpoint/savepoint 目录使用持久化存储
2. 从最新的 savepoint 或 checkpoint 恢复

## 数据丢失风险

| 场景 | 最大数据丢失 |
|------|-------------|
| 正常 checkpoint | 0 |
| 带 savepoint 停止 | 0 |
| 直接取消 | 最多 10 秒 |
| 意外崩溃 | 最多 10 秒 |

## 相关 API

| API | 说明 |
|-----|------|
| `POST /api/jobs/{id}/stop` | 带 savepoint 停止 |
| `POST /api/jobs/{id}/cancel` | 直接取消 |
| `GET /api/jobs/{id}/checkpoints` | 查看 checkpoint |
