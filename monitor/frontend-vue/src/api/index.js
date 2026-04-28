import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 请求拦截器 - 添加 Token
api.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器 - 处理 401
api.interceptors.response.use(
  response => {
    return response.data
  },
  error => {
    console.error('API Error:', error)
    if (error.response && error.response.status === 401) {
      // 清除 token 并跳转到登录页
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

// 认证 API
export const authAPI = {
  login: (credentials) => api.post('/auth/login', credentials),
  logout: () => api.post('/auth/logout'),
  getCurrentUser: () => api.get('/auth/me')
}

// 数据源 API
export const datasourceAPI = {
  list: () => api.get('/datasources'),
  get: (id) => api.get(`/datasources/${id}`),
  create: (data) => api.post('/datasources', data),
  update: (id, data) => api.put(`/datasources/${id}`, data),
  delete: (id) => api.delete(`/datasources/${id}`),
  test: (data) => api.post('/datasources/test', data)
}

// CDC 任务 API
export const taskAPI = {
  list: () => api.get('/cdc/tasks'),
  get: (id) => api.get(`/cdc/tasks/${id}`),
  getDetail: (id) => api.get(`/cdc/tasks/${id}/detail`),
  create: (data) => api.post('/cdc/tasks', data),
  delete: (id) => api.delete(`/cdc/tasks/${id}`),
  submit: (id) => api.post(`/cdc/tasks/${id}/submit`)
}

// Flink 作业 API
export const jobAPI = {
  list: () => api.get('/jobs'),
  get: (id) => api.get(`/jobs/${id}`),
  getMetrics: (id) => api.get(`/jobs/${id}/metrics`),
  cancel: (id) => api.post(`/jobs/${id}/cancel`),
  
  // CDC 作业
  listCdcJobs: () => api.get('/cdc/jobs'),
  getCdcJob: (id) => api.get(`/cdc/jobs/${id}`),
  cancelCdcJob: (id) => api.post(`/cdc/jobs/${id}/cancel`)
}

// 集群 API
export const clusterAPI = {
  overview: () => api.get('/cluster/overview'),
  taskmanagers: () => api.get('/cluster/taskmanagers'),
  jobs: () => api.get('/cluster/jobs')
}

// 输出 API
export const outputAPI = {
  stats: () => api.get('/output/stats'),
  files: (params) => api.get('/output/files', { params })
}

// 系统 API
export const systemAPI = {
  info: () => api.get('/system/info'),
  health: () => api.get('/health')
}

// CDC 事件 API
export const cdcEventsAPI = {
  list: (params) => api.get('/cdc/events', { params }),
  tables: () => api.get('/cdc/events/tables'),
  stats: () => api.get('/cdc/events/stats'),
  todayStats: () => api.get('/cdc/events/stats/today'),
  dailyStats: (table) => api.get('/cdc/events/stats/daily', { params: { table } }),
  // 文件列表和内容（通过 fileId 访问，不暴露文件路径）
  files: (date) => api.get('/cdc/events/files', { params: { date } }),
  fileContent: (fileId, page, size) => api.get('/cdc/events/files/content', { params: { fileId, page, size } }),
  dates: () => api.get('/cdc/events/dates')
}

export default api
