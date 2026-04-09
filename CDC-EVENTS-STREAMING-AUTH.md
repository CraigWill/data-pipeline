# CDC 实时数据流 JWT 认证实现

## 概述
将 SSE (Server-Sent Events) 实时数据流从 EventSource API 迁移到 fetch + ReadableStream，以支持 JWT 认证。

## 问题背景

### EventSource API 的限制
```javascript
// ❌ EventSource 不支持自定义 HTTP 头
const eventSource = new EventSource('/api/cdc/events/stream', {
  headers: {  // 不支持！
    'Authorization': 'Bearer ' + token
  }
});
```

EventSource API 的设计限制：
- 只支持 GET 请求
- 不允许自定义请求头
- 只能通过 cookies 进行身份验证
- 这是 W3C 标准的设计决定

## 解决方案：fetch + ReadableStream

### 方案对比

| 方案 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| 公开端点 | 简单直接 | 无认证保护 | 非敏感数据 |
| URL 参数传 token | 兼容 EventSource | token 暴露在 URL | 不推荐 |
| Cookie 认证 | EventSource 原生支持 | 需改认证机制 | 传统应用 |
| WebSocket | 双向通信 | 实现复杂 | 需要双向通信 |
| **fetch + ReadableStream** | **支持 JWT，完全控制** | **需手动解析 SSE** | **现代应用（推荐）** |

## 实现细节

### 1. 前端实现 (Vue 3)

```javascript
function startSSE() {
  stopSSE()
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
  const sseUrl = `${baseUrl}/cdc/events/stream`
  
  // 从 localStorage 获取 JWT token
  const token = localStorage.getItem('token')
  
  // 使用 fetch 发起请求，携带 Authorization 头
  fetch(sseUrl, {
    headers: {
      'Authorization': token ? `Bearer ${token}` : '',
      'Accept': 'text/event-stream'
    }
  })
  .then(response => {
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`)
    }
    
    sseConnected.value = true
    
    // 获取 ReadableStream reader
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    
    // 保存 reader 以便后续关闭
    eventSource = { reader, close: () => reader.cancel() }
    
    // 递归读取流数据
    function readStream() {
      reader.read().then(({ done, value }) => {
        if (done) {
          console.log('SSE 流结束')
          sseConnected.value = false
          setTimeout(startSSE, 3000)  // 3秒后重连
          return
        }
        
        // 解码数据并添加到缓冲区
        buffer += decoder.decode(value, { stream: true })
        
        // 处理缓冲区中的完整消息
        const lines = buffer.split('\n')
        buffer = lines.pop() || '' // 保留不完整的行
        
        let eventType = ''
        let eventData = ''
        
        // 解析 SSE 格式
        for (const line of lines) {
          if (line.startsWith('event:')) {
            eventType = line.substring(6).trim()
          } else if (line.startsWith('data:')) {
            eventData = line.substring(5).trim()
          } else if (line === '' && eventData) {
            // 空行表示消息结束，处理事件
            try {
              const data = JSON.parse(eventData)
              stats.value = data
              addTrendDataPoint(data.eventsPerSecond || 0)
            } catch (e) {
              console.error('解析 SSE 数据失败:', e, eventData)
            }
            
            // 重置状态
            eventType = ''
            eventData = ''
          }
        }
        
        // 继续读取
        readStream()
      }).catch(error => {
        console.error('SSE 读取错误:', error)
        sseConnected.value = false
        stopSSE()
        setTimeout(startSSE, 3000)
      })
    }
    
    readStream()
  })
  .catch(error => {
    console.error('SSE 连接失败:', error)
    sseConnected.value = false
    setTimeout(startSSE, 3000)
  })
}

function stopSSE() {
  if (eventSource) {
    if (eventSource.close) {
      eventSource.close()
    } else if (eventSource.reader) {
      eventSource.reader.cancel()
    }
    eventSource = null
  }
  sseConnected.value = false
}
```

### 2. 后端配置 (Spring Security)

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, 
                                          JwtTokenProvider tokenProvider,
                                          UserDetailsService userDetailsService) throws Exception {
        http
            .csrf().disable()
            .cors().and()
            .exceptionHandling()
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
            .and()
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeRequests()
                // 公开端点
                .antMatchers("/api/auth/**").permitAll()
                .antMatchers("/actuator/health").permitAll()
                // 所有其他 API 需要认证（包括 SSE 流端点）
                .antMatchers("/api/**").authenticated()
                .anyRequest().permitAll();

        // JWT 过滤器会验证 Authorization 头中的 token
        http.addFilterBefore(jwtAuthenticationFilter, 
                            UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

### 3. SSE 格式说明

标准 SSE 消息格式：
```
event: stats
data: {"date":"2026-03-30","totalEvents":19,"eventsPerSecond":0.0}

event: heartbeat
data: {"date":"2026-03-30","totalEvents":19,"eventsPerSecond":0.0}

