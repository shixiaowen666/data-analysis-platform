package com.senses.permission.model.param;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;

/**
* 应用新增修改参数;
* @author : liaojinlei
* @date : 2022-12-7
*/
@Schema(description = "应用新增修改参数")
@Data
public class ApplicationParam {

   /** ID */
   @Schema(description = "ID",example = "1212")
   private Long id;

   /** 与应用交互的唯一id */
   @Schema(description = "与应用交互的唯一id")
   private String applicationKey;

   /** 应用名称 */
   @Schema(description = "应用名称")
   private String name;

   /** 应用编码 */
   @Schema(description = "应用编码")
   private String code;

}