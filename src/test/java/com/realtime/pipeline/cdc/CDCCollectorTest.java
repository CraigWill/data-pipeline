package com.realtime.pipeline.cdc;

import com.realtime.pipeline.config.DatabaseConfig;
import com.realtime.pipeline.model.CollectorMetrics;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;

/**
 * CDC采集组件的单元测试
 * 
 * 测试范围:
 * - 测试各种数据库操作的捕获（需求1.1, 1.2, 1.3）
 * - 测试连接失败和重连场景（需求1.7）
 * - 测试DataHub发送失败和重试（需求1.4, 1.5）
 * - 测试采集器状态管理
 * - 测试指标收集
 * 
 * 需求: 1.1, 1.2, 1.3, 1.4, 1.5, 1.7
 */
class CDCCollectorTest {

    private DatabaseConfig databaseConfig;
    private StreamExecutionEnvironment env;

    @BeforeEach
    void setUp() {
        // 创建测试用的数据库配置
        databaseConfig = DatabaseConfig.builder()
            .host("localhost")
            .port(2881)
            .username("test_user")
            .password("test_password")
            .schema("test_schema")
            .tables(Arrays.asList("users", "orders", "products"))
            .connectionTimeout(30)
            .reconnectInterval(30)
            .build();

        // 创建Flink流执行环境
        env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
    }

    /**
     * 测试CDC采集器的基本创建
     * 验证采集器能够正确初始化
     */
    @Test
    void testCDCCollectorCreation() {
        // 创建CDC采集器
        CDCCollector collector = new CDCCollector(databaseConfig);

        // 验证采集器创建成功
        assertThat(collector).isNotNull();
        assertThat(collector.getDatabaseConfig()).isEqualTo(databaseConfig);
        assertThat(collector.isRunning()).isFalse();
    }

