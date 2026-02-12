package com.realtime.pipeline.error;

import com.realtime.pipeline.model.DeadLetterRecord;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * 死信队列接口
 * 用于存储和管理处理失败的记录
 */
public interface DeadLetterQueue {

    /**
     * 将失败记录添加到死信队列
     *
     * @param record 死信记录
     * @throws IOException 如果存储失败
     */
    void add(DeadLetterRecord record) throws IOException;

    /**
     * 根据记录ID获取死信记录
     *
     * @param recordId 记录ID
     * @return 死信记录，如果不存在则返回空
     * @throws IOException 如果读取失败
     */
    Optional<DeadLetterRecord> get(String recordId) throws IOException;

    /**
     * 获取所有未重新处理的死信记录
     *
     * @return 未重新处理的死信记录列表
     * @throws IOException 如果读取失败
     */
    List<DeadLetterRecord> listUnprocessed() throws IOException;

    /**
     * 获取所有死信记录
     *
     * @return 所有死信记录列表
     * @throws IOException 如果读取失败
     */
    List<DeadLetterRecord> listAll() throws IOException;

    /**
     * 标记记录为已重新处理
     *
     * @param recordId 记录ID
     * @throws IOException 如果更新失败
     */
    void markAsReprocessed(String recordId) throws IOException;

    /**
     * 删除死信记录
     *
     * @param recordId 记录ID
     * @throws IOException 如果删除失败
     */
    void delete(String recordId) throws IOException;

    /**
     * 获取死信队列中的记录数量
     *
     * @return 记录数量
     * @throws IOException 如果读取失败
     */
    long count() throws IOException;

    /**
     * 清空死信队列
     *
     * @throws IOException 如果清空失败
     */
    void clear() throws IOException;

    /**
     * 关闭死信队列，释放资源
     */
    void close();
}
