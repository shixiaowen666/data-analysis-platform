package com.metadata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.common.exception.BizException;
import com.metadata.entity.MetaColumn;
import com.metadata.mapper.MetaColumnMapper;
import com.metadata.service.MetaColumnService;
import com.common.util.BeanUtil;
import com.metadata.vo.meta.MetaColumnVO;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 元数据-字段 Service 实现
 */
@Service
public class MetaColumnServiceImpl extends ServiceImpl<MetaColumnMapper, MetaColumn>
        implements MetaColumnService {

    @Override
    public List<MetaColumnVO> listByTableId(Long tableId) {
        if (tableId == null) {
            throw new BizException("表 ID 不能为空");
        }
        LambdaQueryWrapper<MetaColumn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MetaColumn::getTableId, tableId)
                .orderByAsc(MetaColumn::getOrdinal)
                .orderByAsc(MetaColumn::getId);
        return list(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public Map<Long, Integer> countByTableIds(List<Long> tableIds) {
        if (tableIds == null || tableIds.isEmpty()) {
            return Collections.emptyMap();
        }
        QueryWrapper<MetaColumn> wrapper = new QueryWrapper<>();
        wrapper.select("table_id", "COUNT(*) AS column_count")
                .in("table_id", tableIds)
                .groupBy("table_id");
        List<Map<String, Object>> rows = baseMapper.selectMaps(wrapper);
        Map<Long, Integer> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object tableId = row.get("table_id");
            Object count = row.get("column_count");
            if (tableId != null && count != null) {
                result.put(Long.valueOf(tableId.toString()), Integer.valueOf(count.toString()));
            }
        }
        return result;
    }

    private MetaColumnVO toVO(MetaColumn entity) {
        MetaColumnVO vo = new MetaColumnVO();
        BeanUtil.copy(entity, vo);
        return vo;
    }
}
