# Flink on Kubernetes 部署指南

## 概述

本方案使用 **Kubernetes 原生 HA** 替代 ZooKeeper，通过 Kubernetes 的以下特性实现高可用：

- **Deployment**: 自动管理 Pod 副本，故障时自动重启
- **ConfigMap**: Kubernetes 原生的配置存储，替代 ZooKeeper 的配置协调
- **Leader Election**: Flink 使用 Kubernetes ConfigMap 实现 Leader 选举
- **Service Discovery**: 通过 Kubernetes Service 实现服务发现

## 架构对比

### 原架构（Docker Compose + ZooKeeper）

```
┌─────────────┐
│  ZooKeeper  │ ← HA 协调器
└──────┬──────┘
       │
   ┌───┴────┐
   │        │
┌──▼──┐  ┌──▼──────┐
│ JM1 │  │ JM2     │
│主节点│  │备用节点 │
└─────┘  └─────────┘
```

### 新架构（Kubernetes 原生 HA）

```
┌──────────────────────────┐
│   Kubernetes API Server  │ ← HA 协调器
│   (ConfigMap Leader      │
│    Election)             │
└────────────┬─────────────┘
             │
        ┌────┴─────┐
        │          │
    ┌───▼──┐   ┌───▼──┐
    │ JM1  │   │ JM2  │
    │ Pod  │   │ Pod  │
    └──────┘   └──────┘
```

## 优势

✅ **无需 ZooKeeper**: 减少组件依赖，降低运维复杂度
✅ **Kubernetes 原生**: 充分利用 K8s 的自愈能力
✅ **自动扩缩容**: 通过 `kubectl scale` 轻松调整 TaskManager 数量
✅ **滚动更新**: 支持零停机升级
✅ **资源管理**: 统一的资源配额和限制
✅ **监控集成**: 易于集成 Prometheus/Grafana

## 前置要求

### 1. Kubernetes 集群

- Kubernetes 1.19+
- 支持 LoadBalancer 或 NodePort（用于外部访问）
- 支持 PersistentVolume（用于存储 checkpoint/savepoint）

### 2. 存储类（StorageClass）

需要支持 `ReadWriteMany` 的存储类，例如：
- NFS
- CephFS
- GlusterFS
- AWS EFS
- Azure Files

检查可用的存储类：
```bash
kubectl get storageclass
```

如果没有合适的存储类，需要先配置。

### 3. Docker 镜像

确保已构建以下镜像：
```bash
# 构建 Flink 镜像
./build-flink-images.sh

# 构建 Monitor Backend 镜像
docker build -f monitor/Dockerfile -t monitor-backend:latest .

# 构建 Monitor Frontend 镜像
docker build -f monitor/frontend-vue/Dockerfile -t monitor-frontend:latest .
```

如果使用私有镜像仓库，需要推送镜像：
```bash
# 标记镜像
docker tag flink-jobmanager:latest your-registry/flink-jobmanager:latest
docker tag flink-taskmanager:latest your-registry/flink-taskmanager:latest
docker tag monitor-backend:latest your-registry/monitor-backend:latest
docker tag monitor-frontend:latest your-registry/monitor-frontend:latest

# 推送镜像
docker push your-registry/flink-jobmanager:latest
docker push your-registry/flink-taskmanager:latest
docker push your-registry/monitor-backend:latest
docker push your-registry/monitor-frontend:latest
```

## 部署步骤

### 1. 配置 Secret

```bash
# 复制示例文件
cp flink-secrets.yaml.example flink-secrets.yaml

# 编辑配置
vim flink-secrets.yaml
```

修改以下配置：
- `database-host`: Oracle 数据库地址
- `database-port`: Oracle 端口（默认 1521）
- `database-username`: 数据库用户名
- `database-password`: 数据库密码
- `database-sid`: Oracle SID
- `database-schema`: 数据库 Schema
- `jwt-secret`: JWT 密钥（至少 32 字符）
- `aes-encryption-key`: AES 加密密钥（16 字符）

### 2. 配置存储类（可选）

如果需要指定存储类，编辑 `flink-pvc.yaml`：

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: flink-checkpoints-pvc
spec:
  storageClassName: your-storage-class  # 指定存储类
  accessModes:
  - ReadWriteMany
  resources:
    requests:
      storage: 10Gi
```

### 3. 一键部署

```bash
cd k8s
./deploy.sh
```

脚本会自动执行以下步骤：
1. 创建命名空间 `flink`
2. 创建 RBAC 权限
3. 创建 Secret
4. 创建 PVC
5. 创建 ConfigMap
6. 部署 JobManager（2 个副本）
7. 部署 TaskManager（3 个副本）
8. 部署 Monitor Backend（2 个副本）
9. 部署 Monitor Frontend（2 个副本）
10. 等待所有 Pod 就绪

### 4. 验证部署

```bash
# 查看 Pod 状态
kubectl get pods -n flink

