package com.realtime.monitor.oss;

import java.util.Date;
import java.util.List;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.BucketInfo;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.ListObjectsV2Request;
import com.aliyun.oss.model.ListObjectsV2Result;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;

/**
 * 手工 OSS 连通性测试（含按桶名访问）。
 *
 * <p>不走 Spring，直接 {@code main} 运行。凭据从环境变量或 {@code -D} 读取，勿把密钥写进代码。
 *
 * <pre>
 * 必填：
 *   OSS_ENDPOINT / OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET / OSS_BUCKET_NAME
 * 可选：
 *   OSS_PREFIX   列对象前缀，默认空
 *   OSS_MAX_KEYS 列对象条数，默认 10
 *
 * IDE：直接 Run main，在 Run Configuration 里配环境变量。
 *
 * 命令行示例：
 *   cd monitor-backend
 *   mvn -q -DskipTests exec:java \
 *     -Dexec.classpathScope=test \
 *     -Dexec.mainClass=com.realtime.monitor.oss.OssClientManualMain \
 *     -DOSS_ENDPOINT=https://oss-cn-shanghai.aliyuncs.com \
 *     -DOSS_ACCESS_KEY_ID=xxx \
 *     -DOSS_ACCESS_KEY_SECRET=xxx \
 *     -DOSS_BUCKET_NAME=your-bucket \
 *     -DOSS_PREFIX=data-pipeline/cdc/
 * </pre>
 */
public final class OssClientManualMain {

    private OssClientManualMain() {}

    public static void main(String[] args) {
        String endpoint = req("OSS_ENDPOINT", "oss.endpoint");
        String accessKeyId = req("OSS_ACCESS_KEY_ID", "oss.accessKeyId");
        String accessKeySecret = req("OSS_ACCESS_KEY_SECRET", "oss.accessKeySecret");
        String bucketName = req("OSS_BUCKET_NAME", "oss.bucketName");
        String prefix = opt("OSS_PREFIX", "oss.prefix", "");
        int maxKeys = Integer.parseInt(opt("OSS_MAX_KEYS", "oss.maxKeys", "10"));

        System.out.println("=== OSS 手工连通测试 ===");
        System.out.println("endpoint   = " + endpoint);
        System.out.println("bucketName = " + bucketName);
        System.out.println("prefix     = " + (prefix.isEmpty() ? "(empty)" : prefix));
        System.out.println("accessKey  = " + mask(accessKeyId));
        System.out.println("sdk        = aliyun-sdk-oss (默认签名 SignVersion.V1)");
        System.out.println();

        OSS client = null;
        int failures = 0;
        try {
            client = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

            // 1) 按桶名：是否存在 / 是否可访问
            System.out.println("--- [1] doesBucketExist(\"" + bucketName + "\") ---");
            boolean exists = client.doesBucketExist(bucketName);
            System.out.println("result = " + exists);
            if (!exists) {
                failures++;
                System.out.println("FAIL: 桶不存在或当前 AK 无权限访问该桶名");
            } else {
                System.out.println("OK");
            }
            System.out.println();

            // 2) 按桶名：拉取桶元信息
            System.out.println("--- [2] getBucketInfo(\"" + bucketName + "\") ---");
            try {
                BucketInfo info = client.getBucketInfo(bucketName);
                System.out.println("name         = " + info.getBucket().getName());
                System.out.println("location     = " + info.getBucket().getLocation());
                System.out.println("creationDate = " + info.getBucket().getCreationDate());
                System.out.println("owner        = " + info.getBucket().getOwner());
                System.out.println("OK");
            } catch (Exception e) {
                failures++;
                System.out.println("FAIL: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            System.out.println();

            // 3) listObjects（与 OssStorageService 一致）
            System.out.println("--- [3] listObjects bucket=" + bucketName + " prefix=" + prefix + " ---");
            try {
                ListObjectsRequest req = new ListObjectsRequest(bucketName)
                        .withPrefix(prefix)
                        .withMaxKeys(maxKeys);
                ObjectListing listing = client.listObjects(req);
                printSummaries("listObjects", listing.getObjectSummaries());
                System.out.println("truncated = " + listing.isTruncated()
                        + ", nextMarker = " + listing.getNextMarker());
                System.out.println("OK");
            } catch (Exception e) {
                failures++;
                System.out.println("FAIL: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                e.printStackTrace(System.out);
            }
            System.out.println();

            // 4) listObjectsV2（生产 Flink / 部分调用链会走此 API）
            System.out.println("--- [4] listObjectsV2 bucket=" + bucketName + " prefix=" + prefix + " ---");
            try {
                ListObjectsV2Request req = new ListObjectsV2Request(bucketName)
                        .withPrefix(prefix)
                        .withMaxKeys(maxKeys);
                ListObjectsV2Result result = client.listObjectsV2(req);
                printSummaries("listObjectsV2", result.getObjectSummaries());
                System.out.println("truncated = " + result.isTruncated()
                        + ", nextContinuationToken = " + result.getNextContinuationToken());
                System.out.println("OK");
            } catch (Exception e) {
                failures++;
                System.out.println("FAIL: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                e.printStackTrace(System.out);
            }
            System.out.println();

        } catch (Exception e) {
            failures++;
            System.out.println("FATAL: 创建客户端或调用失败: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace(System.out);
        } finally {
            if (client != null) {
                try {
                    client.shutdown();
                } catch (Exception ignore) {
                    // ignore
                }
            }
        }

        System.out.println("=== 结束 " + new Date() + " failures=" + failures + " ===");
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static void printSummaries(String label, List<OSSObjectSummary> summaries) {
        System.out.println(label + " count = " + summaries.size());
        int i = 0;
        for (OSSObjectSummary s : summaries) {
            System.out.printf("  [%d] key=%s size=%d%n", ++i, s.getKey(), s.getSize());
        }
    }

    private static String req(String env, String prop) {
        String v = opt(env, prop, null);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("缺少必填配置: 环境变量 " + env + " 或 -D" + prop);
        }
        return v.trim();
    }

    private static String opt(String env, String prop, String defaultValue) {
        String v = System.getenv(env);
        if (v == null || v.isBlank()) {
            v = System.getProperty(prop);
        }
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        return v.trim();
    }

    private static String mask(String ak) {
        if (ak == null || ak.length() < 8) {
            return "****";
        }
        return ak.substring(0, 4) + "****" + ak.substring(ak.length() - 4);
    }
}
