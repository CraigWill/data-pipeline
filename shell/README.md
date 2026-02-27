# Shell 脚本说明

本目录包含用于管理和操作 Flink CDC 实时数据管道的 Shell 脚本。

## 脚本分类

### 1. 部署和启动

#### deploy-flink-ha.sh
部署 Flink 高可用集群（完整部署流程）

```bash
./shell/deploy-flink-ha.sh
```

功能：
- 停止现有服务
- 构建 Docker 镜像
- 启动 ZooKeeper
- 启动 JobManager（主 + 备）
- 启动 TaskManager
- 自动提交作业

#### quick-start-ha.sh
快速启动 HA 集群（不重新构建镜像）

```bash
./shell/quick-start-ha.sh
```

适用场景：镜像已构建，只需启动服务

#### production-deploy.sh
生产环境一键部署脚本

```bash
./shell/production-deploy.sh
```

功能：
- 完整的预检查（Docker、网络、端口、磁盘空间）
- 构建和部署
- 自动验证
- 健康检查

---

### 2. 监控和健康检查

#### production-health-monitor.sh
生产环境健康监控和自动恢复

```bash
./shell/production-health-monitor.sh
```

功能：
- 检测僵死作业（无数据输出超过 5 分钟）
- 自动重启僵死作业
- 告警通知（可配置）
- 持续监控（每 60 秒检查一次）

#### monitor-ha-cluster.sh
监控 HA 集群状态

```bash
./shell/monitor-ha-cluster.sh
```

显示：
- ZooKeeper 状态
- JobManager 状态（主 + 备）
- TaskManager 状态
- 作业状态
- 集群资源使用情况

#### check-flink-ha-status.sh
检查 Flink HA 配置和状态

```bash
./shell/check-flink-ha-status.sh
```

#### check-flink-cdc3-status.sh
检查 Flink CDC 3.x 作业状态

```bash
./shell/check-flink-cdc3-status.sh
```

---

### 3. 作业管理

#### restart-flink-cdc-job.sh
重启 Flink CDC 作业

```bash
./shell/restart-flink-cdc-job.sh [JOB_ID]
```

功能：
- 取消当前作业
- 等待作业完全停止
- 重新提交作业
- 验证新作业状态

#### start-flink-cdc.sh
启动 Flink CDC 作业（手动提交）

```bash
./shell/start-flink-cdc.sh
```

注意：生产环境建议使用自动提交功能（AUTO_SUBMIT_JOB=true）

---

### 4. 测试脚本

#### test-18-records.sh
测试 18 条记录的插入和 CDC 捕获

```bash
./shell/test-18-records.sh
```

功能：
- 检查作业状态
- 插入 18 条测试记录
- 等待 CDC 捕获
- 验证输出文件
- 显示捕获结果

#### test-auto-submit.sh
测试自动作业提交功能

```bash
./shell/test-auto-submit.sh
```

功能：
- 停止现有服务
- 启动 JobManager（启用自动提交）
- 监控自动提交过程
- 验证作业状态
- 检查自动提交日志

#### test-ha-failover.sh
测试 HA 故障转移（简化版）

```bash
./shell/test-ha-failover.sh
```

功能：
- 停止主 JobManager
- 验证备用节点接管
- 恢复主节点
- 检查作业状态

#### test-ha-failover-complete.sh
完整的 HA 故障转移测试

```bash
./shell/test-ha-failover-complete.sh
```

功能：
- 完整的故障转移流程测试
- 数据完整性验证
- 详细的测试报告

#### quick-test-cdc.sh
快速测试 CDC 功能

```bash
./shell/quick-test-cdc.sh
```

#### insert-test-trans.sh
插入测试交易数据

```bash
./shell/insert-test-trans.sh [COUNT]
```

#### quick-insert-test.sh
快速插入测试数据

```bash
./shell/quick-insert-test.sh
```

---

### 5. Oracle 配置

