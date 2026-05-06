package com.realtime.monitor.service;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtime.monitor.config.AppConfig;
import com.realtime.monitor.dto.CdcSubmitRequest;
import com.realtime.monitor.dto.DataSourceConfig;
import com.realtime.monitor.dto.TaskConfig;
import com.realtime.monitor.util.PathSecurityValidator;
import com.realtime.monitor.util.XssSanitizer;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 嵌入式 CDC 服务
 *
 * 通过 Flink REST API 提交 CDC 作业。
 * 首次使用时将本地 JAR 上传到 Flink 集群（/jars/upload），
 * 后续复用已上传的 JAR ID。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddedCdcService {

    private static final String CDC_MAIN_CLASS = "com.realtime.pipeline.CdcJobMain";
    private static final Pattern SAFE_PATH_SEGMENT = Pattern.compile("^[a-zA-Z0-9._-]{1,200}$");

    /** 允许的输出路径前缀白名单 */
    private static final List<String> ALLOWED_OUTPUT_PREFIXES = List.of(
            "./output/", "/opt/flink/output/", "output/"
    );

    /** 允许的 savepoint/checkpoint 路径前缀白名单 */
    private static final List<String> ALLOWED_STATE_PREFIXES = List.of(
            "file:///opt/flink/savepoints", "file:///opt/flink/checkpoints",
            "/opt/flink/savepoints", "/opt/flink/checkpoints",
            "hdfs://", "s3://"
    );

    /** 恶意路径字符模式 */
    private static final Pattern MALICIOUS_PATH_CHARS = Pattern.compile(
            "[\\x00-\\x1f`$|;&!><]|(\\.\\./)|(\\.\\.\\.)"
    );

    /** flink-jobs JAR 路径，可通过配置覆盖（本地开发 vs 容器部署） */
    @org.springframework.beans.factory.annotation.Value("${flink.job.jar-path:/opt/flink/usrlib/flink-jobs-1.0.0-SNAPSHOT.jar}")
    private String localJarPath;

    private final AppConfig appConfig;
    private final DataSourceService dataSourceService;
    private final FlinkService flinkService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestTemplate restTemplate;

    /** 缓存已上传的 JAR ID，避免重复上传 */
    private volatile String cachedJarId;

    @PostConstruct
    public void init() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        restTemplate = new RestTemplate(factory);
    }

    /**
     * 提交 CDC 任务到 Flink 集群（通过 REST API）
     */
    public Map<String, Object> submitTask(CdcSubmitRequest request) throws Exception {
        log.info("通过 Flink REST API 提交 CDC 作业");
        log.info("  数据源: {}:{}/{}", request.getHostname(), request.getPort(), request.getDatabase());
        log.info("  Schema: {}, 表: {}", request.getSchema(), request.getTables());

        try {
            // 1. 获取 JAR ID（先查已上传的，没有则上传）
            String jarId = requireSafePathSegment(getOrUploadJar(), "jarId");

            // 2. 构建 programArgs
            List<String> programArgs = new ArrayList<>();
            programArgs.add("--hostname"); programArgs.add(request.getHostname());
            programArgs.add("--port"); programArgs.add(String.valueOf(request.getPort()));
            programArgs.add("--username"); programArgs.add(request.getUsername());
            programArgs.add("--password"); programArgs.add(request.getPassword());
            programArgs.add("--database"); programArgs.add(request.getDatabase());
            programArgs.add("--schema"); programArgs.add(request.getSchema());
            programArgs.add("--tables"); programArgs.add(String.join(",", request.getTables()));
            programArgs.add("--outputPath"); programArgs.add(
                    validateOutputPath(request.getOutputPath() != null ? request.getOutputPath() : appConfig.getFlinkOutputPath()));
            programArgs.add("--parallelism"); programArgs.add(String.valueOf(
                    request.getParallelism() > 0 ? request.getParallelism() : 2));
            programArgs.add("--splitSize"); programArgs.add(String.valueOf(
                    request.getSplitSize() > 0 ? request.getSplitSize() : 8096));
            
            // Checkpoint/Savepoint 目录（本地 vs Docker 路径不同）
            programArgs.add("--checkpointDir"); programArgs.add(appConfig.getCheckpointDir());
            programArgs.add("--savepointDir"); programArgs.add(appConfig.getSavepointDir());

            // 添加作业名称参数
            if (request.getJobName() != null && !request.getJobName().isEmpty()) {
                programArgs.add("--jobName");
                programArgs.add(request.getJobName());
            }

            // 3. 通过 REST API 提交作业
            // 使用 UriComponentsBuilder 防止 URL 注入和目录遍历
            URI uri = buildFlinkUri("jars", jarId, "run");

            Map<String, Object> body = new HashMap<>();
            body.put("entryClass", CDC_MAIN_CLASS);
            body.put("programArgsList", programArgs);
            body.put("parallelism", request.getParallelism() > 0 ? request.getParallelism() : 2);
            
            // 设置作业名称
            if (request.getJobName() != null && !request.getJobName().isEmpty()) {
                body.put("jobName", request.getJobName());
                log.info("  作业名称: {}", request.getJobName());
            }
            // 从 savepoint 恢复
            if (request.getSavepointPath() != null && !request.getSavepointPath().isEmpty()) {
                String safeSavepointPath = validateStatePath(request.getSavepointPath());
                body.put("savepointPath", safeSavepointPath);
                body.put("allowNonRestoredState", true);
                log.info("  从 savepoint 恢复: {}", safeSavepointPath);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);

            log.info("提交到: {}", uri);
            ResponseEntity<String> response = restTemplate.postForEntity(uri, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode result = objectMapper.readTree(response.getBody());
                String jobId = result.has("jobid") ? result.get("jobid").asText() : "unknown";

                log.info("CDC 作业已提交: {}", jobId);

                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("success", true);
                resultMap.put("job_id", jobId);
                resultMap.put("message", "任务已提交到 Flink 集群");
                resultMap.put("config", Map.of(
                        "hostname", request.getHostname(),
                        "database", request.getDatabase(),
                        "schema", request.getSchema(),
                        "tables", request.getTables()));
                return resultMap;
            } else {
                String responseBody = response.getBody();
                if (responseBody != null && !responseBody.isBlank()) {
                    log.debug("Flink REST API 提交返回: {}", XssSanitizer.sanitizeString(limitForLog(responseBody)));
                }
                throw new RuntimeException("Flink REST API 返回错误: HTTP " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("提交 CDC 作业失败", e);
            throw e;
        }
    }

    /**
     * 提交任务（使用任务配置）
     */
    public Map<String, Object> submitTask(TaskConfig taskConfig) throws Exception {
        DataSourceConfig dsConfig = null;
        if (taskConfig.getDatasourceId() != null) {
            dsConfig = dataSourceService.loadDataSource(taskConfig.getDatasourceId());
        }

        CdcSubmitRequest request = new CdcSubmitRequest();
        if (dsConfig != null) {
            request.setHostname(dsConfig.getHost());
            request.setPort(dsConfig.getPort());
            request.setUsername(dsConfig.getUsername());
            request.setPassword(dsConfig.getPassword());
            request.setDatabase(dsConfig.getSid());
        } else if (taskConfig.getDatabase() != null) {
            request.setHostname(taskConfig.getDatabase().getHost());
            request.setPort(taskConfig.getDatabase().getPort());
            request.setUsername(taskConfig.getDatabase().getUsername());
            request.setPassword(taskConfig.getDatabase().getPassword());
            request.setDatabase(taskConfig.getDatabase().getSid());
        } else {
            throw new IllegalArgumentException("任务配置缺少数据库连接信息");
        }

        request.setSchema(taskConfig.getSchema());
        request.setTables(taskConfig.getTables());
        request.setOutputPath(taskConfig.getOutputPath());
        request.setParallelism(taskConfig.getParallelism());
        request.setSplitSize(taskConfig.getSplitSize());
        
        // 设置作业名称
        if (taskConfig.getName() != null && !taskConfig.getName().isEmpty()) {
            request.setJobName(taskConfig.getName());
        }
        // 传递 savepoint 路径（恢复时使用）
        if (taskConfig.getSavepointPath() != null && !taskConfig.getSavepointPath().isEmpty()) {
            request.setSavepointPath(taskConfig.getSavepointPath());
        }

        return submitTask(request);
    }

    // ============================================
    // Flink REST API - JAR 管理
    // ============================================
    private URI buildFlinkUri(String... pathSegments) {
        URI baseUri = URI.create(flinkService.getActiveLeaderUrl());
        String scheme = baseUri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("无效的 Flink REST URL scheme: " + scheme);
        }
        if (baseUri.getHost() == null || baseUri.getHost().isEmpty()) {
            throw new IllegalArgumentException("无效的 Flink REST URL host");
        }
        if (baseUri.getRawFragment() != null) {
            throw new IllegalArgumentException("无效的 Flink REST URL fragment");
        }
        String[] safeSegments = new String[pathSegments.length];
        for (int i = 0; i < pathSegments.length; i++) {
            safeSegments[i] = requireSafePathSegment(pathSegments[i], "pathSegment[" + i + "]");
        }
        return UriComponentsBuilder.fromUri(baseUri).pathSegment(safeSegments).build().toUri();
    }

    private String requireSafePathSegment(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("无效的路径参数: " + name);
        }
        if (!SAFE_PATH_SEGMENT.matcher(value).matches()) {
            throw new IllegalArgumentException("不安全的路径参数: " + name);
        }
        return value;
    }

    /**
     * 验证输出路径安全性：
     * - 必须在允许的前缀白名单内
     * - 不能包含路径遍历字符（../ 等）
     * - 不能包含恶意符号
     */
    private String validateOutputPath(String outputPath) {
        if (outputPath == null || outputPath.isBlank()) {
            return appConfig.getFlinkOutputPath(); // 使用默认安全路径
        }

        // 检测恶意字符
        if (MALICIOUS_PATH_CHARS.matcher(outputPath).find()) {
            log.warn("输出路径包含恶意字符，使用默认路径: {}", 
                    outputPath.length() > 50 ? outputPath.substring(0, 50) + "..." : outputPath);
            throw new SecurityException("输出路径包含非法字符");
        }

        // 检测路径遍历
        if (outputPath.contains("..")) {
            log.warn("输出路径包含路径遍历字符: {}", outputPath);
            throw new SecurityException("输出路径不允许包含 '..'");
        }

        // 白名单前缀检查
        String normalized = outputPath.replace("\\", "/");
        boolean allowed = ALLOWED_OUTPUT_PREFIXES.stream()
                .anyMatch(prefix -> normalized.startsWith(prefix));
        if (!allowed) {
            log.warn("输出路径不在白名单内: {}", outputPath);
            throw new SecurityException("输出路径不在允许的目录范围内");
        }

        return outputPath;
    }

    /**
     * 验证 savepoint/checkpoint 路径安全性
     */
    private String validateStatePath(String statePath) {
        if (statePath == null || statePath.isBlank()) {
            return null;
        }

        // 检测恶意字符
        if (MALICIOUS_PATH_CHARS.matcher(statePath).find()) {
            log.warn("状态路径包含恶意字符: {}", 
                    statePath.length() > 50 ? statePath.substring(0, 50) + "..." : statePath);
            throw new SecurityException("Savepoint 路径包含非法字符");
        }

        // 检测路径遍历
        if (statePath.contains("..")) {
            throw new SecurityException("Savepoint 路径不允许包含 '..'");
        }

        // 白名单前缀检查
        boolean allowed = ALLOWED_STATE_PREFIXES.stream()
                .anyMatch(prefix -> statePath.startsWith(prefix));
        if (!allowed) {
            log.warn("状态路径不在白名单内: {}", statePath);
            throw new SecurityException("Savepoint 路径不在允许的目录范围内");
        }

        return statePath;
    }

    /**
     * 获取或上传 JAR：先查已上传的，没有则上传本地 JAR
     */
    private String getOrUploadJar() throws Exception {
        // 1. 使用缓存
        if (cachedJarId != null) {
            // 验证缓存的 JAR 是否仍然有效
            if (isJarValid(cachedJarId)) {
                log.info("使用缓存的 JAR ID: {}", cachedJarId);
                return cachedJarId;
            }
            cachedJarId = null;
        }

        // 2. 查找已上传的 JAR
        String jarId = findJarId();
        if (jarId != null) {
            cachedJarId = jarId;
            log.info("找到已上传的 JAR: {}", jarId);
            return jarId;
        }

        // 3. 上传本地 JAR
        log.info("Flink 集群上未找到 JAR，开始上传...");
        jarId = uploadJar();
        cachedJarId = jarId;
        return jarId;
    }

    /**
     * 上传本地 JAR 到 Flink 集群（流式上传，避免 OOM）
     */
    private String uploadJar() throws Exception {
        File jarFile = new File(localJarPath);
        if (!jarFile.exists()) {
            throw new RuntimeException("本地 JAR 文件不存在: " + localJarPath);
        }

        String url = UriComponentsBuilder.fromHttpUrl(flinkService.getActiveLeaderUrl())
                .pathSegment("jars", "upload")
                .build()
                .toUriString();
        log.info("上传 JAR 到 Flink: {} ({}MB)", url, jarFile.length() / 1024 / 1024);

        // 使用 HttpURLConnection 流式上传，避免 RestTemplate 将整个文件加载到内存
        String boundary = "----FlinkJarUpload" + System.currentTimeMillis();
        java.net.URL uploadUrl = new java.net.URL(url);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) uploadUrl.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setChunkedStreamingMode(8192);

        try (java.io.OutputStream out = conn.getOutputStream()) {
            // multipart header
            String header = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"jarfile\"; filename=\"" + jarFile.getName() + "\"\r\n"
                    + "Content-Type: application/java-archive\r\n\r\n";
            out.write(header.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // stream file content
            try (java.io.FileInputStream fis = new java.io.FileInputStream(jarFile)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = fis.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
            }

            // multipart footer
            String footer = "\r\n--" + boundary + "--\r\n";
            out.write(footer.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.flush();
        }

        int responseCode = conn.getResponseCode();
        String responseBody;
        try (java.io.InputStream is = (responseCode >= 200 && responseCode < 300)
                ? conn.getInputStream() : conn.getErrorStream()) {
            responseBody = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }

        if (responseCode >= 200 && responseCode < 300) {
            JsonNode result = objectMapper.readTree(responseBody);
            String filename = result.has("filename") ? result.get("filename").asText() : "";
            String jarId = filename.substring(filename.lastIndexOf("/") + 1);
            log.info("JAR 上传成功: {}", jarId);
            return jarId;
        } else {
            if (responseBody != null && !responseBody.isBlank()) {
                log.debug("JAR 上传返回: {}", XssSanitizer.sanitizeString(limitForLog(responseBody)));
            }
            throw new RuntimeException("JAR 上传失败: HTTP " + responseCode);
        }
    }

    private String limitForLog(String value) {
        if (value == null) return null;
        String v = value.trim();
        return v.length() > 1000 ? v.substring(0, 1000) : v;
    }

    /**
     * 查找集群上已上传的 JAR ID
     */
    private String findJarId() {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(flinkService.getActiveLeaderUrl())
                    .pathSegment("jars")
                    .build()
                    .toUriString();
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode files = root.get("files");
                if (files != null && files.isArray()) {
                    for (JsonNode file : files) {
                        String name = file.has("name") ? file.get("name").asText() : "";
                        // 匹配 flink-jobs JAR 文件名
                        if (name.contains("flink-jobs") || name.contains("realtime-data-pipeline")) {
                            return file.get("id").asText();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查询 JAR 列表失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 验证 JAR ID 是否仍然有效
     */
    private boolean isJarValid(String jarId) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(flinkService.getActiveLeaderUrl())
                    .pathSegment("jars")
                    .build()
                    .toUriString();
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode files = root.get("files");
                if (files != null && files.isArray()) {
                    for (JsonNode file : files) {
                        if (jarId.equals(file.get("id").asText())) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("验证 JAR 有效性失败: {}", e.getMessage());
        }
        return false;
    }

    // ============================================
    // 作业管理
    // ============================================

    public Map<String, Object> cancelJob(String jobId) {
        try {
            flinkService.cancelJob(jobId);
            return Map.of("success", true, "message", "作业已取消");
        } catch (Exception e) {
            log.error("取消作业失败: {}", jobId, e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    public List<Map<String, Object>> listJobs() {
        try {
            return flinkService.getRunningJobs();
        } catch (Exception e) {
            log.error("获取作业列表失败", e);
            return Collections.emptyList();
        }
    }

    public Map<String, Object> getJobDetail(String jobId) {
        try {
            Map<String, Object> result = flinkService.getJobDetail(jobId);
            return result != null ? result : Collections.emptyMap();
        } catch (Exception e) {
            log.error("获取作业详情失败: {}", jobId, e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }
}
