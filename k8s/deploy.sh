#!/bin/bash

# Flink on Kubernetes 部署脚本
# 自动检测环境：本地 (Kind/Docker Desktop) 或生产
#
# 用法:
#   ./deploy.sh              # 自动检测环境并部署
#   ./deploy.sh --local      # 强制使用本地配置
#   ./deploy.sh --prod       # 强制使用生产配置
#   ./deploy.sh --redeploy   # 清理后重新部署
#   ./deploy.sh -y           # 跳过确认

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 参数
MODE=""
REDEPLOY=false
AUTO_YES=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --local)    MODE="local"; shift ;;
        --prod)     MODE="prod"; shift ;;
        --redeploy) REDEPLOY=true; shift ;;
        -y|--yes)   AUTO_YES=true; shift ;;
        -h|--help)
            echo "用法: $0 [--local|--prod] [--redeploy] [-y]"
            echo "  --local      使用本地配置 (hostPath, 无 Secret)"
            echo "  --prod       使用生产配置 (OSS 存储 + Secret，无 PVC)"
            echo "  --redeploy   清理旧部署后重新部署"
            echo "  -y           跳过确认"
            exit 0 ;;
        *) echo -e "${RED}未知参数: $1${NC}"; exit 1 ;;
    esac
done

echo "=========================================="
echo "Flink on Kubernetes 部署脚本"
echo "=========================================="

# ==========================================
# 前置检查
# ==========================================
if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}错误: kubectl 未安装${NC}"
    exit 1
fi

if ! kubectl cluster-info &> /dev/null; then
    echo -e "${RED}错误: 无法连接到 Kubernetes 集群${NC}"
    exit 1
fi
echo -e "${GREEN}✓ K8s 集群连接正常${NC}"

# ==========================================
# 自动检测环境
# ==========================================
if [ -z "$MODE" ]; then
    # 检测 Kind 节点（基于 kindest/node 镜像，避免误匹配 kind-* 辅助容器）
    KIND_NODE=$(docker ps --format '{{.Names}} {{.Image}}' 2>/dev/null | grep 'kindest/node' | awk '{print $1}' | head -1 || true)
    # 检测 Docker Desktop K8s
    CONTEXT=$(kubectl config current-context 2>/dev/null || true)

    if [ -n "$KIND_NODE" ] || [[ "$CONTEXT" == *"docker-desktop"* ]] || [[ "$CONTEXT" == *"minikube"* ]]; then
        MODE="local"
        echo -e "${YELLOW}检测到本地环境 (${CONTEXT})，使用 local 配置${NC}"
    else
        MODE="prod"
        echo -e "${BLUE}使用生产配置${NC}"
    fi
fi

echo "部署模式: ${MODE}"

# 确认
if [ "$AUTO_YES" = false ]; then
    echo ""
    read -p "是否继续？(y/n) " -n 1 -r
    echo
    [[ ! $REPLY =~ ^[Yy]$ ]] && echo "取消" && exit 0
fi

# ==========================================
# 根据模式选择 YAML 后缀
# ==========================================
if [ "$MODE" = "local" ]; then
    JM_DEPLOY="flink-jobmanager-deployment-local.yaml"
    TM_DEPLOY="flink-taskmanager-deployment-local.yaml"
    BE_DEPLOY="monitor-backend-deployment-local.yaml"
    FE_DEPLOY="monitor-frontend-deployment-local.yaml"
    NEED_PVC=false
    NEED_SECRET=false
else
    JM_DEPLOY="flink-jobmanager-deployment.yaml"
    TM_DEPLOY="flink-taskmanager-deployment.yaml"
    BE_DEPLOY="monitor-backend-deployment.yaml"
    FE_DEPLOY="monitor-frontend-deployment.yaml"
    # checkpoint/savepoint/输出已改为 OSS 存储，生产模式不再创建 PVC
    NEED_PVC=false
    NEED_SECRET=true
fi

# ==========================================
# 辅助函数
# ==========================================

