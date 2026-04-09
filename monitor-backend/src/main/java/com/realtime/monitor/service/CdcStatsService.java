package com.realtime.monitor.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CDC 实时统计服务
 * 基于 CDC 事件流进行实时统计，每天0点自动清空
 * 通过定期扫描 CSV 文件来更新统计
 */
@Slf4j
@Service
public class CdcStatsService {
    
    @Value("${output.path:./output/cdc}")
    private String outputPath;
    
    // 当前日期
    private volatile LocalDate currentDate = LocalDate.now();
    
    // 今日总事件数
    private final AtomicLong todayTotal = new AtomicLong(0);
    
    // 今日 INSERT 事件数
    private final AtomicLong todayInsert = new AtomicLong(0);
    
    // 今日 UPDATE 事件数
    private final AtomicLong todayUpdate = new AtomicLong(0);
    
    // 今日 DELETE 事件数
    private final AtomicLong todayDelete = new AtomicLong(0);
    
    // 按小时统计: hour(0-23) -> count
    private final AtomicLong[] hourlyStats = new AtomicLong[24];
    
    // 按表统计: tableName -> [count, fileCount]
    private final ConcurrentHashMap<String, long[]> tableStats = new ConcurrentHashMap<>();
    
    // 已处理的文件及其行数: filePath -> lineCount
    private final ConcurrentHashMap<String, Long> processedFiles = new ConcurrentHashMap<>();
    
    // 事件速率
    private volatile double eventsPerSecond = 0;
    private volatile long lastTotal = 0;
    private volatile long lastTimestamp = System.currentTimeMillis();
    
    // 最近一分钟的事件计数（用于计算实时速率）
    private final AtomicLong recentEventsCount = new AtomicLong(0);
    private volatile long recentEventsTimestamp = System.currentTimeMillis();
    
