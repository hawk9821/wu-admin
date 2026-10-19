package com.admin.server.modules.infra.api.monitor;

import com.admin.server.framework.security.api.RequirePermission;
import com.admin.server.common.core.CommonResult;
import com.admin.server.common.core.PageParam;
import com.admin.server.common.core.PageResult;
import com.admin.server.framework.log.annotation.Log;
import com.admin.server.modules.infra.api.monitor.vo.JobTemplateVO;
import com.admin.server.modules.infra.api.monitor.vo.SysJobVO;
import com.admin.server.modules.infra.dal.dataobject.job.SysJobDO;
import com.admin.server.modules.infra.dal.dataobject.job.SysJobLogDO;
import com.admin.server.modules.infra.service.job.SysJobLogService;
import com.admin.server.modules.infra.service.job.SysJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "定时任务")
@RestController
@RequestMapping("/monitor/job")
public class SysJobController {

    @Resource
    private SysJobService jobService;
    @Resource
    private SysJobLogService jobLogService;

    @GetMapping("/overview")
    @RequirePermission("monitor:job:list")
    @Operation(summary = "任务与调度总览")
    public CommonResult<Map<String, Object>> overview() {
        return CommonResult.success(jobService.overview());
    }

    @GetMapping("/templates")
    @RequirePermission("monitor:job:list")
    @Operation(summary = "内置任务模板")
    public CommonResult<List<JobTemplateVO>> templates() {
        return CommonResult.success(jobService.templates());
    }

    @GetMapping("/checkCron")
    @RequirePermission("monitor:job:list")
    @Operation(summary = "校验 Cron 并预览下次执行时间")
    public CommonResult<Map<String, Object>> checkCron(@RequestParam String cronExpression) {
        return CommonResult.success(jobService.checkCron(cronExpression));
    }

