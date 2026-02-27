# Zookeeper 使用状态说明

## 当前状态：🔄 **间接使用（仅 Kafka 依赖）**

Zookeeper 容器在运行，但**只被 Kafka 使用**，Flink 和其他组件没有使用它。

## 使用情况分析

### ✅ 被 Kafka 使用

**Kafka 依赖 Zookeeper**:
```yaml
# docker-compose.yml
kafka:
  environment:
    KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
```

Kafka 使用 Zookeeper 用于：
- 集群元数据管理
- Broker 注册和发现
- Topic 配置管理
- 分区 Leader 选举

**注意**: Kafka 3.x 开始支持 KRaft 模式（无需 Zookeeper），但当前配置仍使用 Zookeeper。

### ❌ Flink 未使用

**Flink 高可用 (HA) 配置已禁用**:

```yaml
# docker/jobmanager/flink-conf.yaml
# high-availability: zookeeper  # 已注释
# high-availability.zookeeper.quorum: zookeeper:2181  # 已注释
```

```bash
# .env
HA_MODE=NONE  # 未启用 HA
# HA_ZOOKEEPER_QUORUM=zookeeper:2181  # 已注释
```

**当前 Flink 模式**: 单 JobManager，无高可用

### ❌ 其他组件未使用

- Debezium Connect: 不需要 Zookeeper
- Flink CDC: 不需要 Zookeeper
- 应用程序: 不使用 Zookeeper

## 依赖关系图

```
Zookeeper (运行中)
    ↓ (仅此依赖)
Kafka (运行中)
    ↓ (未使用)
❌ 当前没有消费者
```

```
Flink (运行中)
    ↓
❌ 不使用 Zookeeper
    (HA 模式未启用)
```

## Zookeeper 的潜在用途

### 1. Flink 高可用 (HA) - 未启用

**如果启用 Flink HA**，Zookeeper 将用于：
- JobManager 主备选举
- 作业元数据存储
- Checkpoint 元数据管理
- 集群协调

**启用方法**:
```yaml
# .env
HA_MODE=zookeeper
HA_ZOOKEEPER_QUORUM=zookeeper:2181
HA_ZOOKEEPER_PATH_ROOT=/flink
HA_CLUSTER_ID=/default
HA_STORAGE_DIR=file:///opt/flink/ha
```

```yaml
# docker/jobmanager/flink-conf.yaml
high-availability: zookeeper
high-availability.storageDir: file:///opt/flink/ha
high-availability.zookeeper.quorum: zookeeper:2181
high-availability.zookeeper.path.root: /flink
high-availability.cluster-id: /realtime-pipeline
```

### 2. Kafka 集群协调 - 已使用

**当前唯一用途**: Kafka 的集群管理

## 是否需要 Zookeeper？

### 当前需求分析

| 组件 | 是否需要 | 原因 |
|------|---------|------|
| Kafka | ✅ 需要 | Kafka 依赖 Zookeeper（除非迁移到 KRaft） |
| Flink HA | ❌ 不需要 | HA 模式未启用 |
| 其他组件 | ❌ 不需要 | 无依赖 |

### 结论

**当前状态**: 
- ✅ 需要保留 Zookeeper（因为 Kafka 依赖它）
- 但实际上 Kafka 也没被使用
- 所以 Zookeeper 是"间接未使用"

**如果移除 Kafka**:
- 可以同时移除 Zookeeper
- 节省资源

**如果保留 Kafka**:
- 必须保留 Zookeeper（除非迁移到 KRaft）

## 资源占用

```bash
# 检查 Zookeeper 资源使用
docker stats zookeeper --no-stream
```

典型资源占用：
- CPU: < 1%
- 内存: ~50-100MB
- 磁盘: 数据 + 日志卷

## 迁移到 KRaft（可选）

Kafka 3.x 支持 KRaft 模式，无需 Zookeeper：

```yaml
# docker-compose.yml (KRaft 模式示例)
kafka:
  environment:
    KAFKA_PROCESS_ROLES: broker,controller
    KAFKA_NODE_ID: 1
    KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
    KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
    # 移除 KAFKA_ZOOKEEPER_CONNECT
```

**优势**:
- 简化架构
- 减少组件
- 更好的性能
- Kafka 官方推荐

**劣势**:
- 需要重新配置
- 数据迁移复杂

## 启用 Flink HA（可选）

如果需要生产级高可用：

### 1. 修改配置

```bash
# .env
HA_MODE=zookeeper
HA_ZOOKEEPER_QUORUM=zookeeper:2181
```

### 2. 启动多个 JobManager

```bash
docker-compose up -d --scale jobmanager=2
```

### 3. 验证 HA

```bash
# 检查 Zookeeper 中的 Flink 节点
docker exec zookeeper zkCli.sh ls /flink
```

## 移除建议

### 场景 1: 移除 Kafka + Zookeeper

如果确定不需要 Kafka：

```bash
# 停止服务
docker-compose stop kafka zookeeper kafka-ui

# 删除容器
docker-compose rm -f kafka zookeeper kafka-ui

# 删除卷（可选）
docker volume rm zookeeper-data zookeeper-logs kafka-data
```

### 场景 2: 保留但优化

如果可能需要 Kafka：

```bash
# 保持当前状态
# Zookeeper 资源占用不大，可以保留
```

### 场景 3: 迁移到 KRaft

如果要现代化架构：

1. 修改 docker-compose.yml 使用 KRaft 模式
2. 移除 Zookeeper 服务
3. 重新创建 Kafka 容器

## 总结

| 问题 | 答案 |
|------|------|
| Zookeeper 是否运行？ | ✅ 是 |
| Flink 是否使用 Zookeeper？ | ❌ 否（HA 未启用） |
| Kafka 是否使用 Zookeeper？ | ✅ 是 |
| Kafka 是否被使用？ | ❌ 否 |
| 是否需要 Zookeeper？ | 🤔 间接不需要（因为 Kafka 未使用） |
| 应该移除吗？ | 🤔 可以，但建议保留备用 |

**当前状态**: Zookeeper → Kafka → ❌ 无消费者

**实际使用**: ❌ 间接未使用（Kafka 依赖但 Kafka 未被使用）

**建议**: 保留 Zookeeper 和 Kafka 作为备用组件，资源占用不大，未来可能需要。
