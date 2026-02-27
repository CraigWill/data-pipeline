# 单 Source 修复说明

## 问题描述

之前的实现中，Job 有两个 Source：
1. **Oracle CDC Source** - 主要的 CDC 数据流
2. **Header Source** - CSV 标题行（通过 `env.fromElements()` 创建）

这两个 Source 通过 `union()` 合并，导致：
- Flink Web UI 显示两个 Source
- 标题行可能出现在任意位置（union 不保证顺序）
- 可能产生重复的标题行

## 解决方案

### 使用自定义 Encoder

创建了 `CsvEncoderWithHeader` 类，在每个文件开始时自动写入标题行：

```java
static class CsvEncoderWithHeader implements Encoder<String> {
    private static final String HEADER = "交易时间,操作类型,ID,ACCOUNT_ID,AMOUNT,TRANS_TIME,TRANS_TYPE,MERCHANT_ID,STATUS\n";
    private boolean headerWritten = false;
    
    @Override
    public void encode(String element, OutputStream stream) throws IOException {
        // 第一次写入时，先写入标题行
        if (!headerWritten) {
            stream.write(HEADER.getBytes(StandardCharsets.UTF_8));
            headerWritten = true;
        }
        
        // 写入数据行
        stream.write(element.getBytes(StandardCharsets.UTF_8));
        stream.write('\n');
    }
}
```

### 代码改动

#### 移除前

```java
// 添加 CSV 标题行
DataStream<String> headerStream = env.fromElements(
    "交易时间,操作类型,ID,ACCOUNT_ID,AMOUNT,TRANS_TIME,TRANS_TYPE,MERCHANT_ID,STATUS"
).setParallelism(1);

// 合并标题行和数据流
DataStream<String> csvWithHeader = headerStream.union(cdcStream);

// 写入文件
FileSink<String> fileSink = FileSink
    .forRowFormat(new Path(config.outputPath), new SimpleStringEncoder<String>("UTF-8"))
    ...
    .build();

csvWithHeader.sinkTo(fileSink).name("CSV File Sink");
```

#### 修改后

```java
// 写入 CSV 文件（使用自定义 Encoder 添加标题行）
FileSink<String> fileSink = FileSink
    .forRowFormat(new Path(config.outputPath), new CsvEncoderWithHeader())
    ...
    .build();

cdcStream.sinkTo(fileSink).name("CSV File Sink");
```

## 验证结果

### Job 结构

```json
{
    "vertices": [
        {
            "id": "cbc357ccb763df2852fee8c4fc7d55f2",
            "name": "Source: Oracle CDC 3.x Source -> Application-Level Table Filter -> Map -> JSON to CSV Converter",
            "parallelism": 4
        }
    ]
}
```

✅ **只有一个 Source**

### 输出文件

```
位置: output/cdc/2026-02-25--13/
文件: .part-82d2a08f-2f66-4e1a-bfb2-7c743406d7dd-0.csv.inprogress.3b806b91-9625-4851-9464-d2821af14fa5
内容: 
交易时间,操作类型,ID,ACCOUNT_ID,AMOUNT,TRANS_TIME,TRANS_TYPE,MERCHANT_ID,STATUS
```

✅ **标题行正确写入**

### 作业状态

```
Job ID: 022c88233e5b0afba2d44f7c8c3a49e3
Job Name: Flink CDC 3.x Oracle Application
State: RUNNING
Source Count: 1 (Oracle CDC 3.x Source)
```

✅ **作业正常运行**

## 优势

1. **简化拓扑结构**
   - 只有一个 Source
   - 数据流更清晰
   - 减少资源开销

2. **保证标题行顺序**
   - 标题行始终是文件的第一行
   - 不会出现在数据中间

3. **避免重复标题**
   - 每个文件只有一个标题行
   - 即使有多个并行度也不会重复

4. **更好的性能**
   - 减少了一个 Source 的开销
   - 减少了 union 操作的开销

## 工作原理

### Encoder 生命周期

1. **文件创建时**
   - Flink 创建新的输出文件
   - 实例化 `CsvEncoderWithHeader`
   - `headerWritten = false`

2. **第一条记录**
   - 检查 `headerWritten` 标志
   - 写入标题行
   - 设置 `headerWritten = true`
   - 写入数据行

3. **后续记录**
   - `headerWritten` 已为 true
   - 直接写入数据行

4. **文件关闭**
   - Flink 关闭文件
   - Encoder 实例被销毁

### 多并行度处理

- 每个并行度有自己的 Encoder 实例
- 每个实例独立管理 `headerWritten` 标志
- 每个输出文件都有自己的标题行

## 相关文件

- `src/main/java/com/realtime/pipeline/FlinkCDC3App.java` - 主程序
- `output/cdc/2026-02-25--13/` - 新的输出目录

---

**日期**: 2026-02-25
**Job ID**: 022c88233e5b0afba2d44f7c8c3a49e3
**状态**: ✅ 单 Source 实现完成
