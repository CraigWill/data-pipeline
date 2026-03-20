import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/',
      name: 'home',
      component: () => import('../views/HomeView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/datasources',
      name: 'datasources',
      component: () => import('../views/DataSourceView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/tasks',
      name: 'tasks',
      component: () => import('../views/TaskListView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/tasks/create',
      name: 'task-create',
      component: () => import('../views/TaskCreateView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/jobs',
      name: 'jobs',
      component: () => import('../views/JobsView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/cluster',
      name: 'cluster',
      component: () => import('../views/ClusterView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/events',
      name: 'events',
      component: () => import('../views/CdcEventsView.vue'),
      meta: { requiresAuth: true }
    }
  ]
})

// 路由守卫 - 检查登录状态
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth)

  if (requiresAuth && !token) {
    // 需要登录但未登录，跳转到登录页
    next('/login')
  } else if (to.path === '/login' && token) {
    // 已登录但访问登录页，跳转到首页
    next('/')
  } else {
    next()
  }
})

export default router
