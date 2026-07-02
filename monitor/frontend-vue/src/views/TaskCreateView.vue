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
          <div class="custom-select-wrapper">
            <select v-model="selectedDatasource" required class="custom-select">
              <option value="">请选择数据源</option>
              <option v-for="ds in datasources" :key="ds.id" :value="ds.id">
                {{ ds.name }} ({{ ds.host }}:{{ ds.port }})
              </option>
            </select>
            <svg class="select-icon" width="16" height="16" viewBox="0 0 16 16" fill="none">
              <path d="M4 6L8 10L12 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
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
          <div class="custom-select-wrapper">
            <select v-model="selectedSchema" @change="loadTables" required class="custom-select">
              <option value="">请选择 Schema</option>
              <option v-for="schema in schemas" :key="schema" :value="schema">
                {{ schema }}
              </option>
            </select>
            <svg class="select-icon" width="16" height="16" viewBox="0 0 16 16" fill="none">
              <path d="M4 6L8 10L12 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
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
        <!-- 基本信息 -->
        <div class="config-section">
          <div class="section-head">
            <svg class="section-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"/></svg>
            <span>基本信息</span>
          </div>
          <div class="section-body">
            <div class="form-group">
              <label>任务名称 *</label>
              <input v-model="taskConfig.name" type="text" required placeholder="例如：交易信息 CDC">
            </div>
            <div class="form-group">
              <label>任务描述</label>
              <input v-model="taskConfig.description" type="text" placeholder="任务描述（可选）">
            </div>
          </div>
        </div>

        <!-- 输出与性能 -->
        <div class="config-section">
          <div class="section-head">
            <svg class="section-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4 7h16M4 12h10M4 17h16"/><circle cx="17" cy="12" r="2"/></svg>
            <span>输出与性能</span>
          </div>
          <div class="section-body">
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

        <!-- 采集方式 -->
        <div class="config-section">
          <div class="section-head">
            <svg class="section-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12a9 9 0 0 1 15-6.7L21 8"/><path d="M21 3v5h-5"/><path d="M21 12a9 9 0 0 1-15 6.7L3 16"/><path d="M3 21v-5h5"/></svg>
            <span>采集方式</span>
          </div>
          <div class="section-body">
            <div class="mode-cards">
            <div class="mode-card" :class="{ active: taskConfig.sourceMode === 'log' }"
                 role="button" tabindex="0" @click="taskConfig.sourceMode = 'log'"
                 @keydown.enter="taskConfig.sourceMode = 'log'">
              <svg class="mode-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M4 6h16M4 12h16M4 18h10"/>
              </svg>
              <div class="mode-text">
                <div class="mode-title">日志级 CDC<span class="mode-tag">默认</span></div>
                <div class="mode-desc">经 oblogproxy 读日志，实时、可捕获删除。适用 OB 4.x。</div>
              </div>
              <svg v-if="taskConfig.sourceMode === 'log'" class="mode-check" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6L9 17l-5-5"/></svg>
            </div>
            <div class="mode-card" :class="{ active: taskConfig.sourceMode === 'polling' }"
                 role="button" tabindex="0" @click="taskConfig.sourceMode = 'polling'"
                 @keydown.enter="taskConfig.sourceMode = 'polling'">
              <svg class="mode-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M21 12a9 9 0 1 1-2.64-6.36"/><path d="M21 3v6h-6"/>
              </svg>
              <div class="mode-text">
                <div class="mode-title">轮询增量</div>
                <div class="mode-desc">纯 JDBC 按水位列轮询，与 OB 版本无关。适配 OB 3.x 等无匹配 liboblog 场景。</div>
              </div>
              <svg v-if="taskConfig.sourceMode === 'polling'" class="mode-check" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6L9 17l-5-5"/></svg>
            </div>
          </div>

          <!-- 轮询参数面板 -->
          <transition name="fade-slide">
          <div v-if="taskConfig.sourceMode === 'polling'" class="poll-panel">
            <div class="poll-note">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M12 16v-4M12 8h.01"/></svg>
              <span>轮询模式抓不到物理 DELETE，请选择单调递增的水位列（推荐自增主键）。</span>
            </div>
            <div class="form-row">
              <div class="form-group">
                <label>水位列 *</label>
                <div v-if="loadingWatermarkCols" class="hint">加载列...</div>
                <div v-else-if="watermarkColumns.length === 0" class="hint hint-warn">未获取到可用列（请确认所选表结构/连接）</div>
                <div v-else class="custom-select-wrapper">
                  <select v-model="taskConfig.pollWatermarkColumn" @change="onWatermarkColumnChange" class="custom-select">
                    <option v-for="c in watermarkColumns" :key="c.name" :value="c.name">
                      {{ c.name }}{{ c.primaryKey ? ' (主键)' : '' }} · {{ c.typeName }}
                    </option>
                  </select>
                </div>
              </div>
              <div class="form-group">
                <label>水位类型 <span class="label-hint">按列自动</span></label>
                <div class="custom-select-wrapper">
                  <select v-model="taskConfig.pollWatermarkType" class="custom-select">
                    <option value="numeric">numeric（数值列/自增主键）</option>
                    <option value="timestamp">timestamp（时间戳列）</option>
                  </select>
                </div>
              </div>
            </div>
            <div class="form-row">
              <div class="form-group">
                <label>轮询间隔 (ms)</label>
                <input v-model.number="taskConfig.pollIntervalMs" type="number" min="500" step="500">
              </div>
              <div class="form-group">
                <label>起始水位值 <span class="label-hint">可空</span></label>
                <input v-model="taskConfig.pollStartValue" type="text" placeholder="空 = 从当前开始">
              </div>
            </div>
          </div>
        </transition>
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

