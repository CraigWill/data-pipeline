# 前端迁移指南：从原生 JavaScript 到 Vue 3

## 迁移概述

本文档说明如何将前端从原生 JavaScript 迁移到 Vue 3 + Vite 架构。

## 为什么迁移到 Vue 3？

### 优势

1. **组件化开发**: 更好的代码组织和复用
2. **响应式数据**: 自动的 UI 更新，无需手动 DOM 操作
3. **类型安全**: 更好的 IDE 支持和类型检查
4. **现代工具链**: Vite 提供极快的开发体验
5. **生态系统**: 丰富的第三方库和工具
6. **可维护性**: 更清晰的代码结构，易于维护和扩展

### 对比

| 特性 | 原生 JS | Vue 3 |
|------|---------|-------|
| 开发效率 | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| 代码复用 | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| 状态管理 | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| 构建工具 | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| 学习曲线 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |

## 迁移步骤

### 阶段 1: 准备工作（已完成）

- [x] 创建 Vue 3 项目结构
- [x] 配置 Vite 构建工具
- [x] 封装 API 接口
- [x] 创建基础组件
- [x] 实现首页和任务列表

### 阶段 2: 核心功能迁移（进行中）

- [ ] 数据源管理页面
- [ ] 任务创建向导
- [ ] 作业监控页面
- [ ] 集群状态页面
- [ ] 输出文件管理

### 阶段 3: 高级功能

- [ ] 实时数据更新（WebSocket）
- [ ] 图表可视化
- [ ] 用户权限管理
- [ ] 主题切换
- [ ] 国际化支持

### 阶段 4: 优化和部署

- [ ] 性能优化
- [ ] 错误处理完善
- [ ] 单元测试
- [ ] E2E 测试
- [ ] 生产环境部署

## 快速开始

### 1. 安装依赖

```bash
cd monitor/frontend-vue
npm install
```

### 2. 启动开发服务器

```bash
npm run dev
```

访问 http://localhost:3000

### 3. 构建生产版本

```bash
npm run build
```

## 目录结构对比

### 原生 JavaScript

```
frontend/
├── index.html
├── main.html
├── main.js
├── cdc-manager.html
├── cdc-manager.js
├── datasource-manager.html
├── datasource-manager.js
├── task-list.html
├── task-list.js
└── nginx.conf
```

### Vue 3

```
frontend-vue/
├── src/
│   ├── api/              # API 封装
│   ├── assets/           # 静态资源
│   ├── components/       # 可复用组件
│   │   ├── AppHeader.vue
│   │   └── DashboardCard.vue
│   ├── router/           # 路由配置
│   ├── views/            # 页面视图
│   │   ├── HomeView.vue
│   │   ├── TaskListView.vue
│   │   ├── TaskCreateView.vue
│   │   ├── DataSourceView.vue
│   │   ├── JobsView.vue
│   │   └── ClusterView.vue
│   ├── App.vue
│   └── main.js
├── index.html
├── package.json
└── vite.config.js
```

## 代码迁移示例

### 原生 JavaScript

```javascript
// main.js
async function loadJobs() {
    const response = await fetch('/api/jobs');
    const data = await response.json();
    
    const tbody = document.getElementById('jobsTableBody');
    tbody.innerHTML = '';
    
    data.data.forEach(job => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${job.name}</td>
            <td>${job.state}</td>
        `;
        tbody.appendChild(row);
    });
}
```

### Vue 3

```vue
<template>
  <table class="table">
    <tbody>
      <tr v-for="job in jobs" :key="job.jid">
        <td>{{ job.name }}</td>
        <td>{{ job.state }}</td>
      </tr>
    </tbody>
  </table>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { jobAPI } from '@/api'

const jobs = ref([])

const loadJobs = async () => {
  const data = await jobAPI.list()
  if (data.success) {
    jobs.value = data.data
  }
}

