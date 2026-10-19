-- =============================================================================
-- add12 · 文件存储表字段容量加固
-- -----------------------------------------------------------------------------
-- 背景：对象 key 使用「短哈希 + 安全文件名」后，原始文件名、存储文件名、访问 URL
--       和后缀的最大长度需要与后端校验保持一致，避免文件已写入存储但数据库插入失败。
-- 说明：MySQL 的 MODIFY COLUMN 为幂等操作，可安全重复执行。
-- =============================================================================

ALTER TABLE `sys_file`
    MODIFY COLUMN `original_name` VARCHAR(255) DEFAULT '' COMMENT '原始文件名',
    MODIFY COLUMN `file_name` VARCHAR(300) NOT NULL COMMENT '存储文件名',
    MODIFY COLUMN `url` VARCHAR(1024) DEFAULT '' COMMENT '访问URL',
    MODIFY COLUMN `file_suffix` VARCHAR(32) DEFAULT '' COMMENT '后缀';

