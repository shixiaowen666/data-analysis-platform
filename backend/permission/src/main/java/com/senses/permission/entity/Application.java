package com.senses.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;

 /**
 * 应用表;
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Schema(description = "应用表")
@TableName("application")
@Data
public class Application{
    
    /** ID */
    @Schema(description = "ID")
    @TableId(value = "id",type= IdType.AUTO)
    private Long id;
    
    /** 与应用交互的唯一id */
    @Schema(description = "与应用交互的唯一id")
    @TableField("application_key")
    private String applicationKey;
    
    /** 应用名称 */
    @Schema(description = "应用名称")
    @TableField("name")
    private String name;
    
    /** 应用编码 */
    @Schema(description = "应用编码")
    @TableField("code")
    private String code;
    
    /** 状态0禁用1启用2删除 */
    @Schema(description = "状态0禁用1启用2删除")
    @TableField("status")
    private Integer status;
    
    /** 创建日期 */
    @Schema(description = "创建日期")
    @TableField("created_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdTime;
    
    /** 创建者 */
    @Schema(description = "创建者")
    @TableField("created_user")
    private String createdUser;
    
    /** 提交人 */
    @Schema(description = "提交人")
    @TableField("modify_user")
    private String modifyUser;
    
    /** 更新时间 */
    @Schema(description = "更新时间")
    @TableField("modify_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date modifyTime;
}