package com.realtime.monitor.controller;

import com.realtime.monitor.dto.ApiResponse;
import com.realtime.monitor.service.FlinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Flink 作业管理 API
 */
@Slf4j
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {
    
    private final FlinkService flinkService;
    
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> getJobs() {
        try {
            return ApiResponse.success(flinkService.getJobs());
        } catch (Exception e) {
            log.error("获取作业列表失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    @GetMapping("/{jobId}")
    public ApiResponse<Map<String, Object>> getJobDetail(@PathVariable String jobId) {
        try {
            return ApiResponse.success(flinkService.getJobDetail(jobId));
        } catch (Exception e) {
            log.error("获取作业详情失败: {}", jobId, e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    @GetMapping("/{jobId}/metrics")
    public ApiResponse<Map<String, Object>> getJobMetrics(@PathVariable String jobId) {
        try {
            return ApiResponse.success(flinkService.getJobMetrics(jobId));
        } catch (Exception e) {
            log.error("获取作业指标失败: {}", jobId, e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    @PostMapping("/{jobId}/cancel")
    public ApiResponse<Void> cancelJob(@PathVariable String jobId) {
        try {
            flinkService.cancelJob(jobId);
            return ApiResponse.success(null, "作业 " + jobId + " 已取消");
        } catch (Exception e) {
            log.error("取消作业失败: {}", jobId, e);
            return ApiResponse.error(e.getMessage());
        }
    }
}
