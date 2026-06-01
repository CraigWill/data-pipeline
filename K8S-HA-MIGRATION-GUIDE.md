# Kubernetes 原生 HA 迁移指南

## 概述

本文档说明如何从 **Docker Compose + ZooKeeper HA** 迁移到 **Kubernetes 原生 HA**。

## 架构变更

### 原架构（Docker Compose）

```
┌─────────────────────────────────────────┐
│          ZooKeeper                      │
│  - Leader Election                      │
│  - Configuration Storage                │
│  - Coordination Service                 │
└──────────────┬──────────────────────────┘
               │
       ┌───────┴────────┐
       │                │
┌──────▼──────┐  ┌──────▼──────────┐
│ JobManager  │  │ JobManager      │
│ (Primary)   │  │ (Standby)       │
└─────────────┘  └─────────────────┘
       │
       ▼
┌─────────────────────┐
│  TaskManager × 3    │
└─────────────────────┘
```

**问题**:
- 需要额外维护 ZooKeeper 集群
- 增加系统复杂度
- 资源开销大
- 故障点多

### 新架构（Kubernetes 原生 HA）

```
┌──────────────────────────────────────────┐
│      Kubernetes Control Plane            │
│  ┌────────────────────────────────────┐  │
│  │  API Server                        │  │
│  │  - Leader Election (ConfigMap)     │  │
│  │  - Service Discovery               │  │
│  │  - Health Monitoring               │  │
│  │  - Auto Restart                    │  │
│  └────────────────────────────────────┘  │
└──────────────┬───────────────────────────┘
               │
       ┌───────┴────────┐
       │                │
┌──────▼──────┐  ┌──────▼──────┐
│ JobManager  │  │ JobManager  │
│ Pod 1       │  │ Pod 2       │
│ (Leader)    │  │ (Standby)   │
└─────────────┘  └─────────────┘
       │
       ▼
┌─────────────────────────────┐
│  TaskManager Deployment     │
│  (Replicas: 3, Auto-scale)  │
└─────────────────────────────┘
```

**优势**:
- ✅ 无需 ZooKeeper，减少依赖
- ✅ Kubernetes 原生支持，运维简单
- ✅ 自动故障恢复
- ✅ 弹性伸缩
- ✅ 滚动更新
- ✅ 统一监控

## 核心变更

### 1. HA 配置变更

#### Docker Compose 配置

```yaml
# docker-compose.yml
environment:
  - HA_MODE=zookeeper
  - HA_ZOOKEEPER_QUORUM=zookeeper:2181
  - HA_ZOOKEEPER_PATH_ROOT=/flink
  - HA_CLUSTER_ID=/realtime-pipeline
  - HA_STORAGE_DIR=file:///opt/flink/ha
```

#### Kubernetes 配置

```yaml
# flink-configuration-configmap.yaml
high-availability.type: kubernetes
high-availability.storageDir: file:///opt/flink/ha
kubernetes.cluster-id: flink-cluster
kubernetes.namespace: flink
```

### 2. Leader Election 机制

#### ZooKeeper 方式

```
ZooKeeper 节点结构:
/flink
  /realtime-pipeline
    /leader
      /resource_manager_lock
      /dispatcher_lock
      /rest_server_lock
```

- 使用 ZooKeeper 的分布式锁
- 需要维护 ZooKeeper 连接
- 依赖 ZooKeeper 的一致性协议

#### Kubernetes 方式

```
Kubernetes ConfigMap:
flink-cluster-config-map-leader-election
  - resource_manager_lock
  - dispatcher_lock
  - rest_server_lock
```

- 使用 Kubernetes ConfigMap 的 `resourceVersion` 实现乐观锁
- 通过 Kubernetes API 进行 Leader 选举
- 利用 Kubernetes 的 RBAC 权限控制

### 3. 服务发现

#### Docker Compose

```yaml
# 通过 Docker DNS
jobmanager:
  hostname: jobmanager
  
taskmanager:
  environment:
    - JOB_MANAGER_RPC_ADDRESS=jobmanager
```

