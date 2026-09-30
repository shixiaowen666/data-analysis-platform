package com.bi.vo;

import lombok.Data;
import java.util.List;
import com.bi.dto.ValueEntry;

/**
 * 维值采集列表 VO
 */
@Data
public class DimensionValueVO {

    /**
     * 1-标准维 2-杂项维
     */
    private Integer dimensionType;

    /**
     * 0-未采集 1-全量采集 2-部分采集
     */
    private Integer collectStatus;

    private String collectStatusName;

    /**
     * 部分采集上限值
     */
    private Integer collectLimit;

    /**
     * 标准维为字符串数组，杂项维为 ValueEntry 数组
     */
    private Object values;

    private Integer total;

    private Integer page;

    private Integer pageSize;
}
