package com.admin.server.modules.infra.api.file;

import com.admin.server.framework.storage.FileContentTypes;
import com.admin.server.framework.storage.FileStorageProperties;
import com.admin.server.framework.storage.StorageObjectAccessGuard;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 本地文件直链访问 GET /files/**
 */
@Tag(name = "本地文件访问")
@RestController
@RequestMapping("/files")
public class FileAccessController {

    @Resource
    private FileStorageProperties fileStorageProperties;

    @Resource
    private List<StorageObjectAccessGuard> accessGuards;

    @Operation(summary = "本地文件直链访问与预览")
    @GetMapping("/**")
    public ResponseEntity<org.springframework.core.io.Resource> getFile(HttpServletRequest request) throws IOException {
        String uri = request.getRequestURI();
        int idx = uri.indexOf("/files/");
        String relative = idx >= 0 ? uri.substring(idx + "/files/".length()) : "";
        int q = relative.indexOf('?');
        if (q >= 0) {
            relative = relative.substring(0, q);
        }
        int hash = relative.indexOf('#');
        if (hash >= 0) {
            relative = relative.substring(0, hash);
        }
        try {
            // getRequestURI() 返回未解码的原始路径，中文、空格、百分号文件名必须显式解码
            relative = UriUtils.decode(relative, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        // 统一分隔符并按路径段拒绝 ..，防止 public/../files 绕过归属校验
        String safeRelative = relative.replace('\\', '/');
        if (hasTraversalSegment(safeRelative)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String basePath = fileStorageProperties.getLocalPath();
        Path base = Paths.get(basePath).normalize().toAbsolutePath();
        Path full = base.resolve(safeRelative).normalize();
        if (!full.startsWith(base) || !Files.isRegularFile(full)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        // 使用归一化后的相对路径判断 public/ 前缀，避免原始路径中的 . / .. 段影响判断
        String normalizedRelative = base.relativize(full).toString().replace('\\', '/');
        // 旧直链与 /api/storage/files 共用同一套业务归属校验：
        // public/ 由 SecurityPathRules 匿名放行，其余对象必须登录且在当前数据范围内。
        if (!normalizedRelative.startsWith("public/") && !canReadPrivateObject(normalizedRelative)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String storageName = full.getFileName().toString();
        String probed = Files.probeContentType(full);
        String contentType = FileContentTypes.resolve(storageName, null, probed);

        String filenameParam = request.getParameter("filename");
        String dispositionParam = request.getParameter("disposition");
        boolean forceAttachment = "attachment".equalsIgnoreCase(dispositionParam)
                || FileContentTypes.shouldForceDownload(contentType);

        var builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600");
        if (forceAttachment) {
            String downloadName = StringUtils.hasText(filenameParam) ? filenameParam : storageName;
            ContentDisposition cd = ContentDisposition.attachment()
                    .filename(downloadName, StandardCharsets.UTF_8)
                    .build();
            builder.header(HttpHeaders.CONTENT_TYPE, "application/octet-stream");
            builder.header(HttpHeaders.CONTENT_DISPOSITION, cd.toString());
        }
        return builder.body(new FileSystemResource(full));
    }

    /**
     * 判断相对路径是否包含 .. 路径段，防止 public/../files 之类的绕过。
     */
    private boolean hasTraversalSegment(String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        for (String segment : path.split("/")) {
            if ("..".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 依次询问业务模块的归属校验器；没有任何校验器负责该前缀时，保持登录即可读的历史行为。
     * 旧路由只服务本地磁盘，无法确定 bucket，传 null 让守卫按 objectKey 匹配。
     */
    private boolean canReadPrivateObject(String objectName) {
        if (accessGuards == null || accessGuards.isEmpty()) {
            return true;
        }
        for (StorageObjectAccessGuard guard : accessGuards) {
            if (guard != null && guard.supports(objectName)) {
                return guard.canRead(null, objectName);
            }
        }
        return true;
    }
}
