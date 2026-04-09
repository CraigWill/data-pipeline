# CDC 文件大小和行数控制指南

## 当前配置

### 使用的滚动策略

当前系统使用 `OnCheckpointRollingPolicy`，这意味着：

```java
.withRollingPolicy(OnCheckpointRollingPolicy.build())
```

**特点**：
- 文件在每次 Checkpoint 时滚动（关闭当前文件，开始新文件）
- Checkpoint 间隔由 Flink 配置决定（默认 5 分钟）
- 无法直接控制文件大小或行数

## 如何控制文件大小和行数

### 方案 1：使用 DefaultRollingPolicy（推荐）

`DefaultRollingPolicy` 支持三种滚动条件（满足任一即滚动）：

1. **文件大小**：达到指定大小
2. **时间间隔**：超过指定时间
3. **空闲时间**：超过指定时间没有新数据

#### 代码示例

```java
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;
import java.time.Duration;

// 配置滚动策略
DefaultRollingPolicy<String, String> rollingPolicy = DefaultRollingPolicy
    .builder()
    .withRolloverInterval(Duration.ofMinutes(15))  // 15分钟滚动一次
    .withInactivityInterval(Duration.ofMinutes(5)) // 5分钟无数据则滚动
    .withMaxPartSize(128 * 1024 * 1024L)           // 128MB 滚动
    .build();

// 应用到 FileSink
FileSink<String> fileSink = FileSink
    .forRowFormat(new Path(outputPath), (Encoder<String>) (element, stream) -> {
        stream.write(element.getBytes(StandardCharsets.UTF_8));
        stream.write('\n');
    })
    .withRollingPolicy(rollingPolicy)  // 使用自定义策略
    .withOutputFileConfig(OutputFileConfig.builder()
        .withPartPrefix("IDS_" + tableName.toUpperCase() + "_" + timestamp)
        .withPartSuffix(".csv")
        .build())
    .build();
```

### 方案 2：自定义行数滚动策略

Flink 没有内置的按行数滚动策略，需要自定义实现：

```java
import org.apache.flink.core.io.SimpleVersionedSerializer;
import org.apache.flink.streaming.api.functions.sink.filesystem.PartFileInfo;
import org.apache.flink.streaming.api.functions.sink.filesystem.RollingPolicy;

public class RowCountRollingPolicy<IN, BucketID> implements RollingPolicy<IN, BucketID> {
    
    private final long maxRowCount;
    private long currentRowCount = 0;
    
    public RowCountRollingPolicy(long maxRowCount) {
        this.maxRowCount = maxRowCount;
    }
    
    @Override
    public boolean shouldRollOnCheckpoint(PartFileInfo<BucketID> partFileState) {
        return currentRowCount >= maxRowCount;
    }
    
    @Override
    public boolean shouldRollOnEvent(PartFileInfo<BucketID> partFileState, IN element) {
        currentRowCount++;
        return currentRowCount >= maxRowCount;
    }
    
    @Override
    public boolean shouldRollOnProcessingTime(PartFileInfo<BucketID> partFileState, long currentTime) {
        return false;
    }
    
    @Override
    public void onPartFileOpened(PartFileInfo<BucketID> partFileState) {
        currentRowCount = 0;
    }
}

// 使用示例
RollingPolicy<String, String> rowCountPolicy = new RowCountRollingPolicy<>(10000); // 每10000行滚动
```

### 方案 3：组合策略

结合文件大小、时间和行数：

```java
public class CombinedRollingPolicy<IN, BucketID> implements RollingPolicy<IN, BucketID> {
    
    private final long maxRowCount;
    private final long maxFileSize;
    private final long maxRolloverInterval;
    
    private long currentRowCount = 0;
    private long fileStartTime = 0;
    
    public CombinedRollingPolicy(long maxRowCount, long maxFileSize, long maxRolloverInterval) {
        this.maxRowCount = maxRowCount;
        this.maxFileSize = maxFileSize;
        this.maxRolloverInterval = maxRolloverInterval;
    }
    
    @Override
    public boolean shouldRollOnCheckpoint(PartFileInfo<BucketID> partFileState) {
        return currentRowCount >= maxRowCount || 
               partFileState.getSize() >= maxFileSize;
    }
    
    @Override
    public boolean shouldRollOnEvent(PartFileInfo<BucketID> partFileState, IN element) {
        currentRowCount++;
        return currentRowCount >= maxRowCount || 
               partFileState.getSize() >= maxFileSize;
    }
    
    @Override
    public boolean shouldRollOnProcessingTime(PartFileInfo<BucketID> partFileState, long currentTime) {
        return (currentTime - fileStartTime) >= maxRolloverInterval;
    }
    
    @Override
    public void onPartFileOpened(PartFileInfo<BucketID> partFileState) {
        currentRowCount = 0;
        fileStartTime = System.currentTimeMillis();
    }
}

// 使用示例
RollingPolicy<String, String> combinedPolicy = new CombinedRollingPolicy<>(
    50000,                    // 最多50000行
    100 * 1024 * 1024L,      // 最大100MB
    15 * 60 * 1000L          // 最长15分钟
);
```

