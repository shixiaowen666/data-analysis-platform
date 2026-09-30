package com.senses.permission.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;


@Schema(description = "应用分页查询条件")
@Data
public class ApplicationPageParam {
    @Schema(description = "应用名称或编码")
    private String filterVal;
}
