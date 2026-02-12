package com.realtime.pipeline.ha;

import com.realtime.pipeline.config.ConfigurationUpdateManager;
import com.realtime.pipeline.config.FlinkConfig;
import com.realtime.pipeline.config.HighAvailabilityConfig;
import com.realtime.pipeline.flink.ha.HighAvailabilityConfigurator;
import com.realtime.pipeline.flink.ha.JobManagerFailoverManager;
import com.realtime.pipeline.flink.scaling.DynamicScalingManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * 高可用性集成单元测试
 * 
 * 测试JobManager切换、动态扩缩容和配置热更新的集成场景
 * 
 * 验证需求: 5.3, 6.2, 6.3, 6.7, 5.7
 */
class HighAvailabilityIntegrationTest {

    @TempDir
    Path tempDir;

    private HighAvailabilityConfig haConfig;
    private JobManagerFailoverManager failoverManager;
    private DynamicScalingManager scalingManager;
    private ConfigurationUpdateManager configManager;
    private FlinkConfig flinkConfig;

    @BeforeEach
    void setUp() {
        // 初始化HA配置
        haConfig = HighAvailabilityConfig.builder()
            .enabled(true)
            .mode("zookeeper")
            .zookeeperQuorum("localhost:2181")
            .zookeeperPath("/flink")
            .storageDir(tempDir.resolve("ha").toString())
            .jobManagerCount(2)
            .failoverTimeout(30000L)
            .build();

        // 初始化故障切换管理器
        failoverManager = new JobManagerFailoverManager(30000L);

        // 初始化动态扩缩容管理器
        scalingManager = new DynamicScalingManager("localhost", 8081);

        // 初始化配置管理器
        flinkConfig = FlinkConfig.builder()
            .parallelism(4)
            .checkpointInterval(300000L)
            .checkpointTimeout(600000L)
            .checkpointDir(tempDir.resolve("checkpoints").toString())
            .build();

        configManager = new ConfigurationUpdateManager(flinkConfig);
    }

    /**
     * 测试JobManager故障切换
     * 需求 5.3: WHEN 主JobManager失败 THEN THE System SHALL 在30秒内切换到备用JobManager
     */
    @Test
    void testJobManagerFailover() {
        // 验证初始状态
        assertThat(failoverManager.isPrimaryActive()).isTrue();
        assertThat(failoverManager.getCurrentLeader()).isEqualTo("primary");

        // 记录故障切换开始时间
        long startTime = System.currentTimeMillis();

        // 触发主JobManager故障
        boolean success = failoverManager.recordFailure("primary");

        // 计算故障切换时间
        long failoverDuration = System.currentTimeMillis() - startTime;

        // 验证故障切换成功
        assertThat(success).isTrue();
        assertThat(failoverManager.isPrimaryActive()).isFalse();
        assertThat(failoverManager.getCurrentLeader()).isEqualTo("standby");
        assertThat(failoverManager.getFailoverCount()).isEqualTo(1);

        // 验证在30秒内完成切换
        assertThat(failoverDuration).isLessThanOrEqualTo(30000L);
    }

    /**
     * 测试动态扩容TaskManager
     * 需求 6.2: THE System SHALL 支持动态增加TaskManager实例
     */
    @Test
    void testDynamicScaleOut() {
        // 扩容2个TaskManager
        boolean result = scalingManager.scaleOutTaskManagers(2);

        // 验证扩容成功
        assertThat(result).isTrue();
    }

    /**
     * 测试动态缩容TaskManager
     * 需求 6.7: WHEN 缩容操作 THEN THE System SHALL 完成当前处理任务后再释放资源
     */
    @Test
    void testGracefulScaleIn() {
        // 优雅缩容1个TaskManager，等待5分钟完成当前任务
        long drainTimeout = 300000L;
        boolean result = scalingManager.scaleInTaskManagers(1, drainTimeout);

        // 验证缩容成功
        assertThat(result).isTrue();
    }

