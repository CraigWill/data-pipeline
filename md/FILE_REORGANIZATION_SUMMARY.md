# 文件重组总结

**日期**: 2026-02-26  
**操作**: 整理项目根目录，创建 md 和 shell 目录

## 变更概述

为了更好地组织项目文件，我们将根目录下的 Markdown 文档和 Shell 脚本分别移动到专门的目录中。

## 变更详情

### 1. 创建新目录

```bash
mkdir -p md shell
```

- `md/`: 存放所有 Markdown 文档（除 README.md）
- `shell/`: 存放所有 Shell 脚本

### 2. 移动文件

#### Markdown 文件
- **移动数量**: 47 个文件
- **保留位置**: `README.md` 保留在根目录
- **新位置**: `md/`

#### Shell 脚本
- **移动数量**: 50 个文件
- **新位置**: `shell/`

### 3. 路径更新

所有 Shell 脚本中的路径引用已自动更新：

#### 脚本引用
```bash
# 旧路径
./quick-test-cdc.sh
./diagnose-cdc-issue.sh

# 新路径
./shell/quick-test-cdc.sh
./shell/diagnose-cdc-issue.sh
```

#### SQL 文件引用
```bash
# 旧路径
@setup-oracle-cdc.sql
cat configure-oracle-for-cdc.sql

# 新路径
@../setup-oracle-cdc.sql
cat ../configure-oracle-for-cdc.sql
```

#### Markdown 文件引用
```bash
# 旧路径
cat CSV_FILE_NOT_GENERATED_SOLUTION.md

# 新路径
cat ./md/CSV_FILE_NOT_GENERATED_SOLUTION.md
```

### 4. 更新的脚本

以下脚本的路径引用已更新：

1. `check-cdc-status.sh`
2. `check-oracle-cdc-simple.sh`
3. `check-oracle-cdc-status.sh`
4. `check-oracle-cdc-jdbc.sh`
5. `enable-flink-ha.sh`
6. `enable-archivelog-docker.sh`
7. `setup-logminer-cdc.sh`
8. `start-flink-cdc.sh`
9. `start-job-monitor.sh`
10. `switch-to-latest-mode.sh`
11. `test-oracle-connection.sh`
12. `restart-flink-cdc-job.sh`
13. `quick-test-cdc.sh`

## 使用方法

### 从根目录执行脚本

```bash
# 推荐方式：从根目录执行
./shell/restart-flink-cdc-job.sh
./shell/quick-test-cdc.sh
./shell/check-cdc-status.sh
```

### 进入目录执行

```bash
# 也可以进入目录执行
cd shell
./restart-flink-cdc-job.sh
./quick-test-cdc.sh
cd ..
```

### 查看文档

```bash
# 查看状态报告
cat md/CURRENT_CDC_STATUS.md

# 查看问题解决方案
cat md/CSV_FILE_NOT_GENERATED_SOLUTION.md

# 列出所有 CDC 相关文档
ls md/CDC_*.md
```

## 目录结构

### 新的项目结构

```
realtime-data-pipeline/
├── shell/                           # Shell 脚本目录 ⭐
│   ├── README.md                   # 脚本使用说明
│   ├── restart-flink-cdc-job.sh   # 重启作业
│   ├── quick-test-cdc.sh          # 快速测试
│   ├── check-cdc-status.sh        # 检查状态
│   └── ...                         # 其他 50 个脚本
├── md/                              # Markdown 文档目录 ⭐
│   ├── README.md                   # 文档索引
│   ├── CURRENT_CDC_STATUS.md      # 当前状态
│   ├── CDC_*.md                    # CDC 相关文档
│   ├── CSV_*.md                    # CSV 相关文档
│   └── ...                         # 其他 47 个文档
├── docs/                            # 项目文档
├── docker/                          # Docker 配置
├── src/                             # 源代码
├── output/                          # 输出目录
├── *.sql                           # SQL 脚本（保留在根目录）
├── docker-compose.yml              # Docker Compose 配置
├── pom.xml                         # Maven 配置
└── README.md                       # 主文档
```

