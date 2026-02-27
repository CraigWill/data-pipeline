# Flink 高可用（HA）快速开始

**5 分钟快速部署 Flink HA 集群**

## 前置条件

- Docker 和 Docker Compose 已安装
- 至少 8GB 可用内存
- 端口 2181, 8081, 8082 未被占用

## 快速部署（3 步）

### 步骤 1: 一键部署

```bash
./shell/deploy-flink-ha.sh
```

这个脚本会自动：
- ✅ 检查前置条件
- ✅ 停止现有服务
- ✅ 启动 ZooKeeper
- ✅ 启动 2 个 JobManager（主 + 备）
- ✅ 启动 3 个 TaskManager
- ✅ 验证集群状态

### 步骤 2: 验证部署

```bash
./shell/monitor-ha-cluster.sh
```

预期输出：
```
=== Flink HA 集群监控 ===

1. 容器状态:
   zookeeper                         Up (healthy)
   flink-jobmanager                  Up (healthy)
   flink-jobmanager-standby          Up (healthy)
   realtime-pipeline-taskmanager-1   Up (healthy)
   realtime-pipeline-taskmanager-2   Up (healthy)
   realtime-pipeline-taskmanager-3   Up (healthy)

2. JobManager Leader:
   Leader: JobManager 主节点 (端口 8081)

3. 集群资源:
   TaskManagers: 3
   总槽位: 12
   可用槽位: 12
```

### 步骤 3: 访问 Web UI

打开浏览器访问：
- 主 JobManager: http://localhost:8081
- 备 JobManager: http://localhost:8082

## 测试故障转移

```bash
./shell/test-ha-failover.sh
```

这个脚本会自动：
1. 检查当前 Leader
2. 停止当前 Leader
3. 等待故障转移（30秒）
4. 验证新 Leader
5. 检查作业状态
6. 可选：恢复原 Leader

## 日常使用

### 启动集群

```bash
./shell/quick-start-ha.sh
```

### 停止集群

```bash
docker-compose down
```

### 监控集群

```bash
./shell/monitor-ha-cluster.sh
```

### 查看日志

```bash
# 查看主 JobManager
docker-compose logs -f jobmanager

# 查看备 JobManager
docker-compose logs -f jobmanager-standby

# 查看所有 TaskManager
docker-compose logs -f taskmanager
```

### 扩展 TaskManager

```bash
# 扩展到 5 个
docker-compose up -d --scale taskmanager=5

# 缩减到 2 个
docker-compose up -d --scale taskmanager=2
```

## 提交作业

### 方法 1: Web UI（推荐）

1. 编译项目
   ```bash
   mvn clean package -DskipTests
   ```

2. 访问 http://localhost:8081

3. 点击 "Submit New Job"

4. 上传 `target/realtime-data-pipeline-1.0.0-SNAPSHOT.jar`

5. 选择 Entry Class: `com.realtime.pipeline.FlinkCDC3App`

6. 点击 "Submit"

### 方法 2: 命令行

```bash
# 编译
mvn clean package -DskipTests

# 提交作业
docker exec flink-jobmanager flink run \
  -d \
  /opt/flink/lib/realtime-data-pipeline-1.0.0-SNAPSHOT.jar
```

## 验证作业运行

### 检查作业状态

```bash
curl http://localhost:8081/jobs
```

### 查看 CSV 输出

```bash
# 查看最近生成的文件
find output/cdc -name "*.csv" -type f -mmin -5

# 查看文件内容
tail -10 output/cdc/IDS_TRANS_INFO_*.csv
```

## 故障场景测试

### 场景 1: JobManager 故障

```bash
# 停止主 JobManager
docker-compose stop jobmanager

# 等待 30 秒
sleep 30

# 检查备节点是否接管
curl http://localhost:8082/overview

# 恢复主节点
docker-compose start jobmanager
```

### 场景 2: TaskManager 故障

```bash
# 停止一个 TaskManager
docker-compose stop realtime-pipeline-taskmanager-1

# 等待 30 秒
sleep 30

# 检查作业是否继续运行
curl http://localhost:8081/jobs

# 恢复 TaskManager
docker-compose start realtime-pipeline-taskmanager-1
```

### 场景 3: ZooKeeper 故障

```bash
# 停止 ZooKeeper
docker-compose stop zookeeper

# 集群短期内继续运行（使用缓存的元数据）
curl http://localhost:8081/overview

# 恢复 ZooKeeper（必须尽快）
docker-compose start zookeeper
```

## 常见问题

### Q1: 如何查看当前的 Leader？

```bash
# 方法 1: 使用监控脚本
./shell/monitor-ha-cluster.sh

# 方法 2: 手动检查
if curl -s http://localhost:8081/overview >/dev/null 2>&1; then
    echo "Leader: 主节点 (8081)"
else
    echo "Leader: 备节点 (8082)"
fi
```

### Q2: 如何触发 Savepoint？

```bash
# 获取作业 ID
JOB_ID=$(curl -s http://localhost:8081/jobs | python3 -c "
import sys, json
data = json.load(sys.stdin)
print(data['jobs'][0]['id'])
")

# 触发 Savepoint
curl -X POST http://localhost:8081/jobs/$JOB_ID/savepoints \
  -H "Content-Type: application/json" \
  -d '{"target-directory": "/opt/flink/savepoints"}'
```

