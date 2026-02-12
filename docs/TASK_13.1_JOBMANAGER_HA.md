# Task 13.1: JobManager高可用性配置

## 概述

本任务实现了Flink JobManager的高可用性配置，支持ZooKeeper和Kubernetes两种HA模式，确保系统在JobManager故障时能够快速切换到备用实例。

## 验证需求

- **需求 5.1**: THE System SHALL 支持Flink JobManager的高可用配置
- **需求 5.2**: THE System SHALL 支持至少2个JobManager实例
- **需求 5.3**: WHEN 主JobManager失败 THEN THE System SHALL 在30秒内切换到备用JobManager

## 实现内容

### 1. 核心组件

#### 1.1 HighAvailabilityConfig
高可用性配置类，包含以下配置项：

- **enabled**: 是否启用HA
- **mode**: HA模式（zookeeper/kubernetes/none）
- **zookeeperQuorum**: ZooKeeper集群地址
- **zookeeperPath**: ZooKeeper根路径
- **kubernetesNamespace**: Kubernetes命名空间
- **kubernetesClusterId**: Kubernetes集群ID
- **storageDir**: HA元数据存储目录
- **jobManagerCount**: JobManager数量（至少2个）
- **failoverTimeout**: 故障切换超时时间（默认30秒）

#### 1.2 HighAvailabilityConfigurator
高可用性配置器，负责将HA配置应用到Flink环境：

- 支持ZooKeeper模式配置
- 支持Kubernetes模式配置
- 配置HA存储目录
- 设置ZooKeeper会话和连接超时

#### 1.3 JobManagerFailoverManager
故障切换管理器，负责监控和处理JobManager故障切换：

- 记录JobManager故障
- 执行故障切换逻辑
- 验证切换时间是否在超时范围内
- 提供故障切换统计信息

#### 1.4 FlinkEnvironmentConfigurator增强
更新了FlinkEnvironmentConfigurator以支持HA配置：

- 添加HA配置器集成
- 在环境配置时应用HA设置
- 提供HA状态查询方法

### 2. 配置示例

#### 2.1 ZooKeeper模式配置

```yaml
highAvailability:
  enabled: true
  mode: zookeeper
  zookeeperQuorum: localhost:2181,localhost:2182,localhost:2183
  zookeeperPath: /flink
  storageDir: /opt/flink/ha
  jobManagerCount: 2
  failoverTimeout: 30000
  zookeeperSessionTimeout: 60000
  zookeeperConnectionTimeout: 15000
```

#### 2.2 Kubernetes模式配置

```yaml
highAvailability:
  enabled: true
  mode: kubernetes
  kubernetesNamespace: flink
  kubernetesClusterId: flink-cluster-1
  storageDir: /opt/flink/ha
  jobManagerCount: 2
  failoverTimeout: 30000
```

### 3. 使用示例

```java
// 创建HA配置
HighAvailabilityConfig haConfig = HighAvailabilityConfig.builder()
    .enabled(true)
    .mode("zookeeper")
    .zookeeperQuorum("localhost:2181,localhost:2182,localhost:2183")
    .zookeeperPath("/flink")
    .storageDir("/opt/flink/ha")
    .jobManagerCount(2)
    .failoverTimeout(30000L)
    .build();

// 创建Flink配置
FlinkConfig flinkConfig = FlinkConfig.builder()
    .parallelism(4)
    .checkpointInterval(300000L)
    .checkpointDir("/opt/flink/checkpoints")
    .build();

// 创建环境配置器（包含HA配置）
FlinkEnvironmentConfigurator configurator = 
    new FlinkEnvironmentConfigurator(flinkConfig, haConfig);

// 创建并配置Flink执行环境
StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
configurator.configure(env);

// 检查HA是否启用
if (configurator.isHAEnabled()) {
    System.out.println("HA is enabled with " + 
        configurator.getHaConfigurator().getJobManagerCount() + 
        " JobManager instances");
}
```

### 4. 故障切换示例

```java
// 创建故障切换管理器
JobManagerFailoverManager failoverManager = 
    new JobManagerFailoverManager(30000L);

// 记录主JobManager故障
boolean success = failoverManager.recordFailure("primary");

if (success) {
    System.out.println("Failover successful!");
    System.out.println("Current leader: " + failoverManager.getCurrentLeader());
} else {
    System.out.println("Failover failed or exceeded timeout");
}

// 获取故障切换统计信息
JobManagerFailoverManager.FailoverStats stats = failoverManager.getStats();
System.out.println("Failover stats: " + stats);
```

## 测试覆盖

