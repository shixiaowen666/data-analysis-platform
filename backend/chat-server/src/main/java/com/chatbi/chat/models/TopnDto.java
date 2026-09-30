package com.chatbi.chat.models;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class TopnDto implements Serializable {

    private static final long serialVersionUID = 138004009265140236L;

    /**
     * 维度ID
     */
    List<TopGroupDim> groupDims;
    /**
     * 排序指标
     */
    List<OrderByDTO> orders;

    /**
     * topN中的n
     */
    Integer topNum;
}
