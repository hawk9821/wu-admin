package com.admin.server.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("ClientIpUtils 单元测试")
class ClientIpUtilsTest {

    @Test
    @DisplayName("不可信来源直连时忽略伪造的代理头")
    void untrustedRemoteIgnoresSpoofedHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("8.8.8.8");
        request.addHeader("X-Real-IP", "1.2.3.4");
        request.addHeader("X-Forwarded-For", "5.6.7.8");
        request.addHeader("CF-Connecting-IP", "9.9.9.9");

        assertEquals("8.8.8.8", ClientIpUtils.resolve(request));
    }

    @Test
    @DisplayName("可信代理优先使用 Nginx 覆盖后的 X-Real-IP")
    void trustedProxyUsesRealIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP", "1.2.3.4");
        request.addHeader("X-Forwarded-For", "5.6.7.8");

        assertEquals("1.2.3.4", ClientIpUtils.resolve(request));
    }

    @Test
    @DisplayName("可信代理缺少 X-Real-IP 时取 X-Forwarded-For 最右段")
    void trustedProxyUsesRightmostForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "1.1.1.1, 2.2.2.2, 3.3.3.3");

        assertEquals("3.3.3.3", ClientIpUtils.resolve(request));
    }

    @Test
    @DisplayName("内网来源没有代理头时返回对端地址")
    void trustedProxyWithoutHeadersFallsBackToRemote() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.20");

        assertEquals("192.168.1.20", ClientIpUtils.resolve(request));
    }

    @Test
    @DisplayName("IPv4-mapped IPv6 地址归一为 IPv4")
    void ipv4MappedAddressIsNormalized() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("::ffff:192.168.1.10");

        assertEquals("192.168.1.10", ClientIpUtils.resolve(request));
    }

    @Test
    @DisplayName("IPv6 回环地址归一为 127.0.0.1")
    void ipv6LoopbackIsNormalized() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("::1");

        assertEquals("127.0.0.1", ClientIpUtils.resolve(request));
    }
}
