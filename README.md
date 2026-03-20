# 实时数据管道系统 (Realtime Data Pipeline)

基于 Apache Flink 的企业级 CDC 数据采集平台，使用 Flink CDC 3.2 从 Oracle 数据库实时捕获变更数据，支持高可用部署、Web 可视化管理和自动化运维。

## 快速启动

### 1. 启动 Docker 容器
```bash
docker-compose up -d
```

### 2. 访问 Web 管理界面
- 管理界面: http://localhost:8888
- Flink Web UI: http://localhost:8081
- 后端 API: http://localhost:5001

### 3. 创建 CDC 任务
1. 访问管理界面，配置数据源
2. 创建 CDC 任务，选择要监控的表
3. 提交任务到 Flink 集群
4. 实时查看任务状态和输出数据

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

实时数据管道系统是一个企业级的 CDC 数据采集平台，专为 Oracle 数据库设计。系统采用 Flink CDC + Web 管理界面的架构，通过 Docker 容器化部署，提供高可用、易用、可视化的实时数据采集能力。

### 核心特性

- **Web 可视化管理**: 提供完整的 Web 管理界面，支持数据源管理、任务创建、状态监控
- **高可用架构**: 支持 Flink JobManager 高可用配置（ZooKeeper/Kubernetes），主备自动切换
- **数据库持久化**: 任务配置和运行状态存储在 Oracle 数据库，支持数据迁移和备份
- **智能任务调度**: 自动检测资源冲突，防止重复任务，支持并发控制
- **实时数据输出**: 支持 CSV 格式输出，按表和时间自动分区，便于数据分析
- **容器化部署**: 采用 Docker Compose 一键部署，包含所有依赖组件

## 系统架构

### 整体架构

```
┌─────────────────┐      ┌──────────────────┐      ┌─────────────────┐
│  Web 管理界面   │─────>│  后端 API 服务   │─────>│  Oracle 数据库  │
│  (Nginx)        │      │  (Spring Boot)   │      │  (元数据存储)   │
│  Port: 8888     │      │  Port: 5001      │      │                 │
└─────────────────┘      └──────────────────┘      └─────────────────┘
                                  │
                                  ▼
                         ┌──────────────────┐
                         │  Flink 集群      │
                         │  (HA 模式)       │
                         │  Port: 8081      │
                         └──────────────────┘
                                  │
                                  ▼
                         ┌──────────────────┐      ┌─────────────────┐
                         │  CDC 数据采集    │─────>│  CSV 文件输出   │
                         │  (Flink CDC)     │      │  (按表分区)     │
                         └──────────────────┘      └─────────────────┘
                                  ▲
                                  │
                         ┌──────────────────┐
                         │  Oracle 源数据库 │
                         │  (CDC 源)        │
                         └──────────────────┘
```

### 核心组件

1. **Web 管理界面**: 基于 Nginx 的前端应用，提供可视化管理功能
2. **后端 API 服务**: Spring Boot 应用，处理业务逻辑和任务调度
3. **Flink 集群**: 高可用的流处理集群，执行 CDC 数据采集任务
4. **Oracle 数据库**: 存储系统元数据（数据源配置、任务配置、运行状态）
5. **ZooKeeper**: 提供 Flink 高可用协调服务（可选）

### 数据流

1. **任务创建**: 用户通过 Web 界面创建 CDC 任务，配置存储到 Oracle 数据库
2. **任务提交**: 后端服务将任务提交到 Flink 集群，启动 CDC 数据采集
3. **数据采集**: Flink CDC 连接器从源 Oracle 数据库捕获变更数据
4. **数据输出**: 变更数据按表和时间分区输出为 CSV 文件
5. **状态监控**: 实时查询 Flink 作业状态，更新到数据库

## 技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| 流处理引擎 | Apache Flink | 1.18.0 |
| CDC 连接器 | Flink CDC Connector | 3.2.0 (Oracle) |
| 后端框架 | Spring Boot | 2.7.x |
| 前端框架 | 原生 JavaScript + HTML/CSS | - |
| Web 服务器 | Nginx | Alpine |
| 数据库 | Oracle | 11g+ |
| 高可用协调 | Apache ZooKeeper | 3.8.0 |
| 日志框架 | Log4j2 | 2.20.0 |
| 构建工具 | Maven | 3.6+ |
| 容器化 | Docker | 20.10+ |
| 编排工具 | Docker Compose | 2.0+ |
| 编程语言 | Java | 11+ |

