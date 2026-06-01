# 最小化单点部署方案（裸机/进程模式）

## 概述

本方案以**最小资源、单点、无容器**方式直接在主机上运行实时数据管道系统。所有组件作为独立进程运行，适用于：
- 开发/测试环境
- 资源受限的单台服务器（4C8G 即可）
- 不使用 Docker 的环境
- 快速验证功能

**与 HA 方案的区别：**
- ❌ 无 Docker / Docker Compose
- ❌ 无 ZooKeeper（去除 HA 协调器）
- ❌ 无 JobManager Standby
- ❌ 单 TaskManager
- ✅ 更少资源占用（约 2.5GB 内存）
- ✅ 启动更快（无 leader 选举、无容器启动开销）
- ✅ 无 ZK 端口漂移问题
- ✅ 调试方便（直接查看进程日志）

## 前置要求

| 依赖 | 版本 | 说明 |
|------|------|------|
| Java | 11+ | Flink 和 Spring Boot 运行时 |
| Maven | 3.6+ | 构建项目 |
| Node.js | 18+ | 构建前端（可选） |
| Flink | 1.20.x | 流处理引擎 |
| Oracle | 11g+ | CDC 数据源（已有） |
| Nginx | 任意 | 前端静态文件服务（可选，可用 Python http.server 替代） |

## 目录结构

```
minimal/
├── README.md          # 本文档
├── start.sh           # 一键启动脚本
├── stop.sh            # 一键停止脚本
├── env.sh             # 环境变量配置
└── flink-conf.yaml    # Flink 单点配置（无 HA）
```

## 架构

```
┌─────────────────────────────────────────────────┐
│                  单台主机                         │
├─────────────────────────────────────────────────┤
│                                                 │
│  [进程1] Flink JobManager  (端口 8081, 6123)    │
│  [进程2] Flink TaskManager (2 slots)            │
│  [进程3] Monitor Backend   (端口 5001)          │
│  [进程4] Nginx / 静态文件   (端口 8888)          │
│                                                 │
│  [本地文件系统]                                   │
│  ├── output/cdc/          CDC 输出              │
│  ├── checkpoints/         Flink Checkpoint      │
│  └── savepoints/          Flink Savepoint       │
│                                                 │
└─────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────┐
│  Oracle 数据库   │  (本机或远程)
└─────────────────┘
```

## 快速启动

```bash
# 1. 配置环境变量
cp env.sh.example env.sh
vim env.sh

# 2. 构建项目
./start.sh build

# 3. 启动所有服务
./start.sh

# 4. 验证
curl http://localhost:8081/overview
curl http://localhost:5001/actuator/health
open http://localhost:8888
```

## 停止服务

```bash
./stop.sh
```

## 访问地址

| 服务 | 地址 |
|------|------|
| 前端界面 | http://localhost:8888 |
| 后端 API | http://localhost:5001 |
| Flink Web UI | http://localhost:8081 |

## 程序改动评估

**结论：零代码改动。** 所有差异通过环境变量和 Flink 配置文件控制。

详见 `start.sh` 中的环境变量设置。
