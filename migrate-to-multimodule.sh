#!/bin/bash
# 项目重构脚本：将单模块项目拆分为多模块项目
# flink-jobs: Flink CDC 任务
# monitor-backend: Spring Boot 监控后端

set -e

echo "=== 开始项目重构 ==="

# 1. 创建目录结构
echo "创建目录结构..."
mkdir -p flink-jobs/src/main/java/com/realtime/pipeline
mkdir -p flink-jobs/src/main/resources
mkdir -p flink-jobs/src/test/java

mkdir -p monitor-backend/src/main/java/com/realtime
mkdir -p monitor-backend/src/main/resources
mkdir -p monitor-backend/src/test/java

# 2. 移动 Flink 相关代码
echo "移动 Flink 任务代码..."
if [ -d "src/main/java/com/realtime/pipeline" ]; then
    cp -r src/main/java/com/realtime/pipeline/* flink-jobs/src/main/java/com/realtime/pipeline/
    echo "  ✓ 复制 pipeline 包"
fi

# 3. 移动 Monitor Backend 代码
echo "移动 Monitor Backend 代码..."
if [ -d "src/main/java/com/realtime/monitor" ]; then
    cp -r src/main/java/com/realtime/monitor monitor-backend/src/main/java/com/realtime/
    echo "  ✓ 复制 monitor 包"
fi

if [ -f "src/main/java/com/realtime/UnifiedApplication.java" ]; then
    cp src/main/java/com/realtime/UnifiedApplication.java monitor-backend/src/main/java/com/realtime/
    echo "  ✓ 复制 UnifiedApplication.java"
fi

# 4. 移动资源文件
echo "移动资源文件..."

# Flink 资源
if [ -f "src/main/resources/log4j2.xml" ]; then
    cp src/main/resources/log4j2.xml flink-jobs/src/main/resources/
    echo "  ✓ 复制 log4j2.xml 到 flink-jobs"
fi

# Monitor Backend 资源
for file in application.yml application-*.yml; do
    if [ -f "src/main/resources/$file" ]; then
        cp "src/main/resources/$file" monitor-backend/src/main/resources/
        echo "  ✓ 复制 $file 到 monitor-backend"
    fi
done

# 5. 移动测试代码
echo "移动测试代码..."
if [ -d "src/test/java" ]; then
    # 根据包名分类测试代码
    if [ -d "src/test/java/com/realtime/pipeline" ]; then
        cp -r src/test/java/com/realtime/pipeline flink-jobs/src/test/java/com/realtime/ 2>/dev/null || true
        echo "  ✓ 复制 pipeline 测试"
    fi
    
    if [ -d "src/test/java/com/realtime/monitor" ]; then
        cp -r src/test/java/com/realtime/monitor monitor-backend/src/test/java/com/realtime/ 2>/dev/null || true
        echo "  ✓ 复制 monitor 测试"
    fi
fi

# 6. 备份原始 pom.xml
echo "备份原始配置..."
if [ -f "pom.xml" ]; then
    cp pom.xml pom.xml.backup
    echo "  ✓ 备份 pom.xml -> pom.xml.backup"
fi

# 7. 更新 Dockerfile 引用
echo "更新 Dockerfile..."

# 更新 monitor/Dockerfile
if [ -f "monitor/Dockerfile" ]; then
    sed -i.bak 's|target/realtime-data-pipeline-.*\.jar|monitor-backend/target/monitor-backend-*.jar|g' monitor/Dockerfile
    echo "  ✓ 更新 monitor/Dockerfile"
fi

# 更新 JobManager Dockerfile
for dockerfile in docker/jobmanager/Dockerfile docker/jobmanager/Dockerfile.cn; do
    if [ -f "$dockerfile" ]; then
        sed -i.bak 's|target/realtime-data-pipeline-.*\.jar|flink-jobs/target/flink-jobs-*.jar|g' "$dockerfile"
        echo "  ✓ 更新 $dockerfile"
    fi
done

# 更新 TaskManager Dockerfile
for dockerfile in docker/taskmanager/Dockerfile docker/taskmanager/Dockerfile.cn; do
    if [ -f "$dockerfile" ]; then
        sed -i.bak 's|target/realtime-data-pipeline-.*\.jar|flink-jobs/target/flink-jobs-*.jar|g' "$dockerfile"
        echo "  ✓ 更新 $dockerfile"
    fi
done

# 8. 更新构建脚本
echo "更新构建脚本..."

for script in build-flink-images.sh build-flink-images-cn.sh; do
    if [ -f "$script" ]; then
        sed -i.bak 's|target/realtime-data-pipeline-1.0.0-SNAPSHOT.jar|flink-jobs/target/flink-jobs-1.0.0-SNAPSHOT.jar|g' "$script"
        echo "  ✓ 更新 $script"
    fi
done

if [ -f "start.sh" ]; then
    sed -i.bak 's|target/realtime-data-pipeline-1.0.0-SNAPSHOT.jar|flink-jobs/target/flink-jobs-1.0.0-SNAPSHOT.jar|g' start.sh
    echo "  ✓ 更新 start.sh"
fi

echo ""
echo "=== 重构完成 ==="
echo ""
echo "项目结构："
echo "  ├── pom.xml (父 POM)"
echo "  ├── flink-jobs/ (Flink CDC 任务)"
echo "  │   ├── pom.xml"
echo "  │   └── src/main/java/com/realtime/pipeline/"
echo "  └── monitor-backend/ (Spring Boot 监控后端)"
echo "      ├── pom.xml"
echo "      └── src/main/java/com/realtime/monitor/"
echo ""
echo "下一步："
echo "  1. 验证构建: mvn clean install"
echo "  2. 构建 Flink 镜像: ./build-flink-images-cn.sh"
echo "  3. 启动服务: ./start.sh"
echo ""
echo "备份文件："
echo "  - pom.xml.backup (原始 POM)"
echo "  - *.bak (Dockerfile 和脚本备份)"
