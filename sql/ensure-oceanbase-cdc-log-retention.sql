-- ============================================================
-- OceanBase CDC：保证 clog/归档保留窗口，故障恢复不丢数
-- ============================================================
-- 背景：
--   Flink CDC 从 checkpoint/savepoint 恢复时携带旧位点。若 OceanBase 本地 clog
--   已被回收，且归档日志也未覆盖该位点，则会出现：
--     - resolvedTimestamp 长时间不前进（作业仍 RUNNING）
--     - 或 OB_ERR_OUT_OF_LOWER_BOUND 反复 RESTARTING
--   此时若用「当前时间」重提作业会丢失中间变更。
--
-- 正确做法：开启 ARCHIVELOG，并把归档保留窗口设为大于「最大可接受停机时间」
-- （建议 ≥ 7 天，至少大于 HA 故障演练/扩缩容窗口）。
--
-- 执行（SYS 租户）:
--   obclient -h<ob-host> -P2881 -uroot@sys -p -Doceanbase < ensure-oceanbase-cdc-log-retention.sql
-- 将 oratenant 换成实际业务租户名。
-- ============================================================

-- 1) 当前租户归档模式
SELECT tenant_id, tenant_name, log_mode
FROM oceanbase.DBA_OB_TENANTS
WHERE tenant_name = 'oratenant';

-- 2) 归档目的地（若为空请先配置）
-- ALTER SYSTEM SET LOG_ARCHIVE_DEST = 'LOCATION=file:///home/admin/oceanbase/archive' TENANT = oratenant;
SELECT * FROM oceanbase.CDB_OB_ARCHIVE_DEST;

-- 3) 开启归档（已是 ARCHIVELOG 可跳过）
-- ALTER SYSTEM ARCHIVELOG TENANT = oratenant;

-- 4) 归档保留窗口 7 天（根本措施：覆盖 HA/长时间中断后的位点回放）
-- OceanBase 版本差异较大，任选其一可用的语句执行：
--
-- 方式 A（推荐，备份/归档清理策略）:
-- ALTER SYSTEM SET backup_dest_option = 'log_archive_piece_switch_interval=1d,recovery_window=7d' TENANT = oratenant;
--
-- 方式 B（部分版本参数名）:
-- ALTER SYSTEM SET log_archive_retention = '7d' TENANT = oratenant;

-- 5) 验证归档在跑
SELECT tenant_id, status, checkpoint_scn, path
FROM oceanbase.CDB_OB_ARCHIVELOG
WHERE tenant_id = (
  SELECT tenant_id FROM oceanbase.DBA_OB_TENANTS WHERE tenant_name = 'oratenant'
);

-- 6) 运维原则（配合 monitor-backend）
-- - 默认 CDC_ALLOW_OFFSET_SKIP=false：位点过期时不跳到 latest（避免丢数）
-- - Source write-records 长时间不增会打 ERROR 告警，应先修归档再从 savepoint 恢复
-- - 仅在接受丢数时才设 CDC_ALLOW_OFFSET_SKIP=true
