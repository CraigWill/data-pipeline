package com.realtime.pipeline;

import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.configuration.MemorySize;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;
import org.apache.flink.streaming.api.functions.source.RichSourceFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * 基于 JDBC 的 CDC 应用程序
 * 使用轮询方式监控数据库表变更并生成 CSV 文件
 */
public class JdbcCDCApp {
    private static final Logger logger = LoggerFactory.getLogger(JdbcCDCApp.class);

    public static void main(String[] args) throws Exception {
        logger.info("启动 JDBC CDC 应用程序...");

        // 读取环境变量
        Config config = Config.fromEnv();
        config.print();

        // 创建 Flink 执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        env.enableCheckpointing(60000);

        // 创建 JDBC CDC 数据源
        DataStream<String> cdcStream = env.addSource(
            new JdbcCDCSource(config)
        ).name("JDBC CDC Source");

        // 写入 CSV 文件
        FileSink<String> fileSink = FileSink
            .forRowFormat(new Path(config.outputPath), new SimpleStringEncoder<String>("UTF-8"))
            .withRollingPolicy(
                DefaultRollingPolicy.builder()
                    .withRolloverInterval(Duration.ofMinutes(5))
                    .withInactivityInterval(Duration.ofMinutes(2))
                    .withMaxPartSize(MemorySize.ofMebiBytes(128))
                    .build()
            )
            .build();

        cdcStream.sinkTo(fileSink).name("CSV File Sink");

        // 执行作业
        logger.info("提交 Flink 作业...");
        env.execute("JDBC CDC to CSV Application");
    }

    /**
     * 配置类
     */
    static class Config implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        String dbHost;
        String dbPort;
        String dbUser;
        String dbPassword;
        String dbSid;
        String dbSchema;
        String dbTables;
        String outputPath;
        int pollIntervalSeconds;

        static Config fromEnv() {
            Config config = new Config();
            config.dbHost = getEnv("DATABASE_HOST", "localhost");
            config.dbPort = getEnv("DATABASE_PORT", "1521");
            config.dbUser = getEnv("DATABASE_USERNAME", "finance_user");
            config.dbPassword = getEnv("DATABASE_PASSWORD", "password");
            config.dbSid = getEnv("DATABASE_SID", "helowin");
            config.dbSchema = getEnv("DATABASE_SCHEMA", "finance_user");
            config.dbTables = getEnv("DATABASE_TABLES", "trans_info");
            config.outputPath = getEnv("OUTPUT_PATH", "./output/cdc");
            config.pollIntervalSeconds = Integer.parseInt(getEnv("POLL_INTERVAL_SECONDS", "10"));
            return config;
        }

        void print() {
            logger.info("配置信息:");
            logger.info("  数据库: {}:{}", dbHost, dbPort);
            logger.info("  用户: {}", dbUser);
            logger.info("  Schema: {}", dbSchema);
            logger.info("  表: {}", dbTables);
            logger.info("  输出路径: {}", outputPath);
            logger.info("  轮询间隔: {}秒", pollIntervalSeconds);
        }

