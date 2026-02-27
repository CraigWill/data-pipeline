# CDC 交易时间使用当前时间 - 完成

## 修改内容

将 CSV 文件中的"交易时间"字段从数据库的 TRANS_TIME 字段改为使用当前时间（CDC 事件捕获时间）。

## 实现细节

### 1. 代码修改

**文件**: `src/main/java/com/realtime/pipeline/FlinkCDC3App.java`

**修改前**:
```java
// 提取交易时间（TRANS_TIME 字段）
String transTime = dataMap.getOrDefault("TRANS_TIME", "");
if (!transTime.isEmpty() && transTime.matches("\\d+")) {
    // 转换时间戳为可读格式
    try {
        long timestamp = Long.parseLong(transTime);
        transTime = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(timestamp),
            java.time.ZoneId.systemDefault()
        ).format(CSV_TIMESTAMP);
    } catch (Exception e) {
        LOG.warn("时间戳转换失败: {}", transTime);
    }
}

// 构建 CSV 行
csv.append(escapeCsvValue(transTime)).append(",");
```

**修改后**:
```java
// 使用当前时间作为交易时间（CDC 事件捕获时间）
String currentTime = LocalDateTime.now().format(CSV_TIMESTAMP);

// 构建 CSV 行
csv.append(escapeCsvValue(currentTime)).append(",");
```

### 2. 时间格式

- 格式：`yyyy-MM-dd HH:mm:ss`
- 示例：`2026-02-25 14:20:18`
- 时区：系统默认时区

### 3. 验证结果

✅ 成功使用当前时间作为交易时间
✅ 每条 CDC 事件的交易时间反映了事件被捕获的实际时间

**测试数据**:
```csv
交易时间,操作类型,ID,ACCOUNT_ID,AMOUNT,TRANS_TIME,TRANS_TYPE,MERCHANT_ID,STATUS
2026-02-25 14:20:18,UPDATE,999999,ACC06157155,777.77,1719628422000,DEPOSIT,MER048266,COMPLETED
2026-02-25 14:20:36,UPDATE,999999,ACC06157155,555.55,1719628422000,DEPOSIT,MER048266,COMPLETED
2026-02-25 14:20:36,UPDATE,999999,ACC06157155,666.66,1719628422000,DEPOSIT,MER048266,COMPLETED
```

**说明**:
- 第一列"交易时间"：CDC 事件捕获的当前时间（2026-02-25 14:20:xx）
- 第六列"TRANS_TIME"：数据库中的原始时间戳字段（1719628422000）

### 4. 优势

1. **实时性**: 反映了数据变更被捕获的实际时间
2. **准确性**: 不依赖数据库字段，避免字段为空或格式错误的问题
3. **一致性**: 所有 CDC 事件使用统一的时间格式
4. **可追溯**: 可以准确追踪数据变更的捕获时间

## 当前作业状态

- Job ID: `8b484c7eae00ecbfa43672fe5aa2c62e`
- Job State: RUNNING
- Job Name: Flink CDC 3.x Oracle Application
- 输出路径: `./output/cdc/`
- 文件名格式: `cdc_events_yyyyMMdd_HHmmss-<uuid>-<partition>.csv`

## CSV 字段说明

| 字段位置 | 字段名 | 说明 | 示例 |
|---------|--------|------|------|
| 1 | 交易时间 | CDC 事件捕获的当前时间 | 2026-02-25 14:20:18 |
| 2 | 操作类型 | INSERT/UPDATE/DELETE | UPDATE |
| 3 | ID | 记录 ID | 999999 |
| 4 | ACCOUNT_ID | 账户 ID | ACC06157155 |
| 5 | AMOUNT | 金额 | 777.77 |
| 6 | TRANS_TIME | 数据库原始时间戳 | 1719628422000 |
| 7 | TRANS_TYPE | 交易类型 | DEPOSIT |
| 8 | MERCHANT_ID | 商户 ID | MER048266 |
| 9 | STATUS | 状态 | COMPLETED |

## 相关文件

- `src/main/java/com/realtime/pipeline/FlinkCDC3App.java` - 主应用程序
- `CSV_TIMESTAMP_NAMING.md` - CSV 文件时间戳命名文档
- `output/cdc/2026-02-25--14/` - 输出目录

## 注意事项

1. "交易时间"字段现在表示 CDC 事件被 Flink 捕获的时间
2. 数据库中的原始时间戳仍然保留在 TRANS_TIME 字段中
3. 时间使用系统默认时区
4. 每条记录的交易时间可能略有不同，反映了处理的实时性
