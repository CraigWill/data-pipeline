package com.realtime.pipeline.util;

import com.realtime.pipeline.config.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * 配置加载器的基于属性的测试
 * Feature: realtime-data-pipeline
 * 
 * 使用jqwik进行基于属性的测试，验证配置加载和验证的通用属性
 */
class ConfigLoaderPropertyTest {

    /**
     * Property 40: 配置文件参数加载
     * **Validates: Requirements 10.1**
     * 
     * 对于任何在配置文件中设置的系统参数，系统应该正确加载并应用该参数
     */
    @Property(tries = 20)
    void property40_configFileParameterLoading(
            @ForAll("validDatabaseConfigs") DatabaseConfig dbConfig,
            @ForAll("validDataHubConfigs") DataHubConfig dhConfig,
            @ForAll("validFlinkConfigs") FlinkConfig flinkConfig,
            @ForAll("validOutputConfigs") OutputConfig outputConfig) throws IOException {
        
        // 创建临时目录
        Path tempDir = Files.createTempDirectory("config-test-");
        
        try {
            // 创建完整的配置
            PipelineConfig config = PipelineConfig.builder()
                    .database(dbConfig)
                    .datahub(dhConfig)
                    .flink(flinkConfig)
                    .output(outputConfig)
                    .monitoring(MonitoringConfig.builder().build())
                    .build();

            // 将配置写入临时文件
            String yamlContent = generateYamlConfig(config);
            File configFile = tempDir.resolve("test-config-" + System.nanoTime() + ".yml").toFile();
            Files.writeString(configFile.toPath(), yamlContent);

            // 加载配置
            PipelineConfig loadedConfig = ConfigLoader.loadConfig(configFile.getAbsolutePath());

            // 验证所有参数都被正确加载
            assertThat(loadedConfig).isNotNull();
            assertThat(loadedConfig.getDatabase().getHost()).isEqualTo(dbConfig.getHost());
            assertThat(loadedConfig.getDatabase().getPort()).isEqualTo(dbConfig.getPort());
            assertThat(loadedConfig.getDatabase().getUsername()).isEqualTo(dbConfig.getUsername());
            assertThat(loadedConfig.getDatabase().getSchema()).isEqualTo(dbConfig.getSchema());
            assertThat(loadedConfig.getDatabase().getTables()).containsExactlyElementsOf(dbConfig.getTables());
            
            assertThat(loadedConfig.getDatahub().getEndpoint()).isEqualTo(dhConfig.getEndpoint());
            assertThat(loadedConfig.getDatahub().getProject()).isEqualTo(dhConfig.getProject());
            assertThat(loadedConfig.getDatahub().getTopic()).isEqualTo(dhConfig.getTopic());
            
            assertThat(loadedConfig.getFlink().getParallelism()).isEqualTo(flinkConfig.getParallelism());
            assertThat(loadedConfig.getFlink().getCheckpointInterval()).isEqualTo(flinkConfig.getCheckpointInterval());
            assertThat(loadedConfig.getFlink().getStateBackendType()).isEqualTo(flinkConfig.getStateBackendType());
            
            assertThat(loadedConfig.getOutput().getPath()).isEqualTo(outputConfig.getPath());
            assertThat(loadedConfig.getOutput().getFormat()).isEqualTo(outputConfig.getFormat());
        } finally {
            // 清理临时目录
            deleteDirectory(tempDir.toFile());
        }
    }

