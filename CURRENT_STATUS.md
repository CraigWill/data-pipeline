# 当前系统状态总结

## 日期
2026-02-12

## 系统组件状态

### ✅ Docker 服务（全部运行中）
- **Flink JobManager**: http://localhost:8081
- **Flink TaskManager**: 1 个实例，4 个任务槽
- **Kafka**: localhost:9092
- **Kafka UI**: http://localhost:8080
- **Zookeeper**: localhost:2181
- **Oracle 11g**: localhost:1521 (helowin 实例)

### ✅ Flink 作业
- **作业 ID**: e0b3bb0c6fb9f659b5a42236545c55bd
- **作业名称**: Realtime Data Pipeline
- **状态**: RUNNING
- **主类**: FlinkPipelineMain
- **并行度**: 2

## 输出文件状态

### ✅ CSV 文件正在生成
**位置**: `/opt/flink/output/cdc/2026-02-12--01/`

**文件示例**:
```
.part-c128eebd-56c1-4d5c-acc2-afad732577fc-0.inprogress.*
.part-c128eebd-56c1-4d5c-acc2-afad732577fc-1.inprogress.*
```

**内容示例**:
```csv
2026-02-12 01:41:57,trans_info,UPDATE,"name=sample_name_0","id=0","value=712","timestamp=1770860517412",
2026-02-12 01:42:02,trans_info,INSERT,"name=sample_name_1","id=1","value=369","timestamp=1770860522758",
2026-02-12 01:42:07,trans_info,INSERT,"name=sample_name_2","id=2","value=410","timestamp=1770860527760",
```

## 重要发现

### ⚠️ DataHubSource 是 Mock 实现
**问题**: `DefaultDataHubClient.getRecords()` 总是返回空列表

**代码位置**: `src/main/java/com/realtime/pipeline/datahub/client/DefaultDataHubClient.java:149`

```java
public List<java.util.Map<String, Object>> getRecords(...) {
    // 模拟从DataHub获取记录
    // 返回空列表表示当前没有新数据
    return new java.util.ArrayList<>();  // ⚠️ 总是返回空
}
```

**影响**:
- FlinkPipelineMain 使用 DataHubSource，但无法从 Kafka 读取真实数据
- 当前输出的 CSV 文件来自之前运行的 SimpleCDCApp（模拟数据）
- 发送到 Kafka 的测试数据无法被消费

### ✅ SimpleCDCApp 正常工作
- 使用自己的 MockCDCSource 生成模拟数据
- 直接写入 CSV 文件
- 不依赖 Kafka 或 DataHub

## 可用的应用程序

### 1. SimpleCDCApp ✅ 推荐用于演示
**主类**: `com.realtime.pipeline.SimpleCDCApp`

**特点**:
- 使用项目配置类（DatabaseConfig, OutputConfig）
- 使用项目模型类（ChangeEvent, ProcessedEvent）
- 生成模拟 CDC 数据
- 直接写入 CSV 文件
- 不需要外部数据源

**启动方式**:
```bash
./submit-to-flink.sh simple
# 或
./submit-to-flink.sh 1
```

### 2. JdbcCDCApp ⚠️ 需要数据库连接
**主类**: `com.realtime.pipeline.JdbcCDCApp`

**特点**:
- 使用 JDBC 轮询方式监控数据库
- 连接 Oracle 数据库 (localhost:1521)
- 监控 helowin.trans_info 表
- 如果无法连接，自动切换到模拟模式

**启动方式**:
```bash
./submit-to-flink.sh jdbc
# 或
./submit-to-flink.sh 2
```

### 3. FlinkPipelineMain ⚠️ DataHubSource 是 Mock
**主类**: `com.realtime.pipeline.FlinkPipelineMain`

**特点**:
- 完整的管道架构
- 包含监控、告警、健康检查
- 使用 DataHubSource（但是 mock 实现）
- 需要配置文件 `application.yml`

**启动方式**:
```bash
./submit-to-flink.sh main
# 或
./submit-to-flink.sh 3
```

**当前问题**: DataHubSource 不会从 Kafka 读取真实数据

## 查看输出文件

### 在容器内查看
```bash
# 列出所有输出文件
docker exec realtime-pipeline-taskmanager-1 \
  find /opt/flink/output/cdc -type f

# 查看文件内容
docker exec realtime-pipeline-taskmanager-1 \
  sh -c 'cat /opt/flink/output/cdc/2026-02-12--01/.part-*.inprogress.* | head -20'

# 实时查看新数据
docker exec realtime-pipeline-taskmanager-1 \
  sh -c 'tail -f /opt/flink/output/cdc/2026-02-12--01/.part-*.inprogress.*'
```

### 复制到本地
```bash
# 创建本地目录
mkdir -p output/cdc

# 从容器复制文件
docker cp realtime-pipeline-taskmanager-1:/opt/flink/output/cdc/. output/cdc/

# 查看本地文件
ls -la output/cdc/
cat output/cdc/2026-02-12--01/part-*
```

## Kafka 测试数据

### 发送测试数据到 Kafka
```bash
./send-test-data-to-kafka.sh
```

### 验证 Kafka 中的数据
```bash
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic cdc-events \
  --from-beginning \
  --max-messages 10
```

## 作业管理

### 查看运行中的作业
```bash
docker exec flink-jobmanager flink list
```

### 取消作业
```bash
docker exec flink-jobmanager flink cancel <job-id>
```

### 查看日志
```bash
# JobManager 日志
docker logs -f flink-jobmanager

# TaskManager 日志
docker logs -f realtime-pipeline-taskmanager-1
```

## 下一步建议

### 选项 1: 使用 SimpleCDCApp（推荐）
最简单的方式，已经在生成 CSV 文件。

```bash
# 取消当前作业
docker exec flink-jobmanager flink cancel e0b3bb0c6fb9f659b5a42236545c55bd

# 提交 SimpleCDCApp
./submit-to-flink.sh simple
```

### 选项 2: 实现真实的 Kafka Consumer
修改 `DefaultDataHubClient.getRecords()` 以从 Kafka 真正读取数据。

需要修改的文件:
- `src/main/java/com/realtime/pipeline/datahub/client/DefaultDataHubClient.java`

### 选项 3: 使用 Flink Kafka Connector
替换 DataHubSource 为 Flink 官方的 Kafka Connector。

## 配置文件

### 当前使用的配置
- **位置**: `/opt/flink/conf/application.yml` (容器内)
- **本地副本**: `application-local.yml`

### 关键配置
```yaml
datahub:
  endpoint: kafka://kafka:9092
  topic: cdc-events
  consumerGroup: flink-consumer-group
  startPosition: LATEST  # ⚠️ 只读取新消息

output:
  path: /opt/flink/output/cdc
  format: csv
```

## 总结

✅ **正常工作的部分**:
- Flink 集群运行正常
- SimpleCDCApp 生成模拟数据并写入 CSV
- CSV 文件正在生成
- Kafka 服务正常

⚠️ **需要注意的部分**:
- DataHubSource 是 mock 实现，不会从 Kafka 读取真实数据
- FlinkPipelineMain 虽然运行，但没有处理 Kafka 数据
- 当前 CSV 输出来自之前的 SimpleCDCApp

💡 **建议**:
使用 SimpleCDCApp 进行演示和测试，它能正常生成 CSV 文件。
