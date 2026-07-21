# CDC 采集方式：日志级 vs 轮询（双模式，开关控制）

本管道支持两种 CDC 采集方式，通过 `sourceMode` 开关切换：

| 模式 | 值 | 原理 | 适用 |
|------|----|------|------|
| 日志级（默认） | `log` | 经 oblogproxy/obbinlog 读 OB 日志（liboblog/obcdc） | OB 版本与 logproxy/obcdc 匹配的场景（如 OB 4.2.5.x） |
| 轮询 | `polling` | 纯 JDBC，按水位列增量拉取 | 缺少匹配 liboblog 的场景（如 OB **企业版 3.2.3.3**），或不想部署 logproxy |

> 背景：obbinlog-ce 是 4.x 架构的 CDC 工具，无法驱动 OB 3.x（如 3.2.3.3），会在版本探测处报 `ObClusterVersion get_version fail`。轮询模式纯 JDBC，与 OB 版本/版别无关。

---

## 一、日志级模式（默认，行为不变）

不传 `sourceMode` 或传 `log` 即可，参数与原来一致（`--logProxyHost/--logProxyPort/--tenantName/--rsList` 等）。CDC 的 sys 用户由 **obbinlog 容器的 `OB_SYS_USERNAME`** 决定（不是作业参数）。

---

## 二、轮询模式

### 作业参数（CdcJobMain）

| 参数 | 说明 | 默认 |
|------|------|------|
| `--sourceMode polling` | 启用轮询 | `log` |
| `--pollWatermarkColumn` | 水位列，**建议唯一且单调递增**（自增主键最稳） | `ID` |
| `--pollWatermarkType` | `numeric`（数值列）或 `timestamp`（时间戳列） | `numeric` |
| `--pollIntervalMs` | 轮询间隔（毫秒） | `5000` |
| `--pollStartValue` | 起点：数值起点，或时间戳的起始 epoch 毫秒。空则 **numeric 从 `0`（会回填存量）**、**timestamp 从当前时间（只采之后）** | 空 |
| `--pollOp` | CSV 中的操作标签（`c`/`u`） | `c` |
| `--pollMaxBatch` | 单批最大行数 | `5000` |

### 直接提交（Flink REST）示例

```json
{
  "entryClass": "com.realtime.pipeline.CdcJobMain",
  "parallelism": 1,
  "programArgsList": [
    "--hostname","<observer_ip>","--port","2881",
    "--username","<user@tenant>","--password","<pwd>",
    "--database","<db>","--schema","<SCHEMA>","--tables","T1,T2",
    "--dbType","OCEANBASE_ORACLE",
    "--sourceMode","polling",
    "--pollWatermarkColumn","ID","--pollWatermarkType","numeric",
    "--pollIntervalMs","5000",
    "--outputPath","/opt/flink/output/cdc",
    "--parallelism","1","--jobName","my-polling-job",
    "--checkpointDir","file:///opt/flink/checkpoints","--savepointDir","file:///opt/flink/savepoints"
  ]
}
```

### 经后端提交（任务配置字段）

`TaskConfig` / `CdcSubmitRequest` 新增字段（驼峰或下划线均可）：

```json
{
  "schema": "CDC_ADMIN",
  "tables": ["CDC_TEST"],
  "sourceMode": "polling",
  "pollWatermarkColumn": "ID",
  "pollWatermarkType": "numeric",
  "pollIntervalMs": 5000,
  "pollStartValue": null,
  "pollOp": "c",
  "pollMaxBatch": 5000
}
```

后端 `EmbeddedCdcService` 会在 `sourceMode=polling` 时把这些参数拼进作业的 programArgs。

---

## 三、位点与容错

- 每个表的水位值存入 Flink 状态，**checkpoint/恢复不丢位点**（作业级参数默认 10s checkpoint）。
- 数值水位（自增主键）用 `>` 比较，严格不重不漏。

## 四、轮询的固有局限（务必知悉）

- **抓不到物理 DELETE**：需要软删除标志列（`is_deleted`），或在库侧加**触发器写影子表**再对影子表轮询。
- **中间状态合并**：两次轮询间对同一行的多次变更，只会看到最后一次状态。
- **水位列选型**：
  - 首选唯一单调递增列（自增主键）→ 不重不漏。
  - 用时间戳列（`timestamp` 类型）时，**边界同值行可能漏读**（同一时间戳有多行且跨轮询插入）；且要求业务在更新时刷新该时间戳。
- **DB 负载**：轮询有查询压力，水位列务必**建索引**。
- **实时性**：取决于 `pollIntervalMs`（准实时，非日志级的秒级）。

## 五、模式选择建议

- OB 4.x 且已部署匹配的 obbinlog → 用 `log`（默认）。
- OB 企业版 3.2.3.3（或任何缺匹配 liboblog 的库）→ 用 `polling`；若必须抓 DELETE/严格实时，则改用企业版 OMS。

---

## 验证记录（本机 OB 企业版 4.2.5.7）

- 初始回填（`ID>0`）：捞出存量行，CSV 格式与日志级一致。
- 增量捕获：插入 `ID` 大于当前水位的行，数秒内被捕获写出。
- 水位正确性：插入 `ID` 小于当前水位的行被正确跳过。
- checkpoint 状态保存/恢复水位正常。

> 注：3.2.3.3 环境请自行按选定的水位列再实测一次（尤其确认是否需要抓 DELETE）。
