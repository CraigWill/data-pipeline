# 安全认证实现指南

## 概述

本文档描述如何为 monitor-backend 添加完整的安全认证机制，包括：
- JWT Token 认证
- 密码加密存储（BCrypt）
- 登录/登出功能
- API 访问控制
- 数据源密码加密

## 1. 添加依赖

### monitor-backend/pom.xml

在 `<dependencies>` 中添加：

```xml
<!-- Spring Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.11.5</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.11.5</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.11.5</version>
    <scope>runtime</scope>
</dependency>
```

## 2. 创建安全配置类

### monitor-backend/src/main/java/com/realtime/monitor/config/SecurityConfig.java

```java
package com.realtime.monitor.config;

import com.realtime.monitor.security.JwtAuthenticationFilter;
import com.realtime.monitor.security.JwtAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .cors()
            .and()
            .exceptionHandling()
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
            .and()
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeHttpRequests(auth -> auth
                // 公开端点
                .requestMatchers("/api/auth/**", "/actuator/health").permitAll()
                // 所有其他 API 需要认证
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            );

        // 添加 JWT 过滤器
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
```

## 3. 创建 JWT 工具类

### monitor-backend/src/main/java/com/realtime/monitor/security/JwtTokenProvider.java

```java
package com.realtime.monitor.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret:your-256-bit-secret-key-change-this-in-production-environment}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}") // 24小时
    private long jwtExpiration;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Authentication authentication) {
        String username = authentication.getName();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (SecurityException ex) {
            log.error("Invalid JWT signature");
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token");
        } catch (ExpiredJwtException ex) {
            log.error("Expired JWT token");
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token");
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty");
        }
        return false;
    }
}
```

## 4. 创建 JWT 过滤器

### monitor-backend/src/main/java/com/realtime/monitor/security/JwtAuthenticationFilter.java

```java
package com.realtime.monitor.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                String username = tokenProvider.getUsernameFromToken(jwt);

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

## 5. 创建认证入口点

### monitor-backend/src/main/java/com/realtime/monitor/security/JwtAuthenticationEntryPoint.java

```java
package com.realtime.monitor.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.error("Unauthorized error: {}", authException.getMessage());

        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("error", "Unauthorized");
        body.put("message", "请先登录");
        body.put("path", request.getServletPath());

        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(response.getOutputStream(), body);
    }
}
```

## 6. 创建用户实体和服务

### monitor-backend/src/main/java/com/realtime/monitor/entity/User.java

```java
package com.realtime.monitor.entity;

import lombok.Data;

@Data
public class User {
    private Long id;
    private String username;
    private String password; // BCrypt 加密后的密码
    private String email;
    private String role; // ROLE_ADMIN, ROLE_USER
    private boolean enabled;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
}
```

### monitor-backend/src/main/java/com/realtime/monitor/service/UserService.java

```java
package com.realtime.monitor.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final PasswordEncoder passwordEncoder;

    // 临时存储用户（生产环境应使用数据库）
    private final Map<String, com.realtime.monitor.entity.User> users = new HashMap<>();

    public UserService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        // 初始化默认管理员账户
        initDefaultUsers();
    }

    private void initDefaultUsers() {
        com.realtime.monitor.entity.User admin = new com.realtime.monitor.entity.User();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode("admin123")); // 默认密码
        admin.setEmail("admin@example.com");
        admin.setRole("ROLE_ADMIN");
        admin.setEnabled(true);
        users.put("admin", admin);

        log.info("默认管理员账户已创建: username=admin, password=admin123");
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        com.realtime.monitor.entity.User user = users.get(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        return User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(Collections.singletonList(new SimpleGrantedAuthority(user.getRole())))
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(!user.isEnabled())
                .build();
    }

    public boolean changePassword(String username, String oldPassword, String newPassword) {
        com.realtime.monitor.entity.User user = users.get(username);
        if (user == null) {
            return false;
        }

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return false;
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        return true;
    }
}
```

## 7. 创建认证控制器

### monitor-backend/src/main/java/com/realtime/monitor/controller/AuthController.java

```java
package com.realtime.monitor.controller;

import com.realtime.monitor.dto.ApiResponse;
import com.realtime.monitor.security.JwtTokenProvider;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            String token = tokenProvider.generateToken(authentication);

            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("username", authentication.getName());
            data.put("authorities", authentication.getAuthorities());

            log.info("用户登录成功: {}", loginRequest.getUsername());
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("登录失败: {}", e.getMessage());
            return ApiResponse.error("用户名或密码错误");
        }
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        SecurityContextHolder.clearContext();
        return ApiResponse.success(null);
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ApiResponse.error("未登录");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("username", authentication.getName());
        data.put("authorities", authentication.getAuthorities());
        return ApiResponse.success(data);
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }
}
```

## 8. 密码加密工具类

### monitor-backend/src/main/java/com/realtime/monitor/util/PasswordEncryptionUtil.java

```java
package com.realtime.monitor.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密码加密工具类
 * - BCrypt: 用于用户密码（单向加密）
 * - AES: 用于数据源密码（可逆加密）
 */
