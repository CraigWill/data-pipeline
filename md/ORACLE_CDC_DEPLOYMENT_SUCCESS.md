# Oracle CDC 部署成功指南

## ✅ 依赖冲突已解决！

我们成功解决了 Flink CDC Oracle 连接器的依赖冲突问题：

### 解决的问题
1. ✅ **Infinispan 类加载冲突** - 通过排除 Infinispan 依赖解决
2. ✅ **Guava 版本不匹配** - 添加了正确版本的 flink-shaded-guava

### 修改的文件
- `pom.xml` - 添加了依赖排除和 Guava 依赖

## 🎯 当前状态

### Oracle CDC 应用
- **状态**: 已成功部署到 Flink
- **作业 ID**: eb8ebe68cda4314779da8cd4a074f495
- **问题**: Oracle 数据库未启用归档日志模式

### 错误信息
```
io.debezium.DebeziumException: The Oracle server is not configured to use 
a archive log LOG_MODE, which is required for this connector to work properly. 
Change the Oracle configuration to use a LOG_MODE=ARCHIVELOG and restart the connector.
```

## 📋 下一步：配置 Oracle 数据库

要使 Oracle CDC (LogMiner) 正常工作，需要在 Oracle 数据库中执行以下配置：

### 步骤 1: 连接到 Oracle 数据库

#### 如果 Oracle 在 Docker 容器中：
```bash
# 查找 Oracle 容器
docker ps | grep oracle

# 进入容器
docker exec -it <oracle-container-name> bash

# 连接数据库
sqlplus / as sysdba
```

#### 如果 Oracle 在本地或远程：
```bash
sqlplus system/helowin@localhost:1521/helowin as sysdba
```

### 步骤 2: 启用归档日志模式

```sql
-- 1. 检查当前状态
SELECT LOG_MODE FROM V$DATABASE;
-- 如果返回 NOARCHIVELOG，需要启用

-- 2. 关闭数据库
SHUTDOWN IMMEDIATE;

-- 3. 启动到 mount 状态
STARTUP MOUNT;

-- 4. 启用归档日志
ALTER DATABASE ARCHIVELOG;

-- 5. 打开数据库
ALTER DATABASE OPEN;

-- 6. 验证
SELECT LOG_MODE FROM V$DATABASE;
-- 应该返回: ARCHIVELOG
```

### 步骤 3: 启用补充日志

```sql
-- 启用补充日志（必需）
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;

-- 验证
SELECT SUPPLEMENTAL_LOG_DATA_MIN, SUPPLEMENTAL_LOG_DATA_ALL 
FROM V$DATABASE;
-- 应该返回: YES 或 IMPLICIT
```

### 步骤 4: 重启 CDC 作业

配置完成后，Oracle CDC 作业会自动重试并成功启动。

或者手动重启：
```bash
# 取消当前作业
docker exec flink-jobmanager flink cancel eb8ebe68cda4314779da8cd4a074f495

# 重新提交
./submit-oracle-cdc.sh
```

## 🔍 验证部署

### 检查作业状态
```bash
# 通过 Web UI
open http://localhost:8081

# 通过命令行
docker exec flink-jobmanager flink list -r
```

### 查看日志
```bash
# TaskManager 日志
docker logs -f realtime-pipeline-taskmanager-1

# 查找成功消息
docker logs realtime-pipeline-taskmanager-1 2>&1 | grep -i "oracle\|cdc\|logminer"
```

### 查看输出
```bash
# 容器内
docker exec realtime-pipeline-taskmanager-1 ls -la /opt/flink/output/cdc/

# 复制到本地
docker cp realtime-pipeline-taskmanager-1:/opt/flink/output/cdc/. output/cdc/

# 查看内容
cat output/cdc/2026-02-12--*/part-*
```

## 📊 预期输出格式

Oracle CDC 会输出 JSON 格式的 Debezium 变更事件：

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
    "ts_ms": 1770875509562,
    "snapshot": "false",
    "db": "helowin",
    "schema": "finance_user",
    "table": "trans_info",
    "scn": "12345678"
  },
  "op": "c",
  "ts_ms": 1770875509562
}
```

### 操作类型
- `c`: CREATE (INSERT)
- `u`: UPDATE
- `d`: DELETE
- `r`: READ (初始快照)

## 🎉 成功标志

当 Oracle 数据库配置完成后，你会看到：

1. **Flink Web UI** 显示作业状态为 RUNNING
2. **TaskManager 日志** 显示：
   ```
   INFO  - Starting Oracle LogMiner session
   INFO  - LogMiner started successfully
   ```
3. **输出目录** 开始生成 JSON 文件

## 🔧 故障排查

### 问题 1: 归档日志空间不足
```
ORA-00257: archiver error
```

**解决方案：**
```sql
-- 检查空间
SELECT * FROM V$RECOVERY_FILE_DEST;

-- 清理旧日志（保留 7 天）
DELETE ARCHIVELOG ALL COMPLETED BEFORE 'SYSDATE-7';
```

### 问题 2: 权限不足
```
ORA-01031: insufficient privileges
```

**解决方案：**
```sql
GRANT SELECT ANY TABLE TO system;
GRANT EXECUTE_CATALOG_ROLE TO system;
GRANT SELECT ANY TRANSACTION TO system;
GRANT LOGMINING TO system;
```

### 问题 3: 作业持续重启
检查 TaskManager 日志查看具体错误：
```bash
docker logs realtime-pipeline-taskmanager-1 2>&1 | grep -A 10 "ERROR\|Exception"
```

## 📚 相关文档

- [Oracle CDC 配置 SQL](configure-oracle-for-cdc.sql)
- [CDC 方式对比](CDC_COMPARISON.md)
- [快速入门指南](QUICK_START_CDC.md)
- [详细配置指南](docs/ORACLE_CDC_LOGMINER.md)

## 💡 提示

1. **归档日志需要磁盘空间** - 确保有足够的空间（建议至少 10GB）
2. **启用归档日志需要重启数据库** - 请在维护窗口期间执行
3. **补充日志可以在线启用** - 不需要重启数据库
4. **定期清理归档日志** - 避免磁盘空间不足

## 🎯 总结

✅ **依赖冲突已解决** - Flink CDC 连接器可以正常加载  
⏳ **等待数据库配置** - 需要启用 Oracle 归档日志模式  
🚀 **准备就绪** - 配置完成后 CDC 将自动开始工作

---

**下一步行动：** 按照上述步骤配置 Oracle 数据库，然后 Oracle CDC 将自动开始捕获数据变更！
