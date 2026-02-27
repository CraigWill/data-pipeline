#!/bin/bash
# Flink TaskManager Entrypoint Script
# 需求: 8.5, 8.6 - 支持环境变量配置，60秒内完成初始化

set -e

echo "=========================================="
echo "Starting Flink TaskManager"
echo "Timestamp: $(date)"
echo "=========================================="

# 默认配置值
JOB_MANAGER_RPC_ADDRESS=${JOB_MANAGER_RPC_ADDRESS:-jobmanager}
JOB_MANAGER_RPC_PORT=${JOB_MANAGER_RPC_PORT:-6123}
TASK_MANAGER_HEAP_SIZE=${TASK_MANAGER_HEAP_SIZE:-1024m}
TASK_MANAGER_NUMBER_OF_TASK_SLOTS=${TASK_MANAGER_NUMBER_OF_TASK_SLOTS:-4}
TASK_MANAGER_RPC_PORT=${TASK_MANAGER_RPC_PORT:-6122}
TASK_MANAGER_DATA_PORT=${TASK_MANAGER_DATA_PORT:-6121}
TASK_MANAGER_MEMORY_PROCESS_SIZE=${TASK_MANAGER_MEMORY_PROCESS_SIZE:-1728m}
TASK_MANAGER_MEMORY_MANAGED_SIZE=${TASK_MANAGER_MEMORY_MANAGED_SIZE:-512m}
TASK_MANAGER_NETWORK_MEMORY_MIN=${TASK_MANAGER_NETWORK_MEMORY_MIN:-64m}
TASK_MANAGER_NETWORK_MEMORY_MAX=${TASK_MANAGER_NETWORK_MEMORY_MAX:-256m}

# 打印配置信息
echo "Configuration:"
echo "  JobManager Address: $JOB_MANAGER_RPC_ADDRESS:$JOB_MANAGER_RPC_PORT"
echo "  Heap Size: $TASK_MANAGER_HEAP_SIZE"
echo "  Process Memory: $TASK_MANAGER_MEMORY_PROCESS_SIZE"
echo "  Task Slots: $TASK_MANAGER_NUMBER_OF_TASK_SLOTS"
echo "  RPC Port: $TASK_MANAGER_RPC_PORT"
echo "  Data Port: $TASK_MANAGER_DATA_PORT"

# 验证JobManager地址
if [ -z "$JOB_MANAGER_RPC_ADDRESS" ]; then
    echo "ERROR: JOB_MANAGER_RPC_ADDRESS environment variable is required"
    exit 1
fi

# 创建必要的目录
echo "Creating directories..."
mkdir -p /opt/flink/checkpoints
mkdir -p /opt/flink/savepoints
mkdir -p /opt/flink/logs
mkdir -p /opt/flink/data

# 动态生成flink-conf.yaml（环境变量覆盖）
echo "Configuring Flink..."
cat > /opt/flink/conf/flink-conf.yaml.dynamic << EOF
# JobManager配置
jobmanager.rpc.address: ${JOB_MANAGER_RPC_ADDRESS}
jobmanager.rpc.port: ${JOB_MANAGER_RPC_PORT}

# TaskManager配置
taskmanager.memory.process.size: ${TASK_MANAGER_MEMORY_PROCESS_SIZE}
taskmanager.memory.managed.size: ${TASK_MANAGER_MEMORY_MANAGED_SIZE}
taskmanager.numberOfTaskSlots: ${TASK_MANAGER_NUMBER_OF_TASK_SLOTS}
taskmanager.rpc.port: ${TASK_MANAGER_RPC_PORT}
taskmanager.data.port: ${TASK_MANAGER_DATA_PORT}
taskmanager.bind-host: 0.0.0.0

# 网络配置
taskmanager.network.memory.min: ${TASK_MANAGER_NETWORK_MEMORY_MIN}
taskmanager.network.memory.max: ${TASK_MANAGER_NETWORK_MEMORY_MAX}
taskmanager.network.memory.fraction: 0.1

# 数据交换配置
taskmanager.network.numberOfBuffers: 2048

# 监控配置
metrics.reporter.prom.class: org.apache.flink.metrics.prometheus.PrometheusReporter
metrics.reporter.prom.port: 9249

