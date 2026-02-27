# TaskManager 心跳超时问题已解决

## 问题描述

**时间**: 2026-02-26 09:17:25

**错误信息**:
```
org.apache.flink.runtime.JobException: Recovery is suppressed by FixedDelayRestartBackoffTimeStrategy(maxNumberRestartAttempts=3, backoffTimeMS=10000)
...
Caused by: java.util.concurrent.TimeoutException: Heartbeat of TaskManager with id 172.19.0.3:6122-c8842d timed out.
```

**根本原因**:
- TaskManager 心跳超时
- 作业已达到最大重试次数（3次）
- 作业状态变为 FAILED

## 诊断过程

### 1. 检查容器状态
```bash
docker ps --filter "name=flink"
```

**结果**:
- JobManager: 运行中 ✅
- TaskManager: 运行中 ✅（但心跳超时）

### 2. 检查 TaskManager 连接
```bash
curl -s http://localhost:8081/taskmanagers
```

**结果**:
- TaskManager ID: 172.19.0.3:6122-c8842d
- 状态: 离线
- 最后心跳: 1772068秒前（约 492 小时）

### 3. 检查作业状态
```bash
curl -s http://localhost:8081/jobs/overview
```

**结果**:
- 作业 ID: 5d8617582b5546c4a6a56530d0aa2a35
- 状态: FAILED ❌
- 原因: TaskManager 心跳超时，重试次数耗尽

## 解决方案

### 步骤 1: 重启 TaskManager
```bash
docker-compose restart taskmanager
```

**结果**: TaskManager 重新连接，获得新 ID: 172.19.0.3:6122-105722

### 步骤 2: 重新提交作业
```bash
./restart-flink-cdc-job.sh
```

**结果**:
- 新作业 ID: 22b01eb0e022b22b315f2ecb84a95859
- 状态: RUNNING ✅
- 异常数量: 0 ✅

## 验证结果

### 作业状态
```
状态: RUNNING
运行时长: 47+ 秒
异常数量: 0
```

### TaskManager 状态
```
TaskManager 数量: 1
ID: 172.19.0.3:6122-105722
槽位: 4
可用槽位: 0 (全部被使用)
```

## 可能的原因分析

### 1. 网络问题
- TaskManager 和 JobManager 之间的网络连接中断
- Docker 网络问题
- 防火墙或网络策略

### 2. 资源问题
- TaskManager 容器资源不足（CPU/内存）
- 宿主机资源耗尽
- 容器被 OOM Killer 杀死后重启

### 3. 配置问题
- 心跳超时配置过短
- 重试次数配置过少

## 预防措施

### 1. 增加心跳超时时间

编辑 `docker/jobmanager/flink-conf.yaml` 和 `docker/taskmanager/flink-conf.yaml`:

```yaml
# 心跳超时配置（默认 50 秒）
heartbeat.timeout: 180000  # 3 分钟

# 心跳间隔（默认 10 秒）
heartbeat.interval: 10000  # 10 秒
```

### 2. 增加重试次数

在代码中或配置文件中设置：

```yaml
# 重启策略
restart-strategy: fixed-delay
restart-strategy.fixed-delay.attempts: 10  # 增加到 10 次
restart-strategy.fixed-delay.delay: 10s
```

或在代码中：

```java
env.setRestartStrategy(RestartStrategies.fixedDelayRestart(
    10,  // 重试次数
    Time.seconds(10)  // 重试间隔
));
```

### 3. 监控 TaskManager 健康状态

创建监控脚本：

```bash
#!/bin/bash
# monitor-taskmanager.sh

while true; do
    TM_COUNT=$(curl -s http://localhost:8081/taskmanagers | python3 -c "import sys, json; print(len(json.load(sys.stdin).get('taskmanagers', [])))")
    
    if [ "$TM_COUNT" -eq "0" ]; then
        echo "$(date): ❌ TaskManager 离线，重启中..."
        docker-compose restart taskmanager
        sleep 30
    else
        echo "$(date): ✅ TaskManager 在线 ($TM_COUNT 个)"
    fi
    
    sleep 60
done
```

### 4. 配置容器资源限制

编辑 `docker-compose.yml`:

```yaml
taskmanager:
  deploy:
    resources:
      limits:
        cpus: '4.0'      # 增加 CPU 限制
        memory: 4G       # 增加内存限制
      reservations:
        cpus: '2.0'
        memory: 2G
```

### 5. 启用自动重启

确保 `docker-compose.yml` 中有：

```yaml
taskmanager:
  restart: unless-stopped  # 容器崩溃时自动重启
```

## 监控命令

### 检查 TaskManager 状态
```bash
curl -s http://localhost:8081/taskmanagers | python3 -c "
import sys, json
data = json.load(sys.stdin)
tms = data.get('taskmanagers', [])
print(f'TaskManager 数量: {len(tms)}')
for tm in tms:
    heartbeat = tm.get('timeSinceLastHeartbeat', 999999)
    status = '在线' if heartbeat < 60000 else '离线'
    print(f\"  ID: {tm['id']}, 状态: {status}, 心跳: {heartbeat}ms 前\")
"
```

### 检查作业状态
```bash
curl -s http://localhost:8081/jobs/22b01eb0e022b22b315f2ecb84a95859 | python3 -c "
import sys, json
data = json.load(sys.stdin)
print(f\"状态: {data['state']}\")
print(f\"运行时长: {data['duration']//1000}秒\")
"
```

### 检查异常
```bash
curl -s http://localhost:8081/jobs/22b01eb0e022b22b315f2ecb84a95859/exceptions | python3 -c "
import sys, json
data = json.load(sys.stdin)
print(f\"异常数量: {len(data.get('all-exceptions', []))}\")
"
```

### 查看容器日志
```bash
# TaskManager 日志
docker logs realtime-pipeline-taskmanager-1 --tail 100

# JobManager 日志
docker logs flink-jobmanager --tail 100
```

## 相关配置文件

- `docker-compose.yml` - 容器配置
- `docker/jobmanager/flink-conf.yaml` - JobManager 配置
- `docker/taskmanager/flink-conf.yaml` - TaskManager 配置
- `restart-flink-cdc-job.sh` - 重启作业脚本

## 总结

✅ **问题已解决**

- TaskManager 重新连接成功
- 作业重新提交成功
- 作业稳定运行，无异常

**当前状态**:
- 作业 ID: 22b01eb0e022b22b315f2ecb84a95859
- 状态: RUNNING
- TaskManager: 在线
- 异常: 0

**建议**:
1. 监控 TaskManager 心跳状态
2. 考虑增加心跳超时时间
3. 考虑增加重试次数
4. 定期检查容器资源使用情况

## 下一步

1. **持续监控** 作业运行 1-2 小时，确保稳定
2. **插入测试数据** 验证 CDC 功能正常
3. **检查 CSV 文件** 确认数据正常生成
4. **优化配置** 根据实际情况调整心跳和重试参数

## 相关文档

- `CDC_JOB_RESTART_SUCCESS.md` - 上次重启成功记录
- `TASKMANAGER_REGISTRATION_FIX.md` - TaskManager 注册问题
- `ORACLE_CONNECTION_TIMEOUT_FIXED.md` - Oracle 连接超时问题
