# MonitoringService 实现文档

## 概述

MonitoringService是实时数据管道系统的监控服务组件，负责集成Flink Metrics系统，实现自定义指标注册，并暴露Metrics接口（JMX或REST）。

## 功能特性

### 1. Flink Metrics系统集成

MonitoringService完全集成了Apache Flink的Metrics系统，支持：

- **Counter**: 计数器指标，用于记录累计值
- **Gauge**: 仪表指标，用于记录瞬时值
- **Histogram**: 直方图指标，用于记录值的分布
- **Meter**: 速率指标，用于记录事件发生的速率

### 2. 自定义指标注册

支持两种方式注册指标：

#### 简单注册（无分组）
```java
MonitoringService service = new MonitoringService(config);
service.initialize(runtimeContext);

// 注册Counter
Counter counter = service.registerCounter("my.counter");

// 注册Gauge
service.registerGauge("my.gauge", () -> 100L);
```

#### 分组注册
```java
// 注册带分组的Counter
Counter counter = service.registerCounter("throughput", "records.in");

// 注册带分组的Gauge
service.registerGauge("latency", "average", () -> getAverageLatency());
```

### 3. Metrics接口暴露

Flink自动通过以下方式暴露指标：

- **JMX**: 默认启用，可通过JConsole或其他JMX客户端访问
- **REST API**: 通过Flink Web UI的REST API访问（默认端口8081）
- **Prometheus**: 可配置Prometheus Reporter导出指标
- **日志**: 可配置定期将指标写入日志

## 核心组件

### MonitoringService

主要的监控服务类，提供指标注册和管理功能。

**主要方法：**

```java
// 初始化服务
void initialize(RuntimeContext runtimeContext)

// 注册Counter
Counter registerCounter(String name)
Counter registerCounter(String group, String name)

// 注册Gauge
<T> void registerGauge(String name, Gauge<T> gauge)
<T> void registerGauge(String group, String name, Gauge<T> gauge)

// 注册Histogram
void registerHistogram(String name, Histogram histogram)
void registerHistogram(String group, String name, Histogram histogram)

// 注册Meter
void registerMeter(String name, Meter meter)
void registerMeter(String group, String name, Meter meter)

// 获取指标
MetricValue getMetric(String name)
Map<String, MetricValue> getAllMetrics()

// 获取指标摘要
String getMetricsSummary()
```

### MetricsReporter

便捷的指标报告器，提供常用指标的注册和记录功能。

**支持的指标类型：**

1. **吞吐量指标** (需求 7.2)
   - `throughput.records.in`: 输入记录计数
   - `throughput.records.out`: 输出记录计数
   - `throughput.records.failed`: 失败记录计数
   - `throughput.records.in.rate`: 输入速率（记录/秒）
   - `throughput.records.out.rate`: 输出速率（记录/秒）

2. **延迟指标** (需求 7.3)
   - `latency.average`: 平均延迟
   - `latency.last`: 最后一次延迟
   - `latency.histogram`: 延迟分布直方图

3. **反压指标** (需求 7.5)
   - `backpressure.level`: 反压级别（0-1）

4. **资源使用指标**
   - `resource.cpu.usage`: CPU使用率
   - `resource.memory.usage`: 内存使用率

**主要方法：**

```java
// 注册指标
void registerThroughputMetrics()
void registerLatencyMetrics()
void registerBackpressureMetrics()
void registerResourceMetrics()

// 记录数据
void recordInput()
void recordInput(long count)
void recordOutput()
void recordOutput(long count)
void recordFailure()
void recordFailure(long count)
void recordLatency(long latencyMs)

// 获取统计
long getAverageLatency()
long getLastLatency()
void resetLatencyStats()
```

## 使用示例

### 基本使用

```java
// 1. 创建监控配置
MonitoringConfig config = MonitoringConfig.builder()
    .enabled(true)
    .metricsInterval(60)
    .latencyThreshold(60000L)
    .checkpointFailureRateThreshold(0.1)
    .build();

// 2. 在Flink算子中使用
public class MyMapFunction extends RichMapFunction<Input, Output> {
    private transient MonitoringService monitoringService;
    private transient MetricsReporter metricsReporter;
    
    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        
        // 初始化监控服务
        monitoringService = new MonitoringService(config);
        monitoringService.initialize(getRuntimeContext());
        
        // 创建指标报告器
        metricsReporter = new MetricsReporter(monitoringService);
        
        // 注册指标
        metricsReporter.registerThroughputMetrics();
        metricsReporter.registerLatencyMetrics();
    }
    
    @Override
    public Output map(Input input) throws Exception {
        long startTime = System.currentTimeMillis();
        
        // 记录输入
        metricsReporter.recordInput();
        
        // 处理数据
        Output output = process(input);
        
        // 记录输出
        metricsReporter.recordOutput();
        
        // 记录延迟
        long latency = System.currentTimeMillis() - startTime;
        metricsReporter.recordLatency(latency);
        
        return output;
    }
}
```

