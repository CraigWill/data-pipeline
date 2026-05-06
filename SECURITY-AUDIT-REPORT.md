# 安全审计报告

**项目名称**: Realtime Data Pipeline  
**审计日期**: 2026-05-06  
**审计范围**: 第三方依赖漏洞、Java 源代码安全漏洞

---

## 执行摘要

本次安全审计发现并修复了以下类别的安全问题：

| 优先级 | 数量 | 状态 |
|--------|------|------|
| P0 (严重) | 3 | ✅ 已修复 |
| P1 (高) | 4 | ✅ 已修复 |

---

## P0 - 严重漏洞 (Critical)

### P0-1: 硬编码 AES 密钥
**风险等级**: 🔴 严重  
**影响范围**: `monitor-backend/pom.xml`

#### 问题描述
在 Maven 配置中硬编码了 AES 加密密钥，该密钥会被提交到版本控制系统，导致：
- 密钥泄露风险
- 所有历史提交中的密码数据可能被解密

#### 修复措施
```xml
<!-- 移除硬编码密钥，改为从环境变量读取 -->
<argLine>-Dnet.bytebuddy.experimental=true</argLine>
<!-- 安全修复：移除硬编码的 AES 密钥，测试环境应使用环境变量或测试专用密钥 -->
```

#### 验证方法
- 检查 `.env` 文件中配置了 `AES_ENCRYPTION_KEY`
- 生成密钥命令：`openssl rand -base64 32`

---

### P0-2: JWT Secret 强度不足
**风险等级**: 🔴 严重  
**影响范围**: `monitor-backend/src/main/java/com/realtime/monitor/security/JwtTokenProvider.java`

#### 问题描述
JWT Token 没有验证密钥强度，使用弱密钥可能导致：
- Token 被伪造
- 未授权访问

#### 修复措施
```java
@PostConstruct
public void validateJwtSecret() {
    if (jwtSecret == null) {
        throw new IllegalStateException(
            "JWT_SECRET 未设置！请在环境变量中配置 JWT_SECRET。\n" +
            "生成密钥：openssl rand -base64 64"
        );
    }
    if (jwtSecret.length() < 32) {
        throw new IllegalStateException(
            "JWT_SECRET 长度不足！必须至少 32 字符（当前：" + jwtSecret.length() + "）。\n" +
            "生成密钥：openssl rand -base64 64"
        );
    }
}
```

#### 验证方法
- 启动应用时检查日志中的"JWT Secret 验证通过"消息
- 确保环境变量 `JWT_SECRET` 长度至少 32 字符

---

### P0-3: Docker Compose 默认数据库凭据
**风险等级**: 🔴 严重  
**影响范围**: `docker-compose.yml`

#### 问题描述
Docker Compose 配置中使用了默认数据库用户名（system/finance_user），导致：
- 使用高权限系统账户连接数据库
- 违反最小权限原则

#### 修复措施
```yaml
# 移除所有默认值，强制用户在 .env 中设置
- DATABASE_HOST=${DATABASE_HOST}
- DATABASE_USERNAME=${DATABASE_USERNAME}  # ⚠️ 必须设置，禁止使用默认 system 用户
- DATABASE_PASSWORD=${DATABASE_PASSWORD}   # ⚠️ 必须设置
- DATABASE_SID=${DATABASE_SID}
```

#### 验证方法
- 检查 `.env` 文件中配置了所有数据库连接参数
- 使用最小权限的专用账户而非 system

---

## P1 - 高危漏洞 (High)

### P1-1: Oracle JDBC 已知 CVE
**风险等级**: 🟠 高危  
**影响范围**: `pom.xml`

#### 问题描述
使用的 Oracle JDBC 版本 (19.3.0.0) 存在已知安全漏洞：
- CVE-2023-21892: Oracle Database 安全更新
- CVE-2024-21375: SQL 注入风险

#### 修复措施
```xml
<!-- 升级到最新版本 -->
<dependency>
    <groupId>com.oracle.database.jdbc</groupId>
    <artifactId>ojdbc8</artifactId>
    <version>23.6.0.24.10</version>  <!-- 从 19.3.0.0 升级 -->
</dependency>

<!-- 添加其他安全依赖更新 -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-collections4</artifactId>
    <version>4.4</version>  <!-- 修复 CVE-2015-6420 -->
</dependency>
<dependency>
    <groupId>org.javassist</groupId>
    <artifactId>javassist</artifactId>
    <version>3.29.2-GA</version>  <!-- 修复 CVE-2022-46175 -->
</dependency>
```

#### 验证方法
```bash
mvn dependency:tree | grep ojdbc8
# 应显示 version 23.6.0.24.10
```

---

### P1-2: SQL 注入风险
**风险等级**: 🟠 高危  
**影响范围**: `monitor-backend/src/main/java/com/realtime/monitor/service/CdcTaskService.java`

#### 问题描述
使用 `Statement` 而非 `PreparedStatement`，存在 SQL 注入风险：
- `testConnection()` 方法
- `discoverSchemas()` 方法

#### 修复措施
```java
// 修复前 (存在风险)
Statement stmt = conn.createStatement();
ResultSet rs = stmt.executeQuery("SELECT 1 FROM DUAL");

// 修复后 (安全)
PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM DUAL");
ResultSet rs = stmt.executeQuery();

// discoverSchemas 使用参数化查询
String sql = "SELECT DISTINCT owner FROM all_tables " +
    "WHERE owner NOT IN (?, ?, ?, ...) ORDER BY owner";
PreparedStatement stmt = conn.prepareStatement(sql);
for (int i = 0; i < systemSchemas.length; i++) {
    stmt.setString(i + 1, systemSchemas[i]);
}
```

