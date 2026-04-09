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
          <div class="overview-item">
            <div class="overview-icon"><icon-close theme="outline" size="24" fill="#DE350B" /></div>
            <div class="overview-info">
              <div class="overview-label">失败的作业</div>
              <div class="overview-value">{{ clusterInfo['jobs-failed'] || 0 }}</div>
            </div>
          </div>
          <div class="overview-item">
            <div class="overview-icon"><icon-pause theme="outline" size="24" fill="#FF8B00" /></div>
            <div class="overview-info">
              <div class="overview-label">取消的作业</div>
              <div class="overview-value">{{ clusterInfo['jobs-cancelled'] || 0 }}</div>
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

      <!-- JobManager 信息 -->
      <div class="card mb-4">
        <h3 class="card-title">JobManager 信息</h3>
        <div v-if="jobManagerConfig.length === 0" class="empty-state-small">
          <p>暂无 JobManager 配置信息</p>
        </div>
        <div v-else class="config-list">
          <div class="config-section">
            <h4 class="config-section-title">高可用配置</h4>
            <div class="config-items">
              <div v-for="item in getConfigByPrefix('high-availability')" :key="item.key" class="config-item">
                <span class="config-key">{{ item.key }}</span>
                <span class="config-value">{{ item.value }}</span>
              </div>
            </div>
          </div>
          <div class="config-section">
            <h4 class="config-section-title">Checkpoint 配置</h4>
            <div class="config-items">
              <div v-for="item in getConfigByPrefix('state.checkpoints')" :key="item.key" class="config-item">
                <span class="config-key">{{ item.key }}</span>
                <span class="config-value">{{ item.value }}</span>
              </div>
            </div>
          </div>
          <div class="config-section">
            <h4 class="config-section-title">内存配置</h4>
            <div class="config-items">
              <div v-for="item in getConfigByPrefix('jobmanager.memory')" :key="item.key" class="config-item">
                <span class="config-key">{{ item.key }}</span>
                <span class="config-value">{{ item.value }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 运行中的作业 -->
      <div class="card mb-4">
        <h3 class="card-title">运行中的作业</h3>
        <div v-if="runningJobs.length === 0" class="empty-state-small">
          <p>暂无运行中的作业</p>
        </div>
        <div v-else class="jobs-list">
          <div v-for="job in runningJobs" :key="job.jid" class="job-item">
            <div class="job-header">
              <div>
                <h4 class="job-name">{{ job.name }}</h4>
                <p class="job-id">{{ job.jid }}</p>
              </div>
              <span class="status-badge status-running">运行中</span>
            </div>
            <div class="job-metrics">
              <div class="job-metric">
                <span class="job-metric-label">开始时间</span>
                <span class="job-metric-value">{{ formatTimestamp(job['start-time']) }}</span>
              </div>
              <div class="job-metric">
                <span class="job-metric-label">运行时长</span>
                <span class="job-metric-value">{{ formatDuration(job.duration) }}</span>
              </div>
              <div class="job-metric">
                <span class="job-metric-label">任务数</span>
                <span class="job-metric-value">{{ job.tasks?.total || 0 }}</span>
              </div>
            </div>
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
              <div class="tm-metric">
                <span class="tm-metric-label">托管内存</span>
                <span class="tm-metric-value">{{ formatBytes(tm.hardware?.managedMemory) }}</span>
              </div>
              <div class="tm-metric">
                <span class="tm-metric-label">数据端口</span>
                <span class="tm-metric-value">{{ tm.dataPort || 'N/A' }}</span>
              </div>
            </div>
            <div class="tm-memory-details">
              <h5 class="tm-memory-title">内存配置详情</h5>
              <div class="tm-memory-grid">
                <div class="tm-memory-item">
                  <span class="tm-memory-label">Task 堆内存</span>
                  <span class="tm-memory-value">{{ formatBytes(tm.memoryConfiguration?.taskHeap) }}</span>
                </div>
                <div class="tm-memory-item">
                  <span class="tm-memory-label">Framework 堆内存</span>
                  <span class="tm-memory-value">{{ formatBytes(tm.memoryConfiguration?.frameworkHeap) }}</span>
                </div>
                <div class="tm-memory-item">
                  <span class="tm-memory-label">网络内存</span>
                  <span class="tm-memory-value">{{ formatBytes(tm.memoryConfiguration?.networkMemory) }}</span>
                </div>
                <div class="tm-memory-item">
                  <span class="tm-memory-label">托管内存</span>
                  <span class="tm-memory-value">{{ formatBytes(tm.memoryConfiguration?.managedMemory) }}</span>
                </div>
                <div class="tm-memory-item">
                  <span class="tm-memory-label">JVM Metaspace</span>
                  <span class="tm-memory-value">{{ formatBytes(tm.memoryConfiguration?.jvmMetaspace) }}</span>
                </div>
                <div class="tm-memory-item">
                  <span class="tm-memory-label">总进程内存</span>
                  <span class="tm-memory-value">{{ formatBytes(tm.memoryConfiguration?.totalProcessMemory) }}</span>
                </div>
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
const jobManagerConfig = ref([])
const runningJobs = ref([])
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
    // 加载集群概览
    const result = await api.get('/cluster/overview')
    clusterInfo.value = result.data
    
    // 加载 TaskManager 列表
    const tmResult = await api.get('/cluster/taskmanagers')
    // 后端返回的数据直接在 data 数组中，不是 data.taskmanagers
    const tmList = tmResult.data || []
    // 为每个 TaskManager 添加状态（基于心跳时间判断）
    // timeSinceLastHeartbeat 是一个时间戳，需要计算与当前时间的差值
    const now = Date.now()
    taskManagers.value = tmList.map(tm => ({
      ...tm,
      status: (tm.timeSinceLastHeartbeat && (now - tm.timeSinceLastHeartbeat) < 60000) ? 'RUNNING' : 'STOPPED'
    }))
    
    // 加载 JobManager 配置
    const jmResult = await api.get('/cluster/jobmanagers')
    jobManagerConfig.value = jmResult.data || []
    
    // 加载运行中的作业
    const jobsResult = await api.get('/jobs')
    const allJobs = jobsResult.data || []
    runningJobs.value = allJobs.filter(job => job.state === 'RUNNING')
  } catch (error) {
    showAlert('error', '加载集群信息失败: ' + error.message)
    clusterInfo.value = null
  } finally {
    loading.value = false
  }
}

