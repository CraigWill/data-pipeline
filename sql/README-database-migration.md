# 数据库迁移指南

## 概述

将任务和数据源配置从 JSON 文件迁移到 Oracle 数据库的 `flink_user` schema。

## 优势

- **持久化**: 数据存储在数据库中，重启后不会丢失
- **并发安全**: 支持多实例并发访问
- **事务支持**: 保证数据一致性
- **查询能力**: 支持复杂查询和统计
- **自动恢复**: 重启后可以自动恢复运行中的任务

## 迁移步骤

### 1. 创建数据库表

在 Oracle 数据库中执行以下脚本创建元数据表：

```bash
sqlplus flink_user/password@localhost:1521/helowin @sql/setup-metadata-tables.sql
```

或者使用 Docker 容器：

```bash
docker exec -i oracle11g sqlplus flink_user/password@helowin < sql/setup-metadata-tables.sql
```

### 2. 迁移现有配置

使用 Python 脚本将现有的 JSON 配置迁移到数据库：

```bash
# 安装依赖
pip install cx_Oracle

# 执行迁移
python3 sql/migrate-json-to-db.py
```

环境变量配置（可选）：

```bash
export DB_HOST=localhost
export DB_PORT=1521
export DB_SID=helowin
export DB_USER=flink_user
export DB_PASSWORD=password

python3 sql/migrate-json-to-db.py
```

### 3. 配置应用程序

在 `docker-compose.yml` 中添加数据库连接配置：

```yaml
monitor-backend:
  environment:
    - DATABASE_HOST=host.docker.internal
    - DATABASE_PORT=1521
    - DATABASE_SID=helowin
    - DATABASE_USERNAME=flink_user
    - DATABASE_PASSWORD=password
```

### 4. 重新构建和部署

```bash
# 重新构建 JAR
mvn clean package -DskipTests

# 重新构建 Docker 镜像
docker-compose build monitor-backend

# 重启服务
docker-compose up -d monitor-backend
```

## 数据库表结构

### cdc_datasources (数据源配置表)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR2(100) | 主键，数据源唯一标识 |
| name | VARCHAR2(200) | 数据源名称 |
| host | VARCHAR2(200) | 数据库主机地址 |
| port | NUMBER(5) | 数据库端口 |
| username | VARCHAR2(100) | 数据库用户名 |
| password | VARCHAR2(200) | 数据库密码 |
| sid | VARCHAR2(100) | 数据库 SID |
| description | VARCHAR2(500) | 数据源描述 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

### cdc_tasks (任务配置表)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR2(100) | 主键，任务唯一标识 |
| name | VARCHAR2(200) | 任务名称 |
| datasource_id | VARCHAR2(100) | 关联的数据源ID |
| schema_name | VARCHAR2(100) | Oracle Schema 名称 |
| tables | CLOB | 表名列表（JSON 数组） |
| output_path | VARCHAR2(500) | 输出路径 |
| parallelism | NUMBER(3) | 并行度 |
| split_size | NUMBER(10) | 分片大小 |
| status | VARCHAR2(20) | 任务状态 |
| flink_job_id | VARCHAR2(100) | Flink 作业 ID |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

## 任务状态

- `CREATED`: 已创建，未提交
- `RUNNING`: 运行中
- `STOPPED`: 已停止
- `FAILED`: 失败

## 验证迁移

### 查询数据源

```sql
SELECT id, name, host, port, sid FROM flink_user.cdc_datasources;
```

### 查询任务

```sql
SELECT id, name, schema_name, status, flink_job_id 
FROM flink_user.cdc_tasks 
ORDER BY created_at DESC;
```

### 查询运行中的任务

```sql
SELECT id, name, schema_name, flink_job_id 
FROM flink_user.cdc_tasks 
WHERE status = 'RUNNING';
```

## 回滚方案

如果需要回滚到 JSON 文件方式：

1. 保留原有的 JSON 文件作为备份
2. 在代码中切换回使用文件系统的实现
3. 删除数据库表（可选）

```sql
DROP TABLE flink_user.cdc_tasks;
DROP TABLE flink_user.cdc_datasources;
```

## 自动恢复功能

迁移到数据库后，可以实现重启后自动恢复任务：

1. 在 `EmbeddedCdcService` 中添加 `@PostConstruct` 方法
2. 查询状态为 `RUNNING` 的任务
3. 自动重新提交这些任务

示例代码：

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
        }
    }
}
```

## 故障排查

### 连接失败

检查数据库连接配置：

```bash
# 测试连接
sqlplus flink_user/password@localhost:1521/helowin
```

### 表不存在

确认表已创建：

```sql
SELECT table_name FROM all_tables WHERE owner = 'FLINK_USER';
```

### 权限问题

确认用户权限：

```sql
GRANT SELECT, INSERT, UPDATE, DELETE ON flink_user.cdc_datasources TO flink_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON flink_user.cdc_tasks TO flink_user;
```

## 注意事项

1. 迁移前请备份现有的 JSON 配置文件
2. 确保 `flink_user` schema 已创建并有足够权限
3. 数据库密码建议使用环境变量配置，不要硬编码
4. 生产环境建议使用连接池（HikariCP 已配置）
5. 定期备份数据库中的配置数据
