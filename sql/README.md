# SQL 脚本目录

本目录包含所有用于配置和测试 Oracle 数据库 CDC 的 SQL 脚本。

## 目录结构

```
sql/
├── README.md                          # 本文件
├── setup-oracle-cdc.sql              # Oracle CDC 完整配置脚本
├── configure-oracle-for-cdc.sql      # Oracle CDC 配置（简化版）
├── enable-archivelog.sql             # 启用归档日志（简单版）
├── enable-archivelog-mode.sql        # 启用归档日志（完整版）
├── enable-oracle-archivelog.sql      # 启用归档日志（可执行版）
├── check-archivelog-status.sql       # 检查归档日志状态
├── create-test-table.sql             # 创建测试表
├── insert-test-data.sql              # 插入测试数据
├── test-*.sql                        # 各种测试脚本
└── ...
```

## 使用方法

### 从项目根目录执行

```bash
# 使用 sqlplus 执行
sqlplus system/password@database @sql/setup-oracle-cdc.sql

# 或者在 Docker 容器中执行
docker exec oracle11g bash -c "
export ORACLE_HOME=/home/oracle/app/oracle/product/11.2.0/dbhome_2
export PATH=\$ORACLE_HOME/bin:\$PATH
export ORACLE_SID=helowin
sqlplus -S system/helowin @/path/to/sql/setup-oracle-cdc.sql
"
```

### 从 shell 脚本中引用

Shell 脚本已经更新为使用相对路径：

```bash
# 在 shell 脚本中
sqlplus system/password @../sql/setup-oracle-cdc.sql
cat ../sql/configure-oracle-for-cdc.sql
```

## 主要脚本说明

### 配置脚本

#### setup-oracle-cdc.sql
**用途**: Oracle CDC 完整配置脚本  
**功能**:
- 启用归档日志模式
- 启用补充日志
- 配置 LogMiner
- 创建 CDC 用户和权限

**使用场景**: 首次配置 Oracle CDC

**执行方式**:
```sql
sqlplus / as sysdba @sql/setup-oracle-cdc.sql
```

#### configure-oracle-for-cdc.sql
**用途**: Oracle CDC 配置（简化版）  
**功能**:
- 启用数据库级补充日志
- 启用表级补充日志
- 配置必要的权限

**使用场景**: 归档日志已启用，只需配置补充日志

**执行方式**:
```sql
sqlplus system/helowin @sql/configure-oracle-for-cdc.sql
```

### 归档日志脚本

#### enable-archivelog.sql
**用途**: 启用归档日志（简单版）  
**功能**:
- 关闭数据库
- 启动到 MOUNT 状态
- 启用归档日志
- 打开数据库

**使用场景**: 快速启用归档日志

**执行方式**:
```sql
sqlplus / as sysdba @sql/enable-archivelog.sql
```

#### enable-archivelog-mode.sql
**用途**: 启用归档日志（完整版）  
**功能**:
- 检查当前状态
- 配置归档日志目录
- 启用归档日志
- 验证配置

**使用场景**: 生产环境配置归档日志

**执行方式**:
```sql
sqlplus / as sysdba @sql/enable-archivelog-mode.sql
```

#### check-archivelog-status.sql
**用途**: 检查归档日志状态  
**功能**:
- 查询归档日志模式
- 查询归档日志目录
- 查询归档日志文件

**使用场景**: 验证归档日志配置

**执行方式**:
```sql
sqlplus system/helowin @sql/check-archivelog-status.sql
```

### 测试脚本

#### create-test-table.sql
**用途**: 创建测试表  
**功能**: 创建 TRANS_INFO 测试表

#### insert-test-data.sql
**用途**: 插入测试数据  
**功能**: 插入多条测试交易记录

#### test-insert.sql, test-insert2.sql, test-insert3.sql
**用途**: 插入测试数据（不同版本）  
**功能**: 快速插入测试数据验证 CDC

#### test-update.sql, test-update2.sql, test-update3.sql
**用途**: 更新测试数据  
**功能**: 更新记录验证 CDC 捕获 UPDATE 操作

#### test-multiple-updates.sql
**用途**: 批量更新测试  
**功能**: 执行多次更新操作

#### test-insert-multiple.sql
**用途**: 批量插入测试  
**功能**: 插入多条记录

#### test-header-check.sql
**用途**: 检查 CSV 标题行  
**功能**: 插入数据验证 CSV 标题行生成

#### test-naming-verify.sql
**用途**: 验证文件命名  
**功能**: 插入数据验证 CSV 文件命名格式

#### test-new-naming.sql
**用途**: 测试新的命名格式  
**功能**: 验证新的 CSV 文件命名规则

#### test-quick.sql
**用途**: 快速测试  
**功能**: 快速插入一条记录测试 CDC

#### test-final.sql
**用途**: 最终测试  
**功能**: 最终验证 CDC 功能

## 脚本分类

### 按功能分类