# 定位真正的 K8s 节点容器（KIND / Docker Desktop 均基于 kindest/node 镜像）
# 注意：不能简单 grep "kind"，因为 kind-cloud-provider / kind-registry-mirror
# 等辅助容器也会命中，但它们没有 containerd，会导致镜像导入失败。
detect_kind_node() {
    local node
    # 优先用 kindest/node 镜像匹配节点容器（KIND / Docker Desktop 节点均基于此镜像）
    node=$(docker ps --format '{{.Names}} {{.Image}}' 2>/dev/null | grep 'kindest/node' | awk '{print $1}' | head -1 || true)
    if [ -z "$node" ]; then
        # 兜底：匹配 control-plane / worker 结尾的节点，排除 kind-* 辅助容器
        node=$(docker ps --format '{{.Names}}' 2>/dev/null | grep -E '(control-plane|worker)$' | head -1 || true)
    fi
    echo "$node"
}

# 检测 Kind 节点并加载镜像
load_image() {
    local image=$1
    local kind_node
    kind_node=$(detect_kind_node)
    if [ -z "$kind_node" ]; then
        echo -e "    ${RED}✗ 未找到 K8s 节点容器，无法加载 ${image}${NC}"
        return 1
    fi
    if docker save "$image" | docker exec -i "$kind_node" ctr -n k8s.io images import --all-platforms -; then
        echo -e "    ${GREEN}✓ ${image} → ${kind_node}${NC}"
    else
        echo -e "    ${RED}✗ 加载 ${image} 到 ${kind_node} 失败${NC}"
        return 1
    fi
}

wait_pod() {
    local label=$1 name=$2 timeout=${3:-300s}
    echo -n "  ${name}..."
    if kubectl wait --for=condition=ready pod -l "$label" -n flink --timeout="$timeout" &>/dev/null; then
        echo -e " ${GREEN}✓${NC}"
    else
        echo -e " ${RED}✗${NC}"
        echo -e "    ${RED}日志: kubectl logs -n flink -l ${label} --tail=20${NC}"
        kubectl logs -n flink -l "$label" --tail=10 2>/dev/null || true
    fi
}

# 清理旧的时间戳镜像，仅保留当前部署 tag（$KEEP_TAG）
# 同时清理宿主机 Docker 和 K8s 节点 containerd，防止长期堆积
prune_old_images() {
    local keep_tag=$1
    local repos="flink-jobmanager flink-taskmanager monitor-backend monitor-frontend"
    local kind_node
    kind_node=$(detect_kind_node)

    echo -e "${BLUE}>>> 清理旧时间戳镜像（保留 ${keep_tag}）...${NC}"
    for repo in $repos; do
        # 宿主机：删除该仓库所有 10 位时间戳 tag，但保留当前 keep_tag
        docker images --format '{{.Repository}}:{{.Tag}}' 2>/dev/null \
            | grep -E "^${repo}:[0-9]{10}$" | grep -v "^${repo}:${keep_tag}$" \
            | xargs -r docker rmi >/dev/null 2>&1 || true

        # K8s 节点 containerd：同样清理
        if [ -n "$kind_node" ]; then
            docker exec "$kind_node" ctr -n k8s.io images ls -q 2>/dev/null \
                | grep -E "docker.io/library/${repo}:[0-9]{10}$" | grep -v ":${keep_tag}$" \
                | xargs -r docker exec "$kind_node" ctr -n k8s.io images rm >/dev/null 2>&1 || true
        fi
    done
    # 回收悬空层
    docker image prune -f >/dev/null 2>&1 || true
    echo -e "${GREEN}✓ 旧镜像已清理${NC}"
}

cd "$SCRIPT_DIR"

# ==========================================
# 清理旧部署（如果 --redeploy）
# ==========================================
if [ "$REDEPLOY" = true ]; then
    echo ""
    echo -e "${BLUE}>>> 清理旧部署...${NC}"
    kubectl delete deployment --all -n flink --ignore-not-found=true 2>/dev/null || true
    kubectl delete configmap flink-cluster-cluster-config-map -n flink --ignore-not-found=true 2>/dev/null || true
    echo -e "${GREEN}✓ 已清理${NC}"
fi

# ==========================================
# 构建并加载镜像（本地 + prod 模式均执行）
# ==========================================
echo ""
echo -e "${BLUE}>>> 构建 Docker 镜像...${NC}"

