// 主应用 JavaScript

const API_BASE = '/api';

let tasks = [];
let currentTaskDetail = null;

// 检查登录状态
function checkLogin() {
    const user = localStorage.getItem('currentUser');
    if (user) {
        showApp(user);
    } else {
        showLogin();
    }
}

// 显示登录页面
function showLogin() {
    document.getElementById('loginContainer').style.display = 'flex';
    document.getElementById('appContainer').classList.remove('active');
}

// 显示主应用
function showApp(username) {
    document.getElementById('loginContainer').style.display = 'none';
    document.getElementById('appContainer').classList.add('active');
    document.getElementById('currentUser').textContent = username;
}

// 登录表单提交
document.getElementById('loginForm').addEventListener('submit', (e) => {
    e.preventDefault();
    
    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;
    
    // 简单的登录验证（实际应该调用后端 API）
    if (username === 'admin' && password === 'admin') {
        localStorage.setItem('currentUser', username);
        showApp(username);
    } else {
        alert('用户名或密码错误');
    }
});

// 退出登录
function logout() {
    if (confirm('确定要退出登录吗？')) {
        localStorage.removeItem('currentUser');
        showLogin();
    }
}

// 显示页面
function showPage(pageName) {
    // 隐藏所有页面
    document.querySelectorAll('.page-content').forEach(page => {
        page.classList.remove('active');
    });
    
    // 移除所有菜单项的 active 状态
    document.querySelectorAll('.menu-item').forEach(item => {
        item.classList.remove('active');
    });
    
    // 显示选中的页面
    const page = document.getElementById(`page-${pageName}`);
    if (page) {
        page.classList.add('active');
    }
    
    // 设置菜单项为 active
    event.currentTarget.classList.add('active');
    
    // 如果是任务管理页面，加载任务列表
    if (pageName === 'cdc-tasks') {
        loadTasks();
    }
}

// 加载任务列表
async function loadTasks() {
    const loading = document.getElementById('loading');
    const emptyState = document.getElementById('empty-state');
    const taskGrid = document.getElementById('task-grid');

    loading.style.display = 'block';
    emptyState.style.display = 'none';
    taskGrid.style.display = 'none';

    try {
        const response = await fetch(`${API_BASE}/cdc/tasks`);
        const data = await response.json();

        loading.style.display = 'none';

        if (data.success) {
            tasks = data.data;

            if (tasks.length === 0) {
                emptyState.style.display = 'block';
            } else {
                taskGrid.style.display = 'grid';
                renderTasks();
            }
        } else {
            showAlert('error', `加载任务失败: ${data.error}`);
        }
    } catch (error) {
        loading.style.display = 'none';
        showAlert('error', `加载任务失败: ${error.message}`);
    }
}

// 渲染任务列表
function renderTasks() {
    const taskGrid = document.getElementById('task-grid');
    taskGrid.innerHTML = '';

    tasks.forEach(task => {
        const card = document.createElement('div');
        card.className = 'task-card';
        card.onclick = () => viewTaskDetail(task.id);

        const createdDate = task.created !== 'Unknown' 
            ? new Date(task.created).toLocaleString('zh-CN')
            : 'Unknown';

        card.innerHTML = `
            <div class="task-header">
                <div>
                    <div class="task-title">${escapeHtml(task.name)}</div>
                    <div class="task-id">ID: ${task.id}</div>
                </div>
                <div class="task-actions" onclick="event.stopPropagation()">
                    <button class="btn btn-sm btn-success" onclick="submitTask('${task.id}')">提交</button>
                    <button class="btn btn-sm btn-danger" onclick="deleteTask('${task.id}')">删除</button>
                </div>
            </div>
            <div class="task-info">
                <div class="info-item">
                    <div class="info-label">数据源</div>
                    <div class="info-value">${escapeHtml(task.database)}</div>
                </div>
                <div class="info-item">
                    <div class="info-label">监控表数量</div>
                    <div class="info-value">${task.tables} 个表</div>
                </div>
                <div class="info-item">
                    <div class="info-label">创建时间</div>
                    <div class="info-value">${createdDate}</div>
                </div>
            </div>
        `;

        taskGrid.appendChild(card);
    });
}

// 查看任务详情
async function viewTaskDetail(taskId) {
    try {
        const response = await fetch(`${API_BASE}/cdc/tasks/${taskId}/detail`);
        const data = await response.json();

        if (data.success) {
            currentTaskDetail = data.data;
            showDetailModal();
        } else {
            showAlert('error', `加载任务详情失败: ${data.error}`);
        }
    } catch (error) {
        showAlert('error', `加载任务详情失败: ${error.message}`);
    }
}

