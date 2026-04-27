package com.realtime.monitor.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.realtime.monitor.dto.ApiResponse;
import com.realtime.monitor.service.FlinkService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Flink 作业管理 API
 */
@Slf4j
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private static final String SAVEPOINT_BASE = System.getProperty("flink.savepoint.base-dir", "file:///opt/flink/savepoints");

    private final FlinkService flinkService;

    private void validateSavepointDirectory(String dir) {
        if (dir == null || !dir.startsWith(SAVEPOINT_BASE)) {
            // Log the actual value server-side only — never reflect it in the response
            log.warn("Invalid savepoint directory rejected: {}", dir);
            throw new IllegalArgumentException("Invalid targetDirectory: must be under the permitted savepoint base");
        }
    }
    
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
            Map<String, Object> result = flinkService.getJobDetail(jobId);
            if (result == null) {
                return ApiResponse.error("Flink 集群不可用");
            }
            return ApiResponse.success(result);
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
    
    /**
     * 带 Savepoint 停止作业（推荐方式，不丢失数据）
     */
    @PostMapping("/{jobId}/stop")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stopJobWithSavepoint(
            @PathVariable String jobId,
            @RequestParam(defaultValue = "file:///opt/flink/savepoints") String targetDirectory) {
        try {
            validateSavepointDirectory(targetDirectory);
            Map<String, Object> result = flinkService.stopJobWithSavepoint(jobId, targetDirectory);
            return ResponseEntity.ok(ApiResponse.success(result, "作业已停止，Savepoint 已创建"));
        } catch (IllegalArgumentException e) {
            // Warning already logged inside validateSavepointDirectory — do not reflect user input
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid savepoint directory"));
        } catch (Exception e) {
            log.error("停止作业失败: {}", jobId, e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }
    
    /**
     * 获取作业的 Checkpoint 列表
     */
    @GetMapping("/{jobId}/checkpoints")
    public ApiResponse<Map<String, Object>> getCheckpoints(@PathVariable String jobId) {
        try {
            Map<String, Object> result = flinkService.getCheckpoints(jobId);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取 Checkpoint 列表失败: {}", jobId, e);
            return ApiResponse.error(e.getMessage());
        }
    }
}