    /**
     * 测试配置热更新
     * 需求 5.7: THE System SHALL 支持零停机时间的配置更新
     */
    @Test
    void testZeroDowntimeConfigUpdate() {
        // 创建新配置
        FlinkConfig newConfig = FlinkConfig.builder()
            .parallelism(8)
            .checkpointInterval(180000L)
            .checkpointTimeout(600000L)
            .checkpointDir(tempDir.resolve("checkpoints").toString())
            .build();

        // 更新配置
        configManager.updateFlinkConfig(newConfig);

        // 验证配置已更新
        FlinkConfig currentConfig = configManager.getCurrentFlinkConfig();
        assertThat(currentConfig.getParallelism()).isEqualTo(8);
        assertThat(currentConfig.getCheckpointInterval()).isEqualTo(180000L);
    }

    /**
     * 测试故障切换期间的扩容操作
     * 验证在JobManager切换时，扩容操作仍能正常进行
     */
    @Test
    void testScaleOutDuringFailover() {
        // 触发JobManager故障切换
        failoverManager.recordFailure("primary");

        // 在故障切换后立即进行扩容
        boolean scaleOutResult = scalingManager.scaleOutTaskManagers(2);

        // 验证扩容成功
        assertThat(scaleOutResult).isTrue();
        assertThat(failoverManager.getCurrentLeader()).isEqualTo("standby");
    }

    /**
     * 测试配置更新期间的故障切换
     * 验证在配置更新时，故障切换仍能正常进行
     */
    @Test
    void testFailoverDuringConfigUpdate() throws InterruptedException {
        CountDownLatch configUpdateLatch = new CountDownLatch(1);
        AtomicBoolean configUpdated = new AtomicBoolean(false);

        // 在后台线程中更新配置
        Thread configThread = new Thread(() -> {
            FlinkConfig newConfig = FlinkConfig.builder()
                .parallelism(8)
                .checkpointInterval(180000L)
                .checkpointTimeout(600000L)
                .checkpointDir(tempDir.resolve("checkpoints").toString())
                .build();

            configManager.updateFlinkConfig(newConfig);
            configUpdated.set(true);
            configUpdateLatch.countDown();
        });

        configThread.start();

        // 在配置更新期间触发故障切换
        Thread.sleep(50);
        boolean failoverResult = failoverManager.recordFailure("primary");

        // 等待配置更新完成
        assertThat(configUpdateLatch.await(5, TimeUnit.SECONDS)).isTrue();

        // 验证两个操作都成功
        assertThat(failoverResult).isTrue();
        assertThat(configUpdated.get()).isTrue();
        assertThat(failoverManager.getCurrentLeader()).isEqualTo("standby");
        assertThat(configManager.getCurrentFlinkConfig().getParallelism()).isEqualTo(8);
    }

    /**
     * 测试扩容期间的配置更新
     * 需求 6.6: THE System SHALL 在扩容过程中保持数据处理的连续性
     * 需求 5.7: THE System SHALL 支持零停机时间的配置更新
     */
    @Test
    void testConfigUpdateDuringScaling() throws InterruptedException {
        CountDownLatch scalingLatch = new CountDownLatch(1);
        AtomicBoolean scalingCompleted = new AtomicBoolean(false);

        // 在后台线程中进行扩容
        Thread scalingThread = new Thread(() -> {
            boolean result = scalingManager.scaleOutTaskManagers(2);
            scalingCompleted.set(result);
            scalingLatch.countDown();
        });

        scalingThread.start();

        // 在扩容期间更新配置
        Thread.sleep(50);
        FlinkConfig newConfig = FlinkConfig.builder()
            .parallelism(8)
            .checkpointInterval(180000L)
            .checkpointTimeout(600000L)
            .checkpointDir(tempDir.resolve("checkpoints").toString())
            .build();

        configManager.updateFlinkConfig(newConfig);

        // 等待扩容完成
        assertThat(scalingLatch.await(5, TimeUnit.SECONDS)).isTrue();

        // 验证两个操作都成功
        assertThat(scalingCompleted.get()).isTrue();
        assertThat(configManager.getCurrentFlinkConfig().getParallelism()).isEqualTo(8);
    }

