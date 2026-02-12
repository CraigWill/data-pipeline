# Task 14.4: Docker Compose配置

## 概述

本文档描述了实时数据管道系统的Docker Compose配置实现。

**验证需求:**
- 需求 8.7: THE System SHALL 提供Docker Compose配置文件

## 实现内容

### 1. Docker Compose配置文件

**文件位置:** `docker-compose.yml`

Docker Compose配置文件定义了完整的系统部署，包括：
- Flink JobManager服务
- Flink TaskManager服务（支持动态扩展）
- CDC Collector服务
- ZooKeeper服务（可选，用于高可用）
- 网络配置
- 数据卷配置
- 环境变量配置
- 服务依赖关系

### 2. 服务定义

#### 2.1 JobManager服务

```yaml
jobmanager:
  build:
    context: .
    dockerfile: docker/jobmanager/Dockerfile
  container_name: flink-jobmanager
  hostname: jobmanager
  ports:
    - "8081:8081"  # Web UI
    - "6123:6123"  # RPC
    - "6124:6124"  # Blob Server
    - "9249:9249"  # Prometheus Metrics
  environment:
    # 配置参数通过环境变量传递
  volumes:
    # 持久化存储
  networks:
    - flink-network
  restart: unless-stopped
  healthcheck:
    # 健康检查配置
  deploy:
    # 资源限制
```

**特性:**
- 提供Web UI（端口8081）
- 支持高可用配置
- 自动重启策略
- 健康检查
- 资源限制


#### 2.2 TaskManager服务

```yaml
taskmanager:
  build:
    context: .
    dockerfile: docker/taskmanager/Dockerfile
  depends_on:
    jobmanager:
      condition: service_healthy
  # 支持动态扩展，不设置container_name
  environment:
    # 配置参数
  volumes:
    # 持久化存储
  networks:
    - flink-network
  restart: unless-stopped
  healthcheck:
    # 健康检查配置
```

**特性:**
- 支持动态扩展（使用 `--scale` 参数）
- 等待JobManager健康后启动
- 自动连接到JobManager
- 资源限制

**扩展示例:**
```bash
# 启动3个TaskManager实例
docker-compose up -d --scale taskmanager=3
```

#### 2.3 CDC Collector服务

```yaml
cdc-collector:
  build:
    context: .
    dockerfile: docker/cdc-collector/Dockerfile
  container_name: cdc-collector
  depends_on:
    jobmanager:
      condition: service_healthy
  ports:
    - "8080:8080"  # Health Check & Monitoring
  environment:
    # 数据库和DataHub配置
  volumes:
    # 日志和数据存储
  restart: unless-stopped
  healthcheck:
    # HTTP健康检查
```

**特性:**
- 从OceanBase捕获变更数据
- 发送数据到DataHub
- 提供健康检查端点
- 自动重试和重连


#### 2.4 ZooKeeper服务（可选）

```yaml
zookeeper:
  image: zookeeper:3.8
  profiles:
    - ha
  ports:
    - "2181:2181"
  environment:
    # ZooKeeper配置
  volumes:
    # 数据持久化
  restart: unless-stopped
```

**用途:**
- 用于Flink JobManager高可用
- 存储集群元数据
- 协调主备切换

**启用方式:**
```bash
# 启用高可用模式
docker-compose --profile ha up -d
```

### 3. 网络配置

```yaml
networks:
  flink-network:
    driver: bridge
    name: flink-network
```

**特性:**
- 所有服务在同一网络中
- 服务间可通过服务名互相访问
- 隔离的网络环境

**服务发现:**
- JobManager: `jobmanager:6123`
- TaskManager: `taskmanager-1:6122`, `taskmanager-2:6122`, ...
- CDC Collector: `cdc-collector:8080`
- ZooKeeper: `zookeeper:2181`

### 4. 数据卷配置

```yaml
volumes:
  flink-checkpoints:    # Checkpoint数据
  flink-savepoints:     # Savepoint数据
  flink-logs:           # Flink日志
  flink-data:           # TaskManager数据
  cdc-logs:             # CDC Collector日志
  cdc-data:             # CDC Collector数据
  zookeeper-data:       # ZooKeeper数据
  zookeeper-logs:       # ZooKeeper日志
```

