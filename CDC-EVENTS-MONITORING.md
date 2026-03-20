# 📊 CDC 事件监控功能

## 功能概述

CDC 事件监控页面提供实时查看和分析数据变更捕获（CDC）事件的能力，帮助用户了解数据同步状态和变更详情。

## 部署时间
**2026-03-09 10:32**

## 功能特性

### 📈 实时统计

- **总事件数**: 显示捕获的所有 CDC 事件总数
- **INSERT 事件**: 新增数据记录数量
- **UPDATE 事件**: 更新数据记录数量
- **DELETE 事件**: 删除数据记录数量

### 🔍 智能过滤

- **表名筛选**: 按数据库表名过滤事件
- **事件类型**: 按 INSERT/UPDATE/DELETE 类型过滤
- **自动刷新**: 支持 5 秒自动刷新，实时监控数据变化

### 📋 事件详情

每个事件卡片显示：
- **事件类型**: INSERT/UPDATE/DELETE（带颜色标识）
- **表名**: 数据来源表
- **时间戳**: 事件发生时间
- **变更数据**: JSON 格式显示变更后的数据
- **变更前数据**: UPDATE/DELETE 事件显示变更前的数据
- **数据源**: 事件来源文件

### 📄 分页浏览

- 每页显示 20 条事件
- 支持上一页/下一页导航
- 显示当前页码和总页数

## 访问地址

### CDC 事件监控页面
http://localhost:8888/events

## 使用方法

### 1. 登录系统

访问 http://localhost:8888/login 使用账号登录

### 2. 进入 CDC 事件页面

点击导航栏的 "CDC事件" 或直接访问 http://localhost:8888/events

### 3. 查看事件统计

页面顶部显示四个统计卡片：
- 总事件数
- INSERT 事件数
- UPDATE 事件数
- DELETE 事件数

### 4. 过滤事件

使用过滤器筛选特定事件：

```
表名筛选: 选择特定表（如 IDS_ACCOUNT_INFO）
事件类型: 选择 INSERT/UPDATE/DELETE
自动刷新: 勾选启用 5 秒自动刷新
```

### 5. 查看事件详情

每个事件卡片包含：
- 事件类型标签（绿色=INSERT，蓝色=UPDATE，红色=DELETE）
- 表名和时间戳
- 变更数据的 JSON 格式展示
- 变更前数据（如果有）

### 6. 分页浏览

使用底部分页控件：
- 点击"上一页"查看之前的事件
- 点击"下一页"查看更多事件
- 查看当前页码和总页数

## 技术实现

### 前端组件

**文件**: `monitor/frontend-vue/src/views/CdcEventsView.vue`

主要功能：
- 事件列表展示
- 实时统计卡片
- 过滤器组件
- 自动刷新机制
- 分页控件

### 后端 API

**控制器**: `src/main/java/com/realtime/monitor/controller/CdcEventsController.java`

提供的 API 端点：
- `GET /api/cdc/events` - 获取事件列表
- `GET /api/cdc/events/tables` - 获取表列表
- `GET /api/cdc/events/stats` - 获取统计信息

**服务**: `src/main/java/com/realtime/monitor/service/CdcEventsService.java`

核心功能：
- 扫描输出目录中的 CSV 文件
- 解析 CSV 文件为事件对象
- 计算统计信息
- 支持过滤和分页

### 数据来源

CDC 事件数据来自 Flink CDC 作业输出的 CSV 文件：

```
output/cdc/
├── 2026-03-09--10/
│   ├── IDS_ACCOUNT_INFO_20260309_100000-uuid-0.csv
│   ├── IDS_TRANS_INFO_20260309_100000-uuid-0.csv
│   └── ...
└── ...
```

文件命名格式：
```
{TABLE_NAME}_{TIMESTAMP}-{UUID}-{PARTITION}.csv
```

## API 使用示例

### 获取事件列表

```bash
curl http://localhost:5001/api/cdc/events?table=IDS_ACCOUNT_INFO&page=1&size=20
```

响应：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "events": [
      {
        "id": "uuid",
        "tableName": "IDS_ACCOUNT_INFO",
        "eventType": "INSERT",
        "data": {
          "ACCOUNT_ID": "12345",
          "ACCOUNT_NAME": "张三",
          "BALANCE": "1000.00"
        },
        "timestamp": 1709956800000,
        "source": "IDS_ACCOUNT_INFO_20260309_100000-uuid-0.csv"
      }
    ],
    "stats": {
      "totalEvents": 150,
      "insertEvents": 150,
      "updateEvents": 0,
      "deleteEvents": 0
    },
    "total": 150,
    "totalPages": 8,
    "currentPage": 1
  }
}
```

### 获取表列表

```bash
curl http://localhost:5001/api/cdc/events/tables
```

响应：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    "IDS_ACCOUNT_INFO",
    "IDS_TRANS_INFO"
  ]
}
```

