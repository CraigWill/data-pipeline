package com.realtime.pipeline.flink.sink;

import com.realtime.pipeline.model.ProcessedEvent;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.state.FunctionInitializationContext;
import org.apache.flink.runtime.state.FunctionSnapshotContext;
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * 幂等性文件Sink
 * 通过跟踪已处理的事件ID来实现精确一次语义
 * 
 * 实现需求:
 * - 9.2: 支持精确一次（Exactly-once）语义
 * - 9.3: 通过幂等性操作去重
 * 
 * 工作原理:
 * 1. 维护已处理事件ID的状态
 * 2. 在写入前检查事件ID是否已处理
 * 3. 只写入未处理过的事件
 * 4. 状态通过Checkpoint持久化，保证故障恢复后的一致性
 * 
 * 注意:
 * - 需要配置Flink的Exactly-once Checkpoint模式
 * - 状态大小会随着处理的事件数增长，需要定期清理旧状态
 * - 适用于需要严格去重的场景
 */
public class IdempotentFileSink extends RichSinkFunction<ProcessedEvent> implements CheckpointedFunction {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(IdempotentFileSink.class);

    private final SinkFunction<ProcessedEvent> delegateSink;
    private final long stateRetentionTimeMs;
    
    // 运行时状态：已处理的事件ID集合
    private transient Set<String> processedEventIds;
    
    // 检查点状态：持久化的事件ID集合
    private transient ListState<String> checkpointedState;
    
    // 用于状态清理的时间戳映射
    private transient Set<EventIdWithTimestamp> eventIdsWithTimestamp;
    
    // 统计指标
    private transient long totalEvents;
    private transient long duplicateEvents;
    private transient long uniqueEvents;

    /**
     * 构造函数
     * 
     * @param delegateSink 被包装的Sink实现
     * @param stateRetentionTimeMs 状态保留时间（毫秒），超过此时间的事件ID会被清理
     */
    public IdempotentFileSink(SinkFunction<ProcessedEvent> delegateSink, long stateRetentionTimeMs) {
        this.delegateSink = delegateSink;
        this.stateRetentionTimeMs = stateRetentionTimeMs;
    }

    /**
     * 构造函数（使用默认状态保留时间：24小时）
     * 
     * @param delegateSink 被包装的Sink实现
     */
    public IdempotentFileSink(SinkFunction<ProcessedEvent> delegateSink) {
        this(delegateSink, 24 * 60 * 60 * 1000L); // 默认24小时
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        
        // 初始化内存状态
        this.processedEventIds = new HashSet<>();
        this.eventIdsWithTimestamp = new HashSet<>();
        this.totalEvents = 0;
        this.duplicateEvents = 0;
        this.uniqueEvents = 0;
        
        // 如果delegate sink是RichSinkFunction，也需要初始化它
        if (delegateSink instanceof RichSinkFunction) {
            RichSinkFunction<ProcessedEvent> richSink = (RichSinkFunction<ProcessedEvent>) delegateSink;
            richSink.setRuntimeContext(getRuntimeContext());
            richSink.open(parameters);
        }
        
        logger.info("IdempotentFileSink initialized with state retention time: {} ms", stateRetentionTimeMs);
    }

    @Override
    public void close() throws Exception {
        super.close();
        
        // 关闭delegate sink
        if (delegateSink instanceof RichSinkFunction) {
            ((RichSinkFunction<ProcessedEvent>) delegateSink).close();
        }
        
        // 记录统计信息
        logger.info("IdempotentFileSink closing. Stats - Total: {}, Unique: {}, Duplicates: {}, Dedup Rate: {:.2f}%",
                totalEvents, uniqueEvents, duplicateEvents, 
                totalEvents > 0 ? (duplicateEvents * 100.0 / totalEvents) : 0.0);
    }

