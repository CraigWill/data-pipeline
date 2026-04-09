package com.realtime.monitor.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 密码加密工具类
 * - BCrypt: 用于用户密码（单向加密）
 * - AES: 用于数据源密码（可逆加密）
 */
public class PasswordEncryptionUtil {

    private static final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private static final String AES_ALGORITHM = "AES";
    
    // 从环境变量读取 AES 密钥（必须设置，不提供默认值）
    private static final String AES_KEY = getRequiredEnvVar("AES_ENCRYPTION_KEY");
    
    /**
     * 获取必需的环境变量，如果未设置则抛出异常
     */
    private static String getRequiredEnvVar(String name) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException(
                "环境变量 " + name + " 未设置！请在 .env 文件中设置此变量。\n" +
                "生成密钥: openssl rand -base64 32"
            );
        }
        return value;
    }

    /**
     * BCrypt 加密（用于用户密码）
     */
    public static String encodeBCrypt(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * BCrypt 验证
     */
    public static boolean matchesBCrypt(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    /**
     * AES 加密（用于数据源密码）
     */
    public static String encryptAES(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        try {
            // Base64 解码密钥（密钥是 Base64 编码的）
            byte[] keyBytes = Base64.getDecoder().decode(AES_KEY);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, AES_ALGORITHM);
            
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("AES 加密失败", e);
        }
    }

    /**
     * AES 解密（用于数据源密码）
     */
    public static String decryptAES(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }
        try {
            // Base64 解码密钥（密钥是 Base64 编码的）
            byte[] keyBytes = Base64.getDecoder().decode(AES_KEY);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, AES_ALGORITHM);
            
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES 解密失败", e);
        }
    }
}
