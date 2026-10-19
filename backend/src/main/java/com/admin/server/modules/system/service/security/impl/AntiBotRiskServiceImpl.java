package com.admin.server.modules.system.service.security.impl;

import cn.hutool.core.util.StrUtil;
import com.admin.server.modules.system.api.security.vo.RiskSubjectVO;
import com.admin.server.modules.system.dal.dataobject.security.SecurityEventDO;
import com.admin.server.modules.system.service.security.AntiBotRiskService;
import com.admin.server.modules.system.service.security.SecurityEventService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 行为风控与反爬防御核心服务实现
 */
@Slf4j
@Service
public class AntiBotRiskServiceImpl implements AntiBotRiskService {

    private static final String RISK_SCORE_PREFIX = "security:risk:score:";
    private static final String RISK_BLOCK_PREFIX = "security:risk:block:";
    private static final long RISK_SCORE_TTL_HOURS = 1;

    /**
     * 自动化爬虫与探测脚本常用 User-Agent 特征指纹库（对齐 Code Compass 规范）
     */
    private static final List<String> BLOCKED_USER_AGENTS = List.of(
            "python-requests",
            "curl/",
            "wget/",
            "scrapy/",
            "go-http-client",
            "aiohttp/",
            "httpx/",
            "libwww-perl",
            "headlesschrome",
            "phantomjs",
            "selenium",
            "puppeteer",
            "playwright"
    );

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SecurityEventService securityEventService;

    @Resource
    private com.admin.server.modules.system.service.config.SystemConfigHelper systemConfigHelper;

    @Override
    public boolean isBlocked(String ip) {
        if (StrUtil.isBlank(ip)) {
            return false;
        }
        String key = RISK_BLOCK_PREFIX + ip.trim();
        Boolean exists = stringRedisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public int addRisk(String principal, String ip, int delta, String eventType, String action,
                       String uri, String method, String userAgent, String reason) {
        if (StrUtil.isBlank(principal)) {
            principal = StrUtil.isNotBlank(ip) ? ip : "unknown";
        }
        principal = principal.trim();

        // 1. Redis 累加风险分
        String scoreKey = RISK_SCORE_PREFIX + principal;
        Long currentScoreLong = stringRedisTemplate.opsForValue().increment(scoreKey, delta);
        stringRedisTemplate.expire(scoreKey, RISK_SCORE_TTL_HOURS, TimeUnit.HOURS);
        int currentScore = currentScoreLong != null ? currentScoreLong.intValue() : delta;

        // 2. 检查是否触发自动封禁跃迁（阈值来自系统配置，默认 80 / 120）
        String effectiveAction = action;
        int blockScore = systemConfigHelper.getRiskBlockScore();
        int banScore = systemConfigHelper.getRiskBanScore();
        if (currentScore >= banScore) {
            blockPrincipal(principal, 86400, "风险分达 " + currentScore + "，触发自动封禁24小时");
            effectiveAction = "FORBIDDEN";
        } else if (currentScore >= blockScore && !isBlocked(principal)) {
            blockPrincipal(principal, 3600, "风险分达 " + currentScore + "，触发自动临时封禁1小时");
            effectiveAction = "FORBIDDEN";
        }

        // 3. 异步落库审计事件
        SecurityEventDO event = new SecurityEventDO();
        event.setEventType(eventType);
        event.setPrincipal(principal);
        event.setIpaddr(StrUtil.isNotBlank(ip) ? ip : principal);
        event.setRequestUri(StrUtil.nullToEmpty(uri));
        event.setRequestMethod(StrUtil.nullToEmpty(method));
        event.setUserAgent(StrUtil.sub(userAgent, 0, 500));
        event.setRiskDelta(delta);
        event.setTotalRiskScore(currentScore);
        event.setAction(effectiveAction);
        event.setReason(reason);
        event.setCreateTime(LocalDateTime.now());

        securityEventService.recordAsync(event);

        return currentScore;
    }

    @Override
    public int getRiskScore(String principal) {
        if (StrUtil.isBlank(principal)) {
            return 0;
        }
        String raw = stringRedisTemplate.opsForValue().get(RISK_SCORE_PREFIX + principal.trim());
        if (StrUtil.isBlank(raw)) {
            return 0;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void blockPrincipal(String principal, long durationSeconds, String reason) {
        if (StrUtil.isBlank(principal)) {
            return;
        }
        String key = RISK_BLOCK_PREFIX + principal.trim();
        stringRedisTemplate.opsForValue().set(key, StrUtil.blankToDefault(reason, "网络环境异常被系统风控封禁"), durationSeconds, TimeUnit.SECONDS);
        log.warn("风险主体已被封禁: principal={}, durationSeconds={}, reason={}", principal, durationSeconds, reason);
    }

    @Override
    public void unblockPrincipal(String principal) {
        if (StrUtil.isBlank(principal)) {
            return;
        }
        String p = principal.trim();
        stringRedisTemplate.delete(RISK_BLOCK_PREFIX + p);
        stringRedisTemplate.delete(RISK_SCORE_PREFIX + p);
        log.info("风险主体已成功解封: principal={}", p);
    }

    @Override
    public List<RiskSubjectVO> getActiveRiskList() {
        List<RiskSubjectVO> list = new ArrayList<>();
        // 使用 SCAN 增量遍历，避免 KEYS 命令在大 key 空间下阻塞 Redis
        try (Cursor<String> cursor = stringRedisTemplate.scan(
                ScanOptions.scanOptions().match(RISK_BLOCK_PREFIX + "*").count(256).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                if (StrUtil.isBlank(key) || key.length() <= RISK_BLOCK_PREFIX.length()) {
                    continue;
                }
                String principal = key.substring(RISK_BLOCK_PREFIX.length());
                Long expireSeconds = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
                String reason = stringRedisTemplate.opsForValue().get(key);

                RiskSubjectVO vo = new RiskSubjectVO();
                vo.setPrincipal(principal);
                vo.setBlocked(true);
                vo.setRemainingSeconds(expireSeconds != null ? Math.max(0, expireSeconds) : 0);
                vo.setReason(StrUtil.blankToDefault(reason, "系统自动封禁"));
                vo.setRiskScore(getRiskScore(principal));

                list.add(vo);
            }
        } catch (Exception e) {
            log.warn("扫描风险封禁名单失败", e);
            return Collections.emptyList();
        }

        list.sort((a, b) -> Long.compare(b.getRemainingSeconds(), a.getRemainingSeconds()));
        return list;
    }

    @Override
    public String matchBlockedUserAgent(String userAgent) {
        if (StrUtil.isBlank(userAgent)) {
            return null;
        }
        String lowerUa = userAgent.toLowerCase();
        for (String sig : BLOCKED_USER_AGENTS) {
            if (lowerUa.contains(sig)) {
                return sig;
            }
        }
        return null;
    }
}
