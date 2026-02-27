# Oracle 连接超时问题修复

## 问题描述

Flink CDC 作业运行时出现 `SQLRecoverableException: No more data to read from socket` 错误，导致 CDC 流中断。

```
java.sql.SQLRecoverableException: No more data to read from socket
at oracle.jdbc.driver.T4CMAREngineNIO.prepareForUnmarshall(T4CMAREngineNIO.java:784)
```

## 根本原因

这是一个 **Oracle 数据库连接超时/断开**问题，可能由以下原因引起：

1. **数据库连接空闲超时**: Oracle 数据库或中间件（如防火墙）关闭了空闲连接
2. **网络不稳定**: 网络中断导致 TCP 连接断开
3. **LogMiner 会话超时**: Oracle LogMiner 会话超时自动关闭
4. **查询超时**: 长时间运行的查询被数据库终止
5. **连接池限制**: 数据库连接池达到上限

## 解决方案

### 1. 添加连接超时配置

```java
// 连接超时和重试配置
debeziumProps.setProperty("database.connection.timeout.ms", "60000");  // 连接超时 60 秒
debeziumProps.setProperty("database.query.timeout.ms", "600000");  // 查询超时 10 分钟
debeziumProps.setProperty("connect.timeout.ms", "30000");  // TCP 连接超时 30 秒
debeziumProps.setProperty("connect.keep.alive", "true");  // 启用 TCP keep-alive
debeziumProps.setProperty("connect.keep.alive.interval.ms", "60000");  // keep-alive 间隔 60 秒
```

### 2. 添加错误重试机制

```java
// 错误处理和重试
debeziumProps.setProperty("errors.max.retries", "10");  // 最大重试次数
debeziumProps.setProperty("errors.retry.delay.initial.ms", "1000");  // 初始重试延迟 1 秒
debeziumProps.setProperty("errors.retry.delay.max.ms", "60000");  // 最大重试延迟 60 秒
```

### 3. 优化 LogMiner 会话配置

```java
// LogMiner 会话配置
debeziumProps.setProperty("log.mining.session.max.ms", "0");  // 禁用会话超时（0 = 无限制）
debeziumProps.setProperty("log.mining.batch.size.default", "1000");  // 批量大小
debeziumProps.setProperty("log.mining.batch.size.min", "100");  // 最小批量大小
debeziumProps.setProperty("log.mining.batch.size.max", "10000");  // 最大批量大小
debeziumProps.setProperty("log.mining.sleep.time.default.ms", "1000");  // 默认休眠时间
debeziumProps.setProperty("log.mining.sleep.time.min.ms", "0");  // 最小休眠时间
debeziumProps.setProperty("log.mining.sleep.time.max.ms", "3000");  // 最大休眠时间
debeziumProps.setProperty("log.mining.sleep.time.increment.ms", "200");  // 休眠时间增量
```

## 配置说明

### 连接超时配置

| 配置项 | 值 | 说明 |
|--------|-----|------|
| database.connection.timeout.ms | 60000 | 数据库连接超时（60秒） |
| database.query.timeout.ms | 600000 | 查询超时（10分钟） |
| connect.timeout.ms | 30000 | TCP 连接超时（30秒） |
| connect.keep.alive | true | 启用 TCP keep-alive |
| connect.keep.alive.interval.ms | 60000 | keep-alive 间隔（60秒） |

### 重试配置

| 配置项 | 值 | 说明 |
|--------|-----|------|
| errors.max.retries | 10 | 最大重试次数 |
| errors.retry.delay.initial.ms | 1000 | 初始重试延迟（1秒） |
| errors.retry.delay.max.ms | 60000 | 最大重试延迟（60秒） |

### LogMiner 配置

| 配置项 | 值 | 说明 |
|--------|-----|------|
| log.mining.session.max.ms | 0 | 会话超时（0=无限制） |
| log.mining.batch.size.default | 1000 | 默认批量大小 |
| log.mining.batch.size.min | 100 | 最小批量大小 |
| log.mining.batch.size.max | 10000 | 最大批量大小 |
| log.mining.sleep.time.default.ms | 1000 | 默认休眠时间（1秒） |
| log.mining.sleep.time.min.ms | 0 | 最小休眠时间 |
| log.mining.sleep.time.max.ms | 3000 | 最大休眠时间（3秒） |
| log.mining.sleep.time.increment.ms | 200 | 休眠时间增量（200毫秒） |

