package com.realtime.pipeline.flink.recovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 恢复监控器
 * 定期监控恢复操作和Checkpoint清理
 * 
 * 验证需求:
 * - 需求 4.3: WHEN 恢复操作 THEN THE System SHALL 在10分钟内完成恢复
 * - 需求 4.4: THE System SHALL 保留最近3个Checkpoint
 */
public class RecoveryMonitor implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(RecoveryMonitor.class);

    private final FaultRecoveryManager recoveryManager;
    private final long reportIntervalMs;
    private transient ScheduledExecutorService scheduler;
    private transient volatile boolean running = false;

    /**
     * 构造函数
     * @param recoveryManager 故障恢复管理器
     * @param reportIntervalMs 报告间隔（毫秒）
     */
    public RecoveryMonitor(FaultRecoveryManager recoveryManager, long reportIntervalMs) {
        if (recoveryManager == null) {
            throw new IllegalArgumentException("FaultRecoveryManager cannot be null");
        }
        if (reportIntervalMs <= 0) {
            throw new IllegalArgumentException("Report interval must be positive");
        }

        this.recoveryManager = recoveryManager;
        this.reportIntervalMs = reportIntervalMs;
    }

    /**
     * 启动监控
     */
    public synchronized void start() {
        if (running) {
            logger.warn("RecoveryMonitor is already running");
            return;
        }

        logger.info("Starting RecoveryMonitor with report interval: {} ms", reportIntervalMs);

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "RecoveryMonitor");
            thread.setDaemon(true);
            return thread;
        });

        // 定期报告恢复指标
        scheduler.scheduleAtFixedRate(
            this::reportMetrics,
            reportIntervalMs,
            reportIntervalMs,
            TimeUnit.MILLISECONDS
        );

        // 定期清理旧的Checkpoint
        scheduler.scheduleAtFixedRate(
            this::cleanupCheckpoints,
            reportIntervalMs,
            reportIntervalMs,
            TimeUnit.MILLISECONDS
        );

        running = true;
        logger.info("RecoveryMonitor started successfully");
    }

    /**
     * 停止监控
     */
    public synchronized void stop() {
        if (!running) {
            logger.warn("RecoveryMonitor is not running");
            return;
        }

        logger.info("Stopping RecoveryMonitor");

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        running = false;
        logger.info("RecoveryMonitor stopped");
    }

    /**
     * 报告恢复指标
     */
    private void reportMetrics() {
        try {
            String summary = recoveryManager.getRecoveryMetricsSummary();
            logger.info("Recovery Metrics Report: {}", summary);

            // 检查恢复时间是否超过阈值
            long lastDuration = recoveryManager.getLastRecoveryDuration();
            long threshold = recoveryManager.getRecoveryTimeThreshold();

            if (lastDuration > 0 && lastDuration > threshold) {
                logger.warn("ALERT: Last recovery time ({} ms) exceeded threshold ({} ms)",
                    lastDuration, threshold);
            }

            // 检查恢复失败率
            double failureRate = recoveryManager.getRecoveryFailureRate();
            if (failureRate > 10.0) {
                logger.warn("ALERT: Recovery failure rate ({:.2f}%) exceeds 10% threshold",
                    failureRate);
            }
        } catch (Exception e) {
            logger.error("Error reporting recovery metrics", e);
        }
    }

    /**
     * 清理旧的Checkpoint
     */
    private void cleanupCheckpoints() {
        try {
            int cleanedCount = recoveryManager.cleanupOldCheckpoints();
            if (cleanedCount > 0) {
                logger.info("Checkpoint cleanup: removed {} old checkpoints", cleanedCount);
            }
        } catch (Exception e) {
            logger.error("Error cleaning up checkpoints", e);
        }
    }

    /**
     * 检查监控器是否正在运行
     * @return true如果正在运行
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * 获取报告间隔
     * @return 报告间隔（毫秒）
     */
    public long getReportIntervalMs() {
        return reportIntervalMs;
    }

    /**
     * 获取故障恢复管理器
     * @return FaultRecoveryManager
     */
    public FaultRecoveryManager getRecoveryManager() {
        return recoveryManager;
    }
}
