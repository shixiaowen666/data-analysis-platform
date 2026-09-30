-- ========================================
-- 元数据模块表结构
-- ========================================

CREATE TABLE IF NOT EXISTS `meta_data_source` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(128) NOT NULL COMMENT '数据源别名',
  `db_type` varchar(32) NOT NULL COMMENT '数据库类型 MySQL/PostgreSQL/Oracle/达梦/GaussDB/ClickHouse',
  `host` varchar(256) NOT NULL COMMENT '主机地址',
  `port` int NOT NULL COMMENT '端口号',
  `username` varchar(128) NOT NULL COMMENT '账号',
  `password` varchar(256) NOT NULL COMMENT '密码明文存储',
  `default_db` varchar(128) NOT NULL COMMENT '数据库/SID/模式名',
  `schema_name` varchar(128) DEFAULT NULL COMMENT 'Schema（Oracle/PG/GaussDB 选填）',
  `jdbc_url` varchar(512) DEFAULT NULL COMMENT 'JDBC 连接 URL（后端自动生成，前端只读）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1-启用 0-停用',
  `tenant_id` bigint NOT NULL COMMENT '租户 ID',
  `created_by` bigint NOT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint NOT NULL COMMENT '修改人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据源注册表';

CREATE TABLE IF NOT EXISTS `meta_table` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `source_id` bigint NOT NULL COMMENT '关联 meta_data_source.id',
  `tenant_id` bigint NOT NULL COMMENT '租户 ID',
  `schema_name` varchar(128) NOT NULL COMMENT 'Schema/Database 名',
  `table_name` varchar(256) NOT NULL COMMENT '表名',
  `table_comment` varchar(512) DEFAULT NULL COMMENT '表注释',
  `row_count_estimate` bigint DEFAULT NULL COMMENT '预估行数',
  `last_collected_at` datetime DEFAULT NULL COMMENT '最近采集时间',
  `created_by` bigint NOT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint NOT NULL COMMENT '修改人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_source` (`source_id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='元数据-表';

CREATE TABLE IF NOT EXISTS `meta_column` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `table_id` bigint NOT NULL COMMENT '关联 meta_table.id',
  `tenant_id` bigint NOT NULL COMMENT '租户 ID',
  `column_name` varchar(256) NOT NULL COMMENT '列名',
  `data_type` varchar(64) DEFAULT NULL COMMENT '数据类型',
  `is_nullable` tinyint DEFAULT '1' COMMENT '是否可空 1-是 0-否',
  `is_pk` tinyint DEFAULT '0' COMMENT '是否主键 1-是 0-否',
  `default_value` varchar(256) DEFAULT NULL COMMENT '默认值',
  `column_comment` varchar(512) DEFAULT NULL COMMENT '字段注释',
  `ordinal` int DEFAULT '0' COMMENT '字段序号',
  `created_by` bigint NOT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint NOT NULL COMMENT '修改人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_table` (`table_id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='元数据-字段';

CREATE TABLE IF NOT EXISTS `meta_collect_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `source_id` bigint NOT NULL COMMENT '关联 meta_data_source.id',
  `tenant_id` bigint NOT NULL COMMENT '租户 ID',
  `collect_type` varchar(16) NOT NULL DEFAULT 'full' COMMENT '采集方式：full-全库采集 select-选表采集',
  `status` varchar(16) NOT NULL COMMENT '采集状态：RUNNING/SUCCESS/FAIL',
  `table_names` text COMMENT '采集的表名列表（选表时记录）',
  `table_count` int DEFAULT '0' COMMENT '新增表数量',
  `column_count` int DEFAULT '0' COMMENT '新增字段数量',
  `updated_table_count` int DEFAULT '0' COMMENT '修改表数量（注释/行数等变更）',
  `updated_column_count` int DEFAULT '0' COMMENT '修改字段数量',
  `deleted_table_count` int DEFAULT '0' COMMENT '删除表数量（因采集范围调整）',
  `deleted_column_count` int DEFAULT '0' COMMENT '删除字段数量',
  `collect_log` text COMMENT '采集过程日志（JSON，成功/失败均记录步骤）',
  `error_msg` text COMMENT '失败原因（仅 FAIL 时有值）',
  `started_at` datetime DEFAULT NULL COMMENT '开始时间',
  `finished_at` datetime DEFAULT NULL COMMENT '结束时间',
  `created_by` bigint NOT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` bigint NOT NULL COMMENT '修改人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_source` (`source_id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采集日志';

CREATE TABLE IF NOT EXISTS `meta_select_table` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `datasource_id` int DEFAULT NULL COMMENT '数据源id',
  `tenant_id` int DEFAULT NULL COMMENT '租户id',
  `table_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '选中的表名',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据源选中表';
