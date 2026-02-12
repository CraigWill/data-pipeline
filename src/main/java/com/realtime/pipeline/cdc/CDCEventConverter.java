package com.realtime.pipeline.cdc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtime.pipeline.model.ChangeEvent;
import org.apache.flink.api.common.functions.MapFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * CDC事件转换器
 * 将Debezium CDC JSON格式转换为ChangeEvent模型
 * 
 * Debezium CDC事件格式:
 * {
 *   "before": { ... },  // 变更前数据（UPDATE和DELETE）
 *   "after": { ... },   // 变更后数据（INSERT和UPDATE）
 *   "source": {
 *     "db": "database_name",
 *     "table": "table_name",
 *     "ts_ms": 1234567890
 *   },
 *   "op": "c|u|d",      // c=create(INSERT), u=update, d=delete
 *   "ts_ms": 1234567890
 * }
 */
public class CDCEventConverter implements MapFunction<String, ChangeEvent> {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(CDCEventConverter.class);
    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ChangeEvent map(String jsonString) throws Exception {
        try {
            JsonNode root = objectMapper.readTree(jsonString);
            
            // 解析操作类型
            String op = root.has("op") ? root.get("op").asText() : "";
            String eventType = convertOperationType(op);
            
            // 解析source信息
            JsonNode source = root.get("source");
            String database = source != null && source.has("db") ? source.get("db").asText() : "";
            String table = source != null && source.has("table") ? source.get("table").asText() : "";
            long timestamp = root.has("ts_ms") ? root.get("ts_ms").asLong() : System.currentTimeMillis();
            
            // 解析before和after数据
            Map<String, Object> before = null;
            Map<String, Object> after = null;
            
            if (root.has("before") && !root.get("before").isNull()) {
                before = objectMapper.convertValue(root.get("before"), Map.class);
            }
            
            if (root.has("after") && !root.get("after").isNull()) {
                after = objectMapper.convertValue(root.get("after"), Map.class);
            }
            
            // 提取主键（从source.pkNames或从数据中推断）
            List<String> primaryKeys = extractPrimaryKeys(source, before, after);
            
            // 生成事件ID
            String eventId = UUID.randomUUID().toString();
            
            ChangeEvent event = ChangeEvent.builder()
                .eventType(eventType)
                .database(database)
                .table(table)
                .timestamp(timestamp)
                .before(before)
                .after(after)
                .primaryKeys(primaryKeys)
                .eventId(eventId)
                .build();
            
            logger.debug("Converted CDC event: type={}, db={}, table={}, timestamp={}", 
                eventType, database, table, timestamp);
            
            return event;
            
        } catch (Exception e) {
            logger.error("Failed to convert CDC event: {}", jsonString, e);
            throw new RuntimeException("Failed to convert CDC event", e);
        }
    }

    /**
     * 转换操作类型
     * Debezium操作码: c=create(INSERT), u=update, d=delete, r=read(snapshot)
     */
    private String convertOperationType(String op) {
        switch (op) {
            case "c":
            case "r": // snapshot read视为INSERT
                return "INSERT";
            case "u":
                return "UPDATE";
            case "d":
                return "DELETE";
            default:
                logger.warn("Unknown operation type: {}, defaulting to INSERT", op);
                return "INSERT";
        }
    }

    /**
     * 提取主键字段列表
     */
    private List<String> extractPrimaryKeys(JsonNode source, Map<String, Object> before, Map<String, Object> after) {
        List<String> primaryKeys = new ArrayList<>();
        
        // 尝试从source中获取主键名称
        if (source != null && source.has("pkNames")) {
            JsonNode pkNames = source.get("pkNames");
            if (pkNames.isArray()) {
                for (JsonNode pkName : pkNames) {
                    primaryKeys.add(pkName.asText());
                }
            }
        }
        
        // 如果没有找到主键信息，尝试使用常见的主键字段名
        if (primaryKeys.isEmpty()) {
            Map<String, Object> data = after != null ? after : before;
            if (data != null) {
                // 常见的主键字段名
                String[] commonPkNames = {"id", "ID", "Id", "_id"};
                for (String pkName : commonPkNames) {
                    if (data.containsKey(pkName)) {
                        primaryKeys.add(pkName);
                        break;
                    }
                }
            }
        }
        
        // 如果仍然没有找到，使用默认值
        if (primaryKeys.isEmpty()) {
            primaryKeys.add("id");
            logger.warn("No primary key found, using default 'id'");
        }
        
        return primaryKeys;
    }
}