```

关键点：
- `event:` 行指定事件类型
- `data:` 行包含 JSON 数据
- 空行表示消息结束
- 可能有多个 `data:` 行（会自动拼接）

## 技术优势

### 1. 安全性
- ✅ 支持 JWT 认证
- ✅ Token 在 HTTP 头中传输（不暴露在 URL）
- ✅ 与其他 API 使用相同的认证机制
- ✅ 可以实现细粒度的权限控制

### 2. 灵活性
- ✅ 完全控制请求和响应
- ✅ 可以添加任意自定义头
- ✅ 可以处理各种错误情况
- ✅ 支持自定义重连逻辑

### 3. 兼容性
- ✅ 现代浏览器都支持 ReadableStream
- ✅ 与标准 SSE 格式完全兼容
- ✅ 服务端无需修改（仍然返回 text/event-stream）

### 4. 功能性
- ✅ 自动重连（3秒延迟）
- ✅ 错误处理和日志
- ✅ 连接状态显示
- ✅ 优雅的关闭处理

## 浏览器兼容性

| 浏览器 | 版本 | 支持情况 |
|--------|------|----------|
| Chrome | 43+ | ✅ 完全支持 |
| Firefox | 65+ | ✅ 完全支持 |
| Safari | 10.1+ | ✅ 完全支持 |
| Edge | 14+ | ✅ 完全支持 |
| IE | 任何版本 | ❌ 不支持 |

## 部署步骤

### 1. 构建前端
```bash
cd monitor/frontend-vue
npm run build
```

### 2. 构建后端
```bash
mvn clean package -pl monitor-backend -am -DskipTests
```

### 3. 构建 Docker 镜像
```bash
docker-compose build monitor-backend monitor-frontend
```

### 4. 重启容器
```bash
docker-compose stop monitor-backend monitor-frontend
docker-compose up -d monitor-backend monitor-frontend
```

## 验证测试

### 1. 测试未认证访问（应该失败）
```bash
curl -N http://localhost:5001/api/cdc/events/stream
# 预期: HTTP 401 Unauthorized
```

### 2. 测试已认证访问（应该成功）
```bash
# 先登录获取 token
TOKEN=$(curl -X POST http://localhost:5001/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  | jq -r '.data.token')

# 使用 token 访问 SSE 流
curl -N http://localhost:5001/api/cdc/events/stream \
  -H "Authorization: Bearer $TOKEN"

# 预期: 持续接收 SSE 事件
# event:stats
# data:{"date":"2026-03-30","totalEvents":19,...}
```

### 3. 前端测试
1. 打开浏览器访问 http://localhost:8888
2. 登录系统（admin / admin123）
3. 进入 "CDC 事件监控" 页面
4. 检查页面顶部的连接状态：应显示 "● 已连接"
5. 观察实时数据流和趋势图更新

## 故障排查

### 问题 1: 连接状态显示 "○ 未连接"
**检查项**:
- 浏览器控制台是否有错误
- 是否已登录（localStorage 中是否有 token）
- 后端日志是否显示 "Unauthorized" 错误

**解决方法**:
```javascript
// 在浏览器控制台检查 token
console.log(localStorage.getItem('token'))

// 如果没有 token，重新登录
```

### 问题 2: 401 Unauthorized 错误
**原因**: JWT token 无效或过期

**解决方法**:
1. 重新登录获取新 token
2. 检查 token 是否正确存储在 localStorage
3. 检查后端 JWT 配置（密钥、过期时间）

### 问题 3: 数据不更新
**检查项**:
- SSE 连接是否正常（连接状态）
- 后端是否有新的 CDC 事件
- 浏览器控制台是否有 JSON 解析错误

**解决方法**:
```bash
# 检查后端日志
docker-compose logs monitor-backend --tail=50

# 检查是否有新的 CDC 文件
ls -lt output/cdc/$(date +%Y-%m-%d--*)/ | head -10
```

## 性能考虑

### 内存使用
- ReadableStream 是流式处理，不会一次性加载所有数据
- 缓冲区只保留不完整的行（通常很小）
- 自动垃圾回收已处理的数据

### 网络效率
- 与 EventSource 相同，使用 HTTP/1.1 长连接
- 支持 HTTP/2 多路复用
- 自动处理网络中断和重连

### CPU 使用
- JSON 解析是主要开销（与 EventSource 相同）
- 字符串操作（split、substring）开销很小
- 趋势图更新使用 requestAnimationFrame 优化

## 相关文件

### 前端
- `monitor/frontend-vue/src/views/CdcEventsView.vue` - SSE 客户端实现

### 后端
- `monitor-backend/src/main/java/com/realtime/monitor/config/SecurityConfig.java` - 安全配置
- `monitor-backend/src/main/java/com/realtime/monitor/controller/CdcEventsController.java` - SSE 端点
- `monitor-backend/src/main/java/com/realtime/monitor/service/CdcStatsService.java` - 统计服务
- `monitor-backend/src/main/java/com/realtime/monitor/security/JwtTokenProvider.java` - JWT 处理

## 总结

通过使用 fetch + ReadableStream 替代 EventSource API，我们实现了：
- ✅ JWT 认证支持
- ✅ 与现有认证体系集成
- ✅ 更好的错误处理
- ✅ 完全的控制权
- ✅ 现代化的实现方式

这是一个生产级的解决方案，适用于需要认证的实时数据流场景。
