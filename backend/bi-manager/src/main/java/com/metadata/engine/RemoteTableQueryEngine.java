package com.metadata.engine;

import com.common.result.PageResult;
import com.metadata.dto.meta.RemoteTableQueryDTO;
import com.metadata.engine.model.TableMetadata;
import com.metadata.entity.MetaDataSource;

/**
 * 远程库表元数据查询引擎
 */
public interface RemoteTableQueryEngine extends MetadataCollectEngine {

    PageResult<TableMetadata> pageRemoteTables(MetaDataSource dataSource, RemoteTableQueryDTO query);
}
