package com.realtime.pipeline.flink.processor;

import com.realtime.pipeline.model.ChangeEvent;
import com.realtime.pipeline.model.ProcessedEvent;
import org.apache.flink.api.common.functions.MapFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 事件处理器
 * 负责将ChangeEvent转换为ProcessedEvent
 * 
 * 功能:
 * - 数据转换逻辑（ChangeEvent到ProcessedEvent）
 * - 保持事件时间顺序
 * - 添加事件唯一标识符生成
 * 
 * 需求: 2.3, 9.4, 9.6
 */
public class EventProcessor implements MapFunction<ChangeEvent, ProcessedEvent> {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(EventProcessor.class);
    private static final DateTimeFormatter PARTITION_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH");

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
            throw e;
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
    private String generatePartition(long timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(timestamp),
                java.time.ZoneId.systemDefault()
        );
        return dateTime.format(PARTITION_FORMATTER);
    }
}
