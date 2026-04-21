#!/bin/bash

# 本地主机部署启动脚本（无容器）
# 通过 Spring Boot 内嵌 Tomcat 运行 monitor-backend
# 通过 Vite dev server 运行 monitor-frontend
# 通过本地 Flink 安装运行 Flink 集群
#
# 用法:
#   ./start-local.sh              # 启动所有组件
#   ./start-local.sh backend      # 仅启动后端
#   ./start-local.sh frontend     # 仅启动前端
#   ./start-local.sh flink        # 仅启动 Flink 集群
#   ./start-local.sh --stop       # 停止所有组件
#   ./start-local.sh --status     # 查看运行状态
#   ./start-local.sh --build      # 仅构建不启动
#   ./start-local.sh --logs       # 查看日志

set -e

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$PROJECT_ROOT/logs/local"
PID_DIR="$PROJECT_ROOT/.local-pids"

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

mkdir -p "$LOG_DIR" "$PID_DIR"

# ==========================================
# 环境变量加载
# ==========================================
load_env() {
    if [ -f "$PROJECT_ROOT/.env.local" ]; then
        set -a
        source "$PROJECT_ROOT/.env.local"
        set +a
        echo -e "${GREEN}✓ 已加载 .env.local（本地开发配置）${NC}"
    elif [ -f "$PROJECT_ROOT/.env" ]; then
        set -a
        source "$PROJECT_ROOT/.env"
        set +a
        echo -e "${YELLOW}⚠ 未找到 .env.local，已加载 .env（Docker 配置，部分路径可能不适用）${NC}"
    else
        echo -e "${YELLOW}⚠ 未找到 .env.local 或 .env，使用默认值${NC}"
    fi

    # 本地运行：如果 DATABASE_HOST 仍是 Docker 容器名，替换为 localhost
    if [ "${DATABASE_HOST}" = "oracle11g" ] || [ "${DATABASE_HOST}" = "oracle" ]; then
        echo -e "${YELLOW}  ⚠ DATABASE_HOST=${DATABASE_HOST} 是容器名，本地运行改为 localhost${NC}"
        export DATABASE_HOST=localhost
    fi

    # 本地运行：覆盖 Docker 容器路径为本地路径
    export CHECKPOINT_DIR="${CHECKPOINT_DIR:-file:///tmp/flink-checkpoints}"
    export SAVEPOINT_DIR="${SAVEPOINT_DIR:-file:///tmp/flink-savepoints}"

    # 其他本地运行必需的默认值
    export DATABASE_PORT="${DATABASE_PORT:-1521}"
    export DATABASE_SID="${DATABASE_SID:-helowin}"
    export DATABASE_USERNAME="${DATABASE_USERNAME:-finance_user}"
    export FLINK_REST_URL="${FLINK_REST_URL:-http://localhost:8081}"
    export FLINK_REST_URLS="${FLINK_REST_URLS:-http://localhost:8081}"
    export OUTPUT_PATH="${OUTPUT_PATH:-$PROJECT_ROOT/output/cdc}"
    export CONFIG_DIR="${CONFIG_DIR:-$PROJECT_ROOT/config}"
    export TZ="${TZ:-Asia/Shanghai}"

    # JWT/AES 未设置时生成临时值（仅开发用）
    if [ -z "$JWT_SECRET" ]; then
        export JWT_SECRET="$(openssl rand -base64 64 2>/dev/null || echo 'dev-jwt-secret-please-set-in-env-file-min-32-chars')"
        echo -e "${YELLOW}  ⚠ JWT_SECRET 未设置，已生成临时值（重启后失效）${NC}"
    fi
    if [ -z "$AES_ENCRYPTION_KEY" ]; then
        export AES_ENCRYPTION_KEY="$(openssl rand -base64 32 2>/dev/null || echo 'dev-aes-key-32-chars-placeholder!!')"
        echo -e "${YELLOW}  ⚠ AES_ENCRYPTION_KEY 未设置，已生成临时值${NC}"
    fi
}