    // SSE 订阅者列表
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    
    public CdcStatsService() {
        // 初始化小时统计
        for (int i = 0; i < 24; i++) {
            hourlyStats[i] = new AtomicLong(0);
        }
        
        // 启动时执行一次全量重算
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(5000); // 等待5秒让系统完全启动
                fullRecalculate();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
    
    /**
     * 定期扫描 CSV 文件更新统计（每5秒）
     */
    /**
     * 全量重算统计（仅在必要时调用，如系统启动或数据不一致时）
     */
    public void fullRecalculate() {
        log.info("开始全量重算CDC统计...");
        LocalDate today = LocalDate.now();
        String todayStr = today.toString();

        File outputDir = new File(outputPath);
        if (!outputDir.exists()) {
            return;
        }

        // 全量重算：先清空今日统计，再重新扫描所有现存文件
        todayTotal.set(0);
        todayInsert.set(0);
        todayUpdate.set(0);
        todayDelete.set(0);
        for (AtomicLong h : hourlyStats) h.set(0);
        tableStats.clear();
        processedFiles.clear();

        File[] dateDirs = outputDir.listFiles(File::isDirectory);
        if (dateDirs == null) return;

        int fileCount = 0;
        long totalLines = 0;

        for (File dateDir : dateDirs) {
            String dirName = dateDir.getName();
            if (!dirName.startsWith(todayStr)) continue;
            if (!dirName.matches("\\d{4}-\\d{2}-\\d{2}--\\d{2}")) continue;

            int hour = Integer.parseInt(dirName.substring(12, 14));

            File[] csvFiles = dateDir.listFiles((d, name) -> name.endsWith(".csv"));
            if (csvFiles == null) continue;

            for (File csvFile : csvFiles) {
                long[] ops = countOpLines(csvFile);
                long total = ops[3];
                if (total <= 0) continue;
                processedFiles.put(csvFile.getAbsolutePath(), csvFile.length());
                updateStats(csvFile, hour, ops);
                fileCount++;
                totalLines += total;
            }
        }

        calculateRate();
        log.info("全量重算完成: 处理 {} 个文件，总计 {} 行数据", fileCount, totalLines);
    }

    @Scheduled(fixedRate = 10000)  // 改为10秒，减少频率
    public void scanAndUpdateStats() {
        LocalDate today = LocalDate.now();

        // 日期变了则重置
        if (!today.equals(currentDate)) {
            synchronized (this) {
                if (!today.equals(currentDate)) {
                    resetStats();
                    currentDate = today;
                    log.info("CDC 统计已重置，新日期: {}", today);
                }
            }
        }

        String todayStr = today.toString();

        File outputDir = new File(outputPath);
        if (!outputDir.exists()) {
            return;
        }

        // 增量统计：只处理新文件或变化的文件
        File[] dateDirs = outputDir.listFiles(File::isDirectory);
        if (dateDirs == null) return;

        for (File dateDir : dateDirs) {
            String dirName = dateDir.getName();
            if (!dirName.startsWith(todayStr)) continue;
            if (!dirName.matches("\\d{4}-\\d{2}-\\d{2}--\\d{2}")) continue;

            int hour = Integer.parseInt(dirName.substring(12, 14));

            File[] csvFiles = dateDir.listFiles((d, name) -> name.endsWith(".csv"));
            if (csvFiles == null) continue;

            for (File csvFile : csvFiles) {
                String filePath = csvFile.getAbsolutePath();
                long currentSize = csvFile.length();
                
                // 检查文件是否是新的或已变化
                Long lastSize = processedFiles.get(filePath);
                if (lastSize == null) {
                    // 新文件：全量统计
                    long[] ops = countOpLines(csvFile);
                    long total = ops[3];
                    if (total > 0) {
                        processedFiles.put(filePath, currentSize);
                        updateStats(csvFile, hour, ops);
                        log.debug("新文件统计: {} - 总行数={}", csvFile.getName(), total);
                    }
                } else if (currentSize > lastSize) {
                    // 文件增长：只统计新增部分（简化处理，按行数估算）
                    long estimatedNewLines = (currentSize - lastSize) / 100; // 假设每行约100字节
                    if (estimatedNewLines > 0) {
                        processedFiles.put(filePath, currentSize);
                        updateStatsIncremental(csvFile, hour, estimatedNewLines);
                        log.debug("增量统计: {} - 估算新增 {} 行", csvFile.getName(), estimatedNewLines);
                    }
                }
            }
        }

        calculateRate();
    }
    
    /**
     * 更新统计（新文件），ops = [insert, update, delete, total]
     */
    private void updateStats(File csvFile, int hour, long[] ops) {
        String tableName = extractTableName(csvFile.getName());
        long total = ops[3];

        todayTotal.addAndGet(total);
        todayInsert.addAndGet(ops[0]);
        todayUpdate.addAndGet(ops[1]);
        todayDelete.addAndGet(ops[2]);
        hourlyStats[hour].addAndGet(total);

        tableStats.compute(tableName, (k, v) -> {
            if (v == null) return new long[]{total, 1};
            v[0] += total;
            v[1]++;
            return v;
        });

        log.debug("新文件统计: {} - insert={} update={} delete={}", csvFile.getName(), ops[0], ops[1], ops[2]);
    }
    
    /**
     * 增量更新统计
     */
    private void updateStatsIncremental(File csvFile, int hour, long newLines) {
        String tableName = extractTableName(csvFile.getName());
        
        todayTotal.addAndGet(newLines);
        todayInsert.addAndGet(newLines);
        hourlyStats[hour].addAndGet(newLines);
        
        tableStats.compute(tableName, (k, v) -> {
            if (v == null) {
                return new long[]{newLines, 1};
            }
            v[0] += newLines;
            return v;
        });
        
        log.debug("增量统计: {} - 新增 {} 行", csvFile.getName(), newLines);
    }
    
    /**
     * 按操作类型统计行数，返回 [insert, update, delete, total]
     * CSV 第2列为 op_type（INSERT/UPDATE/DELETE）
     */
    private long[] countOpLines(File file) {
        long insert = 0, update = 0, delete = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file), 65536)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                int first = line.indexOf(',');
                if (first < 0) { insert++; continue; }
                int second = line.indexOf(',', first + 1);
                String op = (second < 0
                    ? line.substring(first + 1)
                    : line.substring(first + 1, second)).trim().toUpperCase();
                if (op.equals("DELETE") || op.equals("D")) {
                    delete++;
                } else if (op.startsWith("UPDATE") || op.equals("U")) {
                    update++;
                } else {
                    insert++;
                }
            }
        } catch (Exception e) {
            log.error("统计文件行数失败: {}", file.getName(), e);
        }
        return new long[]{insert, update, delete, insert + update + delete};
    }
    
    /**
     * 从文件名提取表名
     */
    private String extractTableName(String fileName) {
        if (fileName.contains("_")) {
            String[] parts = fileName.split("_");
            StringBuilder tableName = new StringBuilder();
            for (String part : parts) {
                if (part.matches("\\d{8}")) break;
                if (tableName.length() > 0) tableName.append("_");
                tableName.append(part);
            }
            return tableName.toString();
        }
        return fileName.replace(".csv", "");
    }
    
    /**
     * 计算事件速率
     */
    private void calculateRate() {
        long now = System.currentTimeMillis();
        long currentTotal = todayTotal.get();
        long elapsed = now - lastTimestamp;
        
        boolean hasNewData = false;
        
        if (elapsed > 0) {
            long diff = currentTotal - lastTotal;
            if (diff > 0) {
                eventsPerSecond = (double) diff / (elapsed / 1000.0);
                // 更新最近事件计数
                recentEventsCount.addAndGet(diff);
                recentEventsTimestamp = now;
                hasNewData = true;
            } else {
                // 没有新数据，速率逐渐衰减
                eventsPerSecond = eventsPerSecond * 0.5;
                if (eventsPerSecond < 0.1) {
                    eventsPerSecond = 0;
                }
            }
        }
        
        lastTotal = currentTotal;
        lastTimestamp = now;
        
        // 有新数据时推送给所有订阅者
        if (hasNewData) {
            broadcastStats();
        }
    }
    
    /**
     * 获取当前实时速率（基于最近的事件）
     */
    private double getCurrentRate() {
        long now = System.currentTimeMillis();
        long elapsed = now - recentEventsTimestamp;
        
        // 如果超过10秒没有新事件，返回0
        if (elapsed > 10000) {
            return 0;
        }
        
        return eventsPerSecond;
    }
    
    /**
     * 检查是否需要重置（新的一天）
     */
    private void checkAndResetIfNewDay() {
        LocalDate today = LocalDate.now();
        if (!today.equals(currentDate)) {
            synchronized (this) {
                if (!today.equals(currentDate)) {
                    resetStats();
                    currentDate = today;
                    log.info("CDC 统计已重置，新日期: {}", today);
                }
            }
        }
    }
    
    /**
     * 重置所有统计
     */
    private void resetStats() {
        todayTotal.set(0);
        todayInsert.set(0);
        todayUpdate.set(0);
        todayDelete.set(0);
        
        for (int i = 0; i < 24; i++) {
            hourlyStats[i].set(0);
        }
        
        tableStats.clear();
        processedFiles.clear();
        eventsPerSecond = 0;
        lastTotal = 0;
    }
    
    /**
     * 手动记录事件（供外部调用）
     */
    public void recordEvent(String tableName, String eventType) {
        checkAndResetIfNewDay();
        
        todayTotal.incrementAndGet();
        
        if (eventType != null) {
            String type = eventType.toUpperCase();
            if (type.startsWith("UPDATE") || type.equals("U")) {
                todayUpdate.incrementAndGet();
            } else if (type.equals("DELETE") || type.equals("D")) {
                todayDelete.incrementAndGet();
            } else {
                todayInsert.incrementAndGet();
            }
        } else {
            todayInsert.incrementAndGet();
        }
        
        int hour = LocalDateTime.now().getHour();
        hourlyStats[hour].incrementAndGet();
        
        if (tableName != null && !tableName.isEmpty()) {
            tableStats.compute(tableName, (k, v) -> {
                if (v == null) return new long[]{1, 1};
                v[0]++;
                return v;
            });
        }
    }
    
    /**
     * 批量记录事件
     */
    public void recordEvents(String tableName, String eventType, long count) {
        checkAndResetIfNewDay();
        
        todayTotal.addAndGet(count);
        todayInsert.addAndGet(count);
        
        int hour = LocalDateTime.now().getHour();
        hourlyStats[hour].addAndGet(count);
        
        if (tableName != null && !tableName.isEmpty()) {
            tableStats.compute(tableName, (k, v) -> {
                if (v == null) return new long[]{count, 1};
                v[0] += count;
                v[1]++;
                return v;
            });
        }
        
        // 更新速率计算
        recentEventsCount.addAndGet(count);
        recentEventsTimestamp = System.currentTimeMillis();
        eventsPerSecond = count; // 简单设置为本次记录的数量
    }
    
    /**
     * 获取今日统计
     */
    public Map<String, Object> getTodayStats() {
        checkAndResetIfNewDay();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("date", currentDate.toString());
        stats.put("totalEvents", todayTotal.get());
        stats.put("insertEvents", todayInsert.get());
        stats.put("updateEvents", todayUpdate.get());
        stats.put("deleteEvents", todayDelete.get());
        stats.put("eventsPerSecond", Math.round(getCurrentRate() * 100) / 100.0);
        
        return stats;
    }
    
    /**
     * 获取24小时分布统计
     */
    public Map<String, Object> getHourlyStats(String table) {
        checkAndResetIfNewDay();
        
        List<String> labels = new ArrayList<>();
        List<Long> values = new ArrayList<>();
        
        for (int i = 0; i < 24; i++) {
            labels.add(String.format("%02d:00", i));
            values.add(hourlyStats[i].get());
        }
        
        // 表统计
        List<Map<String, Object>> tableList = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : tableStats.entrySet()) {
            Map<String, Object> t = new HashMap<>();
            t.put("name", entry.getKey());
            t.put("count", entry.getValue()[0]);
            t.put("fileCount", entry.getValue()[1]);
            tableList.add(t);
        }
        tableList.sort((a, b) -> Long.compare((Long)b.get("count"), (Long)a.get("count")));
        
        long total = todayTotal.get();
        
        // 如果指定了表，重新计算
        if (table != null && !table.isEmpty()) {
            long[] ts = tableStats.get(table);
            total = ts != null ? ts[0] : 0;
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("labels", labels);
        result.put("values", values);
        result.put("total", total);
        result.put("tables", tableList);
        result.put("date", currentDate.toString());
        result.put("selectedTable", table != null ? table : "");
        result.put("eventsPerSecond", Math.round(getCurrentRate() * 100) / 100.0);
        
        return result;
    }
    
    /**
     * 获取实时速率
     */
    public double getEventsPerSecond() {
        return eventsPerSecond;
    }
    
    /**
     * 获取今日总数
     */
    public long getTodayTotal() {
        checkAndResetIfNewDay();
        return todayTotal.get();
    }
    
    /**
     * 添加 SSE 订阅者
     */
    public void addEmitter(SseEmitter emitter) {
        emitters.add(emitter);
        log.info("新增 SSE 订阅者，当前订阅数: {}", emitters.size());
    }
    
    /**
     * 移除 SSE 订阅者
     */
    public void removeEmitter(SseEmitter emitter) {
        emitters.remove(emitter);
        log.info("移除 SSE 订阅者，当前订阅数: {}", emitters.size());
    }
    
    /**
     * 定期发送心跳保持长连接（每15秒）
     */
    @Scheduled(fixedRate = 15000)
    public void sendHeartbeat() {
        if (emitters.isEmpty()) {
            return;
        }
        
        List<SseEmitter> deadEmitters = new ArrayList<>();
        Map<String, Object> stats = getTodayStats();
        
        for (SseEmitter emitter : emitters) {
            try {
                // 发送心跳和最新统计
                emitter.send(SseEmitter.event()
                    .name("heartbeat")
                    .data(stats));
            } catch (Exception e) {
                log.debug("心跳发送失败，移除订阅者: {}", e.getMessage());
                deadEmitters.add(emitter);
            }
        }
        
        // 清理失效的订阅者
        if (!deadEmitters.isEmpty()) {
            emitters.removeAll(deadEmitters);
            log.info("清理 {} 个失效订阅者，剩余: {}", deadEmitters.size(), emitters.size());
        }
    }
    
    /**
     * 广播统计数据给所有订阅者
     */
    private void broadcastStats() {
        if (emitters.isEmpty()) {
            return;
        }
        
        Map<String, Object> stats = getTodayStats();
        List<SseEmitter> deadEmitters = new ArrayList<>();
        
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                    .name("stats")
                    .data(stats));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }
        
        // 清理失效的订阅者
        emitters.removeAll(deadEmitters);
    }
    
    /**
     * 获取当前订阅者数量
     */
    public int getEmitterCount() {
        return emitters.size();
    }
}
