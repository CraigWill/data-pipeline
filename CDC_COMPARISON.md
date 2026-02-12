# CDC 实现方式对比

## 概述

本项目提供了两种 Oracle 数据库 CDC（Change Data Capture）实现方式：

1. **轮询方式（JdbcCDCApp）** - 简单但功能有限
2. **LogMiner 方式（OracleCDCApp）** - 功能完整的实时 CDC

## 方式对比

| 特性 | 轮询方式 (JdbcCDCApp) | LogMiner 方式 (OracleCDCApp) |
|------|----------------------|----------------------------|
| **实现方式** | JDBC 定期查询 | Oracle LogMiner |
| **延迟** | 高（取决于轮询间隔，默认 10 秒） | 低（秒级实时） |
| **捕获操作** | 仅 INSERT | INSERT/UPDATE/DELETE |
| **数据库压力** | 高（持续查询） | 低（读取日志） |
| **配置复杂度** | 简单 | 中等 |
| **数据库要求** | 无特殊要求 | 需要启用归档日志和补充日志 |
| **磁盘空间** | 无额外要求 | 需要存储归档日志 |
| **输出格式** | CSV | JSON (Debezium 格式) |
| **适用场景** | 开发测试、简单场景 | 生产环境、完整 CDC |

## 使用指南

### 方式 1: 轮询方式（JdbcCDCApp）

#### 优点
- ✅ 配置简单，无需特殊数据库设置
- ✅ 适合快速开发和测试
- ✅ 不需要额外的磁盘空间

#### 缺点
- ❌ 只能捕获 INSERT 操作
- ❌ 延迟较高
- ❌ 对数据库有持续的查询压力

#### 使用步骤

1. **配置环境变量** (`.env`)
```bash
DATABASE_HOST=host.docker.internal
DATABASE_PORT=1521
DATABASE_USERNAME=system
DATABASE_PASSWORD=helowin
DATABASE_SID=helowin
DATABASE_SCHEMA=finance_user
DATABASE_TABLES=trans_info
OUTPUT_PATH=/opt/flink/output/cdc
POLL_INTERVAL_SECONDS=10
```

2. **构建和部署**
```bash
# 构建应用
mvn clean package -DskipTests

# 构建 Docker 镜像
./docker/build-images.sh

# 启动服务
docker-compose up -d

# 提交作业
./submit-to-flink.sh
```

3. **查看输出**
```bash
# 查看输出文件
ls -la output/cdc/

# 查看文件内容
cat output/cdc/2026-02-12--*/part-*
```

#### 输出格式
```csv
2026-02-12 03:30:06,trans_info,INSERT,23,"sample_data_23"
2026-02-12 03:30:16,trans_info,INSERT,24,"sample_data_24"
```

格式：`时间戳,表名,操作类型,字段1,字段2,...`

---

### 方式 2: LogMiner 方式（OracleCDCApp）

#### 优点
- ✅ 实时捕获所有变更（INSERT/UPDATE/DELETE）
- ✅ 延迟低（秒级）
- ✅ 对数据库压力小
- ✅ 提供完整的变更历史

#### 缺点
- ❌ 需要配置归档日志
- ❌ 需要额外的磁盘空间
- ❌ 配置相对复杂

#### 使用步骤

1. **检查数据库配置**
```bash
./check-oracle-cdc-status.sh
```

2. **配置 Oracle 数据库**

如果归档日志未启用，需要执行：
```sql
-- 以 SYSDBA 身份连接
sqlplus / as sysdba

-- 启用归档日志
SHUTDOWN IMMEDIATE;
STARTUP MOUNT;
ALTER DATABASE ARCHIVELOG;
ALTER DATABASE OPEN;

-- 启用补充日志
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;

-- 验证
SELECT LOG_MODE FROM V$DATABASE;
SELECT SUPPLEMENTAL_LOG_DATA_MIN, SUPPLEMENTAL_LOG_DATA_ALL FROM V$DATABASE;
```

3. **配置环境变量** (`.env`)
```bash
DATABASE_HOST=host.docker.internal
DATABASE_PORT=1521
DATABASE_USERNAME=system
DATABASE_PASSWORD=helowin
DATABASE_SID=helowin
DATABASE_SCHEMA=finance_user
DATABASE_TABLES=trans_info
OUTPUT_PATH=/opt/flink/output/cdc
```

4. **构建和部署**
```bash
# 构建应用
mvn clean package -DskipTests

# 构建 Docker 镜像
./docker/build-images.sh

# 启动服务
docker-compose up -d

# 提交 Oracle CDC 作业
./submit-oracle-cdc.sh
```

5. **查看输出**
```bash
# 查看输出文件
docker exec realtime-pipeline-taskmanager-1 ls -la /opt/flink/output/cdc/

# 复制到本地
docker cp realtime-pipeline-taskmanager-1:/opt/flink/output/cdc/. output/cdc/
```

#### 输出格式
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

## 选择建议

### 使用轮询方式（JdbcCDCApp）如果：
- 只需要捕获 INSERT 操作
- 对延迟要求不高（10 秒以上可接受）
- 快速开发和测试
- 无法修改数据库配置

### 使用 LogMiner 方式（OracleCDCApp）如果：
- 需要捕获所有类型的变更（INSERT/UPDATE/DELETE）
- 需要低延迟（秒级）
- 生产环境使用
- 可以配置数据库归档日志

## 故障排查

### 轮询方式常见问题

1. **数据库连接失败**
   - 检查 `DATABASE_HOST` 是否正确（容器内使用 `host.docker.internal`）
   - 检查数据库端口和 SID
   - 检查用户名和密码

2. **没有输出文件**
   - 检查数据库表是否有新数据
   - 查看 TaskManager 日志：`docker logs realtime-pipeline-taskmanager-1`

### LogMiner 方式常见问题

1. **归档日志未启用**
   ```
   ORA-00257: archiver error
   ```
   解决：启用归档日志模式

2. **补充日志未启用**
   ```
   Supplemental logging not properly configured
   ```
   解决：`ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;`

3. **权限不足**
   ```
   ORA-01031: insufficient privileges
   ```
   解决：授予必要的权限（参见 `docs/ORACLE_CDC_LOGMINER.md`）

## 监控

### Flink Web UI
访问 http://localhost:8081 查看：
- 作业状态
- 吞吐量
- Checkpoint 状态
- 任务指标

### 查看日志
```bash
# JobManager 日志
docker logs -f flink-jobmanager

# TaskManager 日志
docker logs -f realtime-pipeline-taskmanager-1
```

### 查看作业列表
```bash
docker exec flink-jobmanager flink list -r
```

## 参考文档

- [Oracle CDC LogMiner 详细配置](docs/ORACLE_CDC_LOGMINER.md)
- [本地 DataHub 部署](docs/LOCAL_DATAHUB_DEPLOYMENT.md)
- [CDC 快速入门](docs/CDC_QUICKSTART.md)
- [部署指南](docs/DEPLOYMENT.md)
