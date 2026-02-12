package com.realtime.pipeline;

import com.realtime.pipeline.config.DatabaseConfig;
import com.realtime.pipeline.config.OutputConfig;
import com.realtime.pipeline.model.ChangeEvent;
import com.realtime.pipeline.model.ProcessedEvent;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.configuration.MemorySize;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 简化的 CDC 应用程序
 * 功能：监控数据库表变更并生成 CSV 文件
 * 使用项目中已有的配置类和模型类
 */
public class SimpleCDCApp {
    private static final Logger logger = LoggerFactory.getLogger(SimpleCDCApp.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) throws Exception {
        logger.info("启动简化 CDC 应用程序...");

        // 使用 DatabaseConfig 读取配置
        DatabaseConfig dbConfig = loadDatabaseConfig();
        OutputConfig outputConfig = loadOutputConfig();

        logger.info("配置信息:");
        logger.info("  数据库: {}:{}", dbConfig.getHost(), dbConfig.getPort());
        logger.info("  Schema: {}", dbConfig.getSchema());
        logger.info("  表: {}", dbConfig.getTables());
        logger.info("  输出路径: {}", outputConfig.getPath());

        // 使用 FlinkEnvironmentConfigurator 创建执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        env.enableCheckpointing(60000);

        // 创建模拟的 CDC 数据源，生成 ChangeEvent
        DataStream<ChangeEvent> cdcStream = env.addSource(
            new MockCDCSource(dbConfig)
        ).name("CDC Source");

        // 转换为 ProcessedEvent
        DataStream<ProcessedEvent> processedStream = cdcStream
            .map(event -> convertToProcessedEvent(event))
            .name("Convert to ProcessedEvent");

        // 转换为 CSV 格式
        DataStream<String> csvStream = processedStream
            .map(event -> convertToCSV(event))
            .name("Convert to CSV");

        // 写入 CSV 文件
        FileSink<String> fileSink = FileSink
            .forRowFormat(new Path(outputConfig.getPath()), new SimpleStringEncoder<String>("UTF-8"))
            .withRollingPolicy(
                DefaultRollingPolicy.builder()
                    .withRolloverInterval(Duration.ofMinutes(5))
                    .withInactivityInterval(Duration.ofMinutes(2))
                    .withMaxPartSize(MemorySize.ofMebiBytes(128))
                    .build()
            )
            .build();

        csvStream.sinkTo(fileSink).name("CSV File Sink");

        // 执行作业
        logger.info("提交 Flink 作业...");
        env.execute("Simple CDC to CSV Application");
    }

    /**
     * 从环境变量加载数据库配置
     */
    private static DatabaseConfig loadDatabaseConfig() {
        String tablesStr = getEnv("DATABASE_TABLES", "trans_info");
        List<String> tables = Arrays.asList(tablesStr.split(","));

        return DatabaseConfig.builder()
            .host(getEnv("DATABASE_HOST", "localhost"))
            .port(Integer.parseInt(getEnv("DATABASE_PORT", "1521")))
            .username(getEnv("DATABASE_USERNAME", "finance_user"))
            .password(getEnv("DATABASE_PASSWORD", "password"))
            .schema(getEnv("DATABASE_SCHEMA", "helowin"))
            .tables(tables)
            .connectionTimeout(30)
            .reconnectInterval(30)
            .build();
    }

    /**
     * 从环境变量加载输出配置
     */
    private static OutputConfig loadOutputConfig() {
        return OutputConfig.builder()
            .path(getEnv("OUTPUT_PATH", "./output/cdc"))
            .format("csv")
            .rollingSizeBytes(128L * 1024L * 1024L)
            .rollingIntervalMs(5L * 60L * 1000L)
            .compression("none")
            .maxRetries(3)
            .retryBackoff(2)
            .build();
    }

    /**
     * 将 ChangeEvent 转换为 ProcessedEvent
     */
    private static ProcessedEvent convertToProcessedEvent(ChangeEvent event) {
        return ProcessedEvent.builder()
            .eventType(event.getEventType())
            .database(event.getDatabase())
            .table(event.getTable())
            .timestamp(event.getTimestamp())
            .processTime(System.currentTimeMillis())
            .data(event.getData())
            .partition(null)
            .eventId(event.getEventId())
            .build();
    }

    /**
     * 将 ProcessedEvent 转换为 CSV 格式
     */
    private static String convertToCSV(ProcessedEvent event) {
        StringBuilder csv = new StringBuilder();
        csv.append(LocalDateTime.now().format(TIMESTAMP_FORMAT)).append(",");
        csv.append(event.getTable()).append(",");
        csv.append(event.getEventType()).append(",");
        
        // 添加数据字段
        if (event.getData() != null) {
            event.getData().forEach((key, value) -> {
                csv.append("\"").append(key).append("=").append(value).append("\",");
            });
        }
        
        return csv.toString();
    }

    /**
     * 获取环境变量
     */
    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null && !value.isEmpty() ? value : defaultValue;
    }

    /**
     * 模拟的 CDC 数据源
     * 生成 ChangeEvent 对象
     */
    private static class MockCDCSource implements SourceFunction<ChangeEvent> {
        private static final long serialVersionUID = 1L;
        private final DatabaseConfig config;
        private volatile boolean running = true;

        public MockCDCSource(DatabaseConfig config) {
            this.config = config;
        }

        @Override
        public void run(SourceContext<ChangeEvent> ctx) throws Exception {
            logger.info("CDC Source 已启动，监控表: {}.{}", config.getSchema(), config.getTables());
            logger.info("注意：这是模拟数据源，实际应用请使用 Flink CDC Connector");

            int counter = 0;
            Random random = new Random();
            
            while (running) {
                // 随机选择一个表
                String table = config.getTables().get(random.nextInt(config.getTables().size()));
                
                // 随机选择操作类型
                String[] operations = {"INSERT", "UPDATE", "DELETE"};
                String operation = operations[random.nextInt(operations.length)];
                
                // 创建模拟数据
                Map<String, Object> data = new HashMap<>();
                data.put("id", counter);
                data.put("name", "sample_name_" + counter);
                data.put("value", random.nextInt(1000));
                data.put("timestamp", System.currentTimeMillis());
                
                // 创建 ChangeEvent
                ChangeEvent event = ChangeEvent.builder()
                    .eventType(operation)
                    .database(config.getSchema())
                    .table(table)
                    .timestamp(System.currentTimeMillis())
                    .before(operation.equals("INSERT") ? null : data)
                    .after(operation.equals("DELETE") ? null : data)
                    .primaryKeys(Collections.singletonList("id"))
                    .eventId(UUID.randomUUID().toString())
                    .build();
                
                ctx.collect(event);
                counter++;

                // 每5秒生成一条记录
                Thread.sleep(5000);
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }
}
