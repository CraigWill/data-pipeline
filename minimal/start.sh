#!/bin/bash
# 最小化单点部署启动脚本（裸机进程模式，无 Docker）
# 直接在主机上启动 Flink + Backend + Frontend

set -e

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
LOG_DIR="$PROJECT_ROOT/logs/minimal"
PID_DIR="$SCRIPT_DIR/.pids"

mkdir -p "$LOG_DIR" "$PID_DIR"

# 加载环境变量
load_env() {
    if [ -f "$SCRIPT_DIR/env.sh" ]; then
        source "$SCRIPT_DIR/env.sh"
        echo -e "${GREEN}✓ 已加载 env.sh${NC}"
    else
        echo -e "${RED}错误: env.sh 不存在${NC}"
        echo "请先创建: cp env.sh.example env.sh && vim env.sh"
        exit 1
    fi
}

# 检查前置条件
check_prerequisites() {
    echo -e "${BLUE}>>> 检查环境${NC}"

    # Java
    if ! command -v java &>/dev/null; then
        echo -e "${RED}✗ Java 未安装${NC}"
        exit 1
    fi
    echo -e "${GREEN}✓ Java: $(java -version 2>&1 | head -1)${NC}"

    # Flink
    if [ -z "$FLINK_HOME" ] || [ ! -f "$FLINK_HOME/bin/start-cluster.sh" ]; then
        echo -e "${RED}✗ FLINK_HOME 未设置或 Flink 未安装${NC}"
        echo "  请在 env.sh 中设置 FLINK_HOME"
        echo "  下载: https://flink.apache.org/downloads/"
        exit 1
    fi
    echo -e "${GREEN}✓ Flink: $FLINK_HOME${NC}"

    # Maven（构建时需要）
    if command -v mvn &>/dev/null; then
        echo -e "${GREEN}✓ Maven: $(mvn -version 2>&1 | head -1 | awk '{print $3}')${NC}"
    else
        echo -e "${YELLOW}⚠ Maven 未安装（构建时需要）${NC}"
    fi
}

# 构建
do_build() {
    echo -e "${BLUE}>>> 构建项目${NC}"
    cd "$PROJECT_ROOT"

    # 构建后端
    echo -e "${BLUE}  构建 monitor-backend...${NC}"
    mvn clean package -pl monitor-backend -am -DskipTests -q
    echo -e "${GREEN}  ✓ $(ls monitor-backend/target/monitor-backend-*-SNAPSHOT.jar | xargs basename)${NC}"

    # 构建 flink-jobs
    echo -e "${BLUE}  构建 flink-jobs...${NC}"
    mvn package -pl flink-jobs -DskipTests -q
    echo -e "${GREEN}  ✓ $(ls flink-jobs/target/flink-jobs-*-SNAPSHOT.jar | xargs basename)${NC}"

    # 构建前端（可选）
    if command -v npm &>/dev/null && [ -d "monitor/frontend-vue" ]; then
        echo -e "${BLUE}  构建 frontend...${NC}"
        cd monitor/frontend-vue
        [ ! -d node_modules ] && npm install --silent
        npm run build --silent
        echo -e "${GREEN}  ✓ frontend dist 构建完成${NC}"
        cd "$PROJECT_ROOT"
    fi

    echo -e "${GREEN}✓ 构建完成${NC}"
}

