package com.realtime.monitor.util;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 路径安全验证工具类
 *
 * 提供统一的路径遍历防护能力，包括：
 * - 路径遍历检测（../ 攻击）
 * - 恶意符号过滤（null 字节、反斜杠、特殊字符）
 * - 文件后缀白名单控制
 * - 路径规范化和沙箱约束
 */
@Slf4j
public final class PathSecurityValidator {

    private PathSecurityValidator() {}

    // =========================================================================
    // 文件后缀白名单
    // =========================================================================

    /** 允许读取的文件后缀白名单 */
    private static final Set<String> ALLOWED_READ_EXTENSIONS = Set.of(
            ".csv", ".json", ".txt", ".log"
    );

    /** 允许写入/创建的文件后缀白名单 */
    private static final Set<String> ALLOWED_WRITE_EXTENSIONS = Set.of(
            ".csv", ".json", ".txt"
    );

    // =========================================================================
    // 恶意字符检测模式
    // =========================================================================

    /** 路径遍历模式：检测 ../ 或 ..\ 变体 */
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(
            "(?i)(\\.\\.[\\\\/])|" +           // ../ 或 ..\
            "([\\\\/]\\.\\.)|" +                // /.. 或 \..
            "(\\.\\.\\z)|" +                    // 以 .. 结尾
            "(%2e%2e)|" +                       // URL 编码的 ..
            "(%252e%252e)|" +                   // 双重 URL 编码的 ..
            "(\\.%2e)|(%2e\\.)"                 // 混合编码
    );

    /** 恶意符号模式：null 字节、命令注入字符等 */
    private static final Pattern MALICIOUS_CHARS_PATTERN = Pattern.compile(
            "[\\x00-\\x1f]|" +                  // 控制字符（含 null 字节）
            "[`$|;&!><]|" +                     // Shell 命令注入字符
            "(\\\\(?![\\\\]))| " +              // 单反斜杠（Windows 路径分隔符）
            "(%00)|" +                          // URL 编码的 null 字节
            "(\\u0000)"                         // Unicode null
    );

    /** 文件名合法字符：只允许字母、数字、下划线、连字符、点、斜杠 */
    private static final Pattern SAFE_PATH_CHARS = Pattern.compile(
            "^[a-zA-Z0-9_.\\-/]+$"
    );

    // =========================================================================
    // 公共验证方法
    // =========================================================================

    /**
     * 验证并规范化相对路径，确保不会逃逸出基础目录。
     *
     * @param basePath     基础目录（沙箱根）
     * @param relativePath 用户提供的相对路径
     * @return 规范化后的安全绝对路径
     * @throws SecurityException 如果路径不安全
     */
    public static Path validateAndResolve(Path basePath, String relativePath) {
        // 1. 基本空值检查
        if (relativePath == null || relativePath.isBlank()) {
            throw new SecurityException("文件路径不能为空");
        }

        // 2. 检测恶意字符
        rejectMaliciousChars(relativePath);

        // 3. 检测路径遍历
        rejectPathTraversal(relativePath);

        // 4. 拒绝绝对路径
        if (relativePath.startsWith("/") || relativePath.startsWith("\\")
                || (relativePath.length() >= 2 && relativePath.charAt(1) == ':')) {
            throw new SecurityException("不允许使用绝对路径: " + sanitizeForLog(relativePath));
        }

        // 5. 解析并规范化
        Path resolved;
        try {
            Path relPath = Paths.get(relativePath);
            resolved = basePath.resolve(relPath).normalize();
        } catch (InvalidPathException e) {
            throw new SecurityException("无效的文件路径: " + sanitizeForLog(relativePath));
        }

        // 6. 沙箱约束：确保解析后的路径仍在基础目录内
        Path normalizedBase = basePath.toAbsolutePath().normalize();
        Path normalizedResolved = resolved.toAbsolutePath().normalize();
        if (!normalizedResolved.startsWith(normalizedBase)) {
            log.warn("路径遍历攻击被阻止: base={}, resolved={}", normalizedBase, normalizedResolved);
            throw new SecurityException("路径遍历检测：文件不在允许的目录内");
        }

        return normalizedResolved;
    }

