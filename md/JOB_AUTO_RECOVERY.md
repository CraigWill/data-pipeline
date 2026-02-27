# Flink 作业自动恢复配置

## 概述

本系统提供完整的 Flink 作业自动恢复功能，包括：

1. ✅ 作业失败自动检测
2. ✅ 从 Checkpoint 自动恢复
3. ✅ 智能重启策略
4. ✅ 作业状态监控
5. ✅ 告警通知
6. ✅ 自动提交作业

## 配置说明

### 1. Flink 重启策略

已在 `docker/jobmanager/flink-conf.yaml` 中配置指数退避重启策略：

```yaml
# 重启策略 - 指数退避
restart-strategy: exponential-delay
restart-strategy.exponential-delay.initial-backoff: 10s      # 初始退避时间
restart-strategy.exponential-delay.max-backoff: 2min         # 最大退避时间
restart-strategy.exponential-delay.backoff-multiplier: 2.0   # 退避倍数
restart-strategy.exponential-delay.reset-backoff-threshold: 10min  # 重置阈值
restart-strategy.exponential-delay.jitter-factor: 0.1        # 抖动因子

# 故障转移策略
jobmanager.execution.failover-strategy: region
```

**重启行为**:
- 第一次失败: 等待 10 秒后重启
- 第二次失败: 等待 20 秒后重启
- 第三次失败: 等待 40 秒后重启
- 后续失败: 最多等待 2 分钟
- 如果作业稳定运行 10 分钟，重置退避时间

### 2. Checkpoint 配置

```yaml
# Checkpoint 配置
execution.checkpointing.interval: 300000              # 5 分钟
execution.checkpointing.mode: EXACTLY_ONCE            # 精确一次语义
execution.checkpointing.timeout: 600000               # 10 分钟超时
execution.checkpointing.min-pause: 60000              # 最小间隔 1 分钟
execution.checkpointing.max-concurrent-checkpoints: 1 # 最多 1 个并发
execution.checkpointing.tolerable-failed-checkpoints: 3  # 容忍 3 次失败
```

### 3. HA 配置

```yaml
# 高可用配置
high-availability: zookeeper
high-availability.storageDir: file:///opt/flink/ha
high-availability.zookeeper.quorum: zookeeper:2181
high-availability.zookeeper.path.root: /flink
high-availability.cluster-id: /realtime-pipeline
```

## 使用方法

### 方法 1: 自动提交作业（推荐）

在 Flink 集群启动后自动提交作业：

```bash
# 启动 Flink 集群
docker-compose up -d

# 自动提交作业
./auto-submit-jobs.sh
```

脚本会：
1. 等待 Flink 集群就绪
2. 检查是否已有运行中的作业
3. 如果没有，自动提交 CDC 作业

### 方法 2: 启动监控服务

启动后台监控服务，自动检测和恢复失败的作业：

```bash
# 启动监控服务
./start-job-monitor.sh

# 查看监控日志
tail -f logs/job-monitor.log

# 停止监控服务
./stop-job-monitor.sh
```

**监控服务功能**:
- 每 30 秒检查一次作业状态
- 自动检测失败的作业
- 尝试从最后的 Checkpoint 恢复
- 如果没有 Checkpoint，重新提交作业
- 最多尝试 3 次恢复
- 发送告警通知

### 方法 3: 手动监控（一次性）

运行一次性监控检查：

```bash
# 直接运行监控脚本（前台）
./monitor-and-recover-jobs.sh
```

按 `Ctrl+C` 停止。

### 方法 4: 系统服务（生产环境）

将监控服务配置为系统服务：

```bash
# 1. 编辑服务文件
sudo nano flink-job-monitor.service

# 2. 修改路径和用户
#    - YOUR_USER: 你的用户名
#    - /path/to/data-pipeline: 项目路径

# 3. 复制到 systemd 目录
sudo cp flink-job-monitor.service /etc/systemd/system/

# 4. 重新加载 systemd
sudo systemctl daemon-reload

# 5. 启动服务
sudo systemctl start flink-job-monitor

# 6. 设置开机自启
sudo systemctl enable flink-job-monitor

# 7. 查看状态
sudo systemctl status flink-job-monitor

# 8. 查看日志
sudo journalctl -u flink-job-monitor -f
```

## 配置参数

### 环境变量

可以通过环境变量自定义监控行为：

```bash
# Flink REST API 地址
export FLINK_REST_URL="http://localhost:8081"

# 检查间隔（秒）
export CHECK_INTERVAL=30

# 最大恢复尝试次数
export MAX_RECOVERY_ATTEMPTS=3

# 恢复冷却时间（秒）
export RECOVERY_COOLDOWN=300

# 日志文件路径
export LOG_FILE="./logs/job-monitor.log"

# 运行监控
./monitor-and-recover-jobs.sh
```

