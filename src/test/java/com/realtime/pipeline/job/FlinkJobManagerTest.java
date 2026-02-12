package com.realtime.pipeline.job;

import com.realtime.pipeline.config.FlinkConfig;
import com.realtime.pipeline.config.PipelineConfig;
import com.realtime.pipeline.model.JobStatus;
import org.apache.flink.core.execution.JobClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * FlinkJobManager单元测试
 * 
 * 测试作业管理功能:
 * - 作业停止
 * - Savepoint触发
 * - 作业状态查询
 * - 作业取消
 * 
 * 验证需求: 4.1
 */
class FlinkJobManagerTest {
    
    private FlinkJobManager jobManager;
    
    @Mock
    private JobClient mockJobClient;
    
    @Mock
    private PipelineConfig mockConfig;
    
    @Mock
    private FlinkConfig mockFlinkConfig;
    
    private static final String TEST_JOB_ID = "test-job-123";
    private static final String TEST_SAVEPOINT_PATH = "/tmp/savepoints";
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        jobManager = new FlinkJobManager("localhost", 8081);
        
        // 设置mock配置
        when(mockConfig.getFlink()).thenReturn(mockFlinkConfig);
        when(mockFlinkConfig.getCheckpointDir()).thenReturn("/tmp/checkpoints");
        when(mockFlinkConfig.getJobManagerHost()).thenReturn("localhost");
        when(mockFlinkConfig.getJobManagerPort()).thenReturn(8081);
    }
    
    @Test
    void testConstructor() {
        FlinkJobManager manager = new FlinkJobManager("test-host", 9090);
        assertNotNull(manager);
        assertEquals(0, manager.getActiveJobIds().size());
    }
    
    @Test
    void testFromConfig() {
        FlinkJobManager manager = FlinkJobManager.fromConfig(mockConfig);
        assertNotNull(manager);
    }
    
    @Test
    void testRegisterJob() {
        jobManager.registerJob(TEST_JOB_ID, mockJobClient);
        assertTrue(jobManager.getActiveJobIds().contains(TEST_JOB_ID));
    }
    
    @Test
    void testStopJob() throws Exception {
        // 注册作业
        jobManager.registerJob(TEST_JOB_ID, mockJobClient);
        
        // Mock cancel方法
        CompletableFuture<Void> cancelFuture = CompletableFuture.completedFuture(null);
        when(mockJobClient.cancel()).thenReturn(cancelFuture);
        
        // 停止作业
        jobManager.stopJob(TEST_JOB_ID);
        
        // 验证cancel被调用
        verify(mockJobClient, times(1)).cancel();
        
        // 验证作业已从活跃列表中移除
        assertFalse(jobManager.getActiveJobIds().contains(TEST_JOB_ID));
    }
    
    @Test
    void testStopJobNotFound() {
        // 尝试停止不存在的作业应该抛出异常或通过REST API处理
        assertThrows(Exception.class, () -> {
            jobManager.stopJob("non-existent-job");
        });
    }
    
    @Test
    void testStopJobWithSavepoint() throws Exception {
        // 注册作业
        jobManager.registerJob(TEST_JOB_ID, mockJobClient);
        
        // Mock stopWithSavepoint方法
        String expectedSavepointPath = TEST_SAVEPOINT_PATH + "/savepoint-123";
        CompletableFuture<String> savepointFuture = 
            CompletableFuture.completedFuture(expectedSavepointPath);
        when(mockJobClient.stopWithSavepoint(anyBoolean(), anyString()))
            .thenReturn(savepointFuture);
        
        // 停止作业并触发Savepoint
        String actualPath = jobManager.stopJobWithSavepoint(TEST_JOB_ID, TEST_SAVEPOINT_PATH);
        
        // 验证
        assertEquals(expectedSavepointPath, actualPath);
        verify(mockJobClient, times(1)).stopWithSavepoint(false, TEST_SAVEPOINT_PATH);
        
        // 验证Savepoint路径被记录
        assertEquals(expectedSavepointPath, jobManager.getSavepointPath(TEST_JOB_ID));
        
        // 验证作业已从活跃列表中移除
        assertFalse(jobManager.getActiveJobIds().contains(TEST_JOB_ID));
    }
    
    @Test
    void testStopJobWithSavepointNotFound() {
        // 尝试停止不存在的作业应该抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            jobManager.stopJobWithSavepoint("non-existent-job", TEST_SAVEPOINT_PATH);
        });
    }
    
    @Test
    void testTriggerSavepoint() throws Exception {
        // 注册作业
        jobManager.registerJob(TEST_JOB_ID, mockJobClient);
        
        // Mock triggerSavepoint方法
        String expectedSavepointPath = TEST_SAVEPOINT_PATH + "/savepoint-456";
        CompletableFuture<String> savepointFuture = 
            CompletableFuture.completedFuture(expectedSavepointPath);
        when(mockJobClient.triggerSavepoint(anyString())).thenReturn(savepointFuture);
        
        // 触发Savepoint
        String actualPath = jobManager.triggerSavepoint(TEST_JOB_ID, TEST_SAVEPOINT_PATH);
        
        // 验证
        assertEquals(expectedSavepointPath, actualPath);
        verify(mockJobClient, times(1)).triggerSavepoint(TEST_SAVEPOINT_PATH);
        
        // 验证Savepoint路径被记录
        assertEquals(expectedSavepointPath, jobManager.getSavepointPath(TEST_JOB_ID));
        
        // 验证作业仍在活跃列表中（triggerSavepoint不停止作业）
        assertTrue(jobManager.getActiveJobIds().contains(TEST_JOB_ID));
    }
    
    @Test
    void testTriggerSavepointNotFound() {
        // 尝试触发不存在作业的Savepoint应该抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            jobManager.triggerSavepoint("non-existent-job", TEST_SAVEPOINT_PATH);
        });
    }
    
    @Test
    void testGetJobStatus() throws Exception {
        // 注册作业
        jobManager.registerJob(TEST_JOB_ID, mockJobClient);
        
        // Mock getJobStatus方法
        CompletableFuture<org.apache.flink.api.common.JobStatus> statusFuture = 
            CompletableFuture.completedFuture(org.apache.flink.api.common.JobStatus.RUNNING);
        when(mockJobClient.getJobStatus()).thenReturn(statusFuture);
        
        // 查询作业状态
        JobStatus status = jobManager.getJobStatus(TEST_JOB_ID);
        
        // 验证
        assertNotNull(status);
        assertEquals(TEST_JOB_ID, status.getJobId());
        assertEquals("RUNNING", status.getState());
        assertTrue(status.isRunning());
        
        verify(mockJobClient, times(1)).getJobStatus();
    }
    
    @Test
    void testGetJobStatusNotFound() {
        // 尝试查询不存在作业的状态应该抛出异常
        assertThrows(Exception.class, () -> {
            jobManager.getJobStatus("non-existent-job");
        });
    }
    
    @Test
    void testCancelJob() throws Exception {
        // 注册作业
        jobManager.registerJob(TEST_JOB_ID, mockJobClient);
        
        // Mock cancel方法
        CompletableFuture<Void> cancelFuture = CompletableFuture.completedFuture(null);
        when(mockJobClient.cancel()).thenReturn(cancelFuture);
        
        // 取消作业
        jobManager.cancelJob(TEST_JOB_ID);
        
        // 验证cancel被调用
        verify(mockJobClient, times(1)).cancel();
        
        // 验证作业已从活跃列表中移除
        assertFalse(jobManager.getActiveJobIds().contains(TEST_JOB_ID));
    }
    
    @Test
    void testStartJobThrowsUnsupportedOperation() {
        // startJob方法应该抛出UnsupportedOperationException
        assertThrows(UnsupportedOperationException.class, () -> {
            jobManager.startJob(mockConfig);
        });
    }
    
    @Test
    void testRestartJobThrowsUnsupportedOperation() {
        // restartJob方法应该抛出UnsupportedOperationException
        assertThrows(UnsupportedOperationException.class, () -> {
            jobManager.restartJob(TEST_JOB_ID, mockConfig);
        });
    }
    
    @Test
    void testGetSavepointPath() {
        // 初始状态应该返回null
        assertNull(jobManager.getSavepointPath(TEST_JOB_ID));
        
        // 注册作业并触发Savepoint后应该返回路径
        jobManager.registerJob(TEST_JOB_ID, mockJobClient);
        
        String expectedPath = TEST_SAVEPOINT_PATH + "/savepoint-789";
        CompletableFuture<String> savepointFuture = 
            CompletableFuture.completedFuture(expectedPath);
        when(mockJobClient.triggerSavepoint(anyString())).thenReturn(savepointFuture);
        
        try {
            jobManager.triggerSavepoint(TEST_JOB_ID, TEST_SAVEPOINT_PATH);
            assertEquals(expectedPath, jobManager.getSavepointPath(TEST_JOB_ID));
        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        }
    }
    
    @Test
    void testMultipleJobsManagement() throws Exception {
        // 注册多个作业
        String jobId1 = "job-1";
        String jobId2 = "job-2";
        
        JobClient mockClient1 = mock(JobClient.class);
        JobClient mockClient2 = mock(JobClient.class);
        
        jobManager.registerJob(jobId1, mockClient1);
        jobManager.registerJob(jobId2, mockClient2);
        
        // 验证两个作业都在活跃列表中
        assertEquals(2, jobManager.getActiveJobIds().size());
        assertTrue(jobManager.getActiveJobIds().contains(jobId1));
        assertTrue(jobManager.getActiveJobIds().contains(jobId2));
        
        // 停止一个作业
        CompletableFuture<Void> cancelFuture = CompletableFuture.completedFuture(null);
        when(mockClient1.cancel()).thenReturn(cancelFuture);
        
        jobManager.stopJob(jobId1);
        
        // 验证只有一个作业被移除
        assertEquals(1, jobManager.getActiveJobIds().size());
        assertFalse(jobManager.getActiveJobIds().contains(jobId1));
        assertTrue(jobManager.getActiveJobIds().contains(jobId2));
    }
    
    @Test
    void testStopJobFailure() {
        // 注册作业
        jobManager.registerJob(TEST_JOB_ID, mockJobClient);
        
        // Mock cancel方法抛出异常
        CompletableFuture<Void> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Cancel failed"));
        when(mockJobClient.cancel()).thenReturn(failedFuture);
        
        // 停止作业应该抛出异常
        assertThrows(Exception.class, () -> {
            jobManager.stopJob(TEST_JOB_ID);
        });
    }
    
    @Test
    void testTriggerSavepointFailure() {
        // 注册作业
        jobManager.registerJob(TEST_JOB_ID, mockJobClient);
        
        // Mock triggerSavepoint方法抛出异常
        CompletableFuture<String> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Savepoint failed"));
        when(mockJobClient.triggerSavepoint(anyString())).thenReturn(failedFuture);
        
        // 触发Savepoint应该抛出异常
        assertThrows(Exception.class, () -> {
            jobManager.triggerSavepoint(TEST_JOB_ID, TEST_SAVEPOINT_PATH);
        });
    }
}