#### Kubernetes

```yaml
# 通过 Kubernetes Service
apiVersion: v1
kind: Service
metadata:
  name: flink-jobmanager
spec:
  selector:
    app: flink
    component: jobmanager
  ports:
  - port: 6123
```

### 4. 存储管理

#### Docker Compose

```yaml
volumes:
  - ./data/flink-checkpoints:/opt/flink/checkpoints
  - ./data/flink-ha:/opt/flink/ha
```

#### Kubernetes

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: flink-checkpoints-pvc
spec:
  accessModes:
  - ReadWriteMany
  resources:
    requests:
      storage: 10Gi
```

## 迁移步骤

### 阶段 1: 准备工作

#### 1.1 备份数据

```bash
# 备份 checkpoint 数据
tar -czf checkpoints-backup.tar.gz data/flink-checkpoints/

# 备份 savepoint
tar -czf savepoints-backup.tar.gz data/flink-savepoints/

# 备份配置
cp .env .env.backup
cp docker-compose.yml docker-compose.yml.backup
```

#### 1.2 停止 Docker Compose 服务

```bash
# 创建 savepoint（可选，用于无缝迁移）
# 通过 Flink Web UI 或 CLI 创建 savepoint

# 停止服务
docker-compose down
```

#### 1.3 准备 Kubernetes 集群

```bash
# 检查集群状态
kubectl cluster-info

# 检查存储类
kubectl get storageclass

# 如果没有支持 ReadWriteMany 的存储类，需要先配置
```

### 阶段 2: 部署到 Kubernetes

#### 2.1 构建镜像

```bash
# 构建 Flink 镜像
./build-flink-images.sh

# 构建 Monitor 镜像
docker build -f monitor/Dockerfile -t monitor-backend:latest .
docker build -f monitor/frontend-vue/Dockerfile -t monitor-frontend:latest .

# 如果使用私有仓库，推送镜像
docker tag flink-jobmanager:latest your-registry/flink-jobmanager:latest
docker push your-registry/flink-jobmanager:latest
# ... 其他镜像
```

#### 2.2 配置 Secret

```bash
cd k8s

# 复制配置模板
cp flink-secrets.yaml.example flink-secrets.yaml

# 编辑配置（从 .env 文件迁移）
vim flink-secrets.yaml
```

从 `.env` 迁移配置：

```bash
# .env
DATABASE_HOST=oracle-host
DATABASE_PASSWORD=secret123

# flink-secrets.yaml
stringData:
  database-host: "oracle-host"
  database-password: "secret123"
```

#### 2.3 上传 Checkpoint 数据（可选）

如果需要从现有 checkpoint 恢复：

```bash
# 创建临时 Pod
kubectl run -it --rm upload-pod --image=busybox -n flink -- sh

# 在另一个终端上传数据
kubectl cp data/flink-checkpoints upload-pod:/tmp/checkpoints -n flink

# 将数据复制到 PVC
# （需要先挂载 PVC 到临时 Pod）
```

#### 2.4 部署服务

```bash
# 一键部署
./deploy.sh

# 或手动部署
kubectl apply -f namespace.yaml
kubectl apply -f flink-rbac.yaml
kubectl apply -f flink-secrets.yaml
kubectl apply -f flink-pvc.yaml
kubectl apply -f flink-configuration-configmap.yaml
kubectl apply -f flink-jobmanager-service.yaml
kubectl apply -f flink-jobmanager-deployment.yaml
kubectl apply -f flink-taskmanager-deployment.yaml
kubectl apply -f monitor-backend-deployment.yaml
kubectl apply -f monitor-frontend-deployment.yaml
```

### 阶段 3: 验证和测试

#### 3.1 验证部署

```bash
# 查看 Pod 状态
kubectl get pods -n flink

# 查看日志
kubectl logs -n flink -l app=flink,component=jobmanager -f

