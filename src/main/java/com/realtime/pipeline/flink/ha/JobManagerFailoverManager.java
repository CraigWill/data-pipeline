package com.realtime.pipeline.flink.ha;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * JobManager故障切换管理器
 * 负责监控JobManager状态并处理故障切换
 * 
 * 验证需求: 5.3
 */
public class JobManagerFailoverManager {
    private static final Logger logger = LoggerFactory.getLogger(JobManagerFailoverManager.class);

    private final long failoverTimeoutMs;
    private final AtomicBoolean isPrimaryActive;
    private final AtomicReference<String> currentLeader;
    private final AtomicLong lastFailoverTime;
    private final AtomicLong failoverCount;

    /**
     * 构造函数
     * @param failoverTimeoutMs 故障切换超时时间（毫秒）
     */
    public JobManagerFailoverManager(long failoverTimeoutMs) {
        if (failoverTimeoutMs <= 0) {
            throw new IllegalArgumentException("Failover timeout must be positive");
        }
        this.failoverTimeoutMs = failoverTimeoutMs;
        this.isPrimaryActive = new AtomicBoolean(true);
        this.currentLeader = new AtomicReference<>("primary");
        this.lastFailoverTime = new AtomicLong(0);
        this.failoverCount = new AtomicLong(0);
    }

    /**
     * 记录JobManager故障
     * 
     * 需求 5.3: WHEN 主JobManager失败 THEN THE System SHALL 在30秒内切换到备用JobManager
     * 
     * @param failedJobManager 失败的JobManager标识
     * @return 故障切换是否成功
     */
    public boolean recordFailure(String failedJobManager) {
        long failureTime = System.currentTimeMillis();
        logger.warn("JobManager failure detected: {}", failedJobManager);

        // 记录故障时间
        lastFailoverTime.set(failureTime);
        failoverCount.incrementAndGet();

        // 如果是主JobManager失败，切换到备用
        if ("primary".equals(failedJobManager) && isPrimaryActive.get()) {
            return performFailover(failureTime);
        }

        return false;
    }

    /**
     * 执行故障切换
     * 
     * @param failureTime 故障发生时间
     * @return 故障切换是否成功
     */
    private boolean performFailover(long failureTime) {
        logger.info("Initiating failover from primary to standby JobManager");

        try {
            // 标记主JobManager为非活动状态
            isPrimaryActive.set(false);

            // 切换到备用JobManager
            String previousLeader = currentLeader.getAndSet("standby");
            logger.info("Leader changed from {} to standby", previousLeader);

            // 计算故障切换时间
            long failoverDuration = System.currentTimeMillis() - failureTime;
            logger.info("Failover completed in {} ms", failoverDuration);

            // 验证故障切换是否在超时时间内完成
            if (failoverDuration <= failoverTimeoutMs) {
                logger.info("Failover successful within timeout ({} ms <= {} ms)", 
                    failoverDuration, failoverTimeoutMs);
                return true;
            } else {
                logger.warn("Failover exceeded timeout ({} ms > {} ms)", 
                    failoverDuration, failoverTimeoutMs);
                return false;
            }

        } catch (Exception e) {
            logger.error("Failover failed with exception", e);
            return false;
        }
    }

    /**
     * 记录JobManager恢复
     * 
     * @param recoveredJobManager 恢复的JobManager标识
     */
    public void recordRecovery(String recoveredJobManager) {
        logger.info("JobManager recovered: {}", recoveredJobManager);

        if ("primary".equals(recoveredJobManager)) {
            // 主JobManager恢复，但不自动切换回去（避免频繁切换）
            logger.info("Primary JobManager recovered but keeping standby as leader");
        }
    }

    /**
     * 获取当前领导者
     * @return 当前领导者标识
     */
    public String getCurrentLeader() {
        return currentLeader.get();
    }

    /**
     * 检查主JobManager是否活动
     * @return true如果主JobManager活动
     */
    public boolean isPrimaryActive() {
        return isPrimaryActive.get();
    }

    /**
     * 获取最后一次故障切换时间
     * @return 最后一次故障切换时间（毫秒时间戳）
     */
    public long getLastFailoverTime() {
        return lastFailoverTime.get();
    }

    /**
     * 获取故障切换次数
     * @return 故障切换次数
     */
    public long getFailoverCount() {
        return failoverCount.get();
    }

    /**
     * 获取故障切换超时时间
     * @return 故障切换超时时间（毫秒）
     */
    public long getFailoverTimeout() {
        return failoverTimeoutMs;
    }

    /**
     * 重置故障切换管理器状态
     */
    public void reset() {
        isPrimaryActive.set(true);
        currentLeader.set("primary");
        lastFailoverTime.set(0);
        failoverCount.set(0);
        logger.info("Failover manager reset to initial state");
    }

    /**
     * 获取故障切换统计信息
     * @return 故障切换统计信息
     */
    public FailoverStats getStats() {
        return new FailoverStats(
            currentLeader.get(),
            isPrimaryActive.get(),
            failoverCount.get(),
            lastFailoverTime.get(),
            failoverTimeoutMs
        );
    }

    /**
     * 故障切换统计信息
     */
    public static class FailoverStats {
        private final String currentLeader;
        private final boolean primaryActive;
        private final long failoverCount;
        private final long lastFailoverTime;
        private final long failoverTimeout;

        public FailoverStats(String currentLeader, boolean primaryActive, 
                           long failoverCount, long lastFailoverTime, long failoverTimeout) {
            this.currentLeader = currentLeader;
            this.primaryActive = primaryActive;
            this.failoverCount = failoverCount;
            this.lastFailoverTime = lastFailoverTime;
            this.failoverTimeout = failoverTimeout;
        }

        public String getCurrentLeader() {
            return currentLeader;
        }

        public boolean isPrimaryActive() {
            return primaryActive;
        }

        public long getFailoverCount() {
            return failoverCount;
        }

        public long getLastFailoverTime() {
            return lastFailoverTime;
        }

        public long getFailoverTimeout() {
            return failoverTimeout;
        }

        @Override
        public String toString() {
            return String.format(
                "FailoverStats{leader=%s, primaryActive=%s, failoverCount=%d, lastFailover=%s, timeout=%dms}",
                currentLeader, primaryActive, failoverCount,
                lastFailoverTime > 0 ? Instant.ofEpochMilli(lastFailoverTime) : "never",
                failoverTimeout
            );
        }
    }
}
