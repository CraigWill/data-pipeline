#!/bin/bash
# 快速测试镜像构建脚本

set -e

echo "=== 测试 Flink 私有镜像构建 ==="

# 检查 JAR 文件
if [ ! -f "target/realtime-data-pipeline-1.0.0-SNAPSHOT.jar" ]; then
    echo "错误: 未找到 JAR 文件，请先运行 mvn clean package -DskipTests"
    exit 1
fi

echo "✓ JAR 文件存在"

# 测试构建 JobManager
echo ""
echo "=== 测试构建 JobManager 镜像 ==="
docker build -f docker/jobmanager/Dockerfile -t flink-jobmanager:test . --no-cache
if [ $? -eq 0 ]; then
    echo "✓ JobManager 镜像构建成功"
else
    echo "✗ JobManager 镜像构建失败"
    exit 1
fi

# 测试构建 TaskManager
echo ""
echo "=== 测试构建 TaskManager 镜像 ==="
docker build -f docker/taskmanager/Dockerfile -t flink-taskmanager:test . --no-cache
if [ $? -eq 0 ]; then
    echo "✓ TaskManager 镜像构建成功"
else
    echo "✗ TaskManager 镜像构建失败"
    exit 1
fi

# 显示镜像信息
echo ""
echo "=== 镜像信息 ==="
docker images | grep -E "REPOSITORY|flink-(jobmanager|taskmanager):test"

# 验证镜像内容
echo ""
echo "=== 验证 JobManager 镜像内容 ==="
docker run --rm flink-jobmanager:test ls -lh /opt/flink/bin/flink
docker run --rm flink-jobmanager:test /opt/flink/bin/flink --version

echo ""
echo "=== 验证 TaskManager 镜像内容 ==="
docker run --rm flink-taskmanager:test ls -lh /opt/flink/bin/flink
docker run --rm flink-taskmanager:test /opt/flink/bin/flink --version

echo ""
echo "✓ 所有测试通过！"
echo ""
echo "清理测试镜像："
echo "  docker rmi flink-jobmanager:test flink-taskmanager:test"
