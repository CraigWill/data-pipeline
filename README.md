# 实时数据管道系统 (Realtime Data Pipeline)

基于 Apache Flink 的流处理平台，使用 Flink CDC 3.x 从 Oracle 数据库实时捕获变更数据（CDC），通过 Kafka 进行数据传输，使用 Flink 进行流处理，最终输出到文件系统。

## 快速启动

### 1. 启动 Docker 容器
```bash
docker-compose up -d
```

### 2. 启动 Flink CDC 作业
```bash
./shell/start-flink-cdc.sh
```

### 3. 检查作业状态
```bash
./shell/check-cdc-status.sh
```

### 4. 访问 Web UI
- Flink Web UI: http://localhost:8081
- Kafka UI: http://localhost:8082

## 目录

- [项目介绍](#项目介绍)
- [系统架构](#系统架构)
- [核心特性](#核心特性)
- [技术栈](#技术栈)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [部署指南](#部署指南)
- [监控和运维](#监控和运维)
- [常见问题](#常见问题)
- [开发指南](#开发指南)

## 项目介绍

实时数据管道系统是一个企业级的流处理解决方案，设计支持500亿级别的数据处理。系统采用DataHub + Flink的两层架构，通过Docker容器化部署，提供高可用、高性能、易运维的实时数据处理能力。

### 核心特性

- **高可用性**: 支持JobManager高可用配置，主备自动切换，系统可用性达99.9%
- **容错性**: 基于Flink Checkpoint机制，保证数据不丢失，支持故障自动恢复
- **可扩展性**: 支持动态扩缩容，水平扩展处理能力，支持至少100个并行任务
- **数据一致性**: 支持至少一次和精确一次语义，保证数据准确性
- **可观测性**: 完整的监控指标和告警机制，实时掌握系统运行状态
- **容器化部署**: 采用Docker容器化，60秒内完成启动，简化部署和运维

## 系统架构

### 整体架构

```
┌─────────────┐      ┌──────────────┐      ┌─────────────┐      ┌──────────────┐
│  OceanBase  │─CDC─>│ CDC Collector│─────>│   DataHub   │─────>│    Flink     │
│   Oracle    │      │  Container   │      │   Topic     │      │   Cluster    │
└─────────────┘      └──────────────┘      └─────────────┘      └──────────────┘
                                                                         │
                                                                         ▼
                                                                  ┌──────────────┐
                                                                  │ File System  │
                                                                  │ JSON/Parquet │
                                                                  │     /CSV     │
                                                                  └──────────────┘
```

### 数据流

1. **CDC采集**: CDC Collector从OceanBase捕获INSERT/UPDATE/DELETE变更事件
2. **数据传输**: 变更数据通过DataHub进行可靠传输
3. **流处理**: Flink集群消费DataHub数据，进行实时处理和转换
4. **数据输出**: 处理后的数据写入文件系统，支持多种格式

### 部署架构

```
Docker容器集群
├── CDC Collector (1个容器)
│   └── 端口: 8080 (健康检查和监控)
├── Flink JobManager (1-2个容器，支持HA)
│   ├── 端口: 8081 (Web UI)
│   ├── 端口: 6123 (RPC)
│   └── 端口: 9249 (Metrics)
└── Flink TaskManager (N个容器，支持动态扩展)
    ├── 端口: 6121 (数据交换)
    └── 端口: 6122 (RPC)
```

## 技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| 流处理引擎 | Apache Flink | 1.18.0 |
| CDC连接器 | Flink CDC Connector | OceanBase |
| 消息队列 | 阿里云DataHub | - |
| 数据库 | OceanBase | Oracle模式 |
| 日志框架 | Log4j2 | 2.20.0 |
| 构建工具 | Maven | 3.6+ |
| 容器化 | Docker | 20.10+ |
| 编排工具 | Docker Compose | 2.0+ |
| 测试框架 | JUnit 5, jqwik | 5.10.0 |
| 编程语言 | Java | 11+ |

## 项目结构

```
realtime-data-pipeline/
├── src/                             # 源代码目录
│   ├── main/
│   │   ├── java/
│   │   │   └── com/realtime/pipeline/
│   │   │       ├── config/          # 配置类
│   │   │       │   ├── PipelineConfig.java
│   │   │       │   ├── DatabaseConfig.java
│   │   │       │   ├── DataHubConfig.java
│   │   │       │   ├── FlinkConfig.java
│   │   │       │   ├── OutputConfig.java
│   │   │       │   └── MonitoringConfig.java
│   │   │       ├── model/           # 数据模型
│   │   │       │   ├── ChangeEvent.java
│   │   │       │   ├── ProcessedEvent.java
│   │   │       │   ├── CollectorMetrics.java
│   │   │       │   └── JobStatus.java
│   │   │       └── util/            # 工具类
│   │   │           ├── ConfigLoader.java
│   │   │           ├── IdGenerator.java
│   │   │           └── RetryUtil.java
│   │   └── resources/
│   │       ├── application.yml      # 配置文件模板
│   │       └── log4j2.xml          # 日志配置
│   └── test/
│       └── java/                    # 测试代码
├── docs/                            # 项目文档
│   ├── DEPLOYMENT.md               # 部署指南
│   ├── DEVELOPMENT.md              # 开发指南
│   └── ...
├── docker/                          # Docker 配置
│   ├── jobmanager/                 # JobManager 配置
│   ├── taskmanager/                # TaskManager 配置
│   └── cdc-collector/              # CDC Collector 配置
├── shell/                           # Shell 脚本目录 ⭐ 新增
│   ├── README.md                   # 脚本使用说明
│   ├── restart-flink-cdc-job.sh   # 重启作业
│   ├── quick-test-cdc.sh          # 快速测试
│   ├── check-cdc-status.sh        # 检查状态
│   └── ...                         # 其他运维脚本
├── md/                              # Markdown 文档目录 ⭐ 新增
│   ├── README.md                   # 文档索引
│   ├── CURRENT_CDC_STATUS.md      # 当前状态报告
│   ├── CDC_*.md                    # CDC 相关文档
│   ├── CSV_*.md                    # CSV 相关文档
│   └── ...                         # 其他状态文档
├── sql/                             # SQL 脚本目录 ⭐ 新增
│   ├── README.md                   # SQL 脚本说明
│   ├── setup-oracle-cdc.sql       # Oracle CDC 配置
│   ├── enable-archivelog.sql      # 启用归档日志
│   ├── configure-oracle-for-cdc.sql # CDC 配置
│   └── ...                         # 其他 SQL 脚本
├── output/                          # 输出目录
│   └── cdc/                        # CDC 输出文件
├── docker-compose.yml              # Docker Compose 配置
├── pom.xml                         # Maven 配置
└── README.md                       # 项目文档（本文件）
```

### 目录说明

- **shell/**: 包含所有运维和管理脚本，从根目录执行 `./shell/script-name.sh`
- **md/**: 包含所有状态报告和问题解决文档，便于查阅和维护
- **sql/**: 包含所有 SQL 配置和测试脚本，便于数据库操作
- **docs/**: 项目技术文档和设计文档
- **docker/**: Docker 容器配置文件
- **output/**: 系统输出目录，包含生成的 CSV 文件

## 核心组件

### 1. 数据模型

- **ChangeEvent**: 变更事件，表示从数据库捕获的CDC事件
- **ProcessedEvent**: 处理后事件，表示经过Flink处理后的数据
- **CollectorMetrics**: 采集指标，记录CDC采集器的运行状态
- **JobStatus**: 作业状态，记录Flink作业的运行信息

### 2. 配置管理

- **PipelineConfig**: 顶层配置，包含所有子系统配置
- **DatabaseConfig**: 数据库连接配置
- **DataHubConfig**: DataHub连接配置
- **FlinkConfig**: Flink执行环境配置
- **OutputConfig**: 文件输出配置
- **MonitoringConfig**: 监控和告警配置

### 3. 工具类

- **ConfigLoader**: 配置加载器，支持从YAML文件加载配置并通过环境变量覆盖
- **IdGenerator**: ID生成器，用于生成唯一标识符
- **RetryUtil**: 重试工具，提供通用的重试机制

## 配置说明

系统配置通过`application.yml`文件和环境变量管理。环境变量优先级高于配置文件。

### 配置文件结构

```yaml
database:
  host: localhost
  port: 2881
  username: root
  password: password
  schema: test
  tables: "*"

datahub:
  endpoint: https://dh-cn-hangzhou.aliyuncs.com
  accessId: your-access-id
  accessKey: your-access-key
  project: realtime-pipeline
  topic: cdc-events
  consumerGroup: cdc-collector-group

flink:
  parallelism: 4
  checkpoint:
    interval: 300000  # 5分钟
    timeout: 600000   # 10分钟
    minPause: 60000   # 1分钟
    maxConcurrent: 1
    retainedNumber: 3
  stateBackend:
    type: hashmap
    checkpointDir: file:///opt/flink/checkpoints

output:
  path: /opt/flink/data
  format: json  # json, parquet, csv
  rolling:
    sizeBytes: 1073741824  # 1GB
    intervalMs: 3600000    # 1小时
  compression: none  # none, gzip, snappy
  maxRetries: 3

monitoring:
  port: 8080
  metrics:
    enabled: true
  alerts:
    latencyThresholdMs: 60000
    checkpointFailureRate: 0.1
    loadThreshold: 0.8
```

### 环境变量覆盖

环境变量格式: `PIPELINE_<SECTION>_<KEY>` 或直接使用大写的配置项名称

**数据库配置**:
- `DATABASE_HOST`: 数据库主机地址
- `DATABASE_PORT`: 数据库端口（默认: 2881）
- `DATABASE_USERNAME`: 数据库用户名
- `DATABASE_PASSWORD`: 数据库密码
- `DATABASE_SCHEMA`: 数据库Schema
- `DATABASE_TABLES`: 监控的表列表，逗号分隔或使用`*`

**DataHub配置**:
- `DATAHUB_ENDPOINT`: DataHub服务端点
- `DATAHUB_ACCESS_ID`: 访问ID（必需）
- `DATAHUB_ACCESS_KEY`: 访问密钥（必需）
- `DATAHUB_PROJECT`: 项目名称
- `DATAHUB_TOPIC`: 主题名称
- `DATAHUB_CONSUMER_GROUP`: 消费者组名称

**Flink配置**:
- `PARALLELISM_DEFAULT`: 默认并行度（默认: 4）
- `CHECKPOINT_INTERVAL`: Checkpoint间隔毫秒（默认: 300000）
- `CHECKPOINT_DIR`: Checkpoint存储目录
- `STATE_BACKEND`: 状态后端类型（hashmap/rocksdb）

**输出配置**:
- `OUTPUT_PATH`: 输出目录路径
- `OUTPUT_FORMAT`: 输出格式（json/parquet/csv）
- `OUTPUT_ROLLING_SIZE`: 文件滚动大小（字节）
- `OUTPUT_ROLLING_INTERVAL`: 文件滚动时间间隔（毫秒）

**监控配置**:
- `MONITORING_PORT`: 监控端口（默认: 8080）
- `LOG_LEVEL`: 日志级别（DEBUG/INFO/WARN/ERROR）

### 配置验证

系统启动时会自动验证配置参数的有效性：

- 必需参数检查（数据库连接、DataHub凭证）
- 参数类型验证（端口号、时间间隔等）
- 参数范围验证（并行度、重试次数等）
- 文件路径可访问性检查

如果配置无效，系统会拒绝启动并返回详细的错误信息。

### 配置示例

**开发环境配置**:
```bash
DATABASE_HOST=localhost
DATABASE_SCHEMA=dev_db
PARALLELISM_DEFAULT=2
LOG_LEVEL=DEBUG
```

**生产环境配置**:
```bash
DATABASE_HOST=prod-oceanbase.example.com
DATABASE_SCHEMA=prod_db
PARALLELISM_DEFAULT=16
CHECKPOINT_INTERVAL=180000  # 3分钟
STATE_BACKEND=rocksdb
LOG_LEVEL=INFO
```

**高可用配置**:
```bash
HA_MODE=zookeeper
HA_ZOOKEEPER_QUORUM=zk1:2181,zk2:2181,zk3:2181
HA_CLUSTER_ID=/flink-cluster
```

## 快速开始

### 前置要求

- Docker 20.10+
- Docker Compose 2.0+
- Maven 3.6+
- Java 11+
- OceanBase数据库（Oracle模式）
- 阿里云DataHub账号

### 5分钟快速部署

#### 1. 克隆项目

```bash
git clone <repository-url>
cd realtime-data-pipeline
```

#### 2. 构建应用

```bash
# 编译并打包
mvn clean package -DskipTests

# 验证构建产物
ls -lh target/realtime-data-pipeline-*.jar
```

#### 3. 配置环境变量

```bash
# 复制环境变量模板
cp .env.example .env

# 编辑配置文件
vim .env
```

**必需配置项**:

```bash
# 数据库配置
DATABASE_HOST=your-oceanbase-host
DATABASE_PORT=2881
DATABASE_USERNAME=your-username
DATABASE_PASSWORD=your-password
DATABASE_SCHEMA=your-schema
DATABASE_TABLES=table1,table2  # 或使用 * 监控所有表

# DataHub配置
DATAHUB_ENDPOINT=https://dh-cn-hangzhou.aliyuncs.com
DATAHUB_ACCESS_ID=your-access-id
DATAHUB_ACCESS_KEY=your-access-key
DATAHUB_PROJECT=your-project
DATAHUB_TOPIC=your-topic
```

#### 4. 启动服务

```bash
# 启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

#### 5. 验证部署

```bash
# 访问Flink Web UI
open http://localhost:8081

# 检查CDC Collector健康状态
curl http://localhost:8080/health

# 查看系统指标
curl http://localhost:8080/metrics
```

### 扩展TaskManager

```bash
# 扩展到3个TaskManager实例
docker-compose up -d --scale taskmanager=3

# 验证扩展结果
docker-compose ps
```

### 停止服务

```bash
# 停止所有服务
docker-compose down

# 停止并删除数据卷
docker-compose down -v
```

更多详细信息请参考: [Docker快速开始指南](docker/QUICKSTART.md)

## 部署指南

### Docker Compose部署（推荐）

最简单的部署方式，适合开发和测试环境。

```bash
# 1. 配置环境变量
cp .env.example .env
vim .env

# 2. 启动服务
docker-compose up -d

# 3. 验证部署
docker-compose ps
curl http://localhost:8081
```

详细信息: [Docker快速开始指南](docker/QUICKSTART.md)

### 手动Docker部署

适合需要精细控制的场景。

#### 1. 构建镜像

```bash
# 构建应用JAR包
mvn clean package -DskipTests

# 构建Docker镜像
docker build -f docker/jobmanager/Dockerfile -t realtime-pipeline/jobmanager:1.0.0 .
docker build -f docker/taskmanager/Dockerfile -t realtime-pipeline/taskmanager:1.0.0 .
docker build -f docker/cdc-collector/Dockerfile -t realtime-pipeline/cdc-collector:1.0.0 .
```

#### 2. 创建网络和卷

```bash
# 创建网络
docker network create flink-network

# 创建数据卷
docker volume create flink-checkpoints
docker volume create flink-savepoints
docker volume create flink-data
```

#### 3. 启动JobManager

```bash
docker run -d \
  --name flink-jobmanager \
  --network flink-network \
  -p 8081:8081 \
  -p 6123:6123 \
  -e JOB_MANAGER_RPC_ADDRESS=jobmanager \
  -e CHECKPOINT_INTERVAL=300000 \
  -v flink-checkpoints:/opt/flink/checkpoints \
  -v flink-savepoints:/opt/flink/savepoints \
  --restart unless-stopped \
  realtime-pipeline/jobmanager:1.0.0
```

#### 4. 启动TaskManager

```bash
docker run -d \
  --name flink-taskmanager-1 \
  --network flink-network \
  -e JOB_MANAGER_RPC_ADDRESS=jobmanager \
  -e TASK_MANAGER_NUMBER_OF_TASK_SLOTS=4 \
  -v flink-checkpoints:/opt/flink/checkpoints \
  -v flink-data:/opt/flink/data \
  --restart unless-stopped \
  realtime-pipeline/taskmanager:1.0.0
```

#### 5. 启动CDC Collector

```bash
docker run -d \
  --name cdc-collector \
  --network flink-network \
  -p 8080:8080 \
  -e DATABASE_HOST=your-db-host \
  -e DATABASE_USERNAME=your-username \
  -e DATABASE_PASSWORD=your-password \
  -e DATAHUB_ACCESS_ID=your-access-id \
  -e DATAHUB_ACCESS_KEY=your-access-key \
  --restart unless-stopped \
  realtime-pipeline/cdc-collector:1.0.0
```

详细信息: [Docker部署指南](docker/README.md)

### 高可用部署

启用JobManager高可用配置，支持主备自动切换。

```bash
# 1. 启动ZooKeeper
docker-compose --profile ha up -d zookeeper

# 2. 配置高可用
echo "HA_MODE=zookeeper" >> .env
echo "HA_ZOOKEEPER_QUORUM=zookeeper:2181" >> .env

# 3. 启动高可用集群
docker-compose --profile ha up -d
```

### 生产环境部署建议

1. **资源配置**:
   - JobManager: 2核CPU, 2GB内存
   - TaskManager: 2核CPU, 2GB内存（每个）
   - CDC Collector: 1核CPU, 1GB内存

2. **存储配置**:
   - Checkpoint目录: 使用高性能存储（SSD）
   - 输出目录: 根据数据量规划容量
   - 日志目录: 预留足够空间

3. **网络配置**:
   - 确保组件间网络连通性
   - 配置防火墙规则开放必要端口
   - 使用内网连接提高性能

4. **安全配置**:
   - 使用环境变量或密钥管理工具存储敏感信息
   - 限制容器资源使用
   - 定期更新镜像和依赖

5. **监控配置**:
   - 配置Prometheus采集指标
   - 配置告警规则
   - 定期检查日志

## 常见问题

### 部署相关

**Q: 容器启动失败，提示"无法连接到JobManager"？**

A: 检查以下几点：
1. 确保JobManager容器已启动并健康：`docker-compose ps`
2. 检查网络连接：`docker network inspect flink-network`
3. 验证环境变量配置：`docker-compose config`
4. 查看JobManager日志：`docker-compose logs jobmanager`

**Q: 如何修改Flink并行度？**

A: 有两种方式：
```bash
# 方式1: 修改环境变量
echo "PARALLELISM_DEFAULT=8" >> .env
docker-compose restart

# 方式2: 修改配置文件
vim src/main/resources/application.yml
# 修改 flink.parallelism 值
```

**Q: 容器启动超过60秒怎么办？**

A: 检查以下原因：
1. 资源不足：增加Docker资源限制
2. 网络问题：检查DataHub和数据库连接
3. 配置错误：验证配置参数有效性
4. 查看启动日志：`docker-compose logs -f`

### 数据处理相关

**Q: 数据延迟很高，如何优化？**

A: 尝试以下优化措施：
1. 增加TaskManager数量：`docker-compose up -d --scale taskmanager=5`
2. 增加并行度：修改`PARALLELISM_DEFAULT`环境变量
3. 调整Checkpoint间隔：减少`CHECKPOINT_INTERVAL`值
4. 检查反压情况：访问Flink Web UI查看反压指标

**Q: Checkpoint频繁失败怎么办？**

A: 排查步骤：
1. 检查存储空间：确保Checkpoint目录有足够空间
2. 增加超时时间：调整`CHECKPOINT_TIMEOUT`参数
3. 检查状态大小：考虑使用RocksDB状态后端
4. 查看错误日志：`docker-compose logs jobmanager | grep checkpoint`

**Q: 如何处理死信队列中的数据？**

A: 死信队列数据存储在`/opt/flink/data/dlq/`目录：
```bash
# 查看死信队列数据
docker exec flink-taskmanager-1 ls -lh /opt/flink/data/dlq/

# 导出死信队列数据
docker cp flink-taskmanager-1:/opt/flink/data/dlq/ ./dlq-backup/

# 分析失败原因并修复后重新处理
```

**Q: 数据重复怎么办？**

A: 系统默认提供至少一次语义，可能产生重复：
1. 启用精确一次语义：配置幂等性Sink
2. 在下游系统实现去重逻辑
3. 使用唯一ID进行去重：每条记录都有`eventId`字段

### 监控相关

**Q: 如何查看实时处理速率？**

A: 访问监控端点：
```bash
# 查看详细指标
curl http://localhost:8080/metrics | jq '.records'

# 访问Flink Web UI
open http://localhost:8081
```

**Q: 告警通知如何配置？**

A: 修改监控配置：
```yaml
monitoring:
  alerts:
    enabled: true
    webhook: https://your-webhook-url
    email: admin@example.com
```

**Q: 如何导出Prometheus指标？**

A: JobManager和TaskManager都暴露Prometheus端口：
```bash
# 访问Prometheus指标
curl http://localhost:9249/metrics

# 配置Prometheus抓取
# prometheus.yml:
# - job_name: 'flink'
#   static_configs:
#   - targets: ['localhost:9249']
```

### 故障恢复相关

**Q: JobManager崩溃后如何恢复？**

A: 系统会自动恢复：
1. Docker会自动重启容器（2分钟内）
2. 如果启用HA，会切换到备用JobManager（30秒内）
3. 作业会从最近的Checkpoint恢复

**Q: 如何手动触发故障恢复？**

A: 使用Savepoint进行恢复：
```bash
# 1. 触发Savepoint
curl -X POST http://localhost:8081/jobs/<job-id>/savepoints \
  -d '{"target-directory": "/opt/flink/savepoints"}'

# 2. 取消作业
curl -X PATCH http://localhost:8081/jobs/<job-id> \
  -d '{"state": "cancelled"}'

# 3. 从Savepoint恢复
docker exec flink-jobmanager flink run \
  -s /opt/flink/savepoints/<savepoint-id> \
  /opt/flink/lib/realtime-data-pipeline.jar
```

**Q: 数据丢失怎么办？**

A: 系统设计保证数据不丢失：
1. 检查Checkpoint是否正常：查看Flink Web UI
2. 验证至少一次语义：检查输出数据
3. 如果确实丢失，从Checkpoint恢复并重放数据

### 性能相关

**Q: 如何提高吞吐量？**

A: 优化建议：
1. 增加并行度：`PARALLELISM_DEFAULT=16`
2. 增加TaskManager：`docker-compose up -d --scale taskmanager=10`
3. 调整内存配置：增加`TASK_MANAGER_HEAP_SIZE`
4. 使用Parquet格式输出：`OUTPUT_FORMAT=parquet`
5. 启用压缩：`OUTPUT_COMPRESSION=snappy`

**Q: 内存使用率过高怎么办？**

A: 调整内存配置：
```bash
# 增加TaskManager内存
export TASK_MANAGER_HEAP_SIZE=2048m
export TASK_MANAGER_MEMORY_PROCESS_SIZE=3456m
docker-compose restart taskmanager
```

**Q: 如何减少Checkpoint开销？**

A: 优化Checkpoint配置：
```bash
# 增加Checkpoint间隔
export CHECKPOINT_INTERVAL=600000  # 10分钟

# 启用增量Checkpoint（RocksDB）
export STATE_BACKEND=rocksdb

# 减少并发Checkpoint
export CHECKPOINT_MAX_CONCURRENT=1
```

### 开发相关

**Q: 如何添加新的数据处理逻辑？**

A: 修改EventProcessor类：
```java
// src/main/java/com/realtime/pipeline/flink/processor/EventProcessor.java
public class EventProcessor extends RichMapFunction<ChangeEvent, ProcessedEvent> {
    @Override
    public ProcessedEvent map(ChangeEvent event) {
        // 添加自定义处理逻辑
        return processEvent(event);
    }
}
```

**Q: 如何添加新的输出格式？**

A: 实现AbstractFileSink：
```java
public class CustomFileSink extends AbstractFileSink {
    @Override
    protected void writeRecord(ProcessedEvent event) {
        // 实现自定义格式写入逻辑
    }
}
```

**Q: 如何运行测试？**

A: 运行测试命令：
```bash
# 运行所有测试
mvn test

# 运行特定测试
mvn test -Dtest=CDCCollectorTest

# 运行属性测试
mvn test -Dtest=*PropertyTest

# 跳过测试构建
mvn package -DskipTests
```

### 其他问题

**Q: 支持哪些数据库？**

A: 当前支持OceanBase（Oracle模式），理论上支持所有Flink CDC支持的数据库。

**Q: 支持哪些输出格式？**

A: 支持JSON、Parquet、CSV三种格式，可通过`OUTPUT_FORMAT`环境变量配置。

**Q: 如何升级系统版本？**

A: 升级步骤：
```bash
# 1. 触发Savepoint
curl -X POST http://localhost:8081/jobs/<job-id>/savepoints

# 2. 停止服务
docker-compose down

# 3. 更新代码并重新构建
git pull
mvn clean package -DskipTests
docker-compose build

# 4. 从Savepoint启动新版本
docker-compose up -d
```

**Q: 如何联系技术支持？**

A: 
- 查看文档：`docs/`目录
- 提交Issue：<repository-url>/issues
- 邮件联系：support@example.com

### 添加新功能

1. 在相应的包下创建新的类
2. 更新配置类（如需要）
3. 编写单元测试和属性测试
4. 更新文档

### 测试策略

系统采用双重测试方法：

1. **单元测试**: 验证特定示例和边界情况
2. **基于属性的测试**: 验证跨所有输入的通用属性

所有测试使用JUnit 5和jqwik框架。

### 代码规范

- 使用Lombok减少样板代码
- 所有公共方法添加JavaDoc注释
- 遵循Java命名规范
- 保持代码简洁和可读性

## 监控和运维

### 健康检查

系统提供多个健康检查端点：

**Flink JobManager**:
```bash
# Web UI健康检查
curl http://localhost:8081/overview

# 作业状态查询
curl http://localhost:8081/jobs
```

**CDC Collector**:
```bash
# 基本健康检查
curl http://localhost:8080/health

# 存活探测
curl http://localhost:8080/health/live

# 就绪探测
curl http://localhost:8080/health/ready

# 详细指标
curl http://localhost:8080/metrics
```

### 监控指标

系统暴露以下关键指标：

**吞吐量指标**:
- `records.in.rate`: 每秒输入记录数
- `records.out.rate`: 每秒输出记录数
- `bytes.in.rate`: 每秒输入字节数
- `bytes.out.rate`: 每秒输出字节数

**延迟指标**:
- `latency.p50`: 端到端处理延迟P50（毫秒）
- `latency.p95`: 端到端处理延迟P95（毫秒）
- `latency.p99`: 端到端处理延迟P99（毫秒）

**Checkpoint指标**:
- `checkpoint.duration`: Checkpoint耗时（毫秒）
- `checkpoint.success.rate`: Checkpoint成功率（百分比）
- `checkpoint.count`: Checkpoint总次数
- `checkpoint.failed.count`: Checkpoint失败次数

**资源指标**:
- `taskmanager.cpu.usage`: TaskManager CPU使用率
- `taskmanager.memory.usage`: TaskManager内存使用率
- `backpressure.level`: 反压级别（0-1）

**错误指标**:
- `failed.records.count`: 失败记录数
- `dlq.records.count`: 死信队列记录数

### 告警规则

系统支持以下告警：

| 告警类型 | 触发条件 | 严重级别 |
|---------|---------|---------|
| 数据延迟告警 | 延迟超过60秒 | 高 |
| Checkpoint失败告警 | 失败率超过10% | 高 |
| 系统负载告警 | CPU使用率超过80% | 中 |
| 反压告警 | 反压级别超过0.8 | 中 |
| 死信队列告警 | 死信记录数持续增长 | 低 |

### 日志管理

**日志文件位置**:
- JobManager: `/opt/flink/logs/`
- TaskManager: `/opt/flink/logs/`
- CDC Collector: `/opt/cdc-collector/logs/`

**查看日志**:
```bash
# 查看容器日志
docker-compose logs -f jobmanager
docker-compose logs -f taskmanager
docker-compose logs -f cdc-collector

# 查看特定时间段的日志
docker-compose logs --since 1h cdc-collector

# 查看最近100行日志
docker-compose logs --tail 100 jobmanager
```

**日志级别**:
- `DEBUG`: 详细调试信息
- `INFO`: 一般信息（默认）
- `WARN`: 警告信息
- `ERROR`: 错误信息

**修改日志级别**:
```bash
# 通过环境变量修改
export LOG_LEVEL=DEBUG
docker-compose restart cdc-collector
```

### 运维操作

**扩容TaskManager**:
```bash
# 扩展到5个TaskManager
docker-compose up -d --scale taskmanager=5

# 验证扩容结果
docker-compose ps
curl http://localhost:8081/taskmanagers
```

**触发Savepoint**:
```bash
# 通过Flink CLI触发
docker exec flink-jobmanager flink savepoint <job-id> /opt/flink/savepoints

# 通过REST API触发
curl -X POST http://localhost:8081/jobs/<job-id>/savepoints \
  -H "Content-Type: application/json" \
  -d '{"target-directory": "/opt/flink/savepoints"}'
```

**从Savepoint恢复**:
```bash
# 停止当前作业
docker exec flink-jobmanager flink cancel <job-id>

# 从Savepoint恢复
docker exec flink-jobmanager flink run \
  -s /opt/flink/savepoints/<savepoint-id> \
  /opt/flink/lib/realtime-data-pipeline.jar
```

**查看作业状态**:
```bash
# 列出所有作业
curl http://localhost:8081/jobs

# 查看特定作业详情
curl http://localhost:8081/jobs/<job-id>

# 查看作业异常
curl http://localhost:8081/jobs/<job-id>/exceptions
```

**重启服务**:
```bash
# 重启单个服务
docker-compose restart cdc-collector

# 重启所有服务
docker-compose restart

# 优雅停止并重启
docker-compose down
docker-compose up -d
```

## 相关文档

- [Docker快速开始指南](docker/QUICKSTART.md) - 5分钟快速部署指南
- [Docker部署指南](docker/README.md) - 详细的Docker部署说明
- [设计文档](.kiro/specs/realtime-data-pipeline/design.md) - 系统架构和设计
- [需求文档](.kiro/specs/realtime-data-pipeline/requirements.md) - 功能需求说明
- [任务列表](.kiro/specs/realtime-data-pipeline/tasks.md) - 实现任务清单

## 性能指标

系统在标准配置下的性能指标：

| 指标 | 目标值 | 说明 |
|------|--------|------|
| 吞吐量 | 10万条/秒 | 每秒处理记录数 |
| 延迟P99 | < 5秒 | 端到端处理延迟 |
| Checkpoint耗时 | < 30秒 | Checkpoint完成时间 |
| 系统可用性 | 99.9% | 年度可用性目标 |
| 故障恢复时间 | < 10分钟 | 从故障到恢复的时间 |
| 容器启动时间 | < 60秒 | 容器初始化时间 |

## 许可证

Copyright © 2025 Realtime Data Pipeline Team

本项目采用企业内部许可证，未经授权不得用于商业用途。

## 联系方式

- **技术支持**: support@example.com
- **问题反馈**: <repository-url>/issues
- **文档**: <repository-url>/wiki
- **团队**: Realtime Data Pipeline Team

---

**最后更新**: 2025-01-28  
**版本**: 1.0.0  
**状态**: 生产就绪


## 开发指南

### 项目结构

```
realtime-data-pipeline/
├── src/
│   ├── main/
│   │   ├── java/com/realtime/pipeline/
│   │   │   ├── cdc/              # CDC采集组件
│   │   │   │   ├── CDCCollector.java
│   │   │   │   ├── CDCEventConverter.java
│   │   │   │   └── ConnectionManager.java
│   │   │   ├── config/           # 配置管理
│   │   │   │   ├── PipelineConfig.java
│   │   │   │   ├── DatabaseConfig.java
│   │   │   │   ├── DataHubConfig.java
│   │   │   │   ├── FlinkConfig.java
│   │   │   │   └── OutputConfig.java
│   │   │   ├── datahub/          # DataHub集成
│   │   │   │   ├── DataHubSender.java
│   │   │   │   └── client/
│   │   │   ├── flink/            # Flink流处理
│   │   │   │   ├── source/       # 数据源
│   │   │   │   ├── processor/    # 数据处理
│   │   │   │   ├── sink/         # 数据输出
│   │   │   │   ├── checkpoint/   # Checkpoint管理
│   │   │   │   ├── recovery/     # 故障恢复
│   │   │   │   ├── ha/           # 高可用
│   │   │   │   └── scaling/      # 动态扩缩容
│   │   │   ├── model/            # 数据模型
│   │   │   │   ├── ChangeEvent.java
│   │   │   │   ├── ProcessedEvent.java
│   │   │   │   └── ...
│   │   │   ├── monitoring/       # 监控组件
│   │   │   │   ├── MonitoringService.java
│   │   │   │   ├── MetricsReporter.java
│   │   │   │   ├── AlertManager.java
│   │   │   │   └── HealthCheckServer.java
│   │   │   ├── error/            # 错误处理
│   │   │   │   ├── DeadLetterQueue.java
│   │   │   │   └── ...
│   │   │   ├── job/              # 作业管理
│   │   │   │   └── FlinkJobManager.java
│   │   │   └── util/             # 工具类
│   │   └── resources/
│   │       ├── application.yml
│   │       └── log4j2.xml
│   └── test/
│       └── java/                 # 测试代码
│           ├── *Test.java        # 单元测试
│           └── *PropertyTest.java # 属性测试
├── docker/                       # Docker配置
│   ├── jobmanager/
│   ├── taskmanager/
│   └── cdc-collector/
├── docs/                         # 文档
├── .kiro/specs/                  # 规格说明
├── pom.xml
├── docker-compose.yml
└── README.md
```

### 开发环境搭建

#### 1. 安装依赖

```bash
# Java 11+
java -version

# Maven 3.6+
mvn -version

# Docker
docker --version
docker-compose --version
```

#### 2. 克隆项目

```bash
git clone <repository-url>
cd realtime-data-pipeline
```

#### 3. 构建项目

```bash
# 编译
mvn clean compile

# 运行测试
mvn test

# 打包
mvn clean package
```

#### 4. IDE配置

**IntelliJ IDEA**:
1. 导入Maven项目
2. 启用Lombok插件
3. 设置Java 11 SDK
4. 配置代码格式化规则

**Eclipse**:
1. 导入Maven项目
2. 安装Lombok
3. 配置Java 11

### 添加新功能

#### 1. 添加新的数据处理逻辑

```java
// 创建新的处理函数
public class CustomProcessor extends RichMapFunction<ChangeEvent, ProcessedEvent> {
    @Override
    public ProcessedEvent map(ChangeEvent event) throws Exception {
        // 实现处理逻辑
        return processEvent(event);
    }
}

// 在Flink作业中使用
DataStream<ProcessedEvent> processed = source
    .map(new CustomProcessor())
    .name("Custom Processor");
```

#### 2. 添加新的输出格式

```java
// 继承AbstractFileSink
public class XmlFileSink extends AbstractFileSink {
    @Override
    protected void writeRecord(ProcessedEvent event) throws IOException {
        // 实现XML格式写入
        String xml = convertToXml(event);
        writer.write(xml);
    }
    
    private String convertToXml(ProcessedEvent event) {
        // XML转换逻辑
        return "<event>...</event>";
    }
}
```

#### 3. 添加新的监控指标

```java
// 注册自定义指标
public class CustomMetrics {
    private final Counter customCounter;
    
    public CustomMetrics(RuntimeContext context) {
        this.customCounter = context.getMetricGroup()
            .counter("custom_metric");
    }
    
    public void increment() {
        customCounter.inc();
    }
}
```

### 测试策略

系统采用双重测试方法：

#### 1. 单元测试

验证特定示例和边界情况：

```java
@Test
public void testEventProcessing() {
    EventProcessor processor = new EventProcessor();
    ChangeEvent input = createTestEvent();
    
    ProcessedEvent output = processor.map(input);
    
    assertNotNull(output);
    assertEquals(input.getEventId(), output.getEventId());
}

@Test
public void testFileRolling() {
    FileSink sink = new JsonFileSink();
    sink.setRollingPolicy(RollingPolicy.onSize(1024));
    
    // 写入超过阈值的数据
    writeTestData(sink, 2048);
    
    // 验证文件滚动
    assertEquals(2, countOutputFiles());
}
```

#### 2. 基于属性的测试

验证跨所有输入的通用属性：

```java
@Property
public void eventOrderPreservation(@ForAll List<ChangeEvent> events) {
    // 发送事件序列
    List<ProcessedEvent> output = processEvents(events);
    
    // 验证时间顺序保持
    for (int i = 1; i < output.size(); i++) {
        assertTrue(output.get(i).getTimestamp() >= 
                   output.get(i-1).getTimestamp());
    }
}

@Property
public void uniqueIdentifierGeneration(@ForAll List<ChangeEvent> events) {
    List<ProcessedEvent> processed = processEvents(events);
    
    Set<String> ids = processed.stream()
        .map(ProcessedEvent::getEventId)
        .collect(Collectors.toSet());
    
    // 验证所有ID唯一
    assertEquals(processed.size(), ids.size());
}
```

#### 3. 集成测试

测试完整的数据流：

```java
@Test
public void testEndToEndDataFlow() {
    // 启动测试环境
    TestEnvironment env = new TestEnvironment();
    
    // 发送测试数据
    env.sendChangeEvent(testEvent);
    
    // 等待处理完成
    env.waitForProcessing();
    
    // 验证输出
    List<ProcessedEvent> output = env.readOutput();
    assertEquals(1, output.size());
}
```

### 代码规范

#### 1. 命名规范

- 类名：大驼峰（PascalCase）
- 方法名：小驼峰（camelCase）
- 常量：全大写下划线分隔（UPPER_SNAKE_CASE）
- 包名：全小写（lowercase）

#### 2. 注释规范

```java
/**
 * CDC采集器，从OceanBase数据库捕获变更数据
 * 
 * <p>功能：
 * <ul>
 *   <li>捕获INSERT/UPDATE/DELETE操作</li>
 *   <li>自动重连和重试</li>
 *   <li>发送数据到DataHub</li>
 * </ul>
 * 
 * @author Your Name
 * @since 1.0.0
 */
public class CDCCollector {
    /**
     * 启动CDC采集
     * 
     * @param dbConfig 数据库配置
     * @param dhConfig DataHub配置
     * @throws IllegalArgumentException 如果配置无效
     */
    public void start(DatabaseConfig dbConfig, DataHubConfig dhConfig) {
        // 实现
    }
}
```

#### 3. 异常处理

```java
// 使用自定义异常
public class CDCException extends RuntimeException {
    public CDCException(String message, Throwable cause) {
        super(message, cause);
    }
}

// 适当的异常处理
try {
    sendToDataHub(event);
} catch (DataHubException e) {
    logger.error("Failed to send event: {}", event.getEventId(), e);
    deadLetterQueue.send(event, e);
}
```

#### 4. 日志规范

```java
// 使用SLF4J
private static final Logger logger = LoggerFactory.getLogger(CDCCollector.class);

// 不同级别的日志
logger.debug("Processing event: {}", event.getEventId());
logger.info("CDC collector started successfully");
logger.warn("Retry attempt {} failed", retryCount);
logger.error("Failed to connect to database", exception);
```

### 调试技巧

#### 1. 本地调试

```bash
# 启动本地Flink集群
./bin/start-cluster.sh

# 提交作业
./bin/flink run target/realtime-data-pipeline-1.0.0-SNAPSHOT.jar

# 查看日志
tail -f log/flink-*-taskexecutor-*.log
```

#### 2. 远程调试

```bash
# 启动JobManager时启用远程调试
export FLINK_ENV_JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
docker-compose up -d

# 在IDE中连接到localhost:5005
```

#### 3. 性能分析

```bash
# 启用JMX监控
export FLINK_ENV_JAVA_OPTS="-Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9010"

# 使用JConsole连接
jconsole localhost:9010
```

### 贡献指南

1. Fork项目
2. 创建特性分支：`git checkout -b feature/new-feature`
3. 提交更改：`git commit -am 'Add new feature'`
4. 推送分支：`git push origin feature/new-feature`
5. 提交Pull Request

### 发布流程

```bash
# 1. 更新版本号
mvn versions:set -DnewVersion=1.1.0

# 2. 运行所有测试
mvn clean test

# 3. 构建发布包
mvn clean package -DskipTests

# 4. 构建Docker镜像
docker build -t realtime-pipeline/jobmanager:1.1.0 .

# 5. 推送镜像
docker push realtime-pipeline/jobmanager:1.1.0

# 6. 创建Git标签
git tag -a v1.1.0 -m "Release version 1.1.0"
git push origin v1.1.0
```
