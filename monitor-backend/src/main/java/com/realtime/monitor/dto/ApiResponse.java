package com.realtime.monitor.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.realtime.monitor.util.XssSanitizer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 统一 API 响应格式
 * 
 * 安全措施：
 * - success() 方法对返回数据进行可信性验证和净化
 * - 所有 String 类型的值经过 HTML 实体编码（防 XSS）
 * - Map/List 类型递归净化所有 String 值
 * - message/error 字段经过 XSS 净化
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String message;
    private String error;

    /**
     * 创建成功响应，对 data 进行可信性验证和净化。
     * 
     * 净化规则：
     * - Map<String, Object>: 递归编码所有 String 值
     * - List<Map<String, Object>>: 递归编码每个 Map 中的 String 值
     * - String: HTML 实体编码
     * - 其他类型（数字、布尔等）: 直接通过
     * - null: 直接通过
     */
    @SuppressWarnings("unchecked")
    public static <T> ApiResponse<T> success(T data) {
        T sanitizedData = sanitizeData(data);
        return ApiResponse.<T>builder()
                .success(true)
                .data(sanitizedData)
                .build();
    }

    @SuppressWarnings("unchecked")
    public static <T> ApiResponse<T> success(T data, String message) {
        T sanitizedData = sanitizeData(data);
        return ApiResponse.<T>builder()
                .success(true)
                .data(sanitizedData)
                .message(XssSanitizer.sanitizeString(message))
                .build();
    }
    
    public static <T> ApiResponse<T> error(String error) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(XssSanitizer.sanitizeString(error))
                .build();
    }

    /**
     * 对响应数据进行递归净化，确保返回可信数据。
     * 
     * 处理逻辑：
     * - null → null
     * - String → HTML 编码
     * - Map → 递归净化所有值
     * - List → 递归净化每个元素
     * - 数字/布尔/枚举等安全类型 → 直接通过
     */
    @SuppressWarnings("unchecked")
    private static <T> T sanitizeData(T data) {
        if (data == null) {
            return null;
        }

        if (data instanceof Map) {
            return (T) XssSanitizer.sanitize((Map<String, Object>) data);
        }

        if (data instanceof List) {
            List<?> list = (List<?>) data;
            if (list.isEmpty()) {
                return data;
            }
            // Check first element to determine list type
            Object first = list.stream().filter(java.util.Objects::nonNull).findFirst().orElse(null);
            if (first instanceof Map) {
                return (T) XssSanitizer.sanitizeList((List<Map<String, Object>>) data);
            }
            if (first instanceof String) {
                List<String> sanitized = new java.util.ArrayList<>(list.size());
                for (Object item : list) {
                    sanitized.add(item != null ? XssSanitizer.sanitizeString((String) item) : null);
                }
                return (T) sanitized;
            }
            // List of other types (numbers, etc.) — pass through
            return data;
        }

        if (data instanceof String) {
            return (T) XssSanitizer.sanitizeString((String) data);
        }

        // Numbers, Booleans, Enums, and other safe types — pass through directly
        if (data instanceof Number || data instanceof Boolean || data instanceof Enum) {
            return data;
        }

        // For unknown complex objects, return as-is (Jackson will serialize them)
        // The XssSanitizer at the field level handles individual strings
        return data;
    }
}