### 修改配置文件

编辑 `monitor-and-recover-jobs.sh` 顶部的配置：

```bash
# 配置
FLINK_REST_URL="${FLINK_REST_URL:-http://localhost:8081}"
CHECK_INTERVAL="${CHECK_INTERVAL:-30}"
MAX_RECOVERY_ATTEMPTS="${MAX_RECOVERY_ATTEMPTS:-3}"
RECOVERY_COOLDOWN="${RECOVERY_COOLDOWN:-300}"
LOG_FILE="${LOG_FILE:-./logs/job-monitor.log}"
```

## 恢复流程

### 自动恢复流程

```
作业失败检测
    ↓
检查失败次数
    ↓
检查冷却时间
    ↓
尝试获取最后的 Checkpoint
    ↓
    ├─ 有 Checkpoint
    │   ↓
    │   取消当前作业
    │   ↓
    │   从 Checkpoint 恢复
    │   ↓
    │   验证恢复状态
    │
    └─ 无 Checkpoint
        ↓
        取消当前作业
        ↓
        重新提交作业（无状态）
        ↓
        验证提交状态
```

### 手动恢复

如果自动恢复失败，可以手动恢复：

```bash
# 1. 查看失败的作业
curl http://localhost:8081/jobs

# 2. 获取作业 ID
JOB_ID="your-job-id"

# 3. 查看 Checkpoint
curl http://localhost:8081/jobs/$JOB_ID/checkpoints

# 4. 取消作业
curl -X PATCH "http://localhost:8081/jobs/$JOB_ID?mode=cancel"

# 5. 从 Savepoint 恢复
docker exec flink-jobmanager flink run \
  -s file:///opt/flink/checkpoints/your-checkpoint-path \
  -d \
  -c com.realtime.pipeline.FlinkCDC3App \
  /opt/flink/usrlib/realtime-data-pipeline.jar
```

## 监控和告警

### 查看监控日志

```bash
# 实时查看日志
tail -f logs/job-monitor.log

# 查看告警日志
tail -f logs/job-monitor-alerts.log

# 搜索特定作业
grep "job-name" logs/job-monitor.log

# 查看恢复记录
grep "恢复" logs/job-monitor.log
```

### 集成告警系统

编辑 `monitor-and-recover-jobs.sh` 中的 `send_alert` 函数：

#### 钉钉告警

```bash
send_alert() {
    local level=$1
    local message=$2
    
    curl -X POST "https://oapi.dingtalk.com/robot/send?access_token=YOUR_TOKEN" \
      -H "Content-Type: application/json" \
      -d "{
        \"msgtype\": \"text\",
        \"text\": {
          \"content\": \"[Flink 作业告警]\\n级别: $level\\n消息: $message\"
        }
      }"
}
```

#### 邮件告警

```bash
send_alert() {
    local level=$1
    local message=$2
    
    echo "$message" | mail -s "[Flink Alert] $level" your-email@example.com
}
```

#### Slack 告警

```bash
send_alert() {
    local level=$1
    local message=$2
    
    curl -X POST "https://hooks.slack.com/services/YOUR/WEBHOOK/URL" \
      -H "Content-Type: application/json" \
      -d "{
        \"text\": \"[Flink Alert] $level: $message\"
      }"
}
```

## 测试自动恢复

### 测试场景 1: 模拟作业失败

```bash
# 1. 提交作业
./auto-submit-jobs.sh

# 2. 获取作业 ID
JOB_ID=$(curl -s http://localhost:8081/jobs | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

# 3. 取消作业（模拟失败）
curl -X PATCH "http://localhost:8081/jobs/$JOB_ID?mode=cancel"

# 4. 观察监控日志
tail -f logs/job-monitor.log

# 预期: 监控服务检测到作业失败并自动恢复
```

### 测试场景 2: 模拟 JobManager 故障

```bash
# 1. 启动监控服务
./start-job-monitor.sh

# 2. 提交作业
./auto-submit-jobs.sh

# 3. 重启 JobManager（模拟故障）
docker-compose restart jobmanager

# 4. 观察恢复过程
tail -f logs/job-monitor.log

# 预期: 
# - HA 模式下，作业从 Checkpoint 自动恢复
# - 监控服务检测到集群恢复并继续监控
```

### 测试场景 3: 验证 Checkpoint 恢复

```bash
# 1. 提交作业并等待 Checkpoint
./auto-submit-jobs.sh
sleep 360  # 等待至少一个 Checkpoint（5分钟）

# 2. 查看 Checkpoint
JOB_ID=$(curl -s http://localhost:8081/jobs | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
curl -s "http://localhost:8081/jobs/$JOB_ID/checkpoints" | grep -o '"external_path":"[^"]*"'

# 3. 取消作业
curl -X PATCH "http://localhost:8081/jobs/$JOB_ID?mode=cancel"

# 4. 观察自动恢复
tail -f logs/job-monitor.log

# 预期: 作业从最后的 Checkpoint 恢复，保留状态
```

