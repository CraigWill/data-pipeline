<template>
  <div class="oss-view">
    <div class="page-header">
      <div>
        <h1>OSS 配置</h1>
        <p class="subtitle">管理多个 OSS 连接（阿里云对象存储），建任务时可选择用于同步/备份的 OSS</p>
      </div>
      <button class="btn btn-primary" data-tour="oss-create-btn" @click="openCreate">+ 新增 OSS 连接</button>
    </div>

    <transition name="fade">
      <div v-if="alert.show" :class="['alert', `alert-${alert.type}`]">{{ alert.message }}</div>
    </transition>

    <div v-if="loading" class="loading"><div class="spinner"></div><p>加载中...</p></div>
    <div v-else-if="items.length === 0" class="empty-state">
      <p>暂无 OSS 连接，点击右上角「新增 OSS 连接」创建。</p>
    </div>

    <div v-else class="oss-list">
      <div v-for="it in items" :key="it.id" class="oss-card">
        <div class="oss-card-head">
          <div class="oss-name">
            {{ it.name }}
            <span :class="['status-badge', `status-${(it.status || 'untested').toLowerCase()}`]">
              {{ it.status === 'SUCCESS' ? '可连接' : (it.status === 'FAILED' ? '连接失败' : '未测试') }}
            </span>
          </div>
          <div class="oss-actions">
            <button class="btn btn-sm btn-ghost" :disabled="it._testing" @click="testItem(it)">
              {{ it._testing ? '测试中...' : '测试连接' }}
            </button>
            <button class="btn btn-sm btn-secondary" @click="openEdit(it)">编辑</button>
            <button class="btn btn-sm btn-danger" @click="removeItem(it)">删除</button>
          </div>
        </div>
        <div class="oss-meta">
          <div><span class="k">Endpoint</span><span class="v">{{ it.endpoint }}</span></div>
          <div><span class="k">Bucket</span><span class="v">{{ it.bucketName }}</span></div>
          <div><span class="k">Prefix</span><span class="v">{{ it.prefix || '(空)' }}</span></div>
          <div><span class="k">AccessKeyId</span><span class="v">{{ maskAk(it.accessKeyId) }}</span></div>
        </div>
      </div>
    </div>

    <!-- 创建/编辑弹窗 -->
    <div v-if="showModal" class="oss-modal-mask">
      <div class="oss-dialog">
        <div class="oss-dialog-head">{{ isEditing ? '编辑 OSS 连接' : '新增 OSS 连接' }}</div>
        <div class="oss-dialog-body">
          <transition name="fade">
            <div v-if="testResult" :class="['test-bar', `test-${testResult.type}`]">{{ testResult.message }}</div>
          </transition>
          <div class="form-group">
            <label>名称 *</label>
            <input v-model="form.name" type="text" placeholder="例如：上海生产 OSS" />
          </div>
          <div class="form-group">
            <label>Endpoint *</label>
            <input v-model="form.endpoint" type="text" placeholder="https://oss-cn-shanghai.aliyuncs.com" @input="testResult = null" />
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>Bucket *</label>
              <input v-model="form.bucketName" type="text" placeholder="my-bucket" @input="testResult = null" />
            </div>
            <div class="form-group">
              <label>Prefix</label>
              <input v-model="form.prefix" type="text" placeholder="data-pipeline/" />
            </div>
          </div>
          <div class="form-group">
            <label>AccessKeyId *</label>
            <input v-model="form.accessKeyId" type="text" autocomplete="off" @input="testResult = null" />
          </div>
          <div class="form-group">
            <label>AccessKeySecret {{ isEditing ? '（不修改请留空）' : '*' }}</label>
            <input v-model="form.accessKeySecret" type="password" autocomplete="new-password"
                   :placeholder="isEditing ? '••••••（留空保留原值）' : ''" @input="testResult = null" />
          </div>
        </div>
        <div class="oss-dialog-foot">
          <button class="btn btn-ghost" :disabled="saving || testing" @click="saveAndTest">
            {{ testing ? '测试中...' : '保存并测试' }}
          </button>
          <div class="foot-right">
            <button class="btn btn-secondary" @click="closeModal">取消</button>
            <button class="btn btn-primary" :disabled="saving" @click="save">{{ saving ? '保存中...' : '保存' }}</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ossConnectionAPI } from '../api/index.js'

const items = ref([])
const loading = ref(false)
const showModal = ref(false)
const isEditing = ref(false)
const saving = ref(false)
const testing = ref(false)
const testResult = ref(null)
const alert = ref({ show: false, type: '', message: '' })
const form = ref(emptyForm())