## 项目结构

```
realtime-data-pipeline/
├── src/                             # 源代码目录
│   ├── main/
│   │   ├── java/com/realtime/
│   │   │   ├── UnifiedApplication.java    # 统一应用入口
│   │   │   ├── monitor/                   # 监控和管理模块
│   │   │   │   ├── controller/            # REST API 控制器
│   │   │   │   │   ├── CdcTaskController.java      # CDC 任务管理
│   │   │   │   │   ├── DataSourceController.java   # 数据源管理
│   │   │   │   │   ├── JobController.java          # Flink 作业管理
│   │   │   │   │   ├── ClusterController.java      # 集群状态查询
│   │   │   │   │   └── RuntimeJobController.java   # 运行时作业管理
│   │   │   │   ├── service/               # 业务逻辑服务
│   │   │   │   │   ├── CdcTaskService.java         # 任务服务
│   │   │   │   │   ├── DataSourceService.java      # 数据源服务
│   │   │   │   │   ├── FlinkService.java           # Flink 集成服务
│   │   │   │   │   ├── EmbeddedCdcService.java     # CDC 提交服务
│   │   │   │   │   └── RuntimeJobService.java      # 运行时作业服务
│   │   │   │   ├── repository/            # 数据访问层
│   │   │   │   │   ├── TaskRepository.java         # 任务数据访问
│   │   │   │   │   ├── DataSourceRepository.java   # 数据源数据访问
│   │   │   │   │   └── RuntimeJobRepository.java   # 运行时作业数据访问
│   │   │   │   ├── dto/                   # 数据传输对象
│   │   │   │   │   ├── TaskConfig.java             # 任务配置
│   │   │   │   │   ├── DataSourceConfig.java       # 数据源配置
│   │   │   │   │   ├── RuntimeJob.java             # 运行时作业
│   │   │   │   │   └── ApiResponse.java            # API 响应
│   │   │   │   └── config/               # 配置类
│   │   │   │       ├── AppConfig.java              # 应用配置
│   │   │   │       └── WebConfig.java              # Web 配置
│   │   │   └── pipeline/                  # CDC 数据管道
│   │   │       └── CdcJobMain.java                 # CDC 作业主类
│   │   └── resources/
│   │       ├── application.yml            # 应用配置
│   │       ├── application-docker.yml     # Docker 环境配置
│   │       ├── application-ha-zookeeper.yml  # ZooKeeper HA 配置
│   │       ├── application-ha-kubernetes.yml # Kubernetes HA 配置
│   │       └── log4j2.xml                 # 日志配置
│   └── test/
│       └── java/                          # 测试代码
├── monitor/                         # 前端和配置
│   ├── frontend/                    # Web 前端
│   │   ├── index.html              # 主页
│   │   ├── main.html               # 主界面
│   │   ├── cdc-manager.html        # CDC 管理
│   │   ├── cdc-task-creator.html   # 任务创建
│   │   ├── datasource-manager.html # 数据源管理
│   │   ├── task-list.html          # 任务列表
│   │   └── nginx.conf              # Nginx 配置
│   ├── config/                      # 配置文件（已迁移到数据库）
│   │   ├── datasources/            # 数据源配置（历史）
│   │   └── cdc_tasks/              # 任务配置（历史）
│   └── Dockerfile                   # 前端镜像构建
├── docker/                          # Docker 配置
│   ├── jobmanager/                 # JobManager 配置
│   │   ├── Dockerfile
│   │   ├── flink-conf.yaml
│   │   ├── entrypoint.sh
│   │   └── log4j.properties
│   └── taskmanager/                # TaskManager 配置
│       ├── Dockerfile
│       ├── flink-conf.yaml
│       ├── entrypoint.sh
│       └── log4j.properties
├── sql/                             # SQL 脚本
│   ├── setup-flink-user-schema.sql # 创建用户和表空间
│   ├── setup-metadata-tables.sql   # 创建元数据表
│   ├── setup-runtime-jobs-table.sql # 创建运行时作业表
│   ├── configure-oracle-for-cdc.sql # 配置 Oracle CDC
│   ├── migrate-json-to-db.py       # 数据迁移脚本
│   └── README-database-migration.md # 迁移文档
├── output/                          # 输出目录
│   └── cdc/                        # CDC 输出文件（按日期分区）
├── logs/                            # 日志目录
├── docs/                            # 项目文档
│   ├── MIGRATION-SUMMARY.md        # 迁移总结
│   ├── RUNTIME-JOB-MANAGEMENT.md   # 运行时作业管理
│   └── LOG-MINING-FLUSH-ISSUE-RESOLVED.md
├── docker-compose.yml              # Docker Compose 配置
├── pom.xml                         # Maven 配置
├── .env                            # 环境变量配置
└── README.md                       # 项目文档（本文件）
```

