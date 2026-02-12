package com.realtime.pipeline.flink.checkpoint;

import org.apache.flink.runtime.checkpoint.CheckpointException;
import org.apache.flink.streaming.api.checkpoint.ListCheckpointed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Checkpoint监听器
 * 负责监听Checkpoint成功和失败事件，记录Checkpoint指标（耗时、成功率）
 * 
 * 验证需求:
 * - 需求 2.5: WHEN Checkpoint失败 THEN THE Flink SHALL 记录错误日志并继续处理
 * - 需求 4.6: THE System SHALL 记录所有故障事件到日志系统
 */
public class CheckpointListener implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(CheckpointListener.class);

    // Checkpoint指标
    private final AtomicLong totalCheckpoints = new AtomicLong(0);
    private final AtomicLong successfulCheckpoints = new AtomicLong(0);
    private final AtomicLong failedCheckpoints = new AtomicLong(0);
    private final AtomicLong lastCheckpointDuration = new AtomicLong(0);
    private final AtomicLong totalCheckpointDuration = new AtomicLong(0);
    private volatile long lastCheckpointStartTime = 0;
    private volatile long lastCheckpointId = -1;

    /**
     * 通知Checkpoint开始
     * @param checkpointId Checkpoint ID
     */
    public void notifyCheckpointStart(long checkpointId) {
        lastCheckpointStartTime = System.currentTimeMillis();
        lastCheckpointId = checkpointId;
        totalCheckpoints.incrementAndGet();
        
        logger.info("Checkpoint {} started at {}", checkpointId, lastCheckpointStartTime);
    }

    /**
     * 通知Checkpoint成功完成
     * @param checkpointId Checkpoint ID
     */
    public void notifyCheckpointComplete(long checkpointId) {
        long endTime = System.currentTimeMillis();
        long duration = endTime - lastCheckpointStartTime;
        
        successfulCheckpoints.incrementAndGet();
        lastCheckpointDuration.set(duration);
        totalCheckpointDuration.addAndGet(duration);
        
        logger.info("Checkpoint {} completed successfully in {} ms", checkpointId, duration);
        
        // 记录成功率
        double successRate = getSuccessRate();
        logger.info("Checkpoint success rate: {:.2f}%", successRate);
    }

    /**
     * 通知Checkpoint失败
     * 需求 2.5: 记录错误日志并继续处理
     * 需求 4.6: 记录所有故障事件到日志系统
     * 
     * @param checkpointId Checkpoint ID
     * @param cause 失败原因
     */
    public void notifyCheckpointFailed(long checkpointId, Throwable cause) {
        long endTime = System.currentTimeMillis();
        long duration = endTime - lastCheckpointStartTime;
        
        failedCheckpoints.incrementAndGet();
        
        // 记录详细的错误日志
        logger.error("Checkpoint {} failed after {} ms. Reason: {}", 
            checkpointId, duration, cause != null ? cause.getMessage() : "Unknown");
        
        if (cause != null) {
            logger.error("Checkpoint failure stack trace:", cause);
        }
        
        // 记录失败率
        double failureRate = getFailureRate();
        logger.warn("Checkpoint failure rate: {:.2f}%", failureRate);
        
        // 如果失败率过高，记录警告
        if (failureRate > 10.0) {
            logger.warn("WARNING: Checkpoint failure rate ({:.2f}%) exceeds 10% threshold!", failureRate);
        }
        
        // 继续处理 - 不抛出异常，让Flink继续运行
        logger.info("Continuing data processing despite checkpoint failure");
    }

    /**
     * 通知Checkpoint被中止
     * @param checkpointId Checkpoint ID
     * @param reason 中止原因
     */
    public void notifyCheckpointAborted(long checkpointId, String reason) {
        long endTime = System.currentTimeMillis();
        long duration = endTime - lastCheckpointStartTime;
        
        failedCheckpoints.incrementAndGet();
        
        logger.warn("Checkpoint {} aborted after {} ms. Reason: {}", 
            checkpointId, duration, reason != null ? reason : "Unknown");
        
        // 记录失败率
        double failureRate = getFailureRate();
        logger.warn("Checkpoint failure rate: {:.2f}%", failureRate);
    }

    /**
     * 获取Checkpoint成功率
     * @return 成功率（百分比）
     */
    public double getSuccessRate() {
        long total = totalCheckpoints.get();
        if (total == 0) {
            return 0.0;
        }
        return (successfulCheckpoints.get() * 100.0) / total;
    }

    /**
     * 获取Checkpoint失败率
     * @return 失败率（百分比）
     */
    public double getFailureRate() {
        long total = totalCheckpoints.get();
        if (total == 0) {
            return 0.0;
        }
        return (failedCheckpoints.get() * 100.0) / total;
    }

    /**
     * 获取平均Checkpoint耗时
     * @return 平均耗时（毫秒）
     */
    public long getAverageCheckpointDuration() {
        long successful = successfulCheckpoints.get();
        if (successful == 0) {
            return 0;
        }
        return totalCheckpointDuration.get() / successful;
    }

    /**
     * 获取最后一次Checkpoint耗时
     * @return 耗时（毫秒）
     */
    public long getLastCheckpointDuration() {
        return lastCheckpointDuration.get();
    }

    /**
     * 获取总Checkpoint次数
     * @return 总次数
     */
    public long getTotalCheckpoints() {
        return totalCheckpoints.get();
    }

    /**
     * 获取成功的Checkpoint次数
     * @return 成功次数
     */
    public long getSuccessfulCheckpoints() {
        return successfulCheckpoints.get();
    }

    /**
     * 获取失败的Checkpoint次数
     * @return 失败次数
     */
    public long getFailedCheckpoints() {
        return failedCheckpoints.get();
    }

    /**
     * 获取最后一次Checkpoint ID
     * @return Checkpoint ID
     */
    public long getLastCheckpointId() {
        return lastCheckpointId;
    }

    /**
     * 获取Checkpoint指标摘要
     * @return 指标摘要字符串
     */
    public String getMetricsSummary() {
        return String.format(
            "Checkpoint Metrics - Total: %d, Successful: %d, Failed: %d, " +
            "Success Rate: %.2f%%, Average Duration: %d ms, Last Duration: %d ms",
            getTotalCheckpoints(),
            getSuccessfulCheckpoints(),
            getFailedCheckpoints(),
            getSuccessRate(),
            getAverageCheckpointDuration(),
            getLastCheckpointDuration()
        );
    }

    /**
     * 重置所有指标
     */
    public void resetMetrics() {
        totalCheckpoints.set(0);
        successfulCheckpoints.set(0);
        failedCheckpoints.set(0);
        lastCheckpointDuration.set(0);
        totalCheckpointDuration.set(0);
        lastCheckpointStartTime = 0;
        lastCheckpointId = -1;
        
        logger.info("Checkpoint metrics reset");
    }
}