function emptyForm() {
  return { id: '', name: '', endpoint: '', bucketName: '', prefix: '', accessKeyId: '', accessKeySecret: '' }
}
function maskAk(ak) {
  if (!ak) return '-'
  return ak.length <= 6 ? ak : ak.slice(0, 4) + '****' + ak.slice(-2)
}
function showAlert(type, message) {
  alert.value = { show: true, type, message }
  setTimeout(() => { alert.value.show = false }, 4000)
}

async function load() {
  loading.value = true
  try {
    const res = await ossConnectionAPI.list()
    items.value = (res.data || []).map(x => ({ ...x, _testing: false }))
  } catch (e) {
    showAlert('error', '加载失败: ' + (e.message || e))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.value = emptyForm()
  isEditing.value = false
  testResult.value = null
  showModal.value = true
}
function openEdit(it) {
  form.value = { id: it.id, name: it.name, endpoint: it.endpoint, bucketName: it.bucketName,
                 prefix: it.prefix, accessKeyId: it.accessKeyId, accessKeySecret: '' }
  isEditing.value = true
  testResult.value = null
  showModal.value = true
}
function closeModal() { showModal.value = false; testResult.value = null }

function validate() {
  if (!form.value.name || !form.value.endpoint || !form.value.bucketName || !form.value.accessKeyId) {
    showAlert('error', '请填写名称、Endpoint、Bucket、AccessKeyId')
    return false
  }
  if (!isEditing.value && !form.value.accessKeySecret) {
    showAlert('error', '请填写 AccessKeySecret')
    return false
  }
  return true
}

async function persist() {
  const body = { ...form.value }
  if (isEditing.value) {
    await ossConnectionAPI.update(body.id, body)
    return body.id
  } else {
    const res = await ossConnectionAPI.create(body)
    return res.data?.id
  }
}

async function save() {
  if (!validate()) return
  saving.value = true
  try {
    await persist()
    showAlert('success', isEditing.value ? 'OSS 连接已更新' : 'OSS 连接已创建')
    closeModal()
    load()
  } catch (e) {
    showAlert('error', '保存失败: ' + (e.message || e))
  } finally {
    saving.value = false
  }
}

async function saveAndTest() {
  if (!validate()) return
  testing.value = true
  testResult.value = null
  let id
  try {
    id = await persist()
    form.value.id = id
    isEditing.value = true
  } catch (e) {
    testResult.value = { type: 'error', message: '保存失败，无法测试: ' + (e.message || e) }
    testing.value = false
    return
  }
  try {
    const res = await ossConnectionAPI.test(id)
    if (res && res.success) {
      testResult.value = { type: 'success', message: res.data?.message || '连接成功，Bucket 可访问' }
    } else {
      testResult.value = { type: 'error', message: res?.error || res?.message || '连接失败' }
    }
    load()
  } catch (e) {
    testResult.value = { type: 'error', message: '连接失败: ' + (e.response?.data?.error || e.message) }
    load()
  } finally {
    testing.value = false
  }
}

async function testItem(it) {
  it._testing = true
  try {
    const res = await ossConnectionAPI.test(it.id)
    if (res && res.success) showAlert('success', `“${it.name}” 连接成功`)
    else showAlert('error', `“${it.name}” 连接失败: ` + (res?.error || res?.message || 'Bucket 不可访问'))
    load()
  } catch (e) {
    showAlert('error', `“${it.name}” 连接失败: ` + (e.response?.data?.error || e.message))
    load()
  } finally {
    it._testing = false
  }
}

async function removeItem(it) {
  if (!confirm(`确定删除 OSS 连接 “${it.name}”？`)) return
  try {
    await ossConnectionAPI.delete(it.id)
    showAlert('success', '已删除')
    load()
  } catch (e) {
    showAlert('error', '删除失败: ' + (e.message || e))
  }
}

onMounted(load)
</script>

<style scoped>
.oss-view { max-width: 1200px; margin: 0 auto; padding: 24px; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 20px; }
.page-header h1 { font-size: 22px; font-weight: 700; color: #172B4D; margin: 0; }
.subtitle { font-size: 13px; color: #5E6C84; margin-top: 6px; }

.alert { padding: 10px 14px; border-radius: 6px; margin-bottom: 16px; font-size: 13px; }
.alert-success { background: #E3FCEF; color: #006644; border: 1px solid #ABF5D1; }
.alert-error { background: #FFEBE6; color: #BF2600; border: 1px solid #FFBDAD; }

.loading, .empty-state { text-align: center; color: #5E6C84; padding: 48px 0; }
.spinner { width: 28px; height: 28px; border: 3px solid #DFE1E6; border-top-color: #0052CC; border-radius: 50%; margin: 0 auto 10px; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.oss-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap: 16px; }
.oss-card { border: 1px solid #DFE1E6; border-radius: 10px; background: #fff; padding: 16px; box-shadow: 0 1px 1px rgba(9,30,66,0.12); }
.oss-card-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.oss-name { font-size: 15px; font-weight: 600; color: #172B4D; display: flex; align-items: center; gap: 8px; }
.oss-actions { display: flex; gap: 6px; }

.status-badge { font-size: 11px; font-weight: 600; padding: 2px 8px; border-radius: 10px; }
.status-success { background: #E3FCEF; color: #006644; }
.status-failed { background: #FFEBE6; color: #BF2600; }
.status-untested { background: #DFE1E6; color: #42526E; }

.oss-meta { display: grid; gap: 6px; }
.oss-meta > div { display: flex; gap: 8px; font-size: 12px; }
.oss-meta .k { width: 96px; color: #7A869A; flex-shrink: 0; }
.oss-meta .v { color: #172B4D; word-break: break-all; }

.btn { border: none; border-radius: 6px; cursor: pointer; font-size: 13px; padding: 7px 14px; transition: background 0.2s ease, border-color 0.2s ease; }
.btn-sm { padding: 5px 10px; font-size: 12px; }
.btn-primary { background: #0052CC; color: #fff; }
.btn-primary:hover:not(:disabled) { background: #0747A6; }
.btn-secondary { background: #EBECF0; color: #42526E; }
.btn-secondary:hover:not(:disabled) { background: #DFE1E6; }
.btn-ghost { background: #fff; border: 1px solid #DFE1E6; color: #0052CC; }
.btn-ghost:hover:not(:disabled) { background: #DEEBFF; }
.btn-danger { background: #fff; border: 1px solid #FFBDAD; color: #BF2600; }
.btn-danger:hover:not(:disabled) { background: #FFEBE6; }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }

.oss-modal-mask { position: fixed; inset: 0; background: rgba(9,30,66,0.5); display: flex; align-items: center; justify-content: center; z-index: 1000; }
.oss-dialog { width: 520px; max-width: 92vw; background: #fff; border-radius: 10px; overflow: hidden; box-shadow: 0 8px 24px rgba(9,30,66,0.25); }
.oss-dialog-head { padding: 16px 20px; font-size: 16px; font-weight: 700; color: #172B4D; border-bottom: 1px solid #EBECF0; }
.oss-dialog-body { padding: 20px; max-height: 60vh; overflow-y: auto; }
.oss-dialog-foot { display: flex; align-items: center; justify-content: space-between; padding: 14px 20px; border-top: 1px solid #EBECF0; }
.foot-right { display: flex; gap: 8px; }

.form-group { margin-bottom: 16px; }
.form-group label { display: block; margin-bottom: 8px; font-size: 12px; font-weight: 600; color: #5E6C84; }
.form-group input { width: 100%; box-sizing: border-box; padding: 11px 14px; font-size: 14px; color: #172B4D; background: #FAFBFC; border: 2px solid #DFE1E6; border-radius: 6px; outline: none; transition: border-color 0.2s ease, box-shadow 0.2s ease; }
.form-group input:focus { border-color: #4C9AFF; background: #fff; box-shadow: 0 0 0 3px rgba(76,154,255,0.15); }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }

.test-bar { padding: 10px 12px; border-radius: 6px; font-size: 13px; margin-bottom: 16px; }
.test-success { background: #E3FCEF; color: #006644; border: 1px solid #ABF5D1; }
.test-error { background: #FFEBE6; color: #BF2600; border: 1px solid #FFBDAD; }

@media (max-width: 767px) {
  .oss-view { padding: 16px 12px; }

  /* 页头堆叠，按钮占满宽度 */
  .page-header { flex-direction: column; align-items: stretch; gap: 12px; }
  .page-header h1 { font-size: 18px; }

  /* 列表单列，避免固定最小宽度导致横向溢出 */
  .oss-list { grid-template-columns: 1fr; gap: 12px; }

  /* 卡片头部堆叠，操作按钮换行 */
  .oss-card-head { flex-direction: column; align-items: flex-start; gap: 8px; }
  .oss-actions { flex-wrap: wrap; width: 100%; }

  /* 元信息键值纵向堆叠、字体缩小、长值换行 */
  .oss-meta > div { flex-direction: column; gap: 2px; font-size: 11px; }
  .oss-meta .k { width: auto; }
  .oss-meta .v { word-break: break-all; }

  /* 弹窗底部铺满，表单单列 */
  .oss-modal-mask { align-items: flex-end; }
  .oss-dialog { width: 100%; max-width: 100%; border-radius: 12px 12px 0 0; max-height: 92vh; }
  .form-row { grid-template-columns: 1fr; }
}
</style>
