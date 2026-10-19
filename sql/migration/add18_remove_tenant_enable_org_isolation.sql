-- =============================================================================
-- add18 · 删除租户隔离并启用组织（部门）数据权限
-- -----------------------------------------------------------------------------
-- 适用场景：
--   已经执行过 add11 / add15 等历史租户脚本的数据库。
--
-- 执行内容：
--   1. 删除所有业务表的 tenant_id 列与 idx_tenant_id 索引；
--   2. 恢复岗位编码、角色编码的全局唯一索引；
--   3. 删除 sys_tenant 租户主表；
--   4. 为文件、文件分组增加 dept_id 归属部门字段并回填存量数据；
--   5. 初始化角色数据范围：超级管理员=全部，普通用户=本部门及以下；
--   6. 清理历史租户管理菜单（109、240~244）及其角色关联。
--
-- 说明：
--   本脚本可重复执行；列、索引、菜单不存在时自动跳过。
--   执行前请先备份数据库。
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `wu-admin` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `wu-admin`;

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. 删除租户唯一索引（这些索引包含 tenant_id，必须先于删列执行）
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_drop_tenant_unique_index;
DELIMITER $$
CREATE PROCEDURE sp_drop_tenant_unique_index()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.statistics
               WHERE table_schema = DATABASE() AND table_name = 'sys_post'
                 AND index_name = 'uk_tenant_post_code') THEN
        ALTER TABLE `sys_post` DROP INDEX `uk_tenant_post_code`;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.statistics
               WHERE table_schema = DATABASE() AND table_name = 'sys_role'
                 AND index_name = 'uk_tenant_code') THEN
        ALTER TABLE `sys_role` DROP INDEX `uk_tenant_code`;
    END IF;
END$$
DELIMITER ;

CALL sp_drop_tenant_unique_index();
DROP PROCEDURE IF EXISTS sp_drop_tenant_unique_index;

-- -----------------------------------------------------------------------------
-- 2. 恢复岗位编码、角色编码全局唯一索引（存在重复数据时跳过，避免迁移中断）
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_restore_global_unique_index;
DELIMITER $$
CREATE PROCEDURE sp_restore_global_unique_index()
BEGIN
    DECLARE duplicate_count INT DEFAULT 0;

    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = DATABASE() AND table_name = 'sys_post') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                       WHERE table_schema = DATABASE() AND table_name = 'sys_post'
                         AND index_name = 'uk_post_code') THEN
            SELECT COUNT(*) INTO duplicate_count FROM (
                SELECT `post_code` FROM `sys_post`
                WHERE `deleted` = 0
                GROUP BY `post_code` HAVING COUNT(*) > 1
            ) AS dup;
            IF duplicate_count = 0 THEN
                ALTER TABLE `sys_post` ADD UNIQUE KEY `uk_post_code` (`post_code`);
            END IF;
        END IF;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = DATABASE() AND table_name = 'sys_role') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                       WHERE table_schema = DATABASE() AND table_name = 'sys_role'
                         AND index_name = 'uk_code') THEN
            SELECT COUNT(*) INTO duplicate_count FROM (
                SELECT `code` FROM `sys_role`
                WHERE `deleted` = 0
                GROUP BY `code` HAVING COUNT(*) > 1
            ) AS dup;
            IF duplicate_count = 0 THEN
                ALTER TABLE `sys_role` ADD UNIQUE KEY `uk_code` (`code`);
            END IF;
        END IF;
    END IF;
END$$
DELIMITER ;

CALL sp_restore_global_unique_index();
DROP PROCEDURE IF EXISTS sp_restore_global_unique_index;

