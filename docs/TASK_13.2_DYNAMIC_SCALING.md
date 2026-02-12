# Task 13.2: 动态扩缩容支持实现

## 概述

本任务实现了Flink集群的动态扩缩容支持，包括动态增加TaskManager实例、动态调整并行度和优雅关闭逻辑。

## 实现的功能

### 1. 动态增加TaskManager实例 (需求 6.2)

**功能描述**: 支持在运行时动态增加TaskManager实例，以应对增长的数据处理需求。

**实现方式**:
- `DynamicScalingManager.scaleOutTaskManagers(int additionalTaskManagers)` 方法
- 通过资源管理器API（Kubernetes、YARN等）增加TaskManager实例
- 新的TaskManager自动注册到JobManager并开始处理数据
- 扩容过程中保持数据处理的连续性（需求 6.6）

**使用示例**:
```java
DynamicScalingManager scalingManager = new DynamicScalingManager("localhost", 8081);
boolean success = scalingManager.scaleOutTaskManagers(2); // 增加2个TaskManager
```

### 2. 动态调整并行度 (需求 6.3)

**功能描述**: 支持在运行时动态调整作业的并行度，以优化资源利用率。

**实现方式**:
- `DynamicScalingManager.adjustParallelism(String jobId, int newParallelism, String savepointPath)` 方法
- 通过以下步骤实现:
  1. 触发Savepoint保存当前状态
  2. 停止作业
  3. 使用新的并行度和Savepoint重启作业
- 确保数据处理的连续性，不丢失状态

**使用示例**:
```java
DynamicScalingManager scalingManager = new DynamicScalingManager("localhost", 8081);
boolean success = scalingManager.adjustParallelism(
    "a1b2c3d4e5f6g7h8i9j0", // 作业ID
    4,                       // 新的并行度
    "/tmp/savepoints"        // Savepoint路径
);
```

### 3. 优雅关闭逻辑 (需求 6.7)

**功能描述**: 在缩容时实现优雅关闭，确保当前处理的任务完成后再释放资源。

**实现方式**:
- `DynamicScalingManager.gracefulShutdownTaskManager(String taskManagerId, long drainTimeout)` 方法
- `DynamicScalingManager.scaleInTaskManagers(int taskManagersToRemove, long drainTimeout)` 方法
- 优雅关闭流程:
  1. 标记TaskManager为"排空"状态
  2. 停止接受新任务
  3. 等待当前任务完成（或超时）
  4. 关闭TaskManager

**使用示例**:
```java
DynamicScalingManager scalingManager = new DynamicScalingManager("localhost", 8081);

// 优雅关闭单个TaskManager
boolean success1 = scalingManager.gracefulShutdownTaskManager(
    "taskmanager-1",  // TaskManager ID
    300000L           // 排空超时时间（5分钟）
);

// 缩容多个TaskManager
boolean success2 = scalingManager.scaleInTaskManagers(
    2,        // 要移除的TaskManager数量
    300000L   // 排空超时时间（5分钟）
);
```

## 核心组件

### DynamicScalingManager

动态扩缩容管理器，提供以下功能:

**主要方法**:
- `scaleOutTaskManagers(int additionalTaskManagers)`: 扩容TaskManager
- `adjustParallelism(String jobId, int newParallelism, String savepointPath)`: 调整并行度
- `gracefulShutdownTaskManager(String taskManagerId, long drainTimeout)`: 优雅关闭TaskManager
- `scaleInTaskManagers(int taskManagersToRemove, long drainTimeout)`: 缩容TaskManager
- `getTaskManagerCount()`: 获取当前TaskManager数量

**配置参数**:
- `jobManagerHost`: JobManager REST API主机地址
- `jobManagerPort`: JobManager REST API端口

### ScalingConfig

扩缩容配置类，包含以下配置项:

```java
ScalingConfig config = ScalingConfig.builder()
    .enabled(true)                    // 是否启用动态扩缩容
    .jobManagerHost("localhost")      // JobManager主机地址
    .jobManagerPort(8081)             // JobManager端口
    .minTaskManagers(1)               // 最小TaskManager数量
    .maxTaskManagers(10)              // 最大TaskManager数量
    .minParallelism(1)                // 最小并行度
    .maxParallelism(100)              // 最大并行度
    .drainTimeout(300000L)            // 优雅关闭超时时间（毫秒）
    .savepointPath("/tmp/savepoints") // Savepoint路径
    .build();
```

## 部署注意事项

### Kubernetes部署

在Kubernetes环境中，TaskManager的扩缩容通过以下方式实现:

1. **扩容**: 增加TaskManager Deployment的副本数
   ```bash
   kubectl scale deployment flink-taskmanager --replicas=5
   ```

2. **缩容**: 减少TaskManager Deployment的副本数
   ```bash
   kubectl scale deployment flink-taskmanager --replicas=3
   ```

