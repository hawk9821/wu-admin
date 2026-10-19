package com.admin.server.framework.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 补齐原 Spring Security {@code HeaderWriterFilter} 默认写入的响应安全头。
 * <p>原配置仅关闭了 {@code frameOptions}（由 {@link Knife4jIframeHeaderFilter} 按路径单独设置），
 * 其余默认头在此按原值输出，避免响应头行为发生变化。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-XSS-Protection", "0");
        response.setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
        if (request.isSecure()) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains");
        }
        filterChain.doFilter(request, response);
    }
}
