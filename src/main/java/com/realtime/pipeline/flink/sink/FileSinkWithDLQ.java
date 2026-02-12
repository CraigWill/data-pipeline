package com.realtime.pipeline.flink.sink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtime.pipeline.error.DeadLetterQueue;
import com.realtime.pipeline.model.DeadLetterRecord;
import com.realtime.pipeline.model.ProcessedEvent;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 带死信队列的文件Sink包装器
 * 捕获文件写入异常并将失败记录发送到死信队列
 * 
 * 功能:
 * - 包装现有的FileSink实现
 * - 捕获写入异常
 * - 将失败记录发送到死信队列
 * - 不影响其他记录的处理
 * 
 * 需求: 3.7, 3.8
 */
public class FileSinkWithDLQ extends RichSinkFunction<ProcessedEvent> {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(FileSinkWithDLQ.class);

    private final SinkFunction<ProcessedEvent> delegateSink;
    private final String dlqPath;
    private final int maxRetries;
    
    private transient DeadLetterQueue deadLetterQueue;
    private transient ObjectMapper objectMapper;

    /**
     * 构造函数
     * 
     * @param delegateSink 被包装的Sink实现
     * @param dlqPath 死信队列存储路径
     * @param maxRetries 最大重试次数
     */
    public FileSinkWithDLQ(SinkFunction<ProcessedEvent> delegateSink, String dlqPath, int maxRetries) {
        this.delegateSink = delegateSink;
        this.dlqPath = dlqPath;
        this.maxRetries = maxRetries;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        
        // 初始化死信队列
        this.deadLetterQueue = createDeadLetterQueue(dlqPath);
        this.objectMapper = new ObjectMapper();
        
        // 如果delegate sink是RichSinkFunction，也需要初始化它
        if (delegateSink instanceof RichSinkFunction) {
            RichSinkFunction<ProcessedEvent> richSink = (RichSinkFunction<ProcessedEvent>) delegateSink;
            richSink.setRuntimeContext(getRuntimeContext());
            richSink.open(parameters);
        }
        
        logger.info("FileSinkWithDLQ initialized with DLQ path: {}, maxRetries: {}", dlqPath, maxRetries);
    }

    @Override
    public void close() throws Exception {
        super.close();
        
        // 关闭delegate sink
        if (delegateSink instanceof RichSinkFunction) {
            ((RichSinkFunction<ProcessedEvent>) delegateSink).close();
        }
        
        // 关闭死信队列
        if (deadLetterQueue != null) {
            deadLetterQueue.close();
        }
    }

    @Override
    public void invoke(ProcessedEvent value, Context context) throws Exception {
        if (value == null) {
            logger.warn("Received null ProcessedEvent, skipping");
            return;
        }

        int retryCount = 0;
        Exception lastException = null;

        // 尝试写入，最多重试maxRetries次
        while (retryCount <= maxRetries) {
            try {
                // 调用delegate sink进行实际写入
                delegateSink.invoke(value, context);
                
                // 写入成功，返回
                if (retryCount > 0) {
                    logger.info("Successfully wrote event {} after {} retries", 
                            value.getEventId(), retryCount);
                }
                return;
                
            } catch (Exception e) {
                lastException = e;
                retryCount++;
                
                if (retryCount <= maxRetries) {
                    // 还有重试机会，等待后重试
                    logger.warn("Failed to write event {} (attempt {}/{}): {}", 
                            value.getEventId(), retryCount, maxRetries + 1, e.getMessage());
                    
                    // 指数退避：2秒 * 2^(retryCount-1)
                    long backoffMs = 2000L * (1L << (retryCount - 1));
                    Thread.sleep(Math.min(backoffMs, 30000)); // 最多等待30秒
                } else {
                    // 所有重试都失败
                    logger.error("Failed to write event {} after {} attempts: {}", 
                            value.getEventId(), retryCount, e.getMessage(), e);
                }
            }
        }

        // 所有重试都失败，发送到死信队列
        if (lastException != null) {
            sendToDeadLetterQueue(value, lastException, retryCount - 1);
        }
    }

    /**
     * 将失败的记录发送到死信队列
     * 
     * @param processedEvent 处理后的事件
     * @param exception 异常信息
     * @param retryCount 重试次数
     */
    private void sendToDeadLetterQueue(ProcessedEvent processedEvent, Exception exception, int retryCount) {
        try {
            // 生成死信记录ID
            String recordId = "dlq_" + UUID.randomUUID().toString().replace("-", "");
            
            // 序列化原始数据
            String originalData = objectMapper.writeValueAsString(processedEvent);
            
            // 获取堆栈跟踪
            String stackTrace = getStackTrace(exception);
            
            // 构建上下文信息
            Map<String, String> context = new HashMap<>();
            context.put("database", processedEvent.getDatabase());
            context.put("table", processedEvent.getTable());
            context.put("eventType", processedEvent.getEventType());
            context.put("timestamp", String.valueOf(processedEvent.getTimestamp()));
            context.put("partition", processedEvent.getPartition());
            context.put("processingLatency", String.valueOf(processedEvent.getProcessingLatency()));
            
            // 创建死信记录
            DeadLetterRecord dlqRecord = DeadLetterRecord.builder()
                    .recordId(recordId)
                    .eventId(processedEvent.getEventId())
                    .failureTimestamp(System.currentTimeMillis())
                    .failureReason(exception.getMessage())
                    .stackTrace(stackTrace)
                    .component("FileSink")
                    .operationType("WRITE")
                    .retryCount(retryCount)
                    .originalData(originalData)
                    .dataType("PROCESSED_EVENT")
                    .context(context)
                    .reprocessed(false)
                    .reprocessTimestamp(null)
                    .build();
            
            // 添加到死信队列
            deadLetterQueue.add(dlqRecord);
            
            logger.info("Sent failed record to dead letter queue: recordId={}, eventId={}, reason={}",
                    recordId, processedEvent.getEventId(), exception.getMessage());
            
        } catch (Exception dlqException) {
            // 如果发送到死信队列也失败，记录错误但不抛出异常
            logger.error("Failed to send record to dead letter queue: {}", dlqException.getMessage(), dlqException);
        }
    }

    /**
     * 获取异常的堆栈跟踪字符串
     */
    private String getStackTrace(Exception exception) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exception.printStackTrace(pw);
        return sw.toString();
    }

    /**
     * 创建死信队列实例（可被子类覆盖以支持不同的实现）
     */
    protected DeadLetterQueue createDeadLetterQueue(String path) throws Exception {
        // 使用反射创建FileBasedDeadLetterQueue，避免直接依赖
        Class<?> clazz = Class.forName("com.realtime.pipeline.error.FileBasedDeadLetterQueue");
        return (DeadLetterQueue) clazz.getConstructor(String.class).newInstance(path);
    }
}
