<template>
  <div class="cdc-events-view">
    <div class="page-header">
      <h2><icon-chart-histogram theme="outline" size="20" /> CDC 事件监控</h2>
      <p class="subtitle">实时监控数据变更捕获事件</p>
    </div>

    <!-- 实时数据速率条 -->
    <div class="realtime-rate-bar">
      <div class="rate-header">
        <span class="rate-icon"><icon-flash theme="filled" size="16" fill="#79F2C0" /></span>
        <span class="rate-title">实时数据流</span>
        <span class="connection-status" :class="{ connected: sseConnected }">
          {{ sseConnected ? '● 已连接' : '○ 未连接' }}
        </span>
        <span class="rate-value" :class="{ active: stats.eventsPerSecond > 0 }">
          {{ stats.eventsPerSecond || 0 }} 条/秒
        </span>
        <span class="rate-date">{{ stats.date || '今日' }} (每日0点重置)</span>
      </div>
      <div class="rate-progress-container">
        <div 
          class="rate-progress" 
          :style="{ width: rateProgressWidth + '%' }"
          :class="{ pulsing: stats.eventsPerSecond > 0 }"
        ></div>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon"><icon-edit-two theme="outline" size="22" fill="#0052CC" /></div>
        <div class="stat-content">
          <div class="stat-value">{{ formatStatNumber(stats.totalEvents) }}</div>
          <div class="stat-label">今日总事件</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon"><icon-add-one theme="outline" size="22" fill="#00875A" /></div>
        <div class="stat-content">
          <div class="stat-value">{{ formatStatNumber(stats.insertEvents) }}</div>
          <div class="stat-label">INSERT 事件</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon"><icon-edit theme="outline" size="22" fill="#FF8B00" /></div>
        <div class="stat-content">
          <div class="stat-value">{{ formatStatNumber(stats.updateEvents) }}</div>
          <div class="stat-label">UPDATE 事件</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon"><icon-delete theme="outline" size="22" fill="#DE350B" /></div>
        <div class="stat-content">
          <div class="stat-value">{{ formatStatNumber(stats.deleteEvents) }}</div>
          <div class="stat-label">DELETE 事件</div>
        </div>
      </div>
    </div>

    <!-- 实时趋势图 -->
    <div class="chart-container">
      <div class="chart-header">
        <h3><icon-chart-line theme="outline" size="16" /> 实时事件趋势 (每秒)</h3>
        <div class="chart-controls">
          <button class="btn btn-sm" @click="testTrendChart"><icon-experiment theme="outline" size="13" /> 测试</button>
          <button class="btn btn-sm" @click="clearTrendData"><icon-delete theme="outline" size="13" /> 清空</button>
          <span class="trend-info">{{ trendDataPoints.length }} 个数据点</span>
        </div>
      </div>
      <div class="chart-wrapper" style="height: 260px;">
        <canvas ref="trendChartCanvas" style="width: 100%; height: 100%;"></canvas>
      </div>
    </div>

    <!-- 柱状图 -->
    <div class="chart-container">
      <div class="chart-header">
        <h3><icon-trend-two theme="outline" size="16" /> 24小时事件分布 <span v-if="chartTotal" class="chart-total">(总计: {{ chartTotal.toLocaleString() }} 条)</span></h3>
        <button class="btn btn-sm" @click="loadDailyStats"><icon-refresh theme="outline" size="13" /> 刷新</button>
      </div>
      <div class="table-tags">
        <span 
          class="table-tag" 
          :class="{ active: selectedChartTable === '' }"
          @click="selectChartTable('')"
        >
          全部表
        </span>
        <span 
          v-for="t in chartTables" 
          :key="t.name"
          class="table-tag"
          :class="{ active: selectedChartTable === t.name }"
          @click="selectChartTable(t.name)"
        >
          {{ t.name }}
          <span class="tag-stats">{{ formatCount(t.count) }}条 / {{ t.fileCount }}文件</span>
        </span>
      </div>
      <div class="chart-wrapper">
        <canvas ref="chartCanvas"></canvas>
      </div>
    </div>

    <!-- 文件列表 -->
    <div class="files-container">
      <div class="files-header">
        <h3><icon-folder-open theme="outline" size="16" /> CDC 文件列表</h3>
        <div class="files-filters">
          <label>选择日期:</label>
          <select v-model="selectedDate" @change="loadFiles">
            <option v-for="d in availableDates" :key="d" :value="d">{{ d }}</option>
          </select>
          <button class="btn btn-sm" @click="loadFiles"><icon-refresh theme="outline" size="13" /> 刷新</button>
        </div>
      </div>

      <div v-if="filesLoading" class="loading">
        <div class="spinner"></div>
        <p>加载文件列表...</p>
      </div>

      <div v-else-if="files.length === 0" class="empty-state">
        <div class="empty-icon">📭</div>
        <h3>暂无文件</h3>
        <p>{{ selectedDate }} 没有 CDC 数据文件</p>
      </div>

      <table v-else class="files-table">
        <thead>
          <tr>
            <th>文件名</th>
            <th>表名</th>
            <th>时间</th>
            <th>记录数</th>
            <th>大小</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="file in files" :key="file.id">
            <td class="file-name">{{ file.name }}</td>
            <td><span class="table-badge">{{ file.table }}</span></td>
            <td>{{ file.hour }}</td>
            <td>{{ file.lineCount.toLocaleString() }}</td>
            <td>{{ file.sizeFormatted }}</td>
            <td>
              <button class="btn btn-sm btn-primary" @click="viewFileContent(file)">
                <icon-file-text theme="outline" size="13" /> 查看明细
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="files-summary" v-if="files.length > 0">
        共 {{ files.length }} 个文件，{{ totalRecords.toLocaleString() }} 条记录
      </div>
    </div>

    <!-- 文件内容模态框 -->
    <div v-if="showContentModal" class="modal-overlay">
      <div class="modal-content">
        <div class="modal-header">
          <h3><icon-file-text theme="outline" size="16" /> 文件内容</h3>
          <button class="modal-close" @click="closeModal"><icon-close theme="outline" size="16" /></button>
        </div>
        <div class="modal-info">
          <span>文件: {{ currentFile?.name }}</span>
          <span>表: {{ currentFile?.table }}</span>
          <span>总记录: {{ contentTotal.toLocaleString() }} 条</span>
        </div>
        <div class="modal-body">
          <div v-if="contentLoading" class="loading">
            <div class="spinner"></div>
            <p>加载数据...</p>
          </div>
          <table v-else class="content-table">
            <thead>
              <tr>
                <th>#</th>
                <th v-for="(_, idx) in (contentRows[0]?.fields || [])" :key="idx">
                  字段{{ idx + 1 }}
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in contentRows" :key="row.lineNumber">
                <td class="line-number">{{ row.lineNumber }}</td>
                <td v-for="(field, idx) in row.fields" :key="idx" class="field-cell">
                  {{ field }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="modal-footer">
          <div class="pagination">
            <button 
              class="btn btn-secondary" 
              :disabled="contentPage === 1"
              @click="loadFileContent(contentPage - 1)"
            >
              上一页
            </button>
            <span class="page-info">
              第 {{ contentPage }} 页 / 共 {{ contentTotalPages }} 页
            </span>
            <button 
              class="btn btn-secondary" 
              :disabled="contentPage === contentTotalPages"
              @click="loadFileContent(contentPage + 1)"
            >
              下一页
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { cdcEventsAPI } from '../api/index.js'
import { Chart, registerables } from 'chart.js'

Chart.register(...registerables)

const stats = ref({
  totalEvents: 0,
  insertEvents: 0,
  updateEvents: 0,
  deleteEvents: 0,
  eventsPerSecond: 0,
  date: ''
})

const rateProgressWidth = computed(() => {
  const rate = stats.value.eventsPerSecond || 0
  return Math.min(rate * 2, 100)
})

let chartInstance = null
let trendChartInstance = null
let eventSource = null

const chartCanvas = ref(null)
const trendChartCanvas = ref(null)
const selectedChartTable = ref('')
const chartTables = ref([])
const chartTotal = ref(0)
const sseConnected = ref(false)

// 实时趋势数据
const trendDataPoints = ref([])
const maxTrendPoints = 0 // 0 表示不限制，保留从0点开始的所有数据

// 文件列表相关
const files = ref([])
const filesLoading = ref(false)
const selectedDate = ref('')
const availableDates = ref([])

// 文件内容模态框相关
const showContentModal = ref(false)
const currentFile = ref(null)
const contentRows = ref([])
const contentLoading = ref(false)
const contentPage = ref(1)
const contentTotal = ref(0)
const contentTotalPages = ref(0)
const contentPageSize = 50

const totalRecords = computed(() => {
  return files.value.reduce((sum, f) => sum + f.lineCount, 0)
})

onMounted(async () => {
  console.log('🚀 Component mounted')
  loadTodayStats()
  loadDailyStats()
  loadAvailableDates()
  
  // 等待 DOM 完全渲染
  await nextTick()
  
  // 延迟初始化图表，确保 canvas 已渲染
  setTimeout(() => {
    console.log('⏰ Attempting to initialize trend chart after timeout')
    initTrendChart()
  }, 100)
  
  startSSE()
})

onUnmounted(() => {
  stopSSE()
  
  // 清理图表实例
  if (chartInstance) {
    chartInstance.destroy()
    chartInstance = null
  }
  
  if (trendChartInstance) {
    trendChartInstance.destroy()
    trendChartInstance = null
  }
})

async function loadTodayStats() {
  try {
    const response = await cdcEventsAPI.todayStats()
    if (response.success && response.data) {
      stats.value = response.data
    }
  } catch (error) {
    console.error('加载当日统计失败:', error)
  }
}

async function loadDailyStats() {
  try {
    const response = await cdcEventsAPI.dailyStats(selectedChartTable.value)
    if (response.success && response.data) {
      // 确保 tables 是数组
      chartTables.value = Array.isArray(response.data.tables) ? response.data.tables : []
      chartTotal.value = response.data.total || 0
      await nextTick()
      renderChart(response.data)
    }
  } catch (error) {
    console.error('加载日期统计失败:', error)
    // 设置默认值避免错误
    chartTables.value = []
    chartTotal.value = 0
  }
}

async function loadAvailableDates() {
  try {
    const response = await cdcEventsAPI.dates()
    if (response.success && response.data) {
      availableDates.value = response.data || []
      // 默认选择第一个日期（最新）
      if (availableDates.value.length > 0) {
        selectedDate.value = availableDates.value[0]
        loadFiles()
      }
    }
  } catch (error) {
    console.error('加载日期列表失败:', error)
    // 使用今天的日期作为默认值
    selectedDate.value = new Date().toISOString().split('T')[0]
    loadFiles()
  }
}

async function loadFiles() {
  console.log('📂 loadFiles called, selectedDate:', selectedDate.value)
  filesLoading.value = true
  try {
    const response = await cdcEventsAPI.files(selectedDate.value)
    console.log('📂 API response:', response)
    if (response.success && response.data) {
      files.value = response.data.files || []
      console.log('📂 Files loaded:', files.value.length)
    } else {
      console.log('📂 No data in response')
      files.value = []
    }
  } catch (error) {
    console.error('加载文件列表失败:', error)
    files.value = []
  } finally {
    filesLoading.value = false
  }
}

function viewFileContent(file) {
  currentFile.value = file
  contentPage.value = 1
  showContentModal.value = true
  loadFileContent(1)
}

async function loadFileContent(page) {
  if (!currentFile.value) {
    console.error('loadFileContent: currentFile is null')
    return
  }
  
  contentLoading.value = true
  contentPage.value = page
  try {
    const response = await cdcEventsAPI.fileContent(
      currentFile.value.id, 
      page, 
      contentPageSize
    )
    if (response.success && response.data) {
      contentRows.value = response.data.rows || []
      contentTotal.value = response.data.total || 0
      contentTotalPages.value = response.data.totalPages || 0
    }
  } catch (error) {
    console.error('加载文件内容失败:', error)
    contentRows.value = []
  } finally {
    contentLoading.value = false
  }
}

function closeModal() {
  showContentModal.value = false
  currentFile.value = null
  contentRows.value = []
}

function selectChartTable(tableName) {
  selectedChartTable.value = tableName
  loadDailyStats()
}

function renderChart(data) {
  if (!chartCanvas.value) return
  
  if (chartInstance) {
    chartInstance.destroy()
  }
  
  const ctx = chartCanvas.value.getContext('2d')
  
  chartInstance = new Chart(ctx, {
    type: 'bar',
    data: {
      labels: data.labels || [],
      datasets: [{
        label: 'CDC 事件数量',
        data: data.values || [],
        backgroundColor: 'rgba(0, 82, 204, 0.65)',
        borderColor: 'rgba(0, 82, 204, 1)',
        borderWidth: 1,
        borderRadius: 3
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: {
            label: function(context) {
              return `事件数: ${context.raw.toLocaleString()} 条`
            }
          }
        }
      },
      scales: {
        x: { grid: { display: false }, title: { display: true, text: '小时' } },
        y: {
          beginAtZero: true,
          title: { display: true, text: '事件数量' },
          ticks: { callback: function(value) { return value.toLocaleString() } }
        }
      }
    }
  })
}

function startSSE() {
  stopSSE()
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
  const sseUrl = `${baseUrl}/cdc/events/stream`
  
  // 使用 fetch + ReadableStream 替代 EventSource，支持 JWT 认证
  const token = localStorage.getItem('token')
  
  fetch(sseUrl, {
    headers: {
      'Authorization': token ? `Bearer ${token}` : '',
      'Accept': 'text/event-stream'
    }
  })
  .then(response => {
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`)
    }
    
    sseConnected.value = true
    
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    
    // 保存 reader 以便后续关闭
    eventSource = { reader, close: () => reader.cancel() }
    
    // 递归读取流数据
    function readStream() {
      reader.read().then(({ done, value }) => {
        if (done) {
          console.log('SSE 流结束')
          sseConnected.value = false
          setTimeout(startSSE, 3000)
          return
        }
        
        // 解码数据并添加到缓冲区
        buffer += decoder.decode(value, { stream: true })
        
        // 处理缓冲区中的完整消息
        const lines = buffer.split('\n')
        buffer = lines.pop() || '' // 保留不完整的行
        
        let eventType = ''
        let eventData = ''
        
        for (const line of lines) {
          if (line.startsWith('event:')) {
            eventType = line.substring(6).trim()
          } else if (line.startsWith('data:')) {
            eventData = line.substring(5).trim()
          } else if (line === '' && eventData) {
            // 空行表示消息结束，处理事件
            try {
              const data = JSON.parse(eventData)
              
              // 验证数据结构
              if (data && typeof data === 'object') {
                stats.value = data
                
                // 添加到趋势图
                if (typeof data.eventsPerSecond === 'number') {
                  addTrendDataPoint(data.eventsPerSecond)
                }
              }
              
              if (eventType === 'stats') {
                console.log('收到 stats 事件:', data)
              }
            } catch (e) {
              console.error('解析 SSE 数据失败:', e, eventData)
            }
            
            // 重置状态
            eventType = ''
            eventData = ''
          }
        }
        
        // 继续读取
        readStream()
      }).catch(error => {
        console.error('SSE 读取错误:', error)
        sseConnected.value = false
        stopSSE()
        setTimeout(startSSE, 3000)
      })
    }
    
    readStream()
  })
  .catch(error => {
    console.error('SSE 连接失败:', error)
    sseConnected.value = false
    setTimeout(startSSE, 3000)
  })
}

function stopSSE() {
  if (eventSource) {
    if (eventSource.close) {
      eventSource.close()
    } else if (eventSource.reader) {
      eventSource.reader.cancel()
    }
    eventSource = null
  }
  sseConnected.value = false
}

function formatCount(count) {
  if (count >= 1000000) return (count / 1000000).toFixed(1) + 'M'
  if (count >= 1000) return (count / 1000).toFixed(1) + 'K'
  return count.toString()
}

function formatStatNumber(num) {
  if (!num) return '0'
  return num.toLocaleString()
}

function initTrendChart() {
  console.log('initTrendChart called')
  console.log('trendChartCanvas.value:', trendChartCanvas.value)
  
  if (!trendChartCanvas.value) {
    console.error('❌ Trend chart canvas not found!')
    return
  }
  
  // 如果已经存在图表实例，先销毁它
  if (trendChartInstance) {
    console.log('🔄 Destroying existing trend chart instance')
    trendChartInstance.destroy()
    trendChartInstance = null
  }
  
  try {
    const ctx = trendChartCanvas.value.getContext('2d')
    console.log('Canvas context obtained:', ctx)
    
    trendChartInstance = new Chart(ctx, {
      type: 'line',
      data: {
        labels: [],
        datasets: [{
          label: '事件/秒',
          data: [],
          borderColor: '#0052CC',
          backgroundColor: 'rgba(0, 82, 204, 0.1)',
          borderWidth: 2,
          fill: true,
          tension: 0.4,
          pointRadius: 1,
          pointHoverRadius: 3
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: {
          duration: 300
        },
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              label: function(context) {
                return `速率: ${context.raw} 条/秒`
              }
            }
          }
        },
        scales: {
          x: {
            grid: { display: false },
            title: { display: true, text: '时间 (今日 00:00 至今)' },
            ticks: {
              maxTicksLimit: 20,
              autoSkip: true,
              callback: function(value, index) {
                // 显示部分标签以避免拥挤
                const label = this.getLabelForValue(value)
                // 每隔一定间隔显示标签
                const totalLabels = this.chart.data.labels.length
                const skipInterval = Math.ceil(totalLabels / 15)
                return index % skipInterval === 0 ? label : ''
              }
            }
          },
          y: {
            beginAtZero: true,
            title: { display: true, text: '事件数/秒' },
            ticks: {
              callback: function(value) {
                return Math.round(value)
              }
            }
          }
        }
      }
    })
    
    console.log('✅ Trend chart initialized successfully:', trendChartInstance)
  } catch (error) {
    console.error('❌ Error initializing trend chart:', error)
  }
}

function addTrendDataPoint(value) {
  const now = new Date()
  const timeLabel = now.toLocaleTimeString('zh-CN', { hour12: false })
  
  // 添加新数据点
  trendDataPoints.value.push({
    time: timeLabel,
    value: value,
    timestamp: now.getTime()
  })
  
  // 不限制数据点数量，保留从0点开始的所有数据
  // 如果需要限制，可以检查是否跨天，跨天后清空
  const firstPoint = trendDataPoints.value[0]
  if (firstPoint) {
    const firstDate = new Date(firstPoint.timestamp).toDateString()
    const currentDate = now.toDateString()
    if (firstDate !== currentDate) {
      // 跨天了，清空旧数据
      trendDataPoints.value = [{
        time: timeLabel,
        value: value,
        timestamp: now.getTime()
      }]
      console.log('🌅 New day detected, trend data reset')
    }
  }
  
  console.log(`📊 Added trend point: ${timeLabel} = ${value}, total points: ${trendDataPoints.value.length}`)
  
  // 如果图表还没初始化，尝试初始化
  if (!trendChartInstance && trendChartCanvas.value) {
    console.log('📊 Chart not initialized, initializing now...')
    initTrendChart()
  }
  
  // 更新图表
  updateTrendChart()
}

function updateTrendChart() {
  if (!trendChartInstance) {
    console.warn('⚠️ Trend chart instance not found, skipping update')
    return
  }
  
  trendChartInstance.data.labels = trendDataPoints.value.map(p => p.time)
  trendChartInstance.data.datasets[0].data = trendDataPoints.value.map(p => p.value)
  trendChartInstance.update('none') // 使用 'none' 模式避免动画延迟
  
  console.log('📈 Trend chart updated with', trendDataPoints.value.length, 'points')
}

function clearTrendData() {
  trendDataPoints.value = []
  updateTrendChart()
}

function testTrendChart() {
  console.log('🧪 Testing trend chart...')
  console.log('Canvas element:', trendChartCanvas.value)
  console.log('Chart instance:', trendChartInstance)
  
  if (!trendChartInstance) {
    console.log('Chart not initialized, initializing now...')
    initTrendChart()
  }
  
  // 添加测试数据 - 模拟从今天0点到现在的数据
  const now = new Date()
  const startOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0)
  const currentTime = now.getTime()
  const startTime = startOfDay.getTime()
  
  // 每5分钟一个数据点
  const interval = 5 * 60 * 1000 // 5分钟
  
  for (let time = startTime; time <= currentTime; time += interval) {
    const testDate = new Date(time)
    const timeLabel = testDate.toLocaleTimeString('zh-CN', { hour12: false })
    const testValue = Math.floor(Math.random() * 1000)
    
    trendDataPoints.value.push({
      time: timeLabel,
      value: testValue,
      timestamp: time
    })
  }
  
  updateTrendChart()
  console.log(`✅ Test data added: ${trendDataPoints.value.length} points from 00:00 to now`)
}
</script>

<style scoped>
.page-header h2 {
  font-size: 20px;
  font-weight: 600;
  margin: 0 0 4px 0;
  color: #172B4D;
}

.subtitle {
  color: #5E6C84;
  font-size: 13px;
  margin: 0;
}

/* 实时速率条 — 保留深色风格 */
.realtime-rate-bar {
  background: #0747A6;
  border-radius: 4px;
  padding: 14px 18px;
  margin-bottom: 16px;
  box-shadow: 0 1px 1px rgba(9,30,66,0.25), 0 0 0 1px rgba(9,30,66,0.08);
}

.rate-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.rate-icon { font-size: 16px; }

.rate-title {
  font-size: 13px;
  color: rgba(255,255,255,0.8);
  font-weight: 500;
}

.connection-status {
  font-size: 11px;
  color: rgba(255,255,255,0.5);
  padding: 2px 8px;
  border-radius: 3px;
  background: rgba(255,255,255,0.1);
}

.connection-status.connected {
  color: #79F2C0;
  background: rgba(121,242,192,0.15);
}

.rate-value {
  font-size: 18px;
  font-weight: 700;
  color: #79F2C0;
  margin-left: auto;
}

.rate-date {
  font-size: 11px;
  color: rgba(255,255,255,0.45);
}

.rate-progress-container {
  height: 4px;
  background: rgba(255,255,255,0.1);
  border-radius: 2px;
  overflow: hidden;
}

.rate-progress {
  height: 100%;
  background: linear-gradient(90deg, #79F2C0 0%, #4C9AFF 100%);
  border-radius: 2px;
  transition: width 0.5s ease;
  min-width: 2px;
}

.rate-progress.pulsing {
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

/* 统计卡片 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.stat-card {
  background: #fff;
  border-radius: 4px;
  padding: 16px 18px;
  display: flex;
  align-items: center;
  gap: 12px;
  box-shadow: 0 1px 1px rgba(9,30,66,0.25), 0 0 0 1px rgba(9,30,66,0.08);
  border-top: 3px solid #0052CC;
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: #172B4D;
  line-height: 1.2;
}

.stat-label {
  font-size: 11px;
  color: #5E6C84;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-top: 3px;
}

/* 图表 */
.chart-container {
  background: #fff;
  border-radius: 4px;
  padding: 18px 20px;
  margin-bottom: 16px;
  box-shadow: 0 1px 1px rgba(9,30,66,0.25), 0 0 0 1px rgba(9,30,66,0.08);
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.chart-header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: #172B4D;
}

.chart-controls {
  display: flex;
  align-items: center;
  gap: 10px;
}

.trend-info {
  font-size: 11px;
  color: #97A0AF;
  padding: 4px 8px;
  background: #F4F5F7;
  border-radius: 3px;
}

.chart-total {
  font-size: 12px;
  font-weight: 400;
  color: #0052CC;
}

.table-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #DFE1E6;
}

.table-tag {
  padding: 4px 10px;
  border-radius: 3px;
  font-size: 12px;
  background: #F4F5F7;
  color: #5E6C84;
  cursor: pointer;
  transition: background 0.15s;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  border: 1px solid #DFE1E6;
}

.table-tag:hover {
  background: #EBECF0;
  color: #172B4D;
}

.table-tag.active {
  background: #0052CC;
  color: #fff;
  border-color: #0052CC;
}

.table-tag.active .tag-stats {
  color: rgba(255,255,255,0.75);
}

.tag-stats {
  font-size: 10px;
  color: #97A0AF;
}

.chart-wrapper {
  height: 260px;
  position: relative;
}

/* 文件列表 */
.files-container {
  background: #fff;
  border-radius: 4px;
  padding: 18px 20px;
  box-shadow: 0 1px 1px rgba(9,30,66,0.25), 0 0 0 1px rgba(9,30,66,0.08);
}

.files-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.files-header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: #172B4D;
}

.files-filters {
  display: flex;
  align-items: center;
  gap: 8px;
}

.files-filters label {
  font-size: 12px;
  color: #5E6C84;
}

.files-filters select {
  padding: 6px 10px;
  border: 2px solid #DFE1E6;
  border-radius: 4px;
  font-size: 13px;
  color: #172B4D;
  outline: none;
}

.files-filters select:focus {
  border-color: #4C9AFF;
}

.files-table {
  width: 100%;
  border-collapse: collapse;
}

.files-table th {
  padding: 8px 12px;
  text-align: left;
  font-size: 11px;
  font-weight: 700;
  color: #5E6C84;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  border-bottom: 2px solid #DFE1E6;
}

.files-table td {
  padding: 10px 12px;
  font-size: 13px;
  color: #172B4D;
  border-bottom: 1px solid #DFE1E6;
}

.files-table tbody tr:hover td {
  background: #F4F5F7;
}

.files-table tbody tr:last-child td {
  border-bottom: none;
}

.file-name {
  font-family: monospace;
  font-size: 11px;
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #5E6C84;
}

.table-badge {
  display: inline-block;
  padding: 2px 6px;
  background: #DEEBFF;
  color: #0052CC;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 700;
}

.files-summary {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #DFE1E6;
  font-size: 12px;
  color: #97A0AF;
  text-align: right;
}

/* 模态框 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(9,30,66,0.54);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 24px;
}

.modal-content {
  background: #fff;
  border-radius: 4px;
  width: 100%;
  max-width: 1100px;
  max-height: 88vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 16px -4px rgba(9,30,66,0.25), 0 0 0 1px rgba(9,30,66,0.08);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #DFE1E6;
}

.modal-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #172B4D;
}

.modal-close {
  background: none;
  border: none;
  font-size: 16px;
  cursor: pointer;
  color: #97A0AF;
  padding: 4px 6px;
  border-radius: 3px;
  transition: background 0.15s;
}

.modal-close:hover {
  background: #F4F5F7;
  color: #172B4D;
}

.modal-info {
  display: flex;
  gap: 20px;
  padding: 10px 20px;
  background: #F4F5F7;
  font-size: 12px;
  color: #5E6C84;
  border-bottom: 1px solid #DFE1E6;
}

.modal-body {
  flex: 1;
  overflow: auto;
  padding: 16px 20px;
}

.content-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.content-table th {
  padding: 8px 10px;
  text-align: left;
  font-size: 11px;
  font-weight: 700;
  color: #5E6C84;
  text-transform: uppercase;
  background: #F4F5F7;
  border: 1px solid #DFE1E6;
  position: sticky;
  top: 0;
}

.content-table td {
  padding: 8px 10px;
  border: 1px solid #DFE1E6;
  color: #172B4D;
}

.line-number {
  background: #F4F5F7;
  color: #97A0AF;
  font-family: monospace;
  text-align: center;
  width: 50px;
}

.field-cell {
  font-family: monospace;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.modal-footer {
  padding: 12px 20px;
  border-top: 1px solid #DFE1E6;
  display: flex;
  justify-content: center;
}

.pagination {
  display: flex;
  align-items: center;
  gap: 16px;
}

.page-info {
  font-size: 13px;
  color: #5E6C84;
}

/* 覆盖全局 btn（scoped 内局部按钮） */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0 12px;
  height: 32px;
  border: none;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s;
}

.btn-sm { height: 26px; padding: 0 8px; font-size: 12px; }

.btn-primary { background: #0052CC; color: #fff; }
.btn-primary:hover { background: #0065FF; }

.btn-secondary { background: #F4F5F7; color: #172B4D; border: 1px solid #DFE1E6; }
.btn-secondary:hover:not(:disabled) { background: #EBECF0; }
.btn-secondary:disabled { opacity: 0.5; cursor: not-allowed; }

.loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px;
  color: #97A0AF;
  gap: 10px;
}

.spinner {
  width: 24px;
  height: 24px;
  border: 3px solid #DFE1E6;
  border-top-color: #0052CC;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.empty-state {
  text-align: center;
  padding: 56px 20px;
  color: #97A0AF;
}

.empty-icon { font-size: 48px; margin-bottom: 12px; opacity: 0.5; }
.empty-state h3 { font-size: 16px; color: #5E6C84; margin: 0 0 6px; }
.empty-state p { font-size: 13px; margin: 0; }
</style>
