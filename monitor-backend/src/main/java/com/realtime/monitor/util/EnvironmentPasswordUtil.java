package com.realtime.monitor.util;

import lombok.extern.slf4j.Slf4j;

/**
 * 环境变量密码工具类
 * 用于处理环境变量中可能加密的密码
 */
@Slf4j
public class EnvironmentPasswordUtil {

    /**
     * 从环境变量获取密码，自动解密（如果已加密）
     * 
     * @param envVarName 环境变量名称
     * @param defaultValue 默认值（如果环境变量不存在）
     * @return 解密后的明文密码
     */
    public static String getPassword(String envVarName, String defaultValue) {
        String password = System.getenv(envVarName);
        
        if (password == null || password.isEmpty()) {
            if (defaultValue == null) {
                log.warn("环境变量 {} 未设置且无默认值", envVarName);
            } else {
                log.debug("环境变量 {} 未设置，使用默认值", envVarName);
            }
            return defaultValue;
        }
        
        log.debug("环境变量 {} 原始值: {}...", envVarName, password.substring(0, Math.min(10, password.length())));
        
        // 尝试解密
        try {
            String decrypted = PasswordEncryptionUtil.decryptAES(password);
            log.info("环境变量 {} 已成功解密", envVarName);
            return decrypted;
        } catch (Exception e) {
            // 解密失败，可能是明文密码
            log.warn("环境变量 {} 解密失败，使用原值（可能是明文）: {}", envVarName, e.getMessage());
            log.debug("解密异常详情:", e);
            return password;
        }
    }
    
    /**
     * 从环境变量获取密码，自动解密（如果已加密）
     * 如果环境变量不存在，抛出异常
     * 
     * @param envVarName 环境变量名称
     * @return 解密后的明文密码
     * @throws IllegalStateException 如果环境变量未设置
     */
    public static String getPasswordRequired(String envVarName) {
        String password = getPassword(envVarName, null);
        if (password == null || password.isEmpty()) {
            throw new IllegalStateException(
                "环境变量 " + envVarName + " 未设置！请在 .env 文件中设置此变量。"
            );
        }
        return password;
    }
    
    /**
     * 从环境变量获取密码，自动解密（如果已加密）
     * 
     * @param envVarName 环境变量名称
     * @return 解密后的明文密码，如果环境变量不存在则返回 null
     */
    public static String getPassword(String envVarName) {
        return getPassword(envVarName, null);
    }
    
    /**
     * 检查密码是否可能是加密的（Base64 格式）
     * 
     * @param password 密码字符串
     * @return true 如果可能是加密密码
     */
    public static boolean isPossiblyEncrypted(String password) {
        if (password == null || password.isEmpty()) {
            return false;
        }
        
        // Base64 字符集：A-Z, a-z, 0-9, +, /, =
        // 加密密码通常以 = 结尾（padding）
        return password.matches("^[A-Za-z0-9+/]+=*$") && password.length() % 4 == 0;
    }
}
