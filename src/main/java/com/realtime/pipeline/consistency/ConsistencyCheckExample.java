package com.realtime.pipeline.consistency;

import com.realtime.pipeline.flink.processor.ConsistencyCheckFunction;
import com.realtime.pipeline.flink.processor.ProcessedEventValidator;
import com.realtime.pipeline.model.ChangeEvent;
import com.realtime.pipeline.model.ProcessedEvent;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/**
 * 数据一致性检测示例
 * 展示如何在Flink流处理中集成一致性检测
 * 
 * 验证需求: 9.5
 */
public class ConsistencyCheckExample {

    /**
     * 在Flink流处理中添加一致性检测
     */
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 假设我们有一个ChangeEvent数据流
        DataStream<ChangeEvent> changeEventStream = createChangeEventStream(env);

        // 方式1: 使用FilterFunction进行一致性检查（不过滤无效事件，仅记录日志）
        DataStream<ChangeEvent> validatedChangeEvents = changeEventStream
                .filter(new ConsistencyCheckFunction(false))
                .name("Consistency Check - ChangeEvent");

        // 方式2: 使用FilterFunction进行一致性检查（过滤掉无效事件）
        // DataStream<ChangeEvent> filteredChangeEvents = changeEventStream
        //         .filter(new ConsistencyCheckFunction(true))
        //         .name("Consistency Check - Filter Invalid");

        // 处理事件
        DataStream<ProcessedEvent> processedEventStream = validatedChangeEvents
                .map(event -> {
                    // 转换为ProcessedEvent
                    return ProcessedEvent.builder()
                            .eventType(event.getEventType())
                            .database(event.getDatabase())
                            .table(event.getTable())
                            .timestamp(event.getTimestamp())
                            .processTime(System.currentTimeMillis())
                            .data(event.getData())
                            .partition(event.getTable())
                            .eventId(event.getEventId())
                            .build();
                })
                .name("Event Processor");

        // 验证ProcessedEvent
        DataStream<ProcessedEvent> validatedProcessedEvents = processedEventStream
                .map(new ProcessedEventValidator())
                .name("Consistency Check - ProcessedEvent");

        // 输出到sink
        validatedProcessedEvents.print();

        env.execute("Consistency Check Example");
    }

    /**
     * 创建示例ChangeEvent数据流
     */
    private static DataStream<ChangeEvent> createChangeEventStream(StreamExecutionEnvironment env) {
        // 这里应该是实际的数据源，例如从DataHub读取
        // 为了示例，我们创建一个简单的数据流
        return env.fromElements(
                ChangeEvent.builder()
                        .eventType("INSERT")
                        .database("testdb")
                        .table("users")
                        .timestamp(System.currentTimeMillis())
                        .after(java.util.Map.of("id", 1, "name", "Alice"))
                        .primaryKeys(java.util.List.of("id"))
                        .eventId("evt-001")
                        .build()
        );
    }

    /**
     * 使用ConsistencyValidator进行独立验证
     */
    public static void standaloneValidationExample() {
        ConsistencyValidator validator = new ConsistencyValidator();

        // 创建测试事件
        ChangeEvent changeEvent = ChangeEvent.builder()
                .eventType("INSERT")
                .database("testdb")
                .table("users")
                .timestamp(System.currentTimeMillis())
                .after(java.util.Map.of("id", 1, "name", "Alice"))
                .primaryKeys(java.util.List.of("id"))
                .eventId("evt-001")
                .build();

        // 验证ChangeEvent
        ValidationResult result = validator.validateChangeEvent(changeEvent);
        if (result.isValid()) {
            System.out.println("ChangeEvent is valid");
        } else {
            System.out.println("ChangeEvent is invalid: " + result.getErrorMessage());
        }

        // 创建ProcessedEvent
        ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventType(changeEvent.getEventType())
                .database(changeEvent.getDatabase())
                .table(changeEvent.getTable())
                .timestamp(changeEvent.getTimestamp())
                .processTime(System.currentTimeMillis())
                .data(changeEvent.getData())
                .partition(changeEvent.getTable())
                .eventId(changeEvent.getEventId())
                .build();

        // 验证ProcessedEvent
        ValidationResult processedResult = validator.validateProcessedEvent(processedEvent);
        if (processedResult.isValid()) {
            System.out.println("ProcessedEvent is valid");
        } else {
            System.out.println("ProcessedEvent is invalid: " + processedResult.getErrorMessage());
        }

        // 验证两个事件之间的一致性
        ValidationResult consistencyResult = validator.validateEventConsistency(changeEvent, processedEvent);
        if (consistencyResult.isValid()) {
            System.out.println("Events are consistent");
        } else {
            System.out.println("Events are inconsistent: " + consistencyResult.getErrorMessage());
        }
    }
}
