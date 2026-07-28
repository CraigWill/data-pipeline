#!/bin/bash

# Kubernetes 高可用故障转移 / Pod 自愈测试
#
# 场景：
#   1) Pod 被删除后由 Deployment 自动重建
#   2) 容器 OOMKilled 后自动重启 / 替换
#   3) 多副本组件(monitor-backend)在单实例故障时仍可服务
#   4) Flink JobManager / TaskManager 故障后观察作业与副本恢复
#
# 用法:
#   ./ha-failover-test.sh status              # 基线：副本、作业、事件
#   ./ha-failover-test.sh kill-tm             # 删除 1 个 TaskManager，等待恢复
#   ./ha-failover-test.sh kill-jm             # 删除 JobManager，等待恢复
#   ./ha-failover-test.sh kill-backend        # 删除 1 个 Backend，验证副本与 API
#   ./ha-failover-test.sh mem-oom-tm [MB]     # TM 内灌内存触发 OOM(默认 3500)
#   ./ha-failover-test.sh mem-oom-backend [MB]# Backend 内灌内存触发 OOM(默认 1800)
#   ./ha-failover-test.sh full                # 顺序跑完上述场景(含内存压测)
#   ./ha-failover-test.sh help
#
# 依赖: kubectl；内存压测需本机 javac(编译 MemStress.java 后 kubectl cp 进 Pod)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NAMESPACE="${NAMESPACE:-flink}"
MEM_STRESS_SRC="$SCRIPT_DIR/tools/MemStress.java"
TIMEOUT_SEC="${TIMEOUT_SEC:-180}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

ok()   { echo -e "${GREEN}✓ $*${NC}"; }
warn() { echo -e "${YELLOW}⚠ $*${NC}"; }
err()  { echo -e "${RED}✗ $*${NC}"; }
info() { echo -e "${BLUE}>>> $*${NC}"; }

need_kubectl() {
    command -v kubectl >/dev/null || { err "kubectl 未安装"; exit 1; }
    kubectl cluster-info >/dev/null 2>&1 || { err "无法连接集群"; exit 1; }
}

ts() { date '+%Y-%m-%d %H:%M:%S'; }

# ------------------------------------------
# 通用观察
# ------------------------------------------
print_pods() {
    echo ""
    echo "── Pods ($(ts)) ──"
    kubectl -n "$NAMESPACE" get pods -o wide 2>/dev/null || true
}

print_jobs() {
    echo ""
    echo "── Flink Jobs ──"
    local jm
    jm=$(kubectl -n "$NAMESPACE" get pods -l component=jobmanager -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)
    if [ -z "$jm" ]; then
        warn "无 JobManager Pod"
        return
    fi
    kubectl -n "$NAMESPACE" exec "$jm" -- curl -s http://localhost:8081/jobs/overview 2>/dev/null \
        | python3 -c '
import sys, json
try:
    d = json.load(sys.stdin)
    jobs = d.get("jobs", [])
    if not jobs:
        print("(no jobs)")
    for j in jobs:
        jid = (j.get("jid") or "?")[:8]
        name = j.get("name")
        state = j.get("state")
        dur = round((j.get("duration") or 0) / 1000)
        print("  %s  %s  %s  dur=%ss" % (jid, name, state, dur))
except Exception as e:
    print("  (无法解析 Flink jobs: %s)" % e)
' 2>/dev/null || warn "无法查询 Flink REST"
}

print_events_related() {
    local name=$1
    echo ""
    echo "── 相关事件 (含 ${name}) ──"
    kubectl -n "$NAMESPACE" get events --sort-by='.lastTimestamp' 2>/dev/null \
        | grep -iE "${name}|OOM|Killing|Started|Created|Failed|Unhealthy" | tail -15 || true
}

wait_deploy_ready() {
    local deploy=$1
    local want=${2:-}
    info "等待 Deployment/${deploy} 就绪 (timeout=${TIMEOUT_SEC}s)..."
    if ! kubectl -n "$NAMESPACE" rollout status "deployment/${deploy}" --timeout="${TIMEOUT_SEC}s"; then
        err "Deployment/${deploy} 未在 ${TIMEOUT_SEC}s 内就绪"
        return 1
    fi
    if [ -n "$want" ]; then
        local ready
        ready=$(kubectl -n "$NAMESPACE" get deploy "${deploy}" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo 0)
        ready=${ready:-0}
        if [ "$ready" -lt "$want" ]; then
            err "期望 Ready>=$want，当前=$ready"
            return 1
        fi
    fi
    ok "Deployment/${deploy} 已就绪"
}