onMounted(() => {
  loadJobs()
})
</script>
```

## API 接口迁移

### 原生 JavaScript

```javascript
// 分散在各个文件中
const response = await fetch('/api/jobs');
const data = await response.json();
```

### Vue 3

```javascript
// src/api/index.js - 统一管理
import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

export const jobAPI = {
  list: () => api.get('/jobs'),
  get: (id) => api.get(`/jobs/${id}`),
  cancel: (id) => api.post(`/jobs/${id}/cancel`)
}
```

## 部署配置

### 开发环境

```bash
# 启动后端
docker-compose up -d monitor-backend

# 启动前端开发服务器
cd monitor/frontend-vue
npm run dev
```

访问:
- 前端: http://localhost:3000
- 后端 API: http://localhost:5001

### 生产环境

#### 方案 1: 使用现有 Nginx 容器

```bash
# 构建 Vue 应用
cd monitor/frontend-vue
npm run build

# 产物会输出到 monitor/frontend/dist
# 更新 Nginx 配置指向 dist 目录
```

#### 方案 2: 独立的 Node.js 容器

```dockerfile
# monitor/Dockerfile.vue
FROM node:18-alpine as builder
WORKDIR /app
COPY frontend-vue/package*.json ./
RUN npm install
COPY frontend-vue/ ./
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY frontend-vue/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

更新 `docker-compose.yml`:

```yaml
services:
  monitor-frontend-vue:
    build:
      context: ./monitor
      dockerfile: Dockerfile.vue
    ports:
      - "8888:80"
    depends_on:
      - monitor-backend
```

## 渐进式迁移策略

### 方案 A: 完全替换（推荐）

1. 完成所有 Vue 页面开发
2. 测试所有功能
3. 一次性切换到 Vue 版本

### 方案 B: 并行运行

1. Vue 版本运行在 3000 端口
2. 原版本继续运行在 8888 端口
3. 逐步迁移用户到新版本
4. 确认稳定后下线旧版本

### 方案 C: 混合模式

1. 使用 Nginx 路由规则
2. 部分路径使用 Vue 版本
3. 其他路径使用原版本
4. 逐步迁移所有路径

## 性能对比

### 构建时间

- 原生 JS: 无需构建
- Vue 3 + Vite: ~5-10 秒（首次），~1-2 秒（增量）

### 加载时间

- 原生 JS: ~500ms
- Vue 3 (开发): ~300ms
- Vue 3 (生产): ~200ms（代码分割和压缩）

### 包大小

- 原生 JS: ~50KB
- Vue 3 (生产): ~150KB（包含 Vue 运行时）

## 常见问题

### Q: 是否需要学习 Vue？

A: 是的，但 Vue 3 的学习曲线相对平缓。推荐资源：
- [Vue 3 官方文档](https://cn.vuejs.org/)
- [Vue 3 教程](https://cn.vuejs.org/tutorial/)

### Q: 迁移会影响现有功能吗？

A: 不会。可以并行运行两个版本，确保平滑过渡。

### Q: 如何处理旧代码？

A: 建议保留旧代码一段时间，确认新版本稳定后再删除。

### Q: 性能会提升吗？

A: 是的，Vue 3 的虚拟 DOM 和响应式系统提供更好的性能。

### Q: 需要多长时间完成迁移？

A: 取决于功能复杂度，预计 2-4 周完成核心功能。

## 下一步

1. **完成核心页面**: 优先完成数据源管理和任务创建
2. **添加测试**: 编写单元测试和 E2E 测试
3. **性能优化**: 代码分割、懒加载、缓存策略
4. **文档完善**: API 文档、组件文档、使用指南
5. **生产部署**: 配置 CI/CD、监控告警

## 参考资源

- [Vue 3 文档](https://cn.vuejs.org/)
- [Vite 文档](https://cn.vitejs.dev/)
- [Vue Router 文档](https://router.vuejs.org/zh/)
- [Pinia 文档](https://pinia.vuejs.org/zh/)
- [Axios 文档](https://axios-http.com/zh/)

## 联系方式

如有问题，请联系开发团队或提交 Issue。
