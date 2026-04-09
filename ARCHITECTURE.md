# 系统架构说明

## flink-jobs 和 monitor-backend 的关系

### 概述

`flink-jobs` 和 `monitor-backend` 是两个**独立但协作**的模块，它们通过 Flink REST API 进行通信。

```
┌─────────────────────────────────────────────────────────────┐
│                     系统整体架构                              │
└─────────────────────────────────────────────────────────────┘

┌──────────────────┐         ┌──────────────────┐
│  Monitor Frontend│◄────────┤  Monitor Backend │
│   (Vue 3 前端)   │  HTTP   │  (Spring Boot)   │
└──────────────────┘         └────────┬─────────┘
                                      │
                                      │ Flink REST API
                                      │ (提交任务、查询状态)
                                      │
                             ┌────────▼─────────┐
                             │  Flink Cluster   │
                             │  ┌─────────────┐ │
                             │  │ JobManager  │ │
                             │  └──────┬──────┘ │
                             │         │        │
                             │  ┌──────▼──────┐ │
                             │  │TaskManager×3│ │
                             │  └──────┬──────┘ │
                             │         │        │
                             │  ┌──────▼──────┐ │
                             │  │ flink-jobs  │ │
                             │  │  (CDC 任务)  │ │
                             │  └──────┬──────┘ │
                             └─────────┼────────┘
                                       │
                                       ▼
                              ┌────────────────┐
                              │ Oracle Database│
                              │  (CDC Source)  │
                              └────────────────┘
                                       │
                                       ▼
                              ┌────────────────┐
                              │  File System   │
                              │  (CSV Output)  │
                              └────────────────┘
```

## 模块详解

### 1. flink-jobs 模块

**职责**: Flink CDC 数据捕获任务

**主要功能**:
- 从 Oracle 数据库捕获变更数据（CDC）
- 处理 DML 事件（INSERT、UPDATE、DELETE）
- 处理 DDL 事件（CREATE、ALTER、DROP）
- 将数据写入 CSV 文件
- 支持 checkpoint 和故障恢复

**运行位置**: Flink 集群内部（JobManager + TaskManager）

**主类**: `com.realtime.pipeline.CdcJobMain`

**依赖**:
- Flink Core (streaming-java, clients, runtime)
- Flink CDC Connector (oracle-cdc 3.4.0)
- Oracle JDBC Driver
- Flink File Connectors

**输出**: `flink-jobs-1.0.0-SNAPSHOT.jar` (~129MB)

**部署方式**:
```bash
# 方式 1: 打包到 Docker 镜像中
# flink-jobs JAR 被复制到 /opt/flink/usrlib/flink-jobs.jar

# 方式 2: 通过 Flink REST API 提交
flink run -d -c com.realtime.pipeline.CdcJobMain flink-jobs.jar
```

### 2. monitor-backend 模块

**职责**: 监控和管理平台后端

**主要功能**:
- 提供 REST API 供前端调用
- 管理 Flink 任务（提交、停止、查询状态）
- 管理数据源配置
- 查询 CDC 事件和统计信息
- 查看输出文件
- 健康检查和集群监控

**运行位置**: 独立的 Spring Boot 应用（Docker 容器）

**主类**: `com.realtime.UnifiedApplication`

**依赖**:
- Spring Boot Web
- Spring Boot Actuator
- Spring Boot JDBC
- Flink Client API（用于与 Flink 集群通信）
- Oracle JDBC Driver

**输出**: `monitor-backend-1.0.0-SNAPSHOT.jar` (~89MB)

**部署方式**:
```bash
# 作为独立的 Spring Boot 应用运行
java -jar monitor-backend.jar --spring.profiles.active=docker
```

## 交互方式

### 1. Monitor Backend → Flink Cluster

Monitor Backend 通过 **Flink REST API** 与 Flink 集群交互：

```java
// FlinkService.java
public class FlinkService {
    private final String flinkJobManagerUrl = "http://jobmanager:8081";
    
    // 提交任务
    public String submitJob(String jarPath, String mainClass) {
        RestTemplate restTemplate = new RestTemplate();
        String url = flinkJobManagerUrl + "/jars/upload";
        // 上传 JAR 并提交任务
    }
    
    // 查询任务状态
    public JobStatus getJobStatus(String jobId) {
        String url = flinkJobManagerUrl + "/jobs/" + jobId;
        // 获取任务状态
    }
    
    // 停止任务
    public void cancelJob(String jobId) {
        String url = flinkJobManagerUrl + "/jobs/" + jobId + "/cancel";
        // 取消任务
    }
}
```

