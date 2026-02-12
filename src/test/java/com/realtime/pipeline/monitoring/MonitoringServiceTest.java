package com.realtime.pipeline.monitoring;

import com.realtime.pipeline.config.MonitoringConfig;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.Gauge;
import org.apache.flink.metrics.groups.OperatorMetricGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * MonitoringService单元测试
 * 
 * 测试范围:
 * 1. 服务初始化
 * 2. 指标注册（Counter、Gauge、Histogram、Meter）
 * 3. 指标值获取和更新
 * 4. 配置验证
 */
class MonitoringServiceTest {

    private MonitoringConfig config;
    private RuntimeContext mockRuntimeContext;
    private OperatorMetricGroup mockMetricGroup;
    private OperatorMetricGroup mockSubGroup;

    @BeforeEach
    void setUp() {
        config = MonitoringConfig.builder()
            .enabled(true)
            .metricsInterval(60)
            .latencyThreshold(60000L)
            .checkpointFailureRateThreshold(0.1)
            .build();

        // Mock RuntimeContext和MetricGroup
        mockRuntimeContext = mock(RuntimeContext.class);
        mockMetricGroup = mock(OperatorMetricGroup.class);
        mockSubGroup = mock(OperatorMetricGroup.class);
        
        when(mockRuntimeContext.getMetricGroup()).thenReturn(mockMetricGroup);
        when(mockMetricGroup.addGroup(anyString())).thenReturn(mockSubGroup);
        
        // Mock Counter
        Counter mockCounter = mock(Counter.class);
        when(mockMetricGroup.counter(anyString())).thenReturn(mockCounter);
        when(mockSubGroup.counter(anyString())).thenReturn(mockCounter);
    }

    @Test
    @DisplayName("测试MonitoringService创建")
    void testMonitoringServiceCreation() {
        MonitoringService service = new MonitoringService(config);
        
        assertNotNull(service);
        assertEquals(config, service.getConfig());
        assertTrue(service.isEnabled());
        assertFalse(service.isInitialized());
    }

    @Test
    @DisplayName("测试创建时配置为null抛出异常")
    void testCreationWithNullConfig() {
        assertThrows(IllegalArgumentException.class, () -> {
            new MonitoringService(null);
        });
    }

    @Test
    @DisplayName("测试MonitoringService初始化")
    void testInitialization() {
        MonitoringService service = new MonitoringService(config);
        
        assertFalse(service.isInitialized());
        
        service.initialize(mockRuntimeContext);
        
        assertTrue(service.isInitialized());
    }

    @Test
    @DisplayName("测试初始化时RuntimeContext为null抛出异常")
    void testInitializationWithNullRuntimeContext() {
        MonitoringService service = new MonitoringService(config);
        
        assertThrows(IllegalArgumentException.class, () -> {
            service.initialize(null);
        });
    }

    @Test
    @DisplayName("测试禁用监控时跳过初始化")
    void testInitializationWhenDisabled() {
        MonitoringConfig disabledConfig = MonitoringConfig.builder()
            .enabled(false)
            .build();
        
        MonitoringService service = new MonitoringService(disabledConfig);
        service.initialize(mockRuntimeContext);
        
        // 即使调用了initialize，服务也不应该被标记为已初始化
        assertFalse(service.isInitialized());
    }

    @Test
    @DisplayName("测试未初始化时注册指标抛出异常")
    void testRegisterMetricWithoutInitialization() {
        MonitoringService service = new MonitoringService(config);
        
        assertThrows(IllegalStateException.class, () -> {
            service.registerCounter("test.counter");
        });
    }

    @Test
    @DisplayName("测试注册Counter指标")
    void testRegisterCounter() {
        MonitoringService service = new MonitoringService(config);
        service.initialize(mockRuntimeContext);
        
        Counter counter = service.registerCounter("test.counter");
        
        assertNotNull(counter);
        assertTrue(service.hasMetric("test.counter"));
        assertEquals(1, service.getMetricCount());
        
        verify(mockMetricGroup).counter("test.counter");
    }

    @Test
    @DisplayName("测试注册带分组的Counter指标")
    void testRegisterCounterWithGroup() {
        MonitoringService service = new MonitoringService(config);
        service.initialize(mockRuntimeContext);
        
        Counter counter = service.registerCounter("throughput", "records.in");
        
        assertNotNull(counter);
        assertTrue(service.hasMetric("throughput.records.in"));
        
        verify(mockMetricGroup).addGroup("throughput");
        verify(mockSubGroup).counter("records.in");
    }

    @Test
    @DisplayName("测试注册Gauge指标")
    void testRegisterGauge() {
        MonitoringService service = new MonitoringService(config);
        service.initialize(mockRuntimeContext);
        
        Gauge<Long> gauge = () -> 100L;
        service.registerGauge("test.gauge", gauge);
        
        assertTrue(service.hasMetric("test.gauge"));
        assertEquals(1, service.getMetricCount());
        
        verify(mockMetricGroup).gauge(eq("test.gauge"), any(Gauge.class));
    }

    @Test
    @DisplayName("测试注册带分组的Gauge指标")
    void testRegisterGaugeWithGroup() {
        MonitoringService service = new MonitoringService(config);
        service.initialize(mockRuntimeContext);
        
        Gauge<Double> gauge = () -> 0.5;
        service.registerGauge("resource", "cpu.usage", gauge);
        
        assertTrue(service.hasMetric("resource.cpu.usage"));
        
        verify(mockMetricGroup).addGroup("resource");
        verify(mockSubGroup).gauge(eq("cpu.usage"), any(Gauge.class));
    }

