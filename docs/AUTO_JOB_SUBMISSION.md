# Flink 自动作业提交功能

## 概述

实现了容器启动时自动提交 Flink 作业的功能，确保在容器重启或故障恢复后，作业能够自动运行，无需手动干预。

## 功能特性

### 1. 自动检测和提交
- JobManager 启动后自动检测是否有运行中的作业
- 如果没有运行中的作业，自动提交新作业
- 如果已有作业运行，跳过提交避免重复

### 2. 智能等待
- 等待 JobManager 完全就绪后再提交作业
- 可配置等待时间（默认60秒）
- 最多重试30次检查 JobManager 状态

### 3. 仅主节点提交
- 只在主 JobManager 上执行自动提交
- 备用 JobManager 不会提交作业
- 避免 HA 模式下的重复提交

### 4. 日志记录
- 记录所有提交尝试和结果
- 日志文件：`/opt/flink/logs/auto-submit.log`
- 包含时间戳和作业 ID

## 配置说明

### 环境变量

在 `.env` 文件中配置：

```bash
# 是否启用自动作业提交（true/false）
AUTO_SUBMIT_JOB=true

# 自动提交延迟时间（秒，等待 JobManager 完全启动）
AUTO_SUBMIT_DELAY=60

# 作业 JAR 文件路径
JOB_JAR_PATH=/opt/flink/usrlib/realtime-data-pipeline.jar

# 作业主类
JOB_MAIN_CLASS=com.realtime.pipeline.FlinkCDC3App
```

### 配置选项详解

#### AUTO_SUBMIT_JOB
- **类型**: Boolean (true/false)
- **默认值**: true
- **说明**: 是否启用自动作业提交功能
- **使用场景**:
  - `true`: 生产环境，需要自动恢复
  - `false`: 开发环境，需要手动控制

#### AUTO_SUBMIT_DELAY
- **类型**: Integer (秒)
- **默认值**: 60
- **说明**: JobManager 启动后等待多久再提交作业
- **建议值**:
  - 单机环境: 30-60秒
  - HA 环境: 60-90秒
  - 大规模集群: 90-120秒

#### JOB_JAR_PATH
- **类型**: String (文件路径)
- **默认值**: /opt/flink/usrlib/realtime-data-pipeline.jar
- **说明**: 作业 JAR 文件在容器内的路径
- **注意**: 确保文件存在且有读取权限

#### JOB_MAIN_CLASS
- **类型**: String (Java 类名)
- **默认值**: com.realtime.pipeline.FlinkCDC3App
- **说明**: 作业的主类（包含 main 方法）
- **格式**: 完整的类名（包名.类名）

## 工作流程

### 启动流程

```
容器启动
    ↓
JobManager 初始化
    ↓
启动 JobManager 进程（后台）
    ↓
启动自动提交脚本（后台）
    ↓
等待 AUTO_SUBMIT_DELAY 秒
    ↓
检查 JobManager 是否就绪
    ├─ 未就绪 → 重试（最多30次）
    └─ 就绪 → 继续
    ↓
检查是否已有运行中的作业
    ├─ 有 → 跳过提交
    └─ 无 → 继续
    ↓
检查 JAR 文件是否存在
    ├─ 不存在 → 报错退出
    └─ 存在 → 继续
    ↓
提交作业
    ├─ 成功 → 记录日志
    └─ 失败 → 记录错误
```

### 故障恢复流程

```
容器故障/重启
    ↓
容器自动重启（Docker restart policy）
    ↓
JobManager 启动
    ↓
自动提交脚本执行
    ↓
检查 HA 存储中的作业状态
    ├─ 有 checkpoint → 从 checkpoint 恢复
    └─ 无 checkpoint → 提交新作业
    ↓
作业运行
```

## 使用示例

### 场景 1: 首次部署

