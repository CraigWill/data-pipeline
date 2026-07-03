<template>
  <div class="simulator-view">
    <div class="page-header">
      <h1>模拟 CDC 事件</h1>
      <p class="subtitle">选择数据源与表，查询现有数据并模拟批量 INSERT / UPDATE / DELETE 操作以触发 CDC 变更</p>
    </div>

    <!-- 提示 -->
    <transition name="fade">
      <div v-if="alert.show" class="alert" :class="`alert-${alert.type}`">
        {{ alert.message }}
      </div>
    </transition>

    <!-- 选择区 -->
    <div class="card selector-card">
      <div class="selector-row">
        <div class="field">
          <label>数据源</label>
          <select v-model="selectedDs" @change="onDsChange" class="custom-select">
            <option value="">请选择数据源</option>
            <option v-for="ds in datasources" :key="ds.id" :value="ds.id">
              {{ ds.name }} ({{ ds.type }})
            </option>
          </select>
        </div>
        <div class="field">
          <label>Schema</label>
          <select v-model="selectedSchema" @change="onSchemaChange" class="custom-select" :disabled="!selectedDs">
            <option value="">请选择 Schema</option>
            <option v-for="s in schemas" :key="s" :value="s">{{ s }}</option>
          </select>
        </div>
        <div class="field">
          <label>表</label>
          <select v-model="selectedTable" @change="onTableChange" class="custom-select" :disabled="!selectedSchema">
            <option value="">请选择表</option>
            <option v-for="t in tables" :key="t.name" :value="t.name">
              {{ t.name }} ({{ t.rows }} 行)
            </option>
          </select>
        </div>
        <button class="btn btn-secondary" @click="refreshData" :disabled="!selectedTable || loading">
          刷新数据
        </button>
      </div>
    </div>

    <template v-if="selectedTable">
      <!-- 列结构概览 -->
      <div class="card" v-if="columns.length">
        <div class="card-title">
          <span>表结构</span>
          <div class="card-title-right">
            <span class="muted">主键列: {{ keyColumns.length ? keyColumns.join(', ') : '未检测到（更新/删除需手动选择键列）' }}</span>
            <button class="btn btn-mini btn-ghost" :disabled="ddlDownloading" @click="downloadTableDef">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v12M7 10l5 5 5-5M5 21h14"/></svg>
              {{ ddlDownloading ? '导出中...' : '下载表定义' }}
            </button>
          </div>
        </div>
        <div class="schema-chips">
          <span v-for="col in columns" :key="col.name" class="chip" :class="{ pk: col.primaryKey }">
            {{ col.name }}
            <em>{{ col.typeName }}</em>
            <i v-if="col.primaryKey" class="pk-badge">PK</i>
          </span>
        </div>
      </div>

      <!-- 操作选项卡 -->
      <div class="card">
        <div class="tabs">
          <button v-for="tab in tabs" :key="tab.key"
                  class="tab" :class="{ active: activeTab === tab.key }"
                  @click="activeTab = tab.key">
            {{ tab.label }}
          </button>
        </div>

        <!-- ========== 浏览数据 ========== -->
        <div v-show="activeTab === 'browse'" class="tab-panel">
          <div class="panel-toolbar">
            <span class="muted">共 {{ total }} 行</span>
            <div class="pagination">
              <button class="btn btn-mini" :disabled="page <= 1 || loading" @click="changePage(page - 1)">上一页</button>
              <span class="page-info">{{ page }} / {{ totalPages }}</span>
              <button class="btn btn-mini" :disabled="page >= totalPages || loading" @click="changePage(page + 1)">下一页</button>
              <select v-model.number="size" @change="changePage(1)" class="custom-select mini">
                <option :value="20">20/页</option>
                <option :value="50">50/页</option>
                <option :value="100">100/页</option>
              </select>
            </div>
          </div>
          <div class="table-wrap">
            <table class="data-table">
              <thead>
                <tr>
                  <th v-for="c in dataColumns" :key="c">{{ c }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, i) in dataRows" :key="i">
                  <td v-for="c in dataColumns" :key="c" :title="row[c]">{{ formatCell(row[c]) }}</td>
                </tr>
                <tr v-if="!dataRows.length">
                  <td :colspan="dataColumns.length || 1" class="empty">暂无数据</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- ========== 批量插入 ========== -->
        <div v-show="activeTab === 'insert'" class="tab-panel">
          <div class="auto-insert-bar">
            <svg class="ai-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 5v14M5 12h14"/></svg>
            <span class="ai-label">自动生成模拟数据</span>
            <input v-model.number="autoInsertCount" type="number" min="1" max="10000" class="ai-count" />
            <span class="ai-unit">行</span>
            <button class="btn btn-primary btn-mini" :disabled="autoInserting || !autoInsertCount || !selectedTable" @click="doAutoInsert">
              {{ autoInserting ? '生成中...' : '自动插入' }}
            </button>
            <span class="ai-hint">按列类型自动造值，数值主键自增，写入即触发 CDC</span>
          </div>
          <div class="panel-toolbar">
            <button class="btn btn-mini" @click="addInsertRow">+ 新增一行</button>
            <button class="btn btn-mini" @click="duplicateLastInsertRow" :disabled="!insertRows.length">复制末行</button>
            <span class="muted">共 {{ insertRows.length }} 行待插入</span>
          </div>
          <div class="table-wrap">
            <table class="data-table editable">
              <thead>
                <tr>
                  <th style="width:48px">#</th>
                  <th v-for="col in columns" :key="col.name">
                    {{ col.name }}<i v-if="col.primaryKey" class="pk-badge">PK</i>
                  </th>
                  <th style="width:60px">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, i) in insertRows" :key="i">
                  <td>{{ i + 1 }}</td>
                  <td v-for="col in columns" :key="col.name">
                    <input v-model="row[col.name]" class="cell-input"
                           :placeholder="col.nullable ? 'null' : '必填'" />
                  </td>
                  <td>
                    <button class="btn-link danger" @click="insertRows.splice(i, 1)">删除</button>
                  </td>
                </tr>
                <tr v-if="!insertRows.length">
                  <td :colspan="columns.length + 2" class="empty">点击"新增一行"添加待插入数据</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="panel-actions">
            <button class="btn btn-primary" :disabled="!insertRows.length || submitting" @click="doInsert">
              {{ submitting ? '执行中...' : `批量插入 ${insertRows.length} 行` }}
            </button>
          </div>
        </div>

        <!-- ========== 批量更新 ========== -->
        <div v-show="activeTab === 'update'" class="tab-panel">
          <p class="hint" v-if="!keyColumns.length">
            未检测到主键，请勾选作为匹配条件的键列：
            <span v-for="col in columns" :key="col.name" class="key-pick">
              <label><input type="checkbox" :value="col.name" v-model="manualKeys" /> {{ col.name }}</label>
            </span>
          </p>
          <div class="panel-toolbar">
            <span class="muted">从下方数据中编辑后提交（基于键列 {{ effectiveKeys.join(', ') || '未选择' }} 匹配）。先在"浏览数据"加载数据。</span>
          </div>
          <div class="table-wrap">
            <table class="data-table editable">
              <thead>
                <tr>
                  <th style="width:48px"><input type="checkbox" :checked="allUpdSelected" @change="toggleAllUpd" /></th>
                  <th v-for="c in dataColumns" :key="c">{{ c }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, i) in editRows" :key="i">
                  <td><input type="checkbox" v-model="row.__selected" /></td>
                  <td v-for="c in dataColumns" :key="c">
                    <input v-model="row[c]" class="cell-input" :disabled="effectiveKeys.includes(c)" />
                  </td>
                </tr>
                <tr v-if="!editRows.length">
                  <td :colspan="dataColumns.length + 1" class="empty">请先在"浏览数据"加载数据</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="panel-actions">
            <button class="btn btn-primary" :disabled="submitting || !selectedUpdCount" @click="doUpdate">
              {{ submitting ? '执行中...' : `批量更新选中 ${selectedUpdCount} 行` }}
            </button>
          </div>
        </div>

        <!-- ========== 批量删除 ========== -->
        <div v-show="activeTab === 'delete'" class="tab-panel">
          <p class="hint" v-if="!keyColumns.length">
            未检测到主键，请勾选作为匹配条件的键列：
            <span v-for="col in columns" :key="col.name" class="key-pick">
              <label><input type="checkbox" :value="col.name" v-model="manualKeys" /> {{ col.name }}</label>
            </span>
          </p>
          <div class="panel-toolbar">
            <span class="muted">勾选要删除的行（基于键列 {{ effectiveKeys.join(', ') || '未选择' }} 匹配）。先在"浏览数据"加载数据。</span>
          </div>
          <div class="table-wrap">
            <table class="data-table">
              <thead>
                <tr>
                  <th style="width:48px"><input type="checkbox" :checked="allDelSelected" @change="toggleAllDel" /></th>
                  <th v-for="c in dataColumns" :key="c">{{ c }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, i) in delRows" :key="i" :class="{ 'row-selected': row.__selected }">
                  <td><input type="checkbox" v-model="row.__selected" /></td>
                  <td v-for="c in dataColumns" :key="c">{{ formatCell(row[c]) }}</td>
                </tr>
                <tr v-if="!delRows.length">
                  <td :colspan="dataColumns.length + 1" class="empty">请先在"浏览数据"加载数据</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="panel-actions">
            <button class="btn btn-danger" :disabled="submitting || !selectedDelCount" @click="doDelete">
              {{ submitting ? '执行中...' : `批量删除选中 ${selectedDelCount} 行` }}
            </button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { datasourceAPI, cdcSimulatorAPI } from '../api/index.js'

