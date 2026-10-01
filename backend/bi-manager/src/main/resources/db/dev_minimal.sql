-- 本地开发/自测用最小表（生产库已有，此处仅用于沙箱联调；字段按 bi-manager 实体推导）
CREATE TABLE IF NOT EXISTS `dcar_chat_model_qa` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `chat_session_id` VARCHAR(64), `chat_id` VARCHAR(64), `item_id` INT, `question` TEXT, `answer` MEDIUMTEXT,
  `feedback` INT, `reason` VARCHAR(1000), `user_id` BIGINT, `created_by` BIGINT, `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_by` BIGINT, `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `update_by` BIGINT,
  `minio_file_path` VARCHAR(512), `record_id` BIGINT, `history_data_minio_path` VARCHAR(512), `tenant_id` BIGINT,
  `type` VARCHAR(32), `status` INT, `chat_info` MEDIUMTEXT, `query_data` MEDIUMTEXT,
  PRIMARY KEY (`id`), KEY `idx_chat` (`chat_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `olap_dim_value` (
  `id` BIGINT NOT NULL AUTO_INCREMENT, `dim_id` BIGINT, `tenant_id` BIGINT, `dim_value` VARCHAR(256), `value_count` INT,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP, `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), KEY `idx_dim` (`dim_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `dcar_chat_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT, `chat_session_id` VARCHAR(64), `ai_body_code` VARCHAR(64), `chat_name` VARCHAR(512),
  `tenant_id` BIGINT, `created_by` BIGINT, `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP, `updated_by` BIGINT,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `user_id` BIGINT, `status` INT,
  `minio_file_path` VARCHAR(512),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- 沙箱: dcar_ai_body_relation 补 tenant_id（bi-manager 实体需要）
ALTER TABLE dcar_ai_body_relation ADD COLUMN IF NOT EXISTS tenant_id BIGINT DEFAULT 1;
ALTER TABLE dcar_ai_body ADD COLUMN IF NOT EXISTS description VARCHAR(1000), ADD COLUMN IF NOT EXISTS hot_words VARCHAR(1000), ADD COLUMN IF NOT EXISTS theme_code VARCHAR(64), ADD COLUMN IF NOT EXISTS interaction_mode INT, ADD COLUMN IF NOT EXISTS authorize_strategy INT, ADD COLUMN IF NOT EXISTS tenant_id BIGINT DEFAULT 1;
