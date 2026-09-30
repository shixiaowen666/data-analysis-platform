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
 * 分组表;
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Schema(description = "分组表")
@TableName("`group`")
@Data
public class Group{
    
    /** ID */
    @Schema(description = "ID")
    @TableId(value = "id",type= IdType.AUTO)
    private Long id;
    
    /** 组名称 */
    @Schema(description = "组名称")
    @TableField("group_name")
    private String groupName;
    
    /** 状态：1启用、0禁用 */
    @Schema(description = "状态：1启用、0禁用、2已删除")
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

    /** 用户id */
    @Schema(description = "用户id")
    @TableField(exist = false)
    private Long userId;

     /** 用户名称 */
     @Schema(description = "用户名称")
     @TableField(exist = false)
     private String username;

     /** 用户中文名称 */
     @Schema(description = "用户中文名称")
     @TableField(exist = false)
     private String cnUsername;

    /**审批状态 0审批中 1审批通过 2审批不通过 */
    @Schema(description = "审批状态 0未审批 1审批通过 2审批不通过 3已撤回")
    @TableField(exist = false)
    private Integer approveStatus;


    @Schema(description = "组成员数量")
    @TableField(exist = false)
    private Integer userCount;
}