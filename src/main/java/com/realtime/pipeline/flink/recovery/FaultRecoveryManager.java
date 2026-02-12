package com.realtime.pipeline.flink.recovery;

import org.apache.flink.runtime.checkpoint.CompletedCheckpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 故障恢复管理器
 * 负责管理Checkpoint恢复、恢复时间监控和Checkpoint清理策略
 * 
 * 验证需求:
 * - 需求 4.1: WHEN Flink任务失败 THEN THE System SHALL 从最近的Checkpoint恢复
 * - 需求 4.3: WHEN 恢复操作 THEN THE System SHALL 在10分钟内完成恢复
 * - 需求 4.4: THE System SHALL 保留最近3个Checkpoint
 */
public class FaultRecoveryManager implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(FaultRecoveryManager.class);

    // 恢复时间阈值：10分钟（毫秒）
    private static final long RECOVERY_TIME_THRESHOLD_MS = 10 * 60 * 1000;

    // 保留的Checkpoint数量
    private final int retainedCheckpoints;

    // Checkpoint存储目录
    private final String checkpointDir;

    // 恢复指标
    private final AtomicLong totalRecoveries = new AtomicLong(0);
    private final AtomicLong successfulRecoveries = new AtomicLong(0);
    private final AtomicLong failedRecoveries = new AtomicLong(0);
    private final AtomicLong lastRecoveryDuration = new AtomicLong(0);
    private final AtomicLong totalRecoveryDuration = new AtomicLong(0);
    private volatile long lastRecoveryStartTime = 0;
    private volatile String lastRecoveredCheckpointPath = null;

    /**
     * 构造函数
     * @param checkpointDir Checkpoint存储目录
     * @param retainedCheckpoints 保留的Checkpoint数量
     */
    public FaultRecoveryManager(String checkpointDir, int retainedCheckpoints) {
        if (checkpointDir == null || checkpointDir.trim().isEmpty()) {
            throw new IllegalArgumentException("Checkpoint directory cannot be null or empty");
        }
        if (retainedCheckpoints <= 0) {
            throw new IllegalArgumentException("Retained checkpoints must be positive");
        }

        this.checkpointDir = normalizeCheckpointDir(checkpointDir);
        this.retainedCheckpoints = retainedCheckpoints;

        logger.info("FaultRecoveryManager initialized with checkpointDir={}, retainedCheckpoints={}",
            this.checkpointDir, retainedCheckpoints);
    }

    /**
     * 标准化Checkpoint目录路径
     * @param dir 原始目录路径
     * @return 标准化后的路径
     */
    private String normalizeCheckpointDir(String dir) {
        // 移除file://前缀（如果存在）
        String normalized = dir.replaceFirst("^file://", "");
        // 确保路径以/结尾
        if (!normalized.endsWith("/")) {
            normalized += "/";
        }
        return normalized;
    }

    /**
     * 开始恢复操作
     * 需求 4.1: 从最近的Checkpoint恢复
     * 需求 4.3: 在10分钟内完成恢复
     */
    public void startRecovery() {
        lastRecoveryStartTime = System.currentTimeMillis();
        totalRecoveries.incrementAndGet();

        logger.info("Starting recovery from checkpoint at {}", lastRecoveryStartTime);
        logger.info("Recovery time threshold: {} ms ({} minutes)",
            RECOVERY_TIME_THRESHOLD_MS, RECOVERY_TIME_THRESHOLD_MS / 60000);
    }

    /**
     * 完成恢复操作
     * @param checkpointPath 恢复的Checkpoint路径
     * @param success 是否成功
     */
    public void completeRecovery(String checkpointPath, boolean success) {
        long endTime = System.currentTimeMillis();
        long duration = endTime - lastRecoveryStartTime;

        lastRecoveryDuration.set(duration);
        totalRecoveryDuration.addAndGet(duration);
        lastRecoveredCheckpointPath = checkpointPath;

        if (success) {
            successfulRecoveries.incrementAndGet();
            logger.info("Recovery completed successfully in {} ms from checkpoint: {}",
                duration, checkpointPath);

            // 检查是否超过恢复时间阈值
            if (duration > RECOVERY_TIME_THRESHOLD_MS) {
                logger.warn("WARNING: Recovery time ({} ms) exceeds threshold ({} ms)",
                    duration, RECOVERY_TIME_THRESHOLD_MS);
            } else {
                logger.info("Recovery time within threshold: {} ms < {} ms",
                    duration, RECOVERY_TIME_THRESHOLD_MS);
            }
        } else {
            failedRecoveries.incrementAndGet();
            logger.error("Recovery failed after {} ms from checkpoint: {}",
                duration, checkpointPath);
        }

        // 记录恢复成功率
        double successRate = getRecoverySuccessRate();
        logger.info("Recovery success rate: {:.2f}%", successRate);
    }

    /**
     * 获取最近的Checkpoint路径
     * 需求 4.1: 从最近的Checkpoint恢复
     * 
     * @return 最近的Checkpoint路径，如果不存在则返回null
     */
    public String getLatestCheckpointPath() {
        try {
            List<CheckpointInfo> checkpoints = listCheckpoints();
            if (checkpoints.isEmpty()) {
                logger.warn("No checkpoints found in directory: {}", checkpointDir);
                return null;
            }

            // 按时间戳降序排序，获取最新的
            CheckpointInfo latest = checkpoints.stream()
                .max(Comparator.comparingLong(CheckpointInfo::getTimestamp))
                .orElse(null);

            if (latest != null) {
                logger.info("Latest checkpoint found: {} (timestamp: {})",
                    latest.getPath(), latest.getTimestamp());
                return latest.getPath();
            }

            return null;
        } catch (Exception e) {
            logger.error("Failed to get latest checkpoint path", e);
            return null;
        }
    }

    /**
     * 清理旧的Checkpoint
     * 需求 4.4: 保留最近3个Checkpoint
     * 
     * @return 清理的Checkpoint数量
     */
    public int cleanupOldCheckpoints() {
        try {
            List<CheckpointInfo> checkpoints = listCheckpoints();
            if (checkpoints.isEmpty()) {
                logger.info("No checkpoints to clean up");
                return 0;
            }

            logger.info("Found {} checkpoints, retaining {}", checkpoints.size(), retainedCheckpoints);

            // 如果Checkpoint数量不超过保留数量，不需要清理
            if (checkpoints.size() <= retainedCheckpoints) {
                logger.info("Checkpoint count ({}) within retention limit ({}), no cleanup needed",
                    checkpoints.size(), retainedCheckpoints);
                return 0;
            }

            // 按时间戳降序排序（最新的在前面）
            checkpoints.sort(Comparator.comparingLong(CheckpointInfo::getTimestamp).reversed());

            // 保留最近的N个，删除其余的
            List<CheckpointInfo> toDelete = checkpoints.subList(retainedCheckpoints, checkpoints.size());
            int deletedCount = 0;

            for (CheckpointInfo checkpoint : toDelete) {
                try {
                    if (deleteCheckpoint(checkpoint.getPath())) {
                        deletedCount++;
                        logger.info("Deleted old checkpoint: {} (timestamp: {})",
                            checkpoint.getPath(), checkpoint.getTimestamp());
                    }
                } catch (Exception e) {
                    logger.error("Failed to delete checkpoint: {}", checkpoint.getPath(), e);
                }
            }

            logger.info("Cleanup completed: deleted {} old checkpoints, retained {}",
                deletedCount, retainedCheckpoints);

            return deletedCount;
        } catch (Exception e) {
            logger.error("Failed to cleanup old checkpoints", e);
            return 0;
        }
    }

    /**
     * 列出所有Checkpoint
     * @return Checkpoint信息列表
     */
    private List<CheckpointInfo> listCheckpoints() throws IOException {
        File dir = new File(checkpointDir);
        if (!dir.exists() || !dir.isDirectory()) {
            logger.warn("Checkpoint directory does not exist or is not a directory: {}", checkpointDir);
            return Collections.emptyList();
        }

        List<CheckpointInfo> checkpoints = new ArrayList<>();
        Path checkpointPath = Paths.get(checkpointDir).toAbsolutePath().normalize();

        try (Stream<Path> paths = Files.list(checkpointPath)) {
            paths.filter(Files::isDirectory)
                .forEach(path -> {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                        // 使用最后修改时间作为时间戳，因为创建时间在某些文件系统上可能不可靠
                        long timestamp = attrs.lastModifiedTime().toMillis();
                        checkpoints.add(new CheckpointInfo(path.toString(), timestamp));
                    } catch (IOException e) {
                        logger.warn("Failed to read attributes for path: {}", path, e);
                    }
                });
        }

        return checkpoints;
    }

    /**
     * 删除Checkpoint
     * @param checkpointPath Checkpoint路径
     * @return true如果删除成功
     */
    private boolean deleteCheckpoint(String checkpointPath) throws IOException {
        Path path = Paths.get(checkpointPath);
        if (!Files.exists(path)) {
            logger.warn("Checkpoint path does not exist: {}", checkpointPath);
            return false;
        }

        // 递归删除目录及其内容
        try (Stream<Path> paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(file -> {
                    if (!file.delete()) {
                        logger.warn("Failed to delete file: {}", file.getAbsolutePath());
                    }
                });
        }
        
        // 验证删除是否成功
        return !Files.exists(path);
    }

    /**
     * 获取恢复成功率
     * @return 成功率（百分比）
     */
    public double getRecoverySuccessRate() {
        long total = totalRecoveries.get();
        if (total == 0) {
            return 0.0;
        }
        return (successfulRecoveries.get() * 100.0) / total;
    }

    /**
     * 获取恢复失败率
     * @return 失败率（百分比）
     */
    public double getRecoveryFailureRate() {
        long total = totalRecoveries.get();
        if (total == 0) {
            return 0.0;
        }
        return (failedRecoveries.get() * 100.0) / total;
    }

    /**
     * 获取平均恢复时间
     * @return 平均恢复时间（毫秒）
     */
    public long getAverageRecoveryDuration() {
        long successful = successfulRecoveries.get();
        if (successful == 0) {
            return 0;
        }
        return totalRecoveryDuration.get() / successful;
    }

    /**
     * 获取最后一次恢复时间
     * @return 恢复时间（毫秒）
     */
    public long getLastRecoveryDuration() {
        return lastRecoveryDuration.get();
    }

    /**
     * 获取总恢复次数
     * @return 总次数
     */
    public long getTotalRecoveries() {
        return totalRecoveries.get();
    }

    /**
     * 获取成功的恢复次数
     * @return 成功次数
     */
    public long getSuccessfulRecoveries() {
        return successfulRecoveries.get();
    }

    /**
     * 获取失败的恢复次数
     * @return 失败次数
     */
    public long getFailedRecoveries() {
        return failedRecoveries.get();
    }

    /**
     * 获取最后恢复的Checkpoint路径
     * @return Checkpoint路径
     */
    public String getLastRecoveredCheckpointPath() {
        return lastRecoveredCheckpointPath;
    }

    /**
     * 获取恢复时间阈值
     * @return 阈值（毫秒）
     */
    public long getRecoveryTimeThreshold() {
        return RECOVERY_TIME_THRESHOLD_MS;
    }

    /**
     * 获取保留的Checkpoint数量
     * @return 保留数量
     */
    public int getRetainedCheckpoints() {
        return retainedCheckpoints;
    }

    /**
     * 获取Checkpoint目录
     * @return 目录路径
     */
    public String getCheckpointDir() {
        return checkpointDir;
    }

    /**
     * 获取恢复指标摘要
     * @return 指标摘要字符串
     */
    public String getRecoveryMetricsSummary() {
        return String.format(
            "Recovery Metrics - Total: %d, Successful: %d, Failed: %d, " +
            "Success Rate: %.2f%%, Average Duration: %d ms, Last Duration: %d ms, " +
            "Last Checkpoint: %s",
            getTotalRecoveries(),
            getSuccessfulRecoveries(),
            getFailedRecoveries(),
            getRecoverySuccessRate(),
            getAverageRecoveryDuration(),
            getLastRecoveryDuration(),
            getLastRecoveredCheckpointPath() != null ? getLastRecoveredCheckpointPath() : "N/A"
        );
    }

    /**
     * 重置所有指标
     */
    public void resetMetrics() {
        totalRecoveries.set(0);
        successfulRecoveries.set(0);
        failedRecoveries.set(0);
        lastRecoveryDuration.set(0);
        totalRecoveryDuration.set(0);
        lastRecoveryStartTime = 0;
        lastRecoveredCheckpointPath = null;

        logger.info("Recovery metrics reset");
    }

    /**
     * Checkpoint信息内部类
     */
    private static class CheckpointInfo {
        private final String path;
        private final long timestamp;

        public CheckpointInfo(String path, long timestamp) {
            this.path = path;
            this.timestamp = timestamp;
        }

        public String getPath() {
            return path;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
