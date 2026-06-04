# obbinlog-ce CDC 模式 — 基于官方 oceanbase/obbinlog-ce:4.2.5-test 镜像
# 只启动 logproxy（CDC 模式），不启动内置的 OB/OBProxy
# 支持 OceanBase 4.2.5.x Oracle 模式 CDC
FROM oceanbase/obbinlog-ce:4.2.5-test

# 复制自定义 CDC 入口脚本
COPY cdc-entrypoint.sh /cdc-entrypoint.sh
RUN chmod +x /cdc-entrypoint.sh

EXPOSE 2983

ENTRYPOINT ["/cdc-entrypoint.sh"]