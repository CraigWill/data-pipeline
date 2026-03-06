# LOG_MINING_FLUSH 表位置问题 - 已解决

## 问题描述

`finance_user` schema 下再次出现了 `LOG_MINING_FLUSH` 表，这违反了最佳实践。该表应该只存在于 `flink_user` schema 中。

## 根本原因

1. **旧作业遗留**: 之前运行的作业（在代码修复前提交的）可能创建了这个表
2. **表未及时清理**: 即使作业已取消，表仍然保留在数据库中
3. **发现表功能**: 系统的"发现表"功能会列出所有表，包括这个不应该存在的表

## 解决方案

### 1. 立即清理

已删除 `finance_user.LOG_MINING_FLUSH` 表：

```sql
DROP TABLE finance_user.LOG_MINING_FLUSH CASCADE CONSTRAINTS;
```

### 2. 验证配置

确认代码配置正确（`CdcJobMain.java` 第 147 行）：

```java
debeziumProps.setProperty("log.mining.flush.table.name", "flink_user.log_mining_flush");
```

### 3. 验证权限

确认 `finance_user` 有正确的权限：

```sql
GRANT SELECT, INSERT, UPDATE, DELETE ON flink_user.log_mining_flush TO finance_user;
```

权限验证结果：
```
GRANTEE              PRIVILEGE            GRA
-------------------- -------------------- ---
FINANCE_USER         DELETE               NO
FINANCE_USER         INSERT               NO
FINANCE_USER         SELECT               NO
FINANCE_USER         UPDATE               NO
```

### 4. 当前状态

```
OWNER                TABLE_NAME           NUM_ROWS
-------------------- -------------------- ----------
FLINK_USER           LOG_MINING_FLUSH              1
```

✅ 只有 `flink_user.LOG_MINING_FLUSH` 存在

## 预防措施

### 自动检查脚本

创建了两个脚本用于定期检查和清理：

#### 1. SQL 清理脚本
`sql/cleanup-duplicate-log-mining-flush.sql`

功能：
- 自动检测并删除 `finance_user.LOG_MINING_FLUSH`
- 验证 `flink_user.LOG_MINING_FLUSH` 存在
- 检查并修复权限
- 显示当前状态

#### 2. Shell 便捷脚本
`check-log-mining-flush.sh`

使用方法：
```bash
./check-log-mining-flush.sh
```

输出示例：
```
============================================
检查 LOG_MINING_FLUSH 表状态
============================================

✓ finance_user 下没有 LOG_MINING_FLUSH 表
✓ flink_user.LOG_MINING_FLUSH 表存在
✓ finance_user 权限正确

============================================
当前 LOG_MINING_FLUSH 表状态:
============================================

OWNER                TABLE_NAME           NUM_ROWS
-------------------- -------------------- ----------
FLINK_USER           LOG_MINING_FLUSH              1

============================================
finance_user 权限:
============================================

GRANTEE              PRIVILEGE            GRA
-------------------- -------------------- ---
FINANCE_USER         DELETE               NO
FINANCE_USER         INSERT               NO
FINANCE_USER         SELECT               NO
FINANCE_USER         UPDATE               NO

============================================
清理完成！
============================================
```

## 监控建议

### 定期检查（推荐每天或每周）

```bash
# 方法 1: 使用便捷脚本
./check-log-mining-flush.sh

# 方法 2: 手动查询
docker exec -i oracle11g bash -c "..." <<EOF
SELECT owner, table_name, num_rows
FROM dba_tables
WHERE table_name = 'LOG_MINING_FLUSH'
ORDER BY owner;
EOF
```

### 作业提交前检查

在提交新的 CDC 作业前，确保：
1. 没有旧作业在运行
2. `finance_user` 下没有 `LOG_MINING_FLUSH` 表
3. 权限配置正确

### 作业运行后验证

作业运行一段时间后，检查：
1. 是否只有 `flink_user.LOG_MINING_FLUSH` 存在
2. 表中是否有数据更新
3. 没有权限错误日志

## 故障排查流程

### 如果再次出现问题

1. **立即清理**
   ```bash
   ./check-log-mining-flush.sh
   ```

2. **检查运行中的作业**
   ```bash
   curl http://localhost:8081/jobs/overview
   ```

3. **取消可疑作业**
   ```bash
   curl -X PATCH http://localhost:8081/jobs/{job_id}
   ```

4. **验证配置**
   ```bash
   grep "log.mining.flush.table.name" src/main/java/com/realtime/pipeline/CdcJobMain.java
   ```

5. **检查应用日志**
   ```bash
   docker logs flink-monitor-backend --tail 100 | grep -i "log.mining"
   ```

## 技术细节

### 为什么会创建在 finance_user 下？

Debezium Oracle CDC 连接器在以下情况下会在当前用户 schema 下创建表：
1. 未配置 `log.mining.flush.table.name`
2. 配置的表不存在
3. 当前用户没有权限访问配置的表

### 为什么要使用 flink_user schema？

1. **职责分离**: 管理表和业务表分开
2. **权限控制**: 业务用户只需读写权限，不需要 DDL 权限
3. **维护方便**: 集中管理 Flink 相关的元数据表
4. **避免污染**: 不在业务 schema 中创建技术表

### 配置传递路径

```
CdcJobMain.java
  ↓
debeziumProps.setProperty("log.mining.flush.table.name", "flink_user.log_mining_flush")
  ↓
OracleSourceBuilder.debeziumProperties(debeziumProps)
  ↓
Debezium Connector
  ↓
使用 flink_user.log_mining_flush
```

## 相关文件

- `sql/cleanup-duplicate-log-mining-flush.sql` - SQL 清理脚本
- `check-log-mining-flush.sh` - Shell 便捷脚本
- `sql/FIX-LOG-MINING-FLUSH-LOCATION.md` - 原始修复文档
- `sql/verify-log-mining-flush.sql` - 验证脚本
- `src/main/java/com/realtime/pipeline/CdcJobMain.java` - 配置代码

## 总结

### 问题状态
✅ **已解决**

### 当前配置
- ✅ 代码配置正确
- ✅ 权限配置正确
- ✅ 只有 `flink_user.LOG_MINING_FLUSH` 存在
- ✅ 清理脚本已创建

### 后续行动
- 🔄 定期运行检查脚本
- 🔄 监控新作业的表创建行为
- 🔄 保持代码和权限配置的一致性

---

**最后更新**: 2026-03-06  
**状态**: ✅ 已解决并建立预防机制