**配置类**:
- setup-oracle-cdc.sql
- configure-oracle-for-cdc.sql

**归档日志类**:
- enable-archivelog.sql
- enable-archivelog-mode.sql
- enable-oracle-archivelog.sql
- check-archivelog-status.sql

**测试类**:
- create-test-table.sql
- insert-test-data.sql
- test-*.sql

### 按使用频率分类

**常用脚本**:
1. setup-oracle-cdc.sql - 首次配置
2. check-archivelog-status.sql - 状态检查
3. insert-test-data.sql - 测试数据
4. test-insert.sql - 快速测试

**偶尔使用**:
- enable-archivelog-mode.sql - 启用归档日志
- configure-oracle-for-cdc.sql - 补充日志配置

**调试用**:
- test-*.sql - 各种测试场景

## 执行顺序

### 首次配置 Oracle CDC

1. **启用归档日志**（如果未启用）
   ```bash
   ./shell/enable-archivelog-docker.sh
   # 或手动执行
   sqlplus / as sysdba @sql/enable-archivelog-mode.sql
   ```

2. **配置 CDC**
   ```bash
   sqlplus / as sysdba @sql/setup-oracle-cdc.sql
   ```

3. **验证配置**
   ```bash
   ./shell/check-oracle-cdc-status.sh
   ```

4. **测试 CDC**
   ```bash
   sqlplus system/helowin @sql/insert-test-data.sql
   ./shell/quick-test-cdc.sh
   ```

### 日常测试

```bash
# 快速插入测试数据
sqlplus system/helowin @sql/test-insert.sql

# 或使用 shell 脚本
./shell/quick-test-cdc.sh
```

## 注意事项

1. **权限要求**
   - 配置脚本需要 SYSDBA 权限
   - 测试脚本需要对应 schema 的权限

2. **归档日志**
   - 启用归档日志需要重启数据库
   - 确保有足够的磁盘空间存储归档日志

3. **补充日志**
   - 补充日志会增加 Redo Log 大小
   - 建议只对需要 CDC 的表启用

4. **测试脚本**
   - 测试脚本会插入/更新数据
   - 不要在生产环境随意执行

## 相关文档

- Shell 脚本: `../shell/`
- Markdown 文档: `../md/`
- 项目文档: `../docs/`
- Docker 配置: `../docker/`

## 常见问题

### Q: 如何检查归档日志是否启用？
```sql
sqlplus system/helowin @sql/check-archivelog-status.sql
```

### Q: 如何快速测试 CDC？
```bash
# 方法 1: 使用 SQL 脚本
sqlplus system/helowin @sql/test-insert.sql

# 方法 2: 使用 shell 脚本
./shell/quick-test-cdc.sh
```

### Q: 如何重新配置 CDC？
```sql
-- 1. 禁用补充日志
ALTER DATABASE DROP SUPPLEMENTAL LOG DATA;

-- 2. 重新配置
sqlplus / as sysdba @sql/setup-oracle-cdc.sql
```

### Q: 脚本执行失败怎么办？
1. 检查数据库连接
2. 检查用户权限
3. 查看错误日志
4. 参考 `../md/` 目录中的问题解决文档

## 维护说明

1. **新增脚本**
   - 放入 sql/ 目录
   - 更新本 README
   - 添加注释说明用途

2. **修改脚本**
   - 保留旧版本（添加版本号）
   - 更新文档说明
   - 测试后再提交

3. **删除脚本**
   - 确认不再使用
   - 移动到 archive/ 子目录
   - 更新文档

## 脚本模板

### 测试脚本模板

```sql
-- 脚本名称: test-example.sql
-- 用途: 测试示例
-- 作者: [Your Name]
-- 日期: YYYY-MM-DD

-- 连接到数据库
CONNECT system/helowin@helowin;

-- 插入测试数据
INSERT INTO FINANCE_USER.TRANS_INFO (
    ID, ACCOUNT_ID, AMOUNT, TRANS_TIME, TRANS_TYPE, MERCHANT_ID, STATUS
) VALUES (
    SEQ_TRANS_INFO.NEXTVAL, 
    'TEST_ACCOUNT', 
    100.00, 
    SYSTIMESTAMP, 
    'TEST', 
    'MERCHANT_TEST', 
    'SUCCESS'
);

-- 提交
COMMIT;

-- 显示结果
SELECT COUNT(*) FROM FINANCE_USER.TRANS_INFO WHERE ACCOUNT_ID = 'TEST_ACCOUNT';

EXIT;
```

### 配置脚本模板

```sql
-- 脚本名称: configure-example.sql
-- 用途: 配置示例
-- 作者: [Your Name]
-- 日期: YYYY-MM-DD
-- 权限要求: SYSDBA

-- 连接为 SYSDBA
CONNECT / AS SYSDBA;

-- 配置步骤 1
ALTER DATABASE ...;

-- 配置步骤 2
ALTER SYSTEM ...;

-- 验证配置
SELECT ... FROM ...;

EXIT;
```
