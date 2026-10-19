package com.admin.server.framework.security.core.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.admin.server.framework.security.api.RequirePermission;
import com.admin.server.framework.security.api.PermissionApi;
import com.admin.server.framework.security.api.PermissionDeniedException;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * {@link RequirePermission} 的执行器：登录态由 Sa-Token 判定，权限码由 {@link PermissionApi} 判定。
 * <p>判定逻辑与改造前 {@code @PreAuthorize("@ss.hasXxx(...)")} 逐项对应，不改变任何权限语义。
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class RequirePermissionAspect {

    private final PermissionApi permissionApi;

    public RequirePermissionAspect(PermissionApi permissionApi) {
        this.permissionApi = permissionApi;
    }

    @Before("@annotation(requirePermission)")
    public void checkMethod(RequirePermission requirePermission) {
        check(requirePermission);
    }

    /** 兼容类级注解；方法上已有注解时不重复校验 */
    @Before("@within(requirePermission) && !@annotation(com.admin.server.framework.security.api.RequirePermission)")
    public void checkType(RequirePermission requirePermission) {
        check(requirePermission);
    }

    private void check(RequirePermission requirePermission) {
        // 未登录统一抛 NotLoginException，由全局异常处理器返回 401
        StpUtil.checkLogin();
        if (requirePermission.type() == RequirePermission.Type.LOGIN) {
            return;
        }
        boolean allowed = switch (requirePermission.type()) {
            case PERMISSION -> permissionApi.hasPermission(requirePermission.value());
            case READ -> permissionApi.hasRead(requirePermission.value());
            case RECYCLE_READ -> permissionApi.hasRecycleRead();
            case RECYCLE_RESTORE -> permissionApi.hasRecycleRestore(requirePermission.value());
            case RECYCLE_DELETE -> permissionApi.hasRecycleDelete(requirePermission.value());
            case ROLE -> permissionApi.hasRole(requirePermission.value());
            case LOGIN -> true;
        };
        if (!allowed) {
            throw new PermissionDeniedException("权限不足，无法访问");
        }
    }
}
