package com.realtime.pipeline.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtime.pipeline.model.ChangeEvent;
import com.realtime.pipeline.model.DeadLetterRecord;
import com.realtime.pipeline.model.ProcessedEvent;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 死信队列重新处理器
 * 用于从死信队列中读取记录并重新处理
 */
@Slf4j
public class DeadLetterReprocessor {

    private final DeadLetterQueue deadLetterQueue;
    private final ObjectMapper objectMapper;

    /**
     * 构造函数
     *
     * @param deadLetterQueue 死信队列
     */
    public DeadLetterReprocessor(DeadLetterQueue deadLetterQueue) {
        this.deadLetterQueue = deadLetterQueue;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 重新处理单个记录
     *
     * @param recordId 记录ID
     * @param changeEventProcessor ChangeEvent处理器
     * @param processedEventProcessor ProcessedEvent处理器
     * @return 是否成功重新处理
     */
    public boolean reprocessRecord(
            String recordId,
            Consumer<ChangeEvent> changeEventProcessor,
            Consumer<ProcessedEvent> processedEventProcessor) {
        
        try {
            // 获取死信记录
            DeadLetterRecord record = deadLetterQueue.get(recordId)
                    .orElseThrow(() -> new IllegalArgumentException("Record not found: " + recordId));

            if (record.isReprocessed()) {
                log.warn("Record already reprocessed: {}", recordId);
                return false;
            }

            // 根据数据类型重新处理
            boolean success = false;
            if ("CHANGE_EVENT".equals(record.getDataType())) {
                ChangeEvent event = objectMapper.readValue(record.getOriginalData(), ChangeEvent.class);
                changeEventProcessor.accept(event);
                success = true;
            } else if ("PROCESSED_EVENT".equals(record.getDataType())) {
                ProcessedEvent event = objectMapper.readValue(record.getOriginalData(), ProcessedEvent.class);
                processedEventProcessor.accept(event);
                success = true;
            } else {
                log.error("Unknown data type: {}", record.getDataType());
                return false;
            }

            if (success) {
                // 标记为已重新处理
                deadLetterQueue.markAsReprocessed(recordId);
                log.info("Successfully reprocessed record: {}", recordId);
            }

            return success;

        } catch (Exception e) {
            log.error("Failed to reprocess record: {}", recordId, e);
            return false;
        }
    }

    /**
     * 重新处理所有未处理的记录
     *
     * @param changeEventProcessor ChangeEvent处理器
     * @param processedEventProcessor ProcessedEvent处理器
     * @return 重新处理结果统计
     */
    public ReprocessResult reprocessAll(
            Consumer<ChangeEvent> changeEventProcessor,
            Consumer<ProcessedEvent> processedEventProcessor) {
        
        ReprocessResult result = new ReprocessResult();
        
        try {
            List<DeadLetterRecord> unprocessedRecords = deadLetterQueue.listUnprocessed();
            result.totalRecords = unprocessedRecords.size();
            
            log.info("Starting reprocessing of {} unprocessed records", result.totalRecords);

            for (DeadLetterRecord record : unprocessedRecords) {
                boolean success = reprocessRecord(
                        record.getRecordId(),
                        changeEventProcessor,
                        processedEventProcessor);
                
                if (success) {
                    result.successCount++;
                } else {
                    result.failureCount++;
                    result.failedRecordIds.add(record.getRecordId());
                }
            }

            log.info("Reprocessing completed: {} successful, {} failed out of {} total",
                    result.successCount, result.failureCount, result.totalRecords);

        } catch (IOException e) {
            log.error("Failed to list unprocessed records", e);
            result.error = e.getMessage();
        }

        return result;
    }

    /**
     * 重新处理指定组件的失败记录
     *
     * @param component 组件名称
     * @param changeEventProcessor ChangeEvent处理器
     * @param processedEventProcessor ProcessedEvent处理器
     * @return 重新处理结果统计
     */
    public ReprocessResult reprocessByComponent(
            String component,
            Consumer<ChangeEvent> changeEventProcessor,
            Consumer<ProcessedEvent> processedEventProcessor) {
        
        ReprocessResult result = new ReprocessResult();
        
        try {
            List<DeadLetterRecord> records = deadLetterQueue.listUnprocessed().stream()
                    .filter(record -> component.equals(record.getComponent()))
                    .collect(java.util.stream.Collectors.toList());
            
            result.totalRecords = records.size();
            
            log.info("Starting reprocessing of {} records from component: {}", result.totalRecords, component);

            for (DeadLetterRecord record : records) {
                boolean success = reprocessRecord(
                        record.getRecordId(),
                        changeEventProcessor,
                        processedEventProcessor);
                
                if (success) {
                    result.successCount++;
                } else {
                    result.failureCount++;
                    result.failedRecordIds.add(record.getRecordId());
                }
            }

            log.info("Reprocessing completed for component {}: {} successful, {} failed out of {} total",
                    component, result.successCount, result.failureCount, result.totalRecords);

        } catch (IOException e) {
            log.error("Failed to list unprocessed records for component: {}", component, e);
            result.error = e.getMessage();
        }

        return result;
    }

    /**
     * 重新处理结果统计
     */
    public static class ReprocessResult {
        public int totalRecords = 0;
        public int successCount = 0;
        public int failureCount = 0;
        public List<String> failedRecordIds = new ArrayList<>();
        public String error = null;

        public boolean isSuccess() {
            return error == null && failureCount == 0;
        }

        @Override
        public String toString() {
            return String.format("ReprocessResult{total=%d, success=%d, failure=%d, error=%s}",
                    totalRecords, successCount, failureCount, error);
        }
    }
}
