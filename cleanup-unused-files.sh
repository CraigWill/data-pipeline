#!/bin/bash

# 清理无用文件脚本
# 用途：删除测试文件、临时文件、已迁移的配置文件等

set -e

echo "🧹 开始清理无用文件..."

# 1. 删除 .DS_Store 文件（macOS 系统文件）
echo "📁 删除 .DS_Store 文件..."
find . -name ".DS_Store" -type f -delete
echo "✅ .DS_Store 文件已删除"

# 2. 删除日志文件（保留目录）
echo "📝 清理日志文件..."
rm -f logs/*.log
echo "✅ 日志文件已清理"

# 3. 删除测试 SQL 文件
echo "🗄️  删除测试 SQL 文件..."
rm -f sql/test-*.sql
rm -f sql/insert-test-data.sql
echo "✅ 测试 SQL 文件已删除"

# 4. 删除已迁移的 JSON 配置文件（数据已迁移到数据库）
echo "📋 删除已迁移的 JSON 配置文件..."
rm -rf monitor/config/cdc_tasks/*.json
rm -rf monitor/config/datasources/*.json
echo "✅ JSON 配置文件已删除（数据已在数据库中）"

# 5. 删除临时脚本和诊断文件
echo "🔧 删除临时脚本..."
rm -f diagnose-tables.sh
rm -f check-db-tables.py
rm -f test-table-api.sh
echo "✅ 临时脚本已删除"

# 6. 删除 fix-log-mining-flush 相关文件（问题已解决）
echo "🔨 删除已解决问题的修复脚本..."
rm -f sql/fix-log-mining-flush.sh
rm -f sql/fix-log-mining-flush-docker.sh
rm -f sql/fix-log-mining-flush.py
echo "✅ 修复脚本已删除"

# 7. 删除旧的迁移脚本（迁移已完成）
echo "📦 删除迁移脚本..."
rm -f sql/migrate-json-to-db.py
echo "✅ 迁移脚本已删除"

# 8. 删除 .jqwik-database（测试框架缓存）
echo "🧪 删除测试缓存..."
rm -f .jqwik-database
echo "✅ 测试缓存已删除"

# 9. 删除 dependency-reduced-pom.xml（Maven shade 插件生成）
echo "📦 删除 Maven 生成文件..."
rm -f dependency-reduced-pom.xml
echo "✅ Maven 生成文件已删除"

# 10. 清理 output 目录中的 .inprogress 文件
echo "📤 清理未完成的输出文件..."
find output/cdc -name "*.inprogress.*" -type f -delete
echo "✅ 未完成的输出文件已清理"

# 11. 删除旧的部署文档（已整合到 README）
echo "📚 删除旧文档..."
rm -f DEPLOYMENT-COMPLETE.md
echo "✅ 旧文档已删除"

# 12. 删除 start.sh（功能已由 docker-compose 替代）
echo "🚀 删除旧启动脚本..."
rm -f start.sh
echo "✅ 旧启动脚本已删除"

echo ""
echo "✨ 清理完成！"
echo ""
echo "保留的重要文件："
echo "  - README.md (项目文档)"
echo "  - MIGRATION-SUMMARY.md (迁移总结)"
echo "  - MIGRATION-VERIFICATION.md (迁移验证)"
echo "  - RUNTIME-JOB-MANAGEMENT.md (运行时作业管理)"
echo "  - LOG-MINING-FLUSH-ISSUE-RESOLVED.md (问题解决记录)"
echo "  - sql/setup-*.sql (数据库初始化脚本)"
echo "  - sql/configure-oracle-for-cdc.sql (CDC 配置脚本)"
echo "  - sql/README-*.md (SQL 文档)"
echo ""
echo "已删除的文件类型："
echo "  - .DS_Store 文件"
echo "  - 日志文件 (logs/*.log)"
echo "  - 测试 SQL 文件 (sql/test-*.sql)"
echo "  - JSON 配置文件 (monitor/config/*/*.json)"
echo "  - 临时脚本和诊断工具"
echo "  - 已解决问题的修复脚本"
echo "  - 迁移脚本（迁移已完成）"
echo "  - 测试缓存和构建产物"
echo "  - 未完成的输出文件"
echo ""