**持久化策略:**
- 使用命名卷（named volumes）
- 容器重启后数据保留
- 支持备份和恢复


### 5. 环境变量配置

#### 5.1 环境变量文件

**文件位置:** `.env.example`

提供了完整的环境变量配置示例，包括：
- 数据库配置（必需）
- DataHub配置（必需）
- Flink配置（可选）
- 高可用配置（可选）
- 监控配置（可选）

**使用方法:**
```bash
# 1. 复制示例文件
cp .env.example .env

# 2. 编辑配置
vim .env

# 3. 启动服务
docker-compose up -d
```

#### 5.2 必需的环境变量

**数据库配置:**
- `DATABASE_HOST`: OceanBase主机地址
- `DATABASE_USERNAME`: 数据库用户名
- `DATABASE_PASSWORD`: 数据库密码

**DataHub配置:**
- `DATAHUB_ENDPOINT`: DataHub服务端点
- `DATAHUB_ACCESS_ID`: 访问ID
- `DATAHUB_ACCESS_KEY`: 访问密钥

#### 5.3 可选的环境变量

所有配置都有合理的默认值，可根据需要覆盖：
- Flink并行度、内存配置
- Checkpoint间隔和存储
- 日志级别
- 重试策略
- 资源限制

### 6. 服务依赖关系

```yaml
taskmanager:
  depends_on:
    jobmanager:
      condition: service_healthy

cdc-collector:
  depends_on:
    jobmanager:
      condition: service_healthy
```

**依赖关系说明:**
- TaskManager等待JobManager健康后启动
- CDC Collector等待JobManager健康后启动
- 确保启动顺序正确
- 避免连接失败


## 使用方法

### 1. 基本使用

#### 1.1 准备工作

```bash
# 1. 构建应用程序
mvn clean package -DskipTests

# 2. 配置环境变量
cp .env.example .env
vim .env  # 编辑配置

# 3. 验证配置
docker-compose config
```

#### 1.2 启动服务

```bash
# 启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f jobmanager
```

#### 1.3 停止服务

```bash
# 停止所有服务
docker-compose down

# 停止并删除数据卷
docker-compose down -v

# 停止特定服务
docker-compose stop taskmanager
```

### 2. 动态扩展

#### 2.1 扩展TaskManager

```bash
# 启动3个TaskManager实例
docker-compose up -d --scale taskmanager=3

# 查看TaskManager实例
docker-compose ps taskmanager

# 缩减到1个实例
docker-compose up -d --scale taskmanager=1
```

#### 2.2 验证扩展

```bash
# 访问Flink Web UI
open http://localhost:8081

# 查看TaskManager列表
curl http://localhost:8081/taskmanagers
```

### 3. 高可用模式

#### 3.1 启用高可用

```bash
# 1. 配置环境变量
cat >> .env << EOF
HA_MODE=zookeeper
HA_ZOOKEEPER_QUORUM=zookeeper:2181
HA_CLUSTER_ID=/flink-cluster
EOF

# 2. 启动服务（包括ZooKeeper）
docker-compose --profile ha up -d

# 3. 验证ZooKeeper
docker exec zookeeper zkServer.sh status
```

#### 3.2 测试故障切换

```bash
# 1. 停止主JobManager
docker-compose stop jobmanager

# 2. 观察备用JobManager接管
docker-compose logs -f jobmanager

# 3. 重启JobManager
docker-compose start jobmanager
```


### 4. 健康检查

#### 4.1 查看健康状态

```bash
# 方法1: docker-compose ps
docker-compose ps

# 方法2: docker ps
docker ps

# 方法3: 检查特定服务
docker inspect flink-jobmanager | jq '.[0].State.Health'
```

#### 4.2 测试健康检查端点

```bash
# JobManager
curl http://localhost:8081/overview

# CDC Collector
curl http://localhost:8080/health
curl http://localhost:8080/health/live
curl http://localhost:8080/health/ready
curl http://localhost:8080/metrics
```

### 5. 监控和日志

#### 5.1 查看日志

```bash
# 所有服务日志
docker-compose logs -f

# 特定服务日志
docker-compose logs -f jobmanager
docker-compose logs -f taskmanager
docker-compose logs -f cdc-collector

# 最近100行日志
docker-compose logs --tail=100 jobmanager
```

