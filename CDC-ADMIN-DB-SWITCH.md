# CDC Admin 数据库切换指南

CDC Admin 库存储系统元数据（`cdc_datasources`、`cdc_tasks`、`runtime_jobs`、`app_config`、`cdc_files`），支持两种数据库后端：

| 模式 | 数据库 | 端口 | 驱动 |
|------|--------|------|------|
| **Oracle** | Oracle 11g+ | 1522 | `ojdbc8` |
| **OceanBase** | OceanBase CE（MySQL 兼容） | 2881 | `mysql-connector-j` |

---

## 一、切换到 OceanBase（当前默认）

### 1. 初始化数据库表

```bash
# 连接 OceanBase 并执行建表脚本
docker exec -i cdcdb sh -c \
  "mysql -h 127.0.0.1 -P 2881 -u root@test -p'<密码>' cdcdb" \
  < sql/ddl-oceanbase-cdc-admin.sql
```

或通过 MySQL 客户端：

```bash
mysql -h 172.17.0.1 -P 2881 -u root@test -p cdcdb \
  < sql/ddl-oceanbase-cdc-admin.sql
```

### 2. 修改 `.env`

```dotenv
CDC_ADMIN_TYPE=MYSQL
CDC_ADMIN_HOST=cdcdb          # Docker 容器名，或 172.17.0.1（宿主机 IP）
CDC_ADMIN_PORT=2881
CDC_ADMIN_DATABASE=cdcdb      # OceanBase 数据库名
CDC_ADMIN_USERNAME=root@test  # 格式：用户名@租户名
CDC_ADMIN_PASSWORD=<加密后的密码>
```

> 密码加密：`./encrypt-password.sh "明文密码"`

### 3. 确保 cdcdb 容器在 flink-network 中

```bash
# 检查网络
docker inspect cdcdb --format '{{json .NetworkSettings.Networks}}' | python3 -m json.tool

# 如果不在 flink-network，加入
docker network connect flink-network cdcdb
```

### 4. 重启 monitor-backend

```bash
docker compose restart monitor-backend
# 或
docker compose up -d monitor-backend
```

### 5. 验证

```bash
curl http://localhost:5001/actuator/health
# 期望: {"status":"UP","components":{"db":{"status":"UP",...}}}
```

---

## 二、切换到 Oracle

### 1. 初始化数据库表

```bash
# 以 system 用户执行（需先创建 cdc_admin 用户）
docker exec oracle-cdcdb bash -c "
  export ORACLE_HOME=/home/oracle/app/oracle/product/11.2.0/dbhome_2
  export PATH=\$ORACLE_HOME/bin:\$PATH
  export ORACLE_SID=helowin
  sqlplus system/helowin@helowin @/tmp/ddl-oracle-cdc-admin.sql
"
```

或先将脚本复制进容器：

```bash
docker cp sql/ddl-oracle-cdc-admin.sql oracle-cdcdb:/tmp/
docker exec oracle-cdcdb bash -c "
  export ORACLE_HOME=/home/oracle/app/oracle/product/11.2.0/dbhome_2
  export PATH=\$ORACLE_HOME/bin:\$PATH
  sqlplus system/helowin@helowin @/tmp/ddl-oracle-cdc-admin.sql
"
```

### 2. 修改 `.env`

```dotenv
CDC_ADMIN_TYPE=ORACLE
CDC_ADMIN_HOST=oracle-cdcdb   # Docker 容器名，或 172.17.0.1
CDC_ADMIN_PORT=1522
CDC_ADMIN_SID=helowin
CDC_ADMIN_USERNAME=cdc_admin
CDC_ADMIN_PASSWORD=<加密后的密码>
# 注意：ORACLE 模式不使用 CDC_ADMIN_DATABASE，使用 CDC_ADMIN_SID
```

### 3. 确保 oracle-cdcdb 容器在 flink-network 中

```bash
docker network connect flink-network oracle-cdcdb
```

### 4. 重启 monitor-backend