const tabs = [
  { key: 'browse', label: '浏览数据' },
  { key: 'insert', label: '批量插入' },
  { key: 'update', label: '批量更新' },
  { key: 'delete', label: '批量删除' }
]
const activeTab = ref('browse')

const datasources = ref([])
const schemas = ref([])
const tables = ref([])
const selectedDs = ref('')
const selectedSchema = ref('')
const selectedTable = ref('')

const columns = ref([])         // 列结构 [{name, typeName, primaryKey, nullable}]
const dataColumns = ref([])     // 当前数据的列名
const dataRows = ref([])        // 浏览数据
const total = ref(0)
const page = ref(1)
const size = ref(50)

const insertRows = ref([])      // 待插入行
const editRows = ref([])        // 可编辑行（更新）
const delRows = ref([])         // 可删除行
const manualKeys = ref([])      // 手动选择的键列

const loading = ref(false)
const submitting = ref(false)
const autoInsertCount = ref(100)   // 自动插入的行数
const autoInserting = ref(false)
const ddlDownloading = ref(false)  // 表定义下载中
const alert = ref({ show: false, type: '', message: '' })

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))
const keyColumns = computed(() => columns.value.filter(c => c.primaryKey).map(c => c.name))
const effectiveKeys = computed(() => keyColumns.value.length ? keyColumns.value : manualKeys.value)