#### 5.2 访问Prometheus指标

```bash
# JobManager指标
curl http://localhost:9249/metrics

# TaskManager指标（动态端口）
docker-compose ps taskmanager
curl http://localhost:<port>/metrics
```

#### 5.3 访问Flink Web UI

```bash
# 浏览器访问
open http://localhost:8081

# 查看作业列表
curl http://localhost:8081/jobs

# 查看TaskManager列表
curl http://localhost:8081/taskmanagers
```

### 6. 数据备份和恢复

#### 6.1 备份数据卷

```bash
# 备份Checkpoint数据
docker run --rm \
  -v flink-checkpoints:/data \
  -v $(pwd)/backup:/backup \
  alpine tar czf /backup/checkpoints-$(date +%Y%m%d).tar.gz /data

# 备份Savepoint数据
docker run --rm \
  -v flink-savepoints:/data \
  -v $(pwd)/backup:/backup \
  alpine tar czf /backup/savepoints-$(date +%Y%m%d).tar.gz /data
```

#### 6.2 恢复数据卷

```bash
# 恢复Checkpoint数据
docker run --rm \
  -v flink-checkpoints:/data \
  -v $(pwd)/backup:/backup \
  alpine tar xzf /backup/checkpoints-20250128.tar.gz -C /
```


## 配置详解

### 1. 资源限制

每个服务都配置了资源限制，防止资源耗尽：

```yaml
deploy:
  resources:
    limits:
      cpus: '2.0'      # 最大CPU使用
      memory: 2G       # 最大内存使用
    reservations:
      cpus: '1.0'      # 保留CPU
      memory: 1G       # 保留内存
```

**调整建议:**
- 开发环境: 使用默认配置
- 生产环境: 根据负载调整
- 监控资源使用: `docker stats`

### 2. 重启策略

所有服务使用 `unless-stopped` 重启策略：

```yaml
restart: unless-stopped
```

**策略说明:**
- 容器退出时自动重启
- 手动停止时不重启
- Docker守护进程重启时重启容器
- 满足需求4.5（2分钟内重启）

### 3. 健康检查配置

所有服务都配置了健康检查：

```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:8081/overview"]
  interval: 30s      # 检查间隔
  timeout: 10s       # 超时时间
  start_period: 60s  # 启动宽限期
  retries: 3         # 重试次数
```

**参数说明:**
- `interval`: 每30秒检查一次
- `timeout`: 单次检查10秒超时
- `start_period`: 启动后60秒开始检查
- `retries`: 连续失败3次标记为不健康

### 4. 端口映射

**JobManager端口:**
- 8081: Web UI（浏览器访问）
- 6123: RPC通信（内部使用）
- 6124: Blob服务器（内部使用）
- 9249: Prometheus指标（监控系统）

**TaskManager端口:**
- 6121-6130: 数据交换端口（支持多实例）
- 6122: RPC通信（动态分配）
- 9249: Prometheus指标（动态分配）

**CDC Collector端口:**
- 8080: 健康检查和监控

**ZooKeeper端口:**
- 2181: 客户端连接


## 故障排查

### 1. 服务无法启动

**问题:** 服务启动失败

**排查步骤:**
```bash
# 1. 查看服务状态
docker-compose ps

# 2. 查看日志
docker-compose logs <service-name>

# 3. 检查配置
docker-compose config

# 4. 验证环境变量
docker-compose config | grep -A 5 environment
```

**常见原因:**
- 环境变量未配置
- 端口被占用
- 镜像未构建
- 数据卷权限问题

### 2. 服务健康检查失败

**问题:** 服务标记为unhealthy

**排查步骤:**
```bash
# 1. 查看健康检查日志
docker inspect <container-name> | jq '.[0].State.Health.Log'

# 2. 手动执行健康检查
docker exec <container-name> curl -f http://localhost:8081/overview

# 3. 查看应用日志
docker logs <container-name>

# 4. 检查进程状态
docker exec <container-name> ps aux
```

### 3. 服务间无法通信

**问题:** 服务间连接失败

**排查步骤:**
```bash
# 1. 检查网络
docker network inspect flink-network

# 2. 测试连接
docker exec taskmanager ping jobmanager
docker exec taskmanager nc -zv jobmanager 6123

# 3. 检查DNS解析
docker exec taskmanager nslookup jobmanager
```

