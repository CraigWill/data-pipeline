# 数据源密码解密失败修复指南

## 问题描述

获取 Schema 列表时出现错误：`密码解密失败: java.lang.RuntimeException: AES 解密失败`

这是因为数据库中存储的数据源密码格式不正确，无法使用当前的 AES 密钥解密。

## 原因分析

可能的原因：
1. 数据源是在密码加密功能实现之前创建的（密码是明文）
2. 数据源密码使用了不同的 AES 密钥加密
3. 数据源密码格式损坏

## 解决方案

### 方案 1: 删除并重新创建数据源（推荐）

这是最简单、最安全的方法。

1. 登录前端界面
2. 进入"数据源管理"页面
3. 删除现有的数据源（例如：`oracle-prod`）
4. 点击"创建数据源"按钮
5. 填写数据源信息：
   - 名称：生产Oracle
   - 主机：host.docker.internal
   - 端口：1521
   - SID：helowin
   - 用户名：finance_user
   - 密码：（输入明文密码）
6. 点击"保存"

新创建的数据源密码会自动使用当前的 AES 密钥加密。

### 方案 2: 手动更新数据库中的密码

如果您知道数据源的明文密码，可以手动加密并更新数据库。

1. 使用加密脚本加密密码：
```bash
./encrypt-password.sh encrypt <明文密码>
```

2. 连接到数据库：
```bash
docker exec -it flink-monitor-backend sh
```

3. 在容器中连接到 Oracle：
```bash
sqlplus monitor/$(echo $DATABASE_PASSWORD | base64 -d)@//oracle-prod:1521/ORCLPDB1
```

4. 更新数据源密码：
```sql
UPDATE cdc_datasources 
SET password = '<加密后的密码>', 
    updated_at = SYSTIMESTAMP 
WHERE id = 'oracle-prod';

COMMIT;

EXIT;
```

### 方案 3: 清空数据源表并重新开始

如果您不介意删除所有数据源配置：

```bash
docker exec -it flink-monitor-backend sh -c "
echo 'DELETE FROM cdc_datasources; COMMIT;' | \
sqlplus -s monitor/\$(echo \$DATABASE_PASSWORD | base64 -d)@//oracle-prod:1521/ORCLPDB1
"
```

然后通过前端界面重新创建所有数据源。

## 验证修复

修复后，尝试以下操作验证：

1. 登录前端界面
2. 进入"创建 CDC 任务"页面
3. 选择数据源
4. 选择 Schema - 应该能成功加载 Schema 列表
5. 选择表 - 应该能成功加载表列表

## 预防措施

为避免将来出现类似问题：

1. 始终通过前端界面创建和管理数据源
2. 不要手动修改数据库中的密码字段
3. 如果更改了 AES_ENCRYPTION_KEY 环境变量，需要重新创建所有数据源
4. 定期备份数据源配置

## 技术细节

### 密码加密流程

1. 用户在前端输入明文密码
2. 前端将明文密码发送到后端（通过 HTTPS）
3. 后端使用 AES 算法加密密码：
   - 密钥：从环境变量 `AES_ENCRYPTION_KEY` 读取（Base64 编码）
   - 算法：AES/ECB/PKCS5Padding
   - 输出：Base64 编码的密文
4. 加密后的密码存储在数据库中

### 密码解密流程

1. 后端从数据库读取加密的密码
2. 检查密码格式（是否为 Base64）
3. 使用相同的 AES 密钥解密
4. 使用明文密码连接数据库

### 当前 AES 密钥

```bash
# 查看当前使用的 AES 密钥
docker exec flink-monitor-backend env | grep AES_ENCRYPTION_KEY
```

输出示例：
```
AES_ENCRYPTION_KEY=E5TDKE7NvyphzdaW0SIUfAhZl2v9sRnjHl1Egqx9arM=
```

## 常见问题

### Q: 为什么不能自动修复密码？

A: 因为我们无法知道原始的明文密码。AES 是对称加密，需要正确的密钥才能解密。如果密钥不匹配或密码格式不正确，就无法恢复明文密码。

### Q: 删除数据源会影响正在运行的 CDC 任务吗？

A: 不会。正在运行的 Flink 作业已经有了数据源连接信息的副本。但是，如果作业重启，可能会因为找不到数据源配置而失败。建议在删除数据源前先停止相关的 CDC 任务。

### Q: 可以使用相同的 ID 重新创建数据源吗？

A: 可以。删除数据源后，您可以使用相同的 ID 重新创建。但通常建议让系统自动生成新的 ID。

## 相关文档

- [密码加密指南](ENV-PASSWORD-ENCRYPTION-GUIDE.md)
- [环境变量快速参考](ENV-QUICK-REFERENCE.md)
- [密钥管理指南](KEY-MANAGEMENT-GUIDE.md)
