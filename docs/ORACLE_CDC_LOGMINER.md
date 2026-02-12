# Oracle CDC 配置指南（基于 LogMiner）

## 概述

本文档介绍如何配置 Oracle 数据库以支持基于 LogMiner 的 CDC（Change Data Capture），以及如何使用 `OracleCDCApp` 应用程序实时捕获数据库变更。

## 架构对比

### 轮询方式（JdbcCDCApp）
- ✅ 简单易用，无需特殊配置
- ❌ 延迟较高（取决于轮询间隔）
- ❌ 只能捕获 INSERT 操作
- ❌ 对数据库有持续的查询压力
- ❌ 无法捕获 UPDATE/DELETE 操作

### LogMiner 方式（OracleCDCApp）
- ✅ 实时捕获变更（秒级延迟）
- ✅ 捕获所有操作（INSERT/UPDATE/DELETE）
- ✅ 对数据库压力小
- ✅ 提供完整的变更历史
- ❌ 需要配置归档日志
- ❌ 需要额外的磁盘空间

## 前置条件

### 1. Oracle 数据库版本
- Oracle 11g 或更高版本
- 支持 LogMiner 功能

### 2. 数据库权限
需要以下权限：
```sql
GRANT CREATE SESSION TO cdc_user;
GRANT SELECT ANY TABLE TO cdc_user;
GRANT EXECUTE_CATALOG_ROLE TO cdc_user;
GRANT SELECT ANY TRANSACTION TO cdc_user;
GRANT LOGMINING TO cdc_user;
```

### 3. 磁盘空间
- 归档日志需要额外的磁盘空间
- 建议至少预留 10GB 空间用于归档日志

## 配置步骤

### 步骤 1: 检查当前配置

运行检查脚本：
```bash
./check-oracle-cdc-status.sh
```

### 步骤 2: 启用归档日志模式

如果数据库未启用归档日志，需要执行以下操作：

```sql
-- 以 SYSDBA 身份连接
sqlplus / as sysdba

-- 关闭数据库
SHUTDOWN IMMEDIATE;

-- 启动到 mount 状态
STARTUP MOUNT;

-- 启用归档日志
ALTER DATABASE ARCHIVELOG;

-- 打开数据库
ALTER DATABASE OPEN;

-- 验证
SELECT LOG_MODE FROM V$DATABASE;
-- 应该返回 ARCHIVELOG
```

### 步骤 3: 启用补充日志

补充日志是 LogMiner CDC 的必需配置：

```sql
-- 启用数据库级别的补充日志
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA;

-- 启用所有列的补充日志（推荐）
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;

-- 验证
SELECT SUPPLEMENTAL_LOG_DATA_MIN, SUPPLEMENTAL_LOG_DATA_ALL 
FROM V$DATABASE;
-- 应该返回 YES 或 IMPLICIT
```

### 步骤 4: 创建 CDC 用户（可选但推荐）

```sql
-- 创建专用的 CDC 用户
CREATE USER cdc_user IDENTIFIED BY cdc_password;

-- 授予必要的权限
GRANT CREATE SESSION TO cdc_user;
GRANT SELECT ANY TABLE TO cdc_user;
GRANT EXECUTE_CATALOG_ROLE TO cdc_user;
GRANT SELECT ANY TRANSACTION TO cdc_user;
GRANT LOGMINING TO cdc_user;

-- 授予对特定 schema 的访问权限
GRANT SELECT ON finance_user.trans_info TO cdc_user;
```

### 步骤 5: 配置环境变量

更新 `.env` 文件：
```bash
# 数据库配置
DATABASE_HOST=host.docker.internal
DATABASE_PORT=1521
DATABASE_USERNAME=system  # 或 cdc_user
DATABASE_PASSWORD=helowin  # 或 cdc_password
DATABASE_SID=helowin
DATABASE_SCHEMA=finance_user
DATABASE_TABLES=trans_info

# 输出配置
OUTPUT_PATH=/opt/flink/output/cdc
```

## 使用方法

### 方法 1: 重新构建并部署

```bash
# 1. 重新构建应用
mvn clean package -DskipTests

# 2. 重新构建 Docker 镜像
./docker/build-images.sh

# 3. 停止现有服务
docker-compose down

# 4. 启动服务
docker-compose up -d

# 5. 提交 Oracle CDC 作业
./submit-oracle-cdc.sh
```

### 方法 2: 直接提交作业

```bash
# 提交 Oracle CDC 作业到 Flink
docker exec -it flink-jobmanager flink run \
  -c com.realtime.pipeline.OracleCDCApp \
  /opt/flink/lib/realtime-data-pipeline-1.0.0-SNAPSHOT.jar
```