# 访问 Flink Web UI
kubectl port-forward -n flink svc/flink-jobmanager-rest 8081:8081
# 浏览器访问: http://localhost:8081
```

#### 3.2 提交测试任务

```bash
# 通过 Monitor Backend API 提交任务
# 或通过 Flink Web UI 上传 JAR 并提交
```

#### 3.3 测试 HA 功能

```bash
# 测试 JobManager 故障转移
kubectl delete pod -n flink <jobmanager-pod-name>

# 观察新 Leader 选举
kubectl logs -n flink -l app=flink,component=jobmanager -f

# 验证任务继续运行
```

### 阶段 4: 清理旧环境

```bash
# 确认 Kubernetes 环境运行正常后，清理 Docker Compose 环境

# 删除容器和网络
docker-compose down -v

# 删除镜像（可选）
docker rmi flink-jobmanager:latest
docker rmi flink-taskmanager:latest

# 保留备份数据
# 不要删除 checkpoints-backup.tar.gz 和 savepoints-backup.tar.gz
```

## 配置对照表

| 配置项 | Docker Compose | Kubernetes |
|--------|----------------|------------|
| HA 类型 | `HA_MODE=zookeeper` | `high-availability.type: kubernetes` |
| HA 存储 | `HA_STORAGE_DIR` | `high-availability.storageDir` |
| 集群 ID | `HA_CLUSTER_ID` | `kubernetes.cluster-id` |
| 命名空间 | - | `kubernetes.namespace` |
| ZK 地址 | `HA_ZOOKEEPER_QUORUM` | ❌ 不需要 |
| ZK 路径 | `HA_ZOOKEEPER_PATH_ROOT` | ❌ 不需要 |
| 副本数 | `scale: 3` | `replicas: 3` |
| 重启策略 | `restart: unless-stopped` | `Deployment` 自动管理 |
| 健康检查 | `healthcheck` | `livenessProbe/readinessProbe` |
| 资源限制 | `deploy.resources` | `resources.limits/requests` |
| 端口映射 | `ports: "8081:8081"` | `Service` + `LoadBalancer/NodePort` |
| 环境变量 | `environment` | `env` + `Secret/ConfigMap` |
| 数据卷 | `volumes` | `PersistentVolumeClaim` |

## 运维对比

### Docker Compose 运维

```bash
# 启动服务
docker-compose up -d

# 扩展 TaskManager
docker-compose up -d --scale taskmanager=5

# 查看日志
docker-compose logs -f jobmanager

# 重启服务
docker-compose restart jobmanager

# 更新镜像
docker-compose pull
docker-compose up -d

# 停止服务
docker-compose down
```

### Kubernetes 运维

```bash
# 启动服务
kubectl apply -f k8s/

# 扩展 TaskManager
kubectl scale deployment flink-taskmanager -n flink --replicas=5

# 查看日志
kubectl logs -n flink -l app=flink,component=jobmanager -f

# 重启服务
kubectl rollout restart deployment flink-jobmanager -n flink

# 更新镜像
kubectl set image deployment/flink-jobmanager -n flink \
  jobmanager=your-registry/flink-jobmanager:v2.0

# 停止服务
kubectl delete -f k8s/
```

## 性能对比

| 指标 | Docker Compose | Kubernetes |
|------|----------------|------------|
| 启动时间 | ~30秒 | ~60秒（包括镜像拉取） |
| 故障恢复 | ~10秒 | ~15秒 |
| 扩容速度 | ~5秒/实例 | ~10秒/实例 |
| 资源开销 | ZooKeeper: ~500MB | API Server: 共享 |
| 监控集成 | 需手动配置 | 原生支持 Prometheus |

## 常见问题

### Q1: 为什么选择 Kubernetes 原生 HA？

**A**: 
- 减少组件依赖（无需 ZooKeeper）
- 降低运维复杂度
- 更好的云原生集成
- 统一的资源管理和监控

### Q2: Kubernetes HA 是否稳定？

**A**: 
- Flink 1.12+ 官方支持 Kubernetes HA
- 生产环境广泛使用
- 性能和稳定性与 ZooKeeper HA 相当

### Q3: 如何从 ZooKeeper HA 迁移？

**A**: 
1. 创建 savepoint
2. 停止 Docker Compose 服务
3. 部署到 Kubernetes
4. 从 savepoint 恢复任务

### Q4: 是否支持混合部署？

**A**: 
- 不建议混合部署（Docker Compose + Kubernetes）
- 建议完全迁移到 Kubernetes
- 或保持 Docker Compose（用于开发/测试）

### Q5: 如何回滚到 Docker Compose？

**A**: 
```bash
# 1. 在 Kubernetes 中创建 savepoint
# 2. 停止 Kubernetes 服务
kubectl delete -f k8s/