        private static String getEnv(String key, String defaultValue) {
            String value = System.getenv(key);
            return value != null && !value.isEmpty() ? value : defaultValue;
        }
    }

    /**
     * JDBC CDC 数据源
     * 使用轮询方式监控表变更
     */
    static class JdbcCDCSource extends RichSourceFunction<String> {
        private static final Logger logger = LoggerFactory.getLogger(JdbcCDCSource.class);
        private static final DateTimeFormatter CSV_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        private final Config config;
        private volatile boolean running = true;
        private Connection connection;
        private long lastMaxId = 0;

        public JdbcCDCSource(Config config) {
            this.config = config;
        }

        @Override
        public void open(org.apache.flink.configuration.Configuration parameters) throws Exception {
            super.open(parameters);
            
            // 建立数据库连接
            String jdbcUrl = String.format("jdbc:oracle:thin:@%s:%s:%s", 
                config.dbHost, config.dbPort, config.dbSid);
            
            logger.info("连接到数据库: {}", jdbcUrl);
            
            Properties props = new Properties();
            props.setProperty("user", config.dbUser);
            props.setProperty("password", config.dbPassword);
            
            try {
                Class.forName("oracle.jdbc.OracleDriver");
                connection = DriverManager.getConnection(jdbcUrl, props);
                logger.info("✅ 数据库连接成功");
                
                // 获取当前最大 ID
                initializeLastMaxId();
            } catch (Exception e) {
                logger.error("❌ 数据库连接失败: {}", e.getMessage());
                logger.info("将使用模拟数据模式");
            }
        }

        private void initializeLastMaxId() {
            if (connection == null) return;
            
            String[] tables = config.dbTables.split(",");
            for (String table : tables) {
                table = table.trim();
                try {
                    // 获取表的行数作为起始点
                    String sql = String.format(
                        "SELECT COUNT(*) as row_count FROM %s.%s", 
                        config.dbSchema, table
                    );
                    
                    try (Statement stmt = connection.createStatement();
                         ResultSet rs = stmt.executeQuery(sql)) {
                        if (rs.next()) {
                            lastMaxId = rs.getLong("row_count");
                            logger.info("表 {} 当前行数: {}", table, lastMaxId);
                        }
                    }
                } catch (SQLException e) {
                    logger.warn("无法获取表 {} 的行数: {}", table, e.getMessage());
                }
            }
        }

        @Override
        public void run(SourceContext<String> ctx) throws Exception {
            logger.info("CDC Source 已启动，监控表: {}.{}", config.dbSchema, config.dbTables);
            
            if (connection == null) {
                runMockMode(ctx);
                return;
            }
            
            runRealMode(ctx);
        }

        private void runRealMode(SourceContext<String> ctx) throws Exception {
            String[] tables = config.dbTables.split(",");
            
            while (running) {
                for (String table : tables) {
                    table = table.trim();
                    pollTableChanges(ctx, table);
                }
                
                Thread.sleep(config.pollIntervalSeconds * 1000L);
            }
        }

        private void pollTableChanges(SourceContext<String> ctx, String table) {
            try {
                // 查询表中的所有数据
                String sql = String.format(
                    "SELECT * FROM %s.%s",
                    config.dbSchema, table
                );
                
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();
                    long currentRowCount = 0;
                    
                    while (rs.next()) {
                        currentRowCount++;
                        
                        // 只处理新增的行
                        if (currentRowCount <= lastMaxId) {
                            continue;
                        }
                        
                        StringBuilder csv = new StringBuilder();
                        csv.append(LocalDateTime.now().format(CSV_TIMESTAMP));
                        csv.append(",").append(table);
                        csv.append(",INSERT");
                        
                        for (int i = 1; i <= columnCount; i++) {
                            csv.append(",");
                            Object value = rs.getObject(i);
                            if (value != null) {
                                String strValue = value.toString().replace(",", ";");
                                csv.append("\"").append(strValue).append("\"");
                            }
                        }
                        
                        ctx.collect(csv.toString());
                    }
                    
                    // 更新最后处理的行数
                    if (currentRowCount > lastMaxId) {
                        logger.info("表 {} 检测到 {} 条新记录", table, currentRowCount - lastMaxId);
                        lastMaxId = currentRowCount;
                    }
                }
            } catch (SQLException e) {
                logger.error("轮询表 {} 时出错: {}", table, e.getMessage());
            }
        }

        private void runMockMode(SourceContext<String> ctx) throws Exception {
            logger.info("⚠️  运行在模拟数据模式");
            
            int counter = 0;
            while (running) {
                String csv = String.format("%s,%s,INSERT,%d,\"sample_data_%d\"",
                    LocalDateTime.now().format(CSV_TIMESTAMP),
                    config.dbTables,
                    counter,
                    counter
                );
                
                ctx.collect(csv);
                counter++;
                
                Thread.sleep(config.pollIntervalSeconds * 1000L);
            }
        }

        @Override
        public void cancel() {
            running = false;
            if (connection != null) {
                try {
                    connection.close();
                    logger.info("数据库连接已关闭");
                } catch (SQLException e) {
                    logger.error("关闭数据库连接时出错: {}", e.getMessage());
                }
            }
        }
    }
}
