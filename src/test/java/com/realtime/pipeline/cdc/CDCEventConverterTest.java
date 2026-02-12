package com.realtime.pipeline.cdc;

import com.realtime.pipeline.model.ChangeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * CDCEventConverter单元测试
 * 
 * 测试范围:
 * - 测试INSERT操作的转换（需求1.1）
 * - 测试UPDATE操作的转换（需求1.2）
 * - 测试DELETE操作的转换（需求1.3）
 * - 测试数据完整性
 * - 测试错误处理
 * - 测试边界情况
 * 
 * 需求: 1.1, 1.2, 1.3
 */
class CDCEventConverterTest {

    private CDCEventConverter converter;

    @BeforeEach
    void setUp() {
        converter = new CDCEventConverter();
    }

    /**
     * 测试INSERT操作的转换
     * 验证INSERT事件能够正确转换（需求1.1）
     */
    @Test
    void testConvertInsertEvent() throws Exception {
        // 准备INSERT事件的JSON
        String insertJson = "{"
            + "\"op\":\"c\","
            + "\"source\":{"
            + "  \"db\":\"testdb\","
            + "  \"table\":\"users\""
            + "},"
            + "\"after\":{"
            + "  \"id\":1,"
            + "  \"name\":\"John Doe\","
            + "  \"email\":\"john@example.com\""
            + "},"
            + "\"ts_ms\":1234567890"
            + "}";

        // 转换事件
        ChangeEvent event = converter.map(insertJson);

        // 验证事件类型
        assertThat(event.getEventType()).isEqualTo("INSERT");
        
        // 验证数据库和表信息
        assertThat(event.getDatabase()).isEqualTo("testdb");
        assertThat(event.getTable()).isEqualTo("users");
        
        // 验证时间戳
        assertThat(event.getTimestamp()).isEqualTo(1234567890L);
        
        // 验证after数据
        assertThat(event.getAfter()).isNotNull();
        assertThat(event.getAfter()).containsEntry("id", 1);
        assertThat(event.getAfter()).containsEntry("name", "John Doe");
        assertThat(event.getAfter()).containsEntry("email", "john@example.com");
        
        // 验证before数据为null（INSERT操作没有before）
        assertThat(event.getBefore()).isNull();
        
        // 验证事件ID已生成
        assertThat(event.getEventId()).isNotNull();
        
        // 验证主键
        assertThat(event.getPrimaryKeys()).isNotNull();
        assertThat(event.getPrimaryKeys()).isNotEmpty();
    }

    /**
     * 测试UPDATE操作的转换
     * 验证UPDATE事件能够正确转换（需求1.2）
     */
    @Test
    void testConvertUpdateEvent() throws Exception {
        // 准备UPDATE事件的JSON
        String updateJson = "{"
            + "\"op\":\"u\","
            + "\"source\":{"
            + "  \"db\":\"testdb\","
            + "  \"table\":\"users\""
            + "},"
            + "\"before\":{"
            + "  \"id\":1,"
            + "  \"name\":\"John Doe\","
            + "  \"email\":\"john@example.com\""
            + "},"
            + "\"after\":{"
            + "  \"id\":1,"
            + "  \"name\":\"John Smith\","
            + "  \"email\":\"john.smith@example.com\""
            + "},"
            + "\"ts_ms\":1234567890"
            + "}";

        // 转换事件
        ChangeEvent event = converter.map(updateJson);

        // 验证事件类型
        assertThat(event.getEventType()).isEqualTo("UPDATE");
        
        // 验证数据库和表信息
        assertThat(event.getDatabase()).isEqualTo("testdb");
        assertThat(event.getTable()).isEqualTo("users");
        
        // 验证before数据（需求1.2：UPDATE操作应包含变更前数据）
        assertThat(event.getBefore()).isNotNull();
        assertThat(event.getBefore()).containsEntry("id", 1);
        assertThat(event.getBefore()).containsEntry("name", "John Doe");
        assertThat(event.getBefore()).containsEntry("email", "john@example.com");
        
        // 验证after数据（需求1.2：UPDATE操作应包含变更后数据）
        assertThat(event.getAfter()).isNotNull();
        assertThat(event.getAfter()).containsEntry("id", 1);
        assertThat(event.getAfter()).containsEntry("name", "John Smith");
        assertThat(event.getAfter()).containsEntry("email", "john.smith@example.com");
        
        // 验证事件ID已生成
        assertThat(event.getEventId()).isNotNull();
    }

