package com.realtime.monitor.util;

import org.springframework.web.util.HtmlUtils;

import java.util.*;

/**
 * XSS 防护工具类
 *
 * 递归 HTML 转义 Map / List / String 中的所有字符串值，
 * 防止不可信数据（Flink REST API、文件内容、用户输入等）
 * 在前端被渲染为 HTML/JS 导致 XSS。
 */
public final class XssSanitizer {

    private XssSanitizer() {}

    /** 递归转义 Map 中所有 String 值 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> sanitize(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return map;
        Map<String, Object> safe = new HashMap<>(map.size());
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            safe.put(entry.getKey(), sanitizeValue(entry.getValue()));
        }
        return safe;
    }

    /** 对 List<Map> 中每个元素执行 sanitize */
    public static List<Map<String, Object>> sanitizeList(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) return list;
        List<Map<String, Object>> safe = new ArrayList<>(list.size());
        for (Map<String, Object> item : list) {
            safe.add(sanitize(item));
        }
        return safe;
    }

    /** 转义单个字符串 */
    public static String sanitizeString(String value) {
        return value == null ? null : HtmlUtils.htmlEscape(value);
    }

    /** 递归转义任意值：String → 转义，Map → 递归，List → 递归，其他原样返回 */
    @SuppressWarnings("unchecked")
    public static Object sanitizeValue(Object value) {
        if (value instanceof String) {
            return HtmlUtils.htmlEscape((String) value);
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
}
