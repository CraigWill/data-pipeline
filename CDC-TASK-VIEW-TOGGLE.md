# CDC 任务管理 - 视图切换功能

## 功能概述

为 CDC 任务管理页面添加了卡片视图和列表视图的切换功能，用户可以根据自己的偏好选择不同的展示方式。

## 功能特性

### 1. 视图切换按钮

在页面头部添加了视图切换控件：
- 卡片视图按钮（网格图标）
- 列表视图按钮（列表图标）
- 当前激活的视图按钮会高亮显示（蓝色背景）
- 悬停效果提供视觉反馈

### 2. 卡片视图（默认）

**特点**：
- 响应式网格布局
- 每个任务显示为独立卡片
- 包含任务图标和状态标签
- 信息分组展示（数据源、表数量、创建时间）
- 底部操作按钮（提交、详情、删除）
- 适合浏览和快速识别任务

**布局**：
- 自适应列数（根据屏幕宽度）
- 最小卡片宽度：380px
- 卡片间距：20px
- 悬停效果：阴影加深 + 轻微上移

### 3. 列表视图

**特点**：
- 表格形式展示
- 信息密度更高
- 便于对比和排序
- 紧凑的操作按钮
- 适合管理大量任务

**列结构**：
| 列名 | 说明 | 宽度 |
|------|------|------|
| 任务名称 | 显示名称和描述 | 自适应 |
| 数据源 | 数据源名称 | 自适应 |
| Schema | 高亮显示 | 自适应 |
| 表数量 | 监控表的数量 | 自适应 |
| 创建时间 | 格式化时间 | 自适应 |
| 状态 | 状态标签 | 自适应 |
| 操作 | 操作按钮 | 140px |

**交互效果**：
- 行悬停高亮（灰色背景）
- 紧凑的图标按钮
- 按钮悬停效果

## 技术实现

### 状态管理

```javascript
const viewMode = ref('card') // 'card' 或 'list'
```

### 视图切换逻辑

```vue
<button 
  :class="['toggle-btn', { active: viewMode === 'card' }]" 
  @click="viewMode = 'card'"
>
  <!-- 卡片图标 -->
</button>
```

### 条件渲染

```vue
<!-- 卡片视图 -->
<div v-if="viewMode === 'card'" class="tasks-grid">
  <!-- 卡片内容 -->
</div>

<!-- 列表视图 -->
<div v-else class="tasks-table-container">
  <!-- 表格内容 -->
</div>
```

## 样式设计

### 视图切换按钮

```css
.view-toggle {
  display: flex;
  background: #fff;
  border-radius: 6px;
  padding: 4px;
  box-shadow: 0 1px 3px rgba(9,30,66,0.12);
  border: 1px solid #DFE1E6;
}

.toggle-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  color: #5E6C84;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.15s ease;
}

.toggle-btn.active {
  background: #0052CC;
  color: #fff;
}
```

### 列表视图表格

```css
.tasks-table {
  width: 100%;
  border-collapse: collapse;
}

.tasks-table thead {
  background: #F4F5F7;
  border-bottom: 2px solid #DFE1E6;
}

.tasks-table tbody tr:hover {
  background: #F4F5F7;
}
```

## 响应式设计

### 移动端适配

- 卡片视图：单列布局
- 列表视图：横向滚动
- 视图切换按钮：保持可见
- 头部操作：垂直堆叠

```css
@media (max-width: 768px) {
  .tasks-grid {
    grid-template-columns: 1fr;
  }
  
  .tasks-table-container {
    overflow-x: auto;
  }
  
  .tasks-table {
    min-width: 800px;
  }
}
```

## 用户体验优化

### 1. 视觉一致性

- 两种视图使用相同的颜色方案
- 状态标签样式保持一致
- 操作按钮图标相同

### 2. 交互反馈

- 按钮悬停效果
- 激活状态明确
- 平滑过渡动画

### 3. 信息展示

- 卡片视图：视觉化、易浏览
- 列表视图：信息密集、易对比

### 4. 操作便捷性

- 卡片视图：大按钮，易点击
- 列表视图：紧凑按钮，节省空间

## 使用场景

### 卡片视图适合：

- 任务数量较少（< 20）
- 需要快速浏览和识别
- 关注任务的整体信息
- 移动设备使用

### 列表视图适合：

- 任务数量较多（> 20）
- 需要对比多个任务
- 关注特定字段（如 Schema）
- 桌面设备使用

## 文件修改

### 前端文件

- `monitor/frontend-vue/src/views/TaskListView.vue`
  - 添加视图切换按钮
  - 添加列表视图 HTML 结构
  - 添加 viewMode 状态
  - 添加列表视图样式
  - 更新响应式样式

## 部署

```bash
# 1. 构建前端
cd monitor/frontend-vue
npm run build

# 2. 构建 Docker 镜像
docker build -t realtime-pipeline-monitor-frontend:latest -f Dockerfile .

# 3. 重启容器
docker-compose restart monitor-frontend
```

## 效果展示

### 卡片视图

- 网格布局，每行 3-4 个卡片
- 卡片包含完整信息和操作按钮
- 悬停效果：阴影 + 上移

### 列表视图

- 表格布局，信息密集
- 表头固定，内容可滚动
- 行悬停高亮
- 紧凑的操作按钮

### 视图切换

- 右上角切换按钮
- 激活状态蓝色高亮
- 即时切换，无需刷新

## 未来改进

### 可能的增强功能

1. **记住用户偏好**
   - 使用 localStorage 保存视图选择
   - 下次访问时自动恢复

2. **列表视图排序**
   - 点击表头排序
   - 支持升序/降序

3. **列表视图筛选**
   - 按数据源筛选
   - 按状态筛选
   - 搜索任务名称

4. **批量操作**
   - 列表视图支持多选
   - 批量提交/删除

5. **自定义列**
   - 用户可选择显示/隐藏列
   - 调整列顺序

## 浏览器兼容性

- Chrome/Edge: ✅
- Firefox: ✅
- Safari: ✅
- 移动浏览器: ✅

## 状态

✅ 已完成 - 前端已重新构建并部署

## 访问地址

http://localhost:8888/tasks
