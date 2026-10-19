-- =============================================================================
-- add6 · 普通用户（体验账号）角色权限收口
-- -----------------------------------------------------------------------------
-- 【背景】
--   正文 Part B 已把普通用户角色裁剪为「只读 + 本人范围」，但文末「附录」段的
--     INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES ... (2, 185), (2, 188), (2, 164) ...
--   未同步裁剪，导致：
--     · 全新安装：附录段又把 185/188/164/165/169/201/204 补回普通用户角色；
--     · 已有库升级：每次执行附录段都会重新补回。
--   已存在的库在旧脚本下已实际授权，需要本脚本一次性撤销。
--
-- 【脚本性质】
--   纯「撤销授权」（DELETE），不含任何 DDL / DROP / 建表，可重复执行（幂等）。
--   dev（库 wu-admin）与 prod（库 wuadmin）通用。
--
-- 【执行】
--   mysql -u root -p wu-admin < sql/migration/add6_role_permission_hardening.sql
--   mysql -u wuadmin -p wuadmin  < sql/migration/add6_role_permission_hardening.sql
--
-- 【目标角色】
--   role_id = 2（普通用户），同时兼容 code = 'user' 的自定义/改名库。
--
-- 【生效说明】
--   权限校验每次均实时查库（PermissionServiceImpl 无 Redis 缓存），执行后立即生效，
-- 自动创建并切换至目标数据库（防止 Navicat 根节点执行报 1046 错误）
CREATE DATABASE IF NOT EXISTS `wu-admin` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `wu-admin`;

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. 【P0】撤销缓存监控（185 列表 / 188 查询）—— 等同交出他人登录态
--
--   Redis 中 Sa-Token 会话键形如 Authorization:login:token:<tokenValue>，
--   键名本身即 token 明文；而缓存监控的「键列表 / 键详情」接口没有任何会话键
--   黑名单（黑名单只作用于删除操作）。持有 monitor:cache:query 即可枚举出
--   所有在线用户的 token，随后以超管身份调用任意接口 —— 属认证绕过级风险。
-- -----------------------------------------------------------------------------
DELETE rm FROM sys_role_menu rm
WHERE rm.role_id IN (SELECT id FROM sys_role WHERE code = 'user' OR id = 2)
  AND rm.menu_id IN (185, 188);

-- -----------------------------------------------------------------------------
-- 2. 【P1】撤销代码生成（164 列表 / 165 查询 / 169 预览）
--   可读取全库表结构与生成的代码，属敏感信息泄露。
-- -----------------------------------------------------------------------------
DELETE rm FROM sys_role_menu rm
WHERE rm.role_id IN (SELECT id FROM sys_role WHERE code = 'user' OR id = 2)
  AND rm.menu_id IN (164, 165, 169);

-- -----------------------------------------------------------------------------
-- 3. 【P1】撤销 AI 模型新增（201）/ 连通性测试（204）
--   baseUrl 无白名单校验，测试接口会让服务端请求该地址（SSRF）；
--   且库中无模型时新建的首个模型会自动成为默认模型，可劫持全站 AI 请求。
-- -----------------------------------------------------------------------------
DELETE rm FROM sys_role_menu rm
WHERE rm.role_id IN (SELECT id FROM sys_role WHERE code = 'user' OR id = 2)
  AND rm.menu_id IN (201, 204);

-- -----------------------------------------------------------------------------
-- 4. 【P1】撤销操作日志（121 列表 / 126 查询）
--   返回全站请求参数原文与操作 IP，密钥类参数仅在 @Log 层掩码了 password。
-- -----------------------------------------------------------------------------
DELETE rm FROM sys_role_menu rm
WHERE rm.role_id IN (SELECT id FROM sys_role WHERE code = 'user' OR id = 2)
  AND rm.menu_id IN (121, 126);

-- -----------------------------------------------------------------------------
-- 5. 【P1】撤销在线用户（103 列表 / 190 查询）
--   可查看全站在线会话并以管理员身份强制下线他人。
-- -----------------------------------------------------------------------------
DELETE rm FROM sys_role_menu rm
WHERE rm.role_id IN (SELECT id FROM sys_role WHERE code = 'user' OR id = 2)
  AND rm.menu_id IN (103, 190);

-- -----------------------------------------------------------------------------
-- 6. 【P2】撤销工单流转全局特权（63）
--   与 dev 脚本口径保持一致：普通用户只流转分配给自己的工单。
--   注：该风险已由代码层兜底（流转前校验工单可见性：仅创建人/处理人/全员工单/超管），
--   如需保留体验账号的流转入口，可注释掉本段。
-- -----------------------------------------------------------------------------
DELETE rm FROM sys_role_menu rm
WHERE rm.role_id IN (SELECT id FROM sys_role WHERE code = 'user' OR id = 2)
  AND rm.menu_id = 63;

-- =============================================================================
-- 执行结果核验
-- =============================================================================

-- 核验 1：普通用户角色当前全部权限清单（人工确认无越权项）
SELECT rm.menu_id, m.name, m.permission
FROM sys_role_menu rm
JOIN sys_menu m ON m.id = rm.menu_id
WHERE rm.role_id IN (SELECT id FROM sys_role WHERE code = 'user' OR id = 2)
ORDER BY rm.menu_id;

-- 核验 2：危险权限残留检查 —— 期望返回 0 行
SELECT rm.menu_id, m.name, m.permission AS residual_danger
FROM sys_role_menu rm
JOIN sys_menu m ON m.id = rm.menu_id
WHERE rm.role_id IN (SELECT id FROM sys_role WHERE code = 'user' OR id = 2)
  AND (   m.permission LIKE 'monitor:cache:%'
       OR m.permission LIKE 'tool:gen:%'
       OR m.permission LIKE 'system:operLog:%'
       OR m.permission LIKE 'monitor:online:%'
       OR m.permission IN ('system:ai-model:create', 'system:ai-model:test', 'system:ticket:transition'));

-- =============================================================================
-- 可选：历史痕迹自查（确认上述越权是否已被利用过）
--   若返回结果中有非本人操作，说明线上 token 可能已泄露，建议轮换所有会话并改密。
-- =============================================================================
-- SELECT oper_name, oper_url, oper_ip, create_time
-- FROM sys_oper_log
-- WHERE oper_url LIKE '%monitor/cache%' OR oper_url LIKE '%ai-model%'
-- ORDER BY id DESC LIMIT 100;
