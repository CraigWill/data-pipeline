#!/bin/bash
# CDC Collector Entrypoint Script
# 需求: 8.5, 8.6 - 支持环境变量配置，60秒内完成初始化

set -e

echo "=========================================="
echo "Starting CDC Collector"
echo "Timestamp: $(date)"
echo "=========================================="

# 记录启动时间（用于验证60秒启动要求）
START_TIME=$(date +%s)

# 默认配置值
DATABASE_PORT=${DATABASE_PORT:-2881}
DATABASE_USERNAME=${DATABASE_USERNAME:-root}
DATABASE_PASSWORD=${DATABASE_PASSWORD:-password}
DATABASE_SCHEMA=${DATABASE_SCHEMA:-test}
DATABASE_TABLES=${DATABASE_TABLES:-*}
DATAHUB_PROJECT=${DATAHUB_PROJECT:-realtime-pipeline}
DATAHUB_TOPIC=${DATAHUB_TOPIC:-cdc-events}
DATAHUB_CONSUMER_GROUP=${DATAHUB_CONSUMER_GROUP:-cdc-collector-group}
RETRY_MAX_ATTEMPTS=${RETRY_MAX_ATTEMPTS:-3}
RETRY_BACKOFF_MS=${RETRY_BACKOFF_MS:-2000}
MONITORING_PORT=${MONITORING_PORT:-8080}
LOG_LEVEL=${LOG_LEVEL:-INFO}
HEAP_SIZE=${HEAP_SIZE:-512m}

# 验证必需的环境变量
echo "Validating required environment variables..."
VALIDATION_FAILED=0

if [ -z "$DATABASE_HOST" ]; then
    echo "ERROR: DATABASE_HOST environment variable is required"
    VALIDATION_FAILED=1
fi

if [ -z "$DATAHUB_ENDPOINT" ]; then
    echo "ERROR: DATAHUB_ENDPOINT environment variable is required"
    VALIDATION_FAILED=1
fi

if [ -z "$DATAHUB_ACCESS_ID" ]; then
    echo "ERROR: DATAHUB_ACCESS_ID environment variable is required"
    VALIDATION_FAILED=1
fi

if [ -z "$DATAHUB_ACCESS_KEY" ]; then
    echo "ERROR: DATAHUB_ACCESS_KEY environment variable is required"
    VALIDATION_FAILED=1
fi

if [ $VALIDATION_FAILED -eq 1 ]; then
    echo "Environment variable validation failed"
    exit 1
fi

echo "Validation passed"

# 打印配置信息（隐藏敏感信息）
echo ""
echo "Configuration:"
echo "  Database:"
echo "    Host: $DATABASE_HOST"
echo "    Port: $DATABASE_PORT"
echo "    Username: $DATABASE_USERNAME"
echo "    Password: ********"
echo "    Schema: $DATABASE_SCHEMA"
echo "    Tables: $DATABASE_TABLES"
echo "  DataHub:"
echo "    Endpoint: $DATAHUB_ENDPOINT"
echo "    Access ID: ${DATAHUB_ACCESS_ID:0:8}********"
echo "    Access Key: ********"
echo "    Project: $DATAHUB_PROJECT"
echo "    Topic: $DATAHUB_TOPIC"
echo "    Consumer Group: $DATAHUB_CONSUMER_GROUP"
echo "  Retry:"
echo "    Max Attempts: $RETRY_MAX_ATTEMPTS"
echo "    Backoff: ${RETRY_BACKOFF_MS}ms"
echo "  Monitoring:"
echo "    Port: $MONITORING_PORT"
echo "  Logging:"
echo "    Level: $LOG_LEVEL"
echo "  Memory:"
echo "    Heap Size: $HEAP_SIZE"

# 创建必要的目录
echo ""
echo "Creating directories..."
mkdir -p /opt/cdc-collector/logs
mkdir -p /opt/cdc-collector/data
mkdir -p /opt/cdc-collector/tmp

# 测试数据库连接（可选，如果安装了mysql客户端）
if command -v nc &> /dev/null; then
    echo ""
    echo "Testing database connectivity..."
    if nc -z -w5 $DATABASE_HOST $DATABASE_PORT 2>/dev/null; then
        echo "  Database host is reachable"
    else
        echo "  WARNING: Cannot reach database host $DATABASE_HOST:$DATABASE_PORT"
        echo "  Application will retry connection on startup"
    fi
fi

# 生成动态配置文件（环境变量覆盖application.yml）
echo ""
echo "Generating dynamic configuration..."
cat > /opt/cdc-collector/config/application-dynamic.yml << EOF
# 动态生成的配置文件（环境变量覆盖）
database:
  host: ${DATABASE_HOST}
  port: ${DATABASE_PORT}
  username: ${DATABASE_USERNAME}
  password: ${DATABASE_PASSWORD}
  schema: ${DATABASE_SCHEMA}
  tables: ${DATABASE_TABLES}

datahub:
  endpoint: ${DATAHUB_ENDPOINT}
  accessId: ${DATAHUB_ACCESS_ID}
  accessKey: ${DATAHUB_ACCESS_KEY}
  project: ${DATAHUB_PROJECT}
  topic: ${DATAHUB_TOPIC}
  consumerGroup: ${DATAHUB_CONSUMER_GROUP}

retry:
  maxAttempts: ${RETRY_MAX_ATTEMPTS}
  backoffMs: ${RETRY_BACKOFF_MS}

monitoring:
  port: ${MONITORING_PORT}
  enabled: true

logging:
  level: ${LOG_LEVEL}
EOF

# 设置Java选项
echo ""
echo "Configuring Java options..."
JAVA_OPTS="-Xmx${HEAP_SIZE} -Xms${HEAP_SIZE}"
JAVA_OPTS="${JAVA_OPTS} -XX:+UseG1GC"
JAVA_OPTS="${JAVA_OPTS} -XX:MaxGCPauseMillis=200"
JAVA_OPTS="${JAVA_OPTS} -XX:+HeapDumpOnOutOfMemoryError"
JAVA_OPTS="${JAVA_OPTS} -XX:HeapDumpPath=/opt/cdc-collector/logs/"
JAVA_OPTS="${JAVA_OPTS} -Dlog4j.configurationFile=/opt/cdc-collector/config/log4j2.xml"
JAVA_OPTS="${JAVA_OPTS} -Dapp.config=/opt/cdc-collector/config/application-dynamic.yml"
JAVA_OPTS="${JAVA_OPTS} -Djava.io.tmpdir=/opt/cdc-collector/tmp"

# 如果提供了额外的Java选项
if [ -n "$EXTRA_JAVA_OPTS" ]; then
    JAVA_OPTS="${JAVA_OPTS} ${EXTRA_JAVA_OPTS}"
    echo "  Extra Java Options: $EXTRA_JAVA_OPTS"
fi

# 计算初始化时间
INIT_TIME=$(($(date +%s) - START_TIME))
echo ""
echo "=========================================="
echo "CDC Collector initialization complete"
echo "Initialization time: ${INIT_TIME}s"
echo "Starting CDC Collector application..."
echo "=========================================="

# 启动CDC Collector
exec java $JAVA_OPTS \
    -jar /opt/cdc-collector/lib/realtime-data-pipeline.jar \
    --config /opt/cdc-collector/config/application-dynamic.yml \
    --mode cdc-collector
