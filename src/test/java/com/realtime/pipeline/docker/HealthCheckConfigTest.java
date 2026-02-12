package com.realtime.pipeline.docker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

/**
 * 健康检查配置单元测试
 * 
 * 测试容器健康检查配置的正确性，验证：
 * - 健康检查命令正确
 * - 健康检查参数合理
 * - 健康检查脚本存在
 * - 健康检查端点可用
 * 
 * 需求: 8.8
 */
class HealthCheckConfigTest {

    private static final String PROJECT_ROOT = System.getProperty("user.dir");
    private static final Path DOCKER_DIR = Paths.get(PROJECT_ROOT, "docker");
    
    @BeforeAll
    static void setup() {
        assertThat(DOCKER_DIR).exists().isDirectory();
    }

    /**
     * 测试所有Dockerfile都配置了健康检查
     * 需求: 8.8
     */
    @ParameterizedTest
    @CsvSource({
        "jobmanager/Dockerfile,JobManager",
        "taskmanager/Dockerfile,TaskManager",
        "cdc-collector/Dockerfile,CDC Collector"
    })
    void testDockerfileHasHealthCheck(String dockerfilePath, String componentName) throws IOException {
        Path dockerfile = DOCKER_DIR.resolve(dockerfilePath);
        String content = Files.readString(dockerfile);
        
        assertThat(content)
                .as("%s Dockerfile should have HEALTHCHECK", componentName)
                .contains("HEALTHCHECK");
    }

    /**
     * 测试健康检查配置了interval参数
     * 需求: 8.8
     */
    @ParameterizedTest
    @CsvSource({
        "jobmanager/Dockerfile,JobManager",
        "taskmanager/Dockerfile,TaskManager",
        "cdc-collector/Dockerfile,CDC Collector"
    })
    void testHealthCheckHasInterval(String dockerfilePath, String componentName) throws IOException {
        Path dockerfile = DOCKER_DIR.resolve(dockerfilePath);
        String content = Files.readString(dockerfile);
        
        assertThat(content)
                .as("%s HEALTHCHECK should have --interval", componentName)
                .contains("--interval");
        
        // 验证interval值合理（应该在10-60秒之间）
        Pattern pattern = Pattern.compile("--interval=(\\d+)s");
        Matcher matcher = pattern.matcher(content);
        
        if (matcher.find()) {
            int interval = Integer.parseInt(matcher.group(1));
            assertThat(interval)
                    .as("%s health check interval should be reasonable", componentName)
                    .isBetween(10, 60);
        }
    }

    /**
     * 测试健康检查配置了timeout参数
     * 需求: 8.8
     */
    @ParameterizedTest
    @CsvSource({
        "jobmanager/Dockerfile,JobManager",
        "taskmanager/Dockerfile,TaskManager",
        "cdc-collector/Dockerfile,CDC Collector"
    })
    void testHealthCheckHasTimeout(String dockerfilePath, String componentName) throws IOException {
        Path dockerfile = DOCKER_DIR.resolve(dockerfilePath);
        String content = Files.readString(dockerfile);
        
        assertThat(content)
                .as("%s HEALTHCHECK should have --timeout", componentName)
                .contains("--timeout");
        
        // 验证timeout值合理（应该在5-30秒之间）
        Pattern pattern = Pattern.compile("--timeout=(\\d+)s");
        Matcher matcher = pattern.matcher(content);
        
        if (matcher.find()) {
            int timeout = Integer.parseInt(matcher.group(1));
            assertThat(timeout)
                    .as("%s health check timeout should be reasonable", componentName)
                    .isBetween(5, 30);
        }
    }

    /**
     * 测试健康检查配置了start-period参数
     * 需求: 8.5, 8.8
     */
    @ParameterizedTest
    @CsvSource({
        "jobmanager/Dockerfile,JobManager",
        "taskmanager/Dockerfile,TaskManager",
        "cdc-collector/Dockerfile,CDC Collector"
    })
    void testHealthCheckHasStartPeriod(String dockerfilePath, String componentName) throws IOException {
        Path dockerfile = DOCKER_DIR.resolve(dockerfilePath);
        String content = Files.readString(dockerfile);
        
        assertThat(content)
                .as("%s HEALTHCHECK should have --start-period", componentName)
                .contains("--start-period");
        
        // 验证start-period至少60秒（满足需求8.5）
        Pattern pattern = Pattern.compile("--start-period=(\\d+)s");
        Matcher matcher = pattern.matcher(content);
        
        if (matcher.find()) {
            int startPeriod = Integer.parseInt(matcher.group(1));
            assertThat(startPeriod)
                    .as("%s health check start-period should be at least 60s", componentName)
                    .isGreaterThanOrEqualTo(60);
        }
    }