### 自定义指标

```java
@Override
public void open(Configuration parameters) throws Exception {
    super.open(parameters);
    
    monitoringService = new MonitoringService(config);
    monitoringService.initialize(getRuntimeContext());
    
    // 注册自定义Counter
    Counter customCounter = monitoringService.registerCounter("custom", "my.events");
    
    // 注册自定义Gauge
    monitoringService.registerGauge("custom", "queue.size", 
        () -> getQueueSize());
    
    // 注册自定义Histogram
    Histogram customHistogram = new SimpleHistogram();
    monitoringService.registerHistogram("custom", "processing.time", customHistogram);
}
```

## 配置说明

### MonitoringConfig参数

```yaml
monitoring:
  enabled: true                              # 是否启用监控
  metricsInterval: 60                        # Metrics报告间隔（秒）
  latencyThreshold: 60000                    # 延迟告警阈值（毫秒）
  checkpointFailureRateThreshold: 0.1        # Checkpoint失败率告警阈值
  loadThreshold: 0.8                         # 负载告警阈值
  backpressureThreshold: 0.8                 # 反压告警阈值
  healthCheckPort: 8081                      # 健康检查端口
  alertMethod: log                           # 告警方式: log, email, webhook
  webhookUrl: http://example.com/webhook     # Webhook URL（当alertMethod为webhook时）
```

### Flink Metrics配置

在`flink-conf.yaml`中配置Metrics Reporter：

```yaml
# JMX Reporter（默认启用）
metrics.reporter.jmx.factory.class: org.apache.flink.metrics.jmx.JMXReporterFactory
metrics.reporter.jmx.port: 9010-9025

# Prometheus Reporter
metrics.reporter.prom.factory.class: org.apache.flink.metrics.prometheus.PrometheusReporterFactory
metrics.reporter.prom.port: 9249

# 日志Reporter
metrics.reporter.slf4j.factory.class: org.apache.flink.metrics.slf4j.Slf4jReporterFactory
metrics.reporter.slf4j.interval: 60 SECONDS
```

## 访问Metrics

### 通过REST API

```bash
# 获取所有指标
curl http://localhost:8081/jobs/<job-id>/metrics

# 获取特定指标
curl http://localhost:8081/jobs/<job-id>/metrics?get=throughput.records.in
```

### 通过JMX

使用JConsole连接到Flink进程：

```bash
jconsole localhost:9010
```

在MBeans标签页中，导航到：
```
org.apache.flink.metrics
  └── taskmanager
      └── <tm-id>
          └── <job-name>
              └── <operator-name>
                  └── throughput
                      └── records.in
```

### 通过Prometheus

配置Prometheus抓取Flink指标：

```yaml
scrape_configs:
  - job_name: 'flink'
    static_configs:
      - targets: ['localhost:9249']
```

## 指标命名规范

指标名称遵循以下格式：

```
<group>.<metric_name>
```

示例：
- `throughput.records.in`
- `latency.average`
- `backpressure.level`
- `resource.cpu.usage`

## 性能考虑

1. **指标更新频率**: 避免过于频繁地更新指标，建议批量更新
2. **Histogram大小**: SimpleHistogram使用无界存储，对于高吞吐量场景考虑使用有界实现
3. **Gauge计算**: Gauge的计算应该快速，避免阻塞操作
4. **指标数量**: 避免注册过多指标，建议控制在100个以内

## 故障排查

### 指标未显示

1. 检查MonitoringService是否已初始化
2. 检查Flink Metrics Reporter配置
3. 检查JMX端口是否被占用
4. 查看Flink日志中的错误信息

### 指标值不准确

1. 确认指标注册时机正确
2. 检查是否有并发更新问题
3. 验证Gauge的计算逻辑
4. 检查Counter是否被正确递增

## 验证需求

MonitoringService实现满足以下需求：

- **需求 7.1**: THE System SHALL 暴露Flink的Metrics接口
  - ✅ 通过Flink Metrics系统暴露JMX和REST接口
  
- **需求 7.2**: THE System SHALL 记录每秒处理的记录数
  - ✅ 通过MetricsReporter的吞吐量指标实现
  
- **需求 7.3**: THE System SHALL 记录端到端的数据延迟
  - ✅ 通过MetricsReporter的延迟指标实现
  
- **需求 7.5**: THE System SHALL 记录Backpressure指标
  - ✅ 通过MetricsReporter的反压指标实现

## 相关文档

- [Flink Metrics官方文档](https://nightlies.apache.org/flink/flink-docs-stable/docs/ops/metrics/)
- [设计文档](../design.md) - 第5节：监控组件
- [需求文档](../requirements.md) - 需求7：监控和可观测性

## 下一步

Task 10.2将实现关键指标收集，包括：
- Checkpoint指标（成功率、耗时）
- 资源使用指标的实际采集
- 与CheckpointListener的集成
