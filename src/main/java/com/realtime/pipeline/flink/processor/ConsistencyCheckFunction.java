package com.realtime.pipeline.flink.processor;

import com.realtime.pipeline.consistency.ConsistencyValidator;
import com.realtime.pipeline.consistency.ValidationResult;
import com.realtime.pipeline.model.ChangeEvent;
import org.apache.flink.api.common.functions.FilterFunction;

/**
 * Flink一致性检查函数
 * 在数据流中验证ChangeEvent的一致性
 * 
 * 验证需求: 9.5
 */
public class ConsistencyCheckFunction implements FilterFunction<ChangeEvent> {
    private static final long serialVersionUID = 1L;

    private final ConsistencyValidator validator;
    private final boolean filterInvalid;

    /**
     * 构造函数
     * 
     * @param filterInvalid 是否过滤掉无效的事件（true=过滤，false=保留但记录日志）
     */
    public ConsistencyCheckFunction(boolean filterInvalid) {
        this.validator = new ConsistencyValidator();
        this.filterInvalid = filterInvalid;
    }

    /**
     * 默认构造函数，不过滤无效事件
     */
    public ConsistencyCheckFunction() {
        this(false);
    }

    @Override
    public boolean filter(ChangeEvent event) throws Exception {
        ValidationResult result = validator.validateChangeEvent(event);

        if (!result.isValid()) {
            // 日志已在validator中记录
            // 根据配置决定是否过滤掉无效事件
            return !filterInvalid;
        }

        return true;
    }
}