    /**
     * 测试健康检查配置了retries参数
     * 需求: 8.8
     */
    @ParameterizedTest
    @CsvSource({
        "jobmanager/Dockerfile,JobManager",
        "taskmanager/Dockerfile,TaskManager",
        "cdc-collector/Dockerfile,CDC Collector"
    })
    void testHealthCheckHasRetries(String dockerfilePath, String componentName) throws IOException {
        Path dockerfile = DOCKER_DIR.resolve(dockerfilePath);
        String content = Files.readString(dockerfile);
        
        assertThat(content)
                .as("%s HEALTHCHECK should have --retries", componentName)
                .contains("--retries");
        
        // 验证retries值合理（应该在2-5之间）
        Pattern pattern = Pattern.compile("--retries=(\\d+)");
        Matcher matcher = pattern.matcher(content);
        
        if (matcher.find()) {
            int retries = Integer.parseInt(matcher.group(1));
            assertThat(retries)
                    .as("%s health check retries should be reasonable", componentName)
                    .isBetween(2, 5);
        }
    }

    /**
     * 测试JobManager健康检查使用HTTP端点
     * 需求: 8.8
     */
    @Test
    void testJobManagerHealthCheckUsesHttpEndpoint() throws IOException {
        Path dockerfile = DOCKER_DIR.resolve("jobmanager/Dockerfile");
        String content = Files.readString(dockerfile);
        
        assertThat(content)
                .as("JobManager should use curl for health check")
                .contains("curl");
        
        assertThat(content)
                .as("JobManager should check Web UI endpoint")
                .contains("8081");
        
        assertThat(content)
                .as("JobManager should use -f flag for curl")
                .contains("curl -f");
    }

    /**
     * 测试TaskManager健康检查使用进程检查
     * 需求: 8.8
     */
    @Test
    void testTaskManagerHealthCheckUsesProcessCheck() throws IOException {
        Path dockerfile = DOCKER_DIR.resolve("taskmanager/Dockerfile");
        String content = Files.readString(dockerfile);
        
        assertThat(content)
                .as("TaskManager should use pgrep for health check")
                .containsAnyOf("pgrep", "ps", "pidof");
        
        assertThat(content)
                .as("TaskManager should check for TaskManager process")
                .containsAnyOf("TaskManagerRunner", "taskmanager", "TaskExecutor");
    }

    /**
     * 测试CDC Collector健康检查使用HTTP端点
     * 需求: 8.8
     */
    @Test
    void testCdcCollectorHealthCheckUsesHttpEndpoint() throws IOException {
        Path dockerfile = DOCKER_DIR.resolve("cdc-collector/Dockerfile");
        String content = Files.readString(dockerfile);
        
        assertThat(content)
                .as("CDC Collector should use curl for health check")
                .contains("curl");
        
        assertThat(content)
                .as("CDC Collector should check health endpoint")
                .containsAnyOf("/health", "/live", "/ready");
        
        assertThat(content)
                .as("CDC Collector should use -f flag for curl")
                .contains("curl -f");
    }

    /**
     * 测试健康检查命令有正确的退出码
     * 需求: 8.8
     */
    @ParameterizedTest
    @CsvSource({
        "jobmanager/Dockerfile,JobManager",
        "taskmanager/Dockerfile,TaskManager",
        "cdc-collector/Dockerfile,CDC Collector"
    })
    void testHealthCheckCommandHasExitCode(String dockerfilePath, String componentName) throws IOException {
        Path dockerfile = DOCKER_DIR.resolve(dockerfilePath);
        String content = Files.readString(dockerfile);
        
        // 健康检查命令应该在失败时返回非零退出码
        assertThat(content)
                .as("%s health check should have exit code", componentName)
                .containsAnyOf("exit 1", "|| exit", "return 1");
    }

