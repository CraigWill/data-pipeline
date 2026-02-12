#!/bin/bash
# 检查 Oracle 数据库 CDC 配置状态

set -e

# 读取环境变量
source .env 2>/dev/null || true

DB_HOST="${DATABASE_HOST:-localhost}"
DB_PORT="${DATABASE_PORT:-1521}"
DB_SID="${DATABASE_SID:-helowin}"
DB_USER="${DATABASE_USERNAME:-system}"
DB_PASSWORD="${DATABASE_PASSWORD:-password}"

echo "=========================================="
echo "Oracle CDC 配置状态检查"
echo "=========================================="
echo "数据库: ${DB_HOST}:${DB_PORT}/${DB_SID}"
echo "用户: ${DB_USER}"
echo ""

# 创建临时 SQL 文件
SQL_FILE=$(mktemp)
cat > "$SQL_FILE" << 'EOF'
SET PAGESIZE 1000
SET LINESIZE 200
SET FEEDBACK OFF
SET HEADING ON

PROMPT ========================================
PROMPT 1. 归档日志模式
PROMPT ========================================
SELECT LOG_MODE FROM V$DATABASE;

PROMPT
PROMPT ========================================
PROMPT 2. 补充日志状态
PROMPT ========================================
SELECT SUPPLEMENTAL_LOG_DATA_MIN, SUPPLEMENTAL_LOG_DATA_ALL FROM V$DATABASE;

PROMPT
PROMPT ========================================
PROMPT 3. 最近的归档日志
PROMPT ========================================
SELECT NAME, SEQUENCE#, TO_CHAR(FIRST_TIME, 'YYYY-MM-DD HH24:MI:SS') AS FIRST_TIME
FROM V$ARCHIVED_LOG 
WHERE ROWNUM <= 5
ORDER BY FIRST_TIME DESC;

PROMPT
PROMPT ========================================
PROMPT 4. 当前 Redo Log 状态
PROMPT ========================================
SELECT GROUP#, THREAD#, SEQUENCE#, BYTES/1024/1024 AS SIZE_MB, MEMBERS, STATUS 
FROM V$LOG;

PROMPT
PROMPT ========================================
PROMPT 5. 用户权限检查
PROMPT ========================================
SELECT PRIVILEGE FROM DBA_SYS_PRIVS WHERE GRANTEE = UPPER('${DB_USER}');

EXIT;
EOF

# 执行 SQL
echo "正在连接数据库..."
sqlplus -S "${DB_USER}/${DB_PASSWORD}@${DB_HOST}:${DB_PORT}/${DB_SID}" @"$SQL_FILE" 2>&1

# 清理临时文件
rm -f "$SQL_FILE"

echo ""
echo "=========================================="
echo "配置建议"
echo "=========================================="
echo "如果归档日志模式为 NOARCHIVELOG，请执行："
echo "  sqlplus / as sysdba @setup-oracle-cdc.sql"
echo ""
echo "如果补充日志未启用，请执行："
echo "  ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;"
echo ""
echo "如果用户权限不足，请授予："
echo "  GRANT SELECT ANY TABLE TO ${DB_USER};"
echo "  GRANT EXECUTE_CATALOG_ROLE TO ${DB_USER};"
echo "  GRANT SELECT ANY TRANSACTION TO ${DB_USER};"
echo "  GRANT LOGMINING TO ${DB_USER};"
echo "=========================================="
