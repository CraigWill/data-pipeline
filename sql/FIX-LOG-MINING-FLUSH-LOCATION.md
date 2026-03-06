# LOG_MINING_FLUSH 表位置修复

## 问题描述

在 `finance_user` schema 下又产生了 `LOG_MINING_FLUSH` 表，这不符合最佳实践。该表应该只存在于 `flink_user` schema 中。

## 根本原因

1. **旧作业未使用新配置**: 之前运行的 CDC 作业是在代码更新之前提交的，没有使用 `log.mining.flush.table.name` 配置
2. **权限问题**: `finance_user` 没有权限访问 `flink_user.log_mining_flush` 表，导致 CDC 连接器回退到在当前用户下创建表

## 修复步骤

### 1. 取消旧的 CDC 作业

```bash
# 查看运行中的作业
curl http://localhost:8081/jobs

# 取消作业（替换为实际的 job_id）
curl -X PATCH http://localhost:8081/jobs/{job_id}
```

**执行结果**: ✅ 已取消作业 `a6e5b9d62ed8a16168cb6da47ad1ac2a`

### 2. 删除 finance_user 下的 LOG_MINING_FLUSH 表

```sql
DROP TABLE finance_user.LOG_MINING_FLUSH CASCADE CONSTRAINTS;
```

**执行结果**: ✅ 已删除

### 3. 授予 finance_user 访问 flink_user.log_mining_flush 的权限

```sql
GRANT SELECT, INSERT, UPDATE, DELETE ON flink_user.log_mining_flush TO finance_user;
```

**执行结果**: ✅ 已授权

### 4. 验证配置

检查代码中的配置（`CdcJobMain.java` 第 147 行）：

```java
debeziumProps.setProperty("log.mining.flush.table.name", "flink_user.log_mining_flush");
```

**状态**: ✅ 配置正确

### 5. 重新构建和部署

```bash
# 重新构建 JAR
mvn clean package -DskipTests

# 重新构建 Docker 镜像
docker-compose build monitor-backend

# 重启服务
docker-compose up -d monitor-backend
```

**执行结果**: ✅ 已完成

## 验证

### 检查表位置

```sql
SELECT owner, table_name, num_rows
FROM dba_tables
WHERE table_name = 'LOG_MINING_FLUSH'
ORDER BY owner;
```

**预期结果**:
```
OWNER                TABLE_NAME                         NUM_ROWS
-------------------- ------------------------------ ------------
FLINK_USER           LOG_MINING_FLUSH                          1
```

### 检查权限

```sql
SELECT grantee, privilege
FROM dba_tab_privs
WHERE owner = 'FLINK_USER'
  AND table_name = 'LOG_MINING_FLUSH'
  AND grantee = 'FINANCE_USER';
```

**预期结果**:
```
GRANTEE              PRIVILEGE
-------------------- ----------
FINANCE_USER         DELETE
FINANCE_USER         INSERT
FINANCE_USER         SELECT
FINANCE_USER         UPDATE
```

## 防止再次发生

### 1. 代码层面

确保所有 CDC 作业都使用以下配置：

```java
Properties debeziumProps = new Properties();
debeziumProps.setProperty("log.mining.flush.table.name", "flink_user.log_mining_flush");
```

### 2. 数据库层面

- ✅ `flink_user.log_mining_flush` 表已创建
- ✅ `finance_user` 已被授予访问权限
- ✅ `finance_user` 下的旧表已删除

### 3. 运维层面

定期检查是否有新的 `LOG_MINING_FLUSH` 表在业务 schema 下创建：

```bash
# 使用提供的验证脚本
docker exec -i oracle11g bash -c "..." < sql/verify-log-mining-flush.sql
```

## 相关文件

- `src/main/java/com/realtime/pipeline/CdcJobMain.java` - CDC 作业配置
- `sql/setup-flink-user-schema.sql` - flink_user schema 和表创建脚本
- `sql/verify-log-mining-flush.sql` - 验证脚本
- `sql/README-fix-log-mining-flush.md` - 原始修复文档

## 总结

### 修复前
- ❌ `finance_user.LOG_MINING_FLUSH` 存在
- ❌ `flink_user.LOG_MINING_FLUSH` 存在
- ❌ `finance_user` 无权限访问 `flink_user.log_mining_flush`
- ❌ 旧作业使用默认配置

### 修复后
- ✅ 只有 `flink_user.LOG_MINING_FLUSH` 存在
- ✅ `finance_user` 有权限访问 `flink_user.log_mining_flush`
- ✅ 代码配置正确
- ✅ 新作业将使用 `flink_user.log_mining_flush`

## 测试建议

1. **提交新的 CDC 任务**
   - 使用前端 UI 或 API 提交新任务
   - 验证任务使用 `flink_user.log_mining_flush`

2. **监控表创建**
   - 运行任务后，检查是否有新表在 `finance_user` 下创建
   - 使用 `sql/verify-log-mining-flush.sql` 脚本

3. **检查日志**
   - 查看 Flink JobManager 日志
   - 确认没有权限错误或表创建警告

## 定期检查和清理

如果 `finance_user` 下又出现了 `LOG_MINING_FLUSH` 表，使用以下脚本清理：

```bash
# 方法 1: 使用便捷脚本
./check-log-mining-flush.sh

# 方法 2: 直接执行 SQL 脚本
docker exec -i oracle11g bash -c "..." < sql/cleanup-duplicate-log-mining-flush.sql
```

**脚本功能**:
- 自动检测并删除 `finance_user.LOG_MINING_FLUSH`
- 验证 `flink_user.LOG_MINING_FLUSH` 存在
- 检查并修复 `finance_user` 的访问权限
- 显示当前状态

## 故障排查

### 如果 finance_user 下又出现 LOG_MINING_FLUSH 表

**原因**: 
1. 旧的作业仍在运行（在代码更新前提交的）
2. 权限配置丢失
3. Debezium 配置未正确传递

**解决**:
```bash
# 1. 运行清理脚本
./check-log-mining-flush.sh

# 2. 检查运行中的作业
curl http://localhost:8081/jobs/overview

# 3. 如果有旧作业，取消它们
curl -X PATCH http://localhost:8081/jobs/{job_id}

# 4. 验证配置
grep "log.mining.flush.table.name" src/main/java/com/realtime/pipeline/CdcJobMain.java
```

### 如果作业无法启动

1. 验证 `flink_user.log_mining_flush` 表存在
2. 验证 `finance_user` 有访问权限
3. 检查数据库连接配置

---

**修复日期**: 2026-03-06  
**修复人员**: Kiro AI Assistant  
**状态**: ✅ 完成
