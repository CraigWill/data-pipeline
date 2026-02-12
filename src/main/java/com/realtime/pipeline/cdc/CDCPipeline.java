package com.realtime.pipeline.cdc;

import com.realtime.pipeline.config.DatabaseConfig;
import com.realtime.pipeline.model.ChangeEvent;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

/**
 * CDC数据管道
 * 整合CDC采集器和事件转换器，提供完整的CDC数据流
 * 
 * 使用示例:
 * <pre>
 * DatabaseConfig dbConfig = ...;
 * StreamExecutionEnvironment env = ...;
 * 
 * CDCPipeline pipeline = new CDCPipeline(dbConfig);
 * DataStream<ChangeEvent> changeEvents = pipeline.createChangeEventStream(env);
 * </pre>
 */
public class CDCPipeline implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(CDCPipeline.class);

    private final CDCCollector collector;
    private final CDCEventConverter converter;

    /**
     * 构造函数
     * @param databaseConfig 数据库配置
     */
    public CDCPipeline(DatabaseConfig databaseConfig) {
        this.collector = new CDCCollector(databaseConfig);
        this.converter = new CDCEventConverter();
    }

    /**
     * 创建变更事件数据流
     * 
     * @param env Flink流执行环境
     * @return 变更事件数据流
     */
    public DataStream<ChangeEvent> createChangeEventStream(StreamExecutionEnvironment env) {
        logger.info("Creating CDC change event stream");

        // 创建CDC原始数据流（JSON格式）
        DataStream<String> rawStream = collector.createSource(env);

        // 转换为ChangeEvent对象
        DataStream<ChangeEvent> changeEventStream = rawStream
            .map(converter)
            .name("CDC Event Converter");

        logger.info("CDC change event stream created");
        return changeEventStream;
    }

    /**
     * 获取CDC采集器
     * 
     * @return CDC采集器
     */
    public CDCCollector getCollector() {
        return collector;
    }

    /**
     * 停止CDC管道
     */
    public void stop() {
        logger.info("Stopping CDC pipeline");
        collector.stop();
    }
}
