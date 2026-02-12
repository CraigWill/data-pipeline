package com.realtime.pipeline.flink.ha;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * JobManager故障切换管理器单元测试
 * 
 * 验证需求: 5.3
 */
class JobManagerFailoverManagerTest {

    private JobManagerFailoverManager failoverManager;

    @BeforeEach
    void setUp() {
        failoverManager = new JobManagerFailoverManager(30000L);
    }

    @Test
    void testInitialState() {
        // 验证初始状态
        assertThat(failoverManager.isPrimaryActive()).isTrue();
        assertThat(failoverManager.getCurrentLeader()).isEqualTo("primary");
        assertThat(failoverManager.getFailoverCount()).isEqualTo(0);
        assertThat(failoverManager.getLastFailoverTime()).isEqualTo(0);
    }

    @Test
    void testPrimaryFailover() {
        // 需求 5.3: WHEN 主JobManager失败 THEN THE System SHALL 在30秒内切换到备用JobManager
        boolean success = failoverManager.recordFailure("primary");

        // 验证故障切换成功
        assertThat(success).isTrue();
        assertThat(failoverManager.isPrimaryActive()).isFalse();
        assertThat(failoverManager.getCurrentLeader()).isEqualTo("standby");
        assertThat(failoverManager.getFailoverCount()).isEqualTo(1);
        assertThat(failoverManager.getLastFailoverTime()).isGreaterThan(0);
    }

    @Test
    void testStandbyFailure() {
        // 备用JobManager失败不触发故障切换
        boolean success = failoverManager.recordFailure("standby");

        assertThat(success).isFalse();
        assertThat(failoverManager.isPrimaryActive()).isTrue();
        assertThat(failoverManager.getCurrentLeader()).isEqualTo("primary");
    }

    @Test
    void testMultipleFailovers() {
        // 第一次故障切换
        failoverManager.recordFailure("primary");
        assertThat(failoverManager.getFailoverCount()).isEqualTo(1);

        // 重置状态
        failoverManager.reset();

        // 第二次故障切换
        failoverManager.recordFailure("primary");
        assertThat(failoverManager.getFailoverCount()).isEqualTo(1);
    }

    @Test
    void testRecovery() {
        // 主JobManager失败
        failoverManager.recordFailure("primary");
        assertThat(failoverManager.getCurrentLeader()).isEqualTo("standby");

        // 主JobManager恢复
        failoverManager.recordRecovery("primary");

        // 验证不会自动切换回主JobManager（避免频繁切换）
        assertThat(failoverManager.getCurrentLeader()).isEqualTo("standby");
    }

    @Test
    void testFailoverTimeout() {
        // 验证故障切换超时时间
        assertThat(failoverManager.getFailoverTimeout()).isEqualTo(30000L);
    }

    @Test
    void testFailoverWithinTimeout() {
        // 需求 5.3: 在30秒内完成故障切换
        long startTime = System.currentTimeMillis();
        boolean success = failoverManager.recordFailure("primary");
        long duration = System.currentTimeMillis() - startTime;

        assertThat(success).isTrue();
        assertThat(duration).isLessThan(30000L);
    }

    @Test
    void testReset() {
        // 触发故障切换
        failoverManager.recordFailure("primary");
        assertThat(failoverManager.getFailoverCount()).isEqualTo(1);

        // 重置
        failoverManager.reset();

        // 验证状态已重置
        assertThat(failoverManager.isPrimaryActive()).isTrue();
        assertThat(failoverManager.getCurrentLeader()).isEqualTo("primary");
        assertThat(failoverManager.getFailoverCount()).isEqualTo(0);
        assertThat(failoverManager.getLastFailoverTime()).isEqualTo(0);
    }

    @Test
    void testGetStats() {
        // 触发故障切换
        failoverManager.recordFailure("primary");

        // 获取统计信息
        JobManagerFailoverManager.FailoverStats stats = failoverManager.getStats();

        assertThat(stats.getCurrentLeader()).isEqualTo("standby");
        assertThat(stats.isPrimaryActive()).isFalse();
        assertThat(stats.getFailoverCount()).isEqualTo(1);
        assertThat(stats.getLastFailoverTime()).isGreaterThan(0);
        assertThat(stats.getFailoverTimeout()).isEqualTo(30000L);
    }

    @Test
    void testStatsToString() {
        failoverManager.recordFailure("primary");
        JobManagerFailoverManager.FailoverStats stats = failoverManager.getStats();

        String statsString = stats.toString();
        assertThat(statsString).contains("leader=standby");
        assertThat(statsString).contains("primaryActive=false");
        assertThat(statsString).contains("failoverCount=1");
        assertThat(statsString).contains("timeout=30000ms");
    }

    @Test
    void testInvalidTimeout() {
        assertThatThrownBy(() -> new JobManagerFailoverManager(0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Failover timeout must be positive");

        assertThatThrownBy(() -> new JobManagerFailoverManager(-1000))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Failover timeout must be positive");
    }

    @Test
    void testFailoverTimeRequirement() {
        // 需求 5.3: 故障切换应在30秒内完成
        JobManagerFailoverManager manager = new JobManagerFailoverManager(30000L);

        long startTime = System.currentTimeMillis();
        boolean success = manager.recordFailure("primary");
        long failoverDuration = System.currentTimeMillis() - startTime;

        // 验证故障切换成功且在30秒内完成
        assertThat(success).isTrue();
        assertThat(failoverDuration).isLessThanOrEqualTo(30000L);
    }

    @Test
    void testConcurrentFailures() throws InterruptedException {
        // 测试并发故障记录
        Thread t1 = new Thread(() -> failoverManager.recordFailure("primary"));
        Thread t2 = new Thread(() -> failoverManager.recordFailure("primary"));

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        // 验证状态一致性
        assertThat(failoverManager.getCurrentLeader()).isEqualTo("standby");
        assertThat(failoverManager.isPrimaryActive()).isFalse();
    }
}
