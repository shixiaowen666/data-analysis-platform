package com.bi.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bi.dto.DataModelQueryDTO;
import com.bi.dto.DataModelSaveDTO;
import com.bi.dto.JoinConfigDTO;
import com.bi.entity.OlapDataModel;
import com.bi.entity.OlapDataModelDimension;
import com.bi.enums.JoinType;
import com.bi.mapper.OlapDataModelDimensionMapper;
import com.bi.mapper.OlapDataModelMapper;
import com.bi.service.IDataModelService;
import com.bi.vo.*;
import com.common.exception.BizException;
import com.metadata.entity.MetaColumn;
import com.metadata.entity.MetaDataSource;
import com.metadata.entity.MetaTable;
import com.metadata.mapper.MetaColumnMapper;
import com.metadata.mapper.MetaDataSourceMapper;
import com.metadata.mapper.MetaTableMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据模型管理 Service 实现
 */
@Service
@RequiredArgsConstructor
public class DataModelServiceImpl implements IDataModelService {

    private final OlapDataModelMapper dataModelMapper;
    private final OlapDataModelDimensionMapper dimensionMapper;
    private final MetaDataSourceMapper dataSourceMapper;
    private final MetaTableMapper metaTableMapper;
    private final MetaColumnMapper metaColumnMapper;

    @Override
    public ApiPageResult<DataModelVO> listModels(DataModelQueryDTO query, Long tenantId) {
        LambdaQueryWrapper<OlapDataModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OlapDataModel::getTenantId, tenantId)
                .like(StrUtil.isNotBlank(query.getKeyword()), OlapDataModel::getName, query.getKeyword())
                .eq(query.getStatus() != null, OlapDataModel::getStatus, query.getStatus())
                .orderByDesc(OlapDataModel::getUpdatedAt);

        Page<OlapDataModel> page = dataModelMapper.selectPage(
                new Page<>(query.getPage(), query.getPageSize()), wrapper);

        Map<Long, MetaDataSource> sourceMap = loadSourceMap(
                page.getRecords().stream().map(OlapDataModel::getSourceId).collect(Collectors.toSet()));
        Map<Long, MetaTable> tableMap = loadTableMap(
                page.getRecords().stream().map(OlapDataModel::getFactTableId).collect(Collectors.toSet()));
        Map<Long, Long> dimCountMap = countDimByModelIds(
                page.getRecords().stream().map(OlapDataModel::getId).collect(Collectors.toList()));

        List<DataModelVO> list = page.getRecords().stream()
                .map(model -> toListVO(model, sourceMap, tableMap, dimCountMap))
                .collect(Collectors.toList());

