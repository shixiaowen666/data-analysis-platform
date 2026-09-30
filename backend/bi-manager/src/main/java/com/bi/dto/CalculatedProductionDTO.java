package com.bi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 计算生产
 *@Author: gaowenqing
 *@Date: 2020/2/7
*/
@Data

@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculatedProductionDTO implements Serializable {

    private String formula;

    private String displayFormula;

    private List<IndicatorDTO> indicatorList;

}
