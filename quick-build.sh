#!/bin/bash
# 快速构建脚本 - 使用 Java 17

set -e

echo "=== 快速构建脚本 ==="
echo ""

# 检查 Java 17 是否可用
if /usr/libexec/java_home -v 17 >/dev/null 2>&1; then
    export JAVA_HOME=$(/usr/libexec/java_home -v 17)
    echo "✓ 使用 Java 17: $JAVA_HOME"
elif /usr/libexec/java_home -v 11 >/dev/null 2>&1; then
    export JAVA_HOME=$(/usr/libexec/java_home -v 11)
    echo "⚠ Java 17 未安装，使用 Java 11: $JAVA_HOME"
    echo "建议安装 Java 17: brew install openjdk@17"
else
    echo "✗ Java 11 或 17 未安装"
    echo "请先安装 Java: brew install openjdk@17"
    exit 1
fi

# 显示 Java 版本
echo ""
java -version
echo ""

# 构建项目
echo "开始 Maven 构建..."
mvn clean install -DskipTests

# 检查构建结果
echo ""
echo "=== 构建结果 ==="
if [ -f "flink-jobs/target/flink-jobs-1.0.0-SNAPSHOT.jar" ]; then
    echo "✓ Flink Jobs JAR: $(ls -lh flink-jobs/target/flink-jobs-*.jar | awk '{print $9, $5}')"
else
    echo "✗ Flink Jobs JAR 未找到"
fi

if [ -f "monitor-backend/target/monitor-backend-1.0.0-SNAPSHOT.jar" ]; then
    echo "✓ Monitor Backend JAR: $(ls -lh monitor-backend/target/monitor-backend-*.jar | awk '{print $9, $5}')"
else
    echo "✗ Monitor Backend JAR 未找到"
fi

echo ""
echo "=== 下一步 ==="
echo "1. 构建 Docker 镜像: ./rebuild-all.sh"
echo "2. 启动服务: docker-compose up -d"
