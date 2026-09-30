package com.bi.vo;

import lombok.Data;

import java.util.List;

/**
 * 指标行
 */
@Data
public class MetricRowVO {

    private String label;

    /**
     * 指标标签列表
     */
    private List<TagItemVO> tags;
}
