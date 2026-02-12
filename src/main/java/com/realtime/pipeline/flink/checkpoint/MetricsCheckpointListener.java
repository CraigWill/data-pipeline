package com.realtime.pipeline.flink.checkpoint;

import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.runtime.state.FunctionInitializationContext;
import org.apache.flink.runtime.state.FunctionSnapshotContext;
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 带指标收集的Checkpoint监听器
 * 实现为一个MapFunction，可以插入到数据流中监听Checkpoint事件
 * 
 * 验证需求:
 * - 需求 2.5: WHEN Checkpoint失败 THEN THE Flink SHALL 记录错误日志并继续处理
 * - 需求 4.6: THE System SHALL 记录所有故障事件到日志系统
 * 
 * @param <T> 数据类型
 */
public class MetricsCheckpointListener<T> extends RichMapFunction<T, T> implements CheckpointedFunction {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(MetricsCheckpointListener.class);

    private final CheckpointListener checkpointListener;
    private transient long lastCheckpointId = -1;

    /**
     * 构造函数
     * @param checkpointListener Checkpoint监听器
     */
    public MetricsCheckpointListener(CheckpointListener checkpointListener) {
        if (checkpointListener == null) {
            throw new IllegalArgumentException("CheckpointListener cannot be null");
        }
        this.checkpointListener = checkpointListener;
    }

    /**
     * 默认构造函数
     */
    public MetricsCheckpointListener() {
        this.checkpointListener = new CheckpointListener();
    }

    @Override
    public T map(T value) throws Exception {
        // 直接返回输入值，不做任何转换
        // 这个函数只是用来监听Checkpoint事件
        return value;
    }

    @Override
    public void snapshotState(FunctionSnapshotContext context) throws Exception {
        // Checkpoint开始时调用
        long checkpointId = context.getCheckpointId();
        lastCheckpointId = checkpointId;
        
        try {
            checkpointListener.notifyCheckpointStart(checkpointId);
            logger.debug("Checkpoint {} snapshot started", checkpointId);
        } catch (Exception e) {
            logger.error("Error in checkpoint start notification", e);
            // 不抛出异常，让Checkpoint继续
        }
    }

    @Override
    public void initializeState(FunctionInitializationContext context) throws Exception {
        // 初始化状态
        if (context.isRestored()) {
            logger.info("State restored from checkpoint");
        } else {
            logger.info("Initializing state for the first time");
        }
    }

    /**
     * 通知Checkpoint完成
     * 注意：这个方法需要在外部调用，因为CheckpointedFunction接口没有提供完成回调
     * 
     * @param checkpointId Checkpoint ID
     */
    public void notifyCheckpointComplete(long checkpointId) {
        // Checkpoint成功完成时调用
        try {
            checkpointListener.notifyCheckpointComplete(checkpointId);
            
            // 定期输出指标摘要
            if (checkpointId % 10 == 0) {
                logger.info("Checkpoint metrics summary: {}", 
                    checkpointListener.getMetricsSummary());
            }
        } catch (Exception e) {
            logger.error("Error in checkpoint complete notification", e);
            // 不抛出异常，让系统继续运行
        }
    }

    /**
     * 通知Checkpoint中止
     * 注意：这个方法需要在外部调用，因为CheckpointedFunction接口没有提供中止回调
     * 
     * @param checkpointId Checkpoint ID
     */
    public void notifyCheckpointAborted(long checkpointId) {
        // Checkpoint中止时调用
        try {
            checkpointListener.notifyCheckpointAborted(checkpointId, "Checkpoint aborted by Flink");
            
            // 检查失败率
            double failureRate = checkpointListener.getFailureRate();
            if (failureRate > 10.0) {
                logger.error("ALERT: Checkpoint failure rate ({:.2f}%) exceeds 10% threshold!", 
                    failureRate);
            }
        } catch (Exception e) {
            logger.error("Error in checkpoint abort notification", e);
            // 不抛出异常，让系统继续运行
        }
    }

    /**
     * 获取Checkpoint监听器
     * @return CheckpointListener
     */
    public CheckpointListener getCheckpointListener() {
        return checkpointListener;
    }
}
