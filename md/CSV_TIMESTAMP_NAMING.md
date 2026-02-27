# CSV 文件时间戳命名 - 完成

## 修改内容

修改了 CSV 文件的命名方式，使用时间戳格式来命名文件。

## 实现细节

### 1. 文件命名格式

文件名格式：`cdc_events_yyyyMMdd_HHmmss-<uuid>-<partition>.csv`

示例：`cdc_events_20260225_141049-64aafb43-3cd7-4be2-a8a2-ee793748242f-0.csv`

### 2. 代码修改

**文件**: `src/main/java/com/realtime/pipeline/FlinkCDC3App.java`

```java
// 生成时间戳格式的文件名前缀
String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

FileSink<String> fileSink = FileSink
    .forRowFormat(new Path(config.outputPath), new CsvEncoderWithHeader())
    .withRollingPolicy(
        DefaultRollingPolicy.builder()
            .withRolloverInterval(Duration.ofMinutes(5))
            .withInactivityInterval(Duration.ofMinutes(2))
            .withMaxPartSize(MemorySize.ofMebiBytes(128))
            .build()
    )
    .withOutputFileConfig(
        OutputFileConfig.builder()
            .withPartPrefix("cdc_events_" + timestamp)  // 添加时间戳前缀
            .withPartSuffix(".csv")
            .build()
    )
    .build();
```

### 3. 时间戳说明

- 时间戳在作业启动时生成（一次性）
- 格式：`yyyyMMdd_HHmmss`（年月日_时分秒）
- 同一个作业运行期间，所有文件使用相同的时间戳前缀
- 不同的文件通过 UUID 和分区号区分

### 4. 验证结果

✅ 成功生成带时间戳的 CSV 文件
✅ 文件名示例：`cdc_events_20260225_141049-64aafb43-3cd7-4be2-a8a2-ee793748242f-0.csv`
✅ 文件内容正确：
   - 第一行：标题行
   - 从第二行开始：数据行
   - 成功捕获 CDC 事件（UPDATE 操作）

### 5. 测试数据

```csv
交易时间,操作类型,ID,ACCOUNT_ID,AMOUNT,TRANS_TIME,TRANS_TYPE,MERCHANT_ID,STATUS
2024-06-29 10:33:42,UPDATE,999999,ACC06157155,777.77,1719628422000,DEPOSIT,MER048266,COMPLETED
2024-06-29 10:33:42,UPDATE,999999,ACC06157155,111.11,1719628422000,DEPOSIT,MER048266,COMPLETED
2024-06-29 10:33:42,UPDATE,999999,ACC06157155,222.22,1719628422000,DEPOSIT,MER048266,COMPLETED
2024-06-29 10:33:42,UPDATE,999999,ACC06157155,333.33,1719628422000,DEPOSIT,MER048266,COMPLETED
```

## 当前作业状态

- Job ID: `f5c03d23526ebf545fc0d74f80b3f482`
- Job State: RUNNING
- Job Name: Flink CDC 3.x Oracle Application
- 输出路径: `./output/cdc/`

## 相关文件

- `src/main/java/com/realtime/pipeline/FlinkCDC3App.java` - 主应用程序
- `start-flink-cdc-job.sh` - 启动脚本（已更新 JAR 文件名）
- `output/cdc/2026-02-25--14/` - 输出目录

## 注意事项

1. 文件在 checkpoint 完成或 rolling policy 触发时才会最终写入（移除 `.inprogress` 后缀）
2. Checkpoint 间隔配置为 3 分钟
3. Rolling policy 配置：
   - 每 5 分钟滚动一次
   - 不活动 2 分钟后滚动
   - 文件大小达到 128MB 时滚动

## 下一步

如需修改时间戳格式或文件命名规则，可以调整 `DateTimeFormatter.ofPattern()` 的格式字符串。

常用格式：
- `yyyyMMdd_HHmmss` - 20260225_141049
- `yyyy-MM-dd_HH-mm-ss` - 2026-02-25_14-10-49
- `yyyyMMddHHmmss` - 20260225141049
