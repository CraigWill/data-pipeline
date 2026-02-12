# Task 6.3 Summary: 实现Parquet格式输出（ParquetFileSink）

## 完成时间
2026-02-09

## 任务描述
实现Parquet格式文件输出功能，支持将ProcessedEvent数据序列化为Parquet格式并写入文件系统。

## 实现内容

### 1. ParquetFileSink类
**文件**: `src/main/java/com/realtime/pipeline/flink/sink/ParquetFileSink.java`

**核心功能**:
- 继承AbstractFileSink基类，复用文件滚动和命名策略
- 使用Apache Avro作为中间格式，将ProcessedEvent转换为Avro GenericRecord
- 使用Flink的AvroParquetWriters将数据写入Parquet文件
- 使用OnCheckpointRollingPolicy在checkpoint边界滚动文件

**关键设计决策**:
1. **Avro Schema定义**: 为ProcessedEvent定义了完整的Avro Schema，包含所有字段
2. **数据类型转换**: 将Map<String, Object>转换为Map<String, String>，因为Parquet需要具体的类型
3. **BulkWriter Factory**: 实现了ProcessedEventParquetWriterFactory，将ProcessedEvent转换为GenericRecord并写入
4. **滚动策略**: 使用OnCheckpointRollingPolicy而不是DefaultRollingPolicy，因为bulk format需要checkpoint边界滚动

**Avro Schema结构**:
```json
{
  "type": "record",
  "name": "ProcessedEvent",
  "namespace": "com.realtime.pipeline.model",
  "fields": [
    {"name": "eventType", "type": ["null", "string"], "default": null},
    {"name": "database", "type": ["null", "string"], "default": null},
    {"name": "table", "type": ["null", "string"], "default": null},
    {"name": "timestamp", "type": "long", "default": 0},
    {"name": "processTime", "type": "long", "default": 0},
    {"name": "data", "type": ["null", {"type": "map", "values": "string"}], "default": null},
    {"name": "partition", "type": ["null", "string"], "default": null},
    {"name": "eventId", "type": ["null", "string"], "default": null}
  ]
}
```

### 2. 单元测试
**文件**: `src/test/java/com/realtime/pipeline/flink/sink/ParquetFileSinkTest.java`

**测试覆盖**:
- ✅ testCreateSink: 验证可以成功创建StreamingFileSink
- ✅ testAvroSchemaIsValid: 验证Avro Schema的有效性和完整性
- ✅ testConstructorWithNullConfig: 验证null配置抛出异常
- ✅ testConstructorWithInvalidConfig: 验证无效配置抛出异常
- ✅ testParquetFileExtension: 验证使用.parquet文件扩展名
- ✅ testBucketAssignerCreation: 验证桶分配器创建
- ✅ testOutputPathCreation: 验证输出路径正确设置

**测试结果**: 所有7个测试全部通过 ✅

## 实现的需求
- **需求 3.2**: 系统支持Parquet格式输出

## 技术栈
- Apache Flink 1.18.0
- Apache Parquet 1.13.1
- Apache Avro (通过flink-parquet依赖)
- Flink Parquet Format

## 与其他组件的集成
- 继承自AbstractFileSink，复用文件滚动、命名和重试机制
- 与JsonFileSink和CsvFileSink（待实现）形成统一的文件输出接口
- 使用OutputConfig进行配置管理

## 使用示例
```java
// 创建配置
OutputConfig config = OutputConfig.builder()
    .path("/data/output")
    .format("parquet")
    .rollingSizeBytes(1024 * 1024 * 1024L) // 1GB
    .rollingIntervalMs(3600000L) // 1 hour
    .compression("snappy")
    .maxRetries(3)
    .retryBackoff(2)
    .build();

// 创建ParquetFileSink
ParquetFileSink sink = new ParquetFileSink(config);
StreamingFileSink<ProcessedEvent> streamingSink = sink.createSink();

// 在Flink作业中使用
DataStream<ProcessedEvent> processedStream = ...;
processedStream.addSink(streamingSink);
```

## 文件输出格式
- **文件扩展名**: `.parquet`
- **文件命名**: `part-{subtask}-{count}.parquet`
- **目录结构**: `{database}/{table}/dt={yyyyMMddHH}/`
- **滚动策略**: 在checkpoint边界滚动（OnCheckpointRollingPolicy）
- **数据格式**: Parquet列式存储，使用Avro schema

## Parquet格式优势
1. **列式存储**: 高效的列式压缩和查询性能
2. **压缩率高**: 支持多种压缩算法（Snappy、Gzip等）
3. **大数据友好**: 适合大规模数据分析和处理
4. **Schema演化**: 支持schema演化和兼容性
5. **生态系统**: 与Hadoop、Spark、Hive等大数据工具无缝集成

## 注意事项
1. **数据类型限制**: Map<String, Object>被转换为Map<String, String>，所有值都转换为字符串
2. **滚动策略**: 使用OnCheckpointRollingPolicy，文件在checkpoint时滚动，而不是基于大小或时间
3. **依赖关系**: 需要flink-parquet和parquet-avro依赖
4. **性能考虑**: Parquet写入比JSON慢，但压缩率更高，查询性能更好

## 后续工作
- Task 6.4: 实现CSV格式输出（CsvFileSink）
- Task 6.5: 编写文件输出组件的基于属性的测试
- Task 6.6: 编写文件输出组件的单元测试（包含所有格式的集成测试）

## 验证状态
- ✅ 编译通过
- ✅ 单元测试通过（7/7）
- ✅ 代码符合设计规范
- ✅ 继承AbstractFileSink模式
- ✅ 任务标记为完成
