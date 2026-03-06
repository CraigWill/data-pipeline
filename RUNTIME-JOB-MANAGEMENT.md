# 运行时作业管理功能

## 概述

实现了完整的运行时作业管理系统，包括：
- 作业状态跟踪和持久化
- 异步 Job ID 获取
- 重启后自动恢复
- 资源检查（TaskManager 槽位）
- 表冲突检测
- 作业取消和删除

## 数据库表

### runtime_jobs 表

```sql
CREATE TABLE flink_user.runtime_jobs (
    id VARCHAR2(100) PRIMARY KEY,
    task_id VARCHAR2(100) NOT NULL,
    flink_job_id VARCHAR2(100),
    job_name VARCHAR2(200),
    status VARCHAR2(20) DEFAULT 'SUBMITTING',
    schema_name VARCHAR2(100),
    tables CLOB,
    parallelism NUMBER(3),
    submit_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    error_message VARCHAR2(2000),
    CONSTRAINT fk_runtime_task FOREIGN KEY (task_id) 
        REFERENCES flink_user.cdc_tasks(id) ON DELETE CASCADE
);
```

**状态说明**:
- `SUBMITTING`: 正在提交到 Flink
- `RUNNING`: 正在运行
- `FINISHED`: 已完成
- `FAILED`: 失败
- `CANCELED`: 已取消

## 核心功能

### 1. 作业提交验证

提交作业前会自动检查：

#### 1.1 TaskManager 槽位检查
```bash
# 检查是否有足够的槽位
GET /api/runtime-jobs/cluster/resources?requiredParallelism=4

# 响应
{
  "success": true,
  "data": {
    "hasAvailableSlots": false,
    "requiredParallelism": 4
  }
}
```

如果没有足够的槽位，提交会被拒绝。

#### 1.2 表冲突检测
```bash
# 检查表是否被其他作业使用
POST /api/runtime-jobs/check-conflicts
Content-Type: application/json

{
  "tables": ["IDS_ACCOUNT_INFO", "IDS_TRANS_INFO"]
}

# 响应
{
  "success": true,
  "data": {
    "hasConflicts": true,
    "conflicts": [
      {
        "id": "runtime-1772523359496",
        "taskId": "task-1772523359496",
        "jobName": "startall",
        "status": "RUNNING",
        "tables": ["IDS_ACCOUNT_INFO", "IDS_TRANS_INFO"]
      }
    ]
  }
}
```

如果有冲突，提交会被拒绝。

### 2. 异步 Job ID 获取

作业提交后，Flink Job ID 会异步获取并更新到数据库：

```java
// 1. 创建运行时作业记录（状态：SUBMITTING）
RuntimeJob runtimeJob = runtimeJobService.createRuntimeJob(config);

// 2. 提交到 Flink
Map<String, Object> result = embeddedCdcService.submitTask(config);

// 3. 异步更新 Flink Job ID（状态：RUNNING）
if (result.get("success") == Boolean.TRUE && result.containsKey("job_id")) {
    String jobId = (String) result.get("job_id");
    runtimeJobService.updateFlinkJobIdAsync(runtimeJob.getId(), jobId);
}
```

### 3. 重启后自动恢复

应用启动时会自动加载运行中的作业：

```java
@PostConstruct
public void loadRunningJobsOnStartup() {
    List<RuntimeJob> runningJobs = runtimeJobRepository.findRunningJobs();
    // 验证这些作业在 Flink 中的实际状态
    for (RuntimeJob job : runningJobs) {
        if (job.getFlinkJobId() != null) {
            syncJobStatus(job);
        }
    }
}
```

### 4. 定期状态同步

每 30 秒自动同步作业状态：

```java
@Scheduled(fixedDelay = 30000, initialDelay = 10000)
public void syncAllJobStatuses() {
    List<RuntimeJob> runningJobs = runtimeJobRepository.findRunningJobs();
    for (RuntimeJob job : runningJobs) {
        if (job.getFlinkJobId() != null) {
            syncJobStatus(job);
        }
    }
}
```

### 5. 作业删除

删除作业时会：
1. 取消 Flink 中的作业
2. 从数据库中删除记录

```bash
# 删除运行时作业
DELETE /api/runtime-jobs/{runtimeJobId}

# 响应
{
  "success": true,
  "message": "作业已取消并删除"
}
```

## API 端点

### 获取所有运行时作业
```bash
GET /api/runtime-jobs
```

### 获取运行中的作业
```bash
GET /api/runtime-jobs/running
```

### 获取单个作业
```bash
GET /api/runtime-jobs/{id}
```

### 取消并删除作业
```bash
DELETE /api/runtime-jobs/{id}
```

