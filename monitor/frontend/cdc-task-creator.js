// CDC 任务创建器 JavaScript

const API_BASE = '/api';
let currentStep = 1;
let selectedDatasource = null;
let selectedSchema = null;
let selectedTables = [];

// 页面加载时初始化
document.addEventListener('DOMContentLoaded', () => {
    loadDataSources();
    
    // 数据源选择变化时加载 Schema
    document.getElementById('datasourceSelect').addEventListener('change', (e) => {
        const datasourceId = e.target.value;
        if (datasourceId) {
            selectedDatasource = datasourceId;
            loadSchemas(datasourceId);
        }
    });
    
    // Schema 选择变化时加载表
    document.getElementById('schemaSelect').addEventListener('change', (e) => {
        const schema = e.target.value;
        if (schema && selectedDatasource) {
            selectedSchema = schema;
            loadTables(selectedDatasource, schema);
        }
    });
});

// 加载数据源列表
async function loadDataSources() {
    const selectEl = document.getElementById('datasourceSelect');
    
    try {
        const response = await fetch(`${API_BASE}/datasources`);
        const result = await response.json();
        
        if (result.success && result.data.length > 0) {
            selectEl.innerHTML = '<option value="">请选择数据源</option>' +
                result.data.map(ds => `<option value="${ds.id}">${escapeHtml(ds.name)} (${ds.host}:${ds.port})</option>`).join('');
        } else {
            selectEl.innerHTML = '<option value="">暂无数据源，请先创建</option>';
        }
    } catch (error) {
        selectEl.innerHTML = '<option value="">加载失败</option>';
        showAlert('加载数据源失败: ' + error.message, 'error');
    }
}

// 加载 Schema 列表
async function loadSchemas(datasourceId) {
    const selectEl = document.getElementById('schemaSelect');
    selectEl.innerHTML = '<option value="">加载中...</option>';
    
    try {
        const response = await fetch(`${API_BASE}/datasources/${datasourceId}/schemas`);
        const result = await response.json();
        
        if (result.success && result.data.length > 0) {
            selectEl.innerHTML = '<option value="">请选择 Schema</option>' +
                result.data.map(schema => `<option value="${schema}">${schema}</option>`).join('');
        } else {
            selectEl.innerHTML = '<option value="">未找到 Schema</option>';
        }
    } catch (error) {
        selectEl.innerHTML = '<option value="">加载失败</option>';
        showAlert('加载 Schema 失败: ' + error.message, 'error');
    }
}

// 加载表列表
async function loadTables(datasourceId, schema) {
    const listEl = document.getElementById('tableList');
    listEl.innerHTML = '<div class="loading">加载中...</div>';
    
    try {
        const response = await fetch(`${API_BASE}/datasources/${datasourceId}/schemas/${schema}/tables`);
        const result = await response.json();
        
        if (result.success && result.data.length > 0) {
            listEl.innerHTML = result.data.map(table => `
                <div class="table-item">
                    <input type="checkbox" id="table-${table.name}" value="${table.name}" onchange="toggleTable('${table.name}')">
                    <label for="table-${table.name}" class="table-info">
                        <div class="table-name">${escapeHtml(table.name)}</div>
                        <div class="table-stats">${table.rows.toLocaleString()} 行 | ${table.columns} 列</div>
                    </label>
                </div>
            `).join('');
        } else {
            listEl.innerHTML = '<div class="loading">未找到表</div>';
        }
    } catch (error) {
        listEl.innerHTML = '<div class="loading">加载失败</div>';
        showAlert('加载表列表失败: ' + error.message, 'error');
    }
}

// 切换表选择
function toggleTable(tableName) {
    const checkbox = document.getElementById(`table-${tableName}`);
    if (checkbox.checked) {
        if (!selectedTables.includes(tableName)) {
            selectedTables.push(tableName);
        }
    } else {
        selectedTables = selectedTables.filter(t => t !== tableName);
    }
}

// 下一步
function nextStep() {
    // 验证当前步骤
    if (currentStep === 1) {
        if (!selectedDatasource) {
            showAlert('请选择数据源', 'error');
            return;
        }
    } else if (currentStep === 2) {
        if (!selectedSchema) {
            showAlert('请选择 Schema', 'error');
            return;
        }
    } else if (currentStep === 3) {
        if (selectedTables.length === 0) {
            showAlert('请至少选择一个表', 'error');
            return;
        }
    } else if (currentStep === 4) {
        // 最后一步，保存任务
        saveTask();
        return;
    }
    
    // 进入下一步
    currentStep++;
    updateStepUI();
}