# ==========================================
# 前置检查
# ==========================================
check_prerequisites() {
    echo -e "${BLUE}>>> 检查运行环境${NC}"

    # Java
    if ! command -v java &>/dev/null; then
        echo -e "${RED}✗ Java 未安装，请安装 Java 17+${NC}"
        exit 1
    fi
    JAVA_VER=$(java -version 2>&1 | head -1)
    echo -e "${GREEN}✓ Java: $JAVA_VER${NC}"

    # Maven
    if ! command -v mvn &>/dev/null; then
        echo -e "${RED}✗ Maven 未安装${NC}"
        exit 1
    fi
    echo -e "${GREEN}✓ Maven: $(mvn -version 2>&1 | head -1 | cut -d' ' -f3)${NC}"

    # Node.js（前端可选）
    if command -v node &>/dev/null; then
        echo -e "${GREEN}✓ Node.js: $(node -v)${NC}"
    else
        echo -e "${YELLOW}⚠ Node.js 未安装，前端将无法启动${NC}"
    fi

    # Oracle 容器（如果 DATABASE_HOST 是容器名，自动启动）
    if docker info &>/dev/null; then
        ORACLE_CONTAINER=$(docker ps -a --filter name=oracle11g --format "{{.Names}}" 2>/dev/null | head -1)
        if [ -n "$ORACLE_CONTAINER" ]; then
            ORACLE_STATUS=$(docker inspect -f '{{.State.Running}}' "$ORACLE_CONTAINER" 2>/dev/null)
            if [ "$ORACLE_STATUS" != "true" ]; then
                echo -e "${YELLOW}  Oracle 容器未运行，正在启动...${NC}"
                docker start "$ORACLE_CONTAINER" > /dev/null
                echo -e "${GREEN}✓ Oracle 容器已启动，等待就绪...${NC}"
                sleep 10
            else
                echo -e "${GREEN}✓ Oracle 容器运行中${NC}"
            fi
            # 检查端口映射
            ORACLE_PORT=$(docker port "$ORACLE_CONTAINER" 1521/tcp 2>/dev/null | cut -d: -f2)
            if [ -n "$ORACLE_PORT" ] && [ "$ORACLE_PORT" != "1521" ]; then
                echo -e "${YELLOW}  ⚠ Oracle 端口映射为 $ORACLE_PORT，更新 DATABASE_PORT${NC}"
                export DATABASE_PORT="$ORACLE_PORT"
            fi
        else
            echo -e "${YELLOW}⚠ 未找到 oracle11g 容器，请确保 Oracle 数据库可访问${NC}"
        fi
    else
        echo -e "${YELLOW}⚠ Docker 未运行，跳过 Oracle 容器检查${NC}"
        echo -e "  请确保 Oracle 数据库在 ${DATABASE_HOST}:${DATABASE_PORT} 可访问"
    fi

    # Flink（可选，不影响 backend 启动）
    if [ -n "$FLINK_HOME" ] && [ -f "$FLINK_HOME/bin/start-cluster.sh" ]; then
        echo -e "${GREEN}✓ Flink: $FLINK_HOME${NC}"
    else
        FLINK_HOME=$(find /usr/local /opt/homebrew/opt ~/Downloads ~ -maxdepth 3 -name "start-cluster.sh" 2>/dev/null | head -1 | sed 's|/bin/start-cluster.sh||')
        if [ -n "$FLINK_HOME" ]; then
            export FLINK_HOME
            echo -e "${GREEN}✓ Flink 自动检测: $FLINK_HOME${NC}"
        else
            echo -e "${YELLOW}⚠ Flink 未安装，Flink 集群将无法本地启动${NC}"
            echo -e "  下载: https://flink.apache.org/downloads/"
            echo -e "  或设置 FLINK_HOME 环境变量"
        fi
    fi
}

# ==========================================
# 构建
# ==========================================
build_backend() {
    echo -e "${BLUE}>>> 构建 monitor-backend${NC}"
    mvn -f "$PROJECT_ROOT/pom.xml" clean package -pl monitor-backend -am -DskipTests -q
    JAR=$(ls "$PROJECT_ROOT"/monitor-backend/target/monitor-backend-*-SNAPSHOT.jar 2>/dev/null | head -1)
    if [ -z "$JAR" ]; then
        echo -e "${RED}✗ 构建失败，JAR 文件未生成${NC}"
        exit 1
    fi
    echo -e "${GREEN}✓ 构建完成: $(basename $JAR)${NC}"
}

