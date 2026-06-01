package com.realtime.monitor.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentPasswordUtilTest {

    @Test
    void testIsPossiblyEncrypted() {
        // Valid Base64 string ending with padding
        assertTrue(EnvironmentPasswordUtil.isPossiblyEncrypted("dGVzdFBhc3N3b3Jk")); // "testPassword"
        assertTrue(EnvironmentPasswordUtil.isPossiblyEncrypted("aK9Hh1+R5yOaQe/x4b8G2+sA0tD6uF1iP3kO5wY8rL0="));
        
        // Invalid Base64 or non-multiple of 4
        assertFalse(EnvironmentPasswordUtil.isPossiblyEncrypted("plainTextPassword"));
        assertFalse(EnvironmentPasswordUtil.isPossiblyEncrypted("some-random-string!"));
        
        // Null or empty
        assertFalse(EnvironmentPasswordUtil.isPossiblyEncrypted(null));
        assertFalse(EnvironmentPasswordUtil.isPossiblyEncrypted(""));
    }

    @Test
    void testGetPasswordWithFallback() {
        // Test fallback mechanism for an environment variable that definitely doesn't exist
        String nonExistentEnv = "NON_EXISTENT_ENV_VAR_12345";
        String fallback = "myFallbackValue";
        
        String result = EnvironmentPasswordUtil.getPassword(nonExistentEnv, fallback);
        assertEquals(fallback, result);
        
        assertNull(EnvironmentPasswordUtil.getPassword(nonExistentEnv));
    }
    
    @Test
    void testGetPasswordRequiredThrowsException() {
        String nonExistentEnv = "NON_EXISTENT_ENV_VAR_12345";
        
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> EnvironmentPasswordUtil.getPasswordRequired(nonExistentEnv)
        );
        
        assertTrue(exception.getMessage().contains(nonExistentEnv));
        assertTrue(exception.getMessage().contains("未设置"));
    }
}
