# Flink 高可用（HA）部署使用手册

**版本**: 1.0.0  
**日期**: 2026-02-26  
**适用于**: Flink CDC 3.x Oracle 实时数据管道

## 目录

1. [概述](#概述)
2. [前置条件](#前置条件)
3. [架构说明](#架构说明)
4. [部署步骤](#部署步骤)
5. [验证高可用](#验证高可用)
6. [故障测试](#故障测试)
7. [监控和维护](#监控和维护)
8. [常见问题](#常见问题)

---

## 概述

### 什么是 Flink 高可用（HA）

Flink 高可用模式通过以下机制保证系统的可靠性：

1. **JobManager 高可用**: 多个 JobManager 实例，一个 Leader，其他 Standby
2. **自动故障转移**: Leader 故障时，Standby 自动接管
3. **状态恢复**: 从 Checkpoint 恢复作业状态
4. **元数据持久化**: 使用 ZooKeeper 存储集群元数据

### 高可用的优势

- ✅ **零停机**: JobManager 故障时自动切换，作业继续运行
- ✅ **数据不丢失**: 基于 Checkpoint 机制恢复状态
- ✅ **快速恢复**: 通常在 30 秒内完成故障转移
- ✅ **生产就绪**: 满足 99.9% 可用性要求


---

## 前置条件

### 系统要求

- **操作系统**: macOS / Linux
- **Docker**: 20.10+
- **Docker Compose**: 2.0+
- **内存**: 至少 8GB 可用内存
- **磁盘**: 至少 20GB 可用空间

### 检查系统环境

```bash
# 检查 Docker 版本
docker --version
# 预期输出: Docker version 20.10.x 或更高

# 检查 Docker Compose 版本
docker-compose --version
# 预期输出: Docker Compose version 2.x.x 或更高

# 检查可用内存
free -h  # Linux
# 或
vm_stat | grep "Pages free" | awk '{print $3 * 4096 / 1024 / 1024 / 1024 " GB"}'  # macOS

# 检查可用磁盘空间
df -h .
```

### 网络端口

确保以下端口未被占用：

| 端口 | 服务 | 说明 |
|------|------|------|
| 2181 | ZooKeeper | 协调服务 |
| 8081 | JobManager Web UI | Flink 管理界面 |
| 8082 | JobManager Standby Web UI | 备用 JobManager |
| 6123 | JobManager RPC | 主 JobManager RPC |
| 6124 | JobManager Blob Server | 文件服务 |
| 6125 | JobManager Standby RPC | 备用 JobManager RPC |
| 6121-6130 | TaskManager Data Port | 数据传输 |

```bash
# 检查端口占用
lsof -i :2181
lsof -i :8081
lsof -i :8082
# 如果有输出，说明端口被占用，需要先停止相关服务
```


---

## 架构说明

### 高可用架构图

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

### 组件说明

1. **ZooKeeper**: 
   - 存储集群元数据
   - 进行 Leader 选举
   - 协调 JobManager 之间的通信

2. **JobManager Leader**:
   - 接收和调度作业
   - 管理 TaskManager
   - 提供 Web UI (8081)

3. **JobManager Standby**:
   - 热备份状态
   - 监控 Leader 健康状态
   - Leader 故障时自动接管
   - 提供备用 Web UI (8082)

4. **TaskManager**:
   - 执行实际的数据处理任务
   - 可动态扩展
   - 连接到当前的 Leader


---

## 部署步骤

### 步骤 1: 准备项目

#### 1.1 进入项目目录

```bash
cd realtime-data-pipeline
```

#### 1.2 检查项目文件

```bash
# 确认关键文件存在
ls -la docker-compose.yml
ls -la .env.example
ls -la docker/jobmanager/
ls -la docker/taskmanager/
```

#### 1.3 查看当前运行的容器

```bash
docker-compose ps
```

如果有容器在运行，先停止它们：

```bash
# 停止所有容器
docker-compose down

# 确认已停止
docker-compose ps
# 应该没有输出或显示所有容器已停止
```

### 步骤 2: 配置环境变量

#### 2.1 复制环境变量模板

```bash
# 如果 .env 文件不存在，从模板复制
cp .env.example .env
```

#### 2.2 编辑 .env 文件

```bash
vim .env
# 或使用其他编辑器
# nano .env
# code .env
```

#### 2.3 配置高可用参数

在 `.env` 文件中添加或修改以下配置：

```bash
# ============================================
# Flink 高可用配置
# ============================================

# 启用高可用模式
HA_MODE=zookeeper

# ZooKeeper 连接地址
HA_ZOOKEEPER_QUORUM=zookeeper:2181

# ZooKeeper 根路径
HA_ZOOKEEPER_PATH_ROOT=/flink

# 集群 ID（用于区分不同的 Flink 集群）
HA_CLUSTER_ID=/realtime-pipeline

# HA 存储目录（存储 JobManager 元数据）
HA_STORAGE_DIR=file:///opt/flink/ha

# ============================================
# 数据库配置（根据实际情况修改）
# ============================================

DATABASE_HOST=host.docker.internal
DATABASE_PORT=1521
DATABASE_USERNAME=system
DATABASE_PASSWORD=helowin
DATABASE_SID=helowin
DATABASE_SCHEMA=FINANCE_USER
DATABASE_TABLES=TRANS_INFO

# ============================================
# 输出配置
# ============================================

OUTPUT_PATH=./output/cdc
PARALLELISM_DEFAULT=4
```

#### 2.4 验证配置

```bash
# 查看配置内容
cat .env | grep HA_
```

预期输出：
```
HA_MODE=zookeeper
HA_ZOOKEEPER_QUORUM=zookeeper:2181
HA_ZOOKEEPER_PATH_ROOT=/flink
HA_CLUSTER_ID=/realtime-pipeline
HA_STORAGE_DIR=file:///opt/flink/ha
```


### 步骤 3: 修改 Docker Compose 配置

#### 3.1 备份原配置

```bash
cp docker-compose.yml docker-compose.yml.backup
```

#### 3.2 添加第二个 JobManager

编辑 `docker-compose.yml`，在 `jobmanager` 服务后添加 `jobmanager-standby` 服务：

```bash
vim docker-compose.yml
```

在文件中找到 `jobmanager` 服务定义后，添加以下内容：

```yaml
  # Flink JobManager Standby（备用）
  jobmanager-standby:
    build:
      context: .
      dockerfile: docker/jobmanager/Dockerfile
    container_name: flink-jobmanager-standby
    hostname: jobmanager-standby
    depends_on:
      zookeeper:
        condition: service_healthy
    ports:
      - "8082:8081"  # Web UI (使用不同端口)
      - "6125:6123"  # RPC (使用不同端口)
      - "6126:6124"  # Blob Server
      - "9250:9249"  # Prometheus Metrics
    environment:
      # JobManager配置
      - JOB_MANAGER_RPC_ADDRESS=jobmanager-standby
      - JOB_MANAGER_RPC_PORT=6123
      - JOB_MANAGER_HEAP_SIZE=1024m
      - REST_PORT=8081
      - PARALLELISM_DEFAULT=4
      
      # Checkpoint配置
      - CHECKPOINT_INTERVAL=300000
      - CHECKPOINT_DIR=file:///opt/flink/checkpoints
      - SAVEPOINT_DIR=file:///opt/flink/savepoints
      - STATE_BACKEND=hashmap
      
      # 高可用配置（与主 JobManager 相同）
      - HA_MODE=${HA_MODE:-zookeeper}
      - HA_ZOOKEEPER_QUORUM=${HA_ZOOKEEPER_QUORUM:-zookeeper:2181}
      - HA_ZOOKEEPER_PATH_ROOT=${HA_ZOOKEEPER_PATH_ROOT:-/flink}
      - HA_CLUSTER_ID=${HA_CLUSTER_ID:-/realtime-pipeline}
      - HA_STORAGE_DIR=${HA_STORAGE_DIR:-file:///opt/flink/ha}
      
      # 数据库配置
      - DATABASE_HOST=${DATABASE_HOST:-localhost}
      - DATABASE_PORT=${DATABASE_PORT:-1521}
      - DATABASE_USERNAME=${DATABASE_USERNAME:-system}
      - DATABASE_PASSWORD=${DATABASE_PASSWORD:-password}
      - DATABASE_SID=${DATABASE_SID:-helowin}
      - DATABASE_SCHEMA=${DATABASE_SCHEMA:-finance_user}
      - DATABASE_TABLES=${DATABASE_TABLES:-trans_info}
      - OUTPUT_PATH=${OUTPUT_PATH:-./output/cdc}
      - TZ=Asia/Shanghai
    volumes:
      - flink-checkpoints:/opt/flink/checkpoints
      - flink-savepoints:/opt/flink/savepoints
      - flink-ha:/opt/flink/ha
      - flink-logs:/opt/flink/logs
    networks:
      - flink-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/overview"]
      interval: 30s
      timeout: 10s
      start_period: 60s
      retries: 3
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 2G
        reservations:
          cpus: '1.0'
          memory: 1G
```

#### 3.3 修改 TaskManager 配置

确保 TaskManager 的 HA 配置正确：

找到 `taskmanager` 服务的环境变量部分，确认包含：

```yaml
      # 高可用配置（与 JobManager 保持一致）
      - HA_MODE=${HA_MODE:-zookeeper}
      - HA_ZOOKEEPER_QUORUM=${HA_ZOOKEEPER_QUORUM:-zookeeper:2181}
      - HA_ZOOKEEPER_PATH_ROOT=${HA_ZOOKEEPER_PATH_ROOT:-/flink}
      - HA_CLUSTER_ID=${HA_CLUSTER_ID:-/realtime-pipeline}
```

注意：将 `HA_MODE=${HA_MODE:-NONE}` 改为 `HA_MODE=${HA_MODE:-zookeeper}`


### 步骤 4: 启动高可用集群

#### 4.1 清理旧数据（可选但推荐）

```bash
# 删除旧的 HA 数据
docker volume rm flink-ha 2>/dev/null || true

# 重新创建 volume
docker volume create flink-ha
```

#### 4.2 启动 ZooKeeper

```bash
# 先启动 ZooKeeper
docker-compose up -d zookeeper

# 等待 ZooKeeper 启动（约 30 秒）
echo "等待 ZooKeeper 启动..."
sleep 30

# 检查 ZooKeeper 状态
docker-compose ps zookeeper
```

预期输出：
```
NAME        IMAGE                             STATUS
zookeeper   confluentinc/cp-zookeeper:7.5.0   Up (healthy)
```

#### 4.3 启动 JobManager（主和备）

```bash
# 启动两个 JobManager
docker-compose up -d jobmanager jobmanager-standby

# 等待 JobManager 启动（约 60 秒）
echo "等待 JobManager 启动..."
sleep 60

# 检查 JobManager 状态
docker-compose ps jobmanager jobmanager-standby
```

预期输出：
```
NAME                        IMAGE                          STATUS
flink-jobmanager            realtime-pipeline-jobmanager   Up (healthy)
flink-jobmanager-standby    realtime-pipeline-jobmanager   Up (healthy)
```

#### 4.4 启动 TaskManager

```bash
# 启动 3 个 TaskManager
docker-compose up -d --scale taskmanager=3

# 等待 TaskManager 启动（约 30 秒）
echo "等待 TaskManager 启动..."
sleep 30

# 检查所有容器状态
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

#### 4.5 查看日志

```bash
# 查看 JobManager 日志
docker-compose logs -f jobmanager | grep -i "leader"

# 查看 Standby JobManager 日志
docker-compose logs -f jobmanager-standby | grep -i "standby"

# 按 Ctrl+C 退出日志查看
```

在日志中应该看到类似的输出：
```
jobmanager | ... Granted leadership with session ID ...
jobmanager-standby | ... Standby mode, waiting for leadership ...
```


---

## 验证高可用

### 步骤 5: 验证集群状态

#### 5.1 访问 Web UI

打开浏览器，访问以下地址：

**主 JobManager**:
```
http://localhost:8081
```

**备用 JobManager**:
```
http://localhost:8082
```

#### 5.2 检查 Leader 状态

在主 JobManager Web UI (http://localhost:8081) 中：

1. 点击左侧菜单 "Overview"
2. 查看 "JobManager" 部分
3. 应该显示当前 JobManager 的地址和状态

#### 5.3 使用命令行检查

```bash
# 检查 JobManager 状态
curl -s http://localhost:8081/overview | python3 -c "
import sys, json
data = json.load(sys.stdin)
print(f'Flink 版本: {data.get(\"flink-version\", \"N/A\")}')
print(f'TaskManagers: {data.get(\"taskmanagers\", 0)}')
print(f'总槽位: {data.get(\"slots-total\", 0)}')
print(f'可用槽位: {data.get(\"slots-available\", 0)}')
"
```

预期输出：
```
Flink 版本: 1.18.0
TaskManagers: 3
总槽位: 12
可用槽位: 12
```

#### 5.4 检查 ZooKeeper 中的 HA 数据

```bash
# 进入 ZooKeeper 容器
docker exec -it zookeeper bash

# 在容器内执行
zookeeper-shell localhost:2181 ls /flink/realtime-pipeline

# 应该看到类似输出：
# [leader, leaderlatch, running_job_registry, ...]

# 查看 Leader 信息
zookeeper-shell localhost:2181 get /flink/realtime-pipeline/leader

# 退出容器
exit
```


### 步骤 6: 提交测试作业

#### 6.1 编译应用程序

```bash
# 编译项目
mvn clean package -DskipTests

# 验证 JAR 文件
ls -lh target/realtime-data-pipeline-*.jar
```

#### 6.2 提交作业到集群

```bash
# 方法 1: 使用 Flink CLI（在 JobManager 容器内）
docker exec flink-jobmanager flink run \
  -d \
  /opt/flink/lib/realtime-data-pipeline-1.0.0-SNAPSHOT.jar

# 方法 2: 使用 REST API
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"entryClass":"com.realtime.pipeline.FlinkCDC3App"}' \
  http://localhost:8081/jars/upload

# 方法 3: 使用 Web UI
# 1. 访问 http://localhost:8081
# 2. 点击 "Submit New Job"
# 3. 上传 JAR 文件
# 4. 选择 Entry Class: com.realtime.pipeline.FlinkCDC3App
# 5. 点击 "Submit"
```

#### 6.3 查看作业状态

```bash
# 列出所有作业
curl -s http://localhost:8081/jobs | python3 -c "
import sys, json
data = json.load(sys.stdin)
for job in data.get('jobs', []):
    print(f'作业 ID: {job[\"id\"]}')
    print(f'状态: {job[\"status\"]}')
"
```

#### 6.4 查看作业详情

```bash
# 获取作业 ID
JOB_ID=$(curl -s http://localhost:8081/jobs | python3 -c "
import sys, json
data = json.load(sys.stdin)
jobs = data.get('jobs', [])
if jobs:
    print(jobs[0]['id'])
")

echo "作业 ID: $JOB_ID"

# 查看作业详情
curl -s http://localhost:8081/jobs/$JOB_ID | python3 -c "
import sys, json
data = json.load(sys.stdin)
print(f'作业名称: {data.get(\"name\", \"N/A\")}')
print(f'状态: {data.get(\"state\", \"N/A\")}')
print(f'开始时间: {data.get(\"start-time\", \"N/A\")}')
print(f'运行时长: {data.get(\"duration\", 0) // 1000} 秒')
"
```

#### 6.5 验证 CSV 输出

```bash
# 等待几分钟让作业生成数据
sleep 120

# 检查输出目录
ls -lh output/cdc/

# 查看最新的 CSV 文件
find output/cdc -name "*.csv" -type f -mmin -5 | head -5

# 查看 CSV 文件内容
head -10 $(find output/cdc -name "*.csv" -type f -mmin -5 | head -1)
```


---

## 故障测试

### 步骤 7: 测试 JobManager 故障转移

#### 7.1 记录当前 Leader

```bash
# 查看当前 Leader
curl -s http://localhost:8081/overview | python3 -c "
import sys, json
data = json.load(sys.stdin)
print('当前 Leader: JobManager 主节点 (端口 8081)')
"

# 或查看日志
docker-compose logs jobmanager | grep -i "leader" | tail -5
```

#### 7.2 模拟 Leader 故障

```bash
# 停止主 JobManager
docker-compose stop jobmanager

echo "主 JobManager 已停止，等待故障转移..."
sleep 30
```

#### 7.3 验证自动故障转移

```bash
# 检查备用 JobManager 是否接管
docker-compose logs jobmanager-standby | grep -i "leader" | tail -10

# 应该看到类似输出：
# ... Granted leadership with session ID ...
# ... Successfully registered at the ResourceManager ...
```

#### 7.4 访问新的 Leader

```bash
# 现在备用 JobManager 成为 Leader
# 访问 http://localhost:8082

# 检查作业状态
curl -s http://localhost:8082/jobs | python3 -c "
import sys, json
data = json.load(sys.stdin)
for job in data.get('jobs', []):
    print(f'作业 ID: {job[\"id\"]}')
    print(f'状态: {job[\"status\"]}')
"
```

#### 7.5 验证作业继续运行

```bash
# 检查 CSV 文件是否继续生成
watch -n 5 'ls -lht output/cdc/ | head -10'

# 按 Ctrl+C 退出

# 或使用命令
find output/cdc -name "*.csv" -type f -mmin -2
```

#### 7.6 恢复主 JobManager

```bash
# 重新启动主 JobManager
docker-compose start jobmanager

echo "主 JobManager 已重启，等待加入集群..."
sleep 30

# 检查状态
docker-compose ps jobmanager

# 查看日志
docker-compose logs jobmanager | grep -i "standby" | tail -5

# 现在主 JobManager 变成 Standby
```


### 步骤 8: 测试 TaskManager 故障

#### 8.1 查看当前 TaskManager

```bash
# 列出所有 TaskManager
curl -s http://localhost:8082/taskmanagers | python3 -c "
import sys, json
data = json.load(sys.stdin)
for tm in data.get('taskmanagers', []):
    print(f'TaskManager ID: {tm[\"id\"]}')
    print(f'  槽位: {tm[\"slotsNumber\"]}')
    print(f'  可用: {tm[\"freeSlots\"]}')
    print()
"
```

#### 8.2 停止一个 TaskManager

```bash
# 停止第一个 TaskManager
docker-compose stop realtime-pipeline-taskmanager-1

echo "TaskManager-1 已停止，等待作业重新调度..."
sleep 30
```

#### 8.3 验证作业自动恢复

```bash
# 检查作业状态
JOB_ID=$(curl -s http://localhost:8082/jobs | python3 -c "
import sys, json
data = json.load(sys.stdin)
jobs = data.get('jobs', [])
if jobs:
    print(jobs[0]['id'])
")

curl -s http://localhost:8082/jobs/$JOB_ID | python3 -c "
import sys, json
data = json.load(sys.stdin)
print(f'作业状态: {data.get(\"state\", \"N/A\")}')
"

# 应该显示: 作业状态: RUNNING
```

#### 8.4 查看剩余 TaskManager

```bash
# 查看当前 TaskManager 数量
curl -s http://localhost:8082/overview | python3 -c "
import sys, json
data = json.load(sys.stdin)
print(f'TaskManagers: {data.get(\"taskmanagers\", 0)}')
print(f'总槽位: {data.get(\"slots-total\", 0)}')
print(f'可用槽位: {data.get(\"slots-available\", 0)}')
"

# 应该显示: TaskManagers: 2
```

#### 8.5 恢复 TaskManager

```bash
# 重新启动 TaskManager
docker-compose start realtime-pipeline-taskmanager-1

# 或者启动新的 TaskManager
docker-compose up -d --scale taskmanager=3

echo "TaskManager 已恢复，等待加入集群..."
sleep 30

# 验证
docker-compose ps | grep taskmanager
```


---

## 监控和维护

### 步骤 9: 日常监控

#### 9.1 创建监控脚本

```bash
# 创建监控脚本
cat > monitor-ha-cluster.sh << 'EOF'
#!/bin/bash

echo "=== Flink HA 集群监控 ==="
echo "时间: $(date)"
echo ""

# 检查容器状态
echo "1. 容器状态:"
docker-compose ps --format "table {{.Name}}\t{{.Status}}" | grep -E "zookeeper|jobmanager|taskmanager"
echo ""

# 检查 Leader
echo "2. JobManager Leader:"
LEADER_PORT=""
if curl -s http://localhost:8081/overview >/dev/null 2>&1; then
    echo "   Leader: JobManager 主节点 (端口 8081)"
    LEADER_PORT="8081"
elif curl -s http://localhost:8082/overview >/dev/null 2>&1; then
    echo "   Leader: JobManager 备节点 (端口 8082)"
    LEADER_PORT="8082"
else
    echo "   错误: 无法连接到任何 JobManager"
    exit 1
fi
echo ""

# 检查集群资源
echo "3. 集群资源:"
curl -s http://localhost:$LEADER_PORT/overview | python3 -c "
import sys, json
data = json.load(sys.stdin)
print(f'   TaskManagers: {data.get(\"taskmanagers\", 0)}')
print(f'   总槽位: {data.get(\"slots-total\", 0)}')
print(f'   可用槽位: {data.get(\"slots-available\", 0)}')
print(f'   运行作业: {data.get(\"jobs-running\", 0)}')
"
echo ""

# 检查作业状态
echo "4. 作业状态:"
curl -s http://localhost:$LEADER_PORT/jobs | python3 -c "
import sys, json
data = json.load(sys.stdin)
jobs = data.get('jobs', [])
if not jobs:
    print('   无运行作业')
else:
    for job in jobs:
        print(f'   作业 ID: {job[\"id\"]}')
        print(f'   状态: {job[\"status\"]}')
"
echo ""

# 检查最近的 CSV 文件
echo "5. 最近生成的 CSV 文件:"
find output/cdc -name "*.csv" -type f -mmin -5 2>/dev/null | head -3 | while read file; do
    echo "   $file ($(ls -lh "$file" | awk '{print $5}'))"
done
echo ""

echo "=== 监控完成 ==="
EOF

chmod +x monitor-ha-cluster.sh
```

#### 9.2 运行监控脚本

```bash
# 手动运行
./monitor-ha-cluster.sh

# 或设置定时任务（每 5 分钟）
# crontab -e
# */5 * * * * /path/to/monitor-ha-cluster.sh >> /path/to/monitor.log 2>&1
```

#### 9.3 查看实时日志

```bash
# 查看所有容器日志
docker-compose logs -f

# 只查看 JobManager 日志
docker-compose logs -f jobmanager jobmanager-standby

# 只查看 TaskManager 日志
docker-compose logs -f taskmanager

# 查看最近 100 行日志
docker-compose logs --tail 100 jobmanager
```

#### 9.4 检查 Checkpoint

```bash
# 查看 Checkpoint 目录
ls -lh flink-checkpoints/

# 查看 HA 存储目录
docker exec flink-jobmanager ls -lh /opt/flink/ha/

# 通过 API 查看 Checkpoint 统计
JOB_ID=$(curl -s http://localhost:8081/jobs | python3 -c "
import sys, json
data = json.load(sys.stdin)
jobs = data.get('jobs', [])
if jobs:
    print(jobs[0]['id'])
")

curl -s http://localhost:8081/jobs/$JOB_ID/checkpoints | python3 -c "
import sys, json
data = json.load(sys.stdin)
latest = data.get('latest', {}).get('completed', {})
print(f'最新 Checkpoint ID: {latest.get(\"id\", \"N/A\")}')
print(f'完成时间: {latest.get(\"latest_ack_timestamp\", \"N/A\")}')
print(f'状态大小: {latest.get(\"state_size\", 0)} bytes')
"
```


### 步骤 10: 维护操作

#### 10.1 触发 Savepoint

```bash
# 获取作业 ID
JOB_ID=$(curl -s http://localhost:8081/jobs | python3 -c "
import sys, json
data = json.load(sys.stdin)
jobs = data.get('jobs', [])
if jobs:
    print(jobs[0]['id'])
")

# 触发 Savepoint
curl -X POST http://localhost:8081/jobs/$JOB_ID/savepoints \
  -H "Content-Type: application/json" \
  -d '{"target-directory": "/opt/flink/savepoints", "cancel-job": false}'

# 查看 Savepoint 状态
# 记录返回的 request-id，然后查询状态
REQUEST_ID="<从上面的响应中获取>"
curl -s http://localhost:8081/jobs/$JOB_ID/savepoints/$REQUEST_ID
```

#### 10.2 从 Savepoint 恢复

```bash
# 列出可用的 Savepoint
docker exec flink-jobmanager ls -lh /opt/flink/savepoints/

# 取消当前作业
curl -X PATCH http://localhost:8081/jobs/$JOB_ID \
  -H "Content-Type: application/json" \
  -d '{"state": "cancelled"}'

# 从 Savepoint 恢复
SAVEPOINT_PATH="/opt/flink/savepoints/<savepoint-id>"
docker exec flink-jobmanager flink run \
  -s $SAVEPOINT_PATH \
  -d \
  /opt/flink/lib/realtime-data-pipeline-1.0.0-SNAPSHOT.jar
```

#### 10.3 清理旧的 Checkpoint

```bash
# 查看 Checkpoint 占用空间
du -sh flink-checkpoints/

# 清理 7 天前的 Checkpoint（谨慎操作）
find flink-checkpoints/ -type d -mtime +7 -exec rm -rf {} \; 2>/dev/null

# 或者手动删除特定的 Checkpoint
docker exec flink-jobmanager rm -rf /opt/flink/checkpoints/<old-checkpoint-id>
```

#### 10.4 升级集群

```bash
# 1. 触发 Savepoint
# （参考 10.1）

# 2. 停止所有服务
docker-compose down

# 3. 更新代码或配置
git pull
mvn clean package -DskipTests

# 4. 重新构建镜像
docker-compose build

# 5. 启动集群
docker-compose up -d zookeeper
sleep 30
docker-compose up -d jobmanager jobmanager-standby
sleep 60
docker-compose up -d --scale taskmanager=3

# 6. 从 Savepoint 恢复作业
# （参考 10.2）
```

#### 10.5 备份关键数据

```bash
# 创建备份脚本
cat > backup-flink-ha.sh << 'EOF'
#!/bin/bash

BACKUP_DIR="./backups/$(date +%Y%m%d_%H%M%S)"
mkdir -p $BACKUP_DIR

echo "开始备份 Flink HA 数据..."

# 备份 Savepoints
echo "备份 Savepoints..."
docker cp flink-jobmanager:/opt/flink/savepoints $BACKUP_DIR/

# 备份 HA 元数据
echo "备份 HA 元数据..."
docker cp flink-jobmanager:/opt/flink/ha $BACKUP_DIR/

# 备份配置文件
echo "备份配置文件..."
cp docker-compose.yml $BACKUP_DIR/
cp .env $BACKUP_DIR/

# 压缩备份
echo "压缩备份..."
tar -czf $BACKUP_DIR.tar.gz $BACKUP_DIR
rm -rf $BACKUP_DIR

echo "备份完成: $BACKUP_DIR.tar.gz"
EOF

chmod +x backup-flink-ha.sh

# 运行备份
./backup-flink-ha.sh
```


---

## 常见问题

### Q1: JobManager 无法选举 Leader

**症状**: 两个 JobManager 都无法成为 Leader

**排查步骤**:

```bash
# 1. 检查 ZooKeeper 状态
docker-compose logs zookeeper | grep -i error

# 2. 检查 ZooKeeper 连接
docker exec zookeeper zookeeper-shell localhost:2181 ls /

# 3. 检查 HA 配置
docker exec flink-jobmanager env | grep HA_

# 4. 查看 JobManager 日志
docker-compose logs jobmanager | grep -i "zookeeper\|leader"
```

**解决方案**:

```bash
# 重启 ZooKeeper
docker-compose restart zookeeper
sleep 30

# 重启 JobManager
docker-compose restart jobmanager jobmanager-standby
```

### Q2: 故障转移后作业无法恢复

**症状**: Leader 切换后，作业状态变为 FAILED

**排查步骤**:

```bash
# 1. 检查 Checkpoint 目录
ls -lh flink-checkpoints/

# 2. 检查作业异常
JOB_ID="<your-job-id>"
curl -s http://localhost:8082/jobs/$JOB_ID/exceptions

# 3. 查看 TaskManager 日志
docker-compose logs taskmanager | grep -i error
```

**解决方案**:

```bash
# 1. 确保 Checkpoint 目录可访问
docker exec flink-jobmanager ls -lh /opt/flink/checkpoints/

# 2. 检查 Checkpoint 配置
docker exec flink-jobmanager cat /opt/flink/conf/flink-conf.yaml | grep checkpoint

# 3. 如果需要，从 Savepoint 手动恢复
# （参考步骤 10.2）
```

### Q3: TaskManager 无法连接到新的 Leader

**症状**: Leader 切换后，TaskManager 显示离线

**排查步骤**:

```bash
# 1. 检查 TaskManager 日志
docker-compose logs taskmanager | grep -i "jobmanager\|connection"

# 2. 检查网络连接
docker exec realtime-pipeline-taskmanager-1 ping -c 3 jobmanager
docker exec realtime-pipeline-taskmanager-1 ping -c 3 jobmanager-standby

# 3. 检查 HA 配置
docker exec realtime-pipeline-taskmanager-1 env | grep HA_
```

**解决方案**:

```bash
# 重启 TaskManager
docker-compose restart taskmanager

# 或重新创建 TaskManager
docker-compose stop taskmanager
docker-compose up -d --scale taskmanager=3
```

### Q4: ZooKeeper 磁盘空间不足

**症状**: ZooKeeper 日志显示磁盘空间错误

**排查步骤**:

```bash
# 检查 ZooKeeper 数据大小
docker exec zookeeper du -sh /var/lib/zookeeper/

# 检查宿主机磁盘空间
df -h
```

**解决方案**:

```bash
# 1. 清理 ZooKeeper 快照（谨慎操作）
docker exec zookeeper bash -c "
cd /var/lib/zookeeper/version-2
ls -lt | tail -n +10 | awk '{print \$9}' | xargs rm -f
"

# 2. 或者重新创建 ZooKeeper（会丢失 HA 元数据）
docker-compose stop zookeeper
docker volume rm zookeeper-data zookeeper-logs
docker-compose up -d zookeeper
```

### Q5: 如何查看当前的 Leader

**方法 1: 通过 Web UI**

访问 http://localhost:8081 和 http://localhost:8082，能访问的就是 Leader

**方法 2: 通过命令行**

```bash
# 检查主 JobManager
if curl -s http://localhost:8081/overview >/dev/null 2>&1; then
    echo "Leader: JobManager 主节点 (端口 8081)"
else
    echo "主节点不是 Leader"
fi

# 检查备用 JobManager
if curl -s http://localhost:8082/overview >/dev/null 2>&1; then
    echo "Leader: JobManager 备节点 (端口 8082)"
else
    echo "备节点不是 Leader"
fi
```

**方法 3: 通过 ZooKeeper**

```bash
docker exec zookeeper zookeeper-shell localhost:2181 get /flink/realtime-pipeline/leader
```


### Q6: 如何完全重置 HA 集群

**场景**: 需要从头开始重新部署

**步骤**:

```bash
# 1. 停止所有容器
docker-compose down

# 2. 删除所有 volumes
docker volume rm flink-checkpoints flink-savepoints flink-ha flink-logs flink-data
docker volume rm zookeeper-data zookeeper-logs

# 3. 清理输出目录（可选）
rm -rf output/cdc/*

# 4. 重新创建 volumes
docker volume create flink-checkpoints
docker volume create flink-savepoints
docker volume create flink-ha
docker volume create flink-logs
docker volume create flink-data
docker volume create zookeeper-data
docker volume create zookeeper-logs

# 5. 重新启动集群
docker-compose up -d zookeeper
sleep 30
docker-compose up -d jobmanager jobmanager-standby
sleep 60
docker-compose up -d --scale taskmanager=3

# 6. 验证集群状态
./monitor-ha-cluster.sh
```

### Q7: 性能优化建议

**增加 JobManager 内存**:

编辑 `.env` 文件：
```bash
JOB_MANAGER_HEAP_SIZE=2048m  # 从 1024m 增加到 2048m
```

**增加 TaskManager 资源**:

编辑 `docker-compose.yml`：
```yaml
taskmanager:
  environment:
    - TASK_MANAGER_HEAP_SIZE=2048m
    - TASK_MANAGER_MEMORY_PROCESS_SIZE=3456m
  deploy:
    resources:
      limits:
        cpus: '4.0'
        memory: 4G
```

**调整 Checkpoint 间隔**:

编辑 `.env` 文件：
```bash
CHECKPOINT_INTERVAL=180000  # 从 5 分钟改为 3 分钟
```

**增加 TaskManager 数量**:

```bash
docker-compose up -d --scale taskmanager=5
```

---

## 附录

### A. 完整的环境变量列表

```bash
# Flink 高可用配置
HA_MODE=zookeeper
HA_ZOOKEEPER_QUORUM=zookeeper:2181
HA_ZOOKEEPER_PATH_ROOT=/flink
HA_CLUSTER_ID=/realtime-pipeline
HA_STORAGE_DIR=file:///opt/flink/ha

# JobManager 配置
JOB_MANAGER_HEAP_SIZE=1024m
REST_PORT=8081
PARALLELISM_DEFAULT=4

# TaskManager 配置
TASK_MANAGER_HEAP_SIZE=1024m
TASK_MANAGER_MEMORY_PROCESS_SIZE=1728m
TASK_MANAGER_MEMORY_MANAGED_SIZE=512m
TASK_MANAGER_NUMBER_OF_TASK_SLOTS=4

# Checkpoint 配置
CHECKPOINT_INTERVAL=300000
CHECKPOINT_DIR=file:///opt/flink/checkpoints
SAVEPOINT_DIR=file:///opt/flink/savepoints
STATE_BACKEND=hashmap

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
```

### B. 常用命令速查

```bash
# 启动集群
docker-compose up -d zookeeper
docker-compose up -d jobmanager jobmanager-standby
docker-compose up -d --scale taskmanager=3

# 停止集群
docker-compose down

# 查看状态
docker-compose ps
./monitor-ha-cluster.sh

# 查看日志
docker-compose logs -f jobmanager
docker-compose logs -f taskmanager

# 重启服务
docker-compose restart jobmanager
docker-compose restart taskmanager

# 扩展 TaskManager
docker-compose up -d --scale taskmanager=5

# 触发 Savepoint
curl -X POST http://localhost:8081/jobs/<job-id>/savepoints \
  -H "Content-Type: application/json" \
  -d '{"target-directory": "/opt/flink/savepoints"}'

# 查看作业
curl -s http://localhost:8081/jobs

# 取消作业
curl -X PATCH http://localhost:8081/jobs/<job-id> \
  -H "Content-Type: application/json" \
  -d '{"state": "cancelled"}'
```

### C. 相关文档

- [Flink 官方文档 - 高可用](https://nightlies.apache.org/flink/flink-docs-stable/docs/deployment/ha/overview/)
- [ZooKeeper 官方文档](https://zookeeper.apache.org/doc/current/)
- [项目主 README](../README.md)
- [Docker 部署指南](../docker/README.md)
- [TaskManager 扩展指南](../md/TASKMANAGER_SCALING.md)

---

## 总结

通过本手册，你已经学会了：

✅ **部署 Flink 高可用集群**
- 配置 ZooKeeper
- 启动多个 JobManager
- 配置 TaskManager

✅ **验证高可用功能**
- 检查 Leader 状态
- 提交测试作业
- 验证数据输出

✅ **测试故障转移**
- JobManager 故障转移
- TaskManager 故障恢复
- 自动状态恢复

✅ **日常监控和维护**
- 监控集群状态
- 管理 Checkpoint/Savepoint
- 备份关键数据

现在你的 Flink CDC 系统已经具备了生产级别的高可用能力！

---

**文档版本**: 1.0.0  
**最后更新**: 2026-02-26  
**维护者**: Kiro AI Assistant  
**反馈**: 如有问题，请查看常见问题部分或查阅相关文档
