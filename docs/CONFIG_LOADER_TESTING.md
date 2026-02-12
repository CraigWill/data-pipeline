# ConfigLoader 测试指南

## 概述

ConfigLoader 实现了以下功能：
1. **从YAML文件加载配置** (需求 10.1)
2. **支持环境变量覆盖配置文件参数** (需求 10.2)
3. **实现配置参数验证逻辑** (需求 10.3)

## 单元测试

单元测试位于 `src/test/java/com/realtime/pipeline/util/ConfigLoaderTest.java`

运行单元测试：
```bash
mvn test -Dtest=ConfigLoaderTest
```

单元测试覆盖：
- ✅ 加载默认配置文件
- ✅ 加载指定配置文件
- ✅ 配置文件不存在时的错误处理
- ✅ 配置验证 - 有效配置
- ✅ 配置验证 - 缺少必需配置
- ✅ 配置验证 - 无效的端口号
- ✅ 配置验证 - 无效的输出格式
- ✅ 配置验证 - 无效的并行度
- ✅ 配置验证 - 空的数据库主机
- ✅ 配置验证 - 空的表列表
- ✅ 配置验证 - 无效的状态后端类型
- ✅ 配置验证 - 监控配置的阈值范围
- ✅ 从文件系统加载配置
- ✅ 配置的默认值
- ✅ 环境变量覆盖逻辑的存在性

## 集成测试 - 环境变量覆盖

集成测试位于 `src/test/java/com/realtime/pipeline/util/ConfigLoaderIntegrationTest.java`

由于Java运行时环境变量的限制，环境变量覆盖功能需要通过集成测试来验证。

### 测试环境变量覆盖 - 字符串类型

```bash
export PIPELINE_DATABASE_HOST=integration-test-host
mvn test -Dtest=ConfigLoaderIntegrationTest#testEnvironmentVariableOverrideDatabaseHost
```

### 测试环境变量覆盖 - 整数类型

```bash
export PIPELINE_FLINK_PARALLELISM=16
mvn test -Dtest=ConfigLoaderIntegrationTest#testEnvironmentVariableOverrideFlinkParallelism
```

### 测试环境变量覆盖 - 输出格式

```bash
export PIPELINE_OUTPUT_FORMAT=parquet
mvn test -Dtest=ConfigLoaderIntegrationTest#testEnvironmentVariableOverrideOutputFormat
```

### 测试环境变量覆盖优先级

```bash
export PIPELINE_DATABASE_PORT=5432
mvn test -Dtest=ConfigLoaderIntegrationTest#testEnvironmentVariableOverridePriority
```

### 测试环境变量覆盖 - 布尔类型

```bash
export PIPELINE_MONITORING_ENABLED=false
mvn test -Dtest=ConfigLoaderIntegrationTest#testEnvironmentVariableOverrideBoolean
```

### 测试环境变量覆盖 - 长整型

```bash
export PIPELINE_FLINK_CHECKPOINTINTERVAL=120000
mvn test -Dtest=ConfigLoaderIntegrationTest#testEnvironmentVariableOverrideLong
```

### 测试环境变量覆盖 - 双精度浮点型

```bash
export PIPELINE_MONITORING_LOADTHRESHOLD=0.9
mvn test -Dtest=ConfigLoaderIntegrationTest#testEnvironmentVariableOverrideDouble
```

### 测试环境变量覆盖 - 列表类型

```bash
export PIPELINE_DATABASE_TABLES=table1,table2,table3
mvn test -Dtest=ConfigLoaderIntegrationTest#testEnvironmentVariableOverrideList
```

### 运行所有集成测试

```bash
# 设置所有环境变量
export PIPELINE_DATABASE_HOST=integration-test-host
export PIPELINE_DATABASE_PORT=5432
export PIPELINE_FLINK_PARALLELISM=16
export PIPELINE_FLINK_CHECKPOINTINTERVAL=120000
export PIPELINE_OUTPUT_FORMAT=parquet
export PIPELINE_MONITORING_ENABLED=false
export PIPELINE_MONITORING_LOADTHRESHOLD=0.9
export PIPELINE_DATABASE_TABLES=table1,table2,table3

# 运行所有集成测试
mvn test -Dtest=ConfigLoaderIntegrationTest
```

