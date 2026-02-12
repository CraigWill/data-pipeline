package com.realtime.pipeline.util;

import java.util.UUID;

/**
 * ID生成器工具类
 * 用于生成唯一标识符
 */
public class IdGenerator {

    /**
     * 生成UUID格式的唯一标识符
     * @return UUID字符串
     */
    public static String generateUUID() {
        return UUID.randomUUID().toString();
    }

    /**
     * 生成带前缀的唯一标识符
     * @param prefix 前缀
     * @return 带前缀的UUID字符串
     */
    public static String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString();
    }

    /**
     * 生成事件ID
     * @return 事件ID
     */
    public static String generateEventId() {
        return generateId("evt");
    }

    /**
     * 生成作业ID
     * @return 作业ID
     */
    public static String generateJobId() {
        return generateId("job");
    }

    /**
     * 生成检查点ID
     * @return 检查点ID
     */
    public static String generateCheckpointId() {
        return generateId("chk");
    }
}
