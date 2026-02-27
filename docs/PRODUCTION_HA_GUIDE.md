# 生产级 Flink HA 部署指南

## 概述

本指南提供完整的生产级 Flink HA 部署方案，包括：
- 优化的连接配置
- 自动健康监控
- 故障自动恢复
- 监控和告警

## 架构改进

### 1. 连接稳定性优化

#### Oracle 连接配置
```java
// 生产级超时配置
database.connection.timeout.ms = 120000  // 2分钟
database.query.timeout.ms = 1800000      // 30分钟
connect.keep.alive = true
connect.keep.alive.interval.ms = 30000   // 30秒

// 增强重试机制
errors.max.retries = 20
errors.retry.delay.initial.ms = 2000
errors.retry.delay.max.ms = 120000
errors.tolerance = all

// Oracle 网络优化
database.tcpKeepAlive = true
oracle.net.keepalive_time = 30
```

#### LogMiner 优化
```java
// 批量处理优化
log.mining.batch.size.default = 2000
log.mining.batch.size.max = 20000

// 延迟优化
log.mining.sleep.time.default.ms = 500

// 故障恢复
log.mining.restart.connection = true
log.mining.transaction.retention.hours = 4
```

### 2. Checkpoint 和重启策略

#### Checkpoint 配置
```java
// 频繁 checkpoint（30秒）
env.enableCheckpointing(30000);

// 高级配置
env.getCheckpointConfig().setMinPauseBetweenCheckpoints(5000);
env.getCheckpointConfig().setCheckpointTimeout(180000);
env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
env.getCheckpointConfig().setTolerableCheckpointFailureNumber(5);

// 保留策略
env.getCheckpointConfig().setExternalizedCheckpointCleanup(
    ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION
);
```

#### 重启策略
```java
// 无限重试，固定延迟30秒
env.setRestartStrategy(
    RestartStrategies.fixedDelayRestart(
        Integer.MAX_VALUE,
        Time.seconds(30)
    )
);
```

### 3. 健康监控系统

#### 监控指标
- **文件生成频率**: 每5分钟检查一次
- **Checkpoint 状态**: 验证最新 checkpoint
- **作业指标**: 检查数据处理量
- **Oracle 连接**: 监控连接状态

#### 自动恢复
- **检测僵死作业**: 无文件生成 + Checkpoint 失败
- **自动重启**: 最多3次，间隔5分钟
- **告警通知**: 邮件 + Webhook

## 部署步骤

### 步骤 1: 编译优化后的代码

```bash
# 编译项目
mvn clean package -DskipTests

# 验证 JAR 文件
ls -lh target/realtime-data-pipeline-1.0.0-SNAPSHOT.jar
```

### 步骤 2: 配置环境变量

编辑 `.env` 文件：

```bash
# HA 配置
HA_MODE=zookeeper
HA_ZOOKEEPER_QUORUM=zookeeper:2181
HA_ZOOKEEPER_PATH_ROOT=/flink
HA_CLUSTER_ID=/realtime-pipeline
HA_STORAGE_DIR=file:///opt/flink/ha

# Checkpoint 配置（生产级）
CHECKPOINT_INTERVAL=30000
CHECKPOINT_TIMEOUT=180000
CHECKPOINT_MIN_PAUSE=5000
CHECKPOINT_MAX_CONCURRENT=1
CHECKPOINT_TOLERABLE_FAILURES=5

# 数据库配置
DATABASE_HOST=host.docker.internal
DATABASE_PORT=1521
DATABASE_USERNAME=system
DATABASE_PASSWORD=helowin
DATABASE_SID=helowin
DATABASE_SCHEMA=FINANCE_USER
DATABASE_TABLES=TRANS_INFO

# 输出配置
OUTPUT_PATH=./output/cdc
PARALLELISM_DEFAULT=4
```

### 步骤 3: 使用生产部署脚本

```bash
# 赋予执行权限
chmod +x shell/production-deploy.sh

# 执行部署
./shell/production-deploy.sh
```

部署脚本会自动：
1. 预检查环境
2. 停止旧服务
3. 清理和准备
4. 启动 HA 集群
5. 部署作业
6. 验证部署
7. 启动健康监控

### 步骤 4: 验证部署

```bash
# 检查容器状态
docker ps

# 检查作业状态
curl http://localhost:8081/jobs/overview | jq

# 检查 HA 状态
curl http://localhost:8082/overview | jq

# 查看监控日志
tail -f logs/health-monitor.log
```

