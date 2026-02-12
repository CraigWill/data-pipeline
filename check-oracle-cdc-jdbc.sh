#!/bin/bash
# 使用 JDBC 检查 Oracle 数据库 CDC 配置状态（不需要 sqlplus）

set -e

# 读取环境变量
source .env 2>/dev/null || true

DB_HOST="${DATABASE_HOST:-localhost}"
DB_PORT="${DATABASE_PORT:-1521}"
DB_SID="${DATABASE_SID:-helowin}"
DB_USER="${DATABASE_USERNAME:-system}"
DB_PASSWORD="${DATABASE_PASSWORD:-password}"

echo "=========================================="
echo "Oracle CDC 配置状态检查（JDBC 方式）"
echo "=========================================="
echo "数据库: ${DB_HOST}:${DB_PORT}/${DB_SID}"
echo "用户: ${DB_USER}"
echo ""

# 创建临时 Java 程序
JAVA_FILE=$(mktemp).java
CLASS_NAME="OracleCDCChecker"

cat > "$JAVA_FILE" << 'EOFJ'
import java.sql.*;

public class OracleCDCChecker {
    public static void main(String[] args) {
        if (args.length < 5) {
            System.err.println("Usage: java OracleCDCChecker <host> <port> <sid> <user> <password>");
            System.exit(1);
        }
        
        String host = args[0];
        String port = args[1];
        String sid = args[2];
        String user = args[3];
        String password = args[4];
        
        String jdbcUrl = String.format("jdbc:oracle:thin:@%s:%s:%s", host, port, sid);
        
        try {
            Class.forName("oracle.jdbc.OracleDriver");
            Connection conn = DriverManager.getConnection(jdbcUrl, user, password);
            
            System.out.println("✅ 数据库连接成功");
            System.out.println();
            
            // 1. 检查归档日志模式
            System.out.println("==========================================");
            System.out.println("1. 归档日志模式");
            System.out.println("==========================================");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT LOG_MODE FROM V$DATABASE")) {
                if (rs.next()) {
                    String logMode = rs.getString("LOG_MODE");
                    System.out.println("LOG_MODE: " + logMode);
                    if ("ARCHIVELOG".equals(logMode)) {
                        System.out.println("✅ 归档日志已启用");
                    } else {
                        System.out.println("❌ 归档日志未启用（需要启用才能使用 LogMiner CDC）");
                    }
                }
            } catch (SQLException e) {
                System.out.println("❌ 无法查询归档日志状态: " + e.getMessage());
            }
            System.out.println();
            
            // 2. 检查补充日志
            System.out.println("==========================================");
            System.out.println("2. 补充日志状态");
            System.out.println("==========================================");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT SUPPLEMENTAL_LOG_DATA_MIN, SUPPLEMENTAL_LOG_DATA_ALL FROM V$DATABASE")) {
                if (rs.next()) {
                    String minLog = rs.getString("SUPPLEMENTAL_LOG_DATA_MIN");
                    String allLog = rs.getString("SUPPLEMENTAL_LOG_DATA_ALL");
                    System.out.println("SUPPLEMENTAL_LOG_DATA_MIN: " + minLog);
                    System.out.println("SUPPLEMENTAL_LOG_DATA_ALL: " + allLog);
                    
                    if ("YES".equals(minLog) || "IMPLICIT".equals(minLog)) {
                        System.out.println("✅ 最小补充日志已启用");
                    } else {
                        System.out.println("❌ 最小补充日志未启用");
                    }
                    
                    if ("YES".equals(allLog)) {
                        System.out.println("✅ 全列补充日志已启用");
                    } else {
                        System.out.println("⚠️  全列补充日志未启用（推荐启用）");
                    }
                }
            } catch (SQLException e) {
                System.out.println("❌ 无法查询补充日志状态: " + e.getMessage());
            }
            System.out.println();
            
