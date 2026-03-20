<template>
  <div id="app">
    <AppHeader v-if="!isLoginPage" />
    <main :class="['main-content', { 'no-padding': isLoginPage }]">
      <RouterView />
    </main>
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
</style>