# 配置 Flink
setup_flink() {
    echo -e "${BLUE}>>> 配置 Flink（单点模式）${NC}"

    # 复制单点配置到 Flink
    cp "$SCRIPT_DIR/flink-conf.yaml" "$FLINK_HOME/conf/flink-conf.yaml"

    # 复制 flink-jobs JAR 到 Flink lib
    local flink_jar=$(ls "$PROJECT_ROOT"/flink-jobs/target/flink-jobs-*-SNAPSHOT.jar 2>/dev/null | head -1)
    if [ -n "$flink_jar" ]; then
        cp "$flink_jar" "$FLINK_HOME/lib/"
        echo -e "${GREEN}  ✓ flink-jobs JAR 已复制到 Flink lib${NC}"
    fi

    # 创建必要目录（与 app_config 中的配置保持一致）
    mkdir -p /opt/flink/checkpoints /opt/flink/savepoints /opt/flink/output/cdc /tmp/flink-upload
    mkdir -p "$PROJECT_ROOT/output/cdc"
    
    # 确保 Flink 上传目录有正确权限（解决 JAR 上传失败问题）
    chmod 777 /tmp/flink-upload 2>/dev/null || true
    chmod 777 /tmp/flink-upload/flink-web-upload 2>/dev/null || true
    
    # 确保 Checkpoint 目录有正确权限
    chmod 777 /tmp/flink-checkpoints 2>/dev/null || true

    echo -e "${GREEN}✓ Flink 配置完成（无 HA 模式）${NC}"
}

# 启动 Flink 集群
start_flink() {
    if curl -sf http://localhost:8081/overview >/dev/null 2>&1; then
        echo -e "${YELLOW}⚠ Flink 已在运行${NC}"
        return
    fi

    echo -e "${BLUE}>>> 启动 Flink 集群（单 JM + 单 TM）${NC}"

    # Java 17+ 模块系统兼容
    export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED"

    "$FLINK_HOME/bin/start-cluster.sh"

    # 等待就绪
    for i in $(seq 1 15); do
        if curl -sf http://localhost:8081/overview >/dev/null 2>&1; then
            echo -e "${GREEN}✓ Flink 就绪: http://localhost:8081${NC}"
            return
        fi
        sleep 2
    done
    echo -e "${YELLOW}⚠ Flink 启动中，请稍后检查 http://localhost:8081${NC}"
}

# 启动后端
start_backend() {
    local pid_file="$PID_DIR/backend.pid"
    if [ -f "$pid_file" ] && kill -0 "$(cat $pid_file)" 2>/dev/null; then
        echo -e "${YELLOW}⚠ Backend 已在运行 (PID: $(cat $pid_file))${NC}"
        return
    fi

    local jar=$(ls "$PROJECT_ROOT"/monitor-backend/target/monitor-backend-*-SNAPSHOT.jar 2>/dev/null | head -1)
    if [ -z "$jar" ]; then
        echo -e "${RED}✗ Backend JAR 不存在，请先运行: ./start.sh build${NC}"
        return
    fi

    echo -e "${BLUE}>>> 启动 Monitor Backend (端口 $SERVER_PORT)${NC}"

    # 复制 minimal 配置文件到 classpath
    cp "$SCRIPT_DIR/application-minimal.yml" "$PROJECT_ROOT/monitor-backend/target/classes/application-minimal.yml" 2>/dev/null

    nohup java \
        -Xms256m -Xmx1024m \
        -Dspring.profiles.active=minimal \
        -Dspring.config.additional-location="file:$SCRIPT_DIR/application-minimal.yml" \
        -DDATABASE_HOST="$DATABASE_HOST" \
        -DDATABASE_PORT="$DATABASE_PORT" \
        -DDATABASE_SID="$DATABASE_SID" \
        -DDATABASE_USERNAME="$DATABASE_USERNAME" \
        -DDATABASE_PASSWORD="$DATABASE_PASSWORD" \
        -DFLINK_REST_URL="$FLINK_REST_URL" \
        -DOUTPUT_PATH="$OUTPUT_PATH" \
        -DJWT_SECRET="$JWT_SECRET" \
        -DJWT_EXPIRATION="${JWT_EXPIRATION:-300000}" \
        -DAES_ENCRYPTION_KEY="$AES_ENCRYPTION_KEY" \
        -DADMIN_INITIAL_PASSWORD="$ADMIN_INITIAL_PASSWORD" \
        -DALLOWED_ORIGINS="$ALLOWED_ORIGINS" \
        -DORACLE_CONTAINER="${ORACLE_CONTAINER:-localhost}" \
        -DFLINK_HOME="${FLINK_HOME}" \
        -DFLINK_JOB_JAR_PATH="$PROJECT_ROOT/flink-jobs/target/flink-jobs-1.0.0-SNAPSHOT.jar" \
        -DCHECKPOINT_DIR="file:///opt/flink/checkpoints" \
        -DSAVEPOINT_DIR="file:///opt/flink/savepoints" \
        -DTZ=Asia/Shanghai \
        -jar "$jar" \
        > "$LOG_DIR/backend.log" 2>&1 &

    echo $! > "$pid_file"
    echo -e "${GREEN}✓ Backend 已启动 (PID: $(cat $pid_file))${NC}"

    # 等待就绪
    for i in $(seq 1 20); do
        if curl -sf http://localhost:${SERVER_PORT:-5001}/actuator/health >/dev/null 2>&1; then
            echo -e "${GREEN}  就绪: http://localhost:${SERVER_PORT:-5001}${NC}"
            return
        fi
        sleep 2
    done
    echo -e "${YELLOW}  启动中，查看日志: tail -f $LOG_DIR/backend.log${NC}"
}

