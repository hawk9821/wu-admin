-- =============================================================================
-- 文件归属校验索引：SysFileStorageAccessGuard 按 objectKey 反查 sys_file
-- 目标：避免私有文件每次访问都全表扫描；同时覆盖多引擎同 objectKey 的 bucket 精确定位
-- 适用：MySQL 5.7+ / 8.0
-- =============================================================================

SET @index_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_file'
      AND index_name = 'idx_file_path_bucket'
);

SET @ddl := IF(
    @index_exists = 0,
    'ALTER TABLE `sys_file` ADD INDEX `idx_file_path_bucket` (`file_path`, `bucket_name`)',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
