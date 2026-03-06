# 表列表问题诊断和修复

## 问题描述

在创建 CDC 任务时，表选择列表中出现以下问题：
1. 有重复的表名（如 2 个 ACCOUNT_INFO）
2. 表的行数统计不准确

## 可能的原因

1. **回收站中的表**: Oracle 删除表时会放入回收站，可能导致重复
2. **统计信息过期**: 表的 `num_rows` 统计信息未更新
3. **查询逻辑问题**: SQL 查询可能返回了重复记录
4. **大小写问题**: Schema 名称大小写不一致

## 诊断步骤

### 1. 检查数据库中的表

连接到 Oracle 数据库：
```bash
sqlplus system/password@localhost:1521/helowin
```

执行诊断脚本：
```sql
@sql/check-table-stats.sql
```

或手动检查：

```sql
-- 查看所有表
SELECT table_name, num_rows, last_analyzed
FROM all_tables
WHERE owner = 'FINANCE_USER'
AND table_name NOT LIKE 'BIN$%'
ORDER BY table_name;

-- 检查重复
SELECT table_name, COUNT(*) 
FROM all_tables
WHERE owner = 'FINANCE_USER'
GROUP BY table_name
HAVING COUNT(*) > 1;

-- 检查回收站
SELECT object_name, original_name, droptime
FROM dba_recyclebin
WHERE owner = 'FINANCE_USER';
```

### 2. 测试 API 响应

```bash
./test-table-api.sh
```

或手动测试：
```bash
curl http://localhost:5001/api/datasources/oracle-prod/schemas/FINANCE_USER/tables | python3 -m json.tool
```

### 3. 检查应用日志

```bash
docker logs flink-monitor-backend | grep "发现表"
```

## 修复方法

### 方法 1: 清空回收站

```sql
-- 连接为 SYSTEM 用户
sqlplus system/password@helowin

-- 清空回收站
PURGE RECYCLEBIN;

-- 或只清空特定用户的回收站
PURGE DBA_RECYCLEBIN;
```

### 方法 2: 更新表统计信息

```sql
-- 更新所有表的统计信息
BEGIN
    DBMS_STATS.GATHER_SCHEMA_STATS(
        ownname => 'FINANCE_USER',
        estimate_percent => DBMS_STATS.AUTO_SAMPLE_SIZE,
        method_opt => 'FOR ALL COLUMNS SIZE AUTO',
        cascade => TRUE
    );
END;
/

-- 或只更新特定表
BEGIN
    DBMS_STATS.GATHER_TABLE_STATS(
        ownname => 'FINANCE_USER',
        tabname => 'IDS_ACCOUNT_INFO',
        estimate_percent => DBMS_STATS.AUTO_SAMPLE_SIZE
    );
END;
/
```

### 方法 3: 重新构建和部署应用

代码已更新，包含以下改进：
- 使用 `DISTINCT` 确保查询结果唯一
- 排除回收站中的表 (`BIN$%`)
- 排除系统表 (`%$%`)
- 排除临时表
- 添加调试日志
- Schema 名称统一转换为大写

重新部署：
```bash
# 1. 重新构建
mvn clean package -DskipTests

# 2. 重新构建 Docker 镜像
docker-compose build monitor-backend

# 3. 重启服务
docker-compose restart monitor-backend

# 4. 查看日志
docker logs -f flink-monitor-backend
```

## 验证修复

### 1. 检查表列表

访问监控界面：http://localhost:8888

创建新任务 → 选择数据源 → 选择 Schema → 查看表列表

### 2. 验证表数据

```sql
-- 检查实际行数
SELECT COUNT(*) FROM finance_user.ids_account_info;
SELECT COUNT(*) FROM finance_user.ids_trans_info;

-- 检查统计信息
SELECT table_name, num_rows, last_analyzed
FROM all_tables
WHERE owner = 'FINANCE_USER'
AND table_name IN ('IDS_ACCOUNT_INFO', 'IDS_TRANS_INFO');
```

### 3. 测试 API

```bash
./test-table-api.sh
```

应该看到：
- ✓ 没有重复的表名
- 表的行数与实际数据一致

## 预防措施

1. **定期更新统计信息**:
   ```sql
   -- 设置自动统计信息收集
   BEGIN
       DBMS_SCHEDULER.SET_ATTRIBUTE(
           name => 'MAINTENANCE_WINDOW_GROUP',
           attribute => 'ENABLED',
           value => TRUE
       );
   END;
   /
   ```

2. **定期清理回收站**:
   ```sql
   -- 每周清理一次
   PURGE RECYCLEBIN;
   ```

3. **使用规范的表命名**:
   - 避免使用特殊字符
   - 统一使用大写或小写
   - 不要使用 Oracle 保留字

## 常见问题

### Q: 为什么会有 2 个 ACCOUNT_INFO 表？

A: 可能原因：
1. 表被删除后在回收站中
2. 有同名表在不同的 schema（但查询应该只返回指定 schema 的表）
3. 查询逻辑问题（已修复）

### Q: 表的行数为什么不准确？

A: Oracle 的 `num_rows` 是统计信息，不是实时计数：
1. 需要手动或自动更新统计信息
2. 大量 DML 操作后统计信息会过期
3. 使用 `DBMS_STATS.GATHER_TABLE_STATS` 更新

### Q: 如何确保查询结果唯一？

A: 代码已添加多重保护：
1. SQL 使用 `DISTINCT`
2. 过滤系统表和临时表
3. Java 代码使用 `LinkedHashMap` 去重
4. Schema 名称统一大写

## 相关文件

- `sql/check-table-stats.sql` - 诊断脚本
- `test-table-api.sh` - API 测试脚本
- `src/main/java/com/realtime/monitor/service/CdcTaskService.java` - 后端代码
- `monitor/frontend/cdc-task-creator.js` - 前端代码
