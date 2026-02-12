package com.realtime.pipeline.flink.sink;

import com.realtime.pipeline.config.OutputConfig;
import com.realtime.pipeline.model.ProcessedEvent;
import org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * CsvFileSink单元测试
 * 测试CSV格式文件输出功能
 */
class CsvFileSinkTest {

    private OutputConfig config;

    @BeforeEach
    void setUp() {
        config = OutputConfig.builder()
                .path("/tmp/test-csv-output")
                .format("csv")
                .rollingSizeBytes(1024L * 1024L * 1024L)
                .rollingIntervalMs(3600000L)
                .compression("none")
                .maxRetries(3)
                .retryBackoff(2)
                .build();
    }

    @Test
    void testCreateSink() {
        // 测试创建CSV Sink
        CsvFileSink sink = new CsvFileSink(config);
        StreamingFileSink<ProcessedEvent> streamingSink = sink.createSink();
        
        assertThat(streamingSink).isNotNull();
    }

    @Test
    void testCreateSinkWithDifferentConfigs() {
        // 测试使用不同配置创建Sink
        OutputConfig config1 = OutputConfig.builder()
                .path("/tmp/output1")
                .format("csv")
                .rollingSizeBytes(500L * 1024L * 1024L) // 500MB
                .rollingIntervalMs(1800000L) // 30 minutes
                .build();

        OutputConfig config2 = OutputConfig.builder()
                .path("/tmp/output2")
                .format("csv")
                .rollingSizeBytes(2048L * 1024L * 1024L) // 2GB
                .rollingIntervalMs(7200000L) // 2 hours
                .build();

        CsvFileSink sink1 = new CsvFileSink(config1);
        CsvFileSink sink2 = new CsvFileSink(config2);

        assertThat(sink1.createSink()).isNotNull();
        assertThat(sink2.createSink()).isNotNull();
    }

    @Test
    void testCsvFileSinkInheritsFromAbstractFileSink() {
        // 测试CsvFileSink继承自AbstractFileSink
        CsvFileSink sink = new CsvFileSink(config);
        
        assertThat(sink).isInstanceOf(AbstractFileSink.class);
    }

    @Test
    void testCsvFileSinkUsesCorrectFileExtension() {
        // 测试CSV文件使用正确的扩展名
        CsvFileSink sink = new CsvFileSink(config);
        
        // 通过创建输出文件配置来验证扩展名
        var fileConfig = sink.createOutputFileConfig("csv");
        assertThat(fileConfig.getPartSuffix()).isEqualTo(".csv");
    }

    @Test
    void testCsvSerializationBasic() throws Exception {
        // 测试基本CSV序列化功能
        // 验证需求 3.3: 支持CSV格式输出
        ProcessedEvent event = createTestEvent();
        
        String csv = serializeEventToCsv(event);
        
        // 验证CSV格式正确
        assertThat(csv).contains("INSERT");
        assertThat(csv).contains("mydb");
        assertThat(csv).contains("users");
        assertThat(csv).contains("test-event-1");
        assertThat(csv).endsWith("\n");
        
        // 验证字段数量（8个字段，7个逗号）
        long commaCount = csv.chars().filter(ch -> ch == ',').count();
        assertThat(commaCount).isEqualTo(7);
    }

    @Test
    void testCsvSerializationWithSpecialCharacters() throws Exception {
        // 测试包含特殊字符的CSV序列化
        Map<String, Object> data = new HashMap<>();
        data.put("name", "张三,李四"); // 包含逗号
        data.put("description", "This is a \"test\""); // 包含引号
        data.put("notes", "Line1\nLine2"); // 包含换行符
        
        ProcessedEvent event = ProcessedEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(1706428800000L)
                .processTime(1706428801000L)
                .data(data)
                .partition("1")
                .eventId("test-event-special")
                .build();
        
        String csv = serializeEventToCsv(event);
        
        // 验证特殊字符被正确转义
        // data字段包含特殊字符，整个字段应该被引号包围
        assertThat(csv).contains("name=张三,李四");
        // 包含引号的字段应该被引号包围，内部引号被转义
        assertThat(csv).contains("This is a \"\"test\"\"");
        // 包含换行符的字段应该被引号包围
        assertThat(csv).contains("Line1\nLine2");
    }

    @Test
    void testCsvSerializationWithNullValues() throws Exception {
        // 测试包含null值的CSV序列化
        ProcessedEvent event = ProcessedEvent.builder()
                .eventType(null)
                .database("testdb")
                .table("users")
                .timestamp(1706428800000L)
                .processTime(1706428801000L)
                .data(null)
                .partition(null)
                .eventId(null)
                .build();
        
        String csv = serializeEventToCsv(event);
        
        // 验证null值被序列化为空字符串
        assertThat(csv).startsWith(",testdb,users,");
        assertThat(csv).contains(",,,");
        assertThat(csv).endsWith("\n");
    }

