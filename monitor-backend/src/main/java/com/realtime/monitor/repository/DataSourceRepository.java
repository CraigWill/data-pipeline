package com.realtime.monitor.repository;

import com.realtime.monitor.dto.DataSourceConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class DataSourceRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String TABLE = "cdc_datasources";

    private final RowMapper<DataSourceConfig> rowMapper = new RowMapper<DataSourceConfig>() {
        @Override
        public DataSourceConfig mapRow(ResultSet rs, int rowNum) throws SQLException {
            DataSourceConfig config = new DataSourceConfig();
            config.setId(rs.getString("id"));
            config.setName(rs.getString("name"));
            config.setHost(rs.getString("host"));
            config.setPort(rs.getInt("port"));
            config.setUsername(rs.getString("username"));
            config.setPassword(rs.getString("password"));
            config.setSid(rs.getString("sid"));
            config.setDescription(rs.getString("description"));
            return config;
        }
    };

    public void save(DataSourceConfig config) {
        String sql = "MERGE INTO " + TABLE + " t " +
                "USING (SELECT ? AS id FROM dual) s " +
                "ON (t.id = s.id) " +
                "WHEN MATCHED THEN " +
                "  UPDATE SET name=?, host=?, port=?, username=?, password=?, sid=?, description=?, updated_at=? " +
                "WHEN NOT MATCHED THEN " +
                "  INSERT (id, name, host, port, username, password, sid, description, created_at, updated_at) " +
                "  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(sql,
                config.getId(),
                config.getName(), config.getHost(), config.getPort(),
                config.getUsername(), config.getPassword(), config.getSid(),
                config.getDescription(), now,
                config.getId(), config.getName(), config.getHost(), config.getPort(),
                config.getUsername(), config.getPassword(), config.getSid(),
                config.getDescription(), now, now);
        
        log.info("保存数据源配置: {}", config.getId());
    }

    public DataSourceConfig findById(String id) {
        String sql = "SELECT * FROM " + TABLE + " WHERE id = ?";
        List<DataSourceConfig> results = jdbcTemplate.query(sql, rowMapper, id);
        return results.isEmpty() ? null : results.get(0);
    }

    public List<DataSourceConfig> findAll() {
        String sql = "SELECT * FROM " + TABLE + " ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public void deleteById(String id) {
        String sql = "DELETE FROM " + TABLE + " WHERE id = ?";
        jdbcTemplate.update(sql, id);
        log.info("删除数据源配置: {}", id);
    }

    public boolean existsById(String id) {
        String sql = "SELECT COUNT(*) FROM " + TABLE + " WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }
}
