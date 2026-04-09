# Flink Checkpoint 恢复指南

## 问题描述

遇到以下错误：
```
Caused by: org.apache.flink.util.FlinkException: Could not retrieve checkpoint 13629 from state handle under /0000000000000013629. 
This indicates that the retrieved state handle is broken. Try cleaning the state handle store.
```

这表示 Flink checkpoint 文件损坏，导致作业无法恢复。

## 原因分析

Checkpoint 损坏的常见原因：
1. 磁盘空间不足导致写入不完整
2. 进程异常终止（OOM、强制 kill 等）
3. 文件系统错误
4. 网络存储问题（如果使用远程存储）
5. Checkpoint 超时

## 解决方案

### 方案 1: 清理损坏的 Checkpoint 并重启（推荐）

这是最安全的方法，会从头开始处理数据。

```bash
# 1. 停止所有 Flink 作业
docker-compose stop jobmanager jobmanager-standby taskmanager

# 2. 清理所有 checkpoint 数据
docker exec flink-jobmanager rm -rf /opt/flink/checkpoints/*

# 3. 清理 ZooKeeper 中的 Flink 状态
docker exec zookeeper zkCli.sh deleteall /flink

# 4. 重启 Flink 集群
docker-compose up -d jobmanager jobmanager-standby taskmanager

# 5. 等待集群就绪（约 30 秒）
sleep 30

# 6. 通过前端或 API 重新提交作业
```

### 方案 2: 只清理特定作业的 Checkpoint

如果只有一个作业有问题：

```bash
# 1. 找到有问题的 Job ID（从错误日志或前端获取）
JOB_ID="77484b68857c075269c03973b188d34a"

# 2. 取消作业
curl -X PATCH "http://localhost:8081/jobs/$JOB_ID?mode=cancel"

# 3. 清理该作业的 checkpoint
docker exec flink-jobmanager rm -rf /opt/flink/checkpoints/$JOB_ID

# 4. 重新提交作业
```

### 方案 3: 从 Savepoint 恢复（如果有）

如果之前创建过 savepoint：

```bash
# 1. 列出可用的 savepoint
docker exec flink-jobmanager ls -la /opt/flink/savepoints/

# 2. 使用 savepoint 路径重新提交作业
# 在提交作业时指定 savepoint 路径
```

## 使用修复脚本

我们提供了自动化脚本来简化修复过程：

### 修复特定作业的 Checkpoint

```bash
./fix-checkpoint.sh <job-id>
```

选项：
- `--clean-all`: 清理所有 checkpoint 数据
- `--restart`: 清理后自动重启作业

示例：
```bash
# 只清理最近的损坏 checkpoint
./fix-checkpoint.sh 77484b68857c075269c03973b188d34a

# 清理所有 checkpoint
./fix-checkpoint.sh 77484b68857c075269c03973b188d34a --clean-all

# 清理并重启
./fix-checkpoint.sh 77484b68857c075269c03973b188d34a --clean-all --restart
```

### 清理旧的 Checkpoint（定期维护）

```bash
chmod +x clean-old-checkpoints.sh
./clean-old-checkpoints.sh
```

## 完全重置 Flink 集群

如果上述方法都不行，可以完全重置：

```bash
# 1. 停止所有服务
docker-compose down

# 2. 清理所有数据
docker volume prune -f

# 3. 重新启动
docker-compose up -d

# 4. 重新创建和提交所有作业
```

## 预防措施

### 1. 增加 Checkpoint 超时时间

编辑 `docker/jobmanager/flink-conf.yaml`:

```yaml
# Checkpoint 超时（默认 10 分钟，可以增加到 30 分钟）
execution.checkpointing.timeout: 1800000  # 30 分钟

# Checkpoint 最小间隔
execution.checkpointing.min-pause: 60000  # 1 分钟
```

### 2. 配置 Checkpoint 保留策略

```yaml
# 保留最近的 N 个 checkpoint
state.checkpoints.num-retained: 3

# Checkpoint 清理模式
execution.checkpointing.externalized-checkpoint-retention: RETAIN_ON_CANCELLATION
```

### 3. 监控磁盘空间

```bash
# 定期检查磁盘使用情况
docker exec flink-jobmanager df -h /opt/flink/checkpoints

# 设置告警（磁盘使用超过 80%）
```

### 4. 定期清理旧 Checkpoint

添加到 crontab：

```bash
# 每天凌晨 2 点清理旧 checkpoint
0 2 * * * /path/to/clean-old-checkpoints.sh
```

### 5. 使用外部存储

对于生产环境，建议使用可靠的外部存储：

```yaml
# 使用 HDFS
state.checkpoints.dir: hdfs://namenode:9000/flink/checkpoints

# 使用 S3
state.checkpoints.dir: s3://my-bucket/flink/checkpoints

# 使用 MinIO
state.checkpoints.dir: s3://my-bucket/flink/checkpoints
s3.endpoint: http://minio:9000
s3.access-key: minioadmin
s3.secret-key: minioadmin
```

## 验证修复

修复后，验证以下内容：

1. **集群状态**
```bash
curl http://localhost:8081/overview
```

2. **作业状态**
```bash
curl http://localhost:8081/jobs
```

3. **Checkpoint 统计**
```bash
curl http://localhost:8081/jobs/<job-id>/checkpoints
```

4. **监控日志**
```bash
docker logs -f flink-jobmanager
docker logs -f flink-taskmanager
```

## 常见问题

### Q: 清理 checkpoint 后数据会丢失吗？

A: 是的，清理 checkpoint 后作业会从头开始处理数据。如果需要保留进度，应该先创建 savepoint。

### Q: 如何创建 Savepoint？

A: 
```bash
curl -X POST http://localhost:8081/jobs/<job-id>/savepoints \
  -H "Content-Type: application/json" \
  -d '{"target-directory": "/opt/flink/savepoints", "cancel-job": false}'
```

### Q: Leader 选举一直失败怎么办？

A: 
1. 检查 ZooKeeper 是否正常运行
2. 清理 ZooKeeper 中的 Flink 状态
3. 重启整个集群

### Q: 如何避免 Checkpoint 损坏？

A: 
1. 确保足够的磁盘空间
2. 使用可靠的存储系统
3. 增加 checkpoint 超时时间
4. 监控作业健康状态
5. 定期创建 savepoint

## 相关文档

- [Flink Checkpoint 官方文档](https://nightlies.apache.org/flink/flink-docs-stable/docs/ops/state/checkpoints/)
- [CDC 恢复指南](CDC-RECOVERY-GUIDE.md)
- [运行时作业管理](RUNTIME-JOB-MANAGEMENT.md)
