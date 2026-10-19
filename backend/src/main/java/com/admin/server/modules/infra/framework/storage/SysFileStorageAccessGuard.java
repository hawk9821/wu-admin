package com.admin.server.modules.infra.framework.storage;

import com.admin.server.framework.security.core.service.SecurityUtils;
import com.admin.server.framework.storage.StorageObjectAccessGuard;
import com.admin.server.modules.infra.dal.dataobject.file.SysFileDO;
import com.admin.server.modules.infra.dal.mysql.file.SysFileMapper;
import com.admin.server.modules.system.service.permission.DataScopeService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * 文件中心与聊天附件的对象存储归属校验。
 * <p>
 * 这些对象在上传时都会写入 {@code sys_file}，因此可以按 object key 反查记录，
 * 再复用组织数据范围判断当前用户是否可见。对于未入库的历史直传对象，无法判定归属，
 * 保持可读以避免破坏存量链接。
 * </p>
 */
@Component
public class SysFileStorageAccessGuard implements StorageObjectAccessGuard {

    private static final String FILE_CENTER_PREFIX = "files/";
    private static final String CHAT_ROOT_PREFIX = "chat/";
    private static final String CHAT_IMAGE_LEGACY_PREFIX = "images/chat/";
    private static final String CHAT_FILE_LEGACY_PREFIX = "files/chat/";

    @Resource
    private SysFileMapper sysFileMapper;

    @Resource
    private DataScopeService dataScopeService;

    @Override
    public boolean supports(String objectName) {
        return objectName != null
                && (objectName.startsWith(FILE_CENTER_PREFIX)
                || objectName.startsWith(CHAT_ROOT_PREFIX)
                || objectName.startsWith(CHAT_IMAGE_LEGACY_PREFIX)
                || objectName.startsWith(CHAT_FILE_LEGACY_PREFIX));
    }

    @Override
    public boolean canRead(String objectName) {
        return canRead(null, objectName);
    }

    @Override
    public boolean canRead(String bucketName, String objectName) {
        Long userId = SecurityUtils.getLoginUserId();
        if (userId == null) {
            return false;
        }
        SysFileDO file = sysFileMapper.selectByFilePathIncludingDeleted(
                StringUtils.hasText(bucketName) ? bucketName.trim() : null, objectName);
        if (file == null) {
            // 历史直传对象没有 sys_file 记录：仅旧聊天前缀保持可读，其余私有前缀一律拒绝，
            // 避免攻击者用未入库的 objectKey 绕过归属校验。
            return isLegacyReadablePrefix(objectName);
        }
        // 逻辑删除后的文件不允许继续通过旧链接读取
        if (file.getDeleted() != null && file.getDeleted() == 1) {
            return false;
        }
        DataScopeService.DataScopeContext scope = dataScopeService.getCurrentScope();
        if (scope.all()) {
            return true;
        }
        if (file.getDeptId() != null && scope.deptIds().contains(file.getDeptId())) {
            return true;
        }
        String username = SecurityUtils.getLoginUsername();
        return scope.selfOnly() && StringUtils.hasText(username)
                && Objects.equals(file.getCreateBy(), username);
    }

    /**
     * 旧版本聊天附件曾经直接落盘、没有 sys_file 记录，为兼容存量链接保留只读白名单。
     */
    private boolean isLegacyReadablePrefix(String objectName) {
        return objectName != null
                && (objectName.startsWith(CHAT_IMAGE_LEGACY_PREFIX)
                || objectName.startsWith(CHAT_FILE_LEGACY_PREFIX));
    }
}
