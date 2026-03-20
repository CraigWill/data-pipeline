<template>
  <div class="task-create-view">
    <div class="page-header">
      <h2>创建 CDC 任务</h2>
      <p>配置并创建新的 CDC 数据采集任务</p>
    </div>

    <div v-if="alert.show" :class="['alert', `alert-${alert.type}`]">
      {{ alert.message }}
    </div>

    <!-- 步骤指示器 -->
    <div class="wizard-steps">
      <div v-for="step in steps" :key="step.num" 
           :class="['step', { active: currentStep === step.num, completed: currentStep > step.num }]">
        <div class="step-number">{{ step.num }}</div>
        <div class="step-title">{{ step.title }}</div>
      </div>
    </div>

    <!-- 步骤内容 -->
    <div class="wizard-content">
      <!-- 步骤 1: 选择数据源 -->
      <div v-show="currentStep === 1" class="step-content">
        <h3 class="mb-3">选择数据源</h3>
        <div class="form-group">
          <label>数据源 *</label>
          <select v-model="selectedDatasource" required>
            <option value="">请选择数据源</option>
            <option v-for="ds in datasources" :key="ds.id" :value="ds.id">
              {{ ds.name }} ({{ ds.host }}:{{ ds.port }})
            </option>
          </select>
        </div>
        <p class="text-muted text-sm mt-2">
          如果没有可用的数据源，请先到"数据源管理"页面创建数据源。
        </p>
      </div>

      <!-- 步骤 2: 选择 Schema -->
      <div v-show="currentStep === 2" class="step-content">
        <h3 class="mb-3">选择 Schema</h3>
        <div class="form-group">
          <label>Schema *</label>
          <select v-model="selectedSchema" @change="loadTables" required>
            <option value="">请选择 Schema</option>
            <option v-for="schema in schemas" :key="schema" :value="schema">
              {{ schema }}
            </option>
          </select>
        </div>
      </div>

      <!-- 步骤 3: 选择表 -->
      <div v-show="currentStep === 3" class="step-content">
        <h3 class="mb-3">选择要监控的表</h3>
        <div v-if="loadingTables" class="loading">
          <div class="spinner"></div>
          <p>加载表列表...</p>
        </div>
        <div v-else-if="tables.length === 0" class="empty-state">
          <p>未找到表</p>
        </div>
        <div v-else class="table-list">
          <div v-for="table in tables" :key="table.name" class="table-item">
            <input type="checkbox" :id="`table-${table.name}`" :value="table.name" 
                   v-model="selectedTables">
            <label :for="`table-${table.name}`" class="table-info">
              <div class="table-name">{{ table.name }}</div>
              <div class="table-stats">{{ table.rows.toLocaleString() }} 行 | {{ table.columns }} 列</div>
            </label>
          </div>
        </div>
      </div>

      <!-- 步骤 4: 配置任务 -->
      <div v-show="currentStep === 4" class="step-content">
        <h3 class="mb-3">配置任务参数</h3>
        <div class="form-group">
          <label>任务名称 *</label>
          <input v-model="taskConfig.name" type="text" required placeholder="例如：交易信息 CDC">
        </div>
        <div class="form-group">
          <label>任务描述</label>
          <input v-model="taskConfig.description" type="text" placeholder="任务描述（可选）">
        </div>
        <div class="form-group">
          <label>输出路径</label>
          <input v-model="taskConfig.outputPath" type="text" placeholder="./output/cdc">
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>并行度</label>
            <input v-model.number="taskConfig.parallelism" type="number" min="1" max="16">
          </div>
          <div class="form-group">
            <label>分片大小</label>
            <input v-model.number="taskConfig.splitSize" type="number" min="1024">
          </div>
        </div>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="wizard-actions">
      <button v-if="currentStep > 1" class="btn btn-secondary" @click="previousStep">上一步</button>
      <div></div>
      <button class="btn btn-primary" @click="nextStep">
        {{ currentStep === 4 ? '保存并提交' : '下一步' }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/api'

const router = useRouter()

const steps = [
  { num: 1, title: '选择数据源' },
  { num: 2, title: '选择 Schema' },
  { num: 3, title: '选择表' },
  { num: 4, title: '配置任务' }
]

const currentStep = ref(1)
const datasources = ref([])
const schemas = ref([])
const tables = ref([])
const loadingTables = ref(false)
const alert = ref({ show: false, type: '', message: '' })

const selectedDatasource = ref('')
const selectedSchema = ref('')
const selectedTables = ref([])

const taskConfig = ref({
  name: '',
  description: '',
  outputPath: './output/cdc',
  parallelism: 2,
  splitSize: 8096
})

onMounted(() => {
  loadDataSources()
})

watch(selectedDatasource, (newVal) => {
  if (newVal) {
    loadSchemas(newVal)
  }
})

async function loadDataSources() {
  try {
    const result = await api.get('/datasources')
    datasources.value = result.data || []
  } catch (error) {
    showAlert('error', '加载数据源失败: ' + error.message)
  }
}

async function loadSchemas(datasourceId) {
  try {
    const result = await api.get(`/datasources/${datasourceId}/schemas`)
    schemas.value = result.data || []
  } catch (error) {
    showAlert('error', '加载 Schema 失败: ' + error.message)
  }
}

async function loadTables() {
  if (!selectedDatasource.value || !selectedSchema.value) return

  loadingTables.value = true
  try {
    const result = await api.get(
      `/datasources/${selectedDatasource.value}/schemas/${selectedSchema.value}/tables`
    )
    tables.value = result.data || []
  } catch (error) {
    showAlert('error', '加载表列表失败: ' + error.message)
  } finally {
    loadingTables.value = false
  }
}

async function nextStep() {
  // 验证当前步骤
  if (currentStep.value === 1) {
    if (!selectedDatasource.value) {
      showAlert('error', '请选择数据源')
      return
    }
  } else if (currentStep.value === 2) {
    if (!selectedSchema.value) {
      showAlert('error', '请选择 Schema')
      return
    }
  } else if (currentStep.value === 3) {
    if (selectedTables.value.length === 0) {
      showAlert('error', '请至少选择一个表')
      return
    }
  } else if (currentStep.value === 4) {
    await saveTask()
    return
  }

  currentStep.value++
}

function previousStep() {
  if (currentStep.value > 1) {
    currentStep.value--
  }
}

async function saveTask() {
  if (!taskConfig.value.name) {
    showAlert('error', '请输入任务名称')
    return
  }

  const config = {
    id: `task-${Date.now()}`,
    name: taskConfig.value.name,
    description: taskConfig.value.description,
    created: new Date().toISOString(),
    datasource_id: selectedDatasource.value,
    schema: selectedSchema.value,
    tables: selectedTables.value,
    output_path: taskConfig.value.outputPath,
    parallelism: taskConfig.value.parallelism,
    split_size: taskConfig.value.splitSize
  }

  try {
    const result = await api.post('/cdc/tasks', config)
    showAlert('success', '任务已创建！')

    if (confirm('任务已创建，是否立即提交到 Flink？')) {
      const taskId = result.data?.id
      if (taskId) {
        await submitTask(taskId)
      }
    } else {
      setTimeout(() => {
        router.push('/tasks')
      }, 2000)
    }
  } catch (error) {
    showAlert('error', '创建任务失败: ' + error.message)
  }
}

async function submitTask(taskId) {
  try {
    const result = await api.post(`/cdc/tasks/${taskId}/submit`)
    showAlert('success', `任务已提交！Job ID: ${result.job_id || 'N/A'}`)
    setTimeout(() => {
      router.push('/tasks')
    }, 2000)
  } catch (error) {
    showAlert('error', '提交任务失败: ' + error.message)
  }
}

function showAlert(type, message) {
  alert.value = { show: true, type, message }
  setTimeout(() => {
    alert.value.show = false
  }, 5000)
}
</script>

<style scoped>
.wizard-steps {
  display: flex;
  justify-content: space-between;
  margin-bottom: 28px;
  position: relative;
}

.wizard-steps::before {
  content: '';
  position: absolute;
  top: 16px;
  left: 0;
  right: 0;
  height: 2px;
  background: #DFE1E6;
  z-index: 0;
}

.step {
  flex: 1;
  text-align: center;
  position: relative;
  z-index: 1;
}

.step-number {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #DFE1E6;
  color: #5E6C84;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 6px;
}

.step.active .step-number {
  background: #0052CC;
  color: #fff;
}

.step.completed .step-number {
  background: #00875A;
  color: #fff;
}

.step-title {
  font-size: 12px;
  color: #5E6C84;
}

.step.active .step-title {
  color: #0052CC;
  font-weight: 600;
}

.wizard-content {
  background: #fff;
  border-radius: 4px;
  padding: 24px;
  box-shadow: 0 1px 1px rgba(9,30,66,0.25), 0 0 0 1px rgba(9,30,66,0.08);
  min-height: 360px;
  margin-bottom: 16px;
}

.wizard-actions {
  display: flex;
  justify-content: space-between;
}

.table-list {
  max-height: 360px;
  overflow-y: auto;
  border: 2px solid #DFE1E6;
  border-radius: 4px;
}

.table-item {
  padding: 10px 14px;
  border-bottom: 1px solid #DFE1E6;
  display: flex;
  align-items: center;
  gap: 10px;
  transition: background 0.15s;
}

.table-item:hover {
  background: #F4F5F7;
}

.table-item:last-child {
  border-bottom: none;
}

.table-info {
  flex: 1;
  cursor: pointer;
}

.table-name {
  font-size: 13px;
  font-weight: 600;
  color: #172B4D;
}

.table-stats {
  font-size: 11px;
  color: #97A0AF;
  margin-top: 2px;
}

.form-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}
</style>
