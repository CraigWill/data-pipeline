# Task 11.1: At-Least-Once Semantics Implementation

## Overview

This document describes the implementation of at-least-once delivery semantics for the realtime data pipeline system, fulfilling **Requirement 9.1**.

## Requirement

**需求 9.1**: THE System SHALL 保证至少一次（At-least-once）的数据传输语义

Translation: The system SHALL guarantee at-least-once data transmission semantics.

## Implementation

### 1. Configuration Changes

#### FlinkConfig.java
Added a new configuration parameter `checkpointingMode` to control the checkpointing mode:

```java
/**
 * Checkpoint模式: at-least-once, exactly-once
 * 默认为at-least-once以满足需求9.1
 * 当配置幂等性Sink时可以使用exactly-once（需求9.2）
 */
@JsonProperty("checkpointingMode")
@Builder.Default
private String checkpointingMode = "at-least-once";
```

**Default Value**: `"at-least-once"` - This ensures the system meets requirement 9.1 by default.

**Validation**: The configuration validates that the mode is either `"at-least-once"` or `"exactly-once"`.

#### FlinkEnvironmentConfigurator.java
Updated the checkpoint configuration logic to use the configured mode:

```java
// 设置Checkpoint模式（at-least-once或exactly-once）
// 需求 9.1: 默认使用at-least-once保证数据不丢失
// 需求 9.2: 配置幂等性Sink时可以使用exactly-once
String mode = flinkConfig.getCheckpointingMode().toLowerCase();
if ("at-least-once".equals(mode)) {
    checkpointConfig.setCheckpointingMode(CheckpointingMode.AT_LEAST_ONCE);
    logger.info("Set checkpointing mode to AT_LEAST_ONCE (guarantees no data loss)");
} else if ("exactly-once".equals(mode)) {
    checkpointConfig.setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
    logger.info("Set checkpointing mode to EXACTLY_ONCE (requires idempotent sinks)");
}
```

### 2. What At-Least-Once Semantics Means

**At-least-once** delivery semantics guarantee that:

1. **No Data Loss**: Every record will be processed at least once, even in the presence of failures
2. **Possible Duplicates**: In case of failures and recovery, some records may be processed multiple times
3. **Lower Overhead**: Compared to exactly-once, at-least-once has lower performance overhead

### 3. How It Works

#### Checkpoint Mechanism
- Flink periodically creates checkpoints (every 5 minutes by default)
- Checkpoints capture the state of all operators and the position in the input streams
- When a failure occurs, Flink restores from the last successful checkpoint
- With AT_LEAST_ONCE mode, records between the last checkpoint and the failure may be reprocessed

#### Recovery Process
1. **Failure Detection**: System detects a task or node failure
2. **Checkpoint Restoration**: Flink restores state from the most recent checkpoint
3. **Stream Replay**: Input streams are replayed from the checkpoint position
4. **Reprocessing**: Records after the checkpoint are processed again (may include duplicates)

### 4. Configuration Example

#### Default Configuration (At-Least-Once)
```yaml
flink:
  checkpointingMode: at-least-once  # Default value
  checkpointInterval: 300000         # 5 minutes
  checkpointTimeout: 600000          # 10 minutes
  retainedCheckpoints: 3
```

#### Exactly-Once Configuration (Optional, for Task 11.2)
```yaml
flink:
  checkpointingMode: exactly-once    # Requires idempotent sinks
  checkpointInterval: 300000
  checkpointTimeout: 600000
  retainedCheckpoints: 3
```

### 5. Testing

#### Updated Tests
- **FlinkStreamProcessingPropertyTest**: Updated to verify the configured checkpointing mode
- **FlinkStreamProcessingIntegrationTest**: Updated to expect AT_LEAST_ONCE by default
- **FlinkEnvironmentConfiguratorTest**: Validates configuration loading and application

#### Test Verification
The tests verify that:
1. Default checkpointing mode is AT_LEAST_ONCE
2. Configuration can override to EXACTLY_ONCE when needed
3. Checkpoint interval and other parameters are correctly applied

### 6. Benefits of At-Least-Once

1. **Data Integrity**: Guarantees no data loss, which is critical for data pipelines
2. **Performance**: Lower overhead compared to exactly-once semantics
3. **Simplicity**: Doesn't require idempotent sinks or two-phase commit protocols
4. **Reliability**: Works with any sink, not just those supporting transactions

### 7. Handling Duplicates

While at-least-once semantics may produce duplicates, the system provides mechanisms to handle them:

1. **Idempotent Operations**: Design downstream processing to be idempotent where possible
2. **Deduplication**: Implement deduplication logic based on event IDs (requirement 9.6)
3. **Exactly-Once Option**: For critical use cases, configure exactly-once mode (task 11.2)

### 8. Monitoring

The system logs the configured checkpointing mode at startup:

```
INFO: Set checkpointing mode to AT_LEAST_ONCE (guarantees no data loss)
```

Monitor checkpoint metrics to ensure the system is functioning correctly:
- Checkpoint success rate
- Checkpoint duration
- Time since last successful checkpoint

### 9. Migration Path

To upgrade from at-least-once to exactly-once (when implementing task 11.2):

1. Ensure all sinks are idempotent or support transactions
2. Update configuration: `checkpointingMode: exactly-once`
3. Restart the Flink job with a savepoint
4. Monitor for any issues with the new mode

## Conclusion

The implementation of at-least-once semantics provides a solid foundation for data reliability in the realtime data pipeline system. It guarantees no data loss while maintaining good performance characteristics. The configurable design allows for future upgrades to exactly-once semantics when needed (task 11.2).

## Related Tasks

- **Task 11.2**: Implement exactly-once semantics (optional, requires idempotent sinks)
- **Task 11.3**: Implement data consistency detection
- **Task 11.4**: Write property-based tests for data consistency
- **Task 11.5**: Write unit tests for data consistency

## References

- Apache Flink Documentation: [Checkpointing](https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/datastream/fault-tolerance/checkpointing/)
- Apache Flink Documentation: [Delivery Guarantees](https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/datastream/fault-tolerance/guarantees/)
