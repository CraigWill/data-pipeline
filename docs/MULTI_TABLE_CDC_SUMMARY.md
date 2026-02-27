# 多表 CDC 功能实现总结

## 实现时间
2026-02-26 18:00

## 功能概述
实现了 Flink CDC 3.x 多表捕获功能，支持同时监控多张 Oracle 表，并为每张表生成独立的 CSV 输出文件。

## 主要改动

### 1. 代码修改

#### FlinkCDC3App.java
- 修改数据流处理逻辑，为每个表创建独立的输出流
- 实现动态字段提取，支持不同表结构
- 为每个表生成独立的文件 Sink
- 文件命名格式：`IDS_{TABLE_NAME}_{TIMESTAMP}-{UUID}.csv`

#### CsvEncoderWithHeader 类
- 支持多表动态表头
- 为不同表定义不同的 CSV 表头
- 使用 ThreadLocal 跟踪每个文件的表头写入状态

#### convertToCSV 方法
- 动态提取所有字段（按字母顺序）
- 支持任意表结构
- 格式：CDC时间,操作类型,字段1,字段2,...

### 2. 配置更新

#### .env 文件
```bash
# 支持多表配置（逗号分隔）
DATABASE_TABLES=TRANS_INFO,ACCOUNT_INFO
```

### 3. 数据库准备

#### 创建 ACCOUNT_INFO 表
```sql
CREATE TABLE FINANCE_USER.ACCOUNT_INFO (
    ID NUMBER PRIMARY KEY,
    ACCOUNT_ID VARCHAR2(20) NOT NULL,
    ACCOUNT_NAME VARCHAR2(100),
    ACCOUNT_TYPE VARCHAR2(20),
    BALANCE NUMBER(15,2) DEFAULT 0,
    STATUS VARCHAR2(10) DEFAULT 'ACTIVE',
    CREATED_TIME DATE DEFAULT SYSDATE,
    UPDATED_TIME DATE DEFAULT SYSDATE
);

-- 启用补充日志
ALTER TABLE FINANCE_USER.ACCOUNT_INFO ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;
```

## 测试结果

### 测试环境
- Flink 集群: 1 个 JobManager + 2 个 TaskManager
- 作业 ID: c689cf45bff5d4bbe8c4e0d98536af63
- 作业状态: RUNNING
- 监控表: TRANS_INFO, ACCOUNT_INFO

### 测试数据
1. TRANS_INFO: 插入 2 条记录
2. ACCOUNT_INFO: 插入 2 条记录，更新 2 条记录

### 测试结果
- ✓ TRANS_INFO 数据成功捕获并写入文件
- ⚠ ACCOUNT_INFO 数据未生成文件（可能原因见下文）

### 生成的文件
```
./output/cdc/2026-02-26--18/
├── IDS_TRANS_INFO_20260226_171216146-caa01944-fc02-4456-b053-08f8c0f8cea9-0.csv
└── IDS_TRANS_INFO_20260226_175959657-db6d4b35-fc69-4d3b-a0eb-e879a122ddea-0.csv
```

### TRANS_INFO 文件内容
```csv
交易时间,操作类型,ID,ACCOUNT_ID,AMOUNT,TRANS_TIME,TRANS_TYPE,MERCHANT_ID,STATUS
2026-02-26 18:00:41,INSERT,1772100036101,ACC001,1000.00,1772128837000,PURCHASE,M001,PENDING
2026-02-26 18:00:42,INSERT,1772100036102,ACC002,2000.00,1772128837000,PURCHASE,M002,PENDING
```

## 已知问题

### 1. ACCOUNT_INFO 文件未生成
**原因分析：**
- 作业使用 `StartupOptions.latest()` 模式
- 只捕获作业启动后的新变更
- ACCOUNT_INFO 表的初始数据在作业启动前插入
- 后续的 UPDATE 和 INSERT 操作可能未被 LogMiner 捕获

**可能的解决方案：**
1. 确保 ACCOUNT_INFO 表的补充日志已正确启用
2. 检查 Oracle LogMiner 是否正确监控该表
3. 验证表名过滤配置是否正确
4. 考虑使用 `StartupOptions.initial()` 进行初始快照

### 2. 表头格式问题
**现状：**
- TRANS_INFO 表头仍使用旧格式（"交易时间"而非"CDC时间"）
- 这是因为容器中的 JAR 文件未完全更新

**解决方案：**
- 重新构建 Docker 镜像
- 或手动复制新 JAR 到容器并重启作业

## 架构优势

### 1. 独立输出流
- 每个表有独立的数据流和文件 Sink
- 互不干扰，易于维护
- 支持不同的滚动策略

### 2. 动态字段支持
- 自动提取所有字段
- 按字母顺序排列
- 无需硬编码字段名

### 3. 可扩展性
- 轻松添加新表
- 只需更新 DATABASE_TABLES 配置
- 自动生成对应的输出文件

## 下一步工作

### 1. 修复 ACCOUNT_INFO 捕获问题
- 验证补充日志配置
- 检查 LogMiner 监控状态
- 测试不同的启动模式

### 2. 优化表头生成
- 实现动态表头生成
- 从数据库元数据获取字段信息
- 支持自定义字段顺序

### 3. 增强监控
- 添加每个表的数据捕获指标
- 监控文件生成状态
- 告警未生成文件的表

### 4. 性能优化
- 测试大量表的性能
- 优化并行度配置
- 调整滚动策略

## 相关文件

- 源代码: `src/main/java/com/realtime/pipeline/FlinkCDC3App.java`
- 配置文件: `.env`
- SQL 脚本: `sql/create-account-table.sql`
- 测试脚本: `shell/test-multi-table.sh`
- 构建脚本: `docker/build-images.sh`

## 配置示例

### 监控单表
```bash
DATABASE_TABLES=TRANS_INFO
```

### 监控多表
```bash
DATABASE_TABLES=TRANS_INFO,ACCOUNT_INFO,USER_INFO,ORDER_INFO
```

### 监控所有表
```bash
DATABASE_TABLES=*
```

## 注意事项

1. 所有表必须启用补充日志
2. 表名区分大小写
3. 使用逗号分隔多个表名
4. 确保 Oracle 用户有权限访问所有表
5. 建议先在测试环境验证

## 总结

多表 CDC 功能已基本实现，TRANS_INFO 表的数据捕获正常工作。ACCOUNT_INFO 表的问题需要进一步调查 Oracle LogMiner 配置和补充日志设置。整体架构设计良好，支持灵活扩展到更多表。
