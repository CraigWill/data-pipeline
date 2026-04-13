# Oracle CDC 数据库配置指南

本指南提供了配置 Oracle 数据库以支持 Flink CDC 的完整步骤。

## 📋 概述

SQL 脚本已经过优化和合并，按照执行顺序编号：

1. **01-enable-oracle-cdc.sql** - 启用 Oracle CDC（归档日志、补充日志、权限）
2. **02-setup-flink-infrastructure.sql** - 创建 Flink 基础设施（表空间、用户、LOG_MINING_FLUSH）
3. **03-setup-metadata-tables.sql** - 创建元数据表（数据源、任务、运行时作业）
4. **04-check-cdc-status.sql** - 综合状态检查

## 🚀 快速开始

### 前提条件

- Oracle 数据库已安装并运行
- 具有 SYSDBA 权限的用户（如 system）
- 已创建 finance_user 用户（业务数据用户）

### 执行步骤

#### 步骤 1: 启用 Oracle CDC

```bash
sqlplus / as sysdba @01-enable-oracle-cdc.sql
```

或使用 system 用户：

```bash
sqlplus system/helowin@localhost:1521/helowin @01-enable-oracle-cdc.sql
```

**此脚本会：**
- ✅ 检查当前归档日志和补充日志状态
- ✅ 启用归档日志模式（需要重启数据库）
- ✅ 启用补充日志（最小 + 全列）
- ✅ 授予 finance_user LogMiner 所需的所有权限
- ✅ 验证配置

**注意：** 此步骤会重启数据库！

---

#### 步骤 2: 创建 Flink 基础设施

```bash
sqlplus / as sysdba @02-setup-flink-infrastructure.sql
```

**此脚本会：**
- ✅ 创建 FLINK_TBS 表空间（100M，自动扩展到 2G）
- ✅ 创建 FLINK_USER 用户（密码: flink123）
- ✅ 授予 FLINK_USER CDC 所需的所有权限
- ✅ 在 FLINK_USER schema 创建 LOG_MINING_FLUSH 表
- ✅ 清理其他 schema 中的重复 LOG_MINING_FLUSH 表
- ✅ 授予 finance_user 访问 LOG_MINING_FLUSH 的权限

**重要：** LOG_MINING_FLUSH 表应该只在 FLINK_USER schema 中！

---

#### 步骤 3: 创建元数据表

```bash
sqlplus flink_user/flink123@helowin @03-setup-metadata-tables.sql
```

或使用 system 用户：

```bash
sqlplus system/helowin@helowin @03-setup-metadata-tables.sql
```

**此脚本会：**
- ✅ 创建 cdc_datasources 表（数据源配置）
- ✅ 创建 cdc_tasks 表（任务配置模板）
- ✅ 创建 runtime_jobs 表（运行时作业跟踪）
- ✅ 创建索引和触发器
- ✅ 授予 finance_user 访问权限

---

#### 步骤 4: 验证配置

```bash
sqlplus system/helowin@helowin @04-check-cdc-status.sql
```

或使用 finance_user：

```bash
sqlplus finance_user/password@helowin @04-check-cdc-status.sql
```

**此脚本会检查：**
- ✅ 归档日志模式（应为 ARCHIVELOG）
- ✅ 补充日志状态（MIN=YES, ALL=YES）
- ✅ 归档日志配置和历史
- ✅ Redo Log 状态
- ✅ FINANCE_USER 和 FLINK_USER 权限
- ✅ LOG_MINING_FLUSH 表位置（应只在 FLINK_USER）
- ✅ 元数据表（CDC_DATASOURCES, CDC_TASKS, RUNTIME_JOBS）
- ✅ 业务表补充日志配置
- ✅ LogMiner 字典和会话状态
- ✅ 当前 SCN 信息

---

## 📊 数据库架构

### Schema 分离

```
┌─────────────────────────────────────────────────────────┐
│ FINANCE_USER Schema (业务数据)                          │
│ - TRANS_INFO (交易表)                                   │
│ - ACCOUNT_INFO (账户表)                                 │
│ - IDS_TRANS_INFO (IDS 交易表)                          │
│ - IDS_ACCOUNT_INFO (IDS 账户表)                        │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ FLINK_USER Schema (Flink 基础设施)                      │
│ - LOG_MINING_FLUSH (LogMiner flush 表)                 │
│ - CDC_DATASOURCES (数据源配置)                          │
│ - CDC_TASKS (任务配置模板)                              │
│ - RUNTIME_JOBS (运行时作业跟踪)                         │
└─────────────────────────────────────────────────────────┘
```

### 表空间

- **FLINK_TBS**: Flink 专用表空间（100M，自动扩展到 2G）
- **SYSTEM**: 系统表空间（不应包含 LOG_MINING_FLUSH）

---

## 🔧 常见问题

### 1. 归档日志模式未启用

**症状：** `log_mode = NOARCHIVELOG`

**解决方案：** 运行 `01-enable-oracle-cdc.sql`，此脚本会自动重启数据库并启用归档日志。

---

### 2. LOG_MINING_FLUSH 表在错误的 schema

**症状：** 表在 SYSTEM 或 FINANCE_USER schema

**解决方案：** 运行 `02-setup-flink-infrastructure.sql`，此脚本会自动清理重复的表并在正确位置创建。

---

### 3. 权限不足

**症状：** Flink CDC 作业报错 `ORA-01031: insufficient privileges`