// 显示详情模态框
function showDetailModal() {
    const modal = document.getElementById('detail-modal');
    const content = document.getElementById('detail-content');

    const detail = currentTaskDetail;
    const createdDate = detail.created !== 'Unknown' 
        ? new Date(detail.created).toLocaleString('zh-CN')
        : 'Unknown';

    content.innerHTML = `
        <div class="detail-section">
            <div class="detail-section-title">基本信息</div>
            <div class="detail-grid">
                <div class="detail-item">
                    <div class="detail-label">任务 ID</div>
                    <div class="detail-value">${detail.id}</div>
                </div>
                <div class="detail-item">
                    <div class="detail-label">任务名称</div>
                    <div class="detail-value">${escapeHtml(detail.name)}</div>
                </div>
                <div class="detail-item">
                    <div class="detail-label">创建时间</div>
                    <div class="detail-value">${createdDate}</div>
                </div>
                <div class="detail-item">
                    <div class="detail-label">输出路径</div>
                    <div class="detail-value">${detail.output_path}</div>
                </div>
            </div>
        </div>

        <div class="detail-section">
            <div class="detail-section-title">数据库配置</div>
            <div class="detail-grid">
                <div class="detail-item">
                    <div class="detail-label">主机地址</div>
                    <div class="detail-value">${detail.database.host}</div>
                </div>
                <div class="detail-item">
                    <div class="detail-label">端口</div>
                    <div class="detail-value">${detail.database.port}</div>
                </div>
                <div class="detail-item">
                    <div class="detail-label">SID</div>
                    <div class="detail-value">${detail.database.sid}</div>
                </div>
                <div class="detail-item">
                    <div class="detail-label">用户名</div>
                    <div class="detail-value">${detail.database.username}</div>
                </div>
                <div class="detail-item">
                    <div class="detail-label">Schema</div>
                    <div class="detail-value">${detail.database.schema}</div>
                </div>
                ${detail.datasource_name ? `
                <div class="detail-item">
                    <div class="detail-label">数据源名称</div>
                    <div class="detail-value">${escapeHtml(detail.datasource_name)}</div>
                </div>
                ` : ''}
            </div>
        </div>

        <div class="detail-section">
            <div class="detail-section-title">监控表 (${detail.tables.length})</div>
            <div class="table-list">
                ${detail.tables.map(table => `<span class="table-tag">${escapeHtml(table)}</span>`).join('')}
            </div>
        </div>

        <div class="detail-section">
            <div class="detail-section-title">任务参数</div>
            <div class="detail-grid">
                <div class="detail-item">
                    <div class="detail-label">并行度</div>
                    <div class="detail-value">${detail.parallelism}</div>
                </div>
                <div class="detail-item">
                    <div class="detail-label">Split 大小</div>
                    <div class="detail-value">${detail.split_size}</div>
                </div>
            </div>
        </div>

        <div style="margin-top: 24px; display: flex; gap: 12px; justify-content: flex-end;">
            <button class="btn btn-secondary" onclick="closeDetailModal()">关闭</button>
            <button class="btn btn-success" onclick="submitTaskFromDetail()">提交任务</button>
        </div>
    `;

    modal.classList.add('active');
}

// 关闭详情模态框
function closeDetailModal() {
    const modal = document.getElementById('detail-modal');
    modal.classList.remove('active');
    currentTaskDetail = null;
}

// 从详情模态框提交任务
async function submitTaskFromDetail() {
    if (currentTaskDetail) {
        await submitTask(currentTaskDetail.id);
        closeDetailModal();
    }
}

// 提交任务
async function submitTask(taskId) {
    if (!confirm('确定要提交这个任务到 Flink 吗？')) {
        return;
    }

    showAlert('info', '正在提交任务...');

    try {
        const response = await fetch(`${API_BASE}/cdc/tasks/${taskId}/submit`, {
            method: 'POST'
        });

        const data = await response.json();

        if (data.success) {
            showAlert('success', `任务提交成功！Job ID: ${data.job_id || 'N/A'}`);
            setTimeout(() => {
                showPage('dashboard');
            }, 2000);
        } else {
            showAlert('error', `任务提交失败: ${data.error}`);
        }
    } catch (error) {
        showAlert('error', `任务提交失败: ${error.message}`);
    }
}

// 删除任务
async function deleteTask(taskId) {
    if (!confirm('确定要删除这个任务吗？此操作不可恢复。')) {
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/cdc/tasks/${taskId}`, {
            method: 'DELETE'
        });

        const data = await response.json();

        if (data.success) {
            showAlert('success', '任务已删除');
            // 重新加载任务列表
            await loadTasks();
        } else {
            showAlert('error', `删除任务失败: ${data.error}`);
        }
    } catch (error) {
        showAlert('error', `删除任务失败: ${error.message}`);
    }
}

// 显示提示信息
function showAlert(type, message) {
    const container = document.getElementById('alert-container');
    const alert = document.createElement('div');
    alert.className = `alert alert-${type}`;
    alert.textContent = message;
    container.innerHTML = '';
    container.appendChild(alert);

    // 3秒后自动消失
    setTimeout(() => {
        alert.remove();
    }, 3000);
}

// HTML 转义
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// 页面加载时检查登录状态
document.addEventListener('DOMContentLoaded', () => {
    checkLogin();
    
    // 默认激活第一个菜单项
    const firstMenuItem = document.querySelector('.menu-item');
    if (firstMenuItem) {
        firstMenuItem.classList.add('active');
    }
});

// 定期检查登录状态（防止用户清除 localStorage）
setInterval(() => {
    const user = localStorage.getItem('currentUser');
    if (!user && document.getElementById('appContainer').classList.contains('active')) {
        showLogin();
    }
}, 5000);
