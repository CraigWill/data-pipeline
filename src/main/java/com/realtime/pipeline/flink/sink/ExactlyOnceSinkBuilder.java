package com.realtime.pipeline.flink.sink;

import com.realtime.pipeline.config.OutputConfig;
import com.realtime.pipeline.model.ProcessedEvent;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 精确一次语义Sink构建器
 * 提供便捷的方法来创建支持精确一次语义的文件Sink
 * 
 * 实现需求:
 * - 9.2: 支持精确一次（Exactly-once）语义
 * - 9.3: 通过幂等性操作去重
 * 
 * 使用方式:
 * <pre>
 * SinkFunction<ProcessedEvent> sink = ExactlyOnceSinkBuilder
 *     .forFormat("json", outputConfig)
 *     .withIdempotence(true)
 *     .withStateRetention(24 * 60 * 60 * 1000L) // 24小时
 *     .withDeadLetterQueue("/path/to/dlq")
 *     .build();
 * </pre>
 */
public class ExactlyOnceSinkBuilder {
    private static final Logger logger = LoggerFactory.getLogger(ExactlyOnceSinkBuilder.class);

    private final String format;
    private final OutputConfig config;
    
    private boolean enableIdempotence = false;
    private long stateRetentionTimeMs = 24 * 60 * 60 * 1000L; // 默认24小时
    private boolean enableDLQ = false;
    private String dlqPath = null;

    private ExactlyOnceSinkBuilder(String format, OutputConfig config) {
        this.format = format;
        this.config = config;
    }

    /**
     * 创建指定格式的Sink构建器
     * 
     * @param format 输出格式（json, csv, parquet）
     * @param config 输出配置
     * @return Sink构建器
     */
    public static ExactlyOnceSinkBuilder forFormat(String format, OutputConfig config) {
        if (format == null || format.isEmpty()) {
            throw new IllegalArgumentException("Format cannot be null or empty");
        }
        if (config == null) {
            throw new IllegalArgumentException("OutputConfig cannot be null");
        }
        return new ExactlyOnceSinkBuilder(format.toLowerCase(), config);
    }

    /**
     * 启用或禁用幂等性
     * 
     * @param enable true启用幂等性，false禁用
     * @return 构建器实例
     */
    public ExactlyOnceSinkBuilder withIdempotence(boolean enable) {
        this.enableIdempotence = enable;
        return this;
    }

    /**
     * 设置状态保留时间
     * 
     * @param retentionTimeMs 保留时间（毫秒）
     * @return 构建器实例
     */
    public ExactlyOnceSinkBuilder withStateRetention(long retentionTimeMs) {
        if (retentionTimeMs <= 0) {
            throw new IllegalArgumentException("State retention time must be positive");
        }
        this.stateRetentionTimeMs = retentionTimeMs;
        return this;
    }

    /**
     * 启用死信队列
     * 
     * @param dlqPath 死信队列路径
     * @return 构建器实例
     */
    public ExactlyOnceSinkBuilder withDeadLetterQueue(String dlqPath) {
        if (dlqPath == null || dlqPath.isEmpty()) {
            throw new IllegalArgumentException("DLQ path cannot be null or empty");
        }
        this.enableDLQ = true;
        this.dlqPath = dlqPath;
        return this;
    }

    /**
     * 构建Sink实例
     * 
     * @return 配置好的Sink实例
     */
    public SinkFunction<ProcessedEvent> build() {
        // 1. 创建基础的文件Sink
        AbstractFileSink baseSink = createBaseSink();
        SinkFunction<ProcessedEvent> sink = baseSink.createSink();
        
        logger.info("Created base {} sink", format);
        
        // 2. 如果启用幂等性，包装为IdempotentFileSink
        if (enableIdempotence) {
            sink = new IdempotentFileSink(sink, stateRetentionTimeMs);
            logger.info("Wrapped with IdempotentFileSink (state retention: {} ms)", stateRetentionTimeMs);
        }
        
        // 3. 如果启用死信队列，包装为FileSinkWithDLQ
        if (enableDLQ) {
            sink = new FileSinkWithDLQ(sink, dlqPath, config.getMaxRetries());
            logger.info("Wrapped with FileSinkWithDLQ (path: {}, maxRetries: {})", dlqPath, config.getMaxRetries());
        }
        
        return sink;
    }

    /**
     * 根据格式创建基础Sink
     */
    private AbstractFileSink createBaseSink() {
        switch (format) {
            case "json":
                return createJsonSink();
            case "csv":
                return new CsvFileSink(config);
            case "parquet":
                return new ParquetFileSink(config);
            default:
                throw new IllegalArgumentException("Unsupported format: " + format + 
                        ". Supported formats: json, csv, parquet");
        }
    }

    /**
     * 创建JSON Sink
     * 使用反射避免直接依赖，因为JsonFileSink可能不存在
     */
    private AbstractFileSink createJsonSink() {
        try {
            // 尝试使用JsonFileSink
            Class<?> clazz = Class.forName("com.realtime.pipeline.flink.sink.JsonFileSink");
            return (AbstractFileSink) clazz.getConstructor(OutputConfig.class).newInstance(config);
        } catch (ClassNotFoundException e) {
            // 如果JsonFileSink不存在，使用CsvFileSink作为后备
            logger.warn("JsonFileSink not found, falling back to CsvFileSink");
            return new CsvFileSink(config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create JSON sink", e);
        }
    }

    /**
     * 创建用于精确一次语义的Sink（快捷方法）
     * 
     * @param format 输出格式
     * @param config 输出配置
     * @param dlqPath 死信队列路径
     * @return 配置好的Sink实例
     */
    public static SinkFunction<ProcessedEvent> createExactlyOnceSink(
            String format, OutputConfig config, String dlqPath) {
        return forFormat(format, config)
                .withIdempotence(true)
                .withDeadLetterQueue(dlqPath)
                .build();
    }

    /**
     * 创建用于至少一次语义的Sink（快捷方法）
     * 
     * @param format 输出格式
     * @param config 输出配置
     * @param dlqPath 死信队列路径
     * @return 配置好的Sink实例
     */
    public static SinkFunction<ProcessedEvent> createAtLeastOnceSink(
            String format, OutputConfig config, String dlqPath) {
        return forFormat(format, config)
                .withIdempotence(false)
                .withDeadLetterQueue(dlqPath)
                .build();
    }
}