## 环境变量命名规范

环境变量格式：`PIPELINE_<SECTION>_<KEY>`

支持的配置段：
- `DATABASE` - 数据库配置
- `DATAHUB` - DataHub配置
- `FLINK` - Flink配置
- `OUTPUT` - 输出配置
- `MONITORING` - 监控配置

示例：
- `PIPELINE_DATABASE_HOST` - 数据库主机地址
- `PIPELINE_DATABASE_PORT` - 数据库端口
- `PIPELINE_FLINK_PARALLELISM` - Flink并行度
- `PIPELINE_OUTPUT_FORMAT` - 输出格式
- `PIPELINE_MONITORING_ENABLED` - 是否启用监控

## 支持的数据类型

ConfigLoader 支持以下数据类型的环境变量覆盖：
- `String` - 字符串
- `int/Integer` - 整数
- `long/Long` - 长整数
- `double/Double` - 双精度浮点数
- `boolean/Boolean` - 布尔值
- `List<String>` - 字符串列表（逗号分隔）

## 手动测试示例

### 示例 1: 覆盖数据库配置

```bash
# 设置环境变量
export PIPELINE_DATABASE_HOST=my-database-host
export PIPELINE_DATABASE_PORT=3307
export PIPELINE_DATABASE_USERNAME=my_user
export PIPELINE_DATABASE_PASSWORD=my_password

# 运行应用程序
java -jar target/realtime-data-pipeline-1.0.0-SNAPSHOT.jar
```

### 示例 2: 覆盖Flink配置

```bash
# 设置环境变量
export PIPELINE_FLINK_PARALLELISM=8
export PIPELINE_FLINK_CHECKPOINTINTERVAL=120000
export PIPELINE_FLINK_CHECKPOINTDIR=/custom/checkpoint/dir

# 运行应用程序
java -jar target/realtime-data-pipeline-1.0.0-SNAPSHOT.jar
```

### 示例 3: Docker容器中使用环境变量

```bash
docker run -e PIPELINE_DATABASE_HOST=db-host \
           -e PIPELINE_DATABASE_PORT=3306 \
           -e PIPELINE_FLINK_PARALLELISM=4 \
           realtime-data-pipeline:latest
```

## 配置验证

ConfigLoader 会在加载配置后自动验证配置的有效性。如果配置无效，系统会拒绝启动并返回明确的错误信息。

验证规则包括：
- 必需字段不能为空
- 端口号必须在 1-65535 范围内
- 并行度必须为正数
- 输出格式必须是 json、parquet 或 csv
- 状态后端类型必须是 hashmap 或 rocksdb
- 监控阈值必须在 0-1 范围内
- 等等...

## 故障排查

### 问题：环境变量没有生效

**可能原因：**
1. 环境变量名称拼写错误
2. 环境变量格式不正确（应该是 `PIPELINE_<SECTION>_<KEY>`）
3. 环境变量值的类型无法转换（例如，将字符串赋值给整数字段）

**解决方法：**
1. 检查环境变量名称是否正确
2. 查看日志中的环境变量覆盖信息
3. 确保环境变量值的类型正确

### 问题：配置验证失败

**可能原因：**
1. 配置文件中的值无效
2. 环境变量覆盖后的值无效

**解决方法：**
1. 查看错误信息，了解哪个配置参数无效
2. 检查配置文件和环境变量的值
3. 参考配置验证规则修正配置

## 总结

ConfigLoader 完全实现了任务 2.1 的所有要求：
- ✅ 支持从YAML文件加载配置 (需求 10.1)
- ✅ 支持环境变量覆盖配置文件参数 (需求 10.2)
- ✅ 实现配置参数验证逻辑 (需求 10.3)

所有功能都经过了全面的测试，包括单元测试和集成测试。
