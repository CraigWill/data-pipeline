# Task 14.5: 容器化基于属性的测试

## 概述

本任务实现了容器化部署的基于属性的测试，验证Docker容器的启动时效性、环境变量配置和自动重启功能。

## 实现的属性

### 属性 33: 容器启动时效性
**验证需求: 8.5**

对于任何容器启动操作，系统应该在60秒内完成初始化并进入就绪状态。

**测试策略:**
- 测试所有容器类型（JobManager、TaskManager、CDC Collector）
- 记录启动时间并验证不超过60秒
- 验证容器通过健康检查
- 验证容器处于运行状态

**实现细节:**
```java
@Property(tries = 10)
void property33_containerStartupTimeliness(
        @ForAll("containerTypes") String containerType)
```

### 属性 34: 环境变量配置
**验证需求: 8.6**

对于任何通过环境变量设置的系统参数，该参数应该在系统运行时生效。

**测试策略:**
- 生成随机的有效环境变量配置
- 使用docker-compose override文件注入环境变量
- 启动容器并验证环境变量在容器中可见
- 验证配置文件反映了环境变量的值

**测试的环境变量:**
- `PARALLELISM_DEFAULT`: Flink默认并行度
- `CHECKPOINT_INTERVAL`: Checkpoint间隔
- `STATE_BACKEND`: 状态后端类型
- `JOB_MANAGER_HEAP_SIZE`: JobManager堆内存大小

**实现细节:**
```java
@Property(tries = 15)
void property34_environmentVariableConfiguration(
        @ForAll("validEnvironmentVariables") Map<String, String> envVars)
```

### 属性 18: 容器自动重启
**验证需求: 4.5**

对于任何容器崩溃事件，系统应该在2分钟内自动重启该容器。

**测试策略:**
- 启动容器并等待健康
- 记录初始容器ID
- 使用`docker kill`模拟容器崩溃
- 验证容器在2分钟内自动重启
- 验证重启计数增加

**实现细节:**
```java
@Property(tries = 10)
void property18_containerAutoRestart(
        @ForAll("containerTypes") String containerType)
```

## 测试文件

### 主测试文件
- `src/test/java/com/realtime/pipeline/docker/ContainerPropertyTest.java`

### 测试配置
- 使用jqwik进行基于属性的测试
- 每个属性测试运行10-15次迭代
- 支持通过系统属性跳过测试

## 运行测试

### 前置条件

1. **Docker环境:**
   ```bash
   # 检查Docker版本
   docker --version  # 需要 20.10+
   docker-compose --version  # 需要 2.0+
   ```

2. **系统资源:**
   - 至少4GB可用内存
   - 至少10GB可用磁盘空间
   - Docker daemon正在运行

3. **项目构建:**
   ```bash
   mvn clean compile test-compile
   ```

### 运行所有容器测试

```bash
# 运行所有容器属性测试
mvn test -Dtest=ContainerPropertyTest

# 运行特定属性测试
mvn test -Dtest=ContainerPropertyTest#property33_containerStartupTimeliness
mvn test -Dtest=ContainerPropertyTest#property34_environmentVariableConfiguration
mvn test -Dtest=ContainerPropertyTest#property18_containerAutoRestart
```

### 跳过Docker测试

如果Docker不可用或在CI/CD环境中不需要运行这些测试：

```bash
# 方式1: 使用系统属性
mvn test -DskipDockerTests=true

# 方式2: 跳过所有测试
mvn test -DskipTests

# 方式3: 只运行非Docker测试
mvn test -Dtest='!ContainerPropertyTest'
```

### CI/CD集成

在CI/CD管道中，可以根据环境决定是否运行Docker测试：

```yaml
# GitHub Actions示例
- name: Run Docker Tests
  if: runner.os == 'Linux'
  run: mvn test -Dtest=ContainerPropertyTest
  
# 或者跳过
- name: Run Tests (Skip Docker)
  run: mvn test -DskipDockerTests=true
```

## 测试行为

### 自动跳过机制

测试会在以下情况自动跳过：
1. Docker不可用（`docker version`失败）
2. 设置了`-DskipDockerTests=true`系统属性
3. docker-compose不可用

跳过时会输出：
```
Docker not available, skipping test
```

### 测试清理

每个测试都会在finally块中清理容器：
```java
finally {
    cleanupContainer(serviceName);
}
```

这确保即使测试失败，容器也会被停止和删除。

### 超时设置

- 容器启动超时: 70秒
- 容器重启超时: 3分钟
- 命令执行超时: 根据操作类型动态设置

## 测试数据生成

### 容器类型生成器
```java
@Provide
Arbitrary<String> containerTypes() {
    return Arbitraries.of("jobmanager", "taskmanager", "cdc-collector");
}
```

