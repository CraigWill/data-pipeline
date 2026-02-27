# Oracle 归档日志模式启用指南

## 为什么需要归档日志？

Oracle CDC (LogMiner) 需要归档日志模式才能工作。归档日志记录了数据库的所有变更，CDC 通过读取这些日志来捕获数据变更。

## 当前状态

根据之前的日志，归档日志模式应该已经启用（Task 8 已完成）。但如果出现以下错误，说明需要重新启用：

```
ORA-01325: archive log mode must be enabled to build into the logstream
```

## 方法 1：使用 SQL*Plus（推荐）

### 步骤 1：检查当前状态

```bash
sqlplus system/helowin@localhost:1521/helowin @check-archivelog-status.sql
```

或者直接查询：

```bash
sqlplus system/helowin@localhost:1521/helowin <<EOF
SELECT log_mode FROM v\$database;
EXIT;
EOF
```

**预期输出**：
- `ARCHIVELOG` - 已启用 ✅
- `NOARCHIVELOG` - 未启用 ❌

### 步骤 2：启用归档日志（如果未启用）

**重要**：启用归档日志需要重启数据库，请确保没有重要业务正在运行。

```bash
# 以 SYSDBA 权限执行
sqlplus / as sysdba @enable-archivelog-mode.sql
```

或者手动执行：

```sql
-- 连接为 SYSDBA
sqlplus / as sysdba

-- 关闭数据库
SHUTDOWN IMMEDIATE;

-- 启动到 MOUNT 状态
STARTUP MOUNT;

-- 启用归档日志
ALTER DATABASE ARCHIVELOG;

-- 打开数据库
ALTER DATABASE OPEN;

-- 验证
SELECT log_mode FROM v$database;
-- 应该显示: ARCHIVELOG

-- 查看详细信息
ARCHIVE LOG LIST;

EXIT;
```

## 方法 2：使用 Docker（如果数据库在容器中）

如果 Oracle 数据库运行在 Docker 容器中：

```bash
# 1. 进入容器
docker exec -it <oracle-container-name> bash

# 2. 连接到数据库
sqlplus / as sysdba

# 3. 执行启用命令（同上）
SHUTDOWN IMMEDIATE;
STARTUP MOUNT;
ALTER DATABASE ARCHIVELOG;
ALTER DATABASE OPEN;
SELECT log_mode FROM v$database;
EXIT;
```

## 方法 3：使用提供的脚本

我已经创建了以下脚本文件：

### 1. 检查状态
```bash
# 使用 SQL 文件
sqlplus system/helowin@localhost:1521/helowin @check-archivelog-status.sql
```

### 2. 启用归档日志
```bash
# 使用 SQL 文件（需要 SYSDBA 权限）
sqlplus / as sysdba @enable-archivelog-mode.sql
```

## 验证归档日志已启用

执行以下命令验证：

```sql
-- 方法 1：查询数据库模式
SELECT log_mode FROM v$database;
-- 应该返回: ARCHIVELOG

-- 方法 2：查看归档日志列表
ARCHIVE LOG LIST;
-- 应该显示: Database log mode: Archive Mode

-- 方法 3：查看归档目标
SELECT dest_name, status, destination 
FROM v$archive_dest 
WHERE status != 'INACTIVE';
```

## 归档日志配置说明

### 默认配置
- **归档目标**: `USE_DB_RECOVERY_FILE_DEST`
- **归档格式**: `%t_%s_%r.dbf`
- **归档位置**: 通常在 `$ORACLE_BASE/fast_recovery_area/`

### 查看归档日志位置
```sql
SELECT name, value 
FROM v$parameter 
WHERE name LIKE 'log_archive_dest%' 
   OR name = 'db_recovery_file_dest';
```

## 常见问题

### Q1: 启用归档日志后磁盘空间不足怎么办？

A: 归档日志会占用磁盘空间，需要定期清理：

```sql
-- 查看归档日志占用空间
SELECT SUM(blocks * block_size) / 1024 / 1024 / 1024 AS size_gb
FROM v$archived_log;

-- 删除旧的归档日志（RMAN）
RMAN TARGET /
DELETE ARCHIVELOG ALL COMPLETED BEFORE 'SYSDATE-7';
EXIT;
```

### Q2: 如何禁用归档日志？

A: 如果需要禁用（不推荐，CDC 将无法工作）：

```sql
SHUTDOWN IMMEDIATE;
STARTUP MOUNT;
ALTER DATABASE NOARCHIVELOG;
ALTER DATABASE OPEN;
```

### Q3: 归档日志对性能有影响吗？

A: 有轻微影响，但对于 CDC 是必需的。可以通过以下方式优化：
- 将归档日志放在快速磁盘上
- 配置多个归档目标
- 定期清理旧的归档日志

### Q4: 如何查看归档日志是否正常工作？

A: 执行以下查询：

```sql
-- 查看最近的归档日志
SELECT sequence#, first_time, next_time, archived, status
FROM v$log
ORDER BY sequence# DESC;

-- 查看归档历史
SELECT sequence#, first_time, next_time
FROM v$archived_log
WHERE dest_id = 1
ORDER BY sequence# DESC
FETCH FIRST 10 ROWS ONLY;
```

## 与 Flink CDC 的关系

启用归档日志后，Flink CDC 可以：
1. 通过 LogMiner 读取归档日志
2. 捕获所有数据变更（INSERT, UPDATE, DELETE）
3. 实现增量数据同步

如果归档日志未启用，会出现错误：
```
ORA-01325: archive log mode must be enabled to build into the logstream
```

## 下一步

启用归档日志后：

1. **重启 Flink CDC 作业**（如果之前失败）：
   ```bash
   ./start-flink-cdc-job-safe.sh
   ```

2. **验证 CDC 工作正常**：
   ```bash
   ./diagnose-cdc-issue.sh
   ```

3. **测试数据捕获**：
   ```bash
   ./quick-test-cdc.sh
   ```

## 相关文件

- `enable-archivelog-mode.sql` - 启用归档日志的 SQL 脚本
- `check-archivelog-status.sql` - 检查归档日志状态的 SQL 脚本
- `enable-archivelog.sql` - 简化版启用脚本（之前创建）
- `check-and-enable-archivelog.sh` - Bash 脚本（需要 sqlplus64）

## 参考

- [Oracle Database Administrator's Guide - Managing Archived Redo Logs](https://docs.oracle.com/en/database/oracle/oracle-database/19/admin/managing-archived-redo-log-files.html)
- [Debezium Oracle Connector - Prerequisites](https://debezium.io/documentation/reference/stable/connectors/oracle.html#oracle-prerequisites)
