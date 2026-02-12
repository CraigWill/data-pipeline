package com.realtime.pipeline.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.realtime.pipeline.model.DeadLetterRecord;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 基于文件系统的死信队列实现
 * 将失败记录存储为JSON文件
 */
@Slf4j
public class FileBasedDeadLetterQueue implements DeadLetterQueue {

    private final String basePath;
    private final ObjectMapper objectMapper;
    private final Path dlqDirectory;

    /**
     * 构造函数
     *
     * @param basePath 死信队列存储目录路径
     * @throws IOException 如果目录创建失败
     */
    public FileBasedDeadLetterQueue(String basePath) throws IOException {
        this.basePath = basePath;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.dlqDirectory = Paths.get(basePath);

        // 创建目录（如果不存在）
        if (!Files.exists(dlqDirectory)) {
            Files.createDirectories(dlqDirectory);
            log.info("Created dead letter queue directory: {}", basePath);
        }
    }

    @Override
    public void add(DeadLetterRecord record) throws IOException {
        if (record == null || record.getRecordId() == null) {
            throw new IllegalArgumentException("Record and recordId cannot be null");
        }

        Path filePath = getFilePath(record.getRecordId());
        
        // 写入临时文件，然后原子性地移动到目标位置
        Path tempFile = Files.createTempFile(dlqDirectory, "dlq-", ".tmp");
        try {
            objectMapper.writeValue(tempFile.toFile(), record);
            Files.move(tempFile, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.info("Added dead letter record: {} (eventId: {}, component: {}, reason: {})",
                    record.getRecordId(), record.getEventId(), record.getComponent(), record.getFailureReason());
        } catch (IOException e) {
            // 清理临时文件
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ex) {
                log.warn("Failed to delete temporary file: {}", tempFile, ex);
            }
            throw e;
        }
    }

    @Override
    public Optional<DeadLetterRecord> get(String recordId) throws IOException {
        if (recordId == null) {
            throw new IllegalArgumentException("RecordId cannot be null");
        }

        Path filePath = getFilePath(recordId);
        if (!Files.exists(filePath)) {
            return Optional.empty();
        }

        try {
            DeadLetterRecord record = objectMapper.readValue(filePath.toFile(), DeadLetterRecord.class);
            return Optional.of(record);
        } catch (IOException e) {
            log.error("Failed to read dead letter record: {}", recordId, e);
            throw e;
        }
    }

    @Override
    public List<DeadLetterRecord> listUnprocessed() throws IOException {
        return listAll().stream()
                .filter(record -> !record.isReprocessed())
                .collect(Collectors.toList());
    }

    @Override
    public List<DeadLetterRecord> listAll() throws IOException {
        if (!Files.exists(dlqDirectory)) {
            return new ArrayList<>();
        }

        try (Stream<Path> paths = Files.list(dlqDirectory)) {
            return paths
                    .filter(path -> path.toString().endsWith(".json"))
                    .map(path -> {
                        try {
                            return objectMapper.readValue(path.toFile(), DeadLetterRecord.class);
                        } catch (IOException e) {
                            log.error("Failed to read dead letter record from file: {}", path, e);
                            return null;
                        }
                    })
                    .filter(record -> record != null)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public void markAsReprocessed(String recordId) throws IOException {
        if (recordId == null) {
            throw new IllegalArgumentException("RecordId cannot be null");
        }

        Optional<DeadLetterRecord> recordOpt = get(recordId);
        if (!recordOpt.isPresent()) {
            throw new IllegalArgumentException("Record not found: " + recordId);
        }

        DeadLetterRecord record = recordOpt.get();
        DeadLetterRecord updatedRecord = DeadLetterRecord.builder()
                .recordId(record.getRecordId())
                .eventId(record.getEventId())
                .failureTimestamp(record.getFailureTimestamp())
                .failureReason(record.getFailureReason())
                .stackTrace(record.getStackTrace())
                .component(record.getComponent())
                .operationType(record.getOperationType())
                .retryCount(record.getRetryCount())
                .originalData(record.getOriginalData())
                .dataType(record.getDataType())
                .context(record.getContext())
                .reprocessed(true)
                .reprocessTimestamp(System.currentTimeMillis())
                .build();

        add(updatedRecord);
        log.info("Marked dead letter record as reprocessed: {}", recordId);
    }

    @Override
    public void delete(String recordId) throws IOException {
        if (recordId == null) {
            throw new IllegalArgumentException("RecordId cannot be null");
        }

        Path filePath = getFilePath(recordId);
        if (Files.exists(filePath)) {
            Files.delete(filePath);
            log.info("Deleted dead letter record: {}", recordId);
        }
    }

    @Override
    public long count() throws IOException {
        if (!Files.exists(dlqDirectory)) {
            return 0;
        }

        try (Stream<Path> paths = Files.list(dlqDirectory)) {
            return paths.filter(path -> path.toString().endsWith(".json")).count();
        }
    }

    @Override
    public void clear() throws IOException {
        if (!Files.exists(dlqDirectory)) {
            return;
        }

        try (Stream<Path> paths = Files.list(dlqDirectory)) {
            List<Path> files = paths
                    .filter(path -> path.toString().endsWith(".json"))
                    .collect(Collectors.toList());
            
            for (Path file : files) {
                Files.delete(file);
            }
            log.info("Cleared dead letter queue: {} records deleted", files.size());
        }
    }

    @Override
    public void close() {
        // 文件系统实现不需要特殊的清理操作
        log.info("Closed dead letter queue");
    }

    /**
     * 获取记录的文件路径
     */
    private Path getFilePath(String recordId) {
        // 如果recordId已经包含.json后缀，不再添加
        if (recordId.endsWith(".json")) {
            return dlqDirectory.resolve(recordId);
        }
        return dlqDirectory.resolve(recordId + ".json");
    }

    /**
     * 获取死信队列目录路径
     */
    public String getBasePath() {
        return basePath;
    }
}
