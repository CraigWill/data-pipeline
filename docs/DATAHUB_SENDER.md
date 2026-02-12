# DataHub发送器实现文档

## 概述

DataHub发送器（DataHubSender）负责将CDC变更事件发送到阿里云DataHub服务。该组件实现了可靠的数据传输机制，包括自动重试和错误处理。

## 功能特性

### 1. 数据发送（需求1.4）

- **单条发送**: 支持发送单个变更事件到DataHub
- **批量发送**: 支持批量发送多个变更事件，提高吞吐量
- **数据序列化**: 自动将ChangeEvent对象转换为DataHub记录格式
- **分区策略**: 使用表名的哈希值作为分区键，确保同一表的数据在同一分区

### 2. 重试机制（需求1.5）

- **最大重试次数**: 默认3次（可配置）
- **重试间隔**: 默认2秒（可配置）
- **指数退避**: 支持固定间隔和指数退避两种重试策略
- **异常过滤**: 只对可重试的异常进行重试

### 3. 监控指标

- **已发送记录数**: 成功发送到DataHub的记录总数
- **失败记录数**: 发送失败的记录总数
- **重试次数**: 总重试次数统计

### 4. 连接管理

- **连接测试**: 提供testConnection()方法测试DataHub连接
- **自动初始化**: 支持延迟初始化和自动重连
- **资源清理**: 实现AutoCloseable接口，支持自动资源管理

## 架构设计

### 类结构

```
com.realtime.pipeline.datahub
├── DataHubSender              # 主发送器类
├── DataHubSendException       # 发送异常类
└── client/                    # DataHub客户端接口层
    ├── DataHubClient          # 客户端接口
    ├── DefaultDataHubClient   # 默认实现
    ├── DataHubClientException # 客户端异常
    ├── RecordEntry            # 记录条目
    ├── PutRecordResult        # 单条发送结果
    ├── PutRecordsResult       # 批量发送结果
    └── TopicInfo              # 主题信息
```

### 接口设计

```java
public class DataHubSender implements Serializable, AutoCloseable {
    // 发送单个事件
    public void send(ChangeEvent event) throws DataHubSendException;
    
    // 批量发送事件
    public void sendBatch(List<ChangeEvent> events) throws DataHubSendException;
    
    // 测试连接
    public boolean testConnection();
    
    // 获取指标
    public long getRecordsSent();
    public long getRecordsFailed();
    public long getRetryCount();
    
    // 关闭资源
    public void close();
}
```

## 使用示例

### 基本使用

```java
// 1. 创建配置
DataHubConfig config = DataHubConfig.builder()
    .endpoint("https://dh-cn-hangzhou.aliyuncs.com")
    .accessId("your-access-id")
    .accessKey("your-access-key")
    .project("your-project")
    .topic("cdc-events")
    .consumerGroup("flink-consumer")
    .maxRetries(3)
    .retryBackoff(2)
    .build();

// 2. 创建发送器
DataHubSender sender = new DataHubSender(config);

// 3. 测试连接
if (sender.testConnection()) {
    System.out.println("DataHub connection successful");
}

// 4. 发送单个事件
ChangeEvent event = ChangeEvent.builder()
    .eventType("INSERT")
    .database("mydb")
    .table("users")
    .timestamp(System.currentTimeMillis())
    .after(Map.of("id", 1, "name", "张三"))
    .primaryKeys(List.of("id"))
    .build();

try {
    sender.send(event);
    System.out.println("Event sent successfully");
} catch (DataHubSendException e) {
    System.err.println("Failed to send event: " + e.getMessage());
}

// 5. 关闭发送器
sender.close();
```

### 批量发送

```java
List<ChangeEvent> events = new ArrayList<>();
for (int i = 0; i < 100; i++) {
    events.add(createChangeEvent(i));
}

try {
    sender.sendBatch(events);
    System.out.println("Batch sent successfully: " + events.size() + " events");
} catch (DataHubSendException e) {
    System.err.println("Failed to send batch: " + e.getMessage());
}
```

### 使用try-with-resources

