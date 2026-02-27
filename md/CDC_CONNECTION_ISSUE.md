# CDC 数据库连接问题解决方案

## 当前状态

✅ **JdbcCDCApp 正在运行**
- 作业 ID: `3ab57727083df12d421a2b710ff466b3`
- 作业名称: "JDBC CDC to CSV Application"
- 状态: RUNNING

⚠️ **运行在模拟数据模式**
- 原因：无法连接到真实的 Oracle 数据库
- 输出文件位置：`/opt/flink/output/cdc/` (容器内)
- 文件格式：CSV，每 10 秒生成一条模拟数据

## 输出文件位置

### 容器内路径
```
/opt/flink/output/cdc/2026-02-12--03/
├── .part-7ed0f6ea-27a1-49ff-b1ca-b664607a39a8-0.inprogress.xxx
├── .part-1bdd1906-0f42-4b04-a998-b189e0a39a10-1.inprogress.xxx
└── ...
```

### 本地路径
文件在容器内，需要使用以下命令复制到本地：

```bash
# 创建本地输出目录
mkdir -p output/cdc

# 从容器复制文件
docker cp realtime-pipeline-taskmanager-1:/opt/flink/output/cdc/. output/cdc/

# 查看文件
ls -la output/cdc/
```

### 查看实时输出
```bash
# 查看最新的输出文件内容
docker exec realtime-pipeline-taskmanager-1 \
  find /opt/flink/output/cdc -type f -name "*inprogress*" -exec tail -20 {} \;
```

## 问题分析

### 问题 1: Oracle JDBC 驱动缺失

**错误信息：**
```
❌ 数据库连接失败: oracle.jdbc.OracleDriver
```

**原因：**
- Oracle JDBC 驱动 (`ojdbc8.jar`) 未包含在项目 JAR 包中
- Oracle JDBC 驱动是商业许可，不能直接添加到 Maven 依赖

**解决方案：**

1. **下载 Oracle JDBC 驱动**
   ```bash
   # 从 Oracle 官网下载 ojdbc8.jar
   # https://www.oracle.com/database/technologies/jdbc-downloads.html
   
   # 或使用 Maven 安装到本地仓库
   mvn install:install-file \
     -Dfile=ojdbc8.jar \
     -DgroupId=com.oracle.database.jdbc \
     -DartifactId=ojdbc8 \
     -Dversion=21.1.0.0 \
     -Dpackaging=jar
   ```

2. **添加到 pom.xml**
   ```xml
   <dependency>
       <groupId>com.oracle.database.jdbc</groupId>
       <artifactId>ojdbc8</artifactId>
       <version>21.1.0.0</version>
   </dependency>
   ```

3. **重新构建项目**
   ```bash
   mvn clean package -DskipTests
   ```

### 问题 2: 容器网络配置

**错误原因：**
- 容器内的 `localhost` 指向容器自己，而不是宿主机
- 数据库运行在宿主机的 `localhost:1521`

**解决方案 A: 使用宿主机网络（推荐用于开发）**

修改 `.env` 文件：
```bash
# 使用 host.docker.internal 访问宿主机（macOS/Windows）
DATABASE_HOST=host.docker.internal

# 或使用宿主机 IP 地址
DATABASE_HOST=192.168.x.x
```

**解决方案 B: 将数据库也容器化**

在 `docker-compose.yml` 中添加 Oracle 数据库服务，让所有服务在同一网络中。

## 快速修复步骤

### 步骤 1: 修改数据库主机配置

```bash
# 编辑 .env 文件
# 将 DATABASE_HOST=localhost 改为 DATABASE_HOST=host.docker.internal
sed -i '' 's/DATABASE_HOST=localhost/DATABASE_HOST=host.docker.internal/' .env

# 验证修改
grep DATABASE_HOST .env
```

### 步骤 2: 添加 Oracle JDBC 驱动（临时方案）

如果你已经有 `ojdbc8.jar` 文件：

```bash
# 复制驱动到容器
docker cp ojdbc8.jar flink-jobmanager:/opt/flink/lib/
docker cp ojdbc8.jar realtime-pipeline-taskmanager-1:/opt/flink/lib/

# 重启容器以加载驱动
docker compose restart jobmanager taskmanager
```

### 步骤 3: 重新提交作业

```bash
# 取消当前作业
docker exec flink-jobmanager flink cancel 3ab57727083df12d421a2b710ff466b3

# 重新构建（如果修改了 pom.xml）
mvn clean package -DskipTests

# 重新提交
./submit-to-flink.sh jdbc
```

### 步骤 4: 验证连接

```bash
# 查看日志，确认连接成功
docker logs -f realtime-pipeline-taskmanager-1 | grep -E "数据库|连接|CDC"

# 应该看到：
# ✅ 数据库连接成功
# 表 trans_info 当前最大 ID: xxx
```

## 当前输出示例

```csv
2026-02-12 03:26:16,trans_info,INSERT,0,"sample_data_0"
2026-02-12 03:26:26,trans_info,INSERT,1,"sample_data_1"
2026-02-12 03:26:36,trans_info,INSERT,2,"sample_data_2"
```

**格式说明：**
- 列 1: 时间戳
- 列 2: 表名
- 列 3: 操作类型 (INSERT/UPDATE/DELETE)
- 列 4+: 数据字段

## 监控和调试

### 查看作业状态
```bash
# Web UI
open http://localhost:8081

# 命令行
docker exec flink-jobmanager flink list
```

### 查看日志
```bash
# JobManager 日志
docker logs -f flink-jobmanager

# TaskManager 日志
docker logs -f realtime-pipeline-taskmanager-1

# 过滤 CDC 相关日志
docker logs realtime-pipeline-taskmanager-1 2>&1 | grep -i "cdc\|jdbc\|数据库"
```

### 查看输出文件
```bash
# 列出所有输出文件
docker exec realtime-pipeline-taskmanager-1 \
  find /opt/flink/output/cdc -type f

# 查看最新内容
docker exec realtime-pipeline-taskmanager-1 \
  find /opt/flink/output/cdc -type f -name "*inprogress*" \
  -exec tail -10 {} \;
```

## 下一步

1. **修复数据库连接**：按照上述步骤添加 JDBC 驱动和修改网络配置
2. **验证真实数据捕获**：在数据库中插入数据，确认 CDC 能够捕获
3. **优化轮询间隔**：根据需要调整 `POLL_INTERVAL_SECONDS` (默认 10 秒)
4. **配置输出格式**：根据需要调整 CSV 格式或切换到其他格式

## 参考文档

- [Oracle JDBC 下载](https://www.oracle.com/database/technologies/jdbc-downloads.html)
- [Docker 网络配置](https://docs.docker.com/network/)
- [Flink CDC Connector](https://ververica.github.io/flink-cdc-connectors/)