    @Test
    @DisplayName("测试更新指标值")
    void testUpdateMetricValue() {
        MonitoringService service = new MonitoringService(config);
        service.initialize(mockRuntimeContext);
        
        service.registerCounter("test.counter");
        service.updateMetricValue("test.counter", 42L);
        
        MonitoringService.MetricValue metricValue = service.getMetric("test.counter");
        assertNotNull(metricValue);
        assertEquals(42L, metricValue.getValue());
    }

    @Test
    @DisplayName("测试获取不存在的指标返回null")
    void testGetNonExistentMetric() {
        MonitoringService service = new MonitoringService(config);
        service.initialize(mockRuntimeContext);
        
        assertNull(service.getMetric("non.existent"));
        assertFalse(service.hasMetric("non.existent"));
    }

    @Test
    @DisplayName("测试获取所有指标")
    void testGetAllMetrics() {
        MonitoringService service = new MonitoringService(config);
        service.initialize(mockRuntimeContext);
        
        service.registerCounter("counter1");
        service.registerCounter("counter2");
        service.registerGauge("gauge1", () -> 100L);
        
        Map<String, MonitoringService.MetricValue> allMetrics = service.getAllMetrics();
        
        assertEquals(3, allMetrics.size());
        assertTrue(allMetrics.containsKey("counter1"));
        assertTrue(allMetrics.containsKey("counter2"));
        assertTrue(allMetrics.containsKey("gauge1"));
    }

    @Test
    @DisplayName("测试指标摘要生成")
    void testGetMetricsSummary() {
        MonitoringService service = new MonitoringService(config);
        service.initialize(mockRuntimeContext);
        
        service.registerCounter("test.counter");
        service.registerGauge("test.gauge", () -> 100L);
        
        String summary = service.getMetricsSummary();
        
        assertNotNull(summary);
        assertTrue(summary.contains("Enabled: true"));
        assertTrue(summary.contains("Initialized: true"));
        assertTrue(summary.contains("Total Metrics: 2"));
        assertTrue(summary.contains("test.counter"));
        assertTrue(summary.contains("test.gauge"));
    }

    @Test
    @DisplayName("测试MetricValue类")
    void testMetricValue() {
        MonitoringService.MetricValue value = new MonitoringService.MetricValue(
            MonitoringService.MetricType.COUNTER, 100L);
        
        assertEquals(MonitoringService.MetricType.COUNTER, value.getType());
        assertEquals(100L, value.getValue());
        assertTrue(value.getLastUpdateTime() > 0);
        
        value.setValue(200L);
        assertEquals(200L, value.getValue());
        
        long newTime = System.currentTimeMillis();
        value.setLastUpdateTime(newTime);
        assertEquals(newTime, value.getLastUpdateTime());
        
        String str = value.toString();
        assertTrue(str.contains("COUNTER"));
        assertTrue(str.contains("200"));
    }

    @Test
    @DisplayName("测试多个指标注册")
    void testMultipleMetricRegistration() {
        MonitoringService service = new MonitoringService(config);
        service.initialize(mockRuntimeContext);
        
        // 注册多种类型的指标
        service.registerCounter("throughput", "records.in");
        service.registerCounter("throughput", "records.out");
        service.registerGauge("latency", "average", () -> 50L);
        service.registerGauge("latency", "p99", () -> 100L);
        
        assertEquals(4, service.getMetricCount());
        assertTrue(service.hasMetric("throughput.records.in"));
        assertTrue(service.hasMetric("throughput.records.out"));
        assertTrue(service.hasMetric("latency.average"));
        assertTrue(service.hasMetric("latency.p99"));
    }

    @Test
    @DisplayName("测试配置获取")
    void testGetConfig() {
        MonitoringService service = new MonitoringService(config);
        
        MonitoringConfig retrievedConfig = service.getConfig();
        
        assertNotNull(retrievedConfig);
        assertEquals(config.getMetricsInterval(), retrievedConfig.getMetricsInterval());
        assertEquals(config.getLatencyThreshold(), retrievedConfig.getLatencyThreshold());
        assertEquals(config.getCheckpointFailureRateThreshold(), 
            retrievedConfig.getCheckpointFailureRateThreshold());
    }

    @Test
    @DisplayName("测试空指标列表的摘要")
    void testMetricsSummaryWithNoMetrics() {
        MonitoringService service = new MonitoringService(config);
        service.initialize(mockRuntimeContext);
        
        String summary = service.getMetricsSummary();
        
        assertNotNull(summary);
        assertTrue(summary.contains("Total Metrics: 0"));
        assertFalse(summary.contains("Registered Metrics:"));
    }

    @Test
    @DisplayName("测试指标类型枚举")
    void testMetricTypeEnum() {
        assertEquals(4, MonitoringService.MetricType.values().length);
        assertNotNull(MonitoringService.MetricType.valueOf("COUNTER"));
        assertNotNull(MonitoringService.MetricType.valueOf("GAUGE"));
        assertNotNull(MonitoringService.MetricType.valueOf("HISTOGRAM"));
        assertNotNull(MonitoringService.MetricType.valueOf("METER"));
    }
}