```java
try (DataHubSender sender = new DataHubSender(config)) {
    sender.send(event);
    System.out.println("Records sent: " + sender.getRecordsSent());
} catch (Exception e) {
    e.printStackTrace();
}
```

## 配置说明

### DataHubConfig参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| endpoint | String | 是 | - | DataHub服务端点 |
| accessId | String | 是 | - | 阿里云访问ID |
| accessKey | String | 是 | - | 阿里云访问密钥 |
| project | String | 是 | - | DataHub项目名称 |
| topic | String | 是 | - | DataHub主题名称 |
| consumerGroup | String | 是 | - | 消费者组名称 |
| startPosition | String | 否 | LATEST | 起始消费位置（EARLIEST/LATEST/TIMESTAMP） |
| maxRetries | int | 否 | 3 | 最大重试次数 |
| retryBackoff | int | 否 | 2 | 重试间隔（秒） |

### 配置文件示例

```yaml
datahub:
  endpoint: https://dh-cn-hangzhou.aliyuncs.com
  accessId: ${DATAHUB_ACCESS_ID}
  accessKey: ${DATAHUB_ACCESS_KEY}
  project: realtime-pipeline
  topic: cdc-events
  consumerGroup: flink-consumer-group
  startPosition: LATEST
  maxRetries: 3
  retryBackoff: 2
```

## 错误处理

### 异常类型

1. **DataHubSendException**: 发送失败异常
   - 所有重试都失败后抛出
   - 包含原始异常信息

2. **DataHubClientException**: 客户端异常
   - 网络错误
   - 认证失败
   - 服务端错误

3. **IllegalArgumentException**: 参数错误
   - 配置参数无效
   - 事件对象为null

### 重试策略

```java
// 重试逻辑伪代码
for (int attempt = 1; attempt <= maxRetries; attempt++) {
    try {
        sendToDataHub(event);
        return; // 成功
    } catch (DataHubClientException e) {
        if (attempt < maxRetries) {
            Thread.sleep(retryBackoff * 1000);
            continue; // 重试
        } else {
            throw new DataHubSendException("All retries failed", e);
        }
    }
}
```

### 错误处理最佳实践

```java
try {
    sender.send(event);
} catch (DataHubSendException e) {
    // 记录错误日志
    logger.error("Failed to send event: {}", event.getEventId(), e);
    
    // 发送到死信队列
    deadLetterQueue.add(event);
    
    // 触发告警
    alertManager.sendAlert("DataHub send failed", e.getMessage());
}
```

## 性能优化

### 1. 批量发送

批量发送可以显著提高吞吐量：

```java
// 不推荐：逐条发送
for (ChangeEvent event : events) {
    sender.send(event);  // 每次都有网络开销
}

// 推荐：批量发送
sender.sendBatch(events);  // 一次网络请求
```

### 2. 异步发送

对于高吞吐量场景，可以使用异步发送：

```java
ExecutorService executor = Executors.newFixedThreadPool(10);

for (ChangeEvent event : events) {
    executor.submit(() -> {
        try {
            sender.send(event);
        } catch (DataHubSendException e) {
            logger.error("Failed to send event", e);
        }
    });
}

executor.shutdown();
executor.awaitTermination(1, TimeUnit.MINUTES);
```

### 3. 连接池

对于多线程环境，建议使用连接池：

```java
// 创建多个sender实例
List<DataHubSender> senderPool = new ArrayList<>();
for (int i = 0; i < 10; i++) {
    senderPool.add(new DataHubSender(config));
}

// 轮询使用
int index = 0;
for (ChangeEvent event : events) {
    DataHubSender sender = senderPool.get(index % senderPool.size());
    sender.send(event);
    index++;
}
```

## 监控和指标

### 指标收集

```java
// 定期收集指标
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
scheduler.scheduleAtFixedRate(() -> {
    long sent = sender.getRecordsSent();
    long failed = sender.getRecordsFailed();
    long retries = sender.getRetryCount();
    
    logger.info("DataHub Metrics - Sent: {}, Failed: {}, Retries: {}", 
        sent, failed, retries);
    
    // 发送到监控系统
    metricsCollector.recordGauge("datahub.records.sent", sent);
    metricsCollector.recordGauge("datahub.records.failed", failed);
    metricsCollector.recordGauge("datahub.retries", retries);
}, 0, 60, TimeUnit.SECONDS);
```

