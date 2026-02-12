# Docker部署指南

本目录包含实时数据管道系统的Docker镜像定义和配置文件。

## 目录结构

```
docker/
├── jobmanager/          # Flink JobManager镜像
│   ├── Dockerfile
│   ├── flink-conf.yaml
│   └── log4j.properties
├── taskmanager/         # Flink TaskManager镜像
│   ├── Dockerfile
│   ├── flink-conf.yaml
│   └── log4j.properties
├── cdc-collector/       # CDC采集器镜像
│   ├── Dockerfile
│   ├── application.yml
│   ├── log4j2.xml
│   └── entrypoint.sh
└── README.md
```

## 镜像说明

### 1. Flink JobManager

**需求**: 8.1 - 提供Flink JobManager的Docker镜像

**功能**:
- Flink集群的主节点
- 负责作业调度和协调
- 提供Web UI（端口8081）
- 支持高可用配置

**端口**:
- 6123: RPC通信端口
- 8081: Web UI端口
- 6124: Blob服务器端口
- 9249: Prometheus metrics端口

### 2. Flink TaskManager

**需求**: 8.2 - 提供Flink TaskManager的Docker镜像

**功能**:
- Flink集群的工作节点
- 执行实际的数据处理任务
- 支持动态扩缩容

**端口**:
- 6121: Data交换端口
- 6122: RPC通信端口
- 8080: Metrics端口
- 9249: Prometheus metrics端口

### 3. CDC Collector

**需求**: 8.3 - 提供数据采集组件的Docker镜像

**功能**:
- 从OceanBase数据库捕获变更数据
- 发送变更数据到DataHub
- 支持自动重连和重试

**端口**:
- 8080: 健康检查和监控端口

## 构建镜像

### 前置条件

1. 构建应用程序JAR包:
```bash
mvn clean package -DskipTests
```

2. 确保JAR包存在于`target/`目录:
```bash
ls -lh target/realtime-data-pipeline-*.jar
```

### 构建所有镜像

```bash
# 构建JobManager镜像
docker build -f docker/jobmanager/Dockerfile -t realtime-pipeline/jobmanager:1.0.0 .

# 构建TaskManager镜像
docker build -f docker/taskmanager/Dockerfile -t realtime-pipeline/taskmanager:1.0.0 .

# 构建CDC Collector镜像
docker build -f docker/cdc-collector/Dockerfile -t realtime-pipeline/cdc-collector:1.0.0 .
```

### 验证镜像

```bash
docker images | grep realtime-pipeline
```

## 运行容器

### 1. 运行JobManager

```bash
docker run -d \
  --name flink-jobmanager \
  -p 8081:8081 \
  -p 6123:6123 \
  -e JOB_MANAGER_RPC_ADDRESS=jobmanager \
  -v /path/to/checkpoints:/opt/flink/checkpoints \
  -v /path/to/savepoints:/opt/flink/savepoints \
  realtime-pipeline/jobmanager:1.0.0
```

### 2. 运行TaskManager

```bash
docker run -d \
  --name flink-taskmanager \
  --link flink-jobmanager:jobmanager \
  -e JOB_MANAGER_RPC_ADDRESS=jobmanager \
  -v /path/to/checkpoints:/opt/flink/checkpoints \
  -v /path/to/data:/opt/flink/data \
  realtime-pipeline/taskmanager:1.0.0
```

### 3. 运行CDC Collector

```bash
docker run -d \
  --name cdc-collector \
  -p 8080:8080 \
  -e DATABASE_HOST=oceanbase-host \
  -e DATABASE_PORT=2881 \
  -e DATABASE_USERNAME=root \
  -e DATABASE_PASSWORD=password \
  -e DATABASE_SCHEMA=mydb \
  -e DATAHUB_ENDPOINT=https://datahub.aliyuncs.com \
  -e DATAHUB_ACCESS_ID=your-access-id \
  -e DATAHUB_ACCESS_KEY=your-access-key \
  -e DATAHUB_PROJECT=realtime-pipeline \
  -e DATAHUB_TOPIC=cdc-events \
  realtime-pipeline/cdc-collector:1.0.0
```

## 环境变量配置

