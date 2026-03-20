<template>
  <header class="app-header">
    <div class="header-inner">
      <!-- Logo -->
      <div class="header-logo">
        <icon-thunderbolt-one theme="filled" size="18" fill="#79F2C0" />
        <span class="logo-text">Flink CDC</span>
      </div>

      <!-- Nav -->
      <nav class="header-nav">
        <RouterLink to="/" class="nav-item">首页</RouterLink>
        <RouterLink to="/datasources" class="nav-item">数据源</RouterLink>
        <RouterLink to="/tasks" class="nav-item">任务管理</RouterLink>
        <RouterLink to="/jobs" class="nav-item">作业监控</RouterLink>
        <RouterLink to="/events" class="nav-item">CDC事件</RouterLink>
        <RouterLink to="/cluster" class="nav-item">集群状态</RouterLink>
      </nav>

      <!-- User -->
      <div class="header-user">
        <div class="user-avatar">{{ usernameInitial }}</div>
        <span class="user-name">{{ username }}</span>
        <button class="logout-btn" @click="handleLogout">退出</button>
      </div>
    </div>
  </header>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

const router = useRouter()
const username = ref('用户')

const usernameInitial = computed(() =>
  username.value ? username.value.charAt(0).toUpperCase() : 'U'
)

onMounted(() => {
  const stored = localStorage.getItem('username')
  if (stored) username.value = stored
})

function handleLogout() {
  if (confirm('确定要退出登录吗？')) {
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    localStorage.removeItem('loginTime')
    router.push('/login')
  }
}
</script>

<style scoped>
.app-header {
  background: #0747A6;
  height: 48px;
  position: sticky;
  top: 0;
  z-index: 100;
  box-shadow: 0 2px 4px rgba(9,30,66,0.3);
}

.header-inner {
  height: 100%;
  max-width: 1440px;
  margin: 0 auto;
  padding: 0 16px;
  display: flex;
  align-items: center;
  gap: 0;
}

/* Logo */
.header-logo {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px 0 0;
  margin-right: 8px;
  border-right: 1px solid rgba(255,255,255,0.15);
  flex-shrink: 0;
}

.logo-icon {
  display: flex;
  align-items: center;
}

.logo-text {
  font-size: 15px;
  font-weight: 700;
  color: #fff;
  letter-spacing: -0.01em;
}

/* Nav */
.header-nav {
  display: flex;
  align-items: center;
  flex: 1;
  height: 100%;
}

.nav-item {
  display: flex;
  align-items: center;
  height: 100%;
  padding: 0 12px;
  color: rgba(255,255,255,0.85);
  text-decoration: none;
  font-size: 13px;
  font-weight: 500;
  transition: background 0.15s, color 0.15s;
  border-bottom: 3px solid transparent;
  white-space: nowrap;
}

.nav-item:hover {
  background: rgba(255,255,255,0.1);
  color: #fff;
}

.nav-item.router-link-active {
  color: #fff;
  border-bottom-color: #4C9AFF;
  background: rgba(255,255,255,0.08);
}

/* User */
.header-user {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: auto;
  flex-shrink: 0;
}

.user-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #0052CC;
  border: 2px solid rgba(255,255,255,0.4);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-name {
  font-size: 13px;
  color: rgba(255,255,255,0.85);
}

.logout-btn {
  padding: 4px 10px;
  background: transparent;
  border: 1px solid rgba(255,255,255,0.35);
  border-radius: 3px;
  color: rgba(255,255,255,0.85);
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.logout-btn:hover {
  background: rgba(255,255,255,0.12);
  border-color: rgba(255,255,255,0.6);
  color: #fff;
}
</style>
