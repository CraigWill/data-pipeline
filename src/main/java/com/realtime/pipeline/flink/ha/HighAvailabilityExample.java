package com.realtime.pipeline.flink.ha;

import com.realtime.pipeline.config.FlinkConfig;
import com.realtime.pipeline.config.HighAvailabilityConfig;
import com.realtime.pipeline.flink.FlinkEnvironmentConfigurator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 高可用性配置示例
 * 演示如何配置Flink JobManager的高可用性
 * 
 * 验证需求: 5.1, 5.2, 5.3
 */
public class HighAvailabilityExample {
    private static final Logger logger = LoggerFactory.getLogger(HighAvailabilityExample.class);

    /**
     * 示例1: 使用ZooKeeper配置高可用性
     */
    public static void exampleZooKeeperHA() {
        logger.info("=== ZooKeeper HA Configuration Example ===");

        // 创建HA配置
        HighAvailabilityConfig haConfig = HighAvailabilityConfig.builder()
            .enabled(true)
            .mode("zookeeper")
            .zookeeperQuorum("localhost:2181,localhost:2182,localhost:2183")
            .zookeeperPath("/flink")
            .storageDir("/tmp/flink-ha")
            .jobManagerCount(2)  // 需求 5.2: 至少2个JobManager实例
            .failoverTimeout(30000L)  // 需求 5.3: 30秒内完成故障切换
            .zookeeperSessionTimeout(60000L)
            .zookeeperConnectionTimeout(15000L)
            .build();

        // 创建Flink配置
        FlinkConfig flinkConfig = FlinkConfig.builder()
            .parallelism(4)
            .checkpointInterval(300000L)
            .checkpointTimeout(600000L)
            .checkpointDir("/tmp/flink-checkpoints")
            .build();

        // 创建环境配置器
        FlinkEnvironmentConfigurator configurator = 
            new FlinkEnvironmentConfigurator(flinkConfig, haConfig);

        // 创建Flink执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 应用配置
        configurator.configure(env);

        logger.info("ZooKeeper HA configured successfully");
        logger.info("JobManager count: {}", haConfig.getJobManagerCount());
        logger.info("Failover timeout: {} ms", haConfig.getFailoverTimeout());
    }

    /**
     * 示例2: 使用Kubernetes配置高可用性
     */
    public static void exampleKubernetesHA() {
        logger.info("=== Kubernetes HA Configuration Example ===");

        // 创建HA配置
        HighAvailabilityConfig haConfig = HighAvailabilityConfig.builder()
            .enabled(true)
            .mode("kubernetes")
            .kubernetesNamespace("flink")
            .kubernetesClusterId("flink-cluster-1")
            .storageDir("/opt/flink/ha")
            .jobManagerCount(2)
            .failoverTimeout(30000L)
            .build();

        // 创建Flink配置
        FlinkConfig flinkConfig = FlinkConfig.builder()
            .parallelism(4)
            .checkpointInterval(300000L)
            .checkpointTimeout(600000L)
            .checkpointDir("/opt/flink/checkpoints")
            .build();

        // 创建环境配置器
        FlinkEnvironmentConfigurator configurator = 
            new FlinkEnvironmentConfigurator(flinkConfig, haConfig);

        // 创建Flink执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 应用配置
        configurator.configure(env);

        logger.info("Kubernetes HA configured successfully");
        logger.info("Namespace: {}", haConfig.getKubernetesNamespace());
        logger.info("Cluster ID: {}", haConfig.getKubernetesClusterId());
    }

    /**
     * 示例3: 不启用高可用性
     */
    public static void exampleNoHA() {
        logger.info("=== No HA Configuration Example ===");

        // 创建HA配置（禁用）
        HighAvailabilityConfig haConfig = HighAvailabilityConfig.builder()
            .enabled(false)
            .mode("none")
            .build();

        // 创建Flink配置
        FlinkConfig flinkConfig = FlinkConfig.builder()
            .parallelism(4)
            .checkpointInterval(300000L)
            .checkpointTimeout(600000L)
            .checkpointDir("/tmp/flink-checkpoints")
            .build();

        // 创建环境配置器（不传入HA配置）
        FlinkEnvironmentConfigurator configurator = 
            new FlinkEnvironmentConfigurator(flinkConfig);

        // 创建Flink执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 应用配置
        configurator.configure(env);

        logger.info("HA is disabled");
    }

    /**
     * 示例4: 故障切换管理器使用
     */
    public static void exampleFailoverManager() {
        logger.info("=== Failover Manager Example ===");

        // 创建故障切换管理器
        JobManagerFailoverManager failoverManager = 
            new JobManagerFailoverManager(30000L);  // 30秒超时

        // 模拟主JobManager故障
        logger.info("Simulating primary JobManager failure...");
        boolean failoverSuccess = failoverManager.recordFailure("primary");
        
        if (failoverSuccess) {
            logger.info("Failover successful!");
        } else {
            logger.warn("Failover failed or exceeded timeout");
        }

        // 获取当前领导者
        String currentLeader = failoverManager.getCurrentLeader();
        logger.info("Current leader: {}", currentLeader);

        // 获取统计信息
        JobManagerFailoverManager.FailoverStats stats = failoverManager.getStats();
        logger.info("Failover stats: {}", stats);

        // 模拟主JobManager恢复
        logger.info("Simulating primary JobManager recovery...");
        failoverManager.recordRecovery("primary");
    }

    public static void main(String[] args) {
        try {
            // 运行示例
            exampleZooKeeperHA();
            System.out.println();
            
            exampleKubernetesHA();
            System.out.println();
            
            exampleNoHA();
            System.out.println();
            
            exampleFailoverManager();
            
        } catch (Exception e) {
            logger.error("Example execution failed", e);
        }
    }
}