## 监控和告警

### 健康监控脚本

`shell/production-health-monitor.sh` 提供：

#### 监控功能
- 每60秒检查一次
- 监控文件生成（5分钟窗口）
- 检查 Checkpoint 状态
- 验证作业指标
- 检测 Leader 状态

#### 自动恢复
- 检测僵死作业
- 自动重启（最多3次）
- 重启冷却时间（5分钟）
- 失败告警通知

#### 配置告警

编辑 `shell/production-health-monitor.sh`：

```bash
# 邮件告警
ALERT_EMAIL="your-email@example.com"

# Webhook 告警（钉钉）
ALERT_WEBHOOK="https://oapi.dingtalk.com/robot/send?access_token=YOUR_TOKEN"

# Webhook 告警（Slack）
ALERT_WEBHOOK="https://hooks.slack.com/services/YOUR/WEBHOOK/URL"
```

### 启动监控

```bash
# 后台启动
nohup ./shell/production-health-monitor.sh > logs/health-monitor.out 2>&1 &

# 查看日志
tail -f logs/health-monitor.log

# 停止监控
pkill -f production-health-monitor.sh
```

## 故障转移测试

### 测试脚本

```bash
# 简化测试
./shell/test-ha-simple.sh

# 完整测试
./shell/test-ha-failover-complete.sh
```

### 手动测试步骤

#### 1. 模拟主节点故障

```bash
# 停止主节点
docker stop flink-jobmanager

# 等待15秒
sleep 15

# 验证备用节点接管
curl http://localhost:8082/jobs/overview | jq
```

#### 2. 测试数据捕获

```sql
-- 在 Oracle 中执行
UPDATE FINANCE_USER.TRANS_INFO 
SET AMOUNT = AMOUNT + 1 
WHERE ROWNUM <= 18;
COMMIT;
```

```bash
# 等待35秒
sleep 35

# 检查文件生成
find output/cdc -name "*.csv" -mmin -2
```

#### 3. 恢复主节点

```bash
# 启动主节点
docker start flink-jobmanager

# 等待20秒
sleep 20

# 验证作业继续运行
curl http://localhost:8081/jobs/overview | jq
curl http://localhost:8082/jobs/overview | jq
```

#### 4. 再次测试数据捕获

```sql
-- 再次在 Oracle 中执行
UPDATE FINANCE_USER.TRANS_INFO 
SET AMOUNT = AMOUNT + 1 
WHERE ROWNUM <= 18;
COMMIT;
```

```bash
# 检查文件生成
find output/cdc -name "*.csv" -mmin -2
```

## 性能优化

### 1. 并行度调整

```bash
# 根据 CPU 核心数调整
PARALLELISM_DEFAULT=8  # 推荐：CPU 核心数 * 2
```

### 2. Checkpoint 间隔

```bash
# 根据数据量调整
CHECKPOINT_INTERVAL=30000   # 高吞吐量：30秒
CHECKPOINT_INTERVAL=60000   # 中等吞吐量：60秒
CHECKPOINT_INTERVAL=300000  # 低吞吐量：5分钟
```

### 3. LogMiner 批量大小

```java
// 高吞吐量
log.mining.batch.size.default = 5000
log.mining.batch.size.max = 50000

// 低延迟
log.mining.batch.size.default = 1000
log.mining.sleep.time.default.ms = 100
```

### 4. 文件滚动策略

```java
// 高吞吐量：更大的文件
.withRollingPolicy(
    DefaultRollingPolicy.builder()
        .withRolloverInterval(Duration.ofMinutes(5))
        .withMaxPartSize(MemorySize.ofMebiBytes(100))
        .build()
)

// 低延迟：更小的文件
.withRollingPolicy(
    DefaultRollingPolicy.builder()
        .withRolloverInterval(Duration.ofSeconds(30))
        .withMaxPartSize(MemorySize.ofMebiBytes(20))
        .build()
)
```

## 故障排查

### 常见问题

#### 1. 作业显示 RUNNING 但不生成文件

**原因**: Oracle 连接断开或作业僵死

**解决**:
```bash
# 检查 TaskManager 日志
docker logs realtime-pipeline-taskmanager-1 --tail 100 | grep -i error

# 重启作业
./shell/fix-ha-job-restart.sh
```

#### 2. Checkpoint 失败

**原因**: 存储空间不足或超时

