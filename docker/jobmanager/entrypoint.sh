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
execution.checkpointing.min-pause: 60000
execution.checkpointing.max-concurrent-checkpoints: 1
state.checkpoints.dir: ${CHECKPOINT_DIR}
state.savepoints.dir: ${SAVEPOINT_DIR}
state.backend: ${STATE_BACKEND}

# 容错配置
restart-strategy: fixed-delay
restart-strategy.fixed-delay.attempts: 3
restart-strategy.fixed-delay.delay: 10s

# 监控配置
metrics.reporter.prom.class: org.apache.flink.metrics.prometheus.PrometheusReporter
metrics.reporter.prom.port: 9249

# Web UI配置
web.submit.enable: true
web.cancel.enable: true
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

# 启动JobManager
# 使用Flink官方的docker-entrypoint.sh
exec /docker-entrypoint.sh jobmanager
