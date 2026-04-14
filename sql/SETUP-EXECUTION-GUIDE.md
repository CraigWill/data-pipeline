# Oracle CDC 配置脚本执行指南

## 概述

本指南说明如何执行 SQL 脚本 00-04，完成 Oracle CDC 的完整配置。

## 脚本说明

| 脚本 | 说明 | 执行用户 | 是否必需 |
|------|------|----------|----------|
| `00-cleanup-flink-user.sql` | 清理旧的 FLINK_USER 和 FLINK_TBS | SYSDBA | 可选 |
| `01-enable-oracle-cdc.sql` | 启用归档日志和补充日志 | SYSDBA | 必需 |
| `02-setup-cdc-metadata.sql` | 创建 LOG_MINING_FLUSH 表 | SYSDBA | 必需 |
| `03-setup-metadata-tables.sql` | 创建元数据表 | SYSTEM/FINANCE_USER | 必需 |
| `04-check-cdc-status.sql` | 检查 CDC 配置状态 | SYSTEM | 验证 |

## 执行方式

### 方式 1: 自动执行（推荐）

#### 1.1 Docker 环境（推荐）

如果 Oracle 运行在 Docker 容器中：

```bash
# 进入项目根目录
cd /path/to/data-pipeline

# 执行配置脚本
./sql/run-setup-docker.sh
```

脚本会自动：
1. 检查 Oracle 容器状态
2. 复制 SQL 文件到容器
3. 按顺序执行所有脚本
4. 显示执行结果

**环境变量**:
```bash
# 自定义容器名称（默认: oracle11g）
export ORACLE_CONTAINER=my-oracle-container

# 自定义 SID（默认: helowin）
export ORACLE_SID=ORCL

# 执行
./sql/run-setup-docker.sh
```

#### 1.2 本地 Oracle 环境

如果 Oracle 安装在本地或远程服务器：

```bash
# 设置环境变量
export ORACLE_HOST=localhost
export ORACLE_PORT=1521
export ORACLE_SID=helowin
export ORACLE_SYSTEM_USER=system
export ORACLE_SYSTEM_PASSWORD=helowin
export ORACLE_FINANCE_USER=finance_user
export ORACLE_FINANCE_PASSWORD=your_password

# 执行配置脚本
./sql/run-setup.sh
```

### 方式 2: 手动执行

#### 2.1 Docker 环境

```bash
# 1. 进入 Oracle 容器
docker exec -it oracle11g bash

# 2. 切换到 oracle 用户
su - oracle

# 3. 复制 SQL 文件到容器（在宿主机执行）
docker cp sql/00-cleanup-flink-user.sql oracle11g:/tmp/
docker cp sql/01-enable-oracle-cdc.sql oracle11g:/tmp/
docker cp sql/02-setup-cdc-metadata.sql oracle11g:/tmp/
docker cp sql/03-setup-metadata-tables.sql oracle11g:/tmp/
docker cp sql/04-check-cdc-status.sql oracle11g:/tmp/

# 4. 在容器内执行（可选：清理旧用户）
cd /tmp
sqlplus / as sysdba @00-cleanup-flink-user.sql

# 5. 启用 CDC（会重启数据库）
sqlplus / as sysdba @01-enable-oracle-cdc.sql

# 6. 创建 CDC 基础设施
sqlplus / as sysdba @02-setup-cdc-metadata.sql

# 7. 创建元数据表
sqlplus system/helowin@helowin @03-setup-metadata-tables.sql

# 8. 检查状态
sqlplus system/helowin@helowin @04-check-cdc-status.sql
```

#### 2.2 本地 Oracle 环境

```bash
# 进入 sql 目录
cd sql

# 1. 清理旧用户（可选）
sqlplus system/helowin@localhost:1521/helowin as sysdba @00-cleanup-flink-user.sql

# 2. 启用 CDC
sqlplus system/helowin@localhost:1521/helowin as sysdba @01-enable-oracle-cdc.sql

# 3. 创建 CDC 基础设施
sqlplus system/helowin@localhost:1521/helowin as sysdba @02-setup-cdc-metadata.sql

# 4. 创建元数据表
sqlplus system/helowin@localhost:1521/helowin @03-setup-metadata-tables.sql

# 5. 检查状态
sqlplus system/helowin@localhost:1521/helowin @04-check-cdc-status.sql
```

