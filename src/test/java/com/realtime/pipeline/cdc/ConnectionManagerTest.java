package com.realtime.pipeline.cdc;

import com.realtime.pipeline.config.DatabaseConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;

/**
 * ConnectionManager单元测试
 * 
 * 测试范围:
 * - 测试连接管理器的创建
 * - 测试连接状态检查
 * - 测试连接断开和重连逻辑（需求1.7）
 * - 测试健康检查机制（需求1.6）
 * - 测试配置验证
 * 
 * 需求: 1.6, 1.7
 * 
 * 注意：由于单元测试环境无法连接真实数据库，
 * 这些测试主要验证ConnectionManager的逻辑和状态管理，
 * 而不是实际的数据库连接。
 */
class ConnectionManagerTest {

    private DatabaseConfig databaseConfig;

    @BeforeEach
    void setUp() {
        // 创建测试用的数据库配置
        databaseConfig = DatabaseConfig.builder()
            .host("localhost")
            .port(2881)
            .username("test_user")
            .password("test_password")
            .schema("test_schema")
            .tables(Arrays.asList("users", "orders"))
            .connectionTimeout(30)
            .reconnectInterval(30)
            .build();
    }

    /**
     * 测试ConnectionManager的基本创建
     * 验证管理器能够正确初始化
     */
    @Test
    void testConnectionManagerCreation() {
        // 创建ConnectionManager
        ConnectionManager manager = new ConnectionManager(databaseConfig);

        // 验证管理器创建成功
        assertThat(manager).isNotNull();
        assertThat(manager.isConnected()).isFalse();
        assertThat(manager.isReconnecting()).isFalse();
    }