### 目录说明

- **src/main/java/com/realtime/monitor**: 后端 API 服务，提供 REST 接口
- **src/main/java/com/realtime/pipeline**: CDC 数据管道，Flink 作业实现
- **monitor/frontend**: Web 前端界面，提供可视化管理
- **docker/**: Docker 容器配置，包含 JobManager 和 TaskManager
- **sql/**: 数据库脚本，包含表结构和配置脚本
- **output/cdc/**: CDC 输出目录，按日期和表名分区存储 CSV 文件

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

系统配置通过环境变量和数据库管理。

### 环境变量配置

创建 `.env` 文件配置系统参数：

```bash
# Oracle 数据库配置（元数据存储）
ORACLE_HOST=host.docker.internal
ORACLE_PORT=1521
ORACLE_SID=helowin
ORACLE_USERNAME=finance_user
ORACLE_PASSWORD=password

# Flink 配置
FLINK_REST_URL=http://jobmanager:8081
FLINK_OUTPUT_PATH=./output/cdc
FLINK_PARALLELISM=2

# 应用配置
SERVER_PORT=5001
SPRING_PROFILES_ACTIVE=docker

# 高可用配置（可选）
HA_MODE=zookeeper
HA_ZOOKEEPER_QUORUM=zookeeper:2181
HA_CLUSTER_ID=/flink-cluster
```

### 数据库表结构

系统使用以下数据库表存储元数据：

**1. datasources - 数据源配置表**
```sql
CREATE TABLE datasources (
    id VARCHAR2(100) PRIMARY KEY,
    name VARCHAR2(200) NOT NULL,
    host VARCHAR2(200) NOT NULL,
    port NUMBER(5) NOT NULL,
    username VARCHAR2(100) NOT NULL,
    password VARCHAR2(200) NOT NULL,
    sid VARCHAR2(100) NOT NULL,
    description VARCHAR2(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**2. cdc_tasks - CDC 任务配置表**
```sql
CREATE TABLE cdc_tasks (
    id VARCHAR2(100) PRIMARY KEY,
    name VARCHAR2(200) NOT NULL,
    datasource_id VARCHAR2(100),
    schema_name VARCHAR2(100) NOT NULL,
    tables CLOB NOT NULL,
    output_path VARCHAR2(500),
    parallelism NUMBER(3) DEFAULT 2,
    split_size NUMBER(10) DEFAULT 8096,
    status VARCHAR2(20) DEFAULT 'CREATED',
    flink_job_id VARCHAR2(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**3. runtime_jobs - 运行时作业表**
```sql
CREATE TABLE runtime_jobs (
    id VARCHAR2(100) PRIMARY KEY,
    task_id VARCHAR2(100),
    task_name VARCHAR2(200),
    schema_name VARCHAR2(100),
    tables CLOB,
    parallelism NUMBER(3),
    flink_job_id VARCHAR2(100),
    status VARCHAR2(20) DEFAULT 'PENDING',
    error_message VARCHAR2(4000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP
);
```

### 初始化数据库

```bash
# 1. 创建用户和表空间
sqlplus sys/password@helowin as sysdba @sql/setup-flink-user-schema.sql

# 2. 创建元数据表
sqlplus finance_user/password@helowin @sql/setup-metadata-tables.sql

# 3. 配置 Oracle CDC（源数据库）
sqlplus sys/password@source_db as sysdba @sql/configure-oracle-for-cdc.sql
```

### 数据迁移

如果从 JSON 文件配置迁移到数据库：

```bash
# 运行迁移脚本
python3 sql/migrate-json-to-db.py
```

详细信息请参考: [数据库迁移文档](sql/README-database-migration.md)

## 快速开始

### 前置要求

- Docker 20.10+
- Docker Compose 2.0+
- Oracle 数据库 11g+（用于元数据存储和 CDC 源）
- Maven 3.6+ 和 Java 11+（仅开发环境需要）

### 5 分钟快速部署

#### 1. 克隆项目

```bash
git clone <repository-url>
cd realtime-data-pipeline
```

#### 2. 配置环境变量

```bash
# 复制环境变量模板
cp .env.example .env

# 编辑配置文件
vim .env
```

**必需配置项**:

```bash
# Oracle 数据库配置（元数据存储）
ORACLE_HOST=your-oracle-host
ORACLE_PORT=1521
ORACLE_SID=your-sid
ORACLE_USERNAME=finance_user
ORACLE_PASSWORD=your-password

# Flink 配置
FLINK_REST_URL=http://jobmanager:8081
FLINK_OUTPUT_PATH=./output/cdc
```

#### 3. 初始化数据库

```bash
# 创建用户和表空间
sqlplus sys/password@helowin as sysdba @sql/setup-flink-user-schema.sql

# 创建元数据表
sqlplus finance_user/password@helowin @sql/setup-metadata-tables.sql
```

#### 4. 启动服务

```bash
# 构建应用（首次部署）
mvn clean package -DskipTests

# 启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps
```

#### 5. 访问管理界面

```bash
# 打开浏览器访问
open http://localhost:8888

# 或查看 Flink Web UI
open http://localhost:8081
```

### 使用 Web 界面创建 CDC 任务

1. **配置数据源**
   - 访问 http://localhost:8888
   - 点击"数据源管理"
   - 添加 Oracle 数据源（CDC 源数据库）
   - 测试连接

2. **创建 CDC 任务**
   - 点击"CDC 任务管理" → "创建任务"
   - 选择数据源和 Schema
   - 选择要监控的表
   - 配置输出路径和并行度
   - 保存任务

3. **提交任务**
   - 在任务列表中找到创建的任务
   - 点击"提交"按钮
   - 系统自动提交到 Flink 集群

4. **查看输出**
   - 输出文件位于 `output/cdc/` 目录
   - 按日期和表名分区：`output/cdc/YYYY-MM-DD--HH/TABLE_NAME_*.csv`

### 使用 API 创建任务

```bash
# 1. 创建数据源
curl -X POST http://localhost:5001/api/datasources \
  -H "Content-Type: application/json" \
  -d '{
    "id": "oracle-prod",
    "name": "生产环境 Oracle",
    "host": "your-oracle-host",
    "port": 1521,
    "username": "your-username",
    "password": "your-password",
    "sid": "your-sid"
  }'

# 2. 创建 CDC 任务
curl -X POST http://localhost:5001/api/cdc/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "name": "我的CDC任务",
    "datasource_id": "oracle-prod",
    "schema": "YOUR_SCHEMA",
    "tables": ["TABLE1", "TABLE2"],
    "output_path": "./output/cdc",
    "parallelism": 2,
    "split_size": 8096
  }'

# 3. 提交任务
curl -X POST http://localhost:5001/api/cdc/tasks/{task-id}/submit
```

### 停止服务

```bash
# 停止所有服务
docker-compose down

# 停止并删除数据卷
docker-compose down -v
```

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

**Q: 容器启动失败，提示"无法连接到 JobManager"？**

A: 检查以下几点：
1. 确保 JobManager 容器已启动：`docker-compose ps`
2. 检查网络连接：`docker network inspect realtime-data-pipeline_default`
3. 查看 JobManager 日志：`docker-compose logs jobmanager`
4. 验证环境变量：`docker-compose config`

**Q: Web 界面无法访问？**

A: 排查步骤：
1. 检查 Nginx 容器状态：`docker-compose ps monitor-frontend`
2. 查看 Nginx 日志：`docker-compose logs monitor-frontend`
3. 验证端口映射：`docker ps | grep 8888`
4. 检查后端服务：`curl http://localhost:5001/api/health`

**Q: 数据库连接失败？**

A: 检查配置：
1. 验证 `.env` 文件中的数据库配置
2. 测试数据库连接：`sqlplus finance_user/password@helowin`
3. 检查防火墙和网络：`telnet oracle-host 1521`
4. 查看后端日志：`docker-compose logs monitor-backend`

### 任务管理相关

**Q: 任务提交失败，提示"任务配置缺少数据库连接信息"？**

A: 这是字段映射问题，已在最新版本修复：
1. 确保使用最新版本的代码
2. 重新构建镜像：`mvn clean package && docker-compose build monitor-backend`
3. 重启服务：`docker-compose restart monitor-backend`
4. 验证数据源配置：检查 `datasources` 表中是否有数据

**Q: 如何查看任务状态？**

A: 多种方式查看：
1. Web 界面：访问 http://localhost:8888，查看任务列表
2. Flink UI：访问 http://localhost:8081，查看运行中的作业
3. API 查询：`curl http://localhost:5001/api/cdc/tasks`
4. 数据库查询：`SELECT * FROM cdc_tasks;`

**Q: 任务重复提交怎么办？**

A: 系统已实现防重复机制：
1. 系统会自动检测相同 Schema 和表的任务
2. 如果已有运行中的任务，会拒绝提交
3. 查看冲突任务：检查 `runtime_jobs` 表
4. 手动取消冲突任务：通过 Flink UI 或 API

**Q: 如何取消运行中的任务？**

A: 两种方式：
```bash
# 方式1: 通过 Flink UI
# 访问 http://localhost:8081，找到作业，点击 Cancel

# 方式2: 通过 API
curl -X POST http://localhost:5001/api/cdc/jobs/{job-id}/cancel
```

### 数据输出相关

**Q: 输出文件在哪里？**

A: 输出文件位置：
- 默认路径：`output/cdc/`
- 文件命名：`YYYY-MM-DD--HH/TABLE_NAME_timestamp-uuid-partition.csv`
- 示例：`2026-03-09--09/IDS_ACCOUNT_INFO_20260309_092928270-a12381c7-0.csv`

**Q: 如何修改输出路径？**

A: 在创建任务时指定：
1. Web 界面：在任务创建页面修改"输出路径"
2. API 方式：设置 `output_path` 参数
3. 默认值：`./output/cdc`

**Q: CSV 文件格式是什么？**

A: CSV 文件包含以下列：
- 操作类型（INSERT/UPDATE/DELETE）
- 数据库名
- Schema 名
- 表名
- 主键值
- 变更前数据（JSON）
- 变更后数据（JSON）
- 时间戳

### 性能相关

**Q: 如何提高吞吐量？**

A: 优化建议：
1. 增加并行度：在任务创建时设置更高的并行度（如 4、8）
2. 增加 TaskManager：`docker-compose up -d --scale taskmanager=3`
3. 调整 split size：增大 `split_size` 参数（默认 8096）
4. 优化数据库：确保源数据库开启归档日志和补充日志

**Q: 内存使用率过高怎么办？**

A: 调整内存配置：
```yaml
# docker-compose.yml
taskmanager:
  environment:
    - FLINK_PROPERTIES=taskmanager.memory.process.size=2048m
```

**Q: Checkpoint 频繁失败？**

A: 排查步骤：
1. 检查存储空间：确保有足够的磁盘空间
2. 查看 Flink 日志：`docker-compose logs jobmanager | grep checkpoint`
3. 增加超时时间：修改 `flink-conf.yaml` 中的 checkpoint 配置
4. 检查网络：确保 JobManager 和 TaskManager 之间网络稳定

### 高可用相关

**Q: 如何启用高可用？**

A: 配置 ZooKeeper HA：
```bash
# 1. 修改 .env 文件
echo "HA_MODE=zookeeper" >> .env
echo "HA_ZOOKEEPER_QUORUM=zookeeper:2181" >> .env

# 2. 启动 ZooKeeper
docker-compose up -d zookeeper

# 3. 启动高可用集群
docker-compose up -d jobmanager jobmanager-standby
```

**Q: JobManager 崩溃后如何恢复？**

A: 自动恢复机制：
1. Docker 会自动重启容器（restart: unless-stopped）
2. 如果启用 HA，备用 JobManager 会接管（30秒内）
3. 作业会从最近的 Checkpoint 恢复
4. 查看恢复状态：访问 Flink UI

### 数据库相关

**Q: 如何从 JSON 配置迁移到数据库？**

A: 运行迁移脚本：
```bash
python3 sql/migrate-json-to-db.py
```
详细信息：[数据库迁移文档](sql/README-database-migration.md)

**Q: 如何备份任务配置？**

A: 导出数据库表：
```bash
# 导出数据源配置
sqlplus finance_user/password@helowin <<EOF
SET PAGESIZE 0
SET FEEDBACK OFF
SPOOL datasources_backup.csv
SELECT * FROM datasources;
SPOOL OFF
EXIT;
EOF

# 导出任务配置
sqlplus finance_user/password@helowin <<EOF
SPOOL cdc_tasks_backup.csv
SELECT * FROM cdc_tasks;
SPOOL OFF
EXIT;
EOF
```

### 其他问题

**Q: 支持哪些数据库？**

A: 当前支持 Oracle 11g+，理论上支持所有 Flink CDC 支持的数据库。

**Q: 如何升级系统版本？**

A: 升级步骤：
```bash
# 1. 备份数据库
# 2. 停止服务
docker-compose down

# 3. 更新代码
git pull

# 4. 重新构建
mvn clean package -DskipTests
docker-compose build

# 5. 启动新版本
docker-compose up -d
```

**Q: 如何查看系统日志？**

A: 查看日志命令：
```bash
# 查看所有服务日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f monitor-backend
docker-compose logs -f jobmanager
docker-compose logs -f taskmanager

# 查看最近 100 行
docker-compose logs --tail 100 monitor-backend
```

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

- [数据库迁移文档](sql/README-database-migration.md) - JSON 配置迁移到数据库
- [运行时作业管理](RUNTIME-JOB-MANAGEMENT.md) - 作业调度和冲突检测
- [迁移总结](MIGRATION-SUMMARY.md) - 系统架构演进历史
- [Log Mining 问题解决](LOG-MINING-FLUSH-ISSUE-RESOLVED.md) - 常见问题排查
- [设计文档](.kiro/specs/realtime-data-pipeline/design.md) - 系统架构和设计
- [需求文档](.kiro/specs/realtime-data-pipeline/requirements.md) - 功能需求说明
- [任务列表](.kiro/specs/realtime-data-pipeline/tasks.md) - 实现任务清单

## API 文档

### REST API 端点

**数据源管理**
- `GET /api/datasources` - 获取数据源列表
- `POST /api/datasources` - 创建数据源
- `GET /api/datasources/{id}` - 获取数据源详情
- `PUT /api/datasources/{id}` - 更新数据源
- `DELETE /api/datasources/{id}` - 删除数据源
- `POST /api/datasources/test` - 测试数据源连接

**CDC 任务管理**
- `GET /api/cdc/tasks` - 获取任务列表
- `POST /api/cdc/tasks` - 创建任务
- `GET /api/cdc/tasks/{id}` - 获取任务详情
- `POST /api/cdc/tasks/{id}/submit` - 提交任务
- `DELETE /api/cdc/tasks/{id}` - 删除任务

**Flink 作业管理**
- `GET /api/cdc/jobs` - 获取运行中作业列表
- `GET /api/cdc/jobs/{id}` - 获取作业详情
- `POST /api/cdc/jobs/{id}/cancel` - 取消作业

**集群管理**
- `GET /api/cluster/overview` - 获取集群概览
- `GET /api/cluster/taskmanagers` - 获取 TaskManager 列表
- `GET /api/cluster/jobs` - 获取所有作业

详细 API 文档请访问：http://localhost:5001/swagger-ui.html（如果启用）

## 性能指标

系统在标准配置下的性能指标：

| 指标 | 目标值 | 说明 |
|------|--------|------|
| 吞吐量 | 1万条/秒 | 每秒处理记录数（单表） |
| 延迟 P99 | < 10秒 | 端到端处理延迟 |
| Checkpoint 耗时 | < 30秒 | Checkpoint 完成时间 |
| 系统可用性 | 99.9% | 年度可用性目标 |
| 故障恢复时间 | < 5分钟 | 从故障到恢复的时间 |
| 容器启动时间 | < 30秒 | 容器初始化时间 |

## 许可证

Copyright © 2026 Realtime Data Pipeline Team

本项目采用企业内部许可证，未经授权不得用于商业用途。

## 联系方式

- **技术支持**: support@example.com
- **问题反馈**: <repository-url>/issues
- **文档**: <repository-url>/wiki
- **团队**: Realtime Data Pipeline Team

---

**最后更新**: 2026-03-09  
**版本**: 1.0.0  
**状态**: 生产就绪
# 启动所有服务（含构建）
./start.sh

# 启动所有服务（跳过构建）
./start.sh --skip-build

# 只启动特定服务
./start.sh backend              # 后端
./start.sh frontend             # 前端
./start.sh flink                # Flink 集群
./start.sh zookeeper            # ZooKeeper
./start.sh frontend backend     # 多服务组合

# 其他操作
./start.sh --stop               # 停止所有服务
./start.sh --stop backend       # 停止特定服务
./start.sh --restart            # 重启所有服务
./start.sh --restart frontend   # 重启特定服务
./start.sh --status             # 查看状态
./start.sh --logs               # 查看所有日志
./start.sh --logs backend       # 查看特定服务日志
./start.sh --build-only         # 只构建不启动
直接使用 docker-compose