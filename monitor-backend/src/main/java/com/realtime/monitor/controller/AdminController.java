package com.realtime.monitor.controller;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.realtime.monitor.dto.ApiResponse;
import com.realtime.monitor.service.DataSourceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 管理员工具 API
 * 
 * 遵循 Controller → Service → Repository 三层架构，
 * Controller 只负责请求/响应处理，业务逻辑委托给 Service 层。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    
    private final DataSourceService dataSourceService;
    
    /**
     * 重新加密所有数据源密码
     * 用于修复密码格式问题或密钥轮换后重新加密
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/datasources/reencrypt")
    public ApiResponse<Map<String, Object>> reencryptDataSourcePasswords() {
        try {
            Map<String, Object> result = dataSourceService.reencryptAllPasswords();
            int updated = (int) result.get("updated");
            int skipped = (int) result.get("skipped");
            int failed = (int) result.get("failed");
            
            return ApiResponse.success(result, 
                String.format("重新加密完成: %d 个已更新, %d 个已跳过, %d 个失败", updated, skipped, failed));
        } catch (Exception e) {
            log.error("重新加密数据源密码失败", e);
            return ApiResponse.error("重新加密失败: " + e.getMessage());
        }
    }
}
