# CDC 实时数据流修复总结

## 问题描述
前端访问 `/api/cdc/events/stream` SSE 端点时返回 401 Unauthorized 错误，导致实时数据流无法显示。

## 根本原因
EventSource API 不支持自定义 HTTP 头（如 Authorization），因此无法发送 JWT token。而 SecurityConfig 中将所有 `/api/**` 端点都设置为需要认证，导致 SSE 端点被拦截。

## 解决方案
在 `SecurityConfig.java` 中将 SSE 流端点添加到白名单：

```java
.authorizeRequests()
    // 公开端点
    .antMatchers("/api/auth/**").permitAll()
    .antMatchers("/actuator/health").permitAll()
    // SSE 流端点（EventSource 不支持自定义 header）
    .antMatchers("/api/cdc/events/stream").permitAll()
    // 所有其他 API 需要认证
    .antMatchers("/api/**").authenticated()
    .anyRequest().permitAll();
```

## 修复步骤
1. 修改 `monitor-backend/src/main/java/com/realtime/monitor/config/SecurityConfig.java`
2. 重新编译：`mvn clean package -pl monitor-backend -am -DskipTests`
3. 重建 Docker 镜像：`docker-compose build monitor-backend`
4. 重启容器：`docker-compose up -d monitor-backend`

## 验证结果

### SSE 连接成功
```
新增 SSE 订阅者，当前订阅数: 1
新增 SSE 订阅者，当前订阅数: 2
```

### 实时数据流正常
```bash
$ curl -N http://localhost:5001/api/cdc/events/stream
event:stats
data:{"date":"2026-03-30","deleteEvents":16,"updateEvents":2,"totalEvents":18,"insertEvents":0,"eventsPerSecond":0.0}

event:heartbeat
data:{"date":"2026-03-30","deleteEvents":16,"updateEvents":2,"totalEvents":18,"insertEvents":0,"eventsPerSecond":0.0}
```

### CDC 数据捕获正常
- **今日统计**: 18 个事件（16 DELETE + 2 UPDATE）
- **最新文件**: `output/cdc/2026-03-30--14/`
- **最新事件**: 8 条 DELETE 记录（14:16:22）

### 示例数据
```csv
2026-03-30 14:16:22.160,DELETE,1772234906940,ACC1772234906,User_1772234906,CHECKING,87511.39,ACTIVE,...
2026-03-30 14:16:22.258,DELETE,1772101202378,ACC094450111277,User_42948,SAVINGS,8276.71,ACTIVE,...
```

## 技术说明

### EventSource 限制
- EventSource API 是浏览器原生的 SSE 客户端
- 不支持自定义 HTTP 头（包括 Authorization）
- 只能通过 URL 参数传递认证信息（不安全）
- 或者将端点设为公开访问（当前方案）

### 安全考虑
- SSE 端点现在是公开的，任何人都可以订阅
- 数据是只读的，不涉及敏感操作
- 如需更严格的安全控制，可以考虑：
  - 使用 WebSocket 替代 SSE（支持自定义头）
  - 通过 URL 参数传递 token（不推荐）
  - 实现基于 IP 的访问控制

## 相关文件
- `monitor-backend/src/main/java/com/realtime/monitor/config/SecurityConfig.java`
- `monitor-backend/src/main/java/com/realtime/monitor/controller/CdcEventsController.java`
- `monitor-backend/src/main/java/com/realtime/monitor/service/CdcStatsService.java`
- `monitor/frontend-vue/src/views/CdcEventsView.vue`

## 状态
✅ 已修复 - SSE 实时数据流正常工作