### 获取统计信息

```bash
curl http://localhost:5001/api/cdc/events/stats
```

响应：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "totalEvents": 300,
    "insertEvents": 300,
    "updateEvents": 0,
    "deleteEvents": 0
  }
}
```

## 界面截图说明

### 统计卡片
```
┌─────────────┬─────────────┬─────────────┬─────────────┐
│ 📝 总事件数  │ ➕ INSERT   │ ✏️ UPDATE   │ 🗑️ DELETE   │
│    300      │    300      │     0       │     0       │
└─────────────┴─────────────┴─────────────┴─────────────┘
```

### 过滤器
```
表名筛选: [全部表 ▼]  事件类型: [全部类型 ▼]  ☑ 自动刷新(5秒)  [🔄 刷新]
```

### 事件卡片
```
┌────────────────────────────────────────────────────────┐
│ [INSERT] IDS_ACCOUNT_INFO          2026-03-09 10:00:00 │
├────────────────────────────────────────────────────────┤
│ 变更数据:                                               │
│ {                                                       │
│   "ACCOUNT_ID": "12345",                               │
│   "ACCOUNT_NAME": "张三",                              │
│   "BALANCE": "1000.00"                                 │
│ }                                                       │
├────────────────────────────────────────────────────────┤
│ 来源: IDS_ACCOUNT_INFO_20260309_100000-uuid-0.csv      │
│ ID: abc-123-def                                        │
└────────────────────────────────────────────────────────┘
```

## 性能优化

### 文件扫描限制

- 最多扫描 100 个 CSV 文件
- 每个文件最多读取 1000 行
- 按文件修改时间倒序排列

### 自动刷新

- 默认 5 秒刷新间隔
- 可手动关闭自动刷新
- 刷新时保持当前过滤条件

### 分页加载

- 每页 20 条记录
- 减少内存占用
- 提高页面响应速度

## 故障排查

### 问题 1: 没有显示事件

**可能原因**:
- 输出目录不存在
- 没有 CSV 文件
- Flink 作业未运行

**解决方法**:
```bash
# 检查输出目录
ls -la output/cdc/

# 检查 Flink 作业状态
curl http://localhost:8081/jobs

# 查看后端日志
docker-compose logs monitor-backend
```

### 问题 2: 统计数据不准确

**可能原因**:
- CSV 文件格式错误
- 文件解析失败

**解决方法**:
```bash
# 查看后端日志
docker-compose logs monitor-backend | grep -i error

# 检查 CSV 文件格式
head -5 output/cdc/2026-03-09--10/*.csv
```

### 问题 3: 自动刷新不工作

**可能原因**:
- 浏览器标签页不活跃
- JavaScript 错误

**解决方法**:
```javascript
// 打开浏览器控制台检查错误
// F12 -> Console

// 手动刷新页面
location.reload()
```

## 扩展功能建议

### 短期改进

- [ ] 支持实时 WebSocket 推送
- [ ] 添加事件搜索功能
- [ ] 支持导出事件数据
- [ ] 添加事件详情弹窗

### 长期规划

- [ ] 事件回放功能
- [ ] 数据对比工具
- [ ] 告警规则配置
- [ ] 事件统计图表
- [ ] 数据质量监控

## 相关文档

- [登录功能文档](monitor/frontend-vue/LOGIN-FEATURE.md)
- [部署成功报告](DEPLOYMENT-SUCCESS.md)
- [Docker 部署指南](DOCKER-DEPLOYMENT.md)
- [Vue 3 迁移报告](monitor/frontend-vue/VUE-MIGRATION-COMPLETE.md)

## 更新日志

### 2026-03-09 10:32

- ✅ 创建 CDC 事件监控页面
- ✅ 实现事件列表展示
- ✅ 添加统计卡片
- ✅ 实现过滤功能
- ✅ 添加自动刷新
- ✅ 实现分页功能
- ✅ 创建后端 API 控制器
- ✅ 实现 CSV 文件解析服务
- ✅ 更新导航栏菜单
- ✅ 重新构建和部署

---

**创建时间**: 2026-03-09 10:32  
**功能状态**: ✅ 已上线  
**访问地址**: http://localhost:8888/events

🎉 CDC 事件监控功能已成功上线！
