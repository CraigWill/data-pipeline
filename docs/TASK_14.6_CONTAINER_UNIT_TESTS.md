# Task 14.6: 容器化单元测试

## 概述

本任务实现了容器化部署的单元测试，验证Docker镜像构建、容器启动和初始化、环境变量配置和健康检查配置的正确性。

## 实现的测试类

### 1. DockerImageBuildTest (14个测试)

测试Docker镜像构建的正确性。

**测试内容:**
- Dockerfile文件存在性验证
- Dockerfile语法正确性验证
- 必要依赖包含验证
- 健康检查配置验证
- docker-compose.yml配置验证
- .env.example文件验证

**关键测试方法:**
- `testJobManagerDockerfileExists()` - 验证JobManager Dockerfile存在
- `testTaskManagerDockerfileExists()` - 验证TaskManager Dockerfile存在
- `testCdcCollectorDockerfileExists()` - 验证CDC Collector Dockerfile存在
- `testJobManagerDockerfileContainsDependencies()` - 验证JobManager依赖完整
- `testTaskManagerDockerfileContainsDependencies()` - 验证TaskManager依赖完整
- `testCdcCollectorDockerfileContainsDependencies()` - 验证CDC Collector依赖完整
- `testAllDockerfilesHaveHealthCheck()` - 验证所有Dockerfile都有健康检查
- `testHealthCheckStartPeriodMeetsRequirement()` - 验证健康检查启动时间满足60秒要求
- `testAllDockerfilesHaveCustomEntrypoint()` - 验证所有Dockerfile都有自定义entrypoint
- `testDockerComposeFileExists()` - 验证docker-compose.yml存在
- `testDockerComposeContainsAllServices()` - 验证docker-compose包含所有服务
- `testDockerComposeHasRestartPolicy()` - 验证docker-compose配置了重启策略
- `testEnvExampleFileExists()` - 验证.env.example存在
- `testEnvExampleContainsRequiredVariables()` - 验证.env.example包含必要变量

**验证需求:** 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8, 4.5

### 2. ContainerStartupTest (21个测试)

测试容器启动和初始化脚本的正确性。

**测试内容:**
- Entrypoint脚本存在性验证
- Shebang正确性验证
- 环境变量支持验证
- 错误处理验证
- 日志输出验证
- 进程启动验证
- 初始化步骤验证
- 配置文件验证
- 依赖等待逻辑验证

**关键测试方法:**
- `testJobManagerEntrypointExists()` - 验证JobManager entrypoint存在
- `testTaskManagerEntrypointExists()` - 验证TaskManager entrypoint存在
- `testCdcCollectorEntrypointExists()` - 验证CDC Collector entrypoint存在
- `testJobManagerEntrypointSupportsEnvVars()` - 验证JobManager支持环境变量
- `testTaskManagerEntrypointSupportsEnvVars()` - 验证TaskManager支持环境变量
- `testCdcCollectorEntrypointSupportsEnvVars()` - 验证CDC Collector支持环境变量
- `testEntrypointsHaveErrorHandling()` - 验证entrypoint有错误处理
- `testEntrypointsHaveLogging()` - 验证entrypoint有日志输出
- `testJobManagerEntrypointStartsCorrectProcess()` - 验证JobManager启动正确进程
- `testTaskManagerEntrypointStartsCorrectProcess()` - 验证TaskManager启动正确进程
- `testCdcCollectorEntrypointStartsCorrectProcess()` - 验证CDC Collector启动正确进程
- `testEntrypointsHaveInitializationSteps()` - 验证entrypoint有初始化步骤
- `testEntrypointsUseExec()` - 验证entrypoint使用exec启动主进程
- `testFlinkConfigTemplatesExist()` - 验证Flink配置模板存在
- `testCdcCollectorConfigTemplateExists()` - 验证CDC Collector配置模板存在
- `testFlinkConfigContainsRequiredSettings()` - 验证Flink配置包含必要设置
- `testLog4jConfigFilesExist()` - 验证Log4j配置文件存在
- `testEntrypointsWaitForDependencies()` - 验证entrypoint等待依赖服务

**验证需求:** 8.5, 8.6

### 3. EnvironmentVariableConfigTest (19个测试)

测试环境变量配置的正确性。

**测试内容:**
- 环境变量定义完整性验证
- 环境变量默认值合理性验证
- 环境变量格式正确性验证
- 环境变量使用验证
- 环境变量命名规范验证
- 环境变量注释验证
- 敏感信息处理验证