**解决**:
```bash
# 检查磁盘空间
df -h

# 增加 checkpoint 超时
CHECKPOINT_TIMEOUT=300000  # 5分钟

# 清理旧 checkpoint
docker volume rm flink-checkpoints
```

#### 3. 主节点恢复后作业取消

**原因**: Leader 选举冲突

**解决**:
```bash
# 确保 HA 配置一致
# 检查两个 JobManager 的配置
docker exec flink-jobmanager cat /opt/flink/conf/flink-conf.yaml | grep high-availability
docker exec flink-jobmanager-standby cat /opt/flink/conf/flink-conf.yaml | grep high-availability
```

#### 4. Oracle 连接超时

**原因**: 网络配置或 Oracle 超时设置

**解决**:
```bash
# 检查 Oracle 连接
docker exec oracle11g bash -c "lsof -i :1521"

# 配置 Oracle sqlnet.ora
SQLNET.RECV_TIMEOUT=600
SQLNET.SEND_TIMEOUT=600
SQLNET.EXPIRE_TIME=10
```

### 日志位置

```bash
# JobManager 日志
docker logs flink-jobmanager
docker logs flink-jobmanager-standby

# TaskManager 日志
docker logs realtime-pipeline-taskmanager-1
docker logs realtime-pipeline-taskmanager-2
docker logs realtime-pipeline-taskmanager-3

# 健康监控日志
tail -f logs/health-monitor.log

# 应用日志
tail -f logs/pipeline.log
```

## 生产清单

### 部署前检查

- [ ] 代码已编译并测试
- [ ] 环境变量已配置
- [ ] 存储空间充足（至少100GB）
- [ ] 网络连接稳定
- [ ] Oracle 数据库已配置 ARCHIVELOG
- [ ] 告警系统已配置

### 部署后验证

- [ ] 两个 JobManager 都健康
- [ ] 3个 TaskManager 都健康
- [ ] 作业状态为 RUNNING
- [ ] Checkpoint 正常执行
- [ ] 文件正常生成
- [ ] 健康监控已启动
- [ ] 告警通知正常

### 故障转移测试

- [ ] 主节点故障测试通过
- [ ] 备用节点接管测试通过
- [ ] 主节点恢复测试通过
- [ ] 数据不丢失验证通过
- [ ] 文件持续生成验证通过

## 维护操作

### 日常维护

```bash
# 检查集群状态
docker ps
curl http://localhost:8081/overview | jq

# 检查作业状态
curl http://localhost:8081/jobs/overview | jq

# 检查磁盘空间
df -h

# 查看监控日志
tail -f logs/health-monitor.log
```

### 定期维护

```bash
# 每周：清理旧日志
find logs -name "*.log" -mtime +7 -delete

# 每月：创建 savepoint
docker exec flink-jobmanager flink savepoint <JOB_ID> /opt/flink/savepoints

# 每季度：更新依赖
mvn versions:display-dependency-updates
```

### 升级流程

```bash
# 1. 创建 savepoint
docker exec flink-jobmanager flink savepoint <JOB_ID> /opt/flink/savepoints

# 2. 停止作业
curl -X PATCH 'http://localhost:8081/jobs/<JOB_ID>?mode=cancel'

# 3. 更新代码并编译
git pull
mvn clean package -DskipTests

# 4. 重新部署
./shell/production-deploy.sh

# 5. 从 savepoint 恢复（可选）
docker exec flink-jobmanager flink run -d \
    -s /opt/flink/savepoints/savepoint-xxx \
    -c com.realtime.pipeline.FlinkCDC3App \
    /opt/flink/usrlib/realtime-data-pipeline.jar
```

## 性能指标

### 目标 SLA

- **可用性**: 99.9% (每月停机时间 < 43分钟)
- **故障转移时间**: < 30秒
- **数据延迟**: < 1分钟
- **Checkpoint 成功率**: > 99%
- **文件生成延迟**: < 2分钟

### 监控指标

- 作业运行时间
- Checkpoint 间隔和持续时间
- 数据处理速率（records/sec）
- 文件生成频率
- 错误率和重试次数
- 资源使用率（CPU、内存、磁盘）

## 总结

生产级 HA 部署包含：

1. **优化的连接配置**: 提高稳定性和容错能力
2. **自动健康监控**: 实时检测问题
3. **自动故障恢复**: 减少人工介入
4. **完整的监控告警**: 及时发现和处理问题
5. **详细的文档和脚本**: 简化运维操作

通过这些改进，系统可以达到生产级的可靠性和可用性。
