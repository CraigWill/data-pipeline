# Flink 高可用（HA）实施总结

**日期**: 2026-02-26  
**任务**: 采用高可用方式部署 Flink  
**状态**: ✅ 完成（配置就绪，待部署）

## 任务概述

根据用户需求"采用高可用方式部署 flink"和"写一个使用手册，每一步怎么做"，完成了以下工作：

1. ✅ 创建详细的 HA 部署使用手册（60+ 页）
2. ✅ 配置 Docker Compose 支持 HA
3. ✅ 更新环境变量启用 HA
4. ✅ 创建 4 个自动化部署和测试脚本
5. ✅ 创建快速开始指南

## 完成的工作

### 1. 详细使用手册 ✅

**文件**: `docs/FLINK_HA_DEPLOYMENT_GUIDE.md`

**内容**（60+ 页）:
- 概述：什么是 Flink HA，优势
- 前置条件：系统要求，端口检查
- 架构说明：HA 架构图，组件说明
- 部署步骤：10 个详细步骤
  * 步骤 1: 准备项目
  * 步骤 2: 配置环境变量
  * 步骤 3: 修改 Docker Compose
  * 步骤 4: 启动高可用集群
  * 步骤 5: 验证集群状态
  * 步骤 6: 提交测试作业
  * 步骤 7: 测试 JobManager 故障转移
  * 步骤 8: 测试 TaskManager 故障
  * 步骤 9: 日常监控
  * 步骤 10: 维护操作
- 常见问题：7 个 Q&A
- 附录：环境变量列表、命令速查、相关文档

**特点**:
- 每个步骤都有具体的命令
- 包含预期输出示例
- 提供验证方法
- 包含故障测试场景
- 提供监控和维护脚本

### 2. Docker Compose 配置 ✅

**文件**: `docker-compose.yml`

**修改内容**:

1. **新增 jobmanager-standby 服务**
   ```yaml
   jobmanager-standby:
     container_name: flink-jobmanager-standby
     hostname: jobmanager-standby
     ports:
       - "8082:8081"  # Web UI
       - "6125:6123"  # RPC
       - "6126:6124"  # Blob Server
       - "9250:9249"  # Metrics
     environment:
       - HA_MODE=zookeeper
       - HA_ZOOKEEPER_QUORUM=zookeeper:2181
       # ... 其他配置与主节点相同
   ```

2. **更新 jobmanager 服务**
   - 启用 HA 配置
   - 添加 HA 环境变量

3. **更新 taskmanager 服务**
   - 修改 HA_MODE 默认值为 zookeeper
   - 添加完整的 HA 配置

### 3. 环境变量配置 ✅

**文件**: `.env`

**修改内容**:
```bash
# 从
HA_MODE=NONE

# 改为
HA_MODE=zookeeper

# 并添加完整的 HA 配置注释
HA_ZOOKEEPER_QUORUM=zookeeper:2181
HA_ZOOKEEPER_PATH_ROOT=/flink
HA_CLUSTER_ID=/realtime-pipeline
HA_STORAGE_DIR=file:///opt/flink/ha
```

### 4. 自动化脚本 ✅

创建了 4 个自动化脚本：

#### 4.1 完整部署脚本

**文件**: `shell/deploy-flink-ha.sh`

**功能**:
- 检查前置条件（Docker, Docker Compose）
- 检查 .env 配置
- 停止现有服务
- 可选清理旧 HA 数据
- 按顺序启动服务：
  1. ZooKeeper（等待 30 秒）
  2. JobManager 主和备（等待 60 秒）
  3. TaskManager x3（等待 30 秒）
- 验证集群状态
- 检查 Leader
- 显示集群资源
- 提供下一步操作指引

**使用**:
```bash
./shell/deploy-flink-ha.sh
```

#### 4.2 快速启动脚本

**文件**: `shell/quick-start-ha.sh`

**功能**:
- 快速启动已配置的 HA 集群
- 适合日常使用
- 无交互式提示

**使用**:
```bash
./shell/quick-start-ha.sh
```

#### 4.3 监控脚本

**文件**: `shell/monitor-ha-cluster.sh`

**功能**:
- 显示容器状态
- 检查 Leader（主节点或备节点）
- 显示集群资源（TaskManager 数量、槽位）
- 显示作业状态
- 显示最近生成的 CSV 文件
- 检查 ZooKeeper 连接
- 显示 Checkpoint 状态

**使用**:
```bash
./shell/monitor-ha-cluster.sh
```

**输出示例**:
```
=== Flink HA 集群监控 ===

1. 容器状态:
   zookeeper                         Up (healthy)
   flink-jobmanager                  Up (healthy)
   flink-jobmanager-standby          Up (healthy)
   realtime-pipeline-taskmanager-1   Up (healthy)

2. JobManager Leader:
   Leader: JobManager 主节点 (端口 8081)

3. 集群资源:
   TaskManagers: 3
   总槽位: 12
   可用槽位: 12
```

