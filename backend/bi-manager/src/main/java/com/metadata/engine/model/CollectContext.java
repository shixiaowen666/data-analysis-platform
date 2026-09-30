package com.metadata.engine.model;

import com.metadata.engine.support.CollectLogWriter;
import com.metadata.entity.MetaCollectLog;
import com.metadata.entity.MetaDataSource;
import lombok.Data;

/**
 * 采集上下文
 */
@Data
public class CollectContext {

    private MetaDataSource dataSource;

    private MetaCollectLog collectLog;

    private CollectRequest request;

    private CollectLogWriter logWriter;
}
