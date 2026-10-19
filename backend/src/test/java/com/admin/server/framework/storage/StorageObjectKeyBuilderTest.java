package com.admin.server.framework.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("对象存储链路生成工具类测试")
class StorageObjectKeyBuilderTest {

    @Test
    @DisplayName("测试用户头像存储链路生成")
    void testUserAvatarPath() {
        String path = StorageObjectKeyBuilder.userAvatar(1001L, "admin", "my-avatar.png");
        assertNotNull(path);
        assertTrue(path.startsWith("users/admin/avatar/avatar_"));
        assertTrue(path.endsWith(".png"));

        String fallbackPath = StorageObjectKeyBuilder.userAvatar(1002L, null, "test.jpg");
        assertTrue(fallbackPath.startsWith("users/1002/avatar/avatar_"));
        assertTrue(fallbackPath.endsWith(".jpg"));
    }

    @Test
    @DisplayName("测试通用业务文件分层链路生成")
    void testSysFilePath() {
        String imgPath = StorageObjectKeyBuilder.sysFile(null, "logo.png");
        assertTrue(imgPath.startsWith("files/images/"));
        assertTrue(imgPath.endsWith("_logo.png"));

        String docPath = StorageObjectKeyBuilder.sysFile(null, "project_summary.pdf");
        assertTrue(docPath.startsWith("files/documents/"));
        assertTrue(docPath.endsWith("_project_summary.pdf"));

        String zipPath = StorageObjectKeyBuilder.sysFile(null, "backup.zip");
        assertTrue(zipPath.startsWith("files/archives/"));
        assertTrue(zipPath.endsWith("_backup.zip"));
    }

    @Test
    @DisplayName("测试企业IM聊天图片与附件链路")
    void testChatFilePath() {
        String chatImg = StorageObjectKeyBuilder.chatImage("screenshot.png");
        assertTrue(chatImg.startsWith("chat/images/"));
        assertTrue(chatImg.endsWith("_screenshot.png"));

        String chatFile = StorageObjectKeyBuilder.chatFile("data_table.xlsx");
        assertTrue(chatFile.startsWith("chat/files/"));
        assertTrue(chatFile.endsWith("_data_table.xlsx"));
    }

    @Test
    @DisplayName("测试文件名与路径片段安全过滤（防目录穿越）")
    void testSanitization() {
        String cleanName = StorageObjectKeyBuilder.sanitizeFilename("../../../etc/passwd");
        assertFalse(cleanName.contains("/"));
        assertFalse(cleanName.contains("\\"));
        assertEquals("passwd", cleanName);

        String segment = StorageObjectKeyBuilder.sanitizeSegment("../../Dept#1");
        assertFalse(segment.contains("/"));
        assertFalse(segment.contains("#"));
        assertTrue(segment.contains("dept_1"));
    }

    @Test
    @DisplayName("测试统一流式访问 URL 构建")
    void testStorageFileUrl() {
        String url = StorageObjectKeyBuilder.storageFileUrl("wu-admin", "users/admin/avatar/avatar_123.jpg");
        assertEquals("/api/storage/files/wu-admin/users/admin/avatar/avatar_123.jpg", url);
    }

    @Test
    @DisplayName("中文、空格与 URL 保留字符应生成安全 ASCII 对象 key")
    void testUnsafeFilenameBecomesSafeAscii() {
        assertEquals("file.pdf", StorageObjectKeyBuilder.sanitizeFilename("中文 文件.pdf"));
        assertEquals("a_b.png", StorageObjectKeyBuilder.sanitizeFilename("a#b?.png"));
        String longName = StorageObjectKeyBuilder.sanitizeFilename("a".repeat(300) + ".pdf");
        assertTrue(longName.length() <= 100);
        assertTrue(longName.endsWith(".pdf"));
    }

    @Test
    @DisplayName("流式访问 URL 应对中文与空格进行路径编码")
    void testStorageFileUrlEncodesPath() {
        String url = StorageObjectKeyBuilder.storageFileUrl(
                "wu-admin", "files/images/20261018/abc_中文 文件.pdf");
        assertTrue(url.contains("%E4%B8%AD"));
        assertTrue(url.contains("%20"));
    }
}
