package com.realtime.pipeline;

import com.ververica.cdc.connectors.oracle.OracleSource;
import com.ververica.cdc.debezium.JsonDebeziumDeserializationSchema;
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
import java.util.Properties;

/**
 * 基于 Flink CDC 的 Oracle LogMiner CDC 应用程序
 * 使用 Oracle LogMiner 实时捕获数据库变更
 * 
 * 需要在 Oracle 数据库中启用归档日志模式：
 * 1. 以 SYSDBA 身份连接: sqlplus / as sysdba
 * 2. 关闭数据库: shutdown immediate
 * 3. 启动到 mount 状态: startup mount
 * 4. 启用归档日志: alter database archivelog;
 * 5. 打开数据库: alter database open;
 * 6. 启用补充日志: ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;
 */
public class OracleCDCApp {
    private static final Logger logger = LoggerFactory.getLogger(OracleCDCApp.class);

    public static void main(String[] args) throws Exception {
        logger.info("启动 Oracle CDC 应用程序（基于 LogMiner）...");

        // 读取环境变量
        Config config = Config.fromEnv();
        config.print();

        // 创建 Flink 执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        env.enableCheckpointing(60000);

        // 创建 Oracle CDC Source（使用 LogMiner）
        SourceFunction<String> oracleSource = createOracleSource(config);

        // 创建数据流并转换为CSV格式
        DataStream<String> cdcStream = env.addSource(oracleSource)
            .name("Oracle CDC Source (LogMiner)")
            .map(jsonStr -> {
                // 将JSON转换为CSV格式
                // JSON格式示例: {"before":null,"after":{"id":1,"name":"test"},"op":"c","ts_ms":1234567890}
                try {
                    // CSV格式: timestamp,table,operation,json_data
                    String timestamp = String.valueOf(System.currentTimeMillis());
                    // 从JSON中提取操作类型
                    String operation = "UNKNOWN";
                    if (jsonStr.contains("\"op\":\"c\"")) operation = "INSERT";
                    else if (jsonStr.contains("\"op\":\"u\"")) operation = "UPDATE";
                    else if (jsonStr.contains("\"op\":\"d\"")) operation = "DELETE";
                    else if (jsonStr.contains("\"op\":\"r\"")) operation = "READ";
                    
                    // 转义CSV中的引号
                    String escapedData = jsonStr.replace("\"", "\"\"");
                    
                    // 记录捕获的事件
                    logger.info("捕获CDC事件: 操作={}, 表={}", operation, config.dbTables);
                    
                    return String.format("%s,%s,%s,\"%s\"", timestamp, config.dbTables, operation, escapedData);
                } catch (Exception e) {
                    logger.error("转换JSON到CSV失败: {}", jsonStr, e);
                    return jsonStr;
                }
            })
            .name("JSON to CSV Converter");

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
        env.execute("Oracle CDC Application (LogMiner)");
    }

    /**
     * 创建 Oracle CDC Source
     */
    private static SourceFunction<String> createOracleSource(Config config) {
        Properties debeziumProps = new Properties();
        
        // Debezium 配置
        debeziumProps.setProperty("decimal.handling.mode", "string");
        debeziumProps.setProperty("bigint.unsigned.handling.mode", "long");
        
        // 数据库连接配置 - 禁用自动提交
        debeziumProps.setProperty("database.connection.adapter", "logminer");
        debeziumProps.setProperty("database.autocommit", "false");
        
        // LogMiner 配置
        debeziumProps.setProperty("log.mining.strategy", "online_catalog");
        debeziumProps.setProperty("log.mining.continuous.mine", "true");
        
        // Snapshot 配置 - 跳过 snapshot，直接进入 streaming 模式
        debeziumProps.setProperty("snapshot.mode", "schema_only");  // 只捕获 schema，不捕获数据
        
        // 构建 Oracle JDBC URL
        String jdbcUrl = String.format("jdbc:oracle:thin:@%s:%s:%s", 
            config.dbHost, config.dbPort, config.dbSid);
        
        logger.info("Oracle JDBC URL: {}", jdbcUrl);
        logger.info("监控 Schema: {}", config.dbSchema);
        logger.info("监控表: {}", config.dbTables);

        // 创建 Oracle CDC Source
        // 注意：根据实际的 Flink CDC 版本，API 可能有所不同
        // 只监控指定的表，格式：SCHEMA.TABLE
        String tableList = config.dbSchema + "." + config.dbTables;
        
        logger.info("创建 Oracle CDC Source:");
        logger.info("  - 主机: {}", config.dbHost);
        logger.info("  - 端口: {}", config.dbPort);
        logger.info("  - 数据库: {}", config.dbSid);
        logger.info("  - Schema列表: {}", config.dbSchema);
        logger.info("  - 表列表: {}", tableList);
        
        return OracleSource.<String>builder()
            .hostname(config.dbHost)
            .port(Integer.parseInt(config.dbPort))
            .database(config.dbSid)
            .tableList(tableList)  // 只监控指定的表，不使用 schemaList
            .username(config.dbUser)
            .password(config.dbPassword)
            .deserializer(new JsonDebeziumDeserializationSchema())
            .debeziumProperties(debeziumProps)
            .build();
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

        static Config fromEnv() {
            Config config = new Config();
            config.dbHost = getEnv("DATABASE_HOST", "localhost");
            config.dbPort = getEnv("DATABASE_PORT", "1521");
            config.dbUser = getEnv("DATABASE_USERNAME", "system");
            config.dbPassword = getEnv("DATABASE_PASSWORD", "password");
            config.dbSid = getEnv("DATABASE_SID", "helowin");
            config.dbSchema = getEnv("DATABASE_SCHEMA", "finance_user");
            config.dbTables = getEnv("DATABASE_TABLES", "trans_info");
            config.outputPath = getEnv("OUTPUT_PATH", "/opt/flink/output/cdc");
            return config;
        }

        void print() {
            logger.info("配置信息:");
            logger.info("  数据库: {}:{}", dbHost, dbPort);
            logger.info("  SID: {}", dbSid);
            logger.info("  用户: {}", dbUser);
            logger.info("  Schema: {}", dbSchema);
            logger.info("  表: {}", dbTables);
            logger.info("  输出路径: {}", outputPath);
        }

        private static String getEnv(String key, String defaultValue) {
            String value = System.getenv(key);
            return value != null && !value.isEmpty() ? value : defaultValue;
        }
    }
}