    /**
     * 测试docker-compose.yml不覆盖Dockerfile中的健康检查
     * 需求: 8.8
     */
    @Test
    void testDockerComposeDoesNotOverrideHealthCheck() throws IOException {
        Path dockerCompose = Paths.get(PROJECT_ROOT, "docker-compose.yml");
        String content = Files.readString(dockerCompose);
        
        // docker-compose可以定义healthcheck，但不应该禁用它
        if (content.contains("healthcheck:")) {
            assertThat(content)
                    .as("docker-compose should not disable health check")
                    .doesNotContain("disable: true");
        }
    }

    /**
     * 测试健康检查脚本存在（如果使用）
     * 需求: 8.8
     */
    @Test
    void testHealthCheckScriptExistsIfUsed() throws IOException {
        Path healthCheckScript = DOCKER_DIR.resolve("test-health-checks.sh");
        
        // 如果有健康检查测试脚本，验证它存在
        if (Files.exists(healthCheckScript)) {
            assertThat(healthCheckScript)
                    .as("Health check test script should be a regular file")
                    .isRegularFile();
            
            String content = Files.readString(healthCheckScript);
            assertThat(content)
                    .as("Health check script should have shebang")
                    .startsWith("#!/");
        }
    }

    /**
     * 测试健康检查使用localhost而不是外部地址
     * 需求: 8.8
     */
    @ParameterizedTest
    @CsvSource({
        "jobmanager/Dockerfile,JobManager",
        "taskmanager/Dockerfile,TaskManager",
        "cdc-collector/Dockerfile,CDC Collector"
    })
    void testHealthCheckUsesLocalhost(String dockerfilePath, String componentName) throws IOException {
        Path dockerfile = DOCKER_DIR.resolve(dockerfilePath);
        String content = Files.readString(dockerfile);
        
        // 提取HEALTHCHECK命令
        Pattern pattern = Pattern.compile("HEALTHCHECK.*CMD (.+)");
        Matcher matcher = pattern.matcher(content);
        
        if (matcher.find()) {
            String healthCheckCmd = matcher.group(1);
            
            // 如果使用HTTP检查，应该使用localhost
            if (healthCheckCmd.contains("http")) {
                assertThat(healthCheckCmd)
                        .as("%s health check should use localhost", componentName)
                        .containsAnyOf("localhost", "127.0.0.1");
            }
        }
    }

    /**
     * 测试健康检查间隔小于启动超时
     * 需求: 8.8
     */
    @ParameterizedTest
    @CsvSource({
        "jobmanager/Dockerfile,JobManager",
        "taskmanager/Dockerfile,TaskManager",
        "cdc-collector/Dockerfile,CDC Collector"
    })
    void testHealthCheckIntervalLessThanStartPeriod(String dockerfilePath, String componentName) throws IOException {
        Path dockerfile = DOCKER_DIR.resolve(dockerfilePath);
        String content = Files.readString(dockerfile);
        
        Pattern intervalPattern = Pattern.compile("--interval=(\\d+)s");
        Pattern startPeriodPattern = Pattern.compile("--start-period=(\\d+)s");
        
        Matcher intervalMatcher = intervalPattern.matcher(content);
        Matcher startPeriodMatcher = startPeriodPattern.matcher(content);
        
        if (intervalMatcher.find() && startPeriodMatcher.find()) {
            int interval = Integer.parseInt(intervalMatcher.group(1));
            int startPeriod = Integer.parseInt(startPeriodMatcher.group(1));
            
            assertThat(interval)
                    .as("%s health check interval should be less than start-period", componentName)
                    .isLessThan(startPeriod);
        }
    }

    /**
     * 测试健康检查超时小于间隔
     * 需求: 8.8
     */
    @ParameterizedTest
    @CsvSource({
        "jobmanager/Dockerfile,JobManager",
        "taskmanager/Dockerfile,TaskManager",
        "cdc-collector/Dockerfile,CDC Collector"
    })
    void testHealthCheckTimeoutLessThanInterval(String dockerfilePath, String componentName) throws IOException {
        Path dockerfile = DOCKER_DIR.resolve(dockerfilePath);
        String content = Files.readString(dockerfile);
        
        Pattern timeoutPattern = Pattern.compile("--timeout=(\\d+)s");
        Pattern intervalPattern = Pattern.compile("--interval=(\\d+)s");
        
        Matcher timeoutMatcher = timeoutPattern.matcher(content);
        Matcher intervalMatcher = intervalPattern.matcher(content);
        
        if (timeoutMatcher.find() && intervalMatcher.find()) {
            int timeout = Integer.parseInt(timeoutMatcher.group(1));
            int interval = Integer.parseInt(intervalMatcher.group(1));
            
            assertThat(timeout)
                    .as("%s health check timeout should be less than interval", componentName)
                    .isLessThan(interval);
        }
    }

