package com.admin.server.framework.storage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 全局存储引擎配置参数模型 (支持 local / minio / cos / oss)
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class StorageConfig implements Serializable {

    /** 激活的存储类型：local / minio / cos / oss */
    @Builder.Default
    private String type = "local";

    /** 本地存储根目录 (例如 ./data/uploads 或绝对路径) */
    private String localPath;

    /** 本地公网访问域名 (可选，留空走相对路径 /files/) */
    private String localDomain;

    // === MinIO 配置 ===
    private String minioDomain;
    private String minioEndpoint;
    private String minioAccessKey;
    private String minioSecretKey;
    @Builder.Default
    private String minioBucket = "wu-admin";

    // === 腾讯云 COS 配置 ===
    private String cosSecretId;
    private String cosSecretKey;
    private String cosRegion;
    private String cosBucket;
    private String cosDomain;

    // === 阿里云 OSS 配置 ===
    private String ossEndpoint;
    private String ossAccessKeyId;
    private String ossAccessKeySecret;
    private String ossBucket;
    private String ossDomain;
}
