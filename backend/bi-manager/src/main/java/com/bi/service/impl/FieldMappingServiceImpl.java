package com.bi.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bi.dto.*;
import com.bi.entity.OlapBasicPro;
import com.bi.entity.OlapTableFieldMapping;
import com.bi.entity.OlapTablePro;
import com.bi.enums.*;
import com.bi.mapper.OlapBasicProMapper;
import com.bi.mapper.OlapTableFieldMappingMapper;
import com.bi.mapper.OlapTableProMapper;
import com.bi.service.IFieldMappingService;
import com.bi.service.IMetricService;
import com.bi.vo.*;
import com.common.base.LoginUser;
import com.common.base.UserThreadLocal;
import com.common.exception.BizException;
import com.common.models.SaasUser;
import com.common.util.JdbcConnectionFactory;
import com.metadata.entity.MetaColumn;
import com.metadata.entity.MetaDataSource;
import com.metadata.entity.MetaTable;
import com.metadata.mapper.MetaColumnMapper;
import com.metadata.mapper.MetaDataSourceMapper;
import com.metadata.mapper.MetaTableMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 字段映射管理 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FieldMappingServiceImpl extends ServiceImpl<OlapTableFieldMappingMapper, OlapTableFieldMapping> implements IFieldMappingService {

    private final OlapTableProMapper tableProMapper;
    private final OlapTableFieldMappingMapper fieldMappingMapper;
    private final MetaTableMapper metaTableMapper;
    private final MetaDataSourceMapper metaDataSourceMapper;
    private final MetaColumnMapper metaColumnMapper;
    private final IMetricService metricService;
    private final OlapBasicProMapper olapBasicProMapper;

    // ===================== F1: 分析表列表 =====================

    @Override
    public ApiPageResult<OlapTableVO> listTables(OlapTableQueryDTO query, Long tenantId) {
        LambdaQueryWrapper<OlapTablePro> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OlapTablePro::getTenantId, tenantId)
                .ne(OlapTablePro::getStatus, TableStatus.DELETED.getCode())
                .eq(query.getSourceId() != null, OlapTablePro::getSourceId, query.getSourceId())
                .eq(StrUtil.isNotBlank(query.getTbTypeKey()), OlapTablePro::getTbTypeKey, query.getTbTypeKey())
                .eq(StrUtil.isNotBlank(query.getTypeKey()), OlapTablePro::getTypeKey, query.getTypeKey())
                .eq(query.getStatus() != null, OlapTablePro::getStatus, query.getStatus())
                .and(StrUtil.isNotBlank(query.getKeyword()), w -> w
                        .like(OlapTablePro::getTbName, query.getKeyword())
                        .or()
                        .like(OlapTablePro::getCnName, query.getKeyword()))
                .orderByAsc(OlapTablePro::getCreatedAt);

        Page<OlapTablePro> page = tableProMapper.selectPage(
                new Page<>(query.getPage(), query.getPageSize()), wrapper);

        // 加载数据源信息
        Map<Long, MetaDataSource> sourceMap = loadSourceMap(page.getRecords());

        List<OlapTableVO> list = page.getRecords().stream()
                .map(table -> toListVO(table, sourceMap))
                .collect(Collectors.toList());

        return new ApiPageResult<>(list, page.getTotal(), page.getCurrent(), page.getSize());
    }

    // ===================== 详情 =====================

    @Override
    public OlapTableDetailVO getTableDetail(Long tableId, Long tenantId) {
        OlapTablePro table = requireTable(tableId, tenantId);

        OlapTableDetailVO vo = new OlapTableDetailVO();
        vo.setId(table.getId());
        vo.setMetaTableId(table.getMetaTableId());
        vo.setDbName(table.getDbName());
        vo.setTbName(table.getTbName());
        vo.setCnName(table.getCnName());
        vo.setTbCnName(table.getTbCnName());
        vo.setNote(table.getNote());
        vo.setType(table.getType());
        vo.setTypeKey(table.getTypeKey());
        vo.setTbType(table.getTbType());
        vo.setTbTypeKey(table.getTbTypeKey());
        vo.setStatus(table.getStatus());
        vo.setSourceId(table.getSourceId());
        vo.setQuerySql(table.getQuerySql());
        vo.setViewSql(table.getViewSql());
        vo.setTenantId(table.getTenantId());
        vo.setCreatedBy(table.getCreatedBy());
        vo.setCreatedAt(table.getCreatedAt());
        vo.setUpdatedBy(table.getUpdatedBy());
        vo.setUpdatedAt(table.getUpdatedAt());
        if("0".equals(table.getTbType())){
            vo.setFields(getFieldMappings(tableId, tenantId));
        }else if("1".equals(table.getTbType())){
            List<OlapTableFieldMapping> mappings = fieldMappingMapper.selectList(
                    new LambdaQueryWrapper<OlapTableFieldMapping>()
                            .eq(OlapTableFieldMapping::getTableId, tableId)
                            .in(OlapTableFieldMapping::getStatus, 1, 2)
                            .orderByAsc(OlapTableFieldMapping::getHeight));
            List<FieldMappingVO> result = new ArrayList<>();
            for (OlapTableFieldMapping mapping : mappings) {
                result.add(toFieldMappingVO(mapping));
            }
            vo.setFields(result);
        }
        return vo;
    }

    // ===================== F2: 注册物理表 =====================

    @Override
    public List<UnregisteredTableVO> listUnregisteredTables(Long sourceId, Long tenantId) {
        List<MetaTable> allTables = metaTableMapper.selectList(
                new LambdaQueryWrapper<MetaTable>()
                        .eq(MetaTable::getSourceId, sourceId)
                        .orderByAsc(MetaTable::getTableName));

        Set<Long> registeredPlusIds = tableProMapper.selectList(
                        new LambdaQueryWrapper<OlapTablePro>()
                                .eq(OlapTablePro::getTenantId, tenantId)
                                .ne(OlapTablePro::getStatus, TableStatus.DELETED.getCode()))
                .stream()
                .map(OlapTablePro::getMetaTableId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return allTables.stream().map(table -> {
            UnregisteredTableVO vo = new UnregisteredTableVO();
            vo.setTableId(table.getId());
            vo.setTableName(table.getTableName());
            vo.setTableComment(table.getTableComment());
            vo.setRegistered(registeredPlusIds.contains(table.getId()));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long registerPhysicalTable(PhysicalTableRegisterDTO dto, Long tenantId) {
        List<Long> registered = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        MetaDataSource dataSource = metaDataSourceMapper.selectById(dto.getSourceId());
        if (dataSource == null) {
            throw new BizException(404, "数据源不存在: " + dto.getSourceId());
        }
        for (Long tableId : dto.getTableIds()) {
            MetaTable metaTable = metaTableMapper.selectById(tableId);
            if (metaTable == null) {
                throw new BizException(404, "表不存在: " + tableId);
            }
            Long existingCount = tableProMapper.selectCount(
                    new LambdaQueryWrapper<OlapTablePro>()
                            .eq(OlapTablePro::getMetaTableId, tableId)
                            .eq(OlapTablePro::getTenantId, tenantId)
                            .ne(OlapTablePro::getStatus, TableStatus.DELETED.getCode()));
            if (existingCount != null && existingCount > 0) {
                throw new BizException(400, "该表已注册，请勿重复注册: " + metaTable.getTableName());
            }

            OlapTablePro table = new OlapTablePro();
            table.setMetaTableId(tableId);
            table.setDbName(resolveDbName(dataSource.getDbType(), dataSource.getDefaultDb(), dataSource.getSchemaName(), dataSource.getUsername()));
            table.setTbName(metaTable.getTableName());
            table.setCnName(metaTable.getTableComment());
            table.setTbCnName(metaTable.getTableComment());
            table.setNote(metaTable.getTableComment());
            table.setTypeKey("fact"); // 默认业务类型
            table.setType(metaTable.getTableComment());
            table.setTbType("0");
            table.setTbTypeKey("0"); // 物理表
            table.setStatus(TableStatus.ONLINE.getCode()); // 待上线
            table.setTenantId(tenantId);
            table.setCreatedAt(now);
            table.setUpdatedAt(now);
            table.setSourceId(dto.getSourceId());

            tableProMapper.insert(table);
            registered.add(table.getId());

            // 导入字段映射
            importColumnsToFieldMapping(table.getId(), tableId, tenantId);
        }

        if (registered.isEmpty()) {
            throw new BizException(400, "请至少选择一张表");
        }

        return registered.get(0);
    }

    // ===================== F3: 注册视图 =====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long registerView(ViewRegisterDTO dto, Long tenantId) {
        if (StrUtil.isBlank(dto.getViewEnName()) || !dto.getViewEnName().startsWith("view_")) {
            throw new BizException(400, "视图英文名必须以 view_ 开头");
        }
        if (CollectionUtil.isEmpty(dto.getColumns())) {
            throw new BizException(400, "视图字段不能为空");
        }

        // 重名校验
        QueryWrapper<OlapTablePro> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(OlapTablePro::getTbName, dto.getViewEnName()).eq(OlapTablePro::getTbType, "1");
        if (dto.getId() != null) {
            queryWrapper.lambda().ne(OlapTablePro::getId, dto.getId());
        }
        if (tableProMapper.selectCount(queryWrapper) > 0) {
            throw new BizException(400, "视图英文名已存在: " + dto.getViewEnName());
        }

        MetaDataSource dataSource = metaDataSourceMapper.selectById(dto.getSourceId());
        if (dataSource == null) {
            throw new BizException(404, "数据源不存在: " + dto.getSourceId());
        }
        LocalDateTime now = LocalDateTime.now();
        OlapTablePro table;
        if (dto.getId() != null) {
            // 更新
            table = requireTable(dto.getId(), tenantId);
            if (!"1".equals(table.getTbTypeKey())) {
                throw new BizException(400, "该表不是视图");
            }
            table.setTbName(dto.getViewEnName());
            table.setCnName(dto.getViewCnName());
            table.setTbCnName(dto.getViewCnName());
            table.setType(dto.getViewCnName());
            table.setSourceId(dto.getSourceId());
            table.setViewSql(dto.getSql());
            table.setNote(dto.getNote());
            table.setUpdatedAt(now);
            tableProMapper.updateById(table);

            // 仅当 column 带了 compareStatus 时才处理字段映射（说明用户点了重新解析）
            boolean hasCompareStatus = dto.getColumns().stream()
                    .anyMatch(c -> StrUtil.isNotBlank(c.getCompareStatus()));
            if (hasCompareStatus) {
                List<OlapTableFieldMapping> existingMappings = fieldMappingMapper.selectList(
                        new LambdaQueryWrapper<OlapTableFieldMapping>()
                                .eq(OlapTableFieldMapping::getTableId, table.getId())
                                .in(OlapTableFieldMapping::getStatus, 1, 2));
                Map<String, OlapTableFieldMapping> existingMap = existingMappings.stream()
                        .collect(Collectors.toMap(OlapTableFieldMapping::getFieldKey, m -> m, (a, b) -> a));

                for (ParseColumnVO item : dto.getColumns()) {
                    String status = item.getCompareStatus();
                    if ("0".equals(status)) {
                        // 保留：更新 fieldType/fieldName，不动注册信息
                        OlapTableFieldMapping existing = existingMap.get(item.getFieldKey());
                        if (existing != null) {
                            existing.setFieldName(item.getFieldName());
                            existing.setFieldType(item.getFieldType());
                            existing.setFieldTypeName(item.getFieldType());
                            existing.setUpdatedAt(now);
                            fieldMappingMapper.updateById(existing);
                        }
                    } else if ("2".equals(status)) {
                        // 废弃：标记删除
                        OlapTableFieldMapping existing = existingMap.get(item.getFieldKey());
                        if (existing != null) {
                            existing.setStatus(0);
                            existing.setUpdatedAt(now);
                            fieldMappingMapper.updateById(existing);
                        }
                    } else {
                        // 新增
                        OlapTableFieldMapping mapping = new OlapTableFieldMapping();
                        mapping.setTableId(table.getId());
                        mapping.setFieldKey(item.getFieldKey());
                        mapping.setFieldName(item.getFieldName());
                        mapping.setFieldType(item.getFieldType());
                        mapping.setFieldTypeName(item.getFieldType());
                        mapping.setStatus(1);
                        mapping.setUpdatedAt(now);
                        mapping.setRegisterStatus("未注册");
                        setBasicTypeByFieldType(mapping, item.getFieldType());
                        fieldMappingMapper.insert(mapping);
                    }
                }
            }
            return table.getId();
        } else {
            // 新建
            table = new OlapTablePro();
            table.setDbName(resolveDbName(dataSource.getDbType(), dataSource.getDefaultDb(), dataSource.getSchemaName(), dataSource.getUsername()));
            table.setTbName(dto.getViewEnName());
            table.setCnName(dto.getViewCnName());
            table.setTbCnName(dto.getViewCnName());
            table.setType(dto.getViewCnName());
            table.setTypeKey("fact");
            table.setTbType("1");
            table.setTbTypeKey("1");
            table.setStatus(TableStatus.ONLINE.getCode());
            table.setSourceId(dto.getSourceId());
            table.setViewSql(dto.getSql());
            table.setNote(dto.getNote());
            table.setTenantId(tenantId);
            table.setCreatedAt(now);
            table.setUpdatedAt(now);
            tableProMapper.insert(table);
        }

        for (ParseColumnVO item : dto.getColumns()) {
            OlapTableFieldMapping mapping = new OlapTableFieldMapping();
            mapping.setTableId(table.getId());
            mapping.setFieldKey(item.getFieldKey());
            mapping.setFieldName(item.getFieldName());
            mapping.setFieldType(item.getFieldType());
            mapping.setFieldTypeName(item.getFieldType());
            mapping.setStatus(1);
            mapping.setUpdatedAt(now);
            mapping.setRegisterStatus("未注册");
            setBasicTypeByFieldType(mapping, item.getFieldType());
            fieldMappingMapper.insert(mapping);
        }
        return table.getId();
    }

    /**
     * 根据字段类型名自动推测基本类型
     */
    private void setBasicTypeByFieldType(OlapTableFieldMapping mapping, String fieldType) {
        autoFillBasicType(mapping, fieldType);
    }

    private void autoFillBasicType(OlapTableFieldMapping mapping, String fieldType) {
        if (fieldType == null) {
            return;
        }
        String lower = fieldType.toLowerCase();
        if (lower.contains("int") || lower.contains("decimal") || lower.contains("float")
                || lower.contains("double") || lower.contains("numeric") || lower.contains("real")
                || lower.contains("serial") || lower.contains("money") || lower.contains("number")) {
            mapping.setBasicTypeKey("index");
            mapping.setBasicType("指标");
        } else {
            mapping.setBasicTypeKey("dim");
            mapping.setBasicType("维度");
        }
    }

    // ===================== SQL 解析 =====================

    @Override
    public List<ParseColumnVO> executeParse(FieldExecuteParseDTO dto) {
        if (dto.getSourceId() == null) {
            throw new BizException(400, "sourceId 不能为空");
        }
        MetaDataSource dataSource = metaDataSourceMapper.selectById(dto.getSourceId());
        if (dataSource == null) {
            throw new BizException(404, "数据源不存在");
        }
        log.info("数据源查询成功: sourceId={}, dbType={}", dto.getSourceId(), dataSource.getDbType());

        // 替换 ${start_time} 和 ${end_time}（兼容花括号内带空格）
        String sql = dto.getSql().trim();
        sql = sql.replaceAll("\\$\\{\\s*start_time\\s*}", "2026-01-01");
        sql = sql.replaceAll("\\$\\{\\s*end_time\\s*}", "2026-01-01");
        log.info("参数替换完成，SQL长度={}", sql.length());

        // 执行 SQL 获取字段元数据
        String wrappedSql = "SELECT * FROM (" + sql + ") AS _parse_wrap WHERE 1=0";
        log.info("实际执行SQL: {}", wrappedSql);

        List<ParseColumnVO> columns = new ArrayList<>();
        log.info("开始创建JDBC连接: sourceId={}", dto.getSourceId());
        try (Connection conn = JdbcConnectionFactory.create(dataSource);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(wrappedSql)) {
            log.info("SQL执行完成，获取元数据: sourceId={}", dto.getSourceId());
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            log.info("获取到 {} 个字段", columnCount);
            for (int i = 1; i <= columnCount; i++) {
                String fieldKey = metaData.getColumnLabel(i);
                String fieldType = metaData.getColumnTypeName(i);
                log.info("字段{}: fieldKey={}, fieldType={}", i, fieldKey, fieldType);
                ParseColumnVO vo = new ParseColumnVO();
                vo.setFieldKey(fieldKey);
                vo.setFieldName(fieldKey);
                vo.setFieldType(fieldType);
                columns.add(vo);
            }
            log.info("元数据解析完成，共{}个字段: sourceId={}", columns.size(), dto.getSourceId());
        } catch (SQLException e) {
            log.error("SQL解析失败: sourceId={}", dto.getSourceId(), e);
            throw new BizException(400, "SQL 解析失败: " + e.getMessage());
        }

        // 如果传了 tableId，与已有映射对比
        if (dto.getTableId() != null) {
            List<OlapTableFieldMapping> existingMappings = fieldMappingMapper.selectList(
                    new LambdaQueryWrapper<OlapTableFieldMapping>()
                            .eq(OlapTableFieldMapping::getTableId, dto.getTableId())
                            .in(OlapTableFieldMapping::getStatus, 1, 2));
            Set<String> existingKeys = existingMappings.stream()
                    .map(OlapTableFieldMapping::getFieldKey)
                    .collect(Collectors.toSet());
            Set<String> newKeys = columns.stream()
                    .map(ParseColumnVO::getFieldKey)
                    .collect(Collectors.toSet());

            // 标记状态：0-保留 1-新增 2-废弃
            for (ParseColumnVO vo : columns) {
                if (existingKeys.contains(vo.getFieldKey())) {
                    vo.setCompareStatus("0");
                    // 回填中文名
                    existingMappings.stream()
                            .filter(m -> vo.getFieldKey().equals(m.getFieldKey()))
                            .findFirst()
                            .ifPresent(m -> vo.setFieldName(m.getFieldName()));
                } else {
                    vo.setCompareStatus("1");
                }
            }

            // 找出废弃的字段（在映射中存在但 SQL 中不存在）
            for (OlapTableFieldMapping m : existingMappings) {
                if (!newKeys.contains(m.getFieldKey())) {
                    ParseColumnVO vo = new ParseColumnVO();
                    vo.setFieldKey(m.getFieldKey());
                    vo.setFieldName(m.getFieldName());
                    vo.setFieldType(m.getFieldTypeName());
                    vo.setCompareStatus("2");
                    columns.add(vo);
                }
            }
        }

        return columns;
    }

    // ===================== F4: 编辑视图 =====================

    @Override
    public ViewInfoVO getViewInfo(Long tableId, Long tenantId) {
        OlapTablePro table = requireTable(tableId, tenantId);
        if (!"1".equals(table.getTbTypeKey())) {
            throw new BizException(400, "该表不是视图");
        }

        List<OlapTableFieldMapping> mappings = fieldMappingMapper.selectList(
                new LambdaQueryWrapper<OlapTableFieldMapping>()
                        .eq(OlapTableFieldMapping::getTableId, tableId)
                        .in(OlapTableFieldMapping::getStatus, 1, 2)
                        .orderByAsc(OlapTableFieldMapping::getHeight));

        List<ParseColumnVO> columns = new ArrayList<>();
        for (OlapTableFieldMapping mapping : mappings) {
            ParseColumnVO col = new ParseColumnVO();
            col.setFieldKey(mapping.getFieldKey());
            col.setFieldType(mapping.getFieldType());
            col.setFieldName(mapping.getFieldName());
            columns.add(col);
        }

        ViewInfoVO vo = new ViewInfoVO();
        vo.setSourceId(table.getSourceId());
        vo.setViewEnName(table.getTbName());
        vo.setViewCnName(table.getCnName());
        vo.setSql(table.getViewSql());
        vo.setColumns(columns);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveViewEdit(ViewEditDTO dto, Long tenantId) {
        OlapTablePro table = requireTable(dto.getTableId(), tenantId);
        table.setViewSql(dto.getSql());
        if (StrUtil.isNotBlank(dto.getViewCnName())) {
            table.setCnName(dto.getViewCnName());
            table.setTbCnName(dto.getViewCnName());
        }
        table.setUpdatedAt(LocalDateTime.now());
        tableProMapper.updateById(table);
    }

    // ===================== F5: 语义映射 =====================

    @Override
    public List<FieldMappingVO> getFieldMappings(Long tableId, Long tenantId) {
        requireTable(tableId, tenantId);

        List<OlapTableFieldMapping> mappings = fieldMappingMapper.selectList(
                new LambdaQueryWrapper<OlapTableFieldMapping>()
                        .eq(OlapTableFieldMapping::getTableId, tableId)
                        .in(OlapTableFieldMapping::getStatus, 1, 2)
                        .orderByAsc(OlapTableFieldMapping::getHeight));

        return mappings.stream()
                .map(this::toFieldMappingVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSemanticMappings(SemanticMappingSaveDTO dto, Long tenantId) {
        OlapTablePro table = requireTable(dto.getTableId(), tenantId);

        // 更新表类型
        if (StrUtil.isNotBlank(dto.getTypeKey())) {
            table.setTypeKey(dto.getTypeKey());
        }

        if (StrUtil.isNotBlank(dto.getCnName())) {
            table.setCnName(dto.getCnName());
        }

        if (StrUtil.isNotBlank(dto.getNote())) {
            table.setNote(dto.getNote());
        }
        QueryWrapper<OlapTableFieldMapping> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(OlapTableFieldMapping::getTableId, dto.getTableId());
        //fieldMappingMapper.delete(queryWrapper);
        LocalDateTime now = LocalDateTime.now();
        List<OlapTableFieldMapping> mappings = new ArrayList<>();
        for (FieldMappingItemDTO item : dto.getFields()) {
            OlapTableFieldMapping mapping = new OlapTableFieldMapping();
            mapping.setTableId(table.getId());
            mapping.setFieldKey(item.getFieldKey());
            mapping.setFieldName(item.getFieldName());
            mapping.setFieldType(item.getFieldType());
            mapping.setFieldTypeName(item.getFieldTypeName());
            if(item.isMetricNew()){
                MetricReq metricReq = new MetricReq();
                metricReq.setEnglishName(item.getBasicKey());
                metricReq.setStandardName(item.getBasicName());
                metricReq.setChineseName(item.getBasicName());
                metricReq.setUnit(item.getUnit());
                metricReq.setDataType(item.getFieldType());
                metricReq.setType(MetricType.ATOM.getCode());
                OlapBasicPro metricOrExit = metricService.createMetricOrExit(metricReq, tenantId);
                mapping.setBasicId(metricOrExit.getId());
                mapping.setBasicKey(metricOrExit.getEnglishName());
                mapping.setBasicName(metricOrExit.getChineseName());
                mapping.setBasicTypeKey("index");
            }else if (StrUtil.isBlank(item.getBasicTypeKey())) {
                if (item.getId() != null) {
                    fieldMappingMapper.update(null,
                        new LambdaUpdateWrapper<OlapTableFieldMapping>()
                            .set(OlapTableFieldMapping::getBasicId, null)
                            .set(OlapTableFieldMapping::getBasicKey, null)
                            .set(OlapTableFieldMapping::getBasicName, null)
                            .set(OlapTableFieldMapping::getBasicTypeKey, "")
                            .set(OlapTableFieldMapping::getBasicType, null)
                            .set(OlapTableFieldMapping::getStatus, 2)
                            .set(OlapTableFieldMapping::getRegisterStatus, "未注册")
                            .eq(OlapTableFieldMapping::getId, item.getId()));
                }
                mapping.setBasicType(null);
            }else{
                mapping.setBasicId(item.getBasicId());
                mapping.setBasicKey(item.getBasicKey());
                mapping.setBasicName(item.getBasicName());
                mapping.setBasicTypeKey(item.getBasicTypeKey());
            }
            mapping.setStatus(StrUtil.isNotBlank(mapping.getBasicTypeKey()) ? 1 : 2);
            mapping.setRegisterStatus(mapping.getBasicId() != null ? "已注册" : "未注册");
            mapping.setSummaryKey(item.getSummaryKey());
            mapping.setSummary(item.getSummaryKey());
            mapping.setExpression(item.getExpression());
            mapping.setLookBackFlag(item.getLookBackFlag());
            mapping.setUnit(item.getUnit());
            mapping.setUpdatedAt(now);

            // 根据注册类型设置基本类型名称
            FieldRegisterType registerType = FieldRegisterType.fromKey(item.getBasicTypeKey());
            mapping.setBasicType(registerType.getLabel());
            // ptdate 日期维度 + 字符类型源字段：自动探测日期格式
            if ("ptdate".equals(mapping.getBasicKey()) && isCharFieldType(mapping.getFieldType())) {
                mapping.setDateFormat(detectDateFormat(table, item.getFieldKey()));
            }
            if(Objects.nonNull(item.getId())){
                mapping.setId(item.getId());
                fieldMappingMapper.updateById(mapping);
            }else{
                fieldMappingMapper.insert(mapping);
            }
        }
       /* if (!mappings.isEmpty()){
            this.saveBatch(mappings);
        }*/
        table.setViewSql(dto.getViewSql());
        table.setUpdatedAt(now);
        tableProMapper.updateById(table);
    }

    // ===================== F6/F7: 批量注册 =====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchRegister(BatchRegisterDTO dto, Long tenantId) {
        OlapTablePro table = requireTable(dto.getTableId(), tenantId);

        List<OlapTableFieldMapping> mappings = fieldMappingMapper.selectList(
                new LambdaQueryWrapper<OlapTableFieldMapping>()
                        .in(OlapTableFieldMapping::getId, dto.getFieldIds())
                        .eq(OlapTableFieldMapping::getTableId, dto.getTableId()));

        if (mappings.isEmpty()) {
            throw new BizException(400, "未找到对应的字段映射");
        }

        FieldRegisterType type = FieldRegisterType.fromKey(dto.getTypeKey());
        LocalDateTime now = LocalDateTime.now();

        for (OlapTableFieldMapping mapping : mappings) {
            mapping.setBasicTypeKey(dto.getTypeKey());
            mapping.setBasicType(type.getLabel());
            mapping.setUpdatedAt(now);

            // 自动填充指标/维度名称
            if (StrUtil.isBlank(mapping.getBasicName())) {
                mapping.setBasicName(mapping.getFieldName());
            }

            fieldMappingMapper.updateById(mapping);
        }
    }

    // ===================== 删除 =====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTable(Long tableId, Long tenantId) {
        OlapTablePro table = requireTable(tableId, tenantId);

        QueryWrapper<OlapTableFieldMapping> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(OlapTableFieldMapping::getTableId, tableId);
        fieldMappingMapper.delete(queryWrapper);
        tableProMapper.deleteById(tableId);
    }

    // ===================== 数据源选项 =====================

    @Override
    public List<DataSourceOptionVO> listDataSources() {
        // 与数据源管理列表一致：不按 tenant 过滤（新建数据源默认 tenant_id=0，BI 模块默认 1，需都能看见）
        List<MetaDataSource> sources = metaDataSourceMapper.selectList(
                new LambdaQueryWrapper<MetaDataSource>()
                        .eq(MetaDataSource::getStatus, 1)
                        .orderByAsc(MetaDataSource::getId));
        return sources.stream().map(ds -> {
            DataSourceOptionVO vo = new DataSourceOptionVO();
            vo.setId(ds.getId());
            vo.setName(ds.getName());
            vo.setDbName(ds.getDefaultDb());
            vo.setDbType(ds.getDbType());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public List<TableMappingVo> listTablesBySourceId(Long sourceId,String keyword, Long currentTenantId) {
        return tableProMapper.listTablesBySourceId(sourceId, keyword, currentTenantId);
    }

    @Override
    public DimensionVO ptdate() {
        Long currentTenantId = getCurrentTenantId();
        DimensionVO metricVO = olapBasicProMapper.selectPtdate(currentTenantId, "ptdate");
        return metricVO;
    }

    // ===================== 私有辅助方法 =====================

    private OlapTablePro requireTable(Long tableId, Long tenantId) {
        OlapTablePro olapTablePro = tableProMapper.selectById(tableId);
        OlapTablePro table = olapTablePro;
        if (table == null) {
            throw new BizException(404, "分析表不存在");
        }
        if (tenantId != null && !Objects.equals(table.getTenantId(), tenantId)) {
            throw new BizException(403, "无权限操作该表");
        }
        return table;
    }

    /**
     * 根据数据库引擎返回 SQL 中 FROM db_name.tb_name 的 db_name 值
     */
    private String resolveDbName(String dbType, String defaultDb, String schemaName, String username) {
        if (dbType == null) {
            return defaultDb;
        }
        switch (dbType.trim()) {
            case "MySQL":
            case "ClickHouse":
                return defaultDb;
            case "PostgreSQL":
            case "GaussDB":
                return StringUtils.isNotBlank(schemaName) ? schemaName.trim() : "public";
            case "Oracle":
            case "达梦 DM":
            case "达梦":
                if (StringUtils.isNotBlank(schemaName)) {
                    return schemaName.trim();
                }
                return StringUtils.isNotBlank(username) ? username.trim() : defaultDb;
            default:
                return defaultDb;
        }
    }

    private Map<Long, MetaDataSource> loadSourceMap(List<OlapTablePro> tables) {
        Set<Long> sourceIds = tables.stream()
                .map(OlapTablePro::getSourceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (sourceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return metaDataSourceMapper.selectBatchIds(sourceIds).stream()
                .collect(Collectors.toMap(MetaDataSource::getId, s -> s, (a, b) -> a));
    }

    private OlapTableVO toListVO(OlapTablePro table, Map<Long, MetaDataSource> sourceMap) {
        OlapTableVO vo = new OlapTableVO();
        vo.setId(table.getId());
        vo.setMetaTableId(table.getMetaTableId());
        vo.setTbName(table.getTbName());
        vo.setCnName(table.getCnName());
        vo.setTbTypeKey(table.getTbTypeKey());
        vo.setTbTypeName(StorageType.fromKey(table.getTbTypeKey()).getLabel());
        vo.setType(table.getType());
        vo.setTypeKey(table.getTypeKey());
        vo.setStatus(table.getStatus());
        vo.setStatusName(TableStatus.fromCode(table.getStatus()).getLabel());
        vo.setUpdatedAt(table.getUpdatedAt());

        MetaDataSource source = sourceMap.get(table.getSourceId());
        if (source != null) {
            vo.setSourceId(source.getId());
            vo.setSourceName(source.getName());
        }

        // 统计字段映射进度
        List<OlapTableFieldMapping> mappings = fieldMappingMapper.selectList(
                new LambdaQueryWrapper<OlapTableFieldMapping>()
                        .eq(OlapTableFieldMapping::getTableId, table.getId())
                        .in(OlapTableFieldMapping::getStatus, 1, 2));
        vo.setTotalCount(mappings.size());
        vo.setMappedCount((int) mappings.stream()
                .filter(m -> Objects.nonNull(m.getBasicId()))
                .count());

        return vo;
    }

    private FieldMappingVO toFieldMappingVO(OlapTableFieldMapping mapping) {
        FieldMappingVO vo = new FieldMappingVO();
        vo.setId(mapping.getId());
        vo.setTableId(mapping.getTableId());
        vo.setFieldKey(mapping.getFieldKey());
        vo.setFieldName(mapping.getFieldName());
        vo.setBasicId(mapping.getBasicId());
        vo.setBasicKey(mapping.getBasicKey());
        vo.setBasicType(mapping.getBasicType());
        vo.setBasicTypeKey(mapping.getBasicTypeKey());
        vo.setBasicName(mapping.getBasicName());
        vo.setFieldType(mapping.getFieldType());
        vo.setFieldTypeName(mapping.getFieldTypeName());
        vo.setFieldNote(mapping.getFieldNote());
        vo.setIsCustomize(mapping.getIsCustomize());
        vo.setPfStatus(mapping.getPfStatus());
        vo.setSummary(mapping.getSummary());
        vo.setSummaryKey(mapping.getSummaryKey());
        vo.setExpression(mapping.getExpression());
        vo.setInnerFieldKey(mapping.getInnerFieldKey());
        vo.setLookBackMappingIds(mapping.getLookBackMappingIds());
        vo.setLookBackMappingNames(mapping.getLookBackMappingNames());
        vo.setLookBackFlag(mapping.getLookBackFlag());
        vo.setHeight(mapping.getHeight());
        vo.setStatus(mapping.getStatus());
        vo.setCreatedAt(mapping.getCreatedAt());
        vo.setUpdatedAt(mapping.getUpdatedAt());
        vo.setUnit(mapping.getUnit());
        vo.setRegisterStatus(mapping.getRegisterStatus());
        return vo;
    }

    /**
     * 从元数据表导入字段到映射表
     */
    private void importColumnsToFieldMapping(Long tableId, Long metaTableId, Long tenantId) {
        List<MetaColumn> columns = metaColumnMapper.selectList(
                new LambdaQueryWrapper<MetaColumn>()
                        .eq(MetaColumn::getTableId, metaTableId)
                        .orderByAsc(MetaColumn::getOrdinal));

        LocalDateTime now = LocalDateTime.now();
        for (MetaColumn col : columns) {
            OlapTableFieldMapping mapping = new OlapTableFieldMapping();
            mapping.setTableId(tableId);
            mapping.setFieldKey(col.getColumnName());
            mapping.setFieldName(col.getColumnComment());
            mapping.setFieldType(col.getDataType());
            mapping.setFieldTypeName(col.getDataType());
            mapping.setFieldNote(col.getColumnComment());
            mapping.setBasicTypeKey(null);
            mapping.setBasicType(null);
            mapping.setBasicName(null);
            mapping.setIsCustomize(0);
            mapping.setPfStatus(0);
            mapping.setLookBackFlag(1);
            mapping.setHeight(col.getOrdinal() != null ? col.getOrdinal() : 0);
            mapping.setStatus(1);
            mapping.setTenantId(tenantId);
            mapping.setCreatedAt(now);
            mapping.setUpdatedAt(now);
            mapping.setRegisterStatus("未注册");
            autoFillBasicType(mapping, col.getDataType());

            fieldMappingMapper.insert(mapping);
        }
    }

    private Long getCurrentTenantId() {
        SaasUser loginUser = UserThreadLocal.get();
        if(Objects.nonNull(loginUser)){
            return loginUser.getTenantId();
        }
        return 1L;
    }

    /**
     * 判断字段类型是否为字符类型（varchar/text/char/string/clob，忽略大小写和长度）
     */
    private boolean isCharFieldType(String fieldType) {
        if (StrUtil.isBlank(fieldType)) {
            return false;
        }
        String t = fieldType.toLowerCase().trim();
        return t.contains("char") || t.contains("text") || t.contains("string") || t.contains("clob");
    }

    /**
     * 探测 ptdate 源字段的实际日期格式：取第一行值做形态匹配。
     * 失败或匹配不上返回 null（标准日期），不阻塞保存流程。
     */
    private String detectDateFormat(OlapTablePro table, String fieldKey) {
        MetaDataSource dataSource = metaDataSourceMapper.selectById(table.getSourceId());
        if (dataSource == null) {
            log.warn("探测 date_format 失败: 数据源不存在, sourceId={}, field={}", table.getSourceId(), fieldKey);
            return null;
        }
        String sql;
        if ("1".equals(table.getTbType())) {
            if (StrUtil.isBlank(table.getViewSql())) {
                return null;
            }
            sql = "SELECT " + fieldKey + " FROM (" + table.getViewSql() + ") AS _fmt_probe LIMIT 1";
        } else {
            sql = "SELECT " + fieldKey + " FROM " + table.getDbName() + "." + table.getTbName() + " LIMIT 1";
        }
        log.info("探测 date_format | tableId={} | dbType={} | field={} | sql={}",
                table.getId(), dataSource.getDbType(), fieldKey, sql);
        try (Connection conn = JdbcConnectionFactory.create(dataSource);
             Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(10);
            try (ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    String format = matchDateFormat(rs.getString(1));
                    log.info("探测 date_format 结果 | tableId={} | field={} | format={}", table.getId(), fieldKey, format);
                    return format;
                }
            }
        } catch (Exception e) {
            log.warn("探测 date_format 失败: tableId={}, field={}, error={}", table.getId(), fieldKey, e.getMessage());
        }
        return null;
    }

    /**
     * 按值形态匹配日期格式，匹配不上返回 null
     */
    private String matchDateFormat(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        String v = value.trim();
        if (v.matches("\\d{8}")) {
            return "yyyyMMdd";
        }
        if (v.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return "yyyy-MM-dd";
        }
        if (v.matches("\\d{4}/\\d{2}/\\d{2}")) {
            return "yyyy/MM/dd";
        }
        if (v.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
            return "yyyy-MM-dd HH:mm:ss";
        }
        if (v.matches("\\d{14}")) {
            return "yyyyMMddHHmmss";
        }
        return null;
    }
}
