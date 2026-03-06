package com.realtime.monitor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtime.monitor.config.AppConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * Flink REST API 服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlinkService {
    
    private final AppConfig appConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestTemplate restTemplate;
    
    @PostConstruct
    public void init() {
        restTemplate = new RestTemplate();
    }
    
    /**
     * 获取所有作业列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getJobs() {
        try {
            String url = appConfig.getFlinkRestUrl() + "/jobs/overview";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode jobs = root.get("jobs");
                if (jobs != null && jobs.isArray()) {
                    List<Map<String, Object>> result = new ArrayList<>();
                    for (JsonNode job : jobs) {
                        result.add(objectMapper.convertValue(job, Map.class));
                    }
                    return result;
                }
            }
        } catch (Exception e) {
            log.error("获取作业列表失败", e);
        }
        return Collections.emptyList();
    }
    
    /**
     * 获取作业详情
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getJobDetail(String jobId) {
        try {
            String url = appConfig.getFlinkRestUrl() + "/jobs/" + jobId;
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return objectMapper.readValue(response.getBody(), Map.class);
            }
        } catch (Exception e) {
            log.error("获取作业详情失败: {}", jobId, e);
        }
        return Collections.emptyMap();
    }
    
    /**
     * 获取作业指标
     */
    public Map<String, Object> getJobMetrics(String jobId) {
        Map<String, Object> jobData = getJobDetail(jobId);
        
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("job_id", jobId);
        metrics.put("name", jobData.get("name"));
        metrics.put("state", jobData.get("state"));
        metrics.put("start_time", jobData.get("start-time"));
        metrics.put("duration", jobData.get("duration"));
        
        List<Map<String, Object>> vertices = new ArrayList<>();
        Object verticesObj = jobData.get("vertices");
        if (verticesObj instanceof List) {
            for (Object v : (List<?>) verticesObj) {
                if (v instanceof Map) {
                    Map<?, ?> vertex = (Map<?, ?>) v;
                    Map<String, Object> vertexMetrics = new HashMap<>();
                    vertexMetrics.put("id", vertex.get("id"));
                    vertexMetrics.put("name", vertex.get("name"));
                    vertexMetrics.put("parallelism", vertex.get("parallelism"));
                    vertexMetrics.put("status", vertex.get("status"));
                    vertices.add(vertexMetrics);
                }
            }
        }
        metrics.put("vertices", vertices);
        
        return metrics;
    }
    
    /**
     * 取消作业
     */
    /**
     * 取消作业
     * 注意：Java 11 的 HttpURLConnection 不支持 PATCH 方法，
     * 所以用 HttpURLConnection + 反射 hack 来发送 PATCH 请求
     */
    public void cancelJob(String jobId) {
        String url = appConfig.getFlinkRestUrl() + "/jobs/" + jobId + "?mode=cancel";
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL(url).openConnection();
            conn.setRequestMethod("POST");
            // Flink REST API 接受 PATCH，但 HttpURLConnection 不支持
            // 使用反射强制设置 method 为 PATCH
            try {
                java.lang.reflect.Field methodField = java.net.HttpURLConnection.class.getDeclaredField("method");
                methodField.setAccessible(true);
                methodField.set(conn, "PATCH");
            } catch (Exception e) {
                // 反射失败时回退：直接用 POST 到 yarn-cancel 端点
                conn.disconnect();
                conn = (java.net.HttpURLConnection)
                        new java.net.URL(appConfig.getFlinkRestUrl() + "/jobs/" + jobId + "/yarn-cancel").openConnection();
                conn.setRequestMethod("GET");
            }
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(false);

            int code = conn.getResponseCode();
            conn.disconnect();
            if (code < 200 || code >= 300) {
                throw new RuntimeException("取消作业失败: HTTP " + code);
            }
            log.info("作业已取消: {}", jobId);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("取消作业失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取集群概览
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getClusterOverview() {
        try {
            String url = appConfig.getFlinkRestUrl() + "/overview";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return objectMapper.readValue(response.getBody(), Map.class);
            }
        } catch (Exception e) {
            log.error("获取集群概览失败", e);
        }
        return Collections.emptyMap();
    }
    
    /**
     * 获取 TaskManager 列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTaskManagers() {
        try {
            String url = appConfig.getFlinkRestUrl() + "/taskmanagers";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode taskmanagers = root.get("taskmanagers");
                if (taskmanagers != null && taskmanagers.isArray()) {
                    List<Map<String, Object>> result = new ArrayList<>();
                    for (JsonNode tm : taskmanagers) {
                        result.add(objectMapper.convertValue(tm, Map.class));
                    }
                    return result;
                }
            }
        } catch (Exception e) {
            log.error("获取 TaskManager 列表失败", e);
        }
        return Collections.emptyList();
    }
    
    /**
     * 获取 JobManager 配置
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getJobManagerConfig() {
        try {
            String url = appConfig.getFlinkRestUrl() + "/jobmanager/config";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return objectMapper.readValue(response.getBody(), List.class);
            }
        } catch (Exception e) {
            log.error("获取 JobManager 配置失败", e);
        }
        return Collections.emptyList();
    }
    
    /**
     * 获取运行中的作业
     */
    public List<Map<String, Object>> getRunningJobs() {
        List<Map<String, Object>> allJobs = getJobs();
        List<Map<String, Object>> runningJobs = new ArrayList<>();
        
        for (Map<String, Object> job : allJobs) {
            if ("RUNNING".equals(job.get("state"))) {
                runningJobs.add(job);
            }
        }
        
        return runningJobs;
    }
    
    /**
     * 检查 Flink 连接状态
     */
    public boolean isFlinkHealthy() {
        try {
            String url = appConfig.getFlinkRestUrl() + "/overview";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Flink 连接检查失败: {}", e.getMessage());
            return false;
        }
    }
    
    private String extractHost(String url) {
        return url.replaceAll("https?://", "").split(":")[0];
    }
    
    private int extractPort(String url) {
        String[] parts = url.replaceAll("https?://", "").split(":");
        if (parts.length > 1) {
            return Integer.parseInt(parts[1].split("/")[0]);
        }
        return 8081;
    }
}
