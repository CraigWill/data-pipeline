package com.realtime.monitor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtime.monitor.config.AppConfig;
import com.realtime.monitor.dto.DataSourceConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 数据源管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataSourceService {

    private final com.realtime.monitor.repository.DataSourceRepository dataSourceRepository;

    /**
     * 保存数据源配置
     */
    public String saveDataSource(DataSourceConfig config) {
        String dsId = config.getId();
        if (dsId == null || dsId.isEmpty()) {
            dsId = "ds-" + System.currentTimeMillis();
            config.setId(dsId);
        }

        dataSourceRepository.save(config);
        return dsId;
    }

    /**
     * 加载数据源配置
     */
    public DataSourceConfig loadDataSource(String dsId) {
        DataSourceConfig config = dataSourceRepository.findById(dsId);
        if (config == null) {
            throw new RuntimeException("数据源配置不存在: " + dsId);
        }
        return config;
    }

    /**
     * 列出所有数据源配置
     */
    public List<Map<String, Object>> listDataSources() {
        List<DataSourceConfig> dataSources = dataSourceRepository.findAll();

        return dataSources.stream()
                .map(config -> {
                    Map<String, Object> summary = new HashMap<>();
                    summary.put("id", config.getId());
                    summary.put("name", config.getName() != null ? config.getName() : "Unnamed DataSource");
                    summary.put("host", config.getHost() != null ? config.getHost() : "Unknown");
                    summary.put("port", config.getPort());
                    summary.put("sid", config.getSid() != null ? config.getSid() : "Unknown");
                    summary.put("description", config.getDescription());
                    summary.put("created_at", config.getCreatedAt());
                    summary.put("updated_at", config.getUpdatedAt());
                    return summary;
                })
                .collect(Collectors.toList());
    }

    /**
     * 删除数据源配置
     */
    public void deleteDataSource(String dsId) {
        dataSourceRepository.deleteById(dsId);
    }

    /**
     * 更新数据源配置
     */
    public void updateDataSource(String dsId, DataSourceConfig config) {
        config.setId(dsId);
        saveDataSource(config);
    }
}
