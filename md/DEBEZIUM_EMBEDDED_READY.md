# Debezium Embedded CDC - 准备就绪

## 概述

已成功实现 Debezium Embedded Engine 方案，作为 Debezium Connect 的替代方案。

## 架构

```
Oracle Database (LogMiner)
    ↓
Debezium Embedded Engine (in Java App)
    ↓
CSV Files (./output/cdc/)
```

优点:
- 无需单独的 Debezium Connect 服务
- 更简单的架构和部署
- 更容易调试和控制
- 真正的 CDC（基于 LogMiner）
- 直接写入 CSV 文件

## 已完成的工作

### 1. 添加依赖 (pom.xml)
```xml
<!-- Debezium Embedded Engine -->
<dependency>
    <groupId>io.debezium</groupId>
    <artifactId>debezium-embedded</artifactId>
    <version>2.4.2.Final</version>
</dependency>

<!-- Debezium Oracle Connector -->
<dependency>
    <groupId>io.debezium</groupId>
    <artifactId>debezium-connector-oracle</artifactId>
    <version>2.4.2.Final</version>
</dependency>
```

### 2. 创建应用 (DebeziumEmbeddedCDCApp.java)

特性:
- 使用 Debezium Embedded Engine API
- 配置 Oracle LogMiner CDC
- 自动处理 offset 和 schema history
- 将变更事件写入 CSV 文件
- 支持 INSERT, UPDATE, DELETE 操作
- 优雅的 shutdown 处理

配置:
- `database.autocommit=false` - 修复 auto-commit 问题
- `snapshot.mode=schema_only` - 跳过初始数据快照
- `table.include.list` - 只监控指定表
- `log.mining.strategy=online_catalog` - 使用在线目录
- `log.mining.continuous.mine=true` - 持续挖掘日志

### 3. 创建运行脚本 (run-debezium-embedded-cdc.sh)

从 `.env` 文件读取配置并运行应用。

### 4. 编译和打包

```bash
mvn clean package -DskipTests
```

状态: ✅ 成功

## 如何使用

### 1. 确保 Oracle 数据库已配置 CDC

```sql
-- 检查归档日志模式
SELECT LOG_MODE FROM V$DATABASE;
-- 应该返回: ARCHIVELOG

-- 检查补充日志
SELECT SUPPLEMENTAL_LOG_DATA_MIN FROM V$DATABASE;
-- 应该返回: YES
```

### 2. 配置环境变量 (.env)

```bash
DATABASE_HOST=host.docker.internal
DATABASE_PORT=1521
DATABASE_USERNAME=system
DATABASE_PASSWORD=helowin
DATABASE_SID=helowin
DATABASE_SCHEMA=FINANCE_USER
DATABASE_TABLES=TRANS_INFO
OUTPUT_PATH=./output/cdc
```

### 3. 运行应用

```bash
./run-debezium-embedded-cdc.sh
```

### 4. 测试 CDC

在另一个终端中插入测试数据:

```sql
-- 连接到 Oracle
sqlplus system/helowin@localhost:1521/helowin

-- 插入数据
INSERT INTO FINANCE_USER.TRANS_INFO (ID, AMOUNT, DESCRIPTION) 
VALUES (1, 100.50, 'Test transaction');
COMMIT;

-- 更新数据
UPDATE FINANCE_USER.TRANS_INFO 
SET AMOUNT = 200.75 
WHERE ID = 1;
COMMIT;

-- 删除数据
DELETE FROM FINANCE_USER.TRANS_INFO WHERE ID = 1;
COMMIT;
```

### 5. 查看输出

```bash
# 查看 CSV 文件
ls -la ./output/cdc/

# 查看内容
cat ./output/cdc/cdc_events_*.csv
```

CSV 格式:
```
timestamp,operation,table,key,before,after
"2026-02-13 09:10:15","INSERT","FINANCE_USER.TRANS_INFO","1","null","{id:1,amount:100.50,...}"
"2026-02-13 09:10:20","UPDATE","FINANCE_USER.TRANS_INFO","1","{id:1,amount:100.50,...}","{id:1,amount:200.75,...}"
"2026-02-13 09:10:25","DELETE","FINANCE_USER.TRANS_INFO","1","{id:1,amount:200.75,...}","null"
```

## 文件结构

```
.
├── pom.xml                                    # 添加了 Debezium 依赖
├── src/main/java/com/realtime/pipeline/
│   └── DebeziumEmbeddedCDCApp.java           # Debezium Embedded 应用
├── run-debezium-embedded-cdc.sh              # 运行脚本
├── .env                                       # 环境变量配置
└── output/cdc/                                # 输出目录
    ├── cdc_events_*.csv                       # CDC 事件 CSV 文件
    ├── offsets.dat                            # Debezium offset 存储
    └── schema_history.dat                     # Schema 历史记录
```

## 与之前方案的对比

| 特性 | Flink CDC | Debezium Connect | Debezium Embedded |
|------|-----------|------------------|-------------------|
| 架构复杂度 | 中 | 高 | 低 |
| 部署难度 | 中 | 高 | 低 |
| 表过滤 | ❌ 不工作 | ❓ 未测试 | ✅ 应该工作 |
| 调试难度 | 高 | 高 | 低 |
| 依赖服务 | Flink | Kafka + Connect | 无 |
| 真正的 CDC | ✅ | ✅ | ✅ |
| 状态 | 放弃 | 失败 | 准备测试 |

## 下一步

1. 运行 `./run-debezium-embedded-cdc.sh`
2. 在 Oracle 中插入/更新/删除数据
3. 验证 CSV 文件中的变更事件
4. 如果成功，可以考虑将其集成到 Flink 流处理管道中

## 故障排除

### 问题: auto-commit 错误
解决方案: 已在配置中添加 `database.autocommit=false`

### 问题: 找不到表
解决方案: 确保使用大写的 Schema 和表名 (FINANCE_USER.TRANS_INFO)

### 问题: 归档日志未启用
解决方案: 运行 `configure-oracle-for-cdc.sql` 和 `enable-oracle-archivelog.sql`

### 问题: 权限不足
解决方案: 确保数据库用户有 LogMiner 权限

## 相关文档

- `DEBEZIUM_CDC_STATUS.md` - Debezium Connect 失败的详细分析
- `CDC_AUTO_COMMIT_FIX.md` - Auto-commit 问题的解决方案
- `CDC_FINAL_STATUS.md` - Flink CDC 失败的总结
- `configure-oracle-for-cdc.sql` - Oracle CDC 配置脚本
- `enable-oracle-archivelog.sql` - 启用归档日志脚本

## 时间线

- 2026-02-13 17:00 - 开始 Debezium Connect 方案
- 2026-02-14 09:00 - Debezium Connect REST API 无法启动
- 2026-02-14 09:45 - 决定使用 Debezium Embedded 方案
- 2026-02-14 10:00 - 添加依赖并创建应用
- 2026-02-14 10:05 - 编译和打包成功
- 2026-02-14 10:10 - 准备就绪，等待测试