# 3. 恢复 Docker Compose 配置
cp docker-compose.yml.backup docker-compose.yml
cp .env.backup .env

# 4. 启动 Docker Compose
docker-compose up -d

# 5. 从 savepoint 恢复任务
```

## 最佳实践

### 1. 使用 Namespace 隔离

```bash
# 开发环境
kubectl create namespace flink-dev

# 生产环境
kubectl create namespace flink-prod
```

### 2. 配置资源配额

```yaml
apiVersion: v1
kind: ResourceQuota
metadata:
  name: flink-quota
  namespace: flink
spec:
  hard:
    requests.cpu: "20"
    requests.memory: 40Gi
    limits.cpu: "40"
    limits.memory: 80Gi
```

### 3. 启用自动扩缩容

```bash
# 应用 HPA 配置
kubectl apply -f flink-hpa.yaml

# 查看 HPA 状态
kubectl get hpa -n flink
```

### 4. 配置监控告警

```yaml
# Prometheus AlertManager 规则
groups:
- name: flink
  rules:
  - alert: FlinkJobManagerDown
    expr: up{job="flink-jobmanager"} == 0
    for: 5m
    annotations:
      summary: "Flink JobManager is down"
```

### 5. 定期备份

```bash
# 定期创建 savepoint
# 通过 CronJob 自动化

apiVersion: batch/v1
kind: CronJob
metadata:
  name: flink-savepoint
  namespace: flink
spec:
  schedule: "0 2 * * *"  # 每天凌晨2点
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: savepoint
            image: flink:1.17
            command:
            - /bin/bash
            - -c
            - |
              flink savepoint <job-id> /opt/flink/savepoints
```

## 总结

### 迁移收益

- ✅ **简化架构**: 移除 ZooKeeper 依赖
- ✅ **降低成本**: 减少资源开销
- ✅ **提升可靠性**: Kubernetes 自愈能力
- ✅ **增强弹性**: 自动扩缩容
- ✅ **统一运维**: 与其他服务一致的管理方式

### 迁移成本

- ⚠️ **学习曲线**: 需要了解 Kubernetes 基础
- ⚠️ **初期投入**: 配置和测试时间
- ⚠️ **存储要求**: 需要 ReadWriteMany 存储类

### 适用场景

**推荐使用 Kubernetes HA**:
- 已有 Kubernetes 集群
- 需要弹性伸缩
- 追求云原生架构
- 多环境部署（dev/staging/prod）

**继续使用 Docker Compose + ZooKeeper**:
- 单机部署
- 开发/测试环境
- 不熟悉 Kubernetes
- 已有稳定的 ZooKeeper 集群

## 相关文档

- `k8s/README.md` - Kubernetes 部署详细指南
- `ARCHITECTURE.md` - 系统架构说明
- `BUILD-GUIDE.md` - 构建指南
- `COMPLETE-STARTUP-GUIDE.md` - Docker Compose 启动指南

## 技术支持

如有问题，请查看：
1. Kubernetes 文档: https://kubernetes.io/docs/
2. Flink on Kubernetes: https://nightlies.apache.org/flink/flink-docs-stable/docs/deployment/resource-providers/native_kubernetes/
3. 项目 Issue: 提交到项目仓库
