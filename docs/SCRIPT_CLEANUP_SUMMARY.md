# Shell 脚本清理总结

## 清理时间
2026-02-26 16:45

## 清理原因
项目已完成架构简化，删除了所有未使用的组件（Kafka、Debezium、DataHub、CDC Collector 等），只保留 Flink CDC 3.x + Oracle 的核心功能。许多旧版脚本已过时或功能重复，需要清理。

## 删除的脚本（44个）

### 1. Debezium 相关（6个）
- `debug-debezium.sh` - Debezium 调试脚本
- `register-debezium-connector.sh` - 注册 Debezium 连接器
- `run-debezium-embedded-cdc.sh` - 运行嵌入式 Debezium CDC
- `run-debezium-embedded.sh` - 运行嵌入式 Debezium
- `run-debezium-maven.sh` - Maven 方式运行 Debezium
- `run-debezium-standalone.sh` - 独立运行 Debezium

### 2. Kafka 相关（2个）
- `send-test-data-to-kafka.sh` - 发送测试数据到 Kafka
- `test-kafka-connection.sh` - 测试 Kafka 连接

### 3. 旧版 CDC 实现（10个）
- `run-cdc-app.sh` - 运行旧版 CDC 应用
- `run-jdbc-cdc.sh` - 运行 JDBC CDC
- `run-standalone-cdc.sh` - 运行独立 CDC
- `start-cdc.sh` - 启动 CDC
- `start-simple-cdc.sh` - 启动简单 CDC
- `check-oracle-cdc-jdbc.sh` - 检查 Oracle JDBC CDC
- `check-oracle-cdc-simple.sh` - 检查简单 Oracle CDC
- `execute-oracle-cdc-config.sh` - 执行 Oracle CDC 配置
- `setup-logminer-cdc.sh` - 设置 LogMiner CDC
- `submit-oracle-cdc.sh` - 提交 Oracle CDC

### 4. 旧版 Flink 作业提交脚本（6个）
已被自动提交功能（entrypoint.sh）替代：
- `run-flink-cdc.sh` - 运行 Flink CDC
- `start-flink-cdc-job.sh` - 启动 Flink CDC 作业
- `start-flink-cdc-job-safe.sh` - 安全启动 Flink CDC 作业
- `submit-flink-cdc-job.sh` - 提交 Flink CDC 作业
- `submit-to-flink.sh` - 提交到 Flink
- `switch-to-latest-mode.sh` - 切换到 latest 模式

### 5. 旧版监控脚本（3个）
已被 `production-health-monitor.sh` 替代：
- `start-job-monitor.sh` - 启动作业监控
- `stop-job-monitor.sh` - 停止作业监控
- `monitor-and-recover-jobs.sh` - 监控和恢复作业

### 6. 旧版测试脚本（7个）
功能重复或已过时：
- `test-cdc.sh` - 测试 CDC
- `test-cdc-with-new-data.sh` - 用新数据测试 CDC
- `test-oracle-cdc.sh` - 测试 Oracle CDC
- `test-db-connection.sh` - 测试数据库连接
- `test-oracle-connection.sh` - 测试 Oracle 连接
- `test-flink-ha-failover.sh` - 测试 Flink HA 故障转移
- `test-ha-simple.sh` - 简单 HA 测试

### 7. 旧版诊断脚本（3个）
功能已整合：
- `diagnose-cdc-issue.sh` - 诊断 CDC 问题
- `check-cdc-status.sh` - 检查 CDC 状态
- `check-oracle-cdc-status.sh` - 检查 Oracle CDC 状态

### 8. 旧版 HA 脚本（2个）
功能重复：
- `enable-flink-ha.sh` - 启用 Flink HA
- `fix-ha-job-restart.sh` - 修复 HA 作业重启

### 9. 其他（5个）
- `enable-archivelog-docker.sh` - Docker 中启用 archivelog（功能重复）
- `test-insert-data.sh` - 测试插入数据（功能重复）
- `view-output.sh` - 查看输出（功能重复）
- `auto-submit-jobs.sh` - 自动提交作业（已整合到 entrypoint.sh）

## 保留的有用脚本（18个）

### 1. 部署和启动（3个）
- `deploy-flink-ha.sh` - 部署 Flink HA 集群
- `quick-start-ha.sh` - 快速启动 HA 集群
- `production-deploy.sh` - 生产环境部署

### 2. 监控和健康检查（3个）
- `check-flink-cdc3-status.sh` - 检查 Flink CDC 3.x 状态
- `check-flink-ha-status.sh` - 检查 Flink HA 状态
- `monitor-ha-cluster.sh` - 监控 HA 集群
- `production-health-monitor.sh` - 生产环境健康监控

### 3. 作业管理（2个）
- `restart-flink-cdc-job.sh` - 重启 Flink CDC 作业
- `start-flink-cdc.sh` - 启动 Flink CDC

### 4. 测试脚本（6个）
- `test-18-records.sh` - 测试 18 条记录插入和捕获
- `test-auto-submit.sh` - 测试自动提交功能
- `test-ha-failover.sh` - 测试 HA 故障转移
- `test-ha-failover-complete.sh` - 完整 HA 故障转移测试
- `quick-test-cdc.sh` - 快速测试 CDC
- `insert-test-trans.sh` - 插入测试交易
- `quick-insert-test.sh` - 快速插入测试

### 5. Oracle 配置（2个）
- `check-and-enable-archivelog.sh` - 检查并启用 archivelog
- `check-archivelog-docker.sh` - Docker 中检查 archivelog

### 6. 文档（1个）
- `README.md` - Shell 脚本说明文档

## 清理效果

- 删除前: 62 个文件（61 个脚本 + 1 个 README）
- 删除后: 19 个文件（18 个脚本 + 1 个 README）
- 清理率: 69.4%（删除了 43 个脚本）

## 保留脚本的用途

### 日常运维
- `production-deploy.sh` - 一键部署生产环境
- `production-health-monitor.sh` - 自动健康监控和恢复
- `restart-flink-cdc-job.sh` - 重启作业

### 监控检查
- `check-flink-cdc3-status.sh` - 检查 CDC 状态
- `check-flink-ha-status.sh` - 检查 HA 状态
- `monitor-ha-cluster.sh` - 监控集群

### 测试验证
- `test-18-records.sh` - 数据捕获测试
- `test-auto-submit.sh` - 自动提交测试
- `test-ha-failover.sh` - HA 故障转移测试
- `test-ha-failover-complete.sh` - 完整故障转移测试

### 快速操作
- `quick-start-ha.sh` - 快速启动
- `quick-test-cdc.sh` - 快速测试
- `quick-insert-test.sh` - 快速插入测试数据

## 建议

1. 定期审查脚本使用情况，删除不再需要的脚本
2. 新增脚本时，确保功能不重复
3. 保持脚本命名规范和文档更新
4. 考虑将常用脚本整合到 Makefile 或统一的管理脚本中

## 相关文档

- `shell/README.md` - Shell 脚本使用说明
- `docs/PRODUCTION_HA_GUIDE.md` - 生产环境 HA 指南
- `docs/AUTO_JOB_SUBMISSION.md` - 自动作业提交文档
