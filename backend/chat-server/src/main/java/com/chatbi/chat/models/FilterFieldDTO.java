package com.chatbi.chat.models;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
public class FilterFieldDTO implements Serializable {

    @Schema(description = "指标维度id")
    private Long id;

    @Schema(description = "指标/维度英文名")
    private String key;

    @Schema(description = "指标/维度中文名")
    private String name;

    @Schema(description = "字段属性(0维度,1指标)")
    private Integer fieldClazz;
}