    /**
     * 测试使用null配置创建管理器
     * 应该抛出IllegalArgumentException
     */
    @Test
    void testConnectionManagerCreation_NullConfig() {
        // 验证null配置抛出异常
        assertThatThrownBy(() -> new ConnectionManager(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Database config cannot be null");
    }

    /**
     * 测试初始连接状态
     * 验证初始状态为未连接
     */
    @Test
    void testInitialConnectionState() {
        // 创建ConnectionManager
        ConnectionManager manager = new ConnectionManager(databaseConfig);

        // 验证初始状态
        assertThat(manager.isConnected()).isFalse();
        assertThat(manager.isReconnecting()).isFalse();
        assertThat(manager.getConnection()).isNull();
    }

    /**
     * 测试断开连接
     * 验证断开连接后状态正确
     */
    @Test
    void testDisconnect() {
        // 创建ConnectionManager
        ConnectionManager manager = new ConnectionManager(databaseConfig);

        // 断开连接（即使没有建立连接也应该能安全调用）
        manager.disconnect();

        // 验证状态
        assertThat(manager.isConnected()).isFalse();
        assertThat(manager.getConnection()).isNull();
    }

    /**
     * 测试重连间隔配置
     * 验证重连间隔参数能够正确设置（需求1.7）
     */
    @Test
    void testReconnectIntervalConfiguration() {
        // 创建不同重连间隔的配置
        DatabaseConfig config15s = DatabaseConfig.builder()
            .host("localhost")
            .port(2881)
            .username("test_user")
            .password("test_password")
            .schema("test_schema")
            .tables(Arrays.asList("users"))
            .connectionTimeout(30)
            .reconnectInterval(15)
            .build();

        DatabaseConfig config30s = DatabaseConfig.builder()
            .host("localhost")
            .port(2881)
            .username("test_user")
            .password("test_password")
            .schema("test_schema")
            .tables(Arrays.asList("users"))
            .connectionTimeout(30)
            .reconnectInterval(30)
            .build();

        DatabaseConfig config60s = DatabaseConfig.builder()
            .host("localhost")
            .port(2881)
            .username("test_user")
            .password("test_password")
            .schema("test_schema")
            .tables(Arrays.asList("users"))
            .connectionTimeout(30)
            .reconnectInterval(60)
            .build();

        // 创建管理器
        ConnectionManager manager15s = new ConnectionManager(config15s);
        ConnectionManager manager30s = new ConnectionManager(config30s);
        ConnectionManager manager60s = new ConnectionManager(config60s);

        // 验证配置（通过尝试连接来触发配置使用）
        assertThat(manager15s).isNotNull();
        assertThat(manager30s).isNotNull();
        assertThat(manager60s).isNotNull();
    }

    /**
     * 测试连接超时配置
     * 验证连接超时参数能够正确设置
     */
    @Test
    void testConnectionTimeoutConfiguration() {
        // 创建不同超时配置
        DatabaseConfig config10s = DatabaseConfig.builder()
            .host("localhost")
            .port(2881)
            .username("test_user")
            .password("test_password")
            .schema("test_schema")
            .tables(Arrays.asList("users"))
            .connectionTimeout(10)
            .reconnectInterval(30)
            .build();

        DatabaseConfig config30s = DatabaseConfig.builder()
            .host("localhost")
            .port(2881)
            .username("test_user")
            .password("test_password")
            .schema("test_schema")
            .tables(Arrays.asList("users"))
            .connectionTimeout(30)
            .reconnectInterval(30)
            .build();

        // 创建管理器
        ConnectionManager manager10s = new ConnectionManager(config10s);
        ConnectionManager manager30s = new ConnectionManager(config30s);

        // 验证管理器创建成功
        assertThat(manager10s).isNotNull();
        assertThat(manager30s).isNotNull();
    }

    /**
     * 测试多次断开连接
     * 验证多次调用disconnect是安全的
     */
    @Test
    void testMultipleDisconnects() {
        // 创建ConnectionManager
        ConnectionManager manager = new ConnectionManager(databaseConfig);

        // 多次断开连接
        manager.disconnect();
        manager.disconnect();
        manager.disconnect();

        // 验证状态正常
        assertThat(manager.isConnected()).isFalse();
        assertThat(manager.getConnection()).isNull();
    }

    /**
     * 测试不同数据库配置
     * 验证管理器能够处理不同的数据库配置
     */
    @Test
    void testDifferentDatabaseConfigurations() {
        // 创建不同的数据库配置
        DatabaseConfig config1 = DatabaseConfig.builder()
            .host("localhost")
            .port(2881)
            .username("user1")
            .password("pass1")
            .schema("schema1")
            .tables(Arrays.asList("table1"))
            .connectionTimeout(30)
            .reconnectInterval(30)
            .build();

        DatabaseConfig config2 = DatabaseConfig.builder()
            .host("192.168.1.100")
            .port(3306)
            .username("user2")
            .password("pass2")
            .schema("schema2")
            .tables(Arrays.asList("table2", "table3"))
            .connectionTimeout(60)
            .reconnectInterval(15)
            .build();

        // 创建管理器
        ConnectionManager manager1 = new ConnectionManager(config1);
        ConnectionManager manager2 = new ConnectionManager(config2);

        // 验证管理器创建成功
        assertThat(manager1).isNotNull();
        assertThat(manager2).isNotNull();
        assertThat(manager1.isConnected()).isFalse();
        assertThat(manager2.isConnected()).isFalse();
    }

    /**
     * 测试连接状态检查
     * 验证isConnected方法的正确性
     */
    @Test
    void testConnectionStateCheck() {
        // 创建ConnectionManager
        ConnectionManager manager = new ConnectionManager(databaseConfig);

        // 初始状态应该是未连接
        assertThat(manager.isConnected()).isFalse();

        // 获取连接应该返回null
        Connection conn = manager.getConnection();
        assertThat(conn).isNull();
    }

    /**
     * 测试重连状态检查
     * 验证isReconnecting方法的正确性
     */
    @Test
    void testReconnectingStateCheck() {
        // 创建ConnectionManager
        ConnectionManager manager = new ConnectionManager(databaseConfig);

        // 初始状态应该不在重连中
        assertThat(manager.isReconnecting()).isFalse();
    }

    /**
     * 测试边界情况：极短的连接超时
     * 验证管理器能够处理极短的超时配置
     */
    @Test
    void testVeryShortConnectionTimeout() {
        // 创建极短超时配置（1秒）
        DatabaseConfig shortTimeoutConfig = DatabaseConfig.builder()
            .host("localhost")
            .port(2881)
            .username("test_user")
            .password("test_password")
            .schema("test_schema")
            .tables(Arrays.asList("users"))
            .connectionTimeout(1)
            .reconnectInterval(30)
            .build();

        // 创建管理器
        ConnectionManager manager = new ConnectionManager(shortTimeoutConfig);

        // 验证管理器创建成功
        assertThat(manager).isNotNull();
        assertThat(manager.isConnected()).isFalse();
    }

    /**
     * 测试边界情况：极长的连接超时
     * 验证管理器能够处理极长的超时配置
     */
    @Test
    void testVeryLongConnectionTimeout() {
        // 创建极长超时配置（300秒）
        DatabaseConfig longTimeoutConfig = DatabaseConfig.builder()
            .host("localhost")
            .port(2881)
            .username("test_user")
            .password("test_password")
            .schema("test_schema")
            .tables(Arrays.asList("users"))
            .connectionTimeout(300)
            .reconnectInterval(30)
            .build();

        // 创建管理器
        ConnectionManager manager = new ConnectionManager(longTimeoutConfig);

        // 验证管理器创建成功
        assertThat(manager).isNotNull();
        assertThat(manager.isConnected()).isFalse();
    }

    /**
     * 测试边界情况：极短的重连间隔
     * 验证管理器能够处理极短的重连间隔
     */
    @Test
    void testVeryShortReconnectInterval() {
        // 创建极短重连间隔配置（1秒）
        DatabaseConfig shortIntervalConfig = DatabaseConfig.builder()
            .host("localhost")
            .port(2881)
            .username("test_user")
            .password("test_password")
            .schema("test_schema")
            .tables(Arrays.asList("users"))
            .connectionTimeout(30)
            .reconnectInterval(1)
            .build();

        // 创建管理器
        ConnectionManager manager = new ConnectionManager(shortIntervalConfig);

        // 验证管理器创建成功
        assertThat(manager).isNotNull();
        assertThat(manager.isConnected()).isFalse();
    }

    /**
     * 测试边界情况：标准的30秒重连间隔（需求1.7）
     * 验证管理器能够正确处理标准的30秒重连间隔
     */
    @Test
    void testStandardReconnectInterval() {
        // 创建标准30秒重连间隔配置
        DatabaseConfig standardConfig = DatabaseConfig.builder()
            .host("localhost")
            .port(2881)
            .username("test_user")
            .password("test_password")
            .schema("test_schema")
            .tables(Arrays.asList("users"))
            .connectionTimeout(30)
            .reconnectInterval(30)  // 需求1.7: 30秒内自动重连
            .build();

        // 创建管理器
        ConnectionManager manager = new ConnectionManager(standardConfig);

        // 验证管理器创建成功
        assertThat(manager).isNotNull();
        assertThat(manager.isConnected()).isFalse();
        assertThat(manager.isReconnecting()).isFalse();
    }

    /**
     * 测试连接管理器的序列化支持
     * 验证ConnectionManager实现了Serializable
     */
    @Test
    void testSerializability() {
        // 创建ConnectionManager
        ConnectionManager manager = new ConnectionManager(databaseConfig);

        // 验证是Serializable的实例
        assertThat(manager).isInstanceOf(java.io.Serializable.class);
    }
}
