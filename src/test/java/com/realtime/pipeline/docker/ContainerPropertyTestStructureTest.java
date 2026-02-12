package com.realtime.pipeline.docker;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.*;

/**
 * 验证ContainerPropertyTest的结构和元数据
 * 
 * 这个测试不需要Docker环境，只验证测试类的结构是否正确
 */
class ContainerPropertyTestStructureTest {

    @Test
    void testClassExists() {
        assertThatCode(() -> Class.forName("com.realtime.pipeline.docker.ContainerPropertyTest"))
                .as("ContainerPropertyTest class should exist")
                .doesNotThrowAnyException();
    }

    @Test
    void testProperty33Exists() throws Exception {
        Class<?> testClass = Class.forName("com.realtime.pipeline.docker.ContainerPropertyTest");
        Method method = testClass.getDeclaredMethod("property33_containerStartupTimeliness", String.class);
        
        assertThat(method).isNotNull();
        assertThat(method.getName()).isEqualTo("property33_containerStartupTimeliness");
    }

    @Test
    void testProperty34Exists() throws Exception {
        Class<?> testClass = Class.forName("com.realtime.pipeline.docker.ContainerPropertyTest");
        Method method = testClass.getDeclaredMethod("property34_environmentVariableConfiguration", java.util.Map.class);
        
        assertThat(method).isNotNull();
        assertThat(method.getName()).isEqualTo("property34_environmentVariableConfiguration");
    }

    @Test
    void testProperty18Exists() throws Exception {
        Class<?> testClass = Class.forName("com.realtime.pipeline.docker.ContainerPropertyTest");
        Method method = testClass.getDeclaredMethod("property18_containerAutoRestart", String.class);
        
        assertThat(method).isNotNull();
        assertThat(method.getName()).isEqualTo("property18_containerAutoRestart");
    }

    @Test
    void testDataProvidersExist() throws Exception {
        Class<?> testClass = Class.forName("com.realtime.pipeline.docker.ContainerPropertyTest");
        
        // 验证数据生成器方法存在
        Method containerTypesProvider = testClass.getDeclaredMethod("containerTypes");
        assertThat(containerTypesProvider).isNotNull();
        
        Method envVarsProvider = testClass.getDeclaredMethod("validEnvironmentVariables");
        assertThat(envVarsProvider).isNotNull();
    }

    @Test
    void testHelperMethodsExist() throws Exception {
        Class<?> testClass = Class.forName("com.realtime.pipeline.docker.ContainerPropertyTest");
        
        // 验证辅助方法存在
        assertThat(testClass.getDeclaredMethod("isDockerAvailable")).isNotNull();
        assertThat(testClass.getDeclaredMethod("getServiceName", String.class)).isNotNull();
        assertThat(testClass.getDeclaredMethod("getContainerName", String.class)).isNotNull();
        assertThat(testClass.getDeclaredMethod("waitForContainerHealthy", String.class, java.time.Duration.class)).isNotNull();
        assertThat(testClass.getDeclaredMethod("getContainerId", String.class)).isNotNull();
        assertThat(testClass.getDeclaredMethod("cleanupContainer", String.class)).isNotNull();
    }
}
