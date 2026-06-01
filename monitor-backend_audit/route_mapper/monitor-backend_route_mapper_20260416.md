# monitor-backend 路由映射报告

**项目**: monitor-backend  
**扫描日期**: 2026-04-16  
**扫描工具**: java-route-mapper  
**审计员**: Java 安全审计专家

---

## 1. 路由总览

共发现 **10 个 Controller**，**52 个 HTTP 端点**。

---

## 2. 完整路由映射表

### 2.1 AuthController (`/api/auth`)

| 方法 | 路径 | 参数 | 认证要求 | 说明 |
|------|------|------|----------|------|
| POST | `/api/auth/login` | Body: `{username, password}` | **公开** | 用户登录，返回 JWT |
| POST | `/api/auth/logout` | 无 | **公开** (antMatchers) | 清除 SecurityContext |
| GET | `/api/auth/me` | 无 | **公开** (antMatchers) | 获取当前用户信息 |

> ⚠️ **安全问题**: `/api/auth/logout` 和 `/api/auth/me` 被 `antMatchers("/api/auth/**").permitAll()` 全部放开，`/api/auth/me` 应当要求认证。

---

### 2.2 AdminController (`/api/admin`)

| 方法 | 路径 | 参数 | 认证要求 | 说明 |
|------|------|------|----------|------|
| POST | `/api/admin/datasources/reencrypt` | 无 | 需认证 (`/api/**`) | 重新加密所有数据源密码 |

> ⚠️ **安全问题**: 该端点执行敏感的密码重加密操作，但仅要求"已认证"，未限制 ROLE_ADMIN 角色。任何已登录用户均可调用。

---

### 2.3 CdcTaskController (`/api/cdc`)

| 方法 | 路径 | 参数 | 认证要求 | 说明 |
|------|------|------|----------|------|
| POST | `/api/cdc/datasource/test` | Body: `DataSourceConfig` | 需认证 | 测试数据源连接 |
| POST | `/api/cdc/datasource/schemas` | Body: `DataSourceConfig` | 需认证 | 获取 Schema 列表 |
| POST | `/api/cdc/datasource/tables` | Body: `{config, schema}` | 需认证 | 获取表列表 |
| GET | `/api/cdc/tasks` | 无 | 需认证 | 列出所有任务 |
| POST | `/api/cdc/tasks` | Body: `TaskConfig` | 需认证 | 创建任务 |
| GET | `/api/cdc/tasks/{taskId}` | Path: `taskId` | 需认证 | 获取任务配置 |
| GET | `/api/cdc/tasks/{taskId}/detail` | Path: `taskId` | 需认证 | 获取任务详情 |
| DELETE | `/api/cdc/tasks/{taskId}` | Path: `taskId` | 需认证 | 删除任务 |
| POST | `/api/cdc/tasks/{taskId}/submit` | Path: `taskId` | 需认证 | 提交任务到 Flink |
| GET | `/api/cdc/jobs` | 无 | 需认证 | 获取运行中作业 |
| GET | `/api/cdc/jobs/{jobId}` | Path: `jobId` | 需认证 | 获取作业状态 |
| POST | `/api/cdc/jobs/{jobId}/cancel` | Path: `jobId` | 需认证 | 取消作业 |
| POST | `/api/cdc/submit` | Body: `CdcSubmitRequest` | 需认证 | 直接提交 CDC 任务 |

---

### 2.4 CdcEventsController (`/api/cdc/events`)

| 方法 | 路径 | 参数 | 认证要求 | 说明 |
|------|------|------|----------|------|
| GET | `/api/cdc/events` | Query: `table, eventType, page, size` | 需认证 | 获取 CDC 事件列表 |
| GET | `/api/cdc/events/tables` | 无 | 需认证 | 获取表列表 |
| GET | `/api/cdc/events/stats` | 无 | 需认证 | 获取事件统计 |
| GET | `/api/cdc/events/stats/today` | 无 | 需认证 | 获取当日统计 |
| GET | `/api/cdc/events/stats/daily` | Query: `table` | 需认证 | 获取24小时分布统计 |
| GET | `/api/cdc/events/stream` | 无 | 需认证 | SSE 实时数据流 |
| POST | `/api/cdc/events/record` | Query: `tableName, eventType, count` | 需认证 | 手动记录事件 |
| GET | `/api/cdc/events/files` | Query: `date` | 需认证 | 获取文件列表 |
| GET | `/api/cdc/events/files/content` | Query: `path, page, size` | 需认证 | 获取文件内容 |
| GET | `/api/cdc/events/dates` | 无 | 需认证 | 获取可用日期列表 |

> ⚠️ **安全问题**: `/api/cdc/events/files/content?path=` 接受用户传入的文件路径，存在路径遍历风险（见 SQL 审计报告）。

---

### 2.5 ClusterController (`/api/cluster`)

| 方法 | 路径 | 参数 | 认证要求 | 说明 |
|------|------|------|----------|------|
| GET | `/api/cluster/overview` | 无 | 需认证 | 获取集群概览 |
| GET | `/api/cluster/taskmanagers` | 无 | 需认证 | 获取 TaskManager 列表 |
| GET | `/api/cluster/jobmanagers` | 无 | 需认证 | 获取 JobManager 配置 |
| GET | `/api/cluster/jobs` | 无 | 需认证 | 获取作业列表 |

