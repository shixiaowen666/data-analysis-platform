package com.chatbi.chat.models;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class VariableInfoDto implements Serializable {

    @Schema(description = "id匹配指标平台")
    private Long id;

    @Schema(description = "变量key")
    private String key;

    @Schema(description = "变量名称")
    private String keyName;

    @Schema(description = "默认值")
    private List<String> defaultValue;

    @Schema(description = "变量值")
    private List<String> value;

    @Schema(description = "是否必填")
    private Boolean required;

    @Schema(description = "查询类型")
    private Integer qryType;

    @Schema(description = "别名")
    private String alias;
}
