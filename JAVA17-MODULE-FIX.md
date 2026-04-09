# Java 17 模块系统修复总结

## 问题描述

在使用 Java 17 运行 Flink 1.20.0 CDC 任务时，遇到以下错误：

```
java.lang.reflect.InaccessibleObjectException: Unable to make field private final java.util.Map java.util.Collections$UnmodifiableMap.m accessible: module java.base does not "opens java.util" to unnamed module
```

### 错误原因

Java 17 引入了更严格的模块系统（JPMS - Java Platform Module System），默认情况下不允许反射访问 JDK 内部类。Flink 1.20.0 在初始化 `ExecutionContextEnvironment` 时需要通过反射访问 `java.util.Collections$UnmodifiableMap` 等内部类，导致初始化失败。

### 错误堆栈

```
java.lang.ExceptionInInitializerError
  at org.apache.flink.client.ClientUtils.executeProgram(ClientUtils.java:109)
  ...
Caused by: java.lang.RuntimeException: Can not register process function transformation translator.
  at org.apache.flink.datastream.impl.ExecutionEnvironmentImpl.<clinit>(ExecutionEnvironmentImpl.java:98)
  ...
Caused by: java.lang.reflect.InaccessibleObjectException: Unable to make field private final java.util.Map java.util.Collections$UnmodifiableMap.m accessible: module java.base does not "opens java.util" to unnamed module
  at java.base/java.lang.reflect.AccessibleObject.checkCanSetAccessible(Unknown Source)
  ...
```

## 解决方案

通过添加 JVM 参数 `--add-opens` 来开放 Java 模块系统的访问限制。

### 修改文件

#### 1. JobManager Entrypoint 脚本

**文件**: `docker/jobmanager/entrypoint.sh`

在 Java 选项配置部分添加：

```bash
# Java 17 模块系统配置（解决反射访问限制）
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} --add-opens=java.base/java.util=ALL-UNNAMED"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} --add-opens=java.base/java.lang=ALL-UNNAMED"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} --add-opens=java.base/java.lang.reflect=ALL-UNNAMED"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} --add-opens=java.base/java.io=ALL-UNNAMED"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} --add-opens=java.base/java.net=ALL-UNNAMED"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} --add-opens=java.base/java.nio=ALL-UNNAMED"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} --add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} --add-opens=java.base/sun.net.dns=ALL-UNNAMED"
```

#### 2. TaskManager Entrypoint 脚本

**文件**: `docker/taskmanager/entrypoint.sh`

添加相同的 Java 17 模块配置：

```bash
# Java 17 模块系统配置（解决反射访问限制）
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} --add-opens=java.base/java.util=ALL-UNNAMED"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} --add-opens=java.base/java.lang=ALL-UNNAMED"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} --add-opens=java.base/java.lang.reflect=ALL-UNNAMED"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} --add-opens=java.base/java.io=ALL-UNNAMED"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} --add-opens=java.base/java.net=ALL-UNNAMED"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} --add-opens=java.base/java.nio=ALL-UNNAMED"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} --add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} --add-opens=java.base/sun.net.dns=ALL-UNNAMED"
```

### 部署步骤

1. **重新构建 Docker 镜像**:
   ```bash
   ./rebuild-all.sh
   ```

2. **重启服务**:
   ```bash
   docker-compose down
   docker-compose up -d
   ```

3. **验证修复**:
   ```bash
   # 提交测试任务
   curl -X POST http://localhost:5001/api/cdc/submit \
     -H "Content-Type: application/json" \
     -d '{
       "hostname": "host.docker.internal",
       "port": 1521,
       "username": "finance_user",
       "password": "password",
       "database": "helowin",
       "schema": "FINANCE_USER",
       "tables": ["ACCOUNT_INFO"],
       "outputPath": "/opt/flink/output/cdc",
       "parallelism": 2,
       "splitSize": 8096,
       "jobName": "test-java17-fix"
     }'
   
   # 检查任务状态
   curl http://localhost:8081/jobs
   ```

## 验证结果

修复后，任务成功提交并运行：

```json
{
  "name": "test-java17-fix",
  "state": "RUNNING",
  "start-time": 1774401851905,
  "vertices": [
    {
      "name": "Source: Oracle CDC Source",
      "status": "RUNNING",
      "parallelism": 1
    },
    {
      "name": "DDL Event Filter -> Map",
      "status": "RUNNING",
      "parallelism": 2
    },
    ...
  ]
}
```

所有算子都处于 RUNNING 状态，任务正常运行。

## 技术说明

### --add-opens 参数说明

`--add-opens` 是 Java 9+ 引入的 JVM 参数，用于在运行时开放模块的访问权限：

- **格式**: `--add-opens=<module>/<package>=<target-module>`
- **作用**: 允许 `<target-module>` 通过反射访问 `<module>` 中的 `<package>`
- **ALL-UNNAMED**: 表示所有未命名模块（包括应用程序代码和第三方库）

### 为什么需要这些参数

Flink 1.20.0 使用了以下反射操作：

1. **java.util**: 访问 `Collections$UnmodifiableMap` 等内部类
2. **java.lang**: 访问类加载器和反射相关的内部 API
3. **java.lang.reflect**: 深度反射操作
4. **java.io/java.net/java.nio**: 网络和 I/O 相关的内部实现
5. **sun.nio.ch/sun.net.dns**: Netty 和网络库需要的底层 API

### 安全性考虑

虽然 `--add-opens` 降低了模块系统的封装性，但这是 Flink 1.20.0 在 Java 17 上运行的必要配置。未来 Flink 版本可能会：

1. 减少对反射的依赖
2. 使用 Java 17+ 的新 API 替代反射
3. 提供官方的 Java 17 配置指南

## 相关问题

### 问题 1: 提交任务看不到作业

**原因**: monitor-backend 无法访问 flink-jobs JAR 文件

**解决方案**: 
- 在 `docker-compose.yml` 中为 monitor-backend 添加卷挂载
- 修改 `EmbeddedCdcService.java` 中的 JAR 路径配置

详见: [上下文总结 - TASK 1]

### 问题 2: Java 17 模块系统限制（本次修复）

**原因**: Flink 反射访问 JDK 内部类被 Java 17 模块系统阻止

**解决方案**: 添加 `--add-opens` JVM 参数

## 参考资料

- [JEP 261: Module System](https://openjdk.org/jeps/261)
- [Flink on Java 17](https://nightlies.apache.org/flink/flink-docs-master/docs/deployment/java_compatibility/)
- [Java 17 Migration Guide](https://docs.oracle.com/en/java/javase/17/migrate/migrating-jdk-8-later-jdk-releases.html)

## 总结

通过添加 Java 17 模块系统的开放参数，成功解决了 Flink 1.20.0 在 Java 17 环境下的反射访问限制问题。任务现在可以正常提交和运行。

**修复日期**: 2026-03-25  
**Flink 版本**: 1.20.0  
**Java 版本**: 17 (Eclipse Temurin 17-jre)  
**状态**: ✅ 已解决
