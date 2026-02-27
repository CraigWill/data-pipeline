package com.realtime.pipeline;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.cdc.connectors.base.options.StartupOptions;
import org.apache.flink.cdc.connectors.base.source.jdbc.JdbcIncrementalSource;
import org.apache.flink.cdc.connectors.oracle.source.OracleSourceBuilder;
import org.apache.flink.cdc.debezium.JsonDebeziumDeserializationSchema;
import org.apache.flink.configuration.MemorySize;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.filesystem.OutputFileConfig;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Flink CDC 3.x Oracle Application
 * 
 * 使用 Flink CDC 3.x 的新 API 捕获 Oracle 数据库变更
 * 
 * 主要改进:
 * 1. 使用新的 OracleSourceBuilder API
 * 2. 支持并行读取 (Incremental Snapshot)
 * 3. 更好的性能和稳定性
 * 4. 改进的表过滤机制
 * 
 * 架构: Oracle (LogMiner) → Flink CDC 3.x → CSV Files
 */
public class FlinkCDC3App {
    private static final Logger LOG = LoggerFactory.getLogger(FlinkCDC3App.class);
    private static final DateTimeFormatter CSV_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) throws Exception {
        LOG.info("=== Flink CDC 3.x Oracle Application ===");
        
        // 显式注册 Oracle JDBC 驱动
        try {
            Class.forName("oracle.jdbc.OracleDriver");
            LOG.info("Oracle JDBC Driver registered successfully");
        } catch (ClassNotFoundException e) {
            LOG.error("Failed to register Oracle JDBC Driver", e);
            throw new RuntimeException("Oracle JDBC Driver not found", e);
        }

        // 读取配置
        Config config = Config.fromEnv();
        config.print();

        // 创建 Flink 执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // 设置并行度
        env.setParallelism(config.parallelism);
        
        // 生产级 Checkpoint 配置（更频繁，更可靠）
        env.enableCheckpointing(30000);  // 30秒一次 checkpoint
        
        // Checkpoint 高级配置
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(5000);  // 最小间隔5秒
        env.getCheckpointConfig().setCheckpointTimeout(180000);  // 超时3分钟
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);  // 同时只有1个 checkpoint
        env.getCheckpointConfig().setTolerableCheckpointFailureNumber(5);  // 容忍5次失败
        
        // Checkpoint 保留策略（作业取消时保留）
        env.getCheckpointConfig().setExternalizedCheckpointCleanup(
            org.apache.flink.streaming.api.environment.CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION
        );
        
        // 生产级重启策略（固定延迟，无限重试）
        env.setRestartStrategy(
            org.apache.flink.api.common.restartstrategy.RestartStrategies.fixedDelayRestart(
                Integer.MAX_VALUE,  // 无限重试
                org.apache.flink.api.common.time.Time.seconds(30)  // 每次重试间隔30秒
            )
        );

        // 创建 Oracle CDC Source (使用 Flink CDC 3.x API)
        JdbcIncrementalSource<String> oracleSource = createOracleSource(config);

        // 创建数据流
        DataStream<String> cdcStream = env
            .fromSource(
                oracleSource,
                WatermarkStrategy.noWatermarks(),
                "Oracle CDC 3.x Source"
            )
            .setParallelism(config.parallelism)
            .filter(jsonStr -> {
                // 应用层过滤：只保留我们关心的表
                try {
                    String tableName = extractTableName(jsonStr);
                    boolean shouldKeep = config.targetTables.contains(tableName.toUpperCase());
                    
                    if (shouldKeep) {
                        LOG.debug("保留事件: 表={}", tableName);
                    } else {
                        LOG.trace("过滤事件: 表={}", tableName);
                    }
                    
                    return shouldKeep;
                } catch (Exception e) {
                    LOG.warn("解析JSON失败，保留该事件: {}", jsonStr);
                    return true;
                }
            })
            .name("Application-Level Table Filter");

        // 为每个表创建独立的输出流和文件 Sink
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS"));
        
        for (String tableName : config.targetTables) {
            LOG.info("为表 {} 创建输出流", tableName);
            
            // 过滤出当前表的数据
            DataStream<String> tableStream = cdcStream
                .filter(jsonStr -> {
                    try {
                        String table = extractTableName(jsonStr);
                        return table.equalsIgnoreCase(tableName);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .name("Filter " + tableName)
                .map(jsonStr -> convertToCSV(jsonStr, tableName, config))
                .filter(csv -> csv != null)
                .name("JSON to CSV - " + tableName);
            
            // 为每个表创建独立的文件 Sink
            String filePrefix = "IDS_" + tableName.toUpperCase() + "_" + timestamp;
            
            FileSink<String> fileSink = FileSink
                .forRowFormat(
                    new Path(config.outputPath), 
                    new CsvEncoderWithHeader(tableName)
                )
                .withRollingPolicy(
                    DefaultRollingPolicy.builder()
                        .withRolloverInterval(Duration.ofSeconds(30))
                        .withInactivityInterval(Duration.ofSeconds(10))
                        .withMaxPartSize(MemorySize.ofMebiBytes(20))
                        .build()
                )
                .withOutputFileConfig(
                    OutputFileConfig.builder()
                        .withPartPrefix(filePrefix)
                        .withPartSuffix(".csv")
                        .build()
                )
                .build();
            
            tableStream.sinkTo(fileSink).name("CSV Sink - " + tableName);
        }

        // 执行作业
        LOG.info("提交 Flink CDC 3.x 作业...");
        LOG.info("监控表: {}", config.targetTables);
        LOG.info("输出路径: {}", config.outputPath);
        LOG.info("并行度: {}", config.parallelism);
        env.execute("Flink CDC 3.x Oracle Application");
    }

    /**
     * 创建 Oracle CDC Source (使用 Flink CDC 3.x API)
     */
    private static JdbcIncrementalSource<String> createOracleSource(Config config) {
        // Debezium 配置
        Properties debeziumProps = new Properties();
        debeziumProps.setProperty("log.mining.strategy", "online_catalog");
        debeziumProps.setProperty("log.mining.continuous.mine", "true");
        debeziumProps.setProperty("decimal.handling.mode", "string");
        debeziumProps.setProperty("time.precision.mode", "adaptive");
        
        // 生产级连接超时和重试配置
        debeziumProps.setProperty("database.connection.timeout.ms", "120000");  // 连接超时 2 分钟
        debeziumProps.setProperty("database.query.timeout.ms", "1800000");  // 查询超时 30 分钟
        debeziumProps.setProperty("connect.timeout.ms", "60000");  // TCP 连接超时 60 秒
        debeziumProps.setProperty("connect.keep.alive", "true");  // 启用 TCP keep-alive
        debeziumProps.setProperty("connect.keep.alive.interval.ms", "30000");  // keep-alive 间隔 30 秒
        
        // 增强的错误处理和重试
        debeziumProps.setProperty("errors.max.retries", "20");  // 最大重试次数
        debeziumProps.setProperty("errors.retry.delay.initial.ms", "2000");  // 初始重试延迟 2 秒
        debeziumProps.setProperty("errors.retry.delay.max.ms", "120000");  // 最大重试延迟 2 分钟
        debeziumProps.setProperty("errors.tolerance", "all");  // 容忍所有错误并重试
        
        // Oracle 网络优化
        debeziumProps.setProperty("database.tcpKeepAlive", "true");  // Oracle JDBC TCP keep-alive
        debeziumProps.setProperty("oracle.net.keepalive_time", "30");  // Oracle 网络 keep-alive 时间（分钟）
        
        // 生产级 LogMiner 会话配置
        debeziumProps.setProperty("log.mining.session.max.ms", "0");  // 禁用会话超时（0 = 无限制）
        debeziumProps.setProperty("log.mining.batch.size.default", "2000");  // 批量大小（增加以提高吞吐量）
        debeziumProps.setProperty("log.mining.batch.size.min", "500");  // 最小批量大小
        debeziumProps.setProperty("log.mining.batch.size.max", "20000");  // 最大批量大小
        debeziumProps.setProperty("log.mining.sleep.time.default.ms", "500");  // 默认休眠时间（减少延迟）
        debeziumProps.setProperty("log.mining.sleep.time.min.ms", "0");  // 最小休眠时间
        debeziumProps.setProperty("log.mining.sleep.time.max.ms", "5000");  // 最大休眠时间
        debeziumProps.setProperty("log.mining.sleep.time.increment.ms", "200");  // 休眠时间增量
        
        // LogMiner 故障恢复
        debeziumProps.setProperty("log.mining.restart.connection", "true");  // 连接失败时重启
        debeziumProps.setProperty("log.mining.transaction.retention.hours", "4");  // 事务保留时间
        
        // 构建表列表 (格式: SCHEMA.TABLE)
        String tableList = config.dbSchema + "." + config.dbTables;
        
        LOG.info("创建 Oracle CDC 3.x Source:");
        LOG.info("  主机: {}", config.dbHost);
        LOG.info("  端口: {}", config.dbPort);
        LOG.info("  数据库: {}", config.dbSid);
        LOG.info("  用户: {}", config.dbUser);
        LOG.info("  Schema列表: {}", config.dbSchema);
        LOG.info("  表列表: {}", tableList);
        LOG.info("  Startup模式: latest (只通过 redo log 捕获新变更，跳过 snapshot)");
        LOG.info("  Split大小: {}", config.splitSize);
        LOG.info("  连接超时: 60秒, 查询超时: 10分钟");
        LOG.info("  最大重试次数: 10");
        
        // 使用新的 OracleSourceBuilder API，指定泛型类型参数
        return new OracleSourceBuilder<String>()
            .hostname(config.dbHost)
            .port(Integer.parseInt(config.dbPort))
            .databaseList(config.dbSid)
            .schemaList(config.dbSchema)
            .tableList(tableList)
            .username(config.dbUser)
            .password(config.dbPassword)
            .deserializer(new JsonDebeziumDeserializationSchema())
            .includeSchemaChanges(false) // 不输出 schema 变更事件
            .startupOptions(StartupOptions.latest()) // 只通过 redo log 捕获新变更，跳过 snapshot
            .debeziumProperties(debeziumProps)
            .splitSize(config.splitSize) // 每个 split 的大小
            .build();
    }

    /**
     * 从 JSON 中提取表名
     */
    private static String extractTableName(String json) {
        int sourceIdx = json.indexOf("\"source\":");
        if (sourceIdx >= 0) {
            int tableIdx = json.indexOf("\"table\":", sourceIdx);
            if (tableIdx >= 0) {
                int start = json.indexOf("\"", tableIdx + 8) + 1;
                int end = json.indexOf("\"", start);
                if (start > 0 && end > start) {
                    return json.substring(start, end);
                }
            }
        }
        return "UNKNOWN";
    }

    /**
     * 转换 JSON 为 CSV 格式（支持多表动态字段）
     * 格式：CDC时间,操作类型,字段1,字段2,...
     */
    private static String convertToCSV(String jsonStr, String tableName, Config config) {
        try {
            // 提取操作类型
            String operation = "UNKNOWN";
            if (jsonStr.contains("\"op\":\"c\"")) operation = "INSERT";
            else if (jsonStr.contains("\"op\":\"u\"")) operation = "UPDATE";
            else if (jsonStr.contains("\"op\":\"d\"")) operation = "DELETE";
            else if (jsonStr.contains("\"op\":\"r\"")) operation = "READ";
            
            // 提取 after 数据（对于 INSERT 和 UPDATE）或 before 数据（对于 DELETE）
            String dataJson = null;
            if ("DELETE".equals(operation)) {
                dataJson = extractField(jsonStr, "before");
            } else {
                dataJson = extractField(jsonStr, "after");
            }
            
            if (dataJson == null || "null".equals(dataJson)) {
                LOG.warn("无法提取数据: {}", jsonStr);
                return null;
            }
            
            // 解析 JSON 数据为 Map
            Map<String, String> dataMap = parseJsonToMap(dataJson);
            
            // 使用当前时间作为 CDC 捕获时间
            String currentTime = LocalDateTime.now().format(CSV_TIMESTAMP);
            
            // 构建 CSV 行：CDC时间,操作类型,所有字段（按字母顺序）
            StringBuilder csv = new StringBuilder();
            csv.append(escapeCsvValue(currentTime)).append(",");
            csv.append(escapeCsvValue(operation));
            
            // 按字母顺序输出所有字段
            java.util.List<String> sortedKeys = new java.util.ArrayList<>(dataMap.keySet());
            java.util.Collections.sort(sortedKeys);
            
            for (String key : sortedKeys) {
                csv.append(",");
                csv.append(escapeCsvValue(dataMap.get(key)));
            }
            
            return csv.toString();
                
        } catch (Exception e) {
            LOG.error("转换JSON到CSV失败: {}", jsonStr, e);
            return null;
        }
    }
    
    /**
     * 解析 JSON 对象为 Map
     */
    private static Map<String, String> parseJsonToMap(String json) {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        if (json == null || json.equals("null") || json.length() < 2) {
            return map;
        }
        
        // 移除首尾的花括号
        String content = json.substring(1, json.length() - 1);
        
        // 简单的 JSON 解析（假设值不包含逗号和引号）
        boolean inQuotes = false;
        int start = 0;
        
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                String pair = content.substring(start, i).trim();
                parsePair(pair, map);
                start = i + 1;
            }
        }
        
        // 处理最后一对
        if (start < content.length()) {
            String pair = content.substring(start).trim();
            parsePair(pair, map);
        }
        
        return map;
    }
    
    /**
     * 解析键值对
     */
    private static void parsePair(String pair, Map<String, String> map) {
        int colonIdx = pair.indexOf(':');
        if (colonIdx > 0) {
            String key = pair.substring(0, colonIdx).trim().replace("\"", "");
            String value = pair.substring(colonIdx + 1).trim();
            
            // 移除值的引号
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            
            map.put(key, value);
        }
    }
    
    /**
     * 转义 CSV 值
     */
    private static String escapeCsvValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        
        // 如果包含逗号、引号或换行符，需要用引号包裹
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            // 转义引号
            value = value.replace("\"", "\"\"");
            return "\"" + value + "\"";
        }
        
        return value;
    }

    /**
     * 从 JSON 中提取字段
     */
    private static String extractField(String json, String fieldName) {
        String searchStr = "\"" + fieldName + "\":";
        int fieldIdx = json.indexOf(searchStr);
        if (fieldIdx >= 0) {
            int start = json.indexOf("{", fieldIdx);
            if (start < 0) {
                return "null";
            }
            int end = findMatchingBrace(json, start);
            if (end > start) {
                return json.substring(start, end + 1);
            }
        }
        return "null";
    }

    /**
     * 查找匹配的右花括号
     */
    private static int findMatchingBrace(String str, int start) {
        if (start < 0 || start >= str.length()) return -1;
        int count = 1;
        for (int i = start + 1; i < str.length(); i++) {
            if (str.charAt(i) == '{') count++;
            else if (str.charAt(i) == '}') {
                count--;
                if (count == 0) return i;
            }
        }
        return -1;
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
        int parallelism;
        int splitSize;
        Set<String> targetTables;

        static Config fromEnv() {
            Config config = new Config();
            config.dbHost = getEnv("DATABASE_HOST", "host.docker.internal");
            config.dbPort = getEnv("DATABASE_PORT", "1521");
            config.dbUser = getEnv("DATABASE_USERNAME", "system");
            config.dbPassword = getEnv("DATABASE_PASSWORD", "helowin");
            config.dbSid = getEnv("DATABASE_SID", "helowin");
            config.dbSchema = getEnv("DATABASE_SCHEMA", "FINANCE_USER");
            config.dbTables = getEnv("DATABASE_TABLES", "TRANS_INFO");
            config.outputPath = getEnv("OUTPUT_PATH", "./output/cdc");
            config.parallelism = Integer.parseInt(getEnv("PARALLELISM_DEFAULT", "2"));
            config.splitSize = Integer.parseInt(getEnv("CDC_SPLIT_SIZE", "8096"));
            
            // 解析目标表列表
            config.targetTables = new HashSet<>();
            for (String table : config.dbTables.split(",")) {
                config.targetTables.add(table.trim().toUpperCase());
            }
            
            return config;
        }

        void print() {
            LOG.info("配置信息:");
            LOG.info("  数据库: {}:{}/{}", dbHost, dbPort, dbSid);
            LOG.info("  用户: {}", dbUser);
            LOG.info("  Schema: {}", dbSchema);
            LOG.info("  表: {}", dbTables);
            LOG.info("  输出路径: {}", outputPath);
            LOG.info("  并行度: {}", parallelism);
            LOG.info("  Split大小: {}", splitSize);
        }

        private static String getEnv(String key, String defaultValue) {
            String value = System.getenv(key);
            return value != null && !value.isEmpty() ? value : defaultValue;
        }
    }
    
    /**
     * 自定义 CSV Encoder，在每个文件开始时写入动态标题行
     * 使用 ThreadLocal 来跟踪每个文件的标题写入状态
     */
    static class CsvEncoderWithHeader implements org.apache.flink.api.common.serialization.Encoder<String> {
        private static final long serialVersionUID = 1L;
        private final String tableName;
        
        // 不同表的标题定义
        private static final java.util.Map<String, String> TABLE_HEADERS = new java.util.HashMap<>();
        static {
            // TRANS_INFO 表头（按字母顺序）
            TABLE_HEADERS.put("TRANS_INFO", "CDC时间,操作类型,ACCOUNT_ID,AMOUNT,ID,MERCHANT_ID,STATUS,TRANS_TIME,TRANS_TYPE\n");
            // ACCOUNT_INFO 表头（按字母顺序）
            TABLE_HEADERS.put("ACCOUNT_INFO", "CDC时间,操作类型,ACCOUNT_ID,ACCOUNT_NAME,ACCOUNT_TYPE,BALANCE,CREATED_TIME,ID,STATUS,UPDATED_TIME\n");
        }
        
        // 使用 ThreadLocal 存储每个输出流的标题写入状态
        private transient ThreadLocal<java.util.Set<java.io.OutputStream>> streamsWithHeader;
        
        public CsvEncoderWithHeader(String tableName) {
            this.tableName = tableName.toUpperCase();
        }
        
        @Override
        public void encode(String element, java.io.OutputStream stream) throws java.io.IOException {
            // 初始化 ThreadLocal
            if (streamsWithHeader == null) {
                streamsWithHeader = ThreadLocal.withInitial(java.util.HashSet::new);
            }
            
            // 检查这个流是否已经写入过标题
            if (!streamsWithHeader.get().contains(stream)) {
                String header = TABLE_HEADERS.getOrDefault(tableName, "CDC时间,操作类型,数据\n");
                stream.write(header.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                streamsWithHeader.get().add(stream);
            }
            
            // 写入数据行
            stream.write(element.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            stream.write('\n');
        }
    }
}

