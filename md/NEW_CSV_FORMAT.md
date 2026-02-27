# 新 CSV 格式说明

## 格式调整完成

✅ **CSV 格式已按要求调整**

### 新格式说明

#### 第一行：标题行

```csv
交易时间,操作类型,ID,ACCOUNT_ID,AMOUNT,TRANS_TIME,TRANS_TYPE,MERCHANT_ID,STATUS
```

#### 从第二行开始：数据行

每行包含以下字段，用逗号分隔：

1. **交易时间** - 从 TRANS_TIME 字段转换的可读时间格式 (yyyy-MM-dd HH:mm:ss)
2. **操作类型** - INSERT, UPDATE, DELETE
3. **ID** - 交易ID
4. **ACCOUNT_ID** - 账户ID
5. **AMOUNT** - 交易金额
6. **TRANS_TIME** - 原始时间戳
7. **TRANS_TYPE** - 交易类型 (DEPOSIT, TRANSFER, PAY, REFUND)
8. **MERCHANT_ID** - 商户ID
9. **STATUS** - 交易状态 (SUCCESS, FAIL)

### 示例输出

```csv
交易时间,操作类型,ID,ACCOUNT_ID,AMOUNT,TRANS_TIME,TRANS_TYPE,MERCHANT_ID,STATUS
2024-10-24 10:13:30,INSERT,20260225101309,ACC03163566,8447.97,1729760810000,DEPOSIT,MER008610,SUCCESS
2024-10-11 15:47:00,INSERT,20260225101310,ACC00319140,4903.38,1728640420000,PAY,MER002590,SUCCESS
2024-11-28 17:38:33,INSERT,20260225101311,ACC01534235,2436.59,1732852713000,REFUND,MER026860,SUCCESS
```

### 代码实现

#### 主要改动

1. **解析 JSON 数据**
   - 从 Debezium JSON 中提取 `after` 字段（INSERT/UPDATE）或 `before` 字段（DELETE）
   - 解析 JSON 对象为 Map<String, String>

2. **提取字段值**
   - 按照表结构提取各个字段
   - 处理时间戳转换

3. **添加标题行**
   - 使用 `env.fromElements()` 创建标题流
   - 使用 `union()` 合并标题流和数据流

4. **CSV 转义**
   - 如果值包含逗号、引号或换行符，用双引号包裹
   - 引号转义为双引号

#### 关键代码片段

```java
// 添加 CSV 标题行
DataStream<String> headerStream = env.fromElements(
    "交易时间,操作类型,ID,ACCOUNT_ID,AMOUNT,TRANS_TIME,TRANS_TYPE,MERCHANT_ID,STATUS"
).setParallelism(1);

// 合并标题行和数据流
DataStream<String> csvWithHeader = headerStream.union(cdcStream);

// 写入文件
csvWithHeader.sinkTo(fileSink).name("CSV File Sink");
```

### 当前状态

#### 作业信息

```
Job ID: 486b5d1d900fa43489614f9c17d97bbb
Job Name: Flink CDC 3.x Oracle Application
State: RUNNING
```

#### 输出文件

```
位置: output/cdc/2026-02-25--11/
文件: .part-4b0a6db6-c755-4e9f-b362-1758479a8edd-0.csv.inprogress.ce0dce50-9fee-4762-ac3b-36c5e03fb112
状态: 正在写入
内容: 已包含标题行
```

### 验证方法

#### 查看当前文件内容

```bash
# 在容器内查看
docker exec realtime-pipeline-taskmanager-1 cat '/opt/flink/output/cdc/2026-02-25--11/.part-4b0a6db6-c755-4e9f-b362-1758479a8edd-0.csv.inprogress.ce0dce50-9fee-4762-ac3b-36c5e03fb112'
```

#### 插入测试数据

由于使用了 `StartupOptions.latest()` 模式，需要插入新数据才能看到数据行：

```sql
-- 连接到 Oracle 数据库
sqlplus system/helowin@//host.docker.internal:1521/helowin

-- 插入测试数据
INSERT INTO FINANCE_USER.TRANS_INFO VALUES 
('TEST20260225001', 'ACC99999999', 1000.00, SYSTIMESTAMP, 'DEPOSIT', 'MER999999', 'SUCCESS');

INSERT INTO FINANCE_USER.TRANS_INFO VALUES 
('TEST20260225002', 'ACC88888888', 2000.00, SYSTIMESTAMP, 'TRANSFER', 'MER888888', 'SUCCESS');

COMMIT;
```

#### 等待并查看结果

```bash
# 等待 10 秒让 CDC 捕获变更
sleep 10

# 查看文件内容
docker exec realtime-pipeline-taskmanager-1 find /opt/flink/output/cdc/2026-02-25--11/ -type f -mmin -2 -exec cat {} \;
```

### 文件命名

- **写入中**: `.part-<uuid>-<index>.csv.inprogress.<uuid>`
- **已提交**: `part-<uuid>-<index>.csv`

文件在以下情况会被提交（关闭并重命名）：
- Checkpoint 完成
- 达到 rolling 策略条件（5分钟、2分钟不活动、128MB大小）
- 作业停止

### 注意事项

1. **标题行只出现一次**
   - 每个文件的第一行是标题
   - 使用 `setParallelism(1)` 确保标题只写入一次

2. **时间格式**
   - 交易时间：从 TRANS_TIME 时间戳转换为 `yyyy-MM-dd HH:mm:ss` 格式
   - 如果转换失败，保留原始值

3. **CSV 转义规则**
   - 包含逗号、引号、换行符的值会用双引号包裹
   - 引号转义为双引号（`"` → `""`）

4. **空值处理**
   - 空字段显示为空字符串
   - 不使用 NULL 或其他占位符

### 相关文件

- `src/main/java/com/realtime/pipeline/FlinkCDC3App.java` - 主程序
- `output/cdc/2026-02-25--11/` - 输出目录
- `quick-insert-test.sh` - 测试数据插入脚本

---

**日期**: 2026-02-25
**作业 ID**: 486b5d1d900fa43489614f9c17d97bbb
**状态**: ✅ 格式调整完成，等待数据验证
