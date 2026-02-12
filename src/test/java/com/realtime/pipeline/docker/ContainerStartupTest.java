package com.realtime.pipeline.docker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * 容器启动和初始化单元测试
 * 
 * 测试容器启动脚本的正确性，验证：
 * - Entrypoint脚本存在且可执行
 * - 脚本包含必要的初始化逻辑
 * - 脚本支持环境变量配置
 * - 脚本包含错误处理
 * 
 * 需求: 8.5, 8.6
 */
class ContainerStartupTest {

    private static final String PROJECT_ROOT = System.getProperty("user.dir");
    private static final Path DOCKER_DIR = Paths.get(PROJECT_ROOT, "docker");
    
    @BeforeAll
    static void setup() {
        assertThat(DOCKER_DIR).exists().isDirectory();
    }

    /**
     * 测试JobManager entrypoint脚本存在
     * 需求: 8.5
     */
    @Test
    void testJobManagerEntrypointExists() {
        Path entrypoint = DOCKER_DIR.resolve("jobmanager/entrypoint.sh");
        
        assertThat(entrypoint)
                .as("JobManager entrypoint.sh should exist")
                .exists()
                .isRegularFile();
    }

    /**
     * 测试TaskManager entrypoint脚本存在
     * 需求: 8.5
     */
    @Test
    void testTaskManagerEntrypointExists() {
        Path entrypoint = DOCKER_DIR.resolve("taskmanager/entrypoint.sh");
        
        assertThat(entrypoint)
                .as("TaskManager entrypoint.sh should exist")
                .exists()
                .isRegularFile();
    }

    /**
     * 测试CDC Collector entrypoint脚本存在
     * 需求: 8.5
     */
    @Test
    void testCdcCollectorEntrypointExists() {
        Path entrypoint = DOCKER_DIR.resolve("cdc-collector/entrypoint.sh");
        
        assertThat(entrypoint)
                .as("CDC Collector entrypoint.sh should exist")
                .exists()
                .isRegularFile();
    }

    /**
     * 测试JobManager entrypoint脚本有正确的shebang
     * 需求: 8.5
     */
    @Test
    void testJobManagerEntrypointHasShebang() throws IOException {
        Path entrypoint = DOCKER_DIR.resolve("jobmanager/entrypoint.sh");
        String content = Files.readString(entrypoint);
        
        assertThat(content)
                .as("Entrypoint should start with shebang")
                .startsWith("#!/");
    }

    /**
     * 测试TaskManager entrypoint脚本有正确的shebang
     * 需求: 8.5
     */
    @Test
    void testTaskManagerEntrypointHasShebang() throws IOException {
        Path entrypoint = DOCKER_DIR.resolve("taskmanager/entrypoint.sh");
        String content = Files.readString(entrypoint);
        
        assertThat(content)
                .as("Entrypoint should start with shebang")
                .startsWith("#!/");
    }

    /**
     * 测试CDC Collector entrypoint脚本有正确的shebang
     * 需求: 8.5
     */
    @Test
    void testCdcCollectorEntrypointHasShebang() throws IOException {
        Path entrypoint = DOCKER_DIR.resolve("cdc-collector/entrypoint.sh");
        String content = Files.readString(entrypoint);
        
        assertThat(content)
                .as("Entrypoint should start with shebang")
                .startsWith("#!/");
    }

    /**
     * 测试JobManager entrypoint支持环境变量配置
     * 需求: 8.6
     */
    @Test
    void testJobManagerEntrypointSupportsEnvVars() throws IOException {
        Path entrypoint = DOCKER_DIR.resolve("jobmanager/entrypoint.sh");
        String content = Files.readString(entrypoint);
        
        // 验证脚本读取环境变量
        assertThat(content)
                .as("Should read PARALLELISM_DEFAULT env var")
                .contains("PARALLELISM_DEFAULT");
        
        assertThat(content)
                .as("Should read CHECKPOINT_INTERVAL env var")
                .contains("CHECKPOINT_INTERVAL");
        
        // 验证脚本使用环境变量更新配置
        assertThat(content)
                .as("Should update flink-conf.yaml with env vars")
                .contains("flink-conf.yaml");
    }

    /**
     * 测试TaskManager entrypoint支持环境变量配置
     * 需求: 8.6
     */
    @Test
    void testTaskManagerEntrypointSupportsEnvVars() throws IOException {
        Path entrypoint = DOCKER_DIR.resolve("taskmanager/entrypoint.sh");
        String content = Files.readString(entrypoint);
        
        // 验证脚本读取环境变量
        assertThat(content)
                .as("Should read TASK_MANAGER_NUMBER_OF_TASK_SLOTS env var")
                .contains("TASK_MANAGER_NUMBER_OF_TASK_SLOTS");
        
        assertThat(content)
                .as("Should read TASK_MANAGER_HEAP_SIZE env var")
                .contains("TASK_MANAGER_HEAP_SIZE");
        
        // 验证脚本使用环境变量更新配置
        assertThat(content)
                .as("Should update flink-conf.yaml with env vars")
                .contains("flink-conf.yaml");
    }

