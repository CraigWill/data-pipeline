package com.realtime.monitor.controller;

import com.realtime.monitor.dto.ApiResponse;
import com.realtime.monitor.service.CdcEventsService;
import com.realtime.monitor.service.CdcStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * CDC 事件监控 API
 */
/**
 * CDC 事件监控 API
 */
@Slf4j
@RestController
@RequestMapping("/api/cdc/events")
@RequiredArgsConstructor
public class CdcEventsController {

    private final CdcEventsService cdcEventsService;
    private final CdcStatsService cdcStatsService;

    /**
     * 获取 CDC 事件列表
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> listEvents(
            @RequestParam(required = false) String table,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Map<String, Object> result = cdcEventsService.listEvents(table, eventType, page, size);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取 CDC 事件列表失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取表列表
     */
    @GetMapping("/tables")
    public ApiResponse<List<String>> getTables() {
        try {
            List<String> tables = cdcEventsService.getTables();
            return ApiResponse.success(tables);
        } catch (Exception e) {
            log.error("获取表列表失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取事件统计
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats() {
        try {
            Map<String, Object> stats = cdcEventsService.getStats();
            return ApiResponse.success(stats);
        } catch (Exception e) {
            log.error("获取事件统计失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取当日实时统计（基于流数据）
     */
    @GetMapping("/stats/today")
    public ApiResponse<Map<String, Object>> getTodayStats() {
        try {
            Map<String, Object> stats = cdcStatsService.getTodayStats();
            return ApiResponse.success(stats);
        } catch (Exception e) {
            log.error("获取当日统计失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取24小时分布统计（基于流数据）
     */
    @GetMapping("/stats/daily")
    public ApiResponse<Map<String, Object>> getStatsByDate(
            @RequestParam(required = false) String table) {
        try {
            Map<String, Object> stats = cdcStatsService.getHourlyStats(table);
            return ApiResponse.success(stats);
        } catch (Exception e) {
            log.error("获取日期统计失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * SSE 实时数据流 - 推送实时速率
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamStats() {
        SseEmitter emitter = new SseEmitter(0L); // 无超时
        cdcStatsService.addEmitter(emitter);

        emitter.onCompletion(() -> cdcStatsService.removeEmitter(emitter));
        emitter.onTimeout(() -> cdcStatsService.removeEmitter(emitter));
        emitter.onError(e -> cdcStatsService.removeEmitter(emitter));

        // 立即发送当前状态
        try {
            emitter.send(SseEmitter.event()
                .name("stats")
                .data(cdcStatsService.getTodayStats()));
        } catch (Exception e) {
            log.debug("发送初始状态失败: {}", e.getMessage());
        }

        return emitter;
    }

    /**
     * 手动记录事件（用于测试或外部调用）
     */
    @PostMapping("/record")
    public ApiResponse<String> recordEvent(
            @RequestParam String tableName,
            @RequestParam(defaultValue = "INSERT") String eventType,
            @RequestParam(defaultValue = "1") long count) {
        try {
            cdcStatsService.recordEvents(tableName, eventType, count);
            return ApiResponse.success("已记录 " + count + " 条事件");
        } catch (Exception e) {
            log.error("记录事件失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }


    /**
     * 根据日期获取文件列表
     */
    @GetMapping("/files")
    public ApiResponse<Map<String, Object>> listFilesByDate(
            @RequestParam(required = false) String date) {
        try {
            // 默认使用今天的日期
            if (date == null || date.isEmpty()) {
                date = java.time.LocalDate.now().toString();
            }
            Map<String, Object> result = cdcEventsService.listFilesByDate(date);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取文件列表失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取文件内容（分页）
     */
    @GetMapping("/files/content")
    public ApiResponse<Map<String, Object>> getFileContent(
            @RequestParam String path,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size) {
        try {
            Map<String, Object> result = cdcEventsService.getFileContent(path, page, size);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取文件内容失败: {}", path, e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取可用的日期列表
     */
    @GetMapping("/dates")
    public ApiResponse<List<String>> getAvailableDates() {
        try {
            List<String> dates = cdcEventsService.getAvailableDates();
            return ApiResponse.success(dates);
        } catch (Exception e) {
            log.error("获取日期列表失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

}
