package com.senses.permission.model.dataroleVo;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(description = "数据角色查询VO")
public class DataRoleQueryCriteria {
    @Schema(description = "搜索关键字")
    private String  keyWords;


}
