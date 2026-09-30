package com.chatbi.chat.models;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

@Data
public class IndexVisualAngleDTO implements Serializable {

    @Schema(description = "指标Id")
    private Long indexId;

    @Schema(description = "指标英文名")
    private String key;

    @Schema(description = "指标中文名")
    private String showName;


    @Schema(description = "等级")
    private Integer level;

    @Schema(description = "有效天数")
    private Integer days;

    @Schema(description = "维度idSet")
    private Set<Long> dimIdSet;

    @Schema(description = "资源访问路径，(主题名称+业务线名称)")
    private String res;
}
