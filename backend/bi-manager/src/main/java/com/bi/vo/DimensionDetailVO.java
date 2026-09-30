package com.bi.vo;

import com.bi.entity.OlapBasicProDimension;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.bi.dto.ValueEntry;

/**
 * 维度详情 VO
 */
@Data
public class DimensionDetailVO {

    private Long id;

    private String chineseName;

    private String alias;

    private String englishName;

    private Integer category;

    private List<Map<String,Object>> mappingList;

    private OlapBasicProDimensionVo extension;
}
