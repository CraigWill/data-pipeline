# SQL 文件重组总结

**日期**: 2026-02-26  
**操作**: 创建 sql 目录并移动所有 SQL 文件

## 变更概述

继上次文件重组（创建 md 和 shell 目录）后，我们将根目录下的所有 SQL 脚本移动到专门的 sql 目录中。

## 变更详情

### 1. 创建新目录

```bash
mkdir -p sql
```

- `sql/`: 存放所有 SQL 脚本

### 2. 移动文件

#### SQL 文件
- **移动数量**: 22 个文件
- **新位置**: `sql/`

移动的文件列表：
1. setup-oracle-cdc.sql
2. configure-oracle-for-cdc.sql
3. enable-archivelog.sql
4. enable-archivelog-mode.sql
5. enable-oracle-archivelog.sql
6. check-archivelog-status.sql
7. create-test-table.sql
8. insert-test-data.sql
9. test-insert.sql
10. test-insert2.sql
11. test-insert3.sql
12. test-insert-data.sql
13. test-insert-multiple.sql
14. test-update.sql
15. test-update2.sql
16. test-update3.sql
17. test-multiple-updates.sql
18. test-header-check.sql
19. test-naming-verify.sql
20. test-new-naming.sql
21. test-quick.sql
22. test-final.sql

### 3. 路径更新

所有 Shell 脚本中的 SQL 文件引用已自动更新：

#### SQL 文件引用
```bash
# 旧路径
@../setup-oracle-cdc.sql
cat ../configure-oracle-for-cdc.sql
参考 ../enable-archivelog-mode.sql

# 新路径
@../sql/setup-oracle-cdc.sql
cat ../sql/configure-oracle-for-cdc.sql
参考 ../sql/enable-archivelog-mode.sql
```

### 4. 更新的脚本

以下脚本的 SQL 路径引用已更新：

1. `check-oracle-cdc-jdbc.sh`
2. `check-oracle-cdc-status.sh`
3. `enable-archivelog-docker.sh`
4. `execute-oracle-cdc-config.sh`
5. `setup-logminer-cdc.sh`
6. `test-db-connection.sh`
7. `test-oracle-connection.sh`

## 使用方法

### 执行 SQL 脚本

```bash
# 方法 1: 使用 sqlplus（从根目录）
sqlplus system/password@database @sql/setup-oracle-cdc.sql

# 方法 2: 在 Docker 容器中执行
docker exec oracle11g bash -c "
export ORACLE_HOME=/home/oracle/app/oracle/product/11.2.0/dbhome_2
export PATH=\$ORACLE_HOME/bin:\$PATH
export ORACLE_SID=helowin
sqlplus -S system/helowin @/path/to/sql/setup-oracle-cdc.sql
"

# 方法 3: 从 shell 脚本中引用（路径已自动更新）
./shell/setup-logminer-cdc.sh
```

### 查看 SQL 脚本

```bash
# 查看配置脚本
cat sql/setup-oracle-cdc.sql
cat sql/configure-oracle-for-cdc.sql

# 查看测试脚本
ls sql/test-*.sql

# 查看说明文档
cat sql/README.md
```

## 目录结构

### 完整的项目结构

```
realtime-data-pipeline/
├── shell/          (50 个脚本 + README)
├── md/             (48 个文档 + README)
├── sql/            (22 个脚本 + README) ⭐ 新增
├── docs/           (项目文档)
├── docker/         (Docker 配置)
├── src/            (源代码)
├── output/         (输出目录)
├── docker-compose.yml
├── pom.xml
└── README.md
```

### SQL 目录结构

```
sql/
├── README.md                          # SQL 脚本说明
├── setup-oracle-cdc.sql              # Oracle CDC 完整配置
├── configure-oracle-for-cdc.sql      # Oracle CDC 配置（简化版）
├── enable-archivelog.sql             # 启用归档日志（简单版）
├── enable-archivelog-mode.sql        # 启用归档日志（完整版）
├── enable-oracle-archivelog.sql      # 启用归档日志（可执行版）
├── check-archivelog-status.sql       # 检查归档日志状态
├── create-test-table.sql             # 创建测试表
├── insert-test-data.sql              # 插入测试数据
└── test-*.sql                        # 各种测试脚本
```

## 验证

### 验证文件移动

```bash
# 检查根目录是否还有 .sql 文件
find . -maxdepth 1 -name "*.sql" -type f | wc -l
# 应该输出: 0

# 检查 sql 目录文件数量
ls sql/*.sql | wc -l
# 应该输出: 22

# 列出所有 SQL 文件
ls -1 sql/*.sql
```

### 验证路径更新

```bash
# 检查脚本中的 SQL 路径引用
grep "setup-oracle-cdc.sql" shell/check-oracle-cdc-status.sh
# 应该输出: echo "  sqlplus / as sysdba @../sql/setup-oracle-cdc.sql"

grep "configure-oracle-for-cdc.sql" shell/setup-logminer-cdc.sh
# 应该输出: echo "  @../sql/configure-oracle-for-cdc.sql"
#          cat ../sql/configure-oracle-for-cdc.sql | ...

grep "enable-archivelog-mode.sql" shell/enable-archivelog-docker.sh
# 应该输出: echo "  参考 ../sql/enable-archivelog-mode.sql 文件"
```

