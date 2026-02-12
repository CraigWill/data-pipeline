package com.realtime.pipeline.monitoring;

import com.realtime.pipeline.config.MonitoringConfig;
import com.realtime.pipeline.flink.checkpoint.CheckpointListener;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AlertManager使用示例
 * 
 * 演示如何在Flink作业中集成告警管理器，实现：
 * 1. 延迟告警（需求 7.6）
 * 2. Checkpoint失败率告警（需求 7.7）
 * 3. 系统负载告警（需求 5.6）
 * 4. 自定义告警规则
 */
public class AlertManagerExample {
    private static final Logger logger = LoggerFactory.getLogger(AlertManagerExample.class);

    /**
     * 示例1: 基本使用
     */
    public static void basicUsage() {
        // 1. 创建监控配置
        MonitoringConfig config = MonitoringConfig.builder()
            .enabled(true)
            .metricsInterval(60) // 每60秒检查一次
            .latencyThreshold(60000L) // 延迟阈值60秒
            .checkpointFailureRateThreshold(0.1) // Checkpoint失败率阈值10%
            .loadThreshold(0.8) // 系统负载阈值80%
            .backpressureThreshold(0.8) // 反压阈值0.8
            .alertMethod("log") // 告警方式：日志
            .build();

        // 2. 创建监控服务和指标报告器
        MonitoringService monitoringService = new MonitoringService(config);
        MetricsReporter metricsReporter = new MetricsReporter(monitoringService);

        // 3. 创建Checkpoint监听器
        CheckpointListener checkpointListener = new CheckpointListener();
        metricsReporter.setCheckpointListener(checkpointListener);

        // 4. 创建告警管理器
        AlertManager alertManager = new AlertManager(config, metricsReporter);

        // 5. 启动告警管理器
        alertManager.start();

        logger.info("AlertManager started with basic configuration");

        // 6. 模拟一些指标更新
        try {
            // 模拟延迟
            metricsReporter.recordLatency(65000); // 超过阈值
            
            // 模拟反压
            metricsReporter.updateBackpressureLevel(0.85); // 超过阈值
            
            // 等待告警检查
            TimeUnit.SECONDS.sleep(65);
            
            // 查看活动告警
            Map<String, AlertManager.Alert> activeAlerts = alertManager.getActiveAlerts();
            logger.info("Active alerts: {}", activeAlerts.size());
            activeAlerts.forEach((name, alert) -> {
                logger.info("Alert: {} - {}", name, alert.getMessage());
            });
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // 7. 关闭告警管理器
            alertManager.shutdown();
        }
    }

    /**
     * 示例2: 使用Webhook告警
     */
    public static void webhookUsage() {
        MonitoringConfig config = MonitoringConfig.builder()
            .enabled(true)
            .metricsInterval(30)
            .latencyThreshold(60000L)
            .checkpointFailureRateThreshold(0.1)
            .loadThreshold(0.8)
            .alertMethod("webhook")
            .webhookUrl("https://hooks.slack.com/services/YOUR/WEBHOOK/URL")
            .build();

        MonitoringService monitoringService = new MonitoringService(config);
        MetricsReporter metricsReporter = new MetricsReporter(monitoringService);
        AlertManager alertManager = new AlertManager(config, metricsReporter);

        alertManager.start();
        logger.info("AlertManager started with webhook notifications");

        // 使用完毕后关闭
        // alertManager.shutdown();
    }

    /**
     * 示例3: 添加自定义告警规则
     */
    public static void customRuleUsage() {
        MonitoringConfig config = MonitoringConfig.builder()
            .enabled(true)
            .metricsInterval(60)
            .latencyThreshold(60000L)
            .alertMethod("log")
            .build();

        MonitoringService monitoringService = new MonitoringService(config);
        MetricsReporter metricsReporter = new MetricsReporter(monitoringService);
        AlertManager alertManager = new AlertManager(config, metricsReporter);

        // 添加自定义规则：监控P99延迟
        AlertManager.AlertRule p99LatencyRule = AlertManager.AlertRule.builder()
            .name("p99_latency_high")
            .description("P99 latency exceeds 5 seconds")
            .severity(AlertManager.AlertSeverity.WARNING)
            .condition(metrics -> {
                // 检查P99延迟是否超过5秒
                Object p99 = metrics.get("latency.p99");
                if (p99 instanceof Long) {
                    return (Long) p99 > 5000;
                }
                return false;
            })
            .messageProvider(metrics -> {
                Object p99 = metrics.get("latency.p99");
                return String.format("P99 latency (%d ms) exceeds 5000 ms", p99);
            })
            .enabled(true)
            .build();

        alertManager.addRule(p99LatencyRule);

        // 添加自定义规则：监控失败记录数
        AlertManager.AlertRule failedRecordsRule = AlertManager.AlertRule.builder()
            .name("failed_records_high")
            .description("Failed records count exceeds threshold")
            .severity(AlertManager.AlertSeverity.ERROR)
            .condition(metrics -> {
                // 检查失败记录数是否超过100
                Object failedCount = metrics.get("records.failed");
                if (failedCount instanceof Long) {
                    return (Long) failedCount > 100;
                }
                return false;
            })
            .messageProvider(metrics -> {
                Object failedCount = metrics.get("records.failed");
                return String.format("Failed records count (%d) exceeds 100", failedCount);
            })
            .enabled(true)
            .build();

        alertManager.addRule(failedRecordsRule);

        alertManager.start();
        logger.info("AlertManager started with custom rules");

        // 使用完毕后关闭
        // alertManager.shutdown();
    }

