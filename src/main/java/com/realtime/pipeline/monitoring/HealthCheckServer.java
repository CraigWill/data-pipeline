package com.realtime.pipeline.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.realtime.pipeline.config.MonitoringConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * 健康检查服务器
 * 提供HTTP健康检查端点，返回系统状态和关键指标
 * 
 * 验证需求:
 * - 需求 7.8: THE System SHALL 提供健康检查接口
 * 
 * 功能:
 * 1. 实现HTTP健康检查端点
 * 2. 返回系统状态和关键指标
 * 3. 支持容器编排系统（Docker、Kubernetes）的健康探测
 * 
 * 端点:
 * - GET /health - 基本健康检查（返回200 OK或503 Service Unavailable）
 * - GET /health/live - 存活探测（Liveness Probe）
 * - GET /health/ready - 就绪探测（Readiness Probe）
 * - GET /metrics - 详细指标信息
 */
public class HealthCheckServer {
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckServer.class);
    
    private final MonitoringConfig config;
    private final MetricsReporter metricsReporter;
    private final AlertManager alertManager;
    private final ObjectMapper objectMapper;
    private HttpServer server;
    private volatile boolean running = false;
    private volatile HealthStatus healthStatus = HealthStatus.STARTING;
    
    // 健康检查配置
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_HOST = "0.0.0.0";
    private static final int BACKLOG = 0; // 使用系统默认值
    
    /**
     * 构造函数
     * @param config 监控配置
     * @param metricsReporter 指标报告器
     * @param alertManager 告警管理器
     */
    public HealthCheckServer(MonitoringConfig config, MetricsReporter metricsReporter, AlertManager alertManager) {
        if (config == null) {
            throw new IllegalArgumentException("MonitoringConfig cannot be null");
        }
        if (metricsReporter == null) {
            throw new IllegalArgumentException("MetricsReporter cannot be null");
        }
        
        this.config = config;
        this.metricsReporter = metricsReporter;
        this.alertManager = alertManager;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        logger.info("HealthCheckServer created");
    }
    
    /**
     * 启动健康检查服务器
     * @throws IOException 如果启动失败
     */
    public void start() throws IOException {
        if (running) {
            logger.warn("HealthCheckServer is already running");
            return;
        }
        
        if (!config.isEnabled()) {
            logger.info("Monitoring is disabled, HealthCheckServer will not start");
            return;
        }
        
        int port = config.getHealthCheckPort() > 0 ? config.getHealthCheckPort() : DEFAULT_PORT;
        
        try {
            // 创建HTTP服务器
            server = HttpServer.create(new InetSocketAddress(DEFAULT_HOST, port), BACKLOG);
            
            // 注册端点
            server.createContext("/health", new HealthHandler());
            server.createContext("/health/live", new LivenessHandler());
            server.createContext("/health/ready", new ReadinessHandler());
            server.createContext("/metrics", new MetricsHandler());
            
            // 使用线程池处理请求
            server.setExecutor(Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "HealthCheck-Handler");
                t.setDaemon(true);
                return t;
            }));
            
            // 启动服务器
            server.start();
            running = true;
            healthStatus = HealthStatus.UP;
            
            logger.info("HealthCheckServer started on port {}", port);
            logger.info("Health check endpoints:");
            logger.info("  - GET http://{}:{}/health", DEFAULT_HOST, port);
            logger.info("  - GET http://{}:{}/health/live", DEFAULT_HOST, port);
            logger.info("  - GET http://{}:{}/health/ready", DEFAULT_HOST, port);
            logger.info("  - GET http://{}:{}/metrics", DEFAULT_HOST, port);
            
        } catch (IOException e) {
            logger.error("Failed to start HealthCheckServer on port {}", port, e);
            throw e;
        }
    }
    
    /**
     * 停止健康检查服务器
     */
    public void stop() {
        if (!running) {
            logger.warn("HealthCheckServer is not running");
            return;
        }
        
        healthStatus = HealthStatus.DOWN;
        
        if (server != null) {
            server.stop(5); // 等待5秒让正在处理的请求完成
            server = null;
        }
        
        running = false;
        logger.info("HealthCheckServer stopped");
    }
    
    /**
     * 设置健康状态
     * @param status 健康状态
     */
    public void setHealthStatus(HealthStatus status) {
        this.healthStatus = status;
        logger.debug("Health status changed to: {}", status);
    }
    
    /**
     * 获取健康状态
     * @return 健康状态
     */
    public HealthStatus getHealthStatus() {
        return healthStatus;
    }
    
    /**
     * 检查是否正在运行
     * @return 如果正在运行返回true
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * 基本健康检查处理器
     * 返回系统整体健康状态和关键指标
     */
    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    sendResponse(exchange, 405, "Method Not Allowed");
                    return;
                }
                
                Map<String, Object> response = new HashMap<>();
                response.put("status", healthStatus.name());
                response.put("timestamp", System.currentTimeMillis());
                
                // 添加关键指标
                Map<String, Object> metrics = new HashMap<>();
                metrics.put("latency", Map.of(
                    "average", metricsReporter.getAverageLatency(),
                    "last", metricsReporter.getLastLatency()
                ));
                
                if (metricsReporter.getCheckpointListener() != null) {
                    metrics.put("checkpoint", Map.of(
                        "successRate", metricsReporter.getCheckpointListener().getSuccessRate(),
                        "failureRate", metricsReporter.getCheckpointListener().getFailureRate(),
                        "lastDuration", metricsReporter.getCheckpointListener().getLastCheckpointDuration()
                    ));
                }
                
                metrics.put("backpressure", Map.of(
                    "level", metricsReporter.getBackpressureLevel()
                ));
                
                response.put("metrics", metrics);
                
                // 添加活动告警
                if (alertManager != null) {
                    Map<String, AlertManager.Alert> activeAlerts = alertManager.getActiveAlerts();
                    response.put("activeAlerts", activeAlerts.size());
                    if (!activeAlerts.isEmpty()) {
                        response.put("alerts", activeAlerts.values());
                    }
                }
                
                // 根据健康状态返回HTTP状态码
                int statusCode = healthStatus == HealthStatus.UP ? 200 : 503;
                sendJsonResponse(exchange, statusCode, response);
                
            } catch (Exception e) {
                logger.error("Error handling health check request", e);
                sendResponse(exchange, 500, "Internal Server Error");
            }
        }
    }
    
    /**
     * 存活探测处理器
     * 用于Kubernetes Liveness Probe
     * 检查应用是否还在运行（不检查依赖服务）
     */
    private class LivenessHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    sendResponse(exchange, 405, "Method Not Allowed");
                    return;
                }
                
                Map<String, Object> response = new HashMap<>();
                
                // 存活探测只检查应用本身是否运行
                boolean alive = running && healthStatus != HealthStatus.DOWN;
                
                response.put("status", alive ? "UP" : "DOWN");
                response.put("timestamp", System.currentTimeMillis());
                
                int statusCode = alive ? 200 : 503;
                sendJsonResponse(exchange, statusCode, response);
                
            } catch (Exception e) {
                logger.error("Error handling liveness probe", e);
                sendResponse(exchange, 500, "Internal Server Error");
            }
        }
    }
    
    /**
     * 就绪探测处理器
     * 用于Kubernetes Readiness Probe
     * 检查应用是否准备好接收流量（检查依赖服务和关键指标）
     */
    private class ReadinessHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    sendResponse(exchange, 405, "Method Not Allowed");
                    return;
                }
                
                Map<String, Object> response = new HashMap<>();
                
                // 就绪探测检查应用是否准备好处理请求
                boolean ready = isReady();
                
                response.put("status", ready ? "READY" : "NOT_READY");
                response.put("timestamp", System.currentTimeMillis());
                
                // 添加就绪检查详情
                Map<String, Object> checks = new HashMap<>();
                checks.put("running", running);
                checks.put("healthStatus", healthStatus.name());
                checks.put("monitoringInitialized", metricsReporter.getMonitoringService().isInitialized());
                
                // 检查关键指标是否正常
                if (metricsReporter.getCheckpointListener() != null) {
                    double failureRate = metricsReporter.getCheckpointListener().getFailureRate();
                    checks.put("checkpointHealthy", failureRate <= config.getCheckpointFailureRateThreshold());
                }
                
                long avgLatency = metricsReporter.getAverageLatency();
                checks.put("latencyHealthy", avgLatency <= config.getLatencyThreshold());
                
                double backpressure = metricsReporter.getBackpressureLevel();
                checks.put("backpressureHealthy", backpressure <= config.getBackpressureThreshold());
                
                response.put("checks", checks);
                
                int statusCode = ready ? 200 : 503;
                sendJsonResponse(exchange, statusCode, response);
                
            } catch (Exception e) {
                logger.error("Error handling readiness probe", e);
                sendResponse(exchange, 500, "Internal Server Error");
            }
        }
    }
    
    /**
     * 指标端点处理器
     * 返回详细的系统指标信息
     */
    private class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    sendResponse(exchange, 405, "Method Not Allowed");
                    return;
                }
                
                Map<String, Object> response = new HashMap<>();
                response.put("timestamp", System.currentTimeMillis());
                
                // 延迟指标
                Map<String, Object> latency = new HashMap<>();
                latency.put("average", metricsReporter.getAverageLatency());
                latency.put("last", metricsReporter.getLastLatency());
                response.put("latency", latency);
                
                // Checkpoint指标
                if (metricsReporter.getCheckpointListener() != null) {
                    Map<String, Object> checkpoint = new HashMap<>();
                    checkpoint.put("successRate", metricsReporter.getCheckpointListener().getSuccessRate());
                    checkpoint.put("failureRate", metricsReporter.getCheckpointListener().getFailureRate());
                    checkpoint.put("totalCount", metricsReporter.getCheckpointListener().getTotalCheckpoints());
                    checkpoint.put("successCount", metricsReporter.getCheckpointListener().getSuccessfulCheckpoints());
                    checkpoint.put("failureCount", metricsReporter.getCheckpointListener().getFailedCheckpoints());
                    checkpoint.put("averageDuration", metricsReporter.getCheckpointListener().getAverageCheckpointDuration());
                    checkpoint.put("lastDuration", metricsReporter.getCheckpointListener().getLastCheckpointDuration());
                    response.put("checkpoint", checkpoint);
                }
                
                // 反压指标
                Map<String, Object> backpressure = new HashMap<>();
                backpressure.put("level", metricsReporter.getBackpressureLevel());
                response.put("backpressure", backpressure);
                
                // 资源使用指标
                Map<String, Object> resources = new HashMap<>();
                Runtime runtime = Runtime.getRuntime();
                resources.put("heapUsed", runtime.totalMemory() - runtime.freeMemory());
                resources.put("heapTotal", runtime.totalMemory());
                resources.put("heapMax", runtime.maxMemory());
                resources.put("heapUsagePercent", 
                    (double) (runtime.totalMemory() - runtime.freeMemory()) / runtime.totalMemory() * 100);
                response.put("resources", resources);
                
                // 告警信息
                if (alertManager != null) {
                    Map<String, Object> alerts = new HashMap<>();
                    alerts.put("activeCount", alertManager.getActiveAlerts().size());
                    alerts.put("active", alertManager.getActiveAlerts().values());
                    alerts.put("historyCount", alertManager.getAlertHistory().size());
                    response.put("alerts", alerts);
                }
                
                // 系统信息
                Map<String, Object> system = new HashMap<>();
                system.put("healthStatus", healthStatus.name());
                system.put("running", running);
                system.put("monitoringEnabled", config.isEnabled());
                response.put("system", system);
                
                sendJsonResponse(exchange, 200, response);
                
            } catch (Exception e) {
                logger.error("Error handling metrics request", e);
                sendResponse(exchange, 500, "Internal Server Error");
            }
        }
    }
    
    /**
     * 检查系统是否就绪
     * @return 如果系统就绪返回true
     */
    private boolean isReady() {
        // 基本检查
        if (!running || healthStatus == HealthStatus.DOWN || healthStatus == HealthStatus.STARTING) {
            return false;
        }
        
        // 检查监控服务是否初始化
        if (!metricsReporter.getMonitoringService().isInitialized()) {
            return false;
        }
        
        // 检查关键指标是否正常
        if (metricsReporter.getCheckpointListener() != null) {
            double failureRate = metricsReporter.getCheckpointListener().getFailureRate();
            if (failureRate > config.getCheckpointFailureRateThreshold()) {
                return false;
            }
        }
        
        // 检查延迟
        long avgLatency = metricsReporter.getAverageLatency();
        if (avgLatency > config.getLatencyThreshold() && avgLatency > 0) {
            return false;
        }
        
        // 检查反压
        double backpressure = metricsReporter.getBackpressureLevel();
        if (backpressure > config.getBackpressureThreshold()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 发送JSON响应
     * @param exchange HTTP交换对象
     * @param statusCode HTTP状态码
     * @param data 响应数据
     * @throws IOException 如果发送失败
     */
    private void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        String json = objectMapper.writeValueAsString(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    /**
     * 发送文本响应
     * @param exchange HTTP交换对象
     * @param statusCode HTTP状态码
     * @param message 响应消息
     * @throws IOException 如果发送失败
     */
    private void sendResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    /**
     * 健康状态枚举
     */
    public enum HealthStatus {
        /** 启动中 */
        STARTING,
        /** 运行正常 */
        UP,
        /** 降级运行 */
        DEGRADED,
        /** 服务不可用 */
        DOWN
    }
}
