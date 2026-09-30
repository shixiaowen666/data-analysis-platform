package com.metadata.engine;

import com.common.enums.DbTypeEnum;
import com.metadata.engine.model.CollectContext;
import com.metadata.engine.model.CollectResult;

/**
 * 元数据采集引擎接口
 */
public interface MetadataCollectEngine {

    boolean supports(DbTypeEnum dbType);

    CollectResult collect(CollectContext context);
}
