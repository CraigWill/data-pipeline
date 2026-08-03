#!/usr/bin/env bash
# 用 Flink 自带 flink-oss-fs-hadoop 插件做 JM/TM 侧 oss:// FileSystem 连通性测试。
# 须先 ./start.sh 拉起本目录独立 Flink（插件已挂到 $FLINK_HOME/plugins/oss-fs-hadoop）。
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
: "${OSS_BUCKET_NAME:?}"
REST_PORT="${REST_PORT:-18081}"
OSS_PREFIX="${OSS_PREFIX:-localosstest/}"
# 探测对象写在独立前缀，避免污染 checkpoint / 业务输出
PROBE_PATH="${PROBE_PATH:-oss://${OSS_BUCKET_NAME}/${OSS_PREFIX}_flink_oss_plugin_probe/}"

if ! curl -sf "http://127.0.0.1:${REST_PORT}/overview" >/dev/null 2>&1; then
    echo -e "${RED}Flink 未就绪: http://127.0.0.1:${REST_PORT}${NC}"
    echo "  先运行: $SCRIPT_DIR/start.sh"
    exit 1
fi

if [ ! -L "$FLINK_HOME/plugins/oss-fs-hadoop" ] && [ ! -d "$FLINK_HOME/plugins/oss-fs-hadoop" ]; then
    echo -e "${RED}未找到 OSS 插件目录: $FLINK_HOME/plugins/oss-fs-hadoop${NC}"
    echo "  请先 ./start.sh（会软链 runtime/plugins/oss-fs-hadoop）"
    exit 1
fi

echo -e "${BLUE}>>> 构建 jar${NC}"
(cd "$SCRIPT_DIR" && mvn -q -DskipTests package)
JAR="$SCRIPT_DIR/target/localosstest-smoke.jar"
if [ ! -f "$JAR" ]; then
    echo -e "${RED}找不到 $JAR${NC}"
    exit 1
fi

export FLINK_CONF_DIR="$CONF_DIR"
export FLINK_LOG_DIR="$LOG_DIR"
export FLINK_PID_DIR="$PID_DIR"

echo -e "${BLUE}>>> 提交 OssFsPluginProbeJob（同步等待结束）${NC}"
echo "  path=$PROBE_PATH"
echo "  plugin=$FLINK_HOME/plugins/oss-fs-hadoop"
echo "  conf=$FLINK_CONF_DIR"

# 不用 -d：批作业跑完即 FINISHED；非 0 退出表示插件/OSS 失败
set +e
"$FLINK_HOME/bin/flink" run \
    -c com.realtime.pipeline.localosstest.OssFsPluginProbeJob \
    "$JAR" \
    --path "$PROBE_PATH"
RC=$?
set -e

if [ "$RC" -ne 0 ]; then
    echo -e "${RED}✗ 探针作业失败 (exit=$RC)${NC}"
    echo "  检查 JM/TM 日志: $LOG_DIR"
    echo "  确认 config.yaml 含 fs.oss.endpoint / accessKeyId / accessKeySecret（无多余引号）"
    exit "$RC"
fi

echo -e "${GREEN}✓ Flink OSS 插件连通性测试通过${NC}"
echo "  UI: http://localhost:${REST_PORT}/#/job/completed"
