package com.realtime.monitor.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.owasp.encoder.Encode;

/**
 * XSS 防护工具类 — 基于 OWASP Java Encoder 实现 ESAPI 风格的编码 API
 *
 * 提供与 OWASP ESAPI Encoder 等价的编码方法：
 * - encodeForHTML / sanitizeString — HTML 实体编码（防 XSS）
 * - encodeForURL / encodeForUrlPath / encodeForUrlParam — URL 百分号编码
 * - encodeForJavaScript — JavaScript 字符串编码
 * - encodeForCSS — CSS 属性值编码
 * - encodeForHTMLAttribute — HTML 属性值编码
 *
 * 底层使用 OWASP Java Encoder（org.owasp.encoder:encoder），
 * 它是 ESAPI Encoder 的官方推荐替代品，零配置、高性能、无外部依赖。
 *
 * 递归处理 Map / List / String，确保所有不可信数据
 * （Flink REST API、文件内容、用户输入等）在输出前被正确编码。
 *
 * @see <a href="https://owasp.org/www-project-java-encoder/">OWASP Java Encoder Project</a>
 */
public final class XssSanitizer {

    private XssSanitizer() {}

    // =========================================================================
    // HTML 编码（输出到 HTML body / JSON 响应中的字符串）
    // 等价于 ESAPI: ESAPI.encoder().encodeForHTML(input)
    // =========================================================================

    /**
     * HTML 编码单个字符串，防止 XSS 注入。
     * 将 &lt; &gt; &amp; &quot; &#x27; 等危险字符转为 HTML 实体。
     *
     * 等价于 ESAPI: {@code ESAPI.encoder().encodeForHTML(value)}
     */
    public static String sanitizeString(String value) {
        if (value == null) return null;
        return Encode.forHtml(value);
    }

    /**
     * 递归 HTML 编码 Map 中所有 String 值。
     * 非 String 类型（Number、Boolean 等）保持不变。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> sanitize(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return map;
        Map<String, Object> safe = new HashMap<>(map.size());
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            safe.put(entry.getKey(), sanitizeValue(entry.getValue()));
        }
        return safe;
    }

    /**
     * 对 List&lt;Map&gt; 中每个元素执行 sanitize
     */
    public static List<Map<String, Object>> sanitizeList(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) return list;
        List<Map<String, Object>> safe = new ArrayList<>(list.size());
        for (Map<String, Object> item : list) {
            safe.add(sanitize(item));
        }
        return safe;
    }

    /**
     * 递归编码任意值。
     * - String → HTML 编码
     * - Map → 递归编码
     * - List → 递归编码每个元素
     * - 其他类型 → 原样返回
     */
    @SuppressWarnings("unchecked")
    public static Object sanitizeValue(Object value) {
        if (value instanceof String str) {
            return Encode.forHtml(str);
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
    // 等价于 ESAPI: ESAPI.encoder().encodeForURL(input)
    // =========================================================================

    /**
     * URL 路径段编码（用于 URL path segment）。
     * 对所有非 URL 安全字符进行百分号编码。
     *
     * 等价于 ESAPI: {@code ESAPI.encoder().encodeForURL(value)}
     */
    public static String encodeForUrlPath(String value) {
        if (value == null) return null;
        return Encode.forUriComponent(value);
    }

    /**
     * URL 查询参数编码（用于 ?key=value）。
     * 对所有非 URL 安全字符进行百分号编码。
     *
     * 等价于 ESAPI: {@code ESAPI.encoder().encodeForURL(value)}
     */
    public static String encodeForUrlParam(String value) {
        if (value == null) return null;
        return Encode.forUriComponent(value);
    }

    /**
     * 完整 URL 编码（ESAPI 兼容方法名）。
     *
     * 等价于 ESAPI: {@code ESAPI.encoder().encodeForURL(value)}
     */
    public static String encodeForURL(String value) {
        if (value == null) return null;
        return Encode.forUriComponent(value);
    }

    // =========================================================================
    // JavaScript 编码（输出到 JS 上下文中的字符串）
    // 等价于 ESAPI: ESAPI.encoder().encodeForJavaScript(input)
    // =========================================================================

    /**
     * JavaScript 字符串编码。
     * 转义引号、反斜杠、换行符等在 JS 字符串中有特殊含义的字符。
     *
     * 等价于 ESAPI: {@code ESAPI.encoder().encodeForJavaScript(value)}
     */
    public static String encodeForJavaScript(String value) {
        if (value == null) return null;
        return Encode.forJavaScript(value);
    }

    // =========================================================================
    // CSS 编码（输出到 CSS 上下文中的字符串）
    // 等价于 ESAPI: ESAPI.encoder().encodeForCSS(input)
    // =========================================================================

    /**
     * CSS 属性值编码。
     * 防止 CSS 注入攻击（如 expression()、url() 注入）。
     *
     * 等价于 ESAPI: {@code ESAPI.encoder().encodeForCSS(value)}
     */
    public static String encodeForCSS(String value) {
        if (value == null) return null;
        return Encode.forCssString(value);
    }

    // =========================================================================
    // HTML 属性编码
    // 等价于 ESAPI: ESAPI.encoder().encodeForHTMLAttribute(input)
    // =========================================================================

    /**
     * HTML 属性值编码。
     * 对属性值中的特殊字符（引号、尖括号等）进行编码。
     *
     * 等价于 ESAPI: {@code ESAPI.encoder().encodeForHTMLAttribute(value)}
     */
    public static String encodeForHTMLAttribute(String value) {
        if (value == null) return null;
        return Encode.forHtmlAttribute(value);
    }

    // =========================================================================
    // ESAPI 兼容别名方法
    // =========================================================================

    /**
     * HTML 编码（ESAPI 兼容方法名）。
     * 等价于 {@link #sanitizeString(String)}
     */
    public static String encodeForHTML(String value) {
        return sanitizeString(value);
    }
}
