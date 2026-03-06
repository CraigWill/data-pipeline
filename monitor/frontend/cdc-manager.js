// CDC 任务管理 JavaScript

const API_BASE = '/api';

let currentStep = 1;
let dbConfig = {};
let selectedSchema = '';
let selectedTables = [];
let taskConfig = {};

// 初始化
document.addEventListener('DOMContentLoaded', () => {
    updateStepUI();
});

// 更新步骤 UI
function updateStepUI() {
    // 更新步骤指示器
    document.querySelectorAll('.wizard-step').forEach((step, index) => {
        const stepNum = index + 1;
        step.classList.remove('active', 'completed');
        
        if (stepNum < currentStep) {
            step.classList.add('completed');
        } else if (stepNum === currentStep) {
            step.classList.add('active');
        }
    });

    // 更新面板显示
    document.querySelectorAll('.wizard-panel').forEach((panel, index) => {
        panel.classList.remove('active');
        if (index + 1 === currentStep) {
            panel.classList.add('active');
        }
    });

    // 更新按钮状态
    document.getElementById('btn-prev').style.display = currentStep > 1 ? 'block' : 'none';
    document.getElementById('btn-next').style.display = currentStep < 4 ? 'block' : 'none';
}

// 下一步
async function nextStep() {
    // 验证当前步骤
    if (currentStep === 1) {
        if (!await validateStep1()) return;
    } else if (currentStep === 2) {
        if (!validateStep2()) return;
    } else if (currentStep === 3) {
        if (!validateStep3()) return;
        buildTaskSummary();
    }

    currentStep++;
    updateStepUI();
}

// 上一步
function prevStep() {
    if (currentStep > 1) {
        currentStep--;
        updateStepUI();
    }
}

// 验证步骤 1
async function validateStep1() {
    const host = document.getElementById('db-host').value;
    const port = document.getElementById('db-port').value;
    const username = document.getElementById('db-username').value;
    const password = document.getElementById('db-password').value;
    const sid = document.getElementById('db-sid').value;

    if (!host || !port || !username || !password || !sid) {
        showAlert('step1-alert', 'error', '请填写所有必填字段');
        return false;
    }

    dbConfig = { host, port, username, password, sid };
    
    // 加载 Schema 列表
    await loadSchemas();
    
    return true;
}

// 验证步骤 2
function validateStep2() {
    if (selectedTables.length === 0) {
        showAlert('step2-alert', 'error', '请至少选择一个表');
        return false;
    }
    return true;
}

// 验证步骤 3
function validateStep3() {
    const taskName = document.getElementById('task-name').value;
    if (!taskName) {
        alert('请输入任务名称');
        return false;
    }
    return true;
}

// 测试连接
async function testConnection() {
    const host = document.getElementById('db-host').value;
    const port = document.getElementById('db-port').value;
    const username = document.getElementById('db-username').value;
    const password = document.getElementById('db-password').value;
    const sid = document.getElementById('db-sid').value;

    if (!host || !port || !username || !password || !sid) {
        showAlert('step1-alert', 'error', '请填写所有必填字段');
        return;
    }

    showAlert('step1-alert', 'info', '正在测试连接...');

    try {
        const response = await fetch(`${API_BASE}/cdc/datasource/test`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ host, port, username, password, sid })
        });

        const data = await response.json();

        if (data.success) {
            showAlert('step1-alert', 'success', '✓ 连接成功！可以进入下一步');
        } else {
            showAlert('step1-alert', 'error', `连接失败: ${data.error}`);
        }
    } catch (error) {
        showAlert('step1-alert', 'error', `连接失败: ${error.message}`);
    }
}

// 加载 Schema 列表
async function loadSchemas() {
    try {
        const response = await fetch(`${API_BASE}/cdc/datasource/schemas`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(dbConfig)
        });

        const data = await response.json();

        if (data.success) {
            const select = document.getElementById('db-schema');
            select.innerHTML = '<option value="">-- 选择 Schema --</option>';
            
            data.data.forEach(schema => {
                const option = document.createElement('option');
                option.value = schema;
                option.textContent = schema;
                if (schema === 'FINANCE_USER') {
                    option.selected = true;
                }
                select.appendChild(option);
            });

            // 如果有默认选中的，自动加载表
            if (select.value) {
                selectedSchema = select.value;
                await loadTables();
            }
        }
    } catch (error) {
        console.error('加载 Schema 失败:', error);
    }
}

