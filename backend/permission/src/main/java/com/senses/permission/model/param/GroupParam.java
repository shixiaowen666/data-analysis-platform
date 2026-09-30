package com.senses.permission.model.param;


import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
* 用户组新增修改参数;
* @author : shinan
* @date : 2022-12-8
*/
@Schema(description = "用户组新增修改参数")
@Data
public class GroupParam {

   /** ID */
   @Schema(description = "ID",example = "1212")
   private Long id;

   /** 组名称 */
   @Schema(description = "组名称")
   private String groupName;

   /** 状态：1启用、0禁用 */
   @Schema(description = "状态：1启用、0禁用、2已删除")
   private Integer status;
}