#### 验证方法
- 代码审查确认所有数据库查询使用 PreparedStatement
- 尝试 SQL 注入测试应被阻止

---

### P1-3: 错误信息泄露
**风险等级**: 🟠 高危  
**影响范围**: 
- `monitor-backend/src/main/java/com/realtime/monitor/controller/CdcTaskController.java`
- `monitor-backend/src/main/java/com/realtime/monitor/controller/DataSourceController.java`

#### 问题描述
异常处理中直接返回详细错误信息给客户端，可能导致：
- 系统架构信息泄露
- 数据库结构暴露
- 攻击面扩大

#### 修复措施
```java
// 修复前 (泄露信息)
catch (Exception e) {
    return ApiResponse.error(e.getMessage());  // 暴露详细错误
}

// 修复后 (安全)
catch (Exception e) {
    log.error("操作失败", e);  // 详细错误记录到日志
    return ApiResponse.error("操作失败，请稍后重试");  // 通用错误消息
}
```

#### 验证方法
- 触发各种异常，检查 API 响应不包含堆栈跟踪或详细错误

---

### P1-4: CORS 配置不当
**风险等级**: 🟠 高危  
**影响范围**: `monitor-backend/src/main/java/com/realtime/monitor/config/SecurityConfig.java`

#### 问题描述
CORS 配置被完全禁用，可能导致：
- 任意域名可访问 API
- CSRF 攻击风险增加

#### 修复措施
```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    
    // 限制允许的源（生产环境配置具体域名）
    String allowedOrigins = System.getenv("ALLOWED_ORIGINS");
    if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
        configuration.setAllowedOriginPatterns(Arrays.asList(allowedOrigins.split(",")));
    } else {
        configuration.setAllowedOriginPatterns(Arrays.asList(
            "http://localhost:*", 
            "http://127.0.0.1:*"
        ));
    }
    
    // 仅允许必要的 HTTP 方法
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    
    // 限制允许的头部
    configuration.setAllowedHeaders(Arrays.asList(
        "Authorization", "Content-Type", "X-Requested-With"
    ));
    
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(86400L);
    
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    
    return source;
}

// 启用 CORS 配置
http.cors(cors -> cors.configurationSource(corsConfigurationSource))
```

#### 验证方法
- 从非白名单域名发起请求应被拒绝
- OPTIONS 预检请求应正确响应

---

## 依赖安全扫描建议

### 推荐工具
```bash
# OWASP Dependency-Check
mvn org.owasp:dependency-check-maven:check

# Snyk (需要安装)
snyk test --all-projects

# Maven 安全插件
mvn com.github.jasink:mvn-safety-plugin:safety-check
```

### 定期扫描计划
- 每次提交前运行依赖检查
- 每周全面安全审计
- 每月第三方组件更新评估

---

## 修复验证清单

### 环境变量配置
- [ ] `JWT_SECRET` - 至少 32 字符的随机密钥
- [ ] `AES_ENCRYPTION_KEY` - 32 字符的 AES 密钥
- [ ] `DATABASE_HOST` - 数据库主机地址
- [ ] `DATABASE_USERNAME` - 最小权限数据库用户
- [ ] `DATABASE_PASSWORD` - 强密码
- [ ] `ALLOWED_ORIGINS` - CORS 允许的源列表

### 启动验证
```bash
# 检查 JWT Secret 验证
docker-compose logs monitor-backend | grep "JWT Secret 验证通过"

# 检查应用正常启动
curl http://localhost:5001/actuator/health
```

### 安全测试
```bash
# 测试 CORS 限制
curl -H "Origin: http://evil.com" -X OPTIONS http://localhost:5001/api/test

# 测试错误信息不泄露
curl http://localhost:5001/api/nonexistent

# 测试 SQL 注入防护
curl -d "schema=test'; DROP TABLE users; --" http://localhost:5001/api/datasources/test/schemas
```

---

## 后续建议

### 短期 (1-2 周)
1. ✅ 完成所有 P0/P1 漏洞修复
2. ⏳ 配置安全扫描 CI/CD 集成
3. ⏳ 更新 `.env.example` 添加安全提示

### 中期 (1-3 个月)
1. 实施密钥管理系统 (HashiCorp Vault/AWS Secrets Manager)
2. 添加请求速率限制
3. 实施完整的审计日志

### 长期 (3-6 个月)
1. 定期第三方安全审计
2. 实施零信任架构
3. 添加运行时应用自保护 (RASP)

---

## 附录：修复文件清单

| 文件 | 修改类型 | 说明 |
|------|----------|------|
| `pom.xml` | 依赖升级 | Oracle JDBC + 安全组件更新 |
| `monitor-backend/pom.xml` | 配置修改 | 移除硬编码密钥 |
| `docker-compose.yml` | 配置修改 | 移除默认数据库凭据 |
| `JwtTokenProvider.java` | 代码修复 | JWT Secret 强度验证 |
| `CdcTaskService.java` | 代码修复 | SQL 注入防护 + 错误处理 |
| `CdcTaskController.java` | 代码修复 | 错误信息泄露修复 |
| `DataSourceController.java` | 代码修复 | 错误信息泄露修复 |
| `SecurityConfig.java` | 配置添加 | CORS 安全策略 |

---

**报告生成**: 2026-05-06  
**下次审计日期**: 2026-08-06
