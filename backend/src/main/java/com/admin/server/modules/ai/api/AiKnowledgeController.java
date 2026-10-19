package com.admin.server.modules.ai.api;

import com.admin.server.common.core.CommonResult;
import com.admin.server.common.core.PageResult;
import com.admin.server.framework.log.annotation.Log;
import com.admin.server.framework.security.api.RequirePermission;
import com.admin.server.modules.ai.api.vo.*;
import com.admin.server.modules.ai.service.AiKnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Tag(name = "AI 知识库")
@RestController
@RequestMapping("/system/ai-knowledge")
public class AiKnowledgeController {

    @Resource
    private AiKnowledgeService aiKnowledgeService;

    @GetMapping("/page")
    @Operation(summary = "知识库分页")
    @RequirePermission(value = "system:ai-knowledge:list", type = RequirePermission.Type.READ)
    public CommonResult<PageResult<AiKnowledgeRespVO>> page(AiKnowledgePageReqVO reqVO) {
        return CommonResult.success(aiKnowledgeService.page(reqVO));
    }

    @GetMapping("/{id}")
    @Operation(summary = "知识详情")
    @RequirePermission(value = "system:ai-knowledge:query", type = RequirePermission.Type.READ)
    public CommonResult<AiKnowledgeRespVO> detail(@PathVariable Long id) {
        return CommonResult.success(aiKnowledgeService.detail(id));
    }

    @PostMapping
    @Operation(summary = "新增知识")
    @RequirePermission("system:ai-knowledge:create")
    @Log(title = "AI知识库", businessType = Log.BusinessType.INSERT)
    public CommonResult<Boolean> create(@Valid @RequestBody AiKnowledgeSaveReqVO reqVO) {
        aiKnowledgeService.create(reqVO);
        return CommonResult.success(true);
    }

    @PutMapping
    @Operation(summary = "修改知识")
    @RequirePermission("system:ai-knowledge:update")
    @Log(title = "AI知识库", businessType = Log.BusinessType.UPDATE)
    public CommonResult<Boolean> update(@Valid @RequestBody AiKnowledgeSaveReqVO reqVO) {
        aiKnowledgeService.update(reqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除知识")
    @RequirePermission("system:ai-knowledge:delete")
    @Log(title = "AI知识库", businessType = Log.BusinessType.DELETE)
    public CommonResult<Boolean> delete(@PathVariable Long id) {
        aiKnowledgeService.delete(id);
        return CommonResult.success(true);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "修改知识状态")
    @RequirePermission("system:ai-knowledge:update")
    @Log(title = "AI知识库", businessType = Log.BusinessType.UPDATE)
    public CommonResult<Boolean> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        aiKnowledgeService.updateStatus(id, status);
        return CommonResult.success(true);
    }

    @PutMapping("/batch-status")
    @Operation(summary = "批量修改知识状态")
    @RequirePermission("system:ai-knowledge:update")
    @Log(title = "AI知识库", businessType = Log.BusinessType.UPDATE)
    public CommonResult<Boolean> batchUpdateStatus(@Valid @RequestBody AiKnowledgeBatchStatusReqVO reqVO) {
        aiKnowledgeService.batchUpdateStatus(reqVO.getIds(), reqVO.getStatus());
        return CommonResult.success(true);
    }

    @DeleteMapping("/batch")
    @Operation(summary = "批量删除知识")
    @RequirePermission("system:ai-knowledge:delete")
    @Log(title = "AI知识库", businessType = Log.BusinessType.DELETE)
    public CommonResult<Boolean> batchDelete(@RequestBody List<Long> ids) {
        aiKnowledgeService.batchDelete(ids);
        return CommonResult.success(true);
    }

    @PostMapping("/test-retrieve")
    @Operation(summary = "知识库检索演练与召回测试")
    @RequirePermission(value = "system:ai-knowledge:query", type = RequirePermission.Type.READ)
    public CommonResult<AiKnowledgeTestRetrieveRespVO> testRetrieve(@Valid @RequestBody AiKnowledgeTestRetrieveReqVO reqVO) {
        return CommonResult.success(aiKnowledgeService.testRetrieve(reqVO));
    }

    @PostMapping("/batch-import")
    @Operation(summary = "批量导入知识")
    @RequirePermission("system:ai-knowledge:create")
    @Log(title = "AI知识库", businessType = Log.BusinessType.IMPORT)
    public CommonResult<Boolean> batchImport(@RequestBody List<AiKnowledgeSaveReqVO> list) {
        aiKnowledgeService.batchImport(list);
        return CommonResult.success(true);
    }

    @PostMapping("/upload-md")
    @Operation(summary = "上传 Markdown (.md) 文档")
    @RequirePermission("system:ai-knowledge:create")
    @Log(title = "AI知识库", businessType = Log.BusinessType.IMPORT)
    public CommonResult<Map<String, Object>> uploadMd(@RequestParam("files") MultipartFile[] files) {
        return CommonResult.success(aiKnowledgeService.uploadMd(files));
    }

    @GetMapping("/stats")
    @Operation(summary = "获取知识库统计指标")
    @RequirePermission(value = "system:ai-knowledge:list", type = RequirePermission.Type.READ)
    public CommonResult<Map<String, Object>> getStats() {
        return CommonResult.success(aiKnowledgeService.getStats());
    }

    @GetMapping("/export")
    @Operation(summary = "导出全部知识")
    @RequirePermission(value = "system:ai-knowledge:query", type = RequirePermission.Type.READ)
    public CommonResult<List<AiKnowledgeRespVO>> exportAll() {
        return CommonResult.success(aiKnowledgeService.exportAll());
    }
}