        return new ApiPageResult<>(list, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public DataModelDetailVO getDetail(Long id, Long tenantId) {
        OlapDataModel model = requireModel(id, tenantId);
        MetaDataSource source = requireSource(model.getSourceId());
        MetaTable factTable = requireMetaTable(model.getFactTableId());

        List<OlapDataModelDimension> joins = dimensionMapper.selectList(
                new LambdaQueryWrapper<OlapDataModelDimension>()
                        .eq(OlapDataModelDimension::getModelId, id)
                        .orderByAsc(OlapDataModelDimension::getId));

        Set<Long> dimTableIds = joins.stream()
                .map(OlapDataModelDimension::getDimTableId)
                .collect(Collectors.toSet());
        Map<Long, MetaTable> dimTableMap = loadTableMap(dimTableIds);

        DataModelDetailVO vo = new DataModelDetailVO();
        vo.setId(model.getId());
        vo.setName(model.getName());
        vo.setDescription(model.getDescription());
        vo.setSourceId(model.getSourceId());
        vo.setSourceName(source.getName());
        vo.setFactTableId(model.getFactTableId());
        vo.setFactTableName(factTable.getTableName());
        vo.setStatus(model.getStatus());
        vo.setCreatedAt(model.getCreatedAt());
        vo.setUpdatedAt(model.getUpdatedAt());
        vo.setJoins(joins.stream().map(j -> toJoinVO(j, dimTableMap)).collect(Collectors.toList()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createModel(DataModelSaveDTO dto, Long tenantId) {
        validateSaveRequest(dto, tenantId, null);
        OlapDataModel model = new OlapDataModel();
        model.setName(dto.getName().trim());
        model.setDescription(dto.getDescription());
        model.setSourceId(dto.getSourceId());
        model.setFactTableId(dto.getFactTableId());
        model.setStatus(1);
        model.setTenantId(tenantId);
        dataModelMapper.insert(model);
        saveJoins(model.getId(), dto.getJoins(), tenantId);
        return model.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateModel(Long id, DataModelSaveDTO dto, Long tenantId) {
        OlapDataModel model = requireModel(id, tenantId);
        validateSaveRequest(dto, tenantId, id);

        model.setName(dto.getName().trim());
        model.setDescription(dto.getDescription());
        model.setSourceId(dto.getSourceId());
        model.setFactTableId(dto.getFactTableId());
        dataModelMapper.updateById(model);

        dimensionMapper.delete(new LambdaQueryWrapper<OlapDataModelDimension>()
                .eq(OlapDataModelDimension::getModelId, id));
        saveJoins(id, dto.getJoins(), tenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteModel(Long id, Long tenantId) {
        requireModel(id, tenantId);
        dimensionMapper.delete(new LambdaQueryWrapper<OlapDataModelDimension>()
                .eq(OlapDataModelDimension::getModelId, id));
        dataModelMapper.deleteById(id);
    }

    @Override
    public List<CandidateTableVO> listCandidateFactTables(Long sourceId, Long tenantId) {
        requireSource(sourceId);
        Set<Long> usedFactTableIds = dataModelMapper.selectList(
                        new LambdaQueryWrapper<OlapDataModel>().eq(OlapDataModel::getTenantId, tenantId))
                .stream()
                .map(OlapDataModel::getFactTableId)
                .collect(Collectors.toSet());

        List<MetaTable> tables = metaTableMapper.selectList(
                new LambdaQueryWrapper<MetaTable>()
                        .eq(MetaTable::getSourceId, sourceId)
                        .orderByAsc(MetaTable::getTableName));

        return tables.stream()
                .filter(t -> !usedFactTableIds.contains(t.getId()))
                .map(this::toCandidateTableVO)
                .collect(Collectors.toList());
    }

    @Override
    public CandidateDimTablesVO listCandidateDimTables(Long sourceId, Long factTableId, Long tenantId) {
        requireSource(sourceId);

        LambdaQueryWrapper<MetaTable> wrapper = new LambdaQueryWrapper<MetaTable>()
                .eq(MetaTable::getSourceId, sourceId)
                .orderByAsc(MetaTable::getTableName);
        if (factTableId != null) {
            wrapper.ne(MetaTable::getId, factTableId);
        }
        List<MetaTable> dimTables = metaTableMapper.selectList(wrapper);

        CandidateDimTablesVO vo = new CandidateDimTablesVO();
        vo.setDimTables(dimTables.stream().map(this::toCandidateTableVO).collect(Collectors.toList()));

        if (factTableId != null) {
            vo.setFactColumns(listColumns(factTableId));
        } else {
            vo.setFactColumns(Collections.emptyList());
        }

        Map<String, List<CandidateColumnVO>> dimColumns = new LinkedHashMap<>();
        for (MetaTable table : dimTables) {
            dimColumns.put(String.valueOf(table.getId()), listColumns(table.getId()));
        }
        vo.setDimColumns(dimColumns);
        return vo;
    }

    private void validateSaveRequest(DataModelSaveDTO dto, Long tenantId, Long excludeModelId) {
        normalizeJoins(dto.getJoins());
        requireSource(dto.getSourceId());

        MetaTable factTable = requireMetaTable(dto.getFactTableId());
        if (!Objects.equals(factTable.getSourceId(), dto.getSourceId())) {
            throw new BizException(400, "主表不属于所选数据源");
        }

        LambdaQueryWrapper<OlapDataModel> factUsedWrapper = new LambdaQueryWrapper<OlapDataModel>()
                .eq(OlapDataModel::getTenantId, tenantId)
                .eq(OlapDataModel::getFactTableId, dto.getFactTableId());
        if (excludeModelId != null) {
            factUsedWrapper.ne(OlapDataModel::getId, excludeModelId);
        }
        if (dataModelMapper.selectCount(factUsedWrapper) > 0) {
            throw new BizException(400, "该主表已配置在其他模型中");
        }

        Set<Long> dimTableIds = new HashSet<>();
        for (JoinConfigDTO join : dto.getJoins()) {
            JoinType.validate(join.getJoinType());
            if (Objects.equals(join.getDimTableId(), dto.getFactTableId())) {
                throw new BizException(400, "关联表不能与主表相同");
            }
            if (!dimTableIds.add(join.getDimTableId())) {
                throw new BizException(400, "关联表不能重复配置");
            }

            MetaTable dimTable = requireMetaTable(join.getDimTableId());
            if (!Objects.equals(dimTable.getSourceId(), dto.getSourceId())) {
                throw new BizException(400, "关联表 " + dimTable.getTableName() + " 不属于所选数据源");
            }

            requireColumnExists(dto.getFactTableId(), join.getFactFkColumn(), "主表");
            requireColumnExists(join.getDimTableId(), join.getDimPkColumn(), "关联表");
        }
    }

    private void normalizeJoins(List<JoinConfigDTO> joins) {
        if (joins == null) {
            return;
        }
        for (JoinConfigDTO join : joins) {
            join.setJoinType(JoinType.normalize(join.getJoinType()));
        }
    }

    private void requireColumnExists(Long tableId, String columnName, String tableLabel) {
        Long count = metaColumnMapper.selectCount(
                new LambdaQueryWrapper<MetaColumn>()
                        .eq(MetaColumn::getTableId, tableId)
                        .eq(MetaColumn::getColumnName, columnName));
        if (count == null || count == 0) {
            throw new BizException(400, tableLabel + "不存在字段: " + columnName);
        }
    }

    private void saveJoins(Long modelId, List<JoinConfigDTO> joins, Long tenantId) {
        for (JoinConfigDTO join : joins) {
            OlapDataModelDimension dim = new OlapDataModelDimension();
            dim.setModelId(modelId);
            dim.setDimTableId(join.getDimTableId());
            dim.setJoinType(join.getJoinType());
            dim.setFactFkColumn(join.getFactFkColumn());
            dim.setDimPkColumn(join.getDimPkColumn());
            dim.setTenantId(tenantId);
            dimensionMapper.insert(dim);
        }
    }

    private List<CandidateColumnVO> listColumns(Long tableId) {
        return metaColumnMapper.selectList(
                        new LambdaQueryWrapper<MetaColumn>()
                                .eq(MetaColumn::getTableId, tableId)
                                .orderByAsc(MetaColumn::getOrdinal)
                                .orderByAsc(MetaColumn::getColumnName))
                .stream()
                .map(col -> {
                    CandidateColumnVO vo = new CandidateColumnVO();
                    vo.setColumnName(col.getColumnName());
                    vo.setDataType(col.getDataType());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    private OlapDataModel requireModel(Long id, Long tenantId) {
        OlapDataModel model = dataModelMapper.selectById(id);
        if (model == null || !Objects.equals(model.getTenantId(), tenantId)) {
            throw new BizException(404, "数据模型不存在");
        }
        return model;
    }

    private MetaDataSource requireSource(Long sourceId) {
        MetaDataSource source = dataSourceMapper.selectById(sourceId);
        if (source == null) {
            throw new BizException(404, "数据源不存在");
        }
        return source;
    }

    private MetaTable requireMetaTable(Long tableId) {
        MetaTable table = metaTableMapper.selectById(tableId);
        if (table == null) {
            throw new BizException(404, "表不存在: " + tableId);
        }
        return table;
    }

    private Map<Long, MetaDataSource> loadSourceMap(Set<Long> sourceIds) {
        if (sourceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return dataSourceMapper.selectBatchIds(sourceIds).stream()
                .collect(Collectors.toMap(MetaDataSource::getId, s -> s, (a, b) -> a));
    }

    private Map<Long, MetaTable> loadTableMap(Set<Long> tableIds) {
        if (tableIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return metaTableMapper.selectBatchIds(tableIds).stream()
                .collect(Collectors.toMap(MetaTable::getId, t -> t, (a, b) -> a));
    }

    private Map<Long, Long> countDimByModelIds(List<Long> modelIds) {
        if (modelIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<OlapDataModelDimension> dims = dimensionMapper.selectList(
                new LambdaQueryWrapper<OlapDataModelDimension>()
                        .in(OlapDataModelDimension::getModelId, modelIds));
        return dims.stream().collect(Collectors.groupingBy(
                OlapDataModelDimension::getModelId, Collectors.counting()));
    }

    private DataModelVO toListVO(OlapDataModel model,
                                 Map<Long, MetaDataSource> sourceMap,
                                 Map<Long, MetaTable> tableMap,
                                 Map<Long, Long> dimCountMap) {
        DataModelVO vo = new DataModelVO();
        vo.setId(model.getId());
        vo.setName(model.getName());
        vo.setSourceId(model.getSourceId());
        vo.setStatus(model.getStatus());
        vo.setUpdatedAt(model.getUpdatedAt());

        MetaDataSource source = sourceMap.get(model.getSourceId());
        vo.setSourceName(source != null ? source.getName() : null);

        MetaTable factTable = tableMap.get(model.getFactTableId());
        vo.setFactTableName(factTable != null ? factTable.getTableName() : null);

        Long dimCount = dimCountMap.get(model.getId());
        vo.setDimTableCount(dimCount != null ? dimCount.intValue() : 0);
        return vo;
    }

    private DataModelJoinVO toJoinVO(OlapDataModelDimension join, Map<Long, MetaTable> dimTableMap) {
        DataModelJoinVO vo = new DataModelJoinVO();
        vo.setId(join.getId());
        vo.setDimTableId(join.getDimTableId());
        vo.setJoinType(join.getJoinType());
        vo.setFactFkColumn(join.getFactFkColumn());
        vo.setDimPkColumn(join.getDimPkColumn());
        MetaTable dimTable = dimTableMap.get(join.getDimTableId());
        vo.setDimTableName(dimTable != null ? dimTable.getTableName() : null);
        return vo;
    }

    private CandidateTableVO toCandidateTableVO(MetaTable table) {
        CandidateTableVO vo = new CandidateTableVO();
        vo.setTableId(table.getId());
        vo.setTableName(table.getTableName());
        vo.setTableComment(table.getTableComment());
        return vo;
    }
}
