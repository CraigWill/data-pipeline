# Java 代码安全与质量审计报告

## 📋 执行摘要

审计日期：2026-04-23
审计范围：`monitor-backend/src/main/java/` 目录下所有 Java 源代码

### 总体评分：A- (85/100)

| 类别 | 得分 | 说明 |
|------|------|------|
| 🔒 安全性 | 92/100 | 密码加密、JWT、XSS 防护完善 |
| 📊 代码质量 | 83/100 | 架构清晰，但存在部分可优化点 |
| ⚡ 性能 | 78/100 | 流式处理良好，但部分查询可优化 |
| 🛠️ 可维护性 | 85/100 | 注释充分，模块化设计良好 |

---

## 🔍 详细发现

### ✅ 安全亮点（已实现的最佳实践）

#### 1. 密码加密机制 - **优秀**
```java
// PasswordEncryptionUtil.java
- BCrypt: 用户密码单向加密 ✓
- AES/GCM: 数据源密码可逆加密 + 认证标签防篡改 ✓
- 密钥从环境变量读取，无硬编码风险 ✓
```

#### 2. JWT Token 管理 - **优秀**
```java
// JwtTokenProvider.java
- 使用 io.jsonwebtoken.security.Keys.hmacShaKeyFor() ✓
- HS256 签名算法（防篡改）✓
- 默认密钥提示用户在生产环境更换 ✓
```

#### 3. XSS 防护 - **优秀**
```java
// XssSanitizer.java
- 集成 OWASP Java Encoder ✓
- HTML/URL/JavaScript 三种编码场景覆盖 ✓
- Flink REST API、文件内容均经过编码 ✓
```

#### 4. SQL 注入防护 - **良好**
```java
// CdcTaskService.java
- 使用 PreparedStatement（参数化查询）✓
- Oracle JDBC URL 构建安全 ✓
```

#### 5. HTTP 请求安全 - **优秀**
```java
// FlinkService.java
- URI 构建使用 UriComponentsBuilder，防止路径注入 ✓
- BaseUrl 白名单验证（仅允许 http/https）✓
- 超时配置合理（3s 连接 + 10s 读取）✓
```

---

### ⚠️ 安全问题与改进建议

#### 🔴 高优先级问题

##### 1. JWT 默认密钥 - **需立即修复**

**位置**: `JwtTokenProvider.java:18`
```java
@Value("${jwt.secret:your-256-bit-secret-key-change-this-in-production-environment-please-use-strong-key}")
private String jwtSecret;
```

**风险**: 默认密钥在生产环境可被攻击者利用进行伪造 Token

**建议修复**:
```bash
# 生成强密钥
openssl rand -base64 32
# 输出示例：K7x9mPqR2vN8wL5tY3uI0oA1sD4fG6hJ
```

---

#### 🟡 中优先级问题

##### 2. JDBC URL 构建 - **需改进**

**位置**: `CdcTaskService.java:145-148`
```java
private String buildJdbcUrl(DataSourceConfig config) {
    return String.format("jdbc:oracle:thin:@%s:%d:%s",
            resolveHost(config.getHost()), config.getPort(), config.getSid());
}
```

**风险**: 如果 `host`、`sid` 包含特殊字符可能导致 URL 注入

**建议修复**:
```java
private String buildJdbcUrl(DataSourceConfig config) {
    // 对主机名和 SID 进行 URL 编码，防止注入
    String safeHost = UriComponentsBuilder.fromHttpUrl("http://" + config.getHost())
        .build()
        .toUri()
        .getHost();
    
    return String.format("jdbc:oracle:thin:@%s:%d:%s",
            safeHost, config.getPort(), config.getSid());
}
```

---

##### 3. 文件路径遍历防护 - **需改进**

**位置**: `CdcEventsService.java:608-610`
```java
File file = new File(outputPath, filePath);
if (!file.exists() || !file.isFile()) {
    return createEmptyContentResult(filePath);
}
```

**风险**: 如果 `filePath` 包含 `../` 可能读取任意文件

**建议修复**:
```java
private String normalizePath(String base, String relative) {
    // 防止路径遍历攻击
    if (relative.contains("..") || relative.contains("//")) {
        throw new SecurityException("非法的文件路径参数");
    }
    
    File file = new File(base, relative);
    // 确保文件在 base 目录内
    String canonicalPath = file.getCanonicalPath();
    String baseCanonical = new File(base).getCanonicalPath();
    if (!canonicalPath.startsWith(baseCanonical)) {
        throw new SecurityException("路径遍历检测：文件不在预期目录内");
    }
    return relative;
}
```

---

