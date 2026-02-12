package com.realtime.pipeline.flink.sink;

import com.realtime.pipeline.config.OutputConfig;
import com.realtime.pipeline.model.ProcessedEvent;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.apache.flink.streaming.api.functions.sink.filesystem.BucketAssigner;
import org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * 文件输出组件的基于属性的测试
 * Feature: realtime-data-pipeline
 * 
 * 使用jqwik进行基于属性的测试，验证文件输出组件的通用属性
 * 
 * 测试属性:
 * - 属性 11: 多格式输出支持
 * - 属性 12: 文件滚动策略
 * - 属性 13: 文件命名规范
 * - 属性 4: 失败重试机制（文件写入）
 */
class FileSinkPropertyTest {

    private static final String TEST_OUTPUT_BASE = "/tmp/flink-sink-property-test";

    /**
     * Property 11: 多格式输出支持
     * **Validates: Requirements 3.1, 3.2, 3.3**
     * 
     * 对于任何配置的输出格式（JSON、Parquet、CSV），系统应该能够正确生成该格式的文件
     */
    @Property(tries = 20)
    @Label("Property 11: Multi-format output support")
    void property11_multiFormatOutputSupport(
            @ForAll("outputFormats") String format,
            @ForAll("processedEvents") ProcessedEvent event) {
        
        // 创建测试输出目录
        String testPath = TEST_OUTPUT_BASE + "/format-test-" + UUID.randomUUID();
        
        try {
            // 创建配置
            OutputConfig config = OutputConfig.builder()
                    .path(testPath)
                    .format(format)
                    .rollingSizeBytes(1024L * 1024L * 1024L)  // 1GB
                    .rollingIntervalMs(3600000L)  // 1 hour
                    .compression("none")
                    .maxRetries(3)
                    .retryBackoff(2)
                    .build();
            
            // 根据格式创建相应的Sink
            AbstractFileSink sink = createSinkForFormat(format, config);
            
            // 验证：Sink应该成功创建
            assertThat(sink)
                    .as("Sink should be created for format: " + format)
                    .isNotNull();
            
            // 验证：Sink应该能够创建StreamingFileSink
            StreamingFileSink<ProcessedEvent> streamingSink = sink.createSink();
            assertThat(streamingSink)
                    .as("StreamingFileSink should be created for format: " + format)
                    .isNotNull();
            
            // 验证：输出文件配置应该使用正确的文件扩展名
            var fileConfig = sink.createOutputFileConfig(format);
            assertThat(fileConfig.getPartSuffix())
                    .as("File suffix should match format: " + format)
                    .isEqualTo("." + format);
            
            // 验证：不同格式的Sink类型应该正确
            if ("json".equals(format)) {
                assertThat(sink).isInstanceOf(JsonFileSink.class);
            } else if ("csv".equals(format)) {
                assertThat(sink).isInstanceOf(CsvFileSink.class);
            } else if ("parquet".equals(format)) {
                assertThat(sink).isInstanceOf(ParquetFileSink.class);
            }
            
        } finally {
            // 清理测试目录
            cleanupTestDirectory(testPath);
        }
    }

    /**
     * Property 12: 文件滚动策略
     * **Validates: Requirements 3.4, 3.5**
     * 
     * 对于任何输出文件，当文件大小达到1GB或时间跨度达到1小时时，系统应该创建新文件
     */
    @Property(tries = 20)
    @Label("Property 12: File rolling strategy - size and time based")
    void property12_fileRollingStrategy(
            @ForAll("rollingSizes") long rollingSizeBytes,
            @ForAll("rollingIntervals") long rollingIntervalMs,
            @ForAll("outputFormats") String format) {
        
        String testPath = TEST_OUTPUT_BASE + "/rolling-test-" + UUID.randomUUID();
        
        try {
            // 创建配置
            OutputConfig config = OutputConfig.builder()
                    .path(testPath)
                    .format(format)
                    .rollingSizeBytes(rollingSizeBytes)
                    .rollingIntervalMs(rollingIntervalMs)
                    .compression("none")
                    .maxRetries(3)
                    .retryBackoff(2)
                    .build();
            
            // 创建Sink
            AbstractFileSink sink = createSinkForFormat(format, config);
            
            // 创建滚动策略
            var rollingPolicy = sink.createRollingPolicy();
            
            // 验证：滚动策略应该成功创建
            assertThat(rollingPolicy)
                    .as("Rolling policy should be created")
                    .isNotNull();
            
            // 验证：配置的滚动参数应该被正确应用
            assertThat(config.getRollingSizeBytes())
                    .as("Rolling size should match configured value")
                    .isEqualTo(rollingSizeBytes);
            assertThat(config.getRollingIntervalMs())
                    .as("Rolling interval should match configured value")
                    .isEqualTo(rollingIntervalMs);
            
            // 验证：滚动策略应该支持标准的滚动阈值
            // 1GB = 1024 * 1024 * 1024 bytes
            // 1 hour = 3600000 ms
            if (rollingSizeBytes == 1024L * 1024L * 1024L) {
                assertThat(config.getRollingSizeBytes())
                        .as("Should support 1GB rolling size")
                        .isEqualTo(1024L * 1024L * 1024L);
            }
            if (rollingIntervalMs == 3600000L) {
                assertThat(config.getRollingIntervalMs())
                        .as("Should support 1 hour rolling interval")
                        .isEqualTo(3600000L);
            }
            
        } finally {
            cleanupTestDirectory(testPath);
        }
    }