    /**
     * 测试多次故障切换
     * 验证系统能够处理多次JobManager故障
     */
    @Test
    void testMultipleFailovers() {
        // 第一次故障切换
        boolean firstFailover = failoverManager.recordFailure("primary");
        assertThat(firstFailover).isTrue();
        assertThat(failoverManager.getCurrentLeader()).isEqualTo("standby");

        // 重置状态
        failoverManager.reset();

        // 第二次故障切换
        boolean secondFailover = failoverManager.recordFailure("primary");
        assertThat(secondFailover).isTrue();
        assertThat(failoverManager.getCurrentLeader()).isEqualTo("standby");
    }

    /**
     * 测试扩容后缩容
     * 需求 6.2: THE System SHALL 支持动态增加TaskManager实例
     * 需求 6.7: WHEN 缩容操作 THEN THE System SHALL 完成当前处理任务后再释放资源
     */
    @Test
    void testScaleOutThenScaleIn() {
        // 先扩容
        boolean scaleOutResult = scalingManager.scaleOutTaskManagers(2);
        assertThat(scaleOutResult).isTrue();

        // 再缩容
        boolean scaleInResult = scalingManager.scaleInTaskManagers(1, 300000L);
        assertThat(scaleInResult).isTrue();
    }

    /**
     * 测试配置更新监听器
     * 验证配置更新时监听器被正确触发
     */
    @Test
    void testConfigUpdateListener() throws InterruptedException {
        CountDownLatch listenerLatch = new CountDownLatch(1);
        AtomicInteger oldParallelism = new AtomicInteger(0);
        AtomicInteger newParallelism = new AtomicInteger(0);

        // 注册监听器
        configManager.addConfigChangeListener(new ConfigurationUpdateManager.ConfigChangeListener() {
            @Override
            public void onFlinkConfigChanged(FlinkConfig oldConfig, FlinkConfig newConfig) {
                oldParallelism.set(oldConfig.getParallelism());
                newParallelism.set(newConfig.getParallelism());
                listenerLatch.countDown();
            }
        });

        // 更新配置
        FlinkConfig newConfig = FlinkConfig.builder()
            .parallelism(8)
            .checkpointInterval(180000L)
            .checkpointTimeout(600000L)
            .checkpointDir(tempDir.resolve("checkpoints").toString())
            .build();

        configManager.updateFlinkConfig(newConfig);

        // 等待监听器被调用
        assertThat(listenerLatch.await(1, TimeUnit.SECONDS)).isTrue();

        // 验证监听器收到了正确的值
        assertThat(oldParallelism.get()).isEqualTo(4);
        assertThat(newParallelism.get()).isEqualTo(8);
    }

    /**
     * 测试HA配置验证
     * 验证HA配置的有效性检查
     */
    @Test
    void testHAConfigValidation() {
        // 有效配置应该通过验证
        assertThatCode(() -> haConfig.validate()).doesNotThrowAnyException();

        // 验证JobManager数量至少为2
        assertThat(haConfig.getJobManagerCount()).isGreaterThanOrEqualTo(2);

        // 验证故障切换超时时间
        assertThat(haConfig.getFailoverTimeout()).isLessThanOrEqualTo(30000L);
    }

    /**
     * 测试HA配置器
     * 验证HA配置能够正确应用到Flink配置中
     */
    @Test
    void testHAConfigurator() {
        HighAvailabilityConfigurator configurator = new HighAvailabilityConfigurator(haConfig);

        // 验证HA已启用
        assertThat(configurator.isHAEnabled()).isTrue();

        // 验证JobManager数量
        assertThat(configurator.getJobManagerCount()).isEqualTo(2);

        // 验证故障切换超时
        assertThat(configurator.getFailoverTimeout()).isEqualTo(30000L);
    }

    /**
     * 测试并发场景：同时进行故障切换、扩容和配置更新
     * 验证系统在高并发场景下的稳定性
     */
    @Test
    void testConcurrentOperations() throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(3);
        AtomicBoolean failoverSuccess = new AtomicBoolean(false);
        AtomicBoolean scalingSuccess = new AtomicBoolean(false);
        AtomicBoolean configUpdateSuccess = new AtomicBoolean(false);

