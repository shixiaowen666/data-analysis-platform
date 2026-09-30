package com.metadata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.common.exception.BizException;
import com.common.result.PageResult;
import com.metadata.dto.meta.MetaTableQueryDTO;
import com.metadata.entity.MetaTable;
import com.metadata.mapper.MetaTableMapper;
import com.metadata.service.MetaColumnService;
import com.metadata.service.MetaTableService;
import com.common.util.BeanUtil;
import com.metadata.vo.meta.MetaTableVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 元数据-表 Service 实现
 */
@Service
public class MetaTableServiceImpl extends ServiceImpl<MetaTableMapper, MetaTable>
        implements MetaTableService {

    private final MetaColumnService metaColumnService;

    public MetaTableServiceImpl(@Lazy MetaColumnService metaColumnService) {
        this.metaColumnService = metaColumnService;
    }

    @Override
    public PageResult<MetaTableVO> pageList(MetaTableQueryDTO query) {
        MetaTableQueryDTO params = query != null ? query : new MetaTableQueryDTO();
        if (params.getSourceId() == null) {
            throw new BizException("数据源 ID 不能为空");
        }

        int pageNo = params.getPage() != null && params.getPage() > 0 ? params.getPage() : 1;
        int pageSize = params.getPageSize() != null && params.getPageSize() > 0 ? params.getPageSize() : 10;

        LambdaQueryWrapper<MetaTable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MetaTable::getSourceId, params.getSourceId())
                .like(StringUtils.isNotBlank(params.getTableName()), MetaTable::getTableName, params.getTableName())
                .orderByDesc(MetaTable::getUpdatedAt);

        Page<MetaTable> page = page(new Page<>(pageNo, pageSize), wrapper);
        List<Long> tableIds = page.getRecords().stream()
                .map(MetaTable::getId)
                .collect(Collectors.toList());
        Map<Long, Integer> columnCountMap = metaColumnService.countByTableIds(tableIds);

        List<MetaTableVO> records = page.getRecords().stream()
                .map(table -> toVO(table, columnCountMap.getOrDefault(table.getId(), 0)))
                .collect(Collectors.toList());
        return new PageResult<>(page.getTotal(), records, Long.valueOf(pageNo), Long.valueOf(pageSize));
    }

    private MetaTableVO toVO(MetaTable entity, Integer columnCount) {
        MetaTableVO vo = new MetaTableVO();
        BeanUtil.copy(entity, vo);
        vo.setColumnCount(columnCount);
        return vo;
    }
}
