package com.realtime.pipeline.consistency;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据验证结果
 * 包含验证是否通过以及错误信息列表
 */
@Data
@AllArgsConstructor
public class ValidationResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 验证是否通过
     */
    private boolean valid;

    /**
     * 错误信息列表
     */
    private List<String> errors;

    /**
     * 创建成功的验证结果
     */
    public static ValidationResult success() {
        return new ValidationResult(true, new ArrayList<>());
    }

    /**
     * 创建失败的验证结果
     */
    public static ValidationResult failure(String error) {
        List<String> errors = new ArrayList<>();
        errors.add(error);
        return new ValidationResult(false, errors);
    }

    /**
     * 创建失败的验证结果
     */
    public static ValidationResult failure(List<String> errors) {
        return new ValidationResult(false, errors);
    }

    /**
     * 获取错误信息的字符串表示
     */
    public String getErrorMessage() {
        if (errors == null || errors.isEmpty()) {
            return "";
        }
        return String.join("; ", errors);
    }

    /**
     * 判断是否有错误
     */
    public boolean hasErrors() {
        return !valid || (errors != null && !errors.isEmpty());
    }
}
