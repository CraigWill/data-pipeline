package com.realtime.monitor.controller;

import com.realtime.monitor.dto.ApiResponse;
import com.realtime.monitor.dto.RuntimeJob;
import com.realtime.monitor.service.RuntimeJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 运行时作业管理 API
 */
@Slf4j
@RestController
@RequestMapping("/api/runtime-jobs")
@RequiredArgsConstructor
public class RuntimeJobController {
    
    private final RuntimeJobService runtimeJobService;
    
    /**
     * 获取所有运行时作业
     */
    @GetMapping
    public ApiResponse<List<RuntimeJob>> getAllRuntimeJobs() {
        try {
            return ApiResponse.success(runtimeJobService.getAllRuntimeJobs());
        } catch (Exception e) {
            log.error("获取运行时作业列表失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    /**
     * 获取运行中的作业
     */
    @GetMapping("/running")
    public ApiResponse<List<RuntimeJob>> getRunningJobs() {
        try {
            return ApiResponse.success(runtimeJobService.getRunningJobs());
        } catch (Exception e) {
            log.error("获取运行中作业失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    /**
     * 获取单个运行时作业
     */
    @GetMapping("/{id}")
    public ApiResponse<RuntimeJob> getRuntimeJob(@PathVariable String id) {
        try {
            RuntimeJob job = runtimeJobService.getRuntimeJob(id);
            if (job == null) {
                return ApiResponse.error("运行时作业不存在: " + id);
            }
            return ApiResponse.success(job);
        } catch (Exception e) {
            log.error("获取运行时作业失败: {}", id, e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    /**
     * 取消并删除作业
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> cancelAndDeleteJob(@PathVariable String id) {
        try {
            runtimeJobService.cancelAndDeleteJob(id);
            return ApiResponse.success(null, "作业已取消并删除");
        } catch (Exception e) {
            log.error("取消并删除作业失败: {}", id, e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    /**
     * 检查集群资源
     */
    @GetMapping("/cluster/resources")
    public ApiResponse<Map<String, Object>> checkClusterResources(
            @RequestParam(defaultValue = "1") int requiredParallelism) {
        try {
            boolean hasSlots = runtimeJobService.hasAvailableSlots(requiredParallelism);
            return ApiResponse.success(Map.of(
                    "hasAvailableSlots", hasSlots,
                    "requiredParallelism", requiredParallelism
            ));
        } catch (Exception e) {
            log.error("检查集群资源失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    /**
     * 检查表冲突
     */
    @PostMapping("/check-conflicts")
    public ApiResponse<Map<String, Object>> checkTableConflicts(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> tables = (List<String>) request.get("tables");
            if (tables == null || tables.isEmpty()) {
                return ApiResponse.error("表列表不能为空");
            }
            
            List<RuntimeJob> conflicts = runtimeJobService.checkTableConflicts(tables);
            return ApiResponse.success(Map.of(
                    "hasConflicts", !conflicts.isEmpty(),
                    "conflicts", conflicts
            ));
        } catch (Exception e) {
            log.error("检查表冲突失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }
}
