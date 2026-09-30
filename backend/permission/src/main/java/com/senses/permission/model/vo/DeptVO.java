package com.senses.permission.model.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;

/**
* 部门表;
* @author : liaojinlei
* @date : 2022-12-7
*/
@Schema(description = "部门信息")
@Data
public class DeptVO {

   /** ID */
   @Schema(description = "ID")
   private Long id;

   /** 名称 */
   @Schema(description = "名称")
   private String name;

   /** 部门编码 */
   @Schema(description = "部门编码")
   private String code;


   /** 状态0禁用1启用 */
   @Schema(description = "状态0禁用1启用")
   private Integer status;

   /** 创建时间 */
   @Schema(description = "创建时间")
   @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
   private Date createdTime;



   /** 修改时间 */
   @Schema(description = "修改时间")
   @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
   private Date modifyTime;




}