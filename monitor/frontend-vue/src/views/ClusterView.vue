<template>
  <div class="cluster-view">
    <div class="page-header">
      <h2>Flink 集群状态</h2>
      <p>查看 Flink 集群的健康状态和资源使用情况</p>
    </div>

    <div v-if="alert.show" :class="['alert', `alert-${alert.type}`]">
      {{ alert.message }}
    </div>

    <div class="actions mb-4">
      <button class="btn btn-secondary" @click="loadClusterInfo"><icon-refresh theme="outline" size="13" /> 刷新</button>
    </div>

    <div v-if="loading" class="loading">
      <div class="spinner"></div>
      <p>加载集群信息...</p>
    </div>

    <div v-else-if="!clusterInfo" class="empty-state">
      <div class="empty-icon"><icon-caution theme="outline" size="48" fill="#FF8B00" /></div>
      <p>无法连接到 Flink 集群</p>
      <p class="text-sm">请检查 Flink 服务是否正常运行</p>
    </div>

    <div v-else class="cluster-content">
      <!-- 集群概览 -->
      <div class="card mb-4">
        <h3 class="card-title">集群概览</h3>
        <div class="overview-grid">
          <div class="overview-item">
          <div class="overview-icon"><icon-monitor theme="outline" size="24" fill="#0052CC" /></div>
            <div class="overview-info">
              <div class="overview-label">TaskManager 数量</div>
              <div class="overview-value">{{ clusterInfo.taskmanagers || 0 }}</div>
            </div>
          </div>
          <div class="overview-item">
          <div class="overview-icon"><icon-chart-histogram theme="outline" size="24" fill="#0052CC" /></div>
            <div class="overview-info">
              <div class="overview-label">可用任务槽</div>
              <div class="overview-value">
                {{ clusterInfo['slots-available'] || 0 }} / {{ clusterInfo['slots-total'] || 0 }}
              </div>
            </div>
          </div>
          <div class="overview-item">
          <div class="overview-icon"><icon-rocket theme="outline" size="24" fill="#0052CC" /></div>
            <div class="overview-info">
              <div class="overview-label">运行中的作业</div>
              <div class="overview-value">{{ clusterInfo['jobs-running'] || 0 }}</div>
            </div>
          </div>
          <div class="overview-item">
          <div class="overview-icon"><icon-check-one theme="outline" size="24" fill="#0052CC" /></div>
            <div class="overview-info">
              <div class="overview-label">已完成的作业</div>
              <div class="overview-value">{{ clusterInfo['jobs-finished'] || 0 }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- Flink 版本信息 -->
      <div class="card mb-4">
        <h3 class="card-title">版本信息</h3>
        <div class="info-grid">
          <div class="info-item">
            <span class="info-label">Flink 版本</span>
            <span class="info-value">{{ clusterInfo['flink-version'] || '未知' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">提交时间</span>
            <span class="info-value">{{ formatTimestamp(clusterInfo['flink-commit']) }}</span>
          </div>
        </div>
      </div>

      <!-- TaskManager 列表 -->
      <div class="card">
        <h3 class="card-title">TaskManager 列表</h3>
        <div v-if="taskManagers.length === 0" class="empty-state-small">
          <p>暂无 TaskManager 信息</p>
        </div>
        <div v-else class="taskmanager-list">
          <div v-for="tm in taskManagers" :key="tm.id" class="taskmanager-item">
            <div class="tm-header">
              <div>
                <h4 class="tm-id">{{ tm.id }}</h4>
                <p class="tm-path">{{ tm.path }}</p>
              </div>
              <span :class="['status-badge', tm.status === 'RUNNING' ? 'status-running' : 'status-stopped']">
                {{ tm.status === 'RUNNING' ? '运行中' : '已停止' }}
              </span>
            </div>
            <div class="tm-metrics">
              <div class="tm-metric">
                <span class="tm-metric-label">任务槽</span>
                <span class="tm-metric-value">{{ tm.slotsNumber || 0 }}</span>
              </div>
              <div class="tm-metric">
                <span class="tm-metric-label">空闲槽</span>
                <span class="tm-metric-value">{{ tm.freeSlots || 0 }}</span>
              </div>
              <div class="tm-metric">
                <span class="tm-metric-label">CPU 核心</span>
                <span class="tm-metric-value">{{ tm.hardware?.cpuCores || 0 }}</span>
              </div>
              <div class="tm-metric">
                <span class="tm-metric-label">物理内存</span>
                <span class="tm-metric-value">{{ formatBytes(tm.hardware?.physicalMemory) }}</span>
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

const clusterInfo = ref(null)
const taskManagers = ref([])
const loading = ref(false)
const alert = ref({ show: false, type: '', message: '' })
let refreshInterval = null

onMounted(() => {
  loadClusterInfo()
  // 每 10 秒自动刷新
  refreshInterval = setInterval(loadClusterInfo, 10000)
})

onUnmounted(() => {
  if (refreshInterval) {
    clearInterval(refreshInterval)
  }
})

async function loadClusterInfo() {
  if (loading.value) return
  
  loading.value = true
  try {
    const result = await api.get('/cluster/overview')
    clusterInfo.value = result.data
    
    // 加载 TaskManager 列表
    const tmResult = await api.get('/cluster/taskmanagers')
    taskManagers.value = tmResult.data?.taskmanagers || []
  } catch (error) {
    showAlert('error', '加载集群信息失败: ' + error.message)
    clusterInfo.value = null
  } finally {
    loading.value = false
  }
}

function formatTimestamp(timestamp) {
  if (!timestamp) return '未知'
  return new Date(timestamp).toLocaleString('zh-CN')
}

function formatBytes(bytes) {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}

function showAlert(type, message) {
  alert.value = { show: true, type, message }
  setTimeout(() => {
    alert.value.show = false
  }, 5000)
}
</script>

<style scoped>
.cluster-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.overview-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: #F4F5F7;
  border-radius: 4px;
  border-left: 3px solid #0052CC;
}

.overview-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
}

.overview-label {
  font-size: 11px;
  color: #5E6C84;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-bottom: 4px;
}

.overview-value {
  font-size: 22px;
  font-weight: 700;
  color: #172B4D;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  padding: 10px 14px;
  background: #F4F5F7;
  border-radius: 4px;
}

.info-label {
  font-size: 13px;
  color: #5E6C84;
}

.info-value {
  font-size: 13px;
  color: #172B4D;
  font-weight: 600;
}

.taskmanager-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.taskmanager-item {
  padding: 16px;
  background: #F4F5F7;
  border-radius: 4px;
  border: 1px solid #DFE1E6;
}

.tm-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.tm-id {
  font-size: 12px;
  font-weight: 600;
  color: #172B4D;
  font-family: monospace;
  margin-bottom: 3px;
}

.tm-path {
  font-size: 11px;
  color: #97A0AF;
}

.status-badge {
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
}

.status-running { background: #E3FCEF; color: #00875A; }
.status-stopped { background: #FFEBE6; color: #DE350B; }

.tm-metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.tm-metric {
  display: flex;
  flex-direction: column;
}

.tm-metric-label {
  font-size: 11px;
  color: #5E6C84;
  margin-bottom: 3px;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.tm-metric-value {
  font-size: 13px;
  color: #172B4D;
  font-weight: 600;
}

.empty-state-small {
  text-align: center;
  padding: 32px;
  color: #97A0AF;
}

.card-title {
  font-size: 13px;
  font-weight: 700;
  color: #5E6C84;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-bottom: 16px;
  padding-bottom: 10px;
  border-bottom: 2px solid #0052CC;
}
</style>
