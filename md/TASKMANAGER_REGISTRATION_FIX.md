# TaskManager 注册问题解决方案

## 问题描述

在启用 Flink 高可用（HA）模式后，TaskManager 无法注册到 JobManager，导致作业提交失败。

### 症状

- Flink Web UI 显示 0 个 TaskManager
- 作业提交失败，错误信息：`NoResourceAvailableException: Could not acquire the minimum required resources`
- TaskManager 日志显示连接被拒绝：`Connection refused: jobmanager/172.19.0.2:6123`

### 根本原因

在 Flink 1.18.0 的 HA 模式下：

1. **JobManager 使用动态端口**
   - JobManager 不监听配置的 RPC 端口 6123
   - 而是使用随机端口（例如 35963）
   - 这是 Flink HA 模式的设计行为

2. **TaskManager 配置问题**
   - TaskManager 仍然尝试直接连接 `jobmanager:6123`
   - 虽然配置了通过 Zookeeper 发现，但实际连接失败
   - Flink 1.18.0 的 HA 模式配置较为复杂

3. **Zookeeper 集成问题**
   - Zookeeper 中有正确的 HA 结构（`/flink/realtime-pipeline`）
   - 但 TaskManager 未能正确通过 Zookeeper 发现 JobManager

## 解决方案

### 临时方案：禁用 HA 模式

为了优先保证 CDC 功能正常运行，暂时禁用 HA 模式：

#### 1. 修改 `.env` 文件

```bash
# 将 HA_MODE 从 zookeeper 改为 NONE
HA_MODE=NONE
```

#### 2. 修改 Flink 配置文件

注释掉 HA 配置：

**docker/jobmanager/flink-conf.yaml**:
```yaml
# high-availability: zookeeper
# high-availability.storageDir: file:///opt/flink/ha
# high-availability.zookeeper.quorum: zookeeper:2181
# high-availability.zookeeper.path.root: /flink
# high-availability.cluster-id: /realtime-pipeline
```

**docker/taskmanager/flink-conf.yaml**:
```yaml
# high-availability: zookeeper
# high-availability.storageDir: file:///opt/flink/ha
# high-availability.zookeeper.quorum: zookeeper:2181
# high-availability.zookeeper.path.root: /flink
# high-availability.cluster-id: /realtime-pipeline
```

#### 3. 重新构建并启动集群

```bash
# 重新构建镜像
./docker/build-images.sh

# 停止并删除现有容器
docker-compose down

# 启动集群
docker-compose up -d

# 等待集群就绪
sleep 20

# 验证 TaskManager 注册
curl -s http://localhost:8081/overview | python3 -m json.tool
```

#### 4. 提交作业

```bash
./auto-submit-jobs.sh
```

## 验证结果

### 集群状态

```json
{
    "taskmanagers": 1,
    "slots-total": 4,
    "slots-available": 4,
    "jobs-running": 1,
    "flink-version": "1.18.0"
}
```

✅ **TaskManager 成功注册**
- 1 个 TaskManager
- 4 个可用 Slot
- 作业正常运行

### 作业状态

```
Job ID: d6d7dd5f5bafad6a92160344bbd07836
Job Name: Flink CDC 3.x Oracle Application
State: RUNNING
Parallelism: 4
```

✅ **CDC 作业成功提交并运行**

## 后续计划

### 重新启用 HA 模式

待 CDC 功能稳定后，可以重新研究并启用 HA 模式：

1. **研究 Flink 1.18.0 HA 配置**
   - 查阅官方文档的最新 HA 配置要求
   - 检查是否需要额外的网络配置
   - 验证 Zookeeper 版本兼容性

2. **可能的解决方向**
   - 使用 Kubernetes 部署（更好的 HA 支持）
   - 升级到 Flink 1.19+ 版本
   - 配置正确的网络发现机制
   - 调整 Pekko/Akka RPC 配置

3. **测试步骤**
   - 在测试环境中启用 HA
   - 验证 TaskManager 注册
   - 测试 JobManager 故障转移
   - 验证作业自动恢复

## 相关文件

- `.env` - 环境变量配置
- `docker/jobmanager/flink-conf.yaml` - JobManager 配置
- `docker/taskmanager/flink-conf.yaml` - TaskManager 配置
- `docker-compose.yml` - Docker Compose 配置
- `auto-submit-jobs.sh` - 作业提交脚本
- `FLINK_HA_STATUS.md` - HA 状态文档
- `FLINK_HA_ENABLED.md` - HA 启用文档

## 总结

通过禁用 HA 模式，成功解决了 TaskManager 注册问题，CDC 作业现在可以正常运行。这是一个务实的临时方案，优先保证了核心功能的可用性。

HA 模式的配置较为复杂，需要更深入的研究和测试。建议在生产环境中使用 Kubernetes 等容器编排平台，它们提供了更成熟的 HA 支持。

---

**状态**: ✅ 已解决
**日期**: 2026-02-25
**作业 ID**: d6d7dd5f5bafad6a92160344bbd07836
