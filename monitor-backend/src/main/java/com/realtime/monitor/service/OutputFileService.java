package com.realtime.monitor.service;

import com.realtime.monitor.config.AppConfig;
import com.realtime.monitor.util.PathSecurityValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
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
 * 
 * 安全措施：
 * - 路径遍历防护：所有路径操作都经过 PathSecurityValidator 验证
 * - 文件后缀白名单：只允许读取 .csv 文件
 * - 沙箱约束：所有文件操作限制在 outputPath 目录内
 * - 输入验证：表名参数只允许字母、数字、下划线
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutputFileService {
    
    private final AppConfig appConfig;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 允许的文件后缀白名单 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".csv");

    /** 表名合法字符模式 */
    private static final java.util.regex.Pattern SAFE_TABLE_NAME = 
            java.util.regex.Pattern.compile("^[a-zA-Z0-9_]{1,128}$");

    /** 已验证的输出基础目录 */
    private Path outputBaseDir;

    @PostConstruct
    public void init() {
        this.outputBaseDir = resolveAndValidateOutputDir();
    }

    /**
     * 验证并解析输出目录，确保在允许的范围内
     */
    private Path resolveAndValidateOutputDir() {
        String configuredPath = appConfig.getOutputPath();
        if (configuredPath == null || configuredPath.isBlank()) {
            configuredPath = "./output/cdc";
        }
        Path base = Paths.get(configuredPath).toAbsolutePath().normalize();

        // 验证输出目录在允许的根目录内
        Path allowedRoot1 = Paths.get("./output").toAbsolutePath().normalize();
        Path allowedRoot2 = Paths.get("/opt/flink/output").toAbsolutePath().normalize();
        if (!base.startsWith(allowedRoot1) && !base.startsWith(allowedRoot2)) {
            log.error("output.path 不在允许目录内: {}", base);
            throw new IllegalStateException("output.path 配置不安全: " + base);
        }
        return base;
    }

    private Path getOutputDir() {
        return outputBaseDir != null ? outputBaseDir : resolveAndValidateOutputDir();
    }

    /**
     * 验证表名参数安全性
     */
    private String validateTableName(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return null;
        }
        if (!SAFE_TABLE_NAME.matcher(tableName).matches()) {
            log.warn("非法的表名参数被拒绝: {}", 
                    tableName.length() > 50 ? tableName.substring(0, 50) + "..." : tableName);
            throw new SecurityException("非法的表名参数");
        }
        return tableName.toUpperCase();
    }

    /**
     * 验证文件名是否安全（白名单后缀 + 无恶意字符）
     */
    private boolean isAllowedFile(Path file) {
        String fileName = file.getFileName().toString();

        // 拒绝隐藏文件
        if (fileName.startsWith(".")) {
            return false;
        }

        // 白名单后缀检查
        String ext = fileName.contains(".") 
                ? fileName.substring(fileName.lastIndexOf('.')).toLowerCase() 
                : "";
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            return false;
        }

        // 拒绝包含恶意字符的文件名
        if (fileName.contains("\u0000") || fileName.contains("..") 
                || fileName.contains(";") || fileName.contains("`")
                || fileName.contains("$") || fileName.contains("|")) {
            log.warn("跳过包含恶意字符的文件: {}", fileName);
            return false;
        }

        // 确保文件在沙箱内
        Path normalized = file.toAbsolutePath().normalize();
        return normalized.startsWith(getOutputDir().toAbsolutePath().normalize());
    }
    
    /**
     * 获取输出文件统计信息
     */
    public Map<String, Object> getOutputStats() throws IOException {
        Path outputDir = getOutputDir();
        
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
        
        try (Stream<Path> paths = Files.walk(outputDir, 3)) { // 限制遍历深度
            paths.filter(Files::isRegularFile)
                 .filter(this::isAllowedFile)
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
        // 验证表名参数
        String safeTableName = validateTableName(tableName);

        Path outputDir = getOutputDir();
        
        if (!Files.exists(outputDir)) {
            return Collections.emptyList();
        }

        // 限制返回数量，防止资源耗尽
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        
        String pattern = safeTableName != null ? "IDS_" + safeTableName + "_*.csv" : "IDS_*.csv";
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        
        try (Stream<Path> paths = Files.walk(outputDir, 3)) { // 限制遍历深度
            return paths.filter(Files::isRegularFile)
                    .filter(this::isAllowedFile)
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
                    .limit(safeLimit)
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
        return Files.exists(getOutputDir());
    }
}
