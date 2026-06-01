package com.realtime.monitor.util;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

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
     * 输出格式: {AES}:Base64(IV || ciphertext+tag)
     * 前缀 {AES}: 用于明确标识密文，避免与明文混淆
     */
    public static String encryptAES(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        // 已经是密文，不重复加密
        if (plainText.startsWith("{AES}:")) {
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

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return "{AES}:" + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("AES/GCM 加密失败", e);
        }
    }

    /**
     * AES/GCM 解密（用于数据源密码）
     * 支持三种格式：
     * 1. {AES}:Base64(...)  — 新格式（推荐）
     * 2. Base64(IV+ciphertext) — 旧 GCM 格式（无前缀，迁移兼容）
     * 3. Base64(ECB ciphertext) — 最旧 ECB 格式（迁移兼容）
     */
    public static String decryptAES(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }
        // 新格式：{AES}: 前缀
        if (encryptedText.startsWith("{AES}:")) {
            String b64 = encryptedText.substring(6);
            return decryptGCM(b64);
        }
        // 旧格式兼容（无前缀的 Base64）
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedText);
            if (combined.length >= GCM_IV_LENGTH + 16) {
                return decryptGCM(encryptedText);
            }
            return decryptAES_ECB_legacy(encryptedText);
        } catch (Exception e) {
            throw new RuntimeException("AES/GCM 解密失败", e);
        }
    }

    /** 判断字符串是否为密文（以 {AES}: 开头，或旧格式 Base64 密文）。 */
    public static boolean isEncrypted(String value) {
        if (value == null || value.isEmpty()) return false;
        if (value.startsWith("{AES}:")) return true;
        // 旧格式：纯 Base64 且长度足够（IV 12 + tag 16 = 28 字节 → Base64 至少 40 字符）
        if (value.length() >= 40 && value.matches("^[A-Za-z0-9+/]+=*$")) {
            try {
                byte[] decoded = Base64.getDecoder().decode(value);
                return decoded.length >= GCM_IV_LENGTH + 16;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private static String decryptGCM(String b64) {
        try {
            byte[] combined = Base64.getDecoder().decode(b64);
            byte[] keyBytes = Base64.getDecoder().decode(AES_KEY);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
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
