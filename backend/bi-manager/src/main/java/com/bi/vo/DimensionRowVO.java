package com.bi.vo;

import lombok.Data;

import java.util.List;

/**
 * 维度行
 */
@Data
public class DimensionRowVO {

    private String label;

    /**
     * 维度标签列表
     */
    private List<TagItemVO> tags;
}
