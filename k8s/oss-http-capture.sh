#!/bin/bash

# 抓取 / 查看访问阿里云 OSS 的 HTTP 报文（排 SignatureDoesNotMatch）
#
# 原理：
#   1) 临时打开 log4j：com.aliyun.oss / Apache HttpClient wire&headers
#   2) 从 Pod 日志提取 Authorization、Host、Date、错误 XML 里的 StringToSign
#   3) 可选：tcpdump（需节点有权限/debug 容器）
#
# 用法:
#   ./oss-http-capture.sh enable          # 打开 DEBUG 并滚动 JM/TM
#   ./oss-http-capture.sh disable         # 恢复 INFO
#   ./oss-http-capture.sh follow [组件]   # 实时跟日志（默认 taskmanager）
#   ./oss-http-capture.sh dump [组件]     # 导出最近 OSS 相关日志到文件
#   ./oss-http-capture.sh error [组件]    # 只提取 SignatureDoesNotMatch / StringToSign
#   ./oss-http-capture.sh tcpdump         # 说明如何抓 443 明文/TLS（受限）
#
# 组件: jobmanager(jm) | taskmanager(tm) | backend(be)  默认 tm
#
# 注意: wire 日志会含 Authorization；用完务必 ./oss-http-capture.sh disable

set -euo pipefail

NAMESPACE="${NAMESPACE:-flink}"
CM_NAME="flink-config"
BACKUP="/tmp/flink-log4j-console.properties.bak.$$"
OUT_DIR="${OUT_DIR:-./oss-http-capture}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

resolve_selector() {
    case "${1:-tm}" in
        jobmanager|jm)   echo "app=flink,component=jobmanager" ;;
        taskmanager|tm)  echo "app=flink,component=taskmanager" ;;
        backend|be)      echo "app=monitor-backend" ;;
        *)               echo "app=flink,component=taskmanager" ;;
    esac
}

resolve_container() {
    case "${1:-tm}" in
        jobmanager|jm)   echo "jobmanager" ;;
        taskmanager|tm)  echo "taskmanager" ;;
        backend|be)      echo "monitor-backend" ;;
        *)               echo "taskmanager" ;;
    esac
}

need_kubectl() {
    command -v kubectl >/dev/null || { echo -e "${RED}需要 kubectl${NC}"; exit 1; }
    kubectl cluster-info &>/dev/null || { echo -e "${RED}无法连接集群${NC}"; exit 1; }
}

# ── 打开 OSS HTTP DEBUG（仅改 ConfigMap 中的 log4j-console.properties）──
enable_debug() {
    need_kubectl
    echo -e "${YELLOW}警告: 会打印 Authorization，用完请执行 disable${NC}"

    kubectl get configmap "$CM_NAME" -n "$NAMESPACE" -o jsonpath='{.data.log4j-console\.properties}' > "$BACKUP"
    echo -e "${BLUE}已备份 log4j 到 ${BACKUP}${NC}"

    # 在现有内容后追加 logger（幂等：先去掉旧的标记块）
    local base
    base=$(kubectl get configmap "$CM_NAME" -n "$NAMESPACE" -o jsonpath='{.data.log4j-console\.properties}')
    base=$(printf '%s\n' "$base" | sed '/# BEGIN OSS-HTTP-DEBUG/,/# END OSS-HTTP-DEBUG/d')

    local patched
    patched=$(cat <<EOF
${base}

# BEGIN OSS-HTTP-DEBUG
logger.aliyunoss.name = com.aliyun.oss
logger.aliyunoss.level = DEBUG
logger.hadoopos.name = org.apache.hadoop.fs.aliyun
logger.hadoopos.level = DEBUG
logger.httpheaders.name = org.apache.http.headers
logger.httpheaders.level = DEBUG
logger.httpwire.name = org.apache.http.wire
logger.httpwire.level = DEBUG
# END OSS-HTTP-DEBUG
EOF
)

    export PATCHED_CONTENT="$patched"
    local tmp
    tmp=$(mktemp)
    python3 <<'PY' > "$tmp"
import json, os, subprocess
ns = os.environ.get("NAMESPACE", "flink")
raw = subprocess.check_output(["kubectl", "get", "configmap", "flink-config", "-n", ns, "-o", "json"])
cm = json.loads(raw)
cm["data"]["log4j-console.properties"] = os.environ["PATCHED_CONTENT"]
print(json.dumps(cm))
PY

    kubectl apply -f "$tmp"
    rm -f "$tmp"

    echo -e "${GREEN}✓ ConfigMap 已写入 OSS HTTP DEBUG${NC}"
    echo -e "${BLUE}滚动 JobManager / TaskManager 使日志配置生效...${NC}"
    kubectl rollout restart deployment/flink-jobmanager deployment/flink-taskmanager -n "$NAMESPACE"
    kubectl rollout status deployment/flink-jobmanager -n "$NAMESPACE" --timeout=180s || true
    kubectl rollout status deployment/flink-taskmanager -n "$NAMESPACE" --timeout=180s || true
    echo -e "${GREEN}完成。触发一次写 OSS（提交作业/checkpoint）后执行:${NC}"
    echo "  ./oss-http-capture.sh follow tm"
    echo "  ./oss-http-capture.sh error tm"
}

