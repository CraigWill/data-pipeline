<template>
  <div class="login-container">
    <div class="login-box">
      <h2>实时数据管道监控系统</h2>
      <form @submit.prevent="handleLogin">
        <div class="form-group">
          <label>用户名</label>
          <input 
            v-model="username" 
            type="text" 
            placeholder="请输入用户名"
            required
          />
        </div>
        <div class="form-group">
          <label>密码</label>
          <input 
            v-model="password" 
            type="password" 
            placeholder="请输入密码"
            required
          />
        </div>
        <div class="form-group captcha-group">
          <label>验证码</label>
          <div class="captcha-row">
            <input 
              v-model="captchaCode" 
              type="text" 
              placeholder="请输入计算结果"
              required
              class="captcha-input"
            />
            <img 
              :src="captchaImage" 
              @click="refreshCaptcha" 
              class="captcha-image"
              title="点击刷新验证码"
              alt="验证码"
            />
          </div>
        </div>
        <div v-if="error" class="error-message">{{ error }}</div>
        <button type="submit" :disabled="loading">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>
    </div>
  </div>
</template>

<script>
import { authAPI } from '@/api'
import axios from 'axios'

export default {
  name: 'LoginView',
  data() {
    return {
      username: '',
      password: '',
      captchaCode: '',
      captchaId: '',
      captchaImage: '',
      error: '',
      loading: false
    }
  },
  mounted() {
    this.refreshCaptcha()
  },
  methods: {
    async refreshCaptcha() {
      try {
        const res = await axios.get('/api/auth/captcha')
        if (res.data && res.data.success) {
          this.captchaId = res.data.data.captchaId
          this.captchaImage = res.data.data.image
        }
      } catch (err) {
        console.error('获取验证码失败:', err)
      }
    },
    async handleLogin() {
      this.error = ''
      this.loading = true

      try {
        const response = await authAPI.login({
          username: this.username,
          password: this.password,
          captchaId: this.captchaId,
          captchaCode: this.captchaCode
        })

        if (response.success) {
          localStorage.setItem('token', response.data.token)
          localStorage.setItem('username', response.data.username)
          this.$router.push('/')
        } else {
          this.error = response.message || response.error || '登录失败'
          this.refreshCaptcha()
          this.captchaCode = ''
        }
      } catch (err) {
        console.error('登录错误:', err)
        this.error = '用户名或密码错误'
        this.refreshCaptcha()
        this.captchaCode = ''
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-box {
  background: white;
  padding: 40px;
  border-radius: 10px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.2);
  width: 100%;
  max-width: 400px;
}

h2 {
  text-align: center;
  margin-bottom: 30px;
  color: #333;
}

.form-group {
  margin-bottom: 20px;
}

label {
  display: block;
  margin-bottom: 5px;
  color: #555;
  font-weight: 500;
}

input {
  width: 100%;
  padding: 10px;
  border: 1px solid #ddd;
  border-radius: 5px;
  font-size: 14px;
  box-sizing: border-box;
}

input:focus {
  outline: none;
  border-color: #667eea;
}

.captcha-group .captcha-row {
  display: flex;
  gap: 10px;
  align-items: center;
}

.captcha-input {
  flex: 1;
}

.captcha-image {
  height: 40px;
  border-radius: 5px;
  border: 1px solid #ddd;
  cursor: pointer;
  transition: opacity 0.2s;
}

.captcha-image:hover {
  opacity: 0.7;
}

button {
  width: 100%;
  padding: 12px;
  background: #667eea;
  color: white;
  border: none;
  border-radius: 5px;
  font-size: 16px;
  cursor: pointer;
  transition: background 0.3s;
}

button:hover:not(:disabled) {
  background: #5568d3;
}

button:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.error-message {
  color: #e74c3c;
  margin-bottom: 15px;
  text-align: center;
  font-size: 14px;
}
</style>
