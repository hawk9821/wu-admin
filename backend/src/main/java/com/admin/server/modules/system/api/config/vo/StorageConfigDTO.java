package com.admin.server.modules.system.api.config.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 存储引擎配置更新入参 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageConfigDTO implements Serializable {

    @NotBlank(message = "存储类型不能为空 (local/minio/cos/oss)")
    private String type;

    /** 本地目录 */
    private String localPath;
    /** 本地外网域名 */
    private String localDomain;

    // === MinIO ===
    private String minioDomain;
    private String minioEndpoint;
    private String minioAccessKey;
    private String minioSecretKey;
    private String minioBucket;
    /** 显式清空 MinIO SecretKey；为空字符串时保留旧值，避免表单回显掩盖导致误删 */
    private Boolean clearMinioSecretKey;

    // === 腾讯云 COS ===
    private String cosSecretId;
    private String cosSecretKey;
    private String cosRegion;
    private String cosBucket;
    private String cosDomain;
    private Boolean clearCosSecretKey;

    // === 阿里云 OSS ===
    private String ossEndpoint;
    private String ossAccessKeyId;
    private String ossAccessKeySecret;
    private String ossBucket;
    private String ossDomain;
    private Boolean clearOssAccessKeySecret;

    // === 规格限制 ===
    private Integer maxSizeMb;
    private String allowedExtensions;
}
