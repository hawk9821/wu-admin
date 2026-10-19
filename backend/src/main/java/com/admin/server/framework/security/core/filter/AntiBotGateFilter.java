package com.admin.server.framework.security.core.filter;

import com.admin.server.common.util.ClientIpUtils;
import com.admin.server.framework.web.core.ApiErrorResponseWriter;
import com.admin.server.modules.system.service.security.AntiBotRiskService;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * 全路径反爬门禁：IP 封禁 + 爬虫 UA 嗅探。
 *
 * <p>与 {@link ApiSecurityFilter} 分离，确保即使请求命中签名/加密排除路径
 * （例如 {@code /files} 文件直出、{@code /ws}、{@code /doc.html}），
 * 也无法绕过封禁和 UA 风控。</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class AntiBotGateFilter extends OncePerRequestFilter {

    /** 健康检查、站点图标、错误页不参与嗅探，避免监控探针被误伤 */
    private static final Set<String> SKIP_PREFIXES = Set.of(
            "/actuator",
            "/favicon.ico",
            "/error"
    );

    @Resource
    private AntiBotRiskService antiBotRiskService;

    @Resource
    private ApiErrorResponseWriter errorResponseWriter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        for (String prefix : SKIP_PREFIXES) {
            if (uri.startsWith(prefix) || uri.startsWith("/api" + prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientIp = ClientIpUtils.resolve(request);

        boolean blocked;
        try {
            blocked = antiBotRiskService.isBlocked(clientIp);
        } catch (Exception e) {
            // 风控存储故障时优先保证站点可用，不能让静态文件与登录页一起不可用
            log.warn("反爬门禁读取封禁状态失败，按放行处理: ip={}, reason={}", clientIp, e.getMessage());
            blocked = false;
        }
        if (blocked) {
            errorResponseWriter.write(response, 403,
                    "请求已被拒绝：当前网络环境风险过高已被系统临时限制，请稍后再试");
            return;
        }

        String userAgent = request.getHeader("User-Agent");
        String matchedUa = antiBotRiskService.matchBlockedUserAgent(userAgent);
        if (matchedUa != null) {
            try {
                antiBotRiskService.addRisk(clientIp, clientIp, 40, "UA_BLOCK", "BLOCKED",
                        request.getRequestURI(), request.getMethod(), userAgent,
                        "命中自动化脚本/爬虫指纹: " + matchedUa);
            } catch (Exception e) {
                log.warn("记录 UA 风险分失败: ip={}, reason={}", clientIp, e.getMessage());
            }
            errorResponseWriter.write(response, 403, "请求已被拒绝：检测到自动化爬虫工具访问");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
