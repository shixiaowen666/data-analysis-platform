package com.bi.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bi.dto.DimensionQueryDTO;
import com.bi.dto.DimensionReq;
import com.bi.dto.ValueEntry;
import com.bi.entity.OlapBasicPro;
import com.bi.entity.OlapBasicProDimension;
import com.bi.entity.OlapBasicProIndicator;
import com.bi.entity.OlapTablePro;
import com.bi.enums.CollectStatus;
import com.bi.enums.DimensionType;
import com.bi.enums.HighLevelFlag;
import com.bi.mapper.OlapBasicProDimensionMapper;
import com.bi.mapper.OlapBasicProMapper;
import com.bi.mapper.OlapTableFieldMappingMapper;
import com.bi.mapper.OlapTableProMapper;
import com.bi.service.IOlapBasicProIndicatorService;
import com.bi.service.IDimensionService;
import com.bi.util.JsonUtil;
import com.bi.vo.DimensionDetailVO;
import com.bi.vo.DimensionValueVO;
import com.bi.vo.DimensionVO;
import com.bi.vo.NameCheckVO;
import com.bi.vo.OlapBasicProDimensionVo;
import com.common.exception.BizException;
import com.common.util.JdbcConnectionFactory;
import com.metadata.entity.MetaDataSource;
import com.metadata.mapper.MetaDataSourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 维度管理 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DimensionServiceImpl implements IDimensionService {

    private static final AtomicLong SEQ = new AtomicLong(0);

    private final OlapBasicProDimensionMapper dimensionMapper;
    private final OlapBasicProMapper basicProMapper;
    private final IOlapBasicProIndicatorService indicatorService;
    private final OlapTableFieldMappingMapper tableFieldMappingMapper;
    private final OlapTableProMapper tableProMapper;
    private final MetaDataSourceMapper metaDataSourceMapper;

    @Override
    public IPage<DimensionVO> listDimensions(DimensionQueryDTO query, Long tenantId) {
        Page<DimensionVO> mpPage = new Page<>(query.getPage(), query.getPageSize());
        return dimensionMapper.selectDimensionPage(mpPage,
                query.getKeyword(),
                query.getDimensionType(),
                query.getStatus(),
                tenantId);
    }

    @Override
    public DimensionDetailVO getDimensionDetail(Long id) {
        OlapBasicPro basic = basicProMapper.selectById(id);
        if (basic == null) {
            throw new BizException(404, "维度不存在");
        }
        if (basic.getCategory() != 1) {
            throw new BizException(400, "该记录不是维度");
        }

        OlapBasicProDimension dimension = dimensionMapper.selectByBasicProId(id);
        if (dimension == null) {
            throw new BizException(404, "维度扩展信息不存在");
        }

        DimensionDetailVO vo = new DimensionDetailVO();
        vo.setId(basic.getId());
        vo.setChineseName(basic.getChineseName());
        vo.setAlias(basic.getAlias());
        vo.setEnglishName(basic.getEnglishName());
        vo.setCategory(basic.getCategory());

        OlapBasicProDimensionVo dimensionVo = new OlapBasicProDimensionVo();
        dimensionVo.setDimensionType(dimension.getDimensionType());
        dimensionVo.setValueSourceTable(dimension.getValueSourceTable());
        dimensionVo.setValueSourceField(dimension.getValueSourceField());
        dimensionVo.setValueFilter(dimension.getValueFilter());

        // 解析杂项维的值映射 JSON
        if (StrUtil.isNotBlank(dimension.getValueEntries())) {
            try {
                dimensionVo.setValueEntries(JsonUtil.fromJsonArray(dimension.getValueEntries(), ValueEntry.class));
            } catch (Exception e) {
                log.warn("解析 valueEntries 失败: {}", e.getMessage());
                dimensionVo.setValueEntries(new ArrayList<>());
            }
        }

        dimensionVo.setCollectStatus(dimension.getCollectStatus());
        dimensionVo.setDatabaseTableId(dimension.getDatabaseTableId());
        dimensionVo.setValueFieldId(dimension.getValueFieldId());
        dimensionVo.setValueFieldName(dimension.getValueFieldName());
        dimensionVo.setCaliberDescription(dimension.getCaliberDescription());
        vo.setExtension(dimensionVo);
        List<Map<String,Object>> mappingList = tableFieldMappingMapper.getMappingListByDimId(basic.getId());
        vo.setMappingList(mappingList);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createDimension(DimensionReq req, Long tenantId) {
        // 生成编码
        String code = generateDimensionCode();

        // 检查编码重复
        LambdaQueryWrapper<OlapBasicPro> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(OlapBasicPro::getEnglishName, req.getEnglishName())
                    .eq(OlapBasicPro::getTenantId, tenantId);
        if (basicProMapper.selectCount(checkWrapper) > 0) {
            throw new BizException(400, "维度编码已存在");
        }

        // 创建基础信息
        OlapBasicPro basic = new OlapBasicPro();
        basic.setTenantId(tenantId);
        basic.setCategory(1);
        basic.setKeyStr(code);
        basic.setChineseName(req.getChineseName());
        basic.setAlias(req.getAlias());
        basic.setEnglishName(req.getEnglishName());
        basic.setDataSourceType(1);
        basic.setStatus(2); // 草稿
        basicProMapper.insert(basic);

        // 创建扩展信息
        OlapBasicProDimension dimension = new OlapBasicProDimension();
        dimension.setOlapBasicProId(basic.getId());
        dimension.setDimensionType(req.getExtension().getDimensionType());
        dimension.setValueSourceTable(req.getExtension().getValueSourceTable());
        dimension.setValueSourceField(req.getExtension().getValueSourceField());
        dimension.setValueFilter(req.getExtension().getValueFilter());
        dimension.setDatabaseTableId(req.getExtension().getDatabaseTableId());
        dimension.setCaliberDescription(req.getExtension().getCaliberDescription());
        dimension.setTenantId(tenantId);

        // 处理杂项维的值映射 JSON
        if (DimensionType.MISC.getCode() == req.getExtension().getDimensionType()) {
            if (req.getExtension().getValueEntries() != null && !req.getExtension().getValueEntries().isEmpty()) {
                dimension.setValueEntries(JsonUtil.toJson(req.getExtension().getValueEntries()));
                dimension.setDimensionValues(extractRawValues(req.getExtension().getValueEntries()));
                dimension.setCollectStatus(CollectStatus.FULL.getCode());
            } else {
                dimension.setCollectStatus(CollectStatus.UNCOLLECTED.getCode());
            }
        } else if (DimensionType.STANDARD.getCode() == req.getExtension().getDimensionType()) {
            collectStandardDimensionValues(dimension, basic.getChineseName());
        }
        dimensionMapper.insert(dimension);
        log.info("维度创建成功: id={}, code={}", basic.getId(), code);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDimension(DimensionReq req, Long tenantId) {
        OlapBasicPro basic = basicProMapper.selectById(req.getId());
        if (basic == null) {
            throw new BizException(404, "维度不存在");
        }
        if (!tenantId.equals(basic.getTenantId())) {
            throw new BizException(403, "无权限操作该维度");
        }

        // 更新基础信息
        basic.setChineseName(req.getChineseName());
        basic.setAlias(req.getAlias());
        basic.setEnglishName(req.getEnglishName());
        basicProMapper.updateById(basic);

        // 更新扩展信息
        OlapBasicProDimension dimension = dimensionMapper.selectByBasicProId(req.getId());
        if (dimension == null) {
            throw new BizException(404, "维度扩展信息不存在");
        }

        if (req.getExtension().getDimensionType() != null) {
            dimension.setDimensionType(req.getExtension().getDimensionType());
        }
        if (req.getExtension().getValueSourceTable() != null) {
            dimension.setValueSourceTable(req.getExtension().getValueSourceTable());
        }
        if (req.getExtension().getValueSourceField() != null) {
            dimension.setValueSourceField(req.getExtension().getValueSourceField());
        }
        if (req.getExtension().getValueFilter() != null) {
            dimension.setValueFilter(req.getExtension().getValueFilter());
        }
        if (req.getExtension().getDatabaseTableId() != null) {
            dimension.setDatabaseTableId(req.getExtension().getDatabaseTableId());
        }

        if (req.getExtension().getCaliberDescription() != null) {
            dimension.setCaliberDescription(req.getExtension().getCaliberDescription());
        }
        // 处理杂项维的值映射
        if (req.getExtension().getValueEntries() != null) {
            dimension.setValueEntries(JsonUtil.toJson(req.getExtension().getValueEntries()));
            dimension.setDimensionValues(extractRawValues(req.getExtension().getValueEntries()));
            dimension.setCollectStatus(
                    req.getExtension().getValueEntries().isEmpty()
                            ? CollectStatus.UNCOLLECTED.getCode()
                            : CollectStatus.FULL.getCode());
        }
        // 标准维：每次保存都重新采集
        int dimType = dimension.getDimensionType() != null ? dimension.getDimensionType() : 0;
        if (DimensionType.STANDARD.getCode() == dimType) {
            collectStandardDimensionValues(dimension, basic.getChineseName());
        }
        dimensionMapper.updateById(dimension);
        log.info("维度更新成功: id={}", req.getId());
    }

    @Override
    public String generateDimensionCode() {
        long seq = SEQ.incrementAndGet();
        return "dim_" + String.format("%04d", seq);
    }

    /**
     * 标准维：连接来源表采集维度值，写入 dimension_values
     * 根据 databaseTableId 查 olap_table_pro，区分物理表/视图构建不同的采集 SQL
     */
    private void collectStandardDimensionValues(OlapBasicProDimension dimension, String dimensionName) {
        if (dimension.getDatabaseTableId() == null) {
            dimension.setCollectStatus(CollectStatus.UNCOLLECTED.getCode());
            log.warn("databaseTableId 为空，跳过采集: dimId={}", dimension.getOlapBasicProId());
            return;
        }

        OlapTablePro tablePro = tableProMapper.selectById(dimension.getDatabaseTableId());
        if (tablePro == null) {
            dimension.setCollectStatus(CollectStatus.UNCOLLECTED.getCode());
            throw new BizException(400, "来源表不存在: databaseTableId=" + dimension.getDatabaseTableId());
        }

        // 校验 sourceId
        if (tablePro.getSourceId() == null) {
            dimension.setCollectStatus(CollectStatus.UNCOLLECTED.getCode());
            throw new BizException(400, "数据源 ID 为空: tableId=" + tablePro.getId());
        }

        MetaDataSource dataSource = metaDataSourceMapper.selectById(tablePro.getSourceId());
        if (dataSource == null) {
            dimension.setCollectStatus(CollectStatus.UNCOLLECTED.getCode());
            throw new BizException(400, "数据源不存在: sourceId=" + tablePro.getSourceId());
        }

        String sql;
        if ("1".equals(tablePro.getTbType())) {
            // 视图：校验 viewSql，替换时间变量后拼采集SQL
            if (StrUtil.isBlank(tablePro.getViewSql())) {
                dimension.setCollectStatus(CollectStatus.UNCOLLECTED.getCode());
                throw new BizException(400, "视图 SQL 为空: tableId=" + tablePro.getId());
            }
            LocalDate now = LocalDate.now();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String resolvedViewSql = tablePro.getViewSql().trim()
                    .replaceAll("\\$\\{\\s*start_time\\s*}", now.minusMonths(6).format(fmt))
                    .replaceAll("\\$\\{\\s*end_time\\s*}", now.format(fmt));
            log.info("视图时间参数替换完成: tableId={}, SQL长度={}", tablePro.getId(), resolvedViewSql.length());
            sql = buildViewCollectSql(resolvedViewSql,
                    dimension.getValueSourceField(), dimension.getValueFilter());
        } else {
            // 物理表：校验 valueSourceTable、valueSourceField
            if (StrUtil.isBlank(dimension.getValueSourceTable())) {
                dimension.setCollectStatus(CollectStatus.UNCOLLECTED.getCode());
                throw new BizException(400, "来源表名不能为空: dimId=" + dimension.getOlapBasicProId());
            }
            if (StrUtil.isBlank(dimension.getValueSourceField())) {
                dimension.setCollectStatus(CollectStatus.UNCOLLECTED.getCode());
                throw new BizException(400, "来源字段不能为空: dimId=" + dimension.getOlapBasicProId());
            }
            sql = buildCollectSql(dimension.getValueSourceTable(),
                    dimension.getValueSourceField(), dimension.getValueFilter());
        }

        log.info("开始采集维度值: dimName={}, dimId={}, table={}, field={}, sql={}",
                dimensionName, dimension.getOlapBasicProId(),
                dimension.getValueSourceTable(), dimension.getValueSourceField(), sql);

        List<String> values = new ArrayList<>();
        try (Connection conn = JdbcConnectionFactory.create(dataSource);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String value = rs.getString(1);
                if (value != null) {
                    values.add(value);
                }
            }
        } catch (SQLException e) {
            log.error("采集维度值失败: dimId={}, sql={}", dimension.getOlapBasicProId(), sql, e);
            dimension.setCollectStatus(CollectStatus.UNCOLLECTED.getCode());
            throw new BizException(400, "采集维度值失败: " + e.getMessage());
        }

        dimension.setDimensionValues(JsonUtil.toJson(values));
        if (values.size() >= 100) {
            dimension.setCollectStatus(CollectStatus.PARTIAL.getCode());
        } else {
            dimension.setCollectStatus(CollectStatus.FULL.getCode());
        }
        log.info("维度值采集完成: dimName={}, dimId={}, count={}", dimensionName, dimension.getOlapBasicProId(), values.size());
    }

    /**
     * 物理表采集 SQL
     */
    private String buildCollectSql(String table, String field, String filter) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT DISTINCT ").append(field).append(" FROM ").append(table);
        if (StrUtil.isNotBlank(filter)) {
            sb.append(" WHERE ").append(filter);
        }
        sb.append(" LIMIT 100");
        return sb.toString();
    }

    /**
     * 视图采集 SQL：套一层子查询
     */
    private String buildViewCollectSql(String viewSql, String field, String filter) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT DISTINCT ").append(field).append(" FROM (").append(viewSql).append(") AS _dim_view");
        if (StrUtil.isNotBlank(filter)) {
            sb.append(" WHERE ").append(filter);
        }
        sb.append(" LIMIT 100");
        return sb.toString();
    }

    private String extractRawValues(List<ValueEntry> entries) {
        List<String> rawValues = entries.stream()
                .map(ValueEntry::getRawValue)
                .collect(Collectors.toList());
        return JsonUtil.toJson(rawValues);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offlineDimension(Long id, Long tenantId) {
        OlapBasicPro basic = basicProMapper.selectById(id);
        if (basic == null) {
            throw new BizException(404, "维度不存在");
        }
        if (!tenantId.equals(basic.getTenantId())) {
            throw new BizException(403, "无权限操作该维度");
        }
        if (basic.getStatus() != 2) {
            throw new BizException(403, "仅已上线状态可下线");
        }

        // 检查是否被计算指标引用
        List<DimensionVO> referencedBy = checkReferencedByCalculation(basic.getKeyStr());
        if (!referencedBy.isEmpty()) {
            throw new BizException(403, "维度被以下计算指标引用，无法下线: " +
                    referencedBy.stream().map(DimensionVO::getChineseName).collect(Collectors.joining("、")));
        }

        basic.setStatus(3); // 已下线
        basicProMapper.updateById(basic);
        log.info("维度下线成功: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onlineDimension(Long id, Long tenantId) {
        OlapBasicPro basic = basicProMapper.selectById(id);
        if (basic == null) {
            throw new BizException(404, "维度不存在");
        }
        if (!tenantId.equals(basic.getTenantId())) {
            throw new BizException(403, "无权限操作该维度");
        }
        if (basic.getStatus() == 2) {
            throw new BizException(400, "维度已是已上线状态");
        }

        basic.setStatus(2);
        basicProMapper.updateById(basic);
        log.info("维度上线成功: id={}", id);
    }

    @Override
    public List<DimensionVO> listAllDimensions(String keyword, Long tenantId) {
        return dimensionMapper.selectDimensionList(keyword, tenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDimension(Long id, Long tenantId) {
        OlapBasicPro basic = basicProMapper.selectById(id);
        if (basic == null) {
            throw new BizException(404, "维度不存在");
        }
        if (!tenantId.equals(basic.getTenantId())) {
            throw new BizException(403, "无权限操作该维度");
        }
        if (basic.getStatus() == 2) {
            throw new BizException(403, "仅草稿状态可删除，请先将维度下线");
        }

        // 检查是否被计算指标引用
        List<DimensionVO> referencedBy = checkReferencedByCalculation(basic.getKeyStr());
        if (!referencedBy.isEmpty()) {
            throw new BizException(403, "维度被以下计算指标引用，无法删除: " +
                    referencedBy.stream().map(DimensionVO::getChineseName).collect(Collectors.joining("、")));
        }

        // 删除扩展信息
        dimensionMapper.delete(
                new LambdaQueryWrapper<OlapBasicProDimension>()
                        .eq(OlapBasicProDimension::getOlapBasicProId, id)
        );
        // 删除基础信息
        basicProMapper.deleteById(id);
        log.info("维度删除成功: id={}", id);
    }

    @Override
    public List<String> getDimensionValues(Long id) {
        OlapBasicPro basic = basicProMapper.selectById(id);
        if (basic == null) {
            throw new BizException(404, "维度不存在");
        }

        OlapBasicProDimension dimension = dimensionMapper.selectByBasicProId(id);
        if (dimension == null) {
            throw new BizException(404, "维度扩展信息不存在");
        }

        if (StrUtil.isNotBlank(dimension.getDimensionValues())) {
            return JsonUtil.fromJsonArray(dimension.getDimensionValues(), String.class);
        }
        return new ArrayList<>();
    }

    @Override
    public List<DimensionVO> checkReferencedByCalculation(String code) {
        LambdaQueryWrapper<OlapBasicPro> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OlapBasicPro::getCategory, 2)
               .eq(OlapBasicPro::getStatus, 2);
        List<OlapBasicPro> onlineMetrics = basicProMapper.selectList(wrapper);

        List<DimensionVO> result = new ArrayList<>();
        for (OlapBasicPro metric : onlineMetrics) {
            OlapBasicProIndicator indicator = indicatorService.getOne(
                    new LambdaQueryWrapper<OlapBasicProIndicator>()
                            .eq(OlapBasicProIndicator::getOlapBasicProId, metric.getId())
            );
            if (indicator != null && StrUtil.isNotBlank(indicator.getCalculatedProduction())) {
                try {
                    if (indicator.getCalculatedProduction().contains(code)) {
                        DimensionVO vo = new DimensionVO();
                        vo.setId(metric.getId());
                        vo.setChineseName(metric.getChineseName());
                        result.add(vo);
                    }
                } catch (Exception e) {
                    log.warn("检查计算指标引用失败: id={}, error={}", metric.getId(), e.getMessage());
                }
            }
        }
        return result;
    }

    @Override
    public NameCheckVO checkEnglishName(String name, Long excludeId, Long tenantId) {
        LambdaQueryWrapper<OlapBasicPro> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OlapBasicPro::getEnglishName, name)
               .eq(OlapBasicPro::getTenantId, tenantId);
        if (excludeId != null) {
            wrapper.ne(OlapBasicPro::getId, excludeId);
        }
        if (basicProMapper.selectCount(wrapper) > 0) {
            return NameCheckVO.conflict("英文名称 " + name + " 已存在");
        }
        return NameCheckVO.ok();
    }

    @Override
    public NameCheckVO checkChineseName(String name, Long excludeId, Long tenantId) {
        LambdaQueryWrapper<OlapBasicPro> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OlapBasicPro::getChineseName, name)
               .eq(OlapBasicPro::getTenantId, tenantId);
        if (excludeId != null) {
            wrapper.ne(OlapBasicPro::getId, excludeId);
        }
        if (basicProMapper.selectCount(wrapper) > 0) {
            return NameCheckVO.conflict("中文名称 " + name + " 已存在");
        }
        return NameCheckVO.ok();
    }
}