```bash
# 1. 配置环境变量
cat > .env << EOF
AUTO_SUBMIT_JOB=true
AUTO_SUBMIT_DELAY=60
JOB_JAR_PATH=/opt/flink/usrlib/realtime-data-pipeline.jar
JOB_MAIN_CLASS=com.realtime.pipeline.FlinkCDC3App
EOF

# 2. 启动服务
docker-compose up -d

# 3. 查看日志（等待60秒）
docker logs -f flink-jobmanager

# 4. 验证作业已提交
curl http://localhost:8081/jobs/overview | jq
```

### 场景 2: 容器重启

```bash
# 1. 重启 JobManager
docker restart flink-jobmanager

# 2. 查看自动提交日志
docker exec flink-jobmanager cat /opt/flink/logs/auto-submit.log

# 3. 验证作业状态
curl http://localhost:8081/jobs/overview | jq
```

### 场景 3: 禁用自动提交

```bash
# 1. 修改配置
echo "AUTO_SUBMIT_JOB=false" >> .env

# 2. 重启服务
docker-compose restart jobmanager

# 3. 手动提交作业
docker exec flink-jobmanager flink run -d \
    -c com.realtime.pipeline.FlinkCDC3App \
    /opt/flink/usrlib/realtime-data-pipeline.jar
```

### 场景 4: HA 模式下的自动提交

```bash
# 1. 启用 HA 和自动提交
cat > .env << EOF
HA_MODE=zookeeper
AUTO_SUBMIT_JOB=true
AUTO_SUBMIT_DELAY=90
EOF

# 2. 启动 HA 集群
docker-compose up -d

# 3. 验证只有主节点提交了作业
curl http://localhost:8081/jobs/overview | jq  # 主节点
curl http://localhost:8082/jobs/overview | jq  # 备用节点

# 4. 测试故障转移
docker stop flink-jobmanager
sleep 30
# 备用节点接管，作业继续运行（不会重复提交）
```

## 日志和监控

### 查看自动提交日志

```bash
# 查看完整日志
docker exec flink-jobmanager cat /opt/flink/logs/auto-submit.log

# 实时监控
docker exec flink-jobmanager tail -f /opt/flink/logs/auto-submit.log

# 查看最近的提交
docker exec flink-jobmanager tail -10 /opt/flink/logs/auto-submit.log
```

### 日志格式

```
2026-02-26 16:30:00 - Auto-submitted job: 7e913c364829dcade3593dfc9be439aa
2026-02-26 16:35:00 - Auto-submit failed: JAR file not found
2026-02-26 16:40:00 - Auto-submitted job: 3b2abe7b9373e82417fd7747984d8c74
```

### 监控指标

```bash
# 检查 JobManager 状态
curl http://localhost:8081/overview | jq

# 检查作业状态
curl http://localhost:8081/jobs/overview | jq

# 检查最近的 checkpoint
curl http://localhost:8081/jobs/<JOB_ID>/checkpoints | jq
```

## 故障排查

### 问题 1: 作业未自动提交

**症状**: 容器启动后没有作业运行

**排查步骤**:
```bash
# 1. 检查配置
docker exec flink-jobmanager env | grep AUTO_SUBMIT

# 2. 检查 JobManager 日志
docker logs flink-jobmanager | grep "Auto-Submit"

# 3. 检查 JAR 文件
docker exec flink-jobmanager ls -lh /opt/flink/usrlib/

# 4. 检查自动提交日志
docker exec flink-jobmanager cat /opt/flink/logs/auto-submit.log
```

**可能原因**:
- `AUTO_SUBMIT_JOB=false`
- JAR 文件不存在
- JobManager 未完全启动
- 主类名错误

### 问题 2: 重复提交作业

**症状**: 有多个相同的作业运行

**排查步骤**:
```bash
# 1. 检查运行中的作业
curl http://localhost:8081/jobs/overview | jq '.jobs[] | select(.state == "RUNNING")'

# 2. 检查是否在备用节点也提交了
docker logs flink-jobmanager-standby | grep "Auto-Submit"
```

**解决方案**:
- 确保只有主节点的 `JOB_MANAGER_RPC_ADDRESS=jobmanager`
- 备用节点应该使用不同的地址

