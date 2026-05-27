<template>
  <div id="app">
    <AppHeader v-if="!isLoginPage" />
    <main :class="['main-content', { 'no-padding': isLoginPage }]">
      <RouterView />
    </main>
    <footer :class="isLoginPage ? 'login-footer' : 'app-footer'">
      <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">
        沪ICP备2026022144号
      </a>
    </footer>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import AppHeader from './components/AppHeader.vue'

const route = useRoute()

// 判断是否是登录页面
const isLoginPage = computed(() => route.path === '/login')
</script>

<style scoped>
.main-content {
  max-width: 1440px;
  margin: 0 auto;
  padding: 24px 24px;
}

.main-content.no-padding {
  max-width: none;
  padding: 0;
}

.app-footer {
  text-align: center;
  padding: 16px 0;
  font-size: 12px;
  color: #8c8c8c;
  border-top: 1px solid #f0f0f0;
  margin-top: 8px;
}

.app-footer a {
  color: #8c8c8c;
  text-decoration: none;
}

.app-footer a:hover {
  color: #1677ff;
}

/* 登录页背景为深色渐变，footer 改为白色半透明 */
.login-footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  text-align: center;
  padding: 12px 0;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.75);
  border-top: none;
  margin-top: 0;
}

.login-footer a {
  color: rgba(255, 255, 255, 0.75);
  text-decoration: none;
}

.login-footer a:hover {
  color: #fff;
}
</style>
