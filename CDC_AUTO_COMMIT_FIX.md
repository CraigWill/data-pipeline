# Oracle CDC Auto-Commit 问题修复

## 问题描述

Oracle CDC 作业在 snapshot 阶段失败，错误信息：
```
io.debezium.DebeziumException: Cannot execute without committing because auto-commit is enabled
```

## 根本原因

Debezium 的 LogMiner 需要手动控制事务提交，但 Oracle JDBC 连接默认启用了自动提交（auto-commit）。

## 解决方案

在 `OracleCDCApp.java` 中添加 Debezium 配置，禁用自动提交：

```java
// 数据库连接配置 - 禁用自动提交
debeziumProps.setProperty("database.connection.adapter", "logminer");
debeziumProps.setProperty("database.autocommit", "false");
```

## 配置更新

### 1. `.env` 文件
确保使用大写的 Schema 和表名（Oracle 默认大写）：
```bash
DATABASE_SCHEMA=FINANCE_USER
DATABASE_TABLES=TRANS_INFO
```

### 2. `OracleCDCApp.java`
添加了以下配置：
- `database.connection.adapter=logminer`
- `database.autocommit=false`

## 当前状态

- ✅ 应用程序已重新构建
- ✅ Docker 镜像已更新
- ✅ 服务已重新部署
- ✅ 新作业已提交 (Job ID: 326ddd2ca2e3702990a02d54e9bf82e5)
- ⏳ 作业正在运行，等待 snapshot 完成

## 下一步

1. 等待 snapshot 阶段完成（监控特定表 FINANCE_USER.TRANS_INFO）
2. 插入测试数据验证 CDC 功能
3. 检查输出文件生成

## 测试命令

```bash
# 检查作业状态
curl -s http://localhost:8081/jobs/326ddd2ca2e3702990a02d54e9bf82e5 | python3 -m json.tool

# 查看作业日志
docker logs realtime-pipeline-taskmanager-1 --tail 100

# 检查输出文件
find output/cdc -type f -name "part-*"

# 插入测试数据（需要先确认表结构）
docker exec oracle11g bash -c "source /home/oracle/.bash_profile && sqlplus system/helowin@helowin @/tmp/create-test-table.sql"
```

## 注意事项

1. Oracle 表名和 Schema 名默认是大写的
2. 需要确认 FINANCE_USER.TRANS_INFO 表是否存在及其结构
3. Snapshot 阶段可能需要较长时间，特别是如果监控多个表
4. 建议只监控必要的表以加快 snapshot 速度
