package com.admin.server.framework.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * 统一对象与文件存储服务接口 (兼容本地存储、MinIO、阿里云 OSS、腾讯云 COS)
 */
public interface FileStorageService {

    /**
     * 上传文件
     *
     * @param bucketName  存储空间/桶名称 (本地存储可传空或指定子分类)
     * @param objectName  对象路径/文件名 (例如 2026/10/18/uuid.png)
     * @param inputStream 文件内容输入流
     * @param contentType 文件的 MIME 类型
     * @return 文件的可访问完整 URL 或相对访问路径
     */
    String uploadFile(String bucketName, String objectName, InputStream inputStream, String contentType);

    /**
     * 获取文件输入流
     *
     * @param bucketName 存储空间/桶名称
     * @param objectName 对象路径/文件名
     * @return 文件输入流 (文件不存在或读取失败返回 null)
     */
    InputStream getFile(String bucketName, String objectName);

    /**
     * 读取文件完整字节
     *
     * @param bucketName 存储空间/桶名称
     * @param objectName 对象路径/文件名
     * @return 文件字节数组 (失败抛出异常或返回 null)
     */
    byte[] getFileBytes(String bucketName, String objectName);

    /**
     * 删除文件
     *
     * @param bucketName 存储空间/桶名称
     * @param objectName 对象路径/文件名
     */
    void deleteFile(String bucketName, String objectName);

    /**
     * 获取文件大小（字节）
     *
     * @return 文件大小；无法获取或文件不存在时返回 -1
     */
    default long getFileSize(String bucketName, String objectName) {
        return -1L;
    }

    /**
     * 获取文件指定区间的输入流，用于 HTTP Range 断点播放。
     * <p>默认实现通过 skip 跳过前缀，对象存储驱动应覆盖为服务端 Range 读取，避免无谓的全量下载。</p>
     *
     * @param offset 起始偏移（从 0 开始）
     * @param length 需要读取的字节数；小于等于 0 表示读到结尾
     */
    default InputStream getFile(String bucketName, String objectName, long offset, long length) {
        InputStream inputStream = getFile(bucketName, objectName);
        if (inputStream == null) {
            return null;
        }
        try {
            long remaining = Math.max(0L, offset);
            while (remaining > 0) {
                long skipped = inputStream.skip(remaining);
                if (skipped <= 0) {
                    break;
                }
                remaining -= skipped;
            }
            return inputStream;
        } catch (IOException e) {
            try {
                inputStream.close();
            } catch (IOException ignored) {
                // 忽略关闭异常
            }
            throw new IllegalStateException("读取文件区间失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取当前存储引擎类型编码 (local / minio / cos / oss)
     */
    default String getStorageType() {
        return "local";
    }

    /**
     * 连通性自测
     */
    default boolean testConnection() {
        return testConnectionDetail() == null;
    }

    /**
     * 连通性测试并返回诊断信息
     *
     * @return 成功返回 null；失败返回具体原因描述
     */
    default String testConnectionDetail() {
        return null;
    }
}