build_flink_jobs() {
    echo -e "${BLUE}>>> 构建 flink-jobs${NC}"
    mvn -f "$PROJECT_ROOT/pom.xml" clean package -pl flink-jobs -am -DskipTests -q
    JAR=$(ls "$PROJECT_ROOT"/flink-jobs/target/flink-jobs-*-SNAPSHOT.jar 2>/dev/null | head -1)
    if [ -z "$JAR" ]; then
        echo -e "${RED}✗ 构建失败${NC}"
        exit 1
    fi
    echo -e "${GREEN}✓ 构建完成: $(basename $JAR)${NC}"
}

build_frontend() {
    echo -e "${BLUE}>>> 构建 monitor-frontend${NC}"
    cd "$PROJECT_ROOT/monitor/frontend-vue"
    if [ ! -d node_modules ]; then
        echo "  安装依赖..."
        npm install --silent
    fi
    npm run build --silent
    echo -e "${GREEN}✓ 构建完成: monitor/frontend-vue/dist/${NC}"
    cd "$PROJECT_ROOT"
}

# ==========================================
# 启动 / 停止函数
# ==========================================

start_backend() {
    local pid_file="$PID_DIR/backend.pid"
    if [ -f "$pid_file" ] && kill -0 "$(cat $pid_file)" 2>/dev/null; then
        echo -e "${YELLOW}⚠ Backend 已在运行 (PID: $(cat $pid_file))${NC}"
        return
    fi

    JAR=$(ls "$PROJECT_ROOT"/monitor-backend/target/monitor-backend-*-SNAPSHOT.jar 2>/dev/null | head -1)
    if [ -z "$JAR" ]; then
        echo -e "${YELLOW}  JAR 不存在，先构建...${NC}"
        build_backend
        JAR=$(ls "$PROJECT_ROOT"/monitor-backend/target/monitor-backend-*-SNAPSHOT.jar | head -1)
    fi

    mkdir -p "$PROJECT_ROOT/output/cdc" "$PROJECT_ROOT/config"

    echo -e "${BLUE}>>> 启动 monitor-backend (内嵌 Tomcat, 端口 5001)${NC}"
    nohup java \
        -Xms512m -Xmx2048m \
        -Dspring.profiles.active=local \
        -Dserver.port=5001 \
        -DDATABASE_HOST="$DATABASE_HOST" \
        -DDATABASE_PORT="$DATABASE_PORT" \
        -DDATABASE_SID="$DATABASE_SID" \
        -DDATABASE_USERNAME="$DATABASE_USERNAME" \
        -DDATABASE_PASSWORD="$DATABASE_PASSWORD" \
        -DDATABASE_SCHEMA="${DATABASE_SCHEMA:-FINANCE_USER}" \
        -DFLINK_REST_URL="$FLINK_REST_URL" \
        -DFLINK_REST_URLS="$FLINK_REST_URLS" \
        -DOUTPUT_PATH="$OUTPUT_PATH" \
        -DCONFIG_DIR="$CONFIG_DIR" \
        -DJWT_SECRET="$JWT_SECRET" \
        -DJWT_EXPIRATION="${JWT_EXPIRATION:-86400000}" \
        -DAES_ENCRYPTION_KEY="$AES_ENCRYPTION_KEY" \
        -DADMIN_INITIAL_PASSWORD="$ADMIN_INITIAL_PASSWORD" \
        -DTZ=Asia/Shanghai \
        -jar "$JAR" \
        > "$LOG_DIR/backend.log" 2>&1 &

    echo $! > "$pid_file"
    echo -e "${GREEN}✓ Backend 已启动 (PID: $!, 日志: logs/local/backend.log)${NC}"
    echo -e "  等待就绪..."

    # 等待健康检查
    for i in $(seq 1 30); do
        sleep 2
        if curl -sf http://localhost:5001/actuator/health &>/dev/null; then
            echo -e "${GREEN}✓ Backend 就绪: http://localhost:5001${NC}"
            return
        fi
        echo -n "."
    done
    echo ""
    echo -e "${YELLOW}⚠ Backend 启动超时，请检查日志: tail -f logs/local/backend.log${NC}"
}