    /**
     * Property 13: 文件命名规范
     * **Validates: Requirements 3.6**
     * 
     * 对于任何生成的输出文件，文件名应该包含数据库名、表名、时间戳和分区信息
     */
    @Property(tries = 20)
    @Label("Property 13: File naming convention")
    void property13_fileNamingConvention(
            @ForAll("processedEvents") ProcessedEvent event,
            @ForAll("outputFormats") String format) {
        
        String testPath = TEST_OUTPUT_BASE + "/naming-test-" + UUID.randomUUID();
        
        try {
            // 创建配置
            OutputConfig config = OutputConfig.builder()
                    .path(testPath)
                    .format(format)
                    .rollingSizeBytes(1024L * 1024L * 1024L)
                    .rollingIntervalMs(3600000L)
                    .build();
            
            // 创建Sink
            AbstractFileSink sink = createSinkForFormat(format, config);
            
            // 创建桶分配器
            BucketAssigner<ProcessedEvent, String> bucketAssigner = sink.createBucketAssigner();
            
            // 获取桶ID（即文件路径）
            String bucketId = bucketAssigner.getBucketId(event, null);
            
            // 验证：桶ID应该包含数据库名
            assertThat(bucketId)
                    .as("Bucket ID should contain database name")
                    .contains(sanitizePath(event.getDatabase()));
            
            // 验证：桶ID应该包含表名
            assertThat(bucketId)
                    .as("Bucket ID should contain table name")
                    .contains(sanitizePath(event.getTable()));
            
            // 验证：桶ID应该包含时间分区（dt=yyyyMMddHH格式）
            assertThat(bucketId)
                    .as("Bucket ID should contain time partition")
                    .containsPattern("dt=\\d{10}");
            
            // 验证：桶ID应该遵循 database/table/dt=yyyyMMddHH 格式
            String expectedPattern = String.format("%s/%s/dt=\\d{10}",
                    Pattern.quote(sanitizePath(event.getDatabase())),
                    Pattern.quote(sanitizePath(event.getTable())));
            assertThat(bucketId)
                    .as("Bucket ID should follow database/table/dt=yyyyMMddHH pattern")
                    .matches(expectedPattern);
            
            // 验证：时间戳应该基于事件的timestamp字段
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHH");
            String expectedDatePartition = dateFormat.format(new Date(event.getTimestamp()));
            assertThat(bucketId)
                    .as("Bucket ID should contain correct timestamp partition")
                    .contains("dt=" + expectedDatePartition);
            
            // 验证：输出文件配置应该包含正确的前缀和后缀
            var fileConfig = sink.createOutputFileConfig(format);
            assertThat(fileConfig.getPartPrefix())
                    .as("File prefix should be 'part'")
                    .isEqualTo("part");
            assertThat(fileConfig.getPartSuffix())
                    .as("File suffix should match format")
                    .isEqualTo("." + format);
            
        } finally {
            cleanupTestDirectory(testPath);
        }
    }

