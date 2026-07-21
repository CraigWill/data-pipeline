package com.realtime.pipeline;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.runtime.state.FunctionInitializationContext;
import org.apache.flink.runtime.state.FunctionSnapshotContext;
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 JDBC 轮询的增量 CDC Source（不依赖 oblogproxy/liboblog）。
 *
 * 用途：当日志级 CDC 不可用时（例如 OceanBase 企业版 3.2.3.3 缺少匹配的 liboblog），
 *       通过水位列（自增主键或会更新的时间戳列）轮询增量数据。
 *
 * 产出与日志级 CDC 一致的 Debezium 风格 JSON，复用下游处理：
 *   {"op":"c","source":{"table":"T"},"after":{"COL":"val",...}}
 *
 * 局限（轮询天生短板，需知悉）：
 *  - 无法捕获物理 DELETE（需软删除列或触发器+影子表方案）。
 *  - 两次轮询间的多次变更会被合并为最后一次状态。
 *  - 水位列建议用唯一且单调递增的列（如自增主键）；用时间戳列时，边界同值行可能漏读。
 *
 * 位点：每个表的水位值存入 Flink 状态，checkpoint/恢复不丢位点。
 */
public class PollingCdcSource implements SourceFunction<String>, CheckpointedFunction {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(PollingCdcSource.class);

    // ── 配置（可序列化）────────────────────────────────────────
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String schema;
    private final List<String> tables;
    private final String watermarkColumn;
    private final String watermarkType;   // "numeric" | "timestamp"
    private final long pollIntervalMs;
    private final String startValue;       // 可空：数值起点或起始 epoch 毫秒；空则数值从 0、时间戳从当前时间
    private final String opLabel;          // "c" / "u"，仅影响 CSV 中的操作标签
    private final boolean oracleMode;      // true: "SCHEMA"."TABLE"; false: `schema`.`table`
    private final int maxBatch;

    // ── 运行时状态 ────────────────────────────────────────────
    private volatile boolean running = true;
    private transient Connection connection;
    // 表名(大写) -> 该表最后水位值(字符串形式)
    private final Map<String, String> watermarks = new LinkedHashMap<>();
    private transient ListState<String> watermarkState;

