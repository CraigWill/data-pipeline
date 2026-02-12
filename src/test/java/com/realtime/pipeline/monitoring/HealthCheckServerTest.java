package com.realtime.pipeline.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtime.pipeline.config.MonitoringConfig;
import com.realtime.pipeline.flink.checkpoint.CheckpointListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * 健康检查服务器单元测试
 * 
 * 验证需求:
 * - 需求 7.8: THE System SHALL 提供健康检查接口
 */
class HealthCheckServerTest {
    
    private MonitoringConfig config;
    private MonitoringService monitoringService;
    private MetricsReporter metricsReporter;
    private AlertManager alertManager;
    private HealthCheckServer healthCheckServer;
    private ObjectMapper objectMapper;
    
    private static final int TEST_PORT = 18080; // 使用不同的端口避免冲突
    
    @BeforeEach
    void setUp() {
        config = MonitoringConfig.builder()
            .enabled(true)
            .metricsInterval(60)
            .latencyThreshold(60000L)
            .checkpointFailureRateThreshold(0.1)
            .loadThreshold(0.8)
            .backpressureThreshold(0.8)
            .healthCheckPort(TEST_PORT)
            .alertMethod("log")
            .build();
        
        monitoringService = new MonitoringService(config);
        metricsReporter = new MetricsReporter(monitoringService);
        alertManager = new AlertManager(config, metricsReporter);
        healthCheckServer = new HealthCheckServer(config, metricsReporter, alertManager);
        objectMapper = new ObjectMapper();
    }
    
    @AfterEach
    void tearDown() {
        if (healthCheckServer != null && healthCheckServer.isRunning()) {
            healthCheckServer.stop();
        }
        if (alertManager != null && alertManager.isRunning()) {
            alertManager.stop();
        }
    }
    
    @Test
    void testConstructor_WithValidParameters_ShouldSucceed() {
        assertThat(healthCheckServer).isNotNull();
        assertThat(healthCheckServer.isRunning()).isFalse();
        assertThat(healthCheckServer.getHealthStatus()).isEqualTo(HealthCheckServer.HealthStatus.STARTING);
    }
    
    @Test
    void testConstructor_WithNullConfig_ShouldThrowException() {
        assertThatThrownBy(() -> new HealthCheckServer(null, metricsReporter, alertManager))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("MonitoringConfig cannot be null");
    }
    
    @Test
    void testConstructor_WithNullMetricsReporter_ShouldThrowException() {
        assertThatThrownBy(() -> new HealthCheckServer(config, null, alertManager))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("MetricsReporter cannot be null");
    }
    
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testStart_ShouldStartServerSuccessfully() throws Exception {
        healthCheckServer.start();
        
        assertThat(healthCheckServer.isRunning()).isTrue();
        assertThat(healthCheckServer.getHealthStatus()).isEqualTo(HealthCheckServer.HealthStatus.UP);
        
        // 等待服务器完全启动
        Thread.sleep(500);
        
        // 验证服务器可以响应请求
        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + TEST_PORT + "/health").openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        int responseCode = conn.getResponseCode();
        assertThat(responseCode).isIn(200, 503); // 可能是UP或其他状态
        
