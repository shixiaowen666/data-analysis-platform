package com.senses.permission.model.param;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
* 用户重置密码参数;
* @author : liaojinlei
* @date : 2022-12-7
*/
@Schema(description = "用户参数")
@Data
public class UserResetPasswordParam {

   /** ID */
   @Schema(description = "ID")
   private Long id;

   /** 密码 */
   @Schema(description = "密码")
   private String password;

   /** 重置密码token */
   @Schema(description = "重置密码token")
   private String resetToken;

}