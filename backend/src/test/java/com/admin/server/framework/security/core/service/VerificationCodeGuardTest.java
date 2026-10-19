package com.admin.server.framework.security.core.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VerificationCodeGuard 单元测试")
class VerificationCodeGuardTest {

    @Mock
    private RedissonClient redissonClient;

    @InjectMocks
    private VerificationCodeGuard verificationCodeGuard;

    @Test
    @DisplayName("失败次数达到 5 次后直接拒绝，不再继续比对验证码")
    void checkRejectsAfterMaxFailures() {
        RAtomicLong counter = mock(RAtomicLong.class);
        when(counter.get()).thenReturn(5L);
        when(redissonClient.getAtomicLong(anyString())).thenReturn(counter);

        assertEquals("验证码错误次数过多，请重新获取验证码",
                verificationCodeGuard.check("sms-login", "13800000000"));
    }

    @Test
    @DisplayName("失败次数未达上限时允许继续校验")
    void checkAllowsBelowMaxFailures() {
        RAtomicLong counter = mock(RAtomicLong.class);
        when(counter.get()).thenReturn(4L);
        when(redissonClient.getAtomicLong(anyString())).thenReturn(counter);

        assertNull(verificationCodeGuard.check("sms-login", "13800000000"));
    }

    @Test
    @DisplayName("记录第 5 次失败时返回上限提示")
    void recordFailureReturnsLimitMessageAtFifthFailure() {
        RAtomicLong counter = mock(RAtomicLong.class);
        when(counter.incrementAndGet()).thenReturn(5L);
        when(redissonClient.getAtomicLong(anyString())).thenReturn(counter);

        assertEquals("验证码错误次数过多，请重新获取验证码",
                verificationCodeGuard.recordFailure("email", "user@example.com"));
    }

    @Test
    @DisplayName("验证成功后清除失败计数")
    void clearDeletesFailCounter() {
        @SuppressWarnings("unchecked")
        RBucket<String> bucket = mock(RBucket.class);
        when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);

        verificationCodeGuard.clear("sms-reset", "user:1");

        verify(bucket).delete();
    }

    @Test
    @DisplayName("同一 IP 每分钟超过 20 次验证请求时拒绝")
    void checkIpLimitRejectsOverLimit() {
        RAtomicLong counter = mock(RAtomicLong.class);
        when(counter.incrementAndGet()).thenReturn(21L);
        when(redissonClient.getAtomicLong(anyString())).thenReturn(counter);

        assertEquals("验证过于频繁，请稍后再试",
                verificationCodeGuard.checkIpLimit("sms-verify", "1.2.3.4"));
    }
}
