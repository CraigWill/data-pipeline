package com.realtime.monitor.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class XssSanitizerTest {

    @Test
    void testSanitizeString() {
        String unsafe = "<script>alert('xss')</script>";
        String safe = XssSanitizer.sanitizeString(unsafe);
        assertNotNull(safe);
        assertFalse(safe.contains("<script>"));
        assertTrue(safe.contains("&lt;script&gt;"));

        assertNull(XssSanitizer.sanitizeString(null));
        assertEquals("Safe String 123", XssSanitizer.sanitizeString("Safe String 123"));
    }

    @Test
    void testSanitizeMap() {
        Map<String, Object> unsafeMap = new HashMap<>();
        unsafeMap.put("name", "John <b onload=alert(1)>Doe</b>");
        unsafeMap.put("age", 30); // Number should not be affected
        unsafeMap.put("html", "<div class=\"test\">Hello</div>");

        Map<String, Object> safeMap = XssSanitizer.sanitize(unsafeMap);

        assertEquals(3, safeMap.size());
        assertEquals("John &lt;b onload=alert(1)&gt;Doe&lt;/b&gt;", safeMap.get("name"));
        assertEquals(30, safeMap.get("age"));
        assertEquals("&lt;div class=&#34;test&#34;&gt;Hello&lt;/div&gt;", safeMap.get("html"));
    }

    @Test
    void testSanitizeNestedListAndMap() {
        Map<String, Object> root = new HashMap<>();
        root.put("title", "<h1>Test</h1>");

        Map<String, Object> childMap = new HashMap<>();
        childMap.put("description", "<img src=x onerror=alert(1)>");
        root.put("child", childMap);

        List<Object> items = Arrays.asList(
                "Plain String",
                "<script>hack()</script>",
                childMap
        );
        root.put("items", items);

        Map<String, Object> safeRoot = XssSanitizer.sanitize(root);

        // Check root scalar
        assertEquals("&lt;h1&gt;Test&lt;/h1&gt;", safeRoot.get("title"));

        // Check nested map
        @SuppressWarnings("unchecked")
        Map<String, Object> safeChildMap = (Map<String, Object>) safeRoot.get("child");
        assertEquals("&lt;img src=x onerror=alert(1)&gt;", safeChildMap.get("description"));

        // Check nested list
        @SuppressWarnings("unchecked")
        List<Object> safeItems = (List<Object>) safeRoot.get("items");
        assertEquals(3, safeItems.size());
        assertEquals("Plain String", safeItems.get(0));
        assertEquals("&lt;script&gt;hack()&lt;/script&gt;", safeItems.get(1));

        @SuppressWarnings("unchecked")
        Map<String, Object> safeListItemMap = (Map<String, Object>) safeItems.get(2);
        assertEquals("&lt;img src=x onerror=alert(1)&gt;", safeListItemMap.get("description"));
    }

    @Test
    void testEncodeForUrl() {
        String unsafeUrlPath = "admin/api/v1/delete/1?id=<script>";
        String safePath = XssSanitizer.encodeForUrlPath(unsafeUrlPath);
        assertFalse(safePath.contains("<"));
        assertTrue(safePath.contains("%3Cscript%3E"));

        String unsafeParam = "key=<value>&other=test";
        String safeParam = XssSanitizer.encodeForUrlParam(unsafeParam);
        assertFalse(safeParam.contains("<"));
        assertTrue(safeParam.contains("%3Cvalue%3E"));
    }

    @Test
    void testEncodeForJavaScript() {
        String unsafeJs = "'; alert(1); //";
        String safeJs = XssSanitizer.encodeForJavaScript(unsafeJs);
        assertNotNull(safeJs);
        // The encoder escapes characters like quotes and semicolons.
        // It might not escape spaces or letters depending on the implementation.
        assertTrue(safeJs.contains("\\x27")); // escapes the single quote
    }
}
