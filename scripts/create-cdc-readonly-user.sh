#!/bin/bash
# ============================================================
# 创建 OceanBase CDC 只读用户脚本
#
# 用途：为 oblogproxy/obbinlog CDC 创建一个 sys 租户的只读账号，
#       授予 libobcdc 读取集群元数据所需的最小权限。
#       避免直接使用 root，符合生产环境最小权限原则。
#
# 前提：需要 DBA 提供 sys 租户的 root（或具备授权能力的）账号执行本脚本。
#
# 用法：
#   ./create-cdc-readonly-user.sh \
#       --host <observer_ip> --port 2881 \
#       --admin-user root --admin-pass <sys_root_password> \
#       --cdc-user cdc_reader --cdc-pass <new_password>
# ============================================================
set -e

# ── 默认参数 ────────────────────────────────────────────────
HOST="127.0.0.1"
PORT="2881"
ADMIN_USER="root"            # sys 租户管理员（执行授权）
ADMIN_PASS=""
CDC_USER="cdc_reader"        # 待创建的 CDC 只读用户
CDC_PASS=""
SYS_TENANT="sys"

# ── 解析参数 ────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        --host)        HOST="$2";       shift 2 ;;
        --port)        PORT="$2";       shift 2 ;;
        --admin-user)  ADMIN_USER="$2"; shift 2 ;;
        --admin-pass)  ADMIN_PASS="$2"; shift 2 ;;
        --cdc-user)    CDC_USER="$2";   shift 2 ;;
        --cdc-pass)    CDC_PASS="$2";   shift 2 ;;
        --sys-tenant)  SYS_TENANT="$2"; shift 2 ;;
        -h|--help)
            echo "用法: $0 --host <ip> --port 2881 --admin-user root --admin-pass <pwd> --cdc-user cdc_reader --cdc-pass <pwd>"
            exit 0 ;;
        *) echo "未知参数: $1"; exit 1 ;;
    esac
done

# ── 校验必填 ────────────────────────────────────────────────
if [ -z "${ADMIN_PASS}" ]; then
    echo "ERROR: 必须提供 --admin-pass（sys 租户 ${ADMIN_USER} 密码）"
    exit 1
fi
if [ -z "${CDC_PASS}" ]; then
    echo "ERROR: 必须提供 --cdc-pass（新建 CDC 用户密码）"
    exit 1
fi

# ── 选择 mysql 客户端 ───────────────────────────────────────
MYSQL_BIN="$(command -v obclient || command -v mysql)"
if [ -z "${MYSQL_BIN}" ]; then
    echo "ERROR: 未找到 obclient 或 mysql 客户端"
    exit 1
fi
echo "[INFO] 使用客户端: ${MYSQL_BIN}"

# sys 租户连接串：user@sys
ADMIN_CONN_USER="${ADMIN_USER}@${SYS_TENANT}"

echo "[INFO] 连接 OceanBase sys 租户: ${HOST}:${PORT} 用户=${ADMIN_CONN_USER}"
echo "[INFO] 创建 CDC 只读用户: ${CDC_USER}"

# ── 执行 SQL ────────────────────────────────────────────────
# OceanBase sys 租户是 MySQL 兼容模式
"${MYSQL_BIN}" -h"${HOST}" -P"${PORT}" -u"${ADMIN_CONN_USER}" -p"${ADMIN_PASS}" -A <<SQL
-- 1. 创建用户（若已存在则忽略错误）
CREATE USER IF NOT EXISTS '${CDC_USER}' IDENTIFIED BY '${CDC_PASS}';

-- 2. 最小权限授权：仅授予 libobcdc (OB 4.2.5) 实际访问的 sys 租户元数据对象，
--    不使用 oceanbase.* 通配，避免扩大只读范围。
--    业务数据由 clog 流拉取，CDC 账号无需任何业务库/表权限。
--    对象集合通过「逐个授权 + 观察 libobcdc.log 权限报错」迭代实测得出（真实订阅捕获验证）：
--    [集群拓扑 / 心跳]
GRANT SELECT ON oceanbase.__all_server                  TO '${CDC_USER}';
GRANT SELECT ON oceanbase.__all_zone                    TO '${CDC_USER}';
GRANT SELECT ON oceanbase.__all_sys_stat                TO '${CDC_USER}';
--    [租户 / 资源单元（视图，定义者权限，无需授其底层基表）]
GRANT SELECT ON oceanbase.DBA_OB_TENANTS                 TO '${CDC_USER}';
GRANT SELECT ON oceanbase.DBA_OB_RESTORE_HISTORY         TO '${CDC_USER}';
GRANT SELECT ON oceanbase.DBA_OB_UNITS                   TO '${CDC_USER}';
GRANT SELECT ON oceanbase.DBA_OB_SERVERS                 TO '${CDC_USER}';
--    [版本 / 集群参数检测]
GRANT SELECT ON oceanbase.__all_virtual_sys_parameter_stat TO '${CDC_USER}';  -- MIN_OBSERVER_VERSION
GRANT SELECT ON oceanbase.GV\$OB_PARAMETERS              TO '${CDC_USER}';      -- cluster_id
--    [日志流定位 + 日志内数据字典（OB 4.x 取 schema 用）]
GRANT SELECT ON oceanbase.GV\$OB_LOG_STAT               TO '${CDC_USER}';
GRANT SELECT ON oceanbase.__all_data_dictionary_in_log  TO '${CDC_USER}';

-- 注意：不再授予 information_schema.* 与 PROCESS ON *.*（实测 OB 4.2.x integrated 模式不需要）。
--      如升级 OB 大版本后 oblogproxy 日志出现权限错误，按报错补充对应单个对象即可。

-- 允许从任意主机连接（生产可按需收紧到指定网段）
-- OceanBase 通过 OB_TCP_INVITED_NODES 控制，用户级默认放行

FLUSH PRIVILEGES;

-- 验证
SELECT user FROM mysql.user WHERE user='${CDC_USER}';
SQL

echo ""
echo "[SUCCESS] CDC 只读用户创建完成"
echo ""
echo "  oblogproxy 配置使用："
echo "    OB_SYS_USERNAME=${CDC_USER}"
echo "    OB_SYS_PASSWORD=<你设置的密码>"
echo ""
echo "  连接验证："
echo "    ${MYSQL_BIN} -h${HOST} -P${PORT} -u'${CDC_USER}@${SYS_TENANT}' -p<密码> -e \"SELECT tenant_id,tenant_name FROM oceanbase.__all_tenant\""
