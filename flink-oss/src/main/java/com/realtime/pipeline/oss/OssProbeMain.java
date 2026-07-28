package com.realtime.pipeline.oss;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.oss.model.BucketInfo;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.ListObjectsV2Request;
import com.aliyun.oss.model.ListObjectsV2Result;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;

/**
 * Flink 容器内 OSS 连通性探针。
 *
 * <p>凭据解析顺序（后者不覆盖前者已有值）：
 * <ol>
 *   <li>环境变量 {@code OSS_*}</li>
 *   <li>{@code CHECKPOINT_DIR}/{@code OUTPUT_PATH}/{@code SAVEPOINT_DIR} 的 {@code oss://bucket/prefix}</li>
 *   <li>{@code FLINK_CONF_DIR}/flink-conf.yaml 或常见路径中的 {@code fs.oss.*} / {@code state.checkpoints.dir}</li>
 * </ol>
 *
 * <pre>
 *   java -jar /opt/flink/usrlib/flink-oss-probe.jar
 *   java -jar flink-oss-probe.jar --max-keys 5
 * </pre>
 */
public final class OssProbeMain {

    private static final Pattern OSS_URI = Pattern.compile("^oss://([^/]+)(?:/(.*))?$");
    private static final Pattern YAML_KV = Pattern.compile("^\\s*([A-Za-z0-9._-]+)\\s*:\\s*(.*?)\\s*$");

    private OssProbeMain() {}

    public static void main(String[] args) {
        int maxKeys = 10;
        for (int i = 0; i < args.length; i++) {
            if ("--max-keys".equals(args[i]) && i + 1 < args.length) {
                maxKeys = Integer.parseInt(args[++i]);
            } else if ("--help".equals(args[i]) || "-h".equals(args[i])) {
                printHelp();
                return;
            }
        }

        Map<String, String> cfg = new LinkedHashMap<>();
        List<String> sources = new ArrayList<>();
        collectFromEnv(cfg, sources);
        collectFromUriEnv(cfg, sources);
        collectFromFlinkConf(cfg, sources);

        System.out.println("=== Flink OSS Probe ===");
        System.out.println("java.version = " + System.getProperty("java.version"));
        System.out.println("hostname     = " + hostname());
        System.out.println("signDefault  = " + SignVersion.V1 + " (aliyun-sdk-oss default)");
        System.out.println("configSources:");
        if (sources.isEmpty()) {
            System.out.println("  (none)");
        } else {
            for (String s : sources) {
                System.out.println("  - " + s);
            }
        }
        System.out.println();
        System.out.println("--- resolved OSS settings (secrets masked) ---");
        printResolved(cfg);
        System.out.println();

        String endpoint = firstNonBlank(cfg.get("endpoint"), cfg.get("fs.oss.endpoint"));
        String ak = firstNonBlank(cfg.get("accessKeyId"), cfg.get("fs.oss.accessKeyId"));
        String sk = firstNonBlank(cfg.get("accessKeySecret"), cfg.get("fs.oss.accessKeySecret"));
        String bucket = firstNonBlank(cfg.get("bucketName"), cfg.get("bucket"));
        String prefix = nullToEmpty(firstNonBlank(cfg.get("prefix"), ""));

        if (isBlank(endpoint) || isBlank(ak) || isBlank(sk) || isBlank(bucket)) {
            System.out.println("FATAL: missing required fields: endpoint / accessKeyId / accessKeySecret / bucketName");
            System.out.println("Hint: export OSS_* or ensure Flink conf has fs.oss.* and oss:// paths");
            System.exit(2);
            return;
        }

        // endpoint 允许带或不带 https://
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            endpoint = "https://" + endpoint;
        }

        int failures = 0;
        System.out.println("--- build OSSClient ---");
        System.out.println("endpoint = " + endpoint);
        final OSS client;
        try {
            client = new OSSClientBuilder().build(endpoint, ak, sk);
            System.out.println("OK: client created");
            System.out.println();
        } catch (Exception e) {
            System.out.println("FATAL: create client failed: " + summarizeError(e));
            e.printStackTrace(System.out);
            System.exit(1);
            return;
        }

