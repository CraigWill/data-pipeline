package com.realtime.pipeline.util;

import com.realtime.pipeline.config.PipelineConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置加载器集成测试
 * 测试环境变量覆盖功能
 * 
 * 这些测试需要设置环境变量才能运行。
 * 例如：
 * export PIPELINE_DATABASE_HOST=integration-test-host
 * export PIPELINE_FLINK_PARALLELISM=16
 * mvn test -Dtest=ConfigLoaderIntegrationTest
 */
class ConfigLoaderIntegrationTest {

    /**
     * 测试环境变量覆盖 - 数据库主机
     * 验证需求: 10.2 - 支持通过环境变量覆盖配置文件参数
     * 
     * 运行前设置: export PIPELINE_DATABASE_HOST=integration-test-host
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "PIPELINE_DATABASE_HOST", matches = ".*")
    void testEnvironmentVariableOverrideDatabaseHost() throws IOException {
        String expectedHost = System.getenv("PIPELINE_DATABASE_HOST");
        assertNotNull(expectedHost, "PIPELINE_DATABASE_HOST environment variable should be set");
        
        PipelineConfig config = ConfigLoader.loadConfig();
        
        assertEquals(expectedHost, config.getDatabase().getHost(),
                "Database host should be overridden by environment variable");
    }

    /**
     * 测试环境变量覆盖 - Flink并行度
     * 验证需求: 10.2 - 支持通过环境变量覆盖配置文件参数
     * 
     * 运行前设置: export PIPELINE_FLINK_PARALLELISM=16
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "PIPELINE_FLINK_PARALLELISM", matches = ".*")
    void testEnvironmentVariableOverrideFlinkParallelism() throws IOException {
        String parallelismStr = System.getenv("PIPELINE_FLINK_PARALLELISM");
        assertNotNull(parallelismStr, "PIPELINE_FLINK_PARALLELISM environment variable should be set");
        int expectedParallelism = Integer.parseInt(parallelismStr);
        
        PipelineConfig config = ConfigLoader.loadConfig();
        
        assertEquals(expectedParallelism, config.getFlink().getParallelism(),
                "Flink parallelism should be overridden by environment variable");
    }

    /**
     * 测试环境变量覆盖 - 输出格式
     * 验证需求: 10.2 - 支持通过环境变量覆盖配置文件参数
     * 
     * 运行前设置: export PIPELINE_OUTPUT_FORMAT=parquet
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "PIPELINE_OUTPUT_FORMAT", matches = ".*")
    void testEnvironmentVariableOverrideOutputFormat() throws IOException {
        String expectedFormat = System.getenv("PIPELINE_OUTPUT_FORMAT");
        assertNotNull(expectedFormat, "PIPELINE_OUTPUT_FORMAT environment variable should be set");
        
        PipelineConfig config = ConfigLoader.loadConfig();
        
        assertEquals(expectedFormat, config.getOutput().getFormat(),
                "Output format should be overridden by environment variable");
    }

    /**
     * 测试环境变量覆盖优先级
     * 验证需求: 10.2 - 环境变量应该覆盖配置文件参数
     * 
     * 运行前设置: export PIPELINE_DATABASE_PORT=5432
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "PIPELINE_DATABASE_PORT", matches = ".*")
    void testEnvironmentVariableOverridePriority() throws IOException {
        String portStr = System.getenv("PIPELINE_DATABASE_PORT");
        assertNotNull(portStr, "PIPELINE_DATABASE_PORT environment variable should be set");
        int expectedPort = Integer.parseInt(portStr);
        
        PipelineConfig config = ConfigLoader.loadConfig();
        
        // 环境变量的值应该覆盖配置文件的值（配置文件中是2881）
        assertEquals(expectedPort, config.getDatabase().getPort(),
                "Database port should be overridden by environment variable");
        assertNotEquals(2881, config.getDatabase().getPort(),
                "Database port should not be the default value from config file");
    }

    /**
     * 测试环境变量覆盖 - 布尔类型
     * 验证需求: 10.2 - 支持通过环境变量覆盖配置文件参数
     * 
     * 运行前设置: export PIPELINE_MONITORING_ENABLED=false
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "PIPELINE_MONITORING_ENABLED", matches = "false")
    void testEnvironmentVariableOverrideBoolean() throws IOException {
        PipelineConfig config = ConfigLoader.loadConfig();
        
        assertFalse(config.getMonitoring().isEnabled(),
                "Monitoring enabled should be overridden to false by environment variable");
    }

    /**
     * 测试环境变量覆盖 - 长整型
     * 验证需求: 10.2 - 支持通过环境变量覆盖配置文件参数
     * 
     * 运行前设置: export PIPELINE_FLINK_CHECKPOINTINTERVAL=120000
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "PIPELINE_FLINK_CHECKPOINTINTERVAL", matches = ".*")
    void testEnvironmentVariableOverrideLong() throws IOException {
        String intervalStr = System.getenv("PIPELINE_FLINK_CHECKPOINTINTERVAL");
        assertNotNull(intervalStr, "PIPELINE_FLINK_CHECKPOINTINTERVAL environment variable should be set");
        long expectedInterval = Long.parseLong(intervalStr);
        
        PipelineConfig config = ConfigLoader.loadConfig();
        
        assertEquals(expectedInterval, config.getFlink().getCheckpointInterval(),
                "Checkpoint interval should be overridden by environment variable");
    }

    /**
     * 测试环境变量覆盖 - 双精度浮点型
     * 验证需求: 10.2 - 支持通过环境变量覆盖配置文件参数
     * 
     * 运行前设置: export PIPELINE_MONITORING_LOADTHRESHOLD=0.9
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "PIPELINE_MONITORING_LOADTHRESHOLD", matches = ".*")
    void testEnvironmentVariableOverrideDouble() throws IOException {
        String thresholdStr = System.getenv("PIPELINE_MONITORING_LOADTHRESHOLD");
        assertNotNull(thresholdStr, "PIPELINE_MONITORING_LOADTHRESHOLD environment variable should be set");
        double expectedThreshold = Double.parseDouble(thresholdStr);
        
        PipelineConfig config = ConfigLoader.loadConfig();
        
        assertEquals(expectedThreshold, config.getMonitoring().getLoadThreshold(), 0.001,
                "Load threshold should be overridden by environment variable");
    }

    /**
     * 测试环境变量覆盖 - 列表类型
     * 验证需求: 10.2 - 支持通过环境变量覆盖配置文件参数
     * 
     * 运行前设置: export PIPELINE_DATABASE_TABLES=table1,table2,table3
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "PIPELINE_DATABASE_TABLES", matches = ".*")
    void testEnvironmentVariableOverrideList() throws IOException {
        String tablesStr = System.getenv("PIPELINE_DATABASE_TABLES");
        assertNotNull(tablesStr, "PIPELINE_DATABASE_TABLES environment variable should be set");
        String[] expectedTables = tablesStr.split(",");
        
        PipelineConfig config = ConfigLoader.loadConfig();
        
        assertEquals(expectedTables.length, config.getDatabase().getTables().size(),
                "Number of tables should match environment variable");
        
        for (String table : expectedTables) {
            assertTrue(config.getDatabase().getTables().contains(table.trim()),
                    "Tables list should contain " + table.trim());
        }
    }
}
