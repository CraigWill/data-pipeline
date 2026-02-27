# CSV 文件生成速度优化 - 完成

## 问题描述

CSV 文件生成速度太慢，之前配置需要 3 分钟才能完成文件写入。

## 优化方案

### 1. 缩短 Checkpoint 间隔

**修改前**:
```java
env.enableCheckpointing(180000);  // 3分钟
env.getCheckpointConfig().setMinPauseBetweenCheckpoints(60000);  // 1分钟
env.getCheckpointConfig().setCheckpointTimeout(600000);  // 10分钟
```

**修改后**:
```java
env.enableCheckpointing(30000);  // 30秒
env.getCheckpointConfig().setMinPauseBetweenCheckpoints(10000);  // 10秒
env.getCheckpointConfig().setCheckpointTimeout(120000);  // 2分钟
```

### 2. 优化 Rolling Policy

**修改前**:
```java
DefaultRollingPolicy.builder()
    .withRolloverInterval(Duration.ofMinutes(5))  // 5分钟
    .withInactivityInterval(Duration.ofMinutes(2))  // 2分钟
    .withMaxPartSize(MemorySize.ofMebiBytes(128))  // 128MB
    .build()
```

**修改后**:
```java
DefaultRollingPolicy.builder()
    .withRolloverInterval(Duration.ofSeconds(30))  // 30秒
    .withInactivityInterval(Duration.ofSeconds(10))  // 10秒不活动就滚动
    .withMaxPartSize(MemorySize.ofMebiBytes(10))  // 10MB
    .build()
```

## 优化效果

### 性能对比

| 指标 | 优化前 | 优化后 | 提升 |
|------|--------|--------|------|
| Checkpoint 间隔 | 180秒 | 30秒 | 6倍 |
| 文件滚动间隔 | 300秒 | 30秒 | 10倍 |
| 不活动滚动 | 120秒 | 10秒 | 12倍 |
| 文件大小阈值 | 128MB | 10MB | 12.8倍 |
| 实际生成时间 | ~180秒 | ~30-40秒 | 4.5-6倍 |

### 实测结果

**测试时间线**:
```
14:25:01 - 插入数据到数据库
14:25:08 - CDC 捕获事件
14:25:48 - CSV 文件完成写入（约 40 秒）
```

**生成的文件**:
```csv
交易时间,操作类型,ID,ACCOUNT_ID,AMOUNT,TRANS_TIME,TRANS_TYPE,MERCHANT_ID,STATUS
2026-02-25 14:25:08,UPDATE,999999,ACC06157155,100.01,1719628422000,DEPOSIT,MER048266,COMPLETED
2026-02-25 14:25:08,UPDATE,999999,ACC06157155,200.02,1719628422000,DEPOSIT,MER048266,COMPLETED
2026-02-25 14:25:08,UPDATE,999999,ACC06157155,300.03,1719628422000,DEPOSIT,MER048266,COMPLETED
2026-02-25 14:25:08,UPDATE,999999,ACC06157155,400.04,1719628422000,DEPOSIT,MER048266,COMPLETED
2026-02-25 14:25:08,UPDATE,999999,ACC06157155,500.05,1719628422000,DEPOSIT,MER048266,COMPLETED
```

## 配置说明

### Checkpoint 配置

- **Checkpoint 间隔 (30秒)**: 控制 Flink 状态快照的频率，也是文件从 `.inprogress` 变为最终文件的触发条件
- **最小暂停间隔 (10秒)**: 两次 checkpoint 之间的最小间隔
- **Checkpoint 超时 (2分钟)**: 单次 checkpoint 的最大执行时间

### Rolling Policy 配置

- **滚动间隔 (30秒)**: 文件打开后最多 30 秒就会滚动到新文件
- **不活动间隔 (10秒)**: 如果 10 秒内没有新数据，就关闭当前文件
- **最大文件大小 (10MB)**: 文件达到 10MB 就会滚动

## 工作原理

1. CDC 事件被捕获并写入 `.inprogress` 文件
2. 当满足以下任一条件时，文件会滚动：
   - 时间达到 30 秒
   - 10 秒内没有新数据
   - 文件大小达到 10MB
3. 当 checkpoint 完成时（每 30 秒），`.inprogress` 文件会被重命名为最终的 `.csv` 文件

## 权衡考虑

### 优点
- ✅ 文件生成速度快（30-40 秒）
- ✅ 近实时的数据可见性
- ✅ 更小的文件便于处理

### 缺点
- ⚠️ 更频繁的 checkpoint 会增加系统开销
- ⚠️ 生成更多的小文件
- ⚠️ 可能增加 I/O 操作

### 适用场景
- 需要近实时查看 CDC 数据
- 数据量不是特别大
- 对延迟敏感的场景

### 不适用场景
- 超大数据量（建议增加 checkpoint 间隔）
- 对系统资源敏感（建议减少 checkpoint 频率）
- 需要大文件批处理（建议增加滚动间隔和文件大小）

## 进一步优化建议

如果需要更快的文件生成：
1. 进一步缩短 checkpoint 间隔（最低建议 10 秒）
2. 减少不活动间隔（最低建议 5 秒）

如果需要平衡性能和资源：
1. 适当增加 checkpoint 间隔（建议 60 秒）
2. 增加文件大小阈值（建议 50MB）

## 当前作业状态

- Job ID: `acba9e6d5f4a2663324e3b1a0274fe7c`
- Job State: RUNNING
- Job Name: Flink CDC 3.x Oracle Application
- Checkpoint 间隔: 30 秒
- 文件滚动间隔: 30 秒
- 不活动间隔: 10 秒

## 相关文件

- `src/main/java/com/realtime/pipeline/FlinkCDC3App.java` - 主应用程序
- `CSV_TIMESTAMP_NAMING.md` - CSV 文件时间戳命名文档
- `CDC_CURRENT_TIME_UPDATE.md` - 交易时间使用当前时间文档
- `output/cdc/` - 输出目录

## 监控建议

1. 监控 checkpoint 成功率：`curl http://localhost:8081/jobs/<job-id>/checkpoints`
2. 监控文件生成速度：`watch -n 5 'ls -lht output/cdc/*/cdc_events_*.csv | head -5'`
3. 监控系统资源：`docker stats flink-jobmanager realtime-pipeline-taskmanager-1`