public class PasswordEncryptionUtil {

    private static final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private static final String AES_ALGORITHM = "AES";
    private static final String AES_KEY = "your-32-byte-secret-key-here!!"; // 生产环境从配置文件读取

    /**
     * BCrypt 加密（用于用户密码）
     */
    public static String encodeBCrypt(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * BCrypt 验证
     */
    public static boolean matchesBCrypt(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    /**
     * AES 加密（用于数据源密码）
     */
    public static String encryptAES(String plainText) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(AES_KEY.getBytes(StandardCharsets.UTF_8), AES_ALGORITHM);
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("AES 加密失败", e);
        }
    }

    /**
     * AES 解密（用于数据源密码）
     */
    public static String decryptAES(String encryptedText) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(AES_KEY.getBytes(StandardCharsets.UTF_8), AES_ALGORITHM);
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES 解密失败", e);
        }
    }

    // 测试方法
    public static void main(String[] args) {
        // 测试 BCrypt
        String password = "admin123";
        String encoded = encodeBCrypt(password);
        System.out.println("BCrypt 加密: " + encoded);
        System.out.println("BCrypt 验证: " + matchesBCrypt(password, encoded));

        // 测试 AES
        String dbPassword = "oracle_password";
        String encrypted = encryptAES(dbPassword);
        String decrypted = decryptAES(encrypted);
        System.out.println("AES 加密: " + encrypted);
        System.out.println("AES 解密: " + decrypted);
    }
}
```

## 9. 修改数据源服务以支持密码加密

### 修改 DataSourceService.java

在保存数据源时加密密码，读取时解密：

```java
// 保存时加密
public DataSourceConfig saveDataSource(DataSourceConfig config) {
    // 加密密码
    if (config.getPassword() != null && !config.getPassword().isEmpty()) {
        config.setPassword(PasswordEncryptionUtil.encryptAES(config.getPassword()));
    }
    // ... 保存逻辑
}

// 读取时解密
public DataSourceConfig loadDataSource(String id) {
    DataSourceConfig config = // ... 从文件读取
    // 解密密码
    if (config.getPassword() != null && !config.getPassword().isEmpty()) {
        config.setPassword(PasswordEncryptionUtil.decryptAES(config.getPassword()));
    }
    return config;
}
```

## 10. 配置文件

### monitor-backend/src/main/resources/application.yml

添加 JWT 配置：

```yaml
jwt:
  secret: your-very-long-secret-key-at-least-256-bits-for-production-use
  expiration: 86400000 # 24小时（毫秒）

# AES 加密密钥（生产环境使用环境变量）
encryption:
  aes-key: ${AES_ENCRYPTION_KEY:your-32-byte-secret-key-here!!}
```

## 11. 前端集成

### 修改 monitor/frontend-vue/src/api/index.js

```javascript
// 添加认证 API
export const authAPI = {
  login: (credentials) => api.post('/auth/login', credentials),
  logout: () => api.post('/auth/logout'),
  getCurrentUser: () => api.get('/auth/me')
}

