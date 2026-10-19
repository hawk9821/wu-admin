-- =============================================================================
-- add9 · AI 菜单图标现代科技感升级（告别魔法棒）与知识库图标纠偏
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
-- 1. 更新 AI 模块各级菜单图标
-- -----------------------------------------------------------------------------
-- 顶级目录：AI 管理 (id=210) -> 科技罗盘 Compass
UPDATE `sys_menu`
SET `icon` = 'Compass'
WHERE `id` = 210 AND (`icon` = 'MagicStick' OR `icon` = '' OR `icon` IS NULL);

-- 二级菜单：AI 模型配置 (id=200) -> 模型算子 Operation
UPDATE `sys_menu`
SET `icon` = 'Operation'
WHERE `id` = 200 AND (`icon` = 'MagicStick' OR `icon` = '' OR `icon` IS NULL);

-- 二级菜单：AI 知识库 (id=220) -> 书本笔记本 Notebook（对齐图3规范）
UPDATE `sys_menu`
SET `icon` = 'Notebook'
WHERE `id` = 220;

-- 兼容按 path 命中的动态记录
UPDATE `sys_menu`
SET `icon` = 'Compass'
WHERE `path` = '/ai' AND `type` = 1 AND (`icon` = 'MagicStick' OR `icon` = '' OR `icon` IS NULL);

UPDATE `sys_menu`
SET `icon` = 'Operation'
WHERE `path` = '/ai/model' AND `type` = 2 AND (`icon` = 'MagicStick' OR `icon` = '' OR `icon` IS NULL);

UPDATE `sys_menu`
SET `icon` = 'Notebook'
WHERE `path` = '/ai/knowledge' AND `type` = 2;

-- -----------------------------------------------------------------------------
-- 2. 确保 AI 知识库关联权限已完整绑定至角色（幂等）
-- -----------------------------------------------------------------------------
-- 超管（role_id=1）授予 AI 知识库管理全权限（查看/新增/修改/删除）
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 220), (1, 224), (1, 221), (1, 222), (1, 223);

-- 普通用户（role_id=2）仅授予 AI 知识库菜单与查询权限（只读）
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2, 220), (2, 224);

-- -----------------------------------------------------------------------------
-- 3. 校验查询（执行后可检查输出）
-- -----------------------------------------------------------------------------
SELECT `id`, `name`, `path`, `icon`, `component`, `status`
FROM `sys_menu`
WHERE `id` IN (210, 200, 205, 220) OR `path` IN ('/ai', '/ai/model', '/ai/log', '/ai/knowledge');
