package com.admin.server.framework.storage;

/**
 * 私有对象存储文件的归属校验扩展点。
 * <p>
 * {@link StorageFileController} 只负责把对象流式返回，不感知业务归属。
 * 业务模块通过实现本接口，对特定对象前缀补充“当前登录用户是否有权读取”的判断，
 * 避免任意登录用户拿到聊天附件、文件中心文件的 URL 后直接下载。
 * </p>
 */
public interface StorageObjectAccessGuard {

    /**
     * 当前实现是否负责该对象 key。
     */
    boolean supports(String objectName);

    /**
     * 是否允许当前登录用户读取该对象。
     */
    boolean canRead(String objectName);

    /**
     * 带 bucket 的读取校验：多引擎下同名 objectKey 可能属于不同桶，
     * 默认实现保持向后兼容，具体实现可按 bucket 精确定位记录。
     */
    default boolean canRead(String bucketName, String objectName) {
        return canRead(objectName);
    }
}
