package com.realtime.pipeline.flink.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtime.pipeline.config.DataHubConfig;
import com.realtime.pipeline.datahub.client.DataHubClient;
import com.realtime.pipeline.datahub.client.DefaultDataHubClient;
import com.realtime.pipeline.model.ChangeEvent;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.source.RichSourceFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * DataHub数据源
 * 实现Flink Source连接DataHub，消费变更数据流
 * 
 * 功能:
 * - 连接DataHub并消费指定Topic的数据
 * - 管理消费者组和偏移量
 * - 反序列化JSON数据到ChangeEvent对象
 * - 支持配置起始消费位置
 * 
 * 需求: 2.1 - Flink从DataHub消费数据
 */
public class DataHubSource extends RichSourceFunction<ChangeEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(DataHubSource.class);
    private static final long serialVersionUID = 1L;

    private final DataHubConfig config;
    private final ChangeEventDeserializer deserializer;
    
    private transient DataHubClient client;
    private transient volatile boolean running;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * 构造函数
     * @param config DataHub配置
     */
    public DataHubSource(DataHubConfig config) {
        this.config = config;
        this.deserializer = new ChangeEventDeserializer();
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        
        // 验证配置
        config.validate();
        
        // 初始化DataHub客户端
        this.client = new DefaultDataHubClient(
            config.getEndpoint(),
            config.getAccessId(),
            config.getAccessKey()
        );
        
        this.running = true;
        this.initialized.set(true);
        
        LOG.info("DataHub Source initialized - Project: {}, Topic: {}, ConsumerGroup: {}, StartPosition: {}",
            config.getProject(), config.getTopic(), config.getConsumerGroup(), config.getStartPosition());
    }

    @Override
    public void run(SourceContext<ChangeEvent> ctx) throws Exception {
        if (!initialized.get()) {
            throw new IllegalStateException("DataHub Source not initialized. Call open() first.");
        }

        LOG.info("Starting to consume from DataHub - Project: {}, Topic: {}",
            config.getProject(), config.getTopic());

        // 主消费循环
        while (running) {
            try {
                // 从DataHub获取记录
                // 注意: 这里使用我们的mock客户端接口
                // 在实际生产环境中，需要使用真实的DataHub SDK
                List<Map<String, Object>> records = client.getRecords(
                    config.getProject(),
                    config.getTopic(),
                    config.getConsumerGroup(),
                    100  // 批量大小
                );

                if (records == null || records.isEmpty()) {
                    // 没有数据时短暂休眠，避免空轮询
                    Thread.sleep(100);
                    continue;
                }

                // 处理每条记录
                for (Map<String, Object> record : records) {
                    if (!running) {
                        break;
                    }

                    try {
                        // 反序列化为ChangeEvent
                        ChangeEvent event = deserializer.deserialize(record);
                        
                        // 发送到下游
                        synchronized (ctx.getCheckpointLock()) {
                            ctx.collect(event);
                        }
                        
                        LOG.debug("Consumed event: {} - {}.{}", 
                            event.getEventId(), event.getDatabase(), event.getTable());
                        
                    } catch (Exception e) {
                        LOG.error("Failed to deserialize record: {}", record, e);
                        // 继续处理下一条记录
                    }
                }

                // 提交偏移量
                // 在实际实现中，这里应该调用DataHub的commit offset API
                LOG.trace("Processed {} records from DataHub", records.size());

            } catch (InterruptedException e) {
                LOG.info("DataHub Source interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOG.error("Error consuming from DataHub", e);
                
                // 如果是致命错误，停止运行
                if (!running) {
                    break;
                }
                
                // 短暂休眠后重试
                Thread.sleep(1000);
            }
        }

        LOG.info("DataHub Source stopped");
    }

    @Override
    public void cancel() {
        LOG.info("Cancelling DataHub Source");
        this.running = false;
    }

    @Override
    public void close() throws Exception {
        this.running = false;
        
        if (client != null) {
            try {
                client.close();
                LOG.info("DataHub client closed");
            } catch (Exception e) {
                LOG.error("Error closing DataHub client", e);
            }
        }
        
        super.close();
    }

    /**
     * ChangeEvent反序列化器
     * 将JSON数据反序列化为ChangeEvent对象
     */
    public static class ChangeEventDeserializer implements DeserializationSchema<ChangeEvent> {
        private static final long serialVersionUID = 1L;
        private transient ObjectMapper objectMapper;

        @Override
        public void open(InitializationContext context) throws Exception {
            this.objectMapper = new ObjectMapper();
        }

        @Override
        public ChangeEvent deserialize(byte[] message) throws IOException {
            if (objectMapper == null) {
                objectMapper = new ObjectMapper();
            }
            return objectMapper.readValue(message, ChangeEvent.class);
        }

        /**
         * 从Map反序列化（用于DataHub记录）
         */
        public ChangeEvent deserialize(Map<String, Object> record) throws IOException {
            if (objectMapper == null) {
                objectMapper = new ObjectMapper();
            }
            
            // 将Map转换为JSON字符串，再反序列化为ChangeEvent
            String json = objectMapper.writeValueAsString(record);
            return objectMapper.readValue(json, ChangeEvent.class);
        }

        @Override
        public boolean isEndOfStream(ChangeEvent nextElement) {
            return false;  // 流式数据源，永不结束
        }

        @Override
        public TypeInformation<ChangeEvent> getProducedType() {
            return TypeInformation.of(ChangeEvent.class);
        }
    }

    /**
     * 获取消费者组名称
     */
    public String getConsumerGroup() {
        return config.getConsumerGroup();
    }

    /**
     * 获取起始消费位置
     */
    public String getStartPosition() {
        return config.getStartPosition();
    }

    /**
     * 检查Source是否正在运行
     */
    public boolean isRunning() {
        return running;
    }
}
