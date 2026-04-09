# Checkpoint 问题解决总结

## 问题描述

遇到 Flink checkpoint 错误和 leader 选举失败：
```
Could not retrieve checkpoint 13629 from state handle under /0000000000000013629
Service temporarily unavailable due to an ongoing leader election
```

## 根本原因

1. **Checkpoint 损坏**: 旧的 checkpoint 文件损坏，导致作业无法恢复
2. **HA Leader 选举失败**: 启用了 ZooKeeper HA 模式，但 leader 选举一直无法完成

## 解决方案

### 步骤 1: 清理损坏的数据

```bash
# 停止所有服务
docker-compose down

# 删除损坏的 volumes
docker volume rm flink-checkpoints flink-savepoints flink-ha
```

### 步骤 2: 禁用 HA 模式

使用单节点模式启动（适合开发/测试环境）：

```bash
HA_MODE=NONE docker-compose up -d jobmanager taskmanager monitor-backend monitor-frontend
```

### 步骤 3: 验证集群状态

```bash
curl http://localhost:8081/overview
```

应该看到：
```json
{
    "taskmanagers": 2,
    "slots-total": 4,
    "slots-available": 4,
    "jobs-running": 0,
    ...
}
```

## 当前配置

- **模式**: 单节点（无 HA）
- **JobManager**: 1 个
- **TaskManager**: 2 个
- **总槽位**: 4 个
- **Checkpoint**: 已清空，作业将从头开始

## 后续步骤

1. **重新提交作业**: 通过前端界面或 API 重新提交所有 CDC 任务
2. **监控运行**: 确保作业正常运行，checkpoint 正常创建
3. **数据验证**: 验证 CDC 数据是否正确捕获

## 如果需要启用 HA

如果生产环境需要高可用性，需要：

1. **调试 ZooKeeper 配置**
   ```bash
   # 检查 ZooKeeper 连接
   docker exec zookeeper zkServer.sh status
   
   # 检查 Flink 在 ZooKeeper 中的数据
   docker exec zookeeper zkCli.sh ls /flink
   ```

2. **确保网络连通性**
   - JobManager 能访问 ZooKeeper
   - Standby JobManager 能访问 ZooKeeper
   - 检查防火墙规则

3. **检查 ZooKeeper 日志**
   ```bash
   docker logs zookeeper
   ```

4. **逐步启用 HA**
   ```bash
   # 先启动主 JobManager
   HA_MODE=zookeeper docker-compose up -d jobmanager taskmanager
   
   # 等待稳定后再启动 standby
   docker-compose up -d jobmanager-standby
   ```

## 预防措施

### 1. 定期清理 Checkpoint

创建定时任务：
```bash
# 每周清理旧 checkpoint
0 2 * * 0 docker exec flink-jobmanager find /opt/flink/checkpoints -type d -mtime +7 -exec rm -rf {} +
```

### 2. 监控磁盘空间

```bash
# 检查磁盘使用
docker exec flink-jobmanager df -h /opt/flink/checkpoints
```

### 3. 配置 Checkpoint 保留策略

在 `flink-conf.yaml` 中：
```yaml
# 只保留最近 3 个 checkpoint
state.checkpoints.num-retained: 3

# 作业取消时保留 checkpoint
execution.checkpointing.externalized-checkpoint-retention: RETAIN_ON_CANCELLATION
```

### 4. 增加 Checkpoint 超时

```yaml
# 30 分钟超时（适合大数据量）
execution.checkpointing.timeout: 1800000
```

### 5. 使用外部存储

生产环境建议使用可靠的外部存储：
```yaml
# HDFS
state.checkpoints.dir: hdfs://namenode:9000/flink/checkpoints

# S3
state.checkpoints.dir: s3://my-bucket/flink/checkpoints

# MinIO
state.checkpoints.dir: s3://my-bucket/flink/checkpoints
s3.endpoint: http://minio:9000
```

## 相关脚本

已创建以下工具脚本：

1. **fix-checkpoint.sh** - 修复特定作业的 checkpoint
2. **clean-old-checkpoints.sh** - 清理旧 checkpoint
3. **reset-flink-cluster.sh** - 完全重置集群

## 参考文档

- [CHECKPOINT-RECOVERY-GUIDE.md](CHECKPOINT-RECOVERY-GUIDE.md) - 详细恢复指南
- [Flink Checkpoint 官方文档](https://nightlies.apache.org/flink/flink-docs-stable/docs/ops/state/checkpoints/)
- [Flink HA 配置](https://nightlies.apache.org/flink/flink-docs-stable/docs/deployment/ha/overview/)