#### 4.4 故障转移测试脚本

**文件**: `shell/test-ha-failover.sh`

**功能**:
- 自动测试 JobManager 故障转移
- 检查当前 Leader
- 记录当前作业
- 停止当前 Leader
- 等待故障转移（30秒）
- 验证新 Leader
- 检查作业状态
- 验证 CSV 文件继续生成
- 可选恢复原 Leader

**使用**:
```bash
./shell/test-ha-failover.sh
```

### 5. 快速开始指南 ✅

**文件**: `docs/FLINK_HA_QUICKSTART.md`

**内容**:
- 5 分钟快速部署指南
- 3 步快速部署流程
- 测试故障转移
- 日常使用命令
- 提交作业方法
- 故障场景测试
- 常见问题 Q&A
- 性能调优建议
- 监控指标查询
- 架构图

### 6. 配置总结文档 ✅

**文件**: `md/FLINK_HA_ENABLED.md`

**内容**:
- 已完成的配置清单
- 高可用架构图
- 快速部署指南
- 验证部署方法
- 测试故障转移
- 日常运维命令
- 高可用特性说明
- 性能优化建议
- 故障排查指南
- 备份和恢复

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
        │               │               │
        └───────────────┴───────────────┘
                        │
                        ▼
                ┌───────────────┐
                │  CSV Files    │
                │  (./output)   │
                └───────────────┘
```

## 高可用特性

### 1. 自动故障转移

- **JobManager Leader 故障**: Standby 自动接管（~30秒）
- **TaskManager 故障**: 作业自动重新调度到其他 TaskManager
- **ZooKeeper 故障**: 集群短期内继续运行（使用缓存的元数据）

### 2. 状态恢复

- **Checkpoint**: 每 5 分钟自动保存作业状态
- **Savepoint**: 手动触发保存完整状态
- **自动恢复**: 从最近的 Checkpoint 恢复作业

### 3. 零停机

- **作业不中断**: 故障转移期间作业继续运行
- **数据不丢失**: 基于 Checkpoint 机制保证数据一致性
- **快速恢复**: 通常在 30 秒内完成故障转移

### 4. 元数据持久化

- **ZooKeeper 存储**: 集群元数据、Leader 信息、作业状态
- **HA 存储目录**: JobManager 元数据、Checkpoint 指针
- **自动同步**: 主备节点自动同步状态

## 部署流程

### 方法 1: 自动化部署（推荐）

```bash
# 一键部署
./shell/deploy-flink-ha.sh
```

### 方法 2: 快速启动

```bash
# 快速启动（已配置）
./shell/quick-start-ha.sh
```

### 方法 3: 手动部署

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

# 5. 验证
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
./shell/monitor-ha-cluster.sh
```

### 4. 测试故障转移

```bash
./shell/test-ha-failover.sh
```

## 文档清单

| 文档 | 路径 | 说明 |
|------|------|------|
| 详细部署手册 | `docs/FLINK_HA_DEPLOYMENT_GUIDE.md` | 60+ 页完整指南 |
| 快速开始指南 | `docs/FLINK_HA_QUICKSTART.md` | 5 分钟快速部署 |
| 配置总结 | `md/FLINK_HA_ENABLED.md` | 配置清单和说明 |
| 实施总结 | `md/FLINK_HA_IMPLEMENTATION_SUMMARY.md` | 本文档 |

## 脚本清单

| 脚本 | 路径 | 说明 |
|------|------|------|
| 完整部署 | `shell/deploy-flink-ha.sh` | 自动化完整部署 |
| 快速启动 | `shell/quick-start-ha.sh` | 快速启动集群 |
| 监控集群 | `shell/monitor-ha-cluster.sh` | 实时监控状态 |
| 测试故障转移 | `shell/test-ha-failover.sh` | 自动测试 HA |

## 配置文件

| 文件 | 修改内容 |
|------|----------|
| `.env` | HA_MODE=zookeeper |
| `docker-compose.yml` | 添加 jobmanager-standby 服务 |
| `docker-compose.yml` | 更新 TaskManager HA 配置 |

## 下一步操作

### 1. 部署 HA 集群

```bash
./shell/deploy-flink-ha.sh
```

### 2. 验证部署

```bash
./shell/monitor-ha-cluster.sh
```

### 3. 提交作业

```bash
# 编译
mvn clean package -DskipTests

# 访问 Web UI 提交
open http://localhost:8081
```

### 4. 测试故障转移

```bash
./shell/test-ha-failover.sh
```

## 关键配置

### ZooKeeper 配置

```yaml
zookeeper:
  image: confluentinc/cp-zookeeper:7.5.0
  ports:
    - "2181:2181"
  environment:
    ZOOKEEPER_CLIENT_PORT: 2181
```

