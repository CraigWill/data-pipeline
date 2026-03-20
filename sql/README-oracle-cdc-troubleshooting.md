# Oracle CDC 故障排查经验

## 问题1：表变更被 Debezium 报 "unsupported operation"

### 现象
Flink 作业正常运行，其他表有 CDC 输出，但某张表（如 CLM_HISTORY）没有任何输出文件。
TaskManager 日志中出现：
```
WARN  io.debezium.connector.oracle.logminer.processor.AbstractLogMinerEventProcessor
An unsupported operation detected for table 'HELOWIN.FINANCE_USER.CLM_HISTORY'
in transaction xxx with SCN yyy on redo thread 1.
```

### 根本原因
表或索引设置了 **NOLOGGING** 属性。NOLOGGING 模式下的 DML 不写完整 redo log，
Oracle LogMiner 无法解析，Debezium 将其标记为 `UNSUPPORTED` 操作并丢弃。

### 排查命令
```sql
-- 以 sysdba 身份检查
SELECT table_name, logging FROM dba_tables
WHERE owner = 'FINANCE_USER' AND table_name = 'CLM_HISTORY';

SELECT index_name, logging FROM dba_indexes
WHERE table_owner = 'FINANCE_USER' AND table_name = 'CLM_HISTORY';
```
如果 `logging = NO`，即为 NOLOGGING，需要修复。

### 修复命令
```sql
-- 以 sysdba 身份执行
ALTER TABLE FINANCE_USER.CLM_HISTORY LOGGING;
ALTER INDEX FINANCE_USER.PK_CLM_HISTORY LOGGING;
```
修复后无需重启 Flink 作业，Debezium 会自动识别并开始捕获该表。

### 验证
执行一条 UPDATE 触发变更，等待约 10-20 秒，日志中应出现：
```
Table 'HELOWIN.FINANCE_USER.CLM_HISTORY' is new and will now be captured.
```
并在 `output/cdc/` 目录下生成对应的 CSV 文件。

---

## 问题2：Oracle Archivelog 模式被关闭

### 现象
```
ORA-01325: archive log mode must be enabled to build into the logstream
```

### 修复
```sql
-- 以 sysdba 连接
SHUTDOWN IMMEDIATE;
STARTUP MOUNT;
ALTER DATABASE ARCHIVELOG;
ALTER DATABASE OPEN;
-- 验证
SELECT log_mode FROM v$database;  -- 应为 ARCHIVELOG
```

---

## 问题3：HA completed checkpoint store 初始化失败

### 现象
```
Failed to initialize high-availability completed checkpoint store
```
ZooKeeper 中有 checkpoint 元数据，但物理文件不存在。

### 修复
```bash
# 清除 ZooKeeper 中的旧作业元数据
docker exec zookeeper zkCli.sh -server localhost:2181
deleteall /flink/realtime-pipeline/jobs
```
然后重启 jobmanager、taskmanager、monitor-backend，从前端重新提交作业。

---

## 问题4：Flink 重启后重复提交作业

### 现象
每次重启 monitor-backend 都会多提交一个同名 Flink 作业。

### 根本原因
`getFlinkRunningJobMap()` 使用了 `job.get("id")`，但 Flink REST API `/jobs/overview`
返回的字段是 `"jid"` 而不是 `"id"`，导致 map 始终为空，每次都误判为需要重新提交。

### 修复
```java
String id = (String) job.getOrDefault("jid", job.get("id"));
```

---

## 问题5：TaskManager 内存配置错误

### 现象
```
Config uses deprecated configuration key 'taskmanager.network.memory.fraction'
```
或 TaskManager 启动失败，内存溢出。

### 修复
- 正确 key：`taskmanager.memory.network.fraction`（不是 `taskmanager.network.memory.fraction`）
- process size 要大于所有子项之和，避免 managed memory 超出 total

---

## 通用检查清单

新增 CDC 监听表时，确认以下几点：

```sql
-- 1. 表存在且可访问
SELECT COUNT(*) FROM FINANCE_USER.<TABLE_NAME>;

-- 2. 表是 LOGGING 模式（非 NOLOGGING）
SELECT logging FROM dba_tables WHERE owner='FINANCE_USER' AND table_name='<TABLE_NAME>';

-- 3. 索引是 LOGGING 模式
SELECT index_name, logging FROM dba_indexes WHERE table_owner='FINANCE_USER' AND table_name='<TABLE_NAME>';

-- 4. 数据库级 supplemental logging 已开启
SELECT supplemental_log_data_all FROM v$database;  -- 应为 YES

-- 5. Archivelog 模式
SELECT log_mode FROM v$database;  -- 应为 ARCHIVELOG
```