    /**
     * 示例4: 在Flink算子中使用
     */
    public static class EventProcessorWithAlerts extends RichMapFunction<String, String> {
        private transient MonitoringService monitoringService;
        private transient MetricsReporter metricsReporter;
        private transient AlertManager alertManager;
        private transient CheckpointListener checkpointListener;

        @Override
        public void open(Configuration parameters) throws Exception {
            // 1. 创建监控配置
            MonitoringConfig config = MonitoringConfig.builder()
                .enabled(true)
                .metricsInterval(60)
                .latencyThreshold(60000L)
                .checkpointFailureRateThreshold(0.1)
                .loadThreshold(0.8)
                .alertMethod("log")
                .build();

            // 2. 初始化监控服务
            monitoringService = new MonitoringService(config);
            monitoringService.initialize(getRuntimeContext());

            // 3. 创建指标报告器
            metricsReporter = new MetricsReporter(monitoringService);
            metricsReporter.registerAllMetrics();

            // 4. 创建Checkpoint监听器
            checkpointListener = new CheckpointListener();
            metricsReporter.setCheckpointListener(checkpointListener);

            // 5. 创建并启动告警管理器
            alertManager = new AlertManager(config, metricsReporter);
            alertManager.start();

            logger.info("EventProcessor with alerts initialized");
        }

        @Override
        public String map(String value) throws Exception {
            long startTime = System.currentTimeMillis();

            try {
                // 记录输入
                metricsReporter.recordInput();

                // 处理数据
                String result = processData(value);

                // 记录延迟
                long latency = System.currentTimeMillis() - startTime;
                metricsReporter.recordLatency(latency);

                // 记录输出
                metricsReporter.recordOutput();

                return result;
            } catch (Exception e) {
                // 记录失败
                metricsReporter.recordFailure();
                throw e;
            }
        }

        private String processData(String value) {
            // 实际的数据处理逻辑
            return value.toUpperCase();
        }

        @Override
        public void close() throws Exception {
            // 关闭告警管理器
            if (alertManager != null) {
                alertManager.shutdown();
            }
            logger.info("EventProcessor with alerts closed");
        }
    }

    /**
     * 示例5: 管理告警规则
     */
    public static void ruleManagement() {
        MonitoringConfig config = MonitoringConfig.builder()
            .enabled(true)
            .metricsInterval(60)
            .latencyThreshold(60000L)
            .alertMethod("log")
            .build();

        MonitoringService monitoringService = new MonitoringService(config);
        MetricsReporter metricsReporter = new MetricsReporter(monitoringService);
        AlertManager alertManager = new AlertManager(config, metricsReporter);

        // 查看所有规则
        Map<String, AlertManager.AlertRule> rules = alertManager.getAllRules();
        logger.info("Total rules: {}", rules.size());
        rules.forEach((name, rule) -> {
            logger.info("Rule: {} - {} (enabled: {})", 
                name, rule.getDescription(), rule.isEnabled());
        });

        // 禁用某个规则
        alertManager.disableRule("backpressure");
        logger.info("Disabled backpressure rule");

        // 启用某个规则
        alertManager.enableRule("backpressure");
        logger.info("Enabled backpressure rule");

        // 移除某个规则
        boolean removed = alertManager.removeRule("backpressure");
        logger.info("Removed backpressure rule: {}", removed);

        // 获取特定规则
        AlertManager.AlertRule latencyRule = alertManager.getRule("latency_threshold");
        if (latencyRule != null) {
            logger.info("Latency rule severity: {}", latencyRule.getSeverity());
        }

        alertManager.shutdown();
    }

    /**
     * 示例6: 查看告警历史
     */
    public static void alertHistory() {
        MonitoringConfig config = MonitoringConfig.builder()
            .enabled(true)
            .metricsInterval(10)
            .latencyThreshold(60000L)
            .alertMethod("log")
            .build();

        MonitoringService monitoringService = new MonitoringService(config);
        MetricsReporter metricsReporter = new MetricsReporter(monitoringService);
        AlertManager alertManager = new AlertManager(config, metricsReporter);

        alertManager.start();

        try {
            // 触发一些告警
            metricsReporter.recordLatency(70000); // 超过阈值
            TimeUnit.SECONDS.sleep(15);

            // 查看活动告警
            Map<String, AlertManager.Alert> activeAlerts = alertManager.getActiveAlerts();
            logger.info("Active alerts: {}", activeAlerts.size());

            // 查看告警历史
            java.util.List<AlertManager.Alert> history = alertManager.getAlertHistory();
            logger.info("Alert history: {} alerts", history.size());
            
            // 查看最近10条告警
            java.util.List<AlertManager.Alert> recentAlerts = alertManager.getAlertHistory(10);
            recentAlerts.forEach(alert -> {
                logger.info("Alert: {} - {} at {}", 
                    alert.getRuleName(), 
                    alert.getMessage(), 
                    alert.getFormattedTimestamp());
            });

            // 清除告警历史
            alertManager.clearAlertHistory();
            logger.info("Alert history cleared");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            alertManager.shutdown();
        }
    }

    /**
     * 主函数 - 运行示例
     */
    public static void main(String[] args) {
        logger.info("=== AlertManager Examples ===");

        logger.info("\n--- Example 1: Basic Usage ---");
        basicUsage();

        logger.info("\n--- Example 5: Rule Management ---");
        ruleManagement();

        logger.info("\n--- Example 6: Alert History ---");
        alertHistory();

        logger.info("\n=== Examples Complete ===");
    }
}
