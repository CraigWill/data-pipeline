package com.realtime.monitor.service;

import com.realtime.monitor.dto.DataSourceConfig;
import com.realtime.monitor.util.PasswordEncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据源管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataSourceService {

    private final com.realtime.monitor.repository.DataSourceRepository dataSourceRepository;

    /**
     * 保存数据源配置
     */
    public String saveDataSource(DataSourceConfig config) {
        String dsId = config.getId();
        if (dsId == null || dsId.isEmpty()) {
            dsId = "ds-" + System.currentTimeMillis();
            config.setId(dsId);
        }

        // 加密密码
        if (config.getPassword() != null && !config.getPassword().isEmpty()) {
            try {
                config.setPassword(PasswordEncryptionUtil.encryptAES(config.getPassword()));
                log.debug("数据源密码已加密: {}", dsId);
            } catch (Exception e) {
                log.error("密码加密失败: {}", e.getMessage());
                throw new RuntimeException("密码加密失败", e);
            }
        }

        dataSourceRepository.save(config);
        return dsId;
    }

    /**
     * 加载数据源配置
     */
    public DataSourceConfig loadDataSource(String dsId) {
        DataSourceConfig config = dataSourceRepository.findById(dsId);
        if (config == null) {
            throw new RuntimeException("数据源配置不存在: " + dsId);
        }
        
        // 解密密码
        if (config.getPassword() != null && !config.getPassword().isEmpty()) {
            try {
                // 检查密码是否看起来像 Base64 编码的密文
                if (isLikelyEncrypted(config.getPassword())) {
                    config.setPassword(PasswordEncryptionUtil.decryptAES(config.getPassword()));
                    log.debug("数据源密码已解密: {}", dsId);
                } else {
                    log.warn("数据源 {} 的密码可能是明文，尝试直接使用", dsId);
                    // 如果密码看起来不像加密的，尝试直接使用（可能是旧数据）
                    // 同时记录警告，建议用户重新创建数据源
                }
            } catch (Exception e) {
                log.error("密码解密失败: dsId={}, error={}", dsId, e.getMessage());
                throw new RuntimeException("密码解密失败。请删除并重新创建此数据源。", e);
            }
        }
        
        return config;
    }
    
    /**
     * 检查字符串是否看起来像 Base64 编码的密文
     */
    private boolean isLikelyEncrypted(String password) {
        // Base64 编码的字符串只包含 A-Z, a-z, 0-9, +, /, =
        // 且长度应该是 4 的倍数
        if (password.length() % 4 != 0) {
            return false;
        }
        return password.matches("^[A-Za-z0-9+/]+=*$");
    }

    /**
     * 列出所有数据源配置
     */
    public List<Map<String, Object>> listDataSources() {
        List<DataSourceConfig> dataSources = dataSourceRepository.findAll();

        return dataSources.stream()
                .map(config -> {
                    Map<String, Object> summary = new HashMap<>();
                    summary.put("id", config.getId());
                    summary.put("name", config.getName() != null ? config.getName() : "Unnamed DataSource");
                    summary.put("host", config.getHost() != null ? config.getHost() : "Unknown");
                    summary.put("port", config.getPort());
                    summary.put("sid", config.getSid() != null ? config.getSid() : "Unknown");
                    summary.put("description", config.getDescription());
                    summary.put("created_at", config.getCreatedAt());
                    summary.put("updated_at", config.getUpdatedAt());
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
        saveDataSource(config);
    }
}
