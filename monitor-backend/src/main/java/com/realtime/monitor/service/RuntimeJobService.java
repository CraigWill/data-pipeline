package com.realtime.monitor.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.realtime.monitor.dto.RuntimeJob;
import com.realtime.monitor.dto.TaskConfig;
import com.realtime.monitor.repository.RuntimeJobRepository;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * 运行时作业管理服务
 */
@Slf4j
@Service
public class RuntimeJobService {

    private final RuntimeJobRepository runtimeJobRepository;
    private final FlinkService flinkService;
    private final CdcTaskService cdcTaskService;
    private final EmbeddedCdcService embeddedCdcService;
    private final com.realtime.monitor.oss.OssStorageService ossStorageService;

    /** 作业连续 RESTARTING 计数器（自愈用） */
    private final java.util.concurrent.ConcurrentHashMap<String, Integer> restartCounter = new java.util.concurrent.ConcurrentHashMap<>();
    /** 连续重启达到此阈值触发自愈（每次同步约 30s，6 次约 3 分钟） */
    private static final int RESTART_THRESHOLD = 6;

    public RuntimeJobService(RuntimeJobRepository runtimeJobRepository, 
                            FlinkService flinkService,
                            @Lazy CdcTaskService cdcTaskService,
                            @Lazy EmbeddedCdcService embeddedCdcService,
                            com.realtime.monitor.oss.OssStorageService ossStorageService) {
        this.runtimeJobRepository = runtimeJobRepository;
        this.flinkService = flinkService;
        this.cdcTaskService = cdcTaskService;
        this.embeddedCdcService = embeddedCdcService;
        this.ossStorageService = ossStorageService;
    }