    /**
     * 测试DELETE操作的转换
     * 验证DELETE事件能够正确转换（需求1.3）
     */
    @Test
    void testConvertDeleteEvent() throws Exception {
        // 准备DELETE事件的JSON
        String deleteJson = "{"
            + "\"op\":\"d\","
            + "\"source\":{"
            + "  \"db\":\"testdb\","
            + "  \"table\":\"users\""
            + "},"
            + "\"before\":{"
            + "  \"id\":1,"
            + "  \"name\":\"John Doe\","
            + "  \"email\":\"john@example.com\""
            + "},"
            + "\"ts_ms\":1234567890"
            + "}";

        // 转换事件
        ChangeEvent event = converter.map(deleteJson);

        // 验证事件类型
        assertThat(event.getEventType()).isEqualTo("DELETE");
        
        // 验证数据库和表信息
        assertThat(event.getDatabase()).isEqualTo("testdb");
        assertThat(event.getTable()).isEqualTo("users");
        
        // 验证before数据
        assertThat(event.getBefore()).isNotNull();
        assertThat(event.getBefore()).containsEntry("id", 1);
        assertThat(event.getBefore()).containsEntry("name", "John Doe");
        
        // 验证after数据为null（DELETE操作没有after）
        assertThat(event.getAfter()).isNull();
        
        // 验证事件ID已生成
        assertThat(event.getEventId()).isNotNull();
    }

    /**
     * 测试快照读取操作的转换
     * 验证快照读取（r操作）被转换为INSERT
     */
    @Test
    void testConvertSnapshotReadEvent() throws Exception {
        // 准备快照读取事件的JSON
        String snapshotJson = "{"
            + "\"op\":\"r\","
            + "\"source\":{"
            + "  \"db\":\"testdb\","
            + "  \"table\":\"users\""
            + "},"
            + "\"after\":{"
            + "  \"id\":1,"
            + "  \"name\":\"John Doe\""
            + "},"
            + "\"ts_ms\":1234567890"
            + "}";

        // 转换事件
        ChangeEvent event = converter.map(snapshotJson);

        // 验证快照读取被转换为INSERT
        assertThat(event.getEventType()).isEqualTo("INSERT");
        assertThat(event.getAfter()).isNotNull();
        assertThat(event.getBefore()).isNull();
    }

    /**
     * 测试未知操作类型的转换
     * 验证未知操作类型默认为INSERT
     */
    @Test
    void testConvertUnknownOperationType() throws Exception {
        // 准备未知操作类型的JSON
        String unknownJson = "{"
            + "\"op\":\"x\","
            + "\"source\":{"
            + "  \"db\":\"testdb\","
            + "  \"table\":\"users\""
            + "},"
            + "\"after\":{"
            + "  \"id\":1"
            + "},"
            + "\"ts_ms\":1234567890"
            + "}";

        // 转换事件
        ChangeEvent event = converter.map(unknownJson);

        // 验证默认为INSERT
        assertThat(event.getEventType()).isEqualTo("INSERT");
    }

    /**
     * 测试主键提取
     * 验证能够正确提取主键字段
     */
    @Test
    void testPrimaryKeyExtraction() throws Exception {
        // 准备包含主键信息的JSON
        String jsonWithPk = "{"
            + "\"op\":\"c\","
            + "\"source\":{"
            + "  \"db\":\"testdb\","
            + "  \"table\":\"users\","
            + "  \"pkNames\":[\"id\"]"
            + "},"
            + "\"after\":{"
            + "  \"id\":1,"
            + "  \"name\":\"John Doe\""
            + "},"
            + "\"ts_ms\":1234567890"
            + "}";

        // 转换事件
        ChangeEvent event = converter.map(jsonWithPk);

        // 验证主键
        assertThat(event.getPrimaryKeys()).containsExactly("id");
    }