-- -----------------------------------------------------------------------------
-- 3. 幂等删除各业务表的 idx_tenant_id 索引与 tenant_id 列
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_drop_tenant_column_if_exists;
DELIMITER $$
CREATE PROCEDURE sp_drop_tenant_column_if_exists(IN target_table VARCHAR(64))
BEGIN
    DECLARE table_exists INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    DECLARE index_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO table_exists
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = target_table;

    IF table_exists > 0 THEN
        SELECT COUNT(*) INTO index_exists
        FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = target_table
          AND index_name = 'idx_tenant_id';

        IF index_exists > 0 THEN
            SET @ddl = CONCAT('ALTER TABLE `', target_table, '` DROP INDEX `idx_tenant_id`');
            PREPARE stmt FROM @ddl;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        SELECT COUNT(*) INTO column_exists
        FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = target_table
          AND column_name = 'tenant_id';

        IF column_exists > 0 THEN
            SET @ddl = CONCAT('ALTER TABLE `', target_table, '` DROP COLUMN `tenant_id`');
            PREPARE stmt FROM @ddl;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
    END IF;
END$$
DELIMITER ;

CALL sp_drop_tenant_column_if_exists('sys_user');
CALL sp_drop_tenant_column_if_exists('sys_role');
CALL sp_drop_tenant_column_if_exists('sys_user_role');
CALL sp_drop_tenant_column_if_exists('sys_user_post');
CALL sp_drop_tenant_column_if_exists('sys_role_menu');
CALL sp_drop_tenant_column_if_exists('sys_dept');
CALL sp_drop_tenant_column_if_exists('sys_post');
CALL sp_drop_tenant_column_if_exists('sys_ticket');
CALL sp_drop_tenant_column_if_exists('sys_ticket_comment');
CALL sp_drop_tenant_column_if_exists('sys_ticket_attachment');
CALL sp_drop_tenant_column_if_exists('sys_approval_form');
CALL sp_drop_tenant_column_if_exists('sys_approval_record');
CALL sp_drop_tenant_column_if_exists('sys_announce');
CALL sp_drop_tenant_column_if_exists('sys_user_announce');
CALL sp_drop_tenant_column_if_exists('sys_announce_send_log');
CALL sp_drop_tenant_column_if_exists('sys_notice');
CALL sp_drop_tenant_column_if_exists('sys_file');
CALL sp_drop_tenant_column_if_exists('sys_file_group');
CALL sp_drop_tenant_column_if_exists('sys_chat_message');
CALL sp_drop_tenant_column_if_exists('sys_chat_group');
CALL sp_drop_tenant_column_if_exists('sys_chat_group_member');
CALL sp_drop_tenant_column_if_exists('sys_chat_group_message');
CALL sp_drop_tenant_column_if_exists('sys_chat_group_log');
CALL sp_drop_tenant_column_if_exists('sys_user_blacklist');
CALL sp_drop_tenant_column_if_exists('sys_oper_log');
CALL sp_drop_tenant_column_if_exists('sys_login_log');
CALL sp_drop_tenant_column_if_exists('sys_ai_chat_log');
CALL sp_drop_tenant_column_if_exists('sys_security_event');

DROP PROCEDURE IF EXISTS sp_drop_tenant_column_if_exists;

-- -----------------------------------------------------------------------------
-- 4. 删除租户主表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_tenant`;

-- -----------------------------------------------------------------------------
-- 5. 文件与文件分组增加归属部门字段并回填存量数据
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_add_dept_column_if_missing;
DELIMITER $$
CREATE PROCEDURE sp_add_dept_column_if_missing(IN target_table VARCHAR(64))
BEGIN
    DECLARE table_exists INT DEFAULT 0;
    DECLARE column_exists INT DEFAULT 0;
    DECLARE index_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO table_exists
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = target_table;

    IF table_exists > 0 THEN
        SELECT COUNT(*) INTO column_exists
        FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = target_table
          AND column_name = 'dept_id';

        IF column_exists = 0 THEN
            SET @ddl = CONCAT('ALTER TABLE `', target_table,
                '` ADD COLUMN `dept_id` BIGINT DEFAULT NULL COMMENT ''归属部门ID'' AFTER `id`');
            PREPARE stmt FROM @ddl;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        SELECT COUNT(*) INTO index_exists
        FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = target_table
          AND index_name = 'idx_dept_id';

        IF index_exists = 0 THEN
            SET @ddl = CONCAT('ALTER TABLE `', target_table, '` ADD INDEX `idx_dept_id` (`dept_id`)');
            PREPARE stmt FROM @ddl;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
    END IF;