    /**
     * 验证文件名（不含路径）是否安全。
     *
     * @param fileName 文件名
     * @throws SecurityException 如果文件名不安全
     */
    public static void validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new SecurityException("文件名不能为空");
        }

        rejectMaliciousChars(fileName);

        // 文件名不应包含路径分隔符
        if (fileName.contains("/") || fileName.contains("\\")) {
            throw new SecurityException("文件名不能包含路径分隔符");
        }

        // 文件名不应以点开头（隐藏文件）
        if (fileName.startsWith(".")) {
            throw new SecurityException("不允许访问隐藏文件");
        }

        // 文件名长度限制
        if (fileName.length() > 255) {
            throw new SecurityException("文件名过长");
        }
    }

    /**
     * 验证文件后缀是否在读取白名单中。
     *
     * @param fileName 文件名
     * @throws SecurityException 如果后缀不在白名单中
     */
    public static void validateReadExtension(String fileName) {
        validateFileName(fileName);
        String ext = getExtension(fileName);
        if (!ALLOWED_READ_EXTENSIONS.contains(ext.toLowerCase())) {
            log.warn("拒绝读取非白名单后缀文件: {}, 后缀: {}", sanitizeForLog(fileName), ext);
            throw new SecurityException("不允许读取该类型的文件: " + ext);
        }
    }

    /**
     * 验证文件后缀是否在写入白名单中。
     *
     * @param fileName 文件名
     * @throws SecurityException 如果后缀不在白名单中
     */
    public static void validateWriteExtension(String fileName) {
        validateFileName(fileName);
        String ext = getExtension(fileName);
        if (!ALLOWED_WRITE_EXTENSIONS.contains(ext.toLowerCase())) {
            log.warn("拒绝写入非白名单后缀文件: {}, 后缀: {}", sanitizeForLog(fileName), ext);
            throw new SecurityException("不允许写入该类型的文件: " + ext);
        }
    }

    /**
     * 验证路径中只包含安全字符。
     *
     * @param path 路径字符串
     * @throws SecurityException 如果包含不安全字符
     */
    public static void validateSafeCharsOnly(String path) {
        if (path == null || path.isBlank()) {
            throw new SecurityException("路径不能为空");
        }
        if (!SAFE_PATH_CHARS.matcher(path).matches()) {
            throw new SecurityException("路径包含非法字符: " + sanitizeForLog(path));
        }
    }

    /**
     * 综合验证：路径遍历 + 恶意字符 + 后缀白名单 + 沙箱约束
     *
     * @param basePath     基础目录
     * @param relativePath 相对路径
     * @return 安全的绝对路径
     * @throws SecurityException 如果任何检查失败
     */
    public static Path validateForRead(Path basePath, String relativePath) {
        Path resolved = validateAndResolve(basePath, relativePath);
        // 验证最终文件名的后缀
        String fileName = resolved.getFileName().toString();
        validateReadExtension(fileName);
        return resolved;
    }

    /**
     * 综合验证（写入场景）：路径遍历 + 恶意字符 + 写入后缀白名单 + 沙箱约束
     *
     * @param basePath     基础目录
     * @param relativePath 相对路径
     * @return 安全的绝对路径
     * @throws SecurityException 如果任何检查失败
     */
    public static Path validateForWrite(Path basePath, String relativePath) {
        Path resolved = validateAndResolve(basePath, relativePath);
        String fileName = resolved.getFileName().toString();
        validateWriteExtension(fileName);
        return resolved;
    }

    // =========================================================================
    // 内部方法
    // =========================================================================

    private static void rejectPathTraversal(String input) {
        if (PATH_TRAVERSAL_PATTERN.matcher(input).find()) {
            log.warn("检测到路径遍历尝试: {}", sanitizeForLog(input));
            throw new SecurityException("检测到路径遍历攻击");
        }
        // 额外检查：规范化后是否包含 ..
        try {
            Path p = Paths.get(input).normalize();
            if (p.toString().contains("..")) {
                throw new SecurityException("检测到路径遍历攻击");
            }
        } catch (InvalidPathException e) {
            throw new SecurityException("无效的文件路径");
        }
    }

    private static void rejectMaliciousChars(String input) {
        if (MALICIOUS_CHARS_PATTERN.matcher(input).find()) {
            log.warn("检测到恶意字符: {}", sanitizeForLog(input));
            throw new SecurityException("路径包含非法字符");
        }
    }

    private static String getExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDot).toLowerCase();
    }

    /**
     * 清理用于日志输出的字符串，防止日志注入
     */
    private static String sanitizeForLog(String input) {
        if (input == null) return "null";
        // 截断 + 移除控制字符
        String safe = input.length() > 100 ? input.substring(0, 100) + "..." : input;
        return safe.replaceAll("[\\x00-\\x1f\\x7f]", "?");
    }
}