// 请求拦截器 - 添加 Token
api.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器 - 处理 401
api.interceptors.response.use(
  response => {
    return response.data
  },
  error => {
    if (error.response && error.response.status === 401) {
      // 清除 token 并跳转到登录页
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)
```

## 12. 部署步骤

1. **更新依赖**:
   ```bash
   cd monitor-backend
   mvn clean install
   ```

2. **生成安全密钥**:
   ```bash
   # 生成 JWT 密钥（至少 256 位）
   openssl rand -base64 32
   
   # 生成 AES 密钥（32 字节）
   openssl rand -base64 32
   ```

3. **配置环境变量**:
   ```bash
   export JWT_SECRET="your-generated-jwt-secret"
   export AES_ENCRYPTION_KEY="your-generated-aes-key"
   ```

4. **重新构建和部署**:
   ```bash
   ./quick-build.sh
   ./rebuild-all.sh
   docker-compose up -d
   ```

## 13. 测试

### 登录测试
```bash
curl -X POST http://localhost:5001/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### 访问受保护的 API
```bash
TOKEN="your-jwt-token"
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:5001/api/cluster/overview
```

## 安全建议

1. **生产环境必须修改默认密码**
2. **使用强密钥**（至少 256 位）
3. **密钥存储在环境变量中**，不要硬编码
4. **启用 HTTPS**
5. **定期更换密钥**
6. **实施密码策略**（复杂度、过期时间）
7. **添加登录失败次数限制**
8. **记录审计日志**

## 后续改进

1. 使用数据库存储用户信息
2. 添加用户管理界面
3. 实现角色权限管理（RBAC）
4. 添加 OAuth2/OIDC 支持
5. 实现 Token 刷新机制
6. 添加双因素认证（2FA）


---

## 实施状态

### ✅ 已完成

1. **依赖添加** - 已在 `monitor-backend/pom.xml` 中添加 Spring Security 和 JWT 依赖
2. **安全配置** - 已创建 `SecurityConfig.java`，配置 JWT 认证和 API 访问控制
3. **JWT 工具类** - 已创建 `JwtTokenProvider.java`，实现 Token 生成和验证
4. **JWT 过滤器** - 已创建 `JwtAuthenticationFilter.java`，拦截请求验证 Token
5. **认证入口点** - 已创建 `JwtAuthenticationEntryPoint.java`，处理未认证请求
6. **用户实体** - 已创建 `User.java` 实体类
7. **用户服务** - 已创建 `UserService.java`，实现 UserDetailsService，初始化默认管理员账户
8. **认证控制器** - 已创建 `AuthController.java`，提供登录、登出、获取当前用户接口
9. **密码加密工具** - 已创建 `PasswordEncryptionUtil.java`，支持 BCrypt 和 AES 加密
10. **数据源服务修改** - 已修改 `DataSourceService.java`，保存时加密密码，读取时解密
11. **前端 API 修改** - 已修改 `monitor/frontend-vue/src/api/index.js`，添加 Token 拦截器
12. **登录页面** - 已创建 `LoginView.vue`，提供登录界面
13. **路由守卫** - 路由配置已包含登录页面和路由守卫
14. **配置文件** - 已在 `application.yml` 中添加 JWT 和 AES 配置
15. **Docker 配置** - 已在 `docker-compose.yml` 中添加安全相关环境变量
16. **环境变量示例** - 已在 `.env.example` 中添加安全配置示例
17. **部署文档** - 已创建 `SECURITY-DEPLOYMENT-GUIDE.md` 详细部署指南
18. **部署脚本** - 已创建 `deploy-security.sh` 自动化部署脚本

### 📝 使用说明

#### 快速部署

```bash
# 使用自动化脚本部署
./deploy-security.sh
```

脚本会自动：
1. 检查并创建 .env 文件
2. 可选：自动生成安全密钥
3. 编译后端和前端
4. 构建 Docker 镜像
5. 重启服务
6. 测试登录功能

#### 手动部署

```bash
# 1. 配置环境变量
cp .env.example .env
# 编辑 .env，设置 JWT_SECRET 和 AES_ENCRYPTION_KEY

# 2. 编译后端
cd monitor-backend
mvn clean package -DskipTests
cd ..

# 3. 编译前端
cd monitor/frontend-vue
npm run build
cd ../..

# 4. 重新构建和启动
docker-compose build monitor-backend monitor-frontend
docker-compose up -d monitor-backend monitor-frontend
```

#### 访问系统

1. 打开浏览器访问: http://localhost:8888
2. 自动跳转到登录页面
3. 输入默认账户:
   - 用户名: `admin`
   - 密码: `admin123`
4. 登录成功后可以正常使用所有功能

### 🔒 安全特性

1. **JWT Token 认证**
   - 所有 API（除登录接口）都需要 Token
   - Token 有效期 24 小时
   - Token 使用 HS256 算法签名

2. **密码加密**
   - 用户密码使用 BCrypt 单向加密
   - 数据源密码使用 AES 可逆加密
   - 密钥从环境变量读取

3. **API 访问控制**
   - 公开端点: `/api/auth/**`
   - 受保护端点: `/api/**`
   - 未认证访问返回 401

4. **前端集成**
   - 自动添加 Token 到请求头
   - 401 响应自动跳转登录页
   - 登出时清除 Token

### ⚠️ 重要提示

1. **生产环境必须修改默认密码**
2. **生产环境必须使用强密钥**（至少 256 位）
3. **不要将 .env 文件提交到版本控制系统**
4. **启用 HTTPS**
5. **配置防火墙规则**
6. **定期轮换密钥**

### 📚 相关文档

- 详细部署指南: `SECURITY-DEPLOYMENT-GUIDE.md`
- 部署脚本: `deploy-security.sh`
- 环境变量配置: `.env.example`

### 🔧 故障排查

如果遇到问题，请查看:
1. 后端日志: `docker-compose logs monitor-backend`
2. 前端日志: `docker-compose logs monitor-frontend`
3. 浏览器控制台（F12）
4. 详细故障排查指南: `SECURITY-DEPLOYMENT-GUIDE.md`

### 🚀 后续改进

1. 用户管理界面（创建、编辑、删除用户）
2. 修改密码功能
3. 角色权限管理（RBAC）
4. 数据库存储用户信息
5. OAuth2/OIDC 集成
6. 审计日志
7. 登录失败限制
8. 双因素认证（2FA）