### JobManager 主节点

```yaml
jobmanager:
  ports:
    - "8081:8081"  # Web UI
    - "6123:6123"  # RPC
  environment:
    - HA_MODE=zookeeper
    - HA_ZOOKEEPER_QUORUM=zookeeper:2181
```

### JobManager 备节点

```yaml
jobmanager-standby:
  ports:
    - "8082:8081"  # Web UI
    - "6125:6123"  # RPC
  environment:
    - HA_MODE=zookeeper
    - HA_ZOOKEEPER_QUORUM=zookeeper:2181
```

### TaskManager

```yaml
taskmanager:
  environment:
    - HA_MODE=zookeeper
    - HA_ZOOKEEPER_QUORUM=zookeeper:2181
```

## 端口分配

| 服务 | 端口 | 说明 |
|------|------|------|
| ZooKeeper | 2181 | 协调服务 |
| JobManager 主 | 8081 | Web UI |
| JobManager 主 | 6123 | RPC |
| JobManager 主 | 6124 | Blob Server |
| JobManager 主 | 9249 | Metrics |
| JobManager 备 | 8082 | Web UI |
| JobManager 备 | 6125 | RPC |
| JobManager 备 | 6126 | Blob Server |
| JobManager 备 | 9250 | Metrics |
| TaskManager | 6121 | Data Port |
| TaskManager | 6122 | RPC |

## 资源配置

### JobManager

- 内存: 1024m (可调整到 2048m)
- CPU: 1-2 核
- 实例: 2 个（主 + 备）

### TaskManager

- 内存: 1024m (可调整到 2048m)
- CPU: 1-2 核
- 槽位: 4 个/实例
- 实例: 3 个（可扩展到 5+）

### ZooKeeper

- 内存: 默认
- CPU: 默认
- 实例: 1 个

## 监控指标

### 集群级别

- TaskManager 数量
- 总槽位数
- 可用槽位数
- 运行作业数
- 完成作业数
- 失败作业数

### 作业级别

- 作业状态（RUNNING/FAILED/FINISHED）
- Checkpoint 状态
- Checkpoint 大小
- 最后 Checkpoint 时间

### 系统级别

- 容器状态（Up/Down）
- 容器健康状态（healthy/unhealthy）
- Leader 状态（主节点/备节点）
- ZooKeeper 连接状态

## 故障场景

### 场景 1: JobManager Leader 故障

**现象**: 主 JobManager 停止响应

**自动处理**:
1. ZooKeeper 检测到 Leader 心跳丢失
2. 触发 Leader 选举
3. Standby JobManager 成为新 Leader（~30秒）
4. 作业从最近的 Checkpoint 恢复
5. TaskManager 重新连接到新 Leader

**用户操作**: 无需操作，系统自动恢复

### 场景 2: TaskManager 故障

**现象**: 一个或多个 TaskManager 停止

**自动处理**:
1. JobManager 检测到 TaskManager 心跳丢失
2. 将任务重新调度到其他 TaskManager
3. 从最近的 Checkpoint 恢复任务状态

**用户操作**: 可选重启故障的 TaskManager

### 场景 3: ZooKeeper 故障

**现象**: ZooKeeper 服务停止

**影响**:
- 短期内集群继续运行（使用缓存的元数据）
- 无法进行 Leader 选举
- 无法更新集群元数据

**用户操作**: 必须尽快恢复 ZooKeeper

## 性能优化建议

### 1. 增加内存

```bash
# 编辑 .env
JOB_MANAGER_HEAP_SIZE=2048m
TASK_MANAGER_HEAP_SIZE=2048m
```

### 2. 调整 Checkpoint

```bash
# 编辑 .env
CHECKPOINT_INTERVAL=180000  # 3 分钟
```

### 3. 增加并行度

```bash
# 编辑 .env
PARALLELISM_DEFAULT=8
TASK_MANAGER_NUMBER_OF_TASK_SLOTS=8
```

### 4. 扩展 TaskManager

```bash
docker-compose up -d --scale taskmanager=5
```

## 总结

✅ **已完成**:
- 详细的 HA 部署使用手册（60+ 页）
- Docker Compose HA 配置
- 环境变量 HA 配置
- 4 个自动化脚本
- 快速开始指南
- 配置总结文档

✅ **待执行**:
- 运行部署脚本启动 HA 集群
- 验证故障转移功能
- 提交作业测试

✅ **文档完整**:
- 每一步都有详细说明
- 包含预期输出
- 提供验证方法
- 包含故障测试
- 提供监控脚本

✅ **用户友好**:
- 一键部署脚本
- 自动化测试脚本
- 实时监控脚本
- 详细的故障排查指南

---

**最后更新**: 2026-02-26  
**状态**: ✅ 完成（配置就绪，待部署）  
**维护者**: Kiro AI Assistant  
**版本**: 1.0.0