        final String bucketFinal = bucket;
        final String listPrefix = prefix;
        final int keys = maxKeys;
        try {
            failures += step("doesBucketExist", () -> {
                boolean ok = client.doesBucketExist(bucketFinal);
                System.out.println("bucket=" + bucketFinal + " exists=" + ok);
                if (!ok) {
                    throw new IllegalStateException("bucket not found or no permission: " + bucketFinal);
                }
            });

            failures += step("getBucketInfo", () -> {
                BucketInfo info = client.getBucketInfo(bucketFinal);
                System.out.println("name=" + info.getBucket().getName()
                        + " location=" + info.getBucket().getLocation()
                        + " creationDate=" + info.getBucket().getCreationDate()
                        + " owner=" + info.getBucket().getOwner());
            });

            failures += step("listObjects", () -> {
                ListObjectsRequest req = new ListObjectsRequest(bucketFinal)
                        .withPrefix(listPrefix)
                        .withMaxKeys(keys);
                ObjectListing listing = client.listObjects(req);
                printSummaries(listing.getObjectSummaries());
                System.out.println("truncated=" + listing.isTruncated()
                        + " nextMarker=" + listing.getNextMarker());
            });

            failures += step("listObjectsV2", () -> {
                ListObjectsV2Request req = new ListObjectsV2Request(bucketFinal)
                        .withPrefix(listPrefix)
                        .withMaxKeys(keys);
                ListObjectsV2Result result = client.listObjectsV2(req);
                printSummaries(result.getObjectSummaries());
                System.out.println("truncated=" + result.isTruncated()
                        + " nextContinuationToken=" + result.getNextContinuationToken());
            });
        } finally {
            try {
                client.shutdown();
            } catch (Exception ignore) {
                // ignore
            }
        }