## 输出格式

LogMiner CDC 输出的是 JSON 格式的 Debezium 变更事件：

```json
{
  "before": null,
  "after": {
    "id": 1,
    "name": "test",
    "value": 100,
    "timestamp": 1234567890
  },
  "source": {
    "version": "2.4.2",
    "connector": "oracle",
    "name": "oracle_cdc",
    "ts_ms": 1234567890123,
    "snapshot": "false",
    "db": "helowin",
    "schema": "finance_user",
    "table": "trans_info",
    "scn": "12345678"
  },
  "op": "c",
  "ts_ms": 1234567890123
}
```

操作类型：
- `c`: CREATE (INSERT)
- `u`: UPDATE
- `d`: DELETE
- `r`: READ (初始快照)

## 监控和维护

### 检查作业状态

访问 Flink Web UI：
```
http://localhost:8081
```

### 查看输出文件

```bash
# 查看容器内的输出
docker exec realtime-pipeline-taskmanager-1 ls -la /opt/flink/output/cdc/

# 复制到本地
docker cp realtime-pipeline-taskmanager-1:/opt/flink/output/cdc/. output/cdc/
```

### 监控归档日志

```sql
-- 检查归档日志空间使用
SELECT * FROM V$RECOVERY_FILE_DEST;

-- 查看最近的归档日志
SELECT NAME, SEQUENCE#, FIRST_TIME, NEXT_TIME 
FROM V$ARCHIVED_LOG 
ORDER BY FIRST_TIME DESC;
```

### 清理归档日志

```bash
# 使用 RMAN 清理 7 天前的归档日志
rman target /
RMAN> DELETE ARCHIVELOG ALL COMPLETED BEFORE 'SYSDATE-7';
```

## 故障排查

### 问题 1: 归档日志未启用

**错误信息：**
```
ORA-00257: archiver error. Connect internal only, until freed.
```

**解决方案：**
1. 检查归档日志目录空间
2. 清理旧的归档日志
3. 增加归档日志目录空间

### 问题 2: 补充日志未启用

**错误信息：**
```
Supplemental logging not properly configured
```

**解决方案：**
```sql
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;
```

### 问题 3: 权限不足

**错误信息：**
```
ORA-01031: insufficient privileges
```

**解决方案：**
```sql
GRANT SELECT ANY TABLE TO cdc_user;
GRANT EXECUTE_CATALOG_ROLE TO cdc_user;
GRANT SELECT ANY TRANSACTION TO cdc_user;
GRANT LOGMINING TO cdc_user;
```

### 问题 4: LogMiner 性能问题

**症状：**
- CDC 延迟增加
- 数据库 CPU 使用率高

**解决方案：**
1. 调整 LogMiner 配置：
```properties
log.mining.strategy=online_catalog
log.mining.continuous.mine=true
```

2. 增加 Flink 并行度
3. 优化归档日志保留策略

## 性能优化

### 1. LogMiner 配置

```properties
# 使用在线目录（推荐）
log.mining.strategy=online_catalog

# 启用连续挖掘
log.mining.continuous.mine=true

# 批量大小
log.mining.batch.size.default=1000
```

### 2. Flink 配置

```yaml
# 增加并行度
parallelism.default: 4

# 调整 checkpoint 间隔
execution.checkpointing.interval: 60s

# 使用 RocksDB 状态后端
state.backend: rocksdb
```

### 3. 数据库优化

```sql
-- 增加 redo log 大小
ALTER DATABASE ADD LOGFILE GROUP 4 
  ('/u01/app/oracle/oradata/redo04.log') SIZE 500M;

-- 调整归档日志压缩
ALTER SYSTEM SET LOG_ARCHIVE_FORMAT='%t_%s_%r.arc' SCOPE=SPFILE;
```

## 最佳实践

1. **使用专用 CDC 用户**：不要使用 SYSTEM 或 SYS 用户
2. **定期清理归档日志**：避免磁盘空间不足
3. **监控 LogMiner 性能**：关注 CPU 和内存使用
4. **配置告警**：监控 CDC 延迟和错误
5. **定期备份**：备份 Flink checkpoint 和 savepoint
6. **测试故障恢复**：定期测试从 checkpoint 恢复

## 参考资料

- [Oracle LogMiner 官方文档](https://docs.oracle.com/en/database/oracle/oracle-database/19/sutil/oracle-logminer-utility.html)
- [Flink CDC Connectors](https://github.com/ververica/flink-cdc-connectors)
- [Debezium Oracle Connector](https://debezium.io/documentation/reference/connectors/oracle.html)