// 上一步
function previousStep() {
    if (currentStep > 1) {
        currentStep--;
        updateStepUI();
    }
}

// 更新步骤 UI
function updateStepUI() {
    // 更新步骤指示器
    document.querySelectorAll('.step').forEach((step, index) => {
        const stepNum = index + 1;
        step.classList.remove('active', 'completed');
        
        if (stepNum < currentStep) {
            step.classList.add('completed');
        } else if (stepNum === currentStep) {
            step.classList.add('active');
        }
    });
    
    // 更新内容区域
    document.querySelectorAll('.step-content').forEach((content, index) => {
        content.classList.remove('active');
        if (index + 1 === currentStep) {
            content.classList.add('active');
        }
    });
    
    // 更新按钮
    const prevBtn = document.getElementById('prevBtn');
    const nextBtn = document.getElementById('nextBtn');
    
    prevBtn.style.display = currentStep > 1 ? 'block' : 'none';
    nextBtn.textContent = currentStep === 4 ? '保存并提交' : '下一步';
}

// 保存任务
async function saveTask() {
    const taskName = document.getElementById('taskName').value;
    const taskDescription = document.getElementById('taskDescription').value;
    const outputPath = document.getElementById('outputPath').value;
    const parallelism = parseInt(document.getElementById('parallelism').value);
    const splitSize = parseInt(document.getElementById('splitSize').value);
    
    if (!taskName) {
        showAlert('请输入任务名称', 'error');
        return;
    }
    
    const taskConfig = {
        id: `task-${Date.now()}`,
        name: taskName,
        description: taskDescription,
        created: new Date().toISOString(),
        datasource_id: selectedDatasource,
        schema: selectedSchema,
        tables: selectedTables,
        output_path: outputPath,
        parallelism: parallelism,
        split_size: splitSize
    };
    
    try {
        const response = await fetch(`${API_BASE}/cdc/tasks`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(taskConfig)
        });
        
        const result = await response.json();
        
        if (result.success) {
            showAlert('任务已创建！', 'success');
            
            // 询问是否立即提交
            if (confirm('任务已创建，是否立即提交到 Flink？')) {
                const taskId = result && result.data ? result.data.id : null;
                if (!taskId) {
                    showAlert('未获取到任务ID，无法提交', 'error');
                    return;
                }
                submitTask(taskId);
            } else {
                // 3秒后重置表单
                setTimeout(() => {
                    resetForm();
                }, 3000);
            }
        } else {
            showAlert('创建任务失败: ' + result.error, 'error');
        }
    } catch (error) {
        showAlert('创建任务失败: ' + error.message, 'error');
    }
}

// 提交任务
async function submitTask(taskId) {
    try {
        const response = await fetch(`${API_BASE}/cdc/tasks/${taskId}/submit`, {
            method: 'POST'
        });
        
        const result = await response.json();
        
        if (result.success) {
            showAlert(`任务已提交！Job ID: ${result.job_id}`, 'success');
            setTimeout(() => {
                resetForm();
            }, 3000);
        } else {
            showAlert('提交任务失败: ' + result.error, 'error');
        }
    } catch (error) {
        showAlert('提交任务失败: ' + error.message, 'error');
    }
}

// 重置表单
function resetForm() {
    currentStep = 1;
    selectedDatasource = null;
    selectedSchema = null;
    selectedTables = [];
    
    document.getElementById('datasourceSelect').value = '';
    document.getElementById('schemaSelect').innerHTML = '<option value="">请先选择数据源</option>';
    document.getElementById('tableList').innerHTML = '<div class="loading">请先选择 Schema</div>';
    document.getElementById('taskName').value = '';
    document.getElementById('taskDescription').value = '';
    document.getElementById('outputPath').value = './output/cdc';
    document.getElementById('parallelism').value = '2';
    document.getElementById('splitSize').value = '8096';
    
    updateStepUI();
}

// 显示提示信息
function showAlert(message, type) {
    const alertEl = document.getElementById('alert');
    alertEl.textContent = message;
    alertEl.className = `alert alert-${type} active`;
    
    setTimeout(() => {
        alertEl.classList.remove('active');
    }, 5000);
}

// HTML 转义
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
