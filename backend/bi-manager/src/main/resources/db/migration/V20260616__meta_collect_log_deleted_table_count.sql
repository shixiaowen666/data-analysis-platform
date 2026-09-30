-- meta_collect_log 补 deleted_table_count（与实体 MetaCollectLog.deletedTableCount 对齐）
ALTER TABLE `meta_collect_log`
    ADD COLUMN `deleted_table_count` int DEFAULT '0' COMMENT '删除表数量（因采集范围调整）' AFTER `updated_column_count`;
