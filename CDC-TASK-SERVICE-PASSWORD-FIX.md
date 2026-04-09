# CdcTaskService 密码处理修复

## 修改时间
2026年3月26日

## 问题描述

之前在 `CdcTaskService` 中的 `testConnection`、`discoverSchemas` 和 `discoverTables` 方法中，错误地尝试解密前端传来的密码。

实际上：
- 前端传来的密码是**明文**（用户输入）
- 这些方法用于测试连接和发现数据库结构
- 不需要解密，应该直接使用明文密码

## 修改内容

### 1. testConnection 方法

**修改前**:
```java
// 解密密码（如果已加密）
String password = config.getPassword();
try {
    // 尝试解密，如果解密失败则使用原密码（可能是明文）
    password = PasswordEncryptionUtil.decryptAES(password);
    log.debug("密码已解密用于测试连接");
} catch (Exception e) {
    log.debug("密码解密失败，使用原密码: {}", e.getMessage());
}
```

**修改后**:
```java
// 前端传来的是明文密码，直接使用
String password = config.getPassword();
log.debug("使用明文密码测试连接");
```

### 2. discoverSchemas 方法

**修改前**:
```java
// 解密密码（如果已加密）
String password = config.getPassword();
try {
    password = PasswordEncryptionUtil.decryptAES(password);
    log.debug("密码已解密用于发现 Schema");
} catch (Exception e) {
    log.debug("密码解密失败，使用原密码: {}", e.getMessage());
}
```

**修改后**:
```java
// 前端传来的是明文密码，直接使用
String password = config.getPassword();
log.debug("使用明文密码发现 Schema");
```

### 3. discoverTables 方法

**修改前**:
```java
// 解密密码（如果已加密）
String password = config.getPassword();
try {
    password = PasswordEncryptionUtil.decryptAES(password);
    log.debug("密码已解密用于发现表");
} catch (Exception e) {
    log.debug("密码解密失败，使用原密码: {}", e.getMessage());
}
```

**修改后**:
```java
// 前端传来的是明文密码，直接使用
String password = config.getPassword();
log.debug("使用明文密码发现表");
```

### 4. 移除不必要的导入

移除了 `PasswordEncryptionUtil` 导入，因为这些方法不再需要加密/解密功能。

## 密码处理逻辑说明

### CdcTaskService（本次修改）
- **用途**: 测试连接、发现 Schema/表
- **输入**: 前端传来的明文密码
- **处理**: 直接使用明文密码连接数据库
- **不需要加密/解密**

### DataSourceService（保持不变）
- **用途**: 保存和加载数据源配置
- **保存时**: 使用 `PasswordEncryptionUtil.encryptAES()` 加密密码
- **加载时**: 使用 `PasswordEncryptionUtil.decryptAES()` 解密密码
- **需要加密/解密**

## 工作流程

### 测试连接流程
```
前端输入明文密码
    ↓
CdcTaskService.testConnection()
    ↓
直接使用明文密码
    ↓
连接数据库测试
```

### 保存数据源流程
```
前端输入明文密码
    ↓
DataSourceService.saveDataSource()
    ↓
使用 AES 加密密码
    ↓
保存到配置文件（密文）
```

### 加载数据源流程
```
从配置文件读取（密文）
    ↓
DataSourceService.loadDataSource()
    ↓
使用 AES 解密密码
    ↓
返回明文密码（内存中）
```

## 为什么这样设计？

### 1. 测试连接不需要加密
- 用户在前端输入密码测试连接
- 密码是临时的，不会保存
- 直接使用明文连接数据库即可

### 2. 保存数据源需要加密
- 密码会保存到配置文件
- 配置文件可能被泄露
- 必须加密存储以提高安全性

### 3. 加载数据源需要解密
- 从配置文件读取的是密文
- 需要解密才能连接数据库
- 解密后的明文只在内存中使用

## 安全考虑

### 内存中的明文密码
- 测试连接时，明文密码只在内存中短暂存在
- 连接完成后，密码对象会被垃圾回收
- 不会写入日志或持久化存储

### 配置文件中的密文密码
- 数据源配置保存时自动加密
- 即使配置文件泄露，密码也是密文
- 需要 AES_ENCRYPTION_KEY 才能解密

### 传输中的密码
- 前端到后端：建议使用 HTTPS
- 后端到数据库：使用 JDBC 加密连接（可选）

## 测试验证

### 1. 测试连接功能
```bash
# 前端操作：输入数据库信息和明文密码，点击"测试连接"
# 预期结果：连接成功或失败（根据密码是否正确）
```

### 2. 发现 Schema 功能
```bash
# 前端操作：输入数据库信息和明文密码，点击"发现 Schema"
# 预期结果：返回 Schema 列表
```

### 3. 发现表功能
```bash
# 前端操作：选择 Schema，点击"发现表"
# 预期结果：返回表列表
```

### 4. 保存数据源功能
```bash
# 前端操作：输入数据库信息和明文密码，点击"保存"
# 预期结果：密码被加密后保存到配置文件
# 验证：查看配置文件，密码应该是 Base64 编码的密文
```

## 相关文件

- `monitor-backend/src/main/java/com/realtime/monitor/service/CdcTaskService.java` - 本次修改
- `monitor-backend/src/main/java/com/realtime/monitor/service/DataSourceService.java` - 密码加密/解密
- `monitor-backend/src/main/java/com/realtime/monitor/util/PasswordEncryptionUtil.java` - 加密工具类

## 总结

修改后的逻辑更加清晰：
- ✅ `CdcTaskService` 直接使用明文密码（测试连接、发现数据库结构）
- ✅ `DataSourceService` 负责密码加密/解密（保存/加载配置）
- ✅ 职责分离，逻辑清晰
- ✅ 安全性得到保障（配置文件中的密码是加密的）

修改已完成，代码编译通过，可以进行测试。