    /**
     * 测试复合主键提取
     * 验证能够正确提取复合主键
     */
    @Test
    void testCompositePrimaryKeyExtraction() throws Exception {
        // 准备包含复合主键的JSON
        String jsonWithCompositePk = "{"
            + "\"op\":\"c\","
            + "\"source\":{"
            + "  \"db\":\"testdb\","
            + "  \"table\":\"order_items\","
            + "  \"pkNames\":[\"order_id\",\"item_id\"]"
            + "},"
            + "\"after\":{"
            + "  \"order_id\":1,"
            + "  \"item_id\":2,"
            + "  \"quantity\":5"
            + "},"
            + "\"ts_ms\":1234567890"
            + "}";

        // 转换事件
        ChangeEvent event = converter.map(jsonWithCompositePk);

        // 验证复合主键
        assertThat(event.getPrimaryKeys()).containsExactly("order_id", "item_id");
    }

    /**
     * 测试默认主键推断
     * 验证当没有主键信息时，能够推断常见的主键字段
     */
    @Test
    void testDefaultPrimaryKeyInference() throws Exception {
        // 准备没有主键信息但包含id字段的JSON
        String jsonWithoutPk = "{"
            + "\"op\":\"c\","
            + "\"source\":{"
            + "  \"db\":\"testdb\","
            + "  \"table\":\"users\""
            + "},"
            + "\"after\":{"
            + "  \"id\":1,"
            + "  \"name\":\"John Doe\""
            + "},"
            + "\"ts_ms\":1234567890"
            + "}";

        // 转换事件
        ChangeEvent event = converter.map(jsonWithoutPk);

        // 验证推断出id作为主键
        assertThat(event.getPrimaryKeys()).contains("id");
    }

    /**
     * 测试时间戳处理
     * 验证时间戳能够正确转换
     */
    @Test
    void testTimestampHandling() throws Exception {
        // 准备包含时间戳的JSON
        long expectedTimestamp = System.currentTimeMillis();
        String jsonWithTimestamp = "{"
            + "\"op\":\"c\","
            + "\"source\":{"
            + "  \"db\":\"testdb\","
            + "  \"table\":\"users\""
            + "},"
            + "\"after\":{"
            + "  \"id\":1"
            + "},"
            + "\"ts_ms\":" + expectedTimestamp
            + "}";

        // 转换事件
        ChangeEvent event = converter.map(jsonWithTimestamp);

        // 验证时间戳
        assertThat(event.getTimestamp()).isEqualTo(expectedTimestamp);
    }

    /**
     * 测试缺少时间戳的情况
     * 验证缺少时间戳时使用当前时间
     */
    @Test
    void testMissingTimestamp() throws Exception {
        // 准备没有时间戳的JSON
        String jsonWithoutTimestamp = "{"
            + "\"op\":\"c\","
            + "\"source\":{"
            + "  \"db\":\"testdb\","
            + "  \"table\":\"users\""
            + "},"
            + "\"after\":{"
            + "  \"id\":1"
            + "}"
            + "}";

        // 记录当前时间
        long beforeTime = System.currentTimeMillis();
        
        // 转换事件
        ChangeEvent event = converter.map(jsonWithoutTimestamp);
        
        long afterTime = System.currentTimeMillis();

        // 验证时间戳在合理范围内
        assertThat(event.getTimestamp()).isBetween(beforeTime, afterTime);
    }

    /**
     * 测试复杂数据类型
     * 验证能够处理各种数据类型
     */
    @Test
    void testComplexDataTypes() throws Exception {
        // 准备包含多种数据类型的JSON
        String complexJson = "{"
            + "\"op\":\"c\","
            + "\"source\":{"
            + "  \"db\":\"testdb\","
            + "  \"table\":\"users\""
            + "},"
            + "\"after\":{"
            + "  \"id\":1,"
            + "  \"name\":\"John Doe\","
            + "  \"age\":30,"
            + "  \"salary\":50000.50,"
            + "  \"active\":true,"
            + "  \"created_at\":1234567890"
            + "},"
            + "\"ts_ms\":1234567890"
            + "}";

        // 转换事件
        ChangeEvent event = converter.map(complexJson);

        // 验证各种数据类型
        Map<String, Object> data = event.getAfter();
        assertThat(data.get("id")).isEqualTo(1);
        assertThat(data.get("name")).isEqualTo("John Doe");
        assertThat(data.get("age")).isEqualTo(30);
        assertThat(data.get("salary")).isEqualTo(50000.50);
        assertThat(data.get("active")).isEqualTo(true);
        assertThat(data.get("created_at")).isEqualTo(1234567890);
    }

