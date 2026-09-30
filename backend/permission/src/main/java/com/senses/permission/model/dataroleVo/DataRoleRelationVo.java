package com.senses.permission.model.dataroleVo;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(description = "数据角色绑定关系VO")
public class DataRoleRelationVo {
    @Schema(description = "数据角色id",example = "1212")
    private Long dataRoleId;
    @Schema(description = "要绑定的id",example = "1212")
    private Long bindId;
    @Schema(description = "绑定类型：0.用户绑定1.部门绑定2.用户组绑定",example = "1212")
    private Integer bindType;//
    @Schema(description = "操作类型 1增加 2.减少",example = "1212")
    private Integer operType;//
    @Schema(description = "用户id",example = "1212")
    private Long userId;



}
