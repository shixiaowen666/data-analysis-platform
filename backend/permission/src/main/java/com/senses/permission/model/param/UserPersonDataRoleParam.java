package com.senses.permission.model.param;

import lombok.Data;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(description = "个人数据权限参数")
public class UserPersonDataRoleParam {
    @Schema(description = "过滤值 表名，列名")
    private String filterVal;
    @Schema(description = "引擎id")
    private List<Long> engineIds;
    @Schema(description = "数据库名称")
    private List<String> databaseNames;
    @Schema(description = "操作权限")
    private List<Integer> actions;
}