// 轮询水位列（从所选表的真实列中选择）
const watermarkColumns = ref([])
const loadingWatermarkCols = ref(false)
// JDBC 时间类类型：DATE=91, TIME=92, TIMESTAMP=93, TIME_TZ=2013, TIMESTAMP_TZ=2014
const TEMPORAL_TYPES = [91, 92, 93, 2013, 2014]
// JDBC 数值类型：bit/tinyint/smallint/int/bigint/numeric/decimal/float/real/double
const NUMERIC_TYPES = [-7, -6, -5, 4, 5, 2, 3, 6, 7, 8]

const taskConfig = ref({
  name: '',
  description: '',
  outputPath: './output/cdc',
  parallelism: 2,
  splitSize: 8096,
  // 采集方式
  sourceMode: 'log',
  pollWatermarkColumn: 'ID',
  pollWatermarkType: 'numeric',
  pollIntervalMs: 5000,
  pollStartValue: ''
})

onMounted(() => {
  loadDataSources()
})

watch(selectedDatasource, (newVal) => {
  if (newVal) {
    loadSchemas(newVal)
  }
})

// 切换到轮询模式时，加载所选表的列供水位列下拉选择
watch(() => taskConfig.value.sourceMode, (mode) => {
  if (mode === 'polling') loadWatermarkColumns()
})

// 所选表变化后，如已处于轮询模式则刷新可选水位列
watch(selectedTables, () => {
  if (taskConfig.value.sourceMode === 'polling') loadWatermarkColumns()
}, { deep: true })

/**
 * 取所选表的列交集（水位列须在所有被采集表中都存在），填充下拉。
 * 默认优先选数值型主键，其次主键，其次首列，并按列类型自动设置水位类型。
 */
