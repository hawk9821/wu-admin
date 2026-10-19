package com.admin.server.modules.infra.api.file;

import com.admin.server.framework.security.api.RequirePermission;
import com.admin.server.framework.log.annotation.Log;
import com.admin.server.common.exception.BusinessException;
import com.admin.server.common.core.CommonResult;
import com.admin.server.common.core.PageParam;
import com.admin.server.common.core.PageResult;
import com.admin.server.framework.storage.FileContentTypes;
import com.admin.server.modules.infra.dal.dataobject.file.SysFileDO;
import com.admin.server.modules.system.service.config.SystemConfigHelper;
import com.admin.server.modules.infra.service.file.SysFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Tag(name = "文件管理")
@RestController
@RequestMapping("/system/file")
public class SysFileController {

    @Resource
    private SysFileService fileService;

    @Resource
    private SystemConfigHelper systemConfigHelper;

    @GetMapping("/upload-policy")
    @Operation(summary = "上传策略（读取系统配置，保存后立即生效）")
    @RequirePermission(value = "sys:file:list", type = RequirePermission.Type.READ)
    public CommonResult<Map<String, Object>> uploadPolicy() {
        Map<String, Object> policy = new HashMap<>();
        policy.put("maxSizeMb", systemConfigHelper.getFileMaxSizeMb());
        policy.put("allowedExtensions", systemConfigHelper.getFileAllowedExtensions());
        policy.put("platformMaxMb", SystemConfigHelper.PLATFORM_MAX_FILE_MB);
        return CommonResult.success(policy);
    }

    @GetMapping("/page-by-group")
    @RequirePermission(value = "sys:file:list", type = RequirePermission.Type.READ)
    public CommonResult<PageResult<SysFileDO>> pageByGroup(
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Boolean ungrouped,
            @RequestParam(required = false) String fileCategory,
            @RequestParam(required = false) String originalName) {
        return CommonResult.success(
                fileService.pageByGroup(pageNo, pageSize, groupId, ungrouped, fileCategory, originalName));
    }

    @GetMapping("/{id}")
    @RequirePermission(value = "sys:file:list", type = RequirePermission.Type.READ)
    public CommonResult<SysFileDO> detail(@PathVariable Long id) {
        return CommonResult.success(fileService.getAccessibleById(id));
    }

