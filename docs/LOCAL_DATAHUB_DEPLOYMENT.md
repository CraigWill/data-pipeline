# 本地 DataHub (Kafka) 部署指南

本文档说明如何在本地部署 Kafka 作为阿里云 DataHub 的替代方案。

## 部署状态

✅ **已部署服务:**
- Zookeeper (Kafka 依赖)
- Kafka Broker
- Kafka UI (Web 管理界面)
- Flink JobManager
- Flink TaskManager

## 访问地址

| 服务 | 地址 | 说明 |
|------|------|------|
| Flink Web UI | http://localhost:8081 | Flink 集群管理界面 |
| Kafka UI | http://localhost:8080 | Kafka 管理界面 |
| Kafka Broker | localhost:9092 | 主机和容器内部访问 |
| Zookeeper | localhost:2181 | Zookeeper 服务 |

## 已创建的 Topics

- `cdc-events` - CDC 事件主题 (3 分区)

## Kafka 常用操作

### 1. 创建 Topic

```bash
docker exec kafka kafka-topics --create \
  --topic my-topic \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1
```

### 2. 列出所有 Topics

```bash
docker exec kafka kafka-topics --list \
  --bootstrap-server localhost:9092
```

### 3. 查看 Topic 详情

```bash
docker exec kafka kafka-topics --describe \
  --topic cdc-events \
  --bootstrap-server localhost:9092
```

### 4. 生产消息 (测试)

```bash
docker exec -it kafka kafka-console-producer \
  --topic cdc-events \
  --bootstrap-server localhost:9092
```

输入消息后按 Enter 发送，Ctrl+C 退出。

### 5. 消费消息 (测试)

```bash
docker exec -it kafka kafka-console-consumer \
  --topic cdc-events \
  --bootstrap-server localhost:9092 \
  --from-beginning
```

### 6. 删除 Topic

```bash
docker exec kafka kafka-topics --delete \
  --topic my-topic \
  --bootstrap-server localhost:9092
```

## 配置说明

### 环境变量 (.env)

本地开发环境已配置为使用 Kafka:

```bash
# DataHub 配置（本地使用 Kafka）
DATAHUB_ENDPOINT=kafka://kafka:9092
DATAHUB_ACCESS_ID=local-dev
DATAHUB_ACCESS_KEY=local-dev-secret
DATAHUB_PROJECT=realtime-pipeline
DATAHUB_TOPIC=cdc-events
DATAHUB_CONSUMER_GROUP=cdc-collector-group
```

### Kafka 配置

- **Broker ID**: 1
- **Zookeeper**: zookeeper:2181
- **自动创建 Topic**: 启用
- **日志保留时间**: 168 小时 (7 天)
- **副本因子**: 1 (单节点)

## 使用 Kafka UI

访问 http://localhost:8080 可以通过图形界面:

1. 查看所有 Topics
2. 查看消息内容
3. 创建/删除 Topics
4. 监控 Kafka 集群状态
5. 查看消费者组信息

## 与 Flink 集成

### 在 Flink 作业中使用 Kafka

```java
// Kafka Source
KafkaSource<String> source = KafkaSource.<String>builder()
    .setBootstrapServers("kafka:9092")
    .setTopics("cdc-events")
    .setGroupId("flink-consumer-group")
    .setStartingOffsets(OffsetsInitializer.earliest())
    .setValueOnlyDeserializer(new SimpleStringSchema())
    .build();

// Kafka Sink
KafkaSink<String> sink = KafkaSink.<String>builder()
    .setBootstrapServers("kafka:9092")
    .setRecordSerializer(KafkaRecordSerializationSchema.builder()
        .setTopic("output-topic")
        .setValueSerializationSchema(new SimpleStringSchema())
        .build()
    )
    .build();
```

## 故障排查

### Kafka 无法启动

```bash
# 查看 Kafka 日志
docker logs kafka

# 检查 Zookeeper 状态
docker logs zookeeper
```

### 无法连接到 Kafka

```bash
# 测试连接
docker exec kafka kafka-broker-api-versions \
  --bootstrap-server localhost:9092
```

### Topic 创建失败

```bash
# 检查 Kafka 配置
docker exec kafka kafka-configs --describe \
  --bootstrap-server localhost:9092 \
  --entity-type brokers \
  --entity-name 1
```

## 性能调优

### 增加分区数

```bash
docker exec kafka kafka-topics --alter \
  --topic cdc-events \
  --partitions 6 \
  --bootstrap-server localhost:9092
```

### 调整保留策略

```bash
docker exec kafka kafka-configs --alter \
  --entity-type topics \
  --entity-name cdc-events \
  --add-config retention.ms=604800000 \
  --bootstrap-server localhost:9092
```

## 生产环境迁移

当迁移到生产环境时，需要:

1. 更新 `.env` 文件中的 DataHub 配置:
   ```bash
   DATAHUB_ENDPOINT=https://dh-cn-hangzhou.aliyuncs.com
   DATAHUB_ACCESS_ID=<your-access-key-id>
   DATAHUB_ACCESS_KEY=<your-access-key-secret>
   ```

2. 修改应用代码以使用 DataHub SDK 而不是 Kafka

3. 更新 docker-compose.yml 移除 Kafka 服务

## 清理

### 停止所有服务

```bash
docker compose down
```

### 删除所有数据

```bash
docker compose down -v
```

## 参考资料

- [Apache Kafka 文档](https://kafka.apache.org/documentation/)
- [Confluent Platform 文档](https://docs.confluent.io/)
- [Kafka UI 文档](https://docs.kafka-ui.provectus.io/)
- [Flink Kafka Connector](https://nightlies.apache.org/flink/flink-docs-stable/docs/connectors/datastream/kafka/)
