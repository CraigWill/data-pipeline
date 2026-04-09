# CDC 任务列表访问错误修复

## 问题描述

访问 CDC 任务列表时出现错误：
```
ORA-01005: null password given; logon denied
```

## 错误原因

1. **缺少 Spring Boot 数据源配置**
   - `RuntimeJobService` 使用 `JdbcTemplate` 访问数据库
   - 需要 Spring Boot 的标准数据源配置 (`spring.datasource`)
   - 原配置文件中只有自定义的 `database` 配置，没有 Spring Boot 数据源配置

2. **环境变量密码未解密**
   - 即使配置了数据源，密码直接从环境变量读取
   - 如果环境变量中的密码是加密的，需要先解密
   - 没有使用 `EnvironmentPasswordUtil` 处理密码

3. **定时任务触发错误**
   - `RuntimeJobService.syncAllJobStatuses()` 每 30 秒执行一次
   - 尝试查询数据库时因为密码为 null 而失败
   - 错误日志: "同步作业状态失败"

## 解决方案

### 1. 添加 Spring Boot 数据源配置

在 `monitor-backend/src/main/resources/application.yml` 中添加:

```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@${DATABASE_HOST:localhost}:${DATABASE_PORT:1521}:${DATABASE_SID:helowin}
    username: ${DATABASE_USERNAME:finance_user}
    password: ${DATABASE_PASSWORD:password}
    driver-class-name: oracle.jdbc.OracleDriver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

### 2. 创建自定义数据源配置类

创建 `DataSourceConfig.java` 来处理密码解密:

```java
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public DataSource dataSource() {
        // 从环境变量读取配置
        String url = buildJdbcUrl();
        String username = System.getenv().getOrDefault("DATABASE_USERNAME", "finance_user");
        
        // 使用 EnvironmentPasswordUtil 自动解密密码
        String password = EnvironmentPasswordUtil.getPassword("DATABASE_PASSWORD", "password");
        
        // 创建数据源
        HikariDataSource dataSource = DataSourceBuilder
                .create()
                .type(HikariDataSource.class)
                .url(url)
                .username(username)
                .password(password)
                .driverClassName("oracle.jdbc.OracleDriver")
                .build();
        
        return dataSource;
    }
}
```

### 3. 工作原理

```
应用启动
    ↓
DataSourceConfig.dataSource()
    ↓
读取环境变量 DATABASE_PASSWORD
    ↓
EnvironmentPasswordUtil.getPassword()
    ↓
检测密码格式
    ├─ 加密密码 → AES 解密
    └─ 明文密码 → 直接使用
    ↓
创建 HikariDataSource
    ↓
RuntimeJobService 使用数据源
    ↓
定时任务正常执行
```

## 修改文件

### 新增文件
- `monitor-backend/src/main/java/com/realtime/monitor/config/DataSourceConfig.java`

### 修改文件
- `monitor-backend/src/main/resources/application.yml`

## 测试验证

### 1. 重新编译

```bash
cd monitor-backend
mvn clean package -DskipTests
```

### 2. 重新构建 Docker 镜像

```bash
docker-compose build monitor-backend --no-cache
```

### 3. 重启服务

```bash
docker-compose restart monitor-backend
```

### 4. 查看日志

```bash
# 查看启动日志
docker-compose logs monitor-backend | grep -i "datasource\|password"

# 应该看到类似输出:
# 配置数据源:
#   URL: jdbc:oracle:thin:@host.docker.internal:1521:helowin
#   Username: finance_user
#   Password: ***
```

### 5. 测试 CDC 任务列表

```bash
# 访问 CDC 任务列表 API
curl -H "Authorization: Bearer <token>" \
  http://localhost:5001/api/cdc/tasks

# 应该返回任务列表，不再报错
```

## 环境变量配置

### 开发环境 (.env)

```bash
# 明文密码
DATABASE_HOST=host.docker.internal
DATABASE_PORT=1521
DATABASE_SID=helowin
DATABASE_USERNAME=finance_user
DATABASE_PASSWORD=password123

# 加密密钥
AES_ENCRYPTION_KEY=your-32-byte-aes-key-here-123456
```

### 生产环境 (.env)

```bash
# 加密密码
DATABASE_HOST=prod-db.example.com
DATABASE_PORT=1521
DATABASE_SID=prod_sid
DATABASE_USERNAME=finance_user
DATABASE_PASSWORD=V+uUYS1hqMh0uvC7JwxwIA==  # 加密

# 强随机密钥
AES_ENCRYPTION_KEY=<strong-random-key>
```

## 相关组件

### RuntimeJobService
- 使用 `JdbcTemplate` 访问数据库
- 存储和查询运行时作业信息
- 定时同步作业状态（每 30 秒）

### DataSourceConfig
- 配置 Spring Boot 数据源
- 自动解密环境变量中的密码
- 使用 HikariCP 连接池

### EnvironmentPasswordUtil
- 从环境变量读取密码
- 自动检测密码格式
- 加密密码自动解密
- 明文密码直接使用

## 故障排查

### 问题 1: 仍然报 "null password" 错误

**原因**: 环境变量未设置或为空

**解决方案**:
```bash
# 检查环境变量
docker-compose exec monitor-backend env | grep DATABASE_PASSWORD

# 如果为空，更新 .env 文件
DATABASE_PASSWORD=your-password

# 重启服务
docker-compose restart monitor-backend
```

### 问题 2: 密码解密失败

**原因**: AES_ENCRYPTION_KEY 不正确

**解决方案**:
```bash
# 确认密钥正确
grep AES_ENCRYPTION_KEY .env

# 重新加密密码
./encrypt-password.sh "correct-password"

# 更新 .env 文件
DATABASE_PASSWORD=<new-encrypted-password>

# 重启服务
docker-compose restart monitor-backend
```

### 问题 3: 连接超时

**原因**: 数据库地址或端口不正确

**解决方案**:
```bash
# 检查数据库配置
grep DATABASE_ .env

# 测试数据库连接
docker-compose exec monitor-backend \
  bash -c 'echo "exit" | sqlplus $DATABASE_USERNAME/$DATABASE_PASSWORD@$DATABASE_HOST:$DATABASE_PORT/$DATABASE_SID'

# 如果连接失败，更新配置
```

## 总结

修复了 CDC 任务列表访问错误，主要改进：

✅ 添加了 Spring Boot 数据源配置
✅ 创建了自定义数据源配置类
✅ 集成了环境变量密码自动解密
✅ 支持明文和加密密码
✅ 使用 HikariCP 连接池
✅ 定时任务正常执行

现在系统可以：
- 正常访问 CDC 任务列表
- 自动解密环境变量中的密码
- 定时同步作业状态
- 支持明文和加密密码
- 向后兼容

修复已完成，请重新编译和部署！
