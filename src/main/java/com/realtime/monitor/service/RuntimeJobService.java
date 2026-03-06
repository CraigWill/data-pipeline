package com.realtime.monitor.service;

import com.realtime.monitor.dto.RuntimeJob;
import com.realtime.monitor.dto.TaskConfig;
import com.realtime.monitor.repository.RuntimeJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 运行时作业管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuntimeJobService {

    private final RuntimeJobRepository runtimeJobRepository;
    private final FlinkService flinkService;

    /**
     * 应用启动时加载运行中的作业
     */
    @PostConstruct
    public void loadRunningJobsOnStartup() {
        log.info("加载运行中的作业...");
        try {
            List<RuntimeJob> runningJobs = runtimeJobRepository.findRunningJobs();
            log.info("找到 {} 个运行中的作业", runningJobs.size());
            
            // 验证这些作业在 Flink 中的实际状态
            for (RuntimeJob job : runningJobs) {
                if (job.getFlinkJobId() != null) {
                    syncJobStatus(job);
                }
            }
        } catch (Exception e) {
            log.error("加载运行中的作业失败", e);
        }
    }

    /**
     * 定期同步作业状态（每30秒）
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 10000)
    public void syncAllJobStatuses() {
        try {
            List<RuntimeJob> runningJobs = runtimeJobRepository.findRunningJobs();
            for (RuntimeJob job : runningJobs) {
                if (job.getFlinkJobId() != null) {
                    syncJobStatus(job);
                }
            }
        } catch (Exception e) {
            log.error("同步作业状态失败", e);
        }
    }

    /**
     * 同步单个作业状态
     */
    private void syncJobStatus(RuntimeJob job) {
        try {
            Map<String, Object> flinkJob = flinkService.getJobDetail(job.getFlinkJobId());
            String flinkState = (String) flinkJob.get("state");
            
            if (flinkState != null) {
                String newStatus = mapFlinkStateToStatus(flinkState);
                if (!newStatus.equals(job.getStatus())) {
                    log.info("作业 {} 状态变更: {} -> {}", job.getId(), job.getStatus(), newStatus);
                    runtimeJobRepository.updateStatus(job.getId(), newStatus, null);
                }
            }
        } catch (Exception e) {
            log.warn("同步作业 {} 状态失败: {}", job.getId(), e.getMessage());
        }
    }

    private String mapFlinkStateToStatus(String flinkState) {
        if ("RUNNING".equals(flinkState)) {
            return "RUNNING";
        } else if ("FINISHED".equals(flinkState)) {
            return "FINISHED";
        } else if ("FAILED".equals(flinkState)) {
            return "FAILED";
        } else if ("CANCELED".equals(flinkState) || "CANCELLING".equals(flinkState)) {
            return "CANCELED";
        } else {
            return "RUNNING";
        }
    }

    /**
     * 创建运行时作业记录
     */
    public RuntimeJob createRuntimeJob(TaskConfig taskConfig) {
        RuntimeJob job = new RuntimeJob();
        job.setId("runtime-" + System.currentTimeMillis());
        job.setTaskId(taskConfig.getId());
        job.setJobName(taskConfig.getName());
        job.setStatus("SUBMITTING");
        job.setSchemaName(taskConfig.getSchema());
        job.setTables(taskConfig.getTables());
        job.setParallelism(taskConfig.getParallelism());
        job.setSubmitTime(Instant.now().toString());
        
        runtimeJobRepository.save(job);
        log.info("创建运行时作业记录: {}", job.getId());
        
        return job;
    }

    /**
     * 异步更新 Flink Job ID
     */
    @Async
    public CompletableFuture<Void> updateFlinkJobIdAsync(String runtimeJobId, String flinkJobId) {
        return CompletableFuture.runAsync(() -> {
            try {
                // 等待一小段时间确保作业已经在 Flink 中注册
                Thread.sleep(1000);
                runtimeJobRepository.updateFlinkJobId(runtimeJobId, flinkJobId);
                log.info("异步更新 Flink Job ID 完成: {} -> {}", runtimeJobId, flinkJobId);
            } catch (Exception e) {
                log.error("异步更新 Flink Job ID 失败", e);
            }
        });
    }

    /**
     * 更新作业状态
     */
    public void updateJobStatus(String runtimeJobId, String status, String errorMessage) {
        runtimeJobRepository.updateStatus(runtimeJobId, status, errorMessage);
    }

    /**
     * 取消并删除作业
     */
    public void cancelAndDeleteJob(String runtimeJobId) {
        RuntimeJob job = runtimeJobRepository.findById(runtimeJobId);
        if (job == null) {
            throw new RuntimeException("运行时作业不存在: " + runtimeJobId);
        }
        
        // 如果有 Flink Job ID，先取消作业
        if (job.getFlinkJobId() != null) {
            try {
                log.info("取消 Flink 作业: {}", job.getFlinkJobId());
                flinkService.cancelJob(job.getFlinkJobId());
            } catch (Exception e) {
                log.warn("取消 Flink 作业失败: {}", e.getMessage());
            }
        }
        
        // 从数据库删除
        runtimeJobRepository.deleteById(runtimeJobId);
        log.info("已删除运行时作业: {}", runtimeJobId);
    }

    /**
     * 获取所有运行时作业
     */
    public List<RuntimeJob> getAllRuntimeJobs() {
        return runtimeJobRepository.findAll();
    }

    /**
     * 获取运行中的作业
     */
    public List<RuntimeJob> getRunningJobs() {
        return runtimeJobRepository.findRunningJobs();
    }

    /**
     * 根据 ID 获取作业
     */
    public RuntimeJob getRuntimeJob(String id) {
        return runtimeJobRepository.findById(id);
    }

    /**
     * 检查是否有足够的 TaskManager 槽位
     */
    public boolean hasAvailableSlots(int requiredParallelism) {
        try {
            Map<String, Object> overview = flinkService.getClusterOverview();
            int totalSlots = ((Number) overview.getOrDefault("slots-total", 0)).intValue();
            int availableSlots = ((Number) overview.getOrDefault("slots-available", 0)).intValue();
            
            log.info("集群槽位: 总数={}, 可用={}, 需要={}", totalSlots, availableSlots, requiredParallelism);
            
            return availableSlots >= requiredParallelism;
        } catch (Exception e) {
            log.error("检查槽位失败", e);
            return false;
        }
    }

    /**
     * 检查表冲突
     */
    public List<RuntimeJob> checkTableConflicts(List<String> tables) {
        return runtimeJobRepository.findRunningJobsWithTables(tables);
    }

    /**
     * 验证是否可以提交新作业
     */
    public Map<String, Object> validateJobSubmission(TaskConfig taskConfig) {
        Map<String, Object> result = new HashMap<>();
        result.put("canSubmit", true);
        List<String> errors = new ArrayList<>();
        
        // 1. 检查槽位
        if (!hasAvailableSlots(taskConfig.getParallelism())) {
            errors.add("没有足够的 TaskManager 槽位（需要 " + taskConfig.getParallelism() + " 个）");
            result.put("canSubmit", false);
        }
        
        // 2. 检查表冲突
        List<RuntimeJob> conflicts = checkTableConflicts(taskConfig.getTables());
        if (!conflicts.isEmpty()) {
            List<String> conflictInfo = conflicts.stream()
                    .map(job -> job.getJobName() + " (ID: " + job.getId() + ")")
                    .collect(java.util.stream.Collectors.toList());
            errors.add("以下作业正在处理相同的表: " + String.join(", ", conflictInfo));
            result.put("canSubmit", false);
            result.put("conflicts", conflicts);
        }
        
        result.put("errors", errors);
        return result;
    }
}
