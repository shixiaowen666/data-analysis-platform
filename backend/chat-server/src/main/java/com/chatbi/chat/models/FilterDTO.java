package com.chatbi.chat.models;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class FilterDTO implements Serializable {

    private FilterFieldDTO filterField;

    @Schema(description = "比较值")
    @JsonDeserialize(using = StringOrArrayDeserializer.class)
    private List<String> filterValue;

    @Schema(description = "筛选类型比较类型：0-大于 1-小于 2-等于 3-大于等于 4-小于等于 5-in 6-not in 7-不等于 8-like 9-not like")
    private Integer operator;

    @Schema(description = "多filter之间的逻辑关系：0-AND 1-OR，默认为AND")
    private Integer logicType = 0;

}