### 告警规则

建议配置以下告警：

1. **发送失败率 > 5%**: 表示DataHub服务可能有问题
2. **重试次数 > 100/分钟**: 表示网络或服务不稳定
3. **连接测试失败**: 表示DataHub服务不可用

## 测试

### 单元测试

```java
@Test
void testSendWithRetry() throws Exception {
    // 配置mock：前2次失败，第3次成功
    when(mockClient.putRecords(any(), any(), any()))
        .thenThrow(new DataHubClientException("Network error"))
        .thenThrow(new DataHubClientException("Network error"))
        .thenReturn(successResult);
    
    // 执行发送
    sender.send(event);
    
    // 验证重试了3次
    verify(mockClient, times(3)).putRecords(any(), any(), any());
}
```

### 集成测试

```java
@Test
void testIntegrationWithRealDataHub() {
    // 使用真实配置
    DataHubConfig config = loadConfigFromFile("test-config.yml");
    DataHubSender sender = new DataHubSender(config);
    
    // 测试连接
    assertTrue(sender.testConnection());
    
    // 发送测试事件
    ChangeEvent event = createTestEvent();
    assertDoesNotThrow(() -> sender.send(event));
    
    // 验证指标
    assertEquals(1, sender.getRecordsSent());
}
```

## 生产部署

### 使用真实DataHub SDK

当前实现使用了模拟的DataHub客户端。在生产环境中，需要：

1. **下载阿里云DataHub SDK**:
   ```bash
   # 从阿里云官网下载SDK
   wget https://datahub-public.oss-cn-hangzhou.aliyuncs.com/sdk/aliyun-sdk-datahub-2.25.2.jar
   ```

2. **安装到本地Maven仓库**:
   ```bash
   mvn install:install-file \
     -Dfile=aliyun-sdk-datahub-2.25.2.jar \
     -DgroupId=com.aliyun.datahub \
     -DartifactId=aliyun-sdk-datahub \
     -Dversion=2.25.2 \
     -Dpackaging=jar
   ```

3. **更新pom.xml**:
   ```xml
   <dependency>
       <groupId>com.aliyun.datahub</groupId>
       <artifactId>aliyun-sdk-datahub</artifactId>
       <version>2.25.2</version>
   </dependency>
   ```

4. **实现真实客户端**: 创建使用官方SDK的DataHubClient实现

### 配置建议

生产环境配置建议：

```yaml
datahub:
  endpoint: https://dh-cn-hangzhou.aliyuncs.com
  accessId: ${DATAHUB_ACCESS_ID}  # 从环境变量读取
  accessKey: ${DATAHUB_ACCESS_KEY}  # 从环境变量读取
  project: prod-realtime-pipeline
  topic: cdc-events-prod
  consumerGroup: flink-consumer-prod
  maxRetries: 5  # 生产环境增加重试次数
  retryBackoff: 3  # 增加重试间隔
```

## 故障排查

### 常见问题

1. **连接超时**
   - 检查网络连接
   - 验证endpoint是否正确
   - 检查防火墙规则

2. **认证失败**
   - 验证accessId和accessKey
   - 检查账户权限
   - 确认project和topic存在

3. **发送失败**
   - 查看详细错误日志
   - 检查DataHub服务状态
   - 验证数据格式是否正确

### 日志级别

```properties
# 开发环境：DEBUG级别
logging.level.com.realtime.pipeline.datahub=DEBUG

# 生产环境：INFO级别
logging.level.com.realtime.pipeline.datahub=INFO
```

## 参考资料

- [阿里云DataHub官方文档](https://help.aliyun.com/product/53345.html)
- [DataHub Java SDK文档](https://help.aliyun.com/document_detail/158778.html)
- [Flink CDC Connector文档](https://ververica.github.io/flink-cdc-connectors/)

## 更新日志

### v1.0.0 (2025-01-28)
- 初始实现
- 支持单条和批量发送
- 实现重试机制（最多3次，间隔2秒）
- 提供监控指标
- 完整的单元测试覆盖
