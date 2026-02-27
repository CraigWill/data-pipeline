# Flink 高可用（HA）部署指南

**状态**: ✅ 配置完成，随时可部署  
**日期**: 2026-02-26

## 🚀 快速开始（3 步）

### 步骤 1: 一键部署

```bash
./shell/deploy-flink-ha.sh
```

### 步骤 2: 验证部署

```bash
./shell/monitor-ha-cluster.sh
```

### 步骤 3: 访问 Web UI

- 主 JobManager: http://localhost:8081
- 备 JobManager: http://localhost:8082

## 📚 文档导航

### 新手入门

1. **快速开始指南** (5 分钟)
   - 文件: `docs/FLINK_HA_QUICKSTART.md`
   - 内容: 3 步快速部署，测试故障转移

2. **详细部署手册** (60+ 页)
   - 文件: `docs/FLINK_HA_DEPLOYMENT_GUIDE.md`
   - 内容: 完整的部署步骤，每一步都有详细说明

### 配置参考

3. **配置总结**
   - 文件: `md/FLINK_HA_ENABLED.md`
   - 内容: 已完成的配置清单，快速部署指南

4. **实施总结**
   - 文件: `md/FLINK_HA_IMPLEMENTATION_SUMMARY.md`
   - 内容: 完整的实施过程，架构说明

## 🛠️ 自动化脚本

### 部署脚本

```bash
# 完整部署（首次部署）
./shell/deploy-flink-ha.sh

# 快速启动（已配置）
./shell/quick-start-ha.sh
```

### 监控脚本

```bash
# 实时监控集群状态
./shell/monitor-ha-cluster.sh
```

### 测试脚本

```bash
# 自动测试故障转移
./shell/test-ha-failover.sh
```

## 🏗️ 架构概览

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
```

## ✨ 高可用特性

- ✅ **自动故障转移**: JobManager 故障时自动切换（~30秒）
- ✅ **零停机**: 作业继续运行，无需人工干预
- ✅ **数据不丢失**: 基于 Checkpoint 机制恢复状态
- ✅ **快速恢复**: 从最近的 Checkpoint 恢复作业
- ✅ **元数据持久化**: ZooKeeper 存储集群元数据

## 📋 配置清单

### 已完成的配置

- ✅ `.env` - HA_MODE=zookeeper
- ✅ `docker-compose.yml` - 添加 jobmanager-standby
- ✅ TaskManager HA 配置更新
- ✅ 4 个自动化脚本
- ✅ 完整文档（60+ 页）

### 服务列表

| 服务 | 端口 | 说明 |
|------|------|------|
| ZooKeeper | 2181 | 协调服务 |
| JobManager 主 | 8081 | Web UI |
| JobManager 备 | 8082 | Web UI |
| TaskManager x3 | - | 数据处理 |

## 🔧 常用命令

### 启动和停止

```bash
# 启动集群
./shell/quick-start-ha.sh

# 停止集群
docker-compose down

# 重启服务
docker-compose restart
```

### 监控和日志

```bash
# 监控集群
./shell/monitor-ha-cluster.sh

# 查看日志
docker-compose logs -f jobmanager
docker-compose logs -f jobmanager-standby
docker-compose logs -f taskmanager
```

### 扩展和缩减

```bash
# 扩展到 5 个 TaskManager
docker-compose up -d --scale taskmanager=5

# 缩减到 2 个 TaskManager
docker-compose up -d --scale taskmanager=2
```

## 🧪 测试故障转移

### 自动测试（推荐）

```bash
./shell/test-ha-failover.sh
```

### 手动测试

```bash
# 1. 停止主 JobManager
docker-compose stop jobmanager

# 2. 等待 30 秒
sleep 30

# 3. 检查备节点是否接管
curl http://localhost:8082/overview

# 4. 恢复主节点
docker-compose start jobmanager
```

## 📊 验证部署

### 检查容器状态

```bash
docker-compose ps
```

预期输出：
```
NAME                              STATUS
zookeeper                         Up (healthy)
flink-jobmanager                  Up (healthy)
flink-jobmanager-standby          Up (healthy)
realtime-pipeline-taskmanager-1   Up (healthy)
realtime-pipeline-taskmanager-2   Up (healthy)
realtime-pipeline-taskmanager-3   Up (healthy)
```

### 检查 Leader

```bash
# 方法 1: 使用监控脚本
./shell/monitor-ha-cluster.sh

