package com.realtime.pipeline.flink.sink;

import com.realtime.pipeline.config.OutputConfig;
import com.realtime.pipeline.model.ProcessedEvent;
import org.apache.flink.api.common.serialization.Encoder;
import org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CSV格式文件Sink
 * 将ProcessedEvent序列化为CSV格式并写入文件
 * 
 * 实现需求 3.3: 支持CSV格式输出
 */
public class CsvFileSink extends AbstractFileSink {
    private static final long serialVersionUID = 1L;

    public CsvFileSink(OutputConfig config) {
        super(config);
    }

    @Override
    public StreamingFileSink<ProcessedEvent> createSink() {
        return StreamingFileSink
                .forRowFormat(getOutputPath(), new CsvEncoder())
                .withBucketAssigner(createBucketAssigner())
                .withRollingPolicy(createRollingPolicy())
                .withOutputFileConfig(createOutputFileConfig("csv"))
                .build();
    }

    /**
     * CSV编码器
     * 将ProcessedEvent对象序列化为CSV格式字符串
     */
    private static class CsvEncoder implements Encoder<ProcessedEvent> {
        private static final long serialVersionUID = 1L;
        private static final String DELIMITER = ",";
        private static final String QUOTE = "\"";
        
        @Override
        public void encode(ProcessedEvent event, OutputStream stream) throws IOException {
            StringBuilder csv = new StringBuilder();
            
            // CSV格式: eventType,database,table,timestamp,processTime,data,partition,eventId
            csv.append(escapeCsvField(event.getEventType())).append(DELIMITER);
            csv.append(escapeCsvField(event.getDatabase())).append(DELIMITER);
            csv.append(escapeCsvField(event.getTable())).append(DELIMITER);
            csv.append(event.getTimestamp()).append(DELIMITER);
            csv.append(event.getProcessTime()).append(DELIMITER);
            csv.append(escapeCsvField(serializeData(event.getData()))).append(DELIMITER);
            csv.append(escapeCsvField(event.getPartition())).append(DELIMITER);
            csv.append(escapeCsvField(event.getEventId()));
            
            stream.write(csv.toString().getBytes(StandardCharsets.UTF_8));
            stream.write('\n'); // 每行一个记录
        }
        
        /**
         * 转义CSV字段
         * 如果字段包含逗号、引号或换行符，则用引号包围并转义内部引号
         * 
         * @param field 字段值
         * @return 转义后的字段
         */
        private String escapeCsvField(String field) {
            if (field == null) {
                return "";
            }
            
            // 如果字段包含特殊字符，需要用引号包围
            if (field.contains(DELIMITER) || field.contains(QUOTE) || 
                field.contains("\n") || field.contains("\r")) {
                // 转义内部的引号（双引号）
                String escaped = field.replace(QUOTE, QUOTE + QUOTE);
                return QUOTE + escaped + QUOTE;
            }
            
            return field;
        }
        
        /**
         * 将data Map序列化为字符串
         * 格式: key1=value1;key2=value2
         * 
         * @param data 数据Map
         * @return 序列化后的字符串
         */
        private String serializeData(Map<String, Object> data) {
            if (data == null || data.isEmpty()) {
                return "";
            }
            
            return data.entrySet().stream()
                    .map(entry -> {
                        String key = entry.getKey();
                        String value = entry.getValue() != null ? entry.getValue().toString() : "";
                        // 转义分号和等号
                        key = key.replace("=", "\\=").replace(";", "\\;");
                        value = value.replace("=", "\\=").replace(";", "\\;");
                        return key + "=" + value;
                    })
                    .collect(Collectors.joining(";"));
        }
    }
}