wait_pod_gone() {
    local pod=$1
    local i=0
    while kubectl -n "$NAMESPACE" get pod "${pod}" >/dev/null 2>&1; do
        i=$((i+1))
        if [ "$i" -gt "$TIMEOUT_SEC" ]; then
            warn "Pod ${pod} 仍存在(可能正在 Terminating)"
            return 1
        fi
        sleep 1
    done
    ok "旧 Pod 已消失: ${pod}"
}

# 等待新 Pod 出现且 Ready(排除旧名)
wait_component_ready() {
    local selector=$1
    local old_pod=${2:-}
    local i=0
    while [ "$i" -lt "$TIMEOUT_SEC" ]; do
        local line
        line=$(kubectl -n "$NAMESPACE" get pods -l "${selector}" --no-headers 2>/dev/null \
            | awk '$3=="Running" && $2 ~ /^[0-9]+\/[0-9]+$/ {
                split($2,a,"/"); if (a[1]==a[2] && a[1]>0) print $1
              }' | head -1)
        if [ -n "${line}" ] && [ "${line}" != "${old_pod}" ]; then
            ok "组件恢复: ${line}"
            return 0
        fi
        # 同名重启(OOM 后 restartCount 增加且 Ready)
        if [ -n "${old_pod}" ] && kubectl -n "$NAMESPACE" get pod "${old_pod}" >/dev/null 2>&1; then
            local ready restarts
            ready=$(kubectl -n "$NAMESPACE" get pod "${old_pod}" -o jsonpath='{.status.containerStatuses[0].ready}' 2>/dev/null || echo false)
            restarts=$(kubectl -n "$NAMESPACE" get pod "${old_pod}" -o jsonpath='{.status.containerStatuses[0].restartCount}' 2>/dev/null || echo 0)
            if [ "$ready" = "true" ]; then
                ok "Pod 已恢复 Ready: ${old_pod} (restarts=${restarts})"
                return 0
            fi
        fi
        i=$((i+5))
        sleep 5
        echo -n "."
    done
    echo
    err "等待组件恢复超时: ${selector}"
    return 1
}

pick_pod() {
    local selector=$1
    kubectl -n "$NAMESPACE" get pods -l "${selector}" --field-selector=status.phase=Running \
        -o jsonpath='{.items[0].metadata.name}' 2>/dev/null
}

container_name_for() {
    case "$1" in
        *taskmanager*) echo "taskmanager" ;;
        *jobmanager*)  echo "jobmanager" ;;
        *monitor-backend*) echo "monitor-backend" ;;
        *) echo "" ;;
    esac
}

# ------------------------------------------
# 内存压测：本机编译 → kubectl cp → java 运行
# ------------------------------------------
compile_mem_stress() {
    if [ ! -f "$MEM_STRESS_SRC" ]; then
        err "缺少 $MEM_STRESS_SRC"
        exit 1
    fi
    if ! command -v javac >/dev/null; then
        err "本机需要 javac 以编译 MemStress"
        exit 1
    fi
    local out="$SCRIPT_DIR/tools"
    javac -d "$out" "$MEM_STRESS_SRC"
    ok "已编译 MemStress.class"
}

