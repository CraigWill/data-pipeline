# CDC 事件捕获问题修复指南

## 🔍 问题诊断

### 问题 1: LOG_MINING_FLUSH 表结构错误

**错误信息：**
```
oracle.jdbc.OracleDatabaseException: ORA-00904: "LAST_SCN": invalid identifier
```

**原因：** 手动创建的 `LOG_MINING_FLUSH` 表结构不正确。

**错误的表结构：**
```sql
CREATE TABLE log_mining_flush (
    scn NUMBER(19,0) NOT NULL,
    PRIMARY KEY (scn)
);
```

**正确的表结构（Debezium 期望）：**
```sql
CREATE TABLE log_mining_flush (
    last_scn NUMBER(19,0) DEFAULT 0 NOT NULL
);
INSERT INTO log_mining_flush VALUES (0);
```

### 问题 2: 数据源主机配置错误

**问题：** 数据源配置使用 `host.docker.internal`，在某些容器环境中无法正确解析。

**错误配置：**
```
host: host.docker.internal
```

**正确配置：**
```
host: oracle11g  (使用容器名)
```

### 问题 3: CDC 作业未读取到数据

**症状：**
- Flink 作业状态：RUNNING
- Source 读取记录数：0
- 所有算子的 read-records 和 write-records 都是 0

## ✅ 解决方案

### 步骤 1: 修复 LOG_MINING_FLUSH 表结构

```bash
# 执行修复脚本
./execute-sql.sh sql/fix-log-mining-flush-table.sql system helowin helowin
```

**脚本内容：**
```sql
-- 删除旧表
DROP TABLE finance_user.log_mining_flush PURGE;

-- 创建正确结构的表
CREATE TABLE finance_user.log_mining_flush (
    last_scn NUMBER(19,0) DEFAULT 0 NOT NULL
);

-- 插入初始记录
INSERT INTO finance_user.log_mining_flush VALUES (0);
COMMIT;
```

### 步骤 2: 更新数据源配置

在前端界面中：

1. **进入数据源管理**
2. **编辑现有数据源**
3. **修改主机地址：**
   ```
   旧值: host.docker.internal
   新值: oracle11g
   ```
4. **测试连接** - 应该显示成功
5. **保存配置**

或者直接在数据库中更新：

```sql
UPDATE finance_user.cdc_datasources
SET host = 'oracle11g'
WHERE host = 'host.docker.internal';
COMMIT;
```

### 步骤 3: 取消并重新提交 CDC 作业

#### 方法 1: 通过前端界面

1. 进入 "作业管理"
2. 找到运行中的作业
3. 点击 "停止" 按钮
4. 等待作业停止
5. 点击 "启动" 按钮重新提交

#### 方法 2: 通过 Flink Web UI

1. 访问 http://localhost:8081
2. 点击运行中的作业
3. 点击 "Cancel Job"
4. 返回前端重新提交作业

#### 方法 3: 通过 API

```bash
# 获取作业 ID
JOB_ID=$(curl -s http://localhost:8081/jobs | jq -r '.jobs[0].id')

# 取消作业
curl -X PATCH "http://localhost:8081/jobs/${JOB_ID}?mode=cancel"

# 等待作业取消
sleep 5

# 通过前端重新提交作业
```

### 步骤 4: 验证 CDC 功能

#### 4.1 检查作业状态

```bash
# 检查作业是否运行
curl -s http://localhost:8081/jobs | jq '.'

# 获取作业详情
JOB_ID=$(curl -s http://localhost:8081/jobs | jq -r '.jobs[0].id')
curl -s "http://localhost:8081/jobs/${JOB_ID}" | jq '.vertices[] | {name: .name, records: .metrics."read-records"}'
```

#### 4.2 测试数据变更

在 Oracle 中插入测试数据：

```sql
-- 连接到 Oracle
docker exec -it oracle11g bash -c "
source /home/oracle/.bash_profile
sqlplus finance_user/password@helowin
"

-- 插入测试数据
INSERT INTO finance_user.trans_info (
    id, trans_id, account_id, amount, trans_type, 
    trans_time, status, created_time
) VALUES (
    1, 'TEST001', 'ACC001', 100.00, 'DEPOSIT',
    SYSTIMESTAMP, 'SUCCESS', SYSTIMESTAMP
);
COMMIT;

-- 查询数据
SELECT * FROM finance_user.trans_info WHERE id = 1;
```

#### 4.3 检查输出文件

```bash
# 查看输出目录
docker exec flink-jobmanager ls -lh /opt/flink/output/

# 查看 CDC 事件文件
docker exec flink-jobmanager find /opt/flink/output/ -name "*.csv" -o -name "*.json"

# 查看文件内容
docker exec flink-jobmanager cat /opt/flink/output/TRANS_INFO/*.csv
```

#### 4.4 检查 LOG_MINING_FLUSH 表

```sql
-- 查看 last_scn 是否更新
SELECT last_scn FROM finance_user.log_mining_flush;

-- 应该看到一个大于 0 的 SCN 值
```

## 📊 正确的表结构对比

### LOG_MINING_FLUSH 表

| 版本 | 列名 | 数据类型 | 约束 | 初始值 |
|------|------|----------|------|--------|
| **错误** | scn | NUMBER(19,0) | NOT NULL, PRIMARY KEY | 无 |
| **正确** | last_scn | NUMBER(19,0) | NOT NULL, DEFAULT 0 | 0 |

