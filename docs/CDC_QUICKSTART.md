# CDC 快速启动指南

本指南说明如何使用 CDC 应用程序监控数据库表变更并生成 CSV 文件。

## 功能说明

CDC 应用程序会：
1. 读取 `.env` 文件中的数据库配置
2. 连接到指定的数据库
3. 监控指定的表
4. 当表发生变更时，将变更数据写入 CSV 文件

## 前置条件

1. Java 11+
2. Maven 3.6+
3. 数据库访问权限
4. （可选）Flink 集群

## 配置

编辑 `.env` 文件，配置数据库连接信息：

```bash
# 数据库配置
DATABASE_HOST=localhost
DATABASE_PORT=1521
DATABASE_USERNAME=finance_user
DATABASE_PASSWORD=password
DATABASE_SCHEMA=helowin
DATABASE_TABLES=trans_info

# 输出配置
OUTPUT_PATH=./output/cdc
POLL_INTERVAL_SECONDS=10
```

### 配置说明

| 参数 | 说明 | 默认值 |
|------|------|--------|
| DATABASE_HOST | 数据库主机地址 | localhost |
| DATABASE_PORT | 数据库端口 | 1521 |
| DATABASE_USERNAME | 数据库用户名 | finance_user |
| DATABASE_PASSWORD | 数据库密码 | password |
| DATABASE_SCHEMA | 数据库 Schema | helowin |
| DATABASE_TABLES | 监控的表（逗号分隔） | trans_info |
| OUTPUT_PATH | CSV 文件输出路径 | ./output/cdc |
| POLL_INTERVAL_SECONDS | 轮询间隔（秒） | 10 |

## 启动方式

### 方式 1: 本地运行（推荐用于测试）

```bash
./start-cdc.sh
```

这将：
- 加载 `.env` 配置
- 启动 CDC 应用程序
- 将 CSV 文件写入 `OUTPUT_PATH` 目录

### 方式 2: 提交到 Flink 集群

```bash
# 确保 Flink 集群正在运行
docker compose ps

# 提交作业
docker exec flink-jobmanager flink run \
  -c com.realtime.pipeline.JdbcCDCApp \
  /opt/flink/lib/realtime-data-pipeline.jar
```

访问 Flink Web UI 查看作业状态: http://localhost:8081

## 输出格式

CSV 文件格式：

```csv
timestamp,table_name,operation,column1,column2,column3,...
2026-02-12 08:30:00,trans_info,INSERT,"value1","value2","value3",...
2026-02-12 08:30:10,trans_info,INSERT,"value4","value5","value6",...
```

### 字段说明

- `timestamp`: 变更检测时间
- `table_name`: 表名
- `operation`: 操作类型（INSERT/UPDATE/DELETE）
- `column1, column2, ...`: 表的各列数据

## 输出文件

文件保存在 `OUTPUT_PATH` 目录下，文件名格式：

```
part-<subtask>-<rolling-number>
```

例如：
```
output/cdc/part-0-0
output/cdc/part-0-1
output/cdc/part-0-2
```

### 文件滚动策略

- 每 5 分钟滚动一次
- 不活动 2 分钟后滚动
- 文件大小达到 128MB 时滚动

## 监控表变更

### 实时模式

如果能成功连接到数据库，应用程序会：
1. 每隔 `POLL_INTERVAL_SECONDS` 秒轮询一次
2. 检测新增的记录
3. 将变更写入 CSV 文件

### 模拟模式

如果无法连接到数据库，应用程序会：
1. 自动切换到模拟模式
2. 每隔 `POLL_INTERVAL_SECONDS` 秒生成一条模拟数据
3. 用于测试和演示

## 查看输出

### 实时查看

```bash
# 查看最新的输出文件
tail -f output/cdc/part-0-*

# 查看所有输出
cat output/cdc/part-0-*
```

### 统计信息

```bash
# 统计记录数
wc -l output/cdc/part-0-*

# 查看最近 10 条记录
tail -10 output/cdc/part-0-*
```

## 停止应用程序

### 本地运行

按 `Ctrl+C` 停止应用程序

### Flink 集群

1. 访问 Flink Web UI: http://localhost:8081
2. 找到运行中的作业
3. 点击 "Cancel" 按钮

或使用命令行：

```bash
# 列出所有作业
docker exec flink-jobmanager flink list

# 取消作业
docker exec flink-jobmanager flink cancel <job-id>
```

## 故障排查

### 无法连接到数据库

**问题**: 应用程序无法连接到数据库

**解决方案**:
1. 检查 `.env` 文件中的数据库配置
2. 确认数据库正在运行
3. 检查网络连接
4. 验证用户名和密码

```bash
# 测试数据库连接（Oracle）
sqlplus ${DATABASE_USERNAME}/${DATABASE_PASSWORD}@${DATABASE_HOST}:${DATABASE_PORT}/${DATABASE_SCHEMA}
```

### 找不到表

**问题**: 应用程序报告找不到指定的表

**解决方案**:
1. 确认表名拼写正确
2. 检查 Schema 是否正确
3. 验证用户是否有表的访问权限

```sql
-- 查看用户可访问的表
SELECT table_name FROM user_tables;
```

### 输出文件为空

**问题**: CSV 文件创建了但没有数据

**解决方案**:
1. 检查表是否有新数据
2. 确认轮询间隔设置合理
3. 查看应用程序日志

### 性能问题

**问题**: 应用程序运行缓慢

**解决方案**:
1. 增加 `POLL_INTERVAL_SECONDS` 值
2. 减少监控的表数量
3. 优化数据库查询
4. 增加 Flink 并行度

## 高级配置

### 监控多个表

在 `.env` 文件中用逗号分隔多个表名：

```bash
DATABASE_TABLES=trans_info,user_info,order_info
```

### 自定义输出路径

```bash
OUTPUT_PATH=/data/cdc-output
```

### 调整轮询间隔

```bash
# 每 5 秒轮询一次
POLL_INTERVAL_SECONDS=5

# 每 60 秒轮询一次
POLL_INTERVAL_SECONDS=60
```

## 生产环境建议

1. **使用真实的 CDC 连接器**: 当前实现使用轮询方式，生产环境建议使用 Debezium 或 Flink CDC Connector
2. **配置 Checkpoint**: 确保启用 Checkpoint 以保证数据不丢失
3. **监控作业状态**: 使用 Flink Web UI 或 Prometheus 监控作业
4. **日志管理**: 配置日志轮转和归档
5. **资源限制**: 根据数据量调整内存和 CPU 配置
6. **高可用**: 在生产环境启用 Flink HA 模式

## 示例

### 完整示例

```bash
# 1. 配置环境变量
cat > .env << EOF
DATABASE_HOST=192.168.1.100
DATABASE_PORT=1521
DATABASE_USERNAME=app_user
DATABASE_PASSWORD=SecurePass123
DATABASE_SCHEMA=production
DATABASE_TABLES=transactions,orders
OUTPUT_PATH=/data/cdc
POLL_INTERVAL_SECONDS=30
EOF

# 2. 启动应用程序
./start-cdc.sh

# 3. 查看输出
tail -f /data/cdc/part-0-*

# 4. 停止应用程序
# 按 Ctrl+C
```

## 参考资料

- [Flink CDC Connectors](https://github.com/ververica/flink-cdc-connectors)
- [Debezium Documentation](https://debezium.io/documentation/)
- [Apache Flink Documentation](https://flink.apache.org/docs/)
