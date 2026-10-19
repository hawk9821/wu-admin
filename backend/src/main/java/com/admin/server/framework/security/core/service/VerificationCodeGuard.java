package com.admin.server.framework.security.core.service;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 验证码校验失败次数与频率保护。
 *
 * <p>短信、邮箱验证码本身只有 6 位，如果服务端不做失败计数，攻击者可以在
 * 验证码有效期内高频枚举并重置任意账号密码。这里统一按「业务场景 + 验证码主体」
 * 维度记录失败次数，达到上限后在窗口期内直接拒绝，不再继续比对验证码。</p>
 *
 * <p>IP 维度只做请求频率兜底，真正的防爆破依赖主体维度的失败次数。</p>
 */
@Component
public class VerificationCodeGuard {

    private static final String FAIL_KEY_PREFIX = "auth:verify:fail:";
    private static final String IP_KEY_PREFIX = "auth:verify:ip:";

    /** 同一验证码主体允许的最大失败次数 */
    private static final int MAX_FAILURES = 5;
    /** 失败计数窗口，与常见验证码有效期保持一致（重新发送不会清零，需等窗口结束） */
    private static final Duration FAIL_TTL = Duration.ofMinutes(5);
    /** 同一 IP 每分钟允许的验证请求上限 */
    private static final int MAX_ATTEMPTS_PER_IP_MINUTE = 20;

    @Resource
    private RedissonClient redissonClient;

    /**
     * IP 维度请求频率兜底。
     *
     * @param scope    业务场景，例如 sms-verify / pwd-reset
     * @param clientIp 客户端 IP
     * @return null 表示允许；否则返回提示语
     */
    public String checkIpLimit(String scope, String clientIp) {
        if (StrUtil.isBlank(clientIp)) {
            return null;
        }
        long minute = System.currentTimeMillis() / 60_000L;
        String key = IP_KEY_PREFIX + safe(scope) + ":" + clientIp.trim() + ":" + minute;
        RAtomicLong counter = redissonClient.getAtomicLong(key);
        long current = counter.incrementAndGet();
        if (current == 1) {
            counter.expire(Duration.ofSeconds(90));
        }
        if (current > MAX_ATTEMPTS_PER_IP_MINUTE) {
            return "验证过于频繁，请稍后再试";
        }
        return null;
    }

    /**
     * 检查验证码主体是否已因失败次数过多被限制。
     *
     * @param scope   业务场景
     * @param subject 验证码主体（手机号、邮箱或 user:{id}）
     * @return null 表示允许；否则返回提示语
     */
    public String check(String scope, String subject) {
        if (StrUtil.isBlank(subject)) {
            return null;
        }
        RAtomicLong counter = redissonClient.getAtomicLong(failKey(scope, subject));
        if (counter.get() >= MAX_FAILURES) {
            return "验证码错误次数过多，请重新获取验证码";
        }
        return null;
    }

    /**
     * 记录一次验证码校验失败。
     *
     * @return null 表示尚未达到上限；否则返回提示语
     */
    public String recordFailure(String scope, String subject) {
        if (StrUtil.isBlank(subject)) {
            return null;
        }
        RAtomicLong counter = redissonClient.getAtomicLong(failKey(scope, subject));
        long current = counter.incrementAndGet();
        if (current == 1) {
            counter.expire(FAIL_TTL);
        }
        if (current >= MAX_FAILURES) {
            return "验证码错误次数过多，请重新获取验证码";
        }
        return null;
    }

    /**
     * 验证码校验成功时清除失败计数。
     * <p>重新发送验证码不要调用本方法，否则攻击者可通过反复重发重置失败次数。</p>
     */
    public void clear(String scope, String subject) {
        if (StrUtil.isBlank(subject)) {
            return;
        }
        redissonClient.getBucket(failKey(scope, subject)).delete();
    }

    private String failKey(String scope, String subject) {
        return FAIL_KEY_PREFIX + safe(scope) + ":" + subject.trim();
    }

    private String safe(String value) {
        return StrUtil.isBlank(value) ? "default" : value.trim();
    }
}
