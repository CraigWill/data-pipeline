package com.realtime.monitor.oss;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;
import com.aliyun.oss.model.ObjectMetadata;
import com.realtime.monitor.dto.OssConnection;
import com.realtime.monitor.service.OssConnectionService;

import lombok.extern.slf4j.Slf4j;

/**
 * OSS 同步存储服务。
 *
 * <p>策略：
 * <ul>
 *   <li>写入：先写本地文件，再同步上传到 OSS（本地为主，OSS 为备份）</li>
 *   <li>读取：优先从 OSS 读取，OSS 不可用时降级到本地文件</li>
 * </ul>
 *
 * <p>当 OSS 未启用时，所有方法静默退化为纯本地操作。
 */
@Slf4j
@Service
public class OssStorageService {

    private final OSS ossClient;
    private final OssConfig ossConfig;
    private final OssConnectionService ossConnectionService;

    // 按 ossConnectionId 缓存的客户端与配置
    private final Map<String, OSS> clientCache = new ConcurrentHashMap<>();
    private final Map<String, OssConnection> connCache = new ConcurrentHashMap<>();

    public OssStorageService(@Nullable OSS ossClient, OssConfig ossConfig,
                             OssConnectionService ossConnectionService) {
        this.ossClient = ossClient;
        this.ossConfig = ossConfig;
        this.ossConnectionService = ossConnectionService;
    }