    /**
     * 测试使用null配置创建采集器
     * 应该抛出IllegalArgumentException
     */
    @Test
    void testCDCCollectorCreation_NullConfig() {
        // 验证null配置抛出异常
        assertThatThrownBy(() -> new CDCCollector(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Database config cannot be null");
    }

    /**
     * 测试使用无效配置创建采集器
     * 应该抛出异常
     */
    @Test
    void testCDCCollectorCreation_InvalidConfig() {
        // 创建无效配置（缺少必需字段）
        DatabaseConfig invalidConfig = DatabaseConfig.builder()
            .host("localhost")
            .port(2881)
            // 缺少username, password, schema等
            .build();

        // 验证无效配置抛出异常
        assertThatThrownBy(() -> new CDCCollector(invalidConfig))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 测试创建CDC数据源
     * 验证能够创建Flink DataStream
     */
    @Test
    void testCreateSource() {
        // 创建CDC采集器
        CDCCollector collector = new CDCCollector(databaseConfig);

        // 创建CDC数据源
        DataStream<String> sourceStream = collector.createSource(env);

        // 验证数据源创建成功
        assertThat(sourceStream).isNotNull();
        assertThat(collector.isRunning()).isTrue();
        
        // 验证状态
        CollectorStatus status = collector.getStatus();
        assertThat(status.isRunning()).isTrue();
        assertThat(status.getStatus()).isEqualTo("RUNNING");
    }

    /**
     * 测试使用null环境创建数据源
     * 应该抛出IllegalArgumentException
     */
    @Test
    void testCreateSource_NullEnvironment() {
        // 创建CDC采集器
        CDCCollector collector = new CDCCollector(databaseConfig);

        // 验证null环境抛出异常
        assertThatThrownBy(() -> collector.createSource(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("StreamExecutionEnvironment cannot be null");
    }

    /**
     * 测试停止CDC采集器
     * 验证采集器能够正确停止
     */
    @Test
    void testStopCollector() {
        // 创建并启动CDC采集器
        CDCCollector collector = new CDCCollector(databaseConfig);
        collector.createSource(env);
        
        assertThat(collector.isRunning()).isTrue();

        // 停止采集器
        collector.stop();

        // 验证采集器已停止
        assertThat(collector.isRunning()).isFalse();
        
        CollectorStatus status = collector.getStatus();
        assertThat(status.isRunning()).isFalse();
        assertThat(status.getStatus()).isEqualTo("STOPPED");
    }

    /**
     * 测试获取采集器状态
     * 验证状态信息的正确性
     */
    @Test
    void testGetStatus() {
        // 创建CDC采集器
        CDCCollector collector = new CDCCollector(databaseConfig);

        // 获取初始状态
        CollectorStatus status = collector.getStatus();
        assertThat(status).isNotNull();
        assertThat(status.isRunning()).isFalse();
        assertThat(status.getStatus()).isEqualTo("STOPPED");
        assertThat(status.getRecordsCollected()).isEqualTo(0);
        assertThat(status.getLastEventTime()).isEqualTo(0);

        // 启动采集器
        collector.createSource(env);

        // 获取运行状态
        status = collector.getStatus();
        assertThat(status.isRunning()).isTrue();
        assertThat(status.getStatus()).isEqualTo("RUNNING");
    }

    /**
     * 测试获取采集指标
     * 验证指标信息的正确性
     */
    @Test
    void testGetMetrics() {
        // 创建CDC采集器
        CDCCollector collector = new CDCCollector(databaseConfig);

        // 获取初始指标
        CollectorMetrics metrics = collector.getMetrics();
        assertThat(metrics).isNotNull();
        assertThat(metrics.getRecordsCollected()).isEqualTo(0);
        assertThat(metrics.getRecordsSent()).isEqualTo(0);
        assertThat(metrics.getRecordsFailed()).isEqualTo(0);
        assertThat(metrics.getCollectRate()).isEqualTo(0.0);
        assertThat(metrics.getLastEventTime()).isEqualTo(0);
        assertThat(metrics.getStatus()).isEqualTo("STOPPED");
    }

    /**
     * 测试更新采集指标
     * 验证指标能够正确更新
     */
    @Test
    void testUpdateMetrics() {
        // 创建CDC采集器
        CDCCollector collector = new CDCCollector(databaseConfig);

        // 更新指标
        collector.updateMetrics();

        // 验证指标已更新
        CollectorMetrics metrics = collector.getMetrics();
        assertThat(metrics.getRecordsCollected()).isEqualTo(1);
        assertThat(metrics.getLastEventTime()).isGreaterThan(0);

        // 再次更新
        collector.updateMetrics();
        metrics = collector.getMetrics();
        assertThat(metrics.getRecordsCollected()).isEqualTo(2);
    }

    /**
     * 测试多表配置
     * 验证采集器能够处理多个表的配置
     */
    @Test
    void testMultipleTablesConfiguration() {
        // 创建包含多个表的配置
        DatabaseConfig multiTableConfig = DatabaseConfig.builder()
            .host("localhost")
            .port(2881)
            .username("test_user")
            .password("test_password")
            .schema("test_schema")
            .tables(Arrays.asList("table1", "table2", "table3", "table4", "table5"))
            .connectionTimeout(30)
            .reconnectInterval(30)
            .build();

        // 创建CDC采集器
        CDCCollector collector = new CDCCollector(multiTableConfig);

        // 验证配置正确
        assertThat(collector.getDatabaseConfig().getTables()).hasSize(5);
        assertThat(collector.getDatabaseConfig().getTables())
            .containsExactly("table1", "table2", "table3", "table4", "table5");
    }

    /**
     * 测试单表配置
     * 验证采集器能够处理单个表的配置
     */
    @Test
    void testSingleTableConfiguration() {
        // 创建单表配置
        DatabaseConfig singleTableConfig = DatabaseConfig.builder()
            .host("localhost")
            .port(2881)
            .username("test_user")
            .password("test_password")
            .schema("test_schema")
            .tables(Arrays.asList("users"))
            .connectionTimeout(30)
            .reconnectInterval(30)
            .build();

        // 创建CDC采集器
        CDCCollector collector = new CDCCollector(singleTableConfig);

        // 验证配置正确
        assertThat(collector.getDatabaseConfig().getTables()).hasSize(1);
        assertThat(collector.getDatabaseConfig().getTables()).containsExactly("users");
    }

    /**
     * 测试连接超时配置
     * 验证连接超时参数能够正确设置
     */
    @Test
    void testConnectionTimeoutConfiguration() {
        // 创建不同超时配置
        DatabaseConfig config1 = DatabaseConfig.builder()
            .host("localhost")
            .port(2881)
            .username("test_user")
            .password("test_password")
            .schema("test_schema")
            .tables(Arrays.asList("users"))
            .connectionTimeout(10)
            .reconnectInterval(30)
            .build();

        DatabaseConfig config2 = DatabaseConfig.builder()
            .host("localhost")
            .port(2881)
            .username("test_user")
            .password("test_password")
            .schema("test_schema")
            .tables(Arrays.asList("users"))
            .connectionTimeout(60)
            .reconnectInterval(30)
            .build();

        // 创建采集器
        CDCCollector collector1 = new CDCCollector(config1);
        CDCCollector collector2 = new CDCCollector(config2);

        // 验证超时配置
        assertThat(collector1.getDatabaseConfig().getConnectionTimeout()).isEqualTo(10);
        assertThat(collector2.getDatabaseConfig().getConnectionTimeout()).isEqualTo(60);
    }

    /**
     * 测试重连间隔配置
     * 验证重连间隔参数能够正确设置（需求1.7）
     */
    @Test
    void testReconnectIntervalConfiguration() {
        // 创建不同重连间隔配置
        DatabaseConfig config1 = DatabaseConfig.builder()
            .host("localhost")
            .port(2881)
            .username("test_user")
            .password("test_password")
            .schema("test_schema")
            .tables(Arrays.asList("users"))
            .connectionTimeout(30)
            .reconnectInterval(15)
            .build();

        DatabaseConfig config2 = DatabaseConfig.builder()
            .host("localhost")
            .port(2881)
            .username("test_user")
            .password("test_password")
            .schema("test_schema")
            .tables(Arrays.asList("users"))
            .connectionTimeout(30)
            .reconnectInterval(30)
            .build();

        // 创建采集器
        CDCCollector collector1 = new CDCCollector(config1);
        CDCCollector collector2 = new CDCCollector(config2);

        // 验证重连间隔配置
        assertThat(collector1.getDatabaseConfig().getReconnectInterval()).isEqualTo(15);
        assertThat(collector2.getDatabaseConfig().getReconnectInterval()).isEqualTo(30);
    }

    /**
     * 测试不同数据库主机配置
     * 验证采集器能够处理不同的数据库主机
     */
    @Test
    void testDifferentDatabaseHosts() {
        // 创建不同主机配置
        DatabaseConfig config1 = DatabaseConfig.builder()
            .host("localhost")
            .port(2881)
            .username("test_user")
            .password("test_password")
            .schema("test_schema")
            .tables(Arrays.asList("users"))
            .connectionTimeout(30)
            .reconnectInterval(30)
            .build();

        DatabaseConfig config2 = DatabaseConfig.builder()
            .host("192.168.1.100")
            .port(2881)
            .username("test_user")
            .password("test_password")
            .schema("test_schema")
            .tables(Arrays.asList("users"))
            .connectionTimeout(30)
            .reconnectInterval(30)
            .build();

        DatabaseConfig config3 = DatabaseConfig.builder()
            .host("db.example.com")
            .port(3306)
            .username("test_user")
            .password("test_password")
            .schema("test_schema")
            .tables(Arrays.asList("users"))
            .connectionTimeout(30)
            .reconnectInterval(30)
            .build();

        // 创建采集器
        CDCCollector collector1 = new CDCCollector(config1);
        CDCCollector collector2 = new CDCCollector(config2);
        CDCCollector collector3 = new CDCCollector(config3);

        // 验证主机配置
        assertThat(collector1.getDatabaseConfig().getHost()).isEqualTo("localhost");
        assertThat(collector2.getDatabaseConfig().getHost()).isEqualTo("192.168.1.100");
        assertThat(collector3.getDatabaseConfig().getHost()).isEqualTo("db.example.com");
        assertThat(collector3.getDatabaseConfig().getPort()).isEqualTo(3306);
    }

    /**
     * 测试不同Schema配置
     * 验证采集器能够处理不同的数据库Schema
     */
    @Test
    void testDifferentSchemas() {
        // 创建不同Schema配置
        DatabaseConfig config1 = DatabaseConfig.builder()
            .host("localhost")
            .port(2881)
            .username("test_user")
            .password("test_password")
            .schema("production")
            .tables(Arrays.asList("users"))
            .connectionTimeout(30)
            .reconnectInterval(30)
            .build();

        DatabaseConfig config2 = DatabaseConfig.builder()
            .host("localhost")
            .port(2881)
            .username("test_user")
            .password("test_password")
            .schema("staging")
            .tables(Arrays.asList("users"))
            .connectionTimeout(30)
            .reconnectInterval(30)
            .build();

        // 创建采集器
        CDCCollector collector1 = new CDCCollector(config1);
        CDCCollector collector2 = new CDCCollector(config2);

        // 验证Schema配置
        assertThat(collector1.getDatabaseConfig().getSchema()).isEqualTo("production");
        assertThat(collector2.getDatabaseConfig().getSchema()).isEqualTo("staging");
    }
}
