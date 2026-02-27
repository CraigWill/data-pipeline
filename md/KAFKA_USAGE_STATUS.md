# Kafka 使用状态说明

## 当前状态：❌ **未使用 Kafka**

虽然 Kafka 容器在运行，但当前的 Flink CDC 作业**并没有使用 Kafka**。

## 当前数据流架构

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
CSV File Sink (直接写入文件系统)
    ↓
./output/cdc/*.csv
```

**数据流说明**:
- Oracle → Flink CDC Source → Filter → Map → **File Sink**
- 数据直接从 Flink 写入本地文件系统
- **没有经过 Kafka**

## Kafka 的角色

### 当前角色：🔄 **待用组件**

Kafka 已部署但未被使用，原因：
1. 当前架构是简化的单层架构
2. Flink CDC 直接输出到文件系统
3. 不需要中间消息队列

### 原始设计：DataHub 替代品

Kafka 最初部署是作为**阿里云 DataHub 的本地开发替代**：

```
原始设计（未实现）:
Oracle → CDC Collector → DataHub/Kafka → Flink → File System
```

当前实现（简化版）:
```
Oracle → Flink CDC → File System
```

## Kafka 容器状态

虽然未使用，但 Kafka 相关组件仍在运行：

| 组件 | 状态 | 端口 | 用途 |
|------|------|------|------|
| Zookeeper | ✅ 运行中 | 2181 | Kafka 协调服务 |
| Kafka | ✅ 运行中 | 9092 (外部), 29092 (内部) | 消息队列 |
| Kafka UI | ✅ 运行中 | 8082 | 管理界面 |
| Debezium Connect | ✅ 运行中 | 8083 | CDC 连接器 (备选) |

**Topic 状态**:
- `cdc-events` topic 已创建
- 但没有消息（因为没有生产者）

## 是否需要 Kafka？

### ❌ 当前不需要

**理由**:
1. 单一消费者场景 - 只有 Flink 处理数据
2. 简化架构 - 减少组件复杂度
3. 性能更好 - 减少一层网络传输
4. 运维更简单 - 少一个组件要管理

### ✅ 以下场景需要 Kafka

如果有以下需求，应该使用 Kafka：

1. **多个消费者**
   - 多个 Flink 作业消费同一份 CDC 数据
   - 其他系统（如实时分析、监控）需要 CDC 数据

2. **解耦生产和消费**
   - CDC 采集和数据处理独立部署
   - 不同团队负责不同环节

3. **数据缓冲**
   - 处理速度波动大，需要消息队列缓冲
   - 下游系统临时不可用时保留数据

4. **数据重放**
   - 需要重新处理历史 CDC 数据
   - 调试和测试需要

## 如何启用 Kafka

如果需要使用 Kafka，可以修改架构：

### 方案 1: Flink CDC → Kafka → Flink

```java
// 1. Flink CDC 写入 Kafka
DataStream<String> cdcStream = env
    .fromSource(oracleSource, ...)
    .sinkTo(KafkaSink.<String>builder()
        .setBootstrapServers("kafka:29092")
        .setRecordSerializer(...)
        .setProperty("transaction.timeout.ms", "900000")
        .build());

// 2. 另一个 Flink 作业从 Kafka 读取
DataStream<String> kafkaStream = env
    .fromSource(KafkaSource.<String>builder()
        .setBootstrapServers("kafka:29092")
        .setTopics("cdc-events")
        .setValueOnlyDeserializer(new SimpleStringSchema())
        .build(), ...);
```

### 方案 2: 使用 Debezium Connect

```bash
# 注册 Oracle 连接器
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d '{
    "name": "oracle-connector",
    "config": {
      "connector.class": "io.debezium.connector.oracle.OracleConnector",
      "database.hostname": "host.docker.internal",
      "database.port": "1521",
      "database.user": "system",
      "database.password": "helowin",
      "database.dbname": "helowin",
      "database.server.name": "oracle",
      "table.include.list": "FINANCE_USER.TRANS_INFO",
      "database.history.kafka.bootstrap.servers": "kafka:29092",
      "database.history.kafka.topic": "schema-changes.oracle"
    }
  }'

# Flink 从 Kafka 读取
# (同方案 1 的第 2 步)
```

## 是否应该移除 Kafka？

### 建议：保留但不使用

**理由**:
1. 已经部署，不影响当前功能
2. 未来可能需要（扩展性）
3. 可用于测试和开发
4. 资源占用不大

**如果要移除**:
```bash
# 停止并删除 Kafka 相关容器
docker-compose stop kafka zookeeper kafka-ui debezium
docker-compose rm -f kafka zookeeper kafka-ui debezium

# 编辑 docker-compose.yml，注释掉相关服务
```

## 总结

| 问题 | 答案 |
|------|------|
| 当前是否使用 Kafka？ | ❌ 否 |
| Kafka 是否在运行？ | ✅ 是 |
| 数据是否经过 Kafka？ | ❌ 否，直接写入文件 |
| 是否需要 Kafka？ | ❌ 当前不需要 |
| 应该移除 Kafka 吗？ | 🤔 建议保留备用 |

**当前架构**: Oracle → Flink CDC → File System (简单直接)  
**Kafka 角色**: 待用组件，未来扩展时可能使用