### 检查集群资源
```bash
GET /api/runtime-jobs/cluster/resources?requiredParallelism=4
```

### 检查表冲突
```bash
POST /api/runtime-jobs/check-conflicts
Content-Type: application/json

{
  "tables": ["TABLE1", "TABLE2"]
}
```

## 提交作业流程

### 1. 原有流程（已废弃）
```
提交任务 -> 直接提交到 Flink -> 更新状态
```

### 2. 新流程
```
提交任务
  ↓
验证槽位
  ↓
检查表冲突
  ↓
创建运行时作业记录（SUBMITTING）
  ↓
提交到 Flink
  ↓
异步更新 Flink Job ID（RUNNING）
  ↓
定期同步状态
```

## 错误处理

### 槽位不足
```json
{
  "success": false,
  "errors": ["没有足够的 TaskManager 槽位（需要 4 个）"]
}
```

### 表冲突
```json
{
  "success": false,
  "errors": ["以下作业正在处理相同的表: startall (ID: runtime-1772523359496)"],
  "conflicts": [...]
}
```

### 提交失败
运行时作业状态会更新为 `FAILED`，并记录错误信息。

## 数据库查询示例

### 查看所有运行时作业
```sql
SELECT id, task_id, flink_job_id, job_name, status, submit_time
FROM flink_user.runtime_jobs
ORDER BY submit_time DESC;
```

### 查看运行中的作业
```sql
SELECT id, job_name, status, flink_job_id
FROM flink_user.runtime_jobs
WHERE status IN ('SUBMITTING', 'RUNNING');
```

### 查看作业监控的表
```sql
SELECT id, job_name, tables
FROM flink_user.runtime_jobs
WHERE status = 'RUNNING';
```

## 配置

### 启用异步和调度
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class UnifiedApplication {
    // ...
}
```

### 调度配置
- 状态同步间隔: 30 秒
- 初始延迟: 10 秒

## 监控和维护

### 1. 检查运行中的作业
```bash
curl http://localhost:5001/api/runtime-jobs/running
```

### 2. 检查集群资源
```bash
curl "http://localhost:5001/api/runtime-jobs/cluster/resources?requiredParallelism=4"
```

### 3. 清理已完成的作业
```sql
DELETE FROM flink_user.runtime_jobs
WHERE status IN ('FINISHED', 'FAILED', 'CANCELED')
AND end_time < SYSDATE - 7;  -- 删除 7 天前的记录
```

### 4. 查看作业历史
```sql
SELECT 
    job_name,
    status,
    submit_time,
    start_time,
    end_time,
    EXTRACT(MINUTE FROM (end_time - start_time)) AS duration_minutes
FROM flink_user.runtime_jobs
WHERE status IN ('FINISHED', 'FAILED', 'CANCELED')
ORDER BY submit_time DESC;
```

## 故障排查

### 问题：作业状态未更新
**原因**: 调度器未启动或 Flink 连接失败

**解决**:
1. 检查应用日志
2. 验证 Flink REST API 可访问
3. 确认 `@EnableScheduling` 已启用

### 问题：Flink Job ID 为空
**原因**: 异步更新失败或作业提交失败

**解决**:
1. 查看应用日志中的错误信息
2. 检查 Flink JobManager 日志
3. 验证作业是否真的在 Flink 中运行

### 问题：槽位检查总是失败
**原因**: TaskManager 未启动或槽位配置不足

**解决**:
1. 检查 TaskManager 状态: `curl http://localhost:8081/taskmanagers`
2. 增加 TaskManager 数量或槽位数
3. 检查 `flink-conf.yaml` 中的 `taskmanager.numberOfTaskSlots`

## 相关文件

- `sql/setup-runtime-jobs-table.sql` - 数据库表创建脚本
- `src/main/java/com/realtime/monitor/dto/RuntimeJob.java` - DTO
- `src/main/java/com/realtime/monitor/repository/RuntimeJobRepository.java` - Repository
- `src/main/java/com/realtime/monitor/service/RuntimeJobService.java` - Service
- `src/main/java/com/realtime/monitor/controller/RuntimeJobController.java` - Controller
- `src/main/java/com/realtime/monitor/service/CdcTaskService.java` - 集成点

## 总结

新的运行时作业管理系统提供了：
- ✅ 完整的作业生命周期跟踪
- ✅ 异步 Job ID 获取
- ✅ 重启后自动恢复
- ✅ 资源检查（防止过载）
- ✅ 表冲突检测（防止数据不一致）
- ✅ 定期状态同步
- ✅ 作业取消和删除

这些功能确保了系统的稳定性和数据一致性。
