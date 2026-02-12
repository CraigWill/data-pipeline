package com.realtime.pipeline.flink.source;

import com.realtime.pipeline.config.DataHubConfig;
import com.realtime.pipeline.model.ChangeEvent;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DataHub Source使用示例
 * 
 * 演示如何在Flink作业中使用DataHubSource消费变更数据
 * 
 * 使用方法:
 * 1. 配置DataHub连接参数
 * 2. 创建DataHubSource实例
 * 3. 添加到Flink DataStream
 * 4. 执行作业
 */
public class DataHubSourceExample {
    private static final Logger LOG = LoggerFactory.getLogger(DataHubSourceExample.class);

    public static void main(String[] args) throws Exception {
        // 创建Flink执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // 配置DataHub连接
        DataHubConfig config = DataHubConfig.builder()
            .endpoint("https://dh-cn-hangzhou.aliyuncs.com")
            .accessId("your-access-id")
            .accessKey("your-access-key")
            .project("your-project")
            .topic("cdc-events")
            .consumerGroup("flink-consumer-group")
            .startPosition("LATEST")  // 从最新位置开始消费
            .maxRetries(3)
            .retryBackoff(2)
            .build();
        
        // 创建DataHub Source
        DataHubSource source = new DataHubSource(config);
        
        // 添加到DataStream
        DataStream<ChangeEvent> stream = env.addSource(source)
            .name("DataHub CDC Source")
            .uid("datahub-source");
        
        // 处理数据流（示例：打印到控制台）
        stream.map(event -> {
            LOG.info("Received event: {} - {}.{} at {}",
                event.getEventType(),
                event.getDatabase(),
                event.getTable(),
                event.getTimestamp());
            return event;
        }).name("Event Logger");
        
        // 执行作业
        env.execute("DataHub Source Example");
    }

    /**
     * 创建带有自定义配置的DataHub Source
     */
    public static DataStream<ChangeEvent> createDataHubStream(
            StreamExecutionEnvironment env,
            DataHubConfig config) {
        
        DataHubSource source = new DataHubSource(config);
        
        return env.addSource(source)
            .name("DataHub Source")
            .uid("datahub-source-" + config.getTopic());
    }

    /**
     * 创建从最早位置开始消费的DataHub Source
     */
    public static DataStream<ChangeEvent> createDataHubStreamFromEarliest(
            StreamExecutionEnvironment env,
            String endpoint,
            String accessId,
            String accessKey,
            String project,
            String topic,
            String consumerGroup) {
        
        DataHubConfig config = DataHubConfig.builder()
            .endpoint(endpoint)
            .accessId(accessId)
            .accessKey(accessKey)
            .project(project)
            .topic(topic)
            .consumerGroup(consumerGroup)
            .startPosition("EARLIEST")  // 从最早位置开始
            .build();
        
        return createDataHubStream(env, config);
    }

    /**
     * 创建从最新位置开始消费的DataHub Source
     */
    public static DataStream<ChangeEvent> createDataHubStreamFromLatest(
            StreamExecutionEnvironment env,
            String endpoint,
            String accessId,
            String accessKey,
            String project,
            String topic,
            String consumerGroup) {
        
        DataHubConfig config = DataHubConfig.builder()
            .endpoint(endpoint)
            .accessId(accessId)
            .accessKey(accessKey)
            .project(project)
            .topic(topic)
            .consumerGroup(consumerGroup)
            .startPosition("LATEST")  // 从最新位置开始
            .build();
        
        return createDataHubStream(env, config);
    }
}