**Flink REST API 端点**:
- `GET /overview` - 集群概览
- `GET /jobs` - 任务列表
- `GET /jobs/:jobid` - 任务详情
- `POST /jars/upload` - 上传 JAR
- `POST /jars/:jarid/run` - 运行任务
- `PATCH /jobs/:jobid` - 取消任务

### 2. flink-jobs ← Oracle Database

flink-jobs 通过 **Flink CDC Connector** 从 Oracle 读取数据：

```java
// CdcJobMain.java
OracleSource<String> oracleSource = OracleSource.<String>builder()
    .hostname("oracle-host")
    .port(1521)
    .database("ORCL")
    .schemaList("IDS")
    .tableList("IDS.ACCOUNT_INFO", "IDS.TRANS_INFO")
    .username("cdc_user")
    .password("password")
    .deserializer(new JsonDebeziumDeserializationSchema())
    .build();

DataStream<String> cdcStream = env
    .fromSource(oracleSource, WatermarkStrategy.noWatermarks(), "Oracle CDC Source")
    .setParallelism(1); // CDC 源必须并行度为 1
```

### 3. flink-jobs → File System

flink-jobs 将处理后的数据写入文件系统：

```java
// 写入 CSV 文件
FileSink<String> sink = FileSink
    .forRowFormat(new Path("output/cdc/"), new SimpleStringEncoder<String>("UTF-8"))
    .withRollingPolicy(
        DefaultRollingPolicy.builder()
            .withRolloverInterval(Duration.ofMinutes(15))
            .withInactivityInterval(Duration.ofMinutes(5))
            .withMaxPartSize(MemorySize.ofMebiBytes(128))
            .build())
    .build();

cdcStream.sinkTo(sink);
```

### 4. Monitor Backend → File System

Monitor Backend 读取 flink-jobs 生成的文件：

```java
// OutputController.java
@GetMapping("/api/output/files")
public List<FileInfo> listOutputFiles() {
    Path outputDir = Paths.get("output/cdc");
    // 列出所有 CSV 文件
}

@GetMapping("/api/output/files/{filename}")
public String readFile(@PathVariable String filename) {
    // 读取文件内容
}
```

## 数据流向

```
┌──────────────┐
│   Oracle DB  │
└──────┬───────┘
       │ CDC (LogMiner)
       │
       ▼
┌──────────────────────────────────┐
│      flink-jobs (CDC 任务)        │
│  ┌────────────────────────────┐  │
│  │ 1. Oracle CDC Source       │  │
│  │    (读取 redo log)          │  │
│  └────────┬───────────────────┘  │
│           │                       │
│  ┌────────▼───────────────────┐  │
│  │ 2. 过滤和转换              │  │
│  │    - DDL/DML 分离          │  │
│  │    - 表过滤                │  │
│  │    - 格式转换              │  │
│  └────────┬───────────────────┘  │
│           │                       │
│  ┌────────▼───────────────────┐  │
│  │ 3. File Sink               │  │
│  │    (写入 CSV)              │  │
│  └────────┬───────────────────┘  │
└───────────┼───────────────────────┘
            │
            ▼
   ┌────────────────┐
   │  output/cdc/   │
   │  ├── 2026-03-24│
   │  │   └── *.csv │
   │  └── 2026-03-25│
   │      └── *.csv │
   └────────┬───────┘
            │
            │ 读取文件
            │
   ┌────────▼────────────┐
   │  monitor-backend    │
   │  ┌────────────────┐ │
   │  │ REST API       │ │
   │  │ - 文件列表     │ │
   │  │ - 文件内容     │ │
   │  │ - 统计信息     │ │
   │  └────────┬───────┘ │
   └───────────┼─────────┘
               │
               │ HTTP
               │
   ┌───────────▼─────────┐
   │  monitor-frontend   │
   │  (Vue 3 界面)       │
   └─────────────────────┘
```

## 依赖关系

### 编译时依赖

```
父 POM (realtime-data-pipeline-parent)
├── flink-jobs (独立模块)
│   └── 依赖: Flink Core, Flink CDC, Oracle JDBC
│
└── monitor-backend (独立模块)
    └── 依赖: Spring Boot, Flink Client API, Oracle JDBC
```

**重要**: flink-jobs 和 monitor-backend 之间**没有直接的 Maven 依赖关系**。它们是完全独立的模块。

### 运行时依赖
f
```
monitor-backend (运行时)
└── 依赖 Flink Cluster (通过 REST API)
    └── 运行 flink-jobs (作为 Flink 任务)
        └── 连接 Oracle Databased
```

## 部署架构

### Docker Compose 部署