# 方法 2: 手动检查
curl http://localhost:8081/overview  # 主节点
curl http://localhost:8082/overview  # 备节点
```

## 🎯 提交作业

### 方法 1: Web UI（推荐）

1. 编译项目
   ```bash
   mvn clean package -DskipTests
   ```

2. 访问 http://localhost:8081

3. 点击 "Submit New Job"

4. 上传 JAR 文件

5. 选择 Entry Class: `com.realtime.pipeline.FlinkCDC3App`

6. 点击 "Submit"

### 方法 2: 命令行

```bash
docker exec flink-jobmanager flink run \
  -d \
  /opt/flink/lib/realtime-data-pipeline-1.0.0-SNAPSHOT.jar
```

## ❓ 常见问题

### Q1: 如何查看当前的 Leader？

```bash
./shell/monitor-ha-cluster.sh
```

### Q2: 如何完全重置集群？

```bash
# 停止所有服务
docker-compose down

# 删除所有数据
docker volume rm flink-checkpoints flink-savepoints flink-ha flink-logs flink-data
docker volume rm zookeeper-data zookeeper-logs

# 重新部署
./shell/deploy-flink-ha.sh
```

### Q3: 故障转移需要多长时间？

通常在 30 秒内完成，包括：
- ZooKeeper 检测故障（~10秒）
- Leader 选举（~10秒）
- 作业恢复（~10秒）

### Q4: 如何增加内存？

编辑 `.env`:
```bash
JOB_MANAGER_HEAP_SIZE=2048m
TASK_MANAGER_HEAP_SIZE=2048m
```

然后重启集群：
```bash
docker-compose down
docker-compose up -d
```

## 🔍 故障排查

### 问题 1: JobManager 无法选举 Leader

```bash
# 检查 ZooKeeper
docker-compose logs zookeeper

# 重启 ZooKeeper
docker-compose restart zookeeper
sleep 30

# 重启 JobManager
docker-compose restart jobmanager jobmanager-standby
```

### 问题 2: TaskManager 无法连接

```bash
# 检查 HA 配置
docker exec realtime-pipeline-taskmanager-1 env | grep HA_

# 重启 TaskManager
docker-compose restart taskmanager
```

### 问题 3: 查看详细日志

```bash
# JobManager 日志
docker-compose logs --tail 100 jobmanager

# Standby JobManager 日志
docker-compose logs --tail 100 jobmanager-standby

# TaskManager 日志
docker-compose logs --tail 100 taskmanager
```

## 📈 性能优化

### 增加内存

编辑 `.env`:
```bash
JOB_MANAGER_HEAP_SIZE=2048m
TASK_MANAGER_HEAP_SIZE=2048m
TASK_MANAGER_MEMORY_PROCESS_SIZE=3456m
```

### 调整 Checkpoint

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

### 扩展 TaskManager

```bash
docker-compose up -d --scale taskmanager=5
```

## 📖 详细文档

| 文档 | 说明 | 页数 |
|------|------|------|
| `docs/FLINK_HA_DEPLOYMENT_GUIDE.md` | 完整部署手册 | 60+ 页 |
| `docs/FLINK_HA_QUICKSTART.md` | 快速开始指南 | 5 分钟 |
| `md/FLINK_HA_ENABLED.md` | 配置总结 | - |
| `md/FLINK_HA_IMPLEMENTATION_SUMMARY.md` | 实施总结 | - |

## 🎬 下一步

1. **部署 HA 集群**
   ```bash
   ./shell/deploy-flink-ha.sh
   ```

2. **验证部署**
   ```bash
   ./shell/monitor-ha-cluster.sh
   ```

3. **测试故障转移**
   ```bash
   ./shell/test-ha-failover.sh
   ```

4. **提交作业**
   - 访问 http://localhost:8081
   - 上传 JAR 文件

5. **监控集群**
   ```bash
   ./shell/monitor-ha-cluster.sh
   ```

## 💡 提示

- 首次部署使用 `deploy-flink-ha.sh`
- 日常启动使用 `quick-start-ha.sh`
- 定期运行 `monitor-ha-cluster.sh` 监控集群
- 定期测试故障转移功能
- 查看详细文档了解更多信息

## 📞 获取帮助

如果遇到问题：

1. 查看日志
   ```bash
   docker-compose logs jobmanager
   ```

2. 运行监控脚本
   ```bash
   ./shell/monitor-ha-cluster.sh
   ```

3. 查看详细手册
   ```bash
   cat docs/FLINK_HA_DEPLOYMENT_GUIDE.md
   ```

4. 查看常见问题
   - 详细手册第 8 章
   - 快速指南常见问题部分

---

**最后更新**: 2026-02-26  
**维护者**: Kiro AI Assistant  
**版本**: 1.0.0  
**状态**: ✅ 配置完成，随时可部署
