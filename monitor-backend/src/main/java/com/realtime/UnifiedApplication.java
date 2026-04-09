package com.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 统一应用入口
 * 
 * 集成了 Flink CDC 和 Spring Boot 监控后端的单体应用
 * 
 * 特性：
 * - 嵌入式 Flink 集群（MiniCluster）
 * - Spring Boot REST API
 * - 通过 API 提交和管理 CDC 任务
 * - 无需外部 Flink 集群
 * - 异步作业管理和状态同步
 * 
 * 使用方式：
 * - 启动应用: java -jar app.jar
 * - 访问 API: http://localhost:5001
 * - 提交任务: POST http://localhost:5001/api/cdc/submit
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class UnifiedApplication {
    private static final Logger LOG = LoggerFactory.getLogger(UnifiedApplication.class);

    public static void main(String[] args) {
        LOG.info("=== 实时数据管道统一应用 ===");
        LOG.info("启动 Spring Boot + 嵌入式 Flink CDC...");
        
        SpringApplication.run(UnifiedApplication.class, args);
        
        LOG.info("应用已启动");
        LOG.info("API 地址: http://localhost:5001");
        LOG.info("健康检查: http://localhost:5001/actuator/health");
    }
}
