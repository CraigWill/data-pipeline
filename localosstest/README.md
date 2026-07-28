# 本地裸机 Flink + OSS 联调

与 Kind / docker-compose 无关：在本机 `$FLINK_HOME` 起一套**独立端口**的单点 Flink，checkpoint 与 FileSink 都走 `oss://`。

## 目录

```
localosstest/
├── env.sh.example      # 复制为 env.sh 填 OSS
├── start.sh            # 启插件 + 生成 conf + start-cluster
├── stop.sh
├── submit-smoke.sh     # 提交 OssLocalSmokeJob
├── pom.xml
└── src/.../OssLocalSmokeJob.java
```

运行时产物（gitignore）：`runtime/`（conf、logs、pids、plugins）、`env.sh`、`target/`。

## 快速开始

```bash
cd localosstest
cp env.sh.example env.sh
vim env.sh   # 填 OSS_*、确认 FLINK_HOME

chmod +x start.sh stop.sh submit-smoke.sh
./start.sh          # 默认 REST :18081，避免和 Kind 的 8081 冲突
./submit-smoke.sh   # 提交写 OSS 的冒烟作业
```

打开 http://localhost:18081 ，等几次成功 checkpoint 后，到 OSS 看：

- `${CHECKPOINT_DIR}` 下出现 chk-
- `${OUTPUT_PATH}` 下出现 part 文件（`OnCheckpointRollingPolicy`，无成功 CK 则看不到文件）

停止：

```bash
./stop.sh
```

## 要点

| 项 | 说明 |
|----|------|
| 配置隔离 | `FLINK_CONF_DIR=localosstest/runtime/conf`，不改 `$FLINK_HOME/conf` |
| OSS 插件 | jar + JAXB 放 `runtime/plugins/oss-fs-hadoop`，软链到 `$FLINK_HOME/plugins/oss-fs-hadoop` |
| 密钥写入 | `fs.oss.*` 经 YAML 双引号转义，避免 `$`/`#` 等截断 SK |
| SDK 版本 | 由本机发行版 `flink-oss-fs-hadoop-*.jar` 决定（1.20.0 → SDK 3.13.x 一带） |

## 只生成配置

```bash
./start.sh conf
grep fs.oss runtime/conf/flink-conf.yaml
```
