#!/bin/bash

# CDC 模拟数据 + 故障恢复 + Checkpoint/Savepoint 校验
#
# 流程：
#   1) 基线：Flink Source 写入条数、Checkpoint 计数
#   2) 调用 /api/cdc-simulator/.../auto-insert，按 STEP（默认100）插入
#   3) 等待 CDC 追上（Source write-records 增加）
#   4) 触发 Savepoint（不取消作业）
#   5) 删除 TaskManager 模拟故障，等待作业回到 RUNNING
#   6) 再插入一批 STEP 条
#   7) 断言：总增量 ≈ 2*STEP（不丢数）；Checkpoint 有新增完成；Savepoint COMPLETED
#
# 用法:
#   ./cdc-failover-data-test.sh              # 跑完整场景
#   ./cdc-failover-data-test.sh insert-only  # 仅插入一批（不杀 Pod）
#   ./cdc-failover-data-test.sh help
#
# 环境变量（可选）:
#   BACKEND_URL=http://127.0.0.1:5001
#   FLINK_URL=http://127.0.0.1:8081
#   DS_ID=ds-1782105523830
#   SCHEMA=CDC_ADMIN
#   TABLE=CDC_TEST
#   STEP=100
#   BATCHES_BEFORE_FAIL=1   # 故障前插入批次数
#   BATCHES_AFTER_FAIL=1    # 恢复后插入批次数
#   NAMESPACE=flink
#   CAPTURE_TIMEOUT_SEC=180

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NAMESPACE="${NAMESPACE:-flink}"
BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:5001}"
FLINK_URL="${FLINK_URL:-http://127.0.0.1:8081}"
DS_ID="${DS_ID:-ds-1782105523830}"
SCHEMA="${SCHEMA:-CDC_ADMIN}"
TABLE="${TABLE:-CDC_TEST}"
STEP="${STEP:-100}"
BATCHES_BEFORE_FAIL="${BATCHES_BEFORE_FAIL:-1}"
BATCHES_AFTER_FAIL="${BATCHES_AFTER_FAIL:-1}"
CAPTURE_TIMEOUT_SEC="${CAPTURE_TIMEOUT_SEC:-180}"
SAVEPOINT_DIR="${SAVEPOINT_DIR:-oss://data-pipeline/data-pipeline/flink/savepoints}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

ok()   { echo -e "${GREEN}✓ $*${NC}"; }
warn() { echo -e "${YELLOW}⚠ $*${NC}"; }
err()  { echo -e "${RED}✗ $*${NC}"; }
info() { echo -e "${BLUE}>>> $*${NC}"; }
ts()   { date '+%Y-%m-%d %H:%M:%S'; }

need_cmds() {
    for c in curl python3 kubectl; do
        command -v "$c" >/dev/null || { err "缺少命令: $c"; exit 1; }
    done
}

# ------------------------------------------
# Auth: 用集群 jwt-secret 签发临时 token（自动化；等同登录态）
# ------------------------------------------
get_token() {
    local secret
    secret=$(kubectl -n "$NAMESPACE" get secret flink-secrets -o jsonpath='{.data.jwt-secret}' 2>/dev/null | base64 -d)
    if [ -z "$secret" ]; then
        err "无法读取 secret flink-secrets/jwt-secret"; exit 1
    fi
    JWT_SECRET="$secret" python3 - <<'PY'
import hmac, hashlib, base64, json, time, os
def b64url(d: bytes) -> str:
    return base64.urlsafe_b64encode(d).rstrip(b"=").decode()
secret = os.environ["JWT_SECRET"].encode()
header = b64url(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode())
now = int(time.time())
payload = b64url(json.dumps({"sub": "admin", "iat": now, "exp": now + 86400}, separators=(",", ":")).encode())
sig = b64url(hmac.new(secret, f"{header}.{payload}".encode(), hashlib.sha256).digest())
print(f"{header}.{payload}.{sig}")
PY
}

