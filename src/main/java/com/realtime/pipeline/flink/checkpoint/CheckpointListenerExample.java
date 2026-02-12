package com.realtime.pipeline.flink.checkpoint;

import com.realtime.pipeline.config.FlinkConfig;
import com.realtime.pipeline.flink.FlinkEnvironmentConfigurator;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CheckpointListener使用示例
 * 演示如何在Flink作业中集成CheckpointListener来监控Checkpoint事件
 * 
 * 验证需求:
 * - 需求 2.5: WHEN Checkpoint失败 THEN THE Flink SHALL 记录错误日志并继续处理
 * - 需求 4.6: THE System SHALL 记录所有故障事件到日志系统
 */
public class CheckpointListenerExample {
    private static final Logger logger = LoggerFactory.getLogger(CheckpointListenerExample.class);

    public static void main(String[] args) throws Exception {
        // 创建Flink执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 配置Flink环境（包括Checkpoint）
        FlinkConfig flinkConfig = createFlinkConfig();
        FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(flinkConfig);
        configurator.configure(env);

        // 创建CheckpointListener
        CheckpointListener checkpointListener = new CheckpointListener();

        // 创建数据流（示例：生成序列数据）
        DataStream<String> dataStream = env
            .fromSequence(1, 1000)
            .map(i -> "Event-" + i)
            .name("Generate Events");

        // 添加MetricsCheckpointListener到数据流中
        // 这个操作符会监听Checkpoint事件并记录指标
        DataStream<String> monitoredStream = dataStream
            .map(new MetricsCheckpointListener<>(checkpointListener))
            .name("Checkpoint Monitor");

        // 继续处理数据流
        monitoredStream
            .map(event -> {
                // 模拟数据处理
                logger.debug("Processing: {}", event);
                return event.toUpperCase();
            })
            .name("Process Events")
            .print();

        // 启动作业
        logger.info("Starting Flink job with checkpoint monitoring");
        env.execute("Checkpoint Listener Example");

        // 作业完成后输出最终指标
        logger.info("Final checkpoint metrics: {}", checkpointListener.getMetricsSummary());
    }

    /**
     * 创建Flink配置
     * @return FlinkConfig
     */
    private static FlinkConfig createFlinkConfig() {
        FlinkConfig config = new FlinkConfig();
        
        // 基本配置
        config.setParallelism(2);
        
        // Checkpoint配置
        config.setCheckpointInterval(60000L); // 1分钟（示例用，实际生产环境是5分钟）
        config.setCheckpointTimeout(600000L); // 10分钟
        config.setMinPauseBetweenCheckpoints(30000L); // 30秒
        config.setMaxConcurrentCheckpoints(1);
        config.setTolerableCheckpointFailures(3);
        config.setRetainedCheckpoints(3);
        
        // 状态后端配置
        config.setStateBackendType("hashmap");
        config.setCheckpointDir("/tmp/flink-checkpoints");
        
        // 重启策略
        config.setRestartStrategy("fixed-delay");
        config.setRestartAttempts(3);
        config.setRestartDelay(10000L);
        
        return config;
    }

    /**
     * 演示如何在自定义操作符中使用CheckpointListener
     */
    public static class CustomOperatorWithCheckpointMonitoring {
        
        public static void addCheckpointMonitoring(
            DataStream<?> dataStream,
            CheckpointListener checkpointListener) {
            
            // 方法1: 使用MetricsCheckpointListener
            dataStream.map(new MetricsCheckpointListener<>(checkpointListener))
                .name("Checkpoint Monitoring");
            
            // 方法2: 在自定义操作符中集成CheckpointListener
            // 可以在任何实现CheckpointedFunction的操作符中使用CheckpointListener
            
            logger.info("Checkpoint monitoring added to data stream");
        }
    }

    /**
     * 演示如何定期检查Checkpoint指标
     */
    public static class CheckpointMetricsReporter implements Runnable {
        private final CheckpointListener checkpointListener;
        private final long reportIntervalMs;
        private volatile boolean running = true;

        public CheckpointMetricsReporter(CheckpointListener checkpointListener, long reportIntervalMs) {
            this.checkpointListener = checkpointListener;
            this.reportIntervalMs = reportIntervalMs;
        }

        @Override
        public void run() {
            while (running) {
                try {
                    Thread.sleep(reportIntervalMs);
                    
                    // 输出指标摘要
                    logger.info("=== Checkpoint Metrics Report ===");
                    logger.info(checkpointListener.getMetricsSummary());
                    
                    // 检查失败率
                    double failureRate = checkpointListener.getFailureRate();
                    if (failureRate > 10.0) {
                        logger.error("WARNING: Checkpoint failure rate ({:.2f}%) exceeds threshold!", 
                            failureRate);
                    }
                    
                    // 检查平均耗时
                    long avgDuration = checkpointListener.getAverageCheckpointDuration();
                    if (avgDuration > 30000) { // 超过30秒
                        logger.warn("WARNING: Average checkpoint duration ({} ms) is high", avgDuration);
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in metrics reporter", e);
                }
            }
        }

        public void stop() {
            running = false;
        }
    }
}
