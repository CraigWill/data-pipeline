package com.realtime.pipeline.flink.checkpoint;

import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.runtime.state.FunctionInitializationContext;
import org.apache.flink.runtime.state.FunctionSnapshotContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checkpoint监听器操作符
 * 实现Flink的CheckpointedFunction接口，用于监听Checkpoint事件
 * 
 * 这是一个轻量级的Sink操作符，不实际输出数据，只用于监听Checkpoint事件
 * 
 * 验证需求:
 * - 需求 2.5: WHEN Checkpoint失败 THEN THE Flink SHALL 记录错误日志并继续处理
 * - 需求 4.6: THE System SHALL 记录所有故障事件到日志系统
 */
public class CheckpointListenerOperator<T> implements SinkFunction<T>, CheckpointedFunction {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(CheckpointListenerOperator.class);

    private final CheckpointListener checkpointListener;
    private transient long currentCheckpointId = -1;

    /**
     * 构造函数
     * @param checkpointListener Checkpoint监听器
     */
    public CheckpointListenerOperator(CheckpointListener checkpointListener) {
        if (checkpointListener == null) {
            throw new IllegalArgumentException("CheckpointListener cannot be null");
        }
        this.checkpointListener = checkpointListener;
    }

    /**
     * 默认构造函数，创建新的CheckpointListener
     */
    public CheckpointListenerOperator() {
        this.checkpointListener = new CheckpointListener();
    }

    @Override
    public void invoke(T value, Context context) throws Exception {
        // 不做任何处理，只是一个监听器
        // 数据会继续流向下游
    }

    @Override
    public void snapshotState(FunctionSnapshotContext context) throws Exception {
        // Checkpoint开始时调用
        currentCheckpointId = context.getCheckpointId();
        checkpointListener.notifyCheckpointStart(currentCheckpointId);
        
        logger.debug("Snapshot state for checkpoint {}", currentCheckpointId);
    }

    @Override
    public void initializeState(FunctionInitializationContext context) throws Exception {
        // 初始化状态，从Checkpoint恢复时调用
        if (context.isRestored()) {
            logger.info("Restoring state from checkpoint");
        } else {
            logger.info("Initializing state for the first time");
        }
    }

    /**
     * 通知Checkpoint完成
     * 注意：这个方法需要在外部调用，因为Flink的CheckpointedFunction接口
     * 没有提供Checkpoint完成的回调
     * 
     * @param checkpointId Checkpoint ID
     */
    public void notifyCheckpointComplete(long checkpointId) {
        checkpointListener.notifyCheckpointComplete(checkpointId);
    }

    /**
     * 通知Checkpoint失败
     * @param checkpointId Checkpoint ID
     * @param cause 失败原因
     */
    public void notifyCheckpointFailed(long checkpointId, Throwable cause) {
        checkpointListener.notifyCheckpointFailed(checkpointId, cause);
    }

    /**
     * 通知Checkpoint中止
     * @param checkpointId Checkpoint ID
     * @param reason 中止原因
     */
    public void notifyCheckpointAborted(long checkpointId, String reason) {
        checkpointListener.notifyCheckpointAborted(checkpointId, reason);
    }

    /**
     * 获取Checkpoint监听器
     * @return CheckpointListener
     */
    public CheckpointListener getCheckpointListener() {
        return checkpointListener;
    }
}
