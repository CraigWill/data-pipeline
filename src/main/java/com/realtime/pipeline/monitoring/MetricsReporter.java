package com.realtime.pipeline.monitoring;

import com.realtime.pipeline.flink.checkpoint.CheckpointListener;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.Gauge;
import org.apache.flink.metrics.Histogram;
import org.apache.flink.metrics.Meter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Metrics报告器
 * 提供便捷的方法来注册和更新常用的监控指标
 * 
 * 支持的指标类型:
 * - 吞吐量指标（每秒记录数）
 * - 延迟指标（端到端延迟P50/P99）
 * - Checkpoint指标（成功率、耗时）
 * - 反压指标
 * - 资源使用指标（CPU、内存）
 * 
 * 验证需求:
 * - 需求 7.2: THE System SHALL 记录每秒处理的记录数
 * - 需求 7.3: THE System SHALL 记录端到端的数据延迟
 * - 需求 7.4: THE System SHALL 记录Checkpoint的成功率和耗时
 * - 需求 7.5: THE System SHALL 记录Backpressure指标
 */
public class MetricsReporter implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(MetricsReporter.class);

    private final MonitoringService monitoringService;
    private final AtomicReference<CheckpointListener> checkpointListener = new AtomicReference<>();

    // 吞吐量指标
    private Counter recordsInCounter;
    private Counter recordsOutCounter;
    private Counter failedRecordsCounter;
    private Meter recordsInRate;
    private Meter recordsOutRate;
    
    // 延迟指标 - 使用列表存储最近的延迟值用于计算百分位数
    private final AtomicLong latencySum = new AtomicLong(0);
    private final AtomicLong latencyCount = new AtomicLong(0);
    private final AtomicLong lastLatency = new AtomicLong(0);
    private final List<Long> recentLatencies = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_LATENCY_SAMPLES = 1000; // 保留最近1000个样本用于百分位数计算
    
    // 反压指标
    private final AtomicReference<Double> backpressureLevel = new AtomicReference<>(0.0);
    
    // 延迟直方图
    private Histogram latencyHistogram;

    /**
     * 构造函数
     * @param monitoringService 监控服务
     */
    public MetricsReporter(MonitoringService monitoringService) {
        if (monitoringService == null) {
            throw new IllegalArgumentException("MonitoringService cannot be null");
        }
        this.monitoringService = monitoringService;
    }
    
    /**
     * 设置Checkpoint监听器
     * @param listener Checkpoint监听器
     */
    public void setCheckpointListener(CheckpointListener listener) {
        this.checkpointListener.set(listener);
        logger.info("CheckpointListener set for metrics reporting");
    }

    /**
     * 注册吞吐量指标
     * 需求 7.2: THE System SHALL 记录每秒处理的记录数
     */
    public void registerThroughputMetrics() {
        if (!monitoringService.isInitialized()) {
            logger.warn("MonitoringService not initialized, skipping throughput metrics registration");
            return;
        }

        // 输入记录计数器
        recordsInCounter = monitoringService.registerCounter("throughput", "records.in");
        
        // 输出记录计数器
        recordsOutCounter = monitoringService.registerCounter("throughput", "records.out");
        
        // 失败记录计数器
        failedRecordsCounter = monitoringService.registerCounter("throughput", "records.failed");
        
        // 输入速率（记录/秒）- 使用简单的Meter实现
        recordsInRate = new SimpleMeter();
        monitoringService.registerMeter("throughput", "records.in.rate", recordsInRate);
        
        // 输出速率（记录/秒）- 使用简单的Meter实现
        recordsOutRate = new SimpleMeter();
        monitoringService.registerMeter("throughput", "records.out.rate", recordsOutRate);
        
        logger.info("Throughput metrics registered");
    }

    /**
     * 注册延迟指标
     * 需求 7.3: THE System SHALL 记录端到端的数据延迟
     */
    public void registerLatencyMetrics() {
        if (!monitoringService.isInitialized()) {
            logger.warn("MonitoringService not initialized, skipping latency metrics registration");
            return;
        }

        // 平均延迟
        monitoringService.registerGauge("latency", "average", (Gauge<Long>) () -> {
            long count = latencyCount.get();
            return count > 0 ? latencySum.get() / count : 0L;
        });
        
        // 最后一次延迟
        monitoringService.registerGauge("latency", "last", (Gauge<Long>) lastLatency::get);
        
        // P50延迟（中位数）
        monitoringService.registerGauge("latency", "p50", (Gauge<Long>) this::calculateP50Latency);
        
        // P99延迟
        monitoringService.registerGauge("latency", "p99", (Gauge<Long>) this::calculateP99Latency);
        
        // 延迟直方图（用于P50、P99计算）
        latencyHistogram = new LatencyHistogram();
        monitoringService.registerHistogram("latency", "histogram", latencyHistogram);
        
        logger.info("Latency metrics registered (average, last, p50, p99, histogram)");
    }
    
    /**
     * 注册Checkpoint指标
     * 需求 7.4: THE System SHALL 记录Checkpoint的成功率和耗时
     */
    public void registerCheckpointMetrics() {
        if (!monitoringService.isInitialized()) {
            logger.warn("MonitoringService not initialized, skipping checkpoint metrics registration");
            return;
        }

        // Checkpoint成功率
        monitoringService.registerGauge("checkpoint", "success.rate", (Gauge<Double>) () -> {
            CheckpointListener listener = checkpointListener.get();
            return listener != null ? listener.getSuccessRate() : 0.0;
        });
        
        // Checkpoint失败率
        monitoringService.registerGauge("checkpoint", "failure.rate", (Gauge<Double>) () -> {
            CheckpointListener listener = checkpointListener.get();
            return listener != null ? listener.getFailureRate() : 0.0;
        });
        
        // 平均Checkpoint耗时
        monitoringService.registerGauge("checkpoint", "duration.average", (Gauge<Long>) () -> {
            CheckpointListener listener = checkpointListener.get();
            return listener != null ? listener.getAverageCheckpointDuration() : 0L;
        });
        
        // 最后一次Checkpoint耗时
        monitoringService.registerGauge("checkpoint", "duration.last", (Gauge<Long>) () -> {
            CheckpointListener listener = checkpointListener.get();
            return listener != null ? listener.getLastCheckpointDuration() : 0L;
        });
        
        // Checkpoint总数
        monitoringService.registerGauge("checkpoint", "total.count", (Gauge<Long>) () -> {
            CheckpointListener listener = checkpointListener.get();
            return listener != null ? listener.getTotalCheckpoints() : 0L;
        });
        
        // Checkpoint成功数
        monitoringService.registerGauge("checkpoint", "success.count", (Gauge<Long>) () -> {
            CheckpointListener listener = checkpointListener.get();
            return listener != null ? listener.getSuccessfulCheckpoints() : 0L;
        });
        
        // Checkpoint失败数
        monitoringService.registerGauge("checkpoint", "failure.count", (Gauge<Long>) () -> {
            CheckpointListener listener = checkpointListener.get();
            return listener != null ? listener.getFailedCheckpoints() : 0L;
        });
        
        logger.info("Checkpoint metrics registered (success rate, failure rate, duration, counts)");
    }

    /**
     * 注册反压指标
     * 需求 7.5: THE System SHALL 记录Backpressure指标
     */
    public void registerBackpressureMetrics() {
        if (!monitoringService.isInitialized()) {
            logger.warn("MonitoringService not initialized, skipping backpressure metrics registration");
            return;
        }

        // 反压级别（0-1之间的值）
        monitoringService.registerGauge("backpressure", "level", (Gauge<Double>) backpressureLevel::get);
        
        // 反压状态（文本描述）
        monitoringService.registerGauge("backpressure", "status", (Gauge<String>) () -> {
            double level = backpressureLevel.get();
            if (level < 0.3) return "OK";
            if (level < 0.6) return "LOW";
            if (level < 0.8) return "MEDIUM";
            return "HIGH";
        });
        
        logger.info("Backpressure metrics registered (level, status)");
    }
    
    /**
     * 更新反压级别
     * @param level 反压级别（0-1之间）
     */
    public void updateBackpressureLevel(double level) {
        if (level < 0.0 || level > 1.0) {
            logger.warn("Invalid backpressure level: {}. Must be between 0.0 and 1.0", level);
            return;
        }
        backpressureLevel.set(level);
        monitoringService.updateMetricValue("backpressure.level", level);
        
        if (level > 0.8) {
            logger.warn("High backpressure detected: {}", level);
        }
    }
    
    /**
     * 获取当前反压级别
     * @return 反压级别（0-1之间）
     */
    public double getBackpressureLevel() {
        return backpressureLevel.get();
    }

    /**
     * 注册资源使用指标
     */
    public void registerResourceMetrics() {
        if (!monitoringService.isInitialized()) {
            logger.warn("MonitoringService not initialized, skipping resource metrics registration");
            return;
        }

        // CPU使用率
        monitoringService.registerGauge("resource", "cpu.usage", (Gauge<Double>) this::getCpuUsage);
        
        // 内存使用率
        monitoringService.registerGauge("resource", "memory.usage", (Gauge<Double>) this::getMemoryUsage);
        
        // 堆内存使用量（字节）
        monitoringService.registerGauge("resource", "heap.used", (Gauge<Long>) () -> {
            Runtime runtime = Runtime.getRuntime();
            return runtime.totalMemory() - runtime.freeMemory();
        });
        
        // 堆内存总量（字节）
        monitoringService.registerGauge("resource", "heap.total", (Gauge<Long>) () -> {
            Runtime runtime = Runtime.getRuntime();
            return runtime.totalMemory();
        });
        
        // 堆内存最大值（字节）
        monitoringService.registerGauge("resource", "heap.max", (Gauge<Long>) () -> {
            Runtime runtime = Runtime.getRuntime();
            return runtime.maxMemory();
        });
        
        logger.info("Resource metrics registered (cpu, memory, heap)");
    }
    
    /**
     * 获取CPU使用率
     * @return CPU使用率（0-1之间）
     */
    private double getCpuUsage() {
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sunOsBean = 
                    (com.sun.management.OperatingSystemMXBean) osBean;
                double cpuLoad = sunOsBean.getProcessCpuLoad();
                return cpuLoad >= 0 ? cpuLoad : 0.0;
            }
            return 0.0;
        } catch (Exception e) {
            logger.debug("Failed to get CPU usage", e);
            return 0.0;
        }
    }
    
    /**
     * 获取内存使用率
     * @return 内存使用率（0-1之间）
     */
    private double getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        return totalMemory > 0 ? (double) (totalMemory - freeMemory) / totalMemory : 0.0;
    }

    /**
     * 记录输入记录
     */
    public void recordInput() {
        if (recordsInCounter != null) {
            recordsInCounter.inc();
        }
        if (recordsInRate != null) {
            recordsInRate.markEvent();
        }
    }

    /**
     * 记录输入记录（批量）
     * @param count 记录数量
     */
    public void recordInput(long count) {
        if (recordsInCounter != null) {
            recordsInCounter.inc(count);
        }
        if (recordsInRate != null) {
            recordsInRate.markEvent(count);
        }
    }

    /**
     * 记录输出记录
     */
    public void recordOutput() {
        if (recordsOutCounter != null) {
            recordsOutCounter.inc();
        }
        if (recordsOutRate != null) {
            recordsOutRate.markEvent();
        }
    }

    /**
     * 记录输出记录（批量）
     * @param count 记录数量
     */
    public void recordOutput(long count) {
        if (recordsOutCounter != null) {
            recordsOutCounter.inc(count);
        }
        if (recordsOutRate != null) {
            recordsOutRate.markEvent(count);
        }
    }

    /**
     * 记录失败记录
     */
    public void recordFailure() {
        if (failedRecordsCounter != null) {
            failedRecordsCounter.inc();
        }
    }

    /**
     * 记录失败记录（批量）
     * @param count 记录数量
     */
    public void recordFailure(long count) {
        if (failedRecordsCounter != null) {
            failedRecordsCounter.inc(count);
        }
    }

    /**
     * 记录延迟
     * @param latencyMs 延迟（毫秒）
     */
    public void recordLatency(long latencyMs) {
        latencySum.addAndGet(latencyMs);
        latencyCount.incrementAndGet();
        lastLatency.set(latencyMs);
        
        // 添加到最近延迟列表用于百分位数计算
        synchronized (recentLatencies) {
            recentLatencies.add(latencyMs);
            // 保持列表大小在限制内
            if (recentLatencies.size() > MAX_LATENCY_SAMPLES) {
                recentLatencies.remove(0);
            }
        }
        
        // 更新直方图
        if (latencyHistogram != null) {
            latencyHistogram.update(latencyMs);
        }
        
        // 更新监控服务中的值
        monitoringService.updateMetricValue("latency.last", latencyMs);
    }
    
    /**
     * 计算P50延迟（中位数）
     * @return P50延迟（毫秒）
     */
    private long calculateP50Latency() {
        return calculatePercentile(0.50);
    }
    
    /**
     * 计算P99延迟
     * @return P99延迟（毫秒）
     */
    private long calculateP99Latency() {
        return calculatePercentile(0.99);
    }
    
    /**
     * 计算指定百分位数的延迟
     * @param percentile 百分位数（0-1之间）
     * @return 延迟值（毫秒）
     */
    private long calculatePercentile(double percentile) {
        synchronized (recentLatencies) {
            if (recentLatencies.isEmpty()) {
                return 0L;
            }
            
            List<Long> sorted = new ArrayList<>(recentLatencies);
            Collections.sort(sorted);
            
            int index = (int) Math.ceil(percentile * sorted.size()) - 1;
            index = Math.max(0, Math.min(index, sorted.size() - 1));
            
            return sorted.get(index);
        }
    }

    /**
     * 获取平均延迟
     * @return 平均延迟（毫秒）
     */
    public long getAverageLatency() {
        long count = latencyCount.get();
        return count > 0 ? latencySum.get() / count : 0L;
    }

    /**
     * 获取最后一次延迟
     * @return 最后一次延迟（毫秒）
     */
    public long getLastLatency() {
        return lastLatency.get();
    }

    /**
     * 重置延迟统计
     */
    public void resetLatencyStats() {
        latencySum.set(0);
        latencyCount.set(0);
        lastLatency.set(0);
        synchronized (recentLatencies) {
            recentLatencies.clear();
        }
    }
    
    /**
     * 获取Checkpoint监听器
     * @return Checkpoint监听器
     */
    public CheckpointListener getCheckpointListener() {
        return checkpointListener.get();
    }
    
    /**
     * 注册所有指标
     * 便捷方法，一次性注册所有类型的指标
     */
    public void registerAllMetrics() {
        registerThroughputMetrics();
        registerLatencyMetrics();
        registerCheckpointMetrics();
        registerBackpressureMetrics();
        registerResourceMetrics();
        logger.info("All metrics registered successfully");
    }
    
    /**
     * 获取指标摘要
     * @return 指标摘要字符串
     */
    public String getMetricsSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Metrics Reporter Summary:\n");
        sb.append(String.format("  Records In: %d\n", recordsInCounter != null ? recordsInCounter.getCount() : 0));
        sb.append(String.format("  Records Out: %d\n", recordsOutCounter != null ? recordsOutCounter.getCount() : 0));
        sb.append(String.format("  Failed Records: %d\n", failedRecordsCounter != null ? failedRecordsCounter.getCount() : 0));
        sb.append(String.format("  Average Latency: %d ms\n", getAverageLatency()));
        sb.append(String.format("  Last Latency: %d ms\n", getLastLatency()));
        sb.append(String.format("  P50 Latency: %d ms\n", calculateP50Latency()));
        sb.append(String.format("  P99 Latency: %d ms\n", calculateP99Latency()));
        sb.append(String.format("  Backpressure Level: %.2f\n", getBackpressureLevel()));
        sb.append(String.format("  CPU Usage: %.2f%%\n", getCpuUsage() * 100));
        sb.append(String.format("  Memory Usage: %.2f%%\n", getMemoryUsage() * 100));
        
        CheckpointListener listener = checkpointListener.get();
        if (listener != null) {
            sb.append(String.format("  Checkpoint Success Rate: %.2f%%\n", listener.getSuccessRate()));
            sb.append(String.format("  Checkpoint Failure Rate: %.2f%%\n", listener.getFailureRate()));
            sb.append(String.format("  Checkpoint Avg Duration: %d ms\n", listener.getAverageCheckpointDuration()));
        }
        
        return sb.toString();
    }

    /**
     * 获取监控服务
     * @return 监控服务
     */
    public MonitoringService getMonitoringService() {
        return monitoringService;
    }

    /**
     * 简单的Meter实现
     */
    private static class SimpleMeter implements Meter {
        private final AtomicLong count = new AtomicLong(0);
        private volatile long lastUpdateTime = System.currentTimeMillis();
        private volatile double rate = 0.0;

        @Override
        public void markEvent() {
            count.incrementAndGet();
            updateRate();
        }

        @Override
        public void markEvent(long n) {
            count.addAndGet(n);
            updateRate();
        }

        @Override
        public double getRate() {
            return rate;
        }

        @Override
        public long getCount() {
            return count.get();
        }

        private void updateRate() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastUpdateTime;
            if (elapsed > 0) {
                rate = (count.get() * 1000.0) / elapsed;
            }
        }
    }

    /**
     * 简单的Histogram实现
     * 用于延迟指标的统计
     */
    private static class LatencyHistogram implements Histogram {
        private final AtomicLong count = new AtomicLong(0);
        private final AtomicLong sum = new AtomicLong(0);
        private final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong max = new AtomicLong(Long.MIN_VALUE);
        private final List<Long> samples = Collections.synchronizedList(new ArrayList<>());
        private static final int MAX_SAMPLES = 1000;

        @Override
        public void update(long value) {
            count.incrementAndGet();
            sum.addAndGet(value);
            
            // Update min
            long currentMin;
            do {
                currentMin = min.get();
                if (value >= currentMin) break;
            } while (!min.compareAndSet(currentMin, value));
            
            // Update max
            long currentMax;
            do {
                currentMax = max.get();
                if (value <= currentMax) break;
            } while (!max.compareAndSet(currentMax, value));
            
            // Store sample for percentile calculation
            synchronized (samples) {
                samples.add(value);
                if (samples.size() > MAX_SAMPLES) {
                    samples.remove(0);
                }
            }
        }

        @Override
        public long getCount() {
            return count.get();
        }

        @Override
        public org.apache.flink.metrics.HistogramStatistics getStatistics() {
            return new org.apache.flink.metrics.HistogramStatistics() {
                @Override
                public double getQuantile(double quantile) {
                    synchronized (samples) {
                        if (samples.isEmpty()) {
                            return 0.0;
                        }
                        
                        List<Long> sorted = new ArrayList<>(samples);
                        Collections.sort(sorted);
                        
                        int index = (int) Math.ceil(quantile * sorted.size()) - 1;
                        index = Math.max(0, Math.min(index, sorted.size() - 1));
                        
                        return sorted.get(index).doubleValue();
                    }
                }

                @Override
                public long[] getValues() {
                    synchronized (samples) {
                        long[] values = new long[samples.size()];
                        for (int i = 0; i < samples.size(); i++) {
                            values[i] = samples.get(i);
                        }
                        return values;
                    }
                }

                @Override
                public int size() {
                    return samples.size();
                }

                @Override
                public double getMean() {
                    long cnt = count.get();
                    return cnt > 0 ? (double) sum.get() / cnt : 0.0;
                }

                @Override
                public double getStdDev() {
                    // 简化实现：不计算标准差
                    return 0.0;
                }

                @Override
                public long getMax() {
                    long maxValue = max.get();
                    return maxValue == Long.MIN_VALUE ? 0 : maxValue;
                }

                @Override
                public long getMin() {
                    long minValue = min.get();
                    return minValue == Long.MAX_VALUE ? 0 : minValue;
                }
            };
        }
    }
}