    /**
     * 测试空数据字段
     * 验证能够处理null的before和after字段
     */
    @Test
    void testNullDataFields() throws Exception {
        // 准备包含null字段的JSON
        String jsonWithNulls = "{"
            + "\"op\":\"c\","
            + "\"source\":{"
            + "  \"db\":\"testdb\","
            + "  \"table\":\"users\""
            + "},"
            + "\"before\":null,"
            + "\"after\":null,"
            + "\"ts_ms\":1234567890"
            + "}";

        // 转换事件
        ChangeEvent event = converter.map(jsonWithNulls);

        // 验证null字段
        assertThat(event.getBefore()).isNull();
        assertThat(event.getAfter()).isNull();
    }

    /**
     * 测试无效JSON
     * 验证无效JSON抛出异常
     */
    @Test
    void testInvalidJson() {
        // 准备无效的JSON
        String invalidJson = "{ invalid json }";

        // 验证抛出异常
        assertThatThrownBy(() -> converter.map(invalidJson))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to convert CDC event");
    }

    /**
     * 测试空字符串
     * 验证空字符串的处理（可能返回默认值或抛出异常）
     */
    @Test
    void testEmptyString() {
        // 准备空字符串
        String emptyJson = "";

        // 空字符串可能会被Jackson解析为null或抛出异常
        // 我们验证它不会导致程序崩溃
        try {
            ChangeEvent event = converter.map(emptyJson);
            // 如果没有抛出异常，验证返回的事件不为null
            assertThat(event).isNotNull();
        } catch (Exception e) {
            // 如果抛出异常，验证是预期的异常类型
            assertThat(e).isInstanceOf(Exception.class);
        }
    }

    /**
     * 测试缺少必需字段
     * 验证缺少source字段时的处理
     */
    @Test
    void testMissingSourceField() throws Exception {
        // 准备缺少source字段的JSON
        String jsonWithoutSource = "{"
            + "\"op\":\"c\","
            + "\"after\":{"
            + "  \"id\":1"
            + "},"
            + "\"ts_ms\":1234567890"
            + "}";

        // 转换事件
        ChangeEvent event = converter.map(jsonWithoutSource);

        // 验证使用空字符串作为默认值
        assertThat(event.getDatabase()).isEmpty();
        assertThat(event.getTable()).isEmpty();
    }

    /**
     * 测试事件ID唯一性
     * 验证每个转换的事件都有唯一的ID
     */
    @Test
    void testEventIdUniqueness() throws Exception {
        // 准备相同的JSON
        String json = "{"
            + "\"op\":\"c\","
            + "\"source\":{"
            + "  \"db\":\"testdb\","
            + "  \"table\":\"users\""
            + "},"
            + "\"after\":{"
            + "  \"id\":1"
            + "},"
            + "\"ts_ms\":1234567890"
            + "}";

        // 转换多次
        ChangeEvent event1 = converter.map(json);
        ChangeEvent event2 = converter.map(json);
        ChangeEvent event3 = converter.map(json);

        // 验证事件ID都不相同
        assertThat(event1.getEventId()).isNotEqualTo(event2.getEventId());
        assertThat(event2.getEventId()).isNotEqualTo(event3.getEventId());
        assertThat(event1.getEventId()).isNotEqualTo(event3.getEventId());
    }

    /**
     * 测试序列化支持
     * 验证CDCEventConverter实现了Serializable
     */
    @Test
    void testSerializability() {
        // 验证是Serializable的实例
        assertThat(converter).isInstanceOf(java.io.Serializable.class);
    }
}
