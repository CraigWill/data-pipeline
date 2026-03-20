<template>
  <div class="jobs-view">
    <div class="page-header">
      <h2>Flink 作业监控</h2>
      <p>查看和管理运行中的 Flink 作业</p>
    </div>

    <div v-if="alert.show" :class="['alert', `alert-${alert.type}`]">
      {{ alert.message }}
    </div>

    <div class="actions mb-4">
      <button class="btn btn-secondary" @click="loadJobs"><icon-refresh theme="outline" size="13" /> 刷新</button>
    </div>

    <div v-if="loading" class="loading">
      <div class="spinner"></div>
      <p>加载作业列表...</p>
    </div>

    <div v-else-if="jobs.length === 0" class="empty-state">
      <div class="empty-icon"><icon-chart-histogram theme="outline" size="48" fill="#DFE1E6" /></div>
      <p>暂无运行中的作业</p>
      <p class="text-sm">提交 CDC 任务后将在此显示</p>
    </div>

    <div v-else class="jobs-grid">
      <div v-for="job in jobs" :key="job.jid" class="job-card">
        <div class="job-header">
          <div>
            <h3 class="job-name">{{ job.name }}</h3>
            <p class="job-id">Job ID: {{ job.jid }}</p>
          </div>
          <span :class="['status-badge', `status-${job.state.toLowerCase()}`]">
            {{ getStatusText(job.state) }}
          </span>
        </div>

        <div class="job-metrics">
          <div class="metric-item">
            <div class="metric-label">开始时间</div>
            <div class="metric-value">{{ formatTimestamp(job['start-time']) }}</div>
          </div>
          <div class="metric-item">
            <div class="metric-label">运行时长</div>
            <div class="metric-value">{{ formatDuration(job.duration) }}</div>
          </div>
          <div class="metric-item">
            <div class="metric-label">任务数</div>
            <div class="metric-value">{{ job.tasks?.total || 0 }}</div>
          </div>
        </div>

        <div class="job-tasks">
          <div class="task-stats">
            <span class="task-stat running">运行: {{ job.tasks?.running || 0 }}</span>
            <span class="task-stat finished">完成: {{ job.tasks?.finished || 0 }}</span>
            <span class="task-stat failed">失败: {{ job.tasks?.failed || 0 }}</span>
          </div>
        </div>

        <div class="job-actions">
          <button class="btn btn-secondary btn-sm" @click="viewJobDetail(job.jid)">查看详情</button>
          <button v-if="job.state === 'RUNNING'" 
                  class="btn btn-danger btn-sm" 
                  @click="cancelJob(job.jid)">
            取消作业
          </button>
        </div>
      </div>
    </div>

    <!-- 作业详情模态框 -->
    <div v-if="showDetailModal" class="modal active" @click.self="closeDetailModal">
      <div class="modal-content modal-large">
        <div class="modal-header">
          <h2>作业详情</h2>
          <span class="modal-close" @click="closeDetailModal">&times;</span>
        </div>
        <div v-if="jobDetail" class="job-detail">
          <div class="detail-section">
            <h3>基本信息</h3>
            <div class="detail-grid">
              <div class="detail-item">
                <span class="detail-label">作业名称</span>
                <span class="detail-value">{{ jobDetail.name }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">Job ID</span>
                <span class="detail-value">{{ jobDetail.jid }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">状态</span>
                <span :class="['status-badge', `status-${jobDetail.state.toLowerCase()}`]">
                  {{ getStatusText(jobDetail.state) }}
                </span>
              </div>
              <div class="detail-item">
                <span class="detail-label">开始时间</span>
                <span class="detail-value">{{ formatTimestamp(jobDetail['start-time']) }}</span>
              </div>
            </div>
          </div>

          <div class="detail-section">
            <h3>任务统计</h3>
            <div class="detail-grid">
              <div class="detail-item">
                <span class="detail-label">总任务数</span>
                <span class="detail-value">{{ jobDetail.tasks?.total || 0 }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">运行中</span>
                <span class="detail-value">{{ jobDetail.tasks?.running || 0 }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">已完成</span>
                <span class="detail-value">{{ jobDetail.tasks?.finished || 0 }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">失败</span>
                <span class="detail-value">{{ jobDetail.tasks?.failed || 0 }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import api from '@/api'

const jobs = ref([])
const loading = ref(false)
const showDetailModal = ref(false)
const jobDetail = ref(null)
const alert = ref({ show: false, type: '', message: '' })
let refreshInterval = null

onMounted(() => {
  loadJobs()
  // 每 5 秒自动刷新
  refreshInterval = setInterval(loadJobs, 5000)
})

onUnmounted(() => {
  if (refreshInterval) {
    clearInterval(refreshInterval)
  }
})

async function loadJobs() {
  if (loading.value) return
  
  loading.value = true
  try {
    const result = await api.get('/jobs')
    jobs.value = result.data || []
  } catch (error) {
    showAlert('error', '加载作业列表失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

async function viewJobDetail(jobId) {
  try {
    const result = await api.get(`/jobs/${jobId}`)
    jobDetail.value = result.data
    showDetailModal.value = true
  } catch (error) {
    showAlert('error', '加载作业详情失败: ' + error.message)
  }
}

function closeDetailModal() {
  showDetailModal.value = false
  jobDetail.value = null
}

async function cancelJob(jobId) {
  if (!confirm('确定要取消这个作业吗？')) {
    return
  }

  try {
    await api.post(`/jobs/${jobId}/cancel`)
    showAlert('success', '作业已取消')
    loadJobs()
  } catch (error) {
    showAlert('error', '取消作业失败: ' + error.message)
  }
}

function getStatusText(state) {
  const statusMap = {
    'RUNNING': '运行中',
    'FINISHED': '已完成',
    'FAILED': '失败',
    'CANCELED': '已取消',
    'CANCELLING': '取消中'
  }
  return statusMap[state] || state
}

function formatTimestamp(timestamp) {
  if (!timestamp) return '未知'
  return new Date(timestamp).toLocaleString('zh-CN')
}

function formatDuration(ms) {
  if (!ms) return '0秒'
  const seconds = Math.floor(ms / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)

  if (days > 0) return `${days}天 ${hours % 24}小时`
  if (hours > 0) return `${hours}小时 ${minutes % 60}分钟`
  if (minutes > 0) return `${minutes}分钟 ${seconds % 60}秒`
  return `${seconds}秒`
}

function showAlert(type, message) {
  alert.value = { show: true, type, message }
  setTimeout(() => {
    alert.value.show = false
  }, 5000)
}
</script>

<style scoped>
.jobs-grid {
  display: grid;
  gap: 16px;
}

.job-card {
  background: #fff;
  border-radius: 4px;
  padding: 20px 24px;
  box-shadow: 0 1px 1px rgba(9,30,66,0.25), 0 0 0 1px rgba(9,30,66,0.08);
  transition: box-shadow 0.15s;
}

.job-card:hover {
  box-shadow: 0 4px 8px -2px rgba(9,30,66,0.25), 0 0 0 1px rgba(9,30,66,0.08);
}

.job-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.job-name {
  font-size: 15px;
  font-weight: 600;
  color: #172B4D;
  margin-bottom: 4px;
}

.job-id {
  font-size: 11px;
  color: #97A0AF;
  font-family: monospace;
}

.status-badge {
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  flex-shrink: 0;
}

.status-running   { background: #E3FCEF; color: #00875A; }
.status-finished  { background: #DEEBFF; color: #0052CC; }
.status-failed    { background: #FFEBE6; color: #DE350B; }
.status-canceled  { background: #EBECF0; color: #5E6C84; }
.status-restarting{ background: #FFFAE6; color: #FF8B00; }

.job-metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 14px;
  padding-bottom: 14px;
  border-bottom: 1px solid #DFE1E6;
}

.metric-item {
  display: flex;
  flex-direction: column;
}

.metric-label {
  font-size: 11px;
  color: #5E6C84;
  margin-bottom: 3px;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.metric-value {
  font-size: 13px;
  color: #172B4D;
  font-weight: 500;
}

.job-tasks {
  margin-bottom: 14px;
}

.task-stats {
  display: flex;
  gap: 8px;
}

.task-stat {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 3px;
}

.task-stat.running  { background: #E3FCEF; color: #00875A; }
.task-stat.finished { background: #DEEBFF; color: #0052CC; }
.task-stat.failed   { background: #FFEBE6; color: #DE350B; }

.job-actions {
  display: flex;
  gap: 8px;
}

.modal-large {
  max-width: 800px;
}

.job-detail {
  padding: 0;
}

.detail-section {
  margin-bottom: 24px;
}

.detail-section h3 {
  font-size: 13px;
  font-weight: 700;
  color: #5E6C84;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 2px solid #0052CC;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}

.detail-item {
  display: flex;
  flex-direction: column;
}

.detail-label {
  font-size: 11px;
  color: #5E6C84;
  margin-bottom: 3px;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.detail-value {
  font-size: 13px;
  color: #172B4D;
  font-weight: 500;
}
</style>