disable_debug() {
    need_kubectl
    local current
    current=$(kubectl get configmap "$CM_NAME" -n "$NAMESPACE" -o jsonpath='{.data.log4j-console\.properties}')
    local cleaned
    cleaned=$(printf '%s\n' "$current" | sed '/# BEGIN OSS-HTTP-DEBUG/,/# END OSS-HTTP-DEBUG/d')

    export PATCHED_CONTENT="$cleaned"
    local tmp
    tmp=$(mktemp)
    python3 <<'PY' > "$tmp"
import json, os, subprocess
ns=os.environ.get("NAMESPACE","flink")
raw=subprocess.check_output(["kubectl","get","configmap","flink-config","-n",ns,"-o","json"])
cm=json.loads(raw)
cm["data"]["log4j-console.properties"]=os.environ["PATCHED_CONTENT"]
print(json.dumps(cm))
PY
    kubectl apply -f "$tmp"
    rm -f "$tmp"

    kubectl rollout restart deployment/flink-jobmanager deployment/flink-taskmanager -n "$NAMESPACE"
    echo -e "${GREEN}✓ 已关闭 OSS HTTP DEBUG 并滚动重启${NC}"
}

follow_logs() {
    need_kubectl
    local comp=${1:-tm}
    local sel
    sel=$(resolve_selector "$comp")
    echo -e "${BLUE}>>> 跟随 ${comp} 日志（过滤 OSS/HTTP）${NC}"
    # Authorization / Date / Host / StringToSign / wire
    kubectl logs -n "$NAMESPACE" -l "$sel" -f --tail=50 2>/dev/null \
      | grep --line-buffered -iE \
        'aliyun\.oss|hadoop\.fs\.aliyun|http\.headers|http\.wire|Authorization|StringToSign|SignatureDoesNotMatch|Canonicalized|oss-cn-|PUT |GET |HEAD '
}

dump_logs() {
    need_kubectl
    local comp=${1:-tm}
    local sel
    sel=$(resolve_selector "$comp")
    mkdir -p "$OUT_DIR"
    local out="${OUT_DIR}/oss-http-${comp}-$(date +%Y%m%d-%H%M%S).log"
    echo -e "${BLUE}>>> 导出 ${comp} 最近日志 → ${out}${NC}"
    kubectl logs -n "$NAMESPACE" -l "$sel" --tail=5000 2>/dev/null \
      | grep -iE 'aliyun\.oss|hadoop\.fs\.aliyun|http\.headers|http\.wire|Authorization|StringToSign|SignatureDoesNotMatch|Canonicalized|OSSException' \
      > "$out" || true
    local n
    n=$(wc -l < "$out" | tr -d ' ')
    echo -e "${GREEN}写入 ${n} 行: ${out}${NC}"
    if [ "$n" = "0" ]; then
        echo -e "${YELLOW}无匹配。请先 ./oss-http-capture.sh enable，再触发一次 OSS 访问。${NC}"
    fi
}

extract_error() {
    need_kubectl
    local comp=${1:-tm}
    local sel
    sel=$(resolve_selector "$comp")
    mkdir -p "$OUT_DIR"
    local out="${OUT_DIR}/oss-error-${comp}-$(date +%Y%m%d-%H%M%S).txt"
    echo -e "${BLUE}>>> 提取 SignatureDoesNotMatch / StringToSign${NC}"
    kubectl logs -n "$NAMESPACE" -l "$sel" --tail=8000 2>/dev/null \
      | grep -iE 'SignatureDoesNotMatch|StringToSign|OSSAccessKeyId|HostId|<Error>|Authorization:|CanonicalizedResource|RequestId' \
      > "$out" || true

    if [ ! -s "$out" ]; then
        echo -e "${YELLOW}日志里还没有错误 XML。可在 Pod 里主动触发一次:${NC}"
        echo "  # TaskManager / JobManager 环境变量已有 OSS_* 时，用 ossutil 或看作业失败栈"
        echo "  ./shell.sh tm -- sh -c 'env | grep OSS_'"
        # 尝试从完整栈里抠更宽一点
        kubectl logs -n "$NAMESPACE" -l "$sel" --tail=8000 2>/dev/null \
          | grep -iE 'OSSException|com\.aliyun\.oss' | tail -80 > "$out" || true
    fi

    echo -e "${GREEN}输出: ${out}${NC}"
    echo "----- 预览（末尾 80 行）-----"
    tail -80 "$out" || true
    echo ""
    echo -e "${YELLOW}排查要点: 对比错误 XML 中的 <StringToSign> 与客户端发出的 Date/Content-Type/Host/x-oss-*${NC}"
}

tcpdump_help() {
    cat <<'EOF'
容器内通常无 root/tcpdump，且 HTTPS 抓包看不到明文。

推荐顺序:
  1) ./oss-http-capture.sh enable && 触发写 OSS && ./oss-http-capture.sh follow tm
  2) ./oss-http-capture.sh error tm
     → 错误响应里的 <StringToSign> 就是服务端认为应签名的串（排签名问题最有用）

若必须抓 TLS 外层:
  # 在节点上（需权限）
  kubectl debug node/<node> -it --image=nicolaka/netshoot -- \
    tcpdump -i any host oss-cn-shanghai.aliyuncs.com -nn -s0 -w /tmp/oss.pcap

monitor-backend（Java SDK）可临时把 logger 调到 DEBUG:
  logging.level.com.aliyun.oss=DEBUG
  logging.level.org.apache.http.wire=DEBUG
  logging.level.org.apache.http.headers=DEBUG
EOF
}

usage() {
    sed -n '2,25p' "$0" | sed 's/^# \?//'
}

export NAMESPACE

case "${1:-}" in
    enable)   enable_debug ;;
    disable)  disable_debug ;;
    follow)   follow_logs "${2:-tm}" ;;
    dump)     dump_logs "${2:-tm}" ;;
    error)    extract_error "${2:-tm}" ;;
    tcpdump)  tcpdump_help ;;
    -h|--help|help|"") usage ;;
    *) echo -e "${RED}未知命令: $1${NC}"; usage; exit 1 ;;
esac
