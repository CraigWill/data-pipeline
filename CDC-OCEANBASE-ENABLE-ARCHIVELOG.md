# OceanBase（Oracle 模式）开启归档指南

> CDC 必需前置条件。OceanBase 的归档模式是在 **SYS 租户**下对**业务租户**开启的，
> 与 Oracle 单库的 `ALTER DATABASE ARCHIVELOG` 不同，请勿混淆。
>
> 配套脚本：[`sql/enable-oceanbase-oracle-archivelog.sql`](sql/enable-oceanbase-oracle-archivelog.sql)

---

## 1. 为什么 CDC 需要开启归档

本项目通过 `obbinlog`（OBLogProxy v4 / libobcdc）订阅 OceanBase Oracle 租户的事务日志（clog）。

- 在线 clog 会被定期回收，回收窗口之外的变更无法再读取；
- 开启归档（ARCHIVELOG）后，事务日志会持久化到归档目的地，CDC 可在中断/重启后从历史位点继续消费，避免数据丢失；
- 归档 + 补充日志（Supplemental Log）共同保证 CDC 能拿到完整的行级变更数据。

未开启归档时，CDC 作业常见报错为日志位点不可达 / 拉取历史日志失败，表现与 Oracle 的
`ORA-01325: archive log mode must be enabled` 类似。

---

## 2. 前置条件

| 项目 | 说明 |
|------|------|
| 连接身份 | 开启归档需 **SYS 租户管理员**（`root@sys`） |
| 业务租户 | 本项目默认 Oracle 租户名为 `oratenant`（按实际情况替换） |
| 客户端 | `obclient` 或 `mysql` 均可 |
| 归档存储 | 需提前准备可写的归档目录或对象存储，并预留足够空间 |

连接 SYS 租户示例：

```bash
obclient -h172.22.0.1 -P2881 -uroot@sys -p -Doceanbase
# 或
mysql -h172.22.0.1 -P2881 -uroot@sys -p oceanbase
```

> `172.22.0.1` 为本项目 docker-compose 中 `centos-ob` 的映射地址，按实际环境替换。

---

## 3. 操作步骤

### 步骤 1：检查当前归档状态

```sql
-- 查看各租户归档模式（log_mode：NOARCHIVELOG / ARCHIVELOG）
SELECT tenant_id, tenant_name, log_mode FROM oceanbase.DBA_OB_TENANTS;

-- 查看归档目的地配置
SELECT * FROM oceanbase.CDB_OB_ARCHIVE_DEST;
```

若目标租户 `log_mode` 已为 `ARCHIVELOG`，可直接跳到[步骤 5](#步骤-5开启补充日志)。

### 步骤 2：配置归档目的地

按环境三选一（在 SYS 租户执行）：

```sql
-- 方式 A：本地文件归档（开发环境）
ALTER SYSTEM SET LOG_ARCHIVE_DEST='LOCATION=file:///home/admin/oceanbase/archive' TENANT = oratenant;

-- 方式 B：NFS 归档（生产环境推荐）
-- ALTER SYSTEM SET LOG_ARCHIVE_DEST='LOCATION=file:///nfs_share/ob_archive' TENANT = oratenant;

-- 方式 C：OSS 归档（阿里云环境）
-- ALTER SYSTEM SET LOG_ARCHIVE_DEST='LOCATION=oss://bucket_name/archive?host=oss-cn-hangzhou.aliyuncs.com&access_id=xxx&access_key=yyy' TENANT = oratenant;
```

> 归档目录必须对 observer 进程可写；本地文件方式需确保该路径在所有 OB 节点上都存在。

### 步骤 3：开启归档模式

```sql
ALTER SYSTEM ARCHIVELOG TENANT = oratenant;
```

### 步骤 4：验证归档状态

```sql
-- log_mode 应为 ARCHIVELOG
SELECT tenant_id, tenant_name, log_mode
FROM oceanbase.DBA_OB_TENANTS
WHERE tenant_name = 'oratenant';

-- 归档任务 status 应为 DOING
SELECT * FROM oceanbase.CDB_OB_ARCHIVELOG
WHERE tenant_id = (
    SELECT tenant_id FROM oceanbase.DBA_OB_TENANTS WHERE tenant_name = 'oratenant'
);
```

### 步骤 5：开启补充日志

CDC 需要完整的行级变更数据，需在业务租户开启补充日志：

```bash
obclient -h172.22.0.1 -P2881 -ucdc_admin@oratenant -p -DCDC_ADMIN
```

```sql
-- 开启富错误信息（便于 CDC 解析）
ALTER SYSTEM SET enable_rich_error_msg = true;

-- 为需要监控的表开启全列补充日志
-- ALTER TABLE schema_name.table_name ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;
```

---

## 4. 归档保留与清理

归档会持续占用存储，需配置保留窗口并定期清理：

```sql
-- 设置归档保留时间（示例：切片间隔 1 天，恢复窗口 7 天）
ALTER SYSTEM SET backup_dest_option = 'log_archive_piece_switch_interval=1d,recovery_window=7d' TENANT = oratenant;

-- 查看清理策略
SELECT * FROM oceanbase.CDB_OB_BACKUP_DELETE_POLICY;

-- 手动清理过期归档（谨慎）
-- ALTER SYSTEM DELETE BACKUPPIECE ALL TENANT = oratenant;
```

> 建议保留窗口 ≥ CDC 可能的最长中断时间，否则中断超过窗口后将无法续传。

---

## 5. 常见问题

| 现象 | 排查方向 |
|------|----------|
| `log_mode` 仍为 `NOARCHIVELOG` | 确认是在 **SYS 租户**执行，且 `TENANT = ` 指向正确的业务租户 |
| 归档 `status` 非 `DOING` | 检查归档目的地路径是否存在、是否对 observer 可写、磁盘是否已满 |
| CDC 拉取历史日志失败 | 归档保留窗口可能已过期；缩短 CDC 中断时间或加大 `recovery_window` |
| 拿不到完整行数据（仅主键） | 未开启补充日志，执行[步骤 5](#步骤-5开启补充日志) |
| 归档目录磁盘占满 | 配置/收紧 `recovery_window`，并执行过期归档清理 |

---

## 6. 回滚（谨慎）

```sql
-- 关闭归档模式（会影响 CDC 与备份恢复能力）
ALTER SYSTEM NOARCHIVELOG TENANT = oratenant;
```

---

## 7. 相关文档

- 配套 SQL 脚本：[`sql/enable-oceanbase-oracle-archivelog.sql`](sql/enable-oceanbase-oracle-archivelog.sql)
- 管理库切换：[`CDC-ADMIN-DB-SWITCH.md`](CDC-ADMIN-DB-SWITCH.md)
- 数据丢失分析：[`CDC-DATA-LOSS-ANALYSIS.md`](CDC-DATA-LOSS-ANALYSIS.md)
- 恢复指南：[`CDC-RECOVERY-GUIDE.md`](CDC-RECOVERY-GUIDE.md)
