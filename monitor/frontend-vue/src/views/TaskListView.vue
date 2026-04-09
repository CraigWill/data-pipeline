<template>
  <div class="task-list-view">
    <!-- Toast 通知 -->
    <transition name="toast">
      <div v-if="toast.show" :class="['toast', `toast-${toast.type}`]">
        <svg v-if="toast.type === 'success'" width="20" height="20" viewBox="0 0 20 20" fill="none">
          <circle cx="10" cy="10" r="9" stroke="currentColor" stroke-width="2"/>
          <path d="M6 10L9 13L14 7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <svg v-else-if="toast.type === 'error'" width="20" height="20" viewBox="0 0 20 20" fill="none">
          <circle cx="10" cy="10" r="9" stroke="currentColor" stroke-width="2"/>
          <path d="M10 6V11M10 14V15" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
        </svg>
        <svg v-else width="20" height="20" viewBox="0 0 20 20" fill="none">
          <circle cx="10" cy="10" r="9" stroke="currentColor" stroke-width="2"/>
          <path d="M10 6V11M10 14H10.01" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
        </svg>
        <span>{{ toast.message }}</span>
      </div>
    </transition>

    <!-- 任务详情模态框 -->
    <transition name="modal">
      <div v-if="detailModal.show" class="modal-overlay" @click="closeDetailModal">
        <div class="modal-content" @click.stop>
          <div class="modal-header">
            <h3>任务详情</h3>
            <button class="modal-close" @click="closeDetailModal">
              <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
                <path d="M5 5L15 15M15 5L5 15" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <div v-if="detailModal.loading" class="modal-loading">
              <div class="spinner"></div>
              <p>加载中...</p>
            </div>
            <div v-else-if="detailModal.error" class="modal-error">
              <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
                <circle cx="24" cy="24" r="20" stroke="#DE350B" stroke-width="2"/>
                <path d="M24 16V26M24 30V32" stroke="#DE350B" stroke-width="2" stroke-linecap="round"/>
              </svg>
              <p>{{ detailModal.error }}</p>
            </div>
            <div v-else-if="detailModal.data" class="detail-content">
              <div class="detail-section">
                <h4>基本信息</h4>
                <div class="detail-grid">
                  <div class="detail-item">
                    <span class="detail-label">任务名称</span>
                    <span class="detail-value">{{ detailModal.data.name }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">任务 ID</span>
                    <span class="detail-value detail-value-code">{{ detailModal.data.id }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">创建时间</span>
                    <span class="detail-value">{{ formatDate(detailModal.data.created) }}</span>
                  </div>
                </div>
              </div>

              <div class="detail-section">
                <h4>数据库配置</h4>
                <div class="detail-grid">
                  <div class="detail-item">
                    <span class="detail-label">数据源</span>
                    <span class="detail-value">{{ detailModal.data.datasource_name || 'N/A' }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">主机地址</span>
                    <span class="detail-value detail-value-code">{{ detailModal.data.database?.host || 'N/A' }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">端口</span>
                    <span class="detail-value">{{ detailModal.data.database?.port || 'N/A' }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">SID</span>
                    <span class="detail-value detail-value-code">{{ detailModal.data.database?.sid || 'N/A' }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">Schema</span>
                    <span class="detail-value detail-value-highlight">{{ detailModal.data.database?.schema || 'N/A' }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">用户名</span>
                    <span class="detail-value">{{ detailModal.data.database?.username || 'N/A' }}</span>
                  </div>
                </div>
              </div>

              <div class="detail-section">
                <h4>监控表列表</h4>
                <div v-if="detailModal.data.tables && detailModal.data.tables.length > 0" class="tables-list">
                  <div v-for="(table, index) in detailModal.data.tables" :key="index" class="table-tag">
                    {{ table }}
                  </div>
                </div>
                <p v-else class="detail-empty">未配置监控表</p>
              </div>

              <div class="detail-section">
                <h4>任务配置</h4>
                <div class="detail-grid">
                  <div class="detail-item">
                    <span class="detail-label">输出路径</span>
                    <span class="detail-value detail-value-code">{{ detailModal.data.output_path || 'N/A' }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">并行度</span>
                    <span class="detail-value">{{ detailModal.data.parallelism || 'N/A' }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">分片大小</span>
                    <span class="detail-value">{{ detailModal.data.split_size || 'N/A' }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </transition>

    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-content">
        <div>
          <h2>CDC 任务管理</h2>
          <p class="subtitle">管理和监控数据变更捕获任务</p>
        </div>
        <div class="header-actions">
          <!-- 视图切换 -->
          <div class="view-toggle">
            <button 
              :class="['toggle-btn', { active: viewMode === 'card' }]" 
              @click="viewMode = 'card'"
              title="卡片视图"
            >
              <svg width="18" height="18" viewBox="0 0 18 18" fill="none">
                <rect x="2" y="2" width="6" height="6" rx="1" stroke="currentColor" stroke-width="1.5"/>
                <rect x="10" y="2" width="6" height="6" rx="1" stroke="currentColor" stroke-width="1.5"/>
                <rect x="2" y="10" width="6" height="6" rx="1" stroke="currentColor" stroke-width="1.5"/>
                <rect x="10" y="10" width="6" height="6" rx="1" stroke="currentColor" stroke-width="1.5"/>
              </svg>
            </button>
            <button 
              :class="['toggle-btn', { active: viewMode === 'list' }]" 
              @click="viewMode = 'list'"
              title="列表视图"
            >
              <svg width="18" height="18" viewBox="0 0 18 18" fill="none">
                <path d="M2 4H16M2 9H16M2 14H16" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
              </svg>
            </button>
          </div>
          <button class="btn btn-primary" @click="$router.push('/tasks/create')">
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none" style="margin-right: 6px;">
              <path d="M8 3V13M3 8H13" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
            创建任务
          </button>
        </div>
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-state">
      <div class="spinner"></div>
      <p>加载任务列表...</p>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="error" class="error-state">
      <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
        <circle cx="24" cy="24" r="20" stroke="#DE350B" stroke-width="2"/>
        <path d="M24 16V26M24 30V32" stroke="#DE350B" stroke-width="2" stroke-linecap="round"/>
      </svg>
      <h3>加载失败</h3>
      <p>{{ error }}</p>
      <button class="btn btn-secondary" @click="loadTasks">重试</button>
    </div>

    <!-- 空状态 -->
    <div v-else-if="tasks.length === 0" class="empty-state">
      <svg width="64" height="64" viewBox="0 0 64 64" fill="none">
        <rect x="12" y="16" width="40" height="36" rx="2" stroke="#97A0AF" stroke-width="2"/>
        <path d="M20 28H44M20 36H36" stroke="#97A0AF" stroke-width="2" stroke-linecap="round"/>
      </svg>
      <h3>暂无任务</h3>
      <p>创建您的第一个 CDC 任务开始捕获数据变更</p>
      <button class="btn btn-primary" @click="$router.push('/tasks/create')">
        创建任务
      </button>
    </div>

    <!-- 任务卡片列表 -->
    <div v-else-if="viewMode === 'card'" class="tasks-grid">
      <div v-for="task in tasks" :key="task.id" class="task-card">
        <!-- 卡片头部 -->
        <div class="card-header">
          <div class="task-info">
            <h3 class="task-name">{{ task.name }}</h3>
            <p class="task-description" v-if="task.description">{{ task.description }}</p>
          </div>
        </div>

        <!-- 卡片内容 -->
        <div class="card-body">
          <div class="info-row">
            <div class="info-item">
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none" class="info-icon">
                <rect x="2" y="3" width="12" height="10" rx="1" stroke="currentColor" stroke-width="1.5"/>
                <path d="M5 6H11M5 9H9" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
              </svg>
              <div>
                <span class="info-label">数据源</span>
                <span class="info-value">{{ task.database || 'Unknown' }}</span>
              </div>
            </div>
            <div class="info-item">
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none" class="info-icon">
                <rect x="3" y="3" width="4" height="4" rx="0.5" stroke="currentColor" stroke-width="1.5"/>
                <rect x="9" y="3" width="4" height="4" rx="0.5" stroke="currentColor" stroke-width="1.5"/>
                <rect x="3" y="9" width="4" height="4" rx="0.5" stroke="currentColor" stroke-width="1.5"/>
                <rect x="9" y="9" width="4" height="4" rx="0.5" stroke="currentColor" stroke-width="1.5"/>
              </svg>
              <div>
                <span class="info-label">表数量</span>
                <span class="info-value">{{ task.tables || 0 }}</span>
              </div>
            </div>
          </div>
          <div class="info-row">
            <div class="info-item">
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none" class="info-icon">
                <circle cx="8" cy="8" r="6" stroke="currentColor" stroke-width="1.5"/>
                <path d="M8 5V8L10 10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
              </svg>
              <div>
                <span class="info-label">创建时间</span>
                <span class="info-value">{{ formatDate(task.created) }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 卡片底部操作 -->
        <div class="card-footer">
          <button 
            class="btn-action btn-action-primary" 
            @click="submitTask(task.id)"
            :disabled="submitting === task.id"
          >
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none" v-if="submitting !== task.id">
              <path d="M2 7L12 7M12 7L8 3M12 7L8 11" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            <div v-else class="btn-spinner"></div>
            {{ submitting === task.id ? '提交中' : '提交任务' }}
          </button>
          <button class="btn-action btn-action-secondary" @click="viewDetail(task.id)">
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <circle cx="7" cy="7" r="2.5" stroke="currentColor" stroke-width="1.5"/>
              <path d="M1 7C1 7 3 3 7 3C11 3 13 7 13 7C13 7 11 11 7 11C3 11 1 7 1 7Z" stroke="currentColor" stroke-width="1.5"/>
            </svg>
            详情
          </button>
          <button class="btn-action btn-action-danger" @click="deleteTask(task.id)">
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path d="M2 4H12M5 4V2.5C5 2.22 5.22 2 5.5 2H8.5C8.78 2 9 2.22 9 2.5V4M3.5 4L4 11.5C4 11.78 4.22 12 4.5 12H9.5C9.78 12 10 11.78 10 11.5L10.5 4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
            </svg>
            删除
          </button>
        </div>
      </div>
    </div>

    <!-- 任务列表视图 -->
    <div v-else class="tasks-table-container">
      <table class="tasks-table">
        <thead>
          <tr>
            <th>任务名称</th>
            <th>数据源</th>
            <th>Schema</th>
            <th>表数量</th>
            <th>创建时间</th>
            <th class="actions-column">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="task in tasks" :key="task.id" class="table-row">
            <td>
              <div class="task-name-cell">
                <span class="task-name-text">{{ task.name }}</span>
                <span v-if="task.description" class="task-description-text">{{ task.description }}</span>
              </div>
            </td>
            <td>
              <span class="cell-value">{{ task.database || 'Unknown' }}</span>
            </td>
            <td>
              <span class="cell-value cell-value-highlight">{{ task.schema || 'N/A' }}</span>
            </td>
            <td>
              <span class="cell-value">{{ task.tables || 0 }}</span>
            </td>
            <td>
              <span class="cell-value cell-value-secondary">{{ formatDate(task.created) }}</span>
            </td>
            <td class="actions-cell">
              <div class="table-actions">
                <button 
                  class="table-btn table-btn-primary" 
                  @click="submitTask(task.id)"
                  :disabled="submitting === task.id"
                  title="提交任务"
                >
                  <svg width="14" height="14" viewBox="0 0 14 14" fill="none" v-if="submitting !== task.id">
                    <path d="M2 7L12 7M12 7L8 3M12 7L8 11" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                  <div v-else class="btn-spinner"></div>
                </button>
                <button 
                  class="table-btn table-btn-secondary" 
                  @click="viewDetail(task.id)"
                  title="查看详情"
                >
                  <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                    <circle cx="7" cy="7" r="2.5" stroke="currentColor" stroke-width="1.5"/>
                    <path d="M1 7C1 7 3 3 7 3C11 3 13 7 13 7C13 7 11 11 7 11C3 11 1 7 1 7Z" stroke="currentColor" stroke-width="1.5"/>
                  </svg>
                </button>
                <button 
                  class="table-btn table-btn-danger" 
                  @click="deleteTask(task.id)"
                  title="删除任务"
                >
                  <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                    <path d="M2 4H12M5 4V2.5C5 2.22 5.22 2 5.5 2H8.5C8.78 2 9 2.22 9 2.5V4M3.5 4L4 11.5C4 11.78 4.22 12 4.5 12H9.5C9.78 12 10 11.78 10 11.5L10.5 4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                  </svg>
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { taskAPI } from '../api'

const router = useRouter()
const tasks = ref([])
const loading = ref(false)
const error = ref(null)
const submitting = ref(null)
const viewMode = ref('card') // 'card' 或 'list'

// Toast 通知
const toast = ref({
  show: false,
  type: 'info', // success, error, info
  message: ''
})

// 详情模态框
const detailModal = ref({
  show: false,
  loading: false,
  error: null,
  data: null
})

const showToast = (message, type = 'info') => {
  toast.value = { show: true, message, type }
  setTimeout(() => {
    toast.value.show = false
  }, 3000)
}

const loadTasks = async () => {
  loading.value = true
  error.value = null

  try {
    const data = await taskAPI.list()
    if (data.success) {
      tasks.value = data.data
    } else {
      error.value = data.error
    }
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

const submitTask = async (taskId) => {
  if (!confirm('确定要提交这个任务到 Flink 集群吗？')) return

  submitting.value = taskId
  try {
    const data = await taskAPI.submit(taskId)
    if (data.success) {
      showToast(`任务已提交！Job ID: ${data.data.job_id}`, 'success')
      loadTasks()
    } else {
      showToast(`提交失败: ${data.error}`, 'error')
    }
  } catch (err) {
    showToast(`提交失败: ${err.message}`, 'error')
  } finally {
    submitting.value = null
  }
}

const viewDetail = async (taskId) => {
  detailModal.value = {
    show: true,
    loading: true,
    error: null,
    data: null
  }

  try {
    const data = await taskAPI.getDetail(taskId)
    if (data.success) {
      detailModal.value.data = data.data
      detailModal.value.loading = false
    } else {
      detailModal.value.error = data.error || '加载失败'
      detailModal.value.loading = false
    }
  } catch (err) {
    detailModal.value.error = err.message || '加载失败'
    detailModal.value.loading = false
  }
}

const closeDetailModal = () => {
  detailModal.value.show = false
}

const deleteTask = async (taskId) => {
  if (!confirm('确定要删除这个任务吗？此操作不可恢复！')) return

  try {
    const data = await taskAPI.delete(taskId)
    if (data.success) {
      showToast('任务已删除', 'success')
      loadTasks()
    } else {
      showToast(`删除失败: ${data.error}`, 'error')
    }
  } catch (err) {
    showToast(`删除失败: ${err.message}`, 'error')
  }
}

const formatDate = (dateStr) => {
  if (!dateStr) return 'N/A'
  return new Date(dateStr).toLocaleString('zh-CN')
}

onMounted(() => {
  loadTasks()
})
</script>

<style scoped>
.task-list-view {
  padding: 24px;
  max-width: 1400px;
  margin: 0 auto;
}

/* Toast 通知 */
.toast {
  position: fixed;
  top: 24px;
  right: 24px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15), 0 0 0 1px rgba(0,0,0,0.05);
  z-index: 10000;
  min-width: 300px;
  max-width: 500px;
}

.toast svg {
  flex-shrink: 0;
}

.toast span {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
  color: #172B4D;
}

.toast-success {
  border-left: 4px solid #00875A;
}

.toast-success svg {
  color: #00875A;
}

.toast-error {
  border-left: 4px solid #DE350B;
}

.toast-error svg {
  color: #DE350B;
}

.toast-info {
  border-left: 4px solid #0052CC;
}

.toast-info svg {
  color: #0052CC;
}

.toast-enter-active, .toast-leave-active {
  transition: all 0.3s ease;
}

.toast-enter-from {
  opacity: 0;
  transform: translateX(100px);
}

.toast-leave-to {
  opacity: 0;
  transform: translateX(100px);
}

/* 模态框 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(9, 30, 66, 0.54);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  padding: 20px;
}

.modal-content {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(9,30,66,0.25);
  max-width: 800px;
  width: 100%;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px 24px 20px 24px;
  border-bottom: 1px solid #DFE1E6;
}

.modal-header h3 {
  font-size: 20px;
  font-weight: 600;
  color: #172B4D;
  margin: 0;
}

.modal-close {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: #5E6C84;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.15s ease;
}

.modal-close:hover {
  background: #F4F5F7;
  color: #172B4D;
}

.modal-body {
  padding: 24px;
  overflow-y: auto;
  flex: 1;
}

.modal-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
}

.modal-loading .spinner {
  width: 40px;
  height: 40px;
  border: 3px solid #DFE1E6;
  border-top-color: #0052CC;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 16px;
}

.modal-loading p {
  color: #5E6C84;
  font-size: 14px;
  margin: 0;
}

.modal-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
}

.modal-error svg {
  margin-bottom: 16px;
}

.modal-error p {
  color: #5E6C84;
  font-size: 14px;
  margin: 0;
}

.detail-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.detail-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-section h4 {
  font-size: 14px;
  font-weight: 600;
  color: #5E6C84;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin: 0;
  padding-bottom: 8px;
  border-bottom: 2px solid #DFE1E6;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 16px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.detail-label {
  font-size: 12px;
  font-weight: 600;
  color: #5E6C84;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.detail-value {
  font-size: 14px;
  color: #172B4D;
  font-weight: 500;
  word-break: break-word;
}

.detail-value-code {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  background: #F4F5F7;
  padding: 6px 10px;
  border-radius: 4px;
  font-size: 13px;
  color: #172B4D;
}

.detail-value-highlight {
  color: #0052CC;
  font-weight: 600;
  font-size: 15px;
}

.tables-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.table-tag {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  background: #DEEBFF;
  color: #0747A6;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 500;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
}

.detail-empty {
  color: #5E6C84;
  font-size: 14px;
  font-style: italic;
  margin: 0;
}

.modal-enter-active, .modal-leave-active {
  transition: opacity 0.3s ease;
}

.modal-enter-active .modal-content,
.modal-leave-active .modal-content {
  transition: transform 0.3s ease;
}

.modal-enter-from, .modal-leave-to {
  opacity: 0;
}

.modal-enter-from .modal-content {
  transform: scale(0.9);
}

.modal-leave-to .modal-content {
  transform: scale(0.9);
}

/* 页面头部 */
.page-header {
  margin-bottom: 32px;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 视图切换 */
.view-toggle {
  display: flex;
  background: #fff;
  border-radius: 6px;
  padding: 4px;
  box-shadow: 0 1px 3px rgba(9,30,66,0.12);
  border: 1px solid #DFE1E6;
}

.toggle-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  color: #5E6C84;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.15s ease;
}

.toggle-btn:hover {
  background: #F4F5F7;
  color: #172B4D;
}

.toggle-btn.active {
  background: #0052CC;
  color: #fff;
}

.toggle-btn.active:hover {
  background: #0747A6;
}

.page-header h2 {
  font-size: 28px;
  font-weight: 700;
  color: #172B4D;
  margin: 0 0 4px 0;
}

.subtitle {
  font-size: 14px;
  color: #5E6C84;
  margin: 0;
}

/* 加载状态 */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(9,30,66,0.12);
}

.spinner {
  width: 40px;
  height: 40px;
  border: 3px solid #DFE1E6;
  border-top-color: #0052CC;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 16px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.loading-state p {
  color: #5E6C84;
  font-size: 14px;
  margin: 0;
}

/* 错误状态 */
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(9,30,66,0.12);
}

.error-state svg {
  margin-bottom: 16px;
}

.error-state h3 {
  font-size: 18px;
  font-weight: 600;
  color: #172B4D;
  margin: 0 0 8px 0;
}

.error-state p {
  color: #5E6C84;
  font-size: 14px;
  margin: 0 0 24px 0;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(9,30,66,0.12);
}

.empty-state svg {
  margin-bottom: 24px;
  opacity: 0.6;
}

.empty-state h3 {
  font-size: 18px;
  font-weight: 600;
  color: #172B4D;
  margin: 0 0 8px 0;
}

.empty-state p {
  color: #5E6C84;
  font-size: 14px;
  margin: 0 0 24px 0;
  max-width: 400px;
  text-align: center;
}

/* 任务卡片网格 */
.tasks-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 20px;
}

/* 任务卡片 */
.task-card {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(9,30,66,0.12), 0 0 0 1px rgba(9,30,66,0.08);
  transition: all 0.2s ease;
  display: flex;
  flex-direction: column;
}

.task-card:hover {
  box-shadow: 0 4px 8px rgba(9,30,66,0.15), 0 0 0 1px rgba(9,30,66,0.08);
  transform: translateY(-2px);
}

/* 卡片头部 */
.card-header {
  padding: 20px 20px 16px 20px;
  border-bottom: 1px solid #DFE1E6;
}

.task-info {
  flex: 1;
  min-width: 0;
}

.task-name {
  font-size: 16px;
  font-weight: 600;
  color: #172B4D;
  margin: 0 0 4px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-description {
  font-size: 13px;
  color: #5E6C84;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 卡片内容 */
.card-body {
  padding: 16px 20px;
  flex: 1;
}

.info-row {
  display: flex;
  gap: 20px;
  margin-bottom: 12px;
}

.info-row:last-child {
  margin-bottom: 0;
}

.info-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.info-icon {
  color: #5E6C84;
  flex-shrink: 0;
  margin-top: 2px;
}

.info-item > div {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.info-label {
  font-size: 11px;
  color: #5E6C84;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  font-weight: 600;
}

.info-value {
  font-size: 14px;
  color: #172B4D;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 卡片底部 */
.card-footer {
  padding: 12px 20px;
  border-top: 1px solid #DFE1E6;
  display: flex;
  gap: 8px;
  background: #FAFBFC;
  border-radius: 0 0 8px 8px;
}

/* 操作按钮 */
.btn-action {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  border-radius: 4px;
  border: none;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-action:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-action-primary {
  background: #0052CC;
  color: #fff;
}

.btn-action-primary:hover:not(:disabled) {
  background: #0747A6;
}

.btn-action-secondary {
  background: #fff;
  color: #42526E;
  border: 1px solid #DFE1E6;
}

.btn-action-secondary:hover:not(:disabled) {
  background: #F4F5F7;
}

.btn-action-danger {
  background: #fff;
  color: #DE350B;
  border: 1px solid #DFE1E6;
}

.btn-action-danger:hover:not(:disabled) {
  background: #FFEBE6;
  border-color: #FFBDAD;
}

.btn-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

/* 列表视图 */
.tasks-table-container {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(9,30,66,0.12), 0 0 0 1px rgba(9,30,66,0.08);
  overflow: hidden;
}

.tasks-table {
  width: 100%;
  border-collapse: collapse;
}

.tasks-table thead {
  background: #F4F5F7;
  border-bottom: 2px solid #DFE1E6;
}

.tasks-table th {
  padding: 16px 20px;
  text-align: left;
  font-size: 12px;
  font-weight: 600;
  color: #5E6C84;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.tasks-table th.actions-column {
  text-align: center;
  width: 140px;
}

.tasks-table tbody tr {
  border-bottom: 1px solid #DFE1E6;
  transition: background 0.15s ease;
}

.tasks-table tbody tr:last-child {
  border-bottom: none;
}

.tasks-table tbody tr:hover {
  background: #F4F5F7;
}

.tasks-table td {
  padding: 16px 20px;
  font-size: 14px;
  color: #172B4D;
}

.task-name-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.task-name-text {
  font-weight: 600;
  color: #172B4D;
}

.task-description-text {
  font-size: 13px;
  color: #5E6C84;
}

.cell-value {
  display: inline-block;
}

.cell-value-highlight {
  color: #0052CC;
  font-weight: 600;
}

.cell-value-secondary {
  color: #5E6C84;
  font-size: 13px;
}

.actions-cell {
  text-align: center;
}

.table-actions {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  justify-content: center;
}

.table-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.table-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.table-btn-primary {
  background: #0052CC;
  color: #fff;
}

.table-btn-primary:hover:not(:disabled) {
  background: #0747A6;
}

.table-btn-secondary {
  background: #fff;
  color: #42526E;
  border: 1px solid #DFE1E6;
}

.table-btn-secondary:hover:not(:disabled) {
  background: #F4F5F7;
}

.table-btn-danger {
  background: #fff;
  color: #DE350B;
  border: 1px solid #DFE1E6;
}

.table-btn-danger:hover:not(:disabled) {
  background: #FFEBE6;
  border-color: #FFBDAD;
}

/* 响应式 */
@media (max-width: 768px) {
  .tasks-grid {
    grid-template-columns: 1fr;
  }
  
  .header-content {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }

  .header-actions {
    width: 100%;
    justify-content: space-between;
  }
  
  .info-row {
    flex-direction: column;
    gap: 12px;
  }

  .tasks-table-container {
    overflow-x: auto;
  }

  .tasks-table {
    min-width: 800px;
  }
}
</style>