# 启动前端（使用本地 nginx 配置）
start_frontend() {
    local pid_file="$PID_DIR/frontend.pid"
    if [ -f "$pid_file" ] && kill -0 "$(cat $pid_file)" 2>/dev/null; then
        echo -e "${YELLOW}⚠ Frontend 已在运行 (PID: $(cat $pid_file))${NC}"
        return
    fi

    local dist_dir="$PROJECT_ROOT/monitor/frontend-vue/dist"
    if [ ! -d "$dist_dir" ]; then
        echo -e "${YELLOW}⚠ 前端未构建，跳过（运行 ./start.sh build 构建）${NC}"
        return
    fi

    echo -e "${BLUE}>>> 启动 Frontend${NC}"

    # 创建 nginx 临时目录
    mkdir -p "$LOG_DIR/nginx-client-body" "$LOG_DIR/nginx-proxy" \
              "$LOG_DIR/nginx-fastcgi" "$LOG_DIR/nginx-uwsgi" "$LOG_DIR/nginx-scgi"

    # 生成 nginx 配置（替换占位符）
    local nginx_conf="$SCRIPT_DIR/.nginx-runtime.conf"
    sed -e "s|__PROJECT_ROOT__|$PROJECT_ROOT|g" \
        -e "s|__BACKEND_PORT__|${SERVER_PORT:-5001}|g" \
        "$SCRIPT_DIR/nginx.conf" > "$nginx_conf"

    # 自动检测 mime.types 路径并修复
    local mime_found=""
    for p in \
        "/opt/homebrew/etc/nginx/mime.types" \
        "/usr/local/etc/nginx/mime.types" \
        "/etc/nginx/mime.types" \
        "$(nginx -V 2>&1 | grep -o 'prefix=[^ ]*' | cut -d= -f2)/conf/mime.types"; do
        if [ -f "$p" ]; then
            mime_found="$p"
            break
        fi
    done

    if [ -z "$mime_found" ]; then
        echo -e "${RED}  ✗ nginx mime.types 未找到，请手动编辑 $SCRIPT_DIR/nginx.conf${NC}"
        echo "     常见路径:"
        echo "       macOS Homebrew: /opt/homebrew/etc/nginx/mime.types"
        echo "       Linux:          /etc/nginx/mime.types"
        return 1
    fi

    # 更新 include 路径
    if ! grep -q "^include $mime_found;" "$nginx_conf"; then
        sed -i.bak "s|^include .*mime.types;|include $mime_found;|" "$nginx_conf"
    fi

    # 检测 nginx 是否已在运行（端口 8888）
    if pgrep -f "nginx.*:8888" >/dev/null 2>&1; then
        echo -e "${YELLOW}  ⚠ nginx 已在运行（端口 8888），跳过启动${NC}"
        echo "nginx" > "$pid_file"
        return
    fi

    # 启动 nginx（需要 sudo）
    echo -e "${BLUE}  启动 nginx（需要 sudo 权限）...${NC}"
    if sudo nginx -c "$nginx_conf" -t 2>/dev/null; then
        sudo nginx -c "$nginx_conf"
        echo "nginx" > "$pid_file"
        echo -e "${GREEN}✓ Frontend (nginx): http://localhost:8888${NC}"
        echo -e "${GREEN}  /api 代理到后端 :${SERVER_PORT:-5001}${NC}"
    else
        echo -e "${YELLOW}  ⚠ nginx 配置测试失败，回退到 Vite dev server${NC}"
        _use_vite=true
    fi

    # 回退：Vite dev server
    if [ "${_use_vite:-false}" = "true" ] && command -v npm &>/dev/null && [ -f "$PROJECT_ROOT/monitor/frontend-vue/package.json" ]; then
        echo -e "${BLUE}  使用 Vite dev server（含 API 代理）${NC}"
        cd "$PROJECT_ROOT/monitor/frontend-vue"
        [ ! -d node_modules ] && npm install --silent
        nohup npm run dev > "$LOG_DIR/frontend.log" 2>&1 &
        echo $! > "$pid_file"
        cd "$SCRIPT_DIR"
        echo -e "${GREEN}✓ Frontend (Vite): http://localhost:3000${NC}"
        echo -e "${YELLOW}  /api 已代理到后端 :${SERVER_PORT:-5001}${NC}"
    elif [ "${_use_vite:-false}" = "true" ]; then
        # 最后回退：Python 简易服务器（不支持 API 代理）
        cd "$dist_dir"
        nohup python3 -m http.server 8888 > "$LOG_DIR/frontend.log" 2>&1 &
        echo $! > "$pid_file"
        cd "$SCRIPT_DIR"
        echo -e "${GREEN}✓ Frontend (python): http://localhost:8888${NC}"
        echo -e "${YELLOW}  ⚠ Python 模式不支持 API 代理，验证码不可用，建议安装 nginx${NC}"
    fi
}