# 预期输出：
# NAME                                READY   STATUS    RESTARTS   AGE
# flink-jobmanager-xxx                1/1     Running   0          2m
# flink-jobmanager-yyy                1/1     Running   0          2m
# flink-taskmanager-xxx               1/1     Running   0          2m
# flink-taskmanager-yyy               1/1     Running   0          2m
# flink-taskmanager-zzz               1/1     Running   0          2m
# monitor-backend-xxx                 1/1     Running   0          2m
# monitor-backend-yyy                 1/1     Running   0          2m
# monitor-frontend-xxx                1/1     Running   0          2m
# monitor-frontend-yyy                1/1     Running   0          2m

# 查看 Service
kubectl get svc -n flink

# 查看 PVC
kubectl get pvc -n flink
```

## 访问服务

### 方法 1: Port Forward（推荐用于测试）

```bash
# Flink Web UI
kubectl port-forward -n flink svc/flink-jobmanager-rest 8081:8081
# 访问: http://localhost:8081

# Monitor Frontend
kubectl port-forward -n flink svc/monitor-frontend 8888:80
# 访问: http://localhost:8888

# Monitor Backend API
kubectl port-forward -n flink svc/monitor-backend 5001:5001
# 访问: http://localhost:5001
```

### 方法 2: LoadBalancer（推荐用于生产）

如果集群支持 LoadBalancer：

```bash
# 查看外部 IP
kubectl get svc -n flink

# 输出示例：
# NAME                      TYPE           EXTERNAL-IP      PORT(S)
# flink-jobmanager-rest     LoadBalancer   192.168.1.100    8081:30081/TCP
# monitor-frontend          LoadBalancer   192.168.1.101    80:30888/TCP
```

直接访问 EXTERNAL-IP。

### 方法 3: NodePort

如果使用 NodePort，编辑 Service 配置：

```yaml
# flink-jobmanager-service.yaml
spec:
  type: NodePort
  ports:
  - name: rest
    port: 8081
    targetPort: 8081
    nodePort: 30081  # 指定端口

# monitor-frontend-deployment.yaml
spec:
  type: NodePort
  ports:
  - port: 80
    targetPort: 80
    nodePort: 30888
```

访问: `http://<node-ip>:30081` 和 `http://<node-ip>:30888`

### 方法 4: Ingress（推荐用于生产）

创建 Ingress 资源：

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: flink-ingress
  namespace: flink
spec:
  rules:
  - host: flink.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: flink-jobmanager-rest
            port:
              number: 8081
  - host: monitor.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: monitor-frontend
            port:
              number: 80
```

## 运维操作

### 扩展 TaskManager

```bash
# 扩展到 5 个 TaskManager
kubectl scale deployment flink-taskmanager -n flink --replicas=5

# 缩减到 2 个 TaskManager
kubectl scale deployment flink-taskmanager -n flink --replicas=2
```

### 查看日志

```bash
# JobManager 日志
kubectl logs -n flink -l app=flink,component=jobmanager -f

# TaskManager 日志
kubectl logs -n flink -l app=flink,component=taskmanager -f

# Monitor Backend 日志
kubectl logs -n flink -l app=monitor-backend -f

# 查看特定 Pod 日志
kubectl logs -n flink <pod-name> -f
```

### 进入容器

```bash
# 进入 JobManager
kubectl exec -it -n flink <jobmanager-pod-name> -- bash

# 进入 TaskManager
kubectl exec -it -n flink <taskmanager-pod-name> -- bash
```

### 重启服务

```bash
# 重启 JobManager
kubectl rollout restart deployment flink-jobmanager -n flink

# 重启 TaskManager
kubectl rollout restart deployment flink-taskmanager -n flink

# 重启 Monitor Backend
kubectl rollout restart deployment monitor-backend -n flink
```

### 更新配置

```bash
# 修改 ConfigMap
kubectl edit configmap flink-config -n flink

# 重启 Pod 使配置生效
kubectl rollout restart deployment flink-jobmanager -n flink
kubectl rollout restart deployment flink-taskmanager -n flink
```

### 更新镜像

```bash
# 更新 JobManager 镜像
kubectl set image deployment/flink-jobmanager -n flink \
  jobmanager=your-registry/flink-jobmanager:v2.0

# 更新 TaskManager 镜像
kubectl set image deployment/flink-taskmanager -n flink \
  taskmanager=your-registry/flink-taskmanager:v2.0
```

### 查看资源使用

```bash
# 查看 Pod 资源使用
kubectl top pods -n flink

# 查看节点资源使用
kubectl top nodes
```

## 高可用验证

### 测试 JobManager 故障转移

```bash
# 1. 查看当前 Leader
kubectl logs -n flink -l app=flink,component=jobmanager | grep "leader"

# 2. 删除 Leader Pod
kubectl delete pod -n flink <leader-pod-name>

# 3. 观察新 Leader 选举
kubectl logs -n flink -l app=flink,component=jobmanager -f