### JobManager环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| JOB_MANAGER_RPC_ADDRESS | JobManager RPC地址 | localhost |
| JOB_MANAGER_RPC_PORT | JobManager RPC端口 | 6123 |
| JOB_MANAGER_HEAP_SIZE | JobManager堆内存 | 1024m |
| REST_PORT | REST API端口 | 8081 |
| BLOB_SERVER_PORT | Blob服务器端口 | 6124 |
| PARALLELISM_DEFAULT | 默认并行度 | 4 |
| CHECKPOINT_INTERVAL | Checkpoint间隔（毫秒） | 300000 |
| CHECKPOINT_DIR | Checkpoint目录 | file:///opt/flink/checkpoints |
| SAVEPOINT_DIR | Savepoint目录 | file:///opt/flink/savepoints |
| STATE_BACKEND | 状态后端类型 | hashmap |
| HA_MODE | 高可用模式 | NONE |
| HA_ZOOKEEPER_QUORUM | ZooKeeper集群地址 | - |
| HA_CLUSTER_ID | 集群ID | /default |
| EXTRA_JAVA_OPTS | 额外的Java选项 | - |

### TaskManager环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| JOB_MANAGER_RPC_ADDRESS | JobManager RPC地址 | jobmanager |
| JOB_MANAGER_RPC_PORT | JobManager RPC端口 | 6123 |
| TASK_MANAGER_HEAP_SIZE | TaskManager堆内存 | 1024m |
| TASK_MANAGER_MEMORY_PROCESS_SIZE | 进程总内存 | 1728m |
| TASK_MANAGER_MEMORY_MANAGED_SIZE | 托管内存 | 512m |
| TASK_MANAGER_NUMBER_OF_TASK_SLOTS | 任务槽数量 | 4 |
| TASK_MANAGER_RPC_PORT | RPC端口 | 6122 |
| TASK_MANAGER_DATA_PORT | 数据交换端口 | 6121 |
| TASK_MANAGER_NETWORK_MEMORY_MIN | 网络内存最小值 | 64m |
| TASK_MANAGER_NETWORK_MEMORY_MAX | 网络内存最大值 | 256m |
| EXTRA_JAVA_OPTS | 额外的Java选项 | - |

### CDC Collector环境变量

**必需变量**:
- `DATABASE_HOST`: OceanBase数据库主机地址
- `DATAHUB_ENDPOINT`: DataHub服务端点
- `DATAHUB_ACCESS_ID`: DataHub访问ID
- `DATAHUB_ACCESS_KEY`: DataHub访问密钥

**可选变量**:
- `DATABASE_PORT`: 数据库端口（默认: 2881）
- `DATABASE_USERNAME`: 数据库用户名（默认: root）
- `DATABASE_PASSWORD`: 数据库密码（默认: password）
- `DATABASE_SCHEMA`: 数据库Schema（默认: test）
- `DATABASE_TABLES`: 监控的表列表（默认: *）
- `DATAHUB_PROJECT`: DataHub项目名称（默认: realtime-pipeline）
- `DATAHUB_TOPIC`: DataHub主题名称（默认: cdc-events）
- `DATAHUB_CONSUMER_GROUP`: 消费者组名称（默认: cdc-collector-group）
- `RETRY_MAX_ATTEMPTS`: 最大重试次数（默认: 3）
- `RETRY_BACKOFF_MS`: 重试间隔毫秒（默认: 2000）
- `MONITORING_PORT`: 监控端口（默认: 8080）
- `LOG_LEVEL`: 日志级别（默认: INFO）
- `HEAP_SIZE`: 堆内存大小（默认: 512m）
- `EXTRA_JAVA_OPTS`: 额外的Java选项

## 健康检查

所有容器都配置了健康检查（需求 8.8）和自动重启策略（需求 4.5）。

### 健康检查配置

每个容器都配置了Docker HEALTHCHECK指令:

**JobManager健康检查**:
```bash
# 检查Web UI是否响应
curl -f http://localhost:8081/overview
```

**TaskManager健康检查**:
```bash
# 检查进程是否运行
pgrep -f "org.apache.flink.runtime.taskexecutor.TaskManagerRunner"
```

**CDC Collector健康检查**:
```bash
# 使用健康检查端点
curl -f http://localhost:8080/health/live
```

### 健康检查参数

所有容器使用相同的健康检查参数:
- `interval`: 30秒 - 每30秒检查一次
- `timeout`: 10秒 - 单次检查超时时间
- `start-period`: 60秒 - 启动后60秒开始检查（满足需求8.5）
- `retries`: 3次 - 连续失败3次后标记为不健康

### 查看健康状态

```bash
# 查看所有容器健康状态
docker ps

# 查看特定容器的健康检查详情
docker inspect --format='{{json .State.Health}}' flink-jobmanager | jq

# 使用测试脚本
./docker/test-health-checks.sh
```

