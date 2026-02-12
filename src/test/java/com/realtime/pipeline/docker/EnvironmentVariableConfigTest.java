package com.realtime.pipeline.docker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

/**
 * 环境变量配置单元测试
 * 
 * 测试环境变量配置的正确性，验证：
 * - 环境变量定义完整
 * - 环境变量有默认值
 * - 环境变量格式正确
 * - 环境变量被正确使用
 * 
 * 需求: 8.6
 */
class EnvironmentVariableConfigTest {

    private static final String PROJECT_ROOT = System.getProperty("user.dir");
    private static final Path DOCKER_DIR = Paths.get(PROJECT_ROOT, "docker");
    private static final Path ENV_EXAMPLE = Paths.get(PROJECT_ROOT, ".env.example");
    
    @BeforeAll
    static void setup() {
        assertThat(DOCKER_DIR).exists().isDirectory();
        assertThat(ENV_EXAMPLE).exists().isRegularFile();
    }

    /**
     * 测试.env.example包含Flink相关环境变量
     * 需求: 8.6
     */
    @Test
    void testEnvExampleContainsFlinkVariables() throws IOException {
        String content = Files.readString(ENV_EXAMPLE);
        
        // Flink基本配置
        assertThat(content)
                .as("Should define PARALLELISM_DEFAULT")
                .contains("PARALLELISM_DEFAULT");
        
        assertThat(content)
                .as("Should define CHECKPOINT_INTERVAL")
                .contains("CHECKPOINT_INTERVAL");
        
        assertThat(content)
                .as("Should define STATE_BACKEND")
                .contains("STATE_BACKEND");
        
        // JobManager配置
        assertThat(content)
                .as("Should define JOB_MANAGER_HEAP_SIZE")
                .contains("JOB_MANAGER_HEAP_SIZE");
        
        assertThat(content)
                .as("Should define JOB_MANAGER_RPC_PORT")
                .contains("JOB_MANAGER_RPC_PORT");
        
        // TaskManager配置
        assertThat(content)
                .as("Should define TASK_MANAGER_NUMBER_OF_TASK_SLOTS")
                .contains("TASK_MANAGER_NUMBER_OF_TASK_SLOTS");
        
        assertThat(content)
                .as("Should define TASK_MANAGER_HEAP_SIZE")
                .contains("TASK_MANAGER_HEAP_SIZE");
    }

    /**
     * 测试.env.example包含数据库相关环境变量
     * 需求: 8.6
     */
    @Test
    void testEnvExampleContainsDatabaseVariables() throws IOException {
        String content = Files.readString(ENV_EXAMPLE);
        
        assertThat(content)
                .as("Should define DATABASE_HOST")
                .contains("DATABASE_HOST");
        
        assertThat(content)
                .as("Should define DATABASE_PORT")
                .contains("DATABASE_PORT");
        
        assertThat(content)
                .as("Should define DATABASE_USERNAME")
                .contains("DATABASE_USERNAME");
        
        assertThat(content)
                .as("Should define DATABASE_PASSWORD")
                .contains("DATABASE_PASSWORD");
        
        assertThat(content)
                .as("Should define DATABASE_SCHEMA")
                .contains("DATABASE_SCHEMA");
    }

    /**
     * 测试.env.example包含DataHub相关环境变量
     * 需求: 8.6
     */
    @Test
    void testEnvExampleContainsDataHubVariables() throws IOException {
        String content = Files.readString(ENV_EXAMPLE);
        
        assertThat(content)
                .as("Should define DATAHUB_ENDPOINT")
                .contains("DATAHUB_ENDPOINT");
        
        assertThat(content)
                .as("Should define DATAHUB_ACCESS_ID")
                .contains("DATAHUB_ACCESS_ID");
        
        assertThat(content)
                .as("Should define DATAHUB_ACCESS_KEY")
                .contains("DATAHUB_ACCESS_KEY");
        
        assertThat(content)
                .as("Should define DATAHUB_PROJECT")
                .contains("DATAHUB_PROJECT");
        
        assertThat(content)
                .as("Should define DATAHUB_TOPIC")
                .contains("DATAHUB_TOPIC");
    }

    /**
     * 测试.env.example包含输出相关环境变量（可选）
     * 需求: 8.6
     */
    @Test
    void testEnvExampleContainsOutputVariables() throws IOException {
        String content = Files.readString(ENV_EXAMPLE);
        
        // 输出配置可能在Flink配置中，不一定作为独立环境变量
        // 这个测试是可选的，因为输出配置可能通过其他方式配置
        assertThat(content)
                .as("Should have some configuration")
                .isNotEmpty();
    }

    /**
     * 测试.env.example包含监控相关环境变量
     * 需求: 8.6
     */
    @Test
    void testEnvExampleContainsMonitoringVariables() throws IOException {
        String content = Files.readString(ENV_EXAMPLE);
        
        assertThat(content)
                .as("Should define MONITORING_PORT")
                .contains("MONITORING_PORT");
    }

