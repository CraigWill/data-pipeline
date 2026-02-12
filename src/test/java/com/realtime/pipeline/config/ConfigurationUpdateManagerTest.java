package com.realtime.pipeline.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConfigurationUpdateManager单元测试
 * 
 * 测试配置热更新功能
 */
class ConfigurationUpdateManagerTest {
    
    private FlinkConfig initialConfig;
    private ConfigurationUpdateManager configManager;
    
    @BeforeEach
    void setUp() {
        initialConfig = FlinkConfig.builder()
            .parallelism(4)
            .checkpointInterval(300000L)
            .checkpointTimeout(600000L)
            .checkpointDir("/tmp/checkpoints")
            .build();
        
        configManager = new ConfigurationUpdateManager(initialConfig);
    }
    
    @Test
    void testConstructorWithNullConfig() {
        assertThrows(IllegalArgumentException.class, 
            () -> new ConfigurationUpdateManager(null));
    }
    
    @Test
    void testGetCurrentFlinkConfig() {
        FlinkConfig current = configManager.getCurrentFlinkConfig();
        assertNotNull(current);
        assertEquals(4, current.getParallelism());
        assertEquals(300000L, current.getCheckpointInterval());
    }
    
    @Test
    void testUpdateFlinkConfig() {
        // 创建新配置
        FlinkConfig newConfig = FlinkConfig.builder()
            .parallelism(8)
            .checkpointInterval(180000L)
            .checkpointTimeout(600000L)
            .checkpointDir("/tmp/checkpoints")
            .build();
        
        // 更新配置
        configManager.updateFlinkConfig(newConfig);
        
        // 验证配置已更新
        FlinkConfig current = configManager.getCurrentFlinkConfig();
        assertEquals(8, current.getParallelism());
        assertEquals(180000L, current.getCheckpointInterval());
    }
    
    @Test
    void testUpdateFlinkConfigWithNullConfig() {
        assertThrows(IllegalArgumentException.class, 
            () -> configManager.updateFlinkConfig(null));
    }
    
    @Test
    void testUpdateFlinkConfigWithInvalidConfig() {
        // 创建无效配置（负数并行度）
        FlinkConfig invalidConfig = FlinkConfig.builder()
            .parallelism(-1)
            .checkpointInterval(300000L)
            .checkpointTimeout(600000L)
            .checkpointDir("/tmp/checkpoints")
            .build();
        
        // 验证抛出异常
        assertThrows(IllegalArgumentException.class, 
            () -> configManager.updateFlinkConfig(invalidConfig));
        
        // 验证配置未被更新
        FlinkConfig current = configManager.getCurrentFlinkConfig();
        assertEquals(4, current.getParallelism());
    }
    
    @Test
    void testUpdateDynamicConfig() {
        // 更新动态配置
        configManager.updateDynamicConfig("log.level", "DEBUG");
        
        // 验证配置已更新
        assertEquals("DEBUG", configManager.getDynamicConfig("log.level"));
    }
    
    @Test
    void testUpdateDynamicConfigWithNullKey() {
        assertThrows(IllegalArgumentException.class, 
            () -> configManager.updateDynamicConfig(null, "value"));
    }
    
    @Test
    void testUpdateDynamicConfigWithEmptyKey() {
        assertThrows(IllegalArgumentException.class, 
            () -> configManager.updateDynamicConfig("", "value"));
    }
    
    @Test
    void testGetDynamicConfigWithDefault() {
        // 获取不存在的配置，应返回默认值
        String value = configManager.getDynamicConfig("nonexistent", "default");
        assertEquals("default", value);
        
        // 设置配置后再获取
        configManager.updateDynamicConfig("test.key", "test.value");
        String actualValue = configManager.getDynamicConfig("test.key", "default");
        assertEquals("test.value", actualValue);
    }
    