## 推荐配置

### 小数据量场景（< 1000 行/秒）

```java
DefaultRollingPolicy.builder()
    .withRolloverInterval(Duration.ofMinutes(30))  // 30分钟
    .withInactivityInterval(Duration.ofMinutes(10)) // 10分钟无数据
    .withMaxPartSize(50 * 1024 * 1024L)            // 50MB
    .build();
```

**预期结果**：
- 文件大小：10-50MB
- 行数：约 10,000 - 50,000 行
- 滚动频率：10-30 分钟

### 中等数据量场景（1000 - 10000 行/秒）

```java
DefaultRollingPolicy.builder()
    .withRolloverInterval(Duration.ofMinutes(15))  // 15分钟
    .withInactivityInterval(Duration.ofMinutes(5))  // 5分钟无数据
    .withMaxPartSize(128 * 1024 * 1024L)           // 128MB
    .build();
```

**预期结果**：
- 文件大小：50-128MB
- 行数：约 50,000 - 500,000 行
- 滚动频率：5-15 分钟

### 大数据量场景（> 10000 行/秒）

```java
DefaultRollingPolicy.builder()
    .withRolloverInterval(Duration.ofMinutes(10))  // 10分钟
    .withInactivityInterval(Duration.ofMinutes(2))  // 2分钟无数据
    .withMaxPartSize(256 * 1024 * 1024L)           // 256MB
    .build();
```

**预期结果**：
- 文件大小：100-256MB
- 行数：约 500,000 - 2,000,000 行
- 滚动频率：2-10 分钟

## 实施步骤

### 1. 修改 CdcJobMain.java

在 `flink-jobs/src/main/java/com/realtime/pipeline/CdcJobMain.java` 中：

```java
// 添加参数解析
int maxFileSizeMB = Integer.parseInt(params.getOrDefault("maxFileSizeMB", "128"));
int rolloverMinutes = Integer.parseInt(params.getOrDefault("rolloverMinutes", "15"));
int inactivityMinutes = Integer.parseInt(params.getOrDefault("inactivityMinutes", "5"));

// 创建滚动策略
DefaultRollingPolicy<String, String> rollingPolicy = DefaultRollingPolicy
    .builder()
    .withRolloverInterval(Duration.ofMinutes(rolloverMinutes))
    .withInactivityInterval(Duration.ofMinutes(inactivityMinutes))
    .withMaxPartSize(maxFileSizeMB * 1024 * 1024L)
    .build();

// 替换现有的 OnCheckpointRollingPolicy
FileSink<String> fileSink = FileSink
    .forRowFormat(new Path(outputPath), (Encoder<String>) (element, stream) -> {
        stream.write(element.getBytes(StandardCharsets.UTF_8));
        stream.write('\n');
    })
    .withRollingPolicy(rollingPolicy)  // 使用新策略
    .withOutputFileConfig(OutputFileConfig.builder()
        .withPartPrefix("IDS_" + tableName.toUpperCase() + "_" + timestamp)
        .withPartSuffix(".csv")
        .build())
    .build();
```

### 2. 更新 TaskConfig 和 CdcSubmitRequest

添加新的配置字段：

```java
// TaskConfig.java
private int maxFileSizeMB = 128;
private int rolloverMinutes = 15;
private int inactivityMinutes = 5;

// CdcSubmitRequest.java
private int maxFileSizeMB = 128;
private int rolloverMinutes = 15;
private int inactivityMinutes = 5;
```

### 3. 更新前端界面

在任务创建页面添加配置选项：