### CDC Collector健康检查端点

CDC Collector提供多个健康检查端点:

1. **`/health`** - 基本健康检查
   ```bash
   curl http://localhost:8080/health
   ```
   返回系统整体健康状态和关键指标

2. **`/health/live`** - 存活探测（Liveness Probe）
   ```bash
   curl http://localhost:8080/health/live
   ```
   检查应用是否还在运行

3. **`/health/ready`** - 就绪探测（Readiness Probe）
   ```bash
   curl http://localhost:8080/health/ready
   ```
   检查应用是否准备好接收流量

4. **`/metrics`** - 详细指标
   ```bash
   curl http://localhost:8080/metrics
   ```
   返回所有系统指标

### 自动重启策略

所有容器配置了自动重启策略（需求 4.5）:

```bash
# 使用unless-stopped策略
docker run -d --restart=unless-stopped ...
```

**重启策略说明**:
- `unless-stopped`: 除非手动停止，否则总是重启
- 容器崩溃后立即重启（<66秒，满足2分钟要求）
- Docker守护进程重启时会重启容器

**测试自动重启**:
```bash
# 模拟容器崩溃
docker exec flink-jobmanager pkill -9 java

# 观察容器自动重启
docker ps -a
docker logs -f flink-jobmanager
```

详细信息请参考: [docs/TASK_14.3_HEALTH_CHECK_CONFIG.md](../docs/TASK_14.3_HEALTH_CHECK_CONFIG.md)

## 容器启动时间

根据需求 8.5，所有容器应在60秒内完成初始化:

- JobManager: ~30秒
- TaskManager: ~30秒
- CDC Collector: ~20秒

## 故障恢复

根据需求 4.5，容器崩溃时会自动重启:

```bash
# 配置自动重启策略
docker run -d --restart=unless-stopped ...
```

## 日志管理

### 查看容器日志

```bash
# JobManager日志
docker logs -f flink-jobmanager

# TaskManager日志
docker logs -f flink-taskmanager

# CDC Collector日志
docker logs -f cdc-collector
```

### 日志文件位置

- JobManager: `/opt/flink/logs/`
- TaskManager: `/opt/flink/logs/`
- CDC Collector: `/opt/cdc-collector/logs/`

## 数据持久化

### 推荐的卷挂载

```bash
# Checkpoint数据
-v /data/checkpoints:/opt/flink/checkpoints

# Savepoint数据
-v /data/savepoints:/opt/flink/savepoints

# 输出数据
-v /data/output:/opt/flink/data

# 日志数据
-v /data/logs:/opt/flink/logs
```

## 监控

### Prometheus Metrics

JobManager和TaskManager都暴露Prometheus metrics端口（9249）:

```bash
curl http://localhost:9249/metrics
```

### Flink Web UI

访问JobManager的Web UI:
```
http://localhost:8081
```

## 故障排查

### 容器无法启动

1. 检查日志:
```bash
docker logs <container-name>
```

2. 检查环境变量:
```bash
docker inspect <container-name> | grep -A 20 Env
```

3. 检查网络连接:
```bash
docker network inspect bridge
```

### 健康检查失败

1. 进入容器:
```bash
docker exec -it <container-name> bash
```

2. 手动执行健康检查命令

3. 检查应用日志

### 性能问题

1. 检查资源使用:
```bash
docker stats
```

2. 调整内存配置:
```bash
-e JOB_MANAGER_HEAP_SIZE=2048m
-e TASK_MANAGER_HEAP_SIZE=4096m
```

## 安全建议

1. **不要在镜像中硬编码敏感信息**
   - 使用环境变量传递密码和密钥
   - 使用Docker secrets或配置管理工具

2. **使用非root用户运行**
   - JobManager和TaskManager使用`flink`用户
   - CDC Collector使用`cdcuser`用户

3. **限制容器资源**
```bash
docker run -d \
  --memory=2g \
  --cpus=2 \
  ...
```

4. **使用私有镜像仓库**
```bash
docker tag realtime-pipeline/jobmanager:1.0.0 registry.example.com/realtime-pipeline/jobmanager:1.0.0
docker push registry.example.com/realtime-pipeline/jobmanager:1.0.0
```

## 下一步

- 查看 `docker-compose.yml` 了解如何使用Docker Compose部署完整系统
- 查看主README了解系统架构和配置详情
- 查看部署文档了解生产环境部署最佳实践
