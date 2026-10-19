package com.admin.server.modules.system.api.security.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 风险主体与封禁状态 VO
 */
@Data
public class RiskSubjectVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主体IP或标识
     */
    private String principal;

    /**
     * 当前累计风险分
     */
    private Integer riskScore;

    /**
     * 是否已被封禁
     */
    private Boolean blocked;

    /**
     * 封禁剩余时长(秒)
     */
    private Long remainingSeconds;

    /**
     * 封禁原因
     */
    private String reason;
}