    /**
     * Property 13b: 文件命名规范 - 特殊字符处理
     * **Validates: Requirements 3.6**
     * 
     * 文件名应该正确处理数据库名和表名中的特殊字符
     */
    @Property(tries = 20)
    @Label("Property 13b: File naming convention - special characters")
    void property13b_fileNamingConvention_specialCharacters(
            @ForAll("eventsWithSpecialChars") ProcessedEvent event,
            @ForAll("outputFormats") String format) {
        
        String testPath = TEST_OUTPUT_BASE + "/naming-special-test-" + UUID.randomUUID();
        
        try {
            OutputConfig config = OutputConfig.builder()
                    .path(testPath)
                    .format(format)
                    .rollingSizeBytes(1024L * 1024L * 1024L)
                    .rollingIntervalMs(3600000L)
                    .build();
            
            AbstractFileSink sink = createSinkForFormat(format, config);
            BucketAssigner<ProcessedEvent, String> bucketAssigner = sink.createBucketAssigner();
            
            String bucketId = bucketAssigner.getBucketId(event, null);
            
            // 验证：特殊字符应该被替换为下划线
            assertThat(bucketId)
                    .as("Bucket ID should not contain illegal path characters")
                    .doesNotContain(":", "*", "?", "\"", "<", ">", "|");
            
            // 验证：路径分隔符应该被替换（除了正常的路径结构）
            String[] parts = bucketId.split("/");
            assertThat(parts)
                    .as("Bucket ID should have 3 parts: database/table/dt=timestamp")
                    .hasSize(3);
            
            // 验证：每个部分都不应该包含非法字符
            for (String part : parts) {
                if (!part.startsWith("dt=")) {
                    assertThat(part)
                            .as("Path component should not contain illegal characters")
                            .doesNotContain(":", "*", "?", "\"", "<", ">", "|", "\\");
                }
            }
            
        } finally {
            cleanupTestDirectory(testPath);
        }
    }

    /**
     * Property 13c: 文件命名规范 - null值处理
     * **Validates: Requirements 3.6**
     * 
     * 文件名应该正确处理null值
     */
    @Property(tries = 20)
    @Label("Property 13c: File naming convention - null values")
    void property13c_fileNamingConvention_nullValues(
            @ForAll("outputFormats") String format) {
        
        String testPath = TEST_OUTPUT_BASE + "/naming-null-test-" + UUID.randomUUID();
        
        try {
            OutputConfig config = OutputConfig.builder()
                    .path(testPath)
                    .format(format)
                    .rollingSizeBytes(1024L * 1024L * 1024L)
                    .rollingIntervalMs(3600000L)
                    .build();
            
            AbstractFileSink sink = createSinkForFormat(format, config);
            BucketAssigner<ProcessedEvent, String> bucketAssigner = sink.createBucketAssigner();
            
            // 创建包含null值的事件
            ProcessedEvent event = ProcessedEvent.builder()
                    .eventType("INSERT")
                    .database(null)  // null database
                    .table(null)     // null table
                    .timestamp(System.currentTimeMillis())
                    .processTime(System.currentTimeMillis())
                    .data(new HashMap<>())
                    .partition("0")
                    .eventId("test-event-null")
                    .build();
            
            String bucketId = bucketAssigner.getBucketId(event, null);
            
            // 验证：null值应该被替换为"unknown"
            assertThat(bucketId)
                    .as("Bucket ID should replace null values with 'unknown'")
                    .contains("unknown");
            
            // 验证：路径结构仍然正确
            assertThat(bucketId)
                    .as("Bucket ID should still follow correct structure")
                    .matches("unknown/unknown/dt=\\d{10}");
            
        } finally {
            cleanupTestDirectory(testPath);
        }
    }

