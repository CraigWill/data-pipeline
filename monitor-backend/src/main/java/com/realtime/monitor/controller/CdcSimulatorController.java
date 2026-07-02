package com.realtime.monitor.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.realtime.monitor.dto.ApiResponse;
import com.realtime.monitor.service.CdcSimulatorService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 模拟 CDC 事件 API：查询表数据、批量 INSERT/UPDATE/DELETE 以触发 CDC 变更。
 */
@Slf4j
@RestController
@RequestMapping("/api/cdc-simulator")
@RequiredArgsConstructor
public class CdcSimulatorController {

    private final CdcSimulatorService cdcSimulatorService;

    /**
     * 获取指定表的列结构（用于前端生成表单）
     */
    @GetMapping("/{dsId}/schemas/{schema}/tables/{table}/columns")
    public ApiResponse<List<Map<String, Object>>> getColumns(
            @PathVariable String dsId,
            @PathVariable String schema,
            @PathVariable String table) {
        try {
            return ApiResponse.success(cdcSimulatorService.getColumns(dsId, schema, table));
        } catch (Exception e) {
            log.error("获取列结构失败：{}/{}/{}", dsId, schema, table, e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 分页查询表数据
     */
    @GetMapping("/{dsId}/schemas/{schema}/tables/{table}/data")
    public ApiResponse<Map<String, Object>> queryData(
            @PathVariable String dsId,
            @PathVariable String schema,
            @PathVariable String table,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            return ApiResponse.success(cdcSimulatorService.queryData(dsId, schema, table, page, size));
        } catch (Exception e) {
            log.error("查询表数据失败：{}/{}/{}", dsId, schema, table, e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 批量插入
     * body: { "rows": [ {col: val, ...}, ... ] }
     */
    @PostMapping("/{dsId}/schemas/{schema}/tables/{table}/insert")
    public ApiResponse<Map<String, Object>> insert(
            @PathVariable String dsId,
            @PathVariable String schema,
            @PathVariable String table,
            @RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("rows");
            int affected = cdcSimulatorService.batchInsert(dsId, schema, table, rows);
            return ApiResponse.success(Map.of("affected", affected), "成功插入 " + affected + " 行");
        } catch (Exception e) {
            log.error("批量插入失败：{}/{}/{}", dsId, schema, table, e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 自动生成并插入指定数量的模拟数据
     * body: { "count": 100 }
     */
    @PostMapping("/{dsId}/schemas/{schema}/tables/{table}/auto-insert")
    public ApiResponse<Map<String, Object>> autoInsert(
            @PathVariable String dsId,
            @PathVariable String schema,
            @PathVariable String table,
            @RequestBody Map<String, Object> body) {
        try {
            Object countObj = body.get("count");
            int count = countObj instanceof Number ? ((Number) countObj).intValue()
                    : Integer.parseInt(String.valueOf(countObj));
            int affected = cdcSimulatorService.autoInsert(dsId, schema, table, count);
            return ApiResponse.success(Map.of("affected", affected), "已自动插入 " + affected + " 行模拟数据");
        } catch (Exception e) {
            log.error("自动插入失败：{}/{}/{}", dsId, schema, table, e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 批量更新
     * body: { "rows": [ {col: val, ...}, ... ], "keyColumns": ["ID"] }
     */
    @PostMapping("/{dsId}/schemas/{schema}/tables/{table}/update")
    public ApiResponse<Map<String, Object>> update(
            @PathVariable String dsId,
            @PathVariable String schema,
            @PathVariable String table,
            @RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("rows");
            @SuppressWarnings("unchecked")
            List<String> keyColumns = (List<String>) body.get("keyColumns");
            int affected = cdcSimulatorService.batchUpdate(dsId, schema, table, rows, keyColumns);
            return ApiResponse.success(Map.of("affected", affected), "成功更新 " + affected + " 行");
        } catch (Exception e) {
            log.error("批量更新失败：{}/{}/{}", dsId, schema, table, e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 批量删除
     * body: { "rows": [ {keyCol: val, ...}, ... ], "keyColumns": ["ID"] }
     */
    @PostMapping("/{dsId}/schemas/{schema}/tables/{table}/delete")
    public ApiResponse<Map<String, Object>> delete(
            @PathVariable String dsId,
            @PathVariable String schema,
            @PathVariable String table,
            @RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("rows");
            @SuppressWarnings("unchecked")
            List<String> keyColumns = (List<String>) body.get("keyColumns");
            int affected = cdcSimulatorService.batchDelete(dsId, schema, table, rows, keyColumns);
            return ApiResponse.success(Map.of("affected", affected), "成功删除 " + affected + " 行");
        } catch (Exception e) {
            log.error("批量删除失败：{}/{}/{}", dsId, schema, table, e);
            return ApiResponse.error(e.getMessage());
        }
    }
}
