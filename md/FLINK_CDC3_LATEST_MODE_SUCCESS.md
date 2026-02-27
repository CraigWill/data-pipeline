# Flink CDC 3.x Latest 模式部署成功

## 部署状态: ✅ 成功

Flink CDC 3.x 已成功配置为 **latest 模式**，通过 Oracle redo log (LogMiner) 捕获数据变更，跳过批处理 snapshot 阶段。

## 配置信息

### Flink CDC 版本
- **Flink CDC**: 3.2.1
- **Flink**: 1.18.0
- **启动模式**: `StartupOptions.latest()` - 只通过 redo log 捕获新变更

### Oracle 数据库配置
- **版本**: Oracle 11g
- **归档模式**: ARCHIVELOG ✅ (已启用)
- **补充日志**: IMPLICIT ✅ (已启用)
- **LogMiner 策略**: online_catalog
- **连续挖掘**: true

### 作业配置
- **作业 ID**: 384db2c6d23d19c74fbef7bd9075eab4
- **状态**: RUNNING
- **并行度**: 4
- **Checkpoint 间隔**: 3 分钟
- **输出格式**: CSV
- **输出路径**: `./output/cdc/`

## 关键修改

### 1. 启用 Oracle 归档日志模式

```sql
-- 关闭数据库
SHUTDOWN IMMEDIATE;

-- 启动到 MOUNT 状态
STARTUP MOUNT;

-- 启用归档日志
ALTER DATABASE ARCHIVELOG;

-- 打开数据库
ALTER DATABASE OPEN;

-- 启用补充日志
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;

-- 为表启用补充日志
ALTER TABLE FINANCE_USER.TRANS_INFO ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;
```

### 2. 修改 Flink CDC 启动模式

**文件**: `src/main/java/com/realtime/pipeline/FlinkCDC3App.java`

```java
// 修改前 (initial 模式 - 需要 snapshot)
.startupOptions(StartupOptions.initial())

// 修改后 (latest 模式 - 只通过 redo log)
.startupOptions(StartupOptions.latest())
```

### 3. Debezium 配置

```java
Properties debeziumProps = new Properties();
debeziumProps.setProperty("log.mining.strategy", "online_catalog");
debeziumProps.setProperty("log.mining.continuous.mine", "true");
debeziumProps.setProperty("decimal.handling.mode", "string");
debeziumProps.setProperty("time.precision.mode", "adaptive");
```

## 工作原理

### Latest 模式 vs Initial 模式

| 特性 | Latest 模式 | Initial 模式 |
|------|------------|--------------|
| Snapshot | ❌ 跳过 | ✅ 需要 |
| 历史数据 | ❌ 不捕获 | ✅ 捕获 |
| 启动时间 | ⚡ 立即 | ⏱️ 取决于表大小 |
| 数据来源 | Redo Log only | Snapshot + Redo Log |
| 适用场景 | 只关心新变更 | 需要完整历史 |

### CDC 数据流

```
Oracle 数据库
    ↓ (DML 操作: INSERT/UPDATE/DELETE)
Redo Log (归档日志)
    ↓ (LogMiner 挖掘)
Flink CDC 3.x Source
    ↓ (JSON 格式)
Application-Level Filter (过滤表)
    ↓
JSON to CSV Converter
    ↓
CSV File Sink
    ↓
./output/cdc/*.csv
```

## 验证步骤

### 1. 检查作业状态

```bash
curl -s http://localhost:8081/jobs/384db2c6d23d19c74fbef7bd9075eab4 | grep state
```

预期输出: `"state":"RUNNING"`

### 2. 插入测试数据

```bash
docker exec oracle11g bash -c "source /home/oracle/.bash_profile && sqlplus system/helowin@helowin <<EOF
INSERT INTO FINANCE_USER.TRANS_INFO (ID, ACCOUNT_ID, AMOUNT, TRANS_TIME, TRANS_TYPE, MERCHANT_ID, STATUS) 
VALUES (123456, 'TEST_CDC', 99999.99, SYSDATE, 'PAYMENT', 'MERCHANT_TEST', 'SUCCESS');
COMMIT;
EXIT;
EOF"
```

### 3. 检查输出文件

```bash
# 等待几秒让 CDC 处理
sleep 10

# 查看最新的 CSV 文件
ls -lht ./output/cdc/*.csv | head -3

# 查看文件内容
tail -5 ./output/cdc/*.csv
```

### 4. 监控指标

```bash
# 检查输出记录数
curl -s "http://localhost:8081/jobs/384db2c6d23d19c74fbef7bd9075eab4/vertices/cbc357ccb763df2852fee8c4fc7d55f2/metrics?get=0.Source__Oracle_CDC_3_x_Source.numRecordsOut"
```

## 优势

### 1. 无需等待 Snapshot
- 对于大表（如 21 亿+记录），snapshot 可能需要数小时甚至数天
- Latest 模式立即开始捕获新变更，无需等待

