#!/bin/bash
# 最小化部署环境变量配置
# 使用方法: cp env.sh.example env.sh && vim env.sh

#!/bin/bash
# 最小化部署环境变量配置
# 使用方法: cp env.sh.example env.sh && vim env.sh

# ============================================
# CDC 管理库配置（存储管理表：runtime_jobs, cdc_tasks 等）
# 连接 oracle-cdcdb 容器（端口 1522 是宿主机映射，容器内是 1521）
# ============================================
export DATABASE_HOST=localhost
export DATABASE_PORT=1522
export DATABASE_SID=helowin
export DATABASE_USERNAME=cdc_admin
export DATABASE_PASSWORD=46RJiI5Y0NQfkonmx5syaA==

# ============================================
# Flink 配置
# ============================================
# Flink 安装目录（下载地址: https://flink.apache.org/downloads/）
export FLINK_HOME=/usr/local/flink-1.20.0
export FLINK_REST_URL=http://localhost:8081

# ============================================
# 应用配置
# ============================================
export OUTPUT_PATH=/opt/flink/output/cdc
export SERVER_PORT=5001

# ============================================
# 安全配置
# ============================================
export JWT_SECRET="xGTMWGPrwsj9s1DtwQuQhl0GfLaetGcy0KXMAeaKIEIgv46z+SM3sg4+4q1GIlX2ApDK5GUw4wbEFCUn1Aa8LQ=="
export JWT_EXPIRATION=300000

# AES 加密密钥（生成: openssl rand -base64 32）
export AES_ENCRYPTION_KEY="E5TDKE7NvyphzdaW0SIUfAhZl2v9sRnjHl1Egqx9arM"

# 管理员初始密码
export ADMIN_INITIAL_PASSWORD=Admin2026Secure

# CORS 允许的来源
export ALLOWED_ORIGINS=http://localhost:8888,http://localhost:3000

# ============================================
# 容器模式配置（bare metal 部署请保持 localhost）
# ============================================
# 当 Flink 和数据库都在主机上运行时，设置为 localhost
# 当 Flink 在容器内运行时，设置为数据库容器名（如 oracle11g）
export ORACLE_CONTAINER=localhost
