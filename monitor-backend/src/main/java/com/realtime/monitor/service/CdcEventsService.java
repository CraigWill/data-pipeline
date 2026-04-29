package com.realtime.monitor.service;

import com.realtime.monitor.util.XssSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * CDC 事件监控服务
 * 从输出的 CSV 文件中解析 CDC 事件
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CdcEventsService {
    
    @Value("${output.path:./output/cdc}")
    private String outputPath;

    private final com.realtime.monitor.repository.CdcFileRepository cdcFileRepository;

    private Path outputBaseDir;

    @jakarta.annotation.PostConstruct
    public void init() {
        this.outputBaseDir = validateOutputBaseDir(outputPath);
    }

    private Path validateOutputBaseDir(String configured) {
        Path base;
        try {
            base = Paths.get(configured).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new IllegalStateException("output.path 配置非法", e);
        }

        Path root1 = Paths.get("./output").toAbsolutePath().normalize();
        Path root2 = Paths.get("/opt/flink/output").toAbsolutePath().normalize();

        if (!base.startsWith(root1) && !base.startsWith(root2)) {
            throw new IllegalStateException("output.path 不在允许目录内: " + base);
        }
        return base;
    }

    private Path getOutputDir() {
        return outputBaseDir != null ? outputBaseDir : validateOutputBaseDir(outputPath);
    }

    private Path resolveUnderOutputDir(String relative) {
        if (relative == null || relative.isBlank()) {
            throw new SecurityException("非法的文件路径参数");
        }
        if (relative.contains("\u0000") || relative.contains("\\") || relative.contains("//")) {
            throw new SecurityException("非法的文件路径参数");
        }
        Path rel;
        try {
            rel = Paths.get(relative);
        } catch (InvalidPathException e) {
            throw new SecurityException("非法的文件路径参数");
        }
        if (rel.isAbsolute()) {
            throw new SecurityException("非法的文件路径参数");
        }
        Path resolved = getOutputDir().resolve(rel).normalize();
        if (!resolved.startsWith(getOutputDir())) {
            throw new SecurityException("路径遍历检测：文件不在预期目录内");
        }
        return resolved;
    }
    
    /**
     * 获取 CDC 事件列表
     */
    public Map<String, Object> listEvents(String table, String eventType, int page, int size) {
        List<Map<String, Object>> allEvents = new ArrayList<>();
        
        try {
            // 扫描输出目录
            Path outputDir = getOutputDir();
            if (!Files.exists(outputDir)) {
                return createEmptyResult();
            }
            
            // 获取所有 CSV 文件
            List<File> csvFiles = findCsvFiles(outputDir, table);
            
            // 解析 CSV 文件
            for (File csvFile : csvFiles) {
                List<Map<String, Object>> events = parseCsvFile(csvFile, eventType);
                allEvents.addAll(events);
            }
            
            // 按时间倒序排序
            allEvents.sort((a, b) -> {
                Long timeA = (Long) a.getOrDefault("timestamp", 0L);
                Long timeB = (Long) b.getOrDefault("timestamp", 0L);
                return timeB.compareTo(timeA);
            });
            
            // 分页
            int start = (page - 1) * size;
            int end = Math.min(start + size, allEvents.size());
            List<Map<String, Object>> pagedEvents = allEvents.subList(start, end);
            
            // 计算统计信息
            Map<String, Object> stats = calculateStats(allEvents);
            
            Map<String, Object> result = new HashMap<>();
            result.put("events", pagedEvents);
            result.put("stats", stats);
            result.put("total", allEvents.size());
            result.put("totalPages", (int) Math.ceil((double) allEvents.size() / size));
            result.put("currentPage", page);
            
            return result;
            
        } catch (Exception e) {
            log.error("获取 CDC 事件失败", e);
            return createEmptyResult();
        }
    }
    
    /**
     * 获取表列表
     */
    public List<String> getTables() {
        Set<String> tables = new HashSet<>();
        
        try {
            Path outputDir = getOutputDir();
            if (!Files.exists(outputDir)) {
                return new ArrayList<>();
            }
            
            List<File> csvFiles = findCsvFiles(outputDir, null);
            for (File csvFile : csvFiles) {
                String tableName = extractTableName(csvFile.getName());
                if (tableName != null) {
                    tables.add(tableName);
                }
            }
            
        } catch (Exception e) {
            log.error("获取表列表失败", e);
        }
        
        return new ArrayList<>(tables);
    }
    
    /**
     * 获取统计信息
     */
    public Map<String, Object> getStats() {
        List<Map<String, Object>> allEvents = new ArrayList<>();
        
        try {
            Path outputDir = getOutputDir();
            if (!Files.exists(outputDir)) {
                return createEmptyStats();
            }
            
            List<File> csvFiles = findCsvFiles(outputDir, null);
            for (File csvFile : csvFiles) {
                List<Map<String, Object>> events = parseCsvFile(csvFile, null);
                allEvents.addAll(events);
            }
            
            return calculateStats(allEvents);
            
        } catch (Exception e) {
            log.error("获取统计信息失败", e);
            return createEmptyStats();
        }
    }
    
    /**
     * 获取当日统计信息
     */
    public Map<String, Object> getTodayStats() {
        Map<String, Object> stats = new HashMap<>();
        long totalEvents = 0;
        long insertEvents = 0;
        long updateEvents = 0;
        long deleteEvents = 0;
        
        try {
            Path outputDir = getOutputDir();
            if (!Files.exists(outputDir)) {
                return createEmptyStats();
            }
            
            // 获取今天的日期
            String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            
            // 遍历日期目录，只统计今天的
            try (Stream<Path> dateDirs = Files.list(outputDir)) {
                for (Path dateDir : dateDirs.filter(Files::isDirectory).toList()) {
                    String dirName = dateDir.getFileName().toString();
                    // 目录名格式: 2026-03-09--10
                    if (dirName.matches("\\d{4}-\\d{2}-\\d{2}--\\d{2}") && dirName.startsWith(today)) {
                        // 统计该目录下的文件
                        try (Stream<Path> csvFiles = Files.list(dateDir)) {
                            for (Path csvFile : csvFiles.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".csv")).toList()) {
                                // 使用优化的行数统计方法
                                long lineCount = countLinesOptimized(csvFile.toFile());
                                totalEvents += lineCount;
                                insertEvents += lineCount; // 默认为 INSERT
                            }
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("获取当日统计失败", e);
        }
        
        stats.put("totalEvents", totalEvents);
        stats.put("insertEvents", insertEvents);
        stats.put("updateEvents", updateEvents);
        stats.put("deleteEvents", deleteEvents);
        stats.put("date", LocalDate.now().toString());
        
        return stats;
    }
    
    /**
     * 优化的行数统计方法（避免 OOM）
     */
    private long countLinesOptimized(File file) {
        long count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file), 8192)) {
            char[] buffer = new char[8192];
            int readChars;
            boolean lastCharWasNewline = true;
            
            while ((readChars = reader.read(buffer)) != -1) {
                for (int i = 0; i < readChars; i++) {
                    if (buffer[i] == '\n') {
                        count++;
                        lastCharWasNewline = true;
                    } else {
                        lastCharWasNewline = false;
                    }
                }
            }
            // 如果文件不以换行符结尾，最后一行也要计数
            if (!lastCharWasNewline) {
                count++;
            }
        } catch (Exception e) {
            log.error("统计文件行数失败: {}", file.getName(), e);
        }
        return count;
    }
    
    /**
     * 查找 CSV 文件
     */
    private List<File> findCsvFiles(Path dir, String table) {
        List<File> csvFiles = new ArrayList<>();
        
        try (Stream<Path> paths = Files.walk(dir)) {
            csvFiles = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".csv"))
                .filter(p -> table == null || p.toString().contains(table))
                .map(Path::toFile)
                .sorted((a, b) -> Long.compare(b.lastModified(), a.lastModified()))
                .limit(100) // 限制文件数量
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("查找 CSV 文件失败", e);
        }
        
        return csvFiles;
    }
    
    /**
     * 解析 CSV 文件
     */
    private List<Map<String, Object>> parseCsvFile(File csvFile, String eventType) {
        List<Map<String, Object>> events = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
            String line;
            int lineNumber = 0;
            
            // 定义默认的列名（根据实际 CSV 格式）
            String[] defaultHeaders = {"timestamp", "op_type", "account_id", "amount", "trans_id", 
                                       "merchant_id", "status", "create_time", "trans_type"};
            
            while ((line = reader.readLine()) != null && lineNumber < 1000) {
                lineNumber++;
                
                // 跳过空行
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                String[] values = line.split(",", -1);
                
                Map<String, Object> event = new HashMap<>();
                Map<String, Object> data = new HashMap<>();
                
                // 解析数据 - 使用默认列名或索引
                for (int i = 0; i < values.length; i++) {
                    String header = i < defaultHeaders.length ? defaultHeaders[i] : "col_" + i;
                    String value = values[i].trim();
                    data.put(header, value);
                }
                
                // 确定事件类型
                String opType = "INSERT";
                if (data.containsKey("op_type")) {
                    opType = String.valueOf(data.get("op_type")).toUpperCase();
                }
                
                // 构建事件对象
                event.put("id", UUID.randomUUID().toString());
                event.put("tableName", extractTableName(csvFile.getName()));
                event.put("eventType", opType);
                event.put("data", data);
                event.put("timestamp", csvFile.lastModified());
                event.put("source", csvFile.getName());
                
                // 过滤事件类型
                if (eventType == null || eventType.isEmpty() || eventType.equalsIgnoreCase(opType)) {
                    events.add(event);
                }
            }
            
        } catch (Exception e) {
            log.error("解析 CSV 文件失败: {}", csvFile.getName(), e);
        }
        
        return events;
    }
    
    /**
     * 从文件名提取表名
     */
    private String extractTableName(String fileName) {
        // 文件名格式: IDS_ACCOUNT_INFO_20260305_172601093-uuid-0.csv
        if (fileName.contains("_")) {
            String[] parts = fileName.split("_");
            if (parts.length >= 3) {
                // 提取前面的部分作为表名
                StringBuilder tableName = new StringBuilder();
                for (int i = 0; i < parts.length - 1; i++) {
                    if (parts[i].matches("\\d{8}")) {
                        break;
                    }
                    if (tableName.length() > 0) {
                        tableName.append("_");
                    }
                    tableName.append(parts[i]);
                }
                return tableName.toString();
            }
        }
        return fileName.replace(".csv", "");
    }
    
    /**
     * 计算统计信息
     */
    private Map<String, Object> calculateStats(List<Map<String, Object>> events) {
        Map<String, Object> stats = new HashMap<>();
        
        long totalEvents = events.size();
        long insertEvents = events.stream()
            .filter(e -> "INSERT".equals(e.get("eventType")))
            .count();
        long updateEvents = events.stream()
            .filter(e -> "UPDATE".equals(e.get("eventType")))
            .count();
        long deleteEvents = events.stream()
            .filter(e -> "DELETE".equals(e.get("eventType")))
            .count();
        
        stats.put("totalEvents", totalEvents);
        stats.put("insertEvents", insertEvents);
        stats.put("updateEvents", updateEvents);
        stats.put("deleteEvents", deleteEvents);
        
        return stats;
    }
    
    /**
     * 获取按小时统计的事件数量（当日24小时）
     */
    public Map<String, Object> getStatsByDate(String table) {
        // 小时 -> 数量
        Map<String, Long> hourlyStats = new TreeMap<>();
        Map<String, long[]> tableStats = new HashMap<>(); // 每个表的 [记录数, 文件数]
        
        // 初始化24小时
        for (int i = 0; i < 24; i++) {
            hourlyStats.put(String.format("%02d:00", i), 0L);
        }
        
        // 获取今天的日期
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        
        try {
            Path outputDir = getOutputDir();
            if (!Files.exists(outputDir)) {
                return createEmptyDateStats();
            }
            
            // 遍历日期目录
            try (Stream<Path> dateDirs = Files.list(outputDir)) {
                for (Path dateDir : dateDirs.filter(Files::isDirectory).toList()) {
                    String dirName = dateDir.getFileName().toString();
                    // 目录名格式: 2026-03-09--10，只统计今天的
                    if (dirName.matches("\\d{4}-\\d{2}-\\d{2}--\\d{2}") && dirName.startsWith(today)) {
                        String hour = dirName.substring(12, 14) + ":00"; // 提取小时部分
                        
                        // 统计该目录下的事件数量（按表筛选）
                        Map<String, long[]> dirTableStats = countEventsByTableInDir(dateDir.toFile());
                        
                        // 累加每个表的统计
                        for (Map.Entry<String, long[]> entry : dirTableStats.entrySet()) {
                            long[] existing = tableStats.getOrDefault(entry.getKey(), new long[]{0, 0});
                            existing[0] += entry.getValue()[0]; // 记录数
                            existing[1] += entry.getValue()[1]; // 文件数
                            tableStats.put(entry.getKey(), existing);
                        }
                        
                        // 如果指定了表，只统计该表；否则统计全部
                        long count = 0;
                        if (table != null && !table.isEmpty()) {
                            long[] stats = dirTableStats.get(table);
                            count = stats != null ? stats[0] : 0;
                        } else {
                            count = dirTableStats.values().stream().mapToLong(arr -> arr[0]).sum();
                        }
                        hourlyStats.merge(hour, count, Long::sum);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("获取小时统计失败", e);
        }
        
        // 转换为图表数据格式
        List<String> labels = new ArrayList<>(hourlyStats.keySet());
        List<Long> values = new ArrayList<>(hourlyStats.values());
        
        // 计算总数
        long total = values.stream().mapToLong(Long::longValue).sum();
        
        // 表列表及其统计（包含记录数和文件数）
        List<Map<String, Object>> tableList = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : tableStats.entrySet()) {
            Map<String, Object> t = new HashMap<>();
            t.put("name", entry.getKey());
            t.put("count", entry.getValue()[0]);     // 记录数
            t.put("fileCount", entry.getValue()[1]); // 文件数
            tableList.add(t);
        }
        // 按记录数降序排序
        tableList.sort((a, b) -> Long.compare((Long)b.get("count"), (Long)a.get("count")));
        
        Map<String, Object> result = new HashMap<>();
        result.put("labels", labels);
        result.put("values", values);
        result.put("total", total);
        result.put("tables", tableList);
        result.put("selectedTable", table != null ? table : "");
        result.put("date", today);
        
        return result;
    }
    
    /**
     * 统计目录下每个表的事件数量和文件数量
     */
    private Map<String, long[]> countEventsByTableInDir(File dir) {
        // 返回 Map<表名, [记录数, 文件数]>
        Map<String, long[]> tableStats = new HashMap<>();
        File[] csvFiles = dir.listFiles((d, name) -> name.endsWith(".csv"));
        if (csvFiles != null) {
            for (File csvFile : csvFiles) {
                String tableName = extractTableName(csvFile.getName());
                long count = countLinesOptimized(csvFile);
                long[] stats = tableStats.getOrDefault(tableName, new long[]{0, 0});
                stats[0] += count;  // 记录数
                stats[1] += 1;      // 文件数
                tableStats.put(tableName, stats);
            }
        }
        return tableStats;
    }
    
    /**
     * 创建空的日期统计
     */
    private Map<String, Object> createEmptyDateStats() {
        Map<String, Object> result = new HashMap<>();
        result.put("labels", new ArrayList<>());
        result.put("values", new ArrayList<>());
        result.put("total", 0);
        return result;
    }
    
    /**
     * 创建空结果
     */
    private Map<String, Object> createEmptyResult() {
        Map<String, Object> result = new HashMap<>();
        result.put("events", new ArrayList<>());
        result.put("stats", createEmptyStats());
        result.put("total", 0);
        result.put("totalPages", 0);
        result.put("currentPage", 1);
        return result;
    }
    
    /**
     * 创建空统计
     */
    private Map<String, Object> createEmptyStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEvents", 0);
        stats.put("insertEvents", 0);
        stats.put("updateEvents", 0);
        stats.put("deleteEvents", 0);
        return stats;
    }


    /**
     * 根据日期获取文件列表
     */
    public Map<String, Object> listFilesByDate(String date) {
        List<Map<String, Object>> files = new ArrayList<>();

        try {
            Path outputDir = getOutputDir();
            if (!Files.exists(outputDir)) {
                return createEmptyFileResult();
            }

            // 遍历日期目录
            try (Stream<Path> dateDirs = Files.list(outputDir)) {
                for (Path dateDir : dateDirs.filter(Files::isDirectory).toList()) {
                    String dirName = dateDir.getFileName().toString();
                    // 目录名格式: 2026-03-09--10
                    if (dirName.matches("\\d{4}-\\d{2}-\\d{2}--\\d{2}") && dirName.startsWith(date)) {
                        String hour = dirName.substring(12, 14) + ":00"; // 提取小时部分，格式化为 10:00

                        // 获取该目录下的 CSV 文件
                        try (Stream<Path> csvFiles = Files.list(dateDir)) {
                            for (Path csvFile : csvFiles.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".csv")).toList()) {
                                String relativePath = dateDir.getFileName().toString() + "/" + csvFile.getFileName().toString();
                                String tableName = extractTableName(csvFile.getFileName().toString());
                                long fileSize = Files.size(csvFile);
                                long lineCount = countLinesOptimized(csvFile.toFile());
                                long timestamp = Files.getLastModifiedTime(csvFile).toMillis();

                                // 注册文件到数据库，获取 ID（前端只看到 ID，不看到路径）
                                String fileId = cdcFileRepository.registerFile(
                                    relativePath, csvFile.getFileName().toString(), tableName,
                                    fileSize, lineCount, timestamp);

                                Map<String, Object> fileInfo = new HashMap<>();
                                fileInfo.put("id", fileId);
                                fileInfo.put("name", csvFile.getFileName().toString());
                                // 不再暴露 path 给前端
                                fileInfo.put("table", tableName);
                                fileInfo.put("size", fileSize);
                                fileInfo.put("sizeFormatted", formatFileSize(fileSize));
                                fileInfo.put("lineCount", lineCount);
                                
                                String formattedTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                                    .format(new java.util.Date(timestamp));
                                fileInfo.put("hour", formattedTime);
                                fileInfo.put("lastModified", timestamp);
                                files.add(fileInfo);
                            }
                        }
                    }
                }
            }

            // 按修改时间倒序排序
            files.sort((a, b) -> Long.compare(
                (Long) b.get("lastModified"),
                (Long) a.get("lastModified")
            ));

        } catch (Exception e) {
            log.error("获取文件列表失败", e);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("files", files);
        result.put("total", files.size());
        result.put("date", date);

        return result;
    }

    /**
     * 根据文件 ID 获取文件内容（分页）。
     * 前端传入 fileId，后端从数据库查出真实路径，前端永远不接触文件路径。
     */
    public Map<String, Object> getFileContentById(String fileId, int page, int size) {
        String filePath = cdcFileRepository.getFilePath(fileId);
        if (filePath == null) {
            log.warn("无效的文件 ID: {}", fileId);
            Map<String, Object> empty = new HashMap<>();
            empty.put("rows", new ArrayList<>());
            empty.put("total", 0);
            empty.put("totalPages", 0);
            empty.put("currentPage", 1);
            empty.put("fileId", fileId);
            return empty;
        }
        Map<String, Object> result = getFileContent(filePath, page, size);
        // 用 fileId 替换 filePath，不暴露路径
        result.remove("filePath");
        result.put("fileId", fileId);
        return result;
    }

    /**
     * 获取文件内容（分页）— 内部方法，接受真实路径。
     * 所有从文件读取的字符串字段均经过 HTML 实体编码，防止 XSS。
     */
    public Map<String, Object> getFileContent(String filePath, int page, int size) {
        List<Map<String, Object>> rows = new ArrayList<>();
        long totalLines = 0;

        try {
            Path file = resolveUnderOutputDir(filePath);
            if (!Files.exists(file) || !Files.isRegularFile(file)) {
                return createEmptyContentResult(filePath);
            }

            // 先统计总行数
            totalLines = countLinesOptimized(file.toFile());

            // 读取指定页的数据
            int startLine = (page - 1) * size;
            int endLine = startLine + size;
            int currentLine = 0;

            try (BufferedReader reader = new BufferedReader(new FileReader(file.toFile()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (currentLine >= startLine && currentLine < endLine) {
                        Map<String, Object> row = new HashMap<>();
                        row.put("lineNumber", currentLine + 1);
                        // HTML-encode the raw line to prevent XSS when rendered in a browser
                        row.put("content", XssSanitizer.sanitizeString(line));
                        // HTML-encode every CSV field individually
                        String[] rawFields = line.split(",", -1);
                        String[] encodedFields = new String[rawFields.length];
                        for (int i = 0; i < rawFields.length; i++) {
                            encodedFields[i] = XssSanitizer.sanitizeString(rawFields[i]);
                        }
                        row.put("fields", encodedFields);
                        rows.add(row);
                    }
                    currentLine++;
                    if (currentLine >= endLine) {
                        break;
                    }
                }
            }

        } catch (Exception e) {
            log.error("读取文件内容失败: {}", filePath, e);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("rows", rows);
        result.put("total", totalLines);
        result.put("totalPages", (int) Math.ceil((double) totalLines / size));
        result.put("currentPage", page);
        result.put("pageSize", size);
        // Do not echo the user-supplied path back into the response
        result.put("filePath", XssSanitizer.sanitizeString(filePath));

        return result;
    }

    /**
     * 获取可用的日期列表
     */
    public List<String> getAvailableDates() {
        Set<String> dates = new TreeSet<>(Collections.reverseOrder());

        try {
            Path outputDir = getOutputDir();
            if (!Files.exists(outputDir)) {
                return new ArrayList<>();
            }

            try (Stream<Path> dateDirs = Files.list(outputDir)) {
                for (Path dateDir : dateDirs.filter(Files::isDirectory).toList()) {
                    String dirName = dateDir.getFileName().toString();
                    if (dirName.matches("\\d{4}-\\d{2}-\\d{2}--\\d{2}")) {
                        dates.add(dirName.substring(0, 10)); // 提取日期部分
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取日期列表失败", e);
        }

        return new ArrayList<>(dates);
    }

    /**
     * 格式化文件大小
     */
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * 创建空的文件列表结果
     */
    private Map<String, Object> createEmptyFileResult() {
        Map<String, Object> result = new HashMap<>();
        result.put("files", new ArrayList<>());
        result.put("total", 0);
        return result;
    }

    /**
     * 创建空的文件内容结果
     */
    private Map<String, Object> createEmptyContentResult(String filePath) {
        Map<String, Object> result = new HashMap<>();
        result.put("rows", new ArrayList<>());
        result.put("total", 0);
        result.put("totalPages", 0);
        result.put("currentPage", 1);
        result.put("filePath", filePath);
        return result;
    }

}
