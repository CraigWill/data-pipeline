# Debezium CDC 部署状态

## 当前状态

### 已完成的工作

1. **下载 Oracle JDBC 驱动**
   - 成功从 Maven Central 下载 ojdbc8.jar (4.9MB)
   - 文件位置: `docker/debezium/ojdbc8.jar`

2. **配置 Debezium Connect 服务**
   - 在 `docker-compose.yml` 中添加了 Debezium Connect 服务
   - 使用官方镜像 `debezium/connect:2.4`
   - 通过 volume 挂载 JDBC 驱动到容器
   - 配置了所有必要的环境变量

3. **修复 auto-commit 问题**
   - 在 `register-oracle-connector.sh` 中添加了 `database.autocommit=false` 配置
   - 这是之前 Flink CDC 遇到的同样问题的解决方案

4. **创建注册脚本**
   - `register-debezium-connector.sh`: 从宿主机注册 connector
   - `docker/debezium/register-oracle-connector.sh`: 容器内注册脚本

### 当前问题

**Debezium Connect REST API 无法启动**

症状:
- Debezium Connect 容器正在运行
- Java 进程正常运行（ConnectDistributed）
- Kafka 连接正常
- ojdbc8.jar 已在 classpath 中
- 但是 REST API (端口 8083) 无法访问

根本原因:
- log4j 配置问题导致日志无法输出
- 无法看到真正的错误信息
- 可能是 Kafka Connect 在等待 Kafka topics 创建或其他初始化步骤

诊断信息:
```bash
# Kafka 连接测试
Kafka is reachable ✅

# 环境变量
BOOTSTRAP_SERVERS=kafka:9092 ✅
GROUP_ID=debezium-connect-cluster ✅
CONFIG_STORAGE_TOPIC=debezium_connect_configs ✅
OFFSET_STORAGE_TOPIC=debezium_connect_offsets ✅
STATUS_STORAGE_TOPIC=debezium_connect_statuses ✅

# Java 进程
org.apache.kafka.connect.cli.ConnectDistributed ✅

# REST API
curl http://localhost:8083/ - Connection refused ❌
```

## 替代方案

由于 Debezium Connect 的 REST API 启动问题难以诊断（log4j 配置问题），我们有以下几个选择：

### 方案 1: 继续调试 Debezium Connect（不推荐）
- 需要修复 log4j 配置
- 可能需要很长时间才能找到根本原因
- 不确定能否解决

### 方案 2: 使用 Debezium Embedded Engine（推荐）
- 在 Flink 应用中直接嵌入 Debezium Engine
- 不需要单独的 Debezium Connect 服务
- 更简单的架构：Oracle → Debezium Embedded (in Flink) → Kafka → CSV
- 更容易调试和控制

### 方案 3: 回到简化的 JDBC CDC 方案
- 使用之前创建的 `JdbcCDCApp.java`
- 通过定期轮询数据库表来检测变更
- 虽然不是真正的 CDC，但对于演示目的足够了
- 已经验证可以工作

### 方案 4: 使用 Flink CDC 但只监控单个表
- 回到 Flink CDC Oracle Connector
- 但这次在应用层面过滤表，而不是依赖 Flink CDC 的配置
- 接受 snapshot 阶段会扫描所有表，但在 streaming 阶段只处理目标表

## 推荐方案

**方案 2: 使用 Debezium Embedded Engine**

优点:
- 架构更简单（少一个服务）
- 更容易调试
- 完全控制 CDC 流程
- 真正的 CDC（基于 LogMiner）
- 可以直接写入 Kafka 或 CSV

实现步骤:
1. 在 `pom.xml` 中添加 Debezium Embedded Engine 依赖
2. 创建 `DebeziumEmbeddedCDCApp.java`
3. 配置 Debezium Engine 连接到 Oracle
4. 将 CDC 事件发送到 Kafka 或直接写入 CSV
5. 测试和验证

## 下一步行动

请选择一个方案继续：
1. 继续调试 Debezium Connect（需要更多时间）
2. 实现 Debezium Embedded Engine（推荐）
3. 使用简化的 JDBC CDC 方案
4. 回到 Flink CDC 但改进过滤逻辑

## 相关文件

- `docker-compose.yml` - Debezium Connect 服务配置
- `docker/debezium/Dockerfile` - Debezium 镜像（未使用，改用官方镜像）
- `docker/debezium/ojdbc8.jar` - Oracle JDBC 驱动
- `docker/debezium/register-oracle-connector.sh` - Connector 注册脚本
- `register-debezium-connector.sh` - 宿主机注册脚本
- `debug-debezium.sh` - 调试脚本
- `.env` - 环境变量配置

## 时间线

- 2026-02-13 17:00 - 开始实现 Debezium CDC
- 2026-02-13 17:05 - 下载 Oracle JDBC 驱动成功
- 2026-02-13 17:10 - 配置 docker-compose.yml
- 2026-02-13 17:15 - 启动 Debezium Connect 容器
- 2026-02-14 09:00 - 发现 REST API 无法启动
- 2026-02-14 09:30 - 诊断问题（log4j 配置）
- 2026-02-14 09:45 - 编写状态文档和替代方案
