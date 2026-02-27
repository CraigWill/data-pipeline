# Flink 高可用（HA）部署完成

**日期**: 2026-02-26  
**状态**: ✅ 配置完成，待部署  
**文档**: docs/FLINK_HA_DEPLOYMENT_GUIDE.md

## 已完成的配置

### 1. 环境变量配置 (.env)

```bash
HA_MODE=zookeeper
HA_ZOOKEEPER_QUORUM=zookeeper:2181
HA_ZOOKEEPER_PATH_ROOT=/flink
HA_CLUSTER_ID=/realtime-pipeline
HA_STORAGE_DIR=file:///opt/flink/ha
```

### 2. Docker Compose 配置

**新增服务**:
- `jobmanager-standby` - 备用 JobManager
  - Web UI: http://localhost:8082
  - RPC: 6125
  - Blob Server: 6126
  - Metrics: 9250

**更新服务**:
- `jobmanager` - 主 JobManager（启用 HA）
- `taskmanager` - 更新 HA 配置

### 3. 部署脚本

创建了 4 个自动化脚本：

1. **deploy-flink-ha.sh** - 完整部署脚本
   - 检查前置条件
   - 停止现有服务
   - 清理旧数据（可选）
   - 按顺序启动服务
   - 验证集群状态

2. **quick-start-ha.sh** - 快速启动脚本
   - 快速启动已配置的 HA 集群
   - 适合日常使用

3. **monitor-ha-cluster.sh** - 监控脚本
   - 实时监控集群状态
   - 显示 Leader 信息
   - 检查作业状态
   - 查看 CSV 文件生成

4. **test-ha-failover.sh** - 故障转移测试脚本
   - 自动测试 JobManager 故障转移
   - 验证作业继续运行
   - 检查数据输出

## 高可用架构

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

## 快速部署指南

### 方法 1: 使用自动化脚本（推荐）

```bash
# 完整部署（首次部署）
./shell/deploy-flink-ha.sh

# 快速启动（已配置）
./shell/quick-start-ha.sh
```

### 方法 2: 手动部署

```bash
# 1. 停止现有服务
docker-compose down

# 2. 启动 ZooKeeper
docker-compose up -d zookeeper
sleep 30

# 3. 启动 JobManager（主和备）
docker-compose up -d jobmanager jobmanager-standby
sleep 60

# 4. 启动 TaskManager
docker-compose up -d --scale taskmanager=3
sleep 30

# 5. 验证集群
./shell/monitor-ha-cluster.sh
```

## 验证部署

### 1. 检查容器状态

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

### 2. 访问 Web UI

- 主 JobManager: http://localhost:8081
- 备 JobManager: http://localhost:8082

### 3. 检查 Leader

```bash
# 方法 1: 使用监控脚本
./shell/monitor-ha-cluster.sh

# 方法 2: 手动检查
curl http://localhost:8081/overview  # 主节点
curl http://localhost:8082/overview  # 备节点
```

### 4. 查看 ZooKeeper 数据

```bash
docker exec zookeeper zookeeper-shell localhost:2181 ls /flink/realtime-pipeline
```

## 测试故障转移

### 自动测试（推荐）

```bash
./shell/test-ha-failover.sh
```

### 手动测试

```bash
# 1. 记录当前 Leader
curl http://localhost:8081/overview  # 如果成功，主节点是 Leader

# 2. 停止 Leader
docker-compose stop jobmanager

# 3. 等待 30 秒
sleep 30

# 4. 检查备节点是否接管
curl http://localhost:8082/overview

# 5. 验证作业继续运行
curl http://localhost:8082/jobs

# 6. 恢复原节点
docker-compose start jobmanager
```

## 日常运维

### 监控集群

```bash
# 实时监控
./shell/monitor-ha-cluster.sh

# 查看日志
docker-compose logs -f jobmanager
docker-compose logs -f jobmanager-standby
docker-compose logs -f taskmanager
```

### 扩展 TaskManager

```bash
# 扩展到 5 个
docker-compose up -d --scale taskmanager=5

# 缩减到 2 个
docker-compose up -d --scale taskmanager=2
```

### 重启服务

```bash
# 重启单个服务
docker-compose restart jobmanager

# 重启所有服务
docker-compose restart
```

### 查看资源使用

```bash
# 容器资源
docker stats

# 集群资源
curl http://localhost:8081/overview
```

## 高可用特性

### 1. 自动故障转移

