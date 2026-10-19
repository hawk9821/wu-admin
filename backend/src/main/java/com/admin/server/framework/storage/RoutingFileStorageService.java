package com.admin.server.framework.storage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态路由文件存储门面服务 (支持管理后台热切换 local / minio / cos / oss)
 * <p>写操作始终落到当前生效引擎；读操作会优先当前引擎，未命中时回退到其它已配置引擎，
 * 从而保证热切换后旧文件仍然可读。删除操作按记录中的历史引擎路由，避免在错误引擎上误删。</p>
 */
@Slf4j
@Primary
@Service
public class RoutingFileStorageService implements FileStorageService {

    @Value("${file.storage.type:local}")
    private String defaultType;

    @Value("${file.storage.local-path:./data/uploads}")
    private String defaultLocalDir;

    @Value("${file.storage.minio.endpoint:http://127.0.0.1:9000}")
    private String defaultMinioEndpoint;

    @Value("${file.storage.minio.access-key:minioadmin}")
    private String defaultMinioAccessKey;

    @Value("${file.storage.minio.secret-key:minioadmin}")
    private String defaultMinioSecretKey;

    @Value("${file.storage.minio.bucket-name:wu-admin}")
    private String defaultMinioBucket;

    private volatile FileStorageService delegate;
    private volatile StorageConfig currentConfig;
    /** 最近一次主动连通性测试结果；读取配置页时不再实时探测 */
    private volatile Boolean lastHealth;
    private final Map<String, FileStorageService> driverCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        StorageConfig initialConfig = StorageConfig.builder()
                .type(defaultType)
                .localPath(defaultLocalDir)
                .minioEndpoint(defaultMinioEndpoint)
                .minioAccessKey(defaultMinioAccessKey)
                .minioSecretKey(defaultMinioSecretKey)
                .minioBucket(defaultMinioBucket)
                .build();
        reload(initialConfig);
    }

    /**
     * 运行时热重载激活的存储引擎驱动
     *
     * @param config 存储配置模型
     */
    public synchronized void reload(StorageConfig config) {
        if (config == null) {
            config = StorageConfig.builder().type("local").localPath(defaultLocalDir).build();
        }
        FileStorageService next = createDriver(config);
        this.delegate = next;
        this.currentConfig = config;
        this.driverCache.clear();
        this.driverCache.put(normalizeType(next.getStorageType()), next);
        log.info("文件存储驱动热切换成功: 引擎类型={}, 驱动实例={}", next.getStorageType(), next.getClass().getSimpleName());
    }

    /**
     * 针对指定配置进行连通性测试 (不替换当前运行态驱动)
     *
     * @param config 待测试配置
     * @return 连通性测试是否成功
     */
    public boolean testConnection(StorageConfig config) {
        return testConnectionDetail(config) == null;
    }

    /**
     * 针对指定配置进行连通性测试并返回诊断信息 (不替换当前运行态驱动)
     *
     * @param config 待测试配置
     * @return 成功返回 null；失败返回具体原因描述
     */
    public String testConnectionDetail(StorageConfig config) {
        if (config == null) {
            return "存储配置对象不能为空";
        }
        try {
            FileStorageService probe = createDriver(config);
            return probe.testConnectionDetail();
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Throwable t) {
            log.warn("存储驱动连通性测试异常: 引擎={}, 异常={}", config.getType(), t.getMessage(), t);
            if (t instanceof LinkageError || t instanceof ClassNotFoundException || t instanceof NoClassDefFoundError) {
                return "存储组件依赖加载失败 (" + t.getClass().getSimpleName() + "): " + t.getMessage();
            }
            return "存储驱动初始化失败: " + t.getMessage();
        }
    }

    /**
     * 获取当前生效的存储配置
     */
    public StorageConfig getCurrentConfig() {
        return currentConfig;
    }

    /**
     * 获取指定引擎的驱动。同一 JVM 内按引擎类型缓存，避免每次读旧文件都重建客户端。
     */
    public FileStorageService driverFor(String storageType) {
        String type = normalizeType(storageType);
        FileStorageService cached = driverCache.get(type);
        if (cached != null) {
            return cached;
        }
        StorageConfig config = currentConfig;
        if (config == null) {
            if ("local".equals(type) && delegate != null) {
                return delegate;
            }
            throw new IllegalStateException("当前未初始化有效的文件存储配置");
        }
        FileStorageService created = createDriver(config.toBuilder().type(type).build());
        FileStorageService previous = driverCache.putIfAbsent(type, created);
        return previous != null ? previous : created;
    }

    /**
     * 判断 URL 中的 bucket 是否属于系统配置的桶，防止公开代理被用于读取任意桶。
     */
    public boolean isAllowedBucket(String bucketName) {
        if (!StringUtils.hasText(bucketName)) {
            return false;
        }
        String bucket = bucketName.trim();
        if ("wu-admin".equalsIgnoreCase(bucket) || "local".equalsIgnoreCase(bucket)) {
            return true;
        }
        StorageConfig config = currentConfig;
        if (config == null) {
            return false;
        }
        return bucket.equals(defaultString(config.getMinioBucket()))
                || bucket.equals(defaultString(config.getCosBucket()))
                || bucket.equals(defaultString(config.getOssBucket()));
    }

    /**
     * 根据配置创建对应存储引擎驱动
     */
    public FileStorageService createDriver(StorageConfig config) {
        String type = config != null && StringUtils.hasText(config.getType())
                ? config.getType().trim().toLowerCase() : "local";
        return switch (type) {
            case "minio" -> new MinioFileStorageService(
                    config.getMinioEndpoint(),
                    config.getMinioAccessKey(),
                    config.getMinioSecretKey(),
                    config.getMinioBucket(),
                    config.getMinioDomain()
            );
            case "cos" -> new CosFileStorageService(
                    config.getCosSecretId(),
                    config.getCosSecretKey(),
                    config.getCosRegion(),
                    config.getCosBucket(),
                    config.getCosDomain()
            );
            case "oss" -> new AliyunOssFileStorageService(
                    config.getOssEndpoint(),
                    config.getOssAccessKeyId(),
                    config.getOssAccessKeySecret(),
                    config.getOssBucket(),
                    config.getOssDomain()
            );
            case "local" -> new LocalFileStorageService(config.getLocalPath(), config.getLocalDomain());
            default -> throw new IllegalArgumentException("不支持的存储引擎类型: " + type);
        };
    }

    @Override
    public String uploadFile(String bucketName, String objectName, InputStream inputStream, String contentType) {
        FileStorageService driver = delegate;
        if (driver == null) {
            throw new IllegalStateException("当前未初始化有效的文件存储驱动");
        }
        return driver.uploadFile(bucketName, objectName, inputStream, contentType);
    }

    @Override
    public InputStream getFile(String bucketName, String objectName) {
        InputStream stream = tryGet(delegate, bucketName, objectName);
        if (stream != null) {
            return stream;
        }
        String currentType = getStorageType();
        for (String type : configuredTypes()) {
            if (type.equals(currentType)) {
                continue;
            }
            stream = tryGet(tryDriver(type), bucketName, objectName);
            if (stream != null) {
                return stream;
            }
        }
        return null;
    }

    @Override
    public InputStream getFile(String bucketName, String objectName, long offset, long length) {
        InputStream stream = tryGet(delegate, bucketName, objectName, offset, length);
        if (stream != null) {
            return stream;
        }
        String currentType = getStorageType();
        for (String type : configuredTypes()) {
            if (type.equals(currentType)) {
                continue;
            }
            stream = tryGet(tryDriver(type), bucketName, objectName, offset, length);
            if (stream != null) {
                return stream;
            }
        }
        return null;
    }

    @Override
    public byte[] getFileBytes(String bucketName, String objectName) {
        try (InputStream stream = getFile(bucketName, objectName)) {
            if (stream == null) {
                return new byte[0];
            }
            return StreamUtils.copyToByteArray(stream);
        } catch (IOException e) {
            throw new IllegalStateException("读取文件字节失败: " + e.getMessage(), e);
        }
    }

    @Override
    public long getFileSize(String bucketName, String objectName) {
        long size = trySize(delegate, bucketName, objectName);
        if (size >= 0) {
            return size;
        }
        String currentType = getStorageType();
        for (String type : configuredTypes()) {
            if (type.equals(currentType)) {
                continue;
            }
            size = trySize(tryDriver(type), bucketName, objectName);
            if (size >= 0) {
                return size;
            }
        }
        return -1L;
    }

    @Override
    public void deleteFile(String bucketName, String objectName) {
        FileStorageService driver = delegate;
        if (driver != null) {
            driver.deleteFile(bucketName, objectName);
        }
    }

    /**
     * 按记录中的存储引擎删除，避免热切换后在当前引擎上误删或漏删。
     */
    public void deleteFileForRecord(String storageType, String bucketName, String objectName) {
        driverFor(storageType).deleteFile(bucketName, objectName);
    }

    @Override
    public String getStorageType() {
        return delegate != null ? delegate.getStorageType() : "local";
    }

    @Override
    public boolean testConnection() {
        return testConnectionDetail() == null;
    }

    @Override
    public String testConnectionDetail() {
        String detail = delegate != null ? delegate.testConnectionDetail() : "当前未初始化有效的文件存储驱动";
        lastHealth = detail == null;
        return detail;
    }

    /**
     * 最近一次主动连通性测试结果；从未测试过时返回 null。
     */
    public Boolean getLastHealth() {
        return lastHealth;
    }

    private FileStorageService tryDriver(String type) {
        try {
            return driverFor(type);
        } catch (Exception e) {
            log.debug("创建历史存储引擎驱动失败: type={}, reason={}", type, e.getMessage());
            return null;
        }
    }

    private InputStream tryGet(FileStorageService driver, String bucketName, String objectName) {
        if (driver == null) {
            return null;
        }
        try {
            return driver.getFile(bucketName, objectName);
        } catch (Exception e) {
            log.debug("存储引擎读取文件失败: type={}, bucket={}, object={}, reason={}",
                    driver.getStorageType(), bucketName, objectName, e.getMessage());
            return null;
        }
    }

    private InputStream tryGet(FileStorageService driver, String bucketName, String objectName,
                               long offset, long length) {
        if (driver == null) {
            return null;
        }
        try {
            return driver.getFile(bucketName, objectName, offset, length);
        } catch (Exception e) {
            log.debug("存储引擎区间读取失败: type={}, bucket={}, object={}, reason={}",
                    driver.getStorageType(), bucketName, objectName, e.getMessage());
            return null;
        }
    }

    private long trySize(FileStorageService driver, String bucketName, String objectName) {
        if (driver == null) {
            return -1L;
        }
        try {
            return driver.getFileSize(bucketName, objectName);
        } catch (Exception e) {
            log.debug("存储引擎读取文件大小失败: type={}, bucket={}, object={}, reason={}",
                    driver.getStorageType(), bucketName, objectName, e.getMessage());
            return -1L;
        }
    }

    private List<String> configuredTypes() {
        List<String> types = new ArrayList<>();
        types.add("local");
        StorageConfig config = currentConfig;
        if (config == null) {
            return types;
        }
        if (StringUtils.hasText(config.getMinioEndpoint())
                && StringUtils.hasText(config.getMinioAccessKey())
                && StringUtils.hasText(config.getMinioSecretKey())) {
            types.add("minio");
        }
        if (StringUtils.hasText(config.getCosSecretId())
                && StringUtils.hasText(config.getCosSecretKey())
                && StringUtils.hasText(config.getCosRegion())
                && StringUtils.hasText(config.getCosBucket())) {
            types.add("cos");
        }
        if (StringUtils.hasText(config.getOssEndpoint())
                && StringUtils.hasText(config.getOssAccessKeyId())
                && StringUtils.hasText(config.getOssAccessKeySecret())
                && StringUtils.hasText(config.getOssBucket())) {
            types.add("oss");
        }
        return types;
    }

    private String normalizeType(String storageType) {
        return StringUtils.hasText(storageType) ? storageType.trim().toLowerCase() : "local";
    }

    private String defaultString(String value) {
        return value == null ? "" : value.trim();
    }
}