**解决方案：**
1. 运行 `04-check-cdc-status.sql` 检查权限
2. 如果缺少权限，重新运行 `01-enable-oracle-cdc.sql`

---

### 4. 补充日志未启用

**症状：** `SUPPLEMENTAL_LOG_DATA_MIN = NO`

**解决方案：** 运行以下命令：

```sql
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA;
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;
```

或重新运行 `01-enable-oracle-cdc.sql`。

---

### 5. 表统计信息过期

**症状：** `last_analyzed` 超过 7 天或为 NULL

**解决方案：** 更新统计信息：

```sql
BEGIN
    DBMS_STATS.GATHER_TABLE_STATS(
        ownname => 'FINANCE_USER',
        tabname => 'TRANS_INFO',
        estimate_percent => DBMS_STATS.AUTO_SAMPLE_SIZE,
        cascade => TRUE
    );
END;
/
```

---

## 📝 旧脚本映射

为了向后兼容，以下是旧脚本到新脚本的映射：

| 旧脚本 | 新脚本 | 说明 |
|--------|--------|------|
| `enable-archivelog.sql` | `01-enable-oracle-cdc.sql` | 已合并 |
| `enable-archivelog-mode.sql` | `01-enable-oracle-cdc.sql` | 已合并 |
| `enable-oracle-archivelog.sql` | `01-enable-oracle-cdc.sql` | 已合并 |
| `configure-oracle-for-cdc.sql` | `01-enable-oracle-cdc.sql` | 已合并 |
| `setup-oracle-cdc.sql` | `01-enable-oracle-cdc.sql` | 已合并 |
| `grant-logminer-permissions.sql` | `01-enable-oracle-cdc.sql` | 已合并 |
| `setup-flink-tablespace.sql` | `02-setup-flink-infrastructure.sql` | 已合并 |
| `setup-flink-user-schema.sql` | `02-setup-flink-infrastructure.sql` | 已合并 |
| `setup-log-mining-flush-table.sql` | `02-setup-flink-infrastructure.sql` | 已合并 |
| `cleanup-duplicate-log-mining-flush.sql` | `02-setup-flink-infrastructure.sql` | 已合并 |
| `setup-complete.sql` | `03-setup-metadata-tables.sql` | 已合并 |
| `setup-metadata-tables.sql` | `03-setup-metadata-tables.sql` | 已合并 |
| `setup-runtime-jobs-table.sql` | `03-setup-metadata-tables.sql` | 已合并 |
| `check-archivelog-status.sql` | `04-check-cdc-status.sql` | 已合并 |
| `check-logminer-status.sql` | `04-check-cdc-status.sql` | 已合并 |
| `check-table-stats.sql` | `04-check-cdc-status.sql` | 已合并 |
| `check-account-info-status.sql` | `04-check-cdc-status.sql` | 已合并 |
| `verify-log-mining-flush.sql` | `04-check-cdc-status.sql` | 已合并 |

**建议：** 使用新的编号脚本，旧脚本保留用于参考。

---

## 🎯 最佳实践

### 1. 执行顺序

**必须按照编号顺序执行：**
1. 01 → 2. 02 → 3. 03 → 4. 04

### 2. 权限管理

- **SYSDBA 权限**：步骤 1 和 2 需要
- **FLINK_USER 或 SYSTEM**：步骤 3 可以使用
- **任何用户**：步骤 4 可以使用任何有权限的用户

### 3. 备份

在执行任何脚本之前，建议：
- 备份数据库
- 记录当前配置
- 在测试环境先验证

### 4. 监控

定期运行 `04-check-cdc-status.sql` 监控：
- 归档日志空间使用
- LogMiner 会话状态
- 表统计信息
- 权限配置

---

## 📚 相关文档

- [Oracle LogMiner 官方文档](https://docs.oracle.com/en/database/oracle/oracle-database/19/sutil/oracle-logminer-utility.html)
- [Flink CDC 官方文档](https://ververica.github.io/flink-cdc-connectors/)
- [Oracle 归档日志管理](https://docs.oracle.com/en/database/oracle/oracle-database/19/admin/managing-archived-redo-log-files.html)

---

## 🆘 获取帮助

如果遇到问题：

1. 运行 `04-check-cdc-status.sql` 获取完整状态报告
2. 检查 Oracle 告警日志：`$ORACLE_BASE/diag/rdbms/helowin/helowin/trace/alert_helowin.log`
3. 查看相关 README 文档：
   - `README-oracle-cdc-troubleshooting.md`
   - `README-fix-log-mining-flush.md`
   - `README-table-issues.md`

---

## ✅ 配置检查清单

完成所有步骤后，确认以下项目：

- [ ] 归档日志模式: `ARCHIVELOG`
- [ ] 补充日志: `MIN=YES`, `ALL=YES`
- [ ] FINANCE_USER 权限: `SELECT ANY TRANSACTION`, `FLASHBACK ANY TABLE`
- [ ] FLINK_USER 权限: `LOGMINING`, `SELECT ANY TABLE`
- [ ] LOG_MINING_FLUSH 表: 只在 `FLINK_USER` schema
- [ ] 元数据表: `CDC_DATASOURCES`, `CDC_TASKS`, `RUNTIME_JOBS` 已创建
- [ ] 业务表补充日志: 已配置
- [ ] 表统计信息: 最近 7 天内更新

---

**最后更新：** 2024-04-10
**版本：** 1.0
