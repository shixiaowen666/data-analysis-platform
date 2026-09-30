package com.senses.permission.model.dataroleVo;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(description = "角色权限查询VO")
public class DataPermissionQueryCriteria {
    @Schema(description = "搜索关键字")
    private String  keyWords;
    @Schema(description = "数据角色id")
    private Long dataRoleId;




}
