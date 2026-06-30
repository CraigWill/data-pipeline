package com.realtime.pipeline;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.Encoder;
import org.apache.flink.cdc.connectors.base.options.StartupOptions;
import org.apache.flink.cdc.connectors.base.source.jdbc.JdbcIncrementalSource;
import org.apache.flink.cdc.connectors.mysql.source.MySqlSource;
import org.apache.flink.cdc.connectors.oracle.source.OracleSourceBuilder;
import org.apache.flink.cdc.debezium.JsonDebeziumDeserializationSchema;
import org.apache.flink.configuration.CheckpointingOptions;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.ExternalizedCheckpointRetention;
import org.apache.flink.configuration.RestartStrategyOptions;
import org.apache.flink.configuration.StateBackendOptions;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.filesystem.OutputFileConfig;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.OnCheckpointRollingPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CDC 作业入口 - 在 Flink 集群内执行
 *
 * 通过 Flink REST API 的 /jars/:jarid/run 调用，参数通过 programArgs 传入。
 *
 * 参数格式:
 *   --hostname HOST --port PORT --username USER --password PASS
 *   --database SID --schema SCHEMA --tables TABLE1,TABLE2
 *   --outputPath PATH --parallelism N --splitSize N
 *   --savepointPath PATH (可选，从 savepoint 恢复)
 *
 * 确保不丢失变更的机制:
 * 1. 使用 EXACTLY_ONCE checkpoint 模式
 * 2. 每10秒一次 checkpoint，文件在 checkpoint 时提交
 * 3. 作业取消时保留 checkpoint (RETAIN_ON_CANCELLATION)
 * 4. 支持从 savepoint/checkpoint 恢复
 * 5. Oracle CDC 使用 log_mining_flush 表记录位置
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
        String dbType = params.getOrDefault("dbType", "ORACLE").toUpperCase();
        String checkpointDir = params.getOrDefault("checkpointDir", "file:///opt/flink/checkpoints");
        String savepointDir = params.getOrDefault("savepointDir", "file:///opt/flink/savepoints");

        List<String> tables = new ArrayList<>();
        for (String t : tablesStr.split(",")) {
            tables.add(t.trim());
        }

        LOG.info("=== CDC Job Starting ===");
        LOG.info("  DB Type: {}", dbType);
        LOG.info("  DB: {}:{}/{}", hostname, port, database);
        LOG.info("  Schema: {}, Tables: {}", schema, tables);
        LOG.info("  Output: {}, Parallelism: {}", outputPath, parallelism);
        LOG.info("  Job Name: {}", jobName != null ? jobName : "(auto-generated)");
        LOG.info("  Checkpoint Dir: {}", checkpointDir);
        LOG.info("  Savepoint Dir: {}", savepointDir);
        LOG.info("  Password present: {}", password != null && !password.isEmpty());

        // 注册 JDBC 驱动（根据数据库类型）
        String jdbcDriverClass;
        switch (dbType) {
            case "MYSQL":
            case "OCEANBASE":
                jdbcDriverClass = "com.mysql.cj.jdbc.Driver";
                break;
            case "OCEANBASE_ORACLE":
                jdbcDriverClass = "com.alipay.oceanbase.jdbc.Driver";
                break;
            default: // ORACLE
                jdbcDriverClass = "oracle.jdbc.OracleDriver";
                break;
        }
        try {
            Class<?> driverClass = Class.forName(jdbcDriverClass);
            java.sql.Driver driver = (java.sql.Driver) driverClass.getDeclaredConstructor().newInstance();
            java.sql.DriverManager.registerDriver(driver);
            LOG.info("{} JDBC Driver registered successfully", dbType);
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException
                | IllegalAccessException | java.lang.reflect.InvocationTargetException
                | java.sql.SQLException e) {
            LOG.warn("{} JDBC Driver 注册失败: {}", dbType, e.getMessage());
        }

        // 先测试 JDBC 连接，确认凭据有效
        String testUrl;
        switch (dbType) {
            case "MYSQL":
            case "OCEANBASE":
                testUrl = "jdbc:mysql://" + hostname + ":" + port + "/" + database + "?useSSL=false";
                break;
            case "OCEANBASE_ORACLE":
                testUrl = "jdbc:oceanbase://" + hostname + ":" + port + "/" + database + "?compatibleMode=ORACLE";
                break;
            default: // ORACLE
                testUrl = "jdbc:oracle:thin:@" + hostname + ":" + port + ":" + database;
                break;
        }
        try {
            Properties testProps = new Properties();
            testProps.setProperty("user", username);
            testProps.setProperty("password", password);
            try (java.sql.Connection testConn = java.sql.DriverManager.getConnection(testUrl, testProps)) {
                LOG.info("=== JDBC connection test PASSED (valid={}) ===", testConn.isValid(5));
            }
        } catch (java.sql.SQLException e) {
            LOG.error("=== JDBC connection test FAILED: {} ===", e.getMessage());
        }

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(parallelism);
        
        // ========== Checkpoint 配置 - 确保不丢失数据 ==========
        // 1. 启用 checkpoint，每10秒一次
        env.enableCheckpointing(10000);
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(3000);
        env.getCheckpointConfig().setCheckpointTimeout(120000);
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
        env.getCheckpointConfig().setTolerableCheckpointFailureNumber(10);
        
        // 2. 作业取消时保留 checkpoint，允许从 checkpoint 恢复
        env.getCheckpointConfig().setExternalizedCheckpointRetention(
                ExternalizedCheckpointRetention.RETAIN_ON_CANCELLATION);
        
        // 3. 启用非对齐 checkpoint 以提高性能
        env.getCheckpointConfig().enableUnalignedCheckpoints();
        
        // 4. 设置 checkpoint 存储、状态后端和重启策略（通过 Configuration API）
        Configuration config = new Configuration();
        config.set(CheckpointingOptions.CHECKPOINT_STORAGE, "filesystem");
        config.set(CheckpointingOptions.CHECKPOINTS_DIRECTORY, checkpointDir);
        config.set(StateBackendOptions.STATE_BACKEND, "hashmap");
        config.set(RestartStrategyOptions.RESTART_STRATEGY, "fixed-delay");
        config.set(RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_ATTEMPTS, Integer.MAX_VALUE);
        config.set(RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_DELAY, Duration.ofSeconds(30));
        env.configure(config);
        
        LOG.info("=== Checkpoint 配置完成 ===");
        LOG.info("  Checkpoint 间隔: 10秒");
        LOG.info("  Checkpoint 存储: {}", checkpointDir);
        LOG.info("  取消时保留 Checkpoint: 是");

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

        // 根据数据库类型构建不同的 CDC Source
        DataStream<String> cdcStream;

        switch (dbType) {
            case "MYSQL":
            case "OCEANBASE": {
                // MySQL / OceanBase MySQL 模式使用 MySqlSource
                // 构建 database.table 列表格式
                String[] tableArray = tables.stream()
                        .map(t -> database + "." + t.trim())
                        .toArray(String[]::new);

                MySqlSource<String> mysqlSource = MySqlSource.<String>builder()
                        .hostname(hostname)
                        .port(port)
                        .databaseList(database)
                        .tableList(tableArray)
                        .username(username)
                        .password(password)
                        .deserializer(new JsonDebeziumDeserializationSchema())
                        .splitSize(splitSize)
                        .build();

                cdcStream = env
                        .fromSource(mysqlSource, WatermarkStrategy.noWatermarks(),
                                dbType + " CDC Source")
                        .setParallelism(1);

                LOG.info("=== {} CDC Source configured ===", dbType);
                break;
            }
            case "OCEANBASE_ORACLE": {
                // OceanBase Oracle 租户使用 OceanBase CDC Connector（通过 oblogproxy）
                // 额外参数: --logProxyHost, --logProxyPort, --tenantName
                String logProxyHost = params.getOrDefault("logProxyHost", "oblogproxy");
                int logProxyPort = Integer.parseInt(params.getOrDefault("logProxyPort", "2983"));
                String tenantName = params.getOrDefault("tenantName", "oratenant");

                LOG.info("  OceanBase Oracle CDC via LogProxy: {}:{}", logProxyHost, logProxyPort);
                LOG.info("  Tenant: {}", tenantName);

                // OceanBase Oracle 模式: databaseName = schema（大写），tableName = 表名正则
                // tableName 支持正则，多表用 | 分隔
                String obTableName = tables.stream()
                        .map(t -> t.trim().toUpperCase())
                        .reduce((a, b) -> a + "|" + b)
                        .orElse(".*");

                // OceanBase CDC 要求 start_timestamp 不能为 0，使用当前时间戳（秒）
                long startTimestampSec = System.currentTimeMillis() / 1000L;
                LOG.info("  OceanBase CDC startTimestamp: {} (current time)", startTimestampSec);

                // obcdc 需要 sys 租户凭据来读取集群元数据并拉取 clog。
                // 注意：实测表明 cluster_user 实际由 oblogproxy(obbinlog) 的 conf.json
                //       (容器环境变量 OB_SYS_USERNAME) 决定，此处 obcdcProperties 不生效。
                //       要切换最小权限只读账号(cdc_reader)请改 obbinlog 容器的 OB_SYS_USERNAME。
                Properties obcdcProps = new Properties();
                obcdcProps.setProperty("cluster_user", "root");
                obcdcProps.setProperty("cluster_password", params.getOrDefault("obSysPassword", "password"));

                org.apache.flink.streaming.api.functions.source.SourceFunction<String> obSource =
                    org.apache.flink.cdc.connectors.oceanbase.OceanBaseSource.<String>builder()
                        .hostname(hostname)
                        .port(port)
                        .username(username)
                        .password(password)
                        .tenantName(tenantName)
                        .databaseName(schema.toUpperCase())
                        .tableName(obTableName)
                        .compatibleMode("ORACLE")
                        .jdbcDriver("com.oceanbase.jdbc.Driver")
                        .connectTimeout(Duration.ofMinutes(3))
                        .rsList(params.getOrDefault("rsList", hostname + ":2882:" + port))
                        .logProxyHost(logProxyHost)
                        .logProxyPort(logProxyPort)
                        .logProxyClientId("flink-cdc-" + System.currentTimeMillis())
                        .startupTimestamp(startTimestampSec)
                        .obcdcProperties(obcdcProps)
                        .deserializer(new JsonDebeziumDeserializationSchema())
                        .build();

                // OceanBaseSource 返回 SourceFunction（旧式 API），使用 addSource
                cdcStream = env
                        .addSource(obSource, "OceanBase Oracle CDC Source")
                        .setParallelism(1);

                LOG.info("=== OceanBase Oracle CDC Source configured (via oblogproxy) ===");
                break;
            }
            default: {
                // ORACLE（默认）- 使用 OracleSourceBuilder
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
                debeziumProps.setProperty("database.autocommit", "false");
                debeziumProps.setProperty("errors.max.retries", "-1");
                debeziumProps.setProperty("errors.retry.delay.initial.ms", "1000");
                debeziumProps.setProperty("errors.retry.delay.max.ms", "30000");
                debeziumProps.setProperty("errors.tolerance", "all");
                debeziumProps.setProperty("log.mining.restart.connection", "false");
                debeziumProps.setProperty("log.mining.session.max.ms", "0");
                // DDL 捕获配置
                debeziumProps.setProperty("include.schema.changes", "true");
                debeziumProps.setProperty("schema.history.internal.store.only.captured.tables.ddl", "true");
                // LogMiner 批处理优化
                debeziumProps.setProperty("log.mining.batch.size.default", "50000");
                debeziumProps.setProperty("log.mining.batch.size.min", "10000");
                debeziumProps.setProperty("log.mining.batch.size.max", "100000");
                debeziumProps.setProperty("log.mining.sleep.time.default.ms", "1000");
                debeziumProps.setProperty("log.mining.sleep.time.min.ms", "200");
                debeziumProps.setProperty("log.mining.sleep.time.max.ms", "5000");
                debeziumProps.setProperty("log.mining.sleep.time.increment.ms", "200");
                debeziumProps.setProperty("log.mining.transaction.retention.hours", "2");
                debeziumProps.setProperty("database.connection.pool.size", "3");
                // LogMiner 会话管理配置
                debeziumProps.setProperty("log.mining.session.max.ms", "0");
                debeziumProps.setProperty("log.mining.restart.connection", "true");
                debeziumProps.setProperty("log.mining.archive.destination.name", "");
                // 显式设置数据库连接信息
                debeziumProps.setProperty("database.hostname", hostname);
                debeziumProps.setProperty("database.port", String.valueOf(port));
                debeziumProps.setProperty("database.user", username);
                debeziumProps.setProperty("database.password", password);
                debeziumProps.setProperty("database.dbname", database);
                // JDBC URL 嵌入凭据（Oracle 11g SID 格式）
                String jdbcUrl = "jdbc:oracle:thin:" + username + "/" + password + "@" + hostname + ":" + port + ":" + database;
                debeziumProps.setProperty("database.url", jdbcUrl);
                LOG.info("  JDBC URL (credentials embedded): jdbc:oracle:thin:{}/*****@{}:{}:{}", username, hostname, port, database);
                LOG.info("  DDL Capture: ENABLED");

                JdbcIncrementalSource<String> oracleSource = new OracleSourceBuilder<String>()
                        .hostname(hostname)
                        .port(port)
                        .databaseList(database)
                        .schemaList(schema)
                        .tableList(tableListBuilder.toString())
                        .username(username)
                        .password(password)
                        .deserializer(new JsonDebeziumDeserializationSchema())
                        .includeSchemaChanges(true)
                        .startupOptions(StartupOptions.latest())
                        .debeziumProperties(debeziumProps)
                        .splitSize(splitSize)
                        .build();

                LOG.info("=== Schema Change Capture ENABLED ===");

                cdcStream = env
                        .fromSource(oracleSource, WatermarkStrategy.noWatermarks(), "Oracle CDC Source")
                        .setParallelism(1);
                break;
            }
        }
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS"));
        
        // 分离 DDL 事件和 DML 事件
        // 在过滤阶段可以使用并行度提高性能
        DataStream<String> ddlStream = cdcStream
                .filter(jsonStr -> isDDLEvent(jsonStr))
                .setParallelism(parallelism)  // 过滤可以并行
                .name("DDL Event Filter");
        
        DataStream<String> dmlStream = cdcStream
                .filter(jsonStr -> !isDDLEvent(jsonStr))
                .filter(jsonStr -> {
                    try {
                        return targetTables.contains(extractTableName(jsonStr).toUpperCase());
                    } catch (Exception e) {
                        return true;
                    }
                })
                .setParallelism(parallelism)  // 过滤可以并行
                .name("DML Event Filter");
        
        // 处理 DDL 事件 - 输出到单独的文件
        DataStream<String> ddlCsvStream = ddlStream
                .map(jsonStr -> convertDDLToCSV(jsonStr))
                .filter(csv -> csv != null)
                .setParallelism(1)  // DDL 写入使用单并行度，避免重复
                .name("DDL to CSV");
        
        FileSink<String> ddlSink = FileSink
                .forRowFormat(new Path(outputPath), (Encoder<String>) (element, stream) -> {
                    stream.write(element.getBytes(StandardCharsets.UTF_8));
                    stream.write('\n');
                })
                .withRollingPolicy(OnCheckpointRollingPolicy.build())
                .withOutputFileConfig(OutputFileConfig.builder()
                        .withPartPrefix("DDL_SCHEMA_CHANGES_" + timestamp)
                        .withPartSuffix(".csv")
                        .build())
                .build();
        
        ddlCsvStream.sinkTo(ddlSink).name("Sink - DDL Events");
        LOG.info("=== DDL Event Sink configured ===");

        for (String tableName : targetTables) {
            // OceanBase Oracle 模式只记录变更（INSERT/UPDATE/DELETE），跳过快照 READ
            final boolean skipRead = "OCEANBASE_ORACLE".equals(dbType);
            DataStream<String> tableStream = dmlStream
                    .filter(jsonStr -> {
                        try { return extractTableName(jsonStr).equalsIgnoreCase(tableName); }
                        catch (Exception e) { return false; }
                    })
                    .name("Filter " + tableName)
                    .map(jsonStr -> convertToCSV(jsonStr, skipRead))
                    .filter(csv -> csv != null)
                    .setParallelism(1)  // 每个表的写入使用单并行度，避免重复
                    .name("CSV - " + tableName);

            FileSink<String> fileSink = FileSink
                    .forRowFormat(new Path(outputPath), (Encoder<String>) (element, stream) -> {
                        stream.write(element.getBytes(StandardCharsets.UTF_8));
                        stream.write('\n');
                    })
                    // 使用 OnCheckpointRollingPolicy - 每次 checkpoint 时提交文件
                    // 这样可以确保数据不会因为 in-progress 文件而丢失
                    .withRollingPolicy(OnCheckpointRollingPolicy.build())
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
    /**
     * 解析参数，必须以--作为命令行参数，不建议修改。
     * 
     */
    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].startsWith("--") || args[i].startsWith("-")) {
                params.put(args[i].substring(2), args[i + 1]);
                i++;
            }
        }
        return params;
    }
    
    /**
     * 判断是否为 DDL 事件
     * DDL 事件的特征：
     * 1. 没有 "op" 字段，或者 op 为 null
     * 2. 包含 "historyRecord" 字段
     * 3. 包含 "ddl" 字段
     */
    private static boolean isDDLEvent(String json) {
        try {
            // DDL 事件通常包含 "historyRecord" 或 "ddl" 字段
            if (json.contains("\"historyRecord\"") || json.contains("\"ddl\"")) {
                return true;
            }
            // 检查是否有 op 字段，DDL 事件可能没有 op 字段
            if (!json.contains("\"op\":")) {
                return json.contains("\"source\":") && json.contains("\"table\":");
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 将 DDL 事件转换为 CSV 格式
     * CSV 格式: timestamp,database,schema,table,ddl_type,ddl_statement
     * 
     * 只捕获列级别的 DDL：ADD_COLUMN、MODIFY_COLUMN、DROP_COLUMN
     */
    private static String convertDDLToCSV(String jsonStr) {
        try {
            String currentTime = LocalDateTime.now().format(CSV_TIMESTAMP);
            
            // 提取 source 信息
            String database = extractJsonValue(jsonStr, "db", "source");
            String schema = extractJsonValue(jsonStr, "schema", "source");
            String table = extractJsonValue(jsonStr, "table", "source");
            
            // 提取 DDL 语句
            String ddlStatement = extractJsonValue(jsonStr, "ddl", null);
            if (ddlStatement == null || "null".equals(ddlStatement)) {
                ddlStatement = extractJsonValue(jsonStr, "historyRecord", null);
            }
            
            if (ddlStatement == null) {
                return null;
            }
            
            // 判断 DDL 类型 - 只处理列级别的 DDL
            String ddlType = null;
            String upperDdl = ddlStatement.toUpperCase();
            
            if (upperDdl.contains("ALTER TABLE") && upperDdl.contains("ADD")) {
                // 排除 ADD CONSTRAINT 等非列操作
                if (!upperDdl.contains("ADD CONSTRAINT") && !upperDdl.contains("ADD PRIMARY KEY") 
                    && !upperDdl.contains("ADD FOREIGN KEY") && !upperDdl.contains("ADD UNIQUE")) {
                    ddlType = "ADD_COLUMN";
                }
            } else if (upperDdl.contains("ALTER TABLE") && upperDdl.contains("MODIFY")) {
                ddlType = "MODIFY_COLUMN";
            } else if (upperDdl.contains("ALTER TABLE") && upperDdl.contains("DROP COLUMN")) {
                ddlType = "DROP_COLUMN";
            }
            
            // 如果不是列级别的 DDL，忽略
            if (ddlType == null) {
                LOG.debug("Ignoring non-column DDL: {}", ddlStatement);
                return null;
            }
            
            StringBuilder csv = new StringBuilder();
            csv.append(escapeCsvValue(currentTime)).append(",");
            csv.append(escapeCsvValue(database)).append(",");
            csv.append(escapeCsvValue(schema)).append(",");
            csv.append(escapeCsvValue(table)).append(",");
            csv.append(escapeCsvValue(ddlType)).append(",");
            csv.append(escapeCsvValue(ddlStatement));
            
            LOG.info("Column-level DDL captured: {} on {}.{}.{}", ddlType, database, schema, table);
            
            return csv.toString();
        } catch (Exception e) {
            LOG.error("Failed to convert DDL event to CSV: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 从 JSON 中提取指定字段的值
     * @param json JSON 字符串
     * @param fieldName 字段名
     * @param parentField 父字段名（可选）
     * @return 字段值
     */
    private static String extractJsonValue(String json, String fieldName, String parentField) {
        try {
            String searchArea = json;
            
            // 如果指定了父字段，先定位到父字段
            if (parentField != null) {
                String parentSearch = "\"" + parentField + "\":";
                int parentIdx = json.indexOf(parentSearch);
                if (parentIdx < 0) return null;
                
                int start = json.indexOf("{", parentIdx);
                if (start < 0) return null;
                
                int end = findMatchingBrace(json, start);
                if (end < 0) return null;
                
                searchArea = json.substring(start, end + 1);
            }
            
            // 在搜索区域内查找字段
            String fieldSearch = "\"" + fieldName + "\":";
            int fieldIdx = searchArea.indexOf(fieldSearch);
            if (fieldIdx < 0) return null;
            
            int valueStart = fieldIdx + fieldSearch.length();
            
            // 跳过空格
            while (valueStart < searchArea.length() && Character.isWhitespace(searchArea.charAt(valueStart))) {
                valueStart++;
            }
            
            if (valueStart >= searchArea.length()) return null;
            
            // 判断值的类型
            char firstChar = searchArea.charAt(valueStart);
            
            switch (firstChar) {
                case '"' -> {
                    // 字符串值
                    int valueEnd = valueStart + 1;
                    while (valueEnd < searchArea.length()) {
                        if (searchArea.charAt(valueEnd) == '"' && searchArea.charAt(valueEnd - 1) != '\\') {
                            return searchArea.substring(valueStart + 1, valueEnd);
                        }
                        valueEnd++;
                    }
                }
                case '{' -> {
                    // 对象值
                    int valueEnd = findMatchingBrace(searchArea, valueStart);
                    if (valueEnd > valueStart) {
                        return searchArea.substring(valueStart, valueEnd + 1);
                    }
                }
                default -> {
                    // 数字、布尔值或 null
                    int valueEnd = valueStart;
                    while (valueEnd < searchArea.length()) {
                        char c = searchArea.charAt(valueEnd);
                        if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) {
                            break;
                        }
                        valueEnd++;
                    }
                    return searchArea.substring(valueStart, valueEnd);
                }
            }
            
            return null;
        } catch (Exception e) {
            return null;
        }
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

    private static String convertToCSV(String jsonStr, boolean skipRead) {
        try {
            String operation = "UNKNOWN";
            if (jsonStr.contains("\"op\":\"c\"")) operation = "INSERT";
            else if (jsonStr.contains("\"op\":\"u\"")) operation = "UPDATE";
            else if (jsonStr.contains("\"op\":\"d\"")) operation = "DELETE";
            else if (jsonStr.contains("\"op\":\"r\"")) operation = "READ";

            // OceanBase Oracle 模式：跳过快照 READ 操作，只记录 INSERT/UPDATE/DELETE 变更
            if ("READ".equals(operation) && skipRead) return null;

            String dataJson = "DELETE".equals(operation)
                    ? extractField(jsonStr, "before") : extractField(jsonStr, "after");
            if (dataJson == null || "null".equals(dataJson)) return null;

            Map<String, String> dataMap = parseJsonToMap(dataJson);
            String currentTime = LocalDateTime.now().format(CSV_TIMESTAMP);

            StringBuilder csv = new StringBuilder();
            csv.append(escapeCsvValue(currentTime)).append(",").append(escapeCsvValue(operation));
            // 保持数据库字段顺序（LinkedHashMap保持插入顺序）
            for (String key : dataMap.keySet()) {
                String value = dataMap.get(key);
                // 格式化时间字段
                value = formatTimeField(key, value);
                csv.append(",").append(escapeCsvValue(value));
            }
            return csv.toString();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 格式化时间字段
     * 如果字段名包含TIME或DATE，且值是数字（毫秒时间戳），则格式化为可读格式
     */
    private static String formatTimeField(String fieldName, String value) {
        if (value == null || value.isEmpty() || "null".equals(value)) {
            return value;
        }
        
        // 检查字段名是否包含时间相关关键字
        String upperFieldName = fieldName.toUpperCase();
        if (upperFieldName.contains("TIME") || upperFieldName.contains("DATE")) {
            try {
                // 尝试解析为长整型（毫秒时间戳）
                long timestamp = Long.parseLong(value);
                // 转换为LocalDateTime并格式化
                LocalDateTime dateTime = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(timestamp),
                    java.time.ZoneId.systemDefault()
                );
                return dateTime.format(CSV_TIMESTAMP);
            } catch (NumberFormatException e) {
                // 不是数字，返回原值
                return value;
            }
        }
        
        return value;
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
