package com.bi.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 可选关联表及字段
 */
@Data
public class CandidateDimTablesVO {

    private List<CandidateTableVO> dimTables;

    private List<CandidateColumnVO> factColumns;

    /**
     * key 为 dimTableId 字符串
     */
    private Map<String, List<CandidateColumnVO>> dimColumns;
}
