package com.realtime.pipeline.flink.sink;

import com.realtime.pipeline.config.OutputConfig;
import com.realtime.pipeline.model.ProcessedEvent;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.flink.api.common.serialization.BulkWriter;
import org.apache.flink.core.fs.FSDataOutputStream;
import org.apache.flink.formats.parquet.avro.AvroParquetWriters;
import org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.OnCheckpointRollingPolicy;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Parquet格式文件Sink
 * 将ProcessedEvent序列化为Parquet格式并写入文件
 * 
 * 实现需求 3.2: 支持Parquet格式输出
 */
public class ParquetFileSink extends AbstractFileSink {
    private static final long serialVersionUID = 1L;
    
    // Avro Schema for ProcessedEvent
    private static final String AVRO_SCHEMA_STRING = 
        "{\"type\":\"record\"," +
        "\"name\":\"ProcessedEvent\"," +
        "\"namespace\":\"com.realtime.pipeline.model\"," +
        "\"fields\":[" +
            "{\"name\":\"eventType\",\"type\":[\"null\",\"string\"],\"default\":null}," +
            "{\"name\":\"database\",\"type\":[\"null\",\"string\"],\"default\":null}," +
            "{\"name\":\"table\",\"type\":[\"null\",\"string\"],\"default\":null}," +
            "{\"name\":\"timestamp\",\"type\":\"long\",\"default\":0}," +
            "{\"name\":\"processTime\",\"type\":\"long\",\"default\":0}," +
            "{\"name\":\"data\",\"type\":[\"null\",{\"type\":\"map\",\"values\":\"string\"}],\"default\":null}," +
            "{\"name\":\"partition\",\"type\":[\"null\",\"string\"],\"default\":null}," +
            "{\"name\":\"eventId\",\"type\":[\"null\",\"string\"],\"default\":null}" +
        "]}";
    
    private static final Schema AVRO_SCHEMA = new Schema.Parser().parse(AVRO_SCHEMA_STRING);

    public ParquetFileSink(OutputConfig config) {
        super(config);
    }

    @Override
    public StreamingFileSink<ProcessedEvent> createSink() {
        // For bulk format (Parquet), we use OnCheckpointRollingPolicy
        // which rolls files on checkpoint boundaries
        return StreamingFileSink
                .forBulkFormat(
                    getOutputPath(),
                    new ProcessedEventParquetWriterFactory()
                )
                .withBucketAssigner(createBucketAssigner())
                .withRollingPolicy(OnCheckpointRollingPolicy.build())
                .withOutputFileConfig(createOutputFileConfig("parquet"))
                .build();
    }

    /**
     * 将ProcessedEvent转换为Avro GenericRecord
     * 
     * @param event ProcessedEvent对象
     * @return Avro GenericRecord
     */
    private static GenericRecord toAvroRecord(ProcessedEvent event) {
        GenericRecord record = new GenericData.Record(AVRO_SCHEMA);
        
        record.put("eventType", event.getEventType());
        record.put("database", event.getDatabase());
        record.put("table", event.getTable());
        record.put("timestamp", event.getTimestamp());
        record.put("processTime", event.getProcessTime());
        
        // 将Map<String, Object>转换为Map<String, String>
        // Parquet/Avro需要具体的类型，这里将所有值转换为字符串
        Map<String, String> dataMap = new HashMap<>();
        if (event.getData() != null) {
            event.getData().forEach((key, value) -> {
                dataMap.put(key, value != null ? value.toString() : null);
            });
        }
        record.put("data", dataMap);
        
        record.put("partition", event.getPartition());
        record.put("eventId", event.getEventId());
        
        return record;
    }

    /**
     * 获取Avro Schema
     * @return Avro Schema
     */
    public static Schema getAvroSchema() {
        return AVRO_SCHEMA;
    }

    /**
     * ProcessedEvent的Parquet Writer Factory
     * 将ProcessedEvent转换为Avro GenericRecord并写入Parquet文件
     */
    private static class ProcessedEventParquetWriterFactory implements BulkWriter.Factory<ProcessedEvent> {
        private static final long serialVersionUID = 1L;

        @Override
        public BulkWriter<ProcessedEvent> create(FSDataOutputStream out) throws IOException {
            // 创建Avro GenericRecord的Parquet Writer
            BulkWriter<GenericRecord> avroWriter = 
                AvroParquetWriters.forGenericRecord(AVRO_SCHEMA).create(out);
            
            // 包装为ProcessedEvent的Writer
            return new BulkWriter<ProcessedEvent>() {
                @Override
                public void addElement(ProcessedEvent element) throws IOException {
                    GenericRecord record = toAvroRecord(element);
                    avroWriter.addElement(record);
                }

                @Override
                public void flush() throws IOException {
                    avroWriter.flush();
                }

                @Override
                public void finish() throws IOException {
                    avroWriter.finish();
                }
            };
        }
    }
}

