package com.bi.vo;

import lombok.Data;

import java.util.List;

/**
 * 视图回显信息，与 ViewRegisterDTO 结构对应
 */
@Data
public class ViewInfoVO {

    private Long sourceId;

    private String viewEnName;

    private String viewCnName;

    private String sql;

    private List<ParseColumnVO> columns;
}
