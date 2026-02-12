#!/bin/bash
# 测试 Oracle 数据库连接

set -e

echo "=========================================="
echo "Oracle 数据库连接测试"
echo "=========================================="
echo ""

# 加载环境变量
if [ -f .env ]; then
    export $(cat .env | grep -v '^#' | grep -v '^$' | xargs)
fi

# 设置默认值
DB_HOST=${DATABASE_HOST:-localhost}
DB_PORT=${DATABASE_PORT:-1521}
DB_USER=${DATABASE_USERNAME:-finance_user}
DB_PASSWORD=${DATABASE_PASSWORD:-password}
DB_SCHEMA=${DATABASE_SCHEMA:-helowin}
DB_TABLE=${DATABASE_TABLES:-trans_info}

echo "配置信息:"
echo "  主机: $DB_HOST"
echo "  端口: $DB_PORT"
echo "  用户: $DB_USER"
echo "  Schema: $DB_SCHEMA"
echo "  表: $DB_TABLE"
echo ""

# 1. 测试端口连接
echo "1. 测试端口连接..."
if nc -z -w5 $DB_HOST $DB_PORT 2>/dev/null; then
    echo "   ✅ 端口 $DB_HOST:$DB_PORT 可访问"
else
    echo "   ❌ 端口 $DB_HOST:$DB_PORT 无法访问"
    echo ""
    echo "可能的原因:"
    echo "  - Oracle 数据库未启动"
    echo "  - 端口配置错误"
    echo "  - 防火墙阻止连接"
    echo ""
    exit 1
fi
echo ""

# 2. 检查 Oracle JDBC 驱动
echo "2. 检查 Oracle JDBC 驱动..."
JAR_FILE=$(ls target/realtime-data-pipeline-*.jar 2>/dev/null | head -n 1)
if [ -z "$JAR_FILE" ]; then
    echo "   ❌ 找不到应用程序 JAR 文件"
    echo "   运行: mvn clean package -DskipTests"
    exit 1
fi

if jar tf "$JAR_FILE" | grep -q "oracle/jdbc/OracleDriver.class"; then
    echo "   ✅ Oracle JDBC 驱动已包含在 JAR 中"
else
    echo "   ⚠️  Oracle JDBC 驱动未找到（可能在依赖中）"
fi
echo ""

# 3. 使用 Java 测试数据库连接
echo "3. 测试数据库连接..."
cat > /tmp/TestConnection.java << 'EOF'
import java.sql.*;
import java.util.Properties;

public class TestConnection {
    public static void main(String[] args) {
        String host = System.getenv("DATABASE_HOST");
        String port = System.getenv("DATABASE_PORT");
        String user = System.getenv("DATABASE_USERNAME");
        String password = System.getenv("DATABASE_PASSWORD");
        String schema = System.getenv("DATABASE_SCHEMA");
        String table = System.getenv("DATABASE_TABLES");
        
        // Oracle JDBC URL 格式
        String jdbcUrl = String.format("jdbc:oracle:thin:@%s:%s:%s", host, port, schema);
        
        System.out.println("   JDBC URL: " + jdbcUrl);
        System.out.println("   用户: " + user);
        System.out.println("");
        
        try {
            // 加载驱动
            Class.forName("oracle.jdbc.OracleDriver");
            System.out.println("   ✅ Oracle JDBC 驱动加载成功");
            
            // 建立连接
            Properties props = new Properties();
            props.setProperty("user", user);
            props.setProperty("password", password);
            
            Connection conn = DriverManager.getConnection(jdbcUrl, props);
            System.out.println("   ✅ 数据库连接成功");
            
            // 测试查询
            String sql = String.format("SELECT COUNT(*) as cnt FROM %s.%s", schema, table);
            System.out.println("   执行查询: " + sql);
            
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            
            if (rs.next()) {
                int count = rs.getInt("cnt");
                System.out.println("   ✅ 表 " + schema + "." + table + " 有 " + count + " 条记录");
            }
            
            // 获取表结构
            System.out.println("");
            System.out.println("   表结构:");
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns(null, schema.toUpperCase(), table.toUpperCase(), null);
            
            int colNum = 0;
            while (columns.next()) {
                colNum++;
                String columnName = columns.getString("COLUMN_NAME");
                String columnType = columns.getString("TYPE_NAME");
                int columnSize = columns.getInt("COLUMN_SIZE");
                System.out.println("     " + colNum + ". " + columnName + " (" + columnType + "(" + columnSize + "))");
            }
            
            rs.close();
            stmt.close();
            conn.close();
            
            System.out.println("");
            System.out.println("✅ 所有测试通过！数据库连接正常。");
            System.exit(0);
            
        } catch (ClassNotFoundException e) {
            System.err.println("   ❌ Oracle JDBC 驱动未找到: " + e.getMessage());
            System.err.println("");
            System.err.println("解决方案:");
            System.err.println("  1. 检查 pom.xml 中是否包含 Oracle JDBC 依赖");
            System.err.println("  2. 运行: mvn clean package");
            System.exit(1);
        } catch (SQLException e) {
            System.err.println("   ❌ 数据库连接失败: " + e.getMessage());
            System.err.println("");
            System.err.println("可能的原因:");
            System.err.println("  - 用户名或密码错误");
            System.err.println("  - Schema 名称错误");
            System.err.println("  - 表不存在或无权限访问");
            System.err.println("  - 数据库服务未启动");
            System.err.println("");
            System.err.println("SQL State: " + e.getSQLState());
            System.err.println("Error Code: " + e.getErrorCode());
            System.exit(1);
        }
    }
}
EOF

# 编译并运行测试
javac -cp "$JAR_FILE" /tmp/TestConnection.java 2>/dev/null || {
    echo "   ⚠️  无法编译测试程序，尝试直接运行..."
}

java -cp "$JAR_FILE:/tmp" TestConnection

echo ""
echo "=========================================="
echo "测试完成"
echo "=========================================="
