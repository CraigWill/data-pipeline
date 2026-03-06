#!/bin/bash
# 实时数据管道 - 一键启动脚本
# 用法: ./start.sh [up|down|status|logs|rebuild]

set -e

ACTION=${1:-up}

case "$ACTION" in
    up)
        SERVICE=${2:-}
        if [ -n "$SERVICE" ]; then
            echo "启动服务 $SERVICE ..."
            docker-compose up -d "$SERVICE"
        else
            echo "启动所有服务..."
            docker-compose up -d
            echo ""
            echo "等待服务启动..."
            sleep 5
            docker-compose ps
            echo ""
            echo "访问地址:"
            echo "  Flink Web UI:  http://localhost:8081"
            echo "  监控前端:       http://localhost:8888"
            echo "  监控后端 API:   http://localhost:5001"
        fi
        ;;
    down)
        SERVICE=${2:-}
        if [ -n "$SERVICE" ]; then
            echo "停止并移除服务 $SERVICE ..."
            docker-compose rm -fs "$SERVICE"
        else
            echo "停止所有服务..."
            docker-compose down
        fi
        ;;
    stop)
        SERVICE=${2:-}
        if [ -n "$SERVICE" ]; then
            echo "停止服务 $SERVICE ..."
            docker-compose stop "$SERVICE"
        else
            echo "请指定服务名，用法: ./start.sh stop <service>"
            echo "可选服务名：flink-monitor-backend、flink-monitor-frontend、jobmanager、taskmanager"
            exit 1
        fi
        ;;
    status)
        SERVICE=${2:-}
        if [ -n "$SERVICE" ]; then
            docker-compose ps "$SERVICE"
        else
            docker-compose ps
            echo ""
            echo "Flink 集群状态:"
            curl -s http://localhost:8081/overview 2>/dev/null | python3 -m json.tool 2>/dev/null || echo "  Flink 未就绪"
        fi
        ;;
    logs)
        SERVICE=${2:-}
        if [ -n "$SERVICE" ]; then
            docker-compose logs -f --tail=100 "$SERVICE"
        else
            docker-compose logs -f --tail=50
        fi
        ;;
    rebuild)
        SERVICE=${2:-}
        if [ -n "$SERVICE" ]; then
            echo "重新构建并启动服务 $SERVICE ..."
            docker-compose stop "$SERVICE"
            docker-compose build "$SERVICE"
            docker-compose up -d "$SERVICE"
        else
            echo "重新构建并启动所有服务..."
            docker-compose down
            docker-compose build
            docker-compose up -d
        fi
        ;;
    *)
        echo "用法: ./start.sh [up|down|stop|status|logs|rebuild] [service]"
        echo ""
        echo "  up       启动所有服务 (默认) 或指定服务，例如: ./start.sh up flink-monitor-backend"
        echo "  down     停止所有服务 或指定服务，例如: ./start.sh down flink-monitor-backend"
        echo "  stop     停止指定服务，例如: ./start.sh stop flink-monitor-backend"
        echo "  status   查看所有服务状态 或指定服务，例如: ./start.sh status flink-monitor-backend"
        echo "  logs     查看日志 (可指定服务名, 如: ./start.sh logs jobmanager)"
        echo "  rebuild  重新构建并启动所有服务 或指定服务，例如: ./start.sh rebuild flink-monitor-backend"
        echo ""
        echo "可选服务名：flink-monitor-backend、flink-monitor-frontend、jobmanager、taskmanager"
        exit 1
        ;;
esac
