-- =============================================================================
-- 安全拦截事件索引：统计与筛选按 action + create_time 查询
-- 目标：安全事件页“今日直接阻断”等统计不再全表扫描
-- 适用：MySQL 5.7+ / 8.0
-- =============================================================================

SET @index_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_security_event'
      AND index_name = 'idx_action_time'
);

SET @ddl := IF(
    @index_exists = 0,
    'ALTER TABLE `sys_security_event` ADD INDEX `idx_action_time` (`action`, `create_time`)',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
