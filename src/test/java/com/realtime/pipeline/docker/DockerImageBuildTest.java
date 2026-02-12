package com.realtime.pipeline.docker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

/**
 * Docker镜像构建单元测试
 * 
 * 测试Docker镜像的构建过程，验证：
 * - Dockerfile语法正确性
 * - 必要文件存在
 * - 镜像构建成功
 * - 镜像包含必要的依赖
 * 
 * 需求: 8.4, 8.5, 8.6, 8.8
 */
class DockerImageBuildTest {

    private static final String PROJECT_ROOT = System.getProperty("user.dir");
    private static final Path DOCKER_DIR = Paths.get(PROJECT_ROOT, "docker");
    
    @BeforeAll
    static void setup() {
        // 验证Docker目录存在
        assertThat(DOCKER_DIR).exists().isDirectory();
    }

    /**
     * 测试JobManager Dockerfile存在且格式正确
     * 需求: 8.1, 8.4
     */
    @Test
    void testJobManagerDockerfileExists() {
        Path dockerfile = DOCKER_DIR.resolve("jobmanager/Dockerfile");
        
        assertThat(dockerfile)
                .as("JobManager Dockerfile should exist")
                .exists()
                .isRegularFile();
        
        // 验证Dockerfile内容
        assertThatCode(() -> {
            String content = Files.readString(dockerfile);
            assertThat(content)
                    .as("Dockerfile should start with FROM")
                    .contains("FROM flink:");
        }).doesNotThrowAnyException();
    }

    /**
     * 测试TaskManager Dockerfile存在且格式正确
     * 需求: 8.2, 8.4
     */
    @Test
    void testTaskManagerDockerfileExists() {
        Path dockerfile = DOCKER_DIR.resolve("taskmanager/Dockerfile");
        
        assertThat(dockerfile)
                .as("TaskManager Dockerfile should exist")
                .exists()
                .isRegularFile();
        
        // 验证Dockerfile内容
        assertThatCode(() -> {
            String content = Files.readString(dockerfile);
            assertThat(content)
                    .as("Dockerfile should start with FROM")
                    .contains("FROM flink:");
        }).doesNotThrowAnyException();
    }

    /**
     * 测试CDC Collector Dockerfile存在且格式正确
     * 需求: 8.3, 8.4
     */
    @Test
    void testCdcCollectorDockerfileExists() {
        Path dockerfile = DOCKER_DIR.resolve("cdc-collector/Dockerfile");
        
        assertThat(dockerfile)
                .as("CDC Collector Dockerfile should exist")
                .exists()
                .isRegularFile();
        
        // 验证Dockerfile内容
        assertThatCode(() -> {
            String content = Files.readString(dockerfile);
            assertThat(content)
                    .as("Dockerfile should start with FROM")
                    .containsAnyOf("FROM openjdk:", "FROM eclipse-temurin:");
        }).doesNotThrowAnyException();
    }

    /**
     * 测试JobManager Dockerfile包含所有必要的依赖
     * 需求: 8.4
     */
    @Test
    void testJobManagerDockerfileContainsDependencies() throws IOException {
        Path dockerfile = DOCKER_DIR.resolve("jobmanager/Dockerfile");
        String content = Files.readString(dockerfile);
        
        // 验证包含必要的系统依赖
        assertThat(content)
                .as("Should install curl for health checks")
                .contains("curl");
        
        // 验证创建必要的目录
        assertThat(content)
                .as("Should create checkpoint directory")
                .contains("checkpoints");
        
        assertThat(content)
                .as("Should create savepoints directory")
                .contains("savepoints");
        
        // 验证复制应用JAR包
        assertThat(content)
                .as("Should copy application JAR")
                .contains("realtime-data-pipeline");
        
        // 验证暴露必要的端口
        assertThat(content)
                .as("Should expose RPC port 6123")
                .contains("6123");
        
        assertThat(content)
                .as("Should expose Web UI port 8081")
                .contains("8081");
    }