start_frontend_dev() {
    local pid_file="$PID_DIR/frontend.pid"
    if [ -f "$pid_file" ] && kill -0 "$(cat $pid_file)" 2>/dev/null; then
        echo -e "${YELLOW}⚠ Frontend 已在运行 (PID: $(cat $pid_file))${NC}"
        return
    fi

    if ! command -v node &>/dev/null; then
        echo -e "${RED}✗ Node.js 未安装，跳过前端启动${NC}"
        return
    fi

    echo -e "${BLUE}>>> 启动 monitor-frontend (Vite dev server, 端口 3000)${NC}"
    cd "$PROJECT_ROOT/monitor/frontend-vue"
    if [ ! -d node_modules ]; then
        echo "  安装依赖..."
        npm install --silent
    fi

    nohup npm run dev > "$LOG_DIR/frontend.log" 2>&1 &
    echo $! > "$pid_file"
    echo -e "${GREEN}✓ Frontend 已启动 (PID: $!, 日志: logs/local/frontend.log)${NC}"
    echo -e "  访问: http://localhost:3000"
    cd "$PROJECT_ROOT"
}

start_flink() {
    if [ -z "$FLINK_HOME" ] || [ ! -f "$FLINK_HOME/bin/start-cluster.sh" ]; then
        # 尝试默认安装路径
        if [ -f "/usr/local/flink-1.20.0/bin/start-cluster.sh" ]; then
            export FLINK_HOME=/usr/local/flink-1.20.0
        else
            echo -e "${YELLOW}⚠ FLINK_HOME 未设置或 Flink 未安装，跳过 Flink 启动${NC}"
            echo -e "  安装路径: /usr/local/flink-1.20.0"
            echo -e "  设置方法: export FLINK_HOME=/usr/local/flink-1.20.0"
            return
        fi
    fi

    # 检查是否已在运行
    if curl -sf http://localhost:8081/overview &>/dev/null; then
        echo -e "${YELLOW}⚠ Flink 集群已在运行${NC}"
        return
    fi

    echo -e "${BLUE}>>> 启动 Flink 集群 (JobManager: 8081)${NC}"

    # 确保 checkpoint 目录存在
    mkdir -p /tmp/flink-checkpoints /tmp/flink-savepoints

    # Java 17+ 模块系统：Flink 1.20 需要 --add-opens 才能在 Java 17+ 上运行
    export JVM_ARGS="${JVM_ARGS} --add-opens=java.base/java.util=ALL-UNNAMED"
    export JVM_ARGS="${JVM_ARGS} --add-opens=java.base/java.lang=ALL-UNNAMED"
    export JVM_ARGS="${JVM_ARGS} --add-opens=java.base/java.lang.reflect=ALL-UNNAMED"
    export JVM_ARGS="${JVM_ARGS} --add-opens=java.base/java.io=ALL-UNNAMED"
    export JVM_ARGS="${JVM_ARGS} --add-opens=java.base/java.net=ALL-UNNAMED"
    export JVM_ARGS="${JVM_ARGS} --add-opens=java.base/java.nio=ALL-UNNAMED"
    export JVM_ARGS="${JVM_ARGS} --add-opens=java.base/sun.nio.ch=ALL-UNNAMED"

    # Flink 使用 FLINK_ENV_JAVA_OPTS 传递额外 JVM 参数
    export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} ${JVM_ARGS}"

    # 覆盖 Flink 配置中的 Docker 路径为本地路径
    # Flink 1.20 使用 FLINK_PROPERTIES 环境变量覆盖 flink-conf.yaml
    export FLINK_PROPERTIES="${FLINK_PROPERTIES}
state.checkpoints.dir: file:///tmp/flink-checkpoints
state.savepoints.dir: file:///tmp/flink-savepoints
"

    "$FLINK_HOME/bin/start-cluster.sh" 2>&1 | grep -v "^$"

    sleep 3
    if curl -sf http://localhost:8081/overview &>/dev/null; then
        echo -e "${GREEN}✓ Flink 集群就绪: http://localhost:8081${NC}"
    else
        echo -e "${YELLOW}⚠ Flink 启动中，请稍后访问 http://localhost:8081${NC}"
    fi
}