- **Leader 故障**: Standby 自动接管（~30秒）
- **TaskManager 故障**: 作业自动重新调度
- **ZooKeeper 故障**: 集群继续运行（短期）

### 2. 状态恢复

- **Checkpoint**: 每 5 分钟自动保存
- **Savepoint**: 手动触发保存
- **恢复**: 从最近的 Checkpoint 恢复

### 3. 零停机

- **作业不中断**: 故障转移期间作业继续运行
- **数据不丢失**: 基于 Checkpoint 机制
- **快速恢复**: 通常 30 秒内完成

## 性能优化

### 1. 增加内存

编辑 `.env`:
```bash
JOB_MANAGER_HEAP_SIZE=2048m
TASK_MANAGER_HEAP_SIZE=2048m
```

### 2. 调整 Checkpoint

编辑 `.env`:
```bash
CHECKPOINT_INTERVAL=180000  # 3 分钟
```

### 3. 增加并行度

编辑 `.env`:
```bash
PARALLELISM_DEFAULT=8
TASK_MANAGER_NUMBER_OF_TASK_SLOTS=8
```

## 故障排查

### 问题 1: JobManager 无法选举 Leader

**症状**: 两个 JobManager 都无法成为 Leader

**解决**:
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

**症状**: TaskManager 显示离线

**解决**:
```bash
# 检查 HA 配置
docker exec realtime-pipeline-taskmanager-1 env | grep HA_

# 重启 TaskManager
docker-compose restart taskmanager
```

### 问题 3: 故障转移失败

**症状**: Leader 切换后作业失败

**解决**:
```bash
# 检查 Checkpoint
ls -lh flink-checkpoints/

# 查看作业异常
curl http://localhost:8082/jobs/<job-id>/exceptions

# 从 Savepoint 恢复
# 参考 docs/FLINK_HA_DEPLOYMENT_GUIDE.md 步骤 10.2
```

## 备份和恢复

### 备份关键数据

```bash
# 创建备份
BACKUP_DIR="./backups/$(date +%Y%m%d_%H%M%S)"
mkdir -p $BACKUP_DIR

# 备份 Savepoints
docker cp flink-jobmanager:/opt/flink/savepoints $BACKUP_DIR/

# 备份 HA 元数据
docker cp flink-jobmanager:/opt/flink/ha $BACKUP_DIR/

# 备份配置
cp docker-compose.yml $BACKUP_DIR/
cp .env $BACKUP_DIR/

# 压缩
tar -czf $BACKUP_DIR.tar.gz $BACKUP_DIR
```

### 恢复数据

```bash
# 解压备份
tar -xzf backups/20260226_120000.tar.gz

# 恢复配置
cp backups/20260226_120000/docker-compose.yml .
cp backups/20260226_120000/.env .

# 恢复数据（需要停止服务）
docker-compose down
docker cp backups/20260226_120000/savepoints flink-jobmanager:/opt/flink/
docker cp backups/20260226_120000/ha flink-jobmanager:/opt/flink/
docker-compose up -d
```

## 相关文档

- **详细手册**: docs/FLINK_HA_DEPLOYMENT_GUIDE.md
- **Docker 配置**: docker-compose.yml
- **环境变量**: .env
- **部署脚本**: shell/deploy-flink-ha.sh
- **监控脚本**: shell/monitor-ha-cluster.sh
- **测试脚本**: shell/test-ha-failover.sh

## 下一步

1. **部署 HA 集群**
   ```bash
   ./shell/deploy-flink-ha.sh
   ```

2. **提交作业**
   ```bash
   mvn clean package -DskipTests
   # 访问 http://localhost:8081 上传 JAR
   ```

3. **测试故障转移**
   ```bash
   ./shell/test-ha-failover.sh
   ```

4. **监控集群**
   ```bash
   ./shell/monitor-ha-cluster.sh
   ```

## 总结

✅ **配置完成**
- .env 文件已更新（HA_MODE=zookeeper）
- docker-compose.yml 已添加 jobmanager-standby
- TaskManager HA 配置已更新
- 创建了 4 个自动化脚本

✅ **待执行**
- 运行部署脚本启动 HA 集群
- 验证故障转移功能
- 提交作业测试

✅ **文档完整**
- 详细部署手册（60+ 页）
- 自动化脚本（4 个）
- 故障排查指南
- 性能优化建议

---

**最后更新**: 2026-02-26  
**状态**: ✅ 配置完成，待部署  
**维护者**: Kiro AI Assistant
