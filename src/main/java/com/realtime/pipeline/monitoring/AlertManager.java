package com.realtime.pipeline.monitoring;

import com.realtime.pipeline.config.MonitoringConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * 告警管理器
 * 负责监控系统指标并触发告警通知
 * 
 * 验证需求:
 * - 需求 7.6: WHEN 数据延迟超过60秒 THEN THE System SHALL 触发告警
 * - 需求 7.7: WHEN Checkpoint失败率超过10% THEN THE System SHALL 触发告警
 * - 需求 5.6: WHEN 系统负载超过80% THEN THE System SHALL 触发扩容告警
 * 
 * 功能:
 * 1. 实现告警规则配置
 * 2. 实现告警触发逻辑（延迟、Checkpoint失败率、负载）
 * 3. 实现告警通知（日志、邮件、Webhook）
 */
public class AlertManager implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(AlertManager.class);
    private static final DateTimeFormatter DATE_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final MonitoringConfig config;
    private final MetricsReporter metricsReporter;
    private final Map<String, AlertRule> alertRules;
    private final Map<String, Alert> activeAlerts;
    private final List<Alert> alertHistory;
    private final ScheduledExecutorService scheduler;
    private volatile boolean running = false;
    private ScheduledFuture<?> checkTask;

    // 告警抑制：同一告警在指定时间内不重复发送（默认5分钟）
    private static final long ALERT_SUPPRESSION_MILLIS = 5 * 60 * 1000L;
    private final Map<String, Long> lastAlertTime;

    /**
     * 构造函数
     * @param config 监控配置
     * @param metricsReporter 指标报告器
     */
    public AlertManager(MonitoringConfig config, MetricsReporter metricsReporter) {
        if (config == null) {
            throw new IllegalArgumentException("MonitoringConfig cannot be null");
        }
        if (metricsReporter == null) {
            throw new IllegalArgumentException("MetricsReporter cannot be null");
        }
        
        this.config = config;
        this.metricsReporter = metricsReporter;
        this.alertRules = new ConcurrentHashMap<>();
        this.activeAlerts = new ConcurrentHashMap<>();
        this.alertHistory = Collections.synchronizedList(new ArrayList<>());
        this.lastAlertTime = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "AlertManager-Checker");
            t.setDaemon(true);
            return t;
        });
        
        // 初始化默认告警规则
        initializeDefaultRules();
        
        logger.info("AlertManager created with config: latencyThreshold={}ms, " +
            "checkpointFailureRateThreshold={}%, loadThreshold={}%, alertMethod={}",
            config.getLatencyThreshold(),
            config.getCheckpointFailureRateThreshold() * 100,
            config.getLoadThreshold() * 100,
            config.getAlertMethod());
    }

    /**
     * 初始化默认告警规则
     */
    private void initializeDefaultRules() {
        // 延迟告警规则（需求 7.6）
        addRule(AlertRule.builder()
            .name("latency_threshold")
            .description("Data latency exceeds threshold")
            .severity(AlertSeverity.WARNING)
            .condition(metrics -> {
                long avgLatency = metricsReporter.getAverageLatency();
                return avgLatency > config.getLatencyThreshold();
            })
            .messageProvider(metrics -> String.format(
                "Average latency (%d ms) exceeds threshold (%d ms)",
                metricsReporter.getAverageLatency(),
                config.getLatencyThreshold()))
            .enabled(true)
            .build());

        // Checkpoint失败率告警规则（需求 7.7）
        addRule(AlertRule.builder()
            .name("checkpoint_failure_rate")
            .description("Checkpoint failure rate exceeds threshold")
            .severity(AlertSeverity.ERROR)
            .condition(metrics -> {
                if (metricsReporter.getCheckpointListener() == null) {
                    return false;
                }
                double failureRate = metricsReporter.getCheckpointListener().getFailureRate();
                return failureRate > config.getCheckpointFailureRateThreshold();
            })
            .messageProvider(metrics -> String.format(
                "Checkpoint failure rate (%.2f%%) exceeds threshold (%.2f%%)",
                metricsReporter.getCheckpointListener().getFailureRate(),
                config.getCheckpointFailureRateThreshold() * 100))
            .enabled(true)
            .build());

        // 系统负载告警规则（需求 5.6）
        addRule(AlertRule.builder()
            .name("system_load")
            .description("System load exceeds threshold")
            .severity(AlertSeverity.WARNING)
            .condition(metrics -> {
                // 检查CPU使用率
                double cpuUsage = getCpuUsage();
                if (cpuUsage > config.getLoadThreshold()) {
                    return true;
                }
                // 检查内存使用率
                double memoryUsage = getMemoryUsage();
                return memoryUsage > config.getLoadThreshold();
            })
            .messageProvider(metrics -> {
                double cpuUsage = getCpuUsage();
                double memoryUsage = getMemoryUsage();
                return String.format(
                    "System load exceeds threshold: CPU=%.2f%%, Memory=%.2f%%, Threshold=%.2f%%",
                    cpuUsage * 100, memoryUsage * 100, config.getLoadThreshold() * 100);
            })
            .enabled(true)
            .build());

        // 反压告警规则
        addRule(AlertRule.builder()
            .name("backpressure")
            .description("Backpressure level exceeds threshold")
            .severity(AlertSeverity.WARNING)
            .condition(metrics -> {
                double backpressure = metricsReporter.getBackpressureLevel();
                return backpressure > config.getBackpressureThreshold();
            })
            .messageProvider(metrics -> String.format(
                "Backpressure level (%.2f) exceeds threshold (%.2f)",
                metricsReporter.getBackpressureLevel(),
                config.getBackpressureThreshold()))
            .enabled(true)
            .build());

        logger.info("Initialized {} default alert rules", alertRules.size());
    }

    /**
     * 添加告警规则
     * @param rule 告警规则
     */
    public void addRule(AlertRule rule) {
        if (rule == null) {
            throw new IllegalArgumentException("AlertRule cannot be null");
        }
        alertRules.put(rule.getName(), rule);
        logger.debug("Added alert rule: {}", rule.getName());
    }

    /**
     * 移除告警规则
     * @param ruleName 规则名称
     * @return 如果规则存在并被移除返回true
     */
    public boolean removeRule(String ruleName) {
        AlertRule removed = alertRules.remove(ruleName);
        if (removed != null) {
            logger.debug("Removed alert rule: {}", ruleName);
            return true;
        }
        return false;
    }

    /**
     * 获取告警规则
     * @param ruleName 规则名称
     * @return 告警规则，如果不存在返回null
     */
    public AlertRule getRule(String ruleName) {
        return alertRules.get(ruleName);
    }

    /**
     * 获取所有告警规则
     * @return 告警规则映射
     */
    public Map<String, AlertRule> getAllRules() {
        return new HashMap<>(alertRules);
    }

    /**
     * 启用告警规则
     * @param ruleName 规则名称
     */
    public void enableRule(String ruleName) {
        AlertRule rule = alertRules.get(ruleName);
        if (rule != null) {
            rule.setEnabled(true);
            logger.info("Enabled alert rule: {}", ruleName);
        }
    }

    /**
     * 禁用告警规则
     * @param ruleName 规则名称
     */
    public void disableRule(String ruleName) {
        AlertRule rule = alertRules.get(ruleName);
        if (rule != null) {
            rule.setEnabled(false);
            logger.info("Disabled alert rule: {}", ruleName);
        }
    }

    /**
     * 启动告警管理器
     * 开始定期检查告警条件
     */
    public void start() {
        if (running) {
            logger.warn("AlertManager is already running");
            return;
        }

        if (!config.isEnabled()) {
            logger.info("Monitoring is disabled, AlertManager will not start");
            return;
        }

        running = true;
        
        // 每隔metricsInterval秒检查一次告警条件
        long intervalSeconds = config.getMetricsInterval();
        checkTask = scheduler.scheduleAtFixedRate(
            this::checkAlerts,
            intervalSeconds,
            intervalSeconds,
            TimeUnit.SECONDS
        );
        
        logger.info("AlertManager started, checking alerts every {} seconds", intervalSeconds);
    }

    /**
     * 停止告警管理器
     */
    public void stop() {
        if (!running) {
            logger.warn("AlertManager is not running");
            return;
        }

        running = false;
        
        if (checkTask != null) {
            checkTask.cancel(false);
            checkTask = null;
        }
        
        logger.info("AlertManager stopped");
    }

    /**
     * 关闭告警管理器
     * 释放所有资源
     */
    public void shutdown() {
        stop();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("AlertManager shutdown complete");
    }

    /**
     * 检查所有告警规则
     */
    private void checkAlerts() {
        try {
            Map<String, Object> metrics = collectMetrics();
            
            for (AlertRule rule : alertRules.values()) {
                if (!rule.isEnabled()) {
                    continue;
                }
                
                try {
                    checkRule(rule, metrics);
                } catch (Exception e) {
                    logger.error("Error checking alert rule: {}", rule.getName(), e);
                }
            }
        } catch (Exception e) {
            logger.error("Error in alert checking cycle", e);
        }
    }

    /**
     * 检查单个告警规则
     * @param rule 告警规则
     * @param metrics 指标数据
     */
    private void checkRule(AlertRule rule, Map<String, Object> metrics) {
        boolean conditionMet = rule.getCondition().test(metrics);
        String ruleName = rule.getName();
        
        if (conditionMet) {
            // 条件满足，触发告警
            if (!activeAlerts.containsKey(ruleName)) {
                // 新告警
                triggerAlert(rule, metrics);
            } else {
                // 已存在的告警，检查是否需要重新发送
                Long lastTime = lastAlertTime.get(ruleName);
                long now = System.currentTimeMillis();
                if (lastTime == null || (now - lastTime) > ALERT_SUPPRESSION_MILLIS) {
                    // 超过抑制时间，重新发送告警
                    triggerAlert(rule, metrics);
                }
            }
        } else {
            // 条件不满足，清除告警
            if (activeAlerts.containsKey(ruleName)) {
                clearAlert(ruleName);
            }
        }
    }

    /**
     * 触发告警
     * @param rule 告警规则
     * @param metrics 指标数据
     */
    private void triggerAlert(AlertRule rule, Map<String, Object> metrics) {
        String message = rule.getMessageProvider().apply(metrics);
        
        Alert alert = Alert.builder()
            .ruleName(rule.getName())
            .severity(rule.getSeverity())
            .message(message)
            .timestamp(System.currentTimeMillis())
            .metrics(new HashMap<>(metrics))
            .build();
        
        activeAlerts.put(rule.getName(), alert);
        alertHistory.add(alert);
        lastAlertTime.put(rule.getName(), alert.getTimestamp());
        
        // 发送告警通知
        sendAlert(alert);
        
        logger.warn("Alert triggered: {} - {}", rule.getName(), message);
    }

    /**
     * 清除告警
     * @param ruleName 规则名称
     */
    private void clearAlert(String ruleName) {
        Alert alert = activeAlerts.remove(ruleName);
        if (alert != null) {
            logger.info("Alert cleared: {}", ruleName);
        }
    }

    /**
     * 发送告警通知
     * @param alert 告警信息
     */
    private void sendAlert(Alert alert) {
        String method = config.getAlertMethod();
        
        try {
            switch (method) {
                case "log":
                    sendLogAlert(alert);
                    break;
                case "email":
                    sendEmailAlert(alert);
                    break;
                case "webhook":
                    sendWebhookAlert(alert);
                    break;
                default:
                    logger.warn("Unknown alert method: {}", method);
                    sendLogAlert(alert);
            }
        } catch (Exception e) {
            logger.error("Failed to send alert via {}: {}", method, alert.getMessage(), e);
            // 失败时回退到日志
            if (!"log".equals(method)) {
                sendLogAlert(alert);
            }
        }
    }

    /**
     * 发送日志告警
     * @param alert 告警信息
     */
    private void sendLogAlert(Alert alert) {
        String timestamp = DATE_FORMATTER.format(Instant.ofEpochMilli(alert.getTimestamp()));
        String logMessage = String.format(
            "[ALERT] [%s] [%s] %s (Time: %s)",
            alert.getSeverity(),
            alert.getRuleName(),
            alert.getMessage(),
            timestamp
        );
        
        switch (alert.getSeverity()) {
            case CRITICAL:
            case ERROR:
                logger.error(logMessage);
                break;
            case WARNING:
                logger.warn(logMessage);
                break;
            case INFO:
                logger.info(logMessage);
                break;
        }
    }

    /**
     * 发送邮件告警
     * @param alert 告警信息
     */
    private void sendEmailAlert(Alert alert) {
        // TODO: 实现邮件发送逻辑
        // 需要配置SMTP服务器、收件人等信息
        logger.info("Email alert (not implemented): {}", alert.getMessage());
        
        // 暂时回退到日志
        sendLogAlert(alert);
    }

    /**
     * 发送Webhook告警
     * @param alert 告警信息
     */
    private void sendWebhookAlert(Alert alert) {
        String webhookUrl = config.getWebhookUrl();
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            logger.error("Webhook URL not configured");
            sendLogAlert(alert);
            return;
        }
        
        // TODO: 实现HTTP POST请求发送告警
        // 需要使用HttpClient发送JSON格式的告警数据
        logger.info("Webhook alert to {} (not implemented): {}", webhookUrl, alert.getMessage());
        
        // 暂时回退到日志
        sendLogAlert(alert);
    }

    /**
     * 收集当前指标数据
     * @return 指标数据映射
     */
    private Map<String, Object> collectMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        // 延迟指标
        metrics.put("latency.average", metricsReporter.getAverageLatency());
        metrics.put("latency.last", metricsReporter.getLastLatency());
        
        // Checkpoint指标
        if (metricsReporter.getCheckpointListener() != null) {
            metrics.put("checkpoint.success.rate", metricsReporter.getCheckpointListener().getSuccessRate());
            metrics.put("checkpoint.failure.rate", metricsReporter.getCheckpointListener().getFailureRate());
        }
        
        // 反压指标
        metrics.put("backpressure.level", metricsReporter.getBackpressureLevel());
        
        // 资源指标
        metrics.put("cpu.usage", getCpuUsage());
        metrics.put("memory.usage", getMemoryUsage());
        
        return metrics;
    }

    /**
     * 获取CPU使用率
     * @return CPU使用率（0-1）
     */
    private double getCpuUsage() {
        try {
            java.lang.management.OperatingSystemMXBean osBean = 
                java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sunOsBean = 
                    (com.sun.management.OperatingSystemMXBean) osBean;
                double cpuLoad = sunOsBean.getProcessCpuLoad();
                return cpuLoad >= 0 ? cpuLoad : 0.0;
            }
            return 0.0;
        } catch (Exception e) {
            logger.debug("Failed to get CPU usage", e);
            return 0.0;
        }
    }

    /**
     * 获取内存使用率
     * @return 内存使用率（0-1）
     */
    private double getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        return totalMemory > 0 ? (double) (totalMemory - freeMemory) / totalMemory : 0.0;
    }

    /**
     * 获取活动告警
     * @return 活动告警映射
     */
    public Map<String, Alert> getActiveAlerts() {
        return new HashMap<>(activeAlerts);
    }

    /**
     * 获取告警历史
     * @return 告警历史列表
     */
    public List<Alert> getAlertHistory() {
        synchronized (alertHistory) {
            return new ArrayList<>(alertHistory);
        }
    }

    /**
     * 获取告警历史（限制数量）
     * @param limit 最大数量
     * @return 告警历史列表
     */
    public List<Alert> getAlertHistory(int limit) {
        synchronized (alertHistory) {
            int size = alertHistory.size();
            int fromIndex = Math.max(0, size - limit);
            return new ArrayList<>(alertHistory.subList(fromIndex, size));
        }
    }

    /**
     * 清除告警历史
     */
    public void clearAlertHistory() {
        synchronized (alertHistory) {
            alertHistory.clear();
        }
        logger.info("Alert history cleared");
    }

    /**
     * 检查是否正在运行
     * @return 如果正在运行返回true
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * 获取配置
     * @return 监控配置
     */
    public MonitoringConfig getConfig() {
        return config;
    }

    /**
     * 获取指标报告器
     * @return 指标报告器
     */
    public MetricsReporter getMetricsReporter() {
        return metricsReporter;
    }

    /**
     * 告警规则
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AlertRule implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String name;
        private String description;
        private AlertSeverity severity;
        private java.util.function.Predicate<Map<String, Object>> condition;
        private java.util.function.Function<Map<String, Object>, String> messageProvider;
        private boolean enabled;
    }

    /**
     * 告警信息
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Alert implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String ruleName;
        private AlertSeverity severity;
        private String message;
        private long timestamp;
        private Map<String, Object> metrics;
        
        public String getFormattedTimestamp() {
            return DATE_FORMATTER.format(Instant.ofEpochMilli(timestamp));
        }
    }

    /**
     * 告警严重级别
     */
    public enum AlertSeverity {
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }
}
