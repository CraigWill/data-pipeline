#!/bin/bash
# Flink JobManager Entrypoint Script
# 需求: 8.5, 8.6 - 支持环境变量配置，60秒内完成初始化

set -e

echo "=========================================="
echo "Starting Flink JobManager"
echo "Timestamp: $(date)"
echo "=========================================="

# 默认配置值
JOB_MANAGER_RPC_ADDRESS=${JOB_MANAGER_RPC_ADDRESS:-localhost}
JOB_MANAGER_RPC_PORT=${JOB_MANAGER_RPC_PORT:-6123}
JOB_MANAGER_HEAP_SIZE=${JOB_MANAGER_HEAP_SIZE:-1024m}
BLOB_SERVER_PORT=${BLOB_SERVER_PORT:-6124}
QUERY_SERVER_PORT=${QUERY_SERVER_PORT:-6125}
REST_PORT=${REST_PORT:-8081}
PARALLELISM_DEFAULT=${PARALLELISM_DEFAULT:-4}
CHECKPOINT_INTERVAL=${CHECKPOINT_INTERVAL:-300000}
CHECKPOINT_DIR=${CHECKPOINT_DIR:-file:///opt/flink/checkpoints}
SAVEPOINT_DIR=${SAVEPOINT_DIR:-file:///opt/flink/savepoints}
STATE_BACKEND=${STATE_BACKEND:-hashmap}
HA_MODE=${HA_MODE:-NONE}

# 打印配置信息
echo "Configuration:"
echo "  RPC Address: $JOB_MANAGER_RPC_ADDRESS"
echo "  RPC Port: $JOB_MANAGER_RPC_PORT"
echo "  Heap Size: $JOB_MANAGER_HEAP_SIZE"
echo "  REST Port: $REST_PORT"
echo "  Default Parallelism: $PARALLELISM_DEFAULT"
echo "  Checkpoint Interval: ${CHECKPOINT_INTERVAL}ms"
echo "  Checkpoint Directory: $CHECKPOINT_DIR"
echo "  State Backend: $STATE_BACKEND"
echo "  HA Mode: $HA_MODE"

# 创建必要的目录
echo "Creating directories..."
mkdir -p /opt/flink/checkpoints
mkdir -p /opt/flink/savepoints
mkdir -p /opt/flink/logs

# 动态生成flink-conf.yaml（环境变量覆盖）
echo "Configuring Flink..."
cat > /opt/flink/conf/flink-conf.yaml.dynamic << EOF
# JobManager配置
jobmanager.rpc.address: ${JOB_MANAGER_RPC_ADDRESS}
jobmanager.rpc.port: ${JOB_MANAGER_RPC_PORT}
jobmanager.memory.process.size: ${JOB_MANAGER_HEAP_SIZE}
jobmanager.bind-host: 0.0.0.0

# REST API配置
rest.port: ${REST_PORT}
rest.bind-address: 0.0.0.0

# Blob Server配置
blob.server.port: ${BLOB_SERVER_PORT}

# 并行度配置
parallelism.default: ${PARALLELISM_DEFAULT}

# Checkpoint配置
execution.checkpointing.interval: ${CHECKPOINT_INTERVAL}
execution.checkpointing.mode: EXACTLY_ONCE
execution.checkpointing.timeout: 600000
execution.checkpointing.min-pause: 30000
execution.checkpointing.max-concurrent-checkpoints: 1
execution.checkpointing.tolerable-failed-checkpoints: 10
state.checkpoints.dir: ${CHECKPOINT_DIR}
state.savepoints.dir: ${SAVEPOINT_DIR}
state.backend: ${STATE_BACKEND}

# 心跳超时配置（防止 LogMiner/GC 导致的心跳超时）
heartbeat.interval: 10000
heartbeat.timeout: 180000
heartbeat.rpc-failure-threshold: 5

# Akka/Pekko RPC 超时配置
akka.ask.timeout: 60s
akka.lookup.timeout: 60s
akka.client.timeout: 60s

# 容错配置
restart-strategy: exponential-delay
restart-strategy.exponential-delay.initial-backoff: 10s
restart-strategy.exponential-delay.max-backoff: 2min
restart-strategy.exponential-delay.backoff-multiplier: 2.0
restart-strategy.exponential-delay.reset-backoff-threshold: 10min
restart-strategy.exponential-delay.jitter-factor: 0.1

# 作业恢复策略
jobmanager.execution.failover-strategy: region

# Web UI配置
web.submit.enable: true
web.cancel.enable: true

# 类加载器配置
classloader.resolve-order: parent-first
EOF

# 如果存在原始配置文件，合并配置
if [ -f /opt/flink/conf/flink-conf.yaml.original ]; then
    echo "Merging with original configuration..."
    cat /opt/flink/conf/flink-conf.yaml.original >> /opt/flink/conf/flink-conf.yaml.dynamic
fi

# 使用动态配置
mv /opt/flink/conf/flink-conf.yaml.dynamic /opt/flink/conf/flink-conf.yaml

# 配置高可用（如果启用）
if [ "$HA_MODE" != "NONE" ]; then
    echo "Configuring High Availability..."
    
    if [ -z "$HA_ZOOKEEPER_QUORUM" ]; then
        echo "WARNING: HA_MODE is enabled but HA_ZOOKEEPER_QUORUM is not set"
    else
        cat >> /opt/flink/conf/flink-conf.yaml << EOF

# 高可用配置
high-availability: zookeeper
high-availability.zookeeper.quorum: ${HA_ZOOKEEPER_QUORUM}
high-availability.zookeeper.path.root: ${HA_ZOOKEEPER_PATH_ROOT:-/flink}
high-availability.cluster-id: ${HA_CLUSTER_ID:-/default}
high-availability.storageDir: ${HA_STORAGE_DIR:-file:///opt/flink/ha}
EOF
        
        # 创建HA存储目录
        mkdir -p /opt/flink/ha
        echo "  ZooKeeper Quorum: $HA_ZOOKEEPER_QUORUM"
        echo "  Cluster ID: ${HA_CLUSTER_ID:-/default}"
    fi
fi

# 设置Java选项
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} -Xmx${JOB_MANAGER_HEAP_SIZE} -Xms${JOB_MANAGER_HEAP_SIZE}"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} -XX:+UseG1GC"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} -XX:MaxGCPauseMillis=200"

# Java 17 模块系统配置（解决反射访问限制）
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} --add-opens=java.base/java.util=ALL-UNNAMED"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} --add-opens=java.base/java.lang=ALL-UNNAMED"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} --add-opens=java.base/java.lang.reflect=ALL-UNNAMED"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} --add-opens=java.base/java.io=ALL-UNNAMED"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} --add-opens=java.base/java.net=ALL-UNNAMED"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} --add-opens=java.base/java.nio=ALL-UNNAMED"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} --add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} --add-opens=java.base/sun.net.dns=ALL-UNNAMED"

