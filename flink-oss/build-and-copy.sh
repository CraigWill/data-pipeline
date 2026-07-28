#!/usr/bin/env bash
# 构建 flink-oss-probe.jar 并拷入本地 Kind 的 JobManager（可选 TM）
#
# 用法:
#   ./build-and-copy.sh              # 仅构建
#   ./build-and-copy.sh copy         # 构建 + 拷到 JM /opt/flink/usrlib/
#   ./build-and-copy.sh run          # 构建 + 拷贝 + 在 JM 内执行探测
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NS="${NAMESPACE:-flink}"
JAR_NAME="flink-oss-probe.jar"
LOCAL_JAR="$SCRIPT_DIR/target/flink-oss-probe.jar"
REMOTE_JAR="/opt/flink/usrlib/${JAR_NAME}"
ACTION="${1:-build}"

echo ">>> mvn package ($SCRIPT_DIR)"
mvn -f "$SCRIPT_DIR/pom.xml" -q -DskipTests package
ls -lh "$LOCAL_JAR"

if [ "$ACTION" = "build" ]; then
  echo "OK: jar ready at $LOCAL_JAR"
  echo "Copy manually:"
  echo "  kubectl -n $NS cp $LOCAL_JAR deploy/flink-jobmanager:$REMOTE_JAR"
  exit 0
fi

echo ">>> copy to JobManager"
JM_POD="$(kubectl -n "$NS" get pods -l component=jobmanager -o jsonpath='{.items[0].metadata.name}')"
if [ -z "$JM_POD" ]; then
  echo "ERROR: no jobmanager pod in namespace $NS"
  exit 1
fi
echo "  target pod: $JM_POD"
kubectl -n "$NS" cp "$LOCAL_JAR" "${JM_POD}:${REMOTE_JAR}"
kubectl -n "$NS" exec "$JM_POD" -- ls -lh "$REMOTE_JAR"

# 同步拷一份到全部 TaskManager（可选，run 也会用到 JM）
if kubectl -n "$NS" get deploy flink-taskmanager >/dev/null 2>&1; then
  for pod in $(kubectl -n "$NS" get pods -l component=taskmanager -o jsonpath='{.items[*].metadata.name}'); do
    echo ">>> copy to $pod"
    kubectl -n "$NS" cp "$LOCAL_JAR" "$pod:${REMOTE_JAR}" || true
  done
fi

if [ "$ACTION" = "copy" ]; then
  echo "OK: copied. Run inside JM:"
  echo "  kubectl -n $NS exec $JM_POD -- java -jar $REMOTE_JAR"
  exit 0
fi

if [ "$ACTION" = "run" ]; then
  echo ">>> run probe in JobManager ($JM_POD)"
  kubectl -n "$NS" exec "$JM_POD" -- java -jar "$REMOTE_JAR"
fi
