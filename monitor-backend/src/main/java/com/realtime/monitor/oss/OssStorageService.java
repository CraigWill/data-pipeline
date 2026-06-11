package com.realtime.monitor.oss;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;

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

    public OssStorageService(@Nullable OSS ossClient, OssConfig ossConfig) {
        this.ossClient = ossClient;
        this.ossConfig = ossConfig;
    }

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
}