# 4. 验证任务继续运行
# 访问 Flink Web UI，确认任务状态为 RUNNING
```

### 测试 TaskManager 故障恢复

```bash
# 1. 删除一个 TaskManager
kubectl delete pod -n flink <taskmanager-pod-name>

# 2. 观察自动重启
kubectl get pods -n flink -w

# 3. 验证任务恢复
# 访问 Flink Web UI，确认任务从 checkpoint 恢复
```

## 监控和告警

### Prometheus 集成

Flink 已配置 Prometheus Metrics：

```yaml
# prometheus-servicemonitor.yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: flink
  namespace: flink
spec:
  selector:
    matchLabels:
      app: flink
  endpoints:
  - port: metrics
    interval: 30s
```

### Grafana Dashboard

导入 Flink 官方 Dashboard：
- Dashboard ID: 10369
- 或使用自定义 Dashboard

## 故障排查

### Pod 无法启动

```bash
# 查看 Pod 事件
kubectl describe pod -n flink <pod-name>

# 查看日志
kubectl logs -n flink <pod-name>

# 常见问题：
# 1. 镜像拉取失败 -> 检查镜像名称和仓库权限
# 2. PVC 绑定失败 -> 检查存储类和 PV
# 3. Secret 不存在 -> 检查 flink-secrets.yaml
```

### JobManager 无法选举 Leader

```bash
# 检查 RBAC 权限
kubectl get role,rolebinding -n flink

# 检查 ConfigMap 权限
kubectl auth can-i create configmaps --namespace=flink --as=system:serviceaccount:flink:flink

# 查看 Leader Election 日志
kubectl logs -n flink -l app=flink,component=jobmanager | grep -i "leader"
```

### TaskManager 无法连接 JobManager

```bash
# 检查 Service
kubectl get svc -n flink flink-jobmanager

# 检查网络连通性
kubectl exec -it -n flink <taskmanager-pod> -- curl http://flink-jobmanager:8081/overview

# 检查 DNS
kubectl exec -it -n flink <taskmanager-pod> -- nslookup flink-jobmanager
```

### Checkpoint 失败

```bash
# 检查 PVC 状态
kubectl get pvc -n flink

# 检查存储空间
kubectl exec -it -n flink <jobmanager-pod> -- df -h /opt/flink/checkpoints

# 查看 Checkpoint 日志
kubectl logs -n flink -l app=flink,component=jobmanager | grep -i checkpoint
```

## 清理环境

```bash
# 删除所有资源
kubectl delete namespace flink

# 或逐个删除
kubectl delete -f monitor-frontend-deployment.yaml
kubectl delete -f monitor-backend-deployment.yaml
kubectl delete -f flink-taskmanager-deployment.yaml
kubectl delete -f flink-jobmanager-deployment.yaml
kubectl delete -f flink-jobmanager-service.yaml
kubectl delete -f flink-configuration-configmap.yaml
kubectl delete -f flink-pvc.yaml
kubectl delete -f flink-secrets.yaml
kubectl delete -f flink-rbac.yaml
kubectl delete -f namespace.yaml
```

## 性能优化

### 调整资源配额

编辑 Deployment 的 `resources` 部分：

```yaml
resources:
  requests:
    memory: "2Gi"
    cpu: "2000m"
  limits:
    memory: "8Gi"
    cpu: "4000m"
```

### 调整 Checkpoint 间隔

编辑 ConfigMap `flink-config`：

```yaml
execution.checkpointing.interval: 300000  # 5分钟
```

### 调整并行度

```yaml
parallelism.default: 8  # 根据 TaskManager 数量调整
```

## 与 Docker Compose 对比

| 特性 | Docker Compose | Kubernetes |
|------|----------------|------------|
| HA 协调器 | ZooKeeper | Kubernetes API |
| 自动重启 | restart: unless-stopped | Deployment |
| 扩缩容 | docker-compose scale | kubectl scale |
| 滚动更新 | 手动 | kubectl rollout |
| 资源限制 | deploy.resources | resources |
| 健康检查 | healthcheck | livenessProbe/readinessProbe |
| 服务发现 | Docker DNS | Kubernetes Service |
| 配置管理 | .env 文件 | ConfigMap/Secret |
| 存储管理 | Docker Volume | PersistentVolume |
| 监控集成 | 手动配置 | ServiceMonitor |

## 相关文档

- [Flink on Kubernetes 官方文档](https://nightlies.apache.org/flink/flink-docs-stable/docs/deployment/resource-providers/native_kubernetes/)
- [Kubernetes 高可用配置](https://nightlies.apache.org/flink/flink-docs-stable/docs/deployment/ha/kubernetes_ha/)
- `ARCHITECTURE.md` - 系统架构说明
- `BUILD-GUIDE.md` - 构建指南

## 技术支持

如有问题，请查看：
1. Pod 日志: `kubectl logs -n flink <pod-name>`
2. Pod 事件: `kubectl describe pod -n flink <pod-name>`
3. Flink Web UI: http://localhost:8081
