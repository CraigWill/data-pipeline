package com.realtime.monitor.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.realtime.monitor.dto.DataSourceConfig;
import com.realtime.monitor.util.PasswordEncryptionUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据源管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataSourceService {

    private final com.realtime.monitor.repository.DataSourceRepository dataSourceRepository;

    /**
     * 保存数据源配置（新建）
     */
    public String saveDataSource(DataSourceConfig config) {
        String dsId = config.getId();
        if (dsId == null || dsId.isEmpty()) {
            dsId = "ds-" + System.currentTimeMillis();
            config.setId(dsId);
        }

        // 明文密码 → 加密存储
        if (config.getPassword() != null && !config.getPassword().isEmpty()
                && !PasswordEncryptionUtil.isEncrypted(config.getPassword())) {
            try {
                config.setPassword(PasswordEncryptionUtil.encryptAES(config.getPassword()));
                log.debug("数据源密码已加密: {}", dsId);
            } catch (Exception e) {
                log.error("密码加密失败: {}", e.getMessage());
                throw new RuntimeException("密码加密失败", e);
            }
        }

        config.setStatus("UNTESTED");
        dataSourceRepository.save(config);
        return dsId;
    }

    public void updateDataSourceStatus(String id, String status) {
        dataSourceRepository.updateStatus(id, status);
    }

    /**
     * 加载数据源配置（密码解密后返回）
     */
    public DataSourceConfig loadDataSource(String dsId) {
        DataSourceConfig config = dataSourceRepository.findById(dsId);
        if (config == null) {
            throw new RuntimeException("数据源配置不存在: " + dsId);
        }

        if (config.getPassword() != null && !config.getPassword().isEmpty()) {
            if (PasswordEncryptionUtil.isEncrypted(config.getPassword())) {
                try {
                    config.setPassword(PasswordEncryptionUtil.decryptAES(config.getPassword()));
                    log.debug("数据源密码已解密: {}", dsId);
                } catch (Exception e) {
                    log.warn("数据源 {} 密码解密失败，尝试直接使用原始值: {}", dsId, e.getMessage());
                }
            } else {
                log.debug("数据源 {} 密码为明文，直接使用", dsId);
            }
        }

        return config;
    }

    /**
     * 列出所有数据源配置（不含密码）
     */
    public List<Map<String, Object>> listDataSources() {
        return dataSourceRepository.findAll().stream()
                .map(config -> {
                    Map<String, Object> summary = new HashMap<>();
                    summary.put("id", config.getId());
                    summary.put("name", config.getName() != null ? config.getName() : "Unnamed DataSource");
                    summary.put("type", config.getType() != null ? config.getType() : "ORACLE");
                    summary.put("host", config.getHost() != null ? config.getHost() : "Unknown");
                    summary.put("port", config.getPort());
                    summary.put("sid", config.getSid() != null ? config.getSid() : "Unknown");
                    summary.put("username", config.getUsername());
                    summary.put("description", config.getDescription());
                    summary.put("status", config.getStatus());
                    summary.put("created_at", config.getCreatedAt());
                    summary.put("updated_at", config.getUpdatedAt());
                    // 密码不返回给前端
                    return summary;
                })
                .collect(Collectors.toList());
    }

    /**
     * 删除数据源配置
     */
    public void deleteDataSource(String dsId) {
        dataSourceRepository.deleteById(dsId);
    }

    /**
     * 更新数据源配置
     */
    public void updateDataSource(String dsId, DataSourceConfig config) {
        config.setId(dsId);

        if (config.getPassword() == null || config.getPassword().isEmpty()) {
            // 前端未传密码（编辑时留空）→ 保留数据库中的原密码（已加密）
            DataSourceConfig existing = dataSourceRepository.findById(dsId);
            if (existing != null && existing.getPassword() != null) {
                config.setPassword(existing.getPassword());
                log.debug("数据源密码未修改，保留原密码: {}", dsId);
            }
        } else if (!PasswordEncryptionUtil.isEncrypted(config.getPassword())) {
            // 明文密码（用户修改了密码）→ 加密
            try {
                config.setPassword(PasswordEncryptionUtil.encryptAES(config.getPassword()));
                log.debug("数据源密码已加密（更新）: {}", dsId);
            } catch (Exception e) {
                log.error("密码加密失败: {}", e.getMessage());
                throw new RuntimeException("密码加密失败", e);
            }
        }
        // 已是密文 → 直接保存，不重复加密

        config.setStatus("UNTESTED");
        dataSourceRepository.save(config);
    }

    /**
     * 重新加密所有数据源密码（管理员操作，用于密钥轮换后迁移）
     */
    public Map<String, Object> reencryptAllPasswords() {
        List<DataSourceConfig> dataSources = dataSourceRepository.findAll();
        int total = dataSources.size(), updated = 0, skipped = 0, failed = 0;

        for (DataSourceConfig config : dataSources) {
            String password = config.getPassword();
            if (password == null || password.isEmpty()) { skipped++; continue; }

            try {
                if (PasswordEncryptionUtil.isEncrypted(password)) {
                    // 已是密文，尝试解密验证可用性
                    try {
                        String plain = PasswordEncryptionUtil.decryptAES(password);
                        // 用新格式重新加密（统一为 {AES}: 前缀）
                        String reEncrypted = PasswordEncryptionUtil.encryptAES(plain);
                        if (!reEncrypted.equals(password)) {
                            config.setPassword(reEncrypted);
                            dataSourceRepository.save(config);
                            log.info("数据源 {} 密码已迁移到新格式", config.getId());
                            updated++;
                        } else {
                            skipped++;
                        }
                    } catch (Exception e) {
                        log.warn("数据源 {} 密码解密失败，跳过: {}", config.getId(), e.getMessage());
                        failed++;
                    }
                } else {
                    // 明文密码，直接加密
                    config.setPassword(PasswordEncryptionUtil.encryptAES(password));
                    dataSourceRepository.save(config);
                    log.info("数据源 {} 明文密码已加密", config.getId());
                    updated++;
                }
            } catch (Exception e) {
                log.error("重新加密数据源 {} 密码失败: {}", config.getId(), e.getMessage());
                failed++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("updated", updated);
        result.put("skipped", skipped);
        result.put("failed", failed);
        return result;
    }
}
