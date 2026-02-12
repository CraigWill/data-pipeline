# Docker容器化测试指南

## 概述

本文档说明如何运行容器化的基于属性的测试。这些测试验证Docker容器的启动时效性、环境变量配置和自动重启功能。

## 测试类型

### 1. 结构测试（无需Docker）

验证测试类的结构和方法签名，不需要Docker环境。

```bash
# 运行结构测试
mvn test -Dtest=ContainerPropertyTestStructureTest
```

这些测试总是可以运行，用于验证测试代码本身的正确性。

### 2. 属性测试（需要Docker）

实际运行容器并验证属性，需要Docker环境。

```bash
# 运行所有容器属性测试
mvn test -Dtest=ContainerPropertyTest

# 运行特定属性测试
mvn test -Dtest=ContainerPropertyTest#property33_containerStartupTimeliness
```

## 前置条件

### 系统要求

1. **Docker Engine 20.10+**
   ```bash
   docker --version
   ```

2. **Docker Compose 2.0+**
   ```bash
   docker-compose --version
   ```

3. **系统资源**
   - 至少4GB可用内存
   - 至少10GB可用磁盘空间
   - Docker daemon正在运行

### 构建Docker镜像

在运行测试前，需要先构建Docker镜像：

```bash
# 构建所有镜像
docker-compose build

# 或者单独构建
docker-compose build jobmanager
docker-compose build taskmanager
docker-compose build cdc-collector
```

## 运行测试

### 方式1: 本地开发环境

如果你有Docker环境，可以直接运行测试：

```bash
# 1. 确保Docker正在运行
docker ps

# 2. 构建镜像
docker-compose build

# 3. 运行测试
mvn test -Dtest=ContainerPropertyTest

# 4. 查看测试结果
cat target/surefire-reports/com.realtime.pipeline.docker.ContainerPropertyTest.txt
```

### 方式2: CI/CD环境

在CI/CD管道中，可能需要跳过Docker测试：

```bash
# 跳过Docker测试
mvn test -DskipDockerTests=true

# 或者只运行非Docker测试
mvn test -Dtest='!ContainerPropertyTest'
```

### 方式3: 手动测试

如果自动化测试有问题，可以手动验证：

```bash
# 1. 启动容器
docker-compose up -d jobmanager

# 2. 检查启动时间
time docker-compose up -d jobmanager
# 应该在60秒内完成

# 3. 检查健康状态
docker-compose ps
# 应该显示 (healthy)

# 4. 测试环境变量
docker exec flink-jobmanager printenv PARALLELISM_DEFAULT

# 5. 测试自动重启
docker kill flink-jobmanager
# 等待2分钟
docker-compose ps
# 应该显示容器已重启

# 6. 清理
docker-compose down
```

## 测试属性说明

### 属性33: 容器启动时效性

**需求**: 8.5  
**验证**: 容器在60秒内启动并通过健康检查

**测试步骤**:
1. 启动容器
2. 记录启动时间
3. 等待健康检查通过
4. 验证时间不超过60秒

**预期结果**:
- 容器状态: Up (healthy)
- 启动时间: ≤ 60秒

### 属性34: 环境变量配置

**需求**: 8.6  
**验证**: 环境变量正确应用到容器配置

**测试步骤**:
1. 生成随机环境变量
2. 创建docker-compose override
3. 启动容器
4. 验证环境变量在容器中可见
5. 验证配置文件反映环境变量

**测试的环境变量**:
- `PARALLELISM_DEFAULT`: 1-16
- `CHECKPOINT_INTERVAL`: 60000-600000ms
- `STATE_BACKEND`: hashmap/rocksdb
- `JOB_MANAGER_HEAP_SIZE`: 512-2048m

**预期结果**:
- 环境变量在容器中可见
- Flink配置文件包含正确的值

### 属性18: 容器自动重启

**需求**: 4.5  
**验证**: 容器崩溃后2分钟内自动重启

**测试步骤**:
1. 启动容器
2. 记录容器ID
3. 强制停止容器（模拟崩溃）
4. 等待容器重启
5. 验证重启时间不超过2分钟
6. 验证重启计数增加

**预期结果**:
- 容器自动重启
- 重启时间: ≤ 2分钟
- 重启计数: > 0

## 故障排查

### 问题1: Docker不可用

**症状**:
```
Docker not available, skipping test
```

