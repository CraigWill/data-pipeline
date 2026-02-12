# Task 10.4: 健康检查接口实现

## 概述

实现了HTTP健康检查服务器，提供多个端点用于系统健康状态监控和容器编排系统的健康探测。

## 验证需求

- **需求 7.8**: THE System SHALL 提供健康检查接口

## 实现内容

### 1. HealthCheckServer 类

健康检查服务器，提供以下HTTP端点：

#### 端点说明

1. **GET /health** - 基本健康检查
   - 返回系统整体健康状态和关键指标
   - 状态码：200 (UP) 或 503 (DOWN/DEGRADED)
   - 响应内容：
     ```json
     {
       "status": "UP",
       "timestamp": 1706428800000,
       "metrics": {
         "latency": {
           "average": 150,
           "last": 120
         },
         "checkpoint": {
           "successRate": 95.5,
           "failureRate": 4.5,
           "lastDuration": 5000
         },
         "backpressure": {
           "level": 0.3
         }
       },
       "activeAlerts": 0
     }
     ```

2. **GET /health/live** - 存活探测 (Liveness Probe)
   - 用于Kubernetes Liveness Probe
   - 检查应用是否还在运行（不检查依赖服务）
   - 状态码：200 (alive) 或 503 (dead)
   - 响应内容：
     ```json
     {
       "status": "UP",
       "timestamp": 1706428800000
     }
     ```

3. **GET /health/ready** - 就绪探测 (Readiness Probe)
   - 用于Kubernetes Readiness Probe
   - 检查应用是否准备好接收流量
   - 检查项：
     - 服务运行状态
     - 监控服务初始化状态
     - Checkpoint失败率是否正常
     - 延迟是否在阈值内
     - 反压是否在阈值内
   - 状态码：200 (ready) 或 503 (not ready)
   - 响应内容：
     ```json
     {
       "status": "READY",
       "timestamp": 1706428800000,
       "checks": {
         "running": true,
         "healthStatus": "UP",
         "monitoringInitialized": true,
         "checkpointHealthy": true,
         "latencyHealthy": true,
         "backpressureHealthy": true
       }
     }
     ```

4. **GET /metrics** - 详细指标信息
   - 返回所有系统指标的详细信息
   - 状态码：200
   - 响应内容：
     ```json
     {
       "timestamp": 1706428800000,
       "latency": {
         "average": 150,
         "last": 120
       },
       "checkpoint": {
         "successRate": 95.5,
         "failureRate": 4.5,
         "totalCount": 100,
         "successCount": 95,
         "failureCount": 5,
         "averageDuration": 5200,
         "lastDuration": 5000
       },
       "backpressure": {
         "level": 0.3
       },
       "resources": {
         "heapUsed": 536870912,
         "heapTotal": 1073741824,
         "heapMax": 2147483648,
         "heapUsagePercent": 50.0
       },
       "alerts": {
         "activeCount": 0,
         "active": [],
         "historyCount": 5
       },
       "system": {
         "healthStatus": "UP",
         "running": true,
         "monitoringEnabled": true
       }
     }
     ```

#### 健康状态

系统支持以下健康状态：

- **STARTING**: 启动中
- **UP**: 运行正常
- **DEGRADED**: 降级运行（部分功能受限）
- **DOWN**: 服务不可用

#### 配置参数

在 `MonitoringConfig` 中配置：

```yaml
monitoring:
  enabled: true
  healthCheckPort: 8080  # 健康检查端口
  latencyThreshold: 60000  # 延迟阈值（毫秒）
  checkpointFailureRateThreshold: 0.1  # Checkpoint失败率阈值
  backpressureThreshold: 0.8  # 反压阈值
```

### 2. 使用示例

#### 基本使用

```java
// 1. 创建监控配置
MonitoringConfig config = MonitoringConfig.builder()
    .enabled(true)
    .healthCheckPort(8080)
    .build();

// 2. 创建监控服务和指标报告器
MonitoringService monitoringService = new MonitoringService(config);
MetricsReporter metricsReporter = new MetricsReporter(monitoringService);

// 3. 创建告警管理器
AlertManager alertManager = new AlertManager(config, metricsReporter);

// 4. 创建并启动健康检查服务器
HealthCheckServer healthCheckServer = new HealthCheckServer(config, metricsReporter, alertManager);
healthCheckServer.start();

// 5. 设置健康状态
healthCheckServer.setHealthStatus(HealthCheckServer.HealthStatus.UP);
```

#### 在Flink作业中集成

```java
public class MyFlinkJob {
    public void run() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // 创建监控组件
        MonitoringConfig config = loadMonitoringConfig();
        MonitoringService monitoringService = new MonitoringService(config);
        MetricsReporter metricsReporter = new MetricsReporter(monitoringService);
        AlertManager alertManager = new AlertManager(config, metricsReporter);
        
        // 启动健康检查服务器
        HealthCheckServer healthCheckServer = new HealthCheckServer(config, metricsReporter, alertManager);
        healthCheckServer.start();
        
        // 构建Flink作业
        DataStream<Event> stream = env.addSource(new MySource());
        // ... 其他处理逻辑
        
        // 执行作业
        env.execute("My Flink Job");
    }
}
```

