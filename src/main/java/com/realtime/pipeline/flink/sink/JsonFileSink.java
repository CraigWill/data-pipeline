package com.realtime.pipeline.flink.sink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.realtime.pipeline.config.OutputConfig;
import com.realtime.pipeline.model.ProcessedEvent;
import org.apache.flink.api.common.serialization.Encoder;
import org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * JSON格式文件Sink
 * 将ProcessedEvent序列化为JSON格式并写入文件
 * 
 * 实现需求 3.1: 支持JSON格式输出
 */
public class JsonFileSink extends AbstractFileSink {
    private static final long serialVersionUID = 1L;

    public JsonFileSink(OutputConfig config) {
        super(config);
    }

    @Override
    public StreamingFileSink<ProcessedEvent> createSink() {
        return StreamingFileSink
                .forRowFormat(getOutputPath(), new JsonEncoder())
                .withBucketAssigner(createBucketAssigner())
                .withRollingPolicy(createRollingPolicy())
                .withOutputFileConfig(createOutputFileConfig("json"))
                .build();
    }

    /**
     * JSON编码器
     * 将ProcessedEvent对象序列化为JSON字符串
     */
    private static class JsonEncoder implements Encoder<ProcessedEvent> {
        private static final long serialVersionUID = 1L;
        private transient ObjectMapper objectMapper;

        @Override
        public void encode(ProcessedEvent event, OutputStream stream) throws IOException {
            if (objectMapper == null) {
                objectMapper = new ObjectMapper();
                objectMapper.registerModule(new JavaTimeModule());
                objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                // 只序列化字段，不序列化getter方法（避免序列化计算属性）
                objectMapper.setVisibility(
                    com.fasterxml.jackson.annotation.PropertyAccessor.FIELD,
                    com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY
                );
                objectMapper.setVisibility(
                    com.fasterxml.jackson.annotation.PropertyAccessor.GETTER,
                    com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE
                );
            }
            
            String json = objectMapper.writeValueAsString(event);
            stream.write(json.getBytes(StandardCharsets.UTF_8));
            stream.write('\n'); // 每行一个JSON对象
        }
    }
}