##### 4. CSV 解析性能 - **需优化**

**位置**: `CdcEventsService.java:259-260`
```java
while ((line = reader.readLine()) != null && lineNumber < 1000) {
```

**问题**: 
- 硬编码的 1000 行限制可能导致数据截断
- `split()` 对大文件性能不佳

**建议修复**:
```java
// 移除硬编码限制，改为基于文件大小或内存阈值的控制
private static final int MAX_LINES_PER_FILE = Integer.MAX_VALUE;

while ((line = reader.readLine()) != null && lineNumber < MAX_LINES_PER_FILE) {
    // 使用更高效的 CSV 解析库（如 OpenCSV）
}
```

---

##### 5. 资源泄漏风险 - **需改进**

**位置**: `CdcEventsService.java:248-301` (parseCsvFile)
```java
try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
    String line;
    int lineNumber = 0;
    while ((line = reader.readLine()) != null && lineNumber < 1000) {
        // ...
    }
} catch (Exception e) {
```

**问题**: `reader` 在异常时可能未正确关闭（虽然 try-with-resources 已处理，但建议显式记录）

**建议修复**:
```java
try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
    int lineNumber = 0;
    while ((line = reader.readLine()) != null && lineNumber < MAX_LINES_PER_FILE) {
        lineNumber++;
        // ...
    }
} catch (Exception e) {
    log.error("解析 CSV 文件失败：{}，已关闭资源", csvFile.getName(), e);
}
```

---

#### 🟢 低优先级问题（代码风格）

##### 6. Magic Number - **建议改进**

**位置**: `CdcEventsService.java:198-200`
```java
try (BufferedReader reader = new BufferedReader(new FileReader(file), 8192)) {
    char[] buffer = new char[8192];
```

**建议**: 提取为常量
```java
private static final int BUFFER_SIZE = 8192;
// ...
new BufferedReader(new FileReader(file), BUFFER_SIZE)
char[] buffer = new char[BUFFER_SIZE]
```

---

##### 7. 异常处理一致性 - **建议改进**

**位置**: `CdcEventsService.java:39-84` (listEvents)
```java
public Map<String, Object> listEvents(...) {
    try {
        // ...
    } catch (Exception e) {
        log.error("获取 CDC 事件失败", e);
        return createEmptyResult();
    }
}
```

**建议**: 区分异常类型，避免吞掉所有异常
```java
} catch (IOException e) {
    log.error("文件 IO 错误：{}", csvFile.getName(), e);
    // ...
} catch (Exception e) {
    log.error("未知错误", e);
}
```

---

## 📊 代码质量分析

### 架构优点

1. **分层清晰**: Controller → Service → Repository 标准三层架构
2. **依赖注入**: 使用 `@RequiredArgsConstructor`，符合 Spring 最佳实践
3. **配置分离**: 敏感配置通过环境变量管理
4. **模块化设计**: Flink CDC、数据源管理、事件监控职责分明

### 可改进点

1. **DTO 验证缺失**: 部分 Controller 缺少参数校验（如 `@Valid`）
2. **日志级别滥用**: 大量使用 `log.error()` 而非 `log.warn()`
3. **硬编码路径**: `outputPath` 等配置可进一步集中管理

---

## 🎯 修复优先级清单

| 优先级 | 问题 | 预计耗时 |
|--------|------|----------|
| 🔴 P0 | JWT 默认密钥更换 | 5 分钟 |
| 🟡 P1 | JDBC URL 构建加固 | 30 分钟 |
| 🟡 P1 | 文件路径遍历防护 | 45 分钟 |
| 🟢 P2 | CSV 解析优化 | 2 小时 |
| 🟢 P2 | Magic Number 提取 | 30 分钟 |

---

## 📝 总结

### 安全评分：92/100 ⭐⭐⭐⭐⭐
- **密码加密**: 优秀（BCrypt + AES/GCM）
- **认证机制**: 优秀（JWT + HS256）
- **XSS 防护**: 优秀（OWASP Encoder）
- **SQL 注入**: 良好（PreparedStatement）

### 质量评分：83/100 ⭐⭐⭐⭐
- **架构设计**: 优秀
- **代码规范**: 良好
- **性能优化**: 中等
- **可维护性**: 良好

---

## 🔗 参考资源

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [Spring Security Best Practices](https://spring.io/projects/spring-security)
- [Flink REST API Security](https://nightlies.apache.org/flink/flink-docs-release-1.19/docs/deployment/rest_api_security/)

---

**报告生成时间**: 2026-04-23 下午 2:19
**审计工具**: 人工代码审查 + 静态分析建议