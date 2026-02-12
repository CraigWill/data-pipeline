package com.realtime.pipeline.job;

import com.realtime.pipeline.config.PipelineConfig;
import com.realtime.pipeline.model.JobStatus;
import org.apache.flink.api.common.JobID;
import org.apache.flink.client.program.rest.RestClusterClient;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestOptions;
import org.apache.flink.core.execution.JobClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Flink作业管理器实现
 * 
 * 使用Flink REST API管理作业的生命周期
 * 
 * 验证需求: 4.1
 */
public class FlinkJobManager implements JobManager {
    private static final Logger logger = LoggerFactory.getLogger(FlinkJobManager.class);
    
    private final String jobManagerHost;
    private final int jobManagerPort;
    private final Map<String, JobClient> activeJobs;
    private final Map<String, String> jobSavepointPaths;
    
    /**
     * 构造函数
     * 
     * @param jobManagerHost JobManager主机地址
     * @param jobManagerPort JobManager REST端口
     */
    public FlinkJobManager(String jobManagerHost, int jobManagerPort) {
        this.jobManagerHost = jobManagerHost;
        this.jobManagerPort = jobManagerPort;
        this.activeJobs = new ConcurrentHashMap<>();
        this.jobSavepointPaths = new ConcurrentHashMap<>();
        
        logger.info("FlinkJobManager initialized with host={}, port={}", 
            jobManagerHost, jobManagerPort);
    }
    
    /**
     * 从配置创建FlinkJobManager
     * 
     * @param config 管道配置
     * @return FlinkJobManager实例
     */
    public static FlinkJobManager fromConfig(PipelineConfig config) {
        String host = config.getFlink().getJobManagerHost();
        int port = config.getFlink().getJobManagerPort();
        return new FlinkJobManager(host, port);
    }
    
    @Override
    public String startJob(PipelineConfig config) throws Exception {
        logger.info("Starting Flink job...");
        
        // 注意: 实际的作业启动由FlinkPipelineMain.main()完成
        // 这里只是记录作业信息
        // 在实际部署中，这个方法会通过REST API提交作业
        
        throw new UnsupportedOperationException(
            "Job start should be done through FlinkPipelineMain.main(). " +
            "This method is for REST API based job submission which requires " +
            "a running Flink cluster."
        );
    }
    
    @Override
    public void stopJob(String jobId) throws Exception {
        logger.info("Stopping job: {}", jobId);
        
        JobClient jobClient = activeJobs.get(jobId);
        if (jobClient == null) {
            logger.warn("Job {} not found in active jobs, attempting to cancel via REST API", jobId);
            cancelJobViaRestApi(jobId);
            return;
        }
        
        try {
            // 取消作业
            jobClient.cancel().get(30, TimeUnit.SECONDS);
            logger.info("Job {} stopped successfully", jobId);
            
            // 从活跃作业列表中移除
            activeJobs.remove(jobId);
            
        } catch (Exception e) {
            logger.error("Failed to stop job {}", jobId, e);
            throw new Exception("Failed to stop job: " + jobId, e);
        }
    }
    
    @Override
    public String stopJobWithSavepoint(String jobId, String savepointPath) throws Exception {
        logger.info("Stopping job {} with savepoint to path: {}", jobId, savepointPath);
        
        JobClient jobClient = activeJobs.get(jobId);
        if (jobClient == null) {
            throw new IllegalArgumentException("Job not found: " + jobId);
        }
        
        try {
            // 触发Savepoint并停止作业
            String actualSavepointPath = jobClient
                .stopWithSavepoint(false, savepointPath)
                .get(5, TimeUnit.MINUTES);
            
            logger.info("Job {} stopped with savepoint at: {}", jobId, actualSavepointPath);
            
            // 记录Savepoint路径
            jobSavepointPaths.put(jobId, actualSavepointPath);
            
            // 从活跃作业列表中移除
            activeJobs.remove(jobId);
            
            return actualSavepointPath;
            
        } catch (Exception e) {
            logger.error("Failed to stop job {} with savepoint", jobId, e);
            throw new Exception("Failed to stop job with savepoint: " + jobId, e);
        }
    }
    
    @Override
    public String restartJob(String jobId, PipelineConfig config) throws Exception {
        logger.info("Restarting job: {}", jobId);
        
        // 注意: 实际的重启需要重新提交作业
        // 这里只是演示逻辑，实际实现需要通过REST API或重新运行main方法
        
        throw new UnsupportedOperationException(
            "Job restart requires resubmitting the job with savepoint restore settings. " +
            "Use FlinkPipelineMain to restart the job."
        );
    }
    
