package com.bi.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data

@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DerivativeProductionDTO implements Serializable {


    private Long indicatorId;

    private String indicatorName;

    private List<DerivativeProductionDimensionDTO> dimensionList;


    private String formula;

    private String displayFormula;

    private List<FieldDTO> fieldDTOList;

}
