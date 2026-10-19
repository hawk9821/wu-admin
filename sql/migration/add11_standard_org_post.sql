-- =============================================================================
-- add11 · 标准组织与岗位精简（含旧菜单冲突修复）
-- -----------------------------------------------------------------------------
-- 【核心内容】
--   1. 组织管理（sys_dept）精简为 3 个标准节点：总公司、研发部、运营部；
--   2. 岗位管理（sys_post）精简为 3 个黄金岗位：总经理(ceo)、技术总监(cto)、研发工程师(dev)；
--   3. 兼容旧版初始化脚本：恢复被覆盖的 110~113 文件管理菜单并清理历史租户菜单。
--
-- 【说明】
--   本脚本可重复执行，仅处理组织、岗位与菜单数据，不涉及任何租户字段。
--   已有库如需删除历史 tenant_id 列、sys_tenant 表并启用组织数据权限，请执行
--   add18_remove_tenant_enable_org_isolation.sql。
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `wu-admin` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `wu-admin`;

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. 组织管理（sys_dept）精简：只保留 3 个标准部门
-- -----------------------------------------------------------------------------
UPDATE `sys_user` SET `dept_id` = 2
WHERE `dept_id` NOT IN (1, 2, 3) OR `dept_id` IS NULL;
UPDATE `sys_user` SET `dept_id` = 1 WHERE `id` = 1; -- 管理员归属总公司
UPDATE `sys_user` SET `dept_id` = 2 WHERE `id` = 2; -- 张三归属研发部

-- 清理旧部门并重建 3 个标准部门
DELETE FROM `sys_dept`;
INSERT INTO `sys_dept` (`id`, `name`, `parent_id`, `ancestors`, `sort`, `status`, `leader_name`, `leader_user_id`, `deleted`)
VALUES
(1, '总公司', 0, '0', 1, 1, '管理员', 1, 0),
(2, '研发部', 1, '0,1', 1, 1, '张三', 2, 0),
(3, '运营部', 1, '0,1', 2, 1, NULL, NULL, 0);

ALTER TABLE `sys_dept` AUTO_INCREMENT = 4;

-- -----------------------------------------------------------------------------
-- 2. 岗位管理（sys_post）精简：只保留 3 个黄金岗位
-- -----------------------------------------------------------------------------
DELETE FROM `sys_user_post`;
DELETE FROM `sys_post`;

INSERT INTO `sys_post` (`id`, `parent_id`, `post_code`, `post_name`, `sort`, `status`, `remark`, `deleted`)
VALUES
(1, 0, 'ceo', '总经理', 1, 1, '企业最高经营管理负责人', 0),
(2, 1, 'cto', '技术总监', 2, 1, '技术与研发整体负责人', 0),
(3, 2, 'dev', '研发工程师', 3, 1, '核心开发与技术实现', 0);

ALTER TABLE `sys_post` AUTO_INCREMENT = 4;

-- 绑定核心用户岗位：admin 绑定总经理(id=1)，zhangsan 绑定研发工程师(id=3)
INSERT INTO `sys_user_post` (`user_id`, `post_id`) VALUES
(1, 1),
(2, 3);

-- -----------------------------------------------------------------------------
-- 3. 兼容旧版初始化脚本的菜单修复
-- -----------------------------------------------------------------------------
-- 3.1 仅在 110~113 被写成 system:tenant:* 时恢复文件管理菜单，避免误改自定义菜单。
UPDATE `sys_menu`
SET `parent_id` = 105, `name` = '文件列表', `path` = '/system/file',
    `component` = 'system/file/index', `icon` = 'DocumentCopy', `sort` = 1,
    `type` = 2, `permission` = 'sys:file:list', `status` = 1
WHERE `id` = 110 AND `permission` = 'system:tenant:query';

UPDATE `sys_menu`
SET `parent_id` = 110, `name` = '文件查询', `path` = '', `component` = '',
    `icon` = '', `sort` = 1, `type` = 3, `permission` = 'sys:file:query', `status` = 1
WHERE `id` = 111 AND `permission` = 'system:tenant:add';

UPDATE `sys_menu`
SET `parent_id` = 110, `name` = '文件上传', `path` = '', `component` = '',
    `icon` = '', `sort` = 2, `type` = 3, `permission` = 'sys:file:upload', `status` = 1
WHERE `id` = 112 AND `permission` = 'system:tenant:edit';

UPDATE `sys_menu`
SET `parent_id` = 110, `name` = '文件删除', `path` = '', `component` = '',
    `icon` = '', `sort` = 3, `type` = 3, `permission` = 'sys:file:delete', `status` = 1
WHERE `id` = 113 AND `permission` = 'system:tenant:delete';

-- 3.2 清理历史租户管理菜单及其角色关联
DELETE FROM `sys_role_menu` WHERE `menu_id` = 109 OR `menu_id` BETWEEN 240 AND 244;
DELETE FROM `sys_menu` WHERE `id` = 109 AND `permission` = 'system:tenant:list';
DELETE FROM `sys_menu` WHERE `id` BETWEEN 240 AND 244;