```bash
docker compose restart monitor-backend
```

### 5. 验证

```bash
curl http://localhost:5001/actuator/health
```

---

## 三、Flink JobManager / TaskManager JDBC 驱动

Flink 镜像（`docker/jobmanager/Dockerfile`、`docker/taskmanager/Dockerfile`）已内置三个 JDBC 驱动：

| 文件 | 用途 | Maven 坐标 |
|------|------|-----------|
| `/opt/flink/lib/ojdbc8.jar` | Oracle 业务库（CDC 数据源） | `com.oracle.database.jdbc:ojdbc8:19.3.0.0` |
| `/opt/flink/lib/oceanbase-client.jar` | OceanBase 原生协议 | `com.oceanbase:oceanbase-client:2.4.18` |
| `/opt/flink/lib/mysql-connector-j.jar` | OceanBase MySQL 兼容 / MySQL | `com.mysql:mysql-connector-j:9.5.0` |

**重新构建 Flink 镜像**（修改 Dockerfile 后需执行）：

```bash
# 先构建 flink-jobs JAR
mvn -f flink-jobs/pom.xml package -DskipTests -q

# 构建 JobManager 镜像
docker build -t flink-jobmanager:latest \
  -f docker/jobmanager/Dockerfile .

# 构建 TaskManager 镜像
docker build -t flink-taskmanager:latest \
  -f docker/taskmanager/Dockerfile .

# 重启 Flink 集群
docker compose restart jobmanager taskmanager
```

---

## 四、docker-compose.yml 环境变量说明

`monitor-backend` 服务读取以下环境变量（均来自 `.env`）：

| 变量 | 说明 | Oracle 示例 | OceanBase 示例 |
|------|------|-------------|----------------|
| `CDC_ADMIN_TYPE` | 数据库类型 | `ORACLE` | `MYSQL` |
| `CDC_ADMIN_HOST` | 主机名或 IP | `oracle-cdcdb` | `cdcdb` |
| `CDC_ADMIN_PORT` | 端口 | `1522` | `2881` |
| `CDC_ADMIN_USERNAME` | 用户名 | `cdc_admin` | `root@test` |
| `CDC_ADMIN_PASSWORD` | 密码（AES 加密） | `46RJiI5Y...` | `CO9AKFe...` |
| `CDC_ADMIN_SID` | Oracle SID（仅 ORACLE 模式） | `helowin` | — |
| `CDC_ADMIN_DATABASE` | 数据库名（仅 MYSQL/POSTGRES 模式） | — | `cdcdb` |

---

## 五、K8s 环境切换

修改 `k8s/flink-secrets.yaml`：

**OceanBase：**
```yaml
stringData:
  cdc-admin-type: "MYSQL"
  cdc-admin-host: "172.17.0.1"
  cdc-admin-port: "2881"
  cdc-admin-username: "root@test"
  cdc-admin-password: "<加密密码>"
  cdc-admin-database: "cdcdb"
```

**Oracle：**
```yaml
stringData:
  cdc-admin-type: "ORACLE"
  cdc-admin-host: "172.17.0.1"
  cdc-admin-port: "1522"
  cdc-admin-username: "cdc_admin"
  cdc-admin-password: "<加密密码>"
  cdc-admin-sid: "helowin"
```

应用并重启：
```bash
kubectl apply -f k8s/flink-secrets.yaml
kubectl rollout restart deployment/monitor-backend -n flink
kubectl rollout status deployment/monitor-backend -n flink
```

---

## 六、DDL 文件位置

| 文件 | 说明 |
|------|------|
| `sql/ddl-oracle-cdc-admin.sql` | Oracle 建表脚本（`VARCHAR2`、`NUMBER`、`DATE DEFAULT SYSDATE`） |
| `sql/ddl-oceanbase-cdc-admin.sql` | OceanBase/MySQL 建表脚本（`VARCHAR`、`INT`、`TIMESTAMP DEFAULT CURRENT_TIMESTAMP`） |
