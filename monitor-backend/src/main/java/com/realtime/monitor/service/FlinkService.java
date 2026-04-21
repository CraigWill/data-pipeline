package com.realtime.monitor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtime.monitor.config.AppConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.PostConstruct;
import java.net.URI;
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
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        restTemplate = new RestTemplate(factory);
    }

    // -------------------------------------------------------------------------
    // XSS sanitization — HTML-escape all string values in Maps/Lists returned
    // from the Flink REST API before they reach the frontend.
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitize(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return map;
        Map<String, Object> safe = new HashMap<>(map.size());
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            safe.put(entry.getKey(), sanitizeValue(entry.getValue()));
        }
        return safe;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> sanitizeList(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) return list;
        List<Map<String, Object>> safe = new ArrayList<>(list.size());
        for (Map<String, Object> item : list) {
            safe.add(sanitize(item));
        }
        return safe;
    }

    @SuppressWarnings("unchecked")
    private Object sanitizeValue(Object value) {
        if (value instanceof String) {
            return HtmlUtils.htmlEscape((String) value);
        } else if (value instanceof Map) {
            return sanitize((Map<String, Object>) value);
        } else if (value instanceof List) {
            List<Object> safeList = new ArrayList<>();
            for (Object item : (List<?>) value) {
                safeList.add(sanitizeValue(item));
            }
            return safeList;
        }
        return value; // numbers, booleans, nulls pass through
    }

    // -------------------------------------------------------------------------
    // URL construction helpers — all URLs go through UriComponentsBuilder so
    // that path segments are percent-encoded and injection is prevented.
    // -------------------------------------------------------------------------

    /**
     * Build a safe URI from the leader base URL and one or more path segments.
     * Each segment is encoded individually, preventing path traversal or host
     * injection via user-supplied values like jobId / requestId.
     */
    private URI buildUri(String base, String... pathSegments) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(base);
        for (String segment : pathSegments) {
            builder.pathSegment(segment);
        }
        return builder.build().toUri();
    }

    /**
     * Validate that a candidate base URL uses http/https and does not contain
     * unexpected characters that could redirect requests to unintended hosts.
     */
    private boolean isAllowedBaseUrl(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (Exception e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Leader discovery
    // -------------------------------------------------------------------------

    private List<String> getCandidateUrls() {
        List<String> urls = new ArrayList<>();
        String primary = appConfig.getFlinkRestUrl();
        if (isAllowedBaseUrl(primary)) {
            urls.add(primary.trim());
        }
        String extra = appConfig.getFlinkRestUrls();
        if (extra != null && !extra.isBlank()) {
            for (String u : extra.split(",")) {
                String trimmed = u.trim();
                if (!trimmed.isEmpty() && isAllowedBaseUrl(trimmed) && !urls.contains(trimmed)) {
                    urls.add(trimmed);
                }
            }
        }
        return urls;
    }

    private String getLeaderUrl() {
        long now = System.currentTimeMillis();
        if (activeLeaderUrl != null && (now - lastLeaderCheckTime) < LEADER_CACHE_TTL) {
            return activeLeaderUrl;
        }
        for (String base : getCandidateUrls()) {
            try {
                URI testUri = buildUri(base, "jobs", "overview");
                ResponseEntity<String> resp = restTemplate.getForEntity(testUri, String.class);
                if (resp.getStatusCode().is2xxSuccessful()) {
                    if (!base.equals(activeLeaderUrl)) {
                        log.info("Flink leader: {}", base);
                    }
                    activeLeaderUrl = base;
                    lastLeaderCheckTime = now;
                    return base;
                }
            } catch (Exception e) {
                log.debug("Flink 候选节点 {} 不可用: {}", base, e.getMessage());
            }
        }
        log.warn("未找到可用的 Flink leader，使用默认 URL");
        String fallback = appConfig.getFlinkRestUrl();
        if (!isAllowedBaseUrl(fallback)) {
            throw new IllegalStateException("Flink REST URL 配置无效: " + fallback);
        }
        return fallback.trim();
    }

    public String getActiveLeaderUrl() {
        return getLeaderUrl();
    }

    // -------------------------------------------------------------------------
    // API methods — all use buildUri() instead of string concatenation
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getJobs() {
        try {
            URI uri = buildUri(getLeaderUrl(), "jobs", "overview");
            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode jobs = root.get("jobs");
                if (jobs != null && jobs.isArray()) {
                    List<Map<String, Object>> result = new ArrayList<>();
                    for (JsonNode job : jobs) {
                        result.add(objectMapper.convertValue(job, Map.class));
                    }
                    return sanitizeList(result);
                }
            }
        } catch (Exception e) {
            log.error("获取作业列表失败", e);
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getJobDetail(String jobId) {
        try {
            URI uri = buildUri(getLeaderUrl(), "jobs", jobId);
            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return sanitize(objectMapper.readValue(response.getBody(), Map.class));
            }
        } catch (Exception e) {
            log.error("获取作业详情失败: {}", jobId, e);
        }
        return Collections.emptyMap();
    }

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
     * 取消作业（使用 RestTemplate + PATCH via exchange，避免反射 hack）
     */
    public void cancelJob(String jobId) {
        URI uri = buildUri(getLeaderUrl(), "jobs", jobId);
        // Flink cancel endpoint accepts PATCH
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>("{\"mode\":\"cancel\"}", headers);
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.PATCH, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("取消作业失败: HTTP " + response.getStatusCode());
            }
            log.info("作业已取消: {}", jobId);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("取消作业失败: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getClusterOverview() {
        try {
            URI uri = buildUri(getLeaderUrl(), "overview");
            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return sanitize(objectMapper.readValue(response.getBody(), Map.class));
            }
        } catch (Exception e) {
            log.error("获取集群概览失败", e);
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTaskManagers() {
        try {
            URI uri = buildUri(getLeaderUrl(), "taskmanagers");
            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode taskmanagers = root.get("taskmanagers");
                if (taskmanagers != null && taskmanagers.isArray()) {
                    List<Map<String, Object>> result = new ArrayList<>();
                    for (JsonNode tm : taskmanagers) {
                        result.add(objectMapper.convertValue(tm, Map.class));
                    }
                    return sanitizeList(result);
                }
            }
        } catch (Exception e) {
            log.error("获取 TaskManager 列表失败", e);
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getJobManagerConfig() {
        try {
            URI uri = buildUri(getLeaderUrl(), "jobmanager", "config");
            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> raw = objectMapper.readValue(response.getBody(), List.class);
                return sanitizeList(raw);
            }
        } catch (Exception e) {
            log.error("获取 JobManager 配置失败", e);
        }
        return Collections.emptyList();
    }

    public List<Map<String, Object>> getRunningJobs() {
        List<Map<String, Object>> runningJobs = new ArrayList<>();
        for (Map<String, Object> job : getJobs()) {
            if ("RUNNING".equals(job.get("state"))) {
                runningJobs.add(job);
            }
        }
        return runningJobs;
    }

    public boolean isFlinkHealthy() {
        try {
            URI uri = buildUri(getLeaderUrl(), "overview");
            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Flink 连接检查失败: {}", e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> triggerSavepoint(String jobId, String targetDirectory) {
        URI uri = buildUri(getLeaderUrl(), "jobs", jobId, "savepoints");
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("target-directory", targetDirectory);
            requestBody.put("cancel-job", false);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(uri, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
                String requestId = (String) result.get("request-id");
                if (requestId != null) {
                    return sanitize(waitForSavepoint(jobId, requestId));
                }
            }
            throw new RuntimeException("触发 savepoint 失败: HTTP " + response.getStatusCode());
        } catch (Exception e) {
            log.error("触发 savepoint 失败: jobId={}", jobId, e);
            throw new RuntimeException("触发 savepoint 失败: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> stopJobWithSavepoint(String jobId, String targetDirectory) {
        URI uri = buildUri(getLeaderUrl(), "jobs", jobId, "stop");
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("targetDirectory", targetDirectory);
            requestBody.put("drain", false);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(uri, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
                String requestId = (String) result.get("request-id");
                if (requestId != null) {
                    Map<String, Object> savepointResult = waitForSavepoint(jobId, requestId);
                    result.putAll(savepointResult);
                }
                log.info("作业 {} 已停止，Savepoint: {}", jobId, result.get("location"));
                return sanitize(result);
            }
            throw new RuntimeException("停止作业失败: HTTP " + response.getStatusCode());
        } catch (Exception e) {
            log.error("停止作业失败: {}", jobId, e);
            throw new RuntimeException("停止作业失败: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> waitForSavepoint(String jobId, String requestId) {
        URI uri = buildUri(getLeaderUrl(), "jobs", jobId, "savepoints", requestId);
        int maxRetries = 60;
        for (int i = 0; i < maxRetries; i++) {
            try {
                Thread.sleep(1000);
                ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
                    Map<String, Object> status = (Map<String, Object>) result.get("status");
                    if (status != null) {
                        String statusId = (String) status.get("id");
                        if ("COMPLETED".equals(statusId)) {
                            Map<String, Object> operation = (Map<String, Object>) result.get("operation");
                            return operation != null ? operation : result;
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

    @SuppressWarnings("unchecked")
    public Map<String, Object> getCheckpoints(String jobId) {
        try {
            URI uri = buildUri(getLeaderUrl(), "jobs", jobId, "checkpoints");
            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return sanitize(objectMapper.readValue(response.getBody(), Map.class));
            }
        } catch (Exception e) {
            log.error("获取 Checkpoint 信息失败: {}", jobId, e);
        }
        return Collections.emptyMap();
    }
}
