package com.realtime.pipeline.consistency;

import com.realtime.pipeline.model.ChangeEvent;
import com.realtime.pipeline.model.ProcessedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据一致性验证器
 * 检测数据不一致并记录详细的错误日志
 * 
 * 验证需求: 9.5
 */
public class ConsistencyValidator implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(ConsistencyValidator.class);

    /**
     * 验证ChangeEvent的数据一致性
     * 
     * @param event 变更事件
     * @return 验证结果
     */
    public ValidationResult validateChangeEvent(ChangeEvent event) {
        List<String> errors = new ArrayList<>();

        if (event == null) {
            errors.add("Event is null");
            return new ValidationResult(false, errors);
        }

        // 验证必填字段
        if (event.getEventType() == null || event.getEventType().isEmpty()) {
            errors.add("Event type is null or empty");
        }

        if (event.getDatabase() == null || event.getDatabase().isEmpty()) {
            errors.add("Database name is null or empty");
        }

        if (event.getTable() == null || event.getTable().isEmpty()) {
            errors.add("Table name is null or empty");
        }

        if (event.getTimestamp() <= 0) {
            errors.add("Timestamp is invalid: " + event.getTimestamp());
        }

        if (event.getEventId() == null || event.getEventId().isEmpty()) {
            errors.add("Event ID is null or empty");
        }

        // 验证事件类型的有效性
        if (event.getEventType() != null) {
            if (!isValidEventType(event.getEventType())) {
                errors.add("Invalid event type: " + event.getEventType());
            }
        }

        // 验证事件类型与数据的一致性
        if (event.getEventType() != null) {
            switch (event.getEventType()) {
                case "INSERT":
                    if (event.getAfter() == null || event.getAfter().isEmpty()) {
                        errors.add("INSERT event must have 'after' data");
                    }
                    if (event.getBefore() != null && !event.getBefore().isEmpty()) {
                        errors.add("INSERT event should not have 'before' data");
                    }
                    break;

                case "UPDATE":
                    if (event.getAfter() == null || event.getAfter().isEmpty()) {
                        errors.add("UPDATE event must have 'after' data");
                    }
                    if (event.getBefore() == null || event.getBefore().isEmpty()) {
                        errors.add("UPDATE event must have 'before' data");
                    }
                    break;

                case "DELETE":
                    if (event.getBefore() == null || event.getBefore().isEmpty()) {
                        errors.add("DELETE event must have 'before' data");
                    }
                    if (event.getAfter() != null && !event.getAfter().isEmpty()) {
                        errors.add("DELETE event should not have 'after' data");
                    }
                    break;
            }
        }

        // 验证主键字段
        if (event.getPrimaryKeys() == null || event.getPrimaryKeys().isEmpty()) {
            errors.add("Primary keys list is null or empty");
        } else {
            // 验证主键字段在数据中存在
            Map<String, Object> data = event.getData();
            if (data != null) {
                for (String pk : event.getPrimaryKeys()) {
                    if (!data.containsKey(pk)) {
                        errors.add("Primary key '" + pk + "' not found in data");
                    } else if (data.get(pk) == null) {
                        errors.add("Primary key '" + pk + "' has null value");
                    }
                }
            }
        }

        // 记录验证结果
        if (!errors.isEmpty()) {
            logInconsistency(event, errors);
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    /**
     * 验证ProcessedEvent的数据一致性
     * 
     * @param event 处理后事件
     * @return 验证结果
     */
    public ValidationResult validateProcessedEvent(ProcessedEvent event) {
        List<String> errors = new ArrayList<>();

        if (event == null) {
            errors.add("Event is null");
            return new ValidationResult(false, errors);
        }

        // 验证必填字段
        if (event.getEventType() == null || event.getEventType().isEmpty()) {
            errors.add("Event type is null or empty");
        }

        if (event.getDatabase() == null || event.getDatabase().isEmpty()) {
            errors.add("Database name is null or empty");
        }

        if (event.getTable() == null || event.getTable().isEmpty()) {
            errors.add("Table name is null or empty");
        }

        if (event.getTimestamp() <= 0) {
            errors.add("Timestamp is invalid: " + event.getTimestamp());
        }

        if (event.getProcessTime() <= 0) {
            errors.add("Process time is invalid: " + event.getProcessTime());
        }

        if (event.getEventId() == null || event.getEventId().isEmpty()) {
            errors.add("Event ID is null or empty");
        }

        if (event.getData() == null || event.getData().isEmpty()) {
            errors.add("Data is null or empty");
        }

        // 验证时间顺序
        if (event.getProcessTime() < event.getTimestamp()) {
            errors.add("Process time (" + event.getProcessTime() + 
                      ") is before event timestamp (" + event.getTimestamp() + ")");
        }

        // 验证事件类型的有效性
        if (event.getEventType() != null && !isValidEventType(event.getEventType())) {
            errors.add("Invalid event type: " + event.getEventType());
        }

        // 记录验证结果
        if (!errors.isEmpty()) {
            logInconsistency(event, errors);
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    /**
     * 验证ChangeEvent和ProcessedEvent之间的一致性
     * 
     * @param changeEvent 原始变更事件
     * @param processedEvent 处理后事件
     * @return 验证结果
     */
    public ValidationResult validateEventConsistency(ChangeEvent changeEvent, ProcessedEvent processedEvent) {
        List<String> errors = new ArrayList<>();

        if (changeEvent == null) {
            errors.add("ChangeEvent is null");
        }

        if (processedEvent == null) {
            errors.add("ProcessedEvent is null");
        }

        if (changeEvent == null || processedEvent == null) {
            return new ValidationResult(false, errors);
        }

        // 验证事件ID一致性
        if (!changeEvent.getEventId().equals(processedEvent.getEventId())) {
            errors.add("Event ID mismatch: change=" + changeEvent.getEventId() + 
                      ", processed=" + processedEvent.getEventId());
        }

        // 验证事件类型一致性
        if (!changeEvent.getEventType().equals(processedEvent.getEventType())) {
            errors.add("Event type mismatch: change=" + changeEvent.getEventType() + 
                      ", processed=" + processedEvent.getEventType());
        }

        // 验证数据库和表名一致性
        if (!changeEvent.getDatabase().equals(processedEvent.getDatabase())) {
            errors.add("Database mismatch: change=" + changeEvent.getDatabase() + 
                      ", processed=" + processedEvent.getDatabase());
        }

        if (!changeEvent.getTable().equals(processedEvent.getTable())) {
            errors.add("Table mismatch: change=" + changeEvent.getTable() + 
                      ", processed=" + processedEvent.getTable());
        }

        // 验证时间戳一致性
        if (changeEvent.getTimestamp() != processedEvent.getTimestamp()) {
            errors.add("Timestamp mismatch: change=" + changeEvent.getTimestamp() + 
                      ", processed=" + processedEvent.getTimestamp());
        }

        // 验证数据内容一致性（基本检查）
        Map<String, Object> changeData = changeEvent.getData();
        Map<String, Object> processedData = processedEvent.getData();

        if (changeData != null && processedData != null) {
            // 验证主键值一致性
            if (changeEvent.getPrimaryKeys() != null) {
                for (String pk : changeEvent.getPrimaryKeys()) {
                    Object changeValue = changeData.get(pk);
                    Object processedValue = processedData.get(pk);

                    if (changeValue == null && processedValue != null) {
                        errors.add("Primary key '" + pk + "' value mismatch: change=null, processed=" + processedValue);
                    } else if (changeValue != null && !changeValue.equals(processedValue)) {
                        errors.add("Primary key '" + pk + "' value mismatch: change=" + changeValue + 
                                  ", processed=" + processedValue);
                    }
                }
            }
        }

        // 记录验证结果
        if (!errors.isEmpty()) {
            logInconsistency(changeEvent, processedEvent, errors);
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    /**
     * 验证事件类型是否有效
     */
    private boolean isValidEventType(String eventType) {
        return "INSERT".equals(eventType) || 
               "UPDATE".equals(eventType) || 
               "DELETE".equals(eventType);
    }

    /**
     * 记录ChangeEvent的数据不一致日志
     */
    private void logInconsistency(ChangeEvent event, List<String> errors) {
        logger.error("Data inconsistency detected in ChangeEvent: eventId={}, eventType={}, database={}, table={}, errors={}",
                event != null ? event.getEventId() : "null",
                event != null ? event.getEventType() : "null",
                event != null ? event.getDatabase() : "null",
                event != null ? event.getTable() : "null",
                errors);

        if (event != null) {
            logger.debug("ChangeEvent details: timestamp={}, primaryKeys={}, before={}, after={}",
                    event.getTimestamp(),
                    event.getPrimaryKeys(),
                    event.getBefore(),
                    event.getAfter());
        }
    }

    /**
     * 记录ProcessedEvent的数据不一致日志
     */
    private void logInconsistency(ProcessedEvent event, List<String> errors) {
        logger.error("Data inconsistency detected in ProcessedEvent: eventId={}, eventType={}, database={}, table={}, errors={}",
                event != null ? event.getEventId() : "null",
                event != null ? event.getEventType() : "null",
                event != null ? event.getDatabase() : "null",
                event != null ? event.getTable() : "null",
                errors);

        if (event != null) {
            logger.debug("ProcessedEvent details: timestamp={}, processTime={}, data={}",
                    event.getTimestamp(),
                    event.getProcessTime(),
                    event.getData());
        }
    }

    /**
     * 记录事件间一致性问题的日志
     */
    private void logInconsistency(ChangeEvent changeEvent, ProcessedEvent processedEvent, List<String> errors) {
        logger.error("Data inconsistency detected between ChangeEvent and ProcessedEvent: " +
                "changeEventId={}, processedEventId={}, errors={}",
                changeEvent != null ? changeEvent.getEventId() : "null",
                processedEvent != null ? processedEvent.getEventId() : "null",
                errors);

        if (changeEvent != null && processedEvent != null) {
            logger.debug("Event comparison: changeType={}, processedType={}, changeDb={}, processedDb={}, " +
                    "changeTable={}, processedTable={}, changeTimestamp={}, processedTimestamp={}",
                    changeEvent.getEventType(), processedEvent.getEventType(),
                    changeEvent.getDatabase(), processedEvent.getDatabase(),
                    changeEvent.getTable(), processedEvent.getTable(),
                    changeEvent.getTimestamp(), processedEvent.getTimestamp());
        }
    }
}
