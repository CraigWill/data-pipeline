package com.realtime.monitor.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.owasp.esapi.ESAPI;
import org.owasp.esapi.Encoder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtime.monitor.config.AppConfig;
import static com.realtime.monitor.util.XssSanitizer.sanitize;
import static com.realtime.monitor.util.XssSanitizer.sanitizeList;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

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
    /** 允许的最大响应体大小 (10 MB) */
    private static final int MAX_RESPONSE_BODY_SIZE = 10 * 1024 * 1024;
    
    @PostConstruct
    public void init() {
        // 使用 HttpComponentsClientHttpRequestFactory 替代 SimpleClientHttpRequestFactory
        // 因为 HttpURLConnection 不支持 PATCH 方法，而 Flink cancel API 需要 PATCH
        org.springframework.http.client.HttpComponentsClientHttpRequestFactory factory =
                new org.springframework.http.client.HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        restTemplate = new RestTemplate(factory);
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
    // Response validation — treat all external responses as untrusted
    // -------------------------------------------------------------------------

    /**
     * Regex patterns for detecting script injection in response bodies.
     * These catch common XSS/script injection vectors that could be embedded
     * in JSON string values from a compromised upstream service.
     */
    private static final java.util.regex.Pattern[] SCRIPT_INJECTION_PATTERNS = {
        // <script>...</script> tags (with optional attributes)
        java.util.regex.Pattern.compile("<\\s*script[^>]*>", java.util.regex.Pattern.CASE_INSENSITIVE),
        java.util.regex.Pattern.compile("</\\s*script\\s*>", java.util.regex.Pattern.CASE_INSENSITIVE),
        // javascript: protocol in any context
        java.util.regex.Pattern.compile("javascript\\s*:", java.util.regex.Pattern.CASE_INSENSITIVE),
        // vbscript: protocol
        java.util.regex.Pattern.compile("vbscript\\s*:", java.util.regex.Pattern.CASE_INSENSITIVE),
        // data: URI with script content types
        java.util.regex.Pattern.compile("data\\s*:[^,]*text/html", java.util.regex.Pattern.CASE_INSENSITIVE),
        // Event handler attributes (onclick, onerror, onload, onmouseover, etc.)
        java.util.regex.Pattern.compile("\\bon\\w+\\s*=", java.util.regex.Pattern.CASE_INSENSITIVE),
        // <iframe>, <object>, <embed>, <applet>, <form> tags
        java.util.regex.Pattern.compile("<\\s*(iframe|object|embed|applet|form)[^>]*>", java.util.regex.Pattern.CASE_INSENSITIVE),
        // expression() CSS injection
        java.util.regex.Pattern.compile("expression\\s*\\(", java.util.regex.Pattern.CASE_INSENSITIVE),
        // eval(), Function(), setTimeout/setInterval with string arg
        java.util.regex.Pattern.compile("\\b(eval|Function)\\s*\\(", java.util.regex.Pattern.CASE_INSENSITIVE),
        // <img src=x onerror=...> pattern
        java.util.regex.Pattern.compile("<\\s*img[^>]+onerror\\s*=", java.util.regex.Pattern.CASE_INSENSITIVE),
        // SVG onload
        java.util.regex.Pattern.compile("<\\s*svg[^>]+onload\\s*=", java.util.regex.Pattern.CASE_INSENSITIVE),
    };

    /**
     * Validate and extract the body from a RestTemplate response.
     * Guards against:
     * - Non-2xx status codes (treated as failures)
     * - Null or empty bodies
     * - Oversized response bodies (potential DoS / memory exhaustion)
     * - Non-JSON content types when JSON is expected
     * - Script injection / XSS payloads embedded in response content
     *
     * @param response the ResponseEntity from restTemplate
     * @param context  description for logging (e.g. "getJobs")
     * @return the validated and sanitized response body string, or null if invalid
     */
    private String validateResponse(ResponseEntity<String> response, String context) {
        if (response == null) {
            log.warn("[{}] 收到 null 响应", context);
            return null;
        }

        HttpStatus status = (HttpStatus) response.getStatusCode();
        if (!status.is2xxSuccessful()) {
            log.warn("[{}] 非成功状态码: {}", context, status.value());
            return null;
        }

        String body = response.getBody();
        if (body == null || body.isBlank()) {
            log.debug("[{}] 响应体为空", context);
            return null;
        }

        // Guard against oversized responses that could exhaust memory
        if (body.length() > MAX_RESPONSE_BODY_SIZE) {
            log.error("[{}] 响应体过大 ({} bytes)，超过限制 ({} bytes)，丢弃",
                    context, body.length(), MAX_RESPONSE_BODY_SIZE);
            return null;
        }

        // Validate content-type header if present — expect JSON from Flink REST API
        MediaType contentType = response.getHeaders().getContentType();
        if (contentType != null
                && !contentType.isCompatibleWith(MediaType.APPLICATION_JSON)
                && !contentType.isCompatibleWith(MediaType.TEXT_PLAIN)) {
            log.warn("[{}] 意外的 Content-Type: {}，期望 JSON", context, contentType);
            return null;
        }

        // Basic structural check: Flink REST API always returns JSON objects or arrays
        String trimmed = body.trim();
        char firstChar = trimmed.charAt(0);
        if (firstChar != '{' && firstChar != '[') {
            log.warn("[{}] 响应体不是有效的 JSON 结构 (首字符: '{}')", context, firstChar);
            return null;
        }

        // Script injection detection and neutralization
        body = neutralizeScriptInjection(body, context);

        return body;
    }

    /**
     * Detect and neutralize script injection patterns in the response body.
     * If dangerous patterns are found, they are stripped/escaped to prevent
     * XSS when the data is eventually rendered in a browser context.
     *
     * This is a defense-in-depth measure — the primary defense is the OWASP
     * HTML encoding in XssSanitizer, but stripping at the source prevents
     * payloads from ever reaching downstream processing.
     */
    private String neutralizeScriptInjection(String body, String context) {
        boolean injectionDetected = false;

        for (java.util.regex.Pattern pattern : SCRIPT_INJECTION_PATTERNS) {
            if (pattern.matcher(body).find()) {
                if (!injectionDetected) {
                    log.warn("[{}] 检测到潜在脚本注入内容，执行净化处理", context);
                    injectionDetected = true;
                }
                // Replace the dangerous pattern with a safe placeholder
                body = pattern.matcher(body).replaceAll("[SANITIZED]");
            }
        }

        // Additional: strip null bytes which can be used to bypass filters
        if (body.indexOf('\u0000') >= 0) {
            log.warn("[{}] 检测到 null 字节，已移除", context);
            body = body.replace("\u0000", "");
        }

        // Strip Unicode direction override characters (used in bidi attacks)
        body = body.replaceAll("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]", "");

        return body;
    }

    /**
     * Safe wrapper around restTemplate.getForEntity that:
     * 1. Encodes the URL using OWASP ESAPI to prevent URL injection
     * 2. Validates the response body for script injection
     * 3. Sets security headers (HttpOnly, X-Content-Type-Options, etc.)
     *    on the current HTTP response if available
     *
     * @param uri     the target URI
     * @param context description for logging
     * @return validated response body, or null if the response is invalid/untrusted
     */
    private String safeGet(URI uri, String context) {
        // Step 1: Encode URL components using OWASP ESAPI to prevent injection
        URI safeUri = encodeUriWithEsapi(uri, context);

        // Step 2: Execute the request
        ResponseEntity<String> response = restTemplate.getForEntity(safeUri, String.class);

        // Step 3: Set security headers on the outgoing HTTP response
        setSecurityResponseHeaders();

        // Step 4: Validate the response body
        return validateResponse(response, context);
    }

    /**
     * Encode URI using OWASP ESAPI to prevent URL-based injection attacks.
     * Validates and sanitizes each component of the URI.
     */
    private URI encodeUriWithEsapi(URI uri, String context) {
        try {
            Encoder encoder = ESAPI.encoder();

            // Validate the scheme — only allow http/https
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new SecurityException("不允许的 URI scheme: " + scheme);
            }

            // Encode the path segments using ESAPI to neutralize injection characters
            String path = uri.getRawPath();
            if (path != null && !path.isEmpty()) {
                // Split path, encode each segment individually, rejoin
                String[] segments = path.split("/", -1);
                StringBuilder safePath = new StringBuilder();
                for (String segment : segments) {
                    if (!segment.isEmpty()) {
                        // ESAPI encodeForURL encodes special characters that could be used for injection
                        String encoded = encoder.encodeForURL(segment);
                        safePath.append("/").append(encoded);
                    } else {
                        safePath.append("/");
                    }
                }
                // Rebuild URI with encoded path
                return UriComponentsBuilder.newInstance()
                        .scheme(scheme)
                        .host(uri.getHost())
                        .port(uri.getPort())
                        .path(safePath.toString())
                        .query(uri.getRawQuery() != null ? encoder.encodeForURL(uri.getRawQuery()) : null)
                        .build(true)
                        .toUri();
            }

            return uri;
        } catch (SecurityException se) {
            throw se;
        } catch (Exception e) {
            log.warn("[{}] ESAPI URL 编码失败，使用原始 URI: {}", context, e.getMessage());
            return uri;
        }
    }

    /**
     * Set security headers on the current HTTP response to prevent
     * script injection and cookie theft when the response reaches the browser.
     *
     * Headers set:
     * - X-Content-Type-Options: nosniff (prevent MIME-type sniffing)
     * - X-XSS-Protection: 1; mode=block (legacy XSS filter)
     * - Cache-Control: no-store (prevent caching of sensitive data)
     * - Set-Cookie attributes: HttpOnly; Secure; SameSite=Strict
     */
    private void setSecurityResponseHeaders() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return; // Not in a web request context (e.g., async task)
            }
            HttpServletResponse httpResponse = attrs.getResponse();
            if (httpResponse == null) {
                return;
            }

            // Prevent MIME-type sniffing — stops browsers from interpreting
            // JSON responses as HTML/script
            httpResponse.setHeader("X-Content-Type-Options", "nosniff");

            // Legacy XSS protection header (still useful for older browsers)
            httpResponse.setHeader("X-XSS-Protection", "1; mode=block");

            // Prevent caching of potentially sensitive Flink cluster data
            httpResponse.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            httpResponse.setHeader("Pragma", "no-cache");

            // Content-Security-Policy to block inline scripts
            // Only set if not already set by a filter
            if (httpResponse.getHeader("Content-Security-Policy") == null) {
                httpResponse.setHeader("Content-Security-Policy",
                        "default-src 'self'; script-src 'self'; object-src 'none'");
            }

            // Ensure any cookies set in this response have HttpOnly + Secure + SameSite
            // This is done via Set-Cookie header manipulation
            String existingCookie = httpResponse.getHeader("Set-Cookie");
            if (existingCookie != null && !existingCookie.contains("HttpOnly")) {
                httpResponse.setHeader("Set-Cookie",
                        existingCookie + "; HttpOnly; Secure; SameSite=Strict");
            }

        } catch (Exception e) {
            log.debug("设置安全响应头失败（非 Web 上下文）: {}", e.getMessage());
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
                String body = safeGet(testUri, "leaderDiscovery");
                if (body != null) {
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
            String body = safeGet(uri, "getJobs");
            if (body != null) {
                JsonNode root = objectMapper.readTree(body);
                JsonNode jobs = root.get("jobs");
                if (jobs != null && jobs.isArray()) {
                    List<Map<String, Object>> result = new ArrayList<>();
                    for (JsonNode job : jobs) {
                        result.add(objectMapper.convertValue(job, Map.class));
                    }
                    return sanitizeList(result);
                }
            }
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.debug("Flink 不可用，获取作业列表跳过: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("获取作业列表失败: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getJobDetail(String jobId) {
        try {
            URI uri = buildUri(getLeaderUrl(), "jobs", jobId);
            String body = safeGet(uri, "getJobDetail");
            if (body != null) {
                return sanitize(objectMapper.readValue(body, Map.class));
            }
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            // 404 — job no longer exists on Flink, confirmed not found
            log.debug("作业不存在于 Flink 集群: {}", jobId);
            return Collections.emptyMap();
        } catch (org.springframework.web.client.ResourceAccessException e) {
            // 连接失败 — Flink 不可用，无法确认作业状态
            log.debug("Flink 不可用，无法查询作业 {}: {}", jobId, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("获取作业详情失败: {} — {}", jobId, e.getMessage());
            return null;
        }
        return Collections.emptyMap();
    }

    public Map<String, Object> getJobMetrics(String jobId) {
        Map<String, Object> jobData = getJobDetail(jobId);
        if (jobData == null) {
            jobData = Collections.emptyMap();
        }
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
        return sanitize(metrics);
    }

    /**
     * 取消作业（使用 RestTemplate + PATCH via exchange，避免反射 hack）
     */
    public void cancelJob(String jobId) {
        // Flink REST API: PATCH /jobs/:jobid 用于取消作业
        try {
            URI uri = buildUri(getLeaderUrl(), "jobs", jobId);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>("{}", headers);
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
            String body = safeGet(uri, "getClusterOverview");
            if (body != null) {
                return sanitize(objectMapper.readValue(body, Map.class));
            }
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.debug("Flink 不可用，获取集群概览跳过: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("获取集群概览失败: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTaskManagers() {
        try {
            URI uri = buildUri(getLeaderUrl(), "taskmanagers");
            String body = safeGet(uri, "getTaskManagers");
            if (body != null) {
                JsonNode root = objectMapper.readTree(body);
                JsonNode taskmanagers = root.get("taskmanagers");
                if (taskmanagers != null && taskmanagers.isArray()) {
                    List<Map<String, Object>> result = new ArrayList<>();
                    for (JsonNode tm : taskmanagers) {
                        result.add(objectMapper.convertValue(tm, Map.class));
                    }
                    return sanitizeList(result);
                }
            }
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.debug("Flink 不可用，获取 TaskManager 列表跳过: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("获取 TaskManager 列表失败: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getJobManagerConfig() {
        try {
            URI uri = buildUri(getLeaderUrl(), "jobmanager", "config");
            String body = safeGet(uri, "getJobManagerConfig");
            if (body != null) {
                List<Map<String, Object>> raw = objectMapper.readValue(body, List.class);
                return sanitizeList(raw);
            }
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.debug("Flink 不可用，获取 JobManager 配置跳过: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("获取 JobManager 配置失败: {}", e.getMessage());
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
            String body = safeGet(uri, "isFlinkHealthy");
            return body != null;
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
                String body = safeGet(uri, "waitForSavepoint");
                if (body != null) {
                    Map<String, Object> result = objectMapper.readValue(body, Map.class);
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
            String body = safeGet(uri, "getCheckpoints");
            if (body != null) {
                return sanitize(objectMapper.readValue(body, Map.class));
            }
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.debug("Flink 不可用，获取 Checkpoint 信息跳过: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("获取 Checkpoint 信息失败: {} — {}", jobId, e.getMessage());
        }
        return Collections.emptyMap();
    }
}