    public PollingCdcSource(String jdbcUrl, String username, String password, String schema,
                            List<String> tables, String watermarkColumn, String watermarkType,
                            long pollIntervalMs, String startValue, String opLabel,
                            boolean oracleMode, int maxBatch) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.schema = schema;
        this.tables = tables;
        this.watermarkColumn = watermarkColumn;
        this.watermarkType = watermarkType == null ? "numeric" : watermarkType.toLowerCase();
        this.pollIntervalMs = pollIntervalMs;
        this.startValue = startValue;
        this.opLabel = (opLabel == null || opLabel.isEmpty()) ? "c" : opLabel;
        this.oracleMode = oracleMode;
        this.maxBatch = maxBatch > 0 ? maxBatch : 5000;
    }

    @Override
    public void initializeState(FunctionInitializationContext context) throws Exception {
        watermarkState = context.getOperatorStateStore()
                .getListState(new ListStateDescriptor<>("polling-watermarks", String.class));
        if (context.isRestored()) {
            for (String entry : watermarkState.get()) {
                int idx = entry.indexOf('\u0001');
                if (idx > 0) {
                    watermarks.put(entry.substring(0, idx), entry.substring(idx + 1));
                }
            }
            LOG.info("[polling] 从状态恢复水位: {}", watermarks);
        }
    }

    @Override
    public void snapshotState(FunctionSnapshotContext context) throws Exception {
        watermarkState.clear();
        for (Map.Entry<String, String> e : watermarks.entrySet()) {
            watermarkState.add(e.getKey() + '\u0001' + e.getValue());
        }
    }

    @Override
    public void run(SourceContext<String> ctx) throws Exception {
        // 初始化未设置水位的表
        String defaultWm = "timestamp".equals(watermarkType)
                ? (startValue != null && !startValue.isEmpty() ? startValue : String.valueOf(System.currentTimeMillis()))
                : (startValue != null && !startValue.isEmpty() ? startValue : "0");
        for (String t : tables) {
            String key = t.trim().toUpperCase();
            watermarks.putIfAbsent(key, defaultWm);
        }

        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);
        connection = DriverManager.getConnection(jdbcUrl, props);
        connection.setAutoCommit(true);
        LOG.info("[polling] 连接建立: {} 表={} 水位列={}({}) 间隔={}ms",
                jdbcUrl, tables, watermarkColumn, watermarkType, pollIntervalMs);

        while (running) {
            for (String rawTable : tables) {
                if (!running) break;
                String table = rawTable.trim().toUpperCase();
                try {
                    // 尽量把积压一次性排空：一直查到不足一个批次为止
                    boolean more = true;
                    while (running && more) {
                        int fetched = pollOnce(ctx, table);
                        more = (fetched >= maxBatch);
                    }
                } catch (Exception e) {
                    LOG.error("[polling] 表 {} 轮询失败（下轮重试）: {}", table, e.toString(), e);
                    // 连接可能失效，重建
                    reconnectQuietly();
                }
            }
            sleepInterruptibly(pollIntervalMs);
        }

        closeQuietly();
    }

    /** 拉取一批增量并发送，返回本批行数。 */
    private int pollOnce(SourceContext<String> ctx, String table) throws Exception {
        String qTable = qualify(table);
        String qCol = quoteIdent(watermarkColumn);
        String sql = "SELECT * FROM " + qTable + " WHERE " + qCol + " > ? ORDER BY " + qCol + " ASC";

        int count = 0;
        String lastWm = watermarks.get(table);
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setMaxRows(maxBatch);
            bindWatermark(stmt, 1, lastWm);
            try (ResultSet rs = stmt.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int colCount = md.getColumnCount();
                while (rs.next()) {
                    Map<String, String> after = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        after.put(md.getColumnLabel(i), rs.getString(i));
                    }
                    String json = buildJson(table, after);
                    String newWm = readWatermark(rs);
                    // 发送与水位推进必须在 checkpoint 锁内，保证一致性
                    synchronized (ctx.getCheckpointLock()) {
                        ctx.collect(json);
                        if (newWm != null) {
                            watermarks.put(table, newWm);
                        }
                    }
                    count++;
                }
            }
        }
        if (count > 0) {
            LOG.info("[polling] 表 {} 本批 {} 行, 新水位={}", table, count, watermarks.get(table));
        }
        return count;
    }

    private void bindWatermark(PreparedStatement stmt, int idx, String wm) throws Exception {
        if ("timestamp".equals(watermarkType)) {
            long millis = Long.parseLong(wm);
            stmt.setTimestamp(idx, new Timestamp(millis));
        } else {
            stmt.setBigDecimal(idx, new BigDecimal(wm));
        }
    }

    private String readWatermark(ResultSet rs) throws Exception {
        // Oracle/OB Oracle 结果集列名多为大写；配置里可能是混合大小写
        String col = oracleMode ? watermarkColumn.toUpperCase() : watermarkColumn;
        if ("timestamp".equals(watermarkType)) {
            Timestamp ts = rs.getTimestamp(col);
            return ts != null ? String.valueOf(ts.getTime()) : null;
        } else {
            BigDecimal v = rs.getBigDecimal(col);
            return v != null ? v.toPlainString() : null;
        }
    }

    /** 构造 Debezium 风格 JSON，兼容下游 convertToCSV/extractTableName。 */
    private String buildJson(String table, Map<String, String> after) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("{\"op\":\"").append(opLabel).append("\",")
          .append("\"source\":{\"table\":\"").append(escape(table)).append("\"},")
          .append("\"after\":{");
        boolean first = true;
        for (Map.Entry<String, String> e : after.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":");
            if (e.getValue() == null) {
                sb.append("null");
            } else {
                sb.append('"').append(escape(e.getValue())).append('"');
            }
        }
        sb.append("}}");
        return sb.toString();
    }

    private String qualify(String table) {
        if (oracleMode) {
            return "\"" + schema.toUpperCase() + "\".\"" + table.toUpperCase() + "\"";
        }
        return "`" + schema + "`.`" + table + "`";
    }

    private String quoteIdent(String ident) {
        if (oracleMode) return "\"" + ident.toUpperCase() + "\"";
        return "`" + ident + "`";
    }

    private static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> b.append("\\\\");
                case '"' -> b.append("\\\"");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    private void sleepInterruptibly(long ms) {
        long end = System.currentTimeMillis() + ms;
        while (running && System.currentTimeMillis() < end) {
            try { Thread.sleep(Math.min(500, ms)); } catch (InterruptedException e) { return; }
        }
    }

    private void reconnectQuietly() {
        closeQuietly();
        try {
            Properties props = new Properties();
            props.setProperty("user", username);
            props.setProperty("password", password);
            connection = DriverManager.getConnection(jdbcUrl, props);
            connection.setAutoCommit(true);
        } catch (Exception e) {
            LOG.warn("[polling] 重连失败: {}", e.getMessage());
        }
    }

    private void closeQuietly() {
        if (connection != null) {
            try { connection.close(); } catch (Exception ignore) { }
            connection = null;
        }
    }

    @Override
    public void cancel() {
        running = false;
        closeQuietly();
    }
}
