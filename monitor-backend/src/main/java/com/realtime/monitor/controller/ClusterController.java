package com.realtime.monitor.controller;

import com.realtime.monitor.dto.ApiResponse;
import com.realtime.monitor.service.FlinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 集群状态 API
 */
@Slf4j
@RestController
@RequestMapping("/api/cluster")
@RequiredArgsConstructor
public class ClusterController {
    
    private final FlinkService flinkService;
    
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> getClusterOverview() {
        try {
            return ApiResponse.success(flinkService.getClusterOverview());
        } catch (Exception e) {
            log.error("获取集群概览失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    @GetMapping("/taskmanagers")
    public ApiResponse<List<Map<String, Object>>> getTaskManagers() {
        try {
            return ApiResponse.success(flinkService.getTaskManagers());
        } catch (Exception e) {
            log.error("获取 TaskManager 列表失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    @GetMapping("/jobmanagers")
    public ApiResponse<List<Map<String, Object>>> getJobManagers() {
        try {
            return ApiResponse.success(flinkService.getJobManagerConfig());
        } catch (Exception e) {
            log.error("获取 JobManager 配置失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    @GetMapping("/jobs")
    public ApiResponse<List<Map<String, Object>>> getJobs() {
        try {
            return ApiResponse.success(flinkService.getJobs());
        } catch (Exception e) {
            log.error("获取作业列表失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }
}