    /**
     * Property 41: 环境变量覆盖优先级
     * **Validates: Requirements 10.2**
     * 
     * 对于任何同时在配置文件和环境变量中设置的参数，环境变量的值应该覆盖配置文件的值
     * 
     * 注意：由于Java运行时环境变量的限制，此测试通过反射模拟环境变量覆盖行为
     */
    @Property(tries = 20)
    void property41_environmentVariableOverridePriority(
            @ForAll("validDatabaseConfigs") DatabaseConfig originalDbConfig,
            @ForAll @StringLength(min = 5, max = 50) @AlphaChars String overrideHost,
            @ForAll @IntRange(min = 1024, max = 65535) int overridePort) throws Exception {
        
        // 创建临时目录
        Path tempDir = Files.createTempDirectory("config-test-");
        
        try {
            // 创建原始配置
            PipelineConfig config = createMinimalValidConfig(originalDbConfig);
            
            // 将配置写入临时文件
            String yamlContent = generateYamlConfig(config);
            File configFile = tempDir.resolve("test-env-override-" + System.nanoTime() + ".yml").toFile();
            Files.writeString(configFile.toPath(), yamlContent);

            // 模拟环境变量覆盖（通过直接调用applyEnvironmentOverrides方法）
            PipelineConfig loadedConfig = ConfigLoader.loadConfig(configFile.getAbsolutePath());
            
            // 手动应用环境变量覆盖来测试优先级
            Map<String, String> mockEnv = new HashMap<>();
            mockEnv.put("PIPELINE_DATABASE_HOST", overrideHost);
            mockEnv.put("PIPELINE_DATABASE_PORT", String.valueOf(overridePort));
            
            // 使用反射调用私有方法进行覆盖
            java.lang.reflect.Method method = ConfigLoader.class.getDeclaredMethod(
                    "overrideFromEnv", Object.class, String.class, Map.class);
            method.setAccessible(true);
            method.invoke(null, loadedConfig.getDatabase(), "PIPELINE_DATABASE_", mockEnv);

            // 验证环境变量覆盖了配置文件的值
            assertThat(loadedConfig.getDatabase().getHost())
                    .as("Environment variable should override config file value")
                    .isEqualTo(overrideHost);
            assertThat(loadedConfig.getDatabase().getPort())
                    .as("Environment variable should override config file value")
                    .isEqualTo(overridePort);
        } finally {
            // 清理临时目录
            deleteDirectory(tempDir.toFile());
        }
    }

    /**
     * Property 42: 配置参数验证
     * **Validates: Requirements 10.3**
     * 
     * 对于任何配置参数，系统应该验证其有效性（类型、范围、格式等）
     */
    @Property(tries = 20)
    void property42_configParameterValidation(
            @ForAll("validDatabaseConfigs") DatabaseConfig dbConfig,
            @ForAll("validDataHubConfigs") DataHubConfig dhConfig,
            @ForAll("validFlinkConfigs") FlinkConfig flinkConfig,
            @ForAll("validOutputConfigs") OutputConfig outputConfig) {
        
        // 创建有效配置
        PipelineConfig config = PipelineConfig.builder()
                .database(dbConfig)
                .datahub(dhConfig)
                .flink(flinkConfig)
                .output(outputConfig)
                .monitoring(MonitoringConfig.builder().build())
                .build();

        // 验证配置应该成功（不抛出异常）
        assertThatCode(() -> config.validate())
                .as("Valid configuration should pass validation")
                .doesNotThrowAnyException();
        
        // 验证各个子配置也应该成功
        assertThatCode(() -> dbConfig.validate()).doesNotThrowAnyException();
        assertThatCode(() -> dhConfig.validate()).doesNotThrowAnyException();
        assertThatCode(() -> flinkConfig.validate()).doesNotThrowAnyException();
        assertThatCode(() -> outputConfig.validate()).doesNotThrowAnyException();
    }

