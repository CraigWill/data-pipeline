package com.realtime.pipeline.datahub.client;

import java.io.Closeable;
import java.util.List;
import java.util.Map;

/**
 * DataHub客户端接口
 * 定义与阿里云DataHub交互的基本操作
 * 
 * 注意: 这是一个简化的接口定义，用于封装DataHub SDK的核心功能。
 * 在生产环境中，应该使用阿里云官方的DataHub SDK。
 */
public interface DataHubClient extends Closeable {
    
    /**
     * 发送单条记录到DataHub
     * 
     * @param project 项目名称
     * @param topic 主题名称
     * @param record 记录数据
     * @return 发送结果
     * @throws DataHubClientException 如果发送失败
     */
    PutRecordResult putRecord(String project, String topic, RecordEntry record) 
        throws DataHubClientException;
    
    /**
     * 批量发送记录到DataHub
     * 
     * @param project 项目名称
     * @param topic 主题名称
     * @param records 记录列表
     * @return 批量发送结果
     * @throws DataHubClientException 如果发送失败
     */
    PutRecordsResult putRecords(String project, String topic, List<RecordEntry> records) 
        throws DataHubClientException;
    
    /**
     * 获取主题信息
     * 
     * @param project 项目名称
     * @param topic 主题名称
     * @return 主题信息
     * @throws DataHubClientException 如果获取失败
     */
    TopicInfo getTopic(String project, String topic) throws DataHubClientException;
    
    /**
     * 从DataHub获取记录（消费数据）
     * 
     * @param project 项目名称
     * @param topic 主题名称
     * @param consumerGroup 消费者组名称
     * @param maxRecords 最大记录数
     * @return 记录列表
     * @throws DataHubClientException 如果获取失败
     */
    List<Map<String, Object>> getRecords(String project, String topic, 
                                          String consumerGroup, int maxRecords) 
        throws DataHubClientException;
    
    /**
     * 关闭客户端连接
     */
    @Override
    void close();
}
