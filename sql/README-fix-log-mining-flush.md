# 修复 log_mining_flush 表位置

## 问题描述

Flink CDC 3.x 默认会在业务 schema（`finance_user`）中创建 `log_mining_flush` 表，但这不是最佳实践。该表应该创建在专门的管理 schema（`flink_user`）中。

## 解决方案

### 方法 1: 使用 SQL 脚本（推荐）

1. 连接到 Oracle 数据库（使用 SYSTEM 用户）:
   ```bash
   sqlplus system/password@localhost:1521/helowin
   ```

2. 执行 SQL 脚本:
   ```bash
   @sql/setup-flink-user-schema.sql
   ```

   或者手动执行以下 SQL:
   ```sql
   -- 创建 flink_user 用户
   CREATE USER flink_user IDENTIFIED BY flink_password;
   GRANT CONNECT, RESOURCE, CREATE SESSION, CREATE TABLE TO flink_user;
   GRANT UNLIMITED TABLESPACE TO flink_user;
   
   -- 删除 finance_user 中的旧表
   DROP TABLE finance_user.log_mining_flush;
   
   -- 切换到 flink_user 创建表
   CONNECT flink_user/flink_password@helowin;
   CREATE TABLE flink_user.log_mining_flush (
       scn NUMBER(19,0) NOT NULL,
       PRIMARY KEY (scn)
   );
   
   -- 切换回 SYSTEM 授予权限
   CONNECT system/password@helowin;
   GRANT SELECT, INSERT, UPDATE, DELETE ON flink_user.log_mining_flush TO finance_user;
   
   -- 验证
   SELECT owner, table_name FROM dba_tables WHERE table_name = 'LOG_MINING_FLUSH';
   ```

### 方法 2: 使用提供的脚本

```bash
# 如果你的系统安装了 cx_Oracle
python3 sql/fix-log-mining-flush.py

# 或者使用 shell 脚本（需要 sqlplus）
./sql/fix-log-mining-flush.sh
```

### 方法 3: 在 Oracle 容器中手动执行

```bash
# 进入 Oracle 容器
docker exec -it oracle11g bash

# 设置环境变量（根据你的 Oracle 安装路径调整）
export ORACLE_HOME=/path/to/oracle/home
export PATH=$ORACLE_HOME/bin:$PATH
export ORACLE_SID=helowin

# 连接数据库
sqlplus system/password

# 执行上面的 SQL 命令
```

## 代码更改

已更新 `src/main/java/com/realtime/pipeline/CdcJobMain.java`，添加了以下配置:

```java
debeziumProps.setProperty("log.mining.flush.table.name", "flink_user.log_mining_flush");
```

## 重新部署

修复表位置后，需要重新部署应用:

```bash
# 1. 重新构建项目
mvn clean package -DskipTests

# 2. 重新构建 Docker 镜像
docker-compose build jobmanager jobmanager-standby taskmanager

# 3. 重启 Flink 集群
docker-compose restart jobmanager jobmanager-standby taskmanager

# 4. 提交新的 CDC 任务（旧任务会自动使用新配置）
```

## 验证

检查表是否在正确的位置:

```sql
SELECT owner, table_name, tablespace_name 
FROM dba_tables 
WHERE table_name = 'LOG_MINING_FLUSH';
```

应该显示:
```
OWNER        TABLE_NAME          TABLESPACE_NAME
FLINK_USER   LOG_MINING_FLUSH    USERS
```

检查权限:

```sql
SELECT grantee, privilege 
FROM dba_tab_privs 
WHERE table_name = 'LOG_MINING_FLUSH';
```

应该显示 `finance_user` 有 `SELECT`, `INSERT`, `UPDATE`, `DELETE` 权限。

## 注意事项

1. 这个表用于跟踪 Flink CDC 已处理的 SCN（System Change Number）
2. 表必须在 Flink CDC 连接用户有权限访问的 schema 中
3. 推荐使用专门的 flink_user schema 而不是业务数据 schema
4. 表结构由 Flink CDC 自动管理，不要手动修改数据
5. flink_user 是专门用于 Flink CDC 管理表的用户
