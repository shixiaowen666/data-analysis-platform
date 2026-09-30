package com.bi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data

@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldDTO implements Serializable {
    //@ApiModelProperty("字段类型（条件指标：CONDITION-IND；结果指标：RESULT-IND；维度：DIM）")
    private String fieldType;
    //@ApiModelProperty("字段id")
    private Long id;
    //@ApiModelProperty("字段名称")
    private String alias;
    //@ApiModelProperty("字段名称（A、B、C）")
    private String letter;
}
