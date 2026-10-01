-- =====================================================================
-- 问答质量管理（错误定位 + 用户反馈 + 自动调优）DDL
-- 库：new_bi   设计文档：docs/requirements/01、03
-- 幂等：全部 CREATE TABLE IF NOT EXISTS / INSERT IGNORE
-- =====================================================================

-- ---------------------------------------------------------------------
-- 5.1 一轮问答一条（chat-server 收到 analyze 响应时写）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `chat_analysis_trace` (
  `id`                     BIGINT NOT NULL AUTO_INCREMENT,
  `chat_session_id`        VARCHAR(64)  NOT NULL,
  `chat_id`                VARCHAR(64)  NOT NULL,
  `ai_body_code`           VARCHAR(64)  DEFAULT NULL,
  `tenant_id`              BIGINT       DEFAULT NULL,
  `user_id`                BIGINT       DEFAULT NULL,
  `username`               VARCHAR(64)  DEFAULT NULL,
  `question`               TEXT,
  `recall_enabled`         TINYINT      DEFAULT 0,
  `recall_elapsed_ms`      INT          DEFAULT NULL,
  `recall_tables`          JSON         DEFAULT NULL,
  `recall_metric_codes`    JSON         DEFAULT NULL,
  `recall_dim_codes`       JSON         DEFAULT NULL,
  `meta_table_count`       INT          DEFAULT NULL,
  `meta_metric_count`      INT          DEFAULT NULL,
  `meta_dim_count`         INT          DEFAULT NULL,
  `decomposition_steps`    JSON         DEFAULT NULL,
  `execution_log`          JSON         DEFAULT NULL,
  `used_tables`            JSON         DEFAULT NULL,
  `resolved_metrics`       JSON         DEFAULT NULL,
  `resolved_dims`          JSON         DEFAULT NULL,
  `final_answer`           MEDIUMTEXT,
  `status`                 VARCHAR(16)  DEFAULT NULL COMMENT 'success/partial/failure/stopped',
  `system_b_log_path`      VARCHAR(512) DEFAULT NULL,
  `total_elapsed_ms`       INT          DEFAULT NULL,
  `llm_model`              VARCHAR(64)  DEFAULT NULL,
  `prompt_version`         VARCHAR(32)  DEFAULT NULL,
  `full_response_minio_path` VARCHAR(512) DEFAULT NULL,
  `auto_error_hint`        VARCHAR(32)  DEFAULT NULL COMMENT '自动预判错误类型',
  `created_at`             DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`             DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chat` (`chat_id`),
  KEY `idx_session` (`chat_session_id`),
  KEY `idx_agent_time` (`ai_body_code`, `created_at`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答全链路追踪';

-- ---------------------------------------------------------------------
-- 5.2 一个 query 步骤一条（chat-server /getdata/ai 写）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `chat_step_trace` (
  `id`                      BIGINT NOT NULL AUTO_INCREMENT,
  `chat_id`                 VARCHAR(64) NOT NULL,
  `step_id`                 VARCHAR(16) NOT NULL,
  `query_description`       VARCHAR(1024) DEFAULT NULL,
  `expected_table`          VARCHAR(128)  DEFAULT NULL,
  `expected_columns`        JSON          DEFAULT NULL,
  `resolved_table_id`       BIGINT        DEFAULT NULL,
  `resolved_table_name`     VARCHAR(128)  DEFAULT NULL,
  `resolved_indicator_ids`  JSON          DEFAULT NULL,
  `resolved_indicator_names` JSON         DEFAULT NULL,
  `resolved_dimension_ids`  JSON          DEFAULT NULL,
  `resolved_dimension_names` JSON         DEFAULT NULL,
  `unmatched_columns`       JSON          DEFAULT NULL,
  `filters`                 JSON          DEFAULT NULL,
  `time_range`              VARCHAR(128)  DEFAULT NULL,
  `group_expanded`          TINYINT       DEFAULT 0,
  `sql_text`                TEXT,
  `row_count`               INT           DEFAULT NULL,
  `elapsed_ms`              INT           DEFAULT NULL,
  `error_message`           VARCHAR(1024) DEFAULT NULL,
  `created_at`              DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chat_step` (`chat_id`, `step_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='取数步骤追踪';

-- ---------------------------------------------------------------------
-- 5.3 用户反馈
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `chat_feedback` (
  `id`               BIGINT NOT NULL AUTO_INCREMENT,
  `chat_session_id`  VARCHAR(64) NOT NULL,
  `chat_id`          VARCHAR(64) NOT NULL,
  `ai_body_code`     VARCHAR(64)  DEFAULT NULL,
  `tenant_id`        BIGINT       DEFAULT NULL,
  `user_id`          BIGINT       DEFAULT NULL,
  `username`         VARCHAR(64)  DEFAULT NULL,
  `question`         TEXT,
  `answer_snapshot`  TEXT,
  `rating`           TINYINT NOT NULL COMMENT '1 赞 / -1 踩',
  `error_types`      JSON         DEFAULT NULL,
  `description`      VARCHAR(1000) DEFAULT NULL,
  `status`           TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1已定位 2已修复 3已忽略',
  `diagnosis_id`     BIGINT       DEFAULT NULL,
  `created_at`       DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chat_user` (`chat_id`, `user_id`),
  KEY `idx_agent_status` (`ai_body_code`, `status`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户反馈';

-- ---------------------------------------------------------------------
-- 5.4 错误诊断（管理员定位结论）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `chat_error_diagnosis` (
  `id`                    BIGINT NOT NULL AUTO_INCREMENT,
  `chat_id`               VARCHAR(64) NOT NULL,
  `chat_session_id`       VARCHAR(64) DEFAULT NULL,
  `ai_body_code`          VARCHAR(64) DEFAULT NULL,
  `tenant_id`             BIGINT      DEFAULT NULL,
  `primary_error_type`    VARCHAR(32) NOT NULL,
  `secondary_error_types` JSON        DEFAULT NULL,
  `error_step_id`         VARCHAR(16) DEFAULT NULL,
  `expected_table`        VARCHAR(128) DEFAULT NULL,
  `actual_table`          VARCHAR(128) DEFAULT NULL,
  `expected_entities`     JSON        DEFAULT NULL COMMENT '[{type:metric|dim, code, name, actualCode}]',
  `actual_entities`       JSON        DEFAULT NULL,
  `root_cause`            VARCHAR(2000) DEFAULT NULL,
  `fix_action_type`       VARCHAR(32) DEFAULT NULL,
  `fix_action_detail`     VARCHAR(2000) DEFAULT NULL,
  `fix_status`            TINYINT NOT NULL DEFAULT 0 COMMENT '0待修复 1已修复 2已验证',
  `add_regression`        TINYINT DEFAULT 0,
  `expected_answer_keywords` JSON     DEFAULT NULL,
  `diagnosed_by`          VARCHAR(64) DEFAULT NULL,
  `diagnosed_at`          DATETIME    DEFAULT NULL,
  `verified_by`           VARCHAR(64) DEFAULT NULL,
  `verified_at`           DATETIME    DEFAULT NULL,
  `created_at`            DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`            DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chat` (`chat_id`),
  KEY `idx_agent_type` (`ai_body_code`, `primary_error_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='错误诊断';

-- ---------------------------------------------------------------------
-- 5.5 错误类型字典
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `chat_error_type_dict` (
  `id`             BIGINT NOT NULL AUTO_INCREMENT,
  `code`           VARCHAR(32) NOT NULL,
  `parent_code`    VARCHAR(32) DEFAULT NULL,
  `name`           VARCHAR(64) NOT NULL,
  `scope`          VARCHAR(8)  NOT NULL DEFAULT 'both' COMMENT 'user/admin/both',
  `pipeline_stage` VARCHAR(32) DEFAULT NULL,
  `sort`           INT DEFAULT 0,
  `enabled`        TINYINT DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='错误类型字典';

INSERT IGNORE INTO `chat_error_type_dict` (`code`,`parent_code`,`name`,`scope`,`pipeline_stage`,`sort`) VALUES
 ('INTENT',       NULL,        '意图识别错误',     'both',  'decompose', 10),
 ('TABLE_SELECT', NULL,        '选表错误',         'both',  'recall',    20),
 ('METRIC_DIM',   NULL,        '指标/维度提取错误','both',  'getdata',   30),
 ('METRIC_DIM.METRIC',    'METRIC_DIM', '指标错误', 'both', 'getdata', 31),
 ('METRIC_DIM.DIMENSION', 'METRIC_DIM', '维度错误', 'both', 'getdata', 32),
 ('DECOMPOSE',    NULL,        '拆分错误',         'both',  'decompose', 40),
 ('SUMMARY',      NULL,        '总结错误',         'both',  'summarize', 50),
 ('RESULT',       NULL,        '结果错误',         'user',  'execute',   60);

-- ---------------------------------------------------------------------
-- 4.1 调优任务
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tuning_task` (
  `id`                 BIGINT NOT NULL AUTO_INCREMENT,
  `task_no`            VARCHAR(32) NOT NULL,
  `diagnosis_id`       BIGINT      DEFAULT NULL,
  `chat_id`            VARCHAR(64) DEFAULT NULL,
  `ai_body_code`       VARCHAR(64) DEFAULT NULL,
  `tenant_id`          BIGINT      DEFAULT NULL,
  `primary_error_type` VARCHAR(32) DEFAULT NULL,
  `question`           TEXT,
  `status`             VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  `round`              INT NOT NULL DEFAULT 1,
  `verify_report_id`   BIGINT      DEFAULT NULL,
  `verify_summary`     JSON        DEFAULT NULL,
  `verify_config`      JSON        DEFAULT NULL COMMENT '任务级验证配置覆盖',
  `confirm_note`       VARCHAR(1000) DEFAULT NULL,
  `approval_id`        BIGINT      DEFAULT NULL,
  `submitted_at`       DATETIME    DEFAULT NULL,
  `submitted_by`       VARCHAR(64) DEFAULT NULL,
  `approved_at`        DATETIME    DEFAULT NULL,
  `approved_by`        VARCHAR(64) DEFAULT NULL,
  `approval_comment`   VARCHAR(1000) DEFAULT NULL,
  `published_at`       DATETIME    DEFAULT NULL,
  `published_by`       VARCHAR(64) DEFAULT NULL,
  `rolled_back_at`     DATETIME    DEFAULT NULL,
  `rolled_back_by`     VARCHAR(64) DEFAULT NULL,
  `rollback_reason`    VARCHAR(1000) DEFAULT NULL,
  `online_recheck`     JSON        DEFAULT NULL COMMENT '发布后线上复验结果',
  `observe_until`      DATETIME    DEFAULT NULL,
  `observe_alert`      JSON        DEFAULT NULL,
  `created_by`         VARCHAR(64) DEFAULT NULL,
  `created_at`         DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_no` (`task_no`),
  KEY `idx_status` (`status`),
  KEY `idx_agent` (`ai_body_code`),
  KEY `idx_diag` (`diagnosis_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调优任务';

CREATE TABLE IF NOT EXISTS `tuning_change` (
  `id`             BIGINT NOT NULL AUTO_INCREMENT,
  `task_id`        BIGINT NOT NULL,
  `seq`            INT NOT NULL DEFAULT 0,
  `source`         VARCHAR(16) NOT NULL DEFAULT 'SUGGEST' COMMENT 'SUGGEST/MANUAL',
  `rule_code`      VARCHAR(16) DEFAULT NULL,
  `confidence`     VARCHAR(8)  DEFAULT 'HIGH' COMMENT 'HIGH/MEDIUM/LOW',
  `accepted`       TINYINT NOT NULL DEFAULT 1,
  `asset_type`     VARCHAR(32) NOT NULL,
  `target_module`  VARCHAR(16) NOT NULL,
  `target_id`      VARCHAR(128) DEFAULT NULL,
  `target_label`   VARCHAR(256) DEFAULT NULL,
  `field`          VARCHAR(64)  DEFAULT NULL,
  `before_value`   TEXT,
  `after_value`    TEXT,
  `diff_summary`   VARCHAR(512) DEFAULT NULL,
  `reason`         VARCHAR(512) DEFAULT NULL,
  `apply_status`   VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  `apply_error`    VARCHAR(512) DEFAULT NULL,
  `applied_at`     DATETIME DEFAULT NULL,
  `published_at`   DATETIME DEFAULT NULL,
  `rolled_back_at` DATETIME DEFAULT NULL,
  `created_at`     DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task` (`task_id`),
  KEY `idx_asset` (`asset_type`, `target_id`, `field`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调优变更';

-- ---------------------------------------------------------------------
-- 4.2 资产快照
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `asset_snapshot` (
  `id`               BIGINT NOT NULL AUTO_INCREMENT,
  `task_id`          BIGINT NOT NULL,
  `change_id`        BIGINT DEFAULT NULL,
  `asset_type`       VARCHAR(32) NOT NULL,
  `target_module`    VARCHAR(16) NOT NULL,
  `target_id`        VARCHAR(128) DEFAULT NULL,
  `field`            VARCHAR(64)  DEFAULT NULL,
  `snapshot_value`   LONGTEXT,
  `snapshot_version` VARCHAR(32) DEFAULT NULL,
  `taken_at`         DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产快照';

-- ---------------------------------------------------------------------
-- 4.3 验证
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tuning_verify_report` (
  `id`                     BIGINT NOT NULL AUTO_INCREMENT,
  `task_id`                BIGINT NOT NULL,
  `round`                  INT NOT NULL DEFAULT 1,
  `status`                 VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
  `case_total`             INT DEFAULT 0,
  `case_done`              INT DEFAULT 0,
  `origin_fixed`           TINYINT DEFAULT NULL,
  `regression_total`       INT DEFAULT 0,
  `regression_pass`        INT DEFAULT 0,
  `regression_fail`        INT DEFAULT 0,
  `degraded_count`         INT DEFAULT 0,
  `improved_count`         INT DEFAULT 0,
  `unchanged_count`        INT DEFAULT 0,
  `avg_elapsed_before_ms`  INT DEFAULT NULL,
  `avg_elapsed_after_ms`   INT DEFAULT NULL,
  `conclusion`             VARCHAR(16) DEFAULT NULL COMMENT 'PASS/PASS_WITH_WARN/FAIL',
  `error_message`          VARCHAR(1024) DEFAULT NULL,
  `started_at`             DATETIME DEFAULT CURRENT_TIMESTAMP,
  `finished_at`            DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='验证报告';

CREATE TABLE IF NOT EXISTS `tuning_verify_case` (
  `id`                 BIGINT NOT NULL AUTO_INCREMENT,
  `report_id`          BIGINT NOT NULL,
  `case_type`          VARCHAR(16) NOT NULL COMMENT 'ORIGIN/SIMILAR/REGRESSION/MANUAL',
  `source_chat_id`     VARCHAR(64) DEFAULT NULL,
  `regression_case_id` BIGINT DEFAULT NULL,
  `question`           TEXT,
  `expected`           JSON DEFAULT NULL,
  `before_result`      JSON DEFAULT NULL,
  `after_result`       JSON DEFAULT NULL,
  `before_pass`        TINYINT DEFAULT NULL,
  `after_pass`         TINYINT DEFAULT NULL,
  `verdict`            VARCHAR(16) DEFAULT NULL,
  `judge_detail`       JSON DEFAULT NULL,
  `after_request_id`   VARCHAR(128) DEFAULT NULL,
  `elapsed_before_ms`  INT DEFAULT NULL,
  `elapsed_after_ms`   INT DEFAULT NULL,
  `status`             VARCHAR(16) DEFAULT 'PENDING',
  `error_message`      VARCHAR(1024) DEFAULT NULL,
  `created_at`         DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_report` (`report_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='验证用例结果';

CREATE TABLE IF NOT EXISTS `regression_case` (
  `id`             BIGINT NOT NULL AUTO_INCREMENT,
  `ai_body_code`   VARCHAR(64) DEFAULT NULL,
  `tenant_id`      BIGINT DEFAULT NULL,
  `question`       TEXT NOT NULL,
  `expected`       JSON DEFAULT NULL,
  `source`         VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT 'DIAGNOSIS/FEEDBACK_UP/MANUAL',
  `source_chat_id` VARCHAR(64) DEFAULT NULL,
  `tags`           JSON DEFAULT NULL,
  `enabled`        TINYINT NOT NULL DEFAULT 1,
  `last_pass_at`   DATETIME DEFAULT NULL,
  `fail_count`     INT DEFAULT 0,
  `created_by`     VARCHAR(64) DEFAULT NULL,
  `created_at`     DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_agent` (`ai_body_code`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回归测试集';

-- ---------------------------------------------------------------------
-- 4.4 验证配置（GLOBAL / AGENT:{code}）+ 发布/观察期配置
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tuning_verify_config` (
  `id`                 BIGINT NOT NULL AUTO_INCREMENT,
  `scope`              VARCHAR(80) NOT NULL DEFAULT 'GLOBAL',
  `max_cases`          INT NOT NULL DEFAULT 30,
  `similar_limit`      INT NOT NULL DEFAULT 10,
  `regression_limit`   INT NOT NULL DEFAULT 15,
  `concurrency`        INT NOT NULL DEFAULT 3,
  `case_timeout_s`     INT NOT NULL DEFAULT 120,
  `total_timeout_min`  INT NOT NULL DEFAULT 30,
  `judge_answer`       TINYINT NOT NULL DEFAULT 1,
  `judge_llm`          TINYINT NOT NULL DEFAULT 1,
  `temperature`        DECIMAL(3,2) NOT NULL DEFAULT 0.00,
  `require_approval`   TINYINT NOT NULL DEFAULT 1,
  `warn_need_note`     TINYINT NOT NULL DEFAULT 1,
  `online_recheck`     TINYINT NOT NULL DEFAULT 1,
  `observe_days`       INT NOT NULL DEFAULT 7,
  `alert_window_hours` INT NOT NULL DEFAULT 24,
  `alert_threshold`    INT NOT NULL DEFAULT 2,
  `retain_trace_days`  INT NOT NULL DEFAULT 90,
  `retain_snapshot_days` INT NOT NULL DEFAULT 180,
  `updated_by`         VARCHAR(64) DEFAULT NULL,
  `updated_at`         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope` (`scope`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='验证/发布配置';

INSERT IGNORE INTO `tuning_verify_config` (`scope`) VALUES ('GLOBAL');

-- ---------------------------------------------------------------------
-- 4.5 调优建议规则（Phase 1 内置，可编辑）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tuning_rule` (
  `id`          BIGINT NOT NULL AUTO_INCREMENT,
  `rule_code`   VARCHAR(16) NOT NULL,
  `error_type`  VARCHAR(32) NOT NULL,
  `name`        VARCHAR(128) DEFAULT NULL,
  `condition_desc` VARCHAR(512) DEFAULT NULL,
  `asset_type`  VARCHAR(32) NOT NULL,
  `field`       VARCHAR(64) DEFAULT NULL,
  `template`    VARCHAR(1024) DEFAULT NULL,
  `confidence`  VARCHAR(8) DEFAULT 'HIGH',
  `priority`    INT DEFAULT 100,
  `enabled`     TINYINT DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule` (`rule_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调优建议规则';

INSERT IGNORE INTO `tuning_rule` (`rule_code`,`error_type`,`name`,`condition_desc`,`asset_type`,`field`,`template`,`confidence`,`priority`) VALUES
 ('R1','TABLE_SELECT','应选表未被召回，补充表描述关键词','expected_table 已填且不在 recall_tables 中','TABLE_DESC','note','{before}；支持按{keywords}查询','HIGH',10),
 ('R2','TABLE_SELECT','应选表已召回但模型未选，增加区分性描述','expected_table 已填且在 recall_tables 中','TABLE_DESC','note','{before}（与 {actual_table} 的区别：{keywords}）','HIGH',20),
 ('R3','TABLE_SELECT','应选表未绑定到智能体','expected_table 不在智能体绑定表内','AGENT_TABLE','relation','绑定表 {expected_table}','HIGH',30),
 ('R4','TABLE_SELECT','召回结果过少，调大 top_k','recall_tables 数 <= 2','RECALL_CONFIG','top_k','{before} -> {after}','MEDIUM',40),
 ('R5','METRIC_DIM.METRIC','应选指标追加别名','expected_entities 含 metric','ENTITY_ALIAS','alias','{before},{keywords}','HIGH',10),
 ('R6','METRIC_DIM.DIMENSION','应选维度追加别名 / 枚举值','expected_entities 含 dim','ENTITY_ALIAS','alias','{before},{keywords}','HIGH',10),
 ('R7','METRIC_DIM','未映射列名作为别名','step_trace.unmatched_columns 非空','ENTITY_ALIAS','alias','{before},{unmatched}','MEDIUM',20),
 ('R8','DECOMPOSE','口径类：修正聚合方式','根因含 快照/累计/率','AGG_TYPE','summary','{agg}','HIGH',10),
 ('R9','DECOMPOSE','规则类：拆解提示词新版本（编辑器）','fix_action_type=PROMPT_DECOMPOSE','PROMPT_DECOMPOSE','content','基于 {before} 新建草稿','LOW',20),
 ('R10','INTENT','追加业务知识条目','fix_action_type=KNOWLEDGE','KNOWLEDGE','knowledge_element','{root_cause}','MEDIUM',10),
 ('R11','SUMMARY','总结提示词新版本（编辑器）','fix_action_type=PROMPT_SUMMARY','PROMPT_SUMMARY','content','基于 {before} 新建草稿','LOW',10),
 ('R12','SUMMARY','截断配置（Phase 2 仅提示）','根因含 截断','SYSTEM_B_CONFIG','TRUNCATE','Phase 2','LOW',20);

-- ---------------------------------------------------------------------
-- 8 审计
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tuning_audit_log` (
  `id`            BIGINT NOT NULL AUTO_INCREMENT,
  `task_id`       BIGINT NOT NULL,
  `action`        VARCHAR(32) NOT NULL,
  `operator`      VARCHAR(64) DEFAULT NULL,
  `before_status` VARCHAR(20) DEFAULT NULL,
  `after_status`  VARCHAR(20) DEFAULT NULL,
  `detail`        VARCHAR(2000) DEFAULT NULL,
  `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调优审计';

-- ---------------------------------------------------------------------
-- 通知（超级管理员待审批 / 回退通知 / 观察期告警）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tuning_notification` (
  `id`          BIGINT NOT NULL AUTO_INCREMENT,
  `type`        VARCHAR(32) NOT NULL,
  `task_id`     BIGINT DEFAULT NULL,
  `title`       VARCHAR(256) DEFAULT NULL,
  `content`     VARCHAR(2000) DEFAULT NULL,
  `target_role` VARCHAR(32) DEFAULT 'SUPER_ADMIN',
  `read_flag`   TINYINT DEFAULT 0,
  `created_at`  DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_role_read` (`target_role`, `read_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知';