## 工作原理

### TCP Keep-Alive

启用 TCP keep-alive 后，系统会定期发送探测包以保持连接活跃：
1. 每 60 秒发送一次 keep-alive 探测包
2. 如果连接断开，及时检测并重新连接
3. 防止防火墙或中间件关闭空闲连接

### 重试机制

当连接失败时，自动重试：
1. 第一次重试延迟 1 秒
2. 后续重试延迟逐渐增加（指数退避）
3. 最大重试延迟 60 秒
4. 最多重试 10 次

### LogMiner 会话管理

优化 LogMiner 会话以提高稳定性：
1. 禁用会话超时（`session.max.ms = 0`）
2. 动态调整批量大小（100-10000）
3. 自适应休眠时间（0-3000ms）
4. 根据负载自动调整

## 部署步骤

### 1. 更新代码

代码已更新，包含所有连接超时和重试配置。

### 2. 重新编译

```bash
mvn clean package -DskipTests
```

### 3. 部署新版本

```bash
# 取消当前作业
curl -X PATCH 'http://localhost:8081/jobs/<job-id>?mode=cancel'

# 复制新 JAR
docker cp target/realtime-data-pipeline-1.0.0-SNAPSHOT.jar flink-jobmanager:/opt/flink/usrlib/realtime-data-pipeline.jar
docker cp target/realtime-data-pipeline-1.0.0-SNAPSHOT.jar realtime-pipeline-taskmanager-1:/opt/flink/usrlib/realtime-data-pipeline.jar

# 提交新作业
./start-flink-cdc-job-safe.sh
```

## 验证

### 1. 检查作业状态

```bash
curl -s http://localhost:8081/jobs/<job-id> | python3 -c "import sys, json; print(json.load(sys.stdin)['state'])"
```

### 2. 监控异常

```bash
curl -s http://localhost:8081/jobs/<job-id>/exceptions | python3 -c "import sys, json; print(len(json.load(sys.stdin).get('all-exceptions', [])))"
```

### 3. 查看日志

```bash
docker logs realtime-pipeline-taskmanager-1 2>&1 | grep -i "socket\|timeout\|retry"
```

## 预防措施

### 1. Oracle 数据库配置

在 Oracle 数据库端增加连接超时：

```sql
-- 增加空闲超时时间（单位：分钟）
ALTER SYSTEM SET SQLNET.EXPIRE_TIME=10;

-- 增加会话超时时间
ALTER PROFILE DEFAULT LIMIT IDLE_TIME UNLIMITED;
```

### 2. 网络配置

确保网络稳定：
- 使用稳定的网络连接
- 避免使用 VPN 或代理（如果可能）
- 配置防火墙允许长连接

### 3. 监控和告警

设置监控告警：
- 监控连接失败次数
- 监控重试次数
- 监控 CDC 延迟

## 故障排查

### 问题 1: 仍然出现连接超时

**解决方案**:
1. 增加 `database.query.timeout.ms` 到更大的值（如 1800000 = 30分钟）
2. 检查 Oracle 数据库日志
3. 检查网络连接稳定性

### 问题 2: 重试次数过多

**解决方案**:
1. 检查数据库是否过载
2. 增加 `errors.retry.delay.max.ms`
3. 减少并行度

### 问题 3: LogMiner 会话频繁断开

**解决方案**:
1. 检查 Oracle 归档日志是否正常
2. 增加 `log.mining.batch.size.max`
3. 调整 `log.mining.sleep.time` 参数

## 性能影响

### 优点
- ✅ 提高连接稳定性
- ✅ 自动恢复连接故障
- ✅ 减少人工干预

### 缺点
- ⚠️ 重试会增加延迟
- ⚠️ Keep-alive 会增加网络流量（很小）
- ⚠️ 更长的超时时间可能延迟错误检测

## 总结

通过添加连接超时、重试机制和 LogMiner 优化配置，可以有效解决 Oracle 连接超时问题。这些配置使 CDC 流更加稳定和可靠，能够自动处理临时的网络或数据库问题。

## 相关文件

- `src/main/java/com/realtime/pipeline/FlinkCDC3App.java` - 主应用程序（已更新）
- `start-flink-cdc-job-safe.sh` - 安全启动脚本
- `JDBC_DRIVER_LOADING_SOLUTION.md` - JDBC 驱动加载问题文档