# 检查 JAR 是否存在或源码更新，需要则 Maven 构建
need_mvn=false
if ! ls "$PROJECT_ROOT"/flink-jobs/target/flink-jobs-*-SNAPSHOT.jar &>/dev/null || \
   ! ls "$PROJECT_ROOT"/monitor-backend/target/monitor-backend-*-SNAPSHOT.jar &>/dev/null; then
    need_mvn=true
else
    # 源码比 JAR 新时强制重编，避免 docker 打进陈旧 class（如 oss:// 路径修复）
    newest_src=$(find "$PROJECT_ROOT"/monitor-backend/src "$PROJECT_ROOT"/flink-jobs/src \
        -type f \( -name '*.java' -o -name 'pom.xml' \) -print0 2>/dev/null \
        | xargs -0 stat -f '%m' 2>/dev/null | sort -nr | head -1)
    newest_jar=$(stat -f '%m' "$PROJECT_ROOT"/monitor-backend/target/monitor-backend-*-SNAPSHOT.jar \
        "$PROJECT_ROOT"/flink-jobs/target/flink-jobs-*-SNAPSHOT.jar 2>/dev/null | sort -nr | head -1)
    if [ -n "$newest_src" ] && [ -n "$newest_jar" ] && [ "$newest_src" -gt "$newest_jar" ]; then
        need_mvn=true
        echo -e "  ${YELLOW}检测到源码新于 JAR，重新 Maven 构建...${NC}"
    fi
fi
if [ "$need_mvn" = true ]; then
    echo "  Maven 构建中..."
    mvn -f "$PROJECT_ROOT/pom.xml" clean package -DskipTests -q 2>&1 | tail -1
fi

# ── 预下载 Flink 二进制到 cache/（构建镜像时直接复用，避免慢速在线下载）──
FLINK_VERSION_CACHE="1.20.4"
SCALA_VERSION_CACHE="2.12"
CACHE_DIR="$PROJECT_ROOT/cache"
FLINK_TARBALL="flink-${FLINK_VERSION_CACHE}-bin-scala_${SCALA_VERSION_CACHE}.tgz"
FLINK_TGZ_PATH="$CACHE_DIR/$FLINK_TARBALL"
mkdir -p "$CACHE_DIR"
if [ -f "$FLINK_TGZ_PATH" ]; then
    echo -e "${GREEN}✓ Flink 缓存已存在: cache/${FLINK_TARBALL}${NC}"
else
    echo -e "${YELLOW}预下载 Flink ${FLINK_VERSION_CACHE} 到 cache/（首次较慢，后续复用）...${NC}"
    if curl -L --fail -o "$FLINK_TGZ_PATH" \
        "https://archive.apache.org/dist/flink/flink-${FLINK_VERSION_CACHE}/${FLINK_TARBALL}"; then
        echo -e "${GREEN}✓ 下载完成: cache/${FLINK_TARBALL}${NC}"
    else
        rm -f "$FLINK_TGZ_PATH"
        echo -e "${YELLOW}⚠ 预下载失败，构建时将回退到在线下载${NC}"
    fi
fi

# 用时间戳 tag 保证每次都是新镜像，避免 IfNotPresent 缓存问题
IMAGE_TAG="$(date +%s)"

echo "  flink-jobmanager:${IMAGE_TAG}"
docker build -f "$PROJECT_ROOT/docker/jobmanager/Dockerfile"  -t "flink-jobmanager:${IMAGE_TAG}"  "$PROJECT_ROOT" -q
echo "  flink-taskmanager:${IMAGE_TAG}"
docker build -f "$PROJECT_ROOT/docker/taskmanager/Dockerfile" -t "flink-taskmanager:${IMAGE_TAG}" "$PROJECT_ROOT" -q
echo "  monitor-backend:${IMAGE_TAG}"
docker build -f "$PROJECT_ROOT/monitor-backend/Dockerfile"    -t "monitor-backend:${IMAGE_TAG}"   "$PROJECT_ROOT" -q
echo "  monitor-frontend:${IMAGE_TAG}"
docker build -f "$PROJECT_ROOT/monitor/frontend-vue/Dockerfile" -t "monitor-frontend:${IMAGE_TAG}" "$PROJECT_ROOT/monitor/frontend-vue/" -q