    @Test
    void testUpdateDynamicConfigs() {
        // 批量更新
        Map<String, Object> updates = new HashMap<>();
        updates.put("key1", "value1");
        updates.put("key2", 123);
        updates.put("key3", true);
        
        configManager.updateDynamicConfigs(updates);
        
        // 验证所有配置已更新
        assertEquals("value1", configManager.getDynamicConfig("key1"));
        assertEquals(123, configManager.getDynamicConfig("key2"));
        assertEquals(true, configManager.getDynamicConfig("key3"));
    }
    
    @Test
    void testUpdateDynamicConfigsWithNullMap() {
        assertThrows(IllegalArgumentException.class, 
            () -> configManager.updateDynamicConfigs(null));
    }
    
    @Test
    void testUpdateDynamicConfigsWithEmptyMap() {
        assertThrows(IllegalArgumentException.class, 
            () -> configManager.updateDynamicConfigs(new HashMap<>()));
    }
    
    @Test
    void testGetAllDynamicConfigs() {
        // 添加一些配置
        configManager.updateDynamicConfig("key1", "value1");
        configManager.updateDynamicConfig("key2", "value2");
        
        // 获取所有配置
        Map<String, Object> allConfigs = configManager.getAllDynamicConfigs();
        
        assertEquals(2, allConfigs.size());
        assertEquals("value1", allConfigs.get("key1"));
        assertEquals("value2", allConfigs.get("key2"));
    }
    
    @Test
    void testConfigChangeListener() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<FlinkConfig> capturedOldConfig = new AtomicReference<>();
        AtomicReference<FlinkConfig> capturedNewConfig = new AtomicReference<>();
        
        // 注册监听器
        configManager.addConfigChangeListener(new ConfigurationUpdateManager.ConfigChangeListener() {
            @Override
            public void onFlinkConfigChanged(FlinkConfig oldConfig, FlinkConfig newConfig) {
                capturedOldConfig.set(oldConfig);
                capturedNewConfig.set(newConfig);
                latch.countDown();
            }
        });
        
        // 更新配置
        FlinkConfig newConfig = FlinkConfig.builder()
            .parallelism(8)
            .checkpointInterval(180000L)
            .checkpointTimeout(600000L)
            .checkpointDir("/tmp/checkpoints")
            .build();
        
        configManager.updateFlinkConfig(newConfig);
        