# 如果提供了额外的Java选项
if [ -n "$EXTRA_JAVA_OPTS" ]; then
    export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} ${EXTRA_JAVA_OPTS}"
    echo "  Extra Java Options: $EXTRA_JAVA_OPTS"
fi

# 验证配置
echo "Validating configuration..."
if [ ! -f /opt/flink/conf/flink-conf.yaml ]; then
    echo "ERROR: flink-conf.yaml not found"
    exit 1
fi

# 打印启动信息
echo "=========================================="
echo "JobManager initialization complete"
echo "Starting Flink JobManager process..."
echo "=========================================="

# 启动JobManager（后台运行）
# 直接使用 Flink 的启动脚本
$FLINK_HOME/bin/jobmanager.sh start-foreground &
JOBMANAGER_PID=$!

# 自动作业提交配置
AUTO_SUBMIT_JOB=${AUTO_SUBMIT_JOB:-true}
AUTO_SUBMIT_DELAY=${AUTO_SUBMIT_DELAY:-60}
JOB_JAR_PATH=${JOB_JAR_PATH:-/opt/flink/usrlib/realtime-data-pipeline.jar}
JOB_MAIN_CLASS=${JOB_MAIN_CLASS:-com.realtime.pipeline.FlinkCDC3App}

# 自动提交作业（仅在主 JobManager 上执行）
if [ "$AUTO_SUBMIT_JOB" = "true" ] && [ "$JOB_MANAGER_RPC_ADDRESS" = "jobmanager" ]; then
    echo "=========================================="
    echo "Auto-submit job enabled"
    echo "  Delay: ${AUTO_SUBMIT_DELAY}s"
    echo "  JAR: $JOB_JAR_PATH"
    echo "  Main Class: $JOB_MAIN_CLASS"
    echo "=========================================="
    
    # 后台启动自动提交脚本
    (
        # 等待 JobManager 完全启动
        echo "[Auto-Submit] Waiting ${AUTO_SUBMIT_DELAY}s for JobManager to be ready..."
        sleep $AUTO_SUBMIT_DELAY
        
        # 检查 JobManager 是否就绪
        MAX_RETRIES=30
        RETRY_COUNT=0
        while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
            if curl -s http://localhost:${REST_PORT}/overview > /dev/null 2>&1; then
                echo "[Auto-Submit] JobManager is ready"
                break
            fi
            RETRY_COUNT=$((RETRY_COUNT + 1))
            echo "[Auto-Submit] Waiting for JobManager... ($RETRY_COUNT/$MAX_RETRIES)"
            sleep 2
        done
        
        if [ $RETRY_COUNT -eq $MAX_RETRIES ]; then
            echo "[Auto-Submit] ERROR: JobManager not ready after ${MAX_RETRIES} retries"
            exit 1
        fi
        
        # 检查是否已有运行中的作业（包括 RESTARTING 状态）
        RUNNING_JOBS=$(curl -s http://localhost:${REST_PORT}/jobs/overview 2>/dev/null | grep -oE '"state":"(RUNNING|RESTARTING)"' | wc -l)
        if [ "$RUNNING_JOBS" -gt 0 ]; then
            echo "[Auto-Submit] Job already running ($RUNNING_JOBS jobs), skipping auto-submit"
            exit 0
        fi
        
        # 额外检查：是否有相同名称的作业（防止重复提交）
        JOB_NAME="Flink CDC 3.x Oracle Application"
        EXISTING_JOBS=$(curl -s http://localhost:${REST_PORT}/jobs/overview 2>/dev/null | grep -c "$JOB_NAME" || echo "0")
        if [ "$EXISTING_JOBS" -gt 0 ]; then
            echo "[Auto-Submit] Job with name '$JOB_NAME' already exists, skipping auto-submit"
            exit 0
        fi
        
        # 检查 JAR 文件是否存在
        if [ ! -f "$JOB_JAR_PATH" ]; then
            echo "[Auto-Submit] ERROR: JAR file not found: $JOB_JAR_PATH"
            exit 1
        fi
        
        # 提交作业
        echo "[Auto-Submit] Submitting job..."
        JOB_OUTPUT=$(flink run -d -c "$JOB_MAIN_CLASS" "$JOB_JAR_PATH" 2>&1)
        
        if echo "$JOB_OUTPUT" | grep -q "Job has been submitted"; then
            JOB_ID=$(echo "$JOB_OUTPUT" | grep -oP 'JobID \K[a-f0-9]+' || echo "unknown")
            echo "[Auto-Submit] ✓ Job submitted successfully"
            echo "[Auto-Submit]   Job ID: $JOB_ID"
            
            # 记录到日志文件
            echo "$(date '+%Y-%m-%d %H:%M:%S') - Auto-submitted job: $JOB_ID" >> /opt/flink/logs/auto-submit.log
        else
            echo "[Auto-Submit] ✗ Job submission failed"
            echo "$JOB_OUTPUT"
            
            # 记录失败到日志
            echo "$(date '+%Y-%m-%d %H:%M:%S') - Auto-submit failed: $JOB_OUTPUT" >> /opt/flink/logs/auto-submit.log
        fi
    ) &
    
    echo "Auto-submit script started in background"
fi

# 等待 JobManager 进程
wait $JOBMANAGER_PID
