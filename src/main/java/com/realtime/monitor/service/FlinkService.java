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
 * 支持 HA 模式：自动发现活跃的 JobManager leader
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlinkService {
    
    private final AppConfig appConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestTemplate restTemplate;

    /** 当前已知的活跃 leader URL */
    private volatile String activeLeaderUrl;
    /** 上次 leader 检测时间 */
    private volatile long lastLeaderCheckTime = 0;
    /** leader 缓存有效期（毫秒） */
    private static final long LEADER_CACHE_TTL = 30_000;
    
    @PostConstruct
    public void init() {
        // 设置较短的超时，避免 standby 节点长时间阻塞
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        restTemplate = new RestTemplate(factory);
    }

    /**
     * 获取所有候选 URL 列表（主 + 备）
     */
    private List<String> getCandidateUrls() {
        List<String> urls = new ArrayList<>();
        urls.add(appConfig.getFlinkRestUrl());
        String extra = appConfig.getFlinkRestUrls();
        if (extra != null && !extra.isBlank()) {
            for (String u : extra.split(",")) {
                String trimmed = u.trim();
                if (!trimmed.isEmpty() && !urls.contains(trimmed)) {
                    urls.add(trimmed);
                }
            }
        }
        return urls;
    }

    /**
     * 获取活跃 leader 的 URL，带缓存
     */
    private String getLeaderUrl() {
        long now = System.currentTimeMillis();
        if (activeLeaderUrl != null && (now - lastLeaderCheckTime) < LEADER_CACHE_TTL) {
            return activeLeaderUrl;
        }
        // 重新探测 leader
        for (String url : getCandidateUrls()) {
            try {
                String testUrl = url + "/jobs/overview";
                ResponseEntity<String> resp = restTemplate.getForEntity(testUrl, String.class);
                if (resp.getStatusCode().is2xxSuccessful()) {
                    if (!url.equals(activeLeaderUrl)) {
                        log.info("Flink leader: {}", url);
                    }
                    activeLeaderUrl = url;
                    lastLeaderCheckTime = now;
                    return url;
                }
            } catch (Exception e) {
                log.debug("Flink 候选节点 {} 不可用: {}", url, e.getMessage());
            }
        }
        // 所有节点都不可用，返回主 URL（让调用方处理异常）
        log.warn("未找到可用的 Flink leader，使用默认 URL");
        return appConfig.getFlinkRestUrl();
    }

    /** 获取活跃 leader URL（供其他服务使用） */
    public String getActiveLeaderUrl() {
        return getLeaderUrl();
    }
    
    /**
     * 获取所有作业列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getJobs() {
        try {
            String url = getLeaderUrl() + "/jobs/overview";
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
            String url = getLeaderUrl() + "/jobs/" + jobId;
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
        String url = getLeaderUrl() + "/jobs/" + jobId + "?mode=cancel";
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
                        new java.net.URL(getLeaderUrl() + "/jobs/" + jobId + "/yarn-cancel").openConnection();
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
            String url = getLeaderUrl() + "/overview";
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
            String url = getLeaderUrl() + "/taskmanagers";
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
            String url = getLeaderUrl() + "/jobmanager/config";
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
            String url = getLeaderUrl() + "/overview";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Flink 连接检查失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 触发 Savepoint（不停止作业，仅创建快照）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> triggerSavepoint(String jobId, String targetDirectory) {
        String url = getLeaderUrl() + "/jobs/" + jobId + "/savepoints";
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("target-directory", targetDirectory);
            requestBody.put("cancel-job", false);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
                String requestId = (String) result.get("request-id");
                if (requestId != null) {
                    return waitForSavepoint(jobId, requestId);
                }
            }
            throw new RuntimeException("触发 savepoint 失败: HTTP " + response.getStatusCode());
        } catch (Exception e) {
            log.error("触发 savepoint 失败: jobId={}", jobId, e);
            throw new RuntimeException("触发 savepoint 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 带 Savepoint 停止作业（推荐方式，不丢失数据）
     * 
     * 这会触发一个 savepoint，然后停止作业。
     * 作业可以从这个 savepoint 恢复，不会丢失任何数据。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> stopJobWithSavepoint(String jobId, String targetDirectory) {
        String url = getLeaderUrl() + "/jobs/" + jobId + "/stop";
        
        try {
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("targetDirectory", targetDirectory);
            requestBody.put("drain", false);  // 不等待所有数据处理完成
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
                String requestId = (String) result.get("request-id");
                
                // 等待 savepoint 完成
                if (requestId != null) {
                    Map<String, Object> savepointResult = waitForSavepoint(jobId, requestId);
                    result.putAll(savepointResult);
                }
                
                log.info("作业 {} 已停止，Savepoint: {}", jobId, result.get("location"));
                return result;
            }
            
            throw new RuntimeException("停止作业失败: HTTP " + response.getStatusCode());
        } catch (Exception e) {
            log.error("停止作业失败: {}", jobId, e);
            throw new RuntimeException("停止作业失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 等待 Savepoint 完成
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> waitForSavepoint(String jobId, String requestId) {
        String url = getLeaderUrl() + "/jobs/" + jobId + "/savepoints/" + requestId;
        
        int maxRetries = 60;  // 最多等待60秒
        for (int i = 0; i < maxRetries; i++) {
            try {
                Thread.sleep(1000);
                
                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
                    Map<String, Object> status = (Map<String, Object>) result.get("status");
                    
                    if (status != null) {
                        String statusId = (String) status.get("id");
                        if ("COMPLETED".equals(statusId)) {
                            Map<String, Object> operation = (Map<String, Object>) result.get("operation");
                            if (operation != null) {
                                return operation;
                            }
                            return result;
                        } else if ("FAILED".equals(statusId)) {
                            Map<String, Object> operation = (Map<String, Object>) result.get("operation");
                            String failureCause = operation != null ? (String) operation.get("failure-cause") : "Unknown";
                            throw new RuntimeException("Savepoint 失败: " + failureCause);
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("等待 Savepoint 被中断", e);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                log.warn("检查 Savepoint 状态失败: {}", e.getMessage());
            }
        }
        
        throw new RuntimeException("等待 Savepoint 超时");
    }
    
    /**
     * 获取作业的 Checkpoint 信息
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getCheckpoints(String jobId) {
        try {
            String url = getLeaderUrl() + "/jobs/" + jobId + "/checkpoints";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return objectMapper.readValue(response.getBody(), Map.class);
            }
        } catch (Exception e) {
            log.error("获取 Checkpoint 信息失败: {}", jobId, e);
        }
        return Collections.emptyMap();
    }
}
