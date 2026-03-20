# Vue 3 前端 Dockerfile
# 多阶段构建：构建阶段 + 运行阶段

# ============================================
# 阶段 1: 构建 Vue 应用
# ============================================
FROM node:18-alpine AS builder

# 设置工作目录
WORKDIR /app

# 复制 package.json 和 package-lock.json
COPY package*.json ./

# 安装所有依赖（包括 devDependencies，构建需要）
RUN npm ci

# 复制源代码
COPY . .

# 构建生产版本
RUN npm run build

# ============================================
# 阶段 2: Nginx 运行环境
# ============================================
FROM nginx:alpine

# 设置时区
ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apk del tzdata

# 从构建阶段复制构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制 Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 暴露端口
EXPOSE 80

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost/ || exit 1

# 启动 Nginx
CMD ["nginx", "-g", "daemon off;"]
