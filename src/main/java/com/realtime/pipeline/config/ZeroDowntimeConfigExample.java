package com.realtime.pipeline.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 零停机配置更新示例
 * 
 * 演示如何在不中断数据处理的情况下更新配置
 * 
 * 验证需求: 5.7
 */
public class ZeroDowntimeConfigExample {
    private static final Logger logger = LoggerFactory.getLogger(ZeroDowntimeConfigExample.class);
    
    public static void main(String[] args) throws Exception {
        logger.info("=== Zero-Downtime Configuration Update Example ===");
        
        // 创建初始配置
        FlinkConfig initialConfig = FlinkConfig.builder()
            .parallelism(4)
            .checkpointInterval(300000L)
            .checkpointTimeout(600000L)
            .checkpointDir("/tmp/checkpoints")
            .build();
        
        // 创建配置更新管理器
        ConfigurationUpdateManager configManager = new ConfigurationUpdateManager(initialConfig);
        
        // 注册配置变更监听器
        configManager.addConfigChangeListener(new ConfigurationUpdateManager.ConfigChangeListener() {
            @Override
            public void onFlinkConfigChanged(FlinkConfig oldConfig, FlinkConfig newConfig) {
                logger.info("Flink config changed:");
                logger.info("  Parallelism: {} -> {}", oldConfig.getParallelism(), newConfig.getParallelism());
                logger.info("  Checkpoint interval: {} -> {}", 
                    oldConfig.getCheckpointInterval(), newConfig.getCheckpointInterval());
            }
            
            @Override
            public void onDynamicConfigChanged(String key, Object oldValue, Object newValue) {
                logger.info("Dynamic config changed: {} = {} -> {}", key, oldValue, newValue);
            }
        });
        
        // 模拟数据处理任务
        AtomicInteger processedCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch latch = new CountDownLatch(1);
        
        // 启动数据处理任务
        for (int i = 0; i < 4; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    latch.await(); // 等待开始信号
                    
                    // 模拟持续的数据处理
                    for (int j = 0; j < 100; j++) {
                        // 获取当前配置
                        FlinkConfig currentConfig = configManager.getCurrentFlinkConfig();
                        
                        // 使用当前配置处理数据
                        processData(taskId, j, currentConfig);
                        processedCount.incrementAndGet();
                        
                        // 模拟处理延迟
                        Thread.sleep(50);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        
        // 等待一秒后开始处理
        Thread.sleep(1000);
        logger.info("Starting data processing...");
        latch.countDown();
        
        // 在数据处理过程中更新配置
        Thread.sleep(2000);
        logger.info("\n=== Updating configuration during data processing ===");
        
        // 更新Flink配置
        FlinkConfig newConfig = FlinkConfig.builder()
            .parallelism(8) // 增加并行度
            .checkpointInterval(180000L) // 减少checkpoint间隔
            .checkpointTimeout(600000L)
            .checkpointDir("/tmp/checkpoints")
            .build();
        
        configManager.updateFlinkConfig(newConfig);
        logger.info("Configuration updated successfully!");
        
        // 更新动态配置
        Thread.sleep(1000);
        logger.info("\n=== Updating dynamic configuration ===");
        
        Map<String, Object> dynamicUpdates = new HashMap<>();
        dynamicUpdates.put("log.level", "DEBUG");
        dynamicUpdates.put("metrics.sampling.rate", 0.5);
        dynamicUpdates.put("alert.latency.threshold", 120);
        
        configManager.updateDynamicConfigs(dynamicUpdates);
        logger.info("Dynamic configuration updated!");
        
        // 等待数据处理完成
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        
        // 输出结果
        logger.info("\n=== Processing completed ===");
        logger.info("Total records processed: {}", processedCount.get());
        logger.info("Data processing was NOT interrupted during configuration updates");
        
        // 显示更新历史
        logger.info("\n=== Configuration update history ===");
        for (ConfigurationUpdateManager.ConfigUpdateRecord record : configManager.getUpdateHistory()) {
            logger.info(record.toString());
        }
        
        // 显示当前配置
        logger.info("\n=== Current configuration ===");
        FlinkConfig currentConfig = configManager.getCurrentFlinkConfig();
        logger.info("Parallelism: {}", currentConfig.getParallelism());
        logger.info("Checkpoint interval: {}", currentConfig.getCheckpointInterval());
        
        Map<String, Object> dynamicConfigs = configManager.getAllDynamicConfigs();
        logger.info("Dynamic configs: {}", dynamicConfigs);
    }
    
    /**
     * 模拟数据处理
     */
    private static void processData(int taskId, int recordId, FlinkConfig config) {
        // 使用当前配置处理数据
        // 在实际应用中，这里会使用config中的参数来处理数据
        if (recordId % 20 == 0) {
            logger.debug("Task {} processing record {} with parallelism {}", 
                taskId, recordId, config.getParallelism());
        }
    }
}