## 故障排查

### 问题 1: 监控服务无法启动

**症状**: `./start-job-monitor.sh` 失败

**解决方案**:
```bash
# 检查 Flink 是否可访问
curl http://localhost:8081/overview

# 检查日志目录权限
ls -la logs/

# 手动运行监控脚本查看错误
./monitor-and-recover-jobs.sh
```

### 问题 2: 作业恢复失败

**症状**: 监控日志显示恢复失败

**解决方案**:
```bash
# 1. 检查 Checkpoint 目录
docker exec flink-jobmanager ls -la /opt/flink/checkpoints

# 2. 检查 HA 存储
docker exec flink-jobmanager ls -la /opt/flink/ha

# 3. 查看 JobManager 日志
docker-compose logs jobmanager | grep -i "checkpoint\|recovery"

# 4. 手动恢复作业
./auto-submit-jobs.sh
```

### 问题 3: 作业频繁失败

**症状**: 作业不断失败和重启

**解决方案**:
```bash
# 1. 查看作业异常
JOB_ID="your-job-id"
curl "http://localhost:8081/jobs/$JOB_ID/exceptions"

# 2. 检查数据库连接
./test-oracle-connection.sh

# 3. 检查资源使用
docker stats

# 4. 增加重启延迟
# 编辑 docker/jobmanager/flink-conf.yaml
# restart-strategy.exponential-delay.initial-backoff: 30s
```

## 性能优化

### 1. 调整 Checkpoint 间隔

根据数据量和性能要求调整：

```yaml
# 高吞吐量场景（减少 Checkpoint 频率）
execution.checkpointing.interval: 600000  # 10 分钟

# 低延迟场景（增加 Checkpoint 频率）
execution.checkpointing.interval: 60000   # 1 分钟
```

### 2. 优化监控间隔

```bash
# 生产环境（降低检查频率）
export CHECK_INTERVAL=60  # 1 分钟

# 开发环境（提高检查频率）
export CHECK_INTERVAL=10  # 10 秒
```

### 3. 调整恢复策略

```bash
# 快速恢复（减少尝试次数）
export MAX_RECOVERY_ATTEMPTS=2
export RECOVERY_COOLDOWN=60

# 稳定恢复（增加尝试次数）
export MAX_RECOVERY_ATTEMPTS=5
export RECOVERY_COOLDOWN=600
```

## 最佳实践

1. **生产环境部署**
   - 使用 systemd 服务管理监控进程
   - 配置告警通知（钉钉/邮件/Slack）
   - 定期检查监控日志
   - 设置日志轮转

2. **Checkpoint 管理**
   - 定期清理旧的 Checkpoint
   - 监控 Checkpoint 大小
   - 使用分布式存储（HDFS/S3）

3. **监控指标**
   - 作业失败率
   - 恢复成功率
   - 平均恢复时间
   - Checkpoint 成功率

4. **容量规划**
   - 预留足够的磁盘空间存储 Checkpoint
   - 确保 HA 存储有足够空间
   - 监控 Zookeeper 存储使用

## 相关脚本

| 脚本 | 用途 |
|------|------|
| `auto-submit-jobs.sh` | 自动提交作业 |
| `monitor-and-recover-jobs.sh` | 监控和恢复作业 |
| `start-job-monitor.sh` | 启动监控服务 |
| `stop-job-monitor.sh` | 停止监控服务 |
| `enable-flink-ha.sh` | 启用 HA 模式 |
| `check-flink-ha-status.sh` | 检查 HA 状态 |
| `test-flink-ha-failover.sh` | 测试故障转移 |

## 参考资料

- [Flink Restart Strategies](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/ops/state/task_failure_recovery/)
- [Flink Checkpointing](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/dev/datastream/fault-tolerance/checkpointing/)
- [Flink High Availability](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/deployment/ha/overview/)

## 总结

Flink 作业自动恢复系统已配置完成，提供：
- ✅ 智能重启策略（指数退避）
- ✅ 自动 Checkpoint 恢复
- ✅ 作业状态监控
- ✅ 自动故障恢复
- ✅ 告警通知支持
- ✅ 生产级可靠性

立即开始使用：
```bash
# 1. 启动 Flink 集群（HA 模式）
./enable-flink-ha.sh

# 2. 自动提交作业
./auto-submit-jobs.sh

# 3. 启动监控服务
./start-job-monitor.sh

# 4. 查看监控日志
tail -f logs/job-monitor.log
```
