# Kiro Data Pipeline - 项目手册

> **版本**: 1.0.0  
> **最后更新**: 2026-04-28

---

## 目录

1. [项目概述](#项目概述)
2. [系统架构](#系统架构)
3. [技术栈](#技术栈)
4. [项目结构详解](#项目结构详解)
5. [核心功能模块](#核心功能模块)
6. [部署指南](#部署指南)
7. [API 接口文档](#api-接口文档)
8. [运维手册](#运维手册)
9. [常见问题](#常见问题)

---

## 项目概述

### 1.1 简介

Kiro Data Pipeline 是一个基于 Apache Flink 的企业级 CDC（Change Data Capture）数据采集平台，实现从 Oracle 数据库实时捕获变更数据（DML + DDL），通过 Web 界面进行可视化管理和监控。

### 1.2 核心特性

| 特性 | 描述 |
|------|------|
| **实时 CDC** | 使用 Flink CDC 3.4 从 Oracle 实时捕获数据变更 |
| **DML + DDL** | 同时支持数据变更（INSERT/UPDATE/DELETE）和 Schema 变更 |
| **高可用架构** | Flink JobManager HA（ZooKeeper / Kubernetes 原生）|
| **Web 管理界面** | Vue 3 + Element Plus 现代化前端 |
| **安全认证** | JWT Token + AES 密码加密 |
| **Checkpoint** | 支持故障恢复和数据一致性保证 |
| **容器化部署** | Docker Compose 一键部署 |
| **K8s 支持** | Kubernetes 生产环境部署 |

### 1.3 应用场景

- **数据同步**: 实时同步 Oracle 数据到数仓、数据湖
- **数据归档**: 历史数据归档和审计追踪
- **数据复制**: 主从数据库实时同步
- **变更通知**: 触发下游业务处理流程
- **数据集成**: ETL/ELT管道的数据源

---

## 系统架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────────┐
│                        用户层 (User Layer)                           │
│                                                                 │
│  ┌───────────────────┐          ┌──────────────────────┐        │
│  │   Web Browser     │          │    API Client        │        │
│  │   (Vue 3 前端)     │──HTTP───▶│   (curl/Postman)    │        │
│  └────────┬──────────┘          └───────────┬──────────┘        │
└───────────┼──────────────────────────────────┼───────────────────┘
            │                                   │
┌───────────▼───────────────────────────────────▼───────────────────┐
│                    接入层 (Access Layer)                            │
│                                                                 │
│  ┌───────────────────┐          ┌──────────────────────┐        │
│  │   Nginx           │◄────────▶│   Spring Boot        │        │
│  │   (Vue 3 静态服务) │          │   (REST API)         │        │
│  │    Port: 80       │          │    Port: 5001        │        │
│  └────────┬──────────┘          └───────────┬──────────┘        │
└───────────┼──────────────────────────────────┼───────────────────┘
            │                                   │
┌───────────▼───────────────────────────────────▼───────────────────┐
│                    服务层 (Service Layer)                           │
│                                                                 │
│  ┌───────────────────┐          ┌──────────────────────┐        │
│  │   Monitor Backend │◄─Flink──▶│    Flink Cluster     │        │
│  │   (监控管理)      │   API    │                      │        │
│  │                   │          │ ┌──────────────┐    │        │
│  └───────────────────┘          │ │ JobManager   │    │        │
│                                 │ │ (HA 主 + 备)   │    │        │
│                                 │ └───────┬────────┘    │        │
│                                 │         │              │        │
│                                 │ ┌───────▼──────────┐  │        │
│                                 │ │ TaskManager × N  │  │        │
│                                 │ │ (CDC Jobs)       │  │        │
│                                 │ └────────┬──────────┘  │        │
└─────────────────────────────────┼──────────┴──────────────┘        │
                                  │                                   │
┌─────────────────────────────────▼───────────────────────────────────┐
│                    数据层 (Data Layer)                               │
│                                                                 │
│  ┌───────────────────┐          ┌──────────────────────┐        │
│  │   Oracle Database │◄────────▶│    File System       │        │
│  │   (元数据存储)     │          │    (CSV Output)      │        │
│  │   ┌─────────────┐  │          │    /output/cdc/     │        │
│  │   │ datasources │  │          │    └──日期/表分区    │        │
│  │   │ cdc_tasks   │  │          │       *.csv         │        │
│  │   │ runtime_jobs│  │          └─────────────────────┘        │
│  │   │ users       │  │                                          │
│  │   └─────────────┘  │          ┌───────────────────┐        │
│  │                    │          │   Oracle Source   │        │
│  └───────────────────┘          │   (CDC 源数据库)    │        │
│                                 └─────────────────────┘        │
└───────────────────────────────────────────────────────────────────┘
```

### 2.2 数据流向图

```
时序图:

用户 ──▶ Web 前端 ──▶ Monitor Backend ──▶ Flink Cluster
                     │              │
                     │              ├──▶ 提交 CDC Job ──▶ Oracle (CDC 源)
                     │              │                   │
                     │              └───────────────────┼──▶ CSV Output
                     │                                  │
                     └──▶ 查询状态 ◀─────────────────────┴─┘
```

### 2.3 模块关系图

```
┌─────────────────────────────────────────────┐
│         realtime-data-pipeline              │
│              (父 POM)                        │
├───────────────┬─────────────────────────────┤
│               │                              │
▼               ▼                              ▼
┌──────────┐  ┌──────────────┐           ┌─────────┐
│flink-jobs│  │monitor-backend│          │  K8s    │
│(CDC 任务) │  │ (监控服务)   │          │部署配置 │
└────┬─────┘  └───────┬──────┘          └─────────┘
     │                │
     │   Flink        │
     │   REST API     │
     └────────┬───────┘
              │
         通信桥梁
```

---

## 技术栈

### 3.1 核心技术选型

| 层次 | 技术 | 版本 | 说明 |
|------|------|------|------|
| **流处理引擎** | Apache Flink | 1.20.0 | 分布式流计算 |
| **CDC 连接器** | Flink CDC | 3.4.0 | Oracle 变更捕获 |
| **后端框架** | Spring Boot | 3.4.13 | REST API 服务 |
| **前端框架** | Vue 3 | ^3.4.0 | 响应式 UI |
| **数据库** | Oracle JDBC | 21.9.0.0 | 数据库连接 |
| **构建工具** | Maven | 3.6+ | 项目构建 |
| **JDK** | OpenJDK | 17 | Java 运行时 |

### 3.2 辅助技术

| 类别 | 技术 | 版本/说明 |
|------|------|-----------|
| **容器化** | Docker | 20.10+ |
| **编排** | Docker Compose | 2.0+ |
| **K8s** | Kubernetes | 1.19+ |
| **HA 协调** | ZooKeeper | 3.8.0 (可选) |
| **日志** | Log4j2 | 2.23.1 |
| **序列化** | Jackson | 2.17.3 |
| **测试** | JUnit 5 | 5.10.0 |

### 3.3 UI 技术栈

| 组件 | 技术 |
|------|------|
| UI 框架 | Vue 3 Composition API |
| 路由 | Vue Router ^4.2.0 |
| HTTP | Axios ^1.6.0 |
| 图表 | Chart.js ^4.5.1 |
| 图标 | @icon-park/vue-next ^1.4.2 |
| CSS | Element Plus (部分) |

---

## 项目结构详解

### 4.1 目录树概览

```
data-pipeline/
├── pom.xml                          # Maven 父 POM
├── docker-compose.yml               # Docker Compose 配置
│
├── flink-jobs/                      # Flink CDC 作业模块
│   ├── pom.xml                     # Maven 配置
│   └── src/main/java/com/realtime/pipeline/
│       └── CdcJobMain.java         # CDC 作业主类 (616 行)
│
├── monitor-backend/                 # 监控后端服务
│   ├── pom.xml                     # Maven 配置
│   ├── Dockerfile                  # Docker 镜像构建
│   └── src/main/java/com/realtime/
│       ├── UnifiedApplication.java # 应用入口
│       ├── config/                 # 配置类 (5 个)
│       ├── controller/             # REST 控制器 (13 个)
│       ├── dto/                    # DTO 对象 (5 个)
│       ├── entity/                 # JPA 实体 (1 个)
│       ├── repository/             # 数据访问层 (4 个)
│       ├── security/               # 安全认证 (2 个)
│       ├── service/                # 业务服务 (9 个)
│       └── util/                   # 工具类 (3 个)
│
├── monitor/frontend-vue/           # Vue 3 前端
│   ├── src/
│   │   ├── api/                  # API 客户端
│   │   ├── components/           # Vue 组件 (2 个)
│   │   ├── router/               # 路由配置
│   │   └── views/                # 页面视图 (7 个)
│   ├── index.html
│   └── vite.config.js
│
├── docker/                         # Docker 配置
│   ├── jobmanager/
│   │   ├── Dockerfile            # JobManager 镜像
│   │   └── flink-conf.yaml       # Flink 配置
│   └── taskmanager/
│       ├── Dockerfile            # TaskManager 镜像
│       └── flink-conf.yaml
│
├── k8s/                            # Kubernetes 配置
│   ├── namespace.yaml            # K8s 命名空间
│   ├── flink-*.yaml              # Flink 部署配置
│   ├── monitor-*.yaml            # Monitor 部署配置
│   └── deploy.sh                 # 一键部署脚本
│
├── sql/                            # SQL 脚本
│   ├── setup-*.sql               # 数据库初始化
│   ├── create-*.sql              # 建表脚本
│   └── migrate-*.sql             # 数据迁移
│
├── output/                         # CDC 输出目录 (挂载点)
├── data/                           # Flink 数据持久化
│   ├── flink-checkpoints/
│   ├── flink-savepoints/
│   └── flink-ha/
│
├── logs/                           # 日志目录
├── .env.example                    # 环境变量模板
└── *.md                            # 文档文件
```

### 4.2 flink-jobs 模块详解

**职责**: Flink CDC 数据捕获作业

```
flink-jobs/
├── pom.xml                                    # 依赖：Flink + Flink CDC
└── src/main/java/com/realtime/pipeline/
    └── CdcJobMain.java                       # CDC 作业主类
        ├── main()                            # 作业入口
        │   ├── 参数解析 (--key value)
        │   ├── JDBC 连接测试
        │   ├── Flink 环境配置 (StreamExecutionEnvironment)
        │   ├── Checkpoint 设置 (5 分钟)
        │   ├── Oracle CDC Source 构建
        │   └── DDL/DML分离处理
        │
        ├── isDDLEvent()                      # 判断 DDL 事件
        ├── convertDDLToCSV()                 # DDL → CSV (仅列变更)
        │   ├── ADD_COLUMN
        │   ├── MODIFY_COLUMN  
        │   └── DROP_COLUMN
        │
        ├── convertToCSV()                    # DML → CSV
        │   ├── 时间戳格式化
        │   ├── INSERT/UPDATE/DELETE处理
        │   └── 字段值转义
        │
        ├── extractJsonValue()                # JSON 解析工具
        └── formatTimeField()                 # TIME/DATE格式化
```

**核心参数**:
| 参数 | 说明 | 默认值 |
|------|------|--------|
| DATABASE_HOST | Oracle 主机 | - |
| DATABASE_PORT | Oracle 端口 | 1521 |
| DATABASE_USERNAME | 用户名 | system |
| DATABASE_PASSWORD | 密码 | - |
| DATABASE_SID | Oracle SID | helowin |
| DATABASE_SCHEMA | Schema 名 | finance_user |
| DATABASE_TABLES | 监控表列表 | trans_info |
| OUTPUT_PATH | CSV 输出路径 | ./output/cdc |

### 4.3 monitor-backend 模块详解

**职责**: 监控管理后端服务（Spring Boot）

```
monitor-backend/
└── src/main/java/com/realtime/
    ├── UnifiedApplication.java              # @SpringBootApplication 入口
    │
    ├── config/                              # 配置类
    │   ├── AppConfig.java                  # 应用配置 (OutputPath/ConfigDir)
    │   ├── DataSourceConfig.java           # Oracle 数据源
    │   ├── PasswordEncoderConfig.java      # BCrypt 配置
    │   ├── SecurityConfig.java             # Spring Security + JWT
    │   └── WebConfig.java                  # CORS 配置
    │
    ├── controller/                          # REST Controller
    │   ├── AuthController.java             # 登录/Token 刷新
    │   ├── AdminController.java            # 用户管理
    │   ├── DataSourceController.java       # 数据源 CRUD
    │   ├── CdcTaskController.java          # CDC 任务 CRUD
    │   ├── RuntimeJobController.java       # 运行中作业管理
    │   ├── JobController.java              # Flink 作业操作
    │   ├── ClusterController.java          # 集群状态查询
    │   ├── CdcEventsController.java        # CDC 事件监控
    │   ├── OutputController.java           # CSV 文件查询/下载
    │   └── HealthController.java           # 健康检查
    │
    ├── dto/                                 # DTO 对象
    │   ├── ApiResponse.java                # 统一响应包装
    │   ├── TaskConfig.java                 # CDC 任务配置
    │   ├── DataSourceConfig.java           # 数据源 DTO
    │   ├── RuntimeJob.java                 # 运行时作业
    │   └── CdcSubmitRequest.java           # 任务提交请求
    │
    ├── entity/                              # JPA Entity
    │   └── User.java                       # 用户实体
    │
    ├── repository/                          # Repository (JPA)
    │   ├── DataSourceRepository.java
    │   ├── TaskRepository.java
    │   ├── RuntimeJobRepository.java
    │   └── CdcFileRepository.java          # CSV 文件元数据
    │
    ├── security/                            # Security 实现
    │   ├── JwtTokenProvider.java           # JWT 签发/验证
    │   └── JwtAuthenticationEntryPoint.java# 401 处理
    │
    ├── service/                             # Business Service
    │   ├── FlinkService.java               # Flink REST API 客户端
    │   ├── DataSourceService.java          # 数据源服务
    │   ├── CdcTaskService.java             # CDC 任务服务
    │   ├── RuntimeJobService.java          # 运行时作业调度
    │   ├── EmbeddedCdcService.java         # CDC 提交服务
    │   ├── CdcEventsService.java           # CDC 事件服务
    │   ├── CdcStatsService.java            # CDC 统计服务
    │   ├── OutputFileService.java          # 文件查询服务
    │   └── UserService.java                # 用户管理服务
    │
    └── util/                                # Utilities
        ├── PasswordEncryptionUtil.java     # AES 加密工具
        ├── EnvironmentPasswordUtil.java    # 环境密码处理
        └── XssSanitizer.java               # XSS 防护
```

### 4.4 monitor/frontend-vue 模块详解

**职责**: Vue 3 前端管理界面

```
monitor/frontend-vue/
└── src/
    ├── main.js                             # 应用入口
    │   ├── createApp(Vue 3)
    │   ├─▶ use(router)
    │   └─▶ mount('#app')
    │
    ├── App.vue                             # 根组件
    │   └── <router-view/>
    │
    ├── router/index.js                     # Vue Router 配置
    │   ├── /login                          # 登录页 (无认证)
    │   └── /                               # 需要 Token
    │       ├── home                        # 仪表盘
    │       ├── datasources                 # 数据源管理
    │       ├── tasks                       # CDC 任务列表
    │       ├── tasks/create                # 创建任务
    │       ├── jobs                        # Flink 作业监控
    │       ├── cluster                     # 集群状态
    │       └── events                      # CDC 事件监控
    │
    ├── api/index.js                        # Axios 实例 + API 定义
    │   ├── api/login
    │   ├── api/datasources
    │   ├── api/cdc/tasks
    │   ├── api/jobs
    │   └── ...
    │
    ├── components/
    │   ├── AppHeader.vue                   # 导航栏 + Logo
    │   └── DashboardCard.vue               # 统计卡片组件
    │
    ├── views/
    │   ├── LoginView.vue                   # 登录表单
    │   ├── HomeView.vue                    # 仪表盘 (统计卡片)
    │   ├── DataSourceView.vue              # 数据源 CRUD + 表格
    │   ├── TaskListView.vue                # CDC 任务列表 + 操作
    │   ├── TaskCreateView.vue              # 创建任务表单 (表选择)
    │   ├── JobsView.vue                    # Flink 作业监控 + 图表
    │   └── ClusterView.vue                 # 集群状态 (JobManager/TaskManager)
    │   └── CdcEventsView.vue               # CDC 事件实时流
    │
    └── assets/main.css                     # 全局样式
```

### 4.5 sql 模块详解

**职责**: Oracle 数据库初始化脚本

```
sql/
├── setup-flink-user-schema.sql             # 创建 Flink Schema
│   ├── CREATE TABLESPACE flink_data
│   └── CREATE USER finance_user
│
├── setup-metadata-tables.sql               # 元数据表创建
│   ├── CREATE TABLE datasources            # 数据源配置
│   ├── CREATE TABLE cdc_tasks             # CDC 任务定义
│   ├── CREATE TABLE runtime_jobs          # 运行时作业
│   └── CREATE TABLE cdc_events            # CDC 事件日志
│
├── setup-runtime-jobs-table.sql            # runtime_jobs表创建
├── add-savepoint-columns.sql               # 添加 savepoint 字段
├── add-status-column.sql                   # 添加状态字段
│
├── enable-oracle-cdc.sql                   # Oracle CDC 配置
│   ├── ALTER DATABASE SUPPLEMENTAL LOGGING
│   └── GRANT LOGMINER...
│
├── check-*.sql                             # 状态检查脚本
└── migrate-*.sql                           # 数据迁移脚本
```

---

## 核心功能模块

### 5.1 CDC 任务管理

#### 5.1.1 功能描述
支持创建、编辑、删除、启动、停止 CDC 任务，实现从 Oracle 数据库实时捕获数据变更。

#### 5.1.2 核心类图

```
┌─────────────────────────────────┐
│    CdcTaskController           │  REST API
├─────────────────────────────────┤
│ POST   /api/cdc/tasks          │  创建任务
│ GET    /api/cdc/tasks          │  查询列表
│ PUT    /api/cdc/tasks/{id}     │  更新任务
│ DELETE /api/cdc/tasks/{id}     │  删除任务
└──────────────┬──────────────────┘
               │
               ▼
┌─────────────────────────────────┐
│    CdcTaskService              │  业务逻辑
├─────────────────────────────────┤
│ createTask()                   │  创建 + Schema 检查
│ updateTask()                   │  更新配置
│ deleteTask()                   │  删除 + 停止作业
│ submitTask()                   │  提交 CDC Job
└──────────────┬──────────────────┘
               │
               ▼
┌─────────────────────────────────┐
│    TaskRepository              │  数据访问
├─────────────────────────────────┤
│ CRUD by TaskConfig             │  JPA Repository
└──────────────┬──────────────────┘
               │
               ▼
┌─────────────────────────────────┐
│       cdc_tasks 表             │  Oracle 数据库
├─────────────────────────────────┤
│ id, name, datasource_id,       │
│ schema_name, tables,           │
│ parallelism, split_size,       │
│ status, flink_job_id           │
└─────────────────────────────────┘
```

#### 5.1.3 TaskConfig DTO

```java
public class TaskConfig {
    private String id;              // 任务 ID (UUID)
    private String name;            // 任务名称
    private String datasourceId;    // 数据源 ID
    private String schemaName;      // Schema 名 (如：finance_user)
    private String tables;          // 表列表 (逗号分隔)
    private Integer parallelism;    // Flink 并行度
    private Long splitSize;        // CDC Split Size (8192)
    private String status;          // CREATED/PENDING/RUNNING/STOPPED
    private String flinkJobId;     // Flink Job ID
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
```

### 5.2 Flink 作业提交流程

#### 5.2.1 时序图

```
前端          Backend            Flink Cluster         Oracle
 │              │                     │                  │
 ├──POST /submit┴                     │                  │
 │              ├─────────────构建 CDC Source ───────┬───▶│
 │              │   (EmbeddedCdcService)             │    │
 │              ├─────────提交作业───────┐           │    │
 │              │   (FlinkService)      │           │    │
 │              │         │             ▼ 读取 Redo Log
 │              │         ├──────▶ 启动 CDC Job ◀─────────┘
 │              │         │                  │
 │◀──201 Created┤         │      ┌──▶ 写入 CSV
 │              │         │      │    /output/cdc/*.csv
 │              │◀──JobId: xxx   │
 └──────────────┴───────────────┴──▶ 实时输出
```

#### 5.2.2 EmbeddedCdcService (核心方法)

```java
// 构建 CDC Source DataStream
DataStream<String> cdcStream = env
    .fromSource(oracleSource, WatermarkStrategy.noWatermarks(), "Oracle CDC")
    .setParallelism(1);  // CDC Source 必须并行度为 1

// DDL/DML分离处理
DataSelector<String> selector = DataSelector.<String>builder()
    .forDDL(selectorContext -> isDDLEvent(event) ? event : null)
    .forDML(selectorContext -> !isDDLEvent(event) ? event : null)
    .build();

// DML -> CSV File Sink  
cdcStream.filter(event -> !isDDLEvent(event))
         .map(this::convertToCSV)
         .sinkTo(csvFileSink);

// DDL -> Separate CSV  
cdcStream.filter(this::isDDLEvent)
         .map(this::convertDDLToCSV)
         .sinkTo(ddlFileSink);
```

### 5.3 安全认证模块

#### 5.3.1 JWT Token 流程

```
┌───────────────────────────────────────────────────────┐
│  1. 用户登录                                         │
│     POST /api/auth/login {username, password}        │
├───────────────────────────────────────────────────────┤
│  2. Backend 验证用户                                 │
│     - BCrypt 校验密码                                │
├───────────────────────────────────────────────────────┤
│  3. JwtTokenProvider.generateToken()                 │
│     - Payload: {id, username, exp}                   │
│     - 使用 JWT_SECRET (HS256) 签名                   │
├───────────────────────────────────────────────────────┤
│  4. 返回 Token                                       │
│     {token: "eyJhbG...", expiresIn: 86400}          │
├───────────────────────────────────────────────         │
│  5. 前端存储 Token (localStorage)                     │
│     - 每次请求添加 Authorization: Bearer {token}      │
└───────────────────────────────────────────────────────┘
```

#### 5.3.2 AES 密码加密

```java
// 数据源密码使用 AES 加密存储
public class PasswordEncryptionUtil {
    // 加密流程:
    // plaintext -> AES Encrypt (aesKey) -> Base64
    
    // 解密流程:
    // Base64 -> AES Decrypt (aesKey) -> plaintext
}
```

### 5.4 高可用架构

#### 5.4.1 HA 对比表

| 模式 | ZooKeeper HA | Kubernetes 原生 HA |
|------|-------------|-------------------|
| **协调器** | ZooKeeper | K8s ConfigMap |
| **Leader 选举** | ZK Watch | K8s Controller |
| **配置存储** | ZooKeeper | ConfigMap |
| **适用场景** | Docker Compose | Kubernetes |

#### 5.4.2 HA 配置示例 (Docker Compose + ZooKeeper)

```yaml
# docker-compose.yml
jobmanager:
  environment:
    - HA_MODE=zookeeper
    - HA_ZOOKEEPER_QUORUM=zookeeper:2181
    - HA_CLUSTER_ID=/realtime-pipeline
    - HA_STORAGE_DIR=file:///opt/flink/ha
```

---

## 部署指南

### 6.1 快速开始 (Docker Compose)

#### Step 1: 配置环境变量

```bash
# 复制模板
cp .env.example .env

# 编辑配置 (必需项)
vi .env
```

**环境变量说明:**

```bash
# ==================== 数据库配置 ====================
DATABASE_HOST=localhost          # Oracle 主机 (元数据存储)
DATABASE_PORT=1521
DATABASE_SID=helowin
DATABASE_USERNAME=finance_user
DATABASE_PASSWORD=your-password

# ==================== 安全配置 ====================
JWT_SECRET=your-32-char-secret-key-here
AES_ENCRYPTION_KEY=16-chars-key
ADMIN_INITIAL_PASSWORD=admin123

# ==================== 其他配置 (可选) ====================
HA_MODE=zookeeper               # HA 模式
CHECKPOINT_INTERVAL=300000      # Checkpoint 间隔 (毫秒)
```

#### Step 2: 初始化数据库

```bash
# 1. 创建 Schema 和用户
sqlplus sys/your_password@helowin as sysdba @sql/setup-flink-user-schema.sql

# 2. 创建元数据表
sqlplus finance_user/your_password@helowin @sql/setup-metadata-tables.sql

# 3. (可选) 配置 Oracle CDC
sqlplus sys/your_password@helowin as sysdba @sql/enable-oracle-cdc.sql
```

#### Step 3: 构建镜像

```bash
# 构建所有镜像
docker-compose build

# 或快速构建 (使用预构建镜像)
./quick-build.sh
```

#### Step 4: 启动服务

```bash
# 启动所有服务
docker-compose up -d

# 查看状态
docker-compose ps

# 预期输出:
# NAME                     STATUS
# zookeeper                Up (healthy)
# flink-jobmanager         Up (starting)
# flink-taskmanager-1      Up
# flink-monitor-backend    Up (starting)
# flink-monitor-frontend   Up
```

#### Step 5: 访问服务

```bash
# Web 管理界面 (登录)
open http://localhost:8888
# 默认账号：admin / admin123 (首次登录需修改)

# Flink Web UI (任务监控)
open http://localhost:8081

# Backend API 健康检查
curl http://localhost:5001/actuator/health
```

### 6.2 Kubernetes 部署

#### Step 1: 配置 Secret

```bash
cd k8s

# 复制示例
cp flink-secrets.yaml.example flink-secrets.yaml

# 编辑配置 (数据库连接 + JWT/AES密钥)
vi flink-secrets.yaml
```

#### Step 2: 一键部署

```bash
# 执行部署脚本
./deploy.sh

# 查看部署状态
kubectl get pods -n flink
```

#### Step 3: 访问服务

```bash
# 方式 1: Port Forward (测试)
kubectl port-forward -n flink svc/flink-jobmanager-rest 8081:8081
kubectl port-forward -n flink svc/monitor-frontend 8888:80

# 方式 2: LoadBalancer (生产)
kubectl get svc -n flink  # 查看 EXTERNAL-IP
```

### 6.3 本地开发环境

```bash
# 1. 启动数据库 (如果有 Docker)
docker-compose -f docker-local.yml up oracle

# 2. 启动 Flink 本地模式
cd flink-jobs && mvn clean package
flink run -c com.realtime.pipeline.CdcJobMain target/flink-jobs-1.0.0-SNAPSHOT.jar \
  --DATABASE_HOST localhost --DATABASE_PORT 1521 --DATABASE_SID xe

# 3. 启动 Backend (单独)
cd monitor-backend && mvn spring-boot:run

# 4. 启动 Frontend (单独)
cd monitor/frontend-vue && npm run dev
```

---

## API 接口文档

### 7.1 认证相关

#### 7.1.1 用户登录

```
POST /api/auth/login
Content-Type: application/json

Request:
{
  "username": "admin",
  "password": "admin123"
}

Response:
{
  "success": true,
  "data": {
    "token": "eyJhbGc...",
    "expiresIn": 86400000
  }
}
```

#### 7.1.2 Token 刷新

```
POST /api/auth/refresh
Authorization: Bearer {old-token}

Response:
{
  "success": true,
  "data": {
    "token": "eyJhbGc...",
    "expiresIn": 86400000
  }
}
```

### 7.2 数据源管理

#### 7.2.1 创建数据源

```
POST /api/datasources
Authorization: Bearer {token}

Request:
{
  "id": "oracle-prod",
  "name": "生产环境 Oracle",
  "host": "192.168.1.100",
  "port": 1521,
  "username": "cdc_user",
  "password": "secret",
  "sid": "ORCL",
  "description": "生产数据库"
}

Response: {"success": true, "message": "数据源创建成功"}
```

#### 7.2.2 查询数据源列表

```
GET /api/datasources
Authorization: Bearer {token}

Response:
{
  "success": true,
  "data": [
    {
      "id": "oracle-prod",
      "name": "生产环境 Oracle",
      "host": "192.168.1.100",
      "port": 1521,
      "sid": "ORCL"
    }
  ]
}
```

#### 7.2.3 测试数据源连接

```
POST /api/datasources/{id}/test-connection
Authorization: Bearer {token}

Response:
{
  "success": true,
  "data": {
    "connected": true,
    "message": "连接成功"
  }
}
```

### 7.3 CDC 任务管理

#### 7.3.1 创建 CDC 任务

```
POST /api/cdc/tasks
Authorization: Bearer {token}

Request:
{
  "name": "订单数据同步",
  "datasourceId": "oracle-prod",
  "schemaName": "ORDER_SCHEMA",
  "tables": "ORDERS,CUSTOMERS",
  "parallelism": 2,
  "splitSize": 8192
}

Response: {"success": true, "message": "任务创建成功"}
```

#### 7.3.2 查询 CDC 任务列表

```
GET /api/cdc/tasks
Authorization: Bearer {token}
Query Params:
  - status: CREATED|PENDING|RUNNING|STOPPED

Response:
{
  "success": true,
  "data": [
    {
      "id": "task-001",
      "name": "订单数据同步",
      "schemaName": "ORDER_SCHEMA",
      "tables": "ORDERS,CUSTOMERS",
      "status": "RUNNING",
      "flinkJobId": "a1b2c3d4-5678-90ab-cdef-123456789abc"
    }
  ]
}
```

#### 7.3.3 提交 CDC 任务

```
POST /api/cdc/tasks/{taskId}/submit
Authorization: Bearer {token}

Request:
{
  "parallelism": 4,
  "splitSize": 16384
}

Response:
{
  "success": true,
  "data": {
    "flinkJobId": "a1b2c3d4-5678-90ab-cdef-123456789abc",
    "status": "PENDING"
  }
}
```

#### 7.3.4 停止 CDC 任务

```
POST /api/cdc/tasks/{taskId}/stop
Authorization: Bearer {token}

Response: {"success": true, "message": "任务已停止"}
```

### 7.4 Flink 作业监控

#### 7.4.1 查询集群状态

```
GET /api/cluster/status
Authorization: Bearer {token}

Response:
{
  "success": true,
  "data": {
    "status": "RUNNING",
    "jobManager": {
      "address": "jobmanager:8081",
      "taskManagers": 3,
      "totalSlots": 6,
      "availableSlots": 2
    }
  }
}
```

#### 7.4.2 查询作业列表

```
GET /api/jobs
Authorization: Bearer {token}

Response:
{
  "success": true,
  "data": [
    {
      "jobId": "a1b2c3d4-5678-90ab-cdef-123456789abc",
      "jobName": "Oracle CDC - ORDERS",
      "status": "RUNNING",
      "startedAt": 1704326400000,
      "duration": 86400000
    }
  ]
}
```

#### 7.4.3 停止 Flink 作业

```
POST /api/jobs/{jobId}/cancel
Authorization: Bearer {token}

Response: {"success": true, "message": "作业已停止"}
```

### 7.5 CDC 事件监控

#### 7.5.1 查询 CDC 事件

```
GET /api/cdc/events?taskId={taskId}&limit=100&startTime={timestamp}
Authorization: Bearer {token}

Response:
{
  "success": true,
  "data": [
    {
      "taskId": "task-001",
      "eventType": "INSERT",
      "schemaName": "ORDER_SCHEMA",
      "tableName": "ORDERS",
      "data": "{\"id\": 1, \"amount\": 100}",
      "timestamp": 1704326400000
    }
  ],
  "total": 12345,
  "hasMore": true
}
```

#### 7.5.2 查询 CDC 统计信息

```
GET /api/cdc/stats?taskId={taskId}
Authorization: Bearer {token}

Response:
{
  "success": true,
  "data": {
    "taskId": "task-001",
    "totalEvents": 12345,
    "insertCount": 8000,
    "updateCount": 3000,
    "deleteCount": 1245,
    "ddlCount": 5,
    "errorCount": 0
  }
}
```

### 7.6 输出文件管理

#### 7.6.1 查询输出文件列表

```
GET /api/output/files?taskId={taskId}
Authorization: Bearer {token}

Response:
{
  "success": true,
  "data": [
    {
      "fileName": "2026-04-28--09/ORDERS_20260428_091530-abc123-0.csv",
      "fileSize": 1048576,
      "createdAt": 1704326400000
    }
  ]
}
```

#### 7.6.2 下载输出文件

```
GET /api/output/files/{fileName}
Authorization: Bearer {token}

Response: [CSV 文件内容]
Content-Disposition: attachment; filename={fileName}
```

---

## 运维手册

### 8.1 日常监控

#### 8.1.1 服务健康检查

```bash
# 检查所有容器状态
docker-compose ps

# 查看日志
docker-compose logs -f

# 查看详细日志 (最近100行)
docker-compose logs --tail 100 monitor-backend
```

#### 8.1.2 Flink 作业监控

```bash
# 访问 Flink Web UI
open http://localhost:8081

# API 方式查询作业状态
curl http://localhost:8081/jobs | jq
```

#### 8.1.3 Checkpoint 状态

```bash
# 查看 Checkpoint 目录
docker exec flink-jobmanager ls -lh /opt/flink/checkpoints/

# 查看最新 Checkpoint
du -sh /opt/flink/checkpoints/*/
```

### 8.2 日志管理

#### 8.2.1 日志文件位置

| 组件 | 日志路径 |
|------|---------|
| JobManager | `/opt/flink/logs/` (容器内) |
| TaskManager | `/opt/flink/logs/` + `data/flink-logs/` (宿主机) |
| Monitor Backend | `logs/local/backend.log` |
| Vue 3 Frontend | Nginx access/error logs |

#### 8.2.2 查看日志命令

```bash
# Flink JobManager 日志
docker exec flink-jobmanager tail -f /opt/flink/logs/jobmanager.log

# Monitor Backend 日志
tail -f logs/local/backend.log

# 搜索特定关键字
grep "ERROR" logs/local/backend.log | tail -50
```

### 8.3 故障恢复

#### 8.3.1 JobManager 崩溃恢复

**场景**: JobManager Pod/容器意外退出

```bash
# Docker Compose 环境 (自动恢复)
# 容器会自动重启 (restart: unless-stopped)
docker-compose ps

# Kubernetes 环境
curl -X POST http://localhost:8081/jobs/{jobId}/savepoints
# 手动触发 Savepoint 确保状态持久化
```

#### 8.3.2 Checkpoint 失败处理

```bash
# 1. 检查磁盘空间
df -h

# 2. 查看 Checkpoint 失败原因
curl http://localhost:8081/jobs/{jobId}/checkpoints | jq '.failedCheckpoints[]'

# 3. 调整 Checkpoint 配置
docker exec -it flink-jobmanager vi /opt/flink/conf/flink-conf.yaml
# execution.checkpointing.interval: 300000 -> 600000
```

#### 8.3.3 CDC 任务失败恢复

```bash
# 1. 查看作业错误日志
docker logs flink-taskmanager-1 | grep -A 20 "Exception"

# 2. 从 Checkpoint 恢复
curl -X POST http://localhost:8081/jobs/{jobId}/restart

# 3. 重启任务 (通过 Web UI)
```

### 8.4 性能调优

#### 8.4.1 资源配置调整

```yaml
# docker-compose.yml 中调整
taskmanager:
  environment:
    - TASK_MANAGER_MEMORY_PROCESS_SIZE=4096m  # 增加到 4GB
    - TASK_MANAGER_NUMBER_OF_TASK_SLOTS=4     # 增加 Slot 数
```

#### 8.4.2 CDC 性能优化

| 参数 | 说明 | 推荐值 |
|------|------|-------|
| split.size | CDC Split Size | 8192-16384 |
| parallelism | Flink 并行度 | = TaskManager Slot 数 |
| checkpoint.interval | Checkpoint 间隔 | 300s-600s |

```bash
# 更新 CDC 任务配置
curl -X PUT http://localhost:5001/api/cdc/tasks/{taskId} \
  -H "Content-Type: application/json" \
  -d '{
    "splitSize": 16384,
    "parallelism": 4
  }'
```

### 8.5 备份与恢复

#### 8.5.1 数据库备份

```bash
# 导出元数据
docker exec -t oracle-container \
  expdp system/password DIRECTORY=DATA_PUMP_DIR \
  DUMPFILE=metadata_%U.dmp \
  TABLES=finance_user.datasources,finance_user.cdc_tasks
```

#### 8.5.2 Checkpoint 备份

```bash
# 复制 Checkpoint 到外部存储
tar -czf checkpoints-backup-$(date +%Y%m%d).tar.gz data/flink-checkpoints/
```

### 8.6 扩缩容操作

#### 8.6.1 TaskManager 扩容

```bash
# Docker Compose
docker-compose up -d --scale taskmanager=5

# Kubernetes
kubectl scale deployment flink-taskmanager -n flink --replicas=5

# 验证
docker-compose ps | grep taskmanager    # Docker
kubectl get pods -n flink              # K8s
```

---

## 常见问题

### FAQ: 部署问题

**Q1: 容器启动失败，提示 "Connection refused to JobManager"？**

```bash
# 检查步骤:
1. docker-compose ps    # 确认 JobManager 状态
2. docker logs flink-jobmanager    # 查看报错信息
3. 确认环境变量正确:
   - JOB_MANAGER_RPC_ADDRESS=jobmanager
   - FLINK_REST_URL=http://jobmanager:8081
```

**Q2: 数据库连接失败？**

```bash
# 测试连接:
telnet ${DATABASE_HOST} ${DATABASE_PORT}

# 检查配置:
echo $DATABASE_HOST
cat .env | grep DATABASE_
```

### FAQ: CDC 问题

**Q3: CDC 任务没有捕获到任何事件？**

```bash
# 确认 Oracle Supplemental Logging:
sqlplus sys/password@db as sysdba <<EOF
SELECT SUPPLEMENTAL_LOG_DATA_MIN FROM V$DATABASE;
-- 应该返回 YES
EOF

# 检查 CDC Source 配置:
- schema_name 是否正确？
- tables 参数是否匹配实际表名？
```

**Q4: DDL 事件没有捕获？**

```bash
# 确认 History Reader 已启用:
# CdcJobMain.java 中应包含:
.debeziumProperties(props)

# props.put("history.reader.enabled", "true");
```

### FAQ: 性能问题

**Q5: Checkpoint 失败？**

```bash
# 1. 检查磁盘空间
df -h data/flink-checkpoints/

# 2. 查看失败原因
curl http://localhost:8081/jobs/{jobId}/checkpoints | jq

# 3. 调整超时时间:
execution.checkpointing.timeout: 600000
```

**Q6: 吞吐量低？**

```bash
# 优化建议:
1. 增加 TaskManager: docker-compose up -d --scale taskmanager=N
2. 增大 split.size:      curl -X PUT /api/cdc/tasks/{id} -d '{"splitSize": 16384}'
3. 检查 Oracle 归档日志是否正常生成
```

### FAQ: 安全问题

**Q7: JWT Secret 和 AES Key 如何生成？**

```bash
# JWT Secret (至少32字符)
echo $(openssl rand -base64 32)

# AES Key (16字符)
echo $(openssl rand -base64 16 | tr -d '/+=' | cut -c1-16)
```

**Q8: 如何修改管理员初始密码？**

```bash
# 方式1: 通过 API (需要先登录)
curl -X PUT http://localhost:5001/api/admin/password \
  -H "Authorization: Bearer {token}" \
  -d '{"newPassword": "new-password-123!@#"}'

# 方式2: 重置环境变量后重启
export ADMIN_INITIAL_PASSWORD=new-password-123!@#
docker-compose restart monitor-backend
```

---

## 附录

### A. 环境变量完整列表

```bash
# ============================================
# Oracle Database Configuration (元数据存储)
# ============================================
DATABASE_HOST        # Oracle 主机地址
DATABASE_PORT=1521   # Oracle 端口
DATABASE_SID         # Oracle SID (ORCL/helowin)
DATABASE_USERNAME    # 数据库用户名
DATABASE_PASSWORD    # 数据库密码 (必需)

# ============================================
# Oracle CDC Source Configuration
# ============================================
CDC_SOURCE_HOST     # CDC 源数据库主机 (如与元数据同库可不配)
CDC_SOURCE_PORT=1521
CDC_SOURCE_SID
CDC_SOURCE_SCHEMA   # 要监控的 Schema
CDC_SOURCE_TABLES   # 要监控的表 (逗号分隔)

# ============================================
# Security Configuration
# ============================================
JWT_SECRET          # JWT 密钥 (必需，至少32字符)
JWT_EXPIRATION=86400000  # Token 过期时间 (毫秒)
AES_ENCRYPTION_KEY  # AES 加密密钥 (必需，16字符)
ADMIN_INITIAL_PASSWORD  # 管理员初始密码

# ============================================
# Flink Configuration
# ============================================
FLINK_VERSION=1.20.0
FLINK_CDC_VERSION=3.4.0
CHECKPOINT_INTERVAL=300000  # Checkpoint 间隔 (毫秒)
PARALLELISM_DEFAULT=2

# ============================================
# High Availability Configuration
# ============================================
HA_MODE=zookeeper         # HA 模式 (zookeeper/kubernetes)
HA_ZOOKEEPER_QUORUM=zookeeper:2181
HA_CLUSTER_ID=/realtime-pipeline
```

### B. 端口说明

| 组件 | 端口 | 用途 |
|------|------|------|
| Nginx (Frontend) | 80 / 8888 | Vue 3 Web UI |
| Monitor Backend | 5001 | REST API + Actuator |
| Flink JobManager | 8081 | Web UI + REST API |
| Flink RPC | 6123 | JobManager 间通信 |
| Flink Data | 6121-6130 | TaskManager 数据传输 |
| ZooKeeper | 2181 | HA 协调 (可选) |
| Oracle Database | 1521 | JDBC 连接 |

### C. 文件目录结构

```
data/
├── flink-checkpoints/     # Checkpoint 数据 (按作业ID分区)
│   └── {jobId}/
│       ├── checkpoint-{id}/
│       │   └── subtasks/
│       └── CHANGED.txt
│
├── flink-savepoints/      # Savepoint 数据 (手动触发)
│   └── {timestamp}/
│       ├── state/
│       └── metadata.json
│
├── flink-ha/              # HA Leader Election 数据
│   └── ha/
│       ├── cluster-state.json
│       └── leader-election/
│
└── flink-logs/            # Flink 日志
    ├── jobmanager.log
    └── taskmanager-*.log
```

### D. 数据库表结构概览

```sql
-- ============================================
-- datasources - 数据源配置表
-- ============================================
CREATE TABLE datasources (
    id          VARCHAR2(100)  PRIMARY KEY,      -- UUID
    name        VARCHAR2(200) NOT NULL,          -- 数据源名称
    host        VARCHAR2(200) NOT NULL,          -- Oracle 主机
    port        NUMBER(5)       DEFAULT 1521,   -- Oracle 端口
    username    VARCHAR2(100) NOT NULL,          -- 用户名
    password    VARCHAR2(4000),              -- AES加密密码
    sid         VARCHAR2(100) NOT NULL,          -- Oracle SID
    description VARCHAR2(500),           -- 描述信息
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_datasources_name ON datasources(name);

-- ============================================
-- cdc_tasks - CDC 任务配置表
-- ============================================
CREATE TABLE cdc_tasks (
    id          VARCHAR2(100)  PRIMARY KEY,      -- UUID
    name        VARCHAR2(200) NOT NULL,          -- 任务名称
    datasource_id VARCHAR2(100), FOREIGN KEY REFERENCES datasources(id),
    schema_name VARCHAR2(100) NOT NULL,          -- Schema 名
    tables      CLOB        NOT NULL,            -- 表列表 (逗号分隔)
    parallelism NUMBER(3)       DEFAULT 2,      -- Flink 并行度
    split_size  NUMBER(10) DEFAULT 8096,        -- CDC Split Size
    output_path VARCHAR2(500),           -- 输出路径
    status      VARCHAR2(20) DEFAULT 'CREATED',      -- CREATED/PENDING/RUNNING/STOPPED
    flink_job_id VARCHAR2(100),           -- Flink Job ID
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_cdc_tasks_status ON cdc_tasks(status);

-- ============================================
-- runtime_jobs - 运行时作业表
-- ============================================
CREATE TABLE runtime_jobs (
    id          VARCHAR2(100)  PRIMARY KEY,      -- UUID
    task_id     VARCHAR2(100), FOREIGN KEY REFERENCES cdc_tasks(id),
    task_name   VARCHAR2(200) NOT NULL,
    schema_name VARCHAR2(100),
    tables      CLOB,
    parallelism NUMBER(3),
    flink_job_id VARCHAR2(100),
    status      VARCHAR2(20) DEFAULT 'PENDING',      -- PENDING/RUNNING/COMPLETED/FAILED
    error_message VARCHAR2(4000),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    started_at  TIMESTAMP,
    finished_at TIMESTAMP
);
CREATE INDEX idx_runtime_jobs_status ON runtime_jobs(status);

-- ============================================
-- users - 用户表
-- ============================================
CREATE TABLE users (
    id          VARCHAR2(100)  PRIMARY KEY,      -- UUID
    username    VARCHAR2(100) NOT NULL UNIQUE,
    password_hash VARCHAR2(500),           -- BCrypt加密
    role        VARCHAR2(20) DEFAULT 'USER',       -- ADMIN/USER
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_users_username ON users(username);
```

### E. CSV 输出文件格式

```csv
# DML Event Format:
# TIMESTAMP|OPERATION|TABLE_NAME|COLUMN1|COLUMN2|...

2026-04-28 09:15:30.000|INSERT|ORDERS|1001|ORDER-ABC-001|$1000.00|
2026-04-28 09:16:45.000|UPDATE|ORDERS|1001|ORDER-ABC-001|$1500.00|
2026-04-28 09:17:20.000|DELETE|ORDERS|1001|||

# DDL Event Format:
# TIMESTAMP|DDL_TYPE|TABLE_NAME|COLUMN_NAME|OLD_DEFINITION|NEW_DEFINITION

2026-04-28 10:00:00.000|ADD_COLUMN|ORDERS|DISCOUNT_PERCENT|%NULL%|NUMBER(5,2)
2026-04-28 10:30:00.000|MODIFY_COLUMN|ORDERS|AMOUNT|NUMBER(10,2)|NUMBER(12,2)
2026-04-28 11:00:00.000|DROP_COLUMN|ORDERS|TEMP_FIELD|%NULL%|%NULL%
```

### F. 相关文档资源

| 文档 | 位置 | 说明 |
|------|------|------|
| 架构设计 | ARCHITECTURE.md | 系统架构说明 |
| 构建指南 | BUILD-GUIDE.md | Maven/Docker构建 |
| 快速开始 | QUICK-START.md | 5分钟部署指南 |
| K8s 部署 | k8s/README.md | Kubernetes 部署文档 |
| 安全实施 | SECURITY-IMPLEMENTATION-GUIDE.md | 安全配置指南 |

### G. 外部链接

- **Flink 官方文档**: https://flink.apache.org/
- **Flink CDC 文档**: https://ververica.github.io/flink-cdc-connectors/
- **Vue 3 官方文档**: https://vuejs.org/
- **Spring Boot 文档**: https://spring.io/projects/spring-boot
- **Oracle LogMiner**: https://docs.oracle.com/cd/B19367_01/server.102/b14220/miner003.htm

---

## 变更历史

| 版本 | 日期 | 变更内容 |
|------|------|---------|
| 1.0.0 | 2026-04-28 | 初始版本发布 |
| 1.0.1 | TBD | Vue 3 迁移完成 |

---

**文档维护**: Kiro Data Pipeline Team  
**技术支持**: [email protected]  

---

*本手册内容基于项目实际代码和配置编写，如有变动请以最新代码为准。*
