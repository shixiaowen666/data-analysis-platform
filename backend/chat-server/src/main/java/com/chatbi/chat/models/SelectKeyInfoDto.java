package com.chatbi.chat.models;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
public class SelectKeyInfoDto implements Serializable {

    @Schema(description = "id（匹配指标平台）")
    private Long id;

    @Schema(description = "字段key")
    private String key;

    @Schema(description = "字段名字（匹配指标平台）")
    private String keyName;

    @Schema(description = "单位")
    private String fieldStyle;

    @Schema(description = "小数点")
    private String decimalPoint;

    @Schema(description = "重命名")
    private String rename;

    @Schema(description = "是否是时间维度 1是 其余不是")
    private Integer dateDimFlag;
}
