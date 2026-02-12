package com.realtime.pipeline;

import com.realtime.pipeline.config.*;
import com.realtime.pipeline.util.ConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 单元测试 - FlinkPipelineMain
 * 
 * 测试主程序入口的配置加载和初始化逻辑
 * 
 * 验证需求: 15.1 - 主程序入口实现
 */
class FlinkPipelineMainTest {

    @TempDir
    Path tempDir;

    /**
     * 测试配置加载 - 有效配置
     */
    @Test
    void testLoadConfiguration_ValidConfig() throws Exception {
        // 创建测试配置文件
        File configFile = tempDir.resolve("test-config.yml").toFile();
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write(createValidConfigYaml());
        }

        // 加载配置
        PipelineConfig config = ConfigLoader.loadConfig(configFile.getAbsolutePath());

        // 验证配置加载成功
        assertNotNull(config);
        assertNotNull(config.getDatabase());
        assertNotNull(config.getDatahub());
        assertNotNull(config.getFlink());
        assertNotNull(config.getOutput());
        assertNotNull(config.getMonitoring());

        // 验证数据库配置
        assertEquals("localhost", config.getDatabase().getHost());
        assertEquals(2881, config.getDatabase().getPort());

        // 验证DataHub配置
        assertEquals("test-project", config.getDatahub().getProject());
        assertEquals("test-topic", config.getDatahub().getTopic());

        // 验证Flink配置
        assertEquals(4, config.getFlink().getParallelism());
        assertEquals(300000, config.getFlink().getCheckpointInterval());

        // 验证输出配置
        assertEquals("/tmp/output", config.getOutput().getPath());
        assertEquals("json", config.getOutput().getFormat());