### 问题 3: 提交失败

**症状**: 日志显示提交失败

**排查步骤**:
```bash
# 1. 查看详细错误
docker exec flink-jobmanager cat /opt/flink/logs/auto-submit.log

# 2. 手动尝试提交
docker exec flink-jobmanager flink run -d \
    -c com.realtime.pipeline.FlinkCDC3App \
    /opt/flink/usrlib/realtime-data-pipeline.jar

# 3. 检查 JobManager 资源
curl http://localhost:8081/overview | jq '.["slots-available"]'
```

**可能原因**:
- 资源不足（槽位不够）
- JAR 文件损坏
- 主类不存在
- 依赖缺失

### 问题 4: 延迟时间不够

**症状**: 提交时 JobManager 还未就绪

**解决方案**:
```bash
# 增加延迟时间
echo "AUTO_SUBMIT_DELAY=90" >> .env
docker-compose restart jobmanager
```

## 最佳实践

### 1. 生产环境配置

```bash
# 启用自动提交
AUTO_SUBMIT_JOB=true

# 较长的延迟时间（确保完全启动）
AUTO_SUBMIT_DELAY=90

# 使用绝对路径
JOB_JAR_PATH=/opt/flink/usrlib/realtime-data-pipeline.jar

# 完整的类名
JOB_MAIN_CLASS=com.realtime.pipeline.FlinkCDC3App
```

### 2. 开发环境配置

```bash
# 禁用自动提交（手动控制）
AUTO_SUBMIT_JOB=false

# 或使用较短的延迟
AUTO_SUBMIT_DELAY=30
```

### 3. HA 环境配置

```bash
# 启用 HA
HA_MODE=zookeeper

# 启用自动提交
AUTO_SUBMIT_JOB=true

# 较长的延迟（等待 Leader 选举）
AUTO_SUBMIT_DELAY=90
```

### 4. 监控和告警

```bash
# 定期检查作业状态
*/5 * * * * curl -s http://localhost:8081/jobs/overview | jq '.jobs[] | select(.state != "RUNNING")' && echo "Alert: Job not running"

# 监控自动提交日志
*/10 * * * * docker exec flink-jobmanager tail -1 /opt/flink/logs/auto-submit.log | grep "failed" && echo "Alert: Auto-submit failed"
```

### 5. 备份和恢复

```bash
# 定期备份 checkpoint
docker exec flink-jobmanager tar -czf /tmp/checkpoints-backup.tar.gz /opt/flink/checkpoints

# 从 savepoint 恢复
docker exec flink-jobmanager flink run -d \
    -s /opt/flink/savepoints/savepoint-xxx \
    -c com.realtime.pipeline.FlinkCDC3App \
    /opt/flink/usrlib/realtime-data-pipeline.jar
```

## 与其他功能的集成

### 与健康监控集成

自动提交功能与健康监控脚本配合使用：

```bash
# 1. 启动自动提交（容器启动时）
AUTO_SUBMIT_JOB=true

# 2. 启动健康监控（检测僵死作业）
./shell/production-health-monitor.sh

# 3. 完整的自动化流程
容器启动 → 自动提交作业 → 健康监控 → 检测问题 → 自动重启 → 自动提交作业
```

### 与 HA 故障转移集成

```
主节点故障
    ↓
备用节点接管（作业继续运行）
    ↓
主节点恢复
    ↓
主节点启动（检测到作业已运行）
    ↓
跳过自动提交（避免重复）
```

## 总结

自动作业提交功能提供了：

1. **自动化**: 容器启动时自动提交作业
2. **智能化**: 检测现有作业，避免重复提交
3. **可靠性**: 与 HA 机制配合，确保作业持续运行
4. **可配置**: 灵活的配置选项适应不同场景
5. **可监控**: 完整的日志记录便于排查问题

通过这个功能，系统可以实现真正的"一键启动，自动运行"，大大降低了运维成本。
