package com.metadata.service.impl;



import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.common.exception.BizException;

import com.metadata.entity.MetaSelectTable;

import com.metadata.mapper.MetaSelectTableMapper;

import com.metadata.service.MetaDataSourceService;

import com.metadata.service.MetaSelectTableService;

import com.metadata.vo.meta.MetaSelectTableVO;

import org.apache.commons.lang3.StringUtils;

import org.springframework.context.annotation.Lazy;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;



import java.util.ArrayList;

import java.util.LinkedHashSet;

import java.util.List;

import java.util.stream.Collectors;



/**

 * 数据源选中表 Service 实现

 */

@Service

public class MetaSelectTableServiceImpl extends ServiceImpl<MetaSelectTableMapper, MetaSelectTable>

        implements MetaSelectTableService {



    private static final Long DEFAULT_TENANT_ID = 0L;



    private final MetaDataSourceService metaDataSourceService;



    public MetaSelectTableServiceImpl(@Lazy MetaDataSourceService metaDataSourceService) {

        this.metaDataSourceService = metaDataSourceService;

    }



    @Override

    public MetaSelectTableVO listSelected(Integer datasourceId) {

        if (datasourceId == null) {

            throw new BizException("数据源 ID 不能为空");

        }

        List<String> tableNames = list(new LambdaQueryWrapper<MetaSelectTable>()

                .eq(MetaSelectTable::getDatasourceId, datasourceId)

                .orderByAsc(MetaSelectTable::getTableName))

                .stream()

                .map(MetaSelectTable::getTableName)

                .filter(StringUtils::isNotBlank)

                .collect(Collectors.toList());



        MetaSelectTableVO vo = new MetaSelectTableVO();

        vo.setDatasourceId(datasourceId);

        vo.setTableNames(tableNames);

        vo.setSelectedCount(tableNames.size());

        return vo;

    }



    @Override

    @Transactional(rollbackFor = Exception.class)

    public void saveSelected(Integer datasourceId, List<String> tableNames, Long tenantId) {

        if (datasourceId == null) {

            throw new BizException("数据源 ID 不能为空");

        }

        if (metaDataSourceService.getById(datasourceId.longValue()) == null) {

            throw new BizException("数据源不存在");

        }



        remove(new LambdaQueryWrapper<MetaSelectTable>()

                .eq(MetaSelectTable::getDatasourceId, datasourceId));



        if (tableNames == null || tableNames.isEmpty()) {

            return;

        }



        LinkedHashSet<String> uniqueNames = tableNames.stream()

                .filter(StringUtils::isNotBlank)

                .map(String::trim)

                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (uniqueNames.isEmpty()) {

            return;

        }



        Long resolvedTenantId = tenantId != null ? tenantId : DEFAULT_TENANT_ID;

        List<MetaSelectTable> entities = new ArrayList<>();

        for (String tableName : uniqueNames) {

            MetaSelectTable entity = new MetaSelectTable();

            entity.setDatasourceId(datasourceId);

            entity.setTenantId(resolvedTenantId.intValue());

            entity.setTableName(tableName);

            entities.add(entity);

        }

        saveBatch(entities);

    }

}

