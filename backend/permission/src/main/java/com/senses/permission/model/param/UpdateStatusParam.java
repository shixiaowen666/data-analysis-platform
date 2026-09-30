package com.senses.permission.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "修改状态参数类")
@Data
public class UpdateStatusParam {
    @Schema(description = "id")
    private Long id;
    @Schema(description = "状态 0禁用1启用")
    private Integer status;
}
