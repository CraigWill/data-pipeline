package com.realtime.pipeline.flink.processor;

import com.realtime.pipeline.consistency.ConsistencyValidator;
import com.realtime.pipeline.consistency.ValidationResult;
import com.realtime.pipeline.model.ProcessedEvent;
import org.apache.flink.api.common.functions.MapFunction;

/**
 * ProcessedEvent验证器
 * 在数据流中验证ProcessedEvent的一致性
 * 
 * 验证需求: 9.5
 */
public class ProcessedEventValidator implements MapFunction<ProcessedEvent, ProcessedEvent> {
    private static final long serialVersionUID = 1L;

    private final ConsistencyValidator validator;

    public ProcessedEventValidator() {
        this.validator = new ConsistencyValidator();
    }

    @Override
    public ProcessedEvent map(ProcessedEvent event) throws Exception {
        ValidationResult result = validator.validateProcessedEvent(event);

        if (!result.isValid()) {
            // 日志已在validator中记录
            // 继续传递事件，但已记录不一致性
        }

        return event;
    }
}
