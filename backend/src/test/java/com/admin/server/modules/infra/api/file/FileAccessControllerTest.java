package com.admin.server.modules.infra.api.file;

import com.admin.server.framework.storage.FileStorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileAccessControllerTest {

    @TempDir
    Path tempDir;

    private FileAccessController controller;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(tempDir.resolve("files"));
        Files.writeString(tempDir.resolve("files/private.txt"), "secret");
        Files.createDirectories(tempDir.resolve("public"));
        Files.writeString(tempDir.resolve("public/logo.txt"), "logo");

        FileStorageProperties properties = new FileStorageProperties();
        properties.setLocalPath(tempDir.toString());

        controller = new FileAccessController();
        ReflectionTestUtils.setField(controller, "fileStorageProperties", properties);
        ReflectionTestUtils.setField(controller, "accessGuards", List.of());
    }

    @Test
    @DisplayName("public/../files 路径穿越返回 403")
    void rejectsPlainTraversal() throws Exception {
        assertEquals(HttpStatus.FORBIDDEN,
                request("/api/files/public/../files/private.txt").getStatusCode());
    }

    @Test
    @DisplayName("public/%2e%2e/files 编码穿越返回 403")
    void rejectsEncodedTraversal() throws Exception {
        assertEquals(HttpStatus.FORBIDDEN,
                request("/api/files/public/%2e%2e/files/private.txt").getStatusCode());
    }

    @Test
    @DisplayName("public/ 目录正常访问返回 200")
    void servesPublicFile() throws Exception {
        assertEquals(HttpStatus.OK,
                request("/api/files/public/logo.txt").getStatusCode());
    }

    private ResponseEntity<Resource> request(String uri) throws Exception {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRequestURI(uri);
        return controller.getFile(servletRequest);
    }
}
