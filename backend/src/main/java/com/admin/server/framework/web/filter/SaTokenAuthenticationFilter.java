package com.admin.server.framework.web.filter;

import cn.dev33.satoken.stp.StpUtil;
import com.admin.server.framework.security.core.AuthTokenResolver;
import com.admin.server.framework.security.core.SecurityPathRules;
import com.admin.server.framework.web.core.ApiErrorResponseWriter;
import com.admin.server.modules.infra.service.monitor.OnlineUserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 统一登录态入口（替代 Spring Security 的 {@code anyRequest().authenticated()} 兜底）。
 * <p>路径规则与改造前 {@code SecurityConfig} 一致：忽略路径完全放行，
 * 其余路径要求 Sa-Token 已登录，未登录返回与原来相同的 401 JSON；
 * 已登录请求继续刷新在线用户最后活跃时间。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SaTokenAuthenticationFilter extends OncePerRequestFilter {

    private final OnlineUserService onlineUserService;
    private final ApiErrorResponseWriter errorResponseWriter;

    public SaTokenAuthenticationFilter(OnlineUserService onlineUserService,
                                       ApiErrorResponseWriter errorResponseWriter) {
        this.onlineUserService = onlineUserService;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String path = SecurityPathRules.resolvePath(request);
        if (SecurityPathRules.isIgnored(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean loggedIn = false;
        // 过滤器链运行在 DispatcherServlet 之前，Sa-Token 的 Spring 适配层此时拿不到
        // RequestContextHolder，调用 StpUtil.isLogin() 会抛「非 web 上下文」。
        // 因此这里直接从请求头/Cookie 解析 Token，再用不依赖 Web 上下文的
        // getLoginIdByToken 校验登录态。
        String token = AuthTokenResolver.resolve(request);
        if (StringUtils.hasText(token)) {
            try {
                Object loginId = StpUtil.getLoginIdByToken(token);
                if (loginId != null) {
                    loggedIn = true;
                    // 在线活跃时间是旁路统计，不能因为 Redis 抖动把已登录用户误判成 401
                    try {
                        onlineUserService.touchLastAccess(Long.parseLong(loginId.toString()));
                    } catch (Exception e) {
                        log.debug("刷新在线活跃时间失败 [{}]: {}", request.getRequestURI(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.debug("Sa-Token 鉴权跳过 [{}]: {}", request.getRequestURI(), e.getMessage());
            }
        }

        if (!loggedIn && !SecurityPathRules.isPermitAll(request.getMethod(), path)) {
            errorResponseWriter.write(response, 401, "未登录或登录已过期");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
