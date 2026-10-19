-- =============================================================================
-- add17 · AI 知识库全面升级为 Markdown (.md) 文档知识库与图标美化
-- -----------------------------------------------------------------------------
-- 优化内容：
-- 1. 知识库菜单图标换掉难看的 Notebook，升级为专业高级的图书阅读图标 Reading
-- 2. 知识库升级为原生支持 Markdown (.md) 业务文档上传与管理
-- 3. AI 对话回答优先检索知识库 Markdown 内容，提供精准知识注入
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `wu-admin` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `wu-admin`;

SET NAMES utf8mb4;

-- 1. 更新知识库菜单图标为 Reading
UPDATE `sys_menu`
SET `icon` = 'Reading'
WHERE `id` = 220 OR `path` = '/ai/knowledge';

-- 2. 校验验证
SELECT `id`, `name`, `icon`, `path`
FROM `sys_menu`
WHERE `id` = 220;
