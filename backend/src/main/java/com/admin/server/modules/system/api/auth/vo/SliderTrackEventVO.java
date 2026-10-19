package com.admin.server.modules.system.api.auth.vo;

import lombok.Data;

/**
 * 滑块拖动轨迹采样点。
 * <p>坐标使用服务端图片的逻辑坐标系（与 challenge 响应中的 width/height 一致），
 * t 为相对拖动开始时间的毫秒数。</p>
 */
@Data
public class SliderTrackEventVO {

    private Integer x;

    private Integer y;

    private Integer t;
}
