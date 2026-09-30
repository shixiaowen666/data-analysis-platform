package com.metadata.engine.factory;

import com.common.enums.DbTypeEnum;
import com.metadata.engine.MetadataCollectEngine;
import com.metadata.engine.RemoteTableQueryEngine;
import com.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 元数据采集引擎工厂
 */
@Component
public class MetadataEngineFactory {

    private final List<MetadataCollectEngine> engines;

    public MetadataEngineFactory(List<MetadataCollectEngine> engines) {
        this.engines = engines;
    }

    public MetadataCollectEngine getEngine(String dbTypeName) {
        DbTypeEnum dbType = DbTypeEnum.ofName(dbTypeName);
        return engines.stream()
                .filter(engine -> engine.supports(dbType))
                .findFirst()
                .orElseThrow(() -> new BizException("暂不支持该数据库类型的元数据采集: " + dbTypeName));
    }

    public RemoteTableQueryEngine getQueryEngine(String dbTypeName) {
        MetadataCollectEngine engine = getEngine(dbTypeName);
        if (!(engine instanceof RemoteTableQueryEngine)) {
            throw new BizException("暂不支持该数据库类型的远程表查询: " + dbTypeName);
        }
        return (RemoteTableQueryEngine) engine;
    }
}