run_mem_oom() {
    local selector=$1
    local target_mb=${2:-2048}
    local pod cname
    pod=$(pick_pod "${selector}")
    [ -n "${pod}" ] || { err "无 Running Pod: ${selector}"; return 1; }
    cname=$(container_name_for "${pod}")
    [ -n "${cname}" ] || cname=$(kubectl -n "$NAMESPACE" get pod "${pod}" -o jsonpath='{.spec.containers[0].name}')

    info "目标 Pod=${pod} 容器=${cname} 申请≈${target_mb}MB(期望触发 OOMKilled)"
    compile_mem_stress

    local before_restarts
    before_restarts=$(kubectl -n "$NAMESPACE" get pod "${pod}" -o jsonpath='{.status.containerStatuses[0].restartCount}' 2>/dev/null || echo 0)
    echo "  当前 restartCount=${before_restarts}"

    kubectl -n "$NAMESPACE" cp "$SCRIPT_DIR/tools/MemStress.class" "${pod}:/tmp/MemStress.class" -c "${cname}"
    # 使用较小 -Xmx，让堆外/本机内存或多次分配更容易顶满 cgroup limit
    # 后台执行；进程被 OOM killer 杀掉时 exec 可能非 0，属预期
    set +e
    kubectl -n "$NAMESPACE" exec "${pod}" -c "${cname}" -- \
        java -Xmx64m -XX:MaxDirectMemorySize=8g -cp /tmp MemStress "${target_mb}" 64
    local rc=$?
    set -e
    echo "  MemStress exit code=$rc(OOM 时常为 137 或连接中断)"

    info "观察 OOM / 重启..."
    local i=0
    while [ "$i" -lt "$TIMEOUT_SEC" ]; do
        if ! kubectl -n "$NAMESPACE" get pod "${pod}" >/dev/null 2>&1; then
            ok "Pod 已被替换(新副本由 Deployment 创建)"
            wait_component_ready "${selector}" ""
            print_events_related "$(echo "${selector}" | tr ',' '|' | sed 's/app=//g;s/component=//g')"
            return 0
        fi
        local ready restarts last_state reason
        ready=$(kubectl -n "$NAMESPACE" get pod "${pod}" -o jsonpath='{.status.containerStatuses[0].ready}' 2>/dev/null || echo false)
        restarts=$(kubectl -n "$NAMESPACE" get pod "${pod}" -o jsonpath='{.status.containerStatuses[0].restartCount}' 2>/dev/null || echo 0)
        reason=$(kubectl -n "$NAMESPACE" get pod "${pod}" -o jsonpath='{.status.containerStatuses[0].lastState.terminated.reason}' 2>/dev/null || true)
        if [ "${restarts:-0}" -gt "${before_restarts:-0}" ]; then
            ok "检测到重启: restartCount ${before_restarts} → ${restarts}  lastReason=${reason:-n/a}"
            wait_component_ready "${selector}" "${pod}"
            print_events_related "OOM|${pod}"
            return 0
        fi
        if [ "$reason" = "OOMKilled" ]; then
            ok "确认 OOMKilled，等待容器 Ready..."
        fi
        i=$((i+5))
        sleep 5
        echo -n "."
    done
    echo
    err "未在超时内观察到 OOM/重启。可调大 MB 或检查 limits.memory"
    kubectl -n "$NAMESPACE" describe pod "${pod}" | tail -40 || true
    return 1
}

# ------------------------------------------
# 场景
# ------------------------------------------
cmd_status() {
    info "HA 基线状态"
    print_pods
    echo ""
    echo "── Deployments ──"
    kubectl -n "$NAMESPACE" get deploy -o wide 2>/dev/null || true
    echo ""
    echo "── HPA ──"
    kubectl -n "$NAMESPACE" get hpa 2>/dev/null || warn "无 HPA(可先 ./autoscale.sh apply)"
    print_jobs
    echo ""
    ok "基线采集完成 ($(ts))"
}

cmd_kill_tm() {
    local pod
    pod=$(pick_pod "app=flink,component=taskmanager")
    [ -n "${pod}" ] || { err "无 Running TaskManager"; exit 1; }
    local replicas
    replicas=$(kubectl -n "$NAMESPACE" get deploy flink-taskmanager -o jsonpath='{.spec.replicas}')
    info "删除 TaskManager Pod: ${pod} (expect replicas=${replicas})"
    print_jobs
    kubectl -n "$NAMESPACE" delete pod "${pod}" --wait=false
    wait_pod_gone "${pod}" || true
    wait_deploy_ready flink-taskmanager "${replicas}"
    wait_component_ready "app=flink,component=taskmanager" "${pod}"
    print_pods
    print_jobs
    print_events_related "taskmanager|Killing|Started"
    ok "TaskManager 故障转移/自愈场景完成"
}

cmd_kill_jm() {
    local pod
    pod=$(pick_pod "app=flink,component=jobmanager")
    [ -n "${pod}" ] || { err "无 Running JobManager"; exit 1; }
    info "删除 JobManager Pod: ${pod}"
    info "说明: 当前 JM replicas 多为 1；验证的是 Deployment 重建 + 作业从 checkpoint/HA 恢复"
    print_jobs
    kubectl -n "$NAMESPACE" delete pod "${pod}" --wait=false
    wait_pod_gone "${pod}" || true
    wait_deploy_ready flink-jobmanager 1
    wait_component_ready "app=flink,component=jobmanager" "${pod}"
    # JM 起来后稍等 REST / 作业恢复
    sleep 10
    print_pods
    print_jobs
    print_events_related "jobmanager|Killing|Started"
    ok "JobManager 自愈场景完成(请核对作业是否回到 RUNNING)"
}

