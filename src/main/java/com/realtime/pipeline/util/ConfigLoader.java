package com.realtime.pipeline.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.realtime.pipeline.config.PipelineConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Map;

/**
 * 配置加载器
 * 负责从YAML文件加载配置，并支持环境变量覆盖
 */
public class ConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    /**
     * 从默认配置文件加载配置
     * @return 管道配置对象
     * @throws IOException 如果加载失败
     */
    public static PipelineConfig loadConfig() throws IOException {
        return loadConfig("application.yml");
    }

    /**
     * 从指定配置文件加载配置
     * @param configPath 配置文件路径
     * @return 管道配置对象
     * @throws IOException 如果加载失败
     */
    public static PipelineConfig loadConfig(String configPath) throws IOException {
        logger.info("Loading configuration from: {}", configPath);

        PipelineConfig config;

        // 尝试从文件系统加载
        File configFile = new File(configPath);
        if (configFile.exists()) {
            logger.info("Loading configuration from file system: {}", configFile.getAbsolutePath());
            config = yamlMapper.readValue(configFile, PipelineConfig.class);
        } else {
            // 从classpath加载
            logger.info("Loading configuration from classpath: {}", configPath);
            try (InputStream inputStream = ConfigLoader.class.getClassLoader().getResourceAsStream(configPath)) {
                if (inputStream == null) {
                    throw new IOException("Configuration file not found: " + configPath);
                }
                config = yamlMapper.readValue(inputStream, PipelineConfig.class);
            }
        }

        // 应用环境变量覆盖
        applyEnvironmentOverrides(config);

        // 验证配置
        config.validate();

        logger.info("Configuration loaded and validated successfully");
        return config;
    }

    /**
     * 应用环境变量覆盖配置
     * 环境变量格式: PIPELINE_<SECTION>_<KEY>
     * 例如: PIPELINE_DATABASE_HOST, PIPELINE_FLINK_PARALLELISM
     */
    private static void applyEnvironmentOverrides(PipelineConfig config) {
        logger.info("Applying environment variable overrides");
        Map<String, String> env = System.getenv();

        // 覆盖数据库配置
        if (config.getDatabase() != null) {
            overrideFromEnv(config.getDatabase(), "PIPELINE_DATABASE_", env);
        }

        // 覆盖DataHub配置
        if (config.getDatahub() != null) {
            overrideFromEnv(config.getDatahub(), "PIPELINE_DATAHUB_", env);
        }

        // 覆盖Flink配置
        if (config.getFlink() != null) {
            overrideFromEnv(config.getFlink(), "PIPELINE_FLINK_", env);
        }

        // 覆盖输出配置
        if (config.getOutput() != null) {
            overrideFromEnv(config.getOutput(), "PIPELINE_OUTPUT_", env);
        }

        // 覆盖监控配置
        if (config.getMonitoring() != null) {
            overrideFromEnv(config.getMonitoring(), "PIPELINE_MONITORING_", env);
        }
    }

    /**
     * 从环境变量覆盖对象字段
     */
    private static void overrideFromEnv(Object obj, String prefix, Map<String, String> env) {
        Class<?> clazz = obj.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            String envKey = prefix + field.getName().toUpperCase();
            String envValue = env.get(envKey);

            if (envValue != null) {
                try {
                    field.setAccessible(true);
                    Object convertedValue = convertValue(envValue, field.getType());
                    field.set(obj, convertedValue);
                    logger.info("Override config from environment: {} = {}", envKey, 
                               field.getName().toLowerCase().contains("password") ? "***" : envValue);
                } catch (Exception e) {
                    logger.warn("Failed to override config from environment: {}", envKey, e);
                }
            }
        }
    }

    /**
     * 转换环境变量值为目标类型
     */
    private static Object convertValue(String value, Class<?> targetType) {
        if (targetType == String.class) {
            return value;
        } else if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(value);
        } else if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(value);
        } else if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(value);
        } else if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(value);
        } else if (targetType == java.util.List.class) {
            // 支持逗号分隔的列表，例如: "table1,table2,table3"
            String[] items = value.split(",");
            java.util.List<String> list = new java.util.ArrayList<>();
            for (String item : items) {
                list.add(item.trim());
            }
            return list;
        } else {
            throw new IllegalArgumentException("Unsupported type: " + targetType);
        }
    }
}
