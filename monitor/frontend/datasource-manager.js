// 数据源管理 JavaScript

const API_BASE = '/api';

// 页面加载时获取数据源列表
document.addEventListener('DOMContentLoaded', () => {
    loadDataSources();
});

// 加载数据源列表
async function loadDataSources() {
    try {
        const response = await fetch(`${API_BASE}/datasources`);
        const result = await response.json();

        if (result.success) {
            renderDataSources(result.data);
        } else {
            showAlert('加载数据源失败: ' + result.error, 'error');
        }
    } catch (error) {
        showAlert('加载数据源失败: ' + error.message, 'error');
    }
}

// 渲染数据源列表
function renderDataSources(datasources) {
    const listEl = document.getElementById('datasourceList');

    if (datasources.length === 0) {
        listEl.innerHTML = `
            <div class="empty-state">
                <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4m0 5c0 2.21-3.582 4-8 4s-8-1.79-8-4"></path>
                </svg>
                <p>暂无数据源配置</p>
                <p style="font-size: 12px; margin-top: 8px;">点击"新建数据源"开始配置</p>
            </div>
        `;
        return;
    }

    listEl.innerHTML = datasources.map(ds => `
        <div class="datasource-item">
            <div class="datasource-info">
                <h3>${escapeHtml(ds.name)}</h3>
                <p>连接: ${escapeHtml(ds.host)}:${escapeHtml(ds.port)}/${escapeHtml(ds.sid)}</p>
                <p style="font-size: 12px; color: #9ca3af;">
                    创建: ${formatDate(ds.created_at)} | 更新: ${formatDate(ds.updated_at)}
                </p>
            </div>
            <div class="datasource-actions">
                <button class="btn btn-success" onclick="testDataSource('${ds.id}')">测试连接</button>
                <button class="btn btn-secondary" onclick="editDataSource('${ds.id}')">编辑</button>
                <button class="btn btn-danger" onclick="deleteDataSource('${ds.id}', '${escapeHtml(ds.name)}')">删除</button>
            </div>
        </div>
    `).join('');
}

// 显示创建模态框
function showCreateModal() {
    document.getElementById('modalTitle').textContent = '新建数据源';
    document.getElementById('datasourceForm').reset();
    document.getElementById('datasourceId').value = '';
    document.getElementById('datasourceModal').classList.add('active');
}

// 显示编辑模态框
async function editDataSource(id) {
    try {
        const response = await fetch(`${API_BASE}/datasources/${id}`);
        const result = await response.json();

        if (result.success) {
            const ds = result.data;
            document.getElementById('modalTitle').textContent = '编辑数据源';
            document.getElementById('datasourceId').value = ds.id;
            document.getElementById('datasourceName').value = ds.name;
            document.getElementById('datasourceHost').value = ds.host;
            document.getElementById('datasourcePort').value = ds.port;
            document.getElementById('datasourceUsername').value = ds.username;
            document.getElementById('datasourcePassword').value = ds.password;
            document.getElementById('datasourceSid').value = ds.sid;
            document.getElementById('datasourceModal').classList.add('active');
        } else {
            showAlert('加载数据源失败: ' + result.error, 'error');
        }
    } catch (error) {
        showAlert('加载数据源失败: ' + error.message, 'error');
    }
}

// 关闭模态框
function closeModal() {
    document.getElementById('datasourceModal').classList.remove('active');
}

// 测试连接
async function testConnection() {
    const config = {
        host: document.getElementById('datasourceHost').value,
        port: document.getElementById('datasourcePort').value,
        username: document.getElementById('datasourceUsername').value,
        password: document.getElementById('datasourcePassword').value,
        sid: document.getElementById('datasourceSid').value
    };

    if (!config.host || !config.port || !config.username || !config.password || !config.sid) {
        showAlert('请填写所有必填字段', 'error');
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/cdc/datasource/test`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(config)
        });

        const result = await response.json();

        if (result.success) {
            showAlert('连接成功！', 'success');
        } else {
            showAlert('连接失败: ' + result.error, 'error');
        }
    } catch (error) {
        showAlert('连接失败: ' + error.message, 'error');
    }
}

// 保存数据源
document.getElementById('datasourceForm').addEventListener('submit', async (e) => {
    e.preventDefault();

    const id = document.getElementById('datasourceId').value;
    const config = {
        id: id || `ds-${Date.now()}`,
        name: document.getElementById('datasourceName').value,
        host: document.getElementById('datasourceHost').value,
        port: document.getElementById('datasourcePort').value,
        username: document.getElementById('datasourceUsername').value,
        password: document.getElementById('datasourcePassword').value,
        sid: document.getElementById('datasourceSid').value
    };

    try {
        const url = id ? `${API_BASE}/datasources/${id}` : `${API_BASE}/datasources`;
        const method = id ? 'PUT' : 'POST';

        const response = await fetch(url, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(config)
        });

        const result = await response.json();

        if (result.success) {
            showAlert(id ? '数据源已更新' : '数据源已创建', 'success');
            closeModal();
            loadDataSources();
        } else {
            showAlert('保存失败: ' + result.error, 'error');
        }
    } catch (error) {
        showAlert('保存失败: ' + error.message, 'error');
    }
});

// 测试数据源连接（通过 ID）
async function testDataSource(id) {
    try {
        const response = await fetch(`${API_BASE}/datasources/${id}/test`, {
            method: 'POST'
        });

        const result = await response.json();

        if (result.success) {
            showAlert('连接成功！', 'success');
        } else {
            showAlert('连接失败: ' + result.error, 'error');
        }
    } catch (error) {
        showAlert('连接失败: ' + error.message, 'error');
    }
}

// 删除数据源
async function deleteDataSource(id, name) {
    if (!confirm(`确定要删除数据源"${name}"吗？`)) {
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/datasources/${id}`, {
            method: 'DELETE'
        });

        const result = await response.json();

        if (result.success) {
            showAlert('数据源已删除', 'success');
            loadDataSources();
        } else {
            showAlert('删除失败: ' + result.error, 'error');
        }
    } catch (error) {
        showAlert('删除失败: ' + error.message, 'error');
    }
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

// 格式化日期
function formatDate(dateStr) {
    if (!dateStr || dateStr === 'Unknown') return '未知';
    try {
        const date = new Date(dateStr);
        return date.toLocaleString('zh-CN');
    } catch {
        return dateStr;
    }
}
