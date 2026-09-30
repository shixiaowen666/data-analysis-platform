package com.metadata.engine.service;

import com.common.exception.BizException;
import com.common.result.PageResult;
import com.metadata.dto.meta.RemoteTableQueryDTO;
import com.metadata.engine.RemoteTableQueryEngine;
import com.metadata.engine.factory.MetadataEngineFactory;
import com.metadata.engine.model.TableMetadata;
import com.metadata.entity.MetaDataSource;
import com.metadata.service.MetaDataSourceService;
import com.common.util.RowCountFormatter;
import com.metadata.vo.meta.RemoteTableVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 远程库表元数据查询服务
 */
@Service
@RequiredArgsConstructor
public class RemoteTableQueryService {

    private final MetaDataSourceService metaDataSourceService;
    private final MetadataEngineFactory metadataEngineFactory;

    public PageResult<RemoteTableVO> pageRemoteTables(RemoteTableQueryDTO query) {
        RemoteTableQueryDTO params = query != null ? query : new RemoteTableQueryDTO();
        if (params.getSourceId() == null) {
            throw new BizException("数据源 ID 不能为空");
        }
        MetaDataSource dataSource = metaDataSourceService.getById(params.getSourceId());
        if (dataSource == null) {
            throw new BizException("数据源不存在");
        }
        RemoteTableQueryEngine engine = metadataEngineFactory.getQueryEngine(dataSource.getDbType());
        PageResult<TableMetadata> pageResult = engine.pageRemoteTables(dataSource, params);
        List<RemoteTableVO> records = pageResult.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return new PageResult<>(pageResult.getTotal(), records,
                pageResult.getPage(), pageResult.getPageSize());
    }

    private RemoteTableVO toVO(TableMetadata metadata) {
        RemoteTableVO vo = new RemoteTableVO();
        vo.setTableName(metadata.getTableName());
        vo.setTableComment(metadata.getTableComment());
        vo.setRowCountEstimate(metadata.getRowCountEstimate());
        vo.setRowCountDisplay(RowCountFormatter.format(metadata.getRowCountEstimate()));
        return vo;
    }
}
