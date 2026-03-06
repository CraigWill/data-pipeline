package com.realtime.monitor.controller;

import com.realtime.monitor.dto.ApiResponse;
import com.realtime.monitor.service.OutputFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
}
