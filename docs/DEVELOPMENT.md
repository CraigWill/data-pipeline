# 实时数据管道系统开发文档

## 目录

- [项目概述](#项目概述)
- [代码结构说明](#代码结构说明)
- [开发环境搭建](#开发环境搭建)
- [测试指南](#测试指南)
- [贡献指南](#贡献指南)
- [开发工作流](#开发工作流)
- [常见开发任务](#常见开发任务)
- [调试技巧](#调试技巧)
- [性能优化](#性能优化)

## 项目概述

实时数据管道系统是一个基于Apache Flink的企业级流处理平台，用于从OceanBase数据库捕获变更数据（CDC），通过阿里云DataHub进行数据传输，使用Flink进行流处理，最终输出到文件系统。

### 技术栈

- **编程语言**: Java 11
- **构建工具**: Maven 3.6+
- **流处理引擎**: Apache Flink 1.18.0
- **CDC连接器**: Flink CDC Connector for OceanBase
- **消息队列**: 阿里云DataHub
- **日志框架**: Log4j2 2.20.0
- **测试框架**: JUnit 5.10.0, jqwik 1.8.0 (Property-Based Testing)
- **容器化**: Docker 20.10+, Docker Compose 2.0+
- **代码简化**: Lombok 1.18.30

### 核心特性

- 高可用性（99.9%可用性）
- 容错性（基于Checkpoint机制）
- 可扩展性（支持动态扩缩容）
- 数据一致性（至少一次和精确一次语义）
- 完整的监控和告警机制
- 容器化部署（60秒内启动）


## 代码结构说明

### 项目目录结构

```
realtime-data-pipeline/
├── src/
│   ├── main/
│   │   ├── java/com/realtime/pipeline/
│   │   │   ├── cdc/                    # CDC数据采集组件
│   │   │   ├── config/                 # 配置管理
│   │   │   ├── consistency/            # 数据一致性检测
│   │   │   ├── datahub/                # DataHub集成
│   │   │   ├── error/                  # 错误处理和死信队列
│   │   │   ├── flink/                  # Flink流处理核心
│   │   │   ├── job/                    # 作业管理
│   │   │   ├── model/                  # 数据模型
│   │   │   ├── monitoring/             # 监控和告警
│   │   │   ├── util/                   # 工具类
│   │   │   └── FlinkPipelineMain.java  # 主程序入口
│   │   └── resources/
│   │       ├── application.yml         # 配置文件模板
│   │       ├── application-ha-zookeeper.yml  # ZooKeeper HA配置
│   │       ├── application-ha-kubernetes.yml # Kubernetes HA配置
│   │       └── log4j2.xml              # 日志配置
│   └── test/
│       ├── java/                       # 测试代码
│       │   ├── *Test.java              # 单元测试
│       │   ├── *PropertyTest.java      # 基于属性的测试
│       │   └── integration/            # 集成测试
│       └── resources/
│           └── test-config-*.yml       # 测试配置文件
├── docker/                             # Docker镜像定义
│   ├── jobmanager/                     # Flink JobManager镜像
│   ├── taskmanager/                    # Flink TaskManager镜像
│   └── cdc-collector/                  # CDC采集器镜像
├── docs/                               # 文档
├── .kiro/specs/                        # 规格说明
├── pom.xml                             # Maven配置
├── docker-compose.yml                  # Docker Compose配置
├── .env.example                        # 环境变量模板
└── README.md                           # 项目说明
```


### 核心包说明

#### 1. `com.realtime.pipeline.cdc` - CDC数据采集

**职责**: 从OceanBase数据库捕获变更数据并发送到DataHub

**核心类**:
- `CDCCollector`: CDC采集器主类，管理数据库连接和变更捕获
- `CDCEventConverter`: 将数据库变更事件转换为标准ChangeEvent格式
- `ConnectionManager`: 管理数据库连接，支持自动重连
- `CDCPipeline`: CDC数据采集管道，整合采集和发送逻辑

**关键功能**:
- 捕获INSERT/UPDATE/DELETE操作（5秒内）
- 自动重连（30秒内）
- 失败重试（最多3次，间隔2秒）

#### 2. `com.realtime.pipeline.config` - 配置管理

**职责**: 管理系统配置，支持配置文件和环境变量

**核心类**:
- `PipelineConfig`: 顶层配置类，包含所有子系统配置
- `DatabaseConfig`: 数据库连接配置
- `DataHubConfig`: DataHub连接配置
- `FlinkConfig`: Flink执行环境配置
- `OutputConfig`: 文件输出配置
- `MonitoringConfig`: 监控和告警配置
- `HighAvailabilityConfig`: 高可用配置
- `ConfigLoader`: 配置加载器（在util包中）

**关键功能**:
- 从YAML文件加载配置
- 环境变量覆盖配置文件参数
- 配置参数验证
- 无效配置拒绝启动


#### 3. `com.realtime.pipeline.datahub` - DataHub集成

**职责**: 与阿里云DataHub服务集成，发送和消费数据

**核心类**:
- `DataHubSender`: 发送变更数据到DataHub
- `client/DataHubClient`: DataHub客户端接口
- `client/DefaultDataHubClient`: 默认DataHub客户端实现
- `client/RecordEntry`: DataHub记录条目
- `client/PutRecordsResult`: 批量写入结果
- `client/TopicInfo`: DataHub主题信息

**关键功能**:
- 发送变更数据到DataHub
- 重试机制（最多3次）
- 批量发送优化

#### 4. `com.realtime.pipeline.flink` - Flink流处理核心

**职责**: Flink流处理的核心实现

**子包结构**:

##### 4.1 `flink.source` - 数据源
- `DataHubSource`: 从DataHub消费数据的Flink Source

##### 4.2 `flink.processor` - 数据处理
- `EventProcessor`: 事件处理器，转换ChangeEvent到ProcessedEvent
- `EventProcessorWithDLQ`: 带死信队列的事件处理器
- `ConsistencyCheckFunction`: 数据一致性检查函数
- `ProcessedEventValidator`: 处理后事件验证器

##### 4.3 `flink.sink` - 数据输出
- `AbstractFileSink`: 文件Sink基类
- `JsonFileSink`: JSON格式输出（已实现为CsvFileSink）
- `ParquetFileSink`: Parquet格式输出
- `CsvFileSink`: CSV格式输出
- `FileSinkWithDLQ`: 带死信队列的文件Sink
- `IdempotentFileSink`: 幂等性文件Sink（精确一次语义）
- `ExactlyOnceSinkBuilder`: 精确一次Sink构建器

##### 4.4 `flink.checkpoint` - Checkpoint管理
- `CheckpointListener`: Checkpoint监听器接口
- `MetricsCheckpointListener`: 带指标收集的Checkpoint监听器

##### 4.5 `flink.recovery` - 故障恢复
- `FaultRecoveryManager`: 故障恢复管理器
- `RecoveryMonitor`: 恢复监控器

##### 4.6 `flink.ha` - 高可用
- `HighAvailabilityConfigurator`: 高可用配置器
- `JobManagerFailoverManager`: JobManager故障切换管理器
- `HighAvailabilityExample`: 高可用示例

##### 4.7 `flink.scaling` - 动态扩缩容
- `DynamicScalingManager`: 动态扩缩容管理器

##### 4.8 核心类
- `FlinkEnvironmentConfigurator`: Flink执行环境配置器
- `FlinkStreamProcessing`: Flink流处理主类（已整合到FlinkPipelineMain）


#### 5. `com.realtime.pipeline.error` - 错误处理

**职责**: 处理错误和管理死信队列

**核心类**:
- `DeadLetterQueue`: 死信队列接口
- `FileBasedDeadLetterQueue`: 基于文件的死信队列实现
- `DeadLetterRecordBuilder`: 死信记录构建器
- `DeadLetterReprocessor`: 死信队列重处理器

**关键功能**:
- 存储处理失败的记录
- 记录失败原因和上下文
- 支持从死信队列重新处理

#### 6. `com.realtime.pipeline.monitoring` - 监控和告警

**职责**: 收集系统指标，提供监控和告警功能

**核心类**:
- `MonitoringService`: 监控服务主类
- `MetricsReporter`: 指标报告器
- `AlertManager`: 告警管理器
- `HealthCheckServer`: 健康检查服务器

**关键功能**:
- 收集吞吐量、延迟、Checkpoint等指标
- 触发告警（延迟、Checkpoint失败率、负载）
- 提供健康检查端点（/health, /health/live, /health/ready）
- 暴露Prometheus metrics端点

#### 7. `com.realtime.pipeline.consistency` - 数据一致性

**职责**: 保证数据一致性，检测数据不一致

**核心类**:
- `ConsistencyValidator`: 一致性验证器
- `ConsistencyCheckExample`: 一致性检查示例

**关键功能**:
- 至少一次语义保证
- 精确一次语义支持（幂等性Sink）
- 数据不一致检测和日志记录

#### 8. `com.realtime.pipeline.job` - 作业管理

**职责**: 管理Flink作业的生命周期

**核心类**:
- `JobManager`: 作业管理器接口
- `FlinkJobManager`: Flink作业管理器实现
- `JobManagerExample`: 作业管理示例

**关键功能**:
- 作业启动、停止、重启
- Savepoint触发
- 作业状态查询


#### 9. `com.realtime.pipeline.model` - 数据模型

**职责**: 定义系统中使用的数据模型

**核心类**:
- `ChangeEvent`: 变更事件，表示从数据库捕获的CDC事件
- `ProcessedEvent`: 处理后事件，表示经过Flink处理后的数据
- `DeadLetterRecord`: 死信记录，表示处理失败的数据
- `CollectorMetrics`: 采集指标，记录CDC采集器的运行状态
- `JobStatus`: 作业状态，记录Flink作业的运行信息

**数据模型关系**:
```
OceanBase变更 → ChangeEvent → Flink处理 → ProcessedEvent → 文件输出
                     ↓ (失败)
                DeadLetterRecord → 死信队列
```

#### 10. `com.realtime.pipeline.util` - 工具类

**职责**: 提供通用工具类

**核心类**:
- `ConfigLoader`: 配置加载器，支持YAML和环境变量
- `IdGenerator`: ID生成器，生成唯一标识符
- `RetryUtil`: 重试工具，提供通用重试机制

#### 11. `com.realtime.pipeline.FlinkPipelineMain` - 主程序

**职责**: 系统主入口，整合所有组件

**关键功能**:
- 加载配置
- 初始化所有组件
- 构建Flink作业DAG
- 提交作业到Flink集群
- 处理优雅关闭

### 数据流架构

```
┌─────────────┐      ┌──────────────┐      ┌─────────────┐
│  OceanBase  │─CDC─>│ CDCCollector │─────>│   DataHub   │
│   Oracle    │      │              │      │   Topic     │
└─────────────┘      └──────────────┘      └─────────────┘
                                                   │
                                                   ▼
                                            ┌─────────────┐
                                            │ DataHubSource│
                                            │ (Flink)     │
                                            └─────────────┘
                                                   │
                                                   ▼
                                            ┌─────────────┐
                                            │EventProcessor│
                                            │             │
                                            └─────────────┘
                                                   │
                                    ┌──────────────┼──────────────┐
                                    ▼              ▼              ▼
                              ┌──────────┐  ┌──────────┐  ┌──────────┐
                              │ FileSink │  │ FileSink │  │   DLQ    │
                              │  (JSON)  │  │(Parquet) │  │  (失败)  │
                              └──────────┘  └──────────┘  └──────────┘
```


## 开发环境搭建

### 前置要求

#### 必需软件

1. **Java Development Kit (JDK) 11+**
   ```bash
   # 验证Java版本
   java -version
   # 应显示: java version "11.x.x" 或更高
   
   # 设置JAVA_HOME环境变量
   export JAVA_HOME=/path/to/jdk-11
   export PATH=$JAVA_HOME/bin:$PATH
   ```

2. **Apache Maven 3.6+**
   ```bash
   # 验证Maven版本
   mvn -version
   # 应显示: Apache Maven 3.6.x 或更高
   
   # 配置Maven镜像（可选，加速下载）
   vim ~/.m2/settings.xml
   ```

3. **Git**
   ```bash
   # 验证Git版本
   git --version
   ```

4. **Docker 20.10+** (用于容器化部署和测试)
   ```bash
   # 验证Docker版本
   docker --version
   docker-compose --version
   ```

#### 推荐软件

1. **IDE**: IntelliJ IDEA (推荐) 或 Eclipse
2. **数据库客户端**: DBeaver, DataGrip
3. **API测试工具**: Postman, curl
4. **容器管理**: Docker Desktop, Portainer

### 克隆项目

```bash
# 克隆仓库
git clone <repository-url>
cd realtime-data-pipeline

# 查看项目结构
tree -L 2 -I 'target|.git'
```

### 构建项目

#### 1. 编译项目

```bash
# 清理并编译
mvn clean compile

# 查看编译结果
ls -lh target/classes/
```

#### 2. 运行测试

```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=CDCCollectorTest

# 运行属性测试
mvn test -Dtest=*PropertyTest

# 跳过测试
mvn clean compile -DskipTests
```

#### 3. 打包应用

```bash
# 打包为JAR文件
mvn clean package

# 验证JAR文件
ls -lh target/realtime-data-pipeline-*.jar

# 查看JAR内容
jar tf target/realtime-data-pipeline-1.0.0-SNAPSHOT.jar | head -20
```


### IDE配置

#### IntelliJ IDEA配置

1. **导入项目**
   - File → Open → 选择项目根目录
   - 选择"Import project from external model" → Maven
   - 等待Maven依赖下载完成

2. **安装Lombok插件**
   - File → Settings → Plugins
   - 搜索"Lombok"并安装
   - 重启IDE

3. **启用Annotation Processing**
   - File → Settings → Build, Execution, Deployment → Compiler → Annotation Processors
   - 勾选"Enable annotation processing"

4. **配置Java SDK**
   - File → Project Structure → Project
   - 设置Project SDK为Java 11
   - 设置Project language level为11

5. **配置代码格式化**
   - File → Settings → Editor → Code Style → Java
   - 导入项目代码风格配置（如果有）

6. **配置运行配置**
   ```
   Main class: com.realtime.pipeline.FlinkPipelineMain
   VM options: -Xmx2g
   Program arguments: --config src/main/resources/application.yml
   Working directory: $PROJECT_DIR$
   ```

#### Eclipse配置

1. **导入Maven项目**
   - File → Import → Maven → Existing Maven Projects
   - 选择项目根目录

2. **安装Lombok**
   - 下载lombok.jar
   - 运行: `java -jar lombok.jar`
   - 选择Eclipse安装目录
   - 重启Eclipse

3. **配置Java编译器**
   - Project → Properties → Java Compiler
   - 设置Compiler compliance level为11

4. **启用Annotation Processing**
   - Project → Properties → Java Compiler → Annotation Processing
   - 勾选"Enable project specific settings"
   - 勾选"Enable annotation processing"

### 本地开发环境

#### 1. 配置本地配置文件

```bash
# 复制配置模板
cp src/main/resources/application.yml src/main/resources/application-local.yml

# 编辑本地配置
vim src/main/resources/application-local.yml
```

**本地配置示例**:
```yaml
database:
  host: localhost
  port: 2881
  username: root
  password: password
  schema: test_db
  tables: "*"

datahub:
  endpoint: https://dh-cn-hangzhou.aliyuncs.com
  accessId: ${DATAHUB_ACCESS_ID}
  accessKey: ${DATAHUB_ACCESS_KEY}
  project: dev-project
  topic: dev-topic

flink:
  parallelism: 2  # 本地开发使用较小并行度
  checkpoint:
    interval: 60000  # 1分钟，加快测试
    
output:
  path: /tmp/flink-output
  format: json
  
monitoring:
  port: 8080
```


#### 2. 启动本地Flink集群

```bash
# 下载Flink（如果还没有）
wget https://archive.apache.org/dist/flink/flink-1.18.0/flink-1.18.0-bin-scala_2.12.tgz
tar -xzf flink-1.18.0-bin-scala_2.12.tgz
cd flink-1.18.0

# 启动本地集群
./bin/start-cluster.sh

# 验证集群启动
curl http://localhost:8081/overview

# 访问Web UI
open http://localhost:8081

# 停止集群
./bin/stop-cluster.sh
```

#### 3. 使用Docker Compose启动依赖服务

```bash
# 启动所有服务
docker-compose up -d

# 仅启动特定服务
docker-compose up -d jobmanager taskmanager

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f

# 停止服务
docker-compose down
```

#### 4. 运行应用

**方式1: 通过IDE运行**
- 在IDE中运行FlinkPipelineMain类
- 配置VM参数和程序参数

**方式2: 通过Maven运行**
```bash
mvn exec:java -Dexec.mainClass="com.realtime.pipeline.FlinkPipelineMain" \
  -Dexec.args="--config src/main/resources/application-local.yml"
```

**方式3: 提交到Flink集群**
```bash
# 打包应用
mvn clean package -DskipTests

# 提交作业
./flink-1.18.0/bin/flink run \
  target/realtime-data-pipeline-1.0.0-SNAPSHOT.jar \
  --config src/main/resources/application-local.yml
```

### 开发工具推荐

#### 1. 代码质量工具

**Checkstyle** - 代码风格检查
```xml
<!-- 在pom.xml中添加 -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-checkstyle-plugin</artifactId>
    <version>3.3.0</version>
</plugin>
```

**SpotBugs** - 静态代码分析
```xml
<plugin>
    <groupId>com.github.spotbugs</groupId>
    <artifactId>spotbugs-maven-plugin</artifactId>
    <version>4.7.3.5</version>
</plugin>
```

#### 2. 调试工具

**JConsole** - JVM监控
```bash
jconsole localhost:9010
```

**VisualVM** - 性能分析
```bash
jvisualvm
```

**Flink Web UI** - Flink作业监控
```
http://localhost:8081
```


## 测试指南

系统采用**双重测试方法**，结合单元测试和基于属性的测试（Property-Based Testing），确保代码质量和正确性。

### 测试策略

#### 1. 单元测试 (Unit Tests)

**目的**: 验证特定示例、边界情况和错误条件

**命名规范**: `*Test.java`

**示例**:
```java
@Test
public void testCDCCollectorCapturesInsertEvent() {
    // Given: 配置CDC采集器
    CDCCollector collector = new CDCCollector();
    collector.start(dbConfig, dhConfig);
    
    // When: 执行INSERT操作
    executeInsert("test_table", testData);
    
    // Then: 验证事件被捕获
    ChangeEvent event = waitForEvent(5000);
    assertNotNull(event);
    assertEquals("INSERT", event.getEventType());
    assertEquals(testData, event.getAfter());
}

@Test
public void testFileRollingOnSizeThreshold() {
    // Given: 配置文件Sink
    FileSink sink = createFileSink();
    sink.setRollingPolicy(RollingPolicy.onSize(1024 * 1024 * 1024)); // 1GB
    
    // When: 写入超过1GB的数据
    writeTestData(sink, 1100 * 1024 * 1024);
    
    // Then: 验证创建了新文件
    List<File> files = listOutputFiles();
    assertTrue(files.size() >= 2);
}
```

#### 2. 基于属性的测试 (Property-Based Tests)

**目的**: 验证跨所有输入的通用属性，发现边界情况

**命名规范**: `*PropertyTest.java`

**框架**: jqwik 1.8.0

**示例**:
```java
/**
 * Feature: realtime-data-pipeline
 * Property 7: 事件时间顺序保持
 * 
 * 验证需求: 2.3, 9.4
 */
@Property
public void eventOrderPreservation(@ForAll("eventSequences") List<ChangeEvent> events) {
    // Given: 一系列事件
    // When: 发送并处理事件
    List<ProcessedEvent> output = processEvents(events);
    
    // Then: 验证时间顺序保持
    for (int i = 1; i < output.size(); i++) {
        assertTrue(output.get(i).getTimestamp() >= output.get(i-1).getTimestamp(),
            "Event order not preserved at index " + i);
    }
}

/**
 * Feature: realtime-data-pipeline
 * Property 39: 唯一标识符生成
 * 
 * 验证需求: 9.6
 */
@Property
public void uniqueIdentifierGeneration(@ForAll("changeEvents") List<ChangeEvent> events) {
    // Given: 一系列事件
    // When: 处理事件
    List<ProcessedEvent> processed = processEvents(events);
    
    // Then: 验证所有ID唯一
    Set<String> ids = processed.stream()
        .map(ProcessedEvent::getEventId)
        .collect(Collectors.toSet());
    
    assertEquals(processed.size(), ids.size(), 
        "Duplicate event IDs found");
}

// 数据生成器
@Provide
Arbitrary<List<ChangeEvent>> eventSequences() {
    return Arbitraries.of(ChangeEvent.class)
        .list()
        .ofMinSize(10)
        .ofMaxSize(100);
}
```


### 运行测试

#### 运行所有测试

```bash
# 运行所有测试（单元测试 + 属性测试）
mvn test

# 查看测试报告
open target/surefire-reports/index.html
```

#### 运行特定类型的测试

```bash
# 仅运行单元测试
mvn test -Dtest=*Test

# 仅运行属性测试
mvn test -Dtest=*PropertyTest

# 运行特定包的测试
mvn test -Dtest=com.realtime.pipeline.cdc.*Test

# 运行特定测试类
mvn test -Dtest=CDCCollectorTest

# 运行特定测试方法
mvn test -Dtest=CDCCollectorTest#testCapturesInsertEvent
```

#### 运行集成测试

```bash
# 运行集成测试（需要Docker环境）
mvn verify -Pintegration-tests

# 或使用特定标签
mvn test -Dgroups=integration
```

### 测试配置

#### jqwik配置

在`src/test/resources/jqwik.properties`中配置:
```properties
# 每个属性测试的迭代次数
jqwik.tries.default = 100

# 随机种子（用于可重现的测试）
# jqwik.seed = 42

# 报告级别
jqwik.reporting.usejunitplatform = true

# 收缩策略
jqwik.shrinking.bounded = true
```

#### 测试资源

测试配置文件位于`src/test/resources/`:
- `test-config-valid.yml`: 有效配置示例
- `test-config-invalid-*.yml`: 无效配置示例（用于测试配置验证）

### 编写新测试

#### 1. 编写单元测试

```java
package com.realtime.pipeline.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

public class ExampleComponentTest {
    
    private ExampleComponent component;
    
    @BeforeEach
    public void setUp() {
        // 初始化测试环境
        component = new ExampleComponent();
    }
    
    @AfterEach
    public void tearDown() {
        // 清理测试环境
        component.close();
    }
    
    @Test
    public void testBasicFunctionality() {
        // Given
        String input = "test input";
        
        // When
        String result = component.process(input);
        
        // Then
        assertNotNull(result);
        assertEquals("expected output", result);
    }
    
    @Test
    public void testErrorHandling() {
        // Given
        String invalidInput = null;
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            component.process(invalidInput);
        });
    }
}
```


#### 2. 编写属性测试

```java
package com.realtime.pipeline.example;

import net.jqwik.api.*;
import static org.junit.jupiter.api.Assertions.*;

public class ExampleComponentPropertyTest {
    
    /**
     * Feature: realtime-data-pipeline
     * Property X: 描述属性
     * 
     * 验证需求: X.X
     */
    @Property
    public void propertyAlwaysHolds(@ForAll String input) {
        // Given
        ExampleComponent component = new ExampleComponent();
        
        // When
        String result = component.process(input);
        
        // Then: 验证属性
        assertNotNull(result);
        assertTrue(result.length() >= input.length());
    }
    
    /**
     * 自定义数据生成器
     */
    @Provide
    Arbitrary<CustomData> customDataGenerator() {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofLength(10),
            Arbitraries.integers().between(1, 100),
            Arbitraries.doubles().greaterOrEqual(0.0)
        ).as((str, num, dbl) -> new CustomData(str, num, dbl));
    }
    
    @Property
    public void propertyWithCustomData(@ForAll("customDataGenerator") CustomData data) {
        // 使用自定义数据进行测试
        assertTrue(data.isValid());
    }
}
```

### 测试覆盖率

#### 生成覆盖率报告

```bash
# 使用JaCoCo生成覆盖率报告
mvn clean test jacoco:report

# 查看报告
open target/site/jacoco/index.html
```

#### 覆盖率目标

- **行覆盖率**: ≥ 80%
- **分支覆盖率**: ≥ 70%
- **核心业务逻辑**: ≥ 90%

### 测试最佳实践

#### 1. 测试命名

- 使用描述性名称: `testCDCCollectorCapturesInsertEvent`
- 遵循模式: `test<MethodName><Scenario>`
- 属性测试使用属性名称: `eventOrderPreservation`

#### 2. 测试结构

使用Given-When-Then模式:
```java
@Test
public void testExample() {
    // Given: 设置测试环境和输入
    ExampleComponent component = new ExampleComponent();
    String input = "test";
    
    // When: 执行被测试的操作
    String result = component.process(input);
    
    // Then: 验证结果
    assertEquals("expected", result);
}
```

#### 3. 测试隔离

- 每个测试应该独立运行
- 使用`@BeforeEach`和`@AfterEach`管理测试状态
- 避免测试之间的依赖

#### 4. 使用断言库

推荐使用AssertJ进行流畅断言:
```java
import static org.assertj.core.api.Assertions.*;

@Test
public void testWithAssertJ() {
    List<String> result = component.getResults();
    
    assertThat(result)
        .isNotNull()
        .hasSize(3)
        .contains("item1", "item2")
        .doesNotContain("invalid");
}
```

#### 5. Mock使用

使用Mockito进行Mock:
```java
import static org.mockito.Mockito.*;

@Test
public void testWithMock() {
    // 创建Mock对象
    DataHubClient mockClient = mock(DataHubClient.class);
    
    // 配置Mock行为
    when(mockClient.send(any()))
        .thenReturn(new SendResult(true));
    
    // 使用Mock对象
    CDCCollector collector = new CDCCollector(mockClient);
    collector.sendToDataHub(testEvent);
    
    // 验证Mock调用
    verify(mockClient, times(1)).send(any());
}
```


### 持续集成测试

#### GitHub Actions配置示例

```yaml
name: CI

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v2
    
    - name: Set up JDK 11
      uses: actions/setup-java@v2
      with:
        java-version: '11'
        distribution: 'adopt'
    
    - name: Cache Maven packages
      uses: actions/cache@v2
      with:
        path: ~/.m2
        key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
    
    - name: Run tests
      run: mvn clean test
    
    - name: Generate coverage report
      run: mvn jacoco:report
    
    - name: Upload coverage to Codecov
      uses: codecov/codecov-action@v2
```

## 贡献指南

### 代码规范

#### 1. 命名规范

**类名**: 大驼峰（PascalCase）
```java
public class CDCCollector { }
public class DataHubSender { }
```

**方法名**: 小驼峰（camelCase）
```java
public void startCollector() { }
public ChangeEvent captureEvent() { }
```

**常量**: 全大写下划线分隔（UPPER_SNAKE_CASE）
```java
public static final int MAX_RETRY_ATTEMPTS = 3;
public static final long RETRY_INTERVAL_MS = 2000;
```

**变量名**: 小驼峰（camelCase）
```java
private String databaseHost;
private int retryCount;
```

**包名**: 全小写（lowercase）
```java
package com.realtime.pipeline.cdc;
package com.realtime.pipeline.flink.processor;
```

#### 2. 注释规范

**类注释**:
```java
/**
 * CDC采集器，从OceanBase数据库捕获变更数据
 * 
 * <p>功能：
 * <ul>
 *   <li>捕获INSERT/UPDATE/DELETE操作</li>
 *   <li>自动重连和重试</li>
 *   <li>发送数据到DataHub</li>
 * </ul>
 * 
 * <p>使用示例：
 * <pre>{@code
 * CDCCollector collector = new CDCCollector();
 * collector.start(dbConfig, dhConfig);
 * }</pre>
 * 
 * @author Your Name
 * @since 1.0.0
 * @see DataHubSender
 */
public class CDCCollector {
    // ...
}
```

**方法注释**:
```java
/**
 * 启动CDC采集
 * 
 * <p>此方法会建立与数据库的连接，开始监听变更事件。
 * 如果连接失败，会在30秒内自动重试。
 * 
 * @param dbConfig 数据库配置，不能为null
 * @param dhConfig DataHub配置，不能为null
 * @throws IllegalArgumentException 如果配置无效
 * @throws ConnectionException 如果无法连接到数据库
 */
public void start(DatabaseConfig dbConfig, DataHubConfig dhConfig) {
    // ...
}
```

**行内注释**:
```java
// 重试最多3次
for (int i = 0; i < MAX_RETRY_ATTEMPTS; i++) {
    try {
        sendToDataHub(event);
        break;  // 成功则退出循环
    } catch (DataHubException e) {
        // 记录错误并继续重试
        logger.warn("Retry attempt {} failed", i + 1, e);
    }
}
```


#### 3. 代码格式化

**缩进**: 4个空格（不使用Tab）

**行长度**: 最大120字符

**大括号**: K&R风格
```java
public void method() {
    if (condition) {
        // code
    } else {
        // code
    }
}
```

**空行**:
- 类成员之间空一行
- 方法之间空一行
- 逻辑块之间空一行

**导入顺序**:
1. Java标准库
2. 第三方库
3. 项目内部包

```java
import java.util.List;
import java.util.Map;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.slf4j.Logger;

import com.realtime.pipeline.model.ChangeEvent;
```

#### 4. 异常处理

**使用自定义异常**:
```java
public class CDCException extends RuntimeException {
    public CDCException(String message) {
        super(message);
    }
    
    public CDCException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

**适当的异常处理**:
```java
try {
    sendToDataHub(event);
} catch (DataHubException e) {
    logger.error("Failed to send event: {}", event.getEventId(), e);
    deadLetterQueue.send(event, e);
    throw new CDCException("Failed to send event after retries", e);
}
```

**不要吞掉异常**:
```java
// 错误示例
try {
    riskyOperation();
} catch (Exception e) {
    // 什么都不做 - 不好！
}

// 正确示例
try {
    riskyOperation();
} catch (Exception e) {
    logger.error("Operation failed", e);
    // 或者重新抛出
    throw new RuntimeException("Operation failed", e);
}
```

#### 5. 日志规范

**使用SLF4J**:
```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CDCCollector {
    private static final Logger logger = LoggerFactory.getLogger(CDCCollector.class);
    
    public void process() {
        logger.debug("Processing event: {}", event.getEventId());
        logger.info("CDC collector started successfully");
        logger.warn("Retry attempt {} failed", retryCount);
        logger.error("Failed to connect to database", exception);
    }
}
```

**日志级别使用**:
- `DEBUG`: 详细的调试信息
- `INFO`: 一般信息（启动、停止、重要操作）
- `WARN`: 警告信息（重试、降级）
- `ERROR`: 错误信息（异常、失败）

**参数化日志**:
```java
// 推荐：使用参数化
logger.info("Processing event {} from table {}", eventId, tableName);

// 不推荐：字符串拼接
logger.info("Processing event " + eventId + " from table " + tableName);
```


### Git工作流

#### 1. 分支策略

**主要分支**:
- `main`: 生产环境代码
- `develop`: 开发分支
- `feature/*`: 功能分支
- `bugfix/*`: 修复分支
- `release/*`: 发布分支

**分支命名**:
```bash
feature/add-parquet-support
bugfix/fix-checkpoint-failure
release/v1.1.0
```

#### 2. 提交规范

**提交消息格式**:
```
<type>(<scope>): <subject>

<body>

<footer>
```

**类型（type）**:
- `feat`: 新功能
- `fix`: 修复bug
- `docs`: 文档更新
- `style`: 代码格式化
- `refactor`: 重构
- `test`: 测试相关
- `chore`: 构建/工具相关

**示例**:
```
feat(cdc): add support for DELETE operations

- Implement DELETE event capture
- Add tests for DELETE operations
- Update documentation

Closes #123
```

#### 3. Pull Request流程

1. **创建功能分支**
   ```bash
   git checkout -b feature/new-feature develop
   ```

2. **开发和提交**
   ```bash
   git add .
   git commit -m "feat(component): add new feature"
   ```

3. **推送分支**
   ```bash
   git push origin feature/new-feature
   ```

4. **创建Pull Request**
   - 在GitHub/GitLab上创建PR
   - 填写PR描述模板
   - 关联相关Issue

5. **代码审查**
   - 至少一个审查者批准
   - 所有CI检查通过
   - 解决所有评论

6. **合并**
   - 使用Squash and Merge（保持历史清晰）
   - 删除功能分支

#### 4. 代码审查清单

**功能性**:
- [ ] 代码实现了需求
- [ ] 边界情况处理正确
- [ ] 错误处理适当

**代码质量**:
- [ ] 遵循代码规范
- [ ] 命名清晰易懂
- [ ] 注释充分
- [ ] 没有重复代码

**测试**:
- [ ] 有单元测试
- [ ] 有属性测试（如适用）
- [ ] 测试覆盖率足够
- [ ] 所有测试通过

**文档**:
- [ ] 更新了相关文档
- [ ] 添加了JavaDoc注释
- [ ] 更新了CHANGELOG

**性能**:
- [ ] 没有明显的性能问题
- [ ] 资源使用合理
- [ ] 没有内存泄漏


## 开发工作流

### 添加新功能

#### 1. 添加新的数据处理逻辑

```java
// 1. 创建新的处理函数
package com.realtime.pipeline.flink.processor;

public class CustomProcessor extends RichMapFunction<ChangeEvent, ProcessedEvent> {
    
    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        // 初始化资源
    }
    
    @Override
    public ProcessedEvent map(ChangeEvent event) throws Exception {
        // 实现自定义处理逻辑
        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(event.getEventId());
        processed.setData(transformData(event.getAfter()));
        return processed;
    }
    
    private Map<String, Object> transformData(Map<String, Object> data) {
        // 数据转换逻辑
        return data;
    }
    
    @Override
    public void close() throws Exception {
        // 清理资源
        super.close();
    }
}

// 2. 在Flink作业中使用
DataStream<ProcessedEvent> processed = source
    .map(new CustomProcessor())
    .name("Custom Processor");
```

#### 2. 添加新的输出格式

```java
// 1. 继承AbstractFileSink
package com.realtime.pipeline.flink.sink;

public class XmlFileSink extends AbstractFileSink {
    
    @Override
    protected void writeRecord(ProcessedEvent event) throws IOException {
        String xml = convertToXml(event);
        writer.write(xml);
        writer.write("\n");
    }
    
    private String convertToXml(ProcessedEvent event) {
        StringBuilder xml = new StringBuilder();
        xml.append("<event>");
        xml.append("<id>").append(event.getEventId()).append("</id>");
        xml.append("<timestamp>").append(event.getTimestamp()).append("</timestamp>");
        // ... 更多字段
        xml.append("</event>");
        return xml.toString();
    }
    
    @Override
    protected String getFileExtension() {
        return ".xml";
    }
}

// 2. 在配置中添加支持
// OutputConfig.java
public enum OutputFormat {
    JSON, PARQUET, CSV, XML  // 添加XML
}
```

#### 3. 添加新的监控指标

```java
// 1. 定义指标
package com.realtime.pipeline.monitoring;

public class CustomMetrics {
    private final Counter customCounter;
    private final Gauge<Long> customGauge;
    
    public CustomMetrics(RuntimeContext context) {
        MetricGroup metricGroup = context.getMetricGroup();
        
        this.customCounter = metricGroup.counter("custom_metric_count");
        this.customGauge = metricGroup.gauge("custom_metric_value", 
            () -> calculateCustomValue());
    }
    
    public void increment() {
        customCounter.inc();
    }
    
    private long calculateCustomValue() {
        // 计算指标值
        return 0L;
    }
}

// 2. 在处理函数中使用
public class MetricsProcessor extends RichMapFunction<ChangeEvent, ProcessedEvent> {
    private transient CustomMetrics metrics;
    
    @Override
    public void open(Configuration parameters) {
        metrics = new CustomMetrics(getRuntimeContext());
    }
    
    @Override
    public ProcessedEvent map(ChangeEvent event) {
        metrics.increment();
        return process(event);
    }
}
```


### 修复Bug

#### Bug修复流程

1. **重现Bug**
   - 编写失败的测试用例
   - 确认Bug存在

2. **定位问题**
   - 使用调试器
   - 查看日志
   - 分析代码

3. **修复代码**
   - 实现修复
   - 确保测试通过

4. **验证修复**
   - 运行所有相关测试
   - 手动验证

5. **提交修复**
   - 提交代码和测试
   - 更新文档

#### Bug修复示例

```java
// 1. 编写失败的测试
@Test
public void testBugFix_EventOrderNotPreserved() {
    // Given: 有序的事件序列
    List<ChangeEvent> events = createOrderedEvents();
    
    // When: 处理事件
    List<ProcessedEvent> result = processor.process(events);
    
    // Then: 验证顺序保持（这个测试会失败）
    for (int i = 1; i < result.size(); i++) {
        assertTrue(result.get(i).getTimestamp() >= result.get(i-1).getTimestamp(),
            "Event order not preserved");
    }
}

// 2. 修复代码
public class EventProcessor {
    public List<ProcessedEvent> process(List<ChangeEvent> events) {
        // 修复：添加排序逻辑
        return events.stream()
            .sorted(Comparator.comparing(ChangeEvent::getTimestamp))
            .map(this::processEvent)
            .collect(Collectors.toList());
    }
}

// 3. 验证测试通过
// mvn test -Dtest=EventProcessorTest#testBugFix_EventOrderNotPreserved
```

## 调试技巧

### 本地调试

#### 1. IDE调试

**IntelliJ IDEA**:
1. 在代码行号左侧点击设置断点
2. 右键点击主类 → Debug 'FlinkPipelineMain'
3. 使用调试工具栏：
   - Step Over (F8): 单步执行
   - Step Into (F7): 进入方法
   - Step Out (Shift+F8): 跳出方法
   - Resume (F9): 继续执行

**条件断点**:
```java
// 右键断点 → 添加条件
event.getEventType().equals("UPDATE")
```

**日志断点**:
```java
// 右键断点 → 选择"Evaluate and log"
"Processing event: " + event.getEventId()
```

#### 2. 远程调试

**启动应用时启用远程调试**:
```bash
# 方式1: 通过环境变量
export JAVA_TOOL_OPTIONS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
java -jar target/realtime-data-pipeline-1.0.0-SNAPSHOT.jar

# 方式2: 直接在命令行指定
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 \
  -jar target/realtime-data-pipeline-1.0.0-SNAPSHOT.jar
```

**在IDE中连接**:
1. Run → Edit Configurations
2. 添加 Remote JVM Debug
3. 设置Host: localhost, Port: 5005
4. 点击Debug按钮连接

**Docker容器远程调试**:
```bash
# 在docker-compose.yml中添加
services:
  cdc-collector:
    environment:
      - JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
    ports:
      - "5005:5005"
```


### 日志调试

#### 1. 调整日志级别

**运行时调整**:
```bash
# 通过环境变量
export LOG_LEVEL=DEBUG
docker-compose restart cdc-collector

# 或在配置文件中
vim src/main/resources/log4j2.xml
```

**log4j2.xml配置**:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN">
    <Appenders>
        <Console name="Console" target="SYSTEM_OUT">
            <PatternLayout pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n"/>
        </Console>
    </Appenders>
    <Loggers>
        <!-- 调整特定包的日志级别 -->
        <Logger name="com.realtime.pipeline.cdc" level="DEBUG"/>
        <Logger name="com.realtime.pipeline.flink" level="INFO"/>
        <Logger name="org.apache.flink" level="WARN"/>
        
        <Root level="INFO">
            <AppenderRef ref="Console"/>
        </Root>
    </Loggers>
</Configuration>
```

#### 2. 查看日志

**Docker日志**:
```bash
# 实时查看日志
docker-compose logs -f cdc-collector

# 查看最近100行
docker-compose logs --tail 100 cdc-collector

# 查看特定时间段
docker-compose logs --since "2025-01-28T12:00:00" cdc-collector

# 搜索日志
docker-compose logs cdc-collector | grep "ERROR"
```

**Flink日志**:
```bash
# JobManager日志
tail -f /opt/flink/logs/flink-*-standalonesession-*.log

# TaskManager日志
tail -f /opt/flink/logs/flink-*-taskexecutor-*.log

# 查看特定作业的日志
# 在Flink Web UI中: http://localhost:8081 → Jobs → 选择作业 → Logs
```

### 性能分析

#### 1. JVM监控

**JConsole**:
```bash
# 启动JConsole
jconsole localhost:9010

# 查看：
# - 内存使用
# - 线程状态
# - CPU使用
# - 类加载
```

**VisualVM**:
```bash
# 启动VisualVM
jvisualvm

# 功能：
# - CPU采样
# - 内存采样
# - 线程分析
# - 堆转储分析
```

#### 2. Flink性能分析

**Flink Web UI**:
```
http://localhost:8081

查看：
- 作业拓扑
- 算子并行度
- 反压情况
- Checkpoint统计
- 任务指标
```

**反压分析**:
```bash
# 查看反压状态
curl http://localhost:8081/jobs/<job-id>/vertices/<vertex-id>/backpressure

# 反压级别：
# - OK: < 0.1
# - LOW: 0.1 - 0.5
# - HIGH: > 0.5
```

**Checkpoint分析**:
```bash
# 查看Checkpoint统计
curl http://localhost:8081/jobs/<job-id>/checkpoints

# 关注：
# - Checkpoint耗时
# - 状态大小
# - 对齐时间
```


## 性能优化

### Flink性能优化

#### 1. 并行度调优

```java
// 全局并行度
env.setParallelism(8);

// 算子级别并行度
DataStream<ChangeEvent> source = env
    .addSource(new DataHubSource())
    .setParallelism(4);  // Source并行度

DataStream<ProcessedEvent> processed = source
    .map(new EventProcessor())
    .setParallelism(8);  // 处理并行度

processed
    .addSink(new FileSink())
    .setParallelism(4);  // Sink并行度
```

**并行度选择建议**:
- Source: 根据数据源分区数
- 处理: CPU密集型操作使用较高并行度
- Sink: 根据输出目标数量

#### 2. 内存配置优化

```yaml
# TaskManager内存配置
taskmanager:
  memory:
    process:
      size: 4096m  # 总进程内存
    flink:
      size: 3456m  # Flink使用的内存
    managed:
      size: 1024m  # 托管内存（RocksDB等）
    network:
      min: 64mb
      max: 256mb   # 网络缓冲区
```

#### 3. Checkpoint优化

```java
// Checkpoint配置
env.enableCheckpointing(300000);  // 5分钟

CheckpointConfig config = env.getCheckpointConfig();
config.setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
config.setMinPauseBetweenCheckpoints(60000);  // 最小间隔
config.setCheckpointTimeout(600000);  // 超时时间
config.setMaxConcurrentCheckpoints(1);  // 并发数
config.setTolerableCheckpointFailureNumber(3);  // 容忍失败次数

// 使用增量Checkpoint（RocksDB）
env.setStateBackend(new EmbeddedRocksDBStateBackend(true));
```

**Checkpoint优化建议**:
- 增加间隔减少开销
- 使用增量Checkpoint
- 调整超时时间
- 限制并发Checkpoint

#### 4. 状态后端选择

```java
// HashMapStateBackend - 适合小状态
env.setStateBackend(new HashMapStateBackend());

// RocksDB - 适合大状态
env.setStateBackend(new EmbeddedRocksDBStateBackend(true));
```

**选择建议**:
- 状态 < 1GB: HashMapStateBackend
- 状态 > 1GB: RocksDB
- 需要增量Checkpoint: RocksDB


### 代码优化

#### 1. 避免频繁对象创建

```java
// 不好：每次都创建新对象
public ProcessedEvent map(ChangeEvent event) {
    return new ProcessedEvent(
        event.getEventId(),
        event.getTimestamp(),
        new HashMap<>(event.getAfter())
    );
}

// 好：重用对象
public class EventProcessor extends RichMapFunction<ChangeEvent, ProcessedEvent> {
    private transient ProcessedEvent reusable;
    
    @Override
    public void open(Configuration parameters) {
        reusable = new ProcessedEvent();
    }
    
    @Override
    public ProcessedEvent map(ChangeEvent event) {
        reusable.setEventId(event.getEventId());
        reusable.setTimestamp(event.getTimestamp());
        reusable.setData(event.getAfter());
        return reusable;
    }
}
```

#### 2. 批量处理

```java
// 不好：逐条发送
for (ChangeEvent event : events) {
    datahubClient.send(event);
}

// 好：批量发送
List<ChangeEvent> batch = new ArrayList<>();
for (ChangeEvent event : events) {
    batch.add(event);
    if (batch.size() >= BATCH_SIZE) {
        datahubClient.sendBatch(batch);
        batch.clear();
    }
}
if (!batch.isEmpty()) {
    datahubClient.sendBatch(batch);
}
```

#### 3. 使用缓存

```java
public class CachedProcessor extends RichMapFunction<ChangeEvent, ProcessedEvent> {
    private transient LoadingCache<String, TableSchema> schemaCache;
    
    @Override
    public void open(Configuration parameters) {
        schemaCache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(new CacheLoader<String, TableSchema>() {
                @Override
                public TableSchema load(String tableName) {
                    return loadSchema(tableName);
                }
            });
    }
    
    @Override
    public ProcessedEvent map(ChangeEvent event) {
        TableSchema schema = schemaCache.get(event.getTable());
        return processWithSchema(event, schema);
    }
}
```

### 数据库优化

#### 1. CDC采集优化

```java
// 配置合适的批量大小
CDCCollector collector = new CDCCollector();
collector.setBatchSize(1000);  // 批量读取
collector.setFetchSize(500);   // 预取大小

// 过滤不需要的表
collector.setTableFilter("orders|users|products");

// 只捕获需要的列
collector.setColumnFilter("id,name,created_at");
```

#### 2. 连接池配置

```java
// 配置数据库连接池
HikariConfig config = new HikariConfig();
config.setJdbcUrl(jdbcUrl);
config.setUsername(username);
config.setPassword(password);
config.setMaximumPoolSize(10);  // 最大连接数
config.setMinimumIdle(2);       // 最小空闲连接
config.setConnectionTimeout(30000);  // 连接超时
config.setIdleTimeout(600000);  // 空闲超时

HikariDataSource dataSource = new HikariDataSource(config);
```


### 文件输出优化

#### 1. 压缩配置

```java
// 使用Snappy压缩（平衡压缩率和速度）
OutputConfig config = new OutputConfig();
config.setCompression(CompressionType.SNAPPY);

// Parquet格式自带压缩
ParquetFileSink sink = new ParquetFileSink();
sink.setCompressionCodec(CompressionCodecName.SNAPPY);
```

#### 2. 文件滚动策略

```java
// 合理设置文件大小和时间间隔
RollingPolicy policy = DefaultRollingPolicy.builder()
    .withRolloverInterval(Duration.ofMinutes(30))  // 30分钟
    .withInactivityInterval(Duration.ofMinutes(5))  // 5分钟无数据
    .withMaxPartSize(512 * 1024 * 1024L)  // 512MB
    .build();
```

#### 3. 缓冲区配置

```java
// 增加写入缓冲区
FileSink<ProcessedEvent> sink = FileSink
    .forRowFormat(outputPath, new SimpleStringEncoder<ProcessedEvent>())
    .withBucketAssigner(new DateTimeBucketAssigner<>())
    .withRollingPolicy(policy)
    .withOutputFileConfig(
        OutputFileConfig.builder()
            .withPartPrefix("part")
            .withPartSuffix(".json")
            .build()
    )
    .build();
```

## 常见问题

### 开发环境问题

**Q: Maven依赖下载失败？**

A: 配置Maven镜像：
```xml
<!-- ~/.m2/settings.xml -->
<mirrors>
    <mirror>
        <id>aliyun</id>
        <mirrorOf>central</mirrorOf>
        <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
</mirrors>
```

**Q: Lombok不工作？**

A: 
1. 确保安装了Lombok插件
2. 启用Annotation Processing
3. 重启IDE

**Q: 测试运行很慢？**

A: 
1. 减少属性测试迭代次数（开发时）
2. 使用`-Dtest=SpecificTest`运行特定测试
3. 跳过集成测试：`mvn test -DskipITs`

### 代码问题

**Q: 如何处理Flink序列化问题？**

A: 
```java
// 1. 实现Serializable接口
public class MyClass implements Serializable {
    private static final long serialVersionUID = 1L;
    // ...
}

// 2. 使用transient标记不需要序列化的字段
private transient Logger logger;

// 3. 在open()方法中初始化transient字段
@Override
public void open(Configuration parameters) {
    logger = LoggerFactory.getLogger(getClass());
}
```

**Q: 如何避免状态过大？**

A:
1. 使用TTL清理过期状态
2. 使用RocksDB状态后端
3. 定期清理不需要的状态
4. 使用KeyedState而不是OperatorState

**Q: 如何处理时间和水印？**

A:
```java
// 使用事件时间
env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

// 配置水印策略
WatermarkStrategy<ChangeEvent> watermarkStrategy = WatermarkStrategy
    .<ChangeEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5))
    .withTimestampAssigner((event, timestamp) -> event.getTimestamp());

DataStream<ChangeEvent> source = env
    .addSource(new DataHubSource())
    .assignTimestampsAndWatermarks(watermarkStrategy);
```


## 发布流程

### 版本管理

#### 语义化版本

遵循[语义化版本 2.0.0](https://semver.org/lang/zh-CN/)规范：

- **主版本号（MAJOR）**: 不兼容的API修改
- **次版本号（MINOR）**: 向下兼容的功能性新增
- **修订号（PATCH）**: 向下兼容的问题修正

示例：`1.2.3`
- 1: 主版本号
- 2: 次版本号
- 3: 修订号

#### 发布步骤

1. **更新版本号**
   ```bash
   # 使用Maven Versions插件
   mvn versions:set -DnewVersion=1.1.0
   
   # 确认更改
   mvn versions:commit
   
   # 或回滚
   mvn versions:revert
   ```

2. **更新CHANGELOG**
   ```markdown
   # CHANGELOG.md
   
   ## [1.1.0] - 2025-01-28
   
   ### Added
   - 新增Parquet格式输出支持
   - 新增动态扩缩容功能
   
   ### Changed
   - 优化Checkpoint性能
   - 更新Flink版本到1.18.0
   
   ### Fixed
   - 修复事件顺序问题
   - 修复内存泄漏
   ```

3. **运行完整测试**
   ```bash
   # 运行所有测试
   mvn clean test
   
   # 运行集成测试
   mvn verify
   
   # 检查代码质量
   mvn checkstyle:check
   mvn spotbugs:check
   ```

4. **构建发布包**
   ```bash
   # 构建JAR包
   mvn clean package -DskipTests
   
   # 验证构建产物
   ls -lh target/realtime-data-pipeline-*.jar
   ```

5. **构建Docker镜像**
   ```bash
   # 构建所有镜像
   docker build -f docker/jobmanager/Dockerfile -t realtime-pipeline/jobmanager:1.1.0 .
   docker build -f docker/taskmanager/Dockerfile -t realtime-pipeline/taskmanager:1.1.0 .
   docker build -f docker/cdc-collector/Dockerfile -t realtime-pipeline/cdc-collector:1.1.0 .
   
   # 标记latest
   docker tag realtime-pipeline/jobmanager:1.1.0 realtime-pipeline/jobmanager:latest
   docker tag realtime-pipeline/taskmanager:1.1.0 realtime-pipeline/taskmanager:latest
   docker tag realtime-pipeline/cdc-collector:1.1.0 realtime-pipeline/cdc-collector:latest
   ```

6. **推送镜像**
   ```bash
   # 推送到镜像仓库
   docker push realtime-pipeline/jobmanager:1.1.0
   docker push realtime-pipeline/taskmanager:1.1.0
   docker push realtime-pipeline/cdc-collector:1.1.0
   
   docker push realtime-pipeline/jobmanager:latest
   docker push realtime-pipeline/taskmanager:latest
   docker push realtime-pipeline/cdc-collector:latest
   ```

7. **创建Git标签**
   ```bash
   # 创建标签
   git tag -a v1.1.0 -m "Release version 1.1.0"
   
   # 推送标签
   git push origin v1.1.0
   
   # 推送所有标签
   git push origin --tags
   ```

8. **创建GitHub Release**
   - 在GitHub上创建Release
   - 上传构建产物
   - 填写Release Notes

## 资源链接

### 官方文档

- [Apache Flink文档](https://flink.apache.org/docs/stable/)
- [Flink CDC文档](https://ververica.github.io/flink-cdc-connectors/)
- [阿里云DataHub文档](https://help.aliyun.com/product/53345.html)
- [Maven文档](https://maven.apache.org/guides/)
- [Docker文档](https://docs.docker.com/)

### 测试框架

- [JUnit 5用户指南](https://junit.org/junit5/docs/current/user-guide/)
- [jqwik用户指南](https://jqwik.net/docs/current/user-guide.html)
- [Mockito文档](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [AssertJ文档](https://assertj.github.io/doc/)

### 工具和库

- [Lombok文档](https://projectlombok.org/features/)
- [Log4j2文档](https://logging.apache.org/log4j/2.x/)
- [Jackson文档](https://github.com/FasterXML/jackson-docs)

### 项目文档

- [README](../README.md) - 项目概述和快速开始
- [部署文档](DEPLOYMENT.md) - 详细部署指南
- [Docker快速开始](../docker/QUICKSTART.md) - Docker部署快速指南
- [设计文档](../.kiro/specs/realtime-data-pipeline/design.md) - 系统设计
- [需求文档](../.kiro/specs/realtime-data-pipeline/requirements.md) - 功能需求

## 联系方式

- **技术支持**: support@example.com
- **问题反馈**: <repository-url>/issues
- **文档**: <repository-url>/wiki
- **团队**: Realtime Data Pipeline Team

---

**最后更新**: 2025-01-28  
**版本**: 1.0.0  
**维护者**: Development Team
