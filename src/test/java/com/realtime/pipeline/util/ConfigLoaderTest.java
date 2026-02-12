package com.realtime.pipeline.util;

import com.realtime.pipeline.config.PipelineConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置加载器测试
 * 测试配置文件加载和配置验证功能
 */
class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    /**
     * 测试加载默认配置文件
     * 验证需求: 10.1 - 支持通过配置文件设置系统参数
     */
    @Test
    void testLoadDefaultConfig() throws IOException {
        // 加载默认配置文件
        PipelineConfig config = ConfigLoader.loadConfig();

        // 验证配置加载成功
        assertNotNull(config, "Config should not be null");
        assertNotNull(config.getDatabase(), "Database config should not be null");
        assertNotNull(config.getDatahub(), "DataHub config should not be null");
        assertNotNull(config.getFlink(), "Flink config should not be null");
        assertNotNull(config.getOutput(), "Output config should not be null");
        assertNotNull(config.getMonitoring(), "Monitoring config should not be null");

        // 验证数据库配置
        assertEquals("localhost", config.getDatabase().getHost());
        assertEquals(2881, config.getDatabase().getPort());
        assertEquals("test_schema", config.getDatabase().getSchema());

        // 验证Flink配置
        assertEquals(4, config.getFlink().getParallelism());
        assertEquals(300000L, config.getFlink().getCheckpointInterval());

        // 验证输出配置
        assertEquals("json", config.getOutput().getFormat());
        assertEquals("/data/output", config.getOutput().getPath());
    }

    /**
     * 测试加载指定的有效配置文件
     * 验证需求: 10.1 - 支持通过配置文件设置系统参数
     */
    @Test
    void testLoadValidConfigFile() throws IOException {
        PipelineConfig config = ConfigLoader.loadConfig("test-config-valid.yml");

        assertNotNull(config);
        assertEquals("test-db-host", config.getDatabase().getHost());
        assertEquals(3306, config.getDatabase().getPort());
        assertEquals("test_user", config.getDatabase().getUsername());
        assertEquals("test_schema", config.getDatabase().getSchema());
        assertEquals(2, config.getDatabase().getTables().size());
        
        assertEquals("test_project", config.getDatahub().getProject());
        assertEquals("test_topic", config.getDatahub().getTopic());
        assertEquals("EARLIEST", config.getDatahub().getStartPosition());
        
        assertEquals(2, config.getFlink().getParallelism());
        assertEquals("hashmap", config.getFlink().getStateBackendType());
        
        assertEquals("/tmp/test-output", config.getOutput().getPath());
        assertEquals("json", config.getOutput().getFormat());
    }

    /**
     * 测试配置文件不存在的情况
     * 验证需求: 10.4 - 配置参数无效时拒绝启动并返回错误信息
     */
    @Test
    void testLoadNonExistentConfigFile() {
        IOException exception = assertThrows(IOException.class, () -> {
            ConfigLoader.loadConfig("non-existent-config.yml");
        });
        
        assertTrue(exception.getMessage().contains("not found"));
    }

    /**
     * 测试配置验证 - 有效配置
     * 验证需求: 10.3 - 验证配置参数的有效性
     */
    @Test
    void testConfigValidation() throws IOException {
        PipelineConfig config = ConfigLoader.loadConfig("test-config-valid.yml");
        
        // 验证配置验证方法不抛出异常
        assertDoesNotThrow(() -> config.validate());
    }

    /**
     * 测试配置验证 - 缺少必需配置
     * 验证需求: 10.3, 10.4 - 验证配置参数的有效性，无效时拒绝启动
     */
    @Test
    void testConfigValidationMissingDatabase() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ConfigLoader.loadConfig("test-config-invalid-missing-database.yml");
        });
        
        assertTrue(exception.getMessage().contains("Database configuration is required"));
    }

    /**
     * 测试配置验证 - 无效的端口号
     * 验证需求: 10.3, 10.4 - 验证配置参数的有效性，无效时拒绝启动
     */
    @Test
    void testConfigValidationInvalidPort() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ConfigLoader.loadConfig("test-config-invalid-port.yml");
        });
        
        assertTrue(exception.getMessage().contains("port must be between 1 and 65535"));
    }

    /**
     * 测试配置验证 - 无效的输出格式
     * 验证需求: 10.3, 10.4 - 验证配置参数的有效性，无效时拒绝启动
     */
    @Test
    void testConfigValidationInvalidFormat() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ConfigLoader.loadConfig("test-config-invalid-format.yml");
        });
        
        assertTrue(exception.getMessage().contains("format must be"));
    }

    /**
     * 测试配置验证 - 无效的并行度（负数）
     * 验证需求: 10.3, 10.4 - 验证配置参数的有效性
     */
    @Test
    void testConfigValidationInvalidParallelism() throws IOException {
        String invalidConfig = "database:\n" +
                "  host: localhost\n" +
                "  port: 3306\n" +
                "  username: user\n" +
                "  password: pass\n" +
                "  schema: test\n" +
                "  tables: [table1]\n" +
                "datahub:\n" +
                "  endpoint: https://test.com\n" +
                "  accessId: id\n" +
                "  accessKey: key\n" +
                "  project: proj\n" +
                "  topic: topic\n" +
                "  consumerGroup: group\n" +
                "flink:\n" +
                "  parallelism: -1\n" +  // 无效值
                "  checkpointInterval: 60000\n" +
                "  checkpointTimeout: 120000\n" +
                "  checkpointDir: /tmp/cp\n" +
                "output:\n" +
                "  path: /tmp/out\n" +
                "  format: json\n";
        
        File configFile = tempDir.resolve("invalid-parallelism.yml").toFile();
        Files.writeString(configFile.toPath(), invalidConfig);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ConfigLoader.loadConfig(configFile.getAbsolutePath());
        });
        
        assertTrue(exception.getMessage().contains("parallelism must be positive"));
    }

    /**
     * 测试配置验证 - 空的数据库主机
     * 验证需求: 10.3, 10.4 - 验证配置参数的有效性
     */
    @Test
    void testConfigValidationEmptyHost() throws IOException {
        String invalidConfig = "database:\n" +
                "  host: ''\n" +  // 空字符串
                "  port: 3306\n" +
                "  username: user\n" +
                "  password: pass\n" +
                "  schema: test\n" +
                "  tables: [table1]\n" +
                "datahub:\n" +
                "  endpoint: https://test.com\n" +
                "  accessId: id\n" +
                "  accessKey: key\n" +
                "  project: proj\n" +
                "  topic: topic\n" +
                "  consumerGroup: group\n" +
                "flink:\n" +
                "  parallelism: 1\n" +
                "  checkpointInterval: 60000\n" +
                "  checkpointTimeout: 120000\n" +
                "  checkpointDir: /tmp/cp\n" +
                "output:\n" +
                "  path: /tmp/out\n" +
                "  format: json\n";
        
        File configFile = tempDir.resolve("empty-host.yml").toFile();
        Files.writeString(configFile.toPath(), invalidConfig);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ConfigLoader.loadConfig(configFile.getAbsolutePath());
        });
        
        assertTrue(exception.getMessage().contains("host is required"));
    }

    /**
     * 测试配置验证 - 空的表列表
     * 验证需求: 10.3, 10.4 - 验证配置参数的有效性
     */
    @Test
    void testConfigValidationEmptyTables() throws IOException {
        String invalidConfig = "database:\n" +
                "  host: localhost\n" +
                "  port: 3306\n" +
                "  username: user\n" +
                "  password: pass\n" +
                "  schema: test\n" +
                "  tables: []\n" +  // 空列表
                "datahub:\n" +
                "  endpoint: https://test.com\n" +
                "  accessId: id\n" +
                "  accessKey: key\n" +
                "  project: proj\n" +
                "  topic: topic\n" +
                "  consumerGroup: group\n" +
                "flink:\n" +
                "  parallelism: 1\n" +
                "  checkpointInterval: 60000\n" +
                "  checkpointTimeout: 120000\n" +
                "  checkpointDir: /tmp/cp\n" +
                "output:\n" +
                "  path: /tmp/out\n" +
                "  format: json\n";
        
        File configFile = tempDir.resolve("empty-tables.yml").toFile();
        Files.writeString(configFile.toPath(), invalidConfig);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ConfigLoader.loadConfig(configFile.getAbsolutePath());
        });
        
        assertTrue(exception.getMessage().contains("At least one table must be specified"));
    }

    /**
     * 测试配置验证 - 无效的状态后端类型
     * 验证需求: 10.3, 10.4 - 验证配置参数的有效性
     */
    @Test
    void testConfigValidationInvalidStateBackend() throws IOException {
        String invalidConfig = "database:\n" +
                "  host: localhost\n" +
                "  port: 3306\n" +
                "  username: user\n" +
                "  password: pass\n" +
                "  schema: test\n" +
                "  tables: [table1]\n" +
                "datahub:\n" +
                "  endpoint: https://test.com\n" +
                "  accessId: id\n" +
                "  accessKey: key\n" +
                "  project: proj\n" +
                "  topic: topic\n" +
                "  consumerGroup: group\n" +
                "flink:\n" +
                "  parallelism: 1\n" +
                "  checkpointInterval: 60000\n" +
                "  checkpointTimeout: 120000\n" +
                "  stateBackendType: invalid\n" +  // 无效类型
                "  checkpointDir: /tmp/cp\n" +
                "output:\n" +
                "  path: /tmp/out\n" +
                "  format: json\n";
        
        File configFile = tempDir.resolve("invalid-backend.yml").toFile();
        Files.writeString(configFile.toPath(), invalidConfig);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ConfigLoader.loadConfig(configFile.getAbsolutePath());
        });
        
        assertTrue(exception.getMessage().contains("stateBackendType must be"));
    }

    /**
     * 测试配置验证 - 监控配置的阈值范围
     * 验证需求: 10.3, 10.4 - 验证配置参数的有效性
     */
    @Test
    void testConfigValidationMonitoringThresholds() throws IOException {
        String invalidConfig = "database:\n" +
                "  host: localhost\n" +
                "  port: 3306\n" +
                "  username: user\n" +
                "  password: pass\n" +
                "  schema: test\n" +
                "  tables: [table1]\n" +
                "datahub:\n" +
                "  endpoint: https://test.com\n" +
                "  accessId: id\n" +
                "  accessKey: key\n" +
                "  project: proj\n" +
                "  topic: topic\n" +
                "  consumerGroup: group\n" +
                "flink:\n" +
                "  parallelism: 1\n" +
                "  checkpointInterval: 60000\n" +
                "  checkpointTimeout: 120000\n" +
                "  checkpointDir: /tmp/cp\n" +
                "output:\n" +
                "  path: /tmp/out\n" +
                "  format: json\n" +
                "monitoring:\n" +
                "  enabled: true\n" +
                "  metricsInterval: 60\n" +
                "  latencyThreshold: 60000\n" +
                "  checkpointFailureRateThreshold: 1.5\n" +  // 超出范围 (0-1)
                "  loadThreshold: 0.8\n" +
                "  backpressureThreshold: 0.8\n" +
                "  healthCheckPort: 8081\n" +
                "  alertMethod: log\n";
        
        File configFile = tempDir.resolve("invalid-threshold.yml").toFile();
        Files.writeString(configFile.toPath(), invalidConfig);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ConfigLoader.loadConfig(configFile.getAbsolutePath());
        });
        
        assertTrue(exception.getMessage().contains("checkpointFailureRateThreshold must be between 0 and 1"));
    }

    /**
     * 测试从文件系统加载配置
     * 验证需求: 10.1 - 支持通过配置文件设置系统参数
     */
    @Test
    void testLoadConfigFromFileSystem() throws IOException {
        String configContent = "database:\n" +
                "  host: filesystem-host\n" +
                "  port: 3306\n" +
                "  username: fs_user\n" +
                "  password: fs_pass\n" +
                "  schema: fs_schema\n" +
                "  tables: [fs_table]\n" +
                "datahub:\n" +
                "  endpoint: https://fs-datahub.com\n" +
                "  accessId: fs_id\n" +
                "  accessKey: fs_key\n" +
                "  project: fs_proj\n" +
                "  topic: fs_topic\n" +
                "  consumerGroup: fs_group\n" +
                "flink:\n" +
                "  parallelism: 8\n" +
                "  checkpointInterval: 120000\n" +
                "  checkpointTimeout: 240000\n" +
                "  checkpointDir: /tmp/fs-cp\n" +
                "output:\n" +
                "  path: /tmp/fs-out\n" +
                "  format: parquet\n";
        
        File configFile = tempDir.resolve("filesystem-config.yml").toFile();
        Files.writeString(configFile.toPath(), configContent);
        
        PipelineConfig config = ConfigLoader.loadConfig(configFile.getAbsolutePath());
        
        assertNotNull(config);
        assertEquals("filesystem-host", config.getDatabase().getHost());
        assertEquals("fs_user", config.getDatabase().getUsername());
        assertEquals(8, config.getFlink().getParallelism());
        assertEquals("parquet", config.getOutput().getFormat());
    }

    /**
     * 测试配置的默认值
     * 验证需求: 10.1 - 配置应该有合理的默认值
     */
    @Test
    void testConfigDefaultValues() throws IOException {
        PipelineConfig config = ConfigLoader.loadConfig("test-config-valid.yml");
        
        // 验证默认值
        assertEquals(30, config.getDatabase().getConnectionTimeout());
        assertEquals(30, config.getDatabase().getReconnectInterval());
        assertEquals(3, config.getDatahub().getMaxRetries());
        assertEquals(2, config.getDatahub().getRetryBackoff());
        assertEquals(1, config.getFlink().getMaxConcurrentCheckpoints());
        assertEquals(3, config.getFlink().getRetainedCheckpoints());
        assertEquals("none", config.getOutput().getCompression());
        assertEquals(true, config.getMonitoring().isEnabled());
    }

    /**
     * 测试环境变量覆盖功能的存在性
     * 验证需求: 10.2 - 支持通过环境变量覆盖配置文件参数
     * 
     * 注意：由于Java运行时环境变量的限制，我们只能验证环境变量覆盖逻辑的存在，
     * 而不能在单元测试中实际测试环境变量的覆盖效果。
     * 实际的环境变量覆盖功能需要通过集成测试或手动测试来验证。
     */
    @Test
    void testEnvironmentVariableOverrideLogicExists() {
        // 验证ConfigLoader类中存在环境变量覆盖的逻辑
        // 通过检查类的方法来确认功能存在
        try {
            java.lang.reflect.Method method = ConfigLoader.class.getDeclaredMethod(
                "applyEnvironmentOverrides", 
                com.realtime.pipeline.config.PipelineConfig.class
            );
            assertNotNull(method, "Environment variable override method should exist");
        } catch (NoSuchMethodException e) {
            fail("ConfigLoader should have applyEnvironmentOverrides method");
        }
    }

    /**
     * 测试环境变量覆盖 - 使用反射模拟
     * 验证需求: 10.2 - 支持通过环境变量覆盖配置文件参数
     */
    @Test
    void testEnvironmentVariableOverrideWithReflection() throws Exception {
        // 加载基础配置
        PipelineConfig config = ConfigLoader.loadConfig("test-config-valid.yml");
        
        // 保存原始值
        String originalHost = config.getDatabase().getHost();
        int originalPort = config.getDatabase().getPort();
        
        // 创建模拟的环境变量
        java.util.Map<String, String> mockEnv = new java.util.HashMap<>();
        mockEnv.put("PIPELINE_DATABASE_HOST", "overridden-host");
        mockEnv.put("PIPELINE_DATABASE_PORT", "9999");
        mockEnv.put("PIPELINE_FLINK_PARALLELISM", "16");
        
        // 使用反射调用overrideFromEnv方法
        java.lang.reflect.Method method = ConfigLoader.class.getDeclaredMethod(
            "overrideFromEnv", Object.class, String.class, java.util.Map.class);
        method.setAccessible(true);
        
        // 应用数据库配置覆盖
        method.invoke(null, config.getDatabase(), "PIPELINE_DATABASE_", mockEnv);
        
        // 验证覆盖生效
        assertEquals("overridden-host", config.getDatabase().getHost());
        assertEquals(9999, config.getDatabase().getPort());
        
        // 应用Flink配置覆盖
        method.invoke(null, config.getFlink(), "PIPELINE_FLINK_", mockEnv);
        assertEquals(16, config.getFlink().getParallelism());
    }

    /**
     * 测试环境变量覆盖 - 列表类型
     * 验证需求: 10.2 - 支持通过环境变量覆盖配置文件参数
     */
    @Test
    void testEnvironmentVariableOverrideListType() throws Exception {
        // 加载基础配置
        PipelineConfig config = ConfigLoader.loadConfig("test-config-valid.yml");
        
        // 创建模拟的环境变量（逗号分隔的列表）
        java.util.Map<String, String> mockEnv = new java.util.HashMap<>();
        mockEnv.put("PIPELINE_DATABASE_TABLES", "new_table1,new_table2,new_table3");
        
        // 使用反射调用overrideFromEnv方法
        java.lang.reflect.Method method = ConfigLoader.class.getDeclaredMethod(
            "overrideFromEnv", Object.class, String.class, java.util.Map.class);
        method.setAccessible(true);
        method.invoke(null, config.getDatabase(), "PIPELINE_DATABASE_", mockEnv);
        
        // 验证列表覆盖生效
        assertEquals(3, config.getDatabase().getTables().size());
        assertTrue(config.getDatabase().getTables().contains("new_table1"));
        assertTrue(config.getDatabase().getTables().contains("new_table2"));
        assertTrue(config.getDatabase().getTables().contains("new_table3"));
    }

    /**
     * 测试环境变量覆盖 - 布尔类型
     * 验证需求: 10.2 - 支持通过环境变量覆盖配置文件参数
     */
    @Test
    void testEnvironmentVariableOverrideBooleanType() throws Exception {
        // 加载基础配置
        PipelineConfig config = ConfigLoader.loadConfig("test-config-valid.yml");
        
        // 创建模拟的环境变量
        java.util.Map<String, String> mockEnv = new java.util.HashMap<>();
        mockEnv.put("PIPELINE_MONITORING_ENABLED", "false");
        
        // 使用反射调用overrideFromEnv方法
        java.lang.reflect.Method method = ConfigLoader.class.getDeclaredMethod(
            "overrideFromEnv", Object.class, String.class, java.util.Map.class);
        method.setAccessible(true);
        method.invoke(null, config.getMonitoring(), "PIPELINE_MONITORING_", mockEnv);
        
        // 验证布尔值覆盖生效
        assertFalse(config.getMonitoring().isEnabled());
    }

    /**
     * 测试环境变量覆盖 - Long类型
     * 验证需求: 10.2 - 支持通过环境变量覆盖配置文件参数
     */
    @Test
    void testEnvironmentVariableOverrideLongType() throws Exception {
        // 加载基础配置
        PipelineConfig config = ConfigLoader.loadConfig("test-config-valid.yml");
        
        // 创建模拟的环境变量
        java.util.Map<String, String> mockEnv = new java.util.HashMap<>();
        mockEnv.put("PIPELINE_FLINK_CHECKPOINTINTERVAL", "600000");
        mockEnv.put("PIPELINE_OUTPUT_ROLLINGSIZEBYTES", "2147483648");
        
        // 使用反射调用overrideFromEnv方法
        java.lang.reflect.Method method = ConfigLoader.class.getDeclaredMethod(
            "overrideFromEnv", Object.class, String.class, java.util.Map.class);
        method.setAccessible(true);
        
        method.invoke(null, config.getFlink(), "PIPELINE_FLINK_", mockEnv);
        method.invoke(null, config.getOutput(), "PIPELINE_OUTPUT_", mockEnv);
        
        // 验证Long类型覆盖生效
        assertEquals(600000L, config.getFlink().getCheckpointInterval());
        assertEquals(2147483648L, config.getOutput().getRollingSizeBytes());
    }

    /**
     * 测试配置验证 - 无效的Checkpoint间隔
     * 验证需求: 10.3, 10.4 - 验证配置参数的有效性
     */
    @Test
    void testConfigValidationInvalidCheckpointInterval() throws IOException {
        String invalidConfig = "database:\n" +
                "  host: localhost\n" +
                "  port: 3306\n" +
                "  username: user\n" +
                "  password: pass\n" +
                "  schema: test\n" +
                "  tables: [table1]\n" +
                "datahub:\n" +
                "  endpoint: https://test.com\n" +
                "  accessId: id\n" +
                "  accessKey: key\n" +
                "  project: proj\n" +
                "  topic: topic\n" +
                "  consumerGroup: group\n" +
                "flink:\n" +
                "  parallelism: 1\n" +
                "  checkpointInterval: -1000\n" +  // 无效值
                "  checkpointTimeout: 120000\n" +
                "  checkpointDir: /tmp/cp\n" +
                "output:\n" +
                "  path: /tmp/out\n" +
                "  format: json\n";
        
        File configFile = tempDir.resolve("invalid-checkpoint-interval.yml").toFile();
        Files.writeString(configFile.toPath(), invalidConfig);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ConfigLoader.loadConfig(configFile.getAbsolutePath());
        });
        
        assertTrue(exception.getMessage().contains("checkpointInterval must be positive"));
    }

    /**
     * 测试配置验证 - 无效的重启策略
     * 验证需求: 10.3, 10.4 - 验证配置参数的有效性
     */
    @Test
    void testConfigValidationInvalidRestartStrategy() throws IOException {
        String invalidConfig = "database:\n" +
                "  host: localhost\n" +
                "  port: 3306\n" +
                "  username: user\n" +
                "  password: pass\n" +
                "  schema: test\n" +
                "  tables: [table1]\n" +
                "datahub:\n" +
                "  endpoint: https://test.com\n" +
                "  accessId: id\n" +
                "  accessKey: key\n" +
                "  project: proj\n" +
                "  topic: topic\n" +
                "  consumerGroup: group\n" +
                "flink:\n" +
                "  parallelism: 1\n" +
                "  checkpointInterval: 60000\n" +
                "  checkpointTimeout: 120000\n" +
                "  checkpointDir: /tmp/cp\n" +
                "  restartStrategy: invalid-strategy\n" +  // 无效策略
                "output:\n" +
                "  path: /tmp/out\n" +
                "  format: json\n";
        
        File configFile = tempDir.resolve("invalid-restart-strategy.yml").toFile();
        Files.writeString(configFile.toPath(), invalidConfig);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ConfigLoader.loadConfig(configFile.getAbsolutePath());
        });
        
        assertTrue(exception.getMessage().contains("restartStrategy must be"));
    }

    /**
     * 测试配置验证 - 缺少DataHub配置
     * 验证需求: 10.3, 10.4 - 验证配置参数的有效性
     */
    @Test
    void testConfigValidationMissingDataHub() throws IOException {
        String invalidConfig = "database:\n" +
                "  host: localhost\n" +
                "  port: 3306\n" +
                "  username: user\n" +
                "  password: pass\n" +
                "  schema: test\n" +
                "  tables: [table1]\n" +
                "flink:\n" +
                "  parallelism: 1\n" +
                "  checkpointInterval: 60000\n" +
                "  checkpointTimeout: 120000\n" +
                "  checkpointDir: /tmp/cp\n" +
                "output:\n" +
                "  path: /tmp/out\n" +
                "  format: json\n";
        
        File configFile = tempDir.resolve("missing-datahub.yml").toFile();
        Files.writeString(configFile.toPath(), invalidConfig);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ConfigLoader.loadConfig(configFile.getAbsolutePath());
        });
        
        assertTrue(exception.getMessage().contains("DataHub configuration is required"));
    }

    /**
     * 测试配置验证 - 缺少输出配置
     * 验证需求: 10.3, 10.4 - 验证配置参数的有效性
     */
    @Test
    void testConfigValidationMissingOutput() throws IOException {
        String invalidConfig = "database:\n" +
                "  host: localhost\n" +
                "  port: 3306\n" +
                "  username: user\n" +
                "  password: pass\n" +
                "  schema: test\n" +
                "  tables: [table1]\n" +
                "datahub:\n" +
                "  endpoint: https://test.com\n" +
                "  accessId: id\n" +
                "  accessKey: key\n" +
                "  project: proj\n" +
                "  topic: topic\n" +
                "  consumerGroup: group\n" +
                "flink:\n" +
                "  parallelism: 1\n" +
                "  checkpointInterval: 60000\n" +
                "  checkpointTimeout: 120000\n" +
                "  checkpointDir: /tmp/cp\n";
        
        File configFile = tempDir.resolve("missing-output.yml").toFile();
        Files.writeString(configFile.toPath(), invalidConfig);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ConfigLoader.loadConfig(configFile.getAbsolutePath());
        });
        
        assertTrue(exception.getMessage().contains("Output configuration is required"));
    }

    /**
     * 测试配置验证 - 空的DataHub endpoint
     * 验证需求: 10.3, 10.4 - 验证配置参数的有效性
     */
    @Test
    void testConfigValidationEmptyDataHubEndpoint() throws IOException {
        String invalidConfig = "database:\n" +
                "  host: localhost\n" +
                "  port: 3306\n" +
                "  username: user\n" +
                "  password: pass\n" +
                "  schema: test\n" +
                "  tables: [table1]\n" +
                "datahub:\n" +
                "  endpoint: ''\n" +  // 空字符串
                "  accessId: id\n" +
                "  accessKey: key\n" +
                "  project: proj\n" +
                "  topic: topic\n" +
                "  consumerGroup: group\n" +
                "flink:\n" +
                "  parallelism: 1\n" +
                "  checkpointInterval: 60000\n" +
                "  checkpointTimeout: 120000\n" +
                "  checkpointDir: /tmp/cp\n" +
                "output:\n" +
                "  path: /tmp/out\n" +
                "  format: json\n";
        
        File configFile = tempDir.resolve("empty-endpoint.yml").toFile();
        Files.writeString(configFile.toPath(), invalidConfig);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ConfigLoader.loadConfig(configFile.getAbsolutePath());
        });
        
        assertTrue(exception.getMessage().contains("endpoint is required"));
    }

    /**
     * 测试配置验证 - 空的输出路径
     * 验证需求: 10.3, 10.4 - 验证配置参数的有效性
     */
    @Test
    void testConfigValidationEmptyOutputPath() throws IOException {
        String invalidConfig = "database:\n" +
                "  host: localhost\n" +
                "  port: 3306\n" +
                "  username: user\n" +
                "  password: pass\n" +
                "  schema: test\n" +
                "  tables: [table1]\n" +
                "datahub:\n" +
                "  endpoint: https://test.com\n" +
                "  accessId: id\n" +
                "  accessKey: key\n" +
                "  project: proj\n" +
                "  topic: topic\n" +
                "  consumerGroup: group\n" +
                "flink:\n" +
                "  parallelism: 1\n" +
                "  checkpointInterval: 60000\n" +
                "  checkpointTimeout: 120000\n" +
                "  checkpointDir: /tmp/cp\n" +
                "output:\n" +
                "  path: ''\n" +  // 空字符串
                "  format: json\n";
        
        File configFile = tempDir.resolve("empty-output-path.yml").toFile();
        Files.writeString(configFile.toPath(), invalidConfig);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ConfigLoader.loadConfig(configFile.getAbsolutePath());
        });
        
        assertTrue(exception.getMessage().contains("path is required"));
    }

    /**
     * 测试配置验证 - 无效的压缩算法
     * 验证需求: 10.3, 10.4 - 验证配置参数的有效性
     */
    @Test
    void testConfigValidationInvalidCompression() throws IOException {
        String invalidConfig = "database:\n" +
                "  host: localhost\n" +
                "  port: 3306\n" +
                "  username: user\n" +
                "  password: pass\n" +
                "  schema: test\n" +
                "  tables: [table1]\n" +
                "datahub:\n" +
                "  endpoint: https://test.com\n" +
                "  accessId: id\n" +
                "  accessKey: key\n" +
                "  project: proj\n" +
                "  topic: topic\n" +
                "  consumerGroup: group\n" +
                "flink:\n" +
                "  parallelism: 1\n" +
                "  checkpointInterval: 60000\n" +
                "  checkpointTimeout: 120000\n" +
                "  checkpointDir: /tmp/cp\n" +
                "output:\n" +
                "  path: /tmp/out\n" +
                "  format: json\n" +
                "  compression: invalid-compression\n";  // 无效压缩算法
        
        File configFile = tempDir.resolve("invalid-compression.yml").toFile();
        Files.writeString(configFile.toPath(), invalidConfig);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ConfigLoader.loadConfig(configFile.getAbsolutePath());
        });
        
        assertTrue(exception.getMessage().contains("compression must be"));
    }

    /**
     * 测试配置的所有有效格式
     * 验证需求: 10.1, 10.7 - 支持配置输出格式
     */
    @Test
    void testConfigValidFormats() throws IOException {
        String[] validFormats = {"json", "parquet", "csv"};
        
        for (String format : validFormats) {
            String config = "database:\n" +
                    "  host: localhost\n" +
                    "  port: 3306\n" +
                    "  username: user\n" +
                    "  password: pass\n" +
                    "  schema: test\n" +
                    "  tables: [table1]\n" +
                    "datahub:\n" +
                    "  endpoint: https://test.com\n" +
                    "  accessId: id\n" +
                    "  accessKey: key\n" +
                    "  project: proj\n" +
                    "  topic: topic\n" +
                    "  consumerGroup: group\n" +
                    "flink:\n" +
                    "  parallelism: 1\n" +
                    "  checkpointInterval: 60000\n" +
                    "  checkpointTimeout: 120000\n" +
                    "  checkpointDir: /tmp/cp\n" +
                    "output:\n" +
                    "  path: /tmp/out\n" +
                    "  format: " + format + "\n";
            
            File configFile = tempDir.resolve("valid-format-" + format + ".yml").toFile();
            Files.writeString(configFile.toPath(), config);
            
            PipelineConfig loadedConfig = ConfigLoader.loadConfig(configFile.getAbsolutePath());
            assertEquals(format, loadedConfig.getOutput().getFormat());
        }
    }

    /**
     * 测试配置的所有有效压缩算法
     * 验证需求: 10.1, 10.7 - 支持配置压缩算法
     */
    @Test
    void testConfigValidCompressions() throws IOException {
        String[] validCompressions = {"none", "gzip", "snappy"};
        
        for (String compression : validCompressions) {
            String config = "database:\n" +
                    "  host: localhost\n" +
                    "  port: 3306\n" +
                    "  username: user\n" +
                    "  password: pass\n" +
                    "  schema: test\n" +
                    "  tables: [table1]\n" +
                    "datahub:\n" +
                    "  endpoint: https://test.com\n" +
                    "  accessId: id\n" +
                    "  accessKey: key\n" +
                    "  project: proj\n" +
                    "  topic: topic\n" +
                    "  consumerGroup: group\n" +
                    "flink:\n" +
                    "  parallelism: 1\n" +
                    "  checkpointInterval: 60000\n" +
                    "  checkpointTimeout: 120000\n" +
                    "  checkpointDir: /tmp/cp\n" +
                    "output:\n" +
                    "  path: /tmp/out\n" +
                    "  format: json\n" +
                    "  compression: " + compression + "\n";
            
            File configFile = tempDir.resolve("valid-compression-" + compression + ".yml").toFile();
            Files.writeString(configFile.toPath(), config);
            
            PipelineConfig loadedConfig = ConfigLoader.loadConfig(configFile.getAbsolutePath());
            assertEquals(compression, loadedConfig.getOutput().getCompression());
        }
    }

    /**
     * 测试配置的所有有效状态后端类型
     * 验证需求: 10.1, 10.8 - 支持配置状态后端
     */
    @Test
    void testConfigValidStateBackends() throws IOException {
        String[] validBackends = {"hashmap", "rocksdb"};
        
        for (String backend : validBackends) {
            String config = "database:\n" +
                    "  host: localhost\n" +
                    "  port: 3306\n" +
                    "  username: user\n" +
                    "  password: pass\n" +
                    "  schema: test\n" +
                    "  tables: [table1]\n" +
                    "datahub:\n" +
                    "  endpoint: https://test.com\n" +
                    "  accessId: id\n" +
                    "  accessKey: key\n" +
                    "  project: proj\n" +
                    "  topic: topic\n" +
                    "  consumerGroup: group\n" +
                    "flink:\n" +
                    "  parallelism: 1\n" +
                    "  checkpointInterval: 60000\n" +
                    "  checkpointTimeout: 120000\n" +
                    "  stateBackendType: " + backend + "\n" +
                    "  checkpointDir: /tmp/cp\n" +
                    "output:\n" +
                    "  path: /tmp/out\n" +
                    "  format: json\n";
            
            File configFile = tempDir.resolve("valid-backend-" + backend + ".yml").toFile();
            Files.writeString(configFile.toPath(), config);
            
            PipelineConfig loadedConfig = ConfigLoader.loadConfig(configFile.getAbsolutePath());
            assertEquals(backend, loadedConfig.getFlink().getStateBackendType());
        }
    }

    /**
     * 测试配置加载后的完整性
     * 验证需求: 10.1 - 所有配置部分都应该被正确加载
     */
    @Test
    void testConfigCompletenessAfterLoading() throws IOException {
        PipelineConfig config = ConfigLoader.loadConfig("test-config-valid.yml");
        
        // 验证所有配置部分都存在且非空
        assertNotNull(config.getDatabase(), "Database config should not be null");
        assertNotNull(config.getDatahub(), "DataHub config should not be null");
        assertNotNull(config.getFlink(), "Flink config should not be null");
        assertNotNull(config.getOutput(), "Output config should not be null");
        assertNotNull(config.getMonitoring(), "Monitoring config should not be null");
        
        // 验证数据库配置的所有字段
        assertNotNull(config.getDatabase().getHost());
        assertTrue(config.getDatabase().getPort() > 0);
        assertNotNull(config.getDatabase().getUsername());
        assertNotNull(config.getDatabase().getPassword());
        assertNotNull(config.getDatabase().getSchema());
        assertNotNull(config.getDatabase().getTables());
        assertFalse(config.getDatabase().getTables().isEmpty());
        
        // 验证DataHub配置的所有字段
        assertNotNull(config.getDatahub().getEndpoint());
        assertNotNull(config.getDatahub().getAccessId());
        assertNotNull(config.getDatahub().getAccessKey());
        assertNotNull(config.getDatahub().getProject());
        assertNotNull(config.getDatahub().getTopic());
        assertNotNull(config.getDatahub().getConsumerGroup());
        
        // 验证Flink配置的所有字段
        assertTrue(config.getFlink().getParallelism() > 0);
        assertTrue(config.getFlink().getCheckpointInterval() > 0);
        assertTrue(config.getFlink().getCheckpointTimeout() > 0);
        assertNotNull(config.getFlink().getStateBackendType());
        assertNotNull(config.getFlink().getCheckpointDir());
        
        // 验证输出配置的所有字段
        assertNotNull(config.getOutput().getPath());
        assertNotNull(config.getOutput().getFormat());
        assertNotNull(config.getOutput().getCompression());
    }

    /**
     * 测试配置验证 - 无效的DataHub起始位置
     * 验证需求: 10.3, 10.4 - 验证配置参数的有效性
     */
    @Test
    void testConfigValidationInvalidStartPosition() throws IOException {
        String invalidConfig = "database:\n" +
                "  host: localhost\n" +
                "  port: 3306\n" +
                "  username: user\n" +
                "  password: pass\n" +
                "  schema: test\n" +
                "  tables: [table1]\n" +
                "datahub:\n" +
                "  endpoint: https://test.com\n" +
                "  accessId: id\n" +
                "  accessKey: key\n" +
                "  project: proj\n" +
                "  topic: topic\n" +
                "  consumerGroup: group\n" +
                "  startPosition: INVALID\n" +  // 无效位置
                "flink:\n" +
                "  parallelism: 1\n" +
                "  checkpointInterval: 60000\n" +
                "  checkpointTimeout: 120000\n" +
                "  checkpointDir: /tmp/cp\n" +
                "output:\n" +
                "  path: /tmp/out\n" +
                "  format: json\n";
        
        File configFile = tempDir.resolve("invalid-start-position.yml").toFile();
        Files.writeString(configFile.toPath(), invalidConfig);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ConfigLoader.loadConfig(configFile.getAbsolutePath());
        });
        
        assertTrue(exception.getMessage().contains("startPosition must be"));
    }

    /**
     * 测试配置验证 - 无效的告警方法
     * 验证需求: 10.3, 10.4 - 验证配置参数的有效性
     */
    @Test
    void testConfigValidationInvalidAlertMethod() throws IOException {
        String invalidConfig = "database:\n" +
                "  host: localhost\n" +
                "  port: 3306\n" +
                "  username: user\n" +
                "  password: pass\n" +
                "  schema: test\n" +
                "  tables: [table1]\n" +
                "datahub:\n" +
                "  endpoint: https://test.com\n" +
                "  accessId: id\n" +
                "  accessKey: key\n" +
                "  project: proj\n" +
                "  topic: topic\n" +
                "  consumerGroup: group\n" +
                "flink:\n" +
                "  parallelism: 1\n" +
                "  checkpointInterval: 60000\n" +
                "  checkpointTimeout: 120000\n" +
                "  checkpointDir: /tmp/cp\n" +
                "output:\n" +
                "  path: /tmp/out\n" +
                "  format: json\n" +
                "monitoring:\n" +
                "  enabled: true\n" +
                "  alertMethod: invalid-method\n";  // 无效方法
        
        File configFile = tempDir.resolve("invalid-alert-method.yml").toFile();
        Files.writeString(configFile.toPath(), invalidConfig);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ConfigLoader.loadConfig(configFile.getAbsolutePath());
        });
        
        assertTrue(exception.getMessage().contains("alertMethod must be"));
    }

    /**
     * 测试配置验证 - webhook方法缺少URL
     * 验证需求: 10.3, 10.4 - 验证配置参数的有效性
     */
    @Test
    void testConfigValidationWebhookWithoutUrl() throws IOException {
        String invalidConfig = "database:\n" +
                "  host: localhost\n" +
                "  port: 3306\n" +
                "  username: user\n" +
                "  password: pass\n" +
                "  schema: test\n" +
                "  tables: [table1]\n" +
                "datahub:\n" +
                "  endpoint: https://test.com\n" +
                "  accessId: id\n" +
                "  accessKey: key\n" +
                "  project: proj\n" +
                "  topic: topic\n" +
                "  consumerGroup: group\n" +
                "flink:\n" +
                "  parallelism: 1\n" +
                "  checkpointInterval: 60000\n" +
                "  checkpointTimeout: 120000\n" +
                "  checkpointDir: /tmp/cp\n" +
                "output:\n" +
                "  path: /tmp/out\n" +
                "  format: json\n" +
                "monitoring:\n" +
                "  enabled: true\n" +
                "  alertMethod: webhook\n";  // webhook但没有URL
        
        File configFile = tempDir.resolve("webhook-without-url.yml").toFile();
        Files.writeString(configFile.toPath(), invalidConfig);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ConfigLoader.loadConfig(configFile.getAbsolutePath());
        });
        
        assertTrue(exception.getMessage().contains("webhookUrl is required"));
    }

    /**
     * 测试配置验证 - 无效的健康检查端口
     * 验证需求: 10.3, 10.4 - 验证配置参数的有效性
     */
    @Test
    void testConfigValidationInvalidHealthCheckPort() throws IOException {
        String invalidConfig = "database:\n" +
                "  host: localhost\n" +
                "  port: 3306\n" +
                "  username: user\n" +
                "  password: pass\n" +
                "  schema: test\n" +
                "  tables: [table1]\n" +
                "datahub:\n" +
                "  endpoint: https://test.com\n" +
                "  accessId: id\n" +
                "  accessKey: key\n" +
                "  project: proj\n" +
                "  topic: topic\n" +
                "  consumerGroup: group\n" +
                "flink:\n" +
                "  parallelism: 1\n" +
                "  checkpointInterval: 60000\n" +
                "  checkpointTimeout: 120000\n" +
                "  checkpointDir: /tmp/cp\n" +
                "output:\n" +
                "  path: /tmp/out\n" +
                "  format: json\n" +
                "monitoring:\n" +
                "  enabled: true\n" +
                "  healthCheckPort: 99999\n";  // 超出范围
        
        File configFile = tempDir.resolve("invalid-health-port.yml").toFile();
        Files.writeString(configFile.toPath(), invalidConfig);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ConfigLoader.loadConfig(configFile.getAbsolutePath());
        });
        
        assertTrue(exception.getMessage().contains("healthCheckPort must be between"));
    }

    /**
     * 测试配置验证 - 负数的重试次数
     * 验证需求: 10.3, 10.4 - 验证配置参数的有效性
     */
    @Test
    void testConfigValidationNegativeMaxRetries() throws IOException {
        String invalidConfig = "database:\n" +
                "  host: localhost\n" +
                "  port: 3306\n" +
                "  username: user\n" +
                "  password: pass\n" +
                "  schema: test\n" +
                "  tables: [table1]\n" +
                "datahub:\n" +
                "  endpoint: https://test.com\n" +
                "  accessId: id\n" +
                "  accessKey: key\n" +
                "  project: proj\n" +
                "  topic: topic\n" +
                "  consumerGroup: group\n" +
                "  maxRetries: -1\n" +  // 负数
                "flink:\n" +
                "  parallelism: 1\n" +
                "  checkpointInterval: 60000\n" +
                "  checkpointTimeout: 120000\n" +
                "  checkpointDir: /tmp/cp\n" +
                "output:\n" +
                "  path: /tmp/out\n" +
                "  format: json\n";
        
        File configFile = tempDir.resolve("negative-max-retries.yml").toFile();
        Files.writeString(configFile.toPath(), invalidConfig);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ConfigLoader.loadConfig(configFile.getAbsolutePath());
        });
        
        assertTrue(exception.getMessage().contains("maxRetries must be non-negative"));
    }

    /**
     * 测试配置验证 - 无效的重试间隔
     * 验证需求: 10.3, 10.4 - 验证配置参数的有效性
     */
    @Test
    void testConfigValidationInvalidRetryBackoff() throws IOException {
        String invalidConfig = "database:\n" +
                "  host: localhost\n" +
                "  port: 3306\n" +
                "  username: user\n" +
                "  password: pass\n" +
                "  schema: test\n" +
                "  tables: [table1]\n" +
                "datahub:\n" +
                "  endpoint: https://test.com\n" +
                "  accessId: id\n" +
                "  accessKey: key\n" +
                "  project: proj\n" +
                "  topic: topic\n" +
                "  consumerGroup: group\n" +
                "flink:\n" +
                "  parallelism: 1\n" +
                "  checkpointInterval: 60000\n" +
                "  checkpointTimeout: 120000\n" +
                "  checkpointDir: /tmp/cp\n" +
                "output:\n" +
                "  path: /tmp/out\n" +
                "  format: json\n" +
                "  retryBackoff: 0\n";  // 零或负数
        
        File configFile = tempDir.resolve("invalid-retry-backoff.yml").toFile();
        Files.writeString(configFile.toPath(), invalidConfig);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ConfigLoader.loadConfig(configFile.getAbsolutePath());
        });
        
        assertTrue(exception.getMessage().contains("retryBackoff must be positive"));
    }

    /**
     * 测试配置验证 - 空的DataHub accessKey
     * 验证需求: 10.3, 10.4 - 验证配置参数的有效性
     */
    @Test
    void testConfigValidationEmptyAccessKey() throws IOException {
        String invalidConfig = "database:\n" +
                "  host: localhost\n" +
                "  port: 3306\n" +
                "  username: user\n" +
                "  password: pass\n" +
                "  schema: test\n" +
                "  tables: [table1]\n" +
                "datahub:\n" +
                "  endpoint: https://test.com\n" +
                "  accessId: id\n" +
                "  accessKey: ''\n" +  // 空字符串
                "  project: proj\n" +
                "  topic: topic\n" +
                "  consumerGroup: group\n" +
                "flink:\n" +
                "  parallelism: 1\n" +
                "  checkpointInterval: 60000\n" +
                "  checkpointTimeout: 120000\n" +
                "  checkpointDir: /tmp/cp\n" +
                "output:\n" +
                "  path: /tmp/out\n" +
                "  format: json\n";
        
        File configFile = tempDir.resolve("empty-access-key.yml").toFile();
        Files.writeString(configFile.toPath(), invalidConfig);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ConfigLoader.loadConfig(configFile.getAbsolutePath());
        });
        
        assertTrue(exception.getMessage().contains("accessKey is required"));
    }

    /**
     * 测试配置验证 - 空的checkpoint目录
     * 验证需求: 10.3, 10.4 - 验证配置参数的有效性
     */
    @Test
    void testConfigValidationEmptyCheckpointDir() throws IOException {
        String invalidConfig = "database:\n" +
                "  host: localhost\n" +
                "  port: 3306\n" +
                "  username: user\n" +
                "  password: pass\n" +
                "  schema: test\n" +
                "  tables: [table1]\n" +
                "datahub:\n" +
                "  endpoint: https://test.com\n" +
                "  accessId: id\n" +
                "  accessKey: key\n" +
                "  project: proj\n" +
                "  topic: topic\n" +
                "  consumerGroup: group\n" +
                "flink:\n" +
                "  parallelism: 1\n" +
                "  checkpointInterval: 60000\n" +
                "  checkpointTimeout: 120000\n" +
                "  checkpointDir: ''\n" +  // 空字符串
                "output:\n" +
                "  path: /tmp/out\n" +
                "  format: json\n";
        
        File configFile = tempDir.resolve("empty-checkpoint-dir.yml").toFile();
        Files.writeString(configFile.toPath(), invalidConfig);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ConfigLoader.loadConfig(configFile.getAbsolutePath());
        });
        
        assertTrue(exception.getMessage().contains("checkpointDir is required"));
    }

    /**
     * 测试配置场景 - 最小有效配置
     * 验证需求: 10.1 - 支持通过配置文件设置系统参数
     */
    @Test
    void testMinimalValidConfig() throws IOException {
        String minimalConfig = "database:\n" +
                "  host: localhost\n" +
                "  port: 3306\n" +
                "  username: user\n" +
                "  password: pass\n" +
                "  schema: test\n" +
                "  tables: [table1]\n" +
                "datahub:\n" +
                "  endpoint: https://test.com\n" +
                "  accessId: id\n" +
                "  accessKey: key\n" +
                "  project: proj\n" +
                "  topic: topic\n" +
                "  consumerGroup: group\n" +
                "flink:\n" +
                "  parallelism: 1\n" +
                "  checkpointInterval: 60000\n" +
                "  checkpointTimeout: 120000\n" +
                "  checkpointDir: /tmp/cp\n" +
                "output:\n" +
                "  path: /tmp/out\n" +
                "  format: json\n";
        
        File configFile = tempDir.resolve("minimal-valid-config.yml").toFile();
        Files.writeString(configFile.toPath(), minimalConfig);
        
        PipelineConfig config = ConfigLoader.loadConfig(configFile.getAbsolutePath());
        
        assertNotNull(config);
        assertDoesNotThrow(() -> config.validate());
        
        // 验证默认值被正确应用
        assertEquals(30, config.getDatabase().getConnectionTimeout());
        assertEquals("LATEST", config.getDatahub().getStartPosition());
        assertEquals(3, config.getDatahub().getMaxRetries());
        assertEquals("hashmap", config.getFlink().getStateBackendType());
        assertEquals("none", config.getOutput().getCompression());
    }

    /**
     * 测试配置场景 - 完整配置
     * 验证需求: 10.1, 10.5, 10.6, 10.7, 10.8 - 支持配置所有系统参数
     */
    @Test
    void testFullConfig() throws IOException {
        PipelineConfig config = ConfigLoader.loadConfig("test-config-valid.yml");
        
        assertNotNull(config);
        assertDoesNotThrow(() -> config.validate());
        
        // 验证所有配置都被正确加载
        assertEquals("test-db-host", config.getDatabase().getHost());
        assertEquals(3306, config.getDatabase().getPort());
        assertEquals("test_user", config.getDatabase().getUsername());
        assertEquals("test_schema", config.getDatabase().getSchema());
        assertEquals(2, config.getDatabase().getTables().size());
        
        assertEquals("https://test-datahub.aliyuncs.com", config.getDatahub().getEndpoint());
        assertEquals("test_project", config.getDatahub().getProject());
        assertEquals("test_topic", config.getDatahub().getTopic());
        assertEquals("EARLIEST", config.getDatahub().getStartPosition());
        
        assertEquals(2, config.getFlink().getParallelism());
        assertEquals(60000L, config.getFlink().getCheckpointInterval());
        assertEquals("hashmap", config.getFlink().getStateBackendType());
        assertEquals("fixed-delay", config.getFlink().getRestartStrategy());
        
        assertEquals("/tmp/test-output", config.getOutput().getPath());
        assertEquals("json", config.getOutput().getFormat());
        assertEquals("none", config.getOutput().getCompression());
        
        assertTrue(config.getMonitoring().isEnabled());
        assertEquals(30, config.getMonitoring().getMetricsInterval());
        assertEquals(0.1, config.getMonitoring().getCheckpointFailureRateThreshold());
        assertEquals("log", config.getMonitoring().getAlertMethod());
    }
}
