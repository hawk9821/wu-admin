package com.admin.server.modules.system.service.security;

import com.admin.server.modules.system.api.security.vo.RiskSubjectVO;

import java.util.List;

/**
 * 行为风控与反爬防御服务
 */
public interface AntiBotRiskService {

    /**
     * 判断当前 IP 是否处于封禁状态
     *
     * @param ip 客户端IP
     * @return 是否被封禁
     */
    boolean isBlocked(String ip);

    /**
     * 累加风险分并在超阈值时自动跃迁封禁
     *
     * @param principal 风险主体（通常为 IP）
     * @param ip 真实 IP
     * @param delta 增加的风险分
     * @param eventType 事件类型
     * @param action 处置动作
     * @param uri 请求路径
     * @param method 请求方法
     * @param userAgent 请求 UA
     * @param reason 触发原因
     * @return 触发后当前总风险分
     */
    int addRisk(String principal, String ip, int delta, String eventType, String action,
                String uri, String method, String userAgent, String reason);

    /**
     * 获取当前主体的风险分
     *
     * @param principal 风险主体
     * @return 风险分
     */
    int getRiskScore(String principal);

    /**
     * 手动封禁主体
     *
     * @param principal 风险主体 (IP)
     * @param durationSeconds 封禁时长（秒）
     * @param reason 封禁原因
     */
    void blockPrincipal(String principal, long durationSeconds, String reason);

    /**
     * 一键解除主体封禁并清空风险分
     *
     * @param principal 风险主体 (IP)
     */
    void unblockPrincipal(String principal);

    /**
     * 获取当前系统处于高风险或被封禁的主体列表
     *
     * @return 风险主体列表
     */
    List<RiskSubjectVO> getActiveRiskList();

    /**
     * 检测 User-Agent 是否命中爬虫黑名单
     *
     * @param userAgent 待检测的 User-Agent
     * @return 命中的爬虫特征标识，未命中返回 null
     */
    String matchBlockedUserAgent(String userAgent);
}
