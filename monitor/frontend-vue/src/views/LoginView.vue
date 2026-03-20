<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <h1>Flink CDC 监控系统</h1>
        <p>实时数据采集与监控平台</p>
      </div>

      <div v-if="alert.show" :class="['alert', `alert-${alert.type}`]">
        {{ alert.message }}
      </div>

      <form @submit.prevent="handleLogin" class="login-form">
        <div class="form-group">
          <label for="username">用户名</label>
          <input
            id="username"
            v-model="form.username"
            type="text"
            required
            placeholder="请输入用户名"
            autocomplete="username"
          />
        </div>

        <div class="form-group">
          <label for="password">密码</label>
          <input
            id="password"
            v-model="form.password"
            type="password"
            required
            placeholder="请输入密码"
            autocomplete="current-password"
          />
        </div>

        <div class="form-group checkbox-group">
          <label>
            <input v-model="form.remember" type="checkbox" />
            <span>记住我</span>
          </label>
        </div>

        <button type="submit" class="btn btn-primary btn-block" :disabled="loading">
          <span v-if="loading">登录中...</span>
          <span v-else>登录</span>
        </button>
      </form>

      <div class="login-footer">
        <p class="text-muted">默认账号: admin / admin</p>
        <p class="text-xs">© 2026 Flink CDC 监控系统</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const form = ref({
  username: '',
  password: '',
  remember: false
})

const loading = ref(false)
const alert = ref({ show: false, type: '', message: '' })

onMounted(() => {
  // 检查是否已登录
  const token = localStorage.getItem('token')
  if (token) {
    router.push('/')
  }

  // 检查是否记住了用户名
  const rememberedUsername = localStorage.getItem('rememberedUsername')
  if (rememberedUsername) {
    form.value.username = rememberedUsername
    form.value.remember = true
  }
})

async function handleLogin() {
  if (!form.value.username || !form.value.password) {
    showAlert('error', '请输入用户名和密码')
    return
  }

  loading.value = true

  try {
    // 简单的本地验证（实际项目中应该调用后端 API）
    await simulateLogin(form.value.username, form.value.password)

    // 保存登录状态
    const token = generateToken()
    localStorage.setItem('token', token)
    localStorage.setItem('username', form.value.username)
    localStorage.setItem('loginTime', new Date().toISOString())

    // 记住用户名
    if (form.value.remember) {
      localStorage.setItem('rememberedUsername', form.value.username)
    } else {
      localStorage.removeItem('rememberedUsername')
    }

    showAlert('success', '登录成功！')

    // 跳转到首页
    setTimeout(() => {
      router.push('/')
    }, 500)
  } catch (error) {
    showAlert('error', error.message)
  } finally {
    loading.value = false
  }
}

// 模拟登录（实际项目中应该调用后端 API）
function simulateLogin(username, password) {
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      // 简单的用户验证
      const validUsers = {
        'admin': 'admin',
        'user': 'user123',
        'test': 'test123'
      }

      if (validUsers[username] && validUsers[username] === password) {
        resolve()
      } else {
        reject(new Error('用户名或密码错误'))
      }
    }, 800)
  })
}

// 生成简单的 token
function generateToken() {
  return 'token_' + Math.random().toString(36).substr(2) + Date.now().toString(36)
}

function showAlert(type, message) {
  alert.value = { show: true, type, message }
  setTimeout(() => {
    alert.value.show = false
  }, 3000)
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #F4F5F7;
  padding: 20px;
}

.login-box {
  background: #fff;
  border-radius: 4px;
  box-shadow: 0 1px 1px rgba(9,30,66,0.25), 0 0 0 1px rgba(9,30,66,0.08);
  width: 100%;
  max-width: 400px;
  padding: 40px 40px 32px;
}

.login-header {
  text-align: center;
  margin-bottom: 28px;
}

.login-header h1 {
  font-size: 20px;
  font-weight: 700;
  color: #172B4D;
  margin-bottom: 6px;
}

.login-header p {
  font-size: 13px;
  color: #5E6C84;
}

.login-form {
  margin-bottom: 16px;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  margin-bottom: 4px;
  font-size: 11px;
  font-weight: 700;
  color: #5E6C84;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.form-group input[type="text"],
.form-group input[type="password"] {
  width: 100%;
  padding: 8px 10px;
  border: 2px solid #DFE1E6;
  border-radius: 4px;
  font-size: 14px;
  color: #172B4D;
  transition: border-color 0.15s;
  outline: none;
}

.form-group input:hover {
  border-color: #B3BAC5;
}

.form-group input:focus {
  border-color: #4C9AFF;
  box-shadow: 0 0 0 2px rgba(76,154,255,0.2);
}

.checkbox-group {
  display: flex;
  align-items: center;
}

.checkbox-group label {
  display: flex;
  align-items: center;
  margin-bottom: 0;
  cursor: pointer;
  font-size: 13px;
  color: #172B4D;
  text-transform: none;
  letter-spacing: 0;
  font-weight: 400;
}

.checkbox-group input[type="checkbox"] {
  width: auto;
  margin-right: 8px;
  cursor: pointer;
  accent-color: #0052CC;
}

.btn-block {
  width: 100%;
  height: 36px;
  font-size: 14px;
  font-weight: 600;
  background: #0052CC;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s;
}

.btn-block:hover {
  background: #0065FF;
}

.btn-block:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.login-footer {
  text-align: center;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #DFE1E6;
}

.login-footer p {
  margin: 6px 0;
  font-size: 12px;
  color: #97A0AF;
}

.alert {
  padding: 10px 14px;
  border-radius: 4px;
  margin-bottom: 16px;
  font-size: 13px;
  border-left: 4px solid transparent;
}

.alert-success {
  background: #E3FCEF;
  color: #006644;
  border-color: #00875A;
}

.alert-error {
  background: #FFEBE6;
  color: #BF2600;
  border-color: #DE350B;
}
</style>