echo -e "${GREEN}✓ 镜像构建完成${NC}"

echo ""
echo -e "${BLUE}>>> 加载镜像到集群...${NC}"
load_image "flink-jobmanager:${IMAGE_TAG}"
load_image "flink-taskmanager:${IMAGE_TAG}"
load_image "monitor-backend:${IMAGE_TAG}"
load_image "monitor-frontend:${IMAGE_TAG}"
echo -e "${GREEN}✓ 镜像加载完成${NC}"

# ==========================================
# 部署 K8s 资源
# ==========================================
echo ""
echo -e "${BLUE}>>> 部署 K8s 资源...${NC}"

# 1. 命名空间 + RBAC
echo -e "  ${BLUE}[1/7] 命名空间 + RBAC${NC}"
kubectl apply -f namespace.yaml
kubectl apply -f flink-rbac.yaml
echo -e "  ${GREEN}✓${NC}"

# 2. Secret（仅生产）
if [ "$NEED_SECRET" = true ]; then
    echo -e "  ${BLUE}[2/7] Secret${NC}"
    if [ -f "flink-secrets.yaml" ]; then
        kubectl apply -f flink-secrets.yaml
        echo -e "  ${GREEN}✓ flink-secrets${NC}"
    else
        echo -e "  ${RED}✗ flink-secrets.yaml 不存在，请先创建:${NC}"
        echo "    cp flink-secrets.yaml.example flink-secrets.yaml"
        exit 1
    fi
else
    echo -e "  ${BLUE}[2/7] Secret${NC} (本地模式跳过)"
fi

# 3. PVC（仅生产）
if [ "$NEED_PVC" = true ]; then
    echo -e "  ${BLUE}[3/7] PVC${NC}"
    kubectl apply -f flink-pvc.yaml
    echo -e "  ${GREEN}✓${NC}"
else
    echo -e "  ${BLUE}[3/7] PVC${NC} (跳过：checkpoint/savepoint/输出已使用 OSS)"
fi

# 4. ConfigMap
echo -e "  ${BLUE}[4/7] ConfigMap${NC}"
kubectl apply -f flink-configuration-configmap.yaml
echo -e "  ${GREEN}✓${NC}"

# 4b. obbinlog (LogProxy) — 以 k8s Deployment 方式运行
# obcdc(在 obbinlog Pod 内)bootstrap 后需直连 OceanBase observer 注册地址
# (ob43-default 网络的 172.22.0.2:2882) 拉取 clog。为此 KIND 节点必须接入
# ob43-default(以及 flink-network)，Pod 经节点 NAT 才能到达该地址。
echo -e "  ${BLUE}[4b] obbinlog (k8s LogProxy)${NC}"
KIND_NODE_C=$(detect_kind_node)
if [ -n "$KIND_NODE_C" ]; then
    for net in ob43-default flink-network; do
        if docker network inspect "$net" >/dev/null 2>&1; then
            if docker inspect "$KIND_NODE_C" --format '{{range $n,$c := .NetworkSettings.Networks}}{{$n}} {{end}}' 2>/dev/null | grep -qw "$net"; then
                : # 已接入
            else
                docker network connect "$net" "$KIND_NODE_C" 2>/dev/null \
                    && echo -e "    ${GREEN}✓ KIND 节点接入 $net（Pod 访问 OB 所需）${NC}" \
                    || echo -e "    ${YELLOW}⚠ KIND 节点接入 $net 失败${NC}"
            fi
        else
            echo -e "    ${YELLOW}⚠ 未找到 docker 网络 $net（OceanBase 未部署？）${NC}"
        fi
    done
