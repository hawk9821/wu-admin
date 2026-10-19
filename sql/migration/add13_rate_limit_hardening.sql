-- =============================================================================
-- add13 · 应用层滑动窗口限流参数
-- -----------------------------------------------------------------------------
-- 为已有数据库的 rateLimit 配置组补齐应用层限流字段：
--   apiPerIpMinute   通用业务 API：单 IP 每分钟请求上限（默认 240）
--   authPerIpMinute  认证接口：单 IP 每分钟请求上限（默认 60）
--   filesPerIpMinute 文件访问：单 IP 每分钟请求上限（默认 600）
-- 0 表示不限制；修改后保存配置即生效，无需重启。
-- =============================================================================

UPDATE `sys_config_group`
SET `config_value` = JSON_SET(
    `config_value`,
    '$.apiPerIpMinute', 240,
    '$.authPerIpMinute', 60,
    '$.filesPerIpMinute', 600
)
WHERE `group_code` = 'rateLimit'
  AND JSON_VALID(`config_value`);

-- 校验查询
SELECT `group_code`, `config_value`
FROM `sys_config_group`
WHERE `group_code` = 'rateLimit';
