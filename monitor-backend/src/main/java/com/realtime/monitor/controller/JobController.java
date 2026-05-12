package com.realtime.monitor.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.realtime.monitor.dto.ApiResponse;
import com.realtime.monitor.repository.AppConfigRepository;
import com.realtime.monitor.service.FlinkService;
import static com.realtime.monitor.util.XssSanitizer.sanitize;

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

    /** 配置键：savepoint 目标目录 */
    private static final String CONFIG_KEY_SAVEPOINT_DIR = "savepoint.target.directory";

    /** 默认 savepoint 目录（数据库配置不可用时的兜底值） */
    private static final String DEFAULT_SAVEPOINT_DIR = "file:///opt/flink/savepoints";

    private final FlinkService flinkService;
    private final AppConfigRepository appConfigRepository;

    /**
     * 从数据库 app_config 表获取允许的 savepoint 目录。
     * 如果数据库不可用，使用默认值。
     */
    private String getSavepointBaseDir() {
        return appConfigRepository.getValue(CONFIG_KEY_SAVEPOINT_DIR, DEFAULT_SAVEPOINT_DIR);
    }

    /**
     * 验证 savepoint 目录是否在允许范围内。
     * 允许的基础目录从 app_config 表动态读取。
     */
    private void validateSavepointDirectory(String dir) {
        String allowedBase = getSavepointBaseDir();
        if (dir == null || !dir.startsWith(allowedBase)) {
            log.warn("Invalid savepoint directory rejected: {}", dir);
            throw new IllegalArgumentException("Invalid targetDirectory: must be under the permitted savepoint base");
        }
        // 额外安全检查：防止路径遍历
        if (dir.contains("..")) {
            log.warn("Path traversal attempt in savepoint directory: {}", dir);
            throw new IllegalArgumentException("Invalid targetDirectory: path traversal not allowed");
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
     * savepoint 目录从 app_config 表读取，不接受外部输入
     */
    @PostMapping("/{jobId}/stop")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stopJobWithSavepoint(
            @PathVariable String jobId) {
        try {
            String targetDirectory = getSavepointBaseDir();
            Map<String, Object> result = flinkService.stopJobWithSavepoint(jobId, targetDirectory);
            return ResponseEntity.ok(ApiResponse.success(sanitize(result), "作业已停止，Savepoint 已创建"));
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
