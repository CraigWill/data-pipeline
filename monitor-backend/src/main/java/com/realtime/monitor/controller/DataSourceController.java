package com.realtime.monitor.controller;

import com.realtime.monitor.dto.ApiResponse;
import com.realtime.monitor.dto.DataSourceConfig;
import com.realtime.monitor.service.CdcTaskService;
import com.realtime.monitor.service.DataSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据源管理 API
 */
@Slf4j
@RestController
@RequestMapping("/api/datasources")
@RequiredArgsConstructor
public class DataSourceController {
    
    private final DataSourceService dataSourceService;
    private final CdcTaskService cdcTaskService;
    
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listDataSources() {
        try {
            return ApiResponse.success(dataSourceService.listDataSources());
        } catch (Exception e) {
            log.error("获取数据源列表失败", e);
            // 安全修复：不泄露详细错误信息给客户端
            return ApiResponse.error("获取数据源列表失败，请稍后重试");
        }
    }
    
    @PostMapping
    public ApiResponse<Map<String, Object>> createDataSource(@RequestBody DataSourceConfig config) {
        try {
            String dsId = dataSourceService.saveDataSource(config);
            return ApiResponse.success(Map.of("id", dsId), "数据源创建成功");
        } catch (Exception e) {
            log.error("创建数据源失败", e);
            // 安全修复：不泄露详细错误信息给客户端
            return ApiResponse.error("创建数据源失败，请稍后重试");
        }
    }
    
    @GetMapping("/{dsId}")
    public ApiResponse<DataSourceConfig> getDataSource(@PathVariable String dsId) {
        try {
            return ApiResponse.success(dataSourceService.loadDataSource(dsId));
        } catch (Exception e) {
            log.error("获取数据源详情失败：{}", dsId, e);
            // 安全修复：不泄露详细错误信息给客户端
            return ApiResponse.error("获取数据源详情失败，请稍后重试");
        }
    }
    
    @PutMapping("/{dsId}")
    public ApiResponse<Void> updateDataSource(@PathVariable String dsId, @RequestBody DataSourceConfig config) {
        try {
            dataSourceService.updateDataSource(dsId, config);
            return ApiResponse.success(null, "数据源更新成功");
        } catch (Exception e) {
            log.error("更新数据源失败：{}", dsId, e);
            // 安全修复：不泄露详细错误信息给客户端
            return ApiResponse.error("更新数据源失败，请稍后重试");
        }
    }
    
    @DeleteMapping("/{dsId}")
    public ApiResponse<Void> deleteDataSource(@PathVariable String dsId) {
        try {
            dataSourceService.deleteDataSource(dsId);
            return ApiResponse.success(null, "数据源删除成功");
        } catch (Exception e) {
            log.error("删除数据源失败：{}", dsId, e);
            // 安全修复：不泄露详细错误信息给客户端
            return ApiResponse.error("删除数据源失败，请稍后重试");
        }
    }
    
    @PostMapping("/{dsId}/test")
    public ApiResponse<Map<String, Object>> testDataSource(@PathVariable String dsId) {
        try {
            DataSourceConfig config = dataSourceService.loadDataSource(dsId);
            Map<String, Object> result = cdcTaskService.testConnection(config);
            boolean success = (boolean) result.get("success");
            
            // 更新状态
            dataSourceService.updateDataSourceStatus(dsId, success ? "SUCCESS" : "FAILED");
            
            return success 
                    ? ApiResponse.success(result) 
                    : ApiResponse.error((String) result.get("error"));
        } catch (Exception e) {
            log.error("测试数据源连接失败：{}", dsId, e);
            dataSourceService.updateDataSourceStatus(dsId, "FAILED");
            // 安全修复：不泄露详细错误信息给客户端
            return ApiResponse.error("测试数据源连接失败，请稍后重试");
        }
    }
    
    @GetMapping("/{dsId}/schemas")
    public ApiResponse<List<String>> getSchemas(@PathVariable String dsId) {
        try {
            DataSourceConfig config = dataSourceService.loadDataSource(dsId);
            return ApiResponse.success(cdcTaskService.discoverSchemas(config));
        } catch (Exception e) {
            log.error("获取 Schema 列表失败：{}", dsId, e);
            // 安全修复：不泄露详细错误信息给客户端
            return ApiResponse.error("获取 Schema 列表失败，请稍后重试");
        }
    }
    
    @GetMapping("/{dsId}/schemas/{schema}/tables")
    public ApiResponse<List<Map<String, Object>>> getTables(
            @PathVariable String dsId, @PathVariable String schema) {
        try {
            DataSourceConfig config = dataSourceService.loadDataSource(dsId);
            return ApiResponse.success(cdcTaskService.discoverTables(config, schema));
        } catch (Exception e) {
            log.error("获取表列表失败：{}/{}", dsId, schema, e);
            // 安全修复：不泄露详细错误信息给客户端
            return ApiResponse.error("获取表列表失败，请稍后重试");
        }
    }
}