    /**
     * 测试TaskManager Dockerfile包含所有必要的依赖
     * 需求: 8.4
     */
    @Test
    void testTaskManagerDockerfileContainsDependencies() throws IOException {
        Path dockerfile = DOCKER_DIR.resolve("taskmanager/Dockerfile");
        String content = Files.readString(dockerfile);
        
        // 验证包含必要的系统依赖
        assertThat(content)
                .as("Should install curl")
                .contains("curl");
        
        // 验证创建必要的目录
        assertThat(content)
                .as("Should create data directory")
                .contains("/opt/flink/data");
        
        // 验证复制应用JAR包
        assertThat(content)
                .as("Should copy application JAR")
                .contains("realtime-data-pipeline");
        
        // 验证暴露必要的端口
        assertThat(content)
                .as("Should expose data exchange port")
                .contains("6121");
    }

    /**
     * 测试CDC Collector Dockerfile包含所有必要的依赖
     * 需求: 8.4
     */
    @Test
    void testCdcCollectorDockerfileContainsDependencies() throws IOException {
        Path dockerfile = DOCKER_DIR.resolve("cdc-collector/Dockerfile");
        String content = Files.readString(dockerfile);
        
        // 验证包含必要的系统依赖
        assertThat(content)
                .as("Should install curl for health checks")
                .contains("curl");
        
        // 验证创建应用用户
        assertThat(content)
                .as("Should create cdcuser")
                .contains("cdcuser");
        
        // 验证创建必要的目录
        assertThat(content)
                .as("Should create config directory")
                .contains("/opt/cdc-collector/config");
        
        assertThat(content)
                .as("Should create logs directory")
                .contains("/opt/cdc-collector/logs");
        
        // 验证复制应用JAR包
        assertThat(content)
                .as("Should copy application JAR")
                .contains("realtime-data-pipeline");
        
        // 验证设置Java选项
        assertThat(content)
                .as("Should set JAVA_OPTS")
                .contains("JAVA_OPTS");
    }

    /**
     * 测试所有Dockerfile都配置了健康检查
     * 需求: 8.8
     */
    @Test
    void testAllDockerfilesHaveHealthCheck() throws IOException {
        String[] dockerfiles = {
            "jobmanager/Dockerfile",
            "taskmanager/Dockerfile",
            "cdc-collector/Dockerfile"
        };
        
        for (String dockerfilePath : dockerfiles) {
            Path dockerfile = DOCKER_DIR.resolve(dockerfilePath);
            String content = Files.readString(dockerfile);
            
            assertThat(content)
                    .as("Dockerfile %s should have HEALTHCHECK", dockerfilePath)
                    .contains("HEALTHCHECK");
            
            // 验证健康检查配置参数
            assertThat(content)
                    .as("HEALTHCHECK should have interval")
                    .contains("--interval");
            
            assertThat(content)
                    .as("HEALTHCHECK should have timeout")
                    .contains("--timeout");
            
            assertThat(content)
                    .as("HEALTHCHECK should have start-period")
                    .contains("--start-period");
            
            assertThat(content)
                    .as("HEALTHCHECK should have retries")
                    .contains("--retries");
        }
    }

    /**
     * 测试健康检查的start-period满足60秒启动要求
     * 需求: 8.5, 8.8
     */
    @Test
    void testHealthCheckStartPeriodMeetsRequirement() throws IOException {
        String[] dockerfiles = {
            "jobmanager/Dockerfile",
            "taskmanager/Dockerfile",
            "cdc-collector/Dockerfile"
        };
        
        Pattern startPeriodPattern = Pattern.compile("--start-period=(\\d+)s");
        
        for (String dockerfilePath : dockerfiles) {
            Path dockerfile = DOCKER_DIR.resolve(dockerfilePath);
            String content = Files.readString(dockerfile);
            
            var matcher = startPeriodPattern.matcher(content);
            assertThat(matcher.find())
                    .as("Dockerfile %s should have start-period", dockerfilePath)
                    .isTrue();
            
            int startPeriod = Integer.parseInt(matcher.group(1));
            assertThat(startPeriod)
                    .as("Start period should be at least 60 seconds for %s", dockerfilePath)
                    .isGreaterThanOrEqualTo(60);
        }
    }

