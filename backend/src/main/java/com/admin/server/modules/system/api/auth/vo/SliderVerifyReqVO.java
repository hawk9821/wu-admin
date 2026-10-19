package com.admin.server.modules.system.api.auth.vo;

import lombok.Data;

import java.util.List;

/**
 * 滑块验证码校验请求。
 */
@Data
public class SliderVerifyReqVO {

    /** challenge 令牌 */
    private String token;

    /** 用户拖动距离（服务端逻辑坐标） */
    private Integer offsetX;

    /** 拖动总耗时（毫秒） */
    private Integer durationMs;

    /** 拖动轨迹采样点 */
    private List<SliderTrackEventVO> events;
}
