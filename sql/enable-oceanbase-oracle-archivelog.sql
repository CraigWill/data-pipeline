-- ============================================================
-- OceanBase Oracle 模式 — 开启归档模式（CDC 必需）
-- ============================================================
-- 执行方式（需以 sys 租户管理员身份连接）:
--   obclient -h172.22.0.1 -P2881 -uroot@sys -p -Doceanbase
--   或
--   mysql -h172.22.0.1 -P2881 -uroot@sys -p oceanbase
--
-- 注意: 归档模式是在 SYS 租户下对集群级别开启的，不是在业务租户下
-- ============================================================

-- ============================================================
-- 步骤 1: 检查当前归档状态
-- ============================================================
-- 查看归档模式是否已开启
SELECT tenant_id, tenant_name, log_mode FROM oceanbase.DBA_OB_TENANTS;

-- 查看归档目的地配置
SELECT * FROM oceanbase.CDB_OB_ARCHIVE_DEST;

-- ============================================================
-- 步骤 2: 配置归档目的地
-- ============================================================
-- 为 Oracle 租户（oratenant）配置归档路径
-- 可选: file://（本地文件）或 oss://（对象存储）

-- 方式 A: 本地文件归档（开发环境）
ALTER SYSTEM SET LOG_ARCHIVE_DEST='LOCATION=file:///home/admin/oceanbase/archive' TENANT = oratenant;

-- 方式 B: NFS 归档（生产环境推荐）
-- ALTER SYSTEM SET LOG_ARCHIVE_DEST='LOCATION=file:///nfs_share/ob_archive' TENANT = oratenant;

-- 方式 C: OSS 归档（阿里云环境）
-- ALTER SYSTEM SET LOG_ARCHIVE_DEST='LOCATION=oss://bucket_name/archive?host=oss-cn-hangzhou.aliyuncs.com&access_id=xxx&access_key=yyy' TENANT = oratenant;

-- ============================================================
-- 步骤 3: 开启归档模式
-- ============================================================
ALTER SYSTEM ARCHIVELOG TENANT = oratenant;

-- ============================================================
-- 步骤 4: 验证归档状态
-- ============================================================
-- 确认归档模式已开启（log_mode 应为 ARCHIVELOG）
SELECT tenant_id, tenant_name, log_mode FROM oceanbase.DBA_OB_TENANTS WHERE tenant_name = 'oratenant';zuh

-- 查看归档状态（status 应为 DOING）
SELECT * FROM oceanbase.CDB_OB_ARCHIVELOG WHERE tenant_id = (
    SELECT tenant_id FROM oceanbase.DBA_OB_TENANTS WHERE tenant_name = 'oratenant'
);

-- ============================================================
-- 步骤 5: 开启补充日志（CDC 需要行级变更数据）
-- ============================================================
-- 切换到业务租户执行
-- obclient -h172.22.0.1 -P2881 -ucdc_admin@oratenant -p -DCDC_ADMIN

-- 开启全量补充日志（记录所有列数据）
ALTER SYSTEM SET enable_rich_error_msg = true;

-- 为需要监控的表开启行级补充日志
-- ALTER TABLE schema_name.table_name ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;

-- ============================================================
-- 常用管理命令
-- ============================================================
-- 关闭归档模式（谨慎操作）
-- ALTER SYSTEM NOARCHIVELOG TENANT = oratenant;

-- 查看归档日志清理策略
-- SELECT * FROM oceanbase.CDB_OB_BACKUP_DELETE_POLICY;

-- 设置归档保留时间（7天）
-- ALTER SYSTEM SET backup_dest_option = 'log_archive_piece_switch_interval=1d,recovery_window=7d' TENANT = oratenant;

-- 手动清理过期归档
-- ALTER SYSTEM DELETE BACKUPPIECE ALL TENANT = oratenant;