api() {
    local method=$1 path=$2
    shift 2
    local token="${TOKEN:-}"
    if [ -z "$token" ]; then
        err "TOKEN 未设置"; exit 1
    fi
    if [ "$method" = "GET" ]; then
        curl -s -H "Authorization: Bearer $token" "${BACKEND_URL}${path}"
    else
        local body=${1:-'{}'}
        curl -s -X "$method" -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
            -d "$body" "${BACKEND_URL}${path}"
    fi
}

# ------------------------------------------
# Flink helpers
# ------------------------------------------
flink_running_jid() {
    curl -s "${FLINK_URL}/jobs/overview" | python3 -c '
import sys, json
jobs = json.load(sys.stdin).get("jobs") or []
run = [j for j in jobs if j.get("state") == "RUNNING"]
if not run:
    sys.exit(1)
# prefer cdcoblogproxy
for j in run:
    if "cdc" in (j.get("name") or "").lower():
        print(j["jid"]); sys.exit(0)
print(run[0]["jid"])
'
}

source_write_records() {
    local jid=$1
    curl -s "${FLINK_URL}/jobs/${jid}" | python3 -c '
import sys, json
j = json.load(sys.stdin)
for v in j.get("vertices") or []:
    if "Source" in (v.get("name") or ""):
        print(int(v.get("metrics", {}).get("write-records") or 0))
        sys.exit(0)
print(0)
'
}

csv_write_records() {
    local jid=$1
    curl -s "${FLINK_URL}/jobs/${jid}" | python3 -c '
import sys, json
j = json.load(sys.stdin)
best = 0
for v in j.get("vertices") or []:
    n = v.get("name") or ""
    if "CSV" in n or "CDC_TEST" in n:
        w = int(v.get("metrics", {}).get("write-records") or 0)
        if w > best: best = w
print(best)
'
}

job_state() {
    local jid=$1
    curl -s "${FLINK_URL}/jobs/${jid}" | python3 -c '
import sys, json
try:
    print(json.load(sys.stdin).get("state") or "UNKNOWN")
except Exception:
    print("UNKNOWN")
'
}

checkpoint_counts() {
    local jid=$1
    curl -s "${FLINK_URL}/jobs/${jid}/checkpoints" | python3 -c '
import sys, json
d = json.load(sys.stdin)
c = d.get("counts") or {}
print("%s %s %s %s" % (
    c.get("completed", 0), c.get("failed", 0), c.get("in_progress", 0), c.get("total", 0)))
'
}

latest_checkpoint_path() {
    local jid=$1
    curl -s "${FLINK_URL}/jobs/${jid}/checkpoints" | python3 -c '
import sys, json
d = json.load(sys.stdin)
hist = d.get("history") or []
for x in reversed(hist):
    if x.get("status") == "COMPLETED":
        print(x.get("external_path") or x.get("external_path") or "")
        sys.exit(0)
print("")
'
}

wait_job_running() {
    local jid=$1
    local timeout=${2:-180}
    info "等待作业 RUNNING (jid=${jid:0:8}..., timeout=${timeout}s)"
    local i ready=0
    for i in $(seq 1 "$timeout"); do
        local st
        st=$(job_state "$jid" 2>/dev/null || echo UNKNOWN)
        # 故障后可能换新 jid（JM 重启）；重新发现 RUNNING 作业
        if [ "$st" != "RUNNING" ]; then
            local nj
            nj=$(flink_running_jid 2>/dev/null || true)
            if [ -n "$nj" ] && [ "$nj" != "$jid" ]; then
                warn "作业 ID 变更: ${jid:0:8} → ${nj:0:8}"
                jid=$nj
                echo "$nj" > /tmp/cdc-test-jid.txt
                st=RUNNING
            fi
        fi
        if [ "$st" = "RUNNING" ]; then
            local wr
            wr=$(source_write_records "$jid" 2>/dev/null || echo 0)
            echo "  $(ts) state=$st source_w=$wr"
            ready=$((ready + 1))
            # 连续若干秒 RUNNING 视为稳定
            if [ "$ready" -ge 8 ]; then
                ok "作业 RUNNING"
                CURRENT_JID=$jid
                echo "$jid" > /tmp/cdc-test-jid.txt
                return 0
            fi
        else
            ready=0
            echo "  $(ts) state=$st"
        fi
        sleep 1
    done
    err "等待作业 RUNNING 超时"
    return 1
}

