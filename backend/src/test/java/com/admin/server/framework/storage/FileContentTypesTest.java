package com.admin.server.framework.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("文件 MIME 类型解析与内联安全策略测试")
class FileContentTypesTest {

    @Test
    @DisplayName("扩展名优先于客户端上传的 Content-Type，防止伪造 text/html")
    void shouldPreferExtensionOverUploadedType() {
        assertEquals("image/jpeg", FileContentTypes.resolve("photo.jpg", "text/html", null));
        assertEquals("image/svg+xml", FileContentTypes.resolve("vector.svg", "image/png", null));
        assertEquals("application/pdf", FileContentTypes.resolve("doc.pdf", "application/octet-stream", null));
    }

    @Test
    @DisplayName("SVG 与 HTML 不允许内联，PDF 与常见媒体允许内联")
    void shouldNotInlineUnsafeTypes() {
        assertFalse(FileContentTypes.isInlineMime("image/svg+xml"));
        assertFalse(FileContentTypes.isInlineMime("text/html"));
        assertTrue(FileContentTypes.isInlineMime("application/pdf"));
        assertTrue(FileContentTypes.isInlineMime("image/png"));
        assertTrue(FileContentTypes.isInlineMime("video/mp4"));
        assertTrue(FileContentTypes.isInlineMime("audio/mpeg"));
    }
}
