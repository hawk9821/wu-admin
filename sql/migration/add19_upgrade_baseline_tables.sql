-- =============================================================================
-- add19 · 旧库升级基线表补齐
-- -----------------------------------------------------------------------------
-- 背景：全量脚本 Part A 是唯一的建表来源，附录不再重复 CREATE TABLE。
--       旧库（已有表但缺后续新增表）先执行本脚本补齐表结构，再执行其他 addXX。
-- 说明：本脚本只包含 CREATE TABLE IF NOT EXISTS，可重复执行，不修改已有表。
-- =============================================================================

SET NAMES utf8mb4;

-- sys_ai_knowledge
CREATE TABLE IF NOT EXISTS sys_ai_knowledge (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '知识ID',
    title VARCHAR(128) NOT NULL DEFAULT '' COMMENT '知识标题',
    keywords VARCHAR(255) NOT NULL DEFAULT '' COMMENT '检索关键词(逗号分隔)',
    content TEXT NOT NULL COMMENT '知识正文(Markdown)',
    category VARCHAR(32) NOT NULL DEFAULT 'faq' COMMENT '分类: faq/manual/module/other',
    sort INT NOT NULL DEFAULT 0 COMMENT '排序权重(大者优先)',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1启用 0停用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    creator VARCHAR(64) DEFAULT '' COMMENT '创建者',
    updater VARCHAR(64) DEFAULT '' COMMENT '更新者',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除',
    INDEX idx_status_category (status, category),
    FULLTEXT INDEX ft_knowledge (title, keywords, content) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 知识库';

-- sys_job
CREATE TABLE IF NOT EXISTS sys_job (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    job_name VARCHAR(64) NOT NULL COMMENT '任务名称',
    job_group VARCHAR(64) DEFAULT 'DEFAULT' COMMENT '任务组名',
    invoke_target VARCHAR(500) NOT NULL COMMENT '调用目标字符串',
    cron_expression VARCHAR(255) DEFAULT NULL COMMENT 'cron执行表达式',
    misfire_policy TINYINT DEFAULT 3 COMMENT '计划执行错误策略(1-立即执行 2-执行一次 3-放弃执行)',
    concurrent TINYINT DEFAULT 1 COMMENT '是否并发执行(0-允许 1-禁止)',
    status TINYINT DEFAULT 0 COMMENT '状态(0-暂停 1-正常)',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    creator VARCHAR(64) DEFAULT '' COMMENT '创建者',
    updater VARCHAR(64) DEFAULT '' COMMENT '更新者',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除',
    INDEX idx_job_group (job_group),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务表';

-- sys_job_log
CREATE TABLE IF NOT EXISTS sys_job_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    job_name VARCHAR(64) NOT NULL COMMENT '任务名称',
    job_group VARCHAR(64) DEFAULT NULL COMMENT '任务组名',
    invoke_target VARCHAR(500) DEFAULT NULL COMMENT '调用目标字符串',
    job_message VARCHAR(500) DEFAULT NULL COMMENT '日志信息',
    status TINYINT DEFAULT 0 COMMENT '执行状态(0-正常 1-失败)',
    exception_info VARCHAR(2000) DEFAULT NULL COMMENT '异常信息',
    start_time DATETIME DEFAULT NULL COMMENT '开始时间',
    stop_time DATETIME DEFAULT NULL COMMENT '停止时间',
    duration_ms BIGINT DEFAULT NULL COMMENT '执行耗时(毫秒)',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_job_name (job_name),
    INDEX idx_start_time (start_time),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务日志表';

-- sys_announce
CREATE TABLE IF NOT EXISTS sys_announce (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    title VARCHAR(200) NOT NULL COMMENT '标题',
    content TEXT COMMENT '内容',
    notice_type TINYINT DEFAULT 1 COMMENT '1通知 2公告',
    channels VARCHAR(200) DEFAULT '["station"]' COMMENT '推送渠道JSON',
    target_type TINYINT DEFAULT 3 COMMENT '1指定用户 2按部门 3全部',
    target_ids VARCHAR(500) DEFAULT NULL COMMENT '目标ID JSON数组',
    status TINYINT DEFAULT 0 COMMENT '0草稿 1已发布',
    create_by BIGINT DEFAULT NULL COMMENT '创建人ID',
    create_name VARCHAR(50) DEFAULT NULL COMMENT '创建人昵称',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    creator VARCHAR(64) DEFAULT '',
    updater VARCHAR(64) DEFAULT '',
    deleted TINYINT DEFAULT 0,
    INDEX idx_status_time (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统通知/公告';

-- sys_user_announce
CREATE TABLE IF NOT EXISTS sys_user_announce (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '接收用户',
    announce_id BIGINT NOT NULL COMMENT '通知ID',
    is_read TINYINT DEFAULT 0 COMMENT '0未读 1已读',
    read_time DATETIME DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_announce (user_id, announce_id),
    INDEX idx_user_read (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户通知已读';

-- sys_announce_send_log
CREATE TABLE IF NOT EXISTS sys_announce_send_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    announce_id BIGINT NOT NULL,
    channel VARCHAR(50) NOT NULL COMMENT 'station等',
    status TINYINT DEFAULT 1 COMMENT '0失败 1成功',
    target_count INT DEFAULT 0,
    success_count INT DEFAULT 0,
    error_msg VARCHAR(500) DEFAULT NULL,
    send_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_announce_id (announce_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知推送日志';

-- sys_chat_message
CREATE TABLE IF NOT EXISTS sys_chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sender_id BIGINT NOT NULL,
    sender_name VARCHAR(50) DEFAULT NULL,
    sender_avatar VARCHAR(255) DEFAULT NULL,
    receiver_id BIGINT NOT NULL,
    content TEXT,
    msg_type TINYINT DEFAULT 1 COMMENT '1文本 2图片',
    is_read TINYINT DEFAULT 0,
    send_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sender (sender_id),
    INDEX idx_receiver (receiver_id),
    INDEX idx_receiver_unread (receiver_id, is_read, sender_id),
    INDEX idx_pair_time (sender_id, receiver_id, send_time),
    INDEX idx_send_time (send_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='私聊消息';

-- sys_user_blacklist
CREATE TABLE IF NOT EXISTS sys_user_blacklist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '拉黑方',
    blocked_user_id BIGINT NOT NULL COMMENT '被拉黑用户',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_blocked (user_id, blocked_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天黑名单';

-- sys_chat_group
CREATE TABLE IF NOT EXISTS sys_chat_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    avatar VARCHAR(500) DEFAULT NULL,
    owner_id BIGINT NOT NULL,
    announcement VARCHAR(500) DEFAULT NULL,
    max_members INT DEFAULT 200,
    status TINYINT DEFAULT 1 COMMENT '0解散 1正常',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群聊';

-- sys_chat_group_member
CREATE TABLE IF NOT EXISTS sys_chat_group_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    nickname VARCHAR(50) DEFAULT NULL,
    role TINYINT DEFAULT 0 COMMENT '0成员 1管理员 2群主',
    muted TINYINT DEFAULT 0,
    notify_muted TINYINT DEFAULT 0 COMMENT '0正常 1免打扰仅@提醒',
    announcement_read_time DATETIME DEFAULT NULL COMMENT '群公告已读时间',
    join_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_group_user (group_id, user_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群成员';

-- sys_chat_group_message
CREATE TABLE IF NOT EXISTS sys_chat_group_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    sender_name VARCHAR(50) DEFAULT NULL,
    sender_avatar VARCHAR(500) DEFAULT NULL,
    content TEXT NOT NULL,
    msg_type TINYINT DEFAULT 1,
    mention_ids VARCHAR(500) DEFAULT NULL COMMENT '@的用户ID列表JSON',
    send_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_group_time (group_id, send_time),
    INDEX idx_send_time (send_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群消息';

-- sys_chat_group_log
CREATE TABLE IF NOT EXISTS sys_chat_group_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL COMMENT '群ID',
    action_type VARCHAR(32) NOT NULL COMMENT '操作类型',
    operator_id BIGINT NOT NULL COMMENT '操作人',
    operator_name VARCHAR(50) DEFAULT NULL COMMENT '操作人昵称',
    target_user_id BIGINT DEFAULT NULL COMMENT '目标用户',
    target_user_name VARCHAR(50) DEFAULT NULL COMMENT '目标用户昵称',
    detail VARCHAR(500) DEFAULT NULL COMMENT '补充说明',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_group_time (group_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群聊操作日志';

-- sys_sms_log
CREATE TABLE IF NOT EXISTS sys_sms_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    phone VARCHAR(20) NOT NULL COMMENT '手机号',
    content VARCHAR(500) DEFAULT NULL COMMENT '短信内容/验证码',
    sms_type VARCHAR(20) DEFAULT 'verify_code' COMMENT 'verify_code / notice / marketing',
    template_id VARCHAR(50) DEFAULT NULL COMMENT '模板ID',
    template_params VARCHAR(500) DEFAULT NULL COMMENT '模板参数 JSON',
    provider VARCHAR(20) DEFAULT NULL COMMENT 'aliyun / tencent / console',
    status TINYINT DEFAULT 0 COMMENT '0-发送中 1-成功 2-失败',
    result_msg VARCHAR(500) DEFAULT NULL COMMENT '结果信息',
    biz_id VARCHAR(100) DEFAULT NULL COMMENT '服务商消息ID',
    send_time DATETIME DEFAULT NULL COMMENT '发送时间',
    user_id BIGINT DEFAULT NULL COMMENT '用户ID',
    biz_type VARCHAR(30) DEFAULT NULL COMMENT '业务类型',
    ip VARCHAR(50) DEFAULT NULL COMMENT 'IP地址',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_phone_create_time (phone, create_time),
    INDEX idx_create_time (create_time),
    INDEX idx_send_time (send_time),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短信发送记录表';

-- sys_config_group
CREATE TABLE IF NOT EXISTS sys_config_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    group_code VARCHAR(50) NOT NULL COMMENT '分组编码 login/register',
    group_name VARCHAR(100) NOT NULL COMMENT '分组名称',
    config_value TEXT NOT NULL COMMENT 'JSON 配置',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    creator VARCHAR(64) DEFAULT '',
    updater VARCHAR(64) DEFAULT '',
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_group_code (group_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置分组';

-- gen_table
CREATE TABLE IF NOT EXISTS gen_table (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '编号',
    table_name VARCHAR(200) DEFAULT '' COMMENT '表名称',
    table_comment VARCHAR(500) DEFAULT '' COMMENT '表描述',
    class_name VARCHAR(100) DEFAULT '' COMMENT '实体类名称',
    package_name VARCHAR(100) DEFAULT '' COMMENT '生成包路径',
    module_name VARCHAR(30) DEFAULT '' COMMENT '生成模块名',
    business_name VARCHAR(30) DEFAULT '' COMMENT '生成业务名',
    function_name VARCHAR(50) DEFAULT '' COMMENT '生成功能名',
    author VARCHAR(50) DEFAULT '' COMMENT '作者',
    gen_type VARCHAR(10) DEFAULT 'crud' COMMENT '生成类型',
    gen_path VARCHAR(200) DEFAULT '/' COMMENT '生成路径',
    front_type VARCHAR(30) DEFAULT 'element-plus' COMMENT '前端模板',
    form_layout VARCHAR(20) DEFAULT 'vertical' COMMENT '表单布局',
    parent_menu_id BIGINT DEFAULT NULL COMMENT '上级菜单ID',
    remark VARCHAR(500) DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_gen_table_name_deleted (table_name, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码生成业务表';

-- gen_table_column
CREATE TABLE IF NOT EXISTS gen_table_column (
    id BIGINT NOT NULL AUTO_INCREMENT,
    table_id BIGINT DEFAULT NULL,
    column_name VARCHAR(200) DEFAULT '',
    column_comment VARCHAR(500) DEFAULT '',
    column_type VARCHAR(100) DEFAULT '',
    java_type VARCHAR(50) DEFAULT '',
    java_field VARCHAR(200) DEFAULT '',
    is_pk TINYINT DEFAULT 0,
    is_increment TINYINT DEFAULT 0,
    is_required TINYINT DEFAULT 0,
    is_insert TINYINT DEFAULT 0,
    is_edit TINYINT DEFAULT 0,
    is_list TINYINT DEFAULT 0,
    is_query TINYINT DEFAULT 0,
    query_type VARCHAR(20) DEFAULT 'EQ',
    html_type VARCHAR(50) DEFAULT '',
    dict_type VARCHAR(100) DEFAULT '',
    sort INT DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_gen_col_table (table_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码生成字段表';

-- sys_ai_model
CREATE TABLE IF NOT EXISTS sys_ai_model (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '模型配置ID',
    name VARCHAR(100) NOT NULL COMMENT '配置名称（展示用，如「DeepSeek 官方」）',
    provider VARCHAR(32) NOT NULL COMMENT '供应商: deepseek/openai/qwen/kimi',
    model_name VARCHAR(100) NOT NULL COMMENT '模型名称（如 deepseek-chat / qwen-plus）',
    base_url VARCHAR(255) NOT NULL COMMENT 'API 基础地址（不含 /chat/completions）',
    api_key VARCHAR(1024) DEFAULT '' COMMENT 'API 密钥（SM4-CBC 加密存储）',
    temperature DECIMAL(3,2) DEFAULT 0.70 COMMENT '采样温度 0~2',
    max_tokens INT DEFAULT 4096 COMMENT '单次回复最大 Token 数',
    system_prompt VARCHAR(2000) DEFAULT NULL COMMENT '系统提示词（角色设定）',
    is_default TINYINT DEFAULT 0 COMMENT '是否默认模型 0:否 1:是（全局唯一）',
    status TINYINT DEFAULT 1 COMMENT '状态 0:禁用 1:启用',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    creator VARCHAR(64) DEFAULT '' COMMENT '创建者',
    updater VARCHAR(64) DEFAULT '' COMMENT '更新者',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除',
    INDEX idx_provider (provider),
    INDEX idx_is_default (is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 模型供应商配置表';

-- sys_ai_chat_log
CREATE TABLE IF NOT EXISTS sys_ai_chat_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    user_id BIGINT DEFAULT 0 COMMENT '提问用户ID',
    username VARCHAR(64) DEFAULT '' COMMENT '提问用户名',
    conversation_id VARCHAR(64) DEFAULT '' COMMENT '会话ID（前端生成，串联多轮对话）',
    model_id BIGINT DEFAULT 0 COMMENT '模型配置ID',
    provider VARCHAR(32) DEFAULT '' COMMENT '供应商',
    model_name VARCHAR(100) DEFAULT '' COMMENT '模型名称',
    question TEXT COMMENT '用户提问（脱敏后）',
    answer MEDIUMTEXT COMMENT 'AI 回答',
    prompt_tokens INT DEFAULT 0 COMMENT '提问 Token 消耗',
    completion_tokens INT DEFAULT 0 COMMENT '回答 Token 消耗',
    total_tokens INT DEFAULT 0 COMMENT '总 Token 消耗',
    duration_ms BIGINT DEFAULT 0 COMMENT '耗时（毫秒）',
    chat_status TINYINT DEFAULT 1 COMMENT '结果 1:成功 0:失败 2:用户中断',
    error_msg VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
    source VARCHAR(20) DEFAULT 'pc' COMMENT '来源终端 pc/mobile',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    creator VARCHAR(64) DEFAULT '' COMMENT '创建者',
    updater VARCHAR(64) DEFAULT '' COMMENT '更新者',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除',
    INDEX idx_user_id (user_id),
    INDEX idx_model_id (model_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 对话日志审计表';
