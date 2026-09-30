package com.senses.permission.model.param;


import lombok.Data;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
* 按部门id集合，用户组id集合，用户名或姓名查询用户列表
* @author : shinan
* @date : 2022-12-8
*/
@Schema(description = "按部门id集合，用户组id集合，用户名或姓名查询用户列表")
@Data
public class DeptGroupNameUserParam {
   @Schema(description = "部门id集合")
   private List<Long> deptIds;
   @Schema(description = "用户组id集合")
   private List<Long> groupIds;
   @Schema(description = "用户名/姓名过滤值")
   private String name;
}