### 方式 3: SQL Developer / DBeaver

1. 连接到 Oracle 数据库（使用 SYSTEM 用户）
2. 打开 SQL 文件
3. 按顺序执行：
   - `00-cleanup-flink-user.sql`（可选）
   - `01-enable-oracle-cdc.sql`
   - `02-setup-cdc-metadata.sql`
   - `03-setup-metadata-tables.sql`
   - `04-check-cdc-status.sql`

## 执行流程

```
┌─────────────────────────────────────┐
│ 步骤 0: 清理 FLINK_USER（可选）     │
│ - 删除旧的 FLINK_USER 用户          │
│ - 删除 FLINK_TBS 表空间             │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│ 步骤 1: 启用 Oracle CDC             │
│ - 启用归档日志模式（重启数据库）    │
│ - 启用补充日志                      │
│ - 授予 FINANCE_USER 权限            │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│ 步骤 2: 创建 CDC 基础设施           │
│ - 创建 LOG_MINING_FLUSH 表          │
│ - 清理重复的 LOG_MINING_FLUSH       │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│ 步骤 3: 创建元数据表                │
│ - CDC_DATASOURCES（数据源配置）     │
│ - CDC_TASKS（任务配置）             │
│ - RUNTIME_JOBS（运行时作业）        │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│ 步骤 4: 检查 CDC 状态               │
│ - 验证归档日志                      │
│ - 验证补充日志                      │
│ - 验证权限                          │
│ - 验证表结构                        │
└─────────────────────────────────────┘
```

## 注意事项

### 1. 步骤 1 会重启数据库

**重要**: `01-enable-oracle-cdc.sql` 会执行以下操作：
- `SHUTDOWN IMMEDIATE` - 关闭数据库
- `STARTUP MOUNT` - 启动到 MOUNT 状态
- `ALTER DATABASE ARCHIVELOG` - 启用归档日志
- `ALTER DATABASE OPEN` - 打开数据库

**建议**:
- 在非业务高峰期执行
- 确保没有重要的业务正在运行
- 提前通知相关人员

### 2. 权限要求

- 步骤 0, 1, 2: 需要 **SYSDBA** 权限
- 步骤 3: 需要 **SYSTEM** 或 **FINANCE_USER** 权限
- 步骤 4: 需要 **SYSTEM** 权限（查询系统视图）

### 3. 磁盘空间

启用归档日志后，需要足够的磁盘空间存储归档日志文件：
- 建议至少 10GB 可用空间
- 定期清理旧的归档日志

### 4. 已启用归档日志的情况

如果归档日志已经启用，可以跳过步骤 1：

```bash
# 检查归档日志状态
sqlplus system/helowin@helowin
SQL> SELECT log_mode FROM v$database;

# 如果显示 ARCHIVELOG，可以跳过步骤 1
# 直接执行步骤 2-4
```

## 验证配置

执行完成后，检查以下内容：

### 1. 归档日志模式

```sql
SELECT log_mode FROM v$database;
-- 预期: ARCHIVELOG
```

### 2. 补充日志

```sql
SELECT supplemental_log_data_min, supplemental_log_data_all 
FROM v$database;
-- 预期: MIN=YES, ALL=YES
```

### 3. LOG_MINING_FLUSH 表

```sql
SELECT owner, table_name 
FROM dba_tables 
WHERE table_name = 'LOG_MINING_FLUSH';
-- 预期: 只有 FINANCE_USER.LOG_MINING_FLUSH
```

### 4. 元数据表

```sql
SELECT table_name 
FROM all_tables 
WHERE owner = 'FINANCE_USER'
  AND table_name IN ('CDC_DATASOURCES', 'CDC_TASKS', 'RUNTIME_JOBS')
ORDER BY table_name;
-- 预期: 3 行记录
```

