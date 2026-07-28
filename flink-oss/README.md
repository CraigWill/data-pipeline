# flink-oss：容器内 OSS 连通性探针

独立 fat JAR，可在 Flink JobManager / TaskManager 内执行，读取环境变量与
`FLINK_CONF_DIR`（如 `/tmp/flink-conf-active`）中的 OSS 配置，验证桶访问并打印完整错误。

## 构建

```bash
cd flink-oss
mvn -DskipTests package
# 产物: target/flink-oss-probe.jar
```

或：

```bash
./flink-oss/build-and-copy.sh          # 只构建
./flink-oss/build-and-copy.sh copy     # 构建并拷到 JM（及 TM）
./flink-oss/build-and-copy.sh run      # 构建 + 拷贝 + 在 JM 内执行
```

## 放入 JobManager 并执行

```bash
JM=$(kubectl -n flink get pods -l component=jobmanager -o jsonpath='{.items[0].metadata.name}')
kubectl -n flink cp flink-oss/target/flink-oss-probe.jar \
  "$JM:/opt/flink/usrlib/flink-oss-probe.jar"

kubectl -n flink exec "$JM" -- java -jar /opt/flink/usrlib/flink-oss-probe.jar
```

容器内已有 `OSS_*` / `CHECKPOINT_DIR` / `FLINK_CONF_DIR` 时无需再传参。

## 读取来源（按优先级）

1. 环境变量：`OSS_ENDPOINT`、`OSS_ACCESS_KEY_ID`、`OSS_ACCESS_KEY_SECRET`、`OSS_BUCKET_NAME`、`OSS_PREFIX`
2. `CHECKPOINT_DIR` / `SAVEPOINT_DIR` / `OUTPUT_PATH` 等 `oss://bucket/prefix`
3. Flink conf：`$FLINK_CONF_DIR/flink-conf.yaml`、`/tmp/flink-conf-active/flink-conf.yaml` 中的 `fs.oss.*`

## 探测步骤

1. `doesBucketExist(bucket)`
2. `getBucketInfo(bucket)`
3. `listObjects`
4. `listObjectsV2`（与生产 Flink/Hadoop 路径一致）

失败时打印 `OSSException`/`ClientException` 的 errorCode、requestId 与完整堆栈。