            // 3. 检查归档日志文件
            System.out.println("==========================================");
            System.out.println("3. 最近的归档日志");
            System.out.println("==========================================");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT NAME, SEQUENCE#, TO_CHAR(FIRST_TIME, 'YYYY-MM-DD HH24:MI:SS') AS FIRST_TIME " +
                     "FROM V$ARCHIVED_LOG WHERE ROWNUM <= 5 ORDER BY FIRST_TIME DESC")) {
                boolean hasLogs = false;
                while (rs.next()) {
                    hasLogs = true;
                    System.out.printf("  Seq: %d, Time: %s%n", 
                        rs.getInt("SEQUENCE#"), 
                        rs.getString("FIRST_TIME"));
                }
                if (!hasLogs) {
                    System.out.println("  没有归档日志记录");
                }
            } catch (SQLException e) {
                System.out.println("❌ 无法查询归档日志: " + e.getMessage());
            }
            System.out.println();
            
            // 4. 检查 Redo Log 状态
            System.out.println("==========================================");
            System.out.println("4. 当前 Redo Log 状态");
            System.out.println("==========================================");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT GROUP#, THREAD#, SEQUENCE#, BYTES/1024/1024 AS SIZE_MB, MEMBERS, STATUS " +
                     "FROM V$LOG")) {
                while (rs.next()) {
                    System.out.printf("  Group %d: Seq=%d, Size=%.0fMB, Members=%d, Status=%s%n",
                        rs.getInt("GROUP#"),
                        rs.getInt("SEQUENCE#"),
                        rs.getDouble("SIZE_MB"),
                        rs.getInt("MEMBERS"),
                        rs.getString("STATUS"));
                }
            } catch (SQLException e) {
                System.out.println("❌ 无法查询 Redo Log 状态: " + e.getMessage());
            }
            System.out.println();
            
            // 5. 检查用户权限
            System.out.println("==========================================");
            System.out.println("5. 用户权限检查");
            System.out.println("==========================================");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT PRIVILEGE FROM DBA_SYS_PRIVS WHERE GRANTEE = UPPER('" + user + "')")) {
                System.out.println("当前用户 " + user + " 的系统权限:");
                boolean hasPrivs = false;
                while (rs.next()) {
                    hasPrivs = true;
                    System.out.println("  - " + rs.getString("PRIVILEGE"));
                }
                if (!hasPrivs) {
                    System.out.println("  没有系统权限或无法查询");
                }
            } catch (SQLException e) {
                System.out.println("⚠️  无法查询用户权限: " + e.getMessage());
                System.out.println("  （可能是权限不足，这是正常的）");
            }
            System.out.println();
            
            // 6. 检查目标表
            System.out.println("==========================================");
            System.out.println("6. 目标表检查");
            System.out.println("==========================================");
            String schema = System.getenv("DATABASE_SCHEMA");
            String table = System.getenv("DATABASE_TABLES");
            if (schema != null && table != null) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(
                         "SELECT COUNT(*) AS ROW_COUNT FROM " + schema + "." + table)) {
                    if (rs.next()) {
                        System.out.println("表 " + schema + "." + table + " 当前行数: " + rs.getInt("ROW_COUNT"));
                        System.out.println("✅ 表可访问");
                    }
                } catch (SQLException e) {
                    System.out.println("❌ 无法访问表 " + schema + "." + table + ": " + e.getMessage());
                }
            }
            
            conn.close();
            
        } catch (ClassNotFoundException e) {
            System.err.println("❌ Oracle JDBC 驱动未找到");
            System.err.println("请确保 ojdbc8.jar 在 classpath 中");
            System.exit(1);
        } catch (SQLException e) {
            System.err.println("❌ 数据库连接失败: " + e.getMessage());
            System.exit(1);
        }
    }
}
EOFJ

# 编译 Java 程序
OJDBC_JAR="$HOME/.m2/repository/com/oracle/database/jdbc/ojdbc8/21.1.0.0/ojdbc8-21.1.0.0.jar"
if [ ! -f "$OJDBC_JAR" ]; then
    echo "正在下载 Oracle JDBC 驱动..."
    mvn dependency:get -Dartifact=com.oracle.database.jdbc:ojdbc8:21.1.0.0 >/dev/null 2>&1 || true
fi

if [ ! -f "$OJDBC_JAR" ]; then
    echo "❌ 无法找到 Oracle JDBC 驱动"
    echo "请运行: mvn dependency:resolve"
    rm -f "$JAVA_FILE"
    exit 1
fi

echo "正在编译检查程序..."
javac -cp "$OJDBC_JAR" "$JAVA_FILE" 2>/dev/null

# 运行 Java 程序
JAVA_CLASS="${JAVA_FILE%.java}.class"
java -cp ".:$OJDBC_JAR:$(dirname $JAVA_FILE)" "$CLASS_NAME" \
    "$DB_HOST" "$DB_PORT" "$DB_SID" "$DB_USER" "$DB_PASSWORD"

# 清理临时文件
rm -f "$JAVA_FILE" "$JAVA_CLASS"

echo ""
echo "=========================================="
echo "配置建议"
echo "=========================================="
echo "如果归档日志模式为 NOARCHIVELOG，请执行："
echo "  1. 连接到数据库容器或服务器"
echo "  2. 以 SYSDBA 身份执行 setup-oracle-cdc.sql"
echo ""
echo "如果补充日志未启用，请执行："
echo "  ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;"
echo ""
echo "推荐的 CDC 用户权限："
echo "  GRANT SELECT ANY TABLE TO ${DB_USER};"
echo "  GRANT EXECUTE_CATALOG_ROLE TO ${DB_USER};"
echo "  GRANT SELECT ANY TRANSACTION TO ${DB_USER};"
echo "  GRANT LOGMINING TO ${DB_USER};"
echo "=========================================="
