package com.admin.server.framework.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器配置（迁移自原 {@code SecurityConfig#passwordEncoder()}，实现与参数完全不变）。
 * <p>只依赖 {@code spring-security-crypto} 的 BCrypt 实现，不引入任何 Web 安全组件，
 * 保证存量密码哈希可继续校验，无需重置密码。
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
