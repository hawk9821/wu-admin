package com.admin.server.framework.security.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口级鉴权注解（替代 Spring Security 的 {@code @PreAuthorize}）。
 * <p>登录态由 Sa-Token 判定，权限码由 {@link PermissionApi} 从数据库角色/菜单计算，
 * 与改造前 {@code @PreAuthorize("@ss.hasXxx(...)")} 的判定结果完全一致。
 * <p>未登录抛出 {@code NotLoginException}（HTTP 401）；已登录但无权限抛出
 * {@link PermissionDeniedException}（HTTP 403）。
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** 权限码 / 角色编码，{@link Type#LOGIN}、{@link Type#RECYCLE_READ} 时可为空 */
    String value() default "";

    /** 校验类型，默认按权限码校验 */
    Type type() default Type.PERMISSION;

    enum Type {
        /** 等价于 {@code @ss.hasPermission('x')} */
        PERMISSION,
        /** 等价于 {@code @ss.hasRead('x')}：list/query/upload 任一即可 */
        READ,
        /** 等价于 {@code @ss.hasRecycleRead()} */
        RECYCLE_READ,
        /** 等价于 {@code @ss.hasRecycleRestore('x')} */
        RECYCLE_RESTORE,
        /** 等价于 {@code @ss.hasRecycleDelete('x')} */
        RECYCLE_DELETE,
        /** 等价于 {@code @ss.hasRole('x')} */
        ROLE,
        /** 等价于 {@code isAuthenticated()} */
        LOGIN
    }
}
