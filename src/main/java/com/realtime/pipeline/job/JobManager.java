package com.realtime.pipeline.job;

import com.realtime.pipeline.config.PipelineConfig;
import com.realtime.pipeline.model.JobStatus;

/**
 * 作业管理接口
 * 
 * 职责:
 * 1. 作业启动、停止、重启
 * 2. Savepoint触发
 * 3. 作业状态查询
 * 
 * 验证需求: 4.1
 */
public interface JobManager {
    
    /**
     * 启动Flink作业
     * 
     * @param config 管道配置
     * @return 作业ID
     * @throws Exception 如果启动失败
     */
    String startJob(PipelineConfig config) throws Exception;
    
    /**
     * 停止Flink作业
     * 
     * @param jobId 作业ID
     * @throws Exception 如果停止失败
     */
    void stopJob(String jobId) throws Exception;
    
    /**
     * 停止Flink作业并触发Savepoint
     * 
     * @param jobId 作业ID
     * @param savepointPath Savepoint保存路径
     * @return Savepoint路径
     * @throws Exception 如果停止失败
     */
    String stopJobWithSavepoint(String jobId, String savepointPath) throws Exception;
    
    /**
     * 重启Flink作业
     * 从最近的Checkpoint或Savepoint恢复
     * 
     * @param jobId 作业ID
     * @param config 管道配置
     * @return 新的作业ID
     * @throws Exception 如果重启失败
     */
    String restartJob(String jobId, PipelineConfig config) throws Exception;
    
    /**
     * 触发Savepoint
     * 
     * @param jobId 作业ID
     * @param savepointPath Savepoint保存路径
     * @return Savepoint路径
     * @throws Exception 如果触发失败
     */
    String triggerSavepoint(String jobId, String savepointPath) throws Exception;
    
    /**
     * 查询作业状态
     * 
     * @param jobId 作业ID
     * @return 作业状态
     * @throws Exception 如果查询失败
     */
    JobStatus getJobStatus(String jobId) throws Exception;
    
    /**
     * 取消作业
     * 
     * @param jobId 作业ID
     * @throws Exception 如果取消失败
     */
    void cancelJob(String jobId) throws Exception;
}
