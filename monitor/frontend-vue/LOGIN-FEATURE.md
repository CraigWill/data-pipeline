# 登录功能说明

## 功能概述

已为 Vue 3 前端添加完整的登录功能，包括登录页面、路由守卫、用户状态管理和登出功能。

## 新增文件

1. **src/views/LoginView.vue** - 登录页面组件
2. **LOGIN-FEATURE.md** - 本文档

## 修改文件

1. **src/router/index.js** - 添加登录路由和路由守卫
2. **src/components/AppHeader.vue** - 添加用户信息和登出按钮
3. **src/App.vue** - 登录页面不显示导航栏

## 功能特性

### 1. 登录页面

- ✅ 美观的渐变背景
- ✅ 响应式设计
- ✅ 表单验证
- ✅ 记住用户名功能
- ✅ 加载状态提示
- ✅ 错误提示
- ✅ 默认账号提示

### 2. 用户认证

**默认账号**:
- 用户名: `admin` / 密码: `admin`
- 用户名: `user` / 密码: `user123`
- 用户名: `test` / 密码: `test123`

**认证方式**:
- 当前使用本地验证（localStorage）
- 可轻松扩展为后端 API 认证

### 3. 路由守卫

- ✅ 未登录自动跳转到登录页
- ✅ 已登录访问登录页自动跳转首页
- ✅ 所有页面都需要登录才能访问
- ✅ 登录状态持久化

### 4. 用户界面

- ✅ 导航栏显示用户名
- ✅ 用户图标
- ✅ 退出登录按钮
- ✅ 退出确认对话框

### 5. 会话管理

**存储信息**:
- `token` - 登录令牌
- `username` - 用户名
- `loginTime` - 登录时间
- `rememberedUsername` - 记住的用户名（可选）

**会话保持**:
- 刷新页面保持登录状态
- 关闭浏览器后重新打开仍保持登录（如果勾选"记住我"）

## 使用方法

### 访问登录页

```
http://localhost:8888/login
```

### 登录流程

1. 访问任何页面，未登录自动跳转到登录页
2. 输入用户名和密码
3. 可选勾选"记住我"
4. 点击"登录"按钮
5. 登录成功后自动跳转到首页

### 登出流程

1. 点击导航栏右上角的"退出"按钮
2. 确认退出
3. 自动跳转到登录页

## 代码示例

### 登录验证

```javascript
// 简单的本地验证
const validUsers = {
  'admin': 'admin',
  'user': 'user123',
  'test': 'test123'
}

if (validUsers[username] && validUsers[username] === password) {
  // 登录成功
  const token = generateToken()
  localStorage.setItem('token', token)
  localStorage.setItem('username', username)
}
```

### 路由守卫

```javascript
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth)

  if (requiresAuth && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/')
  } else {
    next()
  }
})
```

### 登出功能

```javascript
function handleLogout() {
  if (confirm('确定要退出登录吗？')) {
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    localStorage.removeItem('loginTime')
    router.push('/login')
  }
}
```

## 扩展为后端认证

如需使用后端 API 进行认证，修改 `LoginView.vue` 中的 `simulateLogin` 函数：

```javascript
async function handleLogin() {
  loading.value = true
  
  try {
    // 调用后端登录 API
    const response = await api.post('/auth/login', {
      username: form.value.username,
      password: form.value.password
    })
    
    // 保存后端返回的 token
    const { token, username } = response.data
    localStorage.setItem('token', token)
    localStorage.setItem('username', username)
    
    // 跳转到首页
    router.push('/')
  } catch (error) {
    showAlert('error', error.message)
  } finally {
    loading.value = false
  }
}
```

## API 拦截器配置

在 `src/api/index.js` 中添加 token 拦截器：

```javascript
// 请求拦截器 - 添加 token
api.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

// 响应拦截器 - 处理 401 未授权
api.interceptors.response.use(
  response => response.data,
  error => {
    if (error.response?.status === 401) {
      // Token 过期或无效，跳转到登录页
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)
```

## 安全建议

### 生产环境

1. **使用 HTTPS**
   - 确保所有通信加密
   - 防止中间人攻击

2. **Token 过期**
   - 实现 token 过期机制
   - 自动刷新 token

3. **密码加密**
   - 前端传输前加密
   - 后端使用 bcrypt 等算法

4. **防止 XSS**
   - 输入验证和转义
   - Content Security Policy

5. **防止 CSRF**
   - 使用 CSRF token
   - SameSite Cookie

### 示例：Token 过期处理

```javascript
// 检查 token 是否过期
function isTokenExpired() {
  const loginTime = localStorage.getItem('loginTime')
  if (!loginTime) return true
  
  const now = new Date().getTime()
  const loginTimestamp = new Date(loginTime).getTime()
  const expirationTime = 24 * 60 * 60 * 1000 // 24 小时
  
  return now - loginTimestamp > expirationTime
}

// 在路由守卫中检查
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  
  if (token && isTokenExpired()) {
    // Token 过期，清除并跳转登录
    localStorage.clear()
    next('/login')
  } else {
    // Token 有效，继续
    next()
  }
})
```

## 测试

### 手动测试

1. **登录测试**
   ```
   访问: http://localhost:8888
   预期: 自动跳转到 /login
   输入: admin / admin
   预期: 登录成功，跳转到首页
   ```

2. **路由守卫测试**
   ```
   清除 localStorage
   访问: http://localhost:8888/tasks
   预期: 自动跳转到 /login
   ```

3. **登出测试**
   ```
   登录后点击"退出"按钮
   预期: 跳转到登录页，无法访问其他页面
   ```

4. **记住我测试**
   ```
   勾选"记住我"登录
   刷新页面
   预期: 用户名自动填充
   ```

### 自动化测试

```javascript
// 使用 Vitest 或 Jest
describe('Login', () => {
  it('should redirect to login when not authenticated', () => {
    // 测试未登录跳转
  })
  
  it('should login successfully with valid credentials', () => {
    // 测试登录成功
  })
  
  it('should show error with invalid credentials', () => {
    // 测试登录失败
  })
  
  it('should logout successfully', () => {
    // 测试登出
  })
})
```

## 常见问题

### Q1: 刷新页面后登录状态丢失？

A: 检查 localStorage 中是否有 token。如果有但仍然跳转登录页，检查路由守卫逻辑。

### Q2: 如何修改 token 过期时间？

A: 在后端设置 JWT token 的过期时间，前端根据后端返回的过期时间进行处理。

### Q3: 如何添加更多用户？

A: 修改 `LoginView.vue` 中的 `validUsers` 对象，或连接后端用户管理系统。

### Q4: 如何自定义登录页样式？

A: 修改 `LoginView.vue` 中的 `<style>` 部分，可以更改颜色、布局等。

## 更新日志

### 2026-03-09

- ✅ 创建登录页面组件
- ✅ 添加路由守卫
- ✅ 实现用户状态管理
- ✅ 添加登出功能
- ✅ 更新导航栏显示用户信息
- ✅ 登录页面不显示导航栏

## 下一步计划

- [ ] 连接后端登录 API
- [ ] 实现 token 自动刷新
- [ ] 添加忘记密码功能
- [ ] 添加注册功能
- [ ] 实现多因素认证（MFA）
- [ ] 添加社交登录（OAuth）

---

**创建时间**: 2026-03-09  
**版本**: 1.0.0  
**状态**: ✅ 完成
