package com.realtime.pipeline.flink;

import com.realtime.pipeline.config.FlinkConfig;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FlinkEnvironmentConfigurator使用示例
 * 演示如何配置Flink执行环境
 */
public class FlinkEnvironmentConfiguratorExample {
    private static final Logger logger = LoggerFactory.getLogger(FlinkEnvironmentConfiguratorExample.class);

    public static void main(String[] args) throws Exception {
        // 1. 创建Flink配置
        FlinkConfig flinkConfig = FlinkConfig.builder()
            .parallelism(4)                          // 并行度
            .checkpointInterval(300000L)             // Checkpoint间隔: 5分钟
            .checkpointTimeout(600000L)              // Checkpoint超时: 10分钟
            .minPauseBetweenCheckpoints(60000L)      // 最小间隔: 1分钟
            .maxConcurrentCheckpoints(1)             // 最大并发Checkpoint数
            .retainedCheckpoints(3)                  // 保留3个Checkpoint
            .stateBackendType("hashmap")             // 状态后端类型
            .checkpointDir("/tmp/flink-checkpoints") // Checkpoint目录
            .tolerableCheckpointFailures(3)          // 容忍失败次数
            .restartStrategy("fixed-delay")          // 重启策略
            .restartAttempts(3)                      // 重启尝试次数
            .restartDelay(10000L)                    // 重启延迟: 10秒
            .build();

        // 2. 创建Flink执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 3. 使用FlinkEnvironmentConfigurator配置环境
        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(flinkConfig);
        configurator.configure(env);

        logger.info("Flink environment configured successfully");
        logger.info("Parallelism: {}", env.getParallelism());
        logger.info("Checkpoint enabled: {}", env.getCheckpointConfig().isCheckpointingEnabled());

        // 4. 创建数据流并执行作业
        // 示例: 创建一个简单的数据流
        DataStream<String> dataStream = env.fromElements("Hello", "World", "Flink");
        
        dataStream.print();

        // 5. 执行作业
        env.execute("Flink Environment Configurator Example");
    }

    /**
     * 使用RocksDB状态后端的示例
     */
    public static void exampleWithRocksDB() throws Exception {
        FlinkConfig flinkConfig = FlinkConfig.builder()
            .parallelism(8)
            .checkpointInterval(300000L)
            .checkpointTimeout(600000L)
            .minPauseBetweenCheckpoints(60000L)
            .maxConcurrentCheckpoints(1)
            .retainedCheckpoints(3)
            .stateBackendType("rocksdb")             // 使用RocksDB（适合大状态）
            .checkpointDir("/tmp/flink-checkpoints")
            .tolerableCheckpointFailures(3)
            .restartStrategy("failure-rate")         // 使用失败率重启策略
            .restartAttempts(3)
            .restartDelay(10000L)
            .build();

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(flinkConfig);
        configurator.configure(env);

        // ... 创建数据流和执行作业
    }

    /**
     * 使用HDFS作为Checkpoint存储的示例
     */
    public static void exampleWithHDFS() throws Exception {
        FlinkConfig flinkConfig = FlinkConfig.builder()
            .parallelism(4)
            .checkpointInterval(300000L)
            .checkpointTimeout(600000L)
            .minPauseBetweenCheckpoints(60000L)
            .maxConcurrentCheckpoints(1)
            .retainedCheckpoints(3)
            .stateBackendType("hashmap")
            .checkpointDir("hdfs://namenode:9000/flink/checkpoints")  // 使用HDFS
            .tolerableCheckpointFailures(3)
            .restartStrategy("fixed-delay")
            .restartAttempts(3)
            .restartDelay(10000L)
            .build();

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(flinkConfig);
        configurator.configure(env);

        // ... 创建数据流和执行作业
    }

    /**
     * 最小配置示例
     */
    public static void exampleMinimalConfig() throws Exception {
        FlinkConfig flinkConfig = FlinkConfig.builder()
            .parallelism(1)
            .checkpointInterval(60000L)              // 1分钟
            .checkpointTimeout(120000L)              // 2分钟
            .minPauseBetweenCheckpoints(10000L)
            .maxConcurrentCheckpoints(1)
            .retainedCheckpoints(1)
            .stateBackendType("hashmap")
            .checkpointDir("/tmp/checkpoints")
            .tolerableCheckpointFailures(0)
            .restartStrategy("none")                 // 不重启
            .restartAttempts(0)
            .restartDelay(0L)
            .build();

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(flinkConfig);
        configurator.configure(env);

        // ... 创建数据流和执行作业
    }
}
