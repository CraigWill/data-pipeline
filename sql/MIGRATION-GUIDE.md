# SQL 脚本迁移指南

## 📋 概述

为了简化数据库配置流程，我们将 26 个 SQL 脚本合并优化为 4 个核心脚本。本文档说明了迁移过程和脚本映射关系。

## 🎯 优化目标

1. **减少重复**：合并功能重复的脚本
2. **清晰流程**：按执行顺序编号（01-04）
3. **易于维护**：每个脚本职责单一明确
4. **完整验证**：提供综合检查脚本

## 📊 脚本合并方案

### 新脚本结构

```
sql/
├── 01-enable-oracle-cdc.sql          # Oracle CDC 完整配置
├── 02-setup-flink-infrastructure.sql # Flink 基础设施
├── 03-setup-metadata-tables.sql      # 元数据表
├── 04-check-cdc-status.sql           # 综合状态检查
└── README-SETUP-GUIDE.md             # 配置指南
```

### 合并详情

#### 1️⃣ 归档日志和 CDC 配置 → `01-enable-oracle-cdc.sql`

**合并的脚本：**
- `enable-archivelog.sql` (简单版，6 行)
- `enable-archivelog-mode.sql` (详细版，带注释)
- `enable-oracle-archivelog.sql` (包含补充日志)
- `configure-oracle-for-cdc.sql` (CDC 配置)
- `setup-oracle-cdc.sql` (完整 CDC 配置)
- `grant-logminer-permissions.sql` (权限授予)

**合并原因：**
- 3 个归档日志脚本功能完全重复，只是详细程度不同
- 2 个 CDC 配置脚本有 80% 重叠
- 权限授予是 CDC 配置的必要步骤

**新脚本功能：**
- ✅ 检查当前状态
- ✅ 启用归档日志（自动重启数据库）
- ✅ 启用补充日志（最小 + 全列）
- ✅ 授予所有必要权限
- ✅ 验证配置

---

#### 2️⃣ Flink 基础设施 → `02-setup-flink-infrastructure.sql`

**合并的脚本：**
- `setup-flink-tablespace.sql` (最完整的版本)
- `setup-flink-user-schema.sql` (创建用户和 schema)
- `setup-log-mining-flush-table.sql` (创建 flush 表)
- `cleanup-duplicate-log-mining-flush.sql` (清理重复表)

**合并原因：**
- 这些脚本都是创建 Flink 基础设施的步骤
- `setup-flink-tablespace.sql` 已经包含了大部分功能
- 清理重复表应该是设置过程的一部分

**新脚本功能：**
- ✅ 创建 FLINK_TBS 表空间
- ✅ 创建 FLINK_USER 用户
- ✅ 授予 CDC 权限
- ✅ 创建 LOG_MINING_FLUSH 表（正确位置）
- ✅ 清理其他 schema 的重复表
- ✅ 验证配置

---

#### 3️⃣ 元数据表 → `03-setup-metadata-tables.sql`

**合并的脚本：**
- `setup-complete.sql` (完整设置)
- `setup-metadata-tables.sql` (数据源和任务表)
- `setup-runtime-jobs-table.sql` (运行时作业表)

**合并原因：**
- 这 3 个脚本都是创建元数据表
- `setup-complete.sql` 和 `setup-metadata-tables.sql` 有 90% 重叠
- 运行时作业表是元数据的一部分

**新脚本功能：**
- ✅ 创建 cdc_datasources 表
- ✅ 创建 cdc_tasks 表
- ✅ 创建 runtime_jobs 表
- ✅ 创建索引和触发器
- ✅ 授权给 finance_user
- ✅ 验证表创建

---

#### 4️⃣ 综合检查 → `04-check-cdc-status.sql`

**合并的脚本：**
- `check-archivelog-status.sql` (归档日志检查)
- `check-logminer-status.sql` (LogMiner 检查)
- `check-table-stats.sql` (表统计信息)
- `check-account-info-status.sql` (特定表检查)
- `verify-log-mining-flush.sql` (flush 表验证)

**合并原因：**
- 这些都是检查脚本，应该合并为一个综合检查
- 避免多次运行不同的检查脚本
- 提供完整的状态报告

**新脚本功能：**
- ✅ 归档日志模式和配置
- ✅ 补充日志状态
- ✅ Redo Log 状态
- ✅ 用户权限（FINANCE_USER, FLINK_USER）
- ✅ LOG_MINING_FLUSH 表位置
- ✅ 元数据表状态
- ✅ 业务表补充日志
- ✅ LogMiner 字典和会话
- ✅ 当前 SCN 信息
- ✅ 数据库连接

---

## 🔄 迁移步骤

### 对于新部署

直接使用新脚本：

```bash
# 1. 启用 Oracle CDC
sqlplus / as sysdba @01-enable-oracle-cdc.sql

# 2. 创建 Flink 基础设施
sqlplus / as sysdba @02-setup-flink-infrastructure.sql

# 3. 创建元数据表
sqlplus flink_user/flink123@helowin @03-setup-metadata-tables.sql

# 4. 验证配置
sqlplus system/helowin@helowin @04-check-cdc-status.sql
```

### 对于已有部署

如果已经使用旧脚本配置过，可以：