// 加载表列表
async function loadTables() {
    const schema = document.getElementById('db-schema').value;
    if (!schema) return;

    selectedSchema = schema;
    const loading = document.getElementById('tables-loading');
    const list = document.getElementById('tables-list');

    loading.style.display = 'block';
    list.innerHTML = '';

    try {
        const response = await fetch(`${API_BASE}/cdc/datasource/tables`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ config: dbConfig, schema })
        });

        const data = await response.json();

        if (data.success) {
            loading.style.display = 'none';
            
            if (data.data.length === 0) {
                list.innerHTML = '<div style="text-align: center; color: #999; padding: 40px;">该 Schema 中没有表</div>';
                return;
            }

            data.data.forEach(table => {
                const item = document.createElement('div');
                item.className = 'table-item';
                item.innerHTML = `
                    <div class="table-info">
                        <div class="table-name">${table.name}</div>
                        <div class="table-meta">${table.columns} 列 · ${table.rows.toLocaleString()} 行</div>
                    </div>
                    <input type="checkbox" class="checkbox" value="${table.name}" onchange="toggleTable(this)">
                `;
                
                item.onclick = (e) => {
                    if (e.target.type !== 'checkbox') {
                        const checkbox = item.querySelector('.checkbox');
                        checkbox.checked = !checkbox.checked;
                        toggleTable(checkbox);
                    }
                };
                
                list.appendChild(item);
            });
        } else {
            loading.style.display = 'none';
            showAlert('step2-alert', 'error', `加载表失败: ${data.error}`);
        }
    } catch (error) {
        loading.style.display = 'none';
        showAlert('step2-alert', 'error', `加载表失败: ${error.message}`);
    }
}

// 切换表选择
function toggleTable(checkbox) {
    const tableName = checkbox.value;
    const item = checkbox.closest('.table-item');

    if (checkbox.checked) {
        if (!selectedTables.includes(tableName)) {
            selectedTables.push(tableName);
        }
        item.classList.add('selected');
    } else {
        selectedTables = selectedTables.filter(t => t !== tableName);
        item.classList.remove('selected');
    }

    console.log('已选择的表:', selectedTables);
}

// 构建任务摘要
function buildTaskSummary() {
    const taskName = document.getElementById('task-name').value;
    const outputPath = document.getElementById('output-path').value;
    const parallelism = document.getElementById('parallelism').value;
    const splitSize = document.getElementById('split-size').value;

    taskConfig = {
        id: Date.now().toString(),
        name: taskName,
        created: new Date().toISOString(),
        database: {
            ...dbConfig,
            schema: selectedSchema
        },
        tables: selectedTables,
        output_path: outputPath,
        parallelism: parseInt(parallelism),
        split_size: parseInt(splitSize)
    };

    const summary = document.getElementById('task-summary');
    summary.innerHTML = `
        <div class="summary-item">
            <span class="summary-label">任务名称</span>
            <span class="summary-value">${taskName}</span>
        </div>
        <div class="summary-item">
            <span class="summary-label">数据源</span>
            <span class="summary-value">${dbConfig.host}:${dbConfig.port}/${dbConfig.sid}</span>
        </div>
        <div class="summary-item">
            <span class="summary-label">Schema</span>
            <span class="summary-value">${selectedSchema}</span>
        </div>
        <div class="summary-item">
            <span class="summary-label">监控表</span>
            <span class="summary-value">${selectedTables.join(', ')}</span>
        </div>
        <div class="summary-item">
            <span class="summary-label">输出路径</span>
            <span class="summary-value">${outputPath}</span>
        </div>
        <div class="summary-item">
            <span class="summary-label">并行度</span>
            <span class="summary-value">${parallelism}</span>
        </div>
    `;
}

// 提交任务
async function submitTask() {
    showAlert('step4-alert', 'info', '正在提交任务...');

    try {
        // 1. 保存任务配置
        const saveResponse = await fetch(`${API_BASE}/cdc/tasks`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(taskConfig)
        });

        const saveData = await saveResponse.json();

        if (!saveData.success) {
            showAlert('step4-alert', 'error', `保存任务失败: ${saveData.error}`);
            return;
        }

        const taskId = saveData && saveData.data ? saveData.data.id : null;
        if (!taskId) {
            showAlert('step4-alert', 'error', '保存成功但未返回任务ID');
            return;
        }

        // 2. 提交任务到 Flink
        const submitResponse = await fetch(`${API_BASE}/cdc/tasks/${taskId}/submit`, {
            method: 'POST'
        });

        const submitData = await submitResponse.json();

        if (submitData.success) {
            showAlert('step4-alert', 'success', 
                `✓ 任务提交成功！\nJob ID: ${submitData.job_id || 'N/A'}\n\n正在跳转到监控面板...`);
            
            setTimeout(() => {
                window.location.href = 'index.html';
            }, 3000);
        } else {
            showAlert('step4-alert', 'error', `提交任务失败: ${submitData.error}`);
        }
    } catch (error) {
        showAlert('step4-alert', 'error', `提交任务失败: ${error.message}`);
    }
}

// 显示提示信息
function showAlert(elementId, type, message) {
    const element = document.getElementById(elementId);
    element.className = `alert alert-${type}`;
    element.textContent = message;
    element.style.display = 'block';
}
