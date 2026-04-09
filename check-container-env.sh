#!/bin/bash
# 检查 Docker 容器内的环境变量

set -e

echo "=========================================="
echo "Docker 容器环境变量检查"
echo "=========================================="
echo ""

# 检查 Docker Compose 是否运行
if ! docker-compose ps | grep -q "Up"; then
    echo "错误: 没有运行中的容器"
    echo "请先启动服务: docker-compose up -d"
    exit 1
fi

# 获取运行中的服务列表
SERVICES=$(docker-compose ps --services --filter "status=running")

if [ -z "$SERVICES" ]; then
    echo "错误: 没有运行中的服务"
    exit 1
fi

echo "运行中的服务:"
echo "$SERVICES" | sed 's/^/  - /'
echo ""

# 检查每个服务的环境变量
for service in $SERVICES; do
    echo "=========================================="
    echo "服务: $service"
    echo "=========================================="
    
    case "$service" in
        monitor-backend)
            echo ""
            echo "关键环境变量:"
            docker-compose exec -T $service env | grep -E "JWT_SECRET|AES_ENCRYPTION_KEY|DATABASE" | sort || echo "  (无相关环境变量)"
            ;;
        jobmanager|jobmanager-standby)
            echo ""
            echo "Flink 和数据库配置:"
            docker-compose exec -T $service env | grep -E "DATABASE|CHECKPOINT|FLINK_|HA_" | sort | head -20 || echo "  (无相关环境变量)"
            ;;
        taskmanager)
            echo ""
            echo "Flink 配置:"
            docker-compose exec -T $service env | grep -E "FLINK_|TASK_MANAGER" | sort | head -15 || echo "  (无相关环境变量)"
            ;;
        monitor-frontend)
            echo ""
            echo "前端配置:"
            docker-compose exec -T $service env | grep -E "VUE_|NODE_|NGINX" | sort || echo "  (无相关环境变量)"
            ;;
        zookeeper)
            echo ""
            echo "ZooKeeper 配置:"
            docker-compose exec -T $service env | grep -E "ZOOKEEPER_|KAFKA_" | sort | head -10 || echo "  (无相关环境变量)"
            ;;
        *)
            echo ""
            echo "所有环境变量:"
            docker-compose exec -T $service env | sort | head -20
            ;;
    esac
    
    echo ""
done

echo "=========================================="
echo "检查完成"
echo "=========================================="
echo ""

# 验证关键环境变量
echo "验证关键环境变量:"
echo ""

# 检查 monitor-backend
if echo "$SERVICES" | grep -q "monitor-backend"; then
    echo "monitor-backend:"
    
    JWT_SECRET=$(docker-compose exec -T monitor-backend env | grep "^JWT_SECRET=" | cut -d'=' -f2- | tr -d '\n\r')
    AES_KEY=$(docker-compose exec -T monitor-backend env | grep "^AES_ENCRYPTION_KEY=" | cut -d'=' -f2- | tr -d '\n\r')
    DB_PASSWORD=$(docker-compose exec -T monitor-backend env | grep "^DATABASE_PASSWORD=" | cut -d'=' -f2- | tr -d '\n\r')
    
    if [ -n "$JWT_SECRET" ]; then
        echo "  ✓ JWT_SECRET: ${JWT_SECRET:0:20}... (长度: ${#JWT_SECRET})"
    else
        echo "  ❌ JWT_SECRET: 未设置"
    fi
    
    if [ -n "$AES_KEY" ]; then
        echo "  ✓ AES_ENCRYPTION_KEY: ${AES_KEY:0:20}... (长度: ${#AES_KEY})"
    else
        echo "  ❌ AES_ENCRYPTION_KEY: 未设置"
    fi
    
    if [ -n "$DB_PASSWORD" ]; then
        echo "  ✓ DATABASE_PASSWORD: ${DB_PASSWORD:0:10}..."
    else
        echo "  ❌ DATABASE_PASSWORD: 未设置"
    fi
    
    echo ""
fi

# 检查 jobmanager
if echo "$SERVICES" | grep -q "jobmanager"; then
    echo "jobmanager:"
    
    DB_HOST=$(docker-compose exec -T jobmanager env | grep "^DATABASE_HOST=" | cut -d'=' -f2- | tr -d '\n\r')
    DB_PASSWORD=$(docker-compose exec -T jobmanager env | grep "^DATABASE_PASSWORD=" | cut -d'=' -f2- | tr -d '\n\r')
    
    if [ -n "$DB_HOST" ]; then
        echo "  ✓ DATABASE_HOST: $DB_HOST"
    else
        echo "  ❌ DATABASE_HOST: 未设置"
    fi
    
    if [ -n "$DB_PASSWORD" ]; then
        echo "  ✓ DATABASE_PASSWORD: ${DB_PASSWORD:0:10}..."
    else
        echo "  ❌ DATABASE_PASSWORD: 未设置"
    fi
    
    echo ""
fi

echo "=========================================="
echo ""
echo "提示:"
echo "  - 环境变量从 .env 文件加载"
echo "  - 修改 .env 后需要重启容器: docker-compose restart"
echo "  - 查看特定服务: docker-compose exec <service> env"
echo ""
