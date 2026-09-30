package com.senses.permission.model.param;


import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
* 组织部门新增修改参数;
* @author : shinan
* @date : 2022-12-8
*/
@Schema(description = "组织部门新增修改参数")
@Data
public class DeptParam {

   /** ID */
   @Schema(description = "ID",example = "1212")
   private Long id;

   /** 部门名称 */
   @Schema(description = "部门名称")
   private String name;

   /** 部门编码 */
   @Schema(description = "部门编码")
   private String code;

   /** 上级部门 */
   @Schema(description = "上级部门")
   private Long pid;

   /** 状态0禁用1启用 */
   @Schema(description = "状态0禁用1启用")
   private Integer status;

   /** 创建人 */
   @Schema(description = "创建人")
   private String createdUser;

   /** 修改人 */
   @Schema(description = "修改人")
   private String modifyUser;

}