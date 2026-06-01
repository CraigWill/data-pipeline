<template>
  <header class="app-header">
    <div class="header-inner">
      <!-- Logo -->
      <div class="header-logo">
        <icon-thunderbolt-one theme="filled" size="18" fill="#79F2C0" />
        <span class="logo-text">Flink CDC</span>
      </div>

      <!-- 桌面导航 -->
      <nav class="header-nav desktop-nav">
        <RouterLink to="/" class="nav-item">首页</RouterLink>
        <RouterLink to="/datasources" class="nav-item">数据源</RouterLink>
        <RouterLink to="/tasks" class="nav-item">任务管理</RouterLink>
        <RouterLink to="/jobs" class="nav-item">作业监控</RouterLink>
        <RouterLink to="/events" class="nav-item">CDC事件</RouterLink>
        <RouterLink to="/cluster" class="nav-item">集群状态</RouterLink>
      </nav>

      <!-- 用户区 -->
      <div class="header-user">
        <div class="user-avatar">{{ usernameInitial }}</div>
        <span class="user-name desktop-only">{{ username }}</span>
        <button class="logout-btn desktop-only" @click="handleLogout">退出</button>
      </div>

      <!-- 汉堡按钮（手机端） -->
      <button class="hamburger" :class="{ open: menuOpen }" @click="menuOpen = !menuOpen" aria-label="菜单">
        <span></span><span></span><span></span>
      </button>
    </div>

    <!-- 手机端下拉菜单 -->
    <transition name="mobile-menu">
      <div v-if="menuOpen" class="mobile-nav" @click="menuOpen = false">
        <RouterLink to="/" class="mobile-nav-item">首页</RouterLink>
        <RouterLink to="/datasources" class="mobile-nav-item">数据源</RouterLink>
        <RouterLink to="/tasks" class="mobile-nav-item">任务管理</RouterLink>
        <RouterLink to="/jobs" class="mobile-nav-item">作业监控</RouterLink>
        <RouterLink to="/events" class="mobile-nav-item">CDC事件</RouterLink>
        <RouterLink to="/cluster" class="mobile-nav-item">集群状态</RouterLink>
        <div class="mobile-nav-divider"></div>
        <div class="mobile-nav-user">
          <span>{{ username }}</span>
          <button class="logout-btn" @click.stop="handleLogout">退出登录</button>
        </div>
      </div>
    </transition>
  </header>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

const router = useRouter()
const username = ref('用户')
const menuOpen = ref(false)

const usernameInitial = computed(() =>
  username.value ? username.value.charAt(0).toUpperCase() : 'U'
)

onMounted(() => {
  const stored = localStorage.getItem('username')
  if (stored) username.value = stored
  // 路由变化时关闭菜单
  router.afterEach(() => { menuOpen.value = false })
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
  position: sticky;
  top: 0;
  z-index: 100;
  box-shadow: 0 2px 4px rgba(9,30,66,0.3);
}

.header-inner {
  height: 48px;
  max-width: 1440px;
  margin: 0 auto;
  padding: 0 16px;
  display: flex;
  align-items: center;
  gap: 0;
}

/* ── Logo ── */
.header-logo {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px 0 0;
  margin-right: 8px;
  border-right: 1px solid rgba(255,255,255,0.15);
  flex-shrink: 0;
}

.logo-text {
  font-size: 15px;
  font-weight: 700;
  color: #fff;
  letter-spacing: -0.01em;
}

/* ── 桌面导航 ── */
.header-nav {
  display: flex;
  align-items: center;
  flex: 1;
  height: 48px;
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

/* ── 用户区 ── */
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
  flex-shrink: 0;
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
  white-space: nowrap;
}

.logout-btn:hover {
  background: rgba(255,255,255,0.12);
  border-color: rgba(255,255,255,0.6);
  color: #fff;
}

/* ── 汉堡按钮 ── */
.hamburger {
  display: none;
  flex-direction: column;
  justify-content: center;
  gap: 5px;
  width: 36px;
  height: 36px;
  padding: 6px;
  background: none;
  border: none;
  cursor: pointer;
  margin-left: 8px;
  border-radius: 4px;
  transition: background 0.15s;
}

.hamburger:hover { background: rgba(255,255,255,0.1); }

.hamburger span {
  display: block;
  height: 2px;
  background: rgba(255,255,255,0.9);
  border-radius: 2px;
  transition: transform 0.2s, opacity 0.2s;
}

.hamburger.open span:nth-child(1) { transform: translateY(7px) rotate(45deg); }
.hamburger.open span:nth-child(2) { opacity: 0; }
.hamburger.open span:nth-child(3) { transform: translateY(-7px) rotate(-45deg); }

/* ── 手机端下拉菜单 ── */
.mobile-nav {
  background: #0747A6;
  border-top: 1px solid rgba(255,255,255,0.12);
  padding: 8px 0 12px;
}

.mobile-nav-item {
  display: block;
  padding: 12px 20px;
  color: rgba(255,255,255,0.85);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  transition: background 0.15s;
}

.mobile-nav-item:hover,
.mobile-nav-item.router-link-active {
  background: rgba(255,255,255,0.1);
  color: #fff;
}

.mobile-nav-divider {
  height: 1px;
  background: rgba(255,255,255,0.12);
  margin: 8px 0;
}

.mobile-nav-user {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 20px;
  color: rgba(255,255,255,0.7);
  font-size: 13px;
}

/* ── 过渡动画 ── */
.mobile-menu-enter-active,
.mobile-menu-leave-active {
  transition: opacity 0.15s, transform 0.15s;
}
.mobile-menu-enter-from,
.mobile-menu-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* ── 响应式 ── */
@media (max-width: 767px) {
  .desktop-nav { display: none; }
  .desktop-only { display: none; }
  .hamburger { display: flex; }
  .header-user { margin-left: auto; }
}

@media (min-width: 768px) {
  .mobile-nav { display: none; }
  .hamburger { display: none; }
}
</style>
