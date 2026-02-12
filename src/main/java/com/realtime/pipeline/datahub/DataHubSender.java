package com.realtime.pipeline.datahub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtime.pipeline.config.DataHubConfig;
import com.realtime.pipeline.datahub.client.*;
import com.realtime.pipeline.model.ChangeEvent;
import com.realtime.pipeline.util.RetryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DataHub发送器
 * 负责将变更数据发送到阿里云DataHub
 * 
 * 功能:
 * - 集成阿里云DataHub SDK
 * - 实现变更数据发送到DataHub
 * - 实现重试机制（最多3次，间隔2秒）
 * - 提供发送指标监控
 * 
 * 需求: 1.4, 1.5
 */
public class DataHubSender implements Serializable, AutoCloseable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(DataHubSender.class);

    private final DataHubConfig config;
    private transient DataHubClient client;
    private final AtomicLong recordsSent = new AtomicLong(0);
    private final AtomicLong recordsFailed = new AtomicLong(0);
    private final AtomicLong retryCount = new AtomicLong(0);
    private transient ObjectMapper objectMapper;

    /**
     * 构造函数
     * @param config DataHub配置
     */
    public DataHubSender(DataHubConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("DataHub config cannot be null");
        }
        config.validate();
        this.config = config;
        initializeClient();
    }

    /**
     * 构造函数（用于测试，可以注入自定义客户端）
     * @param config DataHub配置
     * @param client DataHub客户端
     */
    public DataHubSender(DataHubConfig config, DataHubClient client) {
        if (config == null) {
            throw new IllegalArgumentException("DataHub config cannot be null");
        }
        config.validate();
        this.config = config;
        this.client = client;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 初始化DataHub客户端
     */
    private void initializeClient() {
        try {
            logger.info("Initializing DataHub client - endpoint: {}, project: {}, topic: {}", 
                config.getEndpoint(), config.getProject(), config.getTopic());

            // 创建默认客户端实现
            // 在生产环境中，应该使用阿里云官方的DataHub SDK客户端
            this.client = new DefaultDataHubClient(config);
            this.objectMapper = new ObjectMapper();

            logger.info("DataHub client initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize DataHub client", e);
            throw new RuntimeException("Failed to initialize DataHub client", e);
        }
    }

    /**
     * 发送单个变更事件到DataHub
     * 
     * 实现需求:
     * - 需求1.4: 将变更数据发送到DataHub
     * - 需求1.5: 失败时重试最多3次，每次间隔2秒
     * 
     * @param event 变更事件
     * @throws DataHubSendException 如果发送失败（包括所有重试）
     */
    public void send(ChangeEvent event) throws DataHubSendException {
        if (event == null) {
            throw new IllegalArgumentException("ChangeEvent cannot be null");
        }

        logger.debug("Sending change event to DataHub - eventId: {}, type: {}, table: {}", 
            event.getEventId(), event.getEventType(), event.getTable());

        try {
            // 使用重试机制发送数据
            RetryUtil.executeWithRetry(
                () -> sendInternal(event),
                config.getMaxRetries(),
                config.getRetryBackoff() * 1000L, // 转换为毫秒
                DataHubClientException.class
            );

            recordsSent.incrementAndGet();
            logger.debug("Successfully sent event to DataHub - eventId: {}", event.getEventId());

        } catch (Exception e) {
            recordsFailed.incrementAndGet();
            logger.error("Failed to send event to DataHub after {} retries - eventId: {}", 
                config.getMaxRetries(), event.getEventId(), e);
            throw new DataHubSendException("Failed to send event to DataHub: " + event.getEventId(), e);
        }
    }

    /**
     * 批量发送变更事件到DataHub
     * 
     * @param events 变更事件列表
     * @throws DataHubSendException 如果发送失败
     */
    public void sendBatch(List<ChangeEvent> events) throws DataHubSendException {
        if (events == null || events.isEmpty()) {
            return;
        }

        logger.debug("Sending batch of {} events to DataHub", events.size());

        try {
            // 使用重试机制发送批量数据
            RetryUtil.executeWithRetry(
                () -> sendBatchInternal(events),
                config.getMaxRetries(),
                config.getRetryBackoff() * 1000L,
                DataHubClientException.class
            );

            recordsSent.addAndGet(events.size());
            logger.debug("Successfully sent batch of {} events to DataHub", events.size());

        } catch (Exception e) {
            recordsFailed.addAndGet(events.size());
            logger.error("Failed to send batch to DataHub after {} retries", 
                config.getMaxRetries(), e);
            throw new DataHubSendException("Failed to send batch to DataHub", e);
        }
    }

    /**
     * 内部方法：发送单个事件
     * 
     * @param event 变更事件
     */
    private void sendInternal(ChangeEvent event) {
        ensureClientInitialized();

        // 创建记录
        RecordEntry record = createRecordEntry(event);

        // 发送记录
        List<RecordEntry> records = new ArrayList<>();
        records.add(record);

        PutRecordsResult result = client.putRecords(
            config.getProject(),
            config.getTopic(),
            records
        );

        // 检查发送结果
        if (result.getFailedRecordCount() > 0) {
            retryCount.incrementAndGet();
            String errorMessage = result.getFailedRecords().get(0).getErrorMessage();
            throw new DataHubClientException("Failed to put record: " + errorMessage);
        }
    }

    /**
     * 内部方法：批量发送事件
     * 
     * @param events 变更事件列表
     */
    private void sendBatchInternal(List<ChangeEvent> events) {
        ensureClientInitialized();

        // 创建记录列表
        List<RecordEntry> records = new ArrayList<>();
        for (ChangeEvent event : events) {
            records.add(createRecordEntry(event));
        }

        // 批量发送记录
        PutRecordsResult result = client.putRecords(
            config.getProject(),
            config.getTopic(),
            records
        );

        // 检查发送结果
        if (result.getFailedRecordCount() > 0) {
            retryCount.addAndGet(result.getFailedRecordCount());
            StringBuilder errorMsg = new StringBuilder("Failed to put records: ");
            result.getFailedRecords().forEach(failedRecord -> 
                errorMsg.append(failedRecord.getErrorMessage()).append("; ")
            );
            throw new DataHubClientException(errorMsg.toString());
        }
    }

    /**
     * 创建DataHub记录条目
     * 
     * @param event 变更事件
     * @return DataHub记录条目
     */
    private RecordEntry createRecordEntry(ChangeEvent event) {
        RecordEntry record = new RecordEntry();
        
        // 设置分区键（使用表名作为分区键，确保同一表的数据在同一分区）
        record.setShardId(String.valueOf(Math.abs(event.getTable().hashCode()) % 10));
        
        // 添加字段
        record.addField("eventId", event.getEventId());
        record.addField("eventType", event.getEventType());
        record.addField("database", event.getDatabase());
        record.addField("table", event.getTable());
        record.addField("timestamp", event.getTimestamp());
        
        // 将before和after数据序列化为JSON字符串
        if (event.getBefore() != null) {
            record.addField("before", serializeMap(event.getBefore()));
        }
        if (event.getAfter() != null) {
            record.addField("after", serializeMap(event.getAfter()));
        }
        
        // 添加主键信息
        if (event.getPrimaryKeys() != null) {
            record.addField("primaryKeys", String.join(",", event.getPrimaryKeys()));
        }
        
        return record;
    }

    /**
     * 序列化Map为JSON字符串
     * 
     * @param map 要序列化的Map
     * @return JSON字符串
     */
    private String serializeMap(Map<String, Object> map) {
        try {
            if (objectMapper == null) {
                objectMapper = new ObjectMapper();
            }
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            logger.error("Failed to serialize map to JSON", e);
            return "{}";
        }
    }

    /**
     * 确保客户端已初始化
     */
    private void ensureClientInitialized() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    initializeClient();
                }
            }
        }
    }

    /**
     * 获取已发送记录数
     * 
     * @return 已发送记录数
     */
    public long getRecordsSent() {
        return recordsSent.get();
    }

    /**
     * 获取失败记录数
     * 
     * @return 失败记录数
     */
    public long getRecordsFailed() {
        return recordsFailed.get();
    }

    /**
     * 获取重试次数
     * 
     * @return 重试次数
     */
    public long getRetryCount() {
        return retryCount.get();
    }

    /**
     * 获取DataHub配置
     * 
     * @return DataHub配置
     */
    public DataHubConfig getConfig() {
        return config;
    }

    /**
     * 关闭DataHub客户端
     */
    @Override
    public void close() {
        if (client != null) {
            try {
                logger.info("Closing DataHub client");
                client.close();
                client = null;
            } catch (Exception e) {
                logger.error("Error closing DataHub client", e);
            }
        }
    }

    /**
     * 测试DataHub连接
     * 
     * @return true如果连接成功
     */
    public boolean testConnection() {
        try {
            ensureClientInitialized();
            // 尝试获取topic信息来测试连接
            client.getTopic(config.getProject(), config.getTopic());
            logger.info("DataHub connection test successful");
            return true;
        } catch (Exception e) {
            logger.error("DataHub connection test failed", e);
            return false;
        }
    }
}
