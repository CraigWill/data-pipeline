# Flink CDC 监控系统 - Vue 3 前端

基于 Vue 3 + Vite 的现代化前端应用。

## 技术栈

- **Vue 3** - 渐进式 JavaScript 框架
- **Vue Router 4** - 官方路由管理器
- **Pinia** - 状态管理
- **Axios** - HTTP 客户端
- **Vite** - 下一代前端构建工具

## 项目结构

```
frontend-vue/
├── src/
│   ├── api/              # API 接口封装
│   ├── assets/           # 静态资源
│   ├── components/       # 可复用组件
│   ├── router/           # 路由配置
│   ├── stores/           # Pinia 状态管理
│   ├── views/            # 页面视图
│   ├── App.vue           # 根组件
│   └── main.js           # 应用入口
├── index.html            # HTML 模板
├── package.json          # 依赖配置
├── vite.config.js        # Vite 配置
└── README.md             # 本文件
```

## 快速开始

### 1. 安装依赖

```bash
cd monitor/frontend-vue
npm install
```

### 2. 开发模式

```bash
npm run dev
```

访问 http://localhost:3000

### 3. 构建生产版本

```bash
npm run build
```

构建产物将输出到 `../frontend/dist` 目录。

### 4. 预览生产构建

```bash
npm run preview
```

## 功能特性

### 已实现

- ✅ 首页仪表盘（完整实现）
- ✅ 任务列表管理（完整实现）
- ✅ 数据源管理（完整 CRUD + 连接测试）
- ✅ 任务创建向导（4步向导流程）
- ✅ Flink 作业监控（实时刷新）
- ✅ 集群状态可视化（TaskManager 监控）
- ✅ 响应式布局
- ✅ API 接口封装
- ✅ 路由导航
- ✅ 错误处理和提示

### 待实现

- ⏳ 用户权限管理
- ⏳ 更多图表可视化
- ⏳ WebSocket 实时推送
- ⏳ 国际化支持

## 开发指南

### 添加新页面

1. 在 `src/views/` 创建新的 Vue 组件
2. 在 `src/router/index.js` 添加路由配置
3. 在导航菜单中添加链接

### 添加新 API

在 `src/api/index.js` 中添加新的 API 方法：

```javascript
export const myAPI = {
  list: () => api.get('/my-endpoint'),
  create: (data) => api.post('/my-endpoint', data)
}
```

### 组件开发规范

- 使用 Composition API (`<script setup>`)
- 组件名使用 PascalCase
- Props 使用 camelCase
- 事件名使用 kebab-case

## API 代理配置

开发环境下，API 请求会自动代理到后端服务：

```javascript
// vite.config.js
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:5001',
      changeOrigin: true
    }
  }
}
```

## 部署

### Docker 部署

更新 `monitor/Dockerfile` 以使用 Vue 构建产物：

```dockerfile
FROM node:18-alpine as builder
WORKDIR /app
COPY frontend-vue/package*.json ./
RUN npm install
COPY frontend-vue/ ./
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY frontend-vue/nginx.conf /etc/nginx/conf.d/default.conf
```

### Nginx 配置

```nginx
server {
  listen 80;
  root /usr/share/nginx/html;
  index index.html;

  location / {
    try_files $uri $uri/ /index.html;
  }

  location /api {
    proxy_pass http://monitor-backend:5001;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
  }
}
```

## 迁移说明

从原生 JavaScript 迁移到 Vue 3 的主要变化：

1. **组件化**: 页面拆分为可复用的 Vue 组件
2. **响应式数据**: 使用 Vue 的响应式系统管理状态
3. **路由管理**: 使用 Vue Router 进行页面导航
4. **API 封装**: 统一的 Axios 实例和拦截器
5. **构建工具**: 使用 Vite 替代传统构建方式

## 常见问题

**Q: 如何调试 API 请求？**

A: 打开浏览器开发者工具的 Network 标签，查看 XHR 请求。

**Q: 如何添加新的依赖？**

A: 使用 `npm install package-name` 安装依赖。

**Q: 如何处理跨域问题？**

A: 开发环境使用 Vite 代理，生产环境使用 Nginx 反向代理。

## 贡献指南

1. 遵循 Vue 3 最佳实践
2. 保持代码风格一致
3. 添加必要的注释
4. 测试新功能

## 许可证

与主项目相同
