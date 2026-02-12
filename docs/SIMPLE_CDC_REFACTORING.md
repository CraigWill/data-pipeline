# SimpleCDCApp 重构说明

## 重构目标

将 SimpleCDCApp 从独立实现重构为使用项目中已有的类和组件，提高代码复用性和一致性。

## 重构前后对比

### 重构前

```java
// 使用原始字符串和简单变量
String dbHost = getEnv("DATABASE_HOST", "localhost");
String dbPort = getEnv("DATABASE_PORT", "1521");
String dbUser = getEnv("DATABASE_USERNAME", "finance_user");
// ...

// 生成简单的 JSON 字符串
String event = String.format(
    "{\"table\":\"%s\",\"operation\":\"INSERT\",\"id\":%d}",
    dbTables, counter
);

// 简单的 CSV 转换
String csv = String.format("%s,%s", timestamp, event.replace(",", ";"));
```

**问题:**
- ❌ 没有使用项目的配置类（DatabaseConfig, OutputConfig）
- ❌ 没有使用项目的模型类（ChangeEvent, ProcessedEvent）
- ❌ 手动拼接 JSON 字符串，容易出错
- ❌ CSV 转换逻辑简单，不符合项目标准
- ❌ 代码重复，不利于维护

### 重构后

```java
// 使用 DatabaseConfig 配置类
DatabaseConfig dbConfig = DatabaseConfig.builder()
    .host(getEnv("DATABASE_HOST", "localhost"))
    .port(Integer.parseInt(getEnv("DATABASE_PORT", "1521")))
    .username(getEnv("DATABASE_USERNAME", "finance_user"))
    .password(getEnv("DATABASE_PASSWORD", "password"))
    .schema(getEnv("DATABASE_SCHEMA", "helowin"))
    .tables(tables)
    .build();

// 使用 OutputConfig 配置类
OutputConfig outputConfig = OutputConfig.builder()
    .path(getEnv("OUTPUT_PATH", "./output/cdc"))
    .format("csv")
    .build();

// 生成 ChangeEvent 对象
ChangeEvent event = ChangeEvent.builder()
    .eventType(operation)
    .database(config.getSchema())
    .table(table)
    .timestamp(System.currentTimeMillis())
    .before(operation.equals("INSERT") ? null : data)
    .after(operation.equals("DELETE") ? null : data)
    .primaryKeys(Collections.singletonList("id"))
    .eventId(UUID.randomUUID().toString())
    .build();

// 转换为 ProcessedEvent
ProcessedEvent processedEvent = convertToProcessedEvent(event);

// 标准的 CSV 转换
String csv = convertToCSV(processedEvent);
```

**改进:**
- ✅ 使用项目标准配置类
- ✅ 使用项目标准模型类
- ✅ 类型安全的对象构建
- ✅ 符合项目架构规范
- ✅ 代码可维护性提高

## 使用的项目类

### 1. 配置类

| 类名 | 用途 | 位置 |
|------|------|------|
| `DatabaseConfig` | 数据库连接配置 | `com.realtime.pipeline.config` |
| `OutputConfig` | 输出文件配置 | `com.realtime.pipeline.config` |

### 2. 模型类

| 类名 | 用途 | 位置 |
|------|------|------|
| `ChangeEvent` | CDC 变更事件 | `com.realtime.pipeline.model` |
| `ProcessedEvent` | 处理后的事件 | `com.realtime.pipeline.model` |

### 3. 数据流转换

```
MockCDCSource (生成数据)
    ↓
ChangeEvent (CDC 事件)
    ↓
ProcessedEvent (处理后事件)
    ↓
CSV String (CSV 格式)
    ↓
FileSink (写入文件)
```

## 代码结构

### 配置加载