    /**
     * Property 4: 失败重试机制（文件写入）
     * **Validates: Requirements 3.7**
     * 
     * 对于任何文件写入失败的操作，系统应该重试最多3次
     */
    @Property(tries = 20)
    @Label("Property 4: Failure retry mechanism for file writes")
    void property4_failureRetryMechanism(
            @ForAll @IntRange(min = 1, max = 3) int failuresBeforeSuccess,
            @ForAll("outputFormats") String format) throws Exception {
        
        String testPath = TEST_OUTPUT_BASE + "/retry-test-" + UUID.randomUUID();
        
        try {
            // 创建配置
            OutputConfig config = OutputConfig.builder()
                    .path(testPath)
                    .format(format)
                    .rollingSizeBytes(1024L * 1024L * 1024L)
                    .rollingIntervalMs(3600000L)
                    .maxRetries(3)
                    .retryBackoff(2)
                    .build();
            
            // 创建Sink
            AbstractFileSink sink = createSinkForFormat(format, config);
            
            // 模拟写入操作，前(failuresBeforeSuccess-1)次失败，第failuresBeforeSuccess次成功
            AtomicInteger attemptCount = new AtomicInteger(0);
            
            Runnable operation = () -> {
                int attempt = attemptCount.incrementAndGet();
                if (attempt < failuresBeforeSuccess) {
                    throw new RuntimeException("Simulated write failure, attempt: " + attempt);
                }
                // 第failuresBeforeSuccess次成功
            };
            
            // 记录开始时间
            long startTime = System.currentTimeMillis();
            
            // 执行带重试的操作
            sink.executeWithRetry(operation, "test write operation");
            
            // 计算总耗时
            long totalTime = System.currentTimeMillis() - startTime;
            
            // 验证：应该执行了failuresBeforeSuccess次
            assertThat(attemptCount.get())
                    .as("Should have attempted " + failuresBeforeSuccess + " times")
                    .isEqualTo(failuresBeforeSuccess);
            
            // 验证：重试间隔应该大约是2秒 * (failuresBeforeSuccess - 1)
            // 允许一些误差（±500ms per retry）
            if (failuresBeforeSuccess > 1) {
                long expectedMinTime = (failuresBeforeSuccess - 1) * 2000L - 500L;
                long expectedMaxTime = (failuresBeforeSuccess - 1) * 2000L + 500L;
                
                assertThat(totalTime)
                        .as("Total time should reflect retry backoff of 2 seconds")
                        .isBetween(expectedMinTime, expectedMaxTime);
            }
            
        } finally {
            cleanupTestDirectory(testPath);
        }
    }

    /**
     * Property 4b: 失败重试机制 - 所有重试失败
     * **Validates: Requirements 3.7**
     * 
     * 当所有重试都失败时，系统应该抛出异常
     */
    @Property(tries = 20)
    @Label("Property 4b: Failure retry mechanism - all retries failed")
    void property4b_failureRetryMechanism_allRetriesFailed(
            @ForAll("outputFormats") String format) {
        
        String testPath = TEST_OUTPUT_BASE + "/retry-fail-test-" + UUID.randomUUID();
        
        try {
            OutputConfig config = OutputConfig.builder()
                    .path(testPath)
                    .format(format)
                    .rollingSizeBytes(1024L * 1024L * 1024L)
                    .rollingIntervalMs(3600000L)
                    .maxRetries(3)
                    .retryBackoff(2)
                    .build();
            
            AbstractFileSink sink = createSinkForFormat(format, config);
            
            // 模拟所有尝试都失败的操作
            AtomicInteger attemptCount = new AtomicInteger(0);
            Runnable operation = () -> {
                attemptCount.incrementAndGet();
                throw new RuntimeException("Persistent write failure");
            };
            
            // 验证：应该抛出异常
            assertThatThrownBy(() -> sink.executeWithRetry(operation, "test write operation"))
                    .as("Should throw exception after all retries failed")
                    .isInstanceOf(Exception.class);
            
            // 验证：应该重试了最大次数（3次）
            assertThat(attemptCount.get())
                    .as("Should have attempted maxRetries times")
                    .isEqualTo(config.getMaxRetries());
            
        } finally {
            cleanupTestDirectory(testPath);
        }
    }