function getConfigByPrefix(prefix) {
  return jobManagerConfig.value.filter(item => item.key && item.key.startsWith(prefix))
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
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
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
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 16px;
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

.tm-memory-details {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #DFE1E6;
}

.tm-memory-title {
  font-size: 11px;
  font-weight: 600;
  color: #5E6C84;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  margin-bottom: 8px;
}

.tm-memory-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.tm-memory-item {
  display: flex;
  flex-direction: column;
  padding: 6px 8px;
  background: #FFFFFF;
  border-radius: 3px;
  border: 1px solid #DFE1E6;
}

.tm-memory-label {
  font-size: 10px;
  color: #5E6C84;
  margin-bottom: 2px;
}

.tm-memory-value {
  font-size: 11px;
  color: #172B4D;
  font-weight: 600;
  font-family: monospace;
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

.config-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.config-section-title {
  font-size: 12px;
  font-weight: 600;
  color: #172B4D;
  margin-bottom: 10px;
  padding-left: 8px;
  border-left: 3px solid #0052CC;
}

.config-items {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.config-item {
  display: flex;
  justify-content: space-between;
  padding: 8px 12px;
  background: #F4F5F7;
  border-radius: 3px;
  font-size: 12px;
}

.config-key {
  color: #5E6C84;
  font-family: monospace;
  flex: 1;
  word-break: break-all;
}

.config-value {
  color: #172B4D;
  font-weight: 600;
  font-family: monospace;
  margin-left: 12px;
  text-align: right;
}

.jobs-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.job-item {
  padding: 16px;
  background: #F4F5F7;
  border-radius: 4px;
  border: 1px solid #DFE1E6;
}

.job-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.job-name {
  font-size: 13px;
  font-weight: 600;
  color: #172B4D;
  margin-bottom: 3px;
}

.job-id {
  font-size: 11px;
  color: #97A0AF;
  font-family: monospace;
}

.job-metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.job-metric {
  display: flex;
  flex-direction: column;
}

.job-metric-label {
  font-size: 11px;
  color: #5E6C84;
  margin-bottom: 3px;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.job-metric-value {
  font-size: 12px;
  color: #172B4D;
  font-weight: 600;
}
</style>