### 关键区别

1. **列名**：`scn` → `last_scn`
2. **主键**：有主键 → 无主键
3. **默认值**：无 → DEFAULT 0
4. **初始记录**：无 → 必须插入一条记录 (0)

## 🔧 完整的配置检查清单

### 数据库配置

- [ ] 归档日志已启用：`SELECT log_mode FROM v$database;` → ARCHIVELOG
- [ ] 补充日志已启用：`SELECT supplemental_log_data_min, supplemental_log_data_all FROM v$database;` → YES, YES
- [ ] LOG_MINING_FLUSH 表结构正确：`DESC finance_user.log_mining_flush;` → LAST_SCN
- [ ] LOG_MINING_FLUSH 有初始记录：`SELECT * FROM finance_user.log_mining_flush;` → 0
- [ ] FINANCE_USER 有 LogMiner 权限

### 网络配置

- [ ] Oracle 容器在 flink-network：`docker network inspect flink-network | grep oracle`
- [ ] 数据源主机使用容器名：`oracle11g`（不是 localhost 或 host.docker.internal）
- [ ] 可以从容器连接 Oracle：`docker exec flink-jobmanager nc -zv oracle11g 1521`

### Flink 作业配置

- [ ] 作业状态：RUNNING
- [ ] Source 读取记录数 > 0
- [ ] 无错误日志
- [ ] 输出目录有文件生成

## 🐛 常见问题

### 问题 1: 作业运行但没有读取数据

**检查：**
```bash
# 查看 Source 的 metrics
curl -s "http://localhost:8081/jobs/${JOB_ID}" | jq '.vertices[0].metrics'
```

**可能原因：**
1. LOG_MINING_FLUSH 表结构错误
2. 数据库连接失败
3. 没有数据变更
4. 表没有补充日志

**解决：**
```sql
-- 检查表的补充日志
SELECT owner, table_name, log_group_type
FROM dba_log_groups
WHERE owner = 'FINANCE_USER'
  AND table_name IN ('TRANS_INFO', 'ACCOUNT_INFO');

-- 如果没有，添加补充日志
ALTER TABLE finance_user.trans_info ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;
ALTER TABLE finance_user.account_info ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;
```

### 问题 2: ORA-00904: "LAST_SCN": invalid identifier

**原因：** 表结构错误

**解决：**
```bash
./execute-sql.sh sql/fix-log-mining-flush-table.sql system helowin helowin
```

### 问题 3: 连接超时

**原因：** 数据源主机配置错误

**解决：**
```sql
UPDATE finance_user.cdc_datasources
SET host = 'oracle11g'
WHERE id = 'your-datasource-id';
COMMIT;
```

### 问题 4: 作业频繁重启

**检查日志：**
```bash
docker-compose logs flink-taskmanager --tail 100 | grep -i error
```

**可能原因：**
1. 内存不足
2. 数据库连接不稳定
3. Checkpoint 失败

## 📝 相关脚本

### 修复脚本

| 脚本 | 用途 |
|------|------|
| `sql/fix-log-mining-flush-table.sql` | 修复 LOG_MINING_FLUSH 表结构 |
| `sql/02-setup-cdc-metadata.sql` | 创建正确的 CDC 元数据（已更新）|
| `sql/04-check-cdc-status.sql` | 检查 CDC 配置状态 |

### 验证脚本

```bash
# 检查表结构
./execute-sql.sh sql/04-check-cdc-status.sql system helowin helowin

# 检查 Flink 作业
curl -s http://localhost:8081/jobs | jq '.'

# 检查输出文件
docker exec flink-jobmanager ls -lh /opt/flink/output/
```

## 📚 参考文档

- [Debezium Oracle Connector](https://debezium.io/documentation/reference/stable/connectors/oracle.html)
- [Flink CDC Connectors](https://ververica.github.io/flink-cdc-connectors/)
- [Oracle LogMiner](https://docs.oracle.com/en/database/oracle/oracle-database/19/sutil/oracle-logminer-utility.html)

## 🎉 总结

### 已修复的问题

✅ **LOG_MINING_FLUSH 表结构** - 从 `scn` 改为 `last_scn`  
✅ **表初始化** - 添加初始记录 (0)  
✅ **SQL 脚本更新** - `02-setup-cdc-metadata.sql` 使用正确结构  

### 需要手动操作

⚠️ **更新数据源配置** - 将 host 改为 `oracle11g`  
⚠️ **重新提交作业** - 取消旧作业，提交新作业  
⚠️ **测试数据变更** - 插入测试数据验证 CDC  

### 验证步骤

1. 检查 LOG_MINING_FLUSH 表：`SELECT * FROM finance_user.log_mining_flush;`
2. 检查作业状态：`curl http://localhost:8081/jobs`
3. 插入测试数据：`INSERT INTO trans_info ...`
4. 检查输出文件：`docker exec flink-jobmanager ls /opt/flink/output/`
5. 验证 SCN 更新：`SELECT last_scn FROM finance_user.log_mining_flush;`

---

**修复时间：** 2026-04-10  
**状态：** ✅ 表结构已修复，等待重新提交作业