    /**
     * 测试CDC Collector entrypoint支持环境变量配置
     * 需求: 8.6
     */
    @Test
    void testCdcCollectorEntrypointSupportsEnvVars() throws IOException {
        Path entrypoint = DOCKER_DIR.resolve("cdc-collector/entrypoint.sh");
        String content = Files.readString(entrypoint);
        
        // 验证脚本读取环境变量
        assertThat(content)
                .as("Should read DATABASE_HOST env var")
                .contains("DATABASE_HOST");
        
        assertThat(content)
                .as("Should read DATAHUB_ENDPOINT env var")
                .contains("DATAHUB_ENDPOINT");
        
        // 验证脚本使用环境变量
        assertThat(content)
                .as("Should use environment variables")
                .containsAnyOf("${DATABASE_HOST}", "$DATABASE_HOST");
    }

    /**
     * 测试entrypoint脚本包含错误处理
     * 需求: 8.5
     */
    @Test
    void testEntrypointsHaveErrorHandling() throws IOException {
        String[] entrypoints = {
            "jobmanager/entrypoint.sh",
            "taskmanager/entrypoint.sh",
            "cdc-collector/entrypoint.sh"
        };
        
        for (String entrypointPath : entrypoints) {
            Path entrypoint = DOCKER_DIR.resolve(entrypointPath);
            String content = Files.readString(entrypoint);
            
            // 验证脚本使用set -e或等效的错误处理
            assertThat(content)
                    .as("Entrypoint %s should have error handling", entrypointPath)
                    .containsAnyOf("set -e", "set -o errexit", "|| exit");
        }
    }

    /**
     * 测试entrypoint脚本包含日志输出
     * 需求: 8.5
     */
    @Test
    void testEntrypointsHaveLogging() throws IOException {
        String[] entrypoints = {
            "jobmanager/entrypoint.sh",
            "taskmanager/entrypoint.sh",
            "cdc-collector/entrypoint.sh"
        };
        
        for (String entrypointPath : entrypoints) {
            Path entrypoint = DOCKER_DIR.resolve(entrypointPath);
            String content = Files.readString(entrypoint);
            
            // 验证脚本包含日志输出
            assertThat(content)
                    .as("Entrypoint %s should have logging", entrypointPath)
                    .containsAnyOf("echo", "log", "print");
        }
    }

    /**
     * 测试JobManager entrypoint启动正确的进程
     * 需求: 8.5
     */
    @Test
    void testJobManagerEntrypointStartsCorrectProcess() throws IOException {
        Path entrypoint = DOCKER_DIR.resolve("jobmanager/entrypoint.sh");
        String content = Files.readString(entrypoint);
        
        // 验证启动JobManager进程
        assertThat(content)
                .as("Should start Flink JobManager")
                .containsAnyOf("jobmanager", "standalone-job", "StandaloneSessionClusterEntrypoint");
    }

    /**
     * 测试TaskManager entrypoint启动正确的进程
     * 需求: 8.5
     */
    @Test
    void testTaskManagerEntrypointStartsCorrectProcess() throws IOException {
        Path entrypoint = DOCKER_DIR.resolve("taskmanager/entrypoint.sh");
        String content = Files.readString(entrypoint);
        
        // 验证启动TaskManager进程
        assertThat(content)
                .as("Should start Flink TaskManager")
                .containsAnyOf("taskmanager", "TaskManagerRunner");
    }

    /**
     * 测试CDC Collector entrypoint启动正确的进程
     * 需求: 8.5
     */
    @Test
    void testCdcCollectorEntrypointStartsCorrectProcess() throws IOException {
        Path entrypoint = DOCKER_DIR.resolve("cdc-collector/entrypoint.sh");
        String content = Files.readString(entrypoint);
        
        // 验证启动Java应用
        assertThat(content)
                .as("Should start Java application")
                .contains("java");
        
        // 验证使用正确的JAR文件
        assertThat(content)
                .as("Should use realtime-data-pipeline JAR")
                .contains("realtime-data-pipeline");
    }

    /**
     * 测试entrypoint脚本包含初始化步骤
     * 需求: 8.5
     */
    @Test
    void testEntrypointsHaveInitializationSteps() throws IOException {
        String[] entrypoints = {
            "jobmanager/entrypoint.sh",
            "taskmanager/entrypoint.sh",
            "cdc-collector/entrypoint.sh"
        };
        
        for (String entrypointPath : entrypoints) {
            Path entrypoint = DOCKER_DIR.resolve(entrypointPath);
            String content = Files.readString(entrypoint);
            
            // 验证脚本包含初始化步骤（如创建目录、检查配置等）
            assertThat(content)
                    .as("Entrypoint %s should have initialization", entrypointPath)
                    .containsAnyOf("mkdir", "config", "init", "setup");
        }
    }