**关键测试方法:**
- `testEnvExampleContainsFlinkVariables()` - 验证包含Flink相关变量
- `testEnvExampleContainsDatabaseVariables()` - 验证包含数据库相关变量
- `testEnvExampleContainsDataHubVariables()` - 验证包含DataHub相关变量
- `testEnvExampleContainsMonitoringVariables()` - 验证包含监控相关变量
- `testEnvExampleHasReasonableDefaults()` - 验证默认值合理
- `testDockerComposeUsesEnvVariables()` - 验证docker-compose使用环境变量
- `testEntrypointReadsEnvVariables()` - 验证entrypoint读取环境变量
- `testJobManagerEntrypointUsesFlinkEnvVars()` - 验证JobManager使用Flink变量
- `testTaskManagerEntrypointUsesTaskManagerEnvVars()` - 验证TaskManager使用TaskManager变量
- `testCdcCollectorEntrypointUsesDatabaseAndDataHubEnvVars()` - 验证CDC Collector使用数据库和DataHub变量
- `testEnvVariableNamingConvention()` - 验证环境变量命名规范
- `testEnvVariablesHaveComments()` - 验证环境变量有注释
- `testSensitiveInfoUsesPlaceholders()` - 验证敏感信息使用占位符
- `testEnvVariablesAreGrouped()` - 验证环境变量分组清晰
- `testBooleanEnvVariablesUseStandardValues()` - 验证布尔变量使用标准值

**验证需求:** 8.6

### 4. HealthCheckConfigTest (39个测试)

测试健康检查配置的正确性。

**测试内容:**
- 健康检查命令正确性验证
- 健康检查参数合理性验证
- 健康检查端点验证
- 健康检查超时配置验证
- 健康检查工具验证

**关键测试方法:**
- `testDockerfileHasHealthCheck()` - 验证Dockerfile有健康检查（参数化测试）
- `testHealthCheckHasInterval()` - 验证健康检查有interval参数（参数化测试）
- `testHealthCheckHasTimeout()` - 验证健康检查有timeout参数（参数化测试）
- `testHealthCheckHasStartPeriod()` - 验证健康检查有start-period参数（参数化测试）
- `testHealthCheckHasRetries()` - 验证健康检查有retries参数（参数化测试）
- `testJobManagerHealthCheckUsesHttpEndpoint()` - 验证JobManager使用HTTP端点
- `testTaskManagerHealthCheckUsesProcessCheck()` - 验证TaskManager使用进程检查
- `testCdcCollectorHealthCheckUsesHttpEndpoint()` - 验证CDC Collector使用HTTP端点
- `testHealthCheckCommandHasExitCode()` - 验证健康检查命令有退出码（参数化测试）
- `testDockerComposeDoesNotOverrideHealthCheck()` - 验证docker-compose不覆盖健康检查
- `testHealthCheckUsesLocalhost()` - 验证健康检查使用localhost（参数化测试）
- `testHealthCheckIntervalLessThanStartPeriod()` - 验证interval小于start-period（参数化测试）
- `testHealthCheckTimeoutLessThanInterval()` - 验证timeout小于interval（参数化测试）
- `testHealthCheckCommandFormat()` - 验证健康检查命令格式（参数化测试）
- `testHealthCheckUsesAppropriateTools()` - 验证健康检查使用合适工具（参数化测试）

**验证需求:** 8.8, 8.5

## 测试统计

- **总测试数:** 93
- **测试类数:** 4
- **参数化测试:** 15个测试方法使用了参数化测试
- **测试覆盖率:** 覆盖了所有容器化相关的需求

## 测试文件位置

```
src/test/java/com/realtime/pipeline/docker/
├── DockerImageBuildTest.java          # Docker镜像构建测试
├── ContainerStartupTest.java          # 容器启动测试
├── EnvironmentVariableConfigTest.java # 环境变量配置测试
└── HealthCheckConfigTest.java         # 健康检查配置测试
```

## 运行测试

### 运行所有容器化单元测试

```bash
mvn test -Dtest="DockerImageBuildTest,ContainerStartupTest,EnvironmentVariableConfigTest,HealthCheckConfigTest"
```

### 运行特定测试类

```bash
# Docker镜像构建测试
mvn test -Dtest=DockerImageBuildTest

# 容器启动测试
mvn test -Dtest=ContainerStartupTest

# 环境变量配置测试
mvn test -Dtest=EnvironmentVariableConfigTest

# 健康检查配置测试
mvn test -Dtest=HealthCheckConfigTest
```

### 运行特定测试方法

```bash
# 测试特定Dockerfile
mvn test -Dtest=DockerImageBuildTest#testJobManagerDockerfileExists

# 测试特定entrypoint
mvn test -Dtest=ContainerStartupTest#testJobManagerEntrypointSupportsEnvVars

# 测试特定环境变量
mvn test -Dtest=EnvironmentVariableConfigTest#testEnvExampleContainsFlinkVariables

# 测试特定健康检查
mvn test -Dtest=HealthCheckConfigTest#testJobManagerHealthCheckUsesHttpEndpoint
```

## 测试特点

### 1. 文件系统测试

