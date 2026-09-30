-- 推荐问题模块 DDL
-- 推荐问题表
CREATE TABLE `chat_recommend_question` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ai_body_code`  VARCHAR(64)  NOT NULL                COMMENT '智能体编码',
    `question`      VARCHAR(500) NOT NULL                COMMENT '问题内容',
    `description`   VARCHAR(500) DEFAULT ''               COMMENT '问题描述',
    `sort_order`    INT          DEFAULT 0               COMMENT '排序',
    `status`        TINYINT      DEFAULT 1               COMMENT '0-禁用 1-启用',
    `tenant_id`     BIGINT       NOT NULL DEFAULT 1      COMMENT '租户ID',
    `created_by`    BIGINT                               COMMENT '创建人',
    `created_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`    BIGINT                               COMMENT '更新人',
    `updated_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_ai_body_code` (`ai_body_code`),
    KEY `idx_tenant` (`tenant_id`),
    KEY `idx_code_sort` (`ai_body_code`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='推荐问题';

-- 标签字典表
CREATE TABLE `chat_recommend_tag` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(100) NOT NULL                COMMENT '标签名称',
    `tenant_id`   BIGINT       NOT NULL DEFAULT 1      COMMENT '租户ID',
    `created_by`  BIGINT                               COMMENT '创建人',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`  BIGINT                               COMMENT '更新人',
    `updated_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_name` (`tenant_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='推荐问题标签';

-- 问题-标签关联表
CREATE TABLE `chat_recommend_question_tag` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `question_id` BIGINT   NOT NULL                COMMENT '推荐问题ID',
    `tag_id`      BIGINT   NOT NULL                COMMENT '标签ID',
    `tenant_id`   BIGINT   NOT NULL DEFAULT 1      COMMENT '租户ID',
    `created_at`  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_question` (`question_id`),
    KEY `idx_tag` (`tag_id`),
    UNIQUE KEY `uk_question_tag` (`question_id`, `tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='推荐问题-标签关联';