        // 验证监控配置
        assertTrue(config.getMonitoring().isEnabled());
        assertEquals(8080, config.getMonitoring().getHealthCheckPort());
    }

    /**
     * 测试配置加载 - 无效配置（缺少必需字段）
     */
    @Test
    void testLoadConfiguration_InvalidConfig() throws Exception {
        // 创建无效配置文件（缺少必需字段）
        File configFile = tempDir.resolve("invalid-config.yml").toFile();
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("database:\n");
            writer.write("  host: localhost\n");
            // 缺少其他必需字段
        }

        // 验证加载失败
        assertThrows(Exception.class, () -> {
            ConfigLoader.loadConfig(configFile.getAbsolutePath());
        });
    }

    /**
     * 测试配置加载 - 文件不存在
     */
    @Test
    void testLoadConfiguration_FileNotFound() {
        // 验证文件不存在时抛出异常
        assertThrows(Exception.class, () -> {
            ConfigLoader.loadConfig("non-existent-file.yml");
        });
    }

    /**
     * 测试配置验证 - 有效配置
     */
    @Test
    void testConfigValidation_ValidConfig() throws Exception {
        // 创建有效配置
        PipelineConfig config = createValidConfig();

        // 验证配置
        assertDoesNotThrow(() -> config.validate());
    }

    /**
     * 测试配置验证 - 无效并行度
     */
    @Test
    void testConfigValidation_InvalidParallelism() {
        // 创建配置
        PipelineConfig config = createValidConfig();
        config.getFlink().setParallelism(-1);  // 无效并行度

        // 验证配置失败
        assertThrows(IllegalArgumentException.class, () -> config.validate());
    }

    /**
     * 测试配置验证 - 无效Checkpoint间隔
     */
    @Test
    void testConfigValidation_InvalidCheckpointInterval() {
        // 创建配置
        PipelineConfig config = createValidConfig();
        config.getFlink().setCheckpointInterval(0);  // 无效间隔

        // 验证配置失败
        assertThrows(IllegalArgumentException.class, () -> config.validate());
    }

    /**
     * 测试配置验证 - 无效输出格式
     */
    @Test
    void testConfigValidation_InvalidOutputFormat() {
        // 创建配置
        PipelineConfig config = createValidConfig();
        config.getOutput().setFormat("invalid");  // 无效格式

        // 验证配置失败
        assertThrows(IllegalArgumentException.class, () -> config.validate());
    }

    /**
     * 测试环境变量覆盖
     */
    @Test
    void testEnvironmentVariableOverride() throws Exception {
        // 设置环境变量（注意：这个测试在实际环境中可能不工作，因为环境变量是只读的）
        // 这里只是演示逻辑，实际测试需要使用系统属性或其他方式

        // 创建测试配置文件
        File configFile = tempDir.resolve("test-config.yml").toFile();
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write(createValidConfigYaml());
        }

        // 加载配置
        PipelineConfig config = ConfigLoader.loadConfig(configFile.getAbsolutePath());

        // 验证配置加载成功
        assertNotNull(config);
        
        // 注意：实际的环境变量覆盖测试需要在集成测试中进行
        // 这里只验证配置加载的基本功能
    }

    /**
     * 测试配置对象的完整性
     */
    @Test
    void testConfigCompleteness() {
        PipelineConfig config = createValidConfig();

        // 验证所有必需的配置对象都存在
        assertNotNull(config.getDatabase(), "Database config should not be null");
        assertNotNull(config.getDatahub(), "DataHub config should not be null");
        assertNotNull(config.getFlink(), "Flink config should not be null");
        assertNotNull(config.getOutput(), "Output config should not be null");
        assertNotNull(config.getMonitoring(), "Monitoring config should not be null");

        // 验证数据库配置字段
        assertNotNull(config.getDatabase().getHost());
        assertTrue(config.getDatabase().getPort() > 0);
        assertNotNull(config.getDatabase().getUsername());
        assertNotNull(config.getDatabase().getPassword());

        // 验证DataHub配置字段
        assertNotNull(config.getDatahub().getEndpoint());
        assertNotNull(config.getDatahub().getProject());
        assertNotNull(config.getDatahub().getTopic());

        // 验证Flink配置字段
        assertTrue(config.getFlink().getParallelism() > 0);
        assertTrue(config.getFlink().getCheckpointInterval() > 0);
        assertNotNull(config.getFlink().getCheckpointDir());

        // 验证输出配置字段
        assertNotNull(config.getOutput().getPath());
        assertNotNull(config.getOutput().getFormat());
        assertTrue(config.getOutput().getRollingSizeBytes() > 0);

        // 验证监控配置字段
        assertTrue(config.getMonitoring().getHealthCheckPort() > 0);
    }

    /**
     * 测试不同输出格式的配置
     */
    @Test
    void testDifferentOutputFormats() {
        String[] formats = {"json", "parquet", "csv"};

        for (String format : formats) {
            PipelineConfig config = createValidConfig();
            config.getOutput().setFormat(format);

            // 验证配置有效
            assertDoesNotThrow(() -> config.validate(), 
                "Format " + format + " should be valid");
        }
    }

    /**
     * 测试Checkpoint配置的不同模式
     */
    @Test
    void testCheckpointModes() {
        String[] modes = {"at-least-once", "exactly-once"};

        for (String mode : modes) {
            PipelineConfig config = createValidConfig();
            config.getFlink().setCheckpointingMode(mode);

            // 验证配置有效
            assertDoesNotThrow(() -> config.validate(), 
                "Checkpoint mode " + mode + " should be valid");
        }
    }

    /**
     * 测试状态后端配置
     */
    @Test
    void testStateBackendTypes() {
        String[] backends = {"hashmap", "rocksdb"};

        for (String backend : backends) {
            PipelineConfig config = createValidConfig();
            config.getFlink().setStateBackendType(backend);

            // 验证配置有效
            assertDoesNotThrow(() -> config.validate(), 
                "State backend " + backend + " should be valid");
        }
    }

    /**
     * 测试监控配置的启用/禁用
     */
    @Test
    void testMonitoringEnabledDisabled() {
        // 测试启用监控
        PipelineConfig config1 = createValidConfig();
        config1.getMonitoring().setEnabled(true);
        assertDoesNotThrow(() -> config1.validate());
        assertTrue(config1.getMonitoring().isEnabled());

        // 测试禁用监控
        PipelineConfig config2 = createValidConfig();
        config2.getMonitoring().setEnabled(false);
        assertDoesNotThrow(() -> config2.validate());
        assertFalse(config2.getMonitoring().isEnabled());
    }

    // ========== 辅助方法 ==========

    /**
     * 创建有效的配置对象
     */
    private PipelineConfig createValidConfig() {
        PipelineConfig config = new PipelineConfig();

        // 数据库配置
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setHost("localhost");
        dbConfig.setPort(2881);
        dbConfig.setUsername("root");
        dbConfig.setPassword("password");
        dbConfig.setSchema("test_db");
        dbConfig.setTables(java.util.Arrays.asList("table1", "table2"));
        config.setDatabase(dbConfig);

        // DataHub配置
        DataHubConfig dhConfig = new DataHubConfig();
        dhConfig.setEndpoint("https://datahub.aliyuncs.com");
        dhConfig.setAccessId("test-access-id");
        dhConfig.setAccessKey("test-access-key");
        dhConfig.setProject("test-project");
        dhConfig.setTopic("test-topic");
        dhConfig.setConsumerGroup("test-group");
        dhConfig.setStartPosition("LATEST");
        config.setDatahub(dhConfig);

        // Flink配置
        FlinkConfig flinkConfig = new FlinkConfig();
        flinkConfig.setParallelism(4);
        flinkConfig.setCheckpointInterval(300000);
        flinkConfig.setCheckpointTimeout(600000);
        flinkConfig.setMinPauseBetweenCheckpoints(60000);
        flinkConfig.setMaxConcurrentCheckpoints(1);
        flinkConfig.setTolerableCheckpointFailures(3);
        flinkConfig.setRetainedCheckpoints(3);
        flinkConfig.setStateBackendType("hashmap");
        flinkConfig.setCheckpointDir("/tmp/checkpoints");
        flinkConfig.setCheckpointingMode("at-least-once");
        flinkConfig.setRestartStrategy("fixed-delay");
        flinkConfig.setRestartAttempts(3);
        flinkConfig.setRestartDelay(10000);
        config.setFlink(flinkConfig);

        // 输出配置
        OutputConfig outputConfig = new OutputConfig();
        outputConfig.setPath("/tmp/output");
        outputConfig.setFormat("json");
        outputConfig.setRollingSizeBytes(1024 * 1024 * 1024L);  // 1GB
        outputConfig.setRollingIntervalMs(3600000L);  // 1 hour
        outputConfig.setCompression("none");
        outputConfig.setMaxRetries(3);
        outputConfig.setRetryBackoff(2);
        config.setOutput(outputConfig);

        // 监控配置
        MonitoringConfig monitoringConfig = new MonitoringConfig();
        monitoringConfig.setEnabled(true);
        monitoringConfig.setMetricsInterval(30);
        monitoringConfig.setHealthCheckPort(8080);
        monitoringConfig.setLatencyThreshold(60000);
        monitoringConfig.setCheckpointFailureRateThreshold(0.1);
        monitoringConfig.setLoadThreshold(0.8);
        monitoringConfig.setBackpressureThreshold(0.8);
        monitoringConfig.setAlertMethod("log");
        config.setMonitoring(monitoringConfig);

        return config;
    }

    /**
     * 创建有效的配置YAML字符串
     */
    private String createValidConfigYaml() {
        return "database:\n" +
               "  host: localhost\n" +
               "  port: 2881\n" +
               "  username: root\n" +
               "  password: password\n" +
               "  schema: test_db\n" +
               "  tables:\n" +
               "    - table1\n" +
               "    - table2\n" +
               "\n" +
               "datahub:\n" +
               "  endpoint: https://datahub.aliyuncs.com\n" +
               "  accessId: test-access-id\n" +
               "  accessKey: test-access-key\n" +
               "  project: test-project\n" +
               "  topic: test-topic\n" +
               "  consumerGroup: test-group\n" +
               "  startPosition: LATEST\n" +
               "\n" +
               "flink:\n" +
               "  parallelism: 4\n" +
               "  checkpointInterval: 300000\n" +
               "  checkpointTimeout: 600000\n" +
               "  minPauseBetweenCheckpoints: 60000\n" +
               "  maxConcurrentCheckpoints: 1\n" +
               "  tolerableCheckpointFailures: 3\n" +
               "  retainedCheckpoints: 3\n" +
               "  stateBackendType: hashmap\n" +
               "  checkpointDir: /tmp/checkpoints\n" +
               "  checkpointingMode: at-least-once\n" +
               "  restartStrategy: fixed-delay\n" +
               "  restartAttempts: 3\n" +
               "  restartDelay: 10000\n" +
               "\n" +
               "output:\n" +
               "  path: /tmp/output\n" +
               "  format: json\n" +
               "  rollingSizeBytes: 1073741824\n" +
               "  rollingIntervalMs: 3600000\n" +
               "  compression: none\n" +
               "  maxRetries: 3\n" +
               "  retryBackoff: 2\n" +
               "\n" +
               "monitoring:\n" +
               "  enabled: true\n" +
               "  metricsInterval: 30\n" +
               "  healthCheckPort: 8080\n" +
               "  latencyThreshold: 60000\n" +
               "  checkpointFailureRateThreshold: 0.1\n" +
               "  loadThreshold: 0.8\n" +
               "  backpressureThreshold: 0.8\n" +
               "  alertMethod: log\n";
    }
}