const selectedUpdCount = computed(() => editRows.value.filter(r => r.__selected).length)
const selectedDelCount = computed(() => delRows.value.filter(r => r.__selected).length)
const allUpdSelected = computed(() => editRows.value.length > 0 && editRows.value.every(r => r.__selected))
const allDelSelected = computed(() => delRows.value.length > 0 && delRows.value.every(r => r.__selected))

onMounted(loadDataSources)

async function loadDataSources() {
  try {
    const res = await datasourceAPI.list()
    datasources.value = (res.data || []).filter(ds => ds.status === 'SUCCESS')
  } catch (e) {
    showAlert('error', '加载数据源失败: ' + errMsg(e))
  }
}

async function onDsChange() {
  selectedSchema.value = ''
  selectedTable.value = ''
  schemas.value = []
  tables.value = []
  resetTableState()
  if (!selectedDs.value) return
  try {
    const res = await datasourceAPI.schemas(selectedDs.value)
    schemas.value = res.data || []
  } catch (e) {
    showAlert('error', '加载 Schema 失败: ' + errMsg(e))
  }
}

async function onSchemaChange() {
  selectedTable.value = ''
  tables.value = []
  resetTableState()
  if (!selectedSchema.value) return
  try {
    const res = await datasourceAPI.tables(selectedDs.value, selectedSchema.value)
    tables.value = res.data || []
  } catch (e) {
    showAlert('error', '加载表列表失败: ' + errMsg(e))
  }
}

