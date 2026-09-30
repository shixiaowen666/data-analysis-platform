package com.senses.permission.model.param;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
* 用户新增修改参数;
* @author : liaojinlei
* @date : 2022-12-7
*/
@Schema(description = "用户参数")
@Data
public class UserParam {

   /** ID */
   @Schema(description = "ID")
   private Long id;

   /** 邮箱 */
   @Schema(description = "邮箱")
   private String email;

   /** 状态：1启用、0禁用 */
   @Schema(description = "状态：1启用、0禁用")
   private Integer Status;

   /** 密码 */
   @Schema(description = "密码")
   private String password;

   /** 用户名 */
   @Schema(description = "用户名")
   private String username;

   /** 姓名 */
   @Schema(description = "姓名")
   private String name;

   /** 手机号 */
   @Schema(description = "手机号")
   private String phone;

   /** 部门id */
   @Schema(description = "部门id")
   private Long deptId;


   /** 是否管理员 0否1是 */
   @Schema(description = "是否管理员 0否1是")
   private Integer isAdmin;

}