### 单元测试

1. **HighAvailabilityConfigTest** (13个测试)
   - 有效的ZooKeeper配置
   - 有效的Kubernetes配置
   - 禁用HA配置
   - 无效模式验证
   - 缺失必需参数验证
   - JobManager数量验证（至少2个）
   - 故障切换超时验证

2. **HighAvailabilityConfiguratorTest** (10个测试)
   - ZooKeeper HA配置应用
   - Kubernetes HA配置应用
   - 禁用HA配置
   - 存储目录自动添加scheme
   - JobManager数量要求验证
   - 故障切换超时要求验证

3. **JobManagerFailoverManagerTest** (13个测试)
   - 初始状态验证
   - 主JobManager故障切换
   - 备用JobManager故障处理
   - 多次故障切换
   - JobManager恢复处理
   - 故障切换时间验证（30秒内）
   - 并发故障处理

4. **FlinkEnvironmentConfiguratorHATest** (8个测试)
   - ZooKeeper HA配置集成
   - Kubernetes HA配置集成
   - 无HA配置
   - 禁用HA配置
   - JobManager数量要求验证
   - 故障切换超时要求验证

**总计**: 44个单元测试，全部通过

## 部署说明

### ZooKeeper模式部署

1. 部署ZooKeeper集群（至少3个节点）
2. 配置application-ha-zookeeper.yml
3. 启动至少2个JobManager实例
4. 确保所有JobManager可以访问共享存储目录

### Kubernetes模式部署

1. 在Kubernetes集群中创建Flink命名空间
2. 配置application-ha-kubernetes.yml
3. 使用Kubernetes Deployment部署JobManager（replicas >= 2）
4. Kubernetes会自动处理领导者选举和故障切换

## 验证方法

### 验证HA配置

```bash
# 检查Flink配置
cat /opt/flink/conf/flink-conf.yaml | grep high-availability

# 应该看到类似输出：
# high-availability: zookeeper
# high-availability.zookeeper.quorum: localhost:2181,localhost:2182,localhost:2183
# high-availability.zookeeper.path.root: /flink
# high-availability.storageDir: file:///opt/flink/ha
```

### 验证故障切换

1. 启动Flink集群（2个JobManager）
2. 提交一个长时间运行的作业
3. 停止主JobManager进程
4. 观察备用JobManager是否在30秒内接管
5. 检查作业是否继续运行

```bash
# 查看JobManager日志
tail -f /opt/flink/log/flink-*-standalonesession-*.log

# 应该看到类似输出：
# INFO  [...] - Granted leadership to ... with session id ...
# INFO  [...] - JobManager ... was granted leadership with session id ...
```

## 注意事项

1. **存储目录**: HA存储目录必须是所有JobManager都能访问的共享存储（如NFS、HDFS、S3等）
2. **ZooKeeper集群**: 建议使用至少3个ZooKeeper节点以保证高可用性
3. **网络延迟**: 故障切换时间受网络延迟影响，确保JobManager之间网络通畅
4. **资源配置**: 每个JobManager需要足够的内存和CPU资源
5. **监控**: 建议配置监控系统监控JobManager状态和故障切换事件

## 相关文件

- `src/main/java/com/realtime/pipeline/config/HighAvailabilityConfig.java`
- `src/main/java/com/realtime/pipeline/flink/ha/HighAvailabilityConfigurator.java`
- `src/main/java/com/realtime/pipeline/flink/ha/JobManagerFailoverManager.java`
- `src/main/java/com/realtime/pipeline/flink/ha/HighAvailabilityExample.java`
- `src/main/resources/application-ha-zookeeper.yml`
- `src/main/resources/application-ha-kubernetes.yml`
- `src/test/java/com/realtime/pipeline/config/HighAvailabilityConfigTest.java`
- `src/test/java/com/realtime/pipeline/flink/ha/HighAvailabilityConfiguratorTest.java`
- `src/test/java/com/realtime/pipeline/flink/ha/JobManagerFailoverManagerTest.java`
- `src/test/java/com/realtime/pipeline/flink/FlinkEnvironmentConfiguratorHATest.java`

## 总结

Task 13.1已成功完成，实现了以下功能：

✅ 支持ZooKeeper和Kubernetes两种HA模式  
✅ 支持至少2个JobManager实例配置  
✅ 实现30秒内故障切换逻辑  
✅ 提供完整的配置验证  
✅ 包含44个单元测试，全部通过  
✅ 提供详细的配置示例和使用文档  

系统现在具备了JobManager高可用性能力，满足了需求5.1、5.2和5.3的所有验收标准。