**解决方案**:
```bash
# 检查Docker状态
systemctl status docker  # Linux
open -a Docker  # macOS

# 检查Docker权限
docker ps
# 如果失败，添加用户到docker组
sudo usermod -aG docker $USER
```

### 问题2: 容器启动超时

**症状**:
```
Container jobmanager should become healthy
Expected: true
Actual: false
```

**解决方案**:
```bash
# 检查容器日志
docker-compose logs jobmanager

# 检查资源使用
docker stats

# 手动启动测试
docker-compose up jobmanager
```

### 问题3: 端口冲突

**症状**:
```
Error starting userland proxy: bind: address already in use
```

**解决方案**:
```bash
# 查找占用端口的进程
lsof -i :8081  # Linux/macOS
netstat -ano | findstr :8081  # Windows

# 停止冲突的容器
docker-compose down

# 或修改端口映射
# 编辑 docker-compose.yml
```

### 问题4: 镜像未构建

**症状**:
```
Error: No such image: realtime-data-pipeline-jobmanager
```

**解决方案**:
```bash
# 构建所有镜像
docker-compose build

# 验证镜像存在
docker images | grep realtime-data-pipeline
```

### 问题5: 测试超时

**症状**:
测试运行很长时间没有结束

**解决方案**:
```bash
# 减少测试迭代次数
# 编辑 ContainerPropertyTest.java
# 修改 @Property(tries = 10) 为更小的值

# 或者增加超时时间
mvn test -Dtest=ContainerPropertyTest -Dsurefire.timeout=600
```

## 性能优化

### 1. 预构建镜像

```bash
# 在运行测试前构建镜像
docker-compose build --parallel
```

### 2. 使用本地缓存

```bash
# 保留镜像缓存
docker-compose build --no-cache=false
```

### 3. 并行测试

```bash
# 并行运行测试（谨慎使用，可能导致端口冲突）
mvn test -Dtest=ContainerPropertyTest -DforkCount=2
```

### 4. 减少迭代次数

修改测试代码中的`@Property(tries = N)`参数。

## 测试报告

### 查看测试结果

```bash
# Surefire报告
cat target/surefire-reports/com.realtime.pipeline.docker.ContainerPropertyTest.txt

# HTML报告
open target/surefire-reports/com.realtime.pipeline.docker.ContainerPropertyTest.html
```

### 测试指标

- **总测试数**: 35 (3个属性 × 10-15次迭代)
- **预期执行时间**: 30-60分钟
- **成功率**: 应该100%通过

## 最佳实践

### 1. 测试前清理

```bash
# 停止所有容器
docker-compose down -v

# 清理悬空镜像
docker image prune -f
```

### 2. 测试后清理

```bash
# 停止测试容器
docker-compose down

# 清理测试数据
docker volume prune -f
```

### 3. 隔离测试环境

```bash
# 使用专用的docker-compose项目名
COMPOSE_PROJECT_NAME=test-pipeline docker-compose up -d
```

### 4. 监控资源使用

```bash
# 实时监控
docker stats

# 检查磁盘使用
docker system df
```

## CI/CD集成示例

### GitHub Actions

```yaml
name: Docker Tests

on: [push, pull_request]

jobs:
  docker-tests:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v2
      
      - name: Set up JDK 11
        uses: actions/setup-java@v2
        with:
          java-version: '11'
          
      - name: Build Docker images
        run: docker-compose build
        
      - name: Run Docker tests
        run: mvn test -Dtest=ContainerPropertyTest
        timeout-minutes: 60
        
      - name: Cleanup
        if: always()
        run: docker-compose down -v
```

### GitLab CI

```yaml
docker-tests:
  stage: test
  image: docker:latest
  services:
    - docker:dind
  before_script:
    - apk add --no-cache docker-compose maven openjdk11
  script:
    - docker-compose build
    - mvn test -Dtest=ContainerPropertyTest
  after_script:
    - docker-compose down -v
  timeout: 1h
```

## 相关文档

- [Task 14.5 详细文档](../docs/TASK_14.5_CONTAINER_PROPERTY_TESTS.md)
- [Docker Compose配置](../docker-compose.yml)
- [环境变量配置](../.env.example)
- [健康检查脚本](./test-health-checks.sh)

## 总结

容器化属性测试确保了：
1. ✅ 容器在60秒内启动
2. ✅ 环境变量正确配置
3. ✅ 容器自动重启功能正常

这些测试为生产环境部署提供了信心，确保容器化部署满足所有需求。