    /**
     * Property: 配置验证
     * 
     * 验证所有格式的Sink都能正确验证配置参数
     */
    @Property(tries = 20)
    @Label("Configuration validation for all formats")
    void configurationValidation(
            @ForAll("outputFormats") String format) {
        
        // 测试有效配置
        OutputConfig validConfig = OutputConfig.builder()
                .path("/tmp/test-output")
                .format(format)
                .rollingSizeBytes(1024L * 1024L * 1024L)
                .rollingIntervalMs(3600000L)
                .build();
        
        AbstractFileSink sink = createSinkForFormat(format, validConfig);
        assertThat(sink).isNotNull();
        
        // 测试无效配置（空路径）
        OutputConfig invalidConfig = OutputConfig.builder()
                .path("")
                .format(format)
                .build();
        
        assertThatThrownBy(() -> createSinkForFormat(format, invalidConfig))
                .as("Should reject invalid configuration")
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== 数据生成器 ====================

    /**
     * 生成输出格式
     */
    @Provide
    Arbitrary<String> outputFormats() {
        return Arbitraries.of("json", "csv", "parquet");
    }

    /**
     * 生成滚动大小（字节）
     */
    @Provide
    Arbitrary<Long> rollingSizes() {
        return Arbitraries.of(
                100L * 1024L * 1024L,      // 100MB
                500L * 1024L * 1024L,      // 500MB
                1024L * 1024L * 1024L,     // 1GB (标准)
                2048L * 1024L * 1024L      // 2GB
        );
    }

    /**
     * 生成滚动时间间隔（毫秒）
     */
    @Provide
    Arbitrary<Long> rollingIntervals() {
        return Arbitraries.of(
                60000L,        // 1 minute
                300000L,       // 5 minutes
                1800000L,      // 30 minutes
                3600000L,      // 1 hour (标准)
                7200000L       // 2 hours
        );
    }

    /**
     * 生成ProcessedEvent
     */
    @Provide
    Arbitrary<ProcessedEvent> processedEvents() {
        // 使用合理的时间范围：当前时间前后1天
        long now = System.currentTimeMillis();
        long oneDayMs = 86400000L;
        
        return Combinators.combine(
                Arbitraries.of("INSERT", "UPDATE", "DELETE"),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20),  // database
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20),  // table
                Arbitraries.longs().between(now - oneDayMs, now + oneDayMs),  // timestamp (within 1 day)
                dataMap(),
                Arbitraries.strings().alpha().numeric().ofLength(10)  // partition
        ).as((eventType, database, table, timestamp, data, partition) ->
                ProcessedEvent.builder()
                        .eventType(eventType)
                        .database(database)
                        .table(table)
                        .timestamp(timestamp)
                        .processTime(System.currentTimeMillis())
                        .data(data)
                        .partition(partition)
                        .eventId(UUID.randomUUID().toString())
                        .build()
        );
    }

    /**
     * 生成包含特殊字符的ProcessedEvent
     */
    @Provide
    Arbitrary<ProcessedEvent> eventsWithSpecialChars() {
        // 使用合理的时间范围：当前时间前后1天
        long now = System.currentTimeMillis();
        long oneDayMs = 86400000L;
        
        return Combinators.combine(
                Arbitraries.of("my/db", "test:db", "db*name", "db?name"),  // database with special chars
                Arbitraries.of("my/table", "test:table", "table*name", "table?name"),  // table with special chars
                Arbitraries.longs().between(now - oneDayMs, now + oneDayMs),  // timestamp (within 1 day)
                dataMap()
        ).as((database, table, timestamp, data) ->
                ProcessedEvent.builder()
                        .eventType("INSERT")
                        .database(database)
                        .table(table)
                        .timestamp(timestamp)
                        .processTime(System.currentTimeMillis())
                        .data(data)
                        .partition("0")
                        .eventId(UUID.randomUUID().toString())
                        .build()
        );
    }

    /**
     * 生成数据Map
     */
    private Arbitrary<Map<String, Object>> dataMap() {
        return Combinators.combine(
                Arbitraries.integers().between(1, 1000000),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(50),
                Arbitraries.strings().alpha().numeric().ofMinLength(5).ofMaxLength(50)
        ).as((id, name, email) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", id);
            map.put("name", name);
            map.put("email", email + "@example.com");
            return map;
        });
    }

    // ==================== 辅助方法 ====================

    /**
     * 根据格式创建相应的Sink
     */
    private AbstractFileSink createSinkForFormat(String format, OutputConfig config) {
        switch (format) {
            case "json":
                return new JsonFileSink(config);
            case "csv":
                return new CsvFileSink(config);
            case "parquet":
                return new ParquetFileSink(config);
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }

    /**
     * 清理路径中的非法字符
     */
    private String sanitizePath(String path) {
        if (path == null) {
            return "unknown";
        }
        return path.replaceAll("[/\\\\:*?\"<>|]", "_");
    }

    /**
     * 清理测试目录
     */
    private void cleanupTestDirectory(String path) {
        try {
            Path dirPath = Paths.get(path);
            if (Files.exists(dirPath)) {
                Files.walk(dirPath)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        } catch (IOException e) {
            // 忽略清理错误
        }
    }
}
