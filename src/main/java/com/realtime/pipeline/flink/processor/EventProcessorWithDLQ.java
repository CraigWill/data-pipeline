package com.realtime.pipeline.flink.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtime.pipeline.error.DeadLetterQueue;
import com.realtime.pipeline.model.ChangeEvent;
import com.realtime.pipeline.model.DeadLetterRecord;
import com.realtime.pipeline.model.ProcessedEvent;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 带死信队列的事件处理器
 * 负责将ChangeEvent转换为ProcessedEvent，并在处理失败时发送到死信队列
 * 
 * 功能:
 * - 数据转换逻辑（ChangeEvent到ProcessedEvent）
 * - 保持事件时间顺序
 * - 添加事件唯一标识符生成
 * - 捕获处理异常并发送到死信队列
 * 
 * 需求: 2.3, 2.7, 9.4, 9.6
 */
public class EventProcessorWithDLQ extends RichMapFunction<ChangeEvent, ProcessedEvent> {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(EventProcessorWithDLQ.class);
    private static final DateTimeFormatter PARTITION_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private final String dlqPath;
    private transient DeadLetterQueue deadLetterQueue;
    private transient ObjectMapper objectMapper;

    /**
     * 构造函数
     * @param dlqPath 死信队列存储路径
     */
    public EventProcessorWithDLQ(String dlqPath) {
        this.dlqPath = dlqPath;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        // 初始化死信队列（每个TaskManager实例一个）
        this.deadLetterQueue = createDeadLetterQueue(dlqPath);
        this.objectMapper = new ObjectMapper();
        logger.info("EventProcessorWithDLQ initialized with DLQ path: {}", dlqPath);
    }

    @Override
    public void close() throws Exception {
        super.close();
        if (deadLetterQueue != null) {
            deadLetterQueue.close();
        }
    }

    @Override
    public ProcessedEvent map(ChangeEvent changeEvent) throws Exception {
        if (changeEvent == null) {
            logger.warn("Received null ChangeEvent, skipping processing");
            return null;
        }

        try {
            // 记录处理时间
            long processTime = System.currentTimeMillis();

            // 生成唯一标识符（如果原事件没有eventId，则生成新的）
            String eventId = changeEvent.getEventId();
            if (eventId == null || eventId.isEmpty()) {
                eventId = generateEventId(changeEvent);
                logger.debug("Generated new eventId: {} for event from {}.{}", 
                    eventId, changeEvent.getDatabase(), changeEvent.getTable());
            }

            // 生成分区信息（基于时间戳）
            String partition = generatePartition(changeEvent.getTimestamp());

            // 获取数据内容（根据事件类型）
            // DELETE事件使用before数据，INSERT和UPDATE使用after数据
            var data = changeEvent.getData();

            // 构建ProcessedEvent
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                    .eventType(changeEvent.getEventType())
                    .database(changeEvent.getDatabase())
                    .table(changeEvent.getTable())
                    .timestamp(changeEvent.getTimestamp())  // 保持原始事件时间戳，用于维持时间顺序
                    .processTime(processTime)
                    .data(data)
                    .partition(partition)
                    .eventId(eventId)
                    .build();

            logger.debug("Processed event: {} from {}.{}, latency: {}ms",
                    eventId, changeEvent.getDatabase(), changeEvent.getTable(),
                    processedEvent.getProcessingLatency());

            return processedEvent;

        } catch (Exception e) {
            logger.error("Error processing ChangeEvent from {}.{}: {}",
                    changeEvent.getDatabase(), changeEvent.getTable(), e.getMessage(), e);
            
            // 发送到死信队列
            sendToDeadLetterQueue(changeEvent, e, 0);
            
            // 返回null以跳过此记录，不影响其他记录的处理
            return null;
        }
    }

    /**
     * 将失败的记录发送到死信队列
     * 
     * @param changeEvent 原始变更事件
     * @param exception 异常信息
     * @param retryCount 重试次数
     */
    private void sendToDeadLetterQueue(ChangeEvent changeEvent, Exception exception, int retryCount) {
        try {
            // 生成死信记录ID
            String recordId = "dlq_" + UUID.randomUUID().toString().replace("-", "");
            
            // 序列化原始数据
            String originalData = objectMapper.writeValueAsString(changeEvent);
            
            // 获取堆栈跟踪
            String stackTrace = getStackTrace(exception);
            
            // 构建上下文信息
            Map<String, String> context = new HashMap<>();
            context.put("database", changeEvent.getDatabase());
            context.put("table", changeEvent.getTable());
            context.put("eventType", changeEvent.getEventType());
            context.put("timestamp", String.valueOf(changeEvent.getTimestamp()));
            
            // 创建死信记录
            DeadLetterRecord dlqRecord = DeadLetterRecord.builder()
                    .recordId(recordId)
                    .eventId(changeEvent.getEventId())
                    .failureTimestamp(System.currentTimeMillis())
                    .failureReason(exception.getMessage())
                    .stackTrace(stackTrace)
                    .component("EventProcessor")
                    .operationType("PROCESS")
                    .retryCount(retryCount)
                    .originalData(originalData)
                    .dataType("CHANGE_EVENT")
                    .context(context)
                    .reprocessed(false)
                    .reprocessTimestamp(null)
                    .build();
            
            // 添加到死信队列
            deadLetterQueue.add(dlqRecord);
            
            logger.info("Sent failed record to dead letter queue: recordId={}, eventId={}, reason={}",
                    recordId, changeEvent.getEventId(), exception.getMessage());
            
        } catch (Exception dlqException) {
            // 如果发送到死信队列也失败，记录错误但不抛出异常
            logger.error("Failed to send record to dead letter queue: {}", dlqException.getMessage(), dlqException);
        }
    }

    /**
     * 生成事件唯一标识符
     * 格式: {database}_{table}_{timestamp}_{uuid}
     * 
     * @param changeEvent 变更事件
     * @return 唯一标识符
     */
    private String generateEventId(ChangeEvent changeEvent) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return String.format("%s_%s_%d_%s",
                changeEvent.getDatabase(),
                changeEvent.getTable(),
                changeEvent.getTimestamp(),
                uuid.substring(0, 8));  // 使用UUID的前8位
    }

    /**
     * 生成分区信息
     * 格式: yyyyMMddHH (年月日小时)
     * 
     * @param timestamp 时间戳（毫秒）
     * @return 分区字符串
     */
    protected String generatePartition(long timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(timestamp),
                java.time.ZoneId.systemDefault()
        );
        return dateTime.format(PARTITION_FORMATTER);
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