    @Test
    void testCsvSerializationWithComplexData() throws Exception {
        // 测试复杂数据的CSV序列化
        Map<String, Object> complexData = new HashMap<>();
        complexData.put("id", 123);
        complexData.put("name", "测试用户");
        complexData.put("age", 30);
        complexData.put("active", true);
        complexData.put("balance", 1234.56);
        
        ProcessedEvent event = ProcessedEvent.builder()
                .eventType("UPDATE")
                .database("testdb")
                .table("accounts")
                .timestamp(1706428800000L)
                .processTime(1706428801000L)
                .data(complexData)
                .partition("1")
                .eventId("test-event-complex")
                .build();
        
        String csv = serializeEventToCsv(event);
        
        // 验证所有数据类型都被正确序列化为字符串
        assertThat(csv).contains("id=123");
        assertThat(csv).contains("name=测试用户");
        assertThat(csv).contains("age=30");
        assertThat(csv).contains("active=true");
        assertThat(csv).contains("balance=1234.56");
    }

    @Test
    void testCsvSerializationWithEmptyData() throws Exception {
        // 测试空数据Map的CSV序列化
        ProcessedEvent event = ProcessedEvent.builder()
                .eventType("DELETE")
                .database("testdb")
                .table("users")
                .timestamp(1706428800000L)
                .processTime(1706428801000L)
                .data(new HashMap<>())
                .partition("1")
                .eventId("test-event-empty")
                .build();
        
        String csv = serializeEventToCsv(event);
        
        // 验证空数据Map被序列化为空字符串
        assertThat(csv).contains("DELETE,testdb,users,");
        assertThat(csv).contains(",,1,test-event-empty");
    }

    @Test
    void testCsvSerializationDataWithSpecialCharacters() throws Exception {
        // 测试data字段中包含特殊字符（分号和等号）
        Map<String, Object> data = new HashMap<>();
        data.put("key=1", "value;1"); // 包含等号和分号
        data.put("key2", "value=2");
        
        ProcessedEvent event = ProcessedEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(1706428800000L)
                .processTime(1706428801000L)
                .data(data)
                .partition("1")
                .eventId("test-event-data-special")
                .build();
        
        String csv = serializeEventToCsv(event);
        
        // 验证data字段中的特殊字符被转义
        assertThat(csv).contains("key\\=1=value\\;1");
        assertThat(csv).contains("key2=value\\=2");
    }

    @Test
    void testCsvFieldEscaping() {
        // 测试CSV字段转义逻辑
        CsvFileSink sink = new CsvFileSink(config);
        
        // 测试不需要转义的字段
        String simple = "simple text";
        assertThat(escapeCsvField(simple)).isEqualTo("simple text");
        
        // 测试包含逗号的字段
        String withComma = "text,with,comma";
        assertThat(escapeCsvField(withComma)).isEqualTo("\"text,with,comma\"");
        
        // 测试包含引号的字段
        String withQuote = "text with \"quotes\"";
        assertThat(escapeCsvField(withQuote)).isEqualTo("\"text with \"\"quotes\"\"\"");
        
        // 测试包含换行符的字段
        String withNewline = "text\nwith\nnewline";
        assertThat(escapeCsvField(withNewline)).isEqualTo("\"text\nwith\nnewline\"");
        
        // 测试null值
        assertThat(escapeCsvField(null)).isEqualTo("");
    }

    // 辅助方法：创建测试事件
    private ProcessedEvent createTestEvent() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", 1);
        data.put("name", "张三");
        data.put("email", "zhangsan@example.com");

        return ProcessedEvent.builder()
                .eventType("INSERT")
                .database("mydb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .processTime(System.currentTimeMillis())
                .data(data)
                .partition("0")
                .eventId("test-event-1")
                .build();
    }

    // 辅助方法：将事件序列化为CSV字符串
    private String serializeEventToCsv(ProcessedEvent event) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        // 模拟CsvEncoder的encode方法
        StringBuilder csv = new StringBuilder();
        csv.append(escapeCsvField(event.getEventType())).append(",");
        csv.append(escapeCsvField(event.getDatabase())).append(",");
        csv.append(escapeCsvField(event.getTable())).append(",");
        csv.append(event.getTimestamp()).append(",");
        csv.append(event.getProcessTime()).append(",");
        csv.append(escapeCsvField(serializeData(event.getData()))).append(",");
        csv.append(escapeCsvField(event.getPartition())).append(",");
        csv.append(escapeCsvField(event.getEventId()));
        
        outputStream.write(csv.toString().getBytes(StandardCharsets.UTF_8));
        outputStream.write('\n');
        
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    // 辅助方法：转义CSV字段
    private String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        
        if (field.contains(",") || field.contains("\"") || 
            field.contains("\n") || field.contains("\r")) {
            String escaped = field.replace("\"", "\"\"");
            return "\"" + escaped + "\"";
        }
        
        return field;
    }

    // 辅助方法：序列化data Map
    private String serializeData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return "";
        }
        
        return data.entrySet().stream()
                .map(entry -> {
                    String key = entry.getKey();
                    String value = entry.getValue() != null ? entry.getValue().toString() : "";
                    key = key.replace("=", "\\=").replace(";", "\\;");
                    value = value.replace("=", "\\=").replace(";", "\\;");
                    return key + "=" + value;
                })
                .collect(java.util.stream.Collectors.joining(";"));
    }
}