# 临时目录配置
io.tmp.dirs: /opt/flink/tmp
EOF

# 配置高可用（如果启用）
if [ "$HA_MODE" != "NONE" ]; then
    echo "Configuring High Availability..."
    
    if [ -z "$HA_ZOOKEEPER_QUORUM" ]; then
        echo "WARNING: HA_MODE is enabled but HA_ZOOKEEPER_QUORUM is not set"
    else
        # 创建 HA 存储目录
        mkdir -p /opt/flink/ha
        
        cat >> /opt/flink/conf/flink-conf.yaml.dynamic << EOF

# 高可用配置
high-availability: zookeeper
high-availability.zookeeper.quorum: ${HA_ZOOKEEPER_QUORUM}
high-availability.zookeeper.path.root: ${HA_ZOOKEEPER_PATH_ROOT:-/flink}
high-availability.cluster-id: ${HA_CLUSTER_ID:-/default}
high-availability.storageDir: ${HA_STORAGE_DIR:-file:///opt/flink/ha}
EOF
        
        echo "  ZooKeeper Quorum: $HA_ZOOKEEPER_QUORUM"
        echo "  Cluster ID: ${HA_CLUSTER_ID:-/default}"
        echo "  HA Storage Dir: ${HA_STORAGE_DIR:-file:///opt/flink/ha}"
    fi
fi

# 如果存在原始配置文件，合并配置
if [ -f /opt/flink/conf/flink-conf.yaml.original ]; then
    echo "Merging with original configuration..."
    cat /opt/flink/conf/flink-conf.yaml.original >> /opt/flink/conf/flink-conf.yaml.dynamic
fi

# 使用动态配置
mv /opt/flink/conf/flink-conf.yaml.dynamic /opt/flink/conf/flink-conf.yaml

# 创建临时目录
mkdir -p /opt/flink/tmp

# 设置Java选项
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} -Xmx${TASK_MANAGER_HEAP_SIZE} -Xms${TASK_MANAGER_HEAP_SIZE}"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} -XX:+UseG1GC"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} -XX:MaxGCPauseMillis=200"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} -XX:+HeapDumpOnOutOfMemoryError"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} -XX:HeapDumpPath=/opt/flink/logs/"

# 如果提供了额外的Java选项
if [ -n "$EXTRA_JAVA_OPTS" ]; then
    export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} ${EXTRA_JAVA_OPTS}"
    echo "  Extra Java Options: $EXTRA_JAVA_OPTS"
fi

# 等待JobManager就绪
# 在 HA 模式下，跳过此检查，因为 JobManager 地址可能动态变化
if [ "${HA_MODE:-NONE}" = "NONE" ]; then
    echo "Waiting for JobManager to be ready..."
    RETRY_COUNT=0
    MAX_RETRIES=30
    RETRY_INTERVAL=2

    while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
        if nc -z $JOB_MANAGER_RPC_ADDRESS $JOB_MANAGER_RPC_PORT 2>/dev/null; then
            echo "JobManager is ready!"
            break
        fi
        
        RETRY_COUNT=$((RETRY_COUNT + 1))
        echo "  Attempt $RETRY_COUNT/$MAX_RETRIES: JobManager not ready, waiting ${RETRY_INTERVAL}s..."
        sleep $RETRY_INTERVAL
    done

    if [ $RETRY_COUNT -eq $MAX_RETRIES ]; then
        echo "WARNING: JobManager not reachable after $MAX_RETRIES attempts"
        echo "  Continuing anyway, TaskManager will retry connection..."
    fi
else
    echo "HA mode enabled, skipping JobManager connectivity check"
    echo "TaskManager will discover JobManager through Zookeeper"
fi

# 验证配置
echo "Validating configuration..."
if [ ! -f /opt/flink/conf/flink-conf.yaml ]; then
    echo "ERROR: flink-conf.yaml not found"
    exit 1
fi

# 打印启动信息
echo "=========================================="
echo "TaskManager initialization complete"
echo "Starting Flink TaskManager process..."
echo "=========================================="

# 启动TaskManager
# 使用Flink官方的docker-entrypoint.sh
exec /docker-entrypoint.sh taskmanager