async function onTableChange() {
  resetTableState()
  if (!selectedTable.value) return
  await loadColumns()
  await loadData(1)
}

function resetTableState() {
  columns.value = []
  dataColumns.value = []
  dataRows.value = []
  insertRows.value = []
  editRows.value = []
  delRows.value = []
  manualKeys.value = []
  total.value = 0
  page.value = 1
}

async function loadColumns() {
  try {
    const res = await cdcSimulatorAPI.columns(selectedDs.value, selectedSchema.value, selectedTable.value)
    columns.value = res.data || []
  } catch (e) {
    showAlert('error', '加载列结构失败: ' + errMsg(e))
  }
}

async function loadData(targetPage) {
  loading.value = true
  try {
    const res = await cdcSimulatorAPI.queryData(selectedDs.value, selectedSchema.value, selectedTable.value, targetPage, size.value)
    const d = res.data || {}
    dataColumns.value = d.columns || []
    dataRows.value = d.rows || []
    total.value = d.total || 0
    page.value = d.page || targetPage
    // 同步可编辑/可删除副本
    editRows.value = dataRows.value.map(r => ({ ...r, __selected: false }))
    delRows.value = dataRows.value.map(r => ({ ...r, __selected: false }))
  } catch (e) {
    showAlert('error', '查询数据失败: ' + errMsg(e))
  } finally {
    loading.value = false
  }
}

function changePage(p) {
  if (p < 1 || p > totalPages.value) return
  loadData(p)
}

function refreshData() {
  loadData(page.value)
}

// ---- 插入 ----
function addInsertRow() {
  const row = {}
  columns.value.forEach(c => { row[c.name] = '' })
  insertRows.value.push(row)
}
function duplicateLastInsertRow() {
  if (!insertRows.value.length) return
  insertRows.value.push({ ...insertRows.value[insertRows.value.length - 1] })
}

async function doInsert() {
  if (!insertRows.value.length) return
  submitting.value = true
  try {
    // 去掉空字符串值（让后端按 null 处理）。保留用户明确填写的值。
    const rows = insertRows.value.map(r => {
      const o = {}
      Object.keys(r).forEach(k => { o[k] = r[k] })
      return o
    })
    const res = await cdcSimulatorAPI.insert(selectedDs.value, selectedSchema.value, selectedTable.value, rows)
    if (res.success) {
      showAlert('success', res.message || '插入成功')
      insertRows.value = []
      await loadData(page.value)
    } else {
      showAlert('error', res.error || '插入失败')
    }
  } catch (e) {
    showAlert('error', '插入失败: ' + errMsg(e))
  } finally {
    submitting.value = false
  }
}

