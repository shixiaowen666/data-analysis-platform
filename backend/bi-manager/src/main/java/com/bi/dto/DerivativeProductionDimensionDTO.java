package com.bi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 衍生生产维度信息
 *@Author: gaowenqing 
 *@Date: 2020/2/7
*/
@Data

@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DerivativeProductionDimensionDTO implements Serializable{


    private Long dimensionId;

    private String dimensionName;

    private SymbolDTO symbol;
    private String dimensionValueList;
}