    @GetMapping("/page")
    @RequirePermission("monitor:job:list")
    @Operation(summary = "分页查询定时任务")
    public CommonResult<PageResult<SysJobVO>> page(
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) String jobGroup,
            @RequestParam(required = false) Integer status) {
        return CommonResult.success(jobService.pageVo(pageNo, pageSize, jobName, jobGroup, status));
    }

    @GetMapping("/{id}")
    @RequirePermission("monitor:job:list")
    @Operation(summary = "任务详情")
    public CommonResult<SysJobDO> detail(@PathVariable Long id) {
        return CommonResult.success(jobService.getById(id));
    }

    @PostMapping
    @RequirePermission("monitor:job:add")
    @Log(title = "定时任务", businessType = Log.BusinessType.INSERT)
    @Operation(summary = "新增任务")
    public CommonResult<Boolean> create(@RequestBody SysJobDO job) {
        jobService.create(job);
        return CommonResult.success(true);
    }

    @PutMapping
    @RequirePermission("monitor:job:edit")
    @Log(title = "定时任务", businessType = Log.BusinessType.UPDATE)
    @Operation(summary = "更新任务")
    public CommonResult<Boolean> update(@RequestBody SysJobDO job) {
        jobService.update(job);
        return CommonResult.success(true);
    }

    @DeleteMapping("/{id}")
    @RequirePermission("monitor:job:delete")
    @Log(title = "定时任务", businessType = Log.BusinessType.DELETE)
    @Operation(summary = "删除任务")
    public CommonResult<Boolean> delete(@PathVariable Long id) {
        jobService.delete(id);
        return CommonResult.success(true);
    }

    @PutMapping("/changeStatus")
    @RequirePermission("monitor:job:edit")
    @Log(title = "定时任务", businessType = Log.BusinessType.UPDATE)
    @Operation(summary = "修改任务状态")
    public CommonResult<Boolean> changeStatus(@RequestBody StatusRequest request) {
        jobService.changeStatus(request.getId(), request.getStatus());
        return CommonResult.success(true);
    }

    @PostMapping("/run/{id}")
    @RequirePermission("monitor:job:edit")
    @Operation(summary = "立即执行")
    public CommonResult<Boolean> run(@PathVariable Long id) {
        jobService.run(id);
        return CommonResult.success(true);
    }

    @GetMapping("/log/statistics")
    @RequirePermission("monitor:job:list")
    @Operation(summary = "调度统计")
    public CommonResult<Map<String, Object>> logStatistics() {
        return CommonResult.success(jobLogService.statistics());
    }

    @GetMapping("/log/page")
    @RequirePermission("monitor:job:list")
    @Operation(summary = "调度日志分页")
    public CommonResult<PageResult<SysJobLogDO>> logPage(
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) String jobGroup,
            @RequestParam(required = false) Integer status) {
        return CommonResult.success(jobLogService.page(pageNo, pageSize, jobName, jobGroup, status));
    }

    @DeleteMapping("/log/clean")
    @RequirePermission("monitor:job:delete")
    @Log(title = "定时任务", businessType = Log.BusinessType.DELETE)
    @Operation(summary = "清空调度日志至回收站（可按任务筛选；无参数时清空全部）")
    public CommonResult<Boolean> cleanLog(
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) String jobGroup) {
        if (StringUtils.hasText(jobName) || StringUtils.hasText(jobGroup)) {
            jobLogService.cleanScope(jobName, jobGroup);
        } else {
            jobLogService.cleanAll();
        }
        return CommonResult.success(true);
    }

    @GetMapping("/log/recycle/page")
    @RequirePermission(type = RequirePermission.Type.RECYCLE_READ)
    @Operation(summary = "调度日志回收站分页")
    public CommonResult<PageResult<SysJobLogDO>> logRecyclePage(
            PageParam pageParam,
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) String jobGroup) {
        return CommonResult.success(jobLogService.recyclePage(pageParam, jobName, jobGroup));
    }

    @PutMapping("/log/restore")
    @RequirePermission(value = "monitor:job:delete", type = RequirePermission.Type.RECYCLE_RESTORE)
    @Log(title = "定时任务", businessType = Log.BusinessType.UPDATE)
    @Operation(summary = "恢复调度日志")
    public CommonResult<Boolean> restoreLog(@RequestParam Long id) {
        jobLogService.restore(id);
        return CommonResult.success(true);
    }

    @DeleteMapping("/log/delete-permanent")
    @RequirePermission(value = "monitor:job:delete", type = RequirePermission.Type.RECYCLE_DELETE)
    @Log(title = "定时任务", businessType = Log.BusinessType.DELETE)
    @Operation(summary = "彻底删除调度日志")
    public CommonResult<Boolean> deleteLogPermanent(@RequestParam Long id) {
        jobLogService.deletePermanent(id);
        return CommonResult.success(true);
    }

    @GetMapping("/recycle/page")
    @RequirePermission(type = RequirePermission.Type.RECYCLE_READ)
    @Operation(summary = "定时任务回收站分页")
    public CommonResult<PageResult<SysJobDO>> recyclePage(
            PageParam pageParam,
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) String jobGroup) {
        return CommonResult.success(jobService.recyclePage(pageParam, jobName, jobGroup));
    }

    @PutMapping("/restore")
    @RequirePermission(value = "monitor:job:delete", type = RequirePermission.Type.RECYCLE_RESTORE)
    @Log(title = "定时任务", businessType = Log.BusinessType.UPDATE)
    @Operation(summary = "恢复定时任务")
    public CommonResult<Boolean> restore(@RequestParam Long id) {
        jobService.restore(id);
        return CommonResult.success(true);
    }

    @DeleteMapping("/delete-permanent")
    @RequirePermission(value = "monitor:job:delete", type = RequirePermission.Type.RECYCLE_DELETE)
    @Log(title = "定时任务", businessType = Log.BusinessType.DELETE)
    @Operation(summary = "彻底删除定时任务")
    public CommonResult<Boolean> deletePermanent(@RequestParam Long id) {
        jobService.deletePermanent(id);
        return CommonResult.success(true);
    }

    @Data
    public static class StatusRequest {
        private Long id;
        private Integer status;
    }
}
