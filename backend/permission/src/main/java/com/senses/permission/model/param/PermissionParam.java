package com.senses.permission.model.param;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;

/**
* 功能权限新增修改参数;
* @author : liaojinlei
* @date : 2022-12-7
*/
@Schema(description = "功能权限新增修改参数")
@TableName("permission")
@Data
public class PermissionParam {

   /** ID */
   @Schema(description = "ID")
   private Long id;

   /** 名称 */
   @Schema(description = "名称")
   private String name;

   /** 上级权限 */
   @Schema(description = "上级权限")
   private Long pid;

   /** 所属应用id */
   @Schema(description = "所属应用id")
   private Long appId;

   /** 功能标识 */
   @Schema(description = "功能标识")
   private String sign;

   /** 功能类型 0菜单menu，1按钮button*/
   @Schema(description = "功能类型 0菜单menu，1按钮button")
   private Integer type;

   /** 菜单类型 0外部菜单，1内部菜单*/
   @Schema(description = "菜单类型 0外部菜单，1内部菜单")
   private Integer menuType;

   /** 按钮类型*/
   @Schema(description = "按钮类型 0内部链接按钮，1外部链接按钮， 2功能性按钮")
   private Integer buttonType;

   /** 链接 */
   @Schema(description = "链接")
   private String externalUrl;

   /** 序号*/
   @Schema(description = "序号")
   private Integer orderNum;
}