package com.realtime.pipeline.docker;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.assertj.core.api.Assertions;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

/**
 * 容器化的基于属性的测试
 * Feature: realtime-data-pipeline
 * 
 * 使用jqwik进行基于属性的测试，验证Docker容器化部署的通用属性
 * 
 * 注意：这些测试需要Docker环境，如果Docker不可用，测试将被跳过
 * 
 * 运行方式：
 * 1. 确保Docker和docker-compose已安装并运行
 * 2. 运行测试：mvn test -Dtest=ContainerPropertyTest
 * 3. 如果要跳过这些测试：mvn test -DskipDockerTests=true
 * 
 * 环境要求：
 * - Docker Engine 20.10+
 * - Docker Compose 2.0+
 * - 至少4GB可用内存
 * - 至少10GB可用磁盘空间
 */
class ContainerPropertyTest {

    private static final String DOCKER_COMPOSE_FILE = "docker-compose.yml";
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(70);
    private static final Duration RESTART_TIMEOUT = Duration.ofMinutes(3);
    private static final boolean SKIP_DOCKER_TESTS = Boolean.getBoolean("skipDockerTests");
    
    /**
     * Property 33: 容器启动时效性
     * **Validates: Requirements 8.5**
     * 
     * 对于任何容器启动操作，系统应该在60秒内完成初始化并进入就绪状态
     * 
     * 此测试验证所有容器类型（JobManager、TaskManager、CDC Collector）
     * 都能在规定时间内启动并通过健康检查
     */
    @Property(tries = 10)
    void property33_containerStartupTimeliness(
            @ForAll("containerTypes") String containerType) {
        
        // 检查是否跳过Docker测试
        if (SKIP_DOCKER_TESTS) {
            Assume.that(false);
            return;
        }
        
        // 检查Docker是否可用
        if (!isDockerAvailable()) {
            System.out.println("Docker not available, skipping test");
            Assume.that(false); // 跳过测试
            return;
        }
        
        String serviceName = getServiceName(containerType);
        long startTime = System.currentTimeMillis();
        
        try {
            // 启动特定服务
            ProcessResult startResult = executeDockerCompose(
                    "up", "-d", serviceName
            );
            
            assertThat(startResult.exitCode)
                    .as("Docker compose up should succeed")
                    .isEqualTo(0);
            
            // 等待容器健康检查通过
            boolean healthy = waitForContainerHealthy(serviceName, STARTUP_TIMEOUT);
            long elapsedTime = System.currentTimeMillis() - startTime;
            
            // 验证容器在60秒内启动并健康
            assertThat(healthy)
                    .as("Container %s should become healthy", serviceName)
                    .isTrue();
            
            assertThat(elapsedTime)
                    .as("Container %s startup time should be within 60 seconds", serviceName)
                    .isLessThanOrEqualTo(60000L);
            
            // 验证容器确实在运行
            ProcessResult psResult = executeDockerCompose("ps", serviceName);
            assertThat(psResult.output)
                    .as("Container should be running")
                    .contains("Up");
            
        } finally {
            // 清理：停止并删除容器
            cleanupContainer(serviceName);
        }
    }

    /**
     * Property 34: 环境变量配置
     * **Validates: Requirements 8.6**
     * 
     * 对于任何通过环境变量设置的系统参数，该参数应该在系统运行时生效
     * 
     * 此测试验证容器能够正确读取和应用环境变量配置
     */
    @Property(tries = 15)
    void property34_environmentVariableConfiguration(
            @ForAll("validEnvironmentVariables") Map<String, String> envVars) {
        
        // 检查是否跳过Docker测试
        if (SKIP_DOCKER_TESTS) {
            Assume.that(false);
            return;
        }
        
        // 检查Docker是否可用
        if (!isDockerAvailable()) {
            System.out.println("Docker not available, skipping test");
            Assume.that(false);
            return;
        }
        
        // 创建临时的docker-compose override文件
        Path tempOverride = null;
        
        try {
            tempOverride = createDockerComposeOverride(envVars);
            
            // 使用环境变量启动容器
            ProcessResult startResult = executeDockerCompose(
                    "-f", DOCKER_COMPOSE_FILE,
                    "-f", tempOverride.toString(),
                    "up", "-d", "jobmanager"
            );
            
            assertThat(startResult.exitCode)
                    .as("Docker compose up with env vars should succeed")
                    .isEqualTo(0);
            
            // 等待容器启动
            boolean healthy = waitForContainerHealthy("jobmanager", STARTUP_TIMEOUT);
            assertThat(healthy).isTrue();
            
            // 验证环境变量在容器中生效
            for (Map.Entry<String, String> entry : envVars.entrySet()) {
                String envName = entry.getKey();
                String expectedValue = entry.getValue();
                
                // 在容器中检查环境变量
                ProcessResult envCheckResult = executeDockerCommand(
                        "exec", "flink-jobmanager",
                        "printenv", envName
                );
                
                if (envCheckResult.exitCode == 0) {
                    String actualValue = envCheckResult.output.trim();
                    assertThat(actualValue)
                            .as("Environment variable %s should have correct value", envName)
                            .isEqualTo(expectedValue);
                }
            }
            
            // 验证配置确实被应用（检查Flink配置）
            ProcessResult configCheckResult = executeDockerCommand(
                    "exec", "flink-jobmanager",
                    "cat", "/opt/flink/conf/flink-conf.yaml"
            );
            
            assertThat(configCheckResult.exitCode)
                    .as("Should be able to read Flink configuration")
                    .isEqualTo(0);
            
            // 验证配置文件包含预期的值
            String config = configCheckResult.output;
            if (envVars.containsKey("PARALLELISM_DEFAULT")) {
                assertThat(config)
                        .as("Configuration should reflect environment variable")
                        .contains("parallelism.default: " + envVars.get("PARALLELISM_DEFAULT"));
            }
            
        } catch (IOException e) {
            fail("Failed to create docker-compose override: " + e.getMessage());
        } finally {
            // 清理
            cleanupContainer("jobmanager");
            if (tempOverride != null) {
                try {
                    Files.deleteIfExists(tempOverride);
                } catch (IOException e) {
                    // Ignore cleanup errors
                }
            }
        }
    }

