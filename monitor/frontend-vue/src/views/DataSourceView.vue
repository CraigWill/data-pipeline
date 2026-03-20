<template>
  <div class="datasource-view">
    <div class="page-header">
      <div class="page-header-left">
        <h2><icon-data-base theme="outline" size="20" /> 数据源管理</h2>
        <p class="subtitle">管理 Oracle 数据库连接配置</p>
      </div>
      <button class="btn btn-primary" @click="showCreateModal">
        <icon-add-one theme="outline" size="14" /> 新建数据源
      </button>
    </div>

    <!-- 全局提示 -->
    <transition name="fade">
      <div v-if="alert.show" :class="['alert-bar', `alert-bar-${alert.type}`]">
        <icon-check-one v-if="alert.type === 'success'" theme="filled" size="14" />
        <icon-caution v-else theme="filled" size="14" />
        {{ alert.message }}
      </div>
    </transition>

    <!-- 加载 -->
    <div v-if="loading" class="loading">
      <div class="spinner"></div>
      <p>加载中...</p>
    </div>

    <!-- 空状态 -->
    <div v-else-if="datasources.length === 0" class="empty-state">
      <div class="empty-icon"><icon-data-base theme="outline" size="56" fill="#C1C7D0" /></div>
      <h3>暂无数据源</h3>
      <p>点击"新建数据源"添加 Oracle 连接配置</p>
      <button class="btn btn-primary mt-3" @click="showCreateModal">
        <icon-add-one theme="outline" size="14" /> 新建数据源
      </button>
    </div>

    <!-- 数据源列表 -->
    <div v-else class="datasource-list">
      <div v-for="ds in datasources" :key="ds.id" class="datasource-card">
        <div class="ds-icon-wrap">
          <icon-data-base theme="filled" size="22" fill="#0052CC" />
        </div>
        <div class="ds-info">
          <div class="ds-name">{{ ds.name }}</div>
          <div class="ds-conn">
            <icon-link theme="outline" size="12" />
            {{ ds.host }}:{{ ds.port }} / {{ ds.sid }}
          </div>
          <div class="ds-meta">
            <icon-user theme="outline" size="11" /> {{ ds.username }}
            <span class="dot">·</span>
            创建于 {{ formatDate(ds.created_at) }}
          </div>
        </div>
        <div class="ds-actions">
          <button class="btn btn-sm btn-ghost" @click="testDataSource(ds)" :disabled="ds._testing">
            <icon-wifi theme="outline" size="13" />
            {{ ds._testing ? '测试中...' : '测试连接' }}
          </button>
          <button class="btn btn-sm btn-secondary" @click="editDataSource(ds)">
            <icon-edit theme="outline" size="13" /> 编辑
          </button>
          <button class="btn btn-sm btn-danger-ghost" @click="deleteDataSource(ds)">
            <icon-delete theme="outline" size="13" /> 删除
          </button>
        </div>
      </div>
    </div>

    <!-- 创建/编辑对话框 -->
    <transition name="modal">
      <div v-if="showModal" class="modal-overlay" @click.self="closeModal">
        <div class="modal-dialog">
          <!-- 对话框头部 -->
          <div class="dialog-header">
            <div class="dialog-title-wrap">
              <icon-data-base theme="filled" size="18" fill="#0052CC" />
              <span class="dialog-title">{{ isEditing ? '编辑数据源' : '新建数据源' }}</span>
            </div>
            <button class="dialog-close" @click="closeModal">
              <icon-close theme="outline" size="16" />
            </button>
          </div>

          <!-- 对话框主体 -->
          <div class="dialog-body">
            <!-- 连接测试结果条 -->
            <transition name="fade">
              <div v-if="testResult" :class="['test-result-bar', `test-result-${testResult.type}`]">
                <icon-check-one v-if="testResult.type === 'success'" theme="filled" size="14" />
                <icon-close-one v-else theme="filled" size="14" />
                {{ testResult.message }}
              </div>
            </transition>

            <form @submit.prevent="saveDataSource" autocomplete="off">
              <!-- 基本信息 -->
              <div class="form-section">
                <div class="form-section-title">基本信息</div>
                <div class="form-field">
                  <label class="field-label">
                    数据源名称 <span class="required">*</span>
                  </label>
                  <input
                    v-model="form.name"
                    type="text"
                    class="field-input"
                    :class="{ 'field-error': errors.name }"
                    placeholder="例如：生产环境 Oracle"
                    @input="errors.name = ''"
                  />
                  <span v-if="errors.name" class="error-msg">{{ errors.name }}</span>
                </div>
              </div>

              <!-- 连接配置 -->
              <div class="form-section">
                <div class="form-section-title">连接配置</div>
                <div class="form-row">
                  <div class="form-field flex-3">
                    <label class="field-label">
                      主机地址 <span class="required">*</span>
                    </label>
                    <input
                      v-model="form.host"
                      type="text"
                      class="field-input"
                      :class="{ 'field-error': errors.host }"
                      placeholder="host.docker.internal"
                      @input="errors.host = ''; testResult = null"
                    />
                    <span v-if="errors.host" class="error-msg">{{ errors.host }}</span>
                  </div>
                  <div class="form-field flex-1">
                    <label class="field-label">
                      端口 <span class="required">*</span>
                    </label>
                    <input
                      v-model="form.port"
                      type="text"
                      class="field-input"
                      :class="{ 'field-error': errors.port }"
                      placeholder="1521"
                      @input="errors.port = ''; testResult = null"
                    />
                    <span v-if="errors.port" class="error-msg">{{ errors.port }}</span>
                  </div>
                </div>
                <div class="form-field">
                  <label class="field-label">
                    SID / 服务名 <span class="required">*</span>
                  </label>
                  <input
                    v-model="form.sid"
                    type="text"
                    class="field-input"
                    :class="{ 'field-error': errors.sid }"
                    placeholder="helowin"
                    @input="errors.sid = ''; testResult = null"
                  />
                  <span v-if="errors.sid" class="error-msg">{{ errors.sid }}</span>
                </div>
              </div>

              <!-- 认证信息 -->
              <div class="form-section">
                <div class="form-section-title">认证信息</div>
                <div class="form-row">
                  <div class="form-field flex-1">
                    <label class="field-label">
                      用户名 <span class="required">*</span>
                    </label>
                    <input
                      v-model="form.username"
                      type="text"
                      class="field-input"
                      :class="{ 'field-error': errors.username }"
                      placeholder="finance_user"
                      autocomplete="off"
                      @input="errors.username = ''; testResult = null"
                    />
                    <span v-if="errors.username" class="error-msg">{{ errors.username }}</span>
                  </div>
                  <div class="form-field flex-1">
                    <label class="field-label">
                      密码 <span class="required">*</span>
                    </label>
                    <div class="password-wrap">
                      <input
                        v-model="form.password"
                        :type="showPassword ? 'text' : 'password'"
                        class="field-input"
                        :class="{ 'field-error': errors.password }"
                        :placeholder="isEditing ? '不修改请留空' : '••••••••'"
                        autocomplete="new-password"
                        @input="errors.password = ''; testResult = null"
                      />
                      <button type="button" class="password-toggle" @click="showPassword = !showPassword">
                        <icon-preview v-if="!showPassword" theme="outline" size="15" />
                        <icon-preview-close v-else theme="outline" size="15" />
                      </button>
                    </div>
                    <span v-if="errors.password" class="error-msg">{{ errors.password }}</span>
                  </div>
                </div>
              </div>
            </form>
          </div>

          <!-- 对话框底部 -->
          <div class="dialog-footer">
            <button type="button" class="btn btn-ghost" @click="testConnection" :disabled="testing">
              <icon-wifi theme="outline" size="14" />
              {{ testing ? '测试中...' : '测试连接' }}
            </button>
            <div class="footer-right">
              <button type="button" class="btn btn-secondary" @click="closeModal">取消</button>
              <button type="button" class="btn btn-primary" @click="saveDataSource" :disabled="saving">
                {{ saving ? '保存中...' : (isEditing ? '保存更改' : '创建') }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '@/api'

const datasources = ref([])
const loading = ref(false)
const showModal = ref(false)
const isEditing = ref(false)
const testing = ref(false)
const saving = ref(false)
const showPassword = ref(false)
const testResult = ref(null)
const alert = ref({ show: false, type: '', message: '' })
const errors = ref({})

const form = ref({
  id: '', name: '', host: '', port: '1521', username: '', password: '', sid: ''
})

onMounted(() => loadDataSources())

async function loadDataSources() {
  loading.value = true
  try {
    const result = await api.get('/datasources')
    datasources.value = (result.data || []).map(ds => ({ ...ds, _testing: false }))
  } catch (error) {
    showAlert('error', '加载数据源失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

function showCreateModal() {
  isEditing.value = false
  form.value = { id: '', name: '', host: '', port: '1521', username: '', password: '', sid: '' }
  errors.value = {}
  testResult.value = null
  showPassword.value = false
  showModal.value = true
}

function editDataSource(ds) {
  isEditing.value = true
  form.value = { ...ds }
  errors.value = {}
  testResult.value = null
  showPassword.value = false
  showModal.value = true
}

function closeModal() {
  showModal.value = false
  testResult.value = null
}

function validate(requirePassword = true) {
  const e = {}
  if (!form.value.name?.trim()) e.name = '请输入数据源名称'
  if (!form.value.host?.trim()) e.host = '请输入主机地址'
  if (!form.value.port?.trim()) e.port = '请输入端口'
  if (!form.value.sid?.trim()) e.sid = '请输入 SID'
  if (!form.value.username?.trim()) e.username = '请输入用户名'
  if (requirePassword && !form.value.password?.trim()) e.password = '请输入密码'
  errors.value = e
  return Object.keys(e).length === 0
}

async function testConnection() {
  // 编辑模式且未填密码：直接用已保存的凭据测试
  if (isEditing.value && !form.value.password?.trim()) {
    if (!validate(false)) return
    testing.value = true
    testResult.value = null
    try {
      await api.post(`/datasources/${form.value.id}/test`)
      testResult.value = { type: 'success', message: '连接成功，数据库可正常访问' }
    } catch (error) {
      testResult.value = { type: 'error', message: '连接失败: ' + (error.response?.data?.error || error.message) }
    } finally {
      testing.value = false
    }
    return
  }
  // 新建或已填密码：用表单数据测试
  if (!validate(true)) return
  testing.value = true
  testResult.value = null
  try {
    await api.post('/cdc/datasource/test', {
      host: form.value.host, port: form.value.port,
      username: form.value.username, password: form.value.password, sid: form.value.sid
    })
    testResult.value = { type: 'success', message: '连接成功，数据库可正常访问' }
  } catch (error) {
    testResult.value = { type: 'error', message: '连接失败: ' + (error.response?.data?.error || error.message) }
  } finally {
    testing.value = false
  }
}

async function saveDataSource() {
  if (!validate(!isEditing.value || !!form.value.password?.trim())) return
  saving.value = true
  const config = {
    id: form.value.id || `ds-${Date.now()}`,
    name: form.value.name, host: form.value.host, port: form.value.port,
    username: form.value.username, password: form.value.password, sid: form.value.sid
  }
  try {
    if (isEditing.value) {
      await api.put(`/datasources/${config.id}`, config)
      showAlert('success', `数据源"${config.name}"已更新`)
    } else {
      await api.post('/datasources', config)
      showAlert('success', `数据源"${config.name}"已创建`)
    }
    closeModal()
    loadDataSources()
  } catch (error) {
    showAlert('error', '保存失败: ' + error.message)
  } finally {
    saving.value = false
  }
}

async function testDataSource(ds) {
  ds._testing = true
  try {
    await api.post(`/datasources/${ds.id}/test`)
    showAlert('success', `"${ds.name}" 连接成功`)
  } catch (error) {
    showAlert('error', `"${ds.name}" 连接失败: ` + error.message)
  } finally {
    ds._testing = false
  }
}

async function deleteDataSource(ds) {
  if (!confirm(`确定要删除数据源"${ds.name}"吗？`)) return
  try {
    await api.delete(`/datasources/${ds.id}`)
    showAlert('success', `数据源"${ds.name}"已删除`)
    loadDataSources()
  } catch (error) {
    showAlert('error', '删除失败: ' + error.message)
  }
}

function showAlert(type, message) {
  alert.value = { show: true, type, message }
  setTimeout(() => { alert.value.show = false }, 4000)
}

function formatDate(dateStr) {
  if (!dateStr || dateStr === 'Unknown') return '未知'
  try { return new Date(dateStr).toLocaleString('zh-CN') } catch { return dateStr }
}
</script>

<style scoped>
/* ── 页头 ── */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}
.page-header h2 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 20px;
  font-weight: 600;
  color: #172B4D;
  margin: 0 0 4px;
}
.subtitle { font-size: 13px; color: #5E6C84; margin: 0; }

/* ── 全局提示条 ── */
.alert-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 4px;
  font-size: 13px;
  margin-bottom: 16px;
}
.alert-bar-success { background: #E3FCEF; color: #006644; border: 1px solid #ABF5D1; }
.alert-bar-error   { background: #FFEBE6; color: #BF2600; border: 1px solid #FFBDAD; }

/* ── 数据源列表 ── */
.datasource-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.datasource-card {
  background: #fff;
  border-radius: 4px;
  padding: 14px 18px;
  display: flex;
  align-items: center;
  gap: 14px;
  box-shadow: 0 1px 1px rgba(9,30,66,0.25), 0 0 0 1px rgba(9,30,66,0.08);
  transition: box-shadow 0.15s;
}
.datasource-card:hover {
  box-shadow: 0 3px 6px -2px rgba(9,30,66,0.2), 0 0 0 1px rgba(9,30,66,0.08);
}
.ds-icon-wrap {
  width: 40px; height: 40px;
  background: #DEEBFF;
  border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.ds-info { flex: 1; min-width: 0; }
.ds-name { font-size: 14px; font-weight: 600; color: #172B4D; margin-bottom: 3px; }
.ds-conn {
  display: flex; align-items: center; gap: 4px;
  font-size: 12px; color: #5E6C84; margin-bottom: 2px;
}
.ds-meta {
  display: flex; align-items: center; gap: 4px;
  font-size: 11px; color: #97A0AF;
}
.dot { margin: 0 2px; }
.ds-actions { display: flex; gap: 6px; flex-shrink: 0; }

/* ── 空状态 ── */
.empty-state {
  text-align: center; padding: 64px 20px; color: #97A0AF;
}
.empty-icon { margin-bottom: 16px; }
.empty-state h3 { font-size: 16px; color: #5E6C84; margin: 0 0 6px; }
.empty-state p  { font-size: 13px; margin: 0; }
.mt-3 { margin-top: 16px; }

/* ── 对话框遮罩 ── */
.modal-overlay {
  position: fixed; inset: 0;
  background: rgba(9,30,66,0.54);
  display: flex; align-items: center; justify-content: center;
  z-index: 500; padding: 24px;
}
.modal-dialog {
  background: #fff;
  border-radius: 4px;
  width: 100%; max-width: 520px;
  box-shadow: 0 8px 16px -4px rgba(9,30,66,0.25), 0 0 0 1px rgba(9,30,66,0.08);
  display: flex; flex-direction: column;
  max-height: 90vh; overflow: hidden;
}

/* ── 对话框头部 ── */
.dialog-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #DFE1E6;
  flex-shrink: 0;
}
.dialog-title-wrap { display: flex; align-items: center; gap: 8px; }
.dialog-title { font-size: 16px; font-weight: 600; color: #172B4D; }
.dialog-close {
  background: none; border: none; cursor: pointer;
  color: #97A0AF; padding: 4px; border-radius: 3px;
  display: flex; align-items: center;
  transition: background 0.15s, color 0.15s;
}
.dialog-close:hover { background: #F4F5F7; color: #172B4D; }

/* ── 对话框主体 ── */
.dialog-body {
  padding: 20px;
  overflow-y: auto;
  flex: 1;
}

/* ── 连接测试结果 ── */
.test-result-bar {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 12px;
  border-radius: 4px;
  font-size: 13px;
  margin-bottom: 16px;
}
.test-result-success { background: #E3FCEF; color: #006644; border: 1px solid #ABF5D1; }
.test-result-error   { background: #FFEBE6; color: #BF2600; border: 1px solid #FFBDAD; }

/* ── 表单分组 ── */
.form-section { margin-bottom: 20px; }
.form-section:last-child { margin-bottom: 0; }
.form-section-title {
  font-size: 11px; font-weight: 700; color: #5E6C84;
  text-transform: uppercase; letter-spacing: 0.06em;
  margin-bottom: 12px;
  padding-bottom: 6px;
  border-bottom: 1px solid #DFE1E6;
}
.form-row { display: flex; gap: 12px; }
.form-field { display: flex; flex-direction: column; margin-bottom: 12px; }
.form-field:last-child { margin-bottom: 0; }
.flex-1 { flex: 1; }
.flex-3 { flex: 3; }

.field-label {
  font-size: 12px; font-weight: 600; color: #172B4D;
  margin-bottom: 5px;
}
.required { color: #DE350B; margin-left: 2px; }

.field-input {
  height: 36px;
  padding: 0 10px;
  border: 2px solid #DFE1E6;
  border-radius: 4px;
  font-size: 13px;
  color: #172B4D;
  outline: none;
  transition: border-color 0.15s;
  background: #fff;
  width: 100%;
  box-sizing: border-box;
}
.field-input:focus { border-color: #4C9AFF; }
.field-input.field-error { border-color: #DE350B; }
.field-input::placeholder { color: #C1C7D0; }

.error-msg { font-size: 11px; color: #DE350B; margin-top: 3px; }

/* ── 密码输入 ── */
.password-wrap { position: relative; }
.password-wrap .field-input { padding-right: 36px; }
.password-toggle {
  position: absolute; right: 8px; top: 50%; transform: translateY(-50%);
  background: none; border: none; cursor: pointer;
  color: #97A0AF; padding: 2px;
  display: flex; align-items: center;
  transition: color 0.15s;
}
.password-toggle:hover { color: #5E6C84; }

/* ── 对话框底部 ── */
.dialog-footer {
  display: flex; justify-content: space-between; align-items: center;
  padding: 14px 20px;
  border-top: 1px solid #DFE1E6;
  flex-shrink: 0;
  background: #F4F5F7;
}
.footer-right { display: flex; gap: 8px; }

/* ── 按钮变体 ── */
.btn {
  display: inline-flex; align-items: center; gap: 5px;
  padding: 0 14px; height: 32px;
  border: none; border-radius: 4px;
  font-size: 13px; font-weight: 500;
  cursor: pointer; transition: background 0.15s, opacity 0.15s;
  white-space: nowrap;
}
.btn:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-sm { height: 28px; padding: 0 10px; font-size: 12px; }
.btn-primary   { background: #0052CC; color: #fff; }
.btn-primary:hover:not(:disabled) { background: #0065FF; }
.btn-secondary { background: #F4F5F7; color: #172B4D; border: 1px solid #DFE1E6; }
.btn-secondary:hover:not(:disabled) { background: #EBECF0; }
.btn-ghost     { background: transparent; color: #0052CC; border: 1px solid #0052CC; }
.btn-ghost:hover:not(:disabled) { background: #DEEBFF; }
.btn-danger-ghost { background: transparent; color: #DE350B; border: 1px solid transparent; }
.btn-danger-ghost:hover { background: #FFEBE6; border-color: #FFBDAD; }

/* ── 过渡动画 ── */
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

.modal-enter-active { transition: opacity 0.2s; }
.modal-leave-active { transition: opacity 0.15s; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
.modal-enter-active .modal-dialog { animation: dialog-in 0.2s ease; }
.modal-leave-active .modal-dialog { animation: dialog-out 0.15s ease; }
@keyframes dialog-in  { from { transform: translateY(-12px); opacity: 0; } to { transform: none; opacity: 1; } }
@keyframes dialog-out { from { transform: none; opacity: 1; } to { transform: translateY(-8px); opacity: 0; } }

/* ── 加载 ── */
.loading { display: flex; flex-direction: column; align-items: center; padding: 48px; color: #97A0AF; gap: 10px; }
.spinner {
  width: 24px; height: 24px;
  border: 3px solid #DFE1E6; border-top-color: #0052CC;
  border-radius: 50%; animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
</style>
