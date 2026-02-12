package com.realtime.pipeline.flink.scaling;

import com.realtime.pipeline.config.ScalingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 动态扩缩容示例
 * 演示如何使用DynamicScalingManager进行扩缩容操作
 */
public class DynamicScalingExample {
    private static final Logger logger = LoggerFactory.getLogger(DynamicScalingExample.class);

    public static void main(String[] args) {
        // 创建扩缩容配置
        ScalingConfig config = ScalingConfig.builder()
            .autoScalingEnabled(true)
            .minTaskManagers(2)
            .maxTaskManagers(10)
            .drainTimeout(300000L) // 5分钟
            .build();

        // 验证配置
        config.validate();

        // 创建动态扩缩容管理器
        String jobManagerHost = "localhost";
        int jobManagerPort = 8081;
        DynamicScalingManager scalingManager = new DynamicScalingManager(
            jobManagerHost,
            jobManagerPort
        );

        // 示例1: 扩容 - 增加TaskManager
        logger.info("=== Example 1: Scale Out TaskManagers ===");
        boolean scaleOutSuccess = scalingManager.scaleOutTaskManagers(2);
        if (scaleOutSuccess) {
            logger.info("Successfully initiated scale-out operation");
        } else {
            logger.error("Failed to scale out TaskManagers");
        }

        // 示例2: 调整并行度
        logger.info("\n=== Example 2: Adjust Parallelism ===");
        String jobId = "a1b2c3d4e5f6g7h8i9j0"; // 示例作业ID
        int newParallelism = 4;
        String savepointPath = "/tmp/savepoints";
        boolean adjustSuccess = scalingManager.adjustParallelism(
            jobId,
            newParallelism,
            savepointPath
        );
        if (adjustSuccess) {
            logger.info("Successfully adjusted parallelism to {}", newParallelism);
        } else {
            logger.error("Failed to adjust parallelism");
        }

        // 示例3: 优雅关闭TaskManager
        logger.info("\n=== Example 3: Graceful Shutdown TaskManager ===");
        String taskManagerId = "taskmanager-1";
        boolean shutdownSuccess = scalingManager.gracefulShutdownTaskManager(
            taskManagerId,
            config.getDrainTimeout()
        );
        if (shutdownSuccess) {
            logger.info("Successfully shut down TaskManager {}", taskManagerId);
        } else {
            logger.error("Failed to shut down TaskManager {}", taskManagerId);
        }

        // 示例4: 缩容 - 移除TaskManager
        logger.info("\n=== Example 4: Scale In TaskManagers ===");
        boolean scaleInSuccess = scalingManager.scaleInTaskManagers(
            1,
            config.getDrainTimeout()
        );
        if (scaleInSuccess) {
            logger.info("Successfully initiated scale-in operation");
        } else {
            logger.error("Failed to scale in TaskManagers");
        }

        // 示例5: 查询TaskManager数量
        logger.info("\n=== Example 5: Get TaskManager Count ===");
        int taskManagerCount = scalingManager.getTaskManagerCount();
        if (taskManagerCount >= 0) {
            logger.info("Current TaskManager count: {}", taskManagerCount);
        } else {
            logger.error("Failed to get TaskManager count");
        }

        logger.info("\n=== Dynamic Scaling Examples Completed ===");
    }
}
