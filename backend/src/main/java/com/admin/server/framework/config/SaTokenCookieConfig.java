package com.admin.server.framework.config;

import cn.dev33.satoken.config.SaTokenConfig;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Sa-Token 写入 httpOnly Cookie，避免前端 localStorage 暴露 Token。
 *
 * <p>注意：Sa-Token 1.39 只从 {@link SaTokenConfig#getCookie()} 读取 Cookie 属性，
 * 不会注入容器中单独声明的 {@code SaCookieConfig} Bean。因此这里必须修改
 * {@link SaTokenConfig} 内部的 Cookie 配置，否则 HttpOnly / SameSite / Secure 不会生效。
 */
@Configuration
public class SaTokenCookieConfig {

    private final SaTokenConfig saTokenConfig;
    private final boolean cookieSecure;

    public SaTokenCookieConfig(SaTokenConfig saTokenConfig,
                               @Value("${app.security.cookie-secure:false}") boolean cookieSecure) {
        this.saTokenConfig = saTokenConfig;
        this.cookieSecure = cookieSecure;
    }

    @PostConstruct
    public void customizeCookie() {
        saTokenConfig.getCookie()
                .setPath("/")
                .setHttpOnly(true)
                .setSecure(cookieSecure)
                .setSameSite("Lax");
    }
}