        // 故障切换线程
        Thread failoverThread = new Thread(() -> {
            try {
                startLatch.await();
                boolean result = failoverManager.recordFailure("primary");
                failoverSuccess.set(result);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });

        // 扩容线程
        Thread scalingThread = new Thread(() -> {
            try {
                startLatch.await();
                boolean result = scalingManager.scaleOutTaskManagers(2);
                scalingSuccess.set(result);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });

        // 配置更新线程
        Thread configThread = new Thread(() -> {
            try {
                startLatch.await();
                FlinkConfig newConfig = FlinkConfig.builder()
                    .parallelism(8)
                    .checkpointInterval(180000L)
                    .checkpointTimeout(600000L)
                    .checkpointDir(tempDir.resolve("checkpoints").toString())
                    .build();
                configManager.updateFlinkConfig(newConfig);
                configUpdateSuccess.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });

        // 启动所有线程
        failoverThread.start();
        scalingThread.start();
        configThread.start();

        // 同时开始所有操作
        startLatch.countDown();

        // 等待所有操作完成
        assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();

        // 验证所有操作都成功
        assertThat(failoverSuccess.get()).isTrue();
        assertThat(scalingSuccess.get()).isTrue();
        assertThat(configUpdateSuccess.get()).isTrue();
    }

    /**
     * 测试故障切换时间要求
     * 需求 5.3: WHEN 主JobManager失败 THEN THE System SHALL 在30秒内切换到备用JobManager
     */
    @Test
    void testFailoverTimeRequirement() {
        long startTime = System.currentTimeMillis();
        boolean success = failoverManager.recordFailure("primary");
        long duration = System.currentTimeMillis() - startTime;

        // 验证故障切换成功且在30秒内完成
        assertThat(success).isTrue();
        assertThat(duration).isLessThanOrEqualTo(30000L);
    }

    /**
     * 测试优雅缩容的超时时间
     * 需求 6.7: WHEN 缩容操作 THEN THE System SHALL 完成当前处理任务后再释放资源
     */
    @Test
    void testGracefulScaleInTimeout() {
        long drainTimeout = 300000L; // 5分钟
        long startTime = System.currentTimeMillis();

        boolean result = scalingManager.scaleInTaskManagers(1, drainTimeout);

        long duration = System.currentTimeMillis() - startTime;

        // 验证缩容成功
        assertThat(result).isTrue();

        // 验证操作在合理时间内完成（应该远小于超时时间）
        assertThat(duration).isLessThan(drainTimeout);
    }

    /**
     * 测试零停机配置更新
     * 需求 5.7: THE System SHALL 支持零停机时间的配置更新
     */
    @Test
    void testZeroDowntimeConfigUpdateWithDataProcessing() throws InterruptedException {
        AtomicInteger processedCount = new AtomicInteger(0);
        CountDownLatch processingLatch = new CountDownLatch(1);
        AtomicBoolean processingInterrupted = new AtomicBoolean(false);

        // 模拟数据处理线程
        Thread processingThread = new Thread(() -> {
            try {
                processingLatch.await();

                // 持续处理数据
                for (int i = 0; i < 100; i++) {
                    FlinkConfig config = configManager.getCurrentFlinkConfig();
                    // 模拟使用配置处理数据
                    processedCount.incrementAndGet();
                    Thread.sleep(10);
                }
            } catch (InterruptedException e) {
                processingInterrupted.set(true);
                Thread.currentThread().interrupt();
            }
        });

        processingThread.start();

        // 开始处理
        processingLatch.countDown();
        Thread.sleep(200);

        // 在处理过程中更新配置
        FlinkConfig newConfig = FlinkConfig.builder()
            .parallelism(8)
            .checkpointInterval(180000L)
            .checkpointTimeout(600000L)
            .checkpointDir(tempDir.resolve("checkpoints").toString())
            .build();

        configManager.updateFlinkConfig(newConfig);

        // 等待处理完成
        processingThread.join(5000);

        // 验证数据处理没有中断
        assertThat(processingInterrupted.get()).isFalse();
        assertThat(processedCount.get()).isEqualTo(100);

        // 验证配置已更新
        assertThat(configManager.getCurrentFlinkConfig().getParallelism()).isEqualTo(8);
    }
}
