<template>
  <div class="home-view">
    <!-- 仪表盘 -->
    <div class="dashboard">
      <DashboardCard
        title="运行中的作业"
        :value="dashboard.runningJobs"
        label="Running Jobs"
      />
      <DashboardCard
        title="TaskManager"
        :value="dashboard.availableSlots"
        label="Available Slots"
      />
      <DashboardCard
        title="输出文件"
        :value="dashboard.totalFiles"
        label="Total CSV Files"
      />
      <DashboardCard
        title="数据量"
        :value="`${dashboard.totalSizeMB} MB`"
        label="Total Size"
      />
    </div>

    <!-- 快速操作 -->
    <div class="quick-actions card">
      <h2 class="section-title">快速操作</h2>
      <div class="action-buttons">
        <button class="btn btn-primary" @click="$router.push('/datasources')">
          <icon-data-display theme="outline" size="14" /> 管理数据源
        </button>
        <button class="btn btn-success" @click="$router.push('/tasks/create')">
          <icon-add-one theme="outline" size="14" /> 创建 CDC 任务
        </button>
        <button class="btn btn-secondary" @click="$router.push('/tasks')">
          <icon-list-checkbox theme="outline" size="14" /> 查看任务列表
        </button>
        <button class="btn btn-secondary" @click="$router.push('/jobs')">
          <icon-search theme="outline" size="14" /> 监控作业
        </button>
      </div>
    </div>

    <!-- 最近的作业 -->
    <div class="recent-jobs card">
      <div class="d-flex justify-between align-center mb-3">
        <h2 class="section-title">最近的作业</h2>
        <button class="btn btn-sm btn-secondary" @click="loadJobs">
          <icon-refresh theme="outline" size="13" /> 刷新
        </button>
      </div>

      <div v-if="loading" class="loading">加载中...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else-if="jobs.length === 0" class="text-center text-muted">
        暂无作业
      </div>
      <table v-else class="table">
        <thead>
          <tr>
            <th>作业名称</th>
            <th>作业 ID</th>
            <th>状态</th>
            <th>开始时间</th>
            <th>持续时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="job in jobs.slice(0, 5)" :key="job.jid">
            <td>{{ job.name || 'Unknown' }}</td>
            <td><code>{{ job.jid }}</code></td>
            <td>
              <span :class="['badge', getStatusClass(job.state)]">
                {{ job.state }}
              </span>
            </td>
            <td>{{ formatTimestamp(job['start-time']) }}</td>
            <td>{{ formatDuration(job.duration) }}</td>
            <td>
              <button 
                class="btn btn-sm btn-primary" 
                @click="viewJobDetail(job.jid)"
              >
                详情
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 刷新按钮 -->
    <button class="refresh-btn" @click="refreshAll" title="刷新数据">
      <icon-refresh theme="outline" size="20" fill="#fff" />
    </button>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import DashboardCard from '../components/DashboardCard.vue'
import { clusterAPI, outputAPI, jobAPI } from '../api'

const router = useRouter()

const dashboard = ref({
  runningJobs: '-',
  availableSlots: '-',
  totalFiles: '-',
  totalSizeMB: '-'
})

const jobs = ref([])
const loading = ref(false)
const error = ref(null)

let refreshInterval = null

// 加载仪表盘数据
const loadDashboard = async () => {
  try {
    const [clusterData, outputData] = await Promise.all([
      clusterAPI.overview(),
      outputAPI.stats()
    ])

    if (clusterData.success) {
      dashboard.value.runningJobs = clusterData.data['jobs-running'] || 0
      dashboard.value.availableSlots = clusterData.data['slots-available'] || 0
    }

    if (outputData.success) {
      dashboard.value.totalFiles = outputData.data.total_files || 0
      dashboard.value.totalSizeMB = outputData.data.total_size_mb || 0
    }
  } catch (err) {
    console.error('加载仪表盘失败:', err)
  }
}

// 加载作业列表
const loadJobs = async () => {
  loading.value = true
  error.value = null

  try {
    const data = await jobAPI.list()
    if (data.success) {
      jobs.value = data.data
    } else {
      error.value = data.error
    }
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

// 查看作业详情
const viewJobDetail = (jobId) => {
  router.push(`/jobs?id=${jobId}`)
}

// 刷新所有数据
const refreshAll = () => {
  loadDashboard()
  loadJobs()
}

// 获取状态样式类
const getStatusClass = (state) => {
  const statusMap = {
    'RUNNING': 'badge-success',
    'FAILED': 'badge-danger',
    'FINISHED': 'badge-info',
    'CANCELED': 'badge-secondary'
  }
  return statusMap[state] || 'badge-secondary'
}

// 格式化时间戳
const formatTimestamp = (ts) => {
  if (!ts) return 'N/A'
  return new Date(ts).toLocaleString('zh-CN')
}

// 格式化持续时间
const formatDuration = (ms) => {
  if (!ms) return 'N/A'
  const seconds = Math.floor(ms / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  
  if (hours > 0) return `${hours}h ${minutes % 60}m`
  if (minutes > 0) return `${minutes}m ${seconds % 60}s`
  return `${seconds}s`
}

onMounted(() => {
  refreshAll()
  // 自动刷新 (每 30 秒)
  refreshInterval = setInterval(refreshAll, 30000)
})

onUnmounted(() => {
  if (refreshInterval) {
    clearInterval(refreshInterval)
  }
})
</script>

<style scoped>
.dashboard {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
  color: #172B4D;
}

.quick-actions {
  margin-bottom: 20px;
}

.action-buttons {
  display: flex;
  gap: 10px;
  margin-top: 16px;
  flex-wrap: wrap;
}

.recent-jobs {
  margin-bottom: 80px;
}

.refresh-btn {
  position: fixed;
  bottom: 28px;
  right: 28px;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #0052CC;
  color: white;
  border: none;
  font-size: 20px;
  cursor: pointer;
  box-shadow: 0 4px 8px rgba(0,82,204,0.4);
  transition: all 0.2s;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
}

.refresh-btn:hover {
  background: #0065FF;
  box-shadow: 0 6px 12px rgba(0,82,204,0.5);
  transform: scale(1.05);
}
</style>
