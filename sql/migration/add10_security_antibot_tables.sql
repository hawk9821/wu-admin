-- =============================================================================
-- add10 · 满血反爬与安全风控中心（表结构、审计日志与系统日志菜单集成）
-- -----------------------------------------------------------------------------
-- 【执行说明 · 必读】
--   若使用 Navicat / DBeaver 等图形客户端：
--   1. 请先在左侧双击打开业务数据库【wu-admin】（使其变为绿色连接状态）；
--   2. 然后在【wu-admin】节点上右键 ->「运行 SQL 文件」；
--   ★ 脚本开头已内置 USE `wu-admin`，直接运行亦可自动切库，彻底避免「1046 No database selected」报错。
--   ★ 生产环境请将下方的 wu-admin 替换为 wuadmin 即可。
-- =============================================================================

-- 自动创建并切换至目标数据库（防止 Navicat 根节点执行报 1046 错误）
CREATE DATABASE IF NOT EXISTS `wu-admin` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `wu-admin`;

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. 创建安全审计与反爬拦截事件表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_security_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `event_type` VARCHAR(32) NOT NULL COMMENT '事件类型: UA_BLOCK/ANTI_REPLAY/SIGN_FAIL/TIMESTAMP_INVALID/RATE_LIMIT/RISK_BLOCK',
  `principal` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '主体标识(IP或账号)',
  `ipaddr` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '客户端真实IP',
  `request_uri` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '请求路径',
  `request_method` VARCHAR(16) NOT NULL DEFAULT '' COMMENT '请求方法(GET/POST/PUT/DELETE)',
  `user_agent` VARCHAR(512) NOT NULL DEFAULT '' COMMENT '请求User-Agent头',
  `risk_delta` INT NOT NULL DEFAULT 0 COMMENT '本次触发风险分增量',
  `total_risk_score` INT NOT NULL DEFAULT 0 COMMENT '触发后累计风险分',
  `action` VARCHAR(32) NOT NULL DEFAULT 'BLOCKED' COMMENT '处置动作: BLOCKED/FORBIDDEN/WARNING',
  `reason` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '触发原因详细说明',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '触发时间',
  PRIMARY KEY (`id`),
  KEY `idx_event_type` (`event_type`),
  KEY `idx_ipaddr` (`ipaddr`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全审计与反爬拦截事件表';

-- -----------------------------------------------------------------------------
-- 2. 在【系统日志】（parent_id=120）下挂载【安全风控】菜单与权限
-- （分配安全未占用 ID: 230 ~ 235，坚决避免与系统字典管理 130~135 产生任何冲突）
-- -----------------------------------------------------------------------------
-- 二级菜单：安全风控 (id=230)
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `path`, `component`, `icon`, `sort`, `type`, `permission`, `status`, `create_time`, `update_time`)
VALUES (230, 120, '安全风控', 'system/security-log', 'system/security-log/index', 'Shield', 3, 2, 'system:security:list', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  `parent_id` = VALUES(`parent_id`),
  `name` = VALUES(`name`),
  `path` = VALUES(`path`),
  `component` = VALUES(`component`),
  `icon` = VALUES(`icon`),
  `status` = 1,
  `permission` = VALUES(`permission`);

-- 按钮权限：查询
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `path`, `component`, `icon`, `sort`, `type`, `permission`, `status`, `create_time`, `update_time`)
VALUES (231, 230, '安全查询', '', '', '', 1, 3, 'system:security:query', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE `permission` = VALUES(`permission`), `status` = 1;

-- 按钮权限：一键解封
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `path`, `component`, `icon`, `sort`, `type`, `permission`, `status`, `create_time`, `update_time`)
VALUES (232, 230, '风险解封', '', '', '', 2, 3, 'system:security:unblock', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE `permission` = VALUES(`permission`), `status` = 1;

-- 按钮权限：手动拉黑
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `path`, `component`, `icon`, `sort`, `type`, `permission`, `status`, `create_time`, `update_time`)
VALUES (233, 230, '手动拉黑', '', '', '', 3, 3, 'system:security:block', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE `permission` = VALUES(`permission`), `status` = 1;

-- 按钮权限：清空日志
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `path`, `component`, `icon`, `sort`, `type`, `permission`, `status`, `create_time`, `update_time`)
VALUES (234, 230, '清空安全日志', '', '', '', 4, 3, 'system:security:clear', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE `permission` = VALUES(`permission`), `status` = 1;

-- 按钮权限：删除单条
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `path`, `component`, `icon`, `sort`, `type`, `permission`, `status`, `create_time`, `update_time`)
VALUES (235, 230, '删除安全日志', '', '', '', 5, 3, 'system:security:delete', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE `permission` = VALUES(`permission`), `status` = 1;

-- -----------------------------------------------------------------------------
-- 3. 授予超级管理员（role_id=1）相关菜单权限
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 230),
(1, 231),
(1, 232),
(1, 233),
(1, 234),
(1, 235);

-- -----------------------------------------------------------------------------
-- 4. 校验查询（执行后检查结果）
-- -----------------------------------------------------------------------------
SELECT `id`, `parent_id`, `name`, `path`, `permission`, `status`
FROM `sys_menu`
WHERE `id` BETWEEN 230 AND 235;