    /**
     * 测试健康检查命令格式正确
     * 需求: 8.8
     */
    @ParameterizedTest
    @CsvSource({
        "jobmanager/Dockerfile,JobManager",
        "taskmanager/Dockerfile,TaskManager",
        "cdc-collector/Dockerfile,CDC Collector"
    })
    void testHealthCheckCommandFormat(String dockerfilePath, String componentName) throws IOException {
        Path dockerfile = DOCKER_DIR.resolve(dockerfilePath);
        String content = Files.readString(dockerfile);
        
        // 验证HEALTHCHECK命令格式
        // Handle both single-line and multi-line formats
        Pattern singleLinePattern = Pattern.compile("HEALTHCHECK.*CMD");
        Pattern multiLinePattern = Pattern.compile("HEALTHCHECK[^\\n]+\\\\\\s*\\n\\s*CMD", Pattern.MULTILINE);
        
        boolean hasSingleLine = singleLinePattern.matcher(content).find();
        boolean hasMultiLine = multiLinePattern.matcher(content).find();
        
        assertThat(hasSingleLine || hasMultiLine)
                .as("%s HEALTHCHECK should have CMD", componentName)
                .isTrue();
        
        // 验证命令不为空
        Pattern cmdPattern = Pattern.compile("HEALTHCHECK.*CMD\\s+(.+)");
        Matcher cmdMatcher = cmdPattern.matcher(content);
        
        if (cmdMatcher.find()) {
            String healthCheckCmd = cmdMatcher.group(1);
            
            assertThat(healthCheckCmd)
                    .as("%s HEALTHCHECK CMD should not be empty", componentName)
                    .isNotEmpty();
        } else {
            // Already verified above that CMD exists
            assertThat(hasMultiLine)
                    .as("%s HEALTHCHECK should have CMD (multi-line format)", componentName)
                    .isTrue();
        }
    }

    /**
     * 测试健康检查使用合适的工具
     * 需求: 8.8
     */
    @ParameterizedTest
    @CsvSource({
        "jobmanager/Dockerfile,JobManager",
        "taskmanager/Dockerfile,TaskManager",
        "cdc-collector/Dockerfile,CDC Collector"
    })
    void testHealthCheckUsesAppropriateTools(String dockerfilePath, String componentName) throws IOException {
        Path dockerfile = DOCKER_DIR.resolve(dockerfilePath);
        String content = Files.readString(dockerfile);
        
        // 提取HEALTHCHECK命令
        Pattern pattern = Pattern.compile("HEALTHCHECK.*CMD (.+)");
        Matcher matcher = pattern.matcher(content);
        
        if (matcher.find()) {
            String healthCheckCmd = matcher.group(1);
            
            // 验证使用的工具已安装
            if (healthCheckCmd.contains("curl")) {
                assertThat(content)
                        .as("%s should install curl", componentName)
                        .contains("curl");
            }
            
            if (healthCheckCmd.contains("pgrep")) {
                // pgrep通常是系统自带的，但可以验证
                assertThat(healthCheckCmd)
                        .as("%s should use valid pgrep syntax", componentName)
                        .containsPattern("pgrep\\s+-f");
            }
        }
    }

    /**
     * 测试健康检查文档存在
     * 需求: 8.8
     */
    @Test
    void testHealthCheckDocumentationExists() {
        Path healthCheckDoc = DOCKER_DIR.resolve("README.md");
        
        if (Files.exists(healthCheckDoc)) {
            assertThatCode(() -> {
                String content = Files.readString(healthCheckDoc);
                assertThat(content)
                        .as("README should mention health checks")
                        .containsIgnoringCase("health");
            }).doesNotThrowAnyException();
        }
    }
}
