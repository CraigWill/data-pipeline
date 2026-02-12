package com.realtime.pipeline.flink.recovery;

import com.realtime.pipeline.config.FlinkConfig;
import com.realtime.pipeline.flink.FlinkEnvironmentConfigurator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 故障恢复使用示例
 * 演示如何使用FaultRecoveryManager和RecoveryMonitor
 */
public class FaultRecoveryExample {
    private static final Logger logger = LoggerFactory.getLogger(FaultRecoveryExample.class);

    public static void main(String[] args) {
        // 示例1: 基本使用
        basicUsageExample();

        // 示例2: 使用恢复监控器
        recoveryMonitorExample();

        // 示例3: 手动恢复操作
        manualRecoveryExample();
    }

    /**
     * 示例1: 基本使用
     * 通过FlinkEnvironmentConfigurator自动配置故障恢复
     */
    public static void basicUsageExample() {
        logger.info("=== Basic Usage Example ===");

        // 创建Flink配置
        FlinkConfig config = FlinkConfig.builder()
            .parallelism(2)
            .checkpointInterval(300000L) // 5分钟
            .checkpointTimeout(600000L)  // 10分钟
            .retainedCheckpoints(3)      // 保留3个Checkpoint
            .checkpointDir("/tmp/flink-checkpoints")
            .stateBackendType("hashmap")
            .restartStrategy("fixed-delay")
            .restartAttempts(3)
            .restartDelay(10000L)
            .build();

        // 创建环境配置器（自动初始化FaultRecoveryManager）
        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(config);

        // 创建Flink执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 配置环境（包括故障恢复）
        configurator.configure(env);

        // 获取恢复管理器
        FaultRecoveryManager recoveryManager = configurator.getRecoveryManager();

        // 查看恢复指标
        logger.info("Recovery metrics: {}", recoveryManager.getRecoveryMetricsSummary());

        // 获取最近的Checkpoint路径
        String latestCheckpoint = recoveryManager.getLatestCheckpointPath();
        if (latestCheckpoint != null) {
            logger.info("Latest checkpoint: {}", latestCheckpoint);
        } else {
            logger.info("No checkpoints found");
        }

        // 清理旧的Checkpoint
        int cleanedCount = recoveryManager.cleanupOldCheckpoints();
        logger.info("Cleaned up {} old checkpoints", cleanedCount);
    }

    /**
     * 示例2: 使用恢复监控器
     * 自动监控恢复操作和清理Checkpoint
     */
    public static void recoveryMonitorExample() {
        logger.info("=== Recovery Monitor Example ===");

        // 创建故障恢复管理器
        FaultRecoveryManager recoveryManager = new FaultRecoveryManager(
            "/tmp/flink-checkpoints",
            3  // 保留3个Checkpoint
        );

        // 创建恢复监控器（每分钟报告一次）
        RecoveryMonitor monitor = new RecoveryMonitor(recoveryManager, 60000L);

        // 启动监控器
        monitor.start();

        logger.info("Recovery monitor started, will report metrics every minute");

        // 模拟运行一段时间
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 停止监控器
        monitor.stop();

        logger.info("Recovery monitor stopped");
    }

    /**
     * 示例3: 手动恢复操作
     * 手动控制恢复过程和监控恢复时间
     */
    public static void manualRecoveryExample() {
        logger.info("=== Manual Recovery Example ===");

        // 创建故障恢复管理器
        FaultRecoveryManager recoveryManager = new FaultRecoveryManager(
            "/tmp/flink-checkpoints",
            3
        );

        // 模拟恢复操作
        logger.info("Starting recovery operation...");

        // 开始恢复
        recoveryManager.startRecovery();

        // 获取最近的Checkpoint
        String checkpointPath = recoveryManager.getLatestCheckpointPath();
        if (checkpointPath != null) {
            logger.info("Recovering from checkpoint: {}", checkpointPath);

            // 模拟恢复过程
            try {
                Thread.sleep(2000); // 模拟恢复耗时
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 完成恢复
            recoveryManager.completeRecovery(checkpointPath, true);

            // 查看恢复指标
            logger.info("Recovery completed");
            logger.info("Recovery duration: {} ms", recoveryManager.getLastRecoveryDuration());
            logger.info("Recovery success rate: {:.2f}%", recoveryManager.getRecoverySuccessRate());
        } else {
            logger.info("No checkpoint available for recovery");
            recoveryManager.completeRecovery(null, false);
        }

        // 查看完整的恢复指标
        logger.info("Recovery metrics: {}", recoveryManager.getRecoveryMetricsSummary());
    }

    /**
     * 示例4: 集成到Flink作业
     * 在实际的Flink作业中使用故障恢复
     */
    public static void flinkJobIntegrationExample() {
        logger.info("=== Flink Job Integration Example ===");

        try {
            // 创建Flink配置
            FlinkConfig config = FlinkConfig.builder()
                .parallelism(2)
                .checkpointInterval(300000L)
                .retainedCheckpoints(3)
                .checkpointDir("/tmp/flink-checkpoints")
                .stateBackendType("hashmap")
                .build();

            // 创建环境配置器
            FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(config);

            // 创建Flink执行环境
            StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

            // 配置环境
            configurator.configure(env);

            // 获取恢复管理器
            FaultRecoveryManager recoveryManager = configurator.getRecoveryManager();

            // 创建并启动恢复监控器
            RecoveryMonitor monitor = new RecoveryMonitor(recoveryManager, 60000L);
            monitor.start();

            // 构建数据流
            // env.fromSource(...).map(...).addSink(...);

            // 执行作业
            // env.execute("Flink Job with Fault Recovery");

            // 停止监控器
            monitor.stop();

            logger.info("Flink job completed");
        } catch (Exception e) {
            logger.error("Error in Flink job", e);
        }
    }
}
