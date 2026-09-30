package com.wm.semantic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wm.semantic.dto.CandidateTable;
import com.wm.semantic.dto.TableIndicatorDto;
import com.wm.semantic.entity.OlapTableProDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

@Mapper
public interface OlapTableProMapper extends BaseMapper<OlapTableProDO> {
    List<CandidateTable> findDimCandidates(
            @Param("dimensionIds") List<Long> dimensionIds,
            @Param("dimensionSize") int dimensionSize,
            @Param("preferredTableIds") List<Long> preferredTableIds,
            @Param("preferredModelIds") List<Long> preferredModelIds
    );

    List<Long> findIndicatorIdsByTableId(@Param("tableId") Long tableId);

    List<TableIndicatorDto> batchFindIndicatorsByTablesAndModels(
            @Param("tableIds") Set<Long> tableIds,
            @Param("modelIds") Set<Long> modelIds
    );
}