    /**
     * 将本地文件同步到指定 OSS 连接；ossConnectionId 为空或解析失败时回退到全局默认 OSS。
     */
    public void syncToOss(File localFile, String ossKey, String ossConnectionId) {
        if (ossConnectionId == null || ossConnectionId.isBlank()) {
            syncToOss(localFile, ossKey);
            return;
        }
        OssConnection conn = resolveConn(ossConnectionId);
        OSS client = clientFor(ossConnectionId);
        if (conn == null || client == null) {
            log.warn("OSS 连接 {} 不可用，回退到全局默认 OSS", ossConnectionId);
            syncToOss(localFile, ossKey);
            return;
        }
        if (!localFile.exists()) {
            log.warn("本地文件不存在，跳过 OSS 同步: {}", localFile.getAbsolutePath());
            return;
        }
        String fullKey = nn(conn.getPrefix()) + ossKey;
        try (FileInputStream fis = new FileInputStream(localFile)) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(localFile.length());
            client.putObject(conn.getBucketName(), fullKey, fis, metadata);
            log.debug("文件已同步到 OSS[{}]: {} -> {}", ossConnectionId, localFile.getName(), fullKey);
        } catch (Exception e) {
            log.warn("OSS[{}] 同步失败（不影响本地存储）: {} -> {}, error: {}",
                    ossConnectionId, localFile.getName(), fullKey, e.getMessage());
        }
    }

    /** 解析 OSS 连接（带缓存）。 */
    private OssConnection resolveConn(String id) {
        return connCache.computeIfAbsent(id, k -> {
            try {
                return ossConnectionService.load(k); // secret 已解密
            } catch (Exception e) {
                log.warn("加载 OSS 连接失败: {}, {}", k, e.getMessage());
                return null;
            }
        });
    }

    /** 构建/获取指定连接的 OSS 客户端（带缓存）。 */
    private OSS clientFor(String id) {
        return clientCache.computeIfAbsent(id, k -> {
            OssConnection c = resolveConn(k);
            if (c == null || c.getEndpoint() == null || c.getAccessKeyId() == null) return null;
            try {
                return new OSSClientBuilder().build(c.getEndpoint(), c.getAccessKeyId(), c.getAccessKeySecret());
            } catch (Exception e) {
                log.warn("构建 OSS 客户端失败: {}, {}", k, e.getMessage());
                return null;
            }
        });
    }

    private static String nn(String v) { return v != null ? v : ""; }

    /**
     * 是否启用 OSS。
     */
    public boolean isEnabled() {
        return ossConfig.isEnabled() && ossClient != null;
    }

    /**
     * 将本地文件同步上传到 OSS。
     *
     * @param localFile 本地文件
     * @param ossKey    OSS 对象键（不含 prefix，如 "savepoints/sp-123"）
     */
    public void syncToOss(File localFile, String ossKey) {
        if (!isEnabled()) return;
        if (!localFile.exists()) {
            log.warn("本地文件不存在，跳过 OSS 同步: {}", localFile.getAbsolutePath());
            return;
        }
        String fullKey = ossConfig.getPrefix() + ossKey;
        try (FileInputStream fis = new FileInputStream(localFile)) {
            ObjectMetadata metadata = new ObjectMetadata();
     
            metadata.setContentLength(localFile.length());
            ossClient.putObject(ossConfig.getBucketName(), fullKey, fis, metadata);
            log.debug("文件已同步到 OSS: {} -> {}", localFile.getName(), fullKey);
        } catch (Exception e) {
            log.warn("OSS 同步失败（不影响本地存储）: {} -> {}, error: {}", localFile.getName(), fullKey, e.getMessage());
        }
    }

    /**
     * 将字节数据上传到 OSS。
     *
     * @param data   文件内容
     * @param ossKey OSS 对象键（不含 prefix）
     */
    public void uploadBytes(byte[] data, String ossKey) {
        if (!isEnabled()) return;
        String fullKey = ossConfig.getPrefix() + ossKey;
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(data.length);
            ossClient.putObject(ossConfig.getBucketName(), fullKey, new ByteArrayInputStream(data), metadata);
            log.debug("数据已上传到 OSS: {} ({} bytes)", fullKey, data.length);
        } catch (Exception e) {
            log.warn("OSS 上传失败: {}, error: {}", fullKey, e.getMessage());
        }
    }

    /**
     * 从 OSS 下载文件到本地。优先 OSS，失败时降级到本地。
     *
     * @param ossKey    OSS 对象键（不含 prefix）
     * @param localPath 本地保存路径
     * @return true=从 OSS 获取成功，false=使用本地文件
     */
    public boolean downloadToLocal(String ossKey, Path localPath) {
        if (!isEnabled()) return false;
        String fullKey = ossConfig.getPrefix() + ossKey;
        try {
            if (!ossClient.doesObjectExist(ossConfig.getBucketName(), fullKey)) {
                return false;
            }
            OSSObject object = ossClient.getObject(ossConfig.getBucketName(), fullKey);
            try (InputStream is = object.getObjectContent()) {
                Files.createDirectories(localPath.getParent());
                Files.copy(is, localPath, StandardCopyOption.REPLACE_EXISTING);
            }
            log.debug("从 OSS 下载文件: {} -> {}", fullKey, localPath);
            return true;
        } catch (Exception e) {
            log.warn("OSS 下载失败，降级到本地: {}, error: {}", fullKey, e.getMessage());
            return false;
        }
    }

    /**
     * 从 OSS 读取文件内容。优先 OSS，不存在返回 null。
     *
     * @param ossKey OSS 对象键（不含 prefix）
     * @return 文件内容字节数组，不存在或失败返回 null
     */
    @Nullable
    public byte[] readFromOss(String ossKey) {
        if (!isEnabled()) return null;
        String fullKey = ossConfig.getPrefix() + ossKey;
        try {
            if (!ossClient.doesObjectExist(ossConfig.getBucketName(), fullKey)) {
                return null;
            }
            OSSObject object = ossClient.getObject(ossConfig.getBucketName(), fullKey);
            try (InputStream is = object.getObjectContent()) {
                return is.readAllBytes();
            }
        } catch (Exception e) {
            log.warn("OSS 读取失败: {}, error: {}", fullKey, e.getMessage());
            return null;
        }
    }

    /**
     * 检查 OSS 上是否存在指定对象。
     */
    public boolean existsOnOss(String ossKey) {
        if (!isEnabled()) return false;
        String fullKey = ossConfig.getPrefix() + ossKey;
        try {
            return ossClient.doesObjectExist(ossConfig.getBucketName(), fullKey);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 删除 OSS 上的对象。
     */
    public void deleteFromOss(String ossKey) {
        if (!isEnabled()) return;
        String fullKey = ossConfig.getPrefix() + ossKey;
        try {
            ossClient.deleteObject(ossConfig.getBucketName(), fullKey);
            log.debug("OSS 对象已删除: {}", fullKey);
        } catch (Exception e) {
            log.warn("OSS 删除失败: {}, error: {}", fullKey, e.getMessage());
        }
    }

    /** 判断是否为 oss:// URI。 */
    public static boolean isOssUri(String path) {
        return path != null && path.regionMatches(true, 0, "oss://", 0, 6);
    }

    /**
     * 从 OUTPUT_PATH（如 {@code oss://bucket/data-pipeline/cdc}）解析相对前缀
     * （不含全局 {@link OssConfig#getPrefix()}，如 {@code cdc}）。
     * 解析失败时回退为 {@code cdc}。
     */
    public String resolveCdcRelativePrefix(String outputPath) {
        if (!isOssUri(outputPath)) {
            return "cdc";
        }
        String withoutScheme = outputPath.substring(6); // strip oss://
        int slash = withoutScheme.indexOf('/');
        if (slash < 0 || slash == withoutScheme.length() - 1) {
            return "cdc";
        }
        String keyAfterBucket = withoutScheme.substring(slash + 1);
        while (keyAfterBucket.endsWith("/")) {
            keyAfterBucket = keyAfterBucket.substring(0, keyAfterBucket.length() - 1);
        }
        String cfgPrefix = nn(ossConfig.getPrefix());
        if (!cfgPrefix.isEmpty() && keyAfterBucket.startsWith(cfgPrefix)) {
            String rel = keyAfterBucket.substring(cfgPrefix.length());
            while (rel.startsWith("/")) {
                rel = rel.substring(1);
            }
            return rel.isEmpty() ? "cdc" : rel;
        }
        // 取最后一段作为相对根（兼容非标准 prefix）
        int last = keyAfterBucket.lastIndexOf('/');
        return last >= 0 ? keyAfterBucket.substring(last + 1) : keyAfterBucket;
    }

    /**
     * 列出相对前缀下的对象（自动拼接全局 prefix）。
     *
     * @param relativePrefix 如 {@code cdc/} 或 {@code cdc}
     * @param maxKeys        最多返回条数（防止一次拉太多）
     */
    public List<OssObjectEntry> listObjects(String relativePrefix, int maxKeys) {
        List<OssObjectEntry> result = new ArrayList<>();
        if (!isEnabled()) return result;
        String rel = relativePrefix == null ? "" : relativePrefix;
        if (!rel.isEmpty() && !rel.endsWith("/")) {
            rel = rel + "/";
        }
        String fullPrefix = ossConfig.getPrefix() + rel;
        int limit = Math.min(Math.max(maxKeys, 1), 2000);
        try {
            String marker = null;
            while (result.size() < limit) {
                ListObjectsRequest req = new ListObjectsRequest(ossConfig.getBucketName())
                        .withPrefix(fullPrefix)
                        .withMaxKeys(Math.min(200, limit - result.size()))
                        .withMarker(marker);
                ObjectListing listing = ossClient.listObjects(req);
                for (OSSObjectSummary s : listing.getObjectSummaries()) {
                    String fullKey = s.getKey();
                    if (fullKey.endsWith("/")) continue; // 目录占位
                    String relativeKey = fullKey;
                    String cfgPrefix = nn(ossConfig.getPrefix());
                    if (!cfgPrefix.isEmpty() && fullKey.startsWith(cfgPrefix)) {
                        relativeKey = fullKey.substring(cfgPrefix.length());
                    }
                    String fileName = relativeKey;
                    int slash = relativeKey.lastIndexOf('/');
                    if (slash >= 0) {
                        fileName = relativeKey.substring(slash + 1);
                    }
                    // 跳过 Flink in-progress / 非 CSV
                    if (fileName.startsWith(".") || fileName.contains(".inprogress")
                            || !fileName.toLowerCase().endsWith(".csv")) {
                        continue;
                    }
                    String dateDir = "";
                    // 相对路径形如 cdc/2026-07-14--14/file.csv
                    String[] parts = relativeKey.split("/");
                    if (parts.length >= 2) {
                        dateDir = parts[parts.length - 2];
                    }
                    result.add(new OssObjectEntry(
                            relativeKey,
                            dateDir,
                            fileName,
                            s.getSize(),
                            s.getLastModified()));
                    if (result.size() >= limit) break;
                }
                if (!listing.isTruncated() || result.size() >= limit) break;
                marker = listing.getNextMarker();
            }
        } catch (Exception e) {
            log.warn("OSS 列举失败 prefix={}: {}", fullPrefix, e.getMessage());
        }
        return result;
    }

    /**
     * OSS 对象摘要（相对 key，不含全局 prefix）。
     */
    public record OssObjectEntry(
            String relativeKey,
            String dateDir,
            String fileName,
            long size,
            Date lastModified) {}
}