### 4. 数据卷问题

**问题:** 数据丢失或权限错误

**排查步骤:**
```bash
# 1. 查看数据卷
docker volume ls
docker volume inspect flink-checkpoints

# 2. 检查数据卷内容
docker run --rm -v flink-checkpoints:/data alpine ls -la /data

# 3. 修复权限
docker run --rm -v flink-checkpoints:/data alpine chown -R 9999:9999 /data
```

### 5. 资源不足

**问题:** 容器OOM或CPU限制

**排查步骤:**
```bash
# 1. 查看资源使用
docker stats

# 2. 查看容器退出原因
docker inspect <container-name> | jq '.[0].State'

# 3. 调整资源限制
# 编辑 docker-compose.yml 中的 deploy.resources
```


## 生产环境部署

### 1. 安全配置

#### 1.1 敏感信息管理

```bash
# 使用Docker secrets（Swarm模式）
echo "my-password" | docker secret create db_password -

# 在docker-compose.yml中引用
secrets:
  db_password:
    external: true

services:
  cdc-collector:
    secrets:
      - db_password
```

#### 1.2 网络隔离

```yaml
networks:
  frontend:
    driver: bridge
  backend:
    driver: bridge
    internal: true  # 内部网络，不能访问外部
```

#### 1.3 只读文件系统

```yaml
services:
  jobmanager:
    read_only: true
    tmpfs:
      - /tmp
      - /opt/flink/tmp
```

### 2. 高可用配置

#### 2.1 多JobManager实例

```yaml
jobmanager-1:
  # 主JobManager配置
  environment:
    - HA_MODE=zookeeper
    - HA_ZOOKEEPER_QUORUM=zookeeper:2181

jobmanager-2:
  # 备用JobManager配置
  environment:
    - HA_MODE=zookeeper
    - HA_ZOOKEEPER_QUORUM=zookeeper:2181
```

#### 2.2 ZooKeeper集群

```yaml
zookeeper-1:
  environment:
    - ZOO_MY_ID=1
    - ZOO_SERVERS=server.1=zookeeper-1:2888:3888;2181 server.2=zookeeper-2:2888:3888;2181 server.3=zookeeper-3:2888:3888;2181

zookeeper-2:
  environment:
    - ZOO_MY_ID=2
    - ZOO_SERVERS=server.1=zookeeper-1:2888:3888;2181 server.2=zookeeper-2:2888:3888;2181 server.3=zookeeper-3:2888:3888;2181

zookeeper-3:
  environment:
    - ZOO_MY_ID=3
    - ZOO_SERVERS=server.1=zookeeper-1:2888:3888;2181 server.2=zookeeper-2:2888:3888;2181 server.3=zookeeper-3:2888:3888;2181
```

### 3. 监控和告警

#### 3.1 Prometheus配置

```yaml
prometheus:
  image: prom/prometheus:latest
  ports:
    - "9090:9090"
  volumes:
    - ./prometheus.yml:/etc/prometheus/prometheus.yml
    - prometheus-data:/prometheus
  command:
    - '--config.file=/etc/prometheus/prometheus.yml'
    - '--storage.tsdb.path=/prometheus'
```

#### 3.2 Grafana配置

```yaml
grafana:
  image: grafana/grafana:latest
  ports:
    - "3000:3000"
  volumes:
    - grafana-data:/var/lib/grafana
  environment:
    - GF_SECURITY_ADMIN_PASSWORD=admin
```

### 4. 日志管理

#### 4.1 集中式日志收集

```yaml
services:
  jobmanager:
    logging:
      driver: "json-file"
      options:
        max-size: "100m"
        max-file: "10"
        labels: "service=jobmanager"
```

#### 4.2 ELK Stack集成

```yaml
elasticsearch:
  image: elasticsearch:8.11.0
  environment:
    - discovery.type=single-node

logstash:
  image: logstash:8.11.0
  volumes:
    - ./logstash.conf:/usr/share/logstash/pipeline/logstash.conf

kibana:
  image: kibana:8.11.0
  ports:
    - "5601:5601"
```


### 5. 性能优化

#### 5.1 资源调优