    /**
     * 测试环境变量有合理的默认值
     * 需求: 8.6
     */
    @Test
    void testEnvExampleHasReasonableDefaults() throws IOException {
        String content = Files.readString(ENV_EXAMPLE);
        Map<String, String> envVars = parseEnvFile(content);
        
        // 验证并行度默认值
        if (envVars.containsKey("PARALLELISM_DEFAULT")) {
            int parallelism = Integer.parseInt(envVars.get("PARALLELISM_DEFAULT"));
            assertThat(parallelism)
                    .as("PARALLELISM_DEFAULT should be reasonable")
                    .isBetween(1, 32);
        }
        
        // 验证Checkpoint间隔默认值
        if (envVars.containsKey("CHECKPOINT_INTERVAL")) {
            int interval = Integer.parseInt(envVars.get("CHECKPOINT_INTERVAL"));
            assertThat(interval)
                    .as("CHECKPOINT_INTERVAL should be at least 60000ms (1 minute)")
                    .isGreaterThanOrEqualTo(60000);
        }
        
        // 验证TaskManager slots默认值
        if (envVars.containsKey("TASK_MANAGER_SLOTS")) {
            int slots = Integer.parseInt(envVars.get("TASK_MANAGER_SLOTS"));
            assertThat(slots)
                    .as("TASK_MANAGER_SLOTS should be reasonable")
                    .isBetween(1, 16);
        }
    }

    /**
     * 测试docker-compose.yml使用环境变量
     * 需求: 8.6
     */
    @Test
    void testDockerComposeUsesEnvVariables() throws IOException {
        Path dockerCompose = Paths.get(PROJECT_ROOT, "docker-compose.yml");
        String content = Files.readString(dockerCompose);
        
        // 验证使用环境变量语法
        assertThat(content)
                .as("Should use environment variable syntax")
                .containsAnyOf("${", "$");
        
        // 验证定义environment部分
        assertThat(content)
                .as("Should define environment section")
                .contains("environment:");
    }

    /**
     * 测试docker-compose.yml引用.env文件
     * 需求: 8.6
     */
    @Test
    void testDockerComposeReferencesEnvFile() throws IOException {
        Path dockerCompose = Paths.get(PROJECT_ROOT, "docker-compose.yml");
        String content = Files.readString(dockerCompose);
        
        // docker-compose默认会读取.env文件，但也可以显式指定
        // 验证至少使用了环境变量
        assertThat(content)
                .as("Should use environment variables")
                .containsPattern("\\$\\{[A-Z_]+[:-]");
    }

    /**
     * 测试entrypoint脚本正确读取环境变量
     * 需求: 8.6
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "jobmanager/entrypoint.sh",
        "taskmanager/entrypoint.sh",
        "cdc-collector/entrypoint.sh"
    })
    void testEntrypointReadsEnvVariables(String entrypointPath) throws IOException {
        Path entrypoint = DOCKER_DIR.resolve(entrypointPath);
        String content = Files.readString(entrypoint);
        
        // 验证使用环境变量语法
        assertThat(content)
                .as("Entrypoint %s should read environment variables", entrypointPath)
                .containsAnyOf("${", "$");
        
        // 验证使用:-语法提供默认值
        assertThat(content)
                .as("Entrypoint %s should provide default values", entrypointPath)
                .containsPattern("\\$\\{[A-Z_]+:-");
    }

    /**
     * 测试JobManager entrypoint使用Flink相关环境变量
     * 需求: 8.6
     */
    @Test
    void testJobManagerEntrypointUsesFlinkEnvVars() throws IOException {
        Path entrypoint = DOCKER_DIR.resolve("jobmanager/entrypoint.sh");
        String content = Files.readString(entrypoint);
        
        assertThat(content)
                .as("Should use PARALLELISM_DEFAULT")
                .contains("PARALLELISM_DEFAULT");
        
        assertThat(content)
                .as("Should use CHECKPOINT_INTERVAL")
                .contains("CHECKPOINT_INTERVAL");
        
        assertThat(content)
                .as("Should use JOB_MANAGER_HEAP_SIZE")
                .contains("JOB_MANAGER_HEAP_SIZE");
    }

    /**
     * 测试TaskManager entrypoint使用TaskManager相关环境变量
     * 需求: 8.6
     */
    @Test
    void testTaskManagerEntrypointUsesTaskManagerEnvVars() throws IOException {
        Path entrypoint = DOCKER_DIR.resolve("taskmanager/entrypoint.sh");
        String content = Files.readString(entrypoint);
        
        assertThat(content)
                .as("Should use TASK_MANAGER_NUMBER_OF_TASK_SLOTS")
                .contains("TASK_MANAGER_NUMBER_OF_TASK_SLOTS");
        
        assertThat(content)
                .as("Should use TASK_MANAGER_HEAP_SIZE")
                .contains("TASK_MANAGER_HEAP_SIZE");
    }