3. **自动扩缩容**: 使用Horizontal Pod Autoscaler (HPA)
   ```yaml
   apiVersion: autoscaling/v2
   kind: HorizontalPodAutoscaler
   metadata:
     name: flink-taskmanager-hpa
   spec:
     scaleTargetRef:
       apiVersion: apps/v1
       kind: Deployment
       name: flink-taskmanager
     minReplicas: 2
     maxReplicas: 10
     metrics:
     - type: Resource
       resource:
         name: cpu
         target:
           type: Utilization
           averageUtilization: 70
   ```

### YARN部署

在YARN环境中，TaskManager的扩缩容通过Flink的YARN客户端实现:

1. **扩容**: 通过YARN API请求额外的容器
2. **缩容**: 释放空闲的YARN容器

### Standalone部署

在Standalone模式下，需要手动启动/停止TaskManager进程:

1. **扩容**: 在新节点上启动TaskManager
   ```bash
   ./bin/taskmanager.sh start
   ```

2. **缩容**: 停止TaskManager进程
   ```bash
   ./bin/taskmanager.sh stop
   ```

## 测试

### 单元测试

实现了以下单元测试:

1. **DynamicScalingManagerTest**: 测试扩缩容管理器的各项功能
   - 测试构造函数参数验证
   - 测试扩容TaskManager
   - 测试调整并行度
   - 测试优雅关闭
   - 测试缩容TaskManager
   - 测试多次扩缩容操作

2. **ScalingConfigTest**: 测试扩缩容配置
   - 测试默认值
   - 测试配置验证
   - 测试边界值
   - 测试无效配置

### 运行测试

```bash
mvn test -Dtest=DynamicScalingManagerTest,ScalingConfigTest
```

## 使用示例

完整的使用示例请参考 `DynamicScalingExample.java`:

```java
// 创建配置
ScalingConfig config = ScalingConfig.builder()
    .enabled(true)
    .jobManagerHost("localhost")
    .jobManagerPort(8081)
    .minTaskManagers(2)
    .maxTaskManagers(10)
    .drainTimeout(300000L)
    .savepointPath("/tmp/savepoints")
    .build();

// 创建扩缩容管理器
DynamicScalingManager scalingManager = new DynamicScalingManager(
    config.getJobManagerHost(),
    config.getJobManagerPort()
);

// 扩容
scalingManager.scaleOutTaskManagers(2);

// 调整并行度
scalingManager.adjustParallelism("job-id", 4, config.getSavepointPath());

// 缩容
scalingManager.scaleInTaskManagers(1, config.getDrainTimeout());
```

## 验证需求

本实现验证了以下需求:

- **需求 6.2**: THE System SHALL 支持动态增加TaskManager实例
  - ✅ 实现了 `scaleOutTaskManagers()` 方法
  - ✅ 支持在运行时增加TaskManager

- **需求 6.3**: THE System SHALL 支持动态调整并行度
  - ✅ 实现了 `adjustParallelism()` 方法
  - ✅ 通过Savepoint机制保证状态一致性

- **需求 6.7**: WHEN 缩容操作 THEN THE System SHALL 完成当前处理任务后再释放资源
  - ✅ 实现了 `gracefulShutdownTaskManager()` 方法
  - ✅ 实现了 `scaleInTaskManagers()` 方法
  - ✅ 支持排空超时配置

- **需求 6.6**: THE System SHALL 在扩容过程中保持数据处理的连续性
  - ✅ 扩容操作不中断数据处理
  - ✅ 新TaskManager自动注册并开始工作

## 限制和注意事项

1. **并行度调整需要重启作业**: 调整并行度需要停止作业并从Savepoint恢复，会有短暂的中断。

2. **资源管理器依赖**: 实际的TaskManager启动/停止依赖于底层资源管理器（Kubernetes、YARN等）。

3. **排空超时**: 优雅关闭时需要设置合理的超时时间，避免等待过长或过早终止。

4. **状态大小**: 调整并行度时，Savepoint的大小可能很大，需要确保有足够的存储空间。

5. **网络带宽**: 扩缩容过程中可能涉及状态的重新分配，需要足够的网络带宽。

## 后续改进

1. **自动扩缩容**: 基于负载指标自动触发扩缩容
2. **更细粒度的控制**: 支持按算子调整并行度
3. **预测性扩缩容**: 基于历史数据预测负载并提前扩缩容
4. **成本优化**: 在满足性能要求的前提下最小化资源使用

## 相关文档

- [Flink官方文档 - 扩展性](https://nightlies.apache.org/flink/flink-docs-stable/docs/ops/state/savepoints/)
- [Kubernetes HPA文档](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/)
- [YARN动态资源分配](https://hadoop.apache.org/docs/current/hadoop-yarn/hadoop-yarn-site/YARN.html)