### 2. 真正的 CDC
- 基于 Oracle LogMiner，捕获所有 DML 操作
- 支持 INSERT, UPDATE, DELETE
- 低延迟（秒级）

### 3. 高性能
- 并行读取 redo log
- 增量处理，无需扫描全表
- 资源消耗低

### 4. 可靠性
- Flink checkpoint 保证 exactly-once 语义
- 自动故障恢复
- 状态持久化

## 限制

### 1. 不捕获历史数据
- Latest 模式只捕获作业启动后的新变更
- 如果需要历史数据，考虑：
  - 使用 Initial 模式（需要等待 snapshot）
  - 单独运行批处理作业导出历史数据
  - 使用时间范围过滤的 snapshot

### 2. 依赖归档日志
- 必须启用 Oracle ARCHIVELOG 模式
- 需要足够的归档日志存储空间
- 归档日志保留时间影响可恢复性

### 3. 表级补充日志
- 需要为每个监控的表启用补充日志
- 会增加 redo log 大小（约 10-20%）

## 故障排查

### 问题 1: 作业运行但无输出

**可能原因**:
1. Oracle 归档日志未启用
2. 补充日志未配置
3. 表过滤配置错误
4. 没有新的数据变更

**解决方案**:
```bash
# 检查归档模式
docker exec oracle11g bash -c "source /home/oracle/.bash_profile && sqlplus -s system/helowin@helowin <<EOF
SELECT LOG_MODE FROM V\\\$DATABASE;
EXIT;
EOF"

# 检查补充日志
docker exec oracle11g bash -c "source /home/oracle/.bash_profile && sqlplus -s system/helowin@helowin <<EOF
SELECT SUPPLEMENTAL_LOG_DATA_MIN FROM V\\\$DATABASE;
EXIT;
EOF"

# 插入测试数据
docker exec oracle11g bash -c "source /home/oracle/.bash_profile && sqlplus system/helowin@helowin <<EOF
INSERT INTO FINANCE_USER.TRANS_INFO (ID, ACCOUNT_ID, AMOUNT, TRANS_TIME, TRANS_TYPE, MERCHANT_ID, STATUS) 
VALUES (999999, 'TEST', 100.00, SYSDATE, 'TEST', 'TEST', 'SUCCESS');
COMMIT;
EXIT;
EOF"
```

### 问题 2: LogMiner 错误

**可能原因**:
- 归档日志文件损坏
- 磁盘空间不足
- 权限问题

**解决方案**:
```bash
# 查看 TaskManager 日志
docker logs realtime-pipeline-taskmanager-1 --tail 100 | grep -i "logminer\|error"

# 检查 Oracle 告警日志
docker exec oracle11g tail -100 /u01/app/oracle/diag/rdbms/helowin/helowin/trace/alert_helowin.log
```

### 问题 3: 性能问题

**可能原因**:
- 并行度不足
- Checkpoint 间隔太短
- 网络延迟

**解决方案**:
```bash
# 调整并行度（修改 .env 文件）
PARALLELISM_DEFAULT=8

# 调整 Checkpoint 间隔
CHECKPOINT_INTERVAL=600000  # 10 分钟

# 重新提交作业
mvn clean package -DskipTests
docker exec flink-jobmanager flink run -d -c com.realtime.pipeline.FlinkCDC3App /opt/flink/usrlib/realtime-data-pipeline-1.0.0-SNAPSHOT.jar
```

## 相关文件

- **应用代码**: `src/main/java/com/realtime/pipeline/FlinkCDC3App.java`
- **配置文件**: `.env`
- **POM 文件**: `pom.xml`
- **状态检查脚本**: `check-flink-cdc3-status.sh`
- **切换脚本**: `switch-to-latest-mode.sh`
- **状态文档**: `FLINK_CDC_STATUS.md`

## 下一步

### 1. 生产环境部署

- 配置高可用 (HA) 模式
- 使用外部 checkpoint 存储（HDFS/S3）
- 配置监控和告警
- 设置日志轮转

### 2. 性能优化

- 根据数据量调整并行度
- 优化 Checkpoint 配置
- 使用 RocksDB 状态后端（大状态场景）
- 配置网络缓冲区

### 3. 数据质量

- 添加数据验证逻辑
- 实现死信队列 (DLQ)
- 配置数据一致性检查
- 监控数据延迟

## 总结

Flink CDC 3.x 的 latest 模式成功解决了大表 snapshot 性能问题：

✅ 通过 Oracle redo log (LogMiner) 实时捕获数据变更  
✅ 跳过批处理 snapshot 阶段，立即开始工作  
✅ 支持 INSERT, UPDATE, DELETE 操作  
✅ 低延迟、高性能、高可靠性  
✅ 适合只关心增量变更的实时数据管道场景  

对于需要历史数据的场景，建议使用分阶段处理策略：
1. 使用 latest 模式捕获实时变更
2. 单独运行批处理作业处理历史数据
3. 或使用带时间范围过滤的 snapshot 模式
