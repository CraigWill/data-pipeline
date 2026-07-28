#!/usr/bin/env bash
# 构建并提交 OssLocalSmokeJob 到 localosstest Flink 集群
set -euo pipefail

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RUNTIME_DIR="$SCRIPT_DIR/runtime"
CONF_DIR="$RUNTIME_DIR/conf"
LOG_DIR="$RUNTIME_DIR/logs"
PID_DIR="$RUNTIME_DIR/pids"

# shellcheck disable=SC1091
source "$SCRIPT_DIR/env.sh"

: "${FLINK_HOME:?}"
: "${OUTPUT_PATH:?}"
REST_PORT="${REST_PORT:-18081}"
CHECKPOINT_INTERVAL_MS="${CHECKPOINT_INTERVAL_MS:-10000}"

if ! curl -sf "http://localhost:${REST_PORT}/overview" >/dev/null 2>&1; then
    echo -e "${RED}Flink 未就绪: http://localhost:${REST_PORT}${NC}"
    echo "  先运行: $SCRIPT_DIR/start.sh"
    exit 1
fi

echo -e "${BLUE}>>> 构建 smoke jar${NC}"
(cd "$SCRIPT_DIR" && mvn -q -DskipTests package)
JAR="$SCRIPT_DIR/target/localosstest-smoke.jar"
if [ ! -f "$JAR" ]; then
    echo -e "${RED}找不到 $JAR${NC}"
    exit 1
fi

export FLINK_CONF_DIR="$CONF_DIR"
export FLINK_LOG_DIR="$LOG_DIR"
export FLINK_PID_DIR="$PID_DIR"

echo -e "${BLUE}>>> 提交作业${NC}"
echo "  output=$OUTPUT_PATH"
echo "  ui=http://localhost:${REST_PORT}"

"$FLINK_HOME/bin/flink" run -d \
    -c com.realtime.pipeline.localosstest.OssLocalSmokeJob \
    "$JAR" \
    --output "$OUTPUT_PATH" \
    --checkpoint-interval-ms "$CHECKPOINT_INTERVAL_MS" \
    --sleep-ms 500 \
    --roll-on-checkpoint true

echo -e "${GREEN}✓ 已提交（detached）${NC}"
echo "  UI: http://localhost:${REST_PORT}/#/job/running"
echo "  观察 OSS:"
echo "    - checkpoint: $CHECKPOINT_DIR"
echo "    - 输出文件 : $OUTPUT_PATH"
echo "  探针（可选）: mvn -pl flink-oss -am -DskipTests package && java -jar flink-oss/target/flink-oss-probe.jar"