stop_component() {
    local name=$1
    local pid_file="$PID_DIR/${name}.pid"
    if [ -f "$pid_file" ]; then
        local pid=$(cat "$pid_file")
        if kill -0 "$pid" 2>/dev/null; then
            kill "$pid"
            echo -e "${GREEN}✓ $name 已停止 (PID: $pid)${NC}"
        fi
        rm -f "$pid_file"
    else
        echo -e "${YELLOW}  $name 未运行${NC}"
    fi
}

stop_all() {
    echo -e "${BLUE}>>> 停止所有本地服务${NC}"
    stop_component "backend"
    stop_component "frontend"
    FLINK_HOME_RESOLVED="${FLINK_HOME:-/usr/local/flink-1.20.0}"
    if [ -f "$FLINK_HOME_RESOLVED/bin/stop-cluster.sh" ]; then
        "$FLINK_HOME_RESOLVED/bin/stop-cluster.sh" 2>/dev/null && echo -e "${GREEN}✓ Flink 集群已停止${NC}" || true
    fi
}

show_status() {
    echo ""
    echo "=========================================="
    echo "  本地服务状态"
    echo "=========================================="

    for name in backend frontend; do
        local pid_file="$PID_DIR/${name}.pid"
        if [ -f "$pid_file" ] && kill -0 "$(cat $pid_file)" 2>/dev/null; then
            echo -e "  $name: ${GREEN}运行中${NC} (PID: $(cat $pid_file))"
        else
            echo -e "  $name: ${RED}未运行${NC}"
        fi
    done

    # Flink
    if curl -sf http://localhost:8081/overview &>/dev/null; then
        echo -e "  flink:   ${GREEN}运行中${NC} → http://localhost:8081"
    else
        echo -e "  flink:   ${RED}未运行${NC}"
    fi

    echo ""
    echo "  访问地址:"
    echo "    Frontend:  http://localhost:3000"
    echo "    Backend:   http://localhost:5001"
    echo "    Flink UI:  http://localhost:8081"
    echo ""
    echo "  日志目录: logs/local/"
    echo "=========================================="
}

show_logs() {
    local target=${1:-backend}
    local log_file="$LOG_DIR/${target}.log"
    if [ -f "$log_file" ]; then
        tail -f "$log_file"
    else
        echo -e "${YELLOW}日志文件不存在: $log_file${NC}"
    fi
}

# ==========================================
# 主流程
# ==========================================
ACTION="start"
TARGETS=()

while [[ $# -gt 0 ]]; do
    case $1 in
        --stop)   ACTION="stop"; shift ;;
        --status) ACTION="status"; shift ;;
        --build)  ACTION="build"; shift ;;
        --logs)   ACTION="logs"; shift ;;
        -h|--help)
            echo "用法: $0 [backend|frontend|flink] [--stop|--status|--build|--logs]"
            exit 0 ;;
        backend|frontend|flink) TARGETS+=("$1"); shift ;;
        *) echo -e "${RED}未知参数: $1${NC}"; exit 1 ;;
    esac
done

# 默认启动全部
[ ${#TARGETS[@]} -eq 0 ] && TARGETS=("backend" "frontend" "flink")

case $ACTION in
    stop)
        stop_all
        ;;
    status)
        show_status
        ;;
    build)
        load_env
        for t in "${TARGETS[@]}"; do
            case $t in
                backend)  build_backend ;;
                frontend) build_frontend ;;
                flink)    build_flink_jobs ;;
            esac
        done
        ;;
    logs)
        show_logs "${TARGETS[0]}"
        ;;
    start)
        echo "=========================================="
        echo "  本地主机部署启动 (无容器)"
        echo "=========================================="
        check_prerequisites
        load_env
        echo ""
        for t in "${TARGETS[@]}"; do
            case $t in
                backend)  start_backend ;;
                frontend) start_frontend_dev ;;
                flink)    start_flink ;;
            esac
            echo ""
        done
        show_status
        ;;
esac