// ---- 下载表定义 ----
async function downloadTableDef() {
  if (!selectedDs.value || !selectedSchema.value || !selectedTable.value) {
    showAlert('error', '请先选择数据源 / Schema / 表')
    return
  }
  ddlDownloading.value = true
  try {
    const res = await cdcSimulatorAPI.tableDdl(selectedDs.value, selectedSchema.value, selectedTable.value)
    if (res.success && res.data && res.data.ddl) {
      // 后端对响应做了 HTML 转义（&#34; 等），下载前解码还原
      const ta = document.createElement('textarea')
      ta.innerHTML = res.data.ddl
      const ddl = ta.value
      const blob = new Blob([ddl], { type: 'text/plain;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${selectedSchema.value}.${selectedTable.value}.sql`
      document.body.appendChild(a)
      a.click()
      a.remove()
      URL.revokeObjectURL(url)
    } else {
      showAlert('error', res.error || '获取表定义失败')
    }
  } catch (e) {
    showAlert('error', '下载失败: ' + errMsg(e))
  } finally {
    ddlDownloading.value = false
  }
}

// ---- 自动插入模拟数据 ----
async function doAutoInsert() {
  if (!selectedDs.value || !selectedSchema.value || !selectedTable.value) {
    showAlert('error', '请先选择数据源 / Schema / 表')
    return
  }
  if (!autoInsertCount.value || autoInsertCount.value < 1) {
    showAlert('error', '请输入要插入的行数')
    return
  }
  autoInserting.value = true
  try {
    const res = await cdcSimulatorAPI.autoInsert(
      selectedDs.value, selectedSchema.value, selectedTable.value, autoInsertCount.value)
    if (res.success) {
      showAlert('success', res.message || '自动插入成功')
      await loadData(page.value)
    } else {
      showAlert('error', res.error || '自动插入失败')
    }
  } catch (e) {
    showAlert('error', '自动插入失败: ' + errMsg(e))
  } finally {
    autoInserting.value = false
  }
}

// ---- 更新 ----
function toggleAllUpd(e) {
  const v = e.target.checked
  editRows.value.forEach(r => { r.__selected = v })
}

async function doUpdate() {
  if (!effectiveKeys.value.length) {
    showAlert('error', '请先选择用于匹配的键列')
    return
  }
  const selected = editRows.value.filter(r => r.__selected)
  if (!selected.length) return
  submitting.value = true
  try {
    const rows = selected.map(r => {
      const o = {}
      dataColumns.value.forEach(c => { o[c] = r[c] })
      return o
    })
    const res = await cdcSimulatorAPI.update(selectedDs.value, selectedSchema.value, selectedTable.value, rows, effectiveKeys.value)
    if (res.success) {
      showAlert('success', res.message || '更新成功')
      await loadData(page.value)
    } else {
      showAlert('error', res.error || '更新失败')
    }
  } catch (e) {
    showAlert('error', '更新失败: ' + errMsg(e))
  } finally {
    submitting.value = false
  }
}

// ---- 删除 ----
function toggleAllDel(e) {
  const v = e.target.checked
  delRows.value.forEach(r => { r.__selected = v })
}

async function doDelete() {
  if (!effectiveKeys.value.length) {
    showAlert('error', '请先选择用于匹配的键列')
    return
  }
  const selected = delRows.value.filter(r => r.__selected)
  if (!selected.length) return
  if (!confirm(`确认删除选中的 ${selected.length} 行数据？此操作不可恢复。`)) return
  submitting.value = true
  try {
    const rows = selected.map(r => {
      const o = {}
      effectiveKeys.value.forEach(c => { o[c] = r[c] })
      return o
    })
    const res = await cdcSimulatorAPI.delete(selectedDs.value, selectedSchema.value, selectedTable.value, rows, effectiveKeys.value)
    if (res.success) {
      showAlert('success', res.message || '删除成功')
      await loadData(page.value)
    } else {
      showAlert('error', res.error || '删除失败')
    }
  } catch (e) {
    showAlert('error', '删除失败: ' + errMsg(e))
  } finally {
    submitting.value = false
  }
}

function formatCell(v) {
  if (v === null || v === undefined) return 'NULL'
  const s = String(v)
  return s.length > 80 ? s.slice(0, 80) + '…' : s
}

function errMsg(e) {
  return e?.response?.data?.error || e?.message || '未知错误'
}

let alertTimer = null
function showAlert(type, message) {
  alert.value = { show: true, type, message }
  if (alertTimer) clearTimeout(alertTimer)
  alertTimer = setTimeout(() => { alert.value.show = false }, 4000)
}
</script>

<style scoped>
.simulator-view {
  max-width: 1440px;
  margin: 0 auto;
  padding: 20px 16px 48px;
}

.page-header {
  margin-bottom: 16px;
}
.page-header h1 {
  font-size: 20px;
  font-weight: 700;
  color: #172B4D;
  margin: 0 0 4px;
}
.subtitle {
  font-size: 13px;
  color: #5E6C84;
  margin: 0;
}

.card {
  background: #fff;
  border: 1px solid #DFE1E6;
  border-radius: 6px;
  padding: 16px;
  margin-bottom: 16px;
  box-shadow: 0 1px 2px rgba(9,30,66,0.08);
}

.card-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  font-size: 14px;
  font-weight: 600;
  color: #172B4D;
  margin-bottom: 12px;
}
.card-title-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.btn-ghost {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #fff;
  border: 1px solid #DFE1E6;
  color: #0052CC;
}
.btn-ghost:hover:not(:disabled) { background: #DEEBFF; border-color: #B3D4FF; }
.btn-ghost svg { width: 15px; height: 15px; }

.selector-row {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 16px;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 200px;
  flex: 1;
}
.field label {
  font-size: 12px;
  font-weight: 600;
  color: #5E6C84;
}

.custom-select {
  height: 36px;
  padding: 0 10px;
  border: 1px solid #DFE1E6;
  border-radius: 4px;
  background: #FAFBFC;
  font-size: 13px;
  color: #172B4D;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.custom-select:hover:not(:disabled) { background: #F4F5F7; }
.custom-select:focus { outline: none; border-color: #4C9AFF; background: #fff; }
.custom-select:disabled { opacity: 0.5; cursor: not-allowed; }
.custom-select.mini { height: 30px; min-width: 90px; }

/* 列结构 chips */
.schema-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: #F4F5F7;
  border: 1px solid #DFE1E6;
  border-radius: 4px;
  font-size: 12px;
  color: #172B4D;
}
.chip.pk { background: #E3FCEF; border-color: #79F2C0; }
.chip em {
  font-style: normal;
  color: #5E6C84;
  font-size: 11px;
}
.pk-badge {
  display: inline-block;
  font-style: normal;
  font-size: 9px;
  font-weight: 700;
  color: #006644;
  background: #ABF5D1;
  border-radius: 2px;
  padding: 1px 4px;
  margin-left: 4px;
}

/* tabs */
.tabs {
  display: flex;
  gap: 4px;
  border-bottom: 2px solid #DFE1E6;
  margin-bottom: 16px;
}
.tab {
  padding: 8px 16px;
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  font-size: 13px;
  font-weight: 600;
  color: #5E6C84;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
}
.tab:hover { color: #172B4D; }
.tab.active { color: #0052CC; border-bottom-color: #0052CC; }

.tab-panel { min-height: 120px; }

.panel-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.panel-actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.muted { font-size: 12px; color: #5E6C84; }

/* 自动插入模拟数据条 */
.auto-insert-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 12px 14px;
  margin-bottom: 14px;
  background: #F7F9FC;
  border: 1px solid #DFE1E6;
  border-left: 3px solid #0052CC;
  border-radius: 8px;
}
.auto-insert-bar .ai-icon { width: 18px; height: 18px; color: #0052CC; flex-shrink: 0; }
.auto-insert-bar .ai-label { font-size: 13px; font-weight: 600; color: #172B4D; }
.auto-insert-bar .ai-count {
  width: 96px;
  padding: 7px 10px;
  font-size: 14px;
  font-weight: 500;
  color: #172B4D;
  background: #fff;
  border: 2px solid #DFE1E6;
  border-radius: 6px;
  outline: none;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
.auto-insert-bar .ai-count:hover { border-color: #B3BAC5; }
.auto-insert-bar .ai-count:focus { border-color: #4C9AFF; box-shadow: 0 0 0 3px rgba(76, 154, 255, 0.15); }
.auto-insert-bar .ai-unit { font-size: 13px; color: #5E6C84; }
.auto-insert-bar .ai-hint { font-size: 12px; color: #7A869A; margin-left: auto; }

.hint {
  font-size: 12px;
  color: #5E6C84;
  background: #FFFAE6;
  border: 1px solid #FFE380;
  border-radius: 4px;
  padding: 8px 12px;
  margin: 0 0 12px;
}
.key-pick { margin-left: 8px; }
.key-pick label { display: inline-flex; align-items: center; gap: 4px; cursor: pointer; }

/* pagination */
.pagination {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}
.page-info { font-size: 12px; color: #5E6C84; min-width: 60px; text-align: center; }

/* tables */
.table-wrap {
  overflow-x: auto;
  border: 1px solid #DFE1E6;
  border-radius: 4px;
}
.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.data-table th {
  background: #F4F5F7;
  color: #5E6C84;
  font-weight: 600;
  text-align: left;
  padding: 8px 10px;
  white-space: nowrap;
  border-bottom: 1px solid #DFE1E6;
  position: sticky;
  top: 0;
}
.data-table td {
  padding: 6px 10px;
  border-bottom: 1px solid #F4F5F7;
  color: #172B4D;
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.data-table tr:hover td { background: #FAFBFC; }
.data-table .empty {
  text-align: center;
  color: #97A0AF;
  padding: 24px;
}
.data-table.editable td { padding: 4px 6px; }
.row-selected td { background: #FFEBE6 !important; }

.cell-input {
  width: 100%;
  min-width: 90px;
  height: 28px;
  padding: 0 6px;
  border: 1px solid #DFE1E6;
  border-radius: 3px;
  font-size: 12px;
  color: #172B4D;
  background: #fff;
}
.cell-input:focus { outline: none; border-color: #4C9AFF; }
.cell-input:disabled { background: #F4F5F7; color: #97A0AF; }

/* buttons */
.btn {
  height: 36px;
  padding: 0 16px;
  border: none;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s, opacity 0.15s;
}
.btn:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-primary { background: #0052CC; color: #fff; }
.btn-primary:hover:not(:disabled) { background: #0747A6; }
.btn-secondary { background: #F4F5F7; color: #172B4D; border: 1px solid #DFE1E6; }
.btn-secondary:hover:not(:disabled) { background: #EBECF0; }
.btn-danger { background: #DE350B; color: #fff; }
.btn-danger:hover:not(:disabled) { background: #BF2600; }
.btn-mini {
  height: 30px;
  padding: 0 12px;
  background: #F4F5F7;
  color: #172B4D;
  border: 1px solid #DFE1E6;
  font-size: 12px;
}
.btn-mini:hover:not(:disabled) { background: #EBECF0; }

.btn-link {
  background: none;
  border: none;
  color: #0052CC;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 4px;
}
.btn-link.danger { color: #DE350B; }
.btn-link:hover { text-decoration: underline; }

/* alert */
.alert {
  padding: 10px 14px;
  border-radius: 4px;
  font-size: 13px;
  margin-bottom: 16px;
}
.alert-success { background: #E3FCEF; color: #006644; border: 1px solid #79F2C0; }
.alert-error { background: #FFEBE6; color: #BF2600; border: 1px solid #FF8F73; }

.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

@media (max-width: 767px) {
  .field { min-width: 100%; }
  .pagination { margin-left: 0; }
}
</style>