### 5. FINANCE_USER 权限

```sql
SELECT privilege 
FROM dba_sys_privs 
WHERE grantee = 'FINANCE_USER'
  AND privilege IN ('SELECT ANY TRANSACTION', 'FLASHBACK ANY TABLE', 'SELECT ANY TABLE');
-- 预期: 3 行记录
```

## 故障排查

### 问题 1: 数据库重启失败

**错误**: `ORA-01034: ORACLE not available`

**解决方案**:
```bash
# 手动启动数据库
sqlplus / as sysdba
SQL> STARTUP;
```

### 问题 2: 权限不足

**错误**: `ORA-01031: insufficient privileges`

**解决方案**:
- 确保使用 SYSDBA 权限执行步骤 1-2
- 检查连接字符串是否包含 `as sysdba`

### 问题 3: 表已存在

**错误**: `ORA-00955: name is already used by an existing object`

**解决方案**:
- 步骤 3 会自动删除已存在的表
- 如果仍然报错，手动删除表：
  ```sql
  DROP TABLE finance_user.cdc_datasources CASCADE CONSTRAINTS;
  DROP TABLE finance_user.cdc_tasks CASCADE CONSTRAINTS;
  DROP TABLE finance_user.runtime_jobs CASCADE CONSTRAINTS;
  ```

### 问题 4: 磁盘空间不足

**错误**: `ORA-00257: archiver error`

**解决方案**:
```sql
-- 检查归档日志空间
SELECT * FROM v$flash_recovery_area_usage;

-- 清理旧的归档日志
RMAN> DELETE ARCHIVELOG ALL COMPLETED BEFORE 'SYSDATE-7';
```

### 问题 5: Docker 容器无法访问

**错误**: `Error response from daemon: No such container`

**解决方案**:
```bash
# 检查容器名称
docker ps -a

# 启动容器
docker start oracle11g

# 等待 Oracle 启动
sleep 10
```

## 回滚操作

如果需要回滚配置：

### 回滚步骤 3（删除元数据表）

```sql
DROP TABLE finance_user.runtime_jobs CASCADE CONSTRAINTS;
DROP TABLE finance_user.cdc_tasks CASCADE CONSTRAINTS;
DROP TABLE finance_user.cdc_datasources CASCADE CONSTRAINTS;
```

### 回滚步骤 2（删除 LOG_MINING_FLUSH）

```sql
DROP TABLE finance_user.log_mining_flush PURGE;
```

### 回滚步骤 1（禁用归档日志）

```sql
-- 警告: 这会再次重启数据库
SHUTDOWN IMMEDIATE;
STARTUP MOUNT;
ALTER DATABASE NOARCHIVELOG;
ALTER DATABASE OPEN;
```

### 回滚步骤 0（恢复 FLINK_USER）

如果需要恢复 FLINK_USER，需要重新创建用户和表空间。

## 下一步

配置完成后：

1. **启动 Flink 集群**:
   ```bash
   ./start.sh
   ```

2. **访问监控界面**:
   - Flink Web UI: http://localhost:8081
   - Monitor Frontend: http://localhost:8888

3. **创建数据源**:
   - 在监控界面中添加 Oracle 数据源
   - 配置连接信息

4. **创建 CDC 任务**:
   - 选择数据源
   - 选择要监控的表
   - 提交任务

## 相关文档

- `sql/README-SETUP-GUIDE.md` - 详细的设置指南
- `sql/README-oracle-cdc-troubleshooting.md` - CDC 故障排查
- `ARCHITECTURE.md` - 系统架构说明
- `BUILD-GUIDE.md` - 构建指南
- `COMPLETE-STARTUP-GUIDE.md` - 启动指南

## 技术支持

如有问题，请查看：
1. 脚本输出日志
2. Oracle 告警日志: `$ORACLE_BASE/diag/rdbms/helowin/helowin/trace/alert_helowin.log`
3. 相关文档（见上方列表）
