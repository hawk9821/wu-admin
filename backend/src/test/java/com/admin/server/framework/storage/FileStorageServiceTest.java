package com.admin.server.framework.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("文件与对象存储服务集成测试")
class FileStorageServiceTest {

    @Test
    @DisplayName("本地存储服务: 上传、读取、字节获取与删除完整闭环")
    void testLocalFileStorageServiceLifecycle() throws Exception {
        LocalFileStorageService localService = new LocalFileStorageService("./target/test-storage", null);
        assertTrue(localService.testConnection(), "本地存储连通性自测应该通过");
        assertEquals("local", localService.getStorageType());

        String testContent = "Hello Wu-Admin Multi Storage Engine!";
        String objectName = "2026/10/18/test_sample.txt";

        // 1. 上传
        try (InputStream in = new ByteArrayInputStream(testContent.getBytes(StandardCharsets.UTF_8))) {
            String url = localService.uploadFile("wu-admin", objectName, in, "text/plain");
            assertNotNull(url);
            assertTrue(url.contains("test_sample.txt"));
        }

        // 2. 读取流
        try (InputStream readIn = localService.getFile("wu-admin", objectName)) {
            assertNotNull(readIn, "读取流不能为空");
            String readContent = new String(readIn.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(testContent, readContent);
        }

        // 3. 读取字节
        byte[] bytes = localService.getFileBytes("wu-admin", objectName);
        assertNotNull(bytes);
        assertEquals(testContent, new String(bytes, StandardCharsets.UTF_8));

        // 4. 删除
        localService.deleteFile("wu-admin", objectName);
        assertNull(localService.getFile("wu-admin", objectName), "删除后获取应为 null");
    }

    @Test
    @DisplayName("动态路由门面服务: 默认驱动、热重载与连通性自测")
    void testRoutingFileStorageServiceReload() {
        RoutingFileStorageService routingService = new RoutingFileStorageService();
        routingService.init();

        assertEquals("local", routingService.getStorageType());
        assertTrue(routingService.testConnection());

        // 热重载配置为本地路径并带有自定义域名
        StorageConfig localCustom = StorageConfig.builder()
                .type("local")
                .localPath("./target/test-storage-custom")
                .localDomain("https://static.wushij.com/files")
                .build();

        routingService.reload(localCustom);
        assertEquals("local", routingService.getStorageType());

        String testContent = "Dynamic Routing Test Content";
        try (InputStream in = new ByteArrayInputStream(testContent.getBytes(StandardCharsets.UTF_8))) {
            String url = routingService.uploadFile("default", "unit-test/sample.txt", in, "text/plain");
            assertTrue(url.startsWith("https://static.wushij.com/files/"));
        } catch (Exception e) {
            fail("上传测试异常: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("MinIO 驱动依赖与类加载完整性测试")
    void testMinioDriverClassLoading() {
        assertDoesNotThrow(() -> {
            Class<?> clazz = Class.forName("io.minio.MinioAsyncClient");
            assertNotNull(clazz);
        });
    }
}
