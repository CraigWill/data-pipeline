# Flink HA 模式启用总结

## 成功启用 HA 模式

Flink 集群现在运行在高可用（HA）模式下，使用 ZooKeeper 进行 leader 选举和状态管理。

## 当前配置

### 集群组件

| 组件 | 数量 | 状态 | 端口 |
|------|------|------|------|
| ZooKeeper | 1 | Healthy | 2181 |
| JobManager (主) | 1 | Healthy | 8081, 6123-6124 |
| JobManager (备) | 1 | Healthy | 8082, 6125-6126 |
| TaskManager | 2 | Healthy | 多个端口 |
| Monitor Backend | 1 | Running | 5001 |
| Monitor Frontend | 1 | Running | 8888 |

### HA 配置详情

```yaml
high-availability: zookeeper
high-availability.zookeeper.quorum: zookeeper:2181
high-availability.zookeeper.path.root: /flink
high-availability.cluster-id: /realtime-pipeline
high-availability.storageDir: file:///opt/flink/ha
```

### 集群资源

- **TaskManagers**: 2 个
- **总槽位**: 4 个
- **可用槽位**: 4 个
- **Flink 版本**: 1.20.0

## 启用步骤回顾

### 1. 清理旧数据

```bash
# 停止所有服务
docker-compose down

# 删除损坏的 volumes
docker volume rm flink-checkpoints flink-savepoints flink-ha
```

### 2. 逐步启动服务

```bash
# 1. 启动 ZooKeeper
docker-compose up -d zookeeper
sleep 10

# 2. 清理 ZooKeeper 中的旧数据
docker exec zookeeper bash -c "echo 'deleteall /flink/realtime-pipeline' | /usr/bin/zookeeper-shell localhost:2181"

# 3. 启动主 JobManager
docker-compose up -d jobmanager
sleep 20

# 4. 启动 TaskManager
docker-compose up -d taskmanager
sleep 10

# 5. 启动 standby JobManager
docker-compose up -d jobmanager-standby
sleep 15

# 6. 启动监控服务
docker-compose up -d monitor-backend monitor-frontend
```

## HA 功能验证

### 1. 检查集群状态

```bash
curl http://localhost:8081/overview
```

预期输出：
```json
{
    "taskmanagers": 2,
    "slots-total": 4,
    "slots-available": 4,
    "jobs-running": 0,
    "flink-version": "1.20.0"
}
```

### 2. 检查 ZooKeeper 连接

```bash
docker exec zookeeper bash -c "echo 'ls /flink/realtime-pipeline' | /usr/bin/zookeeper-shell localhost:2181"
```

应该看到：
```
[jobgraphs, jobs, leader]
```

### 3. 测试故障转移

```bash
# 停止主 JobManager
docker-compose stop jobmanager

# 等待 30 秒，standby 应该接管
sleep 30

# 检查 standby JobManager（端口 8082）
curl http://localhost:8082/overview

# 重启主 JobManager
docker-compose start jobmanager
```

## HA 的优势

1. **自动故障转移**: 主 JobManager 故障时，standby 自动接管
2. **作业恢复**: 作业状态存储在 ZooKeeper 和 HA 存储中，可以从故障中恢复
3. **零停机维护**: 可以滚动升级 JobManager
4. **数据一致性**: 使用 ZooKeeper 保证分布式一致性

## 监控 HA 状态

### 1. 查看 leader 信息

```bash
docker exec zookeeper bash -c "echo 'get /flink/realtime-pipeline/leader/latch' | /usr/bin/zookeeper-shell localhost:2181"
```

### 2. 查看 JobManager 日志

```bash
# 主 JobManager
docker logs flink-jobmanager | grep -i "leader"

# Standby JobManager
docker logs flink-jobmanager-standby | grep -i "leader"
```

### 3. 检查 HA 存储

```bash
docker exec flink-jobmanager ls -la /opt/flink/ha/
```

## 常见问题

### Q: Leader 选举一直失败怎么办？

A: 清理 ZooKeeper 数据：
```bash
docker-compose stop jobmanager jobmanager-standby
docker exec zookeeper bash -c "echo 'deleteall /flink/realtime-pipeline' | /usr/bin/zookeeper-shell localhost:2181"
docker-compose start jobmanager
sleep 20
docker-compose start jobmanager-standby
```

### Q: 如何查看当前的 leader？

A: 
```bash
# 主 JobManager（端口 8081）
curl http://localhost:8081/overview

# Standby JobManager（端口 8082）
curl http://localhost:8082/overview

# 只有 leader 会返回正常的集群信息
```

### Q: 如何禁用 HA 模式？

A: 
```bash
docker-compose down
HA_MODE=NONE docker-compose up -d jobmanager taskmanager monitor-backend monitor-frontend
```

### Q: HA 存储占用太多空间怎么办？

A: 定期清理旧的 HA 数据：
```bash
docker exec flink-jobmanager find /opt/flink/ha -type f -mtime +7 -delete
```

## 生产环境建议

### 1. 使用外部 ZooKeeper 集群

生产环境应该使用独立的 ZooKeeper 集群（至少 3 个节点）：

```yaml
high-availability.zookeeper.quorum: zk1:2181,zk2:2181,zk3:2181
```

### 2. 使用可靠的 HA 存储

```yaml
# HDFS
high-availability.storageDir: hdfs://namenode:9000/flink/ha

# S3
high-availability.storageDir: s3://my-bucket/flink/ha

# MinIO
high-availability.storageDir: s3://my-bucket/flink/ha
s3.endpoint: http://minio:9000
```

### 3. 配置合适的超时时间

```yaml
# ZooKeeper 会话超时
high-availability.zookeeper.client.session-timeout: 60000

# ZooKeeper 连接超时
high-availability.zookeeper.client.connection-timeout: 15000
```

### 4. 监控和告警

- 监控 ZooKeeper 健康状态
- 监控 JobManager leader 变更
- 监控 HA 存储空间使用
- 设置 leader 选举失败告警

## 下一步

1. **提交作业**: 通过前端界面提交 CDC 任务
2. **测试故障转移**: 手动停止主 JobManager，验证 standby 接管
3. **监控运行**: 确保作业正常运行，checkpoint 正常创建
4. **配置告警**: 设置 HA 相关的监控告警

## 访问地址

- **主 JobManager Web UI**: http://localhost:8081
- **Standby JobManager Web UI**: http://localhost:8082
- **Monitor Frontend**: http://localhost:8888
- **Monitor Backend API**: http://localhost:5001

## 相关文档

- [CHECKPOINT-ISSUE-RESOLUTION.md](CHECKPOINT-ISSUE-RESOLUTION.md) - Checkpoint 问题解决
- [CHECKPOINT-RECOVERY-GUIDE.md](CHECKPOINT-RECOVERY-GUIDE.md) - Checkpoint 恢复指南
- [Flink HA 官方文档](https://nightlies.apache.org/flink/flink-docs-stable/docs/deployment/ha/overview/)
