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
        
        // 默认状态
        config.setStatus("UNTESTED");

        dataSourceRepository.save(config);
        return dsId;
    }

    public void updateDataSourceStatus(String id, String status) {
        dataSourceRepository.updateStatus(id, status);
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
                    log.debug("数据源 {} 的密码为明文格式，直接使用", dsId);
                }
            } catch (Exception e) {
                // 解密失败 — 可能是 AES 密钥变更（如容器重启后密钥不同）
                // 保留原始密码值，让后续连接尝试去验证
                log.warn("数据源 {} 密码解密失败（AES 密钥可能已变更），尝试直接使用原始值: {}",
                        dsId, e.getMessage());
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
                    summary.put("status", config.getStatus());
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

        // 处理密码：如果前端传来的密码已经是加密格式（用户没改密码），不要二次加密
        if (config.getPassword() != null && !config.getPassword().isEmpty()) {
            if (!isLikelyEncrypted(config.getPassword())) {
                // 明文密码（用户修改了密码），需要加密
                try {
                    config.setPassword(PasswordEncryptionUtil.encryptAES(config.getPassword()));
                    log.debug("数据源密码已加密（更新）: {}", dsId);
                } catch (Exception e) {
                    log.error("密码加密失败: {}", e.getMessage());
                    throw new RuntimeException("密码加密失败", e);
                }
            }
            // 已加密格式 → 直接保存，不二次加密
        }

        config.setStatus("UNTESTED");
        dataSourceRepository.save(config);
    }
}
