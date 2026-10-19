package com.admin.server.framework.security.api;

/**
 * 已登录但缺少所需权限（替代 Spring Security 的 {@code AccessDeniedException}）。
 * <p>由全局异常处理器统一转换为 HTTP 403 与 {@code {"code":403,"message":"权限不足，无法访问"}}，
 * 与改造前的响应结构保持一致。
 */
public class PermissionDeniedException extends RuntimeException {

    public PermissionDeniedException(String message) {
        super(message);
    }
}