#### check-and-enable-archivelog.sh
检查并启用 Oracle ARCHIVELOG 模式

```bash
./shell/check-and-enable-archivelog.sh
```

功能：
- 检查当前 ARCHIVELOG 状态
- 如果未启用，提供启用步骤
- 验证配置

#### check-archivelog-docker.sh
在 Docker 容器中检查 ARCHIVELOG 状态

```bash
./shell/check-archivelog-docker.sh
```

---

## 常用操作流程

### 首次部署

```bash
# 1. 部署 HA 集群
./shell/production-deploy.sh

# 2. 验证部署
./shell/check-flink-ha-status.sh

# 3. 启动健康监控
./shell/production-health-monitor.sh &
```

### 日常运维

```bash
# 检查集群状态
./shell/monitor-ha-cluster.sh

# 检查作业状态
./shell/check-flink-cdc3-status.sh

# 重启作业（如需要）
./shell/restart-flink-cdc-job.sh
```

### 测试验证

```bash
# 测试数据捕获
./shell/test-18-records.sh

# 测试 HA 故障转移
./shell/test-ha-failover.sh

# 测试自动提交
./shell/test-auto-submit.sh
```

### 故障恢复

```bash
# 1. 检查集群状态
./shell/monitor-ha-cluster.sh

# 2. 重启作业
./shell/restart-flink-cdc-job.sh

# 3. 如果需要，重新部署
./shell/deploy-flink-ha.sh
```

---

## 环境变量

大多数脚本使用 `.env` 文件中的配置。关键变量：

```bash
# HA 配置
HA_MODE=zookeeper
HA_ZOOKEEPER_QUORUM=zookeeper:2181

# 自动提交配置
AUTO_SUBMIT_JOB=true
AUTO_SUBMIT_DELAY=60

# 数据库配置
DATABASE_HOST=host.docker.internal
DATABASE_PORT=1521
DATABASE_SID=helowin
DATABASE_SCHEMA=FINANCE_USER
DATABASE_TABLES=TRANS_INFO
```

---

## 脚本开发规范

1. 使用 `set -e` 确保错误时退出
2. 提供清晰的输出信息（使用颜色区分）
3. 包含使用说明和示例
4. 验证前置条件（Docker、网络、端口等）
5. 提供错误处理和回滚机制
6. 记录日志到文件（如需要）

---

## 故障排查

### 作业无法启动

```bash
# 检查 JobManager 日志
docker logs flink-jobmanager

# 检查自动提交日志
docker exec flink-jobmanager cat /opt/flink/logs/auto-submit.log

# 检查作业状态
curl http://localhost:8081/jobs/overview | jq
```

### 数据未捕获

```bash
# 检查作业是否运行
./shell/check-flink-cdc3-status.sh

# 检查 Oracle 连接
docker exec oracle11g bash -c 'source /home/oracle/.bash_profile && sqlplus system/helowin@helowin'

# 检查 ARCHIVELOG 模式
./shell/check-archivelog-docker.sh
```

### HA 故障转移失败

```bash
# 检查 ZooKeeper 状态
docker logs zookeeper

# 检查 HA 配置
./shell/check-flink-ha-status.sh

# 查看 JobManager 日志
docker logs flink-jobmanager
docker logs flink-jobmanager-standby
```

---

## 相关文档

- `docs/PRODUCTION_HA_GUIDE.md` - 生产环境 HA 指南
- `docs/AUTO_JOB_SUBMISSION.md` - 自动作业提交文档
- `docs/FLINK_HA_DEPLOYMENT_GUIDE.md` - HA 部署指南
- `docs/SCRIPT_CLEANUP_SUMMARY.md` - 脚本清理总结

---

## 更新历史

- 2026-02-26: 清理无用脚本，保留 18 个核心脚本
- 2026-02-26: 添加自动提交测试脚本
- 2026-02-26: 添加 18 条记录测试脚本
- 2026-02-26: 添加生产环境部署和监控脚本
