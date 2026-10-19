-- =============================================================================
-- add14 · 风险分封禁阈值配置化
-- -----------------------------------------------------------------------------
-- 为已有数据库的 security 配置组补齐风险分阈值：
--   riskBlockScore 达到后临时封禁 1 小时（默认 80，最小 20）
--   riskBanScore   达到后封禁 24 小时（默认 120，必须高于 riskBlockScore）
-- 0 或非法值不会生效，后端会回退到默认阈值。
-- =============================================================================

UPDATE `sys_config_group`
SET `config_value` = JSON_SET(
    `config_value`,
    '$.riskBlockScore', 80,
    '$.riskBanScore', 120
)
WHERE `group_code` = 'security'
  AND JSON_VALID(`config_value`);

-- 校验查询
SELECT `group_code`, `config_value`
FROM `sys_config_group`
WHERE `group_code` = 'security';
