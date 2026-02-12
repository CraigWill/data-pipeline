package com.realtime.pipeline.flink.sink;

import com.realtime.pipeline.config.OutputConfig;
import com.realtime.pipeline.model.ProcessedEvent;
import com.realtime.pipeline.util.RetryUtil;
import org.apache.flink.api.common.serialization.BulkWriter;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.functions.sink.filesystem.BucketAssigner;
import org.apache.flink.streaming.api.functions.sink.filesystem.OutputFileConfig;
import org.apache.flink.streaming.api.functions.sink.filesystem.RollingPolicy;
import org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink;
import org.apache.flink.streaming.api.functions.sink.filesystem.bucketassigners.SimpleVersionedStringSerializer;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;

/**
 * 文件Sink基类
 * 提供文件滚动策略、文件命名策略和写入重试机制
 * 
 * 实现需求:
 * - 3.4: 文件大小达到1GB时创建新文件
 * - 3.5: 文件时间跨度达到1小时时创建新文件
 * - 3.6: 文件名包含时间戳和分区信息
 * - 3.7: 文件写入失败时重试最多3次
 */
public abstract class AbstractFileSink implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(AbstractFileSink.class);

    protected final OutputConfig config;

    /**
     * 构造函数
     * @param config 输出配置
     */
    public AbstractFileSink(OutputConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("OutputConfig cannot be null");
        }
        config.validate();
        this.config = config;
    }

    /**
     * 创建文件滚动策略
     * 实现需求 3.4 和 3.5: 基于大小和时间的文件滚动
     * 
     * @return 滚动策略
     */
    protected RollingPolicy<ProcessedEvent, String> createRollingPolicy() {
        return DefaultRollingPolicy
                .<ProcessedEvent, String>builder()
                // 需求 3.4: 文件大小达到配置的阈值时滚动（默认1GB）
                .withMaxPartSize(config.getRollingSizeBytes())
                // 需求 3.5: 文件时间跨度达到配置的间隔时滚动（默认1小时）
                .withRolloverInterval(Duration.ofMillis(config.getRollingIntervalMs()))
                // 设置不活跃超时，避免文件长时间打开
                .withInactivityInterval(Duration.ofMinutes(5))
                .build();
    }

    /**
     * 创建桶分配器
     * 实现需求 3.6: 文件名包含时间戳和分区信息
     * 
     * @return 桶分配器
     */
    protected BucketAssigner<ProcessedEvent, String> createBucketAssigner() {
        return new TimestampPartitionBucketAssigner();
    }

    /**
     * 创建输出文件配置
     * 实现需求 3.6: 文件命名策略
     * 
     * @param format 文件格式后缀
     * @return 输出文件配置
     */
    protected OutputFileConfig createOutputFileConfig(String format) {
        return OutputFileConfig
                .builder()
                .withPartPrefix("part")
                .withPartSuffix("." + format)
                .build();
    }

    /**
     * 执行带重试的写入操作
     * 实现需求 3.7: 写入失败时重试最多3次
     * 
     * @param operation 写入操作
     * @param operationName 操作名称（用于日志）
     * @throws Exception 如果所有重试都失败
     */
    protected void executeWithRetry(Runnable operation, String operationName) throws Exception {
        RetryUtil.executeWithRetry(
                operation,
                config.getMaxRetries(),
                config.getRetryBackoff(),
                operationName
        );
    }

    /**
     * 获取输出路径
     * @return 输出路径
     */
    protected Path getOutputPath() {
        return new Path(config.getPath());
    }

    /**
     * 创建StreamingFileSink（子类实现具体格式）
     * @return StreamingFileSink实例
     */
    public abstract StreamingFileSink<ProcessedEvent> createSink();

    /**
     * 时间戳分区桶分配器
     * 根据事件的数据库、表名和时间戳分配到不同的桶（目录）
     * 
     * 桶路径格式: {database}/{table}/dt={yyyyMMddHH}/
     * 例如: mydb/users/dt=2025012812/
     */
    protected static class TimestampPartitionBucketAssigner implements BucketAssigner<ProcessedEvent, String> {
        private static final long serialVersionUID = 1L;
        private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMddHH");

        @Override
        public String getBucketId(ProcessedEvent event, Context context) {
            // 使用事件的原始时间戳进行分区
            String datePartition = DATE_FORMAT.format(new Date(event.getTimestamp()));
            
            // 构建分区路径: database/table/dt=yyyyMMddHH
            return String.format("%s/%s/dt=%s",
                    sanitizePath(event.getDatabase()),
                    sanitizePath(event.getTable()),
                    datePartition);
        }

        @Override
        public SimpleVersionedStringSerializer getSerializer() {
            return SimpleVersionedStringSerializer.INSTANCE;
        }

        /**
         * 清理路径中的非法字符
         */
        private String sanitizePath(String path) {
            if (path == null) {
                return "unknown";
            }
            // 替换路径分隔符和其他非法字符
            return path.replaceAll("[/\\\\:*?\"<>|]", "_");
        }
    }

    /**
     * 带重试的BulkWriter包装器
     * 实现需求 3.7: 写入失败时自动重试
     */
    protected static class RetryableBulkWriter<T> implements BulkWriter<T> {
        private final BulkWriter<T> delegate;
        private final int maxRetries;
        private final int retryBackoffSeconds;
        private static final Logger logger = LoggerFactory.getLogger(RetryableBulkWriter.class);

        public RetryableBulkWriter(BulkWriter<T> delegate, int maxRetries, int retryBackoffSeconds) {
            this.delegate = delegate;
            this.maxRetries = maxRetries;
            this.retryBackoffSeconds = retryBackoffSeconds;
        }

        @Override
        public void addElement(T element) throws IOException {
            try {
                RetryUtil.executeWithRetry(
                        () -> {
                            try {
                                delegate.addElement(element);
                            } catch (IOException e) {
                                throw new RuntimeException("Failed to write element", e);
                            }
                        },
                        maxRetries,
                        retryBackoffSeconds,
                        "Write element to file"
                );
            } catch (Exception e) {
                if (e instanceof IOException) {
                    throw (IOException) e;
                } else if (e.getCause() instanceof IOException) {
                    throw (IOException) e.getCause();
                } else {
                    throw new IOException("Failed to write element after retries", e);
                }
            }
        }

        @Override
        public void flush() throws IOException {
            try {
                RetryUtil.executeWithRetry(
                        () -> {
                            try {
                                delegate.flush();
                            } catch (IOException e) {
                                throw new RuntimeException("Failed to flush", e);
                            }
                        },
                        maxRetries,
                        retryBackoffSeconds,
                        "Flush file"
                );
            } catch (Exception e) {
                if (e instanceof IOException) {
                    throw (IOException) e;
                } else if (e.getCause() instanceof IOException) {
                    throw (IOException) e.getCause();
                } else {
                    throw new IOException("Failed to flush after retries", e);
                }
            }
        }

        @Override
        public void finish() throws IOException {
            try {
                RetryUtil.executeWithRetry(
                        () -> {
                            try {
                                delegate.finish();
                            } catch (IOException e) {
                                throw new RuntimeException("Failed to finish", e);
                            }
                        },
                        maxRetries,
                        retryBackoffSeconds,
                        "Finish file"
                );
            } catch (Exception e) {
                if (e instanceof IOException) {
                    throw (IOException) e;
                } else if (e.getCause() instanceof IOException) {
                    throw (IOException) e.getCause();
                } else {
                    throw new IOException("Failed to finish after retries", e);
                }
            }
        }
    }
}
