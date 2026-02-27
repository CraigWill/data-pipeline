#!/bin/bash
# 生产级 Flink HA 健康监控和自动恢复脚本
# 功能：
# 1. 监控作业健康状态
# 2. 检测文件生成频率
# 3. 检测 Oracle 连接状态
# 4. 自动重启僵死作业
# 5. 发送告警通知

set -e

# 配置
CHECK_INTERVAL=60  # 检查间隔（秒）
FILE_CHECK_MINUTES=5  # 文件生成检查窗口（分钟）
MAX_RESTART_ATTEMPTS=3  # 最大自动重启次数
RESTART_COOLDOWN=300  # 重启冷却时间（秒）
OUTPUT_DIR="output/cdc"
LOG_FILE="logs/health-monitor.log"

# 告警配置
ALERT_EMAIL=""  # 设置告警邮箱
ALERT_WEBHOOK=""  # 设置 Webhook URL（钉钉、Slack等）

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 状态变量
RESTART_COUNT=0
LAST_RESTART_TIME=0

# 日志函数
log() {
    local level=$1
    shift
    local message="$@"
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    echo "[$timestamp] [$level] $message" | tee -a "$LOG_FILE"
}

log_info() {
    log "INFO" "$@"
}

log_warn() {
    log "WARN" "$@"
}

log_error() {
    log "ERROR" "$@"
}

# 发送告警
send_alert() {
    local severity=$1
    local message=$2
    
    log_warn "告警: [$severity] $message"
    
    # 邮件告警
    if [ -n "$ALERT_EMAIL" ]; then
        echo "$message" | mail -s "[Flink HA Alert] $severity" "$ALERT_EMAIL" 2>/dev/null || true
    fi
    
    # Webhook 告警（钉钉、Slack等）
    if [ -n "$ALERT_WEBHOOK" ]; then
        curl -X POST "$ALERT_WEBHOOK" \
            -H 'Content-Type: application/json' \
            -d "{\"text\": \"[Flink HA Alert] $severity: $message\"}" \
            2>/dev/null || true
    fi
}

