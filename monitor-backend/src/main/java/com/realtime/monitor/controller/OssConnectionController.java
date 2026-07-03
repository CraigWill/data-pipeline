package com.realtime.monitor.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.realtime.monitor.dto.ApiResponse;
import com.realtime.monitor.dto.OssConnection;
import com.realtime.monitor.service.OssConnectionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OSS 连接配置管理 API
 */
@Slf4j
@RestController
@RequestMapping("/api/oss-connections")
@RequiredArgsConstructor
public class OssConnectionController {

    private final OssConnectionService service;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        try {
            return ApiResponse.success(service.list());
        } catch (Exception e) {
            log.error("获取 OSS 连接列表失败", e);
            return ApiResponse.error("获取 OSS 连接列表失败，请稍后重试");
        }
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody OssConnection config) {
        try {
            String id = service.save(config);
            return ApiResponse.success(Map.of("id", id), "OSS 连接创建成功");
        } catch (Exception e) {
            log.error("创建 OSS 连接失败", e);
            return ApiResponse.error("创建 OSS 连接失败，请稍后重试");
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable String id, @RequestBody OssConnection config) {
        try {
            service.update(id, config);
            return ApiResponse.success(null, "OSS 连接更新成功");
        } catch (Exception e) {
            log.error("更新 OSS 连接失败：{}", id, e);
            return ApiResponse.error("更新 OSS 连接失败，请稍后重试");
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        try {
            service.delete(id);
            return ApiResponse.success(null, "OSS 连接删除成功");
        } catch (Exception e) {
            log.error("删除 OSS 连接失败：{}", id, e);
            return ApiResponse.error("删除 OSS 连接失败，请稍后重试");
        }
    }

    @PostMapping("/{id}/test")
    public ApiResponse<Map<String, Object>> test(@PathVariable String id) {
        try {
            Map<String, Object> result = service.testConnection(id);
            boolean success = Boolean.TRUE.equals(result.get("success"));
            service.updateStatus(id, success ? "SUCCESS" : "FAILED");
            return success ? ApiResponse.success(result) : ApiResponse.error((String) result.get("error"));
        } catch (Exception e) {
            log.error("测试 OSS 连接失败：{}", id, e);
            service.updateStatus(id, "FAILED");
            return ApiResponse.error("测试 OSS 连接失败，请稍后重试");
        }
    }
}
