package com.realtime.monitor.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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

        try (Connection conn = DriverManager.getConnection(jdbcUrl, config.getUsername(), password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1 FROM DUAL")) {
            if (rs.next()) {
                return Map.of("success", true, "message", "连接成功");
            }
            return Map.of("success", false, "error", "连接失败: 无返回结果");
        } catch (SQLException e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 发现数据库 Schema
     */
    public List<String> discoverSchemas(DataSourceConfig config) throws Exception {
        String jdbcUrl = buildJdbcUrl(config);
        // config 来自 DataSourceService.loadDataSource 时密码已解密，直接使用
        String password = config.getPassword();
        String sql = "SELECT DISTINCT owner FROM all_tables " +
                "WHERE owner NOT IN ('SYS','SYSTEM','OUTLN','DBSNMP','APPQOSSYS'," +
                "'WMSYS','EXFSYS','CTXSYS','XDB','ANONYMOUS'," +
                "'ORDSYS','ORDDATA','MDSYS','OLAPSYS') " +
                "ORDER BY owner";

        List<String> schemas = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(jdbcUrl, config.getUsername(), password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                schemas.add(rs.getString(1));
            }
        }
        return schemas;
    }

    /**
     * 发现 Schema 中的表
     */
    public List<Map<String, Object>> discoverTables(DataSourceConfig config, String schema) throws Exception {
        String jdbcUrl = buildJdbcUrl(config);
        // config 来自 DataSourceService.loadDataSource 时密码已解密，直接使用
        String password = config.getPassword();
        String sql = "SELECT t.table_name, " +
                "CAST(NVL(t.num_rows, 0) AS NUMBER(10)) AS row_count, " +
                "(SELECT COUNT(*) FROM all_tab_columns c WHERE c.owner = ? AND c.table_name = t.table_name) AS col_count " +
                "FROM all_tables t " +
                "WHERE t.owner = ? " +
                "AND t.table_name NOT LIKE 'BIN$%' " +
                "AND t.table_name NOT LIKE '%$%' " +
                "AND t.temporary = 'N' " +
                "ORDER BY t.table_name, t.num_rows DESC NULLS LAST";

        Map<String, Map<String, Object>> uniqueTables = new LinkedHashMap<>();

        try (Connection conn = DriverManager.getConnection(jdbcUrl, config.getUsername(), password);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, schema.toUpperCase());
            stmt.setString(2, schema.toUpperCase());

            log.info("查询 Schema {} 的表列表", schema);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("table_name");

                    if (!uniqueTables.containsKey(name)) {
                        long rowCount = rs.getLong("row_count");
                        int colCount = rs.getInt("col_count");

                        Map<String, Object> table = new HashMap<>();
                        table.put("name", name);
                        table.put("rows", rowCount);
                        table.put("columns", colCount);

                        uniqueTables.put(name, table);
                        log.debug("添加表: {} (行数: {}, 列数: {})", name, rowCount, colCount);
                    } else {
                        log.warn("跳过重复表: {} (行数: {})", name, rs.getLong("row_count"));
                    }
                }
            }
        }

        log.info("Schema {} 共发现 {} 个唯一表", schema, uniqueTables.size());
        return new ArrayList<>(uniqueTables.values());
    }

    private String buildJdbcUrl(DataSourceConfig config) {
        String host = resolveHost(config.getHost());
        String safeHost = URLEncoder.encode(host, StandardCharsets.UTF_8);

        return String.format("jdbc:oracle:thin:@%s:%d:%s",
                safeHost, config.getPort(), config.getSid());
    }

    /**
     * 将 Docker 内部主机名转换为本地可访问地址。
     *
     * 数据源配置可能在 Docker 环境中保存，使用 host.docker.internal 作为主机名。
     * 在本地（非 Docker）环境中，该主机名无法解析，需替换为 localhost。
     */
    private String resolveHost(String host) {
        if ("host.docker.internal".equalsIgnoreCase(host)) {
            // 在 Docker 容器内，host.docker.internal 是有效的，不需要替换
            // 只有在本地（非 Docker）环境中才替换为 localhost
            if (!isRunningInsideDocker()) {
                log.debug("Replacing host.docker.internal with localhost for local execution");
                return "localhost";
            }
        }
        return host;
    }

    /**
     * 检测当前是否运行在 Docker 容器内。
     * 兼容 cgroup v1 和 v2。
     */
    private boolean isRunningInsideDocker() {
        // 方法1: 检查 /.dockerenv 文件（最可靠）
        if (java.nio.file.Files.exists(java.nio.file.Paths.get("/.dockerenv"))) {
            return true;
        }
        // 方法2: 检查 /proc/1/cgroup（cgroup v1 包含 "docker"）
        try {
            java.nio.file.Path cgroupPath = java.nio.file.Paths.get("/proc/1/cgroup");
            if (java.nio.file.Files.exists(cgroupPath)) {
                String content = java.nio.file.Files.readString(cgroupPath);
                if (content.contains("docker") || content.contains("kubepods")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        // 方法3: 检查容器特有的环境变量（如 HOSTNAME 格式为容器 ID）
        String hostname = System.getenv("HOSTNAME");
        if (hostname != null && hostname.matches("[a-f0-9]{12,}")) {
            return true;
        }
        return false;
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
