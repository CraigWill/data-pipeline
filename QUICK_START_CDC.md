# Oracle CDC 快速入门指南

## 当前状态

✅ **轮询方式 CDC 正在运行**
- 应用：JdbcCDCApp
- 状态：运行中
- 输出：`output/cdc/`
- 延迟：10 秒
- 捕获操作：INSERT

## 两种 CDC 方式

### 方式 1: 轮询方式（当前使用）

**优点：**
- ✅ 无需配置数据库
- ✅ 立即可用
- ✅ 简单易用

**缺点：**
- ❌ 只能捕获 INSERT
- ❌ 延迟较高（10秒）
- ❌ 对数据库有查询压力

**查看输出：**
```bash
# 查看输出文件
ls -la output/cdc/

# 查看最新数据
tail -20 output/cdc/2026-02-12--*/part-*
```

---

### 方式 2: LogMiner 方式（需要配置）

**优点：**
- ✅ 实时捕获（秒级）
- ✅ 捕获所有操作（INSERT/UPDATE/DELETE）
- ✅ 对数据库压力小
- ✅ 提供完整变更历史

**缺点：**
- ❌ 需要 DBA 权限配置数据库
- ❌ 需要额外磁盘空间

## 升级到 LogMiner CDC

### 前提条件检查

运行检查脚本：
```bash
./check-oracle-cdc-simple.sh
```

### 配置步骤

#### 步骤 1: 连接到 Oracle 数据库

如果你的 Oracle 在 Docker 容器中：
```bash
# 查找 Oracle 容器名称
docker ps | grep oracle

# 连接到容器
docker exec -it <oracle-container-name> sqlplus / as sysdba
```

如果你的 Oracle 在本地或远程服务器：
```bash
sqlplus system/helowin@localhost:1521/helowin as sysdba
```

#### 步骤 2: 启用归档日志

```sql
-- 检查当前状态
SELECT LOG_MODE FROM V$DATABASE;

-- 如果返回 NOARCHIVELOG，需要启用
SHUTDOWN IMMEDIATE;
STARTUP MOUNT;
ALTER DATABASE ARCHIVELOG;
ALTER DATABASE OPEN;

-- 验证
SELECT LOG_MODE FROM V$DATABASE;
-- 应该返回: ARCHIVELOG
```

#### 步骤 3: 启用补充日志

```sql
-- 启用补充日志
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;

-- 验证
SELECT SUPPLEMENTAL_LOG_DATA_MIN, SUPPLEMENTAL_LOG_DATA_ALL 
FROM V$DATABASE;
-- 应该返回: YES 或 IMPLICIT
```

#### 步骤 4: 授予权限（如果使用 system 用户可跳过）

```sql
-- 如果使用专用 CDC 用户
GRANT SELECT ANY TABLE TO system;
GRANT EXECUTE_CATALOG_ROLE TO system;
GRANT SELECT ANY TRANSACTION TO system;
GRANT LOGMINING TO system;
```

#### 步骤 5: 部署 LogMiner CDC

```bash
# 1. 停止当前作业
docker exec flink-jobmanager flink list -r
docker exec flink-jobmanager flink cancel <job-id>

# 2. 提交 Oracle CDC 作业
./submit-oracle-cdc.sh

# 3. 查看作业状态
docker exec flink-jobmanager flink list -r

# 4. 查看日志
docker logs -f realtime-pipeline-taskmanager-1
```

## 监控和验证

### 查看 Flink Web UI
```
http://localhost:8081
```

### 查看输出文件
```bash
# 容器内
docker exec realtime-pipeline-taskmanager-1 ls -la /opt/flink/output/cdc/

# 复制到本地
docker cp realtime-pipeline-taskmanager-1:/opt/flink/output/cdc/. output/cdc/

# 查看内容
cat output/cdc/2026-02-12--*/part-*
```

### 查看日志
```bash
# JobManager 日志
docker logs -f flink-jobmanager

# TaskManager 日志
docker logs -f realtime-pipeline-taskmanager-1
```

## 输出格式对比

### 轮询方式输出（CSV）
```csv
2026-02-12 03:30:06,trans_info,INSERT,23,"sample_data_23"
2026-02-12 03:30:16,trans_info,INSERT,24,"sample_data_24"
```

### LogMiner 方式输出（JSON）
```json
{
  "before": null,
  "after": {
    "id": 1,
    "name": "test",
    "value": 100
  },
  "source": {
    "connector": "oracle",
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

## 故障排查

### 问题 1: 无法连接到数据库

**检查：**
```bash
# 测试端口
nc -z localhost 1521

# 检查环境变量
cat .env | grep DATABASE
```

### 问题 2: 归档日志空间不足

**错误：**
```
ORA-00257: archiver error
```

**解决：**
```sql
-- 检查空间
SELECT * FROM V$RECOVERY_FILE_DEST;

-- 清理旧日志（保留 7 天）
-- 使用 RMAN
DELETE ARCHIVELOG ALL COMPLETED BEFORE 'SYSDATE-7';
```

### 问题 3: 权限不足

**错误：**
```
ORA-01031: insufficient privileges
```

**解决：**
```sql
-- 授予必要权限
GRANT SELECT ANY TABLE TO system;
GRANT EXECUTE_CATALOG_ROLE TO system;
GRANT SELECT ANY TRANSACTION TO system;
GRANT LOGMINING TO system;
```

## 性能调优

### 调整轮询间隔（轮询方式）

编辑 `.env`：
```bash
POLL_INTERVAL_SECONDS=5  # 减少到 5 秒
```

重启服务：
```bash
docker-compose restart
./submit-to-flink.sh
```

### 调整 Checkpoint 间隔（LogMiner 方式）

编辑 `docker-compose.yml`：
```yaml
environment:
  - CHECKPOINT_INTERVAL=30000  # 30 秒
```

## 常用命令

```bash
# 查看所有作业
docker exec flink-jobmanager flink list -a

# 取消作业
docker exec flink-jobmanager flink cancel <job-id>

# 查看容器状态
docker-compose ps

# 重启服务
docker-compose restart

# 查看日志
docker-compose logs -f

# 清理输出
rm -rf output/cdc/*
```

## 参考文档

- [CDC 方式对比](CDC_COMPARISON.md)
- [Oracle LogMiner 详细配置](docs/ORACLE_CDC_LOGMINER.md)
- [本地 DataHub 部署](docs/LOCAL_DATAHUB_DEPLOYMENT.md)
- [部署指南](docs/DEPLOYMENT.md)

## 获取帮助

如果遇到问题：
1. 查看日志：`docker logs -f realtime-pipeline-taskmanager-1`
2. 检查配置：`./check-oracle-cdc-simple.sh`
3. 查看文档：`docs/ORACLE_CDC_LOGMINER.md`
