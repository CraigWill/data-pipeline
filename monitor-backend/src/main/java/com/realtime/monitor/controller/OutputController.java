package com.realtime.monitor.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.realtime.monitor.dto.ApiResponse;
import com.realtime.monitor.service.OutputFileService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 输出文件监控 API
 */
@Slf4j
@RestController
@RequestMapping("/api/output")
@RequiredArgsConstructor
public class OutputController {
    
    private final OutputFileService outputFileService;
    
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getOutputStats() {
        try {
            return ApiResponse.success(outputFileService.getOutputStats());
        } catch (Exception e) {
            log.error("获取输出统计失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    @GetMapping("/files")
    public ApiResponse<List<Map<String, Object>>> getOutputFiles(
            @RequestParam(required = false) String table,
            @RequestParam(defaultValue = "50") int limit) {
        try {
            return ApiResponse.success(outputFileService.getOutputFiles(table, limit));
        } catch (Exception e) {
            log.error("获取输出文件列表失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 读取文件内容（优先 OSS，降级本地）
     */
    @GetMapping("/files/content")
    public ApiResponse<String> getFileContent(
            @RequestParam String dateDir,
            @RequestParam String fileName) {
        try {
            byte[] content = outputFileService.readFileContent(dateDir, fileName);
            if (content == null) {
                return ApiResponse.error("文件不存在");
            }
            return ApiResponse.success(new String(content, java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("读取文件内容失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }
}