# 显示状态
show_status() {
    echo ""
    echo "=========================================="
    echo "  最小化部署状态"
    echo "=========================================="

    # Flink
    if curl -sf http://localhost:8081/overview >/dev/null 2>&1; then
        echo -e "  Flink:    ${GREEN}运行中${NC} → http://localhost:8081"
    else
        echo -e "  Flink:    ${RED}未运行${NC}"
    fi

    # Backend
    local backend_pid="$PID_DIR/backend.pid"
    if [ -f "$backend_pid" ] && kill -0 "$(cat $backend_pid)" 2>/dev/null; then
        echo -e "  Backend:  ${GREEN}运行中${NC} → http://localhost:${SERVER_PORT:-5001} (PID: $(cat $backend_pid))"
    else
        echo -e "  Backend:  ${RED}未运行${NC}"
    fi

    # Frontend
    local frontend_pid="$PID_DIR/frontend.pid"
    if [ -f "$frontend_pid" ]; then
        echo -e "  Frontend: ${GREEN}运行中${NC} → http://localhost:8888"
    else
        echo -e "  Frontend: ${RED}未运行${NC}"
    fi

    echo "=========================================="
    echo ""
}

# 主流程
case "${1:-start}" in
    build)
        load_env
        check_prerequisites
        do_build
        ;;
    start)
        load_env
        check_prerequisites
        echo -e "${GREEN}=== 最小化单点部署（无 Docker）===${NC}"
        echo ""
        setup_flink
        start_flink
        start_backend
        start_frontend
        echo ""
        show_status
        ;;
    status)
        load_env
        show_status
        ;;
    logs)
        tail -f "$LOG_DIR/backend.log"
        ;;
    -h|--help)
        echo "用法: ./start.sh [build|start|status|logs|--help]"
        echo ""
        echo "  build   构建所有组件"
        echo "  start   启动所有服务（默认）"
        echo "  status  查看运行状态"
        echo "  logs    查看后端日志"
        ;;
    *)
        echo -e "${RED}未知命令: $1${NC}"
        echo "用法: ./start.sh [build|start|status|logs]"
        exit 1
        ;;
esac