所有测试都是基于文件系统的单元测试，不需要Docker环境：
- 验证文件存在性
- 验证文件内容正确性
- 验证配置格式正确性

### 2. 快速执行

- 所有93个测试在不到1秒内完成
- 不需要启动容器
- 不需要网络连接
- 适合CI/CD集成

### 3. 全面覆盖

测试覆盖了容器化部署的所有关键方面：
- Docker镜像构建
- 容器启动和初始化
- 环境变量配置
- 健康检查配置

### 4. 参数化测试

使用JUnit 5的参数化测试功能，减少代码重复：
- `@ParameterizedTest` - 参数化测试
- `@CsvSource` - CSV数据源
- `@ValueSource` - 值数据源

### 5. 清晰的断言

使用AssertJ提供清晰的断言消息：
```java
assertThat(content)
    .as("JobManager should use curl for health check")
    .contains("curl");
```

## 测试覆盖的需求

### 需求 8.4: Docker镜像包含所有必要依赖
- ✅ 验证Dockerfile安装必要的系统依赖
- ✅ 验证Dockerfile复制应用JAR包
- ✅ 验证Dockerfile创建必要的目录
- ✅ 验证Dockerfile暴露必要的端口

### 需求 8.5: 容器在60秒内完成初始化
- ✅ 验证健康检查start-period至少60秒
- ✅ 验证entrypoint脚本包含初始化逻辑
- ✅ 验证entrypoint脚本有错误处理
- ✅ 验证entrypoint脚本有日志输出

### 需求 8.6: 支持通过环境变量配置系统参数
- ✅ 验证.env.example包含所有必要的环境变量
- ✅ 验证entrypoint脚本读取环境变量
- ✅ 验证entrypoint脚本使用环境变量更新配置
- ✅ 验证docker-compose.yml使用环境变量

### 需求 8.8: 提供容器健康检查配置
- ✅ 验证所有Dockerfile都配置了HEALTHCHECK
- ✅ 验证健康检查参数合理（interval, timeout, start-period, retries）
- ✅ 验证健康检查命令正确
- ✅ 验证健康检查使用合适的工具

## 测试最佳实践

### 1. 测试隔离

每个测试方法都是独立的，不依赖其他测试的状态。

### 2. 清晰的测试名称

测试方法名称清楚地描述了测试的内容：
```java
testJobManagerDockerfileContainsDependencies()
testHealthCheckStartPeriodMeetsRequirement()
```

### 3. 详细的断言消息

每个断言都有清晰的消息，便于定位问题：
```java
assertThat(content)
    .as("JobManager Dockerfile should have HEALTHCHECK")
    .contains("HEALTHCHECK");
```

### 4. 辅助方法

提供辅助方法简化测试代码：
```java
private Map<String, String> parseEnvFile(String content)
```

### 5. 常量定义

使用常量定义路径和配置：
```java
private static final Path DOCKER_DIR = Paths.get(PROJECT_ROOT, "docker");
```

## 与属性测试的关系

这些单元测试与Task 14.5的属性测试是互补的：

**单元测试（Task 14.6）:**
- 验证配置文件的正确性
- 验证脚本的语法正确性
- 快速执行，不需要Docker环境
- 适合开发阶段的快速反馈

**属性测试（Task 14.5）:**
- 验证容器的实际运行行为
- 验证容器启动时效性
- 验证环境变量实际生效
- 验证容器自动重启
- 需要Docker环境
- 执行时间较长

## 故障排查

### 问题1: 文件不存在

**症状:**
```
Expecting path to exist but was not found
```

**解决方案:**
1. 确保在项目根目录运行测试
2. 确保所有Docker文件已创建
3. 检查文件路径是否正确

### 问题2: 内容不匹配

**症状:**
```
Expecting actual to contain "HEALTHCHECK"
```

**解决方案:**
1. 检查Dockerfile内容是否正确
2. 检查环境变量名称是否匹配
3. 检查配置文件格式是否正确

### 问题3: 正则表达式不匹配

**症状:**
```
Expecting actual to contain pattern "HEALTHCHECK.*CMD"
```

**解决方案:**
1. 检查正则表达式是否正确
2. 考虑多行格式的情况
3. 使用Pattern.MULTILINE标志

## 总结

Task 14.6成功实现了93个容器化单元测试，全面验证了：

1. ✅ Docker镜像构建的正确性
2. ✅ 容器启动和初始化的正确性
3. ✅ 环境变量配置的正确性
4. ✅ 健康检查配置的正确性

这些测试为容器化部署提供了坚实的质量保证，确保所有配置文件和脚本都符合需求规范。测试执行快速，适合集成到CI/CD流程中，为开发团队提供即时反馈。

与Task 14.5的属性测试结合，形成了完整的容器化测试策略：单元测试验证配置正确性，属性测试验证运行时行为，共同确保容器化部署的质量。
