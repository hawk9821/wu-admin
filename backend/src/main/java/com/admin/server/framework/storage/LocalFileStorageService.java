package com.admin.server.framework.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地磁盘文件存储驱动实现
 */
@Slf4j
public class LocalFileStorageService implements FileStorageService {

    private final Path localStorageDir;
    private final String customDomain;

    public LocalFileStorageService(String customPath, String customDomain) {
        this.localStorageDir = resolveStorageDir(customPath);
        this.customDomain = StringUtils.hasText(customDomain) ? customDomain.trim().replaceAll("/+$", "") : null;
        try {
            Files.createDirectories(this.localStorageDir);
        } catch (IOException e) {
            log.warn("初始化本地存储目录失败 {}: {}", this.localStorageDir, e.getMessage());
        }
    }

    @Override
    public String uploadFile(String bucketName, String objectName, InputStream inputStream, String contentType) {
        String safeName = normalizeObjectName(objectName);
        try {
            Path target = resolvePath(safeName);
            Files.createDirectories(target.getParent());
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);

            if (StringUtils.hasText(customDomain)) {
                String cleanDomain = customDomain.replaceAll("/+$", "");
                return cleanDomain + "/" + safeName;
            }
            return StorageObjectKeyBuilder.storageFileUrl(StringUtils.hasText(bucketName) ? bucketName : "local", safeName);
        } catch (IOException e) {
            throw new IllegalStateException("本地磁盘写入失败: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream getFile(String bucketName, String objectName) {
        String safeName = normalizeObjectName(objectName);
        Path target = resolvePath(safeName);
        if (Files.exists(target)) {
            try {
                return Files.newInputStream(target);
            } catch (IOException e) {
                log.warn("本地文件流读取失败: {}", e.getMessage());
                return null;
            }
        }

        // 回退兼容相对根目录搜索
        Path directTarget = resolveLegacyFallback(safeName);
        if (directTarget != null && Files.exists(directTarget)) {
            try {
                return Files.newInputStream(directTarget);
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    @Override
    public byte[] getFileBytes(String bucketName, String objectName) {
        String safeName = normalizeObjectName(objectName);
        Path target = resolvePath(safeName);
        if (Files.exists(target)) {
            try {
                return Files.readAllBytes(target);
            } catch (IOException e) {
                throw new IllegalStateException("读取本地文件字节失败: " + e.getMessage(), e);
            }
        }
        return new byte[0];
    }

    @Override
    public long getFileSize(String bucketName, String objectName) {
        String safeName = normalizeObjectName(objectName);
        Path target = resolvePath(safeName);
        try {
            if (Files.isRegularFile(target)) {
                return Files.size(target);
            }
            Path directTarget = resolveLegacyFallback(safeName);
            if (directTarget != null && Files.isRegularFile(directTarget)) {
                return Files.size(directTarget);
            }
        } catch (IOException e) {
            log.warn("读取本地文件大小失败: {}", e.getMessage());
        }
        return -1L;
    }

    @Override
    public void deleteFile(String bucketName, String objectName) {
        String safeName = normalizeObjectName(objectName);
        Path target = resolvePath(safeName);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            // 删除失败必须让上层感知，否则数据库记录会被误删、物理文件变成孤儿
            throw new IllegalStateException("本地文件删除失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getStorageType() {
        return "local";
    }

    @Override
    public boolean testConnection() {
        return testConnectionDetail() == null;
    }

    @Override
    public String testConnectionDetail() {
        try {
            Files.createDirectories(localStorageDir);
            Path testFile = localStorageDir.resolve(".probe_" + System.currentTimeMillis());
            Files.writeString(testFile, "test_probe");
            Files.deleteIfExists(testFile);
            return null;
        } catch (Exception e) {
            log.warn("本地存储连通性自测失败: {}", e.getMessage());
            return "本地存储目录无写入权限或创建失败: " + e.getMessage();
        }
    }

    public String getStoragePath() {
        return this.localStorageDir.toAbsolutePath().normalize().toString().replace("\\", "/");
    }

    private Path resolvePath(String objectName) {
        Path target = localStorageDir.resolve(objectName).normalize();
        if (!target.startsWith(localStorageDir.normalize())) {
            throw new SecurityException("非法存储路径访问: " + objectName);
        }
        return target;
    }

    private Path resolveLegacyFallback(String safeName) {
        Path base = Paths.get("data", "uploads").toAbsolutePath().normalize();
        Path target = base.resolve(safeName).normalize();
        return target.startsWith(base) ? target : null;
    }

    private String normalizeObjectName(String objectName) {
        if (!StringUtils.hasText(objectName)) {
            throw new IllegalArgumentException("对象名称不能为空");
        }
        String clean = objectName.replace("\\", "/");
        while (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        // 任何包含 .. 的路径段都拒绝，避免回退分支绕过主目录边界
        if (clean.contains("..")) {
            throw new IllegalArgumentException("非法的对象路径: " + objectName);
        }
        return clean;
    }

    private Path resolveStorageDir(String customPath) {
        if (StringUtils.hasText(customPath)) {
            Path p = Paths.get(customPath);
            return p.isAbsolute() ? p.normalize() : Paths.get(".").resolve(p).normalize().toAbsolutePath();
        }
        return Paths.get("./data/uploads").normalize().toAbsolutePath();
    }
}
