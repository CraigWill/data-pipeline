package com.realtime.monitor.util;

import org.owasp.encoder.Encode;

import java.util.*;

/**
 * XSS 防护工具类（基于 OWASP Java Encoder）
 *
 * 提供 HTML / URL 两种编码方式：
 * - HTML 编码：用于将不可信数据输出到 HTML 内容中
 * - URL 编码：用于将不可信数据输出到 URL 参数或路径中
 *
 * 递归处理 Map / List / String，防止不可信数据
 * （Flink REST API、文件内容、用户输入等）导致 XSS。
 */
public final class XssSanitizer {

    private XssSanitizer() {}

    // =========================================================================
    // HTML 编码（输出到 HTML body / JSON 响应中的字符串）
    // =========================================================================

    /** HTML 编码单个字符串 */
    public static String sanitizeString(String value) {
        return value == null ? null : Encode.forHtml(value);
    }

    /** 递归 HTML 编码 Map 中所有 String 值 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> sanitize(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return map;
        Map<String, Object> safe = new HashMap<>(map.size());
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            safe.put(entry.getKey(), sanitizeValue(entry.getValue()));
        }
        return safe;
    }

    /** 对 List&lt;Map&gt; 中每个元素执行 sanitize */
    public static List<Map<String, Object>> sanitizeList(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) return list;
        List<Map<String, Object>> safe = new ArrayList<>(list.size());
        for (Map<String, Object> item : list) {
            safe.add(sanitize(item));
        }
        return safe;
    }

    /** 递归编码任意值 */
    @SuppressWarnings("unchecked")
    public static Object sanitizeValue(Object value) {
        if (value instanceof String) {
            return Encode.forHtml((String) value);
        } else if (value instanceof Map) {
            return sanitize((Map<String, Object>) value);
        } else if (value instanceof List) {
            List<Object> safeList = new ArrayList<>();
            for (Object item : (List<?>) value) {
                safeList.add(sanitizeValue(item));
            }
            return safeList;
        }
        return value;
    }

    // =========================================================================
    // URL 编码（输出到 URL 路径 / 查询参数中的字符串）
    // =========================================================================

    /** URL 路径段编码（用于 URL path segment） */
    public static String encodeForUrlPath(String value) {
        return value == null ? null : Encode.forUriComponent(value);
    }

    /** URL 查询参数编码（用于 ?key=value） */
    public static String encodeForUrlParam(String value) {
        return value == null ? null : Encode.forUriComponent(value);
    }

    // =========================================================================
    // JavaScript 编码（输出到 JS 上下文中的字符串）
    // =========================================================================

    /** JavaScript 字符串编码 */
    public static String encodeForJavaScript(String value) {
        return value == null ? null : Encode.forJavaScript(value);
    }
}