1. **运行检查脚本验证当前状态：**
   ```bash
   sqlplus system/helowin@helowin @04-check-cdc-status.sql
   ```

2. **根据检查结果决定是否需要重新配置：**
   - 如果所有检查都通过（✓），无需重新配置
   - 如果有失败项（✗），运行相应的新脚本

3. **增量修复（推荐）：**
   ```bash
   # 如果只是 LOG_MINING_FLUSH 位置错误
   sqlplus / as sysdba @02-setup-flink-infrastructure.sql
   
   # 如果缺少元数据表
   sqlplus flink_user/flink123@helowin @03-setup-metadata-tables.sql
   ```

---

## 📝 旧脚本处理建议

### 保留的脚本

以下脚本因为特殊用途而保留：

| 脚本 | 用途 | 说明 |
|------|------|------|
| `create-test-table.sql` | 测试 | 创建测试表 |
| `create-account-table.sql` | 业务表创建 | 创建账户表 |
| `migrate-data.sql` | 数据迁移 | 迁移历史数据 |
| `migrate-remove-task-status.sql` | Schema 迁移 | 移除旧字段 |
| `add-savepoint-columns.sql` | Schema 变更 | 添加 savepoint 列 |

### 可以删除的脚本

以下脚本已被新脚本完全替代，可以安全删除：

```bash
# 归档日志相关（已合并到 01）
rm sql/enable-archivelog.sql
rm sql/enable-archivelog-mode.sql
rm sql/enable-oracle-archivelog.sql

# CDC 配置相关（已合并到 01）
rm sql/configure-oracle-for-cdc.sql
rm sql/setup-oracle-cdc.sql
rm sql/grant-logminer-permissions.sql

# Flink 基础设施相关（已合并到 02）
rm sql/setup-flink-tablespace.sql
rm sql/setup-flink-user-schema.sql
rm sql/setup-log-mining-flush-table.sql
rm sql/cleanup-duplicate-log-mining-flush.sql

# 元数据表相关（已合并到 03）
rm sql/setup-complete.sql
rm sql/setup-metadata-tables.sql
rm sql/setup-runtime-jobs-table.sql

# 检查脚本相关（已合并到 04）
rm sql/check-archivelog-status.sql
rm sql/check-logminer-status.sql
rm sql/check-table-stats.sql
rm sql/check-account-info-status.sql
rm sql/verify-log-mining-flush.sql
```

**或者保留在归档目录：**

```bash
mkdir -p sql/archive
mv sql/enable-archivelog*.sql sql/archive/
mv sql/configure-oracle-for-cdc.sql sql/archive/
mv sql/setup-*.sql sql/archive/
mv sql/check-*.sql sql/archive/
mv sql/cleanup-*.sql sql/archive/
mv sql/verify-*.sql sql/archive/
mv sql/grant-*.sql sql/archive/
```

---

## 📊 对比统计

### 脚本数量

| 类型 | 旧版本 | 新版本 | 减少 |
|------|--------|--------|------|
| 归档日志配置 | 3 | 1 | -67% |
| CDC 配置 | 3 | 1 | -67% |
| Flink 基础设施 | 4 | 1 | -75% |
| 元数据表 | 3 | 1 | -67% |
| 检查脚本 | 5 | 1 | -80% |
| **总计** | **18** | **4** | **-78%** |

### 代码行数

| 脚本 | 旧版本总行数 | 新版本行数 | 变化 |
|------|-------------|-----------|------|
| CDC 配置 | ~450 行 | ~180 行 | -60% |
| Flink 基础设施 | ~380 行 | ~200 行 | -47% |
| 元数据表 | ~320 行 | ~250 行 | -22% |
| 检查脚本 | ~520 行 | ~350 行 | -33% |

**总体减少：** ~1670 行 → ~980 行（-41%）

---

## ✅ 验证清单

迁移完成后，运行以下检查：

```bash
# 1. 运行综合检查
sqlplus system/helowin@helowin @04-check-cdc-status.sql

# 2. 确认以下项目
```

- [ ] 归档日志模式: `ARCHIVELOG`
- [ ] 补充日志: `MIN=YES`, `ALL=YES`
- [ ] FINANCE_USER 权限完整
- [ ] FLINK_USER 权限完整
- [ ] LOG_MINING_FLUSH 只在 FLINK_USER schema
- [ ] 元数据表已创建（3 个表）
- [ ] 业务表补充日志已配置
- [ ] 无重复表或配置

---

## 🆘 回滚方案

如果新脚本出现问题，可以回滚到旧脚本：

```bash
# 1. 恢复旧脚本（如果已删除）
git checkout HEAD -- sql/

# 2. 使用旧脚本重新配置
# （按照旧的 README 文档执行）
```

---

## 📚 相关文档

- [README-SETUP-GUIDE.md](./README-SETUP-GUIDE.md) - 新脚本使用指南
- [README-oracle-cdc-troubleshooting.md](./README-oracle-cdc-troubleshooting.md) - 故障排查
- [README-fix-log-mining-flush.md](./README-fix-log-mining-flush.md) - LOG_MINING_FLUSH 问题修复

---

**最后更新：** 2024-04-10
**版本：** 1.0
