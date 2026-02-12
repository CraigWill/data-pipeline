package com.realtime.pipeline.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ScalingConfig单元测试
 */
class ScalingConfigTest {
    
    @Test
    void testDefaultValues() {
        ScalingConfig config = ScalingConfig.builder().build();
        
        assertFalse(config.isAutoScalingEnabled());
        assertEquals(1, config.getMinTaskManagers());
        assertEquals(10, config.getMaxTaskManagers());
        assertEquals(80.0, config.getCpuThreshold());
        assertEquals(80.0, config.getMemoryThreshold());
        assertEquals(0.8, config.getBackpressureThreshold());
        assertEquals(300, config.getScaleOutCooldown());
        assertEquals(600, config.getScaleInCooldown());
        assertEquals(300000L, config.getDrainTimeout());
    }
    
    @Test
    void testValidConfiguration() {
        ScalingConfig config = ScalingConfig.builder()
            .autoScalingEnabled(true)
            .minTaskManagers(2)
            .maxTaskManagers(20)
            .cpuThreshold(70.0)
            .memoryThreshold(75.0)
            .backpressureThreshold(0.7)
            .scaleOutCooldown(180)
            .scaleInCooldown(360)
            .drainTimeout(600000L)
            .build();
        
        assertDoesNotThrow(config::validate);
    }
    
    @Test
    void testInvalidMinTaskManagers() {
        ScalingConfig config = ScalingConfig.builder()
            .minTaskManagers(0)
            .build();
        
        assertThrows(IllegalArgumentException.class, config::validate);
    }
    
    @Test
    void testInvalidMaxTaskManagers() {
        ScalingConfig config = ScalingConfig.builder()
            .minTaskManagers(10)
            .maxTaskManagers(5)
            .build();
        
        assertThrows(IllegalArgumentException.class, config::validate);
    }
    
    @Test
    void testInvalidCpuThreshold() {
        ScalingConfig config = ScalingConfig.builder()
            .cpuThreshold(150.0)
            .build();
        
        assertThrows(IllegalArgumentException.class, config::validate);
    }
    
    @Test
    void testInvalidMemoryThreshold() {
        ScalingConfig config = ScalingConfig.builder()
            .memoryThreshold(-10.0)
            .build();
        
        assertThrows(IllegalArgumentException.class, config::validate);
    }
    
    @Test
    void testInvalidBackpressureThreshold() {
        ScalingConfig config = ScalingConfig.builder()
            .backpressureThreshold(1.5)
            .build();
        
        assertThrows(IllegalArgumentException.class, config::validate);
    }
    
    @Test
    void testInvalidScaleOutCooldown() {
        ScalingConfig config = ScalingConfig.builder()
            .scaleOutCooldown(-1)
            .build();
        
        assertThrows(IllegalArgumentException.class, config::validate);
    }
    
    @Test
    void testInvalidScaleInCooldown() {
        ScalingConfig config = ScalingConfig.builder()
            .scaleInCooldown(-1)
            .build();
        
        assertThrows(IllegalArgumentException.class, config::validate);
    }
    
    @Test
    void testInvalidDrainTimeout() {
        ScalingConfig config = ScalingConfig.builder()
            .drainTimeout(0)
            .build();
        
        assertThrows(IllegalArgumentException.class, config::validate);
    }
}