fi
# 加载 obbinlog 镜像到集群（若尚未导入）。优先用 kind load（对大/多平台镜像更可靠），
# 回退到 docker save | ctr import（去掉 --all-platforms，避免 digest not found）。
KIND_CLUSTER=$(kubectl config current-context 2>/dev/null | sed 's/^kind-//')
if [ -n "$KIND_NODE_C" ] && ! docker exec "$KIND_NODE_C" ctr -n k8s.io images ls -q 2>/dev/null | grep -q "obbinlog-ce:k8s2"; then
    if docker image inspect obbinlog-ce:k8s2 >/dev/null 2>&1; then
        echo -e "    ${BLUE}加载 obbinlog 镜像到集群（约10GB，稍慢）...${NC}"
        if command -v kind >/dev/null 2>&1 && kind load docker-image obbinlog-ce:k8s2 --name "${KIND_CLUSTER:-desktop}" >/dev/null 2>&1; then
            echo -e "    ${GREEN}✓ obbinlog 镜像已加载 (kind load)${NC}"
        else
            echo -e "    ${YELLOW}⚠ obbinlog 镜像加载失败${NC}"
        fi
    else
        echo -e "    ${YELLOW}⚠ 本地无 obbinlog-ce:k8s2 镜像。请先从运行中的 docker obbinlog 构建：${NC}"
        echo -e "    ${YELLOW}   docker export obbinlog | docker import -c 'ENTRYPOINT [\"/app/start_services.sh\"]' - obbinlog-ce:k8s${NC}"
        echo -e "    ${YELLOW}   docker build -f docker/obbinlog/Dockerfile.k8s -t obbinlog-ce:k8s2 .${NC}"
    fi
fi
kubectl apply -f obbinlog-deployment.yaml
echo -e "  ${GREEN}✓${NC}"

# 部署函数：替换镜像标签（两种模式都用时间戳 tag，避免 IfNotPresent 缓存）
apply_deployment() {
    local file=$1
    sed -E "s|image: (flink-jobmanager\|flink-taskmanager\|monitor-backend\|monitor-frontend):.*|image: \1:${IMAGE_TAG}|g" "$file" | kubectl apply -f -
}

# 5. JobManager
echo -e "  ${BLUE}[5/7] JobManager${NC}"
kubectl apply -f flink-jobmanager-service.yaml
apply_deployment "$JM_DEPLOY"
echo -e "  ${GREEN}✓${NC}"

# 6. TaskManager
echo -e "  ${BLUE}[6/7] TaskManager${NC}"
apply_deployment "$TM_DEPLOY"
echo -e "  ${GREEN}✓${NC}"

# 7. Monitor
echo -e "  ${BLUE}[7/7] Monitor Backend + Frontend${NC}"
apply_deployment "$BE_DEPLOY"
apply_deployment "$FE_DEPLOY"
echo -e "  ${GREEN}✓${NC}"

# ==========================================
# 等待就绪
# ==========================================
echo ""
echo -e "${BLUE}>>> 等待 Pod 就绪...${NC}"
wait_pod "app=flink,component=jobmanager" "JobManager" "180s"
wait_pod "app=flink,component=taskmanager" "TaskManager" "180s"
wait_pod "app=monitor-backend" "Monitor Backend" "120s"
wait_pod "app=monitor-frontend" "Monitor Frontend" "60s"

# ==========================================
# 清理旧时间戳镜像（部署成功后，仅保留本次 tag）
# ==========================================
echo ""
prune_old_images "$IMAGE_TAG"

# ==========================================
# 完成
# ==========================================
echo ""
echo "=========================================="
echo -e "${GREEN}部署完成${NC}"
echo "=========================================="
echo ""
kubectl get pods -n flink
echo ""
kubectl get svc -n flink
echo ""
echo "访问:"
echo "  Frontend:  http://localhost:30888"
echo "  Backend:   http://localhost:30501"
echo "  Flink UI:  http://localhost:30081"
echo ""
echo "常用命令:"
echo "  重新部署: $0 --redeploy"
echo "  卸载:     $SCRIPT_DIR/undeploy.sh"
echo ""

# ==========================================
# 自动启动 port-forward（兜底，NodePort 在
# Docker Desktop / Kind 下有时不通）
# ==========================================
echo -e "${BLUE}>>> 启动端口映射...${NC}"
if [ -x "$SCRIPT_DIR/port-forward.sh" ]; then
    "$SCRIPT_DIR/port-forward.sh" start || true
else
    echo -e "${YELLOW}⚠ 缺少 port-forward.sh，跳过自动映射${NC}"
    echo -e "${YELLOW}  NodePort 直连: Frontend=30888  Backend=30501  Flink=30081${NC}"
fi
echo ""