async function loadWatermarkColumns() {
  if (!selectedDatasource.value || !selectedSchema.value || selectedTables.value.length === 0) {
    watermarkColumns.value = []
    return
  }
  loadingWatermarkCols.value = true
  try {
    let common = null
    const byName = {}
    for (const t of selectedTables.value) {
      const res = await api.get(
        `/cdc-simulator/${selectedDatasource.value}/schemas/${selectedSchema.value}/tables/${t}/columns`
      )
      const cols = res.data || []
      cols.forEach(c => { byName[c.name] = c })
      const names = new Set(cols.map(c => c.name))
      common = common === null ? names : new Set([...common].filter(n => names.has(n)))
    }
    const list = [...(common || [])].map(n => byName[n])
    list.sort((a, b) => (b.primaryKey ? 1 : 0) - (a.primaryKey ? 1 : 0) || a.name.localeCompare(b.name))
    watermarkColumns.value = list

    // 若当前选中列不在交集中，重新挑默认
    const stillValid = list.some(c => c.name === taskConfig.value.pollWatermarkColumn)
    if (list.length && !stillValid) {
      const preferred = list.find(c => c.primaryKey && NUMERIC_TYPES.includes(c.dataType))
        || list.find(c => c.primaryKey)
        || list[0]
      taskConfig.value.pollWatermarkColumn = preferred.name
    }
    onWatermarkColumnChange()
  } catch (error) {
    watermarkColumns.value = []
    showAlert('error', '加载列失败: ' + (error.message || error))
  } finally {
    loadingWatermarkCols.value = false
  }
}

/** 依据所选水位列的 JDBC 类型自动设置水位类型（时间戳/数值）。 */
function onWatermarkColumnChange() {
  const c = watermarkColumns.value.find(x => x.name === taskConfig.value.pollWatermarkColumn)
  if (!c) return
  taskConfig.value.pollWatermarkType = TEMPORAL_TYPES.includes(c.dataType) ? 'timestamp' : 'numeric'
}

