<template>
  <div class="task-list-view">
    <div class="card">
      <div class="d-flex justify-between align-center mb-3">
        <h2 class="section-title">CDC 任务列表</h2>
        <button class="btn btn-primary" @click="$router.push('/tasks/create')">
          <icon-add-one theme="outline" size="14" /> 创建任务
        </button>
      </div>

      <div v-if="loading" class="loading">加载中...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else-if="tasks.length === 0" class="text-center text-muted">
        暂无任务，点击"创建任务"开始
      </div>
      <table v-else class="table">
        <thead>
          <tr>
            <th>任务名称</th>
            <th>数据库</th>
            <th>表数量</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="task in tasks" :key="task.id">
            <td><strong>{{ task.name }}</strong></td>
            <td>{{ task.database || 'Unknown' }}</td>
            <td>{{ task.tables }}</td>
            <td>{{ formatDate(task.created) }}</td>
            <td>
              <button 
                class="btn btn-sm btn-success" 
                @click="submitTask(task.id)"
                :disabled="submitting === task.id"
              >
                {{ submitting === task.id ? '提交中...' : '提交' }}
              </button>
              <button 
                class="btn btn-sm btn-primary" 
                @click="viewDetail(task.id)"
              >
                详情
              </button>
              <button 
                class="btn btn-sm btn-danger" 
                @click="deleteTask(task.id)"
              >
                删除
              </button>
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
      alert(`任务已提交！Job ID: ${data.data.job_id}`)
      loadTasks()
    } else {
      alert(`提交失败: ${data.error}`)
    }
  } catch (err) {
    alert(`提交失败: ${err.message}`)
  } finally {
    submitting.value = null
  }
}

const viewDetail = async (taskId) => {
  try {
    const data = await taskAPI.getDetail(taskId)
    if (data.success) {
      const task = data.data
      alert(`任务详情:\n\n名称: ${task.name}\nSchema: ${task.schema_name || 'N/A'}\n表: ${task.tables ? task.tables.join(', ') : 'N/A'}\n并行度: ${task.parallelism}\n输出路径: ${task.output_path}`)
    }
  } catch (err) {
    alert(`加载详情失败: ${err.message}`)
  }
}

const deleteTask = async (taskId) => {
  if (!confirm('确定要删除这个任务吗？此操作不可恢复！')) return

  try {
    const data = await taskAPI.delete(taskId)
    if (data.success) {
      alert('任务已删除')
      loadTasks()
    } else {
      alert(`删除失败: ${data.error}`)
    }
  } catch (err) {
    alert(`删除失败: ${err.message}`)
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
.section-title {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
  color: #333;
}
</style>
