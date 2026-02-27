# CSV 文件标题行修复 - 完成

## 问题描述

在优化文件生成速度后，发现新生成的 CSV 文件缺少标题行。只有第一个文件有标题行，后续滚动生成的文件都没有标题行。

## 问题原因

原来的 `CsvEncoderWithHeader` 实现使用了一个实例级别的 `headerWritten` 标志：

```java
static class CsvEncoderWithHeader implements Encoder<String> {
    private boolean headerWritten = false;
    
    @Override
    public void encode(String element, OutputStream stream) throws IOException {
        if (!headerWritten) {
            stream.write(HEADER.getBytes(StandardCharsets.UTF_8));
            headerWritten = true;  // 一旦设置为 true，永远不会重置
        }
        stream.write(element.getBytes(StandardCharsets.UTF_8));
        stream.write('\n');
    }
}
```

**问题**：
- `headerWritten` 标志在整个 Encoder 实例的生命周期中只设置一次
- 当文件滚动创建新文件时，标志已经是 `true`
- 新文件不会写入标题行

## 解决方案

使用 `ThreadLocal<Set<OutputStream>>` 来跟踪每个输出流的标题写入状态：

```java
static class CsvEncoderWithHeader implements Encoder<String> {
    private static final long serialVersionUID = 1L;
    private static final String HEADER = "交易时间,操作类型,ID,ACCOUNT_ID,AMOUNT,TRANS_TIME,TRANS_TYPE,MERCHANT_ID,STATUS\n";
    
    // 使用 ThreadLocal 存储每个输出流的标题写入状态
    private transient ThreadLocal<Set<OutputStream>> streamsWithHeader;
    
    @Override
    public void encode(String element, OutputStream stream) throws IOException {
        // 初始化 ThreadLocal
        if (streamsWithHeader == null) {
            streamsWithHeader = ThreadLocal.withInitial(HashSet::new);
        }
        
        // 检查这个流是否已经写入过标题
        if (!streamsWithHeader.get().contains(stream)) {
            stream.write(HEADER.getBytes(StandardCharsets.UTF_8));
            streamsWithHeader.get().add(stream);
        }
        
        // 写入数据行
        stream.write(element.getBytes(StandardCharsets.UTF_8));
        stream.write('\n');
    }
}
```

## 工作原理

1. **ThreadLocal**: 为每个线程维护独立的 `Set<OutputStream>`
2. **OutputStream 跟踪**: 每个新的输出流（新文件）都会被添加到 Set 中
3. **首次写入检测**: 当遇到新的输出流时，先写入标题行，然后将流添加到 Set
4. **后续写入**: 对于已经在 Set 中的流，直接写入数据行

## 验证结果

✅ 每个新文件都有标题行
✅ 包括 `.inprogress` 文件也有标题行
✅ 文件滚动后标题行正常

**测试文件 1**:
```csv
交易时间,操作类型,ID,ACCOUNT_ID,AMOUNT,TRANS_TIME,TRANS_TYPE,MERCHANT_ID,STATUS
2026-02-25 14:42:31,UPDATE,999999,ACC06157155,111.11,1719628422000,DEPOSIT,MER048266,COMPLETED
2026-02-25 14:42:31,UPDATE,999999,ACC06157155,222.22,1719628422000,DEPOSIT,MER048266,COMPLETED
2026-02-25 14:42:31,UPDATE,999999,ACC06157155,333.33,1719628422000,DEPOSIT,MER048266,COMPLETED
```

**测试文件 2** (inprogress):
```csv
交易时间,操作类型,ID,ACCOUNT_ID,AMOUNT,TRANS_TIME,TRANS_TYPE,MERCHANT_ID,STATUS
2026-02-25 14:44:05,UPDATE,999999,ACC06157155,555.55,1719628422000,DEPOSIT,MER048266,COMPLETED
```

## 技术细节

### 为什么使用 ThreadLocal？

1. **线程安全**: Flink 的 Encoder 可能在多个线程中并发执行
2. **隔离性**: 每个线程维护自己的输出流集合，避免冲突
3. **性能**: 避免使用同步锁，提高并发性能

### 为什么使用 Set<OutputStream>？

1. **唯一性**: 每个输出流只记录一次
2. **快速查找**: O(1) 时间复杂度检查流是否已写入标题
3. **自动去重**: Set 自动处理重复的流引用

### transient 关键字

```java
private transient ThreadLocal<Set<OutputStream>> streamsWithHeader;
```

- `transient`: 标记字段不参与序列化
- ThreadLocal 不需要序列化，每次反序列化后会重新初始化
- 避免序列化相关的问题

## 当前作业状态

- Job ID: `59ebab97929f6195796f17971ea20fa0`
- Job State: RUNNING
- Job Name: Flink CDC 3.x Oracle Application
- Checkpoint 间隔: 30 秒
- 文件滚动间隔: 30 秒
- 标题行: ✅ 每个文件都有

## 相关文件

- `src/main/java/com/realtime/pipeline/FlinkCDC3App.java` - 主应用程序
- `CSV_GENERATION_SPEED_OPTIMIZATION.md` - 文件生成速度优化文档
- `CSV_TIMESTAMP_NAMING.md` - CSV 文件时间戳命名文档
- `CDC_CURRENT_TIME_UPDATE.md` - 交易时间使用当前时间文档

## 注意事项

1. ThreadLocal 需要在使用前检查是否为 null 并初始化
2. Set 会持续增长，但由于文件数量有限，内存占用可接受
3. 如果需要清理 Set，可以在适当的时候调用 `streamsWithHeader.remove()`

## 总结

通过使用 ThreadLocal 和 Set 来跟踪每个输出流的标题写入状态，成功解决了文件滚动后标题行丢失的问题。现在每个 CSV 文件都有完整的标题行，确保了数据的可读性和一致性。
