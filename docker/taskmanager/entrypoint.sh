#!/bin/bash
# Flink TaskManager Entrypoint Script
# 需求: 8.5, 8.6 - 支持环境变量配置，60秒内完成初始化

set -e

# YAML 双引号转义：避免 AK/SK 中的 $ # : " \ 等破坏 flink-conf 或被 shell 二次展开
yaml_quote() {
    local s=${1-}
    s=${s//\\/\\\\}
    s=${s//\"/\\\"}
    printf '"%s"' "$s"
}

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

# TaskManager 对外地址：必须是各容器唯一的可路由地址。
# 多副本（docker-compose --scale / k8s）下若使用共享服务名，JobManager 会通过
# 轮询 DNS 回连到错误的容器，导致 TM 注册抖动、作业反复 RESTARTING。
# 优先使用显式传入的 TASK_MANAGER_HOST，否则取容器自身 IP。
TASK_MANAGER_HOST=${TASK_MANAGER_HOST:-$(hostname -i 2>/dev/null | awk '{print $1}')}
if [ -z "$TASK_MANAGER_HOST" ]; then
    TASK_MANAGER_HOST=$(hostname)
fi
TASK_MANAGER_MEMORY_PROCESS_SIZE=${TASK_MANAGER_MEMORY_PROCESS_SIZE:-1728m}
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
echo "  Advertised Host: $TASK_MANAGER_HOST"

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
# 在 Kubernetes 中，/opt/flink/conf 是只读的 ConfigMap，需要写到临时目录
echo "Configuring Flink..."
DYNAMIC_CONF_DIR="/tmp/flink-conf"
mkdir -p "$DYNAMIC_CONF_DIR"
cat > "$DYNAMIC_CONF_DIR/flink-conf.yaml.dynamic" << EOF
# JobManager配置
jobmanager.rpc.address: ${JOB_MANAGER_RPC_ADDRESS}
jobmanager.rpc.port: ${JOB_MANAGER_RPC_PORT}

# TaskManager配置
taskmanager.memory.process.size: ${TASK_MANAGER_MEMORY_PROCESS_SIZE}
taskmanager.numberOfTaskSlots: ${TASK_MANAGER_NUMBER_OF_TASK_SLOTS}
taskmanager.host: ${TASK_MANAGER_HOST}
taskmanager.rpc.port: ${TASK_MANAGER_RPC_PORT}
taskmanager.data.port: ${TASK_MANAGER_DATA_PORT}
taskmanager.bind-host: 0.0.0.0

# 网络配置（使用比例而非固定大小，避免内存超限）
taskmanager.memory.network.fraction: 0.1
taskmanager.memory.network.min: 64mb
taskmanager.memory.network.max: 256mb
taskmanager.memory.managed.fraction: 0.3

# 心跳超时配置（防止 LogMiner/GC 导致的心跳超时）
heartbeat.interval: 10000
heartbeat.timeout: 180000
heartbeat.rpc-failure-threshold: 5

# Akka/Pekko RPC 超时配置
akka.ask.timeout: 60s
akka.lookup.timeout: 60s
akka.client.timeout: 60s

# 临时目录配置
io.tmp.dirs: /opt/flink/tmp

# 类加载器配置
classloader.resolve-order: child-first
classloader.parent-first-patterns.additional: oracle.jdbc
EOF

# OSS 文件系统配置（将 checkpoint/savepoint/CSV 存储到阿里云 OSS 时启用）
# 仅当提供了 OSS 凭证时才注入 fs.oss.* 配置；否则保持本地文件系统不变
OSS_PROPS=""
if [ -n "${OSS_ACCESS_KEY_ID:-}" ] && [ -n "${OSS_ACCESS_KEY_SECRET:-}" ]; then
    OSS_ACCESS_KEY_ID=$(printf '%s' "$OSS_ACCESS_KEY_ID" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')
    OSS_ACCESS_KEY_SECRET=$(printf '%s' "$OSS_ACCESS_KEY_SECRET" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')
    OSS_ENDPOINT=$(printf '%s' "${OSS_ENDPOINT:-}" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')
    OSS_FS_ENDPOINT=$(echo "${OSS_ENDPOINT}" | sed -E 's#^https?://##')
    echo "  Configuring OSS filesystem (fs.oss.endpoint=${OSS_FS_ENDPOINT})"
    OSS_EP_Q=$(yaml_quote "$OSS_FS_ENDPOINT")
    OSS_AK_Q=$(yaml_quote "$OSS_ACCESS_KEY_ID")
    OSS_SK_Q=$(yaml_quote "$OSS_ACCESS_KEY_SECRET")
    OSS_PROPS="
fs.oss.endpoint: ${OSS_EP_Q}
fs.oss.accessKeyId: ${OSS_AK_Q}
fs.oss.accessKeySecret: ${OSS_SK_Q}"
    cat >> "$DYNAMIC_CONF_DIR/flink-conf.yaml.dynamic" << EOF

# OSS 文件系统（用于 checkpoint/savepoint 直写 OSS）
fs.oss.endpoint: ${OSS_EP_Q}
fs.oss.accessKeyId: ${OSS_AK_Q}
fs.oss.accessKeySecret: ${OSS_SK_Q}
EOF

    # Java 11+ 无内置 JAXB；flink-oss-fs-hadoop 的阿里云 SDK 需要 javax.xml.bind。
    # 须与插件同目录（隔离 ClassLoader）。镜像构建时可能已带上；此处按需 curl 补齐，便于旧镜像热修。
    OSS_PLUGIN_DIR="/opt/flink/plugins/oss-fs-hadoop"
    mkdir -p "$OSS_PLUGIN_DIR"
    if [ -d /opt/flink/opt ] && ! ls "$OSS_PLUGIN_DIR"/flink-oss-fs-hadoop-*.jar >/dev/null 2>&1; then
        cp /opt/flink/opt/flink-oss-fs-hadoop-*.jar "$OSS_PLUGIN_DIR/" 2>/dev/null || true
    fi
    MAVEN_CENTRAL="https://repo1.maven.org/maven2"
    MAVEN_ALIYUN="https://maven.aliyun.com/repository/public"
    download_oss_plugin_jar() {
        local name="$1"
        local path="$2"
        local dest="${OSS_PLUGIN_DIR}/${name}"
        if [ -s "$dest" ]; then
            return 0
        fi
        echo "  Downloading ${name} -> ${dest}"
        if curl -fsSL -o "$dest" "${MAVEN_CENTRAL}/${path}"; then
            return 0
        fi
        echo "  Central failed, retry Aliyun mirror for ${name}"
        if curl -fsSL -o "$dest" "${MAVEN_ALIYUN}/${path}"; then
            return 0
        fi
        echo "WARNING: failed to download ${name} (OSS JAXB may still be missing)"
        rm -f "$dest"
        return 1
    }
    download_oss_plugin_jar "jaxb-api-2.3.1.jar" \
        "javax/xml/bind/jaxb-api/2.3.1/jaxb-api-2.3.1.jar" || true
    download_oss_plugin_jar "jaxb-impl-2.3.1.jar" \
        "com/sun/xml/bind/jaxb-impl/2.3.1/jaxb-impl-2.3.1.jar" || true
    download_oss_plugin_jar "jaxb-core-2.3.0.1.jar" \
        "com/sun/xml/bind/jaxb-core/2.3.0.1/jaxb-core-2.3.0.1.jar" || true
    download_oss_plugin_jar "activation-1.1.1.jar" \
        "javax/activation/activation/1.1.1/activation-1.1.1.jar" || true
    echo "  OSS plugin jars: $(ls -1 "$OSS_PLUGIN_DIR" 2>/dev/null | tr '\n' ' ')"
fi

# 配置高可用（如果启用）
if [ "$HA_MODE" != "NONE" ] && [ "$HA_MODE" != "kubernetes" ]; then
    echo "Configuring High Availability (ZooKeeper mode)..."
    
    if [ -z "$HA_ZOOKEEPER_QUORUM" ]; then
        echo "WARNING: HA_MODE is enabled but HA_ZOOKEEPER_QUORUM is not set"
    else
        # 创建 HA 存储目录
        mkdir -p /opt/flink/ha
        
        if [ -f /opt/flink/conf/flink-conf.yaml ] && [ ! -w /opt/flink/conf/flink-conf.yaml ]; then
            export FLINK_PROPERTIES="${FLINK_PROPERTIES}
high-availability: zookeeper
high-availability.zookeeper.quorum: ${HA_ZOOKEEPER_QUORUM}
high-availability.zookeeper.path.root: ${HA_ZOOKEEPER_PATH_ROOT:-/flink}
high-availability.cluster-id: ${HA_CLUSTER_ID:-/default}
high-availability.storageDir: ${HA_STORAGE_DIR:-file:///opt/flink/ha}
"
        else
            cat >> "$DYNAMIC_CONF_DIR/flink-conf.yaml.dynamic" << EOF

# 高可用配置
high-availability: zookeeper
high-availability.zookeeper.quorum: ${HA_ZOOKEEPER_QUORUM}
high-availability.zookeeper.path.root: ${HA_ZOOKEEPER_PATH_ROOT:-/flink}
high-availability.cluster-id: ${HA_CLUSTER_ID:-/default}
high-availability.storageDir: ${HA_STORAGE_DIR:-file:///opt/flink/ha}
EOF
        fi
        
        echo "  ZooKeeper Quorum: $HA_ZOOKEEPER_QUORUM"
        echo "  Cluster ID: ${HA_CLUSTER_ID:-/default}"
        echo "  HA Storage Dir: ${HA_STORAGE_DIR:-file:///opt/flink/ha}"
    fi
elif [ "$HA_MODE" = "kubernetes" ]; then
    echo "Using Kubernetes native HA (configured in ConfigMap)"
    mkdir -p /opt/flink/ha
fi

# 如果存在原始配置文件，合并配置
if [ -f /opt/flink/conf/flink-conf.yaml ]; then
    echo "Merging with existing configuration..."
    cat /opt/flink/conf/flink-conf.yaml >> "$DYNAMIC_CONF_DIR/flink-conf.yaml.dynamic"
fi

# 在 Kubernetes 环境中，使用环境变量 FLINK_PROPERTIES 或直接使用现有配置
if [ -f /opt/flink/conf/flink-conf.yaml ] && [ ! -w /opt/flink/conf/flink-conf.yaml ]; then
    # ConfigMap 挂载为只读；taskmanager.sh start-foreground 不处理 FLINK_PROPERTIES，
    # 必须生成可写配置并用 FLINK_CONF_DIR 指向它，fs.oss.* 才能生效
    # （FileSink 写 CSV 到 OSS、checkpoint 直写 OSS 都依赖它）。
    echo "Running in Kubernetes with read-only ConfigMap, 生成可写配置目录..."
    WRITABLE_CONF_DIR="/tmp/flink-conf-active"
    mkdir -p "$WRITABLE_CONF_DIR"
    cp /opt/flink/conf/flink-conf.yaml "$WRITABLE_CONF_DIR/flink-conf.yaml"
    cp /opt/flink/conf/log4j-console.properties "$WRITABLE_CONF_DIR/" 2>/dev/null || true
    cp /opt/flink/conf/log4j.properties "$WRITABLE_CONF_DIR/" 2>/dev/null || true
    cat >> "$WRITABLE_CONF_DIR/flink-conf.yaml" << EOF

# ── 运行期覆盖（entrypoint 注入）──
jobmanager.rpc.address: ${JOB_MANAGER_RPC_ADDRESS}
jobmanager.rpc.port: ${JOB_MANAGER_RPC_PORT}
taskmanager.memory.process.size: ${TASK_MANAGER_MEMORY_PROCESS_SIZE}
taskmanager.numberOfTaskSlots: ${TASK_MANAGER_NUMBER_OF_TASK_SLOTS}
taskmanager.host: ${TASK_MANAGER_HOST}
taskmanager.rpc.port: ${TASK_MANAGER_RPC_PORT}
taskmanager.data.port: ${TASK_MANAGER_DATA_PORT}
taskmanager.bind-host: 0.0.0.0
taskmanager.memory.network.fraction: 0.1
taskmanager.memory.network.min: 64mb
taskmanager.memory.network.max: 256mb
taskmanager.memory.managed.fraction: 0.3
${OSS_PROPS}
EOF
    export FLINK_CONF_DIR="$WRITABLE_CONF_DIR"
    echo "  FLINK_CONF_DIR=$FLINK_CONF_DIR"
    [ -n "$OSS_PROPS" ] && echo "  已注入 fs.oss.* 配置到生效 flink-conf.yaml"
else
    echo "Using dynamic configuration..."
    mv "$DYNAMIC_CONF_DIR/flink-conf.yaml.dynamic" /opt/flink/conf/flink-conf.yaml
fi

# 创建临时目录
mkdir -p /opt/flink/tmp

# 设置Java选项
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} -Xmx${TASK_MANAGER_HEAP_SIZE} -Xms${TASK_MANAGER_HEAP_SIZE}"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} -XX:+UseG1GC"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} -XX:MaxGCPauseMillis=200"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} -XX:+HeapDumpOnOutOfMemoryError"
export FLINK_ENV_JAVA_OPTS="${FLINK_ENV_JAVA_OPTS} -XX:HeapDumpPath=/opt/flink/logs/"

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
# 直接使用 Flink 的启动脚本
exec $FLINK_HOME/bin/taskmanager.sh start-foreground
