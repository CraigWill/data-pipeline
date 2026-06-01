package com.realtime.monitor.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.realtime.monitor.dto.CdcSubmitRequest;
import com.realtime.monitor.dto.DataSourceConfig;
import com.realtime.monitor.dto.TaskConfig;
import com.realtime.monitor.util.PasswordEncryptionUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * CDC 任务管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CdcTaskService {

    private final DataSourceService dataSourceService;
    private final EmbeddedCdcService embeddedCdcService;
    private final com.realtime.monitor.repository.TaskRepository taskRepository;
    private final RuntimeJobService runtimeJobService;


    /**
     * 测试数据库连接
     */
    public Map<String, Object> testConnection(DataSourceConfig config) {
        String jdbcUrl = buildJdbcUrl(config);
        // config 来自 DataSourceService.loadDataSource 时密码已解密，直接使用
        String password = config.getPassword();

        // 安全修复：使用 PreparedStatement 替代 Statement，防止 SQL 注入
        try (Connection conn = DriverManager.getConnection(jdbcUrl, config.getUsername(), password);
             PreparedStatement stmt = conn.prepareStatement(pingQuery(config));
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return Map.of("success", true, "message", "连接成功");
            }
            return Map.of("success", false, "error", "连接失败：无返回结果");
        } catch (SQLException e) {
            // 安全修复：不泄露详细错误信息给客户端
            log.error("数据库连接测试失败", e);
            return Map.of("success", false, "error", "数据库连接测试失败，请检查配置");
        }
    }

    /**
     * 发现数据库 Schema 列表（根据数据库类型使用不同查询）
     */
    public List<String> discoverSchemas(DataSourceConfig config) throws Exception {
        String jdbcUrl = buildJdbcUrl(config);
        String password = config.getPassword();
        String type = config.getType() != null ? config.getType().toUpperCase() : "ORACLE";

        List<String> schemas = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(jdbcUrl, config.getUsername(), password)) {
            switch (type) {
                case "MYSQL":
                case "OCEANBASE": {
                    // MySQL/OceanBase: information_schema.schemata
                    String sql = "SELECT schema_name FROM information_schema.schemata " +
                            "WHERE schema_name NOT IN (?,?,?,?,?,?) ORDER BY schema_name";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        String[] sys = {"information_schema","mysql","performance_schema","sys","oceanbase","__oceanbase_inner_standby_replication__"};
                        for (int i = 0; i < sys.length; i++) stmt.setString(i + 1, sys[i]);
                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) schemas.add(rs.getString(1));
                        }
                    }
                    break;
                }
                case "POSTGRES": {
                    // PostgreSQL: information_schema.schemata
                    String sql = "SELECT schema_name FROM information_schema.schemata " +
                            "WHERE schema_name NOT IN (?,?,?) ORDER BY schema_name";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, "information_schema");
                        stmt.setString(2, "pg_catalog");
                        stmt.setString(3, "pg_toast");
                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) schemas.add(rs.getString(1));
                        }
                    }
                    break;
                }
                default: {
                    // Oracle: all_tables
                    String sql = "SELECT DISTINCT owner FROM all_tables " +
                            "WHERE owner NOT IN (?,?,?,?,?,?,?,?,?,?,?,?,?) ORDER BY owner";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        String[] sys = {"SYS","SYSTEM","OUTLN","DBSNMP","APPQOSSYS","WMSYS","EXFSYS","CTXSYS","XDB","ANONYMOUS","ORDSYS","ORDDATA","MDSYS"};
                        for (int i = 0; i < sys.length; i++) stmt.setString(i + 1, sys[i]);
                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) schemas.add(rs.getString(1));
                        }
                    }
                    break;
                }
            }
        } catch (SQLException e) {
            log.error("发现 Schema 列表失败 [{}]", type, e);
            throw new Exception("获取 Schema 列表失败，请检查数据库连接");
        }
        return schemas;
    }

    /**
     * 发现 Schema 中的表（根据数据库类型使用不同查询）
     */
    public List<Map<String, Object>> discoverTables(DataSourceConfig config, String schema) throws Exception {
        String jdbcUrl = buildJdbcUrl(config);
        String password = config.getPassword();
        String type = config.getType() != null ? config.getType().toUpperCase() : "ORACLE";

        Map<String, Map<String, Object>> uniqueTables = new LinkedHashMap<>();
        log.info("查询 Schema {} 的表列表 [{}]", schema, type);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, config.getUsername(), password)) {
            switch (type) {
                case "MYSQL":
                case "OCEANBASE": {
                    // MySQL/OceanBase: information_schema.tables
                    String sql = "SELECT table_name, table_rows, " +
                            "(SELECT COUNT(*) FROM information_schema.columns c " +
                            " WHERE c.table_schema=? AND c.table_name=t.table_name) AS col_count " +
                            "FROM information_schema.tables t " +
                            "WHERE table_schema=? AND table_type='BASE TABLE' " +
                            "ORDER BY table_name";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, schema);
                        stmt.setString(2, schema);
                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                String name = rs.getString("table_name");
                                if (!uniqueTables.containsKey(name)) {
                                    Map<String, Object> t = new HashMap<>();
                                    t.put("name", name);
                                    t.put("rows", rs.getLong("table_rows"));
                                    t.put("columns", rs.getInt("col_count"));
                                    uniqueTables.put(name, t);
                                }
                            }
                        }
                    }
                    break;
                }
                case "POSTGRES": {
                    // PostgreSQL: information_schema.tables
                    String sql = "SELECT table_name, " +
                            "(SELECT COUNT(*) FROM information_schema.columns c " +
                            " WHERE c.table_schema=? AND c.table_name=t.table_name) AS col_count " +
                            "FROM information_schema.tables t " +
                            "WHERE table_schema=? AND table_type='BASE TABLE' " +
                            "ORDER BY table_name";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, schema);
                        stmt.setString(2, schema);
                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                String name = rs.getString("table_name");
                                if (!uniqueTables.containsKey(name)) {
                                    Map<String, Object> t = new HashMap<>();
                                    t.put("name", name);
                                    t.put("rows", 0L);
                                    t.put("columns", rs.getInt("col_count"));
                                    uniqueTables.put(name, t);
                                }
                            }
                        }
                    }
                    break;
                }
                default: {
                    // Oracle: all_tables
                    String sql = "SELECT t.table_name, " +
                            "CAST(NVL(t.num_rows, 0) AS NUMBER(10)) AS row_count, " +
                            "(SELECT COUNT(*) FROM all_tab_columns c WHERE c.owner=? AND c.table_name=t.table_name) AS col_count " +
                            "FROM all_tables t " +
                            "WHERE t.owner=? AND t.table_name NOT LIKE 'BIN$%' " +
                            "AND t.table_name NOT LIKE '%$%' AND t.temporary='N' " +
                            "ORDER BY t.table_name, t.num_rows DESC NULLS LAST";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, schema.toUpperCase());
                        stmt.setString(2, schema.toUpperCase());
                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                String name = rs.getString("table_name");
                                if (!uniqueTables.containsKey(name)) {
                                    Map<String, Object> t = new HashMap<>();
                                    t.put("name", name);
                                    t.put("rows", rs.getLong("row_count"));
                                    t.put("columns", rs.getInt("col_count"));
                                    uniqueTables.put(name, t);
                                }
                            }
                        }
                    }
                    break;
                }
            }
        } catch (SQLException e) {
            log.error("发现表列表失败 [{}]", type, e);
            throw new Exception("获取表列表失败，请检查数据库连接");
        }

        log.info("Schema {} 共发现 {} 个唯一表", schema, uniqueTables.size());
        return new ArrayList<>(uniqueTables.values());
    }

    private String buildJdbcUrl(DataSourceConfig config) {
        String host = config.getHost();
        String type = config.getType() != null ? config.getType().toUpperCase() : "ORACLE";

        switch (type) {
            case "MYSQL":
            case "OCEANBASE":
                ensureDriver("com.mysql.cj.jdbc.Driver");
                return String.format(
                    "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true",
                    host, config.getPort(), config.getSid());
            case "POSTGRES":
                ensureDriver("org.postgresql.Driver");
                return String.format("jdbc:postgresql://%s:%d/%s", host, config.getPort(), config.getSid());
            case "ORACLE":
            default:
                ensureDriver("oracle.jdbc.OracleDriver");
                return String.format("jdbc:oracle:thin:@%s:%d:%s", host, config.getPort(), config.getSid());
        }
    }

    private void ensureDriver(String className) {
        try {
            Class.forName(className);
        } catch (ClassNotFoundException e) {
            log.warn("JDBC 驱动未找到: {}，请确认 JAR 已加入 classpath", className);
        }
    }

    private String pingQuery(DataSourceConfig config) {
        String type = config.getType() != null ? config.getType().toUpperCase() : "ORACLE";
        return switch (type) {
            case "MYSQL", "OCEANBASE", "POSTGRES" -> "SELECT 1";
            default -> "SELECT 1 FROM DUAL";
        };
    }

    /**
     * 保存任务配置
     */
    public String saveTaskConfig(TaskConfig config) {
        String taskId = config.getId();
        if (taskId == null || taskId.isEmpty()) {
            taskId = "task-" + System.currentTimeMillis();
            config.setId(taskId);
        }

        // 如果有 datasourceId，加载数据源配置
        if (config.getDatasourceId() != null && !config.getDatasourceId().isEmpty()) {
            try {
                DataSourceConfig dsConfig = dataSourceService.loadDataSource(config.getDatasourceId());
                TaskConfig.DatabaseConfig dbConfig = new TaskConfig.DatabaseConfig();
                dbConfig.setHost(dsConfig.getHost());
                dbConfig.setPort(dsConfig.getPort());
                dbConfig.setUsername(dsConfig.getUsername());
                dbConfig.setPassword(dsConfig.getPassword());
                dbConfig.setSid(dsConfig.getSid());
                dbConfig.setSchema(config.getSchema());
                config.setDatabase(dbConfig);
                config.setDatasourceName(dsConfig.getName());
            } catch (Exception e) {
                log.warn("加载数据源配置失败: {}", config.getDatasourceId(), e);
            }
        }

        taskRepository.save(config);
        return taskId;
    }

    /**
     * 加载任务配置
     */
    public TaskConfig loadTaskConfig(String taskId) {
        TaskConfig config = taskRepository.findById(taskId);
        if (config == null) {
            throw new RuntimeException("任务配置不存在: " + taskId);
        }

        // 加载数据源名称
        if (config.getDatasourceId() != null) {
            try {
                DataSourceConfig dsConfig = dataSourceService.loadDataSource(config.getDatasourceId());
                config.setDatasourceName(dsConfig.getName());

                // 填充 database 配置
                if (config.getDatabase() == null) {
                    TaskConfig.DatabaseConfig dbConfig = new TaskConfig.DatabaseConfig();
                    dbConfig.setHost(dsConfig.getHost());
                    dbConfig.setPort(dsConfig.getPort());
                    dbConfig.setUsername(dsConfig.getUsername());
                    dbConfig.setPassword(dsConfig.getPassword());
                    dbConfig.setSid(dsConfig.getSid());
                    dbConfig.setSchema(config.getSchema());
                    config.setDatabase(dbConfig);
                }
            } catch (Exception e) {
                log.warn("加载数据源配置失败: {}", config.getDatasourceId(), e);
            }
        }

        return config;
    }

    /**
     * 列出所有任务配置
     */
    public List<Map<String, Object>> listTasks() {
        List<TaskConfig> tasks = taskRepository.findAll();

        return tasks.stream()
                .map(config -> {
                    Map<String, Object> summary = new HashMap<>();
                    summary.put("id", config.getId());
                    summary.put("name", config.getName() != null ? config.getName() : "Unnamed Task");

                    String dbDisplay = config.getDatasourceName();
                    if (dbDisplay == null) {
                        try {
                            if (config.getDatasourceId() != null) {
                                DataSourceConfig dsConfig = dataSourceService.loadDataSource(config.getDatasourceId());
                                dbDisplay = dsConfig.getName();
                            } else if (config.getDatabase() != null) {
                                dbDisplay = config.getDatabase().getHost();
                            }
                        } catch (Exception e) {
                            log.warn("加载数据源名称失败", e);
                        }
                    }
                    summary.put("database", dbDisplay != null ? dbDisplay : "Unknown");
                    
                    // 添加 schema 字段
                    String schema = config.getSchema();
                    if (schema == null && config.getDatabase() != null) {
                        schema = config.getDatabase().getSchema();
                    }
                    summary.put("schema", schema);
                    
                    summary.put("tables", config.getTables() != null ? config.getTables().size() : 0);
                    summary.put("created", config.getCreated());
                    return summary;
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取任务详情
     */
    public Map<String, Object> getTaskDetail(String taskId) {
        TaskConfig config = loadTaskConfig(taskId);

        Map<String, Object> detail = new HashMap<>();
        detail.put("id", config.getId());
        detail.put("name", config.getName());
        detail.put("created", config.getCreated());

        Map<String, Object> database = new HashMap<>();
        if (config.getDatabase() != null) {
            database.put("host", config.getDatabase().getHost());
            database.put("port", config.getDatabase().getPort());
            database.put("sid", config.getDatabase().getSid());
            database.put("schema", config.getDatabase().getSchema());
            database.put("username", config.getDatabase().getUsername());
        }
        detail.put("database", database);
        detail.put("tables", config.getTables());
        detail.put("output_path", config.getOutputPath());
        detail.put("parallelism", config.getParallelism());
        detail.put("split_size", config.getSplitSize());
        detail.put("datasource_id", config.getDatasourceId());
        detail.put("datasource_name", config.getDatasourceName());

        return detail;
    }

    /**
     * 删除任务配置
     */
    public void deleteTask(String taskId) {
        taskRepository.deleteById(taskId);
    }

    /**
     * 提交任务到 Flink
     */
    public Map<String, Object> submitTask(String taskId) throws Exception {
        TaskConfig config = loadTaskConfig(taskId);

        // 验证是否可以提交
        Map<String, Object> validation = runtimeJobService.validateJobSubmission(config);
        if (!(Boolean) validation.get("canSubmit")) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("errors", validation.get("errors"));
            errorResult.put("conflicts", validation.get("conflicts"));
            return errorResult;
        }

        // 创建运行时作业记录
        com.realtime.monitor.dto.RuntimeJob runtimeJob = runtimeJobService.createRuntimeJob(config);

        try {
            // 提交到 Flink
            Map<String, Object> result = embeddedCdcService.submitTask(config);

            // 异步更新 Flink Job ID
            if (result.get("success") == Boolean.TRUE && result.containsKey("job_id")) {
                String jobId = (String) result.get("job_id");
                runtimeJobService.updateFlinkJobIdAsync(runtimeJob.getId(), jobId);
                result.put("runtime_job_id", runtimeJob.getId());
            } else {
                runtimeJobService.updateJobStatus(runtimeJob.getId(), "FAILED", "提交失败");
            }

            return result;
        } catch (Exception e) {
            runtimeJobService.updateJobStatus(runtimeJob.getId(), "FAILED", e.getMessage());
            throw e;
        }
    }

    /**
     * 动态提交任务
     */
    public Map<String, Object> submitTaskDynamic(String taskId) throws Exception {
        return submitTask(taskId);
    }

    /**
     * 直接提交 CDC 任务（也会创建 RuntimeJob 记录）
     */
    public Map<String, Object> submitDirect(CdcSubmitRequest request) throws Exception {
        // 构建 TaskConfig 用于验证和 RuntimeJob 记录
        TaskConfig taskConfig = new TaskConfig();
        taskConfig.setId("task-" + System.currentTimeMillis());
        taskConfig.setName(request.getJobName() != null ? request.getJobName() : "direct-" + System.currentTimeMillis());
        taskConfig.setSchema(request.getSchema());
        taskConfig.setTables(request.getTables());
        taskConfig.setOutputPath(request.getOutputPath());
        taskConfig.setParallelism(request.getParallelism());
        taskConfig.setSplitSize(request.getSplitSize());

        // 设置数据库配置，对前端传入的明文密码进行加密后存储
        TaskConfig.DatabaseConfig dbConfig = new TaskConfig.DatabaseConfig();
        dbConfig.setHost(request.getHostname());
        dbConfig.setPort(request.getPort());
        dbConfig.setUsername(request.getUsername());
        // 加密密码：前端传来明文，存储前加密
        try {
            dbConfig.setPassword(PasswordEncryptionUtil.encryptAES(request.getPassword()));
        } catch (Exception e) {
            log.error("submitDirect 密码加密失败", e);
            throw new RuntimeException("密码加密失败", e);
        }
        dbConfig.setSid(request.getDatabase());
        dbConfig.setSchema(request.getSchema());
        taskConfig.setDatabase(dbConfig);

        // 验证是否可以提交
        Map<String, Object> validation = runtimeJobService.validateJobSubmission(taskConfig);
        if (!(Boolean) validation.get("canSubmit")) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("errors", validation.get("errors"));
            errorResult.put("conflicts", validation.get("conflicts"));
            return errorResult;
        }

        // 保存任务配置
        taskRepository.save(taskConfig);

        // 创建运行时作业记录
        com.realtime.monitor.dto.RuntimeJob runtimeJob = runtimeJobService.createRuntimeJob(taskConfig);

        try {
            Map<String, Object> result = embeddedCdcService.submitTask(request);

            if (result.get("success") == Boolean.TRUE && result.containsKey("job_id")) {
                String jobId = (String) result.get("job_id");
                runtimeJobService.updateFlinkJobIdAsync(runtimeJob.getId(), jobId);
                result.put("runtime_job_id", runtimeJob.getId());
                result.put("task_id", taskConfig.getId());
            } else {
                runtimeJobService.updateJobStatus(runtimeJob.getId(), "FAILED", "提交失败");
            }

            return result;
        } catch (Exception e) {
            runtimeJobService.updateJobStatus(runtimeJob.getId(), "FAILED", e.getMessage());
            throw e;
        }
    }

}
