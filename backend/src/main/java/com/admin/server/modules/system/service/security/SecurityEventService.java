package com.admin.server.modules.system.service.security;

import com.admin.server.modules.system.dal.dataobject.security.SecurityEventDO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Map;

/**
 * 安全事件审计日志服务
 */
public interface SecurityEventService {

    /**
     * 分页查询安全事件
     */
    Page<SecurityEventDO> getPage(Integer pageNo, Integer pageSize, String ipaddr, String eventType, String action);

    /**
     * 异步记录安全事件
     */
    void recordAsync(SecurityEventDO event);

    /**
     * 获取今日安全大盘统计数据
     */
    Map<String, Object> getDashboardStats();

    /**
     * 删除单条安全事件
     */
    void delete(Long id);

    /**
     * 清空所有安全事件
     */
    void clear();
}
