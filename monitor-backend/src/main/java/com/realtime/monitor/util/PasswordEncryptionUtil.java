package com.realtime.monitor.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密码加密工具类
 * - BCrypt: 用于用户密码（单向加密）
 * - AES/GCM: 用于数据源密码（可逆加密，带认证标签防篡改）
 */
public class PasswordEncryptionUtil {

    private static final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;   // 96-bit IV (NIST recommended)
    private static final int GCM_TAG_LENGTH = 128;  // 128-bit authentication tag

    // 从环境变量或系统属性读取 AES 密钥（测试环境使用系统属性）
    private static final String AES_KEY = getAESKey();

    /**
     * 获取 AES 密钥，优先从环境变量读取，测试环境可从系统属性读取
     */
    private static String getAESKey() {
        // 优先从环境变量读取（生产环境）
        String envKey = System.getenv("AES_ENCRYPTION_KEY");
        if (envKey != null && !envKey.isEmpty()) {
            return envKey;
        }
        
        // 测试环境从系统属性读取（通过 -Daes.encryption.key=xxx 设置）
        String propKey = System.getProperty("aes.encryption.key");
        if (propKey != null && !propKey.isEmpty()) {
            return propKey;
        }
        
        // 如果都没有设置，抛出异常
        throw new IllegalStateException(
            "AES_ENCRYPTION_KEY 未设置！\n" +
            "生产环境：请在 .env 文件中设置 AES_ENCRYPTION_KEY\n" +
            "测试环境：添加 -Daes.encryption.key=xxx JVM 参数\n" +
            "生成密钥：openssl rand -base64 32"
        );
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
     * AES/GCM 加密（用于数据源密码）
     * 输出格式: Base64(IV || ciphertext+tag)
     */
    public static String encryptAES(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(AES_KEY);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext so we can recover it during decryption
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("AES/GCM 加密失败", e);
        }
    }

    /**
     * AES/GCM 解密（用于数据源密码）
     * 支持旧 ECB 格式的自动降级解密（迁移期间）
     */
    public static String decryptAES(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedText);

            // GCM 密文至少包含 12 字节 IV + 16 字节 tag = 28 字节
            if (combined.length < GCM_IV_LENGTH + 16) {
                // 旧 ECB 格式降级解密（迁移期间兼容）
                return decryptAES_ECB_legacy(encryptedText);
            }

            byte[] keyBytes = Base64.getDecoder().decode(AES_KEY);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);

            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] decrypted = cipher.doFinal(ciphertext);

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES/GCM 解密失败", e);
        }
    }

    /** 旧 ECB 格式降级解密，仅用于迁移期间 */
    private static String decryptAES_ECB_legacy(String encryptedText) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(AES_KEY);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES/ECB 降级解密失败", e);
        }
    }
}
