# 数据库迁移总结

## 完成的工作

### 1. 数据库表结构设计

创建了两个元数据表用于存储配置：

- `flink_user.cdc_datasources`: 数据源配置表
- `flink_user.cdc_tasks`: CDC 任务配置表

文件: `sql/setup-metadata-tables.sql`

### 2. Repository 层实现

创建了数据访问层，使用 Spring JDBC Template：

- `TaskRepository.java`: 任务配置的 CRUD 操作
- `DataSourceRepository.java`: 数据源配置的 CRUD 操作

### 3. Service 层重构

重构了服务层，从 JSON 文件存储迁移到数据库存储：

- `CdcTaskService.java`: 使用 TaskRepository 替代文件操作
- `DataSourceService.java`: 使用 DataSourceRepository 替代文件操作

### 4. DTO 增强

为 DTO 类添加了新字段：

- `TaskConfig`: 添加 `status` 和 `flinkJobId` 字段
- `DataSourceConfig`: 添加 `description` 字段

### 5. 配置更新

- 添加了 Spring JDBC 依赖到 `pom.xml`
- 在 `application-monitor.yml` 中配置了数据源连接

### 6. 迁移工具

创建了 Python 脚本用于将现有 JSON 配置迁移到数据库：

- `sql/migrate-json-to-db.py`: 自动迁移脚本
- `sql/README-database-migration.md`: 详细的迁移指南

## 主要优势

1. **持久化**: 配置存储在数据库中，容器重启后不会丢失
2. **任务状态跟踪**: 可以跟踪任务的运行状态（CREATED, RUNNING, STOPPED, FAILED）
3. **Flink Job ID 关联**: 保存 Flink 作业 ID，便于管理和监控
4. **并发安全**: 支持多实例并发访问
5. **事务支持**: 保证数据一致性
6. **自动恢复**: 为实现重启后自动恢复任务奠定基础

## 部署步骤

### 1. 创建数据库表

```bash
# 使用 Docker 容器执行
docker exec -i oracle11g sqlplus flink_user/password@helowin < sql/setup-metadata-tables.sql
```

### 2. 迁移现有配置（可选）

如果有现有的 JSON 配置需要迁移：

```bash
pip install cx_Oracle
python3 sql/migrate-json-to-db.py
```

### 3. 配置环境变量

在 `docker-compose.yml` 的 `monitor-backend` 服务中添加：

```yaml
environment:
  - DATABASE_HOST=host.docker.internal
  - DATABASE_PORT=1521
  - DATABASE_SID=helowin
  - DATABASE_USERNAME=flink_user
  - DATABASE_PASSWORD=password
```

### 4. 重新构建和部署

```bash
# 重新构建 JAR（已完成）
mvn clean package -DskipTests

# 重新构建 Docker 镜像
docker-compose build monitor-backend

# 重启服务
docker-compose up -d monitor-backend
```

## 数据库连接配置

应用程序通过以下环境变量连接数据库：

- `DATABASE_HOST`: 数据库主机（默认: host.docker.internal）
- `DATABASE_PORT`: 数据库端口（默认: 1521）
- `DATABASE_SID`: 数据库 SID（默认: helowin）
- `DATABASE_USERNAME`: 数据库用户（默认: flink_user）
- `DATABASE_PASSWORD`: 数据库密码（默认: password）

## 任务状态管理

任务现在有以下状态：

- `CREATED`: 任务已创建，但未提交到 Flink
- `RUNNING`: 任务正在 Flink 中运行
- `STOPPED`: 任务已停止
- `FAILED`: 任务提交或运行失败

当任务提交到 Flink 时，状态会自动更新为 `RUNNING`，并保存 Flink Job ID。

## 下一步工作（可选）

### 自动恢复功能

可以在 `EmbeddedCdcService` 中添加启动时自动恢复功能：

```java
@PostConstruct
public void autoRecoverTasks() {
    List<TaskConfig> runningTasks = taskRepository.findByStatus("RUNNING");
    for (TaskConfig task : runningTasks) {
        try {
            log.info("自动恢复任务: {}", task.getName());
            submitTask(task);
        } catch (Exception e) {
            log.error("恢复任务失败: {}", task.getId(), e);
            taskRepository.updateStatus(task.getId(), "FAILED", null);
        }
    }
}
```

### 任务监控

可以添加定期检查任务状态的功能：

- 检查 Flink 中的作业是否还在运行
- 如果作业已停止，更新数据库中的状态
- 发送告警通知

## 验证

### 查看数据源

```sql
SELECT id, name, host, port, sid FROM flink_user.cdc_datasources;
```

### 查看任务

```sql
SELECT id, name, schema_name, status, flink_job_id 
FROM flink_user.cdc_tasks 
ORDER BY created_at DESC;
```

### 查看运行中的任务

```sql
SELECT id, name, schema_name, flink_job_id 
FROM flink_user.cdc_tasks 
WHERE status = 'RUNNING';
```

## 文件清单

### 新增文件

- `sql/setup-metadata-tables.sql`: 数据库表创建脚本
- `sql/migrate-json-to-db.py`: 配置迁移脚本
- `sql/README-database-migration.md`: 迁移指南
- `src/main/java/com/realtime/monitor/repository/TaskRepository.java`: 任务仓库
- `src/main/java/com/realtime/monitor/repository/DataSourceRepository.java`: 数据源仓库
- `MIGRATION-SUMMARY.md`: 本文档

### 修改文件

- `pom.xml`: 添加 Spring JDBC 依赖
- `src/main/resources/application-monitor.yml`: 添加数据源配置
- `src/main/java/com/realtime/monitor/dto/TaskConfig.java`: 添加状态字段
- `src/main/java/com/realtime/monitor/dto/DataSourceConfig.java`: 添加描述字段
- `src/main/java/com/realtime/monitor/service/CdcTaskService.java`: 使用数据库存储
- `src/main/java/com/realtime/monitor/service/DataSourceService.java`: 使用数据库存储

## 注意事项

1. 确保 Oracle 数据库中的 `flink_user` schema 已创建
2. 确保 `flink_user` 用户有足够的权限
3. 数据库密码建议通过环境变量配置
4. 迁移前请备份现有的 JSON 配置文件
5. 生产环境建议定期备份数据库