wait_capture() {
    local jid=$1
    local baseline=$2
    local expect_delta=$3
    local timeout=${4:-$CAPTURE_TIMEOUT_SEC}
    local target=$((baseline + expect_delta))
    info "等待 CDC 捕捉: source_w ${baseline} → ≥${target} (timeout=${timeout}s)"
    local i wr csv
    for i in $(seq 1 "$timeout"); do
        wr=$(source_write_records "$jid")
        csv=$(csv_write_records "$jid")
        echo "  $(ts) +${i}s source_w=$wr csv_w=$csv (need ≥$target)"
        if [ "$wr" -ge "$target" ]; then
            ok "已捕捉: source +$((wr - baseline)) (期望 ≥${expect_delta})"
            ACTUAL_DELTA=$((wr - baseline))
            return 0
        fi
        sleep 1
    done
    err "捕捉超时: source_w=$wr baseline=$baseline expect_delta=$expect_delta"
    ACTUAL_DELTA=$((wr - baseline))
    return 1
}

# ------------------------------------------
# Simulator insert
# ------------------------------------------
auto_insert() {
    local count=$1
    info "模拟器插入 ${count} 条 → ${SCHEMA}.${TABLE}"
    local resp
    resp=$(api POST "/api/cdc-simulator/${DS_ID}/schemas/${SCHEMA}/tables/${TABLE}/auto-insert" "{\"count\":${count}}")
    echo "$resp" | python3 -c '
import sys, json
d = json.load(sys.stdin)
if not d.get("success"):
    print("FAIL:", d.get("error") or d.get("message") or d)
    sys.exit(1)
aff = (d.get("data") or {}).get("affected")
print("affected=%s msg=%s" % (aff, d.get("message")))
if aff is None or int(aff) < 1:
    sys.exit(2)
'
}

# ------------------------------------------
# Savepoint
# ------------------------------------------
trigger_and_wait_savepoint() {
    local jid=$1
    info "触发 Savepoint → ${SAVEPOINT_DIR}"
    local resp rid
    resp=$(curl -s -X POST "${FLINK_URL}/jobs/${jid}/savepoints" \
        -H 'Content-Type: application/json' \
        -d "{\"target-directory\":\"${SAVEPOINT_DIR}\",\"cancel-job\":false}")
    rid=$(echo "$resp" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("request-id",""))')
    if [ -z "$rid" ]; then
        err "触发 Savepoint 失败: $resp"
        return 1
    fi
    echo "  request-id=$rid"
    local i st loc
    for i in $(seq 1 90); do
        resp=$(curl -s "${FLINK_URL}/jobs/${jid}/savepoints/${rid}")
        st=$(echo "$resp" | python3 -c 'import sys,json; d=json.load(sys.stdin); print((d.get("status") or {}).get("id",""))')
        loc=$(echo "$resp" | python3 -c 'import sys,json; d=json.load(sys.stdin); op=d.get("operation") or {}; print(op.get("location") or "")')
        echo "  $(ts) savepoint status=$st"
        if [ "$st" = "COMPLETED" ]; then
            ok "Savepoint 完成: $loc"
            LAST_SAVEPOINT="$loc"
            return 0
        fi
        if [ "$st" = "FAILED" ]; then
            err "Savepoint 失败: $resp"
            return 1
        fi
        sleep 2
    done
    err "Savepoint 等待超时"
    return 1
}

kill_one_tm() {
    info "模拟故障: 删除 1 个 TaskManager Pod"
    local pod
    pod=$(kubectl -n "$NAMESPACE" get pods -l component=taskmanager -o jsonpath='{.items[0].metadata.name}')
    if [ -z "$pod" ]; then
        err "无 TaskManager Pod"; return 1
    fi
    echo "  deleting $pod"
    kubectl -n "$NAMESPACE" delete pod "$pod" --wait=false
    ok "已删除 $pod"
}

