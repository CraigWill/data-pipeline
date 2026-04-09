package com.realtime.monitor.controller;

import com.realtime.monitor.dto.ApiResponse;
import com.realtime.monitor.dto.DataSourceConfig;
import com.realtime.monitor.repository.DataSourceRepository;
import com.realtime.monitor.util.PasswordEncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员工具 API
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    
    private final DataSourceRepository dataSourceRepository;
    
    /**
     * 重新加密所有数据源密码
     * 用于修复密码格式问题
     */
    @PostMapping("/datasources/reencrypt")
    public ApiResponse<Map<String, Object>> reencryptDataSourcePasswords() {
        try {
            List<DataSourceConfig> dataSources = dataSourceRepository.findAll();
            int total = dataSources.size();
            int updated = 0;
            int skipped = 0;
            int failed = 0;
            
            for (DataSourceConfig config : dataSources) {
                String password = config.getPassword();
                if (password == null || password.isEmpty()) {
                    skipped++;
                    continue;
                }
                
                try {
                    // 检查密码是否已经加密
                    if (isLikelyEncrypted(password)) {
                        // 尝试解密，如果成功说明已经是正确的加密格式
                        try {
                            PasswordEncryptionUtil.decryptAES(password);
                            log.info("数据源 {} 密码已正确加密，跳过", config.getId());
                            skipped++;
                            continue;
                        } catch (Exception e) {
                            // 解密失败，说明格式不对，需要重新加密
                            log.warn("数据源 {} 密码解密失败，将作为明文重新加密", config.getId());
                        }
                    }
                    
                    // 将密码作为明文重新加密
                    String encryptedPassword = PasswordEncryptionUtil.encryptAES(password);
                    config.setPassword(encryptedPassword);
                    dataSourceRepository.save(config);
                    
                    log.info("数据源 {} 密码已重新加密", config.getId());
                    updated++;
                    
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
            
            return ApiResponse.success(result, 
                String.format("重新加密完成: %d 个已更新, %d 个已跳过, %d 个失败", updated, skipped, failed));
            
        } catch (Exception e) {
            log.error("重新加密数据源密码失败", e);
            return ApiResponse.error("重新加密失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查字符串是否看起来像 Base64 编码的密文
     */
    private boolean isLikelyEncrypted(String password) {
        if (password.length() % 4 != 0) {
            return false;
        }
        return password.matches("^[A-Za-z0-9+/]+=*$");
    }
}