### 功能测试

```bash
# 测试 SQL 脚本是否能正常访问
cat sql/setup-oracle-cdc.sql

# 测试 shell 脚本中的引用是否正确
./shell/check-oracle-cdc-status.sh
```

## SQL 脚本分类

### 配置脚本
- `setup-oracle-cdc.sql` - 完整的 Oracle CDC 配置
- `configure-oracle-for-cdc.sql` - 简化的 CDC 配置

### 归档日志脚本
- `enable-archivelog.sql` - 启用归档日志（简单版）
- `enable-archivelog-mode.sql` - 启用归档日志（完整版）
- `enable-oracle-archivelog.sql` - 启用归档日志（可执行版）
- `check-archivelog-status.sql` - 检查归档日志状态

### 测试脚本
- `create-test-table.sql` - 创建测试表
- `insert-test-data.sql` - 插入测试数据
- `test-insert*.sql` - 插入测试（多个版本）
- `test-update*.sql` - 更新测试（多个版本）
- `test-multiple-updates.sql` - 批量更新测试
- `test-insert-multiple.sql` - 批量插入测试
- `test-header-check.sql` - CSV 标题行测试
- `test-naming-verify.sql` - 文件命名验证
- `test-new-naming.sql` - 新命名格式测试
- `test-quick.sql` - 快速测试
- `test-final.sql` - 最终测试

## 优势

### 1. 更清晰的项目结构
- SQL 脚本集中管理
- 便于查找和维护
- 根目录更加整洁

### 2. 更好的组织性
- 按功能分类（配置、测试、归档日志）
- 有专门的 README 说明
- 便于新成员理解

### 3. 更容易维护
- 新增 SQL 脚本直接放入 sql/ 目录
- 不会污染根目录
- 版本控制更清晰

### 4. 更好的可移植性
- SQL 脚本独立于其他文件
- 可以单独打包分发
- 便于在不同环境使用

## 与之前的文件重组对比

### 第一次重组（md 和 shell）
- 创建了 md/ 目录（47 个文档）
- 创建了 shell/ 目录（50 个脚本）
- 更新了 13 个脚本的路径引用

### 第二次重组（sql）
- 创建了 sql/ 目录（22 个脚本）
- 更新了 7 个脚本的路径引用
- 完善了项目结构

### 总体效果
```
重组前:
realtime-data-pipeline/
├── 47 个 .md 文件（散落在根目录）
├── 50 个 .sh 文件（散落在根目录）
├── 22 个 .sql 文件（散落在根目录）
└── ...

重组后:
realtime-data-pipeline/
├── shell/          (50 个脚本 + README)
├── md/             (48 个文档 + README)
├── sql/            (22 个脚本 + README)
├── docs/           (项目文档)
├── docker/         (Docker 配置)
├── src/            (源代码)
└── ...
```

## 注意事项

1. **执行 SQL 脚本时的路径**
   - 从根目录执行：`sqlplus ... @sql/script.sql`
   - 从 shell 脚本引用：`../sql/script.sql`

2. **Docker 容器中的路径**
   - 如果需要在容器中执行，需要挂载 sql 目录
   - 或者将 SQL 文件复制到容器中

3. **权限要求**
   - 某些 SQL 脚本需要 SYSDBA 权限
   - 测试脚本需要对应 schema 的权限

4. **归档日志脚本**
   - 启用归档日志需要重启数据库
   - 谨慎在生产环境执行

## 相关文件

- `sql/README.md` - SQL 脚本详细说明
- `shell/README.md` - Shell 脚本使用说明
- `md/README.md` - Markdown 文档索引
- `md/FILE_REORGANIZATION_SUMMARY.md` - 第一次文件重组总结
- `README.md` - 项目主文档（已更新）

## 回滚方法

如果需要回滚到原来的结构：

```bash
# 移回 SQL 文件
mv sql/*.sql .

# 删除 sql 目录
rm -rf sql

# 恢复路径引用（需要手动或使用备份）
# 将 ../sql/ 替换回 ../
```

## 总结

✅ **SQL 文件重组成功完成**

- 移动了 22 个 SQL 脚本到 sql/ 目录
- 更新了 7 个 shell 脚本中的路径引用
- 创建了 sql/README.md 详细说明文档
- 更新了主 README.md 文档

**完整的文件重组成果**:
- shell/ 目录：50 个脚本 + README
- md/ 目录：48 个文档 + README
- sql/ 目录：22 个脚本 + README

项目结构现在非常清晰，所有文件都按类型分类存放，便于维护和使用！

## 下一步建议

1. **创建 scripts/ 目录**（可选）
   - 存放其他类型的脚本（Python、JavaScript 等）

2. **创建 config/ 目录**（可选）
   - 存放配置文件模板

3. **创建 logs/ 目录**（可选）
   - 存放日志文件

4. **添加 .gitignore 规则**
   - 忽略临时文件和日志文件
   - 保持仓库整洁
