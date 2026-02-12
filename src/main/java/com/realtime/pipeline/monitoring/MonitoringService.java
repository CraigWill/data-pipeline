package com.realtime.pipeline.monitoring;

import com.realtime.pipeline.config.MonitoringConfig;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.metrics.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 监控服务
 * 负责集成Flink Metrics系统，实现自定义指标注册，暴露Metrics接口
 * 
 * 验证需求:
 * - 需求 7.1: THE System SHALL 暴露Flink的Metrics接口
 * 
 * 功能:
 * 1. 集成Flink Metrics系统
 * 2. 实现自定义指标注册
 * 3. 暴露Metrics接口（JMX或REST）
 * 4. 支持Counter、Gauge、Histogram、Meter等指标类型
 */
public class MonitoringService implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(MonitoringService.class);

    private final MonitoringConfig config;
    private transient RuntimeContext runtimeContext;
    private final Map<String, MetricValue> customMetrics;
    private volatile boolean initialized = false;

    /**
     * 构造函数
     * @param config 监控配置
     */
    public MonitoringService(MonitoringConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("MonitoringConfig cannot be null");
        }
        this.config = config;
        this.customMetrics = new ConcurrentHashMap<>();
        logger.info("MonitoringService created with config: enabled={}, metricsInterval={}s",
            config.isEnabled(), config.getMetricsInterval());
    }

    /**
     * 初始化监控服务
     * @param runtimeContext Flink运行时上下文
     */
    public void initialize(RuntimeContext runtimeContext) {
        if (!config.isEnabled()) {
            logger.info("Monitoring is disabled, skipping initialization");
            return;
        }

        if (runtimeContext == null) {
            throw new IllegalArgumentException("RuntimeContext cannot be null");
        }

        this.runtimeContext = runtimeContext;
        this.initialized = true;
        logger.info("MonitoringService initialized with RuntimeContext");
    }

    /**
     * 检查是否已初始化
     */
    private void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("MonitoringService not initialized. Call initialize() first.");
        }
    }

    /**
     * 注册Counter指标
     * @param name 指标名称
     * @return Counter实例
     */
    public Counter registerCounter(String name) {
        checkInitialized();
        
        Counter counter = runtimeContext.getMetricGroup().counter(name);
        customMetrics.put(name, new MetricValue(MetricType.COUNTER, 0L));
        
        logger.debug("Registered counter metric: {}", name);
        return counter;
    }

    /**
     * 注册Counter指标（带分组）
     * @param group 指标组
     * @param name 指标名称
     * @return Counter实例
     */
    public Counter registerCounter(String group, String name) {
        checkInitialized();
        
        Counter counter = runtimeContext.getMetricGroup()
            .addGroup(group)
            .counter(name);
        
        String fullName = group + "." + name;
        customMetrics.put(fullName, new MetricValue(MetricType.COUNTER, 0L));
        
        logger.debug("Registered counter metric: {}.{}", group, name);
        return counter;
    }

    /**
     * 注册Gauge指标
     * @param name 指标名称
     * @param gauge Gauge实例
     * @param <T> Gauge值类型
     */
    public <T> void registerGauge(String name, Gauge<T> gauge) {
        checkInitialized();
        
        runtimeContext.getMetricGroup().gauge(name, gauge);
        customMetrics.put(name, new MetricValue(MetricType.GAUGE, null));
        
        logger.debug("Registered gauge metric: {}", name);
    }

    /**
     * 注册Gauge指标（带分组）
     * @param group 指标组
     * @param name 指标名称
     * @param gauge Gauge实例
     * @param <T> Gauge值类型
     */
    public <T> void registerGauge(String group, String name, Gauge<T> gauge) {
        checkInitialized();
        
        runtimeContext.getMetricGroup()
            .addGroup(group)
            .gauge(name, gauge);
        
        String fullName = group + "." + name;
        customMetrics.put(fullName, new MetricValue(MetricType.GAUGE, null));
        
        logger.debug("Registered gauge metric: {}.{}", group, name);
    }

    /**
     * 注册Histogram指标
     * @param name 指标名称
     * @param histogram Histogram实例
     */
    public void registerHistogram(String name, Histogram histogram) {
        checkInitialized();
        
        runtimeContext.getMetricGroup().histogram(name, histogram);
        customMetrics.put(name, new MetricValue(MetricType.HISTOGRAM, null));
        
        logger.debug("Registered histogram metric: {}", name);
    }

    /**
     * 注册Histogram指标（带分组）
     * @param group 指标组
     * @param name 指标名称
     * @param histogram Histogram实例
     */
    public void registerHistogram(String group, String name, Histogram histogram) {
        checkInitialized();
        
        runtimeContext.getMetricGroup()
            .addGroup(group)
            .histogram(name, histogram);
        
        String fullName = group + "." + name;
        customMetrics.put(fullName, new MetricValue(MetricType.HISTOGRAM, null));
        
        logger.debug("Registered histogram metric: {}.{}", group, name);
    }

    /**
     * 注册Meter指标
     * @param name 指标名称
     * @param meter Meter实例
     */
    public void registerMeter(String name, Meter meter) {
        checkInitialized();
        
        runtimeContext.getMetricGroup().meter(name, meter);
        customMetrics.put(name, new MetricValue(MetricType.METER, null));
        
        logger.debug("Registered meter metric: {}", name);
    }

    /**
     * 注册Meter指标（带分组）
     * @param group 指标组
     * @param name 指标名称
     * @param meter Meter实例
     */
    public void registerMeter(String group, String name, Meter meter) {
        checkInitialized();
        
        runtimeContext.getMetricGroup()
            .addGroup(group)
            .meter(name, meter);
        
        String fullName = group + "." + name;
        customMetrics.put(fullName, new MetricValue(MetricType.METER, null));
        
        logger.debug("Registered meter metric: {}.{}", group, name);
    }

    /**
     * 更新自定义指标值（用于跟踪）
     * @param name 指标名称
     * @param value 指标值
     */
    public void updateMetricValue(String name, Object value) {
        MetricValue metricValue = customMetrics.get(name);
        if (metricValue != null) {
            metricValue.setValue(value);
            metricValue.setLastUpdateTime(System.currentTimeMillis());
        }
    }

    /**
     * 获取指标值
     * @param name 指标名称
     * @return 指标值，如果不存在返回null
     */
    public MetricValue getMetric(String name) {
        return customMetrics.get(name);
    }

    /**
     * 获取所有指标
     * @return 所有指标的映射
     */
    public Map<String, MetricValue> getAllMetrics() {
        return new ConcurrentHashMap<>(customMetrics);
    }

    /**
     * 获取指标数量
     * @return 已注册的指标数量
     */
    public int getMetricCount() {
        return customMetrics.size();
    }

    /**
     * 检查指标是否存在
     * @param name 指标名称
     * @return 如果指标存在返回true
     */
    public boolean hasMetric(String name) {
        return customMetrics.containsKey(name);
    }

    /**
     * 获取监控配置
     * @return 监控配置
     */
    public MonitoringConfig getConfig() {
        return config;
    }

    /**
     * 检查监控是否启用
     * @return 如果启用返回true
     */
    public boolean isEnabled() {
        return config.isEnabled();
    }

    /**
     * 检查是否已初始化
     * @return 如果已初始化返回true
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 获取指标摘要
     * @return 指标摘要字符串
     */
    public String getMetricsSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Monitoring Service Metrics Summary:\n");
        sb.append(String.format("  Enabled: %s\n", config.isEnabled()));
        sb.append(String.format("  Initialized: %s\n", initialized));
        sb.append(String.format("  Total Metrics: %d\n", customMetrics.size()));
        
        if (!customMetrics.isEmpty()) {
            sb.append("  Registered Metrics:\n");
            customMetrics.forEach((name, value) -> {
                sb.append(String.format("    - %s: type=%s, value=%s\n", 
                    name, value.getType(), value.getValue()));
            });
        }
        
        return sb.toString();
    }

    /**
     * 指标类型枚举
     */
    public enum MetricType {
        COUNTER,
        GAUGE,
        HISTOGRAM,
        METER
    }

    /**
     * 指标值包装类
     */
    public static class MetricValue implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private final MetricType type;
        private volatile Object value;
        private volatile long lastUpdateTime;

        public MetricValue(MetricType type, Object value) {
            this.type = type;
            this.value = value;
            this.lastUpdateTime = System.currentTimeMillis();
        }

        public MetricType getType() {
            return type;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }

        public long getLastUpdateTime() {
            return lastUpdateTime;
        }

        public void setLastUpdateTime(long lastUpdateTime) {
            this.lastUpdateTime = lastUpdateTime;
        }

        @Override
        public String toString() {
            return String.format("MetricValue{type=%s, value=%s, lastUpdateTime=%d}", 
                type, value, lastUpdateTime);
        }
    }
}