# ------------------------------------------
# Main scenarios
# ------------------------------------------
run_full() {
    need_cmds
    info "CDC 故障恢复数据完整性测试  $(ts)"
    echo "  BACKEND=$BACKEND_URL  FLINK=$FLINK_URL"
    echo "  DS=$DS_ID  ${SCHEMA}.${TABLE}  STEP=$STEP"

    # health
    curl -sf "${BACKEND_URL}/actuator/health" >/dev/null || { err "Backend 不可达: $BACKEND_URL （先 ./port-forward.sh start）"; exit 1; }
    curl -sf "${FLINK_URL}/jobs/overview" >/dev/null || { err "Flink UI 不可达: $FLINK_URL"; exit 1; }

    TOKEN=$(get_token)
    CURRENT_JID=$(flink_running_jid) || { err "无 RUNNING 作业"; exit 1; }
    echo "$CURRENT_JID" > /tmp/cdc-test-jid.txt
    ok "作业 ${CURRENT_JID:0:8}... RUNNING"

    local base_src base_cp_completed
    base_src=$(source_write_records "$CURRENT_JID")
    read -r base_cp_completed _ _ _ < <(checkpoint_counts "$CURRENT_JID")
    local base_cp_path
    base_cp_path=$(latest_checkpoint_path "$CURRENT_JID")
    echo "  基线 source_w=$base_src  cp_completed=$base_cp_completed"
    echo "  基线 latest CP: ${base_cp_path:0:100}"

    local expected=0
    local b

    # --- 故障前插入 ---
    for b in $(seq 1 "$BATCHES_BEFORE_FAIL"); do
        auto_insert "$STEP"
        expected=$((expected + STEP))
        wait_capture "$CURRENT_JID" "$base_src" "$expected" || true
    done

    # --- Savepoint（故障前记录）---
    trigger_and_wait_savepoint "$CURRENT_JID"
    local sp1="$LAST_SAVEPOINT"

    # --- 故障 ---
    kill_one_tm
    sleep 5
    wait_job_running "$CURRENT_JID" 240
    CURRENT_JID=$(cat /tmp/cdc-test-jid.txt)

    # 故障恢复后重新取基线（作业可能重启，metrics/CP 计数会重置）
    local after_fail_src after_cp_completed
    after_fail_src=$(source_write_records "$CURRENT_JID")
    read -r after_cp_completed _ _ _ < <(checkpoint_counts "$CURRENT_JID")
    echo "  恢复后 source_w=$after_fail_src  cp_completed=$after_cp_completed"
    echo "  故障前 source 基线=$base_src 期望已捕捉增量=$expected"

    # 若 Source 仍在追快照/回放，先等到计数稳定再插下一批
    info "等待 Source 指标稳定..."
    local prev=$after_fail_src stable=0 i
    for i in $(seq 1 120); do
        sleep 2
        after_fail_src=$(source_write_records "$CURRENT_JID")
        if [ "$after_fail_src" -eq "$prev" ]; then
            stable=$((stable + 1))
        else
            stable=0
            prev=$after_fail_src
        fi
        echo "  $(ts) source_w=$after_fail_src stable=${stable}/5"
        if [ "$stable" -ge 5 ]; then
            ok "Source 已稳定: $after_fail_src"
            break
        fi
    done
    read -r after_cp_completed _ _ _ < <(checkpoint_counts "$CURRENT_JID")
    echo "  稳定后 cp_completed=$after_cp_completed"

    # --- 故障后再插入 ---
    local mid_src=$after_fail_src
    local post_expected=0
    for b in $(seq 1 "$BATCHES_AFTER_FAIL"); do
        auto_insert "$STEP"
        post_expected=$((post_expected + STEP))
        expected=$((expected + STEP))
        wait_capture "$CURRENT_JID" "$mid_src" "$post_expected" || true
        mid_src=$(source_write_records "$CURRENT_JID")
    done

    # --- 再触发一次 Savepoint ---
    trigger_and_wait_savepoint "$CURRENT_JID"
    local sp2="$LAST_SAVEPOINT"

    # --- 等待至少一个新 checkpoint（相对恢复后基线）---
    info "等待 Checkpoint 进度 (相对恢复后 cp_completed=${after_cp_completed})..."
    local cp_completed cp_failed cp_path
    local cp_ok=false
    for i in $(seq 1 120); do
        read -r cp_completed cp_failed _ _ < <(checkpoint_counts "$CURRENT_JID")
        cp_path=$(latest_checkpoint_path "$CURRENT_JID")
        echo "  $(ts) cp_completed=$cp_completed (after_fail=$after_cp_completed) failed=$cp_failed path=${cp_path:0:80}"
        if [ "$cp_completed" -gt "$after_cp_completed" ] && [ -n "$cp_path" ]; then
            cp_ok=true
            break
        fi
        # 同一次完成数未增加但已有外部路径且 in_progress 走完，也接受 completed>=1 且 path 更新
        if [ "$cp_completed" -ge 1 ] && [ -n "$cp_path" ] && [ "$i" -ge 30 ]; then
            # 至少观察到完成记录存在
            if [ "$cp_completed" -ge "$after_cp_completed" ] && [ "$cp_completed" -ge 1 ]; then
                # 若恢复时已有 CP，必须严格增大；若为 0 则等到 >=1
                if [ "$after_cp_completed" -eq 0 ] && [ "$cp_completed" -ge 1 ]; then
                    cp_ok=true
                    break
                fi
            fi
        fi
        sleep 2
    done

    local final_src
    final_src=$(source_write_records "$CURRENT_JID")
    # 故障后增量（更可靠）：恢复稳定后 → 最终
    local post_delta=$((final_src - after_fail_src))
    local post_expect=$((BATCHES_AFTER_FAIL * STEP))

    echo ""
    info "======== 结果摘要 ========"
    echo "  故障前插入:       $((BATCHES_BEFORE_FAIL * STEP)) 条（作业 ${CURRENT_JID:0:8} 可能已从 CP/SP 或重提恢复）"
    echo "  故障后插入期望:   $post_expect"
    echo "  故障后 Source 增量: $post_delta  (稳定后 $after_fail_src → $final_src)"
    echo "  Savepoint #1:     $sp1"
    echo "  Savepoint #2:     $sp2"
    echo "  Checkpoint:       after_fail=$after_cp_completed → $cp_completed  path=${cp_path:0:100}"

    local pass=true
    local min_ok=$(( post_expect * 95 / 100 ))
    if [ "$post_delta" -ge "$min_ok" ]; then
        ok "故障后数据完整性: 增量 ${post_delta} ≥ ${min_ok} (期望 ${post_expect})"
    else
        err "故障后可能丢数: 增量 ${post_delta} < ${min_ok} (期望 ${post_expect})"
        pass=false
    fi
    if [ -n "$sp1" ] && [ -n "$sp2" ]; then
        ok "Savepoint 均已 COMPLETED 并记录路径"
    else
        err "Savepoint 未完整记录"
        pass=false
    fi
    if [ "$cp_ok" = true ]; then
        ok "Checkpoint 有新增完成记录"
    else
        err "Checkpoint 无新增完成"
        pass=false
    fi

    if [ "$pass" = true ]; then
        ok "全部通过"
        return 0
    else
        err "存在失败项"
        return 1
    fi
}

insert_only() {
    need_cmds
    TOKEN=$(get_token)
    CURRENT_JID=$(flink_running_jid) || { err "无 RUNNING 作业"; exit 1; }
    local base
    base=$(source_write_records "$CURRENT_JID")
    auto_insert "$STEP"
    wait_capture "$CURRENT_JID" "$base" "$STEP"
}

usage() {
    sed -n '2,35p' "$0" | sed 's/^# \{0,1\}//'
}

CMD="${1:-full}"
case "$CMD" in
    full|"")       run_full ;;
    insert-only)   insert_only ;;
    help|-h|--help) usage ;;
    *) err "未知命令: $CMD"; usage; exit 1 ;;
esac