async function loadDataSources() {
  try {
    const result = await api.get('/datasources')
    // 过滤出连接成功的数据源
    datasources.value = (result.data || []).filter(ds => ds.status === 'SUCCESS')
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
  if (taskConfig.value.sourceMode === 'polling' && !taskConfig.value.pollWatermarkColumn) {
    showAlert('error', '轮询模式必须指定水位列')
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
    split_size: taskConfig.value.splitSize,
    source_mode: taskConfig.value.sourceMode,
    poll_watermark_column: taskConfig.value.pollWatermarkColumn,
    poll_watermark_type: taskConfig.value.pollWatermarkType,
    poll_interval_ms: taskConfig.value.pollIntervalMs,
    poll_start_value: taskConfig.value.pollStartValue || null
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

/* 自定义下拉菜单样式 */
.custom-select-wrapper {
  position: relative;
  display: inline-block;
  width: 100%;
}

.custom-select {
  width: 100%;
  padding: 12px 40px 12px 16px;
  font-size: 14px;
  font-weight: 500;
  color: #172B4D;
  background: #FAFBFC;
  border: 2px solid #DFE1E6;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  appearance: none;
  -webkit-appearance: none;
  -moz-appearance: none;
}

.custom-select:hover {
  background: #F4F5F7;
  border-color: #B3BAC5;
}

.custom-select:focus {
  outline: none;
  background: #fff;
  border-color: #4C9AFF;
  box-shadow: 0 0 0 3px rgba(76, 154, 255, 0.15);
}

.custom-select option {
  padding: 12px;
  font-size: 14px;
  color: #172B4D;
  background: #fff;
}

.custom-select option:hover {
  background: #DEEBFF;
}

.custom-select option:checked {
  background: #0052CC;
  color: #fff;
  font-weight: 600;
}

.select-icon {
  position: absolute;
  right: 14px;
  top: 50%;
  transform: translateY(-50%);
  color: #5E6C84;
  pointer-events: none;
  transition: transform 0.2s ease, color 0.2s ease;
}

.custom-select:focus + .select-icon {
  color: #0052CC;
  transform: translateY(-50%) rotate(180deg);
}

.custom-select:hover + .select-icon {
  color: #172B4D;
}

/* 禁用状态 */
.custom-select:disabled {
  background: #F4F5F7;
  color: #A5ADBA;
  cursor: not-allowed;
  border-color: #DFE1E6;
}

.custom-select:disabled + .select-icon {
  color: #A5ADBA;
}

/* ── 配置分组卡片 ── */
.config-section {
  border: 1px solid #DFE1E6;
  border-radius: 10px;
  background: #fff;
  margin-bottom: 16px;
  overflow: hidden;
}
.section-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #F7F8FA;
  border-bottom: 1px solid #EBECF0;
  font-size: 13px;
  font-weight: 700;
  color: #172B4D;
}
.section-icon { width: 18px; height: 18px; color: #0052CC; flex-shrink: 0; }
.section-body { padding: 20px; }
.section-body > .form-group,
.section-body > .form-row { margin-bottom: 18px; }
.section-body > .form-group:last-child,
.section-body > .form-row:last-child { margin-bottom: 0; }

/* ── 表单标签与输入框美化 ── */
.form-group label {
  display: block;
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #5E6C84;
  letter-spacing: 0.02em;
}
.form-group input[type="text"],
.form-group input[type="number"] {
  width: 100%;
  box-sizing: border-box;
  padding: 11px 14px;
  font-size: 14px;
  font-weight: 500;
  color: #172B4D;
  background: #FAFBFC;
  border: 2px solid #DFE1E6;
  border-radius: 6px;
  transition: border-color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease;
  outline: none;
}
.form-group input::placeholder { color: #A5ADBA; font-weight: 400; }
.form-group input:hover { border-color: #B3BAC5; background: #F4F5F7; }
.form-group input:focus {
  border-color: #4C9AFF;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(76, 154, 255, 0.15);
}

/* ── 采集方式卡片 ── */
.mode-cards {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}
.mode-card {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 16px;
  border: 2px solid #DFE1E6;
  border-radius: 8px;
  background: #FAFBFC;
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease;
}
.mode-card:hover { border-color: #B3BAC5; background: #F4F5F7; }
.mode-card.active {
  border-color: #0052CC;
  background: #DEEBFF;
  box-shadow: 0 0 0 3px rgba(76, 154, 255, 0.15);
}
.mode-card:focus-visible { outline: none; border-color: #4C9AFF; box-shadow: 0 0 0 3px rgba(76, 154, 255, 0.25); }
.mode-icon { width: 22px; height: 22px; color: #5E6C84; flex-shrink: 0; margin-top: 2px; }
.mode-card.active .mode-icon { color: #0052CC; }
.mode-text { flex: 1; min-width: 0; }
.mode-title { font-size: 14px; font-weight: 600; color: #172B4D; display: flex; align-items: center; gap: 6px; }
.mode-tag { font-size: 10px; font-weight: 700; color: #0052CC; background: #E9F2FF; border-radius: 3px; padding: 1px 6px; }
.mode-desc { font-size: 12px; color: #5E6C84; margin-top: 4px; line-height: 1.5; }
.mode-check { width: 18px; height: 18px; color: #0052CC; flex-shrink: 0; }

/* ── 轮询参数面板 ── */
.poll-panel {
  margin-top: 14px;
  padding: 16px;
  border: 1px solid #DFE1E6;
  border-left: 3px solid #0052CC;
  border-radius: 8px;
  background: #F7F9FC;
}
.poll-note {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 14px;
  font-size: 12px;
  line-height: 1.5;
  color: #7A4D00;
  background: #FFFAE6;
  border: 1px solid #FFF0B3;
  border-radius: 6px;
  padding: 8px 12px;
}
.poll-note svg { width: 16px; height: 16px; flex-shrink: 0; margin-top: 1px; color: #974F0C; }

.hint { display: block; font-size: 12px; color: #5E6C84; margin-top: 6px; line-height: 1.5; }
.hint-warn { color: #974F0C; }
.label-hint { font-size: 11px; font-weight: 500; color: #97A0AF; }

/* 面板过渡 */
.fade-slide-enter-active, .fade-slide-leave-active { transition: opacity 0.2s ease, transform 0.2s ease; }
.fade-slide-enter-from, .fade-slide-leave-to { opacity: 0; transform: translateY(-6px); }

@media (max-width: 767px) {
  .mode-cards { grid-template-columns: 1fr; }
  .form-row { flex-direction: column; }
  .form-actions { flex-direction: column; gap: 8px; }
  .form-actions .btn { width: 100%; justify-content: center; }
}
</style>