---

### 2.6 DataSourceController (`/api/datasources`)

| 方法 | 路径 | 参数 | 认证要求 | 说明 |
|------|------|------|----------|------|
| GET | `/api/datasources` | 无 | 需认证 | 列出所有数据源 |
| POST | `/api/datasources` | Body: `DataSourceConfig` | 需认证 | 创建数据源 |
| GET | `/api/datasources/{dsId}` | Path: `dsId` | 需认证 | 获取数据源详情 |
| PUT | `/api/datasources/{dsId}` | Path: `dsId`, Body: `DataSourceConfig` | 需认证 | 更新数据源 |
| DELETE | `/api/datasources/{dsId}` | Path: `dsId` | 需认证 | 删除数据源 |
| POST | `/api/datasources/{dsId}/test` | Path: `dsId` | 需认证 | 测试数据源连接 |
| GET | `/api/datasources/{dsId}/schemas` | Path: `dsId` | 需认证 | 获取 Schema 列表 |
| GET | `/api/datasources/{dsId}/schemas/{schema}/tables` | Path: `dsId, schema` | 需认证 | 获取表列表 |

---

### 2.7 HealthController (`/api`)

| 方法 | 路径 | 参数 | 认证要求 | 说明 |
|------|------|------|----------|------|
| GET | `/api/health` | 无 | 需认证 (`/api/**`) | 健康检查 |
| GET | `/api/system/info` | 无 | 需认证 | 系统信息（含 JVM 信息） |

> ⚠️ **安全问题**: `/api/health` 被 `/api/**` 规则要求认证，但 `/actuator/health` 是公开的。`/api/system/info` 暴露 JVM 内存、Java 版本、Flink URL 等敏感信息，应限制为管理员访问。

---

### 2.8 JobController (`/api/jobs`)

| 方法 | 路径 | 参数 | 认证要求 | 说明 |
|------|------|------|----------|------|
| GET | `/api/jobs` | 无 | 需认证 | 获取作业列表 |
| GET | `/api/jobs/{jobId}` | Path: `jobId` | 需认证 | 获取作业详情 |
| GET | `/api/jobs/{jobId}/metrics` | Path: `jobId` | 需认证 | 获取作业指标 |
| POST | `/api/jobs/{jobId}/cancel` | Path: `jobId` | 需认证 | 取消作业 |
| POST | `/api/jobs/{jobId}/stop` | Path: `jobId`, Query: `targetDirectory` | 需认证 | 带 Savepoint 停止作业 |
| GET | `/api/jobs/{jobId}/checkpoints` | Path: `jobId` | 需认证 | 获取 Checkpoint 列表 |

> ⚠️ **安全问题**: `/api/jobs/{jobId}/stop?targetDirectory=` 接受用户传入的目录路径，存在路径注入风险。

---

### 2.9 OutputController (`/api/output`)

| 方法 | 路径 | 参数 | 认证要求 | 说明 |
|------|------|------|----------|------|
| GET | `/api/output/stats` | 无 | 需认证 | 获取输出统计 |
| GET | `/api/output/files` | Query: `table, limit` | 需认证 | 获取输出文件列表 |

---

### 2.10 RuntimeJobController (`/api/runtime-jobs`)

| 方法 | 路径 | 参数 | 认证要求 | 说明 |
|------|------|------|----------|------|
| GET | `/api/runtime-jobs` | 无 | 需认证 | 获取所有运行时作业 |
| GET | `/api/runtime-jobs/running` | 无 | 需认证 | 获取运行中作业 |
| GET | `/api/runtime-jobs/{id}` | Path: `id` | 需认证 | 获取单个作业 |
| DELETE | `/api/runtime-jobs/{id}` | Path: `id` | 需认证 | 取消并删除作业 |
| GET | `/api/runtime-jobs/cluster/resources` | Query: `requiredParallelism` | 需认证 | 检查集群资源 |
| POST | `/api/runtime-jobs/check-conflicts` | Body: `{tables}` | 需认证 | 检查表冲突 |

---

## 3. Spring Security 路由分类

### 3.1 公开路由（无需认证）

```
/api/auth/**          → permitAll()
/actuator/health      → permitAll()
```

### 3.2 受保护路由（需认证）

```
/api/**               → authenticated()
```

### 3.3 其他路由

```
anyRequest()          → permitAll()   ⚠️ 非 /api/** 路径全部公开
```

---

## 4. 路由安全问题汇总

| 编号 | 路径 | 问题类型 | 风险等级 |
|------|------|----------|----------|
| R-001 | `/api/auth/me` | 公开端点泄露用户信息 | 中 |
| R-002 | `/api/admin/datasources/reencrypt` | 缺少角色限制 | **高** |
| R-003 | `/api/system/info` | 敏感信息暴露 | 中 |
| R-004 | `/api/cdc/events/files/content?path=` | 路径遍历 | **高** |
| R-005 | `/api/jobs/{jobId}/stop?targetDirectory=` | 路径注入 | 中 |
| R-006 | `anyRequest().permitAll()` | 非 API 路径全部公开 | 低 |
