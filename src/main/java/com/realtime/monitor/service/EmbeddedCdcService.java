package com.realtime.monitor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtime.monitor.config.AppConfig;
import com.realtime.monitor.dto.CdcSubmitRequest;
import com.realtime.monitor.dto.DataSourceConfig;
import com.realtime.monitor.dto.TaskConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.*;

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
    /** monitor-backend 容器内的 JAR 路径 */
    private static final String LOCAL_JAR_PATH = "/app/app.jar";

    private final AppConfig appConfig;
    private final DataSourceService dataSourceService;
    private final FlinkService flinkService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestTemplate restTemplate;

    /** 缓存已上传的 JAR ID，避免重复上传 */
    private volatile String cachedJarId;

    @PostConstruct
    public void init() {
        restTemplate = new RestTemplate();
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
            String jarId = getOrUploadJar();

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
                    request.getOutputPath() != null ? request.getOutputPath() : appConfig.getFlinkOutputPath());
            programArgs.add("--parallelism"); programArgs.add(String.valueOf(
                    request.getParallelism() > 0 ? request.getParallelism() : 2));
            programArgs.add("--splitSize"); programArgs.add(String.valueOf(
                    request.getSplitSize() > 0 ? request.getSplitSize() : 8096));
            
            // 添加作业名称参数
            if (request.getJobName() != null && !request.getJobName().isEmpty()) {
                programArgs.add("--jobName");
                programArgs.add(request.getJobName());
            }

            // 3. 通过 REST API 提交作业
            String url = flinkService.getActiveLeaderUrl() + "/jars/" + jarId + "/run";

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
                body.put("savepointPath", request.getSavepointPath());
                body.put("allowNonRestoredState", true);
                log.info("  从 savepoint 恢复: {}", request.getSavepointPath());
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);

            log.info("提交到: {}", url);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

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
                throw new RuntimeException("Flink REST API 返回错误: " + response.getStatusCode()
                        + " " + response.getBody());
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
        File jarFile = new File(LOCAL_JAR_PATH);
        if (!jarFile.exists()) {
            throw new RuntimeException("本地 JAR 文件不存在: " + LOCAL_JAR_PATH);
        }

        String url = flinkService.getActiveLeaderUrl() + "/jars/upload";
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
            throw new RuntimeException("JAR 上传失败: " + responseCode + " " + responseBody);
        }
    }

    /**
     * 查找集群上已上传的 JAR ID
     */
    private String findJarId() {
        try {
            String url = flinkService.getActiveLeaderUrl() + "/jars";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode files = root.get("files");
                if (files != null && files.isArray()) {
                    for (JsonNode file : files) {
                        String name = file.has("name") ? file.get("name").asText() : "";
                        if (name.contains("realtime-data-pipeline")) {
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
            String url = flinkService.getActiveLeaderUrl() + "/jars";
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
            return flinkService.getJobDetail(jobId);
        } catch (Exception e) {
            log.error("获取作业详情失败: {}", jobId, e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }
}