    /**
     * Property 43: 无效配置拒绝
     * **Validates: Requirements 10.4**
     * 
     * 对于任何无效的配置参数，系统应该拒绝启动并返回明确的错误信息
     */
    @Property(tries = 20)
    void property43_invalidConfigRejection_invalidPort(
            @ForAll("validDatabaseConfigs") DatabaseConfig dbConfig,
            @ForAll @IntRange(min = -1000, max = 0) int invalidPort) throws IOException {
        
        // 创建临时目录
        Path tempDir = Files.createTempDirectory("config-test-");
        
        try {
            // 创建包含无效端口的配置
            dbConfig.setPort(invalidPort);
            PipelineConfig config = createMinimalValidConfig(dbConfig);
            
            String yamlContent = generateYamlConfig(config);
            File configFile = tempDir.resolve("test-invalid-port-" + System.nanoTime() + ".yml").toFile();
            Files.writeString(configFile.toPath(), yamlContent);

            // 验证加载配置时应该抛出异常
            assertThatThrownBy(() -> ConfigLoader.loadConfig(configFile.getAbsolutePath()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("port must be between 1 and 65535");
        } finally {
            // 清理临时目录
            deleteDirectory(tempDir.toFile());
        }
    }

    @Property(tries = 20)
    void property43_invalidConfigRejection_emptyHost(
            @ForAll("validDatabaseConfigs") DatabaseConfig dbConfig) throws IOException {
        
        // 创建临时目录
        Path tempDir = Files.createTempDirectory("config-test-");
        
        try {
            // 创建包含空主机名的配置
            dbConfig.setHost("");
            PipelineConfig config = createMinimalValidConfig(dbConfig);
            
            String yamlContent = generateYamlConfig(config);
            File configFile = tempDir.resolve("test-empty-host-" + System.nanoTime() + ".yml").toFile();
            Files.writeString(configFile.toPath(), yamlContent);

            // 验证加载配置时应该抛出异常
            assertThatThrownBy(() -> ConfigLoader.loadConfig(configFile.getAbsolutePath()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("host is required");
        } finally {
            // 清理临时目录
            deleteDirectory(tempDir.toFile());
        }
    }

    @Property(tries = 20)
    void property43_invalidConfigRejection_invalidParallelism(
            @ForAll("validFlinkConfigs") FlinkConfig flinkConfig,
            @ForAll @IntRange(min = -100, max = 0) int invalidParallelism) throws IOException {
        
        // 创建临时目录
        Path tempDir = Files.createTempDirectory("config-test-");
        
        try {
            // 创建包含无效并行度的配置
            flinkConfig.setParallelism(invalidParallelism);
            PipelineConfig config = createMinimalValidConfig(
                    DatabaseConfig.builder()
                            .host("localhost")
                            .port(3306)
                            .username("user")
                            .password("pass")
                            .schema("test")
                            .tables(List.of("table1"))
                            .build()
            );
            config.setFlink(flinkConfig);
            
            String yamlContent = generateYamlConfig(config);
            File configFile = tempDir.resolve("test-invalid-parallelism-" + System.nanoTime() + ".yml").toFile();
            Files.writeString(configFile.toPath(), yamlContent);

            // 验证加载配置时应该抛出异常
            assertThatThrownBy(() -> ConfigLoader.loadConfig(configFile.getAbsolutePath()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("parallelism must be positive");
        } finally {
            // 清理临时目录
            deleteDirectory(tempDir.toFile());
        }
    }

    @Property(tries = 20)
    void property43_invalidConfigRejection_invalidFormat(
            @ForAll("validOutputConfigs") OutputConfig outputConfig,
            @ForAll @StringLength(min = 3, max = 10) @AlphaChars String invalidFormat) throws IOException {
        
        // 确保格式是无效的
        Assume.that(!invalidFormat.matches("json|parquet|csv"));
        
        // 创建临时目录
        Path tempDir = Files.createTempDirectory("config-test-");
        
        try {
            // 创建包含无效格式的配置
            outputConfig.setFormat(invalidFormat);
            PipelineConfig config = createMinimalValidConfig(
                    DatabaseConfig.builder()
                            .host("localhost")
                            .port(3306)
                            .username("user")
                            .password("pass")
                            .schema("test")
                            .tables(List.of("table1"))
                            .build()
            );
            config.setOutput(outputConfig);
            
            String yamlContent = generateYamlConfig(config);
            File configFile = tempDir.resolve("test-invalid-format-" + System.nanoTime() + ".yml").toFile();
            Files.writeString(configFile.toPath(), yamlContent);

            // 验证加载配置时应该抛出异常
            assertThatThrownBy(() -> ConfigLoader.loadConfig(configFile.getAbsolutePath()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("format must be");
        } finally {
            // 清理临时目录
            deleteDirectory(tempDir.toFile());
        }
    }

    @Property(tries = 20)
    void property43_invalidConfigRejection_emptyTables(
            @ForAll("validDatabaseConfigs") DatabaseConfig dbConfig) throws IOException {
        
        // 创建临时目录
        Path tempDir = Files.createTempDirectory("config-test-");
        
        try {
            // 创建包含空表列表的配置
            dbConfig.setTables(List.of());
            PipelineConfig config = createMinimalValidConfig(dbConfig);
            
            String yamlContent = generateYamlConfig(config);
            File configFile = tempDir.resolve("test-empty-tables-" + System.nanoTime() + ".yml").toFile();
            Files.writeString(configFile.toPath(), yamlContent);

            // 验证加载配置时应该抛出异常
            assertThatThrownBy(() -> ConfigLoader.loadConfig(configFile.getAbsolutePath()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("At least one table must be specified");
        } finally {
            // 清理临时目录
            deleteDirectory(tempDir.toFile());
        }
    }

    // ==================== 数据生成器 ====================

    @Provide
    Arbitrary<DatabaseConfig> validDatabaseConfigs() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(50),  // host
                Arbitraries.integers().between(1024, 65535),  // port
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(30),  // username
                Arbitraries.strings().ofMinLength(6).ofMaxLength(30),  // password
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(30),  // schema
                Arbitraries.of("table1", "table2", "table3").list().ofMinSize(1).ofMaxSize(5)  // tables
        ).as((host, port, username, password, schema, tables) ->
                DatabaseConfig.builder()
                        .host(host)
                        .port(port)
                        .username(username)
                        .password(password)
                        .schema(schema)
                        .tables(tables)
                        .connectionTimeout(30)
                        .reconnectInterval(30)
                        .build()
        );
    }

    @Provide
    Arbitrary<DataHubConfig> validDataHubConfigs() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(50).map(s -> "https://" + s + ".com"),  // endpoint
                Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(30),  // accessId
                Arbitraries.strings().alpha().numeric().ofMinLength(10).ofMaxLength(30),  // accessKey - 确保不为空
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(30),  // project
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(30),  // topic
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(30),  // consumerGroup
                Arbitraries.of("EARLIEST", "LATEST", "TIMESTAMP")  // startPosition
        ).as((endpoint, accessId, accessKey, project, topic, consumerGroup, startPosition) ->
                DataHubConfig.builder()
                        .endpoint(endpoint)
                        .accessId(accessId)
                        .accessKey(accessKey)
                        .project(project)
                        .topic(topic)
                        .consumerGroup(consumerGroup)
                        .startPosition(startPosition)
                        .maxRetries(3)
                        .retryBackoff(2)
                        .build()
        );
    }

    @Provide
    Arbitrary<FlinkConfig> validFlinkConfigs() {
        return Combinators.combine(
                Arbitraries.integers().between(1, 100),  // parallelism
                Arbitraries.longs().between(60000L, 600000L),  // checkpointInterval
                Arbitraries.longs().between(120000L, 1200000L),  // checkpointTimeout
                Arbitraries.of("hashmap", "rocksdb"),  // stateBackendType
                Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(30).map(s -> "/tmp/checkpoint-" + s)  // checkpointDir
        ).as((parallelism, checkpointInterval, checkpointTimeout, stateBackendType, checkpointDir) ->
                FlinkConfig.builder()
                        .parallelism(parallelism)
                        .checkpointInterval(checkpointInterval)
                        .checkpointTimeout(checkpointTimeout)
                        .minPauseBetweenCheckpoints(60000L)
                        .maxConcurrentCheckpoints(1)
                        .retainedCheckpoints(3)
                        .stateBackendType(stateBackendType)
                        .checkpointDir(checkpointDir)
                        .tolerableCheckpointFailures(3)
                        .restartStrategy("fixed-delay")
                        .restartAttempts(3)
                        .restartDelay(10000L)
                        .build()
        );
    }

    @Provide
    Arbitrary<OutputConfig> validOutputConfigs() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(30).map(s -> "/tmp/output-" + s),  // path
                Arbitraries.of("json", "parquet", "csv"),  // format
                Arbitraries.of("none", "gzip", "snappy")  // compression
        ).as((path, format, compression) ->
                OutputConfig.builder()
                        .path(path)
                        .format(format)
                        .rollingSizeBytes(1024L * 1024L * 1024L)
                        .rollingIntervalMs(3600000L)
                        .compression(compression)
                        .maxRetries(3)
                        .retryBackoff(2)
                        .build()
        );
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建最小有效配置
     */
    private PipelineConfig createMinimalValidConfig(DatabaseConfig dbConfig) {
        return PipelineConfig.builder()
                .database(dbConfig)
                .datahub(DataHubConfig.builder()
                        .endpoint("https://test.com")
                        .accessId("test_id")
                        .accessKey("test_key")
                        .project("test_project")
                        .topic("test_topic")
                        .consumerGroup("test_group")
                        .build())
                .flink(FlinkConfig.builder()
                        .parallelism(1)
                        .checkpointInterval(60000L)
                        .checkpointTimeout(120000L)
                        .checkpointDir("/tmp/checkpoint")
                        .build())
                .output(OutputConfig.builder()
                        .path("/tmp/output")
                        .format("json")
                        .build())
                .monitoring(MonitoringConfig.builder().build())
                .build();
    }

    /**
     * 生成YAML配置内容
     * 使用Jackson YAML mapper来确保生成有效的YAML
     */
    private String generateYamlConfig(PipelineConfig config) throws IOException {
        com.fasterxml.jackson.databind.ObjectMapper yamlMapper = 
                new com.fasterxml.jackson.databind.ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
        return yamlMapper.writeValueAsString(config);
    }

    /**
     * 递归删除目录
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
}