        System.out.println();
        System.out.println("=== done failures=" + failures + " ===");
        System.exit(failures > 0 ? 1 : 0);
    }

    private static void printHelp() {
        System.out.println("Usage: java -jar flink-oss-probe.jar [--max-keys N]");
        System.out.println("Reads OSS_ENDPOINT / OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET / OSS_BUCKET_NAME");
        System.out.println("Optional: OSS_PREFIX, CHECKPOINT_DIR, FLINK_CONF_DIR");
    }

    private static void collectFromEnv(Map<String, String> cfg, List<String> sources) {
        putIfAbsent(cfg, "endpoint", env("OSS_ENDPOINT"));
        putIfAbsent(cfg, "accessKeyId", env("OSS_ACCESS_KEY_ID"));
        putIfAbsent(cfg, "accessKeySecret", env("OSS_ACCESS_KEY_SECRET"));
        putIfAbsent(cfg, "bucketName", env("OSS_BUCKET_NAME"));
        putIfAbsent(cfg, "prefix", env("OSS_PREFIX"));
        if (env("OSS_ENDPOINT") != null || env("OSS_ACCESS_KEY_ID") != null) {
            sources.add("env:OSS_*");
        }
    }

    private static void collectFromUriEnv(Map<String, String> cfg, List<String> sources) {
        for (String key : List.of("CHECKPOINT_DIR", "SAVEPOINT_DIR", "HA_STORAGE_DIR", "OUTPUT_PATH",
                "FLINK_OUTPUT_PATH", "FLINK_CHECKPOINT_DIR")) {
            String uri = env(key);
            if (isBlank(uri)) {
                continue;
            }
            Matcher m = OSS_URI.matcher(uri.trim());
            if (!m.matches()) {
                continue;
            }
            putIfAbsent(cfg, "bucketName", m.group(1));
            String path = m.group(2);
            if (!isBlank(path)) {
                // 仅在 prefix 仍空时用路径前缀的一级目录启发；完整路径保留供展示
                putIfAbsent(cfg, "uriPath." + key, path);
                if (isBlank(cfg.get("prefix"))) {
                    // 取到最后一个目录段之前作为 list 前缀（保守：用整段 path 的目录部分）
                    int slash = path.lastIndexOf('/');
                    String pfx = slash >= 0 ? path.substring(0, slash + 1) : path;
                    // checkpoints 场景用上级 prefix（如 data-pipeline/）
                    if (pfx.contains("flink/") || pfx.contains("cdc")) {
                        int idx = pfx.indexOf('/');
                        if (idx > 0) {
                            pfx = pfx.substring(0, idx + 1);
                        }
                    }
                    putIfAbsent(cfg, "prefix", pfx);
                }
            }
            sources.add("env:" + key + "=" + uri);
        }
    }

    private static void collectFromFlinkConf(Map<String, String> cfg, List<String> sources) {
        List<Path> candidates = new ArrayList<>();
        String confDir = env("FLINK_CONF_DIR");
        if (!isBlank(confDir)) {
            candidates.add(Path.of(confDir, "flink-conf.yaml"));
        }
        candidates.add(Path.of("/tmp/flink-conf-active/flink-conf.yaml"));
        candidates.add(Path.of("/tmp/flink-conf/flink-conf.yaml.dynamic"));
        candidates.add(Path.of("/opt/flink/conf/flink-conf.yaml"));

        for (Path p : candidates) {
            if (!Files.isRegularFile(p)) {
                continue;
            }
            try {
                Map<String, String> yaml = readSimpleYaml(p);
                putIfAbsent(cfg, "fs.oss.endpoint", yaml.get("fs.oss.endpoint"));
                putIfAbsent(cfg, "fs.oss.accessKeyId", yaml.get("fs.oss.accessKeyId"));
                putIfAbsent(cfg, "fs.oss.accessKeySecret", yaml.get("fs.oss.accessKeySecret"));
                putIfAbsent(cfg, "endpoint", yaml.get("fs.oss.endpoint"));
                putIfAbsent(cfg, "accessKeyId", yaml.get("fs.oss.accessKeyId"));
                putIfAbsent(cfg, "accessKeySecret", yaml.get("fs.oss.accessKeySecret"));

                for (String k : List.of("state.checkpoints.dir", "state.savepoints.dir",
                        "high-availability.storageDir")) {
                    String uri = yaml.get(k);
                    if (isBlank(uri)) {
                        continue;
                    }
                    Matcher m = OSS_URI.matcher(uri.trim());
                    if (m.matches()) {
                        putIfAbsent(cfg, "bucketName", m.group(1));
                        sources.add("conf:" + p + "#" + k + "=" + uri);
                    }
                }
                sources.add("conf:" + p);
                // 找到第一份可用 conf 即可（通常 active 最完整）
                if (!isBlank(cfg.get("accessKeyId")) || !isBlank(cfg.get("fs.oss.accessKeyId"))) {
                    break;
                }
            } catch (IOException e) {
                System.out.println("WARN: cannot read " + p + ": " + e.getMessage());
            }
        }
    }

    private static Map<String, String> readSimpleYaml(Path path) throws IOException {
        Map<String, String> map = new LinkedHashMap<>();
        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) {
                    continue;
                }
                Matcher m = YAML_KV.matcher(line);
                if (!m.matches()) {
                    continue;
                }
                String key = m.group(1);
                String val = stripQuotes(m.group(2));
                // 同名 key：后者覆盖前者（与 Flink last-wins 一致）
                map.put(key, val);
            }
        }
        return map;
    }

    private static String stripQuotes(String v) {
        if (v == null) {
            return null;
        }
        v = v.trim();
        if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    private static void printResolved(Map<String, String> cfg) {
        for (Map.Entry<String, String> e : cfg.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if (k.toLowerCase().contains("secret") || k.toLowerCase().contains("password")) {
                v = mask(v);
            } else if (k.toLowerCase().contains("accesskeyid") || k.equals("accessKeyId")
                    || k.equals("fs.oss.accessKeyId")) {
                v = mask(v);
            }
            System.out.println(k + " = " + v);
        }
    }

    private static int step(String name, ThrowingRunnable action) {
        System.out.println("--- [" + name + "] ---");
        try {
            action.run();
            System.out.println("OK");
            System.out.println();
            return 0;
        } catch (Exception e) {
            System.out.println("FAIL: " + summarizeError(e));
            e.printStackTrace(System.out);
            System.out.println();
            return 1;
        }
    }

    private static String summarizeError(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = e;
        int depth = 0;
        while (cur != null && depth < 6) {
            if (depth > 0) {
                sb.append(" <- ");
            }
            sb.append(cur.getClass().getSimpleName());
            if (cur instanceof OSSException oe) {
                sb.append("{errorCode=").append(oe.getErrorCode())
                        .append(", requestId=").append(oe.getRequestId())
                        .append(", message=").append(oe.getErrorMessage()).append('}');
            } else if (cur instanceof ClientException ce) {
                sb.append("{errorCode=").append(ce.getErrorCode())
                        .append(", message=").append(ce.getMessage()).append('}');
            } else if (cur.getMessage() != null) {
                sb.append('{').append(cur.getMessage()).append('}');
            }
            cur = cur.getCause();
            depth++;
        }
        // 附带精简 stack 首行位置
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sb.toString();
    }

    private static void printSummaries(List<OSSObjectSummary> summaries) {
        System.out.println("count=" + summaries.size());
        int i = 0;
        for (OSSObjectSummary s : summaries) {
            System.out.printf("  [%d] %s (%d bytes)%n", ++i, s.getKey(), s.getSize());
        }
    }

    private static void putIfAbsent(Map<String, String> cfg, String key, String value) {
        if (isBlank(value)) {
            return;
        }
        cfg.putIfAbsent(key, value.trim());
    }

    private static String env(String name) {
        String v = System.getenv(name);
        return isBlank(v) ? null : v.trim();
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) {
            return null;
        }
        for (String v : vals) {
            if (!isBlank(v)) {
                return v.trim();
            }
        }
        return null;
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }

    private static String mask(String v) {
        if (isBlank(v)) {
            return "(empty)";
        }
        if (v.length() < 8) {
            return "****";
        }
        return v.substring(0, 4) + "****" + v.substring(v.length() - 4);
    }

    private static String hostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "(unknown)";
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