END$$
DELIMITER ;

CALL sp_add_dept_column_if_missing('sys_file');
CALL sp_add_dept_column_if_missing('sys_file_group');

DROP PROCEDURE IF EXISTS sp_add_dept_column_if_missing;

UPDATE `sys_file` f
JOIN `sys_user` u ON u.`username` = f.`create_by`
SET f.`dept_id` = u.`dept_id`
WHERE f.`dept_id` IS NULL;

UPDATE `sys_file_group` g
JOIN `sys_user` u ON u.`username` = g.`create_by`
SET g.`dept_id` = u.`dept_id`
WHERE g.`dept_id` IS NULL;

-- 无法按 create_by 归属的历史数据不再保留：
-- 1) 先解除这些分组下的文件关联，再删除分组；
-- 2) 文件记录软删除进入回收站，由文件保留期任务（默认 30 天）物理清理。
-- 如果确认旧文件完全不需要，可把最后的 UPDATE 改为 DELETE FROM sys_file。
UPDATE `sys_file` SET `group_id` = NULL
WHERE `group_id` IN (SELECT `id` FROM `sys_file_group` WHERE `dept_id` IS NULL);

DELETE FROM `sys_file_group` WHERE `dept_id` IS NULL;

UPDATE `sys_file` SET `deleted` = 1, `update_time` = NOW()
WHERE `dept_id` IS NULL AND `deleted` = 0;

-- -----------------------------------------------------------------------------
-- 6. 角色数据范围初始化（组织隔离）
--    1 全部 / 2 自定义 / 3 本部门 / 4 本部门及以下 / 5 仅本人
-- -----------------------------------------------------------------------------
SET @has_data_scope = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_role' AND column_name = 'data_scope'
);
SET @ddl = IF(@has_data_scope = 0,
    'ALTER TABLE `sys_role` ADD COLUMN `data_scope` TINYINT DEFAULT 1 COMMENT ''数据范围 1:全部 2:自定义 3:本部门 4:本部门及以下 5:仅本人'' AFTER `remark`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_data_scope_dept_ids = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_role' AND column_name = 'data_scope_dept_ids'
);
SET @ddl = IF(@has_data_scope_dept_ids = 0,
    'ALTER TABLE `sys_role` ADD COLUMN `data_scope_dept_ids` VARCHAR(500) DEFAULT NULL COMMENT ''数据范围部门ID'' AFTER `data_scope`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `sys_role` SET `data_scope` = 1
WHERE `code` = 'super_admin' AND (`data_scope` IS NULL OR `data_scope` = 0);

UPDATE `sys_role` SET `data_scope` = 4
WHERE `code` = 'user' AND (`data_scope` IS NULL OR `data_scope` = 1);

-- -----------------------------------------------------------------------------
-- 7. 清理历史租户管理菜单及其角色关联
-- -----------------------------------------------------------------------------
DELETE FROM `sys_role_menu` WHERE `menu_id` = 109 OR `menu_id` BETWEEN 240 AND 244;
DELETE FROM `sys_menu` WHERE `id` = 109 AND `permission` = 'system:tenant:list';
DELETE FROM `sys_menu` WHERE `id` BETWEEN 240 AND 244;

-- -----------------------------------------------------------------------------
-- 8. 校验：确认已无 tenant_id 列
-- -----------------------------------------------------------------------------
SELECT COUNT(*) AS remaining_tenant_columns
FROM information_schema.columns
WHERE table_schema = DATABASE() AND column_name = 'tenant_id';
