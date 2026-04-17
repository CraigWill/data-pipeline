package com.realtime.monitor.controller;

import com.realtime.monitor.config.AppConfig;
import com.realtime.monitor.dto.ApiResponse;
import com.realtime.monitor.service.FlinkService;
import com.realtime.monitor.service.OutputFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 健康检查 API
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {
    
    private final FlinkService flinkService;
    private final OutputFileService outputFileService;
    private final AppConfig appConfig;
    
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> healthCheck() {
        try {
            boolean flinkHealthy = flinkService.isFlinkHealthy();
            boolean outputHealthy = outputFileService.isOutputDirExists();
            
            Map<String, Object> data = new HashMap<>();
            data.put("status", (flinkHealthy && outputHealthy) ? "healthy" : "unhealthy");
            data.put("flink", flinkHealthy ? "connected" : "disconnected");
            data.put("output_dir", outputHealthy ? "exists" : "missing");
            data.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("健康检查失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    
        @PreAuthorize("hasRole('ADMIN')")
        @GetMapping("/system/info")
        public ApiResponse<Map<String, Object>> getSystemInfo() {
            try {
                Map<String, Object> data = new HashMap<>();
                data.put("flink_url", appConfig.getFlinkRestUrl());
                data.put("output_path", appConfig.getOutputPath());
                data.put("flink_connected", flinkService.isFlinkHealthy());
                data.put("output_dir_exists", outputFileService.isOutputDirExists());

                // JVM / runtime info (works inside containers)
                Runtime runtime = Runtime.getRuntime();
                Map<String, Object> jvmInfo = new HashMap<>();
                jvmInfo.put("available_processors", runtime.availableProcessors());
                jvmInfo.put("max_memory_mb", runtime.maxMemory() / (1024 * 1024));
                jvmInfo.put("total_memory_mb", runtime.totalMemory() / (1024 * 1024));
                jvmInfo.put("free_memory_mb", runtime.freeMemory() / (1024 * 1024));
                jvmInfo.put("java_version", System.getProperty("java.version"));
                data.put("jvm", jvmInfo);

                data.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

                return ApiResponse.success(data);
            } catch (Exception e) {
                log.error("获取系统信息失败", e);
                return ApiResponse.error(e.getMessage());
            }
        }

}
