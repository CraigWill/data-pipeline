# 实时数据管道系统 - 组件清单

## 当前运行的组件

### 核心流处理组件

#### 1. Apache Flink 集群
- **Flink JobManager** (flink-jobmanager)
  - 版本: 1.18.0
  - 端口: 8081 (Web UI), 6123 (RPC), 6124 (Blob Server), 9249 (Metrics)
  - 状态: ✅ 运行中 (健康)
  - 功能: 作业调度、资源管理、协调

- **Flink TaskManager** (realtime-pipeline-taskmanager-1)
  - 版本: 1.18.0
  - 端口: 6121 (数据交换), 6122 (RPC), 9249 (Metrics)
  - 状态: ✅ 运行中 (健康)
  - 任务槽: 4
  - 功能: 执行数据处理任务

#### 2. Flink CDC 3.x
- **版本**: 3.2.1
- **连接器**: flink-connector-oracle-cdc
- **启动模式**: Latest (只捕获新变更，跳过 snapshot)
- **功能**: 通过 Oracle LogMiner 实时捕获数据库变更
- **当前作业**: 
  - Job ID: b4b27abf8b1d1ce7a632dcc0d0e0a21f
  - 状态: ✅ RUNNING
  - 并行度: 4

### 数据源

#### 3. Oracle Database 11g
- **版本**: Oracle Database 11g Enterprise Edition Release 11.2.0.1.0
- **端口**: 1521
- **实例**: helowin
- **Schema**: FINANCE_USER
- **监控表**: TRANS_INFO (21亿+ 记录)
- **CDC 配置**:
  - 归档日志模式: ✅ ARCHIVELOG
  - 补充日志: ✅ IMPLICIT
  - LogMiner: ✅ 已启用

### 消息队列 (DataHub 本地替代)

#### 4. Apache Kafka
- **版本**: 7.5.0 (Confluent Platform)
- **端口**: 
  - 内部: kafka:29092 (容器间通信)
  - 外部: localhost:9092 (宿主机访问)
- **状态**: ✅ 运行中 (健康)
- **Topic**: cdc-events (3 分区)
- **功能**: 消息队列，DataHub 的本地开发替代

#### 5. Apache Zookeeper
- **版本**: 7.5.0 (Confluent Platform)
- **端口**: 2181
- **状态**: ✅ 运行中 (健康)
- **功能**: Kafka 集群协调

#### 6. Kafka UI
- **版本**: latest (provectuslabs)
- **端口**: 8082
- **状态**: ✅ 运行中
- **功能**: Kafka 集群管理和监控界面
- **访问**: http://localhost:8082

### CDC 组件 (备选方案)

#### 7. Debezium Connect
- **版本**: 2.4
- **端口**: 8083 (REST API)
- **状态**: ✅ 运行中 (启动中)
- **功能**: 独立的 CDC 连接器服务 (备选方案)
- **注**: 当前主要使用 Flink CDC 3.x

## 核心依赖库

### Flink 相关
- **Apache Flink**: 1.18.0
- **Flink CDC**: 3.2.1
- **Flink State Backend**: RocksDB (可选)
- **Flink Connectors**: Files, Parquet

### 数据库驱动
- **Oracle JDBC**: ojdbc8 21.1.0.0
- **Oracle XML Database**: xdb 19.3.0.0

### 数据格式
- **Apache Parquet**: 1.13.1
- **Apache Avro**: 1.11.1
- **Jackson JSON**: 2.15.2

### 日志和监控
- **Log4j2**: 2.20.0
- **SLF4J**: 1.7.36

### 测试框架
- **JUnit 5**: 5.10.0
- **jqwik** (属性测试): 1.8.0
- **Mockito**: 5.5.0
- **AssertJ**: 3.24.2

## 数据流架构

```
Oracle 11g (TRANS_INFO 表)
    ↓ (DML 操作: INSERT/UPDATE/DELETE)
Redo Log (归档日志)
    ↓ (LogMiner 挖掘)
Flink CDC 3.x Source
    ↓ (JSON 格式)
Application-Level Filter (过滤表)
    ↓
JSON to CSV Converter
    ↓
CSV File Sink
    ↓
./output/cdc/*.csv
```

## 访问端点

| 服务 | 内部地址 (Docker) | 外部地址 (宿主机) |
|------|------------------|------------------|
| Flink Web UI | jobmanager:8081 | http://localhost:8081 |
| Kafka | kafka:29092 | localhost:9092 |
| Kafka UI | - | http://localhost:8082 |
| Zookeeper | zookeeper:2181 | localhost:2181 |
| Debezium API | debezium:8083 | http://localhost:8083 |
| Oracle DB | host.docker.internal:1521 | localhost:1521 |

## 存储卷

- **flink-checkpoints**: Flink checkpoint 数据
- **flink-savepoints**: Flink savepoint 数据
- **flink-logs**: Flink 日志
- **kafka-data**: Kafka 数据
- **zookeeper-data**: Zookeeper 数据
- **zookeeper-logs**: Zookeeper 日志
- **debezium-data**: Debezium 数据
- **debezium-logs**: Debezium 日志

## 网络

- **flink-network**: Docker 桥接网络，所有服务互联

## 输出

- **格式**: CSV
- **路径**: `./output/cdc/`
- **内容**: 数据库变更事件 (timestamp, table, operation, before, after)

## 系统状态总览

| 组件 | 状态 | 版本 | 功能 |
|------|------|------|------|
| Flink JobManager | ✅ 运行中 | 1.18.0 | 作业管理 |
| Flink TaskManager | ✅ 运行中 | 1.18.0 | 任务执行 |
| Flink CDC 3.x Job | ✅ 运行中 | 3.2.1 | CDC 捕获 |
| Oracle 11g | ✅ 运行中 | 11.2.0.1.0 | 数据源 |
| Kafka | ✅ 运行中 | 7.5.0 | 消息队列 |
| Zookeeper | ✅ 运行中 | 7.5.0 | 协调服务 |
| Kafka UI | ✅ 运行中 | latest | 监控界面 |
| Debezium | ✅ 运行中 | 2.4 | CDC 备选 |

所有核心组件运行正常，CDC 数据捕获功能已验证可用。