```yaml
services:
  jobmanager:
    deploy:
      resources:
        limits:
          cpus: '4.0'
          memory: 8G
        reservations:
          cpus: '2.0'
          memory: 4G
    environment:
      - JOB_MANAGER_HEAP_SIZE=4096m
```

#### 5.2 网络优化

```yaml
networks:
  flink-network:
    driver: bridge
    driver_opts:
      com.docker.network.driver.mtu: 9000  # Jumbo frames
```

#### 5.3 存储优化

```yaml
volumes:
  flink-checkpoints:
    driver: local
    driver_opts:
      type: none
      o: bind
      device: /mnt/fast-ssd/checkpoints  # 使用SSD
```

## 最佳实践

### 1. 配置管理

- ✅ 使用 `.env` 文件管理环境变量
- ✅ 不要在镜像中硬编码敏感信息
- ✅ 使用配置管理工具（如Consul、etcd）
- ✅ 定期审查和更新配置

### 2. 安全性

- ✅ 使用非root用户运行容器
- ✅ 限制容器权限（capabilities）
- ✅ 使用私有镜像仓库
- ✅ 定期更新基础镜像
- ✅ 扫描镜像漏洞

### 3. 可靠性

- ✅ 配置健康检查
- ✅ 设置合理的重启策略
- ✅ 使用数据卷持久化数据
- ✅ 定期备份关键数据
- ✅ 测试故障恢复流程

### 4. 可观测性

- ✅ 配置日志收集
- ✅ 暴露Prometheus指标
- ✅ 设置告警规则
- ✅ 使用分布式追踪
- ✅ 监控资源使用

### 5. 扩展性

- ✅ 使用 `--scale` 动态扩展
- ✅ 配置合理的资源限制
- ✅ 使用负载均衡
- ✅ 支持水平扩展
- ✅ 监控性能指标


## 验证清单

- [x] 定义了所有服务（JobManager、TaskManager、CDC Collector）
- [x] 配置了网络（flink-network）
- [x] 配置了存储卷（checkpoints、savepoints、logs、data）
- [x] 配置了环境变量（通过.env文件）
- [x] 配置了服务依赖关系（depends_on with condition）
- [x] 配置了健康检查（所有服务）
- [x] 配置了自动重启策略（unless-stopped）
- [x] 配置了资源限制（CPU和内存）
- [x] 支持动态扩展（TaskManager scaling）
- [x] 支持高可用模式（ZooKeeper profile）
- [x] 提供了环境变量示例文件（.env.example）
- [x] 提供了完整的使用文档
- [x] 提供了故障排查指南
- [x] 提供了生产环境部署建议

## 与其他任务的关系

### 依赖的任务

- **Task 14.1**: Dockerfile定义
  - 使用JobManager、TaskManager、CDC Collector的Dockerfile
  
- **Task 14.2**: 启动脚本
  - 使用entrypoint.sh脚本
  - 支持环境变量配置
  
- **Task 14.3**: 健康检查配置
  - 使用健康检查配置
  - 使用自动重启策略

### 支持的任务

- **Task 14.5**: 容器化测试
  - 提供完整的测试环境
  
- **Task 15.1**: 主程序集成
  - 提供运行环境

## 总结

本任务实现了完整的Docker Compose配置：

1. **服务定义**
   - JobManager: Flink主节点
   - TaskManager: Flink工作节点（支持扩展）
   - CDC Collector: 数据采集组件
   - ZooKeeper: 高可用支持（可选）

2. **网络配置**
   - 独立的bridge网络
   - 服务间通过服务名通信
   - 支持服务发现

3. **存储配置**
   - 持久化Checkpoint和Savepoint
   - 日志和数据存储
   - 支持备份和恢复

4. **环境变量配置**
   - 完整的.env.example文件
   - 支持环境变量覆盖
   - 敏感信息外部化

5. **服务依赖**
   - 基于健康检查的依赖
   - 确保启动顺序
   - 避免连接失败

6. **高可用支持**
   - ZooKeeper集成
   - 支持JobManager HA
   - 使用profile按需启用

7. **动态扩展**
   - 支持TaskManager扩展
   - 使用--scale参数
   - 自动服务发现

8. **文档完善**
   - 详细的使用指南
   - 故障排查步骤
   - 生产环境建议
   - 最佳实践

系统现在具备了完整的Docker Compose部署能力，满足需求8.7的所有要求。