    @PostConstruct
    public void loadRunningJobsOnStartup() {
        log.info("加载运行中的作业...");
        // 延迟执行，等待 Flink 集群就绪
        new Thread(() -> {
            try {
                Thread.sleep(30000); // 等待 30 秒让 Flink 集群及 HA 作业恢复完成
                restoreRunningJobs();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "job-restore-thread").start();
    }

    /**
     * 恢复运行中的作业
     */
    private void restoreRunningJobs() {
        try {
            List<RuntimeJob> runningJobs = runtimeJobRepository.findRunningJobs();
            log.info("找到 {} 个需要检查的运行时作业", runningJobs.size());
            
            if (runningJobs.isEmpty()) {
                return;
            }

            // 等待 Flink 集群就绪
            if (!waitForFlinkReady(30)) {
                log.warn("Flink 集群未就绪，跳过作业恢复");
                return;
            }

            // 等待 HA 自动恢复的 job 从 RECONCILING/RESTARTING 稳定到 RUNNING
            waitForHaJobsStable(20);

            // 获取 Flink 中当前运行的作业（id -> name）
            Map<String, String> flinkRunningJobMap = getFlinkRunningJobMap();
            log.info("Flink 集群中有 {} 个运行中的作业: {}", flinkRunningJobMap.size(), flinkRunningJobMap);

            // 检查每个运行时作业
            for (RuntimeJob job : runningJobs) {
                try {
                    checkAndRestoreJob(job, flinkRunningJobMap);
                } catch (Exception e) {
                    log.error("检查/恢复作业 {} 失败: {}", job.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("恢复运行中的作业失败", e);
        }
    }

    /**
     * 等待 Flink 集群就绪
     */
    private boolean waitForFlinkReady(int maxRetries) {
        for (int i = 0; i < maxRetries; i++) {
            try {
                Map<String, Object> overview = flinkService.getClusterOverview();
                int totalSlots = ((Number) overview.getOrDefault("slots-total", 0)).intValue();
                if (totalSlots > 0) {
                    log.info("Flink 集群已就绪，总槽位: {}", totalSlots);
                    return true;
                }
            } catch (Exception e) {
                log.debug("等待 Flink 集群就绪... ({})", i + 1);
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * 等待 HA 自动恢复的 job 从 RECONCILING/RESTARTING 稳定到 RUNNING 或 FAILED
     * 最多等待 maxRetries * 3s，避免无限等待
     */
    private void waitForHaJobsStable(int maxRetries) {
        for (int i = 0; i < maxRetries; i++) {
            try {
                List<Map<String, Object>> jobs = flinkService.getJobs();
                boolean hasTransient = jobs.stream().anyMatch(j -> {
                    String state = (String) j.get("state");
                    return "RECONCILING".equals(state) || "RESTARTING".equals(state) || "CREATED".equals(state);
                });
                if (!hasTransient) {
                    log.info("所有 Flink 作业状态已稳定");
                    return;
                }
                log.info("等待 Flink HA 作业恢复稳定... ({}/{})", i + 1, maxRetries);
            } catch (Exception e) {
                log.debug("查询 Flink 作业状态失败: {}", e.getMessage());
            }
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.warn("等待 HA 作业稳定超时，继续执行恢复逻辑");
    }

    /**
     * 获取 Flink 中运行的作业：Map<jobId, jobName>
     * 包含 RUNNING / RECONCILING / RESTARTING 状态，避免 HA 恢复窗口期误判为无作业
     */
    private Map<String, String> getFlinkRunningJobMap() {
        Map<String, String> jobMap = new HashMap<>();
        try {
            List<Map<String, Object>> jobs = flinkService.getJobs();
            for (Map<String, Object> job : jobs) {
                String state = (String) job.get("state");
                if ("RUNNING".equals(state) || "RECONCILING".equals(state) || "RESTARTING".equals(state)) {
                    // Flink REST API 返回的字段是 "jid"，不是 "id"
                    String id = (String) job.getOrDefault("jid", job.get("id"));
                    String name = (String) job.get("name");
                    if (id != null) {
                        jobMap.put(id, name != null ? name : "");
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取 Flink 运行作业列表失败", e);
        }
        return jobMap;
    }

    /**
     * 检查并恢复单个作业
     * 优先检查 Flink 中是否已有同名作业（HA 自动恢复场景），避免重复提交
     */
    private void checkAndRestoreJob(RuntimeJob job, Map<String, String> flinkRunningJobMap) {
        String flinkJobId = job.getFlinkJobId();

        // 1. 如果记录的 flinkJobId 仍在运行，无需任何操作
        if (flinkJobId != null && flinkRunningJobMap.containsKey(flinkJobId)) {
            log.info("作业 {} (Flink: {}) 仍在运行，无需恢复", job.getId(), flinkJobId);
            return;
        }

        // 2. 检查是否有 taskId 可以用于恢复
        if (job.getTaskId() == null) {
            log.warn("作业 {} 没有关联的 taskId，无法恢复", job.getId());
            runtimeJobRepository.updateStatus(job.getId(), "FAILED", "无法恢复：缺少 taskId");
            return;
        }

        TaskConfig taskConfig;
        try {
            taskConfig = cdcTaskService.loadTaskConfig(job.getTaskId());
        } catch (Exception e) {
            log.error("加载任务配置失败: taskId={}", job.getTaskId(), e);
            runtimeJobRepository.updateStatus(job.getId(), "FAILED", "加载任务配置失败: " + e.getMessage());
            return;
        }

        // 3. 检查 Flink 中是否已有同名作业（Flink HA 自动恢复的情况）
        String matchedFlinkJobId = null;
        String expectedJobName = taskConfig.getName();
        for (Map.Entry<String, String> entry : flinkRunningJobMap.entrySet()) {
            if (expectedJobName != null && expectedJobName.equals(entry.getValue())) {
                matchedFlinkJobId = entry.getKey();
                break;
            }
        }

        if (matchedFlinkJobId != null) {
            // Flink 已经在运行同名作业，只需更新 DB 中的 flinkJobId，不重复提交
            log.info("作业 {} 在 Flink 中已以同名 '{}' 运行 (Flink Job ID: {})，更新记录，不重复提交",
                    job.getId(), expectedJobName, matchedFlinkJobId);
            runtimeJobRepository.updateFlinkJobId(job.getId(), matchedFlinkJobId);
            return;
        }

        // 4. Flink 中确实没有该作业，重新提交（优先从 savepoint 恢复）
        log.info("作业 {} (任务: {}) 在 Flink 中未找到，正在重新提交...", job.getId(), expectedJobName);
        try {
            // 如果有 savepoint，注入到 taskConfig 中
            if (job.getLastSavepointPath() != null && !job.getLastSavepointPath().isEmpty()) {
                log.info("从 savepoint 恢复: {}", job.getLastSavepointPath());
                taskConfig.setSavepointPath(job.getLastSavepointPath());
            }
            Map<String, Object> result = embeddedCdcService.submitTask(taskConfig);

            if (result.get("success") == Boolean.TRUE && result.containsKey("job_id")) {
                String newFlinkJobId = (String) result.get("job_id");
                runtimeJobRepository.updateFlinkJobId(job.getId(), newFlinkJobId);
                log.info("作业 {} 恢复成功，新 Flink Job ID: {}", job.getId(), newFlinkJobId);
            } else {
                String error = result.containsKey("error") ? (String) result.get("error") : "未知错误";
                runtimeJobRepository.updateStatus(job.getId(), "FAILED", "恢复失败: " + error);
                log.error("作业 {} 恢复失败: {}", job.getId(), error);
            }
        } catch (Exception e) {
            log.error("恢复作业 {} 失败", job.getId(), e);
            runtimeJobRepository.updateStatus(job.getId(), "FAILED", "恢复异常: " + e.getMessage());
        }
    }

    /**
     * 定期触发 Savepoint（每小时），防止 checkpoint 文件丢失后无法恢复
     */
    @Scheduled(fixedDelay = 3600000, initialDelay = 300000) // 5分钟后首次，之后每小时
    public void scheduledSavepoint() {
        List<RuntimeJob> runningJobs = runtimeJobRepository.findRunningJobs();
        for (RuntimeJob job : runningJobs) {
            if (job.getFlinkJobId() == null) continue;
            try {
                Map<String, Object> result = flinkService.triggerSavepoint(job.getFlinkJobId());
                String location = (String) result.get("location");
                if (location != null) {
                    runtimeJobRepository.updateSavepoint(job.getId(), location);
                    log.info("定期 savepoint 完成: job={} path={}", job.getId(), location);
                    // 同步 savepoint 目录到 OSS
                    syncSavepointToOss(location, job.getId());
                }
            } catch (Exception e) {
                log.warn("定期 savepoint 失败: job={} err={}", job.getId(), e.getMessage());
            }
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
            
            if (flinkJob == null) {
                // Flink 不可用（连接失败/超时），跳过本次同步，不改变作业状态
                log.debug("Flink 不可用，跳过作业 {} 状态同步", job.getId());
                return;
            }
            
            if (flinkJob.isEmpty()) {
                // Flink 明确返回 404 — 作业确实不存在，标记为 LOST
                log.info("作业 {} (flinkJobId={}) 在 Flink 集群中不存在，标记为 LOST",
                        job.getId(), job.getFlinkJobId());
                runtimeJobRepository.updateStatus(job.getId(), "LOST", "作业在 Flink 集群中不存在");
                return;
            }
            
            String flinkState = (String) flinkJob.get("state");
            if (flinkState != null) {
                String newStatus = mapFlinkStateToStatus(flinkState);
                if (!newStatus.equals(job.getStatus())) {
                    log.info("作业 {} 状态变更: {} -> {}", job.getId(), job.getStatus(), newStatus);
                    runtimeJobRepository.updateStatus(job.getId(), newStatus, null);
                }

                // 自愈：检测作业是否卡在 RESTARTING（OceanBase CDC 位点过期会导致反复重启）
                if ("RESTARTING".equalsIgnoreCase(flinkState) || "FAILING".equalsIgnoreCase(flinkState)) {
                    int count = restartCounter.merge(job.getId(), 1, Integer::sum);
                    log.warn("作业 {} 处于 {} 状态，连续计数: {}/{}", job.getId(), flinkState, count, RESTART_THRESHOLD);
                    if (count >= RESTART_THRESHOLD) {
                        log.warn("作业 {} 持续重启超过阈值，触发自愈：取消并用全新位点重提（丢弃过期 savepoint）", job.getId());
                        autoHealStuckJob(job);
                        restartCounter.remove(job.getId());
                    }
                } else {
                    // 状态恢复正常，清除计数
                    restartCounter.remove(job.getId());
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

    /**
     * 自愈卡死的作业：取消当前 Flink 作业，清除过期 savepoint，用全新位点重新提交。
     *
     * <p>适用场景：OceanBase CDC 从过期 savepoint 恢复时，因 clog 已被回收
     * 导致 {@code OB_ERR_OUT_OF_LOWER_BOUND (-4233)} 反复重启。
     * 此时丢弃 savepoint、用当前时间戳作为起始位点是唯一可行的恢复方式。
     */
    private void autoHealStuckJob(RuntimeJob job) {
        try {
            // 1. 取消当前卡死的 Flink 作业
            if (job.getFlinkJobId() != null && !job.getFlinkJobId().isEmpty()) {
                try {
                    flinkService.cancelJob(job.getFlinkJobId());
                    log.info("自愈：已取消卡死作业 {} (FlinkJobId={})", job.getId(), job.getFlinkJobId());
                } catch (Exception e) {
                    log.warn("自愈：取消作业失败（可能已不存在）: {}", e.getMessage());
                }
            }

            // 2. 清除数据库中的过期 savepoint 记录，避免下次又从它恢复
            runtimeJobRepository.clearSavepoint(job.getId());

            Thread.sleep(3000); // 等待 slot 释放

            // 3. 加载任务配置，不带 savepoint 全新提交（使用当前时间作为 CDC 起始位点）
            TaskConfig taskConfig = cdcTaskService.loadTaskConfig(job.getTaskId());
            taskConfig.setSavepointPath(null);
            Map<String, Object> result = embeddedCdcService.submitTask(taskConfig);

            if (result.get("success") == Boolean.TRUE && result.containsKey("job_id")) {
                String newFlinkJobId = (String) result.get("job_id");
                runtimeJobRepository.updateFlinkJobId(job.getId(), newFlinkJobId);
                runtimeJobRepository.updateStatus(job.getId(), "RUNNING", null);
                log.info("自愈成功：作业 {} 已用全新位点重提，新 FlinkJobId={}", job.getId(), newFlinkJobId);
            } else {
                String error = result.containsKey("error") ? (String) result.get("error") : "未知错误";
                log.error("自愈失败：作业 {} 重提失败: {}", job.getId(), error);
            }
        } catch (Exception e) {
            log.error("自愈作业 {} 时发生异常: {}", job.getId(), e.getMessage(), e);
        }
    }

    /**
     * 同步 savepoint 目录到 OSS。
     * savepoint 路径格式如: file:///opt/flink/savepoints/savepoint-xxxx-yyyy
     */
    private void syncSavepointToOss(String savepointPath, String jobId) {
        if (!ossStorageService.isEnabled()) return;
        try {
            // 去掉 file:// 前缀
            String localPath = savepointPath.replaceFirst("^file://", "");
            java.io.File dir = new java.io.File(localPath);
            if (!dir.exists() || !dir.isDirectory()) {
                // savepoint 可能是单个文件
                java.io.File file = new java.io.File(localPath);
                if (file.exists()) {
                    String ossKey = "savepoints/" + jobId + "/" + file.getName();
                    ossStorageService.syncToOss(file, ossKey);
                }
                return;
            }
            // 遍历 savepoint 目录上传所有文件
            java.io.File[] files = dir.listFiles();
            if (files == null) return;
            for (java.io.File file : files) {
                if (file.isFile()) {
                    String ossKey = "savepoints/" + jobId + "/" + dir.getName() + "/" + file.getName();
                    ossStorageService.syncToOss(file, ossKey);
                }
            }
            log.debug("Savepoint 已同步到 OSS: job={} path={}", jobId, savepointPath);
        } catch (Exception e) {
            log.warn("Savepoint OSS 同步失败（不影响本地）: {}", e.getMessage());
        }
    }
}
