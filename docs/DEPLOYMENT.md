# 实时数据管道系统部署文档

## 目录

- [概述](#概述)
- [部署架构](#部署架构)
- [Docker部署](#docker部署)
- [Kubernetes部署](#kubernetes部署)
- [配置参数详解](#配置参数详解)
- [监控和运维](#监控和运维)
- [故障排查](#故障排查)
- [性能优化](#性能优化)
- [安全配置](#安全配置)

## 概述

本文档提供实时数据管道系统的完整部署指南，包括Docker和Kubernetes两种部署方式。系统采用容器化部署，支持高可用、动态扩缩容和零停机更新。

### 系统要求

**最低配置**:
- CPU: 4核
- 内存: 8GB
- 磁盘: 100GB SSD
- 网络: 1Gbps

**推荐配置**:
- CPU: 16核
- 内存: 32GB
- 磁盘: 500GB SSD
- 网络: 10Gbps

**软件依赖**:
- Docker 20.10+
- Docker Compose 2.0+ (Docker部署)
- Kubernetes 1.20+ (Kubernetes部署)
- kubectl 1.20+ (Kubernetes部署)
- Helm 3.0+ (可选，Kubernetes部署)

## 部署架构

### 组件架构

```
┌─────────────────────────────────────────────────────────────┐
│                     实时数据管道系统                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐ │
│  │ CDC Collector│───>│   DataHub    │───>│    Flink     │ │
│  │  Container   │    │   Service    │    │   Cluster    │ │
│  └──────────────┘    └──────────────┘    └──────────────┘ │
│         │                                        │          │
│         │                                        ▼          │
│         │                                 ┌──────────────┐ │
│         │                                 │ File System  │ │
│         │                                 │ JSON/Parquet │ │
│         │                                 │     /CSV     │ │
│         │                                 └──────────────┘ │
│         │                                                  │
│         ▼                                                  │
│  ┌──────────────────────────────────────────────────────┐ │
│  │              监控和日志系统                            │ │
│  │  - Prometheus Metrics                                │ │
│  │  - Health Check Endpoints                            │ │
│  │  - Alert Manager                                     │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```


### 网络架构

```
外部网络
    │
    ├─> OceanBase数据库 (端口 2881)
    │
    ├─> 阿里云DataHub (HTTPS)
    │
    └─> 管理访问
         │
         ├─> Flink Web UI (端口 8081)
         └─> CDC Collector监控 (端口 8080)

内部网络 (flink-network)
    │
    ├─> JobManager (端口 6123 RPC, 8081 Web UI)
    │
    ├─> TaskManager (端口 6121 Data, 6122 RPC)
    │
    └─> CDC Collector (端口 8080 Health Check)
```

## Docker部署

Docker部署是推荐的部署方式，适合开发、测试和中小规模生产环境。

### 快速部署

#### 1. 准备工作

```bash
# 克隆项目
git clone <repository-url>
cd realtime-data-pipeline

# 构建应用
mvn clean package -DskipTests

# 验证构建产物
ls -lh target/realtime-data-pipeline-*.jar
```

#### 2. 配置环境变量

```bash
# 复制环境变量模板
cp .env.example .env

# 编辑配置文件
vim .env
```

**必需配置项**:

```bash
# 数据库配置
DATABASE_HOST=your-oceanbase-host
DATABASE_PORT=2881
DATABASE_USERNAME=your-username
DATABASE_PASSWORD=your-password
DATABASE_SCHEMA=your-schema
DATABASE_TABLES=table1,table2  # 或使用 * 监控所有表

# DataHub配置
DATAHUB_ENDPOINT=https://dh-cn-hangzhou.aliyuncs.com
DATAHUB_ACCESS_ID=your-access-id
DATAHUB_ACCESS_KEY=your-access-key
DATAHUB_PROJECT=your-project
DATAHUB_TOPIC=your-topic
DATAHUB_CONSUMER_GROUP=cdc-collector-group

# 输出配置
OUTPUT_PATH=/opt/flink/data
OUTPUT_FORMAT=json  # json, parquet, csv
```

#### 3. 启动服务

```bash
# 启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

#### 4. 验证部署

```bash
# 检查服务健康状态
docker-compose ps

# 访问Flink Web UI
open http://localhost:8081

# 检查CDC Collector健康状态
curl http://localhost:8080/health

# 查看系统指标
curl http://localhost:8080/metrics | jq
```


### 高可用部署

启用JobManager高可用配置，支持主备自动切换。

#### 使用ZooKeeper HA

```bash
# 1. 配置高可用模式
cat >> .env << EOF
HA_MODE=zookeeper
HA_ZOOKEEPER_QUORUM=zk1:2181,zk2:2181,zk3:2181
HA_CLUSTER_ID=/flink-cluster
EOF

# 2. 启动ZooKeeper集群（如果没有现成的）
docker-compose --profile ha up -d zookeeper

# 3. 启动Flink集群
docker-compose up -d

# 4. 验证HA配置
curl http://localhost:8081/overview | jq '.flink-version'
```

#### 配置说明

**ZooKeeper HA配置**:
- `HA_MODE=zookeeper`: 启用ZooKeeper HA模式
- `HA_ZOOKEEPER_QUORUM`: ZooKeeper集群地址，多个地址用逗号分隔
- `HA_CLUSTER_ID`: Flink集群在ZooKeeper中的路径
- `HA_STORAGE_DIR`: HA元数据存储目录（默认: /opt/flink/ha）

**验证HA功能**:

```bash
# 1. 查看当前JobManager状态
docker exec flink-jobmanager cat /opt/flink/log/flink-*-standalonesession-*.log | grep "leader"

# 2. 模拟JobManager故障
docker stop flink-jobmanager

# 3. 观察备用JobManager接管（应在30秒内完成）
docker logs -f flink-jobmanager-2

# 4. 重启原JobManager
docker start flink-jobmanager
```

### 扩展TaskManager

系统支持动态扩展TaskManager实例以提高处理能力。

```bash
# 扩展到5个TaskManager
docker-compose up -d --scale taskmanager=5

# 验证扩展结果
docker-compose ps | grep taskmanager

# 查看Flink集群中的TaskManager
curl http://localhost:8081/taskmanagers | jq '.taskmanagers | length'

# 缩减到2个TaskManager
docker-compose up -d --scale taskmanager=2
```

### 数据持久化

配置数据卷以持久化重要数据。

```bash
# 创建数据目录
mkdir -p /data/flink/{checkpoints,savepoints,data,logs}
mkdir -p /data/cdc/{logs,data}

# 修改docker-compose.yml中的卷挂载
volumes:
  - /data/flink/checkpoints:/opt/flink/checkpoints
  - /data/flink/savepoints:/opt/flink/savepoints
  - /data/flink/data:/opt/flink/data
  - /data/flink/logs:/opt/flink/logs
```

### 资源限制

为容器配置资源限制以防止资源耗尽。

```yaml
# 在docker-compose.yml中配置
services:
  jobmanager:
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 2G
        reservations:
          cpus: '1.0'
          memory: 1G
  
  taskmanager:
    deploy:
      resources:
        limits:
          cpus: '4.0'
          memory: 4G
        reservations:
          cpus: '2.0'
          memory: 2G
```


## Kubernetes部署

Kubernetes部署适合大规模生产环境，提供更强大的编排和管理能力。

### 前置条件

```bash
# 验证Kubernetes集群
kubectl cluster-info
kubectl get nodes

# 创建命名空间
kubectl create namespace flink-pipeline

# 设置默认命名空间
kubectl config set-context --current --namespace=flink-pipeline
```

### 部署步骤

#### 1. 创建ConfigMap

```bash
# 创建配置文件
cat > flink-config.yaml << EOF
apiVersion: v1
kind: ConfigMap
metadata:
  name: flink-config
  namespace: flink-pipeline
data:
  flink-conf.yaml: |
    jobmanager.rpc.address: flink-jobmanager
    jobmanager.rpc.port: 6123
    jobmanager.memory.process.size: 2048m
    taskmanager.memory.process.size: 4096m
    taskmanager.numberOfTaskSlots: 4
    parallelism.default: 4
    
    # Checkpoint配置
    state.backend: hashmap
    state.checkpoints.dir: file:///opt/flink/checkpoints
    state.savepoints.dir: file:///opt/flink/savepoints
    execution.checkpointing.interval: 300000
    execution.checkpointing.mode: EXACTLY_ONCE
    execution.checkpointing.timeout: 600000
    
    # 高可用配置
    high-availability: kubernetes
    high-availability.storageDir: file:///opt/flink/ha
    kubernetes.cluster-id: flink-cluster
    
    # Metrics配置
    metrics.reporter.prom.class: org.apache.flink.metrics.prometheus.PrometheusReporter
    metrics.reporter.prom.port: 9249
EOF

kubectl apply -f flink-config.yaml
```

#### 2. 创建持久化存储

```bash
# 创建PersistentVolumeClaim
cat > flink-pvc.yaml << EOF
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: flink-checkpoints
  namespace: flink-pipeline
spec:
  accessModes:
    - ReadWriteMany
  resources:
    requests:
      storage: 100Gi
  storageClassName: standard
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: flink-savepoints
  namespace: flink-pipeline
spec:
  accessModes:
    - ReadWriteMany
  resources:
    requests:
      storage: 50Gi
  storageClassName: standard
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: flink-data
  namespace: flink-pipeline
spec:
  accessModes:
    - ReadWriteMany
  resources:
    requests:
      storage: 500Gi
  storageClassName: standard
EOF

kubectl apply -f flink-pvc.yaml
```

#### 3. 部署JobManager

```bash
cat > jobmanager-deployment.yaml << EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: flink-jobmanager
  namespace: flink-pipeline
spec:
  replicas: 2  # 高可用配置，至少2个实例
  selector:
    matchLabels:
      app: flink
      component: jobmanager
  template:
    metadata:
      labels:
        app: flink
        component: jobmanager
    spec:
      serviceAccountName: flink
      containers:
      - name: jobmanager
        image: realtime-pipeline/jobmanager:1.0.0
        args: ["jobmanager"]
        ports:
        - containerPort: 6123
          name: rpc
        - containerPort: 8081
          name: webui
        - containerPort: 9249
          name: metrics
        env:
        - name: JOB_MANAGER_RPC_ADDRESS
          value: "flink-jobmanager"
        - name: HA_MODE
          value: "kubernetes"
        volumeMounts:
        - name: flink-config
          mountPath: /opt/flink/conf
        - name: checkpoints
          mountPath: /opt/flink/checkpoints
        - name: savepoints
          mountPath: /opt/flink/savepoints
        resources:
          requests:
            cpu: "1"
            memory: "2Gi"
          limits:
            cpu: "2"
            memory: "4Gi"
        livenessProbe:
          httpGet:
            path: /overview
            port: 8081
          initialDelaySeconds: 60
          periodSeconds: 30
          timeoutSeconds: 10
        readinessProbe:
          httpGet:
            path: /overview
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 10
      volumes:
      - name: flink-config
        configMap:
          name: flink-config
      - name: checkpoints
        persistentVolumeClaim:
          claimName: flink-checkpoints
      - name: savepoints
        persistentVolumeClaim:
          claimName: flink-savepoints
---
apiVersion: v1
kind: Service
metadata:
  name: flink-jobmanager
  namespace: flink-pipeline
spec:
  type: LoadBalancer
  ports:
  - name: rpc
    port: 6123
    targetPort: 6123
  - name: webui
    port: 8081
    targetPort: 8081
  - name: metrics
    port: 9249
    targetPort: 9249
  selector:
    app: flink
    component: jobmanager
EOF

kubectl apply -f jobmanager-deployment.yaml
```


#### 4. 部署TaskManager

```bash
cat > taskmanager-deployment.yaml << EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: flink-taskmanager
  namespace: flink-pipeline
spec:
  replicas: 3  # 初始3个TaskManager
  selector:
    matchLabels:
      app: flink
      component: taskmanager
  template:
    metadata:
      labels:
        app: flink
        component: taskmanager
    spec:
      containers:
      - name: taskmanager
        image: realtime-pipeline/taskmanager:1.0.0
        args: ["taskmanager"]
        ports:
        - containerPort: 6121
          name: data
        - containerPort: 6122
          name: rpc
        - containerPort: 9249
          name: metrics
        env:
        - name: JOB_MANAGER_RPC_ADDRESS
          value: "flink-jobmanager"
        - name: TASK_MANAGER_NUMBER_OF_TASK_SLOTS
          value: "4"
        volumeMounts:
        - name: flink-config
          mountPath: /opt/flink/conf
        - name: checkpoints
          mountPath: /opt/flink/checkpoints
        - name: data
          mountPath: /opt/flink/data
        resources:
          requests:
            cpu: "2"
            memory: "4Gi"
          limits:
            cpu: "4"
            memory: "8Gi"
        livenessProbe:
          exec:
            command:
            - pgrep
            - -f
            - org.apache.flink.runtime.taskexecutor.TaskManagerRunner
          initialDelaySeconds: 60
          periodSeconds: 30
      volumes:
      - name: flink-config
        configMap:
          name: flink-config
      - name: checkpoints
        persistentVolumeClaim:
          claimName: flink-checkpoints
      - name: data
        persistentVolumeClaim:
          claimName: flink-data
EOF

kubectl apply -f taskmanager-deployment.yaml
```

#### 5. 部署CDC Collector

```bash
# 创建Secret存储敏感信息
kubectl create secret generic cdc-credentials \
  --from-literal=database-password='your-password' \
  --from-literal=datahub-access-id='your-access-id' \
  --from-literal=datahub-access-key='your-access-key' \
  -n flink-pipeline

# 创建Deployment
cat > cdc-collector-deployment.yaml << EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: cdc-collector
  namespace: flink-pipeline
spec:
  replicas: 1
  selector:
    matchLabels:
      app: cdc-collector
  template:
    metadata:
      labels:
        app: cdc-collector
    spec:
      containers:
      - name: cdc-collector
        image: realtime-pipeline/cdc-collector:1.0.0
        ports:
        - containerPort: 8080
          name: http
        env:
        - name: DATABASE_HOST
          value: "oceanbase.example.com"
        - name: DATABASE_PORT
          value: "2881"
        - name: DATABASE_USERNAME
          value: "root"
        - name: DATABASE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: cdc-credentials
              key: database-password
        - name: DATABASE_SCHEMA
          value: "mydb"
        - name: DATAHUB_ENDPOINT
          value: "https://dh-cn-hangzhou.aliyuncs.com"
        - name: DATAHUB_ACCESS_ID
          valueFrom:
            secretKeyRef:
              name: cdc-credentials
              key: datahub-access-id
        - name: DATAHUB_ACCESS_KEY
          valueFrom:
            secretKeyRef:
              name: cdc-credentials
              key: datahub-access-key
        - name: DATAHUB_PROJECT
          value: "realtime-pipeline"
        - name: DATAHUB_TOPIC
          value: "cdc-events"
        resources:
          requests:
            cpu: "0.5"
            memory: "512Mi"
          limits:
            cpu: "1"
            memory: "1Gi"
        livenessProbe:
          httpGet:
            path: /health/live
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /health/ready
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: cdc-collector
  namespace: flink-pipeline
spec:
  type: ClusterIP
  ports:
  - port: 8080
    targetPort: 8080
  selector:
    app: cdc-collector
EOF

kubectl apply -f cdc-collector-deployment.yaml
```


#### 6. 配置RBAC权限

Kubernetes HA模式需要ServiceAccount权限。

```bash
cat > flink-rbac.yaml << EOF
apiVersion: v1
kind: ServiceAccount
metadata:
  name: flink
  namespace: flink-pipeline
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: flink
  namespace: flink-pipeline
rules:
- apiGroups: [""]
  resources: ["configmaps"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]
- apiGroups: [""]
  resources: ["pods"]
  verbs: ["get", "list", "watch"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: flink
  namespace: flink-pipeline
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: flink
subjects:
- kind: ServiceAccount
  name: flink
  namespace: flink-pipeline
EOF

kubectl apply -f flink-rbac.yaml
```

#### 7. 验证部署

```bash
# 查看所有Pod状态
kubectl get pods -n flink-pipeline

# 查看服务
kubectl get svc -n flink-pipeline

# 查看JobManager日志
kubectl logs -f deployment/flink-jobmanager -n flink-pipeline

# 访问Flink Web UI
kubectl port-forward svc/flink-jobmanager 8081:8081 -n flink-pipeline
open http://localhost:8081

# 查看CDC Collector健康状态
kubectl port-forward svc/cdc-collector 8080:8080 -n flink-pipeline
curl http://localhost:8080/health
```

### 动态扩缩容

#### 手动扩缩容

```bash
# 扩展TaskManager到5个实例
kubectl scale deployment flink-taskmanager --replicas=5 -n flink-pipeline

# 验证扩展结果
kubectl get pods -l component=taskmanager -n flink-pipeline

# 缩减到2个实例
kubectl scale deployment flink-taskmanager --replicas=2 -n flink-pipeline
```

#### 自动扩缩容（HPA）

```bash
# 创建HorizontalPodAutoscaler
cat > taskmanager-hpa.yaml << EOF
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: flink-taskmanager
  namespace: flink-pipeline
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: flink-taskmanager
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
      - type: Percent
        value: 50
        periodSeconds: 60
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
      - type: Percent
        value: 100
        periodSeconds: 60
EOF

kubectl apply -f taskmanager-hpa.yaml

# 查看HPA状态
kubectl get hpa -n flink-pipeline
kubectl describe hpa flink-taskmanager -n flink-pipeline
```

### 监控集成

#### Prometheus监控

```bash
# 创建ServiceMonitor（需要Prometheus Operator）
cat > flink-servicemonitor.yaml << EOF
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: flink-metrics
  namespace: flink-pipeline
  labels:
    app: flink
spec:
  selector:
    matchLabels:
      app: flink
  endpoints:
  - port: metrics
    interval: 30s
    path: /metrics
EOF

kubectl apply -f flink-servicemonitor.yaml
```

#### Grafana Dashboard

```bash
# 导入Flink Dashboard
# Dashboard ID: 10369 (Flink Dashboard)
# 或使用项目提供的自定义Dashboard
```


## 配置参数详解

### 数据库配置

| 参数 | 环境变量 | 默认值 | 说明 |
|------|---------|--------|------|
| database.host | DATABASE_HOST | localhost | OceanBase数据库主机地址 |
| database.port | DATABASE_PORT | 2881 | 数据库端口 |
| database.username | DATABASE_USERNAME | root | 数据库用户名 |
| database.password | DATABASE_PASSWORD | - | 数据库密码（必需） |
| database.schema | DATABASE_SCHEMA | test | 要监控的Schema |
| database.tables | DATABASE_TABLES | * | 要监控的表列表，逗号分隔或* |

**示例**:
```yaml
database:
  host: oceanbase.example.com
  port: 2881
  username: cdc_user
  password: ${DATABASE_PASSWORD}
  schema: production_db
  tables: "orders,users,products"
```

### DataHub配置

| 参数 | 环境变量 | 默认值 | 说明 |
|------|---------|--------|------|
| datahub.endpoint | DATAHUB_ENDPOINT | - | DataHub服务端点（必需） |
| datahub.accessId | DATAHUB_ACCESS_ID | - | 访问ID（必需） |
| datahub.accessKey | DATAHUB_ACCESS_KEY | - | 访问密钥（必需） |
| datahub.project | DATAHUB_PROJECT | realtime-pipeline | 项目名称 |
| datahub.topic | DATAHUB_TOPIC | cdc-events | 主题名称 |
| datahub.consumerGroup | DATAHUB_CONSUMER_GROUP | cdc-collector-group | 消费者组名称 |

**示例**:
```yaml
datahub:
  endpoint: https://dh-cn-hangzhou.aliyuncs.com
  accessId: ${DATAHUB_ACCESS_ID}
  accessKey: ${DATAHUB_ACCESS_KEY}
  project: my-project
  topic: cdc-events
  consumerGroup: flink-consumer
```

### Flink配置

#### 基础配置

| 参数 | 环境变量 | 默认值 | 说明 |
|------|---------|--------|------|
| flink.parallelism | PARALLELISM_DEFAULT | 4 | 默认并行度 |
| jobmanager.memory.process.size | JOB_MANAGER_HEAP_SIZE | 1024m | JobManager内存 |
| taskmanager.memory.process.size | TASK_MANAGER_HEAP_SIZE | 1024m | TaskManager内存 |
| taskmanager.numberOfTaskSlots | TASK_MANAGER_NUMBER_OF_TASK_SLOTS | 4 | 每个TaskManager的任务槽数 |

**示例**:
```yaml
flink:
  parallelism: 8
  jobmanager:
    memory:
      process:
        size: 2048m
  taskmanager:
    memory:
      process:
        size: 4096m
    numberOfTaskSlots: 8
```

#### Checkpoint配置

| 参数 | 环境变量 | 默认值 | 说明 |
|------|---------|--------|------|
| flink.checkpoint.interval | CHECKPOINT_INTERVAL | 300000 | Checkpoint间隔（毫秒） |
| flink.checkpoint.timeout | CHECKPOINT_TIMEOUT | 600000 | Checkpoint超时（毫秒） |
| flink.checkpoint.minPause | CHECKPOINT_MIN_PAUSE | 60000 | 两次Checkpoint最小间隔 |
| flink.checkpoint.maxConcurrent | CHECKPOINT_MAX_CONCURRENT | 1 | 最大并发Checkpoint数 |
| flink.checkpoint.retainedNumber | CHECKPOINT_RETAINED_NUMBER | 3 | 保留的Checkpoint数量 |
| flink.statebackend.type | STATE_BACKEND | hashmap | 状态后端类型 |
| flink.statebackend.checkpointDir | CHECKPOINT_DIR | file:///opt/flink/checkpoints | Checkpoint存储目录 |

**示例**:
```yaml
flink:
  checkpoint:
    interval: 180000  # 3分钟
    timeout: 600000   # 10分钟
    minPause: 60000   # 1分钟
    maxConcurrent: 1
    retainedNumber: 5
  stateBackend:
    type: rocksdb  # 或 hashmap
    checkpointDir: file:///data/checkpoints
```

**状态后端选择**:
- `hashmap`: 内存状态后端，适合小状态
- `rocksdb`: 磁盘状态后端，适合大状态

#### 高可用配置

| 参数 | 环境变量 | 默认值 | 说明 |
|------|---------|--------|------|
| highAvailability.enabled | HA_MODE | false | 是否启用HA |
| highAvailability.mode | HA_MODE | NONE | HA模式（zookeeper/kubernetes） |
| highAvailability.zookeeperQuorum | HA_ZOOKEEPER_QUORUM | - | ZooKeeper集群地址 |
| highAvailability.zookeeperPath | HA_ZOOKEEPER_PATH | /flink | ZooKeeper根路径 |
| highAvailability.kubernetesNamespace | - | flink | Kubernetes命名空间 |
| highAvailability.kubernetesClusterId | - | flink-cluster | Kubernetes集群ID |
| highAvailability.storageDir | HA_STORAGE_DIR | /opt/flink/ha | HA元数据存储目录 |
| highAvailability.jobManagerCount | - | 2 | JobManager数量 |

**ZooKeeper HA示例**:
```yaml
highAvailability:
  enabled: true
  mode: zookeeper
  zookeeperQuorum: zk1:2181,zk2:2181,zk3:2181
  zookeeperPath: /flink-cluster
  storageDir: file:///data/ha
  jobManagerCount: 2
```

**Kubernetes HA示例**:
```yaml
highAvailability:
  enabled: true
  mode: kubernetes
  kubernetesNamespace: flink-pipeline
  kubernetesClusterId: flink-cluster-1
  storageDir: file:///data/ha
  jobManagerCount: 2
```


### 输出配置

| 参数 | 环境变量 | 默认值 | 说明 |
|------|---------|--------|------|
| output.path | OUTPUT_PATH | /opt/flink/data | 输出目录路径 |
| output.format | OUTPUT_FORMAT | json | 输出格式（json/parquet/csv） |
| output.rolling.sizeBytes | OUTPUT_ROLLING_SIZE | 1073741824 | 文件滚动大小（1GB） |
| output.rolling.intervalMs | OUTPUT_ROLLING_INTERVAL | 3600000 | 文件滚动时间间隔（1小时） |
| output.compression | OUTPUT_COMPRESSION | none | 压缩算法（none/gzip/snappy） |
| output.maxRetries | OUTPUT_MAX_RETRIES | 3 | 写入失败最大重试次数 |

**示例**:
```yaml
output:
  path: /data/output
  format: parquet
  rolling:
    sizeBytes: 536870912  # 512MB
    intervalMs: 1800000   # 30分钟
  compression: snappy
  maxRetries: 3
```

**格式选择建议**:
- **JSON**: 易读，适合调试和小数据量
- **Parquet**: 列式存储，适合大数据分析，压缩率高
- **CSV**: 通用格式，适合数据交换

**压缩算法选择**:
- **none**: 无压缩，写入速度最快
- **gzip**: 压缩率高，CPU开销大
- **snappy**: 平衡压缩率和速度（推荐）

### 监控配置

| 参数 | 环境变量 | 默认值 | 说明 |
|------|---------|--------|------|
| monitoring.port | MONITORING_PORT | 8080 | 监控端口 |
| monitoring.metrics.enabled | METRICS_ENABLED | true | 是否启用指标收集 |
| monitoring.alerts.latencyThresholdMs | ALERT_LATENCY_THRESHOLD | 60000 | 延迟告警阈值（毫秒） |
| monitoring.alerts.checkpointFailureRate | ALERT_CHECKPOINT_FAILURE_RATE | 0.1 | Checkpoint失败率告警阈值 |
| monitoring.alerts.loadThreshold | ALERT_LOAD_THRESHOLD | 0.8 | 负载告警阈值 |

**示例**:
```yaml
monitoring:
  port: 8080
  metrics:
    enabled: true
  alerts:
    latencyThresholdMs: 30000  # 30秒
    checkpointFailureRate: 0.05  # 5%
    loadThreshold: 0.7  # 70%
```

### 日志配置

| 参数 | 环境变量 | 默认值 | 说明 |
|------|---------|--------|------|
| logging.level | LOG_LEVEL | INFO | 日志级别（DEBUG/INFO/WARN/ERROR） |
| logging.pattern | - | - | 日志格式 |
| logging.file.path | - | /opt/flink/logs | 日志文件路径 |
| logging.file.maxSize | - | 100MB | 单个日志文件最大大小 |
| logging.file.maxHistory | - | 30 | 保留的日志文件数量 |

**示例**:
```yaml
logging:
  level: INFO
  pattern: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    path: /var/log/flink
    maxSize: 100MB
    maxHistory: 30
```

### 性能调优参数

#### 网络缓冲区配置

```yaml
taskmanager:
  network:
    memory:
      min: 64mb
      max: 256mb
    numberOfBuffers: 2048
```

#### 内存配置

```yaml
taskmanager:
  memory:
    process:
      size: 4096m
    flink:
      size: 3456m
    managed:
      size: 1024m
    network:
      min: 64mb
      max: 256mb
```

#### 并行度配置

```yaml
# 全局并行度
parallelism.default: 8

# 算子级别并行度（在代码中设置）
source.parallelism: 4
process.parallelism: 8
sink.parallelism: 4
```

### 配置优先级

配置参数的优先级从高到低：

1. **环境变量**: 最高优先级
2. **配置文件**: application.yml
3. **默认值**: 代码中的默认值

**示例**:
```bash
# 环境变量覆盖配置文件
export PARALLELISM_DEFAULT=16
export CHECKPOINT_INTERVAL=180000

# 启动应用
docker-compose up -d
```

### 配置验证

系统启动时会自动验证配置参数：

```bash
# 查看配置验证日志
docker logs cdc-collector 2>&1 | grep "Configuration validation"

# 常见验证错误
# - 必需参数缺失
# - 参数类型错误
# - 参数值超出范围
# - 文件路径不可访问
```


## 监控和运维

### 健康检查

系统提供多个健康检查端点用于监控服务状态。

#### CDC Collector健康检查

```bash
# 基本健康检查
curl http://localhost:8080/health

# 存活探测（Liveness Probe）
curl http://localhost:8080/health/live

# 就绪探测（Readiness Probe）
curl http://localhost:8080/health/ready

# 详细指标
curl http://localhost:8080/metrics | jq
```

**响应示例**:
```json
{
  "status": "UP",
  "timestamp": "2025-01-28T12:00:00Z",
  "checks": {
    "database": "UP",
    "datahub": "UP",
    "memory": "UP"
  },
  "metrics": {
    "recordsCollected": 1000000,
    "recordsSent": 999950,
    "recordsFailed": 50,
    "collectRate": 1000.5
  }
}
```

#### Flink健康检查

```bash
# JobManager健康检查
curl http://localhost:8081/overview

# 查看作业状态
curl http://localhost:8081/jobs

# 查看TaskManager状态
curl http://localhost:8081/taskmanagers

# 查看Checkpoint统计
curl http://localhost:8081/jobs/<job-id>/checkpoints
```

### 监控指标

#### 关键指标

**吞吐量指标**:
```bash
# 每秒处理记录数
curl http://localhost:8080/metrics | jq '.records.in.rate'
curl http://localhost:8080/metrics | jq '.records.out.rate'
```

**延迟指标**:
```bash
# 端到端延迟
curl http://localhost:8080/metrics | jq '.latency.p50'
curl http://localhost:8080/metrics | jq '.latency.p99'
```

**Checkpoint指标**:
```bash
# Checkpoint成功率和耗时
curl http://localhost:8081/jobs/<job-id>/checkpoints | jq '.latest.completed'
```

**资源指标**:
```bash
# CPU和内存使用率
curl http://localhost:8081/taskmanagers/<tm-id>/metrics | jq '.cpu.usage'
curl http://localhost:8081/taskmanagers/<tm-id>/metrics | jq '.memory.usage'
```

#### Prometheus集成

```bash
# JobManager Prometheus端点
curl http://localhost:9249/metrics

# 配置Prometheus抓取
cat >> prometheus.yml << EOF
scrape_configs:
  - job_name: 'flink-jobmanager'
    static_configs:
      - targets: ['localhost:9249']
    metrics_path: '/metrics'
    
  - job_name: 'flink-taskmanager'
    static_configs:
      - targets: ['taskmanager-1:9249', 'taskmanager-2:9249']
    metrics_path: '/metrics'
    
  - job_name: 'cdc-collector'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/metrics'
EOF
```

### 告警配置

#### 告警规则

系统支持以下告警类型：

1. **数据延迟告警**: 延迟超过60秒
2. **Checkpoint失败告警**: 失败率超过10%
3. **系统负载告警**: CPU使用率超过80%
4. **反压告警**: 反压级别超过0.8
5. **死信队列告警**: 死信记录数持续增长

#### Prometheus告警规则

```yaml
# prometheus-alerts.yml
groups:
  - name: flink_alerts
    interval: 30s
    rules:
      # 数据延迟告警
      - alert: HighDataLatency
        expr: flink_latency_p99 > 60000
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "数据处理延迟过高"
          description: "P99延迟 {{ $value }}ms 超过60秒阈值"
      
      # Checkpoint失败告警
      - alert: HighCheckpointFailureRate
        expr: rate(flink_checkpoint_failed[5m]) / rate(flink_checkpoint_total[5m]) > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Checkpoint失败率过高"
          description: "失败率 {{ $value | humanizePercentage }} 超过10%"
      
      # CPU使用率告警
      - alert: HighCPUUsage
        expr: flink_taskmanager_cpu_usage > 0.8
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "TaskManager CPU使用率过高"
          description: "CPU使用率 {{ $value | humanizePercentage }} 超过80%"
      
      # 反压告警
      - alert: HighBackpressure
        expr: flink_backpressure_level > 0.8
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "系统反压严重"
          description: "反压级别 {{ $value }} 超过0.8"
```

#### AlertManager配置

```yaml
# alertmanager.yml
global:
  resolve_timeout: 5m

route:
  group_by: ['alertname', 'cluster']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 12h
  receiver: 'default'
  routes:
    - match:
        severity: critical
      receiver: 'critical'
    - match:
        severity: warning
      receiver: 'warning'

receivers:
  - name: 'default'
    webhook_configs:
      - url: 'http://alertmanager-webhook:8080/alerts'
  
  - name: 'critical'
    email_configs:
      - to: 'ops-team@example.com'
        from: 'alertmanager@example.com'
        smarthost: 'smtp.example.com:587'
        auth_username: 'alertmanager@example.com'
        auth_password: 'password'
    webhook_configs:
      - url: 'http://alertmanager-webhook:8080/alerts'
  
  - name: 'warning'
    webhook_configs:
      - url: 'http://alertmanager-webhook:8080/alerts'
```


### 日志管理

#### 日志查看

**Docker部署**:
```bash
# 查看实时日志
docker-compose logs -f jobmanager
docker-compose logs -f taskmanager
docker-compose logs -f cdc-collector

# 查看最近100行日志
docker-compose logs --tail 100 cdc-collector

# 查看特定时间段的日志
docker-compose logs --since 1h cdc-collector
docker-compose logs --since "2025-01-28T12:00:00" jobmanager
```

**Kubernetes部署**:
```bash
# 查看Pod日志
kubectl logs -f deployment/flink-jobmanager -n flink-pipeline
kubectl logs -f deployment/flink-taskmanager -n flink-pipeline

# 查看特定Pod的日志
kubectl logs flink-jobmanager-abc123 -n flink-pipeline

# 查看之前容器的日志
kubectl logs flink-jobmanager-abc123 --previous -n flink-pipeline

# 导出日志到文件
kubectl logs deployment/flink-jobmanager -n flink-pipeline > jobmanager.log
```

#### 日志级别调整

**运行时调整**:
```bash
# 通过环境变量调整
docker exec cdc-collector sh -c 'export LOG_LEVEL=DEBUG'
docker-compose restart cdc-collector

# Kubernetes中调整
kubectl set env deployment/cdc-collector LOG_LEVEL=DEBUG -n flink-pipeline
```

**永久调整**:
```yaml
# 修改application.yml
logging:
  level:
    root: INFO
    com.realtime.pipeline: DEBUG
    org.apache.flink: INFO
```

#### 日志聚合

**使用ELK Stack**:
```yaml
# filebeat配置
filebeat.inputs:
  - type: container
    paths:
      - '/var/lib/docker/containers/*/*.log'
    processors:
      - add_kubernetes_metadata:
          host: ${NODE_NAME}
          matchers:
          - logs_path:
              logs_path: "/var/lib/docker/containers/"

output.elasticsearch:
  hosts: ["elasticsearch:9200"]
  index: "flink-logs-%{+yyyy.MM.dd}"
```

### 运维操作

#### 启动和停止

**Docker部署**:
```bash
# 启动所有服务
docker-compose up -d

# 停止所有服务
docker-compose down

# 重启特定服务
docker-compose restart cdc-collector

# 优雅停止（等待任务完成）
docker-compose stop
```

**Kubernetes部署**:
```bash
# 启动服务
kubectl apply -f k8s/

# 停止服务
kubectl delete -f k8s/

# 重启Pod
kubectl rollout restart deployment/flink-taskmanager -n flink-pipeline

# 优雅停止
kubectl scale deployment flink-taskmanager --replicas=0 -n flink-pipeline
```

#### 扩缩容操作

**扩展TaskManager**:
```bash
# Docker
docker-compose up -d --scale taskmanager=5

# Kubernetes
kubectl scale deployment flink-taskmanager --replicas=5 -n flink-pipeline

# 验证扩展结果
curl http://localhost:8081/taskmanagers | jq '.taskmanagers | length'
```

**缩减TaskManager**:
```bash
# Docker
docker-compose up -d --scale taskmanager=2

# Kubernetes
kubectl scale deployment flink-taskmanager --replicas=2 -n flink-pipeline
```

#### Savepoint操作

**触发Savepoint**:
```bash
# 通过Flink CLI
docker exec flink-jobmanager flink savepoint <job-id> /opt/flink/savepoints

# 通过REST API
curl -X POST http://localhost:8081/jobs/<job-id>/savepoints \
  -H "Content-Type: application/json" \
  -d '{"target-directory": "/opt/flink/savepoints", "cancel-job": false}'

# Kubernetes
kubectl exec -it flink-jobmanager-0 -n flink-pipeline -- \
  flink savepoint <job-id> /opt/flink/savepoints
```

**从Savepoint恢复**:
```bash
# 停止当前作业
curl -X PATCH http://localhost:8081/jobs/<job-id> \
  -d '{"state": "cancelled"}'

# 从Savepoint启动
docker exec flink-jobmanager flink run \
  -s /opt/flink/savepoints/<savepoint-id> \
  /opt/flink/lib/realtime-data-pipeline.jar

# Kubernetes
kubectl exec -it flink-jobmanager-0 -n flink-pipeline -- \
  flink run -s /opt/flink/savepoints/<savepoint-id> \
  /opt/flink/lib/realtime-data-pipeline.jar
```

#### 配置更新

**零停机配置更新**:
```bash
# 1. 触发Savepoint
curl -X POST http://localhost:8081/jobs/<job-id>/savepoints \
  -d '{"target-directory": "/opt/flink/savepoints", "cancel-job": true}'

# 2. 更新配置
vim .env
# 或
kubectl edit configmap flink-config -n flink-pipeline

# 3. 重启服务
docker-compose up -d
# 或
kubectl rollout restart deployment/flink-jobmanager -n flink-pipeline

# 4. 从Savepoint恢复作业
# 作业会自动从最新的Savepoint恢复
```

#### 备份和恢复

**备份Checkpoint和Savepoint**:
```bash
# Docker
docker run --rm -v flink-checkpoints:/source -v /backup:/backup \
  alpine tar czf /backup/checkpoints-$(date +%Y%m%d).tar.gz -C /source .

docker run --rm -v flink-savepoints:/source -v /backup:/backup \
  alpine tar czf /backup/savepoints-$(date +%Y%m%d).tar.gz -C /source .

# Kubernetes
kubectl exec -it flink-jobmanager-0 -n flink-pipeline -- \
  tar czf /tmp/checkpoints.tar.gz -C /opt/flink/checkpoints .

kubectl cp flink-pipeline/flink-jobmanager-0:/tmp/checkpoints.tar.gz \
  ./checkpoints-$(date +%Y%m%d).tar.gz
```

**恢复备份**:
```bash
# Docker
docker run --rm -v flink-checkpoints:/target -v /backup:/backup \
  alpine tar xzf /backup/checkpoints-20250128.tar.gz -C /target

# Kubernetes
kubectl cp ./checkpoints-20250128.tar.gz \
  flink-pipeline/flink-jobmanager-0:/tmp/checkpoints.tar.gz

kubectl exec -it flink-jobmanager-0 -n flink-pipeline -- \
  tar xzf /tmp/checkpoints.tar.gz -C /opt/flink/checkpoints
```


## 故障排查

### 常见问题

#### 1. 容器启动失败

**症状**: 容器无法启动或频繁重启

**排查步骤**:
```bash
# 查看容器状态
docker-compose ps
# 或
kubectl get pods -n flink-pipeline

# 查看容器日志
docker-compose logs cdc-collector
# 或
kubectl logs deployment/cdc-collector -n flink-pipeline

# 查看容器事件
kubectl describe pod <pod-name> -n flink-pipeline

# 检查资源限制
docker stats
# 或
kubectl top pods -n flink-pipeline
```

**常见原因**:
- 配置参数错误（检查环境变量）
- 资源不足（增加内存/CPU限制）
- 网络连接问题（检查数据库和DataHub连接）
- 镜像拉取失败（检查镜像仓库访问）

**解决方案**:
```bash
# 验证配置
docker-compose config

# 增加资源限制
# 修改docker-compose.yml中的resources配置

# 测试网络连接
docker exec cdc-collector ping oceanbase-host
docker exec cdc-collector curl -v https://dh-cn-hangzhou.aliyuncs.com
```

#### 2. 健康检查失败

**症状**: 容器显示unhealthy状态

**排查步骤**:
```bash
# 查看健康检查状态
docker inspect <container-name> | jq '.[0].State.Health'

# 手动执行健康检查命令
docker exec cdc-collector curl -f http://localhost:8080/health/live

# 查看健康检查日志
docker logs cdc-collector 2>&1 | grep health
```

**常见原因**:
- 应用启动时间过长（调整start_period）
- 依赖服务不可用（数据库、DataHub）
- 端口未正确暴露
- 健康检查超时（调整timeout）

**解决方案**:
```yaml
# 调整健康检查参数
healthcheck:
  start_period: 120s  # 增加启动等待时间
  timeout: 30s        # 增加超时时间
  interval: 60s       # 增加检查间隔
```

#### 3. 数据处理延迟高

**症状**: P99延迟超过阈值，触发告警

**排查步骤**:
```bash
# 查看反压情况
curl http://localhost:8081/jobs/<job-id>/vertices/<vertex-id>/backpressure

# 查看TaskManager资源使用
curl http://localhost:8081/taskmanagers | jq '.taskmanagers[] | {id, cpu, memory}'

# 查看Checkpoint耗时
curl http://localhost:8081/jobs/<job-id>/checkpoints | jq '.latest.completed.duration'

# 查看数据倾斜
curl http://localhost:8081/jobs/<job-id>/vertices/<vertex-id>/subtasks/metrics
```

**常见原因**:
- 并行度不足
- 资源不足（CPU、内存）
- 数据倾斜
- Checkpoint耗时过长
- 网络带宽不足

**解决方案**:
```bash
# 增加并行度
export PARALLELISM_DEFAULT=16
docker-compose restart

# 扩展TaskManager
docker-compose up -d --scale taskmanager=5

# 调整Checkpoint间隔
export CHECKPOINT_INTERVAL=600000  # 10分钟
docker-compose restart

# 使用RocksDB状态后端（大状态）
export STATE_BACKEND=rocksdb
docker-compose restart
```

#### 4. Checkpoint频繁失败

**症状**: Checkpoint失败率超过10%

**排查步骤**:
```bash
# 查看Checkpoint历史
curl http://localhost:8081/jobs/<job-id>/checkpoints/history

# 查看失败原因
curl http://localhost:8081/jobs/<job-id>/checkpoints | jq '.latest.failed'

# 检查存储空间
docker exec flink-jobmanager df -h /opt/flink/checkpoints

# 查看Checkpoint大小
du -sh /data/flink/checkpoints/*
```

**常见原因**:
- 存储空间不足
- Checkpoint超时
- 状态过大
- 网络问题

**解决方案**:
```bash
# 增加存储空间
# 清理旧的Checkpoint
docker exec flink-jobmanager find /opt/flink/checkpoints -mtime +7 -delete

# 增加Checkpoint超时
export CHECKPOINT_TIMEOUT=900000  # 15分钟
docker-compose restart

# 使用RocksDB增量Checkpoint
export STATE_BACKEND=rocksdb
docker-compose restart

# 减少Checkpoint频率
export CHECKPOINT_INTERVAL=600000  # 10分钟
docker-compose restart
```

#### 5. 内存溢出

**症状**: TaskManager频繁重启，日志显示OutOfMemoryError

**排查步骤**:
```bash
# 查看内存使用
docker stats

# 查看JVM堆内存
docker exec flink-taskmanager jstat -gc <pid>

# 查看内存配置
docker exec flink-taskmanager cat /opt/flink/conf/flink-conf.yaml | grep memory
```

**常见原因**:
- 堆内存配置过小
- 状态过大
- 内存泄漏
- 托管内存不足

**解决方案**:
```bash
# 增加TaskManager内存
export TASK_MANAGER_HEAP_SIZE=4096m
export TASK_MANAGER_MEMORY_PROCESS_SIZE=5632m
docker-compose restart

# 增加托管内存
export TASK_MANAGER_MEMORY_MANAGED_SIZE=2048m
docker-compose restart

# 使用RocksDB状态后端（减少堆内存使用）
export STATE_BACKEND=rocksdb
docker-compose restart

# 启用堆外内存
export EXTRA_JAVA_OPTS="-XX:MaxDirectMemorySize=2048m"
docker-compose restart
```

#### 6. 数据丢失

**症状**: 输出数据少于输入数据

**排查步骤**:
```bash
# 查看死信队列
docker exec flink-taskmanager ls -lh /opt/flink/data/dlq/

# 查看失败记录数
curl http://localhost:8080/metrics | jq '.failed.records.count'

# 查看Checkpoint恢复情况
curl http://localhost:8081/jobs/<job-id>/checkpoints | jq '.latest.restored'

# 检查数据一致性
# 比对输入和输出记录数
```

**常见原因**:
- 处理失败未重试
- Checkpoint恢复不完整
- 死信队列未处理
- 数据过滤逻辑错误

**解决方案**:
```bash
# 检查死信队列数据
docker exec flink-taskmanager cat /opt/flink/data/dlq/*.json

# 重新处理死信队列数据
# 修复错误后重新提交

# 从Checkpoint恢复
docker exec flink-jobmanager flink run \
  -s /opt/flink/checkpoints/<checkpoint-id> \
  /opt/flink/lib/realtime-data-pipeline.jar

# 启用精确一次语义
# 配置幂等性Sink
```


### 诊断工具

#### Flink Web UI

访问 `http://localhost:8081` 查看：
- 作业拓扑和执行计划
- TaskManager资源使用
- Checkpoint统计
- 反压情况
- 异常信息

#### JVM诊断

```bash
# 查看JVM进程
docker exec flink-taskmanager jps -l

# 查看线程栈
docker exec flink-taskmanager jstack <pid>

# 查看堆内存使用
docker exec flink-taskmanager jmap -heap <pid>

# 生成堆转储
docker exec flink-taskmanager jmap -dump:format=b,file=/tmp/heap.hprof <pid>

# 查看GC情况
docker exec flink-taskmanager jstat -gcutil <pid> 1000
```

#### 网络诊断

```bash
# 测试数据库连接
docker exec cdc-collector nc -zv oceanbase-host 2881

# 测试DataHub连接
docker exec cdc-collector curl -v https://dh-cn-hangzhou.aliyuncs.com

# 查看网络延迟
docker exec cdc-collector ping -c 10 oceanbase-host

# 查看网络带宽
docker exec flink-taskmanager iperf3 -c flink-jobmanager
```

#### 性能分析

```bash
# 使用async-profiler
docker exec flink-taskmanager /opt/async-profiler/profiler.sh -d 60 -f /tmp/profile.html <pid>

# 使用perf（需要特权模式）
docker exec --privileged flink-taskmanager perf record -g -p <pid> -- sleep 60
docker exec --privileged flink-taskmanager perf report
```

## 性能优化

### 并行度优化

**原则**:
- 并行度 = TaskManager数量 × 每个TaskManager的槽数
- 建议并行度为CPU核心数的1-2倍
- 不同算子可以设置不同的并行度

**配置示例**:
```yaml
# 全局并行度
flink:
  parallelism: 16

# 算子级别并行度（代码中设置）
source.setParallelism(8);
process.setParallelism(16);
sink.setParallelism(8);
```

**优化建议**:
```bash
# 计算最优并行度
# 并行度 = 目标吞吐量 / 单个任务吞吐量

# 示例：目标100万条/秒，单任务10万条/秒
# 并行度 = 1000000 / 100000 = 10

# 设置并行度
export PARALLELISM_DEFAULT=10
docker-compose restart
```

### 内存优化

**TaskManager内存配置**:
```yaml
taskmanager:
  memory:
    process:
      size: 4096m      # 总进程内存
    flink:
      size: 3456m      # Flink使用的内存
    managed:
      size: 1024m      # 托管内存（RocksDB）
    network:
      min: 64mb        # 网络缓冲区最小值
      max: 256mb       # 网络缓冲区最大值
    jvm-overhead:
      min: 192mb       # JVM开销最小值
      max: 512mb       # JVM开销最大值
```

**优化建议**:
- 使用RocksDB状态后端处理大状态
- 增加托管内存大小
- 调整网络缓冲区大小
- 启用堆外内存

### Checkpoint优化

**配置优化**:
```yaml
flink:
  checkpoint:
    interval: 300000           # 5分钟（根据数据量调整）
    timeout: 600000            # 10分钟
    minPause: 60000            # 1分钟
    maxConcurrent: 1           # 避免并发Checkpoint
    retainedNumber: 3          # 保留3个Checkpoint
  stateBackend:
    type: rocksdb              # 使用RocksDB
    incremental: true          # 启用增量Checkpoint
```

**优化建议**:
- 根据数据量调整Checkpoint间隔
- 使用RocksDB增量Checkpoint
- 避免Checkpoint期间的大量状态变更
- 使用高性能存储（SSD）

### 网络优化

**配置优化**:
```yaml
taskmanager:
  network:
    numberOfBuffers: 4096      # 增加网络缓冲区数量
    bufferSize: 32kb           # 缓冲区大小
    memory:
      min: 128mb               # 增加网络内存
      max: 512mb
```

**优化建议**:
- 增加网络缓冲区数量和大小
- 使用高带宽网络
- 减少跨节点数据传输
- 优化数据序列化

### 输出优化

**文件输出优化**:
```yaml
output:
  format: parquet              # 使用Parquet格式
  compression: snappy          # 使用Snappy压缩
  rolling:
    sizeBytes: 536870912       # 512MB（根据需求调整）
    intervalMs: 1800000        # 30分钟
  batchSize: 10000             # 批量写入
```

**优化建议**:
- 使用Parquet格式提高压缩率
- 使用Snappy压缩平衡速度和压缩率
- 调整文件滚动大小和间隔
- 启用批量写入

### 资源隔离

**Docker资源限制**:
```yaml
services:
  taskmanager:
    deploy:
      resources:
        limits:
          cpus: '4.0'
          memory: 8G
        reservations:
          cpus: '2.0'
          memory: 4G
```

**Kubernetes资源配置**:
```yaml
resources:
  requests:
    cpu: "2"
    memory: "4Gi"
  limits:
    cpu: "4"
    memory: "8Gi"
```

### 监控和调优

**关键指标监控**:
- 吞吐量：records.in.rate, records.out.rate
- 延迟：latency.p50, latency.p99
- 反压：backpressure.level
- Checkpoint：checkpoint.duration, checkpoint.success.rate
- 资源：cpu.usage, memory.usage

**调优流程**:
1. 监控关键指标
2. 识别瓶颈（CPU、内存、网络、I/O）
3. 调整相应配置
4. 验证优化效果
5. 重复上述步骤


## 安全配置

### 网络安全

#### 防火墙配置

**开放端口**:
```bash
# Flink JobManager
firewall-cmd --permanent --add-port=8081/tcp  # Web UI
firewall-cmd --permanent --add-port=6123/tcp  # RPC
firewall-cmd --permanent --add-port=9249/tcp  # Metrics

# CDC Collector
firewall-cmd --permanent --add-port=8080/tcp  # Health Check

# 重载防火墙规则
firewall-cmd --reload
```

**限制访问**:
```bash
# 只允许特定IP访问Web UI
iptables -A INPUT -p tcp --dport 8081 -s 10.0.0.0/8 -j ACCEPT
iptables -A INPUT -p tcp --dport 8081 -j DROP
```

#### TLS/SSL配置

**启用HTTPS**:
```yaml
# Flink配置
security.ssl.enabled: true
security.ssl.keystore: /opt/flink/conf/keystore.jks
security.ssl.keystore-password: ${SSL_KEYSTORE_PASSWORD}
security.ssl.key-password: ${SSL_KEY_PASSWORD}
security.ssl.truststore: /opt/flink/conf/truststore.jks
security.ssl.truststore-password: ${SSL_TRUSTSTORE_PASSWORD}
```

**生成证书**:
```bash
# 生成自签名证书
keytool -genkeypair -alias flink \
  -keyalg RSA -keysize 2048 \
  -validity 365 \
  -keystore keystore.jks \
  -storepass changeit

# 导出证书
keytool -exportcert -alias flink \
  -keystore keystore.jks \
  -file flink.crt

# 导入到truststore
keytool -importcert -alias flink \
  -file flink.crt \
  -keystore truststore.jks \
  -storepass changeit
```

### 认证和授权

#### 基本认证

**配置Nginx反向代理**:
```nginx
server {
    listen 80;
    server_name flink.example.com;
    
    location / {
        auth_basic "Flink Web UI";
        auth_basic_user_file /etc/nginx/.htpasswd;
        proxy_pass http://localhost:8081;
    }
}
```

**创建密码文件**:
```bash
htpasswd -c /etc/nginx/.htpasswd admin
```

#### Kubernetes RBAC

**创建ServiceAccount**:
```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: flink-operator
  namespace: flink-pipeline
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: flink-operator
  namespace: flink-pipeline
rules:
- apiGroups: [""]
  resources: ["pods", "services", "configmaps"]
  verbs: ["get", "list", "watch", "create", "update", "delete"]
- apiGroups: ["apps"]
  resources: ["deployments"]
  verbs: ["get", "list", "watch", "create", "update", "delete"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: flink-operator
  namespace: flink-pipeline
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: flink-operator
subjects:
- kind: ServiceAccount
  name: flink-operator
  namespace: flink-pipeline
```

### 密钥管理

#### 使用Docker Secrets

```bash
# 创建密钥
echo "my-secret-password" | docker secret create db_password -

# 在docker-compose.yml中使用
services:
  cdc-collector:
    secrets:
      - db_password
    environment:
      - DATABASE_PASSWORD_FILE=/run/secrets/db_password

secrets:
  db_password:
    external: true
```

#### 使用Kubernetes Secrets

```bash
# 创建Secret
kubectl create secret generic cdc-credentials \
  --from-literal=database-password='my-password' \
  --from-literal=datahub-access-key='my-key' \
  -n flink-pipeline

# 在Deployment中使用
env:
- name: DATABASE_PASSWORD
  valueFrom:
    secretKeyRef:
      name: cdc-credentials
      key: database-password
```

#### 使用外部密钥管理

**HashiCorp Vault集成**:
```bash
# 安装Vault Agent
# 配置Vault Agent注入
annotations:
  vault.hashicorp.com/agent-inject: "true"
  vault.hashicorp.com/role: "flink"
  vault.hashicorp.com/agent-inject-secret-database: "secret/data/database"
  vault.hashicorp.com/agent-inject-template-database: |
    {{- with secret "secret/data/database" -}}
    export DATABASE_PASSWORD="{{ .Data.data.password }}"
    {{- end }}
```

### 数据加密

#### 传输加密

**DataHub TLS**:
```yaml
datahub:
  endpoint: https://dh-cn-hangzhou.aliyuncs.com  # 使用HTTPS
  sslEnabled: true
  sslVerify: true
```

**数据库TLS**:
```yaml
database:
  host: oceanbase.example.com
  port: 2881
  sslMode: require
  sslCert: /opt/certs/client-cert.pem
  sslKey: /opt/certs/client-key.pem
  sslRootCert: /opt/certs/ca-cert.pem
```

#### 静态数据加密

**文件系统加密**:
```bash
# 使用LUKS加密卷
cryptsetup luksFormat /dev/sdb
cryptsetup luksOpen /dev/sdb flink-data
mkfs.ext4 /dev/mapper/flink-data
mount /dev/mapper/flink-data /data/flink
```

**Kubernetes加密**:
```yaml
# 启用etcd加密
apiVersion: apiserver.config.k8s.io/v1
kind: EncryptionConfiguration
resources:
  - resources:
    - secrets
    providers:
    - aescbc:
        keys:
        - name: key1
          secret: <base64-encoded-secret>
    - identity: {}
```

### 审计日志

#### 启用审计日志

**Flink审计日志**:
```yaml
# log4j2.xml
<Logger name="org.apache.flink.runtime.security" level="INFO">
  <AppenderRef ref="AuditFile"/>
</Logger>

<RollingFile name="AuditFile"
             fileName="/opt/flink/logs/audit.log"
             filePattern="/opt/flink/logs/audit-%d{yyyy-MM-dd}.log">
  <PatternLayout pattern="%d{ISO8601} %-5p [%t] %c{2} - %m%n"/>
  <Policies>
    <TimeBasedTriggeringPolicy interval="1"/>
  </Policies>
</RollingFile>
```

**Kubernetes审计日志**:
```yaml
# kube-apiserver配置
--audit-log-path=/var/log/kubernetes/audit.log
--audit-log-maxage=30
--audit-log-maxbackup=10
--audit-log-maxsize=100
--audit-policy-file=/etc/kubernetes/audit-policy.yaml
```

### 容器安全

#### 非root用户运行

**Dockerfile**:
```dockerfile
# 创建非root用户
RUN groupadd -r flink && useradd -r -g flink flink

# 设置文件权限
RUN chown -R flink:flink /opt/flink

# 切换用户
USER flink
```

#### 安全扫描

```bash
# 使用Trivy扫描镜像
trivy image realtime-pipeline/jobmanager:1.0.0

# 使用Clair扫描
clairctl analyze realtime-pipeline/jobmanager:1.0.0

# 使用Snyk扫描
snyk container test realtime-pipeline/jobmanager:1.0.0
```

#### 资源限制

**Docker**:
```yaml
services:
  taskmanager:
    security_opt:
      - no-new-privileges:true
    cap_drop:
      - ALL
    cap_add:
      - NET_BIND_SERVICE
    read_only: true
    tmpfs:
      - /tmp
```

**Kubernetes**:
```yaml
securityContext:
  runAsNonRoot: true
  runAsUser: 1000
  fsGroup: 1000
  capabilities:
    drop:
    - ALL
  readOnlyRootFilesystem: true
  allowPrivilegeEscalation: false
```

### 网络策略

**Kubernetes NetworkPolicy**:
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: flink-network-policy
  namespace: flink-pipeline
spec:
  podSelector:
    matchLabels:
      app: flink
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: flink
    ports:
    - protocol: TCP
      port: 6123
    - protocol: TCP
      port: 6121
  egress:
  - to:
    - podSelector:
        matchLabels:
          app: flink
    ports:
    - protocol: TCP
      port: 6123
  - to:
    - namespaceSelector: {}
    ports:
    - protocol: TCP
      port: 53  # DNS
```

## 附录

### 参考文档

- [Apache Flink官方文档](https://flink.apache.org/docs/stable/)
- [Docker官方文档](https://docs.docker.com/)
- [Kubernetes官方文档](https://kubernetes.io/docs/)
- [阿里云DataHub文档](https://help.aliyun.com/product/53345.html)
- [OceanBase文档](https://www.oceanbase.com/docs)

### 相关文档

- [README.md](../README.md) - 项目概述和快速开始
- [docker/README.md](../docker/README.md) - Docker镜像说明
- [docker/QUICKSTART.md](../docker/QUICKSTART.md) - 5分钟快速部署
- [设计文档](../.kiro/specs/realtime-data-pipeline/design.md) - 系统设计
- [需求文档](../.kiro/specs/realtime-data-pipeline/requirements.md) - 功能需求

### 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0.0 | 2025-01-28 | 初始版本 |

### 联系方式

- **技术支持**: support@example.com
- **问题反馈**: <repository-url>/issues
- **文档**: <repository-url>/wiki

---

**最后更新**: 2025-01-28  
**文档版本**: 1.0.0  
**适用系统版本**: 1.0.0