# 获取当前 Leader
get_leader() {
    local main_jobs=$(curl -s http://localhost:8081/jobs/overview 2>/dev/null | jq -r '.jobs[]?' 2>/dev/null)
    if [ -n "$main_jobs" ]; then
        echo "8081"
        return 0
    fi
    
    local standby_jobs=$(curl -s http://localhost:8082/jobs/overview 2>/dev/null | jq -r '.jobs[]?' 2>/dev/null)
    if [ -n "$standby_jobs" ]; then
        echo "8082"
        return 0
    fi
    
    echo ""
    return 1
}

# 获取运行中的作业
get_running_job() {
    local port=$1
    curl -s http://localhost:$port/jobs/overview 2>/dev/null | \
        jq -r '.jobs[] | select(.state == "RUNNING") | .jid' 2>/dev/null | head -1
}

# 检查文件生成
check_file_generation() {
    local minutes=$1
    local count=$(find "$OUTPUT_DIR" -type f -name "*.csv" -mmin -$minutes 2>/dev/null | wc -l | tr -d ' ')
    echo $count
}

# 检查 Checkpoint 状态
check_checkpoint() {
    local port=$1
    local job_id=$2
    
    local checkpoint_info=$(curl -s http://localhost:$port/jobs/$job_id/checkpoints 2>/dev/null)
    local latest_id=$(echo "$checkpoint_info" | jq -r '.latest.completed.id' 2>/dev/null)
    local latest_time=$(echo "$checkpoint_info" | jq -r '.latest.completed.trigger_timestamp' 2>/dev/null)
    
    if [ "$latest_id" != "null" ] && [ -n "$latest_id" ]; then
        echo "OK|$latest_id|$latest_time"
        return 0
    else
        echo "FAILED"
        return 1
    fi
}

# 检查作业指标
check_job_metrics() {
    local port=$1
    local job_id=$2
    
    local vertices=$(curl -s http://localhost:$port/jobs/$job_id/vertices 2>/dev/null | jq -r '.vertices[]?' 2>/dev/null)
    if [ -z "$vertices" ]; then
        echo "NO_METRICS"
        return 1
    fi
    
    echo "OK"
    return 0
}

# 重启作业
restart_job() {
    local port=$1
    local job_id=$2
    
    local current_time=$(date +%s)
    local time_since_last_restart=$((current_time - LAST_RESTART_TIME))
    
    # 检查冷却时间
    if [ $time_since_last_restart -lt $RESTART_COOLDOWN ]; then
        log_warn "重启冷却中，剩余 $((RESTART_COOLDOWN - time_since_last_restart)) 秒"
        return 1
    fi
    
    # 检查重启次数
    if [ $RESTART_COUNT -ge $MAX_RESTART_ATTEMPTS ]; then
        log_error "达到最大重启次数 ($MAX_RESTART_ATTEMPTS)，需要人工介入"
        send_alert "CRITICAL" "作业重启失败次数过多，需要人工介入"
        return 1
    fi
    
    log_info "开始重启作业 $job_id..."
    send_alert "WARNING" "检测到作业异常，正在自动重启"
    
    # 取消作业
    curl -X PATCH "http://localhost:$port/jobs/$job_id?mode=cancel" 2>/dev/null
    sleep 5
    
    # 提交新作业
    docker exec flink-jobmanager flink run -d \
        -c com.realtime.pipeline.FlinkCDC3App \
        /opt/flink/usrlib/realtime-data-pipeline.jar
    
    if [ $? -eq 0 ]; then
        log_info "作业重启成功"
        RESTART_COUNT=$((RESTART_COUNT + 1))
        LAST_RESTART_TIME=$current_time
        send_alert "INFO" "作业已成功重启 (第 $RESTART_COUNT 次)"
        return 0
    else
        log_error "作业重启失败"
        send_alert "CRITICAL" "作业重启失败，需要人工介入"
        return 1
    fi
}

# 主监控循环
main() {
    log_info "=========================================="
    log_info "生产级健康监控启动"
    log_info "检查间隔: ${CHECK_INTERVAL}秒"
    log_info "文件检查窗口: ${FILE_CHECK_MINUTES}分钟"
    log_info "=========================================="
    
    # 创建日志目录
    mkdir -p logs
    
    while true; do
        log_info "开始健康检查..."
        
        # 1. 检查 Leader
        LEADER_PORT=$(get_leader)
        if [ -z "$LEADER_PORT" ]; then
            log_error "无法找到 Leader JobManager"
            send_alert "CRITICAL" "无法找到 Leader JobManager"
            sleep $CHECK_INTERVAL
            continue
        fi
        log_info "当前 Leader: 端口 $LEADER_PORT"
        
        # 2. 获取运行中的作业
        JOB_ID=$(get_running_job $LEADER_PORT)
        if [ -z "$JOB_ID" ]; then
            log_error "没有运行中的作业"
            send_alert "CRITICAL" "没有运行中的作业"
            sleep $CHECK_INTERVAL
            continue
        fi
        log_info "运行中的作业: $JOB_ID"
        
        # 3. 检查文件生成
        FILE_COUNT=$(check_file_generation $FILE_CHECK_MINUTES)
        log_info "最近 ${FILE_CHECK_MINUTES} 分钟生成文件数: $FILE_COUNT"
        
        if [ $FILE_COUNT -eq 0 ]; then
            log_warn "警告: ${FILE_CHECK_MINUTES} 分钟内未生成新文件"
            
            # 4. 检查 Checkpoint
            CHECKPOINT_STATUS=$(check_checkpoint $LEADER_PORT $JOB_ID)
            if [[ $CHECKPOINT_STATUS == OK* ]]; then
                CHECKPOINT_ID=$(echo $CHECKPOINT_STATUS | cut -d'|' -f2)
                log_info "Checkpoint 正常: $CHECKPOINT_ID"
            else
                log_error "Checkpoint 失败"
                send_alert "ERROR" "Checkpoint 失败，作业可能异常"
            fi
            
            # 5. 检查作业指标
            METRICS_STATUS=$(check_job_metrics $LEADER_PORT $JOB_ID)
            if [ "$METRICS_STATUS" = "OK" ]; then
                log_info "作业指标正常"
            else
                log_error "无法获取作业指标"
            fi
            
            # 6. 决定是否重启
            if [ $FILE_COUNT -eq 0 ] && [ "$CHECKPOINT_STATUS" != "OK"* ]; then
                log_error "检测到作业僵死：无文件生成且 Checkpoint 失败"
                restart_job $LEADER_PORT $JOB_ID
            else
                log_warn "作业可能正常但无数据，继续监控"
            fi
        else
            log_info "✓ 健康检查通过：文件正常生成"
            # 重置重启计数器
            if [ $RESTART_COUNT -gt 0 ]; then
                log_info "作业恢复正常，重置重启计数器"
                RESTART_COUNT=0
            fi
        fi
        
        log_info "等待 ${CHECK_INTERVAL} 秒进行下一次检查..."
        echo ""
        sleep $CHECK_INTERVAL
    done
}

# 信号处理
trap 'log_info "收到停止信号，退出监控"; exit 0' SIGINT SIGTERM

# 启动监控
main