        conn.disconnect();
    }
    
    @Test
    void testStart_WhenAlreadyRunning_ShouldLogWarning() throws Exception {
        healthCheckServer.start();
        assertThat(healthCheckServer.isRunning()).isTrue();
        
        // 再次启动应该只记录警告，不抛出异常
        healthCheckServer.start();
        assertThat(healthCheckServer.isRunning()).isTrue();
    }
    
    @Test
    void testStart_WhenMonitoringDisabled_ShouldNotStart() throws Exception {
        MonitoringConfig disabledConfig = MonitoringConfig.builder()
            .enabled(false)
            .healthCheckPort(TEST_PORT + 1)
            .build();
        
        HealthCheckServer disabledServer = new HealthCheckServer(disabledConfig, metricsReporter, alertManager);
        disabledServer.start();
        
        assertThat(disabledServer.isRunning()).isFalse();
    }
    
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testStop_ShouldStopServerSuccessfully() throws Exception {
        healthCheckServer.start();
        assertThat(healthCheckServer.isRunning()).isTrue();
        
        Thread.sleep(500);
        
        healthCheckServer.stop();
        assertThat(healthCheckServer.isRunning()).isFalse();
        assertThat(healthCheckServer.getHealthStatus()).isEqualTo(HealthCheckServer.HealthStatus.DOWN);
    }
    
    @Test
    void testStop_WhenNotRunning_ShouldLogWarning() {
        assertThat(healthCheckServer.isRunning()).isFalse();
        
        // 停止未运行的服务器应该只记录警告，不抛出异常
        healthCheckServer.stop();
        assertThat(healthCheckServer.isRunning()).isFalse();
    }
    
    @Test
    void testSetHealthStatus_ShouldUpdateStatus() {
        assertThat(healthCheckServer.getHealthStatus()).isEqualTo(HealthCheckServer.HealthStatus.STARTING);
        
        healthCheckServer.setHealthStatus(HealthCheckServer.HealthStatus.UP);
        assertThat(healthCheckServer.getHealthStatus()).isEqualTo(HealthCheckServer.HealthStatus.UP);
        
        healthCheckServer.setHealthStatus(HealthCheckServer.HealthStatus.DEGRADED);
        assertThat(healthCheckServer.getHealthStatus()).isEqualTo(HealthCheckServer.HealthStatus.DEGRADED);
        
        healthCheckServer.setHealthStatus(HealthCheckServer.HealthStatus.DOWN);
        assertThat(healthCheckServer.getHealthStatus()).isEqualTo(HealthCheckServer.HealthStatus.DOWN);
    }
    
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testHealthEndpoint_ShouldReturnSystemStatus() throws Exception {
        healthCheckServer.start();
        healthCheckServer.setHealthStatus(HealthCheckServer.HealthStatus.UP);
        
        Thread.sleep(500);
        
        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + TEST_PORT + "/health").openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        int responseCode = conn.getResponseCode();
        assertThat(responseCode).isEqualTo(200);
        
        String contentType = conn.getHeaderField("Content-Type");
        assertThat(contentType).contains("application/json");
        
        // 读取响应
        String response = new String(conn.getInputStream().readAllBytes());
        Map<String, Object> data = objectMapper.readValue(response, Map.class);
        
        assertThat(data).containsKey("status");
        assertThat(data).containsKey("timestamp");
        assertThat(data).containsKey("metrics");
        assertThat(data.get("status")).isEqualTo("UP");
        
        conn.disconnect();
    }
    
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testHealthEndpoint_WhenDown_ShouldReturn503() throws Exception {
        healthCheckServer.start();
        healthCheckServer.setHealthStatus(HealthCheckServer.HealthStatus.DOWN);
        
        Thread.sleep(500);
        
        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + TEST_PORT + "/health").openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        int responseCode = conn.getResponseCode();
        assertThat(responseCode).isEqualTo(503);
        
        conn.disconnect();
    }
    
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testHealthEndpoint_WithInvalidMethod_ShouldReturn405() throws Exception {
        healthCheckServer.start();
        
        Thread.sleep(500);
        
        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + TEST_PORT + "/health").openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        int responseCode = conn.getResponseCode();
        assertThat(responseCode).isEqualTo(405);
        
        conn.disconnect();
    }
    
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testLivenessEndpoint_ShouldReturnAliveStatus() throws Exception {
        healthCheckServer.start();
        
        Thread.sleep(500);
        
        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + TEST_PORT + "/health/live").openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        int responseCode = conn.getResponseCode();
        assertThat(responseCode).isEqualTo(200);
        
        String response = new String(conn.getInputStream().readAllBytes());
        Map<String, Object> data = objectMapper.readValue(response, Map.class);
        
        assertThat(data).containsKey("status");
        assertThat(data).containsKey("timestamp");
        assertThat(data.get("status")).isEqualTo("UP");
        
        conn.disconnect();
    }
    
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testLivenessEndpoint_WhenDown_ShouldReturn503() throws Exception {
        healthCheckServer.start();
        healthCheckServer.setHealthStatus(HealthCheckServer.HealthStatus.DOWN);
        
        Thread.sleep(500);
        
        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + TEST_PORT + "/health/live").openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        int responseCode = conn.getResponseCode();
        assertThat(responseCode).isEqualTo(503);
        
        conn.disconnect();
    }
    
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testReadinessEndpoint_ShouldReturnReadyStatus() throws Exception {
        healthCheckServer.start();
        healthCheckServer.setHealthStatus(HealthCheckServer.HealthStatus.UP);
        
        Thread.sleep(500);
        
        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + TEST_PORT + "/health/ready").openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        int responseCode = conn.getResponseCode();
        // 可能是200或503，取决于监控服务是否初始化
        assertThat(responseCode).isIn(200, 503);
        
        // 根据响应码选择正确的输入流
        String response;
        if (responseCode == 200) {
            response = new String(conn.getInputStream().readAllBytes());
        } else {
            response = new String(conn.getErrorStream().readAllBytes());
        }
        
        Map<String, Object> data = objectMapper.readValue(response, Map.class);
        
        assertThat(data).containsKey("status");
        assertThat(data).containsKey("timestamp");
        assertThat(data).containsKey("checks");
        
        conn.disconnect();
    }
    
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testMetricsEndpoint_ShouldReturnDetailedMetrics() throws Exception {
        // 设置一些指标
        metricsReporter.recordLatency(100);
        metricsReporter.recordLatency(200);
        metricsReporter.updateBackpressureLevel(0.5);
        
        CheckpointListener checkpointListener = new CheckpointListener();
        checkpointListener.notifyCheckpointStart(1L);
        checkpointListener.notifyCheckpointComplete(1L);
        metricsReporter.setCheckpointListener(checkpointListener);
        
        healthCheckServer.start();
        
        Thread.sleep(500);
        
        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + TEST_PORT + "/metrics").openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        int responseCode = conn.getResponseCode();
        assertThat(responseCode).isEqualTo(200);
        
        String response = new String(conn.getInputStream().readAllBytes());
        Map<String, Object> data = objectMapper.readValue(response, Map.class);
        
        assertThat(data).containsKey("timestamp");
        assertThat(data).containsKey("latency");
        assertThat(data).containsKey("checkpoint");
        assertThat(data).containsKey("backpressure");
        assertThat(data).containsKey("resources");
        assertThat(data).containsKey("system");
        
        // 验证延迟指标
        Map<String, Object> latency = (Map<String, Object>) data.get("latency");
        assertThat(latency).containsKey("average");
        assertThat(latency).containsKey("last");
        
        // 验证反压指标
        Map<String, Object> backpressure = (Map<String, Object>) data.get("backpressure");
        assertThat(backpressure).containsKey("level");
        
        conn.disconnect();
    }
    
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testMetricsEndpoint_WithAlerts_ShouldIncludeAlertInfo() throws Exception {
        // 设置高延迟触发告警
        metricsReporter.recordLatency(70000); // 超过60秒阈值
        
        CheckpointListener checkpointListener = new CheckpointListener();
        metricsReporter.setCheckpointListener(checkpointListener);
        
        alertManager.start();
        healthCheckServer.start();
        
        Thread.sleep(1000); // 等待告警检查
        
        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + TEST_PORT + "/metrics").openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        int responseCode = conn.getResponseCode();
        assertThat(responseCode).isEqualTo(200);
        
        String response = new String(conn.getInputStream().readAllBytes());
        Map<String, Object> data = objectMapper.readValue(response, Map.class);
        
        assertThat(data).containsKey("alerts");
        Map<String, Object> alerts = (Map<String, Object>) data.get("alerts");
        assertThat(alerts).containsKey("activeCount");
        assertThat(alerts).containsKey("historyCount");
        
        conn.disconnect();
    }
    
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testHealthEndpoint_WithActiveAlerts_ShouldIncludeAlertCount() throws Exception {
        // 设置高延迟触发告警
        metricsReporter.recordLatency(70000);
        
        CheckpointListener checkpointListener = new CheckpointListener();
        metricsReporter.setCheckpointListener(checkpointListener);
        
        alertManager.start();
        healthCheckServer.start();
        healthCheckServer.setHealthStatus(HealthCheckServer.HealthStatus.UP);
        
        Thread.sleep(1000); // 等待告警检查
        
        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + TEST_PORT + "/health").openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        int responseCode = conn.getResponseCode();
        assertThat(responseCode).isEqualTo(200);
        
        String response = new String(conn.getInputStream().readAllBytes());
        Map<String, Object> data = objectMapper.readValue(response, Map.class);
        
        assertThat(data).containsKey("activeAlerts");
        
        conn.disconnect();
    }
    
    @Test
    void testHealthStatus_AllValues_ShouldBeValid() {
        for (HealthCheckServer.HealthStatus status : HealthCheckServer.HealthStatus.values()) {
            healthCheckServer.setHealthStatus(status);
            assertThat(healthCheckServer.getHealthStatus()).isEqualTo(status);
        }
    }
    
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testConcurrentRequests_ShouldHandleMultipleRequests() throws Exception {
        healthCheckServer.start();
        
        Thread.sleep(500);
        
        // 并发发送多个请求
        Thread[] threads = new Thread[10];
        boolean[] results = new boolean[10];
        
        for (int i = 0; i < 10; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + TEST_PORT + "/health").openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    
                    int responseCode = conn.getResponseCode();
                    results[index] = (responseCode == 200 || responseCode == 503);
                    
                    conn.disconnect();
                } catch (IOException e) {
                    results[index] = false;
                }
            });
            threads[i].start();
        }
        
        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }
        
        // 验证所有请求都成功
        for (boolean result : results) {
            assertThat(result).isTrue();
        }
    }
}