cmd_kill_backend() {
    local pods count
    count=$(kubectl -n "$NAMESPACE" get pods -l app=monitor-backend --field-selector=status.phase=Running --no-headers 2>/dev/null | wc -l | tr -d ' ')
    if [ "${count:-0}" -lt 2 ]; then
        warn "monitor-backend Running 副本 < 2(当前=${count})。HA 建议 replicas>=2"
        warn "继续删除 1 个实例以验证 Deployment 重建..."
    fi
    local pod
    pod=$(pick_pod "app=monitor-backend")
    [ -n "${pod}" ] || { err "无 Running monitor-backend"; exit 1; }

    info "删除 Backend Pod: ${pod}"
    # 故障中探测：若仍有其他副本，API 应可用
    local survivors
    survivors=$((count - 1))
    kubectl -n "$NAMESPACE" delete pod "${pod}" --wait=false

    if [ "$survivors" -ge 1 ]; then
        sleep 2
        if kubectl -n "$NAMESPACE" exec deploy/monitor-backend -- curl -sf http://127.0.0.1:5001/actuator/health >/dev/null 2>&1; then
            ok "删除过程中其它副本仍可响应 /actuator/health"
        else
            warn "短暂无法通过 deploy 访问 health(可能选到了正在终止的 Pod)"
        fi
    fi

    wait_pod_gone "${pod}" || true
    wait_deploy_ready monitor-backend
    wait_component_ready "app=monitor-backend" "${pod}"
    if kubectl -n "$NAMESPACE" exec deploy/monitor-backend -- curl -sf http://127.0.0.1:5001/actuator/health >/dev/null 2>&1; then
        ok "Backend API 恢复健康"
    else
        err "Backend health 检查失败"
    fi
    print_pods
    print_events_related "monitor-backend|Killing|Started"
    ok "Backend 故障转移场景完成"
}

cmd_mem_oom_tm() {
    local mb=${1:-3500}
    info "TaskManager 内存压测 → OOMKilled(limits.memory 通常为 4Gi，建议 MB=3000~3800)"
    print_jobs
    run_mem_oom "app=flink,component=taskmanager" "${mb}"
    sleep 5
    print_pods
    print_jobs
    ok "TM OOM 自愈场景完成"
}

cmd_mem_oom_backend() {
    local mb=${1:-1800}
    info "Backend 内存压测 → OOMKilled(limits.memory 通常为 2Gi，建议 MB=1500~1900)"
    run_mem_oom "app=monitor-backend" "${mb}"
    sleep 5
    print_pods
    if kubectl -n "$NAMESPACE" exec deploy/monitor-backend -- curl -sf http://127.0.0.1:5001/actuator/health >/dev/null 2>&1; then
        ok "Backend API 在 OOM 恢复后健康"
    fi
    ok "Backend OOM 自愈场景完成"
}

cmd_full() {
    info "======== 全量 HA 自愈测试开始 $(ts) ========"
    cmd_status
    echo ""
    info "==== [1/5] kill-tm ===="
    cmd_kill_tm
    echo ""
    info "==== [2/5] kill-backend ===="
    cmd_kill_backend
    echo ""
    info "==== [3/5] kill-jm ===="
    cmd_kill_jm
    echo ""
    info "==== [4/5] mem-oom-tm ===="
    cmd_mem_oom_tm "${1:-3500}"
    echo ""
    info "==== [5/5] mem-oom-backend ===="
    cmd_mem_oom_backend "${2:-1800}"
    echo ""
    cmd_status
    info "======== 全量 HA 自愈测试结束 $(ts) ========"
    ok "全部场景执行完毕。请结合上方 Flink Jobs 状态确认作业是否保持/恢复 RUNNING。"
}

usage() {
    sed -n '3,22p' "$0" | sed 's/^# \?//'
}

# ------------------------------------------
main() {
    need_kubectl
    local cmd=${1:-help}
    shift || true
    case "${cmd}" in
        status)           cmd_status ;;
        kill-tm)          cmd_kill_tm ;;
        kill-jm)          cmd_kill_jm ;;
        kill-backend)     cmd_kill_backend ;;
        mem-oom-tm)       cmd_mem_oom_tm "${1:-3500}" ;;
        mem-oom-backend)  cmd_mem_oom_backend "${1:-1800}" ;;
        full)             cmd_full "${1:-3500}" "${2:-1800}" ;;
        help|-h|--help)   usage ;;
        *) err "未知命令: ${cmd}"; usage; exit 1 ;;
    esac
}

main "$@"
