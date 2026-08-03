package com.realtime.pipeline.localosstest;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.core.fs.FSDataInputStream;
import org.apache.flink.core.fs.FSDataOutputStream;
import org.apache.flink.core.fs.FileStatus;
import org.apache.flink.core.fs.FileSystem;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/**
 * 用 Flink 自带 {@code flink-oss-fs-hadoop} 插件验证 {@code oss://} FileSystem。
 *
 * <p>与 {@code flink-oss} 的阿里云 SDK 探针不同：本作业只走
 * {@link FileSystem#get(java.net.URI)}，在 JM/TM 进程内由插件类加载器解析 {@code oss} scheme。
 *
 * <pre>
 *   flink run -c com.realtime.pipeline.localosstest.OssFsPluginProbeJob localosstest-smoke.jar \
 *     --path oss://bucket/prefix/_flink_oss_plugin_probe/
 * </pre>
 */
public final class OssFsPluginProbeJob {

    private static final String MARKER = "flink-oss-fs-hadoop-ok";

    private OssFsPluginProbeJob() {}

    public static void main(String[] args) throws Exception {
        String path = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--path":
                    path = requireValue(args, ++i, "--path");
                    break;
                case "--help":
                case "-h":
                    printHelp();
                    return;
                default:
                    throw new IllegalArgumentException("Unknown arg: " + args[i]);
            }
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Missing required --path (e.g. oss://bucket/prefix/_probe/)");
        }
        if (!path.startsWith("oss://")) {
            throw new IllegalArgumentException("--path must be oss://... (got: " + path + ")");
        }
        if (!path.endsWith("/")) {
            path = path + "/";
        }

        System.out.println("=== OssFsPluginProbeJob (Flink OSS plugin) ===");
        System.out.println("path = " + path);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        final String basePath = path;
        env.fromElements(basePath)
                .map(new RichMapFunction<String, String>() {
                    @Override
                    public String map(String base) throws Exception {
                        return probe(base);
                    }
                })
                .name("oss-fs-plugin-probe")
                .uid("oss-fs-plugin-probe")
                .print();

        env.execute("oss-fs-plugin-probe");
    }

    static String probe(String baseUri) throws Exception {
        Path dir = new Path(baseUri);
        FileSystem fs = dir.getFileSystem();
        String scheme = fs.getUri().getScheme();
        String fsClass = fs.getClass().getName();

        if (!"oss".equalsIgnoreCase(scheme)) {
            throw new IllegalStateException(
                    "Expected scheme=oss via flink-oss-fs-hadoop plugin, got scheme="
                            + scheme + " class=" + fsClass);
        }

        String objectName = "probe-" + UUID.randomUUID() + ".txt";
        Path file = new Path(dir, objectName);
        byte[] payload = (MARKER + "\n" + System.currentTimeMillis() + "\n")
                .getBytes(StandardCharsets.UTF_8);

        try (FSDataOutputStream out = fs.create(file, FileSystem.WriteMode.OVERWRITE)) {
            out.write(payload);
        }

        if (!fs.exists(file)) {
            throw new IllegalStateException("exists()=false after create: " + file);
        }

        ByteArrayOutputStream readBack = new ByteArrayOutputStream();
        try (FSDataInputStream in = fs.open(file)) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) >= 0) {
                readBack.write(buf, 0, n);
            }
        }
        String content = readBack.toString(StandardCharsets.UTF_8.name());
        if (!content.startsWith(MARKER)) {
            throw new IllegalStateException("read mismatch, content=" + content);
        }

        FileStatus status = fs.getFileStatus(file);
        long len = status.getLen();
        if (len != payload.length) {
            throw new IllegalStateException("len mismatch: expected=" + payload.length + " actual=" + len);
        }

        FileStatus[] listed = fs.listStatus(dir);
        int listedCount = listed == null ? 0 : listed.length;

        boolean deleted = fs.delete(file, false);
        if (!deleted) {
            throw new IllegalStateException("delete returned false: " + file);
        }

        return "OK scheme=" + scheme
                + " class=" + fsClass
                + " wrote=" + objectName
                + " bytes=" + len
                + " listed=" + listedCount
                + " deleted=true";
    }

    private static String requireValue(String[] args, int idx, String flag) {
        if (idx >= args.length) {
            throw new IllegalArgumentException("Missing value for " + flag);
        }
        return args[idx];
    }

    private static void printHelp() {
        System.out.println("Usage: OssFsPluginProbeJob --path oss://bucket/prefix/_flink_oss_plugin_probe/");
        System.out.println("  Uses Flink FileSystem + flink-oss-fs-hadoop plugin (not Aliyun OSS SDK).");
        System.out.println("  Steps: create → exists → open/read → getFileStatus → listStatus → delete");
    }
}