```yaml
services:
  # ZooKeeper (高可用协调)
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    
  # Flink JobManager
  jobmanager:
    image: flink-jobmanager:latest
    # 包含 flink-jobs.jar
    volumes:
      - ./output:/opt/flink/output
      
  # Flink TaskManager (3个实例)
  taskmanager:
    image: flink-taskmanager:latest
    # 包含 flink-jobs.jar
    scale: 3
    
  # Monitor Backend
  monitor-backend:
    image: monitor-backend:latest
    # 独立的 Spring Boot 应用
    depends_on:
      - jobmanager
    environment:
      - FLINK_JOBMANAGER_URL=http://jobmanager:8081
      
  # Monitor Frontend
  monitor-frontend:
    image: monitor-frontend:latest
    depends_on:
      - monitor-backend
```

## 通信协议

### 1. Monitor Backend ↔ Flink Cluster

**协议**: HTTP REST API

**示例**:
```bash
# 查询集群状态
curl http://jobmanager:8081/overview

# 查询任务列表
curl http://jobmanager:8081/jobs

# 提交任务
curl -X POST http://jobmanager:8081/jars/:jarid/run \
  -H "Content-Type: application/json" \
  -d '{"entryClass":"com.realtime.pipeline.CdcJobMain"}'
```

### 2. flink-jobs ↔ Oracle Database

**协议**: JDBC + Oracle LogMiner

**配置**:
```java
OracleSource.builder()
    .hostname("oracle-host")
    .port(1521)
    .database("ORCL")
    .username("cdc_user")
    .password("password")
    // LogMiner 配置
    .debeziumProperties(properties)
    .build();
```

### 3. Monitor Frontend ↔ Monitor Backend

**协议**: HTTP REST API

**示例**:
```javascript
// 获取任务列表
axios.get('http://localhost:5001/api/jobs')

// 提交 CDC 任务
axios.post('http://localhost:5001/api/cdc/submit', {
  dataSourceId: 1,
  tables: ['ACCOUNT_INFO', 'TRANS_INFO']
})
```

## 配置管理

### flink-jobs 配置

**位置**: 
- `flink-jobs/src/main/resources/log4j2.xml`
- `docker/jobmanager/flink-conf.yaml`
- `docker/taskmanager/flink-conf.yaml`

**配置内容**:
- Flink 运行时参数（内存、并行度、checkpoint）
- CDC 连接参数（数据库地址、用户名、密码）
- 输出路径配置

### monitor-backend 配置

**位置**:
- `monitor-backend/src/main/resources/application.yml`
- `monitor-backend/src/main/resources/application-docker.yml`

**配置内容**:
- Spring Boot 配置（端口、日志）
- Flink 集群地址
- 数据源配置
- 文件路径配置

## 数据共享

### 共享文件系统

flink-jobs 和 monitor-backend 通过**共享文件系统**交换数据：

```yaml
# docker-compose.yml
volumes:
  - ./output:/opt/flink/output  # flink-jobs 写入
  - ./output:/app/output        # monitor-backend 读取
```

**目录结构**:
```
output/cdc/
├── 2026-03-24--16/
│   ├── IDS_ACCOUNT_INFO_xxx-0.csv
│   ├── IDS_TRANS_INFO_xxx-0.csv
│   └── DDL_EVENTS_xxx-0.csv
└── 2026-03-25--10/
    └── ...
```

## 扩展性

### 水平扩展

```bash
# 扩展 TaskManager（增加处理能力）
docker-compose up -d --scale taskmanager=5

# 扩展 Monitor Backend（增加 API 吞吐量）
docker-compose up -d --scale monitor-backend=3
```

### 高可用

```yaml
# 启用 Flink HA
jobmanager:
  environment:
    - HA_MODE=zookeeper
    - HA_ZOOKEEPER_QUORUM=zookeeper:2181
    
# 启动备用 JobManager
jobmanager-standby:
  image: flink-jobmanager:latest
  environment:
    - HA_MODE=zookeeper
```

## 总结

### 关键点

1. **独立性**: flink-jobs 和 monitor-backend 是两个完全独立的模块
2. **通信方式**: 通过 Flink REST API 进行通信
3. **数据共享**: 通过共享文件系统交换数据
4. **职责分离**: 
   - flink-jobs: 数据捕获和处理
   - monitor-backend: 管理和监控
5. **部署方式**: 
   - flink-jobs: 打包到 Flink 镜像，作为 Flink 任务运行
   - monitor-backend: 独立的 Spring Boot 应用

### 优势

- ✅ 模块化设计，职责清晰
- ✅ 独立部署和扩展
- ✅ 松耦合，易于维护
- ✅ 支持多种部署方式
- ✅ 便于测试和调试

### 相关文档

- `PROJECT-RESTRUCTURE-SUMMARY.md` - 项目重构总结
- `MULTI-MODULE-MIGRATION.md` - 多模块迁移文档
- `RUNTIME-JOB-MANAGEMENT.md` - 运行时任务管理