    /**
     * 测试entrypoint脚本使用exec启动主进程
     * 需求: 8.5
     */
    @Test
    void testEntrypointsUseExec() throws IOException {
        String[] entrypoints = {
            "jobmanager/entrypoint.sh",
            "taskmanager/entrypoint.sh",
            "cdc-collector/entrypoint.sh"
        };
        
        for (String entrypointPath : entrypoints) {
            Path entrypoint = DOCKER_DIR.resolve(entrypointPath);
            String content = Files.readString(entrypoint);
            
            // 验证使用exec启动主进程（确保信号正确传递）
            assertThat(content)
                    .as("Entrypoint %s should use exec", entrypointPath)
                    .contains("exec");
        }
    }

    /**
     * 测试Flink配置文件模板存在
     * 需求: 8.5, 8.6
     */
    @Test
    void testFlinkConfigTemplatesExist() {
        Path jobManagerConfig = DOCKER_DIR.resolve("jobmanager/flink-conf.yaml");
        Path taskManagerConfig = DOCKER_DIR.resolve("taskmanager/flink-conf.yaml");
        
        assertThat(jobManagerConfig)
                .as("JobManager flink-conf.yaml should exist")
                .exists()
                .isRegularFile();
        
        assertThat(taskManagerConfig)
                .as("TaskManager flink-conf.yaml should exist")
                .exists()
                .isRegularFile();
    }

    /**
     * 测试CDC Collector配置文件模板存在
     * 需求: 8.5, 8.6
     */
    @Test
    void testCdcCollectorConfigTemplateExists() {
        Path config = DOCKER_DIR.resolve("cdc-collector/application.yml");
        
        assertThat(config)
                .as("CDC Collector application.yml should exist")
                .exists()
                .isRegularFile();
    }

    /**
     * 测试Flink配置文件包含必要的配置项
     * 需求: 8.6
     */
    @Test
    void testFlinkConfigContainsRequiredSettings() throws IOException {
        Path jobManagerConfig = DOCKER_DIR.resolve("jobmanager/flink-conf.yaml");
        String content = Files.readString(jobManagerConfig);
        
        // 验证包含基本配置
        assertThat(content)
                .as("Should configure jobmanager.rpc.address")
                .contains("jobmanager.rpc.address");
        
        assertThat(content)
                .as("Should configure parallelism.default")
                .contains("parallelism.default");
        
        // 验证包含checkpoint配置
        assertThat(content)
                .as("Should configure checkpoint settings")
                .containsAnyOf("execution.checkpointing", "state.backend");
    }

    /**
     * 测试Log4j配置文件存在
     * 需求: 8.5
     */
    @Test
    void testLog4jConfigFilesExist() {
        Path jobManagerLog4j = DOCKER_DIR.resolve("jobmanager/log4j.properties");
        Path taskManagerLog4j = DOCKER_DIR.resolve("taskmanager/log4j.properties");
        Path cdcCollectorLog4j = DOCKER_DIR.resolve("cdc-collector/log4j2.xml");
        
        assertThat(jobManagerLog4j)
                .as("JobManager log4j.properties should exist")
                .exists()
                .isRegularFile();
        
        assertThat(taskManagerLog4j)
                .as("TaskManager log4j.properties should exist")
                .exists()
                .isRegularFile();
        
        assertThat(cdcCollectorLog4j)
                .as("CDC Collector log4j2.xml should exist")
                .exists()
                .isRegularFile();
    }

    /**
     * 测试entrypoint脚本包含等待依赖服务的逻辑
     * 需求: 8.5
     */
    @Test
    void testEntrypointsWaitForDependencies() throws IOException {
        // TaskManager应该等待JobManager
        Path taskManagerEntrypoint = DOCKER_DIR.resolve("taskmanager/entrypoint.sh");
        String taskManagerContent = Files.readString(taskManagerEntrypoint);
        
        assertThat(taskManagerContent)
                .as("TaskManager should wait for JobManager")
                .containsAnyOf("wait", "nc ", "netcat", "until", "while");
        
        // CDC Collector应该等待数据库和DataHub
        Path cdcCollectorEntrypoint = DOCKER_DIR.resolve("cdc-collector/entrypoint.sh");
        String cdcCollectorContent = Files.readString(cdcCollectorEntrypoint);
        
        assertThat(cdcCollectorContent)
                .as("CDC Collector should wait for dependencies")
                .containsAnyOf("wait", "nc ", "netcat", "until", "while");
    }
}