        // 等待监听器被调用
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        
        // 验证监听器收到了正确的配置
        assertNotNull(capturedOldConfig.get());
        assertNotNull(capturedNewConfig.get());
        assertEquals(4, capturedOldConfig.get().getParallelism());
        assertEquals(8, capturedNewConfig.get().getParallelism());
    }
    
    @Test
    void testDynamicConfigChangeListener() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> capturedKey = new AtomicReference<>();
        AtomicReference<Object> capturedOldValue = new AtomicReference<>();
        AtomicReference<Object> capturedNewValue = new AtomicReference<>();
        
        // 注册监听器
        configManager.addConfigChangeListener(new ConfigurationUpdateManager.ConfigChangeListener() {
            @Override
            public void onDynamicConfigChanged(String key, Object oldValue, Object newValue) {
                capturedKey.set(key);
                capturedOldValue.set(oldValue);
                capturedNewValue.set(newValue);
                latch.countDown();
            }
        });
        
        // 更新动态配置
        configManager.updateDynamicConfig("test.key", "test.value");
        
        // 等待监听器被调用
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        
        // 验证监听器收到了正确的值
        assertEquals("test.key", capturedKey.get());
        assertNull(capturedOldValue.get());
        assertEquals("test.value", capturedNewValue.get());
    }
    
    @Test
    void testRemoveConfigChangeListener() throws InterruptedException {
        AtomicInteger callCount = new AtomicInteger(0);
        
        ConfigurationUpdateManager.ConfigChangeListener listener = 
            new ConfigurationUpdateManager.ConfigChangeListener() {
                @Override
                public void onDynamicConfigChanged(String key, Object oldValue, Object newValue) {
                    callCount.incrementAndGet();
                }
            };
        
        // 注册监听器
        configManager.addConfigChangeListener(listener);
        
        // 更新配置，监听器应该被调用
        configManager.updateDynamicConfig("key1", "value1");
        Thread.sleep(100);
        assertEquals(1, callCount.get());
        
        // 移除监听器
        configManager.removeConfigChangeListener(listener);
        
        // 再次更新配置，监听器不应该被调用
        configManager.updateDynamicConfig("key2", "value2");
        Thread.sleep(100);
        assertEquals(1, callCount.get()); // 计数不应该增加
    }
    
    @Test
    void testUpdateHistory() {
        // 更新配置
        FlinkConfig newConfig = FlinkConfig.builder()
            .parallelism(8)
            .checkpointInterval(180000L)
            .checkpointTimeout(600000L)
            .checkpointDir("/tmp/checkpoints")
            .build();
        
        configManager.updateFlinkConfig(newConfig);
        configManager.updateDynamicConfig("key1", "value1");
        
        // 获取更新历史
        var history = configManager.getUpdateHistory();
        
        assertEquals(2, history.size());
        assertEquals("FlinkConfig", history.get(0).getConfigType());
        assertEquals("DynamicConfig.key1", history.get(1).getConfigType());
    }
    
    @Test
    void testClearUpdateHistory() {
        // 添加一些更新
        configManager.updateDynamicConfig("key1", "value1");
        configManager.updateDynamicConfig("key2", "value2");
        
        // 验证历史不为空
        assertFalse(configManager.getUpdateHistory().isEmpty());
        
        // 清除历史
        configManager.clearUpdateHistory();
        
        // 验证历史已清空
        assertTrue(configManager.getUpdateHistory().isEmpty());
    }
    
    @Test
    void testConcurrentConfigUpdates() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        // 启动多个线程同时更新配置
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            new Thread(() -> {
                try {
                    startLatch.await();
                    configManager.updateDynamicConfig("key" + index, "value" + index);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }
        
        // 开始所有线程
        startLatch.countDown();
        
        // 等待所有线程完成
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        
        // 给一点时间让所有更新完成
        Thread.sleep(200);
        
        // 验证所有线程都成功执行
        assertEquals(threadCount, successCount.get());
        
        // 验证所有配置都已更新
        Map<String, Object> allConfigs = configManager.getAllDynamicConfigs();
        assertEquals(threadCount, allConfigs.size(), 
            "Expected " + threadCount + " configs but got " + allConfigs.size() + ": " + allConfigs.keySet());
        
        for (int i = 0; i < threadCount; i++) {
            String key = "key" + i;
            assertTrue(allConfigs.containsKey(key), "Missing key: " + key);
            assertEquals("value" + i, allConfigs.get(key));
        }
    }
    
    @Test
    void testZeroDowntimeUpdate() throws InterruptedException {
        // 模拟数据处理过程中的配置更新
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger configChanges = new AtomicInteger(0);
        CountDownLatch processingLatch = new CountDownLatch(1);
        
        // 启动数据处理线程
        Thread processingThread = new Thread(() -> {
            try {
                processingLatch.await();
                
                // 持续处理数据
                for (int i = 0; i < 100; i++) {
                    FlinkConfig config = configManager.getCurrentFlinkConfig();
                    // 模拟使用配置处理数据
                    processedCount.incrementAndGet();
                    Thread.sleep(10);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        processingThread.start();
        
        // 开始处理
        processingLatch.countDown();
        Thread.sleep(200);
        
        // 在处理过程中更新配置
        FlinkConfig newConfig = FlinkConfig.builder()
            .parallelism(8)
            .checkpointInterval(180000L)
            .checkpointTimeout(600000L)
            .checkpointDir("/tmp/checkpoints")
            .build();
        
        configManager.updateFlinkConfig(newConfig);
        configChanges.incrementAndGet();
        
        // 等待处理完成
        processingThread.join(5000);
        
        // 验证数据处理没有中断
        assertEquals(100, processedCount.get());
        assertEquals(1, configChanges.get());
        
        // 验证配置已更新
        assertEquals(8, configManager.getCurrentFlinkConfig().getParallelism());
    }
}