### 3. Docker集成

#### Dockerfile健康检查

```dockerfile
FROM openjdk:11-jre-slim

# 安装curl用于健康检查
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# 复制应用
COPY target/realtime-data-pipeline-1.0.0-SNAPSHOT.jar /app/app.jar

# 暴露健康检查端口
EXPOSE 8080

# 配置健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

# 启动应用
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

#### Docker Compose配置

```yaml
version: '3.8'

services:
  flink-jobmanager:
    image: realtime-pipeline:latest
    ports:
      - "8080:8080"  # 健康检查端口
      - "8081:8081"  # Flink Web UI
    environment:
      - MONITORING_ENABLED=true
      - MONITORING_HEALTH_CHECK_PORT=8080
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s
    networks:
      - flink-network

  flink-taskmanager:
    image: realtime-pipeline:latest
    depends_on:
      flink-jobmanager:
        condition: service_healthy
    environment:
      - MONITORING_ENABLED=true
      - MONITORING_HEALTH_CHECK_PORT=8080
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s
    networks:
      - flink-network

networks:
  flink-network:
    driver: bridge
```

### 4. Kubernetes集成

#### Deployment配置

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: flink-jobmanager
  labels:
    app: flink
    component: jobmanager
spec:
  replicas: 1
  selector:
    matchLabels:
      app: flink
      component: jobmanager
  template:
    metadata:
      labels:
        app: flink
        component: jobmanager
    spec:
      containers:
      - name: jobmanager
        image: realtime-pipeline:latest
        ports:
        - containerPort: 8080
          name: health
        - containerPort: 8081
          name: webui
        env:
        - name: MONITORING_ENABLED
          value: "true"
        - name: MONITORING_HEALTH_CHECK_PORT
          value: "8080"
        
        # 存活探测：检查应用是否还在运行
        livenessProbe:
          httpGet:
            path: /health/live
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        
        # 就绪探测：检查应用是否准备好接收流量
        readinessProbe:
          httpGet:
            path: /health/ready
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3
        
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
```

#### Service配置

```yaml
apiVersion: v1
kind: Service
metadata:
  name: flink-jobmanager
  labels:
    app: flink
    component: jobmanager
spec:
  type: ClusterIP
  ports:
  - name: health
    port: 8080
    targetPort: 8080
  - name: webui
    port: 8081
    targetPort: 8081
  selector:
    app: flink
    component: jobmanager
```

### 5. 测试验证

#### 单元测试

创建了 `HealthCheckServerTest` 类，包含20个测试用例：

1. 构造函数测试
2. 启动/停止测试
3. 健康状态设置测试
4. 各个端点的功能测试
5. 错误处理测试
6. 并发请求测试

所有测试均通过。

#### 手动测试

启动健康检查服务器后，可以使用以下命令测试：

```bash
# 基本健康检查
curl http://localhost:8080/health

# 存活探测
curl http://localhost:8080/health/live

# 就绪探测
curl http://localhost:8080/health/ready

# 详细指标
curl http://localhost:8080/metrics
```

### 6. 特性

1. **轻量级实现**：使用JDK内置的 `HttpServer`，无需额外依赖
2. **多端点支持**：提供不同粒度的健康检查端点
3. **容器友好**：完全支持Docker和Kubernetes的健康探测机制
4. **详细指标**：提供丰富的系统指标信息
5. **告警集成**：与告警管理器集成，显示活动告警
6. **线程安全**：支持并发请求
7. **优雅关闭**：停止时等待正在处理的请求完成

### 7. 最佳实践

1. **端口选择**：使用独立的端口（如8080）用于健康检查，避免与Flink Web UI端口冲突
2. **超时配置**：合理配置探测超时时间，避免误判
3. **启动延迟**：设置适当的 `initialDelaySeconds`，给应用足够的启动时间
4. **探测频率**：根据应用特性调整 `periodSeconds`，平衡及时性和资源消耗
5. **失败阈值**：设置合理的 `failureThreshold`，避免因临时波动导致重启
6. **分离探测**：使用不同的端点进行存活探测和就绪探测
7. **监控集成**：将健康检查指标集成到监控系统中

## 文件清单

### 实现文件
- `src/main/java/com/realtime/pipeline/monitoring/HealthCheckServer.java` - 健康检查服务器
- `src/main/java/com/realtime/pipeline/monitoring/HealthCheckExample.java` - 使用示例

### 测试文件
- `src/test/java/com/realtime/pipeline/monitoring/HealthCheckServerTest.java` - 单元测试

### 文档文件
- `docs/TASK_10.4_HEALTH_CHECK.md` - 本文档

## 总结

成功实现了健康检查接口，满足需求7.8的要求。实现包括：

1. ✅ HTTP健康检查端点（/health）
2. ✅ 存活探测端点（/health/live）
3. ✅ 就绪探测端点（/health/ready）
4. ✅ 详细指标端点（/metrics）
5. ✅ 返回系统状态和关键指标
6. ✅ 支持Docker和Kubernetes健康探测
7. ✅ 完整的单元测试覆盖
8. ✅ 详细的使用文档和示例

健康检查接口已经可以用于生产环境，支持容器编排系统的自动化健康管理。
