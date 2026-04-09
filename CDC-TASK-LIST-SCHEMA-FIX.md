# CDC 任务列表 Schema 显示修复

## 问题描述

1. **Schema 显示 N/A**：列表视图中 Schema 列显示 "N/A"
2. **无意义的状态**：显示"草稿"状态没有意义，因为这些是静态模板而非运行时任务

## 根本原因

### Schema 显示问题

后端 `CdcTaskService.listTasks()` 方法没有返回 `schema` 字段，导致前端无法显示。

### 状态显示问题

任务配置是静态模板，不是运行时任务，不应该有"草稿"、"运行中"等状态概念。

## 解决方案

### 1. 后端修改

在 `CdcTaskService.listTasks()` 方法中添加 schema 字段：

```java
// 添加 schema 字段
String schema = config.getSchema();
if (schema == null && config.getDatabase() != null) {
    schema = config.getDatabase().getSchema();
}
summary.put("schema", schema);
```

**逻辑**：
1. 首先尝试从 `config.getSchema()` 获取
2. 如果为空，从 `config.getDatabase().getSchema()` 获取
3. 如果都为空，返回 null（前端会显示为 "N/A"）

### 2. 前端修改

#### 移除状态列（列表视图）

**修改前**：
```html
<th>状态</th>
...
<td>
  <span class="status-badge status-draft">草稿</span>
</td>
```

**修改后**：
```html
<!-- 移除状态列 -->
```

#### 移除状态标签（卡片视图）

**修改前**：
```html
<div class="card-header">
  <div class="task-info">...</div>
  <div class="task-status">
    <span class="status-badge status-draft">草稿</span>
  </div>
</div>
```

**修改后**：
```html
<div class="card-header">
  <div class="task-info">...</div>
</div>
```

#### 移除状态相关样式

删除以下 CSS 类：
- `.task-status`
- `.status-badge`
- `.status-draft`

## 数据流

### 后端返回数据结构

```json
{
  "success": true,
  "data": [
    {
      "id": "task-1234567890",
      "name": "CDC Task 1",
      "database": "oracle-prod",
      "schema": "FINANCE_USER",  // 新增字段
      "tables": 5,
      "created": "2026-03-31T10:00:00"
    }
  ]
}
```

### 前端显示

**列表视图**：
| 任务名称 | 数据源 | Schema | 表数量 | 创建时间 | 操作 |
|---------|--------|--------|--------|---------|------|
| CDC Task 1 | oracle-prod | FINANCE_USER | 5 | 2026-03-31 10:00:00 | [按钮] |

**卡片视图**：
```
┌─────────────────────────────────┐
│ CDC Task 1                      │
├─────────────────────────────────┤
│ 📊 数据源: oracle-prod          │
│ 📋 表数量: 5                    │
│ 🕐 创建时间: 2026-03-31 10:00  │
├─────────────────────────────────┤
│ [提交] [详情] [删除]            │
└─────────────────────────────────┘
```

## 文件修改

### 后端文件

- `monitor-backend/src/main/java/com/realtime/monitor/service/CdcTaskService.java`
  - 修改 `listTasks()` 方法
  - 添加 schema 字段到返回的 Map

### 前端文件

- `monitor/frontend-vue/src/views/TaskListView.vue`
  - 移除列表视图的状态列
  - 移除卡片视图的状态标签
  - 删除状态相关样式

## 部署步骤

```bash
# 1. 构建后端
mvn clean package -DskipTests -pl monitor-backend -am

# 2. 构建前端
cd monitor/frontend-vue
npm run build

# 3. 构建 Docker 镜像
docker build -t realtime-pipeline-monitor-backend:latest -f monitor/Dockerfile .
docker build -t realtime-pipeline-monitor-frontend:latest -f monitor/frontend-vue/Dockerfile monitor/frontend-vue

# 4. 重启容器
docker-compose restart monitor-backend monitor-frontend
```

## 验证

### 1. 检查后端 API

```bash
curl -H "Authorization: Bearer <token>" http://localhost:5001/api/cdc/tasks
```

**预期响应**：
```json
{
  "success": true,
  "data": [
    {
      "id": "task-xxx",
      "name": "Task Name",
      "database": "oracle-prod",
      "schema": "FINANCE_USER",  // ✅ 应该有值
      "tables": 5,
      "created": "2026-03-31T10:00:00"
    }
  ]
}
```

### 2. 检查前端显示

访问 http://localhost:8888/tasks

**列表视图**：
- ✅ Schema 列显示实际值（如 "FINANCE_USER"）
- ✅ 没有状态列

**卡片视图**：
- ✅ 没有状态标签
- ✅ 卡片头部只有任务名称和描述

## 概念澄清

### 任务配置 vs 运行时作业

| 概念 | 说明 | 状态 |
|------|------|------|
| **任务配置（Task Config）** | 静态模板，定义了 CDC 任务的参数 | 无状态 |
| **运行时作业（Runtime Job）** | 提交到 Flink 的实际作业 | 有状态（运行中、失败、完成等） |

**关系**：
1. 用户创建任务配置（静态模板）
2. 用户提交任务配置到 Flink
3. 系统创建运行时作业记录
4. 运行时作业有状态（RUNNING, FAILED, FINISHED 等）

**页面对应**：
- `/tasks` - 任务配置列表（无状态）
- `/jobs` - 运行时作业列表（有状态）

## 未来改进

### 可能的增强

1. **显示提交次数**
   - 显示该任务配置被提交了多少次
   - 链接到相关的运行时作业

2. **最后提交时间**
   - 显示最后一次提交的时间
   - 帮助用户了解任务使用情况

3. **关联作业状态**
   - 显示基于此配置的最新作业状态
   - 快速了解任务是否正在运行

4. **标签系统**
   - 允许用户为任务添加标签
   - 便于分类和筛选

## 状态

✅ 已完成 - 后端和前端已重新构建并部署

## 访问地址

- 任务配置列表：http://localhost:8888/tasks
- 运行时作业列表：http://localhost:8888/jobs
