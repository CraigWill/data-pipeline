package com.realtime.monitor.service;

import com.realtime.monitor.config.AppConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 输出文件监控服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutputFileService {
    
    private final AppConfig appConfig;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 获取输出文件统计信息
     */
    public Map<String, Object> getOutputStats() throws IOException {
        Path outputDir = Paths.get(appConfig.getOutputPath());
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_files", 0);
        stats.put("total_size", 0L);
        stats.put("tables", new HashMap<String, Object>());
        
        if (!Files.exists(outputDir)) {
            stats.put("total_size_mb", 0.0);
            return stats;
        }
        
        Map<String, Map<String, Object>> tables = new HashMap<>();
        int[] totalFiles = {0};
        long[] totalSize = {0L};
        
        try (Stream<Path> paths = Files.walk(outputDir)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".csv"))
                 .filter(p -> !p.getFileName().toString().startsWith("."))
                 .forEach(csvFile -> {
                     try {
                         String fileName = csvFile.getFileName().toString();
                         String[] parts = fileName.split("_");
                         
                         if (parts.length >= 3 && "IDS".equals(parts[0])) {
                             String tableName = parts[1];
                             
                             tables.computeIfAbsent(tableName, k -> {
                                 Map<String, Object> tableStats = new HashMap<>();
                                 tableStats.put("file_count", 0);
                                 tableStats.put("total_size", 0L);
                                 tableStats.put("latest_file", null);
                                 tableStats.put("latest_time", null);
                                 return tableStats;
                             });
                             
                             Map<String, Object> tableStats = tables.get(tableName);
                             BasicFileAttributes attrs = Files.readAttributes(csvFile, BasicFileAttributes.class);
                             long fileSize = attrs.size();
                             long fileMtime = attrs.lastModifiedTime().toMillis();
                             
                             tableStats.put("file_count", (int) tableStats.get("file_count") + 1);
                             tableStats.put("total_size", (long) tableStats.get("total_size") + fileSize);
                             
                             totalFiles[0]++;
                             totalSize[0] += fileSize;
                             
                             Long latestTime = (Long) tableStats.get("latest_time");
                             if (latestTime == null || fileMtime > latestTime) {
                                 tableStats.put("latest_file", fileName);
                                 tableStats.put("latest_time", fileMtime);
                             }
                         }
                     } catch (IOException e) {
                         log.warn("读取文件属性失败: {}", csvFile, e);
                     }
                 });
        }
        
        // 格式化大小和时间
        for (Map.Entry<String, Map<String, Object>> entry : tables.entrySet()) {
            Map<String, Object> tableStats = entry.getValue();
            long size = (long) tableStats.get("total_size");
            tableStats.put("total_size_mb", Math.round(size / (1024.0 * 1024.0) * 100.0) / 100.0);
            
            Long latestTime = (Long) tableStats.get("latest_time");
            if (latestTime != null) {
                LocalDateTime dateTime = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(latestTime), ZoneId.systemDefault());
                tableStats.put("latest_time_str", dateTime.format(DATE_FORMATTER));
            }
        }
        
        stats.put("total_files", totalFiles[0]);
        stats.put("total_size", totalSize[0]);
        stats.put("total_size_mb", Math.round(totalSize[0] / (1024.0 * 1024.0) * 100.0) / 100.0);
        stats.put("tables", tables);
        
        return stats;
    }
    
    /**
     * 获取输出文件列表
     */
    public List<Map<String, Object>> getOutputFiles(String tableName, int limit) throws IOException {
        Path outputDir = Paths.get(appConfig.getOutputPath());
        
        if (!Files.exists(outputDir)) {
            return Collections.emptyList();
        }
        
        String pattern = tableName != null ? "IDS_" + tableName + "_*.csv" : "IDS_*.csv";
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        
        try (Stream<Path> paths = Files.walk(outputDir)) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".csv"))
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .filter(p -> matcher.matches(p.getFileName()))
                    .sorted((a, b) -> {
                        try {
                            return Long.compare(
                                    Files.getLastModifiedTime(b).toMillis(),
                                    Files.getLastModifiedTime(a).toMillis());
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .limit(limit)
                    .map(csvFile -> {
                        Map<String, Object> fileInfo = new HashMap<>();
                        try {
                            BasicFileAttributes attrs = Files.readAttributes(csvFile, BasicFileAttributes.class);
                            fileInfo.put("name", csvFile.getFileName().toString());
                            fileInfo.put("path", outputDir.relativize(csvFile).toString());
                            fileInfo.put("size", attrs.size());
                            fileInfo.put("size_mb", Math.round(attrs.size() / (1024.0 * 1024.0) * 100.0) / 100.0);
                            
                            LocalDateTime dateTime = LocalDateTime.ofInstant(
                                    attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault());
                            fileInfo.put("modified", dateTime.format(DATE_FORMATTER));
                        } catch (IOException e) {
                            log.warn("读取文件属性失败: {}", csvFile, e);
                        }
                        return fileInfo;
                    })
                    .collect(Collectors.toList());
        }
    }
    
    /**
     * 检查输出目录是否存在
     */
    public boolean isOutputDirExists() {
        return Files.exists(Paths.get(appConfig.getOutputPath()));
    }
}
