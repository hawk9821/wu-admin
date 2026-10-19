package com.admin.server.modules.infra.api.file;

import com.admin.server.framework.security.api.RequirePermission;
import com.admin.server.framework.log.annotation.Log;
import com.admin.server.common.core.CommonResult;
import com.admin.server.modules.infra.dal.dataobject.file.SysFileGroupDO;
import com.admin.server.modules.infra.service.file.SysFileGroupService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.Map;

@Tag(name = "文件分组")
@RestController
@RequestMapping("/system/file-group")
public class SysFileGroupController {

    @Resource
    private SysFileGroupService fileGroupService;

    @GetMapping("/list")
    @RequirePermission(value = "sys:file:list", type = RequirePermission.Type.READ)
    public CommonResult<Map<String, Object>> list(
            @RequestParam(required = false) String fileCategory) {
        return CommonResult.success(fileGroupService.listWithUngroupedCount(fileCategory));
    }

    @Log(title = "文件分组", businessType = Log.BusinessType.INSERT)
    @PostMapping
    @RequirePermission("sys:file:upload")
    public CommonResult<Boolean> create(@RequestBody SysFileGroupDO group) {
        fileGroupService.create(group);
        return CommonResult.success(true);
    }

    @Log(title = "文件分组", businessType = Log.BusinessType.UPDATE)
    @PutMapping
    @RequirePermission("sys:file:upload")
    public CommonResult<Boolean> update(@RequestBody SysFileGroupDO group) {
        fileGroupService.update(group);
        return CommonResult.success(true);
    }

    @Log(title = "文件分组", businessType = Log.BusinessType.DELETE)
    @DeleteMapping("/{id}")
    @RequirePermission("sys:file:delete")
    public CommonResult<Boolean> delete(@PathVariable Long id) {
        fileGroupService.delete(id);
        return CommonResult.success(true);
    }
}
