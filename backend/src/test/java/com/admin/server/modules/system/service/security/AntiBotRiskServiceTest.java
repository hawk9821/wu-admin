package com.admin.server.modules.system.service.security;

import com.admin.server.modules.system.dal.dataobject.security.SecurityEventDO;
import com.admin.server.modules.system.service.config.SystemConfigHelper;
import com.admin.server.modules.system.service.security.impl.AntiBotRiskServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AntiBotRiskService 单元测试")
class AntiBotRiskServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SecurityEventService securityEventService;

    @Mock
    private SystemConfigHelper systemConfigHelper;

    @InjectMocks
    private AntiBotRiskServiceImpl antiBotRiskService;

    @Test
    @DisplayName("matchBlockedUserAgent：准确识别自动化爬虫脚本 UA")
    void testMatchBlockedUserAgent() {
        assertNotNull(antiBotRiskService.matchBlockedUserAgent("python-requests/2.31.0"));
        assertNotNull(antiBotRiskService.matchBlockedUserAgent("curl/8.1.2"));
        assertNotNull(antiBotRiskService.matchBlockedUserAgent("Scrapy/2.11.0 (+https://scrapy.org)"));
        assertNotNull(antiBotRiskService.matchBlockedUserAgent("Mozilla/5.0 (compatible; Go-http-client/1.1)"));
        assertNotNull(antiBotRiskService.matchBlockedUserAgent("HeadlessChrome/118.0.5993.88"));

        // 正常浏览器 User-Agent 不应误报
        assertNull(antiBotRiskService.matchBlockedUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"));
        assertNull(antiBotRiskService.matchBlockedUserAgent(null));
        assertNull(antiBotRiskService.matchBlockedUserAgent(""));
    }

    @Test
    @DisplayName("isBlocked：正确检测 Redis 封禁标记")
    void testIsBlocked() {
        when(stringRedisTemplate.hasKey("security:risk:block:1.2.3.4")).thenReturn(true);
        when(stringRedisTemplate.hasKey("security:risk:block:5.6.7.8")).thenReturn(false);

        assertTrue(antiBotRiskService.isBlocked("1.2.3.4"));
        assertFalse(antiBotRiskService.isBlocked("5.6.7.8"));
        assertFalse(antiBotRiskService.isBlocked(null));
    }

    @Test
    @DisplayName("addRisk：累加风险分并在达到80分时自动触发临时封禁")
    void testAddRiskAutoBlock() {
        when(systemConfigHelper.getRiskBlockScore()).thenReturn(80);
        when(systemConfigHelper.getRiskBanScore()).thenReturn(120);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("security:risk:score:192.168.1.100", 40)).thenReturn(85L);
        when(stringRedisTemplate.hasKey("security:risk:block:192.168.1.100")).thenReturn(false);

        int total = antiBotRiskService.addRisk("192.168.1.100", "192.168.1.100", 40,
                "UA_BLOCK", "BLOCKED", "/api/test", "GET", "python-requests/2.28", "命中爬虫UA");

        assertEquals(85, total);
        // 验证自动触发封禁写入 Redis（1小时）
        verify(valueOperations).set(eq("security:risk:block:192.168.1.100"), anyString(), eq(3600L), eq(TimeUnit.SECONDS));
        // 验证异步落库调用
        verify(securityEventService).recordAsync(any(SecurityEventDO.class));
    }

    @Test
    @DisplayName("unblockPrincipal：成功清除封禁与风险积分缓存")
    void testUnblockPrincipal() {
        antiBotRiskService.unblockPrincipal("192.168.1.100");

        verify(stringRedisTemplate).delete("security:risk:block:192.168.1.100");
        verify(stringRedisTemplate).delete("security:risk:score:192.168.1.100");
    }
}
