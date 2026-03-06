package com.realtime.pipeline;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.common.serialization.Encoder;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.cdc.connectors.base.options.StartupOptions;
import org.apache.flink.cdc.connectors.base.source.jdbc.JdbcIncrementalSource;
import org.apache.flink.cdc.connectors.oracle.source.OracleSourceBuilder;
import org.apache.flink.cdc.debezium.JsonDebeziumDeserializationSchema;
import org.apache.flink.configuration.MemorySize;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.filesystem.OutputFileConfig;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * CDC 作业入口 - 在 Flink 集群内执行
 *
 * 通过 Flink REST API 的 /jars/:jarid/run 调用，参数通过 programArgs 传入。
 *
 * 参数格式:
 *   --hostname HOST --port PORT --username USER --password PASS
 *   --database SID --schema SCHEMA --tables TABLE1,TABLE2
 *   --outputPath PATH --parallelism N --splitSize N
 */
public class CdcJobMain {
    private static final Logger LOG = LoggerFactory.getLogger(CdcJobMain.class);
    private static final DateTimeFormatter CSV_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public static void main(String[] args) throws Exception {
        LOG.info("=== CdcJobMain received {} args ===", args.length);
        for (int i = 0; i < args.length; i++) {
            String val = args[i].startsWith("--") ? args[i] : "***";
            LOG.info("  arg[{}] = '{}'", i, val);
        }

        Map<String, String> params = parseArgs(args);

        String hostname = params.getOrDefault("hostname", "localhost");
        int port = Integer.parseInt(params.getOrDefault("port", "1521"));
        String username = params.getOrDefault("username", "system");
        String password = params.getOrDefault("password", "");
        String database = params.getOrDefault("database", "helowin");
        String schema = params.getOrDefault("schema", "FINANCE_USER");
        String tablesStr = params.getOrDefault("tables", "TRANS_INFO");
        String outputPath = params.getOrDefault("outputPath", "./output/cdc");
        int parallelism = Integer.parseInt(params.getOrDefault("parallelism", "2"));
        int splitSize = Integer.parseInt(params.getOrDefault("splitSize", "8096"));
        String jobName = params.get("jobName");  // 可选的作业名称

        List<String> tables = new ArrayList<>();
        for (String t : tablesStr.split(",")) {
            tables.add(t.trim());
        }

        LOG.info("=== CDC Job Starting ===");
        LOG.info("  DB: {}:{}/{}", hostname, port, database);
        LOG.info("  Schema: {}, Tables: {}", schema, tables);
        LOG.info("  Output: {}, Parallelism: {}", outputPath, parallelism);
        LOG.info("  Job Name: {}", jobName != null ? jobName : "(auto-generated)");
        LOG.info("  Password present: {}", password != null && !password.isEmpty());

        // 注册 Oracle JDBC 驱动
        try {
            Class<?> driverClass = Class.forName("oracle.jdbc.OracleDriver");
            java.sql.Driver driver = (java.sql.Driver) driverClass.getDeclaredConstructor().newInstance();
            java.sql.DriverManager.registerDriver(driver);
            LOG.info("Oracle JDBC Driver registered successfully");
        } catch (Exception e) {
            LOG.warn("Oracle JDBC Driver 注册失败: {}", e.getMessage());
        }

        // 先测试 JDBC 连接，确认凭据有效
        String testUrl = "jdbc:oracle:thin:@" + hostname + ":" + port + ":" + database;
        try {
            Properties testProps = new Properties();
            testProps.setProperty("user", username);
            testProps.setProperty("password", password);
            java.sql.Connection testConn = java.sql.DriverManager.getConnection(testUrl, testProps);
            LOG.info("=== JDBC connection test PASSED ===");
            testConn.close();
        } catch (Exception e) {
            LOG.error("=== JDBC connection test FAILED: {} ===", e.getMessage());
        }

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(parallelism);
        env.enableCheckpointing(30000);
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(5000);
        env.getCheckpointConfig().setCheckpointTimeout(180000);
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
        env.getCheckpointConfig().setTolerableCheckpointFailureNumber(5);
        env.getCheckpointConfig().setExternalizedCheckpointCleanup(
                CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(Integer.MAX_VALUE, Time.seconds(30)));

        Set<String> targetTables = new HashSet<>();
        for (String t : tables) {
            targetTables.add(t.trim().toUpperCase());
        }

        // 构建 SCHEMA.TABLE 列表
        StringBuilder tableListBuilder = new StringBuilder();
        for (int i = 0; i < tables.size(); i++) {
            if (i > 0) tableListBuilder.append(",");
            tableListBuilder.append(schema).append(".").append(tables.get(i).trim());
        }

        // Debezium 配置
        Properties debeziumProps = new Properties();
        debeziumProps.setProperty("log.mining.strategy", "online_catalog");
        debeziumProps.setProperty("log.mining.continuous.mine", "true");
        debeziumProps.setProperty("decimal.handling.mode", "string");
        debeziumProps.setProperty("time.precision.mode", "adaptive");
        debeziumProps.setProperty("database.connection.timeout.ms", "30000");
        debeziumProps.setProperty("database.query.timeout.ms", "600000");
        debeziumProps.setProperty("database.jdbc.driver", "oracle.jdbc.OracleDriver");
        debeziumProps.setProperty("database.tcpKeepAlive", "true");
        debeziumProps.setProperty("errors.max.retries", "-1");
        debeziumProps.setProperty("errors.retry.delay.initial.ms", "1000");
        debeziumProps.setProperty("errors.retry.delay.max.ms", "30000");
        debeziumProps.setProperty("errors.tolerance", "all");
        debeziumProps.setProperty("log.mining.restart.connection", "true");
        debeziumProps.setProperty("log.mining.session.max.ms", "300000");
        debeziumProps.setProperty("log.mining.batch.size.default", "1000");
        debeziumProps.setProperty("log.mining.batch.size.min", "100");
        debeziumProps.setProperty("log.mining.batch.size.max", "5000");
        debeziumProps.setProperty("log.mining.sleep.time.default.ms", "1000");
        debeziumProps.setProperty("log.mining.sleep.time.min.ms", "100");
        debeziumProps.setProperty("log.mining.sleep.time.max.ms", "3000");
        debeziumProps.setProperty("log.mining.sleep.time.increment.ms", "100");
        debeziumProps.setProperty("log.mining.transaction.retention.hours", "2");
        debeziumProps.setProperty("database.connection.pool.size", "3");
        // log_mining_flush 表由 Debezium 自动在连接用户 schema 下创建和管理
        // 不再指定 flink_user schema，避免权限和 synonym 冲突
        // 显式设置数据库连接信息（确保 coordinator 序列化后也能正确连接）
        debeziumProps.setProperty("database.hostname", hostname);
        debeziumProps.setProperty("database.port", String.valueOf(port));
        debeziumProps.setProperty("database.user", username);
        debeziumProps.setProperty("database.password", password);
        debeziumProps.setProperty("database.dbname", database);
        // JDBC URL 嵌入凭据（Oracle 11g SID 格式）
        // 原因：OperatorCoordinator 在 JobManager 上运行，Debezium Configuration 的
        // subset("database.",true) 操作后 password 可能丢失（null），
        // 但 URL 中嵌入的凭据不受影响，Oracle thin driver 会优先使用 URL 中的凭据
        String jdbcUrl = "jdbc:oracle:thin:" + username + "/" + password + "@" + hostname + ":" + port + ":" + database;
        debeziumProps.setProperty("database.url", jdbcUrl);
        LOG.info("  JDBC URL (credentials embedded): jdbc:oracle:thin:{}/*****@{}:{}:{}", username, hostname, port, database);

        JdbcIncrementalSource<String> oracleSource = new OracleSourceBuilder<String>()
                .hostname(hostname)
                .port(port)
                .databaseList(database)
                .schemaList(schema)
                .tableList(tableListBuilder.toString())
                .username(username)
                .password(password)
                .deserializer(new JsonDebeziumDeserializationSchema())
                .includeSchemaChanges(false)
                .startupOptions(StartupOptions.latest())
                .debeziumProperties(debeziumProps)
                .splitSize(splitSize)
                .build();

        DataStream<String> cdcStream = env
                .fromSource(oracleSource, WatermarkStrategy.noWatermarks(), "Oracle CDC Source")
                .setParallelism(parallelism)
                .filter(jsonStr -> {
                    try {
                        return targetTables.contains(extractTableName(jsonStr).toUpperCase());
                    } catch (Exception e) {
                        return true;
                    }
                })
                .name("Table Filter");

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS"));

        for (String tableName : targetTables) {
            DataStream<String> tableStream = cdcStream
                    .filter(jsonStr -> {
                        try { return extractTableName(jsonStr).equalsIgnoreCase(tableName); }
                        catch (Exception e) { return false; }
                    })
                    .name("Filter " + tableName)
                    .map(jsonStr -> convertToCSV(jsonStr, tableName))
                    .filter(csv -> csv != null)
                    .name("CSV - " + tableName);

            FileSink<String> fileSink = FileSink
                    .forRowFormat(new Path(outputPath), (Encoder<String>) (element, stream) -> {
                        stream.write(element.getBytes(StandardCharsets.UTF_8));
                        stream.write('\n');
                    })
                    .withRollingPolicy(DefaultRollingPolicy.builder()
                            .withRolloverInterval(Duration.ofSeconds(30))
                            .withInactivityInterval(Duration.ofSeconds(10))
                            .withMaxPartSize(MemorySize.ofMebiBytes(20))
                            .build())
                    .withOutputFileConfig(OutputFileConfig.builder()
                            .withPartPrefix("IDS_" + tableName.toUpperCase() + "_" + timestamp)
                            .withPartSuffix(".csv")
                            .build())
                    .build();

            tableStream.sinkTo(fileSink).name("Sink - " + tableName);
        }

        // 使用自定义作业名称（如果提供），否则使用默认格式
        String finalJobName = (jobName != null && !jobName.isEmpty()) 
            ? jobName 
            : "CDC-" + schema + "-" + String.join(",", tables);
        
        LOG.info("=== Executing job: {} ===", finalJobName);
        env.execute(finalJobName);
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].startsWith("--")) {
                params.put(args[i].substring(2), args[i + 1]);
                i++;
            }
        }
        return params;
    }

    private static String extractTableName(String json) {
        int sourceIdx = json.indexOf("\"source\":");
        if (sourceIdx >= 0) {
            int tableIdx = json.indexOf("\"table\":", sourceIdx);
            if (tableIdx >= 0) {
                int start = json.indexOf("\"", tableIdx + 8) + 1;
                int end = json.indexOf("\"", start);
                if (start > 0 && end > start) return json.substring(start, end);
            }
        }
        return "UNKNOWN";
    }

    private static String convertToCSV(String jsonStr, String tableName) {
        try {
            String operation = "UNKNOWN";
            if (jsonStr.contains("\"op\":\"c\"")) operation = "INSERT";
            else if (jsonStr.contains("\"op\":\"u\"")) operation = "UPDATE";
            else if (jsonStr.contains("\"op\":\"d\"")) operation = "DELETE";
            else if (jsonStr.contains("\"op\":\"r\"")) operation = "READ";

            String dataJson = "DELETE".equals(operation)
                    ? extractField(jsonStr, "before") : extractField(jsonStr, "after");
            if (dataJson == null || "null".equals(dataJson)) return null;

            Map<String, String> dataMap = parseJsonToMap(dataJson);
            String currentTime = LocalDateTime.now().format(CSV_TIMESTAMP);

            StringBuilder csv = new StringBuilder();
            csv.append(escapeCsvValue(currentTime)).append(",").append(escapeCsvValue(operation));
            List<String> sortedKeys = new ArrayList<>(dataMap.keySet());
            Collections.sort(sortedKeys);
            for (String key : sortedKeys) {
                csv.append(",").append(escapeCsvValue(dataMap.get(key)));
            }
            return csv.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, String> parseJsonToMap(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        if (json == null || "null".equals(json) || json.length() < 2) return map;
        String content = json.substring(1, json.length() - 1);
        boolean inQuotes = false; int start = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '"') inQuotes = !inQuotes;
            else if (c == ',' && !inQuotes) { parsePair(content.substring(start, i).trim(), map); start = i + 1; }
        }
        if (start < content.length()) parsePair(content.substring(start).trim(), map);
        return map;
    }

    private static void parsePair(String pair, Map<String, String> map) {
        int colonIdx = pair.indexOf(':');
        if (colonIdx > 0) {
            String key = pair.substring(0, colonIdx).trim().replace("\"", "");
            String value = pair.substring(colonIdx + 1).trim();
            if (value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
            map.put(key, value);
        }
    }

    private static String escapeCsvValue(String value) {
        if (value == null || value.isEmpty()) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r"))
            return "\"" + value.replace("\"", "\"\"") + "\"";
        return value;
    }

    private static String extractField(String json, String fieldName) {
        String searchStr = "\"" + fieldName + "\":";
        int fieldIdx = json.indexOf(searchStr);
        if (fieldIdx >= 0) {
            int start = json.indexOf("{", fieldIdx);
            if (start < 0) return "null";
            int end = findMatchingBrace(json, start);
            if (end > start) return json.substring(start, end + 1);
        }
        return "null";
    }

    private static int findMatchingBrace(String str, int start) {
        if (start < 0 || start >= str.length()) return -1;
        int count = 1;
        for (int i = start + 1; i < str.length(); i++) {
            if (str.charAt(i) == '{') count++;
            else if (str.charAt(i) == '}') { count--; if (count == 0) return i; }
        }
        return -1;
    }
}