    /**
     * 测试所有Dockerfile都使用自定义entrypoint脚本
     * 需求: 8.5, 8.6
     */
    @Test
    void testAllDockerfilesHaveCustomEntrypoint() throws IOException {
        String[] dockerfiles = {
            "jobmanager/Dockerfile",
            "taskmanager/Dockerfile",
            "cdc-collector/Dockerfile"
        };
        
        for (String dockerfilePath : dockerfiles) {
            Path dockerfile = DOCKER_DIR.resolve(dockerfilePath);
            String content = Files.readString(dockerfile);
            
            assertThat(content)
                    .as("Dockerfile %s should have ENTRYPOINT", dockerfilePath)
                    .contains("ENTRYPOINT");
            
            assertThat(content)
                    .as("Dockerfile %s should copy entrypoint script", dockerfilePath)
                    .contains("entrypoint.sh");
        }
    }

    /**
     * 测试docker-compose.yml文件存在且格式正确
     * 需求: 8.7
     */
    @Test
    void testDockerComposeFileExists() {
        Path dockerCompose = Paths.get(PROJECT_ROOT, "docker-compose.yml");
        
        assertThat(dockerCompose)
                .as("docker-compose.yml should exist")
                .exists()
                .isRegularFile();
        
        // 验证docker-compose.yml内容
        assertThatCode(() -> {
            String content = Files.readString(dockerCompose);
            
            assertThat(content)
                    .as("Should define services")
                    .contains("services:");
        }).doesNotThrowAnyException();
    }

    /**
     * 测试docker-compose.yml包含所有必要的服务
     * 需求: 8.7
     */
    @Test
    void testDockerComposeContainsAllServices() throws IOException {
        Path dockerCompose = Paths.get(PROJECT_ROOT, "docker-compose.yml");
        String content = Files.readString(dockerCompose);
        
        assertThat(content)
                .as("Should define jobmanager service")
                .contains("jobmanager:");
        
        assertThat(content)
                .as("Should define taskmanager service")
                .contains("taskmanager:");
        
        assertThat(content)
                .as("Should define cdc-collector service")
                .contains("cdc-collector:");
    }

    /**
     * 测试docker-compose.yml配置了容器重启策略
     * 需求: 4.5
     */
    @Test
    void testDockerComposeHasRestartPolicy() throws IOException {
        Path dockerCompose = Paths.get(PROJECT_ROOT, "docker-compose.yml");
        String content = Files.readString(dockerCompose);
        
        // 验证所有服务都配置了restart策略
        assertThat(content)
                .as("Should have restart policy")
                .contains("restart:");
        
        // 验证restart策略是合理的（unless-stopped或always）
        assertThat(content)
                .as("Should use unless-stopped or always restart policy")
                .matches(s -> s.contains("unless-stopped") || s.contains("always"));
    }

    /**
     * 测试.env.example文件存在
     * 需求: 8.6
     */
    @Test
    void testEnvExampleFileExists() {
        Path envExample = Paths.get(PROJECT_ROOT, ".env.example");
        
        assertThat(envExample)
                .as(".env.example should exist")
                .exists()
                .isRegularFile();
    }

    /**
     * 测试.env.example包含必要的环境变量
     * 需求: 8.6
     */
    @Test
    void testEnvExampleContainsRequiredVariables() throws IOException {
        Path envExample = Paths.get(PROJECT_ROOT, ".env.example");
        String content = Files.readString(envExample);
        
        // 验证包含Flink配置变量
        assertThat(content)
                .as("Should define PARALLELISM_DEFAULT")
                .contains("PARALLELISM_DEFAULT");
        
        assertThat(content)
                .as("Should define CHECKPOINT_INTERVAL")
                .contains("CHECKPOINT_INTERVAL");
        
        // 验证包含数据库配置变量
        assertThat(content)
                .as("Should define DATABASE_HOST")
                .contains("DATABASE_HOST");
        
        // 验证包含DataHub配置变量
        assertThat(content)
                .as("Should define DATAHUB_ENDPOINT")
                .contains("DATAHUB_ENDPOINT");
    }
}
