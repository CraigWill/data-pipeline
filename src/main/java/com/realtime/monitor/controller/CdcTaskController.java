package com.realtime.monitor.controller;

import com.realtime.monitor.dto.ApiResponse;
import com.realtime.monitor.dto.CdcSubmitRequest;
import com.realtime.monitor.dto.DataSourceConfig;
import com.realtime.monitor.dto.TaskConfig;
import com.realtime.monitor.service.CdcTaskService;
import com.realtime.monitor.service.EmbeddedCdcService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * CDC 任务管理 API
 */
@Slf4j
@RestController
@RequestMapping("/api/cdc")
@RequiredArgsConstructor
public class CdcTaskController {
    
    private final CdcTaskService cdcTaskService;
    private final EmbeddedCdcService embeddedCdcService;
    
    // ============================================
    // 数据源操作（兼容旧接口）
    // ============================================
    
    @PostMapping("/datasource/test")
    public ApiResponse<Map<String, Object>> testDataSource(@RequestBody DataSourceConfig config) {
        try {
            Map<String, Object> result = cdcTaskService.testConnection(config);
            return (boolean) result.get("success") 
                    ? ApiResponse.success(result) 
                    : ApiResponse.error((String) result.get("error"));
        } catch (Exception e) {
            log.error("测试数据源连接失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    @PostMapping("/datasource/schemas")
    public ApiResponse<List<String>> getSchemas(@RequestBody DataSourceConfig config) {
        try {
            return ApiResponse.success(cdcTaskService.discoverSchemas(config));
        } catch (Exception e) {
            log.error("获取 Schema 列表失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    @PostMapping("/datasource/tables")
    public ApiResponse<List<Map<String, Object>>> getTables(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> configMap = (Map<String, Object>) request.get("config");
            String schema = (String) request.get("schema");
            
            DataSourceConfig config = new DataSourceConfig();
            config.setHost((String) configMap.get("host"));
            config.setPort((Integer) configMap.get("port"));
            config.setUsername((String) configMap.get("username"));
            config.setPassword((String) configMap.get("password"));
            config.setSid((String) configMap.get("sid"));
            
            return ApiResponse.success(cdcTaskService.discoverTables(config, schema));
        } catch (Exception e) {
            log.error("获取表列表失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    // ============================================
    // CDC 任务管理
    // ============================================
    
    @GetMapping("/tasks")
    public ApiResponse<List<Map<String, Object>>> listTasks() {
        try {
            return ApiResponse.success(cdcTaskService.listTasks());
        } catch (Exception e) {
            log.error("获取任务列表失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    @PostMapping("/tasks")
    public ApiResponse<Map<String, Object>> createTask(@RequestBody TaskConfig config) {
        try {
            String taskId = cdcTaskService.saveTaskConfig(config);
            return ApiResponse.success(Map.of("id", taskId), "任务创建成功");
        } catch (Exception e) {
            log.error("创建任务失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    @GetMapping("/tasks/{taskId}")
    public ApiResponse<TaskConfig> getTask(@PathVariable String taskId) {
        try {
            return ApiResponse.success(cdcTaskService.loadTaskConfig(taskId));
        } catch (Exception e) {
            log.error("获取任务配置失败: {}", taskId, e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    @GetMapping("/tasks/{taskId}/detail")
    public ApiResponse<Map<String, Object>> getTaskDetail(@PathVariable String taskId) {
        try {
            return ApiResponse.success(cdcTaskService.getTaskDetail(taskId));
        } catch (Exception e) {
            log.error("获取任务详情失败: {}", taskId, e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    @DeleteMapping("/tasks/{taskId}")
    public ApiResponse<Void> deleteTask(@PathVariable String taskId) {
        try {
            cdcTaskService.deleteTask(taskId);
            return ApiResponse.success(null, "任务删除成功");
        } catch (Exception e) {
            log.error("删除任务失败: {}", taskId, e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    @PostMapping("/tasks/{taskId}/submit")
    public ApiResponse<Map<String, Object>> submitTask(@PathVariable String taskId) {
        try {
            if (taskId == null || taskId.trim().isEmpty() || "undefined".equalsIgnoreCase(taskId)) {
                return ApiResponse.error("无效的任务ID: " + taskId);
            }
            Map<String, Object> result = cdcTaskService.submitTask(taskId);
            if (result.containsKey("success") && Boolean.TRUE.equals(result.get("success"))) {
                return ApiResponse.success(result);
            } else {
                String error = result.containsKey("errors") ? result.get("errors").toString() : "提交失败";
                return ApiResponse.error(error);
            }
        } catch (Exception e) {
            log.error("提交任务失败: {}", taskId, e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    // ============================================
    // CDC 作业管理（嵌入式）
    // ============================================
    
    @GetMapping("/jobs")
    public ApiResponse<List<Map<String, Object>>> getRunningJobs() {
        try {
            return ApiResponse.success(embeddedCdcService.listJobs());
        } catch (Exception e) {
            log.error("获取运行中作业失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    @GetMapping("/jobs/{jobId}")
    public ApiResponse<Map<String, Object>> getJobStatus(@PathVariable String jobId) {
        try {
            return ApiResponse.success(embeddedCdcService.getJobDetail(jobId));
        } catch (Exception e) {
            log.error("获取作业状态失败: {}", jobId, e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    @PostMapping("/jobs/{jobId}/cancel")
    public ApiResponse<Map<String, Object>> cancelJob(@PathVariable String jobId) {
        try {
            Map<String, Object> result = embeddedCdcService.cancelJob(jobId);
            return (boolean) result.get("success") 
                    ? ApiResponse.success(result) 
                    : ApiResponse.error((String) result.get("error"));
        } catch (Exception e) {
            log.error("取消作业失败: {}", jobId, e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    // ============================================
    // 直接提交 CDC 任务（嵌入式）
    // ============================================
    
    @PostMapping("/submit")
    public ApiResponse<Map<String, Object>> submitDirect(@RequestBody CdcSubmitRequest request) {
        try {
            // 验证必需参数
            if (request.getHostname() == null || request.getHostname().isEmpty()) {
                return ApiResponse.error("缺少必需参数: hostname");
            }
            if (request.getUsername() == null || request.getUsername().isEmpty()) {
                return ApiResponse.error("缺少必需参数: username");
            }
            if (request.getPassword() == null || request.getPassword().isEmpty()) {
                return ApiResponse.error("缺少必需参数: password");
            }
            if (request.getDatabase() == null || request.getDatabase().isEmpty()) {
                return ApiResponse.error("缺少必需参数: database");
            }
            if (request.getSchema() == null || request.getSchema().isEmpty()) {
                return ApiResponse.error("缺少必需参数: schema");
            }
            if (request.getTables() == null || request.getTables().isEmpty()) {
                return ApiResponse.error("缺少必需参数: tables");
            }
            
            // 设置默认值
            if (request.getPort() == 0) {
                request.setPort(1521);
            }
            if (request.getOutputPath() == null || request.getOutputPath().isEmpty()) {
                request.setOutputPath("./output/cdc");
            }
            if (request.getParallelism() == 0) {
                request.setParallelism(2);
            }
            if (request.getSplitSize() == 0) {
                request.setSplitSize(8096);
            }
            
            Map<String, Object> result = cdcTaskService.submitDirect(request);
            if (result.containsKey("success") && Boolean.TRUE.equals(result.get("success"))) {
                return ApiResponse.success(result);
            } else {
                String error = result.containsKey("errors") ? result.get("errors").toString() : "提交失败";
                return ApiResponse.error(error);
            }
        } catch (Exception e) {
            log.error("直接提交任务失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }
}
