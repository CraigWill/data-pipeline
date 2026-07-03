package com.realtime.monitor.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.realtime.monitor.dto.OssConnection;
import com.realtime.monitor.repository.OssConnectionRepository;
import com.realtime.monitor.util.PasswordEncryptionUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OSS 连接配置管理：CRUD + 连接测试。AccessKeySecret 加密存储。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OssConnectionService {

    private final OssConnectionRepository repository;

    /** 列表（脱敏：不返回 secret 明文）。 */
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (OssConnection c : repository.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("endpoint", c.getEndpoint());
            m.put("accessKeyId", c.getAccessKeyId());
            m.put("bucketName", c.getBucketName());
            m.put("prefix", c.getPrefix());
            m.put("status", c.getStatus());
            m.put("createdAt", c.getCreatedAt());
            m.put("updatedAt", c.getUpdatedAt());
            out.add(m);
        }
        return out;
    }

    /** 加载（返回明文 secret，用于内部使用/编辑回填时可选择不下发）。 */
    public OssConnection load(String id) {
        OssConnection c = repository.findById(id);
        if (c == null) throw new RuntimeException("OSS 连接不存在: " + id);
        if (c.getAccessKeySecret() != null && PasswordEncryptionUtil.isEncrypted(c.getAccessKeySecret())) {
            try {
                c.setAccessKeySecret(PasswordEncryptionUtil.decryptAES(c.getAccessKeySecret()));
            } catch (Exception e) {
                log.warn("OSS secret 解密失败: {}", id);
            }
        }
        return c;
    }

    public String save(OssConnection c) {
        if (c.getId() == null || c.getId().isEmpty()) {
            c.setId("oss-" + System.currentTimeMillis());
        }
        // secret 明文 → 加密存储
        if (c.getAccessKeySecret() != null && !c.getAccessKeySecret().isEmpty()
                && !PasswordEncryptionUtil.isEncrypted(c.getAccessKeySecret())) {
            try {
                c.setAccessKeySecret(PasswordEncryptionUtil.encryptAES(c.getAccessKeySecret()));
            } catch (Exception e) {
                log.error("OSS secret 加密失败", e);
            }
        }
        if (c.getStatus() == null) c.setStatus("UNTESTED");
        repository.save(c);
        return c.getId();
    }

    public void update(String id, OssConnection c) {
        OssConnection existing = repository.findById(id);
        if (existing == null) throw new RuntimeException("OSS 连接不存在: " + id);
        c.setId(id);
        // secret 留空表示不修改，沿用原密文
        if (c.getAccessKeySecret() == null || c.getAccessKeySecret().isEmpty()) {
            c.setAccessKeySecret(existing.getAccessKeySecret());
        } else if (!PasswordEncryptionUtil.isEncrypted(c.getAccessKeySecret())) {
            try {
                c.setAccessKeySecret(PasswordEncryptionUtil.encryptAES(c.getAccessKeySecret()));
            } catch (Exception e) {
                log.error("OSS secret 加密失败", e);
            }
        }
        if (c.getStatus() == null) c.setStatus(existing.getStatus());
        repository.save(c);
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    public void updateStatus(String id, String status) {
        repository.updateStatus(id, status);
    }

    /**
     * 测试连接：用配置的凭据建临时 OSS 客户端，检查 bucket 是否可访问。
     */
    public Map<String, Object> testConnection(String id) {
        Map<String, Object> result = new LinkedHashMap<>();
        OssConnection c = load(id); // secret 已解密
        OSS client = null;
        try {
            client = new OSSClientBuilder().build(c.getEndpoint(), c.getAccessKeyId(), c.getAccessKeySecret());
            boolean exists = client.doesBucketExist(c.getBucketName());
            if (exists) {
                result.put("success", true);
                result.put("message", "连接成功，Bucket 可访问");
            } else {
                result.put("success", false);
                result.put("error", "连接成功但 Bucket 不存在或无权限: " + c.getBucketName());
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "连接失败: " + e.getMessage());
        } finally {
            if (client != null) {
                try { client.shutdown(); } catch (Exception ignore) { }
            }
        }
        return result;
    }
}