### 环境变量生成器
```java
@Provide
Arbitrary<Map<String, String>> validEnvironmentVariables() {
    return Combinators.combine(
        Arbitraries.integers().between(1, 16),  // PARALLELISM_DEFAULT
        Arbitraries.integers().between(60000, 600000),  // CHECKPOINT_INTERVAL
        Arbitraries.of("hashmap", "rocksdb"),  // STATE_BACKEND
        Arbitraries.integers().between(512, 2048)  // JOB_MANAGER_HEAP_SIZE
    ).as((parallelism, checkpointInterval, stateBackend, heapSize) -> {
        // 生成环境变量Map
    });
}
```

## 故障排查

### 问题1: Docker不可用

**症状:**
```
Docker not available, skipping test
```

**解决方案:**
1. 确保Docker daemon正在运行
2. 检查Docker权限（可能需要sudo或将用户添加到docker组）
3. 验证Docker版本兼容性

### 问题2: 容器启动超时

**症状:**
```
Container jobmanager should become healthy
Expected: true
Actual: false
```

**解决方案:**
1. 检查系统资源（内存、CPU）
2. 增加启动超时时间
3. 检查Docker日志：`docker-compose logs jobmanager`
4. 验证镜像是否正确构建

### 问题3: 端口冲突

**症状:**
```
Error starting userland proxy: listen tcp 0.0.0.0:8081: bind: address already in use
```

**解决方案:**
1. 停止占用端口的服务
2. 修改docker-compose.yml中的端口映射
3. 清理旧容器：`docker-compose down -v`

### 问题4: 环境变量未生效

**症状:**
```
Environment variable PARALLELISM_DEFAULT should have correct value
Expected: 8
Actual: 4
```

**解决方案:**
1. 检查docker-compose override文件是否正确生成
2. 验证entrypoint.sh脚本是否正确读取环境变量
3. 检查容器内的环境变量：`docker exec flink-jobmanager printenv`

### 问题5: 容器未自动重启

**症状:**
```
Container should have restarted at least once
Expected: > 0
Actual: 0
```

**解决方案:**
1. 检查docker-compose.yml中的restart策略
2. 验证容器是否配置了`restart: unless-stopped`
3. 检查Docker daemon的重启策略设置

## 性能考虑

### 测试执行时间

- 单个容器启动测试: ~60-90秒
- 环境变量配置测试: ~70-100秒
- 容器重启测试: ~120-180秒
- 完整测试套件: ~30-60分钟（取决于迭代次数）

### 优化建议

1. **并行执行:**
   ```bash
   mvn test -Dtest=ContainerPropertyTest -DforkCount=2
   ```

2. **减少迭代次数:**
   修改`@Property(tries = N)`中的N值

3. **使用更快的状态后端:**
   在测试中优先使用hashmap而不是rocksdb

4. **预构建镜像:**
   在运行测试前先构建所有Docker镜像

## 最佳实践

### 1. 测试隔离

每个测试都应该：
- 使用唯一的容器名称或服务名称
- 在finally块中清理资源
- 不依赖其他测试的状态

### 2. 错误处理

```java
try {
    // 测试逻辑
} catch (Exception e) {
    fail("Test failed: " + e.getMessage());
} finally {
    // 清理资源
    cleanupContainer(serviceName);
}
```

### 3. 超时管理

为所有可能长时间运行的操作设置超时：
```java
boolean healthy = waitForContainerHealthy(serviceName, STARTUP_TIMEOUT);
```

### 4. 日志记录

在关键步骤添加日志输出：
```java
System.out.println("Starting container: " + serviceName);
System.out.println("Startup time: " + elapsedTime + "ms");
```

## 验证清单

运行测试前，确保：

- [ ] Docker daemon正在运行
- [ ] docker-compose已安装
- [ ] 有足够的系统资源
- [ ] 没有端口冲突
- [ ] Docker镜像已构建
- [ ] 项目已编译

运行测试后，验证：

- [ ] 所有属性测试通过
- [ ] 容器启动时间在60秒内
- [ ] 环境变量正确应用
- [ ] 容器能够自动重启
- [ ] 测试清理完成（无残留容器）

## 相关文档

- [Task 14.1: Dockerfiles](TASK_14.1_DOCKERFILES.md)
- [Task 14.2: 启动脚本](TASK_14.2_STARTUP_SCRIPTS.md)
- [Task 14.3: 健康检查配置](TASK_14.3_HEALTH_CHECK_CONFIG.md)
- [Task 14.4: Docker Compose配置](TASK_14.4_SUMMARY.md)
- [Docker Compose配置文件](../docker-compose.yml)
- [环境变量示例](../.env.example)

## 总结

本任务实现了三个关键的容器化属性测试：

1. **容器启动时效性**: 验证所有容器在60秒内启动
2. **环境变量配置**: 验证环境变量正确应用到容器配置
3. **容器自动重启**: 验证容器崩溃后2分钟内自动重启

这些测试确保了容器化部署满足需求8.5、8.6和4.5的要求，为生产环境部署提供了信心。

测试采用基于属性的测试方法，通过随机生成测试数据来验证系统在各种配置下的行为，提供了比传统单元测试更全面的覆盖。