    @Log(title = "文件管理", businessType = Log.BusinessType.INSERT, isSaveRequestData = false)
    @PostMapping("/upload")
    @RequirePermission("sys:file:upload")
    public CommonResult<SysFileDO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) Long groupId) {
        return CommonResult.success(fileService.upload(file, path, groupId));
    }

    @PostMapping("/upload/image")
    @RequirePermission("sys:file:upload")
    public CommonResult<SysFileDO> uploadImage(@RequestParam("file") MultipartFile file) {
        return CommonResult.success(fileService.uploadImage(file));
    }

    @GetMapping("/download/{id}")
    @RequirePermission(value = "sys:file:list", type = RequirePermission.Type.READ)
    public ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable Long id) throws IOException {
        SysFileDO file = fileService.getAccessibleById(id);
        org.springframework.core.io.Resource resource = fileService.openFileResource(id);
        String downloadName = StringUtils.hasText(file.getOriginalName())
                ? file.getOriginalName()
                : StringUtils.hasText(file.getFileName()) ? file.getFileName() : "download";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(downloadName, StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header(HttpHeaders.CONTENT_TYPE, contentTypeOf(file))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(resource);
    }

    @GetMapping("/preview/{id}")
    @RequirePermission(value = "sys:file:list", type = RequirePermission.Type.READ)
    public ResponseEntity<org.springframework.core.io.Resource> preview(@PathVariable Long id) throws IOException {
        SysFileDO file = fileService.getAccessibleById(id);
        org.springframework.core.io.Resource resource = fileService.openFileResource(id);
        String contentType = contentTypeOf(file);
        String fileName = StringUtils.hasText(file.getOriginalName())
                ? file.getOriginalName()
                : StringUtils.hasText(file.getFileName()) ? file.getFileName() : "download";

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600");
        if (FileContentTypes.shouldForceDownload(contentType)) {
            // SVG/HTML/可执行内容等一律下载，避免同源存储型 XSS
            builder.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
            builder.header(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment()
                            .filename(fileName, StandardCharsets.UTF_8)
                            .build()
                            .toString());
        } else {
            builder.header(HttpHeaders.CONTENT_TYPE, contentType);
        }
        return builder.body(resource);
    }

    private static String contentTypeOf(SysFileDO file) {
        String fileName = StringUtils.hasText(file.getOriginalName())
                ? file.getOriginalName()
                : file.getFileName();
        return FileContentTypes.resolve(fileName, file.getFileType(), null);
    }

    @GetMapping("/text/{id}")
    @RequirePermission(value = "sys:file:list", type = RequirePermission.Type.READ)
    public CommonResult<String> text(@PathVariable Long id) {
        SysFileDO file = fileService.getAccessibleById(id);
        if (file.getFileSize() != null && file.getFileSize() > 5 * 1024 * 1024) {
            throw new BusinessException(400, "文件过大，无法预览");
        }
        return CommonResult.success(new String(fileService.getFileBytes(id), StandardCharsets.UTF_8));
    }

    @Log(title = "文件管理", businessType = Log.BusinessType.DELETE)
    @DeleteMapping("/{id}")
    @RequirePermission("sys:file:delete")
    public CommonResult<Boolean> delete(@PathVariable Long id) {
        fileService.delete(id);
        return CommonResult.success(true);
    }

    @Log(title = "文件管理", businessType = Log.BusinessType.DELETE)
    @DeleteMapping("/batch")
    @RequirePermission("sys:file:delete")
    public CommonResult<Boolean> deleteBatch(@RequestBody Long[] ids) {
        fileService.deleteBatch(ids);
        return CommonResult.success(true);
    }

    @Log(title = "文件管理", businessType = Log.BusinessType.UPDATE)
    @PostMapping("/move")
    @RequirePermission("sys:file:upload")
    public CommonResult<Boolean> move(@RequestBody MoveFileRequest request) {
        fileService.moveToGroup(request.getFileIds(), request.getGroupId());
        return CommonResult.success(true);
    }

    @Log(title = "文件管理", businessType = Log.BusinessType.UPDATE)
    @PutMapping("/{id}/rename")
    @RequirePermission("sys:file:upload")
    public CommonResult<Boolean> rename(@PathVariable Long id, @RequestBody RenameRequest request) {
        fileService.rename(id, request.getNewName());
        return CommonResult.success(true);
    }

    @GetMapping("/recycle/page")
    @RequirePermission(type = RequirePermission.Type.RECYCLE_READ)
    @Operation(summary = "文件回收站分页")
    public CommonResult<PageResult<SysFileDO>> recyclePage(
            PageParam pageParam,
            @RequestParam(required = false) String originalName) {
        return CommonResult.success(fileService.recyclePage(pageParam, originalName));
    }

    @PutMapping("/restore")
    @RequirePermission(value = "sys:file:delete", type = RequirePermission.Type.RECYCLE_RESTORE)
    @Operation(summary = "恢复文件")
    public CommonResult<Boolean> restore(@RequestParam Long id) {
        fileService.restore(id);
        return CommonResult.success(true);
    }

    @DeleteMapping("/delete-permanent")
    @RequirePermission(value = "sys:file:delete", type = RequirePermission.Type.RECYCLE_DELETE)
    @Operation(summary = "彻底删除文件")
    public CommonResult<Boolean> deletePermanent(@RequestParam Long id) {
        fileService.deletePermanent(id);
        return CommonResult.success(true);
    }

    @Data
    public static class MoveFileRequest {
        private Long[] fileIds;
        private Long groupId;
    }

    @Data
    public static class RenameRequest {
        private String newName;
    }
}