    @Override
    public void invoke(ProcessedEvent value, Context context) throws Exception {
        if (value == null) {
            logger.warn("Received null ProcessedEvent, skipping");
            return;
        }

        totalEvents++;
        
        String eventId = value.getEventId();
        if (eventId == null || eventId.isEmpty()) {
            logger.warn("ProcessedEvent has null or empty eventId, cannot deduplicate. Writing anyway.");
            delegateSink.invoke(value, context);
            uniqueEvents++;
            return;
        }

        // 需求 9.3: 通过幂等性操作去重
        // 检查事件ID是否已经处理过
        if (processedEventIds.contains(eventId)) {
            // 重复事件，跳过写入
            duplicateEvents++;
            
            if (duplicateEvents % 100 == 0) {
                logger.info("Detected duplicate event: {} (total duplicates: {})", eventId, duplicateEvents);
            } else {
                logger.debug("Skipping duplicate event: {}", eventId);
            }
            return;
        }

        // 需求 9.2: 支持精确一次语义
        // 这是一个新事件，写入并记录
        try {
            delegateSink.invoke(value, context);
            
            // 写入成功后，记录事件ID
            processedEventIds.add(eventId);
            eventIdsWithTimestamp.add(new EventIdWithTimestamp(eventId, System.currentTimeMillis()));
            uniqueEvents++;
            
            // 定期清理过期的事件ID状态
            if (uniqueEvents % 1000 == 0) {
                cleanupExpiredState();
            }
            
            if (uniqueEvents % 10000 == 0) {
                logger.info("IdempotentFileSink stats - Total: {}, Unique: {}, Duplicates: {}, State size: {}",
                        totalEvents, uniqueEvents, duplicateEvents, processedEventIds.size());
            }
            
        } catch (Exception e) {
            // 写入失败，不记录事件ID，以便下次重试
            logger.error("Failed to write event {}: {}", eventId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 清理过期的事件ID状态
     * 移除超过保留时间的事件ID，防止状态无限增长
     */
    private void cleanupExpiredState() {
        long currentTime = System.currentTimeMillis();
        long expirationTime = currentTime - stateRetentionTimeMs;
        
        int beforeSize = processedEventIds.size();
        
        // 移除过期的事件ID
        eventIdsWithTimestamp.removeIf(entry -> {
            if (entry.timestamp < expirationTime) {
                processedEventIds.remove(entry.eventId);
                return true;
            }
            return false;
        });
        
        int afterSize = processedEventIds.size();
        int removed = beforeSize - afterSize;
        
        if (removed > 0) {
            logger.info("Cleaned up {} expired event IDs from state (retention: {} ms)", 
                    removed, stateRetentionTimeMs);
        }
    }

    @Override
    public void snapshotState(FunctionSnapshotContext context) throws Exception {
        // 清空检查点状态
        checkpointedState.clear();
        
        // 将当前的事件ID集合保存到检查点状态
        for (String eventId : processedEventIds) {
            checkpointedState.add(eventId);
        }
        
        logger.debug("Snapshot state: checkpoint {}, {} event IDs saved", 
                context.getCheckpointId(), processedEventIds.size());
    }

    @Override
    public void initializeState(FunctionInitializationContext context) throws Exception {
        // 创建或恢复检查点状态
        ListStateDescriptor<String> descriptor = new ListStateDescriptor<>(
                "processed-event-ids",
                TypeInformation.of(new TypeHint<String>() {})
        );
        
        checkpointedState = context.getOperatorStateStore().getListState(descriptor);
        
        // 如果是从检查点恢复，加载已处理的事件ID
        if (context.isRestored()) {
            processedEventIds = new HashSet<>();
            eventIdsWithTimestamp = new HashSet<>();
            
            long currentTime = System.currentTimeMillis();
            for (String eventId : checkpointedState.get()) {
                processedEventIds.add(eventId);
                // 恢复时使用当前时间作为时间戳，避免立即清理
                eventIdsWithTimestamp.add(new EventIdWithTimestamp(eventId, currentTime));
            }
            
            logger.info("Restored {} event IDs from checkpoint", processedEventIds.size());
        }
    }

    /**
     * 获取统计信息（用于测试和监控）
     */
    public long getTotalEvents() {
        return totalEvents;
    }

    public long getDuplicateEvents() {
        return duplicateEvents;
    }

    public long getUniqueEvents() {
        return uniqueEvents;
    }

    public int getStateSize() {
        return processedEventIds != null ? processedEventIds.size() : 0;
    }

    /**
     * 事件ID和时间戳的组合，用于状态清理
     */
    private static class EventIdWithTimestamp {
        final String eventId;
        final long timestamp;

        EventIdWithTimestamp(String eventId, long timestamp) {
            this.eventId = eventId;
            this.timestamp = timestamp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EventIdWithTimestamp that = (EventIdWithTimestamp) o;
            return eventId.equals(that.eventId);
        }

        @Override
        public int hashCode() {
            return eventId.hashCode();
        }
    }
}
