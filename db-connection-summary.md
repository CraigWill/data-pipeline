# Oracle 数据库连接测试总结

## 测试时间
2026-02-12

## 数据库状态

### ✅ Oracle 容器运行正常
- **容器名称**: oracle11g
- **镜像**: akaiot/oracle_11g:latest
- **状态**: Up 16 hours
- **端口映射**: 0.0.0.0:1521->1521/tcp

### ✅ 端口连接正常
- **主机**: localhost
- **端口**: 1521
- **状态**: 可访问 ✅

### ✅ Oracle 进程运行正常
- **监听器**: LISTENER (运行中)
- **数据库实例**: helowin (运行中)
- **PMON 进程**: ora_pmon_helowin (运行中)

## 配置信息（来自 .env）

```bash
DATABASE_HOST=localhost
DATABASE_PORT=1521
DATABASE_USERNAME=finance_user
DATABASE_PASSWORD=password
DATABASE_SCHEMA=helowin
DATABASE_TABLES=trans_info
```

## 连接字符串

### JDBC URL 格式
```
jdbc:oracle:thin:@localhost:1521:helowin
```

### SQLPlus 格式
```
sqlplus finance_user/password@localhost:1521/helowin
```

## 测试结果

| 测试项 | 状态 | 说明 |
|--------|------|------|
| 容器运行 | ✅ | Oracle 11g 容器正常运行 |
| 端口连接 | ✅ | localhost:1521 可访问 |
| 监听器 | ✅ | TNS Listener 运行中 |
| 数据库实例 | ✅ | helowin 实例运行中 |
| JDBC 驱动 | ⚠️ | 需要添加到 pom.xml |

## 下一步操作

### 选项 1: 使用模拟模式（推荐用于测试）
CDC 应用程序会自动检测数据库连接，如果无法连接会切换到模拟模式：

```bash
./start-cdc.sh
```

模拟模式会：
- 每 10 秒生成一条测试数据
- 写入 CSV 文件到 `./output/cdc`
- 用于演示和测试流程

### 选项 2: 添加 Oracle JDBC 驱动（用于真实连接）

1. 在 `pom.xml` 中添加 Oracle JDBC 依赖：

```xml
<dependency>
    <groupId>com.oracle.database.jdbc</groupId>
    <artifactId>ojdbc8</artifactId>
    <version>21.9.0.0</version>
</dependency>
```

2. 重新编译：

```bash
mvn clean package -DskipTests
```

3. 运行 CDC 应用：

```bash
./start-cdc.sh
```

### 选项 3: 验证数据库表和权限

进入容器检查表是否存在：

```bash
docker exec -it oracle11g bash
su - oracle
sqlplus finance_user/password@localhost:1521/helowin

SQL> SELECT table_name FROM user_tables;
SQL> SELECT COUNT(*) FROM trans_info;
```

## 建议

1. **立即可用**: 直接运行 `./start-cdc.sh`，应用会使用模拟模式
2. **生产环境**: 添加 Oracle JDBC 驱动后连接真实数据库
3. **验证数据**: 检查 `./output/cdc/` 目录下的 CSV 文件

## 故障排查

如果遇到问题：

1. **容器未运行**:
   ```bash
   docker start oracle11g
   ```

2. **端口被占用**:
   ```bash
   lsof -i :1521
   ```

3. **查看容器日志**:
   ```bash
   docker logs oracle11g
   ```

4. **重启容器**:
   ```bash
   docker restart oracle11g
   ```
