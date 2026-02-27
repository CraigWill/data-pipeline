# Flink HA 故障转移问题与解决方案

## 问题描述

在 Flink HA 模式下，当主 JobManager 挂掉后，备用节点会接管作业。但是当主节点恢复后，可能出现以下问题：

1. **作业停止生成文件**：虽然作业显示 RUNNING，但不再处理数据
2. **Oracle 连接断开**：CDC Source 与 Oracle 的连接丢失
3. **双 Leader 问题**：两个 JobManager 可能产生冲突

## 根本原因

### 1. Oracle 连接超时

```
java.sql.SQLRecoverableException: No more data to read from socket
```

**原因**：
- LogMiner 会话长时间运行
- Oracle 网络超时配置
- TCP 连接被中断

### 2. JobManager 故障转移流程

```
主 JM 挂掉
    ↓
ZooKeeper 检测故障 (心跳超时)
    ↓
备用 JM 成为 Leader
    ↓
从 checkpoint 恢复作业
    ↓
主 JM 恢复
    ↓
主 JM 注册为 Standby
    ↓
作业继续在原 Leader 运行
```

**问题**：如果在故障转移期间 Oracle 连接断开，作业会停止处理数据。

### 3. FileSink 不生成文件

**原因**：
- CDC Source 没有捕获到新数据
- Checkpoint 完成但没有数据需要提交
- `.inprogress` 文件一直不会被提交

## 解决方案

### 方案 1：自动重启脚本（推荐）

使用提供的脚本自动检测和重启问题作业：

```bash
./shell/fix-ha-job-restart.sh
```

脚本功能：
- 自动检测当前 Leader
- 检查作业健康状态
- 必要时重启作业

### 方案 2：手动重启作业

```bash
# 1. 找到当前 Leader (哪个端口响应)
curl http://localhost:8081/overview  # 主节点
curl http://localhost:8082/overview  # 备用节点

# 2. 获取作业 ID
LEADER_PORT=8082  # 或 8081
curl -s http://localhost:$LEADER_PORT/jobs/overview | jq -r '.jobs[] | .jid'

# 3. 取消作业
JOB_ID="your-job-id"
curl -X PATCH "http://localhost:$LEADER_PORT/jobs/$JOB_ID?mode=cancel"

# 4. 重新提交作业
docker exec flink-jobmanager flink run -d -c com.realtime.pipeline.FlinkCDC3App /opt/flink/usrlib/realtime-data-pipeline.jar
```

### 方案 3：改进代码配置

修改 `FlinkCDC3App.java` 中的 Debezium 配置：

```java
// 增加连接超时和重试
debeziumProps.setProperty("database.connection.timeout.ms", "120000");  // 2分钟
debeziumProps.setProperty("database.query.timeout.ms", "1200000");  // 20分钟
debeziumProps.setProperty("errors.max.retries", "20");  // 增加重试次数
debeziumProps.setProperty("connect.keep.alive", "true");
debeziumProps.setProperty("connect.keep.alive.interval.ms", "30000");  // 30秒

// LogMiner 会话配置
debeziumProps.setProperty("log.mining.session.max.ms", "0");  // 无限制
debeziumProps.setProperty("log.mining.restart.on.error", "true");  // 错误时重启
```

### 方案 4：配置 Oracle 网络超时

在 Oracle 服务器上配置 `sqlnet.ora`：

```properties
# 增加超时时间
SQLNET.RECV_TIMEOUT=600
SQLNET.SEND_TIMEOUT=600
SQLNET.EXPIRE_TIME=10
```

## 预防措施

### 1. 监控作业健康状态

创建监控脚本定期检查：

```bash
#!/bin/bash
# 每分钟检查一次
while true; do
    # 检查是否有新文件生成
    LATEST_FILE=$(find output/cdc -type f -name "*.csv" -mmin -2 | wc -l)
    
    if [ $LATEST_FILE -eq 0 ]; then
        echo "警告：2分钟内没有新文件生成"
        # 发送告警或自动重启
    fi
    
    sleep 60
done
```

### 2. 配置作业重启策略

在 `flink-conf.yaml` 中：

```yaml
# 重启策略
restart-strategy: fixed-delay
restart-strategy.fixed-delay.attempts: 10
restart-strategy.fixed-delay.delay: 30s

# Checkpoint 配置
execution.checkpointing.interval: 30s
execution.checkpointing.timeout: 2min
execution.checkpointing.max-concurrent-checkpoints: 1
```

### 3. 使用 Savepoint 进行计划内重启

```bash
# 1. 创建 savepoint
docker exec flink-jobmanager flink savepoint $JOB_ID /opt/flink/savepoints

# 2. 取消作业
curl -X PATCH "http://localhost:8081/jobs/$JOB_ID?mode=cancel"

# 3. 从 savepoint 恢复
docker exec flink-jobmanager flink run -d \
    -s /opt/flink/savepoints/savepoint-xxx \
    -c com.realtime.pipeline.FlinkCDC3App \
    /opt/flink/usrlib/realtime-data-pipeline.jar
```

## 故障排查步骤

### 1. 检查作业状态

```bash
# 检查两个 JobManager
curl http://localhost:8081/jobs/overview | jq '.'
curl http://localhost:8082/jobs/overview | jq '.'
```

### 2. 检查 TaskManager 日志

```bash
docker logs realtime-pipeline-taskmanager-1 --tail 100 | grep -i error
```

### 3. 检查 Oracle 连接

```bash
docker exec oracle11g bash -c "lsof -i :1521"
```

### 4. 检查 ZooKeeper 状态

```bash
docker exec zookeeper zkServer.sh status
```

### 5. 检查文件生成

```bash
# 查看最近的文件
find output/cdc -type f -name "*.csv" -mmin -5

# 查看 inprogress 文件
find output/cdc -type f -name "*.inprogress*"
```

## 最佳实践

1. **定期健康检查**：每分钟检查作业是否正常处理数据
2. **自动重启**：检测到问题时自动重启作业
3. **告警通知**：集成告警系统（邮件、钉钉、Slack）
4. **日志监控**：使用 ELK 或 Prometheus 监控日志
5. **备份策略**：定期创建 savepoint

## 相关文档

- [Flink HA 部署指南](FLINK_HA_DEPLOYMENT_GUIDE.md)
- [Flink HA 快速开始](FLINK_HA_QUICKSTART.md)
- [CDC 实现文档](CDC_IMPLEMENTATION.md)