### 根目录清理结果

根目录现在更加整洁：

- ✅ 只保留必要的配置文件（docker-compose.yml, pom.xml, .env 等）
- ✅ SQL 脚本保留在根目录（便于 Docker 容器访问）
- ✅ README.md 保留在根目录
- ✅ 所有 Shell 脚本移至 shell/ 目录
- ✅ 所有 Markdown 文档移至 md/ 目录

## 验证

### 验证文件移动

```bash
# 检查根目录是否还有 .md 文件（除 README.md）
find . -maxdepth 1 -name "*.md" -type f ! -name "README.md" | wc -l
# 应该输出: 0

# 检查根目录是否还有 .sh 文件
find . -maxdepth 1 -name "*.sh" -type f | wc -l
# 应该输出: 0 或很少（只有临时脚本）

# 检查 md 目录文件数量
ls md/*.md | wc -l
# 应该输出: 48（47 个文档 + 1 个 README）

# 检查 shell 目录文件数量
ls shell/*.sh | wc -l
# 应该输出: 51（50 个脚本 + 1 个 README）
```

### 验证路径更新

```bash
# 检查脚本中的路径引用
grep "quick-test-cdc.sh" shell/restart-flink-cdc-job.sh
# 应该输出: echo "  ./shell/quick-test-cdc.sh"

grep "CSV_FILE_NOT_GENERATED" shell/quick-test-cdc.sh
# 应该输出: echo "- 查看解决方案: cat ./md/CSV_FILE_NOT_GENERATED_SOLUTION.md"
```

### 功能测试

```bash
# 测试脚本是否能正常执行
./shell/check-cdc-status.sh

# 测试文档是否能正常访问
cat md/CURRENT_CDC_STATUS.md
```

## 优势

### 1. 更清晰的项目结构
- 根目录不再杂乱
- 文件分类明确
- 易于导航和查找

### 2. 更好的可维护性
- 脚本集中管理
- 文档集中存放
- 便于版本控制

### 3. 更友好的使用体验
- 新用户更容易理解项目结构
- 脚本和文档有专门的 README 说明
- 路径引用更加规范

### 4. 更容易扩展
- 新增脚本直接放入 shell/ 目录
- 新增文档直接放入 md/ 目录
- 不会污染根目录

## 注意事项

1. **执行脚本时的路径**
   - 推荐从根目录执行：`./shell/script.sh`
   - 也可以进入目录执行：`cd shell && ./script.sh`

2. **脚本中的相对路径**
   - 所有路径都已更新为相对于根目录
   - 不需要手动修改

3. **SQL 文件位置**
   - SQL 文件仍保留在根目录
   - 便于 Docker 容器挂载和访问

4. **文档引用**
   - 在其他文档中引用时使用新路径
   - 例如：`参见 md/CURRENT_CDC_STATUS.md`

## 相关文件

- `shell/README.md` - Shell 脚本使用说明
- `md/README.md` - Markdown 文档索引
- `README.md` - 项目主文档（已更新）
- `fix-all-paths.sh` - 路径更新脚本（临时文件，可删除）

## 回滚方法

如果需要回滚到原来的结构：

```bash
# 移回 Markdown 文件
mv md/*.md .

# 移回 Shell 脚本
mv shell/*.sh .

# 删除新目录
rm -rf md shell

# 恢复路径引用（需要手动或使用备份）
```

## 总结

✅ **文件重组成功完成**

- 移动了 47 个 Markdown 文档到 md/ 目录
- 移动了 50 个 Shell 脚本到 shell/ 目录
- 更新了 13 个脚本中的路径引用
- 创建了 README 文档说明使用方法
- 更新了主 README 文档

项目结构现在更加清晰和易于维护！