```java
private static DatabaseConfig loadDatabaseConfig() {
    // 从环境变量构建 DatabaseConfig
    return DatabaseConfig.builder()
        .host(getEnv("DATABASE_HOST", "localhost"))
        .port(Integer.parseInt(getEnv("DATABASE_PORT", "1521")))
        // ...
        .build();
}

private static OutputConfig loadOutputConfig() {
    // 从环境变量构建 OutputConfig
    return OutputConfig.builder()
        .path(getEnv("OUTPUT_PATH", "./output/cdc"))
        .format("csv")
        // ...
        .build();
}
```

### 数据源

```java
private static class MockCDCSource implements SourceFunction<ChangeEvent> {
    private final DatabaseConfig config;
    
    @Override
    public void run(SourceContext<ChangeEvent> ctx) throws Exception {
        // 生成 ChangeEvent 对象
        ChangeEvent event = ChangeEvent.builder()
            .eventType(operation)
            .database(config.getSchema())
            .table(table)
            // ...
            .build();
        
        ctx.collect(event);
    }
}
```

### 数据转换

```java
// ChangeEvent -> ProcessedEvent
private static ProcessedEvent convertToProcessedEvent(ChangeEvent event) {
    return ProcessedEvent.builder()
        .eventType(event.getEventType())
        .database(event.getDatabase())
        .table(event.getTable())
        .timestamp(event.getTimestamp())
        .processTime(System.currentTimeMillis())
        .data(event.getData())
        .eventId(event.getEventId())
        .build();
}

// ProcessedEvent -> CSV String
private static String convertToCSV(ProcessedEvent event) {
    StringBuilder csv = new StringBuilder();
    csv.append(timestamp).append(",");
    csv.append(event.getTable()).append(",");
    csv.append(event.getEventType()).append(",");
    // 添加数据字段
    return csv.toString();
}
```

## 运行方式

### 使用新的启动脚本

```bash
./start-simple-cdc.sh
```

### 输出示例

```csv
2026-02-12 09:00:00,trans_info,INSERT,"id=0","name=sample_name_0","value=123","timestamp=1707703200000",
2026-02-12 09:00:05,trans_info,UPDATE,"id=1","name=sample_name_1","value=456","timestamp=1707703205000",
2026-02-12 09:00:10,trans_info,DELETE,"id=2","name=sample_name_2","value=789","timestamp=1707703210000",
```

## 优势

### 1. 代码复用
- 使用项目标准配置类，避免重复代码
- 使用项目标准模型类，保持数据结构一致

### 2. 类型安全
- 使用强类型对象而不是字符串
- 编译时检查，减少运行时错误

### 3. 可维护性
- 配置集中管理
- 模型统一定义
- 易于扩展和修改

### 4. 一致性
- 与项目其他组件保持一致
- 遵循项目架构规范
- 便于团队协作

### 5. 可测试性
- 配置类可以独立测试
- 模型类可以独立测试
- 转换逻辑可以独立测试

## 下一步改进

1. **使用真实的 CDC Connector**
   - 替换 MockCDCSource 为 Flink CDC Connector
   - 连接真实的 Oracle 数据库

2. **使用项目的 CsvFileSink**
   - 替换简单的 FileSink 为项目的 CsvFileSink
   - 获得更完善的 CSV 格式化功能

3. **添加错误处理**
   - 使用项目的 DeadLetterQueue
   - 处理失败的记录

4. **添加监控**
   - 使用项目的 MonitoringService
   - 收集运行指标

5. **配置文件支持**
   - 支持从 YAML 文件加载配置
   - 不仅仅依赖环境变量

## 总结

重构后的 SimpleCDCApp：
- ✅ 使用项目标准配置类（DatabaseConfig, OutputConfig）
- ✅ 使用项目标准模型类（ChangeEvent, ProcessedEvent）
- ✅ 代码结构清晰，易于理解
- ✅ 符合项目架构规范
- ✅ 为后续功能扩展打下基础

这次重构大大提高了代码质量和可维护性，使 SimpleCDCApp 真正成为项目的一部分。
