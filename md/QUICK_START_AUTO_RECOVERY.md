# Flink 作业自动恢复 - 快速启动指南

## 5 分钟快速开始

### 步骤 1: 启动 Flink 集群（HA 模式）

```bash
# 启用 HA 模式并启动集群
./enable-flink-ha.sh
```

等待集群启动完成（约 30 秒）。

### 步骤 2: 自动提交 CDC 作业

```bash
# 自动提交作业
./auto-submit-jobs.sh
```

脚本会自动：
- 等待 Flink 集群就绪
- 检查是否已有作业运行
- 提交 CDC 作业

### 步骤 3: 启动监控服务

```bash
# 启动后台监控服务
./start-job-monitor.sh

# 查看监控日志
tail -f logs/job-monitor.log
```

监控服务会：
- 每 30 秒检查作业状态
- 自动恢复失败的作业
- 记录所有操作日志

### 步骤 4: 验证运行状态

```bash
# 检查 Flink Web UI
open http://localhost:8081

# 检查作业状态
curl -s http://localhost:8081/jobs | grep -o '"state":"[^"]*"'

# 检查输出文件
ls -lh output/cdc/
```

## 测试自动恢复

### 测试 1: 模拟作业失败

```bash
# 1. 获取作业 ID
JOB_ID=$(curl -s http://localhost:8081/jobs | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

# 2. 取消作业（模拟失败）
curl -X PATCH "http://localhost:8081/jobs/$JOB_ID?mode=cancel"

# 3. 观察自动恢复
tail -f logs/job-monitor.log
```

预期结果：监控服务检测到失败并在 30 秒内自动恢复作业。

### 测试 2: 模拟 JobManager 故障

```bash
# 1. 重启 JobManager
docker-compose restart jobmanager

# 2. 观察恢复过程
tail -f logs/job-monitor.log
```

预期结果：HA 模式下，作业从 Checkpoint 自动恢复。

## 常用命令

```bash
# 查看所有作业
curl http://localhost:8081/jobs

# 查看作业详情
curl http://localhost:8081/jobs/<JOB_ID>

# 查看 Checkpoint
curl http://localhost:8081/jobs/<JOB_ID>/checkpoints

# 停止监控服务
./stop-job-monitor.sh

# 重启监控服务
./stop-job-monitor.sh && ./start-job-monitor.sh

# 查看 HA 状态
./check-flink-ha-status.sh
```

## 配置调整

### 修改监控间隔

编辑 `monitor-and-recover-jobs.sh`:

```bash
CHECK_INTERVAL="${CHECK_INTERVAL:-30}"  # 改为你想要的秒数
```

### 修改最大恢复次数

```bash
MAX_RECOVERY_ATTEMPTS="${MAX_RECOVERY_ATTEMPTS:-3}"  # 改为你想要的次数
```

### 修改 Checkpoint 间隔

编辑 `docker/jobmanager/flink-conf.yaml`:

```yaml
execution.checkpointing.interval: 300000  # 改为你想要的毫秒数
```

然后重启 JobManager:

```bash
docker-compose restart jobmanager
```

## 故障排查

### 问题: 监控服务无法启动

```bash
# 检查 Flink 是否可访问
curl http://localhost:8081/overview

# 手动运行查看错误
./monitor-and-recover-jobs.sh
```

### 问题: 作业恢复失败

```bash
# 查看 JobManager 日志
docker-compose logs jobmanager | tail -50

# 查看 Checkpoint 目录
docker exec flink-jobmanager ls -la /opt/flink/checkpoints

# 手动重新提交
./auto-submit-jobs.sh
```

### 问题: 作业频繁失败

```bash
# 查看作业异常
curl http://localhost:8081/jobs/<JOB_ID>/exceptions

# 检查数据库连接
./test-oracle-connection.sh

# 检查资源使用
docker stats
```

## 停止所有服务

```bash
# 停止监控服务
./stop-job-monitor.sh

# 停止 Flink 集群
docker-compose down
```

## 下一步

- 阅读完整文档: `JOB_AUTO_RECOVERY.md`
- 配置告警通知
- 部署到生产环境
- 设置系统服务

## 帮助

如有问题，请查看：
- 监控日志: `logs/job-monitor.log`
- 告警日志: `logs/job-monitor-alerts.log`
- Flink 日志: `docker-compose logs jobmanager`
