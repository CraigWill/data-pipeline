# Flink 集群状态页面增强

## 问题描述

用户反馈 Flink 集群状态页面信息不全，缺少关键的集群信息。

## 解决方案

增强了 Flink 集群状态页面，添加了以下信息：

### 1. 集群概览增强

新增显示指标：
- 失败的作业数量（jobs-failed）
- 取消的作业数量（jobs-cancelled）

原有指标：
- TaskManager 数量
- 可用任务槽 / 总任务槽
- 运行中的作业
- 已完成的作业

### 2. JobManager 配置信息

新增 JobManager 配置信息卡片，按类别显示关键配置：

**高可用配置（High Availability）**
- high-availability.type（HA 类型：zookeeper/kubernetes/none）
- high-availability.cluster-id（集群 ID）
- high-availability.zookeeper.quorum（ZooKeeper 地址）
- high-availability.zookeeper.path.root（ZooKeeper 根路径）
- high-availability.storageDir（HA 存储目录）

**Checkpoint 配置**
- state.checkpoints.dir（Checkpoint 存储目录）
- state.checkpoints.num-retained（保留的 Checkpoint 数量）
- state.savepoints.dir（Savepoint 存储目录）

**内存配置**
- jobmanager.memory.process.size（JobManager 进程内存）
- jobmanager.memory.heap.size（JobManager 堆内存）
- jobmanager.memory.off-heap.size（JobManager 堆外内存）

### 3. 运行中的作业列表

新增运行中的作业列表卡片，显示：
- 作业名称
- 作业 ID（JID）
- 开始时间
- 运行时长（自动格式化：天/小时/分钟/秒）
- 任务数量
- 作业状态（运行中）

### 4. TaskManager 信息增强

在原有基础上新增显示：
- JVM 堆内存使用情况（空闲内存 / 托管内存）
- 数据端口（dataPort）

原有信息：
- TaskManager ID
- 路径（path）
- 状态（运行中/已停止）
- 任务槽数量
- 空闲槽数量
- CPU 核心数
- 物理内存

## 技术实现

### 前端修改

**文件**: `monitor/frontend-vue/src/views/ClusterView.vue`

1. 新增数据字段：
   - `jobManagerConfig`: 存储 JobManager 配置
   - `runningJobs`: 存储运行中的作业列表

2. 新增 API 调用：
   - `/api/cluster/jobmanagers`: 获取 JobManager 配置
   - `/api/cluster/jobs`: 获取所有作业列表

3. 新增辅助函数：
   - `getConfigByPrefix(prefix)`: 按前缀过滤配置项
   - `formatDuration(ms)`: 格式化时长（毫秒 → 天/小时/分钟/秒）

4. 新增 UI 组件：
   - JobManager 配置卡片（按类别分组显示）
   - 运行中的作业列表卡片
   - 失败/取消作业数量指标

### 后端修改

**文件**: `monitor-backend/src/main/java/com/realtime/monitor/controller/ClusterController.java`

新增 API 端点：
```java
@GetMapping("/jobs")
public ApiResponse<List<Map<String, Object>>> getJobs()
```

该端点调用 `FlinkService.getJobs()` 方法，返回所有作业列表（包括运行中、已完成、失败、取消的作业）。

前端会过滤出状态为 `RUNNING` 的作业显示在"运行中的作业"卡片中。

## 部署步骤

1. 构建前端：
```bash
cd monitor/frontend-vue
npm run build
```

2. 构建后端：
```bash
mvn clean package -DskipTests -pl monitor-backend -am
```

3. 构建 Docker 镜像：
```bash
docker build -t realtime-monitor:latest -f monitor/Dockerfile .
```

4. 重启服务：
```bash
docker-compose restart monitor-backend
docker-compose restart monitor-frontend
```

## 验证

访问 http://localhost:8888/cluster，应该看到：

1. 集群概览显示 6 个指标（包括失败和取消的作业）
2. 版本信息卡片
3. JobManager 配置信息卡片（按类别分组）
4. 运行中的作业列表卡片
5. TaskManager 列表（包含更详细的内存和端口信息）

## 注意事项

1. JobManager 配置信息来自 Flink REST API 的 `/jobmanager/config` 端点，返回所有配置项
2. 配置项按前缀分组显示，便于查看相关配置
3. 运行中的作业列表每 10 秒自动刷新
4. 时长格式化会自动选择合适的单位（天/小时/分钟/秒）
5. 如果某些信息不可用，会显示"未知"或"N/A"

## 后续优化建议

1. 添加 Checkpoint 统计信息（成功率、平均时长、最近失败原因）
2. 添加网络指标（吞吐量、背压情况）
3. 添加作业详情链接（点击作业跳转到作业详情页）
4. 添加 TaskManager 详情链接（点击 TaskManager 查看详细指标）
5. 添加图表展示历史趋势（作业数量、资源使用率等）
