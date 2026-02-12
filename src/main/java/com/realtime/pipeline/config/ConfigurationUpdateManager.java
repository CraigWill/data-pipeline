package com.realtime.pipeline.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 配置更新管理器
 * 支持运行时配置热更新，实现零停机配置变更
 * 
 * 功能:
 * 1. 支持运行时配置热更新
 * 2. 配置变更不中断数据处理
 * 3. 通知监听器配置变更
 * 4. 验证配置有效性
 * 
 * 验证需求: 5.7
 * 属性 22: 零停机配置更新
 */
public class ConfigurationUpdateManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationUpdateManager.class);
    
    // 使用AtomicReference保证配置更新的原子性
    private final AtomicReference<FlinkConfig> currentFlinkConfig;
    private final AtomicReference<Map<String, Object>> currentDynamicConfig;
    
    // 配置变更监听器
    private final CopyOnWriteArrayList<ConfigChangeListener> listeners;
    
    // 配置更新历史
    private final CopyOnWriteArrayList<ConfigUpdateRecord> updateHistory;
    
    // 最大历史记录数
    private static final int MAX_HISTORY_SIZE = 100;
    
    /**
     * 构造函数
     * @param initialFlinkConfig 初始Flink配置
     */
    public ConfigurationUpdateManager(FlinkConfig initialFlinkConfig) {
        if (initialFlinkConfig == null) {
            throw new IllegalArgumentException("Initial FlinkConfig cannot be null");
        }
        
        this.currentFlinkConfig = new AtomicReference<>(initialFlinkConfig);
        this.currentDynamicConfig = new AtomicReference<>(new ConcurrentHashMap<>());
        this.listeners = new CopyOnWriteArrayList<>();
        this.updateHistory = new CopyOnWriteArrayList<>();
        
        logger.info("ConfigurationUpdateManager initialized with initial config");
    }
    
    /**
     * 更新Flink配置（热更新）
     * 
     * 需求 5.7: THE System SHALL 支持零停机时间的配置更新
     * 
     * 此方法实现零停机配置更新:
     * 1. 验证新配置的有效性
     * 2. 原子性地更新配置引用
     * 3. 通知所有监听器
     * 4. 记录更新历史
     * 
     * 数据处理不会中断，因为:
     * - 使用AtomicReference保证配置切换的原子性
     * - 正在处理的数据继续使用旧配置
     * - 新的数据处理使用新配置
     * 
     * @param newConfig 新的Flink配置
     * @throws IllegalArgumentException 如果配置无效
     */
    public void updateFlinkConfig(FlinkConfig newConfig) {
        if (newConfig == null) {
            throw new IllegalArgumentException("New FlinkConfig cannot be null");
        }
        
        logger.info("Attempting to update Flink configuration");
        
        // 步骤1: 验证新配置的有效性
        try {
            newConfig.validate();
            logger.info("New configuration validated successfully");
        } catch (Exception e) {
            logger.error("Configuration validation failed", e);
            throw new IllegalArgumentException("Invalid configuration: " + e.getMessage(), e);
        }
        
        // 步骤2: 获取旧配置（用于回滚和通知）
        FlinkConfig oldConfig = currentFlinkConfig.get();
        
        // 步骤3: 原子性地更新配置
        // 使用AtomicReference.set()保证配置切换的原子性
        // 这确保了配置更新不会中断正在进行的数据处理
        currentFlinkConfig.set(newConfig);
        logger.info("Configuration updated atomically");
        
        // 步骤4: 记录更新历史
        ConfigUpdateRecord record = new ConfigUpdateRecord(
            System.currentTimeMillis(),
            "FlinkConfig",
            oldConfig,
            newConfig
        );
        addUpdateRecord(record);
        
        // 步骤5: 通知所有监听器
        notifyListeners(oldConfig, newConfig);
        
        logger.info("Flink configuration updated successfully without interrupting data processing");
    }
    
    /**
     * 更新动态配置参数
     * 
     * 支持更新不需要重启作业的配置参数，例如:
     * - 日志级别
     * - 监控采样率
     * - 告警阈值
     * - 其他运行时参数
     * 
     * @param key 配置键
     * @param value 配置值
     */
    public synchronized void updateDynamicConfig(String key, Object value) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Config key cannot be null or empty");
        }
        
        logger.info("Updating dynamic config: {} = {}", key, value);
        
        // 获取当前动态配置
        Map<String, Object> currentConfig = currentDynamicConfig.get();
        Object oldValue = currentConfig.get(key);
        
        // 创建新的配置Map（不可变更新）
        Map<String, Object> newConfig = new ConcurrentHashMap<>(currentConfig);
        newConfig.put(key, value);
        
        // 原子性地更新配置
        currentDynamicConfig.set(newConfig);
        
        // 记录更新历史
        ConfigUpdateRecord record = new ConfigUpdateRecord(
            System.currentTimeMillis(),
            "DynamicConfig." + key,
            oldValue,
            value
        );
        addUpdateRecord(record);
        
        // 通知监听器
        notifyDynamicConfigChange(key, oldValue, value);
        
        logger.info("Dynamic config updated: {} = {}", key, value);
    }
    
    /**
     * 批量更新动态配置
     * 
     * @param updates 配置更新Map
     */
    public void updateDynamicConfigs(Map<String, Object> updates) {
        if (updates == null || updates.isEmpty()) {
            throw new IllegalArgumentException("Updates map cannot be null or empty");
        }
        
        logger.info("Batch updating {} dynamic configs", updates.size());
        
        // 获取当前配置
        Map<String, Object> currentConfig = currentDynamicConfig.get();
        
        // 创建新配置
        Map<String, Object> newConfig = new ConcurrentHashMap<>(currentConfig);
        newConfig.putAll(updates);
        
        // 原子性地更新
        currentDynamicConfig.set(newConfig);
        
        // 记录每个更新
        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            String key = entry.getKey();
            Object newValue = entry.getValue();
            Object oldValue = currentConfig.get(key);
            
            ConfigUpdateRecord record = new ConfigUpdateRecord(
                System.currentTimeMillis(),
                "DynamicConfig." + key,
                oldValue,
                newValue
            );
            addUpdateRecord(record);
            
            // 通知监听器
            notifyDynamicConfigChange(key, oldValue, newValue);
        }
        
        logger.info("Batch update completed for {} configs", updates.size());
    }
    
    /**
     * 获取当前Flink配置
     * 
     * @return 当前的FlinkConfig
     */
    public FlinkConfig getCurrentFlinkConfig() {
        return currentFlinkConfig.get();
    }
    
    /**
     * 获取动态配置值
     * 
     * @param key 配置键
     * @return 配置值，如果不存在返回null
     */
    public Object getDynamicConfig(String key) {
        return currentDynamicConfig.get().get(key);
    }
    
    /**
     * 获取动态配置值（带默认值）
     * 
     * @param key 配置键
     * @param defaultValue 默认值
     * @param <T> 值类型
     * @return 配置值或默认值
     */
    @SuppressWarnings("unchecked")
    public <T> T getDynamicConfig(String key, T defaultValue) {
        Object value = currentDynamicConfig.get().get(key);
        return value != null ? (T) value : defaultValue;
    }
    
    /**
     * 获取所有动态配置
     * 
     * @return 动态配置Map的副本
     */
    public Map<String, Object> getAllDynamicConfigs() {
        return new ConcurrentHashMap<>(currentDynamicConfig.get());
    }
    
    /**
     * 注册配置变更监听器
     * 
     * @param listener 监听器
     */
    public void addConfigChangeListener(ConfigChangeListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        listeners.add(listener);
        logger.info("Config change listener registered: {}", listener.getClass().getSimpleName());
    }
    
    /**
     * 移除配置变更监听器
     * 
     * @param listener 监听器
     */
    public void removeConfigChangeListener(ConfigChangeListener listener) {
        listeners.remove(listener);
        logger.info("Config change listener removed: {}", listener.getClass().getSimpleName());
    }
    
    /**
     * 获取配置更新历史
     * 
     * @return 更新历史列表
     */
    public CopyOnWriteArrayList<ConfigUpdateRecord> getUpdateHistory() {
        return new CopyOnWriteArrayList<>(updateHistory);
    }
    
    /**
     * 清除更新历史
     */
    public void clearUpdateHistory() {
        updateHistory.clear();
        logger.info("Update history cleared");
    }
    
    /**
     * 添加更新记录
     * 
     * @param record 更新记录
     */
    private void addUpdateRecord(ConfigUpdateRecord record) {
        updateHistory.add(record);
        
        // 限制历史记录大小
        while (updateHistory.size() > MAX_HISTORY_SIZE) {
            updateHistory.remove(0);
        }
    }
    
    /**
     * 通知监听器Flink配置变更
     * 
     * @param oldConfig 旧配置
     * @param newConfig 新配置
     */
    private void notifyListeners(FlinkConfig oldConfig, FlinkConfig newConfig) {
        for (ConfigChangeListener listener : listeners) {
            try {
                listener.onFlinkConfigChanged(oldConfig, newConfig);
            } catch (Exception e) {
                logger.error("Error notifying listener: " + listener.getClass().getSimpleName(), e);
            }
        }
    }
    
    /**
     * 通知监听器动态配置变更
     * 
     * @param key 配置键
     * @param oldValue 旧值
     * @param newValue 新值
     */
    private void notifyDynamicConfigChange(String key, Object oldValue, Object newValue) {
        for (ConfigChangeListener listener : listeners) {
            try {
                listener.onDynamicConfigChanged(key, oldValue, newValue);
            } catch (Exception e) {
                logger.error("Error notifying listener: " + listener.getClass().getSimpleName(), e);
            }
        }
    }
    
    /**
     * 配置变更监听器接口
     */
    public interface ConfigChangeListener {
        /**
         * Flink配置变更回调
         * 
         * @param oldConfig 旧配置
         * @param newConfig 新配置
         */
        default void onFlinkConfigChanged(FlinkConfig oldConfig, FlinkConfig newConfig) {
            // 默认空实现
        }
        
        /**
         * 动态配置变更回调
         * 
         * @param key 配置键
         * @param oldValue 旧值
         * @param newValue 新值
         */
        default void onDynamicConfigChanged(String key, Object oldValue, Object newValue) {
            // 默认空实现
        }
    }
    
    /**
     * 配置更新记录
     */
    public static class ConfigUpdateRecord {
        private final long timestamp;
        private final String configType;
        private final Object oldValue;
        private final Object newValue;
        
        public ConfigUpdateRecord(long timestamp, String configType, Object oldValue, Object newValue) {
            this.timestamp = timestamp;
            this.configType = configType;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        public String getConfigType() {
            return configType;
        }
        
        public Object getOldValue() {
            return oldValue;
        }
        
        public Object getNewValue() {
            return newValue;
        }
        
        @Override
        public String toString() {
            return String.format("ConfigUpdateRecord{timestamp=%d, type=%s, old=%s, new=%s}",
                timestamp, configType, oldValue, newValue);
        }
    }
}
