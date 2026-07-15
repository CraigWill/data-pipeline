package com.realtime.monitor.service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.realtime.monitor.dto.DataSourceConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 模拟 CDC 事件服务：查询表数据，并对目标表执行批量 INSERT/UPDATE/DELETE，
 * 用于触发数据源的 CDC 变更事件。
 *
 * 安全：表名/列名/Schema 名通过白名单正则校验（仅允许字母数字下划线），
 * 所有数据值通过 PreparedStatement 绑定，避免 SQL 注入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CdcSimulatorService {

    private final DataSourceService dataSourceService;
    private final CdcTaskService cdcTaskService;

    private static final int MAX_BATCH_ROWS = 1000;
    private static final int MAX_PAGE_SIZE = 500;

    // ==================== 公开方法 ====================

    /**
     * 获取表的列结构（列名、类型、是否可空、是否主键）
     */
    public List<Map<String, Object>> getColumns(String dsId, String schema, String table) throws Exception {
        validateIdentifier(schema, "schema");
        validateIdentifier(table, "table");
        DataSourceConfig config = dataSourceService.loadDataSource(dsId);
        String type = dbType(config);
        boolean upper = isOracleLike(type);
        String s = upper ? schema.toUpperCase() : schema;
        String t = upper ? table.toUpperCase() : table;

        String jdbcUrl = cdcTaskService.buildJdbcUrl(config);
        List<Map<String, Object>> columns = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(jdbcUrl, config.getUsername(), config.getPassword())) {
            DatabaseMetaData meta = conn.getMetaData();

            // 主键集合
            java.util.Set<String> pkCols = new java.util.HashSet<>();
            try (ResultSet pk = meta.getPrimaryKeys(null, s, t)) {
                while (pk.next()) {
                    pkCols.add(pk.getString("COLUMN_NAME"));
                }
            } catch (SQLException ignore) {
                // 某些驱动不支持，忽略
            }

            try (ResultSet rs = meta.getColumns(null, s, t, "%")) {
                while (rs.next()) {
                    String colName = rs.getString("COLUMN_NAME");
                    Map<String, Object> col = new LinkedHashMap<>();
                    col.put("name", colName);
                    col.put("typeName", rs.getString("TYPE_NAME"));
                    col.put("dataType", rs.getInt("DATA_TYPE"));
                    col.put("size", rs.getInt("COLUMN_SIZE"));
                    col.put("nullable", rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls);
                    col.put("primaryKey", pkCols.contains(colName));
                    columns.add(col);
                }
            }
        } catch (SQLException e) {
            log.error("获取列结构失败 [{}].{}.{}", dsId, schema, table, e);
            throw new Exception("获取列结构失败，请检查数据库连接");
        }

        if (columns.isEmpty()) {
            throw new Exception("未找到表 " + schema + "." + table + " 的列信息");
        }
        return columns;
    }

    /**
     * 分页查询表数据
     */
    public Map<String, Object> queryData(String dsId, String schema, String table, int page, int size) throws Exception {
        validateIdentifier(schema, "schema");
        validateIdentifier(table, "table");
        if (page < 1) page = 1;
        if (size < 1) size = 50;
        if (size > MAX_PAGE_SIZE) size = MAX_PAGE_SIZE;

        DataSourceConfig config = dataSourceService.loadDataSource(dsId);
        String type = dbType(config);
        String jdbcUrl = cdcTaskService.buildJdbcUrl(config);
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        long total = 0;

        try (Connection conn = DriverManager.getConnection(jdbcUrl, config.getUsername(), config.getPassword())) {
            try (PreparedStatement stmt = prepareCountStar(conn, type, schema, table);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) total = rs.getLong(1);
            }

            try (PreparedStatement stmt = preparePagedSelect(conn, type, schema, table)) {
                bindPagingParams(stmt, type, page, size);
                try (ResultSet rs = stmt.executeQuery()) {
                    ResultSetMetaData md = rs.getMetaData();
                    int colCount = md.getColumnCount();
                    for (int i = 1; i <= colCount; i++) columns.add(md.getColumnLabel(i));
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= colCount; i++) {
                            Object v = rs.getObject(i);
                            row.put(md.getColumnLabel(i), v == null ? null : stringifyValue(v));
                        }
                        rows.add(row);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("查询表数据失败 [{}].{}.{}", dsId, schema, table, e);
            throw new Exception("查询表数据失败：" + e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("columns", columns);
        result.put("rows", rows);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /**
     * 批量 INSERT
     * @param rows 每行是 列名->值 的 Map
     */
    public int batchInsert(String dsId, String schema, String table, List<Map<String, Object>> rows) throws Exception {
        validateIdentifier(schema, "schema");
        validateIdentifier(table, "table");
        if (rows == null || rows.isEmpty()) throw new Exception("插入数据为空");
        if (rows.size() > MAX_BATCH_ROWS) throw new Exception("单次批量操作行数不能超过 " + MAX_BATCH_ROWS);

        // 列名先收集，连接后经元数据解析为可信列名
        List<String> requestedCols = new ArrayList<>(rows.get(0).keySet());
        if (requestedCols.isEmpty()) throw new Exception("插入数据列为空");

        DataSourceConfig config = dataSourceService.loadDataSource(dsId);
        String type = dbType(config);
        String jdbcUrl = cdcTaskService.buildJdbcUrl(config);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, config.getUsername(), config.getPassword())) {
            List<String> cols = resolveTrustedColumns(conn, type, schema, table, requestedCols);

            conn.setAutoCommit(false);
            try (PreparedStatement stmt = prepareInsert(conn, type, schema, table, cols)) {
                for (Map<String, Object> row : rows) {
                    for (int i = 0; i < cols.size(); i++) {
                        bindValue(stmt, i + 1, rowValue(row, cols.get(i)));
                    }
                    stmt.addBatch();
                }
                int[] result = stmt.executeBatch();
                conn.commit();
                return countAffected(result);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            log.error("批量插入失败 [{}].{}.{}", dsId, schema, table, e);
            throw new Exception("批量插入失败：" + e.getMessage());
        }
    }

    /**
     * 自动生成并插入指定数量的模拟数据。
     * 根据列的 JDBC 类型自动造值：数值主键自增（从当前 MAX+1 开始，避免主键冲突），
     * 字符串造 mock 文本，时间列取当前时间。复用 batchInsert 落库并触发 CDC。
     * @param count 生成行数
     */
    public int autoInsert(String dsId, String schema, String table, int count) throws Exception {
        // 验证标识符，防止SQL注入
        validateIdentifier(schema, "schema");
        validateIdentifier(table, "table");
        
        if (count <= 0) throw new Exception("插入数量必须大于 0");
        if (count > MAX_BATCH_ROWS) throw new Exception("单次自动插入不能超过 " + MAX_BATCH_ROWS + " 行");

        List<Map<String, Object>> columns = getColumns(dsId, schema, table);
        DataSourceConfig config = dataSourceService.loadDataSource(dsId);
        String type = dbType(config);

        // 为数值型主键计算自增基准（MAX+1），避免主键冲突
        long pkBase = 1L;
        for (Map<String, Object> col : columns) {
            if (Boolean.TRUE.equals(col.get("primaryKey")) && isNumericType((Integer) col.get("dataType"))) {
                pkBase = getMaxLong(config, type, schema, table, (String) col.get("name")) + 1L;
                break;
            }
        }

        String nowTs = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        List<Map<String, Object>> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (Map<String, Object> col : columns) {
                String name = (String) col.get("name");
                int dataType = col.get("dataType") != null ? (Integer) col.get("dataType") : java.sql.Types.VARCHAR;
                boolean pk = Boolean.TRUE.equals(col.get("primaryKey"));
                int size = col.get("size") != null ? (Integer) col.get("size") : 50;
                row.put(name, generateValue(name, dataType, pk, size, pkBase + i, i, nowTs));
            }
            rows.add(row);
        }
        return batchInsert(dsId, schema, table, rows);
    }

    /** 按列类型生成模拟值 */
    private Object generateValue(String colName, int dataType, boolean pk, int size,
                                 long pkValue, int idx, String nowTs) {
        if (isNumericType(dataType)) {
            if (pk) return pkValue;
            return java.util.concurrent.ThreadLocalRandom.current().nextLong(0, 1_000_000L);
        }
        switch (dataType) {
            case java.sql.Types.FLOAT:
            case java.sql.Types.REAL:
            case java.sql.Types.DOUBLE:
                return Math.round(java.util.concurrent.ThreadLocalRandom.current().nextDouble(0, 100_000) * 100.0) / 100.0;
            case java.sql.Types.DATE:
            case java.sql.Types.TIME:
            case java.sql.Types.TIMESTAMP:
            case java.sql.Types.TIMESTAMP_WITH_TIMEZONE:
                return nowTs;
            case java.sql.Types.BOOLEAN:
            case java.sql.Types.BIT:
                return idx % 2 == 0;
            default: {
                // 字符类：mock_<列名>_<序号>，按列宽截断
                String v = "mock_" + colName.toLowerCase() + "_" + (pkValue);
                int max = size > 0 ? size : 50;
                if (v.length() > max) v = v.substring(0, max);
                return v;
            }
        }
    }

    private boolean isNumericType(Integer dataType) {
        if (dataType == null) return false;
        switch (dataType) {
            case java.sql.Types.TINYINT:
            case java.sql.Types.SMALLINT:
            case java.sql.Types.INTEGER:
            case java.sql.Types.BIGINT:
            case java.sql.Types.NUMERIC:
            case java.sql.Types.DECIMAL:
                return true;
            default:
                return false;
        }
    }

    /** 查询数值列的当前最大值（用于主键自增基准）；失败或空表返回一个安全随机基准。 */
    private long getMaxLong(DataSourceConfig config, String type, String schema, String table, String col) throws Exception {
        String jdbcUrl = cdcTaskService.buildJdbcUrl(config);
        try (Connection conn = DriverManager.getConnection(jdbcUrl, config.getUsername(), config.getPassword())) {
            List<String> trustedCols = resolveTrustedColumns(conn, type, schema, table, List.of(col));
            try (PreparedStatement stmt = prepareMaxColumn(conn, type, schema, table, trustedCols.get(0));
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    java.math.BigDecimal v = rs.getBigDecimal(1);
                    if (v != null) return v.longValue();
                }
                return 0L;
            }
        } catch (SQLException e) {
            log.warn("获取主键最大值失败 [{}].{}.{}，改用随机基准: {}", schema, table, col, e.getMessage());
            return java.util.concurrent.ThreadLocalRandom.current().nextLong(1, 1_000_000L);
        }
    }

    /**
     * 获取表定义（DDL）。
     * Oracle / OceanBase Oracle：优先 DBMS_METADATA.GET_DDL；MySQL/OB MySQL：SHOW CREATE TABLE；
     * 失败则用列元数据拼装近似 DDL。
     */
    public String getTableDdl(String dsId, String schema, String table) throws Exception {
        validateIdentifier(schema, "schema");
        validateIdentifier(table, "table");
        DataSourceConfig config = dataSourceService.loadDataSource(dsId);
        String type = dbType(config);
        String jdbcUrl = cdcTaskService.buildJdbcUrl(config);
        boolean oracle = isOracleLike(type);

        // GET_DDL 的表名/Schema 是绑定值（非 FROM 标识符），仍须先净化
        String safeTable = sanitizeIdentifier(table, "table");
        String safeSchema = sanitizeIdentifier(schema, "schema");
        if (oracle) {
            safeTable = safeTable.toUpperCase();
            safeSchema = safeSchema.toUpperCase();
        }

        try (Connection conn = DriverManager.getConnection(jdbcUrl, config.getUsername(), config.getPassword())) {
            if (oracle) {
                try (PreparedStatement st = conn.prepareStatement("SELECT DBMS_METADATA.GET_DDL('TABLE', ?, ?) FROM DUAL")) {
                    st.setString(1, safeTable);
                    st.setString(2, safeSchema);
                    try (ResultSet rs = st.executeQuery()) {
                        if (rs.next()) {
                            String ddl = rs.getString(1);
                            if (ddl != null && !ddl.trim().isEmpty()) return ddl.trim() + ";\n";
                        }
                    }
                } catch (SQLException e) {
                    log.warn("DBMS_METADATA.GET_DDL 失败，改用列元数据拼装: {}", e.getMessage());
                }
            } else {
                try (PreparedStatement st = prepareShowCreate(conn, type, schema, table);
                     ResultSet rs = st.executeQuery()) {
                    if (rs.next()) {
                        String ddl = rs.getString(2);
                        if (ddl != null && !ddl.trim().isEmpty()) return ddl.trim() + ";\n";
                    }
                } catch (SQLException e) {
                    log.warn("SHOW CREATE TABLE 失败，改用列元数据拼装: {}", e.getMessage());
                }
            }
        } catch (SQLException e) {
            log.error("获取表定义失败 [{}].{}.{}", dsId, schema, table, e);
            throw new Exception("获取表定义失败：" + e.getMessage());
        }

        return buildApproxDdl(type, schema, table, getColumns(dsId, schema, table));
    }

    /** 用列元数据拼一个近似 CREATE TABLE（无默认值/存储子句，仅结构参考）。 */
    private String buildApproxDdl(String type, String schema, String table, List<Map<String, Object>> cols) {
        String safeQualified = qualifiedName(type, schema, table);
        StringBuilder sb = new StringBuilder();
        sb.append("-- 近似结构（由列元数据生成，非数据库原始 DDL）\n");
        sb.append("CREATE TABLE ").append(safeQualified).append(" (\n");
        List<String> pks = new ArrayList<>();
        for (int i = 0; i < cols.size(); i++) {
            Map<String, Object> c = cols.get(i);
            String name = sanitizeIdentifier(String.valueOf(c.get("name")), "column");
            String typeName = String.valueOf(c.get("typeName"));
            int size = c.get("size") != null ? (Integer) c.get("size") : 0;
            boolean nullable = Boolean.TRUE.equals(c.get("nullable"));
            if (Boolean.TRUE.equals(c.get("primaryKey"))) pks.add(name);

            sb.append("  ").append(quoteIdentifier(type, name)).append(" ").append(typeName);
            String up = typeName == null ? "" : typeName.toUpperCase();
            if (size > 0 && (up.contains("CHAR") || up.contains("VARCHAR"))) {
                sb.append("(").append(size).append(")");
            }
            if (!nullable) sb.append(" NOT NULL");
            if (i < cols.size() - 1 || !pks.isEmpty()) sb.append(",");
            sb.append("\n");
        }
        if (!pks.isEmpty()) {
            sb.append("  PRIMARY KEY (");
            for (int i = 0; i < pks.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(quoteIdentifier(type, pks.get(i)));
            }
            sb.append(")\n");
        }
        sb.append(");\n");
        return sb.toString();
    }

    /**
     * 批量 UPDATE，按主键(或指定 keyColumns)匹配
     * @param rows 每行包含待更新的列值
     * @param keyColumns 作为 WHERE 条件的键列
     */
    public int batchUpdate(String dsId, String schema, String table,
                           List<Map<String, Object>> rows, List<String> keyColumns) throws Exception {
        validateIdentifier(schema, "schema");
        validateIdentifier(table, "table");
        if (rows == null || rows.isEmpty()) throw new Exception("更新数据为空");
        if (rows.size() > MAX_BATCH_ROWS) throw new Exception("单次批量操作行数不能超过 " + MAX_BATCH_ROWS);
        if (keyColumns == null || keyColumns.isEmpty()) throw new Exception("未指定主键列，无法更新");

        List<String> requestedKeys = new ArrayList<>(keyColumns);
        List<String> requestedSet = new ArrayList<>();
        for (String c : rows.get(0).keySet()) {
            boolean isKey = false;
            for (String k : requestedKeys) {
                if (k.equalsIgnoreCase(c)) { isKey = true; break; }
            }
            if (!isKey) requestedSet.add(c);
        }
        if (requestedSet.isEmpty()) throw new Exception("没有可更新的非主键列");

        DataSourceConfig config = dataSourceService.loadDataSource(dsId);
        String type = dbType(config);
        String jdbcUrl = cdcTaskService.buildJdbcUrl(config);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, config.getUsername(), config.getPassword())) {
            List<String> safeKeys = resolveTrustedColumns(conn, type, schema, table, requestedKeys);
            List<String> setCols = resolveTrustedColumns(conn, type, schema, table, requestedSet);

            conn.setAutoCommit(false);
            try (PreparedStatement stmt = prepareUpdate(conn, type, schema, table, setCols, safeKeys)) {
                for (Map<String, Object> row : rows) {
                    int idx = 1;
                    for (String c : setCols) bindValue(stmt, idx++, rowValue(row, c));
                    for (String k : safeKeys) bindValue(stmt, idx++, rowValue(row, k));
                    stmt.addBatch();
                }
                int[] result = stmt.executeBatch();
                conn.commit();
                return countAffected(result);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            log.error("批量更新失败 [{}].{}.{}", dsId, schema, table, e);
            throw new Exception("批量更新失败：" + e.getMessage());
        }
    }

    /**
     * 批量 DELETE，按主键(或指定 keyColumns)匹配
     * @param rows 每行包含键列的值
     * @param keyColumns 作为 WHERE 条件的键列
     */
    public int batchDelete(String dsId, String schema, String table,
                           List<Map<String, Object>> rows, List<String> keyColumns) throws Exception {
        validateIdentifier(schema, "schema");
        validateIdentifier(table, "table");
        if (rows == null || rows.isEmpty()) throw new Exception("删除数据为空");
        if (rows.size() > MAX_BATCH_ROWS) throw new Exception("单次批量操作行数不能超过 " + MAX_BATCH_ROWS);
        if (keyColumns == null || keyColumns.isEmpty()) throw new Exception("未指定主键列，无法删除");

        List<String> requestedKeys = new ArrayList<>(keyColumns);

        DataSourceConfig config = dataSourceService.loadDataSource(dsId);
        String type = dbType(config);
        String jdbcUrl = cdcTaskService.buildJdbcUrl(config);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, config.getUsername(), config.getPassword())) {
            List<String> safeKeys = resolveTrustedColumns(conn, type, schema, table, requestedKeys);

            conn.setAutoCommit(false);
            try (PreparedStatement stmt = prepareDelete(conn, type, schema, table, safeKeys)) {
                for (Map<String, Object> row : rows) {
                    int idx = 1;
                    for (String k : safeKeys) bindValue(stmt, idx++, rowValue(row, k));
                    stmt.addBatch();
                }
                int[] result = stmt.executeBatch();
                conn.commit();
                return countAffected(result);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            log.error("批量删除失败 [{}].{}.{}", dsId, schema, table, e);
            throw new Exception("批量删除失败：" + e.getMessage());
        }
    }

    // ==================== 私有辅助 ====================

    private String dbType(DataSourceConfig config) {
        return config.getType() != null ? config.getType().toUpperCase() : "ORACLE";
    }

    private boolean isOracleLike(String type) {
        return "ORACLE".equals(type) || "OCEANBASE_ORACLE".equals(type);
    }

    /** 校验标识符合法性，防止 SQL 注入 */
    private void validateIdentifier(String name, String role) throws Exception {
        try {
            sanitizeIdentifier(name, role);
        } catch (IllegalArgumentException e) {
            throw new Exception(e.getMessage());
        }
    }

    /**
     * 将不可信标识符净化为可信字符串。
     * 仅允许字母数字下划线；按字符白名单重新构造，不返回原始引用。
     */
    private String sanitizeIdentifier(String raw, String role) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("非法的" + role + "名称: null");
        }
        if (raw.length() > 128 || !Character.isLetter(raw.charAt(0))) {
            throw new IllegalArgumentException("非法的" + role + "名称: " + raw);
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '_') {
                sb.append(c);
            } else {
                throw new IllegalArgumentException("非法的" + role + "名称: " + raw);
            }
        }
        return sb.toString();
    }

    /** 对已净化的标识符加引号（入参必须已是 sanitize 结果）。 */
    private String quoteSafeIdent(String type, String safeIdent) {
        switch (type) {
            case "MYSQL":
            case "OCEANBASE":
                return "`" + safeIdent + "`";
            case "ORACLE":
            case "OCEANBASE_ORACLE":
            case "POSTGRES":
            default:
                return "\"" + safeIdent + "\"";
        }
    }

    /**
     * 给标识符加引号：先净化再加引号。
     */
    private String quoteIdentifier(String type, String identifier) {
        return quoteSafeIdent(type, sanitizeIdentifier(identifier, "identifier"));
    }

    /**
     * 从 DatabaseMetaData 构建 schema.table → 已加引号限定名 的目录。
     * 目录的 value 只来自元数据字段的字符拷贝，请求参数仅作 HashMap 查找 key。
     */
    private String resolveTrustedQualifiedName(Connection conn, String type, String schema, String table)
            throws SQLException {
        String wantSchema = sanitizeIdentifier(schema, "schema");
        String wantTable = sanitizeIdentifier(table, "table");
        if (isOracleLike(type)) {
            wantSchema = wantSchema.toUpperCase();
            wantTable = wantTable.toUpperCase();
        }

        Map<String, String> catalog = new LinkedHashMap<>();
        DatabaseMetaData meta = conn.getMetaData();

        switch (type) {
            case "MYSQL":
            case "OCEANBASE":
                loadTableCatalog(meta, type, wantSchema, null, catalog, true);
                break;
            default:
                loadTableCatalog(meta, type, null, wantSchema, catalog, false);
                break;
        }

        String key = (wantSchema + "." + wantTable).toUpperCase();
        String trusted = catalog.get(key);
        if (trusted == null) {
            // 大小写不敏感再找一遍
            for (Map.Entry<String, String> e : catalog.entrySet()) {
                if (e.getKey().equalsIgnoreCase(wantSchema + "." + wantTable)) {
                    trusted = e.getValue();
                    break;
                }
            }
        }
        if (trusted == null) {
            throw new SQLException("表不存在或无权访问: " + wantSchema + "." + wantTable);
        }
        return trusted;
    }

    /**
     * 扫描元数据表列表，把「净化后的 schema.table」映射为加引号限定名。
     * @param useCatalogAsSchema MySQL 系用 TABLE_CAT 作为 schema
     */
    private void loadTableCatalog(DatabaseMetaData meta, String type,
                                  String catalogFilter, String schemaFilter,
                                  Map<String, String> out, boolean useCatalogAsSchema) throws SQLException {
        try (ResultSet rs = meta.getTables(catalogFilter, schemaFilter, "%", new String[]{"TABLE", "VIEW"})) {
            while (rs.next()) {
                String rawSchema = useCatalogAsSchema ? rs.getString("TABLE_CAT") : rs.getString("TABLE_SCHEM");
                if (rawSchema == null || rawSchema.isEmpty()) {
                    rawSchema = useCatalogAsSchema ? rs.getString("TABLE_SCHEM") : rs.getString("TABLE_CAT");
                }
                String rawTable = rs.getString("TABLE_NAME");
                if (rawSchema == null || rawTable == null) {
                    continue;
                }
                String safeSchema;
                String safeTable;
                try {
                    safeSchema = sanitizeIdentifier(rawSchema, "schema");
                    safeTable = sanitizeIdentifier(rawTable, "table");
                } catch (IllegalArgumentException skip) {
                    continue;
                }
                String mapKey = (safeSchema + "." + safeTable).toUpperCase();
                String quoted = quoteSafeIdent(type, safeSchema) + "." + quoteSafeIdent(type, safeTable);
                out.put(mapKey, quoted);
            }
        }
    }

    /**
     * 将请求中的列名解析为元数据中的真实列名（加引号前的安全名）。
     * 请求列名只作查找 key；返回列表元素均来自 getColumns 元数据。
     */
    private List<String> resolveTrustedColumns(Connection conn, String type,
                                               String schema, String table,
                                               List<String> requested) throws SQLException {
        String wantSchema = sanitizeIdentifier(schema, "schema");
        String wantTable = sanitizeIdentifier(table, "table");
        if (isOracleLike(type)) {
            wantSchema = wantSchema.toUpperCase();
            wantTable = wantTable.toUpperCase();
        }

        Map<String, String> colCatalog = new LinkedHashMap<>();
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = isMysqlFamily(type)
                ? meta.getColumns(wantSchema, null, wantTable, "%")
                : meta.getColumns(null, wantSchema, wantTable, "%")) {
            while (rs.next()) {
                String raw = rs.getString("COLUMN_NAME");
                if (raw == null) continue;
                try {
                    String safe = sanitizeIdentifier(raw, "column");
                    colCatalog.put(safe.toUpperCase(), safe);
                } catch (IllegalArgumentException skip) {
                    // ignore odd names
                }
            }
        }

        List<String> trusted = new ArrayList<>(requested.size());
        for (String req : requested) {
            String want = sanitizeIdentifier(req, "column");
            String hit = colCatalog.get(want.toUpperCase());
            if (hit == null) {
                throw new SQLException("列不存在或无权访问: " + want);
            }
            trusted.add(hit);
        }
        return trusted;
    }

    private boolean isMysqlFamily(String type) {
        return "MYSQL".equals(type) || "OCEANBASE".equals(type);
    }

    // ── PreparedStatement 工厂：调用处不再出现「字符串拼接 + prepareStatement」──

    private PreparedStatement prepareCountStar(Connection conn, String type, String schema, String table)
            throws SQLException {
        String q = resolveTrustedQualifiedName(conn, type, schema, table);
        return conn.prepareStatement("SELECT COUNT(*) FROM " + q);
    }

    private PreparedStatement preparePagedSelect(Connection conn, String type, String schema, String table)
            throws SQLException {
        String q = resolveTrustedQualifiedName(conn, type, schema, table);
        return conn.prepareStatement(buildPagedQuery(type, q));
    }

    private PreparedStatement prepareInsert(Connection conn, String type, String schema, String table,
                                            List<String> trustedCols) throws SQLException {
        String q = resolveTrustedQualifiedName(conn, type, schema, table);
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(q).append(" (");
        for (int i = 0; i < trustedCols.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(quoteSafeIdent(type, trustedCols.get(i)));
        }
        sql.append(") VALUES (");
        for (int i = 0; i < trustedCols.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("?");
        }
        sql.append(")");
        return conn.prepareStatement(sql.toString());
    }

    private PreparedStatement prepareMaxColumn(Connection conn, String type, String schema, String table,
                                               String trustedCol) throws SQLException {
        String q = resolveTrustedQualifiedName(conn, type, schema, table);
        return conn.prepareStatement(
                "SELECT MAX(" + quoteSafeIdent(type, trustedCol) + ") FROM " + q);
    }

    private PreparedStatement prepareShowCreate(Connection conn, String type, String schema, String table)
            throws SQLException {
        String q = resolveTrustedQualifiedName(conn, type, schema, table);
        return conn.prepareStatement("SHOW CREATE TABLE " + q);
    }

    private PreparedStatement prepareUpdate(Connection conn, String type, String schema, String table,
                                            List<String> setCols, List<String> keyCols) throws SQLException {
        String q = resolveTrustedQualifiedName(conn, type, schema, table);
        StringBuilder sql = new StringBuilder("UPDATE ").append(q).append(" SET ");
        for (int i = 0; i < setCols.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(quoteSafeIdent(type, setCols.get(i))).append(" = ?");
        }
        sql.append(" WHERE ");
        for (int i = 0; i < keyCols.size(); i++) {
            if (i > 0) sql.append(" AND ");
            sql.append(quoteSafeIdent(type, keyCols.get(i))).append(" = ?");
        }
        return conn.prepareStatement(sql.toString());
    }

    private PreparedStatement prepareDelete(Connection conn, String type, String schema, String table,
                                            List<String> keyCols) throws SQLException {
        String q = resolveTrustedQualifiedName(conn, type, schema, table);
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(q).append(" WHERE ");
        for (int i = 0; i < keyCols.size(); i++) {
            if (i > 0) sql.append(" AND ");
            sql.append(quoteSafeIdent(type, keyCols.get(i))).append(" = ?");
        }
        return conn.prepareStatement(sql.toString());
    }

    /** 按净化后的列名取值（兼容请求里大小写不一致的 key）。 */
    private Object rowValue(Map<String, Object> row, String safeCol) {
        if (row.containsKey(safeCol)) {
            return row.get(safeCol);
        }
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(safeCol)) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * 构造 schema.table 限定名（无连接时的兜底，如拼近似 DDL 文本；不用于执行）。
     */
    private String qualifiedName(String type, String schema, String table) {
        String safeSchema = sanitizeIdentifier(schema, "schema");
        String safeTable = sanitizeIdentifier(table, "table");
        if (isOracleLike(type)) {
            safeSchema = safeSchema.toUpperCase();
            safeTable = safeTable.toUpperCase();
        }
        return quoteSafeIdent(type, safeSchema) + "." + quoteSafeIdent(type, safeTable);
    }

    /**
     * 构造分页查询 SQL。LIMIT/OFFSET/FETCH 的数值均使用 ? 占位符，
     * 不将 page/size/offset 拼接进 SQL 字符串，避免任何字符串拼接方式的注入风险；
     * 实际数值通过 bindPagingParams() 以 PreparedStatement.setInt 绑定。
     */
    private String buildPagedQuery(String type, String qualified) {
        switch (type) {
            case "MYSQL":
            case "OCEANBASE":
            case "POSTGRES":
                return "SELECT * FROM " + qualified + " LIMIT ? OFFSET ?";
            case "ORACLE":
            case "OCEANBASE_ORACLE":
            default:
                // Oracle 12c+ / OceanBase Oracle 模式支持 OFFSET ... FETCH
                return "SELECT * FROM " + qualified + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        }
    }

    /**
     * 绑定分页参数。MySQL/OceanBase/Postgres 的 LIMIT ? OFFSET ? 顺序为 (size, offset)；
     * Oracle/OceanBase Oracle 的 OFFSET ? ROWS FETCH NEXT ? ROWS ONLY 顺序为 (offset, size)。
     */
    private void bindPagingParams(PreparedStatement stmt, String type, int page, int size) throws SQLException {
        int offset = (page - 1) * size;
        switch (type) {
            case "MYSQL":
            case "OCEANBASE":
            case "POSTGRES":
                stmt.setInt(1, size);
                stmt.setInt(2, offset);
                break;
            case "ORACLE":
            case "OCEANBASE_ORACLE":
            default:
                stmt.setInt(1, offset);
                stmt.setInt(2, size);
                break;
        }
    }

    /** 将数据库返回值转为字符串用于前端展示 */
    private String stringifyValue(Object v) {
        if (v == null) return null;
        if (v instanceof byte[]) return "[binary]";
        return v.toString();
    }

    /**
     * 绑定参数到 PreparedStatement。
     * 时间类字符串尝试转 Timestamp（遵循 OceanBase 不传 null 给 setObject 的约束）。
     */
    private void bindValue(PreparedStatement stmt, int index, Object value) throws SQLException {
        if (value == null || (value instanceof String && ((String) value).isEmpty())) {
            stmt.setNull(index, java.sql.Types.VARCHAR);
            return;
        }
        if (value instanceof String) {
            String s = ((String) value).trim();
            Timestamp ts = tryParseTimestamp(s);
            if (ts != null) {
                stmt.setTimestamp(index, ts);
            } else {
                stmt.setString(index, s);
            }
            return;
        }
        if (value instanceof Number) {
            stmt.setObject(index, value);
            return;
        }
        if (value instanceof Boolean) {
            stmt.setBoolean(index, (Boolean) value);
            return;
        }
        stmt.setString(index, value.toString());
    }

    /** 尝试解析多种日期格式，支持Oracle和标准格式 */
    private Timestamp tryParseTimestamp(String s) {
        if (s == null) return null;
        
        s = s.trim().toUpperCase(); // Oracle月份缩写通常是大写

        // 兼容 ISO 8601 的 'T' 分隔符: yyyy-MM-ddTHH:mm:ss[.fff] → 空格分隔
        if (s.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?")) {
            s = s.replace('T', ' ');
        }

        // 1. 标准格式: yyyy-MM-dd HH:mm:ss[.fff]
        //    必须兼容小数秒：Timestamp.toString() 会输出 "2026-06-29 14:30:00.0"，
        //    浏览数据回写更新时若不识别小数秒会被当作字符串绑定，
        //    导致 OceanBase Oracle 模式按 NLS_DATE_FORMAT 隐式转换报 ORA-01843。
        if (s.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}(\\.\\d+)?")) {
            try { return Timestamp.valueOf(s); } catch (Exception ignore) { return null; }
        }
        
        // 2. 标准日期格式: yyyy-MM-dd
        if (s.matches("\\d{4}-\\d{2}-\\d{2}")) {
            try { return Timestamp.valueOf(s + " 00:00:00"); } catch (Exception ignore) { return null; }
        }
        
        // 3. Oracle格式: dd-MMM-yyyy HH:mm:ss[.fff] (如: 29-JUN-2026 14:30:00)
        if (s.matches("\\d{2}-[A-Z]{3}-\\d{4} \\d{2}:\\d{2}:\\d{2}(\\.\\d+)?")) {
            try {
                return parseOracleTimestamp(s);
            } catch (Exception ignore) { return null; }
        }
        
        // 4. Oracle日期格式: dd-MMM-yyyy
        if (s.matches("\\d{2}-[A-Z]{3}-\\d{4}")) {
            try {
                return parseOracleTimestamp(s + " 00:00:00");
            } catch (Exception ignore) { return null; }
        }
        
        // 5. 斜杠格式: yyyy/MM/dd HH:mm:ss[.fff]
        if (s.matches("\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}(\\.\\d+)?")) {
            try { 
                String normalized = s.replace('/', '-');
                return Timestamp.valueOf(normalized); 
            } catch (Exception ignore) { return null; }
        }
        
        // 6. 斜杠日期格式: yyyy/MM/dd
        if (s.matches("\\d{4}/\\d{2}/\\d{2}")) {
            try { 
                String normalized = s.replace('/', '-') + " 00:00:00";
                return Timestamp.valueOf(normalized); 
            } catch (Exception ignore) { return null; }
        }
        
        return null;
    }
    
    /** 解析Oracle格式的时间戳: dd-MMM-yyyy HH:mm:ss */
    private Timestamp parseOracleTimestamp(String s) {
        String[] parts = s.split("[- :]");
        if (parts.length < 6) return null;
        
        int day = Integer.parseInt(parts[0]);
        String monthStr = parts[1].toUpperCase();
        int year = Integer.parseInt(parts[2]);
        int hour = Integer.parseInt(parts[3]);
        int minute = Integer.parseInt(parts[4]);
        int second = (int) Double.parseDouble(parts[5]); // 兼容小数秒 "00.0"
        
        // Oracle月份缩写映射
        java.util.Map<String, Integer> monthMap = java.util.Map.ofEntries(
            java.util.Map.entry("JAN", 1),
            java.util.Map.entry("FEB", 2),
            java.util.Map.entry("MAR", 3),
            java.util.Map.entry("APR", 4),
            java.util.Map.entry("MAY", 5),
            java.util.Map.entry("JUN", 6),
            java.util.Map.entry("JUL", 7),
            java.util.Map.entry("AUG", 8),
            java.util.Map.entry("SEP", 9),
            java.util.Map.entry("OCT", 10),
            java.util.Map.entry("NOV", 11),
            java.util.Map.entry("DEC", 12)
        );
        
        if (!monthMap.containsKey(monthStr)) {
            return null;
        }
        
        int month = monthMap.get(monthStr);
        
        // 创建Calendar实例设置日期时间
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.YEAR, year);
        cal.set(java.util.Calendar.MONTH, month - 1); // Calendar月份是0-based
        cal.set(java.util.Calendar.DAY_OF_MONTH, day);
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour);
        cal.set(java.util.Calendar.MINUTE, minute);
        cal.set(java.util.Calendar.SECOND, second);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        
        return new Timestamp(cal.getTimeInMillis());
    }

    private int countAffected(int[] batchResult) {
        int total = 0;
        for (int r : batchResult) {
            if (r >= 0) total += r;
            else total += 1; // SUCCESS_NO_INFO
        }
        return total;
    }
    
    /**
     * 查询Oracle数据库的日期格式设置
     */
    private String getOracleDateFormat(DataSourceConfig config) {
        String jdbcUrl = cdcTaskService.buildJdbcUrl(config);
        try (Connection conn = DriverManager.getConnection(jdbcUrl, config.getUsername(), config.getPassword())) {
            try (PreparedStatement stmt = conn.prepareStatement("SELECT value FROM nls_session_parameters WHERE parameter = 'NLS_DATE_FORMAT'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString(1);
                    }
                }
            }
            
            // 如果上面查询失败，尝试查询另一个视图
            try (PreparedStatement stmt = conn.prepareStatement("SELECT value FROM nls_database_parameters WHERE parameter = 'NLS_DATE_FORMAT'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString(1);
                    }
                }
            }
        } catch (SQLException e) {
            log.warn("查询Oracle日期格式失败: {}", e.getMessage());
        }
        return null;
    }
}