### Q3: 如何完全重置集群？

```bash
# 停止所有服务
docker-compose down

# 删除所有数据
docker volume rm flink-checkpoints flink-savepoints flink-ha flink-logs flink-data
docker volume rm zookeeper-data zookeeper-logs

# 重新部署
./shell/deploy-flink-ha.sh
```

### Q4: 如何查看 ZooKeeper 中的 HA 数据？

```bash
# 进入 ZooKeeper 容器
docker exec -it zookeeper bash

# 查看 HA 节点
zookeeper-shell localhost:2181 ls /flink/realtime-pipeline

# 查看 Leader 信息
zookeeper-shell localhost:2181 get /flink/realtime-pipeline/leader

# 退出
exit
```

## 性能调优

### 增加内存

编辑 `.env`:
```bash
JOB_MANAGER_HEAP_SIZE=2048m
TASK_MANAGER_HEAP_SIZE=2048m
TASK_MANAGER_MEMORY_PROCESS_SIZE=3456m
```

重启集群：
```bash
docker-compose down
docker-compose up -d
```

### 调整 Checkpoint 间隔

编辑 `.env`:
```bash
CHECKPOINT_INTERVAL=180000  # 3 分钟
```

### 增加并行度

编辑 `.env`:
```bash
PARALLELISM_DEFAULT=8
TASK_MANAGER_NUMBER_OF_TASK_SLOTS=8
```

## 监控指标

### 集群资源

```bash
curl http://localhost:8081/overview | python3 -c "
import sys, json
data = json.load(sys.stdin)
print(f'TaskManagers: {data[\"taskmanagers\"]}')
print(f'总槽位: {data[\"slots-total\"]}')
print(f'可用槽位: {data[\"slots-available\"]}')
print(f'运行作业: {data[\"jobs-running\"]}')
"
```

### 作业状态

```bash
curl http://localhost:8081/jobs | python3 -c "
import sys, json
data = json.load(sys.stdin)
for job in data['jobs']:
    print(f'作业: {job[\"id\"]}')
    print(f'状态: {job[\"status\"]}')
"
```

### Checkpoint 统计

```bash
JOB_ID="<your-job-id>"
curl http://localhost:8081/jobs/$JOB_ID/checkpoints | python3 -c "
import sys, json
data = json.load(sys.stdin)
latest = data['latest']['completed']
print(f'Checkpoint ID: {latest[\"id\"]}')
print(f'状态大小: {latest[\"state_size\"]} bytes')
"
```

## 脚本说明

| 脚本 | 用途 | 使用场景 |
|------|------|----------|
| `deploy-flink-ha.sh` | 完整部署 | 首次部署或重新部署 |
| `quick-start-ha.sh` | 快速启动 | 日常启动已配置的集群 |
| `monitor-ha-cluster.sh` | 监控集群 | 查看集群状态和健康度 |
| `test-ha-failover.sh` | 测试故障转移 | 验证 HA 功能 |

## 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                        ZooKeeper                            │
│                    (协调和元数据存储)                         │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ 选举和协调
                              │
        ┌─────────────────────┴─────────────────────┐
        │                                           │
┌───────▼────────┐                         ┌───────▼────────┐
│  JobManager 1  │                         │  JobManager 2  │
│    (Leader)    │◄────────心跳────────────►│   (Standby)   │
│   端口: 8081   │                         │   端口: 8082   │
└────────┬───────┘                         └────────┬───────┘
         │                                          │
         │                                          │
         └──────────────┬───────────────────────────┘
                        │
                        │ 任务分配
                        │
        ┌───────────────┴───────────────┐
        │               │               │
┌───────▼────┐  ┌───────▼────┐  ┌───────▼────┐
│TaskManager1│  │TaskManager2│  │TaskManager3│
│  4 Slots   │  │  4 Slots   │  │  4 Slots   │
└────────────┘  └────────────┘  └────────────┘
        │               │               │
        └───────────────┴───────────────┘
                        │
                        ▼
                ┌───────────────┐
                │  CSV Files    │
                │  (./output)   │
                └───────────────┘
```

## 相关文档

- **详细手册**: docs/FLINK_HA_DEPLOYMENT_GUIDE.md（60+ 页完整指南）
- **配置说明**: md/FLINK_HA_ENABLED.md
- **Docker 配置**: docker-compose.yml
- **环境变量**: .env

## 获取帮助

如果遇到问题：

1. 查看日志
   ```bash
   docker-compose logs jobmanager
   docker-compose logs jobmanager-standby
   ```

2. 检查容器状态
   ```bash
   docker-compose ps
   ```

3. 运行监控脚本
   ```bash
   ./shell/monitor-ha-cluster.sh
   ```

4. 查看详细手册
   ```bash
   cat docs/FLINK_HA_DEPLOYMENT_GUIDE.md
   ```

---

**最后更新**: 2026-02-26  
**维护者**: Kiro AI Assistant  
**版本**: 1.0.0
