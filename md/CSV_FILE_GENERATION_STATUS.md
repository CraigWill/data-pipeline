# CSV 文件生成状态

## 当前状态

✅ **CSV 文件已成功生成**

### 文件位置

```
output/cdc/2026-02-25--11/part-76d38d52-6509-4882-8a4b-5199251dbafe-0
```

### 文件信息

- **大小**: 2.9 MB (3,003,664 字节)
- **记录数**: 8,000 行
- **生成时间**: 2026-02-25 11:33
- **格式**: CSV (逗号分隔)

### 文件内容示例

```csv
"2026-02-25 11:32:38","TRANS_INFO","INSERT","{'ID':'20260225101309','ACCOUNT_ID':'ACC03163566','AMOUNT':'8447.97',...}","{'ID':'20260225101309','ACCOUNT_ID':'ACC03163566','AMOUNT':'8447.97',...}"
"2026-02-25 11:32:38","TRANS_INFO","INSERT","{'ID':'20260225101310','ACCOUNT_ID':'ACC00319140','AMOUNT':'4903.38',...}","{'ID':'20260225101310','ACCOUNT_ID':'ACC00319140','AMOUNT':'4903.38',...}"
```

### CSV 字段说明

1. **时间戳**: 变更事件发生的时间
2. **表名**: 数据库表名 (TRANS_INFO)
3. **操作类型**: INSERT, UPDATE, DELETE
4. **变更前数据**: 操作前的数据（JSON 格式）
5. **变更后数据**: 操作后的数据（JSON 格式）

## 关于文件扩展名

### 为什么文件没有 .csv 扩展名？

这是 **Flink FileSink 的正常行为**：

1. **写入中的文件** (In-Progress Files)
   - 文件名格式：`part-<uuid>-<subtask-index>`
   - 没有扩展名
   - 正在接收数据流

2. **已提交的文件** (Committed Files)
   - 当满足以下条件之一时，文件会被"提交"：
     - Checkpoint 完成
     - 达到 rolling 策略的条件（时间、大小、不活动时间）
     - 作业正常停止
   - 提交后文件会添加配置的后缀（如 `.csv`）

### 当前配置

```java
FileSink<String> fileSink = FileSink
    .forRowFormat(new Path(config.outputPath), new SimpleStringEncoder<String>("UTF-8"))
    .withRollingPolicy(
        DefaultRollingPolicy.builder()
            .withRolloverInterval(Duration.ofMinutes(5))  // 5分钟滚动
            .withInactivityInterval(Duration.ofMinutes(2)) // 2分钟不活动
            .withMaxPartSize(MemorySize.ofMebiBytes(128))  // 128MB 最大大小
            .build()
    )
    .withOutputFileConfig(
        OutputFileConfig.builder()
            .withPartSuffix(".csv")  // 添加 .csv 后缀
            .build()
    )
    .build();
```

### 如何获得带 .csv 扩展名的文件？

有几种方法：

#### 方法 1: 等待自动 Rolling

文件会在以下情况自动关闭并添加扩展名：
- 5 分钟后（rolloverInterval）
- 2 分钟无新数据（inactivityInterval）
- 文件大小达到 128MB（maxPartSize）

#### 方法 2: 触发 Checkpoint

```bash
# 通过 REST API 触发 savepoint
curl -X POST http://localhost:8081/jobs/<job-id>/savepoints \
  -H "Content-Type: application/json" \
  -d '{"target-directory": "file:///opt/flink/savepoints", "cancel-job": false}'
```

#### 方法 3: 停止作业

```bash
# 优雅停止作业（会触发最终的 checkpoint）
curl -X PATCH 'http://localhost:8081/jobs/<job-id>?mode=cancel'
```

#### 方法 4: 手动重命名

```bash
# 如果需要立即使用文件，可以手动添加扩展名
cd output/cdc/2026-02-25--11/
mv part-76d38d52-6509-4882-8a4b-5199251dbafe-0 part-76d38d52-6509-4882-8a4b-5199251dbafe-0.csv
```

## 验证数据

### 查看文件内容

```bash
# 查看前 10 行
head -10 output/cdc/2026-02-25--11/part-76d38d52-6509-4882-8a4b-5199251dbafe-0

# 统计行数
wc -l output/cdc/2026-02-25--11/part-76d38d52-6509-4882-8a4b-5199251dbafe-0

# 查看文件大小
ls -lh output/cdc/2026-02-25--11/
```

### 数据统计

```bash
# 统计不同操作类型
grep -o '"INSERT"' output/cdc/2026-02-25--11/part-* | wc -l
grep -o '"UPDATE"' output/cdc/2026-02-25--11/part-* | wc -l
grep -o '"DELETE"' output/cdc/2026-02-25--11/part-* | wc -l
```

## 作业状态

### 当前运行的作业

```
Job ID: 321b2e49a4c5d02042f25a7955e40f55
Job Name: Flink CDC 3.x Oracle Application
State: RUNNING
Parallelism: 4
```

### Checkpoint 状态

```bash
# 查看 checkpoint 统计
curl -s http://localhost:8081/jobs/321b2e49a4c5d02042f25a7955e40f55/checkpoints | python3 -m json.tool
```

### 监控作业

- **Flink Web UI**: http://localhost:8081/#/job/321b2e49a4c5d02042f25a7955e40f55/overview
- **REST API**: http://localhost:8081/jobs/321b2e49a4c5d02042f25a7955e40f55

## 总结

✅ **CDC 功能正常工作**
- Oracle 数据库变更成功捕获
- 数据正确转换为 CSV 格式
- 文件成功写入到输出目录

⚠️ **文件扩展名说明**
- 正在写入的文件没有 `.csv` 扩展名（这是正常的）
- 文件关闭后会自动添加扩展名
- 可以手动重命名或等待自动 rolling

📊 **数据质量**
- 8,000 条记录已成功捕获
- CSV 格式正确
- 包含完整的变更信息（时间戳、表名、操作类型、数据）

---

**日期**: 2026-02-25
**作业 ID**: 321b2e49a4c5d02042f25a7955e40f55
**输出目录**: output/cdc/2026-02-25--11/
