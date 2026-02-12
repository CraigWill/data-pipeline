package com.realtime.pipeline.job;

import com.realtime.pipeline.config.PipelineConfig;
import com.realtime.pipeline.model.JobStatus;
import com.realtime.pipeline.util.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JobManager使用示例
 * 
 * 演示如何使用JobManager进行作业管理:
 * 1. 查询作业状态
 * 2. 触发Savepoint
 * 3. 停止作业
 * 4. 停止作业并保存Savepoint
 * 
 * 验证需求: 4.1
 */
public class JobManagerExample {
    private static final Logger logger = LoggerFactory.getLogger(JobManagerExample.class);
    
    public static void main(String[] args) {
        try {
            // 1. 加载配置
            PipelineConfig config = ConfigLoader.loadConfig("application.yml");
            logger.info("Configuration loaded");
            
            // 2. 创建JobManager
            FlinkJobManager jobManager = FlinkJobManager.fromConfig(config);
            logger.info("JobManager created");
            
            // 假设有一个正在运行的作业
            String jobId = "example-job-id";
            
            // 3. 查询作业状态
            logger.info("=== Querying Job Status ===");
            try {
                JobStatus status = jobManager.getJobStatus(jobId);
                logger.info("Job ID: {}", status.getJobId());
                logger.info("Job Name: {}", status.getJobName());
                logger.info("Job State: {}", status.getState());
                logger.info("Is Running: {}", status.isRunning());
            } catch (Exception e) {
                logger.warn("Failed to get job status (job may not exist): {}", e.getMessage());
            }
            
            // 4. 触发Savepoint（不停止作业）
            logger.info("\n=== Triggering Savepoint ===");
            String savepointPath = config.getFlink().getCheckpointDir() + "/savepoints";
            try {
                String actualSavepointPath = jobManager.triggerSavepoint(jobId, savepointPath);
                logger.info("Savepoint created at: {}", actualSavepointPath);
            } catch (Exception e) {
                logger.warn("Failed to trigger savepoint: {}", e.getMessage());
            }
            
            // 5. 停止作业并保存Savepoint
            logger.info("\n=== Stopping Job with Savepoint ===");
            try {
                String finalSavepointPath = jobManager.stopJobWithSavepoint(jobId, savepointPath);
                logger.info("Job stopped with savepoint at: {}", finalSavepointPath);
            } catch (Exception e) {
                logger.warn("Failed to stop job with savepoint: {}", e.getMessage());
            }
            
            // 6. 简单停止作业（不保存Savepoint）
            logger.info("\n=== Stopping Job ===");
            try {
                jobManager.stopJob(jobId);
                logger.info("Job stopped successfully");
            } catch (Exception e) {
                logger.warn("Failed to stop job: {}", e.getMessage());
            }
            
            // 7. 取消作业
            logger.info("\n=== Canceling Job ===");
            try {
                jobManager.cancelJob(jobId);
                logger.info("Job canceled successfully");
            } catch (Exception e) {
                logger.warn("Failed to cancel job: {}", e.getMessage());
            }
            
            // 8. 查看活跃作业列表
            logger.info("\n=== Active Jobs ===");
            logger.info("Active job count: {}", jobManager.getActiveJobIds().size());
            jobManager.getActiveJobIds().forEach(id -> 
                logger.info("Active job: {}", id)
            );
            
            logger.info("\n=== Example Completed ===");
            
        } catch (Exception e) {
            logger.error("Error in JobManager example", e);
        }
    }
    
    /**
     * 演示如何在实际应用中使用JobManager
     * 
     * 典型的使用场景:
     * 1. 定期触发Savepoint进行备份
     * 2. 在配置更新前停止作业并保存Savepoint
     * 3. 监控作业状态并在失败时重启
     */
    public static void demonstrateTypicalUsage() {
        logger.info("=== Typical Usage Scenarios ===");
        
        // 场景1: 定期备份
        logger.info("\nScenario 1: Periodic Backup");
        logger.info("Schedule a task to trigger savepoint every hour:");
        logger.info("  jobManager.triggerSavepoint(jobId, savepointPath);");
        
        // 场景2: 配置更新
        logger.info("\nScenario 2: Configuration Update");
        logger.info("Before updating configuration:");
        logger.info("  1. Stop job with savepoint: jobManager.stopJobWithSavepoint(jobId, path);");
        logger.info("  2. Update configuration");
        logger.info("  3. Restart job from savepoint: FlinkPipelineMain with -s parameter");
        
        // 场景3: 故障恢复
        logger.info("\nScenario 3: Failure Recovery");
        logger.info("Monitor job status and restart on failure:");
        logger.info("  JobStatus status = jobManager.getJobStatus(jobId);");
        logger.info("  if (status.isFailed()) {");
        logger.info("    // Restart job from last checkpoint/savepoint");
        logger.info("  }");
        
        // 场景4: 优雅关闭
        logger.info("\nScenario 4: Graceful Shutdown");
        logger.info("Before system maintenance:");
        logger.info("  1. Trigger savepoint: jobManager.triggerSavepoint(jobId, path);");
        logger.info("  2. Wait for savepoint completion");
        logger.info("  3. Stop job: jobManager.stopJob(jobId);");
    }
}
