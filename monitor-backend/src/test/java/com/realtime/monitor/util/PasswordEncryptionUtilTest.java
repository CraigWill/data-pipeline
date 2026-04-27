package com.realtime.monitor.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

class PasswordEncryptionUtilTest {

    @Test
    void testEncodeBCrypt() {
        String rawPassword = "mySecretPassword123!";
        String encoded = PasswordEncryptionUtil.encodeBCrypt(rawPassword);

        assertNotNull(encoded);
        assertNotEquals(rawPassword, encoded);
        assertTrue(encoded.startsWith("$2a$")); // BCrypt prefix
    }

    @Test
    void testMatchesBCrypt() {
        String rawPassword = "password123";
        String encoded = PasswordEncryptionUtil.encodeBCrypt(rawPassword);

        assertTrue(PasswordEncryptionUtil.matchesBCrypt(rawPassword, encoded));
        assertFalse(PasswordEncryptionUtil.matchesBCrypt("wrongPassword", encoded));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AES_ENCRYPTION_KEY", matches = ".*")
    void testEncryptDecryptAES() {
        String plainText = "dbSuperSecretPassword999";
        
        // Test encryption
        String encrypted = PasswordEncryptionUtil.encryptAES(plainText);
        assertNotNull(encrypted);
        assertNotEquals(plainText, encrypted);
        
        // Test decryption
        String decrypted = PasswordEncryptionUtil.decryptAES(encrypted);
        assertEquals(plainText, decrypted);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AES_ENCRYPTION_KEY", matches = ".*")
    void testAESWithNullOrEmpty() {
        assertNull(PasswordEncryptionUtil.encryptAES(null));
        assertEquals("", PasswordEncryptionUtil.encryptAES(""));
        
        assertNull(PasswordEncryptionUtil.decryptAES(null));
        assertEquals("", PasswordEncryptionUtil.decryptAES(""));
    }
}
