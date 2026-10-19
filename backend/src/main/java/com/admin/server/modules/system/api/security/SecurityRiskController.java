package com.admin.server.modules.system.api.security;

import com.admin.server.common.core.CommonResult;
import com.admin.server.common.core.PageResult;
import com.admin.server.framework.log.annotation.Log;
import com.admin.server.framework.security.api.RequirePermission;
import com.admin.server.modules.system.api.security.vo.RiskSubjectVO;
import com.admin.server.modules.system.dal.dataobject.security.SecurityEventDO;
import com.admin.server.modules.system.service.security.AntiBotRiskService;
import com.admin.server.modules.system.service.security.SecurityEventService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 安全审计与反爬风控 Controller
 */
@Tag(name = "安全审计与反爬风控")
@RestController
@RequestMapping("/system/security")
public class SecurityRiskController {

    @Resource
    private SecurityEventService securityEventService;

    @Resource
    private AntiBotRiskService antiBotRiskService;

    @Operation(summary = "获取安全拦截事件列表")
    @GetMapping("/events")
    @RequirePermission("system:security:query")
    public CommonResult<PageResult<SecurityEventDO>> listEvents(
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String ipaddr,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String action) {
        Page<SecurityEventDO> page = securityEventService.getPage(pageNo, pageSize, ipaddr, eventType, action);
        return CommonResult.success(PageResult.of(page.getRecords(), page.getTotal()));
    }

    @Operation(summary = "获取安全拦截大盘统计数据")
    @GetMapping("/stats")
    @RequirePermission("system:security:query")
    public CommonResult<Map<String, Object>> getStats() {
        return CommonResult.success(securityEventService.getDashboardStats());
    }

    @Operation(summary = "获取当前处于封禁中的风险名单")
    @GetMapping("/risk-list")
    @RequirePermission("system:security:query")
    public CommonResult<List<RiskSubjectVO>> getRiskList() {
        return CommonResult.success(antiBotRiskService.getActiveRiskList());
    }

    @Log(title = "安全风控", businessType = Log.BusinessType.UPDATE)
    @Operation(summary = "一键解除主体封禁")
    @PostMapping("/unblock")
    @RequirePermission("system:security:unblock")
    public CommonResult<Boolean> unblock(@RequestParam String principal) {
        antiBotRiskService.unblockPrincipal(principal);
        return CommonResult.success(true);
    }

    @Log(title = "安全风控", businessType = Log.BusinessType.INSERT)
    @Operation(summary = "手动封禁主体")
    @PostMapping("/block")
    @RequirePermission("system:security:block")
    public CommonResult<Boolean> block(@RequestParam String principal,
                                       @RequestParam(defaultValue = "3600") Long durationSeconds,
                                       @RequestParam(defaultValue = "管理员手动封禁") String reason) {
        antiBotRiskService.blockPrincipal(principal, durationSeconds, reason);
        return CommonResult.success(true);
    }

    @Log(title = "安全风控", businessType = Log.BusinessType.DELETE)
    @Operation(summary = "删除安全事件")
    @DeleteMapping("/events/{id}")
    @RequirePermission("system:security:delete")
    public CommonResult<Boolean> delete(@PathVariable Long id) {
        securityEventService.delete(id);
        return CommonResult.success(true);
    }

    @Log(title = "安全风控", businessType = Log.BusinessType.DELETE, isSaveRequestData = false)
    @Operation(summary = "清空安全事件日志")
    @DeleteMapping("/events/clear")
    @RequirePermission("system:security:clear")
    public CommonResult<Boolean> clear() {
        securityEventService.clear();
        return CommonResult.success(true);
    }
}
