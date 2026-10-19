package com.admin.server.framework.security.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityPathRulesTest {

    @Test
    @DisplayName("public/ 目录保持匿名可读")
    void publicFilesRemainAnonymous() {
        assertTrue(SecurityPathRules.isPermitAll("GET", "/files/public/logo.png"));
        assertTrue(SecurityPathRules.isPermitAll("GET", "/storage/files/wu-admin/public/logo.png"));
        assertTrue(SecurityPathRules.isPermitAll("GET", "/api/storage/files/wu-admin/public/logo.png"));
    }

    @Test
    @DisplayName("public/../ 路径穿越不允许匿名放行")
    void traversalInsidePublicPathRequiresLogin() {
        assertFalse(SecurityPathRules.isPermitAll("GET", "/files/public/../files/private.pdf"));
        assertFalse(SecurityPathRules.isPermitAll("GET", "/files/public/%2e%2e/files/private.pdf"));
        assertFalse(SecurityPathRules.isPermitAll(
                "GET", "/storage/files/wu-admin/public/../files/private.pdf"));
        assertFalse(SecurityPathRules.isPermitAll(
                "GET", "/storage/files/wu-admin/public/%2e%2e/files/private.pdf"));
    }

    @Test
    @DisplayName("私有文件路径必须登录")
    void privateFilesRequireLogin() {
        assertFalse(SecurityPathRules.isPermitAll("GET", "/files/files/documents/20261019/report.pdf"));
        assertFalse(SecurityPathRules.isPermitAll("GET", "/files/chat/images/20261019/a.png"));
        assertFalse(SecurityPathRules.isPermitAll("GET", "/storage/files/wu-admin/files/documents/a.pdf"));
        assertFalse(SecurityPathRules.isPermitAll("GET", "/api/storage/files/wu-admin/chat/files/a.pdf"));
    }

    @Test
    @DisplayName("支付回调等非文件路径保持原有放行规则")
    void nonFilePermitAllPathsUnchanged() {
        assertTrue(SecurityPathRules.isPermitAll("POST", "/pay/notify/wechat"));
        assertFalse(SecurityPathRules.isPermitAll("GET", "/system/user/page"));
    }
}