    /**
     * Property 18: 容器自动重启
     * **Validates: Requirements 4.5**
     * 
     * 对于任何容器崩溃事件，系统应该在2分钟内自动重启该容器
     * 
     * 此测试通过强制停止容器来模拟崩溃，然后验证容器自动重启
     */
    @Property(tries = 10)
    void property18_containerAutoRestart(
            @ForAll("containerTypes") String containerType) {
        
        // 检查是否跳过Docker测试
        if (SKIP_DOCKER_TESTS) {
            Assume.that(false);
            return;
        }
        
        // 检查Docker是否可用
        if (!isDockerAvailable()) {
            System.out.println("Docker not available, skipping test");
            Assume.that(false);
            return;
        }
        
        String serviceName = getServiceName(containerType);
        String containerName = getContainerName(containerType);
        
        try {
            // 启动容器
            ProcessResult startResult = executeDockerCompose("up", "-d", serviceName);
            assertThat(startResult.exitCode).isEqualTo(0);
            
            // 等待容器健康
            boolean initiallyHealthy = waitForContainerHealthy(serviceName, STARTUP_TIMEOUT);
            assertThat(initiallyHealthy)
                    .as("Container should start successfully")
                    .isTrue();
            
            // 获取初始容器ID
            String initialContainerId = getContainerId(containerName);
            assertThat(initialContainerId)
                    .as("Should be able to get container ID")
                    .isNotEmpty();
            
            // 模拟容器崩溃（强制停止容器）
            long crashTime = System.currentTimeMillis();
            ProcessResult killResult = executeDockerCommand("kill", containerName);
            assertThat(killResult.exitCode)
                    .as("Should be able to kill container")
                    .isEqualTo(0);
            
            // 等待一小段时间确保容器已停止
            Thread.sleep(2000);
            
            // 验证容器已停止
            ProcessResult statusAfterKill = executeDockerCommand("ps", "-a", "-f", "name=" + containerName);
            assertThat(statusAfterKill.output)
                    .as("Container should be stopped or restarting")
                    .matches(s -> s.contains("Restarting") || s.contains("Exited"));
            
            // 等待容器自动重启并恢复健康
            boolean restartedHealthy = waitForContainerHealthy(serviceName, RESTART_TIMEOUT);
            long restartTime = System.currentTimeMillis() - crashTime;
            
            // 验证容器在2分钟内重启
            assertThat(restartedHealthy)
                    .as("Container %s should restart after crash", serviceName)
                    .isTrue();
            
            assertThat(restartTime)
                    .as("Container %s restart time should be within 2 minutes", serviceName)
                    .isLessThanOrEqualTo(120000L);
            
            // 验证容器确实重启了（可能是新的容器ID或重启计数增加）
            String newContainerId = getContainerId(containerName);
            ProcessResult inspectResult = executeDockerCommand(
                    "inspect", "--format={{.RestartCount}}", containerName
            );
            
            if (inspectResult.exitCode == 0) {
                int restartCount = Integer.parseInt(inspectResult.output.trim());
                assertThat(restartCount)
                        .as("Container should have restarted at least once")
                        .isGreaterThan(0);
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Test interrupted: " + e.getMessage());
        } finally {
            // 清理
            cleanupContainer(serviceName);
        }
    }

    // ==================== 数据生成器 ====================

    @Provide
    Arbitrary<String> containerTypes() {
        return Arbitraries.of("jobmanager", "taskmanager", "cdc-collector");
    }

    @Provide
    Arbitrary<Map<String, String>> validEnvironmentVariables() {
        return Combinators.combine(
                Arbitraries.integers().between(1, 16),  // PARALLELISM_DEFAULT
                Arbitraries.integers().between(60000, 600000),  // CHECKPOINT_INTERVAL
                Arbitraries.of("hashmap", "rocksdb"),  // STATE_BACKEND
                Arbitraries.integers().between(512, 2048)  // JOB_MANAGER_HEAP_SIZE (MB)
        ).as((parallelism, checkpointInterval, stateBackend, heapSize) -> {
            Map<String, String> envVars = new HashMap<>();
            envVars.put("PARALLELISM_DEFAULT", String.valueOf(parallelism));
            envVars.put("CHECKPOINT_INTERVAL", String.valueOf(checkpointInterval));
            envVars.put("STATE_BACKEND", stateBackend);
            envVars.put("JOB_MANAGER_HEAP_SIZE", heapSize + "m");
            return envVars;
        });
    }

    // ==================== 辅助方法 ====================

    /**
     * 检查Docker是否可用
     */
    private boolean isDockerAvailable() {
        try {
            ProcessResult result = executeCommand("docker", "version");
            return result.exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取服务名称
     */
    private String getServiceName(String containerType) {
        return containerType;
    }

    /**
     * 获取容器名称
     */
    private String getContainerName(String containerType) {
        switch (containerType) {
            case "jobmanager":
                return "flink-jobmanager";
            case "taskmanager":
                return "flink-taskmanager-1";
            case "cdc-collector":
                return "cdc-collector";
            default:
                return containerType;
        }
    }

    /**
     * 等待容器健康检查通过
     */
    private boolean waitForContainerHealthy(String serviceName, Duration timeout) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeout.toMillis();
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                ProcessResult result = executeDockerCompose("ps", serviceName);
                
                if (result.exitCode == 0) {
                    String output = result.output;
                    
                    // 检查容器状态
                    if (output.contains("Up") && output.contains("(healthy)")) {
                        return true;
                    }
                    
                    // 对于没有健康检查的容器，只要是Up状态就认为健康
                    if (output.contains("Up") && !output.contains("(health:")) {
                        // 额外等待一小段时间确保容器完全启动
                        Thread.sleep(5000);
                        return true;
                    }
                }
                
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        return false;
    }

    /**
     * 获取容器ID
     */
    private String getContainerId(String containerName) {
        ProcessResult result = executeDockerCommand(
                "ps", "-q", "-f", "name=" + containerName
        );
        
        if (result.exitCode == 0) {
            return result.output.trim();
        }
        
        return "";
    }

    /**
     * 创建docker-compose override文件
     */
    private Path createDockerComposeOverride(Map<String, String> envVars) throws IOException {
        Path tempFile = Files.createTempFile("docker-compose-override-", ".yml");
        
        StringBuilder yaml = new StringBuilder();
        yaml.append("version: '3.8'\n");
        yaml.append("services:\n");
        yaml.append("  jobmanager:\n");
        yaml.append("    environment:\n");
        
        for (Map.Entry<String, String> entry : envVars.entrySet()) {
            yaml.append("      - ").append(entry.getKey())
                    .append("=").append(entry.getValue()).append("\n");
        }
        
        Files.writeString(tempFile, yaml.toString());
        return tempFile;
    }

    /**
     * 执行docker-compose命令
     */
    private ProcessResult executeDockerCompose(String... args) {
        List<String> command = new ArrayList<>();
        command.add("docker-compose");
        command.addAll(Arrays.asList(args));
        return executeCommand(command.toArray(new String[0]));
    }

    /**
     * 执行docker命令
     */
    private ProcessResult executeDockerCommand(String... args) {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(Arrays.asList(args));
        return executeCommand(command.toArray(new String[0]));
    }

    /**
     * 执行命令并返回结果
     */
    private ProcessResult executeCommand(String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            return new ProcessResult(exitCode, output.toString());
            
        } catch (IOException | InterruptedException e) {
            return new ProcessResult(-1, "Error: " + e.getMessage());
        }
    }

    /**
     * 清理容器
     */
    private void cleanupContainer(String serviceName) {
        try {
            executeDockerCompose("down", serviceName);
            // 额外等待确保清理完成
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 进程执行结果
     */
    private static class ProcessResult {
        final int exitCode;
        final String output;
        
        ProcessResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