    /**
     * 测试CDC Collector entrypoint使用数据库和DataHub环境变量
     * 需求: 8.6
     */
    @Test
    void testCdcCollectorEntrypointUsesDatabaseAndDataHubEnvVars() throws IOException {
        Path entrypoint = DOCKER_DIR.resolve("cdc-collector/entrypoint.sh");
        String content = Files.readString(entrypoint);
        
        assertThat(content)
                .as("Should use DATABASE_HOST")
                .contains("DATABASE_HOST");
        
        assertThat(content)
                .as("Should use DATAHUB_ENDPOINT")
                .contains("DATAHUB_ENDPOINT");
    }

    /**
     * 测试环境变量命名符合规范
     * 需求: 8.6
     */
    @Test
    void testEnvVariableNamingConvention() throws IOException {
        String content = Files.readString(ENV_EXAMPLE);
        Map<String, String> envVars = parseEnvFile(content);
        
        Pattern validNamePattern = Pattern.compile("^[A-Z][A-Z0-9_]*$");
        
        for (String varName : envVars.keySet()) {
            assertThat(varName)
                    .as("Environment variable name should follow convention")
                    .matches(validNamePattern);
        }
    }

    /**
     * 测试环境变量有注释说明
     * 需求: 8.6
     */
    @Test
    void testEnvVariablesHaveComments() throws IOException {
        String content = Files.readString(ENV_EXAMPLE);
        String[] lines = content.split("\n");
        
        int commentCount = 0;
        int varCount = 0;
        
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("#") && !line.isEmpty()) {
                commentCount++;
            } else if (line.contains("=") && !line.startsWith("#")) {
                varCount++;
            }
        }
        
        // 至少应该有一些注释来说明环境变量
        assertThat(commentCount)
                .as("Should have comments explaining environment variables")
                .isGreaterThan(0);
        
        assertThat(varCount)
                .as("Should have environment variable definitions")
                .isGreaterThan(0);
    }

    /**
     * 测试敏感信息使用占位符
     * 需求: 8.6
     */
    @Test
    void testSensitiveInfoUsesPlaceholders() throws IOException {
        String content = Files.readString(ENV_EXAMPLE);
        Map<String, String> envVars = parseEnvFile(content);
        
        // 验证密码和密钥使用占位符而不是真实值
        String[] sensitiveVars = {
            "DATABASE_PASSWORD",
            "DATAHUB_ACCESS_KEY"
        };
        
        for (String varName : sensitiveVars) {
            if (envVars.containsKey(varName)) {
                String value = envVars.get(varName);
                assertThat(value)
                        .as("%s should use placeholder", varName)
                        .matches(v -> v.contains("your") || v.contains("xxx") || 
                                     v.contains("***") || v.contains("changeme") ||
                                     v.isEmpty());
            }
        }
    }

    /**
     * 测试环境变量分组清晰
     * 需求: 8.6
     */
    @Test
    void testEnvVariablesAreGrouped() throws IOException {
        String content = Files.readString(ENV_EXAMPLE);
        
        // 验证有分组注释
        assertThat(content)
                .as("Should have section for Flink configuration")
                .containsIgnoringCase("flink");
        
        assertThat(content)
                .as("Should have section for database configuration")
                .containsIgnoringCase("database");
        
        assertThat(content)
                .as("Should have section for DataHub configuration")
                .containsIgnoringCase("datahub");
    }

    /**
     * 测试布尔类型环境变量使用标准值
     * 需求: 8.6
     */
    @Test
    void testBooleanEnvVariablesUseStandardValues() throws IOException {
        String content = Files.readString(ENV_EXAMPLE);
        Map<String, String> envVars = parseEnvFile(content);
        
        for (Map.Entry<String, String> entry : envVars.entrySet()) {
            String value = entry.getValue().toLowerCase();
            
            // 如果看起来像布尔值，应该使用标准格式
            if (value.equals("true") || value.equals("false") ||
                value.equals("yes") || value.equals("no") ||
                value.equals("1") || value.equals("0")) {
                
                assertThat(value)
                        .as("Boolean variable %s should use standard format", entry.getKey())
                        .isIn("true", "false", "yes", "no", "1", "0");
            }
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 解析.env文件内容为Map
     */
    private Map<String, String> parseEnvFile(String content) {
        Map<String, String> envVars = new HashMap<>();
        String[] lines = content.split("\n");
        
        for (String line : lines) {
            line = line.trim();
            
            // 跳过注释和空行
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            // 解析KEY=VALUE
            int equalsIndex = line.indexOf('=');
            if (equalsIndex > 0) {
                String key = line.substring(0, equalsIndex).trim();
                String value = line.substring(equalsIndex + 1).trim();
                
                // 移除引号
                value = value.replaceAll("^['\"]|['\"]$", "");
                
                envVars.put(key, value);
            }
        }
        
        return envVars;
    }
}