```vue
<div class="form-group">
  <label>文件大小限制 (MB)</label>
  <input v-model.number="form.maxFileSizeMB" type="number" min="10" max="1024" />
</div>

<div class="form-group">
  <label>滚动时间间隔 (分钟)</label>
  <input v-model.number="form.rolloverMinutes" type="number" min="1" max="60" />
</div>

<div class="form-group">
  <label>空闲滚动时间 (分钟)</label>
  <input v-model.number="form.inactivityMinutes" type="number" min="1" max="30" />
</div>
```

## 文件大小估算

### CSV 文件大小计算

```
文件大小 = 行数 × 平均行宽度

示例：
- 10列，每列平均20字符
- 平均行宽度 ≈ 200 字节
- 10,000 行 ≈ 2 MB
- 50,000 行 ≈ 10 MB
- 100,000 行 ≈ 20 MB
- 500,000 行 ≈ 100 MB
```

### 根据行数设置文件大小

| 目标行数 | 平均行宽 | 推荐文件大小 |
|---------|---------|-------------|
| 10,000 | 200 字节 | 10 MB |
| 50,000 | 200 字节 | 50 MB |
| 100,000 | 200 字节 | 100 MB |
| 500,000 | 200 字节 | 500 MB |
| 1,000,000 | 200 字节 | 1 GB |

## 监控和调优

### 1. 查看文件大小分布

```bash
# 查看输出目录中的文件大小
ls -lh output/cdc/*/IDS_*.csv | awk '{print $5, $9}'

# 统计平均文件大小
du -sh output/cdc/*/*.csv | awk '{sum+=$1; count++} END {print sum/count}'
```

### 2. 查看文件行数分布

```bash
# 统计每个文件的行数
for file in output/cdc/*/*.csv; do
    echo "$file: $(wc -l < $file) lines"
done

# 计算平均行数
find output/cdc -name "*.csv" -exec wc -l {} \; | awk '{sum+=$1; count++} END {print sum/count}'
```

### 3. 监控滚动频率

```bash
# 查看文件创建时间
ls -lt output/cdc/*/*.csv | head -20

# 计算文件创建间隔
stat -f "%Sm %N" -t "%Y-%m-%d %H:%M:%S" output/cdc/*/*.csv
```

## 常见问题

### Q1: 为什么文件大小不精确？

A: 文件大小检查是近似的，因为：
- 检查发生在写入数据后
- 缓冲区可能还有未刷新的数据
- 文件系统块大小影响

### Q2: 如何确保文件不会太小？

A: 设置合理的 `inactivityInterval`：
```java
.withInactivityInterval(Duration.ofMinutes(5))  // 至少等待5分钟
```

### Q3: 如何确保文件不会太大？

A: 设置合理的 `maxPartSize`：
```java
.withMaxPartSize(128 * 1024 * 1024L)  // 最大128MB
```

### Q4: OnCheckpointRollingPolicy vs DefaultRollingPolicy？

| 策略 | 优点 | 缺点 | 适用场景 |
|------|------|------|---------|
| OnCheckpointRollingPolicy | 简单，数据一致性好 | 无法控制文件大小 | 数据一致性要求高 |
| DefaultRollingPolicy | 灵活，可控制大小 | 配置复杂 | 需要控制文件大小 |

### Q5: 如何平衡文件大小和数据延迟？

```
小文件（< 10MB）：
  ✓ 低延迟（数据快速可见）
  ✗ 文件数量多
  ✗ 元数据开销大

大文件（> 100MB）：
  ✓ 文件数量少
  ✓ 元数据开销小
  ✗ 高延迟（数据需等待文件关闭）

推荐：50-128MB
  ✓ 平衡延迟和文件数量
  ✓ 适合大多数场景
```

## 总结

| 配置项 | 默认值 | 推荐范围 | 说明 |
|--------|--------|---------|------|
| maxFileSizeMB | 128 | 50-256 | 文件大小限制 |
| rolloverMinutes | 15 | 10-30 | 最大滚动间隔 |
| inactivityMinutes | 5 | 2-10 | 空闲滚动时间 |

**关键点**：
- 使用 `DefaultRollingPolicy` 替代 `OnCheckpointRollingPolicy`
- 根据数据量和业务需求调整参数
- 监控文件大小和行数分布
- 定期优化配置
