package com.admin.server.modules.system.service.security.impl;

import cn.hutool.core.util.StrUtil;
import com.admin.server.modules.system.dal.dataobject.security.SecurityEventDO;
import com.admin.server.modules.system.dal.mysql.security.SecurityEventMapper;
import com.admin.server.modules.system.service.security.AntiBotRiskService;
import com.admin.server.modules.system.service.security.SecurityEventService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 安全事件审计日志服务实现
 */
@Slf4j
@Service
public class SecurityEventServiceImpl implements SecurityEventService {

    @Resource
    private SecurityEventMapper securityEventMapper;

    @Lazy
    @Resource
    private AntiBotRiskService antiBotRiskService;

    @Override
    public Page<SecurityEventDO> getPage(Integer pageNo, Integer pageSize, String ipaddr, String eventType, String action) {
        int safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 500);
        Page<SecurityEventDO> page = new Page<>(safePageNo, safePageSize);
        LambdaQueryWrapper<SecurityEventDO> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(ipaddr)) {
            wrapper.like(SecurityEventDO::getIpaddr, ipaddr.trim());
        }
        if (StrUtil.isNotBlank(eventType)) {
            wrapper.eq(SecurityEventDO::getEventType, eventType.trim());
        }
        if (StrUtil.isNotBlank(action)) {
            wrapper.eq(SecurityEventDO::getAction, action.trim());
        }
        wrapper.orderByDesc(SecurityEventDO::getCreateTime);
        return securityEventMapper.selectPage(page, wrapper);
    }

    @Async
    @Override
    public void recordAsync(SecurityEventDO event) {
        if (event == null) return;
        try {
            if (event.getCreateTime() == null) {
                event.setCreateTime(LocalDateTime.now());
            }
            securityEventMapper.insert(event);
        } catch (Exception e) {
            log.error("异步落库安全事件失败: ip={}, type={}", event.getIpaddr(), event.getEventType(), e);
        }
    }

    @Override
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);

        // 今日拦截事件总数
        Long todayTotal = securityEventMapper.selectCount(new LambdaQueryWrapper<SecurityEventDO>()
                .ge(SecurityEventDO::getCreateTime, todayStart));
        stats.put("todayTotal", todayTotal != null ? todayTotal : 0L);

        // 今日直接阻断次数 (BLOCKED)
        Long todayBlocked = securityEventMapper.selectCount(new LambdaQueryWrapper<SecurityEventDO>()
                .ge(SecurityEventDO::getCreateTime, todayStart)
                .eq(SecurityEventDO::getAction, "BLOCKED"));
        stats.put("todayBlocked", todayBlocked != null ? todayBlocked : 0L);

        // 今日爬虫 UA 拦截次数
        Long todayUaBlock = securityEventMapper.selectCount(new LambdaQueryWrapper<SecurityEventDO>()
                .ge(SecurityEventDO::getCreateTime, todayStart)
                .eq(SecurityEventDO::getEventType, "UA_BLOCK"));
        stats.put("todayUaBlock", todayUaBlock != null ? todayUaBlock : 0L);

        // 今日重放攻击拦截次数
        Long todayReplayBlock = securityEventMapper.selectCount(new LambdaQueryWrapper<SecurityEventDO>()
                .ge(SecurityEventDO::getCreateTime, todayStart)
                .eq(SecurityEventDO::getEventType, "ANTI_REPLAY"));
        stats.put("todayReplayBlock", todayReplayBlock != null ? todayReplayBlock : 0L);

        // 当前处于封禁中的 IP 数量
        int activeBlockedCount = 0;
        try {
            activeBlockedCount = antiBotRiskService.getActiveRiskList().size();
        } catch (Exception ignored) {}
        stats.put("activeBlockedCount", activeBlockedCount);

        return stats;
    }

    @Override
    public void delete(Long id) {
        securityEventMapper.deleteById(id);
    }

    @Override
    public void clear() {
        securityEventMapper.delete(new LambdaQueryWrapper<>());
    }
}
