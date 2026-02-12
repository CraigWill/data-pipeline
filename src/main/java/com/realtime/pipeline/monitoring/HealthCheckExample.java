package com.realtime.pipeline.monitoring;

import com.realtime.pipeline.config.MonitoringConfig;
import com.realtime.pipeline.flink.checkpoint.CheckpointListener;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 健康检查服务器使用示例
 * 演示如何启动和使用健康检查服务器
 */
public class HealthCheckExample {
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckExample.class);

    public static void main(String[] args) {
        try {
            // 1. 创建监控配置
            MonitoringConfig config = MonitoringConfig.builder()
                .enabled(true)
                .metricsInterval(60)
                .latencyThreshold(60000L)
                .checkpointFailureRateThreshold(0.1)
                .loadThreshold(0.8)
                .backpressureThreshold(0.8)
                .healthCheckPort(8080)
                .alertMethod("log")
                .build();

            // 2. 创建监控服务
            MonitoringService monitoringService = new MonitoringService(config);
            
            // 注意：在实际使用中，需要在Flink算子中初始化
            // monitoringService.initialize(getRuntimeContext());
            
            // 3. 创建指标报告器
            MetricsReporter metricsReporter = new MetricsReporter(monitoringService);
            
            // 4. 创建Checkpoint监听器（可选）
            CheckpointListener checkpointListener = new CheckpointListener();
            metricsReporter.setCheckpointListener(checkpointListener);
            
            // 5. 创建告警管理器
            AlertManager alertManager = new AlertManager(config, metricsReporter);
            
            // 6. 创建健康检查服务器
            HealthCheckServer healthCheckServer = new HealthCheckServer(config, metricsReporter, alertManager);
            
            // 7. 启动健康检查服务器
            healthCheckServer.start();
            
            logger.info("Health check server started successfully");
            logger.info("Try accessing:");
            logger.info("  - http://localhost:8080/health");
            logger.info("  - http://localhost:8080/health/live");
            logger.info("  - http://localhost:8080/health/ready");
            logger.info("  - http://localhost:8080/metrics");
            
            // 8. 模拟一些指标更新
            simulateMetrics(metricsReporter, checkpointListener);
            
            // 9. 启动告警管理器
            alertManager.start();
            
            // 保持运行
            logger.info("Press Ctrl+C to stop...");
            Thread.sleep(Long.MAX_VALUE);
            
        } catch (Exception e) {
            logger.error("Error running health check example", e);
        }
    }

    /**
     * 模拟指标更新
     */
    private static void simulateMetrics(MetricsReporter metricsReporter, CheckpointListener checkpointListener) {
        // 启动一个线程模拟指标更新
        Thread metricsThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    // 模拟延迟
                    long latency = 100 + (long) (Math.random() * 500);
                    metricsReporter.recordLatency(latency);
                    
                    // 模拟反压
                    double backpressure = Math.random() * 0.5;
                    metricsReporter.updateBackpressureLevel(backpressure);
                    
                    // 模拟Checkpoint
                    long checkpointId = System.currentTimeMillis();
                    checkpointListener.notifyCheckpointStart(checkpointId);
                    
                    if (Math.random() > 0.1) {
                        // 90%成功
                        checkpointListener.notifyCheckpointComplete(checkpointId);
                    } else {
                        // 10%失败
                        checkpointListener.notifyCheckpointAborted(
                            checkpointId,
                            "Simulated checkpoint failure"
                        );
                    }
                    
                    Thread.sleep(5000); // 每5秒更新一次
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Metrics-Simulator");
        
        metricsThread.setDaemon(true);
        metricsThread.start();
        
        logger.info("Metrics simulation started");
    }

    /**
     * 演示如何在Flink作业中集成健康检查
     */
    public static class FlinkIntegrationExample {
        
        public static void setupHealthCheck(RuntimeContext runtimeContext, MonitoringConfig config) {
            try {
                // 1. 创建监控服务并初始化
                MonitoringService monitoringService = new MonitoringService(config);
                monitoringService.initialize(runtimeContext);
                
                // 2. 创建指标报告器并注册所有指标
                MetricsReporter metricsReporter = new MetricsReporter(monitoringService);
                metricsReporter.registerAllMetrics();
                
                // 3. 创建告警管理器
                AlertManager alertManager = new AlertManager(config, metricsReporter);
                alertManager.start();
                
                // 4. 创建并启动健康检查服务器
                HealthCheckServer healthCheckServer = new HealthCheckServer(config, metricsReporter, alertManager);
                healthCheckServer.start();
                
                logger.info("Health check integration completed");
                
            } catch (Exception e) {
                logger.error("Failed to setup health check", e);
            }
        }
    }

    /**
     * 演示如何使用健康检查状态
     */
    public static class HealthStatusExample {
        
        public static void demonstrateHealthStatus(HealthCheckServer healthCheckServer) {
            // 设置为启动中
            healthCheckServer.setHealthStatus(HealthCheckServer.HealthStatus.STARTING);
            logger.info("Status: STARTING");
            
            // 模拟初始化完成
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // 设置为运行正常
            healthCheckServer.setHealthStatus(HealthCheckServer.HealthStatus.UP);
            logger.info("Status: UP");
            
            // 模拟降级
            healthCheckServer.setHealthStatus(HealthCheckServer.HealthStatus.DEGRADED);
            logger.info("Status: DEGRADED");
            
            // 恢复正常
            healthCheckServer.setHealthStatus(HealthCheckServer.HealthStatus.UP);
            logger.info("Status: UP");
        }
    }

    /**
     * 演示Docker健康检查配置
     */
    public static class DockerHealthCheckExample {
        
        public static void printDockerHealthCheckConfig() {
            logger.info("Docker Compose health check configuration:");
            logger.info("```yaml");
            logger.info("services:");
            logger.info("  flink-jobmanager:");
            logger.info("    image: realtime-pipeline:latest");
            logger.info("    healthcheck:");
            logger.info("      test: [\"CMD\", \"curl\", \"-f\", \"http://localhost:8080/health\"]");
            logger.info("      interval: 30s");
            logger.info("      timeout: 10s");
            logger.info("      retries: 3");
            logger.info("      start_period: 40s");
            logger.info("```");
            
            logger.info("\nKubernetes health check configuration:");
            logger.info("```yaml");
            logger.info("apiVersion: v1");
            logger.info("kind: Pod");
            logger.info("metadata:");
            logger.info("  name: flink-jobmanager");
            logger.info("spec:");
            logger.info("  containers:");
            logger.info("  - name: jobmanager");
            logger.info("    image: realtime-pipeline:latest");
            logger.info("    livenessProbe:");
            logger.info("      httpGet:");
            logger.info("        path: /health/live");
            logger.info("        port: 8080");
            logger.info("      initialDelaySeconds: 30");
            logger.info("      periodSeconds: 10");
            logger.info("      timeoutSeconds: 5");
            logger.info("      failureThreshold: 3");
            logger.info("    readinessProbe:");
            logger.info("      httpGet:");
            logger.info("        path: /health/ready");
            logger.info("        port: 8080");
            logger.info("      initialDelaySeconds: 10");
            logger.info("      periodSeconds: 5");
            logger.info("      timeoutSeconds: 3");
            logger.info("      failureThreshold: 3");
            logger.info("```");
        }
    }
}