    @Override
    public String triggerSavepoint(String jobId, String savepointPath) throws Exception {
        logger.info("Triggering savepoint for job {} to path: {}", jobId, savepointPath);
        
        JobClient jobClient = activeJobs.get(jobId);
        if (jobClient == null) {
            throw new IllegalArgumentException("Job not found: " + jobId);
        }
        
        try {
            // 触发Savepoint（不停止作业）
            CompletableFuture<String> savepointFuture = jobClient.triggerSavepoint(savepointPath);
            String actualSavepointPath = savepointFuture.get(5, TimeUnit.MINUTES);
            
            logger.info("Savepoint triggered successfully for job {}: {}", 
                jobId, actualSavepointPath);
            
            // 记录Savepoint路径
            jobSavepointPaths.put(jobId, actualSavepointPath);
            
            return actualSavepointPath;
            
        } catch (Exception e) {
            logger.error("Failed to trigger savepoint for job {}", jobId, e);
            throw new Exception("Failed to trigger savepoint: " + jobId, e);
        }
    }
    
    @Override
    public JobStatus getJobStatus(String jobId) throws Exception {
        logger.debug("Querying status for job: {}", jobId);
        
        JobClient jobClient = activeJobs.get(jobId);
        if (jobClient == null) {
            // 如果本地没有找到，尝试通过REST API查询
            return getJobStatusViaRestApi(jobId);
        }
        
        try {
            // 获取作业状态
            org.apache.flink.api.common.JobStatus flinkStatus = jobClient
                .getJobStatus()
                .get(10, TimeUnit.SECONDS);
            
            // 转换为我们的JobStatus模型
            JobStatus status = JobStatus.builder()
                .jobId(jobId)
                .jobName("Realtime Data Pipeline")
                .state(flinkStatus.name())
                .startTime(System.currentTimeMillis()) // 简化实现
                .duration(0L)
                .parallelism(0)
                .metrics(new HashMap<>())
                .build();
            
            logger.debug("Job {} status: {}", jobId, status.getState());
            
            return status;
            
        } catch (Exception e) {
            logger.error("Failed to get status for job {}", jobId, e);
            throw new Exception("Failed to get job status: " + jobId, e);
        }
    }
    
    @Override
    public void cancelJob(String jobId) throws Exception {
        logger.info("Canceling job: {}", jobId);
        stopJob(jobId);
    }
    
    /**
     * 注册活跃作业
     * 
     * @param jobId 作业ID
     * @param jobClient 作业客户端
     */
    public void registerJob(String jobId, JobClient jobClient) {
        activeJobs.put(jobId, jobClient);
        logger.info("Job {} registered in JobManager", jobId);
    }
    
    /**
     * 通过REST API取消作业
     * 
     * @param jobId 作业ID
     * @throws Exception 如果取消失败
     */
    private void cancelJobViaRestApi(String jobId) throws Exception {
        Configuration config = new Configuration();
        config.setString(RestOptions.ADDRESS, jobManagerHost);
        config.setInteger(RestOptions.PORT, jobManagerPort);
        
        try (RestClusterClient<String> client = new RestClusterClient<>(config, "standalone")) {
            JobID flinkJobId = JobID.fromHexString(jobId);
            client.cancel(flinkJobId).get(30, TimeUnit.SECONDS);
            logger.info("Job {} canceled via REST API", jobId);
        } catch (Exception e) {
            logger.error("Failed to cancel job {} via REST API", jobId, e);
            throw e;
        }
    }
    
    /**
     * 通过REST API查询作业状态
     * 
     * @param jobId 作业ID
     * @return 作业状态
     * @throws Exception 如果查询失败
     */
    private JobStatus getJobStatusViaRestApi(String jobId) throws Exception {
        Configuration config = new Configuration();
        config.setString(RestOptions.ADDRESS, jobManagerHost);
        config.setInteger(RestOptions.PORT, jobManagerPort);
        
        try (RestClusterClient<String> client = new RestClusterClient<>(config, "standalone")) {
            JobID flinkJobId = JobID.fromHexString(jobId);
            
            // 获取作业状态
            org.apache.flink.api.common.JobStatus flinkStatus = client
                .getJobStatus(flinkJobId)
                .get(10, TimeUnit.SECONDS);
            
            // 转换为我们的JobStatus模型
            return JobStatus.builder()
                .jobId(jobId)
                .jobName("Realtime Data Pipeline")
                .state(flinkStatus.name())
                .startTime(System.currentTimeMillis())
                .duration(0L)
                .parallelism(0)
                .metrics(new HashMap<>())
                .build();
                
        } catch (Exception e) {
            logger.error("Failed to get job status via REST API for job {}", jobId, e);
            throw e;
        }
    }
    
    /**
     * 获取所有活跃作业的ID
     * 
     * @return 作业ID集合
     */
    public java.util.Set<String> getActiveJobIds() {
        return activeJobs.keySet();
    }
    
    /**
     * 获取作业的Savepoint路径
     * 
     * @param jobId 作业ID
     * @return Savepoint路径，如果不存在返回null
     */
    public String getSavepointPath(String jobId) {
        return jobSavepointPaths.get(jobId);
    }
}
